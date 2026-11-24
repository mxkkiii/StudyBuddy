package studybuddy.simulation;

import studybuddy.exception.InvalidInputException;
import studybuddy.scheduling.*;
import studybuddy.simulation.ImpactAnalysis.DeadlineConflict;
import studybuddy.simulation.ImpactAnalysis.TaskImpact;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;

/** Compares a baseline schedule with a simulated one and works out what the change does. */
public class ImpactAnalyzer {
    private static final double EPS = 1e-9;

    public ImpactAnalysis analyze(StudySchedule baseline, StudySchedule simulated) throws InvalidInputException {
        if (baseline == null || simulated == null) throw new InvalidInputException("Two schedules are required.");
        LocalDate start = simulated.getStartDate();
        LocalDate windowEnd = start;
        for (TaskForecast f : simulated.getForecasts()) if (f.getDeadline().isAfter(windowEnd)) windowEnd = f.getDeadline();

        ImpactAnalysis a = new ImpactAnalysis(start, windowEnd);
        a.setAvailability(baseline.getAvailability().getTotalHours(start, windowEnd),
                simulated.getAvailability().getTotalHours(start, windowEnd));
        a.setWorkload(workload(baseline), workload(simulated));

        a.setSimulatedForecasts(new ArrayList<>(simulated.getForecasts(Feasibility.ON_TRACK)),
                new ArrayList<>(simulated.getForecasts(Feasibility.DELAYED)),
                new ArrayList<>(simulated.getForecasts(Feasibility.IMPOSSIBLE)));
        a.setBaselineCounts(baseline.getForecasts(Feasibility.ON_TRACK).size(),
                baseline.getForecasts(Feasibility.DELAYED).size(), baseline.getForecasts(Feasibility.IMPOSSIBLE).size());

        a.setTaskImpacts(taskImpacts(baseline, simulated));
        a.setOverloadedDays(fullyBookedDays(simulated, start, windowEnd), fullyBookedDays(baseline, start, windowEnd).size());
        a.setDeadlineConflicts(findConflicts(simulated), findConflicts(baseline).size());
        a.setDistributionChanges(distributionChanges(baseline, simulated));
        a.setSuggestions(suggest(a, simulated));
        return a;
    }

    private double workload(StudySchedule s) {
        double total = 0;
        for (TaskForecast f : s.getForecasts()) total += f.getRequiredHours();
        return total;
    }

    /** Verdict and finish date of every task, before and after; the most affected come first. */
    private List<TaskImpact> taskImpacts(StudySchedule baseline, StudySchedule simulated) {
        List<TaskImpact> list = new ArrayList<>();
        for (TaskForecast after : simulated.getForecasts()) {
            TaskForecast before = baseline.getForecast(after.getTaskId());
            if (before == null) before = after;
            Long shift = (before.getCompletionDate() != null && after.getCompletionDate() != null)
                    ? ChronoUnit.DAYS.between(before.getCompletionDate(), after.getCompletionDate()) : null;
            list.add(new TaskImpact(after.getTaskId(), after.getTitle(), after.getSubjectName(), before.getFeasibility(),
                    after.getFeasibility(), before.getCompletionDate(), after.getCompletionDate(), shift));
        }
        list.sort(Comparator.comparing(TaskImpact::getAfter).reversed()
                .thenComparing((TaskImpact t) -> t.getShiftDays() == null ? Long.MIN_VALUE : t.getShiftDays(),
                        Comparator.reverseOrder()));
        return list;
    }

