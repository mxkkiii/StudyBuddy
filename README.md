# StudyBuddy: Personal Study Planner and Progress Tracker
 
**OOP 101 – Object-Oriented Programming** | CS/IT Department | 1st Semester, AY 2026–2027
**Batangas State University – The National Engineering University, Alangilan Campus**
College of Informatics and Computing Sciences
 
---
 
## Project Title
 
**StudyBuddy: Personal Study Planner and Progress Tracker**
 
## Project Description
 
StudyBuddy is a console-based Java application that puts all of a student's subjects, tasks, and deadlines in one organized planner. Each task has a type (assignment, quiz, exam, or reading), a deadline, estimated hours, and a difficulty level. The program ranks tasks using a rule-based priority score, builds a day-by-day study schedule from the hours the student has available, and tracks progress over time.
 
It also includes a **What-If Scenario** feature that lets students test postponing tasks *before* committing, showing the effect on deadlines and daily workload without changing the real plan.
 
## Problem Being Addressed
 
College students juggle assignments, quizzes, projects, readings, and exams across several subjects at once. Most track them in scattered places such as notebooks, sticky notes, group chats, or memory, which leads to:
 
- Missed deadlines and tasks noticed too late
- Last-minute cramming instead of steady daily study
- Difficulty deciding what to study first when a short quiz and a big project compete for the same hours
- No visibility into how many hours are actually spent per subject, so neglected subjects go unnoticed until grades drop
- No way to see what happens when a task is postponed. Pushing a task to "tomorrow" quietly removes a day from the time left and piles more hours onto the remaining days
Existing planner and to-do apps are general-purpose: they do not rank academic tasks by type and urgency, build a realistic schedule from available study hours, or let students test a postponement first.
 
## Project Objectives
 
- Organize all subjects, tasks, and deadlines in one place
- Rank tasks by priority using task type, deadline proximity, and difficulty
- Generate a realistic day-by-day study schedule from the student's daily available hours, and warn about tasks that cannot be finished in time
- Alert the student about tasks that are near or past their deadline
- Let students simulate postponing tasks (What-If Scenario) and see the effects before applying them
- Record study sessions and completed tasks, and produce a weekly progress report
- Persist data to local files so the plan is still there on the next run
- Demonstrate core OOP concepts in a practical, student-focused application

**Sustainable Development Goals:**
- **SDG 4 (Target 4.3):** equal access to quality tertiary education, by helping students manage coursework and finish their studies
- **SDG 3 (Target 3.4):** mental health and well-being, by reducing stress from last-minute cramming and missed deadlines
  
## Target Users
 
College students who need a simple, student-focused tool to plan, prioritize, and track their academic work.
 
## Main Features
 
- **Subject and task management:** add subjects and tasks (assignment, quiz, exam, reading) with deadline, estimated hours, and difficulty
- **Input validation:** invalid deadlines are rejected with an error and the student is asked again
- **Priority ranking:** rule-based score ranks every task from highest to lowest priority
- **Study schedule generator:** enter your daily available hours and get a plan for each day; large tasks are split across several days
- **Deadline alerts:** list of tasks that are near or past their deadline
- **Task completion and session logging:** mark tasks as done and log study sessions
- **What-If Scenario:** choose one or more tasks, enter how many days to postpone each, and see a before-and-after comparison of deadlines and daily workload, a risk level (LOW, MEDIUM, HIGH), and the extra hours per day needed to catch up. Then apply or discard the scenario
- **Weekly progress report:** a Python script reads the exported data and shows completed tasks, hours per subject, and a bar chart
- **Data persistence:** save and reload data from text/CSV files
## Technologies Used
 
| Technology | Purpose |
| --- | --- |
| **Java** | Main application (console interface; Swing/JavaFX GUI if time allows) |
| **Python 3** | Weekly progress report and charts, called from Java through `ProcessBuilder` |
| **CSV / text files** | Local data storage via `FileWriter` and `BufferedReader` |
| **Git and GitHub** | Version control and team collaboration |
 
## Team Members and Roles
 
**Team Name:** Voltes Five (Team 5) | **Section:** CS-2105
 
| No. | Sr-Code | Name | Duties / Responsibility |
| --- | --- | --- | --- |
| 1 | 25-09524 | Buendia, Beverly Rose T. | Project Leader |
| 2 | 25-07947 | Dalisay, Chris David L. | Documentation |
| 3 | 25-08084 | Holgado, Mark Joseph P. | Lead Programmer |
 
