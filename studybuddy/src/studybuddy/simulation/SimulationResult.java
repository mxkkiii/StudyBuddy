package studybuddy.simulation;

import studybuddy.scheduling.ScheduleEntry;
import studybuddy.scheduling.StudySchedule;
import studybuddy.scheduling.TaskForecast;

import java.time.LocalDate;
import java.util.*;

/** Everything a What-If simulation produced: three outcomes, each comparing original and simulated schedules. */
public class SimulationResult {

    /** One outcome (optimistic / expected / pessimistic) with both schedules and their comparison. */
    public static class OutcomeResult {
        private final OutcomeType type;
        private final double multiplier;
        private final StudySchedule original;
        private final StudySchedule simulated;
        private final ImpactAnalysis analysis;

        OutcomeResult(OutcomeType type, double multiplier, StudySchedule original, StudySchedule simulated, ImpactAnalysis analysis) {
            this.type = type;
            this.multiplier = multiplier;
            this.original = original;
            this.simulated = simulated;
            this.analysis = analysis;
        }

        public OutcomeType getType() { return type; }
        public double getMultiplier() { return multiplier; }
        public StudySchedule getOriginalSchedule() { return original; }
        public StudySchedule getSimulatedSchedule() { return simulated; }
        public ImpactAnalysis getAnalysis() { return analysis; }
    }

    public static final String DISCLAIMER = "These results are estimates based on your planned hours, task estimates and the assumptions "
            + "above. They are not guaranteed predictions.";

    private final SimulationScenario scenario;
    private final String scenarioDescription;
    private final LocalDate startDate;
    private final SimulationAssumptions assumptions;
    private final List<String> historyNotes;
    private final Map<OutcomeType, OutcomeResult> outcomes = new EnumMap<>(OutcomeType.class);

    SimulationResult(SimulationScenario scenario, String scenarioDescription, LocalDate startDate,
                     SimulationAssumptions assumptions, List<String> historyNotes) {
        this.scenario = scenario;
        this.scenarioDescription = scenarioDescription;
        this.startDate = startDate;
        this.assumptions = assumptions;
        this.historyNotes = historyNotes;
    }

    void put(OutcomeResult outcome) { outcomes.put(outcome.getType(), outcome); }

    public SimulationScenario getScenario() { return scenario; }
    public String getScenarioDescription() { return scenarioDescription; }
    public LocalDate getStartDate() { return startDate; }
    public SimulationAssumptions getAssumptions() { return assumptions; }
    public List<String> getHistoryNotes() { return Collections.unmodifiableList(historyNotes); }
    public OutcomeResult getOutcome(OutcomeType type) { return outcomes.get(type); }
    public OutcomeResult getExpected() { return outcomes.get(OutcomeType.EXPECTED); }
    /** The current plan under expected durations. */
    public StudySchedule getOriginalSchedule() { return getExpected().getOriginalSchedule(); }
    /** The hypothetical plan under expected durations. */
    public StudySchedule getSimulatedSchedule() { return getExpected().getSimulatedSchedule(); }

    // =====================================================================
    // Text output
    // =====================================================================
    /** Full report: scenario, assumptions, the expected outcome in detail, and the other two outcomes compactly. */
    public String render() {
        StringBuilder sb = new StringBuilder();
        String bar = "=".repeat(64);
        sb.append(bar).append('\n');
        sb.append("WHAT-IF SIMULATION: ").append(scenarioDescription).append('\n');
        sb.append("Simulation start: ").append(startDate).append('\n');
        sb.append(String.format("Assumptions: optimistic x%.2f, expected x1.00, pessimistic x%.2f of the estimated hours%n",
                assumptions.getOptimisticMultiplier(), assumptions.getPessimisticMultiplier()));
        for (String note : historyNotes) sb.append("  - ").append(note).append('\n');
        sb.append("Your real schedule and progress are NOT changed by this simulation.\n");
        sb.append(bar).append('\n');

        sb.append(renderOutcome(OutcomeType.EXPECTED));

        sb.append("\nOUTCOME COMPARISON (tasks on track / delayed / impossible)\n");
        for (OutcomeType t : OutcomeType.values()) {
            ImpactAnalysis a = outcomes.get(t).getAnalysis();
            sb.append(String.format("  %-12s workload %5.1fh | before: %d / %d / %d | after: %d / %d / %d%n", t.getLabel(),
                    a.getSimulatedWorkload(), a.getBaselineOnTrackCount(), a.getBaselineDelayedCount(),
                    a.getBaselineImpossibleCount(), a.getOnTrack().size(), a.getDelayed().size(), a.getImpossible().size()));
        }
        for (OutcomeType t : new OutcomeType[]{OutcomeType.OPTIMISTIC, OutcomeType.PESSIMISTIC}) {
            ImpactAnalysis a = outcomes.get(t).getAnalysis();
            for (TaskForecast f : a.getImpossible()) sb.append("  ").append(t.getLabel()).append(" - impossible: ").append(f.getTitle()).append('\n');
            for (TaskForecast f : a.getDelayed()) sb.append("  ").append(t.getLabel()).append(" - delayed: ").append(f.getTitle()).append('\n');
        }
        sb.append('\n').append(DISCLAIMER).append('\n').append(bar).append('\n');
        return sb.toString();
    }

