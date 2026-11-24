package studybuddy.model;

import studybuddy.exception.*;

import java.time.LocalDate;
import java.util.*;

/**
 * Central manager of subjects, tasks and study sessions (the "backend facade" a GUI will talk to).
 * It owns the Subjects (composition); each Subject owns its StudyItems.
 */
public class StudyPlanner {
    private final Map<Integer, Subject> subjects = new LinkedHashMap<>();          // by ID (keeps creation order)
    private final Map<String, Subject> subjectsByName = new HashMap<>();           // by lower-case name: fast lookup
    private final List<StudySession> sessions = new ArrayList<>();
    private double dailyAvailableHours = 4.0;
    private final PlanAdjustments adjustments = new PlanAdjustments();
    private int nextSubjectId = 1;
    private int nextTaskId = 1;
    private int nextSessionId = 1;

    // =====================================================================
    // Settings
    // =====================================================================
    public double getDailyAvailableHours() { return dailyAvailableHours; }

    /** The student's REAL plan adjustments (skipped days, postponed tasks, focus subject). */
    public PlanAdjustments getAdjustments() { return adjustments; }

    public void setDailyAvailableHours(double hours) throws InvalidStudyHoursException {
        if (Double.isNaN(hours) || hours < 0 || hours > 24)
            throw new InvalidStudyHoursException("Daily study hours must be between 0 and 24.");
        this.dailyAvailableHours = hours;
    }

    // =====================================================================
    // Subjects
    // =====================================================================
    public Subject addSubject(String name, String description) throws InvalidInputException {
        Subject.checkName(name);
        ensureNameIsFree(name, -1);
        Subject s = new Subject(nextSubjectId++, name, description);
        subjects.put(s.getId(), s);
        subjectsByName.put(nameKey(s.getName()), s);
        return s;
    }

    /** Overload: no description. */
    public Subject addSubject(String name) throws InvalidInputException { return addSubject(name, ""); }

    public List<Subject> getAllSubjects() { return new ArrayList<>(subjects.values()); }

    public Subject findSubject(int subjectId) throws SubjectNotFoundException {
        Subject s = subjects.get(subjectId);
        if (s == null) throw new SubjectNotFoundException(subjectId);
        return s;
    }

    /** Overload: exact name lookup (case-insensitive). */
    public Subject findSubject(String name) throws SubjectNotFoundException {
        if (name != null) {
            Subject s = subjectsByName.get(nameKey(name));
            if (s != null) return s;
        }
        throw new SubjectNotFoundException("Subject \"" + name + "\" was not found.");
    }

    /** Keyword search in name and description. */
    public List<Subject> searchSubjects(String keyword) throws InvalidInputException {
        String k = requireKeyword(keyword);
        List<Subject> result = new ArrayList<>();
        for (Subject s : subjects.values()) {
            if (s.getName().toLowerCase().contains(k) || s.getDescription().toLowerCase().contains(k)) result.add(s);
        }
        return result;
    }

    public void editSubject(int subjectId, String newName, String newDescription)
            throws SubjectNotFoundException, InvalidInputException {
        Subject s = findSubject(subjectId);
        if (newName != null) {
            Subject.checkName(newName);
            ensureNameIsFree(newName, subjectId);
        }
        if (newName != null) {
            subjectsByName.remove(nameKey(s.getName()));
            s.setName(newName);
            subjectsByName.put(nameKey(s.getName()), s);
        }
        if (newDescription != null) s.setDescription(newDescription);
    }

    /**
     * Deletes the subject, ALL of its tasks and every study session logged for those tasks
     * (sessions cannot exist without their task). Returns how many tasks were removed.
     */
    public int deleteSubject(int subjectId) throws SubjectNotFoundException {
        Subject s = findSubject(subjectId);
        List<StudyItem> removed = s.clearTasks();
        Set<Integer> removedIds = new HashSet<>();
        for (StudyItem t : removed) { removedIds.add(t.getId()); adjustments.clearPostponement(t.getId()); }
        if (Integer.valueOf(subjectId).equals(adjustments.getFocusSubjectId())) adjustments.clearFocus();
        sessions.removeIf(x -> removedIds.contains(x.getTask().getId()));
        subjects.remove(subjectId);
        subjectsByName.remove(nameKey(s.getName()));
        return removed.size();
    }

    private void ensureNameIsFree(String name, int ignoreId) throws InvalidInputException {
        Subject existing = subjectsByName.get(nameKey(name));
        if (existing != null && existing.getId() != ignoreId)
            throw new InvalidInputException("A subject named \"" + name.trim() + "\" already exists.");
    }

    private static String nameKey(String name) { return name.trim().toLowerCase(); }

