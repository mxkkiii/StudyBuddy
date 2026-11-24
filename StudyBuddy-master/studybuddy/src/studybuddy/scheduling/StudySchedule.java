package studybuddy.scheduling;

import java.time.LocalDate;
import java.util.*;

/**
 * The result of scheduling: a list of ScheduleEntry lines plus a TaskForecast per task.
 * Read-only for outsiders; only the scheduler (same package) can fill it.
 */
public class StudySchedule {
    private final LocalDate startDate;
    private final DailyAvailability availability;                  // private copy used to build this schedule
    private final List<ScheduleEntry> entries = new ArrayList<>();
    private final Map<Integer, TaskForecast> forecasts = new LinkedHashMap<>();

    StudySchedule(LocalDate startDate, DailyAvailability availability) {
        this.startDate = startDate;
        this.availability = availability;
    }

    // ----- package-private builders -----
    void addEntry(ScheduleEntry entry) { entries.add(entry); }
    void addForecast(TaskForecast forecast) { forecasts.put(forecast.getTaskId(), forecast); }

    // ----- queries -----
    public LocalDate getStartDate() { return startDate; }

    /** A copy, so callers cannot change the availability this schedule was built from. */
    public DailyAvailability getAvailability() { return availability.copy(); }

    public List<ScheduleEntry> getEntries() { return Collections.unmodifiableList(entries); }

    public List<ScheduleEntry> getEntriesForDate(LocalDate date) {
        List<ScheduleEntry> list = new ArrayList<>();
        for (ScheduleEntry e : entries) if (e.getDate().equals(date)) list.add(e);
        return list;
    }

    public List<ScheduleEntry> getEntriesForTask(int taskId) {
        List<ScheduleEntry> list = new ArrayList<>();
        for (ScheduleEntry e : entries) if (e.getTaskId() == taskId) list.add(e);
        return list;
    }

    /** Dates that have at least one entry, in order. */
    public List<LocalDate> getScheduledDates() {
        return new ArrayList<>(new TreeSet<>(entriesDates()));
    }

    private Set<LocalDate> entriesDates() {
        Set<LocalDate> dates = new HashSet<>();
        for (ScheduleEntry e : entries) dates.add(e.getDate());
        return dates;
    }

    /** Last date with study planned (the start date if nothing is scheduled). */
    public LocalDate getEndDate() {
        LocalDate end = startDate;
        for (ScheduleEntry e : entries) if (e.getDate().isAfter(end)) end = e.getDate();
        return end;
    }

    public double getScheduledHours(LocalDate date) {
        double total = 0;
        for (ScheduleEntry e : entries) if (e.getDate().equals(date)) total += e.getHours();
        return total;
    }

    public double getTotalScheduledHours() {
        double total = 0;
        for (ScheduleEntry e : entries) total += e.getHours();
        return total;
    }

    /** Hours available (not necessarily used) from the start date to the last scheduled date. */
    public double getAvailableHoursInSchedule() { return availability.getTotalHours(startDate, getEndDate()); }

    public double getAvailableHours(LocalDate date) { return availability.getHours(date); }

    /** Hours scheduled per task ID. */
    public Map<Integer, Double> getHoursPerTask() {
        Map<Integer, Double> map = new LinkedHashMap<>();
        for (ScheduleEntry e : entries) map.merge(e.getTaskId(), e.getHours(), Double::sum);
        return map;
    }

    public Collection<TaskForecast> getForecasts() { return Collections.unmodifiableCollection(forecasts.values()); }

    public TaskForecast getForecast(int taskId) { return forecasts.get(taskId); }

    public List<TaskForecast> getForecasts(Feasibility feasibility) {
        List<TaskForecast> list = new ArrayList<>();
        for (TaskForecast f : forecasts.values()) if (f.getFeasibility() == feasibility) list.add(f);
        return list;
    }

    /** One warning message per task that may miss its deadline. */
    public List<String> getWarnings() {
        List<String> warnings = new ArrayList<>();
        for (TaskForecast f : forecasts.values()) if (!f.isOnTrack()) warnings.add(f.getMessage());
        return warnings;
    }

    public boolean isFeasible() { return getWarnings().isEmpty(); }

    public boolean isEmpty() { return entries.isEmpty(); }

    /** A printable day-by-day plan followed by any warnings. */
    public String render() {
        StringBuilder sb = new StringBuilder();
        sb.append("STUDY SCHEDULE from ").append(startDate).append('\n');
        if (entries.isEmpty()) {
            sb.append("  (nothing to schedule)\n");
        } else {
            for (LocalDate d = startDate; !d.isAfter(getEndDate()); d = d.plusDays(1)) {
                String dow = d.getDayOfWeek().toString().substring(0, 3);
                sb.append(String.format("%s %s  (%.1fh of %.1fh)%n", dow, d, getScheduledHours(d), availability.getHours(d)));
                List<ScheduleEntry> day = getEntriesForDate(d);
                if (day.isEmpty()) sb.append("     - no study time -\n");
                for (ScheduleEntry e : day) sb.append("     ").append(e).append('\n');
            }
        }
        List<String> warnings = getWarnings();
        if (warnings.isEmpty()) {
            sb.append("All tasks can be completed before their deadlines.\n");
        } else {
            sb.append("WARNINGS:\n");
            for (String w : warnings) sb.append("  ! ").append(w).append('\n');
        }
        return sb.toString();
    }

    @Override public String toString() { return render(); }
}
