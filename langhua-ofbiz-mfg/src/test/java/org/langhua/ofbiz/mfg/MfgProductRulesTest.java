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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.time.LocalDate;
import java.util.List;

import org.junit.Test;

/**
 * The engineering side: the BOM version identifiers, the development tracks and the stage ladder,
 * the change orders, the attachment types and the two BOM formats.
 */
public class MfgProductRulesTest {

    @Test
    public void numbersTheNextBomVersion() {
        List<String> none = new java.util.ArrayList<>();
        assertEquals(1L, MfgProductServices.nextBomVersionNo(none, "BT5"));

        List<String> one = java.util.Arrays.asList("BT5-V1");
        assertEquals(2L, MfgProductServices.nextBomVersionNo(one, "BT5"));

        // gaps do not matter, the highest number wins
        List<String> gap = java.util.Arrays.asList("BT5-V1", "BT5-V3", "BT5-V2");
        assertEquals(4L, MfgProductServices.nextBomVersionNo(gap, "BT5"));

        // versions of another model are ignored, and a suffix that is not a number is skipped
        List<String> mixed = java.util.Arrays.asList("BT6-V9", "BT5-VX");
        assertEquals(1L, MfgProductServices.nextBomVersionNo(mixed, "BT5"));
    }

    @Test
    public void countsWhatTheProductBoardShows() {
        // three models: one in mass production with a BOM, one in design with a BOM but nobody on it,
        // one in concept with neither
        List<java.util.Map<String, Object>> models = new java.util.ArrayList<>();
        models.add(model("M1", "MFG_STAGE_MASS", "M1-V1", 5, 5));
        models.add(model("M2", "MFG_STAGE_DESIGN", "M2-V1", 2, 3));
        models.add(model("M3", "MFG_STAGE_CONCEPT", null, 0, 0));
        java.util.Set<String> withProjects = new java.util.HashSet<>(java.util.Arrays.asList("M1"));
        java.util.Map<String, Object> summary = MfgProductServices.productSummary(models, withProjects);
        assertEquals(3, summary.get("modelCount"));
        assertEquals(1, summary.get("massCount"));
        assertEquals(1, summary.get("noBomCount"));
        assertEquals(2, summary.get("noProjectCount"));
        assertEquals(7, summary.get("gatePassed"));
        assertEquals(8, summary.get("gateReviewed"));
        // five gates per model, less the eight already written down
        assertEquals(7, summary.get("gatePending"));
        // nothing at all is not an error
        assertEquals(0, MfgProductServices.productSummary(new java.util.ArrayList<>(), withProjects)
                .get("gatePending"));
    }

    @Test
    public void countsWhatTheDevelopmentBoardShows() {
        List<java.util.Map<String, Object>> rows = new java.util.ArrayList<>();
        rows.add(devRow("MFG_DEV_ACTIVE", "M1", 2, 0, 0));
        rows.add(devRow(null, "M2", 1, 0, 0));
        rows.add(devRow("MFG_DEV_ACTIVE", null, 0, 0, 12));
        rows.add(devRow("MFG_DEV_ONHOLD", "M3", 0, 0, 0));
        rows.add(devRow("MFG_DEV_DONE", "M4", 0, 1, 0));
        rows.add(devRow("MFG_DEV_CANCELLED", "M5", 0, 0, 0));
        java.util.Map<String, Object> summary = MfgDevServices.projectSummary(rows);
        assertEquals(6, summary.get("projectCount"));
        // an empty status reads as running, the same way the list reads it
        assertEquals(3, summary.get("activeCount"));
        assertEquals(1, summary.get("holdCount"));
        assertEquals(1, summary.get("doneCount"));
        assertEquals(1, summary.get("cancelledCount"));
        assertEquals(1, summary.get("noModelCount"));
        // a late project is counted where it stands and again as late
        assertEquals(1, summary.get("overdueCount"));
        assertEquals(3, summary.get("openIssueTotal"));
        assertEquals(1, summary.get("openEcoTotal"));
    }

    @Test
    public void saysHowLateAProjectIs() {
        LocalDate today = LocalDate.of(2026, 11, 3);
        // a step planned ten days ago and not finished
        java.util.Map<String, Object> late = MfgDevServices.overdueOf(today,
                LocalDate.of(2026, 12, 31), null, "MFG_DEV_ACTIVE",
                "MFG_PCB_LAYOUT", LocalDate.of(2026, 10, 24));
        assertEquals(10, late.get("days"));
        assertTrue(String.valueOf(late.get("reason")).contains("10"));
        // the launch date is what slipped, the step is still on schedule
        java.util.Map<String, Object> launch = MfgDevServices.overdueOf(today,
                LocalDate.of(2026, 10, 20), null, "MFG_DEV_ACTIVE",
                "MFG_PCB_LAYOUT", LocalDate.of(2026, 11, 20));
        assertEquals(14, launch.get("days"));
        // nothing is late yet
        assertEquals(0, MfgDevServices.overdueOf(today, LocalDate.of(2026, 12, 31), null,
                "MFG_DEV_ACTIVE", "MFG_PCB_LAYOUT", LocalDate.of(2026, 11, 20)).get("days"));
        // launched or cancelled projects have no deadline left to miss
        assertEquals(0, MfgDevServices.overdueOf(today, LocalDate.of(2026, 10, 20), LocalDate.of(2026, 10, 25),
                "MFG_DEV_DONE", "MFG_PCB_LAYOUT", LocalDate.of(2026, 10, 24)).get("days"));
        assertEquals(0, MfgDevServices.overdueOf(today, LocalDate.of(2026, 10, 20), null,
                "MFG_DEV_CANCELLED", "MFG_PCB_LAYOUT", LocalDate.of(2026, 10, 24)).get("days"));
    }

