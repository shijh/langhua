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

import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
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
 * The stock pages: the lots, incoming inspection, the locations and the transfers, and the ledger
 * that every one of them writes into.
 *
 * <p>The arithmetic of the ledger itself is {@link MfgStockLedger}; this class is the service layer
 * on top of it: it validates what the page asked for and turns a stock count, a scrap, a transfer
 * or an inspection into rows of the ledger. A lot never changes its quantity by hand - the totals
 * are always summed back from those rows. Moved out of {@code MfgServices} in Step 171.
 */
public final class MfgStockServices {

    private MfgStockServices() {
    }

    /**
     * Creates or updates one material lot. When {@code lotId} is empty a new identifier
     * {@code LOT-yyMMdd-NN} is generated.
     *
     * @param dctx the dispatch context
     * @param context service context, see {@code servicedef/stock-services.xml#mfgLotSave}
     * @return the service result containing {@code lotId}
     */
    public static Map<String, Object> lotSave(DispatchContext dctx, Map<String, ? extends Object> context) {
        String denied = MfgSecurity.permissionProblem(dctx, context, MfgConstants.PERM_STOCK);
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
                lotId = MfgStockServices.nextLotId(delegator, LocalDate.now());
                isNew = true;
            } else if (EntityQuery.use(delegator).from("MfgMaterialLot")
                    .where("lotId", lotId).queryOne() == null) {
                isNew = true;
            }
            GenericValue lot = delegator.makeValue("MfgMaterialLot");
            lot.set("lotId", lotId);
            lot.set("componentCode", componentCode);
            // the name comes in as partName: componentName is what the container fills with the
            // component itself ("mfg"), so a parameter of that name never survives
            lot.set("componentName", MfgSupport.toText(context.get("partName")));
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
                        + "], expected " + String.join(" / ", MfgConstants.INSPECT_STATUSES));
            }
            lot.set("inspectStatusId", inspectStatus);
            lot.set("note", MfgSupport.toText(context.get("note")));
            delegator.createOrStore(lot);
            if (isNew) {
                MfgStockLedger.recordMovement(delegator, lot, MfgConstants.MOVE_RECEIPT, receivedQty, lot.get("unitPrice"), null, null,
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
     * Rewrites the readable details of a lot: its name, the supplier, the supplier's batch number and
     * the note. The part number is not touched - it is the key that ties the lot to the BOM, the
     * issues and the receipt, so it moves with {@code mfgPartCodeRename} instead.
     *
     * <p>Why a separate service: a lot imported from a shop order carries the whole article title as
     * its name ("PCB打样电路板线路板pcb板打样批量生产加工源头厂家制作定制"), and that is what the page offers
     * while a BOM row's part number is corrected - a name like "PCB 线圈板 v94" is what makes the right
     * lot recognizable in that list.
     *
     * <p>Only the fields that were sent are written, so a form that shows one field cannot wipe the
     * rest; a blank field clears the value.
     *
     * @param dctx the dispatch context
     * @param context service context, see {@code servicedef/stock-services.xml#mfgLotEdit}
     * @return the service result with {@code lotId}, {@code editedLot} and {@code search}
     */
    public static Map<String, Object> lotEdit(DispatchContext dctx, Map<String, ? extends Object> context) {
        String denied = MfgSecurity.permissionProblem(dctx, context, MfgConstants.PERM_STOCK);
        if (denied != null) {
            return ServiceUtil.returnError(denied);
        }
        Delegator delegator = dctx.getDelegator();
        String lotId = MfgSupport.toText(context.get("lotId"));
        if (lotId == null) {
            return ServiceUtil.returnError("lotId is required");
        }
        try {
            GenericValue lot = EntityQuery.use(delegator).from("MfgMaterialLot").where("lotId", lotId).queryOne();
            if (lot == null) {
                return ServiceUtil.returnError("Unknown material lot [" + lotId + "]");
            }
            if (context.containsKey("partName")) {
                lot.set("componentName", MfgSupport.toText(context.get("partName")));
            }
            if (context.containsKey("supplierName")) {
                lot.set("supplierName", MfgSupport.toText(context.get("supplierName")));
            }
            if (context.containsKey("supplierLot")) {
                lot.set("supplierLot", MfgSupport.toText(context.get("supplierLot")));
            }
            if (context.containsKey("note")) {
                lot.set("note", MfgSupport.toText(context.get("note")));
            }
            delegator.store(lot);
            Map<String, Object> result = ServiceUtil.returnSuccess();
            result.put("lotId", lotId);
            result.put("editedLot", lotId);
            keepView(result, context);
            narrowToTheLot(result, lotId);
            return result;
        } catch (GenericEntityException e) {
            return ServiceUtil.returnError("保存批次资料失败：" + e.getMessage());
        }
    }

    /**
     * Corrects the part number of <b>one lot</b>: the lot, the issues made from it and the receipt
     * line it hangs on follow. Everything else keeps its own number - two reels of the same article
     * are still two lots, and a shop that has booked forty lots under one guessed code wants to fix
     * one of them, not all of them.
     *
     * <p>{@code mfgPartCodeRename} stays the tool for "this code is wrong everywhere" (it also moves
     * the BOM lines and the stock setting); this one is the tool for "this parcel is a different
     * part".
     *
     * @param dctx the dispatch context
     * @param context service context, see {@code servicedef/stock-services.xml#mfgLotCodeChange}
     * @return the service result with {@code lotId}, {@code newCode}, {@code renamedLot} and the view
     *         fields the page posted ({@code search} / {@code page} / {@code pageSize} / {@code componentCode})
     */
    public static Map<String, Object> lotCodeChange(DispatchContext dctx, Map<String, ? extends Object> context) {
        String denied = MfgSecurity.permissionProblem(dctx, context, MfgConstants.PERM_STOCK);
        if (denied != null) {
            return ServiceUtil.returnError(denied);
        }
        Delegator delegator = dctx.getDelegator();
        String lotId = MfgSupport.toText(context.get("lotId"));
        String newCode = MfgSupport.toText(context.get("partCode"));
        if (lotId == null) {
            return ServiceUtil.returnError("lotId is required");
        }
        if (newCode == null) {
            return ServiceUtil.returnError("新料号要填");
        }
        if (newCode.length() > MfgConstants.MAX_COMPONENT_CODE) {
            return ServiceUtil.returnError("新料号太长（最多 " + MfgConstants.MAX_COMPONENT_CODE + " 个字符）：" + newCode);
        }
        try {
            GenericValue lot = EntityQuery.use(delegator).from("MfgMaterialLot").where("lotId", lotId).queryOne();
            if (lot == null) {
                return ServiceUtil.returnError("Unknown material lot [" + lotId + "]");
            }
            String oldCode = lot.getString("componentCode");
            Map<String, Object> result = ServiceUtil.returnSuccess();
            result.put("lotId", lotId);
            result.put("renamedLot", lotId);
            result.put("newCode", newCode);
            keepView(result, context);
            narrowToTheLot(result, lotId);
            if (newCode.equals(oldCode)) {
                return result;
            }
            lot.set("componentCode", newCode);
            delegator.store(lot);
            // the rows that hang on this very lot follow it; rows of other lots keep their number
            int issues = 0;
            for (GenericValue issue : EntityQuery.use(delegator).from("MfgMaterialIssue")
                    .where("lotId", lotId).queryList()) {
                issue.set("componentCode", newCode);
                issue.store();
                issues++;
            }
            int lines = 0;
            for (GenericValue line : EntityQuery.use(delegator).from("MfgPurchaseReceiptLine")
                    .where("lotId", lotId).queryList()) {
                line.set("componentCode", newCode);
                line.store();
                lines++;
            }
            result.put("issuesUpdated", String.valueOf(issues));
            result.put("receiptLinesUpdated", String.valueOf(lines));
            result.put("oldCode", oldCode);
            return result;
        } catch (GenericEntityException e) {
            return ServiceUtil.returnError("改料号失败：" + e.getMessage());
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
     * @param context service context, see {@code servicedef/stock-services.xml#mfgLotInspect}
     * @return the service result containing {@code lotId} and the stored status
     */
    public static Map<String, Object> lotInspect(DispatchContext dctx, Map<String, ? extends Object> context) {
        String denied = MfgSecurity.permissionProblem(dctx, context, MfgConstants.PERM_STOCK);
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
                    + "], expected " + String.join(" / ", MfgConstants.INSPECT_STATUSES));
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
            lot.set("inspectDate", MfgConstants.IQC_PENDING.equals(status) ? null : java.sql.Date.valueOf(LocalDate.now()));
            delegator.store(lot);

            Map<String, Object> result = ServiceUtil.returnSuccess();
            result.put("lotId", lotId);
            result.put("inspectStatusId", status);
            result.put("rejectedQty", Double.valueOf(rejectedQty));
            keepView(result, context);
            narrowToTheLot(result, lotId);
            return result;
        } catch (GenericEntityException e) {
            return ServiceUtil.returnError("Could not store the inspection: " + e.getMessage());
        }
    }

    /**
     * Sets the received quantity of a lot to the number of pieces that really arrived.
     *
     * <p>Why it exists: a PCB house sells "one order" of prototype boards and ships three to five of
     * them, the shop page prints {@code ¥21.00 x1}, and nobody knows how many boards are in the
     * parcel until it is opened. Booked as one piece the lot says one board for twenty-one yuan, so
     * both the stock and the cost of a board are wrong. Counting the parcel and restating the
     * quantity fixes the stock (a ledger row is appended, nothing is rewritten) and the unit cost
     * (the money already left the account, so 21 yuan for five boards is 4.20 a board), and brings
     * the receipt line of the lot along so the purchase document says five too.
     *
     * @param dctx the dispatch context
     * @param context service context, see {@code servicedef/stock-services.xml#mfgLotRecount}
     * @return the service result with {@code receivedQty}, {@code onHandQty}, {@code unitPrice} and
     *         {@code diffQty}
     */
    public static Map<String, Object> lotRecount(DispatchContext dctx, Map<String, ? extends Object> context) {
        String denied = MfgSecurity.permissionProblem(dctx, context, MfgConstants.PERM_STOCK);
        if (denied != null) {
            return ServiceUtil.returnError(denied);
        }
        Delegator delegator = dctx.getDelegator();
        String lotId = MfgSupport.toText(context.get("lotId"));
        if (lotId == null) {
            return ServiceUtil.returnError("lotId is required");
        }
        double actualQty = MfgSupport.toDouble(context.get("actualQty"), 0.0);
        if (actualQty <= 0.0) {
            return ServiceUtil.returnError("点数得到的数量要大于 0");
        }
        String note = MfgSupport.toText(context.get("note"));
        String operatorName = MfgSupport.toText(context.get("operatorName"));
        try {
            GenericValue lot = EntityQuery.use(delegator).from("MfgMaterialLot").where("lotId", lotId).queryOne();
            if (lot == null) {
                return ServiceUtil.returnError("Unknown material lot [" + lotId + "]");
            }
            if (MfgStockLedger.isProductLot(lot)) {
                return ServiceUtil.returnError("批次 [" + lotId + "] 是成品，数量由工单完工入库决定，不在这里改");
            }
            MfgStockLedger.ensureLotLedger(delegator, lot);
            double received = MfgSupport.numberOrZero(lot.get("receivedQty"));
            double diff = actualQty - received;
            double unitPrice = restateLot(delegator, lot, actualQty, operatorName, "按点数修正到货数量", note);
            Map<String, Object> result = ServiceUtil.returnSuccess();
            result.put("lotId", lotId);
            result.put("diffQty", MfgSupport.formatQty(diff));
            result.put("receivedQty", MfgSupport.formatQty(actualQty));
            result.put("onHandQty", MfgSupport.formatQty(MfgSupport.numberOrZero(lot.get("onHandQty"))));
            result.put("unitPrice", mfgDouble(unitPrice));
            result.put("recounted", lotId);
            result.put("recountQty", MfgSupport.formatQty(actualQty));
            keepView(result, context);
            // the reader is brought back to this very row: with no search of his own he used to land
            // on page one of the whole ledger, where the row he just changed was often not to be seen,
            // so the page showed the success line and nothing that proved it (Step 179s)
            narrowToTheLot(result, lotId);
            return result;
        } catch (GenericEntityException e) {
            return ServiceUtil.returnError("按点数修正失败：" + e.getMessage());
        }
    }

    /**
     * Splits one lot into the lots its contents really are.
     *
     * <p>Why: a shop sells "1/4W metal film resistors, 130 values, 20 of each" as one article of 2600
     * pieces, and a breadboard build then needs them one value at a time (a BOM row says 100Ω). One
     * lot can only carry one part number, so the parcel has to become one lot per value. Doing that
     * by hand means typing 130 rows and 130 unit prices, and the money would stay on the empty parcel.
     *
     * <p>So this takes the quantity out of the parent (a ledger row, nothing is rewritten), creates a
     * lot per typed line with the unit price, the supplier, the batch number, the date and the
     * inspection state of the parent, and puts the quantity in there. The parent keeps the rest; its
     * received quantity and amount shrink by what moved, so a later count still prices it right.
     *
     * @param dctx the dispatch context
     * @param context service context, see {@code servicedef/stock-services.xml#mfgLotSplit}: {@code lines}
     *        is one {@code part number, name, quantity} per line, the name may be left out
     * @return the service result with {@code splitLot}, {@code splitCount}, {@code splitQty} and
     *         {@code splitRef}, plus the view the page posted
     */
    public static Map<String, Object> lotSplit(DispatchContext dctx, Map<String, ? extends Object> context) {
        String denied = MfgSecurity.permissionProblem(dctx, context, MfgConstants.PERM_STOCK);
        if (denied != null) {
            return ServiceUtil.returnError(denied);
        }
        Delegator delegator = dctx.getDelegator();
        String lotId = MfgSupport.toText(context.get("lotId"));
        if (lotId == null) {
            return ServiceUtil.returnError("要填原批次的批次号（例如 LOT-261025-844）");
        }
        List<Map<String, Object>> rows = splitRowsOf(MfgSupport.toText(context.get("lines")));
        if (rows.isEmpty()) {
            return ServiceUtil.returnError("没有读到要拆的料号：一行一个「料号,名称,数量」，名称可以空着");
        }
        List<String> problems = new ArrayList<>();
        List<Map<String, Object>> readable = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            String code = MfgSupport.toText(row.get("componentCode"));
            Double qty = (Double) row.get("qty");
            int lineNo = ((Integer) row.get("lineNo")).intValue();
            if (code == null) {
                problems.add("第 " + lineNo + " 行没有料号：" + row.get("raw"));
                continue;
            }
            if (qty == null || qty.doubleValue() <= 0.0) {
                // the quantity has to be its own cell: a full-width comma is fine (it is turned into
                // a normal one), a name that swallows the number is not
                problems.add("第 " + lineNo + " 行读不出数量（数量要单独放最后一格，用英文或中文逗号都行）："
                        + row.get("raw"));
                continue;
            }
            if (code.length() > MfgConstants.MAX_COMPONENT_CODE) {
                problems.add("第 " + lineNo + " 行的料号太长（最多 " + MfgConstants.MAX_COMPONENT_CODE
                        + " 个字符）：" + code);
                continue;
            }
            readable.add(row);
        }
        if (!problems.isEmpty()) {
            StringBuilder message = new StringBuilder("拆包内容有问题");
            message.append(problems.size() > 3 ? "（先列前三处，共 " + problems.size() + " 处）："
                    : "：");
            for (int i = 0; i < problems.size() && i < 3; i++) {
                if (i > 0) {
                    message.append("；");
                }
                message.append(problems.get(i));
            }
            return ServiceUtil.returnError(message.toString());
        }
        // a part number typed twice is a slip of the paste, not a reason to refuse the whole list:
        // the first line wins, the later ones are dropped and the answer says which
        List<String> dropped = new ArrayList<>();
        List<Map<String, Object>> kept = withoutRepeatedRows(readable, dropped);
        double total = 0.0;
        for (Map<String, Object> row : kept) {
            total = MfgSupport.round2(total + ((Double) row.get("qty")).doubleValue());
        }
        // how many pieces the parcel really holds: a shop writes x1 for a whole pack, and when only a
        // part of it is split out the rest has to stay on the parcel instead of disappearing
        double packQty = MfgSupport.toDouble(context.get("packQty"), 0.0);
        if (packQty > 0.0 && packQty < total - 0.0001) {
            return ServiceUtil.returnError("清单要拆 " + MfgSupport.formatQty(total) + " 件，比「这一包实际多少件」"
                    + MfgSupport.formatQty(packQty) + " 还多，改一下其中一个再试");
        }
        String operatorName = MfgSupport.toText(context.get("operatorName"));
        String note = MfgSupport.toText(context.get("note"));
        try {
            GenericValue parent = EntityQuery.use(delegator).from("MfgMaterialLot").where("lotId", lotId).queryOne();
            if (parent == null) {
                return ServiceUtil.returnError("Unknown material lot [" + lotId + "]");
            }
            if (MfgStockLedger.isProductLot(parent)) {
                return ServiceUtil.returnError("批次 [" + lotId + "] 是成品，成品走出货，不拆包");
            }
            String parentCode = parent.getString("componentCode");
            for (Map<String, Object> row : kept) {
                if (parentCode != null
                        && parentCode.equalsIgnoreCase(MfgSupport.toText(row.get("componentCode")))) {
                    return ServiceUtil.returnError("子批次的料号不能和原批次一样（" + parentCode + "），换个号再拆");
                }
            }
            MfgStockLedger.ensureLotLedger(delegator, parent);
            double onHand = MfgSupport.numberOrZero(parent.get("onHandQty"));
            double reserved = MfgSupport.numberOrZero(parent.get("reservedQty"));
            double available = MfgStockLedger.availableOf(onHand, reserved, parent.getString("inspectStatusId"));
            String restatedNote = null;
            boolean restate = restatesTheParcel(total, onHand, reserved)
                    || (packQty > 0.0 && packQty > onHand + 0.0001 && reserved <= 0.0001);
            if (restate) {
                // a shop writes "x1" for a whole pack, so the ledger says one piece while the pack
                // holds 2600. The list being split - or the count typed next to it - is the person
                // saying what is really inside: the count is corrected first and the pieces come out
                // afterwards, in the same transaction
                double restateTo = packQty > total + 0.0001 ? packQty : total;
                double before = MfgSupport.numberOrZero(parent.get("receivedQty"));
                restateLot(delegator, parent, restateTo, operatorName, "按拆包清单修正到货数量",
                        "拆包前按清单件数修正");
                MfgStockLedger.ensureLotLedger(delegator, parent);
                onHand = MfgSupport.numberOrZero(parent.get("onHandQty"));
                reserved = MfgSupport.numberOrZero(parent.get("reservedQty"));
                available = MfgStockLedger.availableOf(onHand, reserved,
                        parent.getString("inspectStatusId"));
                restatedNote = MfgSupport.formatQty(before) + " → " + MfgSupport.formatQty(restateTo);
            }
            if (total > available + 0.0001) {
                // the ledger is what counts, and only a parcel booked as one piece is corrected by
                // itself; a reserved one cannot be taken apart at all
                return ServiceUtil.returnError("要拆 " + MfgSupport.formatQty(total) + " 件，但批次 [" + lotId
                        + "] 只有 " + MfgSupport.formatQty(available) + " 件可用"
                        + (reserved > 0.0001
                                ? "（其中 " + MfgSupport.formatQty(reserved) + " 件已被预留，先释放再拆）"
                                : "；先用「点数修正」把数量改对，再拆"));
            }
            // the exact cost of one piece (amount / quantity); the price columns of the lot and of
            // the movement only keep two decimals, so the money of a child comes from this number
            double unitCost = MfgStockLedger.lotUnitCost(parent);
            double unitPrice = MfgSupport.round2(unitCost);
            LocalDate today = LocalDate.now();
            String prefix = "LOT-" + today.format(MfgConstants.DAY) + "-";
            int sequence = MfgSupport.parseTrailingNumber(nextLotId(delegator, today).substring(prefix.length()));
            String parentName = MfgSupport.toText(parent.getString("componentName"));
            java.sql.Date receivedDate = parent.getDate("receivedDate");
            String ref = "SPLIT-" + lotId;
            List<String> childIds = new ArrayList<>();
            int index = 0;
            for (Map<String, Object> row : kept) {
                String code = MfgSupport.toText(row.get("componentCode"));
                String name = MfgSupport.toText(row.get("componentName"));
                double qty = ((Double) row.get("qty")).doubleValue();
                String childId = formatLotId(today, sequence + index);
                index++;
                GenericValue child = delegator.makeValue("MfgMaterialLot");
                child.set("lotId", childId);
                child.set("componentCode", code);
                child.set("componentName", name == null ? parentName : name);
                child.set("supplierName", parent.getString("supplierName"));
                child.set("supplierLot", parent.getString("supplierLot"));
                child.set("receivedQty", Double.valueOf(MfgSupport.round2(qty)));
                child.set("receivedDate", receivedDate);
                child.set("receivedAmount", Double.valueOf(MfgSupport.round2(qty * unitCost)));
                child.set("unitPrice", Double.valueOf(unitPrice));
                child.set("inspectStatusId", parent.getString("inspectStatusId"));
                child.set("splitFromLotId", lotId);
                child.set("note", "从 " + lotId + " 拆包" + (note == null ? "" : "：" + note));
                delegator.create(child);
                MfgStockLedger.recordMovement(delegator, child, MfgConstants.MOVE_RECEIPT, qty,
                        Double.valueOf(unitPrice), null, null, ref, operatorName, null,
                        "从 " + lotId + " 拆包");
                MfgStockLedger.recalcLotStock(delegator, child);
                childIds.add(childId);
            }
            MfgStockLedger.recordMovement(delegator, parent, MfgConstants.MOVE_ADJUST, -total,
                    Double.valueOf(unitPrice), null, null, ref, operatorName, null,
                    "拆包到 " + kept.size() + " 个子批次" + (note == null ? "" : "：" + note));
            double received = MfgSupport.numberOrZero(parent.get("receivedQty"));
            if (parent.get("receivedQty") != null) {
                parent.set("receivedQty", Double.valueOf(MfgSupport.round2(Math.max(0.0, received - total))));
            }
            Double amount = MfgSupport.optionalAmount(parent.get("receivedAmount"));
            if (amount != null) {
                parent.set("receivedAmount", Double.valueOf(
                        MfgSupport.round2(Math.max(0.0, amount.doubleValue() - total * unitCost))));
            }
            MfgStockLedger.recalcLotStock(delegator, parent);
            parent.store();

            Map<String, Object> result = ServiceUtil.returnSuccess();
            result.put("splitLot", lotId);
            result.put("splitCount", String.valueOf(kept.size()));
            result.put("splitQty", MfgSupport.formatQty(total));
            result.put("splitRef", ref);
            result.put("unitPrice", MfgSupport.formatQty(unitPrice));
            result.put("splitIds", childIds);
            // the repeated lines are not a failure: say which ones were left out, so nobody wonders
            result.put("splitDropped", String.valueOf(dropped.size()));
            result.put("splitDroppedNote", dropped.isEmpty() ? null : joinDropped(dropped));
            result.put("splitRestated", restatedNote);
            keepView(result, context);
            return result;
        } catch (GenericEntityException e) {
            return ServiceUtil.returnError("拆包失败：" + e.getMessage());
        }
    }

    /**
     * Reads the box of the split form: one part per line, {@code part number, name, quantity}.
     *
     * <p>The name may be left out ({@code part number, quantity}), blank lines and lines starting
     * with {@code #} are skipped, and a pasted spreadsheet range separates its cells with tabs. A row
     * whose last cell is not a number is kept with a {@code null} quantity, so the service can say
     * which line is wrong instead of quietly splitting less than the person asked for.
     *
     * @param text what was pasted, may be null
     * @return the rows, each with {@code lineNo}, {@code raw}, {@code componentCode},
     *         {@code componentName} and {@code qty}
     */
    static List<Map<String, Object>> splitRowsOf(String text) {
        List<Map<String, Object>> rows = new ArrayList<>();
        if (text == null) {
            return rows;
        }
        String[] lines = text.split("\r\n|\n|\r");
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            if (line == null) {
                continue;
            }
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }
            // a full-width comma is what a Chinese keyboard types, and it is what turns up when the
            // list is written by hand: "R-1/4W-2R3, 1/4W电阻，2R3，20" would otherwise be two cells
            String normalized = trimmed.replace('，', ',').replace('、', ',');
            String[] cells = MfgCsv.splitCsvLine(normalized);
            String firstCell = cells.length > 0 ? MfgSupport.toText(cells[0]) : null;
            if (firstCell != null && isHeaderCell(firstCell)) {
                // a header copied along with the rows is not a part
                continue;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("lineNo", Integer.valueOf(i + 1));
            row.put("raw", trimmed);
            row.put("componentCode", firstCell);
            row.put("componentName", null);
            row.put("qty", null);
            if (cells.length >= 2) {
                String last = MfgSupport.toText(cells[cells.length - 1]);
                Double qty = quantityOf(last);
                row.put("qty", qty);
                if (qty != null && cells.length > 2) {
                    // the name may itself contain a comma, so everything between the two ends is it
                    StringBuilder name = new StringBuilder();
                    for (int c = 1; c < cells.length - 1; c++) {
                        String part = MfgSupport.toText(cells[c]);
                        if (part == null) {
                            continue;
                        }
                        if (name.length() > 0) {
                            name.append(',');
                        }
                        name.append(part);
                    }
                    row.put("componentName", name.length() == 0 ? null : name.toString());
                }
            }
            rows.add(row);
        }
        return rows;
    }

    /**
     * Sets the received quantity of a lot to the number of pieces that are really in it, keeping the
     * money that was paid.
     *
     * <p>The arrival row keeps what was typed; the correction is its own ledger row, so the history
     * of the lot still says who counted it and when. The unit price follows the count (26 yuan for
     * 260 pieces is 0.10 a piece), and the arrival line of the purchase document is restated with it.
     *
     * <p>Shared by the count correction and by the split, which corrects a parcel booked as one piece
     * before taking the pieces out.
     *
     * @param delegator the delegator
     * @param lot the lot to restate, with its ledger already made sure of
     * @param actualQty how many pieces are really in it
     * @param operatorName who counted
     * @param reason what the ledger row says it was for, e.g. {@code 按点数修正到货数量}
     * @param note a remark to append, may be null
     * @return the unit price the lot carries afterwards
     * @throws GenericEntityException when the row cannot be written
     */
    static double restateLot(Delegator delegator, GenericValue lot, double actualQty, String operatorName,
            String reason, String note) throws GenericEntityException {
        double received = MfgSupport.numberOrZero(lot.get("receivedQty"));
        double amount = MfgSupport.numberOrZero(lot.get("receivedAmount"));
        double diff = actualQty - received;
        double unitPrice = restatedUnitPrice(amount, actualQty,
                MfgSupport.numberOrZero(lot.get("unitPrice")));
        if (Math.abs(diff) > 0.0001) {
            MfgStockLedger.recordMovement(delegator, lot, MfgConstants.MOVE_ADJUST, diff,
                    Double.valueOf(unitPrice), null, null, null, operatorName, null,
                    reason + "：" + MfgSupport.formatQty(received) + " → "
                            + MfgSupport.formatQty(actualQty)
                            + (note == null ? "" : "（" + note + "）"));
            lot.set("receivedQty", Double.valueOf(actualQty));
            lot.set("unitPrice", Double.valueOf(unitPrice));
            lot.store();
            restateReceiptLine(delegator, lot, actualQty, amount, unitPrice);
            MfgStockLedger.recalcLotStock(delegator, lot);
        }
        return unitPrice;
    }

    /**
     * Whether a split should first correct the count of the parcel it takes the pieces out of.
     *
     * <p>A shop writes {@code ¥21.00 x1} for a whole pack, so the ledger says one piece while the pack
     * holds 2600: taking 2600 out of it is the person saying what is really inside, so the count is
     * corrected first and the pieces come out afterwards. Any other parcel is left alone - splitting
     * more than the ledger has is a mistake there, and correcting it silently would hide it.
     *
     * @param total how many pieces the pasted list takes out
     * @param onHand how many the parcel holds according to the ledger
     * @param reserved how many of them are earmarked for a project or an order
     * @return {@code true} when the parcel is booked as one piece and the list is larger than that
     */
    static boolean restatesTheParcel(double total, double onHand, double reserved) {
        return total > onHand + 0.0001 && onHand <= 1.0001 && reserved <= 0.0001;
    }

    /** The quantity of a split line, {@code null} when the cell is not a positive number. */
    private static Double quantityOf(String text) {
        if (text == null) {
            return null;
        }
        try {
            double qty = Double.parseDouble(text);
            if (Double.isNaN(qty) || qty <= 0.0) {
                return null;
            }
            return Double.valueOf(qty);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Drops the lines whose part number was already typed, and writes down which ones they were.
     *
     * <p>A list of 130 values copied out of a spreadsheet often has a line twice; refusing the whole
     * list over that makes the person hunt for it. The first line wins (it is the one they meant),
     * the later ones are left out, and the page says which line repeated which so nothing is lost
     * silently.
     *
     * @param rows the readable lines, in the order they were pasted
     * @param dropped receives one message per line that was left out
     * @return the lines to split, in paste order
     */
    static List<Map<String, Object>> withoutRepeatedRows(List<Map<String, Object>> rows, List<String> dropped) {
        List<Map<String, Object>> kept = new ArrayList<>();
        Map<String, Integer> firstLineOf = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            String code = MfgSupport.toText(row.get("componentCode"));
            if (code == null) {
                continue;
            }
            int lineNo = ((Integer) row.get("lineNo")).intValue();
            Integer seenAt = firstLineOf.put(code.toUpperCase(Locale.ROOT), Integer.valueOf(lineNo));
            if (seenAt == null) {
                kept.add(row);
            } else if (dropped != null) {
                dropped.add("第 " + lineNo + " 行（" + code + "）和第 " + seenAt + " 行重复，已忽略这行");
            }
        }
        return kept;
    }

    /** The repeated lines of a split as one sentence, cut short so the page stays readable. */
    private static String joinDropped(List<String> dropped) {
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < dropped.size() && i < 3; i++) {
            if (i > 0) {
                text.append("；");
            }
            text.append(dropped.get(i));
        }
        if (dropped.size() > 3) {
            text.append("；另有 ").append(dropped.size() - 3).append(" 行重复也忽略了");
        }
        return text.toString();
    }

    /** Whether a first cell is a column title copied along with the rows. */
    private static boolean isHeaderCell(String cell) {
        String text = cell.trim();
        if (text.equals("料号") || text.equals("零件号") || text.equals("数量")) {
            return true;
        }
        String lower = text.toLowerCase(Locale.ROOT);
        return lower.equals("part") || lower.equals("part number") || lower.equals("code")
                || lower.equals("componentcode");
    }

    /**
     * The unit price a lot shows once its received quantity is the real count. The money was paid
     * once, so five boards that cost 21 yuan are 4.20 each, not 21 each.
     *
     * @param amount what was really paid, {@code 0} when it is not known
     * @param actualQty the counted quantity
     * @param currentPrice the price the lot carries now, kept when the amount is unknown
     * @return the price of one piece
     */
    static double restatedUnitPrice(double amount, double actualQty, double currentPrice) {
        if (amount > 0.0 && actualQty > 0.0) {
            return MfgSupport.round2(amount / actualQty);
        }
        return currentPrice;
    }

    /**
     * Writes the counted quantity into the receipt line of the lot, so the purchase document says
     * the same as the stock. A voided receipt is left alone: it is the record of what was once typed.
     */
    private static void restateReceiptLine(Delegator delegator, GenericValue lot, double actualQty,
            double amount, double unitPrice) throws GenericEntityException {
        GenericValue line = EntityQuery.use(delegator).from("MfgPurchaseReceiptLine")
                .where("lotId", lot.getString("lotId")).queryFirst();
        if (line == null) {
            return;
        }
        GenericValue receipt = EntityQuery.use(delegator).from("MfgPurchaseReceipt")
                .where("receiptId", line.getString("receiptId")).queryOne();
        if (receipt != null && MfgConstants.RECEIPT_VOID.equals(MfgPurchaseServices.statusOfReceipt(receipt))) {
            return;
        }
        line.set("qty", Double.valueOf(actualQty));
        line.set("unitPrice", Double.valueOf(unitPrice));
        line.set("goodsAmount", Double.valueOf(MfgSupport.round2(
                amount > 0.0 ? amount : actualQty * unitPrice)));
        line.store();
    }

    /** A formatted number as the OUT map of a service sends it to the page. */
    private static Double mfgDouble(double value) {
        return Double.valueOf(MfgSupport.round2(value));
    }

    /**
     * Lists material lots, newest first, with the quantity already issued.
     *
     * @param dctx the dispatch context
     * @param context service context, see {@code servicedef/stock-services.xml#mfgLotList}
     * @return the service result containing {@code lots}
     */
    public static Map<String, Object> lotList(DispatchContext dctx, Map<String, ? extends Object> context) {
        Delegator delegator = dctx.getDelegator();
        String componentCode = MfgSupport.toText(context.get("componentCode"));
        String kind = MfgSupport.toText(context.get("kind"));
        if (kind == null) {
            // the material page lists the purchased parts; the finished goods have their own page
            kind = MfgConstants.LOT_COMPONENT;
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
            // the picture the shop printed next to this article, when the lot came in from a screenshot:
            // one query for the page and not one per row
            Map<String, String> shots = shotOfLot(delegator);
            List<Map<String, Object>> list = new ArrayList<>(lots.size());
            for (GenericValue lot : lots) {
                Map<String, Object> row = MfgRecordMaps.toLotMap(lot, issued.get(lot.getString("lotId")));
                row.put("scanShotId", shots.get(lot.getString("lotId")));
                list.add(row);
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
     * The product picture of every part that ever came in with a screenshot of a shop order.
     *
     * <p>Used wherever a page lists parts and the shop's own photo tells them apart (the material ledger,
     * the stock summary, the shortage list, a BOM): a part has no picture of its own, the newest receipt
     * line that carries one speaks for it. One query for the whole page, never one per row.
     *
     * @param delegator the delegator
     * @return componentCode to picture id, empty when no line has one
     * @throws GenericEntityException when the lines cannot be read
     */
    public static Map<String, String> shotByComponent(Delegator delegator) throws GenericEntityException {
        Map<String, String> shots = new java.util.HashMap<>();
        for (GenericValue line : EntityQuery.use(delegator).from("MfgPurchaseReceiptLine")
                .select("componentCode", "scanShotId", "createdStamp")
                .orderBy("createdStamp", "lineSeq").queryList()) {
            String shot = line.getString("scanShotId");
            if (shot != null && !shot.isEmpty()) {
                // the newest line wins: it overwrites what an older purchase of the same part filed
                shots.put(pictureKey(line.getString("componentCode")), shot);
            }
        }
        dropMissingPictures(delegator, shots);
        // a parcel that was taken apart (one bag of 130 kinds of resistors) was photographed once, as
        // the bag: every kind that came out of it shows that same picture, because it is the only one
        // there is (Step 179r)
        inheritFromTheParcel(delegator, shots);
        return shots;
    }

    /**
     * Lets the parts that were split out of another lot show the picture of that lot.
     *
     * <p>{@code mfgLotSplit} files the children under {@code splitFromLotId}; their own component code
     * was never bought on its own, so it has no receipt line and no picture. The photograph of the
     * parcel they came in is the honest answer - the warehouse person recognises the bag, and a part
     * without any picture is the one thing a split parcel can never provide.
     *
     * @param delegator the delegator
     * @param shots componentCode to picture id, filled in place; an own picture is never overwritten
     * @throws GenericEntityException when the lots cannot be read
     */
    private static void inheritFromTheParcel(Delegator delegator, Map<String, String> shots)
            throws GenericEntityException {
        Map<String, String> lotComponent = new java.util.HashMap<>();
        Map<String, String> lotParent = new java.util.HashMap<>();
        for (GenericValue lot : EntityQuery.use(delegator).from("MfgMaterialLot")
                .select("lotId", "componentCode", "splitFromLotId").queryList()) {
            String lotId = lot.getString("lotId");
            lotComponent.put(lotId, lot.getString("componentCode"));
            String parent = lot.getString("splitFromLotId");
            if (parent != null && !parent.isEmpty()) {
                lotParent.put(lotId, parent);
            }
        }
        // the map this fills is keyed by part number, so the chain has to be read in part numbers as
        // well: the child lot and the parcel it came out of are two different parts. Both sides go
        // through pictureKey, or a parcel booked as "NFC 模块" and its child typed as "nfc 模块" would
        // look like two unrelated parts and the child would show nothing (Step 204)
        Map<String, String> componentParent = new java.util.HashMap<>();
        for (Map.Entry<String, String> entry : lotParent.entrySet()) {
            String child = lotComponent.get(entry.getKey());
            String parent = lotComponent.get(entry.getValue());
            if (child != null && parent != null) {
                componentParent.put(pictureKey(child), pictureKey(parent));
            }
        }
        inheritFromParents(shots, componentParent);
    }

    /**
     * The part number in the form the picture map is keyed by: no blanks at either end, one case.
     *
     * <p>Part numbers are matched ignoring case everywhere else (the BOM's stock lookup groups by
     * {@code upperCode}, {@code lotsOfComponent} compares with {@code equalsIgnoreCase}), so a picture
     * has to be found the same way. A BOM row typed as {@code NFC COIL} and the batch it came out of
     * booked as {@code NFC Coil} are the same part, and the photo of the batch belongs on the row
     * (Step 204: the row showed no picture on production while the test copy of it did, and only the
     * case of the two numbers differed).
     *
     * @param componentCode the part number as printed anywhere
     * @return the key to look its picture up with, empty when there is no part number
     */
    public static String pictureKey(String componentCode) {
        return componentCode == null ? "" : componentCode.trim().toUpperCase(java.util.Locale.ROOT);
    }

    /**
     * The picture of a part, from the map {@code shotByComponent} returned.
     *
     * <p>Every caller goes through this instead of {@code map.get(code)}: the map is keyed by
     * {@link #pictureKey} and a plain get would silently miss a part whose number is typed in another
     * case, which is exactly the bug this rule exists for.
     *
     * @param shots the map from {@code shotByComponent}, may be null or empty
     * @param componentCode the part number of the row
     * @return the picture id, null when the part has none
     */
    public static String pictureOf(Map<String, String> shots, String componentCode) {
        if (shots == null || shots.isEmpty() || componentCode == null) {
            return null;
        }
        return shots.get(pictureKey(componentCode));
    }

    /**
     * Copies a picture down a chain of parent and child keys, without ever overwriting one of its own.
     *
     * <p>Split in two from the database on purpose: the rule is worth a unit test, and the walk has to
     * stop on a chain that points at itself.
     *
     * @param shots key to picture id, filled in place
     * @param parentOf key to the key it was split out of
     */
    static void inheritFromParents(Map<String, String> shots, Map<String, String> parentOf) {
        if (shots == null || parentOf == null || parentOf.isEmpty()) {
            return;
        }
        // a child of a child is possible, so the walk repeats until nothing changes (it never does
        // more than a handful of times: a parcel is taken apart once)
        for (int round = 0; round < 5; round++) {
            boolean changed = false;
            for (Map.Entry<String, String> entry : parentOf.entrySet()) {
                if (shots.containsKey(entry.getKey())) {
                    continue;
                }
                String shot = shots.get(entry.getValue());
                if (shot != null) {
                    shots.put(entry.getKey(), shot);
                    changed = true;
                }
            }
            if (!changed) {
                break;
            }
        }
    }

    /**
     * Drops the picture ids whose attachment is no longer there.
     *
     * <p>A receipt line only stores a pointer, so a pointer can outlive its picture (the file was
     * deleted, or a test import made one up). Left in place it greys out one table cell with a broken
     * image icon on every page that lists the part, which looks like the whole feature is broken.
     *
     * @param delegator the delegator
     * @param shots the picture ids to check, cleaned in place
     * @throws GenericEntityException when the attachments cannot be read
     */
    private static void dropMissingPictures(Delegator delegator, Map<String, String> shots)
            throws GenericEntityException {
        if (shots.isEmpty()) {
            return;
        }
        List<Object> ids = new ArrayList<>(new java.util.HashSet<>(shots.values()));
        java.util.Set<String> stored = new java.util.HashSet<>();
        for (GenericValue attachment : EntityQuery.use(delegator).from("MfgAttachment").select("ownerId")
                .where(org.apache.ofbiz.entity.condition.EntityCondition.makeCondition("ownerTypeId",
                                MfgConstants.ATT_ORDER_SCAN),
                        org.apache.ofbiz.entity.condition.EntityCondition.makeCondition("ownerId",
                                org.apache.ofbiz.entity.condition.EntityOperator.IN, ids))
                .queryList()) {
            stored.add(attachment.getString("ownerId"));
        }
        shots.values().removeIf(id -> !stored.contains(id));
    }

    /**
     * The product picture of every lot that came in with a screenshot of a shop order.
     *
     * <p>The pointer lives on the receipt line ({@code scanShotId}), so a lot has no picture of its own:
     * looking them up for the whole page at once keeps the material page at one extra query.
     *
     * @param delegator the delegator
     * @return lotId to picture id, empty when no lot has one
     * @throws GenericEntityException when the lines cannot be read
     */
    public static Map<String, String> shotOfLot(Delegator delegator) throws GenericEntityException {
        Map<String, String> shots = new java.util.HashMap<>();
        for (GenericValue line : EntityQuery.use(delegator).from("MfgPurchaseReceiptLine")
                .select("lotId", "scanShotId").orderBy("lineSeq").queryList()) {
            String shot = line.getString("scanShotId");
            if (shot != null && !shot.isEmpty()) {
                shots.put(line.getString("lotId"), shot);
            }
        }
        dropMissingPictures(delegator, shots);
        // the children of a parcel that was taken apart show the picture of the parcel: they were
        // never on a receipt of their own, so nothing else can be shown for them (Step 179r)
        Map<String, String> parents = new java.util.HashMap<>();
        for (GenericValue lot : EntityQuery.use(delegator).from("MfgMaterialLot")
                .select("lotId", "splitFromLotId").queryList()) {
            String parent = lot.getString("splitFromLotId");
            if (parent != null && !parent.isEmpty()) {
                parents.put(lot.getString("lotId"), parent);
            }
        }
        inheritFromParents(shots, parents);
        return shots;
    }

    /**
     * Comes back to the one row that was just changed, instead of to the page the reader came from.
     *
     * <p>Why: the ledger is sorted by creation time and paged, so a row edited on page three - or on
     * page one of a list that keeps growing with today's arrivals - is often not on the page he is
     * returned to. What he then sees is the success line and no row at all, which reads as "the batch
     * I changed has disappeared" (reported on a real screen after a recount).
     *
     * <p>The batch number always matches exactly one row, so narrowing to it can never come back
     * empty; the part filter is dropped on purpose, because a search word and a part number combined
     * are what emptied pages in the past. The search box above the table is one click from the whole
     * ledger again.
     *
     * @param result the service result that becomes the redirect parameters
     * @param lotId the batch that was just changed
     */
    static void narrowToTheLot(Map<String, Object> result, String lotId) {
        if (result == null || lotId == null) {
            return;
        }
        result.put("search", lotId);
        result.remove("componentCode");
        result.put("page", "1");
    }

    /**
     * Carries the filter and the page of the material list back into the redirect of a row action, so
     * saving one row does not throw the reader onto page one of the whole ledger.
     *
     * <p>The page posts its own state as {@code back…} fields, so this works for a filtered list and
     * a page size of 50 alike. A value that was not posted is left out, so the redirect does not carry
     * empty parameters. {@link #narrowToTheLot} then overrides the search with the changed batch.
     *
     * @param result the service result that becomes the redirect parameters
     * @param context the service context, holding the {@code back…} fields
     */
    private static void keepView(Map<String, Object> result, Map<String, ? extends Object> context) {
        String search = MfgSupport.toText(context.get("backSearch"));
        if (search != null) {
            result.put("search", search);
        }
        String page = MfgSupport.toText(context.get("backPage"));
        if (page != null) {
            result.put("page", page);
        }
        String pageSize = MfgSupport.toText(context.get("backPageSize"));
        if (pageSize != null) {
            result.put("pageSize", pageSize);
        }
        String componentCode = MfgSupport.toText(context.get("backComponentCode"));
        if (componentCode != null) {
            result.put("componentCode", componentCode);
        }
    }

    /**
     * Whether the page posted the view it was showing.
     *
     * <p>A row action restores <b>only</b> what the page posted. Filling in a filter the reader never
     * typed changes what he sees: the search word and the part filter are combined, so a part filter
     * invented by the action can turn the list empty ("searched NFC, edited a row, and the ledger
     * suddenly says 0 rows"). Only when the page posted nothing - an old link, a script posting the
     * bare service inputs - does the action narrow the list to the part it changed.
     *
     * @param context the service context
     * @return {@code true} when any of the {@code back…} fields was posted
     */
    private static boolean postedAView(Map<String, ? extends Object> context) {
        return MfgSupport.toText(context.get("backSearch")) != null
                || MfgSupport.toText(context.get("backPage")) != null
                || MfgSupport.toText(context.get("backPageSize")) != null
                || MfgSupport.toText(context.get("backComponentCode")) != null;
    }

    /**
     * Maps the incoming inspection status, defaulting an empty value to {@code PENDING}.
     *
     * @param value the raw input
     * @return one of {@link MfgConstants#INSPECT_STATUSES}, or {@code null} when the value is not a known status
     */
    static String normalizeInspectStatus(Object value) {
        String text = MfgSupport.toText(value);
        if (text == null) {
            return MfgConstants.IQC_PENDING;
        }
        String upper = text.toUpperCase(Locale.ROOT);
        for (String status : MfgConstants.INSPECT_STATUSES) {
            if (status.equals(upper)) {
                return status;
            }
        }
        return null;
    }

    /**
     * Imports material lots from CSV text. Each line is
     * {@code componentCode,componentName,supplierName,supplierLot,qty,receivedDate}.
     *
     * @param dctx the dispatch context
     * @param context service context, see {@code servicedef/stock-services.xml#mfgLotImportCsv}
     * @return the service result containing {@code importedCount}
     */
    public static Map<String, Object> lotImportCsv(DispatchContext dctx, Map<String, ? extends Object> context) {
        String denied = MfgSecurity.permissionProblem(dctx, context, MfgConstants.PERM_STOCK);
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
                String lotId = MfgStockServices.nextLotId(delegator, LocalDate.now());
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
                MfgStockLedger.recordMovement(delegator, lot, MfgConstants.MOVE_RECEIPT, qty, null, null, null, row[3], null,
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
     * Tells whether a type is one of the stock location types.
     *
     * @param locationTypeId the type to test
     * @return {@code true} when it is known
     */
    static boolean isKnownLocationType(String locationTypeId) {
        for (String type : MfgConstants.LOC_TYPES) {
            if (type.equals(locationTypeId)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Turns a partly typed endpoint into the URL the API expects: many people type only the host or
     * stop at the version, and the API answers those with 404.
     *
     * @param endpoint what the settings hold
     * @return the chat completions URL, {@code null} for a null input
     */
    public static Map<String, Object> locationSave(DispatchContext dctx, Map<String, ? extends Object> context) {
        String denied = MfgSecurity.permissionProblem(dctx, context, MfgConstants.PERM_STOCK);
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
            locationTypeId = MfgConstants.LOC_BIN;
        }
        if (!isKnownLocationType(locationTypeId)) {
            return ServiceUtil.returnError("Unknown location type [" + locationTypeId + "], expected "
                    + String.join(" / ", MfgConstants.LOC_TYPES));
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
     * @param context service context, see {@code servicedef/stock-services.xml#mfgLocationList}
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
     * @param context service context, see {@code servicedef/stock-services.xml#mfgLocationStock}
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
                row.put("lotKindId", lot == null ? MfgConstants.LOT_COMPONENT : MfgStockLedger.lotKindOf(lot));
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
     * @param context service context, see {@code servicedef/stock-services.xml#mfgStockTransfer}
     * @return the service result with the transfer number
     */
    public static Map<String, Object> stockTransfer(DispatchContext dctx, Map<String, ? extends Object> context) {
        String denied = MfgSecurity.permissionProblem(dctx, context, MfgConstants.PERM_STOCK);
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
            MfgStockLedger.recordMovement(delegator, lot, MfgConstants.MOVE_TRANSFER, -qty, lot.get("unitPrice"), null, null,
                    transferId, operator, null,
                    "移库 " + transferId + "：" + (fromLocationId == null ? "未分配" : fromLocationId)
                            + " → " + toLocationId + (note == null ? "" : "，" + note), fromLocationId);
            MfgStockLedger.recordMovement(delegator, lot, MfgConstants.MOVE_TRANSFER, qty, lot.get("unitPrice"), null, null,
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
            result.put("toLocationQty",
                    Double.valueOf(MfgSupport.round2(MfgStockLedger.locationOnHand(delegator, lotId, toLocationId))));
            result.put("onHandQty", lot.get("onHandQty"));
            return result;
        } catch (GenericEntityException e) {
            return ServiceUtil.returnError("Could not move the stock: " + e.getMessage());
        }
    }

    static String nextTransferId(Delegator delegator, LocalDate today) throws GenericEntityException {
        String prefix = MfgConstants.TRANSFER_PREFIX + today.format(MfgConstants.DAY) + "-";
        int max = 0;
        for (GenericValue movement : EntityQuery.use(delegator).from("MfgInventoryDetail")
                .where("movementTypeId", MfgConstants.MOVE_TRANSFER).queryList()) {
            String reference = movement.getString("referenceNo");
            if (reference != null && reference.startsWith(prefix)) {
                max = Math.max(max, MfgSupport.parseTrailingNumber(reference.substring(prefix.length())));
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
     * @param context service context, see {@code servicedef/stock-services.xml#mfgInventoryMove}
     * @return the service result containing the new totals of the lot
     */
    public static Map<String, Object> inventoryMove(DispatchContext dctx, Map<String, ? extends Object> context) {
        String denied = MfgSecurity.permissionProblem(dctx, context, MfgConstants.PERM_STOCK);
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
                    + "], expected " + String.join(" / ", MfgConstants.MOVE_TYPES));
        }
        if (qty == 0.0) {
            return ServiceUtil.returnError("qty must not be zero");
        }
        if (MfgConstants.MOVE_COUNT.equals(movementTypeId)) {
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
                    && (MfgConstants.MOVE_ISSUE.equals(movementTypeId) || MfgConstants.MOVE_RETURN.equals(movementTypeId))) {
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
                    && (MfgConstants.MOVE_ISSUE.equals(movementTypeId) || MfgConstants.MOVE_RETURN.equals(movementTypeId))) {
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
            if (MfgConstants.MOVE_ISSUE.equals(movementTypeId) && workOrderId != null) {
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
     * @param context service context, see {@code servicedef/stock-services.xml#mfgStockSummary}
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
            Map<String, String> shots = shotByComponent(delegator);
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
                    // the picture the shop printed for this part, if it ever came in from a screenshot
                    row.put("scanShotId", pictureOf(shots, componentCode));
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
                if (MfgConstants.IQC_PENDING.equals(inspect)) {
                    row.put("pendingQty", Double.valueOf(MfgSupport.numberOrZero(row.get("pendingQty")) + onHand));
                }
                if (MfgConstants.IQC_FAIL.equals(inspect)) {
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
     * @param context service context, see {@code servicedef/stock-services.xml#mfgInventoryLedger}
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
            Map<String, String> shots = shotByComponent(delegator);
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
                row.put("lotKindId", lot == null ? MfgConstants.LOT_COMPONENT : MfgStockLedger.lotKindOf(lot));
                row.put("locationId", movement.getString("locationId"));
                row.put("componentCode", lot == null ? null : lot.getString("componentCode"));
                row.put("componentName", lot == null ? null : lot.getString("componentName"));
                row.put("scanShotId", lot == null ? null : pictureOf(shots, lot.getString("componentCode")));
                row.put("movementTypeId", movement.getString("movementTypeId"));
                row.put("quantityDiff", Double.valueOf(MfgSupport.round2(diff)));
                row.put("reservedDiff", Double.valueOf(MfgSupport.round2(MfgSupport.numberOrZero(movement.get("reservedDiff")))));
                row.put("displayQty", Double.valueOf(MfgSupport.round2(
                        MfgConstants.MOVE_RESERVE.equals(movement.getString("movementTypeId"))
                                || MfgConstants.MOVE_RELEASE.equals(movement.getString("movementTypeId"))
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
     * @param context service context, see {@code servicedef/stock-services.xml#mfgStockSettingSave}
     * @return the service result containing {@code componentCode}
     */
    public static Map<String, Object> stockSettingSave(DispatchContext dctx, Map<String, ? extends Object> context) {
        String denied = MfgSecurity.permissionProblem(dctx, context, MfgConstants.PERM_STOCK);
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
                setting.set("componentName", MfgSupport.toText(context.get("partName")));
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
     * @param context service context, see {@code servicedef/stock-services.xml#mfgStockCount}
     * @return the service result containing the count number and the variance
     */
    public static Map<String, Object> stockCount(DispatchContext dctx, Map<String, ? extends Object> context) {
        String denied = MfgSecurity.permissionProblem(dctx, context, MfgConstants.PERM_STOCK);
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
            String reasonProblem = MfgStockLedger.movementReasonProblem(MfgConstants.MOVE_COUNT, reasonEnumId, variance);
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
                MfgStockLedger.recordMovement(delegator, lot, MfgConstants.MOVE_COUNT, variance, lot.get("unitPrice"), null,
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
     * @param context service context, see {@code servicedef/stock-services.xml#mfgStockCountList}
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
                map.put("lotKindId", lot == null ? MfgConstants.LOT_COMPONENT : MfgStockLedger.lotKindOf(lot));
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
    static String nextStockCountId(Delegator delegator, LocalDate today)
            throws GenericEntityException {
        String prefix = "CNT-" + today.format(MfgConstants.DAY) + "-";
        int max = 0;
        for (GenericValue count : EntityQuery.use(delegator).from("MfgStockCount").queryList()) {
            String id = count.getString("physicalInventoryId");
            if (id != null && id.startsWith(prefix)) {
                max = Math.max(max, MfgSupport.parseTrailingNumber(id.substring(prefix.length())));
            }
        }
        return prefix + String.format(Locale.ROOT, "%02d", Integer.valueOf(max + 1));
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
    static String createStockInLot(Delegator delegator, GenericValue project, GenericValue step)
            throws GenericEntityException {
        if (!MfgConstants.TRACK_ENCLOSURE.equals(project.getString("trackId"))
                || !MfgConstants.STEP_ENCLOSURE_STOCK_IN.equals(step.getString("stepId"))
                || !MfgConstants.STEP_DONE.equals(step.getString("statusId"))
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
        String lotId = MfgStockServices.nextLotId(delegator, LocalDate.now());
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
        MfgStockLedger.recordMovement(delegator, lot, MfgConstants.MOVE_RECEIPT, qty.doubleValue(), null, null, null,
                step.getString("referenceNo"), null,
                Timestamp.valueOf(LocalDate.now().atStartOfDay()), "外壳入库");
        MfgStockLedger.recalcLotStock(delegator, lot);
        step.set("lotId", lotId);
        return null;
    }

    private static String formatLotId(LocalDate date, int sequence) {
        return "LOT-" + date.format(MfgConstants.DAY) + "-" + String.format(Locale.ROOT, "%02d", sequence);
    }

    static String nextLotId(Delegator delegator, LocalDate today) throws GenericEntityException {
        String prefix = "LOT-" + today.format(MfgConstants.DAY) + "-";
        List<GenericValue> existing = EntityQuery.use(delegator).from("MfgMaterialLot").queryList();
        int max = 0;
        for (GenericValue lot : existing) {
            String id = lot.getString("lotId");
            if (id != null && id.startsWith(prefix)) {
                max = Math.max(max, MfgSupport.parseTrailingNumber(id.substring(prefix.length())));
            }
        }
        return formatLotId(today, max + 1);
    }
}
