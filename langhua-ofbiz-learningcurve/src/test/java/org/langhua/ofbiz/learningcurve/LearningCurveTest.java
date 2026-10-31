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
import static org.junit.Assert.assertNotSame;

import org.junit.Test;

import Jama.Matrix;

/**
 * Unit tests for the {@link LearningCurve} value object.
 */
public class LearningCurveTest {

    @Test
    public void constructorAndGetters() {
        Matrix qualified = new Matrix(new double[][] {{1.0, 100.0}});
        Matrix filtered = new Matrix(new double[][] {{2.0, 95.0}});

        LearningCurve curve = new LearningCurve(-0.2, 100.0, 0.98, qualified, filtered, 12.5, I_OFBizLearningCurve.WRIGHT);

        assertEquals(-0.2, curve.getSlope(), 1e-12);
        assertEquals(100.0, curve.getIntercept(), 1e-12);
        assertEquals(0.98, curve.getLeastSquare(), 1e-12);
        assertEquals(12.5, curve.getCumulativeX(), 1e-12);
        assertEquals(I_OFBizLearningCurve.WRIGHT, curve.getType());
        assertEquals(qualified, curve.getQualifiedData());
        assertEquals(filtered, curve.getFilteredData());
    }

    @Test
    public void setters() {
        LearningCurve curve = new LearningCurve(0.0, 0.0, 0.0, I_OFBizLearningCurve.CRAWFORD);

        curve.setSlope(-0.5);
        curve.setIntercept(42.0);
        curve.setLeastSquare(0.9);
        curve.setCumulativeX(7.0);
        curve.setType(I_OFBizLearningCurve.WRIGHT);

        assertEquals(-0.5, curve.getSlope(), 1e-12);
        assertEquals(42.0, curve.getIntercept(), 1e-12);
        assertEquals(0.9, curve.getLeastSquare(), 1e-12);
        assertEquals(7.0, curve.getCumulativeX(), 1e-12);
        assertEquals(I_OFBizLearningCurve.WRIGHT, curve.getType());
    }

    @Test
    public void factoryMethod() {
        LearningCurve curve = LearningCurve.getInstance(-0.1, 10.0, 0.95, I_OFBizLearningCurve.WRIGHT);

        assertNotNull(curve);
        assertEquals(-0.1, curve.getSlope(), 1e-12);
        assertEquals(10.0, curve.getIntercept(), 1e-12);
        assertEquals(0.95, curve.getLeastSquare(), 1e-12);
    }

    @Test
    public void cloneCreatesIndependentCopy() {
        Matrix qualified = new Matrix(new double[][] {{1.0, 100.0}, {2.0, 190.0}});
        LearningCurve original = new LearningCurve(-0.1, 10.0, 0.95, qualified, null, 3.0, I_OFBizLearningCurve.WRIGHT);

        LearningCurve copy = original.clone();
        copy.setSlope(-0.9);

        assertNotSame(original, copy);
        assertEquals(-0.1, original.getSlope(), 1e-12);
        assertEquals(-0.9, copy.getSlope(), 1e-12);
        assertEquals(original.getLeastSquare(), copy.getLeastSquare(), 1e-12);
        assertEquals(original.getCumulativeX(), copy.getCumulativeX(), 1e-12);
        assertEquals(original.getType(), copy.getType());
    }
}
