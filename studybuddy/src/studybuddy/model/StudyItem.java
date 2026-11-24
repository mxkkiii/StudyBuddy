package studybuddy.model;

import studybuddy.exception.InvalidDeadlineException;
import studybuddy.exception.InvalidInputException;
import studybuddy.exception.InvalidStudyHoursException;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * Abstract parent of every academic task.
 * Priority Score = (Task Type Weight x 2) + Urgency + Difficulty, where
 * Urgency = max(0, 10 - DaysLeft). An unfinished task with DaysLeft <= 0 (due today or already late) also gets
 * OVERDUE_BONUS, so it always ranks first. (Status becomes OVERDUE only once the deadline day has passed.)
 */
public abstract class StudyItem implements Schedulable, Exportable, Comparable<StudyItem> {
    public static final String CSV_HEADER = "id,type,title,subject,deadline,estimatedHours,remainingHours,difficulty,priorityScore,status,details";

    public static final double MAX_HOURS = 200.0;
    public static final double OVERDUE_BONUS = 100.0;
    public static final int MIN_DIFFICULTY = 1;
    public static final int MAX_DIFFICULTY = 5;

    // ----- encapsulated state -----
    private final int id;
    private String title;
    private Subject subject;          // set by Subject.addTask(...)
    private LocalDate deadline;
    private double estimatedHours;
    private double remainingHours;
    private int difficulty;
    private double priorityScore;
    private TaskStatus status;
    private final boolean simulationCopy;   // true for objects made by copy(); they can never join the real plan

    protected StudyItem(int id, String title, LocalDate deadline, double estimatedHours, int difficulty)
            throws InvalidInputException, InvalidDeadlineException, InvalidStudyHoursException {
        checkTitle(title);
        checkDeadline(deadline, true);     // past dates allowed so overdue tasks can be loaded from file
        checkHours(estimatedHours);
        checkDifficulty(difficulty);
        this.id = id;
        this.simulationCopy = false;
        this.title = title.trim();
        this.deadline = deadline;
        this.estimatedHours = estimatedHours;
        this.remainingHours = estimatedHours;
        this.difficulty = difficulty;
        this.status = TaskStatus.PENDING;
    }

    /** Copy constructor used by copy() so What-If simulations never touch the real task. */
    protected StudyItem(StudyItem other) {
        this.id = other.id;
        this.simulationCopy = true;
        this.title = other.title;
        this.subject = other.subject;   // shared read-only reference; the copy is NOT added to the subject's list
        this.deadline = other.deadline;
        this.estimatedHours = other.estimatedHours;
        this.remainingHours = other.remainingHours;
        this.difficulty = other.difficulty;
        this.priorityScore = other.priorityScore;
        this.status = other.status;
    }

    // ----- validation helpers (public static so the planner can validate BEFORE creating a task) -----
    public static void checkTitle(String title) throws InvalidInputException {
        if (title == null || title.trim().isEmpty()) throw new InvalidInputException("Task title cannot be empty.");
    }

    public static void checkDeadline(LocalDate deadline, boolean allowPast) throws InvalidDeadlineException {
        if (deadline == null) throw new InvalidDeadlineException("Deadline cannot be empty.");
        if (!allowPast && deadline.isBefore(LocalDate.now()))
            throw new InvalidDeadlineException("Deadline " + deadline + " is already in the past.");
    }

    public static void checkHours(double hours) throws InvalidStudyHoursException {
        if (Double.isNaN(hours) || hours <= 0)
            throw new InvalidStudyHoursException("Study hours must be greater than 0.");
        if (hours > MAX_HOURS)
            throw new InvalidStudyHoursException("Study hours cannot exceed " + MAX_HOURS + ".");
    }

    public static void checkDifficulty(int difficulty) throws InvalidInputException {
        if (difficulty < MIN_DIFFICULTY || difficulty > MAX_DIFFICULTY)
            throw new InvalidInputException("Difficulty must be between " + MIN_DIFFICULTY + " and " + MAX_DIFFICULTY + ".");
    }

    // ----- abstract methods every subclass must supply -----
    public abstract TaskType getTaskType();
    /** Priority score for the given "today". Each subclass overrides this with its own type weight. */
    public abstract double calculatePriority(LocalDate today);
    /** Human-readable type-specific info (coverage topics, submission format, ...). */
    public abstract String getDetails();
    /** Raw type-specific data used when saving to file. */
    public abstract String getExtraData();
    /** Independent deep copy. */
    public abstract StudyItem copy();

    // ----- priority logic -----
    /** Overload: uses the real current date. */
    public double calculatePriority() { return calculatePriority(LocalDate.now()); }

    /** Shared formula. Subclasses pass in their own weight. */
    protected double computeScore(int typeWeight, LocalDate today) {
        double urgency = Math.max(0, 10 - getDaysLeft(today));
        double score = (typeWeight * 2) + urgency + difficulty;
        if (isUnfinished() && getDaysLeft(today) <= 0) score += OVERDUE_BONUS;   // due today or already late
        return score;
    }

    public void recalculatePriority(LocalDate today) { this.priorityScore = calculatePriority(today); }
    public void recalculatePriority() { recalculatePriority(LocalDate.now()); }

    public Priority getPriorityLevel() { return Priority.fromScore(priorityScore); }

    // ----- date / status helpers -----
    public long getDaysLeft(LocalDate today) { return ChronoUnit.DAYS.between(today, deadline); }

