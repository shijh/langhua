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

import org.langhua.ofbiz.mfg.oir.OirOrders;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.junit.Test;

/**
 * The pure helpers of the import and the list pages: CSV and BOM parsing, the identifier formats,
 * the paging arithmetic, the receipt summary and the order line parsing.
 */
public class MfgFormatRulesTest {

    private static java.util.Map<String, Object> row(String keyOne, String valueOne, String keyTwo,
            String valueTwo) {
        java.util.Map<String, Object> row = new java.util.LinkedHashMap<>();
        row.put(keyOne, valueOne);
        row.put(keyTwo, valueTwo);
        return row;
    }

    private static Map<String, Object> receiptLine(double goodsAmount) {
        Map<String, Object> line = new java.util.LinkedHashMap<>();
        line.put("goodsAmount", Double.valueOf(goodsAmount));
        return line;
    }

    @Test
    public void formatsSerialNumber() {
        LocalDate day = LocalDate.of(2026, 10, 20);
        assertEquals("BT5-261020-0001", MfgWorkOrderServices.formatSn("BT5", day, 1));
        assertEquals("BT5-261020-0042", MfgWorkOrderServices.formatSn("BT5", day, 42));
        assertEquals("A-261220-1234", MfgWorkOrderServices.formatSn("A", LocalDate.of(2026, 12, 20), 1234));
    }

    @Test
    public void formatsWorkOrderId() {
        LocalDate day = LocalDate.of(2026, 10, 20);
        assertEquals("WO-261020-001", MfgWorkOrderServices.formatWorkOrderId(day, 1));
        assertEquals("WO-261020-017", MfgWorkOrderServices.formatWorkOrderId(day, 17));
    }

    @Test
    public void parsesBomCsv() {
        String csv = "r0402-10k,10K 电阻,4,\"R1-R4\",N\n"
                + "# a comment line\n"
                + "\n"
                + "U1-BT5-SOC,主控 SoC,1,U1,y\n";
        List<String[]> rows = MfgCsv.parseBomCsv(csv);

        assertEquals(2, rows.size());
        assertEquals("R0402-10K", rows.get(0)[0]);
        assertEquals("10K 电阻", rows.get(0)[1]);
        assertEquals("4", rows.get(0)[2]);
        assertEquals("R1-R4", rows.get(0)[3]);
        assertEquals("N", rows.get(0)[4]);
        assertEquals("U1-BT5-SOC", rows.get(1)[0]);
        assertEquals("y", rows.get(1)[4]);
    }

    @Test
    public void parsesBomCsvWithMissingColumns() {
        List<String[]> rows = MfgCsv.parseBomCsv("C0402-100N\n");

        assertEquals(1, rows.size());
        assertEquals("C0402-100N", rows.get(0)[0]);
        assertNull(rows.get(0)[1]);
        assertEquals("1", rows.get(0)[2]);
        assertNull(rows.get(0)[3]);
        assertEquals("N", rows.get(0)[4]);
    }

    @Test
    public void parsesEmptyCsvAsEmptyList() {
        assertTrue(MfgCsv.parseBomCsv(null).isEmpty());
        assertTrue(MfgCsv.parseBomCsv("   \n\n").isEmpty());
    }

    @Test
    public void keepsQuotedCellContent() {
        List<String[]> rows = MfgCsv.parseBomCsv("\"R1,R2\",\"1/4W 电阻\",2,R1 R2,Y");

        assertEquals(1, rows.size());
        assertEquals("R1,R2", rows.get(0)[0]);
        assertEquals("1/4W 电阻", rows.get(0)[1]);
        assertEquals("2", rows.get(0)[2]);
        assertEquals("R1 R2", rows.get(0)[3]);
        assertEquals("Y", rows.get(0)[4]);
    }

    @Test
    public void splitsCsvLineWithEscapedQuotes() {
        String[] cells = MfgCsv.splitCsvLine("a,\"b\"\"c\",d");

        assertEquals(3, cells.length);
        assertEquals("a", cells[0]);
        assertEquals("b\"c", cells[1]);
        assertEquals("d", cells[2]);
    }

