/*
 * Copyright (C) 2010-2026 Tianjin Langhua Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.langhua.ofbiz.mfg;

import java.sql.Date;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
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
 * Product development: the projects with their three tracks (PCB, firmware, enclosure), the steps
 * and the issues of those steps, the stage ladder, and the engineering change orders that switch a
 * model to a new BOM version.
 *
 * <p>A project may start before the model exists, so the stage of the project and the stage of the
 * model are kept apart. Only a step that belongs to the track of the project can be edited, and an
 * ECO changes the BOM once it is approved, never while it is a draft.
 * Moved out of {@code MfgServices} in Step 171.
 */
public final class MfgDevServices {

    private MfgDevServices() {
    }

    /**
     * Returns the stage that follows the given one.
     *
     * @param stageId the current stage
     * @return the next stage, {@code null} when the stage is unknown or is already the last one
     */
    static String nextDevStage(String stageId) {
        for (int i = 0; i < MfgConstants.DEV_STAGES.length - 1; i++) {
            if (MfgConstants.DEV_STAGES[i].equals(stageId)) {
                return MfgConstants.DEV_STAGES[i + 1];
            }
        }
        return null;
    }

    /**
     * Returns the zero based position of a stage in the ladder.
     *
     * @param stageId the stage
     * @return the position, -1 when the stage is unknown
     */
    static int devStageIndex(String stageId) {
        for (int i = 0; i < MfgConstants.DEV_STAGES.length; i++) {
            if (MfgConstants.DEV_STAGES[i].equals(stageId)) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Tells whether a gate review result lets the project move on.
     *
     * @param resultId the review result
     * @return {@code true} for a pass and for a conditional pass
     */
    static boolean stageAdvancesOn(String resultId) {
        return MfgConstants.REVIEW_PASS.equals(resultId) || MfgConstants.REVIEW_CONDITIONAL.equals(resultId);
    }

    static boolean isKnownDevStage(String stageId) {
        return devStageIndex(stageId) >= 0;
    }

    static boolean isKnownReviewResult(String resultId) {
        return MfgConstants.REVIEW_PASS.equals(resultId) || MfgConstants.REVIEW_CONDITIONAL.equals(resultId)
                || MfgConstants.REVIEW_FAIL.equals(resultId);
    }

    static boolean isKnownDevStatus(String statusId) {
        return MfgConstants.DEV_ACTIVE.equals(statusId) || MfgConstants.DEV_ONHOLD.equals(statusId)
                || MfgConstants.DEV_DONE.equals(statusId) || MfgConstants.DEV_CANCELLED.equals(statusId);
    }

    static boolean isKnownEcoType(String ecoTypeId) {
        return "MFG_ECO_BOM".equals(ecoTypeId) || "MFG_ECO_PROCESS".equals(ecoTypeId)
                || "MFG_ECO_DESIGN".equals(ecoTypeId) || "MFG_ECO_MATERIAL".equals(ecoTypeId);
    }

    /**
     * Tells whether a change order may be applied: only an approved one, and only once.
     *
     * @param statusId the change order status
     * @return {@code true} when applying it is allowed
     */
    static boolean ecoAppliable(String statusId) {
        return MfgConstants.ECO_APPROVED.equals(statusId);
    }

    /**
     * Returns the steps of one development track, in the order they have to be done.
     *
     * @param trackId the track, one of the {@code TRACK_*} constants
     * @return the step identifiers, empty when the track is unknown or has no steps
     */
    static String[] stepsOfTrack(String trackId) {
        if (MfgConstants.TRACK_PCB.equals(trackId)) {
            return MfgConstants.PCB_STEPS;
        }
        if (MfgConstants.TRACK_FIRMWARE.equals(trackId)) {
            return MfgConstants.FIRMWARE_STEPS;
        }
        if (MfgConstants.TRACK_ENCLOSURE.equals(trackId)) {
            return MfgConstants.ENCLOSURE_STEPS;
        }
        return new String[0];
    }

    static boolean isKnownTrack(String trackId) {
        return MfgConstants.TRACK_PCB.equals(trackId) || MfgConstants.TRACK_FIRMWARE.equals(trackId)
                || MfgConstants.TRACK_ENCLOSURE.equals(trackId);
    }

    /**
     * Tells whether a step belongs to a track.
     *
     * @param trackId the track, may be empty for a project without a track
     * @param stepId the step
     * @return {@code true} when the step is part of the catalog of that track
     */
    static boolean isKnownStep(String trackId, String stepId) {
        for (String step : stepsOfTrack(trackId)) {
            if (step.equals(stepId)) {
                return true;
            }
        }
        return false;
    }

    static boolean isKnownStepStatus(String statusId) {
        return MfgConstants.STEP_TODO.equals(statusId) || MfgConstants.STEP_DOING.equals(statusId)
                || MfgConstants.STEP_DONE.equals(statusId) || MfgConstants.STEP_SKIPPED.equals(statusId);
    }

    /**
     * Tells whether a step status counts as finished, so it no longer holds the track up.
     *
     * @param statusId the step status
     * @return {@code true} for done and for skipped
     */
    static boolean stepFinished(String statusId) {
        return MfgConstants.STEP_DONE.equals(statusId) || MfgConstants.STEP_SKIPPED.equals(statusId);
    }

    /**
     * Returns the first step of a track that is not finished yet.
     *
     * @param trackId the track
     * @param finishedStepIds the steps that are done or skipped
     * @return the step identifier, {@code null} when every step is finished
     */
    static String firstOpenStep(String trackId, List<String> finishedStepIds) {
        for (String step : stepsOfTrack(trackId)) {
            if (finishedStepIds == null || !finishedStepIds.contains(step)) {
                return step;
            }
        }
        return null;
    }

    /**
     * Formats a development project number as {@code DEV-yyMMdd-NN}.
     *
     * @param date the creation day
     * @param sequence the running number, starting at 1
     * @return the formatted project number
     */
    static String formatDevProjectId(LocalDate date, int sequence) {
        return "DEV-" + date.format(MfgConstants.DAY) + "-" + String.format(Locale.ROOT, "%02d", sequence);
    }

    /**
     * Formats a change order number as {@code ECO-yyMMdd-NN}.
     *
     * @param date the creation day
     * @param sequence the running number, starting at 1
     * @return the formatted change order number
     */
    static String formatEcoId(LocalDate date, int sequence) {
        return "ECO-" + date.format(MfgConstants.DAY) + "-" + String.format(Locale.ROOT, "%02d", sequence);
    }

    static String nextDevProjectId(Delegator delegator, LocalDate today) throws GenericEntityException {
        String prefix = "DEV-" + today.format(MfgConstants.DAY) + "-";
        int max = 0;
        for (GenericValue project : EntityQuery.use(delegator).from("MfgDevProject").queryList()) {
            String id = project.getString("projectCode");
            if (id != null && id.startsWith(prefix)) {
                max = Math.max(max, MfgSupport.parseTrailingNumber(id.substring(prefix.length())));
            }
        }
        return formatDevProjectId(today, max + 1);
    }

    static String nextEcoId(Delegator delegator, LocalDate today) throws GenericEntityException {
        String prefix = "ECO-" + today.format(MfgConstants.DAY) + "-";
        int max = 0;
        for (GenericValue eco : EntityQuery.use(delegator).from("MfgDevEco").queryList()) {
            String id = eco.getString("ecoId");
            if (id != null && id.startsWith(prefix)) {
                max = Math.max(max, MfgSupport.parseTrailingNumber(id.substring(prefix.length())));
            }
        }
        return formatEcoId(today, max + 1);
    }

    static String nextIssueId(Delegator delegator, LocalDate today) throws GenericEntityException {
        String prefix = "ISSUE-" + today.format(MfgConstants.DAY) + "-";
        int max = 0;
        for (GenericValue issue : EntityQuery.use(delegator).from("MfgDevStepIssue").queryList()) {
            String id = issue.getString("issueId");
            if (id != null && id.startsWith(prefix)) {
                max = Math.max(max, MfgSupport.parseTrailingNumber(id.substring(prefix.length())));
            }
        }
        return prefix + String.format(Locale.ROOT, "%02d", Integer.valueOf(max + 1));
    }

    /**
     * Tells whether a status is one of the issue statuses.
     *
     * @param statusId the status to test
     * @return {@code true} when it is known
     */
    static boolean isKnownIssueStatus(String statusId) {
        for (String known : MfgConstants.ISSUE_STATUSES) {
            if (known.equals(statusId)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Tells whether a severity is one of the issue severities.
     *
     * @param severityId the severity to test
     * @return {@code true} when it is known
     */
    static boolean isKnownIssueSeverity(String severityId) {
        for (String known : MfgConstants.ISSUE_SEVERITIES) {
            if (known.equals(severityId)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Orders issue statuses so the open ones come first.
     *
     * @param statusId the status, may be {@code null}
     * @return a small number for open issues, a big one for closed ones
     */
    static int statusRank(String statusId) {
        if (MfgConstants.ISSUE_CLOSED.equals(statusId)) {
            return 2;
        }
        if (MfgConstants.ISSUE_DOING.equals(statusId)) {
            return 1;
        }
        return 0;
    }

    /**
     * Orders issue severities from high to low.
     *
     * @param severityId the severity, may be {@code null}
     * @return 3 for high, 2 for medium and 1 for low or unknown
     */
    static int severityRank(String severityId) {
        if (MfgConstants.ISSUE_HIGH.equals(severityId)) {
            return 3;
        }
        if (MfgConstants.ISSUE_MEDIUM.equals(severityId)) {
            return 2;
        }
        return 1;
    }

    /**
     * Tells whether an issue still has to be dealt with.
     *
     * @param statusId the status, may be {@code null}
     * @return {@code true} when it is neither closed nor empty
     */
    static boolean isOpenIssue(String statusId) {
        return statusId != null && !MfgConstants.ISSUE_CLOSED.equals(statusId);
    }

    static Map<String, Object> toDevProjectMap(Delegator delegator, GenericValue project)
            throws GenericEntityException {
        String projectCode = project.getString("projectCode");
        String stageId = project.getString("stageId");
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("projectCode", projectCode);
        map.put("projectName", project.getString("projectName"));
        map.put("productCode", project.getString("productCode"));
        map.put("stageId", stageId);
        map.put("stageIndex", Integer.valueOf(devStageIndex(stageId)));
        map.put("statusId", project.getString("statusId"));
        map.put("ownerName", project.getString("ownerName"));
        map.put("targetCost", project.getDouble("targetCost"));
        map.put("targetPrice", project.getDouble("targetPrice"));
        map.put("plannedLaunchDate", project.get("plannedLaunchDate"));
        map.put("actualLaunchDate", project.get("actualLaunchDate"));
        map.put("note", project.getString("note"));
        List<GenericValue> reviews = EntityQuery.use(delegator).from("MfgDevStageReview")
                .where("projectCode", projectCode).queryList();
        int passed = 0;
        for (GenericValue review : reviews) {
            if (stageAdvancesOn(review.getString("resultId"))) {
                passed++;
            }
        }
        map.put("reviewCount", Integer.valueOf(reviews.size()));
        map.put("passedCount", Integer.valueOf(passed));
        int openEcos = 0;
        for (GenericValue eco : EntityQuery.use(delegator).from("MfgDevEco")
                .where("projectCode", projectCode).queryList()) {
            String status = eco.getString("statusId");
            if (MfgConstants.ECO_DRAFT.equals(status) || MfgConstants.ECO_APPROVED.equals(status)) {
                openEcos++;
            }
        }
        map.put("openEcoCount", Integer.valueOf(openEcos));

        int openIssues = 0;
        for (GenericValue issue : EntityQuery.use(delegator).from("MfgDevStepIssue")
                .where("projectCode", projectCode).queryList()) {
            if (isOpenIssue(issue.getString("statusId"))) {
                openIssues++;
            }
        }
        map.put("openIssueCount", Integer.valueOf(openIssues));

        String trackId = project.getString("trackId");
        map.put("trackId", trackId);
        map.put("partCode", project.getString("partCode"));
        String[] catalog = stepsOfTrack(trackId);
        List<String> finished = new ArrayList<>();
        for (GenericValue step : EntityQuery.use(delegator).from("MfgDevTrackStep")
                .where("projectCode", projectCode).queryList()) {
            if (stepFinished(step.getString("statusId"))) {
                finished.add(step.getString("stepId"));
            }
        }
        map.put("stepTotal", Integer.valueOf(catalog.length));
        map.put("stepDone", Integer.valueOf(finished.size()));
        map.put("currentStepId", firstOpenStep(trackId, finished));
        return map;
    }

    static Map<String, Object> toTrackStepMap(GenericValue step) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("stepId", step.getString("stepId"));
        map.put("statusId", step.getString("statusId"));
        map.put("plannedDate", step.get("plannedDate"));
        map.put("doneDate", step.get("doneDate"));
        map.put("ownerName", step.getString("ownerName"));
        map.put("vendorName", step.getString("vendorName"));
        map.put("amount", step.getDouble("amount"));
        map.put("qty", step.getDouble("qty"));
        map.put("referenceNo", step.getString("referenceNo"));
        map.put("lotId", step.getString("lotId"));
        map.put("note", step.getString("note"));
        return map;
    }

    static Map<String, Object> toEcoMap(GenericValue eco) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("ecoId", eco.getString("ecoId"));
        map.put("productCode", eco.getString("productCode"));
        map.put("projectCode", eco.getString("projectCode"));
        map.put("ecoTypeId", eco.getString("ecoTypeId"));
        map.put("reason", eco.getString("reason"));
        map.put("changeSummary", eco.getString("changeSummary"));
        map.put("statusId", eco.getString("statusId"));
        map.put("fromBomVersionId", eco.getString("fromBomVersionId"));
        map.put("toBomVersionId", eco.getString("toBomVersionId"));
        map.put("requestedBy", eco.getString("requestedBy"));
        map.put("approvedBy", eco.getString("approvedBy"));
        map.put("requestedDate", eco.get("requestedDate"));
        map.put("effectiveDate", eco.get("effectiveDate"));
        map.put("note", eco.getString("note"));
        return map;
    }

    /**
     * Creates or updates a development project.
     *
     * @param dctx the dispatch context
     * @param context service context, see {@code servicedef/dev-services.xml#mfgDevProjectSave}
     * @return the service result containing {@code projectCode}
     */
    public static Map<String, Object> devProjectSave(DispatchContext dctx, Map<String, ? extends Object> context) {
        String denied = MfgSecurity.permissionProblem(dctx, context, MfgConstants.PERM_ENG);
        if (denied != null) {
            return ServiceUtil.returnError(denied);
        }
        Delegator delegator = dctx.getDelegator();
        String projectCode = MfgSupport.toText(context.get("projectCode"));
        String projectName = MfgSupport.toText(context.get("projectName"));
        String stageId = MfgSupport.toText(context.get("stageId"));
        String statusId = MfgSupport.toText(context.get("statusId"));
        String trackId = MfgSupport.toText(context.get("trackId"));
        if (stageId != null && !isKnownDevStage(stageId)) {
            return ServiceUtil.returnError("Unknown development stage [" + stageId + "]");
        }
        if (statusId != null && !isKnownDevStatus(statusId)) {
            return ServiceUtil.returnError("Unknown project status [" + statusId + "]");
        }
        if (trackId != null && !isKnownTrack(trackId)) {
            return ServiceUtil.returnError("Unknown development track [" + trackId + "]");
        }
        try {
            GenericValue project;
            String isNew = "N";
            if (projectCode == null) {
                if (projectName == null) {
                    return ServiceUtil.returnError("projectName is required for a new project");
                }
                projectCode = nextDevProjectId(delegator, LocalDate.now());
                project = delegator.makeValue("MfgDevProject");
                project.set("projectCode", projectCode);
                project.set("stageId", stageId == null ? MfgConstants.DEV_STAGES[0] : stageId);
                project.set("statusId", statusId == null ? MfgConstants.DEV_ACTIVE : statusId);
                isNew = "Y";
            } else {
                project = EntityQuery.use(delegator).from("MfgDevProject")
                        .where("projectCode", projectCode).queryOne();
                if (project == null) {
                    project = delegator.makeValue("MfgDevProject");
                    project.set("projectCode", projectCode);
                    project.set("stageId", stageId == null ? MfgConstants.DEV_STAGES[0] : stageId);
                    project.set("statusId", statusId == null ? MfgConstants.DEV_ACTIVE : statusId);
                    isNew = "Y";
                } else {
                    if (stageId != null) {
                        project.set("stageId", stageId);
                    }
                    if (statusId != null) {
                        project.set("statusId", statusId);
                    }
                }
            }
            if (projectName != null) {
                project.set("projectName", projectName);
            }
            if (context.containsKey("productCode")) {
                project.set("productCode", MfgSupport.toText(context.get("productCode")));
            }
            if (trackId != null) {
                project.set("trackId", trackId);
            }
            if (context.containsKey("partCode")) {
                project.set("partCode", MfgSupport.toText(context.get("partCode")));
            }
            if (context.containsKey("ownerName")) {
                project.set("ownerName", MfgSupport.toText(context.get("ownerName")));
            }
            if (context.containsKey("targetCost")) {
                project.set("targetCost", Double.valueOf(MfgSupport.toDouble(context.get("targetCost"), 0.0)));
            }
            if (context.containsKey("targetPrice")) {
                project.set("targetPrice", Double.valueOf(MfgSupport.toDouble(context.get("targetPrice"), 0.0)));
            }
            if (context.containsKey("plannedLaunchDate")) {
                project.set("plannedLaunchDate", MfgSupport.parseDate(context.get("plannedLaunchDate")));
            }
            if (context.containsKey("actualLaunchDate")) {
                project.set("actualLaunchDate", MfgSupport.parseDate(context.get("actualLaunchDate")));
            }
            if (context.containsKey("note")) {
                project.set("note", MfgSupport.toText(context.get("note")));
            }
            delegator.createOrStore(project);

            Map<String, Object> result = ServiceUtil.returnSuccess();
            result.put("projectCode", projectCode);
            result.put("isNew", isNew);
            return result;
        } catch (GenericEntityException e) {
            return ServiceUtil.returnError("Could not save the development project: " + e.getMessage());
        }
    }

    /**
     * Lists development projects, newest first.
     *
     * @param dctx the dispatch context
     * @param context service context, see {@code servicedef/dev-services.xml#mfgDevProjectList}
     * @return the service result containing {@code projects}
     */
    public static Map<String, Object> devProjectList(DispatchContext dctx, Map<String, ? extends Object> context) {
        Delegator delegator = dctx.getDelegator();
        String stageId = MfgSupport.toText(context.get("stageId"));
        String statusId = MfgSupport.toText(context.get("statusId"));
        try {
            List<GenericValue> projects = EntityQuery.use(delegator).from("MfgDevProject")
                    .orderBy("-createdStamp").queryList();
            List<Map<String, Object>> list = new ArrayList<>(projects.size());
            for (GenericValue project : projects) {
                if (stageId != null && !stageId.equals(project.getString("stageId"))) {
                    continue;
                }
                if (statusId != null && !statusId.equals(project.getString("statusId"))) {
                    continue;
                }
                list.add(toDevProjectMap(delegator, project));
            }
            List<Map<String, Object>> stages = new ArrayList<>(MfgConstants.DEV_STAGES.length);
            for (String stage : MfgConstants.DEV_STAGES) {
                Map<String, Object> map = new LinkedHashMap<>();
                map.put("stageId", stage);
                map.put("stageIndex", Integer.valueOf(devStageIndex(stage)));
                stages.add(map);
            }
            Map<String, Object> result = ServiceUtil.returnSuccess();
            result.put("stages", stages);
            result.put("stageId", stageId);
            result.put("statusId", statusId);
            MfgPaging.putPage(result, "projects",
                    MfgPaging.filterByKeyword(list, MfgSupport.toText(context.get("search")), "projectCode", "projectName",
                            "productCode", "ownerName", "stageId", "statusId"),
                    MfgPaging.pageOf(context), MfgPaging.pageSizeOf(context), MfgSupport.toText(context.get("search")));
            return result;
        } catch (GenericEntityException e) {
            return ServiceUtil.returnError("Could not read the development projects: " + e.getMessage());
        }
    }

    /**
     * Returns one development project with its gate reviews and change orders.
     *
     * @param dctx the dispatch context
     * @param context service context, see {@code servicedef/dev-services.xml#mfgDevProjectDetail}
     * @return the service result containing {@code project}, {@code reviews} and {@code ecos}
     */
    public static Map<String, Object> devProjectDetail(DispatchContext dctx, Map<String, ? extends Object> context) {
        Delegator delegator = dctx.getDelegator();
        String projectCode = MfgSupport.toText(context.get("projectCode"));
        if (projectCode == null) {
            Map<String, Object> empty = ServiceUtil.returnSuccess();
            empty.put("reviews", new ArrayList<Map<String, Object>>());
            empty.put("ecos", new ArrayList<Map<String, Object>>());
            return empty;
        }
        try {
            GenericValue project = EntityQuery.use(delegator).from("MfgDevProject")
                    .where("projectCode", projectCode).queryOne();
            if (project == null) {
                return ServiceUtil.returnError("Unknown development project [" + projectCode + "]");
            }
            List<GenericValue> stored = EntityQuery.use(delegator).from("MfgDevStageReview")
                    .where("projectCode", projectCode).queryList();
            Map<String, GenericValue> byStage = new LinkedHashMap<>();
            for (GenericValue review : stored) {
                byStage.put(review.getString("stageId"), review);
            }
            List<Map<String, Object>> reviews = new ArrayList<>(MfgConstants.DEV_STAGES.length);
            for (String stage : MfgConstants.DEV_STAGES) {
                GenericValue review = byStage.get(stage);
                Map<String, Object> map = new LinkedHashMap<>();
                map.put("stageId", stage);
                map.put("stageIndex", Integer.valueOf(devStageIndex(stage)));
                map.put("plannedDate", review == null ? null : review.get("plannedDate"));
                map.put("doneDate", review == null ? null : review.get("doneDate"));
                map.put("resultId", review == null ? null : review.getString("resultId"));
                map.put("reviewer", review == null ? null : review.getString("reviewer"));
                map.put("note", review == null ? null : review.getString("note"));
                reviews.add(map);
            }
            List<Map<String, Object>> ecos = new ArrayList<>();
            for (GenericValue eco : EntityQuery.use(delegator).from("MfgDevEco")
                    .where("projectCode", projectCode).orderBy("-createdStamp").queryList()) {
                ecos.add(toEcoMap(eco));
            }
            String stageId = project.getString("stageId");
            Map<String, Object> projectMap = toDevProjectMap(delegator, project);
            projectMap.put("nextStageId", nextDevStage(stageId));

            // the steps of the track, with the stored state merged in, in catalog order
            String trackId = project.getString("trackId");
            Map<String, GenericValue> stepByStepId = new LinkedHashMap<>();
            for (GenericValue step : EntityQuery.use(delegator).from("MfgDevTrackStep")
                    .where("projectCode", projectCode).queryList()) {
                stepByStepId.put(step.getString("stepId"), step);
            }
            List<Map<String, Object>> steps = new ArrayList<>();
            String[] catalog = stepsOfTrack(trackId);
            for (int i = 0; i < catalog.length; i++) {
                GenericValue storedStep = stepByStepId.get(catalog[i]);
                Map<String, Object> map;
                if (storedStep == null) {
                    map = new LinkedHashMap<>();
                    map.put("stepId", catalog[i]);
                    map.put("statusId", MfgConstants.STEP_TODO);
                    map.put("plannedDate", null);
                    map.put("doneDate", null);
                    map.put("ownerName", null);
                    map.put("vendorName", null);
                    map.put("amount", null);
                    map.put("qty", null);
                    map.put("referenceNo", null);
                    map.put("lotId", null);
                    map.put("note", null);
                } else {
                    map = toTrackStepMap(storedStep);
                }
                map.put("stepIndex", Integer.valueOf(i));
                steps.add(map);
            }

            // open issues per step, so the step list can show where the problems are
            Map<String, Integer> openIssuesByStep = new LinkedHashMap<>();
            for (GenericValue issue : EntityQuery.use(delegator).from("MfgDevStepIssue")
                    .where("projectCode", projectCode).queryList()) {
                if (!isOpenIssue(issue.getString("statusId"))) {
                    continue;
                }
                String issueStep = issue.getString("stepId");
                String key = issueStep == null ? "" : issueStep;
                Integer current = openIssuesByStep.get(key);
                openIssuesByStep.put(key, Integer.valueOf(current == null ? 1 : current.intValue() + 1));
            }
            for (Map<String, Object> stepRow : steps) {
                Integer count = openIssuesByStep.get(String.valueOf(stepRow.get("stepId")));
                stepRow.put("openIssueCount", Integer.valueOf(count == null ? 0 : count.intValue()));
            }

            Map<String, Object> result = ServiceUtil.returnSuccess();
            result.put("project", projectMap);
            result.put("reviews", reviews);
            result.put("ecos", ecos);
            result.put("steps", steps);
            result.put("trackId", trackId);
            result.put("stageIndex", Integer.valueOf(devStageIndex(stageId)));
            return result;        } catch (GenericEntityException e) {
            return ServiceUtil.returnError("Could not read the development project: " + e.getMessage());
        }
    }

    /**
     * Records the gate review of one stage. A pass or a conditional pass moves the project to the
     * next stage of the ladder.
     *
     * @param dctx the dispatch context
     * @param context service context, see {@code servicedef/dev-services.xml#mfgDevStageSave}
     * @return the service result containing {@code stageId} and the new {@code projectStageId}
     */
    public static Map<String, Object> devStageSave(DispatchContext dctx, Map<String, ? extends Object> context) {
        String denied = MfgSecurity.permissionProblem(dctx, context, MfgConstants.PERM_ENG);
        if (denied != null) {
            return ServiceUtil.returnError(denied);
        }
        Delegator delegator = dctx.getDelegator();
        String projectCode = MfgSupport.toText(context.get("projectCode"));
        String stageId = MfgSupport.toText(context.get("stageId"));
        String resultId = MfgSupport.toText(context.get("resultId"));
        if (projectCode == null) {
            return ServiceUtil.returnError("projectCode is required");
        }
        if (stageId == null || !isKnownDevStage(stageId)) {
            return ServiceUtil.returnError("Unknown development stage [" + stageId + "]");
        }
        if (resultId != null && !isKnownReviewResult(resultId)) {
            return ServiceUtil.returnError("Unknown review result [" + resultId + "]");
        }
        try {
            GenericValue project = EntityQuery.use(delegator).from("MfgDevProject")
                    .where("projectCode", projectCode).queryOne();
            if (project == null) {
                return ServiceUtil.returnError("Unknown development project [" + projectCode + "]");
            }
            GenericValue review = delegator.makeValue("MfgDevStageReview");
            review.set("projectCode", projectCode);
            review.set("stageId", stageId);
            review.set("plannedDate", MfgSupport.parseDate(context.get("plannedDate")));
            review.set("doneDate", MfgSupport.parseDate(context.get("doneDate")));
            review.set("resultId", resultId);
            review.set("reviewer", MfgSupport.toText(context.get("reviewer")));
            review.set("note", MfgSupport.toText(context.get("note")));
            delegator.createOrStore(review);

            String projectStageId = project.getString("stageId");
            String next = nextDevStage(stageId);
            if (stageAdvancesOn(resultId) && next != null
                    && devStageIndex(next) > devStageIndex(projectStageId)) {
                projectStageId = next;
                project.set("stageId", next);
                if (MfgConstants.DEV_STAGES[MfgConstants.DEV_STAGES.length - 1].equals(next)) {
                    if (project.get("actualLaunchDate") == null) {
                        project.set("actualLaunchDate", java.sql.Date.valueOf(LocalDate.now()));
                    }
                    project.set("statusId", MfgConstants.DEV_DONE);
                }
                project.store();
            }
            Map<String, Object> result = ServiceUtil.returnSuccess();
            result.put("projectCode", projectCode);
            result.put("stageId", stageId);
            result.put("projectStageId", projectStageId);
            return result;
        } catch (GenericEntityException e) {
            return ServiceUtil.returnError("Could not save the stage review: " + e.getMessage());
        }
    }

    /**
     * Loads the project and checks that the step belongs to its track.
     *
     * @param delegator the delegator
     * @param projectCode the project
     * @param stepId the step
     * @return the project, or {@code null} when the project is unknown or the step does not fit it
     * @throws GenericEntityException when the project cannot be read
     */
    static GenericValue projectOfStep(Delegator delegator, String projectCode, String stepId)
            throws GenericEntityException {
        GenericValue project = EntityQuery.use(delegator).from("MfgDevProject")
                .where("projectCode", projectCode).queryOne();
        if (project == null) {
            return null;
        }
        return isKnownStep(project.getString("trackId"), stepId) ? project : null;
    }

    /**
     * Writes the details of one development track step. An empty value clears the field, the
     * status is only touched when it is given.
     *
     * @param dctx the dispatch context
     * @param context service context, see {@code servicedef/dev-services.xml#mfgDevStepSave}
     * @return the service result containing {@code stepId} and the {@code lotId} when one was created
     */
    public static Map<String, Object> devStepSave(DispatchContext dctx, Map<String, ? extends Object> context) {
        String denied = MfgSecurity.permissionProblem(dctx, context, MfgConstants.PERM_ENG);
        if (denied != null) {
            return ServiceUtil.returnError(denied);
        }
        Delegator delegator = dctx.getDelegator();
        String projectCode = MfgSupport.toText(context.get("projectCode"));
        String stepId = MfgSupport.toText(context.get("stepId"));
        String statusId = MfgSupport.toText(context.get("statusId"));
        if (projectCode == null || stepId == null) {
            return ServiceUtil.returnError("projectCode and stepId are required");
        }
        if (statusId != null && !isKnownStepStatus(statusId)) {
            return ServiceUtil.returnError("Unknown step status [" + statusId + "]");
        }
        try {
            GenericValue project = projectOfStep(delegator, projectCode, stepId);
            if (project == null) {
                return ServiceUtil.returnError("Step [" + stepId + "] does not belong to project ["
                        + projectCode + "], check the track of the project");
            }
            GenericValue step = EntityQuery.use(delegator).from("MfgDevTrackStep")
                    .where("projectCode", projectCode, "stepId", stepId).queryOne();
            if (step == null) {
                step = delegator.makeValue("MfgDevTrackStep");
                step.set("projectCode", projectCode);
                step.set("stepId", stepId);
                step.set("statusId", MfgConstants.STEP_TODO);
            }
            step.set("plannedDate", MfgSupport.parseDate(context.get("plannedDate")));
            step.set("doneDate", MfgSupport.parseDate(context.get("doneDate")));
            step.set("ownerName", MfgSupport.toText(context.get("ownerName")));
            step.set("vendorName", MfgSupport.toText(context.get("vendorName")));
            step.set("amount", Double.valueOf(MfgSupport.toDouble(context.get("amount"), 0.0)));
            step.set("qty", Double.valueOf(MfgSupport.toDouble(context.get("qty"), 0.0)));
            step.set("referenceNo", MfgSupport.toText(context.get("referenceNo")));
            step.set("note", MfgSupport.toText(context.get("note")));
            if (statusId != null) {
                step.set("statusId", statusId);
            }
            String error = MfgStockServices.createStockInLot(delegator, project, step);
            if (error != null) {
                return ServiceUtil.returnError(error);
            }
            delegator.createOrStore(step);

            Map<String, Object> result = ServiceUtil.returnSuccess();
            result.put("projectCode", projectCode);
            result.put("stepId", stepId);
            result.put("statusId", step.getString("statusId"));
            result.put("lotId", step.getString("lotId"));
            return result;
        } catch (GenericEntityException e) {
            return ServiceUtil.returnError("Could not save the step: " + e.getMessage());
        }
    }

    /**
     * Changes only the status of one step, keeping every other field, and stamps the completion
     * date the first time it is finished.
     *
     * @param dctx the dispatch context
     * @param context service context, see {@code servicedef/dev-services.xml#mfgDevStepStatus}
     * @return the service result containing {@code stepId} and the {@code lotId} when one was created
     */
    public static Map<String, Object> devStepStatus(DispatchContext dctx, Map<String, ? extends Object> context) {
        String denied = MfgSecurity.permissionProblem(dctx, context, MfgConstants.PERM_ENG);
        if (denied != null) {
            return ServiceUtil.returnError(denied);
        }
        Delegator delegator = dctx.getDelegator();
        String projectCode = MfgSupport.toText(context.get("projectCode"));
        String stepId = MfgSupport.toText(context.get("stepId"));
        String statusId = MfgSupport.toText(context.get("statusId"));
        if (projectCode == null || stepId == null || statusId == null) {
            return ServiceUtil.returnError("projectCode, stepId and statusId are required");
        }
        if (!isKnownStepStatus(statusId)) {
            return ServiceUtil.returnError("Unknown step status [" + statusId + "]");
        }
        try {
            GenericValue project = projectOfStep(delegator, projectCode, stepId);
            if (project == null) {
                return ServiceUtil.returnError("Step [" + stepId + "] does not belong to project ["
                        + projectCode + "], check the track of the project");
            }
            GenericValue step = EntityQuery.use(delegator).from("MfgDevTrackStep")
                    .where("projectCode", projectCode, "stepId", stepId).queryOne();
            if (step == null) {
                step = delegator.makeValue("MfgDevTrackStep");
                step.set("projectCode", projectCode);
                step.set("stepId", stepId);
            }
            step.set("statusId", statusId);
            if (MfgConstants.STEP_DONE.equals(statusId) && step.get("doneDate") == null) {
                step.set("doneDate", java.sql.Date.valueOf(LocalDate.now()));
            }
            String error = MfgStockServices.createStockInLot(delegator, project, step);
            if (error != null) {
                return ServiceUtil.returnError(error);
            }
            delegator.createOrStore(step);

            Map<String, Object> result = ServiceUtil.returnSuccess();
            result.put("projectCode", projectCode);
            result.put("stepId", stepId);
            result.put("statusId", statusId);
            result.put("lotId", step.getString("lotId"));
            return result;
        } catch (GenericEntityException e) {
            return ServiceUtil.returnError("Could not change the step status: " + e.getMessage());
        }
    }

    /**
     * Raises or updates an issue found at one development step. The step may be left empty for a
     * project level problem, but a project must always be given.
     *
     * @param dctx the dispatch context
     * @param context service context, see {@code servicedef/dev-services.xml#mfgDevStepIssueSave}
     * @return the service result containing {@code issueId} and {@code statusId}
     */
    public static Map<String, Object> devStepIssueSave(DispatchContext dctx, Map<String, ? extends Object> context) {
        String denied = MfgSecurity.permissionProblem(dctx, context, MfgConstants.PERM_ENG);
        if (denied != null) {
            return ServiceUtil.returnError(denied);
        }
        Delegator delegator = dctx.getDelegator();
        String issueId = MfgSupport.toText(context.get("issueId"));
        String projectCode = MfgSupport.toText(context.get("projectCode"));
        String stepId = MfgSupport.toText(context.get("stepId"));
        String severityId = MfgSupport.toText(context.get("severityId"));
        String statusId = MfgSupport.toText(context.get("statusId"));
        if (severityId != null && !isKnownIssueSeverity(severityId)) {
            return ServiceUtil.returnError("Unknown issue severity [" + severityId + "]");
        }
        if (statusId != null && !isKnownIssueStatus(statusId)) {
            return ServiceUtil.returnError("Unknown issue status [" + statusId + "]");
        }
        try {
            GenericValue issue;
            if (issueId == null) {
                if (projectCode == null) {
                    return ServiceUtil.returnError("projectCode is required for a new issue");
                }
                issueId = nextIssueId(delegator, LocalDate.now());
                String storedProject = projectCode;
                if (stepId != null && projectOfStep(delegator, projectCode, stepId) == null) {
                    return ServiceUtil.returnError("Step [" + stepId + "] does not belong to project ["
                            + projectCode + "], check the track of the project");
                }
                issue = delegator.makeValue("MfgDevStepIssue");
                issue.set("issueId", issueId);
                issue.set("projectCode", storedProject);
                issue.set("statusId", statusId == null ? MfgConstants.ISSUE_OPEN : statusId);
                issue.set("foundDate", MfgSupport.parseDate(context.get("foundDate")));
                if (issue.get("foundDate") == null) {
                    issue.set("foundDate", java.sql.Date.valueOf(LocalDate.now()));
                }
            } else {
                issue = EntityQuery.use(delegator).from("MfgDevStepIssue")
                        .where("issueId", issueId).queryOne();
                if (issue == null) {
                    issue = delegator.makeValue("MfgDevStepIssue");
                    issue.set("issueId", issueId);
                    if (projectCode == null) {
                        return ServiceUtil.returnError("projectCode is required for a new issue");
                    }
                    issue.set("projectCode", projectCode);
                    issue.set("statusId", statusId == null ? MfgConstants.ISSUE_OPEN : statusId);
                    issue.set("foundDate", MfgSupport.parseDate(context.get("foundDate")));
                    if (issue.get("foundDate") == null) {
                        issue.set("foundDate", java.sql.Date.valueOf(LocalDate.now()));
                    }
                } else {
                    String storedProject = issue.getString("projectCode");
                    String wantedStep = stepId;
                    if (wantedStep != null && !wantedStep.isEmpty()
                            && projectOfStep(delegator, storedProject, wantedStep) == null) {
                        return ServiceUtil.returnError("Step [" + wantedStep + "] does not belong to project ["
                                + storedProject + "], check the track of the project");
                    }
                    if (statusId != null) {
                        issue.set("statusId", statusId);
                    }
                }
            }
            if (stepId != null) {
                issue.set("stepId", stepId.isEmpty() ? null : stepId);
            }
            if (context.containsKey("title")) {
                issue.set("title", MfgSupport.toText(context.get("title")));
            }
            if (context.containsKey("detail")) {
                issue.set("detail", MfgSupport.toText(context.get("detail")));
            }
            if (severityId != null) {
                issue.set("severityId", severityId);
            }
            if (context.containsKey("ownerName")) {
                issue.set("ownerName", MfgSupport.toText(context.get("ownerName")));
            }
            if (context.containsKey("dueDate")) {
                issue.set("dueDate", MfgSupport.parseDate(context.get("dueDate")));
            }
            if (context.containsKey("resolution")) {
                issue.set("resolution", MfgSupport.toText(context.get("resolution")));
            }
            if (MfgConstants.ISSUE_CLOSED.equals(issue.getString("statusId")) && issue.get("closedDate") == null) {
                issue.set("closedDate", java.sql.Date.valueOf(LocalDate.now()));
            }
            if (!MfgConstants.ISSUE_CLOSED.equals(issue.getString("statusId"))) {
                issue.set("closedDate", null);
            }
            if (issue.getString("severityId") == null) {
                issue.set("severityId", MfgConstants.ISSUE_MEDIUM);
            }
            delegator.createOrStore(issue);

            Map<String, Object> result = ServiceUtil.returnSuccess();
            result.put("issueId", issueId);
            result.put("projectCode", issue.getString("projectCode"));
            result.put("statusId", issue.getString("statusId"));
            return result;
        } catch (GenericEntityException e) {
            return ServiceUtil.returnError("Could not save the issue: " + e.getMessage());
        }
    }

    /**
     * Changes only the status of one issue, used by the quick buttons: closing stamps the date,
     * reopening clears it.
     *
     * @param dctx the dispatch context
     * @param context service context, see {@code servicedef/dev-services.xml#mfgDevStepIssueStatus}
     * @return the service result containing {@code issueId} and the new {@code statusId}
     */
    public static Map<String, Object> devStepIssueStatus(DispatchContext dctx, Map<String, ? extends Object> context) {
        String denied = MfgSecurity.permissionProblem(dctx, context, MfgConstants.PERM_ENG);
        if (denied != null) {
            return ServiceUtil.returnError(denied);
        }
        Delegator delegator = dctx.getDelegator();
        String issueId = MfgSupport.toText(context.get("issueId"));
        String statusId = MfgSupport.toText(context.get("statusId"));
        if (issueId == null || statusId == null) {
            return ServiceUtil.returnError("issueId and statusId are required");
        }
        if (!isKnownIssueStatus(statusId)) {
            return ServiceUtil.returnError("Unknown issue status [" + statusId + "]");
        }
        try {
            GenericValue issue = EntityQuery.use(delegator).from("MfgDevStepIssue")
                    .where("issueId", issueId).queryOne();
            if (issue == null) {
                return ServiceUtil.returnError("Unknown issue [" + issueId + "]");
            }
            issue.set("statusId", statusId);
            if (MfgConstants.ISSUE_CLOSED.equals(statusId)) {
                if (issue.get("closedDate") == null) {
                    issue.set("closedDate", java.sql.Date.valueOf(LocalDate.now()));
                }
                String resolution = MfgSupport.toText(context.get("resolution"));
                if (resolution != null) {
                    issue.set("resolution", resolution);
                }
            } else {
                issue.set("closedDate", null);
            }
            delegator.store(issue);

            Map<String, Object> result = ServiceUtil.returnSuccess();
            result.put("issueId", issueId);
            result.put("statusId", statusId);
            result.put("closedDate", issue.get("closedDate"));
            return result;
        } catch (GenericEntityException e) {
            return ServiceUtil.returnError("Could not change the issue status: " + e.getMessage());
        }
    }

    /**
     * Lists the issues of one development project, open ones first and then the newest.
     *
     * @param dctx the dispatch context
     * @param context service context, see {@code servicedef/dev-services.xml#mfgDevStepIssueList}
     * @return the service result with the issues and the open counts
     */
    public static Map<String, Object> devStepIssueList(DispatchContext dctx, Map<String, ? extends Object> context) {
        Delegator delegator = dctx.getDelegator();
        String projectCode = MfgSupport.toText(context.get("projectCode"));
        String statusId = MfgSupport.toText(context.get("statusId"));
        boolean openOnly = "Y".equalsIgnoreCase(MfgSupport.toText(context.get("openOnly")));
        Map<String, Object> result = ServiceUtil.returnSuccess();
        List<Map<String, Object>> rows = new ArrayList<>();
        result.put("issues", rows);
        result.put("openCount", Integer.valueOf(0));
        result.put("totalCount", Integer.valueOf(0));
        result.put("highOpenCount", Integer.valueOf(0));
        if (projectCode == null) {
            return result;
        }
        try {
            EntityQuery query = EntityQuery.use(delegator).from("MfgDevStepIssue")
                    .where("projectCode", projectCode);
            if (statusId != null) {
                query = query.where("statusId", statusId);
            }
            List<GenericValue> stored = query.orderBy("-createdStamp").queryList();
            int open = 0;
            int high = 0;
            for (GenericValue issue : stored) {
                Map<String, Object> map = MfgRecordMaps.toIssueMap(issue);
                boolean closed = MfgConstants.ISSUE_CLOSED.equals(issue.getString("statusId"));
                if (!closed) {
                    open++;
                    if (MfgConstants.ISSUE_HIGH.equals(issue.getString("severityId"))) {
                        high++;
                    }
                }
                if (openOnly && closed) {
                    continue;
                }
                rows.add(map);
            }
            // open first, then high severity, then the newest
            rows.sort(new Comparator<Map<String, Object>>() {
                @Override
                public int compare(Map<String, Object> left, Map<String, Object> right) {
                    int byStatus = Integer.valueOf(statusRank((String) left.get("statusId")))
                            .compareTo(Integer.valueOf(statusRank((String) right.get("statusId"))));
                    if (byStatus != 0) {
                        return byStatus;
                    }
                    int bySeverity = Integer.valueOf(severityRank((String) right.get("severityId")))
                            .compareTo(Integer.valueOf(severityRank((String) left.get("severityId"))));
                    if (bySeverity != 0) {
                        return bySeverity;
                    }
                    Object leftCreated = left.get("createdStamp");
                    Object rightCreated = right.get("createdStamp");
                    String leftText = leftCreated == null ? "" : leftCreated.toString();
                    String rightText = rightCreated == null ? "" : rightCreated.toString();
                    return rightText.compareTo(leftText);
                }
            });
            result.put("issues", rows);
            result.put("openCount", Integer.valueOf(open));
            result.put("totalCount", Integer.valueOf(stored.size()));
            result.put("highOpenCount", Integer.valueOf(high));
            return result;
        } catch (GenericEntityException e) {
            return ServiceUtil.returnError("Could not read the issues: " + e.getMessage());
        }
    }

    /**
     * Raises or updates an engineering change order.
     *
     * @param dctx the dispatch context
     * @param context service context, see {@code servicedef/dev-services.xml#mfgDevEcoSave}
     * @return the service result containing {@code ecoId}
     */
    public static Map<String, Object> devEcoSave(DispatchContext dctx, Map<String, ? extends Object> context) {
        String denied = MfgSecurity.permissionProblem(dctx, context, MfgConstants.PERM_ENG);
        if (denied != null) {
            return ServiceUtil.returnError(denied);
        }
        Delegator delegator = dctx.getDelegator();
        String ecoId = MfgSupport.toText(context.get("ecoId"));
        String productCode = MfgSupport.toText(context.get("productCode"));
        String ecoTypeId = MfgSupport.toText(context.get("ecoTypeId"));
        if (productCode == null) {
            return ServiceUtil.returnError("productCode is required");
        }
        if (ecoTypeId != null && !isKnownEcoType(ecoTypeId)) {
            return ServiceUtil.returnError("Unknown change type [" + ecoTypeId + "]");
        }
        try {
            if (EntityQuery.use(delegator).from("MfgProduct").where("productCode", productCode).queryOne() == null) {
                return ServiceUtil.returnError("Unknown product model [" + productCode + "], create it first");
            }
            String toVersionId = MfgSupport.toText(context.get("toBomVersionId"));
            if (toVersionId != null) {
                GenericValue toVersion = EntityQuery.use(delegator).from("MfgBomVersion")
                        .where("bomVersionId", toVersionId).queryOne();
                if (toVersion == null) {
                    return ServiceUtil.returnError("Unknown BOM version [" + toVersionId + "]");
                }
                if (!productCode.equals(toVersion.getString("productCode"))) {
                    return ServiceUtil.returnError("BOM version [" + toVersionId + "] belongs to another model");
                }
            }
            GenericValue eco;
            String isNew = "N";
            if (ecoId == null) {
                ecoId = nextEcoId(delegator, LocalDate.now());
                eco = delegator.makeValue("MfgDevEco");
                eco.set("ecoId", ecoId);
                eco.set("productCode", productCode);
                eco.set("statusId", MfgConstants.ECO_DRAFT);
                eco.set("requestedDate", java.sql.Date.valueOf(LocalDate.now()));
                isNew = "Y";
            } else {
                eco = EntityQuery.use(delegator).from("MfgDevEco").where("ecoId", ecoId).queryOne();
                if (eco == null) {
                    eco = delegator.makeValue("MfgDevEco");
                    eco.set("ecoId", ecoId);
                    eco.set("productCode", productCode);
                    eco.set("statusId", MfgConstants.ECO_DRAFT);
                    eco.set("requestedDate", java.sql.Date.valueOf(LocalDate.now()));
                    isNew = "Y";
                } else if (MfgConstants.ECO_APPLIED.equals(eco.getString("statusId"))) {
                    return ServiceUtil.returnError("Change order [" + ecoId + "] is already applied");
                } else {
                    eco.set("productCode", productCode);
                }
            }
            if (ecoTypeId != null) {
                eco.set("ecoTypeId", ecoTypeId);
            }
            if (context.containsKey("projectCode")) {
                eco.set("projectCode", MfgSupport.toText(context.get("projectCode")));
            }
            if (context.containsKey("reason")) {
                eco.set("reason", MfgSupport.toText(context.get("reason")));
            }
            if (context.containsKey("changeSummary")) {
                eco.set("changeSummary", MfgSupport.toText(context.get("changeSummary")));
            }
            if (context.containsKey("requestedBy")) {
                eco.set("requestedBy", MfgSupport.toText(context.get("requestedBy")));
            }
            if (context.containsKey("approvedBy")) {
                eco.set("approvedBy", MfgSupport.toText(context.get("approvedBy")));
            }
            if (context.containsKey("effectiveDate")) {
                eco.set("effectiveDate", MfgSupport.parseDate(context.get("effectiveDate")));
            }
            if (context.containsKey("note")) {
                eco.set("note", MfgSupport.toText(context.get("note")));
            }
            if (toVersionId != null) {
                eco.set("toBomVersionId", toVersionId);
            }
            String fromVersionId = MfgSupport.toText(context.get("fromBomVersionId"));
            if (fromVersionId == null) {
                fromVersionId = MfgSupport.toText(eco.getString("fromBomVersionId"));
            }
            if (fromVersionId == null) {
                fromVersionId = MfgProductServices.currentBomVersionId(delegator, productCode);
            }
            eco.set("fromBomVersionId", fromVersionId);
            delegator.createOrStore(eco);

            Map<String, Object> result = ServiceUtil.returnSuccess();
            result.put("ecoId", ecoId);
            result.put("isNew", isNew);
            return result;
        } catch (GenericEntityException e) {
            return ServiceUtil.returnError("Could not save the change order: " + e.getMessage());
        }
    }

    /**
     * Lists change orders, newest first.
     *
     * @param dctx the dispatch context
     * @param context service context, see {@code servicedef/dev-services.xml#mfgDevEcoList}
     * @return the service result containing {@code ecos}
     */
    public static Map<String, Object> devEcoList(DispatchContext dctx, Map<String, ? extends Object> context) {
        Delegator delegator = dctx.getDelegator();
        String statusId = MfgSupport.toText(context.get("statusId"));
        String productCode = MfgSupport.toText(context.get("productCode"));
        try {
            List<GenericValue> ecos = EntityQuery.use(delegator).from("MfgDevEco")
                    .orderBy("-createdStamp").queryList();
            List<Map<String, Object>> list = new ArrayList<>(ecos.size());
            long open = 0;
            for (GenericValue eco : ecos) {
                String status = eco.getString("statusId");
                if (MfgConstants.ECO_DRAFT.equals(status) || MfgConstants.ECO_APPROVED.equals(status)) {
                    open++;
                }
                if (statusId != null && !statusId.equals(status)) {
                    continue;
                }
                if (productCode != null && !productCode.equals(eco.getString("productCode"))) {
                    continue;
                }
                list.add(toEcoMap(eco));
            }
            Map<String, Object> result = ServiceUtil.returnSuccess();
            result.put("statusId", statusId);
            result.put("openCount", Long.valueOf(open));
            MfgPaging.putPage(result, "ecos",
                    MfgPaging.filterByKeyword(list, MfgSupport.toText(context.get("search")), "ecoId", "productCode", "projectCode",
                            "ecoTypeId", "reason", "statusId", "requestedBy"),
                    MfgPaging.pageOf(context), MfgPaging.pageSizeOf(context), MfgSupport.toText(context.get("search")));
            return result;
        } catch (GenericEntityException e) {
            return ServiceUtil.returnError("Could not read the change orders: " + e.getMessage());
        }
    }

    /**
     * Returns one change order with the BOM versions it switches between.
     *
     * @param dctx the dispatch context
     * @param context service context, see {@code servicedef/dev-services.xml#mfgDevEcoDetail}
     * @return the service result containing {@code eco}, {@code fromVersion}, {@code toVersion}
     */
    public static Map<String, Object> devEcoDetail(DispatchContext dctx, Map<String, ? extends Object> context) {
        Delegator delegator = dctx.getDelegator();
        String ecoId = MfgSupport.toText(context.get("ecoId"));
        if (ecoId == null) {
            Map<String, Object> empty = ServiceUtil.returnSuccess();
            empty.put("versions", new ArrayList<Map<String, Object>>());
            return empty;
        }
        try {
            GenericValue eco = EntityQuery.use(delegator).from("MfgDevEco").where("ecoId", ecoId).queryOne();
            if (eco == null) {
                return ServiceUtil.returnError("Unknown change order [" + ecoId + "]");
            }
            String productCode = eco.getString("productCode");
            Map<String, Object> result = ServiceUtil.returnSuccess();
            result.put("eco", toEcoMap(eco));
            String fromId = MfgSupport.toText(eco.getString("fromBomVersionId"));
            String toId = MfgSupport.toText(eco.getString("toBomVersionId"));
            if (fromId != null) {
                GenericValue from = EntityQuery.use(delegator).from("MfgBomVersion")
                        .where("bomVersionId", fromId).queryOne();
                if (from != null) {
                    result.put("fromVersion", MfgProductServices.toBomVersionMap(delegator, from));
                }
            }
            if (toId != null) {
                GenericValue to = EntityQuery.use(delegator).from("MfgBomVersion")
                        .where("bomVersionId", toId).queryOne();
                if (to != null) {
                    result.put("toVersion", MfgProductServices.toBomVersionMap(delegator, to));
                }
            }
            List<Map<String, Object>> versions = new ArrayList<>();
            for (GenericValue version : MfgProductServices.bomVersionsOf(delegator, productCode)) {
                versions.add(MfgProductServices.toBomVersionMap(delegator, version));
            }
            result.put("versions", versions);
            return result;
        } catch (GenericEntityException e) {
            return ServiceUtil.returnError("Could not read the change order: " + e.getMessage());
        }
    }

    /**
     * Approves a draft change order.
     *
     * @param dctx the dispatch context
     * @param context service context, see {@code servicedef/dev-services.xml#mfgDevEcoApprove}
     * @return the service result containing the new {@code statusId}
     */
    public static Map<String, Object> devEcoApprove(DispatchContext dctx, Map<String, ? extends Object> context) {
        String denied = MfgSecurity.permissionProblem(dctx, context, MfgConstants.PERM_ENG);
        if (denied != null) {
            return ServiceUtil.returnError(denied);
        }
        Delegator delegator = dctx.getDelegator();
        String ecoId = MfgSupport.toText(context.get("ecoId"));
        if (ecoId == null) {
            return ServiceUtil.returnError("ecoId is required");
        }
        try {
            GenericValue eco = EntityQuery.use(delegator).from("MfgDevEco").where("ecoId", ecoId).queryOne();
            if (eco == null) {
                return ServiceUtil.returnError("Unknown change order [" + ecoId + "]");
            }
            if (!MfgConstants.ECO_DRAFT.equals(eco.getString("statusId"))) {
                return ServiceUtil.returnError("Only a draft change order can be approved, [" + ecoId
                        + "] is " + eco.getString("statusId"));
            }
            eco.set("statusId", MfgConstants.ECO_APPROVED);
            eco.set("approvedBy", MfgSupport.toText(context.get("approvedBy")));
            eco.store();
            Map<String, Object> result = ServiceUtil.returnSuccess();
            result.put("ecoId", ecoId);
            result.put("statusId", MfgConstants.ECO_APPROVED);
            return result;
        } catch (GenericEntityException e) {
            return ServiceUtil.returnError("Could not approve the change order: " + e.getMessage());
        }
    }

    /**
     * Applies an approved change order: the model switches to the target BOM version when one is
     * given.
     *
     * @param dctx the dispatch context
     * @param context service context, see {@code servicedef/dev-services.xml#mfgDevEcoApply}
     * @return the service result containing the new {@code statusId}
     */
    public static Map<String, Object> devEcoApply(DispatchContext dctx, Map<String, ? extends Object> context) {
        String denied = MfgSecurity.permissionProblem(dctx, context, MfgConstants.PERM_ENG);
        if (denied != null) {
            return ServiceUtil.returnError(denied);
        }
        Delegator delegator = dctx.getDelegator();
        String ecoId = MfgSupport.toText(context.get("ecoId"));
        if (ecoId == null) {
            return ServiceUtil.returnError("ecoId is required");
        }
        try {
            GenericValue eco = EntityQuery.use(delegator).from("MfgDevEco").where("ecoId", ecoId).queryOne();
            if (eco == null) {
                return ServiceUtil.returnError("Unknown change order [" + ecoId + "]");
            }
            String statusId = eco.getString("statusId");
            if (!ecoAppliable(statusId)) {
                return ServiceUtil.returnError("Only an approved change order can be applied, [" + ecoId
                        + "] is " + statusId);
            }
            String toVersionId = MfgSupport.toText(eco.getString("toBomVersionId"));
            if (toVersionId != null) {
                GenericValue version = EntityQuery.use(delegator).from("MfgBomVersion")
                        .where("bomVersionId", toVersionId).queryOne();
                if (version == null) {
                    return ServiceUtil.returnError("Unknown BOM version [" + toVersionId + "]");
                }
                String productCode = eco.getString("productCode");
                GenericValue product = EntityQuery.use(delegator).from("MfgProduct")
                        .where("productCode", productCode).queryOne();
                for (GenericValue other : MfgProductServices.bomVersionsOf(delegator, productCode)) {
                    if (!toVersionId.equals(other.getString("bomVersionId"))
                            && MfgConstants.BOM_RELEASED.equals(other.getString("statusId"))) {
                        other.set("statusId", MfgConstants.BOM_OBSOLETE);
                        other.store();
                    }
                }
                version.set("statusId", MfgConstants.BOM_RELEASED);
                version.set("effectiveDate", java.sql.Date.valueOf(LocalDate.now()));
                version.store();
                if (product != null) {
                    product.set("currentBomVersionId", toVersionId);
                    product.store();
                }
            }
            eco.set("statusId", MfgConstants.ECO_APPLIED);
            eco.set("effectiveDate", java.sql.Date.valueOf(LocalDate.now()));
            eco.store();
            Map<String, Object> result = ServiceUtil.returnSuccess();
            result.put("ecoId", ecoId);
            result.put("statusId", MfgConstants.ECO_APPLIED);
            result.put("bomVersionId", toVersionId);
            return result;
        } catch (GenericEntityException e) {
            return ServiceUtil.returnError("Could not apply the change order: " + e.getMessage());
        }
    }

    /**
     * Cancels a change order that has not been applied.
     *
     * @param dctx the dispatch context
     * @param context service context, see {@code servicedef/dev-services.xml#mfgDevEcoCancel}
     * @return the service result containing the new {@code statusId}
     */
    public static Map<String, Object> devEcoCancel(DispatchContext dctx, Map<String, ? extends Object> context) {
        String denied = MfgSecurity.permissionProblem(dctx, context, MfgConstants.PERM_ENG);
        if (denied != null) {
            return ServiceUtil.returnError(denied);
        }
        Delegator delegator = dctx.getDelegator();
        String ecoId = MfgSupport.toText(context.get("ecoId"));
        if (ecoId == null) {
            return ServiceUtil.returnError("ecoId is required");
        }
        try {
            GenericValue eco = EntityQuery.use(delegator).from("MfgDevEco").where("ecoId", ecoId).queryOne();
            if (eco == null) {
                return ServiceUtil.returnError("Unknown change order [" + ecoId + "]");
            }
            if (MfgConstants.ECO_APPLIED.equals(eco.getString("statusId"))) {
                return ServiceUtil.returnError("Change order [" + ecoId + "] is already applied, it cannot be cancelled");
            }
            eco.set("statusId", MfgConstants.ECO_CANCELLED);
            eco.store();
            Map<String, Object> result = ServiceUtil.returnSuccess();
            result.put("ecoId", ecoId);
            result.put("statusId", MfgConstants.ECO_CANCELLED);
            return result;
        } catch (GenericEntityException e) {
            return ServiceUtil.returnError("Could not cancel the change order: " + e.getMessage());
        }
    }

    static boolean isKnownEcoStatus(String statusId) {
        return MfgConstants.ECO_DRAFT.equals(statusId) || MfgConstants.ECO_APPROVED.equals(statusId) || MfgConstants.ECO_APPLIED.equals(statusId)
                || MfgConstants.ECO_CANCELLED.equals(statusId);
    }
}
