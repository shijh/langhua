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
 * Deliveries: which serial numbers went out with which delivery note, and what that takes off the
 * stock.
 *
 * <p>Only a completed unit can ship, and the finished goods lot of its work order is what the
 * delivery draws from; the reservation the sales order holds on that lot has to be released in the
 * same step, otherwise the shop would see stock as available twice. Moved out of
 * {@code MfgServices} in Step 169.
 */
public final class MfgDeliveryServices {

    private MfgDeliveryServices() {
    }

    /**
     * Creates a delivery order and binds the shipped serial numbers to it. Either {@code sns} is
     * given (whitespace or comma separated), or {@code workOrderId} + {@code qty} are used to pick
     * the next undelivered units of that work order.
     *
     * @param dctx the dispatch context
     * @param context service context, see {@code servicedef/services.xml#mfgDeliverySave}
     * @return the service result containing {@code deliveryId} and {@code snCount}
     */
    public static Map<String, Object> deliverySave(DispatchContext dctx, Map<String, ? extends Object> context) {
        // shipping is done by the warehouse, and sales may send the goods themselves
        String denied = MfgServices.permissionProblem(dctx, context, MfgServices.PERM_SALES, MfgServices.PERM_STOCK);
        if (denied != null) {
            return ServiceUtil.returnError(denied);
        }
        Delegator delegator = dctx.getDelegator();
        String customerName = MfgSupport.toText(context.get("customerName"));
        String salesOrderId = MfgSupport.toText(context.get("salesOrderId"));
        if (customerName == null) {
            return ServiceUtil.returnError("customerName is required");
        }
        try {
            List<String> sns = MfgCsv.parseSnList(MfgSupport.toText(context.get("sns")));
            String workOrderId = MfgSupport.toText(context.get("workOrderId"));
            long qty = MfgSupport.toLong(context.get("qty"), 0);
            if (sns.isEmpty()) {
                if (workOrderId == null) {
                    return ServiceUtil.returnError("sns or workOrderId is required");
                }
                if (qty <= 0) {
                    return ServiceUtil.returnError("qty must be a positive number when workOrderId is used");
                }
                List<GenericValue> units = EntityQuery.use(delegator).from("MfgSn")
                        .where("workOrderId", workOrderId).orderBy("sn").queryList();
                for (GenericValue unit : units) {
                    if (sns.size() >= qty) {
                        break;
                    }
                    String sn = unit.getString("sn");
                    if (!isDelivered(delegator, sn)) {
                        sns.add(sn);
                    }
                }
                if (sns.isEmpty()) {
                    return ServiceUtil.returnError("No undelivered serial number left on [" + workOrderId + "]");
                }
            }
            Map<String, Integer> toShip = new LinkedHashMap<>();
            for (String sn : sns) {
                GenericValue unit = EntityQuery.use(delegator).from("MfgSn").where("sn", sn).queryOne();
                if (unit == null) {
                    return ServiceUtil.returnError("Unknown serial number [" + sn + "]");
                }
                if (isDelivered(delegator, sn)) {
                    return ServiceUtil.returnError("Serial number [" + sn + "] has already been delivered");
                }
                if (!MfgServices.SN_COMPLETED.equals(unit.getString("statusId"))) {
                    return ServiceUtil.returnError("Serial number [" + sn + "] is not in the finished goods stock yet,"
                            + " complete work order [" + unit.getString("workOrderId") + "] first");
                }
                String unitOrder = unit.getString("workOrderId");
                GenericValue unitLot = EntityQuery.use(delegator).from("MfgMaterialLot")
                        .where("lotId", MfgStockLedger.productLotId(unitOrder)).queryOne();
                if (unitLot == null) {
                    return ServiceUtil.returnError("Work order [" + unitOrder + "] has no finished goods lot yet,"
                            + " complete it first");
                }
                Integer pending = toShip.get(unitOrder);
                toShip.put(unitOrder, Integer.valueOf(pending == null ? 1 : pending.intValue() + 1));
            }
            for (Map.Entry<String, Integer> entry : toShip.entrySet()) {
                GenericValue unitLot = EntityQuery.use(delegator).from("MfgMaterialLot")
                        .where("lotId", MfgStockLedger.productLotId(entry.getKey())).queryOne();
                double available = unitLot == null ? 0.0 : MfgSupport.numberOrZero(unitLot.get("availableQty"));
                // what this very order reserved on the lot is not somebody else taking it
                if (salesOrderId != null) {
                    available += orderReservedOnLot(delegator, MfgStockLedger.productLotId(entry.getKey()), salesOrderId);
                }
                if (available + 0.0001 < entry.getValue().intValue()) {
                    return ServiceUtil.returnError("Only " + MfgSupport.formatQty(available) + " unit(s) left in the finished"
                            + " goods lot [" + MfgStockLedger.productLotId(entry.getKey()) + "], cannot ship "
                            + entry.getValue().intValue());
                }
            }
            Map<String, Integer> shipByModel = new LinkedHashMap<>();
            for (Map.Entry<String, Integer> entry : toShip.entrySet()) {
                GenericValue unitOrder = EntityQuery.use(delegator).from("MfgWorkOrder")
                        .where("workOrderId", entry.getKey()).queryOne();
                String model = unitOrder == null ? null : unitOrder.getString("productCode");
                if (model == null) {
                    continue;
                }
                Integer current = shipByModel.get(model);
                shipByModel.put(model, Integer.valueOf((current == null ? 0 : current.intValue())
                        + entry.getValue().intValue()));
            }
            if (salesOrderId != null) {
                GenericValue salesOrder = EntityQuery.use(delegator).from("MfgSalesOrder")
                        .where("salesOrderId", salesOrderId).queryOne();
                if (salesOrder == null) {
                    return ServiceUtil.returnError("Unknown sales order [" + salesOrderId + "]");
                }
                if (MfgServices.SO_CLOSED.equals(salesOrder.getString("statusId"))
                        || MfgServices.SO_CANCELLED.equals(salesOrder.getString("statusId"))) {
                    return ServiceUtil.returnError("Sales order [" + salesOrderId + "] is "
                            + salesOrder.getString("statusId"));
                }
                for (Map.Entry<String, Integer> entry : shipByModel.entrySet()) {
                    double shippable = 0.0;
                    for (GenericValue item : EntityQuery.use(delegator).from("MfgSalesOrderItem")
                            .where("salesOrderId", salesOrderId).queryList()) {
                        if (!entry.getKey().equals(item.getString("productCode"))) {
                            continue;
                        }
                        long lineSeq = item.getLong("lineSeq").longValue();
                        double lineReserved = MfgSalesOrderServices.orderLineReserved(delegator, salesOrderId, lineSeq);
                        // what the order already locked counts as shippable, its open quantity too
                        shippable += lineReserved + MfgSalesOrderServices.openQtyOf(MfgSupport.numberOrZero(item.get("qty")), lineReserved,
                                MfgSupport.numberOrZero(item.get("shippedQty")));
                    }
                    if (shippable + 0.0001 < entry.getValue().intValue()) {
                        return ServiceUtil.returnError("Sales order [" + salesOrderId + "] only has "
                                + MfgSupport.formatQty(shippable) + " unit(s) of [" + entry.getKey() + "], cannot ship "
                                + entry.getValue().intValue());
                    }
                }
            }

            String deliveryId = nextDeliveryId(delegator, LocalDate.now());
            GenericValue delivery = delegator.makeValue("MfgDelivery");
            delivery.set("deliveryId", deliveryId);
            delivery.set("customerName", customerName);
            delivery.set("customerPo", MfgSupport.toText(context.get("customerPo")));
            Object deliveryDate = MfgSupport.parseDate(context.get("deliveryDate"));
            delivery.set("deliveryDate", deliveryDate == null
                    ? java.sql.Date.valueOf(LocalDate.now()) : deliveryDate);
            delivery.set("carrier", MfgSupport.toText(context.get("carrier")));
            delivery.set("trackingNo", MfgSupport.toText(context.get("trackingNo")));
            delivery.set("workOrderId", workOrderId);
            delivery.set("note", MfgSupport.toText(context.get("note")));
            delegator.create(delivery);

            for (String sn : sns) {
                GenericValue item = delegator.makeValue("MfgDeliverySn");
                item.set("deliveryId", deliveryId);
                item.set("sn", sn);
                delegator.create(item);
                GenericValue unit = EntityQuery.use(delegator).from("MfgSn").where("sn", sn).queryOne();
                if (unit != null) {
                    unit.set("statusId", MfgServices.SN_SHIPPED);
                    delegator.store(unit);
                }
            }

            // the goods leave the finished stock of the work order that built them
            String operator = MfgSupport.toText(context.get("operatorName"));
            java.sql.Date shippedDay = delivery.getDate("deliveryDate");
            double issuedQty = 0.0;
            for (Map.Entry<String, Integer> entry : toShip.entrySet()) {
                GenericValue unitLot = EntityQuery.use(delegator).from("MfgMaterialLot")
                        .where("lotId", MfgStockLedger.productLotId(entry.getKey())).queryOne();
                if (unitLot == null) {
                    continue;
                }
                double units = entry.getValue().intValue();
                MfgStockLedger.recordMovement(delegator, unitLot, MfgServices.MOVE_ISSUE, -units, unitLot.get("unitPrice"),
                        entry.getKey(), null, deliveryId, operator,
                        shippedDay == null ? null
                                : Timestamp.valueOf(shippedDay.toLocalDate().atStartOfDay()),
                        "出货 " + deliveryId);
                MfgStockLedger.recalcLotStock(delegator, unitLot);
                issuedQty += units;
            }

            // an order delivery writes the units off its lines and gives their reservations back
            if (salesOrderId != null) {
                shipAgainstSalesOrder(delegator, salesOrderId, shipByModel, operator, deliveryId);
            }

            Map<String, Object> result = ServiceUtil.returnSuccess();
            result.put("deliveryId", deliveryId);
            result.put("snCount", String.valueOf(sns.size()));
            result.put("issuedQty", Double.valueOf(MfgSupport.round2(issuedQty)));
            result.put("productLotCount", Integer.valueOf(toShip.size()));
            if (salesOrderId != null) {
                result.put("salesOrderId", salesOrderId);
            }
            return result;
        } catch (GenericEntityException e) {
            return ServiceUtil.returnError("Could not create the delivery: " + e.getMessage());
        }
    }

