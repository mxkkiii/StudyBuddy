package studybuddy.storage;

import studybuddy.exception.InvalidInputException;

import java.util.ArrayList;
import java.util.List;

/** Reads one CSV line (the reverse of Exportable.escape): handles quoted fields, commas and doubled quotes. */
public final class CsvUtil {
    private CsvUtil() { }

    public static List<String> parseLine(String line) throws InvalidInputException {
        List<String> fields = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < line.length() && line.charAt(i + 1) == '"') { current.append('"'); i++; }
                    else inQuotes = false;
                } else {
                    current.append(c);
                }
            } else if (c == '"') {
                inQuotes = true;
            } else if (c == ',') {
                fields.add(current.toString());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        if (inQuotes) throw new InvalidInputException("unterminated quote");
        fields.add(current.toString());
        return fields;
    }
}
