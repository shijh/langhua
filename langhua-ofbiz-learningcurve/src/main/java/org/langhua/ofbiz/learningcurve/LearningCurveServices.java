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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.apache.ofbiz.base.util.GeneralException;
import org.apache.ofbiz.entity.Delegator;
import org.apache.ofbiz.entity.GenericEntityException;
import org.apache.ofbiz.entity.GenericValue;
import org.apache.ofbiz.entity.util.EntityQuery;
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
            result.put("unitCost", Double.valueOf(newUnitNumber > 0.0 ? estimate / newUnitNumber : 0.0));
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

    /**
     * Computes the estimate and stores it (header + history batches) for later reference.
     *
     * @param dctx the dispatch context
     * @param context service context, see {@code servicedef/services.xml#learningCurveSaveEstimate}
     * @return the service result map containing {@code estimateId} and the computed values
     */
    public static Map<String, Object> saveEstimate(DispatchContext dctx, Map<String, ? extends Object> context) {
        try {
            String productCode = toText(context.get("productCode"));
            if (productCode == null) {
                return ServiceUtil.returnError("productCode is required");
            }
            String measureType = toText(context.get("measureType"));
            if (measureType == null) {
                measureType = "COST";
            }
            Matrix historyData = parseHistoryData(context.get("historyData"));
            double newUnitNumber = toDouble(context.get("newUnitNumber"), 0.0);
            double leastSquare = toDouble(context.get("leastSquare"), DEFAULT_LEAST_SQUARE);
            int methodType = (int) toDouble(context.get("methodType"), I_OFBizLearningCurve.WRIGHT);
            Map<String, Object> handlerContext = newHandlerContext(context);

            OFBizLearningCurveHandler handler = OFBizLearningCurveHandler.getInstance();
            LearningCurve curve = handler.getLearningCurve(leastSquare, historyData, handlerContext, methodType);
            double estimate = handler.getEstimate(leastSquare, historyData, newUnitNumber, handlerContext, methodType);
            double unitCost = newUnitNumber > 0.0 ? estimate / newUnitNumber : 0.0;

            Delegator delegator = dctx.getDelegator();
            String estimateId = delegator.getNextSeqId("LearningCurveEstimate");

            GenericValue header = delegator.makeValue("LearningCurveEstimate");
            header.set("estimateId", estimateId);
            header.set("productCode", productCode);
            header.set("measureType", measureType);
            header.set("methodType", Long.valueOf(methodType));
            header.set("leastSquareTarget", Double.valueOf(leastSquare));
            header.set("newUnitNumber", Double.valueOf(newUnitNumber));
            header.set("estimateTotal", Double.valueOf(estimate));
            header.set("estimateUnit", Double.valueOf(unitCost));
            header.set("slope", Double.valueOf(curve.getSlope()));
            header.set("intercept", Double.valueOf(curve.getIntercept()));
            header.set("leastSquareActual", Double.valueOf(curve.getLeastSquare()));
            header.set("qualifiedRows", Long.valueOf(rowCount(curve.getQualifiedData())));
            header.set("filteredRows", Long.valueOf(rowCount(curve.getFilteredData())));
            header.set("historyData", String.valueOf(context.get("historyData")));
            delegator.create(header);

            for (int i = 0; i < historyData.getRowDimension(); i++) {
                GenericValue batch = delegator.makeValue("LearningCurveBatch");
                batch.set("estimateId", estimateId);
                batch.set("batchSeq", Long.valueOf(i + 1));
                batch.set("lotSize", Double.valueOf(historyData.get(i, 0)));
                batch.set("lotValue", Double.valueOf(historyData.get(i, 1)));
                delegator.create(batch);
            }

            Map<String, Object> result = ServiceUtil.returnSuccess();
            result.put("estimateId", estimateId);
            result.put("estimate", Double.valueOf(estimate));
            result.put("unitCost", Double.valueOf(unitCost));
            result.put("filteredRowCount", Integer.valueOf(rowCount(curve.getFilteredData())));
            putCurveParameters(result, curve);
            return result;
        } catch (GeneralException e) {
            return ServiceUtil.returnError("Could not store the estimate: " + e.getMessage());
        }
    }

    /**
     * Returns the stored estimates, newest first.
     *
     * @param dctx the dispatch context
     * @param context service context, see {@code servicedef/services.xml#learningCurveListEstimates}
     * @return the service result map containing the {@code estimates} list
     */
    public static Map<String, Object> listEstimates(DispatchContext dctx, Map<String, ? extends Object> context) {
        try {
            String productCode = toText(context.get("productCode"));
            EntityQuery query = EntityQuery.use(dctx.getDelegator()).from("LearningCurveEstimate");
            if (productCode != null) {
                query = query.where("productCode", productCode);
            }
            List<GenericValue> estimates = query.orderBy("-createdStamp").queryList();

            Map<String, Object> result = ServiceUtil.returnSuccess();
            result.put("estimates", toEstimateMaps(estimates));
            return result;
        } catch (GenericEntityException e) {
            return ServiceUtil.returnError("Could not read the estimates: " + e.getMessage());
        }
    }

    /**
     * Builds the series used by the learning curve chart: actual unit values per batch and the
     * values predicted by the fitted curve at the same cumulative positions.
     *
     * @param dctx the dispatch context
     * @param context service context, see {@code servicedef/services.xml#learningCurveChartData}
     * @return the service result map containing {@code points}, {@code maxX} and {@code maxY}
     */
    public static Map<String, Object> chartData(DispatchContext dctx, Map<String, ? extends Object> context) {
        try {
            String estimateId = toText(context.get("estimateId"));
            Delegator delegator = dctx.getDelegator();
            GenericValue header = EntityQuery.use(delegator).from("LearningCurveEstimate")
                    .where("estimateId", estimateId).queryOne();
            if (header == null) {
                Map<String, Object> empty = ServiceUtil.returnSuccess();
                empty.put("points", new ArrayList<Map<String, Object>>());
                empty.put("maxX", Double.valueOf(1.0));
                empty.put("maxY", Double.valueOf(1.0));
                return empty;
            }

            List<GenericValue> batches = EntityQuery.use(delegator).from("LearningCurveBatch")
                    .where("estimateId", estimateId).orderBy("batchSeq").queryList();
            if (batches.isEmpty()) {
                Map<String, Object> empty = ServiceUtil.returnSuccess();
                empty.put("estimate", toEstimateMap(header));
                empty.put("batches", toBatchMaps(batches));
                empty.put("points", new ArrayList<Map<String, Object>>());
                empty.put("maxX", Double.valueOf(1.0));
                empty.put("maxY", Double.valueOf(1.0));
                return empty;
            }

            double[][] values = new double[batches.size()][2];
            for (int i = 0; i < batches.size(); i++) {
                values[i][0] = batches.get(i).getDouble("lotSize").doubleValue();
                values[i][1] = batches.get(i).getDouble("lotValue").doubleValue();
            }
            Matrix history = new Matrix(values);
            int methodType = header.getLong("methodType").intValue();
            Matrix plot = methodType == I_OFBizLearningCurve.CRAWFORD
                    ? OFBizLearningCurveHandler.dataTransferToCrawfordMethod(history)
                    : OFBizLearningCurveHandler.dataTransferToWrightMethod(history);

            double slope = header.getDouble("slope").doubleValue();
            double intercept = header.getDouble("intercept").doubleValue();

            List<Map<String, Object>> points = new ArrayList<>();
            double cumulative = 0.0;
            double maxX = 0.0;
            double maxY = 0.0;
            for (int i = 0; i < values.length; i++) {
                cumulative += values[i][0];
                double actual = values[i][0] == 0.0 ? 0.0 : values[i][1] / values[i][0];
                double predicted = Math.exp(intercept + slope * Math.log(plot.get(i, 0)));

                Map<String, Object> point = new HashMap<>();
                point.put("units", Double.valueOf(cumulative));
                point.put("actual", Double.valueOf(actual));
                point.put("predicted", Double.valueOf(predicted));
                points.add(point);

                maxX = Math.max(maxX, cumulative);
                maxY = Math.max(maxY, Math.max(actual, predicted));
            }

            Map<String, Object> result = ServiceUtil.returnSuccess();
            result.put("estimate", toEstimateMap(header));
            result.put("batches", toBatchMaps(batches));
            result.put("points", points);
            result.put("maxX", Double.valueOf(maxX));
            result.put("maxY", Double.valueOf(maxY));
            result.put("productCode", header.getString("productCode"));
            result.put("measureType", header.getString("measureType"));
            return result;
        } catch (GenericEntityException e) {
            return ServiceUtil.returnError("Could not read the estimate: " + e.getMessage());
        }
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

    private static String toText(Object value) {
        if (value == null) {
            return null;
        }
        String text = value.toString().trim();
        return text.isEmpty() ? null : text;
    }

    private static int rowCount(Matrix matrix) {
        return matrix == null ? 0 : matrix.getRowDimension();
    }

    private static List<Map<String, Object>> toEstimateMaps(List<GenericValue> estimates) {
        List<Map<String, Object>> list = new ArrayList<>(estimates.size());
        for (GenericValue estimate : estimates) {
            list.add(toEstimateMap(estimate));
        }
        return list;
    }

    private static Map<String, Object> toEstimateMap(GenericValue value) {
        Map<String, Object> map = new HashMap<>();
        map.put("estimateId", value.getString("estimateId"));
        map.put("productCode", value.getString("productCode"));
        map.put("measureType", value.getString("measureType"));
        map.put("methodType", value.get("methodType"));
        map.put("leastSquareTarget", value.get("leastSquareTarget"));
        map.put("newUnitNumber", value.get("newUnitNumber"));
        map.put("estimateTotal", value.get("estimateTotal"));
        map.put("estimateUnit", value.get("estimateUnit"));
        map.put("slope", value.get("slope"));
        map.put("intercept", value.get("intercept"));
        map.put("leastSquareActual", value.get("leastSquareActual"));
        map.put("qualifiedRows", value.get("qualifiedRows"));
        map.put("filteredRows", value.get("filteredRows"));
        map.put("historyData", value.getString("historyData"));
        map.put("createdStamp", value.get("createdStamp"));
        return map;
    }

    private static List<Map<String, Object>> toBatchMaps(List<GenericValue> batches) {
        List<Map<String, Object>> list = new ArrayList<>(batches.size());
        for (GenericValue batch : batches) {
            Map<String, Object> map = new HashMap<>();
            map.put("batchSeq", batch.get("batchSeq"));
            map.put("lotSize", batch.get("lotSize"));
            map.put("lotValue", batch.get("lotValue"));
            list.add(map);
        }
        return list;
    }
}
