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

package org.langhua.ofbiz.mfg.oir;

import org.langhua.ofbiz.mfg.MfgAttachmentServices;
import org.langhua.ofbiz.mfg.MfgBomEvents;
import org.langhua.ofbiz.mfg.MfgConstants;
import org.langhua.ofbiz.mfg.MfgSettingsServices;

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
public final class OirScan {

    private static final String MODULE = OirScan.class.getName();

    /** Where the chat completions endpoint lives, e.g. https://host/v1/chat/completions. */
    public static final String VISION_ENDPOINT = "org.langhua.mfg.vision.endpoint";
    /** Model name the endpoint knows, e.g. a vision capable model. */
    public static final String VISION_MODEL = "org.langhua.mfg.vision.model";
    /** Bearer token of the endpoint. */
    public static final String VISION_KEY = "org.langhua.mfg.vision.key";

    /**
     * Largest screenshot taken in. A phone screenshot of a dozen orders is a few megabytes, but a
     * whole shop page captured in one picture (3 000 by 12 000 pixels) is ten or more, and the model
     * only ever sees slices of it - so the limit is there to keep a runaway upload out of the heap,
     * not because of the call.
     */
    public static final int MAX_IMAGE_BYTES = 32 * 1024 * 1024;

    /**
     * How much one call may carry. A slice of a long page is cut out of the picture and written as
     * PNG, which is big for a screenshot holding product photographs; above this size it is handed
     * over as JPEG, which the models read just as well and which keeps the request small.
     */
    public static final int MAX_PAYLOAD_BYTES = 4 * 1024 * 1024;

    /** Answer code of the events when the vision service is not set up yet. */
    public static final String UNCONFIGURED = "UNCONFIGURED";

    /** Answer code when the service answered but no order could be read out of it. */
    public static final String NO_ORDERS = "NO_ORDERS";

    /** Answer code when the upload is larger than {@link #MAX_IMAGE_BYTES}. */
    public static final String TOO_LARGE = "TOO_LARGE";

    /** Answer code when the vision service did not answer within {@link #SCAN_TIMEOUT_SECONDS}. */
    public static final String TIMEOUT = "TIMEOUT";

    /** How often the screenshot is sent before the person is asked to try again. */
    public static final int ATTEMPTS = 3;

    /**
     * How tall a picture may be before it is cut into slices, used when the picture does not say how
     * tall its blocks are. A cut through the middle of an order block leaves its title in one slice and
     * its articles in the next, and the model then hangs those articles on the block that follows (a
     * page of five orders came back as four right ones and one written out of two).
     *
     * <p>The walls of a shop page leave 30 to 40 pixels of white both inside a block and between two
     * of them, so no cut can be trusted to fall between blocks. The height of a block is therefore
     * measured on the picture itself ({@link #blockHeightOf}) and one slice holds two of them; this
     * value is only the answer for a picture whose blocks cannot be told apart (Step 148).
     */
    public static final int SLICE_HEIGHT = 900;

    /**
     * A picture up to this height is sent in one piece: cutting it in two would cost two calls and
     * lose the look at the whole page, which is what a short page needs.
     */
    public static final int ONE_CALL_HEIGHT = 2400;

    /** How tall a slice may get: one block may not be shorter than this, nor a slice longer. */
    private static final int MIN_SLICE_HEIGHT = 600;
    private static final int MAX_SLICE_HEIGHT = 2000;

    /** How far the cut may slide to find a row with no text, and how many pixels one row is sampled at. */
    public static final int SLICE_SEARCH = 180;
    private static final int SLICE_SAMPLE = 4;

    /**
     * How much two slices share. An order block is 300 to 500 pixels tall, so a block that the cut
     * goes through is still whole inside the next slice: with no overlap its title landed in one
     * slice and its articles in the next, and when the model did not hang the rows on the block above
     * that order was gone (production lost the fourth block of a page this way, Step 130). The rows
     * the overlap reads twice are merged by the order number and cut back to one copy by
     * {@code OirOrders#tidyOrder}.
     */
    public static final int SLICE_OVERLAP = 500;

    /**
     * Slices that are read at the same time. Ten turns the nineteen slices of a long page into two
     * waiting rounds instead of four, which is what the wait of such a page is made of; a page of five
     * orders has fewer slices than this and is unaffected. Raising it further buys nothing until the
     * service starts answering slower or refusing (Step 195).
     */
    public static final int MAX_PARALLEL = 10;

    /**
     * How wide the strip with the order numbers is, in pixels of the picture: the page prints its number
     * and shop on the left, and a strip 1700 wide is handed to the model at about the size it was
     * captured, while the whole slice of 3434 has to be scaled down (Step 156).
     */
    public static final int STRIP_WIDTH = 1700;

    /**
     * How large the product picture of a receipt line is kept, in pixels: the page prints it about 250
     * wide, and 160 is enough to tell one reel from another while staying a few kilobytes.
     */
    public static final int SHOT_SIDE = 160;

    /** How many characters a key of the model holds: an {@code id} field of the data model. */
    public static final int MAX_ID_LENGTH = 20;

    /**
     * The orange of the 再买一单 button of a shop order block. Every block of a Taobao / 天猫 list
     * prints one, so the blocks can be counted in the picture and the cuts put between them instead
     * of at a fixed height (Step 139).
     */
    private static final int BUTTON_RED = 200;
    private static final int BUTTON_GREEN_LOW = 50;
    private static final int BUTTON_GREEN_HIGH = 140;
    private static final int BUTTON_BLUE = 90;

    /** Two runs of orange closer than this belong to one button (its face and its label). */
    private static final int BUTTON_RUN_GAP = 90;

    /**
     * How far above its own button a block prints its header line (订单号 / 下单时间 / 店铺名称 / 实付款).
     * Every cut is laid on such a header row, so each slice starts at a block and goes on to the header
     * of a later one: a cut anywhere else may fall inside a block, and the blocks of one page are 300 to
     * 2700 pixels tall - a thirteen row order split in two is answered with a few rows of each half, and
     * the money of the two halves adds up to neither the paid total nor anything else (Step 159).
     */
    public static final int BUTTON_TO_HEADER = 150;

    /**
     * How wide a band of empty rows has to be before it is taken for the space below the order list
     * instead of the space between two articles of one order, and how much wider than the widest band
     * inside the list it has to be.
     *
     * <p>The page prints 30 to 80 empty rows between the articles of one order (measured: 56 to 78 on
     * the page of sixteen orders), and 500 and more between the last order and the cards of 猜你喜欢
     * below it. Cutting the tail at a fixed one block below the last button instead cut through the
     * last order whenever that order was tall: a ten article order (20.00 yuan) had only its header and
     * two rows inside the picture, and the model filled the rest in from the paid total - once with the
     * right ten rows, twice with one or two (Step 161). Both a floor and the comparison are needed: a
     * page that prints its articles far apart (180 empty rows between two of them) must not be cut at
     * that spacing either.
     */
    public static final int MIN_TAIL_GAP = 300;

    private static final int TAIL_GAP_RATIO = 2;

    /**
     * How many empty rows make a real gap between two things on the page. Used for the cut below the
     * last picture of the last block: there the question is not "is this the feed" but "has the last
     * order run out", and the answer is the first gap after its last row.
     */
    private static final int ROW_GAP_ROWS = 60;


    /** A row of the page counts as empty when fewer samples of it are dark than this. */
    private static final int BLANK_ROW_DARK = 3;

    /**
     * How often one slice is sent. A slice holds one or two order blocks and its money is allowed not
     * to add up (an order may be cut in two), so there is much less to go wrong than on a whole page.
     */
    public static final int SLICE_ATTEMPTS = 3;

    /**
     * The reason a picture is asked again because its money does not add up: the only one that can be
     * put into words for the model (how much is missing), so the second read is asked with it.
     */
    private static final String MONEY_REASON = "the goods of an order and its freight are not its paid total";

    /**
     * Why a slice is asked again when it prints more article pictures than the answer has rows: the
     * number is named in the next request, which is what makes a model go back over a long block.
     */
    private static final String COUNT_REASON = "the slice prints more article pictures than were read";

    /**
     * Why a slice is asked again when every row of a block came back with the same part number: a block
     * of different articles that all carry one code imports as one part, and the next request names the
     * grey spec line each row prints of its own (Step 193).
     */
    private static final String CODES_REASON = "every row of a block was answered with one part number";

    /**
     * How many rows an answer may be short of the pictures the block prints and still be asked again:
     * a row or two is a model that stopped early, ten is a page that prints two pictures beside a row
     * and tells nothing about the rows.
     */
    private static final int MAX_ARTICLES_MISSING = 3;

    /**
     * From how many article rows a block is worth reading in two halves when the count does not work
     * out: a block of a dozen rows is what a model still reads in one picture.
     */
    public static final int SPLIT_FROM_ARTICLES = 12;

    /** How many rows the two halves of a block read twice, so the seam never falls between two rows. */
    private static final int HALF_OVERLAP_ROWS = 80;

    /** How far below the top of a block its header line (order number, date, shop, paid) may sit. */
    private static final int MAX_BLOCK_HEAD_ROWS = 400;

    /** Seconds one call to the vision service may take, with room on top of the socket timeouts. */
    public static final int SLICE_WAIT_SECONDS = 240;

    /**
     * How long the whole recognition of one screenshot may take, in seconds.
     *
     * <p>A page of 26 orders is read in about 20 seconds (26 slices, six at a time), so two minutes is
     * ten times the room a page needs - but a service that is having a bad day, or one that answers
     * nothing at all, must not keep the page waiting: the person is left staring at "正在识别…" with no
     * way to know whether anything is happening (that is how this limit came to be). When it runs out,
     * the slices that are still out are dropped and the answer says so.
     */
    public static final int SCAN_TIMEOUT_SECONDS = 120;

    /**
     * How long the first retry waits before asking again, in milliseconds. The wait doubles with every
     * further attempt (700, 1400), so a service that is having a bad minute is not hit three times in
     * a row - the first wait alone was enough for a hiccup.
     */
    private static final long RETRY_PAUSE_MILLIS = 700L;

    /**
     * Room for the answer. A thinking model spends part of it on its reasoning, so a small budget
     * ends with an empty answer: 4000 was not enough for a screenshot holding three orders.
     */
    public static final int MAX_TOKENS = 8000;

    /** A 1x1 png, used by the connection test so a vision endpoint has something to look at. */
    private static final String TINY_PNG =
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==";

    /**
     * The "numbers only" question, asked about a picture cropped to the left of the page: the order
     * number, the date and the shop of every block, and nothing else. Kept as short as it can be - the
     * answer is one line per block, so a thinking model has nothing to weigh.
     */
    public static final String HEADER_PROMPT = "Read only the header line of every order block in this"
            + " picture (the line with 订单号 / 下单时间 / 店铺名称 above the articles) and answer with"
            + " one JSON object only, no prose:"
            + " {\"blocks\":[{\"orderNo\":\"\",\"orderDate\":\"yyyy-MM-dd\",\"shop\":\"\"}]}"
            + " One element per block, top to bottom, in the order the blocks are printed. orderNo is the"
            + " order number exactly as printed after 订单号, digit by digit - copy it, never guess the"
            + " digits from the other numbers of the page. shop is the shop name only, without the"
            + " platform word. Do not read the articles, the money or the buttons.";

