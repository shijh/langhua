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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.ofbiz.base.util.Debug;
import org.apache.ofbiz.base.util.UtilHttp;
import org.apache.ofbiz.entity.GenericValue;
import org.apache.ofbiz.service.GenericServiceException;
import org.apache.ofbiz.service.LocalDispatcher;
import org.apache.ofbiz.service.ServiceUtil;

/**
 * Web events of the purchase receipt page. They only translate between the page and the services,
 * so the rules stay in {@link MfgPurchaseServices}.
 *
 * <p>The page keeps the plain form post as a fallback and posts here instead when the browser can
 * save without reloading the whole page.
 */
public final class MfgReceiptEvents {

    private static final String MODULE = MfgReceiptEvents.class.getName();

    /** The parameters the receipt head form posts. */
    private static final String[] RECEIPT_PARAMETERS = {
        "receiptId", "supplierName", "supplierOrderNo", "receiptDate", "freightAmount", "note"};

    /** The parameters the line editor posts. */
    private static final String[] LINE_PARAMETERS = {"receiptId", "lineSeq", "qty", "unitPrice"};

    private MfgReceiptEvents() {
    }

    /**
     * Saves the head of a receipt and answers with a small JSON document
     * ({@code {"ok":true,"message":"已保存到货单 PR-261023-49"}}) instead of a redirect, so the page
     * can show the result without loading everything again.
     *
     * @param request the request, form encoded
     * @param response the response, receives the JSON
     * @return {@code success}, {@code error} when the answer could not be written
     */
    public static String saveJson(HttpServletRequest request, HttpServletResponse response) {
        Map<String, Object> parameters = UtilHttp.getParameterMap(request);
        LocalDispatcher dispatcher = (LocalDispatcher) request.getAttribute("dispatcher");
        GenericValue userLogin = (GenericValue) request.getSession().getAttribute("userLogin");
        boolean ok;
        String message;
        if (dispatcher == null) {
            ok = false;
            message = "服务不可用，请刷新页面后重试";
        } else {
            Map<String, Object> context = new HashMap<>();
            context.put("userLogin", userLogin);
            for (String name : RECEIPT_PARAMETERS) {
                if (parameters.containsKey(name)) {
                    context.put(name, parameters.get(name));
                }
            }
            try {
                Map<String, Object> result = dispatcher.runSync("mfgPurchaseReceiptSave", context, -1, true);
                ok = !ServiceUtil.isError(result);
                if (ok) {
                    message = "已保存到货单 " + String.valueOf(result.get("receiptId"));
                    Double freight = (Double) result.get("freightAmount");
                    if (freight != null) {
                        message += "（运费 " + String.format(java.util.Locale.ROOT, "%.2f", freight) + "）";
                    }
                } else {
                    message = ServiceUtil.getErrorMessage(result);
                }
            } catch (GenericServiceException e) {
                Debug.logError(e, "Could not save a purchase receipt", MODULE);
                ok = false;
                message = "保存失败：" + e.getMessage();
            }
        }
        try {
            response.setContentType("application/json; charset=UTF-8");
            response.setHeader("Cache-Control", "no-store");
            response.getWriter().write("{\"ok\":" + ok + ",\"message\":"
                    + MfgBomEvents.jsonString(message) + "}");
            response.getWriter().flush();
        } catch (IOException e) {
            Debug.logError(e, "Could not write the answer of the receipt form", MODULE);
            return "error";
        }
        return "success";
    }

    /**
     * Answers which parts of one shop order are already booked in, so the page can ask before the
     * same order is imported a second time.
     *
     * @param request the request, with {@code supplierOrderNo}
     * @param response the response, receives the JSON
     * @return {@code success}, {@code error} when the answer could not be written
     */
    public static String orderPeekJson(HttpServletRequest request, HttpServletResponse response) {
        Map<String, Object> parameters = UtilHttp.getParameterMap(request);
        LocalDispatcher dispatcher = (LocalDispatcher) request.getAttribute("dispatcher");
        StringBuilder json = new StringBuilder();
        try {
            Map<String, Object> result = dispatcher == null ? null
                    : dispatcher.runSync("mfgReceiptLinesOfOrder", parameters, -1, true);
            if (result == null || ServiceUtil.isError(result)) {
                json.append("{\"ok\":false,\"lines\":[]}");
            } else {
                json.append("{\"ok\":true,\"receiptId\":")
                        .append(MfgBomEvents.jsonString((String) result.get("receiptId")))
                        .append(",\"lines\":[");
                List<?> lines = (List<?>) result.get("lines");
                if (lines != null) {
                    boolean first = true;
                    for (Object row : lines) {
                        Map<String, Object> line = org.apache.ofbiz.base.util.UtilGenerics.cast(row);
                        if (!first) {
                            json.append(',');
                        }
                        first = false;
                        json.append("{\"componentCode\":")
                                .append(MfgBomEvents.jsonString((String) line.get("componentCode")))
                                .append(",\"qty\":").append(line.get("qty"))
                                .append(",\"goodsAmount\":").append(line.get("goodsAmount"))
                                .append('}');
                    }
                }
                json.append("]}");
            }
            response.setContentType("application/json; charset=UTF-8");
            response.setHeader("Cache-Control", "no-store");
            response.getWriter().write(json.toString());
            response.getWriter().flush();
        } catch (IOException e) {
            Debug.logError(e, "Could not write the peek answer", MODULE);
            return "error";
        } catch (GenericServiceException e) {
            Debug.logError(e, "Could not look up the order", MODULE);
            return "error";
        }
        return "success";
    }

