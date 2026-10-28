package org.langhua.ofbiz.mfg;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.apache.ofbiz.entity.Delegator;
import org.apache.ofbiz.entity.GenericEntityException;
import org.apache.ofbiz.entity.GenericValue;
import org.apache.ofbiz.entity.util.EntityQuery;
import org.apache.ofbiz.service.DispatchContext;
import org.apache.ofbiz.service.ServiceUtil;

/**
 * Sales orders: the two level reservation (stock now, finished goods later), the plan that turns
 * the missing quantity into work orders, and the line editor of the order page.
 *
 * <p>A line is short by {@code qty - reserved - shipped}; the plan only starts a work order for
 * what is neither in stock nor already in production, and completion puts the new units back on
 * the same line. The reservation rows are ordinary inventory movements, so the lot page and the
 * order page can never tell two different numbers. Moved out of {@code MfgServices} in Step 170.
 */
public final class MfgSalesOrderServices {

    private MfgSalesOrderServices() {
    }

    /**
     * What is still owed on an order line: what was ordered, minus what is reserved from stock and
     * what was already shipped.
     *
     * @param qty the ordered quantity
     * @param reserved the quantity reserved from stock
     * @param shipped the quantity already shipped
     * @return the open quantity, never negative
     */
    static double openQtyOf(double qty, double reserved, double shipped) {
        double open = qty - reserved - shipped;
        return open <= 0.0001 ? 0.0 : MfgSupport.round2(open);
    }

    /**
     * How much of an order line still has to be produced: the open quantity minus what the work
     * orders of that line will still deliver.
     *
     * @param qty the ordered quantity
     * @param reserved the quantity reserved from stock
     * @param shipped the quantity already shipped
     * @param inProduction what the line work orders will still deliver
     * @return the quantity to plan, never negative
     */
    static double productionQtyOf(double qty, double reserved, double shipped, double inProduction) {
        double missing = openQtyOf(qty, reserved, shipped) - Math.max(0.0, inProduction);
        return missing <= 0.0001 ? 0.0 : MfgSupport.round2(missing);
    }

