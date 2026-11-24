package studybuddy.simulation;

import studybuddy.exception.InvalidInputException;
import studybuddy.exception.InvalidStudyHoursException;
import studybuddy.exception.StudyBuddyException;
import studybuddy.model.PlanAdjustments;
import studybuddy.model.StudyPlanner;

import java.time.LocalDate;

/**
 * One hypothetical change ("what if I skip Friday?"). Create it with one of the static factory methods.
 * applyTo(...) changes a PlanAdjustments object: the simulator calls it on a COPY; the real plan is only
 * changed if the student explicitly chooses to apply the scenario.
 */
public class SimulationScenario {
    private final ScenarioType type;
    private final LocalDate fromDate;
    private final LocalDate toDate;
    private final double hours;              // hours removed / added per day
    private final int id;                    // task ID or subject ID
    private final LocalDate postponeUntil;   // POSTPONE_TASK with a date
    private final int postponeDays;          // POSTPONE_TASK with a number of days
    private final double sharePercent;       // PRIORITIZE_SUBJECT

    private SimulationScenario(ScenarioType type, LocalDate fromDate, LocalDate toDate, double hours, int id,
                               LocalDate postponeUntil, int postponeDays, double sharePercent) {
        this.type = type;
        this.fromDate = fromDate;
        this.toDate = toDate;
        this.hours = hours;
        this.id = id;
        this.postponeUntil = postponeUntil;
        this.postponeDays = postponeDays;
        this.sharePercent = sharePercent;
    }

    // ----- factories -----
    /** 1. Zero study hours on one day. */
    public static SimulationScenario skipDay(LocalDate date) throws InvalidInputException {
        requireDate(date);
        return new SimulationScenario(ScenarioType.SKIP_DAY, date, date, 0, 0, null, 0, 0);
    }

    /** 2. Fewer study hours per day over a date range (never below 0). */
    public static SimulationScenario reduceHours(LocalDate from, LocalDate to, double hoursPerDay)
            throws InvalidInputException, InvalidStudyHoursException {
        requireRange(from, to);
        requireHours(hoursPerDay);
        return new SimulationScenario(ScenarioType.REDUCE_HOURS, from, to, hoursPerDay, 0, null, 0, 0);
    }

    /** Overload: a single day. */
    public static SimulationScenario reduceHours(LocalDate date, double hoursPerDay)
            throws InvalidInputException, InvalidStudyHoursException {
        return reduceHours(date, date, hoursPerDay);
    }

    /** 3. Do not start the task before the given date. */
    public static SimulationScenario postponeTask(int taskId, LocalDate notBefore) throws InvalidInputException {
        requireDate(notBefore);
        return new SimulationScenario(ScenarioType.POSTPONE_TASK, null, null, 0, taskId, notBefore, 0, 0);
    }

    /** Overload: postpone by a number of days counted from the simulation's start date. */
    public static SimulationScenario postponeTask(int taskId, int days) throws InvalidInputException {
        if (days < 1) throw new InvalidInputException("Postpone by at least 1 day.");
        return new SimulationScenario(ScenarioType.POSTPONE_TASK, null, null, 0, taskId, null, days, 0);
    }

    /** 4. Give a subject a share (1-100%) of every day's study time. */
    public static SimulationScenario prioritizeSubject(int subjectId, double sharePercent) throws InvalidInputException {
        if (Double.isNaN(sharePercent) || sharePercent < 1 || sharePercent > 100)
            throw new InvalidInputException("Share must be between 1 and 100 percent.");
        return new SimulationScenario(ScenarioType.PRIORITIZE_SUBJECT, null, null, 0, subjectId, null, 0, sharePercent);
    }

    /** 5. Extra study hours per day over a date range (capped at 24 hours a day). */
    public static SimulationScenario increaseHours(LocalDate from, LocalDate to, double hoursPerDay)
            throws InvalidInputException, InvalidStudyHoursException {
        requireRange(from, to);
        requireHours(hoursPerDay);
        return new SimulationScenario(ScenarioType.INCREASE_HOURS, from, to, hoursPerDay, 0, null, 0, 0);
    }

