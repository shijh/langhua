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
    static final DateTimeFormatter MONTH_DAY = DateTimeFormatter.ofPattern("MM-dd");
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

    // ------------------------------------------------------------ cost

    // ------------------------------------------------------------ material and outsourcing

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

    static String nextLotId(Delegator delegator, LocalDate today) throws GenericEntityException {
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

    static List<Map<String, Object>> vendorMaps(Delegator delegator) throws GenericEntityException {
        List<GenericValue> vendors = EntityQuery.use(delegator).from("MfgVendor")
                .orderBy("vendorName").queryList();
        List<Map<String, Object>> list = new ArrayList<>(vendors.size());
        for (GenericValue vendor : vendors) {
            list.add(MfgWorkOrderServices.toVendorMap(vendor));
        }
        return list;
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

    // ==================== inventory: append only movements, totals summed from them ====================

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

    /** How many unassigned lots the receipt page offers in its picker. */
    static final int MAX_FREE_LOTS = 200;

    // ------------------------------------------------------------ refunded / closed shop orders

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

    static boolean isKnownEcoStatus(String statusId) {
        return ECO_DRAFT.equals(statusId) || ECO_APPROVED.equals(statusId) || ECO_APPLIED.equals(statusId)
                || ECO_CANCELLED.equals(statusId);
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

}