    /**
     * Safely writes a sales order with its lines, then locks what the shelf can already cover.
     *
     * <p>The lines are given as text, one model per line: {@code 型号,数量} or
     * {@code 型号,数量,交期}. Saving an existing order keeps the line numbers, so the reservations
     * that already belong to a line survive an edit.
     *
     * @param dctx the dispatch context
     * @param context service context, see {@code servicedef/salesorder-services.xml#mfgSalesOrderSave}
     * @return the service result with the order number
     */
    public static Map<String, Object> salesOrderSave(DispatchContext dctx, Map<String, ? extends Object> context) {
        String denied = MfgSecurity.permissionProblem(dctx, context, MfgConstants.PERM_SALES);
        if (denied != null) {
            return ServiceUtil.returnError(denied);
        }
        Delegator delegator = dctx.getDelegator();
        String salesOrderId = MfgSupport.toText(context.get("salesOrderId"));
        String customerName = MfgSupport.toText(context.get("customerName"));
        if (customerName == null) {
            return ServiceUtil.returnError("customerName is required");
        }
        List<Map<String, Object>> lines = MfgCsv.parseOrderLines(MfgSupport.toText(context.get("lines")));
        if (lines.isEmpty()) {
            return ServiceUtil.returnError("Give at least one line as 型号,数量");
        }
        for (Map<String, Object> line : lines) {
            if (MfgSupport.numberOrZero(line.get("qty")) <= 0.0) {
                return ServiceUtil.returnError("Line [" + line.get("productCode")
                        + "] needs a positive quantity");
            }
        }
        try {
            for (Map<String, Object> line : lines) {
                String checkedCode = (String) line.get("productCode");
                if (EntityQuery.use(delegator).from("MfgProduct")
                        .where("productCode", checkedCode).queryOne() == null) {
                    return ServiceUtil.returnError("Unknown product model [" + checkedCode
                            + "], create it first");
                }
            }
            GenericValue order;
            if (salesOrderId == null) {
                salesOrderId = nextSalesOrderId(delegator, LocalDate.now());
                order = delegator.makeValue("MfgSalesOrder");
                order.set("salesOrderId", salesOrderId);
                order.set("statusId", MfgConstants.SO_OPEN);
                delegator.create(order);
            } else {
                order = EntityQuery.use(delegator).from("MfgSalesOrder")
                        .where("salesOrderId", salesOrderId).queryOne();
                if (order == null) {
                    return ServiceUtil.returnError("Unknown sales order [" + salesOrderId + "]");
                }
                if (MfgConstants.SO_CLOSED.equals(order.getString("statusId"))
                        || MfgConstants.SO_CANCELLED.equals(order.getString("statusId"))) {
                    return ServiceUtil.returnError("Sales order [" + salesOrderId + "] is "
                            + order.getString("statusId"));
                }
            }
            order.set("customerName", customerName);
            if (context.containsKey("customerPo")) {
                order.set("customerPo", MfgSupport.toText(context.get("customerPo")));
            }
            Object orderDate = MfgSupport.parseDate(context.get("orderDate"));
            order.set("orderDate", orderDate == null ? java.sql.Date.valueOf(LocalDate.now()) : orderDate);
            if (context.containsKey("dueDate")) {
                order.set("dueDate", MfgSupport.parseDate(context.get("dueDate")));
            }
            if (context.containsKey("note")) {
                order.set("note", MfgSupport.toText(context.get("note")));
            }
            delegator.store(order);

            Map<Long, GenericValue> existing = new LinkedHashMap<>();
            for (GenericValue item : EntityQuery.use(delegator).from("MfgSalesOrderItem")
                    .where("salesOrderId", salesOrderId).orderBy("lineSeq").queryList()) {
                existing.put(item.getLong("lineSeq"), item);
            }
            long lineSeq = 0;
            for (Map<String, Object> line : lines) {
                lineSeq++;
                GenericValue item = existing.remove(Long.valueOf(lineSeq));
                if (item == null) {
                    item = delegator.makeValue("MfgSalesOrderItem");
                    item.set("salesOrderId", salesOrderId);
                    item.set("lineSeq", Long.valueOf(lineSeq));
                    item.set("shippedQty", Double.valueOf(0.0));
                }
                item.set("productCode", line.get("productCode"));
                item.set("qty", line.get("qty"));
                Object lineDue = line.get("lineDueDate");
                item.set("lineDueDate", lineDue == null ? order.get("dueDate") : lineDue);
                delegator.createOrStore(item);
            }
            // the lines that are gone take their reservations with them
            for (GenericValue removedItem : existing.values()) {
                releaseOrderLine(delegator, salesOrderId, removedItem.getLong("lineSeq").longValue(),
                        MfgSupport.toText(context.get("operatorName")), "订单行被删除，释放预留");
                delegator.removeValue(removedItem);
            }

            Map<String, Object> reservation = reserveSalesOrder(dctx, context, salesOrderId, order);
            Map<String, Object> result = ServiceUtil.returnSuccess();
            result.put("salesOrderId", salesOrderId);
            result.put("lineCount", Integer.valueOf(lines.size()));
            result.put("statusId", order.getString("statusId"));
            if (!ServiceUtil.isError(reservation)) {
                result.put("reservedQty", reservation.get("reservedQty"));
                result.put("shortQty", reservation.get("shortQty"));
            }
            return result;
        } catch (GenericEntityException e) {
            return ServiceUtil.returnError("Could not save the sales order: " + e.getMessage());
        }
    }

    /**
     * Locks what the shelf can cover for one sales order: for every open line it reserves finished
     * goods, oldest lot first. What is still missing stays open for the planning step.
     *
     * @param dctx the dispatch context
     * @param context service context, see {@code servicedef/salesorder-services.xml#mfgSalesOrderReserve}
     * @return the service result with what was reserved and what is still owed
     */
    public static Map<String, Object> salesOrderReserve(DispatchContext dctx, Map<String, ? extends Object> context) {
        String denied = MfgSecurity.permissionProblem(dctx, context, MfgConstants.PERM_SALES);
        if (denied != null) {
            return ServiceUtil.returnError(denied);
        }
        Delegator delegator = dctx.getDelegator();
        String salesOrderId = MfgSupport.toText(context.get("salesOrderId"));
        if (salesOrderId == null) {
            return ServiceUtil.returnError("salesOrderId is required");
        }
        try {
            GenericValue order = EntityQuery.use(delegator).from("MfgSalesOrder")
                    .where("salesOrderId", salesOrderId).queryOne();
            if (order == null) {
                return ServiceUtil.returnError("Unknown sales order [" + salesOrderId + "]");
            }
            Map<String, Object> result = reserveSalesOrder(dctx, context, salesOrderId, order);
            if (ServiceUtil.isError(result)) {
                return result;
            }
            result.put("salesOrderId", salesOrderId);
            return result;
        } catch (GenericEntityException e) {
            return ServiceUtil.returnError("Could not reserve the finished goods: " + e.getMessage());
        }
    }

