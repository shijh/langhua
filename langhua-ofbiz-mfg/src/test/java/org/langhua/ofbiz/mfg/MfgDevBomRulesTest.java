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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;

/**
 * The design BOM: reading the HTML report Fritzing writes next to a breadboard sketch, and comparing
 * one version of it with the version before.
 *
 * <p>The classes are the ones the shop buys under, so the rules that build them are pinned here: a
 * part with no manufacturer number has to get a code that is the same on every import (otherwise
 * every evening would create a new part and the stock would never match).
 */
public class MfgDevBomRulesTest {

    /** The shape of a Fritzing report, cut down to the parts this test is about. */
    private static final String REPORT = "<!DOCTYPE html PUBLIC \"-//W3C//DTD XHTML 1.0 Transitional//EN\""
            + " \"http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd\">\n"
            + "<html xmlns=\"http://www.w3.org/1999/xhtml\"><head><title>Fritzing Bill of Materials</title>"
            + "<style type=\"text/css\">table { border-collapse: collapse; }</style></head><body>\n"
            + "<h1>Bill of Materials: pixel-flash-v94.fzz</h1>\n"
            + "<h2>Assembly List</h2>\n<table>\n  <thead>\n   <tr>\n"
            + "    <th>Label</th><th>MN (manufacturer number)</th><th>MPN (manufacturer part number)</th>"
            + "<th>Part Type</th><th>Properties</th>\n    </tr>\n  </thead>\n  <tbody>\n"
            + "<tr><td>C1</td><td></td><td></td><td>Capacitor 0603</td>"
            + "<td class=\"props\">电容 100 nF; 电压 16V; dielectric X7R; 公差 ±10%; 包 [SMD] 0603</td></tr>\n"
            + "<tr><td>C2</td><td></td><td></td><td>Capacitor 0603</td>"
            + "<td class=\"props\">电容 100 nF; 电压 16V; dielectric X7R; 公差 ±10%; 包 [SMD] 0603</td></tr>\n"
            + "<tr><td>D3</td><td></td><td></td><td>BAS70BRW</td>"
            + "<td class=\"props\">包 SOT-363 (SC-70-6); 元件号 BAS70BRW</td></tr>\n"
            + "<tr><td>R1</td><td></td><td></td><td>10kΩ Resistor</td>"
            + "<td class=\"props\">电阻 10kΩ; 公差 ±5%; 电源 1/10W; 包 [SMD] 0603</td></tr>\n"
            + "<tr><td>U1</td><td></td><td>CH32V003F4U6</td><td>CH32V003</td>"
            + "<td class=\"props\">针脚 20; chip CH32V003; 包 QFN20</td></tr>\n"
            + "  </tbody>\n</table>\n"
            + "<h2>Shopping List</h2>\n<table>\n  <thead>\n\t<tr>\n"
            + "    <th>Amount</th><th>MN (manufacturer number)</th><th>MPN (manufacturer part number)</th>"
            + "<th>Part Type</th><th>Properties</th>\n    </tr>\n  </thead>\n  <tbody>\n"
            + "<tr><td>2</td><td></td><td></td><td>Capacitor 0603</td>"
            + "<td class=\"props\">电容 100 nF; 电压 16V; dielectric X7R; 公差 ±10%; 包 [SMD] 0603</td></tr>\n"
            + "<tr><td>1</td><td></td><td></td><td>BAS70BRW</td>"
            + "<td class=\"props\">包 SOT-363 (SC-70-6); 元件号 BAS70BRW</td></tr>\n"
            + "<tr><td>1</td><td></td><td></td><td>10kΩ Resistor</td>"
            + "<td class=\"props\">电阻 10kΩ; 公差 ±5%; 电源 1/10W; 包 [SMD] 0603</td></tr>\n"
            + "<tr><td>1</td><td></td><td>CH32V003F4U6</td><td>CH32V003</td>"
            + "<td class=\"props\">针脚 20; chip CH32V003; 包 QFN20</td></tr>\n"
            + "  </tbody>\n</table>\n</body>\n</html>\n";

