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
    public void parseHistoryDataAcceptsSemicolonAndComma() throws GeneralException {
        Matrix semicolon = LearningCurveServices.parseHistoryData("2:200;3:285;5:450");
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
}
