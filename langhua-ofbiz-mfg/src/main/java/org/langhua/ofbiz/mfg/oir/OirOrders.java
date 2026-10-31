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

import org.langhua.ofbiz.mfg.MfgCsv;
import org.langhua.ofbiz.mfg.MfgProductServices;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The rules that turn what the vision service answered into orders: reading the answer, joining the
 * reads of one long page, and making the money of every order add up.
 *
 * <p>Nothing here talks to the network or to the database: the class is pure - a string in, orders
 * out - so it can be read, tested and replaced on its own (see {@code oir/README.md}).
 */
public final class OirOrders {

    private OirOrders() {
    }

    /**
     * How far two readings of the same block may be apart in money, in yuan: six fen.
     *
     * <p>A page prints its unit prices rounded to the fen, so a block of 100 pieces at 2.179 prints
     * "¥2.18 x100" and its money can be a tenth off. The value is measured, not guessed: 0.06 is what
     * let the 27 order page come back with no "请核对" row at all (Step 129), while a read that lost a
     * whole article was still caught.
     */
    private static final double MONEY_TOLERANCE = 0.06;

    /**
     * Half a fen. That is the most one piece can be off by when the page rounds its unit price to the
     * fen, so a row of {@code n} pieces can be off by {@code 0.005 * n} - see {@link #roundingSlack}.
     * An amount below it is zero once rounded to the fen.
     */
    private static final double HALF_A_FEN = 0.005;

    /**
     * What one row of an order may be off by at most, in yuan: a dime. The page prints its amounts in
     * money, so a row is exact to the fen; the room is only there for a unit price the page rounded
     * (2.179 printed as 2.18 over 100 pieces). Without the cap a hundred piece row leaves fifty cents
     * of room, and nine of them hid 4.50 yuan of goods the model never read (Step 159).
     */
    private static final double MAX_ROW_SLACK = 0.10;

    /**
     * The share of a block the page may be off by when it prints its unit prices rounded to the fen: a
     * sixth of a fen on a price of 2.12 is 0.05%, and a block of 210 piece rows reads 2564.10 where
     * 2562.84 was paid. A row that was lost is off by a large share of the block (15.90 read as 11.40),
     * so the two can be told apart by how much of the block is missing (Step 159).
     */
    private static final double RELATIVE_SLACK = 0.005;

    /** What counts as the same number when a quantity is compared with one. */
    private static final double PRACTICALLY_ZERO = 0.0001;

    /** How many packs of a row its money may be the price of (Step 145). */
    private static final int MAX_PACKS = 20;

    /**
     * How far short of what was paid the rows of a block have to come before their amounts are taken
     * for the price of one piece each: a block that holds the money of its rows comes close to the paid
     * total, while prices alone come to a half or a third of it (Step 190).
     */
    private static final double ROW_MONEY_SHORTFALL = 0.75;

    /**
     * How large a gap may be before it stops looking like the freight the model did not read: a page
     * of that shape paid 127.46 with 4.00 of freight and the pieces left 3.96 (Step 190).
     */
    private static final double FREIGHT_FLOOR = 10.0;

    private static final double FREIGHT_SHARE = 0.1;

    /**
     * How much of the goods a red packet or a 满减 may take, when the discount is not read but worked
     * out from the paid total. Half is generous for a coupon and far below what a bad read makes of the
     * difference: a block of five rows copied over answers 975.00 of goods against 195.65 paid, and
     * turning those 780.35 into a discount would book an order nobody placed (Step 162).
     */
    private static final double MAX_DISCOUNT_SHARE = 0.5;

    /** How many digits of an order number a second read may lose and still be the same order. */
    private static final int MISSING_DIGITS_ALLOWED = 4;

    /** The notes a shop prints behind an article code, which a second read may leave out. */
    private static final Pattern TRADE_NOTE = Pattern.compile(
            "\\s*[\\[【(（][^\\]】)）]{0,6}(交易快照|快照)[\\]】)）]");

    /** A pack size a shop prints behind the code, e.g. {@code 黄色（5个）}. */
    private static final Pattern PACK_SUFFIX = Pattern.compile(
            "[（(]\\s*[0-9]+\\s*[只个块件套]\\s*[）)]\\s*$");

    private static final Pattern BLANKS = Pattern.compile("\\s+");

    public static List<String[]> parseOrderArticleLines(String text) {
        List<String[]> rows = new ArrayList<>();
        if (text == null) {
            return rows;
        }
        for (String raw : text.split("\r?\n")) {
            if (raw.trim().isEmpty() || raw.trim().startsWith("#")) {
                continue;
            }
            // the first cell may be empty - a shop that prints no article code - and trimming the line
            // took the tab that held that empty cell with it: the row then had three cells, was
            // dropped, and `mfgReceiptFromOrder` refused the whole order with "没有解析出明细" while the
            // page showed the row quite happily (Step 163)
            String line = raw.replaceAll("\\s+$", "");
            String[] cells = MfgCsv.splitCsvLine(line, cellsSeparator(line));
            if (cells.length == 3) {
                // a shop that prints no article code: the model answers an empty first cell, and by the
                // time the text reaches the service the first cell is gone - OFBiz trims every request
                // parameter (UtilHttp.canonicalizeParameterMap), so the row arrives as name, quantity,
                // amount. Read it as a row without a code rather than losing the order (Step 163)
                if (optionalAmount(cells[1]) == null || optionalAmount(cells[2]) == null) {
                    // a row of code, name, quantity reads the same way: only take three cells when the
                    // middle one is a quantity and the last one is money
                    continue;
                }
                cells = new String[] {"", trimmedCell(cells[0]), trimmedCell(cells[1]),
                    trimmedCell(cells[2])};
            }
            if (cells.length < 4) {
                continue;
            }
            String code = cells[0] == null ? "" : cells[0].trim();
            if ("料号".equals(code)) {
                continue;
            }
            if (code.isEmpty()) {
                // a shop often prints no article code at all, and a model then answers with an
                // empty first cell; the line is still worth keeping, so the code comes from the name
                code = codeFromName(cells[1]);
            }
            rows.add(new String[] {code, trimmedCell(cells[1]), trimmedCell(cells[2]),
                trimmedCell(cells[3])});
        }
        return rows;
    }

    /** @return the cell without the spaces a copied line may hold around it */
    private static String trimmedCell(String cell) {
        return cell == null ? "" : cell.trim();
    }

    private static char cellsSeparator(String line) {
        return line.indexOf('\t') >= 0 ? '\t' : ',';
    }

    /**
     * Builds a part number from the name of an article, for an order that prints no code: the
     * printable ASCII of the name (5.5V1.5F C型 21*7.5 gives 5.5V1.5FC217.5), or a short stable code
     * of the whole name when it holds no ASCII at all. The same name always gives the same code, so
     * a second delivery still lands on the same lots.
     *
     * @param name the article name
     * @return a code that fits the code fields, never empty
     */
    public static String codeFromName(String name) {
        String text = name == null ? "" : name.trim().replaceAll("\\s+", " ");
        if (text.isEmpty()) {
            return "P-000000";
        }
        // the name itself is the clearest code a shop has: 5.5V1.5F C型 21*7.5 stays readable,
        // joining the words would give 5.5V1.5FC217.5 where F, C型 and 21*7.5 run together
        return MfgProductServices.fittingComponentCode(text);
    }

    /**
     * Fits a part number into the 100 characters the code fields allow. A shop order can carry a
     * whole package description as the part number, and a real one fits; anything longer is
     * shortened with a short hash of the whole text ({@code QFN16-0.65-6f1a2b}) so that two similar
     * parts never merge and the same text always gives the same code.
     *
     * @param code the part number as typed or recognised
     * @return the code itself when it fits, otherwise a shorter but stable one
     */
    public static String fitOrderLines(String lines) {
        List<String[]> rows = parseOrderArticleLines(lines);
        if (rows.isEmpty()) {
            return lines;
        }
        StringBuilder out = new StringBuilder();
        for (String[] row : rows) {
            if (out.length() > 0) {
                out.append('\n');
            }
            // the note a shop prints behind the title ([交易快照]) is not part of the part number, and
            // a model that has no code to copy often answers with the whole title
            String fitted = MfgProductServices.fittingComponentCode(rowKey(row[0]));
            // only a shortened code needs the full number kept in the name
            String name = fitted.equals(row[0]) ? (row[1] == null ? "" : row[1])
                    : MfgProductServices.nameWithFullCode(row[1], row[0]);
            out.append(fitted).append('\t').append(name)
                    .append('\t').append(row[2]).append('\t').append(row[3]);
        }
        return out.toString();
    }

    /**
     * Reads the orders of one answer that must carry their order number, which is the key the import
     * joins on and the one field a read may not invent.
     *
     * @param reply the answer text
     * @return the orders of the answer, none of them allowed to have lost its order number
     */
    public static List<Map<String, String>> orderListFrom(String reply) {
        return orderListFrom(reply, false);
    }

    /**
     * Reads the orders of one answer.
     *
     * @param reply the answer text
     * @param partial {@code true} for one slice of a long page: a slice can start in the middle of
     *        an order, with its number printed above the cut, and the rows it reads are worth keeping -
     *        the scan hangs them on the order whose header the slice before it carried
     * @return one map per order that carries lines, empty when there is nothing usable
     */
    public static List<Map<String, String>> orderListFrom(String reply, boolean partial) {
        List<Map<String, String>> orders = new ArrayList<>();
        String text = orderAnswerText(reply);
        if (text == null) {
            return orders;
        }
        // the objects of the "orders" array, or the answer itself when it is one flat object
        int array = text.indexOf('[');
        int key = text.indexOf("\"orders\"");
        int from = key >= 0 && array > key ? array : text.indexOf('{');
        if (from < 0) {
            return orders;
        }
        for (String object : objectTexts(text, from)) {
            Map<String, String> fields = fieldsOfOneOrder(object, partial);
            if (fields != null) {
                orders.add(fields);
            }
        }
        if (orders.isEmpty()) {
            // an answer without an object wrapper, e.g. the four values separated by commas
            Map<String, String> flat = fieldsOfOneOrder(text, partial);
            if (flat != null) {
                orders.add(flat);
            }
        }
        return orders;
    }

