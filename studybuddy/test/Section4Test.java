import studybuddy.exception.*;
import studybuddy.model.*;
import studybuddy.scheduling.*;
import studybuddy.storage.*;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDate;
import java.util.List;

public class Section4Test {
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

    static Student buildStudent() throws Exception {
        Student st = new Student("Maria Santos", "BS Computer Science");
        StudyPlanner p = st.getPlanner();
        p.setDailyAvailableHours(5);
        p.addSubject("Mathematics", "Calculus I, with \"limits\"");
        p.addSubject("Programming");
        p.addSubject("English");
        p.addExam(1, "Calculus Midterm", TODAY.plusDays(3), 8, 4, List.of("Limits", "Derivatives; Integrals"));
        p.addQuiz(1, "Quiz, Part 2", TODAY.plusDays(1), 2, 2, "Chapters 3-4");
        p.addAssignment(2, "Java Lab", TODAY.plusDays(12), 4, 3, "ZIP");
        p.addReadingTask(3, "Read Ch. 7", TODAY.plusDays(20), 1.5, 1, 30);
        p.addAssignment(3, "Essay", TODAY.plusDays(6), 3, 2);          // no format
        p.logSession(1, TODAY.minusDays(1), 3, "Practice, set A\nand B");
        p.logSession(3, TODAY, 1.5, null);
        p.markTaskCompleted(5);
        p.restoreTask(2, new Assignment(60, "Missed Report", TODAY.minusDays(3), 2, 3));   // already past deadline
        return st;
    }

    static String read(Path dir, String file) throws Exception { return new String(Files.readAllBytes(dir.resolve(file)), StandardCharsets.UTF_8); }

