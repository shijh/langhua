package org.langhua.ofbiz.mfg;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.apache.ofbiz.entity.Delegator;
import org.apache.ofbiz.entity.GenericEntityException;
import org.apache.ofbiz.entity.GenericValue;
import org.apache.ofbiz.entity.util.EntityQuery;
import org.apache.ofbiz.service.DispatchContext;
import org.apache.ofbiz.service.ServiceUtil;

/**
 * The shop floor: work orders from creation to completion, the material that goes into them, what
 * the operators report, the serial numbers and defects, the outside processing fees, and the
 * traceability that ties a lot back to the units it ended up in.
 *
 * <p>A work order freezes the BOM version it was started with, so a later change of the model does
 * not rewrite history, and it reserves material as soon as it is created: without that, two orders
 * would both believe the same lot is theirs. Reported quantities are what kitting, completion and
 * the finished goods lot are all derived from.
 *
 * <p>The row builders of other areas ({@code toClaimMap}, {@code toDeliveryMap}, {@code toLotMap},
 * the shortage lines, the sales order arithmetic) stay in {@code MfgServices}: the lot, delivery,
 * claim and sales pages need them too, and this class only reads them. Moved out of
 * {@code MfgServices} in Step 168.
 */
public final class MfgWorkOrderServices {

    private MfgWorkOrderServices() {
    }

    /**
     * Creates a work order. The identifier is {@code WO-yyMMdd-NNN}.
     *
     * @param dctx the dispatch context
     * @param context service context, see {@code servicedef/services.xml#mfgWorkOrderCreate}
     * @return the service result containing {@code workOrderId}
     */
    public static Map<String, Object> workOrderCreate(DispatchContext dctx, Map<String, ? extends Object> context) {
        // sales planning creates work orders too, so the sales area is accepted here
        String denied = MfgServices.permissionProblem(dctx, context, MfgServices.PERM_PRODUCTION, MfgServices.PERM_SALES);
        if (denied != null) {
            return ServiceUtil.returnError(denied);
        }
        Delegator delegator = dctx.getDelegator();
        String productCode = MfgSupport.toText(context.get("productCode"));
        if (productCode == null) {
            return ServiceUtil.returnError("productCode is required");
        }
        long plannedQty = MfgSupport.toLong(context.get("plannedQty"), 0);
        if (plannedQty <= 0) {
            return ServiceUtil.returnError("plannedQty must be a positive number");
        }
        try {
            if (EntityQuery.use(delegator).from("MfgProduct").where("productCode", productCode).queryOne() == null) {
                return ServiceUtil.returnError("Unknown product model [" + productCode + "], create it first");
            }
            LocalDate today = LocalDate.now();
            String workOrderId = nextWorkOrderId(delegator, today);
            GenericValue order = delegator.makeValue("MfgWorkOrder");
            order.set("workOrderId", workOrderId);
            order.set("productCode", productCode);
            order.set("plannedQty", Long.valueOf(plannedQty));
            order.set("statusId", MfgServices.WO_CREATED);
            order.set("dueDate", MfgSupport.parseDate(context.get("dueDate")));
            order.set("customerName", MfgSupport.toText(context.get("customerName")));
            order.set("smtVendor", MfgSupport.toText(context.get("smtVendor")));
            order.set("bomVersionId", MfgProductServices.currentBomVersionId(delegator, productCode));
            order.set("note", MfgSupport.toText(context.get("note")));
            delegator.create(order);

            // reserve what the warehouse can cover, the rest stays a shortage on the kitting page
            Map<String, Object> reserveContext = new HashMap<>();
            reserveContext.put("workOrderId", workOrderId);
            reserveContext.put("operatorName", MfgSupport.toText(context.get("customerName")));
            reserveContext.put("userLogin", context.get("userLogin"));
            Map<String, Object> reservation = reserveMaterial(dctx, reserveContext);

            Map<String, Object> result = ServiceUtil.returnSuccess();
            result.put("workOrderId", workOrderId);
            if (!ServiceUtil.isError(reservation)) {
                result.put("reservedQty", reservation.get("reservedQty"));
                result.put("shortQty", reservation.get("shortQty"));
            }
            return result;
        } catch (GenericEntityException e) {
            return ServiceUtil.returnError("Could not create the work order: " + e.getMessage());
        }
    }

    /**
     * Lists work orders, newest first.
     *
     * @param dctx the dispatch context
     * @param context service context, see {@code servicedef/services.xml#mfgWorkOrderList}
     * @return the service result containing {@code workOrders}
     */
    public static Map<String, Object> workOrderList(DispatchContext dctx, Map<String, ? extends Object> context) {
        Delegator delegator = dctx.getDelegator();
        String statusId = MfgSupport.toText(context.get("statusId"));
        try {
            EntityQuery query = EntityQuery.use(delegator).from("MfgWorkOrder");
            if (statusId != null) {
                query = query.where("statusId", statusId);
            }
            List<GenericValue> orders = query.orderBy("-createdStamp").queryList();
            List<Map<String, Object>> list = new ArrayList<>(orders.size());
            Map<String, GenericValue> ordersById = new LinkedHashMap<>();
            for (GenericValue order : orders) {
                Map<String, Object> map = MfgServices.toWorkOrderMap(order);
                map.put("reportedHours", Double.valueOf(sumHours(delegator, order.getString("workOrderId"))));
                list.add(map);
                ordersById.put(order.getString("workOrderId"), order);
            }
            Map<String, Object> result = ServiceUtil.returnSuccess();
            MfgPaging.putPage(result, "workOrders",
                    MfgPaging.filterByKeyword(list, MfgSupport.toText(context.get("search")), "workOrderId", "productCode",
                            "customerName", "smtVendor", "statusId"),
                    MfgPaging.pageOf(context), MfgPaging.pageSizeOf(context), MfgSupport.toText(context.get("search")));
            // the list unfolds its rows in place: the kitting gaps and the progress of the visible
            // page only, so a long list does not cost a query per order
            boolean showCost = MfgServices.can(dctx, (GenericValue) context.get("userLogin"), MfgServices.PERM_COST);
            for (Map<String, Object> row : MfgPaging.rowsOf(result, "workOrders")) {
                String workOrderId = (String) row.get("workOrderId");
                GenericValue order = ordersById.get(workOrderId);
                if (order == null) {
                    continue;
                }
                List<Map<String, Object>> shortages = new ArrayList<>();
                for (Map<String, Object> line : kittingLines(delegator, order)) {
                    Object shortage = line.get("shortage");
                    if (shortage instanceof Number && ((Number) shortage).doubleValue() > 0.0) {
                        shortages.add(line);
                    }
                }
                row.put("shortages", shortages);
                row.put("showCost", Boolean.valueOf(showCost));
                List<GenericValue> routings = EntityQuery.use(delegator).from("MfgRouting")
                        .where("productCode", order.getString("productCode")).orderBy("opSeq").queryList();
                List<GenericValue> reports = EntityQuery.use(delegator).from("MfgReport")
                        .where("workOrderId", workOrderId).queryList();
                row.put("operations", toOperationMaps(routings, reports));
            }
            return result;
        } catch (GenericEntityException e) {
            return ServiceUtil.returnError("Could not read the work orders: " + e.getMessage());
        }
    }

    /**
     * Returns one work order with its operation progress, units and defects.
     *
     * @param dctx the dispatch context
     * @param context service context, see {@code servicedef/services.xml#mfgWorkOrderDetail}
     * @return the service result containing {@code workOrder}, {@code operations}, {@code sns}, {@code defects}
     */
    public static Map<String, Object> workOrderDetail(DispatchContext dctx, Map<String, ? extends Object> context) {
        Delegator delegator = dctx.getDelegator();
        String workOrderId = MfgSupport.toText(context.get("workOrderId"));
        if (workOrderId == null) {
            Map<String, Object> empty = ServiceUtil.returnSuccess();
            empty.put("operations", new ArrayList<Map<String, Object>>());
            empty.put("sns", new ArrayList<Map<String, Object>>());
            empty.put("defects", new ArrayList<Map<String, Object>>());
            return empty;
        }
        try {
            GenericValue order = EntityQuery.use(delegator).from("MfgWorkOrder")
                    .where("workOrderId", workOrderId).queryOne();
            if (order == null) {
                Map<String, Object> empty = ServiceUtil.returnSuccess();
                empty.put("operations", new ArrayList<Map<String, Object>>());
                empty.put("sns", new ArrayList<Map<String, Object>>());
                empty.put("defects", new ArrayList<Map<String, Object>>());
                return empty;
            }
            List<GenericValue> reports = EntityQuery.use(delegator).from("MfgReport")
                    .where("workOrderId", workOrderId).queryList();
            List<GenericValue> defects = EntityQuery.use(delegator).from("MfgDefect")
                    .where("workOrderId", workOrderId).queryList();
            List<GenericValue> units = EntityQuery.use(delegator).from("MfgSn")
                    .where("workOrderId", workOrderId).orderBy("sn").queryList();
            List<GenericValue> routings = EntityQuery.use(delegator).from("MfgRouting")
                    .where("productCode", order.getString("productCode")).orderBy("opSeq").queryList();

            Map<String, Object> result = ServiceUtil.returnSuccess();
            result.put("workOrder", MfgServices.toWorkOrderMap(order));
            result.put("operations", toOperationMaps(routings, reports));
            result.put("sns", toSnMaps(units));
            result.put("defects", toDefectMaps(defects));
            result.put("reportedHours", Double.valueOf(sumHours(reports)));
            result.put("totalQtyOk", Long.valueOf(sumQty(reports, "qtyOk")));
            result.put("totalQtyNg", Long.valueOf(sumQty(reports, "qtyNg")));

            List<GenericValue> tests = EntityQuery.use(delegator).from("MfgTestResult")
                    .where("workOrderId", workOrderId).queryList();
            Set<String> testedSns = new LinkedHashSet<>();
            Set<String> failedSns = new LinkedHashSet<>();
            for (GenericValue test : tests) {
                String testSn = test.getString("sn");
                if (testSn == null) {
                    continue;
                }
                testedSns.add(testSn);
                if ("N".equals(test.getString("passFail"))) {
                    failedSns.add(testSn);
                }
            }
            result.put("testedSnCount", Integer.valueOf(testedSns.size()));
            result.put("failedSnCount", Integer.valueOf(failedSns.size()));
            double testYield = testedSns.isEmpty() ? 0.0
                    : (testedSns.size() - failedSns.size()) * 100.0 / testedSns.size();
            result.put("testYield", Double.valueOf(testYield));

            GenericValue vendor = null;
            String smtVendorId = order.getString("smtVendorId");
            if (smtVendorId != null) {
                vendor = EntityQuery.use(delegator).from("MfgVendor")
                        .where("vendorId", smtVendorId).queryOne();
            }
            if (vendor == null) {
                String smtVendor = order.getString("smtVendor");
                if (smtVendor != null) {
                    vendor = EntityQuery.use(delegator).from("MfgVendor")
                            .where("vendorName", smtVendor).queryOne();
                }
            }
            result.put("vendor", vendor == null ? null : toVendorMap(vendor));
            result.put("vendors", MfgServices.vendorMaps(delegator));

            // the finished goods lot of this order, once it was completed
            GenericValue productLot = EntityQuery.use(delegator).from("MfgMaterialLot")
                    .where("lotId", MfgStockLedger.productLotId(workOrderId)).queryOne();
            if (productLot != null) {
                MfgStockLedger.ensureLotLedger(delegator, productLot);
            }
            result.put("productLot", productLot == null ? null : MfgServices.toLotMap(productLot, null));
            return result;
        } catch (GenericEntityException e) {
            return ServiceUtil.returnError("Could not read the work order: " + e.getMessage());
        }
    }