    /**
     * Reserves the open quantity of every line of one order, oldest finished goods lot first.
     *
     * @return the result with {@code reservedQty} and {@code shortQty}
     */
    static Map<String, Object> reserveSalesOrder(DispatchContext dctx, Map<String, ? extends Object> context,
            String salesOrderId, GenericValue order) throws GenericEntityException {
        Delegator delegator = dctx.getDelegator();
        String operator = MfgSupport.toText(context.get("operatorName"));
        double reservedTotal = 0.0;
        double shortTotal = 0.0;
        for (GenericValue item : EntityQuery.use(delegator).from("MfgSalesOrderItem")
                .where("salesOrderId", salesOrderId).orderBy("lineSeq").queryList()) {
            double need = openQtyOf(MfgSupport.numberOrZero(item.get("qty")),
                    orderLineReserved(delegator, salesOrderId, item.getLong("lineSeq").longValue()),
                    MfgSupport.numberOrZero(item.get("shippedQty")));
            if (need <= 0.0001) {
                continue;
            }
            double taken = reserveOrderLine(delegator, salesOrderId, item, need, operator);
            reservedTotal += taken;
            shortTotal += MfgSupport.round2(need - taken);
        }
        Map<String, Object> result = ServiceUtil.returnSuccess();
        result.put("reservedQty", Double.valueOf(MfgSupport.round2(reservedTotal)));
        result.put("shortQty", Double.valueOf(MfgSupport.round2(shortTotal)));
        result.put("salesOrderId", salesOrderId);
        result.put("statusId", order.getString("statusId"));
        return result;
    }

    /**
     * Reserves up to {@code need} units of one order line out of the finished goods lots of its
     * model.
     *
     * @return how much was reserved
     */
    static double reserveOrderLine(Delegator delegator, String salesOrderId, GenericValue item,
            double need, String operator) throws GenericEntityException {
        double remaining = need;
        double taken = 0.0;
        long lineSeq = item.getLong("lineSeq").longValue();
        String productCode = item.getString("productCode");
        for (GenericValue lot : productLotsOf(delegator, productCode)) {
            if (remaining <= 0.0001) {
                break;
            }
            MfgStockLedger.ensureLotLedger(delegator, lot);
            double free = MfgSupport.numberOrZero(lot.get("availableQty"));
            if (free <= 0.0001) {
                continue;
            }
            double take = Math.min(remaining, free);
            recordOrderReservation(delegator, lot, MfgConstants.MOVE_RESERVE, take, salesOrderId, lineSeq, operator,
                    "订单 " + salesOrderId + " 第 " + lineSeq + " 行预留");
            MfgStockLedger.recalcLotStock(delegator, lot);
            remaining = MfgSupport.round2(remaining - take);
            taken += take;
        }
        return taken;
    }

