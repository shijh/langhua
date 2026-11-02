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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
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

    @Test
    public void keepsTheImportedFileWithTheVersion() {
        // the rows are what the pages work with, but "why did this version look like that" is answered
        // by the file the sketch exported; it travels as base64 through the service context
        byte[] html = "<html><body>Bill of Materials</body></html>".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        String encoded = java.util.Base64.getEncoder().encodeToString(html);
        byte[] back = MfgDevBomServices.sourceBytesOf(encoded);
        assertTrue(back != null && back.length == html.length);
        assertEquals("<html><body>Bill of Materials</body></html>",
                new String(back, java.nio.charset.StandardCharsets.UTF_8));
        // a pasted import has no file, and a form that lost a character must not lose the import
        assertNull(MfgDevBomServices.sourceBytesOf(null));
        assertNull(MfgDevBomServices.sourceBytesOf("   "));
        assertNull(MfgDevBomServices.sourceBytesOf("*** not base64 ***"));
    }

    private static Map<String, Object> option(String code, String name) {
        Map<String, Object> option = new LinkedHashMap<>();
        option.put("componentCode", code);
        option.put("componentName", name);
        option.put("onHandQty", Double.valueOf(3.0));
        return option;
    }

    @Test
    public void addsUpTheRequirementOfAPartTheReportPrintsTwice() {
        // the same part on two lines of the report is one line to pick, not two
        List<Map<String, Object>> items = new ArrayList<>();
        items.add(bomRow("C-100NF-16V-0603", "100nF 16V 0603", 2.0, "C1", MfgConstants.BOM_SAME));
        items.add(bomRow("c-100nf-16v-0603", "100nF 16V 0603", 3.0, "C7", MfgConstants.BOM_SAME));
        items.add(bomRow("R-10K-0603", "10k 0603", 1.0, null, MfgConstants.BOM_SAME));

        List<Map<String, Object>> needs = MfgDevBomServices.requirementRows(items);
        assertEquals(2, needs.size());
        // the first spelling of the part number is the one written down, and case does not split it
        assertEquals("C-100NF-16V-0603", needs.get(0).get("componentCode"));
        assertEquals(5.0, number(needs.get(0).get("neededQty")), 0.0001);
        assertEquals("C1,C7", needs.get(0).get("designators"));
        assertEquals(1.0, number(needs.get(1).get("neededQty")), 0.0001);
        assertNull(needs.get(1).get("designators"));
    }

    @Test
    public void leavesDroppedPartsAndEmptyLinesOutOfTheRequirement() {
        List<Map<String, Object>> items = new ArrayList<>();
        items.add(bomRow("OLD-1", "gone", 0.0, null, MfgConstants.BOM_DROPPED));
        items.add(bomRow("ZERO-1", "nobody counts it", 0.0, null, MfgConstants.BOM_SAME));
        items.add(bomRow("R-10K-0603", "10k 0603", 2.0, "R1", MfgConstants.BOM_NEW));

        List<Map<String, Object>> needs = MfgDevBomServices.requirementRows(items);
        assertEquals(1, needs.size());
        assertEquals("R-10K-0603", needs.get(0).get("componentCode"));
    }

    @Test
    public void asksOnlyForWhatTheProjectHasNotPickedYet() {
        // the second draft of a board: the first document already took what it took
        List<Map<String, Object>> needs = new ArrayList<>();
        needs.add(need("C-100NF-16V-0603", 20.0));
        needs.add(need("NFC COIL", 1.0));
        needs.add(need("LED-0805", 2.0));
        Map<String, Double> picked = new LinkedHashMap<>();
        picked.put("C-100NF-16V-0603", Double.valueOf(10.0));
        picked.put("NFC COIL", Double.valueOf(1.0));

        List<Map<String, Object>> rows = MfgDevBomServices.outstandingRows(needs, picked);
        assertEquals(2, rows.size());
        // what the version needs and what is left to pick are both kept: the page shows three numbers
        assertEquals("C-100NF-16V-0603", rows.get(0).get("componentCode"));
        assertEquals(20.0, number(rows.get(0).get("neededQty")), 0.0001);
        assertEquals(10.0, number(rows.get(0).get("alreadyQty")), 0.0001);
        assertEquals(10.0, number(rows.get(0).get("qty")), 0.0001);
        // a part the version wants no more of does not come back to the shelf
        assertEquals("LED-0805", rows.get(1).get("componentCode"));
        assertEquals(2.0, number(rows.get(1).get("qty")), 0.0001);
    }

    @Test
    public void comparesThePartNumbersWhateverTheCaseOfTheRows() {
        List<Map<String, Object>> needs = new ArrayList<>();
        needs.add(need("nfc coil", 1.0));
        Map<String, Double> picked = new LinkedHashMap<>();
        picked.put(MfgDevBomServices.upperCode("NFC Coil"), Double.valueOf(1.0));

        assertTrue(MfgDevBomServices.outstandingRows(needs, picked).isEmpty());
    }

    @Test
    public void booksNothingBackWhenTheVersionNeedsLessThanWasPicked() {
        // a line that shrank is a part that was used on the board, not a part to put back
        List<Map<String, Object>> needs = new ArrayList<>();
        needs.add(need("C-100NF-16V-0603", 5.0));
        Map<String, Double> picked = new LinkedHashMap<>();
        picked.put("C-100NF-16V-0603", Double.valueOf(10.0));

        assertTrue(MfgDevBomServices.outstandingRows(needs, picked).isEmpty());
    }

    @Test
    public void asksAgainForEverythingWhenNoDocumentWasIssued() {
        // a document that was cancelled or refused never left the shelf, so its parts are asked for
        // again: the picked quantities come from the issued documents only
        List<Map<String, Object>> needs = new ArrayList<>();
        needs.add(need("C-100NF-16V-0603", 3.0));

        List<Map<String, Object>> rows = MfgDevBomServices.outstandingRows(needs, new LinkedHashMap<>());
        assertEquals(1, rows.size());
        assertEquals(3.0, number(rows.get(0).get("qty")), 0.0001);
        assertEquals(0.0, number(rows.get(0).get("alreadyQty")), 0.0001);
        // and a project with no document at all takes the whole requirement
        assertEquals(1, MfgDevBomServices.outstandingRows(needs, null).size());
    }

    @Test
    public void dropsTheCancellationFromTheNoteOfAnOldDocument() {
        // documents dropped before the reason got its own field carry "；已作废：<原因>" in the note,
        // which repeated the state column and printed whatever the caller typed (our own check wrote
        // "verify"). It is cut off when the row is read; the note of the version stays.
        assertEquals("按研发 BOM v94 生成",
                MfgDevBomServices.noteWithoutCancellation("按研发 BOM v94 生成；已作废：verify"));
        assertEquals("按研发 BOM v94 生成；已于 16 件出库",
                MfgDevBomServices.noteWithoutCancellation("按研发 BOM v94 生成；已于 16 件出库"));
        assertNull(MfgDevBomServices.noteWithoutCancellation("已作废：verify"));
        assertNull(MfgDevBomServices.noteWithoutCancellation(null));
        assertEquals("", MfgDevBomServices.noteWithoutCancellation(""));
    }

    @Test
    public void keepsTheReceiverNameInsideItsColumn() {
        // the name of whoever carries the parts away is free text (an engineer, a fitter), stored in
        // a 100 character column: a longer one has to be refused with a sentence, not with an SQL error
        assertNull(MfgDevBomServices.receiverProblem(null));
        assertNull(MfgDevBomServices.receiverProblem("李四"));
        StringBuilder name = new StringBuilder();
        for (int i = 0; i < 100; i++) {
            name.append('x');
        }
        assertNull(MfgDevBomServices.receiverProblem(name.toString()));
        name.append('x');
        assertNotNull(MfgDevBomServices.receiverProblem(name.toString()));
    }

    @Test
    public void keepsTheBoxCountOfAPackedOrderSensible() {
        // packing is optional (no number means one box), but zero boxes or a thousand are typos
        assertNull(MfgDevBomServices.packingProblem(null));
        assertNull(MfgDevBomServices.packingProblem(Double.valueOf(1.0)));
        assertNull(MfgDevBomServices.packingProblem(Double.valueOf(999.0)));
        assertNotNull(MfgDevBomServices.packingProblem(Double.valueOf(0.0)));
        assertNotNull(MfgDevBomServices.packingProblem(Double.valueOf(1000.0)));
    }

    @Test
    public void givesEveryDocumentItsOwnCheckKey() {
        // the check page lets a phone open a document without a login, so the address carries a key:
        // one document always gets the same twelve characters, two documents never share one
        String one = MfgDevBomServices.checkKeyOf("OUT-261101-01");
        assertEquals(12, one.length());
        assertEquals(one, MfgDevBomServices.checkKeyOf("OUT-261101-01"));
        assertFalse(one.equals(MfgDevBomServices.checkKeyOf("OUT-261101-02")));
        assertEquals("", MfgDevBomServices.checkKeyOf(null));
    }

    @Test
    public void writesThePickingListIntoTheLabelCode() {
        // the QR code carries what is in the box - the names and the quantities - not a web address:
        // the sticker is read in a workshop where the phone may have no way back to the server
        List<Map<String, Object>> lines = new ArrayList<>();
        lines.add(line("NFC COIL", "C-1", 1.0));
        lines.add(line("Capacitor 0603", "C-2", 4.0));

        String text = MfgDevBomServices.labelTextOf("OUT-261101-01", "DEV-261031-04", "v94", lines);
        assertTrue(text.contains("OUT-261101-01"));
        assertTrue(text.contains("DEV-261031-04 v94"));
        assertTrue(text.contains("NFC COIL x1"));
        assertTrue(text.contains("Capacitor 0603 x4"));
        assertTrue(text.contains("2 行 / 共 5 件"));
        assertFalse(text.contains("http"));
    }

    @Test
    public void keepsTheLabelCodeSmallEnoughToScan() {
        // a long picking list still has to fit one code that prints 38 mm wide: names are cut short,
        // and when even that is not enough the part numbers stand in for them
        List<Map<String, Object>> lines = new ArrayList<>();
        for (int i = 0; i < 40; i++) {
            lines.add(line("QFN16 0.65MM/QFN20 0.4MM间距转 2.54MM孔距 转接板转直插 测试板 " + i, "P-" + i, 2.0));
        }
        String text = MfgDevBomServices.labelTextOf("OUT-261101-01", "DEV-261031-04", "v94", lines);
        assertTrue(text.length() > 0);
        assertTrue(text.getBytes(java.nio.charset.StandardCharsets.UTF_8).length
                <= MfgDevBomServices.MAX_LABEL_BYTES);
        // the summary always says how many lines and how many parts, even when the detail is cut
        assertTrue(text.contains("40 行 / 共 80 件"));
    }

    @Test
    public void cutsTheListShorterWhenTheStickerGetsSmaller() {
        // 300 bytes needs a version 11 code (61 modules plus the quiet zone), which prints 28 mm wide
        // at 0.41 mm per module - a sticker that carries less text can be much smaller
        List<Map<String, Object>> lines = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            lines.add(line("Capacitor 0603 100NF 50V X7R", "C-" + i, 2.0));
        }
        String big = MfgDevBomServices.labelTextOf("OUT-261101-01", "DEV-261031-04", "v94", lines,
                MfgDevBomServices.MAX_LABEL_BYTES);
        String smaller = MfgDevBomServices.labelTextOf("OUT-261101-01", "DEV-261031-04", "v94", lines,
                MfgDevBomServices.LABEL_BYTES_MID);
        assertTrue(big.getBytes(java.nio.charset.StandardCharsets.UTF_8).length
                <= MfgDevBomServices.MAX_LABEL_BYTES);
        assertTrue(smaller.getBytes(java.nio.charset.StandardCharsets.UTF_8).length
                <= MfgDevBomServices.LABEL_BYTES_MID);
        assertTrue(smaller.getBytes(java.nio.charset.StandardCharsets.UTF_8).length
                < big.getBytes(java.nio.charset.StandardCharsets.UTF_8).length);
        // whatever gets cut, the number of the document and the summary stay
        assertTrue(smaller.contains("OUT-261101-01"));
        assertTrue(smaller.contains("12 行 / 共 24 件"));
        // and a list that fits is not announced as cut ("另有 0 行" would be a lie)
        assertFalse(smaller.contains("另有 0 行"));
    }

    @Test
    public void addsTheProjectNameToTheMiddleSticker() {
        // every sticker prints the same two things - the code and the project - so the middle code
        // carries the number, the project and the counts, and nothing else
        String text = MfgDevBomServices.shortLabelTextOf("OUT-261101-01", "NFC 门锁控制板", 13, 16.0);
        assertTrue(text.startsWith("OUT-261101-01"));
        assertTrue(text.contains("NFC 门锁控制板"));
        assertTrue(text.contains("13行/16件"));
        assertFalse(text.contains("http"));
        assertTrue(text.getBytes(java.nio.charset.StandardCharsets.UTF_8).length
                <= MfgDevBomServices.LABEL_BYTES_MID);
        // a name longer than the code can hold is cut, not dropped
        String longName = MfgDevBomServices.shortLabelTextOf("OUT-1",
                "一个名字特别特别长的开发项目名称用来测试截断", 2, 3.0);
        assertTrue(longName.contains("…"));
        assertTrue(longName.getBytes(java.nio.charset.StandardCharsets.UTF_8).length
                <= MfgDevBomServices.LABEL_BYTES_MID);
        // a project without a name still gives a code that says which document it is
        assertTrue(MfgDevBomServices.shortLabelTextOf("OUT-1", null, 1, 1.0).contains("OUT-1"));
    }

    @Test
    public void writesOnlyTheNumberAndTheCountsOnAVerySmallSticker() {
        // 15 mm of tape holds a 13 mm code, and thirteen lines would not be readable in it: the small
        // label says which document it is and how much is in the box, no more
        String text = MfgDevBomServices.compactLabelTextOf("OUT-261101-01", 13, 16.0);
        assertTrue(text.startsWith("OUT-261101-01"));
        assertTrue(text.contains("13行/16件"));
        assertFalse(text.contains("http"));
        assertTrue(text.getBytes(java.nio.charset.StandardCharsets.UTF_8).length <= 40);
        // whole numbers stay whole, a half part keeps its half
        assertTrue(MfgDevBomServices.compactLabelTextOf("OUT-1", 1, 2.5).contains("1行/2.5件"));
    }

    @Test
    public void takesBothTheLockAndThePickedQuantityOffTheRequirement() {
        // one downloader is needed, none is locked (the outbound document released the lock) and one
        // was already picked: nothing is missing, the part is in the engineer's drawer
        assertEquals(0.0, MfgDevBomServices.shortQtyOf(1.0, 0.0, 1.0), 1e-9);
        // part locked, nothing picked yet: one still to buy
        assertEquals(1.0, MfgDevBomServices.shortQtyOf(2.0, 1.0, 0.0), 1e-9);
        // both count, a half part keeps its half
        assertEquals(0.5, MfgDevBomServices.shortQtyOf(3.0, 1.5, 1.0), 1e-9);
        // never below zero, even when more was picked than this version needs
        assertEquals(0.0, MfgDevBomServices.shortQtyOf(1.0, 2.0, 3.0), 1e-9);
    }

    private static Map<String, Object> line(String name, String code, double qty) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("componentName", name);
        row.put("componentCode", code);
        row.put("qty", Double.valueOf(qty));
        return row;
    }

    private static Map<String, Object> bomRow(String code, String name, double qty, String designators,
            String changeId) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("componentCode", code);
        row.put("componentName", name);
        row.put("qty", Double.valueOf(qty));
        row.put("designators", designators);
        row.put("changeId", changeId);
        return row;
    }

    private static Map<String, Object> need(String code, double needed) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("componentCode", code);
        row.put("componentName", code);
        row.put("neededQty", Double.valueOf(needed));
        return row;
    }

    private static double number(Object value) {
        return value == null ? 0.0 : ((Number) value).doubleValue();
    }
}
