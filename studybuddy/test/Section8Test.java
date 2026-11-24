import studybuddy.exception.*;
import studybuddy.model.*;
import studybuddy.scheduling.*;
import studybuddy.storage.FileManager;
import studybuddy.storage.LoadResult;

import java.lang.reflect.Method;
import java.nio.file.*;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

/** Checks every point where the code was aligned with the project proposal (StudyPlanner.pdf). */
public class Section8Test {
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

    static boolean hasMethod(Class<?> c, String name) {
        return Arrays.stream(c.getMethods()).map(Method::getName).anyMatch(name::equals);
    }

    static LocalDate d(int offset) { return TODAY.plusDays(offset); }

    public static void main(String[] args) throws Exception {
        System.out.println("== Interfaces named as in the proposal ==");
        check("Schedulable has getDeadline() and getEstimatedHours()", hasMethod(Schedulable.class, "getDeadline") && hasMethod(Schedulable.class, "getEstimatedHours"));
        check("Exportable has toCsvLine() (and no toCsvRow)", hasMethod(Exportable.class, "toCsvLine") && !hasMethod(Exportable.class, "toCsvRow"));
        check("Task classes implement both; Subject implements Exportable",
                Schedulable.class.isAssignableFrom(Exam.class) && Exportable.class.isAssignableFrom(Quiz.class) && Exportable.class.isAssignableFrom(Subject.class));

        System.out.println("== Priority: daysLeft <= 0 gets the highest score ==");
        Quiz dueToday = new Quiz(1, "Due today", TODAY, 2, 2);
        dueToday.recalculatePriority(TODAY);
        check("Due today: 3*2 + 10 + 2 + 100 = 118, CRITICAL", dueToday.getPriorityScore() == 118 && dueToday.getPriorityLevel() == Priority.CRITICAL);
        check("Due today is NOT yet 'overdue' (status stays Pending until the day has passed)", !dueToday.isOverdue(TODAY) && dueToday.getStatus() == TaskStatus.PENDING);
        Quiz dueTomorrow = new Quiz(2, "Tomorrow", d(1), 2, 2);
        dueTomorrow.recalculatePriority(TODAY);
        check("Due tomorrow: no bonus (6 + 9 + 2 = 17)", dueTomorrow.getPriorityScore() == 17);
        Quiz late = new Quiz(3, "Late", d(-3), 2, 2);
        late.recalculatePriority(TODAY);
        check("Overdue: still highest (6 + 13 + 2 + 100 = 121)", late.getPriorityScore() == 121);
        dueToday.markCompleted();
        dueToday.recalculatePriority(TODAY);
        check("Completed task due today gets no bonus", dueToday.getPriorityScore() == 18);
        Exam exam = new Exam(4, "Exam", d(5), 4, 3);
        ReadingTask reading = new ReadingTask(5, "Reading", d(5), 4, 3);
        exam.recalculatePriority(TODAY);
        reading.recalculatePriority(TODAY);
        check("Polymorphism: an Exam outscores a ReadingTask with the same deadline", exam.getPriorityScore() > reading.getPriorityScore());
        check("Deadline label text", dueTomorrow.getDeadlineLabel(TODAY).equals("due tomorrow") && late.getDeadlineLabel(TODAY).equals("OVERDUE by 3 days")
                && new Quiz(6, "x", TODAY, 1, 1).getDeadlineLabel(TODAY).equals("DUE TODAY") && exam.getDeadlineLabel(TODAY).equals("due in 5 days"));

        System.out.println("== addTask() overloads ==");
        Student st = new Student("Maria");
        StudyPlanner p = st.getPlanner();
        p.addSubject("Mathematics");
        p.addSubject("Programming");
        StudyItem a = p.addTask(1, TaskType.READING_TASK, "Skim ch. 1", d(10));
        check("addTask(title, deadline): defaults 1h, difficulty 3, right subclass", a instanceof ReadingTask && a.getEstimatedHours() == 1.0 && a.getDifficulty() == 3);
        StudyItem b = p.addTask(1, TaskType.QUIZ, "Quiz 1", d(4), 2.5);
        check("addTask(+ hours): 2.5h, difficulty 3", b instanceof Quiz && b.getEstimatedHours() == 2.5 && b.getDifficulty() == 3);
        StudyItem c = p.addTask(2, TaskType.EXAM, "Final", d(9), 10, 5);
        check("addTask(+ hours, difficulty): Exam, 10h, difficulty 5", c instanceof Exam && c.getEstimatedHours() == 10 && c.getDifficulty() == 5);
        StudyItem e = p.addTask(2, TaskType.ASSIGNMENT, "Lab", d(6), 3, 2);
        check("All four types can be created through addTask()", e instanceof Assignment && p.getAllTasks().size() == 4);
        check("IDs continue 1..4 and tasks are in their subjects", c.getId() == 3 && c.getSubjectName().equals("Programming") && p.getTasksBySubject(1).size() == 2);
        expectThrows("addTask: past deadline", InvalidDeadlineException.class, () -> p.addTask(1, TaskType.QUIZ, "Old", d(-1)));
        expectThrows("addTask: hours 0", InvalidStudyHoursException.class, () -> p.addTask(1, TaskType.QUIZ, "Q", d(1), 0));
        expectThrows("addTask: difficulty 6", InvalidInputException.class, () -> p.addTask(1, TaskType.QUIZ, "Q", d(1), 1, 6));
        expectThrows("addTask: null type", InvalidInputException.class, () -> p.addTask(1, null, "Q", d(1)));
        expectThrows("addTask: unknown subject", SubjectNotFoundException.class, () -> p.addTask(9, TaskType.QUIZ, "Q", d(1)));
        check("Failed addTask calls used no IDs", p.addTask(1, TaskType.QUIZ, "Next", d(3)).getId() == 5);
        p.deleteTask(5);

        System.out.println("== HashMap of subject names ==");
        check("Lookup by name ignores case and spaces", p.findSubject("  MATHEMATICS ").getId() == 1);
        p.editSubject(1, "Calculus", null);
        check("After renaming: new name found", p.findSubject("calculus").getId() == 1);
        expectThrows("After renaming: old name gone", SubjectNotFoundException.class, () -> p.findSubject("Mathematics"));
        expectThrows("Rename to an existing name rejected", InvalidInputException.class, () -> p.editSubject(1, "programming", null));
        p.addSubject("Temp");
        p.deleteSubject(3);
        expectThrows("Deleted subject's name no longer resolves", SubjectNotFoundException.class, () -> p.findSubject("Temp"));
        check("The freed name can be used again", p.addSubject("temp").getName().equals("temp"));
        Path dir = Files.createTempDirectory("sb-align").resolve("data");
        new FileManager(dir).save(st);
        LoadResult lr = new FileManager(dir).load();
        check("After save/load, lookup by name works", lr.getStudent().getPlanner().findSubject("CALCULUS").getId() == 1);

        System.out.println("== Deadline alert list ==");
        Student s2 = new Student("Alerts");
        StudyPlanner q = s2.getPlanner();
        q.addSubject("Math");
        q.restoreTask(1, new Assignment(10, "Missed", d(-2), 2, 3));
        q.restoreTask(1, new Quiz(11, "Today", TODAY, 1, 2));
        q.addTask(1, TaskType.EXAM, "Soon", d(3), 4, 4);
        q.addTask(1, TaskType.QUIZ, "Later", d(4), 2, 2);
        Assignment finished = new Assignment(20, "Done late", d(-1), 2, 2);
        finished.markCompleted();
        q.restoreTask(1, finished);
        List<StudyItem> alerts = q.getDeadlineAlerts(TODAY);
        check("Alerts: overdue, due today, due in 3 days (not +4, not completed), soonest first",
                alerts.size() == 3 && alerts.get(0).getId() == 10 && alerts.get(1).getId() == 11 && alerts.get(2).getTitle().equals("Soon"));
        check("Wider window (7 days) adds the later task", q.getDeadlineAlerts(TODAY, 7).size() == 4);
        check("Negative window behaves like 0 (overdue + due today)", q.getDeadlineAlerts(TODAY, -5).size() == 2);
        check("No-argument overload uses today", q.getDeadlineAlerts().size() == 3);
        check("Labels for the alert list", alerts.get(0).getDeadlineLabel(TODAY).equals("OVERDUE by 2 days") && alerts.get(1).getDeadlineLabel(TODAY).equals("DUE TODAY")
                && alerts.get(2).getDeadlineLabel(TODAY).equals("due in 3 days"));
        check("Due-today task is first in the priority ranking after the overdue one", q.getTasksByPriority(TODAY).get(1).getId() == 11);
        StudySchedule sched = new PriorityScheduler().generate(q, TODAY);
        check("Scheduler plans the overdue and due-today tasks first", sched.getEntriesForDate(TODAY).get(0).getTaskId() == 10);

        System.out.println("== File I/O classes named in the proposal ==");
        String fm = Files.readString(Paths.get("src", "studybuddy", "storage", "FileManager.java"));
        check("FileManager uses FileWriter and BufferedReader", fm.contains("new FileWriter(") && fm.contains("new BufferedReader(") && fm.contains("new FileReader(") && fm.contains("BufferedWriter"));
        check("Streams are closed with try-with-resources", fm.contains("try (BufferedWriter") && fm.contains("try (BufferedReader"));

        System.out.println("\nRESULT: " + passed + " passed, " + failed + " failed");
        if (failed > 0) System.exit(1);
    }
}
