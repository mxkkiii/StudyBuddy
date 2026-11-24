package studybuddy.model;

import studybuddy.exception.InvalidInputException;

public enum TaskStatus {
    PENDING("Pending"),
    IN_PROGRESS("In Progress"),
    COMPLETED("Completed"),
    OVERDUE("Overdue");

    private final String label;

    TaskStatus(String label) { this.label = label; }

    public String getLabel() { return label; }

    public static TaskStatus fromString(String text) throws InvalidInputException {
        if (text != null) {
            String cleaned = text.trim().toUpperCase().replace(' ', '_');
            for (TaskStatus s : values()) {
                if (s.name().equals(cleaned)) return s;
            }
        }
        throw new InvalidInputException("Unknown task status: " + text);
    }

    @Override public String toString() { return label; }
}
