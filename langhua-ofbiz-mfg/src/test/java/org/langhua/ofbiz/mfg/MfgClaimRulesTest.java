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
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.time.LocalDate;

import org.junit.Test;

/**
 * The after sales arithmetic: the warranty and Taobao windows of a delivery, the buyer key and its
 * risk level, the flags that stop applying, the percentages, the age buckets, the cost of a claim
 * and the labels of the dashboard axis.
 */
public class MfgClaimRulesTest {

    @Test
    public void computesTheWarrantyEndDate() {
        java.sql.Date shipped = java.sql.Date.valueOf("2026-10-01");

        assertEquals("2027-10-01", MfgClaimServices.warrantyEndDate(shipped, 12).toString());
        assertEquals("2028-04-01", MfgClaimServices.warrantyEndDate(shipped, 18).toString());
        // an unset or invalid period falls back to 12 months
        assertEquals("2027-10-01", MfgClaimServices.warrantyEndDate(shipped, 0).toString());
        assertNull(MfgClaimServices.warrantyEndDate(null, 12));
    }

    @Test
    public void tellsWhetherTheClaimIsInWarranty() {
        java.sql.Date end = java.sql.Date.valueOf("2027-10-01");

        assertTrue(MfgClaimServices.isInWarranty(end, java.sql.Date.valueOf("2026-10-01")));
        assertTrue(MfgClaimServices.isInWarranty(end, java.sql.Date.valueOf("2027-10-01")));
        assertTrue(!MfgClaimServices.isInWarranty(end, java.sql.Date.valueOf("2027-10-02")));
        assertTrue(!MfgClaimServices.isInWarranty(null, java.sql.Date.valueOf("2026-10-01")));
        assertTrue(!MfgClaimServices.isInWarranty(end, null));
    }

    @Test
    public void endsTheNoReasonWindowEightDaysAfterSigning() {
        java.sql.Date signed = java.sql.Date.valueOf("2026-10-01");

        // midnight after the signed day, plus 168 hours
        assertEquals("2026-10-09 00:00:00.0", MfgClaimServices.sevenDayDeadline(signed).toString());
        assertNull(MfgClaimServices.sevenDayDeadline(null));
    }

    @Test
    public void endsTheAfterSalesWindowFifteenDaysAfterSigning() {
        java.sql.Date signed = java.sql.Date.valueOf("2026-10-01");

        assertEquals("2026-10-16 23:59:59.0", MfgClaimServices.afterSaleDeadline(signed).toString());
        assertNull(MfgClaimServices.afterSaleDeadline(null));
    }

    @Test
    public void tellsWhetherTheReturnIsInsideTheNoReasonWindow() {
        java.sql.Date signed = java.sql.Date.valueOf("2026-10-01");

        assertTrue(MfgClaimServices.isWithinSevenDays(signed, java.sql.Date.valueOf("2026-10-01")));
        assertTrue(MfgClaimServices.isWithinSevenDays(signed, java.sql.Date.valueOf("2026-10-08")));
        assertTrue(!MfgClaimServices.isWithinSevenDays(signed, java.sql.Date.valueOf("2026-10-09")));
        assertTrue(!MfgClaimServices.isWithinSevenDays(null, java.sql.Date.valueOf("2026-10-01")));
        assertTrue(!MfgClaimServices.isWithinSevenDays(signed, null));
    }

    @Test
    public void countsTheHoursLeftUntilADeadline() {
        java.sql.Timestamp deadline = java.sql.Timestamp.valueOf("2026-10-16 12:00:00");
        java.time.LocalDateTime now = java.time.LocalDateTime.of(2026, 10, 15, 10, 30);

        assertEquals(25L, MfgSupport.hoursLeft(deadline, now));
        assertEquals(-2L, MfgSupport.hoursLeft(deadline,
                java.time.LocalDateTime.of(2026, 10, 16, 13, 30)));
        assertEquals(0L, MfgSupport.hoursLeft(null, now));
    }

    @Test
    public void flagsDeadlinesThatExpireSoon() {
        assertTrue(MfgClaimServices.isExpiringSoon(MfgConstants.DEADLINE_WARNING_HOURS));
        assertTrue(MfgClaimServices.isExpiringSoon(0L));
        assertTrue(!MfgClaimServices.isExpiringSoon(MfgConstants.DEADLINE_WARNING_HOURS + 1));
        assertTrue(!MfgClaimServices.isExpiringSoon(-1L));
    }

