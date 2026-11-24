package studybuddy.model;

import studybuddy.exception.InvalidInputException;
import studybuddy.exception.InvalidStudyHoursException;

import java.time.LocalDate;
import java.util.*;

/**
 * Changes a student has made to the "normal" plan:
 *  - hours on specific dates (a skipped day = 0h, a shorter or longer day),
 *  - tasks postponed until a later date ("do not start before ..."),
 *  - a focus subject that receives a share of every day's study time.
 * The planner owns one real instance. The What-If simulator works on COPIES and only touches the real
 * one when the student explicitly chooses to apply a scenario.
 */
public class PlanAdjustments {
    public static final double MAX_HOURS_PER_DAY = 24.0;

    private final Map<LocalDate, Double> hourOverrides = new TreeMap<>();
    private final Map<Integer, LocalDate> postponements = new TreeMap<>();
    private Integer focusSubjectId = null;
    private double focusSharePercent = 0;

    // ----- hours on specific dates -----
    public void setHoursForDate(LocalDate date, double hours) throws InvalidInputException, InvalidStudyHoursException {
        if (date == null) throw new InvalidInputException("Date cannot be empty.");
        if (Double.isNaN(hours) || hours < 0 || hours > MAX_HOURS_PER_DAY)
            throw new InvalidStudyHoursException("Hours per day must be between 0 and " + MAX_HOURS_PER_DAY + ".");
        hourOverrides.put(date, hours);
    }

    public void clearHoursForDate(LocalDate date) { hourOverrides.remove(date); }

    /** Hours for a date: the override if there is one, otherwise the given default. */
    public double getHours(LocalDate date, double defaultHours) {
        Double override = hourOverrides.get(date);
        return override != null ? override : defaultHours;
    }

    public Map<LocalDate, Double> getHourOverrides() { return Collections.unmodifiableMap(hourOverrides); }

    // ----- postponed tasks -----
    public void postponeTask(int taskId, LocalDate notBefore) throws InvalidInputException {
        if (notBefore == null) throw new InvalidInputException("Postpone date cannot be empty.");
        postponements.put(taskId, notBefore);
    }

    public void clearPostponement(int taskId) { postponements.remove(taskId); }

    /** The earliest date the task may be scheduled, or null if it is not postponed. */
    public LocalDate getEarliestStart(int taskId) { return postponements.get(taskId); }

    public Map<Integer, LocalDate> getPostponements() { return Collections.unmodifiableMap(postponements); }

    // ----- focus subject -----
    /** The focus subject receives this percentage (1-100) of each day's hours while it has unfinished work. */
    public void setFocus(int subjectId, double sharePercent) throws InvalidInputException {
        if (Double.isNaN(sharePercent) || sharePercent < 1 || sharePercent > 100)
            throw new InvalidInputException("Focus share must be between 1 and 100 percent.");
        this.focusSubjectId = subjectId;
        this.focusSharePercent = sharePercent;
    }

    public void clearFocus() { focusSubjectId = null; focusSharePercent = 0; }

    public Integer getFocusSubjectId() { return focusSubjectId; }
    public double getFocusSharePercent() { return focusSharePercent; }

    // ----- general -----
    public boolean isEmpty() { return hourOverrides.isEmpty() && postponements.isEmpty() && focusSubjectId == null; }

    public void clear() { hourOverrides.clear(); postponements.clear(); clearFocus(); }

    /** Independent copy (used by the simulator). */
    public PlanAdjustments copy() {
        PlanAdjustments c = new PlanAdjustments();
        c.hourOverrides.putAll(hourOverrides);
        c.postponements.putAll(postponements);
        c.focusSubjectId = focusSubjectId;
        c.focusSharePercent = focusSharePercent;
        return c;
    }

    public List<String> describe() {
        List<String> lines = new ArrayList<>();
        for (Map.Entry<LocalDate, Double> e : hourOverrides.entrySet())
            lines.add("Study hours on " + e.getKey() + ": " + e.getValue() + "h");
        for (Map.Entry<Integer, LocalDate> e : postponements.entrySet())
            lines.add("Task #" + e.getKey() + " postponed until " + e.getValue());
        if (focusSubjectId != null)
            lines.add("Focus on subject #" + focusSubjectId + ": " + focusSharePercent + "% of each day");
        return lines;
    }
}
