package studybuddy.exception;

public class TaskNotFoundException extends StudyBuddyException {
    public TaskNotFoundException(int taskId) { super("Task with ID " + taskId + " was not found."); }
    public TaskNotFoundException(String message) { super(message); }
}