    @Test
    public void measuresEachStepFromTheEndOfTheOneBefore() {
        // the first step counts from the day the project was created, so the waiting time is in it
        List<String> catalog = java.util.Arrays.asList("MFG_PCB_BREADBOARD", "MFG_PCB_SCHEMATIC", "MFG_PCB_LAYOUT");
        java.util.Map<String, LocalDate> done = new java.util.LinkedHashMap<>();
        done.put("MFG_PCB_BREADBOARD", LocalDate.of(2026, 10, 5));
        done.put("MFG_PCB_SCHEMATIC", LocalDate.of(2026, 10, 12));
        java.util.Map<String, Double> days = MfgDevServices.stepDays(catalog, LocalDate.of(2026, 10, 1), done);
        assertEquals(4.0, days.get("MFG_PCB_BREADBOARD").doubleValue(), 0.001);
        assertEquals(7.0, days.get("MFG_PCB_SCHEMATIC").doubleValue(), 0.001);
        // a step nobody finished has no duration
        assertEquals(2, days.size());
    }

    @Test
    public void fitsALearningCurveOverTheFinishedProjects() {
        // three projects of one route on an exact 90% Wright curve: T(k) = 100 * 0.9^(log2 k)
        double b = Math.log(0.9) / Math.log(2.0);
        List<java.util.Map<String, Double>> history = curveHistory(b);
        java.util.Map<String, Object> estimate = MfgDevServices.durationOf(
                java.util.Arrays.asList("A", "B"), history, new java.util.LinkedHashMap<String, Double>(),
                LocalDate.of(2026, 11, 3), LocalDate.of(2026, 12, 31));
        assertEquals(3, estimate.get("samples"));
        assertEquals(b, ((Double) estimate.get("slope")).doubleValue(), 0.01);
        assertEquals(90.0, ((Double) estimate.get("rate")).doubleValue(), 0.5);
        // the next project of the curve: 100 * 4^b = 81 days
        assertEquals(81.0, ((Double) estimate.get("totalDays")).doubleValue(), 1.0);
        // the split follows the history: 60 / 40
        List<java.util.Map<String, Object>> steps = stepsOf(estimate);
        assertEquals(48.6, ((Double) steps.get(0).get("plannedDays")).doubleValue(), 1.0);
        assertEquals(32.4, ((Double) steps.get(1).get("plannedDays")).doubleValue(), 1.0);
        assertEquals(81L, estimate.get("remainingDays"));
        assertEquals(java.sql.Date.valueOf(LocalDate.of(2027, 1, 23)), estimate.get("finishDate"));
        // 81 days from 11-03 lands on 01-23, which is 23 days after the promised 12-31
        assertEquals(23L, estimate.get("deltaDays"));
    }

    @Test
    public void saysSoWhenThereIsNothingToFitYet() {
        List<java.util.Map<String, Double>> history = new java.util.ArrayList<>();
        history.add(twoSteps(60, 40));
        history.add(twoSteps(54, 36));
        java.util.Map<String, Object> estimate = MfgDevServices.durationOf(
                java.util.Arrays.asList("A", "B"), history, new java.util.LinkedHashMap<String, Double>(),
                LocalDate.of(2026, 11, 3), null);
        assertEquals(2, estimate.get("samples"));
        assertNotNull(estimate.get("message"));
        // no curve and nothing to fall back on, no dates: the page shows the counts and says why
        assertNull(estimate.get("finishDate"));
        assertNull(estimate.get("steps"));
    }

    @Test
    public void fallsBackToTheMedianOfEachStepWhenTheCurveCannotBeFitted() {
        // only one finished project: no curve, but three projects walked the steps, so the medians
        // of what they took still say how long a step lasts
        List<java.util.Map<String, Double>> history = new java.util.ArrayList<>();
        history.add(twoSteps(60, 40));
        List<java.util.Map<String, Double>> partial = new java.util.ArrayList<>();
        partial.add(twoSteps(40, 20));
        partial.add(twoSteps(60, 40));
        partial.add(twoSteps(50, 30));
        java.util.Map<String, Object> estimate = MfgDevServices.durationOf(
                java.util.Arrays.asList("A", "B"), history, partial,
                new java.util.LinkedHashMap<String, Double>(), LocalDate.of(2026, 11, 3), null);
        assertEquals("MEDIAN", estimate.get("mode"));
        assertNull(estimate.get("message"));
        // no learning rate is invented when there is no curve to fit
        assertNull(estimate.get("rate"));
        assertNull(estimate.get("slope"));
        assertTrue(String.valueOf(estimate.get("note")).contains("3"));
        // step A: median of 40 / 60 / 50 = 50, step B: median of 20 / 40 / 30 = 30
        List<java.util.Map<String, Object>> steps = stepsOf(estimate);
        assertEquals(50.0, ((Double) steps.get(0).get("plannedDays")).doubleValue(), 0.001);
        assertEquals(30.0, ((Double) steps.get(1).get("plannedDays")).doubleValue(), 0.001);
        assertEquals(80.0, ((Double) estimate.get("totalDays")).doubleValue(), 0.001);
        assertEquals(80L, estimate.get("remainingDays"));
        assertEquals(java.sql.Date.valueOf(LocalDate.of(2027, 1, 22)), estimate.get("finishDate"));
    }

