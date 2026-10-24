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
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.ofbiz.base.util.Debug;
import org.apache.ofbiz.base.util.UtilGenerics;
import org.apache.ofbiz.entity.Delegator;
import org.apache.ofbiz.service.LocalDispatcher;

import static org.langhua.ofbiz.mfg.MfgServices.isRefunded;
import static org.langhua.ofbiz.mfg.MfgServices.rememberRefundedOrder;

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

    /** Answer code of the events when the vision service is not set up yet. */
    static final String UNCONFIGURED = "UNCONFIGURED";

    /** A 1x1 png, used by the connection test so a vision endpoint has something to look at. */
    private static final String TINY_PNG =
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==";

    /** What the model is asked for: one JSON object, nothing else. */
    static final String PROMPT = "This is a screenshot of an online shop order (Taobao / Tmall). It may"
            + " show ONE order (the order detail page) or a LIST of several orders (the order list of"
            + " the shop), each with its own order number, shop, amounts and articles."
            + " Read, for every order: the order number, the shop name, the order date the page shows"
            + " (yyyy-MM-dd), the money really paid for that order (paidTotal), the freight (shipping"
            + " fee) and the article lines of THAT order. Copy the numbers, do not invent them:"
            + " \"含运费: ￥2.00\" means the freight is 2.00, a freight of 0 is only right when the"
            + " page says 免运费 or 包邮; when a row of a list only shows 合计, that is the paid total"
            + " of that order."
            + " When the page shows 退款成功 / 已退款 / 退款中 / 交易关闭 / 已取消 (refunded or"
            + " closed), put Y in refunded, otherwise N: such an order did not stay in the shop and"
            + " must not be booked into stock."
            + " Answer with one JSON object only, no prose:"
            + " {\"orders\":[{\"orderNo\":\"\",\"shop\":\"\",\"orderDate\":\"\",\"paidTotal\":\"\","
            + "\"freight\":\"\",\"refunded\":\"\",\"lines\":\"code,name,quantity,amount\"}]}"
            + " with one element per order, in the order the page shows them; lines has one row per"
            + " article of that order (x2 in a list means quantity 2, a lot of 100 pieces is 100) and"
            + " amount is the goods money of that row without freight: when 实付款 is 3.50 and 含运费"
            + " is 2.00, the row amount is 3.50 minus 2.00. When an order prints no article code, put"
            + " the model number of the article (for example 5.5V1.5F C型 21*7.5) in the code column;"
            + " never leave it empty.";

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
            return answer(response, false, "没有收到图片，先选一张订单截图", null, null);
        }
        if (image.length > MAX_IMAGE_BYTES) {
            return answer(response, false, "图片太大（超过 8 MB），截小一点再试", null, null);
        }
        String endpoint = text(MfgServices.setting(delegatorOf(request), VISION_ENDPOINT));
        String model = text(MfgServices.setting(delegatorOf(request), VISION_MODEL));
        String key = text(MfgServices.setting(delegatorOf(request), VISION_KEY));
        if (endpoint == null || model == null || key == null) {
            return answer(response, false, missingSetting(), null, null, UNCONFIGURED);
        }
        endpoint = MfgServices.normalizeVisionEndpoint(endpoint);
        String contentType = text(parameters.get("_image_contentType"));
        if (contentType == null) {
            contentType = "image/png";
        }
        try {
            String reply = askVision(endpoint, model, key, image, contentType);
            List<Map<String, String>> orders = MfgServices.orderListFrom(reply);
            if (orders.isEmpty()) {
                // quoting the answer tells apart "not an order" from "the model cannot see images"
                Debug.logInfo("The vision answer carried no lines: " + reply, MODULE);
                return answer(response, false, "识图服务返回的内容里没有订单明细。模型的原话："
                        + MfgServices.visionReplyExcerpt(reply, 160)
                        + "。换一张订单/商品明细的截图再试，也可以手工填", null, null);
            }
            int refundedCount = 0;
            for (Map<String, String> order : orders) {
                // a package description as the part number does not fit the code field: short it here
                // so the page already shows the code that will be stored
                order.put("lines", MfgServices.fitOrderLines(order.get("lines")));
                if (isRefunded(order)) {
                    // the parts never stayed in the shop: the number goes on the list, so even a hand
                    // typed import of it is refused later
                    recordRefund(delegatorOf(request), order);
                    order.put("note", "已退款/关闭，不入库");
                    refundedCount++;
                } else {
                    // freight is what the page says, but goods plus freight must come to what was paid
                    String totals = MfgServices.checkOrderTotals(order);
                    if (totals != null) {
                        order.put("note", totals);
                    }
                }
            }
            if (orders.size() == 1) {
                Map<String, String> one = orders.get(0);
                String message;
                if (isRefunded(one)) {
                    message = "已识别。这张单显示已退款/已关闭，按规矩不入库，已经记进"
                            + "「退款 / 关闭的单」；货确实到了就去那儿撤掉标记";
                } else {
                    message = one.get("note") == null ? "已识别，核对后再提交" : "已识别。" + one.get("note");
                }
                return answer(response, true, message, one, null);
            }
            String message = "识别到 " + orders.size() + " 张单";
            if (refundedCount > 0) {
                message += "（其中 " + refundedCount + " 张已退款，不会入库）";
            }
            message += "，核对后点“导入这些单”";
            return answer(response, true, message, null, orders);
        } catch (IOException e) {
            Debug.logError(e, "Could not ask the vision service", MODULE);
            return answer(response, false, "识图服务调用失败：" + e.getMessage(), null, null);
        }
    }

    /**
     * Tries the configured endpoint with a one pixel picture, so a wrong address, key or model is
     * found on the settings page instead of in the middle of booking an order in.
     *
     * @param request the request
     * @param response the response, receives a small JSON document
     * @return {@code success}, {@code error} when the answer could not be written
     */
    public static String test(HttpServletRequest request, HttpServletResponse response) {
        Map<String, Object> parameters = parametersOf(request);
        Delegator delegator = delegatorOf(request);
        // what is on the form wins: the person has just typed it and should not have to save
        // before the test, which used to answer "not configured" to a filled in form
        String endpoint = firstOf(text(parameters.get("visionEndpoint")),
                text(MfgServices.setting(delegator, VISION_ENDPOINT)));
        String model = firstOf(text(parameters.get("visionModel")),
                text(MfgServices.setting(delegator, VISION_MODEL)));
        String key = firstOf(text(parameters.get("visionKey")),
                text(MfgServices.setting(delegator, VISION_KEY)));
        if (endpoint == null || model == null || key == null) {
            return answer(response, false, testMissing(endpoint, model, key), null, null, UNCONFIGURED);
        }
        try {
            String reply = askVision(MfgServices.normalizeVisionEndpoint(endpoint), model, key,
                    Base64.getDecoder().decode(TINY_PNG), "image/png");
            String content = MfgServices.jsonStringField(reply, "content");
            if (content == null) {
                content = reply;
            }
            if (content.length() > 200) {
                content = content.substring(0, 200) + "…";
            }
            String kept = saveTestedSettings(request, endpoint, model, key);
            return answer(response, true, "连接成功" + kept + "，模型回答：" + content, null, null);
        } catch (IOException e) {
            Debug.logError(e, "Could not test the vision service", MODULE);
            return answer(response, false, "识图服务调用失败：" + e.getMessage(), null, null);
        }
    }

    /**
     * Keeps the settings that have just been proven to work, so "test" doubles as "save" for a
     * person who filled the form in and pressed the test button.
     *
     * @param request the request, its dispatcher and login are used
     * @param endpoint the endpoint that answered
     * @param model the model that answered
     * @param key the key that answered
     * @return the text to add to the answer, empty when nothing had to be stored
     */
    private static String saveTestedSettings(HttpServletRequest request, String endpoint, String model,
            String key) {
        LocalDispatcher dispatcher = (LocalDispatcher) request.getAttribute("dispatcher");
        if (dispatcher == null) {
            return "";
        }
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("userLogin", request.getSession().getAttribute("userLogin"));
        context.put("visionEndpoint", endpoint);
        context.put("visionModel", model);
        context.put("visionKey", key);
        try {
            Map<String, Object> result = dispatcher.runSync("mfgSettingsSave", context, -1, true);
            return org.apache.ofbiz.service.ServiceUtil.isError(result) ? "" : "，设置已保存";
        } catch (Exception e) {
            Debug.logError(e, "Could not keep the tested vision settings", MODULE);
            return "";
        }
    }

    /**
     * Names the field a person still has to fill before the test can run.
     *
     * @param endpoint the endpoint on the form or in the settings
     * @param model the model on the form or in the settings
     * @param key the key on the form or in the settings
     * @return the message shown under the test button
     */
    static String testMissing(String endpoint, String model, String key) {
        StringBuilder missing = new StringBuilder();
        if (endpoint == null) {
            missing.append("接口地址");
        }
        if (model == null) {
            missing.append(missing.length() == 0 ? "" : "、").append("模型名");
        }
        if (key == null) {
            missing.append(missing.length() == 0 ? "" : "、").append("密钥");
        }
        return "还差「" + missing + "」没填，填完再点一次「测试连接」。"
                + "密钥是识图服务的 API Key（在服务商后台生成），只写库、不回显。";
    }

    /**
     * @param first what the form sent
     * @param second what is stored
     * @return the first value that is not empty
     */
    private static String firstOf(String first, String second) {
        return first != null ? first : second;
    }

    /**
     * The message shown on the receipt page when recognition is not set up yet. Plain words: the
     * person reading it is at the bench, the names of the system properties mean nothing to them.
     *
     * @return the message shown on the receipt page
     */
    static String missingSetting() {
        return "识图服务还没配好：到「系统设置」页填上密钥（地址与模型已默认填好），保存后就能识别截图。"
                + "没配也能用——把订单明细按 料号,名称,数量,实付金额 粘进上面的框里，点「建单并入库」。";
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
                // a model that thinks first needs room for the reasoning and the answer
                .append(",\"max_tokens\":4000")
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
                throw new IOException(MfgServices.describeVisionError(status, text));
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
     * Writes the answer of the scan: {@code ok}, a message and, when it worked, the form fields of
     * one order or the list of recognised orders.
     *
     * @param response the response
     * @param ok whether the scan worked
     * @param message what to tell the person
     * @param fields the form fields of one order, may be {@code null}
     * @param orders the recognised orders when the screenshot showed a list, may be {@code null}
     * @return {@code success}, {@code error} when the answer could not be written
     */
    private static String answer(HttpServletResponse response, boolean ok, String message,
            Map<String, String> fields, List<Map<String, String>> orders) {
        return answer(response, ok, message, fields, orders, null);
    }

    /**
     * Writes the answer of the event as JSON, with an optional machine readable code the page can
     * act on without matching on the wording of the message.
     *
     * @param response the response
     * @param ok whether the scan worked
     * @param message what to tell the person
     * @param fields the form fields of one order, may be {@code null}
     * @param orders the recognised orders when the screenshot showed a list, may be {@code null}
     * @param code a short code such as {@code UNCONFIGURED}, {@code null} when there is none
     * @return {@code success}, {@code error} when the answer could not be written
     */
    private static String answer(HttpServletResponse response, boolean ok, String message,
            Map<String, String> fields, List<Map<String, String>> orders, String code) {
        StringBuilder json = new StringBuilder();
        json.append("{\"ok\":").append(ok).append(",\"message\":")
                .append(MfgBomEvents.jsonString(message));
        if (code != null) {
            json.append(",\"code\":").append(MfgBomEvents.jsonString(code));
        }
        if (fields != null) {
            for (Map.Entry<String, String> entry : fields.entrySet()) {
                json.append(',').append(MfgBomEvents.jsonString(entry.getKey())).append(':')
                        .append(MfgBomEvents.jsonString(entry.getValue()));
            }
        }
        if (orders != null) {
            json.append(",\"count\":").append(orders.size()).append(",\"orders\":[");
            boolean first = true;
            for (Map<String, String> order : orders) {
                if (!first) {
                    json.append(',');
                }
                first = false;
                json.append('{');
                boolean firstField = true;
                for (Map.Entry<String, String> entry : order.entrySet()) {
                    if (!firstField) {
                        json.append(',');
                    }
                    firstField = false;
                    json.append(MfgBomEvents.jsonString(entry.getKey())).append(':')
                            .append(MfgBomEvents.jsonString(entry.getValue()));
                }
                json.append('}');
            }
            json.append(']');
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

    /**
     * Puts the recognised order on the refunded list, so a later hand typed import of the same
     * number is refused as well.
     *
     * @param delegator the delegator, may be {@code null}
     * @param fields the recognised fields
     */
    private static void recordRefund(Delegator delegator, Map<String, String> fields) {
        if (delegator == null) {
            return;
        }
        rememberRefundedOrder(delegator, fields.get("orderNo"), fields.get("shop"), "截图显示退款/关闭");
    }

    private static Map<String, Object> parametersOf(HttpServletRequest request) {
        Map<String, Object> parsed = UtilGenerics.cast(request.getAttribute("multiPartMap"));
        if (parsed != null && !parsed.isEmpty()) {
            return parsed;
        }
        return org.apache.ofbiz.base.util.UtilHttp.getParameterMap(request);
    }

    /**
     * The delegator of the running instance, needed to read the settings saved on the settings page.
     *
     * @param request the request
     * @return the delegator, {@code null} when the framework did not hand it over
     */
    private static Delegator delegatorOf(HttpServletRequest request) {
        Object delegator = request.getAttribute("delegator");
        if (delegator instanceof Delegator) {
            return (Delegator) delegator;
        }
        Object dispatcher = request.getAttribute("dispatcher");
        if (dispatcher instanceof LocalDispatcher) {
            return ((LocalDispatcher) dispatcher).getDelegator();
        }
        return null;
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
        // a form field sent twice arrives as an array; the first value is the one that counts
        Object plain = value instanceof String[] && ((String[]) value).length > 0
                ? ((String[]) value)[0] : value;
        String text = plain == null ? "" : plain.toString().trim();
        return text.isEmpty() ? null : text;
    }
}
