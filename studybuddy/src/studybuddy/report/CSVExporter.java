package studybuddy.report;

import studybuddy.exception.DataStorageException;
import studybuddy.exception.InvalidInputException;
import studybuddy.model.*;
import studybuddy.scheduling.ScheduleEntry;
import studybuddy.scheduling.StudySchedule;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Writes CSV files for spreadsheets and for the Python report. (This is an EXPORT for reading;
 * the app's own save files are written by FileManager.)
 */
public class CSVExporter {
    public static final String SUMMARY_FILE = "weekly_summary.csv";
    public static final String SUBJECT_HOURS_FILE = "weekly_subject_hours.csv";
    public static final String SESSIONS_FILE = "weekly_sessions.csv";

    private final Path directory;

    public CSVExporter(Path directory) { this.directory = directory; }

    /** Overload: folder given as text. */
    public CSVExporter(String directory) { this(Paths.get(directory)); }

    public Path getDirectory() { return directory; }

    /** Generic writer: any list of Exportable objects, with an explicit header (so empty lists still get one). */
    public <T extends Exportable> Path writeCsv(String fileName, String header, List<T> rows) throws DataStorageException {
        List<String> lines = new ArrayList<>();
        lines.add(header);
        for (T row : rows) lines.add(row.toCsvLine());
        return writeLines(fileName, lines);
    }

    // ----- full exports -----
    public Path exportSubjects(StudyPlanner planner) throws DataStorageException {
        return writeCsv("export_subjects.csv", Subject.CSV_HEADER, planner.getAllSubjects());
    }

    public Path exportTasks(StudyPlanner planner) throws DataStorageException {
        return writeCsv("export_tasks.csv", StudyItem.CSV_HEADER, planner.getTasksByDeadline());
    }

    public Path exportSessions(StudyPlanner planner) throws DataStorageException {
        return writeCsv("export_sessions.csv", StudySession.CSV_HEADER, new ArrayList<>(planner.getSessions()));
    }

    public Path exportSchedule(StudySchedule schedule) throws DataStorageException {
        return writeCsv("export_schedule.csv", ScheduleEntry.CSV_HEADER, new ArrayList<>(schedule.getEntries()));
    }

    /** Subjects, tasks and sessions in one call. */
    public List<Path> exportAll(StudyPlanner planner) throws DataStorageException {
        List<Path> files = new ArrayList<>();
        files.add(exportSubjects(planner));
        files.add(exportTasks(planner));
        files.add(exportSessions(planner));
        return files;
    }

    // ----- weekly report (input for weekly_report.py) -----
    public List<Path> exportWeeklyReport(ProgressReport report) throws DataStorageException, InvalidInputException {
        if (report == null) throw new InvalidInputException("A report is required.");
        List<Path> files = new ArrayList<>();

        List<ProgressReport> summary = new ArrayList<>();
        summary.add(report);
        files.add(writeCsv(SUMMARY_FILE, ProgressReport.CSV_HEADER, summary));

        List<String> subjectLines = new ArrayList<>();
        subjectLines.add("subject,hours");
        for (Map.Entry<String, Double> e : report.getHoursPerSubject().entrySet()) {
            subjectLines.add(Exportable.escape(e.getKey()) + "," + e.getValue());
        }
        files.add(writeLines(SUBJECT_HOURS_FILE, subjectLines));

        files.add(writeCsv(SESSIONS_FILE, StudySession.CSV_HEADER, new ArrayList<>(report.getSessions())));
        return files;
    }

    private Path writeLines(String fileName, List<String> lines) throws DataStorageException {
        try {
            Files.createDirectories(directory);
            Path file = directory.resolve(fileName);
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(file.toFile(), StandardCharsets.UTF_8))) {
                for (String line : lines) {
                    writer.write(line);
                    writer.newLine();
                }
            }
            return file;
        } catch (IOException e) {
            throw new DataStorageException("Could not write \"" + fileName + "\" to \"" + directory + "\": " + e.getMessage());
        }
    }
}
