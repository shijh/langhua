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
 *
 * For the latest version about this module, please see the
 * project website: http://langhua.org/opensource/ofbiz/ofbiz-learningcurve

 * For more information on Apache OFBiz, please see the
 * project website: http://ofbiz.apache.org/
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */

package org.langhua.ofbiz.learningcurve;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import org.apache.ofbiz.base.util.GeneralException;
import org.apache.ofbiz.service.DispatchContext;
import org.apache.ofbiz.service.ServiceUtil;

import Jama.Matrix;

/**
 * OFBiz service facade for the learning curve component.
 *
 * <p>The services are declared in {@code servicedef/services.xml} and can be invoked like any other
 * OFBiz service (service engine, Web Tools &gt; Run Service, event handlers, screen actions, ...).
 */
public final class LearningCurveServices {

    public static final String MODULE = LearningCurveServices.class.getName();

    /** Default desired coefficient of determination. */
    private static final double DEFAULT_LEAST_SQUARE = 0.95;

    private LearningCurveServices() {
    }

    /**
     * Estimates the cost of the next units.
     *
     * @param dctx the dispatch context
     * @param context service context, see {@code servicedef/services.xml#learningCurveEstimate}
     * @return the service result map, containing {@code estimate} and the fitted curve parameters
     */
    public static Map<String, Object> learningCurveEstimate(DispatchContext dctx, Map<String, ? extends Object> context) {
        try {
            Matrix historyData = parseHistoryData(context.get("historyData"));
            double newUnitNumber = toDouble(context.get("newUnitNumber"), 0.0);
            double leastSquare = toDouble(context.get("leastSquare"), DEFAULT_LEAST_SQUARE);
            int methodType = (int) toDouble(context.get("methodType"), I_OFBizLearningCurve.WRIGHT);
            Map<String, Object> handlerContext = newHandlerContext(context);

            OFBizLearningCurveHandler handler = OFBizLearningCurveHandler.getInstance();
            LearningCurve curve = handler.getLearningCurve(leastSquare, historyData, handlerContext, methodType);
            double estimate = handler.getEstimate(leastSquare, historyData, newUnitNumber, handlerContext, methodType);

            Map<String, Object> result = ServiceUtil.returnSuccess();
            result.put("estimate", Double.valueOf(estimate));
            putCurveParameters(result, curve);
            return result;
        } catch (GeneralException e) {
            return ServiceUtil.returnError(e.getMessage());
        }
    }

    /**
     * Returns the fitted curve parameters without estimating a new lot.
     *
     * @param dctx the dispatch context
     * @param context service context, see {@code servicedef/services.xml#learningCurveParameters}
     * @return the service result map, containing the fitted curve parameters
     */
    public static Map<String, Object> learningCurveParameters(DispatchContext dctx, Map<String, ? extends Object> context) {
        try {
            Matrix historyData = parseHistoryData(context.get("historyData"));
            double leastSquare = toDouble(context.get("leastSquare"), DEFAULT_LEAST_SQUARE);
            int methodType = (int) toDouble(context.get("methodType"), I_OFBizLearningCurve.WRIGHT);
            Map<String, Object> handlerContext = newHandlerContext(context);

            LearningCurve curve = OFBizLearningCurveHandler.getInstance()
                    .getLearningCurve(leastSquare, historyData, handlerContext, methodType);

            Map<String, Object> result = ServiceUtil.returnSuccess();
            putCurveParameters(result, curve);
            result.put("cumulativeX", Double.valueOf(curve.getCumulativeX()));
            int filtered = curve.getFilteredData() == null ? 0 : curve.getFilteredData().getRowDimension();
            result.put("filteredRowCount", Integer.valueOf(filtered));
            return result;
        } catch (GeneralException e) {
            return ServiceUtil.returnError(e.getMessage());
        }
    }

    /**
     * Parses a history data string such as {@code "2:200;3:285;5:450"} into a matrix.
     *
     * @param historyData the string to parse, pairs separated by {@code ;} or {@code ,}
     * @return a matrix with one row per lot and two columns (units, cost)
     * @throws GeneralException if the string is blank or malformed
     */
    public static Matrix parseHistoryData(Object historyData) throws GeneralException {
        String value = historyData == null ? null : historyData.toString().trim();
        if (value == null || value.isEmpty()) {
            throw new GeneralException("historyData is required, expected \"units:cost;units:cost;...\"");
        }
        String[] entries = value.split("\\s*[;,]\\s*");
        double[][] values = new double[entries.length][2];
        for (int i = 0; i < entries.length; i++) {
            String[] pair = entries[i].split("\\s*[:=]\\s*");
            if (pair.length != 2) {
                throw new GeneralException("Invalid historyData entry [" + entries[i] + "], expected units:cost");
            }
            try {
                values[i][0] = Double.parseDouble(pair[0]);
                values[i][1] = Double.parseDouble(pair[1]);
            } catch (NumberFormatException e) {
                throw new GeneralException("Invalid number in historyData entry [" + entries[i] + "]");
            }
        }
        return new Matrix(values);
    }

    private static Map<String, Object> newHandlerContext(Map<String, ? extends Object> context) {
        Map<String, Object> handlerContext = new HashMap<>();
        Object locale = context.get("locale");
        handlerContext.put("locale", locale instanceof Locale ? locale : Locale.getDefault());
        return handlerContext;
    }

    private static void putCurveParameters(Map<String, Object> result, LearningCurve curve) {
        result.put("slope", Double.valueOf(curve.getSlope()));
        result.put("intercept", Double.valueOf(curve.getIntercept()));
        result.put("leastSquareActual", Double.valueOf(curve.getLeastSquare()));
        Matrix qualified = curve.getQualifiedData();
        result.put("qualifiedRowCount", Integer.valueOf(qualified == null ? 0 : qualified.getRowDimension()));
    }

    private static double toDouble(Object value, double defaultValue) {
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        if (value instanceof String) {
            try {
                return Double.parseDouble(((String) value).trim());
            } catch (NumberFormatException e) {
                return defaultValue;
            }
        }
        return defaultValue;
    }
}