    /**
     * Records reported labour for one operation.
     *
     * @param dctx the dispatch context
     * @param context service context, see {@code servicedef/services.xml#mfgReportCreate}
     * @return the service result containing {@code reportId}
     */
    public static Map<String, Object> reportCreate(DispatchContext dctx, Map<String, ? extends Object> context) {
        String denied = MfgServices.permissionProblem(dctx, context, MfgServices.PERM_PRODUCTION);
        if (denied != null) {
            return ServiceUtil.returnError(denied);
        }
        Delegator delegator = dctx.getDelegator();
        String workOrderId = MfgSupport.toText(context.get("workOrderId"));
        long opSeq = MfgSupport.toLong(context.get("opSeq"), 0);
        if (workOrderId == null) {
            return ServiceUtil.returnError("workOrderId is required");
        }
        if (opSeq <= 0) {
            return ServiceUtil.returnError("opSeq is required");
        }
        double workHours = MfgSupport.toDouble(context.get("workHours"), 0.0);
        long qtyOk = MfgSupport.toLong(context.get("qtyOk"), 0);
        long qtyNg = MfgSupport.toLong(context.get("qtyNg"), 0);
        if (workHours <= 0.0) {
            return ServiceUtil.returnError("workHours must be greater than zero");
        }
        if (qtyOk <= 0 && qtyNg <= 0) {
            return ServiceUtil.returnError("Report a positive qtyOk or qtyNg");
        }
        try {
            GenericValue order = EntityQuery.use(delegator).from("MfgWorkOrder")
                    .where("workOrderId", workOrderId).queryOne();
            if (order == null) {
                return ServiceUtil.returnError("Unknown work order [" + workOrderId + "]");
            }
            GenericValue report = delegator.makeValue("MfgReport");
            report.set("reportId", delegator.getNextSeqId("MfgReport"));
            report.set("workOrderId", workOrderId);
            report.set("opSeq", Long.valueOf(opSeq));
            report.set("sn", MfgSupport.toText(context.get("sn")));
            report.set("operatorName", MfgSupport.toText(context.get("operatorName")));
            report.set("workHours", Double.valueOf(workHours));
            report.set("qtyOk", Long.valueOf(qtyOk));
            report.set("qtyNg", Long.valueOf(qtyNg));
            report.set("reportDate", new Timestamp(System.currentTimeMillis()));
            report.set("note", MfgSupport.toText(context.get("note")));
            delegator.create(report);

            if (!MfgServices.WO_CLOSED.equals(order.getString("statusId"))
                && !MfgServices.WO_COMPLETED.equals(order.getString("statusId"))) {
                order.set("statusId", MfgServices.WO_IN_PROGRESS);
                delegator.store(order);
            }

            Map<String, Object> result = ServiceUtil.returnSuccess();
            result.put("reportId", report.getString("reportId"));
            return result;
        } catch (GenericEntityException e) {
            return ServiceUtil.returnError("Could not store the report: " + e.getMessage());
        }
    }

    /**
     * Generates and stores {@code qty} serial numbers for a work order using the pattern
     * {@code prefix-yyMMdd-NNNN}.
     *
     * @param dctx the dispatch context
     * @param context service context, see {@code servicedef/services.xml#mfgSnRegister}
     * @return the service result containing {@code sns}, {@code firstSn} and {@code lastSn}
     */
    public static Map<String, Object> snRegister(DispatchContext dctx, Map<String, ? extends Object> context) {
        String denied = MfgServices.permissionProblem(dctx, context, MfgServices.PERM_PRODUCTION);
        if (denied != null) {
            return ServiceUtil.returnError(denied);
        }
        Delegator delegator = dctx.getDelegator();
        String workOrderId = MfgSupport.toText(context.get("workOrderId"));
        long qty = MfgSupport.toLong(context.get("qty"), 0);
        if (workOrderId == null) {
            return ServiceUtil.returnError("workOrderId is required");
        }
        if (qty <= 0) {
            return ServiceUtil.returnError("qty must be a positive number");
        }
        if (qty > 500) {
            return ServiceUtil.returnError("qty must not exceed 500 per registration");
        }
        try {
            GenericValue order = EntityQuery.use(delegator).from("MfgWorkOrder")
                    .where("workOrderId", workOrderId).queryOne();
            if (order == null) {
                return ServiceUtil.returnError("Unknown work order [" + workOrderId + "]");
            }
            String productCode = order.getString("productCode");
            GenericValue product = EntityQuery.use(delegator).from("MfgProduct")
                    .where("productCode", productCode).queryOne();
            String prefix = product == null ? productCode : product.getString("snPrefix");
            if (prefix == null || prefix.trim().isEmpty()) {
                prefix = productCode;
            }
            LocalDate today = LocalDate.now();
            int startSeq = nextSnSequence(delegator, prefix);
            List<String> created = new ArrayList<>();
            for (int i = 0; i < qty; i++) {
                String sn = formatSn(prefix, today, startSeq + i);
                GenericValue unit = delegator.makeValue("MfgSn");
                unit.set("sn", sn);
                unit.set("workOrderId", workOrderId);
                unit.set("productCode", productCode);
                unit.set("statusId", MfgServices.SN_IN_PROGRESS);
                delegator.create(unit);
                created.add(sn);
            }
            Map<String, Object> result = ServiceUtil.returnSuccess();
            result.put("sns", created);
            result.put("firstSn", created.get(0));
            result.put("lastSn", created.get(created.size() - 1));
            return result;
        } catch (GenericEntityException e) {
            return ServiceUtil.returnError("Could not register the serial numbers: " + e.getMessage());
        }
    }

    /**
     * Returns the full history of one serial number.
     *
     * @param dctx the dispatch context
     * @param context service context, see {@code servicedef/services.xml#mfgSnDetail}
     * @return the service result containing {@code snRecord}, {@code workOrder}, {@code reports},
     *         {@code defects}, {@code lots} and {@code replacements}
     */
    public static Map<String, Object> snDetail(DispatchContext dctx, Map<String, ? extends Object> context) {
        Delegator delegator = dctx.getDelegator();
        String sn = MfgSupport.toText(context.get("sn"));
        try {
            List<Map<String, Object>> empty = new ArrayList<>();
            if (sn == null) {
                Map<String, Object> result = ServiceUtil.returnSuccess();
                result.put("reports", empty);
                result.put("defects", empty);
                result.put("lots", empty);
                result.put("replacements", empty);
                return result;
            }
            GenericValue unit = EntityQuery.use(delegator).from("MfgSn").where("sn", sn).queryOne();
            if (unit == null) {
                Map<String, Object> result = ServiceUtil.returnSuccess();
                result.put("reports", empty);
                result.put("defects", empty);
                result.put("lots", empty);
                result.put("replacements", empty);
                return result;
            }
            String workOrderId = unit.getString("workOrderId");
            GenericValue order = EntityQuery.use(delegator).from("MfgWorkOrder")
                    .where("workOrderId", workOrderId).queryOne();

            List<GenericValue> reports = EntityQuery.use(delegator).from("MfgReport")
                    .where("workOrderId", workOrderId).orderBy("reportDate").queryList();
            List<GenericValue> unitReports = new ArrayList<>();
            for (GenericValue report : reports) {
                String reportSn = report.getString("sn");
                if (reportSn == null || reportSn.equals(sn)) {
                    unitReports.add(report);
                }
            }
            List<GenericValue> defects = EntityQuery.use(delegator).from("MfgDefect")
                    .where("workOrderId", workOrderId).orderBy("createdStamp").queryList();
            List<GenericValue> unitDefects = new ArrayList<>();
            for (GenericValue defect : defects) {
                String defectSn = defect.getString("sn");
                if (defectSn == null || defectSn.equals(sn)) {
                    unitDefects.add(defect);
                }
            }
            List<GenericValue> issues = EntityQuery.use(delegator).from("MfgMaterialIssue")
                    .where("workOrderId", workOrderId).orderBy("createdStamp").queryList();
            List<Map<String, Object>> lotMaps = new ArrayList<>(issues.size());
            for (GenericValue issue : issues) {
                GenericValue lot = EntityQuery.use(delegator).from("MfgMaterialLot")
                        .where("lotId", issue.getString("lotId")).queryOne();
                lotMaps.add(MfgServices.toIssueMap(issue, lot));
            }
            List<GenericValue> replacements = EntityQuery.use(delegator).from("MfgReplacement")
                    .where("sn", sn).orderBy("createdStamp").queryList();
            List<GenericValue> deliveryItems = EntityQuery.use(delegator).from("MfgDeliverySn")
                    .where("sn", sn).queryList();
            Map<String, Object> deliveryMap = null;
            if (!deliveryItems.isEmpty()) {
                GenericValue delivery = EntityQuery.use(delegator).from("MfgDelivery")
                        .where("deliveryId", deliveryItems.get(0).getString("deliveryId")).queryOne();
                if (delivery != null) {
                    deliveryMap = MfgServices.toDeliveryMap(delivery);
                }
            }
            List<GenericValue> tests = EntityQuery.use(delegator).from("MfgTestResult")
                    .where("sn", sn).orderBy("-testDate").queryList();
            List<GenericValue> claims = EntityQuery.use(delegator).from("MfgWarrantyClaim")
                    .where("sn", sn).orderBy("-createdStamp").queryList();

            Map<String, Object> result = ServiceUtil.returnSuccess();
            result.put("snRecord", MfgServices.toSnMap(unit));
            result.put("workOrder", order == null ? null : MfgServices.toWorkOrderMap(order));
            result.put("reports", toReportMaps(unitReports));
            result.put("defects", toDefectMaps(unitDefects));
            result.put("lots", lotMaps);
            result.put("replacements", MfgServices.toReplacementMaps(replacements));
            result.put("delivery", deliveryMap);
            result.put("tests", toTestResultMaps(tests));
            result.put("claims", toClaimMaps(claims));
            return result;
        } catch (GenericEntityException e) {
            return ServiceUtil.returnError("Could not read the serial number: " + e.getMessage());
        }
    }

    /**
     * Records one defect, optionally bound to a single unit.
     *
     * @param dctx the dispatch context
     * @param context service context, see {@code servicedef/services.xml#mfgDefectCreate}
     * @return the service result containing {@code defectId}
     */
    public static Map<String, Object> defectCreate(DispatchContext dctx, Map<String, ? extends Object> context) {
        String denied = MfgServices.permissionProblem(dctx, context, MfgServices.PERM_PRODUCTION);
        if (denied != null) {
            return ServiceUtil.returnError(denied);
        }
        Delegator delegator = dctx.getDelegator();
        String workOrderId = MfgSupport.toText(context.get("workOrderId"));
        String defectCode = MfgSupport.toText(context.get("defectCode"));
        if (workOrderId == null) {
            return ServiceUtil.returnError("workOrderId is required");
        }
        if (defectCode == null) {
            return ServiceUtil.returnError("defectCode is required");
        }
        long qty = MfgSupport.toLong(context.get("qty"), 1);
        if (qty <= 0) {
            return ServiceUtil.returnError("qty must be a positive number");
        }
        try {
            GenericValue defect = delegator.makeValue("MfgDefect");
            defect.set("defectId", delegator.getNextSeqId("MfgDefect"));
            defect.set("workOrderId", workOrderId);
            defect.set("sn", MfgSupport.toText(context.get("sn")));
            defect.set("opSeq", Long.valueOf(MfgSupport.toLong(context.get("opSeq"), 0)));
            defect.set("defectCode", defectCode);
            defect.set("qty", Long.valueOf(qty));
            String responsibility = MfgSupport.toText(context.get("responsibility"));
            defect.set("responsibility", responsibility == null ? "HOUSE" : responsibility);
            defect.set("repairHours", Double.valueOf(MfgSupport.toDouble(context.get("repairHours"), 0.0)));
            defect.set("note", MfgSupport.toText(context.get("note")));
            delegator.create(defect);

            Map<String, Object> result = ServiceUtil.returnSuccess();
            result.put("defectId", defect.getString("defectId"));
            return result;
        } catch (GenericEntityException e) {
            return ServiceUtil.returnError("Could not store the defect: " + e.getMessage());
        }
    }

    /**
     * Costs one work order: the standard cost of the model against what really happened, meaning the
     * lots issued times their purchase price plus the reported hours times the labour rate.
     *
     * @param dctx the dispatch context
     * @param context service context, see {@code servicedef/services.xml#mfgWorkOrderCost}
     * @return the service result with the standard and actual costs and their difference
     */
    public static Map<String, Object> workOrderCost(DispatchContext dctx, Map<String, ? extends Object> context) {
        // the page cannot fail for a role without the cost area: it renders a hint instead
        Map<String, Object> denied = MfgProductServices.costDenied(dctx, context);
        if (denied != null) {
            return denied;
        }
        return workOrderCostOf(dctx, context);
    }

