/*
 * This library is part of OFBiz-MFG component of Langhua
 *
 * Copyright (C) 2010  Langhua Opensource Foundation (http://langhua.org)
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3.0 of the License, or (at your option) any later version.
 */

package org.langhua.ofbiz.mfg;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.ofbiz.base.util.Debug;
import org.apache.ofbiz.base.util.UtilGenerics;
import org.apache.ofbiz.entity.Delegator;
import org.apache.ofbiz.entity.GenericEntityException;
import org.apache.ofbiz.service.GenericServiceException;
import org.apache.ofbiz.service.LocalDispatcher;

/**
 * Web events that download a report as CSV: the dashboard snapshot, the shop floor alerts and the
 * material shortages.
 *
 * <p>The files are written as UTF-8 with a byte order mark, because that is what Excel on a Chinese
 * Windows opens without asking (it would otherwise read them as ANSI and garble the headings).
 * Numbers keep a dot as the decimal separator so the files can also be read by a script.
 */
public final class MfgExportEvents {

    private static final String MODULE = MfgExportEvents.class.getName();

    private static final String CONTENT_TYPE = "text/csv; charset=UTF-8";

    private MfgExportEvents() {
    }

    /**
     * Downloads the alerts of the dashboard: the work orders with a shortage and the claims whose
     * after sales window is closing or closed.
     *
     * @param request the request
     * @param response the response, receives the file
     * @return {@code success} when the file was written, {@code error} otherwise
     */
    public static String alertsCsv(HttpServletRequest request, HttpServletResponse response) {
        Delegator delegator = delegatorOf(request);
        if (delegator == null) {
            return "error";
        }
        try {
            List<String> lines = new ArrayList<>();
            lines.add(MfgServices.csvLine(Arrays.asList("级别", "类型", "编号", "对象", "截止",
                    "剩余小时", "说明")));
            for (Map<String, Object> alert : MfgServices.buildAlerts(delegator)) {
                lines.add(MfgServices.csvLine(Arrays.asList(
                        text(alert.get("level")),
                        typeLabel(text(alert.get("typeId"))),
                        text(alert.get("refId")),
                        text(alert.get("subject")),
                        text(alert.get("dueDate")),
                        text(alert.get("hoursLeft")),
                        text(alert.get("detail")))));
            }
            writeCsv(response, "mfg-alerts", lines);
            return "success";
        } catch (GenericEntityException e) {
            Debug.logError(e, "Could not read the alerts for the CSV export", MODULE);
            return "error";
        }
    }

    /**
     * Downloads the material shortages, one row per component of one open work order.
     *
     * @param request the request
     * @param response the response, receives the file
     * @return {@code success} when the file was written, {@code error} otherwise
     */
    public static String shortageCsv(HttpServletRequest request, HttpServletResponse response) {
        Delegator delegator = delegatorOf(request);
        if (delegator == null) {
            return "error";
        }
        try {
            List<String> lines = new ArrayList<>();
            lines.add(MfgServices.csvLine(Arrays.asList("工单", "型号", "交期", "客户", "料号", "名称",
                    "需求", "已发", "欠料")));
            for (Map<String, Object> row : MfgServices.shortageRows(delegator)) {
                lines.add(MfgServices.csvLine(Arrays.asList(
                        text(row.get("workOrderId")),
                        text(row.get("productCode")),
                        text(row.get("dueDate")),
                        text(row.get("customerName")),
                        text(row.get("componentCode")),
                        text(row.get("componentName")),
                        text(row.get("requiredQty")),
                        text(row.get("issuedQty")),
                        text(row.get("shortage")))));
            }
            writeCsv(response, "mfg-shortage", lines);
            return "success";
        } catch (GenericEntityException e) {
            Debug.logError(e, "Could not read the shortages for the CSV export", MODULE);
            return "error";
        }
    }

