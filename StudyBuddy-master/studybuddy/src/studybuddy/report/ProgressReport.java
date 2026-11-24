package studybuddy.report;

import studybuddy.exception.InvalidInputException;
import studybuddy.model.Exportable;
import studybuddy.model.Student;
import studybuddy.model.StudyPlanner;
import studybuddy.model.StudySession;

import java.time.LocalDate;
import java.util.*;

/**
 * A snapshot of the student's progress between two dates (inclusive), taken when the object is created.
 * Task counts are the CURRENT numbers (completed / pending / overdue); study hours and sessions are
 * limited to the period.
 */
public class ProgressReport implements Exportable {
    public static final String CSV_HEADER = "studentName,weekStart,weekEnd,totalHours,completedTasks,pendingTasks,overdueTasks";

    private final String studentName;
    private final LocalDate startDate;
    private final LocalDate endDate;
    private final double totalHours;
    private final Map<String, Double> hoursPerSubject;
    private final int completedTasks;
    private final int pendingTasks;
    private final int overdueTasks;
    private final List<StudySession> sessions;

    public ProgressReport(Student student, LocalDate startDate, LocalDate endDate, LocalDate today)
            throws InvalidInputException {
        if (student == null) throw new InvalidInputException("A student is required.");
        if (startDate == null || endDate == null) throw new InvalidInputException("Start and end dates are required.");
        if (endDate.isBefore(startDate)) throw new InvalidInputException("End date cannot be before the start date.");
        StudyPlanner planner = student.getPlanner();
        this.studentName = student.getName();
        this.startDate = startDate;
        this.endDate = endDate;
        this.totalHours = planner.getTotalStudyHours(startDate, endDate);
        this.hoursPerSubject = new LinkedHashMap<>(planner.getStudyHoursPerSubject(startDate, endDate));
        this.completedTasks = planner.getCompletedTasks().size();
        this.pendingTasks = planner.getPendingTasks(today).size();
        this.overdueTasks = planner.getOverdueTasks(today).size();
        List<StudySession> list = new ArrayList<>(planner.getSessionsBetween(startDate, endDate));
        list.sort(Comparator.comparing(StudySession::getDate).thenComparingInt(StudySession::getId));
        this.sessions = Collections.unmodifiableList(list);
    }

    /** Overload: "today" is the real current date. */
    public ProgressReport(Student student, LocalDate startDate, LocalDate endDate) throws InvalidInputException {
        this(student, startDate, endDate, LocalDate.now());
    }

    /** The 7 days ending today (today and the 6 days before it). */
    public static ProgressReport lastSevenDays(Student student) throws InvalidInputException {
        LocalDate today = LocalDate.now();
        return new ProgressReport(student, today.minusDays(6), today, today);
    }

    /** Seven days beginning on the given date. */
    public static ProgressReport forWeekStarting(Student student, LocalDate start) throws InvalidInputException {
        if (start == null) throw new InvalidInputException("Start date is required.");
        return new ProgressReport(student, start, start.plusDays(6));
    }

    public String getStudentName() { return studentName; }
    public LocalDate getStartDate() { return startDate; }
    public LocalDate getEndDate() { return endDate; }
    public double getTotalHours() { return totalHours; }
    public Map<String, Double> getHoursPerSubject() { return Collections.unmodifiableMap(hoursPerSubject); }
    public int getCompletedTasks() { return completedTasks; }
    public int getPendingTasks() { return pendingTasks; }
    public int getOverdueTasks() { return overdueTasks; }
    public List<StudySession> getSessions() { return sessions; }

    @Override public String getCsvHeader() { return CSV_HEADER; }

    @Override public String toCsvLine() {
        return Exportable.escape(studentName) + "," + startDate + "," + endDate + "," + totalHours + ","
                + completedTasks + "," + pendingTasks + "," + overdueTasks;
    }

    /** Plain-text version of the report (the Python script produces a similar one plus the chart). */
    public String render() {
        StringBuilder sb = new StringBuilder();
        String bar = "========================================================";
        sb.append(bar).append('\n');
        sb.append(" STUDYBUDDY WEEKLY PROGRESS REPORT\n");
        sb.append(" Student : ").append(studentName).append('\n');
        sb.append(" Period  : ").append(startDate).append(" to ").append(endDate).append('\n');
        sb.append(bar).append('\n');
        sb.append(String.format("Total study hours : %.1f%n", totalHours));
        sb.append("Completed tasks   : ").append(completedTasks).append('\n');
        sb.append("Pending tasks     : ").append(pendingTasks).append('\n');
        sb.append("Overdue tasks     : ").append(overdueTasks).append("\n\n");
        sb.append("Study hours per subject\n");
        for (Map.Entry<String, Double> e : hoursPerSubject.entrySet()) {
            sb.append(String.format("  %-16s %5.1f%n", e.getKey(), e.getValue()));
        }
        sb.append("\nStudy sessions (").append(sessions.size()).append(")\n");
        if (sessions.isEmpty()) sb.append("  No study sessions were recorded in this period.\n");
        for (StudySession s : sessions) sb.append("  ").append(s).append('\n');
        sb.append(bar).append('\n');
        return sb.toString();
    }

    @Override public String toString() { return render(); }
}
