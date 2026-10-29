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

import org.langhua.ofbiz.mfg.oir.OirOrders;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.imageio.ImageIO;

import java.awt.image.BufferedImage;

import org.langhua.ofbiz.mfg.oir.OirScan;
import org.langhua.ofbiz.mfg.oir.OirShots;

import org.junit.Test;

/**
 * How a screenshot of a shop order becomes orders: the answer of the vision model, the slices of a
 * long page, the merge of what the overlap read twice, the tidy of the money and the warnings the
 * page shows. These are the rules of {@code oir} and they need no database.
 */
public class MfgOrderReadRulesTest {

    /**
     * The first order of a vision answer, {@code null} when there is none. The scan only ever uses
     * the list, so the tests read the first element the same way the event does.
     *
     * @param reply the answer text
     * @return the first order, {@code null} when the answer carries none
     */
    private static Map<String, String> oneOrder(String reply) {
        List<Map<String, String>> orders = OirOrders.orderListFrom(reply);
        return orders.isEmpty() ? null : orders.get(0);
    }

    private static byte[] png(int width, int height) throws IOException {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }

    /** @return the top row of the four buttons of the page {@link #orderPage} draws */
    private static List<Integer> orderPageButtons() {
        return java.util.Arrays.asList(Integer.valueOf(150), Integer.valueOf(550),
                Integer.valueOf(950), Integer.valueOf(1350));
    }

