import jdk.jfr.consumer.*;
import java.io.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;

/** Offline, aggregate-only JFR analysis; never prints event payloads or credentials. */
class AnalyzeRestBottleneck {
    static class Stats {
        long count; double total, max; String example = "";
        void add(RecordedEvent e, double ms, String thread, String stack, String detail) {
            count++; total += ms;
            if (ms > max) { max=ms; example=e.getStartTime()+"\t"+e.getEndTime()+"\t"+thread+"\t"+detail+"\t"+stack; }
        }
    }
    static String operation(List<String> frames) {
        for (String frame:frames) {
            for (String method:List.of("page","detail","topRankings","labels"))
                if (frame.contains("GuestbookQueryService") && frame.endsWith("."+method)) return method;
            if (frame.contains("GuestbookCommandService")) return "like";
            if (frame.contains("MemberCommandService")) return "member_command";
        }
        return "other";
    }
    public static void main(String[] args) throws Exception {
        if (args.length!=4) throw new IllegalArgumentException("recording.jfr body-start-ISO body-end-ISO output-prefix");
        Instant start=Instant.parse(args[1]), end=Instant.parse(args[2]), boundary=start.plusSeconds(30);
        Map<String,Stats> totals=new TreeMap<>(); Map<String,Long> counts=new TreeMap<>();
        List<String> gc=new ArrayList<>();
        try (RecordingFile recording=new RecordingFile(Path.of(args[0]))) {
            while (recording.hasMoreEvents()) {
                var e=recording.readEvent(); String type=e.getEventType().getName();
                counts.merge(type,1L,Long::sum);
                if (e.getStartTime().isBefore(start)||e.getStartTime().isAfter(end)) continue;
                if (type.equals("jdk.GCPhasePause")) {
                    gc.add(e.getStartTime()+"\t"+e.getEndTime()+"\t"+e.getDuration().toNanos()/1e6); continue;
                }
                if (!Set.of("jdk.ThreadPark","jdk.JavaMonitorEnter","jdk.SocketRead","jdk.SocketWrite","jdk.FileWrite").contains(type)) continue;
                var thread=e.getThread();String threadName=thread==null?"":thread.getJavaName();
                if (!threadName.startsWith("http-nio-8080-exec-")) continue;
                List<String> frames=new ArrayList<>();
                if (e.getStackTrace()!=null) for (var f:e.getStackTrace().getFrames())
                    frames.add(f.getMethod().getType().getName()+"."+f.getMethod().getName());
                String stack=String.join("|",frames), category="other", detail="";
                if (stack.contains("org.apache.tomcat.util.threads.TaskQueue.poll")) category="idle_worker";
                else if (type.equals("jdk.ThreadPark") && (stack.contains("ConcurrentBag.borrow")||stack.contains("HikariPool.getConnection"))) category="pool_acquire_wait";
                else if (stack.contains("HikariPool.getConnection")) category="pool_acquire_other";
                else if (stack.contains("ch.qos.logback")&&type.equals("jdk.FileWrite")) {
                    String path=e.getString("path");detail=path==null?"":Path.of(path).getFileName().toString();
                    category=stack.contains("ConsoleAppender")||stack.contains("ConsoleTarget")||stack.contains("PrintStream")?"console_write":"log_file_write";
                } else if (stack.contains("ch.qos.logback")) category="logging_lock";
                else if ((type.equals("jdk.SocketRead")||type.equals("jdk.SocketWrite"))&&e.getInt("port")==3306) {
                    if (stack.contains(".doBegin")) category="jdbc_begin";
                    else if (stack.contains(".doCommit")) category="jdbc_commit";
                    else if (stack.contains(".doCleanupAfterCompletion")) category="jdbc_cleanup";
                    else category="jdbc_query_or_other";
                }
                if (type.equals("jdk.JavaMonitorEnter") && e.hasField("previousOwner")) {
                    var owner=e.getThread("previousOwner");
                    if (owner!=null) detail+=" owner="+owner.getJavaName();
                }
                String phase=e.getStartTime().isBefore(boundary)?"first30":"steady";
                String key=phase+"\t"+type+"\t"+category+"\t"+operation(frames);
                totals.computeIfAbsent(key,k->new Stats()).add(e,e.getDuration().toNanos()/1e6,threadName,stack,detail);
            }
        }
        try (var summary=new PrintWriter(Files.newBufferedWriter(Path.of(args[3]+"-summary.tsv")));
             var examples=new PrintWriter(Files.newBufferedWriter(Path.of(args[3]+"-examples.tsv")))) {
            summary.println("phase\tevent\tcategory\toperation\tcount\tsummed_thread_ms\tmax_ms");
            examples.println("phase\tevent\tcategory\toperation\tduration_ms\tstart_utc\tend_utc\tthread\tdetail\tstack");
            for(var entry:totals.entrySet()) {
                var s=entry.getValue();
                summary.printf(Locale.ROOT,"%s\t%d\t%.3f\t%.3f%n",entry.getKey(),s.count,s.total,s.max);
                examples.printf(Locale.ROOT,"%s\t%.3f\t%s%n",entry.getKey(),s.max,s.example);
            }
        }
        Files.write(Path.of(args[3]+"-gc.tsv"),gc);
        Files.write(Path.of(args[3]+"-event-counts.tsv"),counts.entrySet().stream().map(e->e.getKey()+"\t"+e.getValue()).toList());
        System.out.println("Grouped "+totals.size()+" event/phase/operation combinations; GC pauses "+gc.size());
        for (String name:List.of("jdk.DataLoss","jdk.InitialEnvironmentVariable","jdk.InitialSystemProperty","jdk.JVMInformation"))
            System.out.println(name+"="+counts.getOrDefault(name,0L));
    }
}