    /** Overload: a single day. */
    public static SimulationScenario increaseHours(LocalDate date, double hoursPerDay)
            throws InvalidInputException, InvalidStudyHoursException {
        return increaseHours(date, date, hoursPerDay);
    }

    private static void requireDate(LocalDate d) throws InvalidInputException {
        if (d == null) throw new InvalidInputException("A date is required.");
    }

    private static void requireRange(LocalDate from, LocalDate to) throws InvalidInputException {
        requireDate(from);
        requireDate(to);
        if (to.isBefore(from)) throw new InvalidInputException("End date cannot be before the start date.");
    }

    private static void requireHours(double h) throws InvalidStudyHoursException {
        if (Double.isNaN(h) || h <= 0 || h > PlanAdjustments.MAX_HOURS_PER_DAY)
            throw new InvalidStudyHoursException("Hours per day must be above 0 and at most " + PlanAdjustments.MAX_HOURS_PER_DAY + ".");
    }

    // ----- getters -----
    public ScenarioType getType() { return type; }
    public LocalDate getFromDate() { return fromDate; }
    public LocalDate getToDate() { return toDate; }
    public double getHours() { return hours; }
    /** Task ID (POSTPONE_TASK) or subject ID (PRIORITIZE_SUBJECT). */
    public int getId() { return id; }
    public LocalDate getPostponeUntil() { return postponeUntil; }
    public int getPostponeDays() { return postponeDays; }
    public double getSharePercent() { return sharePercent; }

    /** The last calendar date this scenario touches, or null if it is not date-based. */
    public LocalDate getLastDate() { return toDate; }

    /** The date a postponed task may start, resolved against the simulation start. */
    public LocalDate resolvePostponeDate(LocalDate simulationStart) {
        return postponeUntil != null ? postponeUntil : simulationStart.plusDays(postponeDays);
    }

    // ----- applying -----
    /** Changes the given adjustments (a copy during simulation, or the real ones when the student applies it). */
    public void applyTo(PlanAdjustments adj, double defaultHours, LocalDate today) throws StudyBuddyException {
        switch (type) {
            case SKIP_DAY:
                adj.setHoursForDate(fromDate, 0);
                break;
            case REDUCE_HOURS:
                for (LocalDate d = clampStart(today); !d.isAfter(toDate); d = d.plusDays(1))
                    adj.setHoursForDate(d, Math.max(0, adj.getHours(d, defaultHours) - hours));
                break;
            case INCREASE_HOURS:
                for (LocalDate d = clampStart(today); !d.isAfter(toDate); d = d.plusDays(1))
                    adj.setHoursForDate(d, Math.min(PlanAdjustments.MAX_HOURS_PER_DAY, adj.getHours(d, defaultHours) + hours));
                break;
            case POSTPONE_TASK:
                adj.postponeTask(id, resolvePostponeDate(today));
                break;
            default:
                adj.setFocus(id, sharePercent);
        }
    }

    private LocalDate clampStart(LocalDate today) { return fromDate.isBefore(today) ? today : fromDate; }

    // ----- description -----
    /** Plain description using IDs. */
    public String describe() { return describe(null); }

    /** Description using task / subject names when the planner can find them. */
    public String describe(StudyPlanner planner) {
        switch (type) {
            case SKIP_DAY:
                return "Skip studying on " + fromDate;
            case REDUCE_HOURS:
                return String.format("Study %.1fh less per day from %s to %s", hours, fromDate, toDate);
            case INCREASE_HOURS:
                return String.format("Study %.1fh more per day from %s to %s", hours, fromDate, toDate);
            case POSTPONE_TASK: {
                String name = "task #" + id;
                if (planner != null) { try { name = "\"" + planner.findTask(id).getTitle() + "\""; } catch (StudyBuddyException ignored) { } }
                return "Postpone " + name + (postponeUntil != null ? " until " + postponeUntil : " by " + postponeDays + " days");
            }
            default: {
                String name = "subject #" + id;
                if (planner != null) { try { name = "\"" + planner.findSubject(id).getName() + "\""; } catch (StudyBuddyException ignored) { } }
                return String.format("Give %s %.0f%% of every day's study time", name, sharePercent);
            }
        }
    }

    @Override public String toString() { return describe(); }
}
