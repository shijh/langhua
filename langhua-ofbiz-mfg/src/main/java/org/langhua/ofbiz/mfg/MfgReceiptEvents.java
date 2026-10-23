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
 * so the rules stay in {@link MfgServices}.
 *
 * <p>The page keeps the plain form post as a fallback and posts here instead when the browser can
 * save without reloading the whole page.
 */
public final class MfgReceiptEvents {

    private static final String MODULE = MfgReceiptEvents.class.getName();

    /** The parameters the receipt head form posts. */
    private static final String[] RECEIPT_PARAMETERS = {
        "receiptId", "supplierName", "supplierOrderNo", "receiptDate", "freightAmount", "note"};

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
}