    /**
     * Lists delivery orders, newest first, with the number of shipped units.
     *
     * @param dctx the dispatch context
     * @param context service context, see {@code servicedef/services.xml#mfgDeliveryList}
     * @return the service result containing {@code deliveries}
     */
    public static Map<String, Object> deliveryList(DispatchContext dctx, Map<String, ? extends Object> context) {
        Delegator delegator = dctx.getDelegator();
        String customerName = MfgSupport.toText(context.get("customerName"));
        try {
            EntityQuery query = EntityQuery.use(delegator).from("MfgDelivery");
            if (customerName != null) {
                query = query.where("customerName", customerName);
            }
            List<GenericValue> deliveries = query.orderBy("-deliveryDate").queryList();
            List<Map<String, Object>> list = new ArrayList<>(deliveries.size());
            Map<String, List<String>> serials = new LinkedHashMap<>();
            for (GenericValue delivery : deliveries) {
                Map<String, Object> map = MfgServices.toDeliveryMap(delivery);
                List<GenericValue> items = EntityQuery.use(delegator).from("MfgDeliverySn")
                        .where("deliveryId", delivery.getString("deliveryId")).orderBy("sn").queryList();
                map.put("snCount", Integer.valueOf(items.size()));
                List<String> sns = new ArrayList<>(items.size());
                for (GenericValue item : items) {
                    sns.add(item.getString("sn"));
                }
                serials.put(delivery.getString("deliveryId"), sns);
                list.add(map);
            }
            Map<String, Object> result = ServiceUtil.returnSuccess();
            MfgPaging.putPage(result, "deliveries",
                    MfgPaging.filterByKeyword(list, MfgSupport.toText(context.get("search")), "deliveryId", "customerName",
                            "customerPo", "carrier", "trackingNo"),
                    MfgPaging.pageOf(context), MfgPaging.pageSizeOf(context), MfgSupport.toText(context.get("search")));
            // the list unfolds the shipped serial numbers of the visible page in place
            for (Map<String, Object> row : MfgPaging.rowsOf(result, "deliveries")) {
                List<String> sns = serials.get((String) row.get("deliveryId"));
                row.put("snList", sns == null ? new ArrayList<String>() : sns);
            }
            result.put("customerName", customerName);
            return result;
        } catch (GenericEntityException e) {
            return ServiceUtil.returnError("Could not read the deliveries: " + e.getMessage());
        }
    }

