/*
 * This library is part of OFBiz-MFG component of Langhua
 *
 * Copyright (C) 2010  Langhua Opensource Foundation (http://langhua.org)
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3.0 of the License, or (at your option) any later version.
 */

package org.langhua.ofbiz.mfg;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
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
 * Services of the hardware assembly component: product models with BOM and routing,
 * work orders, serial numbers, work reports, defects and the shop floor dashboard.
 *
 * <p>All services return plain {@link Map} / {@link List} structures instead of
 * {@link GenericValue} instances so the values can be rendered by FreeMarker templates.
 */
public final class MfgServices {

    public static final String MODULE = MfgServices.class.getName();

    public static final String STATUS_ACTIVE = "MFG_ACTIVE";
    public static final String STATUS_INACTIVE = "MFG_INACTIVE";

    public static final String WO_CREATED = "MFG_CREATED";
    public static final String WO_IN_PROGRESS = "MFG_IN_PROGRESS";
    public static final String WO_COMPLETED = "MFG_COMPLETED";
    public static final String WO_CLOSED = "MFG_CLOSED";

    public static final String SN_IN_PROGRESS = "MFG_IN_PROGRESS";
    public static final String SN_COMPLETED = "MFG_COMPLETED";
    public static final String SN_SCRAPPED = "MFG_SCRAPPED";

    public static final String SHIP_OUT = "MFG_SHIP_OUT";
    public static final String SHIP_IN = "MFG_SHIP_IN";

    public static final String SN_SHIPPED = "MFG_SHIPPED";

    public static final String RMA_OPEN = "MFG_RMA_OPEN";
    public static final String RMA_REPAIRED = "MFG_RMA_REPAIRED";
    public static final String RMA_REPLACED = "MFG_RMA_REPLACED";
    public static final String RMA_CLOSED = "MFG_RMA_CLOSED";

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyMMdd");

    private MfgServices() {
    }

    /**
     * Creates or updates a product model.
     *
     * @param dctx the dispatch context
     * @param context service context, see {@code servicedef/services.xml#mfgProductSave}
     * @return the service result containing {@code productCode}
     */
    public static Map<String, Object> productSave(DispatchContext dctx, Map<String, ? extends Object> context) {
        Delegator delegator = dctx.getDelegator();
        String productCode = toText(context.get("productCode"));
        if (productCode == null) {
            return ServiceUtil.returnError("productCode is required");
        }
        try {
            GenericValue product = delegator.makeValue("MfgProduct");
            product.set("productCode", productCode);
            product.set("productName", toText(context.get("productName")));
            String snPrefix = toText(context.get("snPrefix"));
            product.set("snPrefix", snPrefix == null ? productCode : snPrefix);
            product.set("standardHours", Double.valueOf(toDouble(context.get("standardHours"), 0.0)));
            long warrantyMonths = toLong(context.get("warrantyMonths"), 12);
            product.set("warrantyMonths", Long.valueOf(warrantyMonths <= 0 ? 12 : warrantyMonths));
            String statusId = toText(context.get("statusId"));
            product.set("statusId", statusId == null ? STATUS_ACTIVE : statusId);
            product.set("note", toText(context.get("note")));
            delegator.createOrStore(product);

            Map<String, Object> result = ServiceUtil.returnSuccess();
            result.put("productCode", productCode);
            return result;
        } catch (GenericEntityException e) {
            return ServiceUtil.returnError("Could not save the product model: " + e.getMessage());
        }
    }

    /**
     * Imports BOM lines from CSV text. Each line is
     * {@code componentCode,componentName,qty,designators,isKeyPart}. When {@code replace} is
     * {@code Y} the existing BOM of the model is removed first.
     *
     * @param dctx the dispatch context
     * @param context service context, see {@code servicedef/services.xml#mfgBomImport}
     * @return the service result containing {@code importedCount}
     */
    public static Map<String, Object> bomImport(DispatchContext dctx, Map<String, ? extends Object> context) {
        Delegator delegator = dctx.getDelegator();
        String productCode = toText(context.get("productCode"));
        String csv = toText(context.get("csv"));
        if (productCode == null) {
            return ServiceUtil.returnError("productCode is required");
        }
        if (csv == null) {
            return ServiceUtil.returnError("csv is required");
        }
        List<String[]> rows = parseBomCsv(csv);
        if (rows.isEmpty()) {
            return ServiceUtil.returnError("No BOM line could be parsed, expected componentCode,componentName,qty,designators,isKeyPart");
        }
        try {
            if (!"N".equals(toText(context.get("replace")))) {
                delegator.removeByAnd("MfgBomItem", "productCode", productCode);
            }
            long lineSeq = 10;
            int imported = 0;
            for (String[] row : rows) {
                GenericValue item = delegator.makeValue("MfgBomItem");
                item.set("productCode", productCode);
                item.set("lineSeq", Long.valueOf(lineSeq));
                item.set("componentCode", row[0]);
                item.set("componentName", row[1] == null ? row[0] : row[1]);
                item.set("qty", Double.valueOf(toDouble(row[2], 1.0)));
                item.set("designators", row[3]);
                item.set("isKeyPart", "Y".equalsIgnoreCase(row[4]) ? "Y" : "N");
                delegator.create(item);
                lineSeq += 10;
                imported++;
            }
            Map<String, Object> result = ServiceUtil.returnSuccess();
            result.put("productCode", productCode);
            // String, so that the value can be carried by the redirect to the product page
            result.put("importedCount", String.valueOf(imported));
            return result;
        } catch (GenericEntityException e) {
            return ServiceUtil.returnError("Could not import the BOM: " + e.getMessage());
        }
    }

    /**
     * Creates or updates one routing operation of a product model.
     *
     * @param dctx the dispatch context
     * @param context service context, see {@code servicedef/services.xml#mfgRoutingSave}
     * @return the service result containing {@code productCode} and {@code opSeq}
     */
    public static Map<String, Object> routingSave(DispatchContext dctx, Map<String, ? extends Object> context) {
        Delegator delegator = dctx.getDelegator();
        String productCode = toText(context.get("productCode"));
        long opSeq = toLong(context.get("opSeq"), 0);
        String opName = toText(context.get("opName"));
        if (productCode == null) {
            return ServiceUtil.returnError("productCode is required");
        }
        if (opSeq <= 0) {
            return ServiceUtil.returnError("opSeq must be a positive number");
        }
        if (opName == null) {
            return ServiceUtil.returnError("opName is required");
        }
        try {
            GenericValue operation = delegator.makeValue("MfgRouting");
            operation.set("productCode", productCode);
            operation.set("opSeq", Long.valueOf(opSeq));
            operation.set("opName", opName);
            operation.set("workCenter", toText(context.get("workCenter")));
            operation.set("stdHours", Double.valueOf(toDouble(context.get("stdHours"), 0.0)));
            operation.set("note", toText(context.get("note")));
            delegator.createOrStore(operation);

            Map<String, Object> result = ServiceUtil.returnSuccess();
            result.put("productCode", productCode);
            result.put("opSeq", Long.valueOf(opSeq));
            return result;
        } catch (GenericEntityException e) {
            return ServiceUtil.returnError("Could not save the routing operation: " + e.getMessage());
        }
    }

