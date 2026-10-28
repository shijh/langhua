package org.langhua.ofbiz.mfg;

import java.sql.Timestamp;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The small helpers every service of this component shares: reading text and numbers, rounding money,
 * dates, percentages and the bucket labels of the reports.
 *
 * <p>They live here instead of in {@code MfgServices} so that file can be about the services it
 * declares - it held 14,600 lines and one method in fifteen was one of these (Step 164). Nothing here
 * touches the database or a dispatch context, so they are the part the unit tests can check on their
 * own, and a service cannot hide a mistake in them.
 */
public final class MfgSupport {

    private MfgSupport() {
    }

    /**
     * Turns a date into the label of the bucket it belongs to.
     *
     * @param date the date, may be {@code null}
     * @param groupBy {@code DAY}, {@code WEEK} or {@code MONTH}
     * @return the label, e.g. {@code 2026-10-21}, {@code 2026-W43} or {@code 2026-10}
     */
    static String periodKey(java.sql.Date date, String groupBy) {
        if (date == null) {
            return "-";
        }
        LocalDate day = date.toLocalDate();
        if ("DAY".equals(groupBy)) {
            return day.toString();
        }
        if ("WEEK".equals(groupBy)) {
            int year = day.get(java.time.temporal.IsoFields.WEEK_BASED_YEAR);
            int week = day.get(java.time.temporal.IsoFields.WEEK_OF_WEEK_BASED_YEAR);
            return String.format(Locale.ROOT, "%04d-W%02d",
                    Integer.valueOf(year), Integer.valueOf(week));
        }
        return String.format(Locale.ROOT, "%04d-%02d",
                Integer.valueOf(day.getYear()), Integer.valueOf(day.getMonthValue()));
    }

    /**
     * Yield as a percentage of everything that was reported.
     *
     * @param qtyOk good quantity
     * @param qtyNg rejected quantity
     * @return the yield in percent, {@code 0} when nothing was reported
     */
    static double yieldRate(double qtyOk, double qtyNg) {
        double total = qtyOk + qtyNg;
        return total <= 0.0 ? 0.0 : round2(qtyOk * 100.0 / total);
    }

    /**
     * Hours spent per good unit.
     *
     * @param hours reported hours
     * @param qtyOk good quantity
     * @return the hours per unit, {@code 0} when there is no good unit
     */
    static double hoursPerUnit(double hours, double qtyOk) {
        return qtyOk <= 0.0 ? 0.0 : round2(hours / qtyOk);
    }

    static double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    static double numberOrZero(Object value) {
        return value instanceof Number ? ((Number) value).doubleValue() : 0.0;
    }

