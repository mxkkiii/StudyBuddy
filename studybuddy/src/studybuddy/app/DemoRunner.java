package studybuddy.app;

import studybuddy.exception.ReportException;
import studybuddy.exception.StudyBuddyException;
import studybuddy.model.*;
import studybuddy.report.CSVExporter;
import studybuddy.report.ProgressReport;
import studybuddy.report.PythonReportResult;
import studybuddy.report.PythonReportRunner;
import studybuddy.scheduling.PriorityScheduler;
import studybuddy.scheduling.StudySchedule;
import studybuddy.simulation.*;
import studybuddy.storage.FileManager;
import studybuddy.storage.LoadResult;

import java.io.PrintStream;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** A scripted walk through every major feature, using dates relative to today. */
public class DemoRunner {
    private final PrintStream out;
    private final Path workDirectory;
    private final Path pythonScript;

    public DemoRunner(PrintStream out, Path workDirectory, Path pythonScript) {
        this.out = out;
        this.workDirectory = workDirectory;
        this.pythonScript = pythonScript;
    }

    /** Runs the demo; returns true when the final save/load check succeeded. */
    public boolean run() {
        try {
            return demo();
        } catch (StudyBuddyException e) {
            out.println("\nDemo stopped because of an unexpected problem: " + e.getMessage());
            return false;
        }
    }

    private void section(String title) {
        out.println("\n" + "=".repeat(70));
        out.println(title);
        out.println("=".repeat(70));
    }