## OOP Concepts Demonstrated
 
| Concept | How it is used in StudyBuddy |
| --- | --- |
| **Encapsulation** | `Student`, `Subject`, and `StudyItem` keep fields private and expose them through getters and setters. Setters validate input (e.g., rejecting a past deadline or estimated time ≤ 0). |
| **Abstraction** | Abstract class `StudyItem` defines what every task has (title, subject, deadline, status) and declares abstract methods such as `calculatePriority()` and `getDetails()`. |
| **Inheritance** | `Assignment`, `Quiz`, `Exam`, and `ReadingTask` extend `StudyItem` and add their own fields (e.g., `Exam` adds coverage topics, `Assignment` adds a submission format). |
| **Polymorphism** | Each subclass overrides `calculatePriority()`. The planner stores every task as a `StudyItem` and calls the same method on all of them, so one scheduling loop handles every task type. |
| **Interfaces** | `Schedulable` (`getDeadline()`, `getEstimatedHours()`) and `Exportable` (`toCsvLine()`) are implemented by task classes and `Subject` where needed. |
| **Composition / Association** | A `Student` has a `StudyPlanner`; the planner contains `Subject`s, and each subject contains its `StudyItem`s. A `StudySession` is associated with the `StudyItem` it was spent on. |
| **Method overloading and exception handling** | `addTask()` is overloaded (title and deadline; with estimated hours; with hours and priority). Custom exceptions such as `InvalidDeadlineException`, `TaskNotFoundException`, and `InvalidPostponementException` handle bad input and missing records. |
| **Collections, enums, and File I/O** | `ArrayList`, `HashMap`, and `PriorityQueue` organize tasks. Enums define `TaskType`, `Priority`, `Status`, and `RiskLevel`. Data is saved and loaded using `FileWriter` and `BufferedReader`. |
| **Other language (Python)** | A Python script produces the weekly progress report from the exported CSV file. |
| **What-If simulation** | `WhatIfSimulator` copies tasks through a copy constructor, applies postponements to the copies only, reruns the same scheduler and overridden `calculatePriority()` methods, and returns a `ScenarioResult` with the before/after workload, late tasks, and a `RiskLevel`. |
 
## Instructions for Running the Program
 
**Requirements**
- Java JDK 17 or newer
- Python 3 (with `matplotlib` for the bar chart: `pip install matplotlib`)
  
**Steps**
 
1. Clone the repository:
```bash
git clone https://github.com/mxkkiii/StudyBuddy.git
cd StudyBuddy
```
2. Compile the Java source files:
```bash
javac -d out src/*.java
```
3. Run the program:
```bash
java -cp out studybuddy.Main
```
4. Use the main menu to add subjects and tasks, generate a schedule, run a What-If Scenario, log sessions, and view the progress report. Choose **Save and Exit** so your data is reloaded on the next run.
## Sample Screenshots
 
_Screenshots will be added once the program is running._
 
## Known Limitations
 
- The priority score is a simple rule-based guide, not a guarantee of the best study plan
- Supports a **single user per data file**
- Deadline alerts are **in-app only** (no phone or email notifications)
- Does not connect to school systems such as the LMS or class schedules
- No cloud sync; data is stored in local files only
- What-If results are only an estimate based on the hours, deadlines, and postponement values entered. They do not predict real-life events, and they never change the saved plan unless the student chooses to apply them
- StudyBuddy is a planning aid and does not replace the student's own judgment
  
## Future Improvements
 
- Swing/JavaFX graphical user interface
- Notifications for upcoming deadlines (email or mobile)
- Multi-user support
- Integration with class schedules or an LMS
- Cloud sync across devices
## Project Contributors
 
| Name | Role | GitHub |
| --- | --- | --- |
| Beverly Rose T. Buendia | Project Leader | [@bevsss18](https://github.com/bevsss18) |
| Chris David L. Dalisay | Documentation | [@sirchdvdl](https://github.com/sirchdvdl) |
| Mark Joseph P. Holgado | Lead Programmer | [@mxkkiii](https://github.com/mxkkiii) |
 
---
 
*Leading Innovations, Transforming Lives, Building the Nation*
 
