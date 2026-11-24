package studybuddy.exception;

/** Thrown when data cannot be saved (disk full, folder not writable, refusing to save simulation data...). */
public class DataStorageException extends StudyBuddyException {
    public DataStorageException(String message) { super(message); }
}
