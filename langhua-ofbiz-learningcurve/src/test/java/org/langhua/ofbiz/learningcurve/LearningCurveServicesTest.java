/*
 * This library is part of OFBiz-LearningCurve component of Langhua
 *
 * Copyright (C) 2010  Langhua Opensource Foundation (http://langhua.org)
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3.0 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 */

package org.langhua.ofbiz.learningcurve;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import org.apache.ofbiz.base.util.GeneralException;
import org.apache.ofbiz.service.ServiceUtil;
import org.junit.Test;

import Jama.Matrix;

/**
 * Unit tests for the {@link LearningCurveServices} service facade.
 */
public class LearningCurveServicesTest {

    private Map<String, Object> newContext() {
        Map<String, Object> context = new HashMap<>();
        context.put("historyData", "2:200;3:285;5:450");
        context.put("newUnitNumber", Double.valueOf(5));
        context.put("leastSquare", Double.valueOf(0.95));
        context.put("methodType", Integer.valueOf(I_OFBizLearningCurve.WRIGHT));
        context.put("locale", Locale.US);
        return context;
    }

    @Test
    public void estimateServiceReturnsEstimateAndParameters() {
        Map<String, Object> result = LearningCurveServices.learningCurveEstimate(null, newContext());

        assertFalse(ServiceUtil.isError(result));
        assertEquals(440.18817654561616, ((Double) result.get("estimate")).doubleValue(), 1e-6);
        assertEquals(88.03763530912323, ((Double) result.get("unitCost")).doubleValue(), 1e-6);
        assertNotNull(result.get("slope"));
        assertNotNull(result.get("intercept"));
        assertNotNull(result.get("leastSquareActual"));
        assertEquals(Integer.valueOf(3), result.get("qualifiedRowCount"));
    }

    @Test
    public void estimateServiceSupportsCommaSeparator() {
        Map<String, Object> context = newContext();
        context.put("historyData", "2:200,3:285,5:450");

        Map<String, Object> result = LearningCurveServices.learningCurveEstimate(null, context);

        assertFalse(ServiceUtil.isError(result));
        assertEquals(440.18817654561616, ((Double) result.get("estimate")).doubleValue(), 1e-6);
    }

    @Test
    public void estimateServiceUsesDefaults() {
        Map<String, Object> context = new HashMap<>();
        context.put("historyData", "2:200;3:285;5:450");
        context.put("newUnitNumber", Double.valueOf(5));

        Map<String, Object> result = LearningCurveServices.learningCurveEstimate(null, context);

        assertFalse(ServiceUtil.isError(result));
        assertEquals(440.18817654561616, ((Double) result.get("estimate")).doubleValue(), 1e-6);
    }

    @Test
    public void estimateServiceCrawford() {
        Map<String, Object> context = newContext();
        context.put("methodType", Integer.valueOf(I_OFBizLearningCurve.CRAWFORD));

        Map<String, Object> result = LearningCurveServices.learningCurveEstimate(null, context);

        assertFalse(ServiceUtil.isError(result));
        assertEquals(445.5113089399582, ((Double) result.get("estimate")).doubleValue(), 1e-6);
    }

    @Test
    public void estimateServiceReturnsErrorForMalformedHistoryData() {
        Map<String, Object> context = newContext();
        context.put("historyData", "not-a-number");

        Map<String, Object> result = LearningCurveServices.learningCurveEstimate(null, context);

        assertTrue(ServiceUtil.isError(result));
        assertNotNull(ServiceUtil.getErrorMessage(result));
    }

    @Test
    public void estimateServiceReturnsErrorForMissingHistoryData() {
        Map<String, Object> context = newContext();
        context.remove("historyData");

        Map<String, Object> result = LearningCurveServices.learningCurveEstimate(null, context);

        assertTrue(ServiceUtil.isError(result));
    }

    @Test
    public void parametersServiceReturnsCurveParameters() {
        Map<String, Object> context = newContext();
        context.remove("newUnitNumber");

        Map<String, Object> result = LearningCurveServices.learningCurveParameters(null, context);

        assertFalse(ServiceUtil.isError(result));
        assertEquals(-0.051167616170754375, ((Double) result.get("slope")).doubleValue(), 1e-9);
        assertEquals(4.608685142340069, ((Double) result.get("intercept")).doubleValue(), 1e-9);
        assertEquals(10.0, ((Double) result.get("cumulativeX")).doubleValue(), 1e-9);
        assertEquals(Integer.valueOf(0), result.get("filteredRowCount"));
    }

    @Test
    public void parseHistoryDataAcceptsSemicolonAndComma() throws GeneralException {        Matrix semicolon = LearningCurveServices.parseHistoryData("2:200;3:285;5:450");
        Matrix comma = LearningCurveServices.parseHistoryData("2:200,3:285,5:450");

        assertEquals(3, semicolon.getRowDimension());
        assertEquals(2, semicolon.getColumnDimension());
        assertEquals(2.0, semicolon.get(0, 0), 1e-12);
        assertEquals(450.0, semicolon.get(2, 1), 1e-12);
        assertEquals(3, comma.getRowDimension());
        assertEquals(285.0, comma.get(1, 1), 1e-12);
    }

