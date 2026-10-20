/*
 * This library is part of OFBiz-LearningCurve component of Langhua
 *
 * Copyright (C) 2010  Langhua Opensource Foundation (http://langhua.org)
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3.0 of the License, or (at your option) any later version.
 */

package org.langhua.ofbiz.learningcurve;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.ofbiz.base.util.Debug;
import org.apache.ofbiz.entity.Delegator;
import org.apache.ofbiz.entity.GenericValue;
import org.apache.ofbiz.entity.util.EntityQuery;

/**
 * HTTP events of the learning curve web application.
 */
public final class LearningCurveEvents {

    public static final String MODULE = LearningCurveEvents.class.getName();

    private LearningCurveEvents() {
    }

    /**
     * Exports one stored estimate (header + history batches) as CSV.
     *
     * @param request the request, parameter {@code estimateId} is required
     * @param response the response the CSV is written to
     * @return "success" or "error"
     */
    public static String exportEstimateCsv(HttpServletRequest request, HttpServletResponse response) {
        Delegator delegator = (Delegator) request.getAttribute("delegator");
        String estimateId = request.getParameter("estimateId");
        try {
            GenericValue estimate = EntityQuery.use(delegator).from("LearningCurveEstimate")
                    .where("estimateId", estimateId).queryOne();
            if (estimate == null) {
                request.setAttribute("_ERROR_MESSAGE_", "Estimate not found: " + estimateId);
                return "error";
            }
            List<GenericValue> batches = EntityQuery.use(delegator).from("LearningCurveBatch")
                    .where("estimateId", estimateId).orderBy("batchSeq").queryList();

            response.setContentType("text/csv; charset=UTF-8");
            response.setHeader("Content-Disposition",
                    "attachment; filename=learningcurve-" + estimateId + ".csv");

            PrintWriter writer = response.getWriter();
            // byte order mark so spreadsheet applications detect UTF-8 (Chinese product codes)
            writer.print('\uFEFF');
            writer.println("estimateId,productCode,measureType,methodType,leastSquareTarget,newUnitNumber,"
                    + "estimateTotal,estimateUnit,slope,intercept,leastSquareActual,qualifiedRows,filteredRows,createdStamp");
            writer.println(join(estimate.get("estimateId"), estimate.get("productCode"), estimate.get("measureType"),
                    estimate.get("methodType"), estimate.get("leastSquareTarget"), estimate.get("newUnitNumber"),
                    estimate.get("estimateTotal"), estimate.get("estimateUnit"), estimate.get("slope"),
                    estimate.get("intercept"), estimate.get("leastSquareActual"), estimate.get("qualifiedRows"),
                    estimate.get("filteredRows"), estimate.get("createdStamp")));
            writer.println();
            writer.println("batchSeq,lotSize,lotValue");
            for (GenericValue batch : batches) {
                writer.println(join(batch.get("batchSeq"), batch.get("lotSize"), batch.get("lotValue")));
            }
            writer.flush();
            return "success";
        } catch (IOException | RuntimeException e) {
            Debug.logError(e, "Could not export estimate [" + estimateId + "]", MODULE);
            request.setAttribute("_ERROR_MESSAGE_", e.getMessage());
            return "error";
        } catch (Exception e) {
            Debug.logError(e, "Could not export estimate [" + estimateId + "]", MODULE);
            request.setAttribute("_ERROR_MESSAGE_", e.getMessage());
            return "error";
        }
    }

    private static String join(Object... values) {
        StringBuilder line = new StringBuilder();
        for (int i = 0; i < values.length; i++) {
            if (i > 0) {
                line.append(',');
            }
            line.append(csv(values[i]));
        }
        return line.toString();
    }

    private static String csv(Object value) {
        if (value == null) {
            return "";
        }
        String text = value.toString();
        if (text.indexOf(',') >= 0 || text.indexOf('"') >= 0 || text.indexOf('\n') >= 0) {
            return "\"" + text.replace("\"", "\"\"") + "\"";
        }
        return text;
    }
}
