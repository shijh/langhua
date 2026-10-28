package org.langhua.ofbiz.mfg;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.apache.ofbiz.entity.Delegator;
import org.apache.ofbiz.entity.GenericEntityException;
import org.apache.ofbiz.entity.GenericValue;
import org.apache.ofbiz.entity.util.EntityQuery;

/**
 * The inventory ledger: how a lot's stock on hand, its reservations and its unit cost are kept.
 *
 * <p>Every change of stock is a row of {@code MfgInventoryDetail} and is never updated; the totals on
 * the lot ({@code onHandQty}, {@code reservedQty}, {@code availableQty}) are always written back from
 * the sum of those rows, never added to by hand (see the inventory section of the component README).
 * The same rules are needed by the lot pages, the issue, the delivery, the sales orders and the
 * transfers, so they live in one class instead of in the middle of {@code MfgServices} (Step 165).
 *
 * <p>The movement and reason constants stay in {@code MfgServices}: they are the vocabulary every
 * service of the component speaks, and moving them would have touched a hundred call sites for no
 * gain. Everything here works on a {@link Delegator} and an entity value; nothing reaches for a
 * dispatch context.
 */
public final class MfgStockLedger {

    private MfgStockLedger() {
    }

    /**
     * Money per unit of a lot: the amount really paid divided by the quantity received when that
     * amount is known, otherwise the unit price typed in. Cheap parts (100 resistors for 2.91) need
     * more than the two decimals of the price column, so the amount is the precise source.
     *
     * @param lot the material lot
     * @return the cost of one unit, {@code 0} when neither is known
     */
    static double lotUnitCost(GenericValue lot) {
        return unitCostOf(MfgSupport.numberOrZero(lot.get("receivedQty")),
                MfgSupport.numberOrZero(lot.get("receivedAmount")),
                MfgSupport.numberOrZero(lot.get("unitPrice")));
    }

    /**
     * The arithmetic behind {@link #lotUnitCost}.
     *
     * @param qty quantity received
     * @param amount amount really paid, {@code 0} when unknown
     * @param unitPrice the unit price typed in
     * @return the amount per unit when the amount is known, otherwise the unit price
     */
    static double unitCostOf(double qty, double amount, double unitPrice) {
        if (qty > 0.0 && amount > 0.0) {
            return amount / qty;
        }
        return unitPrice;
    }

