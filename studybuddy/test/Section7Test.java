import studybuddy.app.ConsoleMenu;
import studybuddy.app.DemoRunner;
import studybuddy.model.Student;
import studybuddy.storage.FileManager;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Scanner;

public class Section7Test {
    static int passed = 0, failed = 0;
    static final Path SCRIPT = Paths.get("python", "weekly_report.py");

    static void check(String name, boolean ok) {
        if (ok) passed++; else failed++;
        System.out.println((ok ? "  PASS  " : "  FAIL  ") + name);
    }

    static String runMenu(Path dataDir, Path exportDir, String... inputLines) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(bytes, true, "UTF-8");
        String input = inputLines.length == 0 ? "" : String.join("\n", inputLines) + "\n";
        new ConsoleMenu(new Scanner(input), out, new FileManager(dataDir), SCRIPT, exportDir).run();
        return new String(bytes.toByteArray(), StandardCharsets.UTF_8);
    }

    public static void main(String[] args) throws Exception {
        System.out.println("== Scripted demo ==");
        ByteArrayOutputStream demoBytes = new ByteArrayOutputStream();
        boolean ok = new DemoRunner(new PrintStream(demoBytes, true, "UTF-8"), Files.createTempDirectory("sb-demo"), SCRIPT).run();
        String demo = new String(demoBytes.toByteArray(), StandardCharsets.UTF_8);
        check("Demo finishes successfully", ok);
        for (String h : new String[]{"1. SUBJECT MANAGEMENT", "2. TASK MANAGEMENT", "3. AUTOMATIC PRIORITIES", "4. STUDY SCHEDULE GENERATOR",
                "5. STUDY SESSION TRACKING", "6. WEEKLY PROGRESS REPORT", "7. WHAT-IF SIMULATOR", "8. SAVING AND LOADING"})
            check("Demo has section \"" + h + "\"", demo.contains(h));
        check("Demo: all 4 task types shown", demo.contains("Exam") && demo.contains("Quiz") && demo.contains("Assignment") && demo.contains("Reading Task"));
        check("Demo: invalid input is rejected with custom exceptions", demo.contains("InvalidDeadlineException") && demo.contains("InvalidStudyHoursException")
                && demo.contains("SubjectNotFoundException") && demo.contains("TaskNotFoundException") && demo.contains("InvalidInputException"));
        check("Demo: overloaded addTask() shown", demo.contains("Quick add with the overloaded addTask()") && demo.contains("Skim Chapter 8"));
        check("Demo: deadline alert list shown", demo.contains("DEADLINE ALERT LIST") && demo.contains("due in 2 days"));
        check("Demo: schedule shows a deadline warning", demo.contains("WARNINGS:"));
        check("Demo: Python report and chart", demo.contains("STUDYBUDDY WEEKLY PROGRESS REPORT") && demo.contains("Bar chart saved"));
        check("Demo: simulation says real plan unchanged", demo.contains("is the real plan unchanged? YES"));
        check("Demo: apply changes tomorrow's hours 4.0 -> 0.0", demo.contains("Before applying, study planned tomorrow: 4.0h") && demo.contains("After applying,  study planned tomorrow: 0.0h"));
        check("Demo: save/load verified", demo.contains("Loaded data identical to what was saved? YES"));
        check("Demo: no stack traces", !demo.contains("\tat ") && !demo.contains("Exception in thread"));

        System.out.println("== Interactive menu: first session ==");
        Path root = Files.createTempDirectory("sb-menu");
        Path data = root.resolve("data"), export = root.resolve("export");
        String s1 = runMenu(data, export,
                "Maria", "BSCS",                                  // first-run setup
                "1", "Mathematics", "Calculus I",                 // 1. Add Subject
                "1", "Programming", "",                           //    (no description)
                "1", "mathematics", "",                           //    duplicate -> error, back at menu
                "2", "1", "1", "Calculus Midterm",                // 2. Add Task: subject 1, exam, title
                "abc", "2020-01-01", "+5",                        //    deadline: bad format, PAST date (re-asked), valid
                "0", "-3", "8",                                   //    hours: 0 and -3 are re-asked, then 8
                "9", "4",                                         //    difficulty 9 is re-asked, then 4
                "Limits, Derivatives",                            //    exam topics
                "2", "2", "3", "Quiz 2", "tomorrow", "2", "2", "Ch 3",   // add a quiz due tomorrow
                "3", "0",                                         // 3. View Tasks (alert list + ranked list), back
                "4", "6",                                         // 4. Generate Study Schedule: 6 hours per day
                "4", "",                                          //    again, Enter keeps 6
                "5", "2", "1", "", "2", "Practice problems",      // 5. Log Session (date blank = today)
                "5", "2", "1", "", "-3", "",                      //    invalid hours (notes are asked first) -> error, back at menu
                "5", "1", "2",                                    //    Mark Task as Done (the quiz)
                "6", "1",                                         // 6. Progress report: CSV + Python
                "8", "4", "1", "tomorrow", "6", "7", "y", "0",    // 8. More > What-If > skip tomorrow, comparison, apply, back
                "5", "3", "0", "0",                               //    plan settings > view adjustments > back > back
                "foo",                                            // invalid main-menu choice
                "7");                                             // 7. Save and Exit
        System.out.println(s1.lines().limit(60).reduce("", (x, y) -> x + "     " + y + "\n"));
        check("First run asks for setup", s1.contains("No saved data found") && s1.contains("Your name"));
        check("Main menu matches the proposal", s1.contains(" 1. Add Subject") && s1.contains(" 2. Add Task") && s1.contains(" 3. View Tasks")
                && s1.contains(" 4. Generate Study Schedule") && s1.contains(" 5. Mark Task as Done / Log Session")
                && s1.contains(" 6. View Progress Report") && s1.contains(" 7. Save and Exit"));
        check("Subjects added", s1.contains("Added: [#1] Mathematics - Calculus I") && s1.contains("Added: [#2] Programming"));
        check("Duplicate subject rejected, menu continues", s1.contains("already exists"));
        check("Invalid date text re-asked", s1.contains("Please use yyyy-MM-dd"));
        check("PAST deadline: error shown and the deadline is asked AGAIN", s1.contains("is already in the past. Please enter today's date or a later one."));
        check("Bad hours re-asked (0 and -3), bad difficulty re-asked", s1.contains("Study hours must be greater than 0") && s1.contains("Please enter a number from 1 to 5"));
        check("Exam added after the re-asks", s1.contains("Added: [#1] Exam"));
        check("Quiz added with 'tomorrow'", s1.contains("Added: [#2] Quiz"));
        check("Deadline alert list shows the quiz due tomorrow", s1.contains("DEADLINE ALERTS (1)") && s1.contains("due tomorrow"));
        check("Ranked task list shown", s1.contains("ranked from highest to lowest priority"));
        check("Generate asks for hours per day and uses the answer", s1.contains("Study hours available per day") && s1.contains("of 6.0h"));
        check("Session logged and remaining hours updated", s1.contains("Logged: [S1]") && s1.contains("Remaining on \"Calculus Midterm\": 6.0h"));
        check("Invalid session hours rejected", s1.contains("Study hours must be greater than 0"));
        check("Task marked as done", s1.contains("Marked as done: Quiz 2"));
        check("Report ran through Python", s1.contains("CSV files written") && s1.contains("STUDYBUDDY WEEKLY PROGRESS REPORT"));
        check("What-If shown, comparison, applied", s1.contains("WHAT-IF SIMULATION: Skip studying on") && s1.contains("SCHEDULE COMPARISON") && s1.contains("Applied."));
        check("Adjustment visible in the plan settings", s1.contains("Study hours on") && s1.contains(": 0.0h"));
        check("Invalid main-menu choice handled", s1.contains("Please enter a number from 1 to 8."));
        check("Saved on exit", s1.contains("Data saved to") && s1.contains("Goodbye!"));
        check("Data files exist", Files.exists(data.resolve("tasks.csv")) && Files.exists(data.resolve("adjustments.csv")));
        check("No stack traces", !s1.contains("\tat ") && !s1.contains("Exception in thread"));

        System.out.println("== Interactive menu: restart, reload, delete ==");
        String s2 = runMenu(data, export,
                "3", "0",                                         // view tasks, back
                "8", "1", "5", "2", "n",                          // more > manage subjects > delete Programming, answer no
                "5", "2", "y", "0", "0",                          //   ... delete again, yes; back; back
                "7");
        check("Welcome back with loaded counts", s2.contains("Welcome back, Maria") && s2.contains("2 subjects, 2 tasks, 1 sessions"));
        check("Nothing due soon: no start-up alert, and View Tasks says so", !s2.contains("!! DEADLINE ALERTS") && s2.contains("No tasks are overdue or due within 3 days."));
        Student alertStudent = new Student("Ben");
        alertStudent.getPlanner().addSubject("Math");
        alertStudent.getPlanner().addQuiz(1, "Surprise Quiz", java.time.LocalDate.now().plusDays(1), 1, 2, null);
        new FileManager(root.resolve("alert")).save(alertStudent);
        String s7 = runMenu(root.resolve("alert"), export, "7");
        check("Alert list shown at start-up when a task is due soon", s7.contains("Welcome back, Ben") && s7.contains("!! DEADLINE ALERTS (1)") && s7.contains("due tomorrow"));
        check("Loaded subjects and tasks visible", s2.contains("Calculus Midterm") && s2.contains("Quiz 2"));
        check("Deleting can be cancelled", s2.contains("(y/n)"));
        check("Deleting removes subject and its tasks", s2.contains("Deleted the subject and 1 tasks."));
        String s3 = runMenu(data, export, "3", "0", "7");
        check("Deleted data stays deleted after another restart", s3.contains("Welcome back") && s3.contains("1 subjects, 1 tasks") && !s3.contains("Quiz 2"));

        System.out.println("== Interactive menu: input ends unexpectedly ==");
        String s4 = runMenu(root.resolve("empty"), export);
        check("EOF at the very first prompt: no crash", s4.contains("(input ended)") && s4.contains("Goodbye!"));
        String s5 = runMenu(root.resolve("partial"), export, "Ana", "", "1", "Biology");
        check("EOF in the middle of a prompt: earlier work is saved, half-typed subject is not", s5.contains("(input ended)") && s5.contains("Data saved")
                && !Files.readString(root.resolve("partial").resolve("subjects.csv")).contains("Biology"));
        String s6 = runMenu(root.resolve("partial"), export, "7");
        check("Saved partial data reloads", s6.contains("Welcome back, Ana"));

        System.out.println("\nRESULT: " + passed + " passed, " + failed + " failed");
        if (failed > 0) System.exit(1);
    }
}