    // =====================================================================
    // Tasks: creation (one method per type; all validate BEFORE using an ID)
    // =====================================================================
    public Assignment addAssignment(int subjectId, String title, LocalDate deadline, double hours,
                                    int difficulty, String submissionFormat) throws StudyBuddyException {
        Subject s = findSubject(subjectId);
        validateNewTask(title, deadline, hours, difficulty);
        Assignment a = new Assignment(nextTaskId, title, deadline, hours, difficulty, submissionFormat);
        return register(s, a);
    }

    /** Overload: no submission format. */
    public Assignment addAssignment(int subjectId, String title, LocalDate deadline, double hours, int difficulty)
            throws StudyBuddyException {
        return addAssignment(subjectId, title, deadline, hours, difficulty, null);
    }

    public Quiz addQuiz(int subjectId, String title, LocalDate deadline, double hours, int difficulty,
                        String coverage) throws StudyBuddyException {
        Subject s = findSubject(subjectId);
        validateNewTask(title, deadline, hours, difficulty);
        Quiz q = new Quiz(nextTaskId, title, deadline, hours, difficulty, coverage);
        return register(s, q);
    }

    public Exam addExam(int subjectId, String title, LocalDate deadline, double hours, int difficulty,
                        List<String> topics) throws StudyBuddyException {
        Subject s = findSubject(subjectId);
        validateNewTask(title, deadline, hours, difficulty);
        Exam e = new Exam(nextTaskId, title, deadline, hours, difficulty, topics);
        return register(s, e);
    }

    public ReadingTask addReadingTask(int subjectId, String title, LocalDate deadline, double hours,
                                      int difficulty, int pages) throws StudyBuddyException {
        Subject s = findSubject(subjectId);
        validateNewTask(title, deadline, hours, difficulty);
        ReadingTask r = new ReadingTask(nextTaskId, title, deadline, hours, difficulty, pages);
        return register(s, r);
    }

    // ----- addTask(): one method, three overloads (type chosen with the TaskType enum) -----
    public static final double DEFAULT_ESTIMATED_HOURS = 1.0;
    public static final int DEFAULT_DIFFICULTY = 3;

    /** Title and deadline only: 1 hour estimated, difficulty 3. */
    public StudyItem addTask(int subjectId, TaskType type, String title, LocalDate deadline) throws StudyBuddyException {
        return addTask(subjectId, type, title, deadline, DEFAULT_ESTIMATED_HOURS);
    }

    /** With estimated study hours (difficulty 3). */
    public StudyItem addTask(int subjectId, TaskType type, String title, LocalDate deadline, double estimatedHours)
            throws StudyBuddyException {
        return addTask(subjectId, type, title, deadline, estimatedHours, DEFAULT_DIFFICULTY);
    }

    /**
     * With estimated hours and difficulty (1-5). The priority itself is never typed in: it is calculated from the
     * task type, the deadline and this difficulty.
     */
    public StudyItem addTask(int subjectId, TaskType type, String title, LocalDate deadline, double estimatedHours,
                             int difficulty) throws StudyBuddyException {
        if (type == null) throw new InvalidInputException("Task type is required.");
        switch (type) {
            case EXAM: return addExam(subjectId, title, deadline, estimatedHours, difficulty, null);
            case ASSIGNMENT: return addAssignment(subjectId, title, deadline, estimatedHours, difficulty);
            case QUIZ: return addQuiz(subjectId, title, deadline, estimatedHours, difficulty, null);
            default: return addReadingTask(subjectId, title, deadline, estimatedHours, difficulty, 0);
        }
    }

    private void validateNewTask(String title, LocalDate deadline, double hours, int difficulty)
            throws StudyBuddyException {
        StudyItem.checkTitle(title);
        StudyItem.checkDeadline(deadline, false);   // NEW tasks may not have a past deadline
        StudyItem.checkHours(hours);
        StudyItem.checkDifficulty(difficulty);
    }

    private <T extends StudyItem> T register(Subject subject, T task) throws InvalidInputException {
        subject.addTask(task);
        nextTaskId++;
        task.recalculatePriority();
        return task;
    }

    // =====================================================================
    // Tasks: lookup, edit, delete, status
    // =====================================================================
    public List<StudyItem> getAllTasks() {
        List<StudyItem> all = new ArrayList<>();
        for (Subject s : subjects.values()) all.addAll(s.getTasks());
        return all;
    }

    public StudyItem findTask(int taskId) throws TaskNotFoundException {
        for (Subject s : subjects.values()) {
            for (StudyItem t : s.getTasks()) if (t.getId() == taskId) return t;
        }
        throw new TaskNotFoundException(taskId);
    }

