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

import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
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
}