    /**
     * Downloads the dashboard as one three column report: {@code section, name, value}. It holds the
     * cards, the daily shipped / claims series, the cost per responsibility and the alerts, so a
     * single file is enough for a meeting note.
     *
     * @param request the request
     * @param response the response, receives the file
     * @return {@code success} when the file was written, {@code error} otherwise
     */
    public static String dashboardCsv(HttpServletRequest request, HttpServletResponse response) {
        Delegator delegator = delegatorOf(request);
        LocalDispatcher dispatcher = (LocalDispatcher) request.getAttribute("dispatcher");
        if (delegator == null || dispatcher == null) {
            return "error";
        }
        try {
            Map<String, Object> board = dispatcher.runSync("mfgDashboard",
                    new java.util.HashMap<String, Object>());
            Map<String, Object> context = new java.util.HashMap<>();
            context.put("dayCount", Long.valueOf(30));
            Map<String, Object> trends = dispatcher.runSync("mfgDashboardTrends", context);

            List<String> lines = new ArrayList<>();
            lines.add(MfgServices.csvLine(Arrays.asList("区段", "名称", "数值")));
            addKpi(lines, "生产", "在制工单", board.get("openOrderCount"));
            addKpi(lines, "生产", "在制计划台数", board.get("openPlannedQty"));
            addKpi(lines, "生产", "今日工时", board.get("todayHours"));
            addKpi(lines, "生产", "今日良品", board.get("todayQtyOk"));
            addKpi(lines, "生产", "今日不良", board.get("todayQtyNg"));
            addKpi(lines, "生产", "今日良率", board.get("todayYield"));

            Map<String, Object> kpi = UtilGenerics.cast(trends.get("kpi"));
            if (kpi != null) {
                addKpi(lines, "售后", "出货台数", kpi.get("shippedUnits"));
                addKpi(lines, "售后", "售后单数", kpi.get("claimCount"));
                addKpi(lines, "售后", "售后发生率", kpi.get("claimRate"));
                addKpi(lines, "售后", "未结售后单", kpi.get("openClaims"));
                addKpi(lines, "售后", "售后成本", kpi.get("costTotal"));
                addKpi(lines, "售后", "单台售后成本", kpi.get("costPerUnit"));
            }

            List<Map<String, Object>> days = UtilGenerics.cast(trends.get("days"));
            if (days != null) {
                for (Map<String, Object> day : days) {
                    addKpi(lines, "趋势", text(day.get("date")) + " 出货台数", day.get("shipped"));
                    addKpi(lines, "趋势", text(day.get("date")) + " 售后单数", day.get("claims"));
                }
            }

            List<Map<String, Object>> responsibilities = UtilGenerics.cast(trends.get("responsibilities"));
            if (responsibilities != null) {
                for (Map<String, Object> row : responsibilities) {
                    addKpi(lines, "责任方成本", text(row.get("responsibility")), row.get("cost"));
                    addKpi(lines, "责任方单数", text(row.get("responsibility")), row.get("claimCount"));
                }
            }

            for (Map<String, Object> alert : MfgServices.buildAlerts(delegator)) {
                addKpi(lines, "告警", text(alert.get("refId")), alert.get("detail"));
            }

            writeCsv(response, "mfg-dashboard", lines);
            return "success";
        } catch (GenericServiceException e) {
            Debug.logError(e, "Could not read the dashboard for the CSV export", MODULE);
            return "error";
        } catch (GenericEntityException e) {
            Debug.logError(e, "Could not read the alerts for the CSV export", MODULE);
            return "error";
        }
    }

    private static void addKpi(List<String> lines, String section, String name, Object value) {
        lines.add(MfgServices.csvLine(Arrays.asList(section, name, text(value))));
    }

    /**
     * Downloads the yield trend: one row per period with the good and rejected quantities.
     *
     * @param request the request, may carry {@code fromDate}, {@code toDate} and {@code groupBy}
     * @param response the response, receives the file
     * @return {@code success} when the file was written, {@code error} otherwise
     */
    public static String yieldCsv(HttpServletRequest request, HttpServletResponse response) {
        Map<String, Object> report = trendOf(request);
        if (report == null) {
            return "error";
        }
        List<Map<String, Object>> rows = UtilGenerics.cast(report.get("rows"));
        List<String> lines = new ArrayList<>();
        lines.add(MfgServices.csvLine(Arrays.asList("期间", "汇总方式", "良品", "不良", "良率%", "工单数")));
        if (rows != null) {
            for (Map<String, Object> row : rows) {
                lines.add(MfgServices.csvLine(Arrays.asList(
                        text(row.get("period")),
                        text(report.get("groupBy")),
                        text(row.get("qtyOk")),
                        text(row.get("qtyNg")),
                        text(row.get("yieldRate")),
                        text(row.get("orderCount")))));
            }
        }
        lines.add(MfgServices.csvLine(Arrays.asList("合计", text(report.get("groupBy")),
                text(report.get("qtyOkTotal")), text(report.get("qtyNgTotal")),
                text(report.get("yieldRate")), "")));
        writeCsv(response, "mfg-yield-trend", lines);
        return "success";
    }

