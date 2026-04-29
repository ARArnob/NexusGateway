package nexus.core;

import nexus.config.ConfigManager;
import nexus.routing.CircuitBreaker;
import nexus.routing.ClientHandler;
import nexus.routing.Router;
import nexus.security.RateLimiter;
import nexus.telemetry.AuditLogger;
import nexus.telemetry.TelemetryDashboard;

import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class Gateway {
    private static final int PORT = 8000;
    private static volatile boolean running = true;

    public static void main(String[] args) {
        CircuitBreaker circuitBreaker = new CircuitBreaker();
        Router router = new Router(circuitBreaker);
        RateLimiter rateLimiter = new RateLimiter(5);
        AuditLogger auditLogger = new AuditLogger();
        TelemetryDashboard telemetryDashboard = new TelemetryDashboard(circuitBreaker);
        ConfigManager configManager = new ConfigManager(router, rateLimiter, auditLogger, telemetryDashboard);

        ExecutorService threadPool = Executors.newCachedThreadPool();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("Initiating graceful shutdown...");
            running = false;
            
            threadPool.shutdown();
            try {
                if (!threadPool.awaitTermination(5, TimeUnit.SECONDS)) {
                    threadPool.shutdownNow();
                }
            } catch (InterruptedException e) {
                threadPool.shutdownNow();
            }

            configManager.shutdown();
            telemetryDashboard.shutdown();
            auditLogger.shutdown();
            System.out.println("Shutdown complete.");
        }));

        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("Nexus Gateway started on port " + PORT);
            
            serverSocket.setSoTimeout(1000); 

            while (running) {
                try {
                    Socket clientSocket = serverSocket.accept();
                    threadPool.submit(new ClientHandler(clientSocket, router, rateLimiter, auditLogger, telemetryDashboard, circuitBreaker));
                } catch (java.net.SocketTimeoutException e) {
                    // Ignore, checking running flag
                }
            }
        } catch (Exception e) {
            if (running) {
                e.printStackTrace();
            }
        }
    }
}
