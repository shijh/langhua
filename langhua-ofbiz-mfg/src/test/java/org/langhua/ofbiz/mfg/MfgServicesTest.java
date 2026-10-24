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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;

/**
 * Unit tests of the pure helper functions of {@link MfgServices}.
 *
 * <p>They cover the parts that do not need a database: BOM CSV parsing, the serial
 * number and work order number formats.
 */
public class MfgServicesTest {

    /**
     * The first order of a vision answer, {@code null} when there is none. The scan only ever uses
     * the list, so the tests read the first element the same way the event does.
     *
     * @param reply the answer text
     * @return the first order, {@code null} when the answer carries none
     */
    private static Map<String, String> oneOrder(String reply) {
        List<Map<String, String>> orders = MfgServices.orderListFrom(reply);
        return orders.isEmpty() ? null : orders.get(0);
    }

    @Test
    public void formatsSerialNumber() {
        LocalDate day = LocalDate.of(2026, 10, 20);
        assertEquals("BT5-261020-0001", MfgServices.formatSn("BT5", day, 1));
        assertEquals("BT5-261020-0042", MfgServices.formatSn("BT5", day, 42));
        assertEquals("A-261220-1234", MfgServices.formatSn("A", LocalDate.of(2026, 12, 20), 1234));
    }

    @Test
    public void formatsWorkOrderId() {
        LocalDate day = LocalDate.of(2026, 10, 20);
        assertEquals("WO-261020-001", MfgServices.formatWorkOrderId(day, 1));
        assertEquals("WO-261020-017", MfgServices.formatWorkOrderId(day, 17));
    }

    @Test
    public void parsesBomCsv() {
        String csv = "r0402-10k,10K 电阻,4,\"R1-R4\",N\n"
                + "# a comment line\n"
                + "\n"
                + "U1-BT5-SOC,主控 SoC,1,U1,y\n";
        List<String[]> rows = MfgServices.parseBomCsv(csv);

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
        List<String[]> rows = MfgServices.parseBomCsv("C0402-100N\n");

        assertEquals(1, rows.size());
        assertEquals("C0402-100N", rows.get(0)[0]);
        assertNull(rows.get(0)[1]);
        assertEquals("1", rows.get(0)[2]);
        assertNull(rows.get(0)[3]);
        assertEquals("N", rows.get(0)[4]);
    }

    @Test
    public void parsesEmptyCsvAsEmptyList() {
        assertTrue(MfgServices.parseBomCsv(null).isEmpty());
        assertTrue(MfgServices.parseBomCsv("   \n\n").isEmpty());
    }

    @Test
    public void keepsQuotedCellContent() {
        List<String[]> rows = MfgServices.parseBomCsv("\"R1,R2\",\"1/4W 电阻\",2,R1 R2,Y");

        assertEquals(1, rows.size());
        assertEquals("R1,R2", rows.get(0)[0]);
        assertEquals("1/4W 电阻", rows.get(0)[1]);
        assertEquals("2", rows.get(0)[2]);
        assertEquals("R1 R2", rows.get(0)[3]);
        assertEquals("Y", rows.get(0)[4]);
    }

    @Test
    public void splitsCsvLineWithEscapedQuotes() {
        String[] cells = MfgServices.splitCsvLine("a,\"b\"\"c\",d");

        assertEquals(3, cells.length);
        assertEquals("a", cells[0]);
        assertEquals("b\"c", cells[1]);
        assertEquals("d", cells[2]);
    }

    @Test
    public void acceptsTabsSoASpreadsheetRangeCanBePasted() {
        // a range copied out of Excel is tab separated; both import boxes take it as it comes
        List<String[]> bom = MfgServices.parseBomCsv("R0402-10K\t10K 电阻\t4\tR1-R4\tN\t0.05");
        assertEquals(1, bom.size());
        assertEquals("R0402-10K", bom.get(0)[0]);
        assertEquals("10K 电阻", bom.get(0)[1]);
        assertEquals("4", bom.get(0)[2]);
        assertEquals("0.05", bom.get(0)[5]);

        List<String[]> lots = MfgServices.parseLotCsv("U1-BT5-SOC\t主控 SoC\t立创商城\tDC2410A\t120\t2026-10-20");
        assertEquals(1, lots.size());
        assertEquals("U1-BT5-SOC", lots.get(0)[0]);
        assertEquals("120", lots.get(0)[4]);
        assertEquals("2026-10-20", lots.get(0)[5]);

        String[] cells = MfgServices.splitCsvLine("a\t\"b,c\"\td");
        assertEquals(3, cells.length);
        assertEquals("b,c", cells[1]);
    }

    @Test
    public void ignoresTheByteOrderMarkOfAnExcelUtf8File() {
        // "CSV UTF-8" saves a BOM in front of the first cell, which must not end up in the part number
        List<String[]> bom = MfgServices.parseBomCsv("\uFEFFR0402-10K,10K 电阻,4,R1-R4,N,0.05");
        assertEquals(1, bom.size());
        assertEquals("R0402-10K", bom.get(0)[0]);

        List<String[]> lots = MfgServices.parseLotCsv("\uFEFFU1-BT5-SOC,主控 SoC,立创商城,DC2410A,120,2026-10-20");
        assertEquals(1, lots.size());
        assertEquals("U1-BT5-SOC", lots.get(0)[0]);
    }

