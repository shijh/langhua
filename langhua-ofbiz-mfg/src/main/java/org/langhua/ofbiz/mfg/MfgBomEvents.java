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
 * Web events of the BOM editor. They only translate between the page and the services, so the
 * rules stay in {@link MfgProductServices}.
 *
 * <p>The page keeps the plain form posts as a fallback and posts here instead when the browser can
 * do it without reloading the whole page.
 */
public final class MfgBomEvents {

    private static final String MODULE = MfgBomEvents.class.getName();

    /** The parameters the line editor posts, in the order they are copied into the service context. */
    private static final String[] LINE_PARAMETERS = {
        "productCode", "bomVersionId", "lineSeq", "componentCode", "componentName", "qty",
        "designators", "isKeyPart", "unitPrice", "delete"};

    private MfgBomEvents() {
    }

    /**
     * Saves, appends or deletes one BOM line and answers with a small JSON document
     * ({@code {"ok":true,"message":"已保存 BOM 行 10"}}) instead of a redirect, so the page can
     * refresh just the BOM block.
     *
     * @param request the request, form encoded
     * @param response the response, receives the JSON
     * @return {@code success}, {@code error} when the answer could not be written
     */
    public static String saveLine(HttpServletRequest request, HttpServletResponse response) {
        Map<String, Object> parameters = UtilHttp.getParameterMap(request);
        LocalDispatcher dispatcher = (LocalDispatcher) request.getAttribute("dispatcher");
        GenericValue userLogin = (GenericValue) request.getSession().getAttribute("userLogin");
        boolean removing = "Y".equals(String.valueOf(parameters.get("delete")));
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
                Map<String, Object> result = dispatcher.runSync("mfgBomItemSave", context, -1, true);
                ok = !ServiceUtil.isError(result);
                if (ok) {
                    message = (removing ? "已删除 BOM 行 " : "已保存 BOM 行 ")
                            + String.valueOf(result.get("lineSeq"));
                } else {
                    message = ServiceUtil.getErrorMessage(result);
                }
            } catch (GenericServiceException e) {
                Debug.logError(e, "Could not save a BOM line", MODULE);
                ok = false;
                message = "保存失败：" + e.getMessage();
            }
        }
        try {
            response.setContentType("application/json; charset=UTF-8");
            response.setHeader("Cache-Control", "no-store");
            response.getWriter().write("{\"ok\":" + ok + ",\"message\":" + jsonString(message) + "}");
            response.getWriter().flush();
        } catch (IOException e) {
            Debug.logError(e, "Could not write the answer of the BOM line editor", MODULE);
            return "error";
        }
        return "success";
    }

    /**
     * Quotes one string for a JSON document.
     *
     * @param value the text, may be {@code null}
     * @return the quoted text, {@code null} for a null value
     */
    public static String jsonString(String value) {
        if (value == null) {
            return "null";
        }
        StringBuilder out = new StringBuilder(value.length() + 2);
        out.append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
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
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", Integer.valueOf(c)));
                    } else {
                        out.append(c);
                    }
            }
        }
        return out.append('"').toString();
    }
}
