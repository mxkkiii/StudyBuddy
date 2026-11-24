import studybuddy.exception.*;
import studybuddy.model.*;
import studybuddy.scheduling.*;
import studybuddy.simulation.*;
import studybuddy.simulation.ImpactAnalysis.TaskImpact;
import studybuddy.storage.*;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class Section6Test {
    static int passed = 0, failed = 0;
    static final LocalDate TODAY = LocalDate.now();

    interface Action { void run() throws Exception; }

    static void check(String name, boolean ok) {
        if (ok) passed++; else failed++;
        System.out.println((ok ? "  PASS  " : "  FAIL  ") + name);
    }

    static boolean near(double a, double b) { return Math.abs(a - b) < 1e-6; }

    static void expectThrows(String name, Class<? extends Exception> type, Action a) {
        try { a.run(); check(name + " (no exception)", false); }
        catch (Exception e) { check(name, type.isInstance(e)); }
    }

    static LocalDate d(int offset) { return TODAY.plusDays(offset); }

    /** 4h/day. Exam (Math) due +4 8h; Assignment (Programming) due +6 6h; Reading (English) due +8 4h. All on track. */
    static Student buildStudent() throws Exception {
        Student st = new Student("Maria Santos");
        StudyPlanner p = st.getPlanner();
        p.addSubject("Mathematics");
        p.addSubject("Programming");
        p.addSubject("English");
        p.addExam(1, "Calculus Midterm", d(4), 8, 4, List.of("Limits"));
        p.addAssignment(2, "Java Lab", d(6), 6, 3, "ZIP");
        p.addReadingTask(3, "Read Ch. 7", d(8), 4, 2, 30);
        return st;
    }

    static String day(StudySchedule s, int offset) {
        StringBuilder sb = new StringBuilder();
        for (ScheduleEntry e : s.getEntriesForDate(d(offset))) sb.append('#').append(e.getTaskId()).append(':').append(e.getHours()).append(' ');
        return sb.toString().trim();
    }

    static String snapshot(StudyPlanner p) {
        StringBuilder sb = new StringBuilder();
        for (StudyItem t : p.getAllTasks()) sb.append(t.toCsvLine()).append('\n');
        sb.append(p.getSessions().size()).append(p.getAdjustments().describe()).append(p.getDailyAvailableHours());
        return sb.toString();
    }

    static List<String> rows(StudySchedule s) {
        List<String> list = new ArrayList<>();
        for (ScheduleEntry e : s.getEntries()) list.add(e.toCsvLine());
        return list;
    }

    public static void main(String[] args) throws Exception {
        PriorityScheduler scheduler = new PriorityScheduler();

        System.out.println("== PlanAdjustments ==");
        PlanAdjustments adj = new PlanAdjustments();
        check("starts empty", adj.isEmpty());
        adj.setHoursForDate(d(1), 0);
        adj.postponeTask(3, d(5));
        adj.setFocus(3, 50);
        check("holds overrides, postponement, focus", adj.getHours(d(1), 4) == 0 && adj.getHours(d(2), 4) == 4
                && adj.getEarliestStart(3).equals(d(5)) && adj.getFocusSubjectId() == 3 && adj.describe().size() == 3);
        PlanAdjustments copy = adj.copy();
        copy.clear();
        check("copy() is independent", !adj.isEmpty() && copy.isEmpty());
        expectThrows("26h rejected", InvalidStudyHoursException.class, () -> adj.setHoursForDate(d(1), 26));
        expectThrows("focus 0% rejected", InvalidInputException.class, () -> adj.setFocus(1, 0));
        expectThrows("focus 101% rejected", InvalidInputException.class, () -> adj.setFocus(1, 101));
        expectThrows("null date rejected", InvalidInputException.class, () -> adj.setHoursForDate(null, 1));

        System.out.println("== Scheduler honours adjustments ==");
        Student s0 = buildStudent();
        StudyPlanner p0 = s0.getPlanner();
        StudySchedule base = scheduler.generate(p0, TODAY);
        check("Baseline: d0 exam, d1 exam, d2 lab, d3 lab+reading, d4 reading", day(base, 0).equals("#1:4.0") && day(base, 1).equals("#1:4.0")
                && day(base, 2).equals("#2:4.0") && day(base, 3).equals("#2:2.0 #3:2.0") && day(base, 4).equals("#3:2.0"));
        check("Baseline is fully on track", base.isFeasible());
        p0.getAdjustments().setHoursForDate(d(1), 0);
        StudySchedule skipped = scheduler.generate(p0, TODAY);
        check("Skipped day has no entries, work moves later", skipped.getEntriesForDate(d(1)).isEmpty() && skipped.getTotalScheduledHours() == 18);
        p0.getAdjustments().clear();
        p0.getAdjustments().postponeTask(3, d(5));
        StudySchedule postponed = scheduler.generate(p0, TODAY);
        check("Postponed task starts on its date", postponed.getEntriesForTask(3).size() == 1 && postponed.getEntriesForTask(3).get(0).getDate().equals(d(5)));
        check("Postponed task still on track", postponed.getForecast(3).isOnTrack());
        p0.getAdjustments().clear();
        p0.getAdjustments().setFocus(3, 50);
        check("Focus 50% on English: day 0 = exam 2h + reading 2h", day(scheduler.generate(p0, TODAY), 0).equals("#1:2.0 #3:2.0"));
        p0.getAdjustments().clear();
        p0.deleteTask(3);
        check("Deleting a task clears its postponement", p0.getAdjustments().getPostponements().isEmpty());
        p0.getAdjustments().setFocus(2, 60);
        p0.deleteSubject(2);
        check("Deleting the focus subject clears the focus", p0.getAdjustments().getFocusSubjectId() == null);

        System.out.println("== SimulationScenario / Assumptions validation ==");
        expectThrows("skipDay(null)", InvalidInputException.class, () -> SimulationScenario.skipDay(null));
        expectThrows("reduceHours reversed range", InvalidInputException.class, () -> SimulationScenario.reduceHours(d(3), d(1), 1));
        expectThrows("reduceHours 0h", InvalidStudyHoursException.class, () -> SimulationScenario.reduceHours(d(1), 0));
        expectThrows("increaseHours 25h", InvalidStudyHoursException.class, () -> SimulationScenario.increaseHours(d(1), 25));
        expectThrows("postpone 0 days", InvalidInputException.class, () -> SimulationScenario.postponeTask(1, 0));
        expectThrows("prioritize 0%", InvalidInputException.class, () -> SimulationScenario.prioritizeSubject(1, 0));
        SimulationAssumptions sa = new SimulationAssumptions();
        check("Defaults: 0.8 / 1.3 / 3 samples", sa.getOptimisticMultiplier() == 0.8 && sa.getPessimisticMultiplier() == 1.3 && sa.getMinHistorySamples() == 3);
        expectThrows("optimistic 1.5 rejected", InvalidInputException.class, () -> sa.setOptimisticMultiplier(1.5));
        expectThrows("optimistic 0 rejected", InvalidInputException.class, () -> sa.setOptimisticMultiplier(0));
        expectThrows("pessimistic 0.9 rejected", InvalidInputException.class, () -> sa.setPessimisticMultiplier(0.9));
        expectThrows("0 samples rejected", InvalidInputException.class, () -> sa.setMinHistorySamples(0));

        System.out.println("== Scenario 2: reduce hours (4h -> 1h/day for 5 days) ==");
        Student st = buildStudent();
        StudyPlanner planner = st.getPlanner();
        WhatIfSimulator sim = new WhatIfSimulator(planner);
        String before = snapshot(planner);
        SimulationResult r = sim.simulate(SimulationScenario.reduceHours(d(0), d(4), 3), TODAY);
        System.out.println(r.render());
        System.out.println(r.renderScheduleComparison(OutcomeType.EXPECTED));
        ImpactAnalysis a = r.getExpected().getAnalysis();
        check("Original plan: all 3 tasks on track", a.getBaselineOnTrackCount() == 3 && a.getBaselineDelayedCount() == 0 && a.getBaselineImpossibleCount() == 0);
        check("Exam becomes IMPOSSIBLE", a.getImpossible().size() == 1 && a.getImpossible().get(0).getTaskId() == 1);
        check("Lab becomes DELAYED", a.getDelayed().size() == 1 && a.getDelayed().get(0).getTaskId() == 2);
        check("Reading stays on track", a.getOnTrack().size() == 1 && a.getOnTrack().get(0).getTaskId() == 3);
        check("Available hours 36 -> 21 (-15)", near(a.getOriginalAvailableHours(), 36) && near(a.getSimulatedAvailableHours(), 21) && near(a.getAvailableHoursChange(), -15));
        check("Total workload 18h", near(a.getSimulatedWorkload(), 18));
        check("2 deadline conflicts (exam short 3h, lab short 1h)", a.getDeadlineConflicts().size() == 2
                && near(a.getDeadlineConflicts().get(0).getShortfall(), 3) && near(a.getDeadlineConflicts().get(1).getShortfall(), 1) && a.getBaselineConflictCount() == 0);
        check("Fully booked days 4 -> 8", a.getBaselineOverloadedDayCount() == 4 && a.getOverloadedDays().size() == 8);
        check("All 3 tasks affected, worst first", a.getAffectedTasks().size() == 3 && a.getAffectedTasks().get(0).getTaskId() == 1
                && a.getAffectedTasks().get(1).getTaskId() == 2);
        TaskImpact readingImpact = a.getAffectedTasks().get(2);
        check("Reading finish moves d4 -> d8 (+4 days)", readingImpact.getShiftDays() == 4 && readingImpact.getFinishAfter().equals(d(8)));
        check("Distribution changes listed", !a.getDistributionChanges().isEmpty());
        String sug = String.join("\n", a.getSuggestions());
        check("Suggestions: free-up hours, impossible exam, delayed lab, recovery plan",
                sug.contains("Free up about 3.0h") && sug.contains("cannot be finished by") && sug.contains("could still make")
                        && sug.contains("removes 15.0h"));
        check("Schedule comparison marks changed days", r.renderScheduleComparison(OutcomeType.EXPECTED).contains("*"));
        check("render() states real plan is unchanged and results are estimates",
                r.render().contains("NOT changed") && r.render().contains("not guaranteed") && r.render().contains("Study 3.0h less per day"));

        System.out.println("== Three outcomes ==");
        ImpactAnalysis opt = r.getOutcome(OutcomeType.OPTIMISTIC).getAnalysis();
        ImpactAnalysis pes = r.getOutcome(OutcomeType.PESSIMISTIC).getAnalysis();
        check("Workloads 14.4 / 18 / 23.4", near(opt.getSimulatedWorkload(), 14.4) && near(a.getSimulatedWorkload(), 18) && near(pes.getSimulatedWorkload(), 23.4));
        int po = opt.getDelayed().size() + opt.getImpossible().size();
        int pe = a.getDelayed().size() + a.getImpossible().size();
        int pp = pes.getDelayed().size() + pes.getImpossible().size();
        check("At-risk tasks: optimistic 1 <= expected 2 <= pessimistic 3 (got " + po + "/" + pe + "/" + pp + ")", po == 1 && pe == 2 && pp == 3);
        check("Multipliers recorded", near(r.getOutcome(OutcomeType.OPTIMISTIC).getMultiplier(), 0.8) && near(r.getOutcome(OutcomeType.PESSIMISTIC).getMultiplier(), 1.3));
        check("History notes say 'not enough history'", r.getHistoryNotes().get(0).contains("not enough history"));

        System.out.println("== Real data is never modified ==");
        check("Tasks, sessions, adjustments, hours identical after simulation", snapshot(planner).equals(before));
        check("No adjustments leaked into the real plan", planner.getAdjustments().isEmpty());
        SimulationResult again = sim.simulate(SimulationScenario.reduceHours(d(0), d(4), 3), TODAY);
        check("Same simulation twice gives identical results", again.render().equals(r.render()));
        sim.simulate(SimulationScenario.skipDay(d(2)), TODAY);
        sim.simulate(SimulationScenario.postponeTask(1, 2), TODAY);
        check("Several different simulations later: still unchanged", snapshot(planner).equals(before));
        check("Original schedule in result equals the real current schedule", rows(r.getOriginalSchedule()).equals(rows(scheduler.generate(planner, TODAY))));
        check("Simulation copies are flagged and cannot join a subject", planner.getAllTasks().stream().noneMatch(StudyItem::isSimulationCopy));

        System.out.println("== Scenario 1: skip a day ==");
        SimulationResult skip = sim.simulate(SimulationScenario.skipDay(d(1)), TODAY);
        ImpactAnalysis sk = skip.getExpected().getAnalysis();
        check("Skipping day 1: nothing breaks", !sk.hasProblems() && sk.getOnTrack().size() == 3 && sk.getDeadlineConflicts().isEmpty());
        check("Available 36 -> 32", near(sk.getOriginalAvailableHours(), 36) && near(sk.getSimulatedAvailableHours(), 32));
        check("Every task shifts one day later", sk.getAffectedTasks().size() == 3 && sk.getAffectedTasks().stream().allMatch(t -> t.getShiftDays() == 1));
        check("Simulated schedule has no entries on the skipped day", skip.getSimulatedSchedule().getEntriesForDate(d(1)).isEmpty());
        check("Suggestion: no changes needed", sk.getSuggestions().size() == 1 && sk.getSuggestions().get(0).startsWith("No changes needed"));

        System.out.println("== Scenario 3: postpone a task ==");
        SimulationResult post = sim.simulate(SimulationScenario.postponeTask(3, 6), TODAY);
        ImpactAnalysis pa = post.getExpected().getAnalysis();
        check("Reading postponed 6 days: still on track, finishes d6 (+2)", !pa.hasProblems() && pa.getAffectedTasks().size() == 1
                && pa.getAffectedTasks().get(0).getTaskId() == 3 && pa.getAffectedTasks().get(0).getShiftDays() == 2);
        check("Distribution: 3 lines (d3, d4 removed, d6 added)", pa.getDistributionChanges().size() == 3);
        check("Description uses the task title", post.getScenarioDescription().contains("\"Read Ch. 7\" by 6 days"));
        SimulationResult tooLate = sim.simulate(SimulationScenario.postponeTask(3, d(9)), TODAY);
        TaskForecast tf = tooLate.getExpected().getSimulatedSchedule().getForecast(3);
        check("Postponed past its deadline: IMPOSSIBLE, message says postponed", tf.getFeasibility() == Feasibility.IMPOSSIBLE && tf.getMessage().contains("postponed until"));

        System.out.println("== Scenario 4: prioritize a subject ==");
        SimulationResult foc = sim.simulate(SimulationScenario.prioritizeSubject(3, 75), TODAY);
        ImpactAnalysis fa = foc.getExpected().getAnalysis();
        TaskImpact fr = fa.getTaskImpacts().stream().filter(t -> t.getTaskId() == 3).findFirst().get();
        TaskImpact fe = fa.getTaskImpacts().stream().filter(t -> t.getTaskId() == 1).findFirst().get();
        check("English reading finishes 3 days EARLIER", fr.getShiftDays() == -3);
        check("Exam finishes 1 day later but is still on track", fe.getShiftDays() == 1 && fe.getAfter() == Feasibility.ON_TRACK);
        check("Day 0 simulated: reading 3h + exam 1h", day(foc.getSimulatedSchedule(), 0).equals("#1:1.0 #3:3.0"));
        check("Description names the subject", foc.getScenarioDescription().contains("\"English\" 75%"));

        System.out.println("== Scenario 5: increase hours ==");
        SimulationResult inc = sim.simulate(SimulationScenario.increaseHours(d(0), d(4), 2), TODAY);
        ImpactAnalysis ia = inc.getExpected().getAnalysis();
        check("Available 36 -> 46 (+10)", near(ia.getAvailableHoursChange(), 10));
        check("Lab 1 day earlier, reading 2 days earlier, exam unchanged", ia.getAffectedTasks().size() == 2
                && ia.getTaskImpacts().stream().filter(t -> t.getTaskId() == 3).findFirst().get().getShiftDays() == -2
                && ia.getTaskImpacts().stream().filter(t -> t.getTaskId() == 2).findFirst().get().getShiftDays() == -1);

        System.out.println("== Custom assumptions and history ==");
        SimulationAssumptions custom = new SimulationAssumptions();
        custom.setOptimisticMultiplier(0.5);
        custom.setPessimisticMultiplier(2.0);
        sim.setAssumptions(custom);
        SimulationResult cr = sim.simulate(SimulationScenario.skipDay(d(1)), TODAY);
        check("Custom multipliers: workloads 9 / 18 / 36", near(cr.getOutcome(OutcomeType.OPTIMISTIC).getAnalysis().getSimulatedWorkload(), 9)
                && near(cr.getOutcome(OutcomeType.PESSIMISTIC).getAnalysis().getSimulatedWorkload(), 36));
        custom.setOptimisticMultiplier(0.9);
        check("Result keeps its own copy of the assumptions", near(cr.getAssumptions().getOptimisticMultiplier(), 0.5));
        sim.setAssumptions(new SimulationAssumptions());

        Student hist = new Student("History");
        StudyPlanner hp = hist.getPlanner();
        hp.addSubject("Programming");
        for (int i = 0; i < 3; i++) {
            StudyItem t = hp.addAssignment(1, "Done " + i, d(20), 4, 2);
            hp.logSession(t.getId(), TODAY.minusDays(1), 3, null);
            hp.logSession(t.getId(), TODAY, 3, null);
            hp.markTaskCompleted(t.getId());
        }
        StudyItem quizDone = hp.addQuiz(1, "Done quiz", d(20), 2, 1, null);
        hp.logSession(quizDone.getId(), TODAY, 3, null);
        hp.markTaskCompleted(quizDone.getId());
        hp.addAssignment(1, "Pending lab", d(10), 4, 3);
        DurationHistory dh = new DurationHistory(hp, new SimulationAssumptions());
        check("History: 3 assignments took 6h for a 4h estimate -> x1.5", dh.getSampleCount(TaskType.ASSIGNMENT) == 3 && near(dh.getFactor(TaskType.ASSIGNMENT), 1.5));
        check("History: 1 quiz sample is not enough -> x1.0", dh.getSampleCount(TaskType.QUIZ) == 1 && dh.getFactor(TaskType.QUIZ) == 1.0);
        SimulationAssumptions strict = new SimulationAssumptions();
        strict.setMinHistorySamples(4);
        check("Needing 4 samples -> falls back to the estimate", new DurationHistory(hp, strict).getFactor(TaskType.ASSIGNMENT) == 1.0);
        SimulationAssumptions off = new SimulationAssumptions();
        off.setUseHistory(false);
        check("History switched off -> x1.0", new DurationHistory(hp, off).getFactor(TaskType.ASSIGNMENT) == 1.0);
        WhatIfSimulator hsim = new WhatIfSimulator(hp);
        SimulationResult hr = hsim.simulate(SimulationScenario.skipDay(d(30)), TODAY);
        check("Expected workload uses history: 4h x 1.5 = 6h; optimistic 4.8; pessimistic 7.8",
                near(hr.getExpected().getAnalysis().getSimulatedWorkload(), 6.0)
                        && near(hr.getOutcome(OutcomeType.OPTIMISTIC).getAnalysis().getSimulatedWorkload(), 4.8)
                        && near(hr.getOutcome(OutcomeType.PESSIMISTIC).getAnalysis().getSimulatedWorkload(), 7.8));
        check("History note shows the learned factor", hr.getHistoryNotes().get(1).contains("x1.50"));
        check("Real remaining hours untouched by history scaling", hp.getUnfinishedTasks().get(0).getRemainingHours() == 4);

        System.out.println("== Simulator validation ==");
        expectThrows("Unknown task", TaskNotFoundException.class, () -> sim.simulate(SimulationScenario.postponeTask(99, 2), TODAY));
        expectThrows("Unknown subject", SubjectNotFoundException.class, () -> sim.simulate(SimulationScenario.prioritizeSubject(99, 50), TODAY));
        expectThrows("Date before start", InvalidInputException.class, () -> sim.simulate(SimulationScenario.skipDay(TODAY.minusDays(1)), TODAY));
        expectThrows("Postpone date not after start", InvalidInputException.class, () -> sim.simulate(SimulationScenario.postponeTask(3, TODAY), TODAY));
        expectThrows("Null scenario", InvalidInputException.class, () -> sim.simulate(null, TODAY));
        expectThrows("Null planner", InvalidInputException.class, () -> new WhatIfSimulator(null));
        Student done = buildStudent();
        done.getPlanner().markTaskCompleted(3);
        expectThrows("Postponing a completed task", InvalidInputException.class,
                () -> new WhatIfSimulator(done.getPlanner()).simulate(SimulationScenario.postponeTask(3, 2), TODAY));
        SimulationResult empty = new WhatIfSimulator(new Student("Nobody").getPlanner()).simulate(SimulationScenario.skipDay(d(1)), TODAY);
        check("Empty planner: no crash, 0 workload, 'no changes needed'", near(empty.getExpected().getAnalysis().getSimulatedWorkload(), 0)
                && empty.getExpected().getAnalysis().getSuggestions().get(0).startsWith("No changes needed"));

        System.out.println("== Applying a scenario (only when explicitly chosen) ==");
        SimulationResult toApply = sim.simulate(SimulationScenario.skipDay(d(1)), TODAY);
        check("Before applying: real plan still has study on day 1", !scheduler.generate(planner, TODAY).getEntriesForDate(d(1)).isEmpty());
        sim.applyToPlan(toApply);
        check("After applyToPlan: adjustment stored in the real plan", planner.getAdjustments().getHours(d(1), 4) == 0);
        check("Real schedule now skips day 1", scheduler.generate(planner, TODAY).getEntriesForDate(d(1)).isEmpty());
        SimulationResult second = sim.simulate(SimulationScenario.skipDay(d(2)), TODAY);
        check("Next simulation's 'original' includes the applied change", near(second.getExpected().getAnalysis().getOriginalAvailableHours(), 32)
                && near(second.getExpected().getAnalysis().getSimulatedAvailableHours(), 28));
        expectThrows("applyToPlan(null)", InvalidInputException.class, () -> sim.applyToPlan(null));
        planner.getAdjustments().clear();

        System.out.println("== Saving / loading adjustments ==");
        Path dir = Files.createTempDirectory("studybuddy-sim").resolve("data");
        FileManager fm = new FileManager(dir);
        planner.getAdjustments().setHoursForDate(d(2), 1.5);
        planner.getAdjustments().postponeTask(3, d(5));
        planner.getAdjustments().setFocus(2, 60);
        fm.save(st);
        String tasksFile = new String(Files.readAllBytes(dir.resolve("tasks.csv")), StandardCharsets.UTF_8);
        sim.simulate(SimulationScenario.reduceHours(d(0), d(4), 3), TODAY);
        sim.simulate(SimulationScenario.prioritizeSubject(1, 90), TODAY);
        fm.save(st);
        check("Running simulations does not change saved data", tasksFile.equals(new String(Files.readAllBytes(dir.resolve("tasks.csv")), StandardCharsets.UTF_8))
                && !new String(Files.readAllBytes(dir.resolve("adjustments.csv")), StandardCharsets.UTF_8).contains("90"));
        LoadResult lr = fm.load();
        PlanAdjustments loaded = lr.getStudent().getPlanner().getAdjustments();
        check("Adjustments round trip (hours, postponement, focus)", !lr.hasWarnings() && loaded.getHours(d(2), 4) == 1.5
                && loaded.getEarliestStart(3).equals(d(5)) && loaded.getFocusSubjectId() == 2 && loaded.getFocusSharePercent() == 60);
        check("Loaded plan schedules the same as the saved one", rows(scheduler.generate(lr.getStudent().getPlanner(), TODAY)).equals(rows(scheduler.generate(planner, TODAY))));
        Files.write(dir.resolve("adjustments.csv"), ("type,key,value\nHOURS," + d(3) + ",2\nHOURS,not-a-date,2\nPOSTPONE,99," + d(5) + "\n"
                + "FOCUS,77,50\nFOCUS,1,500\nBOGUS,1,2\nHOURS," + d(4) + ",abc\n").getBytes(StandardCharsets.UTF_8));
        LoadResult bad = fm.load();
        bad.getWarnings().forEach(w -> System.out.println("     warning: " + w));
        check("6 bad adjustment lines skipped, the valid one kept", bad.getWarnings().size() == 6
                && bad.getStudent().getPlanner().getAdjustments().getHours(d(3), 4) == 2);
        check("Bad adjustments file backed up as .corrupt", Files.exists(dir.resolve("adjustments.csv.corrupt")));
        Files.delete(dir.resolve("adjustments.csv"));
        check("Older save folder without adjustments.csv still loads cleanly", !fm.load().hasWarnings());

        System.out.println("\nRESULT: " + passed + " passed, " + failed + " failed");
        if (failed > 0) System.exit(1);
    }
}
