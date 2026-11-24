package studybuddy.model;

import java.time.LocalDate;

/** Anything the scheduler can place on a study plan. */
public interface Schedulable {
    int getId();
    String getTitle();
    LocalDate getDeadline();
    double getEstimatedHours();
    double getRemainingHours();
    double getPriorityScore();
    /** True while the item still needs study time. */
    boolean isUnfinished();
}