    /**
     * Puts the units that the shelf cannot cover into production: every line that is still open
     * gets a work order, or has the planned quantity of its work order raised.
     *
     * @param dctx the dispatch context
     * @param context service context, see {@code servicedef/salesorder-services.xml#mfgSalesOrderPlan}
     * @return the service result with the planned quantity
     */
    public static Map<String, Object> salesOrderPlan(DispatchContext dctx, Map<String, ? extends Object> context) {
        String denied = MfgSecurity.permissionProblem(dctx, context, MfgConstants.PERM_SALES);
        if (denied != null) {
            return ServiceUtil.returnError(denied);
        }
        Delegator delegator = dctx.getDelegator();
        String salesOrderId = MfgSupport.toText(context.get("salesOrderId"));
        if (salesOrderId == null) {
            return ServiceUtil.returnError("salesOrderId is required");
        }
        try {
            GenericValue order = EntityQuery.use(delegator).from("MfgSalesOrder")
                    .where("salesOrderId", salesOrderId).queryOne();
            if (order == null) {
                return ServiceUtil.returnError("Unknown sales order [" + salesOrderId + "]");
            }
            if (MfgConstants.SO_CLOSED.equals(order.getString("statusId"))
                    || MfgConstants.SO_CANCELLED.equals(order.getString("statusId"))) {
                return ServiceUtil.returnError("Sales order [" + salesOrderId + "] is "
                        + order.getString("statusId"));
            }
            double plannedTotal = 0.0;
            int plannedLines = 0;
            for (GenericValue item : EntityQuery.use(delegator).from("MfgSalesOrderItem")
                    .where("salesOrderId", salesOrderId).orderBy("lineSeq").queryList()) {
                double need = productionQtyOf(MfgSupport.numberOrZero(item.get("qty")),
                        orderLineReserved(delegator, salesOrderId, item.getLong("lineSeq").longValue()),
                        MfgSupport.numberOrZero(item.get("shippedQty")),
                        inProductionOf(delegator, item.getString("workOrderId")));
                if (need <= 0.0001) {
                    continue;
                }
                String productCode = item.getString("productCode");
                Object lineDue = item.get("lineDueDate") == null ? order.get("dueDate") : item.get("lineDueDate");
                String workOrderId = item.getString("workOrderId");
                GenericValue workOrder = workOrderId == null ? null
                        : EntityQuery.use(delegator).from("MfgWorkOrder")
                                .where("workOrderId", workOrderId).queryOne();
                if (workOrder != null && (MfgConstants.WO_CLOSED.equals(workOrder.getString("statusId"))
                        || MfgConstants.WO_COMPLETED.equals(workOrder.getString("statusId")))) {
                    // the order of that line is finished, so a new one has to make the rest
                    workOrder = null;
                }
                if (workOrder == null) {
                    Map<String, Object> createContext = new LinkedHashMap<>();
                    createContext.put("productCode", productCode);
                    createContext.put("plannedQty", Long.valueOf(Math.round(need)));
                    if (lineDue != null) {
                        createContext.put("dueDate", lineDue.toString());
                    }
                    createContext.put("customerName", "订单 " + salesOrderId + " / "
                            + order.getString("customerName"));
                    createContext.put("note", "由销售订单 " + salesOrderId + " 第 "
                            + item.getLong("lineSeq") + " 行自动排产");
                    createContext.put("userLogin", context.get("userLogin"));
                    Map<String, Object> created = MfgWorkOrderServices.workOrderCreate(dctx, createContext);
                    if (ServiceUtil.isError(created)) {
                        return created;
                    }
                    workOrderId = (String) created.get("workOrderId");
                    item.set("workOrderId", workOrderId);
                } else {
                    long raised = workOrder.getLong("plannedQty") == null ? 0L
                            : workOrder.getLong("plannedQty").longValue();
                    workOrder.set("plannedQty", Long.valueOf(raised + Math.round(need)));
                    delegator.store(workOrder);
                }
                delegator.store(item);
                plannedTotal += Math.round(need);
                plannedLines++;
            }
            if (plannedLines > 0 && !MfgConstants.SO_SHIPPED.equals(order.getString("statusId"))) {
                order.set("statusId", MfgConstants.SO_PLANNED);
                delegator.store(order);
            }
            Map<String, Object> result = ServiceUtil.returnSuccess();
            result.put("salesOrderId", salesOrderId);
            result.put("plannedQty", Double.valueOf(MfgSupport.round2(plannedTotal)));
            result.put("plannedLines", Integer.valueOf(plannedLines));
            result.put("statusId", order.getString("statusId"));
            return result;
        } catch (GenericEntityException e) {
            return ServiceUtil.returnError("Could not plan the sales order: " + e.getMessage());
        }
    }

    /**
     * Closes a sales order: every reservation it holds goes back to the shelf.
     *
     * @param dctx the dispatch context
     * @param context service context, see {@code servicedef/salesorder-services.xml#mfgSalesOrderClose}
     * @return the service result with the released quantity
     */
    public static Map<String, Object> salesOrderClose(DispatchContext dctx, Map<String, ? extends Object> context) {
        String denied = MfgSecurity.permissionProblem(dctx, context, MfgConstants.PERM_SALES);
        if (denied != null) {
            return ServiceUtil.returnError(denied);
        }
        Delegator delegator = dctx.getDelegator();
        String salesOrderId = MfgSupport.toText(context.get("salesOrderId"));
        if (salesOrderId == null) {
            return ServiceUtil.returnError("salesOrderId is required");
        }
        try {
            GenericValue order = EntityQuery.use(delegator).from("MfgSalesOrder")
                    .where("salesOrderId", salesOrderId).queryOne();
            if (order == null) {
                return ServiceUtil.returnError("Unknown sales order [" + salesOrderId + "]");
            }
            double released = releaseSalesOrder(delegator, salesOrderId, MfgSupport.toText(context.get("operatorName")),
                    "订单 " + salesOrderId + " 关闭，释放预留");
            order.set("statusId", MfgConstants.SO_CLOSED);
            delegator.store(order);
            Map<String, Object> result = ServiceUtil.returnSuccess();
            result.put("salesOrderId", salesOrderId);
            result.put("releasedQty", Double.valueOf(MfgSupport.round2(released)));
            result.put("statusId", MfgConstants.SO_CLOSED);
            return result;
        } catch (GenericEntityException e) {
            return ServiceUtil.returnError("Could not close the sales order: " + e.getMessage());
        }
    }

