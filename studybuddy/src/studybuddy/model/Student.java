package studybuddy.model;

import studybuddy.exception.InvalidInputException;

/** The user of the app. A Student HAS-A StudyPlanner (composition): the planner lives and dies with the student. */
public class Student {
    private String name;
    private String course;
    private final StudyPlanner planner = new StudyPlanner();

    public Student(String name, String course) throws InvalidInputException {
        checkName(name);
        this.name = name.trim();
        this.course = course == null ? "" : course.trim();
    }

    /** Overload: course is optional. */
    public Student(String name) throws InvalidInputException { this(name, ""); }

    private static void checkName(String name) throws InvalidInputException {
        if (name == null || name.trim().isEmpty()) throw new InvalidInputException("Student name cannot be empty.");
    }

    public String getName() { return name; }
    public String getCourse() { return course; }
    public StudyPlanner getPlanner() { return planner; }

    public void setName(String name) throws InvalidInputException {
        checkName(name);
        this.name = name.trim();
    }

    public void setCourse(String course) { this.course = course == null ? "" : course.trim(); }

    @Override public String toString() { return name + (course.isEmpty() ? "" : " (" + course + ")"); }
}