    /**
     * Creates a work order. The identifier is {@code WO-yyMMdd-NNN}.
     *
     * @param dctx the dispatch context
     * @param context service context, see {@code servicedef/services.xml#mfgWorkOrderCreate}
     * @return the service result containing {@code workOrderId}
     */
    public static Map<String, Object> workOrderCreate(DispatchContext dctx, Map<String, ? extends Object> context) {
        Delegator delegator = dctx.getDelegator();
        String productCode = toText(context.get("productCode"));
        if (productCode == null) {
            return ServiceUtil.returnError("productCode is required");
        }
        long plannedQty = toLong(context.get("plannedQty"), 0);
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
            order.set("statusId", WO_CREATED);
            order.set("dueDate", parseDate(context.get("dueDate")));
            order.set("customerName", toText(context.get("customerName")));
            order.set("smtVendor", toText(context.get("smtVendor")));
            order.set("note", toText(context.get("note")));
            delegator.create(order);

            Map<String, Object> result = ServiceUtil.returnSuccess();
            result.put("workOrderId", workOrderId);
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
        String statusId = toText(context.get("statusId"));
        try {
            EntityQuery query = EntityQuery.use(delegator).from("MfgWorkOrder");
            if (statusId != null) {
                query = query.where("statusId", statusId);
            }
            List<GenericValue> orders = query.orderBy("-createdStamp").queryList();
            List<Map<String, Object>> list = new ArrayList<>(orders.size());
            for (GenericValue order : orders) {
                Map<String, Object> map = toWorkOrderMap(order);
                map.put("reportedHours", Double.valueOf(sumHours(delegator, order.getString("workOrderId"))));
                list.add(map);
            }
            Map<String, Object> result = ServiceUtil.returnSuccess();
            result.put("workOrders", list);
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
        String workOrderId = toText(context.get("workOrderId"));
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
            result.put("workOrder", toWorkOrderMap(order));
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
            result.put("vendors", vendorMaps(delegator));
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
        Delegator delegator = dctx.getDelegator();
        String workOrderId = toText(context.get("workOrderId"));
        long opSeq = toLong(context.get("opSeq"), 0);
        if (workOrderId == null) {
            return ServiceUtil.returnError("workOrderId is required");
        }
        if (opSeq <= 0) {
            return ServiceUtil.returnError("opSeq is required");
        }
        double workHours = toDouble(context.get("workHours"), 0.0);
        long qtyOk = toLong(context.get("qtyOk"), 0);
        long qtyNg = toLong(context.get("qtyNg"), 0);
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
            report.set("sn", toText(context.get("sn")));
            report.set("operatorName", toText(context.get("operatorName")));
            report.set("workHours", Double.valueOf(workHours));
            report.set("qtyOk", Long.valueOf(qtyOk));
            report.set("qtyNg", Long.valueOf(qtyNg));
            report.set("reportDate", new Timestamp(System.currentTimeMillis()));
            report.set("note", toText(context.get("note")));
            delegator.create(report);

            if (!WO_CLOSED.equals(order.getString("statusId")) && !WO_COMPLETED.equals(order.getString("statusId"))) {
                order.set("statusId", WO_IN_PROGRESS);
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
        Delegator delegator = dctx.getDelegator();
        String workOrderId = toText(context.get("workOrderId"));
        long qty = toLong(context.get("qty"), 0);
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
                unit.set("statusId", SN_IN_PROGRESS);
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
     * @return the service result containing {@code snRecord}, {@code workOrder}, {@code reports}, {@code defects}, {@code lots} and {@code replacements}
     */
    public static Map<String, Object> snDetail(DispatchContext dctx, Map<String, ? extends Object> context) {
        Delegator delegator = dctx.getDelegator();
        String sn = toText(context.get("sn"));
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
                lotMaps.add(toIssueMap(issue, lot));
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
                    deliveryMap = toDeliveryMap(delivery);
                }
            }
            List<GenericValue> tests = EntityQuery.use(delegator).from("MfgTestResult")
                    .where("sn", sn).orderBy("-testDate").queryList();
            List<GenericValue> claims = EntityQuery.use(delegator).from("MfgWarrantyClaim")
                    .where("sn", sn).orderBy("-createdStamp").queryList();

            Map<String, Object> result = ServiceUtil.returnSuccess();
            result.put("snRecord", toSnMap(unit));
            result.put("workOrder", order == null ? null : toWorkOrderMap(order));
            result.put("reports", toReportMaps(unitReports));
            result.put("defects", toDefectMaps(unitDefects));
            result.put("lots", lotMaps);
            result.put("replacements", toReplacementMaps(replacements));
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
        Delegator delegator = dctx.getDelegator();
        String workOrderId = toText(context.get("workOrderId"));
        String defectCode = toText(context.get("defectCode"));
        if (workOrderId == null) {
            return ServiceUtil.returnError("workOrderId is required");
        }
        if (defectCode == null) {
            return ServiceUtil.returnError("defectCode is required");
        }
        long qty = toLong(context.get("qty"), 1);
        if (qty <= 0) {
            return ServiceUtil.returnError("qty must be a positive number");
        }
        try {
            GenericValue defect = delegator.makeValue("MfgDefect");
            defect.set("defectId", delegator.getNextSeqId("MfgDefect"));
            defect.set("workOrderId", workOrderId);
            defect.set("sn", toText(context.get("sn")));
            defect.set("opSeq", Long.valueOf(toLong(context.get("opSeq"), 0)));
            defect.set("defectCode", defectCode);
            defect.set("qty", Long.valueOf(qty));
            String responsibility = toText(context.get("responsibility"));
            defect.set("responsibility", responsibility == null ? "HOUSE" : responsibility);
            defect.set("repairHours", Double.valueOf(toDouble(context.get("repairHours"), 0.0)));
            defect.set("note", toText(context.get("note")));
            delegator.create(defect);

            Map<String, Object> result = ServiceUtil.returnSuccess();
            result.put("defectId", defect.getString("defectId"));
            return result;
        } catch (GenericEntityException e) {
            return ServiceUtil.returnError("Could not store the defect: " + e.getMessage());
        }
    }

    /**
     * Shop floor dashboard: today's output, yield and open work orders.
     *
     * @param dctx the dispatch context
     * @param context service context, see {@code servicedef/services.xml#mfgDashboard}
     * @return the service result containing the aggregated figures
     */
    public static Map<String, Object> dashboard(DispatchContext dctx, Map<String, ? extends Object> context) {
        Delegator delegator = dctx.getDelegator();
        Timestamp todayStart = Timestamp.valueOf(LocalDate.now().atStartOfDay());
        try {
            List<GenericValue> orders = EntityQuery.use(delegator).from("MfgWorkOrder").queryList();
            long openOrders = 0;
            long plannedQty = 0;
            for (GenericValue order : orders) {
                String statusId = order.getString("statusId");
                if (!WO_CLOSED.equals(statusId) && !WO_COMPLETED.equals(statusId)) {
                    openOrders++;
                    plannedQty += order.getLong("plannedQty") == null ? 0 : order.getLong("plannedQty").longValue();
                }
            }
            List<GenericValue> reports = EntityQuery.use(delegator).from("MfgReport").queryList();
            double todayHours = 0.0;
            long todayOk = 0;
            long todayNg = 0;
            List<Map<String, Object>> recent = new ArrayList<>();
            List<GenericValue> sorted = new ArrayList<>(reports);
            sorted.sort(Comparator.comparing(
                    (GenericValue value) -> value.getTimestamp("reportDate"),
                    Comparator.nullsFirst(Comparator.naturalOrder())).reversed());
            for (GenericValue report : sorted) {
                Timestamp stamp = report.getTimestamp("reportDate");
                if (stamp != null && !stamp.before(todayStart)) {
                    todayHours += report.getDouble("workHours") == null ? 0.0 : report.getDouble("workHours").doubleValue();
                    todayOk += report.getLong("qtyOk") == null ? 0 : report.getLong("qtyOk").longValue();
                    todayNg += report.getLong("qtyNg") == null ? 0 : report.getLong("qtyNg").longValue();
                }
                if (recent.size() < 15) {
                    recent.add(toReportMap(report));
                }
            }
            long totalQty = todayOk + todayNg;
            double yieldRate = totalQty == 0 ? 0.0 : (double) todayOk / (double) totalQty;

            Map<String, Object> result = ServiceUtil.returnSuccess();
            result.put("openOrderCount", Long.valueOf(openOrders));
            result.put("openPlannedQty", Long.valueOf(plannedQty));
            result.put("todayHours", Double.valueOf(todayHours));
            result.put("todayQtyOk", Long.valueOf(todayOk));
            result.put("todayQtyNg", Long.valueOf(todayNg));
            result.put("todayYield", Double.valueOf(yieldRate));
            result.put("recentReports", recent);
            return result;
        } catch (GenericEntityException e) {
            return ServiceUtil.returnError("Could not build the dashboard: " + e.getMessage());
        }
    }

    // ------------------------------------------------------------ material and outsourcing

    /**
     * Creates or updates one material lot. When {@code lotId} is empty a new identifier
     * {@code LOT-yyMMdd-NN} is generated.
     *
     * @param dctx the dispatch context
     * @param context service context, see {@code servicedef/services.xml#mfgLotSave}
     * @return the service result containing {@code lotId}
     */
    public static Map<String, Object> lotSave(DispatchContext dctx, Map<String, ? extends Object> context) {
        Delegator delegator = dctx.getDelegator();
        String componentCode = toText(context.get("componentCode"));
        if (componentCode == null) {
            return ServiceUtil.returnError("componentCode is required");
        }
        double receivedQty = toDouble(context.get("receivedQty"), 0.0);
        if (receivedQty <= 0.0) {
            return ServiceUtil.returnError("receivedQty must be greater than zero");
        }
        try {
            String lotId = toText(context.get("lotId"));
            if (lotId == null) {
                lotId = nextLotId(delegator, LocalDate.now());
            }
            GenericValue lot = delegator.makeValue("MfgMaterialLot");
            lot.set("lotId", lotId);
            lot.set("componentCode", componentCode);
            lot.set("componentName", toText(context.get("componentName")));
            lot.set("supplierName", toText(context.get("supplierName")));
            lot.set("supplierLot", toText(context.get("supplierLot")));
            lot.set("receivedQty", Double.valueOf(receivedQty));
            Object receivedDate = parseDate(context.get("receivedDate"));
            lot.set("receivedDate", receivedDate == null
                    ? java.sql.Date.valueOf(LocalDate.now()) : receivedDate);
            lot.set("note", toText(context.get("note")));
            delegator.createOrStore(lot);

            Map<String, Object> result = ServiceUtil.returnSuccess();
            result.put("lotId", lotId);
            return result;
        } catch (GenericEntityException e) {
            return ServiceUtil.returnError("Could not save the material lot: " + e.getMessage());
        }
    }

    /**
     * Lists material lots, newest first, with the quantity already issued.
     *
     * @param dctx the dispatch context
     * @param context service context, see {@code servicedef/services.xml#mfgLotList}
     * @return the service result containing {@code lots}
     */
    public static Map<String, Object> lotList(DispatchContext dctx, Map<String, ? extends Object> context) {
        Delegator delegator = dctx.getDelegator();
        String componentCode = toText(context.get("componentCode"));
        try {
            EntityQuery query = EntityQuery.use(delegator).from("MfgMaterialLot");
            if (componentCode != null) {
                query = query.where("componentCode", componentCode);
            }
            List<GenericValue> lots = query.orderBy("-createdStamp").queryList();
            Map<String, Double> issued = issuedQtyByLot(delegator);
            List<Map<String, Object>> list = new ArrayList<>(lots.size());
            for (GenericValue lot : lots) {
                list.add(toLotMap(lot, issued.get(lot.getString("lotId"))));
            }
            Map<String, Object> result = ServiceUtil.returnSuccess();
            result.put("lots", list);
            result.put("componentCode", componentCode);
            return result;
        } catch (GenericEntityException e) {
            return ServiceUtil.returnError("Could not read the material lots: " + e.getMessage());
        }
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
        Delegator delegator = dctx.getDelegator();
        String workOrderId = toText(context.get("workOrderId"));
        String componentCode = toText(context.get("componentCode"));
        String lotId = toText(context.get("lotId"));
        double qty = toDouble(context.get("qty"), 0.0);
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
            String lotComponent = lot.getString("componentCode");
            if (lotComponent != null && !lotComponent.equals(componentCode)) {
                return ServiceUtil.returnError("Lot [" + lotId + "] belongs to component [" + lotComponent
                        + "], not [" + componentCode + "]");
            }
            String issueId = delegator.getNextSeqId("MfgMaterialIssue");
            GenericValue issue = delegator.makeValue("MfgMaterialIssue");
            issue.set("issueId", issueId);
            issue.set("workOrderId", workOrderId);
            issue.set("componentCode", componentCode);
            issue.set("lotId", lotId);
            issue.set("qty", Double.valueOf(qty));
            Object issuedDate = parseDate(context.get("issuedDate"));
            issue.set("issuedDate", issuedDate == null
                    ? java.sql.Date.valueOf(LocalDate.now()) : issuedDate);
            issue.set("designators", toText(context.get("designators")));
            issue.set("snFrom", toText(context.get("snFrom")));
            issue.set("snTo", toText(context.get("snTo")));
            issue.set("note", toText(context.get("note")));
            delegator.create(issue);

            Map<String, Object> result = ServiceUtil.returnSuccess();
            result.put("issueId", issueId);
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
        String workOrderId = toText(context.get("workOrderId"));
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
                issueMaps.add(toIssueMap(issue, lot));
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
                if (SHIP_IN.equals(shipment.getString("directionId"))) {
                    inQty += qty;
                    inDefectQty += defectQty;
                } else {
                    outQty += qty;
                }
            }

            Map<String, Object> result = ServiceUtil.returnSuccess();
            result.put("workOrder", toWorkOrderMap(order));
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
        Delegator delegator = dctx.getDelegator();
        String workOrderId = toText(context.get("workOrderId"));
        String directionId = toText(context.get("directionId"));
        if (workOrderId == null) {
            return ServiceUtil.returnError("workOrderId is required");
        }
        if (directionId == null) {
            directionId = SHIP_OUT;
        }
        if (!SHIP_IN.equals(directionId) && !SHIP_OUT.equals(directionId)) {
            return ServiceUtil.returnError("directionId must be " + SHIP_OUT + " or " + SHIP_IN);
        }
        double qty = toDouble(context.get("qty"), 0.0);
        double defectQty = toDouble(context.get("defectQty"), 0.0);
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
            Object shipmentDate = parseDate(context.get("shipmentDate"));
            shipment.set("shipmentDate", shipmentDate == null
                    ? java.sql.Date.valueOf(LocalDate.now()) : shipmentDate);
            shipment.set("qty", Double.valueOf(qty));
            shipment.set("defectQty", Double.valueOf(defectQty));
            shipment.set("carrier", toText(context.get("carrier")));
            shipment.set("trackingNo", toText(context.get("trackingNo")));
            shipment.set("note", toText(context.get("note")));
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
        String lotId = toText(context.get("lotId"));
        String supplierLot = toText(context.get("supplierLot"));
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
                lotMaps.add(toLotMap(lot, issuedByLot.get(lot.getString("lotId"))));
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

    private static Map<String, Double> issuedQtyByLot(Delegator delegator) throws GenericEntityException {
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
    private static List<Map<String, Object>> estimateSnRanges(Delegator delegator, String workOrderId,
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

    private static Map<String, Object> toLotMap(GenericValue lot, Double issuedQty) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("lotId", lot.getString("lotId"));
        map.put("componentCode", lot.getString("componentCode"));
        map.put("componentName", lot.getString("componentName"));
        map.put("supplierName", lot.getString("supplierName"));
        map.put("supplierLot", lot.getString("supplierLot"));
        map.put("receivedQty", lot.getDouble("receivedQty"));
        map.put("receivedDate", lot.getDate("receivedDate"));
        map.put("issuedQty", issuedQty == null ? Double.valueOf(0.0) : issuedQty);
        double received = lot.getDouble("receivedQty") == null ? 0.0 : lot.getDouble("receivedQty").doubleValue();
        double issued = issuedQty == null ? 0.0 : issuedQty.doubleValue();
        map.put("remainingQty", Double.valueOf(received - issued));
        map.put("note", lot.getString("note"));
        return map;
    }

    private static Map<String, Object> toIssueMap(GenericValue issue, GenericValue lot) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("issueId", issue.getString("issueId"));
        map.put("workOrderId", issue.getString("workOrderId"));
        map.put("componentCode", issue.getString("componentCode"));
        map.put("lotId", issue.getString("lotId"));
        map.put("qty", issue.getDouble("qty"));
        map.put("issuedDate", issue.getDate("issuedDate"));
        map.put("designators", issue.getString("designators"));
        map.put("snFrom", issue.getString("snFrom"));
        map.put("snTo", issue.getString("snTo"));
        map.put("note", issue.getString("note"));
        if (lot != null) {
            map.put("supplierName", lot.getString("supplierName"));
            map.put("supplierLot", lot.getString("supplierLot"));
            map.put("receivedDate", lot.getDate("receivedDate"));
        }
        return map;
    }

    private static Map<String, Object> toShipmentMap(GenericValue shipment) {
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

    private static List<Map<String, Object>> toReplacementMaps(List<GenericValue> replacements) {
        List<Map<String, Object>> list = new ArrayList<>(replacements.size());
        for (GenericValue replacement : replacements) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("replacementId", replacement.getString("replacementId"));
            map.put("sn", replacement.getString("sn"));
            map.put("workOrderId", replacement.getString("workOrderId"));
            map.put("componentCode", replacement.getString("componentCode"));
            map.put("opSeq", replacement.getLong("opSeq"));
            map.put("oldLotId", replacement.getString("oldLotId"));
            map.put("newLotId", replacement.getString("newLotId"));
            map.put("qty", replacement.getDouble("qty"));
            map.put("replacedDate", replacement.getDate("replacedDate"));
            map.put("reason", replacement.getString("reason"));
            map.put("hours", replacement.getDouble("hours"));
            map.put("note", replacement.getString("note"));
            list.add(map);
        }
        return list;
    }

    private static String formatLotId(LocalDate date, int sequence) {
        return "LOT-" + date.format(DAY) + "-" + String.format(Locale.ROOT, "%02d", sequence);
    }

    private static String nextLotId(Delegator delegator, LocalDate today) throws GenericEntityException {
        String prefix = "LOT-" + today.format(DAY) + "-";
        List<GenericValue> existing = EntityQuery.use(delegator).from("MfgMaterialLot").queryList();
        int max = 0;
        for (GenericValue lot : existing) {
            String id = lot.getString("lotId");
            if (id != null && id.startsWith(prefix)) {
                max = Math.max(max, parseTrailingNumber(id.substring(prefix.length())));
            }
        }
        return formatLotId(today, max + 1);
    }

    /**
     * Imports material lots from CSV text. Each line is
     * {@code componentCode,componentName,supplierName,supplierLot,qty,receivedDate}.
     *
     * @param dctx the dispatch context
     * @param context service context, see {@code servicedef/services.xml#mfgLotImportCsv}
     * @return the service result containing {@code importedCount}
     */
    public static Map<String, Object> lotImportCsv(DispatchContext dctx, Map<String, ? extends Object> context) {
        Delegator delegator = dctx.getDelegator();
        String csv = toText(context.get("csv"));
        if (csv == null) {
            return ServiceUtil.returnError("csv is required");
        }
        List<String[]> rows = parseLotCsv(csv);
        if (rows.isEmpty()) {
            return ServiceUtil.returnError("No lot line could be parsed, expected "
                    + "componentCode,componentName,supplierName,supplierLot,qty,receivedDate");
        }
        try {
            int imported = 0;
            List<String> lotIds = new ArrayList<>();
            for (String[] row : rows) {
                double qty = toDouble(row[4], 0.0);
                if (qty <= 0.0) {
                    continue;
                }
                String lotId = nextLotId(delegator, LocalDate.now());
                GenericValue lot = delegator.makeValue("MfgMaterialLot");
                lot.set("lotId", lotId);
                lot.set("componentCode", row[0]);
                lot.set("componentName", row[1]);
                lot.set("supplierName", row[2]);
                lot.set("supplierLot", row[3]);
                lot.set("receivedQty", Double.valueOf(qty));
                Object receivedDate = parseDate(row[5]);
                lot.set("receivedDate", receivedDate == null
                        ? java.sql.Date.valueOf(LocalDate.now()) : receivedDate);
                delegator.create(lot);
                lotIds.add(lotId);
                imported++;
            }
            if (imported == 0) {
                return ServiceUtil.returnError("Every line had a non positive quantity, nothing imported");
            }
            Map<String, Object> result = ServiceUtil.returnSuccess();
            result.put("importedCount", String.valueOf(imported));
            result.put("lotIds", lotIds);
            return result;
        } catch (GenericEntityException e) {
            return ServiceUtil.returnError("Could not import the material lots: " + e.getMessage());
        }
    }

    /**
     * Open work orders with their material shortages, ordered by due date.
     *
     * @param dctx the dispatch context
     * @param context service context, see {@code servicedef/services.xml#mfgShortageList}
     * @return the service result containing {@code orders}, {@code orderCount} and {@code shortageOrderCount}
     */
    public static Map<String, Object> shortageList(DispatchContext dctx, Map<String, ? extends Object> context) {
        Delegator delegator = dctx.getDelegator();
        try {
            List<GenericValue> orders = EntityQuery.use(delegator).from("MfgWorkOrder")
                    .orderBy("dueDate").queryList();
            List<Map<String, Object>> rows = new ArrayList<>();
            long shortageOrders = 0;
            for (GenericValue order : orders) {
                String statusId = order.getString("statusId");
                if (WO_CLOSED.equals(statusId) || WO_COMPLETED.equals(statusId)) {
                    continue;
                }
                List<Map<String, Object>> lines = kittingLines(delegator, order);
                List<Map<String, Object>> shortages = new ArrayList<>();
                double totalShortage = 0.0;
                for (Map<String, Object> line : lines) {
                    Double shortage = (Double) line.get("shortage");
                    if (shortage != null && shortage.doubleValue() > 0.0001) {
                        shortages.add(line);
                        totalShortage += shortage.doubleValue();
                    }
                }
                Map<String, Object> row = toWorkOrderMap(order);
                row.put("lineCount", Integer.valueOf(lines.size()));
                row.put("shortageLineCount", Integer.valueOf(shortages.size()));
                row.put("shortageQty", Double.valueOf(totalShortage));
                row.put("shortages", shortages);
                if (!shortages.isEmpty()) {
                    shortageOrders++;
                }
                rows.add(row);
            }
            Map<String, Object> result = ServiceUtil.returnSuccess();
            result.put("orders", rows);
            result.put("orderCount", Integer.valueOf(rows.size()));
            result.put("shortageOrderCount", Long.valueOf(shortageOrders));
            return result;
        } catch (GenericEntityException e) {
            return ServiceUtil.returnError("Could not compute the material shortages: " + e.getMessage());
        }
    }

    /**
     * Records a component swapped on one unit during repair or rework. Unlike an SMT reel change this
     * is measured in house, so it is exact.
     *
     * @param dctx the dispatch context
     * @param context service context, see {@code servicedef/services.xml#mfgReplacementSave}
     * @return the service result containing {@code replacementId}
     */
    public static Map<String, Object> replacementSave(DispatchContext dctx, Map<String, ? extends Object> context) {
        Delegator delegator = dctx.getDelegator();
        String sn = toText(context.get("sn"));
        String componentCode = toText(context.get("componentCode"));
        if (sn == null) {
            return ServiceUtil.returnError("sn is required");
        }
        if (componentCode == null) {
            return ServiceUtil.returnError("componentCode is required");
        }
        double qty = toDouble(context.get("qty"), 1.0);
        if (qty <= 0.0) {
            return ServiceUtil.returnError("qty must be greater than zero");
        }
        try {
            GenericValue unit = EntityQuery.use(delegator).from("MfgSn").where("sn", sn).queryOne();
            if (unit == null) {
                return ServiceUtil.returnError("Unknown serial number [" + sn + "]");
            }
            String oldLotId = toText(context.get("oldLotId"));
            String newLotId = toText(context.get("newLotId"));
            if (newLotId != null
                    && EntityQuery.use(delegator).from("MfgMaterialLot").where("lotId", newLotId).queryOne() == null) {
                return ServiceUtil.returnError("Unknown material lot [" + newLotId + "]");
            }
            String replacementId = delegator.getNextSeqId("MfgReplacement");
            GenericValue replacement = delegator.makeValue("MfgReplacement");
            replacement.set("replacementId", replacementId);
            replacement.set("sn", sn);
            replacement.set("workOrderId", unit.getString("workOrderId"));
            replacement.set("componentCode", componentCode);
            replacement.set("opSeq", Long.valueOf(toLong(context.get("opSeq"), 0)));
            replacement.set("oldLotId", oldLotId);
            replacement.set("newLotId", newLotId);
            replacement.set("qty", Double.valueOf(qty));
            Object replacedDate = parseDate(context.get("replacedDate"));
            replacement.set("replacedDate", replacedDate == null
                    ? java.sql.Date.valueOf(LocalDate.now()) : replacedDate);
            replacement.set("reason", toText(context.get("reason")));
            replacement.set("hours", Double.valueOf(toDouble(context.get("hours"), 0.0)));
            replacement.set("note", toText(context.get("note")));
            delegator.create(replacement);

            Map<String, Object> result = ServiceUtil.returnSuccess();
            result.put("replacementId", replacementId);
            return result;
        } catch (GenericEntityException e) {
            return ServiceUtil.returnError("Could not record the replacement: " + e.getMessage());
        }
    }

    /**
     * BOM requirement against issued quantity of one work order.
     *
     * @param delegator the delegator
     * @param order the work order
     * @return one map per BOM line with {@code requiredQty}, {@code issuedQty} and {@code shortage}
     * @throws GenericEntityException when the BOM or the issues cannot be read
     */
    private static List<Map<String, Object>> kittingLines(Delegator delegator, GenericValue order)
            throws GenericEntityException {
        String productCode = order.getString("productCode");
        long plannedQty = toLong(order.get("plannedQty"), 0);
        List<GenericValue> bomItems = EntityQuery.use(delegator).from("MfgBomItem")
                .where("productCode", productCode).orderBy("lineSeq").queryList();
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

        List<Map<String, Object>> lines = new ArrayList<>(bomItems.size());
        for (GenericValue item : bomItems) {
            String componentCode = item.getString("componentCode");
            Double bomQty = item.getDouble("qty");
            double required = (bomQty == null ? 0.0 : bomQty.doubleValue()) * plannedQty;
            Double issued = issuedByComponent.get(componentCode);
            double issuedQty = issued == null ? 0.0 : issued.doubleValue();
            Map<String, Object> line = new LinkedHashMap<>();
            line.put("componentCode", componentCode);
            line.put("componentName", item.getString("componentName"));
            line.put("isKeyPart", item.getString("isKeyPart"));
            line.put("requiredQty", Double.valueOf(required));
            line.put("issuedQty", Double.valueOf(issuedQty));
            line.put("shortage", Double.valueOf(Math.max(0.0, required - issuedQty)));
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
     * Parses material lot CSV text into rows of
     * {@code componentCode, componentName, supplierName, supplierLot, qty, receivedDate}.
     * Empty lines and lines starting with {@code #} are ignored.
     *
     * @param csv the CSV text
     * @return the parsed rows, never {@code null}
     */
    static List<String[]> parseLotCsv(String csv) {
        List<String[]> rows = new ArrayList<>();
        if (csv == null) {
            return rows;
        }
        for (String line : csv.replace("\r\n", "\n").replace('\r', '\n').split("\n")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }
            String[] cells = splitCsvLine(trimmed);
            String code = unquote(cells[0]).toUpperCase(Locale.ROOT);
            if (code.isEmpty()) {
                continue;
            }
            String[] row = new String[6];
            row[0] = code;
            row[1] = cells.length > 1 ? unquote(cells[1]) : null;
            row[2] = cells.length > 2 ? unquote(cells[2]) : null;
            row[3] = cells.length > 3 ? unquote(cells[3]) : null;
            row[4] = cells.length > 4 ? unquote(cells[4]) : null;
            row[5] = cells.length > 5 ? unquote(cells[5]) : null;
            rows.add(row);
        }
        return rows;
    }

    // ------------------------------------------------------------- delivery and testing

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
        Delegator delegator = dctx.getDelegator();
        String customerName = toText(context.get("customerName"));
        if (customerName == null) {
            return ServiceUtil.returnError("customerName is required");
        }
        try {
            List<String> sns = parseSnList(toText(context.get("sns")));
            String workOrderId = toText(context.get("workOrderId"));
            long qty = toLong(context.get("qty"), 0);
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
            for (String sn : sns) {
                if (EntityQuery.use(delegator).from("MfgSn").where("sn", sn).queryOne() == null) {
                    return ServiceUtil.returnError("Unknown serial number [" + sn + "]");
                }
                if (isDelivered(delegator, sn)) {
                    return ServiceUtil.returnError("Serial number [" + sn + "] has already been delivered");
                }
            }

            String deliveryId = nextDeliveryId(delegator, LocalDate.now());
            GenericValue delivery = delegator.makeValue("MfgDelivery");
            delivery.set("deliveryId", deliveryId);
            delivery.set("customerName", customerName);
            delivery.set("customerPo", toText(context.get("customerPo")));
            Object deliveryDate = parseDate(context.get("deliveryDate"));
            delivery.set("deliveryDate", deliveryDate == null
                    ? java.sql.Date.valueOf(LocalDate.now()) : deliveryDate);
            delivery.set("carrier", toText(context.get("carrier")));
            delivery.set("trackingNo", toText(context.get("trackingNo")));
            delivery.set("workOrderId", workOrderId);
            delivery.set("note", toText(context.get("note")));
            delegator.create(delivery);

            for (String sn : sns) {
                GenericValue item = delegator.makeValue("MfgDeliverySn");
                item.set("deliveryId", deliveryId);
                item.set("sn", sn);
                delegator.create(item);
                GenericValue unit = EntityQuery.use(delegator).from("MfgSn").where("sn", sn).queryOne();
                if (unit != null) {
                    unit.set("statusId", SN_SHIPPED);
                    delegator.store(unit);
                }
            }

            Map<String, Object> result = ServiceUtil.returnSuccess();
            result.put("deliveryId", deliveryId);
            result.put("snCount", String.valueOf(sns.size()));
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
        String customerName = toText(context.get("customerName"));
        try {
            EntityQuery query = EntityQuery.use(delegator).from("MfgDelivery");
            if (customerName != null) {
                query = query.where("customerName", customerName);
            }
            List<GenericValue> deliveries = query.orderBy("-deliveryDate").queryList();
            List<Map<String, Object>> list = new ArrayList<>(deliveries.size());
            for (GenericValue delivery : deliveries) {
                Map<String, Object> map = toDeliveryMap(delivery);
                List<GenericValue> items = EntityQuery.use(delegator).from("MfgDeliverySn")
                        .where("deliveryId", delivery.getString("deliveryId")).queryList();
                map.put("snCount", Integer.valueOf(items.size()));
                list.add(map);
            }
            Map<String, Object> result = ServiceUtil.returnSuccess();
            result.put("deliveries", list);
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
        String deliveryId = toText(context.get("deliveryId"));
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
            result.put("delivery", toDeliveryMap(delivery));
            result.put("units", units);
            return result;
        } catch (GenericEntityException e) {
            return ServiceUtil.returnError("Could not read the delivery: " + e.getMessage());
        }
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
        Delegator delegator = dctx.getDelegator();
        String csv = toText(context.get("csv"));
        if (csv == null) {
            return ServiceUtil.returnError("csv is required");
        }
        List<String[]> rows = parseTestCsv(csv);
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
                result.set("measureValue", Double.valueOf(toDouble(row[3], 0.0)));
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
            result.put("workOrderId", toText(context.get("workOrderId")));
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
        String sn = toText(context.get("sn"));
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

    private static boolean isDelivered(Delegator delegator, String sn) throws GenericEntityException {
        return EntityQuery.use(delegator).from("MfgDeliverySn").where("sn", sn).queryOne() != null;
    }

    private static Map<String, Object> toDeliveryMap(GenericValue delivery) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("deliveryId", delivery.getString("deliveryId"));
        map.put("customerName", delivery.getString("customerName"));
        map.put("customerPo", delivery.getString("customerPo"));
        map.put("deliveryDate", delivery.getDate("deliveryDate"));
        map.put("carrier", delivery.getString("carrier"));
        map.put("trackingNo", delivery.getString("trackingNo"));
        map.put("workOrderId", delivery.getString("workOrderId"));
        map.put("note", delivery.getString("note"));
        return map;
    }

    private static List<Map<String, Object>> toTestResultMaps(List<GenericValue> results) {
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
     * Splits a whitespace or comma separated list of serial numbers.
     *
     * @param text the raw text, may be {@code null}
     * @return the serial numbers, never {@code null}
     */
    static List<String> parseSnList(String text) {
        List<String> sns = new ArrayList<>();
        if (text == null) {
            return sns;
        }
        for (String candidate : text.split("[,\\s]+")) {
            String sn = candidate.trim();
            if (!sn.isEmpty() && !sns.contains(sn)) {
                sns.add(sn);
            }
        }
        return sns;
    }

    /**
     * Parses test bench CSV text into rows of {@code sn, station, item, value, unit, pass}.
     * Empty lines and lines starting with {@code #} are ignored.
     *
     * @param csv the CSV text
     * @return the parsed rows, never {@code null}
     */
    static List<String[]> parseTestCsv(String csv) {
        List<String[]> rows = new ArrayList<>();
        if (csv == null) {
            return rows;
        }
        for (String line : csv.replace("\r\n", "\n").replace('\r', '\n').split("\n")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }
            String[] cells = splitCsvLine(trimmed);
            String sn = unquote(cells[0]);
            if (sn.isEmpty()) {
                continue;
            }
            String[] row = new String[6];
            row[0] = sn;
            row[1] = cells.length > 1 ? unquote(cells[1]) : null;
            row[2] = cells.length > 2 ? unquote(cells[2]) : null;
            row[3] = cells.length > 3 ? unquote(cells[3]) : null;
            row[4] = cells.length > 4 ? unquote(cells[4]) : null;
            row[5] = cells.length > 5 ? unquote(cells[5]) : null;
            rows.add(row);
        }
        return rows;
    }

    private static String formatDeliveryId(LocalDate date, int sequence) {
        return "DO-" + date.format(DAY) + "-" + String.format(Locale.ROOT, "%02d", sequence);
    }

    private static String nextDeliveryId(Delegator delegator, LocalDate today) throws GenericEntityException {
        String prefix = "DO-" + today.format(DAY) + "-";
        List<GenericValue> existing = EntityQuery.use(delegator).from("MfgDelivery").queryList();
        int max = 0;
        for (GenericValue delivery : existing) {
            String id = delivery.getString("deliveryId");
            if (id != null && id.startsWith(prefix)) {
                max = Math.max(max, parseTrailingNumber(id.substring(prefix.length())));
            }
        }
        return formatDeliveryId(today, max + 1);
    }

    // ---------------------------------------------------------------- vendors

    /**
     * Creates or updates an outsourcing vendor, keeping the address and the customer code that has
     * to be printed on parcels of material.
     *
     * @param dctx the dispatch context
     * @param context service context, see {@code servicedef/services.xml#mfgVendorSave}
     * @return the service result containing {@code vendorId}
     */
    public static Map<String, Object> vendorSave(DispatchContext dctx, Map<String, ? extends Object> context) {
        Delegator delegator = dctx.getDelegator();
        String vendorName = toText(context.get("vendorName"));
        if (vendorName == null) {
            return ServiceUtil.returnError("vendorName is required");
        }
        try {
            String vendorId = toText(context.get("vendorId"));
            if (vendorId == null) {
                vendorId = nextVendorId(delegator, LocalDate.now());
            }
            GenericValue vendor = delegator.makeValue("MfgVendor");
            vendor.set("vendorId", vendorId);
            vendor.set("vendorName", vendorName);
            vendor.set("vendorType", toText(context.get("vendorType")));
            vendor.set("contactName", toText(context.get("contactName")));
            vendor.set("contactMobile", toText(context.get("contactMobile")));
            vendor.set("addressText", toText(context.get("addressText")));
            vendor.set("customerCode", toText(context.get("customerCode")));
            String statusId = toText(context.get("statusId"));
            vendor.set("statusId", statusId == null ? STATUS_ACTIVE : statusId);
            vendor.set("note", toText(context.get("note")));
            delegator.createOrStore(vendor);

            Map<String, Object> result = ServiceUtil.returnSuccess();
            result.put("vendorId", vendorId);
            return result;
        } catch (GenericEntityException e) {
            return ServiceUtil.returnError("Could not save the vendor: " + e.getMessage());
        }
    }

    /**
     * Lists the outsourcing vendors.
     *
     * @param dctx the dispatch context
     * @param context service context, see {@code servicedef/services.xml#mfgVendorList}
     * @return the service result containing {@code vendors}
     */
    public static Map<String, Object> vendorList(DispatchContext dctx, Map<String, ? extends Object> context) {
        Delegator delegator = dctx.getDelegator();
        try {
            Map<String, Object> result = ServiceUtil.returnSuccess();
            result.put("vendors", vendorMaps(delegator));
            return result;
        } catch (GenericEntityException e) {
            return ServiceUtil.returnError("Could not read the vendors: " + e.getMessage());
        }
    }

    private static List<Map<String, Object>> vendorMaps(Delegator delegator) throws GenericEntityException {
        List<GenericValue> vendors = EntityQuery.use(delegator).from("MfgVendor")
                .orderBy("vendorName").queryList();
        List<Map<String, Object>> list = new ArrayList<>(vendors.size());
        for (GenericValue vendor : vendors) {
            list.add(toVendorMap(vendor));
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
        Delegator delegator = dctx.getDelegator();
        String workOrderId = toText(context.get("workOrderId"));
        String vendorId = toText(context.get("vendorId"));
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

    private static Map<String, Object> toVendorMap(GenericValue vendor) {
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

    private static String formatVendorId(LocalDate date, int sequence) {
        return "V-" + date.format(DAY) + "-" + String.format(Locale.ROOT, "%02d", sequence);
    }

    private static String nextVendorId(Delegator delegator, LocalDate today) throws GenericEntityException {
        String prefix = "V-" + today.format(DAY) + "-";
        List<GenericValue> existing = EntityQuery.use(delegator).from("MfgVendor").queryList();
        int max = 0;
        for (GenericValue vendor : existing) {
            String id = vendor.getString("vendorId");
            if (id != null && id.startsWith(prefix)) {
                max = Math.max(max, parseTrailingNumber(id.substring(prefix.length())));
            }
        }
        return formatVendorId(today, max + 1);
    }

    // --------------------------------------------------------------- warranty claims

    /**
     * Creates a warranty claim for one unit. The customer, the delivery date and the warranty end
     * date are derived from the delivery order and the product warranty months.
     *
     * @param dctx the dispatch context
     * @param context service context, see {@code servicedef/services.xml#mfgClaimCreate}
     * @return the service result containing {@code claimId} and {@code inWarranty}
     */
    public static Map<String, Object> claimCreate(DispatchContext dctx, Map<String, ? extends Object> context) {
        Delegator delegator = dctx.getDelegator();
        String sn = toText(context.get("sn"));
        String issueDesc = toText(context.get("issueDesc"));
        if (sn == null) {
            return ServiceUtil.returnError("sn is required");
        }
        if (issueDesc == null) {
            return ServiceUtil.returnError("issueDesc is required");
        }
        try {
            GenericValue unit = EntityQuery.use(delegator).from("MfgSn").where("sn", sn).queryOne();
            if (unit == null) {
                return ServiceUtil.returnError("Unknown serial number [" + sn + "]");
            }
            GenericValue delivery = null;
            GenericValue deliveryItem = EntityQuery.use(delegator).from("MfgDeliverySn")
                    .where("sn", sn).queryOne();
            if (deliveryItem != null) {
                delivery = EntityQuery.use(delegator).from("MfgDelivery")
                        .where("deliveryId", deliveryItem.getString("deliveryId")).queryOne();
            }
            GenericValue product = EntityQuery.use(delegator).from("MfgProduct")
                    .where("productCode", unit.getString("productCode")).queryOne();
            long warrantyMonths = product == null ? 12 : toLong(product.get("warrantyMonths"), 12);
            java.sql.Date deliveryDate = delivery == null ? null : delivery.getDate("deliveryDate");
            java.sql.Date endDate = warrantyEndDate(deliveryDate, warrantyMonths);
            java.sql.Date receivedDate = (java.sql.Date) parseDate(context.get("receivedDate"));
            if (receivedDate == null) {
                receivedDate = java.sql.Date.valueOf(LocalDate.now());
            }

            String claimId = nextClaimId(delegator, LocalDate.now());
            GenericValue claim = delegator.makeValue("MfgWarrantyClaim");
            claim.set("claimId", claimId);
            claim.set("sn", sn);
            claim.set("workOrderId", unit.getString("workOrderId"));
            claim.set("customerName", delivery == null ? toText(context.get("customerName"))
                    : delivery.getString("customerName"));
            claim.set("deliveryId", delivery == null ? null : delivery.getString("deliveryId"));
            claim.set("deliveryDate", deliveryDate);
            claim.set("warrantyEndDate", endDate);
            claim.set("inWarranty", isInWarranty(endDate, receivedDate) ? "Y" : "N");
            claim.set("receivedDate", receivedDate);
            claim.set("issueDesc", issueDesc);
            claim.set("statusId", RMA_OPEN);
            claim.set("responsibility", toText(context.get("responsibility")));
            claim.set("repairHours", Double.valueOf(toDouble(context.get("repairHours"), 0.0)));
            claim.set("note", toText(context.get("note")));
            delegator.create(claim);

            Map<String, Object> result = ServiceUtil.returnSuccess();
            result.put("claimId", claimId);
            result.put("inWarranty", claim.getString("inWarranty"));
            return result;
        } catch (GenericEntityException e) {
            return ServiceUtil.returnError("Could not create the claim: " + e.getMessage());
        }
    }

    /**
     * Updates the handling of a claim: status, responsibility, repair hours and note.
     *
     * @param dctx the dispatch context
     * @param context service context, see {@code servicedef/services.xml#mfgClaimUpdate}
     * @return the service result containing {@code claimId} and {@code statusId}
     */
    public static Map<String, Object> claimUpdate(DispatchContext dctx, Map<String, ? extends Object> context) {
        Delegator delegator = dctx.getDelegator();
        String claimId = toText(context.get("claimId"));
        if (claimId == null) {
            return ServiceUtil.returnError("claimId is required");
        }
        try {
            GenericValue claim = EntityQuery.use(delegator).from("MfgWarrantyClaim")
                    .where("claimId", claimId).queryOne();
            if (claim == null) {
                return ServiceUtil.returnError("Unknown claim [" + claimId + "]");
            }
            String statusId = toText(context.get("statusId"));
            if (statusId != null) {
                if (!RMA_OPEN.equals(statusId) && !RMA_REPAIRED.equals(statusId)
                        && !RMA_REPLACED.equals(statusId) && !RMA_CLOSED.equals(statusId)) {
                    return ServiceUtil.returnError("statusId must be " + RMA_OPEN + ", " + RMA_REPAIRED
                            + ", " + RMA_REPLACED + " or " + RMA_CLOSED);
                }
                claim.set("statusId", statusId);
                claim.set("closedDate", RMA_CLOSED.equals(statusId)
                        ? java.sql.Date.valueOf(LocalDate.now()) : null);
            }
            String responsibility = toText(context.get("responsibility"));
            if (responsibility != null) {
                claim.set("responsibility", responsibility);
            }
            if (context.get("repairHours") != null) {
                claim.set("repairHours", Double.valueOf(toDouble(context.get("repairHours"), 0.0)));
            }
            String note = toText(context.get("note"));
            if (note != null) {
                claim.set("note", note);
            }
            delegator.store(claim);

            Map<String, Object> result = ServiceUtil.returnSuccess();
            result.put("claimId", claimId);
            result.put("statusId", claim.getString("statusId"));
            return result;
        } catch (GenericEntityException e) {
            return ServiceUtil.returnError("Could not update the claim: " + e.getMessage());
        }
    }

    /**
     * Lists warranty claims, newest first.
     *
     * @param dctx the dispatch context
     * @param context service context, see {@code servicedef/services.xml#mfgClaimList}
     * @return the service result containing {@code claims}
     */
    public static Map<String, Object> claimList(DispatchContext dctx, Map<String, ? extends Object> context) {
        Delegator delegator = dctx.getDelegator();
        String sn = toText(context.get("sn"));
        String statusId = toText(context.get("statusId"));
        try {
            EntityQuery query = EntityQuery.use(delegator).from("MfgWarrantyClaim");
            if (sn != null) {
                query = query.where("sn", sn);
            }
            if (statusId != null) {
                query = query.where("statusId", statusId);
            }
            List<GenericValue> claims = query.orderBy("-createdStamp").queryList();
            List<Map<String, Object>> list = new ArrayList<>(claims.size());
            long openCount = 0;
            for (GenericValue claim : claims) {
                list.add(toClaimMap(claim));
                if (RMA_OPEN.equals(claim.getString("statusId"))) {
                    openCount++;
                }
            }
            Map<String, Object> result = ServiceUtil.returnSuccess();
            result.put("claims", list);
            result.put("claimCount", Integer.valueOf(list.size()));
            result.put("openCount", Long.valueOf(openCount));
            return result;
        } catch (GenericEntityException e) {
            return ServiceUtil.returnError("Could not read the claims: " + e.getMessage());
        }
    }

    /**
     * Returns one claim with the context needed to judge it: the unit, its delivery and the parts
     * that were replaced on it.
     *
     * @param dctx the dispatch context
     * @param context service context, see {@code servicedef/services.xml#mfgClaimDetail}
     * @return the service result containing {@code claim}, {@code snRecord}, {@code delivery} and {@code replacements}
     */
    public static Map<String, Object> claimDetail(DispatchContext dctx, Map<String, ? extends Object> context) {
        Delegator delegator = dctx.getDelegator();
        String claimId = toText(context.get("claimId"));
        Map<String, Object> empty = ServiceUtil.returnSuccess();
        empty.put("replacements", new ArrayList<Map<String, Object>>());
        if (claimId == null) {
            return empty;
        }
        try {
            GenericValue claim = EntityQuery.use(delegator).from("MfgWarrantyClaim")
                    .where("claimId", claimId).queryOne();
            if (claim == null) {
                return empty;
            }
            String sn = claim.getString("sn");
            GenericValue unit = EntityQuery.use(delegator).from("MfgSn").where("sn", sn).queryOne();
            GenericValue delivery = null;
            String deliveryId = claim.getString("deliveryId");
            if (deliveryId != null) {
                delivery = EntityQuery.use(delegator).from("MfgDelivery")
                        .where("deliveryId", deliveryId).queryOne();
            }
            List<GenericValue> replacements = EntityQuery.use(delegator).from("MfgReplacement")
                    .where("sn", sn).orderBy("createdStamp").queryList();
            List<GenericValue> defects = EntityQuery.use(delegator).from("MfgDefect")
                    .where("workOrderId", claim.getString("workOrderId")).queryList();

            Map<String, Object> result = ServiceUtil.returnSuccess();
            result.put("claim", toClaimMap(claim));
            result.put("snRecord", unit == null ? null : toSnMap(unit));
            result.put("delivery", delivery == null ? null : toDeliveryMap(delivery));
            result.put("replacements", toReplacementMaps(replacements));
            result.put("defectCount", Integer.valueOf(defects.size()));
            return result;
        } catch (GenericEntityException e) {
            return ServiceUtil.returnError("Could not read the claim: " + e.getMessage());
        }
    }

    /**
     * End of the warranty: delivery date plus the product warranty months (12 when unset).
     *
     * @param deliveryDate the date the unit was shipped
     * @param warrantyMonths warranty length in months
     * @return the end date, or {@code null} when the unit was never delivered
     */
    static java.sql.Date warrantyEndDate(java.sql.Date deliveryDate, long warrantyMonths) {
        if (deliveryDate == null) {
            return null;
        }
        long months = warrantyMonths <= 0 ? 12 : warrantyMonths;
        return java.sql.Date.valueOf(deliveryDate.toLocalDate().plusMonths(months));
    }

    /**
     * Tells whether a claim that arrived on {@code checkDate} is still inside the warranty.
     *
     * @param endDate the warranty end date
     * @param checkDate the date to test
     * @return {@code true} when the claim is covered
     */
    static boolean isInWarranty(java.sql.Date endDate, java.sql.Date checkDate) {
        if (endDate == null || checkDate == null) {
            return false;
        }
        return !checkDate.after(endDate);
    }

    private static List<Map<String, Object>> toClaimMaps(List<GenericValue> claims) {
        List<Map<String, Object>> list = new ArrayList<>(claims.size());
        for (GenericValue claim : claims) {
            list.add(toClaimMap(claim));
        }
        return list;
    }

    private static Map<String, Object> toClaimMap(GenericValue claim) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("claimId", claim.getString("claimId"));
        map.put("sn", claim.getString("sn"));
        map.put("workOrderId", claim.getString("workOrderId"));
        map.put("customerName", claim.getString("customerName"));
        map.put("deliveryId", claim.getString("deliveryId"));
        map.put("deliveryDate", claim.getDate("deliveryDate"));
        map.put("warrantyEndDate", claim.getDate("warrantyEndDate"));
        map.put("inWarranty", claim.getString("inWarranty"));
        map.put("receivedDate", claim.getDate("receivedDate"));
        map.put("issueDesc", claim.getString("issueDesc"));
        map.put("statusId", claim.getString("statusId"));
        map.put("responsibility", claim.getString("responsibility"));
        map.put("repairHours", claim.getDouble("repairHours"));
        map.put("closedDate", claim.getDate("closedDate"));
        map.put("note", claim.getString("note"));
        return map;
    }

    private static String formatClaimId(LocalDate date, int sequence) {
        return "RMA-" + date.format(DAY) + "-" + String.format(Locale.ROOT, "%02d", sequence);
    }

    private static String nextClaimId(Delegator delegator, LocalDate today) throws GenericEntityException {
        String prefix = "RMA-" + today.format(DAY) + "-";
        List<GenericValue> existing = EntityQuery.use(delegator).from("MfgWarrantyClaim").queryList();
        int max = 0;
        for (GenericValue claim : existing) {
            String id = claim.getString("claimId");
            if (id != null && id.startsWith(prefix)) {
                max = Math.max(max, parseTrailingNumber(id.substring(prefix.length())));
            }
        }
        return formatClaimId(today, max + 1);
    }

    // ------------------------------------------------------------------ helpers

    /**
     * Parses BOM CSV text into rows of {@code componentCode, componentName, qty, designators, isKeyPart}.
     * Empty lines and lines starting with {@code #} are ignored; quotes are stripped.
     *
     * @param csv the CSV text
     * @return the parsed rows, never {@code null}
     */
    static List<String[]> parseBomCsv(String csv) {
        List<String[]> rows = new ArrayList<>();
        if (csv == null) {
            return rows;
        }
        for (String line : csv.replace("\r\n", "\n").replace('\r', '\n').split("\n")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }
            String[] cells = splitCsvLine(trimmed);
            String code = unquote(cells[0]).toUpperCase(Locale.ROOT);
            if (code.isEmpty()) {
                continue;
            }
            String[] row = new String[5];
            row[0] = code;
            row[1] = cells.length > 1 ? unquote(cells[1]) : null;
            row[2] = cells.length > 2 ? unquote(cells[2]) : "1";
            row[3] = cells.length > 3 ? unquote(cells[3]) : null;
            row[4] = cells.length > 4 ? unquote(cells[4]) : "N";
            rows.add(row);
        }
        return rows;
    }

    /**
     * Splits one CSV line into cells, honouring double quoted cells which may contain commas.
     *
     * @param line the raw line
     * @return the cells, never {@code null}
     */
    static String[] splitCsvLine(String line) {
        List<String> cells = new ArrayList<>();
        StringBuilder cell = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (quoted) {
                if (c == '"') {
                    if (i + 1 < line.length() && line.charAt(i + 1) == '"') {
                        cell.append('"');
                        i++;
                    } else {
                        quoted = false;
                    }
                } else {
                    cell.append(c);
                }
            } else if (c == '"') {
                quoted = true;
            } else if (c == ',') {
                cells.add(cell.toString());
                cell.setLength(0);
            } else {
                cell.append(c);
            }
        }
        cells.add(cell.toString());
        return cells.toArray(new String[cells.size()]);
    }

    private static String unquote(String value) {
        if (value == null) {
            return "";
        }
        String text = value.trim();
        if (text.length() >= 2 && text.startsWith("\"") && text.endsWith("\"")) {
            text = text.substring(1, text.length() - 1).replace("\"\"", "\"").trim();
        }
        return text;
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
        return prefix + "-" + date.format(DAY) + "-" + String.format(Locale.ROOT, "%04d", sequence);
    }

    /**
     * Formats a work order identifier as {@code WO-yyMMdd-NNN}.
     *
     * @param date the creation day
     * @param sequence the running number, starting at 1
     * @return the formatted work order identifier
     */
    static String formatWorkOrderId(LocalDate date, int sequence) {
        return "WO-" + date.format(DAY) + "-" + String.format(Locale.ROOT, "%03d", sequence);
    }

    private static String nextWorkOrderId(Delegator delegator, LocalDate today) throws GenericEntityException {
        String prefix = "WO-" + today.format(DAY) + "-";
        List<GenericValue> existing = EntityQuery.use(delegator).from("MfgWorkOrder").queryList();
        int max = 0;
        for (GenericValue order : existing) {
            String id = order.getString("workOrderId");
            if (id != null && id.startsWith(prefix)) {
                max = Math.max(max, parseTrailingNumber(id.substring(prefix.length())));
            }
        }
        return formatWorkOrderId(today, max + 1);
    }

    private static int nextSnSequence(Delegator delegator, String prefix) throws GenericEntityException {
        String stem = prefix + "-" + LocalDate.now().format(DAY) + "-";
        List<GenericValue> existing = EntityQuery.use(delegator).from("MfgSn").queryList();
        int max = 0;
        for (GenericValue unit : existing) {
            String sn = unit.getString("sn");
            if (sn != null && sn.startsWith(stem)) {
                max = Math.max(max, parseTrailingNumber(sn.substring(stem.length())));
            }
        }
        return max + 1;
    }

    private static int parseTrailingNumber(String text) {
        try {
            return Integer.parseInt(text.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static double sumHours(Delegator delegator, String workOrderId) {
        try {
            return sumHours(EntityQuery.use(delegator).from("MfgReport")
                    .where("workOrderId", workOrderId).queryList());
        } catch (GenericEntityException e) {
            return 0.0;
        }
    }

    private static double sumHours(List<GenericValue> reports) {
        double total = 0.0;
        for (GenericValue report : reports) {
            Double hours = report.getDouble("workHours");
            if (hours != null) {
                total += hours.doubleValue();
            }
        }
        return total;
    }

    private static long sumQty(List<GenericValue> reports, String field) {
        long total = 0;
        for (GenericValue report : reports) {
            Long qty = report.getLong(field);
            if (qty != null) {
                total += qty.longValue();
            }
        }
        return total;
    }

    private static List<Map<String, Object>> toOperationMaps(List<GenericValue> routings, List<GenericValue> reports) {
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

    private static Map<String, Object> toWorkOrderMap(GenericValue order) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("smtVendorId", order.getString("smtVendorId"));
        map.put("workOrderId", order.getString("workOrderId"));
        map.put("productCode", order.getString("productCode"));
        map.put("plannedQty", order.getLong("plannedQty"));
        map.put("statusId", order.getString("statusId"));
        map.put("dueDate", order.get("dueDate"));
        map.put("customerName", order.getString("customerName"));
        map.put("smtVendor", order.getString("smtVendor"));
        map.put("note", order.getString("note"));
        map.put("createdStamp", order.get("createdStamp"));
        return map;
    }

    private static List<Map<String, Object>> toSnMaps(List<GenericValue> units) {
        List<Map<String, Object>> list = new ArrayList<>(units.size());
        for (GenericValue unit : units) {
            list.add(toSnMap(unit));
        }
        return list;
    }

    private static Map<String, Object> toSnMap(GenericValue unit) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("sn", unit.getString("sn"));
        map.put("workOrderId", unit.getString("workOrderId"));
        map.put("productCode", unit.getString("productCode"));
        map.put("statusId", unit.getString("statusId"));
        map.put("createdStamp", unit.get("createdStamp"));
        return map;
    }

    private static List<Map<String, Object>> toReportMaps(List<GenericValue> reports) {
        List<Map<String, Object>> list = new ArrayList<>(reports.size());
        for (GenericValue report : reports) {
            list.add(toReportMap(report));
        }
        return list;
    }

    private static Map<String, Object> toReportMap(GenericValue report) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("reportId", report.getString("reportId"));
        map.put("workOrderId", report.getString("workOrderId"));
        map.put("opSeq", report.getLong("opSeq"));
        map.put("sn", report.getString("sn"));
        map.put("operatorName", report.getString("operatorName"));
        map.put("workHours", report.getDouble("workHours"));
        map.put("qtyOk", report.getLong("qtyOk"));
        map.put("qtyNg", report.getLong("qtyNg"));
        map.put("reportDate", report.get("reportDate"));
        map.put("note", report.getString("note"));
        return map;
    }

    private static List<Map<String, Object>> toDefectMaps(List<GenericValue> defects) {
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

    private static Map<String, Object> newMap() {
        return new HashMap<>();
    }

    private static Object parseDate(Object value) {
        String text = toText(value);
        if (text == null) {
            return null;
        }
        try {
            return java.sql.Date.valueOf(LocalDate.parse(text));
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static long toLong(Object value, long defaultValue) {
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        if (value instanceof String) {
            try {
                String text = ((String) value).trim();
                return text.isEmpty() ? defaultValue : Math.round(Double.parseDouble(text));
            } catch (NumberFormatException e) {
                return defaultValue;
            }
        }
        return defaultValue;
    }

    private static double toDouble(Object value, double defaultValue) {
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        if (value instanceof String) {
            try {
                String text = ((String) value).trim();
                return text.isEmpty() ? defaultValue : Double.parseDouble(text);
            } catch (NumberFormatException e) {
                return defaultValue;
            }
        }
        return defaultValue;
    }

    private static String toText(Object value) {
        if (value == null) {
            return null;
        }
        String text = value.toString().trim();
        return text.isEmpty() ? null : text;
    }

    /**
     * Returns the products with their BOM and routing line counts, used by the product page.
     *
     * @param dctx the dispatch context
     * @param context service context, see {@code servicedef/services.xml#mfgProductList}
     * @return the service result containing {@code products}
     */
    public static Map<String, Object> productList(DispatchContext dctx, Map<String, ? extends Object> context) {
        Delegator delegator = dctx.getDelegator();
        try {
            List<GenericValue> products = EntityQuery.use(delegator).from("MfgProduct").orderBy("productCode").queryList();
            List<Map<String, Object>> list = new ArrayList<>(products.size());
            for (GenericValue product : products) {
                String productCode = product.getString("productCode");
                Map<String, Object> map = new LinkedHashMap<>();
                map.put("productCode", productCode);
                map.put("productName", product.getString("productName"));
                map.put("snPrefix", product.getString("snPrefix"));
                map.put("standardHours", product.getDouble("standardHours"));
                map.put("warrantyMonths", product.getLong("warrantyMonths"));
                map.put("statusId", product.getString("statusId"));
                map.put("bomCount", Integer.valueOf(count(delegator, "MfgBomItem", productCode)));
                map.put("routingCount", Integer.valueOf(count(delegator, "MfgRouting", productCode)));
                list.add(map);
            }
            Map<String, Object> result = ServiceUtil.returnSuccess();
            result.put("products", list);
            return result;
        } catch (GenericEntityException e) {
            return ServiceUtil.returnError("Could not read the product models: " + e.getMessage());
        }
    }

    private static int count(Delegator delegator, String entityName, String productCode) {
        try {
            return EntityQuery.use(delegator).from(entityName).where("productCode", productCode).queryList().size();
        } catch (GenericEntityException e) {
            return 0;
        }
    }

    /**
     * Returns the BOM and routing of one product model, used by the product page.
     *
     * @param dctx the dispatch context
     * @param context service context, see {@code servicedef/services.xml#mfgProductDetail}
     * @return the service result containing {@code product}, {@code bomItems} and {@code routings}
     */
    public static Map<String, Object> productDetail(DispatchContext dctx, Map<String, ? extends Object> context) {
        Delegator delegator = dctx.getDelegator();
        String productCode = toText(context.get("productCode"));
        try {
            if (productCode == null) {
                Map<String, Object> result = ServiceUtil.returnSuccess();
                result.put("bomItems", new ArrayList<Map<String, Object>>());
                result.put("routings", new ArrayList<Map<String, Object>>());
                return result;
            }
            GenericValue product = EntityQuery.use(delegator).from("MfgProduct")
                    .where("productCode", productCode).queryOne();
            List<GenericValue> bomItems = EntityQuery.use(delegator).from("MfgBomItem")
                    .where("productCode", productCode).orderBy("lineSeq").queryList();
            List<GenericValue> routings = EntityQuery.use(delegator).from("MfgRouting")
                    .where("productCode", productCode).orderBy("opSeq").queryList();

            List<Map<String, Object>> bomMaps = new ArrayList<>(bomItems.size());
            for (GenericValue item : bomItems) {
                Map<String, Object> map = new LinkedHashMap<>();
                map.put("lineSeq", item.getLong("lineSeq"));
                map.put("componentCode", item.getString("componentCode"));
                map.put("componentName", item.getString("componentName"));
                map.put("qty", item.getDouble("qty"));
                map.put("designators", item.getString("designators"));
                map.put("isKeyPart", item.getString("isKeyPart"));
                bomMaps.add(map);
            }
            List<Map<String, Object>> routingMaps = new ArrayList<>(routings.size());
            for (GenericValue operation : routings) {
                Map<String, Object> map = new LinkedHashMap<>();
                map.put("opSeq", operation.getLong("opSeq"));
                map.put("opName", operation.getString("opName"));
                map.put("workCenter", operation.getString("workCenter"));
                map.put("stdHours", operation.getDouble("stdHours"));
                routingMaps.add(map);
            }
            Map<String, Object> result = ServiceUtil.returnSuccess();
            if (product != null) {
                result.put("product", newMapWithProduct(product));
            }
            result.put("bomItems", bomMaps);
            result.put("routings", routingMaps);
            return result;
        } catch (GenericEntityException e) {
            return ServiceUtil.returnError("Could not read the product model: " + e.getMessage());
        }
    }

    private static Map<String, Object> newMapWithProduct(GenericValue product) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("productCode", product.getString("productCode"));
        map.put("productName", product.getString("productName"));
        map.put("snPrefix", product.getString("snPrefix"));
        map.put("standardHours", product.getDouble("standardHours"));
        map.put("warrantyMonths", product.getLong("warrantyMonths"));
        map.put("statusId", product.getString("statusId"));
        map.put("note", product.getString("note"));
        return map;
    }
}