    @Test
    public void acceptsTabsSoASpreadsheetRangeCanBePasted() {
        // a range copied out of Excel is tab separated; both import boxes take it as it comes
        List<String[]> bom = MfgCsv.parseBomCsv("R0402-10K\t10K 电阻\t4\tR1-R4\tN\t0.05");
        assertEquals(1, bom.size());
        assertEquals("R0402-10K", bom.get(0)[0]);
        assertEquals("10K 电阻", bom.get(0)[1]);
        assertEquals("4", bom.get(0)[2]);
        assertEquals("0.05", bom.get(0)[5]);

        List<String[]> lots = MfgCsv.parseLotCsv("U1-BT5-SOC\t主控 SoC\t立创商城\tDC2410A\t120\t2026-10-20");
        assertEquals(1, lots.size());
        assertEquals("U1-BT5-SOC", lots.get(0)[0]);
        assertEquals("120", lots.get(0)[4]);
        assertEquals("2026-10-20", lots.get(0)[5]);

        String[] cells = MfgCsv.splitCsvLine("a\t\"b,c\"\td");
        assertEquals(3, cells.length);
        assertEquals("b,c", cells[1]);
    }

    @Test
    public void ignoresTheByteOrderMarkOfAnExcelUtf8File() {
        // "CSV UTF-8" saves a BOM in front of the first cell, which must not end up in the part number
        List<String[]> bom = MfgCsv.parseBomCsv("\uFEFFR0402-10K,10K 电阻,4,R1-R4,N,0.05");
        assertEquals(1, bom.size());
        assertEquals("R0402-10K", bom.get(0)[0]);

        List<String[]> lots = MfgCsv.parseLotCsv("\uFEFFU1-BT5-SOC,主控 SoC,立创商城,DC2410A,120,2026-10-20");
        assertEquals(1, lots.size());
        assertEquals("U1-BT5-SOC", lots.get(0)[0]);
    }

    @Test
    public void parsesCrlfAndUppercasesComponentCode() {
        List<String[]> rows = MfgCsv.parseBomCsv(
                "r0402-10k,10K 电阻,4,\"R1,R2\",y\r\nb-conn,电池座,1,BAT1,n\r\n");

        assertEquals(2, rows.size());
        assertEquals("R0402-10K", rows.get(0)[0]);
        assertEquals("R1,R2", rows.get(0)[3]);
        assertEquals("y", rows.get(0)[4]);
        assertEquals("B-CONN", rows.get(1)[0]);
        assertEquals("电池座", rows.get(1)[1]);
        assertEquals("n", rows.get(1)[4]);
    }

    @Test
    public void formatsNumbersAtTheEndOfTheDay() {
        LocalDate day = LocalDate.of(2026, 10, 20);

        assertEquals("BT5-261020-9999", MfgWorkOrderServices.formatSn("BT5", day, 9999));
        assertEquals("WO-261020-999", MfgWorkOrderServices.formatWorkOrderId(day, 999));
    }

    @Test
    public void parsesLotCsv() {
        String csv = "u1-bt5-soc,主控 SoC,立创商城,DC2410A,120,2026-10-20\n"
                + "# a comment line\n"
                + "BAT-18650,电池,某电池厂,2026W40,50\n";

        List<String[]> rows = MfgCsv.parseLotCsv(csv);

        assertEquals(2, rows.size());
        assertEquals("U1-BT5-SOC", rows.get(0)[0]);
        assertEquals("主控 SoC", rows.get(0)[1]);
        assertEquals("立创商城", rows.get(0)[2]);
        assertEquals("DC2410A", rows.get(0)[3]);
        assertEquals("120", rows.get(0)[4]);
        assertEquals("2026-10-20", rows.get(0)[5]);
        assertEquals("BAT-18650", rows.get(1)[0]);
        assertNull(rows.get(1)[5]);
    }

    @Test
    public void parsesEmptyLotCsvAsEmptyList() {
        assertTrue(MfgCsv.parseLotCsv(null).isEmpty());
        assertTrue(MfgCsv.parseLotCsv("   \n\r\n").isEmpty());
    }