    /**
     * Lists the sales orders, newest first, with what each one still owes.
     *
     * @param dctx the dispatch context
     * @param context service context, see {@code servicedef/salesorder-services.xml#mfgSalesOrderList}
     * @return the service result containing {@code orders}
     */
    public static Map<String, Object> salesOrderList(DispatchContext dctx, Map<String, ? extends Object> context) {
        Delegator delegator = dctx.getDelegator();
        String statusId = MfgSupport.toText(context.get("statusId"));
        try {
            List<Map<String, Object>> list = new ArrayList<>();
            for (GenericValue order : EntityQuery.use(delegator).from("MfgSalesOrder")
                    .orderBy("-createdStamp").queryList()) {
                if (statusId != null && !statusId.equals(order.getString("statusId"))) {
                    continue;
                }
                Map<String, Object> map = toSalesOrderMap(delegator, order);
                list.add(map);
            }
            Map<String, Object> result = ServiceUtil.returnSuccess();
            MfgPaging.putPage(result, "orders",
                    MfgPaging.filterByKeyword(list, MfgSupport.toText(context.get("search")), "salesOrderId", "customerName",
                            "customerPo"),
                    MfgPaging.pageOf(context), MfgPaging.pageSizeOf(context), MfgSupport.toText(context.get("search")));
            // the list unfolds its lines in place, and the quantity of a line can be corrected there
            for (Map<String, Object> row : MfgPaging.rowsOf(result, "orders")) {
                row.put("items", orderLineMaps(delegator, (String) row.get("salesOrderId")));
            }
            result.put("statusId", statusId);
            result.put("statuses", MfgConstants.SO_STATUSES);
            return result;
        } catch (GenericEntityException e) {
            return ServiceUtil.returnError("Could not read the sales orders: " + e.getMessage());
        }
    }

    /**
     * One sales order with its lines, what it reserved and the work orders it started.
     *
     * @param dctx the dispatch context
     * @param context service context, see {@code servicedef/salesorder-services.xml#mfgSalesOrderDetail}
     * @return the service result containing {@code order}, {@code items} and {@code reservations}
     */
    public static Map<String, Object> salesOrderDetail(DispatchContext dctx, Map<String, ? extends Object> context) {
        Delegator delegator = dctx.getDelegator();
        String salesOrderId = MfgSupport.toText(context.get("salesOrderId"));
        Map<String, Object> empty = ServiceUtil.returnSuccess();
        empty.put("items", new ArrayList<Map<String, Object>>());
        empty.put("reservations", new ArrayList<Map<String, Object>>());
        if (salesOrderId == null) {
            return empty;
        }
        try {
            GenericValue order = EntityQuery.use(delegator).from("MfgSalesOrder")
                    .where("salesOrderId", salesOrderId).queryOne();
            if (order == null) {
                return empty;
            }
            Map<String, Object> result = ServiceUtil.returnSuccess();
            result.put("order", toSalesOrderMap(delegator, order));
            result.put("items", orderLineMaps(delegator, salesOrderId));

            List<Map<String, Object>> reservations = new ArrayList<>();
            Map<String, GenericValue> lots = new LinkedHashMap<>();
            for (GenericValue movement : EntityQuery.use(delegator).from("MfgInventoryDetail")
                    .where("salesOrderId", salesOrderId).queryList()) {
                double reserved = MfgSupport.numberOrZero(movement.get("reservedDiff"));
                if (reserved == 0.0) {
                    continue;
                }
                String lotId = movement.getString("lotId");
                GenericValue lot = lots.get(lotId);
                if (lot == null) {
                    lot = EntityQuery.use(delegator).from("MfgMaterialLot").where("lotId", lotId).queryOne();
                    if (lot != null) {
                        lots.put(lotId, lot);
                    }
                }
                Map<String, Object> map = new LinkedHashMap<>();
                map.put("lotId", lotId);
                map.put("lineSeq", movement.get("salesOrderItemSeq"));
                map.put("productCode", lot == null ? null : lot.getString("componentCode"));
                map.put("reservedDiff", Double.valueOf(MfgSupport.round2(reserved)));
                map.put("movementTypeId", movement.getString("movementTypeId"));
                map.put("effectiveDate", movement.get("effectiveDate"));
                map.put("note", movement.getString("note"));
                reservations.add(map);
            }
            result.put("reservations", reservations);
            return result;
        } catch (GenericEntityException e) {
            return ServiceUtil.returnError("Could not read the sales order: " + e.getMessage());
        }
    }

