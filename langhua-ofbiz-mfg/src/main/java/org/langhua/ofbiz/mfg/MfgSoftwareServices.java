package org.langhua.ofbiz.mfg;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.apache.ofbiz.entity.Delegator;
import org.apache.ofbiz.entity.GenericEntityException;
import org.apache.ofbiz.entity.GenericValue;
import org.apache.ofbiz.entity.util.EntityQuery;
import org.apache.ofbiz.service.DispatchContext;
import org.apache.ofbiz.service.ServiceUtil;

/**
 * Software projects: the function point cost model, the ledger of planned versus actual effort,
 * and the productivity the finished projects show.
 *
 * <p>The model is pure arithmetic ({@code softwareCost}), so the quote screen and the ledger can
 * never tell two different numbers, and the unit tests can check it without a database. A project
 * freezes the planned person days when it is saved, so changing a parameter later does not rewrite
 * history.
 *
 * <p>Moved out of {@code MfgServices} in Step 166.
 */
public final class MfgSoftwareServices {

    private MfgSoftwareServices() {
    }

    /**
     * The software cost model of the assessment standard: size, effort, schedule and price.
     *
     * <p>{@code UFP = 35 * ILF + 15 * EIF} (estimated function points), {@code AE = PDR * UFP * SWF * RDF}
     * in hours, {@code D = 1.277 * (AE / 176)^0.404} in months and the price is the person days times
     * the day rate, plus VAT and the direct non labour cost.
     *
     * <p>Pure, so the arithmetic can be tested without a database and the quote screen and the ledger
     * always tell the same numbers.
     *
     * @param ilf internal logical files
     * @param eif external interface files
     * @param ufp function points when they are known, otherwise {@code null} and ILF / EIF are used
     * @param swfBase software factor starting value
     * @param swfReuse adjustment for reuse and experience (usually negative)
     * @param swfPreInvest adjustment for design work already paid for
     * @param swfQuality adjustment for a certified quality system
     * @param rdf development factor, 1 when nothing special is required
     * @param pdr hours per function point
     * @param dayRate price of one person day before tax
     * @param taxRate VAT rate
     * @param dncRate direct non labour cost as a share of the taxed amount
     * @return the computed values, keyed by {@code ufp}, {@code swf}, {@code effortHours},
     *         {@code effortDays}, {@code scheduleMonths}, {@code amountBeforeTax}, {@code taxAmount},
     *         {@code amountWithTax}, {@code dncAmount}, {@code totalAmount} and {@code costPerFp}
     */
    static Map<String, Object> softwareCost(double ilf, double eif, Double ufp, double swfBase,
            double swfReuse, double swfPreInvest, double swfQuality, double rdf, double pdr,
            double dayRate, double taxRate, double dncRate) {
        double size = ufp == null ? 35.0 * ilf + 15.0 * eif : ufp.doubleValue();
        double swf = swfBase + swfReuse + swfPreInvest + swfQuality;
        double effort = pdr * size * swf * rdf;
        double days = effort / MfgServices.HOURS_PER_DAY;
        double months = effort <= 0.0 ? 0.0
                : MfgServices.SCHEDULE_FACTOR
                        * Math.pow(effort / MfgServices.HOURS_PER_MONTH, MfgServices.SCHEDULE_EXPONENT);
        double beforeTax = days * dayRate;
        double tax = beforeTax * taxRate;
        double withTax = beforeTax + tax;
        double dnc = withTax * dncRate;

        Map<String, Object> map = new LinkedHashMap<>();
        map.put("ufp", Double.valueOf(MfgSupport.round2(size)));
        map.put("swf", Double.valueOf(MfgSupport.round2(swf)));
        map.put("effortHours", Double.valueOf(MfgSupport.round2(effort)));
        map.put("effortDays", Double.valueOf(MfgSupport.round2(days)));
        map.put("scheduleMonths", Double.valueOf(MfgSupport.round2(months)));
        map.put("amountBeforeTax", Double.valueOf(MfgSupport.round2(beforeTax)));
        map.put("taxAmount", Double.valueOf(MfgSupport.round2(tax)));
        map.put("amountWithTax", Double.valueOf(MfgSupport.round2(withTax)));
        map.put("dncAmount", Double.valueOf(MfgSupport.round2(dnc)));
        map.put("totalAmount", Double.valueOf(MfgSupport.round2(withTax + dnc)));
        map.put("costPerFp", Double.valueOf(MfgSupport.round2(size <= 0.0 ? 0.0 : withTax / size)));
        return map;
    }