    /** Keyword search in title, type, subject name and details. */
    public List<StudyItem> searchTasks(String keyword) throws InvalidInputException {
        String k = requireKeyword(keyword);
        List<StudyItem> result = new ArrayList<>();
        for (StudyItem t : getAllTasks()) {
            String haystack = (t.getTitle() + " " + t.getTaskType().getLabel() + " " + t.getSubjectName()
                    + " " + t.getDetails()).toLowerCase();
            if (haystack.contains(k)) result.add(t);
        }
        return result;
    }

    public List<StudyItem> getTasksBySubject(int subjectId) throws SubjectNotFoundException {
        return new ArrayList<>(findSubject(subjectId).getTasks());
    }

    /**
     * Edits a task. A null argument means "leave unchanged". Everything is validated first,
     * so a bad value never leaves the task half-edited.
     */
    public StudyItem editTask(int taskId, String title, LocalDate deadline, Double estimatedHours, Integer difficulty)
            throws StudyBuddyException {
        StudyItem t = findTask(taskId);
        boolean deadlineChanged = deadline != null && !deadline.equals(t.getDeadline());
        if (title != null) StudyItem.checkTitle(title);
        if (deadlineChanged) StudyItem.checkDeadline(deadline, false);
        if (estimatedHours != null) StudyItem.checkHours(estimatedHours);
        if (difficulty != null) StudyItem.checkDifficulty(difficulty);

        if (title != null) t.setTitle(title);
        if (deadlineChanged) t.setDeadline(deadline);
        if (estimatedHours != null) t.setEstimatedHours(estimatedHours);
        if (difficulty != null) t.setDifficulty(difficulty);
        t.refreshStatus(LocalDate.now());
        t.recalculatePriority();
        return t;
    }

    /** Deletes a task and the study sessions logged for it. */
    public void deleteTask(int taskId) throws TaskNotFoundException {
        StudyItem t = findTask(taskId);
        t.getSubject().removeTask(taskId);
        adjustments.clearPostponement(taskId);
        sessions.removeIf(x -> x.getTask().getId() == taskId);
    }

    public void markTaskCompleted(int taskId) throws TaskNotFoundException {
        findTask(taskId).markCompleted();
    }

    public void updateTaskStatus(int taskId, TaskStatus status) throws StudyBuddyException {
        findTask(taskId).setStatus(status);
    }

    // =====================================================================
    // Priority / deadline views
    // =====================================================================
    /** Refreshes OVERDUE status and recalculates every priority for the given date. */
    public void refreshAll(LocalDate today) {
        for (StudyItem t : getAllTasks()) {
            t.refreshStatus(today);
            t.recalculatePriority(today);
        }
    }

    public void refreshAll() { refreshAll(LocalDate.now()); }

    /** All tasks from highest to lowest priority, using a PriorityQueue. */
    public List<StudyItem> getTasksByPriority(LocalDate today) {
        refreshAll(today);
        PriorityQueue<StudyItem> queue = new PriorityQueue<>(getAllTasks());
        List<StudyItem> ordered = new ArrayList<>();
        while (!queue.isEmpty()) ordered.add(queue.poll());
        return ordered;
    }

    public List<StudyItem> getTasksByPriority() { return getTasksByPriority(LocalDate.now()); }

    /** All tasks, earliest deadline first. */
    public List<StudyItem> getTasksByDeadline() {
        List<StudyItem> list = getAllTasks();
        list.sort(Comparator.comparing(StudyItem::getDeadline).thenComparingInt(StudyItem::getId));
        return list;
    }

    // ----- deadline alert list -----
    public static final int DEFAULT_ALERT_DAYS = 3;

    /** Unfinished tasks that are overdue, due today, or due within the next 'withinDays' days (soonest first). */
    public List<StudyItem> getDeadlineAlerts(LocalDate today, int withinDays) {
        List<StudyItem> list = new ArrayList<>();
        for (StudyItem t : getAllTasks()) if (t.isUnfinished() && t.getDaysLeft(today) <= Math.max(0, withinDays)) list.add(t);
        list.sort(Comparator.comparing(StudyItem::getDeadline).thenComparingInt(StudyItem::getId));
        return list;
    }

    /** Overload: the default alert window (3 days). */
    public List<StudyItem> getDeadlineAlerts(LocalDate today) { return getDeadlineAlerts(today, DEFAULT_ALERT_DAYS); }

    /** Overload: today's date. */
    public List<StudyItem> getDeadlineAlerts() { return getDeadlineAlerts(LocalDate.now()); }

    public List<StudyItem> getUnfinishedTasks() {
        List<StudyItem> list = new ArrayList<>();
        for (StudyItem t : getAllTasks()) if (t.isUnfinished()) list.add(t);
        return list;
    }

