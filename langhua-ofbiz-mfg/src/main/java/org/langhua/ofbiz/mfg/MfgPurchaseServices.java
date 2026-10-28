package org.langhua.ofbiz.mfg;

import org.langhua.ofbiz.mfg.oir.OirOrders;

import java.text.SimpleDateFormat;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.apache.ofbiz.base.util.Debug;
import org.apache.ofbiz.base.util.UtilGenerics;
import org.apache.ofbiz.base.util.UtilMisc;
import org.apache.ofbiz.entity.Delegator;
import org.apache.ofbiz.entity.GenericEntityException;
import org.apache.ofbiz.entity.GenericValue;
import org.apache.ofbiz.entity.util.EntityQuery;
import org.apache.ofbiz.service.DispatchContext;
import org.apache.ofbiz.service.GenericServiceException;
import org.apache.ofbiz.service.ServiceUtil;

/**
 * Purchase receipts: what was bought, from which shop, with the freight and the whole order
 * discount of that purchase kept on the receipt instead of being spread over the unit prices.
 *
 * <p>A receipt can be voided but not deleted - it is the paper that says what was bought - and the
 * receipt of a shop order is imported from a pasted line list or from the exported spreadsheet,
 * keyed by the order number so importing the same order twice updates instead of duplicating.
 * Moved out of {@code MfgServices} in Step 170.
 */
public final class MfgPurchaseServices {

