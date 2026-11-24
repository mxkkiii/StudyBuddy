package studybuddy.simulation;

import studybuddy.scheduling.Feasibility;
import studybuddy.scheduling.TaskForecast;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** The comparison of one baseline schedule with one simulated schedule (filled in by ImpactAnalyzer). */
public class ImpactAnalysis {

    /** What happened to one task: feasibility and finish date before and after the change. */
    public static class TaskImpact {
        private final int taskId;
        private final String title;
        private final String subjectName;
        private final Feasibility before;
        private final Feasibility after;
        private final LocalDate finishBefore;     // null = not fully scheduled
        private final LocalDate finishAfter;
        private final Long shiftDays;             // finishAfter - finishBefore, null if either is unknown

        TaskImpact(int taskId, String title, String subjectName, Feasibility before, Feasibility after,
                   LocalDate finishBefore, LocalDate finishAfter, Long shiftDays) {
            this.taskId = taskId;
            this.title = title;
            this.subjectName = subjectName;
            this.before = before;
            this.after = after;
            this.finishBefore = finishBefore;
            this.finishAfter = finishAfter;
            this.shiftDays = shiftDays;
        }

        public int getTaskId() { return taskId; }
        public String getTitle() { return title; }
        public String getSubjectName() { return subjectName; }
        public Feasibility getBefore() { return before; }
        public Feasibility getAfter() { return after; }
        public LocalDate getFinishBefore() { return finishBefore; }
        public LocalDate getFinishAfter() { return finishAfter; }
        public Long getShiftDays() { return shiftDays; }

        /** True if the verdict or the planned finish date changed. */
        public boolean isChanged() {
            return before != after || !java.util.Objects.equals(finishBefore, finishAfter);
        }

        private static String days(long n) { return n + (n == 1 ? " day" : " days"); }

        @Override public String toString() {
            String shift = shiftDays == null ? "" : (shiftDays > 0 ? "  (" + days(shiftDays) + " later)"
                    : shiftDays < 0 ? "  (" + days(-shiftDays) + " earlier)" : "");
            return String.format("[#%d] %s: %s -> %s, finish %s -> %s%s", taskId, title, before.getLabel(),
                    after.getLabel(), finishBefore == null ? "not scheduled" : finishBefore,
                    finishAfter == null ? "not scheduled" : finishAfter, shift);
        }
    }

    /** A deadline by which more work is due than there are study hours available. */
    public static class DeadlineConflict {
        private final LocalDate date;
        private final double requiredHours;
        private final double availableHours;

        DeadlineConflict(LocalDate date, double requiredHours, double availableHours) {
            this.date = date;
            this.requiredHours = requiredHours;
            this.availableHours = availableHours;
        }

        public LocalDate getDate() { return date; }
        public double getRequiredHours() { return requiredHours; }
        public double getAvailableHours() { return availableHours; }
        public double getShortfall() { return requiredHours - availableHours; }

        @Override public String toString() {
            return String.format("By %s: %.1fh of work is due but only %.1fh are available (short by %.1fh)",
                    date, requiredHours, availableHours, getShortfall());
        }
    }

    private final LocalDate startDate;
    private final LocalDate windowEnd;           // the latest deadline: the period the analysis looks at
    private double originalAvailableHours, simulatedAvailableHours;
    private double originalWorkload, simulatedWorkload;
    private List<TaskForecast> onTrack = new ArrayList<>(), delayed = new ArrayList<>(), impossible = new ArrayList<>();
    private int baselineOnTrack, baselineDelayed, baselineImpossible;
    private List<TaskImpact> taskImpacts = new ArrayList<>();
    private List<LocalDate> overloadedDays = new ArrayList<>();
    private int baselineOverloadedDays;
    private List<DeadlineConflict> deadlineConflicts = new ArrayList<>();
    private int baselineConflictCount;
    private List<String> distributionChanges = new ArrayList<>();
    private List<String> suggestions = new ArrayList<>();

    ImpactAnalysis(LocalDate startDate, LocalDate windowEnd) {
        this.startDate = startDate;
        this.windowEnd = windowEnd;
    }

    // ----- package-private setters (only ImpactAnalyzer fills this object) -----
    void setAvailability(double original, double simulated) { originalAvailableHours = original; simulatedAvailableHours = simulated; }
    void setWorkload(double original, double simulated) { originalWorkload = original; simulatedWorkload = simulated; }
    void setSimulatedForecasts(List<TaskForecast> on, List<TaskForecast> del, List<TaskForecast> imp) { onTrack = on; delayed = del; impossible = imp; }
    void setBaselineCounts(int on, int del, int imp) { baselineOnTrack = on; baselineDelayed = del; baselineImpossible = imp; }
    void setTaskImpacts(List<TaskImpact> list) { taskImpacts = list; }
    void setOverloadedDays(List<LocalDate> days, int baselineCount) { overloadedDays = days; baselineOverloadedDays = baselineCount; }
    void setDeadlineConflicts(List<DeadlineConflict> list, int baselineCount) { deadlineConflicts = list; baselineConflictCount = baselineCount; }
    void setDistributionChanges(List<String> list) { distributionChanges = list; }
    void setSuggestions(List<String> list) { suggestions = list; }

    // ----- getters -----
    public LocalDate getStartDate() { return startDate; }
    public LocalDate getWindowEnd() { return windowEnd; }
    public double getOriginalAvailableHours() { return originalAvailableHours; }
    public double getSimulatedAvailableHours() { return simulatedAvailableHours; }
    public double getAvailableHoursChange() { return simulatedAvailableHours - originalAvailableHours; }
    public double getOriginalWorkload() { return originalWorkload; }
    public double getSimulatedWorkload() { return simulatedWorkload; }
    public List<TaskForecast> getOnTrack() { return Collections.unmodifiableList(onTrack); }
    public List<TaskForecast> getDelayed() { return Collections.unmodifiableList(delayed); }
    public List<TaskForecast> getImpossible() { return Collections.unmodifiableList(impossible); }
    public int getBaselineOnTrackCount() { return baselineOnTrack; }
    public int getBaselineDelayedCount() { return baselineDelayed; }
    public int getBaselineImpossibleCount() { return baselineImpossible; }
    public List<TaskImpact> getTaskImpacts() { return Collections.unmodifiableList(taskImpacts); }

    /** Tasks whose verdict or finish date changed, most seriously affected first. */
    public List<TaskImpact> getAffectedTasks() {
        List<TaskImpact> list = new ArrayList<>();
        for (TaskImpact t : taskImpacts) if (t.isChanged()) list.add(t);
        return list;
    }

    /** Days (up to the last deadline) on which the simulated schedule uses every available hour. */
    public List<LocalDate> getOverloadedDays() { return Collections.unmodifiableList(overloadedDays); }
    public int getBaselineOverloadedDayCount() { return baselineOverloadedDays; }
    public List<DeadlineConflict> getDeadlineConflicts() { return Collections.unmodifiableList(deadlineConflicts); }
    public int getBaselineConflictCount() { return baselineConflictCount; }
    public List<String> getDistributionChanges() { return Collections.unmodifiableList(distributionChanges); }
    public List<String> getSuggestions() { return Collections.unmodifiableList(suggestions); }

    public boolean hasProblems() { return !delayed.isEmpty() || !impossible.isEmpty(); }
}
