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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.imageio.ImageIO;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.awt.image.BufferedImage;

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

    /** Answer code when the service answered but no order could be read out of it. */
    static final String NO_ORDERS = "NO_ORDERS";

    /** How often the screenshot is sent before the person is asked to try again. */
    static final int ATTEMPTS = 3;

    /**
     * How tall a picture may be before it is cut into slices. Three or four order blocks fit in this
     * much, which is what one call reads reliably; five blocks in one picture is where the model
     * starts dropping whole orders.
     */
    static final int SLICE_HEIGHT = 900;

    /** How much two neighbouring slices share, so an order block is whole in at least one of them. */
    static final int SLICE_OVERLAP = 150;

    /** Slices that are read at the same time; four small pictures take about as long as one. */
    static final int MAX_PARALLEL = 4;

    /**
     * How often one slice is sent. A slice holds one or two order blocks and its money is allowed not
     * to add up (an order may be cut in two), so there is much less to go wrong than on a whole page.
     */
    static final int SLICE_ATTEMPTS = 2;

    /** Seconds one call to the vision service may take, with room on top of the socket timeouts. */
    static final int SLICE_WAIT_SECONDS = 240;

    /**
     * Room for the answer. A thinking model spends part of it on its reasoning, so a small budget
     * ends with an empty answer: 4000 was not enough for a screenshot holding three orders.
     */
    static final int MAX_TOKENS = 8000;

    /** A 1x1 png, used by the connection test so a vision endpoint has something to look at. */
    private static final String TINY_PNG =
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==";

    /**
     * What the model is asked for: one JSON object, nothing else.
     *
     * <p>It is worded as decisions rather than as questions: the earlier, longer version made the
     * model deliberate (the shop name with or without 天猫, the struck through price), and a thinking
     * model that deliberates spends its whole output budget on the reasoning and answers nothing.
     */
    static final String PROMPT = "Read this screenshot of shop orders (淘宝 / 天猫, and the same kind of"
            + " page from 京东 / 拼多多 / 抖音) and answer with one"
            + " JSON object only, no prose, no explanation:"
            + " {\"orderCount\":\"1\",\"orders\":[{\"orderNo\":\"\",\"shop\":\"\",\"platform\":\"\","
            + "\"orderDate\":\"\",\"paidTotal\":\"\",\"freight\":\"\",\"discount\":\"\","
            + "\"refunded\":\"\",\"items\":[{\"code\":\"\",\"name\":\"\",\"qty\":\"\",\"amount\":\"\"}]}]}"
            + " orderCount is how many order blocks the picture shows; orders has one element per"
            + " block, top to bottom, and items has one element per article of that order - two"
            + " products are two elements, never one."
            + " orderNo is the order number of that block (after 订单号, or the number the page prints"
            + " for it). shop is the shop name only, without the platform word. platform is which"
            + " shop platform the page belongs to: 淘宝, 天猫, 京东, 拼多多, 抖音, or empty when nothing"
            + " says. orderDate is yyyy-MM-dd as printed."
            + " paidTotal is the money really paid for that order (实付款 / 实付; in a list page the 合计"
            + " of the block). freight is the shipping money of the block (含运费 / 运费), and 0 when the"
            + " page says 免运费 or 包邮."
            + " discount is the money taken off the whole order (红包 / 优惠券 / 满减): it is what makes"
            + " paidTotal smaller than the articles plus the freight, 0 when there is none."
            + " refunded is Y when the block shows 退款成功 / 已退款 / 退款中 / 交易关闭 / 已取消,"
            + " otherwise N."
            + " In items, quantity is how many pieces were bought: Taobao sells resistors and"
            + " capacitors by the pack, and the grey spec line under the title prints the pack size"
            + " as 一件100只 / （50只） / 100只装 / 1件=100个. qty = the number after x times that"
            + " pack size, so x1 with 一件100只 is 100, x4 with (50只) is 200 and an article with no"
            + " pack size is the number after x itself; amount is that row's money: for a block with one"
            + " article it is paidTotal minus freight,"
            + " for a block with two or more articles it is the printed row price times the number of"
            + " packs after x (1.20 with x2 is 2.40), never the price of a single piece. One element per"
            + " article - a block that shows two products gets two elements, never one. Never use the"
            + " struck through price. code is the short model or spec line printed under the title"
            + " (2.0mm单排8P(共8P), 502035(350mah), 30K) and name is the title itself; when the article"
            + " prints neither, use the model number of the title, and never leave code empty. The pack"
            + " size is never part of the code: the spec"
            + " line 30K（一件100只）;1%精度;0603贴片电阻 gives the code 30K, not the whole line."
            + " Take the numbers printed on the page: do not weigh alternatives, do not describe the"
            + " picture.";

    /**
     * The same request in as few words as possible, used for the retry. A thinking model reasons about
     * every rule it is given; with the long prompt a picture of three orders sometimes ended with the
     * output budget spent on the reasoning and a truncated (or empty) answer.
     */
    static final String SHORT_PROMPT = "Read the shop orders (淘宝 / 天猫 / 京东 / 拼多多 / 抖音) in this"
            + " picture. Answer with one JSON object only, no prose:"
            + " {\"orderCount\":\"1\",\"orders\":[{\"orderNo\":\"\",\"shop\":\"\",\"platform\":\"\","
            + "\"orderDate\":\"\",\"paidTotal\":\"\",\"freight\":\"\",\"discount\":\"\","
            + "\"refunded\":\"Y or N\",\"items\":[{\"code\":\"\",\"name\":\"\",\"qty\":\"\",\"amount\":\"\"}]}]}"
            + " orderCount = how many order blocks you see; one element per block, top to bottom."
            + " platform = 淘宝 / 天猫 / 京东 / 拼多多 / 抖音. shop without the platform word."
            + " paidTotal = 实付款 (or 合计). freight = the shipping money (含运费 / 运费)."
            + " discount = the red packet / coupon / 满减 taken off the order, 0 when none."
            + " refunded = Y when it says 退款成功 / 已退款 / 交易关闭. items = one element per article,"
            + " never two products in one element: their amounts add up to paidTotal minus freight;"
            + " qty = the number after x times the pack size printed in the grey spec line"
            + " (一件100只 → x1 is 100, (50只) → x4 is 200); amount = the row's own money (paidTotal"
            + " minus freight for a block with one article, printed row price times the packs after x for"
            + " a block with several). No explanation.";

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
        }        String endpoint = text(MfgServices.setting(delegatorOf(request), VISION_ENDPOINT));
        String model = text(MfgServices.setting(delegatorOf(request), VISION_MODEL));
        String key = text(MfgServices.setting(delegatorOf(request), VISION_KEY));
        if (endpoint == null || model == null || key == null) {
            return answer(response, false, missingSetting(), null, UNCONFIGURED);
        }
        endpoint = MfgServices.normalizeVisionEndpoint(endpoint);
        String contentType = text(parameters.get("_image_contentType"));
        if (contentType == null) {
            contentType = "image/png";
        }
        String reply = null;
        String transportProblem = null;
        List<Map<String, String>> orders = Collections.emptyList();
        int seen = 0;
        // a long page (several order blocks one under the other) is cut into overlapping slices: one
        // small picture per slice is read far more reliably than five blocks at once, and the rows of
        // an order that straddles two slices are joined afterwards by its number
        List<byte[]> slices = slicesOf(image);
        String sliceNote = "";
        if (slices.size() == 1) {
            Read read = readPicture(slices.get(0), endpoint, model, key, contentType, false);
            orders = read.orders;
            reply = read.reply;
            seen = read.seen;
            transportProblem = read.transportProblem;
        } else {
            // the slices are read side by side: four small calls in parallel take about as long as
            // one, while reading them one after the other made a five order page wait minutes
            List<List<Map<String, String>>> answers = new ArrayList<>();
            ExecutorService pool = Executors.newFixedThreadPool(Math.min(MAX_PARALLEL, slices.size()));
            List<Future<Read>> running = new ArrayList<>();
            final String sliceEndpoint = endpoint;
            final String sliceModel = model;
            final String sliceKey = key;
            final String sliceType = contentType;
            for (byte[] slice : slices) {
                final byte[] picture = slice;
                running.add(pool.submit(new Callable<Read>() {
                    @Override
                    public Read call() {
                        return readPicture(picture, sliceEndpoint, sliceModel, sliceKey, sliceType, true);
                    }
                }));
            }
            pool.shutdown();
            for (Future<Read> future : running) {
                Read read;
                try {
                    read = future.get(SLICE_WAIT_SECONDS, TimeUnit.SECONDS);
                } catch (InterruptedException | ExecutionException | TimeoutException e) {
                    Debug.logError(e, "A slice of the screenshot was not read", MODULE);
                    transportProblem = e.getMessage();
                    answers.add(Collections.emptyList());
                    continue;
                }
                if (read.reply != null) {
                    reply = read.reply;
                }
                if (read.transportProblem != null) {
                    transportProblem = read.transportProblem;
                }
                answers.add(read.orders);
            }
            orders = MfgServices.mergeOrders(answers);
            seen = orders.size();
            sliceNote = "（长图分 " + slices.size() + " 段逐段识别）";
        }
        if (orders.isEmpty()) {
            return answer(response, false, scanFailure(reply, transportProblem), null, NO_ORDERS);
        }
        String shortfall = seen > orders.size()
                ? "（截图上看上去有 " + seen + " 张单，这次只读出 " + orders.size()
                        + " 张，可能漏了；核对一下，也可以再点一次「识别并核对」）"
                : "";
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
        // one order is a list of one: the page always gets the array, so there is only one path to walk
        // (a single order used to fill the form instead, which the shop found confusing)
        String message = orders.size() == 1
                ? "识别到 1 张单"
                : "识别到 " + orders.size() + " 张单";
        if (refundedCount > 0) {
            if (refundedCount == orders.size()) {
                message += orders.size() == 1
                        ? "，这张单显示已退款/已关闭，按规矩不入库（订单号已经记进「退款 / 关闭的单」）"
                        : "，都是退款/关闭的，按规矩不入库（订单号已经记进「退款 / 关闭的单」）";
            } else {
                message += "（其中 " + refundedCount + " 张已退款，不会入库）";
            }
        }
        if (refundedCount < orders.size()) {
            message += "，核对后点「" + (orders.size() == 1 ? "导入这张单" : "导入这些单") + "」";
        }
        return answer(response, true, message + sliceNote + shortfall, orders, null);
    }

    /** What one picture (the whole screenshot, or one slice of a long one) answered. */
    private static final class Read {
        private List<Map<String, String>> orders = Collections.emptyList();
        private String reply;
        private int seen;
        private String transportProblem;
    }

    /**
     * Sends one picture and keeps the best answer of the attempts. A vision call falls over now and
     * then (a slow answer, a thinking model that runs out of room, a dropped connection, one order of
     * a list read and the others skipped), and the last attempt is not always the best one, so the
     * answer is graded and the best one is kept.
     *
     * @param picture the image bytes, the whole screenshot or one slice of it
     * @param endpoint the vision endpoint
     * @param model the model name
     * @param key the api key
     * @param contentType the content type of the picture
     * @param partial {@code true} for a slice of a long page: an order may be cut in two, so the money
     *        of that slice is allowed not to add up
     * @return what was read
     */
    private static Read readPicture(byte[] picture, String endpoint, String model, String key,
            String contentType, boolean partial) {
        Read read = new Read();
        List<Map<String, String>> bestOrders = Collections.emptyList();
        String bestReply = null;
        int bestSeen = 0;
        int bestScore = -1;
        int attempts = partial ? SLICE_ATTEMPTS : ATTEMPTS;
        for (int attempt = 1; attempt <= attempts; attempt++) {
            if (attempt > 1) {
                pause();
            }
            String reply;
            try {
                reply = askVision(endpoint, model, key, picture, contentType,
                        attempt == 1 ? PROMPT : SHORT_PROMPT);
                read.transportProblem = null;
            } catch (IOException e) {
                Debug.logError(e, "Could not ask the vision service (attempt " + attempt + ")", MODULE);
                read.transportProblem = e.getMessage();
                continue;
            }
            read.reply = reply;
            List<Map<String, String>> got = MfgServices.orderListFrom(reply);
            int counted = MfgServices.orderCountIn(reply);
            int score = MfgServices.answerScore(got, counted);
            if (score > bestScore) {
                bestScore = score;
                bestOrders = got;
                bestReply = reply;
                bestSeen = counted;
            }
            // fewer orders read than the model says it saw, an answer that is mostly the answer
            // format, a row whose quantity or amount cannot be read, a page that prints a pack size
            // while every line came back as one piece, or (on a whole screenshot) an order whose
            // goods plus freight is not the paid total: either way one more send is worth it
            boolean shortRead = !partial && counted > 0 && got.size() < counted;
            if (!got.isEmpty() && !shortRead && !MfgServices.echoesTemplate(reply)
                    && !MfgServices.anyUnreadable(got)
                    && !MfgServices.ignoredThePackSize(reply, MfgServices.linesOfOrders(got))
                    && (partial || !anyTotalsDisagree(got))) {
                bestOrders = got;
                bestReply = reply;
                bestSeen = counted;
                break;
            }
            Debug.logInfo("The vision answer carried no lines: " + reply, MODULE);
        }
        read.orders = bestOrders;
        read.reply = bestReply != null ? bestReply : read.reply;
        read.seen = bestSeen;
        return read;
    }

    /**
     * Cuts a tall screenshot into overlapping slices, so each call sees only one or two order blocks.
     * A picture that already fits is sent as it is; a picture that cannot be decoded is sent as it is
     * too, so an odd format never loses the scan.
     *
     * @param image the uploaded image
     * @return one entry per slice
     */
    static List<byte[]> slicesOf(byte[] image) {
        List<byte[]> slices = new ArrayList<>();
        BufferedImage full;
        try {
            full = ImageIO.read(new ByteArrayInputStream(image));
        } catch (IOException e) {
            Debug.logError(e, "Could not read the screenshot to slice it", MODULE);
            full = null;
        }
        if (full == null || full.getHeight() <= SLICE_HEIGHT) {
            slices.add(image);
            return slices;
        }
        int step = SLICE_HEIGHT - SLICE_OVERLAP;
        for (int top = 0; top < full.getHeight(); top += step) {
            int bottom = Math.min(full.getHeight(), top + SLICE_HEIGHT);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            try {
                // the overlap keeps an order block whole in at least one slice
                ImageIO.write(full.getSubimage(0, top, full.getWidth(), bottom - top), "png", out);
            } catch (IOException e) {
                Debug.logError(e, "Could not write a slice of the screenshot", MODULE);
                slices.clear();
                slices.add(image);
                return slices;
            }
            slices.add(out.toByteArray());
            if (bottom >= full.getHeight()) {
                break;
            }
        }
        return slices;
    }

    /**
     * What to tell the person when the screenshot could not be read even after the retry. The message
     * has to hand them something to do: press the button again, trim the screenshot, or type it in.
     *
     * @param reply the last answer of the model, {@code null} when the call itself failed
     * @param transportProblem the error of the last call, {@code null} when a call did get through
     * @return the message shown under the scan button
     */
    static String scanFailure(String reply, String transportProblem) {
        if (transportProblem != null) {
            return "识图服务连不上（已自动重试 " + (ATTEMPTS - 1) + " 次）：" + transportProblem
                    + "。过一会儿再点一次「识别并核对」，或者把明细粘进框里。";
        }
        if ("length".equals(MfgServices.jsonStringField(reply, "finish_reason"))) {
            // a thinking model that spends the whole output budget on reasoning answers with nothing
            return "模型这次想得太久、没把结果写完（输出长度到顶了），已经重试过。"
                    + "把截图截小一点（只框住订单那几行）再点一次「识别并核对」，或者直接把明细粘进框里。";
        }
        int said = MfgServices.orderCountIn(reply);
        if (said > 0) {
            // the answer names a count but no order could be read out of it: it was cut off
            return "模型说看到 " + said + " 张单，但这次的回答被截断了（只写了一部分），已经重试过。"
                    + "把截图截小一点（一次只框一两张单）再点一次「识别并核对」，或者直接把明细粘进框里。";
        }
        return "这次没从截图里读出订单（已自动重试 " + (ATTEMPTS - 1) + " 次）。"
                + "再点一次「识别并核对」通常就好了；也可以把截图截小一点（只框住订单列表），或者手工填。"
                + "模型的原话：" + MfgServices.visionReplyExcerpt(reply, 160);
    }

    /**
     * Says whether the money of any recognised order does not add up.
     *
     * @param orders the recognised orders
     * @return {@code true} when at least one order looks like it lost an article
     */
    private static boolean anyTotalsDisagree(List<Map<String, String>> orders) {
        for (Map<String, String> order : orders) {
            if (MfgServices.totalsDisagree(order)) {
                return true;
            }
        }
        return false;
    }

    /** Waits a moment before the retry, so a hiccup of the service is not hit twice in a row. */
    private static void pause() {
        try {
            Thread.sleep(700L);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Tries the settings with a one pixel picture, so a wrong address, key or model is found on the
     * settings page instead of in the middle of booking an order in. Tests what the form holds, and
     * keeps it when the call works.
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
            return answer(response, false, testMissing(endpoint, model, key), null, UNCONFIGURED);
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
            return answer(response, true, "连接成功" + kept + "，模型回答：" + content, null, null);        } catch (IOException e) {
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
        return askVision(endpoint, model, key, image, contentType, PROMPT);
    }

    /**
     * Calls an OpenAI compatible chat endpoint with the screenshot as a data URL.
     *
     * @param endpoint the chat completions URL
     * @param model the model name
     * @param key the bearer token
     * @param image the picture
     * @param contentType its content type
     * @param prompt what the model is asked
     * @return the answer text of the model
     * @throws IOException when the call fails
     */
    static String askVision(String endpoint, String model, String key, byte[] image, String contentType,
            String prompt) throws IOException {
        String dataUrl = "data:" + contentType + ";base64,"
                + Base64.getEncoder().encodeToString(image);
        StringBuilder body = new StringBuilder();
        body.append("{\"model\":").append(MfgBomEvents.jsonString(model))
                // a model that thinks first needs room for the reasoning and the answer
                .append(",\"max_tokens\":").append(MAX_TOKENS)
                .append(",\"messages\":[{\"role\":\"user\",\"content\":[")
                .append("{\"type\":\"text\",\"text\":").append(MfgBomEvents.jsonString(prompt)).append("},")
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
     * Writes the answer of the event as JSON: {@code ok}, a message, the recognised orders and an
     * optional machine readable code the page can act on without matching the wording.
     *
     * @param response the response
     * @param ok whether the scan worked
     * @param message what to tell the person
     * @param orders the recognised orders, {@code null} when there are none
     * @param code a short code such as {@code UNCONFIGURED}, {@code null} when there is none
     * @return {@code success}, {@code error} when the answer could not be written
     */
    private static String answer(HttpServletResponse response, boolean ok, String message,
            List<Map<String, String>> orders, String code) {
        StringBuilder json = new StringBuilder();
        json.append("{\"ok\":").append(ok).append(",\"message\":")
                .append(MfgBomEvents.jsonString(message));
        if (code != null) {
            json.append(",\"code\":").append(MfgBomEvents.jsonString(code));
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