    /**
     * Downloads the labour hour trend: one row per period with the reported hours.
     *
     * @param request the request, may carry {@code fromDate}, {@code toDate} and {@code groupBy}
     * @param response the response, receives the file
     * @return {@code success} when the file was written, {@code error} otherwise
     */
    public static String hoursCsv(HttpServletRequest request, HttpServletResponse response) {
        Map<String, Object> report = trendOf(request);
        if (report == null) {
            return "error";
        }
        List<Map<String, Object>> rows = UtilGenerics.cast(report.get("rows"));
        List<String> lines = new ArrayList<>();
        lines.add(MfgServices.csvLine(Arrays.asList("期间", "汇总方式", "工时", "良品", "单台工时", "工单数")));
        if (rows != null) {
            for (Map<String, Object> row : rows) {
                lines.add(MfgServices.csvLine(Arrays.asList(
                        text(row.get("period")),
                        text(report.get("groupBy")),
                        text(row.get("hours")),
                        text(row.get("qtyOk")),
                        text(row.get("hoursPerUnit")),
                        text(row.get("orderCount")))));
            }
        }
        lines.add(MfgServices.csvLine(Arrays.asList("合计", text(report.get("groupBy")),
                text(report.get("hoursTotal")), text(report.get("qtyOkTotal")),
                text(report.get("hoursPerUnit")), "")));
        writeCsv(response, "mfg-hours-trend", lines);
        return "success";
    }

    /**
     * Runs the trend service with the filters of the request.
     *
     * @param request the request
     * @return the service result, {@code null} when it did not run
     */
    private static Map<String, Object> trendOf(HttpServletRequest request) {
        LocalDispatcher dispatcher = (LocalDispatcher) request.getAttribute("dispatcher");
        if (dispatcher == null) {
            return null;
        }
        Map<String, Object> context = new java.util.HashMap<>();
        context.put("fromDate", request.getParameter("fromDate"));
        context.put("toDate", request.getParameter("toDate"));
        context.put("groupBy", request.getParameter("groupBy"));
        try {
            return dispatcher.runSync("mfgTrendReport", context);
        } catch (GenericServiceException e) {
            Debug.logError(e, "Could not read the trend report for the CSV export", MODULE);
            return null;
        }
    }

    private static String typeLabel(String typeId) {
        if (MfgServices.ALERT_SHORTAGE.equals(typeId)) {
            return "欠料";
        }
        if (MfgServices.ALERT_CLAIM_7DAY.equals(typeId)) {
            return "7 天窗口";
        }
        if (MfgServices.ALERT_CLAIM_AFTERSALE.equals(typeId)) {
            return "15 天窗口";
        }
        return typeId;
    }

    private static Delegator delegatorOf(HttpServletRequest request) {
        Object delegator = request.getAttribute("delegator");
        return delegator instanceof Delegator ? (Delegator) delegator : null;
    }

    private static String text(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof java.math.BigDecimal) {
            return ((java.math.BigDecimal) value).stripTrailingZeros().toPlainString();
        }
        if (value instanceof Double || value instanceof Float) {
            return MfgServices.formatQty(((Number) value).doubleValue());
        }
        return value.toString();
    }

    private static void writeCsv(HttpServletResponse response, String name, List<String> lines) {
        response.setContentType(CONTENT_TYPE);
        response.setHeader("Content-Disposition", "attachment; filename=\"" + name + "-"
                + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE) + ".csv\"");
        StringBuilder text = new StringBuilder();
        for (String line : lines) {
            text.append(line).append("\r\n");
        }
        try {
            OutputStream out = response.getOutputStream();
            // byte order mark, so Excel opens the file as UTF-8 instead of ANSI
            out.write(new byte[] {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF});
            out.write(text.toString().getBytes(StandardCharsets.UTF_8));
            out.flush();
        } catch (IOException e) {
            Debug.logError(e, "Could not write the CSV export [" + name + "]", MODULE);
        }
    }
}