    /**
     * A page of four order blocks: three short ones, then a last one that prints ten articles as far
     * apart as the page prints them, and optionally the recommendation strip below the list.
     *
     * @param height how tall the picture is
     * @param articleGap the empty rows between two articles of the last order
     * @param feed whether the page prints its 猜你喜欢 strip from row 2600 on
     * @return the picture
     */
    private static BufferedImage orderPage(int height, int articleGap, boolean feed) {
        BufferedImage image = new BufferedImage(300, height, BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics2D pen = image.createGraphics();
        pen.setColor(java.awt.Color.WHITE);
        pen.fillRect(0, 0, 300, height);
        pen.setColor(java.awt.Color.BLACK);
        int[] buttons = new int[] {150, 550, 950, 1350};
        for (int i = 0; i < buttons.length; i++) {
            // a block prints its header BUTTON_TO_HEADER rows above its own button
            pen.fillRect(10, buttons[i] - 140, 280, 30);
            pen.fillRect(240, buttons[i], 50, 20);
        }
        for (int i = 0; i < 3; i++) {
            pen.fillRect(10, buttons[i] + 50, 200, 20);
        }
        for (int k = 0; k < 10; k++) {
            pen.fillRect(10, 1400 + k * articleGap, 200, 20);
        }
        if (feed) {
            for (int r = 0; r < 6; r++) {
                pen.fillRect(10, 2600 + r * 40, 260, 20);
            }
        }
        pen.dispose();
        return image;
    }

    /** The 面包板 page: one board sold in six colours, so six rows look exactly the same. */
    private static String boardRows(int count) {
        StringBuilder rows = new StringBuilder();
        for (int i = 0; i < count; i++) {
            if (rows.length() > 0) {
                rows.append('\n');
            }
            rows.append("面包板 SYB-170\t面包板 实验板 彩色小面包板 SYB-170 170孔 可拼接 带扣（5个）\t5\t6.88");
        }
        return rows.toString();
    }

    private static Map<String, String> boardOrder(String lines) {
        Map<String, String> order = new java.util.LinkedHashMap<>();
        order.put("orderNo", "2371626699152500570");
        order.put("paidTotal", "41.28");
        order.put("freight", "0.00");
        order.put("lines", lines);
        return order;
    }

    /** One row of the export, keyed by column letter as the reader returns it. */
    private static Map<String, String> excelRow(String... cells) {
        Map<String, String> row = new LinkedHashMap<>();
        for (int i = 0; i < cells.length; i++) {
            row.put(String.valueOf((char) ('A' + i)), cells[i]);
        }
        return row;
    }

    /**
     * A page shaped like a shop order list: every block prints a pale band with its number, a product
     * picture on the left of its article and the title right of it.
     *
     * @param blocks how many blocks the page prints
     * @param part the colour of the part photographed on the card
     * @return the picture
     */
    private static BufferedImage shopPage(int blocks, java.awt.Color part) {
        return shopPage(blocks, part, 0);
    }

    /**
     * The same page, with the photograph drawn {@code inset} pixels to the right of the card: a part in
     * the middle of a white card does not start where the card starts.
     */
    private static BufferedImage shopPage(int blocks, java.awt.Color part, int inset) {
        int blockHeight = 600;
        // 800 wide: the left 45 per cent is searched for pictures, and the title of the row sits far
        // enough right to stay out of the picture strip (the page prints it about 320 pixels right of
        // the column, as the audited page does)
        BufferedImage image = new BufferedImage(800, blocks * blockHeight + 200, BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics2D pen = image.createGraphics();
        pen.setColor(java.awt.Color.WHITE);
        pen.fillRect(0, 0, image.getWidth(), image.getHeight());
        for (int i = 0; i < blocks; i++) {
            int top = i * blockHeight;
            // the number band: pale, 235 by 231 by 215 on the audited page
            pen.setColor(new java.awt.Color(235, 231, 215));
            pen.fillRect(40, top + 10, 740, 110);
            // the part on its card, at the column the page prints its pictures in
            pen.setColor(part);
            pen.fillRect(40 + inset, top + 150, 120, 120);
            // the title and the price, right of the picture
            pen.setColor(java.awt.Color.BLACK);
            for (int line = 0; line < 3; line++) {
                pen.fillRect(420, top + 160 + line * 30, 300, 18);
            }
        }
        pen.dispose();
        return image;
    }

    @Test
    public void cutsTheProductPictureOutOfEveryOrderBlock() {
        List<int[]> boxes = OirShots.boxesOf(shopPage(3, java.awt.Color.BLUE));
        assertEquals(3, boxes.size());
        // top to bottom, one per block, each a square of at least one card that holds the part
        for (int i = 0; i < 3; i++) {
            assertEquals(boxes.get(i)[2] - boxes.get(i)[0], boxes.get(i)[3] - boxes.get(i)[1]);
            assertTrue(boxes.get(i)[0] <= 40);
            assertTrue(boxes.get(i)[2] >= 160);
            assertTrue(boxes.get(i)[1] <= i * 600 + 150);
            assertTrue(boxes.get(i)[3] >= i * 600 + 270);
        }
        assertTrue(boxes.get(0)[1] < boxes.get(1)[1]);
        assertTrue(boxes.get(1)[1] < boxes.get(2)[1]);
    }

    @Test
    public void findsAPictureWhosePhotographStartsInsideItsCard() {
        // two orders whose picture fills its card tell the page where the pictures are printed, and the
        // third is a black connector in the middle of a white card: its content starts 126 pixels right
        // of the column. It was the one picture of 26 that never appeared on the audited page
        BufferedImage page = shopPage(3, java.awt.Color.BLUE);
        java.awt.Graphics2D pen = page.createGraphics();
        pen.setColor(java.awt.Color.WHITE);
        pen.fillRect(40, 2 * 600 + 150, 120, 120);
        pen.setColor(java.awt.Color.BLACK);
        pen.fillRect(40 + 126, 2 * 600 + 150, 120, 120);
        pen.dispose();
        List<int[]> boxes = OirShots.boxesOf(page);
        assertEquals(3, boxes.size());
        // the box is centred on the connector, so the part is inside it
        assertTrue(boxes.get(2)[0] <= 166);
        assertTrue(boxes.get(2)[2] >= 286);
        assertTrue(boxes.get(2)[1] <= 2 * 600 + 150);
        assertTrue(boxes.get(2)[3] >= 2 * 600 + 270);
    }

    @Test
    public void keepsThePaleBandOfAnOrderHeaderOutOfThePictures() {
        // a page that prints no picture at all: its bands are pale, its words are thin
        BufferedImage page = shopPage(2, java.awt.Color.WHITE);
        assertEquals(0, OirShots.boxesOf(page).size());
    }

    @Test
    public void leavesAPageOfOneOrderWithoutAPicture() {
        // one picture on a page says nothing about a column, and the title of an order sits in a
        // column of its own: rather than guess, the receipt line keeps the whole screenshot
        assertEquals(0, OirShots.boxesOf(shopPage(1, java.awt.Color.BLUE)).size());
    }

    @Test
    public void handsThePicturesOfOneBlockToItsOrders() {
        List<int[]> boxes = OirShots.boxesOf(shopPage(3, java.awt.Color.BLUE));
        // the first block prints rows 0..600, the second 600..1200
        List<Integer> first = OirShots.indexesWithin(boxes, 0, 600);
        assertEquals(1, first.size());
        assertEquals(Integer.valueOf(0), first.get(0));
        assertEquals(1, OirShots.indexesWithin(boxes, 600, 1200).size());
        assertEquals(1, OirShots.indexesWithin(boxes, 1200, 1800).size());
        assertEquals(0, OirShots.indexesWithin(boxes, 1900, 2000).size());
    }

    @Test
    public void scalesAPictureDownToASmallJpeg() throws IOException {
        BufferedImage page = shopPage(2, java.awt.Color.RED);
        List<int[]> boxes = OirShots.boxesOf(page);
        assertEquals(2, boxes.size());
        byte[] thumb = OirShots.thumbnailOf(page, boxes.get(0), 160);
        assertNotNull(thumb);
        // a JPEG starts with SOI, and a thumbnail of 160 pixels is a few kilobytes
        assertEquals(0xFF, thumb[0] & 0xFF);
        assertEquals(0xD8, thumb[1] & 0xFF);
        assertTrue(thumb.length > 100);
        assertTrue(thumb.length < 40000);
        assertEquals(160, ImageIO.read(new java.io.ByteArrayInputStream(thumb)).getWidth());
    }

    @Test
    public void keepsThePictureIdInsideAnIdField() {
        // a scan id is "SCAN-" and the milliseconds: 18 characters of the 20 an id field holds
        String scanId = "SCAN-1761700000000";
        assertEquals(18, scanId.length());
        assertEquals("SCAN-176170000000001", OirScan.shotIdOf(scanId, 1));
        // the tenth picture used to become "SCAN-1761700000000-10", which Derby shortened to the id of
        // the first one: every receipt line past the tenth showed the first product of the page
        assertEquals(20, OirScan.shotIdOf(scanId, 10).length());
        assertEquals(20, OirScan.shotIdOf(scanId, 99).length());
        assertNull(OirScan.shotIdOf(scanId, 100));
        assertNull(OirScan.shotIdOf(null, 1));
        java.util.Set<String> ids = new java.util.HashSet<>();
        for (int i = 1; i <= 99; i++) {
            ids.add(OirScan.shotIdOf(scanId, i));
        }
        assertEquals(99, ids.size());
    }

    @Test
    public void takesThePicturesNearestTheTopWhenThePagePrintsItsStripOfAdvertisements() {
        // the strip of the page (猜你喜欢) sits inside a block and has a picture of its own: a real page of
        // 26 orders printed 27 pictures, and the first order lost its picture to the strip
        assertEquals(Arrays.asList(Integer.valueOf(0), Integer.valueOf(1)),
                OirScan.picturesForOrders(
                        Arrays.asList(Integer.valueOf(0), Integer.valueOf(1), Integer.valueOf(2)), 2));
        assertEquals(Arrays.asList(Integer.valueOf(0)),
                OirScan.picturesForOrders(Collections.singletonList(Integer.valueOf(0)), 1));
        // fewer pictures than orders: nothing is handed over, the line keeps the whole screenshot
        assertTrue(OirScan.picturesForOrders(Collections.singletonList(Integer.valueOf(0)), 2).isEmpty());
        assertTrue(OirScan.picturesForOrders(null, 2).isEmpty());
        assertTrue(OirScan.picturesForOrders(Collections.singletonList(Integer.valueOf(0)), 0).isEmpty());
    }

    @Test
    public void givesUpAfterTwoMinutes() {
        // a page of 26 orders needs about 20 seconds, so two minutes is ten times the room - and no call
        // is ever handed less than a second (a short deadline must not mean a zero second wait)
        assertEquals(120, OirScan.SCAN_TIMEOUT_SECONDS);
        assertTrue(OirScan.secondsLeft(System.currentTimeMillis() + 60000L) > 30L);
        assertEquals(1L, OirScan.secondsLeft(System.currentTimeMillis() - 5000L));
    }

    @Test
    public void readsTheFieldsOfAVisionAnswer() {
        // the model may answer plain, fenced, or inside a chat envelope
        Map<String, String> plain = oneOrder(
                "{\"orderNo\":\"3316517475066009372\",\"shop\":\"天猫 risym旗舰店\","
                + "\"orderDate\":\"2026-10-18\",\"freight\":\"0\","
                + "\"lines\":\"CAP-104-50V,瓷片电容器 104,100,2.91\"}");
        assertEquals("3316517475066009372", plain.get("orderNo"));
        assertEquals("0", plain.get("freight"));
        assertEquals("CAP-104-50V,瓷片电容器 104,100,2.91", plain.get("lines"));
        // the date of an order is the day it was placed, not the day the parts arrived
        assertEquals("2026-10-18", plain.get("orderDate"));

        // an answer that still calls it date is read as well
        assertEquals("2026-10-19", oneOrder(
                "{\"orderNo\":\"45\",\"date\":\"2026-10-19\",\"lines\":\"X,part,1,1.00\"}").get("orderDate"));

        Map<String, String> fenced = oneOrder(
                "```json\n{\"orderNo\":\"46\",\"lines\":\"A,part,100,2.91\\nB,other,50,1.50\"}\n```");
        assertEquals("A,part,100,2.91\nB,other,50,1.50", fenced.get("lines"));

        Map<String, String> enveloped = oneOrder(
                "{\"choices\":[{\"message\":{\"content\":"
                + "\"{\\\"orderNo\\\":\\\"42\\\",\\\"lines\\\":\\\"X,part,10,1.00\\\"}\"}}]}");
        assertEquals("42", enveloped.get("orderNo"));
        assertEquals("X,part,10,1.00", enveloped.get("lines"));

        // a model that thinks first may leave content empty and put the answer in the reasoning
        Map<String, String> thinking = oneOrder(
                "{\"choices\":[{\"message\":{\"content\":\"\",\"reasoning_content\":"
                + "\"{\\\"orderNo\\\":\\\"43\\\",\\\"lines\\\":\\\"Y,part,5,9.90\\\"}\"}}]}");
        assertEquals("43", thinking.get("orderNo"));
        assertEquals("Y,part,5,9.90", thinking.get("lines"));

        // a model that escapes the newline twice must still give two rows
        Map<String, String> twiceEscaped = oneOrder(
                "{\"choices\":[{\"message\":{\"content\":"
                + "\"{\\\"orderNo\\\":\\\"44\\\",\\\"lines\\\":\\\"A,part,100,2.91\\\\nB,other,100,1.50\\\"}\"}}]}");
        assertEquals("44", twiceEscaped.get("orderNo"));
        assertEquals("A,part,100,2.91\nB,other,100,1.50", twiceEscaped.get("lines"));

        // an answer without lines is not usable, the person fills the form by hand
        assertNull(oneOrder("{\"orderNo\":\"42\"}"));
        assertNull(oneOrder(null));
    }

    @Test
    public void readsALongAnswerWithoutBlowingTheStack() {
        // a thinking model can answer with thousands of characters; a pattern based reader
        // overflowed the stack on this and the page showed an empty answer
        StringBuilder longLines = new StringBuilder("A1,part");
        for (int i = 0; i < 4000; i++) {
            longLines.append(",x\\t").append(i);
        }
        String answer = "{\"choices\":[{\"message\":{\"content\":\"{\\\"lines\\\":\\\"" + longLines
                + "\\\",\\\"orderNo\\\":\\\"123\\\"}\"}}]}";
        List<Map<String, String>> orders = OirOrders.orderListFrom(answer);
        assertEquals(1, orders.size());
        assertEquals("123", orders.get(0).get("orderNo"));
        assertTrue(orders.get(0).get("lines").startsWith("A1,part,x\t0,x\t1"));
        assertEquals(4000 + 1, orders.get(0).get("lines").split("\t").length);

        // a value that is not a string is skipped, the next occurrence of the key is read
        assertEquals("B", OirOrders.jsonStringField("{\"shop\":{\"a\":1},\"name\":\"B\"}", "name"));
        assertNull(OirOrders.jsonStringField("{\"lines\": 5}", "lines"));
        assertNull(OirOrders.jsonStringField("{\"lines\":\"open", "lines"));
        assertEquals("v", OirOrders.jsonStringField("  \"k\" : \"v\" }", "k"));
    }

    @Test
    public void readsARedPacketAsADiscount() {
        // the shop paid 10.73 for 6.00 + 3.00 of goods and 2.00 of freight: a 0.27 red packet. The
        // amount is not a guess - paid = goods + freight - discount fixes it - so it is filled in and
        // the block is not flagged: the row shows the discount in its 优惠 column (Step 162)
        Map<String, String> order = new HashMap<>();
        order.put("paidTotal", "10.73");
        order.put("freight", "2.00");
        order.put("lines", "TS-D014,switch,100,6.00\nSKT-125,socket,100,3.00");
        String note = OirOrders.checkOrderTotals(order);
        assertEquals("0.27", order.get("discount"));
        assertNull("a block whose money adds up is not flagged", note);
        assertEquals("2.00", order.get("freight"));
        // paying less than the goods and the freight is not a lost article: no retry for it
        assertFalse(OirOrders.totalsDisagree(order));

        // the model reported the discount itself: nothing to say
        Map<String, String> told = new HashMap<>();
        told.put("paidTotal", "10.73");
        told.put("freight", "2.00");
        told.put("discount", "0.27");
        told.put("lines", "TS-D014,switch,100,6.00\nSKT-125,socket,100,3.00");
        assertNull(OirOrders.checkOrderTotals(told));

        // but paying MORE than the lines still means an article is missing
        Map<String, String> short_ = new HashMap<>();
        short_.put("paidTotal", "14.40");
        short_.put("freight", "0.00");
        short_.put("discount", "0.00");
        short_.put("lines", "PIN-40,header pin,2,6.40");
        assertTrue(OirOrders.totalsDisagree(short_));
        assertTrue(OirOrders.checkOrderTotals(short_).contains("可能漏了一件商品"));

        // the summary takes the discount off the spend, not off the part prices
        java.util.List<Map<String, Object>> lines = new java.util.ArrayList<>();
        Map<String, Object> l1 = new HashMap<>();
        l1.put("goodsAmount", Double.valueOf(6.00));
        Map<String, Object> l2 = new HashMap<>();
        l2.put("goodsAmount", Double.valueOf(3.00));
        lines.add(l1);
        lines.add(l2);
        Map<String, Object> summary = MfgPurchaseServices.receiptSummary(lines, 2.00, 0.27);
        assertEquals(9.00, ((Double) summary.get("goodsTotal")).doubleValue(), 0.001);
        assertEquals(2.00, ((Double) summary.get("freightTotal")).doubleValue(), 0.001);
        assertEquals(0.27, ((Double) summary.get("discountTotal")).doubleValue(), 0.001);
        assertEquals(10.73, ((Double) summary.get("spendTotal")).doubleValue(), 0.001);
    }

    @Test
    public void doesNotTurnALostArticleIntoFreight() {
        // the shop paid 14.40 for two articles; the model answered only the first one
        Map<String, String> short_ = new HashMap<>();
        short_.put("paidTotal", "14.40");
        short_.put("freight", "0.00");
        short_.put("lines", "PIN-40,header pin,2,6.40");
        String note = OirOrders.checkOrderTotals(short_);
        assertTrue(note != null && note.contains("14.40"));
        assertTrue(note.contains("8.00"));
        // the freight stays what the screenshot says: the missing money is not a shipping fee
        assertEquals("0.00", short_.get("freight"));
        assertTrue(OirOrders.totalsDisagree(short_));

        // both articles read: the numbers agree and nothing is said
        Map<String, String> full = new HashMap<>();
        full.put("paidTotal", "14.40");
        full.put("freight", "0.00");
        full.put("lines", "PIN-40,header pin,2,6.40\nSOC-40,socket,2,8.00");
        assertNull(OirOrders.checkOrderTotals(full));
        assertFalse(OirOrders.totalsDisagree(full));

        // no freight printed at all is taken as 0, and the difference is treated as a discount
        Map<String, String> noFreight = new HashMap<>();
        noFreight.put("paidTotal", "9.84");
        noFreight.put("lines", "WIRE,wire,5,9.84");
        assertNull(OirOrders.checkOrderTotals(noFreight));
        assertEquals("0.00", noFreight.get("freight"));
        assertNull(noFreight.get("discount"));

        // a real freight of 5.00 with matching money is left alone
        Map<String, String> withFreight = new HashMap<>();
        withFreight.put("paidTotal", "8.88");
        withFreight.put("freight", "5.00");
        withFreight.put("lines", "CHIP,chip,5,3.88");
        assertNull(OirOrders.checkOrderTotals(withFreight));
        assertEquals("5.00", withFreight.get("freight"));
    }

    @Test
    public void fillsTheDiscountTheModelAnsweredAsZero() {
        // a real receipt: 货款 9.00 + 运费 2.00, 实付 10.73, and the model answered "discount":"0"
        // rather than leaving the field out. The 0.27 is not readable from the picture, but the
        // identity paid = goods + freight - discount fixes it, so it is written down instead of a red
        // "比实付还多" on a block whose money is right (Step 162)
        Map<String, String> order = new HashMap<>();
        order.put("paidTotal", "10.73");
        order.put("freight", "2.00");
        order.put("discount", "0");
        order.put("rowCount", "2");
        order.put("lines", "TS-D014,switch,100,6.00\nSKT-125,socket,100,3.00");
        assertNull(OirOrders.checkOrderTotals(order));
        assertEquals("0.27", order.get("discount"));

        // the same when the discount field is empty rather than zero
        Map<String, String> blank = new HashMap<>(order);
        blank.put("discount", "");
        assertNull(OirOrders.checkOrderTotals(blank));
        assertEquals("0.27", blank.get("discount"));

        // nothing is invented when the money already adds up
        Map<String, String> even = new HashMap<>();
        even.put("paidTotal", "11.00");
        even.put("freight", "2.00");
        even.put("discount", "0");
        even.put("lines", "TS-D014,switch,100,6.00\nSKT-125,socket,100,3.00");
        assertNull(OirOrders.checkOrderTotals(even));
        assertEquals("0", even.get("discount"));

        // a block that says it prints three rows while two were read is missing an article, and the
        // difference is not a coupon: it stays in the report
        Map<String, String> shortRead = new HashMap<>();
        shortRead.put("paidTotal", "10.73");
        shortRead.put("freight", "2.00");
        shortRead.put("discount", "0");
        shortRead.put("rowCount", "3");
        shortRead.put("lines", "TS-D014,switch,100,6.00\nSKT-125,socket,100,3.00");
        assertTrue(OirOrders.checkOrderTotals(shortRead).contains("请核对"));
        assertEquals("0", shortRead.get("discount"));

        // and paying more than the lines is a missing article, never a negative discount
        Map<String, String> paidMore = new HashMap<>();
        paidMore.put("paidTotal", "14.40");
        paidMore.put("freight", "0.00");
        paidMore.put("discount", "0");
        paidMore.put("lines", "PIN-40,header pin,2,6.40");
        assertTrue(OirOrders.checkOrderTotals(paidMore).contains("可能漏了一件商品"));
        assertEquals("0", paidMore.get("discount"));
    }

    @Test
    public void doesNotBookACopiedBlockAsADiscount() {
        // the real shape that came back from the 23 order page: one article of 195.65 answered five
        // times over, so 975.00 of goods against 195.65 paid. The difference (780.35) is smaller than
        // the goods, which is all the first version of the filled in discount asked - it booked a
        // 780 yuan discount on the order and said nothing at all (Step 162)
        Map<String, String> copied = new HashMap<>();
        copied.put("paidTotal", "195.65");
        copied.put("freight", "1.00");
        copied.put("discount", "0");
        copied.put("rowCount", "5");
        copied.put("lines", "RT6150AGQW,RT6150AGQW,100,195.00\n"
                + "RT6150B-33GQW,RT6150B-33GQW,100,195.00\n"
                + "RT6150BGQW,RT6150BGQW,100,195.00\n"
                + "RT6154AGQW,RT6154AGQW,100,195.00\n"
                + "RT6160AWSC,RT6160AWSC,100,195.00");
        String note = OirOrders.checkOrderTotals(copied);
        assertTrue("a copied block has to be reported, not made to add up: " + note,
                note != null && note.contains("比实付还多"));
        assertEquals("0", copied.get("discount"));

        // the same order read once: the freight sits inside the paid total and nothing is invented
        Map<String, String> once = new HashMap<>();
        once.put("paidTotal", "195.65");
        once.put("freight", "1.00");
        once.put("discount", "0");
        once.put("rowCount", "1");
        once.put("lines", "RT6150AGQW,RT6150AGQW,100,194.65");
        assertNull(OirOrders.checkOrderTotals(once));
        assertEquals("0", once.get("discount"));

        // a coupon never takes more than the dearest article of the order either: 9.00 of goods paid
        // with 0.50 of freight leaves 7.50 to explain, and no red packet of one order is that large
        Map<String, String> tooBig = new HashMap<>();
        tooBig.put("paidTotal", "2.00");
        tooBig.put("freight", "0.50");
        tooBig.put("discount", "0");
        tooBig.put("rowCount", "2");
        tooBig.put("lines", "TS-D014,switch,100,6.00\nSKT-125,socket,100,3.00");
        String told = OirOrders.checkOrderTotals(tooBig);
        assertEquals("0", tooBig.get("discount"));
        assertTrue("7.50 of a 9.00 order is no red packet: " + told, told != null);
    }

    @Test
    public void keepsARowWhoseFirstCellIsEmpty() {
        // a shop that prints no article code: the model answers with an empty first cell. Trimming the
        // line ate the tab that held that cell, so the row came back with three cells, was dropped, and
        // the import refused the whole order with "没有解析出明细" while the page showed the row happily
        // (Step 163)
        String line = "\tPCB打样电路板线路板pcb板打板打样批量生产加工源头厂家制作定制 [交易快照]\t1\t21.00";
        List<String[]> rows = OirOrders.parseOrderArticleLines(line);
        assertEquals(1, rows.size());
        assertTrue("the code comes from the name: " + rows.get(0)[0], rows.get(0)[0].startsWith("PCB"));
        assertEquals("1", rows.get(0)[2]);
        assertEquals("21.00", rows.get(0)[3]);
        // the same with spaces around the empty first cell, and with a comma
        assertEquals(1, OirOrders.parseOrderArticleLines("  ,开关,2,3.20").size());
        assertEquals(1, OirOrders.parseOrderArticleLines(",开关,2,3.20").size());
        // and the shape it really arrives in: OFBiz trims every request parameter, so the empty first
        // cell is gone before the service sees the text and the row has three cells
        List<String[]> trimmed = OirOrders.parseOrderArticleLines("PCB sample board\t1\t21.00");
        assertEquals(1, trimmed.size());
        assertTrue(trimmed.get(0)[0].startsWith("PCB"));
        assertEquals("1", trimmed.get(0)[2]);
        assertEquals("21.00", trimmed.get(0)[3]);
        // a row that is really code, name, quantity is not mistaken for one without a code
        assertEquals(0, OirOrders.parseOrderArticleLines("PCB sample board\tswitch\t1").size());
        // a line that really has too few cells is still skipped, and the header is not an article
        assertEquals(0, OirOrders.parseOrderArticleLines("\t只有名称\t1").size());
        assertEquals(0, OirOrders.parseOrderArticleLines("料号\t名称\t数量\t金额").size());
        // the fitted lines carry the code, so the checklist shows one instead of an empty cell
        String fitted = OirOrders.fitOrderLines(line);
        assertTrue("fitted: " + fitted, fitted.startsWith("PCB"));
        assertEquals(1, fitted.split("\n").length);
        assertEquals("1", fitted.split("\t")[2]);
    }

    @Test
    public void keepsTheEchoedTemplateOutOfTheOrders() {
        // a chatty model repeated the answer format: seven placeholder objects around the real order
        String answer = "{\"orders\":["
                + "{\"orderNo\":null,\"shop\":null,\"lines\":\"code\\tname\\tquantity\\tamount\"},"
                + "{\"orderNo\":\"\",\"lines\":\"code\\tname\\tquantity\\tamount\"},"
                + "{\"orderNo\":\"\",\"lines\":\"code\\tname\\tquantity\\tamount\"},"
                + "{\"orderNo\":\"3316868509015009372\",\"shop\":\"risym\",\"orderDate\":\"2026-09-16\","
                + "\"freight\":\"0.00\",\"paidTotal\":\"9.84\",\"refunded\":\"N\","
                + "\"lines\":\"10CM 母对母 2.54mm 1排40P\\t杜邦线\\t5\\t9.84\"}]}";
        List<Map<String, String>> orders = OirOrders.orderListFrom(answer);
        assertEquals(1, orders.size());
        assertEquals("3316868509015009372", orders.get(0).get("orderNo"));
        // an order without a number cannot be imported anyway: it is the key the import de-dupes on
        assertTrue(OirOrders.orderListFrom(
                "{\"orders\":[{\"lines\":\"A,part,1,1.00\"}]}").isEmpty());
        assertTrue(OirOrders.isLineTemplate("code,name,quantity,amount"));
        assertTrue(OirOrders.isLineTemplate("Code\tName\tQuantity\tAmount"));
        assertFalse(OirOrders.isLineTemplate("A1,part,1,1.00"));
        assertFalse(OirOrders.isLineTemplate("code1,part,1,1.00"));
        // an answer that falls back to explaining the format is a poor read: the scan sends it again
        assertTrue(OirOrders.echoesTemplate("{\"lines\":\"code\\tname\\tquantity\\tamount\"}"));
        assertTrue(OirOrders.echoesTemplate("please answer code,name,quantity,amount"));
        assertFalse(OirOrders.echoesTemplate("{\"orderNo\":\"1\",\"lines\":\"A,part,1,1.00\"}"));
        assertFalse(OirOrders.echoesTemplate(null));
    }

    @Test
    public void readsTheArticlesFromOneObjectEach() {
        // the answer carries items as objects: two similar products stay two products, which a
        // tab separated string got wrong (a row ending in "3.24;402530")
        String answer = "{\"orderCount\":\"1\",\"orders\":[{\"orderNo\":\"3026437575388500570\","
                + "\"shop\":\"科迪亚新能源科技\",\"orderDate\":\"2025-10-28\",\"paidTotal\":\"8.68\","
                + "\"freight\":\"2.50\",\"discount\":\"0\",\"refunded\":\"N\",\"items\":["
                + "{\"code\":\"502035(350mah)\",\"name\":\"3.7v聚合物锂电池\",\"qty\":\"1\",\"amount\":\"3.24\"},"
                + "{\"code\":\"402530(300mah)\",\"name\":\"3.7v聚合物锂电池\",\"qty\":\"1\",\"amount\":\"2.94\"}]}]}";
        List<Map<String, String>> orders = OirOrders.orderListFrom(answer);
        assertEquals(1, orders.size());
        String lines = orders.get(0).get("lines");
        String[] rows = lines.split("\n");
        assertEquals(2, rows.length);
        assertEquals("502035(350mah)\t3.7v聚合物锂电池\t1\t3.24", rows[0]);
        assertEquals("402530(300mah)\t3.7v聚合物锂电池\t1\t2.94", rows[1]);
        // the money of the two rows plus the freight is the paid total
        assertFalse(OirOrders.totalsDisagree(orders.get(0)));

        // quantity is read from either name, and a tab inside a field cannot break the row
        String withTabs = "{\"orders\":[{\"orderNo\":\"1\",\"items\":["
                + "{\"code\":\"A\",\"name\":\"two\\twords\",\"quantity\":\"100\",\"amount\":\"1.20\"}]}]}";
        List<Map<String, String>> tabbed = OirOrders.orderListFrom(withTabs);
        assertEquals(1, tabbed.size());
        assertEquals("A\ttwo words\t100\t1.20", tabbed.get(0).get("lines"));

        // an items array that only repeats the empty format is not an order
        assertTrue(OirOrders.orderListFrom("{\"orders\":[{\"orderNo\":\"9\",\"items\":["
                + "{\"code\":\"\",\"name\":\"\",\"qty\":\"\",\"amount\":\"\"}]}]}").isEmpty());
        // the old shape still reads, so old screenshots and the mock images keep working
        assertEquals(1, OirOrders.orderListFrom(
                "{\"orders\":[{\"orderNo\":\"7\",\"lines\":\"A,part,2,4.00\"}]}").size());
    }

    @Test
    public void gradesAnAnswerAndSpotsAnUnreadableRow() {
        // the best of several sends survives: two orders beat one, and more articles beat fewer
        List<Map<String, String>> two = OirOrders.orderListFrom("{\"orders\":["
                + "{\"orderNo\":\"1\",\"paidTotal\":\"8.68\",\"freight\":\"2.50\",\"items\":["
                + "{\"code\":\"A\",\"name\":\"battery\",\"qty\":\"1\",\"amount\":\"3.24\"},"
                + "{\"code\":\"B\",\"name\":\"battery\",\"qty\":\"1\",\"amount\":\"2.94\"}]},"
                + "{\"orderNo\":\"2\",\"paidTotal\":\"77.99\",\"freight\":\"0.00\",\"items\":["
                + "{\"code\":\"C\",\"name\":\"clip\",\"qty\":\"2\",\"amount\":\"77.99\"}]}]}");
        List<Map<String, String>> one = OirOrders.orderListFrom("{\"orders\":["
                + "{\"orderNo\":\"1\",\"paidTotal\":\"8.68\",\"freight\":\"2.50\",\"items\":["
                + "{\"code\":\"A\",\"name\":\"battery\",\"qty\":\"1\",\"amount\":\"3.24\"}]}]}");
        assertTrue(OirOrders.answerScore(two, 2) > OirOrders.answerScore(one, 2));
        assertTrue(OirOrders.answerScore(two, 2) > OirOrders.answerScore(two, 1));
        assertEquals(-1, OirOrders.answerScore(one.subList(0, 0), 2));
        assertFalse(OirOrders.anyUnreadable(two));
        // a row whose amount came back as "3.24;402530" is two articles squeezed into one
        Map<String, String> squeezed = new java.util.LinkedHashMap<>();
        squeezed.put("orderNo", "1");
        squeezed.put("paidTotal", "8.68");
        squeezed.put("freight", "2.50");
        squeezed.put("lines", "502035\tbattery\t1\t3.24;402530");
        assertTrue(OirOrders.anyUnreadable(Arrays.asList(squeezed)));
        // the squeezed answer is worth less than the same order read properly
        List<Map<String, String>> readProperly = OirOrders.orderListFrom("{\"orders\":["
                + "{\"orderNo\":\"1\",\"paidTotal\":\"8.68\",\"freight\":\"2.50\",\"items\":["
                + "{\"code\":\"A\",\"name\":\"battery\",\"qty\":\"1\",\"amount\":\"3.24\"},"
                + "{\"code\":\"B\",\"name\":\"battery\",\"qty\":\"1\",\"amount\":\"2.94\"}]}]}");
        assertTrue(OirOrders.answerScore(Arrays.asList(squeezed), 1)
                < OirOrders.answerScore(readProperly, 1));
    }

    @Test
    public void joinsTheOrdersOfTheSlicesOfOnePage() {
        // a long page is read slice by slice: the same order number is one order, its rows are
        // joined and missing fields are filled; the row seen in the overlap of two slices stays in
        // the joined rows (a shop may print one row twice on purpose) and the money decides later
        List<Map<String, String>> first = OirOrders.orderListFrom("{\"orders\":["
                + "{\"orderNo\":\"1\",\"shop\":\"shop A\",\"paidTotal\":\"11.00\",\"items\":["
                + "{\"code\":\"X\",\"name\":\"x\",\"qty\":\"1\",\"amount\":\"6.00\"}]}]}");
        List<Map<String, String>> second = OirOrders.orderListFrom("{\"orders\":["
                + "{\"orderNo\":\"1\",\"freight\":\"1.00\",\"items\":["
                + "{\"code\":\"X\",\"name\":\"x\",\"qty\":\"1\",\"amount\":\"6.00\"},"
                + "{\"code\":\"Y\",\"name\":\"y\",\"qty\":\"1\",\"amount\":\"4.00\"}]},"
                + "{\"orderNo\":\"2\",\"items\":[{\"code\":\"Z\",\"name\":\"z\",\"qty\":\"1\",\"amount\":\"3.00\"}]}]}");
        List<Map<String, String>> merged = OirOrders.mergeOrders(Arrays.asList(first, second));
        assertEquals(2, merged.size());
        assertEquals("1", merged.get(0).get("orderNo"));
        assertEquals("shop A", merged.get(0).get("shop"));
        assertEquals("1.00", merged.get(0).get("freight"));
        assertEquals(3, merged.get(0).get("lines").split("\n").length);
        // 6.00 + 4.00 and 1.00 freight is the 11.00 paid: the copy the overlap read is dropped
        OirOrders.tidyOrder(merged.get(0));
        String[] rows = merged.get(0).get("lines").split("\n");
        assertEquals(2, rows.length);
        assertTrue(rows[0].startsWith("X"));
        assertTrue(rows[1].startsWith("Y"));
        assertNull(OirOrders.checkOrderTotals(merged.get(0)));
        assertEquals("2", merged.get(1).get("orderNo"));
        // nothing to merge is not an error
        assertEquals(0, OirOrders.mergeOrders(Arrays.asList(Collections.emptyList())).size());
        assertEquals(0, OirOrders.mergeOrders(null).size());
    }

    @Test
    public void measuresTheHeightOfOneOrderBlock() throws IOException {
        // a page of identical blocks: a header strip, a title, a price row, then white. The darkness of
        // the page repeats with the height of the block, which is what the slicer measures instead of
        // trusting one number picked on one screenshot (Step 148)
        int block = 500;
        int blocks = 8;
        BufferedImage image = new BufferedImage(300, block * blocks, BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics2D pen = image.createGraphics();
        pen.setColor(java.awt.Color.WHITE);
        pen.fillRect(0, 0, 300, image.getHeight());
        pen.setColor(java.awt.Color.BLACK);
        for (int i = 0; i < blocks; i++) {
            int top = i * block;
            pen.fillRect(0, top + 10, 300, 40);
            pen.fillRect(20, top + 80, 200, 120);
            pen.fillRect(20, top + 260, 260, 20);
            pen.fillRect(20, top + 300, 120, 20);
        }
        pen.dispose();
        int measured = OirScan.blockHeightOf(image);
        assertTrue("block height " + measured + " is not " + block, Math.abs(measured - block) <= 20);
        // one slice holds two blocks
        assertEquals(2 * block, OirScan.sliceTargetOf(image));
        // a page that does not repeat (nothing printed on it) says nothing
        BufferedImage blank = new BufferedImage(300, 1200, BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics2D empty = blank.createGraphics();
        empty.setColor(java.awt.Color.WHITE);
        empty.fillRect(0, 0, 300, 1200);
        empty.dispose();
        assertEquals(0, OirScan.blockHeightOf(blank));
        assertEquals(OirScan.SLICE_HEIGHT, OirScan.sliceTargetOf(blank));
    }

    @Test
    public void cutsALongPageIntoOverlappingSlices() throws IOException {
        // a picture that already fits is sent as it is
        assertEquals(1, OirScan.slicesOf(png(300, 600)).size());
        assertEquals(1, OirScan.slicesOf(png(300, OirScan.SLICE_HEIGHT)).size());
        // a page taller than that is cut into slices, each one short enough to be read in one go
        List<byte[]> slices = OirScan.slicesOf(png(300, OirScan.ONE_CALL_HEIGHT + OirScan.SLICE_HEIGHT));
        assertTrue(slices.size() >= 2);
        for (byte[] slice : slices) {
            BufferedImage image = ImageIO.read(new java.io.ByteArrayInputStream(slice));
            assertTrue(image.getHeight() <= OirScan.SLICE_HEIGHT + OirScan.SLICE_SEARCH);
        }
        // something that is not an image never loses the scan
        assertEquals(1, OirScan.slicesOf(new byte[] {1, 2, 3}).size());
    }

    @Test
    public void overlapsTheSlicesSoAnOrderBlockIsNeverCutInTwo() throws IOException {
        // one band is one order block: the cut may fall through a band, but because the slices share
        // SLICE_OVERLAP pixels every band is still whole inside one of them. Without the overlap the
        // block the cut went through was reported by halves and dropped on production.
        int width = 200;
        int bands = 30;
        int band = 120;
        BufferedImage image = new BufferedImage(width, bands * band, BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics2D pen = image.createGraphics();
        int[] colours = new int[bands];
        for (int i = 0; i < bands; i++) {
            int rgb = ((10 + i * 7) << 16) | ((20 + i * 11) << 8) | (30 + i * 13);
            colours[i] = rgb & 0xFFFFFF;
            pen.setColor(new java.awt.Color(rgb));
            pen.fillRect(0, i * band, width, band);
        }
        pen.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        List<byte[]> slices = OirScan.slicesOf(out.toByteArray());
        assertTrue(slices.size() >= 2);
        assertTrue(OirScan.SLICE_OVERLAP < OirScan.SLICE_HEIGHT);
        java.util.Set<Integer> whole = new java.util.HashSet<>();
        for (byte[] slice : slices) {
            BufferedImage picture = ImageIO.read(new java.io.ByteArrayInputStream(slice));
            for (int i = 0; i < bands; i++) {
                int rows = 0;
                for (int y = 0; y < picture.getHeight(); y++) {
                    if ((picture.getRGB(0, y) & 0xFFFFFF) == colours[i]) {
                        rows++;
                    }
                }
                if (rows >= band) {
                    whole.add(Integer.valueOf(i));
                }
            }
        }
        for (int i = 0; i < bands; i++) {
            assertTrue("band " + i + " was cut in two by every slice", whole.contains(Integer.valueOf(i)));
        }
    }

    @Test
    public void joinsTheSameOrderWhoseNumberLostADigit() {
        // one block is shown by two overlapping slices, and one of the two reads lost a digit: the
        // page prints 3316453503083081392 and it came back once as 331645303083081392. The same shop
        // and the same paid money say it is one order, so the page is not shown with one order too
        // many (a page of 29 blocks was reported as 30, Step 131)
        List<Map<String, String>> whole = OirOrders.orderListFrom("{\"orders\":["
                + "{\"orderNo\":\"3316453503083081392\",\"shop\":\"华瑞芯城\",\"paidTotal\":\"22.24\","
                + "\"freight\":\"2.80\",\"items\":[{\"code\":\"CH32V002J4M6\",\"name\":\"ch32\","
                + "\"qty\":\"30\",\"amount\":\"19.44\"}]}]}");
        List<Map<String, String>> shortened = OirOrders.orderListFrom("{\"orders\":["
                + "{\"orderNo\":\"331645303083081392\",\"shop\":\"华瑞芯城\",\"paidTotal\":\"22.24\","
                + "\"freight\":\"2.80\",\"items\":[{\"code\":\"CH32V002J4M6\",\"name\":\"ch32\","
                + "\"qty\":\"30\",\"amount\":\"19.44\"}]}]}");
        List<Map<String, String>> merged = OirOrders.mergeOrders(Arrays.asList(whole, shortened));
        assertEquals(1, merged.size());
        // the number the page prints stays, the short read only joins it
        assertEquals("3316453503083081392", merged.get(0).get("orderNo"));
        assertEquals(2, merged.get(0).get("lines").split("\n").length);
        OirOrders.tidyOrder(merged.get(0));
        assertEquals(1, merged.get(0).get("lines").split("\n").length);
        assertNull(OirOrders.checkOrderTotals(merged.get(0)));
        // the shop of the two reads is not asked: the number and the shop sit on the same strip, so a
        // misread hits either of them - the money of the block is what says both reads are one block
        List<Map<String, String>> otherShop = OirOrders.orderListFrom("{\"orders\":["
                + "{\"orderNo\":\"331645303083081392\",\"shop\":\"别的店\",\"paidTotal\":\"22.24\","
                + "\"freight\":\"2.80\",\"items\":[{\"code\":\"CH32V002J4M6\",\"name\":\"ch32\","
                + "\"qty\":\"30\",\"amount\":\"19.44\"}]}]}");
        assertEquals(1, OirOrders.mergeOrders(Arrays.asList(whole, otherShop)).size());
        // a read that left the shop empty is still the same order when the money is the same
        List<Map<String, String>> noShop = OirOrders.orderListFrom("{\"orders\":["
                + "{\"orderNo\":\"331645303083081392\",\"shop\":\"\",\"paidTotal\":\"22.24\","
                + "\"freight\":\"2.80\",\"items\":[{\"code\":\"CH32V002J4M6\",\"name\":\"ch32\","
                + "\"qty\":\"30\",\"amount\":\"19.44\"}]}]}");
        assertEquals(1, OirOrders.mergeOrders(Arrays.asList(whole, noShop)).size());
        // and so is other money
        List<Map<String, String>> otherMoney = OirOrders.orderListFrom("{\"orders\":["
                + "{\"orderNo\":\"331645303083081392\",\"shop\":\"华瑞芯城\",\"paidTotal\":\"9.99\","
                + "\"items\":[{\"code\":\"CH32V002J4M6\",\"name\":\"ch32\",\"qty\":\"1\","
                + "\"amount\":\"9.99\"}]}]}");
        assertEquals(2, OirOrders.mergeOrders(Arrays.asList(whole, otherMoney)).size());
        // two numbers that differ by more than one character are never the same order
        assertTrue(OirOrders.numbersOfTheSameOrder("3316453503083081392", "331645303083081392"));
        // a read may also lose three digits: 3316453503083081392 came back once as 3316453503081392
        assertTrue(OirOrders.numbersOfTheSameOrder("3316453503083081392", "3316453503081392"));
        assertTrue(OirOrders.numbersOfTheSameOrder("3316453503083081392", "3316453503083081393"));
        // and two neighbouring digits are read the other way round now and then
        assertTrue(OirOrders.numbersOfTheSameOrder("3316115535567018554", "3316115553567018554"));
        assertFalse(OirOrders.numbersOfTheSameOrder("3316115535567018554", "3316115535567099554"));
        assertFalse(OirOrders.numbersOfTheSameOrder("3316452855010009372", "3316485867036009372"));
        assertFalse(OirOrders.numbersOfTheSameOrder("3316453503083081392", "3316453503083081392"));
        assertFalse(OirOrders.numbersOfTheSameOrder("3316453503083081392", "3453508"));
        assertFalse(OirOrders.numbersOfTheSameOrder("", "3316453503083081392"));
        assertFalse(OirOrders.numbersOfTheSameOrder(null, "3316453503083081392"));
    }

    @Test
    public void keepsTwoBlocksThatWereReadWithTheSameNumber() {
        // a page never prints one order number on two blocks: when two blocks of different money come
        // back with the same number one of the reads is wrong, and merging them would drop a whole
        // order into the other (a page of 31 blocks came back with 30, Step 134)
        List<Map<String, String>> page = OirOrders.orderListFrom("{\"orders\":["
                + "{\"orderNo\":\"3316374446353172593\",\"shop\":\"telesky\",\"paidTotal\":\"2.63\","
                + "\"items\":[{\"code\":\"2.54mm 1*40\",\"name\":\"pin header\",\"qty\":\"5\","
                + "\"amount\":\"0.63\"}]},"
                + "{\"orderNo\":\"3316374446353172593\",\"shop\":\"telesky\",\"paidTotal\":\"3.87\","
                + "\"items\":[{\"code\":\"SOP16\",\"name\":\"adapter\",\"qty\":\"10\","
                + "\"amount\":\"3.87\"}]}]}");
        List<Map<String, String>> merged = OirOrders.mergeOrders(Collections.singletonList(page));
        assertEquals(2, merged.size());
        assertEquals(1, OirOrders.parseOrderArticleLines(merged.get(0).get("lines")).size());
        assertEquals(1, OirOrders.parseOrderArticleLines(merged.get(1).get("lines")).size());
        // the second one says why it carries the same number
        assertNotNull(merged.get(1).get("note"));
        // two reads of one block still join: same number, same money, and read from two pictures (the
        // overlap of two slices), which is the only way one block is seen twice
        List<Map<String, String>> first = OirOrders.orderListFrom("{\"orders\":["
                + "{\"orderNo\":\"3316374446353172593\",\"paidTotal\":\"2.63\","
                + "\"items\":[{\"code\":\"X\",\"name\":\"x\",\"qty\":\"5\",\"amount\":\"0.63\"}]}]}");
        List<Map<String, String>> second = OirOrders.orderListFrom("{\"orders\":["
                + "{\"orderNo\":\"3316374446353172593\",\"paidTotal\":\"2.63\","
                + "\"items\":[{\"code\":\"Y\",\"name\":\"y\",\"qty\":\"2\",\"amount\":\"2.00\"}]}]}");
        assertEquals(1, OirOrders.mergeOrders(Arrays.asList(first, second)).size());
    }

    @Test
    public void bringsTheTwoHalvesOfOneBlockBackTogether() {
        // the block prints 原装正品 TPS63051RMWR … ¥2.18 x100 with 实付款 ¥217.90, and the model answered
        // that one row twice: once with the money of the order and once with the price of one piece
        // (217.90 is 100 x 2.18). The piece price is not an order of its own - a page of 23 orders came
        // back with 24 - so only the read that carries the block's money stays
        List<Map<String, String>> picture = OirOrders.orderListFrom("{\"orderCount\":\"1\",\"orders\":["
                + "{\"orderNo\":\"3071465510024500570\",\"shop\":\"深圳市华胜特电子商城\","
                + "\"paidTotal\":\"217.90\",\"freight\":\"0.00\",\"rowCount\":\"1\","
                + "\"items\":[{\"code\":\"TPS63051RMWR\",\"name\":\"buck converter\",\"qty\":\"100\","
                + "\"amount\":\"217.90\"}]},"
                + "{\"orderNo\":\"3071465510024500570\",\"shop\":\"深圳市华胜特电子商城\","
                + "\"paidTotal\":\"2.18\",\"freight\":\"0.00\",\"rowCount\":\"1\","
                + "\"items\":[{\"code\":\"TPS63051RMWR\",\"name\":\"buck converter\",\"qty\":\"100\","
                + "\"amount\":\"2.18\"}]}]}");
        // the count the model gives does not matter: the money says the second read is the piece price
        for (Integer said : new Integer[] {null, Integer.valueOf(1), Integer.valueOf(2)}) {
            List<Integer> counts = said == null ? null : Collections.singletonList(said);
            List<Map<String, String>> merged = OirOrders.mergeOrders(Collections.singletonList(picture),
                    counts);
            assertEquals(1, merged.size());
            assertEquals(1, OirOrders.parseOrderArticleLines(merged.get(0).get("lines")).size());
            assertEquals("217.90", merged.get(0).get("paidTotal"));
            assertNotNull(merged.get(0).get("note"));
            assertNull(OirOrders.checkOrderTotals(merged.get(0)));
        }

        // a block of two real articles that came back as two objects is joined again when the picture
        // says it holds one block: both rows stay and the money is the two halves added up
        List<Map<String, String>> splitBlock = OirOrders.orderListFrom("{\"orderCount\":\"1\",\"orders\":["
                + "{\"orderNo\":\"3071465510024500571\",\"shop\":\"深圳市华胜特电子商城\","
                + "\"paidTotal\":\"10.00\",\"freight\":\"0.00\",\"rowCount\":\"2\","
                + "\"items\":[{\"code\":\"A\",\"name\":\"a\",\"qty\":\"1\",\"amount\":\"10.00\"}]},"
                + "{\"orderNo\":\"3071465510024500571\",\"shop\":\"深圳市华胜特电子商城\","
                + "\"paidTotal\":\"5.00\",\"freight\":\"0.00\",\"rowCount\":\"2\","
                + "\"items\":[{\"code\":\"B\",\"name\":\"b\",\"qty\":\"3\",\"amount\":\"5.00\"}]}]}");
        List<Map<String, String>> joined = OirOrders.mergeOrders(Collections.singletonList(splitBlock),
                Collections.singletonList(Integer.valueOf(1)));
        assertEquals(1, joined.size());
        assertEquals(2, OirOrders.parseOrderArticleLines(joined.get(0).get("lines")).size());
        assertEquals("15.00", joined.get(0).get("paidTotal"));

        // a picture that says it holds two blocks keeps the two blocks
        List<Map<String, String>> twoBlocks = OirOrders.orderListFrom("{\"orderCount\":\"2\",\"orders\":["
                + "{\"orderNo\":\"3071465510024500571\",\"shop\":\"深圳市华胜特电子商城\","
                + "\"paidTotal\":\"10.00\",\"rowCount\":\"1\","
                + "\"items\":[{\"code\":\"A\",\"name\":\"a\",\"qty\":\"1\",\"amount\":\"10.00\"}]},"
                + "{\"orderNo\":\"3071465510024500571\",\"shop\":\"深圳市华胜特电子商城\","
                + "\"paidTotal\":\"5.00\",\"rowCount\":\"1\","
                + "\"items\":[{\"code\":\"B\",\"name\":\"b\",\"qty\":\"3\",\"amount\":\"5.00\"}]}]}");
        assertEquals(2, OirOrders.mergeOrders(Collections.singletonList(twoBlocks),
                Collections.singletonList(Integer.valueOf(2))).size());

        // two shops are two orders however the count reads: the number and the shop sit on one strip
        List<Map<String, String>> twoShops = OirOrders.orderListFrom("{\"orderCount\":\"1\",\"orders\":["
                + "{\"orderNo\":\"3071465510024500571\",\"shop\":\"深圳市华胜特电子商城\","
                + "\"paidTotal\":\"10.00\",\"rowCount\":\"1\","
                + "\"items\":[{\"code\":\"A\",\"name\":\"a\",\"qty\":\"1\",\"amount\":\"10.00\"}]},"
                + "{\"orderNo\":\"3071465510024500571\",\"shop\":\"深圳市德明电子贸易商\","
                + "\"paidTotal\":\"5.00\",\"rowCount\":\"1\","
                + "\"items\":[{\"code\":\"B\",\"name\":\"b\",\"qty\":\"3\",\"amount\":\"5.00\"}]}]}");
        assertEquals(2, OirOrders.mergeOrders(Collections.singletonList(twoShops),
                Collections.singletonList(Integer.valueOf(1))).size());
    }

    @Test
    public void joinsTwoReadsOfOneBlockThatPrintTheSamePaidTotal() {
        // the block prints four rows of 4.00 and 实付款 16.00; the model answered it as two objects of
        // the same number, the same shop and the same 16.00 - two rows of 4.00 and one row of 16.00.
        // A page prints one 实付款 per block, so the two objects are that one block (Step 146)
        List<Map<String, String>> picture = OirOrders.orderListFrom("{\"orderCount\":\"2\",\"orders\":["
                + "{\"orderNo\":\"3071440382104500570\",\"shop\":\"诚信达电子科技\",\"paidTotal\":\"16.00\","
                + "\"freight\":\"0.00\",\"rowCount\":\"4\",\"items\":["
                + "{\"code\":\"10K 50个\",\"name\":\"排阻\",\"qty\":\"100\",\"amount\":\"4.00\"},"
                + "{\"code\":\"100K 50个\",\"name\":\"排阻\",\"qty\":\"100\",\"amount\":\"4.00\"}]},"
                + "{\"orderNo\":\"3071440382104500570\",\"shop\":\"诚信达电子科技\",\"paidTotal\":\"16.00\","
                + "\"freight\":\"0.00\",\"rowCount\":\"4\",\"items\":["
                + "{\"code\":\"0603*4贴片排阻\",\"name\":\"排阻\",\"qty\":\"100\",\"amount\":\"16.00\"}]}]}");
        List<Map<String, String>> merged = OirOrders.mergeOrders(Collections.singletonList(picture));
        assertEquals(1, merged.size());
        // the row that carries the whole 实付款 is the header read as an article, so it is dropped and
        // what was really read of the block stays
        OirOrders.tidyOrder(merged.get(0));
        String[] rows = merged.get(0).get("lines").split("\n");
        assertEquals(2, rows.length);
        assertEquals("4.00", rows[0].split("\t")[3]);
        assertEquals("4.00", rows[1].split("\t")[3]);
        assertEquals("16.00", merged.get(0).get("paidTotal"));
        // the rest of the block was not read, and the page keeps saying so
        assertNotNull(OirOrders.checkOrderTotals(merged.get(0)));
    }

    @Test
    public void dropsTheSecondReadThatLostADigitAndCarriesThePriceOfOnePiece() {
        // the same block came back twice: 3071465510024500570 with 217.90 and 307146551002450570 (one
        // digit short) with the 2.18 of a single piece. The price does not change because the number
        // lost a digit: 100 x 2.18 is the 217.90 that was paid (Step 144)
        List<Map<String, String>> picture = OirOrders.orderListFrom("{\"orderCount\":\"2\",\"orders\":["
                + "{\"orderNo\":\"3071465510024500570\",\"shop\":\"深圳市华胜特电子商城\","
                + "\"paidTotal\":\"217.90\",\"rowCount\":\"1\",\"items\":["
                + "{\"code\":\"TPS63051RMWR\",\"name\":\"buck\",\"qty\":\"100\",\"amount\":\"217.90\"}]},"
                + "{\"orderNo\":\"307146551002450570\",\"shop\":\"深圳市华胜特电子商城\","
                + "\"paidTotal\":\"2.18\",\"rowCount\":\"1\",\"items\":["
                + "{\"code\":\"TPS63051RMWR\",\"name\":\"buck\",\"qty\":\"100\",\"amount\":\"2.18\"}]}]}");
        List<Map<String, String>> merged = OirOrders.mergeOrders(Collections.singletonList(picture));
        assertEquals(1, merged.size());
        assertEquals("217.90", merged.get(0).get("paidTotal"));
        assertEquals(1, OirOrders.parseOrderArticleLines(merged.get(0).get("lines")).size());
    }

    @Test
    public void joinsAFragmentOfABlockThatCarriesNoMoney() {
        // the overlap of two slices showed the articles of the last block without its header, and the
        // second read came back with the same number and no 实付款 at all. A block always prints one,
        // so that read is a piece of the block and not a second order (Step 147)
        List<Map<String, String>> picture = OirOrders.orderListFrom("{\"orderCount\":\"2\",\"orders\":["
                + "{\"orderNo\":\"3071758657686500570\",\"shop\":\"深圳市芯信世纪电子\",\"paidTotal\":\"534.10\","
                + "\"freight\":\"0.00\",\"rowCount\":\"1\",\"items\":["
                + "{\"code\":\"STM32F103\",\"name\":\"mcu\",\"qty\":\"10\",\"amount\":\"534.10\"}]},"
                + "{\"orderNo\":\"3071758657686500570\",\"shop\":\"\",\"paidTotal\":\"0.00\","
                + "\"rowCount\":\"1\",\"items\":["
                + "{\"code\":\"STM32F103\",\"name\":\"mcu\",\"qty\":\"10\",\"amount\":\"0.00\"}]}]}");
        List<Map<String, String>> merged = OirOrders.mergeOrders(Collections.singletonList(picture));
        assertEquals(1, merged.size());
        assertEquals("534.10", merged.get(0).get("paidTotal"));
        // the same fragment seen from another slice is joined as well
        List<Map<String, String>> first = OirOrders.orderListFrom("{\"orders\":["
                + "{\"orderNo\":\"3071758657686500570\",\"shop\":\"深圳市芯信世纪电子\",\"paidTotal\":\"534.10\","
                + "\"rowCount\":\"1\",\"items\":[{\"code\":\"A\",\"name\":\"a\",\"qty\":\"10\",\"amount\":\"534.10\"}]}]}");
        List<Map<String, String>> fragment = OirOrders.orderListFrom("{\"orders\":["
                + "{\"orderNo\":\"3071758657686500570\",\"shop\":\"\",\"paidTotal\":\"0.00\","
                + "\"rowCount\":\"1\",\"items\":[{\"code\":\"A\",\"name\":\"a\",\"qty\":\"10\",\"amount\":\"0.00\"}]}]}");
        assertEquals(1, OirOrders.mergeOrders(Arrays.asList(first, fragment)).size());
        // two blocks that both print a money stay apart
        List<Map<String, String>> twoMonies = OirOrders.orderListFrom("{\"orders\":["
                + "{\"orderNo\":\"3071758657686500570\",\"shop\":\"深圳市芯信世纪电子\",\"paidTotal\":\"534.10\","
                + "\"rowCount\":\"1\",\"items\":[{\"code\":\"A\",\"name\":\"a\",\"qty\":\"1\",\"amount\":\"534.10\"}]},"
                + "{\"orderNo\":\"3071758657686500570\",\"shop\":\"深圳市芯信世纪电子\",\"paidTotal\":\"8.00\","
                + "\"rowCount\":\"1\",\"items\":[{\"code\":\"B\",\"name\":\"b\",\"qty\":\"1\",\"amount\":\"8.00\"}]}]}");
        assertEquals(2, OirOrders.mergeOrders(Collections.singletonList(twoMonies)).size());
    }

    @Test
    public void seesABlockThatCarriedTheArticlesOfTheBlockAbove() {
        // the codes and names of one printed line are written differently by two reads, so the blocks
        // look different although the articles are the same: the money tells it (a block on the page
        // came back with the breadboard of the block above it, Step 137)
        Map<String, String> first = new LinkedHashMap<>();
        first.put("orderNo", "3316822177083054564");
        first.put("paidTotal", "39.50");
        first.put("freight", "1.50");
        first.put("lines", "SYB-500面包板(240*200*8.5MM) 组合型（1个）\t400孔面包板线MB-102 SYB-500\t2\t38.00");
        Map<String, String> second = new LinkedHashMap<>();
        second.put("orderNo", "3316819945036045382");
        second.put("paidTotal", "39.50");
        second.put("freight", "1.50");
        second.put("lines", "SYB-500\t400孔面包板线MB-102 SYB-500 [交易快照]\t2\t38.00");
        assertFalse(OirOrders.orderContentKey(first).equals(OirOrders.orderContentKey(second)));
        assertEquals(OirOrders.moneyKeyOf(first), OirOrders.moneyKeyOf(second));
        // another order of other articles is not the same block
        Map<String, String> other = new LinkedHashMap<>();
        other.put("orderNo", "3316999260043018554");
        other.put("paidTotal", "52.60");
        other.put("freight", "3.00");
        other.put("lines", "NanoDLA\tNanoDLA 逻辑分析仪\t1\t49.60");
        assertFalse(OirOrders.moneyKeyOf(first).equals(OirOrders.moneyKeyOf(other)));
        assertNull(OirOrders.moneyKeyOf(new LinkedHashMap<>()));
    }

    @Test
    public void keepsTwoAlikeOrdersThatThePagePrintsUnderDifferentNumbers() {
        // the page holds two orders with the same articles and the same money and different numbers;
        // one picture shows each block once, so two blocks of one answer with two numbers are two
        // orders - merging them lost the second (29 orders came back as 28, Step 135)
        List<Map<String, String>> picture = OirOrders.orderListFrom("{\"orders\":["
                + "{\"orderNo\":\"3316981944203136583\",\"shop\":\"欧贝特旗舰店\",\"paidTotal\":\"2.00\","
                + "\"items\":[{\"code\":\"0603 200R\",\"name\":\"resistor\",\"qty\":\"100\","
                + "\"amount\":\"2.00\"}]},"
                + "{\"orderNo\":\"3316981944203136599\",\"shop\":\"欧贝特旗舰店\",\"paidTotal\":\"2.00\","
                + "\"items\":[{\"code\":\"0603 200R\",\"name\":\"resistor\",\"qty\":\"100\","
                + "\"amount\":\"2.00\"}]}]}");
        List<Map<String, String>> merged = OirOrders.mergeOrders(Collections.singletonList(picture));
        assertEquals(2, merged.size());
        // but the very same block read twice in one answer is one block: the same number with exactly
        // the same articles and the same money is not two orders (a page of 27 came back with 28)
        List<Map<String, String>> twice = OirOrders.orderListFrom("{\"orders\":["
                + "{\"orderNo\":\"3111921698687500570\",\"shop\":\"安致二三极管\",\"paidTotal\":\"10.00\","
                + "\"freight\":\"2.00\",\"items\":[{\"code\":\"0.5A\",\"name\":\"fuse\",\"qty\":\"200\","
                + "\"amount\":\"8.00\"}]},"
                + "{\"orderNo\":\"3111921698687500570\",\"shop\":\"\",\"paidTotal\":\"10.00\","
                + "\"freight\":\"2.00\",\"items\":[{\"code\":\"0.5A\",\"name\":\"fuse\",\"qty\":\"200\","
                + "\"amount\":\"8.00\"}]}]}");
        List<Map<String, String>> one = OirOrders.mergeOrders(Collections.singletonList(twice));
        assertEquals(1, one.size());
        assertEquals("安致二三极管", one.get(0).get("shop"));
        OirOrders.tidyOrder(one.get(0));
        assertEquals(1, one.get(0).get("lines").split("\n").length);
        // the shop is printed on the same strip as the number, so the same number with the shop lost
        // once is the same block - even when the two reads do not agree on the articles (Step 141)
        List<Map<String, String>> fragment = OirOrders.orderListFrom("{\"orders\":["
                + "{\"orderNo\":\"3111921698687500570\",\"shop\":\"安致二三极管\",\"paidTotal\":\"10.00\","
                + "\"freight\":\"2.00\",\"items\":[{\"code\":\"0.5A\",\"name\":\"fuse\",\"qty\":\"500\","
                + "\"amount\":\"8.00\"}]},"
                + "{\"orderNo\":\"3111921698687500570\",\"shop\":\"\",\"paidTotal\":\"2.00\","
                + "\"freight\":\"2.00\",\"items\":[{\"code\":\"0.5A\",\"name\":\"fuse\",\"qty\":\"200\","
                + "\"amount\":\"2.00\"}]}]}");
        assertEquals(1, OirOrders.mergeOrders(Collections.singletonList(fragment)).size());
        // two blocks that both print a shop stay apart when they came back with one number and two
        // different monies: that is two orders misnumbered, not one block read twice
        List<Map<String, String>> misnumbered = OirOrders.orderListFrom("{\"orders\":["
                + "{\"orderNo\":\"3316377687042009372\",\"shop\":\"优利德品牌企业店\",\"paidTotal\":\"3.87\","
                + "\"items\":[{\"code\":\"SOP16\",\"name\":\"adapter\",\"qty\":\"10\",\"amount\":\"3.87\"}]},"
                + "{\"orderNo\":\"3316377687042009372\",\"shop\":\"优利德品牌企业店\",\"paidTotal\":\"113.16\","
                + "\"items\":[{\"code\":\"UT890C\",\"name\":\"meter\",\"qty\":\"1\",\"amount\":\"113.16\"}]}]}");
        assertEquals(2, OirOrders.mergeOrders(Collections.singletonList(misnumbered)).size());
    }

    @Test
    public void mergesAnOrderReadTwiceWithATooShortNumber() {
        // one block came back as 3141164514099372 beside the same block read in full: the page prints
        // its numbers at 19 digits, and the short read carries exactly the same articles, the same shop
        // and the same money, so it is the same order read twice (Step 133)
        List<Map<String, String>> firstSlice = OirOrders.orderListFrom("{\"orders\":["
                + "{\"orderNo\":\"3316900333085054564\",\"shop\":\"华瑞芯城\",\"paidTotal\":\"7.00\","
                + "\"items\":[{\"code\":\"CH32V002J4M6\",\"name\":\"ch32\",\"qty\":\"10\","
                + "\"amount\":\"6.44\"}]},"
                + "{\"orderNo\":\"3316900549003018554\",\"shop\":\"华瑞芯城\",\"paidTotal\":\"9.24\","
                + "\"items\":[{\"code\":\"CH32V002J4M6\",\"name\":\"ch32\",\"qty\":\"10\","
                + "\"amount\":\"6.44\"}]},"
                + "{\"orderNo\":\"3316901197252018554\",\"shop\":\"优信电子\",\"paidTotal\":\"1.33\","
                + "\"items\":[{\"code\":\"SH1.0\",\"name\":\"端子线\",\"qty\":\"10\",\"amount\":\"1.33\"}]}]}");
        List<Map<String, String>> secondSlice = OirOrders.orderListFrom("{\"orders\":["
                + "{\"orderNo\":\"3141164514099372\",\"shop\":\"华瑞芯城\",\"paidTotal\":\"9.24\","
                + "\"items\":[{\"code\":\"CH32V002J4M6\",\"name\":\"ch32\",\"qty\":\"10\","
                + "\"amount\":\"6.44\"}]}]}");
        List<Map<String, String>> merged = OirOrders.mergeOrders(Arrays.asList(firstSlice, secondSlice));
        assertEquals(3, merged.size());
        for (Map<String, String> order : merged) {
            assertFalse("3141164514099372".equals(order.get("orderNo")));
        }
        // the same articles of two different shops are two orders, however short one number is
        List<Map<String, String>> otherShops = OirOrders.orderListFrom("{\"orders\":["
                + "{\"orderNo\":\"3316900333085054564\",\"shop\":\"华瑞芯城\",\"paidTotal\":\"7.00\","
                + "\"items\":[{\"code\":\"A\",\"name\":\"a\",\"qty\":\"1\",\"amount\":\"7.00\"}]},"
                + "{\"orderNo\":\"3316900549003018554\",\"shop\":\"别家\",\"paidTotal\":\"7.00\","
                + "\"items\":[{\"code\":\"A\",\"name\":\"a\",\"qty\":\"1\",\"amount\":\"7.00\"}]},"
                + "{\"orderNo\":\"3316901197252018554\",\"shop\":\"优信电子\",\"paidTotal\":\"1.33\","
                + "\"items\":[{\"code\":\"B\",\"name\":\"b\",\"qty\":\"1\",\"amount\":\"1.33\"}]}]}");
        List<Map<String, String>> thirdShop = OirOrders.orderListFrom("{\"orders\":["
                + "{\"orderNo\":\"3141164514099372\",\"shop\":\"第三家\",\"paidTotal\":\"7.00\","
                + "\"items\":[{\"code\":\"A\",\"name\":\"a\",\"qty\":\"1\",\"amount\":\"7.00\"}]}]}");
        assertEquals(4, OirOrders.mergeOrders(Arrays.asList(otherShops, thirdShop)).size());
    }

    @Test
    public void dropsTheRowTheOverlapReadTwiceWithAnotherCode() {
        // the two reads of one row wrote the code differently (FC-135 beside 无源贴片FC-135…), so the
        // row identity cannot see that they are one row; the block says it prints one row, and two rows
        // of the same quantity and the same money are the same printed row (Step 134)
        Map<String, String> order = new LinkedHashMap<>();
        order.put("paidTotal", "2.25");
        order.put("freight", "1.00");
        order.put("rowCount", "1");
        order.put("lines", "FC-135\t无源贴片FC-135 32.768K 3215\t5\t2.25\n"
                + "FC-135 32.768K 3215\t无源贴片FC-135 32.768K 3215 [交易快照]\t5\t2.25");
        OirOrders.tidyOrder(order);
        String[] rows = order.get("lines").split("\n");
        assertEquals(1, rows.length);
        assertTrue(rows[0].startsWith("FC-135"));
        // the freight the model reported stands as it does for every other block (Step 129), so the
        // article is what was paid less the freight - what matters here is that the block adds up and
        // the page does not have to warn about a row that was read twice
        assertNull(OirOrders.checkOrderTotals(order));
        // a block that prints as many rows as were read is left alone, even when two of them share
        // their quantity and their money (six colours of one board are six rows)
        Map<String, String> colours = new LinkedHashMap<>();
        colours.put("paidTotal", "41.28");
        colours.put("rowCount", "6");
        colours.put("lines", "A\t白色\t5\t6.88\nB\t蓝色\t5\t6.88\nC\t绿色\t5\t6.88\n"
                + "D\t黄色\t5\t6.88\nE\t红色\t5\t6.88\nF\t黑色\t5\t6.88");
        OirOrders.tidyOrder(colours);
        assertEquals(6, colours.get("lines").split("\n").length);
    }

    @Test
    public void leavesTheProductPicturesBelowTheOrderListOutOfTheLastOrder() {        // under the last block the page prints the pictures of other products (猜你喜欢 / 为你推荐) with
        // their prices; the model hands them to the order above, which then comes to more than was paid
        Map<String, String> order = new LinkedHashMap<>();
        order.put("paidTotal", "12.75");
        order.put("freight", "0.00");
        order.put("lines", "ATECC608B\tATECC608B 加密芯片\t5\t12.75\n"
                + "面包板\t某面包板\t1\t9.90\n"
                + "杜邦线\t某杜邦线\t1\t5.00");
        OirOrders.tidyOrder(order);
        String[] rows = order.get("lines").split("\n");
        assertEquals(1, rows.length);
        assertTrue(rows[0].startsWith("ATECC608B"));
        assertNull(OirOrders.checkOrderTotals(order));
        // a picture carries no money of its own either: a row without an amount at the end of a block
        // that did cost money is not an article of it
        Map<String, String> free = new LinkedHashMap<>();
        free.put("paidTotal", "9.84");
        free.put("lines", "XH2.54\t杜邦线 200 根\t200\t9.84\n面包板\t某面包板\t1\t0");
        OirOrders.tidyOrder(free);
        assertEquals(1, free.get("lines").split("\n").length);
        // a block that adds up as it is keeps every row: six colours of one board are six articles
        Map<String, String> colours = new LinkedHashMap<>();
        colours.put("paidTotal", "41.28");
        colours.put("lines", "面包板-白\t面包板 白色\t5\t6.88\n面包板-蓝\t面包板 蓝色\t5\t6.88\n"
                + "面包板-绿\t面包板 绿色\t5\t6.88\n面包板-黄\t面包板 黄色\t5\t6.88\n"
                + "面包板-红\t面包板 红色\t5\t6.88\n面包板-黑\t面包板 黑色\t5\t6.88");
        OirOrders.tidyOrder(colours);
        assertEquals(6, colours.get("lines").split("\n").length);
        assertNull(OirOrders.checkOrderTotals(colours));
    }

    @Test
    public void booksTheWholePaidTotalOnTheOnlyArticleOfTheBlock() {
        // the page prints 实付款 6.60 beside ¥3.30 x2, and the grey line says SH1.0/3P/10CM双头（5条）:
        // one article of 10 pieces worth 6.60. The model took the price of one pack (3.30) as the
        // money of the row and the block came to half of what was paid (Step 132)
        Map<String, String> order = new LinkedHashMap<>();
        order.put("paidTotal", "6.60");
        order.put("freight", "0.00");
        order.put("rowCount", "1");
        order.put("lines", "SH1.0/3P/10CM\tSH1.0mm 2/3/4/5/6/7/8/9/10P 单/双头 电子端子线 长10CM连接线\t10\t3.30");
        OirOrders.tidyOrder(order);
        String[] rows = order.get("lines").split("\n");
        assertEquals(1, rows.length);
        // the quantity the page printed stays, only the money of the row is the money of the order
        assertEquals("10", rows[0].split("\t")[2]);
        assertEquals("6.60", rows[0].split("\t")[3]);
        assertNull(OirOrders.checkOrderTotals(order));
        // a block that prints two rows but was read with one is left alone: the money is not moved on
        // to the row that was read, and the page goes on warning that an article may be missing
        Map<String, String> shortRead = new LinkedHashMap<>();
        shortRead.put("paidTotal", "6.60");
        shortRead.put("freight", "0.00");
        shortRead.put("rowCount", "2");
        shortRead.put("lines", "SH1.0/3P/10CM\tSH1.0mm 电子端子线\t10\t3.30");
        OirOrders.tidyOrder(shortRead);
        assertEquals("3.30", shortRead.get("lines").split("\n")[0].split("\t")[3]);
        assertNotNull(OirOrders.checkOrderTotals(shortRead));
        // and without a row count nothing is assumed about the block either
        Map<String, String> noCount = new LinkedHashMap<>();
        noCount.put("paidTotal", "6.60");
        noCount.put("freight", "0.00");
        noCount.put("lines", "SH1.0/3P/10CM\tSH1.0mm 电子端子线\t10\t3.30");
        OirOrders.tidyOrder(noCount);
        assertEquals("3.30", noCount.get("lines").split("\n")[0].split("\t")[3]);
        // a block of several rows keeps its own money: rowCount 2 with two rows is complete
        Map<String, String> twoRows = new LinkedHashMap<>();
        twoRows.put("paidTotal", "4.64");
        twoRows.put("rowCount", "2");
        twoRows.put("lines", "AT24C64\tEEPROM\t10\t3.22\nAT24C02\tEEPROM\t10\t1.42");
        OirOrders.tidyOrder(twoRows);
        assertEquals(2, twoRows.get("lines").split("\n").length);
        assertNull(OirOrders.checkOrderTotals(twoRows));
        // the same shape the other way round: the row was read as more than what was paid
        Map<String, String> dearer = new LinkedHashMap<>();
        dearer.put("paidTotal", "2.00");
        dearer.put("freight", "0.00");
        dearer.put("rowCount", "1");
        dearer.put("lines", "0603 100NF\t0603 贴片电容 100NF 50只\t50\t3.00");
        OirOrders.tidyOrder(dearer);
        assertEquals("2.00", dearer.get("lines").split("\n")[0].split("\t")[3]);
        assertNull(OirOrders.checkOrderTotals(dearer));
        // and the discount the model reports to balance a row it read too high is not a discount of
        // the order: one printed article is worth what was paid for it (Step 132)
        Map<String, String> invented = new LinkedHashMap<>();
        invented.put("paidTotal", "2.00");
        invented.put("freight", "0.00");
        invented.put("discount", "1.00");
        invented.put("rowCount", "1");
        invented.put("lines", "0603 100NF\t0603 贴片电容 100NF 50只\t50\t3.00");
        OirOrders.tidyOrder(invented);
        assertEquals("2.00", invented.get("lines").split("\n")[0].split("\t")[3]);
        assertEquals("0", invented.get("discount"));
        assertNull(OirOrders.checkOrderTotals(invented));
        // the freight stays beside the article: 实付 9.99 with 含运费 2.99 is 7.00 of goods
        Map<String, String> withFreight = new LinkedHashMap<>();
        withFreight.put("paidTotal", "9.99");
        withFreight.put("freight", "2.99");
        withFreight.put("rowCount", "1");
        withFreight.put("lines", "BAS70DW-04\t肖特基二极管\t20\t9.99");
        OirOrders.tidyOrder(withFreight);
        assertEquals("7.00", withFreight.get("lines").split("\n")[0].split("\t")[3]);
        assertNull(OirOrders.checkOrderTotals(withFreight));
    }

    @Test
    public void multipliesThePriceOfOnePackByItsPacks() {
        // the page prints 0603叠层高频电感 … ¥2.00 x2 with 0603 1.5UH ±10%（50个）in the grey line, so the
        // row is 100 pieces for 4.00; the model read the 100 pieces but kept the 2.00 of one pack as
        // the money of the row, and the block came to 12.00 of the 14.00 that was paid (Step 145)
        Map<String, String> order = new LinkedHashMap<>();
        order.put("paidTotal", "14.00");
        order.put("freight", "0.00");
        order.put("rowCount", "2");
        order.put("lines", "0603 1.5nH 10%\t0603叠层高频电感1NH 1.5NH 2.2NH 4.7NH\t100\t2.00\n"
                + "10UF 10V 6.3X5.4 20个\tSMD贴片铝电解电容器 10UF22UF33UF47UF\t100\t10.00");
        OirOrders.tidyOrder(order);
        String[] rows = order.get("lines").split("\n");
        assertEquals(2, rows.length);
        // the quantity the page printed stays and the money of the row becomes two packs of 2.00
        assertEquals("100", rows[0].split("\t")[2]);
        assertEquals("4.00", rows[0].split("\t")[3]);
        assertEquals("100", rows[1].split("\t")[2]);
        assertEquals("10.00", rows[1].split("\t")[3]);
        assertNull(OirOrders.checkOrderTotals(order));
        assertNotNull(order.get("note"));

        // two rows of the same amount both explain the gap: nothing says which of them the page sold
        // in packs, so the rows stay as they were read and the money check keeps warning
        Map<String, String> ambiguous = new LinkedHashMap<>();
        ambiguous.put("paidTotal", "8.00");
        ambiguous.put("freight", "0.00");
        ambiguous.put("rowCount", "2");
        ambiguous.put("lines", "A\ta\t100\t2.00\nB\tb\t100\t2.00");
        OirOrders.tidyOrder(ambiguous);
        assertEquals("2.00", ambiguous.get("lines").split("\n")[0].split("\t")[3]);
        assertEquals("2.00", ambiguous.get("lines").split("\n")[1].split("\t")[3]);
        assertNotNull(OirOrders.checkOrderTotals(ambiguous));

        // a block whose rows already come to what was paid is left alone
        Map<String, String> fine = new LinkedHashMap<>();
        fine.put("paidTotal", "14.00");
        fine.put("freight", "0.00");
        fine.put("rowCount", "2");
        fine.put("lines", "A\ta\t100\t4.00\nB\tb\t100\t10.00");
        OirOrders.tidyOrder(fine);
        assertEquals("4.00", fine.get("lines").split("\n")[0].split("\t")[3]);
        assertNull(fine.get("note"));
    }

    @Test
    public void cutsBetweenTheBlocksOfAnOrderList() throws IOException {
        // every block of a Taobao list prints an orange 再买一单 button, so the blocks are found in the
        // picture and the cuts go between them: no block is ever cut in two (Step 139)
        int width = 300;
        int blockHeight = 400;
        int gap = 120;
        int blocks = 8;
        BufferedImage image = new BufferedImage(width, blocks * (blockHeight + gap),
                BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics2D pen = image.createGraphics();
        pen.setColor(java.awt.Color.WHITE);
        pen.fillRect(0, 0, width, image.getHeight());
        int[] colours = new int[blocks];
        for (int i = 0; i < blocks; i++) {
            int top = i * (blockHeight + gap);
            int rgb = ((20 + i * 10) << 16) | ((30 + i * 11) << 8) | (40 + i * 13);
            colours[i] = rgb & 0xFFFFFF;
            pen.setColor(new java.awt.Color(rgb));
            pen.fillRect(0, top, width, blockHeight);
            pen.setColor(new java.awt.Color(0xFF5000));
            // the button sits a header below the top of its block, as on a real page: that is what the
            // cut is laid on
            pen.fillRect(width - 100, top + OirScan.BUTTON_TO_HEADER, 60, 40);
        }
        pen.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        assertEquals(blocks, OirScan.blockAnchors(image).size());
        List<byte[]> slices = OirScan.slicesOf(out.toByteArray());
        assertTrue(slices.size() >= 2);
        // a slice is one target plus the overlap, and the cut may slide a quarter of a target
        int target = OirScan.sliceTargetOf(image);
        java.util.Set<Integer> whole = new java.util.HashSet<>();
        for (byte[] slice : slices) {
            BufferedImage picture = ImageIO.read(new java.io.ByteArrayInputStream(slice));
            assertTrue(picture.getHeight() <= target * 5 / 4 + OirScan.SLICE_OVERLAP);
            for (int i = 0; i < blocks; i++) {
                int rows = 0;
                for (int y = 0; y < picture.getHeight(); y++) {
                    if ((picture.getRGB(0, y) & 0xFFFFFF) == colours[i]) {
                        rows++;
                    }
                }
                if (rows >= blockHeight) {
                    whole.add(Integer.valueOf(i));
                }
            }
        }
        for (int i = 0; i < blocks; i++) {
            assertTrue("block " + i + " was cut in two by every slice", whole.contains(Integer.valueOf(i)));
        }
        // a page with no such button (another platform) is still cut, at the blank rows as before
        BufferedImage plain = new BufferedImage(width, 400 * 10, BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics2D pen2 = plain.createGraphics();
        pen2.setColor(java.awt.Color.WHITE);
        pen2.fillRect(0, 0, width, plain.getHeight());
        for (int i = 0; i < 10; i++) {
            pen2.setColor(new java.awt.Color(0x808080));
            pen2.fillRect(0, i * 400 + 40, width, 120);
        }
        pen2.dispose();
        assertEquals(0, OirScan.blockAnchors(plain).size());
        ByteArrayOutputStream out2 = new ByteArrayOutputStream();
        ImageIO.write(plain, "png", out2);
        assertTrue(OirScan.slicesOf(out2.toByteArray()).size() >= 2);
    }

    @Test
    public void tellsAButtonFromTheOrangeWordsOfAHeader() throws IOException {
        // a block prints "订单详情 >" and "交易成功" in the same orange, and a whole row of those words
        // holds as much orange as a button does - but in strokes a few pixels wide with gaps between
        // them. Counting all the orange of a row found 32 buttons on a real page of 23 orders: 23
        // buttons and 9 rows of words (audited 2026-10-27)
        int width = 400;
        int blockHeight = 400;
        int blocks = 3;
        BufferedImage withButtons = new BufferedImage(width, blocks * blockHeight,
                BufferedImage.TYPE_INT_RGB);
        BufferedImage withWords = new BufferedImage(width, blocks * blockHeight,
                BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics2D buttons = withButtons.createGraphics();
        java.awt.Graphics2D words = withWords.createGraphics();
        buttons.setColor(java.awt.Color.WHITE);
        buttons.fillRect(0, 0, width, withButtons.getHeight());
        words.setColor(java.awt.Color.WHITE);
        words.fillRect(0, 0, width, withWords.getHeight());
        for (int i = 0; i < blocks; i++) {
            int top = i * blockHeight;
            // the header row: many short strokes of the shop orange, spread over the whole width
            buttons.setColor(new java.awt.Color(0xFF5000));
            words.setColor(new java.awt.Color(0xFF5000));
            for (int x = 10; x < width - 10; x += 30) {
                buttons.fillRect(x, top + 20, 8, 14);
                words.fillRect(x, top + 20, 8, 14);
            }
            if (i == 0) {
                // one block prints no button at all, only words: it must not be counted as a block
                continue;
            }
        }
        for (int i = 1; i < blocks; i++) {
            buttons.setColor(new java.awt.Color(0xFF5000));
            buttons.fillRect(width - 100, i * blockHeight + 200, 60, 30);
        }
        buttons.dispose();
        words.dispose();
        assertEquals(2, OirScan.blockAnchors(withButtons).size());
        assertEquals(0, OirScan.blockAnchors(withWords).size());
    }

    @Test
    public void countsTheBlocksOfAPageByWhereTheirButtonsArePrinted() {
        // every block of the list prints its button at the same place, so the buttons that share a
        // place are the list's: the page header button and the ones of the recommendation strip below
        // the list stand somewhere else and are not counted. A block keeps its button whatever became
        // of the deal - a closed one offers 再买一单 or 加入购物车 too - so this count can be held
        // against the number of orders read (Step 153)
        int width = 300;
        int blockHeight = 300;
        int blocks = 5;
        BufferedImage image = new BufferedImage(width, blocks * blockHeight + 400,
                BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics2D pen = image.createGraphics();
        pen.setColor(java.awt.Color.WHITE);
        pen.fillRect(0, 0, width, image.getHeight());
        pen.setColor(new java.awt.Color(0xFF5000));
        // a second button style starts two pixels further left, as a shop prints 再买一单 beside 加入购物车
        for (int i = 0; i < blocks; i++) {
            pen.fillRect(i % 3 == 0 ? 238 : 240, i * blockHeight + 200, 50, 20);
        }
        pen.fillRect(100, 60, 50, 20);
        pen.fillRect(60, blocks * blockHeight + 100, 50, 20);
        pen.dispose();
        assertEquals(blocks, OirScan.blockCountOf(image));

        // two blocks say nothing: with so few there is nothing to compare against
        BufferedImage pair = new BufferedImage(width, 2 * blockHeight, BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics2D pen2 = pair.createGraphics();
        pen2.setColor(java.awt.Color.WHITE);
        pen2.fillRect(0, 0, width, pair.getHeight());
        pen2.setColor(new java.awt.Color(0xFF5000));
        pen2.fillRect(240, 200, 50, 20);
        pen2.fillRect(240, blockHeight + 200, 50, 20);
        pen2.dispose();
        assertEquals(0, OirScan.blockCountOf(pair));
        assertEquals(0, OirScan.blockCountOf(new BufferedImage(width, 200, BufferedImage.TYPE_INT_RGB)));

        // the count is only held against the read when the two are close: one order too many is a
        // block read as two, and one too few is a block the model never answered
        assertEquals("", OirScan.blockCountNote(23, 23));
        assertEquals("", OirScan.blockCountNote(0, 23));
        assertTrue(OirScan.blockCountNote(23, 24).contains("可能是同一块被拆开了"));
        assertTrue(OirScan.blockCountNote(23, 21).contains("可能漏了 2 张"));
        assertEquals("", OirScan.blockCountNote(20, 30));
    }

    @Test
    public void cutsTheFeedOffBelowTheLastOrderHoweverTallItIs() {
        // the tail of the page below the last order is the 猜你喜欢 strip, and it is cut off - but not
        // one block below the last button: that height is measured on the blocks the page repeats, and
        // the last order of the list need not look like them. A ten article order (20.00 yuan) lost
        // eight of its rows that way, and the model then filled them in from the paid total - with the
        // right ten rows once, with one or two the other times (Step 161)
        BufferedImage image = orderPage(3400, 70, true);
        int lastArticleEnd = 1400 + 9 * 70 + 20;
        int cut = OirScan.tailCutOf(image, orderPageButtons(), 400);
        assertTrue("the cut at " + cut + " is above the last article, which ends at " + lastArticleEnd,
                cut >= lastArticleEnd);
        assertTrue("the cut at " + cut + " reaches into the feed that starts at 2600", cut <= 2600);
        // one block below the button (1350 + 400) would have cut eight of the ten articles away
        assertTrue("the cut is still one block below the last button", cut > 1350 + 400);
        // the empty band the page prints between two articles is not the end of the list
        assertTrue(cut > 2030);
    }

    @Test
    public void keepsOneBlockOfRoomWhenThePageSeparatesNoFeed() {
        // cards with pictures of their own leave no wide empty band under the list (measured 18 to 40
        // rows, like the space between two articles), so the room below the last button is the tallest
        // block the page prints - never less than before, so this cannot cut away more than it did
        BufferedImage image = orderPage(2200, 70, false);
        assertEquals(1350 + 400, OirScan.tailCutOf(image, orderPageButtons(), 400));
        // a page whose blocks are taller than the measured block height keeps that room
        assertEquals(1350 + 400, OirScan.tailCutOf(image, orderPageButtons(), 0));
        // a picture with no button of its own keeps all of it
        assertEquals(2200, OirScan.tailCutOf(image, java.util.Collections.<Integer>emptyList(), 400));
    }

    @Test
    public void tellsHowManyBlocksASliceShowsWhole() {
        // a block runs from the header above its own button (BUTTON_TO_HEADER rows up) to the header of
        // the block below it, so a block is whole in a slice when both of those rows are inside. This is
        // what a slice's answer is held against: one block less than it prints is a block the model
        // dropped, and nothing else in the read notices it (Step 154)
        List<Integer> buttons = java.util.Arrays.asList(Integer.valueOf(118), Integer.valueOf(673),
                Integer.valueOf(1037), Integer.valueOf(1445), Integer.valueOf(2148));
        // in the first 1241 rows only the block ending at 1037 is whole: the one at 118 has no header
        // inside the slice and the one at 1445 is cut by the bottom
        assertEquals(1, OirScan.blocksInside(buttons, 0, 1241));
        // the second slice starts below the 673 button, so that block is cut at its top and the two
        // below it are whole
        assertEquals(2, OirScan.blocksInside(buttons, 741, 2352));
        // a slice that shows only one block says nothing: one is what the model is expected to answer
        assertEquals(0, OirScan.blocksInside(buttons, 1000, 1500));
        assertEquals(0, OirScan.blocksInside(null, 0, 100));
    }

    @Test
    public void countsTheBlocksOfASliceByRowNotByThePlaceTheirButtonsWereGroupedUnder() {
        // the buttons of the list are found place by place (a page prints 再买一单 two pixels beside
        // 加入购物车, so neighbouring places are one place), and that order is not the order of the rows:
        // everything that pairs the buttons up - how many blocks a slice shows whole, which block is
        // the last of the list - needs them top to bottom (Step 158)
        int width = 300;
        int blockHeight = 300;
        BufferedImage image = new BufferedImage(width, 5 * blockHeight + 400,
                BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics2D pen = image.createGraphics();
        pen.setColor(java.awt.Color.WHITE);
        pen.fillRect(0, 0, width, image.getHeight());
        pen.setColor(new java.awt.Color(0xFF5000));
        for (int i = 0; i < 5; i++) {
            pen.fillRect(i % 2 == 0 ? 238 : 240, i * blockHeight + 200, 50, 20);
        }
        pen.dispose();
        List<Integer> buttons = OirScan.listButtonsOf(image);
        assertEquals(5, buttons.size());
        for (int i = 1; i < buttons.size(); i++) {
            assertTrue("the buttons of the list are read top to bottom, not place by place",
                    buttons.get(i).intValue() > buttons.get(i - 1).intValue());
        }
        // three blocks end well inside the first 1241 rows; the fourth one is cut by the bottom
        assertEquals(3, OirScan.blocksInside(buttons, 0, 1241));
        // the slice 741..2352 starts above the third block and reaches past the fourth, so those two
        // are whole in it
        assertEquals(2, OirScan.blocksInside(buttons, 741, 2352));
    }

    @Test
    public void multipliesAPriceByItsQuantityWhenTheRowsAreUnitPrices() {
        // the model answered the price of one piece for each of six rows of 210 pieces: 12.21 in all,
        // where 2562.84 was paid. Multiplying by the quantity gives 2564.10 - the page's own rounding
        // to 2.13 of a price of 2.124 - and that is the row money the receipt needs (Step 159)
        Map<String, String> order = new LinkedHashMap<>();
        order.put("paidTotal", "2562.84");
        order.put("freight", "0.00");
        order.put("discount", "0");
        order.put("rowCount", "6");
        order.put("lines", "黑色\tPB86 按键\t210\t1.94\n黄色\tPB86 按键\t210\t2.13\n"
                + "绿色\tPB86 按键\t210\t2.13\n蓝色\tPB86 按键\t210\t1.94\n"
                + "红色\tPB86 按键\t210\t2.13\n灰色\tPB86 按键\t210\t1.94");
        OirOrders.tidyOrder(order);
        double sum = 0.0;
        for (String row : order.get("lines").split("\n")) {
            sum += Double.parseDouble(row.split("\t")[3]);
        }
        assertEquals(2564.10, sum, 0.005);
        // and the block is not called out: what is left is the rounding of the printed unit prices
        assertNull(OirOrders.checkOrderTotals(order));
    }

    @Test
    public void doesNotLetAPackRowSwallowAMissingRow() {
        // a block of nine resistor rows of 100 pieces each: nine times 1.20 is 10.80, the page says
        // 15.90. The room one row may be off by is a dime, not half a yuan for its hundred pieces, so
        // the 5.10 the model never read is not taken for rounded unit prices (Step 159)
        Map<String, String> order = new LinkedHashMap<>();
        order.put("paidTotal", "15.90");
        order.put("freight", "0.00");
        order.put("rowCount", "9");
        StringBuilder lines = new StringBuilder();
        for (int i = 0; i < 9; i++) {
            if (lines.length() > 0) {
                lines.append('\n');
            }
            lines.append("R").append(i).append("\t0603 贴片电阻\t100\t1.20");
        }
        order.put("lines", lines.toString());
        assertTrue(OirOrders.totalsDisagree(order));
        assertNotNull(OirOrders.checkOrderTotals(order));
        // the same block read to the fen is left alone
        Map<String, String> exact = new LinkedHashMap<>();
        exact.put("paidTotal", "15.90");
        exact.put("freight", "0.00");
        exact.put("rowCount", "2");
        exact.put("lines", "R0\t0603\t100\t7.95\nR1\t0603\t100\t7.95");
        assertFalse(OirOrders.totalsDisagree(exact));
    }

    @Test
    public void keepsAWholeOrderBlockInOneSliceHoweverTallItIs() {
        // the blocks of one page are 300 to 2700 rows tall, so a cut at a fixed height lands inside a
        // block: a thirteen row order split in two is answered with a few rows of each half and its
        // money adds up to nothing. One block per slice, cut on its header (Step 159)
        List<Integer> buttons = java.util.Arrays.asList(Integer.valueOf(200), Integer.valueOf(900),
                Integer.valueOf(4000), Integer.valueOf(4700));
        List<Integer> cuts = OirScan.cutsBetweenBlocks(buttons, 1400, 5200);
        assertEquals(3, cuts.size());
        // every cut is the header of the next block, the tall one included: no block is cut in two
        for (int i = 0; i < cuts.size(); i++) {
            assertEquals(buttons.get(i + 1).intValue() - OirScan.BUTTON_TO_HEADER,
                    cuts.get(i).intValue());
        }
        // so each slice shows one block whole, and the second of them is the tall one
        assertEquals(1, OirScan.blocksInside(buttons, 0, cuts.get(0).intValue()));
        assertEquals(1, OirScan.blocksInside(buttons, cuts.get(0).intValue(), cuts.get(1).intValue()));
        assertEquals(1, OirScan.blocksInside(buttons, cuts.get(1).intValue(), 5200));
        // nothing to cut when the page shows one block: it fits in a single call
        assertTrue(OirScan.cutsBetweenBlocks(java.util.Arrays.asList(Integer.valueOf(200)), 1400, 5200)
                .isEmpty());
    }

    @Test
    public void holdsASliceToItsMoneyOnlyWhenEveryBlockInItIsWhole() {
        // a slice of a long page may hold the top half of one block and the bottom half of the next, so
        // its money cannot add up and says nothing. A slice that prints one block whole and answers
        // one order holds nothing else, so the money is worth checking - a nine row block answered
        // with five rows is short by exactly the rows it lost (Step 158)
        assertTrue(OirScan.moneyCanBeChecked(false, 1, 0));
        assertTrue(OirScan.moneyCanBeChecked(false, 3, 2));
        assertTrue(OirScan.moneyCanBeChecked(true, 1, 1));
        assertTrue(OirScan.moneyCanBeChecked(true, 3, 3));
        // a slice that also carries a block the cut halved says nothing: its rows are partly in the
        // slice above or below, and asking it again only cost time (a page of 14 orders came back
        // noisier with it than without)
        assertFalse(OirScan.moneyCanBeChecked(true, 2, 1));
        assertFalse(OirScan.moneyCanBeChecked(true, 4, 3));
        assertFalse(OirScan.moneyCanBeChecked(true, 1, 0));
    }

    @Test
    public void foldsTwoReadsOfOneNumberWhenThePageHasFewerBlocks() {
        // the join keeps two blocks read with one number apart, because a page never prints one number
        // on two blocks and dropping one would lose an order. The blocks of the picture can be
        // counted, so when there are fewer blocks than orders that split was wrong and the two rows
        // are one block read twice (Step 155)
        Map<String, String> one = new LinkedHashMap<>();
        one.put("orderNo", "3102077787932500570");
        one.put("shop", "盛创");
        one.put("paidTotal", "199.00");
        one.put("freight", "0.00");
        one.put("discount", "0");
        one.put("rowCount", "1");
        one.put("lines", "CH340X\tCH340X 芯片\t100\t199.00");
        Map<String, String> again = new LinkedHashMap<>(one);
        again.put("paidTotal", "2.00");
        List<Map<String, String>> orders = new ArrayList<>(java.util.Arrays.asList(one, again));
        // the page does not say how many blocks it has, or says as many as were read: leave them alone
        assertEquals(2, OirOrders.foldExtraReads(orders, 0).size());
        assertEquals(2, OirOrders.foldExtraReads(orders, 2).size());
        // one block, two reads: folded, and the money kept is the one the articles come to
        List<Map<String, String>> folded = OirOrders.foldExtraReads(orders, 1);
        assertEquals(1, folded.size());
        assertEquals("199.00", folded.get(0).get("paidTotal"));
        assertEquals(1, OirOrders.parseOrderArticleLines(folded.get(0).get("lines")).size());
        assertTrue(folded.get(0).get("note").contains("已合成一张"));
        // two different numbers are two orders, whatever the count says
        Map<String, String> other = new LinkedHashMap<>(again);
        other.put("orderNo", "3102053594917500570");
        assertEquals(2, OirOrders.foldExtraReads(
                new ArrayList<>(java.util.Arrays.asList(one, other)), 1).size());

        // the join keeps a second read of one number under "<number> #2", so the fold has to look
        // behind that mark - otherwise it never finds the pair it was written for
        Map<String, String> readA = new LinkedHashMap<>();
        readA.put("orderNo", "3102077787932500570");
        readA.put("shop", "盛创");
        readA.put("paidTotal", "199.00");
        readA.put("freight", "0.00");
        readA.put("lines", "CH340X\tCH340X 芯片\t100\t199.00");
        Map<String, String> readB = new LinkedHashMap<>(readA);
        // the same article read twice with a different money: not the price of one piece (199.00 is
        // not 199.00 times a quantity), so the join keeps the two apart
        readB.put("paidTotal", "150.00");
        List<List<Map<String, String>>> answers = new ArrayList<>();
        answers.add(new ArrayList<>(java.util.Arrays.asList(readA, readB)));
        List<Map<String, String>> joined = OirOrders.mergeOrders(answers);
        assertEquals(2, joined.size());
        // the join keeps the number on both rows and marks only its own key, so the fold finds them
        assertEquals("3102077787932500570", joined.get(1).get("orderNo"));
        List<Map<String, String>> foldedJoin = OirOrders.foldExtraReads(joined, 1);
        assertEquals(1, foldedJoin.size());
        assertEquals("3102077787932500570", foldedJoin.get(0).get("orderNo"));
    }

    @Test
    public void settlesTheOrderNumberWithTheLengthThePagePrintsAt() {
        // the number is the key the import joins on, and a nineteen digit run is the one field a model
        // fills in from the shape of its neighbours. Fifty runs of a 23 order page showed three where
        // one of the two readings had a digit too few or too many - and the second read (from the strip,
        // which the service scales less) was as often the wrong one as the first. So neither wins by
        // being bigger: the reading with the length the page prints at wins, and two of the same length
        // are left as they were with both written into the note (Step 156/157)
        List<Map<String, String>> headers = OirOrders.headerListFrom(
                "{\"blocks\":[{\"orderNo\":\"3102077787932500570\",\"orderDate\":\"2025-12-04\","
                        + "\"shop\":\"盛创\"}]}");
        assertEquals(1, headers.size());

        // the whole page read one digit too few, the strip read it right: the strip's number is used
        Map<String, String> short1 = new LinkedHashMap<>();
        short1.put("orderNo", "310207778793250570");
        short1.put("shop", "");
        short1.put("orderDate", "");
        Map<String, String> fine1 = new LinkedHashMap<>();
        fine1.put("orderNo", "3102053594917500570");
        Map<String, String> fine2 = new LinkedHashMap<>();
        fine2.put("orderNo", "3102079443458500570");
        List<Map<String, String>> orders = new ArrayList<>();
        orders.add(short1);
        orders.add(fine1);
        orders.add(fine2);
        List<Map<String, String>> threeBlocks = OirOrders.headerListFrom(
                "{\"blocks\":[{\"orderNo\":\"3102077787932500570\",\"orderDate\":\"2025-12-04\","
                        + "\"shop\":\"盛创\"},{\"orderNo\":\"3102053594917500570\"},"
                        + "{\"orderNo\":\"3102079443458500570\"}]}");
        assertEquals(3, threeBlocks.size());
        assertEquals(1, OirOrders.noteTheSecondRead(orders, threeBlocks));
        assertEquals(1, OirOrders.settleNumbers(orders));
        assertEquals("3102077787932500570", short1.get("orderNo"));
        assertEquals("2025-12-04", short1.get("orderDate"));
        assertEquals("盛创", short1.get("shop"));
        assertTrue(short1.get("note").contains("单号两遍读数不一致"));

        // the other way round: the strip read a digit too many, the whole page was right, so the number
        // stays - it is only written into the note
        Map<String, String> good = new LinkedHashMap<>();
        good.put("orderNo", "3102077787932500570");
        Map<String, String> other = new LinkedHashMap<>();
        other.put("orderNo", "3102053594917500570");
        Map<String, String> third = new LinkedHashMap<>();
        third.put("orderNo", "3102079443458500570");
        List<Map<String, String>> run = new ArrayList<>();
        run.add(good);
        run.add(other);
        run.add(third);
        List<Map<String, String>> longStrip = OirOrders.headerListFrom(
                "{\"blocks\":[{\"orderNo\":\"31020777879325000570\"},{\"orderNo\":\"3102053594917500570\"},"
                        + "{\"orderNo\":\"3102079443458500570\"}]}");
        assertEquals(1, OirOrders.noteTheSecondRead(run, longStrip));
        assertEquals(0, OirOrders.settleNumbers(run));
        assertEquals("3102077787932500570", good.get("orderNo"));
        assertTrue(good.get("note").contains("已保留 3102077787932500570"));

        // two readings of the same length cannot be told apart by the picture: the first stays, and the
        // row says both were seen
        Map<String, String> same = new LinkedHashMap<>();
        same.put("orderNo", "3102077787932500570");
        List<Map<String, String>> one = new ArrayList<>(java.util.Arrays.asList(same));
        List<Map<String, String>> sameStrip = OirOrders.headerListFrom(
                "{\"blocks\":[{\"orderNo\":\"3071881380774500570\"}]}");
        assertEquals(1, OirOrders.noteTheSecondRead(one, sameStrip));
        assertEquals(0, OirOrders.settleNumbers(one));
        assertEquals("3102077787932500570", same.get("orderNo"));
        assertNull(same.get("headerOrderNo"));

        // a block cut at the top of the picture has no number of its own: the two lists are not the same
        // blocks then, so nothing may be lined up
        Map<String, String> anonymous = new LinkedHashMap<>();
        anonymous.put("orderNo", "");
        List<Map<String, String>> withAnonymous = new ArrayList<>(java.util.Arrays.asList(anonymous));
        assertEquals(0, OirOrders.noteTheSecondRead(withAnonymous, headers));
        assertEquals("", anonymous.get("orderNo"));
        assertEquals(0, OirOrders.noteTheSecondRead(run, headers));
    }

    @Test
    public void cutsTheEmptyMarginAway() {
        // a shop page keeps an empty margin each side; cutting it away is what makes the digits of an
        // order number bigger once the vision service scales the picture down
        int width = 800;
        int height = 300;
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics2D pen = image.createGraphics();
        pen.setColor(java.awt.Color.WHITE);
        pen.fillRect(0, 0, width, height);
        pen.setColor(java.awt.Color.BLACK);
        pen.fillRect(200, 40, 400, 200);
        pen.dispose();
        int[] columns = OirScan.contentColumns(image, 0, height);
        assertTrue("left " + columns[0], columns[0] >= 180 && columns[0] <= 200);
        assertTrue("right " + columns[1], columns[1] >= 598 && columns[1] <= 612);

        // a picture printed edge to edge keeps all its columns
        BufferedImage solid = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics2D pen2 = solid.createGraphics();
        pen2.setColor(java.awt.Color.BLACK);
        pen2.fillRect(0, 0, width, height);
        pen2.dispose();
        assertEquals(0, OirScan.contentColumns(solid, 0, height)[0]);
        assertEquals(width - 1, OirScan.contentColumns(solid, 0, height)[1]);
    }

    @Test
    public void takesALongPageScreenshotAsItIs() {
        // a whole shop page captured in one picture runs to ten megabytes and more; the picture is
        // cut into slices anyway, so the upload is not refused for being long (Step 128)
        assertNull(OirScan.imageSizeProblem(3 * 1024 * 1024));
        assertNull(OirScan.imageSizeProblem(OirScan.MAX_IMAGE_BYTES));
        assertEquals(32 * 1024 * 1024, OirScan.MAX_IMAGE_BYTES);
        String tooBig = OirScan.imageSizeProblem(OirScan.MAX_IMAGE_BYTES + 1);
        // the message says the real limit and what to do with a page above it
        assertTrue(tooBig.contains("32 MB"));
        assertTrue(tooBig.contains("分段"));
        assertTrue(tooBig.contains("截成两张"));
    }

    @Test
    public void handsALargeSliceToTheModelAsJpeg() throws IOException {
        byte[] jpeg = OirScan.jpegOf(png(120, 160));
        // a JPEG starts with FF D8 and ends with FF D9
        assertEquals((byte) 0xFF, jpeg[0]);
        assertEquals((byte) 0xD8, jpeg[1]);
        assertEquals((byte) 0xFF, jpeg[jpeg.length - 2]);
        assertEquals((byte) 0xD9, jpeg[jpeg.length - 1]);
        // something that is not a picture is not lost: there is nothing to re-encode, so the caller
        // sends what it got
        assertNull(OirScan.jpegOf(new byte[] {1, 2, 3}));
    }

    @Test
    public void cutsThePageBetweenOrderBlocksNotThroughOne() throws IOException {
        // a band of text sits where the page would otherwise be cut, so the cut has to slide away
        int band = OirScan.SLICE_HEIGHT - 60;
        int height = OirScan.ONE_CALL_HEIGHT + OirScan.SLICE_HEIGHT;
        BufferedImage image = new BufferedImage(400, height, BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics2D pen = image.createGraphics();
        pen.setColor(java.awt.Color.WHITE);
        pen.fillRect(0, 0, 400, height);
        // text reads as colour changes along a row: alternate every four pixels, which is also the
        // step the slicer samples at
        for (int y = band; y <= band + 80; y++) {
            for (int x = 0; x < 400; x += 4) {
                pen.setColor(((x / 4) % 2 == 0) ? java.awt.Color.BLACK : java.awt.Color.WHITE);
                pen.fillRect(x, y, 4, 1);
            }
        }
        pen.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        List<byte[]> slices = OirScan.slicesOf(out.toByteArray());
        assertTrue(slices.size() >= 2);
        int cut = ImageIO.read(new java.io.ByteArrayInputStream(slices.get(0))).getHeight();
        assertTrue("the cut landed in the text rows: " + cut, cut < band || cut > band + 80);
        // the cut may slide either way to find that blank row, but not far
        assertTrue("slice too tall: " + cut, cut <= OirScan.SLICE_HEIGHT + OirScan.SLICE_SEARCH);
    }

    @Test
    public void tidiesWhatTheOverlapOfTwoSlicesAddsOn() {
        // the same article seen in both slices, one of them with the note the page prints behind the
        // title: it is one row, not two (two would book the part in twice)
        Map<String, String> twice = new java.util.LinkedHashMap<>();
        twice.put("paidTotal", "2.07");
        twice.put("freight", "0.00");
        twice.put("lines", "SOP转接板 10P\t名\t10\t2.07\nSOP转接板 10P [交易快照]\t名\t10\t2.07");
        OirOrders.tidyOrder(twice);
        assertEquals(1, OirOrders.parseOrderArticleLines(twice.get("lines")).size());
        assertNull(OirOrders.checkOrderTotals(twice));
        // a row that only repeats the paid total is a summary, not an article
        Map<String, String> summary = new java.util.LinkedHashMap<>();
        summary.put("paidTotal", "3.40");
        summary.put("freight", "2.00");
        summary.put("lines", "原装富满 DW01A\t名\t1\t3.40\nDW01A\t名\t10\t0.60\n"
                + "8205LA\t名\t5\t0.55\n8205HA\t名\t5\t0.30");
        OirOrders.tidyOrder(summary);
        assertEquals(3, OirOrders.parseOrderArticleLines(summary.get("lines")).size());
        // and a discount that would break an order that already adds up is dropped
        Map<String, String> invented = new java.util.LinkedHashMap<>();
        invented.put("paidTotal", "3.66");
        invented.put("freight", "0.00");
        invented.put("discount", "4.80");
        invented.put("lines", "3528钽电容\t名\t10\t3.66");
        assertNull(OirOrders.checkOrderTotals(invented));
        assertEquals("0", invented.get("discount"));
    }

    @Test
    public void readsTheFreightOutOfThePaidTotal() {
        // a page prints 实付款 9.99 next to 含运费 2.99: the paid total already holds the freight, so
        // a model that reads the article as 9.99 counts the freight twice (Step 129)
        Map<String, String> order = new java.util.LinkedHashMap<>();
        order.put("orderNo", "3316901521014009372");
        order.put("paidTotal", "9.99");
        order.put("freight", "2.99");
        order.put("discount", "0");
        order.put("lines", "0603\t0603贴片电容 100NF 104K\t100\t9.99");
        OirOrders.tidyOrder(order);
        // the article is what was paid for it, the freight stands beside it
        assertEquals("7.00", OirOrders.parseOrderArticleLines(order.get("lines")).get(0)[3]);
        assertNull(OirOrders.checkOrderTotals(order));
        // several rows keep their share of the money and still come to the same total
        Map<String, String> split = new java.util.LinkedHashMap<>();
        split.put("orderNo", "3317076120013109755");
        split.put("paidTotal", "9.40");
        split.put("freight", "5.00");
        split.put("lines", "A\t名\t10\t5.00\nB\t名\t10\t4.40");
        OirOrders.tidyOrder(split);
        java.util.List<String[]> afterSplit = OirOrders.parseOrderArticleLines(split.get("lines"));
        assertEquals(2, afterSplit.size());
        double sum = Double.parseDouble(afterSplit.get(0)[3]) + Double.parseDouble(afterSplit.get(1)[3]);
        assertEquals("4.40", String.format(java.util.Locale.ROOT, "%.2f", Double.valueOf(sum)));
        assertNull(OirOrders.checkOrderTotals(split));
        // a page that prints the goods and the freight apart is left alone: 7.00 + 2.99 = 9.99
        Map<String, String> apart = new java.util.LinkedHashMap<>();
        apart.put("paidTotal", "9.99");
        apart.put("freight", "2.99");
        apart.put("lines", "0603\t0603贴片电容\t100\t7.00");
        OirOrders.tidyOrder(apart);
        assertEquals("7.00", OirOrders.parseOrderArticleLines(apart.get("lines")).get(0)[3]);
        assertNull(OirOrders.checkOrderTotals(apart));
    }

    @Test
    public void keepsTheSixRowsOfOneBoardInSixColours() {
        // six rows carry the same part number, the same quantity and the same money, and differ only
        // in the colour printed on the grey line: six articles, not one article read twice
        Map<String, String> order = boardOrder(boardRows(6));
        OirOrders.tidyOrder(order);
        assertEquals(6, OirOrders.parseOrderArticleLines(order.get("lines")).size());
        // and the money agrees, so the page shows no warning
        assertNull(OirOrders.checkOrderTotals(order));
    }

    @Test
    public void cutsTheCopiesTwoSlicesMakeOfTheSameRowsBackToWhatWasPaid() {        // the same page cut into two overlapping slices: the rows of the overlap are read twice (here
        // five rows and three rows, two of them shared), so seven rows come back for six articles
        Map<String, String> left = new java.util.LinkedHashMap<>(boardOrder(boardRows(5)));
        Map<String, String> right = new java.util.LinkedHashMap<>(boardOrder(boardRows(3)));
        right.put("paidTotal", "");
        List<Map<String, String>> merged = OirOrders.mergeOrders(
                Arrays.asList(Arrays.asList(left), Arrays.asList(right)));
        assertEquals(1, merged.size());
        Map<String, String> order = merged.get(0);
        // joining the two reads keeps every row; only the money can tell which of them are copies
        assertEquals(8, OirOrders.parseOrderArticleLines(order.get("lines")).size());
        OirOrders.tidyOrder(order);
        assertEquals(6, OirOrders.parseOrderArticleLines(order.get("lines")).size());
        assertNull(OirOrders.checkOrderTotals(order));
    }

    @Test
    public void leavesTheRowsAloneWhenNoPaidTotalSaysWhichCopyIsReal() {        // without 实付 nothing can tell an overlap from two identical articles, so the rows are kept
        // as they were read; dropping them would lose material without a word
        Map<String, String> order = new java.util.LinkedHashMap<>();
        order.put("lines", boardRows(2));
        OirOrders.tidyOrder(order);
        assertEquals(2, OirOrders.parseOrderArticleLines(order.get("lines")).size());
        // rows that differ are never touched
        Map<String, String> different = boardOrder(
                "A\t一件\t1\t1.00\nB\t另一件\t2\t4.00\nC\t第三件\t3\t2.00");
        different.put("paidTotal", "7.00");
        OirOrders.tidyOrder(different);
        assertEquals(3, OirOrders.parseOrderArticleLines(different.get("lines")).size());
        assertNull(OirOrders.checkOrderTotals(different));
    }

    @Test
    public void dropsTheCopiesOneSliceReadWithThePackSizeAndTheNextWithout() {
        // three slices of one order: the first read all six colours with the pack size in the code,
        // the next two read three of them again without it, and one invented an article that is not
        // on the page - the money is the only thing that can sort this out
        String six = "白（5个）\t板\t5\t6.88\n蓝（5个）\t板\t5\t6.88\n绿（5个）\t板\t5\t6.88"
                + "\n黄（5个）\t板\t5\t6.88\n黑（5个）\t板\t5\t6.88\n红（5个）\t板\t5\t6.88";
        String again = "黄\t板\t5\t6.88\n黑\t板\t5\t6.88\n红\t板\t5\t6.88";
        String invented = "一次性雨衣\t雨衣\t1\t14.90";
        Map<String, String> order = boardOrder(six + "\n" + again + "\n" + invented);
        OirOrders.tidyOrder(order);
        assertEquals(6, OirOrders.parseOrderArticleLines(order.get("lines")).size());
        assertNull(OirOrders.checkOrderTotals(order));
    }

    @Test
    public void catchesAPageWhoseSixRowsCameBackAsOneItem() {
        List<Map<String, String>> oneItem = OirOrders.orderListFrom("{\"orderCount\":\"1\",\"orders\":["
                + "{\"orderNo\":\"2371626699152500570\",\"paidTotal\":\"41.28\",\"rowCount\":\"6\","
                + "\"items\":[{\"code\":\"面包板 SYB-170\",\"name\":\"面包板 彩色小面包板\","
                + "\"qty\":\"5\",\"amount\":\"41.28\"}]}]}");
        // the money adds up while five articles are missing, so only the printed row count finds it
        assertNull(OirOrders.checkOrderTotals(oneItem.get(0)));
        assertTrue(OirOrders.rowsMissing(oneItem.get(0)));
        // six rows for six counted rows is what the page prints
        List<Map<String, String>> six = OirOrders.orderListFrom("{\"orders\":[{"
                + "\"orderNo\":\"1\",\"paidTotal\":\"41.28\",\"rowCount\":\"6\",\"items\":["
                + "{\"code\":\"白\",\"name\":\"板\",\"qty\":\"5\",\"amount\":\"6.88\"},"
                + "{\"code\":\"蓝\",\"name\":\"板\",\"qty\":\"5\",\"amount\":\"6.88\"},"
                + "{\"code\":\"绿\",\"name\":\"板\",\"qty\":\"5\",\"amount\":\"6.88\"},"
                + "{\"code\":\"黄\",\"name\":\"板\",\"qty\":\"5\",\"amount\":\"6.88\"},"
                + "{\"code\":\"黑\",\"name\":\"板\",\"qty\":\"5\",\"amount\":\"6.88\"},"
                + "{\"code\":\"红\",\"name\":\"板\",\"qty\":\"5\",\"amount\":\"6.88\"}]}]}");
        assertFalse(OirOrders.rowsMissing(six.get(0)));
        assertEquals(6, OirOrders.parseOrderArticleLines(six.get(0).get("lines")).size());
        // an answer that does not count its rows is taken as it is, and nothing is null
        assertFalse(OirOrders.rowsMissing(OirOrders.orderListFrom(
                "{\"orders\":[{\"orderNo\":\"1\",\"items\":[{\"code\":\"A\",\"name\":\"a\","
                        + "\"qty\":\"1\",\"amount\":\"1.00\"}]}]}").get(0)));
        assertFalse(OirOrders.rowsMissing(null));
    }

    @Test
    public void spotsAnOrderCopiedFromTheOneAbove() {
        // the page lists five blocks; the model carried the articles and the money of the fourth into
        // the fifth, which then adds up as well (0.19 + 1.00 = 1.19), so nothing else can see it
        List<Map<String, String>> answer = OirOrders.orderListFrom("{\"orderCount\":\"2\",\"orders\":["
                + "{\"orderNo\":\"2366688758496500570\",\"paidTotal\":\"1.19\",\"freight\":\"1.00\","
                + "\"items\":[{\"code\":\"TP4056\",\"name\":\"充电芯片\",\"qty\":\"2\",\"amount\":\"0.19\"}]},"
                + "{\"orderNo\":\"2366655602274500570\",\"paidTotal\":\"1.19\",\"freight\":\"1.00\","
                + "\"items\":[{\"code\":\"TP4056\",\"name\":\"充电芯片\",\"qty\":\"2\",\"amount\":\"0.19\"}]}]}");
        assertEquals(2, answer.size());
        assertTrue(OirOrders.repeatsAnotherOrder(answer));
        // two different orders are not a copy
        List<Map<String, String>> different = OirOrders.orderListFrom("{\"orders\":["
                + "{\"orderNo\":\"1\",\"paidTotal\":\"1.19\",\"freight\":\"1.00\","
                + "\"items\":[{\"code\":\"TP4056\",\"name\":\"充电芯片\",\"qty\":\"2\",\"amount\":\"0.19\"}]},"
                + "{\"orderNo\":\"2\",\"paidTotal\":\"17.90\",\"freight\":\"0.00\","
                + "\"items\":[{\"code\":\"HFD1.9\",\"name\":\"串口屏\",\"qty\":\"1\",\"amount\":\"17.90\"}]}]}");
        assertFalse(OirOrders.repeatsAnotherOrder(different));
        // the same article bought twice for the same money is suspicious but the same key needs both
        // the articles and the money to match
        List<Map<String, String>> sameArticle = OirOrders.orderListFrom("{\"orders\":["
                + "{\"orderNo\":\"1\",\"paidTotal\":\"8.00\",\"freight\":\"0.00\","
                + "\"items\":[{\"code\":\"A\",\"name\":\"a\",\"qty\":\"1\",\"amount\":\"8.00\"}]},"
                + "{\"orderNo\":\"2\",\"paidTotal\":\"9.00\",\"freight\":\"0.00\","
                + "\"items\":[{\"code\":\"A\",\"name\":\"a\",\"qty\":\"1\",\"amount\":\"8.00\"}]}]}");
        assertFalse(OirOrders.repeatsAnotherOrder(sameArticle));
        assertFalse(OirOrders.repeatsAnotherOrder(Collections.emptyList()));
        assertNull(OirOrders.orderContentKey(null));
    }

    @Test
    public void dropsTheSnapshotNoteFromThePartNumber() {
        // a model with no code to copy answers with the whole title; the note the shop prints behind
        // it is not part of the part number
        String lines = OirOrders.fitOrderLines(
                "SYB-500 组合面包板 [交易快照]\tSYB-500 组合面包板 [交易快照]\t1\t16.00");
        assertTrue(lines, lines.startsWith("SYB-500 组合面包板\t"));
    }

    @Test
    public void readsTheRowMoneyWhenTheStruckPriceCameAsADiscount() {
        // a page prints 0.10 beside the struck through 0.09 and x2: the model answered the unit price
        // as the row money and the struck price as a discount of the whole order
        Map<String, String> order = new java.util.LinkedHashMap<>();
        order.put("orderNo", "2366688758496500570");
        order.put("paidTotal", "1.19");
        order.put("freight", "1.00");
        order.put("discount", "0.09");
        order.put("lines", "TP4056\t充电芯片\t2\t0.10");
        OirOrders.tidyOrder(order);
        assertEquals("0", order.get("discount"));
        assertEquals(1, OirOrders.parseOrderArticleLines(order.get("lines")).size());
        assertNull(OirOrders.checkOrderTotals(order));
        // a real red packet survives: 9.00 + 2.00 - 0.27 = 10.73
        Map<String, String> packet = new java.util.LinkedHashMap<>();
        packet.put("paidTotal", "10.73");
        packet.put("freight", "2.00");
        packet.put("discount", "0.27");
        packet.put("lines", "一条\t商品\t1\t9.00");
        OirOrders.tidyOrder(packet);
        assertEquals("0.27", packet.get("discount"));
        assertNull(OirOrders.checkOrderTotals(packet));
    }

    @Test
    public void catchesAPictureWhosePacksWereCountedAsPieces() {
        // Taobao prints the pack size in the grey spec line: x1 of 一件100只 is 100 pieces, and an
        // answer that says 1 for every article read the number of packs, not the number of pieces
        String packLine = "30K\t0603贴片电阻\t1\t1.20\n1M\t0603贴片电阻\t1\t1.20";
        String reply = "{\"orders\":[{\"orderNo\":\"1\",\"lines\":\"30K(一件100只)\"}]}";
        assertTrue(OirOrders.ignoredThePackSize(reply, Arrays.asList(packLine)));
        // the same answer after the pack size was multiplied reads like a person would write it
        String counted = "30K\t0603贴片电阻\t100\t1.20\n1M\t0603贴片电阻\t100\t1.20";
        assertFalse(OirOrders.ignoredThePackSize(reply, Arrays.asList(counted)));
        // one line with more than one piece is enough to accept the answer
        String mixed = "30K\t0603贴片电阻\t1\t1.20\n排针\t2.54mm\t5\t9.84";
        assertFalse(OirOrders.ignoredThePackSize(reply, Arrays.asList(mixed)));
        // no pack size in the picture: buying one piece of each is a normal order
        assertFalse(OirOrders.ignoredThePackSize(
                "{\"orders\":[{\"orderNo\":\"1\",\"lines\":\"A,part,1,1.00\"}]}", Arrays.asList(packLine)));
        assertFalse(OirOrders.ignoredThePackSize(null, Arrays.asList(packLine)));
        assertFalse(OirOrders.ignoredThePackSize(reply, Arrays.asList("")));
        // the other spellings of a pack size are recognised too
        assertTrue(OirOrders.ignoredThePackSize(
                "{\"lines\":\"1N5819W (50只)\"}", Arrays.asList(packLine)));
        assertTrue(OirOrders.ignoredThePackSize(
                "{\"lines\":\"电容（100只）\"}", Arrays.asList(packLine)));
        // the lines of every order are looked at, not only the first
        List<String> lines = OirOrders.linesOfOrders(OirOrders.orderListFrom(
                "{\"orders\":[{\"orderNo\":\"1\",\"lines\":\"A,part,1,1.00\"},"
                        + "{\"orderNo\":\"2\",\"lines\":\"B,part,1,1.00\"}]}"));
        assertEquals(2, lines.size());
        assertTrue(OirOrders.ignoredThePackSize(reply, lines));
    }

    @Test
    public void knowsHowManyOrdersThePictureShows() {
        // the model reports the number of blocks it counted, so a skipped one can be caught
        String answer = "{\"orderCount\":\"3\",\"orders\":["
                + "{\"orderNo\":\"1\",\"lines\":\"A,part,1,1.00\"},"
                + "{\"orderNo\":\"2\",\"lines\":\"B,part,2,2.00\"}]}";
        assertEquals(3, OirOrders.orderCountIn(answer));
        assertEquals(2, OirOrders.orderListFrom(answer).size());
        // an answer without the number is taken as it is
        assertEquals(0, OirOrders.orderCountIn("{\"orders\":[{\"orderNo\":\"1\",\"lines\":\"A,part,1,1\"}]}"));
        assertEquals(0, OirOrders.orderCountIn(null));
        // and it is found through the chat envelope as well
        String enveloped = "{\"choices\":[{\"message\":{\"content\":"
                + "\"{\\\"orderCount\\\":\\\"2\\\",\\\"orders\\\":[]}\"}}]}";
        assertEquals(2, OirOrders.orderCountIn(enveloped));
    }

    @Test
    public void saysWhatToDoWhenTheScreenshotCouldNotBeRead() {
        // a thinking model that ran out of output budget answers with reasoning only
        String truncated = "{\"choices\":[{\"finish_reason\":\"length\",\"message\":{\"content\":\"\","
                + "\"reasoning_content\":\"We need answer only JSON object. Need parse image...\"}}]}";
        String message = OirScan.scanFailure(truncated, null);
        assertTrue(message.contains("输出长度"));
        assertTrue(message.contains("再点一次"));
        assertFalse(message.contains("org.langhua"));

        // no call got through at all
        String offline = OirScan.scanFailure(null, "Connection refused");
        assertTrue(offline.contains("连不上"));
        assertTrue(offline.contains("Connection refused"));

        // the model answered something else entirely
        String other = OirScan.scanFailure("{\"orders\":[]}", null);
        assertTrue(other.contains("没从截图里读出订单"));
        assertTrue(other.contains("再点一次"));
        // the swallowed retries are still part of the message, so nobody wonders why it took longer
        assertTrue(other.contains("已自动重试 " + (OirScan.ATTEMPTS - 1) + " 次"));

        // an answer that names a count but holds no usable order was cut off, not "not an order"
        String cutOff = "{\"orderCount\":\"3\",\"orders\":[{\"orderNo\":\"3316358535012009372\",\"shop\":";
        String cutMessage = OirScan.scanFailure(cutOff, null);
        assertTrue(cutMessage.contains("3 张单"));
        assertTrue(cutMessage.contains("截断"));
        assertFalse(cutMessage.contains("模型的原话"));
    }

    @Test
    public void tellsWhichFieldTheVisionTestStillNeeds() {
        // the person is looking at the form: name the field, never a system property
        String all = OirScan.testMissing(null, null, null);
        assertTrue(all.contains("接口地址"));
        assertTrue(all.contains("模型名"));
        assertTrue(all.contains("密钥"));
        String onlyKey = OirScan.testMissing("https://api.deepseek.com/v1/chat/completions",
                "deepseek-flash", null);
        assertTrue(onlyKey.contains("密钥"));
        assertFalse(onlyKey.contains("接口地址"));
        assertFalse(onlyKey.contains("模型名"));
        // the receipt page must point at the settings page, not at -D options
        assertFalse(OirScan.missingSetting().contains("org.langhua"));
        assertTrue(OirScan.missingSetting().contains("系统设置"));
    }

    @Test
    public void readsSeveralOrdersOfOneScreenshot() {
        // the order list of the shop: three orders with their own numbers, shops and money
        String answer = "{\"orders\":["
                + "{\"orderNo\":\"3316822177083054564\",\"shop\":\"佳仕通数码专营店\","
                + "\"orderDate\":\"2026-08-30\",\"paidTotal\":\"39.50\",\"freight\":\"1.50\","
                + "\"refunded\":\"N\",\"lines\":\"MB-102,400孔面包板线 830,2,38.00\"},"
                + "{\"orderNo\":\"3316819945036045382\",\"shop\":\"Muse Lab\","
                + "\"orderDate\":\"2026-08-29\",\"paidTotal\":\"52.60\",\"freight\":\"3.00\","
                + "\"refunded\":\"N\",\"lines\":\"NanoDLA,NanoDLA 逻辑分析仪,1,49.60\"},"
                + "{\"orderNo\":\"3316999260043018554\",\"shop\":\"telesky旗舰店\","
                + "\"orderDate\":\"2026-08-29\",\"paidTotal\":\"3.87\",\"freight\":\"0.00\","
                + "\"refunded\":\"Y\",\"lines\":\"SOP16,SSOP16 贴片转直插,2,3.87\"}"
                + "]}";
        List<Map<String, String>> orders = OirOrders.orderListFrom(answer);
        assertEquals(3, orders.size());
        assertEquals("3316822177083054564", orders.get(0).get("orderNo"));
        assertEquals("1.50", orders.get(0).get("freight"));
        assertEquals("MB-102,400孔面包板线 830,2,38.00", orders.get(0).get("lines"));
        assertEquals("NanoDLA,NanoDLA 逻辑分析仪,1,49.60", orders.get(1).get("lines"));
        assertEquals("3316999260043018554", orders.get(2).get("orderNo"));
        assertTrue(OirOrders.isRefunded(orders.get(2)));
        assertFalse(OirOrders.isRefunded(orders.get(0)));

        // a single order still comes out as one row, whatever shape the model used
        assertEquals(1, OirOrders.orderListFrom(
                "{\"orders\":[{\"orderNo\":\"1\",\"lines\":\"A,part,1,1.00\"}]}").size());
        assertEquals(1, OirOrders.orderListFrom(
                "{\"orderNo\":\"42\",\"lines\":\"A,part,1,1.00\"}").size());

        // a chat envelope with the array escaped inside the message
        String enveloped = "{\"choices\":[{\"message\":{\"content\":"
                + "\"{\\\"orders\\\":[{\\\"orderNo\\\":\\\"7\\\",\\\"lines\\\":\\\"A,part,2,2.00\\\"},"
                + "{\\\"orderNo\\\":\\\"8\\\",\\\"lines\\\":\\\"B,part,3,3.00\\\"}]}\"}}]}";
        List<Map<String, String>> fromEnvelope = OirOrders.orderListFrom(enveloped);
        assertEquals(2, fromEnvelope.size());
        assertEquals("7", fromEnvelope.get(0).get("orderNo"));
        assertEquals("B,part,3,3.00", fromEnvelope.get(1).get("lines"));

        // braces inside a product name must not cut the object in half
        List<Map<String, String>> braces = OirOrders.orderListFrom(
                "{\"orders\":[{\"orderNo\":\"9\",\"lines\":\"X,板 {带孔} 2个,1,5.00\"}]}");
        assertEquals(1, braces.size());
        assertEquals("X,板 {带孔} 2个,1,5.00", braces.get(0).get("lines"));

        assertTrue(OirOrders.orderListFrom(null).isEmpty());
        assertTrue(OirOrders.orderListFrom("{\"orderNo\":\"42\"}").isEmpty());
    }

    @Test
    public void keepsARefundedOrderOutOfTheStock() {        // "退款成功" on the screenshot: the parts never stayed in the shop
        Map<String, String> refunded = oneOrder(
                "{\"orderNo\":\"3316900549003018554\",\"refunded\":\"Y\","
                + "\"lines\":\"CH32V002J4M6,CH32V002J4M6 SOP8,10,6.44\"}");
        assertTrue(OirOrders.isRefunded(refunded));
        assertEquals("Y", refunded.get("refunded"));

        Map<String, String> normal = oneOrder(
                "{\"orderNo\":\"42\",\"refunded\":\"N\",\"lines\":\"X,part,1,1.00\"}");
        assertFalse(OirOrders.isRefunded(normal));
        // older answers do not carry the flag at all, which is not a refund either
        assertFalse(OirOrders.isRefunded(oneOrder(
                "{\"orderNo\":\"42\",\"lines\":\"X,part,1,1.00\"}")));
        assertFalse(OirOrders.isRefunded(null));
    }

    @Test
    public void keepsAClosedOrderOutOfTheStockTooButSaysSoDifferently() {        // 交易关闭: the deal never went through (the person confirmed they simply never paid), so the
        // parts were never shipped - it is not a refund, and the page must say which of the two it is
        Map<String, String> closed = oneOrder(
                "{\"orderNo\":\"1975537021797500570\",\"paidTotal\":\"79.00\",\"freight\":\"0.00\","
                + "\"refunded\":\"N\",\"closed\":\"Y\","
                + "\"lines\":\"FOGO激光测距仪,FOGO激光测距仪 旗舰款,1,79.00\"}");
        assertTrue(OirOrders.isClosed(closed));
        assertTrue(OirOrders.notToBookIn(closed));
        assertFalse(OirOrders.isRefunded(closed));

        // 交易成功 with the same article is booked in
        Map<String, String> done = oneOrder(
                "{\"orderNo\":\"1975568594158500570\",\"paidTotal\":\"69.00\",\"freight\":\"0.00\","
                + "\"refunded\":\"N\",\"closed\":\"N\","
                + "\"lines\":\"FOGO激光测距仪,FOGO激光测距仪 旗舰款,1,69.00\"}");
        assertFalse(OirOrders.isClosed(done));
        assertFalse(OirOrders.notToBookIn(done));

        // a refund is left out as well, but it is not a closed order
        Map<String, String> refunded = oneOrder(
                "{\"orderNo\":\"42\",\"refunded\":\"Y\",\"lines\":\"X,part,1,1.00\"}");
        assertTrue(OirOrders.notToBookIn(refunded));
        assertFalse(OirOrders.isClosed(refunded));

        // answers read before the flag existed carry neither
        assertFalse(OirOrders.isClosed(oneOrder(
                "{\"orderNo\":\"42\",\"lines\":\"X,part,1,1.00\"}")));
        assertFalse(OirOrders.notToBookIn(oneOrder(
                "{\"orderNo\":\"42\",\"lines\":\"X,part,1,1.00\"}")));
        assertFalse(OirOrders.isClosed(null));
    }

    @Test
    public void readsTheWorkbookTaobaoLetsABuyerDownload() {
        // the export prints the order level cells on the first row of an order and leaves the next
        // rows empty, so the second article of an order must land on the same order
        List<Map<String, String>> rows = new ArrayList<>();
        rows.add(excelRow("订单号", "订单提交时间", "订单状态", "店铺名称", "商品名称", "型号款式",
                "商品数量", "商品金额", "实付金额", "运费"));
        rows.add(excelRow("2012720340237500570", "2023-11-10 16:22:01", "交易成功", "高芯达电子",
                "鼠标脚贴3M脚垫", "3M G102黑色脚贴", "2", "￥5.05", "￥23.14", "￥3.00"));
        rows.add(excelRow("", "", "", "", "SMA天线", "2.4G/5.8G双频8dBi", "2", "￥5.02", "", ""));
        rows.add(excelRow("776630145044500570", "2023-11-11 09:00:00", "交易成功", "宇宙悍将",
                "面包板", "830孔 面包板", "1", "￥64.00", "￥51.80", "￥0.00"));
        rows.add(excelRow("1975537021797500570", "2023-09-19 10:00:00", "交易关闭", "优速家居",
                "激光测距仪", "旗舰款（50米）", "1", "￥79.00", "￥79.00", "￥0.00"));
        rows.add(excelRow("1234567890123450570", "2023-09-20 10:00:00", "充值成功", "话费充值",
                "100元话费", "", "1", "￥100.00", "￥100.00", "￥0.00"));

        List<Map<String, String>> orders = MfgOrderExcel.ordersOfRows(rows);
        assertEquals(4, orders.size());

        Map<String, String> first = orders.get(0);
        assertEquals("2012720340237500570", first.get("orderNo"));
        assertEquals("高芯达电子", first.get("shop"));
        assertEquals("2023-11-10", first.get("orderDate"));
        assertEquals("3.00", first.get("freight"));
        assertEquals("2", first.get("rowCount"));
        // 商品金额 is the price of one piece: two pieces of 5.05 are 10.10, two of 5.02 are 10.04
        assertTrue(first.get("lines").startsWith("3M G102黑色脚贴\t"));
        assertTrue(first.get("lines").contains("\t2\t10.10"));
        assertTrue(first.get("lines").contains("\t2\t10.04"));
        assertNull(first.get("skip"));
        // 20.14 of goods plus 3.00 of freight is what was paid: no discount to keep
        assertEquals("0", first.get("discount"));

        // a red packet makes the paid total smaller than the lines plus the freight: the difference
        // is the discount of the receipt, not a mangled line amount (Step 95)
        assertEquals("12.20", orders.get(1).get("discount"));

        Map<String, String> closed = orders.get(2);
        assertEquals(MfgOrderExcel.SKIP_CLOSED, closed.get("skip"));

        Map<String, String> topUp = orders.get(3);
        assertEquals(MfgOrderExcel.SKIP_TOPUP, topUp.get("skip"));

        // a file that is not a workbook is answered with "no orders" instead of an exception
        assertTrue(MfgOrderExcel.ordersOf(new byte[] {1, 2, 3}).isEmpty());
        assertTrue(MfgOrderExcel.ordersOf(null).isEmpty());
    }

    @Test
    public void readsTheZipOfAWorkbookAndItsSharedStrings() throws IOException {
        // an xlsx is a zip: xl/sharedStrings.xml holds the text, the sheet points at it by index
        java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
        try (java.util.zip.ZipOutputStream zip = new java.util.zip.ZipOutputStream(bytes)) {
            zip.putNextEntry(new java.util.zip.ZipEntry("xl/sharedStrings.xml"));
            zip.write(("<?xml version=\"1.0\" encoding=\"UTF-8\"?><sst>"
                    + "<si><t>订单号</t></si><si><t>订单状态</t></si>"
                    + "<si><t>2012720340237500570</t></si><si><t>交易成功</t></si></sst>")
                    .getBytes(java.nio.charset.StandardCharsets.UTF_8));
            zip.closeEntry();
            zip.putNextEntry(new java.util.zip.ZipEntry("xl/worksheets/sheet1.xml"));
            zip.write(("<?xml version=\"1.0\" encoding=\"UTF-8\"?><worksheet><sheetData>"
                    + "<row r=\"1\"><c r=\"A1\" t=\"s\"><v>0</v></c><c r=\"B1\" t=\"s\"><v>1</v></c></row>"
                    + "<row r=\"2\"><c r=\"A2\" t=\"s\"><v>2</v></c><c r=\"B2\" t=\"s\"><v>3</v></c>"
                    + "<c r=\"C2\" t=\"inlineStr\"><is><t>7</t></is></c></row>"
                    + "</sheetData></worksheet>").getBytes(java.nio.charset.StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        List<Map<String, String>> rows = MfgOrderExcel.rowsOf(bytes.toByteArray());
        assertEquals(2, rows.size());
        assertEquals("订单号", rows.get(0).get("A"));
        assertEquals("交易成功", rows.get(1).get("B"));
        // an inline string is read as well (Taobao writes the newer columns that way)
        assertEquals("7", rows.get(1).get("C"));

        // a zip that carries no worksheet is answered with nothing instead of an exception
        assertTrue(MfgOrderExcel.rowsOf("not a zip".getBytes(java.nio.charset.StandardCharsets.UTF_8))
                .isEmpty());
    }

    @Test
    public void fitsALongPartNumberIntoTheCodeField() {
        // a code that fits is kept, the blanks around it are not
        assertEquals("CAP-104-50V", MfgProductServices.fittingComponentCode(" CAP-104-50V "));

        // the blanks inside the number stay: 5.5V1.5F C型 21*7.5 must not become 5.5V1.5FC217.5
        assertEquals("5.5V1.5F C型 21*7.5", MfgProductServices.fittingComponentCode("5.5V1.5F  C型   21*7.5"));

        // the code field holds 100 characters, so a package description is stored as it is
        String long1 = "QFN16-0.65-QFN20-0.4-2.54";
        assertEquals(long1, MfgProductServices.fittingComponentCode(long1));

        // only something longer than the column is shortened, always the same way
        String tooLong = "QFN16-0.65-QFN20-0.4-2.54-" + "1234567890".repeat(9);
        String fitted = MfgProductServices.fittingComponentCode(tooLong);
        assertTrue(fitted.length() <= 100);
        assertTrue(fitted.startsWith("QFN16-0.65-QFN20"));
        assertEquals(fitted, MfgProductServices.fittingComponentCode(tooLong));
        assertFalse(fitted.equals(MfgProductServices.fittingComponentCode(tooLong + "X")));

        // the lines of an order keep their codes and their four cells
        String lines = OirOrders.fitOrderLines(long1 + ",转接板 2.54mm,2,12.60");
        String[] cells = lines.split("\t");
        assertEquals(long1, cells[0]);
        assertEquals("转接板 2.54mm", cells[1]);
        assertEquals("2", cells[2]);
        assertEquals("12.60", cells[3]);
    }

    @Test
    public void reportsAFreightThatDoesNotAddUp() {
        // the page says 实付款 3.50 and 含运费 2.00, the model answered a freight of 4.00: the money
        // does not add up, so it is reported - the freight is not silently rewritten any more, because
        // a number like that is just as likely a missing article
        Map<String, String> wrong = new HashMap<>();
        wrong.put("paidTotal", "3.50");
        wrong.put("freight", "4.00");
        wrong.put("lines", "SK-12D02,switch,10,1.50");
        String said = OirOrders.checkOrderTotals(wrong);
        assertTrue(said != null && said.contains("3.50") && said.contains("4.00"));
        assertEquals("4.00", wrong.get("freight"));

        // neither freight nor discount: the money does not come out, so it is reported as a possible
        // missing article instead of being turned into a freight
        Map<String, String> missing = new HashMap<>();
        missing.put("paidTotal", "3.50");
        missing.put("lines", "SK-12D02,switch,10,1.50");
        String missingNote = OirOrders.checkOrderTotals(missing);
        assertTrue(missingNote != null && missingNote.contains("可能漏了一件商品"));
        assertEquals("0.00", missing.get("freight"));

        // a freight that adds up is left alone, and so is an order without a paid total
        Map<String, String> fine = new HashMap<>();
        fine.put("paidTotal", "3.50");
        fine.put("freight", "2.00");
        fine.put("lines", "SK-12D02,switch,10,1.50");
        assertNull(OirOrders.checkOrderTotals(fine));
        assertEquals("2.00", fine.get("freight"));

        Map<String, String> unknown = new HashMap<>();
        unknown.put("freight", "4.00");
        unknown.put("lines", "SK-12D02,switch,10,1.50");
        assertNull(OirOrders.checkOrderTotals(unknown));
        assertEquals("4.00", unknown.get("freight"));
    }

    @Test
    public void keepsAnOrderLineWithoutACode() {
        // a shop that prints no article code: the model answers with an empty first cell
        List<String[]> rows = OirOrders.parseOrderArticleLines(
                ",超级法拉电容器 5.5V1.5F C型 21*7.5,2,9.28");
        assertEquals(1, rows.size());
        assertEquals("超级法拉电容器 5.5V1.5F C型 21*7.5", rows.get(0)[0]);
        assertEquals("2", rows.get(0)[2]);

        // a name without any numbers is still a code of its own, and the same every time
        assertEquals("超级法拉电容器", OirOrders.codeFromName("超级法拉电容器"));
        assertFalse(OirOrders.codeFromName("超级法拉电容器")
                .equals(OirOrders.codeFromName("瓷片电容器")));
        assertEquals("P-000000", OirOrders.codeFromName("  "));

        // and nothing is checked against the paid total when no line could be read
        Map<String, String> unreadable = new HashMap<>();
        unreadable.put("paidTotal", "9.28");
        unreadable.put("lines", ",,,");
        assertNull(OirOrders.checkOrderTotals(unreadable));
    }

    @Test
    public void quotesWhatTheVisionModelAnswered() {
        // the quote is what tells "this is not an order" from "the model cannot see images"
        assertEquals("（空回答）", OirOrders.visionReplyExcerpt(null, 40));
        assertEquals("This is a web page not an order", OirOrders.visionReplyExcerpt(
                "{\"choices\":[{\"message\":{\"content\":\"This is a web page\\nnot an order\"}}]}", 40));
        assertEquals("cannot see the image", OirOrders.visionReplyExcerpt(
                "{\"choices\":[{\"message\":{\"content\":\"\","
                + "\"reasoning_content\":\"cannot see the image\"}}]}", 40));
        assertEquals("abcd…", OirOrders.visionReplyExcerpt("abcdefgh", 4));
    }

    @Test
    public void completesTheVisionEndpoint() {
        // people type only the host, or stop at the version: the API answers those with 404
        assertEquals("https://api.deepseek.com/v1/chat/completions",
                OirOrders.normalizeVisionEndpoint("https://api.deepseek.com"));
        assertEquals("https://api.deepseek.com/v1/chat/completions",
                OirOrders.normalizeVisionEndpoint("https://api.deepseek.com/"));
        assertEquals("https://open.bigmodel.cn/api/paas/v4/chat/completions",
                OirOrders.normalizeVisionEndpoint("https://open.bigmodel.cn/api/paas/v4"));
        // a complete address is left alone
        assertEquals("https://api.openai.com/v1/chat/completions",
                OirOrders.normalizeVisionEndpoint("https://api.openai.com/v1/chat/completions"));
        assertNull(OirOrders.normalizeVisionEndpoint(null));
    }

    @Test
    public void explainsAFailedVisionCall() {
        assertTrue(OirOrders.describeVisionError(404, "").contains("chat/completions"));
        assertTrue(OirOrders.describeVisionError(401, "{\"error\":\"bad key\"}").contains("密钥"));
        assertTrue(OirOrders.describeVisionError(400, "").contains("不支持看图"));
        // the body is quoted but kept short
        String longBody = OirOrders.describeVisionError(400,
                "{\"error\":\"" + "x".repeat(500) + "\"}");
        assertTrue(longBody.contains("…"));
        assertTrue(longBody.length() < 380);
    }
}
