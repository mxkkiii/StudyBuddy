package studybuddy.model;

import studybuddy.exception.InvalidDeadlineException;
import studybuddy.exception.InvalidInputException;
import studybuddy.exception.InvalidStudyHoursException;

import java.time.LocalDate;

public class Quiz extends StudyItem {
    private String coverage = "Not specified";

    public Quiz(int id, String title, LocalDate deadline, double estimatedHours, int difficulty)
            throws InvalidInputException, InvalidDeadlineException, InvalidStudyHoursException {
        super(id, title, deadline, estimatedHours, difficulty);
        recalculatePriority();
    }

    public Quiz(int id, String title, LocalDate deadline, double estimatedHours, int difficulty, String coverage)
            throws InvalidInputException, InvalidDeadlineException, InvalidStudyHoursException {
        this(id, title, deadline, estimatedHours, difficulty);
        setCoverage(coverage);
    }

    private Quiz(Quiz other) {
        super(other);
        this.coverage = other.coverage;
    }

    public String getCoverage() { return coverage; }

    public void setCoverage(String coverage) {
        this.coverage = (coverage == null || coverage.trim().isEmpty()) ? "Not specified" : coverage.trim();
    }

    @Override public TaskType getTaskType() { return TaskType.QUIZ; }

    @Override public double calculatePriority(LocalDate today) {
        return computeScore(TaskType.QUIZ.getWeight(), today);
    }

    @Override public String getDetails() { return "Coverage: " + coverage; }

    @Override public String getExtraData() { return coverage; }

    @Override public StudyItem copy() { return new Quiz(this); }
}