    /**
     * Costs a work order without asking for the cost permission: completing a work order stores the
     * unit cost of the finished goods, so that number is needed even when the operator may not look
     * at cost figures.
     *
     * @param dctx the dispatch context
     * @param context service context with the {@code workOrderId}
     * @return the same structure as {@code mfgWorkOrderCost}
     */
    static Map<String, Object> workOrderCostOf(DispatchContext dctx, Map<String, ? extends Object> context) {
        Delegator delegator = dctx.getDelegator();
        String workOrderId = MfgSupport.toText(context.get("workOrderId"));
        Map<String, Object> result = ServiceUtil.returnSuccess();
        result.put("workOrderId", workOrderId);
        if (workOrderId == null) {
            return result;
        }
        try {
            GenericValue order = EntityQuery.use(delegator).from("MfgWorkOrder")
                    .where("workOrderId", workOrderId).queryOne();
            if (order == null) {
                result.put("message", "Unknown work order [" + workOrderId + "]");
                return result;
            }
            String productCode = order.getString("productCode");
            GenericValue product = EntityQuery.use(delegator).from("MfgProduct")
                    .where("productCode", productCode).queryOne();
            double laborRate = product == null || !(product.get("laborRate") instanceof Number)
                    ? 0.0 : ((Number) product.get("laborRate")).doubleValue();
            double productHours = product == null || product.getDouble("standardHours") == null
                    ? 0.0 : product.getDouble("standardHours").doubleValue();

            // the standard hours of one unit come from the routing when the model has one
            List<GenericValue> routing = MfgProductServices.routingOf(delegator, productCode);
            double routingHours = 0.0;
            for (GenericValue operation : routing) {
                Double operationHours = operation.getDouble("stdHours");
                routingHours += operationHours == null ? 0.0 : operationHours.doubleValue();
            }
            String hoursSource = routing.isEmpty() ? "PRODUCT" : "ROUTING";
            double standardHours = routing.isEmpty() ? productHours : routingHours;

            // standard: the BOM of the version the order was frozen with
            String versionId = order.getString("bomVersionId");
            if (versionId == null) {
                versionId = MfgProductServices.currentBomVersionId(delegator, productCode);
            }
            List<GenericValue> bomItems = versionId == null ? new ArrayList<GenericValue>()
                    : MfgProductServices.bomItemsOfVersion(delegator, versionId);
            double materialPerUnit = 0.0;
            int missing = 0;
            for (GenericValue item : bomItems) {
                double lineQty = item.getDouble("qty") == null ? 0.0 : item.getDouble("qty").doubleValue();
                if (!(item.get("unitPrice") instanceof Number)) {
                    missing++;
                } else {
                    materialPerUnit += lineQty * ((Number) item.get("unitPrice")).doubleValue();
                }
            }

            // actual material: what was issued, at the price of the lot it came from
            List<GenericValue> issues = EntityQuery.use(delegator).from("MfgMaterialIssue")
                    .where("workOrderId", workOrderId).queryList();
            double issuedMaterial = 0.0;
            int issuesWithoutPrice = 0;
            for (GenericValue issue : issues) {
                double qty = issue.getDouble("qty") == null ? 0.0 : issue.getDouble("qty").doubleValue();
                GenericValue lot = EntityQuery.use(delegator).from("MfgMaterialLot")
                        .where("lotId", issue.getString("lotId")).queryOne();
                if (lot == null || !(lot.get("unitPrice") instanceof Number)) {
                    issuesWithoutPrice++;
                } else {
                    // the amount paid on the lot keeps the cent of a cheap part, see lotUnitCost
                    issuedMaterial += qty * MfgStockLedger.lotUnitCost(lot);
                }
            }

            List<GenericValue> reports = EntityQuery.use(delegator).from("MfgReport")
                    .where("workOrderId", workOrderId).queryList();
            double reportedHours = 0.0;
            double qtyOk = 0.0;
            for (GenericValue report : reports) {
                reportedHours += MfgSupport.numberOrZero(report.get("workHours"));
                qtyOk += MfgSupport.numberOrZero(report.get("qtyOk"));
            }
            double plannedQty = MfgSupport.toLong(order.get("plannedQty"), 0);
            double actualLabor = reportedHours * laborRate;
            double outsourceAmount = MfgSupport.numberOrZero(order.get("outsourceAmount"));
            double standardLaborTotal = standardHours * laborRate * qtyOk;

            result.put("productCode", productCode);
            result.put("plannedQty", Double.valueOf(plannedQty));
            result.put("qtyOk", Double.valueOf(qtyOk));
            result.put("laborRate", Double.valueOf(laborRate));
            result.put("standardHours", Double.valueOf(standardHours));
            result.put("productStandardHours", Double.valueOf(productHours));
            result.put("routingHoursPerUnit", Double.valueOf(routingHours));
            result.put("hoursSource", hoursSource);
            result.put("materialPerUnit", Double.valueOf(materialPerUnit));
            result.put("laborPerUnit", Double.valueOf(standardHours * laborRate));
            result.put("standardUnitCost", Double.valueOf(materialPerUnit + standardHours * laborRate));
            result.put("standardTotal", Double.valueOf((materialPerUnit + standardHours * laborRate) * plannedQty));
            result.put("issuedMaterialCost", Double.valueOf(issuedMaterial));
            result.put("reportedHours", Double.valueOf(reportedHours));
            result.put("actualLaborCost", Double.valueOf(actualLabor));
            result.put("outsourceAmount", Double.valueOf(outsourceAmount));
            result.put("outsourcePerUnit", Double.valueOf(outsourcePerUnit(outsourceAmount, qtyOk, plannedQty)));
            result.put("outsourcePerPlannedUnit", Double.valueOf(outsourcePerUnit(outsourceAmount, plannedQty, 0.0)));
            result.put("actualTotal", Double.valueOf(issuedMaterial + actualLabor + outsourceAmount));
            result.put("operations", operationCosts(routing, reports, laborRate, qtyOk));
            double actualUnit = qtyOk > 0.0 ? (issuedMaterial + actualLabor + outsourceAmount) / qtyOk : 0.0;
            result.put("actualUnitCost", Double.valueOf(actualUnit));
            double diff = (issuedMaterial + actualLabor + outsourceAmount)
                    - (materialPerUnit + standardHours * laborRate) * qtyOk;
            result.put("differenceExOutsource", Double.valueOf(diff - outsourceAmount));
            result.put("differenceVsStandard", Double.valueOf(diff));
            result.put("missingPriceCount", Integer.valueOf(missing));
            result.put("issuesWithoutPrice", Integer.valueOf(issuesWithoutPrice));
            return result;
        } catch (GenericEntityException e) {
            return ServiceUtil.returnError("Could not cost the work order: " + e.getMessage());
        }
    }

    /**
     * Costs every operation of the routing: the standard hours of one unit against the hours that
     * were really reported, both at the labour rate. Operations that were reported without being in
     * the routing are appended with an empty name, so their hours are never lost.
     *
     * @param routing the operations of the model
     * @param reports the work reports of the order
     * @param laborRate money per hour
     * @param qtyOk good quantity the standard of the order is measured against
     * @return one row per operation
     */
    static List<Map<String, Object>> operationCosts(List<GenericValue> routing, List<GenericValue> reports,
            double laborRate, double qtyOk) {
        List<Map<String, Object>> rows = new ArrayList<>();
        Map<Long, Double> hoursByOp = new LinkedHashMap<>();
        for (GenericValue report : reports) {
            Long opSeq = report.getLong("opSeq");
            if (opSeq == null) {
                continue;
            }
            Double current = hoursByOp.get(opSeq);
            hoursByOp.put(opSeq, Double.valueOf((current == null ? 0.0 : current.doubleValue())
                    + MfgSupport.numberOrZero(report.get("workHours"))));
        }
        for (GenericValue operation : routing) {
            Long opSeq = operation.getLong("opSeq");
            Double stdHours = operation.getDouble("stdHours");
            double hours = stdHours == null ? 0.0 : stdHours.doubleValue();
            double actualHours = opSeq != null && hoursByOp.containsKey(opSeq)
                    ? hoursByOp.get(opSeq).doubleValue() : 0.0;
            rows.add(operationCostRow(opSeq, operation.getString("opName"),
                    operation.getString("workCenter"), hours, actualHours, laborRate, qtyOk, true));
            if (opSeq != null) {
                hoursByOp.remove(opSeq);
            }
        }
        for (Map.Entry<Long, Double> entry : hoursByOp.entrySet()) {
            rows.add(operationCostRow(entry.getKey(), null, null, 0.0,
                    entry.getValue().doubleValue(), laborRate, qtyOk, false));
        }
        return rows;
    }

    static Map<String, Object> operationCostRow(Long opSeq, String opName, String workCenter,
            double stdHours, double actualHours, double laborRate, double qtyOk, boolean inRouting) {
        double standardCost = MfgSupport.round2(stdHours * laborRate * qtyOk);
        double actualCost = MfgSupport.round2(actualHours * laborRate);
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("opSeq", opSeq);
        row.put("opName", opName);
        row.put("workCenter", workCenter);
        row.put("stdHours", Double.valueOf(stdHours));
        row.put("stdHoursTotal", Double.valueOf(MfgSupport.round2(stdHours * qtyOk)));
        row.put("actualHours", Double.valueOf(MfgSupport.round2(actualHours)));
        row.put("standardCost", Double.valueOf(standardCost));
        row.put("actualCost", Double.valueOf(actualCost));
        row.put("variance", Double.valueOf(MfgSupport.round2(actualCost - standardCost)));
        row.put("inRouting", Boolean.valueOf(inRouting));
        return row;
    }

    /**
     * Issues one lot to a work order, which is what makes the traceability chain
     * "serial number - work order - component lot" possible.
     *
     * @param dctx the dispatch context
     * @param context service context, see {@code servicedef/services.xml#mfgIssueSave}
     * @return the service result containing {@code issueId}
     */
    public static Map<String, Object> issueSave(DispatchContext dctx, Map<String, ? extends Object> context) {
        String denied = MfgServices.permissionProblem(dctx, context, MfgServices.PERM_STOCK);
        if (denied != null) {
            return ServiceUtil.returnError(denied);
        }
        Delegator delegator = dctx.getDelegator();
        String workOrderId = MfgSupport.toText(context.get("workOrderId"));
        String componentCode = MfgSupport.toText(context.get("componentCode"));
        String lotId = MfgSupport.toText(context.get("lotId"));
        double qty = MfgSupport.toDouble(context.get("qty"), 0.0);
        if (workOrderId == null) {
            return ServiceUtil.returnError("workOrderId is required");
        }
        if (componentCode == null) {
            return ServiceUtil.returnError("componentCode is required");
        }
        if (lotId == null) {
            return ServiceUtil.returnError("lotId is required");
        }
        if (qty <= 0.0) {
            return ServiceUtil.returnError("qty must be greater than zero");
        }
        try {
            if (EntityQuery.use(delegator).from("MfgWorkOrder").where("workOrderId", workOrderId).queryOne() == null) {
                return ServiceUtil.returnError("Unknown work order [" + workOrderId + "]");
            }
            GenericValue lot = EntityQuery.use(delegator).from("MfgMaterialLot").where("lotId", lotId).queryOne();
            if (lot == null) {
                return ServiceUtil.returnError("Unknown material lot [" + lotId + "]");
            }
            if (MfgStockLedger.isProductLot(lot)) {
                return ServiceUtil.returnError("Lot [" + lotId + "] holds finished goods, ship them through a"
                        + " delivery order instead of issuing them to a work order");
            }
            String lotComponent = lot.getString("componentCode");
            if (lotComponent != null && !lotComponent.equals(componentCode)) {
                return ServiceUtil.returnError("Lot [" + lotId + "] belongs to component [" + lotComponent
                        + "], not [" + componentCode + "]");
            }
            if (!isLotUsable(lot.getString("inspectStatusId"))) {
                return ServiceUtil.returnError("Lot [" + lotId
                        + "] failed incoming inspection, mark it passed or concession in the material lots first");
            }
            MfgStockLedger.ensureLotLedger(delegator, lot);
            // material reserved by this very work order may of course be issued to it
            double ownReservation = MfgStockLedger.reservationOf(delegator, lotId, workOrderId);
            double available = MfgSupport.numberOrZero(lot.get("availableQty")) + ownReservation;
            if (available + 0.0001 < qty) {
                return ServiceUtil.returnError("Only " + MfgSupport.formatQty(available) + " left in lot [" + lotId
                        + "], cannot issue " + MfgSupport.formatQty(qty));
            }
            String issueId = delegator.getNextSeqId("MfgMaterialIssue");
            GenericValue issue = delegator.makeValue("MfgMaterialIssue");
            issue.set("issueId", issueId);
            issue.set("workOrderId", workOrderId);
            issue.set("componentCode", componentCode);
            issue.set("lotId", lotId);
            issue.set("qty", Double.valueOf(qty));
            issue.set("directionId", MfgServices.MOVE_ISSUE);
            Object issuedDate = MfgSupport.parseDate(context.get("issuedDate"));
            issue.set("issuedDate", issuedDate == null
                    ? java.sql.Date.valueOf(LocalDate.now()) : issuedDate);
            issue.set("designators", MfgSupport.toText(context.get("designators")));
            issue.set("snFrom", MfgSupport.toText(context.get("snFrom")));
            issue.set("snTo", MfgSupport.toText(context.get("snTo")));
            issue.set("note", MfgSupport.toText(context.get("note")));
            delegator.create(issue);
            MfgStockLedger.recordMovement(delegator, lot, MfgServices.MOVE_ISSUE, -qty, lot.get("unitPrice"), workOrderId, null,
                    null, null,
                    Timestamp.valueOf(issue.getDate("issuedDate").toLocalDate().atStartOfDay()),
                    "发料给工单 " + workOrderId);
            MfgStockLedger.releaseReservation(delegator, lot, workOrderId, qty, null);
            MfgStockLedger.recalcLotStock(delegator, lot);

            Map<String, Object> result = ServiceUtil.returnSuccess();
            result.put("issueId", issueId);
            result.put("onHandQty", lot.get("onHandQty"));
            result.put("availableQty", lot.get("availableQty"));
            return result;
        } catch (GenericEntityException e) {
            return ServiceUtil.returnError("Could not issue the material: " + e.getMessage());
        }
    }

