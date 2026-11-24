package studybuddy.model;

import studybuddy.exception.InvalidDeadlineException;
import studybuddy.exception.InvalidInputException;
import studybuddy.exception.InvalidStudyHoursException;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Exam extends StudyItem {
    private final List<String> coverageTopics = new ArrayList<>();

    public Exam(int id, String title, LocalDate deadline, double estimatedHours, int difficulty)
            throws InvalidInputException, InvalidDeadlineException, InvalidStudyHoursException {
        super(id, title, deadline, estimatedHours, difficulty);
        recalculatePriority();
    }

    /** Overloaded constructor that also records the coverage topics. */
    public Exam(int id, String title, LocalDate deadline, double estimatedHours, int difficulty, List<String> topics)
            throws InvalidInputException, InvalidDeadlineException, InvalidStudyHoursException {
        this(id, title, deadline, estimatedHours, difficulty);
        if (topics != null) for (String t : topics) addTopic(t);
    }

    private Exam(Exam other) {
        super(other);
        this.coverageTopics.addAll(other.coverageTopics);
    }

    public void addTopic(String topic) {
        if (topic != null && !topic.trim().isEmpty()) coverageTopics.add(topic.trim().replace(';', ','));   // ';' is the separator used when saving
    }

    public List<String> getCoverageTopics() { return Collections.unmodifiableList(coverageTopics); }

    @Override public TaskType getTaskType() { return TaskType.EXAM; }

    @Override public double calculatePriority(LocalDate today) {
        return computeScore(TaskType.EXAM.getWeight(), today);
    }

    @Override public String getDetails() {
        return coverageTopics.isEmpty() ? "Coverage: not specified" : "Coverage: " + String.join(", ", coverageTopics);
    }

    @Override public String getExtraData() { return String.join(";", coverageTopics); }

    @Override public StudyItem copy() { return new Exam(this); }
}