    /**
     * Reads an optional amount: an empty or unparsable value stays {@code null} instead of zero, so
     * the cost report can tell "no price yet" from "free".
     *
     * @param value the raw input
     * @return the amount, or {@code null} when there is none
     */
    static Double optionalAmount(Object value) {
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

    /**
     * Formats a quantity for the short texts of the alerts: at most three decimals and a dot as the
     * decimal separator, whatever the server locale is.
     *
     * @param value the quantity
     * @return the formatted quantity
     */
    static String formatQty(double value) {
        DecimalFormat format = new DecimalFormat("0.###",
                DecimalFormatSymbols.getInstance(Locale.ROOT));
        return format.format(value);
    }

    /**
     * Median of the samples, used as the productivity baseline of the finished projects.
     *
     * @param values the samples
     * @return the median, {@code 0} when there is no sample
     */
    static double median(List<Double> values) {
        if (values == null || values.isEmpty()) {
            return 0.0;
        }
        List<Double> sorted = new ArrayList<>(values);
        java.util.Collections.sort(sorted);
        int size = sorted.size();
        if (size % 2 == 1) {
            return sorted.get(size / 2).doubleValue();
        }
        return (sorted.get(size / 2 - 1).doubleValue() + sorted.get(size / 2).doubleValue()) / 2.0;
    }

    /**
     * Scales a value against the largest one so a bar can be drawn without any chart library.
     *
     * @param value the value
     * @param max the largest value of the series
     * @return the percentage of the largest value, 0 for zero and at least 2 for a non zero value
     */
    static long percentOfMax(long value, long max) {
        if (value <= 0) {
            return 0;
        }
        if (max <= 0) {
            return 100;
        }
        long pct = Math.round(value * 100.0 / max);
        return Math.max(2, pct);
    }

    /**
     * Formats a ratio as a percentage with one decimal, for the reports.
     *
     * @param part the numerator
     * @param whole the denominator
     * @return the percentage, or {@code "-"} when the denominator is zero
     */
    static String percent(long part, long whole) {
        if (whole <= 0) {
            return "-";
        }
        return percentOf(part, whole);
    }

    /**
     * Formats a money ratio as a percentage with one decimal.
     *
     * @param part the numerator
     * @param whole the denominator
     * @return the percentage, or {@code "-"} when the denominator is zero
     */
    static String percentOf(double part, double whole) {
        if (whole <= 0.0) {
            return "-";
        }
        return String.format(Locale.ROOT, "%.1f", part * 100.0 / whole);
    }

    /**
     * Days between two dates, used to age a claim.
     *
     * @param from the earlier date, usually the delivery date
     * @param to the later date, usually the date the unit came back
     * @return the days, or {@code -1} when either date is missing
     */
    static int daysBetween(java.sql.Date from, java.sql.Date to) {
        if (from == null || to == null) {
            return -1;
        }
        return (int) java.time.temporal.ChronoUnit.DAYS.between(from.toLocalDate(), to.toLocalDate());
    }

    /**
     * Buckets the age of a claim, so early failures can be told apart from wear.
     *
     * @param days days between delivery and return
     * @return the bucket label
     */
    static String ageBucket(int days) {
        if (days < 0) {
            return "未知";
        }
        if (days <= 30) {
            return "0-30";
        }
        if (days <= 90) {
            return "31-90";
        }
        if (days <= 180) {
            return "91-180";
        }
        return ">180";
    }

    static Map<String, Object> newMap() {
        return new HashMap<>();
    }

    static Object parseDate(Object value) {
        String text = toText(value);
        if (text == null) {
            return null;
        }
        try {
            return java.sql.Date.valueOf(LocalDate.parse(text));
        } catch (RuntimeException e) {
            return null;
        }
    }

    /**
     * Parses an optional day and time, used by the movements of the inventory ledger.
     *
     * @param value the raw input, e.g. {@code 2026-10-21} or {@code 2026-10-21T14:30:00}
     * @return the timestamp, {@code null} when there is none or it cannot be read
     */
    static Timestamp parseTimestamp(Object value) {
        String text = toText(value);
        if (text == null) {
            return null;
        }
        try {
            if (text.length() <= 10) {
                return Timestamp.valueOf(LocalDate.parse(text).atStartOfDay());
            }
            return Timestamp.valueOf(java.time.LocalDateTime.parse(text.replace(' ', 'T')));
        } catch (RuntimeException e) {
            return null;
        }
    }

    static long toLong(Object value, long defaultValue) {
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        if (value instanceof String) {
            try {
                String text = ((String) value).trim();
                return text.isEmpty() ? defaultValue : Math.round(Double.parseDouble(text));
            } catch (NumberFormatException e) {
                return defaultValue;
            }
        }
        return defaultValue;
    }

    static double toDouble(Object value, double defaultValue) {
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

    static String toText(Object value) {
        if (value == null) {
            return null;
        }
        String text = value.toString().trim();
        return text.isEmpty() ? null : text;
    }

    /**
     * Hours left until a deadline, negative when the deadline already passed.
     *
     * @param deadline the deadline
     * @return whole hours left, {@code 0} when there is no deadline
     */
    static long hoursLeft(java.sql.Timestamp deadline) {
        return hoursLeft(deadline, LocalDateTime.now());
    }

    /**
     * Hours left until a deadline relative to a given moment, negative when it already passed.
     *
     * @param deadline the deadline
     * @param now the moment to compare with
     * @return whole hours left, rounded down, {@code 0} when there is no deadline
     */
    static long hoursLeft(java.sql.Timestamp deadline, LocalDateTime now) {
        if (deadline == null || now == null) {
            return 0L;
        }
        long minutes = java.time.Duration.between(now, deadline.toLocalDateTime()).toMinutes();
        return minutes >= 0 ? minutes / 60 : -((-minutes + 59) / 60);
    }

    static int parseTrailingNumber(String text) {
        try {
            return Integer.parseInt(text.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
