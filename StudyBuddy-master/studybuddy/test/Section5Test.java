import studybuddy.exception.*;
import studybuddy.model.*;
import studybuddy.report.*;
import studybuddy.scheduling.*;
import studybuddy.storage.CsvUtil;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDate;
import java.util.List;

public class Section5Test {
    static int passed = 0, failed = 0;
    static final LocalDate TODAY = LocalDate.now();

    interface Action { void run() throws Exception; }

    static void check(String name, boolean ok) {
        if (ok) passed++; else failed++;
        System.out.println((ok ? "  PASS  " : "  FAIL  ") + name);
    }

    static void expectThrows(String name, Class<? extends Exception> type, Action a) {
        try { a.run(); check(name + " (no exception)", false); }
        catch (Exception e) { check(name, type.isInstance(e)); }
    }

    static List<String> lines(Path p) throws Exception { return Files.readAllLines(p, StandardCharsets.UTF_8); }

    static Student buildStudent() throws Exception {
        Student st = new Student("Maria Santos", "BSCS");
        StudyPlanner p = st.getPlanner();
        p.addSubject("Mathematics");
        p.addSubject("Programming");
        p.addSubject("English");
        p.addExam(1, "Calculus Midterm", TODAY.plusDays(3), 12, 4, List.of("Limits"));
        p.addQuiz(1, "Quiz 2", TODAY.plusDays(1), 4, 2, "Ch 3");
        p.addAssignment(2, "Java Lab", TODAY.plusDays(12), 6, 3, "ZIP");
        p.addReadingTask(3, "Read Ch. 7", TODAY.plusDays(20), 1.5, 1, 30);
        p.logSession(1, TODAY.minusDays(10), 2, "outside the week");
        p.logSession(1, TODAY.minusDays(3), 3, "Practice");
        p.logSession(3, TODAY.minusDays(1), 2, "set up, tests");
        p.logSession(2, TODAY, 1.5, null);
        p.markTaskCompleted(4);
        p.restoreTask(2, new Assignment(60, "Missed Report", TODAY.minusDays(3), 2, 3));
        return st;
    }