    @Test
    public void readsTheFritzingHtmlReport() {
        assertEquals(MfgCsv.BomFormat.FRITZING_HTML, MfgCsv.detectBomFormat(REPORT));
        List<String[]> rows = MfgCsv.parseBom(REPORT, null);
        assertEquals(4, rows.size());

        // the capacitor prints no manufacturer number: the code comes from part type and properties
        assertEquals("C-100NF-16V-0603", rows.get(0)[0]);
        // the quantity is the amount of the shopping list, the designators come from the assembly list
        assertEquals("2", rows.get(0)[2]);
        assertEquals("C1,C2", rows.get(0)[3]);
        assertTrue(rows.get(0)[6].contains("100 nF"));

        // 元件号 inside the properties line is the part number when MN and MPN are empty
        assertEquals("BAS70BRW", rows.get(1)[0]);
        assertEquals("1", rows.get(1)[2]);

        // a resistor without a part number
        assertEquals("R-10K-0603", rows.get(2)[0]);
        assertEquals("R1", rows.get(2)[3]);

        // the manufacturer part number wins over everything else
        assertEquals("CH32V003F4U6", rows.get(3)[0]);
    }

    @Test
    public void buildsACodeFromPartTypeAndProperties() {
        assertEquals("R-10K-0603", MfgCsv.syntheticComponentCode("10kΩ Resistor",
                "电阻 10kΩ; 公差 ±5%; 电源 1/10W; 包 [SMD] 0603"));
        assertEquals("R-100R-0603", MfgCsv.syntheticComponentCode("100Ω Resistor",
                "电阻 100Ω; 公差 ±1%; 包 [SMD] 0603"));
        assertEquals("C-100NF-16V-0603", MfgCsv.syntheticComponentCode("Capacitor 0603",
                "电容 100 nF; 电压 16V; 包 [SMD] 0603"));
        // nothing identifies the part: a stand-in that is the same on every import
        assertEquals(MfgCsv.syntheticComponentCode("WCH-LinkE Debugger", "包 42 x 16.5 x 1.6 mm"),
                MfgCsv.syntheticComponentCode("WCH-LinkE Debugger", "包 42 x 16.5 x 1.6 mm"));
    }

    @Test
    public void readsASingleWordPartTypeAsTheCode() {
        List<String[]> rows = MfgCsv.parseFritzingHtmlBom(REPORT);
        boolean found = false;
        for (String[] row : rows) {
            if (row[1] != null && row[1].startsWith("BAS70BRW")) {
                found = true;
                assertEquals("BAS70BRW", row[0]);
            }
        }
        assertTrue(found);
    }

    @Test
    public void decodesTheTagsAndEntitiesOfACell() {
        assertEquals("10kΩ Resistor", MfgCsv.cleanHtml("  <b>10kΩ</b>\n   Resistor  "));
        assertEquals("A & B", MfgCsv.cleanHtml("A &amp; B"));
        assertEquals("100 µF", MfgCsv.cleanHtml("100 &#181;F"));
        assertEquals("x<y>", MfgCsv.cleanHtml("x&lt;y&gt;"));
        assertNull(MfgCsv.cleanHtml(null));
    }

    @Test
    public void marksNewChangedUnchangedAndDroppedParts() {
        Map<String, Double> previous = new LinkedHashMap<>();
        previous.put("C1", Double.valueOf(2.0));
        previous.put("R1", Double.valueOf(1.0));
        previous.put("OLD-1", Double.valueOf(5.0));

        List<String[]> parsed = new ArrayList<>();
        parsed.add(row("C1", "Capacitor 0603", "2", "C1"));
        parsed.add(row("R1", "10kΩ Resistor", "3", "R1"));
        parsed.add(row("R2", "4.7kΩ Resistor", "1", "R2"));

        List<MfgDevBomServices.DevBomRow> rows = MfgDevBomServices.devBomRows(previous, parsed);
        assertEquals(4, rows.size());
        assertEquals(MfgConstants.BOM_SAME, rows.get(0).changeId);
        assertEquals(MfgConstants.BOM_CHANGED, rows.get(1).changeId);
        assertEquals(3.0, rows.get(1).qty, 0.0001);
        assertEquals(1.0, rows.get(1).previousQty.doubleValue(), 0.0001);
        assertEquals(MfgConstants.BOM_NEW, rows.get(2).changeId);
        assertNull(rows.get(2).previousQty);
        // the part the sketch no longer prints: quantity 0, the old quantity remembered
        assertEquals(MfgConstants.BOM_DROPPED, rows.get(3).changeId);
        assertEquals("OLD-1", rows.get(3).componentCode);
        assertEquals(0.0, rows.get(3).qty, 0.0001);
        assertEquals(5.0, rows.get(3).previousQty.doubleValue(), 0.0001);
    }

