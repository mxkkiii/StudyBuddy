package studybuddy.exception;

/** Base class of all StudyBuddy checked exceptions, so callers can catch them all in one place. */
public class StudyBuddyException extends Exception {
    public StudyBuddyException(String message) { super(message); }
}
