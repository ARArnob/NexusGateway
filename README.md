# 🚀 Nexus Gateway

**Enterprise L7 API Gateway Infrastructure**

Nexus Gateway is a high-performance, production-grade Layer 7 API Gateway built entirely from scratch using pure Java standard libraries. It acts as a robust entry point for microservices, providing advanced traffic management, security, and observability with zero external dependencies.

---

## ✨ Features

- **⚡ Circuit Breaker Pattern**
  Protects upstream backend services by continuously monitoring error rates. Automatically halts traffic to failing nodes to prevent cascading failures, enabling self-healing and rapid fault recovery.
  
- **🛡️ Token Bucket Rate Limiting**
  Strictly enforces API rate limits per client IP using a highly concurrent, thread-safe token bucket algorithm. Prevents abuse and ensures fair resource allocation across all consumers.
  
- **⚖️ Weighted Round-Robin Load Balancer**
  Distributes incoming traffic efficiently across available backend nodes based on configurable priority weights (e.g., `8002:w3`). Integrates tightly with the Circuit Breaker to seamlessly skip failing nodes.
  
- **📊 Real-Time Telemetry Dashboard**
  Built-in, asynchronous observability suite. Monitor live request metrics, gateway health, and circuit breaker status directly from your browser.
  
- **🗺️ Dynamic Path-Based Routing**
  Easily configurable routing rules managed via a lightweight `nexus.conf` configuration file.

---

## 🖥️ The Command Center (Dashboard Preview)

The newly injected **Command Center** (`GET /dashboard`) is a premium, zero-dependency "Glassmorphism" SaaS UI built directly into the gateway.

- **🎨 Premium Theme:** Deep slate gradients (`#13111C` to `#1A1625`) with frosted glass cards (`backdrop-filter: blur(12px)`), accented by vibrant purple (`#8B5CF6`) and alert red (`#ef4444`).
- **📱 Fully Responsive:** The dashboard seamlessly adapts to mobile and desktop displays using fluid CSS Grid layouts and viewport-relative scaling.
- **📈 Vital Signs:** Live tracking of Uptime, Routed Requests, Blocked Attacks, and dynamic CSS-based visualizations for Compression Savings and Live Traffic powered by a fortified, asynchronous telemetry stream.
- **🗄️ Node Cluster:** Visual "Server Racks" that dynamically track node health and lock down into a `TRIPPED` state when the Circuit Breaker opens.
- **🛡️ WAF Security Feed:** A scrolling, terminal-style log of gateway events with threat mitigation and a screen-flash animation whenever the Web Application Firewall detects a threat (SQLi/XSS).
- **👤 Customizable Admin Avatar:** Personalize your dashboard by simply replacing the `admin_avatar.png` file in the root directory.

---

## 🚀 Quick Start

Getting the infrastructure up and running is incredibly simple.

### 1. Boot Up the Microservice Cluster
Simply double-click the **`start_all.bat`** file from your File Explorer (or run `.\start_all.bat` in your terminal). 
This script will automatically spin up:
- The main **Nexus Gateway** on port `8000`
- **6 Backend Nodes** (Ports 8001-8006), organized into high-availability service pools:
  - **Auth Service:** Nodes 8001, 8006
  - **User Service:** Nodes 8002, 8003
  - **Payment Service:** Nodes 8004, 8005

### 2. Access the Telemetry Dashboard
Once the servers are online, open your web browser and navigate to the built-in observability UI:
> **[http://localhost:8000/dashboard](http://localhost:8000/dashboard)**

Here you can monitor live traffic metrics and the operational status of the gateway.

### 3. Test the API Endpoints
You can verify the Gateway's routing and load balancing by making HTTP GET requests to the configured endpoints. The backend nodes actively simulate real microservices, returning context-aware JSON responses!
- `http://localhost:8000/api/users` -> Load balanced across nodes 8002 & 8003
- `http://localhost:8000/auth` -> Routed to nodes 8001 & 8006
- `http://localhost:8000/api/payments` -> Routed to nodes 8004 & 8005

*Tip: Try refreshing an endpoint multiple times from different devices to see the Weighted Round-Robin load balancer distribute traffic! If you exceed the rate limits quickly, the Token Bucket will block you. Try sending a malicious request (e.g., `/api?q=<script>`) to trigger the WAF and see the dashboard flash red!*

---

## 🛠️ Architecture Highlights
- **100% Native Java 15+:** Built utilizing pure standard libraries (Text Blocks, NIO, Concurrent Collections) without Spring, Netty, or any third-party dependencies.
- **Bulletproof Hot-Reload:** Dynamic configuration updates via `nexus.conf` using hardened `WatchService` integration with strict pathing.
- **Highly Concurrent:** Asynchronous, multi-threaded request handling utilizing robust thread pools.
- **Modular Design:** Clean separation of routing, security, configuration, and telemetry components.
