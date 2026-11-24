package studybuddy.scheduling;

import studybuddy.exception.InvalidInputException;
import studybuddy.exception.InvalidStudyHoursException;
import studybuddy.model.StudyItem;
import studybuddy.model.StudyPlanner;

import java.time.LocalDate;
import java.util.*;

/**
 * Builds a day-by-day plan: every day the available hours go to the highest-priority unfinished task first;
 * a task that does not fit is split and its remaining hours carry over to the next day.
 *
 * Optional ScheduleOptions add two rules used by the student's plan adjustments and by the What-If simulator:
 *  - a postponed task is not scheduled before its earliest-start date;
 *  - a focus subject receives a share of each day's hours (unused focus hours flow back to the other tasks).
 *
 * The scheduler works on COPIES of the tasks, so it never changes real tasks. This is also why the
 * What-If simulator can reuse it directly.
 */
public class PriorityScheduler {
    public static final int DEFAULT_MAX_DAYS = 180;
    private static final double EPS = 1e-9;

    private final int maxDays;

    public PriorityScheduler() { this(DEFAULT_MAX_DAYS); }

    /** Overload: limit how many days ahead the scheduler will plan. */
    public PriorityScheduler(int maxDays) { this.maxDays = Math.max(1, maxDays); }

    public int getMaxDays() { return maxDays; }

    // ------------------------------------------------------------------
    // Entry points (overloaded)
    // ------------------------------------------------------------------
    /** Schedules every unfinished task of the planner, starting today. */
    public StudySchedule generate(StudyPlanner planner) throws InvalidInputException, InvalidStudyHoursException {
        return generate(planner, LocalDate.now());
    }

    /** Uses the planner's daily hours AND its real plan adjustments (skipped days, postponed tasks, focus). */
    public StudySchedule generate(StudyPlanner planner, LocalDate start)
            throws InvalidInputException, InvalidStudyHoursException {
        if (planner == null) throw new InvalidInputException("Planner is required.");
        return generate(planner.getUnfinishedTasks(), start,
                DailyAvailability.of(planner.getDailyAvailableHours(), planner.getAdjustments()),
                ScheduleOptions.of(planner.getAdjustments()));
    }

    /** Plain scheduling with no extra rules. */
    public StudySchedule generate(Collection<? extends StudyItem> tasks, LocalDate start, DailyAvailability availability)
            throws InvalidInputException {
        return generate(tasks, start, availability, ScheduleOptions.none());
    }

    /** The core algorithm. Works with any StudyItem subclass through the common parent type. */
    public StudySchedule generate(Collection<? extends StudyItem> tasks, LocalDate start,
                                  DailyAvailability availability, ScheduleOptions options) throws InvalidInputException {
        if (tasks == null) throw new InvalidInputException("Task list is required.");
        if (start == null) throw new InvalidInputException("Start date is required.");
        if (availability == null) throw new InvalidInputException("Availability is required.");
        if (options == null) options = ScheduleOptions.none();

        StudySchedule schedule = new StudySchedule(start, availability.copy());

        // 1-3. Keep unfinished tasks, copy them, score them, and put them in PriorityQueues.
        PriorityQueue<StudyItem> focusQueue = new PriorityQueue<>();
        PriorityQueue<StudyItem> otherQueue = new PriorityQueue<>();
        List<StudyItem> waiting = new ArrayList<>();               // postponed tasks that may not start yet
        List<StudyItem> copies = new ArrayList<>();
        Map<Integer, Double> remaining = new HashMap<>();
        Map<Integer, Double> required = new HashMap<>();
        for (StudyItem t : tasks) {
            if (!t.isUnfinished() || t.getRemainingHours() <= EPS) continue;
            StudyItem copy = t.copy();
            copy.recalculatePriority(start);          // polymorphism: each subclass applies its own weight
            copies.add(copy);
            remaining.put(copy.getId(), copy.getRemainingHours());
            required.put(copy.getId(), copy.getRemainingHours());
            LocalDate earliest = options.getNotBefore(copy.getId());
            if (earliest != null && earliest.isAfter(start)) waiting.add(copy);
            else enqueue(copy, options, focusQueue, otherQueue);
        }

        // 4-6. Fill each day with the highest-priority work; split tasks and carry the rest forward.
        LocalDate day = start;
        for (int i = 0; i < maxDays && !(focusQueue.isEmpty() && otherQueue.isEmpty() && waiting.isEmpty());
             i++, day = day.plusDays(1)) {
            Iterator<StudyItem> it = waiting.iterator();
            while (it.hasNext()) {
                StudyItem t = it.next();
                if (!options.getNotBefore(t.getId()).isAfter(day)) { enqueue(t, options, focusQueue, otherQueue); it.remove(); }
            }

            double free = availability.getHours(day);
            double focusHours = options.hasFocus() ? free * options.getFocusShare() : 0;
            Map<Integer, Double> dayHours = new HashMap<>();
            Map<Integer, StudyItem> dayTasks = new HashMap<>();

            double unused = allocate(focusQueue, focusHours, remaining, dayHours, dayTasks);   // focus subject first
            unused += free - focusHours;                                                       // rest of the day
            unused = allocate(otherQueue, unused, remaining, dayHours, dayTasks);
            allocate(focusQueue, unused, remaining, dayHours, dayTasks);                       // leftovers flow back

            List<StudyItem> order = new ArrayList<>(dayTasks.values());
            Collections.sort(order);                                                           // highest priority first
            for (StudyItem t : order) schedule.addEntry(new ScheduleEntry(day, t, dayHours.get(t.getId())));
        }

        // 7-8. Check every task against its deadline.
        Collections.sort(copies);                      // highest priority first, for readable output
        for (StudyItem c : copies) {
            schedule.addForecast(buildForecast(c, schedule, availability, start, options,
                    required.get(c.getId()), Math.max(0, remaining.get(c.getId()))));
        }
        return schedule;
    }

