package nexus.telemetry;

import nexus.routing.CircuitBreaker;
import java.util.concurrent.atomic.AtomicLong;

public class TelemetryDashboard {
    private final CircuitBreaker circuitBreaker;
    private final Thread worker;
    private volatile boolean running = true;
    
    private final AtomicLong requestsRouted = new AtomicLong(0);
    private final AtomicLong requestsBlocked = new AtomicLong(0);
    private final long startTime;

    public TelemetryDashboard(CircuitBreaker circuitBreaker) {
        this.circuitBreaker = circuitBreaker;
        this.startTime = System.currentTimeMillis();
        
        worker = new Thread(() -> {
            while (running) {
                try {
                    Thread.sleep(5000);
                    printDashboard();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }, "TelemetryDashboard-Thread");
        worker.start();
    }
    
    public void incrementRouted() {
        requestsRouted.incrementAndGet();
    }
    
    public void incrementBlocked() {
        requestsBlocked.incrementAndGet();
    }

    private void printDashboard() {
        System.out.print("\033[H\033[2J");
        System.out.flush();
        long uptime = (System.currentTimeMillis() - startTime) / 1000;
        
        System.out.println("=========================================");
        System.out.println("        NEXUS GATEWAY TELEMETRY          ");
        System.out.println("=========================================");
        System.out.println("Uptime (s)       : " + uptime);
        System.out.println("Requests Routed  : " + requestsRouted.get());
        System.out.println("Requests Blocked : " + requestsBlocked.get());
        System.out.println("Node Status      : " + circuitBreaker.getDashboardStatus());
        System.out.println("=========================================");
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
