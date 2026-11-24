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
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

/**
 * A text-menu front end (main menu follows the project proposal: options 1-7, plus 8 for extra features). It contains NO study logic: every action calls the backend classes
 * (StudyPlanner, PriorityScheduler, WhatIfSimulator, ...). A Swing/JavaFX GUI would call the same methods.
 * Input and output are injected so the menu can be tested with scripted input.
 */
public class ConsoleMenu {

    /** Thrown when the input stream ends (e.g. Ctrl+D) so the program can save and stop cleanly. */
    private static class InputEnded extends RuntimeException { }

    private final Scanner in;
    private final PrintStream out;
    private final FileManager files;
    private final Path pythonScript;
    private final Path exportDirectory;
    private final PriorityScheduler scheduler = new PriorityScheduler();

    private Student student;
    private StudyPlanner planner;
    private WhatIfSimulator simulator;
    private SimulationResult lastSimulation;

    public ConsoleMenu(Scanner in, PrintStream out, FileManager files, Path pythonScript, Path exportDirectory) {
        this.in = in;
        this.out = out;
        this.files = files;
        this.pythonScript = pythonScript;
        this.exportDirectory = exportDirectory;
    }

    // =====================================================================
    // Start / main loop
    // =====================================================================
    public void run() {
        try {
            startUp();
            mainMenu();
        } catch (InputEnded e) {
            out.println("\n(input ended)");
        } catch (StudyBuddyException e) {
            out.println("Fatal problem: " + e.getMessage());
        } finally {
            saveQuietly();
            out.println("Goodbye!");
        }
    }

    private void startUp() throws StudyBuddyException {
        out.println("=== StudyBuddy: Personal Study Planner ===");
        LoadResult loaded = files.load();
        for (String w : loaded.getWarnings()) out.println("  ! " + w);
        if (loaded.isEmpty()) {
            out.println("No saved data found in \"" + files.getDirectory() + "\". Let's set you up.");
            String name = ask("Your name");
            String course = askOptional("Your course (optional)");
            student = new Student(name.isEmpty() ? "Student" : name, course);
        } else {
            student = loaded.getStudent();
            out.println("Welcome back, " + student.getName() + ". " + loaded);
        }
        planner = student.getPlanner();
        simulator = new WhatIfSimulator(planner);
        if (!planner.getDeadlineAlerts().isEmpty()) printAlerts();
    }

    private void mainMenu() {
        while (true) {
            planner.refreshAll();
            int alerts = planner.getDeadlineAlerts().size();
            out.println("\n--- MAIN MENU (" + student.getName() + ")" + (alerts > 0 ? "  [" + alerts + " deadline alert" + (alerts == 1 ? "" : "s") + "]" : "") + " ---");
            out.println(" 1. Add Subject");
            out.println(" 2. Add Task");
            out.println(" 3. View Tasks");
            out.println(" 4. Generate Study Schedule");
            out.println(" 5. Mark Task as Done / Log Session");
            out.println(" 6. View Progress Report");
            out.println(" 7. Save and Exit");
            out.println(" 8. More options (manage subjects/tasks, session history, What-If simulator, plan settings)");
            int choice = readChoice(1, 8);
            try {
                switch (choice) {
                    case 1: addSubject(); break;
                    case 2: addTask(); break;
                    case 3: viewTasks(); break;
                    case 4: generateSchedule(); break;
                    case 5: doneOrLogMenu(); break;
                    case 6: reportMenu(); break;
                    case 7: return;                                   // run() saves before the program ends
                    default: moreMenu();
                }
            } catch (StudyBuddyException e) {
                out.println("  ! " + e.getMessage());
            }
        }
    }

    private void addSubject() throws StudyBuddyException {
        Subject s = planner.addSubject(ask("Subject name"), askOptional("Description (optional)"));
        out.println("Added: " + s);
    }

    private void viewTasks() throws StudyBuddyException {
        printAlerts();
        out.println("\nAll tasks, ranked from highest to lowest priority:");
        printTasks(planner.getTasksByPriority());
        while (true) {
            out.println("\nOther views: 1. By deadline   2. By subject   3. Search   4. Deadline alerts   0. Back");
            int c = readChoice(0, 4);
            switch (c) {
                case 1: printTasks(planner.getTasksByDeadline()); break;
                case 2: printSubjects(); printTasks(planner.getTasksBySubject(readInt("Subject ID"))); break;
                case 3: printTasks(planner.searchTasks(ask("Search for"))); break;
                case 4: printAlerts(); break;
                default: return;
            }
        }
    }