    /**
     * The lines of one sales order with what each one still owes, in line order.
     *
     * @param delegator the delegator
     * @param salesOrderId the order
     * @return one map per line, empty when the order has no line
     * @throws GenericEntityException when the lines or the reservations cannot be read
     */
    static List<Map<String, Object>> orderLineMaps(Delegator delegator, String salesOrderId)
            throws GenericEntityException {
        List<Map<String, Object>> items = new ArrayList<>();
        for (GenericValue item : EntityQuery.use(delegator).from("MfgSalesOrderItem")
                .where("salesOrderId", salesOrderId).orderBy("lineSeq").queryList()) {
            long lineSeq = item.getLong("lineSeq").longValue();
            double reserved = orderLineReserved(delegator, salesOrderId, lineSeq);
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("lineSeq", Long.valueOf(lineSeq));
            map.put("productCode", item.getString("productCode"));
            map.put("qty", item.get("qty"));
            map.put("reservedQty", Double.valueOf(MfgSupport.round2(reserved)));
            map.put("shippedQty", item.get("shippedQty"));
            map.put("openQty", Double.valueOf(openQtyOf(MfgSupport.numberOrZero(item.get("qty")), reserved,
                    MfgSupport.numberOrZero(item.get("shippedQty")))));
            map.put("workOrderId", item.getString("workOrderId"));
            map.put("lineDueDate", item.get("lineDueDate"));
            map.put("note", item.getString("note"));
            items.add(map);
        }
        return items;
    }

    /**
     * Changes the quantity of one order line from the list page, so a mistyped order does not have
     * to be cancelled and entered again.
     *
     * <p>The reservations already taken stay as they are: raising the quantity leaves a bigger gap
     * for {@code mfgSalesOrderReserve} / {@code mfgSalesOrderPlan} to fill, lowering it below what
     * is reserved only lowers the gap. The quantity can never drop under what has shipped.
     *
     * @param dctx the dispatch context
     * @param context service context, see {@code servicedef/salesorder-services.xml#mfgSalesOrderLineQty}
     * @return the service result with the numbers of the line and of the whole order
     */
    public static Map<String, Object> salesOrderLineQty(DispatchContext dctx, Map<String, ? extends Object> context) {
        String denied = MfgSecurity.permissionProblem(dctx, context, MfgConstants.PERM_SALES, MfgConstants.PERM_ADMIN);
        if (denied != null) {
            return ServiceUtil.returnError(denied);
        }
        Delegator delegator = dctx.getDelegator();
        String salesOrderId = MfgSupport.toText(context.get("salesOrderId"));
        long lineSeq = MfgSupport.toLong(context.get("lineSeq"), -1);
        Double qty = MfgSupport.optionalAmount(context.get("qty"));
        if (salesOrderId == null || lineSeq < 0) {
            return ServiceUtil.returnError("缺少订单号或行号");
        }
        if (qty == null || qty.doubleValue() <= 0.0) {
            return ServiceUtil.returnError("数量要大于 0");
        }
        try {
            GenericValue order = EntityQuery.use(delegator).from("MfgSalesOrder")
                    .where("salesOrderId", salesOrderId).queryOne();
            if (order == null) {
                return ServiceUtil.returnError("Unknown sales order [" + salesOrderId + "]");
            }
            String statusId = order.getString("statusId");
            if (MfgConstants.SO_CLOSED.equals(statusId) || MfgConstants.SO_CANCELLED.equals(statusId)) {
                return ServiceUtil.returnError("订单已" + (MfgConstants.SO_CLOSED.equals(statusId) ? "关闭" : "取消")
                        + "，不能再改数量");
            }
            GenericValue item = EntityQuery.use(delegator).from("MfgSalesOrderItem")
                    .where("salesOrderId", salesOrderId, "lineSeq", Long.valueOf(lineSeq)).queryOne();
            if (item == null) {
                return ServiceUtil.returnError("订单 " + salesOrderId + " 没有第 " + lineSeq + " 行");
            }
            double newQty = MfgSupport.round2(qty.doubleValue());
            double shipped = MfgSupport.numberOrZero(item.get("shippedQty"));
            if (newQty < shipped) {
                return ServiceUtil.returnError("第 " + lineSeq + " 行已经出货 " + MfgSupport.formatQty(shipped)
                        + "，数量不能小于这个数");
            }
            item.set("qty", Double.valueOf(newQty));
            delegator.store(item);

            Map<String, Object> result = ServiceUtil.returnSuccess();
            result.put("salesOrderId", salesOrderId);
            result.put("lineSeq", Long.valueOf(lineSeq));
            double reserved = orderLineReserved(delegator, salesOrderId, lineSeq);
            result.put("qty", Double.valueOf(newQty));
            result.put("reservedQty", Double.valueOf(reserved));
            result.put("openQty", Double.valueOf(openQtyOf(newQty, reserved, shipped)));
            Map<String, Object> orderMap = toSalesOrderMap(delegator, order);
            result.put("openTotal", orderMap.get("openTotal"));
            result.put("qtyTotal", orderMap.get("qtyTotal"));
            result.put("statusId", order.getString("statusId"));
            return result;
        } catch (GenericEntityException e) {
            return ServiceUtil.returnError("Could not change the line: " + e.getMessage());
        }
    }

