/*
 * Copyright (C) 2010-2026 Tianjin Langhua Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.langhua.ofbiz.mfg;

import java.time.format.DateTimeFormatter;

/**
 * The vocabulary every service of the component speaks: what a work order, a lot, a claim, an ECO
 * or an alert can be, which inventory movements exist, the permission names, the alert kinds and
 * the defaults of the cost models.
 *
 * <p>They live in one place because the same string has to mean the same thing on every page and
 * in every export. A status is never spelled out at a call site, and a constant is only added here
 * when at least two areas need it - what one area alone needs stays with that area (the price rates
 * of the software model, for example, live in {@link MfgSoftwareServices}).
 *
 * <p>This is the last piece of what used to be {@code MfgServices}; the methods are gone, the words
 * are here (Step 172).
 */
public final class MfgConstants {

    private MfgConstants() {
    }

    public static final String MODULE = MfgConstants.class.getName();

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

    /**
     * What one import of a design BOM (Fritzing) means for one part, compared with the version before
     * it. A part that is no longer used keeps its row with {@link #BOM_DROPPED} and the quantity that
     * was used up as development material, so the history of a sketch stays readable.
     */
    public static final String BOM_NEW = "MFG_BOM_NEW";

    public static final String BOM_SAME = "MFG_BOM_SAME";

    public static final String BOM_CHANGED = "MFG_BOM_CHANGED";

    public static final String BOM_DROPPED = "MFG_BOM_DROPPED";

    /** The change states a part row can have, in the order the page explains them. */
    public static final String[] BOM_CHANGES = {BOM_NEW, BOM_CHANGED, BOM_SAME, BOM_DROPPED};

    /**
     * Where a row of a design BOM came from. {@code null} means the sketch printed it; this value
     * means somebody typed it in because the board needs it and the sketch does not show it (an
     * adapter board that lets a part sit in the breadboard). Rows of this kind are carried into the
     * next import, otherwise every new export would drop them.
     */
    public static final String BOM_SOURCE_MANUAL = "MFG_BOM_MANUAL";

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

    /** Freight stays a period cost: the default of a receipt, the price of a part stays the price paid for it. */
    public static final String ALLOC_NONE = "MFG_ALLOC_NONE";

    /** Freight spread over the lines in proportion to the goods value, kept for a later release. */
    public static final String ALLOC_BY_AMOUNT = "MFG_ALLOC_BY_AMOUNT";

    /** Freight spread over the lines in proportion to the quantity, kept for a later release. */
    public static final String ALLOC_BY_QTY = "MFG_ALLOC_BY_QTY";

    /** Ways a receipt may treat its freight; only {@link #ALLOC_NONE} is open for now. */
    public static final String[] ALLOCATIONS = {ALLOC_NONE, ALLOC_BY_AMOUNT, ALLOC_BY_QTY};

    /**
     * Days between the day an order was placed and the day its parcel is taken to have arrived.
     *
     * <p>The screenshots and the shop exports only print the day of the order; filling in today
     * would book last year's order into this month's stock (and into this month's freight report),
     * so the arrival day is the order day plus this many days - never later than today.
     */
    public static final int ARRIVAL_DAYS = 3;

    /** How many unassigned lots the receipt page offers in its picker. */
    static final int MAX_FREE_LOTS = 200;

    /** Resource id the component writes its settings under in {@code SystemProperty}. */
    public static final String SETTINGS_RESOURCE = "mfg";

    /** The {@code componentCode} fields are of type {@code name}, so 100 characters are the limit. */
    static final int MAX_COMPONENT_CODE = 100;

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

    /**
     * Files that belong to a part number, not to one parcel of it: the datasheet, the drawing, the
     * supplier's certificate. The owner id is the part number as the stock books it, so the same
     * sheet shows for every batch of that part.
     */
    public static final String ATT_PART = "MFG_ATT_PART";

    /**
     * A shop page somebody photographed for the order recognition. The screenshot is kept as it was,
     * under the scan id the page hands back, so the receipts made from it can point at the picture the
     * numbers came from - and the product pictures can be cut out of it later.
     */
    public static final String ATT_ORDER_SCAN = "MFG_ATT_ORDER_SCAN";

    /** Owner types an attachment can hang on. */
    public static final String[] ATTACHMENT_OWNER_TYPES = {
        ATT_PRODUCT, ATT_DEV_PROJECT, ATT_WORK_ORDER, ATT_DELIVERY, ATT_CLAIM, ATT_ORDER_SCAN, ATT_PART};
}
