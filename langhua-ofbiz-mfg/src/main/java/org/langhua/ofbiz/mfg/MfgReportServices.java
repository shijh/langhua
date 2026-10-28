package org.langhua.ofbiz.mfg;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.Locale;
import java.util.Map;

import org.apache.ofbiz.entity.Delegator;
import org.apache.ofbiz.entity.GenericEntityException;
import org.apache.ofbiz.entity.GenericValue;
import org.apache.ofbiz.entity.util.EntityQuery;
import org.apache.ofbiz.service.DispatchContext;
import org.apache.ofbiz.service.ServiceUtil;

/**
 * What the shop looks at: the dashboard and its cards, the alert list that says what is late or
 * short, the yield and hours trends, the shortage page and the vendor list behind it.
 *
 * <p>The alerts are the one place that decides what counts as late (ERROR), what is only worth a
 * warning (WARN) and how the rows are ordered, so the dashboard and the CSV export cannot tell two
 * different stories. Moved out of {@code MfgServices} in Step 171.
 */
public final class MfgReportServices {

    private MfgReportServices() {
    }

    /**
     * Shop floor dashboard: today's output, yield and open work orders.
     *
     * @param dctx the dispatch context
     * @param context service context, see {@code servicedef/services.xml#mfgDashboard}
     * @return the service result containing the aggregated figures
     */
    public static Map<String, Object> dashboard(DispatchContext dctx, Map<String, ? extends Object> context) {
        Delegator delegator = dctx.getDelegator();
        boolean costDenied = !MfgServices.can(dctx, (GenericValue) context.get("userLogin"), MfgServices.PERM_COST);
        Timestamp todayStart = Timestamp.valueOf(LocalDate.now().atStartOfDay());
        try {
            List<GenericValue> orders = EntityQuery.use(delegator).from("MfgWorkOrder").queryList();
            long openOrders = 0;
            long plannedQty = 0;
            for (GenericValue order : orders) {
                String statusId = order.getString("statusId");
                if (!MfgServices.WO_CLOSED.equals(statusId) && !MfgServices.WO_COMPLETED.equals(statusId)) {
                    openOrders++;
                    plannedQty += order.getLong("plannedQty") == null ? 0 : order.getLong("plannedQty").longValue();
                }
            }
            List<GenericValue> reports = EntityQuery.use(delegator).from("MfgReport").queryList();
            double todayHours = 0.0;
            long todayOk = 0;
            long todayNg = 0;
            List<Map<String, Object>> recent = new ArrayList<>();
            List<GenericValue> sorted = new ArrayList<>(reports);
            sorted.sort(Comparator.comparing(
                    (GenericValue value) -> value.getTimestamp("reportDate"),
                    Comparator.nullsFirst(Comparator.naturalOrder())).reversed());
            for (GenericValue report : sorted) {
                Timestamp stamp = report.getTimestamp("reportDate");
                if (stamp != null && !stamp.before(todayStart)) {
                    todayHours += report.getDouble("workHours") == null ? 0.0 : report.getDouble("workHours").doubleValue();
                    todayOk += report.getLong("qtyOk") == null ? 0 : report.getLong("qtyOk").longValue();
                    todayNg += report.getLong("qtyNg") == null ? 0 : report.getLong("qtyNg").longValue();
                }
                if (recent.size() < 15) {
                    recent.add(MfgServices.toReportMap(report));
                }
            }
            long totalQty = todayOk + todayNg;
            double yieldRate = totalQty == 0 ? 0.0 : (double) todayOk / (double) totalQty;

            Map<String, Object> result = ServiceUtil.returnSuccess();
            result.put("openOrderCount", Long.valueOf(openOrders));
            result.put("openPlannedQty", Long.valueOf(plannedQty));
            result.put("todayHours", Double.valueOf(todayHours));
            result.put("todayQtyOk", Long.valueOf(todayOk));
            result.put("todayQtyNg", Long.valueOf(todayNg));
            result.put("todayYield", Double.valueOf(yieldRate));
            result.put("recentReports", recent);
            // the responsibility cost block is a cost view, not everybody may see it
            result.put("costDenied", costDenied ? "Y" : "N");
            return result;
        } catch (GenericEntityException e) {
            return ServiceUtil.returnError("Could not build the dashboard: " + e.getMessage());
        }
    }

