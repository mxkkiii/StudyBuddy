package studybuddy;

import studybuddy.app.ConsoleMenu;
import studybuddy.app.DemoRunner;
import studybuddy.storage.FileManager;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Scanner;

/**
 * StudyBuddy console launcher.
 *   java -cp out studybuddy.Main              interactive menu (data folder "data")
 *   java -cp out studybuddy.Main <folder>     interactive menu using another data folder
 *   java -cp out studybuddy.Main demo         scripted demonstration (uses a temporary folder; your data is untouched)
 * Run it from the project folder so that python/weekly_report.py can be found.
 */
public class Main {
    public static void main(String[] args) throws IOException {
        Path script = Paths.get("python", "weekly_report.py");
        if (args.length > 0 && args[0].equalsIgnoreCase("demo")) {
            Path work = Files.createTempDirectory("studybuddy-demo");
            boolean ok = new DemoRunner(System.out, work, script).run();
            System.exit(ok ? 0 : 1);
        }
        FileManager files = new FileManager(args.length > 0 ? args[0] : "data");
        new ConsoleMenu(new Scanner(System.in), System.out, files, script, Paths.get("export")).run();
    }
}