    /** The deadline alert list: unfinished tasks that are overdue, due today, or due within 3 days. */
    private void printAlerts() {
        LocalDate today = LocalDate.now();
        List<StudyItem> alerts = planner.getDeadlineAlerts(today);
        if (alerts.isEmpty()) {
            out.println("No tasks are overdue or due within " + StudyPlanner.DEFAULT_ALERT_DAYS + " days.");
            return;
        }
        out.println("!! DEADLINE ALERTS (" + alerts.size() + ")");
        for (StudyItem t : alerts)
            out.printf("   ! [#%d] %-22s %-14s %-18s %.1fh left%n", t.getId(), t.getTitle(), t.getSubjectName(), t.getDeadlineLabel(today), t.getRemainingHours());
    }

    private void generateSchedule() throws StudyBuddyException {
        while (true) {
            String answer = askOptional("Study hours available per day (Enter to keep " + planner.getDailyAvailableHours() + ")");
            if (answer.isEmpty()) break;
            try {
                planner.setDailyAvailableHours(parseDouble(answer));
                break;
            } catch (StudyBuddyException e) {
                out.println("  " + e.getMessage());
            }
        }
        out.println(scheduler.generate(planner).render());
    }

    private void doneOrLogMenu() {
        out.println("\n--- MARK TASK AS DONE / LOG SESSION ---");
        out.println(" 1. Mark a task as done   2. Log a study session   0. Back");
        int c = readChoice(0, 2);
        if (c == 0) return;
        try {
            if (c == 1) markDone(); else logSession();
        } catch (StudyBuddyException e) {
            out.println("  ! " + e.getMessage());
        }
    }

    private void markDone() throws StudyBuddyException {
        printTasks(planner.getUnfinishedTasks());
        int id = readInt("Task ID to mark as done");
        planner.markTaskCompleted(id);
        out.println("Marked as done: " + planner.findTask(id).getTitle());
    }

    private void logSession() throws StudyBuddyException {
        printTasks(planner.getUnfinishedTasks());
        int id = readInt("Task ID studied");
        LocalDate date = readDate("Date (blank = today)", LocalDate.now());
        double hours = readDouble("Hours studied");
        StudySession s = planner.logSession(id, date, hours, askOptional("Notes (optional)"));
        out.println("Logged: " + s);
        out.printf("Remaining on \"%s\": %.1fh%n", s.getTask().getTitle(), s.getTask().getRemainingHours());
    }

    private void moreMenu() {
        while (true) {
            out.println("\n--- MORE OPTIONS ---");
            out.println(" 1. Manage subjects (view, search, edit, delete)");
            out.println(" 2. Manage tasks (edit, delete, search, update status)");
            out.println(" 3. Study session history and totals");
            out.println(" 4. What-If simulator");
            out.println(" 5. Plan settings (daily hours, plan adjustments)");
            out.println(" 6. Save now");
            out.println(" 0. Back");
            int c = readChoice(0, 6);
            try {
                switch (c) {
                    case 1: subjectsMenu(); break;
                    case 2: tasksMenu(); break;
                    case 3: sessionsMenu(); break;
                    case 4: whatIfMenu(); break;
                    case 5: scheduleMenu(); break;
                    case 6: files.save(student); out.println("Saved to \"" + files.getDirectory() + "\"."); break;
                    default: return;
                }
            } catch (StudyBuddyException e) {
                out.println("  ! " + e.getMessage());
            }
        }
    }