    /**
     * Tells whether a movement type is one of the six the ledger knows.
     *
     * @param movementTypeId the type to test
     * @return {@code true} when it is known
     */
    static boolean isKnownMovement(String movementTypeId) {
        for (String known : MfgConstants.MOVE_TYPES) {
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
        if (MfgConstants.MOVE_RESERVE.equals(movementTypeId)
                || MfgConstants.MOVE_RELEASE.equals(movementTypeId)) {
            return 0.0;
        }
        for (String in : MfgConstants.MOVE_IN_TYPES) {
            if (in.equals(movementTypeId)) {
                return Math.abs(qty);
            }
        }
        for (String out : MfgConstants.MOVE_OUT_TYPES) {
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
        if (MfgConstants.IQC_FAIL.equals(inspectStatusId)) {
            return 0.0;
        }
        double free = onHand - reserved;
        return free <= 0.0 ? 0.0 : MfgSupport.round2(free);
    }

    /**
     * Chinese name of one of OFBiz's inventory reasons, so the pages and the CSV exports agree.
     *
     * @param reasonEnumId the reason, may be {@code null}
     * @return the name, the raw id when it is unknown, an empty string when there is none
     */
    static String reasonLabel(String reasonEnumId) {
        if (reasonEnumId == null) {
            return "";
        }
        switch (reasonEnumId) {
            case MfgConstants.REASON_LOST:
                return "丢失";
            case MfgConstants.REASON_STOLEN:
                return "被盗";
            case MfgConstants.REASON_FOUND:
                return "找到（盘盈）";
            case MfgConstants.REASON_DAMAGED:
                return "损坏";
            case MfgConstants.REASON_SAMPLE:
                return "样品 / 赠送";
            case MfgConstants.REASON_INTEGR:
                return "生产领用（组装）";
            case MfgConstants.REASON_MISSHIP_ORDERED:
                return "供方少发（应到）";
            case MfgConstants.REASON_MISSHIP_SHIPPED:
                return "我方多发（已发）";
            default:
                return reasonEnumId;
        }
    }

    /**
     * The movement type a reason implies when the person only picks a reason: material that is lost,
     * stolen, broken, given away or sent out by mistake leaves the stock as 报废 (or 发料 for material
     * used up in assembly), what is found or still owed by the supplier comes back in as 补入库.
     *
     * @param reasonEnumId the reason, may be {@code null}
     * @return the movement type, {@code null} when the reason is unknown
     */
    static String movementTypeOfReason(String reasonEnumId) {
        if (reasonEnumId == null) {
            return null;
        }
        switch (reasonEnumId) {
            case MfgConstants.REASON_LOST:
            case MfgConstants.REASON_STOLEN:
            case MfgConstants.REASON_DAMAGED:
            case MfgConstants.REASON_SAMPLE:
            case MfgConstants.REASON_MISSHIP_SHIPPED:
                return MfgConstants.MOVE_SCRAP;
            case MfgConstants.REASON_INTEGR:
                return MfgConstants.MOVE_ISSUE;
            case MfgConstants.REASON_FOUND:
            case MfgConstants.REASON_MISSHIP_ORDERED:
                return MfgConstants.MOVE_RECEIPT;
            default:
                return null;
        }
    }

    /**
     * Chinese name of one movement type, used in the messages the person sees.
     *
     * @param movementTypeId the type, may be {@code null}
     * @return the name, the raw id when it is unknown
     */
    static String movementLabel(String movementTypeId) {
        if (movementTypeId == null) {
            return "";
        }
        switch (movementTypeId) {
            case MfgConstants.MOVE_RECEIPT:
                return "到货入库";
            case MfgConstants.MOVE_ISSUE:
                return "发料";
            case MfgConstants.MOVE_RETURN:
                return "退料";
            case MfgConstants.MOVE_SCRAP:
                return "报废";
            case MfgConstants.MOVE_COUNT:
                return "盘盈亏";
            case MfgConstants.MOVE_ADJUST:
                return "调整";
            case MfgConstants.MOVE_TRANSFER:
                return "移库";
            case MfgConstants.MOVE_RESERVE:
                return "预留";
            case MfgConstants.MOVE_RELEASE:
                return "释放预留";
            default:
                return movementTypeId;
        }
    }

    /**
     * Checks that the reason is one of OFBiz's and that it does not fight with the movement type: a
     * reason that means "material left" may not be booked as 补入库, or the stock would grow while the
     * note says it was lost.
     *
     * @param movementTypeId the movement type, may be {@code null} to take it from the reason
     * @param reasonEnumId the reason, may be {@code null}
     * @param qty the quantity as entered (its sign settles the direction of an 调整)
     * @return {@code null} when they fit together, else the message for the person
     */
    static String movementReasonProblem(String movementTypeId, String reasonEnumId, double qty) {
        if (reasonEnumId != null && movementTypeOfReason(reasonEnumId) == null) {
            return "未知的库存原因 [" + reasonEnumId + "]，可选：" + String.join(" / ", MfgConstants.MOVE_REASONS);
        }
        if (movementTypeId == null || reasonEnumId == null) {
            return null;
        }
        double byType = movementDiff(movementTypeId, qty);
        double byReason = movementDiff(movementTypeOfReason(reasonEnumId), 1.0);
        if (byType != 0.0 && byReason != 0.0 && (byType > 0.0) != (byReason > 0.0)) {
            // a count or an adjustment carries its own sign, so the message talks about the sign of
            // the quantity instead of the bucket it lands in
            boolean counted = MfgConstants.MOVE_COUNT.equals(movementTypeId);
            String what = counted
                    ? (byType > 0.0 ? "可这次盘点是盘盈（多了）" : "可这次盘点是盘亏（少了）")
                    : "可类型「" + movementLabel(movementTypeId) + "」是"
                            + (byType > 0.0 ? "入库" : "出库");
            String advice = counted
                    ? "盘盈一般填「找到」或「供方少发」，盘亏一般填「丢失」「被盗」「损坏」"
                    : "选了原因后类型会自动跟着改";
            return "原因「" + reasonLabel(reasonEnumId) + "」是" + (byReason > 0.0 ? "入库" : "出库")
                    + "的，" + what + "，对不上，请核对（" + advice + "）";
        }
        return null;
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
        return lot != null && MfgConstants.LOT_PRODUCT.equals(lot.getString("lotKindId"));
    }

    /**
     * The kind of a lot, with the empty value of the rows written before the split read as a
     * purchased component.
     *
     * @param lot the lot, may be {@code null}
     * @return {@link MfgConstants#LOT_COMPONENT} or {@link MfgConstants#LOT_PRODUCT}
     */
    static String lotKindOf(GenericValue lot) {
        return isProductLot(lot) ? MfgConstants.LOT_PRODUCT : MfgConstants.LOT_COMPONENT;
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
        return workOrderId == null ? null : MfgConstants.PRODUCT_LOT_PREFIX + workOrderId;
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
        return qtyOk <= 0.0 ? 0.0 : MfgSupport.round2(actualTotal / qtyOk);
    }

    /**
     * The purchased component lots, so the part oriented views never count finished goods.
     *
     * @param delegator the delegator
     * @return the component lots
     * @throws GenericEntityException when they cannot be read
     */
    static List<GenericValue> componentLots(Delegator delegator) throws GenericEntityException {
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
    static List<GenericValue> movementsOf(Delegator delegator, String lotId)
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
        return recordMovement(delegator, lot, movementTypeId, diff, unitCost, workOrderId,
                physicalInventoryId, referenceNo, operatorName, effectiveDate, note, null);
    }

    /**
     * Appends one movement and remembers where it happened.
     *
     * @return the row that was written
     * @throws GenericEntityException when it cannot be stored
     */
    static GenericValue recordMovement(Delegator delegator, GenericValue lot, String movementTypeId,
            double diff, Object unitCost, String workOrderId, String physicalInventoryId,
            String referenceNo, String operatorName, java.sql.Timestamp effectiveDate, String note,
            String locationId) throws GenericEntityException {
        return recordMovement(delegator, lot, movementTypeId, diff, unitCost, workOrderId,
                physicalInventoryId, referenceNo, operatorName, effectiveDate, note, locationId, null);
    }

    /**
     * Appends one movement, remembering where it happened and why.
     *
     * @return the row that was written
     * @throws GenericEntityException when it cannot be stored
     */
    static GenericValue recordMovement(Delegator delegator, GenericValue lot, String movementTypeId,
            double diff, Object unitCost, String workOrderId, String physicalInventoryId,
            String referenceNo, String operatorName, java.sql.Timestamp effectiveDate, String note,
            String locationId, String reasonEnumId) throws GenericEntityException {
        long seq = nextMovementSeq(delegator, lot.getString("lotId"));
        GenericValue movement = delegator.makeValue("MfgInventoryDetail");
        movement.set("lotId", lot.getString("lotId"));
        movement.set("seqId", Long.valueOf(seq));
        movement.set("movementTypeId", movementTypeId);
        movement.set("quantityDiff", Double.valueOf(MfgSupport.round2(diff)));
        if (unitCost != null) {
            movement.set("unitCost", unitCost);
        }
        movement.set("workOrderId", workOrderId);
        movement.set("physicalInventoryId", physicalInventoryId);
        movement.set("referenceNo", referenceNo);
        movement.set("reasonEnumId", reasonEnumId);
        movement.set("operatorName", operatorName);
        movement.set("locationId", locationId);
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
            onHand += MfgSupport.numberOrZero(movement.get("quantityDiff"));
            reserved += MfgSupport.numberOrZero(movement.get("reservedDiff"));
        }
        lot.set("onHandQty", Double.valueOf(MfgSupport.round2(onHand)));
        lot.set("reservedQty", Double.valueOf(reserved <= 0.0 ? 0.0 : MfgSupport.round2(reserved)));
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
        double receivedQty = MfgSupport.numberOrZero(lot.get("receivedQty"));
        if (receivedQty > 0.0) {
            java.sql.Date receivedDate = lot.getDate("receivedDate");
            recordMovement(delegator, lot, MfgConstants.MOVE_RECEIPT, receivedQty,
                    lot.get("unitPrice"), null, null,
                    lot.getString("supplierLot"), null,
                    receivedDate == null ? null
                            : Timestamp.valueOf(receivedDate.toLocalDate().atStartOfDay()),
                    "首次建账：到货");
        }
        List<GenericValue> issues = EntityQuery.use(delegator).from("MfgMaterialIssue")
                .where("lotId", lotId).orderBy("issuedDate").queryList();
        for (GenericValue issue : issues) {
            java.sql.Date issuedDate = issue.getDate("issuedDate");
            recordMovement(delegator, lot, MfgConstants.MOVE_ISSUE, -MfgSupport.numberOrZero(issue.get("qty")),
                    lot.get("unitPrice"), issue.getString("workOrderId"), null, null, null,
                    issuedDate == null ? null
                            : Timestamp.valueOf(issuedDate.toLocalDate().atStartOfDay()),
                    "首次建账：历史发料");
        }
        recalcLotStock(delegator, lot);
    }

    static long nextMovementSeq(Delegator delegator, String lotId) throws GenericEntityException {
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
            reserved += MfgSupport.numberOrZero(movement.get("reservedDiff"));
        }
        return reserved <= 0.0 ? 0.0 : reserved;
    }

