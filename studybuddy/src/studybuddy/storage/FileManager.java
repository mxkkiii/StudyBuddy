package studybuddy.storage;

import studybuddy.exception.DataStorageException;
import studybuddy.exception.InvalidInputException;
import studybuddy.exception.StudyBuddyException;
import studybuddy.model.*;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Saves and loads the student's REAL data in a folder of plain-text files:
 *   student.txt, subjects.csv, tasks.csv, sessions.csv
 *
 * Safety features:
 *  - temporary files are written first and then moved into place, so a crash cannot leave half-written data;
 *  - the previous version of each file is kept as NAME.bak;
 *  - loading never crashes: bad lines are skipped and reported as warnings, and a file with problems is
 *    copied to NAME.corrupt so nothing is lost;
 *  - temporary simulation copies are refused (they can never be saved).
 */
public class FileManager {
    public static final String STUDENT_FILE = "student.txt";
    public static final String SUBJECTS_FILE = "subjects.csv";
    public static final String TASKS_FILE = "tasks.csv";
    public static final String SESSIONS_FILE = "sessions.csv";
    public static final String ADJUSTMENTS_FILE = "adjustments.csv";

    private static final String SUBJECTS_HEADER = "id,name,description";
    private static final String TASKS_HEADER =
            "id,subjectId,type,title,deadline,estimatedHours,remainingHours,difficulty,status,extra";
    private static final String SESSIONS_HEADER = "id,taskId,date,hours,notes";
    private static final String ADJUSTMENTS_HEADER = "type,key,value";

    private final Path directory;

    public FileManager(Path directory) { this.directory = directory; }

    /** Overload: folder given as text, e.g. new FileManager("data"). */
    public FileManager(String directory) { this(Paths.get(directory)); }

    public Path getDirectory() { return directory; }

    public boolean hasSavedData() {
        return Files.exists(directory.resolve(STUDENT_FILE)) || Files.exists(directory.resolve(SUBJECTS_FILE))
                || Files.exists(directory.resolve(TASKS_FILE)) || Files.exists(directory.resolve(SESSIONS_FILE));
    }

    // =====================================================================
    // SAVE
    // =====================================================================
    public void save(Student student) throws StudyBuddyException {
        if (student == null) throw new InvalidInputException("No student to save.");
        StudyPlanner planner = student.getPlanner();

        // Guard: simulation copies must never be written to disk.
        for (StudyItem t : planner.getAllTasks()) {
            if (t.isSimulationCopy()) throw new DataStorageException("Refusing to save: task #" + t.getId() + " is simulation data.");
        }
        for (StudySession s : planner.getSessions()) {
            if (s.getTask().isSimulationCopy()) throw new DataStorageException("Refusing to save: session S" + s.getId() + " is simulation data.");
        }

        List<String> studentLines = new ArrayList<>();
        studentLines.add("version=1");
        studentLines.add("name=" + oneLine(student.getName()));
        studentLines.add("course=" + oneLine(student.getCourse()));
        studentLines.add("dailyAvailableHours=" + planner.getDailyAvailableHours());

        List<String> subjectLines = new ArrayList<>();
        subjectLines.add(SUBJECTS_HEADER);
        for (Subject s : planner.getAllSubjects()) {
            subjectLines.add(s.getId() + "," + Exportable.escape(oneLine(s.getName())) + ","
                    + Exportable.escape(oneLine(s.getDescription())));
        }

        List<String> taskLines = new ArrayList<>();
        taskLines.add(TASKS_HEADER);
        for (StudyItem t : planner.getAllTasks()) {
            taskLines.add(t.getId() + "," + t.getSubject().getId() + "," + t.getTaskType().name() + ","
                    + Exportable.escape(oneLine(t.getTitle())) + "," + t.getDeadline() + "," + t.getEstimatedHours() + ","
                    + t.getRemainingHours() + "," + t.getDifficulty() + "," + t.getStatus().name() + ","
                    + Exportable.escape(oneLine(t.getExtraData())));
        }

        List<String> sessionLines = new ArrayList<>();
        sessionLines.add(SESSIONS_HEADER);
        for (StudySession s : planner.getSessions()) {
            sessionLines.add(s.getId() + "," + s.getTask().getId() + "," + s.getDate() + "," + s.getHours() + ","
                    + Exportable.escape(oneLine(s.getNotes())));
        }

        List<String> adjustmentLines = new ArrayList<>();
        adjustmentLines.add(ADJUSTMENTS_HEADER);
        PlanAdjustments adj = planner.getAdjustments();
        for (Map.Entry<LocalDate, Double> e : adj.getHourOverrides().entrySet())
            adjustmentLines.add("HOURS," + e.getKey() + "," + e.getValue());
        for (Map.Entry<Integer, LocalDate> e : adj.getPostponements().entrySet())
            adjustmentLines.add("POSTPONE," + e.getKey() + "," + e.getValue());
        if (adj.getFocusSubjectId() != null)
            adjustmentLines.add("FOCUS," + adj.getFocusSubjectId() + "," + adj.getFocusSharePercent());

        try {
            Files.createDirectories(directory);
            Path tmpStudent = writeTemp(STUDENT_FILE, studentLines);
            Path tmpSubjects = writeTemp(SUBJECTS_FILE, subjectLines);
            Path tmpTasks = writeTemp(TASKS_FILE, taskLines);
            Path tmpSessions = writeTemp(SESSIONS_FILE, sessionLines);
            Path tmpAdjustments = writeTemp(ADJUSTMENTS_FILE, adjustmentLines);
            replace(tmpStudent, STUDENT_FILE);
            replace(tmpSubjects, SUBJECTS_FILE);
            replace(tmpTasks, TASKS_FILE);
            replace(tmpSessions, SESSIONS_FILE);
            replace(tmpAdjustments, ADJUSTMENTS_FILE);
        } catch (IOException e) {
            throw new DataStorageException("Could not save data to \"" + directory + "\": " + e.getMessage());
        }
    }