    // =====================================================================
    // 1. Subjects
    // =====================================================================
    private void subjectsMenu() {
        while (true) {
            out.println("\n--- SUBJECTS ---");
            out.println(" 1. Add   2. View all   3. Search   4. Edit   5. Delete   6. Tasks of a subject   0. Back");
            int c = readChoice(0, 6);
            if (c == 0) return;
            try {
                switch (c) {
                    case 1: {
                        Subject s = planner.addSubject(ask("Subject name"), askOptional("Description (optional)"));
                        out.println("Added: " + s);
                        break;
                    }
                    case 2: printSubjects(); break;
                    case 3: {
                        List<Subject> found = planner.searchSubjects(ask("Search for"));
                        if (found.isEmpty()) out.println("No subjects match.");
                        for (Subject s : found) out.println("  " + s);
                        break;
                    }
                    case 4: {
                        printSubjects();
                        int id = readInt("Subject ID to edit");
                        String name = askOptional("New name (blank = keep)");
                        String desc = askOptional("New description (blank = keep)");
                        planner.editSubject(id, name.isEmpty() ? null : name, desc.isEmpty() ? null : desc);
                        out.println("Updated: " + planner.findSubject(id));
                        break;
                    }
                    case 5: {
                        printSubjects();
                        int id = readInt("Subject ID to delete");
                        Subject s = planner.findSubject(id);
                        if (confirm("Delete \"" + s.getName() + "\" and its " + s.getTasks().size() + " tasks and their study sessions?")) {
                            out.println("Deleted the subject and " + planner.deleteSubject(id) + " tasks.");
                        }
                        break;
                    }
                    default: {
                        printSubjects();
                        printTasks(planner.getTasksBySubject(readInt("Subject ID")));
                    }
                }
            } catch (StudyBuddyException e) {
                out.println("  ! " + e.getMessage());
            }
        }
    }

    private void printSubjects() {
        if (planner.getAllSubjects().isEmpty()) out.println("No subjects yet.");
        for (Subject s : planner.getAllSubjects()) out.println("  " + s);
    }

    // =====================================================================
    // 2. Tasks
    // =====================================================================
    private void tasksMenu() {
        while (true) {
            out.println("\n--- TASKS ---");
            out.println(" 1. Add   2. View all (by deadline)   3. View by priority   4. View by subject   5. Search");
            out.println(" 6. Edit  7. Delete   8. Mark completed   9. Update status   0. Back");
            int c = readChoice(0, 9);
            if (c == 0) return;
            try {
                switch (c) {
                    case 1: addTask(); break;
                    case 2: printTasks(planner.getTasksByDeadline()); break;
                    case 3: printTasks(planner.getTasksByPriority()); break;
                    case 4: printSubjects(); printTasks(planner.getTasksBySubject(readInt("Subject ID"))); break;
                    case 5: printTasks(planner.searchTasks(ask("Search for"))); break;
                    case 6: editTask(); break;
                    case 7: {
                        printTasks(planner.getTasksByDeadline());
                        int id = readInt("Task ID to delete");
                        StudyItem t = planner.findTask(id);
                        if (confirm("Delete \"" + t.getTitle() + "\" and its study sessions?")) {
                            planner.deleteTask(id);
                            out.println("Deleted.");
                        }
                        break;
                    }
                    case 8: {
                        printTasks(planner.getUnfinishedTasks());
                        int id = readInt("Task ID to mark completed");
                        planner.markTaskCompleted(id);
                        out.println("Marked completed: " + planner.findTask(id).getTitle());
                        break;
                    }
                    default: {
                        printTasks(planner.getTasksByDeadline());
                        int id = readInt("Task ID");
                        out.println("Statuses: Pending, In Progress, Completed, Overdue");
                        planner.updateTaskStatus(id, TaskStatus.fromString(ask("New status")));
                        out.println("Updated: " + planner.findTask(id));
                    }
                }
            } catch (StudyBuddyException e) {
                out.println("  ! " + e.getMessage());
            }
        }
    }

    private void addTask() throws StudyBuddyException {
        printSubjects();
        if (planner.getAllSubjects().isEmpty()) { out.println("Add a subject first."); return; }
        int subjectId = readInt("Subject ID");
        planner.findSubject(subjectId);                               // fail early if it does not exist
        out.println("Type: 1 = Exam, 2 = Assignment, 3 = Quiz, 4 = Reading task");
        int type = readInt("Type", 1, 4);
        String title = ask("Title");
        LocalDate deadline = readFutureDate("Deadline (yyyy-MM-dd, 'today', 'tomorrow' or +days)");
        double hours = readHours("Estimated study hours");
        int difficulty = readInt("Difficulty (1-5)", StudyItem.MIN_DIFFICULTY, StudyItem.MAX_DIFFICULTY);
        StudyItem task;
        switch (type) {
            case 1: {
                String topics = askOptional("Coverage topics, separated by commas (optional)");
                List<String> list = new ArrayList<>(Arrays.asList(topics.split(",")));
                task = planner.addExam(subjectId, title, deadline, hours, difficulty, list);
                break;
            }
            case 2: task = planner.addAssignment(subjectId, title, deadline, hours, difficulty, askOptional("Submission format (optional)")); break;
            case 3: task = planner.addQuiz(subjectId, title, deadline, hours, difficulty, askOptional("Coverage (optional)")); break;
            default: {
                String pages = askOptional("Pages to read (optional)");
                task = planner.addReadingTask(subjectId, title, deadline, hours, difficulty, pages.isEmpty() ? 0 : parseInt(pages));
            }
        }
        out.println("Added: " + task);
    }

