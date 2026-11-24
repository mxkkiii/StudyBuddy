package studybuddy.model;

/** Anything that can be written as one line of a CSV file. */
public interface Exportable {
    String getCsvHeader();
    String toCsvLine();

    /** Wraps a value in quotes when it contains commas, quotes or line breaks. */
    static String escape(String value) {
        if (value == null) return "";
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}
