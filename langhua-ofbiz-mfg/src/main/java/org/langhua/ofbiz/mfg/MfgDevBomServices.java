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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.apache.ofbiz.base.util.UtilGenerics;
import org.apache.ofbiz.entity.Delegator;
import org.apache.ofbiz.entity.GenericEntityException;
import org.apache.ofbiz.entity.GenericValue;
import org.apache.ofbiz.entity.util.EntityQuery;
import org.apache.ofbiz.service.DispatchContext;
import org.apache.ofbiz.service.ServiceUtil;

/**
 * The design bill of materials of a development project: importing the HTML report Fritzing writes
 * next to a breadboard sketch, comparing it with the version before it, locking the parts a version
 * needs and listing what has to be bought.
 *
 * <p>Why it exists: a board is born on a breadboard. The sketch prints which parts it uses, so the
 * import turns that into three things the shop otherwise works out by hand - how much of every part
 * the version needs, what of it is already on the shelf (and therefore locked for the project), and
 * what has to be bought. Because the sketch changes every evening, every import is kept as a
 * snapshot and compared with the one before it: a part that appears is new and goes on the shopping
 * list, a part whose quantity grew is topped up, and a part the version no longer uses is written off
 * as development material instead of being put back on the shelf (it was soldered, stripped or lost
 * while breadboarding - see {@link MfgStockLedger#consumeForProject}).
 *
 * <p>The parts of one version live in {@code MfgDevBom} (one row per import) and
 * {@code MfgDevBomItem} (one row per part of that import, with what the version means for it). The
 * lock is a reservation of the inventory ledger, exactly like the one a work order takes, only with
 * {@code devProjectCode} instead of {@code workOrderId}.
 */
public final class MfgDevBomServices {

    /** How many in-stock part numbers the page offers while a BOM row's code is corrected. */
    static final int MAX_STOCK_OPTIONS = 300;

    /**
     * The columns of an import that the pages and the row saves read. The file bytes are not among
     * them: {@code sourceData} is only read when somebody downloads the file, and pulling it on every
     * page view would make the whole import table heavy.
     */
    static final String[] HEAD_FIELDS = {"projectCode", "importSeq", "bomVersion", "sourceFile",
        "importedStamp", "itemCount", "newCount", "changedCount", "droppedCount", "shortCount",
        "lockedQty", "sourceSize", "sourceContentType", "note"};

    private MfgDevBomServices() {
    }

