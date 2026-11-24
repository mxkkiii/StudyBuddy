package studybuddy.exception;

/** Thrown when the report cannot be produced (Python missing, script not found, timeout...). */
public class ReportException extends StudyBuddyException {
    public ReportException(String message) { super(message); }
}
