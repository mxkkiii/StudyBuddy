import studybuddy.exception.*;
import studybuddy.model.*;
import studybuddy.scheduling.*;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class Section3Test {
    static int passed = 0, failed = 0;
    static final LocalDate START = LocalDate.of(2026, 11, 23);   // a Monday

    interface Action { void run() throws Exception; }

    static void check(String name, boolean ok) {
        if (ok) passed++; else failed++;
        System.out.println((ok ? "  PASS  " : "  FAIL  ") + name);
    }

    static void expectThrows(String name, Class<? extends Exception> type, Action a) {
        try { a.run(); check(name + " (no exception)", false); }
        catch (Exception e) { check(name, type.isInstance(e)); }
    }

    static String day(StudySchedule s, int offset) {
        StringBuilder sb = new StringBuilder();
        for (ScheduleEntry e : s.getEntriesForDate(START.plusDays(offset)))
            sb.append('#').append(e.getTaskId()).append(':').append(e.getHours()).append(' ');
        return sb.toString().trim();
    }

    public static void main(String[] args) throws Exception {
        PriorityScheduler scheduler = new PriorityScheduler();

        System.out.println("== DailyAvailability ==");
        DailyAvailability av = new DailyAvailability(4);
        check("Default hours", av.getHours(START) == 4);
        av.setHours(START.plusDays(1), 0);
        check("Override day = 0", av.getHours(START.plusDays(1)) == 0);
        av.setHoursForRange(START.plusDays(3), START.plusDays(4), 2);
        check("Range set", av.getHours(START.plusDays(3)) == 2 && av.getHours(START.plusDays(4)) == 2);
        av.addHoursForRange(START, START, 2);
        check("addHoursForRange adds", av.getHours(START) == 6);
        av.addHoursForRange(START, START, -100);
        check("addHoursForRange clamps at 0", av.getHours(START) == 0);
        check("getTotalHours(day0..day4) = 0+0+4+2+2", av.getTotalHours(START, START.plusDays(4)) == 8);
        DailyAvailability copy = av.copy();
        copy.setHours(START.plusDays(2), 9);
        check("copy() is independent", av.getHours(START.plusDays(2)) == 4);
        expectThrows("25h/day rejected", InvalidStudyHoursException.class, () -> new DailyAvailability(25));
        expectThrows("negative override rejected", InvalidStudyHoursException.class, () -> av.setHours(START, -1));
        expectThrows("bad range rejected", InvalidInputException.class, () -> av.setHoursForRange(START.plusDays(2), START, 1));

        System.out.println("== Basic schedule (4h/day) ==");
        Exam exam = new Exam(1, "Calculus Midterm", START.plusDays(3), 8, 4);       // score 21
        Quiz quiz = new Quiz(2, "Quiz 2", START.plusDays(1), 2, 2);                  // score 17
        Assignment asg = new Assignment(3, "Java Lab", START.plusDays(12), 4, 3);    // score 11
        ReadingTask read = new ReadingTask(4, "Read Ch. 7", START.plusDays(20), 1.5, 1); // score 3
        List<StudyItem> tasks = new ArrayList<>(List.of(read, asg, quiz, exam));      // deliberately unordered
        double examScoreBefore = exam.getPriorityScore();

        StudySchedule s = scheduler.generate(tasks, START, new DailyAvailability(4));
        System.out.println(s.render());
        check("Day 0: exam 4h", day(s, 0).equals("#1:4.0"));
        check("Day 1: exam 4h", day(s, 1).equals("#1:4.0"));
        check("Day 2: quiz 2h then lab 2h", day(s, 2).equals("#2:2.0 #3:2.0"));
        check("Day 3: lab 2h then reading 1.5h", day(s, 3).equals("#3:2.0 #4:1.5"));
        check("Total scheduled = 15.5h", s.getTotalScheduledHours() == 15.5);
        check("End date is day 3", s.getEndDate().equals(START.plusDays(3)));
        boolean withinLimits = true;
        for (LocalDate d : s.getScheduledDates()) if (s.getScheduledHours(d) > s.getAvailableHours(d) + 1e-9) withinLimits = false;
        check("No day exceeds available hours", withinLimits);
        check("Exam on track", s.getForecast(1).getFeasibility() == Feasibility.ON_TRACK
                && s.getForecast(1).getCompletionDate().equals(START.plusDays(1)));
        check("Quiz DELAYED (fits if reordered, but exam outranks it)", s.getForecast(2).getFeasibility() == Feasibility.DELAYED);
        check("Lab and reading on track", s.getForecast(3).isOnTrack() && s.getForecast(4).isOnTrack());
        check("Exactly one warning, about the quiz", s.getWarnings().size() == 1 && s.getWarnings().get(0).contains("Quiz 2"));
        check("Schedule not feasible overall", !s.isFeasible());
        check("Hours per task add up", s.getHoursPerTask().get(1) == 8.0 && s.getHoursPerTask().get(3) == 4.0);
        check("render() mentions WARNINGS", s.render().contains("WARNINGS"));

        System.out.println("== Real tasks are never modified ==");
        check("Exam remaining/score unchanged", exam.getRemainingHours() == 8 && exam.getPriorityScore() == examScoreBefore);
        check("Exam status unchanged", exam.getStatus() == TaskStatus.PENDING);

        System.out.println("== Skipped day and carry-over ==");
        DailyAvailability skip = new DailyAvailability(4);
        skip.setHours(START.plusDays(1), 0);
        StudySchedule s2 = scheduler.generate(tasks, START, skip);
        check("No study planned on the skipped day", s2.getEntriesForDate(START.plusDays(1)).isEmpty());
        check("Work carried forward, same total", s2.getTotalScheduledHours() == 15.5);
        check("Skipped day shows in render", s2.render().contains("no study time"));

        System.out.println("== Impossible task ==");
        Exam big = new Exam(10, "Huge Exam", START.plusDays(2), 20, 5);
        StudySchedule s3 = scheduler.generate(List.of(big), START, new DailyAvailability(4));
        TaskForecast f3 = s3.getForecast(10);
        check("20h exam in 3 days at 4h/day is IMPOSSIBLE", f3.getFeasibility() == Feasibility.IMPOSSIBLE);
        check("All 20h still placed (late)", s3.getTotalScheduledHours() == 20 && f3.getHoursLate() == 8 && f3.getHoursOnTime() == 12);
        check("Message explains the shortage", f3.getMessage().contains("needs 20.0h") && f3.getMessage().contains("only 12.0h"));

        System.out.println("== Overdue task ==");
        Assignment overdue = new Assignment(11, "Missed Report", START.minusDays(2), 3, 3);
        StudySchedule s4 = scheduler.generate(List.of(read, overdue), START, new DailyAvailability(4));
        check("Overdue task scheduled first", s4.getEntriesForDate(START).get(0).getTaskId() == 11);
        check("Overdue task flagged IMPOSSIBLE", s4.getForecast(11).getFeasibility() == Feasibility.IMPOSSIBLE
                && s4.getForecast(11).getMessage().contains("past its deadline"));
        check("Its entries are marked late", s4.getEntriesForTask(11).get(0).isLate());

        System.out.println("== Horizon limit / zero hours ==");
        StudySchedule s5 = scheduler.generate(List.of(read), START, new DailyAvailability(0));
        check("0h/day: nothing scheduled, no infinite loop", s5.isEmpty() && s5.getForecast(4).getHoursUnscheduled() == 1.5);
        check("0h/day: flagged IMPOSSIBLE", s5.getForecast(4).getFeasibility() == Feasibility.IMPOSSIBLE
                || s5.getForecast(4).getFeasibility() == Feasibility.DELAYED);
        ReadingTask marathon = new ReadingTask(12, "Marathon", START.plusDays(100), 50, 3);
        StudySchedule s6 = new PriorityScheduler(10).generate(List.of(marathon), START, new DailyAvailability(1));
        check("10-day horizon places only 10h", s6.getTotalScheduledHours() == 10 && s6.getForecast(12).getHoursUnscheduled() == 40);
        check("Unscheduled task has no completion date", s6.getForecast(12).getCompletionDate() == null);
        check("Warning mentions the horizon", s6.getWarnings().get(0).contains("within 10 days"));

        System.out.println("== Filtering ==");
        Assignment done = new Assignment(13, "Done", START.plusDays(2), 3, 2);
        done.markCompleted();
        Assignment zero = new Assignment(14, "Zero left", START.plusDays(2), 3, 2);
        zero.logHours(3);
        StudySchedule s7 = scheduler.generate(List.of(done, zero, read), START, new DailyAvailability(4));
        check("Completed and 0-hour tasks ignored", s7.getHoursPerTask().size() == 1 && s7.getHoursPerTask().containsKey(4));
        StudySchedule empty = scheduler.generate(new ArrayList<StudyItem>(), START, new DailyAvailability(4));
        check("Empty input -> empty feasible schedule", empty.isEmpty() && empty.isFeasible());
        expectThrows("Null start rejected", InvalidInputException.class, () -> scheduler.generate(tasks, null, new DailyAvailability(4)));
        expectThrows("Null availability rejected", InvalidInputException.class, () -> scheduler.generate(tasks, START, null));

        System.out.println("== Planner integration + regeneration ==");
        Student student = new Student("Maria");
        StudyPlanner p = student.getPlanner();
        LocalDate today = LocalDate.now();
        p.addSubject("Math");
        p.addSubject("Programming");
        p.addExam(1, "Midterm", today.plusDays(5), 8, 4, null);
        p.addAssignment(2, "Lab", today.plusDays(9), 4, 3);
        StudySchedule ps = scheduler.generate(p);
        check("Planner schedule covers 12h and starts today", ps.getTotalScheduledHours() == 12 && ps.getStartDate().equals(today));
        check("Daily limit follows planner setting (4h)", ps.getScheduledHours(today) == 4);
        p.logSession(1, 3, "Studied");
        StudySchedule regenerated = scheduler.generate(p);
        check("Regenerating after a session: 9h left to schedule", regenerated.getTotalScheduledHours() == 9);
        p.setDailyAvailableHours(2);
        check("Changing daily hours changes the plan", scheduler.generate(p).getEndDate().isAfter(regenerated.getEndDate()));
        check("Real task only reduced by the logged session", p.findTask(1).getRemainingHours() == 5);

        check("ScheduleEntry CSV row", s.getEntries().get(0).toCsvLine().startsWith("2026-11-23,1,Calculus Midterm,(no subject),4.0,2026-11-26,false"));

        System.out.println("\nRESULT: " + passed + " passed, " + failed + " failed");
        if (failed > 0) System.exit(1);
    }
}
