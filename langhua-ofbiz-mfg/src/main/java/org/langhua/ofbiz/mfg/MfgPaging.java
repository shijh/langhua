package org.langhua.ofbiz.mfg;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The list pages of the component: filtering by a keyword, cutting the rows into pages, and the paging
 * metadata every list service puts in its result (Step 164).
 *
 * <p>The screens share one footer ({@code pager.ftl}), so the arithmetic behind it is shared here: one
 * place to change the page sizes, the clamping and the fields the template reads.
 */
public final class MfgPaging {

    /** The rows one list page shows unless the person picks another size. */
    public static final long PAGE_SIZE = 20L;

    private MfgPaging() {
    }

    /**
     * Keeps the rows that contain the keyword in any of the given fields, case insensitively.
     *
     * @param rows the rows to filter
     * @param keyword the keyword, may be {@code null} or blank for no filtering
     * @param keys the fields to look at
     * @return the matching rows, or all rows when there is no keyword
     */
    static List<Map<String, Object>> filterByKeyword(List<Map<String, Object>> rows, String keyword,
            String... keys) {
        if (keyword == null || keyword.trim().isEmpty()) {
            return rows;
        }
        String needle = keyword.trim().toLowerCase(Locale.ROOT);
        List<Map<String, Object>> hits = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            for (String key : keys) {
                Object value = row.get(key);
                if (value != null && String.valueOf(value).toLowerCase(Locale.ROOT).contains(needle)) {
                    hits.add(row);
                    break;
                }
            }
        }
        return hits;
    }

    /**
     * Number of pages needed for the given number of rows, at least one.
     *
     * @param total the number of rows
     * @param pageSize the rows per page
     * @return the page count
     */
    static long pageCount(int total, long pageSize) {
        if (pageSize <= 0) {
            return 1;
        }
        return Math.max(1, (total + pageSize - 1) / pageSize);
    }

    /**
     * Clamps a requested page number into the available range.
     *
     * @param page the requested page, 1 based
     * @param total the number of rows
     * @param pageSize the rows per page
     * @return the page that will be shown
     */
    static long pageNumber(long page, int total, long pageSize) {
        long pages = pageCount(total, pageSize);
        if (page <= 0) {
            return 1;
        }
        return Math.min(page, pages);
    }

    /**
     * Returns one page of rows, clamped so an out of range page still shows something sensible.
     *
     * @param all all rows
     * @param page the requested page, 1 based
     * @param pageSize the rows per page
     * @param <T> the row type
     * @return the rows of that page
     */
    static <T> List<T> pageSlice(List<T> all, long page, long pageSize) {
        if (all.isEmpty()) {
            return new ArrayList<>();
        }
        long current = pageNumber(page, all.size(), pageSize);
        int from = (int) ((current - 1) * pageSize);
        int to = (int) Math.min((long) all.size(), from + pageSize);
        if (from >= all.size()) {
            return new ArrayList<>();
        }
        return new ArrayList<>(all.subList(from, to));
    }

    /**
     * Puts one page of rows into the service result together with the paging metadata the screens
     * need, so every list page shows the same footer.
     *
     * @param result the service result
     * @param key the key the rows are stored under
     * @param rows all rows, already filtered
     * @param page the requested page, 1 based
     * @param pageSize the rows per page
     * @param keyword the keyword that was used, echoed back to the form
     */
    static void putPage(Map<String, Object> result, String key, List<Map<String, Object>> rows,
            long page, long pageSize, String keyword) {
        result.put(key, pageSlice(rows, page, pageSize));
        result.put("totalCount", Integer.valueOf(rows.size()));
        result.put("page", Long.valueOf(pageNumber(page, rows.size(), pageSize)));
        result.put("pageCount", Long.valueOf(pageCount(rows.size(), pageSize)));
        result.put("pageSize", Long.valueOf(pageSize));
        result.put("search", keyword);
    }

    static List<Map<String, Object>> rowsOf(Map<String, Object> result, String key) {
        Object rows = result.get(key);
        return rows instanceof List ? (List<Map<String, Object>>) rows
                : new ArrayList<Map<String, Object>>();
    }

    /**
     * Reads the requested page from the service context.
     *
     * @param context the service context
     * @return the page number, 1 when unset
     */
    static long pageOf(Map<String, ? extends Object> context) {
        return MfgSupport.toLong(context.get("page"), 1);
    }

    /**
     * Reads the requested page size from the service context, capped at 200 rows.
     *
     * @param context the service context
     * @return the page size, {@link #PAGE_SIZE} when unset or out of range
     */
    static long pageSizeOf(Map<String, ? extends Object> context) {
        long size = MfgSupport.toLong(context.get("pageSize"), PAGE_SIZE);
        return size <= 0 || size > 200 ? PAGE_SIZE : size;
    }
}