    /**
     * Returns the material picture of one work order: BOM requirement against issued quantity
     * (kitting), the issued lots and the shipments to / from the SMT house.
     *
     * @param dctx the dispatch context
     * @param context service context, see {@code servicedef/services.xml#mfgWorkOrderMaterials}
     * @return the service result containing {@code lines}, {@code issues}, {@code shipments} and {@code kitComplete}
     */
    public static Map<String, Object> workOrderMaterials(DispatchContext dctx, Map<String, ? extends Object> context) {
        Delegator delegator = dctx.getDelegator();
        String workOrderId = MfgSupport.toText(context.get("workOrderId"));
        Map<String, Object> empty = ServiceUtil.returnSuccess();
        empty.put("lines", new ArrayList<Map<String, Object>>());
        empty.put("issues", new ArrayList<Map<String, Object>>());
        empty.put("shipments", new ArrayList<Map<String, Object>>());
        empty.put("kitComplete", Boolean.TRUE);
        empty.put("outQty", Double.valueOf(0.0));
        empty.put("inQty", Double.valueOf(0.0));
        empty.put("inDefectQty", Double.valueOf(0.0));
        if (workOrderId == null) {
            return empty;
        }
        try {
            GenericValue order = EntityQuery.use(delegator).from("MfgWorkOrder")
                    .where("workOrderId", workOrderId).queryOne();
            if (order == null) {
                return empty;
            }
            List<Map<String, Object>> lines = kittingLines(delegator, order);
            List<GenericValue> issues = EntityQuery.use(delegator).from("MfgMaterialIssue")
                    .where("workOrderId", workOrderId).orderBy("-createdStamp").queryList();
            List<GenericValue> shipments = EntityQuery.use(delegator).from("MfgShipment")
                    .where("workOrderId", workOrderId).orderBy("-shipmentDate").queryList();

            boolean kitComplete = true;
            for (Map<String, Object> line : lines) {
                Double shortage = (Double) line.get("shortage");
                if (shortage != null && shortage.doubleValue() > 0.0001) {
                    kitComplete = false;
                }
            }

            List<Map<String, Object>> issueMaps = new ArrayList<>(issues.size());
            for (GenericValue issue : issues) {
                GenericValue lot = EntityQuery.use(delegator).from("MfgMaterialLot")
                        .where("lotId", issue.getString("lotId")).queryOne();
                issueMaps.add(MfgServices.toIssueMap(issue, lot));
            }
            List<Map<String, Object>> shipmentMaps = new ArrayList<>(shipments.size());
            double outQty = 0.0;
            double inQty = 0.0;
            double inDefectQty = 0.0;
            for (GenericValue shipment : shipments) {
                shipmentMaps.add(toShipmentMap(shipment));
                double qty = shipment.getDouble("qty") == null ? 0.0 : shipment.getDouble("qty").doubleValue();
                double defectQty = shipment.getDouble("defectQty") == null
                        ? 0.0 : shipment.getDouble("defectQty").doubleValue();
                if (MfgServices.SHIP_IN.equals(shipment.getString("directionId"))) {
                    inQty += qty;
                    inDefectQty += defectQty;
                } else {
                    outQty += qty;
                }
            }

            Map<String, Object> result = ServiceUtil.returnSuccess();
            result.put("workOrder", MfgServices.toWorkOrderMap(order));
            result.put("lines", lines);
            result.put("issues", issueMaps);
            result.put("shipments", shipmentMaps);
            result.put("kitComplete", Boolean.valueOf(kitComplete));
            result.put("outQty", Double.valueOf(outQty));
            result.put("inQty", Double.valueOf(inQty));
            result.put("inDefectQty", Double.valueOf(inDefectQty));
            return result;
        } catch (GenericEntityException e) {
            return ServiceUtil.returnError("Could not read the work order material: " + e.getMessage());
        }
    }

    /**
     * Records material sent to the SMT house or boards received back.
     *
     * @param dctx the dispatch context
     * @param context service context, see {@code servicedef/services.xml#mfgShipmentSave}
     * @return the service result containing {@code shipmentId}
     */
    public static Map<String, Object> shipmentSave(DispatchContext dctx, Map<String, ? extends Object> context) {
        String denied = MfgServices.permissionProblem(dctx, context, MfgServices.PERM_PRODUCTION);
        if (denied != null) {
            return ServiceUtil.returnError(denied);
        }
        Delegator delegator = dctx.getDelegator();
        String workOrderId = MfgSupport.toText(context.get("workOrderId"));
        String directionId = MfgSupport.toText(context.get("directionId"));
        if (workOrderId == null) {
            return ServiceUtil.returnError("workOrderId is required");
        }
        if (directionId == null) {
            directionId = MfgServices.SHIP_OUT;
        }
        if (!MfgServices.SHIP_IN.equals(directionId) && !MfgServices.SHIP_OUT.equals(directionId)) {
            return ServiceUtil.returnError("directionId must be " + MfgServices.SHIP_OUT + " or " + MfgServices.SHIP_IN);
        }
        double qty = MfgSupport.toDouble(context.get("qty"), 0.0);
        double defectQty = MfgSupport.toDouble(context.get("defectQty"), 0.0);
        if (qty <= 0.0) {
            return ServiceUtil.returnError("qty must be greater than zero");
        }
        if (defectQty < 0.0) {
            return ServiceUtil.returnError("defectQty must not be negative");
        }
        if (defectQty > qty) {
            return ServiceUtil.returnError("defectQty must not exceed qty");
        }
        try {
            if (EntityQuery.use(delegator).from("MfgWorkOrder").where("workOrderId", workOrderId).queryOne() == null) {
                return ServiceUtil.returnError("Unknown work order [" + workOrderId + "]");
            }
            String shipmentId = delegator.getNextSeqId("MfgShipment");
            GenericValue shipment = delegator.makeValue("MfgShipment");
            shipment.set("shipmentId", shipmentId);
            shipment.set("workOrderId", workOrderId);
            shipment.set("directionId", directionId);
            Object shipmentDate = MfgSupport.parseDate(context.get("shipmentDate"));
            shipment.set("shipmentDate", shipmentDate == null
                    ? java.sql.Date.valueOf(LocalDate.now()) : shipmentDate);
            shipment.set("qty", Double.valueOf(qty));
            shipment.set("defectQty", Double.valueOf(defectQty));
            shipment.set("carrier", MfgSupport.toText(context.get("carrier")));
            shipment.set("trackingNo", MfgSupport.toText(context.get("trackingNo")));
            shipment.set("note", MfgSupport.toText(context.get("note")));
            delegator.create(shipment);

            Map<String, Object> result = ServiceUtil.returnSuccess();
            result.put("shipmentId", shipmentId);
            return result;
        } catch (GenericEntityException e) {
            return ServiceUtil.returnError("Could not record the shipment: " + e.getMessage());
        }
    }

    /**
     * Backward trace: which work orders and how many serial numbers used a given lot. This is the
     * query that tells the recall scope when a supplier reports a problem.
     *
     * @param dctx the dispatch context
     * @param context service context, see {@code servicedef/services.xml#mfgTraceByLot}
     * @return the service result containing {@code lots}, {@code workOrders} and {@code totalSnCount}
     */
    public static Map<String, Object> traceByLot(DispatchContext dctx, Map<String, ? extends Object> context) {
        Delegator delegator = dctx.getDelegator();
        String lotId = MfgSupport.toText(context.get("lotId"));
        String supplierLot = MfgSupport.toText(context.get("supplierLot"));
        if (lotId == null && supplierLot == null) {
            // the material page always calls this service, an empty query is not an error
            Map<String, Object> empty = ServiceUtil.returnSuccess();
            empty.put("lots", new ArrayList<Map<String, Object>>());
            empty.put("workOrders", new ArrayList<Map<String, Object>>());
            empty.put("totalSnCount", Long.valueOf(0));
            return empty;
        }
        try {
            List<GenericValue> lots = new ArrayList<>();
            if (lotId != null) {
                GenericValue lot = EntityQuery.use(delegator).from("MfgMaterialLot")
                        .where("lotId", lotId).queryOne();
                if (lot != null) {
                    lots.add(lot);
                }
            } else {
                lots = EntityQuery.use(delegator).from("MfgMaterialLot")
                        .where("supplierLot", supplierLot).queryList();
            }
            List<Map<String, Object>> lotMaps = new ArrayList<>(lots.size());
            Map<String, Double> issuedByLot = issuedQtyByLot(delegator);
            for (GenericValue lot : lots) {
                lotMaps.add(MfgServices.toLotMap(lot, issuedByLot.get(lot.getString("lotId"))));
            }

            List<String> lotIds = new ArrayList<>(lots.size());
            for (GenericValue lot : lots) {
                lotIds.add(lot.getString("lotId"));
            }

            List<GenericValue> allIssues = EntityQuery.use(delegator).from("MfgMaterialIssue").queryList();
            Map<String, Boolean> workOrderIds = new LinkedHashMap<>();
            for (GenericValue issue : allIssues) {
                if (lotIds.contains(issue.getString("lotId"))) {
                    workOrderIds.put(issue.getString("workOrderId"), Boolean.TRUE);
                }
            }

            List<Map<String, Object>> traces = new ArrayList<>(workOrderIds.size());
            long totalSn = 0;
            for (String workOrderId : workOrderIds.keySet()) {
                GenericValue order = EntityQuery.use(delegator).from("MfgWorkOrder")
                        .where("workOrderId", workOrderId).queryOne();
                if (order == null) {
                    continue;
                }
                List<GenericValue> units = EntityQuery.use(delegator).from("MfgSn")
                        .where("workOrderId", workOrderId).orderBy("sn").queryList();
                List<GenericValue> defects = EntityQuery.use(delegator).from("MfgDefect")
                        .where("workOrderId", workOrderId).queryList();
                totalSn += units.size();
                Map<String, Object> trace = new LinkedHashMap<>();
                trace.put("workOrderId", workOrderId);
                trace.put("productCode", order.getString("productCode"));
                trace.put("statusId", order.getString("statusId"));
                trace.put("customerName", order.getString("customerName"));
                trace.put("plannedQty", order.getLong("plannedQty"));
                trace.put("snCount", Integer.valueOf(units.size()));
                trace.put("defectCount", Integer.valueOf(defects.size()));
                trace.put("estimates", estimateSnRanges(delegator, workOrderId, units, lotIds));
                traces.add(trace);
            }

            Map<String, Object> result = ServiceUtil.returnSuccess();
            result.put("lots", lotMaps);
            result.put("workOrders", traces);
            result.put("totalSnCount", Long.valueOf(totalSn));
            result.put("lotId", lotId);
            result.put("supplierLot", supplierLot);
            return result;
        } catch (GenericEntityException e) {
            return ServiceUtil.returnError("Could not trace the lot: " + e.getMessage());
        }
    }

    static Map<String, Double> issuedQtyByLot(Delegator delegator) throws GenericEntityException {
        Map<String, Double> totals = new LinkedHashMap<>();
        List<GenericValue> issues = EntityQuery.use(delegator).from("MfgMaterialIssue").queryList();
        for (GenericValue issue : issues) {
            String lotId = issue.getString("lotId");
            Double current = totals.get(lotId);
            Double qty = issue.getDouble("qty");
            totals.put(lotId, Double.valueOf((current == null ? 0.0 : current.doubleValue())
                    + (qty == null ? 0.0 : qty.doubleValue())));
        }
        return totals;
    }

