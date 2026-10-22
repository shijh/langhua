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
import org.apache.ofbiz.service.GenericServiceException;
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

    /**
     * Builds the history batches of one product straight from the MES component: every work order
     * becomes a batch, its quantity the good units reported and its value the reported hours.
     *
     * <p>The MES entities are read directly (they live in the same database), so this component
     * keeps working when the MES component is not deployed - the query then fails and the caller
     * gets an empty result with a message instead of an error page.
     *
     * @param dctx the dispatch context
     * @param context service context, see {@code servicedef/services.xml#learningCurveMesBatches}
     * @return the service result map containing {@code batches} and {@code historyData}
     */
    public static Map<String, Object> mesBatches(DispatchContext dctx, Map<String, ? extends Object> context) {
        String productCode = toText(context.get("productCode"));
        if (productCode == null) {
            return emptyMesResult(null);
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        try {
            Delegator delegator = dctx.getDelegator();
            List<GenericValue> orders = EntityQuery.use(delegator).from("MfgWorkOrder")
                    .where("productCode", productCode).orderBy("dueDate", "createdStamp").queryList();
            for (GenericValue order : orders) {
                String workOrderId = order.getString("workOrderId");
                List<GenericValue> reports = EntityQuery.use(delegator).from("MfgReport")
                        .where("workOrderId", workOrderId).queryList();
                double hours = 0.0;
                double qtyOk = 0.0;
                for (GenericValue report : reports) {
                    // numeric fields come back as Long, floating point ones as Double: go through Number
                    hours += numberOf(report, "workHours");
                    qtyOk += numberOf(report, "qtyOk");
                }
                Map<String, Object> row = new HashMap<>();
                row.put("workOrderId", workOrderId);
                row.put("qtyOk", Double.valueOf(qtyOk));
                row.put("hours", Double.valueOf(hours));
                Object dueDate = order.get("dueDate");
                row.put("date", dueDate == null ? "" : dueDate.toString().substring(0, Math.min(10, dueDate.toString().length())));
                row.put("statusId", order.getString("statusId"));
                rows.add(row);
            }
        } catch (GeneralException e) {
            return emptyMesResult("读不到 MES 的工单数据（MES 组件可能没部署）：" + e.getMessage());
        } catch (RuntimeException e) {
            return emptyMesResult("读不到 MES 的工单数据（MES 组件可能没部署）：" + e.getMessage());
        }
        Map<String, Object> result = buildMesBatches(rows);
        result.put("productCode", productCode);
        result.put("message", rows.isEmpty()
                ? "这个型号还没有工单，先在 MES 建工单并报工。"
                : null);
        return result;
    }

    /**
     * Turns the per work order rows into the history list and the compact history string.
     *
     * <p>Work orders without good units or without reported hours are skipped: they would make the
     * unit value infinite. The order of the rows is kept, because the cumulative quantity of the
     * batches matters for the curve.
     *
     * @param rows rows with {@code workOrderId}, {@code qtyOk}, {@code hours}, {@code date} and
     *        {@code statusId}; the list may be empty
     * @return the service result map with {@code batches}, {@code batchCount}, {@code totalQty},
     *         {@code totalHours} and {@code historyData}
     */
    public static Map<String, Object> buildMesBatches(List<Map<String, Object>> rows) {
        List<Map<String, Object>> batches = new ArrayList<>();
        StringBuilder history = new StringBuilder();
        double totalQty = 0.0;
        double totalHours = 0.0;
        java.text.DecimalFormat format = new java.text.DecimalFormat("0.###",
                java.text.DecimalFormatSymbols.getInstance(Locale.ROOT));
        int skipped = 0;
        for (Map<String, Object> row : rows) {
            double qtyOk = row.get("qtyOk") instanceof Number ? ((Number) row.get("qtyOk")).doubleValue() : 0.0;
            double hours = row.get("hours") instanceof Number ? ((Number) row.get("hours")).doubleValue() : 0.0;
            if (qtyOk <= 0.0 || hours <= 0.0) {
                skipped++;
                continue;
            }
            Map<String, Object> batch = new HashMap<>(row);
            batch.put("unitHours", Double.valueOf(hours / qtyOk));
            batches.add(batch);
            totalQty += qtyOk;
            totalHours += hours;
            if (history.length() > 0) {
                history.append(';');
            }
            history.append(format.format(qtyOk)).append(':').append(format.format(hours));
        }
        Map<String, Object> result = ServiceUtil.returnSuccess();
        result.put("batches", batches);
        result.put("batchCount", Integer.valueOf(batches.size()));
        result.put("skippedCount", Integer.valueOf(skipped));
        result.put("totalQty", Double.valueOf(totalQty));
        result.put("totalHours", Double.valueOf(totalHours));
        result.put("historyData", history.toString());
        return result;
    }

    /**
     * Reads one field as a number whatever numeric type the entity uses ({@code numeric} fields are
     * Long, {@code floating-point} ones are Double).
     *
     * @param value the entity value
     * @param field the field name
     * @return the value as a double, {@code 0} when empty
     */
    private static double numberOf(GenericValue value, String field) {
        Object object = value.get(field);
        return object instanceof Number ? ((Number) object).doubleValue() : 0.0;
    }

    /**
     * Builds the history batches of the MES shop floor report: the reported labour of every day,
     * week or month becomes one point, its quantity the good units and its value the hours.
     *
     * <p>This is the "hand the shop floor report to the curve" path: the unit hours of each period
     * are already the moving average the curve is fitted to, so nobody has to type
     * {@code 数量:工时} by hand.
     *
     * @param dctx the dispatch context
     * @param context service context, see {@code servicedef/services.xml#learningCurveMesPeriods}
     * @return the service result map containing {@code periods} and {@code historyData}
     */
    public static Map<String, Object> mesPeriods(DispatchContext dctx, Map<String, ? extends Object> context) {
        String groupBy = normalizeGroupBy(toText(context.get("groupBy")));
        String productCode = toText(context.get("productCode"));
        java.sql.Date fromDate = parseDay(context.get("fromDate"));
        java.sql.Date toDate = parseDay(context.get("toDate"));
        List<Map<String, Object>> rows = new ArrayList<>();
        try {
            Delegator delegator = dctx.getDelegator();
            List<GenericValue> reports;
            if (productCode == null) {
                reports = EntityQuery.use(delegator).from("MfgReport").queryList();
            } else {
                // a report knows its work order, the work order knows the model
                List<String> workOrders = new ArrayList<>();
                for (GenericValue order : EntityQuery.use(delegator).from("MfgWorkOrder")
                        .where("productCode", productCode).queryList()) {
                    workOrders.add(order.getString("workOrderId"));
                }
                reports = new ArrayList<>();
                for (GenericValue report : EntityQuery.use(delegator).from("MfgReport").queryList()) {
                    if (workOrders.contains(report.getString("workOrderId"))) {
                        reports.add(report);
                    }
                }
            }
            for (GenericValue report : reports) {
                java.sql.Timestamp reportDate = report.getTimestamp("reportDate");
                if (reportDate == null) {
                    continue;
                }
                java.sql.Date day = new java.sql.Date(reportDate.getTime());
                if (fromDate != null && day.before(fromDate)) {
                    continue;
                }
                if (toDate != null && day.after(toDate)) {
                    continue;
                }
                Map<String, Object> row = new HashMap<>();
                row.put("date", day);
                row.put("qtyOk", Double.valueOf(numberOf(report, "qtyOk")));
                row.put("hours", Double.valueOf(numberOf(report, "workHours")));
                rows.add(row);
            }
        } catch (GeneralException e) {
            Map<String, Object> empty = emptyMesResult("读不到 MES 的报工数据（MES 组件可能没部署）：" + e.getMessage());
            empty.put("groupBy", groupBy);
            return empty;
        } catch (RuntimeException e) {
            Map<String, Object> empty = emptyMesResult("读不到 MES 的报工数据（MES 组件可能没部署）：" + e.getMessage());
            empty.put("groupBy", groupBy);
            return empty;
        }
        Map<String, Object> result = buildMesPeriods(rows, groupBy);
        result.put("productCode", productCode);
        result.put("groupBy", groupBy);
        result.put("message", rows.isEmpty()
                ? (productCode == null ? "MES 里还没有报工记录。" : "这个型号还没有报工记录。")
                : null);
        return result;
    }

    /**
     * Buckets the reported rows by day, ISO week or month and turns them into the history string.
     *
     * <p>A period without good units or without hours is skipped, because it would make the unit
     * value infinite; the periods are kept in chronological order because the cumulative quantity
     * is what the curve is fitted on.
     *
     * @param rows rows with {@code date} (a day), {@code qtyOk} and {@code hours}; may be empty
     * @param groupBy {@code DAY}, {@code WEEK} or {@code MONTH}
     * @return the service result map with {@code periods}, {@code periodCount},
     *         {@code skippedCount}, {@code totalQty}, {@code totalHours} and {@code historyData}
     */
    public static Map<String, Object> buildMesPeriods(List<Map<String, Object>> rows, String groupBy) {
        String bucket = normalizeGroupBy(groupBy);
        Map<String, double[]> totals = new java.util.TreeMap<>();
        int skipped = 0;
        for (Map<String, Object> row : rows) {
            java.time.LocalDate day = toLocalDate(row.get("date"));
            if (day == null) {
                skipped++;
                continue;
            }
            double qtyOk = row.get("qtyOk") instanceof Number ? ((Number) row.get("qtyOk")).doubleValue() : 0.0;
            double hours = row.get("hours") instanceof Number ? ((Number) row.get("hours")).doubleValue() : 0.0;
            if (qtyOk <= 0.0 || hours <= 0.0) {
                skipped++;
                continue;
            }
            String key = periodKeyOf(bucket, day);
            double[] total = totals.get(key);
            if (total == null) {
                total = new double[] {0.0, 0.0};
                totals.put(key, total);
            }
            total[0] += qtyOk;
            total[1] += hours;
        }
        java.text.DecimalFormat format = new java.text.DecimalFormat("0.###",
                java.text.DecimalFormatSymbols.getInstance(Locale.ROOT));
        List<Map<String, Object>> periods = new ArrayList<>();
        StringBuilder history = new StringBuilder();
        double totalQty = 0.0;
        double totalHours = 0.0;
        for (Map.Entry<String, double[]> entry : totals.entrySet()) {
            double qtyOk = entry.getValue()[0];
            double hours = entry.getValue()[1];
            Map<String, Object> period = new HashMap<>();
            period.put("periodKey", entry.getKey());
            period.put("qtyOk", Double.valueOf(qtyOk));
            period.put("hours", Double.valueOf(hours));
            period.put("unitHours", Double.valueOf(hours / qtyOk));
            periods.add(period);
            totalQty += qtyOk;
            totalHours += hours;
            if (history.length() > 0) {
                history.append(';');
            }
            history.append(format.format(qtyOk)).append(':').append(format.format(hours));
        }
        Map<String, Object> result = ServiceUtil.returnSuccess();
        result.put("periods", periods);
        result.put("periodCount", Integer.valueOf(periods.size()));
        result.put("skippedCount", Integer.valueOf(skipped));
        result.put("totalQty", Double.valueOf(totalQty));
        result.put("totalHours", Double.valueOf(totalHours));
        result.put("historyData", history.toString());
        result.put("groupBy", bucket);
        return result;
    }

    /**
     * The label of the period a day belongs to.
     *
     * @param groupBy {@code DAY}, {@code WEEK} or {@code MONTH}
     * @param day the day
     * @return {@code 2026-10-22}, {@code 2026-W43} or {@code 2026-10}
     */
    static String periodKeyOf(String groupBy, java.time.LocalDate day) {
        if (day == null) {
            return "";
        }
        String bucket = normalizeGroupBy(groupBy);
        if ("DAY".equals(bucket)) {
            return day.toString();
        }
        if ("MONTH".equals(bucket)) {
            return String.format(Locale.ROOT, "%04d-%02d", Integer.valueOf(day.getYear()),
                    Integer.valueOf(day.getMonthValue()));
        }
        java.time.temporal.WeekFields iso = java.time.temporal.WeekFields.ISO;
        int week = day.get(iso.weekOfWeekBasedYear());
        int year = day.get(iso.weekBasedYear());
        return String.format(Locale.ROOT, "%04d-W%02d", Integer.valueOf(year), Integer.valueOf(week));
    }

    /**
     * Normalizes the grouping of the MES periods.
     *
     * @param groupBy the raw value, may be {@code null}
     * @return {@code DAY}, {@code WEEK} (the default) or {@code MONTH}
     */
    static String normalizeGroupBy(String groupBy) {
        if (groupBy == null) {
            return "WEEK";
        }
        String upper = groupBy.trim().toUpperCase(Locale.ROOT);
        if ("DAY".equals(upper) || "MONTH".equals(upper)) {
            return upper;
        }
        return "WEEK";
    }

    private static java.time.LocalDate toLocalDate(Object value) {
        if (value instanceof java.sql.Date) {
            return ((java.sql.Date) value).toLocalDate();
        }
        if (value instanceof java.util.Date) {
            return new java.sql.Date(((java.util.Date) value).getTime()).toLocalDate();
        }
        String text = toText(value);
        if (text == null) {
            return null;
        }
        try {
            return java.time.LocalDate.parse(text.length() > 10 ? text.substring(0, 10) : text);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static java.sql.Date parseDay(Object value) {
        java.time.LocalDate day = toLocalDate(value);
        return day == null ? null : java.sql.Date.valueOf(day);
    }

    // ==================== writing the curve back as the standard hours of a model ====================

    /** Source marker the MES product carries while its standard hours came from a learning curve. */
    public static final String HOURS_SOURCE_CURVE = "LEARNING_CURVE";

    /** Source marker of hours typed in by hand; a model without one counts as hand filled in. */
    public static final String HOURS_SOURCE_MANUAL = "MANUAL";

    /** A curve can only become standard hours when it was fitted on hours. */
    private static final String MEASURE_HOURS = "HOURS";

    /** How many past backfills the record page lists. */
    private static final int APPLY_HISTORY = 5;

    /**
     * Works out the standard hours the stored curve would give a model, without changing anything.
     *
     * <p>The unit hours are the hours the curve predicts for the next {@code baseQty} units divided
     * by that quantity, which is exactly the unit value the estimation page shows.
     *
     * @param dctx the dispatch context
     * @param context service context, see {@code servicedef/services.xml#learningCurveStandardHours}
     * @return the service result containing the proposal, the hours the model uses today and whether
     *         it can be written back
     */
    public static Map<String, Object> standardHours(DispatchContext dctx, Map<String, ? extends Object> context) {
        Delegator delegator = dctx.getDelegator();
        String estimateId = toText(context.get("estimateId"));
        if (estimateId == null) {
            return emptyHoursResult("先保存一条估算，再从估算详情页回填标准工时");
        }
        Map<String, Object> result;
        try {
            result = proposeHours(delegator, estimateId, context.get("baseQty"), newHandlerContext(context));
        } catch (GenericEntityException e) {
            return emptyHoursResult("读不到这条估算：" + e.getMessage());
        } catch (GeneralException e) {
            return emptyHoursResult(e.getMessage());
        }
        appendApplyRows(delegator, result);
        return result;
    }

    /**
     * Writes the hours of the stored curve into the standard hours of the MES model and keeps a row
     * describing the change, so the previous value is never lost.
     *
     * @param dctx the dispatch context
     * @param context service context, see {@code servicedef/services.xml#learningCurveApplyStandardHours}
     * @return the service result containing the new hours and the identifier of the audit row
     */
    public static Map<String, Object> applyStandardHours(DispatchContext dctx, Map<String, ? extends Object> context) {
        Delegator delegator = dctx.getDelegator();
        String estimateId = toText(context.get("estimateId"));
        if (estimateId == null) {
            return ServiceUtil.returnError("estimateId is required");
        }
        try {
            Map<String, Object> proposal = proposeHours(delegator, estimateId, context.get("baseQty"), newHandlerContext(context));
            if (!"Y".equals(proposal.get("canApply"))) {
                return ServiceUtil.returnError(String.valueOf(proposal.get("message")));
            }
            String productCode = String.valueOf(proposal.get("productCode"));
            GenericValue product = mesProduct(delegator, productCode);
            if (product == null) {
                return ServiceUtil.returnError("MES 里没有型号 " + productCode + "，先在产品页建好型号再回填");
            }
            double baseQty = ((Double) proposal.get("baseQty")).doubleValue();
            double newHours = ((Double) proposal.get("proposedUnitHours")).doubleValue();
            double oldHours = toDouble(product.get("standardHours"), 0.0);

            product.set("standardHours", Double.valueOf(newHours));
            product.set("standardHoursSource", HOURS_SOURCE_CURVE);
            product.set("standardHoursBaseQty", Double.valueOf(baseQty));
            product.set("standardHoursEstimateRef", estimateId);
            delegator.store(product);

            String applyId = delegator.getNextSeqId("LearningCurveHoursApply");
            GenericValue apply = delegator.makeValue("LearningCurveHoursApply");
            apply.set("applyId", applyId);
            apply.set("productCode", productCode);
            apply.set("estimateId", estimateId);
            apply.set("baseQty", Double.valueOf(baseQty));
            apply.set("oldHours", Double.valueOf(oldHours));
            apply.set("newHours", Double.valueOf(newHours));
            apply.set("methodType", proposal.get("methodType"));
            apply.set("slope", proposal.get("slope"));
            apply.set("intercept", proposal.get("intercept"));
            apply.set("appliedBy", appliedBy(context.get("userLogin")));
            delegator.create(apply);

            Map<String, Object> result = ServiceUtil.returnSuccess();
            result.put("applyId", applyId);
            result.put("estimateId", estimateId);
            result.put("productCode", productCode);
            result.put("baseQty", Double.valueOf(baseQty));
            result.put("oldHours", Double.valueOf(oldHours));
            result.put("appliedHours", Double.valueOf(newHours));
            result.put("applied", "Y");
            result.put("message", "已把 " + productCode + " 的标准工时从 "
                    + String.format(Locale.ROOT, "%.3f", oldHours) + " h 改成 "
                    + String.format(Locale.ROOT, "%.3f", newHours) + " h（基准 "
                    + String.format(Locale.ROOT, "%.0f", baseQty) + " 台）");
            return result;
        } catch (GenericEntityException e) {
            return ServiceUtil.returnError("回填失败：" + e.getMessage());
        } catch (GeneralException e) {
            return ServiceUtil.returnError(e.getMessage());
        }
    }

    /**
     * Unit hours of one lot: the hours divided by the quantity, 0 for an empty lot.
     *
     * @param lotHours the hours of the whole lot
     * @param baseQty the lot size
     * @return the hours of one unit
     */
    static double unitHours(double lotHours, double baseQty) {
        return baseQty <= 0.0 ? 0.0 : lotHours / baseQty;
    }

    /**
     * Relative change between two values, in percent.
     *
     * @param from the value used today
     * @param to the proposed value
     * @return the change in percent, 0 when there is nothing to compare against
     */
    static double percentChange(double from, double to) {
        return from == 0.0 ? 0.0 : (to - from) / from * 100.0;
    }

    /**
     * Turns the curve of an estimate into the hours of one unit, and reads what the model uses today.
     *
     * @param delegator the delegator
     * @param estimateId the stored estimate
     * @param baseQtyParam the lot size to spread the curve over, empty takes the one of the estimate
     * @param handlerContext context for the curve handler (it needs the locale)
     * @return the proposal, with {@code canApply} saying whether it can be written back
     * @throws GeneralException when the estimate or its history data cannot be read
     * @throws GenericEntityException when the estimate cannot be read from the database
     */
    private static Map<String, Object> proposeHours(Delegator delegator, String estimateId, Object baseQtyParam,
            Map<String, Object> handlerContext) throws GeneralException, GenericEntityException {
        GenericValue estimate = EntityQuery.use(delegator).from("LearningCurveEstimate")
                .where("estimateId", estimateId).queryOne();
        if (estimate == null) {
            return emptyHoursResult("找不到估算 " + estimateId);
        }
        String productCode = estimate.getString("productCode");
        String measureType = estimate.getString("measureType");
        double baseQty = toDouble(baseQtyParam, toDouble(estimate.get("newUnitNumber"), 0.0));
        Long method = estimate.getLong("methodType");
        int methodType = method == null ? I_OFBizLearningCurve.WRIGHT : method.intValue();
        double leastSquare = toDouble(estimate.get("leastSquareTarget"), DEFAULT_LEAST_SQUARE);

        Map<String, Object> result = emptyHoursResult(null);
        result.put("estimateId", estimateId);
        result.put("productCode", productCode == null ? "" : productCode);
        result.put("measureType", measureType == null ? "" : measureType);
        result.put("methodType", Long.valueOf(methodType));
        result.put("slope", estimate.get("slope"));
        result.put("intercept", estimate.get("intercept"));
        result.put("baseQty", Double.valueOf(baseQty));

        if (!MEASURE_HOURS.equals(measureType)) {
            result.put("message", "这条估算按成本口径算的，没有工时可比；按工时口径重新估算后再回填");
            return result;
        }
        if (baseQty <= 0.0) {
            result.put("message", "基准批量要大于 0：它决定把曲线上的哪一批摊成单台工时");
            return result;
        }
        Matrix history = parseHistoryData(estimate.getString("historyData"));
        double lotHours = OFBizLearningCurveHandler.getInstance()
                .getEstimate(leastSquare, history, baseQty, handlerContext, methodType);
        double hours = unitHours(lotHours, baseQty);
        result.put("estimateTotal", Double.valueOf(lotHours));
        result.put("proposedUnitHours", Double.valueOf(hours));

        GenericValue product = mesProduct(delegator, productCode);
        if (product == null) {
            result.put("message", "MES 里没有型号 " + productCode + "，先在产品页建好型号再回填");
            return result;
        }
        double currentHours = toDouble(product.get("standardHours"), 0.0);
        result.put("existsInMes", "Y");
        result.put("currentHours", Double.valueOf(currentHours));
        result.put("currentSource", textOrEmpty(product.getString("standardHoursSource")) == ""
                ? HOURS_SOURCE_MANUAL : product.getString("standardHoursSource"));
        result.put("currentBaseQty", Double.valueOf(toDouble(product.get("standardHoursBaseQty"), 0.0)));
        result.put("currentRef", textOrEmpty(product.getString("standardHoursEstimateRef")));
        result.put("deltaHours", Double.valueOf(hours - currentHours));
        result.put("deltaPercent", Double.valueOf(percentChange(currentHours, hours)));
        result.put("canApply", "Y");
        return result;
    }

    /**
     * Reads a model of the MES component; {@code null} when it is missing or that component is not
     * deployed, which is not an error here - there is simply nothing to fill in.
     *
     * @param delegator the delegator
     * @param productCode the model code
     * @return the product row, or {@code null}
     */
    private static GenericValue mesProduct(Delegator delegator, String productCode) {
        if (productCode == null) {
            return null;
        }
        try {
            return EntityQuery.use(delegator).from("MfgProduct").where("productCode", productCode).queryOne();
        } catch (GenericEntityException e) {
            return null;
        }
    }

    /**
     * Adds the last backfills of the model to a proposal, newest first.
     *
     * @param delegator the delegator
     * @param result the proposal to complete
     */
    private static void appendApplyRows(Delegator delegator, Map<String, Object> result) {
        List<Map<String, Object>> rows = new ArrayList<>();
        Object productCode = result.get("productCode");
        if (productCode != null && toText(productCode) != null) {
            try {
                List<GenericValue> applies = EntityQuery.use(delegator).from("LearningCurveHoursApply")
                        .where("productCode", toText(productCode))
                        .orderBy("-createdStamp").maxRows(APPLY_HISTORY).queryList();
                for (GenericValue apply : applies) {
                    Map<String, Object> row = new HashMap<>();
                    row.put("applyId", apply.getString("applyId"));
                    row.put("estimateId", apply.getString("estimateId"));
                    row.put("baseQty", apply.get("baseQty"));
                    row.put("oldHours", apply.get("oldHours"));
                    row.put("newHours", apply.get("newHours"));
                    row.put("appliedBy", apply.getString("appliedBy"));
                    row.put("createdStamp", apply.get("createdStamp"));
                    rows.add(row);
                }
            } catch (GenericEntityException e) {
                rows.clear();
            }
        }
        result.put("applies", rows);
    }

    /**
     * Builds a proposal that carries the neutral values, so the page can render it without checks.
     *
     * @param message the reason why nothing can be written back, empty or {@code null} when it can
     * @return the result map
     */
    private static Map<String, Object> emptyHoursResult(String message) {
        Map<String, Object> result = ServiceUtil.returnSuccess();
        result.put("estimateId", "");
        result.put("productCode", "");
        result.put("measureType", "");
        result.put("methodType", Long.valueOf(0));
        result.put("slope", Double.valueOf(0.0));
        result.put("intercept", Double.valueOf(0.0));
        result.put("baseQty", Double.valueOf(0.0));
        result.put("estimateTotal", Double.valueOf(0.0));
        result.put("proposedUnitHours", Double.valueOf(0.0));
        result.put("currentHours", Double.valueOf(0.0));
        result.put("currentSource", "");
        result.put("currentBaseQty", Double.valueOf(0.0));
        result.put("currentRef", "");
        result.put("deltaHours", Double.valueOf(0.0));
        result.put("deltaPercent", Double.valueOf(0.0));
        result.put("existsInMes", "N");
        result.put("canApply", "N");
        result.put("applies", new ArrayList<Map<String, Object>>());
        result.put("message", message == null ? "" : message);
        return result;
    }

    /**
     * Reads the login name out of the service context.
     *
     * @param userLogin the {@code userLogin} generic value, may be {@code null}
     * @return the login name, {@code null} when the service was called without one
     */
    private static String appliedBy(Object userLogin) {
        return userLogin instanceof GenericValue ? ((GenericValue) userLogin).getString("userLoginId") : null;
    }

    private static String textOrEmpty(String value) {
        return value == null ? "" : value;
    }

    // ==================== software quotes: size, effort, price ====================

    /** Parameters the software estimate understands, forwarded unchanged to the appliance component. */
    private static final String[] SOFTWARE_PARAMS = {
        "ilfCount", "eifCount", "ufp", "swfBase", "swfReuse", "swfPreInvest", "swfQuality",
        "rdf", "pdrHoursPerFp", "dayRate", "taxRate", "dncRate"
    };

    /** Values the estimate returns and the screen shows. */
    private static final String[] SOFTWARE_RESULTS = {
        "ufp", "swf", "effortHours", "effortDays", "scheduleMonths", "amountBeforeTax",
        "taxAmount", "amountWithTax", "dncAmount", "totalAmount", "costPerFp"
    };

    /** Values kept with a saved quote (the entity only holds the ones it declares). */
    private static final String[] SOFTWARE_QUOTE_FIELDS = {
        "ufp", "effortHours", "effortDays", "scheduleMonths", "amountBeforeTax", "taxAmount",
        "amountWithTax", "dncAmount", "totalAmount"
    };

    /**
     * Quotes software from its size. The formulas live in the appliance component
     * ({@code mfgSoftwareEstimate}), so the estimate screen of MFG and this one always tell the same
     * numbers; this service only forwards the parameters and returns the result.
     *
     * @param dctx the dispatch context
     * @param context service context, see {@code servicedef/services.xml#learningCurveSoftwareQuote}
     * @return the service result with the size, effort, schedule and prices
     */
    public static Map<String, Object> softwareQuote(DispatchContext dctx, Map<String, ? extends Object> context) {
        Map<String, Object> estimate;
        try {
            estimate = dctx.getDispatcher().runSync("mfgSoftwareEstimate", softwareParams(context));
        } catch (GenericServiceException e) {
            return ServiceUtil.returnError("MES 组件未部署或服务不可用（mfgSoftwareEstimate）：" + e.getMessage());
        }
        if (ServiceUtil.isError(estimate)) {
            return ServiceUtil.returnError(ServiceUtil.getErrorMessage(estimate));
        }
        Map<String, Object> result = ServiceUtil.returnSuccess();
        for (String field : SOFTWARE_RESULTS) {
            result.put(field, estimate.get(field));
        }
        return result;
    }

    /**
     * Quotes the software and keeps the result as a record, together with the parameters that were
     * used, so a later change of the rates does not rewrite an old quotation.
     *
     * @param dctx the dispatch context
     * @param context service context, see {@code servicedef/services.xml#learningCurveSaveSoftwareQuote}
     * @return the service result containing {@code quoteId} and the computed values
     */
    public static Map<String, Object> saveSoftwareQuote(DispatchContext dctx, Map<String, ? extends Object> context) {
        Map<String, Object> quote = softwareQuote(dctx, context);
        if (ServiceUtil.isError(quote)) {
            return quote;
        }
        try {
            Delegator delegator = dctx.getDelegator();
            String quoteId = delegator.getNextSeqId("LearningCurveSoftwareQuote");
            GenericValue value = delegator.makeValue("LearningCurveSoftwareQuote");
            value.set("quoteId", quoteId);
            value.set("projectName", toText(context.get("projectName")));
            value.set("customerName", toText(context.get("customerName")));
            for (String field : SOFTWARE_PARAMS) {
                Double number = asDouble(context.get(field));
                if (number != null) {
                    value.set(field, number);
                }
            }
            for (String field : SOFTWARE_QUOTE_FIELDS) {
                value.set(field, quote.get(field));
            }
            value.set("note", toText(context.get("note")));
            delegator.create(value);

            Map<String, Object> result = ServiceUtil.returnSuccess();
            result.put("quoteId", quoteId);
            for (String field : SOFTWARE_RESULTS) {
                result.put(field, quote.get(field));
            }
            return result;
        } catch (GenericEntityException e) {
            return ServiceUtil.returnError("Could not save the quote: " + e.getMessage());
        }
    }

    /**
     * Lists the saved quotations, newest first, with the last one as the default of the form.
     *
     * @param dctx the dispatch context
     * @param context service context, see {@code servicedef/services.xml#learningCurveListSoftwareQuotes}
     * @return the service result containing {@code quotes}, {@code quoteCount} and {@code lastQuote}
     */
    public static Map<String, Object> listSoftwareQuotes(DispatchContext dctx, Map<String, ? extends Object> context) {
        try {
            List<GenericValue> quotes = EntityQuery.use(dctx.getDelegator())
                    .from("LearningCurveSoftwareQuote").orderBy("-createdStamp").queryList();
            List<Map<String, Object>> rows = new ArrayList<>(quotes.size());
            double total = 0.0;
            for (GenericValue quote : quotes) {
                rows.add(toQuoteMap(quote));
                total += numberOf(quote, "totalAmount");
            }
            Map<String, Object> result = ServiceUtil.returnSuccess();
            result.put("quotes", rows);
            result.put("quoteCount", Integer.valueOf(rows.size()));
            result.put("totalAmount", Double.valueOf(total));
            result.put("lastQuote", rows.isEmpty() ? null : rows.get(0));
            return result;
        } catch (GenericEntityException e) {
            return ServiceUtil.returnError("Could not read the software quotes: " + e.getMessage());
        }
    }

    /**
     * Forecasts the productivity of the next project from the finished ones: the real hours per
     * function point of each delivery are fitted with the Wright curve, so "the third firmware is
     * cheaper than the first" becomes a number instead of a feeling. The appliance component holds
     * the ledger this reads.
     *
     * @param dctx the dispatch context
     * @param context service context, see {@code servicedef/services.xml#learningCurvePdrForecast}
     * @return the service result with the samples, the predicted productivity and the curve
     */
    public static Map<String, Object> pdrForecast(DispatchContext dctx, Map<String, ? extends Object> context) {
        Map<String, Object> result = ServiceUtil.returnSuccess();
        result.put("samples", new ArrayList<Map<String, Object>>());
        result.put("sampleCount", Integer.valueOf(0));
        List<Map<String, Object>> rows = new ArrayList<>();
        try {
            List<GenericValue> projects = EntityQuery.use(dctx.getDelegator())
                    .from("MfgSoftwareProject").where("statusId", "MFG_SW_DONE")
                    .orderBy("deliverySeq", "createdStamp").queryList();
            for (GenericValue project : projects) {
                double ufp = numberOf(project, "ufp");
                double hours = numberOf(project, "actualHours");
                if (ufp <= 0.0 || hours <= 0.0) {
                    continue;
                }
                Map<String, Object> row = new HashMap<>();
                row.put("projectCode", project.getString("projectCode"));
                row.put("projectName", project.getString("projectName"));
                row.put("deliverySeq", project.get("deliverySeq"));
                row.put("pdr", Double.valueOf(hours / ufp));
                rows.add(row);
            }
        } catch (GenericEntityException e) {
            result.put("message", "读不到 MES 的软件项目（MES 未部署？）：" + e.getMessage());
            return result;
        }
        result.put("samples", rows);
        result.put("sampleCount", Integer.valueOf(rows.size()));
        if (rows.size() < 2) {
            result.put("message", "已完成并填了实际工时的项目不足 2 个，先积累数据再预测");
            return result;
        }
        // when every delivery has the same productivity the least squares would be singular:
        // the honest forecast is "the same again", not an error
        double first = ((Number) rows.get(0).get("pdr")).doubleValue();
        boolean flat = true;
        for (Map<String, Object> row : rows) {
            if (Math.abs(((Number) row.get("pdr")).doubleValue() - first) > 1e-9) {
                flat = false;
                break;
            }
        }
        if (flat) {
            result.put("predictedPdr", Double.valueOf(first));
            result.put("slope", Double.valueOf(1.0));
            result.put("intercept", Double.valueOf(first));
            result.put("leastSquare", Double.valueOf(1.0));
            result.put("message", "实测 PDR 完全一样，暂时看不出学习效应");
            return result;
        }
        try {
            Matrix series = buildPdrSeries(rows);
            double leastSquare = toDouble(context.get("leastSquare"), DEFAULT_LEAST_SQUARE);
            Map<String, Object> handlerContext = newHandlerContext(context);
            OFBizLearningCurveHandler handler = OFBizLearningCurveHandler.getInstance();
            double predicted = handler.getEstimate(leastSquare, series, 1.0, handlerContext,
                    I_OFBizLearningCurve.WRIGHT);
            LearningCurve curve = handler.getLearningCurve(leastSquare, series, handlerContext,
                    I_OFBizLearningCurve.WRIGHT);
            result.put("predictedPdr", Double.valueOf(predicted));
            // + 0.0 turns a negative zero into a positive one, so the page does not show "-0"
            result.put("slope", Double.valueOf(curve.getSlope() + 0.0));
            result.put("intercept", Double.valueOf(curve.getIntercept() + 0.0));
            result.put("leastSquare", Double.valueOf(curve.getLeastSquare()));
            return result;
        } catch (GeneralException e) {
            result.put("message", "拟合失败：" + e.getMessage());
            return result;
        } catch (RuntimeException e) {
            // Jama reports a singular system as an unchecked exception
            result.put("predictedPdr", Double.valueOf(first));
            result.put("message", "样本太少或过于集中，暂时用最近一次的实测 PDR 代替拟合");
            return result;
        }
    }

    /**
     * Turns a series of deliveries into the history rows the curve expects: one unit per delivery,
     * its value being the hours per function point of that delivery.
     *
     * @param rows rows with a {@code pdr} value, oldest first
     * @return the history matrix with one row per delivery
     */
    static Matrix buildPdrSeries(List<Map<String, Object>> rows) {
        double[][] data = new double[rows.size()][2];
        for (int i = 0; i < rows.size(); i++) {
            Object value = rows.get(i).get("pdr");
            data[i][0] = 1.0;
            data[i][1] = value instanceof Number ? ((Number) value).doubleValue() : 0.0;
        }
        return new Matrix(data, rows.size(), 2);
    }

    /**
     * Turns a saved quotation into a software project of the appliance component, so the quote and
     * the ledger row that will report the real hours are the same thing. Calling it twice returns
     * the project created the first time instead of creating another one.
     *
     * @param dctx the dispatch context
     * @param context service context, see {@code servicedef/services.xml#learningCurveQuoteToProject}
     * @return the service result containing {@code projectCode}
     */
    public static Map<String, Object> quoteToProject(DispatchContext dctx, Map<String, ? extends Object> context) {
        String quoteId = toText(context.get("quoteId"));
        if (quoteId == null) {
            return ServiceUtil.returnError("quoteId is required");
        }
        try {
            GenericValue quote = EntityQuery.use(dctx.getDelegator()).from("LearningCurveSoftwareQuote")
                    .where("quoteId", quoteId).queryOne();
            if (quote == null) {
                return ServiceUtil.returnError("Unknown quote [" + quoteId + "]");
            }
            String existing = toText(quote.getString("projectCode"));
            if (existing != null) {
                Map<String, Object> done = ServiceUtil.returnSuccess();
                done.put("projectCode", existing);
                done.put("message", "这个报价已经建过项目：" + existing);
                return done;
            }
            Map<String, Object> params = new HashMap<>();
            String projectName = toText(quote.getString("projectName"));
            params.put("projectName", projectName == null ? quoteId : projectName);
            String categoryId = toText(context.get("categoryId"));
            params.put("categoryId", categoryId == null ? "MFG_SW_OTHER" : categoryId);
            params.put("statusId", "MFG_SW_PLANNED");
            for (String field : SOFTWARE_PARAMS) {
                Object value = quote.get(field);
                if (value != null) {
                    params.put(field, value);
                }
            }
            params.put("quotedAmount", quote.get("totalAmount"));
            params.put("note", "来自报价 " + quoteId);
            Map<String, Object> saved = dctx.getDispatcher().runSync("mfgSoftwareProjectSave", params);
            if (ServiceUtil.isError(saved)) {
                return ServiceUtil.returnError(ServiceUtil.getErrorMessage(saved));
            }
            String projectCode = (String) saved.get("projectCode");
            quote.set("projectCode", projectCode);
            dctx.getDelegator().store(quote);

            Map<String, Object> result = ServiceUtil.returnSuccess();
            result.put("projectCode", projectCode);
            return result;
        } catch (GenericServiceException e) {
            return ServiceUtil.returnError("MES 组件未部署或服务不可用（mfgSoftwareProjectSave）：" + e.getMessage());
        } catch (GenericEntityException e) {
            return ServiceUtil.returnError("Could not convert the quote: " + e.getMessage());
        }
    }

    /**
     * Copies the parameters of the form, leaving out the empty ones so the service engine does not
     * have to convert an empty string into a number.
     *
     * @param context the service context
     * @return the parameters of the software estimate
     */
    private static Map<String, Object> softwareParams(Map<String, ? extends Object> context) {
        Map<String, Object> params = new HashMap<>();
        for (String field : SOFTWARE_PARAMS) {
            Object value = context.get(field);
            if (value != null && toText(value) != null) {
                params.put(field, value);
            }
        }
        return params;
    }

    private static Double asDouble(Object value) {
        String text = toText(value);
        if (text == null) {
            return null;
        }
        try {
            return Double.valueOf(text);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Map<String, Object> toQuoteMap(GenericValue value) {
        Map<String, Object> map = new HashMap<>();
        map.put("quoteId", value.getString("quoteId"));
        map.put("projectName", value.getString("projectName"));
        map.put("customerName", value.getString("customerName"));
        for (String field : SOFTWARE_PARAMS) {
            map.put(field, value.get(field));
        }
        for (String field : SOFTWARE_QUOTE_FIELDS) {
            map.put(field, value.get(field));
        }
        map.put("projectCode", value.getString("projectCode"));
        map.put("note", value.getString("note"));
        map.put("createdStamp", value.getTimestamp("createdStamp"));
        return map;
    }

    private static Map<String, Object> emptyMesResult(String message) {        Map<String, Object> result = ServiceUtil.returnSuccess();        result.put("batches", new ArrayList<Map<String, Object>>());
        result.put("batchCount", Integer.valueOf(0));
        result.put("skippedCount", Integer.valueOf(0));
        result.put("totalQty", Double.valueOf(0.0));
        result.put("totalHours", Double.valueOf(0.0));
        result.put("historyData", "");
        result.put("message", message);
        return result;
    }

    private static Map<String, Object> newHandlerContext(Map<String, ? extends Object> context) {        Map<String, Object> handlerContext = new HashMap<>();
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