    /**
     * Returns one delivery order with its shipped units.
     *
     * @param dctx the dispatch context
     * @param context service context, see {@code servicedef/services.xml#mfgDeliveryDetail}
     * @return the service result containing {@code delivery} and {@code units}
     */
    public static Map<String, Object> deliveryDetail(DispatchContext dctx, Map<String, ? extends Object> context) {
        Delegator delegator = dctx.getDelegator();
        String deliveryId = MfgSupport.toText(context.get("deliveryId"));
        Map<String, Object> empty = ServiceUtil.returnSuccess();
        empty.put("units", new ArrayList<Map<String, Object>>());
        if (deliveryId == null) {
            return empty;
        }
        try {
            GenericValue delivery = EntityQuery.use(delegator).from("MfgDelivery")
                    .where("deliveryId", deliveryId).queryOne();
            if (delivery == null) {
                return empty;
            }
            List<GenericValue> items = EntityQuery.use(delegator).from("MfgDeliverySn")
                    .where("deliveryId", deliveryId).orderBy("sn").queryList();
            List<Map<String, Object>> units = new ArrayList<>(items.size());
            for (GenericValue item : items) {
                String sn = item.getString("sn");
                GenericValue unit = EntityQuery.use(delegator).from("MfgSn").where("sn", sn).queryOne();
                Map<String, Object> map = new LinkedHashMap<>();
                map.put("sn", sn);
                map.put("productCode", unit == null ? null : unit.getString("productCode"));
                map.put("workOrderId", unit == null ? null : unit.getString("workOrderId"));
                map.put("statusId", unit == null ? null : unit.getString("statusId"));
                units.add(map);
            }
            Map<String, Object> result = ServiceUtil.returnSuccess();
            result.put("delivery", MfgServices.toDeliveryMap(delivery));
            result.put("units", units);
            return result;
        } catch (GenericEntityException e) {
            return ServiceUtil.returnError("Could not read the delivery: " + e.getMessage());
        }
    }