    /**
     * Builds the yield and labour hour trend from the work reports, one row per period. The same
     * numbers feed the page and the two CSV files.
     *
     * @param dctx the dispatch context
     * @param context service context, see {@code servicedef/services.xml#mfgTrendReport}
     * @return the service result with the periods, their totals and the worst period
     */
    public static Map<String, Object> trendReport(DispatchContext dctx, Map<String, ? extends Object> context) {
        Delegator delegator = dctx.getDelegator();
        java.sql.Date toDate = (java.sql.Date) MfgSupport.parseDate(context.get("toDate"));
        if (toDate == null) {
            toDate = java.sql.Date.valueOf(LocalDate.now());
        }
        java.sql.Date fromDate = (java.sql.Date) MfgSupport.parseDate(context.get("fromDate"));
        if (fromDate == null) {
            fromDate = java.sql.Date.valueOf(toDate.toLocalDate().minusDays(179));
        }
        String groupBy = MfgSupport.toText(context.get("groupBy"));
        groupBy = groupBy == null ? "MONTH" : groupBy.toUpperCase(Locale.ROOT);
        if (!"DAY".equals(groupBy) && !"WEEK".equals(groupBy) && !"MONTH".equals(groupBy)) {
            groupBy = "MONTH";
        }
        Map<String, Object> result = ServiceUtil.returnSuccess();
        result.put("fromDate", fromDate);
        result.put("toDate", toDate);
        result.put("groupBy", groupBy);
        result.put("rows", new ArrayList<Map<String, Object>>());
        result.put("qtyOkTotal", Long.valueOf(0L));
        result.put("qtyNgTotal", Long.valueOf(0L));
        result.put("hoursTotal", Double.valueOf(0.0));
        result.put("yieldRate", Double.valueOf(0.0));
        result.put("hoursPerUnit", Double.valueOf(0.0));
        result.put("rowCount", Integer.valueOf(0));
        try {
            List<GenericValue> reports = EntityQuery.use(delegator).from("MfgReport")
                    .orderBy("reportDate").queryList();
            Map<String, double[]> buckets = new LinkedHashMap<>();
            Map<String, Set<String>> ordersOfBucket = new LinkedHashMap<>();
            double qtyOkTotal = 0.0;
            double qtyNgTotal = 0.0;
            double hoursTotal = 0.0;
            for (GenericValue report : reports) {
                Timestamp reportedAt = report.getTimestamp("reportDate");
                if (reportedAt == null) {
                    continue;
                }
                java.sql.Date reportedDate = new java.sql.Date(reportedAt.getTime());
                if (reportedDate.before(fromDate) || reportedDate.after(toDate)) {
                    continue;
                }
                String key = MfgSupport.periodKey(reportedDate, groupBy);
                double[] bucket = buckets.get(key);
                if (bucket == null) {
                    bucket = new double[3];
                    buckets.put(key, bucket);
                    ordersOfBucket.put(key, new LinkedHashSet<String>());
                }
                bucket[0] += MfgSupport.numberOrZero(report.get("qtyOk"));
                bucket[1] += MfgSupport.numberOrZero(report.get("qtyNg"));
                bucket[2] += MfgSupport.numberOrZero(report.get("workHours"));
                String workOrderId = report.getString("workOrderId");
                if (workOrderId != null) {
                    ordersOfBucket.get(key).add(workOrderId);
                }
                qtyOkTotal += MfgSupport.numberOrZero(report.get("qtyOk"));
                qtyNgTotal += MfgSupport.numberOrZero(report.get("qtyNg"));
                hoursTotal += MfgSupport.numberOrZero(report.get("workHours"));
            }
            List<Map<String, Object>> rows = new ArrayList<>(buckets.size());
            Map<String, Object> worst = null;
            for (Map.Entry<String, double[]> entry : buckets.entrySet()) {
                double[] bucket = entry.getValue();
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("period", entry.getKey());
                row.put("qtyOk", Double.valueOf(MfgSupport.round2(bucket[0])));
                row.put("qtyNg", Double.valueOf(MfgSupport.round2(bucket[1])));
                row.put("yieldRate", Double.valueOf(MfgSupport.yieldRate(bucket[0], bucket[1])));
                row.put("hours", Double.valueOf(MfgSupport.round2(bucket[2])));
                row.put("hoursPerUnit", Double.valueOf(MfgSupport.hoursPerUnit(bucket[2], bucket[0])));
                row.put("orderCount", Integer.valueOf(ordersOfBucket.get(entry.getKey()).size()));
                rows.add(row);
                if (bucket[0] + bucket[1] > 0.0
                        && (worst == null || ((Double) row.get("yieldRate")).doubleValue()
                                < ((Double) worst.get("yieldRate")).doubleValue())) {
                    worst = row;
                }
            }
            result.put("rows", rows);
            result.put("qtyOkTotal", Long.valueOf(Math.round(qtyOkTotal)));
            result.put("qtyNgTotal", Long.valueOf(Math.round(qtyNgTotal)));
            result.put("hoursTotal", Double.valueOf(MfgSupport.round2(hoursTotal)));
            result.put("yieldRate", Double.valueOf(MfgSupport.yieldRate(qtyOkTotal, qtyNgTotal)));
            result.put("hoursPerUnit", Double.valueOf(MfgSupport.hoursPerUnit(hoursTotal, qtyOkTotal)));
            result.put("rowCount", Integer.valueOf(rows.size()));
            if (worst != null) {
                result.put("worstRow", worst);
            }
            return result;
        } catch (GenericEntityException e) {
            return ServiceUtil.returnError("Could not build the trend report: " + e.getMessage());
        }
    }

