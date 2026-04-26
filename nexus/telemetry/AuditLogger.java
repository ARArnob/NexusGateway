package nexus.telemetry;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;

public class AuditLogger {
    private final LinkedBlockingQueue<String> queue = new LinkedBlockingQueue<>();
    private final Thread worker;
    private volatile boolean running = true;

    public AuditLogger() {
        worker = new Thread(() -> {
            try (FileWriter fw = new FileWriter("nexus-access.log", true);
                 BufferedWriter bw = new BufferedWriter(fw);
                 PrintWriter out = new PrintWriter(bw)) {
                 
                while (running || !queue.isEmpty()) {
                    try {
                        String log = queue.take();
                        out.println(log);
                        out.flush();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
                
                List<String> remaining = new ArrayList<>();
                queue.drainTo(remaining);
                for (String log : remaining) {
                    out.println(log);
                }
                out.flush();

            } catch (IOException e) {
                e.printStackTrace();
            }
        }, "AuditLogger-Thread");
        worker.start();
    }

    public void log(String message) {
        queue.offer(message);
    }

    public void shutdown() {
        running = false;
        worker.interrupt();
        try {
            worker.join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