    @Test
    public void picksTheDefaultFreightPayerByClaimType() {
        assertEquals(MfgConstants.FREIGHT_BUYER, MfgClaimServices.defaultFreightPayer(MfgConstants.CLAIM_RTN));
        assertEquals(MfgConstants.FREIGHT_BUYER, MfgClaimServices.defaultFreightPayer(MfgConstants.CLAIM_EXCH));
        assertEquals(MfgConstants.FREIGHT_SELLER, MfgClaimServices.defaultFreightPayer(MfgConstants.CLAIM_REPAIR));
        assertEquals(MfgConstants.FREIGHT_SELLER, MfgClaimServices.defaultFreightPayer(MfgConstants.CLAIM_REFUND_ONLY));
    }

    @Test
    public void normalizesMobileNumbers() {
        assertEquals("13800002222", MfgClaimServices.normalizeMobile("138-0000-2222"));
        assertEquals("13800002222", MfgClaimServices.normalizeMobile("+86 138 0000 2222".substring(3)));
        assertEquals("13800002222", MfgClaimServices.normalizeMobile(" 13800002222 "));
        assertNull(MfgClaimServices.normalizeMobile("123"));
        // a masked number cannot identify a buyer, so it must never become a key
        assertNull(MfgClaimServices.normalizeMobile("138****2222"));
        assertNull(MfgClaimServices.normalizeMobile("138xxxx2222"));
        assertNull(MfgClaimServices.normalizeMobile(null));
    }

    @Test
    public void buildsTheBuyerKeyFromMobileOrNick() {
        // the nickname wins: it is always available, the mobile is often masked
        assertEquals("TB:N:nick", MfgClaimServices.buyerKey(MfgConstants.CHANNEL_TAOBAO, "138-0000-2222", "nick"));
        assertEquals("TB:N:buyer_1", MfgClaimServices.buyerKey(MfgConstants.CHANNEL_TAOBAO, null, "Buyer_1"));
        assertEquals("TB:13800002222", MfgClaimServices.buyerKey(MfgConstants.CHANNEL_TAOBAO, "13800002222", null));
        assertEquals("TB:13800002222", MfgClaimServices.buyerKey(null, "13800002222", "  "));
        assertNull(MfgClaimServices.buyerKey(MfgConstants.CHANNEL_TAOBAO, "138****2222", null));
        assertNull(MfgClaimServices.buyerKey(MfgConstants.CHANNEL_TAOBAO, null, "  "));
    }

    @Test
    public void masksMobileNumbers() {
        assertEquals("138****2222", MfgClaimServices.maskMobile("13800002222"));
        assertEquals("138****2222", MfgClaimServices.maskMobile("138-0000-2222"));
        // a number that is already masked or incomplete is shown as it came
        assertEquals("138****2222", MfgClaimServices.maskMobile("138****2222"));
        assertEquals("1234567", MfgClaimServices.maskMobile("1234567"));
        assertEquals("", MfgClaimServices.maskMobile(null));
    }

    @Test
    public void suggestsARiskLevelFromTheHistory() {
        assertNull(MfgClaimServices.suggestRiskLevel(0, 0.0));
        assertNull(MfgClaimServices.suggestRiskLevel(2, 499.0));
        assertEquals(MfgConstants.RISK_WATCH, MfgClaimServices.suggestRiskLevel(3, 0.0));
        assertEquals(MfgConstants.RISK_WATCH, MfgClaimServices.suggestRiskLevel(1, 500.0));
        assertEquals(MfgConstants.RISK_BLOCK, MfgClaimServices.suggestRiskLevel(5, 0.0));
        assertEquals(MfgConstants.RISK_BLOCK, MfgClaimServices.suggestRiskLevel(1, 1000.0));
    }

    @Test
    public void onlyActiveFlagsApply() {
        assertTrue(MfgClaimServices.isFlagActive(MfgConstants.FLAG_ACTIVE));
        assertTrue(!MfgClaimServices.isFlagActive(MfgConstants.FLAG_RELEASED));
        assertTrue(!MfgClaimServices.isFlagActive(null));
    }

