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

package org.langhua.ofbiz.learningcurve;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.apache.ofbiz.base.util.GeneralException;
import org.junit.Before;
import org.junit.Test;

import Jama.Matrix;

/**
 * Unit tests for {@link OFBizLearningCurveHandler}.
 *
 * <p>The sample data is taken from the class documentation:
 * lot 1 = 2 units for 200, lot 2 = 3 units for 285, lot 3 = 5 units for 450.
 */
public class OFBizLearningCurveHandlerTest {

    private static final double DELTA = 1e-9;

    private OFBizLearningCurveHandler handler;
    private Matrix historyData;

    @Before
    public void setUp() {
        handler = OFBizLearningCurveHandler.getInstance();
        historyData = new Matrix(new double[][] {{2, 200}, {3, 285}, {5, 450}});
    }

    @Test
    public void wrightCurveParameters() throws GeneralException {
        LearningCurve curve = handler.getLearningCurve(0.95, historyData, Collections.<String, Object>emptyMap(),
                I_OFBizLearningCurve.WRIGHT);

        assertNotNull(curve);
        assertEquals(-0.051167616170754375, curve.getSlope(), DELTA);
        assertEquals(4.608685142340069, curve.getIntercept(), DELTA);
        assertEquals(0.9762073261679526, curve.getLeastSquare(), DELTA);
        assertEquals(10.0, curve.getCumulativeX(), DELTA);
    }

    @Test
    public void wrightEstimate() throws GeneralException {
        double estimate = handler.getEstimate(0.95, historyData, 5,
                Collections.<String, Object>emptyMap(), I_OFBizLearningCurve.WRIGHT);

        assertEquals(440.18817654561616, estimate, 1e-6);
    }

    @Test
    public void crawfordCurveParameters() throws GeneralException {
        LearningCurve curve = handler.getLearningCurve(0.95, historyData, Collections.<String, Object>emptyMap(),
                I_OFBizLearningCurve.CRAWFORD);

        assertNotNull(curve);
        assertEquals(-0.04131379029040091, curve.getSlope(), DELTA);
        assertEquals(4.636033212033878, curve.getIntercept(), DELTA);
        assertEquals(0.9821878705527061, curve.getLeastSquare(), DELTA);
    }

    @Test
    public void crawfordEstimate() throws GeneralException {
        double estimate = handler.getEstimate(0.95, historyData, 5,
                Collections.<String, Object>emptyMap(), I_OFBizLearningCurve.CRAWFORD);

        assertEquals(445.5113089399582, estimate, 1e-6);
    }

    @Test
    public void historyDataTransferToWrightMethod() {
        Matrix transferred = OFBizLearningCurveHandler.dataTransferToWrightMethod(historyData);

        assertEquals(3, transferred.getRowDimension());
        assertEquals(1.0, transferred.get(0, 0), DELTA);
        assertEquals(100.0, transferred.get(0, 1), DELTA);
        assertEquals(3.5, transferred.get(1, 0), DELTA);
        assertEquals(95.0, transferred.get(1, 1), DELTA);
        assertEquals(7.5, transferred.get(2, 0), DELTA);
        assertEquals(90.0, transferred.get(2, 1), DELTA);
    }

    @Test
    public void historyDataTransferToCrawfordMethod() {
        Matrix transferred = OFBizLearningCurveHandler.dataTransferToCrawfordMethod(historyData);

        assertEquals(3, transferred.getRowDimension());
        assertEquals(2.0, transferred.get(0, 0), DELTA);
        assertEquals(100.0, transferred.get(0, 1), DELTA);
        assertEquals(5.0, transferred.get(1, 0), DELTA);
        assertEquals(97.0, transferred.get(1, 1), DELTA);
        assertEquals(10.0, transferred.get(2, 0), DELTA);
        assertEquals(93.5, transferred.get(2, 1), DELTA);
    }

    @Test
    public void logValuesUsesNaturalLogarithm() {
        Matrix logged = OFBizLearningCurveHandler.logValues(new Matrix(new double[][] {{2, 200}}));

        assertEquals(Math.log(2), logged.get(0, 0), DELTA);
        assertEquals(Math.log(200), logged.get(0, 1), DELTA);
    }

    @Test
    public void rejectsLeastSquareOutOfRange() {
        try {
            handler.getLearningCurve(1.5, historyData, Collections.<String, Object>emptyMap(),
                    I_OFBizLearningCurve.WRIGHT);
            fail("expected GeneralException for least square 1.5");
        } catch (GeneralException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("Least square must be between 0 and 1"));
        }
    }

    @Test
    public void rejectsUnknownMethodType() {
        Map<String, Object> context = new HashMap<>();
        context.put("locale", java.util.Locale.US);
        try {
            handler.getLearningCurve(0.95, historyData, context, 99);
            fail("expected GeneralException for unknown method type");
        } catch (GeneralException e) {
            assertNotNull(e.getMessage());
        }
    }

    @Test
    public void rejectsNullHistoryData() {
        try {
            handler.getLearningCurve(0.95, null, Collections.<String, Object>emptyMap(),
                    I_OFBizLearningCurve.WRIGHT);
            fail("expected GeneralException for null history data");
        } catch (GeneralException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("should not be null"));
        }
    }

    @Test
    public void rejectsTooFewHistoryData() {
        Matrix tooFew = new Matrix(new double[][] {{2, 200}, {3, 285}});
        try {
            handler.getLearningCurve(0.95, tooFew, Collections.<String, Object>emptyMap(),
                    I_OFBizLearningCurve.WRIGHT);
            fail("expected GeneralException for too few history data");
        } catch (GeneralException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("too few"));
        }
    }

    @Test
    public void rejectsNonPositiveNewUnitNumber() {
        try {
            handler.getEstimate(0.95, historyData, -1, Collections.<String, Object>emptyMap(),
                    I_OFBizLearningCurve.WRIGHT);
            fail("expected GeneralException for negative new unit number");
        } catch (GeneralException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("must be positive"));
        }
    }
}