    /**
     * Open work orders with their material shortages, ordered by due date.
     *
     * @param dctx the dispatch context
     * @param context service context, see {@code servicedef/services.xml#mfgShortageList}
     * @return the service result containing {@code orders}, {@code orderCount} and {@code shortageOrderCount}
     */
    public static Map<String, Object> shortageList(DispatchContext dctx, Map<String, ? extends Object> context) {
        Delegator delegator = dctx.getDelegator();
        try {
            List<GenericValue> orders = EntityQuery.use(delegator).from("MfgWorkOrder")
                    .orderBy("dueDate").queryList();
            List<Map<String, Object>> rows = new ArrayList<>();
            long shortageOrders = 0;
            for (GenericValue order : orders) {
                String statusId = order.getString("statusId");
                if (MfgServices.WO_CLOSED.equals(statusId) || MfgServices.WO_COMPLETED.equals(statusId)) {
                    continue;
                }
                List<Map<String, Object>> lines = MfgWorkOrderServices.kittingLines(delegator, order);
                List<Map<String, Object>> shortages = new ArrayList<>();
                double totalShortage = 0.0;
                for (Map<String, Object> line : lines) {
                    Double shortage = (Double) line.get("shortage");
                    if (shortage != null && shortage.doubleValue() > 0.0001) {
                        shortages.add(line);
                        totalShortage += shortage.doubleValue();
                    }
                }
                Map<String, Object> row = MfgServices.toWorkOrderMap(order);
                row.put("lineCount", Integer.valueOf(lines.size()));
                row.put("shortageLineCount", Integer.valueOf(shortages.size()));
                row.put("shortageQty", Double.valueOf(totalShortage));
                row.put("shortages", shortages);
                if (!shortages.isEmpty()) {
                    shortageOrders++;
                }
                rows.add(row);
            }
            Map<String, Object> result = ServiceUtil.returnSuccess();
            MfgPaging.putPage(result, "orders",
                    MfgPaging.filterByKeyword(rows, MfgSupport.toText(context.get("search")), "workOrderId", "productCode",
                            "customerName"),
                    MfgPaging.pageOf(context), MfgPaging.pageSizeOf(context), MfgSupport.toText(context.get("search")));
            result.put("orderCount", Integer.valueOf(rows.size()));
            result.put("shortageOrderCount", Long.valueOf(shortageOrders));
            return result;
        } catch (GenericEntityException e) {
            return ServiceUtil.returnError("Could not compute the material shortages: " + e.getMessage());
        }
    }

    /**
     * Creates or updates an outsourcing vendor, keeping the address and the customer code that has
     * to be printed on parcels of material.
     *
     * @param dctx the dispatch context
     * @param context service context, see {@code servicedef/services.xml#mfgVendorSave}
     * @return the service result containing {@code vendorId}
     */
    public static Map<String, Object> vendorSave(DispatchContext dctx, Map<String, ? extends Object> context) {
        String denied = MfgServices.permissionProblem(dctx, context, MfgServices.PERM_PRODUCTION);
        if (denied != null) {
            return ServiceUtil.returnError(denied);
        }
        Delegator delegator = dctx.getDelegator();
        String vendorName = MfgSupport.toText(context.get("vendorName"));
        if (vendorName == null) {
            return ServiceUtil.returnError("vendorName is required");
        }
        try {
            String vendorId = MfgSupport.toText(context.get("vendorId"));
            if (vendorId == null) {
                vendorId = nextVendorId(delegator, LocalDate.now());
            }
            GenericValue vendor = delegator.makeValue("MfgVendor");
            vendor.set("vendorId", vendorId);
            vendor.set("vendorName", vendorName);
            vendor.set("vendorType", MfgSupport.toText(context.get("vendorType")));
            vendor.set("contactName", MfgSupport.toText(context.get("contactName")));
            vendor.set("contactMobile", MfgSupport.toText(context.get("contactMobile")));
            vendor.set("addressText", MfgSupport.toText(context.get("addressText")));
            vendor.set("customerCode", MfgSupport.toText(context.get("customerCode")));
            String statusId = MfgSupport.toText(context.get("statusId"));
            vendor.set("statusId", statusId == null ? MfgServices.STATUS_ACTIVE : statusId);
            vendor.set("note", MfgSupport.toText(context.get("note")));
            delegator.createOrStore(vendor);

            Map<String, Object> result = ServiceUtil.returnSuccess();
            result.put("vendorId", vendorId);
            return result;
        } catch (GenericEntityException e) {
            return ServiceUtil.returnError("Could not save the vendor: " + e.getMessage());
        }
    }

