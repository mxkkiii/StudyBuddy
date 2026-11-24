package studybuddy.scheduling;

import java.time.LocalDate;

/** The scheduler's verdict for one task: will it be finished by its deadline? (immutable) */
public class TaskForecast {
    private final int taskId;
    private final String title;
    private final String subjectName;
    private final LocalDate deadline;
    private final double requiredHours;
    private final double hoursOnTime;
    private final double hoursLate;
    private final double hoursUnscheduled;
    private final LocalDate completionDate;      // null if the task is not fully scheduled
    private final Feasibility feasibility;
    private final String message;

    public TaskForecast(int taskId, String title, String subjectName, LocalDate deadline, double requiredHours,
                        double hoursOnTime, double hoursLate, double hoursUnscheduled, LocalDate completionDate,
                        Feasibility feasibility, String message) {
        this.taskId = taskId;
        this.title = title;
        this.subjectName = subjectName;
        this.deadline = deadline;
        this.requiredHours = requiredHours;
        this.hoursOnTime = hoursOnTime;
        this.hoursLate = hoursLate;
        this.hoursUnscheduled = hoursUnscheduled;
        this.completionDate = completionDate;
        this.feasibility = feasibility;
        this.message = message;
    }

    public int getTaskId() { return taskId; }
    public String getTitle() { return title; }
    public String getSubjectName() { return subjectName; }
    public LocalDate getDeadline() { return deadline; }
    public double getRequiredHours() { return requiredHours; }
    public double getHoursOnTime() { return hoursOnTime; }
    public double getHoursLate() { return hoursLate; }
    public double getHoursUnscheduled() { return hoursUnscheduled; }
    public LocalDate getCompletionDate() { return completionDate; }
    public Feasibility getFeasibility() { return feasibility; }
    public String getMessage() { return message; }
    public boolean isOnTrack() { return feasibility == Feasibility.ON_TRACK; }

    @Override public String toString() {
        return String.format("[#%d] %s: %s", taskId, title, feasibility.getLabel());
    }
}