    /**
     * Imports one version of the design BOM of a development project.
     *
     * <p>The text is parsed by {@link MfgCsv#parseBom}, so the Fritzing HTML report, the Fritzing CSV
     * export and the CSV of this component all work, and the format is detected when it is not given.
     * The rows are compared with the parts the previous import of the same project used, every part
     * of this version is locked on the shelf as far as there is stock
     * ({@link MfgStockLedger#reserveForProject}), and the parts the previous version had are written
     * off as development material when {@code consumeDropped} is not {@code N}.
     *
     * @param dctx the dispatch context
     * @param context service context, see {@code servicedef/devbom-services.xml#mfgDevBomImport}
     * @return the service result with the counts of the import and the shortage it found
     */
    public static Map<String, Object> devBomImport(DispatchContext dctx, Map<String, ? extends Object> context) {
        String denied = MfgSecurity.permissionProblem(dctx, context, MfgConstants.PERM_ENG);
        if (denied != null) {
            return ServiceUtil.returnError(denied);
        }
        Delegator delegator = dctx.getDelegator();
        String projectCode = MfgSupport.toText(context.get("projectCode"));
        String text = MfgSupport.toText(context.get("bomText"));
        if (projectCode == null) {
            return ServiceUtil.returnError("projectCode is required");
        }
        if (text == null) {
            return ServiceUtil.returnError("请粘贴 Fritzing 导出的 BOM 内容，或选择一个 bom.html / CSV 文件");
        }
        String requestedFormat = MfgSupport.toText(context.get("format"));
        MfgCsv.BomFormat forced = MfgCsv.BomFormat.of(requestedFormat);
        if (requestedFormat != null && forced == null) {
            return ServiceUtil.returnError("Unknown BOM format [" + requestedFormat
                    + "], expected MFG, FRITZING or FRITZING_HTML");
        }
        MfgCsv.BomFormat format = forced == null ? MfgCsv.detectBomFormat(text) : forced;
        List<String[]> parsed = MfgCsv.parseBom(text, format);
        if (parsed.isEmpty()) {
            return ServiceUtil.returnError("这个文件里没读到元件：Fritzing 的 bom.html 请用「导出 → 作为图片 → 物料清单」那一份");
        }
        boolean lock = !"N".equals(MfgSupport.toText(context.get("lock")));
        boolean consumeDropped = !"N".equals(MfgSupport.toText(context.get("consumeDropped")));
        String operatorName = MfgSupport.toText(context.get("operatorName"));
        String fileName = MfgSupport.toText(context.get("fileName"));
        try {
            GenericValue project = EntityQuery.use(delegator).from("MfgDevProject")
                    .where("projectCode", projectCode).queryOne();
            if (project == null) {
                return ServiceUtil.returnError("Unknown development project [" + projectCode
                        + "], create it first");
            }
            GenericValue previousImport = lastImport(delegator, projectCode);
            Map<String, Double> previousQty = new LinkedHashMap<>();
            if (previousImport != null) {
                for (GenericValue item : itemsOf(delegator, projectCode,
                        previousImport.getLong("importSeq").longValue())) {
                    if (MfgConstants.BOM_DROPPED.equals(item.getString("changeId"))) {
                        continue;
                    }
                    previousQty.put(item.getString("componentCode"),
                            Double.valueOf(MfgSupport.numberOrZero(item.get("qty"))));
                }
            }
            List<DevBomRow> rows = devBomRows(previousQty, parsed);
            // parts somebody typed in (an adapter board the sketch does not show) are not in the
            // file, so they come along from the version before instead of being dropped
            if (previousImport != null) {
                rows = withManualParts(rows,
                        manualRowsOf(delegator, projectCode, previousImport.getLong("importSeq").longValue()));
            }
            long importSeq = previousImport == null ? 1L
                    : previousImport.getLong("importSeq").longValue() + 1L;
            String version = versionOf(MfgSupport.toText(context.get("bomVersion")), fileName, importSeq);

            Map<String, Double> lockedByComponent = MfgStockLedger.lockedByProjectComponent(delegator, projectCode);
            int newCount = 0;
            int sameCount = 0;
            int changedCount = 0;
            int droppedCount = 0;
            int shortCount = 0;
            int itemCount = 0;
            double lockedTotal = 0.0;
            double shortTotal = 0.0;
            double consumedTotal = 0.0;
            long lineSeq = 10L;
            // the rows carry a foreign key to the head, so the head comes first and the counts are
            // written back once the loop is done
            GenericValue head = delegator.makeValue("MfgDevBom");
            head.set("projectCode", projectCode);
            head.set("importSeq", Long.valueOf(importSeq));
            head.set("bomVersion", version);
            head.set("sourceFile", fileName);
            head.set("importedStamp", new Timestamp(System.currentTimeMillis()));
            // the uploaded file itself is kept with the version: the rows are what the pages work
            // with, but the evidence of "what the sketch said" is the file, and re-importing it later
            // must not depend on somebody still having the export on a laptop
            byte[] source = sourceBytesOf(MfgSupport.toText(context.get("sourceBase64")));
            if (source != null) {
                head.set("sourceData", source);
                head.set("sourceSize", Long.valueOf(source.length));
                head.set("sourceContentType", MfgSupport.toText(context.get("sourceContentType")));
            } else {
                head.set("sourceSize", Long.valueOf(0));
            }
            head.set("itemCount", Long.valueOf(0));
            head.set("newCount", Long.valueOf(0));
            head.set("changedCount", Long.valueOf(0));
            head.set("droppedCount", Long.valueOf(0));
            head.set("shortCount", Long.valueOf(0));
            head.set("lockedQty", Double.valueOf(0.0));
            delegator.create(head);
            for (DevBomRow row : rows) {
                double held = MfgSupport.numberOrZero(lockedByComponent.get(row.componentCode));
                double qty = row.qty;
                double lockedQty = 0.0;
                double shortQty = 0.0;
                double consumedQty = 0.0;
                String note = null;
                if (MfgConstants.BOM_DROPPED.equals(row.changeId)) {
                    droppedCount++;
                    if (held > 0.0001) {
                        if (consumeDropped) {
                            consumedQty = MfgStockLedger.consumeForProject(delegator, projectCode,
                                    row.componentCode, operatorName,
                                    "研发消耗：" + version + " 起不再使用");
                            note = "记作研发消耗 " + MfgSupport.formatQty(consumedQty) + "（不退回库存）";
                        } else {
                            double released = MfgStockLedger.releaseProjectReservation(delegator, projectCode,
                                    row.componentCode, held, operatorName,
                                    "释放锁料：" + version + " 起不再使用");
                            note = "只释放了锁料 " + MfgSupport.formatQty(released) + "，库存不动";
                        }
                        lockedByComponent.put(row.componentCode, Double.valueOf(0.0));
                        consumedTotal += consumedQty;
                    } else {
                        note = "没有锁到料，无需处理";
                    }
                } else {
                    itemCount++;
                    if (MfgConstants.BOM_NEW.equals(row.changeId)) {
                        newCount++;
                    } else if (MfgConstants.BOM_CHANGED.equals(row.changeId)) {
                        changedCount++;
                    } else {
                        sameCount++;
                    }
                    if (lock) {
                        double gap = qty - held;
                        if (gap > 0.0001) {
                            held += MfgStockLedger.reserveForProject(delegator, projectCode,
                                    row.componentCode, gap, operatorName, "锁料：" + version);
                            lockedByComponent.put(row.componentCode, Double.valueOf(held));
                        }
                    }
                    lockedQty = Math.min(held, qty);
                    shortQty = Math.max(0.0, qty - held);
                    if (shortQty > 0.0001) {
                        shortCount++;
                        shortTotal += shortQty;
                    }
                    lockedTotal += lockedQty;
                }
                GenericValue item = delegator.makeValue("MfgDevBomItem");
                item.set("projectCode", projectCode);
                item.set("importSeq", Long.valueOf(importSeq));
                item.set("lineSeq", Long.valueOf(lineSeq));
                item.set("componentCode", row.componentCode);
                item.set("componentName", row.componentName);
                item.set("partType", row.partType);
                item.set("properties", row.properties);
                item.set("designators", row.designators);
                item.set("qty", Double.valueOf(MfgSupport.round2(qty)));
                item.set("previousQty", row.previousQty == null ? null
                        : Double.valueOf(MfgSupport.round2(row.previousQty.doubleValue())));
                item.set("changeId", row.changeId);
                item.set("sourceId", row.sourceId);
                item.set("lockedQty", Double.valueOf(MfgSupport.round2(lockedQty)));
                item.set("shortQty", Double.valueOf(MfgSupport.round2(shortQty)));
                item.set("consumedQty", Double.valueOf(MfgSupport.round2(consumedQty)));
                item.set("note", note);
                delegator.create(item);
                lineSeq += 10L;
            }

            head.set("itemCount", Long.valueOf(itemCount));
            head.set("newCount", Long.valueOf(newCount));
            head.set("changedCount", Long.valueOf(changedCount));
            head.set("droppedCount", Long.valueOf(droppedCount));
            head.set("shortCount", Long.valueOf(shortCount));
            head.set("lockedQty", Double.valueOf(MfgSupport.round2(lockedTotal)));
            head.set("note", "导入格式 " + format.label() + "，与上一版相比：新增 " + newCount + " / 数量变化 "
                    + changedCount + " / 不变 " + sameCount + " / 不再使用 " + droppedCount);
            delegator.store(head);

            logImport(logMessage(projectCode, version, importSeq, itemCount, newCount, changedCount,
                    droppedCount, shortCount, lockedTotal, consumedTotal));
            Map<String, Object> result = ServiceUtil.returnSuccess();
            result.put("projectCode", projectCode);
            result.put("importSeq", Long.valueOf(importSeq));
            result.put("bomVersion", version);
            result.put("bomFormat", format.name());
            // strings, so that a redirect can carry them
            result.put("itemCount", String.valueOf(itemCount));
            result.put("newCount", String.valueOf(newCount));
            result.put("changedCount", String.valueOf(changedCount));
            result.put("sameCount", String.valueOf(sameCount));
            result.put("droppedCount", String.valueOf(droppedCount));
            result.put("shortCount", String.valueOf(shortCount));
            result.put("shortQty", MfgSupport.formatQty(shortTotal));
            result.put("lockedQty", MfgSupport.formatQty(lockedTotal));
            result.put("consumedQty", MfgSupport.formatQty(consumedTotal));
            return result;
        } catch (GenericEntityException e) {
            return ServiceUtil.returnError("导入研发 BOM 失败：" + e.getMessage());
        }
    }

