package nexus.routing;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class Router {
    private final Map<String, List<String>> routes = new ConcurrentHashMap<>();
    private final Map<String, AtomicInteger> roundRobinCounters = new ConcurrentHashMap<>();
    private final CircuitBreaker circuitBreaker;

    public Router(CircuitBreaker circuitBreaker) {
        this.circuitBreaker = circuitBreaker;
    }

    public void updateRoutes(Map<String, List<String>> newRoutes) {
        routes.clear();
        routes.putAll(newRoutes);
        for (List<String> nodes : newRoutes.values()) {
            for (String node : nodes) {
                circuitBreaker.registerNode(node);
            }
        }
    }

    public String getRoute(String path, String clientIp) {
        String bestMatch = null;
        for (String routePath : routes.keySet()) {
            if (path.startsWith(routePath)) {
                if (bestMatch == null || routePath.length() > bestMatch.length()) {
                    bestMatch = routePath;
                }
            }
        }

        if (bestMatch == null) {
            return null; // No route found
        }

        List<String> nodes = routes.get(bestMatch);
        if (nodes == null || nodes.isEmpty()) {
            return null;
        }

        AtomicInteger counter = roundRobinCounters.computeIfAbsent(bestMatch, k -> new AtomicInteger(0));
        int startIndex = (counter.getAndIncrement() & 0x7FFFFFFF) % nodes.size();

        // Find an available node using CircuitBreaker
        for (int i = 0; i < nodes.size(); i++) {
            int index = (startIndex + i) % nodes.size();
            String node = nodes.get(index);
            if (circuitBreaker.allowRequest(node)) {
                return node;
            }
        }

        return null; // All nodes down or open circuit
    }
}