    /**
     * What the model is asked for: one JSON object, nothing else.
     *
     * <p>It is worded as decisions rather than as questions: the earlier, longer version made the
     * model deliberate (the shop name with or without 天猫, the struck through price), and a thinking
     * model that deliberates spends its whole output budget on the reasoning and answers nothing.
     */
    public static final String PROMPT = "Read this screenshot of shop orders (淘宝 / 天猫) and answer with one"
            + " JSON object only, no prose, no explanation:"
            + " {\"orderCount\":\"1\",\"orders\":[{\"orderNo\":\"\",\"shop\":\"\",\"platform\":\"\","
            + "\"orderDate\":\"\",\"paidTotal\":\"\",\"freight\":\"\",\"discount\":\"\","
            + "\"refunded\":\"\",\"closed\":\"\",\"rowCount\":\"\",\"items\":[{\"code\":\"\",\"name\":\"\",\"qty\":\"\",\"price\":\"\",\"times\":\"\",\"amount\":\"\"}]}]}"
            + " orderCount is how many order blocks the picture shows; orders has one element per"
            + " block, top to bottom, and items has one element per article of that order - two"
            + " products are two elements, never one. rowCount is how many article rows that block"
            + " prints, so six colours of the same board is rowCount 6 and six elements."
            + " Answer the items in the order the page prints them, top to bottom: the first item is"
            + " the row whose product picture is at the top of the block, because the pictures are"
            + " handed to the rows in that order."
            + " One block carries one order number, so the same number never comes back on two elements:"
            + " the articles of a block all go into that block's items."
            + " orderNo is the order number of that block (after 订单号, or the number the page prints"
            + " for it). shop is the shop name only, without the platform word. platform is which"
            + " shop platform the page belongs to: 淘宝, 天猫, or empty when nothing"
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
            + " packs after x (1.20 with x2 is 2.40), never the price of a single piece nor the price of"
            + " one pack: the pack size only changes the quantity, so ¥2.00 with x2 is 4.00 however many"
            + " pieces one pack holds. Whenever the number after x is larger than 1 the money of that row is"
            + " larger than the price printed beside it - ¥13.68 with x2 is 27.36, ¥7.01 with x4 is 28.04 -"
            + " and only a row printed with x1 is the printed price itself. price and times are those two"
            + " printed numbers on their own: price is the money beside the row without the ¥, times is the"
            + " number after x, and amount is price times times. One element per"
            + " article - a block that shows two products gets two elements, never one. Never use the"
            + " struck through price. code is the short model or spec line printed under the title"
            + " (2.0mm单排8P(共8P), 502035(350mah), 30K) and name is the title itself; when the article"
            + " prints neither, use the model number of the title, and never leave code empty. A title that"
            + " lists many values of one product (0603 0805 1206贴片电阻 0R欧姆 1 2K 2.2 3.3 4.7K 5.1R 100K 1M)"
            + " is the name, and the one value the grey line prints under it (2K （一件100只）;1%精度) is the"
            + " code: never write the title, and never the title glued to the spec line, into code. When several"
            + " rows print exactly the same title (one board sold in six colours) they are still one element"
            + " each, never one merged element, and the grey line that tells them apart (白色 / 蓝色 / 绿色)"
            + " goes into code so the six rows do not all share one code. The pack"
            + " size is never part of the code: the spec"
            + " line 30K（一件100只）;1%精度;0603贴片电阻 gives the code 30K, not the whole line; a pack size"
            + " printed inside the title counts too, so 面包板 … 带扣（5个） with x1 is 5 pieces, and so does"
            + " a piece count printed bare at the end of the title: IC测试板 … 5只 with x3 is 15 - but a set"
            + " sold as one article (brackets saying 套装 / 组合 / 套餐) does not: 实验板（4块组合套装）"
            + " with x1 is 1 set, so qty is 1."
            + " A spec line that opens with a package size (0201 / 0402 / 0603 / 0805 / 1206 / 1210) is never"
            + " answered with that size alone: the size is what the fifteen rows of a capacitor list have in"
            + " common, so 0805 680PF 681K 10% 50V 50个 is the code 680PF 681K and 0805 33NF 333K 10% 50V"
            + " 50个 is 33NF 333K - one row of that list, one value, never 0805 for every row."
            + " Read every row's money as printed (the struck through price is never used): never change a"
            + " row's amount to make the rows add up to paidTotal, and never write paidTotal as a row of"
            + " its own - when the rows do not add up, answer the rows you can read as they are."
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
    public static final String SHORT_PROMPT = "Read the shop orders (淘宝 / 天猫) in this"
            + " picture. Answer with one JSON object only, no prose:"
            + " {\"orderCount\":\"1\",\"orders\":[{\"orderNo\":\"\",\"shop\":\"\",\"platform\":\"\","
            + "\"orderDate\":\"\",\"paidTotal\":\"\",\"freight\":\"\",\"discount\":\"\","
            + "\"refunded\":\"Y or N\",\"closed\":\"Y or N\",\"rowCount\":\"\","
            + "\"items\":[{\"code\":\"\",\"name\":\"\",\"qty\":\"\",\"price\":\"\",\"times\":\"\",\"amount\":\"\"}]}]}"
            + " orderCount = how many order blocks you see; one element per block, top to bottom."
            + " One block carries one order number, so the same number never comes back twice: the"
            + " articles of a block all go into that block's items."
            + " rowCount = how many article rows that block prints (six colours = 6), and items has"
            + " one element per printed row, in the order the page prints them (the first item is the"
            + " top row of the block)."
            + " platform = 淘宝 / 天猫. shop without the platform word."
            + " paidTotal = 实付款 (or 合计). freight = the shipping money (含运费 / 运费)."
            + " discount = the red packet / coupon / 满减 taken off the order, 0 when none."
            + " refunded = Y only when it says 退款成功 / 已退款 / 退款中 / 退货退款 (never for 退货宝 /"
            + " 7天无理由退货 - that is a shop policy). closed = Y when the badge says 交易关闭 /"
            + " 已取消 / 交易失败, else N; a closed order is not a refund, so its refunded is N."
            + " items = one element per article,"
            + " never two products in one element: their amounts add up to paidTotal minus freight;"
            + " rows that print the same title are still one element each, with the colour in code;"
            + " code = the short model, or the value the grey spec line starts with (2K), never the title,"
            + " never the whole spec line; when the line opens with a package size (0603 / 0805) the code"
            + " is what follows it (0805 680PF 681K 10% 50V → 680PF 681K), never the size on its own, which"
            + " every row of that list shares; name = the title itself;"
            + " qty = the number after x times the pack size printed in the grey spec line or inside the"
            + " title"
            + " (一件100只 → x1 is 100, (50只) → x4 is 200, 带扣（5个） → x1 is 5, 测试板 5只 → x3 is 15); a set"
            + " sold as one article (套装 / 组合 / 套餐 in the brackets) is qty = the number after x"
            + " itself, so （4块组合套装） with x1 is 1; price = the money printed beside that row without the"
            + " ¥, times = the number printed after x, amount = price times times (¥13.68 with x2 is 27.36,"
            + " and x larger than 1 always means more money than the price beside it); a block with one"
            + " article whose page prints 实付款 has amount = paidTotal minus freight. Never bend"
            + " a row's amount so the rows add up, and never make"
            + " paidTotal an item of its own. Pictures of other products below the order list (猜你喜欢 / 推荐 /"
            + " 广告) are not orders and are not items; the articles of one block are never items of the"
            + " block under it. No explanation.";

