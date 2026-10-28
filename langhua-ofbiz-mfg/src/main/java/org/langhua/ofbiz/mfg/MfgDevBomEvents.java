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
import org.apache.ofbiz.base.util.UtilHttp;
import org.apache.ofbiz.entity.GenericValue;
import org.apache.ofbiz.service.GenericServiceException;
import org.apache.ofbiz.service.LocalDispatcher;
import org.apache.ofbiz.service.ServiceUtil;

/**
 * Web events of the design BOM of a development project: importing the Fritzing report (a file, or
 * the text pasted in) and downloading the shopping list it produces.
 *
 * <p>As everywhere else in this component the event only turns the request into the context of the
 * service; parsing, comparing and locking stay in {@link MfgDevBomServices}.
 */
public final class MfgDevBomEvents {

    private static final String MODULE = MfgDevBomEvents.class.getName();

    /** Largest BOM file accepted: a Fritzing report of a big board is a few hundred kilobytes. */
    static final int MAX_BOM_BYTES = 4 * 1024 * 1024;

    private MfgDevBomEvents() {
    }

    /**
     * Imports the Fritzing report of the multipart form. The file wins over the text box when both
     * are filled, the same as the product BOM import.
     *
     * @param request the request, holding the parsed multipart form
     * @param response the response
     * @return {@code success} to go back to the project page, {@code error} to render it with a message
     */
    public static String importHtml(HttpServletRequest request, HttpServletResponse response) {
        Map<String, Object> parameters = parametersOf(request);
        String projectCode = text(parameters.get("projectCode"));
        String bomText = text(parameters.get("bomText"));
        byte[] data = bytesOf(parameters.get("file"));
        String fileName = text(parameters.get("_file_fileName"));
        if (data != null && data.length > MAX_BOM_BYTES) {
            return error(request, "BOM 文件太大（超过 4 MB），Fritzing 的 bom.html 一般只有几百 KB");
        }
        if (data != null && data.length > 0) {
            bomText = MfgImportEvents.decode(data);
        }
        if (bomText == null) {
            return error(request, "请选一个 Fritzing 导出的 bom.html（或 CSV），或者把内容粘贴到文本框里");
        }
        LocalDispatcher dispatcher = (LocalDispatcher) request.getAttribute("dispatcher");
        if (dispatcher == null) {
            return error(request, "服务不可用，请稍后重试或重启实例");
        }
        GenericValue userLogin = (GenericValue) request.getSession().getAttribute("userLogin");
        Map<String, Object> context = new java.util.HashMap<>();
        context.put("userLogin", userLogin);
        context.put("projectCode", projectCode);
        context.put("bomText", bomText);
        context.put("fileName", fileName);
        context.put("bomVersion", text(parameters.get("bomVersion")));
        context.put("format", text(parameters.get("format")));
        // a checkbox that is not ticked sends nothing, so its absence means "no" out of the page
        context.put("lock", parameters.get("lock") == null ? "N" : "Y");
        context.put("consumeDropped", parameters.get("consumeDropped") == null ? "N" : "Y");
        context.put("operatorName", userLogin == null ? null : userLogin.getString("userLoginId"));
        try {
            Map<String, Object> result = dispatcher.runSync("mfgDevBomImport", context, -1, true);
            if (ServiceUtil.isError(result)) {
                return error(request, ServiceUtil.getErrorMessage(result));
            }
            request.setAttribute("projectCode", projectCode);
            request.setAttribute("bomVersion", value(result.get("bomVersion")));
            request.setAttribute("bomItemCount", value(result.get("itemCount")));
            request.setAttribute("bomNewCount", value(result.get("newCount")));
            request.setAttribute("bomChangedCount", value(result.get("changedCount")));
            request.setAttribute("bomDroppedCount", value(result.get("droppedCount")));
            request.setAttribute("bomShortCount", value(result.get("shortCount")));
            request.setAttribute("bomLockedQty", value(result.get("lockedQty")));
            request.setAttribute("bomConsumedQty", value(result.get("consumedQty")));
            return "success";
        } catch (GenericServiceException e) {
            Debug.logError(e, "Could not run mfgDevBomImport", MODULE);
            return error(request, "导入失败：" + e.getMessage());
        }
    }

