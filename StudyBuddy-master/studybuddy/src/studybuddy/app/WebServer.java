package studybuddy.app;

import studybuddy.exception.SubjectNotFoundException;
import studybuddy.model.*;
import studybuddy.storage.FileManager;
import studybuddy.storage.LoadResult;

import java.awt.Desktop;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDate;
import java.util.*;

public class WebServer {
    private final FileManager fileManager;
    private Student student;
    private final StudyPlanner planner;

    public WebServer(FileManager fileManager) {
        this.fileManager = fileManager;
        LoadResult loaded = fileManager.load();
        if (loaded.getStudent() != null) {
            this.student = loaded.getStudent();
        } else {
            try {
                this.student = new Student("Student", "");
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }
        this.planner = this.student.getPlanner();
        this.planner.refreshAll(LocalDate.now());
    }

    public void start(int port) throws IOException {
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            String url = "http://localhost:" + port;
            System.out.println(
                    "StudyBuddy Web UI running at " + url + " (saving to \"" + fileManager.getDirectory() + "\")");
            System.out.println("Press Ctrl+C in this terminal to stop the server.");
            if (Desktop.isDesktopSupported()) {
                try {
                    Desktop.getDesktop().browse(new URI(url));
                } catch (Exception ignored) {
                }
            }
            while (true) {
                Socket client = serverSocket.accept();
                new Thread(() -> handleClient(client)).start();
            }
        }
    }

    private void handleClient(Socket client) {
        try (Socket s = client;
                InputStream in = s.getInputStream();
                OutputStream out = s.getOutputStream()) {

            String requestLine = readLine(in);
            if (requestLine == null || requestLine.isEmpty())
                return;
            String[] parts = requestLine.split(" ");
            if (parts.length < 2)
                return;
            String method = parts[0];
            String path = parts[1];

            int contentLength = 0;
            String header;
            while ((header = readLine(in)) != null && !header.isEmpty()) {
                if (header.toLowerCase().startsWith("content-length:")) {
                    contentLength = Integer.parseInt(header.substring(15).trim());
                }
            }

            String body = "";
            if (contentLength > 0) {
                byte[] buf = in.readNBytes(contentLength);
                body = new String(buf, StandardCharsets.UTF_8);
            }

            if ("GET".equalsIgnoreCase(method) && "/".equals(path)) {
                Path htmlPath = Paths.get("studybuddy_sunset_ui.html");
                if (!Files.exists(htmlPath)) {
                    sendResponse(out, 404, "text/plain",
                            "studybuddy_sunset_ui.html not found.".getBytes(StandardCharsets.UTF_8));
                } else {
                    sendResponse(out, 200, "text/html", Files.readAllBytes(htmlPath));
                }
            } else if ("GET".equalsIgnoreCase(method) && "/api/state".equals(path)) {
                synchronized (planner) {
                    planner.refreshAll(LocalDate.now());
                    sendJson(out, 200, buildStateJson());
                }
            } else if ("POST".equalsIgnoreCase(method) && "/api/tasks/add".equals(path)) {
                synchronized (planner) {
                    try {
                        Map<String, String> p = parseForm(body);
                        String title = p.getOrDefault("title", "Untitled Task");
                        String course = p.getOrDefault("course", "GENERAL").toUpperCase();
                        String typeStr = p.getOrDefault("type", "ASSIGNMENT");
                        int difficulty = Integer.parseInt(p.getOrDefault("difficulty", "3"));
                        double hours = Double.parseDouble(p.getOrDefault("hours", "2.0"));
                        LocalDate deadline = LocalDate.parse(p.get("deadline"));

                        Subject subject;
                        try {
                            subject = planner.findSubject(course);
                        } catch (SubjectNotFoundException e) {
                            subject = planner.addSubject(course);
                        }

                        planner.addTask(subject.getId(), mapTaskType(typeStr), title, deadline, hours, difficulty);
                        fileManager.save(student);
                        sendJson(out, 200, buildStateJson());
                    } catch (Exception e) {
                        sendJson(out, 400, "{\"error\":\"" + esc(e.getMessage()) + "\"}");
                    }
                }
            } else if ("POST".equalsIgnoreCase(method) && "/api/tasks/toggle".equals(path)) {
                synchronized (planner) {
                    try {
                        Map<String, String> p = parseForm(body);
                        int id = Integer.parseInt(p.get("id"));
                        StudyItem item = planner.findTask(id);
                        if (item.isUnfinished()) {
                            planner.markTaskCompleted(id);
                        } else {
                            planner.updateTaskStatus(id, TaskStatus.PENDING);
                            item.setRemainingHours(item.getEstimatedHours());
                        }
                        planner.refreshAll(LocalDate.now());
                        fileManager.save(student);
                        sendJson(out, 200, buildStateJson());
                    } catch (Exception e) {
                        sendJson(out, 400, "{\"error\":\"" + esc(e.getMessage()) + "\"}");
                    }
                }
            } else if ("POST".equalsIgnoreCase(method) && "/api/tasks/delete".equals(path)) {
                synchronized (planner) {
                    try {
                        Map<String, String> p = parseForm(body);
                        int id = Integer.parseInt(p.get("id"));
                        planner.deleteTask(id);
                        fileManager.save(student);
                        sendJson(out, 200, buildStateJson());
                    } catch (Exception e) {
                        sendJson(out, 400, "{\"error\":\"" + esc(e.getMessage()) + "\"}");
                    }
                }
            } else if ("POST".equalsIgnoreCase(method) && "/api/profile".equals(path)) {
                synchronized (planner) {
                    try {
                        Map<String, String> p = parseForm(body);
                        String name = p.getOrDefault("studentName", "Student").trim();
                        if (!name.isEmpty()) {
                            student.setName(name);
                            fileManager.save(student);
                        }
                        sendJson(out, 200, buildStateJson());
                    } catch (Exception e) {
                        sendJson(out, 400, "{\"error\":\"" + esc(e.getMessage()) + "\"}");
                    }
                }
            } else {
                sendResponse(out, 404, "text/plain", "Not Found".getBytes(StandardCharsets.UTF_8));
            }
        } catch (Exception ignored) {
        }
    }

