package nexus.routing;

import java.util.concurrent.ConcurrentHashMap;

public class CircuitBreaker {
    public enum State { CLOSED, OPEN, HALF_OPEN }
    
    private static class NodeState {
        State state = State.CLOSED;
        int failureCount = 0;
        long openTimestamp = 0;
    }

    private final ConcurrentHashMap<String, NodeState> nodeStates = new ConcurrentHashMap<>();
    private static final int FAILURE_THRESHOLD = 3;
    private static final long RESET_TIMEOUT_MS = 30000;

    public void registerNode(String node) {
        nodeStates.putIfAbsent(node, new NodeState());
    }

    public boolean allowRequest(String node) {
        NodeState ns = nodeStates.computeIfAbsent(node, k -> new NodeState());
        synchronized (ns) {
            if (ns.state == State.CLOSED) {
                return true;
            }
            if (ns.state == State.OPEN) {
                if (System.currentTimeMillis() - ns.openTimestamp > RESET_TIMEOUT_MS) {
                    ns.state = State.HALF_OPEN;
                    return true; // The single test request
                }
                return false;
            }
            if (ns.state == State.HALF_OPEN) {
                return false; // Already testing, block others
            }
            return false;
        }
    }

    public void recordSuccess(String node) {
        NodeState ns = nodeStates.computeIfAbsent(node, k -> new NodeState());
        synchronized (ns) {
            ns.failureCount = 0;
            ns.state = State.CLOSED;
        }
    }

    public void recordFailure(String node) {
        NodeState ns = nodeStates.computeIfAbsent(node, k -> new NodeState());
        synchronized (ns) {
            if (ns.state == State.HALF_OPEN) {
                ns.state = State.OPEN;
                ns.openTimestamp = System.currentTimeMillis();
            } else if (ns.state == State.CLOSED) {
                ns.failureCount++;
                if (ns.failureCount >= FAILURE_THRESHOLD) {
                    ns.state = State.OPEN;
                    ns.openTimestamp = System.currentTimeMillis();
                }
            }
        }
    }

    public String getDashboardStatus() {
        StringBuilder sb = new StringBuilder();
        for (String node : nodeStates.keySet()) {
            State s;
            synchronized (nodeStates.get(node)) {
                s = nodeStates.get(node).state;
                if (s == State.OPEN && (System.currentTimeMillis() - nodeStates.get(node).openTimestamp > RESET_TIMEOUT_MS)) {
                    s = State.HALF_OPEN;
                }
            }
            sb.append(node).append(":").append(s).append("  ");
        }
        return sb.toString().trim();
    }
}