    /**
     * Lists the outsourcing vendors.
     *
     * @param dctx the dispatch context
     * @param context service context, see {@code servicedef/services.xml#mfgVendorList}
     * @return the service result containing {@code vendors}
     */
    public static Map<String, Object> vendorList(DispatchContext dctx, Map<String, ? extends Object> context) {
        Delegator delegator = dctx.getDelegator();
        try {
            Map<String, Object> result = ServiceUtil.returnSuccess();
            MfgPaging.putPage(result, "vendors",
                    MfgPaging.filterByKeyword(MfgServices.vendorMaps(delegator),
                            MfgSupport.toText(context.get("search")), "vendorName",
                            "vendorType", "contactName", "contactMobile", "customerCode", "vendorId"),
                    MfgPaging.pageOf(context), MfgPaging.pageSizeOf(context), MfgSupport.toText(context.get("search")));
            return result;
        } catch (GenericEntityException e) {
            return ServiceUtil.returnError("Could not read the vendors: " + e.getMessage());
        }
    }

    static String formatVendorId(LocalDate date, int sequence) {
        return "V-" + date.format(MfgServices.DAY) + "-" + String.format(Locale.ROOT, "%02d", sequence);
    }

    static String nextVendorId(Delegator delegator, LocalDate today) throws GenericEntityException {
        String prefix = "V-" + today.format(MfgServices.DAY) + "-";
        List<GenericValue> existing = EntityQuery.use(delegator).from("MfgVendor").queryList();
        int max = 0;
        for (GenericValue vendor : existing) {
            String id = vendor.getString("vendorId");
            if (id != null && id.startsWith(prefix)) {
                max = Math.max(max, MfgServices.parseTrailingNumber(id.substring(prefix.length())));
            }
        }
        return formatVendorId(today, max + 1);
    }

