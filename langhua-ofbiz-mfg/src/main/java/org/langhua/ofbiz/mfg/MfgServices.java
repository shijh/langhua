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

import org.langhua.ofbiz.mfg.oir.OirOrders;

import java.sql.Timestamp;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.SimpleDateFormat;
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

import org.apache.ofbiz.base.util.Debug;
import org.apache.ofbiz.base.util.UtilGenerics;
import org.apache.ofbiz.base.util.UtilMisc;
import org.apache.ofbiz.common.login.LoginServices;
import org.apache.ofbiz.entity.Delegator;
import org.apache.ofbiz.entity.GenericEntityException;
import org.apache.ofbiz.entity.GenericValue;
import org.apache.ofbiz.entity.condition.EntityCondition;
import org.apache.ofbiz.entity.condition.EntityOperator;
import org.apache.ofbiz.entity.util.EntityQuery;
import org.apache.ofbiz.entity.util.EntityUtil;
import org.apache.ofbiz.entity.util.EntityUtilProperties;
import org.apache.ofbiz.security.Security;
import org.apache.ofbiz.service.DispatchContext;
import org.apache.ofbiz.service.GenericServiceException;
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

    /** Where the standard hours of a model came from: typed in on the product page. */
    public static final String HOURS_SOURCE_MANUAL = "MANUAL";
    /** Where the standard hours of a model came from: written back by the learning curve component. */
    public static final String HOURS_SOURCE_CURVE = "LEARNING_CURVE";

    /**
     * System property that switches the forced password change on or off; {@code Y} by default so a
     * fresh install cannot be used with the password that ships with OFBiz. Development instances
     * start with {@code N} so the page check scripts can keep logging in with the default account.
     */
    public static final String FORCE_PASSWORD_CHANGE_PROPERTY = "org.langhua.mfg.requirePasswordChange";

    /** The password of the {@code admin} account of a freshly loaded OFBiz database. */
    public static final String DEFAULT_PASSWORD = "ofbiz";

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

    /** A purchase receipt that counts: money, goods and reports all read the active ones. */
    public static final String RECEIPT_ACTIVE = "MFG_RCPT_ACTIVE";
    /**
     * A purchase receipt that was voided by mistake or was wrong: it stays as the record of what was
     * once typed (who, when, why), but it is left out of every total and its lots count as free again.
     */
    public static final String RECEIPT_VOID = "MFG_RCPT_VOID";

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
    /** A move between two locations of the same lot: written as a pair, one out and one in. */
    public static final String MOVE_TRANSFER = "MFG_MOVE_TRANSFER";
    /** Reservation of stock for a work order; it changes the reserved quantity, not the quantity on hand. */
    public static final String MOVE_RESERVE = "MFG_MOVE_RESERVE";
    /** Release of a reservation, booked when the material is issued or the order is dropped. */
    public static final String MOVE_RELEASE = "MFG_MOVE_RELEASE";

    /** Movement types that put material into stock, and the ones that take it out. */
    public static final String[] MOVE_IN_TYPES = {MOVE_RECEIPT, MOVE_RETURN};
    public static final String[] MOVE_OUT_TYPES = {MOVE_ISSUE, MOVE_SCRAP};

    /** Every movement type, in the order the ledger filter lists them. */
    public static final String[] MOVE_TYPES = {MOVE_RECEIPT, MOVE_ISSUE, MOVE_RETURN, MOVE_SCRAP,
        MOVE_COUNT, MOVE_ADJUST, MOVE_TRANSFER, MOVE_RESERVE, MOVE_RELEASE};

    /**
     * Why a quantity changed, taken from OFBiz's own list: the enumeration type {@code IID_REASON}
     * ("Inventory Item Detail Reason"). The movement type says which bucket the change lands in (报废,
     * 退料, 补入库, 调整); the reason says what really happened, which is what a stock keeper is asked
     * about. Keeping the same ids as OFBiz means the data can be compared with a standard OFBiz
     * install later on.
     */
    public static final String REASON_LOST = "VAR_LOST";
    public static final String REASON_STOLEN = "VAR_STOLEN";
    public static final String REASON_FOUND = "VAR_FOUND";
    public static final String REASON_DAMAGED = "VAR_DAMAGED";
    public static final String REASON_SAMPLE = "VAR_SAMPLE";
    public static final String REASON_INTEGR = "VAR_INTEGR";
    public static final String REASON_MISSHIP_ORDERED = "VAR_MISSHIP_ORDERED";
    public static final String REASON_MISSHIP_SHIPPED = "VAR_MISSHIP_SHIPPED";
    /** Every reason, in the order the form lists them (OFBiz sequence 01..08). */
    public static final String[] MOVE_REASONS = {REASON_LOST, REASON_STOLEN, REASON_FOUND,
        REASON_DAMAGED, REASON_SAMPLE, REASON_INTEGR, REASON_MISSHIP_ORDERED, REASON_MISSHIP_SHIPPED};

    /** What kind of place a stock location is. */
    public static final String LOC_WAREHOUSE = "MFG_LOC_WAREHOUSE";
    public static final String LOC_ZONE = "MFG_LOC_ZONE";
    public static final String LOC_BIN = "MFG_LOC_BIN";
    /** Every location type, in the order the lists show them. */
    public static final String[] LOC_TYPES = {LOC_WAREHOUSE, LOC_ZONE, LOC_BIN};
    /** Prefix of a transfer number, followed by {@code yyMMdd-NN}. */
    public static final String TRANSFER_PREFIX = "TR-";

    /** What an outsourced fee pays for. */
    public static final String OUTSRC_SMT = "MFG_OUTSRC_SMT";
    public static final String OUTSRC_TOOLING = "MFG_OUTSRC_TOOLING";
    public static final String OUTSRC_ASSEMBLY = "MFG_OUTSRC_ASSEMBLY";
    public static final String OUTSRC_TEST = "MFG_OUTSRC_TEST";
    public static final String OUTSRC_OTHER = "MFG_OUTSRC_OTHER";
    /** Every outsourced fee type, in the order the lists show them. */
    public static final String[] OUTSRC_TYPES = {OUTSRC_SMT, OUTSRC_TOOLING, OUTSRC_ASSEMBLY,
        OUTSRC_TEST, OUTSRC_OTHER};

    /** How an outsourced fee is priced: per board, per placement point, or as one amount. */
    public static final String OUTSRC_PER_UNIT = "MFG_OUTSRC_PER_UNIT";
    public static final String OUTSRC_PER_POINT = "MFG_OUTSRC_PER_POINT";
    public static final String OUTSRC_LUMP = "MFG_OUTSRC_LUMP";
    /** Every pricing basis, in the order the form lists them. */
    public static final String[] OUTSRC_BASES = {OUTSRC_PER_UNIT, OUTSRC_PER_POINT, OUTSRC_LUMP};

    /** What a stock lot holds: a purchased component, or the finished goods of one work order. */
    public static final String LOT_COMPONENT = "MFG_LOT_COMPONENT";
    public static final String LOT_PRODUCT = "MFG_LOT_PRODUCT";
    /** Every lot kind, in the order the filters list them. */
    public static final String[] LOT_KINDS = {LOT_COMPONENT, LOT_PRODUCT};
    /** Prefix of the finished goods lot of a work order: {@code FG-WO-yyMMdd-NN}. */
    public static final String PRODUCT_LOT_PREFIX = "FG-";

    /** Sales order: taken, waiting for production, shipped, closed or cancelled. */
    public static final String SO_OPEN = "MFG_SO_OPEN";
    public static final String SO_PLANNED = "MFG_SO_PLANNED";
    public static final String SO_SHIPPED = "MFG_SO_SHIPPED";
    public static final String SO_CLOSED = "MFG_SO_CLOSED";
    public static final String SO_CANCELLED = "MFG_SO_CANCELLED";
    /** Every sales order status, in the order the lists show them. */
    public static final String[] SO_STATUSES = {SO_OPEN, SO_PLANNED, SO_SHIPPED, SO_CLOSED, SO_CANCELLED};
    /** Prefix of a sales order number, followed by {@code yyMMdd-NN}. */
    public static final String SO_PREFIX = "SO-";

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

    /**
     * Longest BOM version identifier the {@code id} column holds. A model code may use all of it,
     * so the version number is what has to fit and a long code gets shortened.
     */
    static final int MAX_BOM_VERSION_ID = 20;

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

    static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyMMdd");
    private static final DateTimeFormatter MONTH_DAY = DateTimeFormatter.ofPattern("MM-dd");
    static final DateTimeFormatter FULL_DAY = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    /** Day and minute, for the audit list. */
    static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private MfgServices() {
    }

    /**
     * Security group new accounts are put in by default, and the one the group picker lists first.
     * It is the only group a fresh OFBiz database defines, so it is the only safe default.
     */
    public static final String ACCOUNT_ADMIN_GROUP = "SUPER";

    /** The {@code UserLogin.userLoginId} field is {@code id-ne}, so an account name cannot be longer. */
    static final int MAX_ACCOUNT_NAME = 20;

    /** Permission of the component, see {@code data/MfgSecurityPermissionSeedData.xml} for the roles. */
    public static final String PERM_VIEW = "MFG_VIEW";
    public static final String PERM_ADMIN = "MFG_ADMIN";
    public static final String PERM_PRODUCTION = "MFG_PRODUCTION";
    public static final String PERM_STOCK = "MFG_STOCK";
    public static final String PERM_ENG = "MFG_ENG";
    public static final String PERM_SALES = "MFG_SALES";
    public static final String PERM_COST = "MFG_COST";

    /** Every area permission, in the order the account page describes them. */
    public static final String[] AREA_PERMISSIONS = {PERM_PRODUCTION, PERM_STOCK, PERM_ENG, PERM_SALES, PERM_COST};

    /**
     * Tells the decorator which areas the logged in account may work in, so the navigation and the
     * cost blocks are hidden instead of failing with a permission error. Never fails.
     *
     * @param dctx the dispatch context
     * @param context service context, see {@code servicedef/services.xml#mfgPermissions}
     * @return the service result with {@code canView} / {@code canProduce} / {@code canStock} /
     *         {@code canEng} / {@code canSales} / {@code canCost} / {@code isAdmin} ({@code Y} / {@code N})
     */
    public static Map<String, Object> permissions(DispatchContext dctx, Map<String, ? extends Object> context) {
        GenericValue userLogin = (GenericValue) context.get("userLogin");
        Map<String, Object> result = ServiceUtil.returnSuccess();
        result.put("canView", yesNo(can(dctx, userLogin, PERM_VIEW)));
        result.put("canProduce", yesNo(can(dctx, userLogin, PERM_PRODUCTION)));
        result.put("canStock", yesNo(can(dctx, userLogin, PERM_STOCK)));
        result.put("canEng", yesNo(can(dctx, userLogin, PERM_ENG)));
        result.put("canSales", yesNo(can(dctx, userLogin, PERM_SALES)));
        result.put("canCost", yesNo(can(dctx, userLogin, PERM_COST)));
        result.put("isAdmin", yesNo(can(dctx, userLogin, PERM_ADMIN)));
        result.put("manager", yesNo(MfgAccountServices.accountAdminProblem(dctx, userLogin) == null));
        result.put("userLoginId", userLogin == null ? "" : userLogin.getString("userLoginId"));
        return result;
    }

    /**
     * Says whether the account holds one permission. {@code MFG_ADMIN} and the OFBiz administrator
     * permission pass every check, so a database whose administrator sits in {@code FULLADMIN}
     * (a demo database) behaves like a fresh one where it sits in {@code SUPER}.
     *
     * @param dctx the dispatch context
     * @param userLogin the logged in account, may be {@code null}
     * @param permission the permission to look for
     * @return {@code true} when the account may do it
     */
    static boolean can(DispatchContext dctx, GenericValue userLogin, String permission) {
        if (userLogin == null) {
            return false;
        }
        Security security = dctx.getSecurity();
        if (security == null) {
            // no security implementation configured: never lock the shop out of its own data
            return true;
        }
        if (security.hasPermission(permission, userLogin) || security.hasPermission(PERM_ADMIN, userLogin)) {
            return true;
        }
        return security.hasEntityPermission("PARTYMGR", "_UPDATE", userLogin);
    }

    /**
     * Checks the area permissions of an operation.
     *
     * @param dctx the dispatch context
     * @param context service context, must carry the logged in {@code userLogin}
     * @param permissions the permissions of which the account needs at least one
     * @return the message to show, or {@code null} when the account may do it
     */
    static String permissionProblem(DispatchContext dctx, Map<String, ? extends Object> context,
            String... permissions) {
        GenericValue userLogin = (GenericValue) context.get("userLogin");
        if (userLogin == null) {
            return "请先登录";
        }
        for (String permission : permissions) {
            if (can(dctx, userLogin, permission)) {
                return null;
            }
        }
        return "没有权限做这个操作（需要 " + String.join(" 或 ", permissions)
                + "）；请管理员在「用户账号」里调整你的角色";
    }

    /**
     * Turns a flag into the {@code Y} / {@code N} the templates compare against.
     *
     * @param value the flag
     * @return {@code Y} when true, {@code N} otherwise
     */
    private static String yesNo(boolean value) {
        return value ? "Y" : "N";
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
        boolean costDenied = !can(dctx, (GenericValue) context.get("userLogin"), PERM_COST);
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
            // the responsibility cost block is a cost view, not everybody may see it
            result.put("costDenied", costDenied ? "Y" : "N");
            return result;
        } catch (GenericEntityException e) {
            return ServiceUtil.returnError("Could not build the dashboard: " + e.getMessage());
        }
    }

    // ------------------------------------------------------------ cost

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
        java.sql.Date toDate = (java.sql.Date) MfgSupport.parseDate(context.get("toDate"));
        if (toDate == null) {
            toDate = java.sql.Date.valueOf(LocalDate.now());
        }
        java.sql.Date fromDate = (java.sql.Date) MfgSupport.parseDate(context.get("fromDate"));
        if (fromDate == null) {
            fromDate = java.sql.Date.valueOf(toDate.toLocalDate().minusDays(179));
        }
        String groupBy = MfgSupport.toText(context.get("groupBy"));
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
                String key = MfgSupport.periodKey(reportedDate, groupBy);
                double[] bucket = buckets.get(key);
                if (bucket == null) {
                    bucket = new double[3];
                    buckets.put(key, bucket);
                    ordersOfBucket.put(key, new LinkedHashSet<String>());
                }
                bucket[0] += MfgSupport.numberOrZero(report.get("qtyOk"));
                bucket[1] += MfgSupport.numberOrZero(report.get("qtyNg"));
                bucket[2] += MfgSupport.numberOrZero(report.get("workHours"));
                String workOrderId = report.getString("workOrderId");
                if (workOrderId != null) {
                    ordersOfBucket.get(key).add(workOrderId);
                }
                qtyOkTotal += MfgSupport.numberOrZero(report.get("qtyOk"));
                qtyNgTotal += MfgSupport.numberOrZero(report.get("qtyNg"));
                hoursTotal += MfgSupport.numberOrZero(report.get("workHours"));
            }
            List<Map<String, Object>> rows = new ArrayList<>(buckets.size());
            Map<String, Object> worst = null;
            for (Map.Entry<String, double[]> entry : buckets.entrySet()) {
                double[] bucket = entry.getValue();
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("period", entry.getKey());
                row.put("qtyOk", Double.valueOf(MfgSupport.round2(bucket[0])));
                row.put("qtyNg", Double.valueOf(MfgSupport.round2(bucket[1])));
                row.put("yieldRate", Double.valueOf(MfgSupport.yieldRate(bucket[0], bucket[1])));
                row.put("hours", Double.valueOf(MfgSupport.round2(bucket[2])));
                row.put("hoursPerUnit", Double.valueOf(MfgSupport.hoursPerUnit(bucket[2], bucket[0])));
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
            result.put("hoursTotal", Double.valueOf(MfgSupport.round2(hoursTotal)));
            result.put("yieldRate", Double.valueOf(MfgSupport.yieldRate(qtyOkTotal, qtyNgTotal)));
            result.put("hoursPerUnit", Double.valueOf(MfgSupport.hoursPerUnit(hoursTotal, qtyOkTotal)));
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
     * Purchase spend per period: how much goods were bought, how much freight was paid and what
     * share the freight has of the goods value. The freight is a period cost, so it is reported
     * next to the goods value and never inside it.
     *
     * @param dctx the dispatch context
     * @param context service context, see {@code servicedef/services.xml#mfgPurchaseFreightTrend}
     * @return the service result containing {@code rows} and the totals of the period
     */
    public static Map<String, Object> purchaseFreightTrend(DispatchContext dctx, Map<String, ? extends Object> context) {
        Delegator delegator = dctx.getDelegator();
        java.sql.Date toDate = (java.sql.Date) MfgSupport.parseDate(context.get("toDate"));
        if (toDate == null) {
            toDate = java.sql.Date.valueOf(LocalDate.now());
        }
        java.sql.Date fromDate = (java.sql.Date) MfgSupport.parseDate(context.get("fromDate"));
        if (fromDate == null) {
            fromDate = java.sql.Date.valueOf(toDate.toLocalDate().minusDays(179));
        }
        String groupBy = MfgSupport.toText(context.get("groupBy"));
        groupBy = groupBy == null ? "MONTH" : groupBy.toUpperCase(Locale.ROOT);
        if (!"DAY".equals(groupBy) && !"WEEK".equals(groupBy) && !"MONTH".equals(groupBy)) {
            groupBy = "MONTH";
        }
        Map<String, Object> result = ServiceUtil.returnSuccess();
        result.put("fromDate", fromDate);
        result.put("toDate", toDate);
        result.put("groupBy", groupBy);
        result.put("rows", new ArrayList<Map<String, Object>>());
        result.put("receiptCount", Integer.valueOf(0));
        result.put("goodsTotal", Double.valueOf(0.0));
        result.put("freightTotal", Double.valueOf(0.0));
        result.put("spendTotal", Double.valueOf(0.0));
        result.put("freightShare", Double.valueOf(0.0));
        result.put("rowCount", Integer.valueOf(0));
        try {
            Map<String, List<Map<String, Object>>> linesOf = new LinkedHashMap<>();
            for (GenericValue line : EntityQuery.use(delegator).from("MfgPurchaseReceiptLine").queryList()) {
                String id = line.getString("receiptId");
                List<Map<String, Object>> lines = linesOf.get(id);
                if (lines == null) {
                    lines = new ArrayList<>();
                    linesOf.put(id, lines);
                }
                lines.add(toReceiptLineMap(line));
            }
            Map<String, double[]> buckets = new java.util.TreeMap<>();
            for (GenericValue receipt : EntityQuery.use(delegator).from("MfgPurchaseReceipt")
                    .orderBy("receiptDate").queryList()) {
                // a voided receipt was never really bought: it stays out of the freight report
                if (RECEIPT_VOID.equals(statusOfReceipt(receipt))) {
                    continue;
                }
                java.sql.Date day = receipt.getDate("receiptDate");
                if (day == null && receipt.getTimestamp("createdStamp") != null) {
                    day = new java.sql.Date(receipt.getTimestamp("createdStamp").getTime());
                }
                if (day == null || day.before(fromDate) || day.after(toDate)) {
                    continue;
                }
                List<Map<String, Object>> lines = linesOf.get(receipt.getString("receiptId"));
                Map<String, Object> summary = receiptSummary(
                        lines == null ? new ArrayList<Map<String, Object>>() : lines,
                        MfgSupport.numberOrZero(receipt.get("freightAmount")),
                        MfgSupport.numberOrZero(receipt.get("discountAmount")));
                String key = MfgSupport.periodKey(day, groupBy);
                double[] bucket = buckets.get(key);
                if (bucket == null) {
                    bucket = new double[4];
                    buckets.put(key, bucket);
                }
                bucket[0] += 1.0;
                bucket[1] += MfgSupport.numberOrZero(summary.get("goodsTotal"));
                bucket[2] += MfgSupport.numberOrZero(summary.get("freightTotal"));
                // a red packet comes off the period spend too, otherwise the report would claim more
                // money was spent than really left the bank
                bucket[3] += MfgSupport.numberOrZero(summary.get("discountTotal"));
            }
            List<Map<String, Object>> rows = new ArrayList<>(buckets.size());
            double receipts = 0.0;
            double goods = 0.0;
            double freight = 0.0;
            double discount = 0.0;
            for (Map.Entry<String, double[]> entry : buckets.entrySet()) {
                double[] bucket = entry.getValue();
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("period", entry.getKey());
                row.put("receiptCount", Integer.valueOf((int) Math.round(bucket[0])));
                row.put("goodsTotal", Double.valueOf(MfgSupport.round2(bucket[1])));
                row.put("freightTotal", Double.valueOf(MfgSupport.round2(bucket[2])));
                row.put("discountTotal", Double.valueOf(MfgSupport.round2(bucket[3])));
                row.put("spendTotal", Double.valueOf(MfgSupport.round2(bucket[1] + bucket[2] - bucket[3])));
                row.put("freightShare", Double.valueOf(bucket[1] <= 0.0
                        ? 0.0 : MfgSupport.round2(bucket[2] / bucket[1] * 100.0)));
                rows.add(row);
                receipts += bucket[0];
                goods += bucket[1];
                freight += bucket[2];
                discount += bucket[3];
            }
            result.put("rows", rows);
            result.put("receiptCount", Integer.valueOf((int) Math.round(receipts)));
            result.put("goodsTotal", Double.valueOf(MfgSupport.round2(goods)));
            result.put("freightTotal", Double.valueOf(MfgSupport.round2(freight)));
            result.put("discountTotal", Double.valueOf(MfgSupport.round2(discount)));
            result.put("spendTotal", Double.valueOf(MfgSupport.round2(goods + freight - discount)));
            result.put("freightShare", Double.valueOf(goods <= 0.0 ? 0.0 : MfgSupport.round2(freight / goods * 100.0)));
            result.put("rowCount", Integer.valueOf(rows.size()));
            return result;
        } catch (GenericEntityException e) {
            return ServiceUtil.returnError("Could not build the freight report: " + e.getMessage());
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
        String denied = permissionProblem(dctx, context, PERM_STOCK);
        if (denied != null) {
            return ServiceUtil.returnError(denied);
        }
        Delegator delegator = dctx.getDelegator();
        String componentCode = MfgSupport.toText(context.get("componentCode"));
        if (componentCode == null) {
            return ServiceUtil.returnError("componentCode is required");
        }
        double receivedQty = MfgSupport.toDouble(context.get("receivedQty"), 0.0);
        if (receivedQty <= 0.0) {
            return ServiceUtil.returnError("receivedQty must be greater than zero");
        }
        try {
            String lotId = MfgSupport.toText(context.get("lotId"));
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
            lot.set("componentName", MfgSupport.toText(context.get("componentName")));
            lot.set("supplierName", MfgSupport.toText(context.get("supplierName")));
            lot.set("supplierLot", MfgSupport.toText(context.get("supplierLot")));
            if (isNew) {
                lot.set("receivedQty", Double.valueOf(receivedQty));
            }
            Object receivedDate = MfgSupport.parseDate(context.get("receivedDate"));
            lot.set("receivedDate", receivedDate == null
                    ? java.sql.Date.valueOf(LocalDate.now()) : receivedDate);
            Double amount = MfgSupport.optionalAmount(context.get("amount"));
            if (amount != null) {
                // the amount is exact, the unit price column only keeps two decimals
                lot.set("receivedAmount", amount);
                lot.set("unitPrice", Double.valueOf(MfgSupport.round2(amount.doubleValue() / receivedQty)));
            } else {
                lot.set("unitPrice", MfgSupport.optionalAmount(context.get("unitPrice")));
            }
            String inspectStatus = normalizeInspectStatus(context.get("inspectStatusId"));
            if (inspectStatus == null) {
                return ServiceUtil.returnError("Unknown inspection status [" + context.get("inspectStatusId")
                        + "], expected " + String.join(" / ", INSPECT_STATUSES));
            }
            lot.set("inspectStatusId", inspectStatus);
            lot.set("note", MfgSupport.toText(context.get("note")));
            delegator.createOrStore(lot);
            if (isNew) {
                MfgStockLedger.recordMovement(delegator, lot, MOVE_RECEIPT, receivedQty, lot.get("unitPrice"), null, null,
                        lot.getString("supplierLot"), null,
                        Timestamp.valueOf(lot.getDate("receivedDate").toLocalDate().atStartOfDay()),
                        "到货入库", MfgSupport.toText(context.get("locationId")));
            }
            MfgStockLedger.recalcLotStock(delegator, lot);

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
        String denied = permissionProblem(dctx, context, PERM_STOCK);
        if (denied != null) {
            return ServiceUtil.returnError(denied);
        }
        Delegator delegator = dctx.getDelegator();
        String lotId = MfgSupport.toText(context.get("lotId"));
        if (lotId == null) {
            return ServiceUtil.returnError("lotId is required");
        }
        String status = normalizeInspectStatus(context.get("inspectStatusId"));
        if (status == null) {
            return ServiceUtil.returnError("Unknown inspection status [" + context.get("inspectStatusId")
                    + "], expected " + String.join(" / ", INSPECT_STATUSES));
        }
        double rejectedQty = MfgSupport.toDouble(context.get("rejectedQty"), 0.0);
        if (rejectedQty < 0.0) {
            return ServiceUtil.returnError("rejectedQty cannot be negative");
        }
        try {
            GenericValue lot = EntityQuery.use(delegator).from("MfgMaterialLot").where("lotId", lotId).queryOne();
            if (lot == null) {
                return ServiceUtil.returnError("Unknown material lot [" + lotId + "]");
            }
            if (MfgStockLedger.isProductLot(lot)) {
                return ServiceUtil.returnError("Lot [" + lotId + "] holds finished goods, ship them through a"
                        + " delivery order instead of issuing them to a work order");
            }
            double received = MfgSupport.toDouble(lot.get("receivedQty"), 0.0);
            if (rejectedQty > received) {
                return ServiceUtil.returnError("rejectedQty [" + rejectedQty
                        + "] is larger than the received quantity [" + received + "]");
            }
            lot.set("inspectStatusId", status);
            lot.set("rejectedQty", Double.valueOf(rejectedQty));
            lot.set("inspector", MfgSupport.toText(context.get("inspector")));
            lot.set("inspectNote", MfgSupport.toText(context.get("inspectNote")));
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
        String componentCode = MfgSupport.toText(context.get("componentCode"));
        String kind = MfgSupport.toText(context.get("kind"));
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
                if (MfgStockLedger.matchesLotKind(lot, kind)) {
                    lots.add(lot);
                }
            }
            Map<String, Double> issued = MfgWorkOrderServices.issuedQtyByLot(delegator);
            List<Map<String, Object>> list = new ArrayList<>(lots.size());
            for (GenericValue lot : lots) {
                list.add(toLotMap(lot, issued.get(lot.getString("lotId"))));
            }
            Map<String, Object> result = ServiceUtil.returnSuccess();
            MfgPaging.putPage(result, "lots",
                    MfgPaging.filterByKeyword(list, MfgSupport.toText(context.get("search")), "lotId", "componentCode",
                            "componentName", "supplierName", "supplierLot"),
                    MfgPaging.pageOf(context), MfgPaging.pageSizeOf(context), MfgSupport.toText(context.get("search")));
            result.put("componentCode", componentCode);
            result.put("kind", kind);
            return result;
        } catch (GenericEntityException e) {
            return ServiceUtil.returnError("Could not read the material lots: " + e.getMessage());
        }
    }

    /**
     * Maps the incoming inspection status, defaulting an empty value to {@code PENDING}.
     *
     * @param value the raw input
     * @return one of {@link #INSPECT_STATUSES}, or {@code null} when the value is not a known status
     */
    static String normalizeInspectStatus(Object value) {
        String text = MfgSupport.toText(value);
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

    static Map<String, Object> toLotMap(GenericValue lot, Double issuedQty) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("lotId", lot.getString("lotId"));
        map.put("lotKindId", MfgStockLedger.lotKindOf(lot));
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
        // what is really left comes from the ledger, so a scrap or an adjustment shows up here;
        // received minus issued only knows about issuing, which made a scrapped lot look full
        double onHand = lot.getDouble("onHandQty") == null
                ? MfgSupport.round2(received - issued) : lot.getDouble("onHandQty").doubleValue();
        map.put("remainingQty", Double.valueOf(MfgSupport.round2(onHand)));
        String inspectStatus = lot.getString("inspectStatusId");
        map.put("inspectStatusId", inspectStatus == null ? IQC_PENDING : inspectStatus);
        map.put("inspectDate", lot.get("inspectDate"));
        map.put("inspector", lot.getString("inspector"));
        map.put("inspectNote", lot.getString("inspectNote"));
        map.put("rejectedQty", lot.getDouble("rejectedQty") == null
                ? Double.valueOf(0.0) : lot.getDouble("rejectedQty"));
        map.put("usable", Boolean.valueOf(MfgWorkOrderServices.isLotUsable(inspectStatus)));
        map.put("note", lot.getString("note"));
        return map;
    }

    static Map<String, Object> toIssueMap(GenericValue issue, GenericValue lot) {
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

    static List<Map<String, Object>> toReplacementMaps(List<GenericValue> replacements) {
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
        String denied = permissionProblem(dctx, context, PERM_STOCK);
        if (denied != null) {
            return ServiceUtil.returnError(denied);
        }
        Delegator delegator = dctx.getDelegator();
        String csv = MfgSupport.toText(context.get("csv"));
        if (csv == null) {
            return ServiceUtil.returnError("csv is required");
        }
        List<String[]> rows = MfgCsv.parseLotCsv(csv);
        if (rows.isEmpty()) {
            return ServiceUtil.returnError("No lot line could be parsed, expected "
                    + "componentCode,componentName,supplierName,supplierLot,qty,receivedDate");
        }
        try {
            int imported = 0;
            List<String> lotIds = new ArrayList<>();
            for (String[] row : rows) {
                double qty = MfgSupport.toDouble(row[4], 0.0);
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
                Object receivedDate = MfgSupport.parseDate(row[5]);
                lot.set("receivedDate", receivedDate == null
                        ? java.sql.Date.valueOf(LocalDate.now()) : receivedDate);
                delegator.create(lot);
                MfgStockLedger.recordMovement(delegator, lot, MOVE_RECEIPT, qty, null, null, null, row[3], null,
                        Timestamp.valueOf(lot.getDate("receivedDate").toLocalDate().atStartOfDay()),
                        "CSV 导入到货", MfgSupport.toText(context.get("locationId")));
                MfgStockLedger.recalcLotStock(delegator, lot);
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
                List<Map<String, Object>> lines = MfgWorkOrderServices.kittingLines(delegator, order);
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
            MfgPaging.putPage(result, "orders",
                    MfgPaging.filterByKeyword(rows, MfgSupport.toText(context.get("search")), "workOrderId", "productCode",
                            "customerName"),
                    MfgPaging.pageOf(context), MfgPaging.pageSizeOf(context), MfgSupport.toText(context.get("search")));
            result.put("orderCount", Integer.valueOf(rows.size()));
            result.put("shortageOrderCount", Long.valueOf(shortageOrders));
            return result;
        } catch (GenericEntityException e) {
            return ServiceUtil.returnError("Could not compute the material shortages: " + e.getMessage());
        }
    }

    // ------------------------------------------------------------- delivery and testing

    static Map<String, Object> toDeliveryMap(GenericValue delivery) {
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
        String denied = permissionProblem(dctx, context, PERM_PRODUCTION);
        if (denied != null) {
            return ServiceUtil.returnError(denied);
        }
        Delegator delegator = dctx.getDelegator();
        String vendorName = MfgSupport.toText(context.get("vendorName"));
        if (vendorName == null) {
            return ServiceUtil.returnError("vendorName is required");
        }
        try {
            String vendorId = MfgSupport.toText(context.get("vendorId"));
            if (vendorId == null) {
                vendorId = nextVendorId(delegator, LocalDate.now());
            }
            GenericValue vendor = delegator.makeValue("MfgVendor");
            vendor.set("vendorId", vendorId);
            vendor.set("vendorName", vendorName);
            vendor.set("vendorType", MfgSupport.toText(context.get("vendorType")));
            vendor.set("contactName", MfgSupport.toText(context.get("contactName")));
            vendor.set("contactMobile", MfgSupport.toText(context.get("contactMobile")));
            vendor.set("addressText", MfgSupport.toText(context.get("addressText")));
            vendor.set("customerCode", MfgSupport.toText(context.get("customerCode")));
            String statusId = MfgSupport.toText(context.get("statusId"));
            vendor.set("statusId", statusId == null ? STATUS_ACTIVE : statusId);
            vendor.set("note", MfgSupport.toText(context.get("note")));
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
            MfgPaging.putPage(result, "vendors",
                    MfgPaging.filterByKeyword(vendorMaps(delegator), MfgSupport.toText(context.get("search")), "vendorName",
                            "vendorType", "contactName", "contactMobile", "customerCode", "vendorId"),
                    MfgPaging.pageOf(context), MfgPaging.pageSizeOf(context), MfgSupport.toText(context.get("search")));
            return result;
        } catch (GenericEntityException e) {
            return ServiceUtil.returnError("Could not read the vendors: " + e.getMessage());
        }
    }

    static List<Map<String, Object>> vendorMaps(Delegator delegator) throws GenericEntityException {
        List<GenericValue> vendors = EntityQuery.use(delegator).from("MfgVendor")
                .orderBy("vendorName").queryList();
        List<Map<String, Object>> list = new ArrayList<>(vendors.size());
        for (GenericValue vendor : vendors) {
            list.add(MfgWorkOrderServices.toVendorMap(vendor));
        }
        return list;
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

    static Map<String, Object> toClaimMap(GenericValue claim) {
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

    // ------------------------------------------------------------- buyer risk list

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
        long days = MfgSupport.toLong(context.get("dayCount"), 30);
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
                row.put("shipPct", Long.valueOf(MfgSupport.percentOfMax(shipped, maxShipped)));
                row.put("claimPct", Long.valueOf(MfgSupport.percentOfMax(claimValue, maxClaims)));
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
            kpi.put("claimRate", MfgSupport.percent(claimCount, shippedUnits));
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
                total += MfgSupport.numberOrZero(row.get("shortage"));
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
            alert.put("detail", rows.size() + " 行欠料，共 " + MfgSupport.formatQty(total)
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
            double planned = MfgSupport.numberOrZero(project.get("plannedDays"));
            double actualDays = MfgSupport.numberOrZero(project.get("actualHours")) / HOURS_PER_DAY;
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
                detail.append("已用 ").append(MfgSupport.formatQty(actualDays)).append('/')
                        .append(MfgSupport.formatQty(planned)).append(" 人天");
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
            for (Map<String, Object> line : MfgWorkOrderServices.kittingLines(delegator, order)) {
                double shortage = MfgSupport.numberOrZero(line.get("shortage"));
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

    // ==================== inventory: append only movements, totals summed from them ====================

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
     * Tells whether a status is one of the sales order statuses.
     *
     * @param statusId the status to test
     * @return {@code true} when it is known
     */
    static boolean isKnownSoStatus(String statusId) {
        for (String status : SO_STATUSES) {
            if (status.equals(statusId)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Safely writes a sales order with its lines, then locks what the shelf can already cover.
     *
     * <p>The lines are given as text, one model per line: {@code 型号,数量} or
     * {@code 型号,数量,交期}. Saving an existing order keeps the line numbers, so the reservations
     * that already belong to a line survive an edit.
     *
     * @param dctx the dispatch context
     * @param context service context, see {@code servicedef/services.xml#mfgSalesOrderSave}
     * @return the service result with the order number
     */
    public static Map<String, Object> salesOrderSave(DispatchContext dctx, Map<String, ? extends Object> context) {
        String denied = permissionProblem(dctx, context, PERM_SALES);
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
                order.set("statusId", SO_OPEN);
                delegator.create(order);
            } else {
                order = EntityQuery.use(delegator).from("MfgSalesOrder")
                        .where("salesOrderId", salesOrderId).queryOne();
                if (order == null) {
                    return ServiceUtil.returnError("Unknown sales order [" + salesOrderId + "]");
                }
                if (SO_CLOSED.equals(order.getString("statusId"))
                        || SO_CANCELLED.equals(order.getString("statusId"))) {
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
     * @param context service context, see {@code servicedef/services.xml#mfgSalesOrderReserve}
     * @return the service result with what was reserved and what is still owed
     */
    public static Map<String, Object> salesOrderReserve(DispatchContext dctx, Map<String, ? extends Object> context) {
        String denied = permissionProblem(dctx, context, PERM_SALES);
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
    private static Map<String, Object> reserveSalesOrder(DispatchContext dctx, Map<String, ? extends Object> context,
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
            recordOrderReservation(delegator, lot, MOVE_RESERVE, take, salesOrderId, lineSeq, operator,
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
     * @param context service context, see {@code servicedef/services.xml#mfgSalesOrderPlan}
     * @return the service result with the planned quantity
     */
    public static Map<String, Object> salesOrderPlan(DispatchContext dctx, Map<String, ? extends Object> context) {
        String denied = permissionProblem(dctx, context, PERM_SALES);
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
            if (SO_CLOSED.equals(order.getString("statusId"))
                    || SO_CANCELLED.equals(order.getString("statusId"))) {
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
                if (workOrder != null && (WO_CLOSED.equals(workOrder.getString("statusId"))
                        || WO_COMPLETED.equals(workOrder.getString("statusId")))) {
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
            if (plannedLines > 0 && !SO_SHIPPED.equals(order.getString("statusId"))) {
                order.set("statusId", SO_PLANNED);
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
     * @param context service context, see {@code servicedef/services.xml#mfgSalesOrderClose}
     * @return the service result with the released quantity
     */
    public static Map<String, Object> salesOrderClose(DispatchContext dctx, Map<String, ? extends Object> context) {
        String denied = permissionProblem(dctx, context, PERM_SALES);
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
            order.set("statusId", SO_CLOSED);
            delegator.store(order);
            Map<String, Object> result = ServiceUtil.returnSuccess();
            result.put("salesOrderId", salesOrderId);
            result.put("releasedQty", Double.valueOf(MfgSupport.round2(released)));
            result.put("statusId", SO_CLOSED);
            return result;
        } catch (GenericEntityException e) {
            return ServiceUtil.returnError("Could not close the sales order: " + e.getMessage());
        }
    }

    /**
     * Lists the sales orders, newest first, with what each one still owes.
     *
     * @param dctx the dispatch context
     * @param context service context, see {@code servicedef/services.xml#mfgSalesOrderList}
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
            result.put("statuses", SO_STATUSES);
            return result;
        } catch (GenericEntityException e) {
            return ServiceUtil.returnError("Could not read the sales orders: " + e.getMessage());
        }
    }

    /**
     * One sales order with its lines, what it reserved and the work orders it started.
     *
     * @param dctx the dispatch context
     * @param context service context, see {@code servicedef/services.xml#mfgSalesOrderDetail}
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
    private static List<Map<String, Object>> orderLineMaps(Delegator delegator, String salesOrderId)
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
     * @param context service context, see {@code servicedef/services.xml#mfgSalesOrderLineQty}
     * @return the service result with the numbers of the line and of the whole order
     */
    public static Map<String, Object> salesOrderLineQty(DispatchContext dctx, Map<String, ? extends Object> context) {
        String denied = permissionProblem(dctx, context, PERM_SALES, PERM_ADMIN);
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
            if (SO_CLOSED.equals(statusId) || SO_CANCELLED.equals(statusId)) {
                return ServiceUtil.returnError("订单已" + (SO_CLOSED.equals(statusId) ? "关闭" : "取消")
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

    private static Map<String, Object> toSalesOrderMap(Delegator delegator, GenericValue order)
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
     * @param movementTypeId {@link #MOVE_RESERVE} or {@link #MOVE_RELEASE}
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
    private static double releaseOrderLine(Delegator delegator, String salesOrderId, long lineSeq,
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
            recordOrderReservation(delegator, lot, MOVE_RELEASE, -reserved, salesOrderId, lineSeq,
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
    private static double releaseSalesOrder(Delegator delegator, String salesOrderId, String operatorName,
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
    private static List<GenericValue> productLots(Delegator delegator) throws GenericEntityException {
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
            if (MOVE_RECEIPT.equals(movement.getString("movementTypeId"))) {
                booked += MfgSupport.numberOrZero(movement.get("quantityDiff"));
            }
        }
        double left = planned - booked;
        return left <= 0.0 ? 0.0 : MfgSupport.round2(left);
    }

    private static String nextSalesOrderId(Delegator delegator, LocalDate today) throws GenericEntityException {
        String prefix = SO_PREFIX + today.format(DAY) + "-";
        int max = 0;
        for (GenericValue order : EntityQuery.use(delegator).from("MfgSalesOrder").queryList()) {
            String id = order.getString("salesOrderId");
            if (id != null && id.startsWith(prefix)) {
                max = Math.max(max, parseTrailingNumber(id.substring(prefix.length())));
            }
        }
        return prefix + String.format(Locale.ROOT, "%02d", Integer.valueOf(max + 1));
    }

    /**
     * Tells whether a type is one of the stock location types.
     *
     * @param locationTypeId the type to test
     * @return {@code true} when it is known
     */
    static boolean isKnownLocationType(String locationTypeId) {
        for (String type : LOC_TYPES) {
            if (type.equals(locationTypeId)) {
                return true;
            }
        }
        return false;
    }

    // ==================================================================
    // Purchase receipts: the freight of one order belongs to the whole order
    // ==================================================================

    /** Freight stays a period cost: the default of a receipt, the price of a part stays the price paid for it. */
    public static final String ALLOC_NONE = "MFG_ALLOC_NONE";
    /** Freight spread over the lines in proportion to the goods value, kept for a later release. */
    public static final String ALLOC_BY_AMOUNT = "MFG_ALLOC_BY_AMOUNT";
    /** Freight spread over the lines in proportion to the quantity, kept for a later release. */
    public static final String ALLOC_BY_QTY = "MFG_ALLOC_BY_QTY";

    /** Ways a receipt may treat its freight; only {@link #ALLOC_NONE} is open for now. */
    public static final String[] ALLOCATIONS = {ALLOC_NONE, ALLOC_BY_AMOUNT, ALLOC_BY_QTY};

    /**
     * Adds up one receipt: the goods value of its lines, the freight and what the order cost in
     * total. Because the freight is a period cost it is reported next to the goods value, not
     * inside the unit prices.
     *
     * @param lines the lines of the receipt, each carrying {@code goodsAmount}
     * @param freight the freight paid for the whole order
     * @return {@code lineCount}, {@code goodsTotal}, {@code freightTotal}, {@code spendTotal} and
     *         {@code freightShare} (freight as a percentage of the goods value)
     */
    static Map<String, Object> receiptSummary(List<Map<String, Object>> lines, double freight) {
        return receiptSummary(lines, freight, 0.0);
    }

    /**
     * Adds up one receipt: the goods value of its lines, the freight, the discount the shop took off
     * the whole order (a Taobao red packet, coupon or 满减) and what the order cost in total.
     *
     * <p>The discount is a payment discount, not a cheaper part: the unit prices stay the prices
     * printed on the order, and 实付款 = goods + freight - discount.
     *
     * @param lines the lines of the receipt, each carrying {@code goodsAmount}
     * @param freight the freight paid for the whole order
     * @param discount the money taken off the whole order
     * @return {@code lineCount}, {@code goodsTotal}, {@code freightTotal}, {@code discountTotal},
     *         {@code spendTotal} and {@code freightShare}
     */
    static Map<String, Object> receiptSummary(List<Map<String, Object>> lines, double freight, double discount) {
        double goods = 0.0;
        for (Map<String, Object> line : lines) {
            Object amount = line.get("goodsAmount");
            if (amount instanceof Number) {
                goods += ((Number) amount).doubleValue();
            }
        }
        double goodsTotal = MfgSupport.round2(goods);
        double freightTotal = MfgSupport.round2(freight);
        double discountTotal = MfgSupport.round2(discount);
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("lineCount", Integer.valueOf(lines.size()));
        summary.put("goodsTotal", Double.valueOf(goodsTotal));
        summary.put("freightTotal", Double.valueOf(freightTotal));
        summary.put("discountTotal", Double.valueOf(discountTotal));
        summary.put("spendTotal", Double.valueOf(MfgSupport.round2(goodsTotal + freightTotal - discountTotal)));
        summary.put("freightShare", Double.valueOf(goodsTotal <= 0.0
                ? 0.0 : MfgSupport.round2(freightTotal / goodsTotal * 100.0)));
        return summary;
    }

    /**
     * Saves the head of a purchase receipt (supplier, shop order number, arrival day, freight).
     *
     * <p>The freight is kept on the receipt and booked as a period cost: spreading it over the part
     * prices would inflate the cheap parts of a mixed order and leave the expensive ones almost
     * untouched, so the unit price stays the price really paid for the part.
     *
     * @param dctx the dispatch context
     * @param context service context, see {@code servicedef/services.xml#mfgPurchaseReceiptSave}
     * @return the service result containing {@code receiptId}
     */
    public static Map<String, Object> purchaseReceiptSave(DispatchContext dctx, Map<String, ? extends Object> context) {
        String denied = permissionProblem(dctx, context, PERM_STOCK);
        if (denied != null) {
            return ServiceUtil.returnError(denied);
        }
        Delegator delegator = dctx.getDelegator();
        String receiptId = MfgSupport.toText(context.get("receiptId"));
        String allocationId = MfgSupport.toText(context.get("allocationId"));
        if (allocationId == null) {
            allocationId = ALLOC_NONE;
        }
        if (!ALLOC_NONE.equals(allocationId)) {
            return ServiceUtil.returnError("运费暂时只记期间费用（" + ALLOC_NONE + "），按比例分摊还没有打开");
        }
        String freightText = MfgSupport.toText(context.get("freightAmount"));
        Double freight = MfgSupport.optionalAmount(freightText);
        if (freightText != null && freight == null) {
            return ServiceUtil.returnError("运费要填非负数字，或留空：" + freightText);
        }
        // the red packet / coupon taken off the whole order, so 实付款 = goods + freight - discount
        String discountText = MfgSupport.toText(context.get("discountAmount"));
        Double discount = MfgSupport.optionalAmount(discountText);
        if (discountText != null && discount == null) {
            return ServiceUtil.returnError("优惠要填非负数字，或留空：" + discountText);
        }
        try {
            GenericValue receipt = receiptId == null ? null
                    : EntityQuery.use(delegator).from("MfgPurchaseReceipt")
                            .where("receiptId", receiptId).queryOne();
            if (receipt == null) {
                receiptId = nextReceiptId(delegator, LocalDate.now());
                receipt = delegator.makeValue("MfgPurchaseReceipt");
                receipt.set("receiptId", receiptId);
                receipt.set("freightAmount", Double.valueOf(0.0));
                // the column is NOT NULL: an insert that leaves it out fails on Derby
                receipt.set("discountAmount", Double.valueOf(0.0));
                receipt.set("allocationId", ALLOC_NONE);
                receipt.set("receiptDate", java.sql.Date.valueOf(LocalDate.now()));
                receipt.set("statusId", RECEIPT_ACTIVE);
            } else if (RECEIPT_VOID.equals(statusOfReceipt(receipt))) {
                return ServiceUtil.returnError("到货单 [" + receiptId + "] 已经作废，要改先点「恢复这张单」");
            }
            if (context.containsKey("supplierName")) {
                receipt.set("supplierName", MfgSupport.toText(context.get("supplierName")));
            }
            if (context.containsKey("supplierOrderNo")) {
                receipt.set("supplierOrderNo", MfgSupport.toText(context.get("supplierOrderNo")));
            }
            if (context.containsKey("receiptDate")) {
                Object date = MfgSupport.parseDate(MfgSupport.toText(context.get("receiptDate")));
                receipt.set("receiptDate", date == null
                        ? java.sql.Date.valueOf(LocalDate.now()) : date);
            }
            if (context.containsKey("freightAmount")) {
                receipt.set("freightAmount", Double.valueOf(freight == null ? 0.0 : freight.doubleValue()));
            }
            if (context.containsKey("discountAmount")) {
                receipt.set("discountAmount", Double.valueOf(discount == null ? 0.0 : discount.doubleValue()));
            }
            if (context.containsKey("note")) {
                receipt.set("note", MfgSupport.toText(context.get("note")));
            }
            delegator.createOrStore(receipt);
            Map<String, Object> result = ServiceUtil.returnSuccess();
            result.put("receiptId", receiptId);
            result.put("freightAmount", receipt.get("freightAmount"));
            result.put("discountAmount", receipt.get("discountAmount"));
            return result;
        } catch (GenericEntityException e) {
            return ServiceUtil.returnError("Could not save the receipt: " + e.getMessage());
        }
    }

    /**
     * Puts one received lot on a receipt, changes a line, or removes it.
     *
     * <p>Quantity and price are copied from the lot when the line is added; they can be corrected
     * afterwards without touching the lot, so the receipt stays a document of what was bought.
     *
     * @param dctx the dispatch context
     * @param context service context, see {@code servicedef/services.xml#mfgPurchaseReceiptLineSave}
     * @return the service result containing {@code receiptId}, {@code lineSeq} and {@code goodsAmount}
     */
    public static Map<String, Object> purchaseReceiptLineSave(DispatchContext dctx, Map<String, ? extends Object> context) {
        String denied = permissionProblem(dctx, context, PERM_STOCK);
        if (denied != null) {
            return ServiceUtil.returnError(denied);
        }
        Delegator delegator = dctx.getDelegator();
        String receiptId = MfgSupport.toText(context.get("receiptId"));
        if (receiptId == null) {
            return ServiceUtil.returnError("receiptId is required");
        }
        boolean removing = "Y".equals(MfgSupport.toText(context.get("delete")));
        long lineSeq = MfgSupport.toLong(context.get("lineSeq"), 0L);
        try {
            GenericValue receipt = EntityQuery.use(delegator).from("MfgPurchaseReceipt")
                    .where("receiptId", receiptId).queryOne();
            if (receipt == null) {
                return ServiceUtil.returnError("Unknown receipt [" + receiptId + "]");
            }
            if (RECEIPT_VOID.equals(statusOfReceipt(receipt))) {
                return ServiceUtil.returnError("到货单 [" + receiptId + "] 已经作废，料和金额都不再算进去");
            }
            GenericValue line = lineSeq > 0L ? EntityQuery.use(delegator).from("MfgPurchaseReceiptLine")
                    .where("receiptId", receiptId, "lineSeq", Long.valueOf(lineSeq)).queryOne() : null;
            if (removing) {
                if (line == null) {
                    return ServiceUtil.returnError("要删除的行不在这个到货单里，刷新后再试");
                }
                line.remove();
                Map<String, Object> result = ServiceUtil.returnSuccess();
                result.put("receiptId", receiptId);
                result.put("lineSeq", String.valueOf(lineSeq));
                return result;
            }
            boolean created = line == null;
            if (created) {
                String lotId = MfgSupport.toText(context.get("lotId"));
                if (lotId == null) {
                    return ServiceUtil.returnError("请选择要挂到这个到货单上的批次");
                }
                GenericValue lot = EntityQuery.use(delegator).from("MfgMaterialLot")
                        .where("lotId", lotId).queryOne();
                if (lot == null) {
                    return ServiceUtil.returnError("Unknown material lot [" + lotId + "]");
                }
                if (MfgStockLedger.isProductLot(lot)) {
                    return ServiceUtil.returnError("批次 [" + lotId + "] 是成品，采购到货单只记买来的元件");
                }
                GenericValue other = EntityQuery.use(delegator).from("MfgPurchaseReceiptLine")
                        .where("lotId", lotId).queryOne();
                if (other != null && !RECEIPT_VOID.equals(statusOfReceipt(EntityQuery.use(delegator)
                        .from("MfgPurchaseReceipt")
                        .where("receiptId", other.getString("receiptId")).queryOne()))) {
                    return ServiceUtil.returnError("批次 [" + lotId + "] 已经在到货单 ["
                            + other.getString("receiptId") + "] 上");
                }
                lineSeq = nextReceiptLineSeq(delegator, receiptId);
                line = delegator.makeValue("MfgPurchaseReceiptLine");
                line.set("receiptId", receiptId);
                line.set("lineSeq", Long.valueOf(lineSeq));
                line.set("lotId", lotId);
                line.set("componentCode", lot.getString("componentCode"));
                line.set("componentName", lot.getString("componentName"));
                line.set("qty", lot.get("receivedQty"));
                line.set("unitPrice", lot.get("unitPrice"));
            }
            double qty = MfgSupport.toDouble(context.get("qty"), MfgSupport.toDouble(line.get("qty"), 0.0));
            if (qty <= 0.0) {
                return ServiceUtil.returnError("数量要大于 0");
            }
            double unitPrice = MfgSupport.toDouble(line.get("unitPrice"), 0.0);
            if (context.containsKey("unitPrice")) {
                String priceText = MfgSupport.toText(context.get("unitPrice"));
                Double price = MfgSupport.optionalAmount(priceText);
                if (priceText != null && price == null) {
                    return ServiceUtil.returnError("单价要填非负数字，或留空：" + priceText);
                }
                unitPrice = price == null ? 0.0 : price.doubleValue();
            }
            line.set("qty", Double.valueOf(qty));
            line.set("unitPrice", Double.valueOf(unitPrice));
            line.set("goodsAmount", Double.valueOf(MfgSupport.round2(qty * unitPrice)));
            if (context.containsKey("note")) {
                line.set("note", MfgSupport.toText(context.get("note")));
            }
            if (created) {
                delegator.create(line);
            } else {
                line.store();
            }
            Map<String, Object> result = ServiceUtil.returnSuccess();
            result.put("receiptId", receiptId);
            result.put("lineSeq", String.valueOf(lineSeq));
            result.put("goodsAmount", line.get("goodsAmount"));
            return result;
        } catch (GenericEntityException e) {
            return ServiceUtil.returnError("Could not save the receipt line: " + e.getMessage());
        }
    }

    /**
     * Voids a purchase receipt: the head and its lines stay as the record of what was once typed, but
     * the receipt drops out of every total and its lots count as free again.
     *
     * <p>Voiding never touches the shelf. The lots it lists, their stock and the ledger movements are
     * the truth about what is in the warehouse, so they stay; whoever wants the parts out of the
     * stock books a scrap movement under 库存 → 流水, which is what that is for. That is also how
     * OFBiz itself treats a receipt: {@code ShipmentReceipt} has no undo either, the receipt is kept
     * and the stock is corrected with a movement.
     *
     * @param dctx the dispatch context
     * @param context service context, see {@code servicedef/services.xml#mfgPurchaseReceiptVoid}
     * @return the service result containing {@code receiptId}, {@code statusId} and {@code voidedReceipt}
     */
    public static Map<String, Object> purchaseReceiptVoid(DispatchContext dctx, Map<String, ? extends Object> context) {
        String denied = permissionProblem(dctx, context, PERM_STOCK);
        if (denied != null) {
            return ServiceUtil.returnError(denied);
        }
        Delegator delegator = dctx.getDelegator();
        String receiptId = MfgSupport.toText(context.get("receiptId"));
        if (receiptId == null) {
            return ServiceUtil.returnError("请选择要作废的到货单");
        }
        try {
            GenericValue receipt = EntityQuery.use(delegator).from("MfgPurchaseReceipt")
                    .where("receiptId", receiptId).queryOne();
            if (receipt == null) {
                return ServiceUtil.returnError("Unknown receipt [" + receiptId + "]");
            }
            if (RECEIPT_VOID.equals(statusOfReceipt(receipt))) {
                return ServiceUtil.returnError("到货单 [" + receiptId + "] 已经作废了");
            }
            String reason = MfgSupport.toText(context.get("voidReason"));
            receipt.set("statusId", RECEIPT_VOID);
            receipt.set("voidedDate", new Timestamp(System.currentTimeMillis()));
            GenericValue userLogin = (GenericValue) context.get("userLogin");
            receipt.set("voidedByUserLoginId", userLogin == null ? null : userLogin.getString("userLoginId"));
            receipt.set("voidReason", reason == null ? "建错了" : reason);
            receipt.store();
            Map<String, Object> result = ServiceUtil.returnSuccess();
            result.put("receiptId", receiptId);
            result.put("voidedReceipt", receiptId);
            result.put("statusId", RECEIPT_VOID);
            return result;
        } catch (GenericEntityException e) {
            Debug.logError(e, "Could not void the receipt [" + receiptId + "]", MODULE);
            return ServiceUtil.returnError("作废失败：" + e.getMessage());
        }
    }

    /**
     * Puts a voided receipt back into force, for the void that was itself a mistake.
     *
     * @param dctx the dispatch context
     * @param context service context, see {@code servicedef/services.xml#mfgPurchaseReceiptRestore}
     * @return the service result containing {@code receiptId} and {@code statusId}
     */
    public static Map<String, Object> purchaseReceiptRestore(DispatchContext dctx, Map<String, ? extends Object> context) {
        String denied = permissionProblem(dctx, context, PERM_STOCK);
        if (denied != null) {
            return ServiceUtil.returnError(denied);
        }
        Delegator delegator = dctx.getDelegator();
        String receiptId = MfgSupport.toText(context.get("receiptId"));
        if (receiptId == null) {
            return ServiceUtil.returnError("请选择要恢复的到货单");
        }
        try {
            GenericValue receipt = EntityQuery.use(delegator).from("MfgPurchaseReceipt")
                    .where("receiptId", receiptId).queryOne();
            if (receipt == null) {
                return ServiceUtil.returnError("Unknown receipt [" + receiptId + "]");
            }
            receipt.set("statusId", RECEIPT_ACTIVE);
            receipt.set("voidedDate", null);
            receipt.set("voidedByUserLoginId", null);
            receipt.set("voidReason", null);
            receipt.store();
            Map<String, Object> result = ServiceUtil.returnSuccess();
            result.put("receiptId", receiptId);
            result.put("statusId", RECEIPT_ACTIVE);
            return result;
        } catch (GenericEntityException e) {
            Debug.logError(e, "Could not restore the receipt [" + receiptId + "]", MODULE);
            return ServiceUtil.returnError("恢复失败：" + e.getMessage());
        }
    }

    /**
     * The purchase receipts with what each order cost: goods value, freight and the sum of both.
     *
     * @param dctx the dispatch context
     * @param context service context, see {@code servicedef/services.xml#mfgPurchaseReceiptList}
     * @return the service result containing {@code receipts} and the totals of the listed rows
     */
    public static Map<String, Object> purchaseReceiptList(DispatchContext dctx, Map<String, ? extends Object> context) {
        Delegator delegator = dctx.getDelegator();
        Map<String, Object> result = ServiceUtil.returnSuccess();
        result.put("receipts", new ArrayList<Map<String, Object>>());
        result.put("totalCount", Integer.valueOf(0));
        result.put("goodsTotal", Double.valueOf(0.0));
        result.put("freightTotal", Double.valueOf(0.0));
        result.put("spendTotal", Double.valueOf(0.0));
        result.put("freightShare", Double.valueOf(0.0));
        try {
            Map<String, List<Map<String, Object>>> linesOf = new LinkedHashMap<>();
            for (GenericValue line : EntityQuery.use(delegator).from("MfgPurchaseReceiptLine").queryList()) {
                String id = line.getString("receiptId");
                List<Map<String, Object>> lines = linesOf.get(id);
                if (lines == null) {
                    lines = new ArrayList<>();
                    linesOf.put(id, lines);
                }
                lines.add(toReceiptLineMap(line));
            }
            List<Map<String, Object>> rows = new ArrayList<>();
            boolean withVoid = "Y".equalsIgnoreCase(MfgSupport.toText(context.get("includeVoid")));
            for (GenericValue receipt : EntityQuery.use(delegator).from("MfgPurchaseReceipt")
                    .orderBy("-receiptDate", "-createdStamp").queryList()) {
                // a voided receipt keeps its paper but is not a purchase any more: the list hides it
                // unless the person asks for it as well
                if (!withVoid && RECEIPT_VOID.equals(statusOfReceipt(receipt))) {
                    continue;
                }
                Map<String, Object> row = toReceiptMap(receipt);
                List<Map<String, Object>> lines = linesOf.get(receipt.getString("receiptId"));
                row.putAll(receiptSummary(lines == null ? new ArrayList<Map<String, Object>>() : lines,
                        MfgSupport.numberOrZero(receipt.get("freightAmount")), MfgSupport.numberOrZero(receipt.get("discountAmount"))));
                rows.add(row);
            }
            String keyword = MfgSupport.toText(context.get("search"));
            List<Map<String, Object>> filtered = MfgPaging.filterByKeyword(rows, keyword, "receiptId",
                    "supplierName", "supplierOrderNo");
            double goods = 0.0;
            double freight = 0.0;
            for (Map<String, Object> row : filtered) {
                goods += MfgSupport.numberOrZero(row.get("goodsTotal"));
                freight += MfgSupport.numberOrZero(row.get("freightTotal"));
            }
            result.put("goodsTotal", Double.valueOf(MfgSupport.round2(goods)));
            result.put("freightTotal", Double.valueOf(MfgSupport.round2(freight)));
            result.put("spendTotal", Double.valueOf(MfgSupport.round2(goods + freight)));
            result.put("freightShare", Double.valueOf(goods <= 0.0 ? 0.0 : MfgSupport.round2(freight / goods * 100.0)));
            MfgPaging.putPage(result, "receipts", filtered, MfgPaging.pageOf(context), MfgPaging.pageSizeOf(context), keyword);
            // a receipt holds one or two articles, so the list shows its lines when the row is
            // opened; only the rows of this page are carried along
            Map<String, List<Map<String, Object>>> linesByReceipt = new LinkedHashMap<>();
            List<?> paged = (List<?>) result.get("receipts");
            if (paged != null) {
                for (Object row : paged) {
                    Map<String, Object> map = UtilGenerics.cast(row);
                    String id = MfgSupport.toText(map.get("receiptId"));
                    List<Map<String, Object>> lines = linesOf.get(id);
                    if (lines != null && !lines.isEmpty()) {
                        linesByReceipt.put(id, lines);
                    }
                }
            }
            result.put("linesByReceipt", linesByReceipt);
            return result;
        } catch (GenericEntityException e) {
            return ServiceUtil.returnError("Could not read the receipts: " + e.getMessage());
        }
    }

    /**
     * One receipt with its lines, plus the lots that are not on a receipt yet.
     *
     * @param dctx the dispatch context
     * @param context service context, see {@code servicedef/services.xml#mfgPurchaseReceiptDetail}
     * @return the service result containing {@code receipt}, {@code lines}, {@code freeLots} and the totals
     */
    public static Map<String, Object> purchaseReceiptDetail(DispatchContext dctx, Map<String, ? extends Object> context) {
        Delegator delegator = dctx.getDelegator();
        Map<String, Object> result = ServiceUtil.returnSuccess();
        result.put("receipt", null);
        result.put("lines", new ArrayList<Map<String, Object>>());
        result.put("freeLots", new ArrayList<Map<String, Object>>());
        result.put("lineCount", Integer.valueOf(0));
        result.put("goodsTotal", Double.valueOf(0.0));
        result.put("freightTotal", Double.valueOf(0.0));
        result.put("spendTotal", Double.valueOf(0.0));
        result.put("freightShare", Double.valueOf(0.0));
        Map<String, String> onReceipt = new LinkedHashMap<>();
        try {
            for (GenericValue line : EntityQuery.use(delegator).from("MfgPurchaseReceiptLine").queryList()) {
                onReceipt.put(line.getString("lotId"), line.getString("receiptId"));
            }
            // a lot on a voided receipt is free again: that is how a receipt typed wrong is rebuilt
            for (GenericValue voided : EntityQuery.use(delegator).from("MfgPurchaseReceipt")
                    .where("statusId", RECEIPT_VOID).queryList()) {
                for (GenericValue line : EntityQuery.use(delegator).from("MfgPurchaseReceiptLine")
                        .where("receiptId", voided.getString("receiptId")).queryList()) {
                    onReceipt.remove(line.getString("lotId"));
                }
            }
            List<Map<String, Object>> freeLots = new ArrayList<>();
            for (GenericValue lot : EntityQuery.use(delegator).from("MfgMaterialLot")
                    .orderBy("-createdStamp").queryList()) {
                if (MfgStockLedger.isProductLot(lot) || onReceipt.containsKey(lot.getString("lotId"))) {
                    continue;
                }
                if (!MfgStockLedger.matchesLotKind(lot, LOT_COMPONENT)) {
                    continue;
                }
                Map<String, Object> map = new LinkedHashMap<>();
                map.put("lotId", lot.getString("lotId"));
                map.put("componentCode", lot.getString("componentCode"));
                map.put("componentName", lot.getString("componentName"));
                map.put("supplierName", lot.getString("supplierName"));
                map.put("receivedQty", lot.get("receivedQty"));
                map.put("unitPrice", lot.get("unitPrice"));
                map.put("receivedDate", lot.get("receivedDate"));
                freeLots.add(map);
                if (freeLots.size() >= MAX_FREE_LOTS) {
                    break;
                }
            }
            result.put("freeLots", freeLots);

            String receiptId = MfgSupport.toText(context.get("receiptId"));
            if (receiptId == null) {
                return result;
            }
            GenericValue receipt = EntityQuery.use(delegator).from("MfgPurchaseReceipt")
                    .where("receiptId", receiptId).queryOne();
            if (receipt == null) {
                return result;
            }
            List<Map<String, Object>> lines = new ArrayList<>();
            for (GenericValue line : EntityQuery.use(delegator).from("MfgPurchaseReceiptLine")
                    .where("receiptId", receiptId).orderBy("lineSeq").queryList()) {
                lines.add(toReceiptLineMap(line));
            }
            result.put("receipt", toReceiptMap(receipt));
            result.put("lines", lines);
            result.putAll(receiptSummary(lines, MfgSupport.numberOrZero(receipt.get("freightAmount")),
                    MfgSupport.numberOrZero(receipt.get("discountAmount"))));
            return result;
        } catch (GenericEntityException e) {
            return ServiceUtil.returnError("Could not read the receipt: " + e.getMessage());
        }
    }

    /** How many unassigned lots the receipt page offers in its picker. */
    private static final int MAX_FREE_LOTS = 200;

    /**
     * Creates one receipt from the lines of a shop order (a Taobao order) and books every line into
     * stock as its own lot, all in one submission: the person copies the article lines out of the
     * order page, checks them and presses the button once.
     *
     * <p>Each line is {@code componentCode,componentName,quantity,amount} — the quantity in pieces
     * and the amount really paid for that line. The unit price is the amount divided by the
     * quantity, so a line of 100 pieces for 2.91 keeps its 0.0291 instead of being rounded to 0.03.
     *
     * <p>The order number makes it idempotent: an order that already has a receipt is reused and
     * lines that are already on it are skipped, so a double click does not buy the parts twice.
     *
     * @param dctx the dispatch context
     * @param context service context, see {@code servicedef/services.xml#mfgReceiptFromOrder}
     * @return the service result containing {@code receiptId}, {@code importedCount},
     *         {@code skippedCount} and the totals
     */
    public static Map<String, Object> receiptFromOrder(DispatchContext dctx, Map<String, ? extends Object> context) {
        String denied = permissionProblem(dctx, context, PERM_STOCK);
        if (denied != null) {
            return ServiceUtil.returnError(denied);
        }
        Delegator delegator = dctx.getDelegator();
        // the lines are handed over as they were recognised: a row whose first cell is empty (a shop
        // that prints no article code) starts with a tab, and trimming the whole text ate it, so the row
        // came back with three cells and the whole order was refused with "没有解析出明细" while the page
        // showed the row quite happily (Step 163). Only a text of nothing but blanks counts as empty.
        Object rawLines = context.get("lines");
        String lines = rawLines == null ? null : rawLines.toString();
        if (lines != null && lines.trim().isEmpty()) {
            lines = null;
        }
        if (lines == null) {
            return ServiceUtil.returnError("请把订单明细粘进来（一行一个料：料号,名称,数量,实付金额）");
        }
        String supplierOrderNo = MfgSupport.toText(context.get("supplierOrderNo"));
        String supplierName = MfgSupport.toText(context.get("supplierName"));
        String freightText = MfgSupport.toText(context.get("freightAmount"));
        Double freight = MfgSupport.optionalAmount(freightText);
        if (freightText != null && freight == null) {
            return ServiceUtil.returnError("运费要填非负数字，或留空：" + freightText);
        }
        // a Taobao red packet, coupon or 满减 comes off the whole order: the part prices stay as they
        // are printed, so 实付款 = goods + freight - discount
        String discountText = MfgSupport.toText(context.get("discountAmount"));
        Double discount = MfgSupport.optionalAmount(discountText);
        if (discountText != null && discount == null) {
            return ServiceUtil.returnError("优惠要填非负数字，或留空：" + discountText);
        }
        List<String[]> rows = OirOrders.parseOrderArticleLines(lines);
        if (rows.isEmpty()) {
            return ServiceUtil.returnError("没有解析出明细，一行一个料：料号,名称,数量,实付金额");
        }
        if ("Y".equalsIgnoreCase(MfgSupport.toText(context.get("refunded")))) {
            // a refunded or closed order never arrived: booking it would invent stock. Remember it
            // first, so the same order cannot be booked in by hand a week later either - in its own
            // transaction, because returning the error rolls this one back
            rememberRefundedOrderInNewTransaction(dctx, context, supplierOrderNo, supplierName,
                    "识别到退款/关闭");
            return ServiceUtil.returnError("这张单已经退款/关闭，按规矩不入库；已经把它记进"
                    + "「退款 / 关闭的单」了（货确实到了就去那儿撤掉标记）");
        }
        int shortened = 0;
        for (String[] row : rows) {
            if (row[0].length() > MAX_COMPONENT_CODE) {
                // a shop order often carries a package description as the part number, which is
                // longer than the code field; short it instead of refusing the whole order
                String fullCode = row[0];
                row[0] = fittingComponentCode(fullCode);
                row[1] = nameWithFullCode(row[1], fullCode);
                shortened++;
            }
            if (MfgSupport.toDouble(row[2], 0.0) <= 0.0) {
                return ServiceUtil.returnError("数量要大于 0：" + row[0]);
            }
        }
        try {
            java.sql.Date receiptDate = (java.sql.Date) MfgSupport.parseDate(context.get("receiptDate"));
            if (receiptDate == null) {
                receiptDate = java.sql.Date.valueOf(LocalDate.now());
            }
            // an order that was refunded or closed stays out of the stock for good, even when the
            // person types the number in by hand and the screenshot is long gone
            String blocked = refundedOrderProblem(delegator, supplierOrderNo);
            if (blocked != null) {
                return ServiceUtil.returnError(blocked);
            }
            GenericValue receipt = supplierOrderNo == null ? null
                    : EntityQuery.use(delegator).from("MfgPurchaseReceipt")
                            .where("supplierOrderNo", supplierOrderNo).queryFirst();
            if (receipt == null) {
                String receiptId = nextReceiptId(delegator, LocalDate.now());
                receipt = delegator.makeValue("MfgPurchaseReceipt");
                receipt.set("receiptId", receiptId);
                receipt.set("supplierName", supplierName);
                receipt.set("supplierOrderNo", supplierOrderNo);
                receipt.set("receiptDate", receiptDate);
                receipt.set("freightAmount", Double.valueOf(freight == null ? 0.0 : freight.doubleValue()));
                receipt.set("discountAmount", Double.valueOf(discount == null ? 0.0 : discount.doubleValue()));
                receipt.set("allocationId", ALLOC_NONE);
                receipt.set("note", MfgSupport.toText(context.get("note")));
                receipt.set("statusId", RECEIPT_ACTIVE);
                delegator.create(receipt);
            } else if (RECEIPT_VOID.equals(statusOfReceipt(receipt))) {
                // the order number is the key: importing it again means it was wanted after all, so
                // the voided receipt of that order comes back into force instead of staying dead
                receipt.set("statusId", RECEIPT_ACTIVE);
                receipt.set("voidedDate", null);
                receipt.set("voidedByUserLoginId", null);
                receipt.set("voidReason", null);
                if (freight != null) {
                    receipt.set("freightAmount", Double.valueOf(freight.doubleValue()));
                }
                if (discount != null) {
                    receipt.set("discountAmount", Double.valueOf(discount.doubleValue()));
                }
                receipt.store();
            } else if (freight != null || discount != null) {
                boolean replace = "Y".equalsIgnoreCase(MfgSupport.toText(context.get("overwrite")));
                // a re-read of the same order replaces the numbers it wrote before (a freight that was
                // written wrong must be correctable); a plain second delivery adds up
                if (freight != null) {
                    double amount = replace ? freight.doubleValue()
                            : MfgSupport.round2(MfgSupport.numberOrZero(receipt.get("freightAmount")) + freight.doubleValue());
                    receipt.set("freightAmount", Double.valueOf(MfgSupport.round2(amount)));
                }
                if (discount != null) {
                    double amount = replace ? discount.doubleValue()
                            : MfgSupport.round2(MfgSupport.numberOrZero(receipt.get("discountAmount")) + discount.doubleValue());
                    receipt.set("discountAmount", Double.valueOf(MfgSupport.round2(amount)));
                }
                receipt.store();
            }
            String receiptId = receipt.getString("receiptId");
            boolean overwrite = "Y".equalsIgnoreCase(MfgSupport.toText(context.get("overwrite")));
            // a shop prints the same article several times (one board in six colours), so one part
            // number may sit on more than one line: a line is identified by code + quantity + money,
            // and every line of the receipt may be claimed once by the answer
            Map<String, List<GenericValue>> existingByKey = new LinkedHashMap<>();
            Map<String, List<GenericValue>> existingByCode = new LinkedHashMap<>();
            Set<String> claimed = new LinkedHashSet<>();
            for (GenericValue line : EntityQuery.use(delegator).from("MfgPurchaseReceiptLine")
                    .where("receiptId", receiptId).orderBy("lineSeq").queryList()) {
                String code = line.getString("componentCode");
                existingByKey.computeIfAbsent(lotLineKey(code, line.get("qty"), line.get("goodsAmount")),
                        key -> new ArrayList<>()).add(line);
                if (code != null) {
                    existingByCode.computeIfAbsent(code, key -> new ArrayList<>()).add(line);
                }
            }
            int imported = 0;
            int skipped = 0;
            int updated = 0;
            Set<String> answeredCodes = new LinkedHashSet<>();
            long lineSeq = nextReceiptLineSeq(delegator, receiptId);
            for (String[] row : rows) {
                String componentCode = row[0];
                answeredCodes.add(componentCode);
                double qty = MfgSupport.toDouble(row[2], 0.0);
                double amount = MfgSupport.toDouble(row[3], 0.0);
                List<GenericValue> sameLine = existingByKey.get(lotLineKey(componentCode,
                        Double.valueOf(qty), Double.valueOf(MfgSupport.round2(amount))));
                GenericValue exact = claimOne(sameLine, claimed);
                if (exact != null) {
                    // this very line is already on the receipt: an order read twice, nothing to add
                    if (overwrite) {
                        mergeOrderLine(delegator, exact, receiptDate, qty, amount);
                        updated++;
                    } else {
                        skipped++;
                    }
                    continue;
                }
                GenericValue samePart = claimOne(existingByCode.get(componentCode), claimed);
                if (samePart != null) {
                    // the same part with other numbers: the person has to say that the new numbers
                    // should replace the old ones (a corrected read of the same order)
                    if (!overwrite) {
                        skipped++;
                        continue;
                    }
                    mergeOrderLine(delegator, samePart, receiptDate, qty, amount);
                    updated++;
                    continue;
                }
                String lotId = nextLotId(delegator, LocalDate.now());
                GenericValue lot = delegator.makeValue("MfgMaterialLot");
                lot.set("lotId", lotId);
                lot.set("componentCode", componentCode);
                lot.set("componentName", row[1] == null ? componentCode : row[1]);
                lot.set("supplierName", supplierName);
                lot.set("supplierLot", supplierOrderNo);
                lot.set("receivedQty", Double.valueOf(qty));
                lot.set("receivedAmount", Double.valueOf(MfgSupport.round2(amount)));
                lot.set("unitPrice", Double.valueOf(MfgSupport.round2(amount / qty)));
                lot.set("receivedDate", receiptDate);
                lot.set("note", MfgSupport.toText(context.get("note")));
                delegator.create(lot);
                MfgStockLedger.recordMovement(delegator, lot, MOVE_RECEIPT, qty, lot.get("unitPrice"), null, null,
                        supplierOrderNo, null,
                        Timestamp.valueOf(receiptDate.toLocalDate().atStartOfDay()),
                        "订单到货入库", MfgSupport.toText(context.get("locationId")));
                MfgStockLedger.recalcLotStock(delegator, lot);

                GenericValue line = delegator.makeValue("MfgPurchaseReceiptLine");
                line.set("receiptId", receiptId);
                // two imports of the same order may run at the same time, so a line number is
                // only used once it is really free on this receipt
                lineSeq = freeReceiptLineSeq(delegator, receiptId, lineSeq);
                line.set("lineSeq", Long.valueOf(lineSeq));
                line.set("lotId", lotId);
                line.set("componentCode", componentCode);
                line.set("componentName", row[1] == null ? componentCode : row[1]);
                line.set("qty", Double.valueOf(qty));
                line.set("unitPrice", lot.get("unitPrice"));
                line.set("goodsAmount", Double.valueOf(MfgSupport.round2(amount)));
                delegator.create(line);
                lineSeq += 10L;
                imported++;
            }
            if (imported == 0 && skipped == 0 && updated == 0) {
                return ServiceUtil.returnError("没有可入库的明细");
            }
            List<Map<String, Object>> lineMaps = new ArrayList<>();
            for (GenericValue line : EntityQuery.use(delegator).from("MfgPurchaseReceiptLine")
                    .where("receiptId", receiptId).orderBy("lineSeq").queryList()) {
                lineMaps.add(toReceiptLineMap(line));
            }
            Map<String, Object> summary = receiptSummary(lineMaps, MfgSupport.numberOrZero(receipt.get("freightAmount")),
                    MfgSupport.numberOrZero(receipt.get("discountAmount")));
            Map<String, Object> result = ServiceUtil.returnSuccess();
            result.put("receiptId", receiptId);
            result.put("importedCount", String.valueOf(imported));
            result.put("skippedCount", String.valueOf(skipped));
            result.put("shortenedCount", String.valueOf(shortened));
            result.put("updatedCount", String.valueOf(updated));
            // lines that are on the receipt but no longer in the answer: the person has to know, or a
            // corrected read could leave a phantom article behind without a word
            StringBuilder leftOver = new StringBuilder();
            if (overwrite) {
                for (String code : existingByCode.keySet()) {
                    if (answeredCodes.contains(code)) {
                        continue;
                    }
                    if (leftOver.length() > 0) {
                        leftOver.append('、');
                    }
                    leftOver.append(code);
                }
            }
            result.put("leftOverCount", String.valueOf(leftOver.length() == 0 ? 0
                    : leftOver.toString().split("、").length));
            result.put("leftOverLines", leftOver.toString());
            result.putAll(summary);
            return result;
        } catch (GenericEntityException e) {
            Debug.logError(e, "Could not book the shop order [" + supplierOrderNo + "]", MODULE);
            return ServiceUtil.returnError(receiptImportFailure(e));
        }
    }

    /**
     * Turns the database error of an order import into a message the shop can act on. The raw SQL
     * of a Derby constraint violation means nothing to the person at the bench.
     *
     * @param e the error of the import
     * @return the message to show on the receipt page
     */
    static String receiptImportFailure(GenericEntityException e) {
        String message = String.valueOf(e.getMessage());
        if (message.contains("duplicate key") || message.contains("already exists")) {
            return "这单正在入库（可能重复点了一次）：刷新页面看看是不是已经在里面了；"
                    + "只是在改数量的话，展开那一行直接改，不用重新导入";
        }
        if (message.contains("truncation error")) {
            return "导入失败：有字段超出长度限制（料号最多 100 个字符），请缩短后再试";
        }
        return "导入失败，库里的数据没有变化：" + firstSentence(message);
    }

    /**
     * The first sentence of a technical message, so a page keeps the reason without the SQL dump.
     *
     * @param message the raw message, may be {@code null}
     * @return the message up to the first full stop or 160 characters
     */
    private static String firstSentence(String message) {
        String text = message == null ? "" : message.trim();
        int dot = text.indexOf(". ");
        if (dot > 0) {
            text = text.substring(0, dot + 1);
        }
        int colon = text.indexOf("(SQL Exception");
        if (colon > 0) {
            text = text.substring(0, colon);
        }
        return text.length() > 160 ? text.substring(0, 160) + "…" : text;
    }

    // ------------------------------------------------------------ refunded / closed shop orders

    /**
     * Remembers a shop order that was refunded or closed, so it stays out of the stock for good.
     * A failure to write the row is logged and does not stop the refusal: the order is not booked
     * in either way.
     *
     * @param delegator the delegator
     * @param orderNo the shop order number, ignored when empty
     * @param shop the shop, may be {@code null}
     * @param note why it is on the list
     */
    static void rememberRefundedOrder(Delegator delegator, String orderNo, String shop, String note) {
        if (orderNo == null) {
            return;
        }
        try {
            GenericValue row = delegator.makeValue("MfgRefundedOrder");
            row.set("orderNo", orderNo);
            row.set("shop", shop);
            row.set("note", note);
            row.set("recordedDate", Timestamp.valueOf(LocalDateTime.now()));
            delegator.createOrStore(row);
        } catch (GenericEntityException e) {
            Debug.logError(e, "Could not remember the refunded order [" + orderNo + "]", MODULE);
        }
    }

    /**
     * Remembers a refunded order from inside a service that is about to fail, so the row must be
     * written in a transaction of its own: the error return of {@code mfgReceiptFromOrder} rolls
     * back everything the same transaction did.
     *
     * @param dctx the dispatch context, holding the dispatcher
     * @param context the service context, for the logged in account
     * @param orderNo the shop order number
     * @param shop the shop, may be {@code null}
     * @param note why it is on the list
     */
    private static void rememberRefundedOrderInNewTransaction(DispatchContext dctx,
            Map<String, ? extends Object> context, String orderNo, String shop, String note) {
        if (orderNo == null || dctx.getDispatcher() == null) {
            return;
        }
        Map<String, Object> ask = UtilMisc.toMap("orderNo", orderNo, "shop", shop, "note", note,
                "userLogin", context.get("userLogin"));
        try {
            dctx.getDispatcher().runSync("mfgRefundedOrderSave", ask, -1, true);
        } catch (GenericServiceException e) {
            Debug.logError(e, "Could not remember the refunded order [" + orderNo + "]", MODULE);
        }
    }

    /**
     * Tells whether an order number is on the refunded list, and says since when.
     *
     * @param delegator the delegator
     * @param orderNo the shop order number, may be {@code null}
     * @return the message to show the person, {@code null} when the order may be booked in
     * @throws GenericEntityException when the list cannot be read
     */
    static String refundedOrderProblem(Delegator delegator, String orderNo) throws GenericEntityException {
        if (orderNo == null) {
            return null;
        }
        GenericValue row = EntityQuery.use(delegator).from("MfgRefundedOrder")
                .where("orderNo", orderNo).queryFirst();
        if (row == null) {
            return null;
        }
        Object recorded = row.get("recordedDate");
        String when = recorded instanceof Timestamp
                ? new SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format((Timestamp) recorded) + " "
                : "";
        return "这张单 " + when + "已经登记为退款/关闭，按规矩不入库（钱退回来了，料一般也没留下）；"
                + "如果记错了，在「退款 / 关闭的单」里撤掉标记再来";
    }

    /**
     * Adds an order number to the refunded list by hand, for the times the screenshot does not say
     * it and the person knows it anyway.
     *
     * @param dctx the dispatch context
     * @param context service context, see {@code servicedef/services.xml#mfgRefundedOrderSave}
     * @return the service result containing {@code orderNo}
     */
    public static Map<String, Object> refundedOrderSave(DispatchContext dctx, Map<String, ? extends Object> context) {
        String denied = permissionProblem(dctx, context, PERM_STOCK);
        if (denied != null) {
            return ServiceUtil.returnError(denied);
        }
        String orderNo = MfgSupport.toText(context.get("orderNo"));
        if (orderNo == null) {
            return ServiceUtil.returnError("请填店铺订单号（淘宝订单号）");
        }
        Delegator delegator = dctx.getDelegator();
        try {
            rememberRefundedOrder(delegator, orderNo, MfgSupport.toText(context.get("shop")),
                    MfgSupport.toText(context.get("note")) == null ? "手工登记" : MfgSupport.toText(context.get("note")));
            Map<String, Object> result = ServiceUtil.returnSuccess();
            result.put("orderNo", orderNo);
            return result;
        } catch (RuntimeException e) {
            return ServiceUtil.returnError("登记失败：" + e.getMessage());
        }
    }

    /**
     * Takes an order number off the refunded list again, for the times it was marked by mistake.
     *
     * @param dctx the dispatch context
     * @param context service context, see {@code servicedef/services.xml#mfgRefundedOrderDelete}
     * @return the service result containing {@code orderNo}
     */
    public static Map<String, Object> refundedOrderDelete(DispatchContext dctx, Map<String, ? extends Object> context) {
        String denied = permissionProblem(dctx, context, PERM_STOCK);
        if (denied != null) {
            return ServiceUtil.returnError(denied);
        }
        String orderNo = MfgSupport.toText(context.get("orderNo"));
        if (orderNo == null) {
            return ServiceUtil.returnError("请告诉我撤掉哪一张单");
        }
        try {
            dctx.getDelegator().removeByAnd("MfgRefundedOrder", UtilMisc.toMap("orderNo", orderNo));
            Map<String, Object> result = ServiceUtil.returnSuccess();
            result.put("orderNo", orderNo);
            return result;
        } catch (GenericEntityException e) {
            return ServiceUtil.returnError("取消失败：" + e.getMessage());
        }
    }

    /**
     * Lists the orders that are kept out of the stock, newest first.
     *
     * @param dctx the dispatch context
     * @param context service context, see {@code servicedef/services.xml#mfgRefundedOrderList}
     * @return the service result containing {@code refundedOrders}
     */
    public static Map<String, Object> refundedOrderList(DispatchContext dctx, Map<String, ? extends Object> context) {
        Delegator delegator = dctx.getDelegator();
        List<Map<String, Object>> list = new ArrayList<>();
        try {
            for (GenericValue row : EntityQuery.use(delegator).from("MfgRefundedOrder")
                    .orderBy("-recordedDate").queryList()) {
                Map<String, Object> map = new LinkedHashMap<>();
                map.put("orderNo", row.getString("orderNo"));
                map.put("shop", row.getString("shop"));
                map.put("note", row.getString("note"));
                map.put("recordedDate", row.get("recordedDate"));
                list.add(map);
            }
        } catch (GenericEntityException e) {
            Debug.logError(e, "Could not read the refunded orders", MODULE);
        }
        Map<String, Object> result = ServiceUtil.returnSuccess();
        result.put("refundedOrders", list);
        return result;
    }

    /**
     * Writes new numbers into a line that is already on the receipt, and brings its lot and the
     * receipt movement along, so the stock and the traceability keep saying the same thing.
     *
     * @param delegator the delegator
     * @param line the receipt line of the same part
     * @param receiptDate the arrival day of the order
     * @param qty the new quantity
     * @param amount the new goods value
     * @throws GenericEntityException when a row could not be stored
     */
    static void mergeOrderLine(Delegator delegator, GenericValue line, java.sql.Date receiptDate,
            double qty, double amount) throws GenericEntityException {
        double unitPrice = MfgSupport.round2(amount / qty);
        String lotId = line.getString("lotId");
        if (lotId != null) {
            GenericValue lot = EntityQuery.use(delegator).from("MfgMaterialLot")
                    .where("lotId", lotId).queryFirst();
            if (lot != null) {
                lot.set("receivedQty", Double.valueOf(qty));
                lot.set("receivedAmount", Double.valueOf(MfgSupport.round2(amount)));
                lot.set("unitPrice", Double.valueOf(unitPrice));
                lot.set("receivedDate", receiptDate);
                lot.store();
                GenericValue movement = EntityQuery.use(delegator).from("MfgInventoryDetail")
                        .where("lotId", lotId, "movementTypeId", MOVE_RECEIPT).queryFirst();
                if (movement != null) {
                    movement.set("quantityDiff", Double.valueOf(qty));
                    movement.store();
                }
                MfgStockLedger.recalcLotStock(delegator, lot);
            }
        }
        line.set("qty", Double.valueOf(qty));
        line.set("unitPrice", Double.valueOf(unitPrice));
        line.set("goodsAmount", Double.valueOf(MfgSupport.round2(amount)));
        line.store();
    }

    /**
     * Reads the lines of the receipt of one shop order, so the page can ask before an order that
     * is already in is booked a second time.
     *
     * @param dctx the dispatch context
     * @param context service context, see {@code servicedef/services.xml#mfgReceiptLinesOfOrder}
     * @return the service result containing {@code receiptId} and {@code lines}
     */
    public static Map<String, Object> receiptLinesOfOrder(DispatchContext dctx, Map<String, ? extends Object> context) {
        Delegator delegator = dctx.getDelegator();
        Map<String, Object> result = ServiceUtil.returnSuccess();
        result.put("lines", new ArrayList<Map<String, Object>>());
        String supplierOrderNo = MfgSupport.toText(context.get("supplierOrderNo"));
        if (supplierOrderNo == null) {
            return result;
        }
        try {
            GenericValue receipt = EntityQuery.use(delegator).from("MfgPurchaseReceipt")
                    .where("supplierOrderNo", supplierOrderNo).queryFirst();
            if (receipt == null || RECEIPT_VOID.equals(statusOfReceipt(receipt))) {
                // a voided receipt is not “already in”: the page should not ask about overwriting it
                return result;
            }
            List<Map<String, Object>> lines = new ArrayList<>();
            for (GenericValue line : EntityQuery.use(delegator).from("MfgPurchaseReceiptLine")
                    .where("receiptId", receipt.getString("receiptId")).orderBy("lineSeq").queryList()) {
                Map<String, Object> map = new LinkedHashMap<>();
                map.put("componentCode", line.getString("componentCode"));
                map.put("qty", line.get("qty"));
                map.put("goodsAmount", line.get("goodsAmount"));
                lines.add(map);
            }
            result.put("receiptId", receipt.getString("receiptId"));
            result.put("lines", lines);
            return result;
        } catch (GenericEntityException e) {
            return ServiceUtil.returnError("Could not read the receipt of that order: " + e.getMessage());
        }
    }

    /**
     * Reads the order lines typed or pasted by the person: {@code code,name,quantity,amount} per
     * line, separated by comma or tab; blank lines and lines starting with {@code #} are ignored.
     *
     * @param text the pasted text
     * @return one array per line: code, name, quantity, amount (as text)
     */
    public static String fittingComponentCode(String code) {
        // the spaces between the words are part of the number (5.5V1.5F C型 21*7.5); only runs of
        // blanks are squeezed, so F, C型 and 21*7.5 do not run together
        String trimmed = code == null ? "" : code.trim().replaceAll("\\s+", " ");
        if (trimmed.length() <= MAX_COMPONENT_CODE) {
            return trimmed;
        }
        String hash = String.format(Locale.ROOT, "%06x", ((long) trimmed.hashCode()) & 0xffffffL);
        int keep = MAX_COMPONENT_CODE - hash.length() - 1;
        String head = trimmed.substring(0, keep).trim();
        int cut = head.lastIndexOf(' ');
        if (cut >= keep - 8) {
            head = head.substring(0, cut);
        }
        return head + "-" + hash;
    }

    /**
     * Rewrites the code column of pasted order lines so every code fits the code field. A name may
     * hold commas, so the rows are joined with tabs, which the parser accepts too.
     *
     * @param lines the pasted or recognised lines
     * @return the same lines with fitting codes
     */
    public static String nameWithFullCode(String name, String fullCode) {
        String base = name == null ? "" : name.trim();
        if (fullCode == null || fullCode.isEmpty() || base.contains(fullCode)) {
            return base;
        }
        String combined = base.isEmpty() ? fullCode : base + ' ' + fullCode;
        return combined.length() <= 100 ? combined : combined.substring(0, 100);
    }

    private static String lotLineKey(String componentCode, Object qty, Object amount) {
        return componentCode + "|" + MfgSupport.toDouble(qty, 0.0) + "|" + MfgSupport.round2(MfgSupport.toDouble(amount, 0.0));
    }

    /**
     * Takes the first line of a list the answer has not used yet, so a second row with the same part
     * number lands on the second line of the receipt instead of on top of the first (a shop prints
     * one board in six colours as six lines of the same part number).
     *
     * @param lines the lines of the receipt with that part number or those numbers
     * @param claimed the lines already matched to a row of this answer
     * @return the line to use, {@code null} when all of them are taken
     */
    private static GenericValue claimOne(List<GenericValue> lines, Set<String> claimed) {
        if (lines == null) {
            return null;
        }
        for (GenericValue line : lines) {
            if (claimed.add(receiptLineId(line))) {
                return line;
            }
        }
        return null;
    }

    /**
     * @param line one line of a receipt
     * @return its identity, the primary key of the entity as text
     */
    private static String receiptLineId(GenericValue line) {
        return line.getString("receiptId") + "#" + line.getString("lineSeq");
    }

    /**
     * Turns the answer of a vision model into one field map per order. A screenshot may show a
     * single order (the detail page) or a list of several orders (the order list of the shop), so
     * both shapes are read: {@code {"orders":[{…},{…}]}} and the older flat {@code {"lines":"…"}}.
     *
     * @param reply the answer text
     * @return one map per order that carries lines, empty when there is nothing usable
     */
    private static String nextReceiptId(Delegator delegator, LocalDate today) throws GenericEntityException {
        String prefix = "PR-" + today.format(DAY) + "-";
        int max = 0;
        for (GenericValue receipt : EntityQuery.use(delegator).from("MfgPurchaseReceipt").queryList()) {
            String id = receipt.getString("receiptId");
            if (id != null && id.startsWith(prefix)) {
                max = Math.max(max, parseTrailingNumber(id.substring(prefix.length())));
            }
        }
        return prefix + String.format(Locale.ROOT, "%02d", max + 1);
    }

    private static long nextReceiptLineSeq(Delegator delegator, String receiptId) throws GenericEntityException {
        long max = 0L;
        for (GenericValue line : EntityQuery.use(delegator).from("MfgPurchaseReceiptLine")
                .where("receiptId", receiptId).queryList()) {
            Long seq = line.getLong("lineSeq");
            if (seq != null) {
                max = Math.max(max, seq.longValue());
            }
        }
        return max + 10L;
    }

    /**
     * The first line number at or above {@code candidate} that the receipt does not use yet.
     *
     * <p>Two imports of the same order can run at the same time (the page takes a while and somebody
     * clicks again): both would read the same maximum and the second insert would die on the primary
     * key of the line table. Asking the table for a free number right before the insert keeps that
     * from happening, and the import runs in one transaction, so a real clash rolls everything back.
     *
     * @param delegator the delegator
     * @param receiptId the receipt
     * @param candidate the line number to start from
     * @return a line number the receipt does not use
     * @throws GenericEntityException when the lines cannot be read
     */
    private static long freeReceiptLineSeq(Delegator delegator, String receiptId, long candidate)
            throws GenericEntityException {
        java.util.Set<Long> used = new java.util.HashSet<>();
        for (GenericValue line : EntityQuery.use(delegator).from("MfgPurchaseReceiptLine")
                .where("receiptId", receiptId).queryList()) {
            Long seq = line.getLong("lineSeq");
            if (seq != null) {
                used.add(seq);
            }
        }
        long seq = candidate;
        while (used.contains(Long.valueOf(seq))) {
            seq += 10L;
        }
        return seq;
    }

    private static Map<String, Object> toReceiptMap(GenericValue receipt) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("receiptId", receipt.getString("receiptId"));
        map.put("supplierName", receipt.getString("supplierName"));
        map.put("supplierOrderNo", receipt.getString("supplierOrderNo"));
        map.put("receiptDate", receipt.get("receiptDate"));
        map.put("freightAmount", receipt.get("freightAmount"));
        // without this the edit form always showed 0.00 and saving it wiped the discount
        map.put("discountAmount", receipt.get("discountAmount"));
        map.put("allocationId", receipt.getString("allocationId"));
        map.put("statusId", statusOfReceipt(receipt));
        map.put("voidedDate", receipt.get("voidedDate"));
        map.put("voidedByUserLoginId", receipt.getString("voidedByUserLoginId"));
        map.put("voidReason", receipt.getString("voidReason"));
        map.put("note", receipt.getString("note"));
        return map;
    }

    /**
     * The state of a receipt: rows written before voiding existed carry no status and are active.
     *
     * @param receipt the receipt row
     * @return {@link #RECEIPT_VOID} or {@link #RECEIPT_ACTIVE}
     */
    private static String statusOfReceipt(GenericValue receipt) {
        if (receipt == null) {
            return RECEIPT_ACTIVE;
        }
        String status = receipt.getString("statusId");
        return RECEIPT_VOID.equals(status) ? RECEIPT_VOID : RECEIPT_ACTIVE;
    }

    private static Map<String, Object> toReceiptLineMap(GenericValue line) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("receiptId", line.getString("receiptId"));
        map.put("lineSeq", line.getLong("lineSeq"));
        map.put("lotId", line.getString("lotId"));
        map.put("componentCode", line.getString("componentCode"));
        map.put("componentName", line.getString("componentName"));
        map.put("qty", line.get("qty"));
        map.put("unitPrice", line.get("unitPrice"));
        // the price with the decimals the column cannot keep: 2.91 for 100 pieces is 0.0291
        double qty = MfgSupport.numberOrZero(line.get("qty"));
        map.put("unitPriceExact", Double.valueOf(qty > 0.0
                ? MfgSupport.numberOrZero(line.get("goodsAmount")) / qty : MfgSupport.numberOrZero(line.get("unitPrice"))));
        map.put("goodsAmount", line.get("goodsAmount"));
        map.put("note", line.getString("note"));
        return map;
    }

    // ==================================================================
    // Settings kept in the database, so nothing has to be added to the start script
    // ==================================================================

    /** Resource id the component writes its settings under in {@code SystemProperty}. */
    public static final String SETTINGS_RESOURCE = "mfg";

    /**
     * Reads one setting: the value saved on the settings page first, then the system property of
     * the same name, so an existing start script keeps working.
     *
     * @param delegator the delegator, may be {@code null}
     * @param id the property id, e.g. {@code org.langhua.mfg.vision.endpoint}
     * @return the value, {@code null} when neither the database nor the JVM knows it
     */
    public static String setting(Delegator delegator, String id) {
        if (delegator != null) {
            try {
                GenericValue row = EntityQuery.use(delegator).from("SystemProperty")
                        .where("systemResourceId", SETTINGS_RESOURCE, "systemPropertyId", id).queryOne();
                if (row != null) {
                    String value = MfgSupport.toText(row.getString("systemPropertyValue"));
                    if (value != null) {
                        return value;
                    }
                }
            } catch (GenericEntityException e) {
                // the table may not be there yet on a very old database: fall back to the JVM
                return MfgSupport.toText(System.getProperty(id));
            }
        }
        return MfgSupport.toText(System.getProperty(id));
    }

    /**
     * Turns a partly typed endpoint into the URL the API expects: many people type only the host or
     * stop at the version, and the API answers those with 404.
     *
     * @param endpoint what the settings hold
     * @return the chat completions URL, {@code null} for a null input
     */
    public static Map<String, Object> locationSave(DispatchContext dctx, Map<String, ? extends Object> context) {
        String denied = permissionProblem(dctx, context, PERM_STOCK);
        if (denied != null) {
            return ServiceUtil.returnError(denied);
        }
        Delegator delegator = dctx.getDelegator();
        String locationId = MfgSupport.toText(context.get("locationId"));
        String locationTypeId = MfgSupport.toText(context.get("locationTypeId"));
        if (locationId == null) {
            return ServiceUtil.returnError("locationId is required, it is the code printed on the shelf");
        }
        if (locationTypeId == null) {
            locationTypeId = LOC_BIN;
        }
        if (!isKnownLocationType(locationTypeId)) {
            return ServiceUtil.returnError("Unknown location type [" + locationTypeId + "], expected "
                    + String.join(" / ", LOC_TYPES));
        }
        try {
            GenericValue location = EntityQuery.use(delegator).from("MfgStockLocation")
                    .where("locationId", locationId).queryOne();
            if (location == null) {
                location = delegator.makeValue("MfgStockLocation");
                location.set("locationId", locationId);
            }
            location.set("locationTypeId", locationTypeId);
            location.set("locationName", MfgSupport.toText(context.get("locationName")));
            location.set("parentLocationId", MfgSupport.toText(context.get("parentLocationId")));
            if (context.containsKey("note")) {
                location.set("note", MfgSupport.toText(context.get("note")));
            }
            delegator.createOrStore(location);

            Map<String, Object> result = ServiceUtil.returnSuccess();
            result.put("locationId", locationId);
            result.put("locationTypeId", locationTypeId);
            result.put("onHandQty", Double.valueOf(MfgSupport.round2(MfgStockLedger.locationOnHand(delegator, null, locationId))));
            return result;
        } catch (GenericEntityException e) {
            return ServiceUtil.returnError("Could not save the location: " + e.getMessage());
        }
    }

    /**
     * The stock locations with what each one holds right now.
     *
     * @param dctx the dispatch context
     * @param context service context, see {@code servicedef/services.xml#mfgLocationList}
     * @return the service result containing {@code locations} and the unassigned quantity
     */
    public static Map<String, Object> locationList(DispatchContext dctx, Map<String, ? extends Object> context) {
        Delegator delegator = dctx.getDelegator();
        Map<String, Object> result = ServiceUtil.returnSuccess();
        result.put("locations", new ArrayList<Map<String, Object>>());
        result.put("unassignedQty", Double.valueOf(0.0));
        result.put("onHandTotal", Double.valueOf(0.0));
        try {
            List<Map<String, Object>> rows = new ArrayList<>();
            double unassigned = 0.0;
            double total = 0.0;
            Map<String, Integer> lotCounts = new LinkedHashMap<>();
            Map<String, Double> onHand = new LinkedHashMap<>();
            for (GenericValue movement : EntityQuery.use(delegator).from("MfgInventoryDetail").queryList()) {
                String locationId = movement.getString("locationId");
                double diff = MfgSupport.numberOrZero(movement.get("quantityDiff"));
                if (locationId == null) {
                    unassigned += diff;
                    continue;
                }
                onHand.put(locationId, Double.valueOf(MfgSupport.numberOrZero(onHand.get(locationId)) + diff));
                if (diff > 0.0) {
                    Integer count = lotCounts.get(locationId);
                    lotCounts.put(locationId, Integer.valueOf(count == null ? 1 : count.intValue() + 1));
                }
                total += diff;
            }
            for (GenericValue location : EntityQuery.use(delegator).from("MfgStockLocation")
                    .orderBy("locationId").queryList()) {
                String locationId = location.getString("locationId");
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("locationId", locationId);
                row.put("locationName", location.getString("locationName"));
                row.put("locationTypeId", location.getString("locationTypeId"));
                row.put("parentLocationId", location.getString("parentLocationId"));
                row.put("onHandQty", Double.valueOf(MfgSupport.round2(MfgSupport.numberOrZero(onHand.get(locationId)))));
                row.put("lotCount", lotCounts.containsKey(locationId) ? lotCounts.get(locationId)
                        : Integer.valueOf(0));
                row.put("note", location.getString("note"));
                rows.add(row);
            }
            result.put("locations", rows);
            result.put("unassignedQty", Double.valueOf(MfgSupport.round2(unassigned)));
            result.put("onHandTotal", Double.valueOf(MfgSupport.round2(total + unassigned)));
            return result;
        } catch (GenericEntityException e) {
            return ServiceUtil.returnError("Could not read the locations: " + e.getMessage());
        }
    }

    /**
     * What sits where: the balance of every lot in every location, so a shelf can be checked
     * against the screen.
     *
     * @param dctx the dispatch context
     * @param context service context, see {@code servicedef/services.xml#mfgLocationStock}
     * @return the service result containing {@code rows}
     */
    public static Map<String, Object> locationStock(DispatchContext dctx, Map<String, ? extends Object> context) {
        Delegator delegator = dctx.getDelegator();
        String locationId = MfgSupport.toText(context.get("locationId"));
        String componentCode = MfgSupport.toText(context.get("componentCode"));
        Map<String, Object> result = ServiceUtil.returnSuccess();
        result.put("rows", new ArrayList<Map<String, Object>>());
        try {
            Map<String, GenericValue> lots = new LinkedHashMap<>();
            for (GenericValue lot : EntityQuery.use(delegator).from("MfgMaterialLot").queryList()) {
                lots.put(lot.getString("lotId"), lot);
            }
            Map<String, Double> balances = new LinkedHashMap<>();
            for (GenericValue movement : EntityQuery.use(delegator).from("MfgInventoryDetail")
                    .orderBy("lotId", "seqId").queryList()) {
                String key = movement.getString("lotId") + "\u0000"
                        + (movement.getString("locationId") == null ? "" : movement.getString("locationId"));
                balances.put(key, Double.valueOf(MfgSupport.numberOrZero(balances.get(key))
                        + MfgSupport.numberOrZero(movement.get("quantityDiff"))));
            }
            List<Map<String, Object>> rows = new ArrayList<>();
            for (Map.Entry<String, Double> entry : balances.entrySet()) {
                if (Math.abs(entry.getValue().doubleValue()) < 0.0001) {
                    continue;
                }
                int split = entry.getKey().indexOf('\u0000');
                String lotId = entry.getKey().substring(0, split);
                String rowLocation = entry.getKey().substring(split + 1);
                GenericValue lot = lots.get(lotId);
                String rowComponent = lot == null ? null : lot.getString("componentCode");
                if (locationId != null && !locationId.equals(rowLocation)) {
                    continue;
                }
                if (componentCode != null && !componentCode.equals(rowComponent)) {
                    continue;
                }
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("lotId", lotId);
                row.put("locationId", rowLocation.isEmpty() ? null : rowLocation);
                row.put("lotKindId", lot == null ? LOT_COMPONENT : MfgStockLedger.lotKindOf(lot));
                row.put("componentCode", rowComponent);
                row.put("componentName", lot == null ? null : lot.getString("componentName"));
                row.put("onHandQty", Double.valueOf(MfgSupport.round2(entry.getValue().doubleValue())));
                row.put("receivedDate", lot == null ? null : lot.get("receivedDate"));
                rows.add(row);
            }
            result.put("rows", rows);
            result.put("locationId", locationId);
            result.put("componentCode", componentCode);
            return result;
        } catch (GenericEntityException e) {
            return ServiceUtil.returnError("Could not read the location stock: " + e.getMessage());
        }
    }

    /**
     * Moves a quantity of one lot from one location to another: two rows of the same transfer number,
     * one leaving and one arriving, so the lot total does not change.
     *
     * @param dctx the dispatch context
     * @param context service context, see {@code servicedef/services.xml#mfgStockTransfer}
     * @return the service result with the transfer number
     */
    public static Map<String, Object> stockTransfer(DispatchContext dctx, Map<String, ? extends Object> context) {
        String denied = permissionProblem(dctx, context, PERM_STOCK);
        if (denied != null) {
            return ServiceUtil.returnError(denied);
        }
        Delegator delegator = dctx.getDelegator();
        String lotId = MfgSupport.toText(context.get("lotId"));
        String fromLocationId = MfgSupport.toText(context.get("fromLocationId"));
        String toLocationId = MfgSupport.toText(context.get("toLocationId"));
        double qty = MfgSupport.toDouble(context.get("qty"), 0.0);
        if (lotId == null) {
            return ServiceUtil.returnError("lotId is required");
        }
        if (toLocationId == null) {
            return ServiceUtil.returnError("toLocationId is required");
        }
        if (toLocationId.equals(fromLocationId)) {
            return ServiceUtil.returnError("The source and the target location are the same");
        }
        if (qty <= 0.0) {
            return ServiceUtil.returnError("qty must be greater than zero");
        }
        try {
            GenericValue lot = EntityQuery.use(delegator).from("MfgMaterialLot")
                    .where("lotId", lotId).queryOne();
            if (lot == null) {
                return ServiceUtil.returnError("Unknown material lot [" + lotId + "]");
            }
            if (EntityQuery.use(delegator).from("MfgStockLocation")
                    .where("locationId", toLocationId).queryOne() == null) {
                return ServiceUtil.returnError("Unknown location [" + toLocationId + "], create it first");
            }
            if (fromLocationId != null && EntityQuery.use(delegator).from("MfgStockLocation")
                    .where("locationId", fromLocationId).queryOne() == null) {
                return ServiceUtil.returnError("Unknown location [" + fromLocationId + "]");
            }
            MfgStockLedger.ensureLotLedger(delegator, lot);
            double here = MfgStockLedger.locationOnHand(delegator, lotId, fromLocationId);
            if (here + 0.0001 < qty) {
                return ServiceUtil.returnError("Only " + MfgSupport.formatQty(here) + " left in location ["
                        + (fromLocationId == null ? "?" : fromLocationId) + "], cannot move " + MfgSupport.formatQty(qty));
            }
            String transferId = nextTransferId(delegator, LocalDate.now());
            String operator = MfgSupport.toText(context.get("operatorName"));
            String note = MfgSupport.toText(context.get("note"));
            MfgStockLedger.recordMovement(delegator, lot, MOVE_TRANSFER, -qty, lot.get("unitPrice"), null, null,
                    transferId, operator, null,
                    "移库 " + transferId + "：" + (fromLocationId == null ? "未分配" : fromLocationId)
                            + " → " + toLocationId + (note == null ? "" : "，" + note), fromLocationId);
            MfgStockLedger.recordMovement(delegator, lot, MOVE_TRANSFER, qty, lot.get("unitPrice"), null, null,
                    transferId, operator, null,
                    "移库 " + transferId + "：" + (fromLocationId == null ? "未分配" : fromLocationId)
                            + " → " + toLocationId + (note == null ? "" : "，" + note), toLocationId);
            MfgStockLedger.recalcLotStock(delegator, lot);

            Map<String, Object> result = ServiceUtil.returnSuccess();
            result.put("transferId", transferId);
            result.put("lotId", lotId);
            result.put("fromLocationId", fromLocationId);
            result.put("toLocationId", toLocationId);
            result.put("locationId", toLocationId);
            result.put("qty", Double.valueOf(MfgSupport.round2(qty)));
            result.put("toLocationQty", Double.valueOf(MfgSupport.round2(MfgStockLedger.locationOnHand(delegator, lotId, toLocationId))));
            result.put("onHandQty", lot.get("onHandQty"));
            return result;
        } catch (GenericEntityException e) {
            return ServiceUtil.returnError("Could not move the stock: " + e.getMessage());
        }
    }

    private static String nextTransferId(Delegator delegator, LocalDate today) throws GenericEntityException {
        String prefix = TRANSFER_PREFIX + today.format(DAY) + "-";
        int max = 0;
        for (GenericValue movement : EntityQuery.use(delegator).from("MfgInventoryDetail")
                .where("movementTypeId", MOVE_TRANSFER).queryList()) {
            String reference = movement.getString("referenceNo");
            if (reference != null && reference.startsWith(prefix)) {
                max = Math.max(max, parseTrailingNumber(reference.substring(prefix.length())));
            }
        }
        return prefix + String.format(Locale.ROOT, "%02d", Integer.valueOf(max + 1));
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
        String denied = permissionProblem(dctx, context, PERM_STOCK);
        if (denied != null) {
            return ServiceUtil.returnError(denied);
        }
        Delegator delegator = dctx.getDelegator();
        String lotId = MfgSupport.toText(context.get("lotId"));
        String movementTypeId = MfgSupport.toText(context.get("movementTypeId"));
        String reasonEnumId = MfgSupport.toText(context.get("reasonEnumId"));
        double qty = MfgSupport.toDouble(context.get("qty"), 0.0);
        String workOrderId = MfgSupport.toText(context.get("workOrderId"));
        if (lotId == null) {
            return ServiceUtil.returnError("lotId is required");
        }
        // the reason comes from OFBiz's own IID_REASON list; when only the reason is given, the type
        // follows from it (丢失 / 被盗 / 损坏 就是报废，找到 / 供方少发 就是补入库)
        String reasonProblem = MfgStockLedger.movementReasonProblem(movementTypeId, reasonEnumId, qty);
        if (reasonProblem != null) {
            return ServiceUtil.returnError(reasonProblem);
        }
        if (movementTypeId == null && reasonEnumId != null) {
            movementTypeId = MfgStockLedger.movementTypeOfReason(reasonEnumId);
        }
        if (!MfgStockLedger.isKnownMovement(movementTypeId)) {
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
            MfgStockLedger.ensureLotLedger(delegator, lot);
            double diff = MfgStockLedger.movementDiff(movementTypeId, qty);
            if (diff < 0.0) {
                double ownReservation = workOrderId == null ? 0.0
                        : MfgStockLedger.reservationOf(delegator, lotId, workOrderId);
                double available = MfgSupport.numberOrZero(lot.get("availableQty")) + ownReservation;
                if (available + 0.0001 < -diff) {
                    return ServiceUtil.returnError("Only " + MfgSupport.formatQty(available) + " left in lot [" + lotId
                            + "], cannot take " + MfgSupport.formatQty(-diff));
                }
            }
            if (workOrderId != null
                    && (MOVE_ISSUE.equals(movementTypeId) || MOVE_RETURN.equals(movementTypeId))) {
                if (EntityQuery.use(delegator).from("MfgWorkOrder")
                        .where("workOrderId", workOrderId).queryOne() == null) {
                    return ServiceUtil.returnError("Unknown work order [" + workOrderId + "]");
                }
            }
            GenericValue movement = MfgStockLedger.recordMovement(delegator, lot, movementTypeId, diff,
                    MfgSupport.optionalAmount(context.get("unitCost")), workOrderId, null,
                    MfgSupport.toText(context.get("referenceNo")), MfgSupport.toText(context.get("operatorName")),
                    MfgSupport.parseTimestamp(context.get("effectiveDate")), MfgSupport.toText(context.get("note")),
                    MfgSupport.toText(context.get("locationId")), reasonEnumId);
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
                issue.set("note", MfgSupport.toText(context.get("note")));
                delegator.create(issue);
            }
            if (MOVE_ISSUE.equals(movementTypeId) && workOrderId != null) {
                MfgStockLedger.releaseReservation(delegator, lot, workOrderId, -diff, MfgSupport.toText(context.get("operatorName")));
            }
            MfgStockLedger.recalcLotStock(delegator, lot);

            Map<String, Object> result = ServiceUtil.returnSuccess();
            result.put("lotId", lotId);
            result.put("seqId", movement.get("seqId"));
            result.put("movementTypeId", movementTypeId);
            result.put("reasonEnumId", movement.get("reasonEnumId"));
            result.put("quantityDiff", Double.valueOf(MfgSupport.round2(diff)));
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
                if (MfgStockLedger.isProductLot(lot)) {
                    continue;
                }
                MfgStockLedger.ensureLotLedger(delegator, lot);
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
                double onHand = MfgSupport.numberOrZero(lot.get("onHandQty"));
                row.put("onHandQty", Double.valueOf(MfgSupport.numberOrZero(row.get("onHandQty")) + onHand));
                row.put("reservedQty", Double.valueOf(MfgSupport.numberOrZero(row.get("reservedQty"))
                        + MfgSupport.numberOrZero(lot.get("reservedQty"))));
                row.put("availableQty", Double.valueOf(MfgSupport.numberOrZero(row.get("availableQty"))
                        + MfgSupport.numberOrZero(lot.get("availableQty"))));
                row.put("lotCount", Integer.valueOf(((Integer) row.get("lotCount")).intValue() + 1));
                String inspect = lot.getString("inspectStatusId");
                if (IQC_PENDING.equals(inspect)) {
                    row.put("pendingQty", Double.valueOf(MfgSupport.numberOrZero(row.get("pendingQty")) + onHand));
                }
                if (IQC_FAIL.equals(inspect)) {
                    row.put("failedQty", Double.valueOf(MfgSupport.numberOrZero(row.get("failedQty")) + onHand));
                }
                Object price = lot.get("unitPrice");
                if (price instanceof Number) {
                    row.put("value", Double.valueOf(MfgSupport.numberOrZero(row.get("value"))
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
                double minimum = setting == null ? 0.0 : MfgSupport.numberOrZero(setting.get("minimumStock"));
                double reorder = setting == null ? 0.0 : MfgSupport.numberOrZero(setting.get("reorderQty"));
                if (setting != null && setting.getString("componentName") != null) {
                    row.put("componentName", setting.getString("componentName"));
                }
                row.put("minimumStock", Double.valueOf(MfgSupport.round2(minimum)));
                row.put("reorderQty", Double.valueOf(MfgSupport.round2(reorder)));
                row.put("unitOfMeasure", setting == null ? null : setting.getString("unitOfMeasure"));
                row.put("supplierName", setting == null ? null : setting.getString("supplierName"));
                row.put("onHandQty", Double.valueOf(MfgSupport.round2(MfgSupport.numberOrZero(row.get("onHandQty")))));
                row.put("reservedQty", Double.valueOf(MfgSupport.round2(MfgSupport.numberOrZero(row.get("reservedQty")))));
                row.put("availableQty", Double.valueOf(MfgSupport.round2(MfgSupport.numberOrZero(row.get("availableQty")))));
                row.put("value", Double.valueOf(MfgSupport.round2(MfgSupport.numberOrZero(row.get("value")))));
                boolean isBelow = minimum > 0.0 && MfgSupport.numberOrZero(row.get("onHandQty")) < minimum;
                row.put("belowMinimum", Boolean.valueOf(isBelow));
                double suggested = 0.0;
                if (isBelow) {
                    suggested = reorder > 0.0 ? reorder
                            : MfgSupport.round2(minimum - MfgSupport.numberOrZero(row.get("onHandQty")));
                    below++;
                }
                row.put("suggestedQty", Double.valueOf(MfgSupport.round2(suggested)));
                onHandTotal += MfgSupport.numberOrZero(row.get("onHandQty"));
                valueTotal += MfgSupport.numberOrZero(row.get("value"));
                rows.add(row);
            }
            String search = MfgSupport.toText(context.get("search"));
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
            if ("Y".equalsIgnoreCase(MfgSupport.toText(context.get("belowOnly")))) {
                List<Map<String, Object>> matched = new ArrayList<>();
                for (Map<String, Object> row : rows) {
                    if (Boolean.TRUE.equals(row.get("belowMinimum"))) {
                        matched.add(row);
                    }
                }
                rows = matched;
            }
            MfgPaging.putPage(result, "rows", rows, MfgPaging.pageOf(context), MfgPaging.pageSizeOf(context), search);
            result.put("componentCount", Integer.valueOf(rows.size()));
            result.put("belowCount", Integer.valueOf(below));
            result.put("onHandTotal", Double.valueOf(MfgSupport.round2(onHandTotal)));
            result.put("valueTotal", Double.valueOf(MfgSupport.round2(valueTotal)));
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
        String lotId = MfgSupport.toText(context.get("lotId"));
        String componentCode = MfgSupport.toText(context.get("componentCode"));
        String movementTypeId = MfgSupport.toText(context.get("movementTypeId"));
        String reasonEnumId = MfgSupport.toText(context.get("reasonEnumId"));
        String kind = MfgSupport.toText(context.get("kind"));
        java.sql.Date fromDate = (java.sql.Date) MfgSupport.parseDate(context.get("fromDate"));
        java.sql.Date toDate = (java.sql.Date) MfgSupport.parseDate(context.get("toDate"));
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
                double diff = MfgSupport.numberOrZero(movement.get("quantityDiff"));
                double balance = MfgSupport.numberOrZero(balances.get(movementLot)) + diff;
                balances.put(movementLot, Double.valueOf(balance));
                if (lotId != null && !lotId.equals(movementLot)) {
                    continue;
                }
                if (movementTypeId != null && !movementTypeId.equals(movement.getString("movementTypeId"))) {
                    continue;
                }
                if (reasonEnumId != null && !reasonEnumId.equals(movement.getString("reasonEnumId"))) {
                    continue;
                }
                GenericValue lot = lots.get(movementLot);
                if (componentCode != null
                        && (lot == null || !componentCode.equals(lot.getString("componentCode")))) {
                    continue;
                }
                if (lot != null && !MfgStockLedger.matchesLotKind(lot, kind)) {
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
                row.put("lotKindId", lot == null ? LOT_COMPONENT : MfgStockLedger.lotKindOf(lot));
                row.put("locationId", movement.getString("locationId"));
                row.put("componentCode", lot == null ? null : lot.getString("componentCode"));
                row.put("componentName", lot == null ? null : lot.getString("componentName"));
                row.put("movementTypeId", movement.getString("movementTypeId"));
                row.put("quantityDiff", Double.valueOf(MfgSupport.round2(diff)));
                row.put("reservedDiff", Double.valueOf(MfgSupport.round2(MfgSupport.numberOrZero(movement.get("reservedDiff")))));
                row.put("displayQty", Double.valueOf(MfgSupport.round2(
                        MOVE_RESERVE.equals(movement.getString("movementTypeId"))
                                || MOVE_RELEASE.equals(movement.getString("movementTypeId"))
                                ? MfgSupport.numberOrZero(movement.get("reservedDiff")) : diff)));
                row.put("balanceQty", Double.valueOf(MfgSupport.round2(balance)));
                row.put("unitCost", movement.get("unitCost"));
                row.put("workOrderId", movement.getString("workOrderId"));
                row.put("physicalInventoryId", movement.getString("physicalInventoryId"));
                row.put("referenceNo", movement.getString("referenceNo"));
                row.put("reasonEnumId", movement.getString("reasonEnumId"));
                row.put("operatorName", movement.getString("operatorName"));
                row.put("effectiveDate", effective);
                row.put("note", movement.getString("note"));
                rows.add(row);
            }
            Collections.reverse(rows);
            MfgPaging.putPage(result, "rows", rows, MfgPaging.pageOf(context), MfgPaging.pageSizeOf(context), null);
            result.put("lotId", lotId);
            result.put("componentCode", componentCode);
            result.put("movementTypeId", movementTypeId);
            result.put("reasonEnumId", reasonEnumId);
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
        String denied = permissionProblem(dctx, context, PERM_STOCK);
        if (denied != null) {
            return ServiceUtil.returnError(denied);
        }
        Delegator delegator = dctx.getDelegator();
        String componentCode = MfgSupport.toText(context.get("componentCode"));
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
                setting.set("componentName", MfgSupport.toText(context.get("componentName")));
            }
            if (context.containsKey("unitOfMeasure")) {
                setting.set("unitOfMeasure", MfgSupport.toText(context.get("unitOfMeasure")));
            }
            if (context.containsKey("minimumStock")) {
                setting.set("minimumStock", Double.valueOf(MfgSupport.round2(MfgSupport.toDouble(context.get("minimumStock"), 0.0))));
            }
            if (context.containsKey("reorderQty")) {
                setting.set("reorderQty", Double.valueOf(MfgSupport.round2(MfgSupport.toDouble(context.get("reorderQty"), 0.0))));
            }
            if (context.containsKey("supplierName")) {
                setting.set("supplierName", MfgSupport.toText(context.get("supplierName")));
            }
            if (context.containsKey("note")) {
                setting.set("note", MfgSupport.toText(context.get("note")));
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
        String denied = permissionProblem(dctx, context, PERM_STOCK);
        if (denied != null) {
            return ServiceUtil.returnError(denied);
        }
        Delegator delegator = dctx.getDelegator();
        String lotId = MfgSupport.toText(context.get("lotId"));
        Object countedInput = context.get("countedQty");
        if (lotId == null) {
            return ServiceUtil.returnError("lotId is required");
        }
        if (MfgSupport.toText(countedInput) == null) {
            return ServiceUtil.returnError("countedQty is required");
        }
        double countedQty = MfgSupport.toDouble(countedInput, -1.0);
        if (countedQty < 0.0) {
            return ServiceUtil.returnError("countedQty must not be negative");
        }
        try {
            GenericValue lot = EntityQuery.use(delegator).from("MfgMaterialLot")
                    .where("lotId", lotId).queryOne();
            if (lot == null) {
                return ServiceUtil.returnError("Unknown material lot [" + lotId + "]");
            }
            MfgStockLedger.ensureLotLedger(delegator, lot);
            double bookQty = MfgSupport.numberOrZero(lot.get("onHandQty"));
            double variance = MfgSupport.round2(countedQty - bookQty);
            // the reason is optional, but when it is given it has to fit the direction of the variance:
            // a count that lost material may not be explained with "找到"
            String reasonEnumId = MfgSupport.toText(context.get("reasonEnumId"));
            String reasonProblem = MfgStockLedger.movementReasonProblem(MOVE_COUNT, reasonEnumId, variance);
            if (reasonProblem != null) {
                return ServiceUtil.returnError(reasonProblem);
            }
            String physicalInventoryId = nextStockCountId(delegator, LocalDate.now());
            String counterName = MfgSupport.toText(context.get("counterName"));
            GenericValue count = delegator.makeValue("MfgStockCount");
            count.set("physicalInventoryId", physicalInventoryId);
            Object countDate = MfgSupport.parseDate(context.get("countDate"));
            count.set("countDate", countDate == null
                    ? java.sql.Date.valueOf(LocalDate.now()) : countDate);
            count.set("counterName", counterName);
            count.set("lotId", lotId);
            count.set("bookQty", Double.valueOf(bookQty));
            count.set("countedQty", Double.valueOf(countedQty));
            count.set("varianceQty", Double.valueOf(variance));
            count.set("varianceReasonId", reasonEnumId);
            count.set("note", MfgSupport.toText(context.get("note")));
            delegator.create(count);
            if (Math.abs(variance) > 0.0001) {
                MfgStockLedger.recordMovement(delegator, lot, MOVE_COUNT, variance, lot.get("unitPrice"), null,
                        physicalInventoryId, null, counterName, null,
                        "盘点 " + physicalInventoryId + "：" + MfgSupport.formatQty(bookQty) + " → " + MfgSupport.formatQty(countedQty),
                        null, reasonEnumId);
            }
            MfgStockLedger.recalcLotStock(delegator, lot);

            Map<String, Object> result = ServiceUtil.returnSuccess();
            result.put("physicalInventoryId", physicalInventoryId);
            result.put("lotId", lotId);
            result.put("bookQty", Double.valueOf(bookQty));
            result.put("countedQty", Double.valueOf(countedQty));
            result.put("varianceQty", Double.valueOf(variance));
            result.put("reasonEnumId", reasonEnumId);
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
        String lotId = MfgSupport.toText(context.get("lotId"));
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
                map.put("lotKindId", lot == null ? LOT_COMPONENT : MfgStockLedger.lotKindOf(lot));
                map.put("componentCode", lot == null ? null : lot.getString("componentCode"));
                map.put("componentName", lot == null ? null : lot.getString("componentName"));
                map.put("bookQty", count.get("bookQty"));
                map.put("countedQty", count.get("countedQty"));
                map.put("varianceQty", count.get("varianceQty"));
                map.put("reasonEnumId", count.getString("varianceReasonId"));
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
        for (GenericValue lot : MfgStockLedger.componentLots(delegator)) {
            MfgStockLedger.ensureLotLedger(delegator, lot);
            String componentCode = lot.getString("componentCode");
            if (componentCode == null) {
                continue;
            }
            onHand.put(componentCode, Double.valueOf(MfgSupport.numberOrZero(onHand.get(componentCode))
                    + MfgSupport.numberOrZero(lot.get("onHandQty"))));
        }
        for (Map.Entry<String, GenericValue> entry : settings.entrySet()) {
            double minimum = MfgSupport.numberOrZero(entry.getValue().get("minimumStock"));
            if (minimum <= 0.0) {
                continue;
            }
            double current = MfgSupport.numberOrZero(onHand.get(entry.getKey()));
            if (current >= minimum) {
                continue;
            }
            double reorder = MfgSupport.numberOrZero(entry.getValue().get("reorderQty"));
            double suggested = reorder > 0.0 ? reorder : MfgSupport.round2(minimum - current);
            Map<String, Object> alert = new LinkedHashMap<>();
            alert.put("typeId", ALERT_STOCK);
            alert.put("level", current <= 0.0 ? ALERT_ERROR : ALERT_WARN);
            alert.put("refId", entry.getKey());
            alert.put("subject", entry.getValue().getString("componentName"));
            alert.put("dueDate", null);
            alert.put("hoursLeft", Long.valueOf(0L));
            alert.put("detail", "现有 " + MfgSupport.formatQty(current) + " / 安全库存 " + MfgSupport.formatQty(minimum)
                    + "，建议采购 " + MfgSupport.formatQty(suggested)
                    + (entry.getValue().getString("supplierName") == null ? ""
                            : "（" + entry.getValue().getString("supplierName") + "）"));
            alerts.add(alert);
        }
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

    // ------------------------------------------------------------------ helpers

    /** The {@code componentCode} fields are of type {@code name}, so 100 characters are the limit. */
    static final int MAX_COMPONENT_CODE = 100;

    static int parseTrailingNumber(String text) {
        try {
            return Integer.parseInt(text.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    static Map<String, Object> toWorkOrderMap(GenericValue order) {
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

    static Map<String, Object> toSnMap(GenericValue unit) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("sn", unit.getString("sn"));
        map.put("workOrderId", unit.getString("workOrderId"));
        map.put("productCode", unit.getString("productCode"));
        map.put("statusId", unit.getString("statusId"));
        map.put("createdStamp", unit.get("createdStamp"));
        return map;
    }

    static Map<String, Object> toReportMap(GenericValue report) {
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

    // ==================================================================
    // Attachments: the file types and limits every upload shares
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

    // ==================================================================
    // BOM versions
    // ==================================================================

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

    static Map<String, Object> toIssueMap(GenericValue issue) {
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
        String denied = permissionProblem(dctx, context, PERM_ENG);
        if (denied != null) {
            return ServiceUtil.returnError(denied);
        }
        Delegator delegator = dctx.getDelegator();
        String projectCode = MfgSupport.toText(context.get("projectCode"));
        String projectName = MfgSupport.toText(context.get("projectName"));
        String stageId = MfgSupport.toText(context.get("stageId"));
        String statusId = MfgSupport.toText(context.get("statusId"));
        String trackId = MfgSupport.toText(context.get("trackId"));
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
                project.set("productCode", MfgSupport.toText(context.get("productCode")));
            }
            if (trackId != null) {
                project.set("trackId", trackId);
            }
            if (context.containsKey("partCode")) {
                project.set("partCode", MfgSupport.toText(context.get("partCode")));
            }
            if (context.containsKey("ownerName")) {
                project.set("ownerName", MfgSupport.toText(context.get("ownerName")));
            }
            if (context.containsKey("targetCost")) {
                project.set("targetCost", Double.valueOf(MfgSupport.toDouble(context.get("targetCost"), 0.0)));
            }
            if (context.containsKey("targetPrice")) {
                project.set("targetPrice", Double.valueOf(MfgSupport.toDouble(context.get("targetPrice"), 0.0)));
            }
            if (context.containsKey("plannedLaunchDate")) {
                project.set("plannedLaunchDate", MfgSupport.parseDate(context.get("plannedLaunchDate")));
            }
            if (context.containsKey("actualLaunchDate")) {
                project.set("actualLaunchDate", MfgSupport.parseDate(context.get("actualLaunchDate")));
            }
            if (context.containsKey("note")) {
                project.set("note", MfgSupport.toText(context.get("note")));
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
        String stageId = MfgSupport.toText(context.get("stageId"));
        String statusId = MfgSupport.toText(context.get("statusId"));
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
            MfgPaging.putPage(result, "projects",
                    MfgPaging.filterByKeyword(list, MfgSupport.toText(context.get("search")), "projectCode", "projectName",
                            "productCode", "ownerName", "stageId", "statusId"),
                    MfgPaging.pageOf(context), MfgPaging.pageSizeOf(context), MfgSupport.toText(context.get("search")));
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
        String projectCode = MfgSupport.toText(context.get("projectCode"));
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
        String denied = permissionProblem(dctx, context, PERM_ENG);
        if (denied != null) {
            return ServiceUtil.returnError(denied);
        }
        Delegator delegator = dctx.getDelegator();
        String projectCode = MfgSupport.toText(context.get("projectCode"));
        String stageId = MfgSupport.toText(context.get("stageId"));
        String resultId = MfgSupport.toText(context.get("resultId"));
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
            review.set("plannedDate", MfgSupport.parseDate(context.get("plannedDate")));
            review.set("doneDate", MfgSupport.parseDate(context.get("doneDate")));
            review.set("resultId", resultId);
            review.set("reviewer", MfgSupport.toText(context.get("reviewer")));
            review.set("note", MfgSupport.toText(context.get("note")));
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
                || MfgSupport.toText(step.getString("lotId")) != null) {
            return null;
        }
        String partCode = MfgSupport.toText(project.getString("partCode"));
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
        MfgStockLedger.recordMovement(delegator, lot, MOVE_RECEIPT, qty.doubleValue(), null, null, null,
                step.getString("referenceNo"), null,
                Timestamp.valueOf(LocalDate.now().atStartOfDay()), "外壳入库");
        MfgStockLedger.recalcLotStock(delegator, lot);
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
        String denied = permissionProblem(dctx, context, PERM_ENG);
        if (denied != null) {
            return ServiceUtil.returnError(denied);
        }
        Delegator delegator = dctx.getDelegator();
        String projectCode = MfgSupport.toText(context.get("projectCode"));
        String stepId = MfgSupport.toText(context.get("stepId"));
        String statusId = MfgSupport.toText(context.get("statusId"));
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
            step.set("plannedDate", MfgSupport.parseDate(context.get("plannedDate")));
            step.set("doneDate", MfgSupport.parseDate(context.get("doneDate")));
            step.set("ownerName", MfgSupport.toText(context.get("ownerName")));
            step.set("vendorName", MfgSupport.toText(context.get("vendorName")));
            step.set("amount", Double.valueOf(MfgSupport.toDouble(context.get("amount"), 0.0)));
            step.set("qty", Double.valueOf(MfgSupport.toDouble(context.get("qty"), 0.0)));
            step.set("referenceNo", MfgSupport.toText(context.get("referenceNo")));
            step.set("note", MfgSupport.toText(context.get("note")));
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
        String denied = permissionProblem(dctx, context, PERM_ENG);
        if (denied != null) {
            return ServiceUtil.returnError(denied);
        }
        Delegator delegator = dctx.getDelegator();
        String projectCode = MfgSupport.toText(context.get("projectCode"));
        String stepId = MfgSupport.toText(context.get("stepId"));
        String statusId = MfgSupport.toText(context.get("statusId"));
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
        String denied = permissionProblem(dctx, context, PERM_ENG);
        if (denied != null) {
            return ServiceUtil.returnError(denied);
        }
        Delegator delegator = dctx.getDelegator();
        String issueId = MfgSupport.toText(context.get("issueId"));
        String projectCode = MfgSupport.toText(context.get("projectCode"));
        String stepId = MfgSupport.toText(context.get("stepId"));
        String severityId = MfgSupport.toText(context.get("severityId"));
        String statusId = MfgSupport.toText(context.get("statusId"));
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
                issue.set("foundDate", MfgSupport.parseDate(context.get("foundDate")));
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
                    issue.set("foundDate", MfgSupport.parseDate(context.get("foundDate")));
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
                issue.set("title", MfgSupport.toText(context.get("title")));
            }
            if (context.containsKey("detail")) {
                issue.set("detail", MfgSupport.toText(context.get("detail")));
            }
            if (severityId != null) {
                issue.set("severityId", severityId);
            }
            if (context.containsKey("ownerName")) {
                issue.set("ownerName", MfgSupport.toText(context.get("ownerName")));
            }
            if (context.containsKey("dueDate")) {
                issue.set("dueDate", MfgSupport.parseDate(context.get("dueDate")));
            }
            if (context.containsKey("resolution")) {
                issue.set("resolution", MfgSupport.toText(context.get("resolution")));
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
        String denied = permissionProblem(dctx, context, PERM_ENG);
        if (denied != null) {
            return ServiceUtil.returnError(denied);
        }
        Delegator delegator = dctx.getDelegator();
        String issueId = MfgSupport.toText(context.get("issueId"));
        String statusId = MfgSupport.toText(context.get("statusId"));
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
                String resolution = MfgSupport.toText(context.get("resolution"));
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
        String projectCode = MfgSupport.toText(context.get("projectCode"));
        String statusId = MfgSupport.toText(context.get("statusId"));
        boolean openOnly = "Y".equalsIgnoreCase(MfgSupport.toText(context.get("openOnly")));
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
     * Raises or updates an engineering change order.
     *
     * @param dctx the dispatch context
     * @param context service context, see {@code servicedef/services.xml#mfgDevEcoSave}
     * @return the service result containing {@code ecoId}
     */
    public static Map<String, Object> devEcoSave(DispatchContext dctx, Map<String, ? extends Object> context) {
        String denied = permissionProblem(dctx, context, PERM_ENG);
        if (denied != null) {
            return ServiceUtil.returnError(denied);
        }
        Delegator delegator = dctx.getDelegator();
        String ecoId = MfgSupport.toText(context.get("ecoId"));
        String productCode = MfgSupport.toText(context.get("productCode"));
        String ecoTypeId = MfgSupport.toText(context.get("ecoTypeId"));
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
            String toVersionId = MfgSupport.toText(context.get("toBomVersionId"));
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
                eco.set("projectCode", MfgSupport.toText(context.get("projectCode")));
            }
            if (context.containsKey("reason")) {
                eco.set("reason", MfgSupport.toText(context.get("reason")));
            }
            if (context.containsKey("changeSummary")) {
                eco.set("changeSummary", MfgSupport.toText(context.get("changeSummary")));
            }
            if (context.containsKey("requestedBy")) {
                eco.set("requestedBy", MfgSupport.toText(context.get("requestedBy")));
            }
            if (context.containsKey("approvedBy")) {
                eco.set("approvedBy", MfgSupport.toText(context.get("approvedBy")));
            }
            if (context.containsKey("effectiveDate")) {
                eco.set("effectiveDate", MfgSupport.parseDate(context.get("effectiveDate")));
            }
            if (context.containsKey("note")) {
                eco.set("note", MfgSupport.toText(context.get("note")));
            }
            if (toVersionId != null) {
                eco.set("toBomVersionId", toVersionId);
            }
            String fromVersionId = MfgSupport.toText(context.get("fromBomVersionId"));
            if (fromVersionId == null) {
                fromVersionId = MfgSupport.toText(eco.getString("fromBomVersionId"));
            }
            if (fromVersionId == null) {
                fromVersionId = MfgProductServices.currentBomVersionId(delegator, productCode);
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
        String statusId = MfgSupport.toText(context.get("statusId"));
        String productCode = MfgSupport.toText(context.get("productCode"));
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
            MfgPaging.putPage(result, "ecos",
                    MfgPaging.filterByKeyword(list, MfgSupport.toText(context.get("search")), "ecoId", "productCode", "projectCode",
                            "ecoTypeId", "reason", "statusId", "requestedBy"),
                    MfgPaging.pageOf(context), MfgPaging.pageSizeOf(context), MfgSupport.toText(context.get("search")));
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
        String ecoId = MfgSupport.toText(context.get("ecoId"));
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
            String fromId = MfgSupport.toText(eco.getString("fromBomVersionId"));
            String toId = MfgSupport.toText(eco.getString("toBomVersionId"));
            if (fromId != null) {
                GenericValue from = EntityQuery.use(delegator).from("MfgBomVersion")
                        .where("bomVersionId", fromId).queryOne();
                if (from != null) {
                    result.put("fromVersion", MfgProductServices.toBomVersionMap(delegator, from));
                }
            }
            if (toId != null) {
                GenericValue to = EntityQuery.use(delegator).from("MfgBomVersion")
                        .where("bomVersionId", toId).queryOne();
                if (to != null) {
                    result.put("toVersion", MfgProductServices.toBomVersionMap(delegator, to));
                }
            }
            List<Map<String, Object>> versions = new ArrayList<>();
            for (GenericValue version : MfgProductServices.bomVersionsOf(delegator, productCode)) {
                versions.add(MfgProductServices.toBomVersionMap(delegator, version));
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
        String denied = permissionProblem(dctx, context, PERM_ENG);
        if (denied != null) {
            return ServiceUtil.returnError(denied);
        }
        Delegator delegator = dctx.getDelegator();
        String ecoId = MfgSupport.toText(context.get("ecoId"));
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
            eco.set("approvedBy", MfgSupport.toText(context.get("approvedBy")));
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
        String denied = permissionProblem(dctx, context, PERM_ENG);
        if (denied != null) {
            return ServiceUtil.returnError(denied);
        }
        Delegator delegator = dctx.getDelegator();
        String ecoId = MfgSupport.toText(context.get("ecoId"));
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
            String toVersionId = MfgSupport.toText(eco.getString("toBomVersionId"));
            if (toVersionId != null) {
                GenericValue version = EntityQuery.use(delegator).from("MfgBomVersion")
                        .where("bomVersionId", toVersionId).queryOne();
                if (version == null) {
                    return ServiceUtil.returnError("Unknown BOM version [" + toVersionId + "]");
                }
                String productCode = eco.getString("productCode");
                GenericValue product = EntityQuery.use(delegator).from("MfgProduct")
                        .where("productCode", productCode).queryOne();
                for (GenericValue other : MfgProductServices.bomVersionsOf(delegator, productCode)) {
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
        String denied = permissionProblem(dctx, context, PERM_ENG);
        if (denied != null) {
            return ServiceUtil.returnError(denied);
        }
        Delegator delegator = dctx.getDelegator();
        String ecoId = MfgSupport.toText(context.get("ecoId"));
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