    /**
     * Appends a reservation movement. It does not change the quantity on hand, only what is
     * earmarked for a work order, which is exactly how OFBiz keeps its available to promise.
     *
     * @param delegator the delegator
     * @param lot the lot
     * @param movementTypeId {@link MfgConstants#MOVE_RESERVE} or {@link MfgConstants#MOVE_RELEASE}
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
        movement.set("reservedDiff", Double.valueOf(MfgSupport.round2(diff)));
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
        recordReservation(delegator, lot, MfgConstants.MOVE_RELEASE, -release, workOrderId, operatorName,
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
    static Map<String, Double> reservedByComponent(Delegator delegator, String workOrderId)
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
            double reserved = MfgSupport.numberOrZero(movement.get("reservedDiff"));
            if (reserved == 0.0) {
                continue;
            }
            GenericValue lot = lots.get(movement.getString("lotId"));
            if (lot == null) {
                continue;
            }
            String componentCode = lot.getString("componentCode");
            byComponent.put(componentCode,
                    Double.valueOf(MfgSupport.numberOrZero(byComponent.get(componentCode))
                    + reserved));
        }
        return byComponent;
    }

    /**
     * The purchased lots of one component, oldest first, the order material is taken in.
     *
     * @param delegator the delegator
     * @param componentCode the part number
     * @return the lots, an empty list when nothing of that part was ever received
     * @throws GenericEntityException when they cannot be read
     */
    static List<GenericValue> lotsOfComponent(Delegator delegator, String componentCode)
            throws GenericEntityException {
        List<GenericValue> lots = new ArrayList<>();
        if (componentCode == null) {
            return lots;
        }
        String wanted = componentCode.trim();
        for (GenericValue lot : componentLots(delegator)) {
            String lotCode = lot.getString("componentCode");
            // case does not matter: the sketch writes C-100NF-16V-0603, the shop may book 100nf
            if (lotCode == null || !wanted.equalsIgnoreCase(lotCode.trim())) {
                continue;
            }
            if (lot.get("onHandQty") == null) {
                ensureLotLedger(delegator, lot);
            }
            lots.add(lot);
        }
        lots.sort((left, right) -> {
            java.sql.Date leftDate = left.getDate("receivedDate");
            java.sql.Date rightDate = right.getDate("receivedDate");
            if (leftDate == null && rightDate != null) {
                return 1;
            }
            if (leftDate != null && rightDate == null) {
                return -1;
            }
            if (leftDate != null && rightDate != null && leftDate.compareTo(rightDate) != 0) {
                return leftDate.compareTo(rightDate);
            }
            return String.valueOf(left.getString("lotId")).compareTo(String.valueOf(right.getString("lotId")));
        });
        return lots;
    }

