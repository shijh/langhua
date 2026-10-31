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
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.Test;

/**
 * The helpers of the shop floor itself: incoming inspection, the cost of a unit, the software
 * quote, the trends, the inventory movements and their reasons, the sales order arithmetic, the
 * locations, the outside processing fees, the account names and the messages of a refused import.
 */
public class MfgDomainRulesTest {

    private static boolean present(String message) {
        return message != null;
    }

    @Test
    public void onlyAFailedLotBlocksIssuing() {
        assertTrue(MfgWorkOrderServices.isLotUsable(null));
        assertTrue(MfgWorkOrderServices.isLotUsable(MfgConstants.IQC_PENDING));
        assertTrue(MfgWorkOrderServices.isLotUsable(MfgConstants.IQC_PASS));
        assertTrue(MfgWorkOrderServices.isLotUsable(MfgConstants.IQC_CONCESSION));
        assertTrue(!MfgWorkOrderServices.isLotUsable(MfgConstants.IQC_FAIL));
        // the rule is "only a failure blocks", so an unknown status must not lock the shop floor out
        assertTrue(MfgWorkOrderServices.isLotUsable("MFG_IQC_BOGUS"));
    }

    @Test
    public void normalizesTheInspectionStatus() {        // an empty value means "not inspected yet", not an error
        assertEquals(MfgConstants.IQC_PENDING, MfgStockServices.normalizeInspectStatus(null));
        assertEquals(MfgConstants.IQC_PENDING, MfgStockServices.normalizeInspectStatus("   "));
        assertEquals(MfgConstants.IQC_PASS, MfgStockServices.normalizeInspectStatus("MFG_IQC_PASS"));
        assertEquals(MfgConstants.IQC_PASS, MfgStockServices.normalizeInspectStatus("mfg_iqc_pass"));
        assertEquals(MfgConstants.IQC_CONCESSION, MfgStockServices.normalizeInspectStatus("MFG_IQC_CONCESSION"));
        assertEquals(MfgConstants.IQC_FAIL, MfgStockServices.normalizeInspectStatus("MFG_IQC_FAIL"));
        assertNull(MfgStockServices.normalizeInspectStatus("MFG_IQC_BOGUS"));
        assertEquals(4, MfgConstants.INSPECT_STATUSES.length);
    }

    @Test
    public void restatesTheUnitPriceWhenTheCountDiffersFromTheOrder() {
        // a PCB order prints "x1" and ships five boards for 21 yuan: five boards are 4.20 each, not 21
        assertEquals(4.2, MfgStockServices.restatedUnitPrice(21.0, 5.0, 21.0), 1e-9);
        // three boards for 21 yuan
        assertEquals(7.0, MfgStockServices.restatedUnitPrice(21.0, 3.0, 21.0), 1e-9);
        // no amount on the order: keep the price that is there, do not invent one
        assertEquals(2.5, MfgStockServices.restatedUnitPrice(0.0, 5.0, 2.5), 1e-9);
        // a nonsense quantity never divides
        assertEquals(2.5, MfgStockServices.restatedUnitPrice(21.0, 0.0, 2.5), 1e-9);
        assertEquals(0.0, MfgStockServices.restatedUnitPrice(0.0, 5.0, 0.0), 1e-9);
    }

    @Test
    public void costsOneUnitWithTheBomAndTheLaborRate() {
        java.util.Map<String, Object> cost = MfgProductServices.costSummary(18.5, 0.45, 1.0, 19.0, 39.0);

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
        java.util.Map<String, Object> cost = MfgProductServices.costSummary(10.0 / 3.0, 0.0, 3.0, null, null);

        assertEquals(10.0, ((Double) cost.get("totalCost")).doubleValue(), 1e-9);
        assertEquals(3.33, ((Double) cost.get("unitCost")).doubleValue(), 1e-9);
        // no target price means no margin, not a division by zero
        assertNull(cost.get("unitMargin"));
        assertNull(cost.get("marginRate"));
        assertNull(cost.get("targetDifference"));
    }

