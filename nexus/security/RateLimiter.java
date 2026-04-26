package nexus.security;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class RateLimiter {
    private final ConcurrentHashMap<String, AtomicInteger> buckets = new ConcurrentHashMap<>();
    private volatile int maxTokens;

    public RateLimiter(int maxTokens) {
        this.maxTokens = maxTokens;
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "RateLimiter-Daemon");
            t.setDaemon(true);
            return t;
        });

        scheduler.scheduleAtFixedRate(() -> {
            for (AtomicInteger tokens : buckets.values()) {
                while (true) {
                    int current = tokens.get();
                    if (current >= this.maxTokens) break;
                    if (tokens.compareAndSet(current, current + 1)) break;
                }
            }
        }, 2, 2, TimeUnit.SECONDS);
    }

    public void setMaxTokens(int maxTokens) {
        this.maxTokens = maxTokens;
    }

    public boolean allowRequest(String ip) {
        AtomicInteger tokens = buckets.computeIfAbsent(ip, k -> new AtomicInteger(maxTokens));
        while (true) {
            int current = tokens.get();
            if (current == 0) {
                return false;
            }
            if (tokens.compareAndSet(current, current - 1)) {
                return true;
            }
        }
    }
}
