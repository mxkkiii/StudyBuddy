package studybuddy.scheduling;

import studybuddy.model.PlanAdjustments;
import studybuddy.model.StudyItem;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

/** Extra rules for the scheduler: tasks that may not start before a date, and an optional focus subject. */
public class ScheduleOptions {
    private final Map<Integer, LocalDate> notBefore = new HashMap<>();
    private final Integer focusSubjectId;
    private final double focusShare;          // 0..1

    private ScheduleOptions(Map<Integer, LocalDate> notBefore, Integer focusSubjectId, double focusShare) {
        this.notBefore.putAll(notBefore);
        this.focusSubjectId = focusSubjectId;
        this.focusShare = focusShare;
    }

    /** No extra rules: the plain scheduler. */
    public static ScheduleOptions none() { return new ScheduleOptions(new HashMap<>(), null, 0); }

    public static ScheduleOptions of(PlanAdjustments adjustments) {
        if (adjustments == null) return none();
        Integer focus = adjustments.getFocusSubjectId();
        return new ScheduleOptions(adjustments.getPostponements(), focus,
                focus == null ? 0 : adjustments.getFocusSharePercent() / 100.0);
    }

    /** Earliest allowed date for the task, or null. */
    public LocalDate getNotBefore(int taskId) { return notBefore.get(taskId); }

    public boolean hasFocus() { return focusSubjectId != null; }

    public double getFocusShare() { return focusShare; }

    public boolean isFocusTask(StudyItem task) {
        return focusSubjectId != null && task.getSubject() != null && task.getSubject().getId() == focusSubjectId;
    }
}
