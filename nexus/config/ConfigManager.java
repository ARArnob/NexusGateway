package nexus.config;

import nexus.routing.Router;
import nexus.security.RateLimiter;
import nexus.telemetry.AuditLogger;
import nexus.telemetry.TelemetryDashboard;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;

public class ConfigManager {
    private final Router router;
    private final RateLimiter rateLimiter;
    private final Thread worker;
    private volatile boolean running = true;

    public ConfigManager(Router router, RateLimiter rateLimiter, AuditLogger auditLogger,
            TelemetryDashboard telemetryDashboard) {
        this.router = router;
        this.rateLimiter = rateLimiter;

        loadConfig();

        worker = new Thread(() -> {
            try {
                Path configPath = Paths.get("nexus.conf").toAbsolutePath();
                Path configDir = configPath.getParent();
                WatchService watchService = FileSystems.getDefault().newWatchService();

                try {
                    configDir.register(watchService,
                            StandardWatchEventKinds.ENTRY_MODIFY,
                            StandardWatchEventKinds.ENTRY_CREATE);
                } catch (IOException e) {
                    auditLogger.log("CRITICAL | ConfigManager | Directory unreadable. Hot-reload disabled.");
                    return;
                }

                while (running) {
                    WatchKey key;
                    try {
                        key = watchService.take();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }

                    for (WatchEvent<?> event : key.pollEvents()) {
                        Path changed = (Path) event.context();
                        if (changed.getFileName().toString().equals("nexus.conf")) {
                            loadConfig();
                            telemetryDashboard.setSystemEvent("Reloaded nexus.conf at " + java.time.LocalTime.now());
                        }
                    }
                    key.reset();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }, "ConfigWatcher-Thread");
        worker.setDaemon(true);
        worker.start();
    }

    private synchronized void loadConfig() {
        Properties props = new Properties();
        try (FileInputStream in = new FileInputStream("nexus.conf")) {
            props.load(in);

            if (props.containsKey("rate.limit")) {
                int limit = Integer.parseInt(props.getProperty("rate.limit"));
                rateLimiter.setMaxTokens(limit);
            }

            Map<String, List<String>> newRoutes = new HashMap<>();
            for (String key : props.stringPropertyNames()) {
                if (key.startsWith("route.")) {
                    String path = key.substring(6);
                    String[] ports = props.getProperty(key).split(",");
                    List<String> nodes = new ArrayList<>();
                    for (String portStr : ports) {
                        portStr = portStr.trim();
                        int weight = 1;
                        if (portStr.contains(":w")) {
                            String[] parts = portStr.split(":w");
                            portStr = parts[0];
                            weight = Integer.parseInt(parts[1]);
                        }
                        for (int i = 0; i < weight; i++) {
                            nodes.add("localhost:" + portStr);
                        }
                    }
                    newRoutes.put(path, nodes);
                }
            }
            router.updateRoutes(newRoutes);

        } catch (Exception e) {
            System.err.println("Failed to load config: " + e.getMessage());
        }
    }

    public void shutdown() {
        running = false;
        worker.interrupt();
    }
}