    @Test
    public void chargesAStepNobodyWalkedAtTheTypicalStepOfTheRoute() {
        // three projects, all stopped after the first step: the second step still gets a number
        List<java.util.Map<String, Double>> partial = new java.util.ArrayList<>();
        java.util.Map<String, Double> first = new java.util.LinkedHashMap<>();
        first.put("A", Double.valueOf(12.0));
        partial.add(first);
        java.util.Map<String, Double> second = new java.util.LinkedHashMap<>();
        second.put("A", Double.valueOf(8.0));
        partial.add(second);
        java.util.Map<String, Object> estimate = MfgDevServices.durationOf(
                java.util.Arrays.asList("A", "B"), new java.util.ArrayList<java.util.Map<String, Double>>(),
                partial, new java.util.LinkedHashMap<String, Double>(), LocalDate.of(2026, 11, 3), null);
        assertEquals("MEDIAN", estimate.get("mode"));
        List<java.util.Map<String, Object>> steps = stepsOf(estimate);
        assertEquals(10.0, ((Double) steps.get(0).get("plannedDays")).doubleValue(), 0.001);
        // nobody walked B, so it is charged at the median of the steps that were walked
        assertEquals(10.0, ((Double) steps.get(1).get("plannedDays")).doubleValue(), 0.001);
        assertEquals(20.0, ((Double) estimate.get("totalDays")).doubleValue(), 0.001);
    }

    @Test
    public void countsOnlyTheStepsStillToComeAsRemaining() {
        double b = Math.log(0.9) / Math.log(2.0);
        List<java.util.Map<String, Double>> history = curveHistory(b);
        java.util.Map<String, Double> done = new java.util.LinkedHashMap<>();
        done.put("A", Double.valueOf(5.0));
        java.util.Map<String, Object> estimate = MfgDevServices.durationOf(
                java.util.Arrays.asList("A", "B"), history, done, LocalDate.of(2026, 11, 3), null);
        // step A is behind us, only step B is still to come (40% of 81 days)
        assertEquals(5.0, ((Double) estimate.get("spentDays")).doubleValue(), 0.001);
        assertEquals(32L, estimate.get("remainingDays"));
        List<java.util.Map<String, Object>> steps = stepsOf(estimate);
        assertEquals(Boolean.TRUE, steps.get(0).get("done"));
        assertEquals(Boolean.FALSE, steps.get(1).get("done"));
        // without a planned launch date there is nothing to compare the finish date with
        assertNull(estimate.get("deltaDays"));
        assertNull(estimate.get("plannedLaunch"));
    }

    /** Three finished projects of one route, on an exact Wright curve, split 60/40 over two steps. */
    private static List<java.util.Map<String, Double>> curveHistory(double b) {
        List<java.util.Map<String, Double>> history = new java.util.ArrayList<>();
        for (int k = 1; k <= 3; k++) {
            double total = 100.0 * Math.pow(k, b);
            history.add(twoSteps(total * 0.6, total * 0.4));
        }
        return history;
    }

    private static java.util.Map<String, Double> twoSteps(double first, double second) {
        java.util.Map<String, Double> map = new java.util.LinkedHashMap<>();
        map.put("A", Double.valueOf(first));
        map.put("B", Double.valueOf(second));
        return map;
    }

    @SuppressWarnings("unchecked")
    private static List<java.util.Map<String, Object>> stepsOf(java.util.Map<String, Object> estimate) {
        return (List<java.util.Map<String, Object>>) estimate.get("steps");
    }

    private static java.util.Map<String, Object> devRow(String statusId, String productCode,
            int openIssues, int openEcos, int overdueDays) {
        java.util.Map<String, Object> map = new java.util.LinkedHashMap<>();
        map.put("statusId", statusId);
        map.put("productCode", productCode);
        map.put("openIssueCount", Integer.valueOf(openIssues));
        map.put("openEcoCount", Integer.valueOf(openEcos));
        map.put("overdueDays", Integer.valueOf(overdueDays));
        return map;
    }

    private static java.util.Map<String, Object> model(String code, String stage, String bomVersion,
            int passed, int reviewed) {
        java.util.Map<String, Object> map = new java.util.LinkedHashMap<>();
        map.put("productCode", code);
        map.put("devStageId", stage);
        map.put("currentBomVersionId", bomVersion);
        map.put("passedCount", Integer.valueOf(passed));
        map.put("reviewCount", Integer.valueOf(reviewed));
        return map;
    }

    @Test
    public void walksTheDevelopmentStageLadder() {
        assertEquals("MFG_STAGE_DESIGN", MfgDevServices.nextDevStage("MFG_STAGE_CONCEPT"));
        assertEquals("MFG_STAGE_PROTOTYPE", MfgDevServices.nextDevStage("MFG_STAGE_DESIGN"));
        assertEquals("MFG_STAGE_TRIAL", MfgDevServices.nextDevStage("MFG_STAGE_PROTOTYPE"));
        assertEquals("MFG_STAGE_MASS", MfgDevServices.nextDevStage("MFG_STAGE_TRIAL"));
        // mass production is the last stage
        assertNull(MfgDevServices.nextDevStage("MFG_STAGE_MASS"));
        assertNull(MfgDevServices.nextDevStage("NOT_A_STAGE"));
        assertNull(MfgDevServices.nextDevStage(null));

        assertEquals(0, MfgDevServices.devStageIndex("MFG_STAGE_CONCEPT"));
        assertEquals(4, MfgDevServices.devStageIndex("MFG_STAGE_MASS"));
        assertEquals(-1, MfgDevServices.devStageIndex("NOT_A_STAGE"));
    }

