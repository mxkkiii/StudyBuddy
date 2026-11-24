package studybuddy.report;

import java.nio.file.Path;

/** What happened when the Python report script ran. */
public class PythonReportResult {
    private final int exitCode;
    private final String output;
    private final Path chartPath;      // null when no chart was produced

    PythonReportResult(int exitCode, String output, Path chartPath) {
        this.exitCode = exitCode;
        this.output = output;
        this.chartPath = chartPath;
    }

    public int getExitCode() { return exitCode; }
    public boolean isSuccess() { return exitCode == 0; }
    /** Everything the script printed (the text report, or an error message). */
    public String getOutput() { return output; }
    public boolean hasChart() { return chartPath != null; }
    public Path getChartPath() { return chartPath; }
}