    @Test
    public void flagsStopApplyingWhenTheyExpire() {
        java.sql.Date today = java.sql.Date.valueOf("2026-10-21");

        assertTrue(MfgClaimServices.isFlagLive(MfgConstants.FLAG_ACTIVE, java.sql.Date.valueOf("2027-10-21"), today));
        assertTrue(MfgClaimServices.isFlagLive(MfgConstants.FLAG_ACTIVE, today, today));
        assertTrue(!MfgClaimServices.isFlagLive(MfgConstants.FLAG_ACTIVE, java.sql.Date.valueOf("2026-10-20"), today));
        // no expiry means it keeps applying while active
        assertTrue(MfgClaimServices.isFlagLive(MfgConstants.FLAG_ACTIVE, null, today));
        assertTrue(!MfgClaimServices.isFlagLive(MfgConstants.FLAG_RELEASED, null, today));
    }

    @Test
    public void formatsPercentages() {
        assertEquals("2.5", MfgSupport.percent(1, 40));
        assertEquals("0.0", MfgSupport.percent(0, 40));
        assertEquals("100.0", MfgSupport.percent(7, 7));
        // no denominator, no percentage
        assertEquals("-", MfgSupport.percent(3, 0));
        assertEquals("5.0", MfgSupport.percentOf(50.0, 1000.0));
        assertEquals("-", MfgSupport.percentOf(50.0, 0.0));
    }

    @Test
    public void agesClaimsAndBucketsThem() {
        assertEquals(0, MfgSupport.daysBetween(java.sql.Date.valueOf("2026-10-01"),
                java.sql.Date.valueOf("2026-10-01")));
        assertEquals(30, MfgSupport.daysBetween(java.sql.Date.valueOf("2026-10-01"),
                java.sql.Date.valueOf("2026-10-31")));
        assertEquals(-1, MfgSupport.daysBetween(null, java.sql.Date.valueOf("2026-10-31")));

        assertEquals("0-30", MfgSupport.ageBucket(0));
        assertEquals("0-30", MfgSupport.ageBucket(30));
        assertEquals("31-90", MfgSupport.ageBucket(31));
        assertEquals("91-180", MfgSupport.ageBucket(180));
        assertEquals(">180", MfgSupport.ageBucket(181));
        assertEquals("未知", MfgSupport.ageBucket(-1));
    }

    @Test
    public void addsUpTheCostOfOneClaim() {
        // refund + freight + one hour of labour
        assertEquals(200.0, MfgReportServices.claimCost(150.0, 20.0, 0.5, 60.0), 0.001);
        assertEquals(0.0, MfgReportServices.claimCost(0.0, 0.0, 0.0, 60.0), 0.001);
    }

    @Test
    public void tellsWhetherARedLineIsCrossed() {
        assertTrue(MfgClaimServices.isOverLimit(3, 100, 2.0));
        assertTrue(!MfgClaimServices.isOverLimit(2, 100, 2.0));
        assertTrue(!MfgClaimServices.isOverLimit(3, 0, 2.0));
        assertTrue(MfgClaimServices.isOverLimit(60.0, 1000.0, 5.0));
    }

    @Test
    public void listsTheLastDaysOldestFirst() {
        java.time.LocalDate end = LocalDate.of(2026, 10, 21);

        java.util.List<String> labels = MfgReportServices.lastDayLabels(end, 3);
        assertEquals(3, labels.size());
        assertEquals("10-19", labels.get(0));
        assertEquals("10-20", labels.get(1));
        assertEquals("10-21", labels.get(2));

        assertTrue(MfgReportServices.lastDayLabels(end, 0).isEmpty());
        assertTrue(MfgReportServices.lastDayLabels(null, 5).isEmpty());
    }

    @Test
    public void scalesBarsAgainstTheLargestValue() {
        assertEquals(100L, MfgSupport.percentOfMax(10, 10));
        assertEquals(50L, MfgSupport.percentOfMax(5, 10));
        assertEquals(0L, MfgSupport.percentOfMax(0, 10));
        // a value that exists is always visible, even when it is tiny
        assertEquals(2L, MfgSupport.percentOfMax(1, 1000));
        assertEquals(100L, MfgSupport.percentOfMax(3, 0));
    }
}