    @Test
    public void mergesTwoRowsOfTheSamePart() {
        List<String[]> parsed = new ArrayList<>();
        parsed.add(row("C1", "Capacitor 0603", "2", "C1"));
        parsed.add(row("C1", "Capacitor 0603", "3", "C7"));
        List<MfgDevBomServices.DevBomRow> rows = MfgDevBomServices.devBomRows(null, parsed);
        assertEquals(1, rows.size());
        assertEquals(5.0, rows.get(0).qty, 0.0001);
        assertEquals("C1,C7", rows.get(0).designators);
        assertEquals(MfgConstants.BOM_NEW, rows.get(0).changeId);
    }

    @Test
    public void refusesARowWithoutAQuantityAsZero() {
        // a broken row still counts as one piece, otherwise the part would silently disappear
        List<String[]> parsed = new ArrayList<>();
        parsed.add(row("R9", "Resistor", "0", null));
        List<MfgDevBomServices.DevBomRow> rows = MfgDevBomServices.devBomRows(null, parsed);
        assertEquals(1, rows.size());
        assertEquals(1.0, rows.get(0).qty, 0.0001);
    }

    @Test
    public void takesTheVersionFromTheFileName() {
        assertEquals("v94", MfgDevBomServices.versionOf(null, "pixel-flash-v94_bom.html", 3L));
        assertEquals("v95", MfgDevBomServices.versionOf("v95", "pixel-flash-v94_bom.html", 3L));
        assertEquals("import-3", MfgDevBomServices.versionOf(null, "bom.html", 3L));
        assertEquals("import-1", MfgDevBomServices.versionOf(null, null, 1L));
    }

    @Test
    public void readsThePropertiesOfAPart() {
        assertEquals("10kΩ", MfgCsv.propertyValue("电阻 10kΩ; 公差 ±5%", "电阻"));
        assertEquals("[SMD] 0603", MfgCsv.propertyValue("包 [SMD] 0603; 公差 ±5%", "包"));
        assertNull(MfgCsv.propertyValue("电容 100 nF", "电阻"));
        assertNull(MfgCsv.propertyValue(null, "电阻"));
    }

    @Test
    public void offersThePartNumbersOfTheVersionFirst() {
        // a shop with a few hundred part numbers on the shelf: the list the page offers is cut short,
        // and the codes of this very version must never be the ones cut away
        List<String> wanted = new ArrayList<>(java.util.Arrays.asList(
                "C-100NF-16V-0603", "R-10K-0603", "R-10K-0603", "NOT-IN-STOCK"));
        List<String> inStock = new ArrayList<>(java.util.Arrays.asList(
                "A1", "B2", "C-100NF-16V-0603", "R-10K-0603"));
        List<String> order = MfgDevBomServices.offerOrder(wanted, inStock, 3);
        assertEquals(3, order.size());
        assertEquals("C-100NF-16V-0603", order.get(0));
        assertEquals("R-10K-0603", order.get(1));
        assertEquals("A1", order.get(2));
        // a code of the version that is not on the shelf is not offered, and nothing repeats
        assertEquals(java.util.Arrays.asList("C-100NF-16V-0603", "R-10K-0603", "A1", "B2"),
                MfgDevBomServices.offerOrder(wanted, inStock, 10));
        assertTrue(MfgDevBomServices.offerOrder(wanted, inStock, 0).isEmpty());
        assertEquals(java.util.Arrays.asList("A1", "B2", "C-100NF-16V-0603", "R-10K-0603"),
                MfgDevBomServices.offerOrder(null, inStock, 10));
    }

    @Test
    public void readsARowOfTheWholeTableSave() {
        // the page posts lineSeq|part number|quantity; a Fritzing name may carry spaces
        Map<String, Object> row = MfgDevBomServices.rowEditOf("3|5.5V1.5F C型 21*7.5|2");
        assertEquals(Long.valueOf(3L), row.get("lineSeq"));
        assertEquals("5.5V1.5F C型 21*7.5", row.get("componentCode"));
        assertEquals(2.0, (Double) row.get("qty"), 0.0001);
        // a part number may itself contain the separator, so only the outer two are separators
        assertEquals("A|B", MfgDevBomServices.rowEditOf("11|A|B|4.5").get("componentCode"));
        assertEquals(4.5, (Double) MfgDevBomServices.rowEditOf("11|A|B|4.5").get("qty"), 0.0001);
    }

