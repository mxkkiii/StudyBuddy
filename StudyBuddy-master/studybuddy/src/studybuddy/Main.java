package studybuddy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Scanner;
import studybuddy.app.ConsoleMenu;
import studybuddy.app.DemoRunner;
import studybuddy.app.WebServer;
import studybuddy.storage.FileManager;

public class Main {
    public static void main(String[] args) throws IOException {
        Path script = Paths.get("python", "weekly_report.py");
        if (args.length > 0 && args[0].equalsIgnoreCase("demo")) {
            Path work = Files.createTempDirectory("studybuddy-demo");
            boolean ok = new DemoRunner(System.out, work, script).run();
            System.exit(ok ? 0 : 1);
        }
        FileManager files = new FileManager("data");
        if (args.length > 0 && args[0].equalsIgnoreCase("console")) {
            new ConsoleMenu(new Scanner(System.in), System.out, files, script, Paths.get("export")).run();
            return;
        }
        new WebServer(files).start(8080);
    }
}