    private MfgPurchaseServices() {
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
                if (MfgServices.RECEIPT_VOID.equals(statusOfReceipt(receipt))) {
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
        String denied = MfgServices.permissionProblem(dctx, context, MfgServices.PERM_STOCK);
        if (denied != null) {
            return ServiceUtil.returnError(denied);
        }
        Delegator delegator = dctx.getDelegator();
        String receiptId = MfgSupport.toText(context.get("receiptId"));
        String allocationId = MfgSupport.toText(context.get("allocationId"));
        if (allocationId == null) {
            allocationId = MfgServices.ALLOC_NONE;
        }
        if (!MfgServices.ALLOC_NONE.equals(allocationId)) {
            return ServiceUtil.returnError("运费暂时只记期间费用（" + MfgServices.ALLOC_NONE + "），按比例分摊还没有打开");
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
                receipt.set("allocationId", MfgServices.ALLOC_NONE);
                receipt.set("receiptDate", java.sql.Date.valueOf(LocalDate.now()));
                receipt.set("statusId", MfgServices.RECEIPT_ACTIVE);
            } else if (MfgServices.RECEIPT_VOID.equals(statusOfReceipt(receipt))) {
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
        String denied = MfgServices.permissionProblem(dctx, context, MfgServices.PERM_STOCK);
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
            if (MfgServices.RECEIPT_VOID.equals(statusOfReceipt(receipt))) {
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
                if (other != null && !MfgServices.RECEIPT_VOID.equals(statusOfReceipt(EntityQuery.use(delegator)
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
        String denied = MfgServices.permissionProblem(dctx, context, MfgServices.PERM_STOCK);
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
            if (MfgServices.RECEIPT_VOID.equals(statusOfReceipt(receipt))) {
                return ServiceUtil.returnError("到货单 [" + receiptId + "] 已经作废了");
            }
            String reason = MfgSupport.toText(context.get("voidReason"));
            receipt.set("statusId", MfgServices.RECEIPT_VOID);
            receipt.set("voidedDate", new Timestamp(System.currentTimeMillis()));
            GenericValue userLogin = (GenericValue) context.get("userLogin");
            receipt.set("voidedByUserLoginId", userLogin == null ? null : userLogin.getString("userLoginId"));
            receipt.set("voidReason", reason == null ? "建错了" : reason);
            receipt.store();
            Map<String, Object> result = ServiceUtil.returnSuccess();
            result.put("receiptId", receiptId);
            result.put("voidedReceipt", receiptId);
            result.put("statusId", MfgServices.RECEIPT_VOID);
            return result;
        } catch (GenericEntityException e) {
            Debug.logError(e, "Could not void the receipt [" + receiptId + "]", MfgServices.MODULE);
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
        String denied = MfgServices.permissionProblem(dctx, context, MfgServices.PERM_STOCK);
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
            receipt.set("statusId", MfgServices.RECEIPT_ACTIVE);
            receipt.set("voidedDate", null);
            receipt.set("voidedByUserLoginId", null);
            receipt.set("voidReason", null);
            receipt.store();
            Map<String, Object> result = ServiceUtil.returnSuccess();
            result.put("receiptId", receiptId);
            result.put("statusId", MfgServices.RECEIPT_ACTIVE);
            return result;
        } catch (GenericEntityException e) {
            Debug.logError(e, "Could not restore the receipt [" + receiptId + "]", MfgServices.MODULE);
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
                if (!withVoid && MfgServices.RECEIPT_VOID.equals(statusOfReceipt(receipt))) {
                    continue;
                }
                Map<String, Object> row = toReceiptMap(receipt);
                List<Map<String, Object>> lines = linesOf.get(receipt.getString("receiptId"));
                row.putAll(receiptSummary(lines == null ? new ArrayList<Map<String, Object>>() : lines,
                        MfgSupport.numberOrZero(receipt.get("freightAmount")),
                        MfgSupport.numberOrZero(receipt.get("discountAmount"))));
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
                    .where("statusId", MfgServices.RECEIPT_VOID).queryList()) {
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
                if (!MfgStockLedger.matchesLotKind(lot, MfgServices.LOT_COMPONENT)) {
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
                if (freeLots.size() >= MfgServices.MAX_FREE_LOTS) {
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
        String denied = MfgServices.permissionProblem(dctx, context, MfgServices.PERM_STOCK);
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
            if (row[0].length() > MfgServices.MAX_COMPONENT_CODE) {
                // a shop order often carries a package description as the part number, which is
                // longer than the code field; short it instead of refusing the whole order
                String fullCode = row[0];
                row[0] = MfgServices.fittingComponentCode(fullCode);
                row[1] = MfgServices.nameWithFullCode(row[1], fullCode);
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
                receipt.set("allocationId", MfgServices.ALLOC_NONE);
                receipt.set("note", MfgSupport.toText(context.get("note")));
                receipt.set("statusId", MfgServices.RECEIPT_ACTIVE);
                delegator.create(receipt);
            } else if (MfgServices.RECEIPT_VOID.equals(statusOfReceipt(receipt))) {
                // the order number is the key: importing it again means it was wanted after all, so
                // the voided receipt of that order comes back into force instead of staying dead
                receipt.set("statusId", MfgServices.RECEIPT_ACTIVE);
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
                String lotId = MfgServices.nextLotId(delegator, LocalDate.now());
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
                MfgStockLedger.recordMovement(delegator, lot, MfgServices.MOVE_RECEIPT, qty, lot.get("unitPrice"), null, null,
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
            Debug.logError(e, "Could not book the shop order [" + supplierOrderNo + "]", MfgServices.MODULE);
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
    static String firstSentence(String message) {
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
            Debug.logError(e, "Could not remember the refunded order [" + orderNo + "]", MfgServices.MODULE);
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
    static void rememberRefundedOrderInNewTransaction(DispatchContext dctx,
            Map<String, ? extends Object> context, String orderNo, String shop, String note) {
        if (orderNo == null || dctx.getDispatcher() == null) {
            return;
        }
        Map<String, Object> ask = UtilMisc.toMap("orderNo", orderNo, "shop", shop, "note", note,
                "userLogin", context.get("userLogin"));
        try {
            dctx.getDispatcher().runSync("mfgRefundedOrderSave", ask, -1, true);
        } catch (GenericServiceException e) {
            Debug.logError(e, "Could not remember the refunded order [" + orderNo + "]", MfgServices.MODULE);
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
        String denied = MfgServices.permissionProblem(dctx, context, MfgServices.PERM_STOCK);
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
        String denied = MfgServices.permissionProblem(dctx, context, MfgServices.PERM_STOCK);
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
            Debug.logError(e, "Could not read the refunded orders", MfgServices.MODULE);
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
                        .where("lotId", lotId, "movementTypeId", MfgServices.MOVE_RECEIPT).queryFirst();
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
            if (receipt == null || MfgServices.RECEIPT_VOID.equals(statusOfReceipt(receipt))) {
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

    static String lotLineKey(String componentCode, Object qty, Object amount) {
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
    static GenericValue claimOne(List<GenericValue> lines, Set<String> claimed) {
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
    static String receiptLineId(GenericValue line) {
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
    static String nextReceiptId(Delegator delegator, LocalDate today) throws GenericEntityException {
        String prefix = "PR-" + today.format(MfgServices.DAY) + "-";
        int max = 0;
        for (GenericValue receipt : EntityQuery.use(delegator).from("MfgPurchaseReceipt").queryList()) {
            String id = receipt.getString("receiptId");
            if (id != null && id.startsWith(prefix)) {
                max = Math.max(max, MfgServices.parseTrailingNumber(id.substring(prefix.length())));
            }
        }
        return prefix + String.format(Locale.ROOT, "%02d", max + 1);
    }

    static long nextReceiptLineSeq(Delegator delegator, String receiptId) throws GenericEntityException {
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
    static long freeReceiptLineSeq(Delegator delegator, String receiptId, long candidate)
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

    static Map<String, Object> toReceiptMap(GenericValue receipt) {
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
     * @return {@link MfgServices#RECEIPT_VOID} or {@link MfgServices#RECEIPT_ACTIVE}
     */
    static String statusOfReceipt(GenericValue receipt) {
        if (receipt == null) {
            return MfgServices.RECEIPT_ACTIVE;
        }
        String status = receipt.getString("statusId");
        return MfgServices.RECEIPT_VOID.equals(status) ? MfgServices.RECEIPT_VOID : MfgServices.RECEIPT_ACTIVE;
    }

    static Map<String, Object> toReceiptLineMap(GenericValue line) {
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
}