    @Test
    public void splitsSerialNumbersFirstInFirstOut() {
        List<String> sns = List.of("A-0001", "A-0002", "A-0003", "A-0004");
        List<Double> quantities = List.of(1.0, 3.0);

        int[] firstIssue = MfgWorkOrderServices.fifoSlice(sns, quantities, 0);
        assertEquals(0, firstIssue[0]);
        assertEquals(0, firstIssue[1]);

        int[] secondIssue = MfgWorkOrderServices.fifoSlice(sns, quantities, 1);
        assertEquals(1, secondIssue[0]);
        assertEquals(3, secondIssue[1]);
    }

    @Test
    public void refusesToSplitWithoutUsableInput() {
        assertNull(MfgWorkOrderServices.fifoSlice(List.of(), List.of(1.0), 0));
        assertNull(MfgWorkOrderServices.fifoSlice(List.of("A-0001"), List.of(0.0), 0));
        assertNull(MfgWorkOrderServices.fifoSlice(List.of("A-0001"), List.of(1.0), 5));
    }

    @Test
    public void splitsSerialNumberLists() {
        List<String> sns = MfgCsv.parseSnList("SN-1, SN-2\nSN-3\tSN-1");

        assertEquals(3, sns.size());
        assertEquals("SN-1", sns.get(0));
        assertEquals("SN-2", sns.get(1));
        assertEquals("SN-3", sns.get(2));
        assertTrue(MfgCsv.parseSnList(null).isEmpty());
        assertTrue(MfgCsv.parseSnList("  ,  \n").isEmpty());
    }

    @Test
    public void parsesTestResultCsv() {
        String csv = "ITMFG-261021-0001,FCT,VBAT,3.72,V,Y\n"
                + "# comment\n"
                + "ITMFG-261021-0002,AGEING,CURRENT,12.5,mA,N\n";

        List<String[]> rows = MfgCsv.parseTestCsv(csv);

        assertEquals(2, rows.size());
        assertEquals("ITMFG-261021-0001", rows.get(0)[0]);
        assertEquals("FCT", rows.get(0)[1]);
        assertEquals("VBAT", rows.get(0)[2]);
        assertEquals("3.72", rows.get(0)[3]);
        assertEquals("V", rows.get(0)[4]);
        assertEquals("Y", rows.get(0)[5]);
        assertEquals("N", rows.get(1)[5]);
        assertTrue(MfgCsv.parseTestCsv(null).isEmpty());
    }

    @Test
    public void formatsTheShippingLabel() {
        String label = MfgWorkOrderServices.formatShippingLabel("深圳某电子", "张工", "13800001111",
                "广东省深圳市宝安区某路 1 号", "LH-2026-01");

        assertTrue(label.contains("收件人：张工（深圳某电子）"));
        assertTrue(label.contains("手机号：13800001111"));
        assertTrue(label.contains("地址：广东省深圳市宝安区某路 1 号"));
        assertTrue(label.contains("客户编号：LH-2026-01"));
    }

    @Test
    public void omitsTheCustomerCodeWhenMissing() {
        String label = MfgWorkOrderServices.formatShippingLabel("某厂", null, null, null, null);

        assertTrue(label.contains("收件人：（某厂）"));
        assertTrue(!label.contains("客户编号"));
    }

    @Test
    public void filtersRowsByKeyword() {
        java.util.List<java.util.Map<String, Object>> rows = new java.util.ArrayList<>();
        rows.add(row("workOrderId", "WO-261021-001", "customerName", "ACME"));
        rows.add(row("workOrderId", "WO-261021-002", "customerName", "Beta"));
        rows.add(row("workOrderId", "WO-261021-003", "customerName", "acme labs"));

        assertEquals(2, MfgPaging.filterByKeyword(rows, "acme", "customerName").size());
        assertEquals(1, MfgPaging.filterByKeyword(rows, "002", "workOrderId").size());
        // no keyword means no filtering
        assertEquals(3, MfgPaging.filterByKeyword(rows, "  ", "customerName").size());
        assertEquals(3, MfgPaging.filterByKeyword(rows, null, "customerName").size());
        assertEquals(0, MfgPaging.filterByKeyword(rows, "zzz", "customerName").size());
    }

