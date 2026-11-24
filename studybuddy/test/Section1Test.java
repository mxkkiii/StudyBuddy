import studybuddy.exception.*;
import studybuddy.model.*;

import java.time.LocalDate;
import java.util.List;
import java.util.PriorityQueue;

/** Simple self-checking test (no JUnit needed). Uses a fixed "today" so results are repeatable. */
public class Section1Test {
    static int passed = 0, failed = 0;
    static final LocalDate TODAY = LocalDate.of(2026, 11, 23);

    interface Action { void run() throws Exception; }

    static void check(String name, boolean condition) {
        if (condition) { passed++; System.out.println("  PASS  " + name); }
        else { failed++; System.out.println("  FAIL  " + name); }
    }

    static void expectThrows(String name, Class<? extends Exception> type, Action action) {
        try { action.run(); check(name + " (no exception thrown)", false); }
        catch (Exception e) { check(name, type.isInstance(e)); }
    }

    public static void main(String[] args) throws Exception {
        System.out.println("== Priority formula ==");
        Exam exam = new Exam(1, "Calculus Midterm", TODAY.plusDays(3), 8, 4);
        exam.recalculatePriority(TODAY);
        check("Exam: 5*2 + (10-3) + 4 = 21", exam.getPriorityScore() == 21);
        check("Exam level is HIGH", exam.getPriorityLevel() == Priority.HIGH);

        Assignment asg = new Assignment(2, "Lab Report", TODAY.plusDays(12), 4, 3, "PDF");
        asg.recalculatePriority(TODAY);
        check("Assignment: 4*2 + max(0,10-12) + 3 = 11", asg.getPriorityScore() == 11);

        Quiz quiz = new Quiz(3, "Quiz 2", TODAY.plusDays(1), 2, 2, "Chapters 3-4");
        quiz.recalculatePriority(TODAY);
        check("Quiz: 3*2 + 9 + 2 = 17", quiz.getPriorityScore() == 17);

        ReadingTask reading = new ReadingTask(4, "Read Ch. 7", TODAY.plusDays(20), 1.5, 1, 30);
        reading.recalculatePriority(TODAY);
        check("Reading: 1*2 + 0 + 1 = 3 (LOW)", reading.getPriorityScore() == 3 && reading.getPriorityLevel() == Priority.LOW);

        Assignment late = new Assignment(5, "Late Essay", TODAY.minusDays(2), 3, 3);
        late.recalculatePriority(TODAY);
        check("Overdue assignment: 8 + 12 + 3 + 100 = 123 (CRITICAL)",
                late.getPriorityScore() == 123 && late.getPriorityLevel() == Priority.CRITICAL);

        System.out.println("== Polymorphism + PriorityQueue ==");
        PriorityQueue<StudyItem> pq = new PriorityQueue<>();
        List<StudyItem> all = List.of(exam, asg, quiz, reading, late);
        pq.addAll(all);
        StringBuilder order = new StringBuilder();
        while (!pq.isEmpty()) order.append(pq.poll().getId()).append(' ');
        check("Queue order is 5 1 3 2 4 (got " + order.toString().trim() + ")", order.toString().trim().equals("5 1 3 2 4"));

        System.out.println("== Validation ==");
        expectThrows("Difficulty 6 rejected", InvalidInputException.class, () -> new Quiz(9, "Q", TODAY, 1, 6));
        expectThrows("Negative hours rejected", InvalidStudyHoursException.class, () -> new Quiz(9, "Q", TODAY, -1, 3));
        expectThrows("Zero hours rejected", InvalidStudyHoursException.class, () -> new Quiz(9, "Q", TODAY, 0, 3));
        expectThrows("Null deadline rejected", InvalidDeadlineException.class, () -> new Quiz(9, "Q", null, 1, 3));
        expectThrows("Blank title rejected", InvalidInputException.class, () -> new Quiz(9, "  ", TODAY, 1, 3));
        expectThrows("Past deadline rejected by setter", InvalidDeadlineException.class,
                () -> quiz.setDeadline(LocalDate.now().minusDays(1)));
        expectThrows("Unknown type text rejected", InvalidInputException.class, () -> TaskType.fromString("homework"));
        check("TaskType.fromString(\"reading task\")", TaskType.fromString("reading task") == TaskType.READING_TASK);

        System.out.println("== Auto-recalculation and progress ==");
        double before = quiz.getPriorityScore();
        quiz.setDifficulty(5);
        check("Changing difficulty recalculates score", quiz.getPriorityScore() != before);

        Assignment work = new Assignment(6, "Essay", TODAY.plusDays(5), 5, 3);
        work.logHours(2);
        check("Logging 2h of 5h leaves 3h", work.getRemainingHours() == 3);
        check("Status becomes In Progress", work.getStatus() == TaskStatus.IN_PROGRESS);
        work.logHours(10);
        check("Over-logging floors remaining at 0", work.getRemainingHours() == 0);
        expectThrows("logHours(-1) rejected", InvalidStudyHoursException.class, () -> work.logHours(-1));

        Assignment est = new Assignment(7, "Report", TODAY.plusDays(5), 5, 3);
        est.logHours(2);
        est.setEstimatedHours(8);
        check("Raising estimate 5->8 after 2h studied gives 6h left", est.getRemainingHours() == 6);

        System.out.println("== Status ==");
        Assignment st = new Assignment(8, "Old task", TODAY.minusDays(1), 2, 2);
        st.refreshStatus(TODAY);
        check("Past deadline becomes Overdue", st.getStatus() == TaskStatus.OVERDUE);
        st.markCompleted();
        st.refreshStatus(TODAY);
        check("Completed tasks stay Completed", st.getStatus() == TaskStatus.COMPLETED && st.getRemainingHours() == 0);
        check("Completed task is not unfinished", !st.isUnfinished());

        System.out.println("== copy() independence ==");
        Exam copy = (Exam) exam.copy();
        copy.logHours(5);
        copy.addTopic("Limits");
        check("Original hours untouched by copy", exam.getRemainingHours() == 8 && copy.getRemainingHours() == 3);
        check("Original topics untouched by copy", exam.getCoverageTopics().isEmpty() && copy.getCoverageTopics().size() == 1);

        System.out.println("== Subject ==");
        Subject math = new Subject(1, "Mathematics", "Calculus I");
        math.addTask(exam);
        math.addTask(quiz);
        check("Subject holds 2 tasks", math.getTasks().size() == 2);
        check("Task knows its subject", exam.getSubjectName().equals("Mathematics"));
        check("Total remaining hours = 8 + 2", math.getTotalRemainingHours() == 10);
        expectThrows("Duplicate task ID rejected", InvalidInputException.class, () -> math.addTask(exam));
        expectThrows("Missing task throws TaskNotFoundException", TaskNotFoundException.class, () -> math.findTask(99));
        math.removeTask(3);
        check("removeTask detaches the task", math.getTasks().size() == 1 && quiz.getSubject() == null);
        List<StudyItem> removed = math.clearTasks();
        check("clearTasks returns removed tasks", removed.size() == 1 && math.getTasks().isEmpty());
        expectThrows("Blank subject name rejected", InvalidInputException.class, () -> new Subject(2, " "));

        System.out.println("== CSV ==");
        Assignment csv = new Assignment(10, "Essay, draft \"v1\"", TODAY.plusDays(4), 3, 2, "PDF");
        check("CSV escapes commas and quotes", csv.toCsvLine().contains("\"Essay, draft \"\"v1\"\"\""));
        System.out.println("  " + csv.getCsvHeader());
        System.out.println("  " + csv.toCsvLine());

        System.out.println("\nSample toString():");
        System.out.println("  " + exam);

        System.out.println("\nRESULT: " + passed + " passed, " + failed + " failed");
        if (failed > 0) System.exit(1);
    }
}
