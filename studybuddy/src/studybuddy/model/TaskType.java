package studybuddy.model;

import studybuddy.exception.InvalidInputException;

/** The kinds of study items and the weight each one uses in the priority formula. */
public enum TaskType {
    EXAM(5, "Exam"),
    ASSIGNMENT(4, "Assignment"),
    QUIZ(3, "Quiz"),
    READING_TASK(1, "Reading Task");

    private final int weight;
    private final String label;

    TaskType(int weight, String label) {
        this.weight = weight;
        this.label = label;
    }

    public int getWeight() { return weight; }
    public String getLabel() { return label; }

    /** Converts text such as "exam", "Reading Task" or "READING_TASK" into a TaskType. */
    public static TaskType fromString(String text) throws InvalidInputException {
        if (text != null) {
            String cleaned = text.trim().toUpperCase().replace(' ', '_');
            for (TaskType t : values()) {
                if (t.name().equals(cleaned)) return t;
            }
        }
        throw new InvalidInputException("Unknown task type: " + text);
    }

    @Override public String toString() { return label; }
}
