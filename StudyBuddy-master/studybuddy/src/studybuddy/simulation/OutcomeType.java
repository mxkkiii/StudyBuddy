package studybuddy.simulation;

/** The three duration assumptions every simulation is run under. */
public enum OutcomeType {
    OPTIMISTIC("Optimistic", "tasks take less time than estimated"),
    EXPECTED("Expected", "tasks take their estimated or historically typical time"),
    PESSIMISTIC("Pessimistic", "tasks take longer than estimated");

    private final String label;
    private final String meaning;

    OutcomeType(String label, String meaning) { this.label = label; this.meaning = meaning; }

    public String getLabel() { return label; }
    public String getMeaning() { return meaning; }

    @Override public String toString() { return label; }
}