    @Test
    public void onlyAPassMovesTheProjectOn() {
        assertTrue(MfgDevServices.stageAdvancesOn("MFG_REVIEW_PASS"));
        assertTrue(MfgDevServices.stageAdvancesOn("MFG_REVIEW_CONDITIONAL"));
        assertTrue(!MfgDevServices.stageAdvancesOn("MFG_REVIEW_FAIL"));
        assertTrue(!MfgDevServices.stageAdvancesOn(null));
    }

    @Test
    public void coloursTheGatesByTheirResult() {
        // a conditional pass is not the same as a clean one, and a failed review has to stand out
        assertEquals("done", MfgProductServices.toneOf("gate",
                review("MFG_STAGE_CONCEPT", null, null, "MFG_REVIEW_PASS")));
        assertEquals("conditional", MfgProductServices.toneOf("gate",
                review("MFG_STAGE_DESIGN", null, null, "MFG_REVIEW_CONDITIONAL")));
        assertEquals("fail", MfgProductServices.toneOf("gate",
                review("MFG_STAGE_PROTOTYPE", null, null, "MFG_REVIEW_FAIL")));
        // a gate with dates but without a result is still to be reviewed
        assertEquals("plan", MfgProductServices.toneOf("gate",
                review("MFG_STAGE_TRIAL", LocalDate.of(2026, 10, 1), null, null)));
        assertEquals("live", MfgProductServices.toneOf("project",
                project("DEV-261003-08", "MFG_STAGE_DESIGN", null, null, null, "MFG_DEV_ACTIVE")));
        // a launched and a cancelled project are not the same thing any more
        assertEquals("launched", MfgProductServices.toneOf("project",
                project("DEV-261003-10", "MFG_STAGE_MASS", null, null, null, "MFG_DEV_DONE")));
        assertEquals("cancelled", MfgProductServices.toneOf("project",
                project("DEV-261003-09", "MFG_STAGE_DESIGN", null, null, null, "MFG_DEV_CANCELLED")));
        assertEquals("hold", MfgProductServices.toneOf("project",
                project("DEV-261003-11", "MFG_STAGE_DESIGN", null, null, null, "MFG_DEV_ONHOLD")));
    }

    @Test
    public void drawsTheGatesAndTheProjectsOnOneMonthAxis() {
        List<java.util.Map<String, Object>> reviews = new java.util.ArrayList<>();
        reviews.add(review("MFG_STAGE_CONCEPT", LocalDate.of(2026, 9, 3), LocalDate.of(2026, 9, 10), "MFG_REVIEW_PASS"));
        reviews.add(review("MFG_STAGE_DESIGN", LocalDate.of(2026, 10, 1), null, null));
        List<java.util.Map<String, Object>> projects = new java.util.ArrayList<>();
        projects.add(project("DEV-261003-01", "MFG_STAGE_DESIGN", LocalDate.of(2026, 9, 15),
                LocalDate.of(2026, 11, 30), null, "MFG_DEV_ACTIVE"));
        projects.add(project("DEV-261003-02", "MFG_STAGE_CONCEPT", LocalDate.of(2026, 9, 20),
                null, null, "MFG_DEV_CANCELLED"));

        java.util.Map<String, Object> gantt = MfgProductServices.ganttOf(reviews, projects, LocalDate.of(2026, 10, 5));
        List<?> months = (List<?>) gantt.get("months");
        // the window is rounded out to whole months: September through November
        assertEquals(3, months.size());
        assertEquals("2026-09", ((java.util.Map<?, ?>) months.get(0)).get("label"));
        assertEquals("2026-11", ((java.util.Map<?, ?>) months.get(2)).get("label"));
        assertEquals(Boolean.FALSE, gantt.get("empty"));

        List<?> rows = (List<?>) gantt.get("rows");
        // one stage at a time: the review of the stage, then the projects working on that stage,
        // then the next stage - so a project is read under the stage it belongs to
        assertEquals(4, rows.size());
        java.util.Map<?, ?> firstGate = (java.util.Map<?, ?>) rows.get(0);
        assertEquals("gate", firstGate.get("kind"));
        assertEquals("MFG_STAGE_CONCEPT", firstGate.get("stageId"));
        // a signed gate is green, the open one is only planned
        assertEquals("done", firstGate.get("tone"));
        assertEquals("2026-09-03", firstGate.get("startText"));
        assertEquals("09-03", firstGate.get("startShort"));

        java.util.Map<?, ?> cancelled = (java.util.Map<?, ?>) rows.get(1);
        assertEquals("project", cancelled.get("kind"));
        assertEquals("MFG_STAGE_CONCEPT", cancelled.get("stageId"));
        // a cancelled project has a colour of its own (red), not the grey of an unreviewed gate
        assertEquals("cancelled", cancelled.get("tone"));

        java.util.Map<?, ?> designGate = (java.util.Map<?, ?>) rows.get(2);
        assertEquals("gate", designGate.get("kind"));
        assertEquals("MFG_STAGE_DESIGN", designGate.get("stageId"));
        assertEquals("plan", designGate.get("tone"));
        // the gate is still open, so its bar reaches today
        assertEquals(Boolean.FALSE, designGate.get("noDates"));
        assertEquals("2026-10-05", designGate.get("endText"));

        java.util.Map<?, ?> live = (java.util.Map<?, ?>) rows.get(3);
        assertEquals("project", live.get("kind"));
        assertEquals("MFG_STAGE_DESIGN", live.get("stageId"));
        assertEquals("live", live.get("tone"));
        assertEquals(Boolean.FALSE, live.get("noDates"));
        // the dates of a bar are written inside it only when it is wide enough, else just outside
        assertEquals("09-15 → 11-30", live.get("caption"));
        assertEquals(Boolean.TRUE, live.get("labelInside"));
        assertEquals(Boolean.FALSE, firstGate.get("labelInside"));
        assertEquals("09-03 → 09-10", firstGate.get("caption"));
        assertEquals("after", firstGate.get("tagSide"));

        // the bars are percentages the stylesheet can use as they are, never locale formatted
        String left = (String) live.get("left");
        assertTrue(left.matches("\\d+\\.\\d\\d"));
        assertTrue(Double.parseDouble(left) > 0.0);
        assertTrue(Double.parseDouble(left) < 100.0);
        // the window starts on the first day of September, so the gate three days in is near the left edge
        assertEquals("2.20", firstGate.get("left"));
    }