    /**
     * How many order blocks the model says the picture shows. The number is asked for because a model
     * that reads three orders and answers with two would otherwise be taken as a success, and the
     * person would import what it got without noticing the missing one.
     *
     * @param reply the answer text
     * @return the number it reported, 0 when the answer does not say
     */
    public static int orderCountIn(String reply) {
        String text = orderAnswerText(reply);
        if (text == null) {
            return 0;
        }
        String count = jsonStringField(text, "orderCount");
        if (count == null) {
            return 0;
        }
        try {
            return Integer.parseInt(count.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /**
     * The JSON of the answer itself: a chat envelope may wrap it as the message or as the reasoning
     * of a model that thinks before it answers. Both are read from the raw reply so quotes survive.
     *
     * @param reply the answer text, may be {@code null}
     * @return the JSON text, {@code null} when there is none
     */
    private static String orderAnswerText(String reply) {
        if (reply == null) {
            return null;
        }
        String inner = jsonStringField(reply, "content");
        if (!carriesOrders(inner)) {
            String reasoning = jsonStringField(reply, "reasoning_content");
            if (carriesOrders(reasoning)) {
                inner = reasoning;
            }
        }
        if (carriesOrders(inner)) {
            return inner;
        }
        return reply;
    }

    /**
     * @param text what a field of the chat envelope holds
     * @return {@code true} when it is the JSON of an answer instead of prose: the orders of a picture, or
     *         the header lines of the "numbers only" question
     */
    private static boolean carriesOrders(String text) {
        return text != null && (text.contains("lines") || text.contains("\"orders\"")
                || text.contains("blocks"));
    }

    /**
     * Reads the fields of one order object, keeping it only when it prints its order number.
     *
     * @param text the JSON of that one order
     * @return {@code orderNo}, {@code shop}, {@code orderDate}, {@code freight}, {@code paidTotal},
     *         {@code refunded} and {@code lines}, {@code null} when the object is not an order
     */
    private static Map<String, String> fieldsOfOneOrder(String text) {
        return fieldsOfOneOrder(text, false);
    }

    /**
     * Reads the fields of one order object.
     *
     * @param text the JSON of that one order
     * @param anonymousOk {@code true} to keep an object that carries articles but no order number
     * @return {@code orderNo}, {@code shop}, {@code orderDate}, {@code freight}, {@code paidTotal},
     *         {@code refunded} and {@code lines}, {@code null} when the object is not an order
     */
    private static Map<String, String> fieldsOfOneOrder(String text, boolean anonymousOk) {
        Map<String, String> fields = new LinkedHashMap<>();
        String orderNo = jsonStringField(text, "orderNo");
        // an order always prints its number, and it is the key the import de-duplicates on: an object
        // without one is the schema example or a stray brace, not an order (a model that talks a lot
        // echoes the template, and that used to arrive as seven empty "orders")
        if (orderNo == null || orderNo.trim().isEmpty()) {
            orderNo = anonymousOk ? "" : null;
        }
        if (orderNo == null) {
            return null;
        }
        fields.put("orderNo", orderNo.trim());
        fields.put("shop", jsonStringField(text, "shop"));
        // the shop platform the page names (淘宝 / 天猫): the fields are the same on both, so only the
        // label travels with the answer
        fields.put("platform", jsonStringField(text, "platform"));
        // the date printed on a shop order is the day it was placed, not the day the parts arrive;
        // older answers used the bare name date, so both are read
        String orderDate = jsonStringField(text, "orderDate");
        fields.put("orderDate", orderDate != null ? orderDate : jsonStringField(text, "date"));
        fields.put("freight", jsonStringField(text, "freight"));
        fields.put("paidTotal", jsonStringField(text, "paidTotal"));
        // a Taobao red packet or coupon: the difference between the articles plus freight and what was paid
        fields.put("discount", jsonStringField(text, "discount"));
        // a refunded order must not be booked into stock, so the answer carries that too
        fields.put("refunded", jsonStringField(text, "refunded"));
        // and a closed one (交易关闭 / 已取消: the deal never went through, usually never paid) is
        // not booked in either - the shop prints 交易关闭 on its own badge, next to 交易成功
        fields.put("closed", jsonStringField(text, "closed"));
        // how many article rows the block prints: six colours of one board are six rows that look
        // the same, and a model that merges them into one item still adds up to what was paid
        fields.put("rowCount", jsonStringField(text, "rowCount"));
        String lines = itemsIn(text);
        if (lines == null) {
            lines = jsonStringField(text, "lines");
        }
        if (lines == null || lines.trim().isEmpty() || isLineTemplate(lines.trim())) {
            return null;
        }
        fields.put("lines", lines.trim());
        return fields;
    }

    /**
     * Reads the articles of one order out of an answer that carries them as an array of objects,
     * one object per product, and turns them into the tab separated rows the rest of the code uses.
     *
     * <p>This is the shape the prompt asks for: a model writing one long string has to escape tabs
     * and line breaks itself, and on a page with two similar products it produced a single row whose
     * amount was {@code 3.24;402530} - the second article swallowed by the first. One object per
     * product cannot be merged that way.
     *
     * @param text the JSON of one order
     * @return the rows, or {@code null} when the order carries no {@code items} array
     */
    private static String itemsIn(String text) {
        int at = text.indexOf("\"items\"");
        if (at < 0) {
            return null;
        }
        int arrayStart = text.indexOf('[', at);
        if (arrayStart < 0) {
            return null;
        }
        int arrayEnd = matchingBracket(text, arrayStart);
        if (arrayEnd < 0) {
            return null;
        }
        List<String> rows = new ArrayList<>();
        for (String item : objectTexts(text.substring(arrayStart + 1, arrayEnd), 0)) {
            String code = jsonStringField(item, "code");
            String name = jsonStringField(item, "name");
            String qty = jsonStringField(item, "qty");
            if (qty == null) {
                qty = jsonStringField(item, "quantity");
            }
            String amount = jsonStringField(item, "amount");
            if (code == null && name == null && qty == null && amount == null) {
                continue;
            }
            // the price printed beside the row and the number printed after x are asked for on their
            // own, and the multiplication is done here: a model that reads ¥13.68 beside x2 answers the
            // money of the row as 13.68 often enough that the block came back at a third of what was
            // paid (Step 191)
            String money = moneyOfPriceAndTimes(jsonStringField(item, "price"),
                    jsonStringField(item, "times"));
            if (money != null) {
                amount = money;
            }
            code = valueOfSpecLine(code);
            if ((code == null || code.trim().isEmpty()) && name != null && !name.trim().isEmpty()) {
                // the model left the code out: the import derives it from the name anyway, so the
                // table shows the code that will really be stored instead of an empty cell
                code = codeFromName(name);
            }
            String row = oneCell(code) + "\t" + oneCell(name) + "\t" + oneCell(qty) + "\t" + oneCell(amount);
            if (isLineTemplate(row)) {
                continue;
            }
            rows.add(row);
        }
        return rows.isEmpty() ? null : String.join("\n", rows);
    }

    /**
     * The money of an article row out of the two numbers the page prints: the price beside it and the
     * number after {@code x}.
     *
     * <p>The page prints {@code ¥13.68} and {@code x2}, and the money of that row is 27.36. A model
     * reads those two numbers far more reliably than it multiplies them: the same block came back with
     * 68.29 of the 127.46 that was paid, twice, and with seven rows out of eight (Step 191). So the row
     * carries its own price and its own count, and the product is worked out here.
     *
     * @param priceText the price printed beside the row, {@code ¥} and blanks allowed
     * @param timesText the number printed after {@code x}
     * @return the money of the row as text, {@code null} when the answer did not give both
     */
    public static String moneyOfPriceAndTimes(String priceText, String timesText) {
        if (priceText == null || timesText == null) {
            return null;
        }
        String price = priceText.replace("¥", "").replace("￥", "").replace("元", "")
                .replace(",", "").replace("，", "").trim();
        String times = timesText.trim();
        int x = times.indexOf('x');
        if (x < 0) {
            x = times.indexOf('X');
        }
        if (x >= 0) {
            times = times.substring(x + 1).trim();
        }
        double money = toDouble(price, -1.0);
        double count = toDouble(times, -1.0);
        if (money <= 0.0 || count < 1.0) {
            return null;
        }
        return String.format(Locale.ROOT, "%.2f", Double.valueOf(round2(money * count)));
    }

    /**
     * Tells whether an answer gave every row of a block the same part number although the rows are
     * different articles.
     *
     * <p>A page of fifteen capacitor packs prints one title for all of them and the value of each pack
     * on its own grey spec line ({@code 0805 47UF 476M 10% 16V 20个}); a model that answers the start
     * of the line answers {@code 0805} for all fifteen, and the fifteen articles then import as one
     * part. Different money is what makes them different articles - a block that bought the same part
     * twice pays the same for both rows and is left alone (Step 193).
     *
     * @param orders the order maps of one answer
     * @return {@code true} when a block of several rows shares one part number but not one price
     */
    public static boolean codesDoNotTellTheRowsApart(List<Map<String, String>> orders) {
        return sharedCodeOf(orders) != null;
    }

    /**
     * The part number an answer gave every row of a block whose rows are different articles, so the
     * request that goes back can name it: a block of fifteen capacitor packs came back with
     * {@code 0805} on all fifteen rows, and that is the package size printed at the start of each
     * grey spec line, not the article. Naming it is what makes the model drop it and read the value
     * behind it instead of answering the same word again (Step 196).
     *
     * @param orders the order maps of one answer
     * @return the code every row of such a block carries, {@code null} when no block does that
     */
    public static String sharedCodeOf(List<Map<String, String>> orders) {
        if (orders == null) {
            return null;
        }
        for (Map<String, String> order : orders) {
            if (order == null) {
                continue;
            }
            List<String[]> rows = parseOrderArticleLines(order.get("lines"));
            if (rows.size() < 3) {
                continue;
            }
            Set<String> codes = new LinkedHashSet<>();
            Set<String> amounts = new LinkedHashSet<>();
            for (String[] row : rows) {
                codes.add(rowKey(row[0]));
                amounts.add(String.valueOf(round2(toDouble(row[3], 0.0))));
            }
            if (codes.size() == 1 && amounts.size() >= 2) {
                return rowKey(rows.get(0)[0]);
            }
        }
        return null;
    }

    /**
     * Tells a package size from a value, so a request that goes back can say what to do with the word
     * every row of a block was answered with.
     *
     * <p>The package size is the four digits a shop prints at the start of the grey spec line (0402,
     * 0603, 0805, 1206, 2512), and it is the one thing all rows of a list of capacitors have in common.
     * It is told by its shape and not by a list of the sizes in use: a page that prints 2010 or 1812 is
     * the same page. Nothing is decided by the name here - the word itself is taken from the answer
     * ({@link #sharedCodeOf}), and this only says whether it is a size, so the request can tell the
     * model to drop it and write what follows it (Step 196).
     *
     * @param code the word every row of a block was answered with
     * @return {@code true} when that word is a bare package size
     */
    public static boolean looksLikeAPackageSize(String code) {
        return code != null && PACKAGE_SIZE.matcher(code.trim()).matches();
    }

    /** A bare package size: the three or four digits printed at the head of a grey spec line. */
    private static final Pattern PACKAGE_SIZE = Pattern.compile("\\d{3,4}");

    /** The pack size a shop prints after a value in half width brackets: {@code 5.1K (50只)}. */
    private static final Pattern PACK_IN_BRACKETS =
            Pattern.compile("\\s*[（(]\\s*\\d+\\s*(?:只|个|件|只装|片|套)\\s*[)）]\\s*$");

    /**
     * The code of a row, cut back to the value when the model answered the whole grey spec line of the
     * page instead of the one value that tells this row from the next.
     *
     * <p>One shop prints {@code 2K （一件100只）;1%精度;0603贴片电阻} under every row of a long list of
     * resistors, and a block of four came back with that whole line as the code of each row - four rows
     * that import as one part (Step 187). The value is what the line starts with, up to the pack size in
     * full width brackets or the first semicolon; a code that carries half width brackets of its own
     * ({@code 502035(350mah)}, {@code 2.0mm单排8P(共8P)}) has no such separator and is left alone.
     *
     * @param code the code as the answer gave it
     * @return the value of the row, the code itself when it is not a spec line
     */
    public static String valueOfSpecLine(String code) {
        if (code == null) {
            return null;
        }
        String text = code.trim();
        // the grey line of a row starts with the shop's own promises on some pages (7天无理由退货 /
        // 退货宝), and a model that takes the start of that line answers the promise as the part number:
        // two rows of one order then share the code 7天无理由退货 and the parts lose their number
        // (Step 192)
        for (int guard = 0; guard < 3; guard++) {
            String stripped = SHOP_PROMISE.matcher(text).replaceFirst("").trim();
            if (stripped.equals(text)) {
                break;
            }
            text = stripped;
        }
        if (text.isEmpty()) {
            // nothing but a promise: the caller falls back to the title
            return "";
        }
        int semicolon = text.indexOf(';');
        if (semicolon > 0) {
            text = text.substring(0, semicolon).trim();
        }
        // a pack size in half width brackets at the end (5.1K (50只)) goes too, so the same part bought
        // in another pack size keeps one code
        text = PACK_IN_BRACKETS.matcher(text).replaceFirst("").trim();
        int bracket = text.indexOf('（');
        if (bracket > 0) {
            text = text.substring(0, bracket).trim();
        }
        return text.isEmpty() ? code : text;
    }

    /** The shop's own promises, printed at the start of the grey line of every row. */
    private static final Pattern SHOP_PROMISE = Pattern.compile(
            "^(7天无理由退货|7天无理由|7天退换|15天无理由|退货宝|退换货|极速退款|48小时内发货|"
                    + "赠运费险|官方正品)[\\s、,，:：]*");

    /**
     * One field of an article row, with tabs and line breaks flattened so they cannot break the row
     * apart when it is turned back into a line.
     *
     * @param value the field, may be {@code null}
     * @return the value, empty when there is none
     */
    private static String oneCell(String value) {
        if (value == null) {
            return "";
        }
        return value.replace('\t', ' ').replace('\r', ' ').replace('\n', ' ').trim();
    }

    /**
     * Tells the placeholder row of the answer format ({@code code,name,quantity,amount}) from a real
     * one, so the example a chatty model repeats never shows up as an article.
     *
     * @param lines the lines of one order
     * @return {@code true} when every row is the placeholder
     */
    public static boolean isLineTemplate(String lines) {
        if (lines == null || lines.trim().isEmpty()) {
            return false;
        }
        boolean sawRow = false;
        for (String row : lines.split("\r?\n")) {
            String trimmed = row.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            sawRow = true;
            String lower = trimmed.toLowerCase();
            if (!lower.startsWith("code,") && !lower.startsWith("code\t") && !lower.startsWith("料号,")) {
                return false;
            }
        }
        return sawRow;
    }

    /**
     * Says whether an order came back with fewer article rows than the block prints. The money
     * cannot catch this one: one board in six colours read as a single item of 41.28 adds up exactly,
     * so the model reports how many rows it counted and a shorter answer is sent once more.
     *
     * @param order one order of an answer
     * @return {@code true} when the order counts more rows than were read
     */
    public static boolean rowsMissing(Map<String, String> order) {
        if (order == null) {
            return false;
        }
        double said = toDouble(order.get("rowCount"), 0.0);
        if (said <= 0.0) {
            return false;
        }
        return parseOrderArticleLines(order.get("lines")).size() < (int) said;
    }

    /**
     * What two orders of one answer share when one of them was copied from the other: the same
     * articles and the same money. A model reading a list block by block sometimes carries the
     * articles of the block before into the next one; the copy adds up (its freight and its paid
     * total come along too), so neither the money check nor the row count can see it.
     *
     * @param order one order of an answer
     * @return the key two copies share, {@code null} when the order has no articles
     */
    public static String orderContentKey(Map<String, String> order) {
        if (order == null) {
            return null;
        }
        List<String[]> rows = parseOrderArticleLines(order.get("lines"));
        if (rows.isEmpty()) {
            return null;
        }
        List<String> parts = new ArrayList<>();
        for (String[] row : rows) {
            parts.add(rowIdentity(row));
        }
        java.util.Collections.sort(parts);
        return String.join(";", parts) + "|" + order.get("paidTotal") + "|" + order.get("freight");
    }

    /**
     * What two orders share when one of them was copied from the other, when the codes and names are
     * not alike enough: the quantities, the amounts and the money of the order. Two reads of one
     * printed line write the code differently now and then ({@code FC-135} beside {@code 无源贴片FC-135}),
     * so the money is the only witness of a block that carried the articles of the block above.
     *
     * @param order one order of an answer
     * @return the key two blocks of the same articles share, {@code null} when it has no articles
     */
    public static String moneyKeyOf(Map<String, String> order) {
        if (order == null) {
            return null;
        }
        List<String[]> rows = parseOrderArticleLines(order.get("lines"));
        if (rows.isEmpty()) {
            return null;
        }
        List<String> parts = new ArrayList<>();
        for (String[] row : rows) {
            parts.add(row[2] + "|" + row[3]);
        }
        java.util.Collections.sort(parts);
        return String.join(";", parts) + "|" + order.get("paidTotal") + "|" + order.get("freight");
    }

    /**
     * Says whether two different orders of one answer carry exactly the same articles and money.
     *
     * @param orders the orders of one answer
     * @return {@code true} when one order looks copied from another
     */
    public static boolean repeatsAnotherOrder(List<Map<String, String>> orders) {        Set<String> seen = new LinkedHashSet<>();
        for (Map<String, String> order : orders) {
            String key = orderContentKey(order);
            if (key != null && !seen.add(key)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Tells whether the answer repeats the answer format instead of the orders. A model that starts
     * explaining the format usually read the picture badly, so the caller sends it once more.
     *
     * <p>Only the answer itself is looked at, not the reasoning: a thinking model almost always quotes
     * the format while it thinks, and checking the whole reply would retry every single call.
     *
     * @param reply the answer text
     * @return {@code true} when the placeholder row appears in the answer
     */
    public static boolean echoesTemplate(String reply) {
        String text = orderAnswerText(reply);
        if (text == null) {
            return false;
        }
        return text.contains("code,name,quantity,amount") || text.contains("code\\tname\\tquantity\\tamount")
                || text.contains("code\\tname\\tquantity");
    }

    /**
     * How Taobao prints the pack size of a resistor or a capacitor: {@code 一件100只}, {@code （50只）},
     * {@code 100只装}, {@code 1件=100个}.
     */
    private static final java.util.regex.Pattern PACK_SIZE = java.util.regex.Pattern.compile(
            "一件\\s*[0-9]+\\s*[只个]|[（(]\\s*[0-9]+\\s*[只个]\\s*[）)]"
                    + "|[0-9]+\\s*只装|1\\s*件\\s*[=＝]\\s*[0-9]+");

    /**
     * The {@code lines} text of every order in the answer, in the order the orders were read.
     *
     * @param orders the order maps built from one answer
     * @return one entry per order, the tab separated article rows
     */
    public static List<String> linesOfOrders(List<Map<String, String>> orders) {
        List<String> lines = new ArrayList<>();
        if (orders == null) {
            return lines;
        }
        for (Map<String, String> order : orders) {
            lines.add(order == null ? null : order.get("lines"));
        }
        return lines;
    }

    /**
     * Tells whether the answer counted packs instead of pieces: the picture prints a pack size
     * ({@code 一件100只} and the like) yet every article came back as exactly one piece. That is the
     * mistake a person notices last, because the money still adds up, so the caller sends the picture
     * once more with the short request instead of showing a receipt with 1 piece of every part.
     *
     * @param reply the answer text, which carries the pack size when the model read it
     * @param linesByOrder the article rows of the orders that were read
     * @return {@code true} when a pack size was printed and no line counted more than one piece
     */
    public static boolean ignoredThePackSize(String reply, List<String> linesByOrder) {
        if (reply == null || !PACK_SIZE.matcher(reply).find()) {
            return false;
        }
        boolean sawLine = false;
        for (String lines : linesByOrder) {
            for (String[] row : parseOrderArticleLines(lines)) {
                sawLine = true;
                if (Math.abs(toDouble(row[2], 0.0) - 1.0) > PRACTICALLY_ZERO) {
                    return false;
                }
            }
        }
        return sawLine;
    }

    /**
     * Cuts the objects out of a JSON text, starting at the given offset. Braces inside strings do not
     * count, so a product name with {@code {}} in it does not break the split.
     *
     * @param text the JSON text
     * @param from the offset to start at
     * @return the text of every object, in order
     */
    public static List<String> objectTexts(String text, int from) {
        List<String> objects = new ArrayList<>();
        int i = Math.max(0, from);
        while (i < text.length()) {
            if (text.charAt(i) == '{') {
                int end = matchingBrace(text, i);
                if (end > i) {
                    objects.add(text.substring(i, end + 1));
                    i = end + 1;
                    continue;
                }
            }
            i++;
        }
        return objects;
    }

    private static int matchingBrace(String text, int open) {
        return matchingCloser(text, open, '{', '}');
    }

    /**
     * Offset of the square bracket that closes the one at {@code open}. Brackets inside strings do not
     * count, so a product name with {@code []} in it does not end the array early.
     *
     * @param text the JSON text
     * @param open the offset of the opening bracket
     * @return the offset of the closing bracket, {@code -1} when it is not closed
     */
    private static int matchingBracket(String text, int open) {
        return matchingCloser(text, open, '[', ']');
    }

    /**
     * Offset of the character that closes the bracket at {@code open}.
     *
     * @param text the JSON text
     * @param open the offset of the opening bracket
     * @param opening the opening character
     * @param closing the closing character
     * @return the offset of the closing bracket, {@code -1} when it is not closed
     */
    private static int matchingCloser(String text, int open, char opening, char closing) {
        int depth = 0;
        boolean inString = false;
        for (int i = open; i < text.length(); i++) {
            char c = text.charAt(i);
            if (inString) {
                if (c == '\\') {
                    i++;
                } else if (c == '"') {
                    inString = false;
                }
                continue;
            }
            if (c == '"') {
                inString = true;
            } else if (c == opening) {
                depth++;
            } else if (c == closing) {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
        }
        return -1;
    }

    /**
     * Says whether a recognised shop order was refunded. Such an order never stayed in the shop, so
     * booking it into stock would invent material that is not on the shelf.
     *
     * @param fields the recognised fields of {@link #orderFieldsFrom(String)}
     * @return {@code true} when the screenshot said 退款成功 / 已退款 / 退款中 / 退货退款
     */
    public static boolean isRefunded(Map<String, String> fields) {
        return fields != null && "Y".equalsIgnoreCase(toText(fields.get("refunded")));
    }

    /**
     * Says whether a recognised shop order was closed without being paid (交易关闭 / 已取消).
     *
     * <p>The shop prints 交易关闭 right where a finished order prints 交易成功; the person confirmed
     * it means the deal never went through (usually the money was never paid), so such an order is
     * not booked in either - only a hand typed one is, for the rare parcel that turns up anyway.
     *
     * @param fields the recognised fields of {@link #orderFieldsFrom(String)}
     * @return {@code true} when the screenshot said 交易关闭 / 已取消 / 交易失败
     */
    public static boolean isClosed(Map<String, String> fields) {
        return fields != null && "Y".equalsIgnoreCase(toText(fields.get("closed")));
    }

    /**
     * Says whether a recognised order is to be left out of the stock this time: it was refunded, or
     * the deal never went through.
     *
     * @param fields the recognised fields of {@link #orderFieldsFrom(String)}
     * @return {@code true} when the order must not be booked in
     */
    public static boolean notToBookIn(Map<String, String> fields) {
        return isRefunded(fields) || isClosed(fields);
    }

    /**
     * Checks the numbers of a recognised order against the amount paid: the goods plus the freight
     * must come to the paid total.
     *
     * <p>The freight is only filled in when the answer carries none. A mismatch is reported, never
     * repaired by inventing a freight: when a model reads one article of an order that holds two, the
     * missing money looks exactly like a freight, and "helpfully" turning it into one hides the lost
     * article (14.40 paid, 6.40 of goods, so 8.00 of "freight" that never existed).
     *
     * @param fields the recognised fields, {@code paidTotal}, {@code freight} and {@code lines}
     * @return a sentence to show the person, {@code null} when the numbers agree or cannot be checked
     */
    public static String checkOrderTotals(Map<String, String> fields) {
        Double paid = optionalAmount(fields.get("paidTotal"));
        if (paid == null) {
            return null;
        }
        Double freight = optionalAmount(fields.get("freight"));
        Double discount = optionalAmount(fields.get("discount"));
        List<String[]> rows = parseOrderArticleLines(fields.get("lines"));
        double goods = 0.0;
        for (String[] row : rows) {
            goods += toDouble(row[3], 0.0);
        }
        goods = round2(goods);
        if (goods <= 0.0) {
            // no line could be read: there is nothing to check the freight against
            return null;
        }
        // a page prints its unit prices rounded to the cent (0.095 shows as 0.10), so two pieces of
        // 0.10 come to 1.20 where 1.19 was paid: that is rounding, not a missing article
        double slack = roundingSlack(rows);
        double freightValue = freight == null ? 0.0 : freight.doubleValue();
        if (freight == null) {
            // nothing printed next to 含运费 means there was no freight
            fields.put("freight", "0.00");
        }
        if (discount != null && discount.doubleValue() > 0.0
                && Math.abs(round2(goods + freightValue) - paid.doubleValue()) <= slack) {
            // the articles and the freight already come to what was paid, so a discount the model
            // reported (a struck through price read as one) would only break the balance
            fields.put("discount", "0");
            discount = Double.valueOf(0.0);
        }
        if (discount == null || discount.doubleValue() <= 0.0) {
            double off = round2(goods + freightValue - paid.doubleValue());
            if (off > 0.0 && off < goods && off <= goods * MAX_DISCOUNT_SHARE
                    && off <= dearestRow(rows) && !rowsMissing(fields)) {
                // the shop paid less than the goods and the freight: a red packet, a coupon, 满减. The
                // model answers "discount": "0" more often than it leaves the field out, so both count
                // as "it did not read one" - the identity paid = goods + freight - discount fixes the
                // amount itself, and the page then shows it in its 优惠 column instead of flagging a
                // block whose money is right (Step 162).
                //
                // The three bounds are what keeps a bad read out of the books. A block of five rows
                // read twice over answers 975.00 of goods against 195.65 paid, and the difference
                // (780.35) is smaller than the goods - filling that in would have booked a 780 yuan
                // discount on an order whose rows were copied, and no warning would have said so. A
                // coupon takes a small share of the articles, never more than the dearest of them, and
                // no row may be reported missing (then the difference is an article).
                fields.put("discount", String.format(Locale.ROOT, "%.2f", Double.valueOf(off)));
                discount = Double.valueOf(off);
            }
        }
        double discountValue = discount == null ? 0.0 : discount.doubleValue();
        double expected = round2(goods + freightValue - discountValue);
        if (Math.abs(expected - paid.doubleValue()) <= slack) {
            return null;
        }
        if (expected < paid.doubleValue()) {
            return "实付 " + money(paid) + "，但明细 " + money(Double.valueOf(goods)) + " + 运费 "
                    + money(Double.valueOf(freightValue))
                    + (discountValue > 0.0 ? " − 优惠 " + money(Double.valueOf(discountValue)) : "")
                    + " 只有 " + money(Double.valueOf(expected)) + "（差了 "
                    + money(Double.valueOf(round2(paid.doubleValue() - expected)))
                    + "），可能漏了一件商品，请对着截图核对";
        }
        return "实付 " + money(paid) + "，但明细 " + money(Double.valueOf(goods)) + " + 运费 "
                + money(Double.valueOf(freightValue))
                + (discountValue > 0.0 ? " − 优惠 " + money(Double.valueOf(discountValue)) : "")
                + " 是 " + money(Double.valueOf(expected)) + "，比实付还多，请核对";
    }

    /**
     * @param rows the article rows of one order
     * @return the money of the dearest row, 0 when the order prints none
     */
    private static double dearestRow(List<String[]> rows) {
        double dearest = 0.0;
        for (String[] row : rows) {
            dearest = Math.max(dearest, Math.max(0.0, toDouble(row[3], 0.0)));
        }
        return dearest;
    }

    /**
     * @param rows the article rows of one order
     * @return the few cents a page may lose by printing its unit prices rounded
     */
    private static double roundingSlack(List<String[]> rows) {
        double slack = HALF_A_FEN;
        double goods = 0.0;
        for (String[] row : rows) {
            double pieces = Math.max(1.0, toDouble(row[2], 1.0));
            // a row may be off by the rounding of its own unit price, but a pack of a hundred pieces
            // would leave fifty cents and swallow a missing row: the block of nine resistor rows made
            // up 15.90 paid but only 11.40 read, and 4.50 of it was inside the tolerance (Step 159)
            slack += Math.min(HALF_A_FEN * pieces, MAX_ROW_SLACK);
            goods += Math.max(0.0, toDouble(row[3], 0.0));
        }
        // a block of expensive rows may still be off by a fraction of a percent of its money: the page
        // prints 2.124 as 2.13, and six rows of 210 pieces come to 2564.10 where 2562.84 was paid. A
        // lost or misread row is off by a large share of the block, not by a fraction of a percent
        return round2(Math.max(slack, goods * RELATIVE_SLACK));
    }

    /**
     * Says whether the money of one recognised order does not add up, which is the sign of a missing
     * article or a misread amount. The scan sends the screenshot once more when this is true.
     *
     * @param order the recognised order
     * @return {@code true} when goods plus freight is not the paid total
     */
    public static boolean totalsDisagree(Map<String, String> order) {
        if (order == null) {
            return false;
        }
        Double paid = optionalAmount(order.get("paidTotal"));
        Double freight = optionalAmount(order.get("freight"));
        if (paid == null || freight == null) {
            return false;
        }
        List<String[]> rows = parseOrderArticleLines(order.get("lines"));
        double goods = 0.0;
        for (String[] row : rows) {
            goods += toDouble(row[3], 0.0);
        }
        if (goods <= 0.0) {
            return false;
        }
        Double discount = optionalAmount(order.get("discount"));
        double discountValue = discount == null ? 0.0 : discount.doubleValue();
        double total = round2(goods + freight.doubleValue() - discountValue);
        // too little is a lost article, too much is an article read twice or at the wrong price; a few
        // cents of rounded unit prices are no sign of anything
        return Math.abs(total - paid.doubleValue()) > roundingSlack(rows);
    }

    /** @return the amount with two decimals, for a sentence */
    private static String money(Double value) {
        return value == null ? "" : String.format(Locale.ROOT, "%.2f", value.doubleValue());
    }

    /**
     * Says, in the page's own words, how far the articles of the worst order of one answer are from
     * its paid total. Used as the tail of the question for the second read of a slice: a model that
     * summarised thirteen rows of identical resistors into five answered a short total, and naming the
     * money it lost is what makes it go back over the block (Step 158).
     *
     * @param orders the orders of one answer
     * @return the sentence to append to the question, empty when every order adds up
     */
    public static String moneyGapOf(List<Map<String, String>> orders) {
        if (orders == null) {
            return "";
        }
        String worst = "";
        double worstGap = 0.0;
        for (Map<String, String> order : orders) {
            Double paid = optionalAmount(order.get("paidTotal"));
            if (paid == null || order.get("lines") == null) {
                continue;
            }
            List<String[]> rows = parseOrderArticleLines(order.get("lines"));
            if (rows.isEmpty()) {
                continue;
            }
            Double freight = optionalAmount(order.get("freight"));
            Double discount = optionalAmount(order.get("discount"));
            double total = round2(sumOfRows(rows)
                    + (freight == null ? 0.0 : freight.doubleValue())
                    - (discount == null ? 0.0 : discount.doubleValue()));
            double gap = round2(paid.doubleValue() - total);
            if (Math.abs(gap) <= roundingSlack(rows) || Math.abs(gap) <= Math.abs(worstGap)) {
                continue;
            }
            String owed = money(Double.valueOf(Math.abs(gap)));
            worst = gap > 0.0
                    ? " 有一单只读出 " + money(Double.valueOf(total)) + " 元，实付 " + money(paid)
                            + "，还差 " + owed + " 元的商品没有读出。"
                    : " 有一单读出 " + money(Double.valueOf(total)) + " 元，实付 " + money(paid)
                            + "，多了 " + owed + " 元。";
            worstGap = gap;
        }
        if (worst.isEmpty()) {
            return "";
        }
        return worst + " 请把那一块里打印的每一行都读出来（行数要和块里印的行数一致），每行金额按"
                + "打印的单价和数量算，不要为了让合计对上而改动某一行。";
    }

    /**
     * Tells whether an answer holds a row that cannot be read: the quantity is not a number above
     * zero, or the amount is not an amount. A model that squeezes two articles into one row leaves
     * exactly that behind (an amount of {@code 3.24;402530}), and the money check cannot see it
     * because the unreadable amount counts as zero.
     *
     * @param orders the orders of one answer
     * @return {@code true} when any article row is unusable
     */
    public static boolean anyUnreadable(List<Map<String, String>> orders) {
        if (orders == null) {
            return false;
        }
        for (Map<String, String> order : orders) {
            for (String[] row : parseOrderArticleLines(order == null ? null : order.get("lines"))) {
                if (toDouble(row[2], 0.0) <= 0.0 || optionalAmount(row[3]) == null) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Grades one answer of the picture, so the best of several sends survives: the last send is not
     * always the best one (a third attempt dropped a whole order the first one had read). The number
     * of orders outweighs everything, then the number of article rows, then how many orders agree on
     * the money, and last how many of the rows carry a part number of their own: a block of fifteen
     * capacitor packs answered as fifteen rows of {@code 0805} has the same orders, rows and money as
     * the answer that read each value, so without that last term the good one would be dropped
     * (Step 193).
     *
     * @param orders the orders of that answer
     * @param seen how many orders the model said the picture shows, 0 when it did not say
     * @return the grade, negative when the answer holds no order at all
     */
    public static int answerScore(List<Map<String, String>> orders, int seen) {
        return answerScore(orders, seen, 0);
    }

    /**
     * The same grade, with the picture count of the block held against the answer.
     *
     * <p>The product pictures beside the rows count them, and that count is not the model's opinion:
     * a block answered with fifteen rows where fourteen were read is a row of material missing from
     * the receipt, while the same block answered with fourteen rows that each carry their own value
     * reads better to a grade that only looks at money and codes. Both answers of one block were sent
     * for the fifteen packs of capacitors, and the short one won by two points because it named the
     * articles (Step 196).
     *
     * @param orders the orders of that answer
     * @param seen how many orders the model said the picture shows, 0 when it did not say
     * @param articlesHere how many article rows the picture prints, {@code 0} when unknown
     * @return the grade, negative when the answer holds no order at all
     */
    public static int answerScore(List<Map<String, String>> orders, int seen, int articlesHere) {
        if (orders == null || orders.isEmpty()) {
            return -1;
        }
        int rows = 0;
        int agree = 0;
        int codes = 0;
        for (Map<String, String> order : orders) {
            List<String[]> own = parseOrderArticleLines(order == null ? null : order.get("lines"));
            rows += own.size();
            Set<String> distinct = new LinkedHashSet<>();
            for (String[] row : own) {
                distinct.add(rowKey(row[0]));
            }
            codes += Math.min(distinct.size(), 40);
            boolean readable = order != null && optionalAmount(order.get("paidTotal")) != null
                    && !totalsDisagree(order);
            if (readable && !anyUnreadable(java.util.Collections.singletonList(order))) {
                agree++;
            }
        }
        int score = orders.size() * 1000 + rows * 10 + agree + codes;
        if (seen > 0 && orders.size() == seen) {
            score += 500;
        }
        if (articlesHere > 0 && orders.size() == 1) {
            if (rows == articlesHere) {
                score += 60;
            } else if (rows < articlesHere) {
                score -= 60;
            }
        }
        return score;
    }

    /**
     * Tidies what reading a long page adds on top of the articles: the same row printed twice because
     * two slices overlapped (one of them with the {@code [交易快照]} note behind it), and a summary row
     * that only repeats what was paid. Both would otherwise be booked into stock as extra material.
     *
     * <p>A shop that sells one board in six colours prints six rows that carry the same code, the same
     * quantity and the same money; those are six articles, not one seen twice. Which of the two a set
     * of equal rows is can only be told by the money, so copies are dropped one at a time while the
     * rows still come to more than what was paid, and only a reading that adds up is used. Without a
     * paid total nothing can tell them apart, so the rows are left as they were read - dropping them
     * would silently lose material.
     *
     * @param order the order to clean, its {@code lines} are rewritten
     */
    public static void tidyOrder(Map<String, String> order) {
        if (order == null) {
            return;
        }
        List<String[]> rows = parseOrderArticleLines(order.get("lines"));
        if (rows.isEmpty()) {
            return;
        }
        List<String[]> kept = rows;
        // the overlap shows one printed row twice and the two reads write the code differently
        // (FC-135 beside 无源贴片FC-135…), so what makes a row an article cannot tell them apart: when
        // the block says it prints fewer rows than were read, the extra ones share the quantity and the
        // amount of a row already there and are dropped (Step 134)
        kept = withoutExtraRows(rows, (int) toDouble(order.get("rowCount"), 0.0));
        rows = kept;
        // how many rows the block says it prints: a block that prints more rows than were read is
        // missing an article, and then the money must not be moved onto the rows that were read
        int printed = (int) toDouble(order.get("rowCount"), 0.0);
        Double paid = optionalAmount(order.get("paidTotal"));
        Double freight = optionalAmount(order.get("freight"));
        if (paid != null) {
            double freightValue = freight == null ? 0.0 : freight.doubleValue();
            Double discount = optionalAmount(order.get("discount"));
            double discountValue = discount == null ? 0.0 : discount.doubleValue();
            double paidNet = round2(paid.doubleValue() - discountValue);
            // the paid total already holds the freight: a block printing 实付款 9.99 and 含运费 2.99 is
            // read as articles worth 9.99 beside a freight of 2.99, and the freight is counted twice.
            // This has to happen before any row is dropped as a summary, or the money check "fixes" the
            // block by throwing a real article away (Step 129)
            if (freightValue > 0.0) {
                List<String[]> withoutFreight = goodsWithoutFreight(rows, paidNet, freightValue);
                if (withoutFreight != null) {
                    rows = withoutFreight;
                }
            }
            // what the articles alone should come to (paid = goods + freight - discount)
            double wanted = round2(paid.doubleValue() - freightValue + discountValue);
            kept = withoutRepeatedRows(rows, wanted);
            // the pictures a page prints below its last order (猜你喜欢 / 为你推荐) are printed with
            // prices too, and the model hands them to the order above (Step 131)
            kept = withoutTheTailThatDoesNotFit(kept, wanted);
            // the rows sometimes carry a summary row on top (the subtotal, or the paid total itself),
            // and sometimes the price of one piece instead of the money of the row: the reading that
            // adds up wins, so a row may be dropped and the amounts multiplied by the quantity
            kept = readingThatAddsUp(kept, wanted);
            if (discountValue > 0.0 && !addsUp(kept, wanted)) {
                // the discount the model reported may be the struck through price of one piece (0.09
                // beside 0.10): read the rows once more without it, and keep the reading that adds up
                double plain = round2(paid.doubleValue() - freightValue);
                List<String[]> withoutDiscount = readingThatAddsUp(
                        withoutRepeatedRows(rows, plain), plain);
                if (addsUp(withoutDiscount, plain)) {
                    kept = withoutDiscount;
                    order.put("discount", "0");
                }
            }
            if (!addsUp(kept, wanted)) {
                // the price beside an article is the price of one piece, and the number after x is how
                // many were bought: a block of eight rows paid 127.46 came back as 68.29 of prices
                // (13.68 beside x2, 7.01 beside x4), and the freight was not read either. Multiplying
                // by the quantities leaves 3.96, which is that freight - so the reading is tried
                // against the paid total itself (Step 190)
                List<String[]> byPieces = multipliedByPieces(kept, printed, paid.doubleValue());
                if (byPieces != null) {
                    kept = byPieces;
                    double gap = round2(paid.doubleValue() - sumOfRows(kept));
                    order.put("freight", String.format(Locale.ROOT, "%.2f", Double.valueOf(gap)));
                    if (discountValue > 0.0) {
                        order.put("discount", "0");
                    }
                    order.put("note", "这一块的价签是单价，已按数量乘回去；差的 "
                            + String.format(Locale.ROOT, "%.2f", Double.valueOf(gap))
                            + " 记为运费，请对着截图核对");
                }
            }
            if (!addsUp(kept, wanted)) {
                // a row whose money is the price of one pack: the block pays 14.00, the rows come to
                // 12.00, and one of them is the 2.00 of a pack sold twice (Step 145)
                double plain = round2(paid.doubleValue() - freightValue);
                List<String[]> byPacks = moneyOfOnePack(kept, plain, printed);
                if (byPacks != null) {
                    kept = byPacks;
                    if (discountValue > 0.0) {
                        // a discount the model reported only balanced the books it could not read
                        order.put("discount", "0");
                    }
                    order.put("note", "有一行的金额按「每包单价 × 包数」修正了（模型把一包的价钱当成了整行的钱），"
                            + "请对着截图核对");
                }
            }
            if (!addsUp(kept, wanted)) {
                // a row whose money is the whole 实付款 is that number read as an article: the block of
                // 4 rows was read as 2 rows plus one row of 16.00, which is what was paid (Step 146)
                List<String[]> withoutSummary = withoutTheRowThatIsThePaidTotal(
                        kept, round2(paid.doubleValue() - freightValue));
                if (withoutSummary != null) {
                    kept = withoutSummary;
                    order.put("note", "有一行的金额正好是整单实付（那是实付款被当成了一行明细），已去掉；"
                            + "还缺的明细请对着截图补上");
                }
            }
            // a block that prints one article carries the whole money of that block: 实付款 6.60
            // beside ¥3.30 x2 with （5条）a pack is one article of 10 pieces worth 6.60, and the model
            // reads the price of one pack as the money of the row - or invents a discount of the
            // difference to make the two add up. rowCount says the block prints one row, so nothing is
            // missing from the read: the article cost what was paid for it less the freight (Step 132)
            if (kept.size() == 1 && (int) toDouble(order.get("rowCount"), 0.0) == 1) {
                double money = round2(paid.doubleValue() - freightValue);
                if (money > 0.0 && !addsUp(kept, money)) {
                    String[] row = kept.get(0);
                    kept = new ArrayList<>();
                    kept.add(new String[] {row[0], row[1], row[2],
                        String.format(Locale.ROOT, "%.2f", Double.valueOf(money))});
                    // a discount the model reported only balances the books it could not read; the
                    // money of the one article is the money that was paid for it
                    order.put("discount", "0");
                }
            }
        }
        StringBuilder lines = new StringBuilder();
        for (String[] row : kept) {
            if (lines.length() > 0) {
                lines.append('\n');
            }
            lines.append(row[0]).append('\t').append(row[1]).append('\t').append(row[2])
                    .append('\t').append(row[3]);
        }
        order.put("lines", lines.toString());
    }

    /**
     * Takes the freight out of the article rows when the rows came to the whole paid total: a page
     * prints 实付款 9.99 next to 含运费 2.99, and a model that reads the paid total as the money of
     * the article carries the freight twice. The article then really costs 7.00 and the freight stands
     * beside it, which is exactly what the receipt wants - the part price stays what was paid for the
     * part and the freight stays a cost of the order.
     *
     * <p>Only applied when the rows already come to the paid total and the freight fits inside it;
     * a page that prints the goods and the freight separately is left alone.
     *
     * @param rows the article rows of one order
     * @param paid the paid total without the discount
     * @param freight the freight the page printed, above zero
     * @return the rows with the freight taken out, {@code null} when that reading does not fit
     */
    private static List<String[]> goodsWithoutFreight(List<String[]> rows, double paid, double freight) {
        if (freight <= 0.0 || rows.isEmpty()) {
            return null;
        }
        double goods = round2(sumOfRows(rows));
        // only the shape "the rows are the paid total" is corrected: 9.99 rows and 9.99 paid
        if (Math.abs(goods - paid) > MONEY_TOLERANCE) {
            return null;
        }
        double target = round2(paid - freight);
        if (target <= 0.0) {
            return null;
        }
        List<String[]> out = new ArrayList<>(rows.size());
        if (rows.size() == 1) {
            String[] row = rows.get(0);
            out.add(new String[] {row[0], row[1], row[2],
                String.format(Locale.ROOT, "%.2f", Double.valueOf(target))});
            return out;
        }
        // several rows: keep their share of the money, and put the rounding on the largest one
        double factor = target / goods;
        int biggest = 0;
        double biggestAmount = -1.0;
        double sum = 0.0;
        for (int i = 0; i < rows.size(); i++) {
            String[] row = rows.get(i);
            double amount = round2(toDouble(row[3], 0.0) * factor);
            if (amount < 0.0) {
                amount = 0.0;
            }
            if (amount > biggestAmount) {
                biggestAmount = amount;
                biggest = i;
            }
            sum += amount;
            out.add(new String[] {row[0], row[1], row[2],
                String.format(Locale.ROOT, "%.2f", Double.valueOf(amount))});
        }
        double drift = round2(target - sum);
        if (Math.abs(drift) > 0.0) {
            String[] row = out.get(biggest);
            double amount = round2(toDouble(row[3], 0.0) + drift);
            out.set(biggest, new String[] {row[0], row[1], row[2],
                String.format(Locale.ROOT, "%.2f", Double.valueOf(amount))});
        }
        return out;
    }

    /**
     * Drops the copies of a row that two overlapping slices both read, and only those: a copy is
     * removed while the rows add up to more than the shop charged. A page of six identical boards
     * (6 x 6.88 = 41.28 paid) keeps all six while the eight copies two slices made of six are cut
     * back to six; when the copies alone do not explain the difference the reading closest to what
     * was paid is handed on, and the last row of a bad read (a slice that invented an article) is
     * dropped afterwards by {@link #readingThatAddsUp}.
     *
     * @param rows the article rows of one order, as read
     * @param wanted what the articles alone should come to
     * @return the rows with the repeated copies removed
     */
    private static List<String[]> withoutRepeatedRows(List<String[]> rows, double wanted) {
        List<String[]> best = rows;
        double bestGap = Math.abs(round2(sumOfRows(rows)) - wanted);
        List<String[]> current = new ArrayList<>(rows);
        while (round2(sumOfRows(current)) > round2(wanted + MONEY_TOLERANCE)) {
            String key = mostRepeatedKey(current);
            if (key == null) {
                break;
            }
            for (int i = current.size() - 1; i >= 0; i--) {
                if (key.equals(rowIdentity(current.get(i)))) {
                    current.remove(i);
                    break;
                }
            }
            double gap = Math.abs(round2(sumOfRows(current)) - wanted);
            if (gap < bestGap) {
                bestGap = gap;
                best = new ArrayList<>(current);
            }
        }
        return best;
    }

    /**
     * Leaves out what a page prints under its last order: the product pictures of 猜你喜欢 / 为你推荐
     * (and the like) sit right below the order block and carry prices of their own, so the model reads
     * them as further articles of that order. They also cost nothing when the page only shows them as
     * pictures, which is the other half of the rule below.
     *
     * <p>Only a reading that leaves rows out and then comes to exactly what was paid is used; a block
     * that adds up as it is keeps every row (the six colours of one board are six articles).
     *
     * @param rows the article rows of one order
     * @param wanted what the articles alone should come to
     * @return the rows without the tail that is not part of the order
     */
    private static List<String[]> withoutTheTailThatDoesNotFit(List<String[]> rows, double wanted) {
        if (rows.size() < 2 || wanted <= 0.0) {
            return rows;
        }
        // a picture below the list carries no money of its own: a row without an amount at the end of
        // a block that did cost money is not an article of that block
        List<String[]> kept = new ArrayList<>(rows);
        while (kept.size() > 1
                && Math.abs(toDouble(kept.get(kept.size() - 1)[3], -1.0)) < HALF_A_FEN) {
            kept.remove(kept.size() - 1);
        }
        if (addsUp(kept, wanted)) {
            return kept;
        }
        // the prices of those pictures are printed too, so the tail only shows itself in the money: a
        // block that comes to more than was paid, but to exactly the paid total once its last rows are
        // left out, is read without them
        for (int drop = 1; drop < rows.size(); drop++) {
            List<String[]> head = new ArrayList<>(rows.subList(0, rows.size() - drop));
            if (addsUp(head, wanted)) {
                return head;
            }
        }
        return rows;
    }

    /**
     * Drops the rows the overlap of two slices read twice when the block says how many rows it prints:
     * the two reads often write the code of the row differently ({@code FC-135} and {@code 无源贴片FC-135…}),
     * so two copies of one printed row look like two articles. A row is a copy when a row already kept
     * has the same quantity and the same amount.
     *
     * @param rows the article rows of one order, as read
     * @param printed how many rows the block says it prints, 0 when it did not say
     * @return the rows cut back to the number the block prints, or the rows as they came when that
     *     cannot be told apart
     */
    private static List<String[]> withoutExtraRows(List<String[]> rows, int printed) {
        if (printed <= 0 || rows.size() <= printed) {
            return rows;
        }
        List<String[]> out = new ArrayList<>();
        for (String[] row : rows) {
            if (out.size() >= printed) {
                break;
            }
            boolean already = false;
            for (String[] seen : out) {
                if (seen[2].equals(row[2]) && seen[3].equals(row[3])) {
                    already = true;
                    break;
                }
            }
            if (!already) {
                out.add(row);
            }
        }
        return out.size() >= printed ? out : rows;
    }

    /**
     * @param rows the article rows of one order
     * @return what the amounts of the rows come to
     */
    private static double sumOfRows(List<String[]> rows) {
        double sum = 0.0;
        for (String[] row : rows) {
            sum += toDouble(row[3], 0.0);
        }
        return sum;
    }

    /**
     * Drops the row that carries the whole paid total of the block: a page prints that number once, in
     * the header, so a row of exactly that money is the paid total read as an article (a block of four
     * rows at 4.00 each came back as two rows plus one row of 16.00, Step 146).
     *
     * <p>Only a block of three rows or more is corrected, and only when one row says that money: two
     * rows of one block are two articles, and one of them may cost what the block cost.
     *
     * @param rows the article rows of one order
     * @param paid the paid total of the block without the freight
     * @return the rows without that one, {@code null} when no single row carries it
     */
    private static List<String[]> withoutTheRowThatIsThePaidTotal(List<String[]> rows, double paid) {
        if (rows.size() < 3 || paid <= 0.0) {
            return null;
        }
        int at = -1;
        for (int i = 0; i < rows.size(); i++) {
            if (Math.abs(round2(toDouble(rows.get(i)[3], 0.0)) - paid) <= MONEY_TOLERANCE) {
                if (at >= 0) {
                    // two rows claim the whole money: nothing says which one is the summary
                    return null;
                }
                at = i;
            }
        }
        if (at < 0) {
            return null;
        }
        List<String[]> out = new ArrayList<>(rows);
        out.remove(at);
        return out;
    }

    /**
     * Rebuilds every row's money as the printed price times the pieces that were bought, when the rows
     * really carry prices and the gap they leave can only be the freight.
     *
     * <p>A page prints {@code ¥13.68} beside {@code x2}, so that row is 27.36. A model that keeps the
     * price as the money of the row leaves a block of eight rows at 68.29 of the 127.46 that was paid,
     * and the freight of 4.00 is not read either, so no reading of the rows alone can add up. The gap
     * the pieces leave is then taken as the freight, and only a small gap is taken for it: a block whose
     * amounts already are the money of its rows overshoots the paid total as soon as they are
     * multiplied, and a block that prints more rows than were read is missing an article (Step 190).
     *
     * @param rows the article rows of one order
     * @param printed how many rows the block says it prints, 0 when it did not say
     * @param paid what was paid for the order, freight included
     * @return the rows with the money of the pieces, {@code null} when the rows are not prices
     */
    private static List<String[]> multipliedByPieces(List<String[]> rows, int printed, double paid) {
        if (rows.size() < 2 || paid <= 0.0 || (printed > 0 && printed > rows.size())) {
            return null;
        }
        // the rows as they were read have to come well short: a block that is nearly there holds the
        // money of its rows, not the price of one piece
        if (sumOfRows(rows) >= paid * ROW_MONEY_SHORTFALL) {
            return null;
        }
        int pieces = 0;
        double sum = 0.0;
        List<String[]> scaled = new ArrayList<>();
        for (String[] row : rows) {
            double qty = toDouble(row[2], 0.0);
            double amount = toDouble(row[3], 0.0);
            if (qty > 1.0) {
                pieces++;
                amount = round2(amount * qty);
            }
            sum += amount;
            scaled.add(new String[] {row[0], row[1], row[2],
                String.format(Locale.ROOT, "%.2f", Double.valueOf(round2(amount)))});
        }
        // most rows have to say how many pieces were bought, or there is no quantity to go by
        if (pieces * 2 < rows.size()) {
            return null;
        }
        double gap = round2(paid - round2(sum));
        if (gap < -MONEY_TOLERANCE) {
            // the pieces would cost more than the whole order: these were the money of the rows
            return null;
        }
        // what is left is the freight, and freight is small beside what was paid
        if (gap > Math.max(FREIGHT_FLOOR, paid * FREIGHT_SHARE)) {
            return null;
        }
        return scaled;
    }

    /**
     * Reads the money of a row that was given as the price of one pack instead of the money of the
     * whole row: a page prints {@code 0603叠层高频电感 … ¥2.00 x2} with {@code （50个）} in the grey
     * spec line, so the row is 100 pieces for 4.00 (two packs at 2.00), while the model takes the 2.00
     * of one pack as the money of that row. The block paid 14.00 and the rows come to 12.00, so
     * multiplying that one row by 2 is the only reading that comes to what was paid (Step 145).
     *
     * <p>Only a gap that exactly one row explains is corrected: two rows of the same amount could
     * both be the pack price, and then nothing says which of them the page sold in packs.
     *
     * @param rows the article rows of one order
     * @param wanted what the articles alone should come to
     * @param printed how many rows the block says it prints, 0 when it did not say
     * @return the rows with that one amount multiplied, {@code null} when no single row explains it
     */
    private static List<String[]> moneyOfOnePack(List<String[]> rows, double wanted, int printed) {
        // a block of one row is the case of Step 132 (the whole money belongs to that row), and a block
        // that prints more rows than were read is missing an article: neither is a pack price
        if (rows.size() < 2 || wanted <= 0.0 || printed > rows.size()) {
            return null;
        }
        double gap = round2(wanted - sumOfRows(rows));
        if (gap <= MONEY_TOLERANCE) {
            return null;
        }
        List<String[]> found = null;
        int fitting = 0;
        for (int i = 0; i < rows.size(); i++) {
            double amount = round2(toDouble(rows.get(i)[3], 0.0));
            if (amount <= 0.0) {
                continue;
            }
            // the page never sold more packs of a row than the pieces it prints for it, so a row read
            // as x4 cannot be eleven packs of the price beside it: that product only closed the gap
            // with money nobody paid (¥7.01 with x4 became 77.11, Step 189). A row of one piece cannot
            // be several packs either - a page that sold two packs prints two pieces at the least, and
            // the row of one LCD that came back at 6.00 must not turn into 12.00 to close a gap
            // (Step 190)
            double pieces = toDouble(rows.get(i)[2], 0.0);
            int ceiling = (int) Math.min(MAX_PACKS, Math.max(1.0, Math.floor(pieces)));
            for (int packs = 2; packs <= ceiling; packs++) {
                if (Math.abs(round2(amount * (packs - 1)) - gap) > MONEY_TOLERANCE) {
                    continue;
                }
                List<String[]> fixed = new ArrayList<>(rows);
                String[] row = rows.get(i);
                fixed.set(i, new String[] {row[0], row[1], row[2],
                    String.format(Locale.ROOT, "%.2f", Double.valueOf(round2(amount * packs)))});
                found = fixed;
                fitting++;
                break;
            }
        }
        return fitting == 1 ? found : null;
    }

    /**
     * Whether the amounts of the rows come to what they should. A page rounds its unit prices to the
     * cent, so a few cents are allowed.
     *
     * @param rows the article rows
     * @param wanted what the articles alone should come to
     * @return {@code true} when the rows add up
     */
    private static boolean addsUp(List<String[]> rows, double wanted) {
        return Math.abs(round2(sumOfRows(rows)) - wanted) <= MONEY_TOLERANCE;
    }

    /**
     * @param rows the article rows
     * @return the identity of a row that is printed more than once, {@code null} when none is
     */
    private static String mostRepeatedKey(List<String[]> rows) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (String[] row : rows) {
            String key = rowIdentity(row);
            Integer seen = counts.get(key);
            counts.put(key, Integer.valueOf(seen == null ? 1 : seen.intValue() + 1));
        }
        String best = null;
        int most = 1;
        for (Map.Entry<String, Integer> entry : counts.entrySet()) {
            if (entry.getValue().intValue() > most) {
                most = entry.getValue().intValue();
                best = entry.getKey();
            }
        }
        return best;
    }

    /**
     * Reads the header line of every order block out of an answer to the "numbers only" question: the
     * order number, the date it was placed and the shop, one element per block, top to bottom.
     *
     * @param reply the answer text
     * @return one map per block ({@code orderNo}, {@code orderDate}, {@code shop}), empty when nothing
     *         could be read
     */
    public static List<Map<String, String>> headerListFrom(String reply) {
        List<Map<String, String>> headers = new ArrayList<>();
        String text = orderAnswerText(reply);
        if (text == null) {
            return headers;
        }
        int array = text.indexOf('[');
        int blocks = text.indexOf("\"blocks\"");
        int orders = text.indexOf("\"orders\"");
        int key = blocks >= 0 ? blocks : orders;
        int from = key >= 0 && array > key ? array : text.indexOf('{');
        if (from < 0) {
            return headers;
        }
        for (String object : objectTexts(text, from)) {
            String number = jsonStringField(object, "orderNo");
            String date = jsonStringField(object, "orderDate");
            String shop = jsonStringField(object, "shop");
            if ((number == null || number.trim().isEmpty()) && date == null && shop == null) {
                continue;
            }
            Map<String, String> header = new LinkedHashMap<>();
            header.put("orderNo", number == null ? "" : number.trim());
            header.put("orderDate", date);
            header.put("shop", shop);
            headers.add(header);
        }
        return headers;
    }

    /**
     * Keeps a second reading of the order numbers next to the first one, and fills in a date or a shop
     * the first read left empty.
     *
     * <p>The second read comes from a strip cropped to the numbered part of the page, which the vision
     * service scales less, and it does <em>not</em> win by being bigger: fifty runs of the audited page
     * showed three where the strip read a digit short or long and the whole page read the number right
     * (2026-10-27). So both readings are kept and {@link #settleNumbers} decides, with the length the
     * page prints at as the evidence.
     *
     * @param orders the orders of one picture
     * @param headers what the strip read answered, one element per block
     * @return how many orders carry a second reading that differs from the first
     */
    public static int noteTheSecondRead(List<Map<String, String>> orders,
            List<Map<String, String>> headers) {
        if (orders == null || headers == null || headers.isEmpty() || headers.size() != orders.size()) {
            // a block cut at the top of the picture has no number of its own, and then the two lists are
            // not the same blocks and nothing may be lined up
            return 0;
        }
        for (Map<String, String> order : orders) {
            if (order == null || order.get("orderNo") == null || order.get("orderNo").trim().isEmpty()) {
                return 0;
            }
        }
        int differing = 0;
        for (int i = 0; i < orders.size(); i++) {
            Map<String, String> order = orders.get(i);
            Map<String, String> header = headers.get(i);
            if (header == null) {
                continue;
            }
            String seen = order.get("orderNo").trim();
            String clearer = header.get("orderNo") == null ? "" : header.get("orderNo").trim();
            if (!clearer.isEmpty() && !clearer.equals(seen)) {
                order.put("headerOrderNo", clearer);
                differing++;
            }
            fillTheBlank(order, "orderDate", header.get("orderDate"));
            fillTheBlank(order, "shop", header.get("shop"));
        }
        return differing;
    }

    /**
     * Settles the order number of every order that carries a second reading, using the length the page
     * prints its numbers at: a number read with a digit too few or too many is the common way a
     * nineteen digit run goes wrong, and then the reading with the page's own length wins. Two readings
     * of the same length cannot be told apart by the picture, so the first one stays and the order says
     * both were seen - the number is the key the import de-duplicates on, and a person has to look.
     *
     * @param orders the orders of the whole picture, after the reads of one page were joined
     * @return how many numbers were replaced by the second reading
     */
    public static int settleNumbers(List<Map<String, String>> orders) {
        if (orders == null || orders.isEmpty()) {
            return 0;
        }
        Map<Integer, Integer> byLength = new HashMap<>();
        int samples = 0;
        for (Map<String, String> order : orders) {
            String number = order.get("orderNo");
            if (number == null || number.trim().isEmpty()) {
                continue;
            }
            Integer length = Integer.valueOf(number.trim().length());
            Integer there = byLength.get(length);
            byLength.put(length, Integer.valueOf(there == null ? 1 : there.intValue() + 1));
            samples++;
        }
        int usual = 0;
        int most = 0;
        for (Map.Entry<Integer, Integer> entry : byLength.entrySet()) {
            if (entry.getValue().intValue() > most) {
                most = entry.getValue().intValue();
                usual = entry.getKey().intValue();
            }
        }
        if (samples < 3 || most < samples / 2) {
            usual = 0;
        }
        int replaced = 0;
        for (Map<String, String> order : orders) {
            String second = order.remove("headerOrderNo");
            if (second == null || second.trim().isEmpty()) {
                continue;
            }
            String first = order.get("orderNo") == null ? "" : order.get("orderNo").trim();
            String clearer = second.trim();
            if (clearer.equals(first)) {
                continue;
            }
            boolean firstFits = usual <= 0 || first.length() == usual;
            boolean secondFits = usual > 0 && clearer.length() == usual;
            String kept = first;
            if (!firstFits && secondFits) {
                kept = clearer;
                order.put("orderNo", clearer);
                replaced++;
            }
            // a number whose length is not the page's own was decided by that length alone, and the
            // person need not look at it; both of one length (or no length to go by) stay ambiguous
            if (usual > 0 && firstFits != secondFits) {
                continue;
            }
            String why = "单号两遍读数不一致（" + first + " / " + clearer + "），已保留 " + kept
                    + "，请对着截图核对";
            String already = order.get("note");
            order.put("note", already == null || already.trim().isEmpty() ? why : already + "；" + why);
        }
        return replaced;
    }

    /**
     * @param order the order to fill in
     * @param field the field of it
     * @param value what the header line read gave, used only when the order has nothing there
     */
    private static void fillTheBlank(Map<String, String> order, String field, String value) {
        String there = order.get(field);
        if (value != null && !value.trim().isEmpty() && (there == null || there.trim().isEmpty())) {
            order.put(field, value.trim());
        }
    }

    /**
     * Folds two reads of one order number back into one order while the page shows fewer blocks than
     * were read.
     *
     * <p>The join keeps two blocks that were read with the same number apart (Step 134/135/141): a
     * page never prints one number on two blocks, so dropping one of them would lose an order. But the
     * blocks of the picture can be counted, and when there are fewer blocks than orders the split was
     * wrong - the two rows are one block read twice, once with the money of the block and once with
     * something else (a price, a subtotal). The pair whose two monies are closest is folded first, the
     * articles are joined without duplicates, and the paid total kept is the one the rows come to; the
     * money checks then run over the folded order as over any other.
     *
     * <p>Only an exact order number folds: a number read with a digit missing is a different question
     * (see {@link #numbersOfTheSameOrder}) and merging on it could swallow a real order.
     *
     * @param orders the orders of the page
     * @param blocks how many order blocks the picture shows, {@code 0} when the page does not say
     * @return the same orders with the extra reads folded, when the count says they cannot be blocks
     */
    public static List<Map<String, String>> foldExtraReads(List<Map<String, String>> orders, int blocks) {
        if (orders == null) {
            return new ArrayList<>();
        }
        if (blocks <= 0 || orders.size() <= blocks) {
            return orders;
        }
        List<Map<String, String>> out = new ArrayList<>(orders);
        while (out.size() > blocks) {
            int[] pair = closestSameNumber(out);
            if (pair == null) {
                break;
            }
            Map<String, String> joined = joinReads(out.get(pair[0]), out.get(pair[1]));
            out.set(pair[0], joined);
            out.remove(pair[1]);
        }
        return out;
    }

    /**
     * One order out of the whole-block read and the two halves it was read in again.
     *
     * <p>A block of fifteen packs of capacitors came back with fourteen rows, and naming the count did
     * not always make the model go back over it. The block is then read in two halves - the top one and
     * one that carries the header of the block stitched on top of the lower rows - and the rows of the
     * two are joined here. The block is <em>one</em> order whatever the halves wrote in their own
     * header, so the number of orders must not grow: the head (shop, date, paid total, freight) of the
     * whole-block read stands, and only its rows are replaced.
     *
     * <p>The row count the page printed is dropped: each half counts the rows of its own picture, which
     * is not the count of the block. Which reading stands is decided by the money - the halves when
     * their rows come to what was paid, otherwise whichever of the two is closer (the money checks of
     * {@link #tidyOrder} then get another go at it).
     *
     * @param whole the answer to the whole block, {@code null} when it read nothing
     * @param upper the answer to the top half, {@code null} when it read nothing
     * @param lower the answer to the lower half, {@code null} when it read nothing
     * @return the order the block is, {@code null} when no reading has anything in it
     */
    public static Map<String, String> joinHalves(Map<String, String> whole, Map<String, String> upper,
            Map<String, String> lower) {
        Map<String, String> head = whole != null ? whole : upper != null ? upper : lower;
        if (head == null) {
            return null;
        }
        List<String[]> rows = new ArrayList<>();
        java.util.Set<String> seen = new java.util.LinkedHashSet<>();
        for (Map<String, String> read : new Map[] {upper, lower}) {
            if (read == null) {
                continue;
            }
            for (String[] row : parseOrderArticleLines(read.get("lines"))) {
                if (seen.add(rowIdentity(row))) {
                    rows.add(row);
                }
            }
        }
        List<String[]> wholeRows = whole == null
                ? new ArrayList<String[]>() : parseOrderArticleLines(whole.get("lines"));
        if (rows.isEmpty()) {
            // a half that read nothing says nothing: the whole-block read stands as it is
            rows = wholeRows;
        }
        Double paid = optionalAmount(head.get("paidTotal"));
        if (paid != null && !wholeRows.isEmpty() && !rows.isEmpty()) {
            double wanted = round2(paid.doubleValue() - toDouble(head.get("freight"), 0.0)
                    + toDouble(head.get("discount"), 0.0));
            boolean halvesAddUp = addsUp(rows, wanted);
            boolean wholeAddsUp = addsUp(wholeRows, wanted);
            if (!halvesAddUp && (wholeAddsUp || Math.abs(round2(sumOfRows(wholeRows)) - wanted)
                    < Math.abs(round2(sumOfRows(rows)) - wanted))) {
                rows = wholeRows;
            }
        }
        Map<String, String> joined = new LinkedHashMap<>(head);
        joined.put("lines", rowsToText(rows));
        joined.remove("rowCount");
        return joined;
    }

    /**
     * The number of the order a read belongs to: the join keeps a second read of one number under
     * {@code <number> #2}, and folding the two back together has to look behind that mark.
     *
     * @param number an order number, possibly with the mark of a second read
     * @return the number the page prints
     */
    private static String baseNumber(String number) {
        if (number == null) {
            return "";
        }
        String text = number.trim();
        int mark = text.lastIndexOf(" #");
        if (mark > 0) {
            String tail = text.substring(mark + 2);
            boolean digits = !tail.isEmpty();
            for (int i = 0; i < tail.length() && digits; i++) {
                digits = Character.isDigit(tail.charAt(i));
            }
            if (digits) {
                return text.substring(0, mark).trim();
            }
        }
        return text;
    }

    /**
     * @param orders the orders of one page
     * @return the two positions of the pair with the same order number whose money is closest, or
     *         {@code null} when no number appears twice
     */
    private static int[] closestSameNumber(List<Map<String, String>> orders) {
        int[] best = null;
        double closest = Double.MAX_VALUE;
        for (int i = 0; i < orders.size(); i++) {
            String number = orders.get(i).get("orderNo");
            if (number == null || number.trim().isEmpty()) {
                continue;
            }
            for (int j = i + 1; j < orders.size(); j++) {
                if (!baseNumber(number).equals(baseNumber(orders.get(j).get("orderNo")))) {
                    continue;
                }
                Double one = optionalAmount(orders.get(i).get("paidTotal"));
                Double other = optionalAmount(orders.get(j).get("paidTotal"));
                double apart = one == null || other == null
                        ? 0.0 : Math.abs(one.doubleValue() - other.doubleValue());
                if (best == null || apart < closest) {
                    best = new int[] {i, j};
                    closest = apart;
                }
            }
        }
        return best;
    }

    /**
     * One order out of two reads of it: the articles are joined without a duplicate row, and the money
     * kept is the read the articles come to.
     *
     * @param first the read kept in place
     * @param second the read folded into it
     * @return the folded order
     */
    private static Map<String, String> joinReads(Map<String, String> first, Map<String, String> second) {
        List<String[]> rows = parseOrderArticleLines(first.get("lines"));
        java.util.Set<String> seen = new java.util.LinkedHashSet<>();
        for (String[] row : rows) {
            seen.add(rowIdentity(row));
        }
        for (String[] row : parseOrderArticleLines(second.get("lines"))) {
            if (seen.add(rowIdentity(row))) {
                rows.add(row);
            }
        }
        double sum = round2(sumOfRows(rows));
        Double paidFirst = optionalAmount(first.get("paidTotal"));
        Double paidSecond = optionalAmount(second.get("paidTotal"));
        Map<String, String> money = first;
        if (paidFirst == null) {
            money = second;
        } else if (paidSecond != null
                && Math.abs(sum - paidSecond.doubleValue()) < Math.abs(sum - paidFirst.doubleValue())) {
            money = second;
        }
        Map<String, String> joined = new LinkedHashMap<>(first);
        joined.put("lines", rowsToText(rows));
        joined.put("rowCount", String.valueOf(rows.size()));
        if (money.get("paidTotal") != null) {
            joined.put("paidTotal", money.get("paidTotal"));
        }
        if (money.get("freight") != null) {
            joined.put("freight", money.get("freight"));
        }
        if (money.get("discount") != null) {
            joined.put("discount", money.get("discount"));
        }
        if (second.get("shop") != null && String.valueOf(joined.get("shop")).trim().isEmpty()) {
            joined.put("shop", second.get("shop"));
        }
        String why = "单号相同的两行已合成一张（图上的块数比读出的单数少），请对着截图核对";
        String already = first.get("note");
        joined.put("note", already == null || already.trim().isEmpty() ? why : already + "；" + why);
        return joined;
    }

    /**
     * @param rows the article rows of one order
     * @return them as the one row per line text the order carries
     */
    private static String rowsToText(List<String[]> rows) {
        StringBuilder text = new StringBuilder();
        for (String[] row : rows) {
            if (text.length() > 0) {
                text.append('\n');
            }
            text.append(row[0]).append('\t').append(row[1]).append('\t').append(row[2])
                    .append('\t').append(row[3]);
        }
        return text.toString();
    }

    /**
     * @param row one article row (code, name, quantity, amount)
     * @return what two reads of the same printed row share
     */
    private static String rowIdentity(String[] row) {
        return bareCode(row[0]) + "|" + row[2] + "|" + row[3];
    }

    /**
     * The code of a row without the pack size in brackets a shop prints behind it: one slice reads
     * {@code 黄色（5个）} and the next {@code 黄色}, and they are the same article.
     *
     * @param code the code of one row
     * @return the code without that bracket
     */
    private static String bareCode(String code) {
        return PACK_SUFFIX.matcher(rowKey(code)).replaceFirst("").trim();
    }

    /**
     * The code of an article without the notes a shop prints behind it ({@code [交易快照]} and the
     * like), so two reads of the same article end up with the same key.
     *
     * @param code the code of one row
     * @return the code without those notes, blanks squeezed
     */
    private static String rowKey(String code) {
        return code == null ? "" : BLANKS.matcher(TRADE_NOTE.matcher(code).replaceAll("")).replaceAll(" ").trim();
    }

    /**
     * Picks the reading of the article rows whose money comes to what the shop charged. The rows as
     * they were read are tried first, then without one row (a summary row the model added), then with
     * the amounts multiplied by the quantity (the model gave the price of one piece), and finally both.
     * A page rounds its own unit prices, so a few cents are allowed; when nothing fits the rows are
     * left as they are, and the money check on the page tells the person to look at them.
     *
     * @param rows the article rows of one order
     * @param wanted what the articles alone should come to
     * @return the rows of the reading that adds up, or the rows as they were
     */
    private static List<String[]> readingThatAddsUp(List<String[]> rows, double wanted) {
        // the reading that multiplies by the quantity rebuilds each amount as a printed price times a
        // quantity, so it may be off by half a fen a piece: six rows of 210 pieces of 2.12 are 2564.10
        // where 2562.84 was paid, and that is the page's own rounding, not a lost row (Step 159)
        double asUnitPriceSlack = MONEY_TOLERANCE;
        double pieces = 0.0;
        for (String[] row : rows) {
            pieces += Math.max(1.0, toDouble(row[2], 1.0));
        }
        asUnitPriceSlack = Math.max(asUnitPriceSlack, round2(HALF_A_FEN * pieces) + MONEY_TOLERANCE);
        for (int dropAt = -1; dropAt < rows.size(); dropAt++) {
            for (boolean asUnitPrice : new boolean[] {false, true}) {
                double sum = 0.0;
                for (int i = 0; i < rows.size(); i++) {
                    if (i == dropAt) {
                        continue;
                    }
                    double amount = toDouble(rows.get(i)[3], 0.0);
                    double qty = toDouble(rows.get(i)[2], 0.0);
                    sum += asUnitPrice && qty > 0.0 ? amount * qty : amount;
                }
                double allowed = asUnitPrice ? asUnitPriceSlack : MONEY_TOLERANCE;
                if (Math.abs(round2(sum) - wanted) > allowed) {
                    continue;
                }
                List<String[]> picked = new ArrayList<>();
                for (int i = 0; i < rows.size(); i++) {
                    if (i == dropAt) {
                        continue;
                    }
                    String[] row = rows.get(i);
                    if (asUnitPrice) {
                        double qty = toDouble(row[2], 0.0);
                        double amount = toDouble(row[3], 0.0) * (qty > 0.0 ? qty : 1.0);
                        row = new String[] {row[0], row[1], row[2],
                            String.format(Locale.ROOT, "%.2f", Double.valueOf(round2(amount)))};
                    }
                    picked.add(row);
                }
                return picked;
            }
        }
        return rows;
    }

    /**
     * Joins the orders read from the slices of one long page: the same order number is one order, the
     * article rows of the slices are joined (a row that straddles two slices is seen twice and kept
     * once), and a field the other slice read fills in what this one missed.
     *
     * @param answers what each slice answered, in the order the slices were read
     * @return the orders of the whole page
     */
    public static List<Map<String, String>> mergeOrders(List<List<Map<String, String>>> answers) {
        return mergeOrders(answers, null);
    }

    /**
     * The same join, told how many order blocks the model said each picture shows. A model that splits
     * one block into two objects (two articles, one number) says it saw one block and answers with two;
     * a page never prints one order number on two blocks, so two objects of one picture that carry the
     * same number and the same shop are the two halves of that block and are joined (a page of 23
     * orders came back with 24, Step 143).
     *
     * @param answers what each slice answered, in the order the slices were read
     * @param blocksSaid how many blocks the model said each slice shows, {@code null} when unknown
     * @return the orders of the whole page
     */
    public static List<Map<String, String>> mergeOrders(List<List<Map<String, String>>> answers,
            List<Integer> blocksSaid) {
        Map<String, Map<String, String>> byNumber = new LinkedHashMap<>();
        if (answers == null) {
            return new ArrayList<>();
        }
        // the length the page prints its numbers at, so a read that added or lost a digit does not win
        List<Map<String, String>> all = new ArrayList<>();
        for (List<Map<String, String>> answer : answers) {
            if (answer != null) {
                all.addAll(answer);
            }
        }
        int usual = usualNumberLength(all);
        // which picture each order was read from: the overlap of two slices shows one block twice, but
        // one picture shows a block once, so the same number twice in one answer is two blocks (Step 135)
        Map<Map<String, String>, Integer> sliceOf = new java.util.IdentityHashMap<>();
        for (int slice = 0; slice < answers.size(); slice++) {
            List<Map<String, String>> answer = answers.get(slice);
            if (answer == null) {
                continue;
            }
            for (Map<String, String> order : answer) {
                if (order == null) {
                    continue;
                }
                String number = order.get("orderNo") == null ? "" : order.get("orderNo").trim();
                Map<String, String> kept = byNumber.get(number);
                if (kept != null && onePrintsThePriceOfTheOther(kept, order)) {
                    // the block printed 原装正品 TPS63051RMWR … ¥2.18 x100 with 实付款 ¥217.90, and the
                    // model answered that one row twice: once with the money of the order and once
                    // with the price of a single piece. The price of a piece is not an order of its
                    // own, so the read that carries the block's money stays alone (a page of 23
                    // orders came back with 24, Step 144)
                    if (biggerMoney(order, kept)) {
                        kept.clear();
                        kept.putAll(order);
                    }
                    String already = kept.get("note");
                    String why = "同一块被读成了两张单（另一张把单价当成了实付），多出来的那条已去掉，请对着截图核对";
                    kept.put("note", already == null || already.trim().isEmpty() ? why : already + "；" + why);
                    continue;
                }
                boolean halvesOfOneBlock = kept != null && sameAnswer(kept, slice, sliceOf)
                        && saysFewerBlocksThanItRead(blocksSaid, slice, answer.size())
                        && sameShopOrMissing(kept, order);
                if (kept != null && sameAnswer(kept, slice, sliceOf) && !sameMoneyAndArticles(kept, order)
                        && !lostTheShop(kept, order) && !halvesOfOneBlock
                        && hasMoney(kept) && hasMoney(order) && !samePaidMoney(kept, order)) {
                    // a page never prints one order number on two blocks, and one picture shows each
                    // block once: the two blocks were read with the same number, so keeping them apart
                    // shows both orders. Merging them dropped the second one - a page of 29 orders with
                    // two orders of the same articles and the same money came back with 28 (Step 135)
                    String apart = freeNumber(byNumber, number);
                    Map<String, String> stray = new LinkedHashMap<>(order);
                    stray.put("note", "订单号与另一张单读成了一样（" + number
                            + "）：一块只印一个号，这两行里可能有一行是同一块被拆开的，对不上就删掉多出来的那行");
                    byNumber.put(apart, stray);
                    sliceOf.put(stray, Integer.valueOf(slice));
                    continue;
                }
                if (kept != null && !sameAnswer(kept, slice, sliceOf) && hasMoney(kept) && hasMoney(order)
                        && !sameMoney(kept, order)) {
                    // two slices, but two different block monies: this is not the same block either
                    String apart = freeNumber(byNumber, number);
                    Map<String, String> stray = new LinkedHashMap<>(order);
                    stray.put("note", "订单号与另一张单读成了一样（" + number
                            + "）：一块只印一个号，这两行里可能有一行是同一块被拆开的，对不上就删掉多出来的那行");
                    byNumber.put(apart, stray);
                    sliceOf.put(stray, Integer.valueOf(slice));
                    continue;
                }
                if (kept == null) {
                    // the second read of a block may come back with the number one digit short: the
                    // price of one piece beside the paid total tells it is the same block, whatever
                    // number the two reads wrote (Step 144)
                    Map<String, String> priced = twinPrintingThePriceOfTheOther(byNumber, order);
                    if (priced != null) {
                        if (biggerMoney(order, priced)) {
                            priced.clear();
                            priced.putAll(order);
                        }
                        String already = priced.get("note");
                        String why = "同一块被读成了两张单（另一张把单价当成了实付），多出来的那条已去掉，请对着截图核对";
                        priced.put("note", already == null || already.trim().isEmpty() ? why : already + "；" + why);
                        continue;
                    }
                    String twin = numberOneCharacterOff(byNumber, order);
                    kept = twin == null ? null : byNumber.get(twin);
                    if (kept != null && sameAnswer(kept, slice, sliceOf)
                            && !sameMoneyAndArticles(kept, order)) {
                        // the twin rule joined two blocks of one answer: they are two orders
                        kept = null;
                    }
                }
                if (kept == null) {
                    Map<String, String> fresh = new LinkedHashMap<>(order);
                    byNumber.put(number, fresh);
                    sliceOf.put(fresh, Integer.valueOf(slice));
                    continue;
                }
                kept.put("lines", joinRows(kept.get("lines"), order.get("lines")));
                if (halvesOfOneBlock) {
                    // the block printed one 实付款 and two article rows: the two objects carry that one
                    // amount between them, and the note says where the second row came from
                    addUpTheTwoHalves(kept, order);
                    String already = kept.get("note");
                    String why = "同一订单号被读成了两张单（一块的两段），已并回一张，实付按两笔相加，请对着截图核对";
                    kept.put("note", already == null || already.trim().isEmpty() ? why : already + "；" + why);
                }
                for (String field : new String[] {"platform", "shop", "orderDate", "freight",
                        "paidTotal", "discount", "refunded", "closed"}) {
                    String value = kept.get(field);
                    if (value == null || value.trim().isEmpty()) {
                        kept.put(field, order.get(field));
                    }
                }
                // the page prints one number for the order: keep the read that has the length the page
                // writes, and only then prefer the longer one (a read that added a digit is the longer
                // one as well, Step 134)
                String keptNumber = kept.get("orderNo") == null ? "" : kept.get("orderNo").trim();
                boolean takeNew = number.length() > keptNumber.length();
                if (usual > 0 && !number.equals(keptNumber)) {
                    if (keptNumber.length() == usual) {
                        takeNew = false;
                    } else if (number.length() == usual) {
                        takeNew = true;
                    }
                }
                if (takeNew) {
                    kept.put("orderNo", number);
                }
            }
        }
        return withoutDoubleReads(new ArrayList<>(byNumber.values()), sliceOf);
    }

    /**
     * @param order an order already read
     * @param slice the picture the current read comes from
     * @param sliceOf which picture each order already read comes from
     * @return {@code true} when both reads come from the same picture, which shows a block only once
     */
    private static boolean sameAnswer(Map<String, String> order, int slice,
            Map<Map<String, String>, Integer> sliceOf) {
        Integer seen = sliceOf.get(order);
        return seen != null && seen.intValue() == slice;
    }

    /**
     * Collapses the reads of one order that came back with two very different numbers. A page prints
     * its order numbers at one length (19 digits on Taobao), so a number much shorter than the rest of
     * the page that carries exactly the articles, the shop and the money of another order is that
     * order read twice with the middle digits lost (production read one block as 3141164514099372
     * beside the same block read properly, Step 133).
     *
     * @param orders the orders of the whole page
     * @param sliceOf which picture each order was read from
     * @return the same orders without the shorter read of an order that is already there
     */
    private static List<Map<String, String>> withoutDoubleReads(List<Map<String, String>> orders,
            Map<Map<String, String>, Integer> sliceOf) {
        int usual = usualNumberLength(orders);
        if (usual <= 0) {
            return orders;
        }
        List<Map<String, String>> kept = new ArrayList<>();
        for (Map<String, String> order : orders) {
            String number = order.get("orderNo") == null ? "" : order.get("orderNo").trim();
            if (number.length() > usual - 2) {
                kept.add(order);
                continue;
            }
            Map<String, String> full = null;
            for (Map<String, String> other : kept) {
                // two blocks of one picture are two orders, however alike their articles and money are
                if (!sameAnswer(other, sliceOf.get(order) == null ? -1 : sliceOf.get(order).intValue(),
                        sliceOf) && sameArticles(other, order) && sameShopAndMoney(other, order)) {
                    full = other;
                    break;
                }
            }
            if (full == null) {
                kept.add(order);
                continue;
            }
            // a refund or a closed badge seen by either read is true of the order
            for (String flag : new String[] {"refunded", "closed"}) {
                if ("Y".equalsIgnoreCase(String.valueOf(order.get(flag)))
                        && !"Y".equalsIgnoreCase(String.valueOf(full.get(flag)))) {
                    full.put(flag, "Y");
                }
            }
        }
        return kept;
    }

    /**
     * @param orders the orders of one page
     * @return the length most of their order numbers have, 0 when the page does not agree on one
     */
    private static int usualNumberLength(List<Map<String, String>> orders) {
        Map<Integer, Integer> counts = new LinkedHashMap<>();
        for (Map<String, String> order : orders) {
            String number = order.get("orderNo") == null ? "" : order.get("orderNo").trim();
            if (number.length() < 8) {
                continue;
            }
            Integer length = Integer.valueOf(number.length());
            Integer seen = counts.get(length);
            counts.put(length, Integer.valueOf(seen == null ? 1 : seen.intValue() + 1));
        }
        int usual = 0;
        int most = 0;
        for (Map.Entry<Integer, Integer> entry : counts.entrySet()) {
            if (entry.getValue().intValue() > most) {
                most = entry.getValue().intValue();
                usual = entry.getKey().intValue();
            }
        }
        // three numbers of one length are a habit of the page, two are a coincidence
        return most >= 3 ? usual : 0;
    }

    /**
     * @param first one order of a page
     * @param second another order of the same page
     * @return {@code true} when both print exactly the same articles
     */
    private static boolean sameArticles(Map<String, String> first, Map<String, String> second) {
        List<String[]> rows = parseOrderArticleLines(first.get("lines"));
        List<String[]> otherRows = parseOrderArticleLines(second.get("lines"));
        if (rows.isEmpty() || rows.size() != otherRows.size()) {
            return false;
        }
        List<String> keys = new ArrayList<>();
        List<String> otherKeys = new ArrayList<>();
        for (String[] row : rows) {
            keys.add(rowIdentity(row));
        }
        for (String[] row : otherRows) {
            otherKeys.add(rowIdentity(row));
        }
        java.util.Collections.sort(keys);
        java.util.Collections.sort(otherKeys);
        return keys.equals(otherKeys);
    }

    /**
     * Finds the order already read whose number is the same one read with digits lost: a block that
     * two overlapping slices both show comes back with a digit or three missing now and then
     * (production read 3316453503083081392 once as 331645303083081392 and once as 3316453503081392),
     * and the page would then show one order too many. The shop of the two reads is not asked: the
     * number and the shop are printed on the same strip, so a misread hits either of them, and the
     * money of the block is the witness that both reads are the same block.
     *
     * @param byNumber the orders read so far, by their number
     * @param order the order just read
     * @return the number the same order was filed under, {@code null} when it is a new one
     */
    private static String numberOneCharacterOff(Map<String, Map<String, String>> byNumber,
            Map<String, String> order) {
        String number = order.get("orderNo") == null ? "" : order.get("orderNo").trim();
        if (number.isEmpty()) {
            return null;
        }
        for (Map.Entry<String, Map<String, String>> entry : byNumber.entrySet()) {
            if (numbersOfTheSameOrder(entry.getKey(), number)
                    && samePaidMoney(entry.getValue(), order)) {
                return entry.getKey();
            }
        }
        return null;
    }

    /**
     * @param kept an order read so far
     * @param order one read of the same block
     * @return {@code true} when the read got the money of the block at all: a block always prints a
     *     实付款, so a read without one is a fragment of the block (the overlap of two slices shows the
     *     articles without the header) and never a second order
     */
    private static boolean hasMoney(Map<String, String> order) {
        Double paid = optionalAmount(order.get("paidTotal"));
        return paid != null && paid.doubleValue() > 0.0;
    }

    /**
     * @param kept an order read from one slice
     * @param order the same part of the page read from another slice
     * @return {@code true} when both print the same money (a read that got no money says nothing)
     */
    private static boolean samePaidMoney(Map<String, String> kept, Map<String, String> order) {
        Double paid = optionalAmount(kept.get("paidTotal"));
        Double otherPaid = optionalAmount(order.get("paidTotal"));
        return paid != null && otherPaid != null
                && Math.abs(paid.doubleValue() - otherPaid.doubleValue()) <= MONEY_TOLERANCE;
    }

    /**
     * @param first one order number
     * @param second another order number
     * @return {@code true} when one is the other read with a character written differently or with
     *     digits missing (a long number is read digit by digit, and a digit is dropped now and then)
     */
    public static boolean numbersOfTheSameOrder(String first, String second) {
        if (first == null || second == null) {
            return false;
        }
        String a = first.trim();
        String b = second.trim();
        if (a.isEmpty() || b.isEmpty() || a.equals(b) || Math.abs(a.length() - b.length()) > MISSING_DIGITS_ALLOWED) {
            return false;
        }
        if (a.length() == b.length()) {
            int differ = 0;
            int atFirst = -1;
            int atSecond = -1;
            for (int i = 0; i < a.length(); i++) {
                if (a.charAt(i) != b.charAt(i)) {
                    differ++;
                    if (atFirst < 0) {
                        atFirst = i;
                    } else if (atSecond < 0) {
                        atSecond = i;
                    }
                }
            }
            if (differ == 1) {
                return true;
            }
            // two digits read the other way round: 3316115535567018554 came back once as
            // 3316115553567018554 (Step 134)
            return differ == 2 && atSecond == atFirst + 1
                    && a.charAt(atFirst) == b.charAt(atSecond) && a.charAt(atSecond) == b.charAt(atFirst);
        }
        String longer = a.length() > b.length() ? a : b;
        String shorter = a.length() > b.length() ? b : a;
        // a handful of digits says nothing: the short read has to be a long number itself
        return shorter.length() >= 12 && isSubsequence(shorter, longer);
    }

    /**
     * @param shorter the shorter of two order numbers
     * @param longer the longer of the two
     * @return {@code true} when the digits of the shorter appear in the longer in the same order,
     *     which is what dropping digits from a number leaves behind
     */
    private static boolean isSubsequence(String shorter, String longer) {
        int at = 0;
        for (int i = 0; i < longer.length() && at < shorter.length(); i++) {
            if (longer.charAt(i) == shorter.charAt(at)) {
                at++;
            }
        }
        return at == shorter.length();
    }

    /**
     * @param kept an order read from one slice
     * @param order the same part of the page read from another slice
     * @return {@code true} when one of the two reads lost the shop of the block: the shop is printed on
     *     the same strip as the number, so a block with a number and no shop at all is the same block
     *     read a second time, not another order
     */
    private static boolean lostTheShop(Map<String, String> kept, Map<String, String> order) {
        String here = kept.get("shop") == null ? "" : kept.get("shop").trim();
        String there = order.get("shop") == null ? "" : order.get("shop").trim();
        return here.isEmpty() != there.isEmpty();
    }

    /**
     * @param kept an order read from one slice
     * @param order the same part of the page read from another slice
     * @return {@code true} when both carry exactly the same articles, the same articles money and the
     *     same paid total, which is one block read twice and not two orders
     */
    private static boolean sameMoneyAndArticles(Map<String, String> kept, Map<String, String> order) {
        String here = moneyKeyOf(kept);
        return here != null && here.equals(moneyKeyOf(order));
    }

    /**
     * @param kept an order read from one slice
     * @param order the same part of the page read from another slice
     * @return {@code true} when both print the same money, which is what two reads of one block share
     */
    private static boolean sameMoney(Map<String, String> kept, Map<String, String> order) {        Double paid = optionalAmount(kept.get("paidTotal"));
        Double otherPaid = optionalAmount(order.get("paidTotal"));
        if (paid == null || otherPaid == null) {
            // one of the two reads did not get the money: nothing says they are two blocks
            return true;
        }
        return Math.abs(paid.doubleValue() - otherPaid.doubleValue()) <= MONEY_TOLERANCE;
    }

    /**
     * @param byNumber the orders read so far
     * @param number the number two blocks were read with
     * @return a key for the second of them, so both are kept
     */
    private static String freeNumber(Map<String, Map<String, String>> byNumber, String number) {
        String key = number + " #2";
        for (int i = 3; byNumber.containsKey(key); i++) {
            key = number + " #" + i;
        }
        return key;
    }

    /**
     * Tells whether two reads of one block printed the same row, one of them carrying the price of a
     * single piece where the other carries what the order was paid: the page printed
     * {@code 原装正品 TPS63051RMWR … ¥2.18 x100} with 实付款 {@code ¥217.90}, and the model answered that
     * row twice - 217.90 once and 2.18 once - because 217.90 is 100 x 2.18 (Step 144).
     *
     * @param kept one read of the block
     * @param order the other read of it
     * @return {@code true} when one row amount is the other one multiplied by a quantity of the row
     */
    private static boolean onePrintsThePriceOfTheOther(Map<String, String> kept, Map<String, String> order) {
        for (String[] here : parseOrderArticleLines(kept.get("lines"))) {
            double hereQty = toDouble(here[2], 0.0);
            double hereAmount = toDouble(here[3], 0.0);
            for (String[] there : parseOrderArticleLines(order.get("lines"))) {
                double thereQty = toDouble(there[2], 0.0);
                double thereAmount = toDouble(there[3], 0.0);
                if (hereQty <= 0.0 || thereQty <= 0.0 || hereAmount <= 0.0 || thereAmount <= 0.0) {
                    continue;
                }
                double bigger = Math.max(hereAmount, thereAmount);
                double smaller = Math.min(hereAmount, thereAmount);
                // the two reads have to disagree, and a piece price only makes a bigger number when
                // there are pieces: one piece of 6.00 against one piece of 6.00 is the same row read
                // twice in the overlap of two slices, not a price
                if (bigger <= smaller + MONEY_TOLERANCE) {
                    continue;
                }
                // a piece price times the pieces is what the order was paid: the page rounds its unit
                // prices, so a few tenths of a cent per piece are allowed for
                double slack = Math.max(0.05, 0.002 * bigger);
                if ((hereQty >= 2.0 && Math.abs(round2(bigger - hereQty * smaller)) <= slack)
                        || (thereQty >= 2.0 && Math.abs(round2(bigger - thereQty * smaller)) <= slack)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * @param first one read of a block
     * @param second the other read of it
     * @return {@code true} when the first carries the bigger money, which is the read of the order
     */
    private static boolean biggerMoney(Map<String, String> first, Map<String, String> second) {
        Double here = optionalAmount(first.get("paidTotal"));
        Double there = optionalAmount(second.get("paidTotal"));
        return here != null && there != null && here.doubleValue() > there.doubleValue() + MONEY_TOLERANCE;
    }

    /**
     * @param blocksSaid how many blocks the model said each picture shows, {@code null} when unknown
     * @param slice the picture the read comes from
     * @param objects how many objects that picture answered with
     * @return {@code true} when the picture holds fewer blocks than the objects it came back with, so
     *     one of them is a block read in two parts
     */
    private static boolean saysFewerBlocksThanItRead(List<Integer> blocksSaid, int slice, int objects) {
        if (blocksSaid == null || slice < 0 || slice >= blocksSaid.size()) {
            return false;
        }
        Integer said = blocksSaid.get(slice);
        return said != null && said.intValue() > 0 && said.intValue() < objects;
    }

    /**
     * @param byNumber the orders read so far, by their number
     * @param order the order just read
     * @return the read of the same block that carries the price of one piece where this one carries
     *     the money of the order, {@code null} when there is none
     */
    private static Map<String, String> twinPrintingThePriceOfTheOther(
            Map<String, Map<String, String>> byNumber, Map<String, String> order) {
        String number = order.get("orderNo") == null ? "" : order.get("orderNo").trim();
        if (number.isEmpty()) {
            return null;
        }
        for (Map.Entry<String, Map<String, String>> entry : byNumber.entrySet()) {
            if (numbersOfTheSameOrder(entry.getKey(), number)
                    && sameShopOrMissing(entry.getValue(), order)
                    && onePrintsThePriceOfTheOther(entry.getValue(), order)) {
                return entry.getValue();
            }
        }
        return null;
    }

    /**
     * @param kept an order already read
     * @param order the order just read
     * @return {@code true} when both print the same shop, or one of them lost it: the shop sits on the
     *     same strip as the number, so a missing shop says nothing against the same block
     */
    private static boolean sameShopOrMissing(Map<String, String> kept, Map<String, String> order) {
        String here = kept.get("shop") == null ? "" : kept.get("shop").trim();
        String there = order.get("shop") == null ? "" : order.get("shop").trim();
        return here.isEmpty() || there.isEmpty() || here.equals(there);
    }

    /**
     * The two halves of one block carry the block's one 实付款 between them: when the two amounts come
     * to what the joined rows come to, and neither amount alone does, the order was paid that sum.
     *
     * @param kept the order the second half was just joined into
     * @param order the second half
     */
    private static void addUpTheTwoHalves(Map<String, String> kept, Map<String, String> order) {
        Double here = optionalAmount(kept.get("paidTotal"));
        Double there = optionalAmount(order.get("paidTotal"));
        if (here == null || there == null) {
            return;
        }
        List<String[]> rows = parseOrderArticleLines(kept.get("lines"));
        double goods = round2(sumOfRows(rows));
        double slack = roundingSlack(rows);
        double both = round2(here.doubleValue() + there.doubleValue());
        if (Math.abs(round2(both) - goods) <= slack && Math.abs(round2(here.doubleValue()) - goods) > slack) {
            kept.put("paidTotal", money(Double.valueOf(both)));
        }
    }

    /**
     * @param kept an order read from one slice
     * @param order the same part of the page read from another slice
     * @return {@code true} when both print the same shop and the same money
     */
    private static boolean sameShopAndMoney(Map<String, String> kept, Map<String, String> order) {
        String shop = kept.get("shop") == null ? "" : kept.get("shop").trim();
        String otherShop = order.get("shop") == null ? "" : order.get("shop").trim();
        if (!shop.isEmpty() && !otherShop.isEmpty() && !shop.equals(otherShop)) {
            return false;
        }
        Double paid = optionalAmount(kept.get("paidTotal"));
        Double otherPaid = optionalAmount(order.get("paidTotal"));
        if (paid != null && otherPaid != null) {
            // the money decides; a shop one read left empty does not spoil it
            return Math.abs(paid.doubleValue() - otherPaid.doubleValue()) <= MONEY_TOLERANCE;
        }
        // one of the two reads has no money: then the shop has to say they are the same order
        return !shop.isEmpty() && shop.equals(otherShop);
    }

    /**
     * Joins the article rows of two reads of the same order. The overlap of two slices shows a row
     * twice, but a page may also print the same row twice on purpose (one board in six colours), so
     * nothing is dropped here: {@code tidyOrder} removes the copies only when the money says which
     * reading is the true one.
     *
     * @param first the rows read so far
     * @param second the rows of the next read
     * @return the rows of both reads, in order
     */
    private static String joinRows(String first, String second) {
        List<String> rows = new ArrayList<>();
        for (String text : new String[] {first, second}) {
            if (text == null) {
                continue;
            }
            for (String row : text.split("\r?\n")) {
                String trimmed = row.trim();
                if (trimmed.isEmpty()) {
                    continue;
                }
                rows.add(trimmed);
            }
        }
        return String.join("\n", rows);
    }

    /**
     * Makes a short readable quote of what the model answered, so a scan that finds no fields can
     * say why: "this is not an order" and "I cannot see images" look the same without it.
     *
     * @param reply the answer text of the vision service
     * @param max length the quote is cut to
     * @return one line, never empty
     */
    public static String visionReplyExcerpt(String reply, int max) {
        if (reply == null) {
            return "（空回答）";
        }
        String text = jsonStringField(reply, "content");
        if (text == null || text.trim().isEmpty()) {
            text = jsonStringField(reply, "reasoning_content");
        }
        if (text == null || text.trim().isEmpty()) {
            text = reply;
        }
        text = text.replaceAll("\\s+", " ").trim();
        if (text.isEmpty()) {
            return "（空回答）";
        }
        return text.length() <= max ? text : text.substring(0, max) + "…";
    }

    /**
     * Reads the value of one JSON string field, whatever surrounds it. Scanned by hand rather than
     * matched with a pattern: an answer of a thinking model can hold several thousand characters, and
     * a {@code (?:[^"\\]|\\.)*} of that length blows the stack of the request thread
     * ({@code StackOverflowError} in {@code Pattern$Loop.match}, which the page shows as an empty
     * answer).
     *
     * @param text the JSON like text
     * @param name the field name
     * @return the value with its escapes resolved, {@code null} when the field is missing
     */
    public static String jsonStringField(String text, String name) {
        if (text == null || name == null || name.isEmpty()) {
            return null;
        }
        String key = "\"" + name + "\"";
        int at = text.indexOf(key);
        while (at >= 0) {
            int i = at + key.length();
            while (i < text.length() && Character.isWhitespace(text.charAt(i))) {
                i++;
            }
            if (i < text.length() && text.charAt(i) == ':') {
                i++;
                while (i < text.length() && Character.isWhitespace(text.charAt(i))) {
                    i++;
                }
                if (i < text.length() && text.charAt(i) == '"') {
                    int end = closingQuote(text, i + 1);
                    if (end > i) {
                        return jsonUnescape(text.substring(i + 1, end)).trim();
                    }
                }
            }
            at = text.indexOf(key, at + key.length());
        }
        return null;
    }

    /**
     * Finds the quote that closes the string whose body starts at the given offset.
     *
     * @param text the text
     * @param from the offset of the first character of the body
     * @return the offset of the closing quote, -1 when the string is not closed
     */
    private static int closingQuote(String text, int from) {
        boolean escaped = false;
        for (int i = Math.max(0, from); i < text.length(); i++) {
            char c = text.charAt(i);
            if (escaped) {
                escaped = false;
            } else if (c == '\\') {
                escaped = true;
            } else if (c == '"') {
                return i;
            }
        }
        return -1;
    }

    /**
     * Resolves the escapes of a JSON string in one pass. Chained replaces are not enough: a model
     * that escapes a newline twice answers {@code \\n}, which a {@code replace("\\n", "\n")}
     * turns into a lone backslash plus a real newline, and the field pattern then stops matching.
     *
     * @param raw the escaped text
     * @return the text with its escapes resolved
     */
    public static String jsonUnescape(String raw) {
        if (raw == null) {
            return null;
        }
        StringBuilder out = new StringBuilder(raw.length());
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c != '\\' || i + 1 >= raw.length()) {
                out.append(c);
                continue;
            }
            char next = raw.charAt(++i);
            switch (next) {
                case 'n':
                    out.append('\n');
                    break;
                case 'r':
                    out.append('\r');
                    break;
                case 't':
                    out.append('\t');
                    break;
                case 'b':
                    out.append('\b');
                    break;
                case 'f':
                    out.append('\f');
                    break;
                case 'u':
                    if (i + 4 < raw.length()) {
                        try {
                            out.append((char) Integer.parseInt(raw.substring(i + 1, i + 5), 16));
                            i += 4;
                            break;
                        } catch (NumberFormatException e) {
                            // a broken escape is kept as it is rather than losing the whole answer
                        }
                    }
                    out.append('u');
                    break;
                default:
                    // \\, \" and \/ all stand for the character itself
                    out.append(next);
                    break;
            }
        }
        return out.toString();
    }

    public static String normalizeVisionEndpoint(String endpoint) {
        if (endpoint == null) {
            return null;
        }
        String url = endpoint.trim();
        while (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }
        if (url.endsWith("/chat/completions")) {
            return url;
        }
        int slash = url.lastIndexOf('/');
        String last = slash < 0 ? url : url.substring(slash + 1);
        if (last.matches("v\\d+") || last.matches("\\d.*")) {
            // already at /v1, /v4 ...
            return url + "/chat/completions";
        }
        return url + "/v1/chat/completions";
    }

    /**
     * Says in plain words what a failed vision call means, with a short piece of the API answer.
     *
     * @param status the HTTP status
     * @param body the answer body, may be {@code null}
     * @return the message shown to the person
     */
    public static String describeVisionError(int status, String body) {
        String hint;
        if (status == 401 || status == 403) {
            hint = "密钥不对或没有权限";
        } else if (status == 404) {
            hint = "接口地址不对，要填完整的 chat/completions 地址，例如 https://api.deepseek.com/v1/chat/completions";
        } else if (status == 400) {
            hint = "请求被拒：模型名可能不对，或者这个模型不支持看图（换一个能看图的模型）";
        } else if (status == 429) {
            hint = "调用太频繁，稍后再试";
        } else if (status >= 500) {
            hint = "识图服务自己出错，稍后再试";
        } else {
            hint = "调用失败";
        }
        String detail = body == null ? "" : body.replaceAll("\\s+", " ").trim();
        if (detail.length() > 300) {
            detail = detail.substring(0, 300) + "…";
        }
        return "HTTP " + status + "：" + hint + (detail.isEmpty() ? "" : "（" + detail + "）");
    }

    /**
     * Writes one stock location: a warehouse, a zone or a shelf.
     *
     * @param dctx the dispatch context
     * @param context service context, see {@code servicedef/stock-services.xml#mfgLocationSave}
     * @return the service result containing {@code locationId}
     */

    private static double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private static Double optionalAmount(Object value) {
        String text = toText(value);
        if (text == null) {
            return null;
        }
        double amount = toDouble(text, Double.NaN);
        if (Double.isNaN(amount) || amount < 0.0) {
            return null;
        }
        return Double.valueOf(amount);
    }

    private static double toDouble(Object value, double defaultValue) {
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        if (value instanceof String) {
            try {
                String text = ((String) value).trim();
                return text.isEmpty() ? defaultValue : Double.parseDouble(text);
            } catch (NumberFormatException e) {
                return defaultValue;
            }
        }
        return defaultValue;
    }

    private static String toText(Object value) {
        if (value == null) {
            return null;
        }
        String text = value.toString().trim();
        return text.isEmpty() ? null : text;
    }
}

