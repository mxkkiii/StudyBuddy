package studybuddy.storage;

import studybuddy.model.Student;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** What FileManager.load() found: the student (or null when nothing was saved yet) plus any problems. */
public class LoadResult {
    private Student student;
    private final List<String> warnings = new ArrayList<>();
    private int subjectsLoaded, tasksLoaded, sessionsLoaded;

    void setStudent(Student student) { this.student = student; }
    void warn(String message) { warnings.add(message); }
    void countSubject() { subjectsLoaded++; }
    void countTask() { tasksLoaded++; }
    void countSession() { sessionsLoaded++; }

    /** The loaded student, or null when no saved data exists (start a new one). */
    public Student getStudent() { return student; }
    public boolean isEmpty() { return student == null; }
    public List<String> getWarnings() { return Collections.unmodifiableList(warnings); }
    public boolean hasWarnings() { return !warnings.isEmpty(); }
    public int getSubjectsLoaded() { return subjectsLoaded; }
    public int getTasksLoaded() { return tasksLoaded; }
    public int getSessionsLoaded() { return sessionsLoaded; }

    @Override public String toString() {
        return isEmpty() ? "No saved data found."
                : "Loaded " + subjectsLoaded + " subjects, " + tasksLoaded + " tasks, " + sessionsLoaded
                + " sessions" + (hasWarnings() ? " (" + warnings.size() + " warnings)" : "") + ".";
    }
}
