package studybuddy.model;

import studybuddy.exception.InvalidDeadlineException;
import studybuddy.exception.InvalidInputException;
import studybuddy.exception.InvalidStudyHoursException;

import java.time.LocalDate;

public class ReadingTask extends StudyItem {
    private int pagesToRead = 0;

    public ReadingTask(int id, String title, LocalDate deadline, double estimatedHours, int difficulty)
            throws InvalidInputException, InvalidDeadlineException, InvalidStudyHoursException {
        super(id, title, deadline, estimatedHours, difficulty);
        recalculatePriority();
    }

    public ReadingTask(int id, String title, LocalDate deadline, double estimatedHours, int difficulty, int pagesToRead)
            throws InvalidInputException, InvalidDeadlineException, InvalidStudyHoursException {
        this(id, title, deadline, estimatedHours, difficulty);
        setPagesToRead(pagesToRead);
    }

    private ReadingTask(ReadingTask other) {
        super(other);
        this.pagesToRead = other.pagesToRead;
    }

    public int getPagesToRead() { return pagesToRead; }

    public void setPagesToRead(int pages) throws InvalidInputException {
        if (pages < 0) throw new InvalidInputException("Pages to read cannot be negative.");
        this.pagesToRead = pages;
    }

    @Override public TaskType getTaskType() { return TaskType.READING_TASK; }

    @Override public double calculatePriority(LocalDate today) {
        return computeScore(TaskType.READING_TASK.getWeight(), today);
    }

    @Override public String getDetails() { return pagesToRead == 0 ? "Pages: not specified" : "Pages: " + pagesToRead; }

    @Override public String getExtraData() { return String.valueOf(pagesToRead); }

    @Override public StudyItem copy() { return new ReadingTask(this); }
}