    public List<StudyItem> getCompletedTasks() {
        List<StudyItem> list = new ArrayList<>();
        for (StudyItem t : getAllTasks()) if (!t.isUnfinished()) list.add(t);
        return list;
    }

    public List<StudyItem> getOverdueTasks(LocalDate today) {
        List<StudyItem> list = new ArrayList<>();
        for (StudyItem t : getAllTasks()) if (t.isOverdue(today)) list.add(t);
        return list;
    }

    /** Pending = unfinished and not yet overdue. */
    public List<StudyItem> getPendingTasks(LocalDate today) {
        List<StudyItem> list = new ArrayList<>();
        for (StudyItem t : getAllTasks()) if (t.isUnfinished() && !t.isOverdue(today)) list.add(t);
        return list;
    }

    // =====================================================================
    // Study sessions
    // =====================================================================
    /** Records a session AND reduces the task's remaining hours. */
    public StudySession logSession(int taskId, LocalDate date, double hours, String notes) throws StudyBuddyException {
        StudyItem task = findTask(taskId);
        if (!task.isUnfinished())
            throw new InvalidInputException("Task \"" + task.getTitle() + "\" is already completed.");
        StudySession session = new StudySession(nextSessionId, task, date, hours, notes);  // validates everything first
        task.logHours(hours);
        nextSessionId++;
        sessions.add(session);
        return session;
    }

    /** Overload: session happened today. */
    public StudySession logSession(int taskId, double hours, String notes) throws StudyBuddyException {
        return logSession(taskId, LocalDate.now(), hours, notes);
    }

    public List<StudySession> getSessions() { return Collections.unmodifiableList(sessions); }

    public List<StudySession> getSessionsByDate(LocalDate date) {
        List<StudySession> list = new ArrayList<>();
        for (StudySession s : sessions) if (s.getDate().equals(date)) list.add(s);
        return list;
    }

    public List<StudySession> getSessionsBySubject(int subjectId) throws SubjectNotFoundException {
        findSubject(subjectId);
        List<StudySession> list = new ArrayList<>();
        for (StudySession s : sessions) {
            if (s.getSubject() != null && s.getSubject().getId() == subjectId) list.add(s);
        }
        return list;
    }

    /** Sessions from start to end, both inclusive. */
    public List<StudySession> getSessionsBetween(LocalDate start, LocalDate end) {
        List<StudySession> list = new ArrayList<>();
        for (StudySession s : sessions) {
            if (!s.getDate().isBefore(start) && !s.getDate().isAfter(end)) list.add(s);
        }
        return list;
    }

    public double getTotalStudyHours() { return sum(sessions); }

    /** Overload: only a date range (inclusive). */
    public double getTotalStudyHours(LocalDate start, LocalDate end) { return sum(getSessionsBetween(start, end)); }

    /** Hours per subject name; subjects with no sessions show 0. */
    public Map<String, Double> getStudyHoursPerSubject() { return hoursPerSubject(sessions); }

    public Map<String, Double> getStudyHoursPerSubject(LocalDate start, LocalDate end) {
        return hoursPerSubject(getSessionsBetween(start, end));
    }

    private Map<String, Double> hoursPerSubject(List<StudySession> list) {
        Map<String, Double> map = new LinkedHashMap<>();
        for (Subject s : subjects.values()) map.put(s.getName(), 0.0);
        for (StudySession s : list) map.merge(s.getSubjectName(), s.getHours(), Double::sum);
        return map;
    }

    private double sum(List<StudySession> list) {
        double total = 0;
        for (StudySession s : list) total += s.getHours();
        return total;
    }

    // =====================================================================
    // Loading support (used by FileManager in a later section)
    // =====================================================================
    public void restoreSubject(Subject subject) {
        subjects.put(subject.getId(), subject);
        subjectsByName.put(nameKey(subject.getName()), subject);
        nextSubjectId = Math.max(nextSubjectId, subject.getId() + 1);
    }

    public void restoreTask(int subjectId, StudyItem task) throws StudyBuddyException {
        findSubject(subjectId).addTask(task);
        nextTaskId = Math.max(nextTaskId, task.getId() + 1);
    }

    public void restoreSession(StudySession session) {
        sessions.add(session);
        nextSessionId = Math.max(nextSessionId, session.getId() + 1);
    }

    // =====================================================================
    private String requireKeyword(String keyword) throws InvalidInputException {
        if (keyword == null || keyword.trim().isEmpty()) throw new InvalidInputException("Search keyword cannot be empty.");
        return keyword.trim().toLowerCase();
    }
}