    private OirScan() {
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
    public static String imageSizeProblem(int bytes) {
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
        String endpoint = text(MfgSettingsServices.setting(delegatorOf(request), VISION_ENDPOINT));
        String model = text(MfgSettingsServices.setting(delegatorOf(request), VISION_MODEL));
        String key = text(MfgSettingsServices.setting(delegatorOf(request), VISION_KEY));
        if (endpoint == null || model == null || key == null) {
            return answer(response, false, missingSetting(), null, UNCONFIGURED);
        }
        endpoint = OirOrders.normalizeVisionEndpoint(endpoint);
        String contentType = text(parameters.get("_image_contentType"));
        if (contentType == null) {
            contentType = "image/png";
        }
        // the product pictures of the page are cut out before the model is asked anything: the page
        // itself is far too big to keep while ten calls run, a handful of thumbnails is not
        long started = System.currentTimeMillis();
        long deadline = started + SCAN_TIMEOUT_SECONDS * 1000L;
        String scanId = "SCAN-" + started;
        List<int[]> shotBoxes = new ArrayList<>();
        List<byte[]> shotThumbs = new ArrayList<>();
        fillShots(image, shotBoxes, shotThumbs);
        // where the page's grid of recommendations starts: its cards stand side by side, an order's
        // articles one under the other, so the grid can be told apart from the orders (Step 179q)
        int stripTop = OirShots.stripStartOf(shotBoxes);
        long picturesDone = System.currentTimeMillis();
        int[] nextShot = new int[] {1};
        // the pictures a slice printed beside its rows, kept for the warning written after the rows are
        // tidied: written earlier it counts rows that are dropped again and tells the person the page
        // printed sixteen articles where it printed fifteen (Step 193)
        java.util.Map<Map<String, String>, Integer> picturesOf = new java.util.IdentityHashMap<>();
        String reply = null;
        String transportProblem = null;
        List<Map<String, String>> orders = Collections.emptyList();
        int seen = 0;
        // a long page (several order blocks one under the other) is cut into overlapping slices: one
        // small picture per slice is read far more reliably than five blocks at once, and the rows of
        // an order that straddles two slices are joined afterwards by its number
        Slices sliced = slicesOfRanges(image, stripTop, shotBoxes);
        List<byte[]> slices = sliced.pictures;
        String sliceNote = "";
        boolean timedOut = false;
        if (slices.size() == 1) {
            // one call, but still under the deadline: a service that accepts the request and then says
            // nothing would otherwise hold the page until the socket gives up
            ExecutorService one = Executors.newFixedThreadPool(1);
            final byte[] onlyPicture = slices.get(0);
            final byte[] onlyStrip = sliced.strips.isEmpty() ? null : sliced.strips.get(0);
            final String pageEndpoint = endpoint;
            final String pageModel = model;
            final String pageKey = key;
            final String pageType = contentType;
            Future<Read> task = one.submit(new Callable<Read>() {
                @Override
                public Read call() {
                    Read read = readPicture(onlyPicture, pageEndpoint, pageModel, pageKey, pageType,
                            false, 0, 0);
                    // the numbers read from the whole page, and a second reading from the narrow strip:
                    // they are compared after the join, where the length this page prints at is known
                    OirOrders.noteTheSecondRead(read.orders,
                            readHeaders(onlyStrip, pageEndpoint, pageModel, pageKey, "image/png"));
                    return read;
                }
            });
            try {
                Read read = task.get(Math.min(SLICE_WAIT_SECONDS, secondsLeft(deadline)),
                        TimeUnit.SECONDS);
                orders = read.orders;
                reply = read.reply;
                seen = read.seen;
                transportProblem = read.transportProblem;
            } catch (InterruptedException | ExecutionException | TimeoutException e) {
                Debug.logError(e, "The screenshot was not read in time", MODULE);
                transportProblem = e.getMessage();
                timedOut = true;
            } finally {
                one.shutdownNow();
            }
            handPicturesOver(shotBoxes, shotThumbs,
                    new int[] {0, shotsHeight(shotBoxes)}, orders, request, scanId, nextShot, picturesOf);
        } else {
            // the slices are read side by side: four small calls in parallel take about as long as
            // one, while reading them one after the other made a five order page wait minutes
            List<List<Map<String, String>>> answers = new ArrayList<>();
            // how many blocks each picture said it shows: a model that splits one block into two
            // objects says one and answers two, which tells the join those two are one order
            List<Integer> blocksSaid = new ArrayList<>();
            // how many articles each slice prints, kept beside the futures: a block that is still short
            // of its rows after the count was named is read again in two halves below
            List<Integer> articlesBySlice = new ArrayList<>();
            ExecutorService pool = Executors.newFixedThreadPool(Math.min(MAX_PARALLEL, slices.size()));
            List<Future<Read>> running = new ArrayList<>();
            final String sliceEndpoint = endpoint;
            final String sliceModel = model;
            final String sliceKey = key;
            final String sliceType = contentType;
            for (int at = 0; at < slices.size(); at++) {
                final byte[] picture = slices.get(at);
                final byte[] strip = at < sliced.strips.size() ? sliced.strips.get(at) : null;
                // how many blocks that slice shows whole: what its answer should hold, and the only
                // way to see a block the model dropped (a slice cuts nothing else, so nothing else
                // notices)
                int[] range = sliced.ranges.get(at);
                int counted = blocksInside(sliced.buttons, range[0], range[1]);
                // how many articles the slice prints: one picture beside each row, so the pictures
                // inside its rows count the rows. A block of fifteen packs of capacitors came back
                // with fourteen of them, twice in a row, and the money alone did not make the model
                // go back over it (Step 183) - the count does. The pictures of the page's recommendation
                // cards below the list are not article rows, so they are left out where the page prints
                // them: they stand side by side in a grid, and the grid's top row is where the count
                // stops. The last slice is counted too - a block of eight articles came back as seven
                // with the money of prices, and nothing could tell the model it was a row short
                // (Step 190)
                int picturesEnd = stripTop > 0 && stripTop > range[0] && stripTop < range[1]
                        ? stripTop : range[1];
                int pictures = OirShots.indexesWithin(shotBoxes, range[0], picturesEnd).size();
                // the last block of the page has no button after it, so the blocks that stand whole in
                // the last slice count as none even though its block is all there: when that slice
                // prints article pictures, they are the rows of one block, and they are compared
                // (Step 191)
                final boolean lastSlice = at + 1 >= slices.size();
                final int blocksHere = counted == 0 && lastSlice && pictures > 0 ? 1 : counted;
                final int articlesHere = blocksHere == 1 ? pictures : 0;
                articlesBySlice.add(Integer.valueOf(articlesHere));
                running.add(pool.submit(new Callable<Read>() {
                    @Override
                    public Read call() {
                        Read read = readPicture(picture, sliceEndpoint, sliceModel, sliceKey,
                                sliceType, true, blocksHere, articlesHere);
                        OirOrders.noteTheSecondRead(read.orders,
                                readHeaders(strip, sliceEndpoint, sliceModel, sliceKey, "image/png"));
                        return read;
                    }
                }));
            }
            try {
                for (int at = 0; at < running.size(); at++) {
                    long left = deadline - System.currentTimeMillis();
                    if (left <= 0L) {
                        // the page has waited long enough: the slices still out are dropped, and the
                        // answer says how far it got
                        Debug.logInfo("The scan ran out of time after " + at + " of " + running.size()
                                + " slices, the rest is dropped", MODULE);
                        timedOut = true;
                        break;
                    }
                    Future<Read> future = running.get(at);
                    Read read;
                    try {
                        read = future.get(Math.min(SLICE_WAIT_SECONDS, secondsLeft(deadline)),
                                TimeUnit.SECONDS);
                    } catch (InterruptedException | ExecutionException | TimeoutException e) {
                        Debug.logError(e, "A slice of the screenshot was not read", MODULE);
                        transportProblem = e.getMessage();
                        if (e instanceof TimeoutException) {
                            timedOut = true;
                        }
                        answers.add(Collections.emptyList());
                        blocksSaid.add(Integer.valueOf(0));
                        continue;
                    }
                    if (read.reply != null) {
                        reply = read.reply;
                    }
                    int articlesHere = at < articlesBySlice.size()
                            ? articlesBySlice.get(at).intValue() : 0;
                    if (articlesHere >= SPLIT_FROM_ARTICLES
                            && printsMoreArticles(articlesHere, read.orders)) {
                        // a long block that stays short even after the count was named: read it in two
                        // halves, the lower one carrying the header of the block so it still says which
                        // order it belongs to, and add the rows of both together (Step 183)
                        read = readInHalves(slices.get(at), sliced.ranges.get(at), shotBoxes, read,
                                sliceEndpoint, sliceModel, sliceKey);
                        if (read.reply != null) {
                            reply = read.reply;
                        }
                    }
                    if (read.transportProblem != null) {
                        transportProblem = read.transportProblem;
                    }
                    answers.add(read.orders);
                    blocksSaid.add(Integer.valueOf(read.seen));
                    // the rows this slice was cut from: the pictures in them belong to the orders read
                    // from them, one each when the counts agree
                    int[] range = sliced.ranges.get(at);
                    handPicturesOver(shotBoxes, shotThumbs, range, read.orders, request, scanId,
                            nextShot, picturesOf);
                }
            } finally {
                // a slice whose call is still hanging is cancelled here, not left running: the page
                // has already waited for its answer, and a worker thread would otherwise keep the
                // pictures of this request alive until the socket times out on its own
                pool.shutdownNow();
                try {
                    pool.awaitTermination(5L, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            orders = OirOrders.mergeOrders(giveTheRowsAnOrder(answers), blocksSaid);
            seen = orders.size();
            sliceNote = "（长图分 " + slices.size() + " 段逐段识别）";
        }
        if (timedOut) {
            String timeoutNote = "（识别超时：一次最多等 " + SCAN_TIMEOUT_SECONDS + " 秒"
                    + (orders.isEmpty() ? "" : "，只读了 " + orders.size() + " 张单")
                    + "；可以再点一次，或者把截图裁成两张分开识别）";
            sliceNote = timeoutNote;
            if (orders.isEmpty()) {
                return answer(response, false, timeoutNote, null, TIMEOUT);
            }
        }
        if (orders.isEmpty()) {
            return answer(response, false, scanFailure(reply, transportProblem), null, NO_ORDERS);
        }
        // the pictures a slice printed beside its rows, kept for the warning below: the note has to be
        // written once the rows are tidied, or it counts rows that are dropped again and tells the
        // person the page printed sixteen articles where it printed fifteen (Step 193)
        // how many blocks the picture shows, told by the buttons and not by the model: a block keeps
        // its button whatever became of the deal (a closed one offers 再买一单 or 加入购物车 as well),
        // so this count can be held against the read - one order too many is a block read as two
        // (Step 153). Only a near miss is reported: a count that is far off is counting something
        // else on the page (a recommendation strip of twenty buttons) and says nothing about orders
        int blocks = blockCountOf(image);
        Debug.logInfo("The picture shows " + blocks + " order buttons, " + orders.size()
                + " orders were read", MODULE);
        // fewer blocks than orders: two of those rows are one block read twice (the join kept them
        // apart because a page never prints one number on two blocks), so the reads of one number are
        // folded back together - the count of the buttons is what makes that safe (Step 155)
        int read = orders.size();
        orders = OirOrders.foldExtraReads(orders, blocks);
        if (orders.size() != read) {
            Debug.logInfo("The reads of one order number were folded: " + read + " -> " + orders.size(),
                    MODULE);
        }
        // every order that was read twice keeps both numbers up to here: the one that has the length this
        // page prints at wins, two of the same length are left as the first read had them and the row
        // says both were seen
        int replaced = OirOrders.settleNumbers(orders);
        if (replaced > 0) {
            Debug.logInfo("The second reading of the order numbers replaced " + replaced, MODULE);
        }
        String countOff = blockCountNote(blocks, orders.size());
        String shortfall = seen > orders.size()
                ? "（截图上看上去有 " + seen + " 张单，这次只读出 " + orders.size()
                        + " 张，可能漏了；核对一下，也可以再点一次「识别并核对」）"
                : "";
        int refundedCount = 0;
        int closedCount = 0;
        for (Map<String, String> order : orders) {
            // a package description as the part number does not fit the code field: short it here
            // so the page already shows the code that will be stored
            OirOrders.tidyOrder(order);
            order.put("lines", OirOrders.fitOrderLines(order.get("lines")));
            // the pictures this block printed beside its rows, told against the rows that are left: a
            // different count means one of the two sides was read wrong, and only a person can settle
            // which (Step 193)
            Integer pictures = picturesOf.get(order);
            if (pictures != null) {
                String picturesOff = picturesShortfallNote(pictures.intValue(),
                        OirOrders.parseOrderArticleLines(order.get("lines")).size());
                if (picturesOff != null) {
                    addNote(order, picturesOff);
                }
            }
            if (OirOrders.isRefunded(order)) {
                // a refund is not booked in, but reading one is a guess like everything else: the model
                // took 「7天无理由退货」 and 「退货宝」 for a refund and blocked a good order for good.
                // So the order is only left out of this round, and the person puts a real refund on the
                // list with one click (Step 108).
                order.put("note", "显示已退款，这一次不入库；货确实到了就手工粘明细建单，"
                        + "确实退了就把它登记到「退款 / 关闭的单」里");
                refundedCount++;
            } else if (OirOrders.isClosed(order)) {
                // 交易关闭 means the deal never went through - the person confirmed it is usually an
                // order they never paid. Nothing was shipped, so booking it would invent stock; a
                // parcel that does turn up can still be typed in by hand (Step 109).
                order.put("note", "交易关闭：这张单没成交（一般是从没付款），这一次不入库；"
                        + "万一货真发过来了，就手工粘明细建单");
                closedCount++;
            } else {
                // freight is what the page says, but goods plus freight must come to what was paid
                String totals = OirOrders.checkOrderTotals(order);
                // and the rows the block prints have to be there: six colours of one board read as a
                // single item still adds up, so this one cannot be left to the money check
                String rowsOff = null;
                if (OirOrders.rowsMissing(order)) {
                    rowsOff = "截图印了 " + order.get("rowCount") + " 行明细，这次只读出 "
                            + OirOrders.parseOrderArticleLines(order.get("lines")).size()
                            + " 行，请对着截图核对（也可以再点一次「识别并核对」）";
                }
                String note = totals == null ? rowsOff
                        : (rowsOff == null ? totals : totals + "；" + rowsOff);
                if (note != null) {
                    // appended, not written over: the order may already carry what the second read of
                    // its number found (Step 193)
                    addNote(order, note);
                }
            }
        }
        // a block that carries exactly the articles and the money of another block was probably copied
        // from it while the page was read: say so, the money of a copy adds up and nothing else notices.
        // But a shop does sell the same part twice, and on the audited page two orders of 199.00 of the
        // same chip are really there - so the caution is only worth its noise when the buttons say the
        // picture holds fewer blocks than rows, or when the page does not say at all (Step 157)
        boolean mayHoldACopy = blocks <= 0 || orders.size() > blocks;
        Map<String, Map<String, String>> firstOfKind = new LinkedHashMap<>();
        Map<String, Map<String, String>> firstOfMoney = new LinkedHashMap<>();
        Map<String, Integer> firstOfMoneyAt = new LinkedHashMap<>();
        for (int i = 0; i < orders.size(); i++) {
            Map<String, String> order = orders.get(i);
            String content = OirOrders.orderContentKey(order);
            String money = OirOrders.moneyKeyOf(order);
            Map<String, String> other = content == null ? null : firstOfKind.get(content);
            Map<String, String> otherMoney = money == null ? null : firstOfMoney.get(money);
            Integer otherMoneyAt = money == null ? null : firstOfMoneyAt.get(money);
            if (content != null && other == null) {
                firstOfKind.put(content, order);
            }
            if (money != null && otherMoney == null) {
                firstOfMoney.put(money, order);
                firstOfMoneyAt.put(money, Integer.valueOf(i + 1));
            }
            String copied = null;
            if (other != null) {
                copied = "明细与单号 " + other.get("orderNo") + " 那张一模一样，可能识别时抄错了，请对着截图核对";
            } else if (otherMoney != null && otherMoneyAt != null) {
                // the codes and names two reads write differ now and then, so two rows of one printed
                // line do not always look alike: what matches is the quantity and the money
                copied = "明细和金额与第 " + otherMoneyAt + " 张单完全相同，可能把上一块的明细带下来了，请对着截图核对";
            }
            // the same part bought again on another day is not a copy: the page prints the day of
            // every block, so two of them carrying one order's articles and money on two days are two
            // purchases - a shop that sells PCB打样 twice three weeks apart looks exactly like a copy
            // otherwise (Step 199)
            if (copied != null && mayHoldACopy
                    && sameDay(order, other != null ? other : otherMoney)) {
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
        // the screenshot itself is kept under this id: the receipts made from it point at the picture
        // their numbers came from, and the product pictures sit beside it under the same id
        storeScreenshotOf(request, scanId, image, contentType);
        Debug.logInfo("One order scan took " + (System.currentTimeMillis() - started) + " ms: "
                + (picturesDone - started) + " ms to cut " + shotBoxes.size() + " product pictures, "
                + slices.size() + " slices at most " + MAX_PARALLEL + " at a time, " + orders.size()
                + " orders read", MODULE);
        return answer(response, true, message + sliceNote + shortfall + countOff, orders, null, scanId);
    }

    /**
     * Keeps the uploaded screenshot under the scan id the product pictures are already filed under.
     *
     * <p>Best effort on purpose: a picture that cannot be stored must not cost the person the read
     * that was just made - the orders are already parsed at this point.
     *
     * @param request the request, for the delegator
     * @param scanId the id of this scan
     * @param image the uploaded bytes
     * @param contentType what the browser said the bytes are
     * @return the scan id, {@code null} when the picture could not be kept
     */
    private static String storeScreenshotOf(HttpServletRequest request, String scanId, byte[] image,
            String contentType) {
        org.apache.ofbiz.entity.Delegator delegator = delegatorOf(request);
        if (delegator == null || image == null || image.length == 0) {
            return null;
        }
        String name = "order-scan-" + scanId + (contentType != null && contentType.contains("jpeg")
                ? ".jpg" : ".png");
        try {
            MfgAttachmentServices.storeScreenshot(delegator, MfgConstants.ATT_ORDER_SCAN, scanId, name,
                    contentType, image, "识图用的原图");
            return scanId;
        } catch (Exception e) {
            Debug.logError(e, "Could not keep the screenshot " + scanId, MODULE);
            return null;
        }
    }

    /**
     * Cuts the product pictures out of the page and scales each to a thumbnail, before the model is
     * asked anything: a page of 3255 by 16384 pixels takes a fifth of a gigabyte decoded, and holding it
     * through ten model calls would risk the instance for a handful of small pictures.
     *
     * <p>Best effort: a page whose pictures cannot be found simply hands over none, and the receipt
     * line keeps the whole screenshot instead.
     *
     * @param image the uploaded bytes
     * @param boxes receives the box of every picture, top to bottom
     * @param thumbs receives one small picture per box, in the same order
     */
    private static void fillShots(byte[] image, List<int[]> boxes, List<byte[]> thumbs) {
        BufferedImage page;
        try {
            page = ImageIO.read(new ByteArrayInputStream(image));
        } catch (IOException e) {
            Debug.logError(e, "Could not read the screenshot to find its product pictures", MODULE);
            return;
        }
        if (page == null) {
            return;
        }
        for (int[] box : OirShots.boxesOf(page)) {
            boxes.add(box);
            thumbs.add(OirShots.thumbnailOf(page, box, SHOT_SIDE));
        }
        Debug.logInfo("The page printed " + boxes.size() + " product pictures", MODULE);
    }

    /**
     * Hands the pictures of one slice to the articles read from it.
     *
     * <p>A block that bought two different things prints two pictures, one beside each article, and the
     * two are not the same (a reel of resistors next to a soldering iron). So a picture belongs to an
     * <b>article</b> and not to the order: the pictures of a slice are cut top to bottom and the articles
     * are read top to bottom, so article k takes picture k. Each order therefore receives {@code shots},
     * one id per article row, and {@code shot}, the first of them, for the small picture next to the row.
     *
     * <p>Only when the counts agree: if the page printed fewer pictures than there are articles (a white
     * part on a white card cannot be cut out) nothing is handed over for that slice unless the pictures at
     * least match the orders one to one - then each order keeps one picture, as it always did.
     *
     * @param boxes the boxes of every picture of the page
     * @param thumbs the thumbnail of each box
     * @param range the rows of the slice
     * @param orders the orders read from that slice, they receive {@code shot} and {@code shots} fields
     * @param request the request, for the delegator
     * @param scanId the id of this scan, the pictures are filed under it
     * @param nextShot how many pictures have been filed so far, one number in an array
     * @param picturesOf receives how many pictures the slice printed, for the warning written later
     */
    private static void handPicturesOver(List<int[]> boxes, List<byte[]> thumbs, int[] range,
            List<Map<String, String>> orders, HttpServletRequest request, String scanId, int[] nextShot,
            java.util.Map<Map<String, String>, Integer> picturesOf) {
        if (orders == null || orders.isEmpty() || boxes.isEmpty()) {
            return;
        }
        int articles = articlesOf(orders);
        List<Integer> mine = picturesForOrders(
                withoutStrips(OirShots.indexesWithin(boxes, range[0], range[1]), boxes),
                orders.size(), articles);
        if (mine.isEmpty()) {
            Debug.logInfo("The rows printed too few product pictures for " + orders.size()
                    + " orders to tell which picture belongs to which article, so none is handed over",
                    MODULE);
            return;
        }
        // one picture per article only when the page printed a picture for each of them; otherwise the
        // pictures can only be told apart one per order, as they always could
        boolean perArticle = articles > 0 && mine.size() >= articles;
        int at = 0;
        for (int i = 0; i < orders.size(); i++) {
            Map<String, String> order = orders.get(i);
            int take = perArticle ? Math.max(1, articlesOf(java.util.Collections.singletonList(order))) : 1;
            StringBuilder ids = new StringBuilder();
            for (int k = 0; k < take && at < mine.size(); k++) {
                String shotId = storeOne(thumbs, mine.get(at), request, scanId, nextShot);
                if (shotId == null) {
                    // no room left in the id for another picture, or nothing to store: the rest of the
                    // page keeps the whole screenshot, which is honest, instead of sharing an id
                    return;
                }
                at++;
                if (ids.length() > 0) {
                    ids.append(',');
                }
                ids.append(shotId);
            }
            if (ids.length() == 0) {
                continue;
            }
            String all = ids.toString();
            order.put("shots", all);
            order.put("shot", all.indexOf(',') < 0 ? all : all.substring(0, all.indexOf(',')));
        }
        // the page prints one picture beside each article, so a different count means one of the two
        // sides was read wrong: the person is told, instead of trusting a picture that may sit beside
        // the wrong part. The counts are remembered and told after the rows were tidied, because the
        // tidy pass may drop a duplicated row and the warning would then name a wrong count
        picturesOf.put(orders.get(0), Integer.valueOf(mine.size()));
    }

    /**
     * The warning to show when the page printed a different number of pictures than the orders have
     * articles.
     *
     * <p>Two cases produce it: an article whose picture could not be cut out (a white part on a white
     * card), and an answer that merged two articles into one row or split one into two. Either way the
     * pictures may not sit beside the part they belong to, which is something only a person can settle.
     *
     * @param pictures how many pictures were cut out of the slice
     * @param articles how many article rows were read from it
     * @return the warning, or {@code null} when the two agree (or there is nothing to compare)
     */
    public static String picturesShortfallNote(int pictures, int articles) {
        if (articles <= 0 || pictures >= articles) {
            return null;
        }
        return "页面上印了 " + pictures + " 张商品图，却读出 " + articles + " 条明细，图和明细可能对不上，请核对";
    }

    /**
     * Adds one doubt to an order, keeping what the reading rules already said.
     *
     * @param order the order map
     * @param note the warning to add
     */
    public static void addNote(Map<String, String> order, String note) {
        if (order == null || note == null || note.isEmpty()) {
            return;
        }
        String seen = order.get("note");
        order.put("note", seen == null || seen.isEmpty() ? note : seen + "；" + note);
    }

    /**
     * The pictures of a slice that can belong to its articles: the ones printed one above the other.
     *
     * <p>A block ends with the page's 猜你喜欢 grid, which prints three or four cards side by side and
     * whose titles look exactly like article titles. Those cards share their rows, which an article's
     * picture never does, so the first picture that shares its rows with the next one marks the strip -
     * and nothing from there on belongs to an article (Step 179q).
     *
     * @param pictures the places of the pictures in the slice, top to bottom
     * @param boxes the boxes of every picture of the page
     * @return the places up to the strip, in the same order
     */
    public static List<Integer> withoutStrips(List<Integer> pictures, List<int[]> boxes) {
        List<Integer> kept = new ArrayList<>();
        if (pictures == null || boxes == null) {
            return kept;
        }
        for (int i = 0; i < pictures.size(); i++) {
            int place = pictures.get(i).intValue();
            if (i + 1 < pictures.size()
                    && OirShots.sharesRows(boxes.get(place), boxes.get(pictures.get(i + 1).intValue()))) {
                break;
            }
            kept.add(pictures.get(i));
        }
        return kept;
    }

    /**
     * The number of article rows the orders of one slice print.
     *
     * <p>Used for the pictures: a block that bought two different things prints two, one beside each
     * article, so the pictures are handed to the article rows and not to the orders.
     *
     * @param orders the orders read from a slice
     * @return how many pictures the page should print for them
     */
    public static int articlesOf(List<Map<String, String>> orders) {
        int articles = 0;
        for (Map<String, String> order : orders) {
            articles += OirOrders.parseOrderArticleLines(order == null ? null : order.get("lines")).size();
        }
        return articles;
    }

    /**
     * Files one thumbnail and says under which id.
     *
     * @param thumbs the thumbnails of the page
     * @param place which picture of the page
     * @param request the request, for the delegator
     * @param scanId the id of this scan
     * @param nextShot how many pictures have been filed so far
     * @return the id, {@code null} when it could not be filed
     */
    private static String storeOne(List<byte[]> thumbs, Integer place, HttpServletRequest request,
            String scanId, int[] nextShot) {
        byte[] thumb = thumbs.get(place.intValue());
        if (thumb == null || thumb.length == 0) {
            return null;
        }
        String shotId = shotIdOf(scanId, nextShot[0]);
        if (shotId == null) {
            Debug.logInfo("The scan id has no room for picture " + nextShot[0]
                    + ", the rest of the page keeps the whole screenshot", MODULE);
            return null;
        }
        if (!storeShot(request, shotId, thumb)) {
            return null;
        }
        nextShot[0]++;
        return shotId;
    }

    /**
     * Which of the pictures of a slice belong to its orders.
     *
     * <p>A slice prints one picture per article, so when the counts agree every picture is handed over,
     * one after the other. Three things make them disagree:
     * <ul>
     * <li>the page prints more pictures than there are articles - its 猜你喜欢 / 推荐常看商品 strip sits
     *     inside the block, with a card of advertisements that has a picture of its own. The strip comes
     *     after the articles, so the pictures nearest the top of the slice are the articles' (a real page
     *     of 26 orders printed 27 pictures and lost the first order's picture to the strip);</li>
     * <li>fewer pictures than articles, but as many pictures as orders - an article whose picture could
     *     not be cut out (a white part on a white card). Then one picture per order is still right;</li>
     * <li>fewer pictures than orders - a block the model read twice. There is no way to tell which is
     *     which, and a picture against the wrong part is worse than no picture, so none is handed over.</li>
     * </ul>
     *
     * @param pictures the places of the pictures in the slice, top to bottom
     * @param orders how many orders were read from the slice
     * @param articles how many article rows those orders print
     * @return the places to hand over, in the same order; empty when nothing should be
     */
    public static List<Integer> picturesForOrders(List<Integer> pictures, int orders, int articles) {
        if (pictures == null || orders <= 0 || pictures.size() < orders) {
            return new ArrayList<>();
        }
        int wanted = Math.max(orders, articles);
        return new ArrayList<>(pictures.subList(0, Math.min(wanted, pictures.size())));
    }

    /**
     * Which of the pictures of a slice belong to its orders, one per order.
     *
     * @param pictures the places of the pictures in the slice, top to bottom
     * @param orders how many orders were read from the slice
     * @return the places to hand over, in the same order; empty when nothing should be
     */
    public static List<Integer> picturesForOrders(List<Integer> pictures, int orders) {
        return picturesForOrders(pictures, orders, orders);
    }

    /**
     * The seconds left until the deadline of this scan, at least one.
     *
     * @param deadline when the scan has to be answered, in milliseconds
     * @return how long a call may still take
     */
    public static long secondsLeft(long deadline) {
        long left = (deadline - System.currentTimeMillis()) / 1000L;
        return left < 1L ? 1L : left;
    }

    /**
     * The id one product picture of a scan is filed under, or {@code null} when no more fit.
     *
     * <p>Why this is not just {@code scanId + "-" + number}: the key of an attachment is an
     * {@code id}, which holds 20 characters, and a scan id is 18 (Step: "SCAN-" and the milliseconds).
     * With a dash and a two digit number that is 21 - Derby shortens it on the way in, so pictures 10 and
     * up were filed under the id of picture 1, and every receipt line past the tenth showed the first
     * product of the page (found on a real page of 26 orders).
     *
     * @param scanId the id of the scan
     * @param number which picture of the scan, counting from 1
     * @return the id, {@code null} when it would not fit or the arguments make no sense
     */
    public static String shotIdOf(String scanId, int number) {
        if (scanId == null || scanId.isEmpty() || number <= 0) {
            return null;
        }
        String id = scanId + (number < 10 ? "0" + number : String.valueOf(number));
        return id.length() > MAX_ID_LENGTH ? null : id;
    }

    /**
     * Keeps one product picture. Best effort, like the screenshot itself: a picture that cannot be
     * kept must not cost the person the read.
     *
     * @param request the request, for the delegator
     * @param shotId the id the picture is filed under
     * @param data the bytes of the small JPEG
     * @return whether it was kept
     */
    private static boolean storeShot(HttpServletRequest request, String shotId, byte[] data) {
        org.apache.ofbiz.entity.Delegator delegator = delegatorOf(request);
        if (delegator == null) {
            return false;
        }
        try {
            MfgAttachmentServices.storeScreenshot(delegator, MfgConstants.ATT_ORDER_SCAN, shotId,
                    "order-picture-" + shotId + ".jpg", "image/jpeg", data, "订单商品图");
            return true;
        } catch (Exception e) {
            Debug.logError(e, "Could not keep the product picture " + shotId, MODULE);
            return false;
        }
    }

    /** The last row a picture sits at, or 0 when there is none: the whole page is one slice then. */
    private static int shotsHeight(List<int[]> boxes) {
        int bottom = 0;
        for (int[] box : boxes) {
            bottom = Math.max(bottom, box[3]);
        }
        return bottom + 1;
    }

    /**
     * Says what the button count makes of the number of orders read, in the words the page shows. Only
     * a near miss is worth telling: a count far from the read is counting something else on the page
     * (a recommendation strip of twenty buttons) and would only worry the person.
     *
     * @param blocks how many order buttons the picture shows, {@code 0} when the page does not say
     * @param read how many orders came back
     * @return the note to add to the message, empty when there is nothing to say
     */
    public static String blockCountNote(int blocks, int read) {
        if (blocks <= 0 || blocks == read || Math.abs(blocks - read) > 2) {
            return "";
        }
        if (read > blocks) {
            return "（图上有 " + blocks + " 个「再买一单」，这次读出 " + read + " 张单；多出来的 "
                    + (read - blocks) + " 张可能是同一块被拆开了，核对一下再导入）";
        }
        return "（图上有 " + blocks + " 个「再买一单」，这次只读出 " + read + " 张单，可能漏了 "
                + (blocks - read) + " 张，核对一下）";
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
     * How many order blocks a slice shows whole, told by the buttons of the page: a block starts at its
     * header (a fixed number of rows above its own button) and ends where the next block's header
     * starts, so a block is whole in a slice when both rows are inside it.
     *
     * @param buttons the top row of every button of the list, top to bottom
     * @param top the first row of the slice
     * @param bottom the last row of the slice
     * @return how many blocks the slice shows whole
     */
    public static int blocksInside(List<Integer> buttons, int top, int bottom) {
        int inside = 0;
        if (buttons == null) {
            return 0;
        }
        for (int i = 0; i < buttons.size(); i++) {
            int header = buttons.get(i).intValue() - BUTTON_TO_HEADER;
            if (header < top) {
                continue;
            }
            // the last block of the page has no button below it to end it, so it counts only when its
            // own rows are well inside the slice
            int end = i + 1 < buttons.size()
                    ? buttons.get(i + 1).intValue() - BUTTON_TO_HEADER
                    : buttons.get(i).intValue() + SLICE_HEIGHT;
            if (end <= bottom) {
                inside++;
            }
        }
        return inside;
    }

    /**
     * Asks one picture for the header line of every order block and nothing else: the order number, the
     * date it was placed and the shop. Used on a picture cropped to the left of the page, which comes
     * back to the model far larger than the whole slice does, because a nineteen digit number is the one
     * field a model fills in from the shape of its neighbours (a page of 23 orders came back with
     * {@code 3071881380774500570} where the page prints {@code 3102077787932500570}).
     *
     * @param strip the picture cropped to the numbered part of the page
     * @param endpoint the vision endpoint
     * @param model the model name
     * @param key the api key
     * @param contentType the content type of the picture
     * @return one header per block, top to bottom; empty when the call failed or answered nothing
     */
    private static List<Map<String, String>> readHeaders(byte[] strip, String endpoint, String model,
            String key, String contentType) {
        if (strip == null) {
            return Collections.emptyList();
        }
        byte[] picture = strip;
        if (picture.length > MAX_PAYLOAD_BYTES) {
            byte[] jpeg = jpegOf(picture);
            if (jpeg != null && jpeg.length < picture.length) {
                picture = jpeg;
                contentType = "image/jpeg";
            }
        }
        try {
            String reply = askVision(endpoint, model, key, picture, contentType, HEADER_PROMPT);
            List<Map<String, String>> headers = OirOrders.headerListFrom(reply);
            Debug.logInfo("The header line read gave " + headers.size() + " numbers", MODULE);
            if (headers.isEmpty()) {
                Debug.logInfo("The header line answer could not be read: " + reply, MODULE);
            }
            return headers;
        } catch (IOException e) {
            // the numbers of the main read stand as they are: a picture that cannot be asked a second
            // time is not a reason to lose the scan
            Debug.logError(e, "Could not read the order numbers a second time", MODULE);
            return Collections.emptyList();
        }
    }

    /**
     * The columns a page prints in: a shop page carries an empty margin on each side, and cutting it
     * away makes every letter - the nineteen digits of an order number above all - that much bigger
     * once the vision service scales the picture down. The audited page prints between 60 and 2620 of
     * its 3434 pixels, so a fifth of the picture was margin.
     *
     * @param image the picture
     * @param top the first row to look at
     * @param bottom the row after the last one to look at
     * @return {@code {left, right}} of what is printed, {@code {0, width - 1}} when the picture looks
     *         printed all over
     */
    public static int[] contentColumns(BufferedImage image, int top, int bottom) {
        int width = image.getWidth();
        int[] all = new int[] {0, width - 1};
        if (image == null || width < 40) {
            return all;
        }
        int step = Math.max(2, width / 400);
        int first = -1;
        int last = -1;
        for (int x = 0; x < width; x += step) {
            boolean inked = false;
            for (int y = Math.max(0, top); y < Math.min(bottom, image.getHeight()); y += 6) {
                int rgb = image.getRGB(x, y);
                int r = (rgb >> 16) & 0xFF;
                int g = (rgb >> 8) & 0xFF;
                int b = rgb & 0xFF;
                if ((r + g + b) / 3 <= 200) {
                    inked = true;
                    break;
                }
            }
            if (inked) {
                if (first < 0) {
                    first = x;
                }
                last = x;
            }
        }
        // a margin worth cutting away has to be a real one, or a page printed edge to edge would lose
        // its own border
        if (first < 0 || first < width / 50 || width - last < width / 50) {
            return all;
        }
        return new int[] {Math.max(0, first - 8), Math.min(width - 1, last + 8)};
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
     *        of that slice is allowed not to add up - unless the slice prints as many blocks whole as
     *        it answered orders, which means it holds nothing but whole blocks (see
     *        {@link #moneyCanBeChecked})
     * @param blocksHere how many order blocks the picture prints whole, {@code 0} when unknown: an
     *        answer with one block less is asked again, which is the only check that sees a block
     *        dropped in the middle of a slice
     * @param articlesHere how many article rows the picture prints (one product picture beside each),
     *        {@code 0} when unknown: a slice that holds one block has one picture per row, so its
     *        pictures count the rows a block of fifteen packs kept hidden from the model (Step 183)
     * @return what was read
     */
    private static Read readPicture(byte[] picture, String endpoint, String model, String key,
            String contentType, boolean partial, int blocksHere, int articlesHere) {
        Read read = new Read();
        // a slice of a page full of product photographs is far smaller as JPEG and the text stays
        // readable at this quality; the PNG is kept when it comes out smaller than the JPEG
        if (picture != null) {
            byte[] jpeg = jpegOf(picture);
            if (jpeg != null && jpeg.length < picture.length) {
                Debug.logInfo("A slice of " + picture.length + " bytes is sent as " + jpeg.length
                        + " bytes of JPEG", MODULE);
                picture = jpeg;
                contentType = "image/jpeg";
            }
        }
        List<Map<String, String>> bestOrders = Collections.emptyList();
        String bestReply = null;
        int bestSeen = 0;
        int bestScore = -1;
        // what the first read lost, in money: the second read is asked with that named, which is what
        // makes a model go back over a block of thirteen rows it had summarised into five
        String gapHint = "";
        // a block of a dozen articles or more is the one that keeps failing (fifteen packs of
        // capacitors came back with fourteen rows, or with one part number for all of them), and every
        // attempt at it costs another call: two attempts and then the halves keep a scan under half a
        // minute instead of forty seconds, and the halves are asked one call each (Step 194). The
        // ceiling a call may reach is kept, so the one reason that is worth a third send (the same part
        // number on every row, which would import the block as one part) can take it (Step 196)
        int attempts = partial ? SLICE_ATTEMPTS : ATTEMPTS;
        int ceiling = attempts;
        if (articlesHere >= SPLIT_FROM_ARTICLES) {
            attempts = Math.min(attempts, 2);
        }
        for (int attempt = 1; attempt <= attempts; attempt++) {
            if (attempt > 1) {
                pause(attempt);
            }
            String reply;
            try {
                // a slice gets the long request too: it carries the rules for a block of many articles
                // (read every row, the pack size, the money of a row), and a block of fifteen packs
                // came back one row short whenever a slice was asked with the short one (Step 183).
                // Thinking is off in the request itself, so the long list of rules no longer eats the
                // output budget the way it did when the short prompt was written. A long block keeps it
                // on a retry as well: the short request is the one that answered all fifteen rows with
                // the same 0805 (Step 196)
                String question = attempt > 1 && articlesHere < SPLIT_FROM_ARTICLES
                        ? SHORT_PROMPT : PROMPT;
                reply = askVision(endpoint, model, key, picture, contentType, question + gapHint);
                read.transportProblem = null;
            } catch (IOException e) {
                Debug.logError(e, "Could not ask the vision service (attempt " + attempt + ")", MODULE);
                read.transportProblem = e.getMessage();
                continue;
            }
            read.reply = reply;
            List<Map<String, String>> got = OirOrders.orderListFrom(reply, partial);
            int counted = OirOrders.orderCountIn(reply);
            // the pictures beside the rows say how many rows the block prints, and that is worth more
            // than how well an answer named them: a row that is missing never reaches stock (Step 196)
            int score = OirOrders.answerScore(got, counted, articlesHere);
            int kept = bestScore;
            if (score > bestScore) {
                bestScore = score;
                bestOrders = got;
                bestReply = reply;
                bestSeen = counted;
            }
            // fewer orders read than the model says it saw, an answer that is mostly the answer
            // format, a row whose quantity or amount cannot be read, a page that prints a pack size
            // while every line came back as one piece, a slice that prints more blocks than came
            // back, or (on a whole screenshot) an order whose goods plus freight is not the paid
            // total: either way one more send is worth it
            boolean shortRead = !partial && counted > 0 && got.size() < counted;
            String why = whyAskAgain(got, counted, partial, shortRead, blocksHere, articlesHere, reply);
            if (why == null) {
                bestOrders = got;
                bestReply = reply;
                bestSeen = counted;
                break;
            }
            Debug.logInfo("Attempt " + attempt + " of " + attempts + " scored " + score
                    + " (kept " + kept + ", best " + bestScore + "), asking again: " + why, MODULE);
            // the two reasons that make an imported receipt wrong rather than untidy - a row of the
            // block that was never read, and every row carrying one part number - may take the third
            // send even though a long block is kept at two: a block of fifteen packs came back with
            // fourteen once in a regression run of twenty-nine pages and nothing asked again (Step 200)
            if ((COUNT_REASON.equals(why) || CODES_REASON.equals(why))
                    && attempt >= attempts && attempts < ceiling) {
                attempts++;
            }
            if (COUNT_REASON.equals(why)) {
                // the pictures beside the rows say how many rows the block prints: tell the model the
                // number it has to reach, which is what makes it go back over a block of fifteen
                // packs it had stopped at fourteen (Step 183)
                gapHint = " This block prints " + articlesHere + " article rows, one product picture"
                        + " beside each; your answer has " + OirScan.articlesOf(got)
                        + ". Read every row, including the one just above the block's buttons.";
            }
            if (MONEY_REASON.equals(why)) {
                // the money says what went missing, and it is the only reason that can be put into
                // words for the model: one more look with it named, not a third call
                gapHint = OirOrders.moneyGapOf(got);
                if (attempt >= 2) {
                    break;
                }
            }
            if (CODES_REASON.equals(why)) {
                // the word every row was answered with is whatever the answer kept repeating - 0805 on
                // a list of capacitors, something else on another page - so it is read back out of the
                // answer and named, never assumed: a word that is only the package size printed at the
                // start of each row's own grey spec line is dropped and what follows it written, and any
                // other word is simply not the value of that row (Step 193/196). A block read this way
                // would import as one part, so this is the one reason that may take a third send even
                // though a long block is kept at two (Step 196)
                String shared = OirOrders.sharedCodeOf(got);
                String named = shared == null ? "" : " (" + shared + ")";
                String what = shared != null && OirOrders.looksLikeAPackageSize(shared)
                        ? " " + shared + " is the package size printed at the start of the grey spec"
                                + " line, not the article: drop it and write what follows it on that row's"
                                + " own line, so a line beginning " + shared + " 47UF 476M 10% 16V gives"
                                + " 47UF 476M 10% 16V."
                        : "";
                gapHint = " The rows of that block are different articles, but your answer gave every"
                        + " row the same code" + named + "." + what
                        + " Every row takes the value of its own grey line and no two rows share one"
                        + " code.";
            }
            if (got.isEmpty()) {
                Debug.logInfo("The answer that carried no order was: " + reply, MODULE);
            }
        }
        read.orders = bestOrders;
        read.reply = bestReply != null ? bestReply : read.reply;
        read.seen = bestSeen;
        if (blocksHere > 0) {
            // what a slice shows and what it answered, side by side: the only way to tell a slice that
            // dropped a block from one that read its blocks and a piece of the next
            Debug.logInfo("A picture printing " + blocksHere + " blocks was answered with "
                    + bestOrders.size() + " orders", MODULE);
        }
        return read;
    }

    /**
     * Says what is wrong with one read of one picture, or {@code null} when it can be used as it
     * stands. The reason is written to the log, so a page that needed two tries can be told apart from
     * one the service answered badly.
     *
     * @param got the orders the answer was read into
     * @param counted how many order blocks the model said the picture shows, zero when it did not say
     * @param partial {@code true} for one slice of a long page, whose money is allowed not to add up
     * @param shortRead {@code true} when fewer orders came back than the model said it saw
     * @param blocksHere how many order blocks the picture prints whole, {@code 0} when unknown
     * @param articlesHere how many article pictures the picture prints, {@code 0} when unknown
     * @param reply the answer text
     * @return why the picture should be asked again, {@code null} when it need not be
     */
    private static String whyAskAgain(List<Map<String, String>> got, int counted, boolean partial,
            boolean shortRead, int blocksHere, int articlesHere, String reply) {
        if (got.isEmpty()) {
            return "no order came back";
        }
        if (shortRead) {
            return "the model saw " + counted + " orders and answered " + got.size();
        }
        if (blocksHere >= 2 && got.size() < blocksHere) {
            // a block carries its own button whatever became of the deal, so a slice that prints two
            // of them and answers one has dropped one: nothing else notices, the money of what came
            // back adds up on its own (Step 154)
            return "the slice prints " + blocksHere + " blocks and answered " + got.size();
        }
        if (OirOrders.anyUnreadable(got)) {
            // a row with no quantity or no amount is padding, and it is the row an answer invents to
            // reach a count it was told to reach: it is named before the count is, or the request goes
            // back asking for more of the same (Step 198)
            return "a row has no readable quantity or amount";
        }
        if (OirOrders.paysNothing(got)) {
            // the block prints 实付款 1.20 and the row beside it came back as 0.00: a page whose unit
            // price rounds to zero (0.024) is a row to read again, not a row worth nothing (Step 200)
            return "the block paid money that none of its rows carry";
        }
        if (printsMoreArticles(articlesHere, got) && !moneyBalances(got, partial, blocksHere)) {
            // the page prints one product picture beside every article row, and a slice that holds one
            // block has nothing else in its picture column: a block of fifteen packs of capacitors was
            // read as fourteen twice in a row, and the money difference alone never made the model go
            // back over it (Step 183). A page that prints two pictures beside a row would ask for
            // twice the rows, so only a short answer close to the count counts. Rows that already come
            // to what was paid are not short at all: the picture counted is one the page prints of
            // something else, an advertisement card that starts where the product picture column does
            // (Step 198) - and then nothing is asked (Step 200)
            return COUNT_REASON;
        }
        if (OirOrders.echoesTemplate(reply)) {
            return "the answer is the answer format itself";
        }
        if (OirOrders.codesDoNotTellTheRowsApart(got)) {
            // a block of fifteen capacitor packs, each with its own value on the grey line, came back
            // with 0805 as the part number of all fifteen: they would import as one part (Step 193)
            return CODES_REASON;
        }
        if (OirOrders.ignoredThePackSize(reply, OirOrders.linesOfOrders(got))) {
            return "the page prints a pack size and every line came back as one piece";
        }
        if (anyRowsMissing(got)) {
            return "an order prints more rows than came back";
        }
        if (OirOrders.repeatsAnotherOrder(got)) {
            return "two orders print the same articles to the cent";
        }
        if (moneyCanBeChecked(partial, got.size(), blocksHere) && anyTotalsDisagree(got)) {
            return MONEY_REASON;
        }
        return null;
    }

    /**
     * Says whether the money of an answer already comes to what was paid, which is what makes a short
     * answer a page printing something else in its picture column rather than a lost row.
     *
     * @param orders the orders of the answer
     * @param partial {@code true} for one slice of a long page
     * @param blocksHere how many blocks the slice prints whole, {@code 0} when unknown
     * @return {@code true} when the money can be checked and it adds up
     */
    private static boolean moneyBalances(List<Map<String, String>> orders, boolean partial,
            int blocksHere) {
        return moneyCanBeChecked(partial, orders.size(), blocksHere) && !anyTotalsDisagree(orders);
    }

    /**
     * Whether an answer has fewer article rows than the block prints pictures.
     *
     * <p>The page prints one product picture beside every article row, so a slice that holds one block
     * counts its rows in its picture column. Only an answer that is short by a few rows counts: more
     * than {@link #MAX_ARTICLES_MISSING} apart is a page that prints two pictures beside a row (or a
     * picture column that caught something else), and asking for twice the rows would be worse than
     * the short answer.
     *
     * @param articlesHere how many pictures the block prints, {@code 0} when unknown
     * @param got the orders the answer was read into
     * @return {@code true} when the answer is a few rows short of the pictures
     */
    public static boolean printsMoreArticles(int articlesHere, List<Map<String, String>> got) {
        if (articlesHere <= 0) {
            return false;
        }
        int read = articlesOf(got);
        // an answer with no row at all is a different trouble (it is asked again for that), and a few
        // rows short is the case this rule is for
        return read > 0 && read < articlesHere && articlesHere - read <= MAX_ARTICLES_MISSING;
    }

    /**
     * Whether the money of an answer may be held against the page: it may, unless the picture is one
     * slice of a long page, whose first and last block may be cut in two by the cut (their rows are
     * partly in the slice above or below, so their money cannot add up).
     *
     * <p>A slice that prints {@code blocks} blocks whole and answered exactly that many orders holds
     * nothing but whole blocks, so its money is worth checking: a nine row block answered with five
     * rows adds up to neither the paid total nor anything else, and the first read looked fine
     * otherwise - the row count the model reported was the five it answered (Step 158).
     *
     * @param partial {@code true} for one slice of a long page
     * @param read how many orders the answer carried
     * @param blocksHere how many blocks the slice prints whole, {@code 0} when unknown
     * @return {@code true} when the money of the answer means something
     */
    public static boolean moneyCanBeChecked(boolean partial, int read, int blocksHere) {
        return !partial || (blocksHere > 0 && read == blocksHere);
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
    public static List<byte[]> slicesOf(byte[] image) {
        return slicesOfRanges(image).pictures;
    }

    /**
     * The slices of a picture, each with the rows it was cut from, and the buttons of the whole page.
     * The rows are what makes it possible to say how many order blocks a slice really shows, and so to
     * notice a slice the model answered with one block less than it prints.
     */
    private static final class Slices {
        private final List<byte[]> pictures = new ArrayList<>();
        private final List<int[]> ranges = new ArrayList<>();
        private final List<Integer> buttons = new ArrayList<>();
        /** Whether the page's recommendation feed was cut off, so the last slice holds orders only. */
        private boolean feedCut;
        /** The same slices cropped to the numbered left of the page, for the second, sharper read. */
        private final List<byte[]> strips = new ArrayList<>();
    }

    private static Slices slicesOfRanges(byte[] image) {
        return slicesOfRanges(image, Integer.MAX_VALUE, Collections.<int[]>emptyList());
    }

    /**
     * The slices of a picture, cut at the blocks of the list.
     *
     * @param image the screenshot
     * @param stripTop the first row of the page's grid of recommendation cards, or
     *        {@link Integer#MAX_VALUE} when the page prints none
     * @param pictures the boxes of the page's product pictures, top to bottom
     * @return the slices, each with the rows it was cut from
     */
    private static Slices slicesOfRanges(byte[] image, int stripTop, List<int[]> pictures) {
        Slices sliced = new Slices();
        BufferedImage full;
        try {
            full = ImageIO.read(new ByteArrayInputStream(image));
        } catch (IOException e) {
            Debug.logError(e, "Could not read the screenshot to slice it", MODULE);
            full = null;
        }
        if (full == null || full.getHeight() <= ONE_CALL_HEIGHT) {
            sliced.pictures.add(image);
            sliced.ranges.add(new int[] {0, full == null ? 0 : full.getHeight()});
            if (full != null) {
                int[] columns = contentColumns(full, 0, full.getHeight());
                byte[] strip = sliceOf(full, columns[0],
                        Math.min(columns[1], columns[0] + STRIP_WIDTH), 0, full.getHeight());
                if (strip != null) {
                    sliced.strips.add(strip);
                } else {
                    sliced.strips.add(null);
                }
            }
            return sliced;
        }        int height = full.getHeight();
        // how tall one slice should be: two order blocks, measured on the picture itself, so the cuts
        // suit the page in hand instead of one number picked for one screenshot (Step 148)
        int target = sliceTargetOf(full);
        // the blocks of the page are found in the picture itself when it is a list of orders: cutting
        // between two blocks keeps every block whole, which is what the model needs most (Step 139)
        List<int[]> found = buttonsOf(full);
        List<Integer> anchors = new ArrayList<>(found.size());
        for (int[] button : found) {
            anchors.add(Integer.valueOf(button[0]));
        }
        // the buttons of the list itself, without the page's own: only those say how many blocks a
        // slice shows, and a page header or a recommendation strip counted as a block would make a
        // good answer look short and send it again for nothing (Step 154)
        List<Integer> list = listButtons(found, Math.max(2, full.getWidth() / 200));
        sliced.buttons.addAll(list);
        // the last block of the list ends with its button, and below that the page prints its
        // recommendation feed: cards with prices and buttons of their own and no orders at all. On the
        // audited page that feed was the bottom 4700 rows - three of twelve calls spent on
        // advertisements and a chance for the model to invent an order (Step 154)
        if (!list.isEmpty()) {
            // the feed is cut off where the page itself separates it from the last order, and below the
            // tallest block the page prints when it separates nothing: a tall order (ten articles) ran
            // past the old fixed room and lost the rows below the cut, and the model filled them in
            // from the paid total (Step 161)
            int end = tailCutOf(full, list, blockHeightOf(full),
                    lastArticlePictureBottom(pictures,
                            list.get(list.size() - 1).intValue() - ROW_GAP_ROWS,
                            blockHeightOf(full)));
            // the grid of recommendation cards below the list is not orders: when its cards stand side
            // by side somewhere below the last button, the list ends there. This is what keeps the feed
            // out of the last block on a page whose last order is shorter than the tallest one - there
            // the room after the button was measured on the tallest block and reached past the feed,
            // so the model read three advertisement titles as three articles of the last order
            // (Step 179q)
            int lastButton = list.get(list.size() - 1).intValue();
            if (stripTop < end && stripTop > lastButton) {
                Debug.logInfo("A strip of cards printed side by side starts at row " + stripTop
                        + ", the order list ends there", MODULE);
                end = stripTop;
            }
            if (end < height) {
                Debug.logInfo("The order list ends at row " + end + " of " + height
                        + ", the rest is the recommendation feed", MODULE);
                sliced.feedCut = true;
                height = end;
            }
        }
        // how many buttons the page prints is logged: it is close to the number of order blocks, and
        // the next step towards spotting a block the model never read (see oir/README.md, section 6)
        Debug.logInfo("The picture shows " + anchors.size() + " orange buttons in " + height + " rows",
                MODULE);
        List<Integer> cuts = cutsBetweenBlocks(list.isEmpty() ? anchors : list, target, height);
        // a cut laid on a block header needs no overlap, a cut at a blank row does
        boolean atHeaders = !cuts.isEmpty();
        if (!atHeaders) {
            cuts = cutsAtBlankRows(full, target);
        }
        // the page prints between two columns and keeps an empty margin each side: cutting it away makes
        // every letter of the slice bigger once the vision service scales it (Step 156)
        int[] columns = contentColumns(full, 0, height);
        int stripRight = Math.min(columns[1], columns[0] + STRIP_WIDTH);
        Debug.logInfo("The page prints between columns " + columns[0] + " and " + columns[1] + " of "
                + full.getWidth() + ", the numbers are read from columns " + columns[0] + ".."
                + stripRight, MODULE);
        int top = 0;
        for (int i = 0; i <= cuts.size(); i++) {
            int bottom = i < cuts.size() ? cuts.get(i).intValue() : height;
            byte[] slice = sliceOf(full, columns[0], columns[1], top, bottom);
            if (slice == null) {
                sliced.pictures.clear();
                sliced.ranges.clear();
                sliced.buttons.clear();
                sliced.strips.clear();
                sliced.pictures.add(image);
                sliced.ranges.add(new int[] {0, height});
                return sliced;
            }
            sliced.pictures.add(slice);
            sliced.ranges.add(new int[] {top, bottom});
            sliced.strips.add(sliceOf(full, columns[0], stripRight, top, bottom));
            if (bottom >= height) {
                break;
            }
            // a cut laid on a block header already starts the next slice at the block, so there is
            // nothing to overlap: the rows of a block are never in two slices at once
            int next = atHeaders ? bottom : bottom - SLICE_OVERLAP;
            top = next > top ? next : bottom;
        }
        return sliced;
    }

    /**
     * The row below which the page prints its 猜你喜欢 cards instead of orders.
     *
     * <p>Preferred cut: the page separates the two itself. One order keeps its articles 30 to 80 empty
     * rows apart (measured 56 to 78 on a page of sixteen orders), while the cards below a page whose
     * pictures did not load start after a band of 500 empty rows and more. So the cut is the first band
     * that is both wide enough on its own ({@link #MIN_TAIL_GAP}) and twice as wide as the widest band
     * inside the list, which keeps a page that prints its articles far apart from being cut in the
     * middle of its last order.
     *
     * <p>A page whose cards do load separates nothing (its bands are 18 to 40 rows, like the ones
     * between two articles), so the room below the last button falls back to one block: the height the
     * page measures for itself, or the **median** gap between two of its buttons. The median and not the
     * tallest: one order with eleven articles is eight thousand rows tall, and the tallest gap pushed the
     * search past the end of the picture, so nothing was cut (Step 179q). A cut at
     * one block below the last button used to be the only rule, and it cut the tallest order of the
     * list whenever that order was taller than the room: a ten article order (20.00 yuan) had only its
     * header and two rows inside the picture, and the model then filled the rest in from the paid total
     * - the right ten rows once, one or two rows the other times (Step 161).
     *
     * <p>That room is now only where the search <em>starts</em>: the cut is a band of empty rows, never
     * a guessed row. The surest signal the page gives comes first - every article row prints its product
     * picture, so the lowest picture of the last block ends with that block, and the first real gap
     * below it is where the list runs out. Without it the guess could lie in the middle of the last
     * order: a block of four packs of resistors printed its rows 260 rows apart, the guess cut the third
     * row's pack size away (its quantity came back as 1 instead of 100) and the fourth row was gone with
     * nothing to notice it (Step 187). When the page separates nothing at all the picture is kept whole:
     * a title of the cards read as an article is flagged by the money of that order, a row cut away is
     * flagged by nothing.
     *
     * @param image the picture being cut
     * @param buttons the top row of every button of the list, top to bottom
     * @param blockHeight the height of one block as the page measures itself, 0 when it does not say
     * @return the row where the list ends, the height of the picture when nothing is cut
     */
    public static int tailCutOf(BufferedImage image, List<Integer> buttons, int blockHeight) {
        return tailCutOf(image, buttons, blockHeight, 0);
    }

    /**
     * @param image the picture being cut
     * @param buttons the top row of every button of the list, top to bottom
     * @param blockHeight the height of one block as the page measures itself, 0 when it does not say
     * @param lastPictureBottom the bottom row of the lowest product picture below the last button, 0
     *        when the page prints none (a page of text only)
     * @return the row where the list ends, the height of the picture when nothing is cut
     */
    public static int tailCutOf(BufferedImage image, List<Integer> buttons, int blockHeight,
            int lastPictureBottom) {
        int height = image.getHeight();
        if (buttons == null || buttons.isEmpty()) {
            return height;
        }
        int lastButton = buttons.get(buttons.size() - 1).intValue();
        if (lastButton <= 0 || lastButton >= height - 1) {
            return height;
        }
        List<Integer> apart = new ArrayList<>();
        for (int i = 1; i < buttons.size(); i++) {
            apart.add(Integer.valueOf(buttons.get(i).intValue() - buttons.get(i - 1).intValue()));
        }
        // one block of room below the last button, measured on the page's own block height and on the
        // typical gap between two buttons - the median and not the tallest: on a page where one order
        // is far taller than the others (eleven articles), the tallest gap pushed the search past the
        // end of the picture, so nothing was cut and the whole recommendation grid stayed in the last
        // slice, where the model read its titles as three more articles of the last order (Step 179q)
        int typical = 0;
        if (!apart.isEmpty()) {
            Collections.sort(apart);
            typical = apart.get(apart.size() / 2).intValue();
        }
        int room = Math.max(Math.max(300, blockHeight), typical);
        int from = (int) Math.min((long) height, (long) lastButton + room);
        // every article row of a block prints its product picture, and that picture stands beside the
        // whole row: below the last picture of the last block there is nothing of that order left, so
        // the search for the end of the list starts there. This is what a page needs whose last block
        // is taller than any room measured from the other blocks (Step 187)
        if (lastPictureBottom > lastButton) {
            from = Math.max(from, (int) Math.min((long) height, (long) lastPictureBottom + ROW_GAP_ROWS));
        }
        boolean[] blank = blankRows(image);
        // how wide the widest empty band of the list itself is: the articles of one order sit that far
        // apart, and a band below it has to be much wider to be the feed
        int widestInside = 0;
        int start = -1;
        for (int y = 0; y < lastButton; y++) {
            if (blank[y]) {
                if (start < 0) {
                    start = y;
                }
            } else if (start >= 0) {
                widestInside = Math.max(widestInside, y - start);
                start = -1;
            }
        }
        int least = Math.max(MIN_TAIL_GAP, TAIL_GAP_RATIO * widestInside);
        // the lowest picture of the last block ends with its last row, so the first real gap below it is
        // where that block runs out - the surest signal the page gives. It has to come first: below a
        // block of four packs of resistors (its rows 260 rows apart) the band the page puts between the
        // list and the cards was narrower than twice that spacing, and the guessed row that followed cut
        // the third row's pack size away (its quantity came back as 1 instead of 100) and lost the
        // fourth row entirely - with nothing to notice it, because the product pictures below the cut
        // are not counted either (Step 187)
        int separated = -1;
        if (lastPictureBottom > lastButton) {
            separated = bandAfter(blank, from, ROW_GAP_ROWS, height);
        }
        // otherwise the band the page puts between its list and the cards below it, when it separates
        // them ...
        if (separated < 0) {
            separated = bandAfter(blank, from, least, height);
        }
        if (separated >= 0) {
            Debug.logInfo("The tail of the page: last button at " + lastButton + ", last picture ends at "
                    + lastPictureBottom + ", a band of " + least + " empty rows or more at " + separated
                    + ", one block is " + blockHeight + " and two buttons are " + typical + " apart",
                    MODULE);
            return separated;
        }
        // nothing separates the cards below from the list: the whole picture is kept rather than cut an
        // order in two. The cards are told apart later (their pictures stand side by side, and the money
        // of the last order never comes to what was paid when a title of the feed is read as an
        // article), while a row cut away is gone with nothing to notice it (Step 187)
        Debug.logInfo("The page separates nothing below the last button at " + lastButton
                + ", so the whole picture is kept instead of cutting an order in two", MODULE);
        return height;
    }

    /**
     * The bottom row of the last product picture of the last block.
     *
     * <p>The page then prints its recommendation cards, which carry pictures of their own. Taking the
     * lowest picture of the page for the last article pushed the cut past the end of the picture, so
     * nothing was cut and the last slice became the whole tail - 7106 rows where every other slice is
     * 900 - and the model lost a row of the last order reading a slice eight times as tall as the
     * rest (Step 192). The rows of one block print their pictures one after another, while the cards
     * below the list stand a whole block apart from the last of them, so the block ends at the first
     * picture that follows a gap wider than a block.
     *
     * @param boxes the boxes of the page's product pictures, top to bottom
     * @param after the row of the last button of the list
     * @param blockHeight the height of one block as the page measures itself, 0 when it does not say
     * @return the bottom row of the last picture of that block, 0 when it prints none
     */
    public static int lastArticlePictureBottom(List<int[]> boxes, int after, int blockHeight) {
        if (boxes == null || boxes.isEmpty()) {
            return 0;
        }
        int limit = Math.max(ROW_GAP_ROWS, blockHeight);
        int bottom = 0;
        int previous = 0;
        for (int[] box : boxes) {
            if (box[1] < after) {
                continue;
            }
            if (previous > 0 && box[1] - previous > limit) {
                // the cards below the list begin: the block ended above this picture
                break;
            }
            previous = box[3];
            bottom = box[3];
        }
        return bottom;
    }

    /**
     * @param blank the rows that carry nothing, one flag per row
     * @param from the first row the cut may be made at
     * @param least how many empty rows the band needs
     * @param height how tall the picture is
     * @return the row to cut at, {@code -1} when the picture has no such band
     */
    private static int bandAfter(boolean[] blank, int from, int least, int height) {
        int row = 0;
        int limit = Math.min(height, blank.length);
        while (row < limit) {
            if (!blank[row]) {
                row++;
                continue;
            }
            int start = row;
            while (row < limit && blank[row]) {
                row++;
            }
            if (row - start < least) {
                continue;
            }
            if (start >= from) {
                return start;
            }
            if (row > from) {
                // the search starts inside this band (below the last picture of the block, say):
                // anywhere in a band of empty rows is a safe place to cut
                return from;
            }
        }
        return -1;
    }

    /**
     * @param image the picture
     * @return one entry per row, true when that row carries almost nothing printed on it
     */
    private static boolean[] blankRows(BufferedImage image) {
        int height = image.getHeight();
        int width = image.getWidth();
        int step = Math.max(2, width / 200);
        boolean[] blank = new boolean[height];
        for (int y = 0; y < height; y++) {
            int dark = 0;
            for (int x = 0; x < width; x += step) {
                int rgb = image.getRGB(x, y);
                int r = (rgb >> 16) & 0xFF;
                int g = (rgb >> 8) & 0xFF;
                int b = rgb & 0xFF;
                if (r + g + b < 3 * 200) {
                    dark++;
                }
            }
            blank[y] = dark < BLANK_ROW_DARK;
        }
        return blank;
    }

    /**
     * The rows to cut at: the header of every order block, so every slice holds exactly one block.
     *
     * <p>A page prints one button per block, so the blocks are counted, not guessed: each cut lands on
     * the header of the next block and the slice ends just above the block after it. One block per call
     * is what the model reads best - it has one order number, one paid total and one list of articles in
     * front of it, so it cannot hang rows on the wrong order and its money can be checked on every
     * slice. A block taller than a slice is no problem at all: it is simply a tall slice, where a cut at
     * a fixed height halved it and the model read a few rows of each half (a thirteen row order of
     * resistors came back with five rows and 5.10 yuan missing - Step 159).
     *
     * @param buttons the top row of every block's own button, top to bottom
     * @param target unused, kept so the call sites stay the same: the blocks themselves say where to cut
     * @param height the height of the picture
     * @return the rows to cut at, empty when the picture shows fewer than two blocks
     */
    public static List<Integer> cutsBetweenBlocks(List<Integer> buttons, int target, int height) {
        List<Integer> cuts = new ArrayList<>();
        if (buttons == null || buttons.size() < 2) {
            return cuts;
        }
        int top = 0;
        for (int i = 1; i < buttons.size(); i++) {
            int row = buttons.get(i).intValue() - BUTTON_TO_HEADER;
            if (row >= height) {
                break;
            }
            if (row <= top) {
                continue;
            }
            cuts.add(Integer.valueOf(row));
            top = row;
        }
        return cuts;
    }

    /**
     * The cuts of a page that shows no order buttons (another shop platform, or a cropped picture):
     * every {@code target} rows, slid to the nearest row without text.
     *
     * @param image the picture being cut
     * @param target how tall one slice should be
     * @return the rows to cut at
     */
    private static List<Integer> cutsAtBlankRows(BufferedImage image, int target) {
        List<Integer> cuts = new ArrayList<>();
        int height = image.getHeight();
        int top = 0;
        while (top < height) {
            int bottom = Math.min(height, top + target);
            if (bottom < height) {
                bottom = blankRowNear(image, top, bottom);
                cuts.add(Integer.valueOf(bottom));
            }
            if (bottom >= height) {
                break;
            }
            int next = bottom - SLICE_OVERLAP;
            top = next > top ? next : top + Math.max(1, target / 2);
        }
        return cuts;
    }

    /**
     * How tall one slice should be: two order blocks, measured on the picture itself. A page cut for
     * one screenshot does not suit the next one (another platform, another zoom level, another block
     * layout), so the height comes from the page in hand; {@link #SLICE_HEIGHT} answers only when the
     * blocks cannot be told apart (Step 148).
     *
     * @param image the picture being cut
     * @return the height one slice should have
     */
    public static int sliceTargetOf(BufferedImage image) {
        int block = blockHeightOf(image);
        if (block <= 0) {
            return SLICE_HEIGHT;
        }
        int target = 2 * block;
        return Math.max(MIN_SLICE_HEIGHT, Math.min(MAX_SLICE_HEIGHT, target));
    }

    /**
     * Measures how tall one order block of a list is, on the picture itself: the rows of such a page
     * repeat their shape block after block (a strip with the number and the shop, a picture, a title,
     * a price row), so the darkness of the page repeats with the height of one block. The page is read
     * as one darkness value per row and that profile is compared with itself at every distance between
     * one fifth of a slice and half the picture; the distance that repeats best is the height of a
     * block. A page without that repetition (one order, a picture of something else) answers 0.
     *
     * @param image the picture being cut
     * @return the height of one block in rows, 0 when the picture does not repeat
     */
    public static int blockHeightOf(BufferedImage image) {
        int height = image.getHeight();
        int width = image.getWidth();
        if (height < 600 || width < 50) {
            return 0;
        }
        int step = Math.max(2, width / 200);
        int[] ink = new int[height];
        for (int y = 0; y < height; y++) {
            int dark = 0;
            for (int x = 0; x < width; x += step) {
                int rgb = image.getRGB(x, y);
                int r = (rgb >> 16) & 0xFF;
                int g = (rgb >> 8) & 0xFF;
                int b = rgb & 0xFF;
                if (r + g + b < 3 * 200) {
                    dark++;
                }
            }
            ink[y] = dark;
        }
        // a picture with nothing printed on it (all white, all black) has no repeating structure
        long sum = 0L;
        long squares = 0L;
        for (int y = 0; y < height; y++) {
            sum += ink[y];
            squares += (long) ink[y] * ink[y];
        }
        double mean = (double) sum / height;
        if ((double) squares / height - mean * mean < 4.0) {
            return 0;
        }
        int lowest = Math.max(300, height / 40);
        int highest = Math.min(height / 2, MAX_SLICE_HEIGHT);
        double best = -1.0;
        int bestLag = 0;
        for (int lag = lowest; lag <= highest; lag++) {
            long dot = 0L;
            long here = 0L;
            long there = 0L;
            for (int y = 0; y + lag < height; y += 2) {
                dot += (long) ink[y] * ink[y + lag];
                here += (long) ink[y] * ink[y];
                there += (long) ink[y + lag] * ink[y + lag];
            }
            if (here == 0L || there == 0L) {
                continue;
            }
            double score = dot / Math.sqrt((double) here * (double) there);
            if (score > best) {
                best = score;
                bestLag = lag;
            }
        }
        // a page that does not repeat (one order, a picture of something else) has no block height
        return best < 0.5 ? 0 : bestLag;
    }

    /**
     * @param image the picture
     * @param left the first column to keep
     * @param right the last column to keep
     * @param top the first row of the slice
     * @param bottom the row after the last one
     * @return the slice as PNG bytes, {@code null} when it cannot be written
     */
    private static byte[] sliceOf(BufferedImage image, int left, int right, int top, int bottom) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            int width = Math.max(1, Math.min(right, image.getWidth() - 1) - Math.max(0, left) + 1);
            ImageIO.write(image.getSubimage(Math.max(0, left), top, width, bottom - top), "png", out);
        } catch (IOException e) {
            Debug.logError(e, "Could not write a slice of the screenshot", MODULE);
            return null;
        }
        return out.toByteArray();
    }

    /**
     * One picture holding the top of a block and its lower rows: the header lines (order number, date,
     * shop, paid total) are printed only once, at the top of the block, so the lower half of a block
     * sent on its own would say nothing about which order it belongs to.
     *
     * @param image the picture
     * @param left the first column to keep
     * @param right the last column to keep
     * @param headBottom the row after the header lines of the block
     * @param bodyTop the first row of the lower half
     * @param bodyBottom the row after the last one
     * @return the picture as PNG bytes, {@code null} when it cannot be written
     */
    private static byte[] stitchedPicture(BufferedImage image, int left, int right, int headBottom,
            int bodyTop, int bodyBottom) {
        int width = Math.max(1, Math.min(right, image.getWidth() - 1) - Math.max(0, left) + 1);
        int height = Math.max(1, headBottom) + Math.max(0, bodyBottom - bodyTop);
        BufferedImage joined = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D pen = joined.createGraphics();
        pen.setColor(Color.WHITE);
        pen.fillRect(0, 0, width, height);
        pen.drawImage(image, 0, 0, width, Math.max(1, headBottom), Math.max(0, left), 0,
                Math.max(0, left) + width, Math.max(1, headBottom), null);
        pen.drawImage(image, 0, Math.max(1, headBottom), width, height, Math.max(0, left), bodyTop,
                Math.max(0, left) + width, Math.max(bodyTop, bodyBottom), null);
        pen.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            ImageIO.write(joined, "png", out);
        } catch (IOException e) {
            Debug.logError(e, "Could not write the lower half of a block", MODULE);
            return null;
        }
        return out.toByteArray();
    }

    /**
     * Reads a block that is still short of its rows in two halves and adds the rows together.
     *
     * <p>A block of fifteen packs of capacitors came back with fourteen rows, and naming the count did
     * not always help: one picture of the whole block is more rows than the model reads carefully. Two
     * pictures of half a block each are read row by row, and the rows are joined by the order number
     * they both carry. The two halves overlap by {@link #HALF_OVERLAP_ROWS} rows so that the seam never
     * falls between two rows; a row both halves saw is kept once.
     *
     * @param slice the slice as it was sent, the block being inside it
     * @param range the rows of the whole page this slice was cut from
     * @param boxes the product pictures of the whole page
     * @param first what the whole block answered before
     * @param endpoint the vision endpoint
     * @param model the model name
     * @param key the api key
     * @return the two halves joined with the first answer, or {@code first} when the block cannot be cut
     */
    private static Read readInHalves(byte[] slice, int[] range, List<int[]> boxes, Read first,
            String endpoint, String model, String key) {
        BufferedImage page;
        try {
            page = ImageIO.read(new ByteArrayInputStream(slice));
        } catch (IOException e) {
            Debug.logError(e, "Could not read a slice again to cut it in two", MODULE);
            return first;
        }
        if (page == null) {
            return first;
        }
        // the pictures of this block, in the rows of the slice: the header lines sit above the first of
        // them, and the middle one is where the block is cut
        List<int[]> inside = new ArrayList<>();
        for (Integer index : OirShots.indexesWithin(boxes, range[0], range[1])) {
            inside.add(boxes.get(index.intValue()));
        }
        if (inside.size() < 4) {
            return first;
        }
        int headBottom = Math.min(inside.get(0)[1] - range[0], MAX_BLOCK_HEAD_ROWS);
        int middle = inside.get(inside.size() / 2)[1] - range[0];
        int bottom = page.getHeight();
        if (headBottom <= 0 || middle <= headBottom + 200 || middle >= bottom - 200) {
            return first;
        }
        int[] columns = contentColumns(page, 0, bottom);
        byte[] upper = sliceOf(page, columns[0], columns[1], 0,
                Math.min(bottom, middle + HALF_OVERLAP_ROWS));
        byte[] lower = stitchedPicture(page, columns[0], columns[1], headBottom, middle, bottom);
        if (upper == null || lower == null) {
            return first;
        }
        Debug.logInfo("A block of " + inside.size() + " articles is read in two halves of "
                + upper.length + " and " + lower.length + " bytes", MODULE);
        Read up = readPicture(upper, endpoint, model, key, "image/png", true, 0, 0);
        Read down = readPicture(lower, endpoint, model, key, "image/png", true, 0, 0);
        return joinHalves(first, up, down);
    }

    /**
     * Joins the whole-block read with the two halves it was re-read in into the one order the block
     * is (the rule itself is in {@link OirOrders#joinHalves}).
     *
     * @param whole the answer to the whole block
     * @param upper the answer to the top half
     * @param lower the answer to the lower half, its head stitched on top
     * @return one answer holding the block
     */
    private static Read joinHalves(Read whole, Read upper, Read lower) {
        Read joined = new Read();
        for (Read read : new Read[] {whole, upper, lower}) {
            if (read == null) {
                continue;
            }
            if (joined.reply == null && read.reply != null) {
                joined.reply = read.reply;
            }
            joined.seen = Math.max(joined.seen, read.seen);
            if (joined.transportProblem == null) {
                joined.transportProblem = read.transportProblem;
            }
        }
        Map<String, String> one = OirOrders.joinHalves(onlyOrderOf(whole), onlyOrderOf(upper),
                onlyOrderOf(lower));
        if (one == null) {
            joined.orders = whole == null ? Collections.emptyList() : whole.orders;
            return joined;
        }
        Debug.logInfo("The halves of a block came out as one order of "
                + OirOrders.parseOrderArticleLines(one.get("lines")).size() + " rows", MODULE);
        joined.orders = new ArrayList<>();
        joined.orders.add(one);
        return joined;
    }

    /**
     * @param read one answer to one picture
     * @return the order it holds, {@code null} unless it holds exactly one (a half of a block is one
     *     order, and an answer with two of them is a model that did not do what it was asked)
     */
    private static Map<String, String> onlyOrderOf(Read read) {
        if (read == null || read.orders == null || read.orders.size() != 1) {
            return null;
        }
        return read.orders.get(0);
    }

    /**
     * Finds the orange button of every order block of a shop page: the face of a button is one
     * unbroken stretch of that colour, so a row that carries a long band of it is a button row, and a
     * button means a block. Used to cut between blocks, so a cut never goes through one.
     *
     * <p>The band has to be <em>unbroken</em>: the orange words a block prints in its header
     * ({@code 订单详情 >}, {@code 交易成功}, the coupon strip) put as much orange on one row as a
     * button does, but spread over the whole width in strokes a few pixels wide. Counting those as
     * buttons gave 32 buttons for a page of 23 orders (audited on the real page: 23 buttons, 9 rows of
     * orange words, then the header button and the one of the recommendation strip below the list).
     *
     * @param image the picture
     * @return the top row of every button found, top to bottom; empty when the page has none
     */
    public static List<Integer> blockAnchors(BufferedImage image) {
        List<int[]> buttons = buttonsOf(image);
        List<Integer> anchors = new ArrayList<>(buttons.size());
        for (int[] button : buttons) {
            anchors.add(Integer.valueOf(button[0]));
        }
        return anchors;
    }

    /**
     * Counts the order blocks of a page without asking the model: every block of the list prints its
     * button at the same place, so the buttons that repeat that place are the list's, while the page
     * header and the recommendation strip below the list print their own and print it once.
     *
     * <p>A block keeps its button whatever became of the deal - a closed one offers 再买一单 or 加入购物车
     * like an open one - so this count can be held against the number of orders read, which is the
     * only way to notice a block the model never read ({@code oir/README.md} section 5).
     *
     * @param image the picture
     * @return how many order blocks it shows, {@code 0} when the page does not say (fewer than three
     *         buttons, or no place printed more than once)
     */
    public static int blockCountOf(BufferedImage image) {
        List<Integer> buttons = listButtonsOf(image);
        return buttons.size() >= 3 ? buttons.size() : 0;
    }

    /**
     * The buttons of the order list itself, top to bottom, without the ones of the page header and of
     * the recommendation strip below the list: those stand somewhere else on the page and are printed
     * once, while every block of the list prints its button at the same place.
     *
     * @param image the picture
     * @return the top row of every button of the list, empty when the page does not say
     */
    public static List<Integer> listButtonsOf(BufferedImage image) {
        return listButtons(buttonsOf(image), Math.max(2, image.getWidth() / 200));
    }

    /**
     * The buttons of the list among all the buttons found: the members of the biggest place.
     *
     * @param buttons the buttons of the picture, {@code {top row, left edge}} each
     * @param step the width of one sample of the picture
     * @return the top row of every button of the list, empty when no place repeats
     */
    private static List<Integer> listButtons(List<int[]> buttons, int step) {
        if (buttons.size() < 3) {
            return new ArrayList<>();
        }
        java.util.TreeMap<Integer, List<Integer>> byPlace = new java.util.TreeMap<>();
        for (int[] button : buttons) {
            Integer place = Integer.valueOf(button[1] / step);
            List<Integer> there = byPlace.get(place);
            if (there == null) {
                there = new ArrayList<>();
                byPlace.put(place, there);
            }
            there.add(Integer.valueOf(button[0]));
        }
        // a shop may print two button styles that start a step or two apart, so neighbouring places
        // are one place
        List<Integer> best = new ArrayList<>();
        List<Integer> running = new ArrayList<>();
        Integer previous = null;
        for (Map.Entry<Integer, List<Integer>> place : byPlace.entrySet()) {
            if (previous != null && place.getKey().intValue() - previous.intValue() <= 2) {
                running.addAll(place.getValue());
            } else {
                running = new ArrayList<>(place.getValue());
            }
            if (running.size() > best.size()) {
                best = new ArrayList<>(running);
            }
            previous = place.getKey();
        }
        // the buttons of the list are read top to bottom, whatever place their left edge was grouped
        // under: everything that holds a slice's answer against the blocks it prints walks them in
        // pairs, and an unsorted list pairs a button of the last block with one of the second (Step 158)
        java.util.Collections.sort(best);
        return best.size() >= 3 ? best : new ArrayList<>();
    }

    /**
     * The same count, for the picture as it was uploaded.
     *
     * @param image the picture bytes
     * @return how many order blocks it shows, {@code 0} when the page does not say or the picture
     *         cannot be read
     */
    public static int blockCountOf(byte[] image) {
        if (image == null) {
            return 0;
        }
        try {
            BufferedImage picture = ImageIO.read(new ByteArrayInputStream(image));
            return picture == null ? 0 : blockCountOf(picture);
        } catch (IOException e) {
            Debug.logError(e, "Could not read the screenshot to count its order blocks", MODULE);
            return 0;
        }
    }

    /**
     * The buttons of the picture: the top row of each, with the left edge of its longest band.
     *
     * @param image the picture
     * @return one entry {@code {top row, left edge}} per button, top to bottom
     */
    private static List<int[]> buttonsOf(BufferedImage image) {
        List<int[]> runs = new ArrayList<>();
        int step = Math.max(2, image.getWidth() / 200);
        int needed = Math.max(3, (image.getWidth() / step) / 25);
        int runStart = -1;
        int widest = 0;
        int widestAt = 0;
        for (int y = 0; y < image.getHeight(); y++) {
            int band = 0;
            int longest = 0;
            int longestAt = 0;
            for (int x = 0; x < image.getWidth(); x += step) {
                int rgb = image.getRGB(x, y);
                int r = (rgb >> 16) & 0xFF;
                int g = (rgb >> 8) & 0xFF;
                int b = rgb & 0xFF;
                if (r >= BUTTON_RED && g >= BUTTON_GREEN_LOW && g <= BUTTON_GREEN_HIGH
                        && b <= BUTTON_BLUE) {
                    band++;
                    if (band > longest) {
                        longest = band;
                        longestAt = x - (band - 1) * step;
                    }
                } else {
                    band = 0;
                }
            }
            boolean button = longest >= needed;
            if (button) {
                if (runStart < 0) {
                    runStart = y;
                    widest = 0;
                    widestAt = longestAt;
                }
                if (longest > widest) {
                    widest = longest;
                    widestAt = longestAt;
                }
            } else if (runStart >= 0) {
                if (y - runStart >= 8) {
                    runs.add(new int[] {runStart, widestAt});
                }
                runStart = -1;
            }
        }
        List<int[]> buttons = new ArrayList<>();
        for (int[] run : runs) {
            if (!buttons.isEmpty()
                    && run[0] - buttons.get(buttons.size() - 1)[0] < BUTTON_RUN_GAP) {
                continue;
            }
            buttons.add(run);
        }
        return buttons;
    }


    /**
     * Re-encodes a picture as JPEG, so a large slice of a page full of product photographs does not
     * have to be sent as lossless PNG. The text of a screenshot stays readable at this quality.
     *
     * @param image the picture bytes
     * @return the JPEG bytes, {@code null} when the picture cannot be decoded or written (an odd
     *         format is then sent as it came rather than lost)
     */
    public static byte[] jpegOf(byte[] image) {
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
    public static String scanFailure(String reply, String transportProblem) {
        if (transportProblem != null) {
            return "识图服务连不上（已自动重试 " + (ATTEMPTS - 1) + " 次）：" + transportProblem
                    + "。过一会儿再点一次「识别并核对」，或者把明细粘进框里。";
        }
        if ("length".equals(OirOrders.jsonStringField(reply, "finish_reason"))) {
            // a thinking model that spends the whole output budget on reasoning answers with nothing
            return "模型这次想得太久、没把结果写完（输出长度到顶了），已经重试过。"
                    + "把截图截小一点（只框住订单那几行）再点一次「识别并核对」，或者直接把明细粘进框里。";
        }
        int said = OirOrders.orderCountIn(reply);
        if (said > 0) {
            // the answer names a count but no order could be read out of it: it was cut off
            return "模型说看到 " + said + " 张单，但这次的回答被截断了（只写了一部分），已经重试过。"
                    + "把截图截小一点（一次只框一两张单）再点一次「识别并核对」，或者直接把明细粘进框里。";
        }
        return "这次没从截图里读出订单（已自动重试 " + (ATTEMPTS - 1) + " 次）。"
                + "再点一次「识别并核对」通常就好了；也可以把截图截小一点（只框住订单列表），或者手工填。"
                + "模型的原话：" + OirOrders.visionReplyExcerpt(reply, 160);
    }

    /**
     * Says whether two orders of one page carry the same day, which is what makes two blocks with the
     * same articles and money a copy of one another rather than a purchase made twice.
     *
     * <p>Two blocks of one shop paying the same money for the same article three weeks apart are two
     * real orders - a page of PCB打样 carries exactly that - so the caution is only written when both
     * days were read and they are the same. When either is missing nothing can tell them apart and the
     * caution stays, because a copied block costs an imported row that never existed (Step 199).
     *
     * @param one one order
     * @param two the other order
     * @return {@code true} when both carry a day and it is the same day
     */
    public static boolean sameDay(Map<String, String> one, Map<String, String> two) {
        String here = one == null ? null : one.get("orderDate");
        String there = two == null ? null : two.get("orderDate");
        if (here == null || there == null) {
            return false;
        }
        String mine = here.trim();
        String theirs = there.trim();
        return mine.isEmpty() || theirs.isEmpty() || mine.equals(theirs);
    }

    /**
     * Says whether the money of any recognised order does not add up.
     *
     * @param orders the recognised orders
     * @return {@code true} when at least one order looks like it lost an article
     */
    private static boolean anyTotalsDisagree(List<Map<String, String>> orders) {
        for (Map<String, String> order : orders) {
            if (OirOrders.totalsDisagree(order)) {
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
            if (OirOrders.rowsMissing(order)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Waits before a retry, twice as long for every further attempt, so a hiccup of the service is not
     * hit twice in a row and a struggling endpoint gets a moment to itself.
     *
     * @param attempt the attempt that just failed, starting at one
     */
    private static void pause(int attempt) {
        try {
            Thread.sleep(RETRY_PAUSE_MILLIS << Math.min(attempt - 1, 2));
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
                text(MfgSettingsServices.setting(delegator, VISION_ENDPOINT)));
        String model = firstOf(text(parameters.get("visionModel")),
                text(MfgSettingsServices.setting(delegator, VISION_MODEL)));
        String key = firstOf(text(parameters.get("visionKey")),
                text(MfgSettingsServices.setting(delegator, VISION_KEY)));
        if (endpoint == null || model == null || key == null) {
            return answer(response, false, testMissing(endpoint, model, key), null, UNCONFIGURED);
        }
        try {
            String reply = askVision(OirOrders.normalizeVisionEndpoint(endpoint), model, key,
                    Base64.getDecoder().decode(TINY_PNG), "image/png");
            String content = OirOrders.jsonStringField(reply, "content");
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
    public static String testMissing(String endpoint, String model, String key) {
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
    public static String missingSetting() {
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
    public static String askVision(String endpoint, String model, String key, byte[] image, String contentType)
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
    public static String askVision(String endpoint, String model, String key, byte[] image, String contentType,
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
                throw new IOException(OirOrders.describeVisionError(status, text));
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
        return answer(response, ok, message, orders, code, null);
    }

    /**
     * Writes the answer, naming the stored screenshot so the receipts made from it can point back at
     * the picture the numbers came from.
     *
     * @param scanId the id the screenshot was stored under, {@code null} when it could not be kept
     */
    private static String answer(HttpServletResponse response, boolean ok, String message,
            List<Map<String, String>> orders, String code, String scanId) {
        StringBuilder json = new StringBuilder();
        json.append("{\"ok\":").append(ok).append(",\"message\":")
                .append(MfgBomEvents.jsonString(message));
        if (code != null) {
            json.append(",\"code\":").append(MfgBomEvents.jsonString(code));
        }
        if (scanId != null) {
            json.append(",\"scanId\":").append(MfgBomEvents.jsonString(scanId));
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