    private void editTask() throws StudyBuddyException {
        printTasks(planner.getTasksByDeadline());
        int id = readInt("Task ID to edit");
        StudyItem current = planner.findTask(id);
        String title = askOptional("New title (blank = keep)");
        LocalDate deadline = readOptionalDeadline("New deadline (blank = keep)", current.getDeadline());
        String hours = askOptional("New estimated hours (blank = keep)");
        String difficulty = askOptional("New difficulty 1-5 (blank = keep)");
        StudyItem t = planner.editTask(id, title.isEmpty() ? null : title, deadline,
                hours.isEmpty() ? null : Double.valueOf(parseDouble(hours)),
                difficulty.isEmpty() ? null : Integer.valueOf(parseInt(difficulty)));
        out.println("Updated: " + t);
    }

    private void printTasks(List<StudyItem> tasks) {
        if (tasks.isEmpty()) out.println("No tasks to show.");
        for (StudyItem t : tasks) out.println("  " + t);
    }

    // =====================================================================
    // 3. Schedule
    // =====================================================================
    private void scheduleMenu() {
        while (true) {
            out.println("\n--- STUDY SCHEDULE --- (daily study hours: " + planner.getDailyAvailableHours() + ")");
            out.println(" 1. Generate / view schedule   2. Set daily study hours   3. View plan adjustments   4. Clear plan adjustments   0. Back");
            int c = readChoice(0, 4);
            if (c == 0) return;
            try {
                switch (c) {
                    case 1: out.println(scheduler.generate(planner).render()); break;
                    case 2: planner.setDailyAvailableHours(readDouble("Study hours available per day")); out.println("Saved setting."); break;
                    case 3: {
                        List<String> lines = planner.getAdjustments().describe();
                        if (lines.isEmpty()) out.println("No adjustments: your plan uses the normal daily hours.");
                        for (String l : lines) out.println("  " + l);
                        break;
                    }
                    default:
                        planner.getAdjustments().clear();
                        out.println("Adjustments cleared.");
                }
            } catch (StudyBuddyException e) {
                out.println("  ! " + e.getMessage());
            }
        }
    }

    // =====================================================================
    // 4. Study sessions
    // =====================================================================
    private void sessionsMenu() {
        while (true) {
            out.println("\n--- STUDY SESSIONS ---");
            out.println(" 1. Log a session   2. History   3. History by date   4. History by subject   5. Total hours   0. Back");
            int c = readChoice(0, 5);
            if (c == 0) return;
            try {
                switch (c) {
                    case 1: {
                        printTasks(planner.getUnfinishedTasks());
                        int id = readInt("Task ID studied");
                        LocalDate date = readDate("Date (blank = today)", LocalDate.now());
                        double hours = readDouble("Hours studied");
                        StudySession s = planner.logSession(id, date, hours, askOptional("Notes (optional)"));
                        out.println("Logged: " + s);
                        out.printf("Remaining on \"%s\": %.1fh%n", s.getTask().getTitle(), s.getTask().getRemainingHours());
                        break;
                    }
                    case 2: printSessions(planner.getSessions()); break;
                    case 3: printSessions(planner.getSessionsByDate(readDate("Date"))); break;
                    case 4: printSubjects(); printSessions(planner.getSessionsBySubject(readInt("Subject ID"))); break;
                    default: {
                        out.printf("Total study hours: %.1f%n", planner.getTotalStudyHours());
                        for (Map.Entry<String, Double> e : planner.getStudyHoursPerSubject().entrySet())
                            out.printf("  %-20s %.1fh%n", e.getKey(), e.getValue());
                    }
                }
            } catch (StudyBuddyException e) {
                out.println("  ! " + e.getMessage());
            }
        }
    }

    private void printSessions(List<StudySession> list) {
        if (list.isEmpty()) out.println("No sessions to show.");
        for (StudySession s : list) out.println("  " + s);
    }

