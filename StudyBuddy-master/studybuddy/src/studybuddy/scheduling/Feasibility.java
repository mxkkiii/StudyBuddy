package studybuddy.scheduling;

/** Whether a task can finish by its deadline in a generated schedule. */
public enum Feasibility {
    /** The schedule finishes the task on or before its deadline. */
    ON_TRACK("On track"),
    /** Finishes late, although enough hours exist before the deadline if other work were moved. */
    DELAYED("Delayed"),
    /** Even all available hours before the deadline are not enough (or the deadline has passed). */
    IMPOSSIBLE("Impossible");

    private final String label;

    Feasibility(String label) { this.label = label; }

    public String getLabel() { return label; }

    @Override public String toString() { return label; }
}
