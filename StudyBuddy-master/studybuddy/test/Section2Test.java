import studybuddy.exception.*;
import studybuddy.model.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public class Section2Test {
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

    static String ids(List<StudyItem> list) {
        StringBuilder sb = new StringBuilder();
        for (StudyItem t : list) sb.append(t.getId()).append(' ');
        return sb.toString().trim();
    }

    public static void main(String[] args) throws Exception {
        Student student = new Student("Maria Santos", "BS Computer Science");
        StudyPlanner p = student.getPlanner();

        System.out.println("== Student ==");
        check("Student has a planner", p != null && student.toString().equals("Maria Santos (BS Computer Science)"));
        expectThrows("Blank student name rejected", InvalidInputException.class, () -> new Student(" "));

        System.out.println("== Subjects ==");
        Subject math = p.addSubject("Mathematics", "Calculus I");
        Subject prog = p.addSubject("Programming");
        Subject eng = p.addSubject("English", "Technical writing");
        check("3 subjects with IDs 1..3", p.getAllSubjects().size() == 3 && eng.getId() == 3);
        expectThrows("Duplicate name rejected (case-insensitive)", InvalidInputException.class, () -> p.addSubject("mathematics"));
        expectThrows("Blank subject name rejected", InvalidInputException.class, () -> p.addSubject(""));
        check("Search 'calc' finds Mathematics", p.searchSubjects("calc").size() == 1);
        check("Search 'PROG' finds Programming", p.searchSubjects("PROG").get(0) == prog);
        check("findSubject by name", p.findSubject("english") == eng);
        expectThrows("Unknown subject ID", SubjectNotFoundException.class, () -> p.findSubject(99));
        expectThrows("Unknown subject name", SubjectNotFoundException.class, () -> p.findSubject("Biology"));
        p.editSubject(3, "English Communication", null);
        check("Edit name keeps description", eng.getName().equals("English Communication") && eng.getDescription().equals("Technical writing"));
        expectThrows("Edit to an existing name rejected", InvalidInputException.class, () -> p.editSubject(3, "Mathematics", null));

        System.out.println("== Tasks ==");
        Exam exam = p.addExam(1, "Calculus Midterm", TODAY.plusDays(3), 8, 4, List.of("Limits", "Derivatives"));
        Quiz quiz = p.addQuiz(1, "Quiz 2", TODAY.plusDays(1), 2, 2, "Chapters 3-4");
        Assignment asg = p.addAssignment(2, "Java Lab", TODAY.plusDays(12), 4, 3, "ZIP");
        ReadingTask read = p.addReadingTask(3, "Read Ch. 7", TODAY.plusDays(20), 1.5, 1, 30);
        Assignment plain = p.addAssignment(3, "Essay Draft", TODAY.plusDays(6), 3, 2);
        check("5 tasks, IDs 1..5", p.getAllTasks().size() == 5 && plain.getId() == 5);
        check("Exam priority is 21", exam.getPriorityScore() == 21);
        check("Overloaded addAssignment defaults format", plain.getSubmissionFormat().equals("Not specified"));
        expectThrows("Past deadline rejected", InvalidDeadlineException.class,
                () -> p.addQuiz(1, "Old", TODAY.minusDays(1), 1, 1, null));
        expectThrows("Hours 0 rejected", InvalidStudyHoursException.class,
                () -> p.addQuiz(1, "Q", TODAY.plusDays(1), 0, 1, null));
        expectThrows("Difficulty 9 rejected", InvalidInputException.class,
                () -> p.addQuiz(1, "Q", TODAY.plusDays(1), 1, 9, null));
        expectThrows("Unknown subject for task", SubjectNotFoundException.class,
                () -> p.addQuiz(42, "Q", TODAY.plusDays(1), 1, 1, null));
        expectThrows("Negative pages rejected", InvalidInputException.class,
                () -> p.addReadingTask(3, "R", TODAY.plusDays(1), 1, 1, -5));
        Assignment after = p.addAssignment(2, "ID check", TODAY.plusDays(30), 1, 1);
        check("Failed adds do not waste IDs (next ID is 6)", after.getId() == 6);
        p.deleteTask(6);

        check("Search tasks by keyword 'derivatives' (in details)", p.searchTasks("derivatives").size() == 1);
        check("Search tasks by subject 'programming'", p.searchTasks("programming").get(0) == asg);
        expectThrows("Task not found", TaskNotFoundException.class, () -> p.findTask(77));
        check("View tasks by subject", p.getTasksBySubject(1).size() == 2);

        System.out.println("== Sorting views ==");
        check("By priority: 1 2 5 3 4  (got " + ids(p.getTasksByPriority()) + ")", ids(p.getTasksByPriority()).equals("1 2 5 3 4"));
        check("By deadline: 2 1 5 3 4  (got " + ids(p.getTasksByDeadline()) + ")", ids(p.getTasksByDeadline()).equals("2 1 5 3 4"));

        System.out.println("== Editing ==");
        p.editTask(3, "Java Lab Final", null, 6.0, 5);
        check("Edit title/hours/difficulty", asg.getTitle().equals("Java Lab Final") && asg.getEstimatedHours() == 6 && asg.getDifficulty() == 5);
        check("Remaining hours follow the estimate (4 -> 6)", asg.getRemainingHours() == 6);
        double scoreBefore = asg.getPriorityScore();
        p.editTask(3, null, TODAY.plusDays(2), null, null);
        check("Changing deadline recalculates priority", asg.getPriorityScore() > scoreBefore);
        expectThrows("Invalid edit rejected", InvalidInputException.class, () -> p.editTask(3, "Should not apply", null, null, 0));
        check("Invalid edit changed nothing", asg.getTitle().equals("Java Lab Final"));

        System.out.println("== Study sessions ==");
        StudySession s1 = p.logSession(1, TODAY.minusDays(1), 3, "Practice problems");
        String csvRow = s1.toCsvLine();
        check("Session logged", p.getSessions().size() == 1 && s1.getSubjectName().equals("Mathematics"));
        check("Exam remaining 8 -> 5", exam.getRemainingHours() == 5);
        check("Exam status In Progress", exam.getStatus() == TaskStatus.IN_PROGRESS);
        p.logSession(2, TODAY, 1.5, null);
        p.logSession(3, TODAY, 2, "Lab 1");
        check("Total hours = 6.5", p.getTotalStudyHours() == 6.5);
        Map<String, Double> perSubject = p.getStudyHoursPerSubject();
        check("Hours per subject: Math 4.5, Prog 2.0, English 0",
                perSubject.get("Mathematics") == 4.5 && perSubject.get("Programming") == 2.0 && perSubject.get("English Communication") == 0.0);
        check("Sessions by date (today) = 2", p.getSessionsByDate(TODAY).size() == 2);
        check("Sessions by subject (Math) = 2", p.getSessionsBySubject(1).size() == 2);
        check("Hours in range (yesterday..yesterday) = 3", p.getTotalStudyHours(TODAY.minusDays(1), TODAY.minusDays(1)) == 3);
        expectThrows("Negative hours rejected", InvalidStudyHoursException.class, () -> p.logSession(1, -2, null));
        expectThrows("Future date rejected", InvalidInputException.class, () -> p.logSession(1, TODAY.plusDays(1), 1, null));
        expectThrows("Unknown task rejected", TaskNotFoundException.class, () -> p.logSession(99, 1, null));
        check("Rejected sessions did not change data", p.getSessions().size() == 3 && exam.getRemainingHours() == 5);
        p.logSession(2, 9, "Over-studied");
        check("Over-logging floors remaining at 0", quiz.getRemainingHours() == 0);

        System.out.println("== Status ==");
        p.markTaskCompleted(2);
        expectThrows("Cannot log on completed task", InvalidInputException.class, () -> p.logSession(2, 1, null));
        p.updateTaskStatus(4, TaskStatus.IN_PROGRESS);
        check("updateTaskStatus works", read.getStatus() == TaskStatus.IN_PROGRESS);
        check("Completed list = 1, pending list = 4", p.getCompletedTasks().size() == 1 && p.getPendingTasks(TODAY).size() == 4);

        StudyItem old = new Assignment(50, "Missed Report", TODAY.minusDays(3), 2, 3);
        p.restoreTask(2, old);
        p.refreshAll(TODAY);
        check("Restored past-deadline task becomes Overdue", old.getStatus() == TaskStatus.OVERDUE && p.getOverdueTasks(TODAY).size() == 1);
        check("Overdue task is first by priority", p.getTasksByPriority(TODAY).get(0) == old);
        check("Pending excludes overdue", p.getPendingTasks(TODAY).size() == 4);
        check("Next task ID continues after restored 50", p.addAssignment(2, "After restore", TODAY.plusDays(5), 1, 1).getId() == 51);

        System.out.println("== Deleting ==");
        int sessionsBefore = p.getSessions().size();
        p.deleteTask(3);
        check("deleteTask removes its sessions", p.getSessions().size() == sessionsBefore - 1);
        int removed = p.deleteSubject(1);
        check("deleteSubject removed 2 tasks (exam+quiz)", removed == 2);
        check("Subject's sessions gone, subject gone", p.getSessions().isEmpty() && p.getAllSubjects().size() == 2);
        expectThrows("Deleted subject not found", SubjectNotFoundException.class, () -> p.findSubject(1));
        expectThrows("Deleted task not found", TaskNotFoundException.class, () -> p.findTask(1));
        check("Total hours reset to 0", p.getTotalStudyHours() == 0);

        System.out.println("== Settings ==");
        p.setDailyAvailableHours(5);
        check("Daily hours = 5", p.getDailyAvailableHours() == 5);
        expectThrows("25 hours/day rejected", InvalidStudyHoursException.class, () -> p.setDailyAvailableHours(25));
        expectThrows("Negative hours/day rejected", InvalidStudyHoursException.class, () -> p.setDailyAvailableHours(-1));
        check("CSV row for a session", csvRow.startsWith("1,1,Calculus Midterm,Mathematics,"));

        System.out.println("\nRESULT: " + passed + " passed, " + failed + " failed");
        if (failed > 0) System.exit(1);
    }
}