    static Map<String, Object> toSalesOrderMap(Delegator delegator, GenericValue order)
            throws GenericEntityException {
        String salesOrderId = order.getString("salesOrderId");
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("salesOrderId", salesOrderId);
        map.put("customerName", order.getString("customerName"));
        map.put("customerPo", order.getString("customerPo"));
        map.put("orderDate", order.get("orderDate"));
        map.put("dueDate", order.get("dueDate"));
        map.put("statusId", order.getString("statusId"));
        map.put("note", order.getString("note"));
        double qty = 0.0;
        double reserved = 0.0;
        double shipped = 0.0;
        double open = 0.0;
        int lines = 0;
        for (GenericValue item : EntityQuery.use(delegator).from("MfgSalesOrderItem")
                .where("salesOrderId", salesOrderId).orderBy("lineSeq").queryList()) {
            lines++;
            double lineReserved = orderLineReserved(delegator, salesOrderId, item.getLong("lineSeq").longValue());
            qty += MfgSupport.numberOrZero(item.get("qty"));
            reserved += lineReserved;
            shipped += MfgSupport.numberOrZero(item.get("shippedQty"));
            open += openQtyOf(MfgSupport.numberOrZero(item.get("qty")), lineReserved,
                    MfgSupport.numberOrZero(item.get("shippedQty")));
        }
        map.put("lineCount", Integer.valueOf(lines));
        map.put("qtyTotal", Double.valueOf(MfgSupport.round2(qty)));
        map.put("reservedTotal", Double.valueOf(MfgSupport.round2(reserved)));
        map.put("shippedTotal", Double.valueOf(MfgSupport.round2(shipped)));
        map.put("openTotal", Double.valueOf(MfgSupport.round2(open)));
        return map;
    }

    /**
     * What one order line has reserved from stock right now.
     *
     * @param delegator the delegator
     * @param salesOrderId the order
     * @param lineSeq the line
     * @return the reserved quantity, {@code 0} when nothing is reserved
     * @throws GenericEntityException when the ledger cannot be read
     */
    static double orderLineReserved(Delegator delegator, String salesOrderId, long lineSeq)
            throws GenericEntityException {
        double reserved = 0.0;
        for (GenericValue movement : EntityQuery.use(delegator).from("MfgInventoryDetail")
                .where("salesOrderId", salesOrderId).queryList()) {
            Long movementLine = movement.getLong("salesOrderItemSeq");
            if (movementLine == null || movementLine.longValue() != lineSeq) {
                continue;
            }
            reserved += MfgSupport.numberOrZero(movement.get("reservedDiff"));
        }
        return reserved <= 0.0 ? 0.0 : MfgSupport.round2(reserved);
    }

    /**
     * Appends a reservation that belongs to a sales order line.
     *
     * @param delegator the delegator
     * @param lot the finished goods lot
     * @param movementTypeId {@link MfgConstants#MOVE_RESERVE} or {@link MfgConstants#MOVE_RELEASE}
     * @param diff the signed change of the reserved quantity
     * @param salesOrderId the order
     * @param lineSeq the line of the order
     * @param operatorName who did it
     * @param note a remark
     * @throws GenericEntityException when it cannot be stored
     */
    static void recordOrderReservation(Delegator delegator, GenericValue lot, String movementTypeId,
            double diff, String salesOrderId, long lineSeq, String operatorName, String note)
            throws GenericEntityException {
        GenericValue movement = delegator.makeValue("MfgInventoryDetail");
        movement.set("lotId", lot.getString("lotId"));
        movement.set("seqId", Long.valueOf(MfgStockLedger.nextMovementSeq(delegator, lot.getString("lotId"))));
        movement.set("movementTypeId", movementTypeId);
        movement.set("quantityDiff", Double.valueOf(0.0));
        movement.set("reservedDiff", Double.valueOf(MfgSupport.round2(diff)));
        movement.set("salesOrderId", salesOrderId);
        movement.set("salesOrderItemSeq", Long.valueOf(lineSeq));
        movement.set("operatorName", operatorName);
        movement.set("effectiveDate", new Timestamp(System.currentTimeMillis()));
        movement.set("note", note);
        delegator.create(movement);
    }