    /**
     * Saves the quantity and the unit price of one receipt line and answers with a small JSON
     * document, so the list can update the line and its totals without loading the page again.
     *
     * @param request the request, form encoded
     * @param response the response, receives the JSON
     * @return {@code success}, {@code error} when the answer could not be written
     */
    public static String saveLineJson(HttpServletRequest request, HttpServletResponse response) {
        Map<String, Object> parameters = UtilHttp.getParameterMap(request);
        LocalDispatcher dispatcher = (LocalDispatcher) request.getAttribute("dispatcher");
        GenericValue userLogin = (GenericValue) request.getSession().getAttribute("userLogin");
        boolean ok;
        String message;
        if (dispatcher == null) {
            ok = false;
            message = "服务不可用，请刷新页面后重试";
        } else {
            Map<String, Object> context = new HashMap<>();
            context.put("userLogin", userLogin);
            for (String name : LINE_PARAMETERS) {
                if (parameters.containsKey(name)) {
                    context.put(name, parameters.get(name));
                }
            }
            try {
                Map<String, Object> result = dispatcher.runSync("mfgPurchaseReceiptLineSave", context, -1, true);
                ok = !ServiceUtil.isError(result);
                if (ok) {
                    message = "已保存第 " + String.valueOf(result.get("lineSeq")) + " 行，货款 "
                            + String.format(java.util.Locale.ROOT, "%.2f",
                                    Double.parseDouble(String.valueOf(result.get("goodsAmount"))));
                } else {
                    message = ServiceUtil.getErrorMessage(result);
                }
            } catch (GenericServiceException e) {
                Debug.logError(e, "Could not save a receipt line", MODULE);
                ok = false;
                message = "保存失败：" + e.getMessage();
            }
        }
        try {
            response.setContentType("application/json; charset=UTF-8");
            response.setHeader("Cache-Control", "no-store");
            response.getWriter().write("{\"ok\":" + ok + ",\"message\":"
                    + MfgBomEvents.jsonString(message) + "}");
            response.getWriter().flush();
        } catch (IOException e) {
            Debug.logError(e, "Could not write the answer of the receipt line editor", MODULE);
            return "error";
        }
        return "success";
    }

