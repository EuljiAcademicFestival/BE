import jdk.jfr.consumer.*;
import java.io.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;

/** Offline JFR analysis. Does not attach to or change the running application. */
class AnalyzeJfr {
    static class Stats {
        long count;
        double total, max;
        void add(double ms) { count++; total += ms; max = Math.max(max, ms); }
    }
    public static void main(String[] args) throws Exception {
        if (args.length != 4) throw new IllegalArgumentException("recording.jfr start-ISO end-ISO output-prefix");
        Instant start = Instant.parse(args[1]), end = Instant.parse(args[2]);
        Map<String, Stats> totals = new TreeMap<>();
        Map<String, Stats> stacks = new HashMap<>();
        long read = 0, selected = 0;
        try (RecordingFile recording = new RecordingFile(Path.of(args[0]));
             PrintWriter events = new PrintWriter(Files.newBufferedWriter(Path.of(args[3] + "-events.tsv")))) {
            events.println("start_utc\tend_utc\tthread\tevent\tcategory\tduration_ms\toperation\tstack");
            while (recording.hasMoreEvents()) {
                RecordedEvent e = recording.readEvent(); read++;
                if (e.getStartTime().isBefore(start) || e.getStartTime().isAfter(end)) continue;
                String type = e.getEventType().getName();
                boolean pause = type.equals("jdk.GCPhasePause");
                if (!pause && !Set.of("jdk.ThreadPark", "jdk.JavaMonitorEnter", "jdk.SocketRead", "jdk.SocketWrite", "jdk.FileWrite").contains(type)) continue;
                RecordedThread thread = e.getThread();
                String threadName = thread == null ? "" : thread.getJavaName();
                if (!pause && !threadName.startsWith("http-nio-8080-exec-")) continue;
                List<String> frames = new ArrayList<>();
                if (e.getStackTrace() != null) for (RecordedFrame frame : e.getStackTrace().getFrames()) {
                    var method = frame.getMethod();
                    frames.add(method.getType().getName() + "." + method.getName());
                }
                String stack = String.join("|", frames);
                String category;
                if (pause) category = "gc_pause";
                else if (stack.contains("org.apache.tomcat.util.threads.TaskQueue.poll")) category = "idle_worker";
                else if (stack.contains("ConcurrentBag.borrow") || stack.contains("HikariPool.getConnection")) category = "pool_acquire_wait";
                else if (stack.contains("ch.qos.logback") && type.equals("jdk.FileWrite")) {
                    String path = e.getString("path");
                    category = (path == null && stack.contains("PrintStream")) || stack.contains("ConsoleAppender")
                            || stack.contains("ConsoleTarget") ? "console_write" : "log_file_write";
                }
                else if (stack.contains("ch.qos.logback")) category = "logging_lock";
                else if ((type.equals("jdk.SocketRead") || type.equals("jdk.SocketWrite")) && e.getInt("port") == 3306) {
                    if (stack.contains(".doBegin")) category = "jdbc_transaction_begin";
                    else if (stack.contains(".doCommit")) category = "jdbc_commit";
                    else if (stack.contains(".doCleanupAfterCompletion")) category = "jdbc_transaction_cleanup";
                    else category = "jdbc_query_or_other";
                } else category = "other";
                String operation = stack.contains("MemberAuthenticationService") ? "authenticate"
                        : stack.contains("GuestbookQueryService") ? "guestbook_query"
                        : stack.contains("GuestbookCommandService") ? "like"
                        : stack.contains("MemberCommandService") ? "member_command" : "unknown";
                double ms = e.getDuration().toNanos() / 1_000_000.0;
                totals.computeIfAbsent(type + "\t" + category, k -> new Stats()).add(ms);
                stacks.computeIfAbsent(type + "\t" + category + "\t" + operation + "\t" + stack, k -> new Stats()).add(ms);
                events.printf(Locale.ROOT, "%s\t%s\t%s\t%s\t%s\t%.6f\t%s\t%s%n",
                        e.getStartTime(), e.getEndTime(), threadName, type, category, ms, operation, stack);
                selected++;
            }
        }
        try (PrintWriter out = new PrintWriter(Files.newBufferedWriter(Path.of(args[3] + "-summary.tsv")))) {
            out.println("event\tcategory\tcount\tsummed_thread_ms\tmax_ms");
            for (var entry : totals.entrySet()) {
                var s = entry.getValue();
                out.printf(Locale.ROOT, "%s\t%d\t%.3f\t%.3f%n", entry.getKey(), s.count, s.total, s.max);
            }
        }
        try (PrintWriter out = new PrintWriter(Files.newBufferedWriter(Path.of(args[3] + "-stacks.tsv")))) {
            out.println("event\tcategory\toperation\tstack\tcount\tsummed_thread_ms\tmax_ms");
            stacks.entrySet().stream().sorted((a,b) -> Double.compare(b.getValue().total, a.getValue().total)).limit(100)
                    .forEach(entry -> { var s=entry.getValue(); out.printf(Locale.ROOT,"%s\t%d\t%.3f\t%.3f%n",entry.getKey(),s.count,s.total,s.max); });
        }
        System.out.println("Read " + read + " events; selected " + selected + " in the requested time range.");
    }
}