    // =====================================================================
    // 5. Weekly report
    // =====================================================================
    private void reportMenu() {
        out.println("\n--- WEEKLY PROGRESS REPORT (last 7 days) ---");
        out.println(" 1. Full report: export CSV + Python report with bar chart   2. Quick text report   0. Back");
        int c = readChoice(0, 2);
        if (c == 0) return;
        try {
            ProgressReport report = ProgressReport.lastSevenDays(student);
            if (c == 2) { out.println(report.render()); return; }
            new CSVExporter(exportDirectory).exportWeeklyReport(report);
            out.println("CSV files written to \"" + exportDirectory + "\".");
            try {
                PythonReportResult result = new PythonReportRunner(pythonScript).run(exportDirectory);
                if (result.isSuccess()) {
                    out.println(result.getOutput());
                    if (result.hasChart()) out.println("Bar chart saved: " + result.getChartPath());
                } else {
                    out.println("The Python report failed:\n" + result.getOutput());
                    out.println(report.render());
                }
            } catch (ReportException e) {
                out.println("  ! " + e.getMessage() + "\nShowing the built-in text report instead:");
                out.println(report.render());
            }
        } catch (StudyBuddyException e) {
            out.println("  ! " + e.getMessage());
        }
    }

    // =====================================================================
    // 6. What-If simulator
    // =====================================================================
    private void whatIfMenu() {
        while (true) {
            out.println("\n--- WHAT-IF SIMULATOR --- (your real plan is never changed unless you choose option 7)");
            out.println(" 1. Skip a study day          5. Increase study hours");
            out.println(" 2. Reduce study hours        6. Show day-by-day comparison of the last simulation");
            out.println(" 3. Postpone a task           7. Apply the last simulated scenario to my real plan");
            out.println(" 4. Prioritize a subject      0. Back");
            int c = readChoice(0, 7);
            if (c == 0) return;
            try {
                switch (c) {
                    case 1: runSimulation(SimulationScenario.skipDay(readDate("Day to skip"))); break;
                    case 2: {
                        LocalDate from = readDate("From date");
                        LocalDate to = readDate("To date");
                        runSimulation(SimulationScenario.reduceHours(from, to, readDouble("Hours LESS per day")));
                        break;
                    }
                    case 3: {
                        printTasks(planner.getUnfinishedTasks());
                        int id = readInt("Task ID to postpone");
                        runSimulation(SimulationScenario.postponeTask(id, readDate("Do not start before")));
                        break;
                    }
                    case 4: {
                        printSubjects();
                        int id = readInt("Subject ID to prioritize");
                        runSimulation(SimulationScenario.prioritizeSubject(id, readDouble("Share of each day for it (1-100 %)")));
                        break;
                    }
                    case 5: {
                        LocalDate from = readDate("From date");
                        LocalDate to = readDate("To date");
                        runSimulation(SimulationScenario.increaseHours(from, to, readDouble("Extra hours per day")));
                        break;
                    }
                    case 6:
                        if (lastSimulation == null) out.println("Run a simulation first.");
                        else for (OutcomeType t : OutcomeType.values()) out.println(lastSimulation.renderScheduleComparison(t));
                        break;
                    default:
                        if (lastSimulation == null) { out.println("Run a simulation first."); break; }
                        if (confirm("Apply \"" + lastSimulation.getScenarioDescription() + "\" to your REAL plan?")) {
                            simulator.applyToPlan(lastSimulation);
                            out.println("Applied. Your schedule now includes this change (see Study schedule > plan adjustments).");
                        } else {
                            out.println("Not applied. Your plan is unchanged.");
                        }
                }
            } catch (StudyBuddyException e) {
                out.println("  ! " + e.getMessage());
            }
        }
    }

    private void runSimulation(SimulationScenario scenario) throws StudyBuddyException {
        lastSimulation = simulator.simulate(scenario);
        out.println(lastSimulation.render());
        out.println("Your real schedule is unchanged. Use option 6 for the day-by-day comparison or option 7 to apply it.");
    }

    // =====================================================================
    // Input helpers (re-ask until valid; stop cleanly when input ends)
    // =====================================================================
    private String line(String prompt) {
        out.print(prompt + ": ");
        out.flush();
        if (!in.hasNextLine()) throw new InputEnded();
        return in.nextLine().trim();
    }

    private String ask(String prompt) {
        while (true) {
            String s = line(prompt);
            if (!s.isEmpty()) return s;
            out.println("  This cannot be empty.");
        }
    }

