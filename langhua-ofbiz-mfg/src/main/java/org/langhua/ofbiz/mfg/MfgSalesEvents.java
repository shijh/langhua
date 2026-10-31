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
 * Web events of the sales order page. They only translate between the page and the services, so the
 * rules stay in {@link MfgSalesOrderServices}.
 *
 * <p>The list unfolds its lines in place; correcting the quantity of a line posts here and the
 * answer is a small JSON document, so the page can refresh the block instead of reloading.
 */
public final class MfgSalesEvents {

    private static final String MODULE = MfgSalesEvents.class.getName();

    /** The parameters the line editor posts. */
    private static final String[] LINE_PARAMETERS = {"salesOrderId", "lineSeq", "qty"};

    private MfgSalesEvents() {
    }

    /**
     * Changes the quantity of one order line and answers with a small JSON document
     * ({@code {"ok":true,"message":"已改第 1 行为 5"}}).
     *
     * @param request the request, form encoded
     * @param response the response, receives the JSON
     * @return {@code success}, {@code error} when the answer could not be written
     */
    public static String lineQtyJson(HttpServletRequest request, HttpServletResponse response) {
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
                Map<String, Object> result = dispatcher.runSync("mfgSalesOrderLineQty", context, -1, true);
                ok = !ServiceUtil.isError(result);
                if (ok) {
                    message = "订单 " + String.valueOf(result.get("salesOrderId")) + " 第 "
                            + String.valueOf(result.get("lineSeq")) + " 行改为 "
                            + quantity(result.get("qty"));
                } else {
                    message = ServiceUtil.getErrorMessage(result);
                }
            } catch (GenericServiceException e) {
                Debug.logError(e, "Could not change a sales order line", MODULE);
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
            Debug.logError(e, "Could not write the answer of the sales order line editor", MODULE);
            return "error";
        }
        return "success";
    }

    /**
     * Writes a quantity the way the page does: {@code 7}, not {@code 7.0}.
     *
     * @param value the quantity of the result, may be {@code null}
     * @return the text of the quantity
     */
    private static String quantity(Object value) {
        if (!(value instanceof Number)) {
            return String.valueOf(value);
        }
        java.text.DecimalFormat format = new java.text.DecimalFormat("0.###",
                java.text.DecimalFormatSymbols.getInstance(java.util.Locale.ROOT));
        return format.format(((Number) value).doubleValue());
    }
}