    @Test
    public void refusesAnIncompleteRowOfTheWholeTableSave() {
        assertNull(MfgDevBomServices.rowEditOf(null));
        assertNull(MfgDevBomServices.rowEditOf(""));
        assertNull(MfgDevBomServices.rowEditOf("R-10K-0603"));
        assertNull(MfgDevBomServices.rowEditOf("3|R-10K-0603"));
        assertNull(MfgDevBomServices.rowEditOf("|R-10K-0603|2"));
        assertNull(MfgDevBomServices.rowEditOf("0|R-10K-0603|2"));
        assertNull(MfgDevBomServices.rowEditOf("3|R-10K-0603|"));
        assertNull(MfgDevBomServices.rowEditOf("3|R-10K-0603|abc"));
        assertNull(MfgDevBomServices.rowEditOf("3|R-10K-0603|-1"));
    }

    @Test
    public void carriesATypedInPartIntoTheNextVersion() {
        // a sensor does not sit in a breadboard by itself: it needs an adapter board, and the sketch
        // does not show it. The part was typed in, so the next export does not mention it - without
        // carrying it over the lock would go and the shopping list would forget it
        List<String[]> parsed = new ArrayList<>();
        parsed.add(row("C1", "Capacitor 0603", "2", "C1"));
        List<MfgDevBomServices.DevBomRow> rows = MfgDevBomServices.devBomRows(null, parsed);

        List<MfgDevBomServices.DevBomRow> typedIn = new ArrayList<>();
        typedIn.add(new MfgDevBomServices.DevBomRow("NFC-ADAPTER", "NFC 转接板", null, null,
                "J9", 1.0, null, MfgConstants.BOM_SAME));

        List<MfgDevBomServices.DevBomRow> next = MfgDevBomServices.withManualParts(rows, typedIn);
        assertEquals(2, next.size());
        assertEquals("C1", next.get(0).componentCode);
        MfgDevBomServices.DevBomRow adapter = next.get(1);
        assertEquals("NFC-ADAPTER", adapter.componentCode);
        assertEquals("NFC 转接板", adapter.componentName);
        assertEquals("J9", adapter.designators);
        assertEquals(1.0, adapter.qty, 0.0001);
        // it is not a new part: the version before had it too, so the page must not call it new
        assertEquals(MfgConstants.BOM_SAME, adapter.changeId);
        assertEquals(1.0, adapter.previousQty.doubleValue(), 0.0001);
        assertEquals(MfgConstants.BOM_SOURCE_MANUAL, adapter.sourceId);
        // the parts of the file keep coming from the file
        assertNull(next.get(0).sourceId);
    }

    @Test
    public void letsTheFileWinOverATypedInPartItNowPrints() {
        // somebody finally drew the adapter board into the sketch: the export carries it now, so the
        // row comes from the file (with the quantity of the sketch) and is not added a second time
        List<String[]> parsed = new ArrayList<>();
        parsed.add(row("nfc-adapter", "NFC 转接板", "3", "J9"));
        List<MfgDevBomServices.DevBomRow> rows = MfgDevBomServices.devBomRows(null, parsed);

        List<MfgDevBomServices.DevBomRow> typedIn = new ArrayList<>();
        typedIn.add(new MfgDevBomServices.DevBomRow("NFC-ADAPTER", "NFC 转接板", null, null,
                "J9", 1.0, null, MfgConstants.BOM_SAME));

        List<MfgDevBomServices.DevBomRow> next = MfgDevBomServices.withManualParts(rows, typedIn);
        assertEquals(1, next.size());
        assertEquals(3.0, next.get(0).qty, 0.0001);
        assertNull(next.get(0).sourceId);
        // nothing typed in, nothing to carry
        assertEquals(rows.size(), MfgDevBomServices.withManualParts(rows, null).size());
        assertEquals(rows.size(), MfgDevBomServices.withManualParts(rows, new ArrayList<>()).size());
    }

    private static String[] row(String code, String name, String qty, String designators) {
        return new String[] {code, name, qty, designators, "N", null, null};
    }