    /** Detailed section for one outcome. */
    public String renderOutcome(OutcomeType type) {
        OutcomeResult o = outcomes.get(type);
        ImpactAnalysis a = o.getAnalysis();
        StringBuilder sb = new StringBuilder();
        sb.append(type.getLabel().toUpperCase()).append(" OUTCOME (").append(type.getMeaning()).append(")\n");
        sb.append(String.format("  Available study hours until the last deadline (%s): original %.1fh -> simulated %.1fh (%+.1fh)%n",
                a.getWindowEnd(), a.getOriginalAvailableHours(), a.getSimulatedAvailableHours(), a.getAvailableHoursChange()));
        sb.append(String.format("  Total remaining workload: %.1fh%n", a.getSimulatedWorkload()));
        sb.append(String.format("  Tasks on track: %d -> %d | delayed: %d -> %d | impossible: %d -> %d%n",
                a.getBaselineOnTrackCount(), a.getOnTrack().size(), a.getBaselineDelayedCount(), a.getDelayed().size(),
                a.getBaselineImpossibleCount(), a.getImpossible().size()));

        sb.append("  Tasks that remain on track:\n");
        if (a.getOnTrack().isEmpty()) sb.append("     (none)\n");
        for (TaskForecast f : a.getOnTrack()) sb.append("     - [#").append(f.getTaskId()).append("] ").append(f.getTitle())
                .append(" (finish ").append(f.getCompletionDate()).append(", due ").append(f.getDeadline()).append(")\n");
        sb.append("  Tasks that may become delayed:\n");
        if (a.getDelayed().isEmpty()) sb.append("     (none)\n");
        for (TaskForecast f : a.getDelayed()) sb.append("     - ").append(f.getMessage()).append('\n');
        sb.append("  Tasks that may become impossible to complete in time:\n");
        if (a.getImpossible().isEmpty()) sb.append("     (none)\n");
        for (TaskForecast f : a.getImpossible()) sb.append("     - ").append(f.getMessage()).append('\n');

        List<ImpactAnalysis.TaskImpact> affected = a.getAffectedTasks();
        sb.append("  Affected tasks (verdict or finish date changed): ").append(affected.size()).append('\n');
        for (ImpactAnalysis.TaskImpact t : affected) sb.append("     - ").append(t).append('\n');

        sb.append("  Changes in task distribution (").append(a.getDistributionChanges().size()).append(" changes");
        sb.append(a.getDistributionChanges().size() > 12 ? ", first 12 shown):\n" : "):\n");
        int shown = 0;
        for (String line : a.getDistributionChanges()) { if (shown++ == 12) break; sb.append("     ").append(line).append('\n'); }

        sb.append(String.format("  Fully booked days: %d (before: %d)%n", a.getOverloadedDays().size(), a.getBaselineOverloadedDayCount()));
        sb.append(String.format("  Deadline conflicts: %d (before: %d)%n", a.getDeadlineConflicts().size(), a.getBaselineConflictCount()));
        for (ImpactAnalysis.DeadlineConflict c : a.getDeadlineConflicts()) sb.append("     - ").append(c).append('\n');
        sb.append("  Suggested adjustments:\n");
        for (String s : a.getSuggestions()) sb.append("     * ").append(s).append('\n');
        return sb.toString();
    }

    /** Day-by-day table: the original plan next to the simulated plan (days that differ are marked *). */
    public String renderScheduleComparison(OutcomeType type) {
        OutcomeResult o = outcomes.get(type);
        StudySchedule a = o.getOriginalSchedule(), b = o.getSimulatedSchedule();
        LocalDate end = a.getEndDate().isAfter(b.getEndDate()) ? a.getEndDate() : b.getEndDate();
        List<String[]> table = new ArrayList<>();
        int leftWidth = "Original".length();
        for (LocalDate d = startDate; !d.isAfter(end); d = d.plusDays(1)) {
            String left = summarize(a, d), right = summarize(b, d);
            String dow = d.getDayOfWeek().toString().substring(0, 3);
            table.add(new String[]{(left.equals(right) ? "  " : "* ") + dow + " " + d.toString().substring(5), left, right});
            leftWidth = Math.max(leftWidth, left.length());
        }
        StringBuilder sb = new StringBuilder();
        sb.append("SCHEDULE COMPARISON (").append(type.getLabel()).append(")  (* = day differs)\n");
        sb.append(String.format("  %-14s | %-" + leftWidth + "s | %s%n", "Date", "Original", "Simulated"));
        for (String[] row : table) sb.append(String.format("%-16s | %-" + leftWidth + "s | %s%n", row[0], row[1], row[2]));
        return sb.toString();
    }

    private String summarize(StudySchedule s, LocalDate day) {
        List<ScheduleEntry> entries = s.getEntriesForDate(day);
        if (entries.isEmpty()) return s.getAvailableHours(day) <= 0 ? "(no study time)" : "-";
        StringBuilder sb = new StringBuilder();
        for (ScheduleEntry e : entries) {
            if (sb.length() > 0) sb.append(", ");
            String title = e.getTaskTitle().length() > 12 ? e.getTaskTitle().substring(0, 12) : e.getTaskTitle();
            sb.append(title).append(' ').append(String.format("%.1fh", e.getHours()));
        }
        return sb.toString();
    }

    @Override public String toString() { return render(); }
}