    /**
     * Quotes a piece of software from its size: the four steps of the standard in one call, with the
     * parameters of the 2019 assessment case as defaults.
     *
     * @param dctx the dispatch context
     * @param context service context, see {@code servicedef/services.xml#mfgSoftwareEstimate}
     * @return the service result with the size, effort, schedule and prices
     */
    public static Map<String, Object> softwareEstimate(DispatchContext dctx, Map<String, ? extends Object> context) {
        Map<String, Object> result = ServiceUtil.returnSuccess();
        result.putAll(softwareCost(
                MfgSupport.toDouble(context.get("ilfCount"), 0.0),
                MfgSupport.toDouble(context.get("eifCount"), 0.0),
                MfgSupport.optionalAmount(context.get("ufp")),
                MfgSupport.toDouble(context.get("swfBase"), 1.5),
                MfgSupport.toDouble(context.get("swfReuse"), 0.0),
                MfgSupport.toDouble(context.get("swfPreInvest"), 0.0),
                MfgSupport.toDouble(context.get("swfQuality"), 0.0),
                MfgSupport.toDouble(context.get("rdf"), 1.0),
                MfgSupport.toDouble(context.get("pdrHoursPerFp"), MfgServices.DEFAULT_PDR),
                MfgSupport.toDouble(context.get("dayRate"), MfgServices.DEFAULT_DAY_RATE),
                MfgSupport.toDouble(context.get("taxRate"), MfgServices.DEFAULT_TAX_RATE),
                MfgSupport.toDouble(context.get("dncRate"), MfgServices.DEFAULT_DNC_RATE)));
        return result;
    }

    /**
     * Creates or updates one software project of the ledger and freezes the planned person days the
     * estimate gives, so a later change of the parameters does not rewrite history.
     *
     * @param dctx the dispatch context
     * @param context service context, see {@code servicedef/services.xml#mfgSoftwareProjectSave}
     * @return the service result containing {@code projectCode} and the estimate values
     */
    public static Map<String, Object> softwareProjectSave(DispatchContext dctx, Map<String, ? extends Object> context) {
        String denied = MfgServices.permissionProblem(dctx, context, MfgServices.PERM_ENG);
        if (denied != null) {
            return ServiceUtil.returnError(denied);
        }
        Delegator delegator = dctx.getDelegator();
        String projectCode = MfgSupport.toText(context.get("projectCode"));
        String projectName = MfgSupport.toText(context.get("projectName"));
        if (projectName == null) {
            return ServiceUtil.returnError("projectName is required");
        }
        try {
            LocalDate today = LocalDate.now();
            GenericValue project = null;
            if (projectCode != null) {
                project = EntityQuery.use(delegator).from("MfgSoftwareProject")
                        .where("projectCode", projectCode).queryOne();
            }
            if (project == null) {
                if (projectCode == null) {
                    projectCode = nextSoftwareProjectId(delegator, today);
                }
                project = delegator.makeValue("MfgSoftwareProject");
                project.set("projectCode", projectCode);
                project.set("statusId", MfgServices.SW_PLANNED);
            }
            project.set("projectName", projectName);
            project.set("categoryId", MfgSupport.toText(context.get("categoryId")));
            project.set("productCode", MfgSupport.toText(context.get("productCode")));
            project.set("devProjectCode", MfgSupport.toText(context.get("devProjectCode")));
            project.set("ownerName", MfgSupport.toText(context.get("ownerName")));
            String statusId = MfgSupport.toText(context.get("statusId"));
            if (statusId != null) {
                project.set("statusId", statusId);
            }
            project.set("deliverySeq", MfgSupport.optionalAmount(context.get("deliverySeq")));
            project.set("ilfCount", MfgSupport.optionalAmount(context.get("ilfCount")));
            project.set("eifCount", MfgSupport.optionalAmount(context.get("eifCount")));
            project.set("ufp", MfgSupport.optionalAmount(context.get("ufp")));
            project.set("swfBase", MfgSupport.optionalAmount(context.get("swfBase")));
            project.set("swfReuse", MfgSupport.optionalAmount(context.get("swfReuse")));
            project.set("swfPreInvest", MfgSupport.optionalAmount(context.get("swfPreInvest")));
            project.set("swfQuality", MfgSupport.optionalAmount(context.get("swfQuality")));
            project.set("rdf", MfgSupport.optionalAmount(context.get("rdf")));
            project.set("pdrHoursPerFp", MfgSupport.optionalAmount(context.get("pdrHoursPerFp")));
            project.set("dayRate", MfgSupport.optionalAmount(context.get("dayRate")));
            project.set("taxRate", MfgSupport.optionalAmount(context.get("taxRate")));
            project.set("dncRate", MfgSupport.optionalAmount(context.get("dncRate")));
            project.set("plannedDays", MfgSupport.optionalAmount(context.get("plannedDays")));
            project.set("plannedStartDate", MfgSupport.parseDate(context.get("plannedStartDate")));
            project.set("plannedEndDate", MfgSupport.parseDate(context.get("plannedEndDate")));
            project.set("actualHours", MfgSupport.optionalAmount(context.get("actualHours")));
            project.set("actualCost", MfgSupport.optionalAmount(context.get("actualCost")));
            project.set("quotedAmount", MfgSupport.optionalAmount(context.get("quotedAmount")));
            project.set("note", MfgSupport.toText(context.get("note")));

            fillSoftwareDefaults(project);
            Map<String, Object> cost = softwareCostOf(project);
            if (project.get("plannedDays") == null) {
                project.set("plannedDays", cost.get("effortDays"));
            }
            delegator.createOrStore(project);

            Map<String, Object> result = ServiceUtil.returnSuccess();
            result.put("projectCode", projectCode);
            result.putAll(cost);
            result.put("plannedDays", project.get("plannedDays"));
            return result;
        } catch (GenericEntityException e) {
            return ServiceUtil.returnError("Could not save the software project: " + e.getMessage());
        }
    }