    public static void main(String[] args) throws Exception {
        System.out.println("== CsvUtil ==");
        check("plain fields", CsvUtil.parseLine("a,b,c").equals(List.of("a", "b", "c")));
        check("quoted comma and doubled quotes", CsvUtil.parseLine("1,\"Essay, \"\"v1\"\"\",x").equals(List.of("1", "Essay, \"v1\"", "x")));
        check("empty fields kept", CsvUtil.parseLine(",,").size() == 3);
        expectThrows("unterminated quote rejected", InvalidInputException.class, () -> CsvUtil.parseLine("1,\"oops"));

        System.out.println("== First run (nothing saved) ==");
        Path dir = Files.createTempDirectory("studybuddy-test").resolve("data");
        FileManager fm = new FileManager(dir);
        check("hasSavedData false", !fm.hasSavedData());
        LoadResult empty = fm.load();
        check("load() on missing folder returns empty result, no crash", empty.isEmpty() && !empty.hasWarnings());

        System.out.println("== Save / load round trip ==");
        Student original = buildStudent();
        StudyPlanner op = original.getPlanner();
        op.refreshAll(TODAY);
        fm.save(original);
        check("4 data files written", Files.exists(dir.resolve("student.txt")) && Files.exists(dir.resolve("subjects.csv"))
                && Files.exists(dir.resolve("tasks.csv")) && Files.exists(dir.resolve("sessions.csv")));
        check("No leftover .tmp files", !Files.exists(dir.resolve("tasks.csv.tmp")));
        System.out.println("  tasks.csv:\n" + read(dir, "tasks.csv").replaceAll("(?m)^", "     "));

        LoadResult lr = fm.load();
        System.out.println("  " + lr);
        check("No warnings on a clean load", !lr.hasWarnings());
        Student loaded = lr.getStudent();
        StudyPlanner lp = loaded.getPlanner();
        check("Student name/course", loaded.getName().equals("Maria Santos") && loaded.getCourse().equals("BS Computer Science"));
        check("Daily hours restored", lp.getDailyAvailableHours() == 5);
        check("3 subjects, 6 tasks, 2 sessions", lr.getSubjectsLoaded() == 3 && lr.getTasksLoaded() == 6 && lr.getSessionsLoaded() == 2);
        check("Subject description with comma and quotes", lp.findSubject(1).getDescription().equals("Calculus I, with \"limits\""));

        boolean same = true;
        for (StudyItem t : op.getAllTasks()) {
            StudyItem l = lp.findTask(t.getId());
            if (!l.getClass().equals(t.getClass()) || !l.getTitle().equals(t.getTitle())
                    || !l.getDeadline().equals(t.getDeadline()) || l.getEstimatedHours() != t.getEstimatedHours()
                    || l.getRemainingHours() != t.getRemainingHours() || l.getDifficulty() != t.getDifficulty()
                    || l.getStatus() != t.getStatus() || !l.getSubjectName().equals(t.getSubjectName())
                    || l.getPriorityScore() != t.getPriorityScore() || !l.getDetails().equals(t.getDetails())) {
                same = false;
                System.out.println("    mismatch on task #" + t.getId() + ":\n      " + t + "\n      " + l);
            }
        }
        check("Every task identical after reload (type, fields, status, priority, details)", same);
        Exam le = (Exam) lp.findTask(1);
        check("Exam topics restored (';' replaced by ',')", le.getCoverageTopics().equals(List.of("Limits", "Derivatives, Integrals")));
        check("Quiz title with comma", lp.findTask(2).getTitle().equals("Quiz, Part 2"));
        check("Reading pages restored", ((ReadingTask) lp.findTask(4)).getPagesToRead() == 30);
        check("Completed task stays completed", lp.findTask(5).getStatus() == TaskStatus.COMPLETED);
        check("Past-deadline task is Overdue after load", lp.findTask(60).getStatus() == TaskStatus.OVERDUE);
        check("Session links to its task and subject", lp.getSessions().get(0).getTask() == lp.findTask(1)
                && lp.getSessions().get(0).getSubjectName().equals("Mathematics"));
        check("Session notes: newline flattened", lp.getSessions().get(0).getNotes().equals("Practice, set A and B"));
        check("Hours per subject match", lp.getTotalStudyHours() == 4.5);
        check("New IDs continue after loaded ones", lp.addAssignment(2, "After load", TODAY.plusDays(4), 1, 1).getId() == 61
                && lp.logSession(1, 1, null).getId() == 3);
        check("Remaining hours after session survive", lp.findTask(1).getRemainingHours() == 4);   // 8 - 3 - 1 (new session)

        System.out.println("== Stability ==");
        Student loaded2 = fm.load().getStudent();
        String before = read(dir, "tasks.csv");
        fm.save(loaded2);
        check("save(load(save(x))) writes identical tasks.csv", read(dir, "tasks.csv").equals(before));
        check("Previous version kept as .bak", Files.exists(dir.resolve("tasks.csv.bak")));

        System.out.println("== Simulation data is never saved ==");
        new PriorityScheduler().generate(op, TODAY);               // scheduling works on copies
        String snapshot = read(dir, "tasks.csv");
        fm.save(original);
        check("Running the scheduler does not change saved tasks.csv", read(dir, "tasks.csv").equals(snapshot));
        StudyItem copy = op.findTask(1).copy();
        check("copy() is flagged as simulation copy", copy.isSimulationCopy() && !op.findTask(1).isSimulationCopy());
        expectThrows("Subject refuses simulation copies", InvalidInputException.class, () -> op.findSubject(1).addTask(copy));
        expectThrows("restoreTask refuses simulation copies", InvalidInputException.class, () -> op.restoreTask(1, copy));
        check("Planner still has exactly its real tasks", op.getAllTasks().size() == 6 && !fm.load().hasWarnings());

        System.out.println("== Corrupted tasks.csv ==");
        Path dir2 = Files.createTempDirectory("studybuddy-corrupt").resolve("data");
        FileManager fm2 = new FileManager(dir2);
        fm2.save(buildStudent());
        String good = read(dir2, "tasks.csv");
        String bad = good
                + "99,1,QUIZ,Bad hours,2030-01-01,abc,1,2,PENDING,x\n"          // number
                + "98,1,HOMEWORK,Bad type,2030-01-01,1,1,2,PENDING,x\n"          // type
                + "97,1,QUIZ,Bad date,2030-13-45,1,1,2,PENDING,x\n"              // date
                + "96,77,QUIZ,Bad subject,2030-01-01,1,1,2,PENDING,x\n"          // subject
                + "95,1,QUIZ,\"Unterminated,2030-01-01,1,1,2,PENDING,x\n"        // quote
                + "94,1,QUIZ,Bad difficulty,2030-01-01,1,1,9,PENDING,x\n"        // difficulty
                + "93,1\n"                                                       // too short
                + "1,1,QUIZ,Duplicate id,2030-01-01,1,1,2,PENDING,x\n"           // duplicate
                + "92,1,QUIZ,Negative remaining,2030-01-01,1,-4,2,PENDING,x\n"   // remaining
                + "91,1,QUIZ,Fine one,2030-01-01,2,2,2,PENDING,Chapter 1\n";     // valid
        Files.write(dir2.resolve("tasks.csv"), bad.getBytes(StandardCharsets.UTF_8));
        LoadResult cr = fm2.load();
        cr.getWarnings().forEach(w -> System.out.println("     warning: " + w));
        check("Load survives corrupted lines", !cr.isEmpty());
        check("9 bad lines reported", cr.getWarnings().size() == 9);
        check("6 original tasks + 1 valid new line loaded", cr.getTasksLoaded() == 7 && cr.getStudent().getPlanner().findTask(91).getDetails().contains("Chapter 1"));
        check("Corrupt original kept as tasks.csv.corrupt", Files.exists(dir2.resolve("tasks.csv.corrupt")) && read(dir2, "tasks.csv.corrupt").equals(bad));
        check("Sessions still load (their tasks exist)", cr.getSessionsLoaded() == 2);

        System.out.println("== Missing and unreadable files ==");
        Files.delete(dir2.resolve("student.txt"));
        LoadResult noStudent = fm2.load();
        check("Missing student.txt -> default student + warning", noStudent.getStudent().getName().equals("Student")
                && noStudent.getWarnings().get(0).contains("student.txt is missing"));
        Files.write(dir2.resolve("sessions.csv"), new byte[]{(byte) 0xC3, (byte) 0x28, (byte) 0xFF, (byte) 0xFE, 10, 0x41});
        LoadResult binary = fm2.load();
        check("Binary garbage in sessions.csv does not crash", binary.getSessionsLoaded() == 0 && binary.getWarnings().toString().contains("sessions.csv"));
        Files.delete(dir2.resolve("tasks.csv"));
        Files.delete(dir2.resolve("sessions.csv"));
        LoadResult noTasks = fm2.load();
        check("Missing tasks/sessions files -> subjects only", noTasks.getSubjectsLoaded() == 3 && noTasks.getTasksLoaded() == 0);
        Files.write(dir2.resolve("subjects.csv"), "1,Math,ok\n2,Physics,ok\n".getBytes(StandardCharsets.UTF_8));   // no header line
        check("Header line is optional", fm2.load().getSubjectsLoaded() == 2);
        Files.write(dir2.resolve("student.txt"), "name=\ndailyAvailableHours=lots\nnonsense\n".getBytes(StandardCharsets.UTF_8));
        LoadResult badStudent = fm2.load();
        check("Bad student.txt -> defaults + warnings", badStudent.getStudent().getName().equals("Student")
                && badStudent.getStudent().getPlanner().getDailyAvailableHours() == 4.0 && badStudent.hasWarnings());

        System.out.println("== Save failures ==");
        Path blocker = Files.createTempFile("studybuddy-blocker", ".txt");
        expectThrows("Folder path is actually a file -> DataStorageException", DataStorageException.class,
                () -> new FileManager(blocker.resolve("data")).save(buildStudent()));
        expectThrows("Null student rejected", InvalidInputException.class, () -> fm.save(null));

        System.out.println("\nRESULT: " + passed + " passed, " + failed + " failed");
        if (failed > 0) System.exit(1);
    }
}