    @Test
    public void splitsRowsIntoTewntyRowPages() {
        assertEquals(1L, MfgPaging.pageCount(0, 20));
        assertEquals(1L, MfgPaging.pageCount(20, 20));
        assertEquals(2L, MfgPaging.pageCount(21, 20));
        assertEquals(3L, MfgPaging.pageCount(60, 20));

        // an out of range page falls back to the closest available one
        assertEquals(3L, MfgPaging.pageNumber(99, 60, 20));
        assertEquals(1L, MfgPaging.pageNumber(0, 60, 20));
        assertEquals(2L, MfgPaging.pageNumber(2, 60, 20));

        java.util.List<String> rows = new java.util.ArrayList<>();
        for (int i = 0; i < 45; i++) {
            rows.add("row" + i);
        }
        assertEquals(20, MfgPaging.pageSlice(rows, 1, 20).size());
        assertEquals("row20", MfgPaging.pageSlice(rows, 2, 20).get(0));
        assertEquals(5, MfgPaging.pageSlice(rows, 3, 20).size());
        assertEquals("row40", MfgPaging.pageSlice(rows, 9, 20).get(0));
        assertEquals(0, MfgPaging.pageSlice(new java.util.ArrayList<String>(), 1, 20).size());
    }

    @Test
    public void formatsBomVersionId() {
        assertEquals("BT5-V1", MfgProductServices.formatBomVersionId("BT5", 1));
        assertEquals("BT5-V12", MfgProductServices.formatBomVersionId("BT5", 12));
    }

    @Test
    public void shortensLongModelCodesInTheBomVersionId() {
        // a model code may use the whole id column, the version number still has to fit
        String longCode = "orpah-client-demo-bb";
        assertEquals(20, longCode.length());
        assertEquals("orpah-client-demo-V1", MfgProductServices.formatBomVersionId(longCode, 1));
        assertEquals(20, MfgProductServices.formatBomVersionId(longCode, 1).length());
        assertEquals(20, MfgProductServices.formatBomVersionId(longCode, 123).length());
        assertTrue(MfgProductServices.formatBomVersionId(longCode, 123).endsWith("-V123"));
        // short codes are untouched
        assertEquals("BT5-V1", MfgProductServices.formatBomVersionId("BT5", 1));
    }

    @Test
    public void numbersTheNextBomVersionOfALongModelCode() {
        String longCode = "orpah-client-demo-bb";
        List<String> ids = java.util.Arrays.asList(
                MfgProductServices.formatBomVersionId(longCode, 1),
                MfgProductServices.formatBomVersionId(longCode, 2));
        assertEquals(3L, MfgProductServices.nextBomVersionNo(ids, longCode));
    }

    @Test
    public void sumsAPurchaseReceipt() {
        List<Map<String, Object>> lines = new java.util.ArrayList<>();
        lines.add(receiptLine(2.0));
        lines.add(receiptLine(60.0));

        Map<String, Object> summary = MfgPurchaseServices.receiptSummary(lines, 8.0);
        assertEquals(2, summary.get("lineCount"));
        assertEquals(62.0, ((Double) summary.get("goodsTotal")).doubleValue(), 0.001);
        assertEquals(8.0, ((Double) summary.get("freightTotal")).doubleValue(), 0.001);
        // the freight is a period cost: it is added to the order, never to a line
        assertEquals(70.0, ((Double) summary.get("spendTotal")).doubleValue(), 0.001);
        assertEquals(12.9, ((Double) summary.get("freightShare")).doubleValue(), 0.001);

        // a receipt without lines must not divide by zero
        Map<String, Object> empty = MfgPurchaseServices.receiptSummary(new java.util.ArrayList<Map<String, Object>>(), 8.0);
        assertEquals(0.0, ((Double) empty.get("freightShare")).doubleValue(), 0.001);
        assertEquals(8.0, ((Double) empty.get("spendTotal")).doubleValue(), 0.001);
    }

