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
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
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

    /** Incoming inspection (IQC) of a material lot: only a failed lot is blocked from issuing. */
    public static final String IQC_PENDING = "MFG_IQC_PENDING";
    public static final String IQC_PASS = "MFG_IQC_PASS";
    public static final String IQC_CONCESSION = "MFG_IQC_CONCESSION";
    public static final String IQC_FAIL = "MFG_IQC_FAIL";

    /** All incoming inspection statuses, the order is the one shown in the lists. */
    public static final String[] INSPECT_STATUSES = {IQC_PENDING, IQC_PASS, IQC_CONCESSION, IQC_FAIL};

    public static final String SN_SHIPPED = "MFG_SHIPPED";

    public static final String RMA_OPEN = "MFG_RMA_OPEN";
    public static final String RMA_REPAIRED = "MFG_RMA_REPAIRED";
    public static final String RMA_REPLACED = "MFG_RMA_REPLACED";
    public static final String RMA_CLOSED = "MFG_RMA_CLOSED";

    public static final String CLAIM_RTN = "RTN";
    public static final String CLAIM_EXCH = "EXCH";
    public static final String CLAIM_REPAIR = "REPAIR";
    public static final String CLAIM_REFUND_ONLY = "REFUND_ONLY";

    public static final String FREIGHT_SELLER = "SELLER";
    public static final String FREIGHT_BUYER = "BUYER";

    public static final String INSPECT_QUALITY = "QUALITY";
    public static final String INSPECT_NO_FAULT = "NO_FAULT";
    public static final String INSPECT_HUMAN = "HUMAN";
    public static final String INSPECT_UNKNOWN = "UNKNOWN";

    public static final String RISK_WATCH = "WATCH";
    public static final String RISK_BLOCK = "BLOCK";

    public static final String FLAG_ACTIVE = "MFG_FLAG_ACTIVE";
    public static final String FLAG_RELEASED = "MFG_FLAG_RELEASED";

    public static final String CHANNEL_TAOBAO = "TAOBAO";

    /** Months a risk list entry stays valid before it expires on its own. */
    public static final int RISK_VALID_MONTHS = 12;

    /** Claims inside the observation window that already suggest a watch. */
    public static final int RISK_WATCH_CLAIMS = 3;
    /** Claims inside the observation window that suggest a manual review. */
    public static final int RISK_BLOCK_CLAIMS = 5;
    /** Refund total that already suggests a watch. */
    public static final double RISK_WATCH_REFUND = 500.0;
    /** Refund total that suggests a manual review. */
    public static final double RISK_BLOCK_REFUND = 1000.0;

    /** Labour cost per hour used to turn repair hours into money. */
    public static final double QUALITY_HOURLY_RATE = 60.0;
    /** Freight we absorb for one claim when we pay it. */
    public static final double QUALITY_FREIGHT_COST = 20.0;
    /** Red line: share of claims that come back within thirty days, in percent. */
    public static final double QUALITY_REPAIR_RATE_LIMIT = 2.0;
    /** Red line: after sales cost as a share of sales, in percent. */
    public static final double QUALITY_COST_RATE_LIMIT = 5.0;
    /** Red line: a lot carrying this many times the average claim density is suspicious. */
    public static final double QUALITY_LOT_FACTOR = 3.0;

    /** Hours of the no-reason return window, counted from midnight after the signed date. */
    public static final long SEVEN_DAY_HOURS = 168L;
    /** Rows per page on the list screens. */
    public static final long PAGE_SIZE = 20L;
    /** Days of the marketplace after sales window, counted from the signed date. */
    public static final long AFTER_SALE_DAYS = 15L;
    /** Hours left before a deadline is flagged as expiring soon. */
    public static final long DEADLINE_WARNING_HOURS = 24L;

    /** Alert types of the dashboard warning block. */
    public static final String ALERT_SHORTAGE = "MFG_ALERT_SHORTAGE";
    public static final String ALERT_CLAIM_7DAY = "MFG_ALERT_CLAIM_7DAY";
    public static final String ALERT_CLAIM_AFTERSALE = "MFG_ALERT_CLAIM_AFTERSALE";
    public static final String ALERT_SOFTWARE = "MFG_ALERT_SOFTWARE";
    public static final String ALERT_STOCK = "MFG_ALERT_STOCK";
    /** A warning that is worth a look. */
    public static final String ALERT_WARN = "WARN";
    /** Something is already late: a shortage or a passed deadline. */
    public static final String ALERT_ERROR = "ERROR";

    /** Movement types of the inventory ledger. */
    public static final String MOVE_RECEIPT = "MFG_MOVE_RECEIPT";
    public static final String MOVE_ISSUE = "MFG_MOVE_ISSUE";
    public static final String MOVE_RETURN = "MFG_MOVE_RETURN";
    public static final String MOVE_SCRAP = "MFG_MOVE_SCRAP";
    public static final String MOVE_COUNT = "MFG_MOVE_COUNT";
    public static final String MOVE_ADJUST = "MFG_MOVE_ADJUST";
    /** Reservation of stock for a work order; it changes the reserved quantity, not the quantity on hand. */
    public static final String MOVE_RESERVE = "MFG_MOVE_RESERVE";
    /** Release of a reservation, booked when the material is issued or the order is dropped. */
    public static final String MOVE_RELEASE = "MFG_MOVE_RELEASE";

    /** Movement types that put material into stock, and the ones that take it out. */
    public static final String[] MOVE_IN_TYPES = {MOVE_RECEIPT, MOVE_RETURN};
    public static final String[] MOVE_OUT_TYPES = {MOVE_ISSUE, MOVE_SCRAP};

    /** Every movement type, in the order the ledger filter lists them. */
    public static final String[] MOVE_TYPES = {MOVE_RECEIPT, MOVE_ISSUE, MOVE_RETURN, MOVE_SCRAP,
        MOVE_COUNT, MOVE_ADJUST, MOVE_RESERVE, MOVE_RELEASE};

    /** What a stock lot holds: a purchased component, or the finished goods of one work order. */
    public static final String LOT_COMPONENT = "MFG_LOT_COMPONENT";
    public static final String LOT_PRODUCT = "MFG_LOT_PRODUCT";
    /** Every lot kind, in the order the filters list them. */
    public static final String[] LOT_KINDS = {LOT_COMPONENT, LOT_PRODUCT};
    /** Prefix of the finished goods lot of a work order: {@code FG-WO-yyMMdd-NN}. */
    public static final String PRODUCT_LOT_PREFIX = "FG-";

    public static final String SW_PLANNED = "MFG_SW_PLANNED";
    public static final String SW_DOING = "MFG_SW_DOING";
    public static final String SW_DONE = "MFG_SW_DONE";
    public static final String SW_CANCELLED = "MFG_SW_CANCELLED";

    /** Hours per function point of the estimated function point method (industry baseline). */
    public static final double DEFAULT_PDR = 40.0;
    /** Price of one person day before tax; the 2019 case used 880, doubled for the current rate. */
    public static final double DEFAULT_DAY_RATE = 1760.0;
    /** Chinese VAT on a software service invoice. */
    public static final double DEFAULT_TAX_RATE = 0.06;
    /** Direct non labour cost as a share of the taxed amount, from the same case. */
    public static final double DEFAULT_DNC_RATE = 0.05;
    /** Working hours of one person month, used by the schedule formula. */
    public static final double HOURS_PER_MONTH = 176.0;
    /** Factor and exponent of the schedule formula, from the same standard. */
    public static final double SCHEDULE_FACTOR = 1.277;
    public static final double SCHEDULE_EXPONENT = 0.404;
    /** Hours of one person day, used to turn effort into person days. */
    public static final double HOURS_PER_DAY = 8.0;

    public static final String BOM_DRAFT = "MFG_BOM_DRAFT";
    public static final String BOM_RELEASED = "MFG_BOM_RELEASED";
    public static final String BOM_OBSOLETE = "MFG_BOM_OBSOLETE";

    public static final String DEV_ACTIVE = "MFG_DEV_ACTIVE";
    public static final String DEV_ONHOLD = "MFG_DEV_ONHOLD";
    public static final String DEV_DONE = "MFG_DEV_DONE";
    public static final String DEV_CANCELLED = "MFG_DEV_CANCELLED";

    public static final String REVIEW_PASS = "MFG_REVIEW_PASS";
    public static final String REVIEW_CONDITIONAL = "MFG_REVIEW_CONDITIONAL";
    public static final String REVIEW_FAIL = "MFG_REVIEW_FAIL";

    public static final String ECO_DRAFT = "MFG_ECO_DRAFT";
    public static final String ECO_APPROVED = "MFG_ECO_APPROVED";
    public static final String ECO_APPLIED = "MFG_ECO_APPLIED";
    public static final String ECO_CANCELLED = "MFG_ECO_CANCELLED";

    /** Stages a new model walks through, in order; the last one is mass production. */
    public static final String[] DEV_STAGES = {
        "MFG_STAGE_CONCEPT", "MFG_STAGE_DESIGN", "MFG_STAGE_PROTOTYPE", "MFG_STAGE_TRIAL", "MFG_STAGE_MASS"};

    /** Suffix between the model code and the version number of a BOM version identifier. */
    public static final String BOM_VERSION_SEPARATOR = "-V";

    public static final String TRACK_PCB = "MFG_TRACK_PCB";
    public static final String TRACK_FIRMWARE = "MFG_TRACK_FIRMWARE";
    public static final String TRACK_ENCLOSURE = "MFG_TRACK_ENCLOSURE";

    /** PCB track, from the breadboard proof to the hand soldered first board. */
    public static final String[] PCB_STEPS = {
        "MFG_PCB_BREADBOARD", "MFG_PCB_SCHEMATIC", "MFG_PCB_LAYOUT", "MFG_PCB_SAMPLE", "MFG_PCB_HAND_SOLDER"};

    /** Firmware track: write it, flash it, test the functions. */
    public static final String[] FIRMWARE_STEPS = {
        "MFG_FW_DEVELOP", "MFG_FW_FLASH", "MFG_FW_FUNCTION_TEST"};

    /** Enclosure track, from the 3D model to the assembled product. */
    public static final String[] ENCLOSURE_STEPS = {
        "MFG_ENC_BLENDER", "MFG_ENC_PRINT_CHECK", "MFG_ENC_CONTRACT", "MFG_ENC_PAY",
        "MFG_ENC_PRODUCTION", "MFG_ENC_STOCK_IN", "MFG_ENC_ASSEMBLY"};

    /** The step whose completion puts the enclosure into stock as a material lot. */
    public static final String STEP_ENCLOSURE_STOCK_IN = "MFG_ENC_STOCK_IN";

    public static final String STEP_TODO = "MFG_STEP_TODO";
    public static final String STEP_DOING = "MFG_STEP_DOING";
    public static final String STEP_DONE = "MFG_STEP_DONE";
    public static final String STEP_SKIPPED = "MFG_STEP_SKIPPED";

    public static final String ISSUE_OPEN = "MFG_ISSUE_OPEN";
    public static final String ISSUE_DOING = "MFG_ISSUE_DOING";
    public static final String ISSUE_CLOSED = "MFG_ISSUE_CLOSED";

    public static final String ISSUE_LOW = "MFG_ISSUE_LOW";
    public static final String ISSUE_MEDIUM = "MFG_ISSUE_MEDIUM";
    public static final String ISSUE_HIGH = "MFG_ISSUE_HIGH";

    /** Issue statuses, in the order the page lists them. */
    public static final String[] ISSUE_STATUSES = {ISSUE_OPEN, ISSUE_DOING, ISSUE_CLOSED};

    /** Issue severities, from a nit to something that blocks the launch. */
    public static final String[] ISSUE_SEVERITIES = {ISSUE_LOW, ISSUE_MEDIUM, ISSUE_HIGH};

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyMMdd");
    private static final DateTimeFormatter MONTH_DAY = DateTimeFormatter.ofPattern("MM-dd");

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
            product.set("laborRate", optionalAmount(context.get("laborRate")));
            long warrantyMonths = toLong(context.get("warrantyMonths"), 12);
            product.set("warrantyMonths", Long.valueOf(warrantyMonths <= 0 ? 12 : warrantyMonths));
            product.set("salePrice", Double.valueOf(toDouble(context.get("salePrice"), 0.0)));
            String statusId = toText(context.get("statusId"));
            product.set("statusId", statusId == null ? STATUS_ACTIVE : statusId);
            String devStageId = toText(context.get("devStageId"));
            if (devStageId != null) {
                product.set("devStageId", devStageId);
            }
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
     * Imports BOM lines from CSV text into one BOM version. Each line is
     * {@code componentCode,componentName,qty,designators,isKeyPart}. When {@code replace} is
     * {@code Y} the existing lines of that version are removed first.
     *
     * <p>The version is the one given in {@code bomVersionId}, otherwise the version the model
     * currently uses, otherwise a fresh version 1 that is created on the fly.
     *
     * @param dctx the dispatch context
     * @param context service context, see {@code servicedef/services.xml#mfgBomImport}
     * @return the service result containing {@code bomVersionId} and {@code importedCount}
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
            if (EntityQuery.use(delegator).from("MfgProduct").where("productCode", productCode).queryOne() == null) {
                return ServiceUtil.returnError("Unknown product model [" + productCode + "], create it first");
            }
            String bomVersionId = toText(context.get("bomVersionId"));
            if (bomVersionId != null) {
                if (EntityQuery.use(delegator).from("MfgBomVersion")
                        .where("bomVersionId", bomVersionId).queryOne() == null) {
                    return ServiceUtil.returnError("Unknown BOM version [" + bomVersionId + "]");
                }
            } else {
                bomVersionId = currentBomVersionId(delegator, productCode);
                if (bomVersionId == null) {
                    bomVersionId = createBomVersion(delegator, productCode, null, "Y", "Imported BOM");
                    // the first BOM of a model is the one in use, otherwise the import looks lost
                    GenericValue version = EntityQuery.use(delegator).from("MfgBomVersion")
                            .where("bomVersionId", bomVersionId).queryOne();
                    GenericValue product = EntityQuery.use(delegator).from("MfgProduct")
                            .where("productCode", productCode).queryOne();
                    if (version != null && product != null) {
                        version.set("statusId", BOM_RELEASED);
                        version.set("effectiveDate", java.sql.Date.valueOf(LocalDate.now()));
                        version.store();
                        product.set("currentBomVersionId", bomVersionId);
                        product.store();
                    }
                }
            }
            if (!"N".equals(toText(context.get("replace")))) {
                delegator.removeByAnd("MfgBomVersionItem", "bomVersionId", bomVersionId);
            }
            long lineSeq = nextBomLineSeq(delegator, bomVersionId);
            int imported = 0;
            for (String[] row : rows) {
                GenericValue item = delegator.makeValue("MfgBomVersionItem");
                item.set("bomVersionId", bomVersionId);
                item.set("lineSeq", Long.valueOf(lineSeq));
                item.set("componentCode", row[0]);
                item.set("componentName", row[1] == null ? row[0] : row[1]);
                item.set("qty", Double.valueOf(toDouble(row[2], 1.0)));
                item.set("designators", row[3]);
                item.set("isKeyPart", "Y".equalsIgnoreCase(row[4]) ? "Y" : "N");
                item.set("unitPrice", optionalAmount(row[5]));
                delegator.create(item);
                lineSeq += 10;
                imported++;
            }
            Map<String, Object> result = ServiceUtil.returnSuccess();
            result.put("productCode", productCode);
            result.put("bomVersionId", bomVersionId);
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
            order.set("bomVersionId", currentBomVersionId(delegator, productCode));
            order.set("note", toText(context.get("note")));
            delegator.create(order);

            // reserve what the warehouse can cover, the rest stays a shortage on the kitting page
            Map<String, Object> reserveContext = new HashMap<>();
            reserveContext.put("workOrderId", workOrderId);
            reserveContext.put("operatorName", toText(context.get("customerName")));
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
            putPage(result, "workOrders",
                    filterByKeyword(list, toText(context.get("search")), "workOrderId", "productCode",
                            "customerName", "smtVendor", "statusId"),
                    pageOf(context), pageSizeOf(context), toText(context.get("search")));
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

            // the finished goods lot of this order, once it was completed
            GenericValue productLot = EntityQuery.use(delegator).from("MfgMaterialLot")
                    .where("lotId", productLotId(workOrderId)).queryOne();
            if (productLot != null) {
                ensureLotLedger(delegator, productLot);
            }
            result.put("productLot", productLot == null ? null : toLotMap(productLot, null));
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

    // ------------------------------------------------------------ cost

    /**
     * Costs one model: BOM lines times their unit price plus the standard hours times the labour
     * rate, then compares the result with the target cost and the target price of the development
     * project of that model.
     *
     * @param dctx the dispatch context
     * @param context service context, see {@code servicedef/services.xml#mfgProductCost}
     * @return the service result containing the costs, the lines and the margin
     */
    public static Map<String, Object> productCost(DispatchContext dctx, Map<String, ? extends Object> context) {
        Delegator delegator = dctx.getDelegator();
        String productCode = toText(context.get("productCode"));
        Map<String, Object> empty = costSummary(0.0, 0.0, 1.0, null, null);
        empty.put("lines", new ArrayList<Map<String, Object>>());
        empty.put("productCode", productCode);
        if (productCode == null) {
            return empty;
        }
        try {
            GenericValue product = EntityQuery.use(delegator).from("MfgProduct")
                    .where("productCode", productCode).queryOne();
            if (product == null) {
                empty.put("message", "Unknown product model [" + productCode + "]");
                return empty;
            }
            double qty = toDouble(context.get("qty"), 1.0);
            if (qty <= 0.0) {
                qty = 1.0;
            }
            String versionId = toText(context.get("bomVersionId"));
            if (versionId == null) {
                versionId = currentBomVersionId(delegator, productCode);
            }
            List<GenericValue> bomItems = versionId == null ? new ArrayList<GenericValue>()
                    : bomItemsOfVersion(delegator, versionId);

            List<Map<String, Object>> lines = new ArrayList<>(bomItems.size());
            double materialPerUnit = 0.0;
            int missing = 0;
            for (GenericValue item : bomItems) {
                double lineQty = item.getDouble("qty") == null ? 0.0 : item.getDouble("qty").doubleValue();
                Object price = item.get("unitPrice");
                boolean hasPrice = price instanceof Number;
                if (!hasPrice) {
                    missing++;
                }
                double unitPrice = hasPrice ? ((Number) price).doubleValue() : 0.0;
                double amount = lineQty * unitPrice;
                materialPerUnit += amount;
                Map<String, Object> line = new LinkedHashMap<>();
                line.put("componentCode", item.getString("componentCode"));
                line.put("componentName", item.getString("componentName"));
                line.put("qty", Double.valueOf(lineQty));
                line.put("unitPrice", hasPrice ? Double.valueOf(unitPrice) : null);
                line.put("amount", Double.valueOf(amount));
                line.put("hasPrice", Boolean.valueOf(hasPrice));
                lines.add(line);
            }

            Object rateInput = context.get("laborRate");
            double laborRate = rateInput == null
                    ? (product.get("laborRate") instanceof Number
                            ? ((Number) product.get("laborRate")).doubleValue() : 0.0)
                    : toDouble(rateInput, 0.0);
            double productHours = product.getDouble("standardHours") == null
                    ? 0.0 : product.getDouble("standardHours").doubleValue();

            // labour hours: the routing of the model when it has one, else the hours on the product
            List<GenericValue> routing = routingOf(delegator, productCode);
            double routingHours = 0.0;
            List<Map<String, Object>> routingLines = new ArrayList<>(routing.size());
            for (GenericValue operation : routing) {
                Double operationHours = operation.getDouble("stdHours");
                double hours = operationHours == null ? 0.0 : operationHours.doubleValue();
                routingHours += hours;
                Map<String, Object> line = new LinkedHashMap<>();
                line.put("opSeq", operation.getLong("opSeq"));
                line.put("opName", operation.getString("opName"));
                line.put("workCenter", operation.getString("workCenter"));
                line.put("stdHours", Double.valueOf(hours));
                line.put("amount", Double.valueOf(round2(hours * laborRate)));
                routingLines.add(line);
            }
            String hoursSource = routing.isEmpty() ? "PRODUCT" : "ROUTING";
            double standardHours = routing.isEmpty() ? productHours : routingHours;
            double laborPerUnit = standardHours * laborRate;

            // one model may have several development projects: take the newest target
            GenericValue project = EntityQuery.use(delegator).from("MfgDevProject")
                    .where("productCode", productCode).orderBy("-createdStamp").queryFirst();
            Double targetCost = project == null || !(project.get("targetCost") instanceof Number)
                    ? null : Double.valueOf(((Number) project.get("targetCost")).doubleValue());
            Double targetPrice = project == null || !(project.get("targetPrice") instanceof Number)
                    ? null : Double.valueOf(((Number) project.get("targetPrice")).doubleValue());

            Map<String, Object> result = costSummary(materialPerUnit, laborPerUnit, qty, targetCost, targetPrice);
            result.put("productCode", productCode);
            result.put("bomVersionId", versionId);
            result.put("standardHours", Double.valueOf(standardHours));
            result.put("productStandardHours", Double.valueOf(productHours));
            result.put("routingHours", Double.valueOf(routingHours));
            result.put("hoursSource", hoursSource);
            result.put("routingLines", routingLines);
            result.put("laborRate", Double.valueOf(laborRate));
            result.put("lineCount", Integer.valueOf(lines.size()));
            result.put("missingPriceCount", Integer.valueOf(missing));
            result.put("lines", lines);
            result.put("salePrice", product.get("salePrice"));
            return result;
        } catch (GenericEntityException e) {
            return ServiceUtil.returnError("Could not cost the product model: " + e.getMessage());
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
        Delegator delegator = dctx.getDelegator();
        String workOrderId = toText(context.get("workOrderId"));
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
            List<GenericValue> routing = routingOf(delegator, productCode);
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
                versionId = currentBomVersionId(delegator, productCode);
            }
            List<GenericValue> bomItems = versionId == null ? new ArrayList<GenericValue>()
                    : bomItemsOfVersion(delegator, versionId);
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
                    issuedMaterial += qty * ((Number) lot.get("unitPrice")).doubleValue();
                }
            }

            List<GenericValue> reports = EntityQuery.use(delegator).from("MfgReport")
                    .where("workOrderId", workOrderId).queryList();
            double reportedHours = 0.0;
            double qtyOk = 0.0;
            for (GenericValue report : reports) {
                reportedHours += numberOrZero(report.get("workHours"));
                qtyOk += numberOrZero(report.get("qtyOk"));
            }
            double plannedQty = toLong(order.get("plannedQty"), 0);
            double actualLabor = reportedHours * laborRate;
            double outsourceAmount = numberOrZero(order.get("outsourceAmount"));
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
     * Totals the material and labour of one quantity and compares them with the target cost and the
     * target price.
     *
     * <p>Pure so the rounding and the division by zero can be tested without a database.
     *
     * @param materialPerUnit material cost of one unit
     * @param laborPerUnit labour cost of one unit
     * @param qty how many units to cost, at least zero
     * @param targetCost the target cost of one unit, may be {@code null}
     * @param targetPrice the target selling price of one unit, may be {@code null}
     * @return the totals, the unit cost and the margin (all rounded to two decimals)
     */
    static Map<String, Object> costSummary(double materialPerUnit, double laborPerUnit, double qty,
            Double targetCost, Double targetPrice) {
        double unit = materialPerUnit + laborPerUnit;
        Map<String, Object> map = ServiceUtil.returnSuccess();
        map.put("qty", Double.valueOf(qty));
        map.put("materialPerUnit", Double.valueOf(round2(materialPerUnit)));
        map.put("laborPerUnit", Double.valueOf(round2(laborPerUnit)));
        map.put("unitCost", Double.valueOf(round2(unit)));
        map.put("materialCost", Double.valueOf(round2(materialPerUnit * qty)));
        map.put("laborCost", Double.valueOf(round2(laborPerUnit * qty)));
        map.put("totalCost", Double.valueOf(round2(unit * qty)));
        map.put("targetCost", targetCost);
        map.put("targetPrice", targetPrice);
        map.put("targetDifference", targetCost == null
                ? null : Double.valueOf(round2(unit - targetCost.doubleValue())));
        if (targetPrice == null || targetPrice.doubleValue() <= 0.0) {
            map.put("unitMargin", null);
            map.put("marginRate", null);
        } else {
            double margin = targetPrice.doubleValue() - unit;
            map.put("unitMargin", Double.valueOf(round2(margin)));
            map.put("marginRate", Double.valueOf(round2(margin / targetPrice.doubleValue() * 100.0)));
        }
        return map;
    }

    /**
     * Builds the yield and labour hour trend from the work reports, one row per period. The same
     * numbers feed the page and the two CSV files.
     *
     * @param dctx the dispatch context
     * @param context service context, see {@code servicedef/services.xml#mfgTrendReport}
     * @return the service result with the periods, their totals and the worst period
     */
    public static Map<String, Object> trendReport(DispatchContext dctx, Map<String, ? extends Object> context) {
        Delegator delegator = dctx.getDelegator();
        java.sql.Date toDate = (java.sql.Date) parseDate(context.get("toDate"));
        if (toDate == null) {
            toDate = java.sql.Date.valueOf(LocalDate.now());
        }
        java.sql.Date fromDate = (java.sql.Date) parseDate(context.get("fromDate"));
        if (fromDate == null) {
            fromDate = java.sql.Date.valueOf(toDate.toLocalDate().minusDays(179));
        }
        String groupBy = toText(context.get("groupBy"));
        groupBy = groupBy == null ? "MONTH" : groupBy.toUpperCase(Locale.ROOT);
        if (!"DAY".equals(groupBy) && !"WEEK".equals(groupBy) && !"MONTH".equals(groupBy)) {
            groupBy = "MONTH";
        }
        Map<String, Object> result = ServiceUtil.returnSuccess();
        result.put("fromDate", fromDate);
        result.put("toDate", toDate);
        result.put("groupBy", groupBy);
        result.put("rows", new ArrayList<Map<String, Object>>());
        result.put("qtyOkTotal", Long.valueOf(0L));
        result.put("qtyNgTotal", Long.valueOf(0L));
        result.put("hoursTotal", Double.valueOf(0.0));
        result.put("yieldRate", Double.valueOf(0.0));
        result.put("hoursPerUnit", Double.valueOf(0.0));
        result.put("rowCount", Integer.valueOf(0));
        try {
            List<GenericValue> reports = EntityQuery.use(delegator).from("MfgReport")
                    .orderBy("reportDate").queryList();
            Map<String, double[]> buckets = new LinkedHashMap<>();
            Map<String, Set<String>> ordersOfBucket = new LinkedHashMap<>();
            double qtyOkTotal = 0.0;
            double qtyNgTotal = 0.0;
            double hoursTotal = 0.0;
            for (GenericValue report : reports) {
                Timestamp reportedAt = report.getTimestamp("reportDate");
                if (reportedAt == null) {
                    continue;
                }
                java.sql.Date reportedDate = new java.sql.Date(reportedAt.getTime());
                if (reportedDate.before(fromDate) || reportedDate.after(toDate)) {
                    continue;
                }
                String key = periodKey(reportedDate, groupBy);
                double[] bucket = buckets.get(key);
                if (bucket == null) {
                    bucket = new double[3];
                    buckets.put(key, bucket);
                    ordersOfBucket.put(key, new LinkedHashSet<String>());
                }
                bucket[0] += numberOrZero(report.get("qtyOk"));
                bucket[1] += numberOrZero(report.get("qtyNg"));
                bucket[2] += numberOrZero(report.get("workHours"));
                String workOrderId = report.getString("workOrderId");
                if (workOrderId != null) {
                    ordersOfBucket.get(key).add(workOrderId);
                }
                qtyOkTotal += numberOrZero(report.get("qtyOk"));
                qtyNgTotal += numberOrZero(report.get("qtyNg"));
                hoursTotal += numberOrZero(report.get("workHours"));
            }
            List<Map<String, Object>> rows = new ArrayList<>(buckets.size());
            Map<String, Object> worst = null;
            for (Map.Entry<String, double[]> entry : buckets.entrySet()) {
                double[] bucket = entry.getValue();
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("period", entry.getKey());
                row.put("qtyOk", Double.valueOf(round2(bucket[0])));
                row.put("qtyNg", Double.valueOf(round2(bucket[1])));
                row.put("yieldRate", Double.valueOf(yieldRate(bucket[0], bucket[1])));
                row.put("hours", Double.valueOf(round2(bucket[2])));
                row.put("hoursPerUnit", Double.valueOf(hoursPerUnit(bucket[2], bucket[0])));
                row.put("orderCount", Integer.valueOf(ordersOfBucket.get(entry.getKey()).size()));
                rows.add(row);
                if (bucket[0] + bucket[1] > 0.0
                        && (worst == null || ((Double) row.get("yieldRate")).doubleValue()
                                < ((Double) worst.get("yieldRate")).doubleValue())) {
                    worst = row;
                }
            }
            result.put("rows", rows);
            result.put("qtyOkTotal", Long.valueOf(Math.round(qtyOkTotal)));
            result.put("qtyNgTotal", Long.valueOf(Math.round(qtyNgTotal)));
            result.put("hoursTotal", Double.valueOf(round2(hoursTotal)));
            result.put("yieldRate", Double.valueOf(yieldRate(qtyOkTotal, qtyNgTotal)));
            result.put("hoursPerUnit", Double.valueOf(hoursPerUnit(hoursTotal, qtyOkTotal)));
            result.put("rowCount", Integer.valueOf(rows.size()));
            if (worst != null) {
                result.put("worstRow", worst);
            }
            return result;
        } catch (GenericEntityException e) {
            return ServiceUtil.returnError("Could not build the trend report: " + e.getMessage());
        }
    }

    /**
     * Reads the operations of a model in sequence order.
     *
     * @param delegator the delegator
     * @param productCode the model, may be {@code null}
     * @return the operations, empty when the model has no routing
     * @throws GenericEntityException when the routing cannot be read
     */
    private static List<GenericValue> routingOf(Delegator delegator, String productCode)
            throws GenericEntityException {
        if (productCode == null) {
            return new ArrayList<GenericValue>();
        }
        return EntityQuery.use(delegator).from("MfgRouting")
                .where("productCode", productCode).orderBy("opSeq").queryList();
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
                    + numberOrZero(report.get("workHours"))));
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

    private static Map<String, Object> operationCostRow(Long opSeq, String opName, String workCenter,
            double stdHours, double actualHours, double laborRate, double qtyOk, boolean inRouting) {
        double standardCost = round2(stdHours * laborRate * qtyOk);
        double actualCost = round2(actualHours * laborRate);
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("opSeq", opSeq);
        row.put("opName", opName);
        row.put("workCenter", workCenter);
        row.put("stdHours", Double.valueOf(stdHours));
        row.put("stdHoursTotal", Double.valueOf(round2(stdHours * qtyOk)));
        row.put("actualHours", Double.valueOf(round2(actualHours)));
        row.put("standardCost", Double.valueOf(standardCost));
        row.put("actualCost", Double.valueOf(actualCost));
        row.put("variance", Double.valueOf(round2(actualCost - standardCost)));
        row.put("inRouting", Boolean.valueOf(inRouting));
        return row;
    }

    /**
     * Turns a date into the label of the bucket it belongs to.
     *
     * @param date the date, may be {@code null}
     * @param groupBy {@code DAY}, {@code WEEK} or {@code MONTH}
     * @return the label, e.g. {@code 2026-10-21}, {@code 2026-W43} or {@code 2026-10}
     */
    static String periodKey(java.sql.Date date, String groupBy) {
        if (date == null) {
            return "-";
        }
        LocalDate day = date.toLocalDate();
        if ("DAY".equals(groupBy)) {
            return day.toString();
        }
        if ("WEEK".equals(groupBy)) {
            int year = day.get(java.time.temporal.IsoFields.WEEK_BASED_YEAR);
            int week = day.get(java.time.temporal.IsoFields.WEEK_OF_WEEK_BASED_YEAR);
            return String.format(Locale.ROOT, "%04d-W%02d",
                    Integer.valueOf(year), Integer.valueOf(week));
        }
        return String.format(Locale.ROOT, "%04d-%02d",
                Integer.valueOf(day.getYear()), Integer.valueOf(day.getMonthValue()));
    }

    /**
     * Yield as a percentage of everything that was reported.
     *
     * @param qtyOk good quantity
     * @param qtyNg rejected quantity
     * @return the yield in percent, {@code 0} when nothing was reported
     */
    static double yieldRate(double qtyOk, double qtyNg) {
        double total = qtyOk + qtyNg;
        return total <= 0.0 ? 0.0 : round2(qtyOk * 100.0 / total);
    }

    /**
     * Hours spent per good unit.
     *
     * @param hours reported hours
     * @param qtyOk good quantity
     * @return the hours per unit, {@code 0} when there is no good unit
     */
    static double hoursPerUnit(double hours, double qtyOk) {
        return qtyOk <= 0.0 ? 0.0 : round2(hours / qtyOk);
    }

    private static double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private static double numberOrZero(Object value) {
        return value instanceof Number ? ((Number) value).doubleValue() : 0.0;
    }

    /**
     * Reads an optional amount: an empty or unparsable value stays {@code null} instead of zero, so
     * the cost report can tell "no price yet" from "free".
     *
     * @param value the raw input
     * @return the amount, or {@code null} when there is none
     */
    private static Double optionalAmount(Object value) {
        String text = toText(value);
        if (text == null) {
            return null;
        }
        double amount = toDouble(text, Double.NaN);
        if (Double.isNaN(amount) || amount < 0.0) {
            return null;
        }
        return Double.valueOf(amount);
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
            boolean isNew = false;
            if (lotId == null) {
                lotId = nextLotId(delegator, LocalDate.now());
                isNew = true;
            } else if (EntityQuery.use(delegator).from("MfgMaterialLot")
                    .where("lotId", lotId).queryOne() == null) {
                isNew = true;
            }
            GenericValue lot = delegator.makeValue("MfgMaterialLot");
            lot.set("lotId", lotId);
            lot.set("componentCode", componentCode);
            lot.set("componentName", toText(context.get("componentName")));
            lot.set("supplierName", toText(context.get("supplierName")));
            lot.set("supplierLot", toText(context.get("supplierLot")));
            if (isNew) {
                lot.set("receivedQty", Double.valueOf(receivedQty));
            }
            Object receivedDate = parseDate(context.get("receivedDate"));
            lot.set("receivedDate", receivedDate == null
                    ? java.sql.Date.valueOf(LocalDate.now()) : receivedDate);
            lot.set("unitPrice", optionalAmount(context.get("unitPrice")));
            String inspectStatus = normalizeInspectStatus(context.get("inspectStatusId"));
            if (inspectStatus == null) {
                return ServiceUtil.returnError("Unknown inspection status [" + context.get("inspectStatusId")
                        + "], expected " + String.join(" / ", INSPECT_STATUSES));
            }
            lot.set("inspectStatusId", inspectStatus);
            lot.set("note", toText(context.get("note")));
            delegator.createOrStore(lot);
            if (isNew) {
                recordMovement(delegator, lot, MOVE_RECEIPT, receivedQty, lot.get("unitPrice"), null, null,
                        lot.getString("supplierLot"), null,
                        Timestamp.valueOf(lot.getDate("receivedDate").toLocalDate().atStartOfDay()),
                        "到货入库");
            }
            recalcLotStock(delegator, lot);

            Map<String, Object> result = ServiceUtil.returnSuccess();
            result.put("lotId", lotId);
            result.put("inspectStatusId", inspectStatus);
            result.put("onHandQty", lot.get("onHandQty"));
            result.put("availableQty", lot.get("availableQty"));
            return result;
        } catch (GenericEntityException e) {
            return ServiceUtil.returnError("Could not save the material lot: " + e.getMessage());
        }
    }

    /**
     * Records the incoming inspection of one lot.
     *
     * <p>A failed lot stays in the ledger (it has to be traced back to the supplier) but it cannot be
     * issued until somebody passes it or marks it as a concession. A lot that was never inspected is
     * still pending and can be issued, which keeps the flow usable for the parts that are not
     * inspected at all.
     *
     * @param dctx the dispatch context
     * @param context service context, see {@code servicedef/services.xml#mfgLotInspect}
     * @return the service result containing {@code lotId} and the stored status
     */
    public static Map<String, Object> lotInspect(DispatchContext dctx, Map<String, ? extends Object> context) {
        Delegator delegator = dctx.getDelegator();
        String lotId = toText(context.get("lotId"));
        if (lotId == null) {
            return ServiceUtil.returnError("lotId is required");
        }
        String status = normalizeInspectStatus(context.get("inspectStatusId"));
        if (status == null) {
            return ServiceUtil.returnError("Unknown inspection status [" + context.get("inspectStatusId")
                    + "], expected " + String.join(" / ", INSPECT_STATUSES));
        }
        double rejectedQty = toDouble(context.get("rejectedQty"), 0.0);
        if (rejectedQty < 0.0) {
            return ServiceUtil.returnError("rejectedQty cannot be negative");
        }
        try {
            GenericValue lot = EntityQuery.use(delegator).from("MfgMaterialLot").where("lotId", lotId).queryOne();
            if (lot == null) {
                return ServiceUtil.returnError("Unknown material lot [" + lotId + "]");
            }
            if (isProductLot(lot)) {
                return ServiceUtil.returnError("Lot [" + lotId + "] holds finished goods, ship them through a"
                        + " delivery order instead of issuing them to a work order");
            }
            double received = toDouble(lot.get("receivedQty"), 0.0);
            if (rejectedQty > received) {
                return ServiceUtil.returnError("rejectedQty [" + rejectedQty
                        + "] is larger than the received quantity [" + received + "]");
            }
            lot.set("inspectStatusId", status);
            lot.set("rejectedQty", Double.valueOf(rejectedQty));
            lot.set("inspector", toText(context.get("inspector")));
            lot.set("inspectNote", toText(context.get("inspectNote")));
            lot.set("inspectDate", IQC_PENDING.equals(status) ? null : java.sql.Date.valueOf(LocalDate.now()));
            delegator.store(lot);

            Map<String, Object> result = ServiceUtil.returnSuccess();
            result.put("lotId", lotId);
            result.put("inspectStatusId", status);
            result.put("rejectedQty", Double.valueOf(rejectedQty));
            return result;
        } catch (GenericEntityException e) {
            return ServiceUtil.returnError("Could not store the inspection: " + e.getMessage());
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
        String kind = toText(context.get("kind"));
        if (kind == null) {
            // the material page lists the purchased parts; the finished goods have their own page
            kind = LOT_COMPONENT;
        } else if ("ALL".equalsIgnoreCase(kind)) {
            // the count screen inspects every lot, purchased or finished
            kind = null;
        }
        try {
            EntityQuery query = EntityQuery.use(delegator).from("MfgMaterialLot");
            if (componentCode != null) {
                query = query.where("componentCode", componentCode);
            }
            List<GenericValue> lots = new ArrayList<>();
            for (GenericValue lot : query.orderBy("-createdStamp").queryList()) {
                if (matchesLotKind(lot, kind)) {
                    lots.add(lot);
                }
            }
            Map<String, Double> issued = issuedQtyByLot(delegator);
            List<Map<String, Object>> list = new ArrayList<>(lots.size());
            for (GenericValue lot : lots) {
                list.add(toLotMap(lot, issued.get(lot.getString("lotId"))));
            }
            Map<String, Object> result = ServiceUtil.returnSuccess();
            putPage(result, "lots",
                    filterByKeyword(list, toText(context.get("search")), "lotId", "componentCode",
                            "componentName", "supplierName", "supplierLot"),
                    pageOf(context), pageSizeOf(context), toText(context.get("search")));
            result.put("componentCode", componentCode);
            result.put("kind", kind);
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
            if (isProductLot(lot)) {
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
            ensureLotLedger(delegator, lot);
            // material reserved by this very work order may of course be issued to it
            double ownReservation = reservationOf(delegator, lotId, workOrderId);
            double available = numberOrZero(lot.get("availableQty")) + ownReservation;
            if (available + 0.0001 < qty) {
                return ServiceUtil.returnError("Only " + formatQty(available) + " left in lot [" + lotId
                        + "], cannot issue " + formatQty(qty));
            }
            String issueId = delegator.getNextSeqId("MfgMaterialIssue");
            GenericValue issue = delegator.makeValue("MfgMaterialIssue");
            issue.set("issueId", issueId);
            issue.set("workOrderId", workOrderId);
            issue.set("componentCode", componentCode);
            issue.set("lotId", lotId);
            issue.set("qty", Double.valueOf(qty));
            issue.set("directionId", MOVE_ISSUE);
            Object issuedDate = parseDate(context.get("issuedDate"));
            issue.set("issuedDate", issuedDate == null
                    ? java.sql.Date.valueOf(LocalDate.now()) : issuedDate);
            issue.set("designators", toText(context.get("designators")));
            issue.set("snFrom", toText(context.get("snFrom")));
            issue.set("snTo", toText(context.get("snTo")));
            issue.set("note", toText(context.get("note")));
            delegator.create(issue);
            recordMovement(delegator, lot, MOVE_ISSUE, -qty, lot.get("unitPrice"), workOrderId, null,
                    null, null,
                    Timestamp.valueOf(issue.getDate("issuedDate").toLocalDate().atStartOfDay()),
                    "发料给工单 " + workOrderId);
            releaseReservation(delegator, lot, workOrderId, qty, null);
            recalcLotStock(delegator, lot);

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
        return !IQC_FAIL.equals(inspectStatusId);
    }

    /**
     * Maps the incoming inspection status, defaulting an empty value to {@code PENDING}.
     *
     * @param value the raw input
     * @return one of {@link #INSPECT_STATUSES}, or {@code null} when the value is not a known status
     */
    static String normalizeInspectStatus(Object value) {
        String text = toText(value);
        if (text == null) {
            return IQC_PENDING;
        }
        String upper = text.toUpperCase(Locale.ROOT);
        for (String status : INSPECT_STATUSES) {
            if (status.equals(upper)) {
                return status;
            }
        }
        return null;
    }

    private static Map<String, Object> toLotMap(GenericValue lot, Double issuedQty) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("lotId", lot.getString("lotId"));
        map.put("lotKindId", lotKindOf(lot));
        map.put("workOrderId", lot.getString("workOrderId"));
        map.put("componentCode", lot.getString("componentCode"));
        map.put("componentName", lot.getString("componentName"));
        map.put("supplierName", lot.getString("supplierName"));
        map.put("supplierLot", lot.getString("supplierLot"));
        map.put("unitPrice", lot.get("unitPrice"));
        map.put("receivedQty", lot.getDouble("receivedQty"));
        map.put("onHandQty", lot.getDouble("onHandQty") == null
                ? Double.valueOf(0.0) : lot.getDouble("onHandQty"));
        map.put("reservedQty", lot.getDouble("reservedQty") == null
                ? Double.valueOf(0.0) : lot.getDouble("reservedQty"));
        map.put("availableQty", lot.getDouble("availableQty") == null
                ? Double.valueOf(0.0) : lot.getDouble("availableQty"));
        map.put("receivedDate", lot.getDate("receivedDate"));
        map.put("issuedQty", issuedQty == null ? Double.valueOf(0.0) : issuedQty);
        double received = lot.getDouble("receivedQty") == null ? 0.0 : lot.getDouble("receivedQty").doubleValue();
        double issued = issuedQty == null ? 0.0 : issuedQty.doubleValue();
        map.put("remainingQty", Double.valueOf(received - issued));
        String inspectStatus = lot.getString("inspectStatusId");
        map.put("inspectStatusId", inspectStatus == null ? IQC_PENDING : inspectStatus);
        map.put("inspectDate", lot.get("inspectDate"));
        map.put("inspector", lot.getString("inspector"));
        map.put("inspectNote", lot.getString("inspectNote"));
        map.put("rejectedQty", lot.getDouble("rejectedQty") == null
                ? Double.valueOf(0.0) : lot.getDouble("rejectedQty"));
        map.put("usable", Boolean.valueOf(isLotUsable(inspectStatus)));
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
        map.put("directionId", issue.getString("directionId"));
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
                recordMovement(delegator, lot, MOVE_RECEIPT, qty, null, null, null, row[3], null,
                        Timestamp.valueOf(lot.getDate("receivedDate").toLocalDate().atStartOfDay()),
                        "CSV 导入到货");
                recalcLotStock(delegator, lot);
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
            putPage(result, "orders",
                    filterByKeyword(rows, toText(context.get("search")), "workOrderId", "productCode",
                            "customerName"),
                    pageOf(context), pageSizeOf(context), toText(context.get("search")));
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
        long plannedQty = toLong(order.get("plannedQty"), 0);
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
        Map<String, Double> reservedMap = reservedByComponent(delegator, order.getString("workOrderId"));

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
            line.put("reservedQty", Double.valueOf(round2(reservedQty)));
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
            Map<String, Integer> toShip = new LinkedHashMap<>();
            for (String sn : sns) {
                GenericValue unit = EntityQuery.use(delegator).from("MfgSn").where("sn", sn).queryOne();
                if (unit == null) {
                    return ServiceUtil.returnError("Unknown serial number [" + sn + "]");
                }
                if (isDelivered(delegator, sn)) {
                    return ServiceUtil.returnError("Serial number [" + sn + "] has already been delivered");
                }
                if (!SN_COMPLETED.equals(unit.getString("statusId"))) {
                    return ServiceUtil.returnError("Serial number [" + sn + "] is not in the finished goods stock yet,"
                            + " complete work order [" + unit.getString("workOrderId") + "] first");
                }
                String unitOrder = unit.getString("workOrderId");
                GenericValue unitLot = EntityQuery.use(delegator).from("MfgMaterialLot")
                        .where("lotId", productLotId(unitOrder)).queryOne();
                if (unitLot == null) {
                    return ServiceUtil.returnError("Work order [" + unitOrder + "] has no finished goods lot yet,"
                            + " complete it first");
                }
                Integer pending = toShip.get(unitOrder);
                toShip.put(unitOrder, Integer.valueOf(pending == null ? 1 : pending.intValue() + 1));
            }
            for (Map.Entry<String, Integer> entry : toShip.entrySet()) {
                GenericValue unitLot = EntityQuery.use(delegator).from("MfgMaterialLot")
                        .where("lotId", productLotId(entry.getKey())).queryOne();
                double available = unitLot == null ? 0.0 : numberOrZero(unitLot.get("availableQty"));
                if (available + 0.0001 < entry.getValue().intValue()) {
                    return ServiceUtil.returnError("Only " + formatQty(available) + " unit(s) left in the finished"
                            + " goods lot [" + productLotId(entry.getKey()) + "], cannot ship "
                            + entry.getValue().intValue());
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

            // the goods leave the finished stock of the work order that built them
            String operator = toText(context.get("operatorName"));
            java.sql.Date shippedDay = delivery.getDate("deliveryDate");
            double issuedQty = 0.0;
            for (Map.Entry<String, Integer> entry : toShip.entrySet()) {
                GenericValue unitLot = EntityQuery.use(delegator).from("MfgMaterialLot")
                        .where("lotId", productLotId(entry.getKey())).queryOne();
                if (unitLot == null) {
                    continue;
                }
                double units = entry.getValue().intValue();
                recordMovement(delegator, unitLot, MOVE_ISSUE, -units, unitLot.get("unitPrice"),
                        entry.getKey(), null, deliveryId, operator,
                        shippedDay == null ? null
                                : Timestamp.valueOf(shippedDay.toLocalDate().atStartOfDay()),
                        "出货 " + deliveryId);
                recalcLotStock(delegator, unitLot);
                issuedQty += units;
            }

            Map<String, Object> result = ServiceUtil.returnSuccess();
            result.put("deliveryId", deliveryId);
            result.put("snCount", String.valueOf(sns.size()));
            result.put("issuedQty", Double.valueOf(round2(issuedQty)));
            result.put("productLotCount", Integer.valueOf(toShip.size()));
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
            putPage(result, "deliveries",
                    filterByKeyword(list, toText(context.get("search")), "deliveryId", "customerName",
                            "customerPo", "carrier", "trackingNo"),
                    pageOf(context), pageSizeOf(context), toText(context.get("search")));
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
            putPage(result, "vendors",
                    filterByKeyword(vendorMaps(delegator), toText(context.get("search")), "vendorName",
                            "vendorType", "contactName", "contactMobile", "customerCode", "vendorId"),
                    pageOf(context), pageSizeOf(context), toText(context.get("search")));
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
            java.sql.Date signedDate = (java.sql.Date) parseDate(context.get("signedDate"));
            if (signedDate == null) {
                signedDate = deliveryDate;
            }
            java.sql.Date receivedDate = (java.sql.Date) parseDate(context.get("receivedDate"));
            if (receivedDate == null) {
                receivedDate = java.sql.Date.valueOf(LocalDate.now());
            }
            String claimType = toText(context.get("claimType"));
            if (claimType == null) {
                claimType = CLAIM_RTN;
            } else if (!CLAIM_RTN.equals(claimType) && !CLAIM_EXCH.equals(claimType)
                    && !CLAIM_REPAIR.equals(claimType) && !CLAIM_REFUND_ONLY.equals(claimType)) {
                return ServiceUtil.returnError("claimType must be " + CLAIM_RTN + ", " + CLAIM_EXCH
                        + ", " + CLAIM_REPAIR + " or " + CLAIM_REFUND_ONLY);
            }
            String freightPayer = toText(context.get("freightPayer"));
            if (freightPayer != null && !FREIGHT_SELLER.equals(freightPayer)
                    && !FREIGHT_BUYER.equals(freightPayer)) {
                return ServiceUtil.returnError("freightPayer must be " + FREIGHT_SELLER
                        + " or " + FREIGHT_BUYER);
            }

            String claimId = nextClaimId(delegator, LocalDate.now());
            String buyerNick = toText(context.get("buyerNick"));
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
            claim.set("claimType", claimType);
            claim.set("tradeNo", toText(context.get("tradeNo")));
            claim.set("buyerNick", buyerNick);
            claim.set("signedDate", signedDate);
            claim.set("sevenDayDeadline", sevenDayDeadline(signedDate));
            claim.set("afterSaleDeadline", afterSaleDeadline(signedDate));
            claim.set("refundAmount", Double.valueOf(toDouble(context.get("refundAmount"), 0.0)));
            claim.set("deductAmount", Double.valueOf(toDouble(context.get("deductAmount"), 0.0)));
            claim.set("freightPayer", freightPayer == null ? defaultFreightPayer(claimType) : freightPayer);
            claim.set("responsibility", toText(context.get("responsibility")));
            claim.set("repairHours", Double.valueOf(toDouble(context.get("repairHours"), 0.0)));
            claim.set("vendorClaimStatus", "NONE");
            claim.set("buyerRiskLevel", activeRiskLevel(delegator, buyerNick));
            claim.set("note", toText(context.get("note")));
            delegator.create(claim);

            Map<String, Object> result = ServiceUtil.returnSuccess();
            result.put("claimId", claimId);
            result.put("inWarranty", claim.getString("inWarranty"));
            result.put("claimType", claimType);
            result.put("buyerRiskLevel", claim.getString("buyerRiskLevel"));
            result.put("sevenDayNoReason",
                    isWithinSevenDays(signedDate, receivedDate) ? "Y" : "N");
            java.sql.Timestamp sevenDay = sevenDayDeadline(signedDate);
            if (sevenDay != null) {
                result.put("sevenDayDeadline", sevenDay.toString());
            }
            java.sql.Timestamp afterSale = afterSaleDeadline(signedDate);
            if (afterSale != null) {
                result.put("afterSaleDeadline", afterSale.toString());
            }
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
            String claimType = toText(context.get("claimType"));
            if (claimType != null) {
                if (!CLAIM_RTN.equals(claimType) && !CLAIM_EXCH.equals(claimType)
                        && !CLAIM_REPAIR.equals(claimType) && !CLAIM_REFUND_ONLY.equals(claimType)) {
                    return ServiceUtil.returnError("claimType must be " + CLAIM_RTN + ", " + CLAIM_EXCH
                            + ", " + CLAIM_REPAIR + " or " + CLAIM_REFUND_ONLY);
                }
                claim.set("claimType", claimType);
            }
            String freightPayer = toText(context.get("freightPayer"));
            if (freightPayer != null) {
                if (!FREIGHT_SELLER.equals(freightPayer) && !FREIGHT_BUYER.equals(freightPayer)) {
                    return ServiceUtil.returnError("freightPayer must be " + FREIGHT_SELLER
                            + " or " + FREIGHT_BUYER);
                }
                claim.set("freightPayer", freightPayer);
            }
            String inspectResult = toText(context.get("inspectResult"));
            if (inspectResult != null) {
                if (!INSPECT_QUALITY.equals(inspectResult) && !INSPECT_NO_FAULT.equals(inspectResult)
                        && !INSPECT_HUMAN.equals(inspectResult) && !INSPECT_UNKNOWN.equals(inspectResult)) {
                    return ServiceUtil.returnError("inspectResult must be " + INSPECT_QUALITY + ", "
                            + INSPECT_NO_FAULT + ", " + INSPECT_HUMAN + " or " + INSPECT_UNKNOWN);
                }
                claim.set("inspectResult", inspectResult);
            }
            if (context.get("refundAmount") != null) {
                claim.set("refundAmount", Double.valueOf(toDouble(context.get("refundAmount"), 0.0)));
            }
            if (context.get("deductAmount") != null) {
                claim.set("deductAmount", Double.valueOf(toDouble(context.get("deductAmount"), 0.0)));
            }
            String returnTrackingNo = toText(context.get("returnTrackingNo"));
            if (returnTrackingNo != null) {
                claim.set("returnTrackingNo", returnTrackingNo);
            }
            java.sql.Date receivedBackDate = (java.sql.Date) parseDate(context.get("receivedBackDate"));
            if (receivedBackDate != null) {
                claim.set("receivedBackDate", receivedBackDate);
            }
            String vendorClaimStatus = toText(context.get("vendorClaimStatus"));
            if (vendorClaimStatus != null) {
                claim.set("vendorClaimStatus", vendorClaimStatus);
            }
            java.sql.Date signedDate = (java.sql.Date) parseDate(context.get("signedDate"));
            if (signedDate != null) {
                claim.set("signedDate", signedDate);
                claim.set("sevenDayDeadline", sevenDayDeadline(signedDate));
                claim.set("afterSaleDeadline", afterSaleDeadline(signedDate));
            }
            String note = toText(context.get("note"));
            if (note != null) {
                claim.set("note", note);
            }
            delegator.store(claim);

            Map<String, Object> result = ServiceUtil.returnSuccess();
            result.put("claimId", claimId);
            result.put("statusId", claim.getString("statusId"));
            result.put("claimType", claim.getString("claimType"));
            java.sql.Timestamp sevenDay = claim.getTimestamp("sevenDayDeadline");
            if (sevenDay != null) {
                result.put("sevenDayDeadline", sevenDay.toString());
            }
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
        String claimType = toText(context.get("claimType"));
        try {
            EntityQuery query = EntityQuery.use(delegator).from("MfgWarrantyClaim");
            if (sn != null) {
                query = query.where("sn", sn);
            }
            if (statusId != null) {
                query = query.where("statusId", statusId);
            }
            if (claimType != null) {
                query = query.where("claimType", claimType);
            }
            List<GenericValue> claims = query.orderBy("-createdStamp").queryList();
            List<Map<String, Object>> list = new ArrayList<>(claims.size());
            long openCount = 0;
            long expiringCount = 0;
            long sevenDayCount = 0;
            for (GenericValue claim : claims) {
                Map<String, Object> map = toClaimMap(claim);
                list.add(map);
                boolean open = RMA_OPEN.equals(claim.getString("statusId"));
                if (open) {
                    openCount++;
                    long sevenDay = hoursLeft(claim.getTimestamp("sevenDayDeadline"));
                    long afterSale = hoursLeft(claim.getTimestamp("afterSaleDeadline"));
                    if (sevenDay > 0) {
                        sevenDayCount++;
                    }
                    if (isExpiringSoon(sevenDay) || isExpiringSoon(afterSale)) {
                        expiringCount++;
                    }
                }
            }
            Map<String, Object> result = ServiceUtil.returnSuccess();
            putPage(result, "claims",
                    filterByKeyword(list, toText(context.get("search")), "claimId", "sn", "tradeNo",
                            "buyerNick", "customerName", "issueDesc", "responsibility"),
                    pageOf(context), pageSizeOf(context), toText(context.get("search")));
            result.put("claimCount", Integer.valueOf(list.size()));
            result.put("openCount", Long.valueOf(openCount));
            result.put("expiringCount", Long.valueOf(expiringCount));
            result.put("sevenDayCount", Long.valueOf(sevenDayCount));
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
            GenericValue exchangeDelivery = null;
            String exchangeDeliveryId = claim.getString("exchangeDeliveryId");
            List<Map<String, Object>> exchangeUnits = new ArrayList<>();
            if (exchangeDeliveryId != null) {
                exchangeDelivery = EntityQuery.use(delegator).from("MfgDelivery")
                        .where("deliveryId", exchangeDeliveryId).queryOne();
                List<GenericValue> items = EntityQuery.use(delegator).from("MfgDeliverySn")
                        .where("deliveryId", exchangeDeliveryId).orderBy("sn").queryList();
                for (GenericValue item : items) {
                    Map<String, Object> map = new LinkedHashMap<>();
                    map.put("sn", item.getString("sn"));
                    exchangeUnits.add(map);
                }
            }
            GenericValue repairOrder = null;
            String repairWorkOrderId = claim.getString("repairWorkOrderId");
            if (repairWorkOrderId != null) {
                repairOrder = EntityQuery.use(delegator).from("MfgWorkOrder")
                        .where("workOrderId", repairWorkOrderId).queryOne();
            }

            Map<String, Object> result = ServiceUtil.returnSuccess();
            result.put("claim", toClaimMap(claim));
            result.put("snRecord", unit == null ? null : toSnMap(unit));
            result.put("delivery", delivery == null ? null : toDeliveryMap(delivery));
            result.put("exchangeDelivery", exchangeDelivery == null ? null : toDeliveryMap(exchangeDelivery));
            result.put("exchangeUnits", exchangeUnits);
            result.put("repairOrder", repairOrder == null ? null : toWorkOrderMap(repairOrder));
            result.put("replacements", toReplacementMaps(replacements));
            result.put("defectCount", Integer.valueOf(defects.size()));
            return result;
        } catch (GenericEntityException e) {
            return ServiceUtil.returnError("Could not read the claim: " + e.getMessage());
        }
    }

    /**
     * Ships a replacement unit for a claim: a delivery order is created for a free unit of the same
     * model and the claim moves to {@code MFG_RMA_REPLACED}.
     *
     * @param dctx the dispatch context
     * @param context service context, see {@code servicedef/services.xml#mfgClaimExchange}
     * @return the service result containing {@code claimId}, {@code deliveryId} and {@code newSn}
     */
    public static Map<String, Object> claimExchange(DispatchContext dctx, Map<String, ? extends Object> context) {
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
            if (claim.getString("exchangeDeliveryId") != null) {
                return ServiceUtil.returnError("Claim [" + claimId + "] already has a replacement delivery ["
                        + claim.getString("exchangeDeliveryId") + "]");
            }
            String originalSn = claim.getString("sn");
            GenericValue returned = EntityQuery.use(delegator).from("MfgSn")
                    .where("sn", originalSn).queryOne();
            if (returned == null) {
                return ServiceUtil.returnError("Unknown serial number [" + originalSn + "]");
            }
            String productCode = returned.getString("productCode");
            String newSn = toText(context.get("newSn"));
            GenericValue replacement = null;
            if (newSn != null) {
                replacement = EntityQuery.use(delegator).from("MfgSn").where("sn", newSn).queryOne();
                if (replacement == null) {
                    return ServiceUtil.returnError("Unknown serial number [" + newSn + "]");
                }
                if (!productCode.equals(replacement.getString("productCode"))) {
                    return ServiceUtil.returnError("Serial number [" + newSn + "] does not belong to model ["
                            + productCode + "]");
                }
                if (SN_SCRAPPED.equals(replacement.getString("statusId"))) {
                    return ServiceUtil.returnError("Serial number [" + newSn + "] is scrapped");
                }
                if (isDelivered(delegator, newSn)) {
                    return ServiceUtil.returnError("Serial number [" + newSn + "] has already been delivered");
                }
            } else {
                List<GenericValue> candidates = EntityQuery.use(delegator).from("MfgSn")
                        .where("productCode", productCode).orderBy("sn").queryList();
                for (GenericValue candidate : candidates) {
                    String sn = candidate.getString("sn");
                    if (sn.equals(originalSn) || SN_SCRAPPED.equals(candidate.getString("statusId"))
                            || isDelivered(delegator, sn)) {
                        continue;
                    }
                    replacement = candidate;
                    newSn = sn;
                    break;
                }
                if (replacement == null) {
                    return ServiceUtil.returnError("No available unit of model [" + productCode
                            + "] to exchange");
                }
            }

            LocalDate today = LocalDate.now();
            String deliveryId = nextDeliveryId(delegator, today);
            GenericValue delivery = delegator.makeValue("MfgDelivery");
            delivery.set("deliveryId", deliveryId);
            delivery.set("customerName", claim.getString("customerName"));
            delivery.set("customerPo", claim.getString("tradeNo"));
            delivery.set("deliveryDate", java.sql.Date.valueOf(today));
            delivery.set("carrier", toText(context.get("carrier")));
            delivery.set("trackingNo", toText(context.get("trackingNo")));
            delivery.set("workOrderId", replacement.getString("workOrderId"));
            delivery.set("note", "换货 " + claimId + " 替换 " + originalSn);
            delegator.create(delivery);

            GenericValue item = delegator.makeValue("MfgDeliverySn");
            item.set("deliveryId", deliveryId);
            item.set("sn", newSn);
            delegator.create(item);
            replacement.set("statusId", SN_SHIPPED);
            delegator.store(replacement);

            claim.set("exchangeDeliveryId", deliveryId);
            claim.set("statusId", RMA_REPLACED);
            if (claim.getString("claimType") == null) {
                claim.set("claimType", CLAIM_EXCH);
            }
            delegator.store(claim);

            Map<String, Object> result = ServiceUtil.returnSuccess();
            result.put("claimId", claimId);
            result.put("deliveryId", deliveryId);
            result.put("newSn", newSn);
            result.put("statusId", RMA_REPLACED);
            return result;
        } catch (GenericEntityException e) {
            return ServiceUtil.returnError("Could not exchange the unit: " + e.getMessage());
        }
    }

    /**
     * Opens a repair work order for a claim so the rework is measured like any other build step.
     *
     * @param dctx the dispatch context
     * @param context service context, see {@code servicedef/services.xml#mfgClaimRepair}
     * @return the service result containing {@code claimId} and {@code workOrderId}
     */
    public static Map<String, Object> claimRepair(DispatchContext dctx, Map<String, ? extends Object> context) {
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
            if (claim.getString("repairWorkOrderId") != null) {
                return ServiceUtil.returnError("Claim [" + claimId + "] already has repair work order ["
                        + claim.getString("repairWorkOrderId") + "]");
            }
            String sn = claim.getString("sn");
            GenericValue unit = EntityQuery.use(delegator).from("MfgSn").where("sn", sn).queryOne();
            if (unit == null) {
                return ServiceUtil.returnError("Unknown serial number [" + sn + "]");
            }

            LocalDate today = LocalDate.now();
            java.sql.Date dueDate = (java.sql.Date) parseDate(context.get("dueDate"));
            String workOrderId = nextWorkOrderId(delegator, today);
            GenericValue order = delegator.makeValue("MfgWorkOrder");
            order.set("workOrderId", workOrderId);
            order.set("productCode", unit.getString("productCode"));
            order.set("plannedQty", Long.valueOf(1));
            order.set("statusId", WO_CREATED);
            order.set("dueDate", dueDate == null ? java.sql.Date.valueOf(today.plusDays(3)) : dueDate);
            String customerName = claim.getString("customerName");
            order.set("customerName", "售后返修" + (customerName == null ? "" : " " + customerName));
            order.set("note", claimId + " 返修 " + sn);
            delegator.create(order);

            claim.set("repairWorkOrderId", workOrderId);
            if (claim.getString("claimType") == null) {
                claim.set("claimType", CLAIM_REPAIR);
            }
            delegator.store(claim);

            Map<String, Object> result = ServiceUtil.returnSuccess();
            result.put("claimId", claimId);
            result.put("workOrderId", workOrderId);
            return result;
        } catch (GenericEntityException e) {
            return ServiceUtil.returnError("Could not open the repair work order: " + e.getMessage());
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

    /**
     * End of the marketplace seven day no-reason return window: the buyer signed on day D, the
     * window is counted from midnight after that day and lasts 168 hours, so it ends at D + 8 days.
     *
     * @param signedDate the date the buyer signed for the parcel
     * @return the deadline, or {@code null} when the unit was never signed for
     */
    static java.sql.Timestamp sevenDayDeadline(java.sql.Date signedDate) {
        if (signedDate == null) {
            return null;
        }
        LocalDateTime start = signedDate.toLocalDate().plusDays(1).atStartOfDay();
        return java.sql.Timestamp.valueOf(start.plusHours(SEVEN_DAY_HOURS));
    }

    /**
     * End of the marketplace after sales window, counted from the signed date.
     *
     * @param signedDate the date the buyer signed for the parcel
     * @return the deadline at the end of the last day, or {@code null} when never signed for
     */
    static java.sql.Timestamp afterSaleDeadline(java.sql.Date signedDate) {
        if (signedDate == null) {
            return null;
        }
        LocalDateTime end = signedDate.toLocalDate().plusDays(AFTER_SALE_DAYS)
                .atTime(23, 59, 59);
        return java.sql.Timestamp.valueOf(end);
    }

    /**
     * Tells whether a unit that came back on {@code receivedDate} still sits inside the seven day
     * no-reason window: the whole day {@code signedDate + 7} is still covered.
     *
     * @param signedDate the date the buyer signed for the parcel
     * @param receivedDate the date the unit came back
     * @return {@code true} when the return is inside the window
     */
    static boolean isWithinSevenDays(java.sql.Date signedDate, java.sql.Date receivedDate) {
        if (signedDate == null || receivedDate == null) {
            return false;
        }
        return !receivedDate.toLocalDate().isAfter(signedDate.toLocalDate().plusDays(7));
    }

    /**
     * Hours left until a deadline, negative when the deadline already passed.
     *
     * @param deadline the deadline
     * @return whole hours left, {@code 0} when there is no deadline
     */
    static long hoursLeft(java.sql.Timestamp deadline) {
        return hoursLeft(deadline, LocalDateTime.now());
    }

    /**
     * Hours left until a deadline relative to a given moment, negative when it already passed.
     *
     * @param deadline the deadline
     * @param now the moment to compare with
     * @return whole hours left, rounded down, {@code 0} when there is no deadline
     */
    static long hoursLeft(java.sql.Timestamp deadline, LocalDateTime now) {
        if (deadline == null || now == null) {
            return 0L;
        }
        long minutes = java.time.Duration.between(now, deadline.toLocalDateTime()).toMinutes();
        return minutes >= 0 ? minutes / 60 : -((-minutes + 59) / 60);
    }

    /**
     * Tells whether a deadline is close enough to be flagged in the list.
     *
     * @param hours the hours left, as returned by {@link #hoursLeft(java.sql.Timestamp)}
     * @return {@code true} when the deadline is inside the warning window but not yet passed
     */
    static boolean isExpiringSoon(long hours) {
        return hours >= 0 && hours <= DEADLINE_WARNING_HOURS;
    }

    /**
     * Freight payer that follows from the claim type when the operator did not choose one: a
     * quality return is on us, a no-reason return is on the buyer.
     *
     * @param claimType the claim type
     * @return either {@link #FREIGHT_SELLER} or {@link #FREIGHT_BUYER}
     */
    static String defaultFreightPayer(String claimType) {
        return CLAIM_RTN.equals(claimType) || CLAIM_EXCH.equals(claimType)
                ? FREIGHT_BUYER : FREIGHT_SELLER;
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
        map.put("claimType", claim.getString("claimType"));
        map.put("tradeNo", claim.getString("tradeNo"));
        map.put("buyerNick", claim.getString("buyerNick"));
        map.put("signedDate", claim.getDate("signedDate"));
        map.put("sevenDayDeadline", claim.getTimestamp("sevenDayDeadline"));
        map.put("afterSaleDeadline", claim.getTimestamp("afterSaleDeadline"));
        map.put("sevenDayHoursLeft", Long.valueOf(hoursLeft(claim.getTimestamp("sevenDayDeadline"))));
        map.put("afterSaleHoursLeft", Long.valueOf(hoursLeft(claim.getTimestamp("afterSaleDeadline"))));
        map.put("refundAmount", claim.getDouble("refundAmount"));
        map.put("deductAmount", claim.getDouble("deductAmount"));
        map.put("freightPayer", claim.getString("freightPayer"));
        map.put("returnTrackingNo", claim.getString("returnTrackingNo"));
        map.put("receivedBackDate", claim.getDate("receivedBackDate"));
        map.put("inspectResult", claim.getString("inspectResult"));
        map.put("vendorClaimStatus", claim.getString("vendorClaimStatus"));
        map.put("buyerRiskLevel", claim.getString("buyerRiskLevel"));
        map.put("repairWorkOrderId", claim.getString("repairWorkOrderId"));
        map.put("exchangeDeliveryId", claim.getString("exchangeDeliveryId"));
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

    // ------------------------------------------------------------- buyer risk list

    /**
     * Saves or updates a buyer on the internal risk list. The list only adds a warning before
     * shipping; a legal after sales request is never refused because of it.
     *
     * @param dctx the dispatch context
     * @param context service context, see {@code servicedef/services.xml#mfgBuyerFlagSave}
     * @return the service result containing {@code buyerKey}, {@code riskLevel} and {@code statusId}
     */
    public static Map<String, Object> buyerFlagSave(DispatchContext dctx, Map<String, ? extends Object> context) {
        Delegator delegator = dctx.getDelegator();
        String channelId = toText(context.get("channelId"));
        if (channelId == null) {
            channelId = CHANNEL_TAOBAO;
        }
        String rawMobile = toText(context.get("receiverMobile"));
        String mobile = normalizeMobile(rawMobile);
        if (rawMobile != null && mobile == null) {
            return ServiceUtil.returnError("receiverMobile must be a full eleven digit number; a masked "
                    + "number such as 138****2222 cannot identify a buyer");
        }
        String buyerNick = toText(context.get("buyerNick"));
        String buyerKey = buyerKey(channelId, mobile, buyerNick);
        if (buyerKey == null) {
            return ServiceUtil.returnError("receiverMobile or buyerNick is required");
        }
        String riskLevel = toText(context.get("riskLevel"));
        if (riskLevel == null) {
            riskLevel = RISK_WATCH;
        } else if (!RISK_WATCH.equals(riskLevel) && !RISK_BLOCK.equals(riskLevel)) {
            return ServiceUtil.returnError("riskLevel must be " + RISK_WATCH + " or " + RISK_BLOCK);
        }
        String statusId = toText(context.get("statusId"));
        if (statusId == null) {
            statusId = FLAG_ACTIVE;
        } else if (!FLAG_ACTIVE.equals(statusId) && !FLAG_RELEASED.equals(statusId)) {
            return ServiceUtil.returnError("statusId must be " + FLAG_ACTIVE + " or " + FLAG_RELEASED);
        }
        String reasonCode = toText(context.get("reasonCode"));
        if (reasonCode != null && !isKnownReason(reasonCode)) {
            return ServiceUtil.returnError("reasonCode must be one of FREQ_RETURN, EMPTY_BOX, SWAP, "
                    + "ABUSE_7DAY, FAKE_CLAIM or THREAT");
        }
        try {
            GenericValue flag = EntityQuery.use(delegator).from("MfgBuyerFlag")
                    .where("buyerKey", buyerKey).queryOne();
            if (flag == null && mobile != null) {
                // an older entry may have been keyed on the mobile: reuse it instead of duplicating
                List<GenericValue> candidates = EntityQuery.use(delegator).from("MfgBuyerFlag").queryList();
                for (GenericValue candidate : candidates) {
                    if (mobile.equals(candidate.getString("receiverMobile"))) {
                        flag = candidate;
                        break;
                    }
                }
            }
            boolean create = flag == null;
            if (create) {
                flag = delegator.makeValue("MfgBuyerFlag");
                flag.set("buyerKey", buyerKey);
                flag.set("channelId", channelId);
            }
            if (buyerNick != null) {
                flag.set("buyerNick", buyerNick);
            }
            if (mobile != null) {
                flag.set("receiverMobile", mobile);
            }
            String receiverName = toText(context.get("receiverName"));
            if (receiverName != null) {
                flag.set("receiverName", receiverName);
            }
            String addressText = toText(context.get("addressText"));
            if (addressText != null) {
                flag.set("addressText", addressText);
            }
            if (reasonCode != null) {
                flag.set("reasonCode", reasonCode);
            }
            String evidenceNote = toText(context.get("evidenceNote"));
            if (evidenceNote != null) {
                flag.set("evidenceNote", evidenceNote);
            }
            String note = toText(context.get("note"));
            if (note != null) {
                flag.set("note", note);
            }
            flag.set("riskLevel", riskLevel);
            flag.set("statusId", statusId);
            flag.set("releasedDate", FLAG_RELEASED.equals(statusId)
                    ? java.sql.Date.valueOf(LocalDate.now()) : null);
            java.sql.Date expiresDate = (java.sql.Date) parseDate(context.get("expiresDate"));
            if (expiresDate == null && create) {
                expiresDate = java.sql.Date.valueOf(LocalDate.now().plusMonths(RISK_VALID_MONTHS));
            }
            if (expiresDate != null) {
                flag.set("expiresDate", expiresDate);
            }
            if (create) {
                delegator.create(flag);
            } else {
                delegator.store(flag);
            }

            Map<String, Object> result = ServiceUtil.returnSuccess();
            result.put("buyerKey", buyerKey);
            result.put("riskLevel", riskLevel);
            result.put("statusId", statusId);
            return result;
        } catch (GenericEntityException e) {
            return ServiceUtil.returnError("Could not save the buyer flag: " + e.getMessage());
        }
    }

    /**
     * Lists the risk list, newest first, with the mobile number masked.
     *
     * @param dctx the dispatch context
     * @param context service context, see {@code servicedef/services.xml#mfgBuyerFlagList}
     * @return the service result containing {@code flags}, {@code flagCount} and {@code blockCount}
     */
    public static Map<String, Object> buyerFlagList(DispatchContext dctx, Map<String, ? extends Object> context) {
        Delegator delegator = dctx.getDelegator();
        String keyword = toText(context.get("keyword"));
        String riskLevel = toText(context.get("riskLevel"));
        String statusId = toText(context.get("statusId"));
        try {
            List<GenericValue> flags = EntityQuery.use(delegator).from("MfgBuyerFlag")
                    .orderBy("-createdStamp").queryList();
            List<Map<String, Object>> list = new ArrayList<>(flags.size());
            long blockCount = 0;
            for (GenericValue flag : flags) {
                if (riskLevel != null && !riskLevel.equals(flag.getString("riskLevel"))) {
                    continue;
                }
                if (statusId != null && !statusId.equals(flag.getString("statusId"))) {
                    continue;
                }
                Map<String, Object> map = toBuyerFlagMap(flag);
                if (keyword != null) {
                    String haystack = String.valueOf(map.get("buyerKey")) + " "
                            + String.valueOf(map.get("buyerNick")) + " "
                            + String.valueOf(map.get("receiverMobile"));
                    if (!haystack.toLowerCase(Locale.ROOT).contains(keyword.toLowerCase(Locale.ROOT))) {
                        continue;
                    }
                }
                list.add(map);
                if (RISK_BLOCK.equals(flag.getString("riskLevel"))
                        && isFlagLive(flag.getString("statusId"), flag.getDate("expiresDate"),
                                java.sql.Date.valueOf(LocalDate.now()))) {
                    blockCount++;
                }
            }
            Map<String, Object> result = ServiceUtil.returnSuccess();
            putPage(result, "flags", list, pageOf(context), pageSizeOf(context), keyword);
            result.put("flagCount", Integer.valueOf(list.size()));
            result.put("blockCount", Long.valueOf(blockCount));
            result.put("keyword", keyword);
            return result;
        } catch (GenericEntityException e) {
            return ServiceUtil.returnError("Could not read the buyer flags: " + e.getMessage());
        }
    }

    /**
     * Looks a buyer up on the risk list by mobile number or nickname.
     *
     * @param dctx the dispatch context
     * @param context service context, see {@code servicedef/services.xml#mfgBuyerFlagCheck}
     * @return the service result containing {@code hit}, {@code riskLevel} and {@code flag}
     */
    public static Map<String, Object> buyerFlagCheck(DispatchContext dctx, Map<String, ? extends Object> context) {
        Delegator delegator = dctx.getDelegator();
        String mobile = normalizeMobile(toText(context.get("receiverMobile")));
        String buyerNick = toText(context.get("buyerNick"));
        Map<String, Object> result = ServiceUtil.returnSuccess();
        result.put("hit", "N");
        result.put("flags", new ArrayList<Map<String, Object>>());
        if (mobile == null && buyerNick == null) {
            return result;
        }
        try {
            List<Map<String, Object>> hits = new ArrayList<>();
            String bestLevel = null;
            List<GenericValue> flags = EntityQuery.use(delegator).from("MfgBuyerFlag").queryList();
            java.sql.Date today = java.sql.Date.valueOf(LocalDate.now());
            for (GenericValue flag : flags) {
                if (!isFlagLive(flag.getString("statusId"), flag.getDate("expiresDate"), today)) {
                    continue;
                }
                boolean byMobile = mobile != null && mobile.equals(flag.getString("receiverMobile"));
                boolean byNick = buyerNick != null && buyerNick.equals(flag.getString("buyerNick"));
                if (!byMobile && !byNick) {
                    continue;
                }
                hits.add(toBuyerFlagMap(flag));
                String level = flag.getString("riskLevel");
                if (RISK_BLOCK.equals(level)) {
                    bestLevel = RISK_BLOCK;
                } else if (bestLevel == null) {
                    bestLevel = level;
                }
            }
            result.put("hit", hits.isEmpty() ? "N" : "Y");
            result.put("flags", hits);
            if (bestLevel != null) {
                result.put("riskLevel", bestLevel);
            }
            result.put("buyerNick", buyerNick);
            result.put("receiverMobile", mobile);
            return result;
        } catch (GenericEntityException e) {
            return ServiceUtil.returnError("Could not check the buyer: " + e.getMessage());
        }
    }

    /**
     * Aggregates the after sales history per buyer so the operator can spot a pattern before it
     * becomes a problem. Nothing is flagged automatically.
     *
     * @param dctx the dispatch context
     * @param context service context, see {@code servicedef/services.xml#mfgBuyerRiskList}
     * @return the service result containing {@code buyers} and {@code buyerCount}
     */
    public static Map<String, Object> buyerRiskList(DispatchContext dctx, Map<String, ? extends Object> context) {
        Delegator delegator = dctx.getDelegator();
        try {
            List<GenericValue> claims = EntityQuery.use(delegator).from("MfgWarrantyClaim")
                    .orderBy("createdStamp").queryList();
            Map<String, Map<String, Object>> byNick = new LinkedHashMap<>();
            for (GenericValue claim : claims) {
                String nick = claim.getString("buyerNick");
                if (nick == null) {
                    continue;
                }
                Map<String, Object> row = byNick.get(nick);
                if (row == null) {
                    row = new LinkedHashMap<>();
                    row.put("buyerNick", nick);
                    row.put("claimCount", Integer.valueOf(0));
                    row.put("refundTotal", Double.valueOf(0.0));
                    row.put("blockCount", Integer.valueOf(0));
                    byNick.put(nick, row);
                }
                row.put("claimCount", Integer.valueOf(((Integer) row.get("claimCount")).intValue() + 1));
                double refund = claim.getDouble("refundAmount") == null ? 0.0
                        : claim.getDouble("refundAmount").doubleValue();
                row.put("refundTotal", Double.valueOf(((Double) row.get("refundTotal")).doubleValue() + refund));
                if (RISK_BLOCK.equals(claim.getString("buyerRiskLevel"))) {
                    row.put("blockCount", Integer.valueOf(((Integer) row.get("blockCount")).intValue() + 1));
                }
                row.put("lastClaimDate", claim.getDate("receivedDate"));
            }
            List<Map<String, Object>> buyers = new ArrayList<>(byNick.values());
            for (Map<String, Object> row : buyers) {
                int count = ((Integer) row.get("claimCount")).intValue();
                double refund = ((Double) row.get("refundTotal")).doubleValue();
                row.put("suggestedRiskLevel", suggestRiskLevel(count, refund));
                row.put("flagged", hasActiveFlag(delegator, (String) row.get("buyerNick")) ? "Y" : "N");
            }
            buyers.sort(new Comparator<Map<String, Object>>() {
                @Override
                public int compare(Map<String, Object> left, Map<String, Object> right) {
                    return ((Integer) right.get("claimCount")).compareTo((Integer) left.get("claimCount"));
                }
            });
            Map<String, Object> result = ServiceUtil.returnSuccess();
            result.put("buyers", buyers);
            result.put("buyerCount", Integer.valueOf(buyers.size()));
            return result;
        } catch (GenericEntityException e) {
            return ServiceUtil.returnError("Could not read the buyer history: " + e.getMessage());
        }
    }

    /**
     * Normalizes a mobile number to its digits, so formatting differences do not break matching.
     * Masked numbers such as {@code 138****2222} are rejected: they cannot identify a buyer and
     * would make different buyers collide on the same key.
     *
     * @param mobile the raw number
     * @return the eleven digits, or {@code null} when the number is masked or incomplete
     */
    static String normalizeMobile(String mobile) {
        if (mobile == null) {
            return null;
        }
        if (mobile.indexOf('*') >= 0 || mobile.indexOf('x') >= 0 || mobile.indexOf('X') >= 0
                || mobile.indexOf('＊') >= 0) {
            return null;
        }
        StringBuilder digits = new StringBuilder();
        for (int i = 0; i < mobile.length(); i++) {
            char c = mobile.charAt(i);
            if (c >= '0' && c <= '9') {
                digits.append(c);
            }
        }
        return digits.length() == 11 ? digits.toString() : null;
    }

    /**
     * Builds the matching key of a buyer. The marketplace nickname comes first because it is
always
     * available on an order, while the mobile number is often masked or replaced by a privacy
     * number; the mobile is only used when no nickname was given.
     *
     * @param channelId the marketplace, {@code TAOBAO} today
     * @param mobile the receiver mobile, may be {@code null} or masked
     * @param buyerNick the marketplace nickname, may be {@code null}
     * @return the key, or {@code null} when neither is usable
     */
    static String buyerKey(String channelId, String mobile, String buyerNick) {
        String prefix = CHANNEL_TAOBAO.equals(channelId) || channelId == null ? "TB" : channelId;
        if (buyerNick != null && !buyerNick.trim().isEmpty()) {
            return prefix + ":N:" + buyerNick.trim().toLowerCase(Locale.ROOT);
        }
        String digits = normalizeMobile(mobile);
        return digits == null ? null : prefix + ":" + digits;
    }

    /**
     * Masks a mobile number so it can be shown on screen without exposing it.
     *
     * @param mobile the number
     * @return {@code 138****2222}, or the input when it is too short
     */
    static String maskMobile(String mobile) {
        String digits = normalizeMobile(mobile);
        if (digits == null) {
            return mobile == null ? "" : mobile;
        }
        if (digits.length() < 11) {
            return digits.substring(0, 1) + "****" + digits.substring(digits.length() - 1);
        }
        return digits.substring(0, 3) + "****" + digits.substring(digits.length() - 4);
    }

    /**
     * Tells whether a risk list entry still applies.
     *
     * @param statusId the entry status
     * @return {@code true} only when the entry is active
     */
    static boolean isFlagActive(String statusId) {
        return FLAG_ACTIVE.equals(statusId);
    }

    /**
     * Tells whether a risk list entry applies today: active and not expired.
     *
     * @param statusId the entry status
     * @param expiresDate the date the entry stops applying, may be {@code null} for no limit
     * @param today today
     * @return {@code true} only when the entry is active and not yet expired
     */
    static boolean isFlagLive(String statusId, java.sql.Date expiresDate, java.sql.Date today) {
        if (!isFlagActive(statusId)) {
            return false;
        }
        if (expiresDate == null || today == null) {
            return true;
        }
        return !today.after(expiresDate);
    }

    /**
     * Suggests a risk level from the after sales history; the operator still decides.
     *
     * @param claimCount number of claims of the buyer
     * @param refundTotal amount refunded to the buyer
     * @return {@link #RISK_BLOCK}, {@link #RISK_WATCH} or {@code null} when nothing stands out
     */
    static String suggestRiskLevel(int claimCount, double refundTotal) {
        if (claimCount >= RISK_BLOCK_CLAIMS || refundTotal >= RISK_BLOCK_REFUND) {
            return RISK_BLOCK;
        }
        if (claimCount >= RISK_WATCH_CLAIMS || refundTotal >= RISK_WATCH_REFUND) {
            return RISK_WATCH;
        }
        return null;
    }

    private static boolean isKnownReason(String reasonCode) {
        return "FREQ_RETURN".equals(reasonCode) || "EMPTY_BOX".equals(reasonCode)
                || "SWAP".equals(reasonCode) || "ABUSE_7DAY".equals(reasonCode)
                || "FAKE_CLAIM".equals(reasonCode) || "THREAT".equals(reasonCode);
    }

    private static boolean hasActiveFlag(Delegator delegator, String buyerNick) throws GenericEntityException {
        if (buyerNick == null) {
            return false;
        }
        List<GenericValue> flags = EntityQuery.use(delegator).from("MfgBuyerFlag").queryList();
        java.sql.Date today = java.sql.Date.valueOf(LocalDate.now());
        for (GenericValue flag : flags) {
            if (buyerNick.equals(flag.getString("buyerNick"))
                    && isFlagLive(flag.getString("statusId"), flag.getDate("expiresDate"), today)) {
                return true;
            }
        }
        return false;
    }

    private static String activeRiskLevel(Delegator delegator, String buyerNick)
            throws GenericEntityException {
        if (buyerNick == null) {
            return null;
        }
        String level = null;
        List<GenericValue> flags = EntityQuery.use(delegator).from("MfgBuyerFlag").queryList();
        java.sql.Date today = java.sql.Date.valueOf(LocalDate.now());
        for (GenericValue flag : flags) {
            if (!buyerNick.equals(flag.getString("buyerNick"))
                    || !isFlagLive(flag.getString("statusId"), flag.getDate("expiresDate"), today)) {
                continue;
            }
            if (RISK_BLOCK.equals(flag.getString("riskLevel"))) {
                return RISK_BLOCK;
            }
            level = flag.getString("riskLevel");
        }
        return level;
    }

    private static Map<String, Object> toBuyerFlagMap(GenericValue flag) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("buyerKey", flag.getString("buyerKey"));
        map.put("channelId", flag.getString("channelId"));
        map.put("buyerNick", flag.getString("buyerNick"));
        map.put("receiverName", flag.getString("receiverName"));
        map.put("receiverMobile", flag.getString("receiverMobile"));
        map.put("maskedMobile", maskMobile(flag.getString("receiverMobile")));
        map.put("addressText", flag.getString("addressText"));
        map.put("riskLevel", flag.getString("riskLevel"));
        map.put("reasonCode", flag.getString("reasonCode"));
        map.put("evidenceNote", flag.getString("evidenceNote"));
        map.put("statusId", flag.getString("statusId"));
        map.put("releasedDate", flag.getDate("releasedDate"));
        map.put("expiresDate", flag.getDate("expiresDate"));
        map.put("live", isFlagLive(flag.getString("statusId"), flag.getDate("expiresDate"),
                java.sql.Date.valueOf(LocalDate.now())) ? "Y" : "N");
        map.put("note", flag.getString("note"));
        map.put("createdStamp", flag.getTimestamp("createdStamp"));
        return map;
    }

    // ------------------------------------------------------- after sales quality

    /**
     * Daily series and after sales KPI for the dashboard, rendered as plain HTML bars so the page
     * needs no chart library and works offline.
     *
     * @param dctx the dispatch context
     * @param context service context, see {@code servicedef/services.xml#mfgDashboardTrends}
     * @return the service result containing {@code kpi}, {@code days} and {@code responsibilities}
     */
    public static Map<String, Object> dashboardTrends(DispatchContext dctx, Map<String, ? extends Object> context) {
        Delegator delegator = dctx.getDelegator();
        long days = toLong(context.get("dayCount"), 30);
        if (days <= 0 || days > 180) {
            days = 30;
        }
        LocalDate today = LocalDate.now();
        LocalDate start = today.minusDays(days - 1);
        java.sql.Date fromDate = java.sql.Date.valueOf(start);
        java.sql.Date toDate = java.sql.Date.valueOf(today);
        try {
            Map<String, Long> shippedByDay = new LinkedHashMap<>();
            Map<String, Long> claimsByDay = new LinkedHashMap<>();
            for (String label : lastDayLabels(today, (int) days)) {
                shippedByDay.put(label, Long.valueOf(0));
                claimsByDay.put(label, Long.valueOf(0));
            }

            long shippedUnits = 0;
            for (GenericValue delivery : EntityQuery.use(delegator).from("MfgDelivery").queryList()) {
                java.sql.Date deliveryDate = delivery.getDate("deliveryDate");
                if (deliveryDate == null || deliveryDate.before(fromDate) || deliveryDate.after(toDate)) {
                    continue;
                }
                String note = delivery.getString("note");
                if (note != null && note.startsWith("换货")) {
                    continue;
                }
                String label = deliveryDate.toLocalDate().format(MONTH_DAY);
                if (!shippedByDay.containsKey(label)) {
                    continue;
                }
                long units = EntityQuery.use(delegator).from("MfgDeliverySn")
                        .where("deliveryId", delivery.getString("deliveryId")).queryList().size();
                shippedByDay.put(label, Long.valueOf(shippedByDay.get(label).longValue() + units));
                shippedUnits += units;
            }

            long claimCount = 0;
            long openClaims = 0;
            double refundTotal = 0.0;
            double hoursTotal = 0.0;
            double costTotal = 0.0;
            Map<String, Double> costByResponsibility = new LinkedHashMap<>();
            Map<String, Long> countByResponsibility = new LinkedHashMap<>();
            for (GenericValue claim : EntityQuery.use(delegator).from("MfgWarrantyClaim").queryList()) {
                if (RMA_OPEN.equals(claim.getString("statusId"))) {
                    openClaims++;
                }
                java.sql.Date receivedDate = claim.getDate("receivedDate");
                if (receivedDate == null || receivedDate.before(fromDate) || receivedDate.after(toDate)) {
                    continue;
                }
                claimCount++;
                String label = receivedDate.toLocalDate().format(MONTH_DAY);
                if (claimsByDay.containsKey(label)) {
                    claimsByDay.put(label, Long.valueOf(claimsByDay.get(label).longValue() + 1));
                }
                double refund = claim.getDouble("refundAmount") == null
                        ? 0.0 : claim.getDouble("refundAmount").doubleValue();
                double hours = claim.getDouble("repairHours") == null
                        ? 0.0 : claim.getDouble("repairHours").doubleValue();
                double freight = FREIGHT_SELLER.equals(claim.getString("freightPayer"))
                        ? QUALITY_FREIGHT_COST : 0.0;
                double cost = claimCost(refund, freight, hours, QUALITY_HOURLY_RATE);
                refundTotal += refund;
                hoursTotal += hours;
                costTotal += cost;
                String responsibility = claim.getString("responsibility");
                if (responsibility == null) {
                    responsibility = "-";
                }
                Double current = costByResponsibility.get(responsibility);
                costByResponsibility.put(responsibility, Double.valueOf(current == null ? cost
                        : current.doubleValue() + cost));
                Long times = countByResponsibility.get(responsibility);
                countByResponsibility.put(responsibility, Long.valueOf(times == null ? 1
                        : times.longValue() + 1));
            }

            long maxShipped = 1;
            long maxClaims = 1;
            for (Long value : shippedByDay.values()) {
                maxShipped = Math.max(maxShipped, value.longValue());
            }
            for (Long value : claimsByDay.values()) {
                maxClaims = Math.max(maxClaims, value.longValue());
            }
            List<Map<String, Object>> dayRows = new ArrayList<>(shippedByDay.size());
            for (Map.Entry<String, Long> entry : shippedByDay.entrySet()) {
                Map<String, Object> row = new LinkedHashMap<>();
                long shipped = entry.getValue().longValue();
                Long claims = claimsByDay.get(entry.getKey());
                long claimValue = claims == null ? 0 : claims.longValue();
                row.put("date", entry.getKey());
                row.put("shipped", Long.valueOf(shipped));
                row.put("claims", Long.valueOf(claimValue));
                row.put("shipPct", Long.valueOf(percentOfMax(shipped, maxShipped)));
                row.put("claimPct", Long.valueOf(percentOfMax(claimValue, maxClaims)));
                dayRows.add(row);
            }

            double maxCost = 1.0;
            for (Double value : costByResponsibility.values()) {
                maxCost = Math.max(maxCost, value.doubleValue());
            }
            List<Map<String, Object>> responsibilityRows = new ArrayList<>();
            for (Map.Entry<String, Double> entry : costByResponsibility.entrySet()) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("responsibility", entry.getKey());
                row.put("claimCount", countByResponsibility.get(entry.getKey()));
                row.put("cost", entry.getValue());
                row.put("pct", Long.valueOf(Math.round(entry.getValue().doubleValue() * 100.0 / maxCost)));
                responsibilityRows.add(row);
            }
            responsibilityRows.sort(new Comparator<Map<String, Object>>() {
                @Override
                public int compare(Map<String, Object> left, Map<String, Object> right) {
                    return ((Double) right.get("cost")).compareTo((Double) left.get("cost"));
                }
            });

            Map<String, Object> kpi = new LinkedHashMap<>();
            kpi.put("shippedUnits", Long.valueOf(shippedUnits));
            kpi.put("claimCount", Long.valueOf(claimCount));
            kpi.put("openClaims", Long.valueOf(openClaims));
            kpi.put("claimRate", percent(claimCount, shippedUnits));
            kpi.put("refundTotal", Double.valueOf(refundTotal));
            kpi.put("hoursTotal", Double.valueOf(hoursTotal));
            kpi.put("costTotal", Double.valueOf(costTotal));
            kpi.put("costPerUnit", Double.valueOf(shippedUnits == 0 ? 0.0 : costTotal / shippedUnits));

            Map<String, Object> result = ServiceUtil.returnSuccess();
            result.put("kpi", kpi);
            result.put("days", dayRows);
            result.put("responsibilities", responsibilityRows);
            result.put("fromDate", fromDate);
            result.put("toDate", toDate);
            result.put("refreshedAt", java.time.LocalTime.now()
                    .format(DateTimeFormatter.ofPattern("HH:mm:ss", Locale.ROOT)));
            return result;
        } catch (GenericEntityException e) {
            return ServiceUtil.returnError("Could not build the dashboard series: " + e.getMessage());
        }
    }

    /**
     * Collects what the shop floor should look at today: work orders with material shortages, and
     * open claims whose 7 day or 15 day window is running out or has already passed.
     *
     * @param dctx the dispatch context
     * @param context service context, see {@code servicedef/services.xml#mfgAlerts}
     * @return the service result containing {@code alerts} (worst first) and their counts
     */
    public static Map<String, Object> alerts(DispatchContext dctx, Map<String, ? extends Object> context) {
        Delegator delegator = dctx.getDelegator();
        try {
            List<Map<String, Object>> alerts = buildAlerts(delegator);
            int errors = 0;
            int shortages = 0;
            for (Map<String, Object> alert : alerts) {
                if (ALERT_ERROR.equals(alert.get("level"))) {
                    errors++;
                }
                if (ALERT_SHORTAGE.equals(alert.get("typeId"))) {
                    shortages++;
                }
            }
            Map<String, Object> result = ServiceUtil.returnSuccess();
            result.put("alerts", alerts);
            result.put("alertCount", Integer.valueOf(alerts.size()));
            result.put("errorCount", Integer.valueOf(errors));
            result.put("shortageCount", Integer.valueOf(shortages));
            result.put("claimCount", Integer.valueOf(alerts.size() - shortages));
            return result;
        } catch (GenericEntityException e) {
            return ServiceUtil.returnError("Could not compute the alerts: " + e.getMessage());
        }
    }

    /**
     * Builds the dashboard alerts: one per work order with a shortage, and one per open claim window
     * that is about to close or already closed.
     *
     * @param delegator the delegator
     * @return the alerts, already first and then the closest deadline first
     * @throws GenericEntityException when the data cannot be read
     */
    static List<Map<String, Object>> buildAlerts(Delegator delegator) throws GenericEntityException {
        List<Map<String, Object>> alerts = new ArrayList<>();

        Map<String, List<Map<String, Object>>> byOrder = new LinkedHashMap<>();
        for (Map<String, Object> row : shortageRows(delegator)) {
            String workOrderId = String.valueOf(row.get("workOrderId"));
            List<Map<String, Object>> rows = byOrder.get(workOrderId);
            if (rows == null) {
                rows = new ArrayList<>();
                byOrder.put(workOrderId, rows);
            }
            rows.add(row);
        }
        for (Map.Entry<String, List<Map<String, Object>>> entry : byOrder.entrySet()) {
            List<Map<String, Object>> rows = entry.getValue();
            Map<String, Object> first = rows.get(0);
            double total = 0.0;
            List<String> codes = new ArrayList<>();
            for (Map<String, Object> row : rows) {
                total += numberOrZero(row.get("shortage"));
                if (codes.size() < 3) {
                    codes.add(String.valueOf(row.get("componentCode")));
                }
            }
            java.sql.Date dueDate = (java.sql.Date) first.get("dueDate");
            long dueHours = 0L;
            if (dueDate != null) {
                dueHours = hoursLeft(java.sql.Timestamp.valueOf(dueDate.toLocalDate().atTime(23, 59, 59)));
            }
            Map<String, Object> alert = new LinkedHashMap<>();
            alert.put("typeId", ALERT_SHORTAGE);
            alert.put("level", ALERT_ERROR);
            alert.put("refId", entry.getKey());
            alert.put("subject", first.get("productCode"));
            alert.put("dueDate", dueDate);
            alert.put("hoursLeft", Long.valueOf(dueHours));
            alert.put("detail", rows.size() + " 行欠料，共 " + formatQty(total)
                    + "；" + String.join("、", codes) + (rows.size() > codes.size() ? " 等" : ""));
            alerts.add(alert);
        }

        List<GenericValue> openClaims = EntityQuery.use(delegator).from("MfgWarrantyClaim")
                .where("statusId", RMA_OPEN).orderBy("sevenDayDeadline").queryList();
        for (GenericValue claim : openClaims) {
            addClaimAlert(alerts, claim, "sevenDayDeadline", ALERT_CLAIM_7DAY);
            addClaimAlert(alerts, claim, "afterSaleDeadline", ALERT_CLAIM_AFTERSALE);
        }

        addSoftwareAlerts(alerts, delegator);
        addStockAlerts(alerts, delegator);

        alerts.sort(new Comparator<Map<String, Object>>() {
            @Override
            public int compare(Map<String, Object> left, Map<String, Object> right) {
                String leftLevel = String.valueOf(left.get("level"));
                String rightLevel = String.valueOf(right.get("level"));
                if (!leftLevel.equals(rightLevel)) {
                    return ALERT_ERROR.equals(leftLevel) ? -1 : 1;
                }
                return ((Long) left.get("hoursLeft")).compareTo((Long) right.get("hoursLeft"));
            }
        });
        return alerts;
    }

    /**
     * Adds the alert of one claim window when it is inside the warning window or already passed.
     *
     * @param alerts the list to add to
     * @param claim the claim
     * @param deadlineField the field holding the deadline
     * @param typeId the alert type
     */
    private static void addClaimAlert(List<Map<String, Object>> alerts, GenericValue claim,
            String deadlineField, String typeId) {
        java.sql.Timestamp deadline = claim.getTimestamp(deadlineField);
        if (deadline == null) {
            return;
        }
        long hours = hoursLeft(deadline);
        if (hours > DEADLINE_WARNING_HOURS) {
            return;
        }
        StringBuilder detail = new StringBuilder(ALERT_CLAIM_7DAY.equals(typeId)
                ? "7 天无理由窗口" : "15 天售后窗口");
        detail.append(hours < 0 ? "已经过期 " + (-hours) + " 小时" : "还有 " + hours + " 小时");
        if (claim.getString("claimType") != null) {
            detail.append("；").append(claim.getString("claimType"));
        }
        if (claim.getString("tradeNo") != null) {
            detail.append("；订单 ").append(claim.getString("tradeNo"));
        }
        Map<String, Object> alert = new LinkedHashMap<>();
        alert.put("typeId", typeId);
        alert.put("level", hours < 0 ? ALERT_ERROR : ALERT_WARN);
        alert.put("refId", claim.getString("claimId"));
        alert.put("subject", claim.getString("sn"));
        alert.put("dueDate", deadline);
        alert.put("hoursLeft", Long.valueOf(hours));
        alert.put("detail", detail.toString());
        alerts.add(alert);
    }

    /**
     * Adds one alert per open software project that is late or has already used more person days than
     * the estimate gave.
     *
     * @param alerts the list to add to
     * @param delegator the delegator
     * @throws GenericEntityException when the projects cannot be read
     */
    private static void addSoftwareAlerts(List<Map<String, Object>> alerts, Delegator delegator)
            throws GenericEntityException {
        List<GenericValue> projects = EntityQuery.use(delegator).from("MfgSoftwareProject")
                .orderBy("plannedEndDate").queryList();
        LocalDate today = LocalDate.now();
        for (GenericValue project : projects) {
            String statusId = project.getString("statusId");
            if (SW_DONE.equals(statusId) || SW_CANCELLED.equals(statusId)) {
                continue;
            }
            double planned = numberOrZero(project.get("plannedDays"));
            double actualDays = numberOrZero(project.get("actualHours")) / HOURS_PER_DAY;
            boolean overEffort = planned > 0.0 && actualDays > planned;
            java.sql.Date end = project.getDate("plannedEndDate");
            Long daysLeft = end == null ? null
                    : Long.valueOf(java.time.temporal.ChronoUnit.DAYS.between(today, end.toLocalDate()));
            boolean late = daysLeft != null && daysLeft.longValue() < 0L;
            boolean soon = daysLeft != null && daysLeft.longValue() >= 0L && daysLeft.longValue() <= 3L;
            if (!overEffort && !late && !soon) {
                continue;
            }
            StringBuilder detail = new StringBuilder();
            if (planned > 0.0) {
                detail.append("已用 ").append(formatQty(actualDays)).append('/')
                        .append(formatQty(planned)).append(" 人天");
            }
            if (daysLeft != null) {
                if (detail.length() > 0) {
                    detail.append("；");
                }
                detail.append(daysLeft.longValue() < 0L
                        ? "计划结束日已过 " + (-daysLeft.longValue()) + " 天"
                        : "距计划结束 " + daysLeft.longValue() + " 天");
            }
            Map<String, Object> alert = new LinkedHashMap<>();
            alert.put("typeId", ALERT_SOFTWARE);
            alert.put("level", overEffort || late ? ALERT_ERROR : ALERT_WARN);
            alert.put("refId", project.getString("projectCode"));
            alert.put("subject", project.getString("projectName"));
            alert.put("dueDate", end);
            alert.put("hoursLeft", daysLeft == null ? Long.valueOf(0L)
                    : Long.valueOf(daysLeft.longValue() * 24L));
            alert.put("detail", detail.toString());
            alerts.add(alert);
        }
    }

    /**
     * Flattens the shortages of every open work order into one row per component, ordered by due
     * date, so the page and the CSV export tell the same numbers.
     *
     * @param delegator the delegator
     * @return one row per component that is short, empty when everything is kitted
     * @throws GenericEntityException when the data cannot be read
     */
    static List<Map<String, Object>> shortageRows(Delegator delegator) throws GenericEntityException {
        List<Map<String, Object>> rows = new ArrayList<>();
        List<GenericValue> orders = EntityQuery.use(delegator).from("MfgWorkOrder")
                .orderBy("dueDate").queryList();
        for (GenericValue order : orders) {
            String statusId = order.getString("statusId");
            if (WO_CLOSED.equals(statusId) || WO_COMPLETED.equals(statusId)) {
                continue;
            }
            for (Map<String, Object> line : kittingLines(delegator, order)) {
                double shortage = numberOrZero(line.get("shortage"));
                if (shortage <= 0.0001) {
                    continue;
                }
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("workOrderId", order.getString("workOrderId"));
                row.put("productCode", order.getString("productCode"));
                row.put("dueDate", order.getDate("dueDate"));
                row.put("customerName", order.getString("customerName"));
                row.put("componentCode", line.get("componentCode"));
                row.put("componentName", line.get("componentName"));
                row.put("requiredQty", line.get("requiredQty"));
                row.put("issuedQty", line.get("issuedQty"));
                row.put("shortage", Double.valueOf(shortage));
                rows.add(row);
            }
        }
        return rows;
    }

    /**
     * Formats a quantity for the short texts of the alerts: at most three decimals and a dot as the
     * decimal separator, whatever the server locale is.
     *
     * @param value the quantity
     * @return the formatted quantity
     */
    static String formatQty(double value) {
        DecimalFormat format = new DecimalFormat("0.###",
                DecimalFormatSymbols.getInstance(Locale.ROOT));
        return format.format(value);
    }

    /**
     * Quotes one CSV cell: a value holding a comma, a quote or a line break is wrapped in double
     * quotes and its own quotes are doubled.
     *
     * @param value the cell value, may be {@code null}
     * @return the cell as it goes into the file
     */
    static String csvCell(String value) {
        if (value == null) {
            return "";
        }
        boolean quote = value.indexOf(',') >= 0 || value.indexOf('"') >= 0
                || value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0;
        String escaped = value.replace("\"", "\"\"");
        return quote ? "\"" + escaped + "\"" : escaped;
    }

    /**
     * Joins cells into one CSV line, without the line break.
     *
     * @param cells the cells, {@code null} cells become empty
     * @return the line
     */
    static String csvLine(List<String> cells) {
        StringBuilder line = new StringBuilder();
        for (String cell : cells) {
            if (line.length() > 0) {
                line.append(',');
            }
            line.append(csvCell(cell));
        }
        return line.toString();
    }

    // ==================== inventory: append only movements, totals summed from them ====================

    /**
     * Tells whether a movement type is one of the six the ledger knows.
     *
     * @param movementTypeId the type to test
     * @return {@code true} when it is known
     */
    static boolean isKnownMovement(String movementTypeId) {
        for (String known : MOVE_TYPES) {
            if (known.equals(movementTypeId)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Signed quantity change of one movement. Receipts and returns add, issues and scraps take away,
     * a count and an adjustment carry their own signed quantity.
     *
     * @param movementTypeId the movement type
     * @param qty the quantity as entered, positive for the directed types
     * @return the change of the quantity on hand
     */
    static double movementDiff(String movementTypeId, double qty) {
        if (movementTypeId == null) {
            return 0.0;
        }
        if (MOVE_RESERVE.equals(movementTypeId) || MOVE_RELEASE.equals(movementTypeId)) {
            return 0.0;
        }
        for (String in : MOVE_IN_TYPES) {
            if (in.equals(movementTypeId)) {
                return Math.abs(qty);
            }
        }
        for (String out : MOVE_OUT_TYPES) {
            if (out.equals(movementTypeId)) {
                return -Math.abs(qty);
            }
        }
        return qty;
    }

    /**
     * What may still be issued out of a lot: everything on hand, minus what is reserved for other work
     * orders, except for a lot that failed the incoming inspection.
     *
     * @param onHand the quantity in stock
     * @param reserved the quantity earmarked for work orders
     * @param inspectStatusId the inspection status of the lot
     * @return the quantity that may be issued, never negative
     */
    static double availableOf(double onHand, double reserved, String inspectStatusId) {
        if (IQC_FAIL.equals(inspectStatusId)) {
            return 0.0;
        }
        double free = onHand - reserved;
        return free <= 0.0 ? 0.0 : round2(free);
    }

    /**
     * What may still be issued out of a lot that nothing is reserved on.
     *
     * @param onHand the quantity in stock
     * @param inspectStatusId the inspection status of the lot
     * @return the quantity that may be issued, never negative
     */
    static double availableOf(double onHand, String inspectStatusId) {
        return availableOf(onHand, 0.0, inspectStatusId);
    }

    /**
     * Tells whether a lot holds finished goods instead of a purchased component.
     *
     * @param lot the lot, may be {@code null}
     * @return {@code true} for the goods of a work order
     */
    static boolean isProductLot(GenericValue lot) {
        return lot != null && LOT_PRODUCT.equals(lot.getString("lotKindId"));
    }

    /**
     * The kind of a lot, with the empty value of the rows written before the split read as a
     * purchased component.
     *
     * @param lot the lot, may be {@code null}
     * @return {@link #LOT_COMPONENT} or {@link #LOT_PRODUCT}
     */
    static String lotKindOf(GenericValue lot) {
        return isProductLot(lot) ? LOT_PRODUCT : LOT_COMPONENT;
    }

    /**
     * Whether a lot passes a kind filter; an empty filter accepts everything.
     *
     * @param lot the lot
     * @param kind the filter, may be {@code null}
     * @return {@code true} when the lot belongs to the filter
     */
    static boolean matchesLotKind(GenericValue lot, String kind) {
        return kind == null || kind.equals(lotKindOf(lot));
    }

    /**
     * Identifier of the finished goods lot of a work order.
     *
     * @param workOrderId the work order
     * @return {@code FG-WO-yyMMdd-NN}, or {@code null} without a work order
     */
    static String productLotId(String workOrderId) {
        return workOrderId == null ? null : PRODUCT_LOT_PREFIX + workOrderId;
    }

    /**
     * Cost of one finished unit: the actual cost of the work order spread over its good units. The
     * cost of a model is never stored on the model, so a second batch may cost something else.
     *
     * @param actualTotal the actual cost of the work order
     * @param qtyOk the good units it produced
     * @return the cost per unit, {@code 0} when nothing good came out
     */
    static double productUnitCost(double actualTotal, double qtyOk) {
        return qtyOk <= 0.0 ? 0.0 : round2(actualTotal / qtyOk);
    }

    /**
     * The purchased component lots, so the part oriented views never count finished goods.
     *
     * @param delegator the delegator
     * @return the component lots
     * @throws GenericEntityException when they cannot be read
     */
    private static List<GenericValue> componentLots(Delegator delegator) throws GenericEntityException {
        List<GenericValue> lots = new ArrayList<>();
        for (GenericValue lot : EntityQuery.use(delegator).from("MfgMaterialLot").queryList()) {
            if (!isProductLot(lot)) {
                lots.add(lot);
            }
        }
        return lots;
    }

    /**
     * Everything that happened to one lot, oldest first.
     *
     * @param delegator the delegator
     * @param lotId the lot
     * @return the movements
     * @throws GenericEntityException when they cannot be read
     */
    private static List<GenericValue> movementsOf(Delegator delegator, String lotId)
            throws GenericEntityException {
        return EntityQuery.use(delegator).from("MfgInventoryDetail")
                .where("lotId", lotId).orderBy("seqId").queryList();
    }

    /**
     * Appends one movement to the ledger of a lot, the same way OFBiz keeps its own inventory: the
     * row is only ever created, the totals of the lot are summed afterwards.
     *
     * @return the row that was written
     * @throws GenericEntityException when it cannot be stored
     */
    static GenericValue recordMovement(Delegator delegator, GenericValue lot, String movementTypeId,
            double diff, Object unitCost, String workOrderId, String physicalInventoryId,
            String referenceNo, String operatorName, java.sql.Timestamp effectiveDate, String note)
            throws GenericEntityException {
        long seq = nextMovementSeq(delegator, lot.getString("lotId"));
        GenericValue movement = delegator.makeValue("MfgInventoryDetail");
        movement.set("lotId", lot.getString("lotId"));
        movement.set("seqId", Long.valueOf(seq));
        movement.set("movementTypeId", movementTypeId);
        movement.set("quantityDiff", Double.valueOf(round2(diff)));
        if (unitCost != null) {
            movement.set("unitCost", unitCost);
        }
        movement.set("workOrderId", workOrderId);
        movement.set("physicalInventoryId", physicalInventoryId);
        movement.set("referenceNo", referenceNo);
        movement.set("operatorName", operatorName);
        movement.set("effectiveDate", effectiveDate == null
                ? new Timestamp(System.currentTimeMillis()) : effectiveDate);
        movement.set("note", note);
        delegator.create(movement);
        return movement;
    }

    /**
     * Recomputes the stock of a lot from its movements and stores it. Totals are never incremented,
     * so they cannot drift away from the ledger.
     *
     * @param delegator the delegator
     * @param lot the lot, updated and stored
     * @throws GenericEntityException when the movements cannot be read
     */
    static void recalcLotStock(Delegator delegator, GenericValue lot) throws GenericEntityException {
        double onHand = 0.0;
        double reserved = 0.0;
        for (GenericValue movement : movementsOf(delegator, lot.getString("lotId"))) {
            onHand += numberOrZero(movement.get("quantityDiff"));
            reserved += numberOrZero(movement.get("reservedDiff"));
        }
        lot.set("onHandQty", Double.valueOf(round2(onHand)));
        lot.set("reservedQty", Double.valueOf(reserved <= 0.0 ? 0.0 : round2(reserved)));
        lot.set("availableQty", Double.valueOf(availableOf(onHand, reserved,
                lot.getString("inspectStatusId"))));
        delegator.store(lot);
    }

    /**
     * Gives a lot that predates the ledger the receipt and the issues it already had, so every lot
     * answers "how much is left" the same way. Does nothing when the lot already has movements.
     *
     * @param delegator the delegator
     * @param lot the lot
     * @throws GenericEntityException when the ledger cannot be written
     */
    static void ensureLotLedger(Delegator delegator, GenericValue lot) throws GenericEntityException {
        String lotId = lot.getString("lotId");
        if (lotId == null || !movementsOf(delegator, lotId).isEmpty()) {
            recalcLotStock(delegator, lot);
            return;
        }
        double receivedQty = numberOrZero(lot.get("receivedQty"));
        if (receivedQty > 0.0) {
            java.sql.Date receivedDate = lot.getDate("receivedDate");
            recordMovement(delegator, lot, MOVE_RECEIPT, receivedQty, lot.get("unitPrice"), null, null,
                    lot.getString("supplierLot"), null,
                    receivedDate == null ? null
                            : Timestamp.valueOf(receivedDate.toLocalDate().atStartOfDay()),
                    "首次建账：到货");
        }
        List<GenericValue> issues = EntityQuery.use(delegator).from("MfgMaterialIssue")
                .where("lotId", lotId).orderBy("issuedDate").queryList();
        for (GenericValue issue : issues) {
            java.sql.Date issuedDate = issue.getDate("issuedDate");
            recordMovement(delegator, lot, MOVE_ISSUE, -numberOrZero(issue.get("qty")),
                    lot.get("unitPrice"), issue.getString("workOrderId"), null, null, null,
                    issuedDate == null ? null
                            : Timestamp.valueOf(issuedDate.toLocalDate().atStartOfDay()),
                    "首次建账：历史发料");
        }
        recalcLotStock(delegator, lot);
    }

    private static long nextMovementSeq(Delegator delegator, String lotId) throws GenericEntityException {
        List<GenericValue> existing = movementsOf(delegator, lotId);
        if (existing.isEmpty()) {
            return 1L;
        }
        Long last = existing.get(existing.size() - 1).getLong("seqId");
        return last == null ? existing.size() + 1L : last.longValue() + 1L;
    }

    /**
     * What one lot has reserved for one work order right now: the sum of the reservation movements.
     *
     * @param delegator the delegator
     * @param lotId the lot
     * @param workOrderId the work order
     * @return the reserved quantity, {@code 0} when nothing is reserved
     * @throws GenericEntityException when the movements cannot be read
     */
    static double reservationOf(Delegator delegator, String lotId, String workOrderId)
            throws GenericEntityException {
        double reserved = 0.0;
        for (GenericValue movement : movementsOf(delegator, lotId)) {
            if (!workOrderId.equals(movement.getString("workOrderId"))) {
                continue;
            }
            reserved += numberOrZero(movement.get("reservedDiff"));
        }
        return reserved <= 0.0 ? 0.0 : reserved;
    }

    /**
     * Appends a reservation movement. It does not change the quantity on hand, only what is
     * earmarked for a work order, which is exactly how OFBiz keeps its available to promise.
     *
     * @param delegator the delegator
     * @param lot the lot
     * @param movementTypeId {@link #MOVE_RESERVE} or {@link #MOVE_RELEASE}
     * @param diff the signed change of the reserved quantity
     * @param workOrderId the work order that reserves or gives the material back
     * @param operatorName who did it
     * @param note a remark
     * @throws GenericEntityException when it cannot be stored
     */
    static void recordReservation(Delegator delegator, GenericValue lot, String movementTypeId,
            double diff, String workOrderId, String operatorName, String note)
            throws GenericEntityException {
        GenericValue movement = delegator.makeValue("MfgInventoryDetail");
        movement.set("lotId", lot.getString("lotId"));
        movement.set("seqId", Long.valueOf(nextMovementSeq(delegator, lot.getString("lotId"))));
        movement.set("movementTypeId", movementTypeId);
        movement.set("quantityDiff", Double.valueOf(0.0));
        movement.set("reservedDiff", Double.valueOf(round2(diff)));
        movement.set("workOrderId", workOrderId);
        movement.set("operatorName", operatorName);
        movement.set("effectiveDate", new Timestamp(System.currentTimeMillis()));
        movement.set("note", note);
        delegator.create(movement);
    }

    /**
     * Gives back what one lot still has reserved for one work order, at most {@code qty}. Called when
     * the material is issued, so the reservation never blocks its own work order.
     *
     * @param delegator the delegator
     * @param lot the lot
     * @param workOrderId the work order
     * @param qty how much is being issued
     * @param operatorName who did it
     * @throws GenericEntityException when the reservation cannot be written
     */
    static void releaseReservation(Delegator delegator, GenericValue lot, String workOrderId,
            double qty, String operatorName) throws GenericEntityException {
        if (workOrderId == null) {
            return;
        }
        double reserved = reservationOf(delegator, lot.getString("lotId"), workOrderId);
        double release = Math.min(qty, reserved);
        if (release <= 0.0001) {
            return;
        }
        recordReservation(delegator, lot, MOVE_RELEASE, -release, workOrderId, operatorName,
                "发料时释放预留");
    }

    /**
     * Reserved quantity per component of one work order.
     *
     * @param delegator the delegator
     * @param workOrderId the work order
     * @return the reserved quantity by component code
     * @throws GenericEntityException when the ledger cannot be read
     */
    private static Map<String, Double> reservedByComponent(Delegator delegator, String workOrderId)
            throws GenericEntityException {
        Map<String, Double> byComponent = new LinkedHashMap<>();
        if (workOrderId == null) {
            return byComponent;
        }
        Map<String, GenericValue> lots = new LinkedHashMap<>();
        for (GenericValue lot : componentLots(delegator)) {
            lots.put(lot.getString("lotId"), lot);
        }
        for (GenericValue movement : EntityQuery.use(delegator).from("MfgInventoryDetail")
                .where("workOrderId", workOrderId).queryList()) {
            double reserved = numberOrZero(movement.get("reservedDiff"));
            if (reserved == 0.0) {
                continue;
            }
            GenericValue lot = lots.get(movement.getString("lotId"));
            if (lot == null) {
                continue;
            }
            String componentCode = lot.getString("componentCode");
            byComponent.put(componentCode, Double.valueOf(numberOrZero(byComponent.get(componentCode))
                    + reserved));
        }
        return byComponent;
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
        Delegator delegator = dctx.getDelegator();
        String workOrderId = toText(context.get("workOrderId"));
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
            String operator = toText(context.get("operatorName"));
            for (Map<String, Object> line : kittingLines(delegator, order)) {
                double missing = numberOrZero(line.get("shortage"));
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
                    if (isProductLot(lot)) {
                        continue;
                    }
                    ensureLotLedger(delegator, lot);
                    double free = numberOrZero(lot.get("availableQty"));
                    if (free <= 0.0001) {
                        continue;
                    }
                    double take = Math.min(missing, free);
                    recordReservation(delegator, lot, MOVE_RESERVE, take, workOrderId, operator,
                            "工单 " + workOrderId + " 预留");
                    recalcLotStock(delegator, lot);
                    missing = round2(missing - take);
                    reservedTotal += take;
                    reservations++;
                }
                shortTotal += missing;
            }
            result.put("reservedQty", Double.valueOf(round2(reservedTotal)));
            result.put("shortQty", Double.valueOf(round2(shortTotal)));
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
        Delegator delegator = dctx.getDelegator();
        String workOrderId = toText(context.get("workOrderId"));
        Map<String, Object> result = ServiceUtil.returnSuccess();
        result.put("workOrderId", workOrderId);
        result.put("releasedQty", Double.valueOf(0.0));
        if (workOrderId == null) {
            return ServiceUtil.returnError("workOrderId is required");
        }
        try {
            double released = 0.0;
            String operator = toText(context.get("operatorName"));
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
                double reserved = reservationOf(delegator, lotId, workOrderId);
                if (reserved <= 0.0001) {
                    continue;
                }
                recordReservation(delegator, lot, MOVE_RELEASE, -reserved, workOrderId, operator,
                        "释放工单 " + workOrderId + " 的预留");
                recalcLotStock(delegator, lot);
                released += reserved;
            }
            result.put("releasedQty", Double.valueOf(round2(released)));
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
        Delegator delegator = dctx.getDelegator();
        String workOrderId = toText(context.get("workOrderId"));
        if (workOrderId == null) {
            return ServiceUtil.returnError("workOrderId is required");
        }
        try {
            GenericValue order = EntityQuery.use(delegator).from("MfgWorkOrder")
                    .where("workOrderId", workOrderId).queryOne();
            if (order == null) {
                return ServiceUtil.returnError("Unknown work order [" + workOrderId + "]");
            }
            if (WO_CLOSED.equals(order.getString("statusId"))) {
                return ServiceUtil.returnError("Work order [" + workOrderId + "] is already closed");
            }
            double qtyOk = 0.0;
            for (GenericValue report : EntityQuery.use(delegator).from("MfgReport")
                    .where("workOrderId", workOrderId).queryList()) {
                qtyOk += numberOrZero(report.get("qtyOk"));
            }
            qtyOk = round2(qtyOk);
            if (qtyOk <= 0.0001) {
                return ServiceUtil.returnError("Report the good units before completing work order ["
                        + workOrderId + "]");
            }

            Map<String, Object> productCodeContext = new LinkedHashMap<>();
            productCodeContext.put("workOrderId", workOrderId);
            double unitCost = productUnitCost(numberOrZero(workOrderCost(dctx, productCodeContext)
                    .get("actualTotal")), qtyOk);

            String lotId = productLotId(workOrderId);
            Object dateInput = parseDate(context.get("completeDate"));
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
                lot.set("lotKindId", LOT_PRODUCT);
                lot.set("workOrderId", workOrderId);
                lot.set("componentCode", productCode);
                lot.set("componentName", product == null ? null : product.getString("productName"));
                lot.set("receivedQty", Double.valueOf(0.0));
                lot.set("receivedDate", day);
                lot.set("inspectStatusId", IQC_PASS);
                lot.set("note", "工单 " + workOrderId + " 的成品");
                delegator.create(lot);
            }

            double booked = 0.0;
            for (GenericValue movement : movementsOf(delegator, lotId)) {
                if (MOVE_RECEIPT.equals(movement.getString("movementTypeId"))) {
                    booked += numberOrZero(movement.get("quantityDiff"));
                }
            }
            booked = round2(booked);
            double added = round2(qtyOk - booked);
            String message = null;
            if (added > 0.0001) {
                String operator = toText(context.get("operatorName"));
                recordMovement(delegator, lot, MOVE_RECEIPT, added, Double.valueOf(unitCost), workOrderId,
                        null, workOrderId, operator,
                        Timestamp.valueOf(day.toLocalDate().atStartOfDay()),
                        "完工入库：" + formatQty(added) + " 台");
            } else if (added < -0.0001) {
                message = "The reports now say " + formatQty(qtyOk) + " good units but " + formatQty(booked)
                        + " are already in stock, so nothing was booked; book a stock count to correct it";
            }
            lot.set("receivedQty", Double.valueOf(round2(Math.max(booked, qtyOk))));
            lot.set("unitPrice", Double.valueOf(unitCost));
            if (lot.get("inspectStatusId") == null) {
                lot.set("inspectStatusId", IQC_PASS);
            }
            delegator.store(lot);
            recalcLotStock(delegator, lot);

            order.set("statusId", WO_COMPLETED);
            if (order.get("completeDate") == null) {
                order.set("completeDate", day);
            }
            delegator.store(order);
            for (GenericValue unit : EntityQuery.use(delegator).from("MfgSn")
                    .where("workOrderId", workOrderId).queryList()) {
                if (SN_IN_PROGRESS.equals(unit.getString("statusId"))) {
                    unit.set("statusId", SN_COMPLETED);
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
     * Finished goods on hand per model: what the completions put into stock, minus what the
     * deliveries took out, with what is reserved for a sales order and what it is worth.
     *
     * @param dctx the dispatch context
     * @param context service context, see {@code servicedef/services.xml#mfgProductStock}
     * @return the service result containing {@code rows} and the totals
     */
    public static Map<String, Object> productStock(DispatchContext dctx, Map<String, ? extends Object> context) {
        Delegator delegator = dctx.getDelegator();
        Map<String, Object> result = ServiceUtil.returnSuccess();
        result.put("rows", new ArrayList<Map<String, Object>>());
        result.put("productCount", Integer.valueOf(0));
        result.put("onHandTotal", Double.valueOf(0.0));
        result.put("shippedTotal", Double.valueOf(0.0));
        result.put("valueTotal", Double.valueOf(0.0));
        try {
            Map<String, Map<String, Object>> byModel = new LinkedHashMap<>();
            for (GenericValue lot : EntityQuery.use(delegator).from("MfgMaterialLot")
                    .orderBy("componentCode", "lotId").queryList()) {
                if (!isProductLot(lot)) {
                    continue;
                }
                ensureLotLedger(delegator, lot);
                String productCode = lot.getString("componentCode");
                if (productCode == null) {
                    continue;
                }
                Map<String, Object> row = byModel.get(productCode);
                if (row == null) {
                    row = new LinkedHashMap<>();
                    row.put("productCode", productCode);
                    row.put("productName", lot.getString("componentName"));
                    row.put("onHandQty", Double.valueOf(0.0));
                    row.put("reservedQty", Double.valueOf(0.0));
                    row.put("availableQty", Double.valueOf(0.0));
                    row.put("shippedQty", Double.valueOf(0.0));
                    row.put("value", Double.valueOf(0.0));
                    row.put("lotCount", Integer.valueOf(0));
                    row.put("lastCompletedDate", null);
                    byModel.put(productCode, row);
                }
                double onHand = numberOrZero(lot.get("onHandQty"));
                row.put("onHandQty", Double.valueOf(numberOrZero(row.get("onHandQty")) + onHand));
                row.put("reservedQty", Double.valueOf(numberOrZero(row.get("reservedQty"))
                        + numberOrZero(lot.get("reservedQty"))));
                row.put("availableQty", Double.valueOf(numberOrZero(row.get("availableQty"))
                        + numberOrZero(lot.get("availableQty"))));
                row.put("lotCount", Integer.valueOf(((Integer) row.get("lotCount")).intValue() + 1));
                Object price = lot.get("unitPrice");
                if (price instanceof Number) {
                    row.put("value", Double.valueOf(numberOrZero(row.get("value"))
                            + onHand * ((Number) price).doubleValue()));
                }
                java.sql.Date receivedDate = lot.getDate("receivedDate");
                Object last = row.get("lastCompletedDate");
                if (receivedDate != null && (last == null || receivedDate.after((java.util.Date) last))) {
                    row.put("lastCompletedDate", receivedDate);
                }
                double shipped = 0.0;
                for (GenericValue movement : movementsOf(delegator, lot.getString("lotId"))) {
                    if (MOVE_ISSUE.equals(movement.getString("movementTypeId"))) {
                        shipped += -numberOrZero(movement.get("quantityDiff"));
                    }
                }
                row.put("shippedQty", Double.valueOf(numberOrZero(row.get("shippedQty")) + shipped));
            }
            List<Map<String, Object>> rows = new ArrayList<>(byModel.size());
            double onHandTotal = 0.0;
            double shippedTotal = 0.0;
            double valueTotal = 0.0;
            for (Map<String, Object> row : byModel.values()) {
                row.put("onHandQty", Double.valueOf(round2(numberOrZero(row.get("onHandQty")))));
                row.put("reservedQty", Double.valueOf(round2(numberOrZero(row.get("reservedQty")))));
                row.put("availableQty", Double.valueOf(round2(numberOrZero(row.get("availableQty")))));
                row.put("shippedQty", Double.valueOf(round2(numberOrZero(row.get("shippedQty")))));
                row.put("value", Double.valueOf(round2(numberOrZero(row.get("value")))));
                onHandTotal += numberOrZero(row.get("onHandQty"));
                shippedTotal += numberOrZero(row.get("shippedQty"));
                valueTotal += numberOrZero(row.get("value"));
                rows.add(row);
            }
            String search = toText(context.get("search"));
            if (search != null) {
                String needle = search.toLowerCase(Locale.ROOT);
                List<Map<String, Object>> matched = new ArrayList<>();
                for (Map<String, Object> row : rows) {
                    String code = String.valueOf(row.get("productCode")).toLowerCase(Locale.ROOT);
                    String name = row.get("productName") == null ? ""
                            : String.valueOf(row.get("productName")).toLowerCase(Locale.ROOT);
                    if (code.contains(needle) || name.contains(needle)) {
                        matched.add(row);
                    }
                }
                rows = matched;
            }
            putPage(result, "rows", rows, pageOf(context), pageSizeOf(context), search);
            result.put("productCount", Integer.valueOf(rows.size()));
            result.put("onHandTotal", Double.valueOf(round2(onHandTotal)));
            result.put("shippedTotal", Double.valueOf(round2(shippedTotal)));
            result.put("valueTotal", Double.valueOf(round2(valueTotal)));
            result.put("search", search);
            return result;
        } catch (GenericEntityException e) {
            return ServiceUtil.returnError("Could not read the finished goods: " + e.getMessage());
        }
    }

    /**
     * Records one movement of one lot: receipt, issue, return, scrap or adjustment. An issue and a
     * return that name a work order also write the work order issue record, so kitting and the
     * actual material cost keep working.
     *
     * @param dctx the dispatch context
     * @param context service context, see {@code servicedef/services.xml#mfgInventoryMove}
     * @return the service result containing the new totals of the lot
     */
    public static Map<String, Object> inventoryMove(DispatchContext dctx, Map<String, ? extends Object> context) {
        Delegator delegator = dctx.getDelegator();
        String lotId = toText(context.get("lotId"));
        String movementTypeId = toText(context.get("movementTypeId"));
        double qty = toDouble(context.get("qty"), 0.0);
        String workOrderId = toText(context.get("workOrderId"));
        if (lotId == null) {
            return ServiceUtil.returnError("lotId is required");
        }
        if (!isKnownMovement(movementTypeId)) {
            return ServiceUtil.returnError("Unknown movement type [" + movementTypeId
                    + "], expected " + String.join(" / ", MOVE_TYPES));
        }
        if (qty == 0.0) {
            return ServiceUtil.returnError("qty must not be zero");
        }
        if (MOVE_COUNT.equals(movementTypeId)) {
            return ServiceUtil.returnError("Use the stock count screen to book a count");
        }
        try {
            GenericValue lot = EntityQuery.use(delegator).from("MfgMaterialLot")
                    .where("lotId", lotId).queryOne();
            if (lot == null) {
                return ServiceUtil.returnError("Unknown material lot [" + lotId + "]");
            }
            ensureLotLedger(delegator, lot);
            double diff = movementDiff(movementTypeId, qty);
            if (diff < 0.0) {
                double ownReservation = workOrderId == null ? 0.0
                        : reservationOf(delegator, lotId, workOrderId);
                double available = numberOrZero(lot.get("availableQty")) + ownReservation;
                if (available + 0.0001 < -diff) {
                    return ServiceUtil.returnError("Only " + formatQty(available) + " left in lot [" + lotId
                            + "], cannot take " + formatQty(-diff));
                }
            }
            if (workOrderId != null
                    && (MOVE_ISSUE.equals(movementTypeId) || MOVE_RETURN.equals(movementTypeId))) {
                if (EntityQuery.use(delegator).from("MfgWorkOrder")
                        .where("workOrderId", workOrderId).queryOne() == null) {
                    return ServiceUtil.returnError("Unknown work order [" + workOrderId + "]");
                }
            }
            GenericValue movement = recordMovement(delegator, lot, movementTypeId, diff,
                    optionalAmount(context.get("unitCost")), workOrderId, null,
                    toText(context.get("referenceNo")), toText(context.get("operatorName")),
                    parseTimestamp(context.get("effectiveDate")), toText(context.get("note")));
            if (workOrderId != null
                    && (MOVE_ISSUE.equals(movementTypeId) || MOVE_RETURN.equals(movementTypeId))) {
                GenericValue issue = delegator.makeValue("MfgMaterialIssue");
                issue.set("issueId", delegator.getNextSeqId("MfgMaterialIssue"));
                issue.set("workOrderId", workOrderId);
                issue.set("componentCode", lot.getString("componentCode"));
                issue.set("lotId", lotId);
                issue.set("qty", Double.valueOf(diff));
                issue.set("directionId", movementTypeId);
                issue.set("issuedDate", java.sql.Date.valueOf(LocalDate.now()));
                issue.set("note", toText(context.get("note")));
                delegator.create(issue);
            }
            if (MOVE_ISSUE.equals(movementTypeId) && workOrderId != null) {
                releaseReservation(delegator, lot, workOrderId, -diff, toText(context.get("operatorName")));
            }
            recalcLotStock(delegator, lot);

            Map<String, Object> result = ServiceUtil.returnSuccess();
            result.put("lotId", lotId);
            result.put("seqId", movement.get("seqId"));
            result.put("movementTypeId", movementTypeId);
            result.put("quantityDiff", Double.valueOf(round2(diff)));
            result.put("onHandQty", lot.get("onHandQty"));
            result.put("availableQty", lot.get("availableQty"));
            return result;
        } catch (GenericEntityException e) {
            return ServiceUtil.returnError("Could not record the movement: " + e.getMessage());
        }
    }

    /**
     * Stock on hand per component, with the safety stock of each one, so the page can show what is
     * short before the next work order needs it.
     *
     * @param dctx the dispatch context
     * @param context service context, see {@code servicedef/services.xml#mfgStockSummary}
     * @return the service result containing {@code rows} and the totals
     */
    public static Map<String, Object> stockSummary(DispatchContext dctx, Map<String, ? extends Object> context) {
        Delegator delegator = dctx.getDelegator();
        Map<String, Object> result = ServiceUtil.returnSuccess();
        result.put("rows", new ArrayList<Map<String, Object>>());
        result.put("componentCount", Integer.valueOf(0));
        result.put("belowCount", Integer.valueOf(0));
        result.put("onHandTotal", Double.valueOf(0.0));
        result.put("valueTotal", Double.valueOf(0.0));
        try {
            Map<String, GenericValue> settings = new LinkedHashMap<>();
            for (GenericValue setting : EntityQuery.use(delegator).from("MfgStockSetting").queryList()) {
                settings.put(setting.getString("componentCode"), setting);
            }
            Map<String, Map<String, Object>> byComponent = new LinkedHashMap<>();
            for (GenericValue lot : EntityQuery.use(delegator).from("MfgMaterialLot")
                    .orderBy("componentCode", "lotId").queryList()) {
                if (isProductLot(lot)) {
                    continue;
                }
                ensureLotLedger(delegator, lot);
                String componentCode = lot.getString("componentCode");
                if (componentCode == null) {
                    continue;
                }
                Map<String, Object> row = byComponent.get(componentCode);
                if (row == null) {
                    row = new LinkedHashMap<>();
                    row.put("componentCode", componentCode);
                    row.put("componentName", lot.getString("componentName"));
                    row.put("onHandQty", Double.valueOf(0.0));
                    row.put("reservedQty", Double.valueOf(0.0));
                    row.put("availableQty", Double.valueOf(0.0));
                    row.put("pendingQty", Double.valueOf(0.0));
                    row.put("failedQty", Double.valueOf(0.0));
                    row.put("value", Double.valueOf(0.0));
                    row.put("lotCount", Integer.valueOf(0));
                    byComponent.put(componentCode, row);
                }
                double onHand = numberOrZero(lot.get("onHandQty"));
                row.put("onHandQty", Double.valueOf(numberOrZero(row.get("onHandQty")) + onHand));
                row.put("reservedQty", Double.valueOf(numberOrZero(row.get("reservedQty"))
                        + numberOrZero(lot.get("reservedQty"))));
                row.put("availableQty", Double.valueOf(numberOrZero(row.get("availableQty"))
                        + numberOrZero(lot.get("availableQty"))));
                row.put("lotCount", Integer.valueOf(((Integer) row.get("lotCount")).intValue() + 1));
                String inspect = lot.getString("inspectStatusId");
                if (IQC_PENDING.equals(inspect)) {
                    row.put("pendingQty", Double.valueOf(numberOrZero(row.get("pendingQty")) + onHand));
                }
                if (IQC_FAIL.equals(inspect)) {
                    row.put("failedQty", Double.valueOf(numberOrZero(row.get("failedQty")) + onHand));
                }
                Object price = lot.get("unitPrice");
                if (price instanceof Number) {
                    row.put("value", Double.valueOf(numberOrZero(row.get("value"))
                            + onHand * ((Number) price).doubleValue()));
                }
            }
            List<Map<String, Object>> rows = new ArrayList<>(byComponent.size());
            double onHandTotal = 0.0;
            double valueTotal = 0.0;
            int below = 0;
            for (Map<String, Object> row : byComponent.values()) {
                String componentCode = (String) row.get("componentCode");
                GenericValue setting = settings.get(componentCode);
                double minimum = setting == null ? 0.0 : numberOrZero(setting.get("minimumStock"));
                double reorder = setting == null ? 0.0 : numberOrZero(setting.get("reorderQty"));
                if (setting != null && setting.getString("componentName") != null) {
                    row.put("componentName", setting.getString("componentName"));
                }
                row.put("minimumStock", Double.valueOf(round2(minimum)));
                row.put("reorderQty", Double.valueOf(round2(reorder)));
                row.put("unitOfMeasure", setting == null ? null : setting.getString("unitOfMeasure"));
                row.put("supplierName", setting == null ? null : setting.getString("supplierName"));
                row.put("onHandQty", Double.valueOf(round2(numberOrZero(row.get("onHandQty")))));
                row.put("reservedQty", Double.valueOf(round2(numberOrZero(row.get("reservedQty")))));
                row.put("availableQty", Double.valueOf(round2(numberOrZero(row.get("availableQty")))));
                row.put("value", Double.valueOf(round2(numberOrZero(row.get("value")))));
                boolean isBelow = minimum > 0.0 && numberOrZero(row.get("onHandQty")) < minimum;
                row.put("belowMinimum", Boolean.valueOf(isBelow));
                double suggested = 0.0;
                if (isBelow) {
                    suggested = reorder > 0.0 ? reorder
                            : round2(minimum - numberOrZero(row.get("onHandQty")));
                    below++;
                }
                row.put("suggestedQty", Double.valueOf(round2(suggested)));
                onHandTotal += numberOrZero(row.get("onHandQty"));
                valueTotal += numberOrZero(row.get("value"));
                rows.add(row);
            }
            String search = toText(context.get("search"));
            if (search != null) {
                String needle = search.toLowerCase(Locale.ROOT);
                List<Map<String, Object>> matched = new ArrayList<>();
                for (Map<String, Object> row : rows) {
                    String code = String.valueOf(row.get("componentCode")).toLowerCase(Locale.ROOT);
                    String name = row.get("componentName") == null ? ""
                            : String.valueOf(row.get("componentName")).toLowerCase(Locale.ROOT);
                    if (code.contains(needle) || name.contains(needle)) {
                        matched.add(row);
                    }
                }
                rows = matched;
            }
            if ("Y".equalsIgnoreCase(toText(context.get("belowOnly")))) {
                List<Map<String, Object>> matched = new ArrayList<>();
                for (Map<String, Object> row : rows) {
                    if (Boolean.TRUE.equals(row.get("belowMinimum"))) {
                        matched.add(row);
                    }
                }
                rows = matched;
            }
            putPage(result, "rows", rows, pageOf(context), pageSizeOf(context), search);
            result.put("componentCount", Integer.valueOf(rows.size()));
            result.put("belowCount", Integer.valueOf(below));
            result.put("onHandTotal", Double.valueOf(round2(onHandTotal)));
            result.put("valueTotal", Double.valueOf(round2(valueTotal)));
            result.put("search", search);
            return result;
        } catch (GenericEntityException e) {
            return ServiceUtil.returnError("Could not read the stock: " + e.getMessage());
        }
    }

    /**
     * The ledger: one row per movement, newest first, with the balance of its lot after the movement.
     *
     * @param dctx the dispatch context
     * @param context service context, see {@code servicedef/services.xml#mfgInventoryLedger}
     * @return the service result containing {@code rows} and the paging information
     */
    public static Map<String, Object> inventoryLedger(DispatchContext dctx, Map<String, ? extends Object> context) {
        Delegator delegator = dctx.getDelegator();
        String lotId = toText(context.get("lotId"));
        String componentCode = toText(context.get("componentCode"));
        String movementTypeId = toText(context.get("movementTypeId"));
        String kind = toText(context.get("kind"));
        java.sql.Date fromDate = (java.sql.Date) parseDate(context.get("fromDate"));
        java.sql.Date toDate = (java.sql.Date) parseDate(context.get("toDate"));
        Map<String, Object> result = ServiceUtil.returnSuccess();
        result.put("rows", new ArrayList<Map<String, Object>>());
        try {
            Map<String, GenericValue> lots = new LinkedHashMap<>();
            for (GenericValue lot : EntityQuery.use(delegator).from("MfgMaterialLot").queryList()) {
                lots.put(lot.getString("lotId"), lot);
            }
            List<GenericValue> movements = EntityQuery.use(delegator).from("MfgInventoryDetail")
                    .orderBy("lotId", "seqId").queryList();
            Map<String, Double> balances = new LinkedHashMap<>();
            List<Map<String, Object>> rows = new ArrayList<>();
            for (GenericValue movement : movements) {
                String movementLot = movement.getString("lotId");
                double diff = numberOrZero(movement.get("quantityDiff"));
                double balance = numberOrZero(balances.get(movementLot)) + diff;
                balances.put(movementLot, Double.valueOf(balance));
                if (lotId != null && !lotId.equals(movementLot)) {
                    continue;
                }
                if (movementTypeId != null && !movementTypeId.equals(movement.getString("movementTypeId"))) {
                    continue;
                }
                GenericValue lot = lots.get(movementLot);
                if (componentCode != null
                        && (lot == null || !componentCode.equals(lot.getString("componentCode")))) {
                    continue;
                }
                if (lot != null && !matchesLotKind(lot, kind)) {
                    continue;
                }
                Timestamp effective = movement.getTimestamp("effectiveDate");
                if (effective != null) {
                    java.sql.Date day = new java.sql.Date(effective.getTime());
                    if (fromDate != null && day.before(fromDate)) {
                        continue;
                    }
                    if (toDate != null && day.after(toDate)) {
                        continue;
                    }
                }
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("lotId", movementLot);
                row.put("seqId", movement.get("seqId"));
                row.put("lotKindId", lot == null ? LOT_COMPONENT : lotKindOf(lot));
                row.put("componentCode", lot == null ? null : lot.getString("componentCode"));
                row.put("componentName", lot == null ? null : lot.getString("componentName"));
                row.put("movementTypeId", movement.getString("movementTypeId"));
                row.put("quantityDiff", Double.valueOf(round2(diff)));
                row.put("reservedDiff", Double.valueOf(round2(numberOrZero(movement.get("reservedDiff")))));
                row.put("displayQty", Double.valueOf(round2(
                        MOVE_RESERVE.equals(movement.getString("movementTypeId"))
                                || MOVE_RELEASE.equals(movement.getString("movementTypeId"))
                                ? numberOrZero(movement.get("reservedDiff")) : diff)));
                row.put("balanceQty", Double.valueOf(round2(balance)));
                row.put("unitCost", movement.get("unitCost"));
                row.put("workOrderId", movement.getString("workOrderId"));
                row.put("physicalInventoryId", movement.getString("physicalInventoryId"));
                row.put("referenceNo", movement.getString("referenceNo"));
                row.put("operatorName", movement.getString("operatorName"));
                row.put("effectiveDate", effective);
                row.put("note", movement.getString("note"));
                rows.add(row);
            }
            Collections.reverse(rows);
            putPage(result, "rows", rows, pageOf(context), pageSizeOf(context), null);
            result.put("lotId", lotId);
            result.put("componentCode", componentCode);
            result.put("movementTypeId", movementTypeId);
            result.put("kind", kind);
            result.put("fromDate", fromDate);
            result.put("toDate", toDate);
            return result;
        } catch (GenericEntityException e) {
            return ServiceUtil.returnError("Could not read the ledger: " + e.getMessage());
        }
    }

    /**
     * Writes the component master record with its safety stock.
     *
     * @param dctx the dispatch context
     * @param context service context, see {@code servicedef/services.xml#mfgStockSettingSave}
     * @return the service result containing {@code componentCode}
     */
    public static Map<String, Object> stockSettingSave(DispatchContext dctx, Map<String, ? extends Object> context) {
        Delegator delegator = dctx.getDelegator();
        String componentCode = toText(context.get("componentCode"));
        if (componentCode == null) {
            return ServiceUtil.returnError("componentCode is required");
        }
        try {
            GenericValue setting = EntityQuery.use(delegator).from("MfgStockSetting")
                    .where("componentCode", componentCode).queryOne();
            if (setting == null) {
                setting = delegator.makeValue("MfgStockSetting");
                setting.set("componentCode", componentCode);
            }
            if (context.containsKey("componentName")) {
                setting.set("componentName", toText(context.get("componentName")));
            }
            if (context.containsKey("unitOfMeasure")) {
                setting.set("unitOfMeasure", toText(context.get("unitOfMeasure")));
            }
            if (context.containsKey("minimumStock")) {
                setting.set("minimumStock", Double.valueOf(round2(toDouble(context.get("minimumStock"), 0.0))));
            }
            if (context.containsKey("reorderQty")) {
                setting.set("reorderQty", Double.valueOf(round2(toDouble(context.get("reorderQty"), 0.0))));
            }
            if (context.containsKey("supplierName")) {
                setting.set("supplierName", toText(context.get("supplierName")));
            }
            if (context.containsKey("note")) {
                setting.set("note", toText(context.get("note")));
            }
            delegator.createOrStore(setting);

            Map<String, Object> result = ServiceUtil.returnSuccess();
            result.put("componentCode", componentCode);
            result.put("minimumStock", setting.get("minimumStock"));
            result.put("reorderQty", setting.get("reorderQty"));
            return result;
        } catch (GenericEntityException e) {
            return ServiceUtil.returnError("Could not save the stock setting: " + e.getMessage());
        }
    }

    /**
     * Books a physical count of one lot: the difference between what was counted and what the
     * ledger says becomes a movement, never a silent edit of the quantity.
     *
     * @param dctx the dispatch context
     * @param context service context, see {@code servicedef/services.xml#mfgStockCount}
     * @return the service result containing the count number and the variance
     */
    public static Map<String, Object> stockCount(DispatchContext dctx, Map<String, ? extends Object> context) {
        Delegator delegator = dctx.getDelegator();
        String lotId = toText(context.get("lotId"));
        Object countedInput = context.get("countedQty");
        if (lotId == null) {
            return ServiceUtil.returnError("lotId is required");
        }
        if (toText(countedInput) == null) {
            return ServiceUtil.returnError("countedQty is required");
        }
        double countedQty = toDouble(countedInput, -1.0);
        if (countedQty < 0.0) {
            return ServiceUtil.returnError("countedQty must not be negative");
        }
        try {
            GenericValue lot = EntityQuery.use(delegator).from("MfgMaterialLot")
                    .where("lotId", lotId).queryOne();
            if (lot == null) {
                return ServiceUtil.returnError("Unknown material lot [" + lotId + "]");
            }
            ensureLotLedger(delegator, lot);
            double bookQty = numberOrZero(lot.get("onHandQty"));
            double variance = round2(countedQty - bookQty);
            String physicalInventoryId = nextStockCountId(delegator, LocalDate.now());
            String counterName = toText(context.get("counterName"));
            GenericValue count = delegator.makeValue("MfgStockCount");
            count.set("physicalInventoryId", physicalInventoryId);
            Object countDate = parseDate(context.get("countDate"));
            count.set("countDate", countDate == null
                    ? java.sql.Date.valueOf(LocalDate.now()) : countDate);
            count.set("counterName", counterName);
            count.set("lotId", lotId);
            count.set("bookQty", Double.valueOf(bookQty));
            count.set("countedQty", Double.valueOf(countedQty));
            count.set("varianceQty", Double.valueOf(variance));
            count.set("varianceReasonId", MOVE_COUNT);
            count.set("note", toText(context.get("note")));
            delegator.create(count);
            if (Math.abs(variance) > 0.0001) {
                recordMovement(delegator, lot, MOVE_COUNT, variance, lot.get("unitPrice"), null,
                        physicalInventoryId, null, counterName, null,
                        "盘点 " + physicalInventoryId + "：" + formatQty(bookQty) + " → " + formatQty(countedQty));
            }
            recalcLotStock(delegator, lot);

            Map<String, Object> result = ServiceUtil.returnSuccess();
            result.put("physicalInventoryId", physicalInventoryId);
            result.put("lotId", lotId);
            result.put("bookQty", Double.valueOf(bookQty));
            result.put("countedQty", Double.valueOf(countedQty));
            result.put("varianceQty", Double.valueOf(variance));
            result.put("onHandQty", lot.get("onHandQty"));
            return result;
        } catch (GenericEntityException e) {
            return ServiceUtil.returnError("Could not book the count: " + e.getMessage());
        }
    }

    /**
     * The counts that were booked, newest first.
     *
     * @param dctx the dispatch context
     * @param context service context, see {@code servicedef/services.xml#mfgStockCountList}
     * @return the service result containing {@code counts}
     */
    public static Map<String, Object> stockCountList(DispatchContext dctx, Map<String, ? extends Object> context) {
        Delegator delegator = dctx.getDelegator();
        String lotId = toText(context.get("lotId"));
        List<Map<String, Object>> rows = new ArrayList<>();
        Map<String, Object> result = ServiceUtil.returnSuccess();
        result.put("counts", rows);
        try {
            Map<String, GenericValue> lots = new LinkedHashMap<>();
            for (GenericValue lot : EntityQuery.use(delegator).from("MfgMaterialLot").queryList()) {
                lots.put(lot.getString("lotId"), lot);
            }
            EntityQuery query = EntityQuery.use(delegator).from("MfgStockCount");
            if (lotId != null) {
                query = query.where("lotId", lotId);
            }
            int shown = 0;
            for (GenericValue count : query.orderBy("-createdStamp").queryList()) {
                if (shown >= 50) {
                    break;
                }
                shown++;
                GenericValue lot = lots.get(count.getString("lotId"));
                Map<String, Object> map = new LinkedHashMap<>();
                map.put("physicalInventoryId", count.getString("physicalInventoryId"));
                map.put("countDate", count.get("countDate"));
                map.put("counterName", count.getString("counterName"));
                map.put("lotId", count.getString("lotId"));
                map.put("lotKindId", lot == null ? LOT_COMPONENT : lotKindOf(lot));
                map.put("componentCode", lot == null ? null : lot.getString("componentCode"));
                map.put("componentName", lot == null ? null : lot.getString("componentName"));
                map.put("bookQty", count.get("bookQty"));
                map.put("countedQty", count.get("countedQty"));
                map.put("varianceQty", count.get("varianceQty"));
                map.put("note", count.getString("note"));
                rows.add(map);
            }
            return result;
        } catch (GenericEntityException e) {
            return ServiceUtil.returnError("Could not read the counts: " + e.getMessage());
        }
    }

    /**
     * Next stock count number, CNT-yyMMdd-NN.
     *
     * @param delegator the delegator
     * @param today the day
     * @return the identifier
     * @throws GenericEntityException when the counts cannot be read
     */
    private static String nextStockCountId(Delegator delegator, LocalDate today)
            throws GenericEntityException {
        String prefix = "CNT-" + today.format(DAY) + "-";
        int max = 0;
        for (GenericValue count : EntityQuery.use(delegator).from("MfgStockCount").queryList()) {
            String id = count.getString("physicalInventoryId");
            if (id != null && id.startsWith(prefix)) {
                max = Math.max(max, parseTrailingNumber(id.substring(prefix.length())));
            }
        }
        return prefix + String.format(Locale.ROOT, "%02d", Integer.valueOf(max + 1));
    }

    /**
     * Adds one alert per component whose stock dropped below its safety stock.
     *
     * @param alerts the alert list to append to
     * @param delegator the delegator
     * @throws GenericEntityException when the stock cannot be read
     */
    private static void addStockAlerts(List<Map<String, Object>> alerts, Delegator delegator)
            throws GenericEntityException {
        Map<String, GenericValue> settings = new LinkedHashMap<>();
        for (GenericValue setting : EntityQuery.use(delegator).from("MfgStockSetting").queryList()) {
            settings.put(setting.getString("componentCode"), setting);
        }
        if (settings.isEmpty()) {
            return;
        }
        Map<String, Double> onHand = new LinkedHashMap<>();
        for (GenericValue lot : componentLots(delegator)) {
            ensureLotLedger(delegator, lot);
            String componentCode = lot.getString("componentCode");
            if (componentCode == null) {
                continue;
            }
            onHand.put(componentCode, Double.valueOf(numberOrZero(onHand.get(componentCode))
                    + numberOrZero(lot.get("onHandQty"))));
        }
        for (Map.Entry<String, GenericValue> entry : settings.entrySet()) {
            double minimum = numberOrZero(entry.getValue().get("minimumStock"));
            if (minimum <= 0.0) {
                continue;
            }
            double current = numberOrZero(onHand.get(entry.getKey()));
            if (current >= minimum) {
                continue;
            }
            double reorder = numberOrZero(entry.getValue().get("reorderQty"));
            double suggested = reorder > 0.0 ? reorder : round2(minimum - current);
            Map<String, Object> alert = new LinkedHashMap<>();
            alert.put("typeId", ALERT_STOCK);
            alert.put("level", current <= 0.0 ? ALERT_ERROR : ALERT_WARN);
            alert.put("refId", entry.getKey());
            alert.put("subject", entry.getValue().getString("componentName"));
            alert.put("dueDate", null);
            alert.put("hoursLeft", Long.valueOf(0L));
            alert.put("detail", "现有 " + formatQty(current) + " / 安全库存 " + formatQty(minimum)
                    + "，建议采购 " + formatQty(suggested)
                    + (entry.getValue().getString("supplierName") == null ? ""
                            : "（" + entry.getValue().getString("supplierName") + "）"));
            alerts.add(alert);
        }
    }

    // ==================== software projects: estimate, ledger, statistics ====================

    /**
     * The software cost model of the assessment standard: size, effort, schedule and price.
     *
     * <p>{@code UFP = 35 * ILF + 15 * EIF} (estimated function points), {@code AE = PDR * UFP * SWF * RDF}
     * in hours, {@code D = 1.277 * (AE / 176)^0.404} in months and the price is the person days times
     * the day rate, plus VAT and the direct non labour cost.
     *
     * <p>Pure, so the arithmetic can be tested without a database and the quote screen and the ledger
     * always tell the same numbers.
     *
     * @param ilf internal logical files
     * @param eif external interface files
     * @param ufp function points when they are known, otherwise {@code null} and ILF / EIF are used
     * @param swfBase software factor starting value
     * @param swfReuse adjustment for reuse and experience (usually negative)
     * @param swfPreInvest adjustment for design work already paid for
     * @param swfQuality adjustment for a certified quality system
     * @param rdf development factor, 1 when nothing special is required
     * @param pdr hours per function point
     * @param dayRate price of one person day before tax
     * @param taxRate VAT rate
     * @param dncRate direct non labour cost as a share of the taxed amount
     * @return the computed values, keyed by {@code ufp}, {@code swf}, {@code effortHours},
     *         {@code effortDays}, {@code scheduleMonths}, {@code amountBeforeTax}, {@code taxAmount},
     *         {@code amountWithTax}, {@code dncAmount}, {@code totalAmount} and {@code costPerFp}
     */
    static Map<String, Object> softwareCost(double ilf, double eif, Double ufp, double swfBase,
            double swfReuse, double swfPreInvest, double swfQuality, double rdf, double pdr,
            double dayRate, double taxRate, double dncRate) {
        double size = ufp == null ? 35.0 * ilf + 15.0 * eif : ufp.doubleValue();
        double swf = swfBase + swfReuse + swfPreInvest + swfQuality;
        double effort = pdr * size * swf * rdf;
        double days = effort / HOURS_PER_DAY;
        double months = effort <= 0.0 ? 0.0
                : SCHEDULE_FACTOR * Math.pow(effort / HOURS_PER_MONTH, SCHEDULE_EXPONENT);
        double beforeTax = days * dayRate;
        double tax = beforeTax * taxRate;
        double withTax = beforeTax + tax;
        double dnc = withTax * dncRate;

        Map<String, Object> map = new LinkedHashMap<>();
        map.put("ufp", Double.valueOf(round2(size)));
        map.put("swf", Double.valueOf(round2(swf)));
        map.put("effortHours", Double.valueOf(round2(effort)));
        map.put("effortDays", Double.valueOf(round2(days)));
        map.put("scheduleMonths", Double.valueOf(round2(months)));
        map.put("amountBeforeTax", Double.valueOf(round2(beforeTax)));
        map.put("taxAmount", Double.valueOf(round2(tax)));
        map.put("amountWithTax", Double.valueOf(round2(withTax)));
        map.put("dncAmount", Double.valueOf(round2(dnc)));
        map.put("totalAmount", Double.valueOf(round2(withTax + dnc)));
        map.put("costPerFp", Double.valueOf(round2(size <= 0.0 ? 0.0 : withTax / size)));
        return map;
    }

    /**
     * Quotes a piece of software from its size: the four steps of the standard in one call, with the
     * parameters of the 2019 assessment case as defaults.
     *
     * @param dctx the dispatch context
     * @param context service context, see {@code servicedef/services.xml#mfgSoftwareEstimate}
     * @return the service result with the size, effort, schedule and prices
     */
    public static Map<String, Object> softwareEstimate(DispatchContext dctx, Map<String, ? extends Object> context) {
        Map<String, Object> result = ServiceUtil.returnSuccess();
        result.putAll(softwareCost(
                toDouble(context.get("ilfCount"), 0.0),
                toDouble(context.get("eifCount"), 0.0),
                optionalAmount(context.get("ufp")),
                toDouble(context.get("swfBase"), 1.5),
                toDouble(context.get("swfReuse"), 0.0),
                toDouble(context.get("swfPreInvest"), 0.0),
                toDouble(context.get("swfQuality"), 0.0),
                toDouble(context.get("rdf"), 1.0),
                toDouble(context.get("pdrHoursPerFp"), DEFAULT_PDR),
                toDouble(context.get("dayRate"), DEFAULT_DAY_RATE),
                toDouble(context.get("taxRate"), DEFAULT_TAX_RATE),
                toDouble(context.get("dncRate"), DEFAULT_DNC_RATE)));
        return result;
    }

    /**
     * Creates or updates one software project of the ledger and freezes the planned person days the
     * estimate gives, so a later change of the parameters does not rewrite history.
     *
     * @param dctx the dispatch context
     * @param context service context, see {@code servicedef/services.xml#mfgSoftwareProjectSave}
     * @return the service result containing {@code projectCode} and the estimate values
     */
    public static Map<String, Object> softwareProjectSave(DispatchContext dctx, Map<String, ? extends Object> context) {
        Delegator delegator = dctx.getDelegator();
        String projectCode = toText(context.get("projectCode"));
        String projectName = toText(context.get("projectName"));
        if (projectName == null) {
            return ServiceUtil.returnError("projectName is required");
        }
        try {
            LocalDate today = LocalDate.now();
            GenericValue project = null;
            if (projectCode != null) {
                project = EntityQuery.use(delegator).from("MfgSoftwareProject")
                        .where("projectCode", projectCode).queryOne();
            }
            if (project == null) {
                if (projectCode == null) {
                    projectCode = nextSoftwareProjectId(delegator, today);
                }
                project = delegator.makeValue("MfgSoftwareProject");
                project.set("projectCode", projectCode);
                project.set("statusId", SW_PLANNED);
            }
            project.set("projectName", projectName);
            project.set("categoryId", toText(context.get("categoryId")));
            project.set("productCode", toText(context.get("productCode")));
            project.set("devProjectCode", toText(context.get("devProjectCode")));
            project.set("ownerName", toText(context.get("ownerName")));
            String statusId = toText(context.get("statusId"));
            if (statusId != null) {
                project.set("statusId", statusId);
            }
            project.set("deliverySeq", optionalAmount(context.get("deliverySeq")));
            project.set("ilfCount", optionalAmount(context.get("ilfCount")));
            project.set("eifCount", optionalAmount(context.get("eifCount")));
            project.set("ufp", optionalAmount(context.get("ufp")));
            project.set("swfBase", optionalAmount(context.get("swfBase")));
            project.set("swfReuse", optionalAmount(context.get("swfReuse")));
            project.set("swfPreInvest", optionalAmount(context.get("swfPreInvest")));
            project.set("swfQuality", optionalAmount(context.get("swfQuality")));
            project.set("rdf", optionalAmount(context.get("rdf")));
            project.set("pdrHoursPerFp", optionalAmount(context.get("pdrHoursPerFp")));
            project.set("dayRate", optionalAmount(context.get("dayRate")));
            project.set("taxRate", optionalAmount(context.get("taxRate")));
            project.set("dncRate", optionalAmount(context.get("dncRate")));
            project.set("plannedDays", optionalAmount(context.get("plannedDays")));
            project.set("plannedStartDate", parseDate(context.get("plannedStartDate")));
            project.set("plannedEndDate", parseDate(context.get("plannedEndDate")));
            project.set("actualHours", optionalAmount(context.get("actualHours")));
            project.set("actualCost", optionalAmount(context.get("actualCost")));
            project.set("quotedAmount", optionalAmount(context.get("quotedAmount")));
            project.set("note", toText(context.get("note")));

            fillSoftwareDefaults(project);
            Map<String, Object> cost = softwareCostOf(project);
            if (project.get("plannedDays") == null) {
                project.set("plannedDays", cost.get("effortDays"));
            }
            delegator.createOrStore(project);

            Map<String, Object> result = ServiceUtil.returnSuccess();
            result.put("projectCode", projectCode);
            result.putAll(cost);
            result.put("plannedDays", project.get("plannedDays"));
            return result;
        } catch (GenericEntityException e) {
            return ServiceUtil.returnError("Could not save the software project: " + e.getMessage());
        }
    }

    /**
     * Lists the software projects with the numbers needed to plan the next one: what was planned,
     * what it really took, and the productivity the finished projects show.
     *
     * @param dctx the dispatch context
     * @param context service context, see {@code servicedef/services.xml#mfgSoftwareProjectList}
     * @return the service result containing {@code projects} and the summary figures
     */
    public static Map<String, Object> softwareProjectList(DispatchContext dctx, Map<String, ? extends Object> context) {
        Delegator delegator = dctx.getDelegator();
        try {
            List<GenericValue> projects = EntityQuery.use(delegator).from("MfgSoftwareProject")
                    .orderBy("-createdStamp").queryList();
            List<Map<String, Object>> rows = new ArrayList<>(projects.size());
            List<Double> pdrSamples = new ArrayList<>();
            double plannedTotal = 0.0;
            double actualTotal = 0.0;
            int openCount = 0;
            int overCount = 0;
            int finishedCount = 0;
            for (GenericValue project : projects) {
                Map<String, Object> row = toSoftwareProjectMap(project);
                rows.add(row);
                String statusId = project.getString("statusId");
                boolean open = !SW_DONE.equals(statusId) && !SW_CANCELLED.equals(statusId);
                if (open) {
                    openCount++;
                }
                double planned = numberOrZero(row.get("plannedDays"));
                double actualDays = numberOrZero(row.get("actualDays"));
                if (planned > 0.0 && actualDays > planned) {
                    overCount++;
                }
                if (SW_DONE.equals(statusId)) {
                    finishedCount++;
                    plannedTotal += planned;
                    actualTotal += actualDays;
                    Double sample = (Double) row.get("pdrActual");
                    if (sample != null) {
                        pdrSamples.add(sample);
                    }
                }
            }
            Map<String, Object> result = ServiceUtil.returnSuccess();
            putPage(result, "projects",
                    filterByKeyword(rows, toText(context.get("search")), "projectCode", "projectName",
                            "categoryId", "ownerName", "productCode"),
                    pageOf(context), pageSizeOf(context), toText(context.get("search")));
            result.put("projectCount", Integer.valueOf(rows.size()));
            result.put("openCount", Integer.valueOf(openCount));
            result.put("overCount", Integer.valueOf(overCount));
            result.put("finishedCount", Integer.valueOf(finishedCount));
            result.put("plannedDaysTotal", Double.valueOf(round2(plannedTotal)));
            result.put("actualDaysTotal", Double.valueOf(round2(actualTotal)));
            result.put("pdrSampleCount", Integer.valueOf(pdrSamples.size()));
            result.put("pdrMedian", Double.valueOf(round2(median(pdrSamples))));
            result.put("defaultPdr", Double.valueOf(DEFAULT_PDR));
            result.put("defaultDayRate", Double.valueOf(DEFAULT_DAY_RATE));
            result.put("defaultTaxRate", Double.valueOf(DEFAULT_TAX_RATE));
            result.put("defaultDncRate", Double.valueOf(DEFAULT_DNC_RATE));
            return result;
        } catch (GenericEntityException e) {
            return ServiceUtil.returnError("Could not read the software projects: " + e.getMessage());
        }
    }

    /**
     * Fills the parameters a software project did not set with the defaults of the cost model.
     *
     * @param project the project to fill in
     */
    private static void fillSoftwareDefaults(GenericValue project) {
        setDefault(project, "swfBase", 1.5);
        setDefault(project, "swfReuse", 0.0);
        setDefault(project, "swfPreInvest", 0.0);
        setDefault(project, "swfQuality", 0.0);
        setDefault(project, "rdf", 1.0);
        setDefault(project, "pdrHoursPerFp", DEFAULT_PDR);
        setDefault(project, "dayRate", DEFAULT_DAY_RATE);
        setDefault(project, "taxRate", DEFAULT_TAX_RATE);
        setDefault(project, "dncRate", DEFAULT_DNC_RATE);
        setDefault(project, "ilfCount", 0.0);
        setDefault(project, "eifCount", 0.0);
    }

    private static void setDefault(GenericValue value, String field, double fallback) {
        if (value.get(field) == null) {
            value.set(field, Double.valueOf(fallback));
        }
    }

    private static Map<String, Object> softwareCostOf(GenericValue project) {
        fillSoftwareDefaults(project);
        return softwareCost(
                numberOrZero(project.get("ilfCount")),
                numberOrZero(project.get("eifCount")),
                project.get("ufp") instanceof Number
                        ? Double.valueOf(((Number) project.get("ufp")).doubleValue()) : null,
                numberOrZero(project.get("swfBase")),
                numberOrZero(project.get("swfReuse")),
                numberOrZero(project.get("swfPreInvest")),
                numberOrZero(project.get("swfQuality")),
                numberOrZero(project.get("rdf")),
                numberOrZero(project.get("pdrHoursPerFp")),
                numberOrZero(project.get("dayRate")),
                numberOrZero(project.get("taxRate")),
                numberOrZero(project.get("dncRate")));
    }

    private static Map<String, Object> toSoftwareProjectMap(GenericValue project) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("projectCode", project.getString("projectCode"));
        map.put("projectName", project.getString("projectName"));
        map.put("categoryId", project.getString("categoryId"));
        map.put("productCode", project.getString("productCode"));
        map.put("devProjectCode", project.getString("devProjectCode"));
        map.put("ownerName", project.getString("ownerName"));
        map.put("statusId", project.getString("statusId"));
        map.put("deliverySeq", project.get("deliverySeq"));
        map.put("ilfCount", project.get("ilfCount"));
        map.put("eifCount", project.get("eifCount"));
        Map<String, Object> cost = softwareCostOf(project);
        map.putAll(cost);
        map.put("pdrHoursPerFp", project.get("pdrHoursPerFp"));
        map.put("dayRate", project.get("dayRate"));
        map.put("plannedDays", project.get("plannedDays"));
        map.put("plannedStartDate", project.getDate("plannedStartDate"));
        map.put("plannedEndDate", project.getDate("plannedEndDate"));
        map.put("actualHours", project.get("actualHours"));
        double actualDays = numberOrZero(project.get("actualHours")) / HOURS_PER_DAY;
        map.put("actualDays", Double.valueOf(round2(actualDays)));
        map.put("actualCost", project.get("actualCost"));
        map.put("quotedAmount", project.get("quotedAmount"));
        double planned = numberOrZero(project.get("plannedDays"));
        map.put("deviationRate", planned <= 0.0 ? null
                : Double.valueOf(round2((actualDays - planned) / planned * 100.0)));
        double size = numberOrZero(cost.get("ufp"));
        double hours = numberOrZero(project.get("actualHours"));
        map.put("pdrActual", size > 0.0 && hours > 0.0
                ? Double.valueOf(round2(hours / size)) : null);
        java.sql.Date end = project.getDate("plannedEndDate");
        map.put("daysToEnd", end == null ? null
                : Long.valueOf(java.time.temporal.ChronoUnit.DAYS.between(LocalDate.now(), end.toLocalDate())));
        map.put("note", project.getString("note"));
        return map;
    }

    /**
     * Median of the samples, used as the productivity baseline of the finished projects.
     *
     * @param values the samples
     * @return the median, {@code 0} when there is no sample
     */
    static double median(List<Double> values) {
        if (values == null || values.isEmpty()) {
            return 0.0;
        }
        List<Double> sorted = new ArrayList<>(values);
        java.util.Collections.sort(sorted);
        int size = sorted.size();
        if (size % 2 == 1) {
            return sorted.get(size / 2).doubleValue();
        }
        return (sorted.get(size / 2 - 1).doubleValue() + sorted.get(size / 2).doubleValue()) / 2.0;
    }

    private static String nextSoftwareProjectId(Delegator delegator, LocalDate today)
            throws GenericEntityException {
        String prefix = "SW-" + today.format(DAY) + "-";
        int max = 0;
        for (GenericValue project : EntityQuery.use(delegator).from("MfgSoftwareProject").queryList()) {
            String id = project.getString("projectCode");
            if (id != null && id.startsWith(prefix)) {
                max = Math.max(max, parseTrailingNumber(id.substring(prefix.length())));
            }
        }
        return "SW-" + today.format(DAY) + "-" + String.format(Locale.ROOT, "%02d", max + 1);
    }

    /**
     * Labels of the last {@code days} days, oldest first, as used on the dashboard axis.
     *
     * @param end the last day
     * @param days how many days to list
     * @return the labels in {@code MM-dd} form
     */
    static List<String> lastDayLabels(LocalDate end, int days) {
        List<String> labels = new ArrayList<>();
        if (end == null || days <= 0) {
            return labels;
        }
        for (int i = days - 1; i >= 0; i--) {
            labels.add(end.minusDays(i).format(MONTH_DAY));
        }
        return labels;
    }

    /**
     * Scales a value against the largest one so a bar can be drawn without any chart library.
     *
     * @param value the value
     * @param max the largest value of the series
     * @return the percentage of the largest value, 0 for zero and at least 2 for a non zero value
     */
    static long percentOfMax(long value, long max) {
        if (value <= 0) {
            return 0;
        }
        if (max <= 0) {
            return 100;
        }
        long pct = Math.round(value * 100.0 / max);
        return Math.max(2, pct);
    }

    /**
     * Builds the after sales quality report: return, exchange and repair rates, the money they
     * cost, and the breakdowns that tell where the money goes.
     *
     * @param dctx the dispatch context
     * @param context service context, see {@code servicedef/services.xml#mfgQualityReport}
     * @return the service result with the rates, the totals and the breakdown lists
     */
    public static Map<String, Object> qualityReport(DispatchContext dctx, Map<String, ? extends Object> context) {
        Delegator delegator = dctx.getDelegator();
        java.sql.Date toDate = (java.sql.Date) parseDate(context.get("toDate"));
        if (toDate == null) {
            toDate = java.sql.Date.valueOf(LocalDate.now());
        }
        java.sql.Date fromDate = (java.sql.Date) parseDate(context.get("fromDate"));
        if (fromDate == null) {
            fromDate = java.sql.Date.valueOf(toDate.toLocalDate().minusDays(89));
        }
        try {
            Map<String, GenericValue> products = new LinkedHashMap<>();
            for (GenericValue product : EntityQuery.use(delegator).from("MfgProduct").queryList()) {
                products.put(product.getString("productCode"), product);
            }
            Map<String, String> snToProduct = new LinkedHashMap<>();
            for (GenericValue unit : EntityQuery.use(delegator).from("MfgSn").queryList()) {
                snToProduct.put(unit.getString("sn"), unit.getString("productCode"));
            }

            // units actually sold in the period: replacement deliveries are not a sale
            Map<String, Map<String, Object>> byProduct = new LinkedHashMap<>();
            long shippedUnits = 0;
            long deliveries = 0;
            double salesTotal = 0.0;
            for (GenericValue delivery : EntityQuery.use(delegator).from("MfgDelivery").queryList()) {
                java.sql.Date deliveryDate = delivery.getDate("deliveryDate");
                if (deliveryDate == null || deliveryDate.before(fromDate) || deliveryDate.after(toDate)) {
                    continue;
                }
                String note = delivery.getString("note");
                if (note != null && note.startsWith("换货")) {
                    continue;
                }
                List<GenericValue> items = EntityQuery.use(delegator).from("MfgDeliverySn")
                        .where("deliveryId", delivery.getString("deliveryId")).queryList();
                boolean counted = false;
                for (GenericValue item : items) {
                    String productCode = snToProduct.get(item.getString("sn"));
                    if (productCode == null) {
                        continue;
                    }
                    Map<String, Object> row = productRow(byProduct, productCode);
                    row.put("shippedUnits", Long.valueOf(((Long) row.get("shippedUnits")).longValue() + 1));
                    shippedUnits++;
                    counted = true;
                }
                if (counted) {
                    deliveries++;
                }
            }
            for (Map.Entry<String, Map<String, Object>> entry : byProduct.entrySet()) {
                GenericValue product = products.get(entry.getKey());
                double price = product == null || product.getDouble("salePrice") == null
                        ? 0.0 : product.getDouble("salePrice").doubleValue();
                double rowSales = ((Long) entry.getValue().get("shippedUnits")).longValue() * price;
                entry.getValue().put("salePrice", Double.valueOf(price));
                entry.getValue().put("sales", Double.valueOf(rowSales));
                salesTotal += rowSales;
            }

            long claimCount = 0;
            long rtnCount = 0;
            long exchCount = 0;
            long repairCount = 0;
            long refundOnlyCount = 0;
            long claimsWithin30 = 0;
            double refundTotal = 0.0;
            double freightTotal = 0.0;
            double hoursTotal = 0.0;
            double costTotal = 0.0;
            Map<String, Map<String, Object>> byResponsibility = new LinkedHashMap<>();
            Map<String, Long> byAge = new LinkedHashMap<>();
            Map<String, Long> byInspect = new LinkedHashMap<>();
            Map<String, Long> claimsPerSn = new LinkedHashMap<>();
            Map<String, Long> claimsPerLot = new LinkedHashMap<>();
            Map<String, GenericValue> lots = new LinkedHashMap<>();
            Map<String, List<String>> lotsOfWorkOrder = new LinkedHashMap<>();

            for (GenericValue claim : EntityQuery.use(delegator).from("MfgWarrantyClaim").queryList()) {
                java.sql.Date receivedDate = claim.getDate("receivedDate");
                if (receivedDate == null || receivedDate.before(fromDate) || receivedDate.after(toDate)) {
                    continue;
                }
                claimCount++;
                String claimType = claim.getString("claimType");
                if (CLAIM_EXCH.equals(claimType)) {
                    exchCount++;
                } else if (CLAIM_REPAIR.equals(claimType)) {
                    repairCount++;
                } else if (CLAIM_REFUND_ONLY.equals(claimType)) {
                    refundOnlyCount++;
                } else {
                    rtnCount++;
                }

                double refund = claim.getDouble("refundAmount") == null
                        ? 0.0 : claim.getDouble("refundAmount").doubleValue();
                double hours = claim.getDouble("repairHours") == null
                        ? 0.0 : claim.getDouble("repairHours").doubleValue();
                double freight = FREIGHT_SELLER.equals(claim.getString("freightPayer"))
                        ? QUALITY_FREIGHT_COST : 0.0;
                double cost = claimCost(refund, freight, hours, QUALITY_HOURLY_RATE);
                refundTotal += refund;
                freightTotal += freight;
                hoursTotal += hours;
                costTotal += cost;

                int days = daysBetween(claim.getDate("deliveryDate"), receivedDate);
                if (days >= 0 && days <= 30) {
                    claimsWithin30++;
                }

                String sn = claim.getString("sn");
                String productCode = snToProduct.get(sn);
                if (productCode != null) {
                    Map<String, Object> row = productRow(byProduct, productCode);
                    row.put("claimCount", Long.valueOf(((Long) row.get("claimCount")).longValue() + 1));
                    row.put("refundTotal", Double.valueOf(sumOf(row, "refundTotal") + refund));
                    row.put("repairHours", Double.valueOf(sumOf(row, "repairHours") + hours));
                    row.put("cost", Double.valueOf(sumOf(row, "cost") + cost));
                }

                String responsibility = claim.getString("responsibility");
                Map<String, Object> respRow = productRow(byResponsibility,
                        responsibility == null ? "-" : responsibility);
                respRow.put("claimCount", Long.valueOf(((Long) respRow.get("claimCount")).longValue() + 1));
                respRow.put("refundTotal", Double.valueOf(sumOf(respRow, "refundTotal") + refund));
                respRow.put("repairHours", Double.valueOf(sumOf(respRow, "repairHours") + hours));
                respRow.put("cost", Double.valueOf(sumOf(respRow, "cost") + cost));

                bump(byAge, ageBucket(days));
                bump(byInspect, claim.getString("inspectResult") == null
                        ? "UNKNOWN" : claim.getString("inspectResult"));
                bump(claimsPerSn, sn);
                for (String lotId : lotsOf(delegator, claim.getString("workOrderId"), lotsOfWorkOrder)) {
                    bump(claimsPerLot, lotId);
                    if (!lots.containsKey(lotId)) {
                        GenericValue lot = EntityQuery.use(delegator).from("MfgMaterialLot")
                                .where("lotId", lotId).queryOne();
                        if (lot != null) {
                            lots.put(lotId, lot);
                        }
                    }
                }
            }

            List<Map<String, Object>> productRows = new ArrayList<>();
            for (Map<String, Object> row : byProduct.values()) {
                long shipped = ((Long) row.get("shippedUnits")).longValue();
                long claims = ((Long) row.get("claimCount")).longValue();
                row.put("claimRate", percent(claims, shipped));
                row.put("costRate", percentOf(sumOf(row, "cost"), sumOf(row, "sales")));
                productRows.add(row);
            }
            productRows.sort(new Comparator<Map<String, Object>>() {
                @Override
                public int compare(Map<String, Object> left, Map<String, Object> right) {
                    return ((Long) right.get("claimCount")).compareTo((Long) left.get("claimCount"));
                }
            });

            List<Map<String, Object>> responsibilityRows = new ArrayList<>(byResponsibility.values());
            responsibilityRows.sort(new Comparator<Map<String, Object>>() {
                @Override
                public int compare(Map<String, Object> left, Map<String, Object> right) {
                    return ((Long) right.get("claimCount")).compareTo((Long) left.get("claimCount"));
                }
            });

            double averageClaimsPerLot = claimsPerLot.isEmpty() ? 0.0
                    : (double) claimCount / (double) claimsPerLot.size();
            List<Map<String, Object>> lotRows = new ArrayList<>();
            for (Map.Entry<String, Long> entry : claimsPerLot.entrySet()) {
                GenericValue lot = lots.get(entry.getKey());
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("lotId", entry.getKey());
                row.put("componentCode", lot == null ? null : lot.getString("componentCode"));
                row.put("supplierName", lot == null ? null : lot.getString("supplierName"));
                row.put("supplierLot", lot == null ? null : lot.getString("supplierLot"));
                row.put("claimCount", entry.getValue());
                row.put("average", Double.valueOf(averageClaimsPerLot));
                row.put("suspicious", entry.getValue().longValue() >= QUALITY_LOT_FACTOR * averageClaimsPerLot
                        && entry.getValue().longValue() > 1 ? "Y" : "N");
                lotRows.add(row);
            }
            lotRows.sort(new Comparator<Map<String, Object>>() {
                @Override
                public int compare(Map<String, Object> left, Map<String, Object> right) {
                    return ((Long) right.get("claimCount")).compareTo((Long) left.get("claimCount"));
                }
            });

            long repeatedSnCount = 0;
            for (Long times : claimsPerSn.values()) {
                if (times.longValue() > 1) {
                    repeatedSnCount++;
                }
            }

            List<Map<String, Object>> ageRows = new ArrayList<>();
            for (String bucket : new String[] {"0-30", "31-90", "91-180", ">180", "未知"}) {
                Long value = byAge.get(bucket);
                if (value == null || value.longValue() == 0) {
                    continue;
                }
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("bucket", bucket);
                row.put("claimCount", value);
                ageRows.add(row);
            }
            List<Map<String, Object>> inspectRows = new ArrayList<>();
            for (Map.Entry<String, Long> entry : byInspect.entrySet()) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("inspectResult", entry.getKey());
                row.put("claimCount", entry.getValue());
                inspectRows.add(row);
            }

            String costRate = percentOf(costTotal, salesTotal);
            String repairRate30 = percent(claimsWithin30, shippedUnits);

            Map<String, Object> result = ServiceUtil.returnSuccess();
            result.put("fromDate", fromDate);
            result.put("toDate", toDate);
            result.put("deliveries", Long.valueOf(deliveries));
            result.put("shippedUnits", Long.valueOf(shippedUnits));
            result.put("claimCount", Long.valueOf(claimCount));
            result.put("rtnCount", Long.valueOf(rtnCount));
            result.put("exchCount", Long.valueOf(exchCount));
            result.put("repairCount", Long.valueOf(repairCount));
            result.put("refundOnlyCount", Long.valueOf(refundOnlyCount));
            result.put("claimsWithin30", Long.valueOf(claimsWithin30));
            result.put("repeatedSnCount", Long.valueOf(repeatedSnCount));
            result.put("returnRate", percent(rtnCount, shippedUnits));
            result.put("exchangeRate", percent(exchCount, shippedUnits));
            result.put("repairRate", percent(repairCount, shippedUnits));
            result.put("claimRate", percent(claimCount, shippedUnits));
            result.put("repairRate30", repairRate30);
            result.put("refundTotal", Double.valueOf(refundTotal));
            result.put("freightTotal", Double.valueOf(freightTotal));
            result.put("hoursTotal", Double.valueOf(hoursTotal));
            result.put("costTotal", Double.valueOf(costTotal));
            result.put("costPerUnit", Double.valueOf(shippedUnits == 0 ? 0.0 : costTotal / shippedUnits));
            result.put("salesTotal", Double.valueOf(salesTotal));
            result.put("costRate", costRate);
            result.put("hourlyRate", Double.valueOf(QUALITY_HOURLY_RATE));
            result.put("freightPerClaim", Double.valueOf(QUALITY_FREIGHT_COST));
            result.put("repairRateLimit", Double.valueOf(QUALITY_REPAIR_RATE_LIMIT));
            result.put("costRateLimit", Double.valueOf(QUALITY_COST_RATE_LIMIT));
            result.put("lotFactor", Double.valueOf(QUALITY_LOT_FACTOR));
            result.put("overRepairLimit", isOverLimit(claimsWithin30, shippedUnits,
                    QUALITY_REPAIR_RATE_LIMIT) ? "Y" : "N");
            result.put("overCostLimit", isOverLimit(costTotal, salesTotal,
                    QUALITY_COST_RATE_LIMIT) ? "Y" : "N");
            result.put("products", productRows);
            result.put("responsibilities", responsibilityRows);
            result.put("lots", lotRows);
            result.put("ages", ageRows);
            result.put("inspections", inspectRows);
            return result;
        } catch (GenericEntityException e) {
            return ServiceUtil.returnError("Could not build the quality report: " + e.getMessage());
        }
    }

    /**
     * Formats a ratio as a percentage with one decimal, for the reports.
     *
     * @param part the numerator
     * @param whole the denominator
     * @return the percentage, or {@code "-"} when the denominator is zero
     */
    static String percent(long part, long whole) {
        if (whole <= 0) {
            return "-";
        }
        return percentOf(part, whole);
    }

    /**
     * Formats a money ratio as a percentage with one decimal.
     *
     * @param part the numerator
     * @param whole the denominator
     * @return the percentage, or {@code "-"} when the denominator is zero
     */
    static String percentOf(double part, double whole) {
        if (whole <= 0.0) {
            return "-";
        }
        return String.format(Locale.ROOT, "%.1f", part * 100.0 / whole);
    }

    /**
     * Days between two dates, used to age a claim.
     *
     * @param from the earlier date, usually the delivery date
     * @param to the later date, usually the date the unit came back
     * @return the days, or {@code -1} when either date is missing
     */
    static int daysBetween(java.sql.Date from, java.sql.Date to) {
        if (from == null || to == null) {
            return -1;
        }
        return (int) java.time.temporal.ChronoUnit.DAYS.between(from.toLocalDate(), to.toLocalDate());
    }

    /**
     * Buckets the age of a claim, so early failures can be told apart from wear.
     *
     * @param days days between delivery and return
     * @return the bucket label
     */
    static String ageBucket(int days) {
        if (days < 0) {
            return "未知";
        }
        if (days <= 30) {
            return "0-30";
        }
        if (days <= 90) {
            return "31-90";
        }
        if (days <= 180) {
            return "91-180";
        }
        return ">180";
    }

    /**
     * Money one claim cost us: the refund, the freight we paid and the repair labour.
     *
     * @param refund amount refunded
     * @param freight freight we paid
     * @param hours repair hours
     * @param hourlyRate labour cost per hour
     * @return the cost
     */
    static double claimCost(double refund, double freight, double hours, double hourlyRate) {
        return refund + freight + hours * hourlyRate;
    }

    /**
     * Tells whether a measured ratio passed a red line.
     *
     * @param part the numerator
     * @param whole the denominator
     * @param limit the red line in percent
     * @return {@code true} when the ratio is above the limit
     */
    static boolean isOverLimit(double part, double whole, double limit) {
        if (whole <= 0.0) {
            return false;
        }
        return part * 100.0 / whole > limit;
    }

    private static Map<String, Object> productRow(Map<String, Map<String, Object>> rows, String key) {
        Map<String, Object> row = rows.get(key);
        if (row == null) {
            row = new LinkedHashMap<>();
            row.put("productCode", key);
            row.put("shippedUnits", Long.valueOf(0));
            row.put("claimCount", Long.valueOf(0));
            row.put("refundTotal", Double.valueOf(0.0));
            row.put("repairHours", Double.valueOf(0.0));
            row.put("cost", Double.valueOf(0.0));
            row.put("sales", Double.valueOf(0.0));
            rows.put(key, row);
        }
        return row;
    }

    private static double sumOf(Map<String, Object> row, String key) {
        Object value = row.get(key);
        return value == null ? 0.0 : ((Double) value).doubleValue();
    }

    private static void bump(Map<String, Long> counters, String key) {
        if (key == null) {
            return;
        }
        Long current = counters.get(key);
        counters.put(key, Long.valueOf(current == null ? 1 : current.longValue() + 1));
    }

    private static List<String> lotsOf(Delegator delegator, String workOrderId,
            Map<String, List<String>> cache) throws GenericEntityException {
        if (workOrderId == null) {
            return new ArrayList<>();
        }
        List<String> cached = cache.get(workOrderId);
        if (cached != null) {
            return cached;
        }
        List<String> lotIds = new ArrayList<>();
        for (GenericValue issue : EntityQuery.use(delegator).from("MfgMaterialIssue")
                .where("workOrderId", workOrderId).queryList()) {
            String lotId = issue.getString("lotId");
            if (lotId != null && !lotIds.contains(lotId)) {
                lotIds.add(lotId);
            }
        }
        cache.put(workOrderId, lotIds);
        return lotIds;
    }

    // ------------------------------------------------------------------ helpers

    /**
     * Keeps the rows that contain the keyword in any of the given fields, case insensitively.
     *
     * @param rows the rows to filter
     * @param keyword the keyword, may be {@code null} or blank for no filtering
     * @param keys the fields to look at
     * @return the matching rows, or all rows when there is no keyword
     */
    static List<Map<String, Object>> filterByKeyword(List<Map<String, Object>> rows, String keyword,
            String... keys) {
        if (keyword == null || keyword.trim().isEmpty()) {
            return rows;
        }
        String needle = keyword.trim().toLowerCase(Locale.ROOT);
        List<Map<String, Object>> hits = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            for (String key : keys) {
                Object value = row.get(key);
                if (value != null && String.valueOf(value).toLowerCase(Locale.ROOT).contains(needle)) {
                    hits.add(row);
                    break;
                }
            }
        }
        return hits;
    }

    /**
     * Number of pages needed for the given number of rows, at least one.
     *
     * @param total the number of rows
     * @param pageSize the rows per page
     * @return the page count
     */
    static long pageCount(int total, long pageSize) {
        if (pageSize <= 0) {
            return 1;
        }
        return Math.max(1, (total + pageSize - 1) / pageSize);
    }

    /**
     * Clamps a requested page number into the available range.
     *
     * @param page the requested page, 1 based
     * @param total the number of rows
     * @param pageSize the rows per page
     * @return the page that will be shown
     */
    static long pageNumber(long page, int total, long pageSize) {
        long pages = pageCount(total, pageSize);
        if (page <= 0) {
            return 1;
        }
        return Math.min(page, pages);
    }

    /**
     * Returns one page of rows, clamped so an out of range page still shows something sensible.
     *
     * @param all all rows
     * @param page the requested page, 1 based
     * @param pageSize the rows per page
     * @param <T> the row type
     * @return the rows of that page
     */
    static <T> List<T> pageSlice(List<T> all, long page, long pageSize) {
        if (all.isEmpty()) {
            return new ArrayList<>();
        }
        long current = pageNumber(page, all.size(), pageSize);
        int from = (int) ((current - 1) * pageSize);
        int to = (int) Math.min((long) all.size(), from + pageSize);
        if (from >= all.size()) {
            return new ArrayList<>();
        }
        return new ArrayList<>(all.subList(from, to));
    }

    /**
     * Puts one page of rows into the service result together with the paging metadata the screens
     * need, so every list page shows the same footer.
     *
     * @param result the service result
     * @param key the key the rows are stored under
     * @param rows all rows, already filtered
     * @param page the requested page, 1 based
     * @param pageSize the rows per page
     * @param keyword the keyword that was used, echoed back to the form
     */
    static void putPage(Map<String, Object> result, String key, List<Map<String, Object>> rows,
            long page, long pageSize, String keyword) {
        result.put(key, pageSlice(rows, page, pageSize));
        result.put("totalCount", Integer.valueOf(rows.size()));
        result.put("page", Long.valueOf(pageNumber(page, rows.size(), pageSize)));
        result.put("pageCount", Long.valueOf(pageCount(rows.size(), pageSize)));
        result.put("pageSize", Long.valueOf(pageSize));
        result.put("search", keyword);
    }

    /**
     * Reads the requested page from the service context.
     *
     * @param context the service context
     * @return the page number, 1 when unset
     */
    static long pageOf(Map<String, ? extends Object> context) {
        return toLong(context.get("page"), 1);
    }

    /**
     * Reads the requested page size from the service context, capped at 200 rows.
     *
     * @param context the service context
     * @return the page size, {@link #PAGE_SIZE} when unset or out of range
     */
    static long pageSizeOf(Map<String, ? extends Object> context) {
        long size = toLong(context.get("pageSize"), PAGE_SIZE);
        return size <= 0 || size > 200 ? PAGE_SIZE : size;
    }

    /**
     * Parses BOM CSV text into rows of
     * {@code componentCode, componentName, qty, designators, isKeyPart, unitPrice}.
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
            String[] row = new String[6];
            row[0] = code;
            row[1] = cells.length > 1 ? unquote(cells[1]) : null;
            row[2] = cells.length > 2 ? unquote(cells[2]) : "1";
            row[3] = cells.length > 3 ? unquote(cells[3]) : null;
            row[4] = cells.length > 4 ? unquote(cells[4]) : "N";
            row[5] = cells.length > 5 ? unquote(cells[5]) : null;
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

    /**
     * Parses an optional day and time, used by the movements of the inventory ledger.
     *
     * @param value the raw input, e.g. {@code 2026-10-21} or {@code 2026-10-21T14:30:00}
     * @return the timestamp, {@code null} when there is none or it cannot be read
     */
    private static Timestamp parseTimestamp(Object value) {
        String text = toText(value);
        if (text == null) {
            return null;
        }
        try {
            if (text.length() <= 10) {
                return Timestamp.valueOf(LocalDate.parse(text).atStartOfDay());
            }
            return Timestamp.valueOf(java.time.LocalDateTime.parse(text.replace(' ', 'T')));
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
                map.put("salePrice", product.getDouble("salePrice"));
                map.put("statusId", product.getString("statusId"));
                map.put("devStageId", product.getString("devStageId"));
                String currentVersion = currentBomVersionId(delegator, productCode);
                map.put("currentBomVersionId", currentVersion);
                map.put("bomCount", Integer.valueOf(currentVersion == null ? 0 : bomItemsOfVersion(delegator, currentVersion).size()));
                map.put("versionCount", Integer.valueOf(bomVersionCount(delegator, productCode)));
                migrateProductPhotos(delegator, productCode);
                List<GenericValue> attachments = attachmentRows(delegator, ATT_PRODUCT, productCode);
                map.put("imageCount", Integer.valueOf(attachments.size()));
                map.put("latestImageSeq", attachments.isEmpty() ? null : attachments.get(0).getLong("seq"));
                map.put("routingCount", Integer.valueOf(count(delegator, "MfgRouting", productCode)));
                list.add(map);
            }
            Map<String, Object> result = ServiceUtil.returnSuccess();
            putPage(result, "products",
                    filterByKeyword(list, toText(context.get("search")), "productCode", "productName",
                            "snPrefix", "statusId"),
                    pageOf(context), pageSizeOf(context), toText(context.get("search")));
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

    // ==================================================================
    // Attachments: photos and pdf kept with any business record
    // ==================================================================

    /** Biggest image accepted, in bytes. */
    public static final long IMAGE_MAX_BYTES = 4L * 1024L * 1024L;

    /** Biggest pdf accepted, in bytes. */
    public static final long PDF_MAX_BYTES = 8L * 1024L * 1024L;

    /** Content types accepted for an image attachment. */
    public static final String[] IMAGE_TYPES = {"image/jpeg", "image/png", "image/gif"};

    public static final String PDF_TYPE = "application/pdf";

    public static final String ATT_PRODUCT = "MFG_ATT_PRODUCT";
    public static final String ATT_DEV_PROJECT = "MFG_ATT_DEV_PROJECT";
    public static final String ATT_WORK_ORDER = "MFG_ATT_WORK_ORDER";
    public static final String ATT_DELIVERY = "MFG_ATT_DELIVERY";
    public static final String ATT_CLAIM = "MFG_ATT_CLAIM";

    /** Owner types an attachment can hang on. */
    public static final String[] ATTACHMENT_OWNER_TYPES = {
        ATT_PRODUCT, ATT_DEV_PROJECT, ATT_WORK_ORDER, ATT_DELIVERY, ATT_CLAIM};

    /**
     * Tells whether a content type is an accepted image type.
     *
     * @param contentType the content type sent by the browser, may be null
     * @return {@code true} for JPG, PNG and GIF
     */
    static boolean isAllowedImageType(String contentType) {
        if (contentType == null) {
            return false;
        }
        String type = contentType.trim().toLowerCase(Locale.ROOT);
        for (String allowed : IMAGE_TYPES) {
            if (allowed.equals(type)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Tells whether a content type may be attached: an image or a pdf.
     *
     * @param contentType the content type, may be null
     * @return {@code true} for an accepted type
     */
    static boolean isAllowedAttachmentType(String contentType) {
        return isAllowedImageType(contentType)
                || (contentType != null && PDF_TYPE.equals(contentType.trim().toLowerCase(Locale.ROOT)));
    }

    /**
     * Tells whether an owner type is known.
     *
     * @param ownerTypeId the owner type
     * @return {@code true} for a type of {@link #ATTACHMENT_OWNER_TYPES}
     */
    static boolean isKnownAttachmentOwnerType(String ownerTypeId) {
        if (ownerTypeId == null) {
            return false;
        }
        for (String type : ATTACHMENT_OWNER_TYPES) {
            if (type.equals(ownerTypeId)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Guesses the content type from a file name, used when the browser sends something useless such
     * as {@code application/octet-stream}.
     *
     * @param fileName the original file name, may be null
     * @return the content type, {@code null} when the extension is not supported
     */
    static String imageTypeOfFileName(String fileName) {
        if (fileName == null) {
            return null;
        }
        String name = fileName.trim().toLowerCase(Locale.ROOT);
        if (name.endsWith(".jpg") || name.endsWith(".jpeg")) {
            return "image/jpeg";
        }
        if (name.endsWith(".png")) {
            return "image/png";
        }
        if (name.endsWith(".gif")) {
            return "image/gif";
        }
        if (name.endsWith(".pdf")) {
            return PDF_TYPE;
        }
        return null;
    }

    /**
     * Keeps only the last part of a file name and replaces the characters that do not belong in a
     * name, so a Windows path or a strange upload cannot leak into the database.
     *
     * @param fileName the original file name, may be null
     * @return a short and safe file name
     */
    static String cleanFileName(String fileName) {
        if (fileName == null) {
            return "file";
        }
        String name = fileName.trim().replace('\\', '/');
        int slash = name.lastIndexOf('/');
        if (slash >= 0) {
            name = name.substring(slash + 1);
        }
        name = name.replaceAll("[^A-Za-z0-9._-]", "_");
        if (name.length() > 60) {
            name = name.substring(name.length() - 60);
        }
        return name.isEmpty() ? "file" : name;
    }

    private static List<GenericValue> attachmentRows(Delegator delegator, String ownerTypeId, String ownerId)
            throws GenericEntityException {
        return EntityQuery.use(delegator).from("MfgAttachment")
                .where("ownerTypeId", ownerTypeId, "ownerId", ownerId).orderBy("-seq").queryList();
    }

    /**
     * Copies the rows of the old product photo table into the generic attachment table the first
     * time a model is read, so old and new photos live in one place.
     *
     * @param delegator the delegator
     * @param productCode the model
     * @return the number of copied rows
     * @throws GenericEntityException when the rows cannot be read or written
     */
    static int migrateProductPhotos(Delegator delegator, String productCode) throws GenericEntityException {
        List<GenericValue> legacy = EntityQuery.use(delegator).from("MfgProductImage")
                .where("productCode", productCode).orderBy("seq").queryList();
        if (legacy.isEmpty()) {
            return 0;
        }
        Set<Long> existing = new LinkedHashSet<>();
        for (GenericValue attachment : attachmentRows(delegator, ATT_PRODUCT, productCode)) {
            existing.add(attachment.getLong("seq"));
        }
        int copied = 0;
        for (GenericValue image : legacy) {
            Long seq = image.getLong("seq");
            if (seq == null || existing.contains(seq)) {
                continue;
            }
            GenericValue attachment = delegator.makeValue("MfgAttachment");
            attachment.set("ownerTypeId", ATT_PRODUCT);
            attachment.set("ownerId", productCode);
            attachment.set("seq", seq);
            attachment.set("fileName", image.getString("fileName"));
            attachment.set("contentType", image.getString("contentType"));
            attachment.set("fileSize", image.getLong("imageSize"));
            attachment.set("fileData", image.getBytes("imageData"));
            attachment.set("note", image.getString("note"));
            delegator.create(attachment);
            copied++;
        }
        return copied;
    }

    private static long nextAttachmentSeq(Delegator delegator, String ownerTypeId, String ownerId)
            throws GenericEntityException {
        long max = 0;
        for (GenericValue attachment : attachmentRows(delegator, ownerTypeId, ownerId)) {
            Long seq = attachment.getLong("seq");
            if (seq != null) {
                max = Math.max(max, seq.longValue());
            }
        }
        return max + 1L;
    }

    private static Map<String, Object> toAttachmentMap(GenericValue attachment) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("ownerTypeId", attachment.getString("ownerTypeId"));
        map.put("ownerId", attachment.getString("ownerId"));
        map.put("seq", attachment.getLong("seq"));
        map.put("fileName", attachment.getString("fileName"));
        map.put("contentType", attachment.getString("contentType"));
        map.put("fileSize", attachment.getLong("fileSize"));
        map.put("isImage", Boolean.valueOf(isAllowedImageType(attachment.getString("contentType"))));
        map.put("note", attachment.getString("note"));
        map.put("createdStamp", attachment.get("createdStamp"));
        return map;
    }

    /**
     * Validates and stores one attachment. Shared by the upload event and the service so both
     * behave the same way.
     *
     * @param delegator the delegator
     * @param ownerTypeId the kind of record the file belongs to
     * @param ownerId the key of that record
     * @param fileName the original file name
     * @param contentType the content type, the extension is used when it is not a known type
     * @param data the file bytes
     * @param note an optional note
     * @return the service result with {@code seq} and {@code fileSize}, or an error message
     */
    static Map<String, Object> storeAttachment(Delegator delegator, String ownerTypeId, String ownerId,
            String fileName, String contentType, byte[] data, String note) {
        if (!isKnownAttachmentOwnerType(ownerTypeId)) {
            return attachmentError("owner", "Unknown attachment owner type [" + ownerTypeId + "]");
        }
        if (ownerId == null) {
            return attachmentError("owner", "ownerId is required");
        }
        if (data == null || data.length == 0) {
            return attachmentError("nofile", "Choose a file first");
        }
        String type = isAllowedAttachmentType(contentType)
                ? contentType.trim().toLowerCase(Locale.ROOT) : imageTypeOfFileName(fileName);
        if (type == null) {
            return attachmentError("type", "Only JPG, PNG, GIF and PDF files are supported");
        }
        boolean pdf = PDF_TYPE.equals(type);
        long limit = pdf ? PDF_MAX_BYTES : IMAGE_MAX_BYTES;
        if (data.length > limit) {
            return attachmentError("size",
                    (pdf ? "The PDF is larger than 8 MB" : "The image is larger than 4 MB")
                    + ", please shrink it first");
        }
        try {
            long seq = nextAttachmentSeq(delegator, ownerTypeId, ownerId);
            GenericValue attachment = delegator.makeValue("MfgAttachment");
            attachment.set("ownerTypeId", ownerTypeId);
            attachment.set("ownerId", ownerId);
            attachment.set("seq", Long.valueOf(seq));
            attachment.set("fileName", cleanFileName(fileName));
            attachment.set("contentType", type);
            attachment.set("fileSize", Long.valueOf(data.length));
            attachment.set("fileData", data);
            attachment.set("note", note);
            delegator.create(attachment);

            Map<String, Object> result = ServiceUtil.returnSuccess();
            result.put("ownerTypeId", ownerTypeId);
            result.put("ownerId", ownerId);
            result.put("seq", Long.valueOf(seq));
            result.put("fileSize", Long.valueOf(data.length));
            result.put("contentType", type);
            return result;
        } catch (GenericEntityException e) {
            return ServiceUtil.returnError("Could not store the attachment: " + e.getMessage());
        }
    }

    /**
     * Stores an attachment given as a base64 string, handy for scripts and for the integration test.
     *
     * @param dctx the dispatch context
     * @param context service context, see {@code servicedef/services.xml#mfgAttachmentSave}
     * @return the service result containing {@code seq}
     */
    public static Map<String, Object> attachmentSave(DispatchContext dctx, Map<String, ? extends Object> context) {
        String base64 = toText(context.get("base64Data"));
        if (base64 == null) {
            return ServiceUtil.returnError("base64Data is required");
        }
        byte[] data;
        try {
            data = java.util.Base64.getMimeDecoder().decode(base64);
        } catch (IllegalArgumentException e) {
            return ServiceUtil.returnError("The attachment data is not valid base64");
        }
        return storeAttachment(dctx.getDelegator(), toText(context.get("ownerTypeId")),
                toText(context.get("ownerId")), toText(context.get("fileName")),
                toText(context.get("contentType")), data, toText(context.get("note")));
    }

    /**
     * Lists the attachments of one record, newest first, without the file bytes.
     *
     * @param dctx the dispatch context
     * @param context service context, see {@code servicedef/services.xml#mfgAttachmentList}
     * @return the service result containing {@code attachments}
     */
    public static Map<String, Object> attachmentList(DispatchContext dctx, Map<String, ? extends Object> context) {
        Delegator delegator = dctx.getDelegator();
        String ownerTypeId = toText(context.get("ownerTypeId"));
        String ownerId = toText(context.get("ownerId"));
        List<Map<String, Object>> list = new ArrayList<>();
        if (ownerTypeId != null && ownerId != null) {
            try {
                if (ATT_PRODUCT.equals(ownerTypeId)) {
                    // photos of the old single table are moved over the first time they are read
                    migrateProductPhotos(delegator, ownerId);
                }
                for (GenericValue attachment : attachmentRows(delegator, ownerTypeId, ownerId)) {
                    list.add(toAttachmentMap(attachment));
                }
            } catch (GenericEntityException e) {
                return ServiceUtil.returnError("Could not read the attachments: " + e.getMessage());
            }
        }
        Map<String, Object> result = ServiceUtil.returnSuccess();
        result.put("attachments", list);
        result.put("attachmentCount", Integer.valueOf(list.size()));
        return result;
    }

    /**
     * Builds a service error that also carries a short code the page can translate.
     *
     * @param code the code, for example {@code size} or {@code type}
     * @param message the message for the log and for services callers
     * @return the error result
     */
    private static Map<String, Object> attachmentError(String code, String message) {
        Map<String, Object> result = ServiceUtil.returnError(message);
        result.put("errorCode", code);
        return result;
    }

    /**
     * Removes one attachment of a record.
     *
     * @param delegator the delegator
     * @param ownerTypeId the kind of record
     * @param ownerId the key of the record
     * @param seq the sequence number of the file
     * @return the service result or an error
     */
    static Map<String, Object> deleteAttachment(Delegator delegator, String ownerTypeId, String ownerId,
            long seq) {
        try {
            GenericValue attachment = EntityQuery.use(delegator).from("MfgAttachment")
                    .where("ownerTypeId", ownerTypeId, "ownerId", ownerId, "seq", Long.valueOf(seq)).queryOne();
            if (attachment == null) {
                return ServiceUtil.returnError("Attachment " + seq + " of [" + ownerId + "] does not exist");
            }
            attachment.remove();
            Map<String, Object> result = ServiceUtil.returnSuccess();
            result.put("ownerTypeId", ownerTypeId);
            result.put("ownerId", ownerId);
            result.put("seq", Long.valueOf(seq));
            return result;
        } catch (GenericEntityException e) {
            return ServiceUtil.returnError("Could not delete the attachment: " + e.getMessage());
        }
    }

    /**
     * Removes one attachment of a record.
     *
     * @param dctx the dispatch context
     * @param context service context, see {@code servicedef/services.xml#mfgAttachmentDelete}
     * @return the service result containing the owner keys and {@code seq}
     */
    public static Map<String, Object> attachmentDelete(DispatchContext dctx, Map<String, ? extends Object> context) {
        String ownerTypeId = toText(context.get("ownerTypeId"));
        String ownerId = toText(context.get("ownerId"));
        long seq = toLong(context.get("seq"), 0);
        if (ownerTypeId == null || ownerId == null || seq <= 0) {
            return ServiceUtil.returnError("ownerTypeId, ownerId and seq are required");
        }
        return deleteAttachment(dctx.getDelegator(), ownerTypeId, ownerId, seq);
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
            String currentVersion = currentBomVersionId(delegator, productCode);
            List<GenericValue> bomItems = currentVersion == null ? new ArrayList<GenericValue>()
                    : bomItemsOfVersion(delegator, currentVersion);
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
                map.put("unitPrice", item.get("unitPrice"));
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
            result.put("bomVersionId", currentVersion);
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
        map.put("laborRate", product.get("laborRate"));
        map.put("warrantyMonths", product.getLong("warrantyMonths"));
        map.put("salePrice", product.getDouble("salePrice"));
        map.put("statusId", product.getString("statusId"));
        map.put("devStageId", product.getString("devStageId"));
        map.put("currentBomVersionId", product.getString("currentBomVersionId"));
        map.put("note", product.getString("note"));
        return map;
    }

    // ==================================================================
    // BOM versions
    // ==================================================================

    /**
     * Builds the identifier of one BOM version, {@code model-Vnumber}.
     *
     * @param productCode the model code
     * @param versionNo the version number, starting at 1
     * @return the version identifier, e.g. {@code BT5-V2}
     */
    static String formatBomVersionId(String productCode, long versionNo) {
        return productCode + BOM_VERSION_SEPARATOR + versionNo;
    }

    /**
     * Returns the next version number of a model.
     *
     * @param versionIds the identifiers of the versions that already exist
     * @param productCode the model code
     * @return one more than the highest version number found, 1 when there is none
     */
    static long nextBomVersionNo(List<String> versionIds, String productCode) {
        String prefix = productCode + BOM_VERSION_SEPARATOR;
        int max = 0;
        for (String versionId : versionIds) {
            if (versionId != null && versionId.startsWith(prefix)) {
                max = Math.max(max, parseTrailingNumber(versionId.substring(prefix.length())));
            }
        }
        return max + 1L;
    }

    private static List<String> versionIds(Delegator delegator, String productCode) throws GenericEntityException {
        List<String> ids = new ArrayList<>();
        for (GenericValue version : bomVersionsOf(delegator, productCode)) {
            ids.add(version.getString("bomVersionId"));
        }
        return ids;
    }

    private static List<GenericValue> bomVersionsOf(Delegator delegator, String productCode)
            throws GenericEntityException {
        return EntityQuery.use(delegator).from("MfgBomVersion")
                .where("productCode", productCode).orderBy("-versionNo").queryList();
    }

    private static int bomVersionCount(Delegator delegator, String productCode) {
        try {
            return bomVersionsOf(delegator, productCode).size();
        } catch (GenericEntityException e) {
            return 0;
        }
    }

    private static List<GenericValue> bomItemsOfVersion(Delegator delegator, String bomVersionId)
            throws GenericEntityException {
        return EntityQuery.use(delegator).from("MfgBomVersionItem")
                .where("bomVersionId", bomVersionId).orderBy("lineSeq").queryList();
    }

    /**
     * Returns the line number a new BOM line of this version gets: ten more than the highest one in
     * use, so that appending never collides with an existing line.
     *
     * @param delegator the delegator
     * @param bomVersionId the version
     * @return the next free line number, 10 for an empty version
     * @throws GenericEntityException when the version cannot be read
     */
    static long nextBomLineSeq(Delegator delegator, String bomVersionId) throws GenericEntityException {
        long max = 0;
        for (GenericValue item : bomItemsOfVersion(delegator, bomVersionId)) {
            Long lineSeq = item.getLong("lineSeq");
            if (lineSeq != null) {
                max = Math.max(max, lineSeq.longValue());
            }
        }
        return max + 10L;
    }

    /**
     * Returns the BOM version the model currently uses, freezing the old single BOM table into a
     * released version the first time a model that only has the old rows is read.
     *
     * @param delegator the delegator
     * @param productCode the model code
     * @return the version identifier, {@code null} when the model has no BOM at all
     */
    static String currentBomVersionId(Delegator delegator, String productCode) {
        try {
            if (productCode == null) {
                return null;
            }
            GenericValue product = EntityQuery.use(delegator).from("MfgProduct")
                    .where("productCode", productCode).queryOne();
            if (product == null) {
                return null;
            }
            String current = toText(product.getString("currentBomVersionId"));
            if (current != null && EntityQuery.use(delegator).from("MfgBomVersion")
                    .where("bomVersionId", current).queryOne() != null) {
                return current;
            }
            List<GenericValue> legacy = EntityQuery.use(delegator).from("MfgBomItem")
                    .where("productCode", productCode).orderBy("lineSeq").queryList();
            if (legacy.isEmpty()) {
                return null;
            }
            long versionNo = nextBomVersionNo(versionIds(delegator, productCode), productCode);
            String versionId = formatBomVersionId(productCode, versionNo);
            GenericValue version = delegator.makeValue("MfgBomVersion");
            version.set("bomVersionId", versionId);
            version.set("productCode", productCode);
            version.set("versionNo", Long.valueOf(versionNo));
            version.set("statusId", BOM_RELEASED);
            version.set("effectiveDate", java.sql.Date.valueOf(LocalDate.now()));
            version.set("note", "Frozen from the single BOM table");
            delegator.create(version);
            long lineSeq = 10;
            for (GenericValue item : legacy) {
                GenericValue copy = delegator.makeValue("MfgBomVersionItem");
                copy.set("bomVersionId", versionId);
                copy.set("lineSeq", Long.valueOf(lineSeq));
                copy.set("componentCode", item.getString("componentCode"));
                copy.set("componentName", item.getString("componentName"));
                copy.set("qty", item.getDouble("qty"));
                copy.set("designators", item.getString("designators"));
                copy.set("isKeyPart", item.getString("isKeyPart"));
                delegator.create(copy);
                lineSeq += 10;
            }
            product.set("currentBomVersionId", versionId);
            product.store();
            return versionId;
        } catch (GenericEntityException e) {
            return null;
        }
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
    private static List<GenericValue> bomItemsForOrder(Delegator delegator, GenericValue order)
            throws GenericEntityException {
        String versionId = toText(order.getString("bomVersionId"));
        if (versionId == null) {
            versionId = currentBomVersionId(delegator, order.getString("productCode"));
        }
        return versionId == null ? new ArrayList<GenericValue>() : bomItemsOfVersion(delegator, versionId);
    }

    private static String createBomVersion(Delegator delegator, String productCode, String ecoId,
            String copyCurrent, String note) throws GenericEntityException {
        long versionNo = nextBomVersionNo(versionIds(delegator, productCode), productCode);
        String versionId = formatBomVersionId(productCode, versionNo);
        String basis = currentBomVersionId(delegator, productCode);
        GenericValue version = delegator.makeValue("MfgBomVersion");
        version.set("bomVersionId", versionId);
        version.set("productCode", productCode);
        version.set("versionNo", Long.valueOf(versionNo));
        version.set("statusId", BOM_DRAFT);
        version.set("basisVersionId", basis);
        version.set("ecoId", ecoId);
        version.set("note", note);
        delegator.create(version);
        if (!"N".equals(copyCurrent) && basis != null) {
            long lineSeq = 10;
            for (GenericValue item : bomItemsOfVersion(delegator, basis)) {
                GenericValue copy = delegator.makeValue("MfgBomVersionItem");
                copy.set("bomVersionId", versionId);
                copy.set("lineSeq", Long.valueOf(lineSeq));
                copy.set("componentCode", item.getString("componentCode"));
                copy.set("componentName", item.getString("componentName"));
                copy.set("qty", item.getDouble("qty"));
                copy.set("designators", item.getString("designators"));
                copy.set("isKeyPart", item.getString("isKeyPart"));
                delegator.create(copy);
                lineSeq += 10;
            }
        }
        return versionId;
    }

    private static Map<String, Object> toBomVersionMap(Delegator delegator, GenericValue version)
            throws GenericEntityException {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("bomVersionId", version.getString("bomVersionId"));
        map.put("productCode", version.getString("productCode"));
        map.put("versionNo", version.getLong("versionNo"));
        map.put("statusId", version.getString("statusId"));
        map.put("effectiveDate", version.get("effectiveDate"));
        map.put("basisVersionId", version.getString("basisVersionId"));
        map.put("ecoId", version.getString("ecoId"));
        map.put("note", version.getString("note"));
        map.put("itemCount", Integer.valueOf(bomItemsOfVersion(delegator, version.getString("bomVersionId")).size()));
        return map;
    }

    /**
     * Creates the next BOM version of a model as a draft, copying the current lines by default.
     *
     * @param dctx the dispatch context
     * @param context service context, see {@code servicedef/services.xml#mfgBomVersionCreate}
     * @return the service result containing {@code bomVersionId}
     */
    public static Map<String, Object> bomVersionCreate(DispatchContext dctx, Map<String, ? extends Object> context) {
        Delegator delegator = dctx.getDelegator();
        String productCode = toText(context.get("productCode"));
        if (productCode == null) {
            return ServiceUtil.returnError("productCode is required");
        }
        try {
            if (EntityQuery.use(delegator).from("MfgProduct").where("productCode", productCode).queryOne() == null) {
                return ServiceUtil.returnError("Unknown product model [" + productCode + "], create it first");
            }
            String copyCurrent = toText(context.get("copyCurrent"));
            String versionId = createBomVersion(delegator, productCode, toText(context.get("ecoId")),
                    copyCurrent == null ? "Y" : copyCurrent, toText(context.get("note")));
            GenericValue version = EntityQuery.use(delegator).from("MfgBomVersion")
                    .where("bomVersionId", versionId).queryOne();
            Map<String, Object> result = ServiceUtil.returnSuccess();
            result.put("bomVersionId", versionId);
            result.put("versionNo", version.getLong("versionNo"));
            result.put("copiedCount", Long.valueOf(bomItemsOfVersion(delegator, versionId).size()));
            return result;
        } catch (GenericEntityException e) {
            return ServiceUtil.returnError("Could not create the BOM version: " + e.getMessage());
        }
    }

    /**
     * Makes a BOM version the one in use. The version becomes released and the other released
     * versions of the same model become obsolete.
     *
     * @param dctx the dispatch context
     * @param context service context, see {@code servicedef/services.xml#mfgBomVersionActivate}
     * @return the service result containing {@code bomVersionId} and {@code replacedVersionId}
     */
    public static Map<String, Object> bomVersionActivate(DispatchContext dctx, Map<String, ? extends Object> context) {
        Delegator delegator = dctx.getDelegator();
        String bomVersionId = toText(context.get("bomVersionId"));
        if (bomVersionId == null) {
            return ServiceUtil.returnError("bomVersionId is required");
        }
        try {
            GenericValue version = EntityQuery.use(delegator).from("MfgBomVersion")
                    .where("bomVersionId", bomVersionId).queryOne();
            if (version == null) {
                return ServiceUtil.returnError("Unknown BOM version [" + bomVersionId + "]");
            }
            String productCode = version.getString("productCode");
            GenericValue product = EntityQuery.use(delegator).from("MfgProduct")
                    .where("productCode", productCode).queryOne();
            String replaced = product == null ? null : toText(product.getString("currentBomVersionId"));
            for (GenericValue other : bomVersionsOf(delegator, productCode)) {
                if (!bomVersionId.equals(other.getString("bomVersionId"))
                        && BOM_RELEASED.equals(other.getString("statusId"))) {
                    other.set("statusId", BOM_OBSOLETE);
                    other.store();
                }
            }
            version.set("statusId", BOM_RELEASED);
            version.set("effectiveDate", java.sql.Date.valueOf(LocalDate.now()));
            version.store();
            if (product != null) {
                product.set("currentBomVersionId", bomVersionId);
                product.store();
            }
            Map<String, Object> result = ServiceUtil.returnSuccess();
            result.put("bomVersionId", bomVersionId);
            result.put("productCode", productCode);
            result.put("replacedVersionId", replaced);
            return result;
        } catch (GenericEntityException e) {
            return ServiceUtil.returnError("Could not activate the BOM version: " + e.getMessage());
        }
    }

    /**
     * Lists the BOM versions of one model, newest first.
     *
     * @param dctx the dispatch context
     * @param context service context, see {@code servicedef/services.xml#mfgBomVersionList}
     * @return the service result containing {@code bomVersions}
     */
    public static Map<String, Object> bomVersionList(DispatchContext dctx, Map<String, ? extends Object> context) {
        Delegator delegator = dctx.getDelegator();
        String productCode = toText(context.get("productCode"));
        try {
            List<GenericValue> versions = productCode == null
                    ? EntityQuery.use(delegator).from("MfgBomVersion").orderBy("-createdStamp").queryList()
                    : bomVersionsOf(delegator, productCode);
            List<Map<String, Object>> list = new ArrayList<>(versions.size());
            for (GenericValue version : versions) {
                list.add(toBomVersionMap(delegator, version));
            }
            Map<String, Object> result = ServiceUtil.returnSuccess();
            putPage(result, "bomVersions",
                    filterByKeyword(list, toText(context.get("search")), "bomVersionId", "productCode",
                            "statusId", "note"),
                    pageOf(context), pageSizeOf(context), toText(context.get("search")));
            return result;
        } catch (GenericEntityException e) {
            return ServiceUtil.returnError("Could not read the BOM versions: " + e.getMessage());
        }
    }

    /**
     * Freezes the rows of the old single BOM table into a released version for the models that
     * still only have those rows. Idempotent, so it can be called on every deployment.
     *
     * @param dctx the dispatch context
     * @param context service context, see {@code servicedef/services.xml#mfgBomMigrateLegacy}
     * @return the service result containing the migrated model codes
     */
    public static Map<String, Object> bomMigrateLegacy(DispatchContext dctx, Map<String, ? extends Object> context) {
        Delegator delegator = dctx.getDelegator();
        String productCode = toText(context.get("productCode"));
        List<String> migrated = new ArrayList<>();
        try {
            List<GenericValue> products = productCode == null
                    ? EntityQuery.use(delegator).from("MfgProduct").orderBy("productCode").queryList()
                    : EntityQuery.use(delegator).from("MfgProduct").where("productCode", productCode).queryList();
            for (GenericValue product : products) {
                String code = product.getString("productCode");
                String before = toText(product.getString("currentBomVersionId"));
                String after = currentBomVersionId(delegator, code);
                if (before == null && after != null) {
                    migrated.add(code);
                }
            }
            Map<String, Object> result = ServiceUtil.returnSuccess();
            result.put("migratedProducts", migrated);
            result.put("migratedCount", Long.valueOf(migrated.size()));
            return result;
        } catch (GenericEntityException e) {
            return ServiceUtil.returnError("Could not migrate the legacy BOM: " + e.getMessage());
        }
    }

    // ==================================================================
    // Product development
    // ==================================================================

    /**
     * Returns the stage that follows the given one.
     *
     * @param stageId the current stage
     * @return the next stage, {@code null} when the stage is unknown or is already the last one
     */
    static String nextDevStage(String stageId) {
        for (int i = 0; i < DEV_STAGES.length - 1; i++) {
            if (DEV_STAGES[i].equals(stageId)) {
                return DEV_STAGES[i + 1];
            }
        }
        return null;
    }

    /**
     * Returns the zero based position of a stage in the ladder.
     *
     * @param stageId the stage
     * @return the position, -1 when the stage is unknown
     */
    static int devStageIndex(String stageId) {
        for (int i = 0; i < DEV_STAGES.length; i++) {
            if (DEV_STAGES[i].equals(stageId)) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Tells whether a gate review result lets the project move on.
     *
     * @param resultId the review result
     * @return {@code true} for a pass and for a conditional pass
     */
    static boolean stageAdvancesOn(String resultId) {
        return REVIEW_PASS.equals(resultId) || REVIEW_CONDITIONAL.equals(resultId);
    }

    static boolean isKnownDevStage(String stageId) {
        return devStageIndex(stageId) >= 0;
    }

    static boolean isKnownReviewResult(String resultId) {
        return REVIEW_PASS.equals(resultId) || REVIEW_CONDITIONAL.equals(resultId) || REVIEW_FAIL.equals(resultId);
    }

    static boolean isKnownDevStatus(String statusId) {
        return DEV_ACTIVE.equals(statusId) || DEV_ONHOLD.equals(statusId) || DEV_DONE.equals(statusId)
                || DEV_CANCELLED.equals(statusId);
    }

    static boolean isKnownEcoStatus(String statusId) {
        return ECO_DRAFT.equals(statusId) || ECO_APPROVED.equals(statusId) || ECO_APPLIED.equals(statusId)
                || ECO_CANCELLED.equals(statusId);
    }

    static boolean isKnownEcoType(String ecoTypeId) {
        return "MFG_ECO_BOM".equals(ecoTypeId) || "MFG_ECO_PROCESS".equals(ecoTypeId)
                || "MFG_ECO_DESIGN".equals(ecoTypeId) || "MFG_ECO_MATERIAL".equals(ecoTypeId);
    }

    /**
     * Tells whether a change order may be applied: only an approved one, and only once.
     *
     * @param statusId the change order status
     * @return {@code true} when applying it is allowed
     */
    static boolean ecoAppliable(String statusId) {
        return ECO_APPROVED.equals(statusId);
    }

    /**
     * Returns the steps of one development track, in the order they have to be done.
     *
     * @param trackId the track, one of the {@code TRACK_*} constants
     * @return the step identifiers, empty when the track is unknown or has no steps
     */
    static String[] stepsOfTrack(String trackId) {
        if (TRACK_PCB.equals(trackId)) {
            return PCB_STEPS;
        }
        if (TRACK_FIRMWARE.equals(trackId)) {
            return FIRMWARE_STEPS;
        }
        if (TRACK_ENCLOSURE.equals(trackId)) {
            return ENCLOSURE_STEPS;
        }
        return new String[0];
    }

    static boolean isKnownTrack(String trackId) {
        return TRACK_PCB.equals(trackId) || TRACK_FIRMWARE.equals(trackId) || TRACK_ENCLOSURE.equals(trackId);
    }

    /**
     * Tells whether a step belongs to a track.
     *
     * @param trackId the track, may be empty for a project without a track
     * @param stepId the step
     * @return {@code true} when the step is part of the catalog of that track
     */
    static boolean isKnownStep(String trackId, String stepId) {
        for (String step : stepsOfTrack(trackId)) {
            if (step.equals(stepId)) {
                return true;
            }
        }
        return false;
    }

    static boolean isKnownStepStatus(String statusId) {
        return STEP_TODO.equals(statusId) || STEP_DOING.equals(statusId) || STEP_DONE.equals(statusId)
                || STEP_SKIPPED.equals(statusId);
    }

    /**
     * Tells whether a step status counts as finished, so it no longer holds the track up.
     *
     * @param statusId the step status
     * @return {@code true} for done and for skipped
     */
    static boolean stepFinished(String statusId) {
        return STEP_DONE.equals(statusId) || STEP_SKIPPED.equals(statusId);
    }

    /**
     * Returns the first step of a track that is not finished yet.
     *
     * @param trackId the track
     * @param finishedStepIds the steps that are done or skipped
     * @return the step identifier, {@code null} when every step is finished
     */
    static String firstOpenStep(String trackId, List<String> finishedStepIds) {
        for (String step : stepsOfTrack(trackId)) {
            if (finishedStepIds == null || !finishedStepIds.contains(step)) {
                return step;
            }
        }
        return null;
    }

    /**
     * Formats a development project number as {@code DEV-yyMMdd-NN}.
     *
     * @param date the creation day
     * @param sequence the running number, starting at 1
     * @return the formatted project number
     */
    static String formatDevProjectId(LocalDate date, int sequence) {
        return "DEV-" + date.format(DAY) + "-" + String.format(Locale.ROOT, "%02d", sequence);
    }

    /**
     * Formats a change order number as {@code ECO-yyMMdd-NN}.
     *
     * @param date the creation day
     * @param sequence the running number, starting at 1
     * @return the formatted change order number
     */
    static String formatEcoId(LocalDate date, int sequence) {
        return "ECO-" + date.format(DAY) + "-" + String.format(Locale.ROOT, "%02d", sequence);
    }

    private static String nextDevProjectId(Delegator delegator, LocalDate today) throws GenericEntityException {
        String prefix = "DEV-" + today.format(DAY) + "-";
        int max = 0;
        for (GenericValue project : EntityQuery.use(delegator).from("MfgDevProject").queryList()) {
            String id = project.getString("projectCode");
            if (id != null && id.startsWith(prefix)) {
                max = Math.max(max, parseTrailingNumber(id.substring(prefix.length())));
            }
        }
        return formatDevProjectId(today, max + 1);
    }

    private static String nextEcoId(Delegator delegator, LocalDate today) throws GenericEntityException {
        String prefix = "ECO-" + today.format(DAY) + "-";
        int max = 0;
        for (GenericValue eco : EntityQuery.use(delegator).from("MfgDevEco").queryList()) {
            String id = eco.getString("ecoId");
            if (id != null && id.startsWith(prefix)) {
                max = Math.max(max, parseTrailingNumber(id.substring(prefix.length())));
            }
        }
        return formatEcoId(today, max + 1);
    }

    private static String nextIssueId(Delegator delegator, LocalDate today) throws GenericEntityException {
        String prefix = "ISSUE-" + today.format(DAY) + "-";
        int max = 0;
        for (GenericValue issue : EntityQuery.use(delegator).from("MfgDevStepIssue").queryList()) {
            String id = issue.getString("issueId");
            if (id != null && id.startsWith(prefix)) {
                max = Math.max(max, parseTrailingNumber(id.substring(prefix.length())));
            }
        }
        return prefix + String.format(Locale.ROOT, "%02d", Integer.valueOf(max + 1));
    }

    /**
     * Tells whether a status is one of the issue statuses.
     *
     * @param statusId the status to test
     * @return {@code true} when it is known
     */
    static boolean isKnownIssueStatus(String statusId) {
        for (String known : ISSUE_STATUSES) {
            if (known.equals(statusId)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Tells whether a severity is one of the issue severities.
     *
     * @param severityId the severity to test
     * @return {@code true} when it is known
     */
    static boolean isKnownIssueSeverity(String severityId) {
        for (String known : ISSUE_SEVERITIES) {
            if (known.equals(severityId)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Orders issue statuses so the open ones come first.
     *
     * @param statusId the status, may be {@code null}
     * @return a small number for open issues, a big one for closed ones
     */
    static int statusRank(String statusId) {
        if (ISSUE_CLOSED.equals(statusId)) {
            return 2;
        }
        if (ISSUE_DOING.equals(statusId)) {
            return 1;
        }
        return 0;
    }

    /**
     * Orders issue severities from high to low.
     *
     * @param severityId the severity, may be {@code null}
     * @return 3 for high, 2 for medium and 1 for low or unknown
     */
    static int severityRank(String severityId) {
        if (ISSUE_HIGH.equals(severityId)) {
            return 3;
        }
        if (ISSUE_MEDIUM.equals(severityId)) {
            return 2;
        }
        return 1;
    }

    /**
     * Tells whether an issue still has to be dealt with.
     *
     * @param statusId the status, may be {@code null}
     * @return {@code true} when it is neither closed nor empty
     */
    static boolean isOpenIssue(String statusId) {
        return statusId != null && !ISSUE_CLOSED.equals(statusId);
    }

    private static Map<String, Object> toIssueMap(GenericValue issue) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("issueId", issue.getString("issueId"));
        map.put("projectCode", issue.getString("projectCode"));
        map.put("stepId", issue.getString("stepId"));
        map.put("title", issue.getString("title"));
        map.put("detail", issue.getString("detail"));
        map.put("severityId", issue.getString("severityId"));
        map.put("statusId", issue.getString("statusId"));
        map.put("ownerName", issue.getString("ownerName"));
        map.put("foundDate", issue.get("foundDate"));
        map.put("dueDate", issue.get("dueDate"));
        map.put("closedDate", issue.get("closedDate"));
        map.put("resolution", issue.getString("resolution"));
        map.put("createdStamp", issue.get("createdStamp"));
        return map;
    }

    private static Map<String, Object> toDevProjectMap(Delegator delegator, GenericValue project)
            throws GenericEntityException {
        String projectCode = project.getString("projectCode");
        String stageId = project.getString("stageId");
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("projectCode", projectCode);
        map.put("projectName", project.getString("projectName"));
        map.put("productCode", project.getString("productCode"));
        map.put("stageId", stageId);
        map.put("stageIndex", Integer.valueOf(devStageIndex(stageId)));
        map.put("statusId", project.getString("statusId"));
        map.put("ownerName", project.getString("ownerName"));
        map.put("targetCost", project.getDouble("targetCost"));
        map.put("targetPrice", project.getDouble("targetPrice"));
        map.put("plannedLaunchDate", project.get("plannedLaunchDate"));
        map.put("actualLaunchDate", project.get("actualLaunchDate"));
        map.put("note", project.getString("note"));
        List<GenericValue> reviews = EntityQuery.use(delegator).from("MfgDevStageReview")
                .where("projectCode", projectCode).queryList();
        int passed = 0;
        for (GenericValue review : reviews) {
            if (stageAdvancesOn(review.getString("resultId"))) {
                passed++;
            }
        }
        map.put("reviewCount", Integer.valueOf(reviews.size()));
        map.put("passedCount", Integer.valueOf(passed));
        int openEcos = 0;
        for (GenericValue eco : EntityQuery.use(delegator).from("MfgDevEco")
                .where("projectCode", projectCode).queryList()) {
            String status = eco.getString("statusId");
            if (ECO_DRAFT.equals(status) || ECO_APPROVED.equals(status)) {
                openEcos++;
            }
        }
        map.put("openEcoCount", Integer.valueOf(openEcos));

        int openIssues = 0;
        for (GenericValue issue : EntityQuery.use(delegator).from("MfgDevStepIssue")
                .where("projectCode", projectCode).queryList()) {
            if (isOpenIssue(issue.getString("statusId"))) {
                openIssues++;
            }
        }
        map.put("openIssueCount", Integer.valueOf(openIssues));

        String trackId = project.getString("trackId");
        map.put("trackId", trackId);
        map.put("partCode", project.getString("partCode"));
        String[] catalog = stepsOfTrack(trackId);
        List<String> finished = new ArrayList<>();
        for (GenericValue step : EntityQuery.use(delegator).from("MfgDevTrackStep")
                .where("projectCode", projectCode).queryList()) {
            if (stepFinished(step.getString("statusId"))) {
                finished.add(step.getString("stepId"));
            }
        }
        map.put("stepTotal", Integer.valueOf(catalog.length));
        map.put("stepDone", Integer.valueOf(finished.size()));
        map.put("currentStepId", firstOpenStep(trackId, finished));
        return map;
    }

    private static Map<String, Object> toTrackStepMap(GenericValue step) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("stepId", step.getString("stepId"));
        map.put("statusId", step.getString("statusId"));
        map.put("plannedDate", step.get("plannedDate"));
        map.put("doneDate", step.get("doneDate"));
        map.put("ownerName", step.getString("ownerName"));
        map.put("vendorName", step.getString("vendorName"));
        map.put("amount", step.getDouble("amount"));
        map.put("qty", step.getDouble("qty"));
        map.put("referenceNo", step.getString("referenceNo"));
        map.put("lotId", step.getString("lotId"));
        map.put("note", step.getString("note"));
        return map;
    }

    private static Map<String, Object> toEcoMap(GenericValue eco) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("ecoId", eco.getString("ecoId"));
        map.put("productCode", eco.getString("productCode"));
        map.put("projectCode", eco.getString("projectCode"));
        map.put("ecoTypeId", eco.getString("ecoTypeId"));
        map.put("reason", eco.getString("reason"));
        map.put("changeSummary", eco.getString("changeSummary"));
        map.put("statusId", eco.getString("statusId"));
        map.put("fromBomVersionId", eco.getString("fromBomVersionId"));
        map.put("toBomVersionId", eco.getString("toBomVersionId"));
        map.put("requestedBy", eco.getString("requestedBy"));
        map.put("approvedBy", eco.getString("approvedBy"));
        map.put("requestedDate", eco.get("requestedDate"));
        map.put("effectiveDate", eco.get("effectiveDate"));
        map.put("note", eco.getString("note"));
        return map;
    }

    /**
     * Creates or updates a development project.
     *
     * @param dctx the dispatch context
     * @param context service context, see {@code servicedef/services.xml#mfgDevProjectSave}
     * @return the service result containing {@code projectCode}
     */
    public static Map<String, Object> devProjectSave(DispatchContext dctx, Map<String, ? extends Object> context) {
        Delegator delegator = dctx.getDelegator();
        String projectCode = toText(context.get("projectCode"));
        String projectName = toText(context.get("projectName"));
        String stageId = toText(context.get("stageId"));
        String statusId = toText(context.get("statusId"));
        String trackId = toText(context.get("trackId"));
        if (stageId != null && !isKnownDevStage(stageId)) {
            return ServiceUtil.returnError("Unknown development stage [" + stageId + "]");
        }
        if (statusId != null && !isKnownDevStatus(statusId)) {
            return ServiceUtil.returnError("Unknown project status [" + statusId + "]");
        }
        if (trackId != null && !isKnownTrack(trackId)) {
            return ServiceUtil.returnError("Unknown development track [" + trackId + "]");
        }
        try {
            GenericValue project;
            String isNew = "N";
            if (projectCode == null) {
                if (projectName == null) {
                    return ServiceUtil.returnError("projectName is required for a new project");
                }
                projectCode = nextDevProjectId(delegator, LocalDate.now());
                project = delegator.makeValue("MfgDevProject");
                project.set("projectCode", projectCode);
                project.set("stageId", stageId == null ? DEV_STAGES[0] : stageId);
                project.set("statusId", statusId == null ? DEV_ACTIVE : statusId);
                isNew = "Y";
            } else {
                project = EntityQuery.use(delegator).from("MfgDevProject")
                        .where("projectCode", projectCode).queryOne();
                if (project == null) {
                    project = delegator.makeValue("MfgDevProject");
                    project.set("projectCode", projectCode);
                    project.set("stageId", stageId == null ? DEV_STAGES[0] : stageId);
                    project.set("statusId", statusId == null ? DEV_ACTIVE : statusId);
                    isNew = "Y";
                } else {
                    if (stageId != null) {
                        project.set("stageId", stageId);
                    }
                    if (statusId != null) {
                        project.set("statusId", statusId);
                    }
                }
            }
            if (projectName != null) {
                project.set("projectName", projectName);
            }
            if (context.containsKey("productCode")) {
                project.set("productCode", toText(context.get("productCode")));
            }
            if (trackId != null) {
                project.set("trackId", trackId);
            }
            if (context.containsKey("partCode")) {
                project.set("partCode", toText(context.get("partCode")));
            }
            if (context.containsKey("ownerName")) {
                project.set("ownerName", toText(context.get("ownerName")));
            }
            if (context.containsKey("targetCost")) {
                project.set("targetCost", Double.valueOf(toDouble(context.get("targetCost"), 0.0)));
            }
            if (context.containsKey("targetPrice")) {
                project.set("targetPrice", Double.valueOf(toDouble(context.get("targetPrice"), 0.0)));
            }
            if (context.containsKey("plannedLaunchDate")) {
                project.set("plannedLaunchDate", parseDate(context.get("plannedLaunchDate")));
            }
            if (context.containsKey("actualLaunchDate")) {
                project.set("actualLaunchDate", parseDate(context.get("actualLaunchDate")));
            }
            if (context.containsKey("note")) {
                project.set("note", toText(context.get("note")));
            }
            delegator.createOrStore(project);

            Map<String, Object> result = ServiceUtil.returnSuccess();
            result.put("projectCode", projectCode);
            result.put("isNew", isNew);
            return result;
        } catch (GenericEntityException e) {
            return ServiceUtil.returnError("Could not save the development project: " + e.getMessage());
        }
    }

    /**
     * Lists development projects, newest first.
     *
     * @param dctx the dispatch context
     * @param context service context, see {@code servicedef/services.xml#mfgDevProjectList}
     * @return the service result containing {@code projects}
     */
    public static Map<String, Object> devProjectList(DispatchContext dctx, Map<String, ? extends Object> context) {
        Delegator delegator = dctx.getDelegator();
        String stageId = toText(context.get("stageId"));
        String statusId = toText(context.get("statusId"));
        try {
            List<GenericValue> projects = EntityQuery.use(delegator).from("MfgDevProject")
                    .orderBy("-createdStamp").queryList();
            List<Map<String, Object>> list = new ArrayList<>(projects.size());
            for (GenericValue project : projects) {
                if (stageId != null && !stageId.equals(project.getString("stageId"))) {
                    continue;
                }
                if (statusId != null && !statusId.equals(project.getString("statusId"))) {
                    continue;
                }
                list.add(toDevProjectMap(delegator, project));
            }
            List<Map<String, Object>> stages = new ArrayList<>(DEV_STAGES.length);
            for (String stage : DEV_STAGES) {
                Map<String, Object> map = new LinkedHashMap<>();
                map.put("stageId", stage);
                map.put("stageIndex", Integer.valueOf(devStageIndex(stage)));
                stages.add(map);
            }
            Map<String, Object> result = ServiceUtil.returnSuccess();
            result.put("stages", stages);
            result.put("stageId", stageId);
            result.put("statusId", statusId);
            putPage(result, "projects",
                    filterByKeyword(list, toText(context.get("search")), "projectCode", "projectName",
                            "productCode", "ownerName", "stageId", "statusId"),
                    pageOf(context), pageSizeOf(context), toText(context.get("search")));
            return result;
        } catch (GenericEntityException e) {
            return ServiceUtil.returnError("Could not read the development projects: " + e.getMessage());
        }
    }

    /**
     * Returns one development project with its gate reviews and change orders.
     *
     * @param dctx the dispatch context
     * @param context service context, see {@code servicedef/services.xml#mfgDevProjectDetail}
     * @return the service result containing {@code project}, {@code reviews} and {@code ecos}
     */
    public static Map<String, Object> devProjectDetail(DispatchContext dctx, Map<String, ? extends Object> context) {
        Delegator delegator = dctx.getDelegator();
        String projectCode = toText(context.get("projectCode"));
        if (projectCode == null) {
            Map<String, Object> empty = ServiceUtil.returnSuccess();
            empty.put("reviews", new ArrayList<Map<String, Object>>());
            empty.put("ecos", new ArrayList<Map<String, Object>>());
            return empty;
        }
        try {
            GenericValue project = EntityQuery.use(delegator).from("MfgDevProject")
                    .where("projectCode", projectCode).queryOne();
            if (project == null) {
                return ServiceUtil.returnError("Unknown development project [" + projectCode + "]");
            }
            List<GenericValue> stored = EntityQuery.use(delegator).from("MfgDevStageReview")
                    .where("projectCode", projectCode).queryList();
            Map<String, GenericValue> byStage = new LinkedHashMap<>();
            for (GenericValue review : stored) {
                byStage.put(review.getString("stageId"), review);
            }
            List<Map<String, Object>> reviews = new ArrayList<>(DEV_STAGES.length);
            for (String stage : DEV_STAGES) {
                GenericValue review = byStage.get(stage);
                Map<String, Object> map = new LinkedHashMap<>();
                map.put("stageId", stage);
                map.put("stageIndex", Integer.valueOf(devStageIndex(stage)));
                map.put("plannedDate", review == null ? null : review.get("plannedDate"));
                map.put("doneDate", review == null ? null : review.get("doneDate"));
                map.put("resultId", review == null ? null : review.getString("resultId"));
                map.put("reviewer", review == null ? null : review.getString("reviewer"));
                map.put("note", review == null ? null : review.getString("note"));
                reviews.add(map);
            }
            List<Map<String, Object>> ecos = new ArrayList<>();
            for (GenericValue eco : EntityQuery.use(delegator).from("MfgDevEco")
                    .where("projectCode", projectCode).orderBy("-createdStamp").queryList()) {
                ecos.add(toEcoMap(eco));
            }
            String stageId = project.getString("stageId");
            Map<String, Object> projectMap = toDevProjectMap(delegator, project);
            projectMap.put("nextStageId", nextDevStage(stageId));

            // the steps of the track, with the stored state merged in, in catalog order
            String trackId = project.getString("trackId");
            Map<String, GenericValue> stepByStepId = new LinkedHashMap<>();
            for (GenericValue step : EntityQuery.use(delegator).from("MfgDevTrackStep")
                    .where("projectCode", projectCode).queryList()) {
                stepByStepId.put(step.getString("stepId"), step);
            }
            List<Map<String, Object>> steps = new ArrayList<>();
            String[] catalog = stepsOfTrack(trackId);
            for (int i = 0; i < catalog.length; i++) {
                GenericValue storedStep = stepByStepId.get(catalog[i]);
                Map<String, Object> map;
                if (storedStep == null) {
                    map = new LinkedHashMap<>();
                    map.put("stepId", catalog[i]);
                    map.put("statusId", STEP_TODO);
                    map.put("plannedDate", null);
                    map.put("doneDate", null);
                    map.put("ownerName", null);
                    map.put("vendorName", null);
                    map.put("amount", null);
                    map.put("qty", null);
                    map.put("referenceNo", null);
                    map.put("lotId", null);
                    map.put("note", null);
                } else {
                    map = toTrackStepMap(storedStep);
                }
                map.put("stepIndex", Integer.valueOf(i));
                steps.add(map);
            }

            // open issues per step, so the step list can show where the problems are
            Map<String, Integer> openIssuesByStep = new LinkedHashMap<>();
            for (GenericValue issue : EntityQuery.use(delegator).from("MfgDevStepIssue")
                    .where("projectCode", projectCode).queryList()) {
                if (!isOpenIssue(issue.getString("statusId"))) {
                    continue;
                }
                String issueStep = issue.getString("stepId");
                String key = issueStep == null ? "" : issueStep;
                Integer current = openIssuesByStep.get(key);
                openIssuesByStep.put(key, Integer.valueOf(current == null ? 1 : current.intValue() + 1));
            }
            for (Map<String, Object> stepRow : steps) {
                Integer count = openIssuesByStep.get(String.valueOf(stepRow.get("stepId")));
                stepRow.put("openIssueCount", Integer.valueOf(count == null ? 0 : count.intValue()));
            }

            Map<String, Object> result = ServiceUtil.returnSuccess();
            result.put("project", projectMap);
            result.put("reviews", reviews);
            result.put("ecos", ecos);
            result.put("steps", steps);
            result.put("trackId", trackId);
            result.put("stageIndex", Integer.valueOf(devStageIndex(stageId)));
            return result;        } catch (GenericEntityException e) {
            return ServiceUtil.returnError("Could not read the development project: " + e.getMessage());
        }
    }

    /**
     * Records the gate review of one stage. A pass or a conditional pass moves the project to the
     * next stage of the ladder.
     *
     * @param dctx the dispatch context
     * @param context service context, see {@code servicedef/services.xml#mfgDevStageSave}
     * @return the service result containing {@code stageId} and the new {@code projectStageId}
     */
    public static Map<String, Object> devStageSave(DispatchContext dctx, Map<String, ? extends Object> context) {
        Delegator delegator = dctx.getDelegator();
        String projectCode = toText(context.get("projectCode"));
        String stageId = toText(context.get("stageId"));
        String resultId = toText(context.get("resultId"));
        if (projectCode == null) {
            return ServiceUtil.returnError("projectCode is required");
        }
        if (stageId == null || !isKnownDevStage(stageId)) {
            return ServiceUtil.returnError("Unknown development stage [" + stageId + "]");
        }
        if (resultId != null && !isKnownReviewResult(resultId)) {
            return ServiceUtil.returnError("Unknown review result [" + resultId + "]");
        }
        try {
            GenericValue project = EntityQuery.use(delegator).from("MfgDevProject")
                    .where("projectCode", projectCode).queryOne();
            if (project == null) {
                return ServiceUtil.returnError("Unknown development project [" + projectCode + "]");
            }
            GenericValue review = delegator.makeValue("MfgDevStageReview");
            review.set("projectCode", projectCode);
            review.set("stageId", stageId);
            review.set("plannedDate", parseDate(context.get("plannedDate")));
            review.set("doneDate", parseDate(context.get("doneDate")));
            review.set("resultId", resultId);
            review.set("reviewer", toText(context.get("reviewer")));
            review.set("note", toText(context.get("note")));
            delegator.createOrStore(review);

            String projectStageId = project.getString("stageId");
            String next = nextDevStage(stageId);
            if (stageAdvancesOn(resultId) && next != null
                    && devStageIndex(next) > devStageIndex(projectStageId)) {
                projectStageId = next;
                project.set("stageId", next);
                if (DEV_STAGES[DEV_STAGES.length - 1].equals(next)) {
                    if (project.get("actualLaunchDate") == null) {
                        project.set("actualLaunchDate", java.sql.Date.valueOf(LocalDate.now()));
                    }
                    project.set("statusId", DEV_DONE);
                }
                project.store();
            }
            Map<String, Object> result = ServiceUtil.returnSuccess();
            result.put("projectCode", projectCode);
            result.put("stageId", stageId);
            result.put("projectStageId", projectStageId);
            return result;
        } catch (GenericEntityException e) {
            return ServiceUtil.returnError("Could not save the stage review: " + e.getMessage());
        }
    }

    /**
     * Loads the project and checks that the step belongs to its track.
     *
     * @param delegator the delegator
     * @param projectCode the project
     * @param stepId the step
     * @return the project, or {@code null} when the project is unknown or the step does not fit it
     * @throws GenericEntityException when the project cannot be read
     */
    private static GenericValue projectOfStep(Delegator delegator, String projectCode, String stepId)
            throws GenericEntityException {
        GenericValue project = EntityQuery.use(delegator).from("MfgDevProject")
                .where("projectCode", projectCode).queryOne();
        if (project == null) {
            return null;
        }
        return isKnownStep(project.getString("trackId"), stepId) ? project : null;
    }

    /**
     * Creates the material lot of the enclosure when its stock in step is finished and no lot was
     * created before, so that the same step never produces two lots.
     *
     * @param delegator the delegator
     * @param project the project the step belongs to
     * @param step the step, already carrying status, quantity and supplier
     * @return an error message for the user, {@code null} when everything is fine
     * @throws GenericEntityException when the lot cannot be created
     */
    private static String createStockInLot(Delegator delegator, GenericValue project, GenericValue step)
            throws GenericEntityException {
        if (!TRACK_ENCLOSURE.equals(project.getString("trackId"))
                || !STEP_ENCLOSURE_STOCK_IN.equals(step.getString("stepId"))
                || !STEP_DONE.equals(step.getString("statusId"))
                || toText(step.getString("lotId")) != null) {
            return null;
        }
        String partCode = toText(project.getString("partCode"));
        if (partCode == null) {
            return "Set the part code of the project first, the stock in step creates a material lot for it";
        }
        Double qty = step.getDouble("qty");
        if (qty == null || qty.doubleValue() <= 0) {
            return "Set the quantity of the stock in step first, it becomes the received quantity of the lot";
        }
        String lotId = nextLotId(delegator, LocalDate.now());
        GenericValue lot = delegator.makeValue("MfgMaterialLot");
        lot.set("lotId", lotId);
        lot.set("componentCode", partCode);
        lot.set("componentName", partCode);
        lot.set("supplierName", step.getString("vendorName"));
        lot.set("supplierLot", step.getString("referenceNo"));
        lot.set("receivedQty", qty);
        lot.set("receivedDate", java.sql.Date.valueOf(LocalDate.now()));
        lot.set("note", "Enclosure stock in of project " + project.getString("projectCode"));
        delegator.create(lot);
        recordMovement(delegator, lot, MOVE_RECEIPT, qty.doubleValue(), null, null, null,
                step.getString("referenceNo"), null,
                Timestamp.valueOf(LocalDate.now().atStartOfDay()), "外壳入库");
        recalcLotStock(delegator, lot);
        step.set("lotId", lotId);
        return null;
    }

    /**
     * Writes the details of one development track step. An empty value clears the field, the
     * status is only touched when it is given.
     *
     * @param dctx the dispatch context
     * @param context service context, see {@code servicedef/services.xml#mfgDevStepSave}
     * @return the service result containing {@code stepId} and the {@code lotId} when one was created
     */
    public static Map<String, Object> devStepSave(DispatchContext dctx, Map<String, ? extends Object> context) {
        Delegator delegator = dctx.getDelegator();
        String projectCode = toText(context.get("projectCode"));
        String stepId = toText(context.get("stepId"));
        String statusId = toText(context.get("statusId"));
        if (projectCode == null || stepId == null) {
            return ServiceUtil.returnError("projectCode and stepId are required");
        }
        if (statusId != null && !isKnownStepStatus(statusId)) {
            return ServiceUtil.returnError("Unknown step status [" + statusId + "]");
        }
        try {
            GenericValue project = projectOfStep(delegator, projectCode, stepId);
            if (project == null) {
                return ServiceUtil.returnError("Step [" + stepId + "] does not belong to project ["
                        + projectCode + "], check the track of the project");
            }
            GenericValue step = EntityQuery.use(delegator).from("MfgDevTrackStep")
                    .where("projectCode", projectCode, "stepId", stepId).queryOne();
            if (step == null) {
                step = delegator.makeValue("MfgDevTrackStep");
                step.set("projectCode", projectCode);
                step.set("stepId", stepId);
                step.set("statusId", STEP_TODO);
            }
            step.set("plannedDate", parseDate(context.get("plannedDate")));
            step.set("doneDate", parseDate(context.get("doneDate")));
            step.set("ownerName", toText(context.get("ownerName")));
            step.set("vendorName", toText(context.get("vendorName")));
            step.set("amount", Double.valueOf(toDouble(context.get("amount"), 0.0)));
            step.set("qty", Double.valueOf(toDouble(context.get("qty"), 0.0)));
            step.set("referenceNo", toText(context.get("referenceNo")));
            step.set("note", toText(context.get("note")));
            if (statusId != null) {
                step.set("statusId", statusId);
            }
            String error = createStockInLot(delegator, project, step);
            if (error != null) {
                return ServiceUtil.returnError(error);
            }
            delegator.createOrStore(step);

            Map<String, Object> result = ServiceUtil.returnSuccess();
            result.put("projectCode", projectCode);
            result.put("stepId", stepId);
            result.put("statusId", step.getString("statusId"));
            result.put("lotId", step.getString("lotId"));
            return result;
        } catch (GenericEntityException e) {
            return ServiceUtil.returnError("Could not save the step: " + e.getMessage());
        }
    }

    /**
     * Changes only the status of one step, keeping every other field, and stamps the completion
     * date the first time it is finished.
     *
     * @param dctx the dispatch context
     * @param context service context, see {@code servicedef/services.xml#mfgDevStepStatus}
     * @return the service result containing {@code stepId} and the {@code lotId} when one was created
     */
    public static Map<String, Object> devStepStatus(DispatchContext dctx, Map<String, ? extends Object> context) {
        Delegator delegator = dctx.getDelegator();
        String projectCode = toText(context.get("projectCode"));
        String stepId = toText(context.get("stepId"));
        String statusId = toText(context.get("statusId"));
        if (projectCode == null || stepId == null || statusId == null) {
            return ServiceUtil.returnError("projectCode, stepId and statusId are required");
        }
        if (!isKnownStepStatus(statusId)) {
            return ServiceUtil.returnError("Unknown step status [" + statusId + "]");
        }
        try {
            GenericValue project = projectOfStep(delegator, projectCode, stepId);
            if (project == null) {
                return ServiceUtil.returnError("Step [" + stepId + "] does not belong to project ["
                        + projectCode + "], check the track of the project");
            }
            GenericValue step = EntityQuery.use(delegator).from("MfgDevTrackStep")
                    .where("projectCode", projectCode, "stepId", stepId).queryOne();
            if (step == null) {
                step = delegator.makeValue("MfgDevTrackStep");
                step.set("projectCode", projectCode);
                step.set("stepId", stepId);
            }
            step.set("statusId", statusId);
            if (STEP_DONE.equals(statusId) && step.get("doneDate") == null) {
                step.set("doneDate", java.sql.Date.valueOf(LocalDate.now()));
            }
            String error = createStockInLot(delegator, project, step);
            if (error != null) {
                return ServiceUtil.returnError(error);
            }
            delegator.createOrStore(step);

            Map<String, Object> result = ServiceUtil.returnSuccess();
            result.put("projectCode", projectCode);
            result.put("stepId", stepId);
            result.put("statusId", statusId);
            result.put("lotId", step.getString("lotId"));
            return result;
        } catch (GenericEntityException e) {
            return ServiceUtil.returnError("Could not change the step status: " + e.getMessage());
        }
    }

    /**
     * Raises or updates an issue found at one development step. The step may be left empty for a
     * project level problem, but a project must always be given.
     *
     * @param dctx the dispatch context
     * @param context service context, see {@code servicedef/services.xml#mfgDevStepIssueSave}
     * @return the service result containing {@code issueId} and {@code statusId}
     */
    public static Map<String, Object> devStepIssueSave(DispatchContext dctx, Map<String, ? extends Object> context) {
        Delegator delegator = dctx.getDelegator();
        String issueId = toText(context.get("issueId"));
        String projectCode = toText(context.get("projectCode"));
        String stepId = toText(context.get("stepId"));
        String severityId = toText(context.get("severityId"));
        String statusId = toText(context.get("statusId"));
        if (severityId != null && !isKnownIssueSeverity(severityId)) {
            return ServiceUtil.returnError("Unknown issue severity [" + severityId + "]");
        }
        if (statusId != null && !isKnownIssueStatus(statusId)) {
            return ServiceUtil.returnError("Unknown issue status [" + statusId + "]");
        }
        try {
            GenericValue issue;
            if (issueId == null) {
                if (projectCode == null) {
                    return ServiceUtil.returnError("projectCode is required for a new issue");
                }
                issueId = nextIssueId(delegator, LocalDate.now());
                String storedProject = projectCode;
                if (stepId != null && projectOfStep(delegator, projectCode, stepId) == null) {
                    return ServiceUtil.returnError("Step [" + stepId + "] does not belong to project ["
                            + projectCode + "], check the track of the project");
                }
                issue = delegator.makeValue("MfgDevStepIssue");
                issue.set("issueId", issueId);
                issue.set("projectCode", storedProject);
                issue.set("statusId", statusId == null ? ISSUE_OPEN : statusId);
                issue.set("foundDate", parseDate(context.get("foundDate")));
                if (issue.get("foundDate") == null) {
                    issue.set("foundDate", java.sql.Date.valueOf(LocalDate.now()));
                }
            } else {
                issue = EntityQuery.use(delegator).from("MfgDevStepIssue")
                        .where("issueId", issueId).queryOne();
                if (issue == null) {
                    issue = delegator.makeValue("MfgDevStepIssue");
                    issue.set("issueId", issueId);
                    if (projectCode == null) {
                        return ServiceUtil.returnError("projectCode is required for a new issue");
                    }
                    issue.set("projectCode", projectCode);
                    issue.set("statusId", statusId == null ? ISSUE_OPEN : statusId);
                    issue.set("foundDate", parseDate(context.get("foundDate")));
                    if (issue.get("foundDate") == null) {
                        issue.set("foundDate", java.sql.Date.valueOf(LocalDate.now()));
                    }
                } else {
                    String storedProject = issue.getString("projectCode");
                    String wantedStep = stepId;
                    if (wantedStep != null && !wantedStep.isEmpty()
                            && projectOfStep(delegator, storedProject, wantedStep) == null) {
                        return ServiceUtil.returnError("Step [" + wantedStep + "] does not belong to project ["
                                + storedProject + "], check the track of the project");
                    }
                    if (statusId != null) {
                        issue.set("statusId", statusId);
                    }
                }
            }
            if (stepId != null) {
                issue.set("stepId", stepId.isEmpty() ? null : stepId);
            }
            if (context.containsKey("title")) {
                issue.set("title", toText(context.get("title")));
            }
            if (context.containsKey("detail")) {
                issue.set("detail", toText(context.get("detail")));
            }
            if (severityId != null) {
                issue.set("severityId", severityId);
            }
            if (context.containsKey("ownerName")) {
                issue.set("ownerName", toText(context.get("ownerName")));
            }
            if (context.containsKey("dueDate")) {
                issue.set("dueDate", parseDate(context.get("dueDate")));
            }
            if (context.containsKey("resolution")) {
                issue.set("resolution", toText(context.get("resolution")));
            }
            if (ISSUE_CLOSED.equals(issue.getString("statusId")) && issue.get("closedDate") == null) {
                issue.set("closedDate", java.sql.Date.valueOf(LocalDate.now()));
            }
            if (!ISSUE_CLOSED.equals(issue.getString("statusId"))) {
                issue.set("closedDate", null);
            }
            if (issue.getString("severityId") == null) {
                issue.set("severityId", ISSUE_MEDIUM);
            }
            delegator.createOrStore(issue);

            Map<String, Object> result = ServiceUtil.returnSuccess();
            result.put("issueId", issueId);
            result.put("projectCode", issue.getString("projectCode"));
            result.put("statusId", issue.getString("statusId"));
            return result;
        } catch (GenericEntityException e) {
            return ServiceUtil.returnError("Could not save the issue: " + e.getMessage());
        }
    }

    /**
     * Changes only the status of one issue, used by the quick buttons: closing stamps the date,
     * reopening clears it.
     *
     * @param dctx the dispatch context
     * @param context service context, see {@code servicedef/services.xml#mfgDevStepIssueStatus}
     * @return the service result containing {@code issueId} and the new {@code statusId}
     */
    public static Map<String, Object> devStepIssueStatus(DispatchContext dctx, Map<String, ? extends Object> context) {
        Delegator delegator = dctx.getDelegator();
        String issueId = toText(context.get("issueId"));
        String statusId = toText(context.get("statusId"));
        if (issueId == null || statusId == null) {
            return ServiceUtil.returnError("issueId and statusId are required");
        }
        if (!isKnownIssueStatus(statusId)) {
            return ServiceUtil.returnError("Unknown issue status [" + statusId + "]");
        }
        try {
            GenericValue issue = EntityQuery.use(delegator).from("MfgDevStepIssue")
                    .where("issueId", issueId).queryOne();
            if (issue == null) {
                return ServiceUtil.returnError("Unknown issue [" + issueId + "]");
            }
            issue.set("statusId", statusId);
            if (ISSUE_CLOSED.equals(statusId)) {
                if (issue.get("closedDate") == null) {
                    issue.set("closedDate", java.sql.Date.valueOf(LocalDate.now()));
                }
                String resolution = toText(context.get("resolution"));
                if (resolution != null) {
                    issue.set("resolution", resolution);
                }
            } else {
                issue.set("closedDate", null);
            }
            delegator.store(issue);

            Map<String, Object> result = ServiceUtil.returnSuccess();
            result.put("issueId", issueId);
            result.put("statusId", statusId);
            result.put("closedDate", issue.get("closedDate"));
            return result;
        } catch (GenericEntityException e) {
            return ServiceUtil.returnError("Could not change the issue status: " + e.getMessage());
        }
    }

    /**
     * Lists the issues of one development project, open ones first and then the newest.
     *
     * @param dctx the dispatch context
     * @param context service context, see {@code servicedef/services.xml#mfgDevStepIssueList}
     * @return the service result with the issues and the open counts
     */
    public static Map<String, Object> devStepIssueList(DispatchContext dctx, Map<String, ? extends Object> context) {
        Delegator delegator = dctx.getDelegator();
        String projectCode = toText(context.get("projectCode"));
        String statusId = toText(context.get("statusId"));
        boolean openOnly = "Y".equalsIgnoreCase(toText(context.get("openOnly")));
        Map<String, Object> result = ServiceUtil.returnSuccess();
        List<Map<String, Object>> rows = new ArrayList<>();
        result.put("issues", rows);
        result.put("openCount", Integer.valueOf(0));
        result.put("totalCount", Integer.valueOf(0));
        result.put("highOpenCount", Integer.valueOf(0));
        if (projectCode == null) {
            return result;
        }
        try {
            EntityQuery query = EntityQuery.use(delegator).from("MfgDevStepIssue")
                    .where("projectCode", projectCode);
            if (statusId != null) {
                query = query.where("statusId", statusId);
            }
            List<GenericValue> stored = query.orderBy("-createdStamp").queryList();
            int open = 0;
            int high = 0;
            for (GenericValue issue : stored) {
                Map<String, Object> map = toIssueMap(issue);
                boolean closed = ISSUE_CLOSED.equals(issue.getString("statusId"));
                if (!closed) {
                    open++;
                    if (ISSUE_HIGH.equals(issue.getString("severityId"))) {
                        high++;
                    }
                }
                if (openOnly && closed) {
                    continue;
                }
                rows.add(map);
            }
            // open first, then high severity, then the newest
            rows.sort(new Comparator<Map<String, Object>>() {
                @Override
                public int compare(Map<String, Object> left, Map<String, Object> right) {
                    int byStatus = Integer.valueOf(statusRank((String) left.get("statusId")))
                            .compareTo(Integer.valueOf(statusRank((String) right.get("statusId"))));
                    if (byStatus != 0) {
                        return byStatus;
                    }
                    int bySeverity = Integer.valueOf(severityRank((String) right.get("severityId")))
                            .compareTo(Integer.valueOf(severityRank((String) left.get("severityId"))));
                    if (bySeverity != 0) {
                        return bySeverity;
                    }
                    Object leftCreated = left.get("createdStamp");
                    Object rightCreated = right.get("createdStamp");
                    String leftText = leftCreated == null ? "" : leftCreated.toString();
                    String rightText = rightCreated == null ? "" : rightCreated.toString();
                    return rightText.compareTo(leftText);
                }
            });
            result.put("issues", rows);
            result.put("openCount", Integer.valueOf(open));
            result.put("totalCount", Integer.valueOf(stored.size()));
            result.put("highOpenCount", Integer.valueOf(high));
            return result;
        } catch (GenericEntityException e) {
            return ServiceUtil.returnError("Could not read the issues: " + e.getMessage());
        }
    }

    /**
     * Records the outsourced processing fee of one work order.
     *
     * @param dctx the dispatch context
     * @param context service context, see {@code servicedef/services.xml#mfgWorkOrderOutsourceSave}
     * @return the service result containing {@code workOrderId} and the stored amount
     */
    public static Map<String, Object> workOrderOutsourceSave(DispatchContext dctx, Map<String, ? extends Object> context) {
        Delegator delegator = dctx.getDelegator();
        String workOrderId = toText(context.get("workOrderId"));
        if (workOrderId == null) {
            return ServiceUtil.returnError("workOrderId is required");
        }
        try {
            GenericValue order = EntityQuery.use(delegator).from("MfgWorkOrder")
                    .where("workOrderId", workOrderId).queryOne();
            if (order == null) {
                return ServiceUtil.returnError("Unknown work order [" + workOrderId + "]");
            }
            double amount = round2(toDouble(context.get("outsourceAmount"), 0.0));
            if (amount < 0.0) {
                return ServiceUtil.returnError("The outsourced processing fee cannot be negative");
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
     * Raises or updates an engineering change order.
     *
     * @param dctx the dispatch context
     * @param context service context, see {@code servicedef/services.xml#mfgDevEcoSave}
     * @return the service result containing {@code ecoId}
     */
    public static Map<String, Object> devEcoSave(DispatchContext dctx, Map<String, ? extends Object> context) {
        Delegator delegator = dctx.getDelegator();
        String ecoId = toText(context.get("ecoId"));
        String productCode = toText(context.get("productCode"));
        String ecoTypeId = toText(context.get("ecoTypeId"));
        if (productCode == null) {
            return ServiceUtil.returnError("productCode is required");
        }
        if (ecoTypeId != null && !isKnownEcoType(ecoTypeId)) {
            return ServiceUtil.returnError("Unknown change type [" + ecoTypeId + "]");
        }
        try {
            if (EntityQuery.use(delegator).from("MfgProduct").where("productCode", productCode).queryOne() == null) {
                return ServiceUtil.returnError("Unknown product model [" + productCode + "], create it first");
            }
            String toVersionId = toText(context.get("toBomVersionId"));
            if (toVersionId != null) {
                GenericValue toVersion = EntityQuery.use(delegator).from("MfgBomVersion")
                        .where("bomVersionId", toVersionId).queryOne();
                if (toVersion == null) {
                    return ServiceUtil.returnError("Unknown BOM version [" + toVersionId + "]");
                }
                if (!productCode.equals(toVersion.getString("productCode"))) {
                    return ServiceUtil.returnError("BOM version [" + toVersionId + "] belongs to another model");
                }
            }
            GenericValue eco;
            String isNew = "N";
            if (ecoId == null) {
                ecoId = nextEcoId(delegator, LocalDate.now());
                eco = delegator.makeValue("MfgDevEco");
                eco.set("ecoId", ecoId);
                eco.set("productCode", productCode);
                eco.set("statusId", ECO_DRAFT);
                eco.set("requestedDate", java.sql.Date.valueOf(LocalDate.now()));
                isNew = "Y";
            } else {
                eco = EntityQuery.use(delegator).from("MfgDevEco").where("ecoId", ecoId).queryOne();
                if (eco == null) {
                    eco = delegator.makeValue("MfgDevEco");
                    eco.set("ecoId", ecoId);
                    eco.set("productCode", productCode);
                    eco.set("statusId", ECO_DRAFT);
                    eco.set("requestedDate", java.sql.Date.valueOf(LocalDate.now()));
                    isNew = "Y";
                } else if (ECO_APPLIED.equals(eco.getString("statusId"))) {
                    return ServiceUtil.returnError("Change order [" + ecoId + "] is already applied");
                } else {
                    eco.set("productCode", productCode);
                }
            }
            if (ecoTypeId != null) {
                eco.set("ecoTypeId", ecoTypeId);
            }
            if (context.containsKey("projectCode")) {
                eco.set("projectCode", toText(context.get("projectCode")));
            }
            if (context.containsKey("reason")) {
                eco.set("reason", toText(context.get("reason")));
            }
            if (context.containsKey("changeSummary")) {
                eco.set("changeSummary", toText(context.get("changeSummary")));
            }
            if (context.containsKey("requestedBy")) {
                eco.set("requestedBy", toText(context.get("requestedBy")));
            }
            if (context.containsKey("approvedBy")) {
                eco.set("approvedBy", toText(context.get("approvedBy")));
            }
            if (context.containsKey("effectiveDate")) {
                eco.set("effectiveDate", parseDate(context.get("effectiveDate")));
            }
            if (context.containsKey("note")) {
                eco.set("note", toText(context.get("note")));
            }
            if (toVersionId != null) {
                eco.set("toBomVersionId", toVersionId);
            }
            String fromVersionId = toText(context.get("fromBomVersionId"));
            if (fromVersionId == null) {
                fromVersionId = toText(eco.getString("fromBomVersionId"));
            }
            if (fromVersionId == null) {
                fromVersionId = currentBomVersionId(delegator, productCode);
            }
            eco.set("fromBomVersionId", fromVersionId);
            delegator.createOrStore(eco);

            Map<String, Object> result = ServiceUtil.returnSuccess();
            result.put("ecoId", ecoId);
            result.put("isNew", isNew);
            return result;
        } catch (GenericEntityException e) {
            return ServiceUtil.returnError("Could not save the change order: " + e.getMessage());
        }
    }

    /**
     * Lists change orders, newest first.
     *
     * @param dctx the dispatch context
     * @param context service context, see {@code servicedef/services.xml#mfgDevEcoList}
     * @return the service result containing {@code ecos}
     */
    public static Map<String, Object> devEcoList(DispatchContext dctx, Map<String, ? extends Object> context) {
        Delegator delegator = dctx.getDelegator();
        String statusId = toText(context.get("statusId"));
        String productCode = toText(context.get("productCode"));
        try {
            List<GenericValue> ecos = EntityQuery.use(delegator).from("MfgDevEco")
                    .orderBy("-createdStamp").queryList();
            List<Map<String, Object>> list = new ArrayList<>(ecos.size());
            long open = 0;
            for (GenericValue eco : ecos) {
                String status = eco.getString("statusId");
                if (ECO_DRAFT.equals(status) || ECO_APPROVED.equals(status)) {
                    open++;
                }
                if (statusId != null && !statusId.equals(status)) {
                    continue;
                }
                if (productCode != null && !productCode.equals(eco.getString("productCode"))) {
                    continue;
                }
                list.add(toEcoMap(eco));
            }
            Map<String, Object> result = ServiceUtil.returnSuccess();
            result.put("statusId", statusId);
            result.put("openCount", Long.valueOf(open));
            putPage(result, "ecos",
                    filterByKeyword(list, toText(context.get("search")), "ecoId", "productCode", "projectCode",
                            "ecoTypeId", "reason", "statusId", "requestedBy"),
                    pageOf(context), pageSizeOf(context), toText(context.get("search")));
            return result;
        } catch (GenericEntityException e) {
            return ServiceUtil.returnError("Could not read the change orders: " + e.getMessage());
        }
    }

    /**
     * Returns one change order with the BOM versions it switches between.
     *
     * @param dctx the dispatch context
     * @param context service context, see {@code servicedef/services.xml#mfgDevEcoDetail}
     * @return the service result containing {@code eco}, {@code fromVersion}, {@code toVersion}
     */
    public static Map<String, Object> devEcoDetail(DispatchContext dctx, Map<String, ? extends Object> context) {
        Delegator delegator = dctx.getDelegator();
        String ecoId = toText(context.get("ecoId"));
        if (ecoId == null) {
            Map<String, Object> empty = ServiceUtil.returnSuccess();
            empty.put("versions", new ArrayList<Map<String, Object>>());
            return empty;
        }
        try {
            GenericValue eco = EntityQuery.use(delegator).from("MfgDevEco").where("ecoId", ecoId).queryOne();
            if (eco == null) {
                return ServiceUtil.returnError("Unknown change order [" + ecoId + "]");
            }
            String productCode = eco.getString("productCode");
            Map<String, Object> result = ServiceUtil.returnSuccess();
            result.put("eco", toEcoMap(eco));
            String fromId = toText(eco.getString("fromBomVersionId"));
            String toId = toText(eco.getString("toBomVersionId"));
            if (fromId != null) {
                GenericValue from = EntityQuery.use(delegator).from("MfgBomVersion")
                        .where("bomVersionId", fromId).queryOne();
                if (from != null) {
                    result.put("fromVersion", toBomVersionMap(delegator, from));
                }
            }
            if (toId != null) {
                GenericValue to = EntityQuery.use(delegator).from("MfgBomVersion")
                        .where("bomVersionId", toId).queryOne();
                if (to != null) {
                    result.put("toVersion", toBomVersionMap(delegator, to));
                }
            }
            List<Map<String, Object>> versions = new ArrayList<>();
            for (GenericValue version : bomVersionsOf(delegator, productCode)) {
                versions.add(toBomVersionMap(delegator, version));
            }
            result.put("versions", versions);
            return result;
        } catch (GenericEntityException e) {
            return ServiceUtil.returnError("Could not read the change order: " + e.getMessage());
        }
    }

    /**
     * Approves a draft change order.
     *
     * @param dctx the dispatch context
     * @param context service context, see {@code servicedef/services.xml#mfgDevEcoApprove}
     * @return the service result containing the new {@code statusId}
     */
    public static Map<String, Object> devEcoApprove(DispatchContext dctx, Map<String, ? extends Object> context) {
        Delegator delegator = dctx.getDelegator();
        String ecoId = toText(context.get("ecoId"));
        if (ecoId == null) {
            return ServiceUtil.returnError("ecoId is required");
        }
        try {
            GenericValue eco = EntityQuery.use(delegator).from("MfgDevEco").where("ecoId", ecoId).queryOne();
            if (eco == null) {
                return ServiceUtil.returnError("Unknown change order [" + ecoId + "]");
            }
            if (!ECO_DRAFT.equals(eco.getString("statusId"))) {
                return ServiceUtil.returnError("Only a draft change order can be approved, [" + ecoId
                        + "] is " + eco.getString("statusId"));
            }
            eco.set("statusId", ECO_APPROVED);
            eco.set("approvedBy", toText(context.get("approvedBy")));
            eco.store();
            Map<String, Object> result = ServiceUtil.returnSuccess();
            result.put("ecoId", ecoId);
            result.put("statusId", ECO_APPROVED);
            return result;
        } catch (GenericEntityException e) {
            return ServiceUtil.returnError("Could not approve the change order: " + e.getMessage());
        }
    }

    /**
     * Applies an approved change order: the model switches to the target BOM version when one is
     * given.
     *
     * @param dctx the dispatch context
     * @param context service context, see {@code servicedef/services.xml#mfgDevEcoApply}
     * @return the service result containing the new {@code statusId}
     */
    public static Map<String, Object> devEcoApply(DispatchContext dctx, Map<String, ? extends Object> context) {
        Delegator delegator = dctx.getDelegator();
        String ecoId = toText(context.get("ecoId"));
        if (ecoId == null) {
            return ServiceUtil.returnError("ecoId is required");
        }
        try {
            GenericValue eco = EntityQuery.use(delegator).from("MfgDevEco").where("ecoId", ecoId).queryOne();
            if (eco == null) {
                return ServiceUtil.returnError("Unknown change order [" + ecoId + "]");
            }
            String statusId = eco.getString("statusId");
            if (!ecoAppliable(statusId)) {
                return ServiceUtil.returnError("Only an approved change order can be applied, [" + ecoId
                        + "] is " + statusId);
            }
            String toVersionId = toText(eco.getString("toBomVersionId"));
            if (toVersionId != null) {
                GenericValue version = EntityQuery.use(delegator).from("MfgBomVersion")
                        .where("bomVersionId", toVersionId).queryOne();
                if (version == null) {
                    return ServiceUtil.returnError("Unknown BOM version [" + toVersionId + "]");
                }
                String productCode = eco.getString("productCode");
                GenericValue product = EntityQuery.use(delegator).from("MfgProduct")
                        .where("productCode", productCode).queryOne();
                for (GenericValue other : bomVersionsOf(delegator, productCode)) {
                    if (!toVersionId.equals(other.getString("bomVersionId"))
                            && BOM_RELEASED.equals(other.getString("statusId"))) {
                        other.set("statusId", BOM_OBSOLETE);
                        other.store();
                    }
                }
                version.set("statusId", BOM_RELEASED);
                version.set("effectiveDate", java.sql.Date.valueOf(LocalDate.now()));
                version.store();
                if (product != null) {
                    product.set("currentBomVersionId", toVersionId);
                    product.store();
                }
            }
            eco.set("statusId", ECO_APPLIED);
            eco.set("effectiveDate", java.sql.Date.valueOf(LocalDate.now()));
            eco.store();
            Map<String, Object> result = ServiceUtil.returnSuccess();
            result.put("ecoId", ecoId);
            result.put("statusId", ECO_APPLIED);
            result.put("bomVersionId", toVersionId);
            return result;
        } catch (GenericEntityException e) {
            return ServiceUtil.returnError("Could not apply the change order: " + e.getMessage());
        }
    }

    /**
     * Cancels a change order that has not been applied.
     *
     * @param dctx the dispatch context
     * @param context service context, see {@code servicedef/services.xml#mfgDevEcoCancel}
     * @return the service result containing the new {@code statusId}
     */
    public static Map<String, Object> devEcoCancel(DispatchContext dctx, Map<String, ? extends Object> context) {
        Delegator delegator = dctx.getDelegator();
        String ecoId = toText(context.get("ecoId"));
        if (ecoId == null) {
            return ServiceUtil.returnError("ecoId is required");
        }
        try {
            GenericValue eco = EntityQuery.use(delegator).from("MfgDevEco").where("ecoId", ecoId).queryOne();
            if (eco == null) {
                return ServiceUtil.returnError("Unknown change order [" + ecoId + "]");
            }
            if (ECO_APPLIED.equals(eco.getString("statusId"))) {
                return ServiceUtil.returnError("Change order [" + ecoId + "] is already applied, it cannot be cancelled");
            }
            eco.set("statusId", ECO_CANCELLED);
            eco.store();
            Map<String, Object> result = ServiceUtil.returnSuccess();
            result.put("ecoId", ecoId);
            result.put("statusId", ECO_CANCELLED);
            return result;
        } catch (GenericEntityException e) {
            return ServiceUtil.returnError("Could not cancel the change order: " + e.getMessage());
        }
    }
}