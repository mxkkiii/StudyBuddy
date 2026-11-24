package studybuddy.simulation;

import studybuddy.exception.InvalidInputException;
import studybuddy.exception.StudyBuddyException;
import studybuddy.model.PlanAdjustments;
import studybuddy.model.StudyItem;
import studybuddy.model.StudyPlanner;
import studybuddy.scheduling.DailyAvailability;
import studybuddy.scheduling.PriorityScheduler;
import studybuddy.scheduling.ScheduleOptions;
import studybuddy.scheduling.StudySchedule;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Runs "what if" experiments on the student's plan.
 *
 * SAFETY RULES
 *  - The real tasks, sessions and plan adjustments are only READ. Every simulation works on copies
 *    (task.copy(), PlanAdjustments.copy(), a fresh DailyAvailability).
 *  - It reuses the normal PriorityScheduler, so simulated and real schedules follow the same rules.
 *  - Nothing changes in the real plan unless the student explicitly calls applyToPlan(...).
 *  - Simulation objects are never saved by FileManager.
 */
public class WhatIfSimulator {
    private final StudyPlanner planner;
    private final PriorityScheduler scheduler;
    private final ImpactAnalyzer analyzer = new ImpactAnalyzer();
    private SimulationAssumptions assumptions = new SimulationAssumptions();

    public WhatIfSimulator(StudyPlanner planner) throws InvalidInputException {
        this(planner, new PriorityScheduler());
    }

    /** Overload: use a specific scheduler (for example one with a shorter planning horizon). */
    public WhatIfSimulator(StudyPlanner planner, PriorityScheduler scheduler) throws InvalidInputException {
        if (planner == null) throw new InvalidInputException("A planner is required.");
        this.planner = planner;
        this.scheduler = scheduler;
    }

    public SimulationAssumptions getAssumptions() { return assumptions; }

    public void setAssumptions(SimulationAssumptions assumptions) throws InvalidInputException {
        if (assumptions == null) throw new InvalidInputException("Assumptions are required.");
        this.assumptions = assumptions;
    }

    /** Simulates the scenario starting today. */
    public SimulationResult simulate(SimulationScenario scenario) throws StudyBuddyException {
        return simulate(scenario, LocalDate.now());
    }

    public SimulationResult simulate(SimulationScenario scenario, LocalDate start) throws StudyBuddyException {
        validate(scenario, start);

        double defaultHours = planner.getDailyAvailableHours();
        PlanAdjustments originalAdjustments = planner.getAdjustments().copy();   // temporary copy of the current plan settings
        PlanAdjustments simulatedAdjustments = originalAdjustments.copy();
        scenario.applyTo(simulatedAdjustments, defaultHours, start);              // the hypothetical change, on the copy only

        List<StudyItem> unfinished = planner.getUnfinishedTasks();                // read-only; copied below
        SimulationAssumptions used = assumptions.copy();
        DurationHistory history = new DurationHistory(planner, used);
        SimulationResult result = new SimulationResult(scenario, scenario.describe(planner), start, used, history.describe());

        for (OutcomeType outcome : OutcomeType.values()) {
            StudySchedule original = buildSchedule(unfinished, start, defaultHours, originalAdjustments, outcome, used, history);
            StudySchedule simulated = buildSchedule(unfinished, start, defaultHours, simulatedAdjustments, outcome, used, history);
            result.put(new SimulationResult.OutcomeResult(outcome, used.getMultiplier(outcome), original, simulated,
                    analyzer.analyze(original, simulated)));
        }
        return result;
    }

    /**
     * Makes the scenario part of the REAL plan. Call this only when the student explicitly chooses to.
     * (The next real schedule will then include the change.)
     */
    public void applyToPlan(SimulationResult result) throws StudyBuddyException {
        if (result == null) throw new InvalidInputException("A simulation result is required.");
        result.getScenario().applyTo(planner.getAdjustments(), planner.getDailyAvailableHours(), result.getStartDate());
    }

    // ------------------------------------------------------------------
    private void validate(SimulationScenario scenario, LocalDate start) throws StudyBuddyException {
        if (scenario == null) throw new InvalidInputException("A scenario is required.");
        if (start == null) throw new InvalidInputException("A start date is required.");
        switch (scenario.getType()) {
            case SKIP_DAY:
            case REDUCE_HOURS:
            case INCREASE_HOURS:
                if (scenario.getLastDate().isBefore(start))
                    throw new InvalidInputException("That date is before the simulation start (" + start + ").");
                break;
            case POSTPONE_TASK: {
                StudyItem task = planner.findTask(scenario.getId());              // TaskNotFoundException if unknown
                if (!task.isUnfinished()) throw new InvalidInputException("\"" + task.getTitle() + "\" is already completed.");
                if (!scenario.resolvePostponeDate(start).isAfter(start))
                    throw new InvalidInputException("The new start date must be after " + start + ".");
                break;
            }
            default:
                planner.findSubject(scenario.getId());                              // SubjectNotFoundException if unknown
        }
    }

    /** Copies every unfinished task, scales its remaining hours for the outcome, and runs the normal scheduler. */
    private StudySchedule buildSchedule(List<StudyItem> realTasks, LocalDate start, double defaultHours,
                                        PlanAdjustments adjustments, OutcomeType outcome,
                                        SimulationAssumptions used, DurationHistory history) throws StudyBuddyException {
        List<StudyItem> working = new ArrayList<>();
        for (StudyItem real : realTasks) {
            StudyItem copy = real.copy();
            double factor = history.getFactor(real.getTaskType()) * used.getMultiplier(outcome);
            copy.setRemainingHours(Math.min(StudyItem.MAX_HOURS, real.getRemainingHours() * factor));
            working.add(copy);
        }
        return scheduler.generate(working, start, DailyAvailability.of(defaultHours, adjustments),
                ScheduleOptions.of(adjustments));
    }
}
