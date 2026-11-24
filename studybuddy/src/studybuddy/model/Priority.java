package studybuddy.model;

/** A human-friendly label derived from a numeric priority score. */
public enum Priority {
    CRITICAL("Critical"),
    HIGH("High"),
    MEDIUM("Medium"),
    LOW("Low");

    private final String label;

    Priority(String label) { this.label = label; }

    public String getLabel() { return label; }

    /** Overdue tasks always score above OVERDUE_BONUS, so they are CRITICAL. */
    public static Priority fromScore(double score) {
        if (score >= StudyItem.OVERDUE_BONUS) return CRITICAL;
        if (score >= 18) return HIGH;
        if (score >= 11) return MEDIUM;
        return LOW;
    }

    @Override public String toString() { return label; }
}