    /**
     * Lists the software projects with the numbers needed to plan the next one: what was planned,
     * what it really took, and the productivity the finished projects show.
     *
     * @param dctx the dispatch context
     * @param context service context, see {@code servicedef/services.xml#mfgSoftwareProjectList}
     * @return the service result containing {@code projects} and the summary figures
     */
    public static Map<String, Object> softwareProjectList(DispatchContext dctx, Map<String, ? extends Object> context) {
        Delegator delegator = dctx.getDelegator();
        try {
            List<GenericValue> projects = EntityQuery.use(delegator).from("MfgSoftwareProject")
                    .orderBy("-createdStamp").queryList();
            List<Map<String, Object>> rows = new ArrayList<>(projects.size());
            List<Double> pdrSamples = new ArrayList<>();
            double plannedTotal = 0.0;
            double actualTotal = 0.0;
            int openCount = 0;
            int overCount = 0;
            int finishedCount = 0;
            for (GenericValue project : projects) {
                Map<String, Object> row = toSoftwareProjectMap(project);
                rows.add(row);
                String statusId = project.getString("statusId");
                boolean open = !MfgServices.SW_DONE.equals(statusId) && !MfgServices.SW_CANCELLED.equals(statusId);
                if (open) {
                    openCount++;
                }
                double planned = MfgSupport.numberOrZero(row.get("plannedDays"));
                double actualDays = MfgSupport.numberOrZero(row.get("actualDays"));
                if (planned > 0.0 && actualDays > planned) {
                    overCount++;
                }
                if (MfgServices.SW_DONE.equals(statusId)) {
                    finishedCount++;
                    plannedTotal += planned;
                    actualTotal += actualDays;
                    Double sample = (Double) row.get("pdrActual");
                    if (sample != null) {
                        pdrSamples.add(sample);
                    }
                }
            }
            Map<String, Object> result = ServiceUtil.returnSuccess();
            MfgPaging.putPage(result, "projects",
                    MfgPaging.filterByKeyword(rows, MfgSupport.toText(context.get("search")), "projectCode", "projectName",
                            "categoryId", "ownerName", "productCode"),
                    MfgPaging.pageOf(context), MfgPaging.pageSizeOf(context), MfgSupport.toText(context.get("search")));
            result.put("projectCount", Integer.valueOf(rows.size()));
            result.put("openCount", Integer.valueOf(openCount));
            result.put("overCount", Integer.valueOf(overCount));
            result.put("finishedCount", Integer.valueOf(finishedCount));
            result.put("plannedDaysTotal", Double.valueOf(MfgSupport.round2(plannedTotal)));
            result.put("actualDaysTotal", Double.valueOf(MfgSupport.round2(actualTotal)));
            result.put("pdrSampleCount", Integer.valueOf(pdrSamples.size()));
            result.put("pdrMedian", Double.valueOf(MfgSupport.round2(MfgSupport.median(pdrSamples))));
            result.put("defaultPdr", Double.valueOf(MfgServices.DEFAULT_PDR));
            result.put("defaultDayRate", Double.valueOf(MfgServices.DEFAULT_DAY_RATE));
            result.put("defaultTaxRate", Double.valueOf(MfgServices.DEFAULT_TAX_RATE));
            result.put("defaultDncRate", Double.valueOf(MfgServices.DEFAULT_DNC_RATE));
            return result;
        } catch (GenericEntityException e) {
            return ServiceUtil.returnError("Could not read the software projects: " + e.getMessage());
        }
    }