    /**
     * Estimates which serial numbers of a work order most likely used the traced lots, assuming the
     * material was consumed first in first out. The SMT house does not report reel changes, so this
     * is an estimate and is labelled as such in the user interface.
     *
     * @param delegator the delegator
     * @param workOrderId the work order
     * @param units serial numbers of the work order
     * @param tracedLotIds the lots being traced
     * @return one map per traced component with {@code estimatedSnFrom}, {@code estimatedSnTo} and {@code estimatedSnCount}
     * @throws GenericEntityException when the issues cannot be read
     */
    static List<Map<String, Object>> estimateSnRanges(Delegator delegator, String workOrderId,
            List<GenericValue> units, List<String> tracedLotIds) throws GenericEntityException {
        List<Map<String, Object>> estimates = new ArrayList<>();
        if (units.isEmpty() || tracedLotIds.isEmpty()) {
            return estimates;
        }
        List<GenericValue> issues = EntityQuery.use(delegator).from("MfgMaterialIssue")
                .where("workOrderId", workOrderId).orderBy("createdStamp").queryList();
        Map<String, List<GenericValue>> byComponent = new LinkedHashMap<>();
        for (GenericValue issue : issues) {
            String componentCode = issue.getString("componentCode");
            List<GenericValue> list = byComponent.get(componentCode);
            if (list == null) {
                list = new ArrayList<>();
                byComponent.put(componentCode, list);
            }
            list.add(issue);
        }
        List<String> sns = new ArrayList<>(units.size());
        for (GenericValue unit : units) {
            sns.add(unit.getString("sn"));
        }
        for (Map.Entry<String, List<GenericValue>> entry : byComponent.entrySet()) {
            List<GenericValue> issuesOfComponent = entry.getValue();
            List<Double> quantities = new ArrayList<>(issuesOfComponent.size());
            int tracedIndex = -1;
            for (int i = 0; i < issuesOfComponent.size(); i++) {
                GenericValue issue = issuesOfComponent.get(i);
                Double qty = issue.getDouble("qty");
                quantities.add(qty == null ? Double.valueOf(0.0) : qty);
                if (tracedLotIds.contains(issue.getString("lotId"))) {
                    tracedIndex = i;
                }
            }
            if (tracedIndex < 0) {
                continue;
            }
            int[] slice = fifoSlice(sns, quantities, tracedIndex);
            if (slice == null) {
                continue;
            }
            Map<String, Object> estimate = new LinkedHashMap<>();
            estimate.put("componentCode", entry.getKey());
            estimate.put("issueIndex", Integer.valueOf(tracedIndex + 1));
            estimate.put("issueCount", Integer.valueOf(issuesOfComponent.size()));
            estimate.put("estimatedSnFrom", sns.get(slice[0]));
            estimate.put("estimatedSnTo", sns.get(slice[1]));
            estimate.put("estimatedSnCount", Integer.valueOf(slice[1] - slice[0] + 1));
            estimates.add(estimate);
        }
        return estimates;
    }

    /**
     * Tells whether a lot may be issued.
     *
     * <p>Only a failed lot is blocked: a lot that was never inspected ({@code PENDING}) or one that
     * was accepted with reservations ({@code CONCESSION}) is still usable, because a small workshop
     * does not inspect every passive component.
     *
     * @param inspectStatusId the incoming inspection status, may be {@code null}
     * @return {@code true} unless the lot failed the inspection
     */
    static boolean isLotUsable(String inspectStatusId) {
        return !MfgServices.IQC_FAIL.equals(inspectStatusId);
    }