    public static void main(String[] args) throws Exception {
        Student student = buildStudent();
        StudyPlanner planner = student.getPlanner();

        System.out.println("== ProgressReport ==");
        ProgressReport r = ProgressReport.lastSevenDays(student);
        System.out.println(r.render());
        check("Period is the 7 days ending today", r.getStartDate().equals(TODAY.minusDays(6)) && r.getEndDate().equals(TODAY));
        check("Total hours in period = 6.5 (the 10-days-ago session is excluded)", r.getTotalHours() == 6.5);
        check("Math 4.5, Programming 2.0, English 0.0", r.getHoursPerSubject().get("Mathematics") == 4.5
                && r.getHoursPerSubject().get("Programming") == 2.0 && r.getHoursPerSubject().get("English") == 0.0);
        check("Completed 1, pending 3, overdue 1", r.getCompletedTasks() == 1 && r.getPendingTasks() == 3 && r.getOverdueTasks() == 1);
        check("3 sessions, oldest first", r.getSessions().size() == 3 && r.getSessions().get(0).getDate().equals(TODAY.minusDays(3)));
        check("render() lists sessions and subjects", r.render().contains("Practice") && r.render().contains("Programming"));
        ProgressReport past = ProgressReport.forWeekStarting(student, TODAY.minusDays(12));
        check("forWeekStarting: window -12..-6 holds only the old 2h session", past.getTotalHours() == 2.0 && past.getSessions().size() == 1);
        ProgressReport blank = new ProgressReport(new Student("New"), TODAY.minusDays(6), TODAY);
        check("Empty student: zeros and friendly message", blank.getTotalHours() == 0 && blank.render().contains("No study sessions"));
        expectThrows("End before start rejected", InvalidInputException.class, () -> new ProgressReport(student, TODAY, TODAY.minusDays(1)));
        expectThrows("Null student rejected", InvalidInputException.class, () -> new ProgressReport(null, TODAY, TODAY));
        check("Report CSV row", r.toCsvLine().equals("Maria Santos," + TODAY.minusDays(6) + "," + TODAY + ",6.5,1,3,1"));

        System.out.println("== CSVExporter ==");
        Path out = Files.createTempDirectory("studybuddy-export").resolve("report");
        CSVExporter exporter = new CSVExporter(out);
        List<Path> weekly = exporter.exportWeeklyReport(r);
        check("3 weekly files written", weekly.size() == 3 && weekly.stream().allMatch(Files::exists));
        List<String> summary = lines(out.resolve(CSVExporter.SUMMARY_FILE));
        check("Summary: header + 1 row", summary.size() == 2 && summary.get(0).equals(ProgressReport.CSV_HEADER));
        List<String> perSubject = lines(out.resolve(CSVExporter.SUBJECT_HOURS_FILE));
        check("Subject hours: header + 3 subjects (zero included)", perSubject.size() == 4 && perSubject.contains("English,0.0"));
        List<String> sess = lines(out.resolve(CSVExporter.SESSIONS_FILE));
        check("Sessions file: header + 3 rows", sess.size() == 4);
        check("Session notes with comma stay in one column", CsvUtil.parseLine(sess.get(2)).size() == 7
                && CsvUtil.parseLine(sess.get(2)).get(6).equals("set up, tests"));
        List<Path> all = exporter.exportAll(planner);
        check("exportAll: subjects/tasks/sessions", all.size() == 3 && lines(all.get(1)).size() == 1 + planner.getAllTasks().size()
                && lines(all.get(0)).size() == 4 && lines(all.get(2)).size() == 5);
        check("Tasks file sorted by deadline (first data row is the overdue task)", lines(all.get(1)).get(1).startsWith("60,"));
        StudySchedule sched = new PriorityScheduler().generate(planner);
        Path schedFile = exporter.exportSchedule(sched);
        check("Schedule export has one row per entry", lines(schedFile).size() == 1 + sched.getEntries().size()
                && lines(schedFile).get(0).startsWith("date,taskId"));
        Path emptyOut = new CSVExporter(out.resolve("empty")).writeCsv("none.csv", ScheduleEntry.CSV_HEADER, new java.util.ArrayList<ScheduleEntry>());
        check("Empty list still gets a header (generic writeCsv)", lines(emptyOut).equals(List.of(ScheduleEntry.CSV_HEADER)));
        Path blocker = Files.createTempFile("studybuddy-blocker", ".txt");
        expectThrows("Export folder is a file -> DataStorageException", DataStorageException.class,
                () -> new CSVExporter(blocker.resolve("x")).exportWeeklyReport(r));
        expectThrows("Null report rejected", InvalidInputException.class, () -> exporter.exportWeeklyReport(null));

        System.out.println("== Python report via ProcessBuilder ==");
        PythonReportRunner runner = new PythonReportRunner(Paths.get("python", "weekly_report.py"));
        PythonReportResult res = runner.run(out);
        System.out.println(res.getOutput().replaceAll("(?m)^", "     "));
        check("Exit code 0", res.isSuccess());
        check("Output has totals and subjects", res.getOutput().contains("Total study hours : 6.5")
                && res.getOutput().contains("Mathematics") && res.getOutput().contains("Overdue tasks     : 1"));
        check("ASCII bars drawn", res.getOutput().contains("######"));
        check("Python lists the 3 sessions", res.getOutput().contains("Study sessions (3)") && res.getOutput().contains("set up, tests"));
        check("weekly_report.txt saved by the script", Files.exists(out.resolve("weekly_report.txt")));
        if (res.hasChart()) {
            check("Bar chart PNG created", Files.size(res.getChartPath()) > 1000 && res.getChartPath().getFileName().toString().equals("weekly_hours.png"));
            byte[] head = Files.readAllBytes(res.getChartPath());
            check("File really is a PNG", head[1] == 'P' && head[2] == 'N' && head[3] == 'G');
        } else {
            System.out.println("  (matplotlib not installed here: chart checks skipped)");
            check("Fallback message explains why", res.getOutput().contains("CHART: not generated (matplotlib is not installed)"));
        }

        runner.setChartEnabled(false);
        PythonReportResult noChart = runner.run(out);
        check("chartEnabled=false -> no chart, text report still produced", !noChart.hasChart() && noChart.isSuccess()
                && noChart.getOutput().contains("CHART: not generated (disabled)"));

        System.out.println("== Python failures ==");
        Path emptyDir = Files.createTempDirectory("studybuddy-nodata");
        PythonReportResult bad = new PythonReportRunner("python/weekly_report.py").run(emptyDir);
        check("Missing CSV files -> exit code 2 with a clear message", bad.getExitCode() == 2 && bad.getOutput().contains("ERROR: missing file"));
        Files.write(emptyDir.resolve("weekly_summary.csv"), "wrong,header\n1,2\n".getBytes(StandardCharsets.UTF_8));
        Files.write(emptyDir.resolve("weekly_subject_hours.csv"), "subject,hours\n".getBytes(StandardCharsets.UTF_8));
        Files.write(emptyDir.resolve("weekly_sessions.csv"), "x\n".getBytes(StandardCharsets.UTF_8));
        PythonReportResult wrong = new PythonReportRunner("python/weekly_report.py").run(emptyDir);
        check("Wrong CSV format -> exit code 2, no crash", wrong.getExitCode() == 2 && wrong.getOutput().contains("not in the expected format"));
        expectThrows("Script not found -> ReportException", ReportException.class, () -> new PythonReportRunner("python/nope.py").run(out));
        expectThrows("CSV folder not found -> ReportException", ReportException.class, () -> runner.run(out.resolve("missing")));
        PythonReportRunner noPython = new PythonReportRunner("python/weekly_report.py");
        noPython.setPythonCommand("definitely-not-a-python-command");
        expectThrows("Python not installed -> ReportException", ReportException.class, () -> noPython.run(out));
        Path slow = Files.createTempFile("slow", ".py");
        Files.write(slow, "import time\ntime.sleep(10)\n".getBytes(StandardCharsets.UTF_8));
        long t0 = System.currentTimeMillis();
        expectThrows("Script exceeding the time limit is stopped -> ReportException", ReportException.class,
                () -> new PythonReportRunner(slow, 1).run(out));
        check("Timeout returned in about 1 second, not 10", System.currentTimeMillis() - t0 < 5000);

        System.out.println("\nRESULT: " + passed + " passed, " + failed + " failed");
        if (failed > 0) System.exit(1);
    }
}
