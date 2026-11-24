package studybuddy.simulation;

public enum ScenarioType {
    SKIP_DAY("Skip a study day"),
    REDUCE_HOURS("Reduce available study hours"),
    POSTPONE_TASK("Postpone a task"),
    PRIORITIZE_SUBJECT("Prioritize another subject"),
    INCREASE_HOURS("Increase study hours");

    private final String label;

    ScenarioType(String label) { this.label = label; }

    public String getLabel() { return label; }

    @Override public String toString() { return label; }
}