    static Map<String, Object> toShipmentMap(GenericValue shipment) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("shipmentId", shipment.getString("shipmentId"));
        map.put("directionId", shipment.getString("directionId"));
        map.put("shipmentDate", shipment.getDate("shipmentDate"));
        map.put("qty", shipment.getDouble("qty"));
        map.put("defectQty", shipment.getDouble("defectQty"));
        map.put("carrier", shipment.getString("carrier"));
        map.put("trackingNo", shipment.getString("trackingNo"));
        map.put("note", shipment.getString("note"));
        return map;
    }

    /**
     * BOM requirement against issued quantity of one work order.
     *
     * @param delegator the delegator
     * @param order the work order
     * @return one map per BOM line with {@code requiredQty}, {@code issuedQty} and {@code shortage}
     * @throws GenericEntityException when the BOM or the issues cannot be read
     */
    static List<Map<String, Object>> kittingLines(Delegator delegator, GenericValue order)
            throws GenericEntityException {
        long plannedQty = MfgSupport.toLong(order.get("plannedQty"), 0);
        List<GenericValue> bomItems = bomItemsForOrder(delegator, order);
        List<GenericValue> issues = EntityQuery.use(delegator).from("MfgMaterialIssue")
                .where("workOrderId", order.getString("workOrderId")).queryList();

        Map<String, Double> issuedByComponent = new LinkedHashMap<>();
        for (GenericValue issue : issues) {
            String componentCode = issue.getString("componentCode");
            Double current = issuedByComponent.get(componentCode);
            Double qty = issue.getDouble("qty");
            issuedByComponent.put(componentCode, Double.valueOf((current == null ? 0.0 : current.doubleValue())
                    + (qty == null ? 0.0 : qty.doubleValue())));
        }
        Map<String, Double> reservedMap = MfgStockLedger.reservedByComponent(delegator, order.getString("workOrderId"));

        List<Map<String, Object>> lines = new ArrayList<>(bomItems.size());
        for (GenericValue item : bomItems) {
            String componentCode = item.getString("componentCode");
            Double bomQty = item.getDouble("qty");
            double required = (bomQty == null ? 0.0 : bomQty.doubleValue()) * plannedQty;
            Double issued = issuedByComponent.get(componentCode);
            double issuedQty = issued == null ? 0.0 : issued.doubleValue();
            Double reserved = reservedMap.get(componentCode);
            double reservedQty = reserved == null ? 0.0 : reserved.doubleValue();
            Map<String, Object> line = new LinkedHashMap<>();
            line.put("componentCode", componentCode);
            line.put("componentName", item.getString("componentName"));
            line.put("isKeyPart", item.getString("isKeyPart"));
            line.put("requiredQty", Double.valueOf(required));
            line.put("issuedQty", Double.valueOf(issuedQty));
            line.put("reservedQty", Double.valueOf(MfgSupport.round2(reservedQty)));
            line.put("shortage", Double.valueOf(Math.max(0.0, required - issuedQty - reservedQty)));
            lines.add(line);
        }
        return lines;
    }

    /**
     * Splits the serial numbers of one work order into slices proportional to the issued quantities,
     * assuming first in first out. It is an <em>estimate</em>: used only because the SMT house does
     * not report reel changes, so the exact unit per reel is unknown.
     *
     * @param sns serial numbers of the work order, in sequence order
     * @param issueQtys issued quantity per issue, in issue order
     * @param issueIndex index of the issue that is being traced
     * @return {@code {startIndex, endIndex}} (inclusive) or {@code null} when nothing can be estimated
     */
    static int[] fifoSlice(List<String> sns, List<Double> issueQtys, int issueIndex) {
        if (sns == null || sns.isEmpty() || issueQtys == null
                || issueIndex < 0 || issueIndex >= issueQtys.size()) {
            return null;
        }
        double total = 0.0;
        for (Double qty : issueQtys) {
            if (qty != null) {
                total += qty.doubleValue();
            }
        }
        if (total <= 0.0) {
            return null;
        }
        double before = 0.0;
        for (int i = 0; i < issueIndex; i++) {
            Double qty = issueQtys.get(i);
            if (qty != null) {
                before += qty.doubleValue();
            }
        }
        Double current = issueQtys.get(issueIndex);
        double qty = current == null ? 0.0 : current.doubleValue();
        if (qty <= 0.0) {
            return null;
        }
        int count = sns.size();
        int start = (int) Math.floor(before / total * count);
        int end = (int) Math.ceil((before + qty) / total * count) - 1;
        if (start < 0) {
            start = 0;
        }
        if (start > count - 1) {
            start = count - 1;
        }
        if (end > count - 1) {
            end = count - 1;
        }
        if (end < start) {
            end = start;
        }
        return new int[] {start, end};
    }

    /**
     * Imports test bench results from CSV text. Each line is
     * {@code sn,station,item,value,unit,pass}; lines of unknown serial numbers are skipped.
     *
     * @param dctx the dispatch context
     * @param context service context, see {@code servicedef/services.xml#mfgTestImportCsv}
     * @return the service result containing {@code importedCount} and {@code skippedCount}
     */
    public static Map<String, Object> testImportCsv(DispatchContext dctx, Map<String, ? extends Object> context) {
        String denied = MfgServices.permissionProblem(dctx, context, MfgServices.PERM_PRODUCTION);
        if (denied != null) {
            return ServiceUtil.returnError(denied);
        }
        Delegator delegator = dctx.getDelegator();
        String csv = MfgSupport.toText(context.get("csv"));
        if (csv == null) {
            return ServiceUtil.returnError("csv is required");
        }
        List<String[]> rows = MfgCsv.parseTestCsv(csv);
        if (rows.isEmpty()) {
            return ServiceUtil.returnError("No test line could be parsed, expected sn,station,item,value,unit,pass");
        }
        try {
            int imported = 0;
            int skipped = 0;
            Timestamp now = new Timestamp(System.currentTimeMillis());
            for (String[] row : rows) {
                String sn = row[0];
                GenericValue unit = EntityQuery.use(delegator).from("MfgSn").where("sn", sn).queryOne();
                if (unit == null) {
                    skipped++;
                    continue;
                }
                GenericValue result = delegator.makeValue("MfgTestResult");
                result.set("resultId", delegator.getNextSeqId("MfgTestResult"));
                result.set("sn", sn);
                result.set("workOrderId", unit.getString("workOrderId"));
                result.set("station", row[1] == null ? "FCT" : row[1].toUpperCase(Locale.ROOT));
                result.set("testItem", row[2]);
                result.set("measureValue", Double.valueOf(MfgSupport.toDouble(row[3], 0.0)));
                result.set("unitOfMeasure", row[4]);
                result.set("passFail", "N".equalsIgnoreCase(row[5]) ? "N" : "Y");
                result.set("testDate", now);
                delegator.create(result);
                imported++;
            }
            if (imported == 0) {
                return ServiceUtil.returnError("No serial number of the file is known, " + skipped + " line(s) skipped");
            }
            Map<String, Object> result = ServiceUtil.returnSuccess();
            result.put("importedCount", String.valueOf(imported));
            result.put("skippedCount", String.valueOf(skipped));
            // carried through the redirect so the page returns to the work order
            result.put("workOrderId", MfgSupport.toText(context.get("workOrderId")));
            return result;
        } catch (GenericEntityException e) {
            return ServiceUtil.returnError("Could not import the test results: " + e.getMessage());
        }
    }

    /**
     * Lists test results of one unit, or the most recent results when no serial number is given.
     *
     * @param dctx the dispatch context
     * @param context service context, see {@code servicedef/services.xml#mfgTestResultList}
     * @return the service result containing {@code results}
     */
    public static Map<String, Object> testResultList(DispatchContext dctx, Map<String, ? extends Object> context) {
        Delegator delegator = dctx.getDelegator();
        String sn = MfgSupport.toText(context.get("sn"));
        try {
            EntityQuery query = EntityQuery.use(delegator).from("MfgTestResult");
            if (sn != null) {
                query = query.where("sn", sn);
            }
            List<GenericValue> results = query.orderBy("-testDate").queryList();
            Map<String, Object> result = ServiceUtil.returnSuccess();
            result.put("results", toTestResultMaps(results));
            result.put("sn", sn);
            return result;
        } catch (GenericEntityException e) {
            return ServiceUtil.returnError("Could not read the test results: " + e.getMessage());
        }
    }

    static List<Map<String, Object>> toTestResultMaps(List<GenericValue> results) {
        List<Map<String, Object>> list = new ArrayList<>(results.size());
        for (GenericValue result : results) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("resultId", result.getString("resultId"));
            map.put("sn", result.getString("sn"));
            map.put("station", result.getString("station"));
            map.put("testItem", result.getString("testItem"));
            map.put("measureValue", result.getDouble("measureValue"));
            map.put("unitOfMeasure", result.getString("unitOfMeasure"));
            map.put("passFail", result.getString("passFail"));
            map.put("testDate", result.getTimestamp("testDate"));
            list.add(map);
        }
        return list;
    }

    /**
     * Sets the outsourcing vendor of a work order, so the shipping details are at hand when the
     * material is sent out.
     *
     * @param dctx the dispatch context
     * @param context service context, see {@code servicedef/services.xml#mfgWorkOrderVendor}
     * @return the service result containing {@code workOrderId} and {@code vendorId}
     */
    public static Map<String, Object> workOrderVendor(DispatchContext dctx, Map<String, ? extends Object> context) {
        String denied = MfgServices.permissionProblem(dctx, context, MfgServices.PERM_PRODUCTION);
        if (denied != null) {
            return ServiceUtil.returnError(denied);
        }
        Delegator delegator = dctx.getDelegator();
        String workOrderId = MfgSupport.toText(context.get("workOrderId"));
        String vendorId = MfgSupport.toText(context.get("vendorId"));
        if (workOrderId == null) {
            return ServiceUtil.returnError("workOrderId is required");
        }
        try {
            GenericValue order = EntityQuery.use(delegator).from("MfgWorkOrder")
                    .where("workOrderId", workOrderId).queryOne();
            if (order == null) {
                return ServiceUtil.returnError("Unknown work order [" + workOrderId + "]");
            }
            if (vendorId == null) {
                order.set("smtVendorId", null);
                order.set("smtVendor", null);
            } else {
                GenericValue vendor = EntityQuery.use(delegator).from("MfgVendor")
                        .where("vendorId", vendorId).queryOne();
                if (vendor == null) {
                    return ServiceUtil.returnError("Unknown vendor [" + vendorId + "]");
                }
                order.set("smtVendorId", vendorId);
                order.set("smtVendor", vendor.getString("vendorName"));
            }
            delegator.store(order);

            Map<String, Object> result = ServiceUtil.returnSuccess();
            result.put("workOrderId", workOrderId);
            result.put("vendorId", vendorId);
            return result;
        } catch (GenericEntityException e) {
            return ServiceUtil.returnError("Could not set the vendor: " + e.getMessage());
        }
    }

    /**
     * Builds the shipping label used when the material is couriered to the vendor.
     *
     * @param vendorName the vendor name
     * @param contactName the recipient
     * @param contactMobile the mobile number of the recipient
     * @param addressText the address
     * @param customerCode the customer code the vendor assigned to us
     * @return the label text, never {@code null}
     */
    static String formatShippingLabel(String vendorName, String contactName, String contactMobile,
            String addressText, String customerCode) {
        StringBuilder label = new StringBuilder();
        label.append("收件人：").append(contactName == null ? "" : contactName);
        if (vendorName != null) {
            label.append("（").append(vendorName).append("）");
        }
        label.append('\n');
        label.append("手机号：").append(contactMobile == null ? "" : contactMobile).append('\n');
        label.append("地址：").append(addressText == null ? "" : addressText);
        if (customerCode != null) {
            label.append('\n').append("客户编号：").append(customerCode).append("（请标注在快递单/箱外）");
        }
        return label.toString();
    }

    static Map<String, Object> toVendorMap(GenericValue vendor) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("vendorId", vendor.getString("vendorId"));
        map.put("vendorName", vendor.getString("vendorName"));
        map.put("vendorType", vendor.getString("vendorType"));
        map.put("contactName", vendor.getString("contactName"));
        map.put("contactMobile", vendor.getString("contactMobile"));
        map.put("addressText", vendor.getString("addressText"));
        map.put("customerCode", vendor.getString("customerCode"));
        map.put("statusId", vendor.getString("statusId"));
        map.put("note", vendor.getString("note"));
        map.put("shippingLabel", formatShippingLabel(vendor.getString("vendorName"),
                vendor.getString("contactName"), vendor.getString("contactMobile"),
                vendor.getString("addressText"), vendor.getString("customerCode")));
        return map;
    }

    static List<Map<String, Object>> toClaimMaps(List<GenericValue> claims) {
        List<Map<String, Object>> list = new ArrayList<>(claims.size());
        for (GenericValue claim : claims) {
            list.add(MfgServices.toClaimMap(claim));
        }
        return list;
    }

    /**
     * Reserves the material a work order still needs: for every BOM line it takes the missing
     * quantity out of the lots that can be issued, oldest lot first. What cannot be covered stays a
     * shortage, so the kitting page tells the truth either way.
     *
     * @param dctx the dispatch context
     * @param context service context, see {@code servicedef/services.xml#mfgWorkOrderReserve}
     * @return the service result with what was reserved and what is still missing
     */
    public static Map<String, Object> reserveMaterial(DispatchContext dctx, Map<String, ? extends Object> context) {
        String denied = MfgServices.permissionProblem(dctx, context, MfgServices.PERM_PRODUCTION, MfgServices.PERM_SALES);
        if (denied != null) {
            return ServiceUtil.returnError(denied);
        }
        Delegator delegator = dctx.getDelegator();
        String workOrderId = MfgSupport.toText(context.get("workOrderId"));
        Map<String, Object> result = ServiceUtil.returnSuccess();
        result.put("workOrderId", workOrderId);
        result.put("reservedQty", Double.valueOf(0.0));
        result.put("shortQty", Double.valueOf(0.0));
        result.put("reservationCount", Integer.valueOf(0));
        if (workOrderId == null) {
            return ServiceUtil.returnError("workOrderId is required");
        }
        try {
            GenericValue order = EntityQuery.use(delegator).from("MfgWorkOrder")
                    .where("workOrderId", workOrderId).queryOne();
            if (order == null) {
                return ServiceUtil.returnError("Unknown work order [" + workOrderId + "]");
            }
            double reservedTotal = 0.0;
            double shortTotal = 0.0;
            int reservations = 0;
            String operator = MfgSupport.toText(context.get("operatorName"));
            for (Map<String, Object> line : kittingLines(delegator, order)) {
                double missing = MfgSupport.numberOrZero(line.get("shortage"));
                if (missing <= 0.0001) {
                    continue;
                }
                String componentCode = (String) line.get("componentCode");
                List<GenericValue> lots = EntityQuery.use(delegator).from("MfgMaterialLot")
                        .where("componentCode", componentCode)
                        .orderBy("receivedDate", "lotId").queryList();
                for (GenericValue lot : lots) {
                    if (missing <= 0.0001) {
                        break;
                    }
                    if (MfgStockLedger.isProductLot(lot)) {
                        continue;
                    }
                    MfgStockLedger.ensureLotLedger(delegator, lot);
                    double free = MfgSupport.numberOrZero(lot.get("availableQty"));
                    if (free <= 0.0001) {
                        continue;
                    }
                    double take = Math.min(missing, free);
                    MfgStockLedger.recordReservation(delegator, lot, MfgServices.MOVE_RESERVE, take, workOrderId, operator,
                            "工单 " + workOrderId + " 预留");
                    MfgStockLedger.recalcLotStock(delegator, lot);
                    missing = MfgSupport.round2(missing - take);
                    reservedTotal += take;
                    reservations++;
                }
                shortTotal += missing;
            }
            result.put("reservedQty", Double.valueOf(MfgSupport.round2(reservedTotal)));
            result.put("shortQty", Double.valueOf(MfgSupport.round2(shortTotal)));
            result.put("reservationCount", Integer.valueOf(reservations));
            return result;
        } catch (GenericEntityException e) {
            return ServiceUtil.returnError("Could not reserve the material: " + e.getMessage());
        }
    }

    /**
     * Gives back every reservation of one work order, e.g. when it is cancelled.
     *
     * @param dctx the dispatch context
     * @param context service context, see {@code servicedef/services.xml#mfgWorkOrderRelease}
     * @return the service result with the released quantity
     */
    public static Map<String, Object> releaseMaterialReservation(DispatchContext dctx,
            Map<String, ? extends Object> context) {
        String denied = MfgServices.permissionProblem(dctx, context, MfgServices.PERM_PRODUCTION, MfgServices.PERM_SALES);
        if (denied != null) {
            return ServiceUtil.returnError(denied);
        }
        Delegator delegator = dctx.getDelegator();
        String workOrderId = MfgSupport.toText(context.get("workOrderId"));
        Map<String, Object> result = ServiceUtil.returnSuccess();
        result.put("workOrderId", workOrderId);
        result.put("releasedQty", Double.valueOf(0.0));
        if (workOrderId == null) {
            return ServiceUtil.returnError("workOrderId is required");
        }
        try {
            double released = 0.0;
            String operator = MfgSupport.toText(context.get("operatorName"));
            Map<String, GenericValue> lots = new LinkedHashMap<>();
            for (GenericValue lot : EntityQuery.use(delegator).from("MfgMaterialLot").queryList()) {
                lots.put(lot.getString("lotId"), lot);
            }
            for (GenericValue movement : EntityQuery.use(delegator).from("MfgInventoryDetail")
                    .where("workOrderId", workOrderId).queryList()) {
                String lotId = movement.getString("lotId");
                GenericValue lot = lots.get(lotId);
                if (lot == null) {
                    continue;
                }
                double reserved = MfgStockLedger.reservationOf(delegator, lotId, workOrderId);
                if (reserved <= 0.0001) {
                    continue;
                }
                MfgStockLedger.recordReservation(delegator, lot, MfgServices.MOVE_RELEASE, -reserved, workOrderId, operator,
                        "释放工单 " + workOrderId + " 的预留");
                MfgStockLedger.recalcLotStock(delegator, lot);
                released += reserved;
            }
            result.put("releasedQty", Double.valueOf(MfgSupport.round2(released)));
            return result;
        } catch (GenericEntityException e) {
            return ServiceUtil.returnError("Could not release the reservation: " + e.getMessage());
        }
    }

    /**
     * Books the good units of a work order into stock as its own finished goods lot
     * {@code FG-WO-yyMMdd-NN}, prices that lot with the actual cost of the order and marks the order
     * and its serial numbers as completed.
     *
     * <p>It can be pressed twice: only the difference between what the reports say and what is
     * already in stock is booked, so extra reports top the lot up instead of doubling it.
     *
     * @param dctx the dispatch context
     * @param context service context, see {@code servicedef/services.xml#mfgWorkOrderComplete}
     * @return the service result with the lot and its quantity
     */
    public static Map<String, Object> workOrderComplete(DispatchContext dctx, Map<String, ? extends Object> context) {
        String denied = MfgServices.permissionProblem(dctx, context, MfgServices.PERM_PRODUCTION);
        if (denied != null) {
            return ServiceUtil.returnError(denied);
        }
        Delegator delegator = dctx.getDelegator();
        String workOrderId = MfgSupport.toText(context.get("workOrderId"));
        if (workOrderId == null) {
            return ServiceUtil.returnError("workOrderId is required");
        }
        try {
            GenericValue order = EntityQuery.use(delegator).from("MfgWorkOrder")
                    .where("workOrderId", workOrderId).queryOne();
            if (order == null) {
                return ServiceUtil.returnError("Unknown work order [" + workOrderId + "]");
            }
            if (MfgServices.WO_CLOSED.equals(order.getString("statusId"))) {
                return ServiceUtil.returnError("Work order [" + workOrderId + "] is already closed");
            }
            double qtyOk = 0.0;
            for (GenericValue report : EntityQuery.use(delegator).from("MfgReport")
                    .where("workOrderId", workOrderId).queryList()) {
                qtyOk += MfgSupport.numberOrZero(report.get("qtyOk"));
            }
            qtyOk = MfgSupport.round2(qtyOk);
            if (qtyOk <= 0.0001) {
                return ServiceUtil.returnError("Report the good units before completing work order ["
                        + workOrderId + "]");
            }

            Map<String, Object> productCodeContext = new LinkedHashMap<>();
            productCodeContext.put("workOrderId", workOrderId);
            double unitCost = MfgStockLedger.productUnitCost(MfgSupport.numberOrZero(workOrderCostOf(dctx, productCodeContext)
                    .get("actualTotal")), qtyOk);

            String lotId = MfgStockLedger.productLotId(workOrderId);
            Object dateInput = MfgSupport.parseDate(context.get("completeDate"));
            java.sql.Date day = dateInput == null ? java.sql.Date.valueOf(LocalDate.now())
                    : (java.sql.Date) dateInput;
            String productCode = order.getString("productCode");
            GenericValue product = EntityQuery.use(delegator).from("MfgProduct")
                    .where("productCode", productCode).queryOne();
            GenericValue lot = EntityQuery.use(delegator).from("MfgMaterialLot")
                    .where("lotId", lotId).queryOne();
            if (lot == null) {
                lot = delegator.makeValue("MfgMaterialLot");
                lot.set("lotId", lotId);
                lot.set("lotKindId", MfgServices.LOT_PRODUCT);
                lot.set("workOrderId", workOrderId);
                lot.set("componentCode", productCode);
                lot.set("componentName", product == null ? null : product.getString("productName"));
                lot.set("receivedQty", Double.valueOf(0.0));
                lot.set("receivedDate", day);
                lot.set("inspectStatusId", MfgServices.IQC_PASS);
                lot.set("note", "工单 " + workOrderId + " 的成品");
                delegator.create(lot);
            }

            double booked = 0.0;
            for (GenericValue movement : MfgStockLedger.movementsOf(delegator, lotId)) {
                if (MfgServices.MOVE_RECEIPT.equals(movement.getString("movementTypeId"))) {
                    booked += MfgSupport.numberOrZero(movement.get("quantityDiff"));
                }
            }
            booked = MfgSupport.round2(booked);
            double added = MfgSupport.round2(qtyOk - booked);
            String message = null;
            if (added > 0.0001) {
                String operator = MfgSupport.toText(context.get("operatorName"));
                MfgStockLedger.recordMovement(delegator, lot, MfgServices.MOVE_RECEIPT, added, Double.valueOf(unitCost), workOrderId,
                        null, workOrderId, operator,
                        Timestamp.valueOf(day.toLocalDate().atStartOfDay()),
                        "完工入库：" + MfgSupport.formatQty(added) + " 台");
            } else if (added < -0.0001) {
                message = "The reports now say " + MfgSupport.formatQty(qtyOk) + " good units but " + MfgSupport.formatQty(booked)
                        + " are already in stock, so nothing was booked; book a stock count to correct it";
            }
            lot.set("receivedQty", Double.valueOf(MfgSupport.round2(Math.max(booked, qtyOk))));
            lot.set("unitPrice", Double.valueOf(unitCost));
            if (lot.get("inspectStatusId") == null) {
                lot.set("inspectStatusId", MfgServices.IQC_PASS);
            }
            delegator.store(lot);
            MfgStockLedger.recalcLotStock(delegator, lot);

            // a work order started by a sales order locks the fresh units for that line right away
            for (GenericValue item : EntityQuery.use(delegator).from("MfgSalesOrderItem")
                    .where("workOrderId", workOrderId).queryList()) {
                long lineSeq = item.getLong("lineSeq").longValue();
                double need = MfgServices.openQtyOf(MfgSupport.numberOrZero(item.get("qty")),
                        MfgServices.orderLineReserved(delegator, item.getString("salesOrderId"), lineSeq),
                        MfgSupport.numberOrZero(item.get("shippedQty")));
                if (need > 0.0001) {
                    MfgServices.reserveOrderLine(delegator, item.getString("salesOrderId"), item, need,
                            MfgSupport.toText(context.get("operatorName")));
                }
            }

            order.set("statusId", MfgServices.WO_COMPLETED);
            if (order.get("completeDate") == null) {
                order.set("completeDate", day);
            }
            delegator.store(order);
            for (GenericValue unit : EntityQuery.use(delegator).from("MfgSn")
                    .where("workOrderId", workOrderId).queryList()) {
                if (MfgServices.SN_IN_PROGRESS.equals(unit.getString("statusId"))) {
                    unit.set("statusId", MfgServices.SN_COMPLETED);
                    delegator.store(unit);
                }
            }

            Map<String, Object> result = ServiceUtil.returnSuccess();
            result.put("workOrderId", workOrderId);
            result.put("lotId", lotId);
            result.put("qtyOk", Double.valueOf(qtyOk));
            result.put("completedQty", Double.valueOf(added));
            result.put("onHandQty", lot.get("onHandQty"));
            result.put("unitCost", Double.valueOf(unitCost));
            if (message != null) {
                result.put("message", message);
            }
            return result;
        } catch (GenericEntityException e) {
            return ServiceUtil.returnError("Could not complete the work order: " + e.getMessage());
        }
    }

    /**
     * Formats a serial number as {@code prefix-yyMMdd-NNNN}.
     *
     * @param prefix the model prefix
     * @param date the day of production
     * @param sequence the running number, starting at 1
     * @return the formatted serial number
     */
    static String formatSn(String prefix, LocalDate date, int sequence) {
        return prefix + "-" + date.format(MfgServices.DAY) + "-" + String.format(Locale.ROOT, "%04d", sequence);
    }

    /**
     * Formats a work order identifier as {@code WO-yyMMdd-NNN}.
     *
     * @param date the creation day
     * @param sequence the running number, starting at 1
     * @return the formatted work order identifier
     */
    static String formatWorkOrderId(LocalDate date, int sequence) {
        return "WO-" + date.format(MfgServices.DAY) + "-" + String.format(Locale.ROOT, "%03d", sequence);
    }

    static String nextWorkOrderId(Delegator delegator, LocalDate today) throws GenericEntityException {
        String prefix = "WO-" + today.format(MfgServices.DAY) + "-";
        List<GenericValue> existing = EntityQuery.use(delegator).from("MfgWorkOrder").queryList();
        int max = 0;
        for (GenericValue order : existing) {
            String id = order.getString("workOrderId");
            if (id != null && id.startsWith(prefix)) {
                max = Math.max(max, MfgServices.parseTrailingNumber(id.substring(prefix.length())));
            }
        }
        return formatWorkOrderId(today, max + 1);
    }

    static int nextSnSequence(Delegator delegator, String prefix) throws GenericEntityException {
        String stem = prefix + "-" + LocalDate.now().format(MfgServices.DAY) + "-";
        List<GenericValue> existing = EntityQuery.use(delegator).from("MfgSn").queryList();
        int max = 0;
        for (GenericValue unit : existing) {
            String sn = unit.getString("sn");
            if (sn != null && sn.startsWith(stem)) {
                max = Math.max(max, MfgServices.parseTrailingNumber(sn.substring(stem.length())));
            }
        }
        return max + 1;
    }

    static double sumHours(Delegator delegator, String workOrderId) {
        try {
            return sumHours(EntityQuery.use(delegator).from("MfgReport")
                    .where("workOrderId", workOrderId).queryList());
        } catch (GenericEntityException e) {
            return 0.0;
        }
    }

    static double sumHours(List<GenericValue> reports) {
        double total = 0.0;
        for (GenericValue report : reports) {
            Double hours = report.getDouble("workHours");
            if (hours != null) {
                total += hours.doubleValue();
            }
        }
        return total;
    }

    static long sumQty(List<GenericValue> reports, String field) {
        long total = 0;
        for (GenericValue report : reports) {
            Long qty = report.getLong(field);
            if (qty != null) {
                total += qty.longValue();
            }
        }
        return total;
    }

    static List<Map<String, Object>> toOperationMaps(List<GenericValue> routings, List<GenericValue> reports) {
        List<Map<String, Object>> list = new ArrayList<>(routings.size());
        for (GenericValue routing : routings) {
            Long opSeq = routing.getLong("opSeq");
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("opSeq", opSeq);
            map.put("opName", routing.getString("opName"));
            map.put("workCenter", routing.getString("workCenter"));
            map.put("stdHours", routing.getDouble("stdHours"));
            double hours = 0.0;
            long qtyOk = 0;
            long qtyNg = 0;
            for (GenericValue report : reports) {
                Long reportOp = report.getLong("opSeq");
                if (reportOp != null && reportOp.equals(opSeq)) {
                    Double reportHours = report.getDouble("workHours");
                    hours += reportHours == null ? 0.0 : reportHours.doubleValue();
                    Long ok = report.getLong("qtyOk");
                    Long ng = report.getLong("qtyNg");
                    qtyOk += ok == null ? 0 : ok.longValue();
                    qtyNg += ng == null ? 0 : ng.longValue();
                }
            }
            map.put("reportedHours", Double.valueOf(hours));
            map.put("qtyOk", Long.valueOf(qtyOk));
            map.put("qtyNg", Long.valueOf(qtyNg));
            list.add(map);
        }
        return list;
    }

    static List<Map<String, Object>> toSnMaps(List<GenericValue> units) {
        List<Map<String, Object>> list = new ArrayList<>(units.size());
        for (GenericValue unit : units) {
            list.add(MfgServices.toSnMap(unit));
        }
        return list;
    }

    static List<Map<String, Object>> toReportMaps(List<GenericValue> reports) {
        List<Map<String, Object>> list = new ArrayList<>(reports.size());
        for (GenericValue report : reports) {
            list.add(MfgServices.toReportMap(report));
        }
        return list;
    }

    static List<Map<String, Object>> toDefectMaps(List<GenericValue> defects) {
        List<Map<String, Object>> list = new ArrayList<>(defects.size());
        for (GenericValue defect : defects) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("defectId", defect.getString("defectId"));
            map.put("workOrderId", defect.getString("workOrderId"));
            map.put("sn", defect.getString("sn"));
            map.put("opSeq", defect.getLong("opSeq"));
            map.put("defectCode", defect.getString("defectCode"));
            map.put("qty", defect.getLong("qty"));
            map.put("responsibility", defect.getString("responsibility"));
            map.put("repairHours", defect.getDouble("repairHours"));
            map.put("createdStamp", defect.get("createdStamp"));
            list.add(map);
        }
        return list;
    }

    /**
     * BOM lines a work order is built with: the version frozen on the order, otherwise the one the
     * model uses now.
     *
     * @param delegator the delegator
     * @param order the work order
     * @return the BOM lines, empty when the model has no BOM
     * @throws GenericEntityException when the BOM cannot be read
     */
    static List<GenericValue> bomItemsForOrder(Delegator delegator, GenericValue order)
            throws GenericEntityException {
        String versionId = MfgSupport.toText(order.getString("bomVersionId"));
        if (versionId == null) {
            versionId = MfgProductServices.currentBomVersionId(delegator, order.getString("productCode"));
        }
        return versionId == null ? new ArrayList<GenericValue>() : MfgProductServices.bomItemsOfVersion(delegator, versionId);
    }

    /**
     * Records the outsourced processing fee of one work order.
     *
     * @param dctx the dispatch context
     * @param context service context, see {@code servicedef/services.xml#mfgWorkOrderOutsourceSave}
     * @return the service result containing {@code workOrderId} and the stored amount
     */
    public static Map<String, Object> workOrderOutsourceSave(DispatchContext dctx, Map<String, ? extends Object> context) {
        String denied = MfgServices.permissionProblem(dctx, context, MfgServices.PERM_PRODUCTION, MfgServices.PERM_COST);
        if (denied != null) {
            return ServiceUtil.returnError(denied);
        }
        Delegator delegator = dctx.getDelegator();
        String workOrderId = MfgSupport.toText(context.get("workOrderId"));
        if (workOrderId == null) {
            return ServiceUtil.returnError("workOrderId is required");
        }
        try {
            GenericValue order = EntityQuery.use(delegator).from("MfgWorkOrder")
                    .where("workOrderId", workOrderId).queryOne();
            if (order == null) {
                return ServiceUtil.returnError("Unknown work order [" + workOrderId + "]");
            }
            double amount = MfgSupport.round2(MfgSupport.toDouble(context.get("outsourceAmount"), 0.0));
            if (amount < 0.0) {
                return ServiceUtil.returnError("The outsourced processing fee cannot be negative");
            }
            // once the fee is broken down into lines the lines are the truth, not this one number
            if (!EntityQuery.use(delegator).from("MfgOutsourceFee")
                    .where("workOrderId", workOrderId).queryList().isEmpty()) {
                return ServiceUtil.returnError("Work order [" + workOrderId
                        + "] already has outsourced fee lines, change them instead of the total");
            }
            order.set("outsourceAmount", Double.valueOf(amount));
            delegator.store(order);

            Map<String, Object> result = ServiceUtil.returnSuccess();
            result.put("workOrderId", workOrderId);
            result.put("outsourceAmount", Double.valueOf(amount));
            return result;
        } catch (GenericEntityException e) {
            return ServiceUtil.returnError("Could not store the outsourced fee: " + e.getMessage());
        }
    }

    /**
     * Whether a type is one of the outsourced fee types.
     *
     * @param feeTypeId the type to test
     * @return {@code true} when it is known
     */
    static boolean isKnownOutsourceType(String feeTypeId) {
        for (String type : MfgServices.OUTSRC_TYPES) {
            if (type.equals(feeTypeId)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether a basis is one of the outsourced fee bases.
     *
     * @param basisId the basis to test
     * @return {@code true} when it is known
     */
    static boolean isKnownOutsourceBasis(String basisId) {
        for (String basis : MfgServices.OUTSRC_BASES) {
            if (basis.equals(basisId)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Amount of one outsourced fee line. A lump sum is its own amount; a price per board is
     * multiplied by the units (defaulting to the good units, then to the planned ones); a price per
     * placement point is multiplied by the points.
     *
     * @param basisId how the line is priced
     * @param rate the price of one unit / point, or the lump sum
     * @param qty how many units or points, {@code 0} to take the good units
     * @param qtyOk the good units of the work order
     * @param plannedQty the planned units of the work order
     * @return the line amount
     */
    static double outsourceAmount(String basisId, double rate, double qty, double qtyOk, double plannedQty) {
        if (MfgServices.OUTSRC_LUMP.equals(basisId)) {
            return MfgSupport.round2(rate);
        }
        double count = qty;
        if (count <= 0.0) {
            count = MfgServices.OUTSRC_PER_UNIT.equals(basisId) ? (qtyOk > 0.0 ? qtyOk : plannedQty) : 0.0;
        }
        return MfgSupport.round2(rate * count);
    }

    /**
     * One outsourced amount spread over the units it was spent on.
     *
     * @param amount the total outsourced cost
     * @param qtyOk the good units, preferred
     * @param plannedQty the planned units, used while nothing is finished yet
     * @return the cost per unit, {@code 0} when there is no unit
     */
    static double outsourcePerUnit(double amount, double qtyOk, double plannedQty) {
        double base = qtyOk > 0.0 ? qtyOk : plannedQty;
        return base > 0.0 ? MfgSupport.round2(amount / base) : 0.0;
    }

    /**
     * Saves one line of outsourced processing cost and refreshes the total of the work order, so the
     * single number the cost report uses never drifts away from its lines.
     *
     * @param dctx the dispatch context
     * @param context service context, see {@code servicedef/services.xml#mfgOutsourceFeeSave}
     * @return the service result with the line amount and the new totals
     */
    public static Map<String, Object> outsourceFeeSave(DispatchContext dctx, Map<String, ? extends Object> context) {
        String denied = MfgServices.permissionProblem(dctx, context, MfgServices.PERM_PRODUCTION, MfgServices.PERM_COST);
        if (denied != null) {
            return ServiceUtil.returnError(denied);
        }
        Delegator delegator = dctx.getDelegator();
        String workOrderId = MfgSupport.toText(context.get("workOrderId"));
        String feeTypeId = MfgSupport.toText(context.get("feeTypeId"));
        String basisId = MfgSupport.toText(context.get("basisId"));
        if (workOrderId == null) {
            return ServiceUtil.returnError("workOrderId is required");
        }
        if (feeTypeId == null) {
            feeTypeId = MfgServices.OUTSRC_OTHER;
        }
        if (!isKnownOutsourceType(feeTypeId)) {
            return ServiceUtil.returnError("Unknown fee type [" + feeTypeId + "], expected "
                    + String.join(" / ", MfgServices.OUTSRC_TYPES));
        }
        if (basisId == null) {
            basisId = MfgServices.OUTSRC_LUMP;
        }
        if (!isKnownOutsourceBasis(basisId)) {
            return ServiceUtil.returnError("Unknown pricing basis [" + basisId + "], expected "
                    + String.join(" / ", MfgServices.OUTSRC_BASES));
        }
        double rate = MfgSupport.round2(MfgSupport.toDouble(context.get("rate"), 0.0));
        double qty = MfgSupport.round2(MfgSupport.toDouble(context.get("qty"), 0.0));
        if (rate < 0.0) {
            return ServiceUtil.returnError("The price cannot be negative");
        }
        if (qty < 0.0) {
            return ServiceUtil.returnError("The quantity cannot be negative");
        }
        try {
            GenericValue order = EntityQuery.use(delegator).from("MfgWorkOrder")
                    .where("workOrderId", workOrderId).queryOne();
            if (order == null) {
                return ServiceUtil.returnError("Unknown work order [" + workOrderId + "]");
            }
            double qtyOk = 0.0;
            for (GenericValue report : EntityQuery.use(delegator).from("MfgReport")
                    .where("workOrderId", workOrderId).queryList()) {
                qtyOk += MfgSupport.numberOrZero(report.get("qtyOk"));
            }
            double plannedQty = MfgSupport.numberOrZero(order.get("plannedQty"));
            if (MfgServices.OUTSRC_LUMP.equals(basisId)) {
                qty = 1.0;
            } else if (qty <= 0.0) {
                qty = qtyOk > 0.0 ? qtyOk : plannedQty;
            }
            double amount = outsourceAmount(basisId, rate, qty, qtyOk, plannedQty);

            long seq = MfgSupport.toLong(context.get("seqId"), 0);
            GenericValue fee;
            if (seq > 0) {
                fee = EntityQuery.use(delegator).from("MfgOutsourceFee")
                        .where("workOrderId", workOrderId, "seqId", Long.valueOf(seq)).queryOne();
                if (fee == null) {
                    return ServiceUtil.returnError("Unknown fee line [" + seq + "] of [" + workOrderId + "]");
                }
            } else {
                fee = delegator.makeValue("MfgOutsourceFee");
                fee.set("workOrderId", workOrderId);
                fee.set("seqId", Long.valueOf(nextOutsourceSeq(delegator, workOrderId)));
            }
            fee.set("feeTypeId", feeTypeId);
            fee.set("basisId", basisId);
            fee.set("rate", Double.valueOf(rate));
            fee.set("qty", Double.valueOf(qty));
            fee.set("amount", Double.valueOf(amount));
            fee.set("vendorName", MfgSupport.toText(context.get("vendorName")));
            fee.set("referenceNo", MfgSupport.toText(context.get("referenceNo")));
            Object feeDate = MfgSupport.parseDate(context.get("feeDate"));
            fee.set("feeDate", feeDate == null ? null : feeDate);
            if (context.containsKey("note")) {
                fee.set("note", MfgSupport.toText(context.get("note")));
            }
            delegator.createOrStore(fee);

            double total = storeOutsourceTotal(delegator, order);
            Map<String, Object> result = ServiceUtil.returnSuccess();
            result.put("workOrderId", workOrderId);
            result.put("seqId", fee.get("seqId"));
            result.put("amount", Double.valueOf(amount));
            result.put("outsourceAmount", Double.valueOf(total));
            result.put("outsourcePerUnit", Double.valueOf(outsourcePerUnit(total, qtyOk, plannedQty)));
            return result;
        } catch (GenericEntityException e) {
            return ServiceUtil.returnError("Could not store the outsourced fee: " + e.getMessage());
        }
    }

    /**
     * Deletes one line of outsourced processing cost and refreshes the total of the work order.
     *
     * @param dctx the dispatch context
     * @param context service context, see {@code servicedef/services.xml#mfgOutsourceFeeDelete}
     * @return the service result with the new total
     */
    public static Map<String, Object> outsourceFeeDelete(DispatchContext dctx, Map<String, ? extends Object> context) {
        String denied = MfgServices.permissionProblem(dctx, context, MfgServices.PERM_PRODUCTION, MfgServices.PERM_COST);
        if (denied != null) {
            return ServiceUtil.returnError(denied);
        }
        Delegator delegator = dctx.getDelegator();
        String workOrderId = MfgSupport.toText(context.get("workOrderId"));
        long seq = MfgSupport.toLong(context.get("seqId"), 0);
        if (workOrderId == null || seq <= 0) {
            return ServiceUtil.returnError("workOrderId and seqId are required");
        }
        try {
            GenericValue fee = EntityQuery.use(delegator).from("MfgOutsourceFee")
                    .where("workOrderId", workOrderId, "seqId", Long.valueOf(seq)).queryOne();
            if (fee == null) {
                return ServiceUtil.returnError("Unknown fee line [" + seq + "] of [" + workOrderId + "]");
            }
            delegator.removeValue(fee);
            GenericValue order = EntityQuery.use(delegator).from("MfgWorkOrder")
                    .where("workOrderId", workOrderId).queryOne();
            double total = order == null ? 0.0 : storeOutsourceTotal(delegator, order);
            Map<String, Object> result = ServiceUtil.returnSuccess();
            result.put("workOrderId", workOrderId);
            result.put("seqId", Long.valueOf(seq));
            result.put("outsourceAmount", Double.valueOf(total));
            return result;
        } catch (GenericEntityException e) {
            return ServiceUtil.returnError("Could not delete the outsourced fee: " + e.getMessage());
        }
    }

    /**
     * The outsourced fee lines of a work order with their total and what one unit carries.
     *
     * @param dctx the dispatch context
     * @param context service context, see {@code servicedef/services.xml#mfgOutsourceFeeList}
     * @return the service result containing {@code fees}
     */
    public static Map<String, Object> outsourceFeeList(DispatchContext dctx, Map<String, ? extends Object> context) {
        Delegator delegator = dctx.getDelegator();
        String workOrderId = MfgSupport.toText(context.get("workOrderId"));
        Map<String, Object> result = ServiceUtil.returnSuccess();
        result.put("fees", new ArrayList<Map<String, Object>>());
        result.put("outsourceAmount", Double.valueOf(0.0));
        result.put("outsourcePerUnit", Double.valueOf(0.0));
        result.put("feeCount", Integer.valueOf(0));
        if (workOrderId == null) {
            return result;
        }
        try {
            List<Map<String, Object>> rows = new ArrayList<>();
            double total = 0.0;
            for (GenericValue fee : EntityQuery.use(delegator).from("MfgOutsourceFee")
                    .where("workOrderId", workOrderId).orderBy("seqId").queryList()) {
                Map<String, Object> map = new LinkedHashMap<>();
                map.put("seqId", fee.get("seqId"));
                map.put("feeTypeId", fee.getString("feeTypeId"));
                map.put("basisId", fee.getString("basisId"));
                map.put("rate", fee.get("rate"));
                map.put("qty", fee.get("qty"));
                map.put("amount", fee.get("amount"));
                map.put("vendorName", fee.getString("vendorName"));
                map.put("referenceNo", fee.getString("referenceNo"));
                map.put("feeDate", fee.get("feeDate"));
                map.put("note", fee.getString("note"));
                rows.add(map);
                total += MfgSupport.numberOrZero(fee.get("amount"));
            }
            GenericValue order = EntityQuery.use(delegator).from("MfgWorkOrder")
                    .where("workOrderId", workOrderId).queryOne();
            double qtyOk = 0.0;
            double plannedQty = order == null ? 0.0 : MfgSupport.numberOrZero(order.get("plannedQty"));
            for (GenericValue report : EntityQuery.use(delegator).from("MfgReport")
                    .where("workOrderId", workOrderId).queryList()) {
                qtyOk += MfgSupport.numberOrZero(report.get("qtyOk"));
            }
            result.put("fees", rows);
            result.put("feeCount", Integer.valueOf(rows.size()));
            result.put("outsourceAmount", Double.valueOf(MfgSupport.round2(total)));
            result.put("outsourcePerUnit", Double.valueOf(outsourcePerUnit(total, qtyOk, plannedQty)));
            result.put("qtyOk", Double.valueOf(MfgSupport.round2(qtyOk)));
            result.put("plannedQty", Double.valueOf(MfgSupport.round2(plannedQty)));
            return result;
        } catch (GenericEntityException e) {
            return ServiceUtil.returnError("Could not read the outsourced fees: " + e.getMessage());
        }
    }

    /**
     * Adds up the fee lines of a work order and stores the total on it.
     *
     * @return the total
     */
    static double storeOutsourceTotal(Delegator delegator, GenericValue order)
            throws GenericEntityException {
        double total = 0.0;
        for (GenericValue fee : EntityQuery.use(delegator).from("MfgOutsourceFee")
                .where("workOrderId", order.getString("workOrderId")).queryList()) {
            total += MfgSupport.numberOrZero(fee.get("amount"));
        }
        total = MfgSupport.round2(total);
        order.set("outsourceAmount", Double.valueOf(total));
        delegator.store(order);
        return total;
    }

    static long nextOutsourceSeq(Delegator delegator, String workOrderId)
            throws GenericEntityException {
        long max = 0;
        for (GenericValue fee : EntityQuery.use(delegator).from("MfgOutsourceFee")
                .where("workOrderId", workOrderId).queryList()) {
            long seq = fee.getLong("seqId") == null ? 0L : fee.getLong("seqId").longValue();
            max = Math.max(max, seq);
        }
        return max + 1L;
    }
}