    static boolean isDelivered(Delegator delegator, String sn) throws GenericEntityException {
        return EntityQuery.use(delegator).from("MfgDeliverySn").where("sn", sn).queryOne() != null;
    }

    static String formatDeliveryId(LocalDate date, int sequence) {
        return "DO-" + date.format(MfgServices.DAY) + "-" + String.format(Locale.ROOT, "%02d", sequence);
    }

    static String nextDeliveryId(Delegator delegator, LocalDate today) throws GenericEntityException {
        String prefix = "DO-" + today.format(MfgServices.DAY) + "-";
        List<GenericValue> existing = EntityQuery.use(delegator).from("MfgDelivery").queryList();
        int max = 0;
        for (GenericValue delivery : existing) {
            String id = delivery.getString("deliveryId");
            if (id != null && id.startsWith(prefix)) {
                max = Math.max(max, MfgServices.parseTrailingNumber(id.substring(prefix.length())));
            }
        }
        return formatDeliveryId(today, max + 1);
    }

    /**
     * What one sales order has reserved on one lot right now.
     *
     * @param delegator the delegator
     * @param lotId the finished goods lot
     * @param salesOrderId the order
     * @return the reserved quantity, {@code 0} when nothing is reserved
     * @throws GenericEntityException when the ledger cannot be read
     */
    static double orderReservedOnLot(Delegator delegator, String lotId, String salesOrderId)
            throws GenericEntityException {
        double reserved = 0.0;
        for (GenericValue movement : MfgStockLedger.movementsOf(delegator, lotId)) {
            if (!salesOrderId.equals(movement.getString("salesOrderId"))) {
                continue;
            }
            reserved += MfgSupport.numberOrZero(movement.get("reservedDiff"));
        }
        return reserved <= 0.0 ? 0.0 : MfgSupport.round2(reserved);
    }

