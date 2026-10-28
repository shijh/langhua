package org.langhua.ofbiz.mfg;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The text formats of the component: the CSV the pages export, and the text the imports read - BOM
 * exports of this system and of Fritzing, material lots, test bench results, serial number lists and
 * sales order lines.
 *
 * <p>They were pulled out of {@code MfgServices} (Step 164) because they are the part of an import
 * that can be wrong without touching the database: every parser here returns plain cells, and the
 * service that can store them decides what to do with them.
 */
public final class MfgCsv {

    private MfgCsv() {
    }

    /**
     * Parses material lot CSV text into rows of
     * {@code componentCode, componentName, supplierName, supplierLot, qty, receivedDate}.
     * Empty lines and lines starting with {@code #} are ignored.
     *
     * @param csv the CSV text
     * @return the parsed rows, never {@code null}
     */
    static List<String[]> parseLotCsv(String csv) {
        List<String[]> rows = new ArrayList<>();
        if (csv == null) {
            return rows;
        }
        for (String line : csv.replace("\uFEFF", "").replace("\r\n", "\n").replace('\r', '\n').split("\n")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }
            String[] cells = splitCsvLine(trimmed);
            String code = unquote(cells[0]).toUpperCase(Locale.ROOT);
            if (code.isEmpty()) {
                continue;
            }
            String[] row = new String[6];
            row[0] = code;
            row[1] = cells.length > 1 ? unquote(cells[1]) : null;
            row[2] = cells.length > 2 ? unquote(cells[2]) : null;
            row[3] = cells.length > 3 ? unquote(cells[3]) : null;
            row[4] = cells.length > 4 ? unquote(cells[4]) : null;
            row[5] = cells.length > 5 ? unquote(cells[5]) : null;
            rows.add(row);
        }
        return rows;
    }

    /**
     * Splits a whitespace or comma separated list of serial numbers.
     *
     * @param text the raw text, may be {@code null}
     * @return the serial numbers, never {@code null}
     */
    static List<String> parseSnList(String text) {
        List<String> sns = new ArrayList<>();
        if (text == null) {
            return sns;
        }
        for (String candidate : text.split("[,\\s]+")) {
            String sn = candidate.trim();
            if (!sn.isEmpty() && !sns.contains(sn)) {
                sns.add(sn);
            }
        }
        return sns;
    }

    /**
     * Parses test bench CSV text into rows of {@code sn, station, item, value, unit, pass}.
     * Empty lines and lines starting with {@code #} are ignored.
     *
     * @param csv the CSV text
     * @return the parsed rows, never {@code null}
     */
    static List<String[]> parseTestCsv(String csv) {
        List<String[]> rows = new ArrayList<>();
        if (csv == null) {
            return rows;
        }
        for (String line : csv.replace("\r\n", "\n").replace('\r', '\n').split("\n")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }
            String[] cells = splitCsvLine(trimmed);
            String sn = unquote(cells[0]);
            if (sn.isEmpty()) {
                continue;
            }
            String[] row = new String[6];
            row[0] = sn;
            row[1] = cells.length > 1 ? unquote(cells[1]) : null;
            row[2] = cells.length > 2 ? unquote(cells[2]) : null;
            row[3] = cells.length > 3 ? unquote(cells[3]) : null;
            row[4] = cells.length > 4 ? unquote(cells[4]) : null;
            row[5] = cells.length > 5 ? unquote(cells[5]) : null;
            rows.add(row);
        }
        return rows;
    }

    /**
     * Quotes one CSV cell: a value holding a comma, a quote or a line break is wrapped in double
     * quotes and its own quotes are doubled.
     *
     * @param value the cell value, may be {@code null}
     * @return the cell as it goes into the file
     */
    static String csvCell(String value) {
        if (value == null) {
            return "";
        }
        boolean quote = value.indexOf(',') >= 0 || value.indexOf('"') >= 0
                || value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0;
        String escaped = value.replace("\"", "\"\"");
        return quote ? "\"" + escaped + "\"" : escaped;
    }

    /**
     * Joins cells into one CSV line, without the line break.
     *
     * @param cells the cells, {@code null} cells become empty
     * @return the line
     */
    static String csvLine(List<String> cells) {
        StringBuilder line = new StringBuilder();
        for (String cell : cells) {
            if (line.length() > 0) {
                line.append(',');
            }
            line.append(csvCell(cell));
        }
        return line.toString();
    }

    /**
     * Reads the order lines of the order form: one line per model, {@code 型号,数量} or
     * {@code 型号,数量,交期}, blank lines and {@code #} comments are skipped.
     *
     * @param text the text of the form field
     * @return one map per line with {@code productCode}, {@code qty} and an optional {@code lineDueDate}
     */
    static List<Map<String, Object>> parseOrderLines(String text) {
        List<Map<String, Object>> lines = new ArrayList<>();
        if (text == null) {
            return lines;
        }
        for (String raw : text.split("\\r?\\n")) {
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            String[] cells = line.split("[,，\\t;；]");
            String productCode = cells.length == 0 ? "" : cells[0].trim();
            if (productCode.isEmpty()) {
                continue;
            }
            double qty = cells.length > 1 ? MfgSupport.toDouble(cells[1].trim(), 0.0) : 0.0;
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("productCode", productCode);
            map.put("qty", Double.valueOf(MfgSupport.round2(qty)));
            Object lineDueDate = cells.length > 2 ? MfgSupport.parseDate(cells[2].trim()) : null;
            if (lineDueDate != null) {
                map.put("lineDueDate", lineDueDate);
            }
            lines.add(map);
        }
        return lines;
    }

    /**
     * The BOM text formats the import understands.
     *
     * <p>To support another tool, add a constant here, teach {@link #detectBomFormat(String)} to
     * recognize it and write a parser that returns the same six columns as
     * {@link #parseBomCsv(String)}: {@code componentCode, componentName, qty, designators,
     * isKeyPart, unitPrice}.
     */
    enum BomFormat {
        /** The format of this component: {@code componentCode,componentName,qty,designators,isKeyPart,unitPrice}. */
        MFG("本系统"),
        /** The export of Fritzing: {@code Label;Part Type;mn;mpn;part number;} plus an {@code Amount;} block. */
        FRITZING("Fritzing"),
        /** The HTML report Fritzing writes next to the sketch: two tables, Assembly List and Shopping List. */
        FRITZING_HTML("Fritzing HTML");

        private final String label;

        BomFormat(String label) {
            this.label = label;
        }

        /**
         * Returns the name shown to the user.
         *
         * @return the label
         */
        String label() {
            return label;
        }

        /**
         * Looks a format up by its code, ignoring case.
         *
         * @param code the code typed in or stored, may be {@code null}
         * @return the format, {@code null} when the code is unknown or empty
         */
        static BomFormat of(String code) {
            if (code == null) {
                return null;
            }
            for (BomFormat format : values()) {
                if (format.name().equalsIgnoreCase(code.trim())) {
                    return format;
                }
            }
            return null;
        }
    }

    /**
     * Guesses the format of BOM text. Fritzing writes {@code Label;Part Type;mn;mpn;part number;}
     * as a header and separates the cells with a semicolon, which no other supported format does.
     *
     * @param csv the text to inspect
     * @return the detected format, {@link BomFormat#MFG} when nothing else matches
     */
    static BomFormat detectBomFormat(String csv) {
        if (csv == null) {
            return BomFormat.MFG;
        }
        String head = csv.replace("\uFEFF", "").trim().toLowerCase(Locale.ROOT);
        if (head.contains("<html") || head.contains("<!doctype html") || head.startsWith("<table")) {
            return BomFormat.FRITZING_HTML;
        }
        for (String line : csv.replace("\uFEFF", "").replace("\r\n", "\n").replace('\r', '\n').split("\n")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            String lower = trimmed.toLowerCase(Locale.ROOT);
            if (lower.contains(";") && (lower.startsWith("label;") || lower.startsWith("amount;"))) {
                return BomFormat.FRITZING;
            }
            // only the first meaningful line decides
            break;
        }
        return BomFormat.MFG;
    }

    /**
     * Parses BOM text after detecting its format.
     *
     * @param csv the text
     * @param format the format to use, {@code null} to detect it
     * @return the rows in the shape of {@link #parseBomCsv(String)}
     */
    static List<String[]> parseBom(String csv, BomFormat format) {
        BomFormat used = format == null ? detectBomFormat(csv) : format;
        if (used == BomFormat.FRITZING_HTML) {
            return parseFritzingHtmlBom(csv);
        }
        if (used == BomFormat.FRITZING) {
            return parseFritzingBom(csv);
        }
        return parseBomCsv(csv);
    }

    /**
     * Parses a Fritzing BOM export.
     *
     * <p>Fritzing writes two blocks separated by a semicolon:
     * <pre>
     * Label;Part Type;mn;mpn;part number;        one row per reference designator
     * BT1;Supercapacitor 1.5F 5.5V;;;SE-5R5-D155VYC3G;
     * Amount;Part Type;mn;mpn;part number;       the same parts with their amount
     * 1;Supercapacitor 1.5F 5.5V;;;SE-5R5-D155VYC3G;
     * </pre>
     * The amount block gives the quantity, the label block gives the reference designators, and
     * both are merged per part. The part number is the component code; a part without one falls
     * back to its (upper cased) name.
     *
     * @param csv the Fritzing export
     * @return the rows in the shape of {@link #parseBomCsv(String)}
     */
    static List<String[]> parseFritzingBom(String csv) {
        // key -> row being built: code, name, qty, designators
        Map<String, String[]> byPart = new LinkedHashMap<>();
        List<String> order = new ArrayList<>();
        Map<String, Double> amounts = new LinkedHashMap<>();
        if (csv == null) {
            return new ArrayList<>();
        }
        for (String line : csv.replace("\uFEFF", "").replace("\r\n", "\n").replace('\r', '\n').split("\n")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            String[] cells = splitCsvLine(trimmed, ';');
            String first = unquote(cells[0]);
            if (first.equalsIgnoreCase("Label") || first.equalsIgnoreCase("Amount")) {
                // a header row, the layout is known from the position
                continue;
            }
            boolean amountBlock = first.matches("[0-9]+([.,][0-9]+)?");
            String name = cells.length > 1 ? unquote(cells[1]) : "";
            String partNumber = cells.length > 4 ? unquote(cells[4]) : "";
            if (partNumber.isEmpty() && cells.length > 3) {
                partNumber = unquote(cells[3]);
            }
            String key = partNumber.isEmpty() ? name : partNumber;
            if (key.isEmpty()) {
                continue;
            }
            if (!byPart.containsKey(key)) {
                String code = (partNumber.isEmpty() ? name : partNumber).toUpperCase(Locale.ROOT);
                byPart.put(key, new String[] {code, name, null, null});
                order.add(key);
            }
            String[] row = byPart.get(key);
            if (amountBlock) {
                amounts.put(key, Double.valueOf(MfgSupport.toDouble(first.replace(',', '.'), 1.0)));
            } else {
                // one row per reference designator: the quantity is the number of rows
                String designator = first;
                if (row[3] == null) {
                    row[3] = designator;
                } else if (!designator.isEmpty()) {
                    row[3] = row[3] + "," + designator;
                }
                row[2] = String.valueOf(MfgSupport.toLong(row[2], 0) + 1);
            }
        }
        List<String[]> rows = new ArrayList<>(order.size());
        for (String key : order) {
            String[] row = byPart.get(key);
            Double amount = amounts.get(key);
            String qty = amount != null ? String.valueOf(amount.longValue()) : row[2];
            if (qty == null) {
                qty = "1";
            }
            rows.add(new String[] {row[0], row[1] == null || row[1].isEmpty() ? row[0] : row[1],
                    qty, row[3], "N", null});
        }
        return rows;
    }

    /**
     * Parses the HTML report Fritzing writes next to the sketch (面包板 → {@code Export → Bill of
     * Materials}, the {@code <sketch>_bom.html} file).
     *
     * <p>The page prints two tables: the <em>Assembly List</em>, one row per reference designator
     * ({@code Label | MN | MPN | Part Type | Properties}), and the <em>Shopping List</em>, one row
     * per part with its amount ({@code Amount | MN | MPN | Part Type | Properties}). The columns are
     * located by their header, so their order does not matter and an extra column does no harm. Rows
     * of both tables are merged per part: the amount gives the quantity, the labels give the
     * reference designators.
     *
     * <p>The component code is taken from, in this order: the manufacturer part number, the
     * manufacturer number, the {@code 元件号} property, the part type when it is a single word
     * ({@code WS2812B-1010}), a code built from part type and properties ({@code 10kΩ Resistor} in
     * 0603 becomes {@code R-10K-0603}), or a stable {@code P-…} stand-in. The page lets every code
     * be corrected afterwards, because the codes of the shop are the ones stock is booked under.
     *
     * @param html the HTML report
     * @return rows of {@code code, name, qty, designators, isKeyPart, unitPrice, properties}; the
     *         seventh cell is the Fritzing properties line, other formats leave it {@code null}
     */
    static List<String[]> parseFritzingHtmlBom(String html) {
        List<String[]> rows = new ArrayList<>();
        if (html == null) {
            return rows;
        }
        String text = html.replace("\uFEFF", "");
        Map<String, HtmlPart> parts = new LinkedHashMap<>();
        Map<String, Integer> columns = null;
        int pos = 0;
        while (true) {
            int open = indexOfIgnoreCase(text, "<tr", pos);
            if (open < 0) {
                break;
            }
            int close = indexOfIgnoreCase(text, "</tr>", open);
            if (close < 0) {
                close = text.length();
            }
            String rowHtml = text.substring(open, close);
            pos = close + 5;
            List<String> cells = htmlCells(rowHtml);
            if (cells.isEmpty()) {
                continue;
            }
            if (isHtmlHeader(cells)) {
                columns = htmlColumns(cells);
                continue;
            }
            if (columns == null) {
                // a table without a header: nothing tells us which cell is the quantity
                continue;
            }
            String partType = cell(cells, columns, "partType");
            String properties = cell(cells, columns, "properties");
            String mn = cell(cells, columns, "mn");
            String mpn = cell(cells, columns, "mpn");
            if (partType == null && properties == null) {
                continue;
            }
            String key = (mpn == null ? "" : mpn) + '|' + (mn == null ? "" : mn) + '|'
                    + (partType == null ? "" : partType) + '|' + (properties == null ? "" : properties);
            HtmlPart part = parts.get(key);
            if (part == null) {
                part = new HtmlPart(partType, properties, mn, mpn);
                parts.put(key, part);
            }
            String label = cell(cells, columns, "label");
            if (label != null && !label.isEmpty()) {
                part.labels.add(label);
            }
            String amount = cell(cells, columns, "amount");
            if (amount != null) {
                double qty = MfgSupport.toDouble(amount.replace(',', '.'), 0.0);
                if (qty > 0.0) {
                    part.amount = qty;
                }
            }
        }
        for (HtmlPart part : parts.values()) {
            String name = part.partType == null ? part.codeHint() : part.partType;
            double qty = part.amount > 0.0 ? part.amount
                    : (part.labels.isEmpty() ? 1.0 : part.labels.size());
            rows.add(new String[] {part.codeHint(), name, String.valueOf((long) qty),
                    part.labels.isEmpty() ? null : join(part.labels), "N", null, part.properties});
        }
        return rows;
    }

    /** One part of a Fritzing HTML report, filled from both of its tables. */
    private static final class HtmlPart {

        private final String partType;
        private final String properties;
        private final String mn;
        private final String mpn;
        private final List<String> labels = new ArrayList<>();
        private double amount;

        HtmlPart(String partType, String properties, String mn, String mpn) {
            this.partType = partType;
            this.properties = properties;
            this.mn = mn;
            this.mpn = mpn;
        }

        String codeHint() {
            String code = firstNonEmpty(mpn, mn, propertyValue(properties, "元件号"));
            if (code == null && partType != null && partType.indexOf(' ') < 0) {
                code = partType;
            }
            if (code == null) {
                code = syntheticComponentCode(partType, properties);
            }
            return code.toUpperCase(Locale.ROOT);
        }
    }

    /**
     * Whether a table row is the header of its table. Fritzing prints
     * {@code Label | MN (manufacturer number) | MPN (manufacturer part number) | Part Type | Properties}
     * over the Assembly List and the same with {@code Amount} first over the Shopping List.
     *
     * @param cells the cleaned cells of the row
     * @return {@code true} when the row names the columns
     */
    private static boolean isHtmlHeader(List<String> cells) {
        for (String text : cells) {
            String lower = text.toLowerCase(Locale.ROOT);
            if (lower.startsWith("part type") || lower.startsWith("mpn") || lower.startsWith("mn (")
                    || (lower.equals("amount") && cells.size() > 2)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Maps the header names of one table to their column index.
     *
     * @param cells the header cells
     * @return the column index per known column
     */
    private static Map<String, Integer> htmlColumns(List<String> cells) {
        Map<String, Integer> columns = new LinkedHashMap<>();
        for (int i = 0; i < cells.size(); i++) {
            String lower = cells.get(i).toLowerCase(Locale.ROOT);
            if (lower.startsWith("mpn")) {
                columns.put("mpn", Integer.valueOf(i));
            } else if (lower.startsWith("mn")) {
                columns.put("mn", Integer.valueOf(i));
            } else if (lower.equals("label")) {
                columns.put("label", Integer.valueOf(i));
            } else if (lower.equals("amount")) {
                columns.put("amount", Integer.valueOf(i));
            } else if (lower.startsWith("part type")) {
                columns.put("partType", Integer.valueOf(i));
            } else if (lower.startsWith("properties")) {
                columns.put("properties", Integer.valueOf(i));
            }
        }
        return columns;
    }

    private static String cell(List<String> cells, Map<String, Integer> columns, String name) {
        Integer index = columns.get(name);
        if (index == null || index.intValue() >= cells.size()) {
            return null;
        }
        String value = cells.get(index.intValue());
        return value == null || value.isEmpty() ? null : value;
    }

    /**
     * The cells of one {@code <tr>}, in order, with tags removed, entities decoded and whitespace
     * collapsed. Both {@code <td>} and {@code <th>} count as a cell.
     *
     * @param rowHtml the markup of the row
     * @return the cleaned cells
     */
    static List<String> htmlCells(String rowHtml) {
        List<String> cells = new ArrayList<>();
        if (rowHtml == null) {
            return cells;
        }
        int pos = 0;
        while (true) {
            int td = indexOfIgnoreCase(rowHtml, "<td", pos);
            int th = indexOfIgnoreCase(rowHtml, "<th", pos);
            int open = td < 0 ? th : (th < 0 ? td : Math.min(td, th));
            if (open < 0) {
                break;
            }
            boolean cellIsTd = td >= 0 && td == open;
            int tagEnd = rowHtml.indexOf('>', open);
            if (tagEnd < 0) {
                break;
            }
            String closer = cellIsTd ? "</td>" : "</th>";
            int end = indexOfIgnoreCase(rowHtml, closer, tagEnd);
            if (end < 0) {
                end = rowHtml.length();
            }
            cells.add(cleanHtml(rowHtml.substring(tagEnd + 1, end)));
            pos = end + closer.length();
        }
        return cells;
    }

    /**
     * Removes the tags of a fragment, decodes the HTML entities and collapses whitespace, so a cell
     * reads the same as the text on the page.
     *
     * @param html the fragment
     * @return the plain text
     */
    static String cleanHtml(String html) {
        if (html == null) {
            return null;
        }
        StringBuilder out = new StringBuilder(html.length());
        boolean inTag = false;
        for (int i = 0; i < html.length(); i++) {
            char c = html.charAt(i);
            if (c == '<') {
                inTag = true;
                out.append(' ');
            } else if (c == '>') {
                inTag = false;
            } else if (!inTag) {
                out.append(c);
            }
        }
        return collapse(decodeEntities(out.toString()));
    }

    /**
     * Decodes the named and numeric entities an HTML report uses.
     *
     * @param text the text to decode
     * @return the decoded text
     */
    static String decodeEntities(String text) {
        if (text == null || text.indexOf('&') < 0) {
            return text;
        }
        StringBuilder out = new StringBuilder(text.length());
        int i = 0;
        while (i < text.length()) {
            char c = text.charAt(i);
            if (c != '&') {
                out.append(c);
                i++;
                continue;
            }
            int semi = text.indexOf(';', i + 1);
            String plain = semi < 0 || semi - i > 10 ? null : plainEntity(text.substring(i + 1, semi));
            if (plain == null) {
                out.append(c);
                i++;
                continue;
            }
            out.append(plain);
            i = semi + 1;
        }
        return out.toString();
    }

    private static String plainEntity(String name) {
        if (name == null || name.isEmpty()) {
            return null;
        }
        if (name.charAt(0) == '#') {
            try {
                int code = name.length() > 1 && (name.charAt(1) == 'x' || name.charAt(1) == 'X')
                        ? Integer.parseInt(name.substring(2), 16)
                        : Integer.parseInt(name.substring(1));
                if (code <= 0 || code > 0xFFFF) {
                    return null;
                }
                return String.valueOf((char) code);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        switch (name.toLowerCase(Locale.ROOT)) {
            case "nbsp":
                return " ";
            case "amp":
                return "&";
            case "lt":
                return "<";
            case "gt":
                return ">";
            case "quot":
                return "\"";
            case "apos":
                return "'";
            default:
                return null;
        }
    }

    /**
     * Reads one {@code key value} pair out of the properties line of a Fritzing part, e.g.
     * {@code 电容 100 nF; 电压 16V; 包 [SMD] 0603} answers {@code 100 nF} for {@code 电容}.
     *
     * @param properties the properties line
     * @param name the key to look for
     * @return the value, {@code null} when the key is not there
     */
    static String propertyValue(String properties, String name) {
        if (properties == null || name == null) {
            return null;
        }
        for (String piece : properties.split(";")) {
            String trimmed = piece.trim();
            if (!trimmed.startsWith(name)) {
                continue;
            }
            String rest = trimmed.substring(name.length()).trim();
            if (!rest.isEmpty()) {
                return rest;
            }
        }
        return null;
    }

    /**
     * Builds a component code for a Fritzing part that prints no manufacturer number, so a resistor
     * of 10 kΩ in 0603 becomes {@code R-10K-0603} and a 100 nF / 16 V capacitor in 0603 becomes
     * {@code C-100NF-16V-0603}.
     *
     * @param partType the part type of the sketch, e.g. {@code Capacitor 0603}
     * @param properties its properties line
     * @return the code, never {@code null}
     */
    static String syntheticComponentCode(String partType, String properties) {
        List<String> bits = new ArrayList<>();
        String kind = partKind(partType);
        if (kind != null) {
            bits.add(kind);
        }
        String value = firstNonEmpty(propertyValue(properties, "电阻"), propertyValue(properties, "阻值"),
                propertyValue(properties, "电容"), propertyValue(properties, "电感"),
                propertyValue(properties, "晶振"));
        if (value != null) {
            bits.add(unitOf(value));
        }
        String voltage = propertyValue(properties, "电压");
        if (voltage != null) {
            bits.add(unitOf(voltage));
        }
        String devicePackage = packageOf(propertyValue(properties, "包"));
        if (devicePackage != null) {
            bits.add(devicePackage);
        }
        StringBuilder code = new StringBuilder();
        for (String bit : bits) {
            if (bit == null || bit.isEmpty()) {
                continue;
            }
            if (code.length() > 0) {
                code.append('-');
            }
            code.append(bit);
        }
        if (code.length() == 0) {
            // nothing identifies the part: a stand-in that stays the same across imports
            String seed = (partType == null ? "" : partType) + '|' + (properties == null ? "" : properties);
            return "P-" + Integer.toHexString(seed.hashCode()).toUpperCase(Locale.ROOT);
        }
        return code.toString();
    }

    /**
     * The one letter (or short word) a part type is known by, so a resistor and a capacitor with the
     * same value do not end up under one code. Fritzing prints the value first for some parts
     * ({@code 10kΩ Resistor}), so every word of the part type is looked at, not just the first.
     *
     * @param partType the part type of the sketch
     * @return the prefix, {@code null} when the part type says nothing useful
     */
    private static String partKind(String partType) {
        if (partType == null || partType.isEmpty()) {
            return null;
        }
        String[] words = partType.trim().split("\\s+");
        for (String word : words) {
            String kind = kindOfWord(word);
            if (kind != null) {
                return kind;
            }
        }
        String cleaned = words[0].replaceAll("[^A-Za-z0-9-]", "");
        if (cleaned.isEmpty()) {
            return null;
        }
        return (cleaned.length() > 8 ? cleaned.substring(0, 8) : cleaned).toUpperCase(Locale.ROOT);
    }

    private static String kindOfWord(String word) {
        switch (word.toLowerCase(Locale.ROOT)) {
            case "resistor":
                return "R";
            case "capacitor":
                return "C";
            case "inductor":
            case "coil":
                return "L";
            case "diode":
            case "schottky":
                return "D";
            case "transistor":
            case "mosfet":
                return "Q";
            case "crystal":
                return "Y";
            case "switch":
            case "button":
                return "SW";
            case "connector":
            case "socket":
            case "header":
                return "J";
            case "battery":
                return "BT";
            case "relay":
                return "K";
            case "fuse":
                return "F";
            default:
                return null;
        }
    }

    /**
     * Normalises a value so it can go into a code: {@code 100 nF} becomes {@code 100NF},
     * {@code 10kΩ} becomes {@code 10K} and {@code 100Ω} becomes {@code 100R}.
     *
     * @param value the value as printed
     * @return the normalised value
     */
    static String unitOf(String value) {
        String text = value.replace(" ", "").replace("\u03BC", "u").replace("\u00B5", "u");
        boolean ohm = text.indexOf('\u03A9') >= 0;
        text = text.replace("\u03A9", "");
        if (ohm && !text.matches(".*[kKmMgG].*")) {
            text = text + "R";
        }
        return text.toUpperCase(Locale.ROOT);
    }

    /**
     * Turns a Fritzing package description into something a code can hold:
     * {@code [SMD] 0603} becomes {@code 0603}, {@code SOD323[SMD]} becomes {@code SOD323}. A
     * description that is longer than a code can carry is dropped.
     *
     * @param devicePackage the {@code 包} property
     * @return the package, {@code null} when there is none or it is too long
     */
    private static String packageOf(String devicePackage) {
        if (devicePackage == null) {
            return null;
        }
        String text = devicePackage.replaceAll("\\[[^\\]]*\\]", "").replace(" ", "").trim();
        if (text.isEmpty() || text.length() > 12 || text.indexOf(',') >= 0) {
            return null;
        }
        return text.toUpperCase(Locale.ROOT);
    }

    private static String firstNonEmpty(String... values) {
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) {
                return value.trim();
            }
        }
        return null;
    }

    private static String join(List<String> values) {
        StringBuilder out = new StringBuilder();
        for (String value : values) {
            if (value == null || value.isEmpty()) {
                continue;
            }
            if (out.length() > 0) {
                out.append(',');
            }
            out.append(value);
        }
        return out.toString();
    }

    private static String collapse(String text) {
        StringBuilder out = new StringBuilder(text.length());
        boolean space = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (Character.isWhitespace(c)) {
                space = out.length() > 0;
                continue;
            }
            if (space) {
                out.append(' ');
                space = false;
            }
            out.append(c);
        }
        return out.toString();
    }

    private static int indexOfIgnoreCase(String text, String needle, int from) {
        int last = text.length() - needle.length();
        for (int i = Math.max(0, from); i <= last; i++) {
            if (text.regionMatches(true, i, needle, 0, needle.length())) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Parses BOM CSV text into rows of
     * {@code componentCode, componentName, qty, designators, isKeyPart, unitPrice}.
     * Empty lines and lines starting with {@code #} are ignored; quotes are stripped.
     *
     * @param csv the CSV text
     * @return the parsed rows, never {@code null}
     */
    static List<String[]> parseBomCsv(String csv) {        List<String[]> rows = new ArrayList<>();
        if (csv == null) {
            return rows;
        }
        for (String line : csv.replace("\uFEFF", "").replace("\r\n", "\n").replace('\r', '\n').split("\n")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }
            String[] cells = splitCsvLine(trimmed);
            String code = unquote(cells[0]).toUpperCase(Locale.ROOT);
            if (code.isEmpty()) {
                continue;
            }
            String[] row = new String[6];
            row[0] = code;
            row[1] = cells.length > 1 ? unquote(cells[1]) : null;
            row[2] = cells.length > 2 ? unquote(cells[2]) : "1";
            row[3] = cells.length > 3 ? unquote(cells[3]) : null;
            row[4] = cells.length > 4 ? unquote(cells[4]) : "N";
            row[5] = cells.length > 5 ? unquote(cells[5]) : null;
            rows.add(row);
        }
        return rows;
    }

    /**
     * Splits one CSV line into cells, honouring double quoted cells which may contain commas.
     * Both a comma and a tab separate cells, so a range copied out of a spreadsheet can be pasted
     * straight into the import box.
     *
     * @param line the raw line
     * @return the cells, never {@code null}
     */
    public static String[] splitCsvLine(String line) {
        return splitCsvLine(line, ',');
    }

    /**
     * Splits one CSV line on a given separator, honouring double quoted cells.
     *
     * @param line the raw line
     * @param separator the cell separator, a comma also accepts a tab
     * @return the cells, never {@code null}
     */
    public static String[] splitCsvLine(String line, char separator) {
        List<String> cells = new ArrayList<>();
        StringBuilder cell = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (quoted) {
                if (c == '"') {
                    if (i + 1 < line.length() && line.charAt(i + 1) == '"') {
                        cell.append('"');
                        i++;
                    } else {
                        quoted = false;
                    }
                } else {
                    cell.append(c);
                }
            } else if (c == '"') {
                quoted = true;
            } else if (c == separator || (separator == ',' && c == '\t')) {
                cells.add(cell.toString());
                cell.setLength(0);
            } else {
                cell.append(c);
            }
        }
        cells.add(cell.toString());
        return cells.toArray(new String[cells.size()]);
    }

    static String unquote(String value) {
        if (value == null) {
            return "";
        }
        String text = value.trim();
        if (text.length() >= 2 && text.startsWith("\"") && text.endsWith("\"")) {
            text = text.substring(1, text.length() - 1).replace("\"\"", "\"").trim();
        }
        return text;
    }
}
