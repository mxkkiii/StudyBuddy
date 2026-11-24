package studybuddy.exception;

public class SubjectNotFoundException extends StudyBuddyException {
    public SubjectNotFoundException(int subjectId) { super("Subject with ID " + subjectId + " was not found."); }
    public SubjectNotFoundException(String message) { super(message); }
}
