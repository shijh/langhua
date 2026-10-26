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

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

import org.apache.ofbiz.base.util.Debug;
import org.apache.ofbiz.base.util.UtilGenerics;
import org.apache.ofbiz.entity.Delegator;
import org.apache.ofbiz.service.LocalDispatcher;

import static org.langhua.ofbiz.mfg.MfgServices.isClosed;
import static org.langhua.ofbiz.mfg.MfgServices.isRefunded;

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

    /**
     * Largest screenshot taken in. A phone screenshot of a dozen orders is a few megabytes, but a
     * whole shop page captured in one picture (3 000 by 12 000 pixels) is ten or more, and the model
     * only ever sees slices of it - so the limit is there to keep a runaway upload out of the heap,
     * not because of the call.
     */
    static final int MAX_IMAGE_BYTES = 32 * 1024 * 1024;

    /**
     * How much one call may carry. A slice of a long page is cut out of the picture and written as
     * PNG, which is big for a screenshot holding product photographs; above this size it is handed
     * over as JPEG, which the models read just as well and which keeps the request small.
     */
    static final int MAX_PAYLOAD_BYTES = 4 * 1024 * 1024;

    /** Answer code of the events when the vision service is not set up yet. */
    static final String UNCONFIGURED = "UNCONFIGURED";

    /** Answer code when the service answered but no order could be read out of it. */
    static final String NO_ORDERS = "NO_ORDERS";

    /** Answer code when the upload is larger than {@link #MAX_IMAGE_BYTES}. */
    static final String TOO_LARGE = "TOO_LARGE";

    /** How often the screenshot is sent before the person is asked to try again. */
    static final int ATTEMPTS = 3;

    /**
     * How tall a picture may be before it is cut into slices, and how far a cut may move to land on a
     * blank row. A cut through the middle of an order block leaves its title in one slice and its
     * articles in the next, and the model then hangs those articles on the block that follows (a page
     * of five orders came back as four right ones and one written out of two).
     *
     * <p>The walls of a shop page leave 30 to 40 pixels of white both inside a block and between two
     * of them, so no cut can be trusted to fall between blocks: with the reasoning of the model
     * switched off one call reads four or five blocks well, and slicing only pays off on pages that
     * are longer than this.
     */
    static final int SLICE_HEIGHT = 2400;

    /** How far the cut may slide to find a row with no text, and how many pixels one row is sampled at. */
    static final int SLICE_SEARCH = 180;
    private static final int SLICE_SAMPLE = 4;

    /**
     * How much two slices share. An order block is 300 to 500 pixels tall, so a block that the cut
     * goes through is still whole inside the next slice: with no overlap its title landed in one
     * slice and its articles in the next, and when the model did not hang the rows on the block above
     * that order was gone (production lost the fourth block of a page this way, Step 130). The rows
     * the overlap reads twice are merged by the order number and cut back to one copy by
     * {@code MfgServices.tidyOrder}.
     */
    static final int SLICE_OVERLAP = 500;

    /** Slices that are read at the same time; four small pictures take about as long as one. */
    static final int MAX_PARALLEL = 4;

    /**
     * How often one slice is sent. A slice holds one or two order blocks and its money is allowed not
     * to add up (an order may be cut in two), so there is much less to go wrong than on a whole page.
     */
    static final int SLICE_ATTEMPTS = 3;

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
            + "\"refunded\":\"\",\"closed\":\"\",\"rowCount\":\"\",\"items\":[{\"code\":\"\",\"name\":\"\",\"qty\":\"\",\"amount\":\"\"}]}]}"
            + " orderCount is how many order blocks the picture shows; orders has one element per"
            + " block, top to bottom, and items has one element per article of that order - two"
            + " products are two elements, never one. rowCount is how many article rows that block"
            + " prints, so six colours of the same board is rowCount 6 and six elements."
            + " orderNo is the order number of that block (after 订单号, or the number the page prints"
            + " for it). shop is the shop name only, without the platform word. platform is which"
            + " shop platform the page belongs to: 淘宝, 天猫, 京东, 拼多多, 抖音, or empty when nothing"
            + " says. orderDate is yyyy-MM-dd as printed."
            + " paidTotal is the money really paid for that order (实付款 / 实付; in a list page the 合计"
            + " of the block). freight is the shipping money of the block (含运费 / 运费), and 0 when the"
            + " page says 免运费 or 包邮."
            + " discount is the money taken off the whole order (红包 / 优惠券 / 满减): it is what makes"
            + " paidTotal smaller than the articles plus the freight, 0 when there is none."
            + " refunded is Y only when the money went back: 退款成功 / 已退款 / 退款中 / 退货退款."
            + " closed is Y when the badge says 交易关闭 / 已取消 / 交易失败 - the deal never went"
            + " through, usually because it was never paid - and N when it says 交易成功; the two are"
            + " different states, so a closed order has refunded N and a refunded one closed N."
            + " 7天无理由退货 / 退货宝 / 退换货 are the shop's policy printed on every"
            + " row, not the state of the order: never answer Y for those."
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
            + " prints neither, use the model number of the title, and never leave code empty. When several"
            + " rows print exactly the same title (one board sold in six colours) they are still one element"
            + " each, never one merged element, and the grey line that tells them apart (白色 / 蓝色 / 绿色)"
            + " goes into code so the six rows do not all share one code. The pack"
            + " size is never part of the code: the spec"
            + " line 30K（一件100只）;1%精度;0603贴片电阻 gives the code 30K, not the whole line; a pack size"
            + " printed inside the title counts too, so 面包板 … 带扣（5个） with x1 is 5 pieces, and so does"
            + " a piece count printed bare at the end of the title: IC测试板 … 5只 with x3 is 15 - but a set"
            + " sold as one article (brackets saying 套装 / 组合 / 套餐) does not: 实验板（4块组合套装）"
            + " with x1 is 1 set, so qty is 1."
            + " Under the last order the page shows pictures of other products (猜你喜欢 / 为你推荐 /"
            + " 看了又看 / 广告): they have no order number and no 实付款, so they are not an order, and"
            + " their products are never items of the order above them."
            + " The articles printed inside one block belong to that block only: never write the articles"
            + " of a block into the block under it, and never leave a block without its own articles."
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
            + "\"refunded\":\"Y or N\",\"closed\":\"Y or N\",\"rowCount\":\"\","
            + "\"items\":[{\"code\":\"\",\"name\":\"\",\"qty\":\"\",\"amount\":\"\"}]}]}"
            + " orderCount = how many order blocks you see; one element per block, top to bottom."
            + " rowCount = how many article rows that block prints (six colours = 6), and items has"
            + " one element per printed row."
            + " platform = 淘宝 / 天猫 / 京东 / 拼多多 / 抖音. shop without the platform word."
            + " paidTotal = 实付款 (or 合计). freight = the shipping money (含运费 / 运费)."
            + " discount = the red packet / coupon / 满减 taken off the order, 0 when none."
            + " refunded = Y only when it says 退款成功 / 已退款 / 退款中 / 退货退款 (never for 退货宝 /"
            + " 7天无理由退货 - that is a shop policy). closed = Y when the badge says 交易关闭 /"
            + " 已取消 / 交易失败, else N; a closed order is not a refund, so its refunded is N."
            + " items = one element per article,"
            + " never two products in one element: their amounts add up to paidTotal minus freight;"
            + " rows that print the same title are still one element each, with the colour in code;"
            + " qty = the number after x times the pack size printed in the grey spec line or inside the"
            + " title"
            + " (一件100只 → x1 is 100, (50只) → x4 is 200, 带扣（5个） → x1 is 5, 测试板 5只 → x3 is 15); a set"
            + " sold as one article (套装 / 组合 / 套餐 in the brackets) is qty = the number after x"
            + " itself, so （4块组合套装） with x1 is 1; amount = the row's own"
            + " money (paidTotal"
            + " minus freight for a block with one article, printed row price times the packs after x for"
            + " a block with several). Pictures of other products below the order list (猜你喜欢 / 推荐 /"
            + " 广告) are not orders and are not items; the articles of one block are never items of the"
            + " block under it. No explanation.";

    private MfgOrderScan() {
    }

    /**
     * Says whether an upload is too large for the scan, and what to do about it.
     *
     * <p>A long page is not a problem: the picture is cut into slices and each slice is read on its
     * own, so a screenshot of a whole order list is welcome as it is. The limit only stops an upload
     * that would not fit the heap.
     *
     * @param bytes how many bytes the upload has
     * @return the message for the person, {@code null} when the picture may be read
     */
    static String imageSizeProblem(int bytes) {
        if (bytes <= MAX_IMAGE_BYTES) {
            return null;
        }
        return "图片太大（超过 " + (MAX_IMAGE_BYTES / (1024 * 1024)) + " MB）：很长的页面不用缩，"
                + "整张传上来会分段识别；超过这个大小请截成两张再试";
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
            return answer(response, false, imageSizeProblem(image.length), null, TOO_LARGE);
        }
        String endpoint = text(MfgServices.setting(delegatorOf(request), VISION_ENDPOINT));
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
            orders = MfgServices.mergeOrders(giveTheRowsAnOrder(answers));
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
        int closedCount = 0;
        for (Map<String, String> order : orders) {
            // a package description as the part number does not fit the code field: short it here
            // so the page already shows the code that will be stored
            MfgServices.tidyOrder(order);
            order.put("lines", MfgServices.fitOrderLines(order.get("lines")));
            if (isRefunded(order)) {
                // a refund is not booked in, but reading one is a guess like everything else: the model
                // took 「7天无理由退货」 and 「退货宝」 for a refund and blocked a good order for good.
                // So the order is only left out of this round, and the person puts a real refund on the
                // list with one click (Step 108).
                order.put("note", "显示已退款，这一次不入库；货确实到了就手工粘明细建单，"
                        + "确实退了就把它登记到「退款 / 关闭的单」里");
                refundedCount++;
            } else if (isClosed(order)) {
                // 交易关闭 means the deal never went through - the person confirmed it is usually an
                // order they never paid. Nothing was shipped, so booking it would invent stock; a
                // parcel that does turn up can still be typed in by hand (Step 109).
                order.put("note", "交易关闭：这张单没成交（一般是从没付款），这一次不入库；"
                        + "万一货真发过来了，就手工粘明细建单");
                closedCount++;
            } else {
                // freight is what the page says, but goods plus freight must come to what was paid
                String totals = MfgServices.checkOrderTotals(order);
                // and the rows the block prints have to be there: six colours of one board read as a
                // single item still adds up, so this one cannot be left to the money check
                String rowsOff = null;
                if (MfgServices.rowsMissing(order)) {
                    rowsOff = "截图印了 " + order.get("rowCount") + " 行明细，这次只读出 "
                            + MfgServices.parseOrderArticleLines(order.get("lines")).size()
                            + " 行，请对着截图核对（也可以再点一次「识别并核对」）";
                }
                String note = totals == null ? rowsOff
                        : (rowsOff == null ? totals : totals + "；" + rowsOff);
                if (note != null) {
                    order.put("note", note);
                }
            }
        }
        // a block that carries exactly the articles and the money of another block was probably copied
        // from it while the page was read: say so, the money of a copy adds up and nothing else notices
        Map<String, String> firstOfKind = new LinkedHashMap<>();
        Map<String, String> firstOfMoney = new LinkedHashMap<>();
        for (int i = 0; i < orders.size(); i++) {
            Map<String, String> order = orders.get(i);
            String content = MfgServices.orderContentKey(order);
            String money = MfgServices.moneyKeyOf(order);
            String other = content == null ? null : firstOfKind.get(content);
            String otherMoney = money == null ? null : firstOfMoney.get(money);
            if (content != null && other == null) {
                firstOfKind.put(content, order.get("orderNo"));
            }
            if (money != null && otherMoney == null) {
                firstOfMoney.put(money, String.valueOf(i + 1));
            }
            String copied = null;
            if (other != null) {
                copied = "明细与单号 " + other + " 那张一模一样，可能识别时抄错了，请对着截图核对";
            } else if (otherMoney != null) {
                // the codes and names two reads write differ now and then, so two rows of one printed
                // line do not always look alike: what matches is the quantity and the money
                copied = "明细和金额与第 " + otherMoney + " 张单完全相同，可能把上一块的明细带下来了，请对着截图核对";
            }
            if (copied != null) {
                String note = order.get("note");
                order.put("note", note == null ? copied : note + "；" + copied);
            }
        }
        // one order is a list of one: the page always gets the array, so there is only one path to walk
        // (a single order used to fill the form instead, which the shop found confusing)
        String message = orders.size() == 1
                ? "识别到 1 张单"
                : "识别到 " + orders.size() + " 张单";
        int skipped = refundedCount + closedCount;
        if (skipped > 0) {
            String what = refundedCount > 0 && closedCount > 0
                    ? "退款/关闭的"
                    : (refundedCount > 0 ? "已退款的" : "交易关闭的");
            if (skipped == orders.size()) {
                message += orders.size() == 1
                        ? "，这张单是" + what + "，按规矩不入库"
                        : "，都是" + what + "，按规矩不入库";
            } else {
                message += "（其中 " + skipped + " 张是" + what + "，不会入库）";
            }
        }
        if (skipped < orders.size()) {
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
     * Hangs the rows of a slice that carries no order number on the order whose header the slice
     * before it had: a long order of six rows is cut in two, and the second piece prints only the
     * rows - the number is above the cut. Dropping such an answer lost the rows (a page of six
     * boards came back with two), so the number of the order seen last is used.
     *
     * @param answers what each slice answered, in the order the slices were cut, top to bottom
     * @return the same answers with an order number on every order that holds rows
     */
    private static List<List<Map<String, String>>> giveTheRowsAnOrder(
            List<List<Map<String, String>>> answers) {
        String lastNumber = null;
        for (List<Map<String, String>> answer : answers) {
            for (Map<String, String> order : answer) {
                String number = order.get("orderNo");
                if (number == null || number.trim().isEmpty()) {
                    if (lastNumber != null) {
                        order.put("orderNo", lastNumber);
                    }
                    continue;
                }
                lastNumber = number;
            }
        }
        // a slice above every header (nothing seen yet) has nothing to belong to, and an order without
        // a number cannot be imported anyway
        for (List<Map<String, String>> answer : answers) {
            answer.removeIf(order -> {
                String number = order.get("orderNo");
                return number == null || number.trim().isEmpty();
            });
        }
        return answers;
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
        // a slice of a page full of product photographs is large as PNG: hand it over as JPEG, which
        // keeps the call small enough for the endpoint while the text stays readable
        if (picture != null && picture.length > MAX_PAYLOAD_BYTES) {
            byte[] jpeg = jpegOf(picture);
            if (jpeg != null && jpeg.length < picture.length) {
                picture = jpeg;
                contentType = "image/jpeg";
            }
        }
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
                // a slice is asked with the short request from the start: the long one makes a
                // thinking model deliberate about every rule, and on a hard page it then spends the
                // whole output budget on the thinking and answers with nothing at all
                reply = askVision(endpoint, model, key, picture, contentType,
                        partial || attempt > 1 ? SHORT_PROMPT : PROMPT);
                read.transportProblem = null;
            } catch (IOException e) {
                Debug.logError(e, "Could not ask the vision service (attempt " + attempt + ")", MODULE);
                read.transportProblem = e.getMessage();
                continue;
            }
            read.reply = reply;
            List<Map<String, String>> got = MfgServices.orderListFrom(reply, partial);
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
                    && !anyRowsMissing(got)
                    && !MfgServices.repeatsAnotherOrder(got)
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
     * Cuts a tall screenshot into slices, so each call sees only one or two order blocks. A picture
     * that already fits is sent as it is; a picture that cannot be decoded is sent as it is too, so an
     * odd format never loses the scan.
     *
     * <p>The cut slides to a row without text ({@code blankRowNear}), so an order block is whole in
     * one of the slices: a cut through a block used to leave its title in one slice and its articles
     * in the next, and the model then attached them to the order below.
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
        int height = full.getHeight();
        int top = 0;
        while (top < height) {
            int bottom = Math.min(height, top + SLICE_HEIGHT);
            if (bottom < height) {
                bottom = blankRowNear(full, top, bottom);
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            try {
                ImageIO.write(full.getSubimage(0, top, full.getWidth(), bottom - top), "png", out);
            } catch (IOException e) {
                Debug.logError(e, "Could not write a slice of the screenshot", MODULE);
                slices.clear();
                slices.add(image);
                return slices;
            }
            slices.add(out.toByteArray());
            if (bottom >= height) {
                break;
            }
            // the next slice starts before this one ends, so a block the cut went through is whole in
            // it; the guard only keeps the loop moving if the cut slid back too far
            int next = bottom - SLICE_OVERLAP;
            top = next > top ? next : top + Math.max(1, SLICE_HEIGHT / 2);
        }
        return slices;
    }

    /**
     * Re-encodes a picture as JPEG, so a large slice of a page full of product photographs does not
     * have to be sent as lossless PNG. The text of a screenshot stays readable at this quality.
     *
     * @param image the picture bytes
     * @return the JPEG bytes, {@code null} when the picture cannot be decoded or written (an odd
     *         format is then sent as it came rather than lost)
     */
    static byte[] jpegOf(byte[] image) {
        BufferedImage picture;
        try {
            picture = ImageIO.read(new ByteArrayInputStream(image));
        } catch (IOException e) {
            Debug.logError(e, "Could not read the picture to re-encode it", MODULE);
            return null;
        }
        if (picture == null) {
            return null;
        }
        // JPEG has no transparency: draw the picture on white, or a screenshot with an alpha channel
        // comes out black
        BufferedImage flat = new BufferedImage(picture.getWidth(), picture.getHeight(),
                BufferedImage.TYPE_INT_RGB);
        Graphics2D canvas = flat.createGraphics();
        canvas.drawImage(picture, 0, 0, Color.WHITE, null);
        canvas.dispose();
        java.util.Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpeg");
        if (!writers.hasNext()) {
            return null;
        }
        ImageWriter writer = writers.next();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ImageOutputStream stream = ImageIO.createImageOutputStream(out)) {
            writer.setOutput(stream);
            ImageWriteParam param = writer.getDefaultWriteParam();
            if (param.canWriteCompressed()) {
                param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                param.setCompressionQuality(0.9f);
            }
            writer.write(null, new IIOImage(flat, null, null), param);
        } catch (IOException e) {
            Debug.logError(e, "Could not write the picture as JPEG", MODULE);
            return null;
        } finally {
            writer.dispose();
        }
        return out.toByteArray();
    }

    /**
     * The row closest to the wanted cut that carries no text, so the cut falls between two order
     * blocks instead of through one. A page prints a blank line (or the grey band of the next block)
     * between orders, and such a row has almost no colour changes across it.
     *
     * @param image the picture being cut
     * @param top the first row of the slice
     * @param wanted the row the slice would end at
     * @return the row to end the slice at
     */
    private static int blankRowNear(BufferedImage image, int top, int wanted) {
        // a slice that only holds a sliver of a block is worse than a cut through it
        int lowest = top + SLICE_HEIGHT / 2;
        int from = Math.max(lowest, wanted - SLICE_SEARCH);
        int to = Math.min(image.getHeight() - 1, wanted + SLICE_SEARCH);
        int best = wanted;
        int bestChanges = Integer.MAX_VALUE;
        for (int y = from; y <= to; y++) {
            int changes = colourChanges(image, y);
            // of two equally empty rows the one nearer the wanted cut wins: the slices stay as tall
            // as they were meant to be, and a page needs fewer calls
            if (changes < bestChanges || (changes == bestChanges
                    && Math.abs(y - wanted) < Math.abs(best - wanted))) {
                bestChanges = changes;
                best = y;
            }
        }
        return best;
    }

    /**
     * @param image the picture
     * @param y the row to look at
     * @return how often the colour changes along that row; a blank or single coloured row gives 0
     */
    private static int colourChanges(BufferedImage image, int y) {
        int changes = 0;
        int previous = image.getRGB(0, y);
        for (int x = SLICE_SAMPLE; x < image.getWidth(); x += SLICE_SAMPLE) {
            int rgb = image.getRGB(x, y);
            if (rgb != previous) {
                changes++;
                previous = rgb;
            }
        }
        return changes;
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

    /**
     * Says whether any order came back with fewer article rows than its block prints. The money check
     * cannot see this one: one board in six colours read as a single item of 41.28 adds up exactly,
     * so the model is asked to count the rows and a shorter answer is sent once more.
     *
     * @param orders the recognised orders
     * @return {@code true} when an order counts more rows than came back
     */
    private static boolean anyRowsMissing(List<Map<String, String>> orders) {
        for (Map<String, String> order : orders) {
            if (MfgServices.rowsMissing(order)) {
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
     * Whether the endpoint takes the "do not think first" switch. A thinking model spends its output
     * budget on the reasoning when the picture is dense and then answers with an empty content, which
     * is what made a page of eight orders need several sends. Reading a page is not a reasoning task,
     * so the switch is asked for; when the endpoint does not know it (HTTP 400) it is dropped for the
     * rest of the run and the call is repeated once without it.
     */
    private static volatile boolean plainAnswerWorks = true;

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
        return send(endpoint, model, key, image, contentType, prompt, plainAnswerWorks);
    }

    /**
     * One call to the endpoint.
     *
     * @param plain {@code true} to ask the model to answer without thinking first
     * @return the answer text of the model
     * @throws IOException when the call fails
     */
    private static String send(String endpoint, String model, String key, byte[] image, String contentType,
            String prompt, boolean plain) throws IOException {
        String dataUrl = "data:" + contentType + ";base64,"
                + Base64.getEncoder().encodeToString(image);
        StringBuilder body = new StringBuilder();
        body.append("{\"model\":").append(MfgBomEvents.jsonString(model))
                // a model that thinks first needs room for the reasoning and the answer
                .append(",\"max_tokens\":").append(MAX_TOKENS);
        if (plain) {
            // OpenAI compatible servers ignore a field they do not know; the ones that do know it
            // then answer straight away, which is what a screenshot needs
            body.append(",\"thinking\":{\"type\":\"disabled\"}");
        }
        body.append(",\"messages\":[{\"role\":\"user\",\"content\":[")
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
                if (plain && status == 400 && plainAnswerWorks) {
                    // the endpoint did not like the switch: remember it and ask again without it
                    plainAnswerWorks = false;
                    Debug.logInfo("The vision endpoint refused the thinking switch, asking again without"
                            + " it: " + text, MODULE);
                    return send(endpoint, model, key, image, contentType, prompt, false);
                }
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
