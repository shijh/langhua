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
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.apache.ofbiz.base.util.Debug;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/**
 * Reads the workbook Taobao lets a buyer download (已买到的宝贝 → 导出订单, 1-10 pages) and turns it
 * into the orders the receipt page imports: one order per order number, one line per article.
 *
 * <p>The export prints the order level cells (order number, state, shop, paid total, freight) only
 * on the first row of an order and leaves them empty on the next rows, so they are carried down
 * before the rows are grouped. The columns are found by their header, not by their position: a
 * column Taobao adds later does not move the ones we read.
 *
 * <p>An xlsx is a zip of XML: the text lives in {@code xl/sharedStrings.xml} and the cells in
 * {@code xl/worksheets/sheet1.xml}. Apache POI is not on the classpath of this component and an
 * order export is plain (text cells, no formulas, one sheet), so the two parts are read with the
 * JDK alone - the same way {@link MfgProductServices#bomImport} reads a CSV by hand.
 */
public final class MfgOrderExcel {

    private static final String MODULE = MfgOrderExcel.class.getName();

    /** The header of the columns this import reads, as printed by 淘宝. */
    private static final String COL_ORDER = "订单号";
    private static final String COL_TIME = "订单提交时间";
    private static final String COL_STATE = "订单状态";
    private static final String COL_SHOP = "店铺名称";
    private static final String COL_NAME = "商品名称";
    private static final String COL_SPEC = "型号款式";
    private static final String COL_QTY = "商品数量";
    private static final String COL_PRICE = "商品金额";
    private static final String COL_PAID = "实付金额";
    private static final String COL_FREIGHT = "运费";

    /** The columns printed once per order and left empty on the next rows of the same order. */
    private static final String[] ORDER_LEVEL = {COL_ORDER, COL_TIME, COL_STATE, COL_SHOP, COL_PAID,
        COL_FREIGHT};

    /** Why an order of the export is not booked in, keyed by the state the export prints. */
    static final String SKIP_CLOSED = "交易关闭（没成交），不入库";
    static final String SKIP_TOPUP = "充值订单，没有商品，不入库";

    /** The date part of {@code 2023-11-10 16:22:01}, empty when unknown. */
    private static final java.util.regex.Pattern ORDER_STAMP =
            java.util.regex.Pattern.compile("(\\d{4})\\s*[-/.年]\\s*(\\d{1,2})\\s*[-/.月]\\s*(\\d{1,2})");

    private MfgOrderExcel() {
    }

    /**
     * Turns the uploaded workbook into orders ready for {@code mfgReceiptFromOrder}.
     *
     * @param workbook the bytes of the xlsx file
     * @return one map per order number, in the order the file prints them; keys are
     *         {@code orderNo}, {@code shop}, {@code state}, {@code orderDate}, {@code freight},
     *         {@code paidTotal}, {@code discount}, {@code lines}, {@code rowCount} and {@code skip}
     *         (the reason it is not booked in, {@code null} when it is)
     */
    static List<Map<String, String>> ordersOf(byte[] workbook) {
        return ordersOfRows(rowsOf(workbook));
    }

    /**
     * Reads the cells of the first worksheet of a workbook.
     *
     * @param workbook the bytes of the xlsx file
     * @return the rows, each a map of column letter (A, B, C ...) to the text of the cell; empty
     *         when the file is not a workbook this reader understands
     */
    static List<Map<String, String>> rowsOf(byte[] workbook) {
        if (workbook == null || workbook.length == 0) {
            return new ArrayList<>();
        }
        Map<String, byte[]> parts = unzip(workbook);
        List<String> shared = sharedStrings(parts.get("xl/sharedStrings.xml"));
        String sheet = null;
        for (String name : new TreeMap<>(parts).keySet()) {
            if (name.startsWith("xl/worksheets/") && name.endsWith(".xml")) {
                sheet = name;
                break;
            }
        }
        if (sheet == null) {
            return new ArrayList<>();
        }
        return cellsOf(parts.get(sheet), shared);
    }

