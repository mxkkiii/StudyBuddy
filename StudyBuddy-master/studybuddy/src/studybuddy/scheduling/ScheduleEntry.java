package studybuddy.scheduling;

import studybuddy.model.Exportable;
import studybuddy.model.StudyItem;

import java.time.LocalDate;

/** One line of a schedule: "study THIS task for X hours on THIS date". An immutable snapshot. */
public class ScheduleEntry implements Exportable {
    public static final String CSV_HEADER = "date,taskId,taskTitle,subject,hours,deadline,late";
    private final LocalDate date;
    private final int taskId;
    private final String taskTitle;
    private final String subjectName;
    private final LocalDate deadline;
    private final double hours;

    public ScheduleEntry(LocalDate date, StudyItem task, double hours) {
        this.date = date;
        this.taskId = task.getId();
        this.taskTitle = task.getTitle();
        this.subjectName = task.getSubjectName();
        this.deadline = task.getDeadline();
        this.hours = hours;
    }

    public LocalDate getDate() { return date; }
    public int getTaskId() { return taskId; }
    public String getTaskTitle() { return taskTitle; }
    public String getSubjectName() { return subjectName; }
    public LocalDate getDeadline() { return deadline; }
    public double getHours() { return hours; }

    /** True when this study block falls after the task's deadline. */
    public boolean isLate() { return date.isAfter(deadline); }

    @Override public String getCsvHeader() { return CSV_HEADER; }

    @Override public String toCsvLine() {
        return date + "," + taskId + "," + Exportable.escape(taskTitle) + "," + Exportable.escape(subjectName)
                + "," + hours + "," + deadline + "," + isLate();
    }

    @Override public String toString() {
        return String.format("%.1fh  [#%d] %s (%s) - due %s%s", hours, taskId, taskTitle, subjectName, deadline,
                isLate() ? "  ** AFTER DEADLINE **" : "");
    }
}