    /**
     * Fills the parameters a software project did not set with the defaults of the cost model.
     *
     * @param project the project to fill in
     */
    static void fillSoftwareDefaults(GenericValue project) {
        setDefault(project, "swfBase", 1.5);
        setDefault(project, "swfReuse", 0.0);
        setDefault(project, "swfPreInvest", 0.0);
        setDefault(project, "swfQuality", 0.0);
        setDefault(project, "rdf", 1.0);
        setDefault(project, "pdrHoursPerFp", MfgServices.DEFAULT_PDR);
        setDefault(project, "dayRate", MfgServices.DEFAULT_DAY_RATE);
        setDefault(project, "taxRate", MfgServices.DEFAULT_TAX_RATE);
        setDefault(project, "dncRate", MfgServices.DEFAULT_DNC_RATE);
        setDefault(project, "ilfCount", 0.0);
        setDefault(project, "eifCount", 0.0);
    }

    static void setDefault(GenericValue value, String field, double fallback) {
        if (value.get(field) == null) {
            value.set(field, Double.valueOf(fallback));
        }
    }

    static Map<String, Object> softwareCostOf(GenericValue project) {
        fillSoftwareDefaults(project);
        return softwareCost(
                MfgSupport.numberOrZero(project.get("ilfCount")),
                MfgSupport.numberOrZero(project.get("eifCount")),
                project.get("ufp") instanceof Number
                        ? Double.valueOf(((Number) project.get("ufp")).doubleValue()) : null,
                MfgSupport.numberOrZero(project.get("swfBase")),
                MfgSupport.numberOrZero(project.get("swfReuse")),
                MfgSupport.numberOrZero(project.get("swfPreInvest")),
                MfgSupport.numberOrZero(project.get("swfQuality")),
                MfgSupport.numberOrZero(project.get("rdf")),
                MfgSupport.numberOrZero(project.get("pdrHoursPerFp")),
                MfgSupport.numberOrZero(project.get("dayRate")),
                MfgSupport.numberOrZero(project.get("taxRate")),
                MfgSupport.numberOrZero(project.get("dncRate")));
    }

    static Map<String, Object> toSoftwareProjectMap(GenericValue project) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("projectCode", project.getString("projectCode"));
        map.put("projectName", project.getString("projectName"));
        map.put("categoryId", project.getString("categoryId"));
        map.put("productCode", project.getString("productCode"));
        map.put("devProjectCode", project.getString("devProjectCode"));
        map.put("ownerName", project.getString("ownerName"));
        map.put("statusId", project.getString("statusId"));
        map.put("deliverySeq", project.get("deliverySeq"));
        map.put("ilfCount", project.get("ilfCount"));
        map.put("eifCount", project.get("eifCount"));
        Map<String, Object> cost = softwareCostOf(project);
        map.putAll(cost);
        map.put("pdrHoursPerFp", project.get("pdrHoursPerFp"));
        map.put("dayRate", project.get("dayRate"));
        map.put("plannedDays", project.get("plannedDays"));
        map.put("plannedStartDate", project.getDate("plannedStartDate"));
        map.put("plannedEndDate", project.getDate("plannedEndDate"));
        map.put("actualHours", project.get("actualHours"));
        double actualDays = MfgSupport.numberOrZero(project.get("actualHours")) / MfgServices.HOURS_PER_DAY;
        map.put("actualDays", Double.valueOf(MfgSupport.round2(actualDays)));
        map.put("actualCost", project.get("actualCost"));
        map.put("quotedAmount", project.get("quotedAmount"));
        double planned = MfgSupport.numberOrZero(project.get("plannedDays"));
        map.put("deviationRate", planned <= 0.0 ? null
                : Double.valueOf(MfgSupport.round2((actualDays - planned) / planned * 100.0)));
        double size = MfgSupport.numberOrZero(cost.get("ufp"));
        double hours = MfgSupport.numberOrZero(project.get("actualHours"));
        map.put("pdrActual", size > 0.0 && hours > 0.0
                ? Double.valueOf(MfgSupport.round2(hours / size)) : null);
        java.sql.Date end = project.getDate("plannedEndDate");
        map.put("daysToEnd", end == null ? null
                : Long.valueOf(java.time.temporal.ChronoUnit.DAYS.between(LocalDate.now(), end.toLocalDate())));
        map.put("note", project.getString("note"));
        return map;
    }

    static String nextSoftwareProjectId(Delegator delegator, LocalDate today)
            throws GenericEntityException {
        String prefix = "SW-" + today.format(MfgServices.DAY) + "-";
        int max = 0;
        for (GenericValue project : EntityQuery.use(delegator).from("MfgSoftwareProject").queryList()) {
            String id = project.getString("projectCode");
            if (id != null && id.startsWith(prefix)) {
                max = Math.max(max, MfgServices.parseTrailingNumber(id.substring(prefix.length())));
            }
        }
        return "SW-" + today.format(MfgServices.DAY) + "-" + String.format(Locale.ROOT, "%02d", max + 1);
    }
}