    /**
     * Groups the rows of the sheet into orders.
     *
     * @param rows the rows of {@link #rowsOf(byte[])}
     * @return the orders, see {@link #ordersOf(byte[])}
     */
    static List<Map<String, String>> ordersOfRows(List<Map<String, String>> rows) {
        List<Map<String, String>> orders = new ArrayList<>();
        if (rows == null || rows.isEmpty()) {
            return orders;
        }
        int headerAt = -1;
        Map<String, String> columns = new HashMap<>();
        for (int i = 0; i < rows.size() && headerAt < 0; i++) {
            Map<String, String> row = rows.get(i);
            if (row == null) {
                continue;
            }
            boolean isHeader = false;
            Map<String, String> found = new HashMap<>();
            for (Map.Entry<String, String> cell : row.entrySet()) {
                String text = trim(cell.getValue());
                if (COL_ORDER.equals(text)) {
                    isHeader = true;
                }
                if (isOneOfTheColumns(text)) {
                    found.put(text, cell.getKey());
                }
            }
            if (isHeader) {
                headerAt = i;
                columns = found;
            }
        }
        if (headerAt < 0 || !columns.containsKey(COL_ORDER)) {
            return orders;
        }
        Map<String, String> carried = new HashMap<>();
        Map<String, String> current = null;
        List<String> lines = new ArrayList<>();
        double goods = 0.0;
        int rowCount = 0;
        for (int i = headerAt + 1; i < rows.size(); i++) {
            Map<String, String> row = rows.get(i);
            if (row == null) {
                continue;
            }
            // the order level cells are printed once: carry them down to the rows that leave them out
            for (String column : ORDER_LEVEL) {
                String value = trim(value(columns, row, column));
                if (!value.isEmpty()) {
                    carried.put(column, value);
                }
            }
            String orderNo = trim(carried.get(COL_ORDER));
            if (orderNo.isEmpty()) {
                continue;
            }
            if (current == null || !orderNo.equals(current.get("orderNo"))) {
                if (current != null) {
                    finish(current, lines, goods, rowCount);
                    orders.add(current);
                }
                current = new LinkedHashMap<>();
                current.put("orderNo", orderNo);
                current.put("shop", carried.get(COL_SHOP));
                current.put("state", carried.get(COL_STATE));
                current.put("orderDate", dateOf(carried.get(COL_TIME)));
                current.put("freight", money(carried.get(COL_FREIGHT)));
                current.put("paidTotal", money(carried.get(COL_PAID)));
                lines = new ArrayList<>();
                goods = 0.0;
                rowCount = 0;
            }
            String name = trim(value(columns, row, COL_NAME));
            String spec = trim(value(columns, row, COL_SPEC));
            if (name.isEmpty() && spec.isEmpty()) {
                continue;
            }
            double qty = number(value(columns, row, COL_QTY));
            if (qty <= 0.0) {
                qty = 1.0;
            }
            // 商品金额 is the price of one piece; the row's own money is that times the pieces
            double amount = round2(number(value(columns, row, COL_PRICE)) * qty);
            String code = spec.isEmpty() ? name : spec;
            lines.add(MfgProductServices.fittingComponentCode(code) + "\t"
                    + (name.isEmpty() ? spec : name) + "\t" + count(qty) + "\t" + amount(amount));
            goods += amount;
            rowCount++;
        }
        if (current != null) {
            finish(current, lines, goods, rowCount);
            orders.add(current);
        }
        // an order that only carried the header down but printed no article is not an order
        List<Map<String, String>> kept = new ArrayList<>();
        for (Map<String, String> order : orders) {
            if (order.get("rowCount") != null && !"0".equals(order.get("rowCount"))) {
                kept.add(order);
            }
        }
        return kept;
    }

    /**
     * Completes one order: the lines, the discount and whether it is booked in at all.
     *
     * @param order the order being built
     * @param lines the article lines read so far
     * @param goods the sum of the lines
     * @param rowCount how many rows the order printed
     */
    private static void finish(Map<String, String> order, List<String> lines, double goods, int rowCount) {
        order.put("lines", String.join("\n", lines));
        order.put("rowCount", String.valueOf(rowCount));
        double freight = number(order.get("freight"));
        double paid = number(order.get("paidTotal"));
        // 实付金额 is what the order really cost: a red packet or a coupon makes it smaller than the
        // lines plus the freight, and that difference is the discount the receipt keeps (Step 95)
        double discount = round2(goods + freight - paid);
        order.put("discount", paid <= 0.0 || discount <= 0.0 ? "0" : amount(discount));
        String state = trim(order.get("state"));
        if (state.contains("关闭") || state.contains("已取消")) {
            order.put("skip", SKIP_CLOSED);
        } else if (state.contains("充值")) {
            order.put("skip", SKIP_TOPUP);
        }
    }

