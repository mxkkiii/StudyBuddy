#!/usr/bin/env python3
"""
StudyBuddy weekly progress report.

Usage:  python3 weekly_report.py <csv_folder> [--no-chart]

Reads the CSV files exported by the Java app (weekly_summary.csv, weekly_subject_hours.csv,
weekly_sessions.csv), prints a text report, saves it as weekly_report.txt and, when matplotlib is
installed, draws a bar chart of study hours per subject (weekly_hours.png).
The last line printed is always "CHART: <path>" or "CHART: not generated (<reason>)".
Exit code: 0 = success, 2 = bad input.
"""
import csv
import os
import sys

SUMMARY_FILE = "weekly_summary.csv"
SUBJECT_HOURS_FILE = "weekly_subject_hours.csv"
SESSIONS_FILE = "weekly_sessions.csv"
REPORT_FILE = "weekly_report.txt"
CHART_FILE = "weekly_hours.png"
BAR_WIDTH = 30


def read_rows(path):
    with open(path, newline="", encoding="utf-8") as f:
        return list(csv.DictReader(f))


def build_report(summary, subject_hours, sessions):
    lines = []
    bar = "=" * 56
    lines.append(bar)
    lines.append(" STUDYBUDDY WEEKLY PROGRESS REPORT")
    lines.append(" Student : %s" % summary["studentName"])
    lines.append(" Period  : %s to %s" % (summary["weekStart"], summary["weekEnd"]))
    lines.append(bar)
    lines.append("Total study hours : %.1f" % float(summary["totalHours"]))
    lines.append("Completed tasks   : %s" % summary["completedTasks"])
    lines.append("Pending tasks     : %s" % summary["pendingTasks"])
    lines.append("Overdue tasks     : %s" % summary["overdueTasks"])
    lines.append("")
    lines.append("Study hours per subject")
    lines.append("-" * 56)
    if not subject_hours:
        lines.append("  (no subjects)")
    else:
        top = max([hours for _, hours in subject_hours] + [0.0])
        width = max(len(name) for name, _ in subject_hours)
        for name, hours in subject_hours:
            filled = int(round(BAR_WIDTH * hours / top)) if top > 0 else 0
            lines.append("  %-*s %5.1f  %s" % (width, name, hours, "#" * filled))
    lines.append("")
    lines.append("Study sessions (%d)" % len(sessions))
    lines.append("-" * 56)
    if not sessions:
        lines.append("  No study sessions were recorded in this period.")
    for s in sessions:
        note = " - " + s["notes"] if s.get("notes") else ""
        lines.append("  %s  %-14s %-24s %4.1fh%s" % (
            s["date"], s["subject"], s["taskTitle"], float(s["hours"]), note))
    lines.append(bar)
    return "\n".join(lines)


def make_chart(subject_hours, title, out_path):
    """Returns (path, None) on success or (None, reason) when no chart could be made."""
    if not subject_hours:
        return None, "no subjects"
    try:
        import matplotlib
        matplotlib.use("Agg")                       # no window needed
        import matplotlib.pyplot as plt
    except ImportError:
        return None, "matplotlib is not installed"
    names = [n for n, _ in subject_hours]
    hours = [h for _, h in subject_hours]
    fig, ax = plt.subplots(figsize=(8, 5))
    bars = ax.bar(names, hours, color="#4C78A8")
    ax.set_ylabel("Study hours")
    ax.set_title(title)
    ax.set_ylim(0, max(max(hours) * 1.15, 1.0))
    for rect, value in zip(bars, hours):
        ax.annotate("%.1f" % value, (rect.get_x() + rect.get_width() / 2, value),
                    ha="center", va="bottom")
    plt.setp(ax.get_xticklabels(), rotation=20, ha="right")
    fig.tight_layout()
    fig.savefig(out_path, dpi=120)
    plt.close(fig)
    return out_path, None


def main(argv):
    args = [a for a in argv[1:] if not a.startswith("--")]
    no_chart = "--no-chart" in argv
    if len(args) != 1:
        print("Usage: python3 weekly_report.py <csv_folder> [--no-chart]")
        return 2
    folder = args[0]
    try:
        summary_rows = read_rows(os.path.join(folder, SUMMARY_FILE))
        if not summary_rows:
            raise ValueError("%s has no data row" % SUMMARY_FILE)
        summary = summary_rows[0]
        subject_hours = [(r["subject"], float(r["hours"]))
                         for r in read_rows(os.path.join(folder, SUBJECT_HOURS_FILE))]
        sessions = read_rows(os.path.join(folder, SESSIONS_FILE))
        report = build_report(summary, subject_hours, sessions)
    except FileNotFoundError as e:
        print("ERROR: missing file: %s" % e.filename)
        return 2
    except (KeyError, ValueError) as e:
        print("ERROR: the CSV data is not in the expected format (%s)" % e)
        return 2

    print(report)
    try:
        with open(os.path.join(folder, REPORT_FILE), "w", encoding="utf-8") as f:
            f.write(report + "\n")
    except OSError as e:
        print("(could not save %s: %s)" % (REPORT_FILE, e))

    if no_chart:
        print("CHART: not generated (disabled)")
        return 0
    title = "Study hours per subject (%s to %s)" % (summary["weekStart"], summary["weekEnd"])
    path, reason = make_chart(subject_hours, title, os.path.abspath(os.path.join(folder, CHART_FILE)))
    print("CHART: %s" % path if path else "CHART: not generated (%s)" % reason)
    return 0


if __name__ == "__main__":
    sys.stdout.reconfigure(encoding="utf-8")
    sys.exit(main(sys.argv))
