package studybuddy.simulation;

import studybuddy.model.StudyItem;
import studybuddy.model.StudyPlanner;
import studybuddy.model.StudySession;
import studybuddy.model.TaskType;

import java.util.*;

/**
 * How long the student REALLY takes compared with the estimate, per task type, learned from completed
 * tasks that have logged study sessions. With too few samples the factor is 1.0 (= use the estimate).
 */
public class DurationHistory {
    private static final double MIN_FACTOR = 0.25;
    private static final double MAX_FACTOR = 4.0;

    private final Map<TaskType, List<Double>> ratios = new EnumMap<>(TaskType.class);
    private final int minSamples;
    private final boolean enabled;

    public DurationHistory(StudyPlanner planner, SimulationAssumptions assumptions) {
        this.minSamples = assumptions.getMinHistorySamples();
        this.enabled = assumptions.isUseHistory();
        for (TaskType type : TaskType.values()) ratios.put(type, new ArrayList<>());
        for (StudyItem task : planner.getCompletedTasks()) {
            double actual = 0;
            for (StudySession s : planner.getSessions()) if (s.getTask().getId() == task.getId()) actual += s.getHours();
            if (actual > 0 && task.getEstimatedHours() > 0) ratios.get(task.getTaskType()).add(actual / task.getEstimatedHours());
        }
    }

    public int getSampleCount(TaskType type) { return ratios.get(type).size(); }

    public boolean hasEnoughData(TaskType type) { return enabled && getSampleCount(type) >= minSamples; }

    /** Typical actual/estimated ratio (average of the samples), or 1.0 when there is not enough data. */
    public double getFactor(TaskType type) {
        if (!hasEnoughData(type)) return 1.0;
        double sum = 0;
        for (double r : ratios.get(type)) sum += r;
        return Math.max(MIN_FACTOR, Math.min(MAX_FACTOR, sum / ratios.get(type).size()));
    }

    /** One readable line per task type. */
    public List<String> describe() {
        List<String> lines = new ArrayList<>();
        for (TaskType type : TaskType.values()) {
            if (hasEnoughData(type)) {
                lines.add(String.format("%s: you typically need x%.2f of the estimate (%d completed tasks)",
                        type.getLabel(), getFactor(type), getSampleCount(type)));
            } else {
                lines.add(String.format("%s: not enough history (%d of %d needed), using your estimate",
                        type.getLabel(), getSampleCount(type), minSamples));
            }
        }
        return lines;
    }
}