    private static boolean isOneOfTheColumns(String text) {
        for (String column : new String[] {COL_ORDER, COL_TIME, COL_STATE, COL_SHOP, COL_NAME,
                COL_SPEC, COL_QTY, COL_PRICE, COL_PAID, COL_FREIGHT}) {
            if (column.equals(text)) {
                return true;
            }
        }
        return false;
    }

    private static String value(Map<String, String> columns, Map<String, String> row, String column) {
        String letter = columns.get(column);
        return letter == null ? null : row.get(letter);
    }

    /**
     * Reads the two XML parts of the workbook that matter.
     *
     * @param workbook the bytes of the xlsx file
     * @return the parts, keyed by their name inside the zip
     */
    private static Map<String, byte[]> unzip(byte[] workbook) {
        Map<String, byte[]> parts = new HashMap<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(workbook))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                // a zip written by some tools carries backslashes: the sheet is the same either way
                String name = entry.getName().replace('\\', '/');
                if (entry.isDirectory()) {
                    continue;
                }
                boolean wanted = "xl/sharedStrings.xml".equals(name)
                        || (name.startsWith("xl/worksheets/") && name.endsWith(".xml"));
                if (!wanted) {
                    continue;
                }
                parts.put(name, readAll(zip));
            }
        } catch (IOException e) {
            Debug.logError(e, "Could not read the uploaded workbook", MODULE);
            return new HashMap<>();
        }
        return parts;
    }

    /**
     * Reads the shared string table of a workbook.
     *
     * @param xml the content of {@code xl/sharedStrings.xml}, may be {@code null}
     * @return the strings, by the index a cell points at
     */
    private static List<String> sharedStrings(byte[] xml) {
        List<String> strings = new ArrayList<>();
        if (xml == null) {
            return strings;
        }
        Document document = parse(xml);
        if (document == null) {
            return strings;
        }
        NodeList items = document.getElementsByTagName("si");
        for (int i = 0; i < items.getLength(); i++) {
            strings.add(textOf((Element) items.item(i)));
        }
        return strings;
    }

    /**
     * Reads the cells of a worksheet.
     *
     * @param xml the content of the sheet
     * @param shared the shared strings, used by cells that point at them
     * @return the rows, each a map of column letter to the text of the cell
     */
    private static List<Map<String, String>> cellsOf(byte[] xml, List<String> shared) {
        List<Map<String, String>> rows = new ArrayList<>();
        Document document = parse(xml);
        if (document == null) {
            return rows;
        }
        NodeList rowNodes = document.getElementsByTagName("row");
        for (int i = 0; i < rowNodes.getLength(); i++) {
            Map<String, String> row = new LinkedHashMap<>();
            NodeList cells = ((Element) rowNodes.item(i)).getElementsByTagName("c");
            for (int c = 0; c < cells.getLength(); c++) {
                Element cell = (Element) cells.item(c);
                String letter = lettersOf(cell.getAttribute("r"));
                if (letter.isEmpty()) {
                    continue;
                }
                row.put(letter, cellText(cell, shared));
            }
            if (!row.isEmpty()) {
                rows.add(row);
            }
        }
        return rows;
    }

    /**
     * Reads one cell: a shared string, an inline string or a plain value.
     *
     * @param cell the cell element
     * @param shared the shared strings of the workbook
     * @return the text of the cell, never {@code null}
     */
    private static String cellText(Element cell, List<String> shared) {
        String type = cell.getAttribute("t");
        if ("s".equals(type)) {
            String index = firstChildText(cell, "v");
            try {
                int at = Integer.parseInt(index.trim());
                return at >= 0 && at < shared.size() ? shared.get(at) : "";
            } catch (NumberFormatException e) {
                return "";
            }
        }
        if ("inlineStr".equals(type)) {
            Element inline = firstChild(cell, "is");
            return inline == null ? "" : textOf(inline);
        }
        String value = firstChildText(cell, "v");
        return value == null ? "" : value;
    }

    /** The column letters of a cell reference such as {@code AB12}. */
    private static String lettersOf(String reference) {
        StringBuilder letters = new StringBuilder();
        for (int i = 0; i < reference.length(); i++) {
            char ch = reference.charAt(i);
            if (ch >= 'A' && ch <= 'Z') {
                letters.append(ch);
            } else {
                break;
            }
        }
        return letters.toString();
    }

    /** All the {@code <t>} runs of an {@code <si>} or {@code <is>} element, joined. */
    private static String textOf(Element element) {
        StringBuilder text = new StringBuilder();
        NodeList runs = element.getElementsByTagName("t");
        for (int i = 0; i < runs.getLength(); i++) {
            Node run = runs.item(i);
            text.append(run.getTextContent() == null ? "" : run.getTextContent());
        }
        if (runs.getLength() == 0) {
            text.append(element.getTextContent() == null ? "" : element.getTextContent());
        }
        return text.toString();
    }

    private static Element firstChild(Element element, String name) {
        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child instanceof Element && name.equals(child.getNodeName())) {
                return (Element) child;
            }
        }
        return null;
    }

    private static String firstChildText(Element element, String name) {
        Element child = firstChild(element, name);
        return child == null ? null : child.getTextContent();
    }

    /**
     * Parses a part of the workbook, with the external entities switched off: the file comes from a
     * browser upload, so it is data and never something that may pull in another document.
     *
     * @param xml the XML to parse
     * @return the document, {@code null} when it could not be parsed
     */
    private static Document parse(byte[] xml) {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(false);
        factory.setExpandEntityReferences(false);
        try {
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        } catch (ParserConfigurationException e) {
            Debug.logWarning(e, "This parser does not know disallow-doctype-decl", MODULE);
        }
        try {
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        } catch (ParserConfigurationException e) {
            Debug.logWarning(e, "This parser does not switch the external entities off", MODULE);
        }
        try {
            factory.setXIncludeAware(false);
            return factory.newDocumentBuilder().parse(new ByteArrayInputStream(xml));
        } catch (ParserConfigurationException | SAXException | IOException e) {
            Debug.logError(e, "Could not parse a part of the uploaded workbook", MODULE);
            return null;
        }
    }

    private static byte[] readAll(InputStream in) throws IOException {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int read;
        while ((read = in.read(buffer)) > 0) {
            out.write(buffer, 0, read);
        }
        return out.toByteArray();
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }

    /**
     * The date part of the {@code 2023-11-10 16:22:01} the export prints, empty when unknown.
     *
     * <p>{@code 2018/12/1} and {@code 2018年12月1日} are read as well: the day of the order decides
     * the day the goods are taken to have arrived, so a stamp that is not read means the order falls
     * back to today - and a 2018 order would then sit in this month's stock.
     */
    private static String dateOf(String stamp) {
        java.util.regex.Matcher found = ORDER_STAMP.matcher(trim(stamp));
        if (!found.find()) {
            return "";
        }
        int year = Integer.parseInt(found.group(1));
        int month = Integer.parseInt(found.group(2));
        int day = Integer.parseInt(found.group(3));
        if (month < 1 || month > 12 || day < 1 || day > 31) {
            return "";
        }
        return String.format(Locale.ROOT, "%04d-%02d-%02d",
                Integer.valueOf(year), Integer.valueOf(month), Integer.valueOf(day));
    }

    /** Reads a money cell such as {@code ￥5.05} or {@code 5.05}. */
    private static String money(String value) {
        String text = trim(value);
        if (text.isEmpty()) {
            return "0";
        }
        return amount(number(text));
    }

    private static double number(String value) {
        String text = trim(value);
        if (text.isEmpty()) {
            return 0.0;
        }
        StringBuilder digits = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if ((ch >= '0' && ch <= '9') || ch == '.' || (ch == '-' && digits.length() == 0)) {
                digits.append(ch);
            }
        }
        if (digits.length() == 0) {
            return 0.0;
        }
        try {
            return Double.parseDouble(digits.toString());
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }

    private static String amount(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }

    /** A quantity without the {@code .0} a double drags along; the lines parser reads it back. */
    private static String count(double value) {
        if (Math.abs(value - Math.rint(value)) < 0.0001) {
            return String.valueOf((long) Math.rint(value));
        }
        return String.format(Locale.ROOT, "%.3f", value);
    }

    private static double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