    private Path writeTemp(String fileName, List<String> lines) throws IOException {
        Path tmp = directory.resolve(fileName + ".tmp");
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(tmp.toFile(), StandardCharsets.UTF_8))) {
            for (String line : lines) {
                writer.write(line);
                writer.newLine();
            }
        }                                              // try-with-resources closes the stream
        return tmp;
    }

    /** Keeps the old file as .bak, then moves the new one into place. */
    private void replace(Path tmp, String fileName) throws IOException {
        Path target = directory.resolve(fileName);
        if (Files.exists(target)) Files.copy(target, directory.resolve(fileName + ".bak"), StandardCopyOption.REPLACE_EXISTING);
        try {
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static String oneLine(String text) {
        return text == null ? "" : text.replace('\r', ' ').replace('\n', ' ');
    }

    // =====================================================================
    // LOAD
    // =====================================================================
    /** Never throws for bad data: problems are collected as warnings in the result. */
    public LoadResult load() {
        LoadResult result = new LoadResult();
        if (!hasSavedData()) return result;                         // first run: nothing saved yet

        Student student = loadStudent(result);
        result.setStudent(student);
        StudyPlanner planner = student.getPlanner();

        loadSubjects(planner, result);
        loadTasks(planner, result);
        loadSessions(planner, result);
        loadAdjustments(planner, result);

        planner.refreshAll(LocalDate.now());                        // statuses (Overdue) and priorities are recalculated
        return result;
    }

    private Student loadStudent(LoadResult result) {
        String name = "Student", course = "";
        double hours = -1;
        List<String> lines = readLines(STUDENT_FILE, result);
        if (lines == null) {
            if (!Files.exists(directory.resolve(STUDENT_FILE))) result.warn(STUDENT_FILE + " is missing; using a default student profile.");
        } else {
            boolean problem = false;
            for (String line : lines) {
                int eq = line.indexOf('=');
                if (eq < 0) { if (!line.trim().isEmpty()) problem = true; continue; }
                String key = line.substring(0, eq).trim(), value = line.substring(eq + 1).trim();
                if (key.equals("name")) name = value;
                else if (key.equals("course")) course = value;
                else if (key.equals("dailyAvailableHours")) {
                    try { hours = Double.parseDouble(value); }
                    catch (NumberFormatException e) { result.warn(STUDENT_FILE + ": invalid dailyAvailableHours \"" + value + "\"; using default."); problem = true; }
                }
            }
            if (problem) backupCorrupt(STUDENT_FILE);
        }
        Student student;
        try {
            student = new Student(name, course);
        } catch (InvalidInputException e) {
            result.warn(STUDENT_FILE + ": invalid student name; using \"Student\".");
            try { student = new Student("Student", course); } catch (InvalidInputException impossible) { throw new IllegalStateException(impossible); }
        }
        if (hours >= 0) {
            try { student.getPlanner().setDailyAvailableHours(hours); }
            catch (StudyBuddyException e) { result.warn(STUDENT_FILE + ": " + e.getMessage() + " Using default."); }
        }
        return student;
    }

    private void loadSubjects(StudyPlanner planner, LoadResult result) {
        List<String> lines = readLines(SUBJECTS_FILE, result);
        if (lines == null) return;
        boolean problem = false;
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            if (skip(line, i, SUBJECTS_HEADER)) continue;
            try {
                List<String> f = CsvUtil.parseLine(line);
                need(f, 3);
                int id = Integer.parseInt(f.get(0).trim());
                if (exists(planner, id)) throw new InvalidInputException("duplicate subject ID " + id);
                planner.restoreSubject(new Subject(id, f.get(1), f.get(2)));
                result.countSubject();
            } catch (Exception e) {
                problem = true;
                result.warn(SUBJECTS_FILE + " line " + (i + 1) + " skipped: " + describe(e));
            }
        }
        if (problem) backupCorrupt(SUBJECTS_FILE);
    }

    private void loadTasks(StudyPlanner planner, LoadResult result) {
        List<String> lines = readLines(TASKS_FILE, result);
        if (lines == null) return;
        boolean problem = false;
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            if (skip(line, i, TASKS_HEADER)) continue;
            try {
                List<String> f = CsvUtil.parseLine(line);
                need(f, 9);
                int id = Integer.parseInt(f.get(0).trim());
                int subjectId = Integer.parseInt(f.get(1).trim());
                if (taskExists(planner, id)) throw new InvalidInputException("duplicate task ID " + id);
                if (!exists(planner, subjectId)) throw new InvalidInputException("unknown subject ID " + subjectId);
                StudyItem task = buildTask(id, f);
                planner.restoreTask(subjectId, task);
                result.countTask();
            } catch (Exception e) {
                problem = true;
                result.warn(TASKS_FILE + " line " + (i + 1) + " skipped: " + describe(e));
            }
        }
        if (problem) backupCorrupt(TASKS_FILE);
    }

    private void loadSessions(StudyPlanner planner, LoadResult result) {
        List<String> lines = readLines(SESSIONS_FILE, result);
        if (lines == null) return;
        boolean problem = false;
        Set<Integer> seen = new HashSet<>();
        for (StudySession s : planner.getSessions()) seen.add(s.getId());
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            if (skip(line, i, SESSIONS_HEADER)) continue;
            try {
                List<String> f = CsvUtil.parseLine(line);
                need(f, 4);
                int id = Integer.parseInt(f.get(0).trim());
                int taskId = Integer.parseInt(f.get(1).trim());
                if (!seen.add(id)) throw new InvalidInputException("duplicate session ID " + id);
                StudyItem task = planner.findTask(taskId);
                LocalDate date = LocalDate.parse(f.get(2).trim());
                double hours = Double.parseDouble(f.get(3).trim());
                String notes = f.size() > 4 ? f.get(4) : "";
                planner.restoreSession(new StudySession(id, task, date, hours, notes));
                result.countSession();
            } catch (Exception e) {
                problem = true;
                result.warn(SESSIONS_FILE + " line " + (i + 1) + " skipped: " + describe(e));
            }
        }
        if (problem) backupCorrupt(SESSIONS_FILE);
    }

    private void loadAdjustments(StudyPlanner planner, LoadResult result) {
        List<String> lines = readLines(ADJUSTMENTS_FILE, result);
        if (lines == null) return;                       // older save folders have no such file
        boolean problem = false;
        PlanAdjustments adj = planner.getAdjustments();
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            if (skip(line, i, ADJUSTMENTS_HEADER)) continue;
            try {
                List<String> f = CsvUtil.parseLine(line);
                need(f, 3);
                String kind = f.get(0).trim().toUpperCase();
                if (kind.equals("HOURS")) {
                    adj.setHoursForDate(LocalDate.parse(f.get(1).trim()), Double.parseDouble(f.get(2).trim()));
                } else if (kind.equals("POSTPONE")) {
                    int taskId = Integer.parseInt(f.get(1).trim());
                    if (!taskExists(planner, taskId)) throw new InvalidInputException("unknown task ID " + taskId);
                    adj.postponeTask(taskId, LocalDate.parse(f.get(2).trim()));
                } else if (kind.equals("FOCUS")) {
                    int subjectId = Integer.parseInt(f.get(1).trim());
                    if (!exists(planner, subjectId)) throw new InvalidInputException("unknown subject ID " + subjectId);
                    adj.setFocus(subjectId, Double.parseDouble(f.get(2).trim()));
                } else {
                    throw new InvalidInputException("unknown adjustment type \"" + f.get(0) + "\"");
                }
            } catch (Exception e) {
                problem = true;
                result.warn(ADJUSTMENTS_FILE + " line " + (i + 1) + " skipped: " + describe(e));
            }
        }
        if (problem) backupCorrupt(ADJUSTMENTS_FILE);
    }

    /** Creates the right subclass from one saved row (a simple factory). */
    private StudyItem buildTask(int id, List<String> f) throws StudyBuddyException {
        TaskType type = TaskType.fromString(f.get(2));
        String title = f.get(3);
        LocalDate deadline = LocalDate.parse(f.get(4).trim());
        double estimated = Double.parseDouble(f.get(5).trim());
        double remaining = Double.parseDouble(f.get(6).trim());
        int difficulty = Integer.parseInt(f.get(7).trim());
        TaskStatus status = TaskStatus.fromString(f.get(8));
        String extra = f.size() > 9 ? f.get(9) : "";

        StudyItem task;
        switch (type) {
            case EXAM: {
                List<String> topics = new ArrayList<>();
                for (String t : extra.split(";")) if (!t.trim().isEmpty()) topics.add(t.trim());
                task = new Exam(id, title, deadline, estimated, difficulty, topics);
                break;
            }
            case ASSIGNMENT:
                task = new Assignment(id, title, deadline, estimated, difficulty, extra);
                break;
            case QUIZ:
                task = new Quiz(id, title, deadline, estimated, difficulty, extra);
                break;
            default: {
                int pages = extra.trim().isEmpty() ? 0 : Integer.parseInt(extra.trim());
                task = new ReadingTask(id, title, deadline, estimated, difficulty, pages);
            }
        }
        task.setRemainingHours(remaining);
        task.setStatus(status);
        return task;
    }

    // ----- helpers -----
    /** Returns the file's lines, or null if it is missing/unreadable (a warning is added if it exists but cannot be read). */
    private List<String> readLines(String fileName, LoadResult result) {
        Path file = directory.resolve(fileName);
        if (!Files.exists(file)) return null;
        try {
            List<String> lines = new ArrayList<>();
            try (BufferedReader reader = new BufferedReader(new FileReader(file.toFile(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) lines.add(line);
            }
            return lines;
        } catch (IOException e) {
            result.warn(fileName + " could not be read (" + e.getClass().getSimpleName() + "); its data was ignored.");
            backupCorrupt(fileName);
            return null;
        }
    }

    /** True for blank lines and for the header line. */
    private boolean skip(String line, int index, String header) {
        if (line.trim().isEmpty()) return true;
        return index == 0 && line.trim().equalsIgnoreCase(header);
    }

    private void need(List<String> fields, int count) throws InvalidInputException {
        if (fields.size() < count) throw new InvalidInputException("expected at least " + count + " columns but found " + fields.size());
    }

    private boolean exists(StudyPlanner planner, int subjectId) {
        try { planner.findSubject(subjectId); return true; } catch (StudyBuddyException e) { return false; }
    }

    private boolean taskExists(StudyPlanner planner, int taskId) {
        try { planner.findTask(taskId); return true; } catch (StudyBuddyException e) { return false; }
    }

    private String describe(Exception e) {
        String msg = e.getMessage();
        if (e instanceof NumberFormatException) return "not a valid number (" + msg + ")";
        if (e instanceof java.time.format.DateTimeParseException) return "not a valid date (" + msg + ")";
        return msg == null ? e.getClass().getSimpleName() : msg;
    }

    /** Keeps a copy of a problem file as NAME.corrupt so the original content is never lost. */
    private void backupCorrupt(String fileName) {
        try {
            Path file = directory.resolve(fileName);
            if (Files.exists(file)) Files.copy(file, directory.resolve(fileName + ".corrupt"), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ignored) {
            // best effort only
        }
    }
}