    @Test
    public void quotesSoftwareFromFunctionPoints() {
        // the 2019 assessment case: 10 function points, SWF 1, PDR 40, 880 per person day
        java.util.Map<String, Object> cost = MfgSoftwareServices.softwareCost(0.0, 0.0, Double.valueOf(10.0),
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
        java.util.Map<String, Object> cost = MfgSoftwareServices.softwareCost(4.0, 2.0, null,
                1.5, -0.5, 0.1, 0.05, 1.0, 40.0, 880.0, 0.06, 0.05);

        assertEquals(170.0, ((Double) cost.get("ufp")).doubleValue(), 1e-9);
        assertEquals(1.15, ((Double) cost.get("swf")).doubleValue(), 1e-9);
        // 40 x 170 x 1.15 = 7820 hours
        assertEquals(7820.0, ((Double) cost.get("effortHours")).doubleValue(), 1e-9);
    }

    @Test
    public void knowsTheMedianOfTheProductivitySamples() {
        assertEquals(0.0, MfgSupport.median(null), 1e-9);
        assertEquals(0.0, MfgSupport.median(java.util.Collections.<Double>emptyList()), 1e-9);
        assertEquals(45.0, MfgSupport.median(java.util.Arrays.asList(
                Double.valueOf(45.0), Double.valueOf(40.0), Double.valueOf(50.0))), 1e-9);
        assertEquals(45.0, MfgSupport.median(java.util.Arrays.asList(
                Double.valueOf(40.0), Double.valueOf(50.0))), 1e-9);
    }

    @Test
    public void labelsThePeriodOfATrendBucket() {
        java.sql.Date date = java.sql.Date.valueOf(java.time.LocalDate.of(2026, 10, 21));

        assertEquals("2026-10-21", MfgSupport.periodKey(date, "DAY"));
        // 21 October 2026 is in ISO week 43
        assertEquals("2026-W43", MfgSupport.periodKey(date, "WEEK"));
        assertEquals("2026-10", MfgSupport.periodKey(date, "MONTH"));
        // an unknown grouping falls back to the month, a missing date has no period
        assertEquals("2026-10", MfgSupport.periodKey(date, "YEAR"));
        assertEquals("-", MfgSupport.periodKey(null, "MONTH"));
    }

    @Test
    public void countsYieldFromGoodAndRejectedUnits() {
        assertEquals(95.0, MfgSupport.yieldRate(95.0, 5.0), 1e-9);
        assertEquals(100.0, MfgSupport.yieldRate(10.0, 0.0), 1e-9);
        assertEquals(0.0, MfgSupport.yieldRate(0.0, 4.0), 1e-9);
        // nothing reported is not a division by zero
        assertEquals(0.0, MfgSupport.yieldRate(0.0, 0.0), 1e-9);
    }

    @Test
    public void countsHoursPerGoodUnit() {
        assertEquals(0.5, MfgSupport.hoursPerUnit(5.0, 10.0), 1e-9);
        assertEquals(0.0, MfgSupport.hoursPerUnit(5.0, 0.0), 1e-9);
        assertEquals(0.0, MfgSupport.hoursPerUnit(0.0, 10.0), 1e-9);
    }

    @Test
    public void ranksIssuesSoTheOpenHighOnesComeFirst() {
        assertTrue(MfgDevServices.isOpenIssue(MfgConstants.ISSUE_OPEN));
        assertTrue(MfgDevServices.isOpenIssue(MfgConstants.ISSUE_DOING));
        assertFalse(MfgDevServices.isOpenIssue(MfgConstants.ISSUE_CLOSED));
        assertFalse(MfgDevServices.isOpenIssue(null));

        assertTrue(MfgDevServices.statusRank(MfgConstants.ISSUE_OPEN)
                < MfgDevServices.statusRank(MfgConstants.ISSUE_DOING));
        assertTrue(MfgDevServices.statusRank(MfgConstants.ISSUE_DOING)
                < MfgDevServices.statusRank(MfgConstants.ISSUE_CLOSED));
        assertTrue(MfgDevServices.severityRank(MfgConstants.ISSUE_HIGH)
                > MfgDevServices.severityRank(MfgConstants.ISSUE_MEDIUM));
        assertTrue(MfgDevServices.severityRank(MfgConstants.ISSUE_MEDIUM)
                > MfgDevServices.severityRank(MfgConstants.ISSUE_LOW));

        assertTrue(MfgDevServices.isKnownIssueStatus(MfgConstants.ISSUE_DOING));
        assertFalse(MfgDevServices.isKnownIssueStatus("MFG_ISSUE_WHATEVER"));
        assertTrue(MfgDevServices.isKnownIssueSeverity(MfgConstants.ISSUE_HIGH));
        assertFalse(MfgDevServices.isKnownIssueSeverity("MFG_ISSUE_URGENT"));
    }

    @Test
    public void signsTheInventoryMovements() {
        // receipts and returns add, issues and scraps take away
        assertEquals(5.0, MfgStockLedger.movementDiff(MfgConstants.MOVE_RECEIPT, 5.0), 1e-9);
        assertEquals(5.0, MfgStockLedger.movementDiff(MfgConstants.MOVE_RETURN, 5.0), 1e-9);
        assertEquals(-5.0, MfgStockLedger.movementDiff(MfgConstants.MOVE_ISSUE, 5.0), 1e-9);
        assertEquals(-5.0, MfgStockLedger.movementDiff(MfgConstants.MOVE_SCRAP, 5.0), 1e-9);
        // a count and an adjustment carry their own sign
        assertEquals(-2.0, MfgStockLedger.movementDiff(MfgConstants.MOVE_COUNT, -2.0), 1e-9);
        assertEquals(-2.0, MfgStockLedger.movementDiff(MfgConstants.MOVE_ADJUST, -2.0), 1e-9);
        // a reservation changes no quantity on hand
        assertEquals(0.0, MfgStockLedger.movementDiff(MfgConstants.MOVE_RESERVE, 5.0), 1e-9);
        assertEquals(0.0, MfgStockLedger.movementDiff(MfgConstants.MOVE_RELEASE, 5.0), 1e-9);
        assertEquals(0.0, MfgStockLedger.movementDiff(null, 5.0), 1e-9);

        assertTrue(MfgStockLedger.isKnownMovement(MfgConstants.MOVE_COUNT));
        assertFalse(MfgStockLedger.isKnownMovement("MFG_MOVE_BOGUS"));
        assertFalse(MfgStockLedger.isKnownMovement(null));
    }

    @Test
    public void namesTheInventoryReasonsOfOfbiz() {
        // the ids are OFBiz's own IID_REASON list, so an export lines up with a standard install
        assertEquals(8, MfgConstants.MOVE_REASONS.length);
        assertEquals("VAR_LOST", MfgConstants.MOVE_REASONS[0]);
        assertEquals("VAR_MISSHIP_SHIPPED", MfgConstants.MOVE_REASONS[7]);
        assertEquals("\u4e22\u5931", MfgStockLedger.reasonLabel(MfgConstants.REASON_LOST));
        assertEquals("\u88ab\u76d7", MfgStockLedger.reasonLabel(MfgConstants.REASON_STOLEN));
        assertEquals("\u635f\u574f", MfgStockLedger.reasonLabel(MfgConstants.REASON_DAMAGED));
        assertTrue(MfgStockLedger.reasonLabel(MfgConstants.REASON_FOUND).contains("\u627e\u5230"));
        assertEquals("", MfgStockLedger.reasonLabel(null));
        assertEquals("VAR_WHATEVER", MfgStockLedger.reasonLabel("VAR_WHATEVER"));
    }

    @Test
    public void picksTheMovementTypeFromTheReason() {
        // what left the stock is booked out, what came back in is booked in
        assertEquals(MfgConstants.MOVE_SCRAP, MfgStockLedger.movementTypeOfReason(MfgConstants.REASON_LOST));
        assertEquals(MfgConstants.MOVE_SCRAP, MfgStockLedger.movementTypeOfReason(MfgConstants.REASON_STOLEN));
        assertEquals(MfgConstants.MOVE_SCRAP, MfgStockLedger.movementTypeOfReason(MfgConstants.REASON_DAMAGED));
        assertEquals(MfgConstants.MOVE_SCRAP, MfgStockLedger.movementTypeOfReason(MfgConstants.REASON_SAMPLE));
        assertEquals(MfgConstants.MOVE_SCRAP,
                MfgStockLedger.movementTypeOfReason(MfgConstants.REASON_MISSHIP_SHIPPED));
        assertEquals(MfgConstants.MOVE_ISSUE, MfgStockLedger.movementTypeOfReason(MfgConstants.REASON_INTEGR));
        assertEquals(MfgConstants.MOVE_RECEIPT, MfgStockLedger.movementTypeOfReason(MfgConstants.REASON_FOUND));
        assertEquals(MfgConstants.MOVE_RECEIPT,
                MfgStockLedger.movementTypeOfReason(MfgConstants.REASON_MISSHIP_ORDERED));
        assertNull(MfgStockLedger.movementTypeOfReason(null));
        assertNull(MfgStockLedger.movementTypeOfReason("VAR_WHATEVER"));
    }

    @Test
    public void refusesAReasonThatFightsWithTheType() {
        // nothing to complain about: no reason, or a reason that agrees with the type
        assertNull(MfgStockLedger.movementReasonProblem(MfgConstants.MOVE_SCRAP, null, 3.0));
        assertNull(MfgStockLedger.movementReasonProblem(MfgConstants.MOVE_SCRAP,
                MfgConstants.REASON_LOST, 3.0));
        assertNull(MfgStockLedger.movementReasonProblem(MfgConstants.MOVE_ADJUST,
                MfgConstants.REASON_DAMAGED, -2.0));
        assertNull(MfgStockLedger.movementReasonProblem(MfgConstants.MOVE_RECEIPT,
                MfgConstants.REASON_FOUND, 4.0));
        // a lost part may not be booked in, and a found one may not be booked out
        assertTrue(present(MfgStockLedger.movementReasonProblem(MfgConstants.MOVE_RECEIPT,
                MfgConstants.REASON_LOST, 3.0)));
        assertTrue(present(MfgStockLedger.movementReasonProblem(MfgConstants.MOVE_SCRAP,
                MfgConstants.REASON_FOUND, 3.0)));
        // an adjustment follows the sign of the quantity it carries
        assertTrue(present(MfgStockLedger.movementReasonProblem(MfgConstants.MOVE_ADJUST,
                MfgConstants.REASON_LOST, 2.0)));
        assertNull(MfgStockLedger.movementReasonProblem(MfgConstants.MOVE_ADJUST,
                MfgConstants.REASON_LOST, -2.0));
        // the message names the reason, so the person knows which one to keep
        String message = MfgStockLedger.movementReasonProblem(MfgConstants.MOVE_RECEIPT,
                MfgConstants.REASON_LOST, 3.0);
        assertTrue(message.contains("\u4e22\u5931"));
        assertTrue(message.contains("\u8865\u5165\u5e93") || message.contains("\u5230\u8d27\u5165\u5e93"));
        // an unknown reason is refused even when the type is fine
        assertTrue(present(MfgStockLedger.movementReasonProblem(MfgConstants.MOVE_SCRAP, "VAR_WHATEVER", 3.0)));
    }

    @Test
    public void explainsAStockCountVarianceWithTheReasonItFits() {
        // a count carries its own sign, so the variance of -2 fits "lost" and clashes with "found"
        assertNull(MfgStockLedger.movementReasonProblem(MfgConstants.MOVE_COUNT,
                MfgConstants.REASON_LOST, -2.0));
        assertNull(MfgStockLedger.movementReasonProblem(MfgConstants.MOVE_COUNT,
                MfgConstants.REASON_FOUND, 2.0));
        assertTrue(present(MfgStockLedger.movementReasonProblem(MfgConstants.MOVE_COUNT,
                MfgConstants.REASON_FOUND, -2.0)));
        assertTrue(present(MfgStockLedger.movementReasonProblem(MfgConstants.MOVE_COUNT,
                MfgConstants.REASON_LOST, 2.0)));
        // a count that matches the book quantity explains nothing, so any reason goes
        assertNull(MfgStockLedger.movementReasonProblem(MfgConstants.MOVE_COUNT,
                MfgConstants.REASON_LOST, 0.0));
        // and no reason at all is still fine
        assertNull(MfgStockLedger.movementReasonProblem(MfgConstants.MOVE_COUNT, null, -2.0));
        // the refusal talks about 盘盈 / 盘亏, not about the bucket it lands in
        String message = MfgStockLedger.movementReasonProblem(MfgConstants.MOVE_COUNT,
                MfgConstants.REASON_LOST, 6.0);
        assertTrue(message.contains("\u76d8\u76c8"));
        assertTrue(message.contains("\u4e22\u5931"));
    }

    @Test
    public void blocksTheStockOfAFailedLot() {
        assertEquals(10.0, MfgStockLedger.availableOf(10.0, MfgConstants.IQC_PASS), 1e-9);
        // a pending lot may still be issued, a small workshop does not inspect everything
        assertEquals(10.0, MfgStockLedger.availableOf(10.0, MfgConstants.IQC_PENDING), 1e-9);
        assertEquals(10.0, MfgStockLedger.availableOf(10.0, MfgConstants.IQC_CONCESSION), 1e-9);
        assertEquals(0.0, MfgStockLedger.availableOf(10.0, MfgConstants.IQC_FAIL), 1e-9);
        // an overdrawn lot never offers a negative quantity
        assertEquals(0.0, MfgStockLedger.availableOf(-3.0, MfgConstants.IQC_PASS), 1e-9);
    }

    @Test
    public void takesTheReservationOutOfTheAvailableQuantity() {
        assertEquals(6.0, MfgStockLedger.availableOf(10.0, 4.0, MfgConstants.IQC_PASS), 1e-9);
        // over reserved stock offers nothing, not a negative number
        assertEquals(0.0, MfgStockLedger.availableOf(10.0, 12.0, MfgConstants.IQC_PASS), 1e-9);
        // a failed lot is not available at all
        assertEquals(0.0, MfgStockLedger.availableOf(10.0, 4.0, MfgConstants.IQC_FAIL), 1e-9);
    }

    @Test
    public void namesTheFinishedGoodsLotAfterItsWorkOrder() {
        assertEquals("FG-WO-261022-01", MfgStockLedger.productLotId("WO-261022-01"));
        assertNull(MfgStockLedger.productLotId(null));
    }

    @Test
    public void spreadsTheActualCostOverTheGoodUnits() {
        // 300 yuan for 4 good units is 75 each
        assertEquals(75.0, MfgStockLedger.productUnitCost(300.0, 4.0), 1e-9);
        // nothing good came out of the order, so there is no unit cost
        assertEquals(0.0, MfgStockLedger.productUnitCost(300.0, 0.0), 1e-9);
        // two decimals, because it is money
        assertEquals(10.01, MfgStockLedger.productUnitCost(30.02, 3.0), 1e-9);
    }

    @Test
    public void keepsOnlyWhatTheOrderStillOwes() {
        assertEquals(10.0, MfgSalesOrderServices.openQtyOf(10.0, 0.0, 0.0), 1e-9);
        // 10 ordered, 4 on the shelf, 2 already out of the door
        assertEquals(4.0, MfgSalesOrderServices.openQtyOf(10.0, 4.0, 2.0), 1e-9);
        // over reserved or over shipped never gives a negative
        assertEquals(0.0, MfgSalesOrderServices.openQtyOf(5.0, 6.0, 0.0), 1e-9);
        assertEquals(0.0, MfgSalesOrderServices.openQtyOf(5.0, 0.0, 6.0), 1e-9);
    }

    @Test
    public void plansOnlyTheUnitsProductionWillNotCover() {
        // 10 ordered, 4 locked from the shelf, 3 more coming: 3 have to be planned
        assertEquals(3.0, MfgSalesOrderServices.productionQtyOf(10.0, 4.0, 0.0, 3.0), 1e-9);
        // production already covers what is still owed
        assertEquals(0.0, MfgSalesOrderServices.productionQtyOf(10.0, 4.0, 0.0, 9.0), 1e-9);
        // what is already shipped is not planned again
        assertEquals(2.0, MfgSalesOrderServices.productionQtyOf(10.0, 4.0, 4.0, 0.0), 1e-9);
    }

    @Test
    public void readsTheOrderLinesOfTheForm() {
        List<Map<String, Object>> lines = MfgCsv.parseOrderLines(
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
        assertTrue(MfgSalesOrderServices.isKnownSoStatus(MfgConstants.SO_PLANNED));
        assertFalse(MfgSalesOrderServices.isKnownSoStatus("MFG_SO_BOGUS"));
        assertFalse(MfgSalesOrderServices.isKnownSoStatus(null));
    }

    @Test
    public void knowsTheLocationTypes() {
        assertTrue(MfgStockServices.isKnownLocationType(MfgConstants.LOC_WAREHOUSE));
        assertTrue(MfgStockServices.isKnownLocationType(MfgConstants.LOC_ZONE));
        assertTrue(MfgStockServices.isKnownLocationType(MfgConstants.LOC_BIN));
        assertFalse(MfgStockServices.isKnownLocationType("MFG_LOC_BOGUS"));
        assertFalse(MfgStockServices.isKnownLocationType(null));
    }

    @Test
    public void pricesTheOutsourcedFeeByItsBasis() {
        // per board: the price is multiplied by the units given
        assertEquals(120.0, MfgWorkOrderServices.outsourceAmount(MfgConstants.OUTSRC_PER_UNIT, 30.0, 4.0, 0.0, 0.0), 1e-9);
        // per board without a quantity falls back to the good units, then to the planned ones
        assertEquals(90.0, MfgWorkOrderServices.outsourceAmount(MfgConstants.OUTSRC_PER_UNIT, 30.0, 0.0, 3.0, 10.0), 1e-9);
        assertEquals(300.0, MfgWorkOrderServices.outsourceAmount(MfgConstants.OUTSRC_PER_UNIT, 30.0, 0.0, 0.0, 10.0), 1e-9);
        // per placement point: always the points given
        assertEquals(25.0, MfgWorkOrderServices.outsourceAmount(MfgConstants.OUTSRC_PER_POINT, 0.05, 500.0, 3.0, 10.0), 1e-9);
        // a lump sum is its own amount, whatever else is given
        assertEquals(88.5, MfgWorkOrderServices.outsourceAmount(MfgConstants.OUTSRC_LUMP, 88.5, 7.0, 3.0, 10.0), 1e-9);
    }

    @Test
    public void spreadsTheOutsourcedFeeOverTheUnits() {
        // 120 yuan over 4 good units is 30 each
        assertEquals(30.0, MfgWorkOrderServices.outsourcePerUnit(120.0, 4.0, 10.0), 1e-9);
        // nothing finished yet: the planned units carry it, so the estimate is still meaningful
        assertEquals(12.0, MfgWorkOrderServices.outsourcePerUnit(120.0, 0.0, 10.0), 1e-9);
        // no unit at all: no share, never a division by zero
        assertEquals(0.0, MfgWorkOrderServices.outsourcePerUnit(120.0, 0.0, 0.0), 1e-9);
    }

    @Test
    public void knowsTheOutsourcedFeeCodes() {
        assertTrue(MfgWorkOrderServices.isKnownOutsourceType(MfgConstants.OUTSRC_SMT));
        assertFalse(MfgWorkOrderServices.isKnownOutsourceType("MFG_OUTSRC_BOGUS"));
        assertTrue(MfgWorkOrderServices.isKnownOutsourceBasis(MfgConstants.OUTSRC_PER_POINT));
        assertFalse(MfgWorkOrderServices.isKnownOutsourceBasis("MFG_OUTSRC_BOGUS"));
    }

    @Test
    public void normalizesAccountNames() {
        // trimmed and lowered, so Admin and admin cannot become two accounts
        assertEquals("zhang.san", MfgAccountServices.normalizeAccountName("  Zhang.San  "));
        assertEquals("wh-01", MfgAccountServices.normalizeAccountName("WH-01"));
        assertNull(MfgAccountServices.normalizeAccountName("   "));
        assertNull(MfgAccountServices.normalizeAccountName(null));
    }

    @Test
    public void checksAccountNames() {
        assertNull(MfgAccountServices.accountNameProblem("zhang.san"));
        assertNull(MfgAccountServices.accountNameProblem("wh_01"));
        assertNull(MfgAccountServices.accountNameProblem("a1234567890123456789"));
        // empty, too long (the field is id-ne = 20) or with characters OFBiz does not expect
        assertEquals("账号不能为空", MfgAccountServices.accountNameProblem(null));
        assertEquals("账号最多 20 个字符", MfgAccountServices.accountNameProblem("a12345678901234567890"));
        assertEquals("账号只能用字母、数字、点、下划线、短横线", MfgAccountServices.accountNameProblem("张 三"));
        assertEquals("账号只能用字母、数字、点、下划线、短横线", MfgAccountServices.accountNameProblem("zhang san"));
    }

    @Test
    public void joinsThePersonName() {
        assertEquals("张 三", MfgAccountServices.displayNameOf("张", null, "三"));
        assertEquals("San Zhang", MfgAccountServices.displayNameOf("San", null, "Zhang"));
        // the middle name is rare here but must not leave a double space
        assertEquals("San Q Zhang", MfgAccountServices.displayNameOf("San", "Q", "Zhang"));
        assertEquals("", MfgAccountServices.displayNameOf(null, "  ", null));
    }

    @Test
    public void checksTheNewPassword() {
        // too short, typed twice differently, or the same as the current one
        assertEquals("新密码至少 5 个字符", MfgAccountServices.passwordProblem("ofbiz", "abcd", "abcd"));
        assertEquals("新密码至少 5 个字符", MfgAccountServices.passwordProblem("ofbiz", null, "abcd1234"));
        assertEquals("两次输入的新密码不一致", MfgAccountServices.passwordProblem("ofbiz", "abcde", "abcdef"));
        assertEquals("两次输入的新密码不一致", MfgAccountServices.passwordProblem("ofbiz", "abcde", null));
        assertEquals("新密码不能和当前密码相同", MfgAccountServices.passwordProblem("ofbiz", "ofbiz", "ofbiz"));
        assertNull(MfgAccountServices.passwordProblem("ofbiz", "Mfg2026", "Mfg2026"));
        assertNull(MfgAccountServices.passwordProblem(null, "Mfg2026", "Mfg2026"));
    }

    @Test
    public void explainsAnOrderImportThatClashed() throws Exception {
        // a second import of the same order, or a second click while the first is still running
        String clashed = MfgPurchaseServices.receiptImportFailure(new org.apache.ofbiz.entity.GenericEntityException(
                "Error while inserting: [GenericEntity:MfgPurchaseReceiptLine] (SQL Exception while executing the"
                        + " following:INSERT INTO OFBIZ.MFG_PURCHASE_RECEIPT_LINE ... duplicate key value in a"
                        + " unique or primary key constraint)"));
        assertTrue(clashed.contains("正在入库"));
        assertFalse(clashed.contains("SQL Exception"));
        assertFalse(clashed.contains("MFG_PURCHASE_RECEIPT_LINE"));
    }

    @Test
    public void explainsAnOrderImportThatWasTooLong() throws Exception {
        String tooLong = MfgPurchaseServices.receiptImportFailure(new org.apache.ofbiz.entity.GenericEntityException(
                "A truncation error was encountered trying to shrink VARCHAR 'x' to length 100"));
        assertTrue(tooLong.contains("100"));
        assertTrue(tooLong.contains("料号"));
    }

    @Test
    public void keepsOnlyTheReasonOfAnotherImportFailure() throws Exception {
        String other = MfgPurchaseServices.receiptImportFailure(new org.apache.ofbiz.entity.GenericEntityException(
                "Something odd happened. (SQL Exception while executing ...)"));
        assertTrue(other.startsWith("导入失败"));
        assertTrue(other.contains("Something odd happened."));
        assertFalse(other.contains("SQL Exception"));
    }

    @Test
    public void pairsAReReadOfAnOrderLineForLine() {
        // the same screenshot read twice: as many lines as the receipt already holds, so row k belongs
        // to line k even when the recogniser spells the part number differently (SK-12D02 vs
        // 3高(V柄)带滚珠). Matching by part number alone made a second line and a second lot, and the
        // stock of that part counted twice (Step 188)
        assertTrue(MfgPurchaseServices.pairsByPosition(true, 1, 1));
        assertTrue(MfgPurchaseServices.pairsByPosition(true, 4, 4));
        // a plain second delivery is not a re-read: its lines are matched by part number
        assertFalse(MfgPurchaseServices.pairsByPosition(false, 4, 4));
        // an answer that shows more or fewer articles than the receipt cannot be paired by position -
        // one of them is a line the receipt does not have (or the other way round)
        assertFalse(MfgPurchaseServices.pairsByPosition(true, 4, 3));
        assertFalse(MfgPurchaseServices.pairsByPosition(true, 3, 4));
        // nothing on the receipt yet: every row is a new line
        assertFalse(MfgPurchaseServices.pairsByPosition(true, 0, 1));
    }

    @Test
    public void readsTheLinesOfTheSplitBox() {
        // a pack of 2600 resistors has to become one lot per value: the box takes a pasted list,
        // "part number, name, quantity" with the name optional (most people type two cells)
        java.util.List<java.util.Map<String, Object>> rows = MfgStockServices.splitRowsOf(
                "R-1/4W-100R,1/4W 金属膜电阻 100Ω,20\n"
                        + "R-1/4W-1K,,20\n"
                        + "R-1/4W-10K,20\n"
                        + "\n"
                        + "# 这一行是注释\n"
                        + "R-1/4W-4K7,4.7k 电阻,带,逗号的名字,20");
        assertEquals(4, rows.size());
        assertEquals("R-1/4W-100R", rows.get(0).get("componentCode"));
        assertEquals("1/4W 金属膜电阻 100Ω", rows.get(0).get("componentName"));
        assertEquals(20.0, ((Double) rows.get(0).get("qty")).doubleValue(), 0.0001);
        // an empty name cell stays empty: the service then keeps the name of the parent lot
        assertEquals("R-1/4W-1K", rows.get(1).get("componentCode"));
        assertNull(rows.get(1).get("componentName"));
        assertEquals(20.0, ((Double) rows.get(1).get("qty")).doubleValue(), 0.0001);
        // two cells: part number and quantity
        assertEquals("R-1/4W-10K", rows.get(2).get("componentCode"));
        assertEquals(20.0, ((Double) rows.get(2).get("qty")).doubleValue(), 0.0001);
        // a name may itself contain a comma; only the outer cells are the part number and the quantity
        assertEquals("4.7k 电阻,带,逗号的名字", rows.get(3).get("componentName"));
        assertEquals(20.0, ((Double) rows.get(3).get("qty")).doubleValue(), 0.0001);
        // a line with no quantity is kept, so the service can name the line that is wrong
        java.util.List<java.util.Map<String, Object>> bad = MfgStockServices.splitRowsOf("R-1/4W-100R\nR-x,abc");
        assertEquals(2, bad.size());
        assertNull(bad.get(0).get("qty"));
        assertNull(bad.get(1).get("qty"));
        assertEquals(Integer.valueOf(1), bad.get(0).get("lineNo"));
        assertTrue(MfgStockServices.splitRowsOf(null).isEmpty());
        assertTrue(MfgStockServices.splitRowsOf("   \n\n").isEmpty());
    }

    @Test
    public void correctsAParcelBookedAsOnePieceBeforeSplittingIt() {
        // a shop writes x1 for a whole pack, so the ledger says one piece while the pack holds 2600.
        // Splitting 2600 out of it is the person saying what is inside, so the count is corrected
        // first; anything else (210 on the shelf, 2600 asked) is a mistake and stays refused
        assertTrue(MfgStockServices.restatesTheParcel(2600.0, 1.0, 0.0));
        assertTrue(MfgStockServices.restatesTheParcel(50.0, 1.0, 0.0));
        assertFalse(MfgStockServices.restatesTheParcel(2600.0, 210.0, 0.0));
        assertFalse(MfgStockServices.restatesTheParcel(2600.0, 1.0, 5.0));
        assertFalse(MfgStockServices.restatesTheParcel(1.0, 1.0, 0.0));
        assertFalse(MfgStockServices.restatesTheParcel(20.0, 300.0, 0.0));
    }

    @Test
    public void dropsTheRepeatedLinesAndSaysWhich() {
        // the same value pasted twice is a slip, not a reason to refuse 130 lines: keep the first
        // line, leave the later one out and tell the person which line repeated which
        java.util.List<java.util.Map<String, Object>> rows = MfgStockServices.splitRowsOf(
                "R-1/4W-2R3,2R3,20\nR-1/4W-2R4,2R4,20\nR-1/4W-2R3,2R3,20\nR-1/4W-33R,33R,20\n"
                        + "R-1/4W-33R,33R,20");
        java.util.List<String> dropped = new java.util.ArrayList<>();
        java.util.List<java.util.Map<String, Object>> kept = MfgStockServices.withoutRepeatedRows(rows, dropped);
        assertEquals(3, kept.size());
        assertEquals("R-1/4W-2R3", kept.get(0).get("componentCode"));
        assertEquals("R-1/4W-2R4", kept.get(1).get("componentCode"));
        assertEquals("R-1/4W-33R", kept.get(2).get("componentCode"));
        assertEquals(2, dropped.size());
        assertTrue(dropped.get(0).contains("第 3 行"));
        assertTrue(dropped.get(0).contains("第 1 行"));
        assertTrue(dropped.get(1).contains("第 5 行"));
        assertTrue(dropped.get(1).contains("第 4 行"));
        // the case of the part number is not what makes two lines the same
        java.util.List<String> again = new java.util.ArrayList<>();
        assertEquals(1, MfgStockServices.withoutRepeatedRows(
                MfgStockServices.splitRowsOf("R-1K,1K,20\nr-1k,1k,20"), again).size());
        assertEquals(1, again.size());
    }

    @Test
    public void readsASplitListTypedWithAChineseCommaAndATitle() {
        // what a person actually writes: "料号, 名称 型号，20" - the comma before the quantity is the
        // full-width one a Chinese keyboard types, and the title of the list is often copied along
        java.util.List<java.util.Map<String, Object>> rows = MfgStockServices.splitRowsOf(
                "料号,名称,数量\n"
                        + "R-1/4W-2R3, 1/4W金属膜电阻包 1%五色环电阻 2R3，20\n"
                        + "R-1/4W-1K, 1/4W金属膜电阻包 1%五色环电阻 1K，20\n"
                        + "R-1/4W-10K，20");
        assertEquals(3, rows.size());
        assertEquals("R-1/4W-2R3", rows.get(0).get("componentCode"));
        assertEquals("1/4W金属膜电阻包 1%五色环电阻 2R3", rows.get(0).get("componentName"));
        assertEquals(20.0, ((Double) rows.get(0).get("qty")).doubleValue(), 0.0001);
        // a line written with nothing but full-width commas still splits into three cells
        assertEquals("R-1/4W-10K", rows.get(2).get("componentCode"));
        assertEquals(20.0, ((Double) rows.get(2).get("qty")).doubleValue(), 0.0001);
    }

    @Test
    public void findsThePictureOfAPartWhateverTheCaseOfItsNumber() {
        // the shop books the parcel as "NFC Coil", the sketch calls the same part "NFC COIL" and the
        // person may type either: the photo of the parcel belongs on all three (Step 204 - the row
        // showed no picture on production and did on the test copy, only the case differed)
        java.util.Map<String, String> shots = new java.util.HashMap<>();
        shots.put(MfgStockServices.pictureKey("NFC Coil"), "SCAN-1");
        assertEquals("SCAN-1", MfgStockServices.pictureOf(shots, "NFC COIL"));
        assertEquals("SCAN-1", MfgStockServices.pictureOf(shots, " nfc coil "));
        assertEquals("SCAN-1", MfgStockServices.pictureOf(shots, "NFC Coil"));
        assertNull(MfgStockServices.pictureOf(shots, "NFC-COIL-20-6T"));
        assertNull(MfgStockServices.pictureOf(shots, null));
        assertNull(MfgStockServices.pictureOf(null, "NFC Coil"));
        // the key keeps the part number, it only trims and raises it
        assertEquals("NFC COIL", MfgStockServices.pictureKey("  nfc coil "));
        assertEquals("", MfgStockServices.pictureKey(null));
    }

    @Test
    public void carriesAPictureDownASplitChainWhosePartNumbersDifferInCase() {
        // a parcel is photographed once, as the parcel; the kinds that come out of it were never
        // bought on their own. The key of the picture map is the part number, so the chain has to be
        // read in the same case as the map (the caller builds it through pictureKey)
        java.util.Map<String, String> shots = new java.util.HashMap<>();
        shots.put(MfgStockServices.pictureKey("NFC Coil"), "SCAN-1");
        java.util.Map<String, String> parentOf = new java.util.HashMap<>();
        parentOf.put(MfgStockServices.pictureKey("nfc coil small"), MfgStockServices.pictureKey("NFC COIL"));
        parentOf.put(MfgStockServices.pictureKey("nfc coil tiny"), MfgStockServices.pictureKey("NFC COIL SMALL"));
        MfgStockServices.inheritFromParents(shots, parentOf);
        // one hop and two hops both arrive at the photo of the parcel
        assertEquals("SCAN-1", MfgStockServices.pictureOf(shots, "NFC COIL SMALL"));
        assertEquals("SCAN-1", MfgStockServices.pictureOf(shots, "NFC Coil Tiny"));
        // and a part that has a picture of its own never has it overwritten by its parcel
        shots.put(MfgStockServices.pictureKey("NFC COIL SMALL"), "SCAN-2");
        MfgStockServices.inheritFromParents(shots, parentOf);
        assertEquals("SCAN-2", MfgStockServices.pictureOf(shots, "NFC COIL SMALL"));
    }
}