    @Test
    public void takesTheArrivalDayAsTheOrderDayPlusThree() {
        java.sql.Date today = java.sql.Date.valueOf(LocalDate.of(2026, 10, 30));
        // an order placed in 2018 arrives three days after it was placed, not today: otherwise the
        // old order lands in this month's stock and in this month's freight report
        assertEquals("2018-12-04", MfgSupport.arrivalDateOf(
                java.sql.Date.valueOf(LocalDate.of(2018, 12, 1)), today, 3).toString());
        // an order placed today has just arrived: the arrival day may never be in the future
        assertEquals("2026-10-30", MfgSupport.arrivalDateOf(today, today, 3).toString());
        assertEquals("2026-10-30", MfgSupport.arrivalDateOf(
                java.sql.Date.valueOf(LocalDate.of(2026, 10, 29)), today, 3).toString());
        // without an order day there is nothing to count from
        assertEquals("2026-10-30", MfgSupport.arrivalDateOf(null, today, 3).toString());
    }

    @Test
    public void parsesTheLinesOfAShopOrder() {
        List<String[]> rows = OirOrders.parseOrderArticleLines(
                "料号,名称,数量,实付金额\n"
                + "CAP-104-50V,瓷片电容器 104 0.1uF 100NF 50V,100,2.91\n"
                + "\n"
                + "# 这一行是注释\n"
                + "R-0402-10K\t10K 电阻 0402\t100\t1.50\n");
        // the header, the blank line and the comment are skipped, comma and tab both work
        assertEquals(2, rows.size());
        assertEquals("CAP-104-50V", rows.get(0)[0]);
        assertEquals("100", rows.get(0)[2]);
        assertEquals("2.91", rows.get(0)[3]);
        assertEquals("R-0402-10K", rows.get(1)[0]);
        assertEquals("1.50", rows.get(1)[3]);

        // a line that is not four columns is ignored instead of being half imported
        assertEquals(0, OirOrders.parseOrderArticleLines("ONLY-A-CODE").size());
        assertEquals(0, OirOrders.parseOrderArticleLines("").size());
        assertEquals(0, OirOrders.parseOrderArticleLines(null).size());
    }

    @Test
    public void costsACheapPartFromTheAmountPaid() {
        // 100 pieces for 2.91: the unit price column would keep 0.03, the amount keeps the cent
        assertEquals(0.0291, MfgStockLedger.unitCostOf(100.0, 2.91, 0.03), 0.000001);
        // without an amount the typed in price is used
        assertEquals(0.03, MfgStockLedger.unitCostOf(100.0, 0.0, 0.03), 0.000001);
        // a lot without quantity cannot divide
        assertEquals(1.25, MfgStockLedger.unitCostOf(0.0, 2.91, 1.25), 0.000001);
    }

    @Test
    public void quotesCsvCellsThatWouldBreakTheLine() {
        assertEquals("B5X", MfgCsv.csvCell("B5X"));
        assertEquals("", MfgCsv.csvCell(null));
        // a comma, a quote and a line break all force the quotes, and quotes are doubled
        assertEquals("\"a,b\"", MfgCsv.csvCell("a,b"));
        assertEquals("\"say \"\"hi\"\"\"", MfgCsv.csvCell("say \"hi\""));
        assertEquals("\"two\nlines\"", MfgCsv.csvCell("two\nlines"));
        assertEquals("B5X,12,0.5", MfgCsv.csvLine(
                java.util.Arrays.asList("B5X", "12", "0.5")));
        // null cells become empty cells, so the columns stay aligned
        assertEquals("B5X,,12", MfgCsv.csvLine(
                java.util.Arrays.asList("B5X", null, "12")));
    }

    @Test
    public void formatsQuantitiesForTheAlertTexts() {
        assertEquals("0", MfgSupport.formatQty(0.0));
        assertEquals("12", MfgSupport.formatQty(12.0));
        assertEquals("12.5", MfgSupport.formatQty(12.5));
        assertEquals("1.234", MfgSupport.formatQty(1.23449));
    }
}