    /**
     * Writes units off the lines of a sales order and gives their reservations back, one model at a
     * time; the order flips to shipped once nothing is owed any more.
     *
     * @param delegator the delegator
     * @param salesOrderId the order the goods were sold on
     * @param byModel how many units of each model the delivery takes
     * @param operatorName who did it
     * @param deliveryId the delivery that was just created
     * @throws GenericEntityException when the order cannot be written
     */
    static void shipAgainstSalesOrder(Delegator delegator, String salesOrderId,
            Map<String, Integer> byModel, String operatorName, String deliveryId)
            throws GenericEntityException {
        for (Map.Entry<String, Integer> entry : byModel.entrySet()) {
            String model = entry.getKey();
            double remaining = entry.getValue().intValue();
            for (GenericValue item : EntityQuery.use(delegator).from("MfgSalesOrderItem")
                    .where("salesOrderId", salesOrderId).orderBy("lineSeq").queryList()) {
                if (remaining <= 0.0001) {
                    break;
                }
                if (!model.equals(item.getString("productCode"))) {
                    continue;
                }
                long lineSeq = item.getLong("lineSeq").longValue();
                double reserved = MfgSalesOrderServices.orderLineReserved(delegator, salesOrderId, lineSeq);
                double shippable = reserved + MfgSalesOrderServices.openQtyOf(MfgSupport.numberOrZero(item.get("qty")), reserved,
                        MfgSupport.numberOrZero(item.get("shippedQty")));
                if (shippable <= 0.0001) {
                    continue;
                }
                double take = Math.min(shippable, remaining);
                item.set("shippedQty", Double.valueOf(MfgSupport.round2(MfgSupport.numberOrZero(item.get("shippedQty")) + take)));
                delegator.store(item);
                releaseOrderLineUnit(delegator, salesOrderId, lineSeq, model, take, operatorName,
                        "出货 " + deliveryId + " 释放预留");
                remaining = MfgSupport.round2(remaining - take);
            }
        }
        boolean done = true;
        for (GenericValue item : EntityQuery.use(delegator).from("MfgSalesOrderItem")
                .where("salesOrderId", salesOrderId).queryList()) {
            if (MfgSupport.numberOrZero(item.get("shippedQty")) + 0.0001 < MfgSupport.numberOrZero(item.get("qty"))) {
                done = false;
                break;
            }
        }
        GenericValue order = EntityQuery.use(delegator).from("MfgSalesOrder")
                .where("salesOrderId", salesOrderId).queryOne();
        if (order != null && done) {
            order.set("statusId", MfgServices.SO_SHIPPED);
            delegator.store(order);
        }
    }

    /**
     * Gives back at most {@code qty} reserved units of one order line, taken from the finished goods
     * lots that hold that reservation.
     *
     * @return how much was released
     */
    static double releaseOrderLineUnit(Delegator delegator, String salesOrderId, long lineSeq,
            String productCode, double qty, String operatorName, String note) throws GenericEntityException {
        double remaining = qty;
        for (GenericValue lot : MfgSalesOrderServices.productLotsOf(delegator, productCode)) {
            if (remaining <= 0.0001) {
                break;
            }
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
            double release = Math.min(remaining, reserved);
            MfgSalesOrderServices.recordOrderReservation(delegator, lot, MfgServices.MOVE_RELEASE, -release, salesOrderId, lineSeq,
                    operatorName, note);
            MfgStockLedger.recalcLotStock(delegator, lot);
            remaining = MfgSupport.round2(remaining - release);
        }
        return MfgSupport.round2(qty - remaining);
    }
}
