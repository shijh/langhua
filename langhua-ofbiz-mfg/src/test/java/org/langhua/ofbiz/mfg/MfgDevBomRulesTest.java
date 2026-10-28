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

    private static String[] row(String code, String name, String qty, String designators) {
        return new String[] {code, name, qty, designators, "N", null, null};
    }
}
