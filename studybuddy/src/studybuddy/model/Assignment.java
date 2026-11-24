package studybuddy.model;

import studybuddy.exception.InvalidDeadlineException;
import studybuddy.exception.InvalidInputException;
import studybuddy.exception.InvalidStudyHoursException;

import java.time.LocalDate;

public class Assignment extends StudyItem {
    private String submissionFormat = "Not specified";

    public Assignment(int id, String title, LocalDate deadline, double estimatedHours, int difficulty)
            throws InvalidInputException, InvalidDeadlineException, InvalidStudyHoursException {
        super(id, title, deadline, estimatedHours, difficulty);
        recalculatePriority();
    }

    /** Overloaded constructor that also records the submission format (e.g. "PDF", "Online portal"). */
    public Assignment(int id, String title, LocalDate deadline, double estimatedHours, int difficulty, String submissionFormat)
            throws InvalidInputException, InvalidDeadlineException, InvalidStudyHoursException {
        this(id, title, deadline, estimatedHours, difficulty);
        setSubmissionFormat(submissionFormat);
    }

    private Assignment(Assignment other) {
        super(other);
        this.submissionFormat = other.submissionFormat;
    }

    public String getSubmissionFormat() { return submissionFormat; }

    public void setSubmissionFormat(String format) {
        this.submissionFormat = (format == null || format.trim().isEmpty()) ? "Not specified" : format.trim();
    }

    @Override public TaskType getTaskType() { return TaskType.ASSIGNMENT; }

    @Override public double calculatePriority(LocalDate today) {
        return computeScore(TaskType.ASSIGNMENT.getWeight(), today);
    }

    @Override public String getDetails() { return "Submission: " + submissionFormat; }

    @Override public String getExtraData() { return submissionFormat; }

    @Override public StudyItem copy() { return new Assignment(this); }
}