    /**
     * Gives back what the lots still hold for one order line.
     *
     * @param delegator the delegator
     * @param salesOrderId the order
     * @param lineSeq the line
     * @param operatorName who did it
     * @param note a remark
     * @return the released quantity
     * @throws GenericEntityException when the ledger cannot be written
     */
    static double releaseOrderLine(Delegator delegator, String salesOrderId, long lineSeq,
            String operatorName, String note) throws GenericEntityException {
        double released = 0.0;
        for (GenericValue lot : productLots(delegator)) {
            double reserved = 0.0;
            for (GenericValue movement : MfgStockLedger.movementsOf(delegator, lot.getString("lotId"))) {
                Long movementLine = movement.getLong("salesOrderItemSeq");
                if (!salesOrderId.equals(movement.getString("salesOrderId")) || movementLine == null
                        || movementLine.longValue() != lineSeq) {
                    continue;
                }
                reserved += MfgSupport.numberOrZero(movement.get("reservedDiff"));
            }
            if (reserved <= 0.0001) {
                continue;
            }
            recordOrderReservation(delegator, lot, MfgConstants.MOVE_RELEASE, -reserved, salesOrderId, lineSeq,
                    operatorName, note);
            MfgStockLedger.recalcLotStock(delegator, lot);
            released += reserved;
        }
        return released;
    }

    /**
     * Gives back every reservation of one sales order.
     *
     * @return the released quantity
     */
    static double releaseSalesOrder(Delegator delegator, String salesOrderId, String operatorName,
            String note) throws GenericEntityException {
        double released = 0.0;
        for (GenericValue item : EntityQuery.use(delegator).from("MfgSalesOrderItem")
                .where("salesOrderId", salesOrderId).queryList()) {
            released += releaseOrderLine(delegator, salesOrderId, item.getLong("lineSeq").longValue(),
                    operatorName, note);
        }
        return released;
    }

    /**
     * The finished goods lots of one model, oldest first.
     *
     * @param delegator the delegator
     * @param productCode the model
     * @return the lots that hold that model
     * @throws GenericEntityException when they cannot be read
     */
    static List<GenericValue> productLotsOf(Delegator delegator, String productCode)
            throws GenericEntityException {
        List<GenericValue> lots = new ArrayList<>();
        for (GenericValue lot : EntityQuery.use(delegator).from("MfgMaterialLot")
                .where("componentCode", productCode)
                .orderBy("receivedDate", "lotId").queryList()) {
            if (MfgStockLedger.isProductLot(lot)) {
                lots.add(lot);
            }
        }
        return lots;
    }

    /**
     * Every finished goods lot, in no particular order.
     */
    static List<GenericValue> productLots(Delegator delegator) throws GenericEntityException {
        List<GenericValue> lots = new ArrayList<>();
        for (GenericValue lot : EntityQuery.use(delegator).from("MfgMaterialLot").queryList()) {
            if (MfgStockLedger.isProductLot(lot)) {
                lots.add(lot);
            }
        }
        return lots;
    }

    /**
     * What one work order will still deliver: its planned quantity minus the units already booked
     * into stock.
     *
     * @param delegator the delegator
     * @param workOrderId the work order, may be {@code null}
     * @return the quantity still to come, {@code 0} without a work order
     * @throws GenericEntityException when it cannot be read
     */
    static double inProductionOf(Delegator delegator, String workOrderId) throws GenericEntityException {
        if (workOrderId == null) {
            return 0.0;
        }
        GenericValue workOrder = EntityQuery.use(delegator).from("MfgWorkOrder")
                .where("workOrderId", workOrderId).queryOne();
        if (workOrder == null) {
            return 0.0;
        }
        double planned = MfgSupport.numberOrZero(workOrder.get("plannedQty"));
        double booked = 0.0;
        for (GenericValue movement : MfgStockLedger.movementsOf(delegator, MfgStockLedger.productLotId(workOrderId))) {
            if (MfgConstants.MOVE_RECEIPT.equals(movement.getString("movementTypeId"))) {
                booked += MfgSupport.numberOrZero(movement.get("quantityDiff"));
            }
        }
        double left = planned - booked;
        return left <= 0.0 ? 0.0 : MfgSupport.round2(left);
    }

    static String nextSalesOrderId(Delegator delegator, LocalDate today) throws GenericEntityException {
        String prefix = MfgConstants.SO_PREFIX + today.format(MfgConstants.DAY) + "-";
        int max = 0;
        for (GenericValue order : EntityQuery.use(delegator).from("MfgSalesOrder").queryList()) {
            String id = order.getString("salesOrderId");
            if (id != null && id.startsWith(prefix)) {
                max = Math.max(max, MfgSupport.parseTrailingNumber(id.substring(prefix.length())));
            }
        }
        return prefix + String.format(Locale.ROOT, "%02d", Integer.valueOf(max + 1));
    }

    /**
     * Tells whether a status is one of the sales order statuses.
     *
     * @param statusId the status to test
     * @return {@code true} when it is known
     */
    static boolean isKnownSoStatus(String statusId) {
        for (String status : MfgConstants.SO_STATUSES) {
            if (status.equals(statusId)) {
                return true;
            }
        }
        return false;
    }
}
