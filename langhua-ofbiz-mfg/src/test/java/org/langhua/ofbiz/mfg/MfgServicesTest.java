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

import java.time.LocalDate;
import java.util.List;

import org.junit.Test;

/**
 * Unit tests of the pure helper functions of {@link MfgServices}.
 *
 * <p>They cover the parts that do not need a database: BOM CSV parsing, the serial
 * number and work order number formats.
 */
public class MfgServicesTest {

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
}