    @Test
    public void searchesEveryPartNumberOnTheShelfNotJustTheFirstPage() {
        // the page holds only the first codes of the shelf; typing "转接板" used to look at those few
        // and find three of sixty-two, because the other fifty-nine were cut away by the cap
        List<Map<String, Object>> all = new ArrayList<>();
        all.add(option("C-100NF-16V-0603", "100nF 16V 0603"));
        all.add(option("NFC-ADAPTER", "NFC 转接板"));
        all.add(option("LCD1602-BOARD", "LCD1602液晶屏转接板"));
        all.add(option("0.5MM-30P-BOARD", "0.5MM间距 30P 转接板;焊接FPC座+直针"));
        all.add(option("WS2812B-1010", "LED"));

        List<Map<String, Object>> found = MfgDevBomServices.matchingStockOptions(all, "转接板", 40);
        assertEquals(3, found.size());
        // name matches come back, not only the codes of the first page
        assertEquals("NFC-ADAPTER", found.get(0).get("componentCode"));
        assertEquals("LCD1602-BOARD", found.get(1).get("componentCode"));
        assertEquals("0.5MM-30P-BOARD", found.get(2).get("componentCode"));

        // the part number is searched too, and case does not matter
        assertEquals(1, MfgDevBomServices.matchingStockOptions(all, "nfc-ad", 40).size());
        // what starts with the term comes before what merely contains it
        List<Map<String, Object>> byCode = MfgDevBomServices.matchingStockOptions(all, "100nf", 40);
        assertEquals(1, byCode.size());
        assertEquals("C-100NF-16V-0603", byCode.get(0).get("componentCode"));
        // a blank term offers the first codes of the shelf
        assertEquals(2, MfgDevBomServices.matchingStockOptions(all, null, 2).size());
        assertEquals(0, MfgDevBomServices.matchingStockOptions(all, "没有这个料", 40).size());
        assertTrue(MfgDevBomServices.matchingStockOptions(null, "x", 10).isEmpty());
        assertTrue(MfgDevBomServices.matchingStockOptions(all, "x", 0).isEmpty());
        // the answer is capped, so a one-letter search cannot flood the page
        assertEquals(2, MfgDevBomServices.matchingStockOptions(all, "0", 2).size());
    }

    @Test
    public void keepsATypedInPartTheComparisonWouldHaveWrittenOff() {
        // the file no longer mentions the adapter, so comparing with the previous version puts a
        // "no longer used" row there (quantity 0). It was typed in, so it is still wanted: the part
        // has to replace that tombstone, not sit next to it (the first version of this kept both and
        // the adapter silently disappeared from the current table)
        Map<String, Double> previous = new LinkedHashMap<>();
        previous.put("C-100NF-16V-0603", Double.valueOf(2.0));
        previous.put("NFC-ADAPTER", Double.valueOf(1.0));
        List<String[]> parsed = new ArrayList<>();
        parsed.add(row("C-100NF-16V-0603", "Capacitor 0603", "2", "C1"));
        List<MfgDevBomServices.DevBomRow> rows = MfgDevBomServices.devBomRows(previous, parsed);
        assertEquals(2, rows.size());
        assertEquals(MfgConstants.BOM_DROPPED, rows.get(1).changeId);

        List<MfgDevBomServices.DevBomRow> typedIn = new ArrayList<>();
        typedIn.add(new MfgDevBomServices.DevBomRow("NFC-ADAPTER", "NFC 转接板", null, null,
                "J9", 1.0, null, MfgConstants.BOM_SAME));

        List<MfgDevBomServices.DevBomRow> next = MfgDevBomServices.withManualParts(rows, typedIn);
        assertEquals(2, next.size());
        int adapters = 0;
        for (MfgDevBomServices.DevBomRow row : next) {
            if ("NFC-ADAPTER".equals(row.componentCode)) {
                adapters++;
                // still used: quantity kept, not a tombstone
                assertEquals(MfgConstants.BOM_SAME, row.changeId);
                assertEquals(1.0, row.qty, 0.0001);
                assertEquals(MfgConstants.BOM_SOURCE_MANUAL, row.sourceId);
            }
        }
        assertEquals(1, adapters);
    }

    private static Map<String, Object> option(String code, String name) {
        Map<String, Object> option = new LinkedHashMap<>();
        option.put("componentCode", code);
        option.put("componentName", name);
        option.put("onHandQty", Double.valueOf(3.0));
        return option;
    }
}
