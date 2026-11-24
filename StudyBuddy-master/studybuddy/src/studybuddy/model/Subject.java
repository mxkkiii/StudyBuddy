package studybuddy.model;

import studybuddy.exception.InvalidInputException;
import studybuddy.exception.TaskNotFoundException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** A course/subject. Owns (composition) the list of StudyItems that belong to it. */
public class Subject implements Exportable {
    public static final String CSV_HEADER = "id,name,description,taskCount";
    private final int id;
    private String name;
    private String description;
    private final List<StudyItem> tasks = new ArrayList<>();

    public Subject(int id, String name, String description) throws InvalidInputException {
        checkName(name);
        this.id = id;
        this.name = name.trim();
        this.description = description == null ? "" : description.trim();
    }

    /** Overload: description is optional. */
    public Subject(int id, String name) throws InvalidInputException {
        this(id, name, "");
    }

    public static void checkName(String name) throws InvalidInputException {
        if (name == null || name.trim().isEmpty()) throw new InvalidInputException("Subject name cannot be empty.");
    }

    // ----- task management -----
    public void addTask(StudyItem task) throws InvalidInputException {
        if (task == null) throw new InvalidInputException("Task cannot be null.");
        if (task.isSimulationCopy())
            throw new InvalidInputException("Temporary simulation copies cannot be added to a real subject.");
        for (StudyItem t : tasks) {
            if (t.getId() == task.getId()) throw new InvalidInputException("A task with ID " + task.getId() + " already exists.");
        }
        task.setSubject(this);
        tasks.add(task);
    }

    public StudyItem findTask(int taskId) throws TaskNotFoundException {
        for (StudyItem t : tasks) if (t.getId() == taskId) return t;
        throw new TaskNotFoundException(taskId);
    }

    public StudyItem removeTask(int taskId) throws TaskNotFoundException {
        StudyItem task = findTask(taskId);
        tasks.remove(task);
        task.setSubject(null);
        return task;
    }

    /** Used when the whole subject is deleted: every task is detached and returned to the caller. */
    public List<StudyItem> clearTasks() {
        List<StudyItem> removed = new ArrayList<>(tasks);
        for (StudyItem t : removed) t.setSubject(null);
        tasks.clear();
        return removed;
    }

    public List<StudyItem> getTasks() { return Collections.unmodifiableList(tasks); }

    public List<StudyItem> getUnfinishedTasks() {
        List<StudyItem> result = new ArrayList<>();
        for (StudyItem t : tasks) if (t.isUnfinished()) result.add(t);
        return result;
    }

    public double getTotalRemainingHours() {
        double total = 0;
        for (StudyItem t : tasks) total += t.getRemainingHours();
        return total;
    }

    // ----- getters / setters -----
    public int getId() { return id; }
    public String getName() { return name; }
    public String getDescription() { return description; }

    public void setName(String name) throws InvalidInputException {
        checkName(name);
        this.name = name.trim();
    }

    public void setDescription(String description) {
        this.description = description == null ? "" : description.trim();
    }

    // ----- Exportable -----
    @Override public String getCsvHeader() { return CSV_HEADER; }

    @Override public String toCsvLine() {
        return id + "," + Exportable.escape(name) + "," + Exportable.escape(description) + "," + tasks.size();
    }

    @Override public String toString() {
        return "[#" + id + "] " + name + (description.isEmpty() ? "" : " - " + description) + " (" + tasks.size() + " tasks)";
    }
}
