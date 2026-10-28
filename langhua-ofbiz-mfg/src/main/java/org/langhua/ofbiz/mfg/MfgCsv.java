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
        FRITZING("Fritzing");

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
