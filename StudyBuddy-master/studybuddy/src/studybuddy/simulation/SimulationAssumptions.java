package studybuddy.simulation;

import studybuddy.exception.InvalidInputException;

/** Configurable numbers behind the Optimistic / Expected / Pessimistic outcomes. */
public class SimulationAssumptions {
    private double optimisticMultiplier = 0.8;     // 80% of the estimated remaining hours
    private double pessimisticMultiplier = 1.3;    // 130% of the estimated remaining hours
    private int minHistorySamples = 3;             // completed tasks of one type needed to trust history
    private boolean useHistory = true;

    public double getOptimisticMultiplier() { return optimisticMultiplier; }
    public double getPessimisticMultiplier() { return pessimisticMultiplier; }
    public int getMinHistorySamples() { return minHistorySamples; }
    public boolean isUseHistory() { return useHistory; }

    public void setOptimisticMultiplier(double value) throws InvalidInputException {
        if (Double.isNaN(value) || value <= 0 || value > 1)
            throw new InvalidInputException("Optimistic multiplier must be above 0 and at most 1.");
        this.optimisticMultiplier = value;
    }

    public void setPessimisticMultiplier(double value) throws InvalidInputException {
        if (Double.isNaN(value) || value < 1 || value > 3)
            throw new InvalidInputException("Pessimistic multiplier must be between 1 and 3.");
        this.pessimisticMultiplier = value;
    }

    public void setMinHistorySamples(int samples) throws InvalidInputException {
        if (samples < 1) throw new InvalidInputException("At least 1 history sample is required.");
        this.minHistorySamples = samples;
    }

    public void setUseHistory(boolean useHistory) { this.useHistory = useHistory; }

    /** Multiplier applied on top of the expected duration. */
    public double getMultiplier(OutcomeType outcome) {
        switch (outcome) {
            case OPTIMISTIC: return optimisticMultiplier;
            case PESSIMISTIC: return pessimisticMultiplier;
            default: return 1.0;
        }
    }

    public SimulationAssumptions copy() {
        SimulationAssumptions c = new SimulationAssumptions();
        c.optimisticMultiplier = optimisticMultiplier;
        c.pessimisticMultiplier = pessimisticMultiplier;
        c.minHistorySamples = minHistorySamples;
        c.useHistory = useHistory;
        return c;
    }
}