    @Test
    public void parseHistoryDataRejectsBlankInput() {
        try {
            LearningCurveServices.parseHistoryData("  ");
            fail("expected GeneralException for blank history data");
        } catch (GeneralException e) {
            assertNotNull(e.getMessage());
        }
    }

    @Test
    public void parseHistoryDataRejectsMalformedEntry() {
        try {
            LearningCurveServices.parseHistoryData("2:200;3");
            fail("expected GeneralException for malformed entry");
        } catch (GeneralException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("Invalid historyData entry"));
        }
    }

    private Map<String, Object> workOrderRow(String workOrderId, double qtyOk, double hours) {
        Map<String, Object> row = new HashMap<>();
        row.put("workOrderId", workOrderId);
        row.put("qtyOk", Double.valueOf(qtyOk));
        row.put("hours", Double.valueOf(hours));
        row.put("date", "2026-10-01");
        row.put("statusId", "MFG_COMPLETED");
        return row;
    }

    @Test
    public void mesBatchesTurnWorkOrdersIntoHistoryPairs() {
        java.util.List<Map<String, Object>> rows = new java.util.ArrayList<>();
        rows.add(workOrderRow("WO-1", 10, 20));
        rows.add(workOrderRow("WO-2", 20, 30));

        Map<String, Object> result = LearningCurveServices.buildMesBatches(rows);

        assertFalse(ServiceUtil.isError(result));
        assertEquals("10:20;20:30", result.get("historyData"));
        assertEquals(Integer.valueOf(2), result.get("batchCount"));
        assertEquals(30.0, ((Double) result.get("totalQty")).doubleValue(), 1e-9);
        assertEquals(50.0, ((Double) result.get("totalHours")).doubleValue(), 1e-9);
        @SuppressWarnings("unchecked")
        java.util.List<Map<String, Object>> batches = (java.util.List<Map<String, Object>>) result.get("batches");
        assertEquals(2, batches.size());
        assertEquals(2.0, ((Double) batches.get(0).get("unitHours")).doubleValue(), 1e-9);
        assertEquals("WO-2", batches.get(1).get("workOrderId"));
    }

    @Test
    public void mesBatchesSkipOrdersWithoutGoodUnitsOrHours() {
        java.util.List<Map<String, Object>> rows = new java.util.ArrayList<>();
        rows.add(workOrderRow("WO-1", 0, 5));
        rows.add(workOrderRow("WO-2", 5, 0));
        rows.add(workOrderRow("WO-3", 4, 10));

        Map<String, Object> result = LearningCurveServices.buildMesBatches(rows);

        // only the third order is usable: the other two would divide by zero
        assertEquals(Integer.valueOf(1), result.get("batchCount"));
        assertEquals(Integer.valueOf(2), result.get("skippedCount"));
        assertEquals("4:10", result.get("historyData"));
    }

    @Test
    public void mesBatchesKeepDotDecimalSeparator() {
        java.util.List<Map<String, Object>> rows = new java.util.ArrayList<>();
        rows.add(workOrderRow("WO-1", 2.5, 7.25));

        Map<String, Object> result = LearningCurveServices.buildMesBatches(rows);

        // the history string is parsed back with Double.parseDouble, so it must not follow the locale
        assertEquals("2.5:7.25", result.get("historyData"));
    }

    @Test
    public void mesBatchesOfNoRowsAreEmpty() {
        Map<String, Object> result = LearningCurveServices.buildMesBatches(new java.util.ArrayList<Map<String, Object>>());

        assertFalse(ServiceUtil.isError(result));
        assertEquals("", result.get("historyData"));
        assertEquals(Integer.valueOf(0), result.get("batchCount"));
    }

    @Test
    public void pdrSeriesIsOneUnitPerDelivery() {
        java.util.List<Map<String, Object>> rows = new java.util.ArrayList<>();
        rows.add(pdrRow("SW-1", 60.0));
        rows.add(pdrRow("SW-2", 48.0));
        rows.add(pdrRow("SW-3", 40.0));

        Matrix series = LearningCurveServices.buildPdrSeries(rows);

        // every delivery counts as one unit, its value is that delivery's hours per point
        assertEquals(3, series.getRowDimension());
        assertEquals(2, series.getColumnDimension());
        assertEquals(1.0, series.get(0, 0), 1e-9);
        assertEquals(60.0, series.get(0, 1), 1e-9);
        assertEquals(40.0, series.get(2, 1), 1e-9);
    }

    @Test
    public void pdrSeriesOfNoRowsHasNoRow() {
        Matrix series = LearningCurveServices.buildPdrSeries(new java.util.ArrayList<Map<String, Object>>());

        assertEquals(0, series.getRowDimension());
    }

    private Map<String, Object> pdrRow(String projectCode, double pdr) {
        Map<String, Object> row = new HashMap<>();
        row.put("projectCode", projectCode);
        row.put("pdr", Double.valueOf(pdr));
        return row;
    }
}