    /**
     * Reads the current design BOM of a development project: the parts of the latest import with the
     * stock numbers of today, the parts it no longer uses, the shopping list (every part that is
     * short) and the history of imports.
     *
     * <p>The stock numbers are recomputed on every call instead of being taken from the import, so a
     * part that was bought in the meantime drops off the shopping list by itself.
     *
     * @param dctx the dispatch context
     * @param context service context, see {@code servicedef/devbom-services.xml#mfgDevBomDetail}
     * @return the service result, empty lists when the project imported nothing yet
     */
    public static Map<String, Object> devBomDetail(DispatchContext dctx, Map<String, ? extends Object> context) {
        Delegator delegator = dctx.getDelegator();
        String projectCode = MfgSupport.toText(context.get("projectCode"));
        Map<String, Object> result = ServiceUtil.returnSuccess();
        List<Map<String, Object>> items = new ArrayList<>();
        List<Map<String, Object>> dropped = new ArrayList<>();
        List<Map<String, Object>> shortages = new ArrayList<>();
        List<Map<String, Object>> imports = new ArrayList<>();
        result.put("items", items);
        result.put("dropped", dropped);
        result.put("shortages", shortages);
        result.put("imports", imports);
        result.put("stockOptions", new ArrayList<Map<String, Object>>());
        if (projectCode == null) {
            return result;
        }
        try {
            Map<String, double[]> stock = componentStock(delegator);
            Map<String, String> shots = MfgStockServices.shotByComponent(delegator);
            Map<String, Double> lockedByComponent = MfgStockLedger.lockedByProjectComponent(delegator, projectCode);
            GenericValue latest = lastImport(delegator, projectCode);
            List<GenericValue> versionItems = latest == null ? new ArrayList<GenericValue>()
                    : itemsOf(delegator, projectCode, latest.getLong("importSeq").longValue());
            List<String> wantedCodes = new ArrayList<>();
            for (GenericValue item : versionItems) {
                wantedCodes.add(item.getString("componentCode"));
            }
            result.put("stockOptions", componentStockOptions(delegator, wantedCodes));
            double shortTotal = 0.0;
            int shortCount = 0;
            if (latest != null) {
                long importSeq = latest.getLong("importSeq").longValue();
                for (GenericValue item : versionItems) {
                    String code = item.getString("componentCode");
                    double qty = MfgSupport.numberOrZero(item.get("qty"));
                    double locked = MfgSupport.numberOrZero(lockedByComponent.get(code));
                    double[] sums = stock.get(code);
                    double onHand = sums == null ? 0.0 : sums[0];
                    double available = sums == null ? 0.0 : sums[2];
                    double shortQty = Math.max(0.0, qty - locked);
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("lineSeq", item.get("lineSeq"));
                    row.put("componentCode", code);
                    row.put("componentName", item.getString("componentName"));
                    // the photo the shop printed for this part, when it came in from a screenshot
                    row.put("scanShotId", shots.get(code));
                    row.put("partType", item.getString("partType"));
                    row.put("properties", item.getString("properties"));
                    row.put("designators", item.getString("designators"));
                    row.put("qty", Double.valueOf(MfgSupport.round2(qty)));
                    row.put("changeId", item.getString("changeId"));
                    row.put("sourceId", item.getString("sourceId"));
                    row.put("previousQty", item.get("previousQty"));
                    row.put("lockedQty", Double.valueOf(MfgSupport.round2(locked)));
                    row.put("onHandQty", Double.valueOf(MfgSupport.round2(onHand)));
                    row.put("availableQty", Double.valueOf(MfgSupport.round2(available)));
                    row.put("shortQty", Double.valueOf(MfgSupport.round2(shortQty)));
                    row.put("suggestQty", Double.valueOf(Math.ceil(shortQty - 0.0001)));
                    row.put("note", item.getString("note"));
                    if (MfgConstants.BOM_DROPPED.equals(item.getString("changeId"))) {
                        row.put("consumedQty", item.get("consumedQty"));
                        dropped.add(row);
                    } else {
                        if (shortQty > 0.0001) {
                            shortCount++;
                            shortTotal += shortQty;
                            shortages.add(row);
                        }
                        items.add(row);
                    }
                }
                Map<String, Object> head = new LinkedHashMap<>();
                head.put("importSeq", latest.get("importSeq"));
                head.put("bomVersion", latest.getString("bomVersion"));
                head.put("sourceFile", latest.getString("sourceFile"));
                head.put("importedStamp", latest.get("importedStamp"));
                head.put("itemCount", latest.get("itemCount"));
                head.put("newCount", latest.get("newCount"));
                head.put("changedCount", latest.get("changedCount"));
                head.put("droppedCount", latest.get("droppedCount"));
                head.put("shortCount", Long.valueOf(shortCount));
                head.put("note", latest.getString("note"));
                result.put("latest", head);
            }
            for (GenericValue head : EntityQuery.use(delegator).from("MfgDevBom")
                    .select(HEAD_FIELDS)
                    .where("projectCode", projectCode).orderBy("-importSeq").queryList()) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("importSeq", head.get("importSeq"));
                row.put("bomVersion", head.getString("bomVersion"));
                row.put("sourceFile", head.getString("sourceFile"));
                row.put("importedStamp", head.get("importedStamp"));
                row.put("itemCount", head.get("itemCount"));
                row.put("newCount", head.get("newCount"));
                row.put("changedCount", head.get("changedCount"));
                row.put("droppedCount", head.get("droppedCount"));
                row.put("shortCount", head.get("shortCount"));
                row.put("lockedQty", head.get("lockedQty"));
                row.put("sourceSize", head.get("sourceSize"));
                row.put("sourceContentType", head.getString("sourceContentType"));
                imports.add(row);
            }
            result.put("shortCount", Integer.valueOf(shortCount));
            result.put("shortQty", Double.valueOf(MfgSupport.round2(shortTotal)));
            return result;
        } catch (GenericEntityException e) {
            return ServiceUtil.returnError("读取研发 BOM 失败：" + e.getMessage());
        }
    }

    /**
     * Corrects one part of the latest import: its part number (the code stock is booked under) and
     * its quantity. Correcting the code moves the lock from the old code to the new one, because the
     * sketch names a part differently from the shop that sells it.
     *
     * @param dctx the dispatch context
     * @param context service context, see {@code servicedef/devbom-services.xml#mfgDevBomItemSave}
     * @return the service result with {@code componentCode}, {@code lockedQty} and {@code shortQty}
     */
    public static Map<String, Object> devBomItemSave(DispatchContext dctx, Map<String, ? extends Object> context) {
        String denied = MfgSecurity.permissionProblem(dctx, context, MfgConstants.PERM_ENG);
        if (denied != null) {
            return ServiceUtil.returnError(denied);
        }
        Delegator delegator = dctx.getDelegator();
        String projectCode = MfgSupport.toText(context.get("projectCode"));
        long lineSeq = MfgSupport.toLong(context.get("lineSeq"), 0L);
        if (projectCode == null || lineSeq <= 0L) {
            return ServiceUtil.returnError("projectCode 和 lineSeq 都是必填的");
        }
        String operatorName = MfgSupport.toText(context.get("operatorName"));
        try {
            GenericValue item = itemOf(delegator, projectCode, lineSeq);
            if (item == null) {
                return ServiceUtil.returnError("第 " + lineSeq + " 行不存在（可能已经被下一次导入替换）");
            }
            Map<String, Object> result = applyItemEdit(delegator, projectCode, operatorName, item,
                    MfgSupport.toText(context.get("componentCode")),
                    MfgSupport.toDouble(context.get("qty"), MfgSupport.numberOrZero(item.get("qty"))),
                    !"N".equals(MfgSupport.toText(context.get("lock"))), "改正第 " + lineSeq + " 行");
            result.put("lineSeq", String.valueOf(lineSeq));
            return result;
        } catch (GenericEntityException e) {
            return ServiceUtil.returnError("改正研发 BOM 行失败：" + e.getMessage());
        }
    }

    /**
     * Adds a part to the version of the design BOM that is in use.
     *
     * <p>Why it exists: a breadboard build needs parts the sketch does not show - an adapter board
     * that lets a sensor sit in the breadboard, a battery holder, a jumper set. They are not in the
     * Fritzing export, so there is no file to import them from; without this the person has to keep
     * a list on paper and the shopping list of the version is wrong.
     *
     * <p>The row is marked {@link MfgConstants#BOM_SOURCE_MANUAL}, so the next import carries it
     * along instead of dropping it ({@link #withManualParts}). It is locked and counted like an
     * imported part: as much as the shelf has, the rest shows up in the shopping list.
     *
     * @param dctx the dispatch context
     * @param context service context, see {@code servicedef/devbom-services.xml#mfgDevBomItemAdd}
     * @return the service result with {@code addedCode}, {@code addedLine}, {@code lockedQty} and
     *         {@code shortQty}
     */
    public static Map<String, Object> devBomItemAdd(DispatchContext dctx, Map<String, ? extends Object> context) {
        String denied = MfgSecurity.permissionProblem(dctx, context, MfgConstants.PERM_ENG);
        if (denied != null) {
            return ServiceUtil.returnError(denied);
        }
        Delegator delegator = dctx.getDelegator();
        String projectCode = MfgSupport.toText(context.get("projectCode"));
        if (projectCode == null) {
            return ServiceUtil.returnError("projectCode is required");
        }
        String typedCode = MfgSupport.toText(context.get("partCode"));
        if (typedCode == null) {
            return ServiceUtil.returnError("要填料号：库存认的那个号（转接板就填它的型号）");
        }
        String code = codeOf(typedCode, typedCode);
        if (code.length() > MfgConstants.MAX_COMPONENT_CODE) {
            return ServiceUtil.returnError("料号太长（最多 " + MfgConstants.MAX_COMPONENT_CODE + " 个字符）：" + code);
        }
        double qty = MfgSupport.toDouble(context.get("qty"), 0.0);
        if (qty <= 0.0) {
            return ServiceUtil.returnError("数量要大于 0");
        }
        String typedName = MfgSupport.toText(context.get("partName"));
        String name = typedName == null ? code : typedName;
        String designators = MfgSupport.toText(context.get("designators"));
        boolean lock = !"N".equals(MfgSupport.toText(context.get("lock")));
        String operatorName = MfgSupport.toText(context.get("operatorName"));
        try {
            GenericValue latest = lastImport(delegator, projectCode);
            if (latest == null) {
                return ServiceUtil.returnError("这个项目还没导入过研发 BOM：先导入一版，再往这一版里加元件");
            }
            long importSeq = latest.getLong("importSeq").longValue();
            List<GenericValue> items = itemsOf(delegator, projectCode, importSeq);
            long maxLine = 0L;
            GenericValue same = null;
            for (GenericValue item : items) {
                maxLine = Math.max(maxLine, item.getLong("lineSeq").longValue());
                if (code.equalsIgnoreCase(item.getString("componentCode"))) {
                    same = item;
                }
            }
            if (same != null) {
                return ServiceUtil.returnError("这一版已经有料号 " + code + "（第 "
                        + same.getLong("lineSeq").longValue() + " 行），改它的数量就行，不用再加一行");
            }
            Double before = null;
            if (importSeq > 1L) {
                for (GenericValue old : itemsOf(delegator, projectCode, importSeq - 1L)) {
                    if (code.equalsIgnoreCase(old.getString("componentCode"))) {
                        before = Double.valueOf(MfgSupport.numberOrZero(old.get("qty")));
                    }
                }
            }
            String changeId = before == null ? MfgConstants.BOM_NEW
                    : (Math.abs(before.doubleValue() - qty) > 0.0001 ? MfgConstants.BOM_CHANGED
                            : MfgConstants.BOM_SAME);
            // lock first, so the row says what really stands on the shelf and what has to be bought
            double held = MfgSupport.numberOrZero(
                    MfgStockLedger.lockedByProjectComponent(delegator, projectCode).get(code));
            double gap = qty - held;
            if (lock && gap > 0.0001) {
                held += MfgStockLedger.reserveForProject(delegator, projectCode, code, gap, operatorName,
                        "锁料：手工添加的元件 " + name);
            }
            double shortQty = Math.max(0.0, qty - held);
            long lineSeq = maxLine + 10L;
            GenericValue item = delegator.makeValue("MfgDevBomItem");
            item.set("projectCode", projectCode);
            item.set("importSeq", Long.valueOf(importSeq));
            item.set("lineSeq", Long.valueOf(lineSeq));
            item.set("componentCode", code);
            item.set("componentName", name);
            item.set("designators", designators);
            item.set("qty", Double.valueOf(MfgSupport.round2(qty)));
            item.set("previousQty", before);
            item.set("changeId", changeId);
            item.set("sourceId", MfgConstants.BOM_SOURCE_MANUAL);
            item.set("lockedQty", Double.valueOf(MfgSupport.round2(Math.min(held, qty))));
            item.set("shortQty", Double.valueOf(MfgSupport.round2(shortQty)));
            item.set("consumedQty", Double.valueOf(0.0));
            item.set("note", "手工添加（Fritzing 导出里没有），下次导入会自动带过来");
            delegator.create(item);
            items.add(item);
            updateHeadCounts(delegator, latest, items);
            Map<String, Object> result = ServiceUtil.returnSuccess();
            result.put("addedCode", code);
            result.put("addedName", name);
            result.put("addedLine", String.valueOf(lineSeq));
            result.put("lockedQty", MfgSupport.formatQty(Math.min(held, qty)));
            result.put("shortQty", MfgSupport.formatQty(shortQty));
            return result;
        } catch (GenericEntityException e) {
            return ServiceUtil.returnError("往研发 BOM 里加元件失败：" + e.getMessage());
        }
    }

    /**
     * Rewrites the counters the import head caches, after a row was added by hand. The page reads
     * them for the banner above the table, so they have to match the rows again.
     *
     * @param delegator the delegator
     * @param head the import the row belongs to
     * @param items every row of that import, the new one included
     * @throws GenericEntityException when the head cannot be stored
     */
    private static void updateHeadCounts(Delegator delegator, GenericValue head, List<GenericValue> items)
            throws GenericEntityException {
        int itemCount = 0;
        int newCount = 0;
        int changedCount = 0;
        int shortCount = 0;
        double lockedTotal = 0.0;
        for (GenericValue item : items) {
            if (MfgConstants.BOM_DROPPED.equals(item.getString("changeId"))) {
                continue;
            }
            itemCount++;
            if (MfgConstants.BOM_NEW.equals(item.getString("changeId"))) {
                newCount++;
            } else if (MfgConstants.BOM_CHANGED.equals(item.getString("changeId"))) {
                changedCount++;
            }
            if (MfgSupport.numberOrZero(item.get("shortQty")) > 0.0001) {
                shortCount++;
            }
            lockedTotal += MfgSupport.numberOrZero(item.get("lockedQty"));
        }
        // the counters only: storing the whole row would wipe the file bytes, which are deliberately
        // left out of the read that produced this head
        Map<String, Object> counters = new LinkedHashMap<>();
        counters.put("itemCount", Long.valueOf(itemCount));
        counters.put("newCount", Long.valueOf(newCount));
        counters.put("changedCount", Long.valueOf(changedCount));
        counters.put("shortCount", Long.valueOf(shortCount));
        counters.put("lockedQty", Double.valueOf(MfgSupport.round2(lockedTotal)));
        delegator.storeByCondition("MfgDevBom", counters, importKeyOf(head));
    }

    /** The condition that picks one import of one project. */
    private static org.apache.ofbiz.entity.condition.EntityCondition importKeyOf(GenericValue head) {
        Map<String, Object> keys = new LinkedHashMap<>();
        keys.put("projectCode", head.getString("projectCode"));
        keys.put("importSeq", head.get("importSeq"));
        return org.apache.ofbiz.entity.condition.EntityCondition.makeCondition(keys);
    }

    /**
     * Saves every row of the latest import in one go: the person fixes all the part numbers of a
     * version (the sketch names a part differently from the shop that sells it) and then presses one
     * button, instead of saving row by row.
     *
     * <p>A row whose part number and quantity did not change is left alone, so saving the whole table
     * does not churn the ledger; the answer says how many rows really changed.
     *
     * @param dctx the dispatch context
     * @param context service context, see {@code servicedef/devbom-services.xml#mfgDevBomSaveAll}:
     *        {@code rows} is a list of maps with {@code lineSeq}, {@code componentCode} and {@code qty},
     *        built by {@link #rowEditOf} from what the page posted
     * @return the service result with {@code savedLines}, {@code keptLines} and {@code goneLines}
     */
    public static Map<String, Object> devBomSaveAll(DispatchContext dctx, Map<String, ? extends Object> context) {
        String denied = MfgSecurity.permissionProblem(dctx, context, MfgConstants.PERM_ENG);
        if (denied != null) {
            return ServiceUtil.returnError(denied);
        }
        Delegator delegator = dctx.getDelegator();
        String projectCode = MfgSupport.toText(context.get("projectCode"));
        if (projectCode == null) {
            return ServiceUtil.returnError("projectCode is required");
        }
        List<Map<String, Object>> rows = UtilGenerics.cast(context.get("rows"));
        if (rows == null || rows.isEmpty()) {
            return ServiceUtil.returnError("没有收到要保存的行");
        }
        String operatorName = MfgSupport.toText(context.get("operatorName"));
        boolean lock = !"N".equals(MfgSupport.toText(context.get("lock")));
        try {
            if (lastImport(delegator, projectCode) == null) {
                return ServiceUtil.returnError("这个项目还没导入过研发 BOM");
            }
            int saved = 0;
            int kept = 0;
            int gone = 0;
            for (Map<String, Object> row : rows) {
                long lineSeq = MfgSupport.toLong(row.get("lineSeq"), 0L);
                GenericValue item = lineSeq <= 0L ? null : itemOf(delegator, projectCode, lineSeq);
                if (item == null) {
                    gone++;
                    continue;
                }
                double qty = MfgSupport.toDouble(row.get("qty"), MfgSupport.numberOrZero(item.get("qty")));
                if (qty < 0.0) {
                    return ServiceUtil.returnError("第 " + lineSeq + " 行的数量不能是负数");
                }
                String typedCode = MfgSupport.toText(row.get("componentCode"));
                String newCode = codeOf(typedCode, item.getString("componentCode"));
                if (newCode.equals(item.getString("componentCode"))
                        && Math.abs(qty - MfgSupport.numberOrZero(item.get("qty"))) < 0.0001) {
                    kept++;
                    continue;
                }
                applyItemEdit(delegator, projectCode, operatorName, item, typedCode, qty, lock,
                        "整表保存第 " + lineSeq + " 行");
                saved++;
            }
            Map<String, Object> result = ServiceUtil.returnSuccess();
            result.put("savedLines", String.valueOf(saved));
            result.put("keptLines", String.valueOf(kept));
            result.put("goneLines", String.valueOf(gone));
            return result;
        } catch (GenericEntityException e) {
            return ServiceUtil.returnError("整表保存研发 BOM 失败：" + e.getMessage());
        }
    }

    /**
     * Turns one row the page posted into the map the batch save reads: the row number, the part
     * number as typed and the quantity. The page sends {@code lineSeq|part number|quantity}, so the
     * part number may contain spaces (a Fritzing name like {@code 5.5V1.5F C型 21*7.5} does).
     *
     * @param raw the posted value, may be null or malformed
     * @return the row, or null when the value is not a row
     */
    static Map<String, Object> rowEditOf(String raw) {
        if (raw == null) {
            return null;
        }
        String text = raw.trim();
        int first = text.indexOf('|');
        int last = text.lastIndexOf('|');
        if (first <= 0 || last <= first) {
            return null;
        }
        long lineSeq = MfgSupport.toLong(text.substring(0, first).trim(), 0L);
        if (lineSeq <= 0L) {
            return null;
        }
        String code = text.substring(first + 1, last).trim();
        String qtyText = text.substring(last + 1).trim();
        if (qtyText.isEmpty()) {
            return null;
        }
        double qty;
        try {
            qty = Double.parseDouble(qtyText);
        } catch (NumberFormatException e) {
            return null;
        }
        if (Double.isNaN(qty) || qty < 0.0) {
            return null;
        }
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("lineSeq", Long.valueOf(lineSeq));
        row.put("componentCode", code);
        row.put("qty", Double.valueOf(qty));
        return row;
    }

    /**
     * The bytes of the uploaded file, decoded from the base64 the page posts.
     *
     * <p>Base64 through the service context, the same as the attachments do it: a service cannot take
     * a byte array straight from a form, and the imported file has to travel with the rows it became.
     *
     * @param base64 the posted text, may be null or broken
     * @return the bytes, or null when nothing usable was posted
     */
    static byte[] sourceBytesOf(String base64) {
        if (base64 == null || base64.trim().isEmpty()) {
            return null;
        }
        try {
            return java.util.Base64.getMimeDecoder().decode(base64);
        } catch (IllegalArgumentException e) {
            // a form that lost a character should not lose the whole import: the rows still come in
            return null;
        }
    }

    /** The part row of one import, or null when this version does not have that line any more. */
    private static GenericValue itemOf(Delegator delegator, String projectCode, long lineSeq)
            throws GenericEntityException {
        GenericValue latest = lastImport(delegator, projectCode);
        if (latest == null) {
            return null;
        }
        return EntityQuery.use(delegator).from("MfgDevBomItem")
                .where("projectCode", projectCode,
                        "importSeq", Long.valueOf(latest.getLong("importSeq").longValue()),
                        "lineSeq", Long.valueOf(lineSeq))
                .queryOne();
    }

    /** The part number a typed value stands for: what was typed, or the one the row has now. */
    private static String codeOf(String typedCode, String currentCode) {
        return MfgProductServices.fittingComponentCode(typedCode == null ? currentCode : typedCode)
                .toUpperCase(Locale.ROOT);
    }

    /**
     * Applies one correction to a part of the latest import: the code moves the lock from the old
     * part number to the new one (that is the whole point - the sketch and the shop name the same
     * part differently), then the lock is topped up to the quantity and the row is written.
     *
     * <p>Shared by the single row save and the batch save so both behave the same.
     *
     * @return the success result with {@code componentCode}, {@code lockedQty} and {@code shortQty}
     */
    private static Map<String, Object> applyItemEdit(Delegator delegator, String projectCode,
            String operatorName, GenericValue item, String typedCode, double qty, boolean lock, String note)
            throws GenericEntityException {
        String oldCode = item.getString("componentCode");
        String newCode = codeOf(typedCode, oldCode);
        if (!newCode.equals(oldCode)) {
            MfgStockLedger.releaseProjectReservation(delegator, projectCode, oldCode,
                    Double.MAX_VALUE, operatorName, "料号改成 " + newCode + "，先释放旧料号的锁");
        }
        double held = MfgSupport.numberOrZero(
                MfgStockLedger.lockedByProjectComponent(delegator, projectCode).get(newCode));
        double gap = qty - held;
        if (lock && gap > 0.0001) {
            held += MfgStockLedger.reserveForProject(delegator, projectCode, newCode, gap, operatorName,
                    "锁料：" + note);
        }
        double shortQty = Math.max(0.0, qty - held);
        item.set("componentCode", newCode);
        item.set("qty", Double.valueOf(MfgSupport.round2(qty)));
        item.set("lockedQty", Double.valueOf(MfgSupport.round2(Math.min(held, qty))));
        item.set("shortQty", Double.valueOf(MfgSupport.round2(shortQty)));
        delegator.store(item);
        Map<String, Object> result = ServiceUtil.returnSuccess();
        result.put("componentCode", newCode);
        result.put("lockedQty", MfgSupport.formatQty(Math.min(held, qty)));
        result.put("shortQty", MfgSupport.formatQty(shortQty));
        return result;
    }

    /**
     * Gives back every part the project has locked, for when the board is ordered or the project is
     * put aside. The stock itself does not change - only what is earmarked.
     *
     * @param dctx the dispatch context
     * @param context service context, see {@code servicedef/devbom-services.xml#mfgDevBomRelease}
     * @return the service result with the released quantity
     */
    public static Map<String, Object> devBomRelease(DispatchContext dctx, Map<String, ? extends Object> context) {
        String denied = MfgSecurity.permissionProblem(dctx, context, MfgConstants.PERM_ENG);
        if (denied != null) {
            return ServiceUtil.returnError(denied);
        }
        Delegator delegator = dctx.getDelegator();
        String projectCode = MfgSupport.toText(context.get("projectCode"));
        if (projectCode == null) {
            return ServiceUtil.returnError("projectCode is required");
        }
        String operatorName = MfgSupport.toText(context.get("operatorName"));
        try {
            double released = 0.0;
            for (Map.Entry<String, Double> entry : MfgStockLedger
                    .lockedByProjectComponent(delegator, projectCode).entrySet()) {
                double qty = MfgSupport.numberOrZero(entry.getValue());
                if (qty <= 0.0001) {
                    continue;
                }
                released += MfgStockLedger.releaseProjectReservation(delegator, projectCode,
                        entry.getKey(), qty, operatorName, "手工释放研发锁料");
            }
            Map<String, Object> result = ServiceUtil.returnSuccess();
            result.put("releasedQty", MfgSupport.formatQty(released));
            return result;
        } catch (GenericEntityException e) {
            return ServiceUtil.returnError("释放研发锁料失败：" + e.getMessage());
        }
    }

    /**
     * One part of one imported version, with what the version means for it. Pure data, so the diff can
     * be tested without a database.
     */
    static final class DevBomRow {

        final String componentCode;
        final String componentName;
        final String partType;
        final String properties;
        final String designators;
        final double qty;
        final Double previousQty;
        final String changeId;
        final String sourceId;

        DevBomRow(String componentCode, String componentName, String partType, String properties,
                String designators, double qty, Double previousQty, String changeId) {
            this(componentCode, componentName, partType, properties, designators, qty, previousQty,
                    changeId, null);
        }

        DevBomRow(String componentCode, String componentName, String partType, String properties,
                String designators, double qty, Double previousQty, String changeId, String sourceId) {
            this.componentCode = componentCode;
            this.componentName = componentName;
            this.partType = partType;
            this.properties = properties;
            this.designators = designators;
            this.qty = qty;
            this.previousQty = previousQty;
            this.changeId = changeId;
            this.sourceId = sourceId;
        }

        /** The same part, but typed in by hand instead of printed by the sketch. */
        DevBomRow typedIn() {
            return new DevBomRow(componentCode, componentName, partType, properties, designators,
                    qty, Double.valueOf(qty), MfgConstants.BOM_SAME, MfgConstants.BOM_SOURCE_MANUAL);
        }
    }

    /**
     * Compares the rows of one import with the parts of the version before it.
     *
     * <p>Rows of the same part are merged first (a sketch can print one part twice), then every part
     * is compared with the previous quantity: absent before is {@link MfgConstants#BOM_NEW}, another
     * quantity is {@link MfgConstants#BOM_CHANGED}, the same is {@link MfgConstants#BOM_SAME}. Parts
     * the previous version used and this one does not follow as {@link MfgConstants#BOM_DROPPED} with
     * quantity {@code 0} and the old quantity remembered, so the caller can write the material off.
     *
     * @param previous part number to quantity of the version before, empty for the first import
     * @param parsed the rows of this import, as {@link MfgCsv#parseBom} returns them
     * @return the parts of this version, then the dropped ones, in the order they appeared
     */
    static List<DevBomRow> devBomRows(Map<String, Double> previous, List<String[]> parsed) {
        Map<String, double[]> merged = new LinkedHashMap<>();
        Map<String, String[]> details = new LinkedHashMap<>();
        List<String> order = new ArrayList<>();
        if (parsed != null) {
            for (String[] row : parsed) {
                if (row == null || row.length == 0 || row[0] == null) {
                    continue;
                }
                String code = MfgProductServices.fittingComponentCode(row[0].trim()).toUpperCase(Locale.ROOT);
                if (code.isEmpty()) {
                    continue;
                }
                double qty = MfgSupport.toDouble(row.length > 2 ? row[2] : null, 1.0);
                if (qty <= 0.0) {
                    qty = 1.0;
                }
                double[] sum = merged.get(code);
                if (sum == null) {
                    sum = new double[] {0.0};
                    merged.put(code, sum);
                    details.put(code, row);
                    order.add(code);
                } else {
                    String designators = row.length > 3 ? row[3] : null;
                    String[] kept = details.get(code);
                    if (designators != null && !designators.trim().isEmpty()) {
                        String joined = kept.length > 3 && kept[3] != null && !kept[3].isEmpty()
                                ? kept[3] + "," + designators.trim() : designators.trim();
                        String[] bigger = new String[Math.max(kept.length, 7)];
                        System.arraycopy(kept, 0, bigger, 0, kept.length);
                        bigger[3] = joined;
                        details.put(code, bigger);
                    }
                }
                sum[0] += qty;
            }
        }
        List<DevBomRow> rows = new ArrayList<>(order.size());
        Set<String> seen = new LinkedHashSet<>(order);
        for (String code : order) {
            String[] row = details.get(code);
            Double before = previous == null ? null : previous.get(code);
            String changeId;
            if (before == null) {
                changeId = MfgConstants.BOM_NEW;
            } else if (Math.abs(before.doubleValue() - merged.get(code)[0]) > 0.0001) {
                changeId = MfgConstants.BOM_CHANGED;
            } else {
                changeId = MfgConstants.BOM_SAME;
            }
            rows.add(new DevBomRow(code,
                    row.length > 1 && row[1] != null && !row[1].trim().isEmpty() ? row[1].trim() : code,
                    row.length > 1 && row[1] != null && !row[1].trim().isEmpty() ? row[1].trim() : code,
                    row.length > 6 ? row[6] : null,
                    row.length > 3 ? row[3] : null,
                    merged.get(code)[0], before, changeId));
        }
        if (previous != null) {
            for (Map.Entry<String, Double> entry : previous.entrySet()) {
                if (seen.contains(entry.getKey())) {
                    continue;
                }
                rows.add(new DevBomRow(entry.getKey(), entry.getKey(), entry.getKey(), null, null,
                        0.0, entry.getValue(), MfgConstants.BOM_DROPPED));
            }
        }
        return rows;
    }

    /**
     * Carries the parts that were typed in by hand into the next version of the design BOM.
     *
     * <p>A breadboard build needs parts the sketch does not show - an adapter board that lets a
     * sensor sit in the breadboard, a battery holder. They can only be typed in, and the export will
     * never mention them, so without this every new import would drop them: the lock would go, the
     * shopping list would forget to buy them, and the person would have to type them in again after
     * every export.
     *
     * @param rows the parts the file lists, in file order
     * @param typedIn the parts the version before had by hand, in its order
     * @return the parts of the file followed by those only the previous version knew by hand; a part
     *         the file prints now is taken from the file
     */
    static List<DevBomRow> withManualParts(List<DevBomRow> rows, List<DevBomRow> typedIn) {
        if (typedIn == null || typedIn.isEmpty()) {
            return new ArrayList<>(rows);
        }
        Map<String, DevBomRow> byCode = new LinkedHashMap<>();
        for (DevBomRow row : typedIn) {
            byCode.put(row.componentCode.toUpperCase(Locale.ROOT), row);
        }
        List<DevBomRow> out = new ArrayList<>(rows.size() + byCode.size());
        Set<String> printed = new java.util.HashSet<>();
        for (DevBomRow row : rows) {
            String code = row.componentCode.toUpperCase(Locale.ROOT);
            if (MfgConstants.BOM_DROPPED.equals(row.changeId) && byCode.containsKey(code)) {
                // the comparison put a tombstone here because the file does not mention the part any
                // more - but it was typed in, so it is still wanted: the part replaces the tombstone
                continue;
            }
            printed.add(code);
            out.add(row);
        }
        for (Map.Entry<String, DevBomRow> entry : byCode.entrySet()) {
            if (printed.add(entry.getKey())) {
                out.add(entry.getValue().typedIn());
            }
        }
        return out;
    }

    /**
     * The parts of one version that were typed in by hand, so the next import can carry them over.
     *
     * @param delegator the delegator
     * @param projectCode the development project
     * @param importSeq the version whose rows are read
     * @return the rows typed in by hand, empty when the version has none
     * @throws GenericEntityException when the rows cannot be read
     */
    private static List<DevBomRow> manualRowsOf(Delegator delegator, String projectCode, long importSeq)
            throws GenericEntityException {
        List<DevBomRow> rows = new ArrayList<>();
        for (GenericValue item : itemsOf(delegator, projectCode, importSeq)) {
            if (!MfgConstants.BOM_SOURCE_MANUAL.equals(item.getString("sourceId"))) {
                continue;
            }
            if (MfgConstants.BOM_DROPPED.equals(item.getString("changeId"))) {
                continue;
            }
            double qty = MfgSupport.numberOrZero(item.get("qty"));
            if (qty <= 0.0) {
                continue;
            }
            rows.add(new DevBomRow(item.getString("componentCode"), item.getString("componentName"),
                    item.getString("partType"), item.getString("properties"),
                    item.getString("designators"), qty, null, MfgConstants.BOM_SAME));
        }
        return rows;
    }

    /**
     * On hand, reserved and available quantity per part number, summed over the lots. The codes are
     * compared upper case, because the sketch and the shop do not always agree on the case.
     *
     * @param delegator the delegator
     * @return the three numbers per part number
     * @throws GenericEntityException when the lots cannot be read
     */
    static Map<String, double[]> componentStock(Delegator delegator) throws GenericEntityException {
        Map<String, double[]> byCode = new LinkedHashMap<>();
        for (GenericValue lot : MfgStockLedger.componentLots(delegator)) {
            if (lot.get("onHandQty") == null) {
                MfgStockLedger.ensureLotLedger(delegator, lot);
            }
            String code = upperCode(lot.getString("componentCode"));
            if (code.isEmpty()) {
                continue;
            }
            double[] sums = byCode.get(code);
            if (sums == null) {
                sums = new double[3];
                byCode.put(code, sums);
            }
            sums[0] += MfgSupport.numberOrZero(lot.get("onHandQty"));
            sums[1] += MfgSupport.numberOrZero(lot.get("reservedQty"));
            sums[2] += MfgSupport.numberOrZero(lot.get("availableQty"));
        }
        return byCode;
    }

    /**
     * The parts that are on the shelf right now, so the page can offer them while a part number of
     * the sketch is corrected to the one the shop books the part under. This is the answer to "the BOM
     * says 100 nF but the stock has a 100 nF ceramic capacitor": pick its number here.
     *
     * <p>The part numbers of this very version come first: a shop that has run for a while carries
     * more than {@value #MAX_STOCK_OPTIONS} part numbers, and a list that is cut short must never cut
     * away a number the page is about (that is what made "stock codes are offered" fail on a full
     * database - the code was there, only behind the cap).
     *
     * @param delegator the delegator
     * @param wanted part numbers the page shows rows for, offered first
     * @return part number, name and quantity on hand, at most {@value #MAX_STOCK_OPTIONS}
     * @throws GenericEntityException when the lots cannot be read
     */
    static List<Map<String, Object>> componentStockOptions(Delegator delegator, List<String> wanted)
            throws GenericEntityException {
        Map<String, Map<String, Object>> byCode = allStockOptions(delegator);
        List<String> inStock = new ArrayList<>(byCode.keySet());
        // sorted by part number, so the rest of the list is stable and can be searched by typing; the
        // order of the lots would push everything bought recently out of it
        inStock.sort(null);
        List<String> order = offerOrder(upperCodes(wanted), inStock, MAX_STOCK_OPTIONS);
        List<Map<String, Object>> options = new ArrayList<>(order.size());
        for (String code : order) {
            options.add(byCode.get(code));
        }
        return options;
    }

    /**
     * Every part number that has something on the shelf, with a name and the quantity on hand.
     *
     * <p>The page cannot hold them all: a shop that has run for a year carries hundreds of part
     * numbers, and the offer is cut at {@value #MAX_STOCK_OPTIONS}. So the page asks again while the
     * person types ({@link #matchingStockOptions}), and this is what it searches.
     *
     * @param delegator the delegator
     * @return part number to option, in part number order
     * @throws GenericEntityException when the lots cannot be read
     */
    static Map<String, Map<String, Object>> allStockOptions(Delegator delegator)
            throws GenericEntityException {
        Map<String, Map<String, Object>> byCode = new LinkedHashMap<>();
        for (GenericValue lot : MfgStockLedger.componentLots(delegator)) {
            if (lot.get("onHandQty") == null) {
                // a lot created a moment ago has no totals yet: without this the part is missing from
                // the offer right when the person wants to pick it
                MfgStockLedger.ensureLotLedger(delegator, lot);
            }
            String code = upperCode(lot.getString("componentCode"));
            if (code.isEmpty()) {
                continue;
            }
            Map<String, Object> option = byCode.get(code);
            if (option == null) {
                option = new LinkedHashMap<>();
                option.put("componentCode", code);
                option.put("componentName", null);
                option.put("onHandQty", Double.valueOf(0.0));
                byCode.put(code, option);
            }
            if (option.get("componentName") == null && lot.getString("componentName") != null) {
                option.put("componentName", lot.getString("componentName"));
            }
            option.put("onHandQty", Double.valueOf(MfgSupport.numberOrZero(option.get("onHandQty"))
                    + MfgSupport.numberOrZero(lot.get("onHandQty"))));
        }
        // only what is really on the shelf, and in part number order so the list is stable
        Map<String, Map<String, Object>> inStock = new java.util.TreeMap<>();
        for (Map.Entry<String, Map<String, Object>> entry : byCode.entrySet()) {
            if (MfgSupport.numberOrZero(entry.getValue().get("onHandQty")) > 0.0001) {
                inStock.put(entry.getKey(), entry.getValue());
            }
        }
        return inStock;
    }

    /**
     * The part numbers of the shelf that answer what was typed, so the page can offer them while a
     * part is added or a sketch code is corrected.
     *
     * <p>Searching is done here and not in the browser: the page holds at most
     * {@value #MAX_STOCK_OPTIONS} codes, and a shop with hundreds of them would otherwise never offer
     * the one the person is looking for (typing "转接板" found three of sixty-two matches).
     *
     * @param all every part number on the shelf, see {@link #allStockOptions}
     * @param term what was typed, matched against the part number and the name; blank offers the first ones
     * @param limit how many to answer with
     * @return the matches, the ones that start with the term first
     */
    static List<Map<String, Object>> matchingStockOptions(List<Map<String, Object>> all, String term, int limit) {
        List<Map<String, Object>> out = new ArrayList<>();
        if (all == null || all.isEmpty() || limit <= 0) {
            return out;
        }
        String wanted = term == null ? "" : term.trim().toUpperCase(Locale.ROOT);
        if (wanted.isEmpty()) {
            for (Map<String, Object> option : all) {
                if (out.size() >= limit) {
                    break;
                }
                out.add(option);
            }
            return out;
        }
        List<Map<String, Object>> starts = new ArrayList<>();
        List<Map<String, Object>> contains = new ArrayList<>();
        for (Map<String, Object> option : all) {
            String code = upperCode(MfgSupport.toText(option.get("componentCode")));
            String name = MfgSupport.toText(option.get("componentName"));
            String upperName = name == null ? "" : name.toUpperCase(Locale.ROOT);
            if (code.startsWith(wanted) || upperName.startsWith(wanted)) {
                starts.add(option);
            } else if (code.contains(wanted) || upperName.contains(wanted)) {
                contains.add(option);
            }
        }
        for (Map<String, Object> option : starts) {
            if (out.size() >= limit) {
                return out;
            }
            out.add(option);
        }
        for (Map<String, Object> option : contains) {
            if (out.size() >= limit) {
                break;
            }
            out.add(option);
        }
        return out;
    }

    /**
     * The order the page offers the part numbers in: the ones this version uses first, then the rest.
     *
     * @param wanted part numbers of the version, already upper case
     * @param inStock part numbers with stock on hand, sorted
     * @param limit how many the page takes at most
     * @return the part numbers to offer, without duplicates
     */
    static List<String> offerOrder(List<String> wanted, List<String> inStock, int limit) {
        Set<String> order = new LinkedHashSet<>();
        if (wanted != null && limit > 0) {
            for (String code : wanted) {
                if (order.size() >= limit) {
                    break;
                }
                if (code != null && inStock.contains(code)) {
                    order.add(code);
                }
            }
        }
        for (String code : inStock) {
            if (order.size() >= limit) {
                break;
            }
            order.add(code);
        }
        return new ArrayList<>(order);
    }

    /** The part numbers of an empty or null list, upper case, without blanks. */
    private static List<String> upperCodes(List<String> codes) {
        List<String> upper = new ArrayList<>();
        if (codes == null) {
            return upper;
        }
        for (String code : codes) {
            String text = upperCode(code);
            if (!text.isEmpty()) {
                upper.add(text);
            }
        }
        return upper;
    }

    /** The part number in the case the BOM rows use, empty when there is none. */
    static String upperCode(String code) {
        return code == null ? "" : code.trim().toUpperCase(Locale.ROOT);
    }

    /**
     * The newest import of a project.
     *
     * <p>The file bytes are left out: this row is read on every page and on every row save, and the
     * blob belongs to the download only ({@code devBomSource}).
     *
     * @param delegator the delegator
     * @param projectCode the development project
     * @return the import, {@code null} when the project imported nothing yet
     * @throws GenericEntityException when it cannot be read
     */
    static GenericValue lastImport(Delegator delegator, String projectCode) throws GenericEntityException {
        if (projectCode == null) {
            return null;
        }
        List<GenericValue> imports = EntityQuery.use(delegator).from("MfgDevBom")
                .select(HEAD_FIELDS)
                .where("projectCode", projectCode).orderBy("-importSeq").queryList();
        return imports.isEmpty() ? null : imports.get(0);
    }

    /**
     * The parts of one import.
     *
     * @param delegator the delegator
     * @param projectCode the development project
     * @param importSeq the import number
     * @return the rows, in import order
     * @throws GenericEntityException when they cannot be read
     */
    static List<GenericValue> itemsOf(Delegator delegator, String projectCode, long importSeq)
            throws GenericEntityException {
        return EntityQuery.use(delegator).from("MfgDevBomItem")
                .where("projectCode", projectCode, "importSeq", Long.valueOf(importSeq))
                .orderBy("lineSeq").queryList();
    }

    /**
     * The label of a version: what the person typed, otherwise the {@code vNN} of the file name,
     * otherwise the import number.
     *
     * @param typed the version typed in, may be {@code null}
     * @param fileName the name of the uploaded file, may be {@code null}
     * @param importSeq the number of the import
     * @return the label
     */
    static String versionOf(String typed, String fileName, long importSeq) {
        if (typed != null) {
            return typed.length() > 100 ? typed.substring(0, 100) : typed;
        }
        if (fileName != null) {
            java.util.regex.Matcher matcher = java.util.regex.Pattern
                    .compile("(?i)v[0-9]+").matcher(fileName);
            if (matcher.find()) {
                return matcher.group().toLowerCase(Locale.ROOT);
            }
        }
        return "import-" + importSeq;
    }

    private static String logMessage(String projectCode, String version, long importSeq, int itemCount,
            int newCount, int changedCount, int droppedCount, int shortCount, double locked,
            double consumed) {
        return "Design BOM " + projectCode + " " + version + " (import " + importSeq + "): " + itemCount
                + " parts, " + newCount + " new, " + changedCount + " changed, " + droppedCount
                + " dropped, " + shortCount + " short, locked " + MfgSupport.formatQty(locked)
                + ", used up as development material " + MfgSupport.formatQty(consumed);
    }

    private static void logImport(String message) {
        org.apache.ofbiz.base.util.Debug.logInfo(message, MfgDevBomServices.class.getName());
    }
}
