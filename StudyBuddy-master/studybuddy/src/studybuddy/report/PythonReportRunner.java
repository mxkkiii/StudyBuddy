package studybuddy.report;

import studybuddy.exception.ReportException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/** Starts the Python 3 report script with ProcessBuilder and captures what it prints. */
public class PythonReportRunner {
    public static final int DEFAULT_TIMEOUT_SECONDS = 30;
    private static final String CHART_PREFIX = "CHART:";

    private final Path script;
    private final int timeoutSeconds;
    private String pythonCommand;            // null = try python3, python, then py
    private boolean chartEnabled = true;

    public PythonReportRunner(Path script) { this(script, DEFAULT_TIMEOUT_SECONDS); }

    /** Overload: custom time limit. */
    public PythonReportRunner(Path script, int timeoutSeconds) {
        this.script = script;
        this.timeoutSeconds = Math.max(1, timeoutSeconds);
    }

    /** Overload: script path as text, e.g. new PythonReportRunner("python/weekly_report.py"). */
    public PythonReportRunner(String script) { this(Paths.get(script)); }

    public void setPythonCommand(String command) { this.pythonCommand = command; }
    public void setChartEnabled(boolean enabled) { this.chartEnabled = enabled; }

    /** Runs the script on a folder that already holds the exported weekly CSV files. */
    public PythonReportResult run(Path csvDirectory) throws ReportException {
        if (!Files.isRegularFile(script)) throw new ReportException("Report script not found: " + script);
        if (!Files.isDirectory(csvDirectory)) throw new ReportException("CSV folder not found: " + csvDirectory);

        List<String> candidates = new ArrayList<>();
        if (pythonCommand != null) candidates.add(pythonCommand);
        else { candidates.add("python3"); candidates.add("python"); candidates.add("py"); }

        for (String python : candidates) {
            List<String> command = new ArrayList<>();
            command.add(python);
            command.add(script.toString());
            command.add(csvDirectory.toString());
            if (!chartEnabled) command.add("--no-chart");
            try {
                return execute(command);
            } catch (IOException notFound) {
                // this Python name does not exist on the computer: try the next one
            }
        }
        throw new ReportException("Python 3 was not found. Install Python 3 (and optionally matplotlib) to use the weekly report.");
    }

    private PythonReportResult execute(List<String> command) throws IOException, ReportException {
        ProcessBuilder builder = new ProcessBuilder(command);
        builder.redirectErrorStream(true);                             // errors appear in the same output
        builder.environment().put("PYTHONIOENCODING", "utf-8");
        builder.environment().put("MPLCONFIGDIR", System.getProperty("java.io.tmpdir") + "/studybuddy-mpl");
        Process process = builder.start();                              // IOException here = command not found

        StringBuilder output = new StringBuilder();
        Thread reader = new Thread(() -> {
            try (InputStream in = process.getInputStream()) {
                output.append(new String(in.readAllBytes(), StandardCharsets.UTF_8));
            } catch (IOException ignored) { }
        });
        reader.start();

        try {
            if (!process.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                throw new ReportException("The report script took longer than " + timeoutSeconds + " seconds and was stopped.");
            }
            reader.join(2000);
        } catch (InterruptedException e) {
            process.destroyForcibly();
            Thread.currentThread().interrupt();
            throw new ReportException("The report was interrupted.");
        }

        String text = output.toString();            // safe: the reader thread has finished (join above)
        return new PythonReportResult(process.exitValue(), text, findChart(text));
    }

    /** The script's last "CHART:" line tells us where the picture is (or that there is none). */
    private Path findChart(String text) {
        Path chart = null;
        for (String line : text.split("\\R")) {
            if (line.startsWith(CHART_PREFIX)) {
                String value = line.substring(CHART_PREFIX.length()).trim();
                chart = (value.startsWith("not generated") || value.isEmpty()) ? null : Paths.get(value);
            }
        }
        return chart;
    }
}