    private String readLine(InputStream in) throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        int b;
        while ((b = in.read()) != -1) {
            if (b == '\n')
                break;
            if (b != '\r')
                buf.write(b);
        }
        if (b == -1 && buf.size() == 0)
            return null;
        return buf.toString(StandardCharsets.UTF_8);
    }

    private void sendJson(OutputStream out, int code, String json) throws IOException {
        sendResponse(out, code, "application/json", json.getBytes(StandardCharsets.UTF_8));
    }

    private void sendResponse(OutputStream out, int code, String contentType, byte[] body) throws IOException {
        String status = code == 200 ? "OK" : (code == 400 ? "Bad Request" : "Not Found");
        String headers = "HTTP/1.1 " + code + " " + status + "\r\n"
                + "Content-Type: " + contentType + "; charset=UTF-8\r\n"
                + "Content-Length: " + body.length + "\r\n"
                + "Connection: close\r\n\r\n";
        out.write(headers.getBytes(StandardCharsets.UTF_8));
        out.write(body);
        out.flush();
    }

    private TaskType mapTaskType(String raw) {
        try {
            return TaskType.fromString(raw);
        } catch (Exception e) {
            String u = raw.trim().toUpperCase();
            if (u.contains("EXAM")) return TaskType.EXAM;
            if (u.contains("QUIZ")) return TaskType.QUIZ;
            if (u.contains("READ")) return TaskType.values()[TaskType.values().length - 1];
            return TaskType.ASSIGNMENT;
        }
    }
    private String buildStateJson() {
        List<StudyItem> all = planner.getAllTasks();
        StringBuilder sb = new StringBuilder();
        sb.append("{\"studentName\":\"").append(esc(student.getName())).append("\",\"tasks\":[");
        for (int i = 0; i < all.size(); i++) {
            StudyItem t = all.get(i);
            if (i > 0)
                sb.append(",");
            long daysLeft = t.getDaysLeft(LocalDate.now());
            int urgency = (int) Math.max(1, Math.min(5, 6 - Math.max(0, daysLeft)));
            sb.append("{")
                    .append("\"id\":").append(t.getId()).append(",")
                    .append("\"title\":\"").append(esc(t.getTitle())).append("\",")
                    .append("\"course\":\"").append(esc(t.getSubjectName())).append("\",")
                    .append("\"type\":\"").append(esc(t.getTaskType().getLabel())).append("\",")
                    .append("\"urgency\":").append(urgency).append(",")
                    .append("\"difficulty\":").append(t.getDifficulty()).append(",")
                    .append("\"hours\":").append(t.getRemainingHours()).append(",")
                    .append("\"estimatedHours\":").append(t.getEstimatedHours()).append(",")
                    .append("\"priorityScore\":").append(t.getPriorityScore()).append(",")
                    .append("\"deadline\":\"").append(t.getDeadline()).append("\",")
                    .append("\"completed\":").append(!t.isUnfinished())
                    .append("}");
        }
        sb.append("]}");
        return sb.toString();
    }

    private static Map<String, String> parseForm(String body) {
        Map<String, String> map = new HashMap<>();
        if (body == null || body.isEmpty())
            return map;
        for (String pair : body.split("&")) {
            int idx = pair.indexOf('=');
            if (idx > 0) {
                String k = URLDecoder.decode(pair.substring(0, idx), StandardCharsets.UTF_8);
                String v = URLDecoder.decode(pair.substring(idx + 1), StandardCharsets.UTF_8);
                map.put(k, v);
            }
        }
        return map;
    }

    private static String esc(String s) {
        if (s == null)
            return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ").replace("\r", " ");
    }
}