    /**
     * What one lot can still take on top of the lock a development project already holds on it: the
     * quantity on hand, minus everything reserved, plus its own reservation, which is not in its way.
     *
     * @param delegator the delegator
     * @param lot the material lot
     * @param projectCode the development project
     * @return the quantity that may still be locked, never negative
     * @throws GenericEntityException when the ledger cannot be read
     */
    static double spareOf(Delegator delegator, GenericValue lot, String projectCode)
            throws GenericEntityException {
        if (MfgConstants.IQC_FAIL.equals(lot.getString("inspectStatusId"))) {
            return 0.0;
        }
        double onHand = MfgSupport.numberOrZero(lot.get("onHandQty"));
        double reserved = MfgSupport.numberOrZero(lot.get("reservedQty"));
        double mine = projectReservationOf(delegator, lot.getString("lotId"), projectCode);
        double spare = onHand - (reserved - mine);
        return spare <= 0.0 ? 0.0 : MfgSupport.round2(spare);
    }

    /**
     * What one lot has locked for one development project right now.
     *
     * @param delegator the delegator
     * @param lotId the lot
     * @param projectCode the development project
     * @return the locked quantity, {@code 0} when nothing is locked
     * @throws GenericEntityException when the movements cannot be read
     */
    static double projectReservationOf(Delegator delegator, String lotId, String projectCode)
            throws GenericEntityException {
        if (projectCode == null) {
            return 0.0;
        }
        double locked = 0.0;
        for (GenericValue movement : movementsOf(delegator, lotId)) {
            if (!projectCode.equals(movement.getString("devProjectCode"))) {
                continue;
            }
            locked += MfgSupport.numberOrZero(movement.get("reservedDiff"));
        }
        return locked <= 0.0 ? 0.0 : locked;
    }