    /**
     * Books several orders of one screenshot in at once: the page posts {@code count} and, for every
     * order, {@code o<number>.orderNo}, {@code .shop}, {@code .freight}, {@code .lines} and
     * {@code .refunded}. Each order runs in its own transaction, so one bad order does not take the
     * others down with it.
     *
     * @param request the request, form encoded
     * @param response the response, receives the JSON with one result per order
     * @return {@code success}, {@code error} when the answer could not be written
     */
    public static String ordersJson(HttpServletRequest request, HttpServletResponse response) {
        Map<String, Object> parameters = UtilHttp.getParameterMap(request);
        LocalDispatcher dispatcher = (LocalDispatcher) request.getAttribute("dispatcher");
        GenericValue userLogin = (GenericValue) request.getSession().getAttribute("userLogin");
        int count = 0;
        try {
            count = Integer.parseInt(String.valueOf(parameters.get("count")));
        } catch (NumberFormatException e) {
            count = 0;
        }
        StringBuilder json = new StringBuilder();
        if (dispatcher == null || count <= 0) {
            json.append("{\"ok\":false,\"message\":")
                    .append(MfgBomEvents.jsonString("没有要导入的单据，刷新页面后再试"))
                    .append(",\"results\":[]}");
        } else {
            int imported = 0;
            StringBuilder results = new StringBuilder();
            boolean first = true;
            for (int i = 0; i < count; i++) {
                Map<String, Object> context = new HashMap<>();
                context.put("userLogin", userLogin);
                // the person looked at the check table and pressed import: what is read now is the
                // good version, so a part that is already on the receipt takes these numbers instead
                // of being skipped (a corrected read used to leave the old wrong amount in place)
                context.put("overwrite", "Y");
                String orderNo = value(parameters, i, "orderNo");
                context.put("supplierOrderNo", orderNo);
                context.put("supplierName", value(parameters, i, "shop"));
                context.put("freightAmount", value(parameters, i, "freight"));
                // a red packet or coupon comes off the whole order: 实付 = goods + freight - discount
                context.put("discountAmount", value(parameters, i, "discount"));
                context.put("lines", value(parameters, i, "lines"));
                context.put("refunded", value(parameters, i, "refunded"));
                boolean ok;
                String message;
                long newLines = 0L;
                try {
                    Map<String, Object> result = dispatcher.runSync("mfgReceiptFromOrder", context, -1, true);
                    ok = !ServiceUtil.isError(result);
                    if (ok) {
                        long added = countOf(result.get("importedCount"));
                        long changed = countOf(result.get("updatedCount"));
                        long skippedLines = countOf(result.get("skippedCount"));
                        long leftOver = countOf(result.get("leftOverCount"));
                        String leftOverLines = String.valueOf(result.get("leftOverLines"));
                        newLines = added + changed;
                        String receipt = String.valueOf(result.get("receiptId"));
                        if (newLines > 0) {
                            imported++;
                            message = "已入库 " + receipt + "（新增 " + added + " 行，覆盖 " + changed
                                    + " 行，无变化 " + skippedLines + " 行）";
                        } else {
                            // nothing differs from what the receipt already holds
                            message = receipt + " 上的明细已经一样，没有改动（" + skippedLines + " 行无变化）";
                        }
                        if (leftOver > 0 && !"null".equals(leftOverLines)) {
                            // a line the receipt still holds but this answer no longer mentions: it is
                            // not deleted (that would move stock), so the person has to see it
                            message += "；这张单上还有 " + leftOver + " 行不在这张截图的明细里："
                                    + leftOverLines + "，请核对是否要手工删掉";
                        }
                    } else {
                        message = ServiceUtil.getErrorMessage(result);
                    }
                } catch (GenericServiceException e) {
                    Debug.logError(e, "Could not book the order [" + orderNo + "]", MODULE);
                    ok = false;
                    message = "导入失败：" + e.getMessage();
                }
                if (!first) {
                    results.append(',');
                }
                first = false;
                results.append("{\"orderNo\":").append(MfgBomEvents.jsonString(orderNo))
                        .append(",\"ok\":").append(ok)
                        .append(",\"new\":").append(newLines)
                        .append(",\"message\":").append(MfgBomEvents.jsonString(message)).append('}');
            }
            json.append("{\"ok\":true,\"imported\":").append(imported).append(",\"results\":[")
                    .append(results).append("]}");
        }
        try {
            response.setContentType("application/json; charset=UTF-8");
            response.setHeader("Cache-Control", "no-store");
            response.getWriter().write(json.toString());
            response.getWriter().flush();
        } catch (IOException e) {
            Debug.logError(e, "Could not write the answer of the order list import", MODULE);
            return "error";
        }
        return "success";
    }

    /**
     * Reads a count the service put in its result. The services answer with text, so a plain cast
     * would blow up.
     *
     * @param value the value of the result field
     * @return the number, 0 when it is not a number
     */
    private static long countOf(Object value) {
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    /**
     * The text of one field of one order of the batch, empty when the page did not send it.
     *
     * @param parameters the request parameters
     * @param index the order number inside the batch
     * @param field the field name: orderNo, shop, freight, lines or refunded
     * @return the value as text, {@code null} when it is empty
     */
    private static String value(Map<String, Object> parameters, int index, String field) {
        Object value = parameters.get("o" + index + "." + field);
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value);
        if (text.trim().isEmpty()) {
            return null;
        }
        // the lines keep their leading tab: a row whose first cell is empty is a shop that prints no
        // article code, and trimming the text took the tab of that empty cell with it, so the row came
        // back with three cells and the order was refused with "没有解析出明细" (Step 163). The other
        // fields are one value each (an order number, a shop, an amount) and are trimmed as before
        return "lines".equals(field) ? text : text.trim();
    }
}
