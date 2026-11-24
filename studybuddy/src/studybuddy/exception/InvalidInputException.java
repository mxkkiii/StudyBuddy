package studybuddy.exception;

/** Generic bad input (blank title, difficulty out of range, null value, ...). */
public class InvalidInputException extends StudyBuddyException {
    public InvalidInputException(String message) { super(message); }
}