    @Test
    public void keepsAProjectWithoutDatesAndSaysSo() {
        List<java.util.Map<String, Object>> projects = new java.util.ArrayList<>();
        projects.add(project("DEV-261003-03", "MFG_STAGE_CONCEPT", null, null, null, "MFG_DEV_ACTIVE"));

        java.util.Map<String, Object> gantt = MfgProductServices.ganttOf(new java.util.ArrayList<>(), projects,
                LocalDate.of(2026, 10, 5));
        // nothing has a date, so there is no axis to draw
        assertEquals(Boolean.TRUE, gantt.get("empty"));
        assertEquals(0, ((List<?>) gantt.get("rows")).size());

        // with one dated gate the project is kept and marked as not scheduled
        List<java.util.Map<String, Object>> reviews = new java.util.ArrayList<>();
        reviews.add(review("MFG_STAGE_CONCEPT", LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 1),
                "MFG_REVIEW_PASS"));
        gantt = MfgProductServices.ganttOf(reviews, projects, LocalDate.of(2026, 10, 5));
        List<?> rows = (List<?>) gantt.get("rows");
        assertEquals(2, rows.size());
        java.util.Map<?, ?> loose = (java.util.Map<?, ?>) rows.get(1);
        assertEquals(Boolean.TRUE, loose.get("noDates"));
        assertEquals("none", loose.get("tone"));
        assertEquals("", loose.get("startText"));