    /**
     * Appends a reservation row that belongs to a development project instead of a work order. Like
     * every reservation it changes nothing on hand, only what is earmarked, so a part locked by a
     * running prototype cannot be reserved by a work order.
     *
     * @param delegator the delegator
     * @param lot the lot
     * @param movementTypeId {@link MfgConstants#MOVE_RESERVE} or {@link MfgConstants#MOVE_RELEASE}
     * @param diff the signed change of the locked quantity
     * @param projectCode the development project
     * @param operatorName who did it
     * @param note a remark
     * @throws GenericEntityException when it cannot be stored
     */
    static void recordProjectReservation(Delegator delegator, GenericValue lot, String movementTypeId,
            double diff, String projectCode, String operatorName, String note)
            throws GenericEntityException {
        GenericValue movement = delegator.makeValue("MfgInventoryDetail");
        movement.set("lotId", lot.getString("lotId"));
        movement.set("seqId", Long.valueOf(nextMovementSeq(delegator, lot.getString("lotId"))));
        movement.set("movementTypeId", movementTypeId);
        movement.set("quantityDiff", Double.valueOf(0.0));
        movement.set("reservedDiff", Double.valueOf(MfgSupport.round2(diff)));
        movement.set("devProjectCode", projectCode);
        movement.set("operatorName", operatorName);
        movement.set("effectiveDate", new Timestamp(System.currentTimeMillis()));
        movement.set("note", note);
        delegator.create(movement);
    }

    /**
     * Locked quantity per component of one development project.
     *
     * @param delegator the delegator
     * @param projectCode the development project
     * @return the locked quantity by component code, only the components that have some
     * @throws GenericEntityException when the ledger cannot be read
     */
    static Map<String, Double> lockedByProjectComponent(Delegator delegator, String projectCode)
            throws GenericEntityException {
        Map<String, Double> byComponent = new LinkedHashMap<>();
        if (projectCode == null) {
            return byComponent;
        }
        Map<String, GenericValue> lots = new LinkedHashMap<>();
        for (GenericValue lot : componentLots(delegator)) {
            lots.put(lot.getString("lotId"), lot);
        }
        for (GenericValue movement : EntityQuery.use(delegator).from("MfgInventoryDetail")
                .where("devProjectCode", projectCode).queryList()) {
            double locked = MfgSupport.numberOrZero(movement.get("reservedDiff"));
            if (locked == 0.0) {
                continue;
            }
            GenericValue lot = lots.get(movement.getString("lotId"));
            if (lot == null) {
                continue;
            }
            // upper case, because the pages (and the Fritzing sketch) compare part numbers that way
            String componentCode = lot.getString("componentCode");
            if (componentCode == null) {
                continue;
            }
            componentCode = componentCode.trim().toUpperCase(Locale.ROOT);
            byComponent.put(componentCode, Double.valueOf(
                    MfgSupport.numberOrZero(byComponent.get(componentCode)) + locked));
        }
        return byComponent;
    }

    /**
     * Locks up to {@code wanted} units of one component for a development project, oldest lot first.
     * Whatever is not on the shelves stays unlocked and shows up as the shortage.
     *
     * @param delegator the delegator
     * @param projectCode the development project
     * @param componentCode the part number
     * @param wanted how many units the project needs
     * @param operatorName who did it
     * @param note a remark
     * @return how many units this call locked
     * @throws GenericEntityException when the ledger cannot be written
     */
    static double reserveForProject(Delegator delegator, String projectCode, String componentCode,
            double wanted, String operatorName, String note) throws GenericEntityException {
        if (projectCode == null || componentCode == null || wanted <= 0.0001) {
            return 0.0;
        }
        double locked = 0.0;
        for (GenericValue lot : lotsOfComponent(delegator, componentCode)) {
            if (locked >= wanted - 0.0001) {
                break;
            }
            double add = Math.min(wanted - locked, spareOf(delegator, lot, projectCode));
            if (add <= 0.0001) {
                continue;
            }
            recordProjectReservation(delegator, lot, MfgConstants.MOVE_RESERVE, add, projectCode,
                    operatorName, note);
            recalcLotStock(delegator, lot);
            locked += add;
        }
        return MfgSupport.round2(locked);
    }