    /**
     * Downloads the shopping list of the current version of a project: one line per part that is
     * short, with what the version needs, what is locked, what is on the shelf and how much to buy.
     *
     * @param request the request, holding {@code projectCode}
     * @param response the response, receives the file
     * @return {@code success} when the file was written, {@code error} otherwise
     */
    public static String shortageCsv(HttpServletRequest request, HttpServletResponse response) {
        String projectCode = text(UtilHttp.getParameterMap(request).get("projectCode"));
        LocalDispatcher dispatcher = (LocalDispatcher) request.getAttribute("dispatcher");
        if (dispatcher == null || projectCode == null) {
            return "error";
        }
        GenericValue userLogin = (GenericValue) request.getSession().getAttribute("userLogin");
        Map<String, Object> context = new java.util.HashMap<>();
        context.put("userLogin", userLogin);
        context.put("projectCode", projectCode);
        try {
            Map<String, Object> result = dispatcher.runSync("mfgDevBomDetail", context, -1, true);
            if (ServiceUtil.isError(result)) {
                return "error";
            }
            List<Map<String, Object>> shortages = UtilGenerics.cast(result.get("shortages"));
            List<String> lines = new ArrayList<>();
            lines.add(MfgCsv.csvLine(Arrays.asList("料号", "名称", "需求", "已锁", "在库",
                    "缺口", "建议采购", "位号", "说明")));
            for (Map<String, Object> row : shortages == null ? new ArrayList<Map<String, Object>>() : shortages) {
                lines.add(MfgCsv.csvLine(Arrays.asList(
                        value(row.get("componentCode")),
                        value(row.get("componentName")),
                        value(row.get("qty")),
                        value(row.get("lockedQty")),
                        value(row.get("onHandQty")),
                        value(row.get("shortQty")),
                        value(row.get("suggestQty")),
                        value(row.get("designators")),
                        value(row.get("note")))));
            }
            writeCsv(response, "dev-shortage-" + projectCode, lines);
            return "success";
        } catch (GenericServiceException e) {
            Debug.logError(e, "Could not read the shortage list of " + projectCode, MODULE);
            return "error";
        }
    }

    private static void writeCsv(HttpServletResponse response, String name, List<String> lines) {
        response.setContentType("text/csv; charset=UTF-8");
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
            Debug.logError(e, "Could not write the shortage list [" + name + "]", MODULE);
        }
    }

    private static String error(HttpServletRequest request, String message) {
        List<String> messages = new ArrayList<>();
        messages.add(message);
        request.setAttribute("errorMessageList", messages);
        return "error";
    }

    private static Map<String, Object> parametersOf(HttpServletRequest request) {
        Map<String, Object> parsed = UtilGenerics.cast(request.getAttribute("multiPartMap"));
        if (parsed != null && !parsed.isEmpty()) {
            return parsed;
        }
        return UtilHttp.getParameterMap(request);
    }

    private static byte[] bytesOf(Object value) {
        if (value instanceof java.nio.ByteBuffer) {
            java.nio.ByteBuffer buffer = ((java.nio.ByteBuffer) value).duplicate();
            byte[] data = new byte[buffer.remaining()];
            buffer.get(data);
            return data;
        }
        if (value instanceof byte[]) {
            return (byte[]) value;
        }
        return null;
    }

    private static String text(Object value) {
        if (value == null) {
            return null;
        }
        String text = value.toString().trim();
        return text.isEmpty() ? null : text;
    }

    private static String value(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof java.math.BigDecimal) {
            return ((java.math.BigDecimal) value).stripTrailingZeros().toPlainString();
        }
        if (value instanceof Double || value instanceof Float) {
            return MfgSupport.formatQty(((Number) value).doubleValue());
        }
        return value.toString();
    }
}