    /** Days on which every available hour is used (up to the last deadline). */
    private List<LocalDate> fullyBookedDays(StudySchedule s, LocalDate start, LocalDate end) {
        List<LocalDate> days = new ArrayList<>();
        for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
            double available = s.getAvailableHours(d);
            if (available > EPS && s.getScheduledHours(d) >= available - EPS) days.add(d);
        }
        return days;
    }

    /**
     * Deadline conflicts: for each deadline, the work due on or before it is added up and compared with the
     * hours available until then. If the work is larger, no ordering of tasks can meet every deadline.
     */
    private List<DeadlineConflict> findConflicts(StudySchedule s) {
        LocalDate start = s.getStartDate();
        TreeMap<LocalDate, Double> dueOn = new TreeMap<>();
        for (TaskForecast f : s.getForecasts()) {
            LocalDate d = f.getDeadline().isBefore(start) ? start : f.getDeadline();   // overdue work is due "now"
            dueOn.merge(d, f.getRequiredHours(), Double::sum);
        }
        DailyAvailability availability = s.getAvailability();
        List<DeadlineConflict> conflicts = new ArrayList<>();
        double cumulative = 0;
        for (Map.Entry<LocalDate, Double> e : dueOn.entrySet()) {
            cumulative += e.getValue();
            double available = availability.getTotalHours(start, e.getKey());
            if (cumulative > available + EPS) conflicts.add(new DeadlineConflict(e.getKey(), cumulative, available));
        }
        return conflicts;
    }

    /** Lines such as "2026-11-24: Quiz 2 2.0h -> 0.0h" for every place the two schedules differ. */
    private List<String> distributionChanges(StudySchedule baseline, StudySchedule simulated) {
        Map<LocalDate, Map<Integer, Double>> before = hoursByDay(baseline);
        Map<LocalDate, Map<Integer, Double>> after = hoursByDay(simulated);
        Set<LocalDate> dates = new TreeSet<>(before.keySet());
        dates.addAll(after.keySet());
        List<String> lines = new ArrayList<>();
        for (LocalDate d : dates) {
            Map<Integer, Double> b = before.getOrDefault(d, Collections.emptyMap());
            Map<Integer, Double> s = after.getOrDefault(d, Collections.emptyMap());
            Set<Integer> ids = new TreeSet<>(b.keySet());
            ids.addAll(s.keySet());
            for (int id : ids) {
                double hb = b.getOrDefault(id, 0.0), hs = s.getOrDefault(id, 0.0);
                if (Math.abs(hb - hs) > 1e-6) lines.add(String.format("%s: %s %.1fh -> %.1fh", d, titleOf(id, baseline, simulated), hb, hs));
            }
        }
        return lines;
    }

    private Map<LocalDate, Map<Integer, Double>> hoursByDay(StudySchedule s) {
        Map<LocalDate, Map<Integer, Double>> map = new TreeMap<>();
        for (ScheduleEntry e : s.getEntries())
            map.computeIfAbsent(e.getDate(), k -> new TreeMap<>()).merge(e.getTaskId(), e.getHours(), Double::sum);
        return map;
    }

    private String titleOf(int taskId, StudySchedule a, StudySchedule b) {
        TaskForecast f = b.getForecast(taskId);
        if (f == null) f = a.getForecast(taskId);
        return f == null ? "task #" + taskId : f.getTitle();
    }

    // ------------------------------------------------------------------
    private List<String> suggest(ImpactAnalysis a, StudySchedule simulated) {
        List<String> out = new ArrayList<>();
        for (DeadlineConflict c : a.getDeadlineConflicts()) {
            out.add(String.format("Free up about %.1fh before %s (add study time or move other work): %.1fh of work is due by then but only %.1fh are available.",
                    roundUpToHalf(c.getShortfall()), c.getDate(), c.getRequiredHours(), c.getAvailableHours()));
        }
        for (TaskForecast f : a.getDelayed()) {
            out.add(String.format("[#%d] %s could still make %s: study it before lower-priority work, or add about %.1fh before the deadline.",
                    f.getTaskId(), f.getTitle(), f.getDeadline(), roundUpToHalf(f.getHoursLate() + f.getHoursUnscheduled())));
        }
        for (TaskForecast f : a.getImpossible()) {
            if (f.getDeadline().isBefore(a.getStartDate())) {
                out.add(String.format("[#%d] %s is already past its deadline: finish it as soon as possible or ask for an extension.",
                        f.getTaskId(), f.getTitle()));
            } else {
                out.add(String.format("[#%d] %s cannot be finished by %s in this scenario: consider an extension, or avoid the change that removes its study time.",
                        f.getTaskId(), f.getTitle(), f.getDeadline()));
            }
        }
        double lost = a.getOriginalAvailableHours() - a.getSimulatedAvailableHours();
        if (lost > EPS && a.hasProblems()) {
            long days = ChronoUnit.DAYS.between(a.getStartDate(), a.getWindowEnd()) + 1;
            out.add(String.format("This scenario removes %.1fh of study time. Adding about %.1fh per day over the %d days until the last deadline would recover it.",
                    lost, lost / days, days));
        }
        if (a.hasProblems()) {
            List<TaskForecast> onTrack = a.getOnTrack();                  // already ordered highest priority first
            if (onTrack.size() > 1) {
                TaskForecast lowest = onTrack.get(onTrack.size() - 1);
                out.add(String.format("Consider postponing [#%d] %s (lowest priority, due %s) to free time for the at-risk tasks.",
                        lowest.getTaskId(), lowest.getTitle(), lowest.getDeadline()));
            }
        }
        if (out.isEmpty()) out.add("No changes needed: every task stays on track in this outcome.");
        return out;
    }

    private double roundUpToHalf(double hours) { return Math.ceil(hours * 2 - EPS) / 2.0; }
}
