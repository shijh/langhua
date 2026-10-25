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
import java.io.PrintWriter;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
 * Web event of the BOM import: reads the pasted text or the uploaded file of the multipart form
 * and hands it to {@code mfgBomImport}, so a Fritzing export can be dropped in as a file instead
 * of being copied into the text box.
 *
 * <p>The event only turns the request into the context of the service; the parsing, the format
 * detection and the validation stay in {@link MfgServices#bomImport}.
 */
public final class MfgImportEvents {

    private static final String MODULE = MfgImportEvents.class.getName();

    /** Largest BOM file accepted from an upload, a few thousand lines are far below. */
    static final int MAX_BOM_BYTES = 2 * 1024 * 1024;

    /** Largest order export accepted: ten pages of Taobao orders are a few hundred kilobytes. */
    static final int MAX_ORDER_BYTES = 8 * 1024 * 1024;

    private MfgImportEvents() {
    }

    /**
     * Imports the BOM of the multipart form: the file wins over the text box when both are filled.
     *
     * @param request the request, already holding the parsed multipart form
     * @param response the response
     * @return {@code success} to go back to the product page, {@code error} to render it with a message
     */
    public static String bomImport(HttpServletRequest request, HttpServletResponse response) {
        Map<String, Object> parameters = parametersOf(request);
        String productCode = text(parameters.get("productCode"));
        String csv = text(parameters.get("csv"));
        byte[] data = bytesOf(parameters.get("file"));
        if (data != null && data.length > MAX_BOM_BYTES) {
            return error(request, "BOM 文件太大（超过 2 MB），请先精简后再导入");
        }
        if (data != null && data.length > 0) {
            csv = decode(data);
        }
        if (csv == null) {
            return error(request, "请粘贴 BOM 文本，或选择一个 CSV / TXT 文件");
        }
        LocalDispatcher dispatcher = (LocalDispatcher) request.getAttribute("dispatcher");
        if (dispatcher == null) {
            return error(request, "服务不可用，请稍后重试或重启实例");
        }
        GenericValue userLogin = (GenericValue) request.getSession().getAttribute("userLogin");
        Map<String, Object> context = new HashMap<>();
        context.put("userLogin", userLogin);
        context.put("productCode", productCode);
        context.put("csv", csv);
        context.put("format", text(parameters.get("format")));
        context.put("replace", text(parameters.get("replace")));
        context.put("bomVersionId", text(parameters.get("bomVersionId")));
        try {
            Map<String, Object> result = dispatcher.runSync("mfgBomImport", context, -1, true);
            if (ServiceUtil.isError(result)) {
                return error(request, ServiceUtil.getErrorMessage(result));
            }
            request.setAttribute("productCode", productCode);
            request.setAttribute("importedCount", String.valueOf(result.get("importedCount")));
            request.setAttribute("bomFormat", String.valueOf(result.get("bomFormat")));
            Debug.logInfo("Imported " + result.get("importedCount") + " BOM lines into " + productCode
                    + " (" + result.get("bomFormat") + ")", MODULE);
            return "success";
        } catch (GenericServiceException e) {
            Debug.logError(e, "Could not run mfgBomImport", MODULE);
            return error(request, "导入失败：" + e.getMessage());
        }
    }

    /**
     * Imports the workbook Taobao lets a buyer download (已买到的宝贝 → 导出订单): one receipt per
     * order number, one receipt line per article.
     *
     * <p>The order number is the key {@code mfgReceiptFromOrder} already uses, so a second import of
     * a longer export overwrites the lines of the orders it has seen before instead of buying the
     * same parts twice. The arrival day is the day the order was placed - the export prints no other
     * date, and for filling in the past that is the closest there is. Orders the shop closed (交易关闭 /
     * 超时未付款) and phone top ups are left out and listed back, so the person sees why.
     *
     * @param request the request holding the multipart form with the field {@code file}
     * @param response the response, receives a small JSON document (or a redirect when the browser
     *        posts the form without the script)
     * @return {@code json}, the answer is already written
     */
    public static String orderExcel(HttpServletRequest request, HttpServletResponse response) {
        Map<String, Object> parameters = parametersOf(request);
        byte[] data = bytesOf(parameters.get("file"));
        boolean ajax = text(parameters.get("ajax")) != null;
        if (data == null || data.length == 0) {
            return excelAnswer(response, ajax, "没有收到文件，先选淘宝导出的「订单数据.xlsx」", null, null, 0, 0, 0, 0);
        }
        if (data.length > MAX_ORDER_BYTES) {
            return excelAnswer(response, ajax, "文件太大（超过 8 MB），一次导出 1-10 页就够", null, null, 0, 0, 0, 0);
        }
        List<Map<String, String>> orders = MfgOrderExcel.ordersOf(data);
        if (orders.isEmpty()) {
            return excelAnswer(response, ajax,
                    "这个文件里没读到订单行：要淘宝后台导出的「订单数据.xlsx」那一份",
                    null, null, 0, 0, 0, 0);
        }
        LocalDispatcher dispatcher = (LocalDispatcher) request.getAttribute("dispatcher");
        if (dispatcher == null) {
            return excelAnswer(response, ajax, "服务不可用，请稍后重试或重启实例", null, null, 0, 0, 0, 0);
        }
        GenericValue userLogin = (GenericValue) request.getSession().getAttribute("userLogin");
        int imported = 0;
        int lines = 0;
        int skipped = 0;
        int failed = 0;
        Map<String, Integer> whySkipped = new HashMap<>();
        List<String[]> notes = new ArrayList<>();
        Set<String> done = new HashSet<>();
        for (Map<String, String> order : orders) {
            String orderNo = order.get("orderNo");
            String skip = order.get("skip");
            if (skip != null) {
                skipped++;
                whySkipped.merge(skip, 1, Integer::sum);
                notes.add(new String[] {orderNo, skip});
                continue;
            }
            Map<String, Object> context = new HashMap<>();
            context.put("userLogin", userLogin);
            context.put("supplierOrderNo", orderNo);
            context.put("supplierName", order.get("shop"));
            context.put("receiptDate", order.get("orderDate"));
            context.put("freightAmount", order.get("freight"));
            context.put("discountAmount", order.get("discount"));
            context.put("lines", order.get("lines"));
            context.put("overwrite", "Y");
            try {
                Map<String, Object> result = dispatcher.runSync("mfgReceiptFromOrder", context, -1, true);
                if (ServiceUtil.isError(result)) {
                    failed++;
                    notes.add(new String[] {orderNo, ServiceUtil.getErrorMessage(result)});
                    continue;
                }
                imported++;
                done.add(orderNo);
                lines += number(result.get("importedCount")) + number(result.get("updatedCount"));
            } catch (GenericServiceException e) {
                Debug.logError(e, "Could not import the shop order " + orderNo, MODULE);
                failed++;
                notes.add(new String[] {orderNo, "导入失败：" + e.getMessage()});
            }
        }
        StringBuilder message = new StringBuilder();
        message.append("导入 ").append(imported).append(" 张单，写入 ").append(lines).append(" 行");
        if (skipped > 0) {
            message.append("；跳过 ").append(skipped).append(" 张（");
            boolean first = true;
            for (Map.Entry<String, Integer> entry : whySkipped.entrySet()) {
                message.append(first ? "" : "，").append(entry.getKey()).append(" ").append(entry.getValue());
                first = false;
            }
            message.append("）");
        }
        if (failed > 0) {
            message.append("；").append(failed).append(" 张没导进去，见下面的原因");
        }
        Debug.logInfo("Imported the order export: " + message, MODULE);
        return excelAnswer(response, ajax, message.toString(), notes, done, imported, lines, skipped, failed);
    }

    /**
     * Writes the answer of the workbook import: JSON for the page script, a redirect with the counts
     * for a browser that posts the form without it.
     *
     * @param response the response
     * @param ajax whether the page script asked
     * @param message what happened, in words
     * @param notes one {@code [orderNo, why]} pair per order that was skipped or failed, may be null
     * @param done the order numbers that were written, may be null
     * @param imported how many orders were written
     * @param lines how many receipt lines were added or changed
     * @param skipped how many orders were left out on purpose
     * @param failed how many orders could not be written
     * @return {@code json}
     */
    private static String excelAnswer(HttpServletResponse response, boolean ajax, String message,
            List<String[]> notes, Set<String> done, int imported, int lines, int skipped, int failed) {
        boolean ok = imported > 0 || (failed == 0 && skipped > 0);
        if (!ajax) {
            try {
                response.sendRedirect("receipts?excelImported=" + imported + "&excelLines=" + lines
                        + "&excelSkipped=" + skipped + "&excelFailed=" + failed);
            } catch (IOException e) {
                Debug.logError(e, "Could not redirect after the order import", MODULE);
            }
            return "json";
        }
        StringBuilder json = new StringBuilder();
        json.append("{\"ok\":").append(ok);
        json.append(",\"message\":\"").append(escape(message)).append('"');
        json.append(",\"imported\":").append(imported);
        json.append(",\"lines\":").append(lines);
        json.append(",\"skipped\":").append(skipped);
        json.append(",\"failed\":").append(failed);
        json.append(",\"notes\":[");
        if (notes != null) {
            for (int i = 0; i < notes.size(); i++) {
                String[] note = notes.get(i);
                json.append(i == 0 ? "" : ",");
                json.append("{\"orderNo\":\"").append(escape(note[0])).append("\",\"message\":\"")
                        .append(escape(note[1])).append("\"}");
            }
        }
        json.append("]}");
        response.setContentType("application/json; charset=UTF-8");
        try {
            PrintWriter out = response.getWriter();
            out.write(json.toString());
            out.flush();
        } catch (IOException e) {
            Debug.logError(e, "Could not write the answer of the order import", MODULE);
            return "error";
        }
        return "json";
    }

    /** Escapes a text for a JSON string; enough for the messages this event writes. */
    private static String escape(String text) {
        if (text == null) {
            return "";
        }
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            switch (ch) {
                case '"':
                    out.append("\\\"");
                    break;
                case '\\':
                    out.append("\\\\");
                    break;
                case '\n':
                    out.append("\\n");
                    break;
                case '\r':
                    out.append("\\r");
                    break;
                case '\t':
                    out.append("\\t");
                    break;
                default:
                    if (ch < ' ') {
                        out.append(String.format("\\u%04x", (int) ch));
                    } else {
                        out.append(ch);
                    }
            }
        }
        return out.toString();
    }

    /**
     * Decodes the bytes of an uploaded BOM file. A spreadsheet saved on a Chinese Windows writes
     * GBK, everything else (Fritzing, the templates of this component) writes UTF-8.
     *
     * @param data the file content
     * @return the text, never {@code null}
     */
    static String decode(byte[] data) {
        String utf8 = new String(data, StandardCharsets.UTF_8);
        if (utf8.indexOf('\uFFFD') < 0) {
            return utf8;
        }
        try {
            String gbk = new String(data, Charset.forName("GBK"));
            return gbk.indexOf('\uFFFD') < 0 ? gbk : utf8;
        } catch (RuntimeException e) {
            return utf8;
        }
    }

    /**
     * Puts a message on the request so the product page renders it above the form.
     *
     * @param request the request
     * @param message the message to show
     * @return always {@code error}
     */
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
        if (value instanceof ByteBuffer) {
            ByteBuffer buffer = ((ByteBuffer) value).duplicate();
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

    /** A count the service handed back as a string, {@code 0} when it is missing. */
    private static int number(Object value) {
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        if (value == null) {
            return 0;
        }
        try {
            return Integer.parseInt(value.toString().trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