    /**
     * Gives back what a development project has locked on one component, at most {@code qty}.
     *
     * @param delegator the delegator
     * @param projectCode the development project
     * @param componentCode the part number
     * @param qty how much to give back, the whole lock when it is larger than the lock
     * @param operatorName who did it
     * @param note a remark
     * @return how much was given back
     * @throws GenericEntityException when the ledger cannot be written
     */
    static double releaseProjectReservation(Delegator delegator, String projectCode, String componentCode,
            double qty, String operatorName, String note) throws GenericEntityException {
        if (projectCode == null || componentCode == null || qty <= 0.0001) {
            return 0.0;
        }
        double released = 0.0;
        for (GenericValue lot : lotsOfComponent(delegator, componentCode)) {
            if (released >= qty - 0.0001) {
                break;
            }
            double mine = projectReservationOf(delegator, lot.getString("lotId"), projectCode);
            double give = Math.min(mine, qty - released);
            if (give <= 0.0001) {
                continue;
            }
            recordProjectReservation(delegator, lot, MfgConstants.MOVE_RELEASE, -give, projectCode,
                    operatorName, note);
            recalcLotStock(delegator, lot);
            released += give;
        }
        return MfgSupport.round2(released);
    }

    /**
     * Turns what a development project holds of one component into development material: the lock is
     * given back and the same quantity leaves the shelf as a sample the prototype used up. The parts
     * therefore do not become available again - a version that dropped a part must not put it back
     * into stock, it was already soldered, stripped or lost while breadboarding.
     *
     * @param delegator the delegator
     * @param projectCode the development project
     * @param componentCode the part number
     * @param operatorName who did it
     * @param note a remark, e.g. which version dropped the part
     * @return how many units were booked as used up
     * @throws GenericEntityException when the ledger cannot be written
     */
    static double consumeForProject(Delegator delegator, String projectCode, String componentCode,
            String operatorName, String note) throws GenericEntityException {
        if (projectCode == null || componentCode == null) {
            return 0.0;
        }
        double consumed = 0.0;
        for (GenericValue lot : lotsOfComponent(delegator, componentCode)) {
            double mine = projectReservationOf(delegator, lot.getString("lotId"), projectCode);
            if (mine <= 0.0001) {
                continue;
            }
            double onHand = MfgSupport.numberOrZero(lot.get("onHandQty"));
            double usedUp = Math.min(mine, onHand <= 0.0 ? 0.0 : onHand);
            recordProjectReservation(delegator, lot, MfgConstants.MOVE_RELEASE, -mine, projectCode,
                    operatorName, note);
            if (usedUp > 0.0001) {
                recordMovement(delegator, lot, MfgConstants.MOVE_SCRAP, -usedUp, lotUnitCost(lot),
                        null, null, projectCode, operatorName, null, note, null,
                        MfgConstants.REASON_SAMPLE);
            }
            recalcLotStock(delegator, lot);
            consumed += usedUp;
        }
        return MfgSupport.round2(consumed);
    }

    /**
     * How much of one lot sits in one location; a {@code null} location counts the movements that
     * were booked without one, which is where the lots created before locations ended up.
     *
     * @param delegator the delegator
     * @param lotId the lot, {@code null} to sum every lot of the location
     * @param locationId the location, {@code null} for the unassigned bucket
     * @return the quantity in that place
     * @throws GenericEntityException when the ledger cannot be read
     */
    static double locationOnHand(Delegator delegator, String lotId, String locationId)
            throws GenericEntityException {
        double onHand = 0.0;
        EntityQuery query = EntityQuery.use(delegator).from("MfgInventoryDetail");
        if (lotId != null) {
            query = query.where("lotId", lotId);
        }
        for (GenericValue movement : query.queryList()) {
            String movementLocation = movement.getString("locationId");
            if (locationId == null ? movementLocation != null : !locationId.equals(movementLocation)) {
                continue;
            }
            onHand += MfgSupport.numberOrZero(movement.get("quantityDiff"));
        }
        return MfgSupport.round2(onHand);
    }
}
