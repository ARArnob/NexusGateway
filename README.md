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
- **📈 Vital Signs:** Live tracking of Uptime, Routed Requests, Blocked Attacks, and dynamic CSS-based visualizations for Compression Savings and Live Traffic.
- **🗄️ Node Cluster:** Visual "Server Racks" that dynamically track node health and lock down into a `TRIPPED` state when the Circuit Breaker opens.
- **🛡️ WAF Security Feed:** A scrolling, terminal-style log of gateway events with threat mitigation and a screen-flash animation whenever the Web Application Firewall detects a threat (SQLi/XSS).
- **👤 Customizable Admin Avatar:** Personalize your dashboard by simply replacing the `admin_avatar.png` file in the root directory.

---

## 🚀 Quick Start

Getting the infrastructure up and running is incredibly simple.

### 1. Boot Up the Servers
Simply double-click the **`start_all.bat`** file from your File Explorer (or run `.\start_all.bat` in your terminal). 
This script will automatically spin up:
- The main **Nexus Gateway** on port `8000`
- **3 Backend Nodes** on ports `8001`, `8002`, and `8003` (each in their own dedicated console window)

### 2. Access the Telemetry Dashboard
Once the servers are online, open your web browser and navigate to the built-in observability UI:
> **[http://localhost:8000/dashboard](http://localhost:8000/dashboard)**

Here you can monitor live traffic metrics and the operational status of the gateway.

### 3. Test the API Endpoints
You can verify the Gateway's routing and load balancing by making HTTP GET requests to the configured endpoints:
- `http://localhost:8000/api` -> Load balanced across nodes 8002 & 8003
- `http://localhost:8000/auth` -> Routed directly to node 8001

*Tip: Try refreshing the `/api` endpoint multiple times from different devices to see the Weighted Round-Robin load balancer distribute traffic 3:1! If you exceed 5 requests quickly, the Token Bucket will rate-limit you. Try sending a malicious request (e.g., `/api?q=<script>`) to trigger the WAF and see the dashboard flash red!*

---

## 🛠️ Architecture Highlights
- **100% Native Java:** Built without Spring, Netty, or any third-party libraries.
- **Highly Concurrent:** Asynchronous, multi-threaded request handling utilizing robust thread pools.
- **Modular Design:** Clean separation of routing, security, configuration, and telemetry components.