        // a one day gate is 3% of a one month window: the date goes just outside the bar
        java.util.Map<?, ?> gate = (java.util.Map<?, ?>) rows.get(0);
        assertEquals(Boolean.FALSE, gate.get("labelInside"));
        assertEquals("10-01", gate.get("caption"));
        assertEquals("after", gate.get("tagSide"));
        assertTrue(Double.parseDouble((String) gate.get("tagLeft")) > Double.parseDouble((String) gate.get("left")));
    }

    @Test
    public void groupsTheProjectsByTheStageTheyWorkOn() {
        List<java.util.Map<String, Object>> projects = new java.util.ArrayList<>();
        projects.add(project("DEV-261003-04", "MFG_STAGE_MASS", LocalDate.of(2026, 9, 1), null, null, null));
        projects.add(project("DEV-261003-05", "MFG_STAGE_DESIGN", LocalDate.of(2026, 9, 1), null, null, null));
        projects.add(project("DEV-261003-06", "MFG_STAGE_DESIGN", LocalDate.of(2026, 9, 2), null, null, null));
        projects.add(project("DEV-261003-07", null, LocalDate.of(2026, 9, 3), null, null, null));

        List<java.util.Map<String, Object>> groups = MfgProductServices.projectsByStage(projects);
        // ladder order, the project without a stage comes last and is not dropped
        assertEquals(3, groups.size());
        assertEquals("MFG_STAGE_DESIGN", groups.get(0).get("stageId"));
        assertEquals(2, ((List<?>) groups.get(0).get("projects")).size());
        assertEquals("MFG_STAGE_MASS", groups.get(1).get("stageId"));
        assertEquals("", groups.get(2).get("stageId"));
        assertEquals(1, ((List<?>) groups.get(2).get("projects")).size());
    }

    static java.util.Map<String, Object> review(String stageId, LocalDate planned, LocalDate done, String resultId) {
        java.util.Map<String, Object> row = new java.util.LinkedHashMap<>();
        row.put("stageId", stageId);
        row.put("plannedDate", planned == null ? null : java.sql.Date.valueOf(planned));
        row.put("doneDate", done == null ? null : java.sql.Date.valueOf(done));
        row.put("resultId", resultId);
        row.put("reviewer", null);
        row.put("note", null);
        return row;
    }

    static java.util.Map<String, Object> project(String code, String stageId, LocalDate started,
            LocalDate plannedLaunch, LocalDate actualLaunch, String statusId) {
        java.util.Map<String, Object> row = new java.util.LinkedHashMap<>();
        row.put("projectCode", code);
        row.put("projectName", code);
        row.put("trackId", "MFG_TRACK_PCB");
        row.put("stageId", stageId);
        row.put("statusId", statusId);
        row.put("ownerName", null);
        row.put("startDate", started == null ? null : java.sql.Timestamp.valueOf(started.atStartOfDay()));
        row.put("plannedLaunchDate", plannedLaunch == null ? null : java.sql.Date.valueOf(plannedLaunch));
        row.put("actualLaunchDate", actualLaunch == null ? null : java.sql.Date.valueOf(actualLaunch));
        return row;
    }

    @Test
    public void knowsTheDevelopmentAndChangeOrderCodes() {
        assertTrue(MfgDevServices.isKnownDevStage("MFG_STAGE_TRIAL"));
        assertTrue(!MfgDevServices.isKnownDevStage("MFG_STAGE_SOMETHING"));

        assertTrue(MfgDevServices.isKnownReviewResult("MFG_REVIEW_CONDITIONAL"));
        assertTrue(!MfgDevServices.isKnownReviewResult("PASS"));

        assertTrue(MfgDevServices.isKnownDevStatus("MFG_DEV_ONHOLD"));
        assertTrue(!MfgDevServices.isKnownDevStatus("MFG_DEV_BOGUS"));

        assertTrue(MfgDevServices.isKnownEcoStatus("MFG_ECO_APPLIED"));
        assertTrue(!MfgDevServices.isKnownEcoStatus("DRAFT"));

        assertTrue(MfgDevServices.isKnownEcoType("MFG_ECO_MATERIAL"));
        assertTrue(!MfgDevServices.isKnownEcoType("MFG_ECO_PRICE"));
    }

    @Test
    public void appliesOnlyAnApprovedChangeOrder() {
        assertTrue(MfgDevServices.ecoAppliable("MFG_ECO_APPROVED"));
        assertTrue(!MfgDevServices.ecoAppliable("MFG_ECO_DRAFT"));
        assertTrue(!MfgDevServices.ecoAppliable("MFG_ECO_APPLIED"));
        assertTrue(!MfgDevServices.ecoAppliable("MFG_ECO_CANCELLED"));
        assertTrue(!MfgDevServices.ecoAppliable(null));
    }

    @Test
    public void formatsDevelopmentProjectAndChangeOrderIds() {
        LocalDate day = LocalDate.of(2026, 10, 21);
        assertEquals("DEV-261021-01", MfgDevServices.formatDevProjectId(day, 1));
        assertEquals("DEV-261021-12", MfgDevServices.formatDevProjectId(day, 12));
        assertEquals("ECO-261021-01", MfgDevServices.formatEcoId(day, 1));
        assertEquals("ECO-261021-07", MfgDevServices.formatEcoId(day, 7));
    }

    @Test
    public void knowsTheStepsOfEachDevelopmentTrack() {
        assertEquals(5, MfgDevServices.stepsOfTrack(MfgConstants.TRACK_PCB).length);
        assertEquals(3, MfgDevServices.stepsOfTrack(MfgConstants.TRACK_FIRMWARE).length);
        assertEquals(7, MfgDevServices.stepsOfTrack(MfgConstants.TRACK_ENCLOSURE).length);
        assertEquals(0, MfgDevServices.stepsOfTrack("").length);
        assertEquals(0, MfgDevServices.stepsOfTrack(null).length);
        assertEquals(0, MfgDevServices.stepsOfTrack("MFG_TRACK_BOGUS").length);

        // the PCB track starts on the breadboard and ends on the hand soldered test
        assertEquals("MFG_PCB_BREADBOARD", MfgDevServices.stepsOfTrack(MfgConstants.TRACK_PCB)[0]);
        assertEquals("MFG_PCB_HAND_SOLDER", MfgDevServices.stepsOfTrack(MfgConstants.TRACK_PCB)[4]);
        // the enclosure track ends with the product assembly
        assertEquals("MFG_ENC_ASSEMBLY", MfgDevServices.stepsOfTrack(MfgConstants.TRACK_ENCLOSURE)[6]);

        assertTrue(MfgDevServices.isKnownTrack(MfgConstants.TRACK_ENCLOSURE));
        assertTrue(!MfgDevServices.isKnownTrack("MFG_TRACK_BOGUS"));

        assertTrue(MfgDevServices.isKnownStep(MfgConstants.TRACK_FIRMWARE, "MFG_FW_FLASH"));
        assertTrue(!MfgDevServices.isKnownStep(MfgConstants.TRACK_PCB, "MFG_FW_FLASH"));
        assertTrue(!MfgDevServices.isKnownStep("", "MFG_PCB_SCHEMATIC"));
    }

    @Test
    public void findsTheFirstOpenStep() {
        // nothing done yet, so the first step of the track is still open
        assertEquals("MFG_PCB_BREADBOARD",
                MfgDevServices.firstOpenStep(MfgConstants.TRACK_PCB, new java.util.ArrayList<String>()));

        java.util.List<String> done = java.util.Arrays.asList("MFG_PCB_BREADBOARD", "MFG_PCB_SCHEMATIC");
        assertEquals("MFG_PCB_LAYOUT", MfgDevServices.firstOpenStep(MfgConstants.TRACK_PCB, done));

        java.util.List<String> all = java.util.Arrays.asList(MfgConstants.PCB_STEPS);
        assertNull(MfgDevServices.firstOpenStep(MfgConstants.TRACK_PCB, all));

        // a track without steps has nothing to open
        assertNull(MfgDevServices.firstOpenStep("", done));
    }

    @Test
    public void countsSkippedStepsAsFinished() {
        assertTrue(MfgDevServices.stepFinished("MFG_STEP_DONE"));
        assertTrue(MfgDevServices.stepFinished("MFG_STEP_SKIPPED"));
        assertTrue(!MfgDevServices.stepFinished("MFG_STEP_DOING"));
        assertTrue(!MfgDevServices.stepFinished("MFG_STEP_TODO"));
        assertTrue(!MfgDevServices.stepFinished(null));

        assertTrue(MfgDevServices.isKnownStepStatus("MFG_STEP_DOING"));
        assertTrue(!MfgDevServices.isKnownStepStatus("DONE"));
    }

    @Test
    public void knowsTheAllowedPhotoTypes() {
        assertTrue(MfgAttachmentServices.isAllowedImageType("image/jpeg"));
        assertTrue(MfgAttachmentServices.isAllowedImageType("image/png"));
        assertTrue(MfgAttachmentServices.isAllowedImageType("IMAGE/GIF"));
        assertTrue(MfgAttachmentServices.isAllowedImageType("  image/png  "));
        assertTrue(!MfgAttachmentServices.isAllowedImageType("image/webp"));
        assertTrue(!MfgAttachmentServices.isAllowedImageType("application/pdf"));
        assertTrue(!MfgAttachmentServices.isAllowedImageType(""));
        assertTrue(!MfgAttachmentServices.isAllowedImageType(null));
    }

    @Test
    public void guessesThePhotoTypeFromTheFileName() {
        assertEquals("image/jpeg", MfgAttachmentServices.imageTypeOfFileName("front.JPG"));
        assertEquals("image/jpeg", MfgAttachmentServices.imageTypeOfFileName("C:\\photos\\back.jpeg"));
        assertEquals("image/png", MfgAttachmentServices.imageTypeOfFileName("board.png"));
        assertEquals("image/gif", MfgAttachmentServices.imageTypeOfFileName("anim.Gif"));
        assertEquals("application/pdf", MfgAttachmentServices.imageTypeOfFileName("report.pdf"));
        assertNull(MfgAttachmentServices.imageTypeOfFileName("notes.txt"));
        assertNull(MfgAttachmentServices.imageTypeOfFileName("noextension"));
        assertNull(MfgAttachmentServices.imageTypeOfFileName(null));
    }

    @Test
    public void allowsImagesAndPdfOnly() {
        assertTrue(MfgAttachmentServices.isAllowedAttachmentType("image/png"));
        assertTrue(MfgAttachmentServices.isAllowedAttachmentType("application/pdf"));
        assertTrue(MfgAttachmentServices.isAllowedAttachmentType("APPLICATION/PDF"));
        assertTrue(!MfgAttachmentServices.isAllowedAttachmentType("text/plain"));
        assertTrue(!MfgAttachmentServices.isAllowedAttachmentType("application/zip"));
        assertTrue(!MfgAttachmentServices.isAllowedAttachmentType(null));
        // the pdf is not an image, so it never gets a thumbnail
        assertTrue(!MfgAttachmentServices.isAllowedImageType("application/pdf"));
    }

    @Test
    public void knowsTheAttachmentOwnerTypes() {
        assertTrue(MfgAttachmentServices.isKnownAttachmentOwnerType("MFG_ATT_PRODUCT"));
        assertTrue(MfgAttachmentServices.isKnownAttachmentOwnerType("MFG_ATT_DEV_PROJECT"));
        assertTrue(MfgAttachmentServices.isKnownAttachmentOwnerType("MFG_ATT_WORK_ORDER"));
        assertTrue(MfgAttachmentServices.isKnownAttachmentOwnerType("MFG_ATT_DELIVERY"));
        assertTrue(MfgAttachmentServices.isKnownAttachmentOwnerType("MFG_ATT_CLAIM"));
        assertTrue(!MfgAttachmentServices.isKnownAttachmentOwnerType("MFG_ATT_BOGUS"));
        assertTrue(!MfgAttachmentServices.isKnownAttachmentOwnerType(null));
    }

    @Test
    public void cleansUploadedFileNames() {
        assertEquals("photo.png", MfgAttachmentServices.cleanFileName("C:\\Users\\me\\Pictures\\photo.png"));
        assertEquals("photo.png", MfgAttachmentServices.cleanFileName("/tmp/photo.png"));
        // characters outside letters, digits and dot dash underscore become underscores
        String cleaned = MfgAttachmentServices.cleanFileName("外壳 正面.jpg");
        assertTrue("expected only underscores and the extension, got " + cleaned, cleaned.matches("_+\\.jpg"));
        assertEquals("file", MfgAttachmentServices.cleanFileName(""));
        assertEquals("file", MfgAttachmentServices.cleanFileName(null));
        assertTrue(MfgAttachmentServices.cleanFileName("a".repeat(200) + ".png").length() <= 60);
    }

    @Test
    public void keepsTheAttachmentHeadersAscii() {
        // a part number may be Chinese: an HTTP header with it in it kills the whole response over
        // HTTP/2 (ERR_HTTP2_PROTOCOL_ERROR), so the cache tag is hashed
        String tag = MfgAttachmentServices.etagOf("MFG_ATT_PART", "蓝色WCH-LINKE下载器", 2, false);
        assertTrue("the tag must stay printable ASCII, got " + tag, tag.matches("\"[0-9a-f]{40}\""));
        assertEquals(tag, MfgAttachmentServices.etagOf("MFG_ATT_PART", "蓝色WCH-LINKE下载器", 2, false));
        assertTrue(!tag.equals(MfgAttachmentServices.etagOf("MFG_ATT_PART", "蓝色WCH-LINKE下载器", 1, false)));
        assertTrue(!tag.equals(MfgAttachmentServices.etagOf("MFG_ATT_PART", "蓝色WCH-LINKE下载器", 2, true)));
        // ASCII owner ids still get a tag, of course
        assertTrue(MfgAttachmentServices.etagOf("MFG_ATT_WORK_ORDER", "WO-261022-01", 1, false)
                .matches("\"[0-9a-f]{40}\""));

        // and the file name travels in the RFC 5987 parameter, with an ASCII name in the plain one
        String plain = MfgAttachmentServices.contentDispositionOf("inline", "WCH-LinkUserManual.PDF");
        assertTrue(plain.startsWith("inline; filename=\"WCH-LinkUserManual.PDF\""));
        assertTrue(plain.endsWith("filename*=UTF-8''WCH-LinkUserManual.PDF"));
        String chinese = MfgAttachmentServices.contentDispositionOf("attachment", "使用说明书.pdf");
        assertTrue("no raw Chinese may reach the header, got " + chinese,
                chinese.matches("[\\x20-\\x7E]*"));
        assertTrue(chinese.contains("%E4%BD%BF%E7%94%A8%E8%AF%B4%E6%98%8E%E4%B9%A6.pdf"));
        assertTrue(MfgAttachmentServices.contentDispositionOf("inline", null).startsWith("inline; filename=\"file\""));
    }

    @Test
    public void detectsTheBomFormat() {
        // Fritzing writes a header with a semicolon; everything else stays the component format
        assertTrue(MfgCsv.detectBomFormat("Label;Part Type;mn;mpn;part number;\nBT1;Supercapacitor;;;SE-5R5;\n")
                == MfgCsv.BomFormat.FRITZING);
        assertTrue(MfgCsv.detectBomFormat("\uFEFFAmount;Part Type;mn;mpn;part number;\n1;Resistor;;;;\n")
                == MfgCsv.BomFormat.FRITZING);
        assertTrue(MfgCsv.detectBomFormat("R0402-10K,10K 电阻,4,R1-R4,N,0.01\n")
                == MfgCsv.BomFormat.MFG);
        assertTrue(MfgCsv.detectBomFormat("") == MfgCsv.BomFormat.MFG);
        assertTrue(MfgCsv.detectBomFormat(null) == MfgCsv.BomFormat.MFG);
    }

    @Test
    public void parsesAFritzingBom() {
        String csv = "Label;Part Type;mn;mpn;part number;\n"
                + "BT1;Supercapacitor 1.5F 5.5V;;;SE-5R5-D155VYC3G;\n"
                + "J1;PIN HEADER 1x4;;;PinHeader-1x4;\n"
                + "J3;PIN HEADER 1x4;;;PinHeader-1x4;\n"
                + "R1;4.7kΩ Resistor;;;;\n"
                + "R2;4.7kΩ Resistor;;;;\n"
                + "\n"
                + "Amount;Part Type;mn;mpn;part number;\n"
                + "1;Supercapacitor 1.5F 5.5V;;;SE-5R5-D155VYC3G;\n"
                + "2;PIN HEADER 1x4;;;PinHeader-1x4;\n"
                + "2;4.7kΩ Resistor;;;;\n";
        List<String[]> rows = MfgCsv.parseFritzingBom(csv);

        assertEquals(3, rows.size());
        // the part number becomes the code, the amount block the quantity
        assertEquals("SE-5R5-D155VYC3G", rows.get(0)[0]);
        assertEquals("Supercapacitor 1.5F 5.5V", rows.get(0)[1]);
        assertEquals("1", rows.get(0)[2]);
        assertEquals("BT1", rows.get(0)[3]);
        assertEquals("N", rows.get(0)[4]);
        assertEquals("PINHEADER-1X4", rows.get(1)[0]);
        assertEquals("2", rows.get(1)[2]);
        assertEquals("J1,J3", rows.get(1)[3]);
        // no part number: the name is used as the code, upper cased
        assertEquals("4.7KΩ RESISTOR", rows.get(2)[0]);
        assertEquals("2", rows.get(2)[2]);
        assertEquals("R1,R2", rows.get(2)[3]);
    }

    @Test
    public void countsAFritzingBomWithoutTheAmountBlock() {
        // a schematic export without the amount block: one row per designator
        String csv = "Label;Part Type;mn;mpn;part number;\n"
                + "J1;PIN HEADER 1x4;;;PinHeader-1x4;\n"
                + "J3;PIN HEADER 1x4;;;PinHeader-1x4;\n";
        List<String[]> rows = MfgCsv.parseFritzingBom(csv);

        assertEquals(1, rows.size());
        assertEquals("2", rows.get(0)[2]);
        assertEquals("J1,J3", rows.get(0)[3]);
    }

    @Test
    public void parsesTheBomAfterDetectingTheFormat() {
        String fritzing = "Label;Part Type;mn;mpn;part number;\nU1;CH32V203F8U6;;;CH32V203F8U6;\n";
        List<String[]> detected = MfgCsv.parseBom(fritzing, null);
        assertEquals(1, detected.size());
        assertEquals("CH32V203F8U6", detected.get(0)[0]);

        // the format can be forced, which also works for text that looks like neither
        List<String[]> forced = MfgCsv.parseBom("U9;SoC;;;U9-CHIP;\n", MfgCsv.BomFormat.FRITZING);
        assertEquals(1, forced.size());
        assertEquals("U9-CHIP", forced.get(0)[0]);
    }
}