    // ------------------------------------------------------------------
    private void enqueue(StudyItem task, ScheduleOptions options, PriorityQueue<StudyItem> focusQueue,
                         PriorityQueue<StudyItem> otherQueue) {
        if (options.isFocusTask(task)) focusQueue.add(task); else otherQueue.add(task);
    }

    /** Gives up to 'free' hours to the queue's highest-priority tasks; returns the hours left over. */
    private double allocate(PriorityQueue<StudyItem> queue, double free, Map<Integer, Double> remaining,
                            Map<Integer, Double> dayHours, Map<Integer, StudyItem> dayTasks) {
        while (free > EPS && !queue.isEmpty()) {
            StudyItem task = queue.poll();
            double left = remaining.get(task.getId());
            double hours = Math.min(left, free);
            dayHours.merge(task.getId(), hours, Double::sum);
            dayTasks.put(task.getId(), task);
            left -= hours;
            free -= hours;
            remaining.put(task.getId(), left);
            if (left > EPS) queue.add(task);          // not finished: carries over to the next day
        }
        return free;
    }

    private TaskForecast buildForecast(StudyItem task, StudySchedule schedule, DailyAvailability availability,
                                       LocalDate start, ScheduleOptions options, double requiredHours, double unscheduled) {
        double onTime = 0, late = 0;
        LocalDate lastDate = null;
        for (ScheduleEntry e : schedule.getEntriesForTask(task.getId())) {
            if (e.isLate()) late += e.getHours(); else onTime += e.getHours();
            if (lastDate == null || e.getDate().isAfter(lastDate)) lastDate = e.getDate();
        }
        boolean fullyScheduled = unscheduled <= EPS;
        LocalDate completion = fullyScheduled ? lastDate : null;
        LocalDate deadline = task.getDeadline();
        String label = "[#" + task.getId() + "] " + task.getTitle();

        if (fullyScheduled && late <= EPS) {
            return new TaskForecast(task.getId(), task.getTitle(), task.getSubjectName(), deadline, requiredHours,
                    onTime, late, 0, completion, Feasibility.ON_TRACK,
                    label + " is on track (planned finish " + completion + ", due " + deadline + ").");
        }

        // A postponed task cannot use the days before its earliest start.
        LocalDate earliest = options.getNotBefore(task.getId());
        LocalDate effectiveStart = (earliest != null && earliest.isAfter(start)) ? earliest : start;
        String postponed = effectiveStart.isAfter(start) ? " (postponed until " + effectiveStart + ")" : "";

        double capacityBeforeDeadline = availability.getTotalHours(effectiveStart, deadline);
        if (deadline.isBefore(start)) {
            return new TaskForecast(task.getId(), task.getTitle(), task.getSubjectName(), deadline, requiredHours,
                    onTime, late, unscheduled, completion, Feasibility.IMPOSSIBLE,
                    label + " is already past its deadline (" + deadline + "); it can only be done late"
                            + finishText(completion, unscheduled) + ".");
        }
        if (capacityBeforeDeadline + EPS < requiredHours) {
            return new TaskForecast(task.getId(), task.getTitle(), task.getSubjectName(), deadline, requiredHours,
                    onTime, late, unscheduled, completion, Feasibility.IMPOSSIBLE,
                    String.format("%s cannot be finished by %s: it needs %.1fh but only %.1fh are available before the deadline%s%s.",
                            label, deadline, requiredHours, capacityBeforeDeadline, postponed, finishText(completion, unscheduled)));
        }
        return new TaskForecast(task.getId(), task.getTitle(), task.getSubjectName(), deadline, requiredHours,
                onTime, late, unscheduled, completion, Feasibility.DELAYED,
                String.format("%s may NOT be finished by %s: higher-priority work takes the time%s%s. "
                                + "Consider adding study hours or postponing lower-priority tasks.",
                        label, deadline, postponed, finishText(completion, unscheduled)));
    }

    private String finishText(LocalDate completion, double unscheduled) {
        if (completion != null) return " (planned finish " + completion + ")";
        return String.format(" (%.1fh could not be placed within %d days)", unscheduled, maxDays);
    }
}