    @Test
    public void parsesCrlfAndUppercasesComponentCode() {
        List<String[]> rows = MfgServices.parseBomCsv(
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

        assertEquals("BT5-261020-9999", MfgServices.formatSn("BT5", day, 9999));
        assertEquals("WO-261020-999", MfgServices.formatWorkOrderId(day, 999));
    }

    @Test
    public void parsesLotCsv() {
        String csv = "u1-bt5-soc,主控 SoC,立创商城,DC2410A,120,2026-10-20\n"
                + "# a comment line\n"
                + "BAT-18650,电池,某电池厂,2026W40,50\n";

        List<String[]> rows = MfgServices.parseLotCsv(csv);

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
        assertTrue(MfgServices.parseLotCsv(null).isEmpty());
        assertTrue(MfgServices.parseLotCsv("   \n\r\n").isEmpty());
    }

    @Test
    public void splitsSerialNumbersFirstInFirstOut() {
        List<String> sns = List.of("A-0001", "A-0002", "A-0003", "A-0004");
        List<Double> quantities = List.of(1.0, 3.0);

        int[] firstIssue = MfgServices.fifoSlice(sns, quantities, 0);
        assertEquals(0, firstIssue[0]);
        assertEquals(0, firstIssue[1]);

        int[] secondIssue = MfgServices.fifoSlice(sns, quantities, 1);
        assertEquals(1, secondIssue[0]);
        assertEquals(3, secondIssue[1]);
    }

    @Test
    public void refusesToSplitWithoutUsableInput() {
        assertNull(MfgServices.fifoSlice(List.of(), List.of(1.0), 0));
        assertNull(MfgServices.fifoSlice(List.of("A-0001"), List.of(0.0), 0));
        assertNull(MfgServices.fifoSlice(List.of("A-0001"), List.of(1.0), 5));
    }

    @Test
    public void splitsSerialNumberLists() {
        List<String> sns = MfgServices.parseSnList("SN-1, SN-2\nSN-3\tSN-1");

        assertEquals(3, sns.size());
        assertEquals("SN-1", sns.get(0));
        assertEquals("SN-2", sns.get(1));
        assertEquals("SN-3", sns.get(2));
        assertTrue(MfgServices.parseSnList(null).isEmpty());
        assertTrue(MfgServices.parseSnList("  ,  \n").isEmpty());
    }

    @Test
    public void parsesTestResultCsv() {
        String csv = "ITMFG-261021-0001,FCT,VBAT,3.72,V,Y\n"
                + "# comment\n"
                + "ITMFG-261021-0002,AGEING,CURRENT,12.5,mA,N\n";

        List<String[]> rows = MfgServices.parseTestCsv(csv);

        assertEquals(2, rows.size());
        assertEquals("ITMFG-261021-0001", rows.get(0)[0]);
        assertEquals("FCT", rows.get(0)[1]);
        assertEquals("VBAT", rows.get(0)[2]);
        assertEquals("3.72", rows.get(0)[3]);
        assertEquals("V", rows.get(0)[4]);
        assertEquals("Y", rows.get(0)[5]);
        assertEquals("N", rows.get(1)[5]);
        assertTrue(MfgServices.parseTestCsv(null).isEmpty());
    }

    @Test
    public void formatsTheShippingLabel() {
        String label = MfgServices.formatShippingLabel("深圳某电子", "张工", "13800001111",
                "广东省深圳市宝安区某路 1 号", "LH-2026-01");

        assertTrue(label.contains("收件人：张工（深圳某电子）"));
        assertTrue(label.contains("手机号：13800001111"));
        assertTrue(label.contains("地址：广东省深圳市宝安区某路 1 号"));
        assertTrue(label.contains("客户编号：LH-2026-01"));
    }

    @Test
    public void omitsTheCustomerCodeWhenMissing() {
        String label = MfgServices.formatShippingLabel("某厂", null, null, null, null);

        assertTrue(label.contains("收件人：（某厂）"));
        assertTrue(!label.contains("客户编号"));
    }

    @Test
    public void computesTheWarrantyEndDate() {
        java.sql.Date shipped = java.sql.Date.valueOf("2026-10-01");

        assertEquals("2027-10-01", MfgServices.warrantyEndDate(shipped, 12).toString());
        assertEquals("2028-04-01", MfgServices.warrantyEndDate(shipped, 18).toString());
        // an unset or invalid period falls back to 12 months
        assertEquals("2027-10-01", MfgServices.warrantyEndDate(shipped, 0).toString());
        assertNull(MfgServices.warrantyEndDate(null, 12));
    }

    @Test
    public void tellsWhetherTheClaimIsInWarranty() {
        java.sql.Date end = java.sql.Date.valueOf("2027-10-01");

        assertTrue(MfgServices.isInWarranty(end, java.sql.Date.valueOf("2026-10-01")));
        assertTrue(MfgServices.isInWarranty(end, java.sql.Date.valueOf("2027-10-01")));
        assertTrue(!MfgServices.isInWarranty(end, java.sql.Date.valueOf("2027-10-02")));
        assertTrue(!MfgServices.isInWarranty(null, java.sql.Date.valueOf("2026-10-01")));
        assertTrue(!MfgServices.isInWarranty(end, null));
    }

    @Test
    public void endsTheNoReasonWindowEightDaysAfterSigning() {
        java.sql.Date signed = java.sql.Date.valueOf("2026-10-01");

        // midnight after the signed day, plus 168 hours
        assertEquals("2026-10-09 00:00:00.0", MfgServices.sevenDayDeadline(signed).toString());
        assertNull(MfgServices.sevenDayDeadline(null));
    }

    @Test
    public void endsTheAfterSalesWindowFifteenDaysAfterSigning() {
        java.sql.Date signed = java.sql.Date.valueOf("2026-10-01");

        assertEquals("2026-10-16 23:59:59.0", MfgServices.afterSaleDeadline(signed).toString());
        assertNull(MfgServices.afterSaleDeadline(null));
    }

    @Test
    public void tellsWhetherTheReturnIsInsideTheNoReasonWindow() {
        java.sql.Date signed = java.sql.Date.valueOf("2026-10-01");

        assertTrue(MfgServices.isWithinSevenDays(signed, java.sql.Date.valueOf("2026-10-01")));
        assertTrue(MfgServices.isWithinSevenDays(signed, java.sql.Date.valueOf("2026-10-08")));
        assertTrue(!MfgServices.isWithinSevenDays(signed, java.sql.Date.valueOf("2026-10-09")));
        assertTrue(!MfgServices.isWithinSevenDays(null, java.sql.Date.valueOf("2026-10-01")));
        assertTrue(!MfgServices.isWithinSevenDays(signed, null));
    }

    @Test
    public void countsTheHoursLeftUntilADeadline() {
        java.sql.Timestamp deadline = java.sql.Timestamp.valueOf("2026-10-16 12:00:00");
        java.time.LocalDateTime now = java.time.LocalDateTime.of(2026, 10, 15, 10, 30);

        assertEquals(25L, MfgServices.hoursLeft(deadline, now));
        assertEquals(-2L, MfgServices.hoursLeft(deadline,
                java.time.LocalDateTime.of(2026, 10, 16, 13, 30)));
        assertEquals(0L, MfgServices.hoursLeft(null, now));
    }

    @Test
    public void flagsDeadlinesThatExpireSoon() {
        assertTrue(MfgServices.isExpiringSoon(MfgServices.DEADLINE_WARNING_HOURS));
        assertTrue(MfgServices.isExpiringSoon(0L));
        assertTrue(!MfgServices.isExpiringSoon(MfgServices.DEADLINE_WARNING_HOURS + 1));
        assertTrue(!MfgServices.isExpiringSoon(-1L));
    }

    @Test
    public void picksTheDefaultFreightPayerByClaimType() {
        assertEquals(MfgServices.FREIGHT_BUYER, MfgServices.defaultFreightPayer(MfgServices.CLAIM_RTN));
        assertEquals(MfgServices.FREIGHT_BUYER, MfgServices.defaultFreightPayer(MfgServices.CLAIM_EXCH));
        assertEquals(MfgServices.FREIGHT_SELLER, MfgServices.defaultFreightPayer(MfgServices.CLAIM_REPAIR));
        assertEquals(MfgServices.FREIGHT_SELLER, MfgServices.defaultFreightPayer(MfgServices.CLAIM_REFUND_ONLY));
    }

    @Test
    public void normalizesMobileNumbers() {
        assertEquals("13800002222", MfgServices.normalizeMobile("138-0000-2222"));
        assertEquals("13800002222", MfgServices.normalizeMobile("+86 138 0000 2222".substring(3)));
        assertEquals("13800002222", MfgServices.normalizeMobile(" 13800002222 "));
        assertNull(MfgServices.normalizeMobile("123"));
        // a masked number cannot identify a buyer, so it must never become a key
        assertNull(MfgServices.normalizeMobile("138****2222"));
        assertNull(MfgServices.normalizeMobile("138xxxx2222"));
        assertNull(MfgServices.normalizeMobile(null));
    }

    @Test
    public void buildsTheBuyerKeyFromMobileOrNick() {
        // the nickname wins: it is always available, the mobile is often masked
        assertEquals("TB:N:nick", MfgServices.buyerKey(MfgServices.CHANNEL_TAOBAO, "138-0000-2222", "nick"));
        assertEquals("TB:N:buyer_1", MfgServices.buyerKey(MfgServices.CHANNEL_TAOBAO, null, "Buyer_1"));
        assertEquals("TB:13800002222", MfgServices.buyerKey(MfgServices.CHANNEL_TAOBAO, "13800002222", null));
        assertEquals("TB:13800002222", MfgServices.buyerKey(null, "13800002222", "  "));
        assertNull(MfgServices.buyerKey(MfgServices.CHANNEL_TAOBAO, "138****2222", null));
        assertNull(MfgServices.buyerKey(MfgServices.CHANNEL_TAOBAO, null, "  "));
    }

    @Test
    public void masksMobileNumbers() {
        assertEquals("138****2222", MfgServices.maskMobile("13800002222"));
        assertEquals("138****2222", MfgServices.maskMobile("138-0000-2222"));
        // a number that is already masked or incomplete is shown as it came
        assertEquals("138****2222", MfgServices.maskMobile("138****2222"));
        assertEquals("1234567", MfgServices.maskMobile("1234567"));
        assertEquals("", MfgServices.maskMobile(null));
    }

    @Test
    public void suggestsARiskLevelFromTheHistory() {
        assertNull(MfgServices.suggestRiskLevel(0, 0.0));
        assertNull(MfgServices.suggestRiskLevel(2, 499.0));
        assertEquals(MfgServices.RISK_WATCH, MfgServices.suggestRiskLevel(3, 0.0));
        assertEquals(MfgServices.RISK_WATCH, MfgServices.suggestRiskLevel(1, 500.0));
        assertEquals(MfgServices.RISK_BLOCK, MfgServices.suggestRiskLevel(5, 0.0));
        assertEquals(MfgServices.RISK_BLOCK, MfgServices.suggestRiskLevel(1, 1000.0));
    }

    @Test
    public void onlyActiveFlagsApply() {
        assertTrue(MfgServices.isFlagActive(MfgServices.FLAG_ACTIVE));
        assertTrue(!MfgServices.isFlagActive(MfgServices.FLAG_RELEASED));
        assertTrue(!MfgServices.isFlagActive(null));
    }

    @Test
    public void flagsStopApplyingWhenTheyExpire() {
        java.sql.Date today = java.sql.Date.valueOf("2026-10-21");

        assertTrue(MfgServices.isFlagLive(MfgServices.FLAG_ACTIVE, java.sql.Date.valueOf("2027-10-21"), today));
        assertTrue(MfgServices.isFlagLive(MfgServices.FLAG_ACTIVE, today, today));
        assertTrue(!MfgServices.isFlagLive(MfgServices.FLAG_ACTIVE, java.sql.Date.valueOf("2026-10-20"), today));
        // no expiry means it keeps applying while active
        assertTrue(MfgServices.isFlagLive(MfgServices.FLAG_ACTIVE, null, today));
        assertTrue(!MfgServices.isFlagLive(MfgServices.FLAG_RELEASED, null, today));
    }

    @Test
    public void formatsPercentages() {
        assertEquals("2.5", MfgServices.percent(1, 40));
        assertEquals("0.0", MfgServices.percent(0, 40));
        assertEquals("100.0", MfgServices.percent(7, 7));
        // no denominator, no percentage
        assertEquals("-", MfgServices.percent(3, 0));
        assertEquals("5.0", MfgServices.percentOf(50.0, 1000.0));
        assertEquals("-", MfgServices.percentOf(50.0, 0.0));
    }

    @Test
    public void agesClaimsAndBucketsThem() {
        assertEquals(0, MfgServices.daysBetween(java.sql.Date.valueOf("2026-10-01"),
                java.sql.Date.valueOf("2026-10-01")));
        assertEquals(30, MfgServices.daysBetween(java.sql.Date.valueOf("2026-10-01"),
                java.sql.Date.valueOf("2026-10-31")));
        assertEquals(-1, MfgServices.daysBetween(null, java.sql.Date.valueOf("2026-10-31")));

        assertEquals("0-30", MfgServices.ageBucket(0));
        assertEquals("0-30", MfgServices.ageBucket(30));
        assertEquals("31-90", MfgServices.ageBucket(31));
        assertEquals("91-180", MfgServices.ageBucket(180));
        assertEquals(">180", MfgServices.ageBucket(181));
        assertEquals("未知", MfgServices.ageBucket(-1));
    }

    @Test
    public void addsUpTheCostOfOneClaim() {
        // refund + freight + one hour of labour
        assertEquals(200.0, MfgServices.claimCost(150.0, 20.0, 0.5, 60.0), 0.001);
        assertEquals(0.0, MfgServices.claimCost(0.0, 0.0, 0.0, 60.0), 0.001);
    }

    @Test
    public void tellsWhetherARedLineIsCrossed() {
        assertTrue(MfgServices.isOverLimit(3, 100, 2.0));
        assertTrue(!MfgServices.isOverLimit(2, 100, 2.0));
        assertTrue(!MfgServices.isOverLimit(3, 0, 2.0));
        assertTrue(MfgServices.isOverLimit(60.0, 1000.0, 5.0));
    }

    @Test
    public void listsTheLastDaysOldestFirst() {
        java.time.LocalDate end = LocalDate.of(2026, 10, 21);

        java.util.List<String> labels = MfgServices.lastDayLabels(end, 3);
        assertEquals(3, labels.size());
        assertEquals("10-19", labels.get(0));
        assertEquals("10-20", labels.get(1));
        assertEquals("10-21", labels.get(2));

        assertTrue(MfgServices.lastDayLabels(end, 0).isEmpty());
        assertTrue(MfgServices.lastDayLabels(null, 5).isEmpty());
    }

    @Test
    public void scalesBarsAgainstTheLargestValue() {
        assertEquals(100L, MfgServices.percentOfMax(10, 10));
        assertEquals(50L, MfgServices.percentOfMax(5, 10));
        assertEquals(0L, MfgServices.percentOfMax(0, 10));
        // a value that exists is always visible, even when it is tiny
        assertEquals(2L, MfgServices.percentOfMax(1, 1000));
        assertEquals(100L, MfgServices.percentOfMax(3, 0));
    }

    @Test
    public void filtersRowsByKeyword() {
        java.util.List<java.util.Map<String, Object>> rows = new java.util.ArrayList<>();
        rows.add(row("workOrderId", "WO-261021-001", "customerName", "ACME"));
        rows.add(row("workOrderId", "WO-261021-002", "customerName", "Beta"));
        rows.add(row("workOrderId", "WO-261021-003", "customerName", "acme labs"));

        assertEquals(2, MfgServices.filterByKeyword(rows, "acme", "customerName").size());
        assertEquals(1, MfgServices.filterByKeyword(rows, "002", "workOrderId").size());
        // no keyword means no filtering
        assertEquals(3, MfgServices.filterByKeyword(rows, "  ", "customerName").size());
        assertEquals(3, MfgServices.filterByKeyword(rows, null, "customerName").size());
        assertEquals(0, MfgServices.filterByKeyword(rows, "zzz", "customerName").size());
    }

    @Test
    public void splitsRowsIntoTewntyRowPages() {
        assertEquals(1L, MfgServices.pageCount(0, 20));
        assertEquals(1L, MfgServices.pageCount(20, 20));
        assertEquals(2L, MfgServices.pageCount(21, 20));
        assertEquals(3L, MfgServices.pageCount(60, 20));

        // an out of range page falls back to the closest available one
        assertEquals(3L, MfgServices.pageNumber(99, 60, 20));
        assertEquals(1L, MfgServices.pageNumber(0, 60, 20));
        assertEquals(2L, MfgServices.pageNumber(2, 60, 20));

        java.util.List<String> rows = new java.util.ArrayList<>();
        for (int i = 0; i < 45; i++) {
            rows.add("row" + i);
        }
        assertEquals(20, MfgServices.pageSlice(rows, 1, 20).size());
        assertEquals("row20", MfgServices.pageSlice(rows, 2, 20).get(0));
        assertEquals(5, MfgServices.pageSlice(rows, 3, 20).size());
        assertEquals("row40", MfgServices.pageSlice(rows, 9, 20).get(0));
        assertEquals(0, MfgServices.pageSlice(new java.util.ArrayList<String>(), 1, 20).size());
    }

    private static java.util.Map<String, Object> row(String keyOne, String valueOne, String keyTwo,
            String valueTwo) {
        java.util.Map<String, Object> row = new java.util.LinkedHashMap<>();
        row.put(keyOne, valueOne);
        row.put(keyTwo, valueTwo);
        return row;
    }

    @Test
    public void formatsBomVersionId() {
        assertEquals("BT5-V1", MfgServices.formatBomVersionId("BT5", 1));
        assertEquals("BT5-V12", MfgServices.formatBomVersionId("BT5", 12));
    }

    @Test
    public void shortensLongModelCodesInTheBomVersionId() {
        // a model code may use the whole id column, the version number still has to fit
        String longCode = "orpah-client-demo-bb";
        assertEquals(20, longCode.length());
        assertEquals("orpah-client-demo-V1", MfgServices.formatBomVersionId(longCode, 1));
        assertEquals(20, MfgServices.formatBomVersionId(longCode, 1).length());
        assertEquals(20, MfgServices.formatBomVersionId(longCode, 123).length());
        assertTrue(MfgServices.formatBomVersionId(longCode, 123).endsWith("-V123"));
        // short codes are untouched
        assertEquals("BT5-V1", MfgServices.formatBomVersionId("BT5", 1));
    }

    @Test
    public void numbersTheNextBomVersionOfALongModelCode() {
        String longCode = "orpah-client-demo-bb";
        List<String> ids = java.util.Arrays.asList(
                MfgServices.formatBomVersionId(longCode, 1),
                MfgServices.formatBomVersionId(longCode, 2));
        assertEquals(3L, MfgServices.nextBomVersionNo(ids, longCode));
    }

    @Test
    public void sumsAPurchaseReceipt() {
        List<Map<String, Object>> lines = new java.util.ArrayList<>();
        lines.add(receiptLine(2.0));
        lines.add(receiptLine(60.0));

        Map<String, Object> summary = MfgServices.receiptSummary(lines, 8.0);
        assertEquals(2, summary.get("lineCount"));
        assertEquals(62.0, ((Double) summary.get("goodsTotal")).doubleValue(), 0.001);
        assertEquals(8.0, ((Double) summary.get("freightTotal")).doubleValue(), 0.001);
        // the freight is a period cost: it is added to the order, never to a line
        assertEquals(70.0, ((Double) summary.get("spendTotal")).doubleValue(), 0.001);
        assertEquals(12.9, ((Double) summary.get("freightShare")).doubleValue(), 0.001);

        // a receipt without lines must not divide by zero
        Map<String, Object> empty = MfgServices.receiptSummary(new java.util.ArrayList<Map<String, Object>>(), 8.0);
        assertEquals(0.0, ((Double) empty.get("freightShare")).doubleValue(), 0.001);
        assertEquals(8.0, ((Double) empty.get("spendTotal")).doubleValue(), 0.001);
    }

    private static Map<String, Object> receiptLine(double goodsAmount) {
        Map<String, Object> line = new java.util.LinkedHashMap<>();
        line.put("goodsAmount", Double.valueOf(goodsAmount));
        return line;
    }

    @Test
    public void parsesTheLinesOfAShopOrder() {
        List<String[]> rows = MfgServices.parseOrderArticleLines(
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
        assertEquals(0, MfgServices.parseOrderArticleLines("ONLY-A-CODE").size());
        assertEquals(0, MfgServices.parseOrderArticleLines("").size());
        assertEquals(0, MfgServices.parseOrderArticleLines(null).size());
    }

    @Test
    public void costsACheapPartFromTheAmountPaid() {
        // 100 pieces for 2.91: the unit price column would keep 0.03, the amount keeps the cent
        assertEquals(0.0291, MfgServices.unitCostOf(100.0, 2.91, 0.03), 0.000001);
        // without an amount the typed in price is used
        assertEquals(0.03, MfgServices.unitCostOf(100.0, 0.0, 0.03), 0.000001);
        // a lot without quantity cannot divide
        assertEquals(1.25, MfgServices.unitCostOf(0.0, 2.91, 1.25), 0.000001);
    }

    @Test
    public void readsTheFieldsOfAVisionAnswer() {
        // the model may answer plain, fenced, or inside a chat envelope
        Map<String, String> plain = oneOrder(
                "{\"orderNo\":\"3316517475066009372\",\"shop\":\"天猫 risym旗舰店\","
                + "\"orderDate\":\"2026-10-18\",\"freight\":\"0\","
                + "\"lines\":\"CAP-104-50V,瓷片电容器 104,100,2.91\"}");
        assertEquals("3316517475066009372", plain.get("orderNo"));
        assertEquals("0", plain.get("freight"));
        assertEquals("CAP-104-50V,瓷片电容器 104,100,2.91", plain.get("lines"));
        // the date of an order is the day it was placed, not the day the parts arrived
        assertEquals("2026-10-18", plain.get("orderDate"));

        // an answer that still calls it date is read as well
        assertEquals("2026-10-19", oneOrder(
                "{\"orderNo\":\"45\",\"date\":\"2026-10-19\",\"lines\":\"X,part,1,1.00\"}").get("orderDate"));

        Map<String, String> fenced = oneOrder(
                "```json\n{\"orderNo\":\"46\",\"lines\":\"A,part,100,2.91\\nB,other,50,1.50\"}\n```");
        assertEquals("A,part,100,2.91\nB,other,50,1.50", fenced.get("lines"));

        Map<String, String> enveloped = oneOrder(
                "{\"choices\":[{\"message\":{\"content\":"
                + "\"{\\\"orderNo\\\":\\\"42\\\",\\\"lines\\\":\\\"X,part,10,1.00\\\"}\"}}]}");
        assertEquals("42", enveloped.get("orderNo"));
        assertEquals("X,part,10,1.00", enveloped.get("lines"));

        // a model that thinks first may leave content empty and put the answer in the reasoning
        Map<String, String> thinking = oneOrder(
                "{\"choices\":[{\"message\":{\"content\":\"\",\"reasoning_content\":"
                + "\"{\\\"orderNo\\\":\\\"43\\\",\\\"lines\\\":\\\"Y,part,5,9.90\\\"}\"}}]}");
        assertEquals("43", thinking.get("orderNo"));
        assertEquals("Y,part,5,9.90", thinking.get("lines"));

        // a model that escapes the newline twice must still give two rows
        Map<String, String> twiceEscaped = oneOrder(
                "{\"choices\":[{\"message\":{\"content\":"
                + "\"{\\\"orderNo\\\":\\\"44\\\",\\\"lines\\\":\\\"A,part,100,2.91\\\\nB,other,100,1.50\\\"}\"}}]}");
        assertEquals("44", twiceEscaped.get("orderNo"));
        assertEquals("A,part,100,2.91\nB,other,100,1.50", twiceEscaped.get("lines"));

        // an answer without lines is not usable, the person fills the form by hand
        assertNull(oneOrder("{\"orderNo\":\"42\"}"));
        assertNull(oneOrder(null));
    }

    @Test
    public void readsALongAnswerWithoutBlowingTheStack() {
        // a thinking model can answer with thousands of characters; a pattern based reader
        // overflowed the stack on this and the page showed an empty answer
        StringBuilder longLines = new StringBuilder("A1,part");
        for (int i = 0; i < 4000; i++) {
            longLines.append(",x\\t").append(i);
        }
        String answer = "{\"choices\":[{\"message\":{\"content\":\"{\\\"lines\\\":\\\"" + longLines
                + "\\\",\\\"orderNo\\\":\\\"123\\\"}\"}}]}";
        List<Map<String, String>> orders = MfgServices.orderListFrom(answer);
        assertEquals(1, orders.size());
        assertEquals("123", orders.get(0).get("orderNo"));
        assertTrue(orders.get(0).get("lines").startsWith("A1,part,x\t0,x\t1"));
        assertEquals(4000 + 1, orders.get(0).get("lines").split("\t").length);

        // a value that is not a string is skipped, the next occurrence of the key is read
        assertEquals("B", MfgServices.jsonStringField("{\"shop\":{\"a\":1},\"name\":\"B\"}", "name"));
        assertNull(MfgServices.jsonStringField("{\"lines\": 5}", "lines"));
        assertNull(MfgServices.jsonStringField("{\"lines\":\"open", "lines"));
        assertEquals("v", MfgServices.jsonStringField("  \"k\" : \"v\" }", "k"));
    }

    @Test
    public void keepsTheEchoedTemplateOutOfTheOrders() {
        // a chatty model repeated the answer format: seven placeholder objects around the real order
        String answer = "{\"orders\":["
                + "{\"orderNo\":null,\"shop\":null,\"lines\":\"code\\tname\\tquantity\\tamount\"},"
                + "{\"orderNo\":\"\",\"lines\":\"code\\tname\\tquantity\\tamount\"},"
                + "{\"orderNo\":\"\",\"lines\":\"code\\tname\\tquantity\\tamount\"},"
                + "{\"orderNo\":\"3316868509015009372\",\"shop\":\"risym\",\"orderDate\":\"2026-09-16\","
                + "\"freight\":\"0.00\",\"paidTotal\":\"9.84\",\"refunded\":\"N\","
                + "\"lines\":\"10CM 母对母 2.54mm 1排40P\\t杜邦线\\t5\\t9.84\"}]}";
        List<Map<String, String>> orders = MfgServices.orderListFrom(answer);
        assertEquals(1, orders.size());
        assertEquals("3316868509015009372", orders.get(0).get("orderNo"));
        // an order without a number cannot be imported anyway: it is the key the import de-dupes on
        assertTrue(MfgServices.orderListFrom(
                "{\"orders\":[{\"lines\":\"A,part,1,1.00\"}]}").isEmpty());
        assertTrue(MfgServices.isLineTemplate("code,name,quantity,amount"));
        assertTrue(MfgServices.isLineTemplate("Code\tName\tQuantity\tAmount"));
        assertFalse(MfgServices.isLineTemplate("A1,part,1,1.00"));
        assertFalse(MfgServices.isLineTemplate("code1,part,1,1.00"));
        // an answer that falls back to explaining the format is a poor read: the scan sends it again
        assertTrue(MfgServices.echoesTemplate("{\"lines\":\"code\\tname\\tquantity\\tamount\"}"));
        assertTrue(MfgServices.echoesTemplate("please answer code,name,quantity,amount"));
        assertFalse(MfgServices.echoesTemplate("{\"orderNo\":\"1\",\"lines\":\"A,part,1,1.00\"}"));
        assertFalse(MfgServices.echoesTemplate(null));
    }

    @Test
    public void knowsHowManyOrdersThePictureShows() {
        // the model reports the number of blocks it counted, so a skipped one can be caught
        String answer = "{\"orderCount\":\"3\",\"orders\":["
                + "{\"orderNo\":\"1\",\"lines\":\"A,part,1,1.00\"},"
                + "{\"orderNo\":\"2\",\"lines\":\"B,part,2,2.00\"}]}";
        assertEquals(3, MfgServices.orderCountIn(answer));
        assertEquals(2, MfgServices.orderListFrom(answer).size());
        // an answer without the number is taken as it is
        assertEquals(0, MfgServices.orderCountIn("{\"orders\":[{\"orderNo\":\"1\",\"lines\":\"A,part,1,1\"}]}"));
        assertEquals(0, MfgServices.orderCountIn(null));
        // and it is found through the chat envelope as well
        String enveloped = "{\"choices\":[{\"message\":{\"content\":"
                + "\"{\\\"orderCount\\\":\\\"2\\\",\\\"orders\\\":[]}\"}}]}";
        assertEquals(2, MfgServices.orderCountIn(enveloped));
    }

    @Test
    public void saysWhatToDoWhenTheScreenshotCouldNotBeRead() {
        // a thinking model that ran out of output budget answers with reasoning only
        String truncated = "{\"choices\":[{\"finish_reason\":\"length\",\"message\":{\"content\":\"\","
                + "\"reasoning_content\":\"We need answer only JSON object. Need parse image...\"}}]}";
        String message = MfgOrderScan.scanFailure(truncated, null);
        assertTrue(message.contains("输出长度"));
        assertTrue(message.contains("再点一次"));
        assertFalse(message.contains("org.langhua"));

        // no call got through at all
        String offline = MfgOrderScan.scanFailure(null, "Connection refused");
        assertTrue(offline.contains("连不上"));
        assertTrue(offline.contains("Connection refused"));

        // the model answered something else entirely
        String other = MfgOrderScan.scanFailure("{\"orders\":[]}", null);
        assertTrue(other.contains("没从截图里读出订单"));
        assertTrue(other.contains("再点一次"));
        // one swallowed retry is still part of the message, so nobody wonders why it took longer
        assertTrue(other.contains("已自动重试 1 次"));
    }

    @Test
    public void tellsWhichFieldTheVisionTestStillNeeds() {
        // the person is looking at the form: name the field, never a system property
        String all = MfgOrderScan.testMissing(null, null, null);
        assertTrue(all.contains("接口地址"));
        assertTrue(all.contains("模型名"));
        assertTrue(all.contains("密钥"));
        String onlyKey = MfgOrderScan.testMissing("https://api.deepseek.com/v1/chat/completions",
                "deepseek-flash", null);
        assertTrue(onlyKey.contains("密钥"));
        assertFalse(onlyKey.contains("接口地址"));
        assertFalse(onlyKey.contains("模型名"));
        // the receipt page must point at the settings page, not at -D options
        assertFalse(MfgOrderScan.missingSetting().contains("org.langhua"));
        assertTrue(MfgOrderScan.missingSetting().contains("系统设置"));
    }

    @Test
    public void readsSeveralOrdersOfOneScreenshot() {
        // the order list of the shop: three orders with their own numbers, shops and money
        String answer = "{\"orders\":["
                + "{\"orderNo\":\"3316822177083054564\",\"shop\":\"佳仕通数码专营店\","
                + "\"orderDate\":\"2026-08-30\",\"paidTotal\":\"39.50\",\"freight\":\"1.50\","
                + "\"refunded\":\"N\",\"lines\":\"MB-102,400孔面包板线 830,2,38.00\"},"
                + "{\"orderNo\":\"3316819945036045382\",\"shop\":\"Muse Lab\","
                + "\"orderDate\":\"2026-08-29\",\"paidTotal\":\"52.60\",\"freight\":\"3.00\","
                + "\"refunded\":\"N\",\"lines\":\"NanoDLA,NanoDLA 逻辑分析仪,1,49.60\"},"
                + "{\"orderNo\":\"3316999260043018554\",\"shop\":\"telesky旗舰店\","
                + "\"orderDate\":\"2026-08-29\",\"paidTotal\":\"3.87\",\"freight\":\"0.00\","
                + "\"refunded\":\"Y\",\"lines\":\"SOP16,SSOP16 贴片转直插,2,3.87\"}"
                + "]}";
        List<Map<String, String>> orders = MfgServices.orderListFrom(answer);
        assertEquals(3, orders.size());
        assertEquals("3316822177083054564", orders.get(0).get("orderNo"));
        assertEquals("1.50", orders.get(0).get("freight"));
        assertEquals("MB-102,400孔面包板线 830,2,38.00", orders.get(0).get("lines"));
        assertEquals("NanoDLA,NanoDLA 逻辑分析仪,1,49.60", orders.get(1).get("lines"));
        assertEquals("3316999260043018554", orders.get(2).get("orderNo"));
        assertTrue(MfgServices.isRefunded(orders.get(2)));
        assertFalse(MfgServices.isRefunded(orders.get(0)));

        // a single order still comes out as one row, whatever shape the model used
        assertEquals(1, MfgServices.orderListFrom(
                "{\"orders\":[{\"orderNo\":\"1\",\"lines\":\"A,part,1,1.00\"}]}").size());
        assertEquals(1, MfgServices.orderListFrom(
                "{\"orderNo\":\"42\",\"lines\":\"A,part,1,1.00\"}").size());

        // a chat envelope with the array escaped inside the message
        String enveloped = "{\"choices\":[{\"message\":{\"content\":"
                + "\"{\\\"orders\\\":[{\\\"orderNo\\\":\\\"7\\\",\\\"lines\\\":\\\"A,part,2,2.00\\\"},"
                + "{\\\"orderNo\\\":\\\"8\\\",\\\"lines\\\":\\\"B,part,3,3.00\\\"}]}\"}}]}";
        List<Map<String, String>> fromEnvelope = MfgServices.orderListFrom(enveloped);
        assertEquals(2, fromEnvelope.size());
        assertEquals("7", fromEnvelope.get(0).get("orderNo"));
        assertEquals("B,part,3,3.00", fromEnvelope.get(1).get("lines"));

        // braces inside a product name must not cut the object in half
        List<Map<String, String>> braces = MfgServices.orderListFrom(
                "{\"orders\":[{\"orderNo\":\"9\",\"lines\":\"X,板 {带孔} 2个,1,5.00\"}]}");
        assertEquals(1, braces.size());
        assertEquals("X,板 {带孔} 2个,1,5.00", braces.get(0).get("lines"));

        assertTrue(MfgServices.orderListFrom(null).isEmpty());
        assertTrue(MfgServices.orderListFrom("{\"orderNo\":\"42\"}").isEmpty());
    }

    @Test
    public void keepsARefundedOrderOutOfTheStock() {        // "退款成功" on the screenshot: the parts never stayed in the shop
        Map<String, String> refunded = oneOrder(
                "{\"orderNo\":\"3316900549003018554\",\"refunded\":\"Y\","
                + "\"lines\":\"CH32V002J4M6,CH32V002J4M6 SOP8,10,6.44\"}");
        assertTrue(MfgServices.isRefunded(refunded));
        assertEquals("Y", refunded.get("refunded"));

        Map<String, String> normal = oneOrder(
                "{\"orderNo\":\"42\",\"refunded\":\"N\",\"lines\":\"X,part,1,1.00\"}");
        assertFalse(MfgServices.isRefunded(normal));
        // older answers do not carry the flag at all, which is not a refund either
        assertFalse(MfgServices.isRefunded(oneOrder(
                "{\"orderNo\":\"42\",\"lines\":\"X,part,1,1.00\"}")));
        assertFalse(MfgServices.isRefunded(null));
    }

    @Test
    public void fitsALongPartNumberIntoTheCodeField() {
        // a code that fits is kept, the blanks around it are not
        assertEquals("CAP-104-50V", MfgServices.fittingComponentCode(" CAP-104-50V "));

        // the blanks inside the number stay: 5.5V1.5F C型 21*7.5 must not become 5.5V1.5FC217.5
        assertEquals("5.5V1.5F C型 21*7.5", MfgServices.fittingComponentCode("5.5V1.5F  C型   21*7.5"));

        // the code field holds 100 characters, so a package description is stored as it is
        String long1 = "QFN16-0.65-QFN20-0.4-2.54";
        assertEquals(long1, MfgServices.fittingComponentCode(long1));

        // only something longer than the column is shortened, always the same way
        String tooLong = "QFN16-0.65-QFN20-0.4-2.54-" + "1234567890".repeat(9);
        String fitted = MfgServices.fittingComponentCode(tooLong);
        assertTrue(fitted.length() <= 100);
        assertTrue(fitted.startsWith("QFN16-0.65-QFN20"));
        assertEquals(fitted, MfgServices.fittingComponentCode(tooLong));
        assertFalse(fitted.equals(MfgServices.fittingComponentCode(tooLong + "X")));

        // the lines of an order keep their codes and their four cells
        String lines = MfgServices.fitOrderLines(long1 + ",转接板 2.54mm,2,12.60");
        String[] cells = lines.split("\t");
        assertEquals(long1, cells[0]);
        assertEquals("转接板 2.54mm", cells[1]);
        assertEquals("2", cells[2]);
        assertEquals("12.60", cells[3]);
    }

    @Test
    public void correctsAFreightThatDoesNotAddUp() {
        // the page says 实付款 3.50 and 含运费 2.00, so the goods are 1.50 and the freight 2.00
        Map<String, String> wrong = new HashMap<>();
        wrong.put("paidTotal", "3.50");
        wrong.put("freight", "4.00");
        wrong.put("lines", "SK-12D02,switch,10,1.50");
        String said = MfgServices.checkOrderTotals(wrong);
        assertEquals("2.00", wrong.get("freight"));
        assertTrue(said.contains("3.50") && said.contains("4.00") && said.contains("2.00"));

        // without a freight the difference is used
        Map<String, String> missing = new HashMap<>();
        missing.put("paidTotal", "3.50");
        missing.put("lines", "SK-12D02,switch,10,1.50");
        assertTrue(MfgServices.checkOrderTotals(missing) != null);
        assertEquals("2.00", missing.get("freight"));

        // a freight that adds up is left alone, and so is an order without a paid total
        Map<String, String> fine = new HashMap<>();
        fine.put("paidTotal", "3.50");
        fine.put("freight", "2.00");
        fine.put("lines", "SK-12D02,switch,10,1.50");
        assertNull(MfgServices.checkOrderTotals(fine));
        assertEquals("2.00", fine.get("freight"));

        Map<String, String> unknown = new HashMap<>();
        unknown.put("freight", "4.00");
        unknown.put("lines", "SK-12D02,switch,10,1.50");
        assertNull(MfgServices.checkOrderTotals(unknown));
        assertEquals("4.00", unknown.get("freight"));
    }

    @Test
    public void keepsAnOrderLineWithoutACode() {
        // a shop that prints no article code: the model answers with an empty first cell
        List<String[]> rows = MfgServices.parseOrderArticleLines(
                ",超级法拉电容器 5.5V1.5F C型 21*7.5,2,9.28");
        assertEquals(1, rows.size());
        assertEquals("超级法拉电容器 5.5V1.5F C型 21*7.5", rows.get(0)[0]);
        assertEquals("2", rows.get(0)[2]);

        // a name without any numbers is still a code of its own, and the same every time
        assertEquals("超级法拉电容器", MfgServices.codeFromName("超级法拉电容器"));
        assertFalse(MfgServices.codeFromName("超级法拉电容器")
                .equals(MfgServices.codeFromName("瓷片电容器")));
        assertEquals("P-000000", MfgServices.codeFromName("  "));

        // and nothing is checked against the paid total when no line could be read
        Map<String, String> unreadable = new HashMap<>();
        unreadable.put("paidTotal", "9.28");
        unreadable.put("lines", ",,,");
        assertNull(MfgServices.checkOrderTotals(unreadable));
    }

    @Test
    public void quotesWhatTheVisionModelAnswered() {
        // the quote is what tells "this is not an order" from "the model cannot see images"
        assertEquals("（空回答）", MfgServices.visionReplyExcerpt(null, 40));
        assertEquals("This is a web page not an order", MfgServices.visionReplyExcerpt(
                "{\"choices\":[{\"message\":{\"content\":\"This is a web page\\nnot an order\"}}]}", 40));
        assertEquals("cannot see the image", MfgServices.visionReplyExcerpt(
                "{\"choices\":[{\"message\":{\"content\":\"\","
                + "\"reasoning_content\":\"cannot see the image\"}}]}", 40));
        assertEquals("abcd…", MfgServices.visionReplyExcerpt("abcdefgh", 4));
    }

    @Test
    public void completesTheVisionEndpoint() {
        // people type only the host, or stop at the version: the API answers those with 404
        assertEquals("https://api.deepseek.com/v1/chat/completions",
                MfgServices.normalizeVisionEndpoint("https://api.deepseek.com"));
        assertEquals("https://api.deepseek.com/v1/chat/completions",
                MfgServices.normalizeVisionEndpoint("https://api.deepseek.com/"));
        assertEquals("https://open.bigmodel.cn/api/paas/v4/chat/completions",
                MfgServices.normalizeVisionEndpoint("https://open.bigmodel.cn/api/paas/v4"));
        // a complete address is left alone
        assertEquals("https://api.openai.com/v1/chat/completions",
                MfgServices.normalizeVisionEndpoint("https://api.openai.com/v1/chat/completions"));
        assertNull(MfgServices.normalizeVisionEndpoint(null));
    }

    @Test
    public void explainsAFailedVisionCall() {
        assertTrue(MfgServices.describeVisionError(404, "").contains("chat/completions"));
        assertTrue(MfgServices.describeVisionError(401, "{\"error\":\"bad key\"}").contains("密钥"));
        assertTrue(MfgServices.describeVisionError(400, "").contains("不支持看图"));
        // the body is quoted but kept short
        String longBody = MfgServices.describeVisionError(400,
                "{\"error\":\"" + "x".repeat(500) + "\"}");
        assertTrue(longBody.contains("…"));
        assertTrue(longBody.length() < 380);
    }

    @Test
    public void numbersTheNextBomVersion() {
        List<String> none = new java.util.ArrayList<>();
        assertEquals(1L, MfgServices.nextBomVersionNo(none, "BT5"));

        List<String> one = java.util.Arrays.asList("BT5-V1");
        assertEquals(2L, MfgServices.nextBomVersionNo(one, "BT5"));

        // gaps do not matter, the highest number wins
        List<String> gap = java.util.Arrays.asList("BT5-V1", "BT5-V3", "BT5-V2");
        assertEquals(4L, MfgServices.nextBomVersionNo(gap, "BT5"));

        // versions of another model are ignored, and a suffix that is not a number is skipped
        List<String> mixed = java.util.Arrays.asList("BT6-V9", "BT5-VX");
        assertEquals(1L, MfgServices.nextBomVersionNo(mixed, "BT5"));
    }

    @Test
    public void walksTheDevelopmentStageLadder() {
        assertEquals("MFG_STAGE_DESIGN", MfgServices.nextDevStage("MFG_STAGE_CONCEPT"));
        assertEquals("MFG_STAGE_PROTOTYPE", MfgServices.nextDevStage("MFG_STAGE_DESIGN"));
        assertEquals("MFG_STAGE_TRIAL", MfgServices.nextDevStage("MFG_STAGE_PROTOTYPE"));
        assertEquals("MFG_STAGE_MASS", MfgServices.nextDevStage("MFG_STAGE_TRIAL"));
        // mass production is the last stage
        assertNull(MfgServices.nextDevStage("MFG_STAGE_MASS"));
        assertNull(MfgServices.nextDevStage("NOT_A_STAGE"));
        assertNull(MfgServices.nextDevStage(null));

        assertEquals(0, MfgServices.devStageIndex("MFG_STAGE_CONCEPT"));
        assertEquals(4, MfgServices.devStageIndex("MFG_STAGE_MASS"));
        assertEquals(-1, MfgServices.devStageIndex("NOT_A_STAGE"));
    }

    @Test
    public void onlyAPassMovesTheProjectOn() {
        assertTrue(MfgServices.stageAdvancesOn("MFG_REVIEW_PASS"));
        assertTrue(MfgServices.stageAdvancesOn("MFG_REVIEW_CONDITIONAL"));
        assertTrue(!MfgServices.stageAdvancesOn("MFG_REVIEW_FAIL"));
        assertTrue(!MfgServices.stageAdvancesOn(null));
    }

    @Test
    public void knowsTheDevelopmentAndChangeOrderCodes() {
        assertTrue(MfgServices.isKnownDevStage("MFG_STAGE_TRIAL"));
        assertTrue(!MfgServices.isKnownDevStage("MFG_STAGE_SOMETHING"));

        assertTrue(MfgServices.isKnownReviewResult("MFG_REVIEW_CONDITIONAL"));
        assertTrue(!MfgServices.isKnownReviewResult("PASS"));

        assertTrue(MfgServices.isKnownDevStatus("MFG_DEV_ONHOLD"));
        assertTrue(!MfgServices.isKnownDevStatus("MFG_DEV_BOGUS"));

        assertTrue(MfgServices.isKnownEcoStatus("MFG_ECO_APPLIED"));
        assertTrue(!MfgServices.isKnownEcoStatus("DRAFT"));

        assertTrue(MfgServices.isKnownEcoType("MFG_ECO_MATERIAL"));
        assertTrue(!MfgServices.isKnownEcoType("MFG_ECO_PRICE"));
    }

    @Test
    public void appliesOnlyAnApprovedChangeOrder() {
        assertTrue(MfgServices.ecoAppliable("MFG_ECO_APPROVED"));
        assertTrue(!MfgServices.ecoAppliable("MFG_ECO_DRAFT"));
        assertTrue(!MfgServices.ecoAppliable("MFG_ECO_APPLIED"));
        assertTrue(!MfgServices.ecoAppliable("MFG_ECO_CANCELLED"));
        assertTrue(!MfgServices.ecoAppliable(null));
    }

    @Test
    public void formatsDevelopmentProjectAndChangeOrderIds() {
        LocalDate day = LocalDate.of(2026, 10, 21);
        assertEquals("DEV-261021-01", MfgServices.formatDevProjectId(day, 1));
        assertEquals("DEV-261021-12", MfgServices.formatDevProjectId(day, 12));
        assertEquals("ECO-261021-01", MfgServices.formatEcoId(day, 1));
        assertEquals("ECO-261021-07", MfgServices.formatEcoId(day, 7));
    }

    @Test
    public void knowsTheStepsOfEachDevelopmentTrack() {
        assertEquals(5, MfgServices.stepsOfTrack(MfgServices.TRACK_PCB).length);
        assertEquals(3, MfgServices.stepsOfTrack(MfgServices.TRACK_FIRMWARE).length);
        assertEquals(7, MfgServices.stepsOfTrack(MfgServices.TRACK_ENCLOSURE).length);
        assertEquals(0, MfgServices.stepsOfTrack("").length);
        assertEquals(0, MfgServices.stepsOfTrack(null).length);
        assertEquals(0, MfgServices.stepsOfTrack("MFG_TRACK_BOGUS").length);

        // the PCB track starts on the breadboard and ends on the hand soldered test
        assertEquals("MFG_PCB_BREADBOARD", MfgServices.stepsOfTrack(MfgServices.TRACK_PCB)[0]);
        assertEquals("MFG_PCB_HAND_SOLDER", MfgServices.stepsOfTrack(MfgServices.TRACK_PCB)[4]);
        // the enclosure track ends with the product assembly
        assertEquals("MFG_ENC_ASSEMBLY", MfgServices.stepsOfTrack(MfgServices.TRACK_ENCLOSURE)[6]);

        assertTrue(MfgServices.isKnownTrack(MfgServices.TRACK_ENCLOSURE));
        assertTrue(!MfgServices.isKnownTrack("MFG_TRACK_BOGUS"));

        assertTrue(MfgServices.isKnownStep(MfgServices.TRACK_FIRMWARE, "MFG_FW_FLASH"));
        assertTrue(!MfgServices.isKnownStep(MfgServices.TRACK_PCB, "MFG_FW_FLASH"));
        assertTrue(!MfgServices.isKnownStep("", "MFG_PCB_SCHEMATIC"));
    }

    @Test
    public void findsTheFirstOpenStep() {
        // nothing done yet, so the first step of the track is still open
        assertEquals("MFG_PCB_BREADBOARD",
                MfgServices.firstOpenStep(MfgServices.TRACK_PCB, new java.util.ArrayList<String>()));

        java.util.List<String> done = java.util.Arrays.asList("MFG_PCB_BREADBOARD", "MFG_PCB_SCHEMATIC");
        assertEquals("MFG_PCB_LAYOUT", MfgServices.firstOpenStep(MfgServices.TRACK_PCB, done));

        java.util.List<String> all = java.util.Arrays.asList(MfgServices.PCB_STEPS);
        assertNull(MfgServices.firstOpenStep(MfgServices.TRACK_PCB, all));

        // a track without steps has nothing to open
        assertNull(MfgServices.firstOpenStep("", done));
    }

    @Test
    public void countsSkippedStepsAsFinished() {
        assertTrue(MfgServices.stepFinished("MFG_STEP_DONE"));
        assertTrue(MfgServices.stepFinished("MFG_STEP_SKIPPED"));
        assertTrue(!MfgServices.stepFinished("MFG_STEP_DOING"));
        assertTrue(!MfgServices.stepFinished("MFG_STEP_TODO"));
        assertTrue(!MfgServices.stepFinished(null));

        assertTrue(MfgServices.isKnownStepStatus("MFG_STEP_DOING"));
        assertTrue(!MfgServices.isKnownStepStatus("DONE"));
    }

    @Test
    public void knowsTheAllowedPhotoTypes() {
        assertTrue(MfgServices.isAllowedImageType("image/jpeg"));
        assertTrue(MfgServices.isAllowedImageType("image/png"));
        assertTrue(MfgServices.isAllowedImageType("IMAGE/GIF"));
        assertTrue(MfgServices.isAllowedImageType("  image/png  "));
        assertTrue(!MfgServices.isAllowedImageType("image/webp"));
        assertTrue(!MfgServices.isAllowedImageType("application/pdf"));
        assertTrue(!MfgServices.isAllowedImageType(""));
        assertTrue(!MfgServices.isAllowedImageType(null));
    }

    @Test
    public void guessesThePhotoTypeFromTheFileName() {
        assertEquals("image/jpeg", MfgServices.imageTypeOfFileName("front.JPG"));
        assertEquals("image/jpeg", MfgServices.imageTypeOfFileName("C:\\photos\\back.jpeg"));
        assertEquals("image/png", MfgServices.imageTypeOfFileName("board.png"));
        assertEquals("image/gif", MfgServices.imageTypeOfFileName("anim.Gif"));
        assertEquals("application/pdf", MfgServices.imageTypeOfFileName("report.pdf"));
        assertNull(MfgServices.imageTypeOfFileName("notes.txt"));
        assertNull(MfgServices.imageTypeOfFileName("noextension"));
        assertNull(MfgServices.imageTypeOfFileName(null));
    }

    @Test
    public void allowsImagesAndPdfOnly() {
        assertTrue(MfgServices.isAllowedAttachmentType("image/png"));
        assertTrue(MfgServices.isAllowedAttachmentType("application/pdf"));
        assertTrue(MfgServices.isAllowedAttachmentType("APPLICATION/PDF"));
        assertTrue(!MfgServices.isAllowedAttachmentType("text/plain"));
        assertTrue(!MfgServices.isAllowedAttachmentType("application/zip"));
        assertTrue(!MfgServices.isAllowedAttachmentType(null));
        // the pdf is not an image, so it never gets a thumbnail
        assertTrue(!MfgServices.isAllowedImageType("application/pdf"));
    }

    @Test
    public void knowsTheAttachmentOwnerTypes() {
        assertTrue(MfgServices.isKnownAttachmentOwnerType("MFG_ATT_PRODUCT"));
        assertTrue(MfgServices.isKnownAttachmentOwnerType("MFG_ATT_DEV_PROJECT"));
        assertTrue(MfgServices.isKnownAttachmentOwnerType("MFG_ATT_WORK_ORDER"));
        assertTrue(MfgServices.isKnownAttachmentOwnerType("MFG_ATT_DELIVERY"));
        assertTrue(MfgServices.isKnownAttachmentOwnerType("MFG_ATT_CLAIM"));
        assertTrue(!MfgServices.isKnownAttachmentOwnerType("MFG_ATT_BOGUS"));
        assertTrue(!MfgServices.isKnownAttachmentOwnerType(null));
    }

    @Test
    public void cleansUploadedFileNames() {
        assertEquals("photo.png", MfgServices.cleanFileName("C:\\Users\\me\\Pictures\\photo.png"));
        assertEquals("photo.png", MfgServices.cleanFileName("/tmp/photo.png"));
        // characters outside letters, digits and dot dash underscore become underscores
        String cleaned = MfgServices.cleanFileName("外壳 正面.jpg");
        assertTrue("expected only underscores and the extension, got " + cleaned, cleaned.matches("_+\\.jpg"));
        assertEquals("file", MfgServices.cleanFileName(""));
        assertEquals("file", MfgServices.cleanFileName(null));
        assertTrue(MfgServices.cleanFileName("a".repeat(200) + ".png").length() <= 60);
    }

    @Test
    public void onlyAFailedLotBlocksIssuing() {
        assertTrue(MfgServices.isLotUsable(null));
        assertTrue(MfgServices.isLotUsable(MfgServices.IQC_PENDING));
        assertTrue(MfgServices.isLotUsable(MfgServices.IQC_PASS));
        assertTrue(MfgServices.isLotUsable(MfgServices.IQC_CONCESSION));
        assertTrue(!MfgServices.isLotUsable(MfgServices.IQC_FAIL));
        // the rule is "only a failure blocks", so an unknown status must not lock the shop floor out
        assertTrue(MfgServices.isLotUsable("MFG_IQC_BOGUS"));
    }

    @Test
    public void normalizesTheInspectionStatus() {        // an empty value means "not inspected yet", not an error
        assertEquals(MfgServices.IQC_PENDING, MfgServices.normalizeInspectStatus(null));
        assertEquals(MfgServices.IQC_PENDING, MfgServices.normalizeInspectStatus("   "));
        assertEquals(MfgServices.IQC_PASS, MfgServices.normalizeInspectStatus("MFG_IQC_PASS"));
        assertEquals(MfgServices.IQC_PASS, MfgServices.normalizeInspectStatus("mfg_iqc_pass"));
        assertEquals(MfgServices.IQC_CONCESSION, MfgServices.normalizeInspectStatus("MFG_IQC_CONCESSION"));
        assertEquals(MfgServices.IQC_FAIL, MfgServices.normalizeInspectStatus("MFG_IQC_FAIL"));
        assertNull(MfgServices.normalizeInspectStatus("MFG_IQC_BOGUS"));
        assertEquals(4, MfgServices.INSPECT_STATUSES.length);
    }

    @Test
    public void costsOneUnitWithTheBomAndTheLaborRate() {
        java.util.Map<String, Object> cost = MfgServices.costSummary(18.5, 0.45, 1.0, 19.0, 39.0);

        assertEquals(18.5, ((Double) cost.get("materialPerUnit")).doubleValue(), 1e-9);
        assertEquals(0.45, ((Double) cost.get("laborPerUnit")).doubleValue(), 1e-9);
        assertEquals(18.95, ((Double) cost.get("unitCost")).doubleValue(), 1e-9);
        // negative means "cheaper than the target", positive means "over the target"
        assertEquals(-0.05, ((Double) cost.get("targetDifference")).doubleValue(), 1e-9);
        assertEquals(20.05, ((Double) cost.get("unitMargin")).doubleValue(), 1e-9);
        assertEquals(51.41, ((Double) cost.get("marginRate")).doubleValue(), 1e-9);
    }

    @Test
    public void costsABatchAndRoundsToCents() {
        java.util.Map<String, Object> cost = MfgServices.costSummary(10.0 / 3.0, 0.0, 3.0, null, null);

        assertEquals(10.0, ((Double) cost.get("totalCost")).doubleValue(), 1e-9);
        assertEquals(3.33, ((Double) cost.get("unitCost")).doubleValue(), 1e-9);
        // no target price means no margin, not a division by zero
        assertNull(cost.get("unitMargin"));
        assertNull(cost.get("marginRate"));
        assertNull(cost.get("targetDifference"));
    }

    @Test
    public void quotesCsvCellsThatWouldBreakTheLine() {
        assertEquals("B5X", MfgServices.csvCell("B5X"));
        assertEquals("", MfgServices.csvCell(null));
        // a comma, a quote and a line break all force the quotes, and quotes are doubled
        assertEquals("\"a,b\"", MfgServices.csvCell("a,b"));
        assertEquals("\"say \"\"hi\"\"\"", MfgServices.csvCell("say \"hi\""));
        assertEquals("\"two\nlines\"", MfgServices.csvCell("two\nlines"));
        assertEquals("B5X,12,0.5", MfgServices.csvLine(
                java.util.Arrays.asList("B5X", "12", "0.5")));
        // null cells become empty cells, so the columns stay aligned
        assertEquals("B5X,,12", MfgServices.csvLine(
                java.util.Arrays.asList("B5X", null, "12")));
    }

    @Test
    public void formatsQuantitiesForTheAlertTexts() {
        assertEquals("0", MfgServices.formatQty(0.0));
        assertEquals("12", MfgServices.formatQty(12.0));
        assertEquals("12.5", MfgServices.formatQty(12.5));
        assertEquals("1.234", MfgServices.formatQty(1.23449));
    }

    @Test
    public void quotesSoftwareFromFunctionPoints() {
        // the 2019 assessment case: 10 function points, SWF 1, PDR 40, 880 per person day
        java.util.Map<String, Object> cost = MfgServices.softwareCost(0.0, 0.0, Double.valueOf(10.0),
                1.0, 0.0, 0.0, 0.0, 1.0, 40.0, 880.0, 0.06, 0.05);

        assertEquals(10.0, ((Double) cost.get("ufp")).doubleValue(), 1e-9);
        assertEquals(400.0, ((Double) cost.get("effortHours")).doubleValue(), 1e-9);
        assertEquals(50.0, ((Double) cost.get("effortDays")).doubleValue(), 1e-9);
        // 1.277 x (400/176)^0.404, the schedule the workbook of that case shows
        assertEquals(1.78, ((Double) cost.get("scheduleMonths")).doubleValue(), 0.01);
        assertEquals(44000.0, ((Double) cost.get("amountBeforeTax")).doubleValue(), 1e-9);
        assertEquals(2640.0, ((Double) cost.get("taxAmount")).doubleValue(), 1e-9);
        assertEquals(46640.0, ((Double) cost.get("amountWithTax")).doubleValue(), 1e-9);
        assertEquals(2332.0, ((Double) cost.get("dncAmount")).doubleValue(), 1e-9);
        assertEquals(48972.0, ((Double) cost.get("totalAmount")).doubleValue(), 1e-9);
        assertEquals(4664.0, ((Double) cost.get("costPerFp")).doubleValue(), 1e-9);
    }

    @Test
    public void countsFunctionPointsWhenTheyAreNotGiven() {
        // UFP = 35 x ILF + 15 x EIF, and the four software factors add up
        java.util.Map<String, Object> cost = MfgServices.softwareCost(4.0, 2.0, null,
                1.5, -0.5, 0.1, 0.05, 1.0, 40.0, 880.0, 0.06, 0.05);

        assertEquals(170.0, ((Double) cost.get("ufp")).doubleValue(), 1e-9);
        assertEquals(1.15, ((Double) cost.get("swf")).doubleValue(), 1e-9);
        // 40 x 170 x 1.15 = 7820 hours
        assertEquals(7820.0, ((Double) cost.get("effortHours")).doubleValue(), 1e-9);
    }

    @Test
    public void knowsTheMedianOfTheProductivitySamples() {
        assertEquals(0.0, MfgServices.median(null), 1e-9);
        assertEquals(0.0, MfgServices.median(java.util.Collections.<Double>emptyList()), 1e-9);
        assertEquals(45.0, MfgServices.median(java.util.Arrays.asList(
                Double.valueOf(45.0), Double.valueOf(40.0), Double.valueOf(50.0))), 1e-9);
        assertEquals(45.0, MfgServices.median(java.util.Arrays.asList(
                Double.valueOf(40.0), Double.valueOf(50.0))), 1e-9);
    }

    @Test
    public void labelsThePeriodOfATrendBucket() {
        java.sql.Date date = java.sql.Date.valueOf(java.time.LocalDate.of(2026, 10, 21));

        assertEquals("2026-10-21", MfgServices.periodKey(date, "DAY"));
        // 21 October 2026 is in ISO week 43
        assertEquals("2026-W43", MfgServices.periodKey(date, "WEEK"));
        assertEquals("2026-10", MfgServices.periodKey(date, "MONTH"));
        // an unknown grouping falls back to the month, a missing date has no period
        assertEquals("2026-10", MfgServices.periodKey(date, "YEAR"));
        assertEquals("-", MfgServices.periodKey(null, "MONTH"));
    }

    @Test
    public void countsYieldFromGoodAndRejectedUnits() {
        assertEquals(95.0, MfgServices.yieldRate(95.0, 5.0), 1e-9);
        assertEquals(100.0, MfgServices.yieldRate(10.0, 0.0), 1e-9);
        assertEquals(0.0, MfgServices.yieldRate(0.0, 4.0), 1e-9);
        // nothing reported is not a division by zero
        assertEquals(0.0, MfgServices.yieldRate(0.0, 0.0), 1e-9);
    }

    @Test
    public void countsHoursPerGoodUnit() {
        assertEquals(0.5, MfgServices.hoursPerUnit(5.0, 10.0), 1e-9);
        assertEquals(0.0, MfgServices.hoursPerUnit(5.0, 0.0), 1e-9);
        assertEquals(0.0, MfgServices.hoursPerUnit(0.0, 10.0), 1e-9);
    }

    @Test
    public void ranksIssuesSoTheOpenHighOnesComeFirst() {
        assertTrue(MfgServices.isOpenIssue(MfgServices.ISSUE_OPEN));
        assertTrue(MfgServices.isOpenIssue(MfgServices.ISSUE_DOING));
        assertFalse(MfgServices.isOpenIssue(MfgServices.ISSUE_CLOSED));
        assertFalse(MfgServices.isOpenIssue(null));

        assertTrue(MfgServices.statusRank(MfgServices.ISSUE_OPEN)
                < MfgServices.statusRank(MfgServices.ISSUE_DOING));
        assertTrue(MfgServices.statusRank(MfgServices.ISSUE_DOING)
                < MfgServices.statusRank(MfgServices.ISSUE_CLOSED));
        assertTrue(MfgServices.severityRank(MfgServices.ISSUE_HIGH)
                > MfgServices.severityRank(MfgServices.ISSUE_MEDIUM));
        assertTrue(MfgServices.severityRank(MfgServices.ISSUE_MEDIUM)
                > MfgServices.severityRank(MfgServices.ISSUE_LOW));

        assertTrue(MfgServices.isKnownIssueStatus(MfgServices.ISSUE_DOING));
        assertFalse(MfgServices.isKnownIssueStatus("MFG_ISSUE_WHATEVER"));
        assertTrue(MfgServices.isKnownIssueSeverity(MfgServices.ISSUE_HIGH));
        assertFalse(MfgServices.isKnownIssueSeverity("MFG_ISSUE_URGENT"));
    }

    @Test
    public void signsTheInventoryMovements() {
        // receipts and returns add, issues and scraps take away
        assertEquals(5.0, MfgServices.movementDiff(MfgServices.MOVE_RECEIPT, 5.0), 1e-9);
        assertEquals(5.0, MfgServices.movementDiff(MfgServices.MOVE_RETURN, 5.0), 1e-9);
        assertEquals(-5.0, MfgServices.movementDiff(MfgServices.MOVE_ISSUE, 5.0), 1e-9);
        assertEquals(-5.0, MfgServices.movementDiff(MfgServices.MOVE_SCRAP, 5.0), 1e-9);
        // a count and an adjustment carry their own sign
        assertEquals(-2.0, MfgServices.movementDiff(MfgServices.MOVE_COUNT, -2.0), 1e-9);
        assertEquals(-2.0, MfgServices.movementDiff(MfgServices.MOVE_ADJUST, -2.0), 1e-9);
        // a reservation changes no quantity on hand
        assertEquals(0.0, MfgServices.movementDiff(MfgServices.MOVE_RESERVE, 5.0), 1e-9);
        assertEquals(0.0, MfgServices.movementDiff(MfgServices.MOVE_RELEASE, 5.0), 1e-9);
        assertEquals(0.0, MfgServices.movementDiff(null, 5.0), 1e-9);

        assertTrue(MfgServices.isKnownMovement(MfgServices.MOVE_COUNT));
        assertFalse(MfgServices.isKnownMovement("MFG_MOVE_BOGUS"));
        assertFalse(MfgServices.isKnownMovement(null));
    }

    @Test
    public void blocksTheStockOfAFailedLot() {
        assertEquals(10.0, MfgServices.availableOf(10.0, MfgServices.IQC_PASS), 1e-9);
        // a pending lot may still be issued, a small workshop does not inspect everything
        assertEquals(10.0, MfgServices.availableOf(10.0, MfgServices.IQC_PENDING), 1e-9);
        assertEquals(10.0, MfgServices.availableOf(10.0, MfgServices.IQC_CONCESSION), 1e-9);
        assertEquals(0.0, MfgServices.availableOf(10.0, MfgServices.IQC_FAIL), 1e-9);
        // an overdrawn lot never offers a negative quantity
        assertEquals(0.0, MfgServices.availableOf(-3.0, MfgServices.IQC_PASS), 1e-9);
    }

    @Test
    public void takesTheReservationOutOfTheAvailableQuantity() {
        assertEquals(6.0, MfgServices.availableOf(10.0, 4.0, MfgServices.IQC_PASS), 1e-9);
        // over reserved stock offers nothing, not a negative number
        assertEquals(0.0, MfgServices.availableOf(10.0, 12.0, MfgServices.IQC_PASS), 1e-9);
        // a failed lot is not available at all
        assertEquals(0.0, MfgServices.availableOf(10.0, 4.0, MfgServices.IQC_FAIL), 1e-9);
    }

    @Test
    public void namesTheFinishedGoodsLotAfterItsWorkOrder() {
        assertEquals("FG-WO-261022-01", MfgServices.productLotId("WO-261022-01"));
        assertNull(MfgServices.productLotId(null));
    }

    @Test
    public void spreadsTheActualCostOverTheGoodUnits() {
        // 300 yuan for 4 good units is 75 each
        assertEquals(75.0, MfgServices.productUnitCost(300.0, 4.0), 1e-9);
        // nothing good came out of the order, so there is no unit cost
        assertEquals(0.0, MfgServices.productUnitCost(300.0, 0.0), 1e-9);
        // two decimals, because it is money
        assertEquals(10.01, MfgServices.productUnitCost(30.02, 3.0), 1e-9);
    }

    @Test
    public void keepsOnlyWhatTheOrderStillOwes() {
        assertEquals(10.0, MfgServices.openQtyOf(10.0, 0.0, 0.0), 1e-9);
        // 10 ordered, 4 on the shelf, 2 already out of the door
        assertEquals(4.0, MfgServices.openQtyOf(10.0, 4.0, 2.0), 1e-9);
        // over reserved or over shipped never gives a negative
        assertEquals(0.0, MfgServices.openQtyOf(5.0, 6.0, 0.0), 1e-9);
        assertEquals(0.0, MfgServices.openQtyOf(5.0, 0.0, 6.0), 1e-9);
    }

    @Test
    public void plansOnlyTheUnitsProductionWillNotCover() {
        // 10 ordered, 4 locked from the shelf, 3 more coming: 3 have to be planned
        assertEquals(3.0, MfgServices.productionQtyOf(10.0, 4.0, 0.0, 3.0), 1e-9);
        // production already covers what is still owed
        assertEquals(0.0, MfgServices.productionQtyOf(10.0, 4.0, 0.0, 9.0), 1e-9);
        // what is already shipped is not planned again
        assertEquals(2.0, MfgServices.productionQtyOf(10.0, 4.0, 4.0, 0.0), 1e-9);
    }

    @Test
    public void readsTheOrderLinesOfTheForm() {
        List<Map<String, Object>> lines = MfgServices.parseOrderLines(
                "# 型号,数量\r\nBT5-PRO,20,2026-11-30\n\n  BT5-LITE , 5 \nbadline,0\n");
        assertEquals(3, lines.size());
        assertEquals("BT5-PRO", lines.get(0).get("productCode"));
        assertEquals(20.0, ((Double) lines.get(0).get("qty")).doubleValue(), 1e-9);
        assertEquals(java.sql.Date.valueOf("2026-11-30"), lines.get(0).get("lineDueDate"));
        // spaces around the cells are trimmed and a missing due date stays empty
        assertEquals("BT5-LITE", lines.get(1).get("productCode"));
        assertFalse(lines.get(1).containsKey("lineDueDate"));
        // a zero quantity is kept here, the service refuses it with a clear message
        assertEquals(0.0, ((Double) lines.get(2).get("qty")).doubleValue(), 1e-9);
    }

    @Test
    public void knowsTheSalesOrderStatuses() {
        assertTrue(MfgServices.isKnownSoStatus(MfgServices.SO_PLANNED));
        assertFalse(MfgServices.isKnownSoStatus("MFG_SO_BOGUS"));
        assertFalse(MfgServices.isKnownSoStatus(null));
    }

    @Test
    public void knowsTheLocationTypes() {
        assertTrue(MfgServices.isKnownLocationType(MfgServices.LOC_WAREHOUSE));
        assertTrue(MfgServices.isKnownLocationType(MfgServices.LOC_ZONE));
        assertTrue(MfgServices.isKnownLocationType(MfgServices.LOC_BIN));
        assertFalse(MfgServices.isKnownLocationType("MFG_LOC_BOGUS"));
        assertFalse(MfgServices.isKnownLocationType(null));
    }

    @Test
    public void pricesTheOutsourcedFeeByItsBasis() {
        // per board: the price is multiplied by the units given
        assertEquals(120.0, MfgServices.outsourceAmount(MfgServices.OUTSRC_PER_UNIT, 30.0, 4.0, 0.0, 0.0), 1e-9);
        // per board without a quantity falls back to the good units, then to the planned ones
        assertEquals(90.0, MfgServices.outsourceAmount(MfgServices.OUTSRC_PER_UNIT, 30.0, 0.0, 3.0, 10.0), 1e-9);
        assertEquals(300.0, MfgServices.outsourceAmount(MfgServices.OUTSRC_PER_UNIT, 30.0, 0.0, 0.0, 10.0), 1e-9);
        // per placement point: always the points given
        assertEquals(25.0, MfgServices.outsourceAmount(MfgServices.OUTSRC_PER_POINT, 0.05, 500.0, 3.0, 10.0), 1e-9);
        // a lump sum is its own amount, whatever else is given
        assertEquals(88.5, MfgServices.outsourceAmount(MfgServices.OUTSRC_LUMP, 88.5, 7.0, 3.0, 10.0), 1e-9);
    }

    @Test
    public void spreadsTheOutsourcedFeeOverTheUnits() {
        // 120 yuan over 4 good units is 30 each
        assertEquals(30.0, MfgServices.outsourcePerUnit(120.0, 4.0, 10.0), 1e-9);
        // nothing finished yet: the planned units carry it, so the estimate is still meaningful
        assertEquals(12.0, MfgServices.outsourcePerUnit(120.0, 0.0, 10.0), 1e-9);
        // no unit at all: no share, never a division by zero
        assertEquals(0.0, MfgServices.outsourcePerUnit(120.0, 0.0, 0.0), 1e-9);
    }

    @Test
    public void knowsTheOutsourcedFeeCodes() {
        assertTrue(MfgServices.isKnownOutsourceType(MfgServices.OUTSRC_SMT));
        assertFalse(MfgServices.isKnownOutsourceType("MFG_OUTSRC_BOGUS"));
        assertTrue(MfgServices.isKnownOutsourceBasis(MfgServices.OUTSRC_PER_POINT));
        assertFalse(MfgServices.isKnownOutsourceBasis("MFG_OUTSRC_BOGUS"));
    }

    @Test
    public void normalizesAccountNames() {
        // trimmed and lowered, so Admin and admin cannot become two accounts
        assertEquals("zhang.san", MfgServices.normalizeAccountName("  Zhang.San  "));
        assertEquals("wh-01", MfgServices.normalizeAccountName("WH-01"));
        assertNull(MfgServices.normalizeAccountName("   "));
        assertNull(MfgServices.normalizeAccountName(null));
    }

    @Test
    public void checksAccountNames() {
        assertNull(MfgServices.accountNameProblem("zhang.san"));
        assertNull(MfgServices.accountNameProblem("wh_01"));
        assertNull(MfgServices.accountNameProblem("a1234567890123456789"));
        // empty, too long (the field is id-ne = 20) or with characters OFBiz does not expect
        assertEquals("账号不能为空", MfgServices.accountNameProblem(null));
        assertEquals("账号最多 20 个字符", MfgServices.accountNameProblem("a12345678901234567890"));
        assertEquals("账号只能用字母、数字、点、下划线、短横线", MfgServices.accountNameProblem("张 三"));
        assertEquals("账号只能用字母、数字、点、下划线、短横线", MfgServices.accountNameProblem("zhang san"));
    }

    @Test
    public void joinsThePersonName() {
        assertEquals("张 三", MfgServices.displayNameOf("张", null, "三"));
        assertEquals("San Zhang", MfgServices.displayNameOf("San", null, "Zhang"));
        // the middle name is rare here but must not leave a double space
        assertEquals("San Q Zhang", MfgServices.displayNameOf("San", "Q", "Zhang"));
        assertEquals("", MfgServices.displayNameOf(null, "  ", null));
    }

    @Test
    public void detectsTheBomFormat() {
        // Fritzing writes a header with a semicolon; everything else stays the component format
        assertTrue(MfgServices.detectBomFormat("Label;Part Type;mn;mpn;part number;\nBT1;Supercapacitor;;;SE-5R5;\n")
                == MfgServices.BomFormat.FRITZING);
        assertTrue(MfgServices.detectBomFormat("\uFEFFAmount;Part Type;mn;mpn;part number;\n1;Resistor;;;;\n")
                == MfgServices.BomFormat.FRITZING);
        assertTrue(MfgServices.detectBomFormat("R0402-10K,10K 电阻,4,R1-R4,N,0.01\n")
                == MfgServices.BomFormat.MFG);
        assertTrue(MfgServices.detectBomFormat("") == MfgServices.BomFormat.MFG);
        assertTrue(MfgServices.detectBomFormat(null) == MfgServices.BomFormat.MFG);
    }

    @Test
    public void parsesAFritzingBom() {
        String csv = "Label;Part Type;mn;mpn;part number;\n"
                + "BT1;Supercapacitor 1.5F 5.5V;;;SE-5R5-D155VYC3G;\n"
                + "J1;PIN HEADER 1x4;;;PinHeader-1x4;\n"
                + "J3;PIN HEADER 1x4;;;PinHeader-1x4;\n"
                + "R1;4.7kΩ Resistor;;;;\n"
                + "R2;4.7kΩ Resistor;;;;\n"
                + "\n"
                + "Amount;Part Type;mn;mpn;part number;\n"
                + "1;Supercapacitor 1.5F 5.5V;;;SE-5R5-D155VYC3G;\n"
                + "2;PIN HEADER 1x4;;;PinHeader-1x4;\n"
                + "2;4.7kΩ Resistor;;;;\n";
        List<String[]> rows = MfgServices.parseFritzingBom(csv);

        assertEquals(3, rows.size());
        // the part number becomes the code, the amount block the quantity
        assertEquals("SE-5R5-D155VYC3G", rows.get(0)[0]);
        assertEquals("Supercapacitor 1.5F 5.5V", rows.get(0)[1]);
        assertEquals("1", rows.get(0)[2]);
        assertEquals("BT1", rows.get(0)[3]);
        assertEquals("N", rows.get(0)[4]);
        assertEquals("PINHEADER-1X4", rows.get(1)[0]);
        assertEquals("2", rows.get(1)[2]);
        assertEquals("J1,J3", rows.get(1)[3]);
        // no part number: the name is used as the code, upper cased
        assertEquals("4.7KΩ RESISTOR", rows.get(2)[0]);
        assertEquals("2", rows.get(2)[2]);
        assertEquals("R1,R2", rows.get(2)[3]);
    }

    @Test
    public void countsAFritzingBomWithoutTheAmountBlock() {
        // a schematic export without the amount block: one row per designator
        String csv = "Label;Part Type;mn;mpn;part number;\n"
                + "J1;PIN HEADER 1x4;;;PinHeader-1x4;\n"
                + "J3;PIN HEADER 1x4;;;PinHeader-1x4;\n";
        List<String[]> rows = MfgServices.parseFritzingBom(csv);

        assertEquals(1, rows.size());
        assertEquals("2", rows.get(0)[2]);
        assertEquals("J1,J3", rows.get(0)[3]);
    }

    @Test
    public void parsesTheBomAfterDetectingTheFormat() {
        String fritzing = "Label;Part Type;mn;mpn;part number;\nU1;CH32V203F8U6;;;CH32V203F8U6;\n";
        List<String[]> detected = MfgServices.parseBom(fritzing, null);
        assertEquals(1, detected.size());
        assertEquals("CH32V203F8U6", detected.get(0)[0]);

        // the format can be forced, which also works for text that looks like neither
        List<String[]> forced = MfgServices.parseBom("U9;SoC;;;U9-CHIP;\n", MfgServices.BomFormat.FRITZING);
        assertEquals(1, forced.size());
        assertEquals("U9-CHIP", forced.get(0)[0]);
    }

    @Test
    public void checksTheNewPassword() {
        // too short, typed twice differently, or the same as the current one
        assertEquals("新密码至少 5 个字符", MfgServices.passwordProblem("ofbiz", "abcd", "abcd"));
        assertEquals("新密码至少 5 个字符", MfgServices.passwordProblem("ofbiz", null, "abcd1234"));
        assertEquals("两次输入的新密码不一致", MfgServices.passwordProblem("ofbiz", "abcde", "abcdef"));
        assertEquals("两次输入的新密码不一致", MfgServices.passwordProblem("ofbiz", "abcde", null));
        assertEquals("新密码不能和当前密码相同", MfgServices.passwordProblem("ofbiz", "ofbiz", "ofbiz"));
        assertNull(MfgServices.passwordProblem("ofbiz", "Mfg2026", "Mfg2026"));
        assertNull(MfgServices.passwordProblem(null, "Mfg2026", "Mfg2026"));
    }

    @Test
    public void explainsAnOrderImportThatClashed() throws Exception {
        // a second import of the same order, or a second click while the first is still running
        String clashed = MfgServices.receiptImportFailure(new org.apache.ofbiz.entity.GenericEntityException(
                "Error while inserting: [GenericEntity:MfgPurchaseReceiptLine] (SQL Exception while executing the"
                        + " following:INSERT INTO OFBIZ.MFG_PURCHASE_RECEIPT_LINE ... duplicate key value in a"
                        + " unique or primary key constraint)"));
        assertTrue(clashed.contains("正在入库"));
        assertFalse(clashed.contains("SQL Exception"));
        assertFalse(clashed.contains("MFG_PURCHASE_RECEIPT_LINE"));
    }

    @Test
    public void explainsAnOrderImportThatWasTooLong() throws Exception {
        String tooLong = MfgServices.receiptImportFailure(new org.apache.ofbiz.entity.GenericEntityException(
                "A truncation error was encountered trying to shrink VARCHAR 'x' to length 100"));
        assertTrue(tooLong.contains("100"));
        assertTrue(tooLong.contains("料号"));
    }

    @Test
    public void keepsOnlyTheReasonOfAnotherImportFailure() throws Exception {
        String other = MfgServices.receiptImportFailure(new org.apache.ofbiz.entity.GenericEntityException(
                "Something odd happened. (SQL Exception while executing ...)"));
        assertTrue(other.startsWith("导入失败"));
        assertTrue(other.contains("Something odd happened."));
        assertFalse(other.contains("SQL Exception"));
    }
}
