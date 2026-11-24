package studybuddy.scheduling;

import studybuddy.exception.InvalidInputException;
import studybuddy.exception.InvalidStudyHoursException;
import studybuddy.model.PlanAdjustments;

import java.time.LocalDate;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * How many hours the student can study on each date: a default plus per-date overrides.
 * The What-If simulator edits a COPY of this object, so the real settings are never touched.
 */
public class DailyAvailability {
    public static final double MAX_HOURS_PER_DAY = 24.0;

    private double defaultHours;
    private final Map<LocalDate, Double> overrides = new HashMap<>();

    public DailyAvailability(double defaultHours) throws InvalidStudyHoursException {
        checkHours(defaultHours);
        this.defaultHours = defaultHours;
    }

    private static void checkHours(double hours) throws InvalidStudyHoursException {
        if (Double.isNaN(hours) || hours < 0 || hours > MAX_HOURS_PER_DAY)
            throw new InvalidStudyHoursException("Hours per day must be between 0 and " + MAX_HOURS_PER_DAY + ".");
    }

    /** Default hours plus the per-date overrides stored in the plan adjustments. */
    public static DailyAvailability of(double defaultHours, PlanAdjustments adjustments)
            throws InvalidStudyHoursException, InvalidInputException {
        DailyAvailability availability = new DailyAvailability(defaultHours);
        if (adjustments != null) {
            for (Map.Entry<LocalDate, Double> e : adjustments.getHourOverrides().entrySet())
                availability.setHours(e.getKey(), e.getValue());
        }
        return availability;
    }

    public double getDefaultHours() { return defaultHours; }

    public void setDefaultHours(double hours) throws InvalidStudyHoursException {
        checkHours(hours);
        this.defaultHours = hours;
    }

    /** Hours available on one date (override if present, otherwise the default). */
    public double getHours(LocalDate date) {
        Double override = overrides.get(date);
        return override != null ? override : defaultHours;
    }

    public void setHours(LocalDate date, double hours) throws InvalidStudyHoursException, InvalidInputException {
        if (date == null) throw new InvalidInputException("Date cannot be empty.");
        checkHours(hours);
        overrides.put(date, hours);
    }

    /** Sets the same hours for every date from start to end (inclusive). */
    public void setHoursForRange(LocalDate start, LocalDate end, double hours)
            throws InvalidStudyHoursException, InvalidInputException {
        checkRange(start, end);
        for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) setHours(d, hours);
    }

    /** Adds (or, if negative, removes) hours on every date in the range, clamped to 0..24. */
    public void addHoursForRange(LocalDate start, LocalDate end, double delta)
            throws InvalidStudyHoursException, InvalidInputException {
        checkRange(start, end);
        for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
            setHours(d, Math.max(0, Math.min(MAX_HOURS_PER_DAY, getHours(d) + delta)));
        }
    }

    public void clearOverride(LocalDate date) { overrides.remove(date); }

    public Map<LocalDate, Double> getOverrides() { return Collections.unmodifiableMap(overrides); }

    /** Total hours available from start to end, both inclusive (0 if end is before start). */
    public double getTotalHours(LocalDate start, LocalDate end) {
        double total = 0;
        for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) total += getHours(d);
        return total;
    }

    /** Independent copy. */
    public DailyAvailability copy() {
        DailyAvailability c = new DailyAvailability();
        c.defaultHours = this.defaultHours;
        c.overrides.putAll(this.overrides);
        return c;
    }

    private DailyAvailability() { }   // used only by copy()

    private static void checkRange(LocalDate start, LocalDate end) throws InvalidInputException {
        if (start == null || end == null) throw new InvalidInputException("Start and end dates are required.");
        if (end.isBefore(start)) throw new InvalidInputException("End date cannot be before the start date.");
    }
}