    private boolean demo() throws StudyBuddyException {
        LocalDate today = LocalDate.now();
        PriorityScheduler scheduler = new PriorityScheduler();
        out.println("StudyBuddy demo - today is " + today + ". Demo files: " + workDirectory);

        // ------------------------------------------------------------------
        section("1. SUBJECT MANAGEMENT");
        Student student = new Student("Maria Santos", "BS Computer Science");
        StudyPlanner planner = student.getPlanner();
        planner.setDailyAvailableHours(4);
        planner.addSubject("Mathematics", "Calculus I");
        planner.addSubject("Programming", "Java OOP");
        planner.addSubject("English");
        planner.editSubject(3, null, "Technical Writing");
        out.println("All subjects:");
        for (Subject s : planner.getAllSubjects()) out.println("  " + s);
        out.println("Search \"java\": " + planner.searchSubjects("java"));
        out.println("Daily study hours: " + planner.getDailyAvailableHours());

        // ------------------------------------------------------------------
        section("2. TASK MANAGEMENT (Exam, Quiz, Assignment, ReadingTask)");
        planner.addExam(1, "Calculus Midterm", today.plusDays(5), 8, 4, List.of("Limits", "Derivatives", "Integrals"));
        planner.addQuiz(2, "Java Quiz 2", today.plusDays(2), 2, 2, "Chapters 3-4");
        planner.addAssignment(2, "Java Lab 3", today.plusDays(7), 6, 3, "ZIP via portal");
        planner.addReadingTask(3, "Read Chapter 7", today.plusDays(10), 3, 1, 40);
        planner.addAssignment(3, "Essay Draft", today.plusDays(4), 4, 3, "PDF");
        out.println("Tasks by deadline:");
        for (StudyItem t : planner.getTasksByDeadline()) out.println("  " + t + "\n       " + t.getDetails());

        out.println("\nQuick add with the overloaded addTask():");
        StudyItem q1 = planner.addTask(3, TaskType.READING_TASK, "Skim Chapter 8", today.plusDays(12));        // title + deadline only
        StudyItem q2 = planner.addTask(2, TaskType.QUIZ, "Java Quiz 3", today.plusDays(9), 1.5);               // + estimated hours
        StudyItem q3 = planner.addTask(1, TaskType.ASSIGNMENT, "Problem Set 4", today.plusDays(8), 3, 4);      // + hours and difficulty
        for (StudyItem t : new StudyItem[]{q1, q2, q3}) out.println("  " + t);

        out.println("\nInput validation (these are rejected, the program keeps running):");
        tryIt("deadline in the past", () -> planner.addQuiz(1, "Old quiz", today.minusDays(1), 1, 1, null));
        tryIt("difficulty 9", () -> planner.addQuiz(1, "Hard quiz", today.plusDays(1), 1, 9, null));
        tryIt("negative hours", () -> planner.addQuiz(1, "Odd quiz", today.plusDays(1), -2, 3, null));
        tryIt("unknown subject", () -> planner.addQuiz(99, "Lost quiz", today.plusDays(1), 1, 3, null));
        tryIt("unknown task", () -> planner.findTask(99));

        // ------------------------------------------------------------------
        section("3. AUTOMATIC PRIORITIES (PriorityQueue)");
        out.println("Priority = (type weight x 2) + max(0, 10 - days left) + difficulty");
        for (StudyItem t : planner.getTasksByPriority())
            out.printf("  score %3.0f  %-8s %-18s due in %d days%n", t.getPriorityScore(), t.getPriorityLevel(), t.getTitle(), t.getDaysLeft(today));

        out.println("\nDEADLINE ALERT LIST (overdue, due today, or due within " + StudyPlanner.DEFAULT_ALERT_DAYS + " days):");
        for (StudyItem t : planner.getDeadlineAlerts(today))
            out.printf("  ! [#%d] %-18s %s%n", t.getId(), t.getTitle(), t.getDeadlineLabel(today));

        // ------------------------------------------------------------------
        section("4. STUDY SCHEDULE GENERATOR");
        out.println(scheduler.generate(planner).render());

        // ------------------------------------------------------------------
        section("5. STUDY SESSION TRACKING");
        planner.logSession(1, today.minusDays(3), 2, "Practice problems");
        planner.logSession(3, today.minusDays(2), 2, "Set up the project");
        planner.logSession(5, today.minusDays(1), 1, "Outline");
        planner.logSession(1, today, 1.5, "Past exam questions");
        StudySession last = planner.logSession(2, today, 1.5, "Reviewed chapters");
        out.println("Last session logged: " + last);
        out.printf("Remaining hours on \"%s\" went from 2.0 to %.1f%n", last.getTask().getTitle(), last.getTask().getRemainingHours());
        planner.markTaskCompleted(2);
        out.println("Marked \"Java Quiz 2\" as completed.");
        out.println("Session history:");
        for (StudySession s : planner.getSessions()) out.println("  " + s);
        out.printf("Total study hours: %.1f%n", planner.getTotalStudyHours());
        for (Map.Entry<String, Double> e : planner.getStudyHoursPerSubject().entrySet())
            out.printf("  %-14s %.1fh%n", e.getKey(), e.getValue());
        out.println("Sessions today: " + planner.getSessionsByDate(today).size());

        section("5b. SCHEDULE REGENERATED AFTER PROGRESS");
        StudySchedule current = scheduler.generate(planner);
        out.println(current.render());

        // ------------------------------------------------------------------
        section("6. WEEKLY PROGRESS REPORT (CSV export + Python script)");
        ProgressReport report = ProgressReport.lastSevenDays(student);
        Path exportDir = workDirectory.resolve("export");
        for (Path p : new CSVExporter(exportDir).exportWeeklyReport(report)) out.println("Exported: " + p.getFileName());
        try {
            PythonReportResult result = new PythonReportRunner(pythonScript).run(exportDir);
            if (result.isSuccess()) {
                out.println(result.getOutput());
                out.println(result.hasChart() ? "Bar chart saved: " + result.getChartPath() : "(no chart: matplotlib is not installed)");
            } else {
                out.println("Python report failed:\n" + result.getOutput() + "\nBuilt-in report:\n" + report.render());
            }
        } catch (ReportException e) {
            out.println("Python report unavailable (" + e.getMessage() + ").\nBuilt-in report instead:\n" + report.render());
        }

        // ------------------------------------------------------------------
        section("7. WHAT-IF SIMULATOR");
        WhatIfSimulator simulator = new WhatIfSimulator(planner);
        String realBefore = StudyPlannerSnapshot.of(planner);

        out.println(">>> Scenario A: \"I will be busy: 3 hours LESS per day for the next 3 days\"\n");
        SimulationResult a = simulator.simulate(SimulationScenario.reduceHours(today, today.plusDays(2), 3));
        out.println(a.render());
        out.println(a.renderScheduleComparison(OutcomeType.EXPECTED));

        out.println(">>> Scenario B: \"What if I focus 70% of my time on Programming?\" (summary only)");
        SimulationResult b = simulator.simulate(SimulationScenario.prioritizeSubject(2, 70));
        out.println("  " + b.getScenarioDescription());
        for (OutcomeType t : OutcomeType.values()) {
            ImpactAnalysis ia = b.getOutcome(t).getAnalysis();
            out.printf("  %-12s on track %d, delayed %d, impossible %d%n", t.getLabel(), ia.getOnTrack().size(), ia.getDelayed().size(), ia.getImpossible().size());
        }
        for (ImpactAnalysis.TaskImpact t : b.getExpected().getAnalysis().getAffectedTasks()) out.println("  affected: " + t);

        boolean untouched = StudyPlannerSnapshot.of(planner).equals(realBefore) && planner.getAdjustments().isEmpty();
        out.println("\nAfter two simulations, is the real plan unchanged? " + (untouched ? "YES" : "NO (problem!)"));

        out.println("\n>>> The student explicitly applies a 'skip tomorrow' scenario to the real plan:");
        SimulationResult skip = simulator.simulate(SimulationScenario.skipDay(today.plusDays(1)));
        out.println("  Before applying, study planned tomorrow: " + scheduler.generate(planner).getScheduledHours(today.plusDays(1)) + "h");
        simulator.applyToPlan(skip);
        out.println("  After applying,  study planned tomorrow: " + scheduler.generate(planner).getScheduledHours(today.plusDays(1)) + "h");
        out.println("  Plan adjustments now: " + planner.getAdjustments().describe());

        // ------------------------------------------------------------------
        section("8. SAVING AND LOADING");
        FileManager files = new FileManager(workDirectory.resolve("data"));
        files.save(student);
        out.println("Saved to " + files.getDirectory() + ":");
        out.println("  student.txt, subjects.csv, tasks.csv, sessions.csv, adjustments.csv");
        LoadResult loaded = new FileManager(workDirectory.resolve("data")).load();
        out.println(loaded);
        for (String w : loaded.getWarnings()) out.println("  ! " + w);
        StudyPlanner lp = loaded.getStudent().getPlanner();
        boolean same = loaded.getStudent().getName().equals(student.getName())
                && lp.getAllSubjects().size() == planner.getAllSubjects().size()
                && lp.getAllTasks().size() == planner.getAllTasks().size()
                && lp.getSessions().size() == planner.getSessions().size()
                && lp.getTotalStudyHours() == planner.getTotalStudyHours()
                && lp.getAdjustments().describe().equals(planner.getAdjustments().describe())
                && StudyPlannerSnapshot.of(lp).equals(StudyPlannerSnapshot.of(planner));
        out.println("Loaded data identical to what was saved? " + (same ? "YES" : "NO (problem!)"));
        out.println("Simulation objects were never written to disk (only the real plan is saved).");
        out.println("\nDemo finished.");
        return same && untouched;
    }

    private interface Risky { void run() throws StudyBuddyException; }

    private void tryIt(String what, Risky action) {
        try {
            action.run();
            out.println("  (unexpectedly accepted: " + what + ")");
        } catch (StudyBuddyException e) {
            out.println("  " + what + " -> " + e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    /** A text fingerprint of the real data, used to prove simulations change nothing. */
    private static final class StudyPlannerSnapshot {
        static String of(StudyPlanner p) {
            StringBuilder sb = new StringBuilder();
            for (StudyItem t : p.getTasksByDeadline()) sb.append(t.getId()).append(t.getRemainingHours()).append(t.getStatus()).append(t.getDeadline()).append('|');
            sb.append(p.getSessions().size()).append('|').append(p.getDailyAvailableHours());
            return sb.toString();
        }
    }
}
