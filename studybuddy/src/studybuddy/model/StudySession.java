package studybuddy.model;

import studybuddy.exception.InvalidInputException;
import studybuddy.exception.InvalidStudyHoursException;

import java.time.LocalDate;

/** One recorded block of actual studying, linked (association) to the StudyItem it was spent on. */
public class StudySession implements Exportable {
    public static final String CSV_HEADER = "id,taskId,taskTitle,subject,date,hours,notes";
    private final int id;
    private final StudyItem task;
    private final LocalDate date;
    private final double hours;
    private String notes;

    public StudySession(int id, StudyItem task, LocalDate date, double hours, String notes)
            throws InvalidInputException, InvalidStudyHoursException {
        if (task == null) throw new InvalidInputException("A study session needs a task.");
        if (date == null) throw new InvalidInputException("Session date cannot be empty.");
        if (date.isAfter(LocalDate.now())) throw new InvalidInputException("Session date cannot be in the future.");
        StudyItem.checkHours(hours);
        this.id = id;
        this.task = task;
        this.date = date;
        this.hours = hours;
        this.notes = notes == null ? "" : notes.trim();
    }

    /** Overload: no notes. */
    public StudySession(int id, StudyItem task, LocalDate date, double hours)
            throws InvalidInputException, InvalidStudyHoursException {
        this(id, task, date, hours, "");
    }

    public int getId() { return id; }
    public StudyItem getTask() { return task; }
    public Subject getSubject() { return task.getSubject(); }
    public String getSubjectName() { return task.getSubjectName(); }
    public LocalDate getDate() { return date; }
    public double getHours() { return hours; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes == null ? "" : notes.trim(); }

    @Override public String getCsvHeader() { return CSV_HEADER; }

    @Override public String toCsvLine() {
        return id + "," + task.getId() + "," + Exportable.escape(task.getTitle()) + ","
                + Exportable.escape(getSubjectName()) + "," + date + "," + hours + "," + Exportable.escape(notes);
    }

    @Override public String toString() {
        return String.format("[S%d] %s | %-14s | %-24s | %.1fh%s", id, date, getSubjectName(), task.getTitle(),
                hours, notes.isEmpty() ? "" : " | " + notes);
    }
}