    private String askOptional(String prompt) { return line(prompt); }

    private boolean confirm(String question) {
        String s = line(question + " (y/n)").toLowerCase();
        return s.equals("y") || s.equals("yes");
    }

    private int readChoice(int min, int max) {
        while (true) {
            String s = line("Choose");
            try {
                int v = Integer.parseInt(s);
                if (v >= min && v <= max) return v;
            } catch (NumberFormatException ignored) { }
            out.println("  Please enter a number from " + min + " to " + max + ".");
        }
    }

    private int readInt(String prompt) { return readInt(prompt, Integer.MIN_VALUE, Integer.MAX_VALUE); }

    private int readInt(String prompt, int min, int max) {
        while (true) {
            try {
                int v = Integer.parseInt(line(prompt));
                if (v >= min && v <= max) return v;
                out.println("  Please enter a number from " + min + " to " + max + ".");
            } catch (NumberFormatException e) {
                out.println("  Please enter a whole number.");
            }
        }
    }

    private double readDouble(String prompt) {
        while (true) {
            try { return Double.parseDouble(line(prompt)); }
            catch (NumberFormatException e) { out.println("  Please enter a number (for example 1.5)."); }
        }
    }

    /** A deadline for a NEW task: asks again until the date is valid and not in the past. */
    private LocalDate readFutureDate(String prompt) {
        while (true) {
            LocalDate d = readDate(prompt);
            try {
                StudyItem.checkDeadline(d, false);
                return d;
            } catch (StudyBuddyException e) {
                out.println("  " + e.getMessage() + " Please enter today's date or a later one.");
            }
        }
    }

    /** Blank = null (keep). Otherwise asks again until the date is valid and not in the past. */
    private LocalDate readOptionalDeadline(String prompt, LocalDate currentDeadline) {
        while (true) {
            String s = line(prompt);
            if (s.isEmpty()) return null;
            try {
                LocalDate d = parseDate(s);
                if (!d.equals(currentDeadline)) StudyItem.checkDeadline(d, false);
                return d;
            } catch (StudyBuddyException e) {
                out.println("  " + e.getMessage());
            }
        }
    }

    /** Estimated hours: asks again until the value is above 0 (and not absurdly large). */
    private double readHours(String prompt) {
        while (true) {
            double h = readDouble(prompt);
            try {
                StudyItem.checkHours(h);
                return h;
            } catch (StudyBuddyException e) {
                out.println("  " + e.getMessage());
            }
        }
    }

    private LocalDate readDate(String prompt) { return readDate(prompt, null); }

    /** Accepts yyyy-MM-dd, "today", "tomorrow" or +N (days from today). A blank answer uses the default, if any. */
    private LocalDate readDate(String prompt, LocalDate defaultValue) {
        while (true) {
            String s = line(prompt);
            if (s.isEmpty() && defaultValue != null) return defaultValue;
            try { return parseDate(s); }
            catch (StudyBuddyException e) { out.println("  " + e.getMessage()); }
        }
    }

    private LocalDate parseDate(String s) throws StudyBuddyException {
        String t = s.trim().toLowerCase();
        if (t.equals("today")) return LocalDate.now();
        if (t.equals("tomorrow")) return LocalDate.now().plusDays(1);
        try {
            if (t.startsWith("+")) return LocalDate.now().plusDays(Long.parseLong(t.substring(1)));
            return LocalDate.parse(t);
        } catch (NumberFormatException | DateTimeParseException e) {
            throw new studybuddy.exception.InvalidDeadlineException("Please use yyyy-MM-dd (for example 2026-12-05), 'today', 'tomorrow' or +days.");
        }
    }

    private int parseInt(String s) throws StudyBuddyException {
        try { return Integer.parseInt(s.trim()); }
        catch (NumberFormatException e) { throw new studybuddy.exception.InvalidInputException("\"" + s + "\" is not a whole number."); }
    }

    private double parseDouble(String s) throws StudyBuddyException {
        try { return Double.parseDouble(s.trim()); }
        catch (NumberFormatException e) { throw new studybuddy.exception.InvalidInputException("\"" + s + "\" is not a number."); }
    }

    private void saveQuietly() {
        if (student == null) return;
        try {
            files.save(student);
            out.println("Data saved to \"" + files.getDirectory() + "\".");
        } catch (StudyBuddyException e) {
            out.println("  ! Could not save: " + e.getMessage());
        }
    }
}
