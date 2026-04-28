# 🚀 Nexus Gateway

**Enterprise L7 API Gateway Infrastructure**

Nexus Gateway is a high-performance, production-grade Layer 7 API Gateway built entirely from scratch using pure Java standard libraries. It acts as a robust entry point for a multi-service microservice cluster, providing advanced traffic management, security, and real-time observability with **zero external dependencies**.

---

## ✨ Features

- **⚡ Circuit Breaker Pattern**
  Protects upstream backend services by continuously monitoring error rates. Automatically halts traffic to failing nodes to prevent cascading failures, with self-healing via a configurable `HALF_OPEN` recovery probe before fully restoring a node.

- **🛡️ Token Bucket Rate Limiting**
  Strictly enforces API rate limits per client IP using a highly concurrent, thread-safe token bucket algorithm. Prevents abuse and ensures fair resource allocation across all consumers. Configurable at runtime via `nexus.conf`.

- **⚖️ Weighted Round-Robin Load Balancer**
  Distributes incoming traffic across available backend nodes based on configurable priority weights (e.g., `8002:w2`). Integrates tightly with the Circuit Breaker to seamlessly skip failing nodes in real time.

- **📊 Real-Time Telemetry Dashboard**
  Built-in, asynchronous observability suite powered by a premium Glassmorphism UI. Monitor live request metrics, gateway health, circuit breaker states, and WAF security events directly from your browser.

- **🗺️ Dynamic Path-Based Routing**
  Configurable multi-pool routing rules managed via `nexus.conf`. Supports granular path prefixes (`/auth`, `/api/users`, `/api/payments`) with per-pool weighted backends.

- **🔥 Live Hot-Reload**
  Configuration changes in `nexus.conf` are automatically detected and applied to the gateway at runtime via a hardened `WatchService` integration — no restart required.

- **📋 Centralized Audit Logging**
  A unified `AuditLogger` is injected into all core components (`Gateway`, `ClientHandler`, `ConfigManager`), funneling structured operational events into a single, synchronized, tamper-evident audit stream.

- **⚡ Zero-Allocation I/O Pipeline**
  Static assets (dashboard HTML, favicon) are served via direct `OutputStream` byte-stream writes, entirely bypassing Java `String` instantiation to minimize GC pressure on hot paths.

---

## 🖥️ The Command Center (Dashboard Preview)

The built-in **Command Center** (`GET /dashboard`) is a premium, zero-dependency Glassmorphism SaaS UI served directly by the gateway.

- **🎨 Premium Theme:** Deep slate gradients (`#13111C` → `#1A1625`) with frosted glass cards (`backdrop-filter: blur(12px)`), accented by vibrant purple (`#8B5CF6`) and alert red (`#ef4444`).
- **📱 Fully Responsive:** Seamlessly adapts to mobile and desktop using fluid CSS Grid and viewport-relative scaling.
- **📈 Vital Signs:** Live tracking of Uptime, Routed Requests, Blocked Attacks, Compression Savings, and Live Traffic via a fortified asynchronous telemetry stream.
- **🗄️ Node Cluster:** Visual "Server Racks" that dynamically reflect node health and lock down into a `TRIPPED` state when the Circuit Breaker opens.
- **🛡️ WAF Security Feed:** A scrolling, terminal-style log of gateway events with a screen-flash animation whenever the Web Application Firewall detects a threat (SQLi/XSS).
- **👤 Customizable Admin Avatar:** Replace `admin_avatar.png` in the root directory to personalize your dashboard.

---

## 🚀 Quick Start

### 1. Boot the Full Microservice Cluster

Double-click **`start_all.bat`** or run `.\start_all.bat` in your terminal.

This script automatically spins up **7 processes** organized into three HA service pools:

| Service Pool      | Nodes        | Weights |
|-------------------|--------------|---------|
| Auth Service      | 8001, 8006   | w1, w1  |
| User Service      | 8002, 8003   | w2, w1  |
| Payment Service   | 8004, 8005   | w3, w1  |
| **Nexus Gateway** | **8000**     | —       |

### 2. Access the Telemetry Dashboard

Open your browser and navigate to:

> **[http://localhost:8000/dashboard](http://localhost:8000/dashboard)**

### 3. Test the API Endpoints

| Endpoint | Routed To | Behaviour |
|---|---|---|
| `GET /auth` | Nodes 8001, 8006 | Round-robin, equal weight |
| `GET /api/users` | Nodes 8002, 8003 | 2:1 weighted towards 8002 |
| `GET /api/payments` | Nodes 8004, 8005 | 3:1 weighted towards 8004 |

> 💡 **Tips:**
> - Refresh an endpoint repeatedly to observe weighted load balancing in action.
> - Exceed the rate limit quickly to trigger the Token Bucket blocker.
> - Send a malicious request (e.g., `/api?q=<script>`) to trigger the WAF and see the dashboard flash red.
> - Edit `nexus.conf` while the gateway is running to test live hot-reload — changes apply instantly.

---

## 🗂️ Project Structure

```
Nexus Gateway/
├── nexus/
│   ├── core/
│   │   ├── Gateway.java          # Main server: accepts connections, routes requests
│   │   ├── ClientHandler.java    # Per-request handler with zero-allocation I/O
│   │   └── BackendNode.java      # Simulated microservice backend
│   ├── routing/
│   │   └── Router.java           # Weighted round-robin load balancer + circuit breaker
│   ├── security/
│   │   └── RateLimiter.java      # Thread-safe token bucket rate limiter
│   ├── config/
│   │   └── ConfigManager.java    # Hot-reload config watcher with AuditLogger injection
│   └── telemetry/
│       ├── TelemetryDashboard.java # Live metrics engine
│       └── AuditLogger.java        # Centralized, synchronized audit stream
├── dashboard.html                # External Glassmorphism UI (hot-reloadable)
├── nexus.conf                    # Runtime configuration (routes + rate limits)
├── start_all.bat                 # One-click cluster launcher
└── admin_avatar.png              # Customizable dashboard avatar
```

---

## 🛠️ Architecture Highlights

- **100% Native Java 15+** — Built on pure standard libraries (Text Blocks, NIO, `java.util.concurrent`) with no Spring, Netty, or third-party dependencies.
- **Zero-Allocation Data Pipeline** — Static assets served via direct byte-stream writes, entirely bypassing `String` instantiation to minimize GC pressure.
- **Centralized Observability** — `AuditLogger` injected into `Gateway`, `ClientHandler`, and `ConfigManager` to funnel all operational events into a single synchronized audit stream.
- **Hardened Hot-Reload** — `WatchService`-based config watcher with strict path resolution and graceful error recovery via `AuditLogger`.
- **Circuit Breaker with Half-Open Recovery** — Three-state (`CLOSED` → `OPEN` → `HALF_OPEN`) fault isolation with configurable failure thresholds and recovery probing.
- **Highly Concurrent** — Asynchronous, multi-threaded request handling via `ExecutorService` thread pools with daemon config-watcher threads.
- **Modular Design** — Clean package-level separation of routing, security, configuration, telemetry, and core concerns.