    /**
     * Daily series and after sales KPI for the dashboard, rendered as plain HTML bars so the page
     * needs no chart library and works offline.
     *
     * @param dctx the dispatch context
     * @param context service context, see {@code servicedef/services.xml#mfgDashboardTrends}
     * @return the service result containing {@code kpi}, {@code days} and {@code responsibilities}
     */
    public static Map<String, Object> dashboardTrends(DispatchContext dctx, Map<String, ? extends Object> context) {
        Delegator delegator = dctx.getDelegator();
        long days = MfgSupport.toLong(context.get("dayCount"), 30);
        if (days <= 0 || days > 180) {
            days = 30;
        }
        LocalDate today = LocalDate.now();
        LocalDate start = today.minusDays(days - 1);
        java.sql.Date fromDate = java.sql.Date.valueOf(start);
        java.sql.Date toDate = java.sql.Date.valueOf(today);
        try {
            Map<String, Long> shippedByDay = new LinkedHashMap<>();
            Map<String, Long> claimsByDay = new LinkedHashMap<>();
            for (String label : lastDayLabels(today, (int) days)) {
                shippedByDay.put(label, Long.valueOf(0));
                claimsByDay.put(label, Long.valueOf(0));
            }

            long shippedUnits = 0;
            for (GenericValue delivery : EntityQuery.use(delegator).from("MfgDelivery").queryList()) {
                java.sql.Date deliveryDate = delivery.getDate("deliveryDate");
                if (deliveryDate == null || deliveryDate.before(fromDate) || deliveryDate.after(toDate)) {
                    continue;
                }
                String note = delivery.getString("note");
                if (note != null && note.startsWith("换货")) {
                    continue;
                }
                String label = deliveryDate.toLocalDate().format(MfgServices.MONTH_DAY);
                if (!shippedByDay.containsKey(label)) {
                    continue;
                }
                long units = EntityQuery.use(delegator).from("MfgDeliverySn")
                        .where("deliveryId", delivery.getString("deliveryId")).queryList().size();
                shippedByDay.put(label, Long.valueOf(shippedByDay.get(label).longValue() + units));
                shippedUnits += units;
            }

            long claimCount = 0;
            long openClaims = 0;
            double refundTotal = 0.0;
            double hoursTotal = 0.0;
            double costTotal = 0.0;
            Map<String, Double> costByResponsibility = new LinkedHashMap<>();
            Map<String, Long> countByResponsibility = new LinkedHashMap<>();
            for (GenericValue claim : EntityQuery.use(delegator).from("MfgWarrantyClaim").queryList()) {
                if (MfgServices.RMA_OPEN.equals(claim.getString("statusId"))) {
                    openClaims++;
                }
                java.sql.Date receivedDate = claim.getDate("receivedDate");
                if (receivedDate == null || receivedDate.before(fromDate) || receivedDate.after(toDate)) {
                    continue;
                }
                claimCount++;
                String label = receivedDate.toLocalDate().format(MfgServices.MONTH_DAY);
                if (claimsByDay.containsKey(label)) {
                    claimsByDay.put(label, Long.valueOf(claimsByDay.get(label).longValue() + 1));
                }
                double refund = claim.getDouble("refundAmount") == null
                        ? 0.0 : claim.getDouble("refundAmount").doubleValue();
                double hours = claim.getDouble("repairHours") == null
                        ? 0.0 : claim.getDouble("repairHours").doubleValue();
                double freight = MfgServices.FREIGHT_SELLER.equals(claim.getString("freightPayer"))
                        ? MfgServices.QUALITY_FREIGHT_COST : 0.0;
                double cost = claimCost(refund, freight, hours, MfgServices.QUALITY_HOURLY_RATE);
                refundTotal += refund;
                hoursTotal += hours;
                costTotal += cost;
                String responsibility = claim.getString("responsibility");
                if (responsibility == null) {
                    responsibility = "-";
                }
                Double current = costByResponsibility.get(responsibility);
                costByResponsibility.put(responsibility, Double.valueOf(current == null ? cost
                        : current.doubleValue() + cost));
                Long times = countByResponsibility.get(responsibility);
                countByResponsibility.put(responsibility, Long.valueOf(times == null ? 1
                        : times.longValue() + 1));
            }

            long maxShipped = 1;
            long maxClaims = 1;
            for (Long value : shippedByDay.values()) {
                maxShipped = Math.max(maxShipped, value.longValue());
            }
            for (Long value : claimsByDay.values()) {
                maxClaims = Math.max(maxClaims, value.longValue());
            }
            List<Map<String, Object>> dayRows = new ArrayList<>(shippedByDay.size());
            for (Map.Entry<String, Long> entry : shippedByDay.entrySet()) {
                Map<String, Object> row = new LinkedHashMap<>();
                long shipped = entry.getValue().longValue();
                Long claims = claimsByDay.get(entry.getKey());
                long claimValue = claims == null ? 0 : claims.longValue();
                row.put("date", entry.getKey());
                row.put("shipped", Long.valueOf(shipped));
                row.put("claims", Long.valueOf(claimValue));
                row.put("shipPct", Long.valueOf(MfgSupport.percentOfMax(shipped, maxShipped)));
                row.put("claimPct", Long.valueOf(MfgSupport.percentOfMax(claimValue, maxClaims)));
                dayRows.add(row);
            }

            double maxCost = 1.0;
            for (Double value : costByResponsibility.values()) {
                maxCost = Math.max(maxCost, value.doubleValue());
            }
            List<Map<String, Object>> responsibilityRows = new ArrayList<>();
            for (Map.Entry<String, Double> entry : costByResponsibility.entrySet()) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("responsibility", entry.getKey());
                row.put("claimCount", countByResponsibility.get(entry.getKey()));
                row.put("cost", entry.getValue());
                row.put("pct", Long.valueOf(Math.round(entry.getValue().doubleValue() * 100.0 / maxCost)));
                responsibilityRows.add(row);
            }
            responsibilityRows.sort(new Comparator<Map<String, Object>>() {
                @Override
                public int compare(Map<String, Object> left, Map<String, Object> right) {
                    return ((Double) right.get("cost")).compareTo((Double) left.get("cost"));
                }
            });

            Map<String, Object> kpi = new LinkedHashMap<>();
            kpi.put("shippedUnits", Long.valueOf(shippedUnits));
            kpi.put("claimCount", Long.valueOf(claimCount));
            kpi.put("openClaims", Long.valueOf(openClaims));
            kpi.put("claimRate", MfgSupport.percent(claimCount, shippedUnits));
            kpi.put("refundTotal", Double.valueOf(refundTotal));
            kpi.put("hoursTotal", Double.valueOf(hoursTotal));
            kpi.put("costTotal", Double.valueOf(costTotal));
            kpi.put("costPerUnit", Double.valueOf(shippedUnits == 0 ? 0.0 : costTotal / shippedUnits));

            Map<String, Object> result = ServiceUtil.returnSuccess();
            result.put("kpi", kpi);
            result.put("days", dayRows);
            result.put("responsibilities", responsibilityRows);
            result.put("fromDate", fromDate);
            result.put("toDate", toDate);
            result.put("refreshedAt", java.time.LocalTime.now()
                    .format(DateTimeFormatter.ofPattern("HH:mm:ss", Locale.ROOT)));
            return result;
        } catch (GenericEntityException e) {
            return ServiceUtil.returnError("Could not build the dashboard series: " + e.getMessage());
        }
    }

    /**
     * Collects what the shop floor should look at today: work orders with material shortages, and
     * open claims whose 7 day or 15 day window is running out or has already passed.
     *
     * @param dctx the dispatch context
     * @param context service context, see {@code servicedef/services.xml#mfgAlerts}
     * @return the service result containing {@code alerts} (worst first) and their counts
     */
    public static Map<String, Object> alerts(DispatchContext dctx, Map<String, ? extends Object> context) {
        Delegator delegator = dctx.getDelegator();
        try {
            List<Map<String, Object>> alerts = buildAlerts(delegator);
            int errors = 0;
            int shortages = 0;
            for (Map<String, Object> alert : alerts) {
                if (MfgServices.ALERT_ERROR.equals(alert.get("level"))) {
                    errors++;
                }
                if (MfgServices.ALERT_SHORTAGE.equals(alert.get("typeId"))) {
                    shortages++;
                }
            }
            Map<String, Object> result = ServiceUtil.returnSuccess();
            result.put("alerts", alerts);
            result.put("alertCount", Integer.valueOf(alerts.size()));
            result.put("errorCount", Integer.valueOf(errors));
            result.put("shortageCount", Integer.valueOf(shortages));
            result.put("claimCount", Integer.valueOf(alerts.size() - shortages));
            return result;
        } catch (GenericEntityException e) {
            return ServiceUtil.returnError("Could not compute the alerts: " + e.getMessage());
        }
    }

    /**
     * Builds the dashboard alerts: one per work order with a shortage, and one per open claim window
     * that is about to close or already closed.
     *
     * @param delegator the delegator
     * @return the alerts, already first and then the closest deadline first
     * @throws GenericEntityException when the data cannot be read
     */
    static List<Map<String, Object>> buildAlerts(Delegator delegator) throws GenericEntityException {
        List<Map<String, Object>> alerts = new ArrayList<>();

        Map<String, List<Map<String, Object>>> byOrder = new LinkedHashMap<>();
        for (Map<String, Object> row : shortageRows(delegator)) {
            String workOrderId = String.valueOf(row.get("workOrderId"));
            List<Map<String, Object>> rows = byOrder.get(workOrderId);
            if (rows == null) {
                rows = new ArrayList<>();
                byOrder.put(workOrderId, rows);
            }
            rows.add(row);
        }
        for (Map.Entry<String, List<Map<String, Object>>> entry : byOrder.entrySet()) {
            List<Map<String, Object>> rows = entry.getValue();
            Map<String, Object> first = rows.get(0);
            double total = 0.0;
            List<String> codes = new ArrayList<>();
            for (Map<String, Object> row : rows) {
                total += MfgSupport.numberOrZero(row.get("shortage"));
                if (codes.size() < 3) {
                    codes.add(String.valueOf(row.get("componentCode")));
                }
            }
            java.sql.Date dueDate = (java.sql.Date) first.get("dueDate");
            long dueHours = 0L;
            if (dueDate != null) {
                dueHours = MfgServices.hoursLeft(java.sql.Timestamp.valueOf(dueDate.toLocalDate().atTime(23, 59, 59)));
            }
            Map<String, Object> alert = new LinkedHashMap<>();
            alert.put("typeId", MfgServices.ALERT_SHORTAGE);
            alert.put("level", MfgServices.ALERT_ERROR);
            alert.put("refId", entry.getKey());
            alert.put("subject", first.get("productCode"));
            alert.put("dueDate", dueDate);
            alert.put("hoursLeft", Long.valueOf(dueHours));
            alert.put("detail", rows.size() + " 行欠料，共 " + MfgSupport.formatQty(total)
                    + "；" + String.join("、", codes) + (rows.size() > codes.size() ? " 等" : ""));
            alerts.add(alert);
        }

        List<GenericValue> openClaims = EntityQuery.use(delegator).from("MfgWarrantyClaim")
                .where("statusId", MfgServices.RMA_OPEN).orderBy("sevenDayDeadline").queryList();
        for (GenericValue claim : openClaims) {
            addClaimAlert(alerts, claim, "sevenDayDeadline", MfgServices.ALERT_CLAIM_7DAY);
            addClaimAlert(alerts, claim, "afterSaleDeadline", MfgServices.ALERT_CLAIM_AFTERSALE);
        }

        addSoftwareAlerts(alerts, delegator);
        addStockAlerts(alerts, delegator);

        alerts.sort(new Comparator<Map<String, Object>>() {
            @Override
            public int compare(Map<String, Object> left, Map<String, Object> right) {
                String leftLevel = String.valueOf(left.get("level"));
                String rightLevel = String.valueOf(right.get("level"));
                if (!leftLevel.equals(rightLevel)) {
                    return MfgServices.ALERT_ERROR.equals(leftLevel) ? -1 : 1;
                }
                return ((Long) left.get("hoursLeft")).compareTo((Long) right.get("hoursLeft"));
            }
        });
        return alerts;
    }

    /**
     * Adds the alert of one claim window when it is inside the warning window or already passed.
     *
     * @param alerts the list to add to
     * @param claim the claim
     * @param deadlineField the field holding the deadline
     * @param typeId the alert type
     */
    static void addClaimAlert(List<Map<String, Object>> alerts, GenericValue claim,
            String deadlineField, String typeId) {
        java.sql.Timestamp deadline = claim.getTimestamp(deadlineField);
        if (deadline == null) {
            return;
        }
        long hours = MfgServices.hoursLeft(deadline);
        if (hours > MfgServices.DEADLINE_WARNING_HOURS) {
            return;
        }
        StringBuilder detail = new StringBuilder(MfgServices.ALERT_CLAIM_7DAY.equals(typeId)
                ? "7 天无理由窗口" : "15 天售后窗口");
        detail.append(hours < 0 ? "已经过期 " + (-hours) + " 小时" : "还有 " + hours + " 小时");
        if (claim.getString("claimType") != null) {
            detail.append("；").append(claim.getString("claimType"));
        }
        if (claim.getString("tradeNo") != null) {
            detail.append("；订单 ").append(claim.getString("tradeNo"));
        }
        Map<String, Object> alert = new LinkedHashMap<>();
        alert.put("typeId", typeId);
        alert.put("level", hours < 0 ? MfgServices.ALERT_ERROR : MfgServices.ALERT_WARN);
        alert.put("refId", claim.getString("claimId"));
        alert.put("subject", claim.getString("sn"));
        alert.put("dueDate", deadline);
        alert.put("hoursLeft", Long.valueOf(hours));
        alert.put("detail", detail.toString());
        alerts.add(alert);
    }

    /**
     * Adds one alert per open software project that is late or has already used more person days than
     * the estimate gave.
     *
     * @param alerts the list to add to
     * @param delegator the delegator
     * @throws GenericEntityException when the projects cannot be read
     */
    static void addSoftwareAlerts(List<Map<String, Object>> alerts, Delegator delegator)
            throws GenericEntityException {
        List<GenericValue> projects = EntityQuery.use(delegator).from("MfgSoftwareProject")
                .orderBy("plannedEndDate").queryList();
        LocalDate today = LocalDate.now();
        for (GenericValue project : projects) {
            String statusId = project.getString("statusId");
            if (MfgServices.SW_DONE.equals(statusId) || MfgServices.SW_CANCELLED.equals(statusId)) {
                continue;
            }
            double planned = MfgSupport.numberOrZero(project.get("plannedDays"));
            double actualDays = MfgSupport.numberOrZero(project.get("actualHours")) / MfgServices.HOURS_PER_DAY;
            boolean overEffort = planned > 0.0 && actualDays > planned;
            java.sql.Date end = project.getDate("plannedEndDate");
            Long daysLeft = end == null ? null
                    : Long.valueOf(java.time.temporal.ChronoUnit.DAYS.between(today, end.toLocalDate()));
            boolean late = daysLeft != null && daysLeft.longValue() < 0L;
            boolean soon = daysLeft != null && daysLeft.longValue() >= 0L && daysLeft.longValue() <= 3L;
            if (!overEffort && !late && !soon) {
                continue;
            }
            StringBuilder detail = new StringBuilder();
            if (planned > 0.0) {
                detail.append("已用 ").append(MfgSupport.formatQty(actualDays)).append('/')
                        .append(MfgSupport.formatQty(planned)).append(" 人天");
            }
            if (daysLeft != null) {
                if (detail.length() > 0) {
                    detail.append("；");
                }
                detail.append(daysLeft.longValue() < 0L
                        ? "计划结束日已过 " + (-daysLeft.longValue()) + " 天"
                        : "距计划结束 " + daysLeft.longValue() + " 天");
            }
            Map<String, Object> alert = new LinkedHashMap<>();
            alert.put("typeId", MfgServices.ALERT_SOFTWARE);
            alert.put("level", overEffort || late ? MfgServices.ALERT_ERROR : MfgServices.ALERT_WARN);
            alert.put("refId", project.getString("projectCode"));
            alert.put("subject", project.getString("projectName"));
            alert.put("dueDate", end);
            alert.put("hoursLeft", daysLeft == null ? Long.valueOf(0L)
                    : Long.valueOf(daysLeft.longValue() * 24L));
            alert.put("detail", detail.toString());
            alerts.add(alert);
        }
    }

    /**
     * Flattens the shortages of every open work order into one row per component, ordered by due
     * date, so the page and the CSV export tell the same numbers.
     *
     * @param delegator the delegator
     * @return one row per component that is short, empty when everything is kitted
     * @throws GenericEntityException when the data cannot be read
     */
    static List<Map<String, Object>> shortageRows(Delegator delegator) throws GenericEntityException {
        List<Map<String, Object>> rows = new ArrayList<>();
        List<GenericValue> orders = EntityQuery.use(delegator).from("MfgWorkOrder")
                .orderBy("dueDate").queryList();
        for (GenericValue order : orders) {
            String statusId = order.getString("statusId");
            if (MfgServices.WO_CLOSED.equals(statusId) || MfgServices.WO_COMPLETED.equals(statusId)) {
                continue;
            }
            for (Map<String, Object> line : MfgWorkOrderServices.kittingLines(delegator, order)) {
                double shortage = MfgSupport.numberOrZero(line.get("shortage"));
                if (shortage <= 0.0001) {
                    continue;
                }
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("workOrderId", order.getString("workOrderId"));
                row.put("productCode", order.getString("productCode"));
                row.put("dueDate", order.getDate("dueDate"));
                row.put("customerName", order.getString("customerName"));
                row.put("componentCode", line.get("componentCode"));
                row.put("componentName", line.get("componentName"));
                row.put("requiredQty", line.get("requiredQty"));
                row.put("issuedQty", line.get("issuedQty"));
                row.put("shortage", Double.valueOf(shortage));
                rows.add(row);
            }
        }
        return rows;
    }

    /**
     * Adds one alert per component whose stock dropped below its safety stock.
     *
     * @param alerts the alert list to append to
     * @param delegator the delegator
     * @throws GenericEntityException when the stock cannot be read
     */
    static void addStockAlerts(List<Map<String, Object>> alerts, Delegator delegator)
            throws GenericEntityException {
        Map<String, GenericValue> settings = new LinkedHashMap<>();
        for (GenericValue setting : EntityQuery.use(delegator).from("MfgStockSetting").queryList()) {
            settings.put(setting.getString("componentCode"), setting);
        }
        if (settings.isEmpty()) {
            return;
        }
        Map<String, Double> onHand = new LinkedHashMap<>();
        for (GenericValue lot : MfgStockLedger.componentLots(delegator)) {
            MfgStockLedger.ensureLotLedger(delegator, lot);
            String componentCode = lot.getString("componentCode");
            if (componentCode == null) {
                continue;
            }
            onHand.put(componentCode, Double.valueOf(MfgSupport.numberOrZero(onHand.get(componentCode))
                    + MfgSupport.numberOrZero(lot.get("onHandQty"))));
        }
        for (Map.Entry<String, GenericValue> entry : settings.entrySet()) {
            double minimum = MfgSupport.numberOrZero(entry.getValue().get("minimumStock"));
            if (minimum <= 0.0) {
                continue;
            }
            double current = MfgSupport.numberOrZero(onHand.get(entry.getKey()));
            if (current >= minimum) {
                continue;
            }
            double reorder = MfgSupport.numberOrZero(entry.getValue().get("reorderQty"));
            double suggested = reorder > 0.0 ? reorder : MfgSupport.round2(minimum - current);
            Map<String, Object> alert = new LinkedHashMap<>();
            alert.put("typeId", MfgServices.ALERT_STOCK);
            alert.put("level", current <= 0.0 ? MfgServices.ALERT_ERROR : MfgServices.ALERT_WARN);
            alert.put("refId", entry.getKey());
            alert.put("subject", entry.getValue().getString("componentName"));
            alert.put("dueDate", null);
            alert.put("hoursLeft", Long.valueOf(0L));
            alert.put("detail", "现有 " + MfgSupport.formatQty(current) + " / 安全库存 " + MfgSupport.formatQty(minimum)
                    + "，建议采购 " + MfgSupport.formatQty(suggested)
                    + (entry.getValue().getString("supplierName") == null ? ""
                            : "（" + entry.getValue().getString("supplierName") + "）"));
            alerts.add(alert);
        }
    }

    /**
     * Labels of the last {@code days} days, oldest first, as used on the dashboard axis.
     *
     * @param end the last day
     * @param days how many days to list
     * @return the labels in {@code MM-dd} form
     */
    static List<String> lastDayLabels(LocalDate end, int days) {
        List<String> labels = new ArrayList<>();
        if (end == null || days <= 0) {
            return labels;
        }
        for (int i = days - 1; i >= 0; i--) {
            labels.add(end.minusDays(i).format(MfgServices.MONTH_DAY));
        }
        return labels;
    }

    /**
     * Money one claim cost us: the refund, the freight we paid and the repair labour.
     *
     * @param refund amount refunded
     * @param freight freight we paid
     * @param hours repair hours
     * @param hourlyRate labour cost per hour
     * @return the cost
     */
    static double claimCost(double refund, double freight, double hours, double hourlyRate) {
        return refund + freight + hours * hourlyRate;
    }
}
