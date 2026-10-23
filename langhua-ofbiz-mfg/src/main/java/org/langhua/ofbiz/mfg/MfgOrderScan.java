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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.ofbiz.base.util.Debug;
import org.apache.ofbiz.base.util.UtilGenerics;

/**
 * Reads a shop order screenshot with a vision model and answers with the fields of the receipt
 * form, so the person only has to check the numbers before the order is booked in.
 *
 * <p>The model is called over an OpenAI compatible chat endpoint configured with the system
 * properties {@code org.langhua.mfg.vision.endpoint}, {@code org.langhua.mfg.vision.model} and
 * {@code org.langhua.mfg.vision.key}. Without them the event answers with a message instead of
 * calling anything, and the person types or pastes the lines as before — recognition is a help,
 * never the only way in.
 */
public final class MfgOrderScan {

    private static final String MODULE = MfgOrderScan.class.getName();

    /** Where the chat completions endpoint lives, e.g. https://host/v1/chat/completions. */
    static final String VISION_ENDPOINT = "org.langhua.mfg.vision.endpoint";
    /** Model name the endpoint knows, e.g. a vision capable model. */
    static final String VISION_MODEL = "org.langhua.mfg.vision.model";
    /** Bearer token of the endpoint. */
    static final String VISION_KEY = "org.langhua.mfg.vision.key";

    /** Largest screenshot accepted, phone screenshots are well below. */
    static final int MAX_IMAGE_BYTES = 8 * 1024 * 1024;

    /** What the model is asked for: one JSON object, nothing else. */
    static final String PROMPT = "This is a screenshot of an online shop order (Taobao / Tmall)."
            + " Read the order number, the shop name, the order date (yyyy-MM-dd), the freight paid"
            + " (0 when it says the freight is included) and the article lines."
            + " Answer with one JSON object only, no prose:"
            + " {\"orderNo\":\"\",\"shop\":\"\",\"date\":\"\",\"freight\":\"\","
            + "\"lines\":\"code,name,quantity,amount\"} where lines has one row per article,"
            + " quantity is the number of pieces (a lot of 100 pieces is 100, not 1) and amount is"
            + " the money really paid for that row without freight. Use a code the shop can"
            + " recognise from the article name.";

    private MfgOrderScan() {
    }

    /**
     * Sends the uploaded screenshot to the vision service and answers with the form fields.
     *
     * @param request the request, holding the multipart form with the field {@code image}
     * @param response the response, receives a small JSON document
     * @return {@code success}, {@code error} when the answer could not be written
     */
    public static String scan(HttpServletRequest request, HttpServletResponse response) {
        Map<String, Object> parameters = parametersOf(request);
        byte[] image = bytesOf(parameters.get("image"));
        if (image == null || image.length == 0) {
            return answer(response, false, "没有收到图片，先选一张订单截图", null);
        }
        if (image.length > MAX_IMAGE_BYTES) {
            return answer(response, false, "图片太大（超过 8 MB），截小一点再试", null);
        }
        String endpoint = text(System.getProperty(VISION_ENDPOINT));
        String model = text(System.getProperty(VISION_MODEL));
        String key = text(System.getProperty(VISION_KEY));
        if (endpoint == null || model == null || key == null) {
            return answer(response, false, "还没有配置识图服务（系统属性 " + VISION_ENDPOINT
                    + " / " + VISION_MODEL + " / " + VISION_KEY + "），请把明细粘进框里", null);
        }
        String contentType = text(parameters.get("_image_contentType"));
        if (contentType == null) {
            contentType = "image/png";
        }
        try {
            String reply = askVision(endpoint, model, key, image, contentType);
            Map<String, String> fields = MfgServices.orderFieldsFrom(reply);
            if (fields == null) {
                return answer(response, false, "识图服务没有返回可用的明细，请手工填", null);
            }
            return answer(response, true, "已识别，核对后再提交", fields);
        } catch (IOException e) {
            Debug.logError(e, "Could not ask the vision service", MODULE);
            return answer(response, false, "识图服务调用失败：" + e.getMessage(), null);
        }
    }

    /**
     * Calls an OpenAI compatible chat endpoint with the screenshot as a data URL.
     *
     * @param endpoint the chat completions URL
     * @param model the model name
     * @param key the bearer token
     * @param image the picture
     * @param contentType its content type
     * @return the answer text of the model
     * @throws IOException when the call fails
     */
    static String askVision(String endpoint, String model, String key, byte[] image, String contentType)
            throws IOException {
        String dataUrl = "data:" + contentType + ";base64,"
                + Base64.getEncoder().encodeToString(image);
        StringBuilder body = new StringBuilder();
        body.append("{\"model\":").append(MfgBomEvents.jsonString(model))
                .append(",\"messages\":[{\"role\":\"user\",\"content\":[")
                .append("{\"type\":\"text\",\"text\":").append(MfgBomEvents.jsonString(PROMPT)).append("},")
                .append("{\"type\":\"image_url\",\"image_url\":{\"url\":")
                .append(MfgBomEvents.jsonString(dataUrl)).append("}}]}]}");
        HttpURLConnection connection = (HttpURLConnection) new URL(endpoint).openConnection();
        try {
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(20000);
            connection.setReadTimeout(60000);
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            connection.setRequestProperty("Authorization", "Bearer " + key);
            OutputStream out = connection.getOutputStream();
            out.write(body.toString().getBytes(StandardCharsets.UTF_8));
            out.flush();
            int status = connection.getResponseCode();
            InputStream stream = status >= 400 ? connection.getErrorStream() : connection.getInputStream();
            String text = stream == null ? "" : readAll(stream);
            if (status >= 400) {
                throw new IOException("HTTP " + status + " " + text);
            }
            return text;
        } finally {
            connection.disconnect();
        }
    }

    private static String readAll(InputStream stream) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] chunk = new byte[8192];
        int read = stream.read(chunk);
        while (read > 0) {
            buffer.write(chunk, 0, read);
            read = stream.read(chunk);
        }
        return new String(buffer.toByteArray(), StandardCharsets.UTF_8);
    }

    /**
     * Writes the answer of the scan: {@code ok}, a message and, when it worked, the form fields.
     *
     * @param response the response
     * @param ok whether the scan worked
     * @param message what to tell the person
     * @param fields the form fields, may be {@code null}
     * @return {@code success}, {@code error} when the answer could not be written
     */
    private static String answer(HttpServletResponse response, boolean ok, String message,
            Map<String, String> fields) {
        StringBuilder json = new StringBuilder();
        json.append("{\"ok\":").append(ok).append(",\"message\":")
                .append(MfgBomEvents.jsonString(message));
        if (fields != null) {
            for (Map.Entry<String, String> entry : fields.entrySet()) {
                json.append(',').append(MfgBomEvents.jsonString(entry.getKey())).append(':')
                        .append(MfgBomEvents.jsonString(entry.getValue()));
            }
        }
        json.append('}');
        try {
            response.setContentType("application/json; charset=UTF-8");
            response.setHeader("Cache-Control", "no-store");
            response.getWriter().write(json.toString());
            response.getWriter().flush();
            return "success";
        } catch (IOException e) {
            Debug.logError(e, "Could not write the answer of the order scan", MODULE);
            return "error";
        }
    }

    private static Map<String, Object> parametersOf(HttpServletRequest request) {
        Map<String, Object> parsed = UtilGenerics.cast(request.getAttribute("multiPartMap"));
        if (parsed != null && !parsed.isEmpty()) {
            return parsed;
        }
        return org.apache.ofbiz.base.util.UtilHttp.getParameterMap(request);
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