    /** Short text for the deadline alert list, e.g. "OVERDUE by 2 days", "DUE TODAY", "due in 3 days". */
    public String getDeadlineLabel(LocalDate today) {
        long d = getDaysLeft(today);
        if (d < 0) return "OVERDUE by " + (-d) + (d == -1 ? " day" : " days");
        if (d == 0) return "DUE TODAY";
        if (d == 1) return "due tomorrow";
        return "due in " + d + " days";
    }

    public boolean isOverdue(LocalDate today) {
        return status != TaskStatus.COMPLETED && deadline.isBefore(today);
    }

    /** Moves status to/from OVERDUE depending on the date. Completed tasks never change. */
    public void refreshStatus(LocalDate today) {
        if (status == TaskStatus.COMPLETED) return;
        if (deadline.isBefore(today)) {
            status = TaskStatus.OVERDUE;
        } else if (status == TaskStatus.OVERDUE) {
            status = (remainingHours < estimatedHours) ? TaskStatus.IN_PROGRESS : TaskStatus.PENDING;
        }
    }

    // ----- study progress -----
    /** Subtracts studied hours from the remaining hours (never below 0). */
    public void logHours(double hours) throws InvalidStudyHoursException {
        checkHours(hours);
        remainingHours = Math.max(0, remainingHours - hours);
        if (status == TaskStatus.PENDING) status = TaskStatus.IN_PROGRESS;
    }

    public void markCompleted() {
        status = TaskStatus.COMPLETED;
        remainingHours = 0;
    }

    // ----- Schedulable -----
    @Override public int getId() { return id; }
    @Override public String getTitle() { return title; }
    @Override public LocalDate getDeadline() { return deadline; }
    @Override public double getRemainingHours() { return remainingHours; }
    @Override public double getPriorityScore() { return priorityScore; }
    @Override public boolean isUnfinished() { return status != TaskStatus.COMPLETED; }

    // ----- getters / setters with validation -----
    /** True if this object is a temporary copy (scheduler / What-If simulation), never real student data. */
    public boolean isSimulationCopy() { return simulationCopy; }
    public Subject getSubject() { return subject; }
    void setSubject(Subject subject) { this.subject = subject; }   // package-private: only Subject manages this link
    public String getSubjectName() { return subject == null ? "(no subject)" : subject.getName(); }
    @Override public double getEstimatedHours() { return estimatedHours; }
    public int getDifficulty() { return difficulty; }
    public TaskStatus getStatus() { return status; }

    public void setTitle(String title) throws InvalidInputException {
        checkTitle(title);
        this.title = title.trim();
    }

    /** Strict: a new/edited deadline cannot be in the past. Recalculates priority. */
    public void setDeadline(LocalDate deadline) throws InvalidDeadlineException {
        setDeadline(deadline, false);
    }

    /** Overload that can allow past dates (used when loading saved data or in tests). */
    public void setDeadline(LocalDate deadline, boolean allowPast) throws InvalidDeadlineException {
        checkDeadline(deadline, allowPast);
        this.deadline = deadline;
        recalculatePriority();
    }

    /** Changing the estimate shifts the remaining hours by the same amount (never below 0). */
    public void setEstimatedHours(double newEstimate) throws InvalidStudyHoursException {
        checkHours(newEstimate);
        remainingHours = Math.max(0, remainingHours + (newEstimate - estimatedHours));
        estimatedHours = newEstimate;
    }

    /** Remaining hours may exceed the estimate (the Pessimistic simulation relies on this). */
    public void setRemainingHours(double hours) throws InvalidStudyHoursException {
        if (Double.isNaN(hours) || hours < 0 || hours > MAX_HOURS)
            throw new InvalidStudyHoursException("Remaining hours must be between 0 and " + MAX_HOURS + ".");
        this.remainingHours = hours;
    }

    public void setDifficulty(int difficulty) throws InvalidInputException {
        checkDifficulty(difficulty);
        this.difficulty = difficulty;
        recalculatePriority();
    }

    public void setStatus(TaskStatus status) throws InvalidInputException {
        if (status == null) throw new InvalidInputException("Status cannot be empty.");
        if (status == TaskStatus.COMPLETED) markCompleted();
        else this.status = status;
    }

    // ----- Exportable -----
    @Override public String getCsvHeader() { return CSV_HEADER; }

    @Override public String toCsvLine() {
        return id + "," + getTaskType().name() + "," + Exportable.escape(title) + ","
                + Exportable.escape(getSubjectName()) + "," + deadline + "," + estimatedHours + ","
                + remainingHours + "," + difficulty + "," + priorityScore + "," + status.name() + ","
                + Exportable.escape(getDetails());
    }

    // ----- Comparable: highest priority first, then earliest deadline, then lowest id -----
    @Override public int compareTo(StudyItem other) {
        int c = Double.compare(other.priorityScore, this.priorityScore);
        if (c != 0) return c;
        c = this.deadline.compareTo(other.deadline);
        if (c != 0) return c;
        return Integer.compare(this.id, other.id);
    }

    @Override public String toString() {
        return String.format("[#%d] %-12s %-28s %-14s due %s | %.1fh left | diff %d | score %.0f (%s) | %s",
                id, getTaskType().getLabel(), title, getSubjectName(), deadline, remainingHours,
                difficulty, priorityScore, getPriorityLevel().getLabel(), status.getLabel());
    }
}
