package nexus.routing;

import nexus.security.RateLimiter;
import nexus.telemetry.AuditLogger;
import nexus.telemetry.TelemetryDashboard;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.net.URLDecoder;
import java.util.zip.GZIPOutputStream;
import java.io.ByteArrayOutputStream;

public class ClientHandler implements Runnable {
    private final Socket clientSocket;
    private final Router router;
    private final RateLimiter rateLimiter;
    private final AuditLogger auditLogger;
    private final TelemetryDashboard telemetryDashboard;
    private final CircuitBreaker circuitBreaker;

    public ClientHandler(Socket clientSocket, Router router, RateLimiter rateLimiter,
                         AuditLogger auditLogger, TelemetryDashboard telemetryDashboard,
                         CircuitBreaker circuitBreaker) {
        this.clientSocket = clientSocket;
        this.router = router;
        this.rateLimiter = rateLimiter;
        this.auditLogger = auditLogger;
        this.telemetryDashboard = telemetryDashboard;
        this.circuitBreaker = circuitBreaker;
    }

    @Override
    public void run() {
        try (Socket client = clientSocket;
             InputStream clientIn = client.getInputStream();
             OutputStream clientOut = client.getOutputStream()) {

            String clientIp = client.getInetAddress().getHostAddress();

            byte[] headerBuffer = new byte[8192];
            int bytesRead = clientIn.read(headerBuffer);
            if (bytesRead <= 0) return;

            String requestHeader = new String(headerBuffer, 0, bytesRead, StandardCharsets.UTF_8);
            String firstLine = requestHeader.split("\r\n")[0];
            String[] parts = firstLine.split(" ");
            if (parts.length < 2) return;

            String rawPath = parts[1];
            String decodedPath;
            try {
                decodedPath = URLDecoder.decode(rawPath, "UTF-8");
            } catch (Exception e) {
                decodedPath = rawPath;
            }

            boolean acceptsGzip = requestHeader.toLowerCase().contains("accept-encoding: gzip");

            if (rawPath.equals("/dashboard")) {
                serveDashboard(clientOut, acceptsGzip);
                return;
            }

            if (rawPath.equals("/stats")) {
                serveStats(clientOut, acceptsGzip);
                return;
            }

            if (rawPath.equals("/avatar")) {
                serveAvatar(clientOut);
                return;
            }

            if (rawPath.equals("/favicon.ico")) {
                String response = "HTTP/1.1 204 No Content\r\nConnection: close\r\n\r\n";
                clientOut.write(response.getBytes(StandardCharsets.UTF_8));
                clientOut.flush();
                return;
            }

            if (!rateLimiter.allowRequest(clientIp)) {
                telemetryDashboard.incrementBlocked();
                auditLogger.log("BLOCKED | IP: " + clientIp + " | Path: " + rawPath);
                String response = "HTTP/1.1 429 Too Many Requests\r\nConnection: close\r\n\r\n";
                clientOut.write(response.getBytes(StandardCharsets.UTF_8));
                clientOut.flush();
                return;
            }

            String pathUpper = decodedPath.toUpperCase();
            if (pathUpper.contains("UNION SELECT") || pathUpper.contains("OR 1=1") || pathUpper.contains("<SCRIPT>")) {
                telemetryDashboard.incrementWafBlocked();
                auditLogger.log("WAF_BLOCK | IP: " + clientIp + " | Path: " + rawPath);
                String response = "HTTP/1.1 403 Forbidden\r\nConnection: close\r\n\r\n";
                clientOut.write(response.getBytes(StandardCharsets.UTF_8));
                clientOut.flush();
                return;
            }

            String targetNode = router.getRoute(rawPath, clientIp);
            if (targetNode == null) {
                auditLogger.log("NO_ROUTE | IP: " + clientIp + " | Path: " + rawPath);
                String response = "HTTP/1.1 503 Service Unavailable\r\nConnection: close\r\n\r\n";
                clientOut.write(response.getBytes(StandardCharsets.UTF_8));
                clientOut.flush();
                return;
            }

            String[] nodeParts = targetNode.split(":");
            String host = nodeParts[0];
            int port = Integer.parseInt(nodeParts[1]);

            try (Socket backendSocket = new Socket(host, port);
                 InputStream backendIn = backendSocket.getInputStream();
                 OutputStream backendOut = backendSocket.getOutputStream()) {

                backendSocket.setSoTimeout(5000);
                
                backendOut.write(headerBuffer, 0, bytesRead);
                backendOut.flush();

                byte[] responseBuffer = new byte[8192];
                int readFromBackend;
                while ((readFromBackend = backendIn.read(responseBuffer)) != -1) {
                    clientOut.write(responseBuffer, 0, readFromBackend);
                    clientOut.flush();
                }

                circuitBreaker.recordSuccess(targetNode);
                telemetryDashboard.incrementRouted();
                auditLogger.log("ROUTED | IP: " + clientIp + " | Path: " + rawPath + " -> " + targetNode);

            } catch (Exception e) {
                circuitBreaker.recordFailure(targetNode);
                auditLogger.log("ERROR | IP: " + clientIp + " | Path: " + rawPath + " -> " + targetNode + " (" + e.getMessage() + ")");
                String response = "HTTP/1.1 502 Bad Gateway\r\nConnection: close\r\n\r\n";
                clientOut.write(response.getBytes(StandardCharsets.UTF_8));
                clientOut.flush();
            }

        } catch (Exception e) {
            System.err.println("ClientHandler Error: " + e.getMessage());
        }
    }

    private void serveDashboard(OutputStream clientOut, boolean useGzip) throws Exception {
        String html = """
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <title>Nexus Command Center</title>
    <style>
        @import url('https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600;700&display=swap');
        :root { --bg-color: #17151e; --sidebar-bg: #1d1a24; --card-bg: rgba(29, 26, 36, 0.6); --card-border: rgba(255, 255, 255, 0.05); --text-main: #f9fafb; --text-muted: #9ca3af; --accent-purple: #8B5CF6; --accent-magenta: #D946EF; }
        body { margin: 0; background: linear-gradient(135deg, #13111C 0%, #1A1625 100%); color: var(--text-main); font-family: 'Inter', sans-serif; height: 100vh; display: flex; align-items: center; justify-content: center; overflow: hidden; }
        .dashboard-wrapper { width: 95vw; max-width: 1400px; height: 90vh; background: var(--bg-color); border: 1px solid var(--card-border); border-radius: 20px; display: flex; box-shadow: 0 25px 50px -12px rgba(0,0,0,0.5); overflow: hidden; }
        .sidebar { width: 80px; background: var(--sidebar-bg); border-right: 1px solid var(--card-border); display: flex; flex-direction: column; align-items: center; padding: 20px 0; position: relative; }
        .logo { color: var(--accent-purple); font-size: 32px; margin-bottom: 30px; }
        .nav-items { display: flex; flex-direction: column; gap: 20px; width: 100%; }
        .nav-item { width: 100%; height: 50px; display: flex; justify-content: center; align-items: center; color: var(--text-muted); cursor: pointer; position: relative; }
        .nav-item.active { color: var(--text-main); }
        .nav-item.active::before { content: ''; position: absolute; left: 0; top: 10px; bottom: 10px; width: 3px; background: var(--accent-purple); border-radius: 0 4px 4px 0; }
        .nav-bottom { margin-top: auto; color: var(--text-muted); cursor: pointer; }
        .main-content { flex: 1; padding: 30px 40px; display: flex; flex-direction: column; background: rgba(255,255,255,0.01); backdrop-filter: blur(20px); }
        .top-nav { display: flex; justify-content: flex-end; align-items: center; gap: 20px; margin-bottom: 10px; }
        .top-icon { color: var(--text-muted); cursor: pointer; }
        .user-profile { display: flex; align-items: center; gap: 10px; font-size: 14px; }
        .user-avatar { width: 30px; height: 30px; border-radius: 50%; background: url('/avatar') center/cover; }
        .header-section { display: flex; justify-content: space-between; align-items: flex-end; margin-bottom: 30px; }
        .welcome-text h1 { margin: 0 0 5px 0; font-size: 28px; font-weight: 500; }
        .welcome-text p { margin: 0; color: var(--text-muted); font-size: 14px; }
        .header-actions { display: flex; gap: 15px; }
        .btn { background: transparent; border: 1px solid var(--card-border); color: var(--text-main); padding: 8px 16px; border-radius: 20px; font-size: 13px; cursor: pointer; }
        .btn-primary { background: var(--accent-purple); border: none; }
        .grid-container { display: grid; grid-template-columns: 1fr 1fr; grid-template-rows: 320px 200px; gap: 20px; flex: 1; }
        .card { background: var(--card-bg); border: 1px solid var(--card-border); border-radius: 16px; padding: 20px; display: flex; flex-direction: column; position: relative; backdrop-filter: blur(12px); }
        .bottom-cards { grid-column: 1 / -1; display: grid; grid-template-columns: 1fr 1fr 1fr; gap: 20px; }
        .card-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 15px; }
        .card-title { font-size: 14px; font-weight: 500; }
        .metric-large { font-size: 32px; font-weight: 600; margin-bottom: auto; }
        .nodes-container { flex: 1; overflow-y: auto; display: flex; flex-direction: column; gap: 10px; }
        .server-rack { display: flex; justify-content: space-between; align-items: center; padding: 12px 15px; border-radius: 10px; background: rgba(0,0,0,0.2); border: 1px solid var(--card-border); }
        .rack-healthy { border-left: 3px solid var(--accent-purple); }
        .rack-tripped { border-left: 3px solid #ef4444; }
        .status-badge { font-size: 11px; padding: 3px 8px; border-radius: 10px; }
        .bg-purple { background: rgba(139, 92, 246, 0.2); color: #c4b5fd; }
        .bg-red { background: rgba(239, 68, 68, 0.2); color: #fca5a5; }
        .terminal { flex: 1; background: rgba(0,0,0,0.3); border-radius: 10px; padding: 15px; font-family: monospace; font-size: 12px; color: var(--accent-purple); overflow-y: auto; border: 1px solid var(--card-border); }
        .bar-chart-container { height: 60px; display: flex; align-items: flex-end; gap: 6px; }
        .bar { flex: 1; background: var(--accent-purple); border-radius: 3px 3px 0 0; min-height: 5px; transition: height 0.3s; }
        .bar:nth-child(even) { opacity: 0.6; }
        .ring-container { display: flex; justify-content: center; align-items: center; margin-top: -20px; }
        .progress-ring { width: 110px; height: 110px; border-radius: 50%; background: conic-gradient(var(--accent-purple) 0%, rgba(255,255,255,0.05) 0); display: flex; justify-content: center; align-items: center; }
        .progress-ring-inner { width: 86px; height: 86px; background: #191621; border-radius: 50%; display: flex; justify-content: center; align-items: center; font-size: 18px; font-weight: 600; }
        .flash { animation: screenFlash 0.5s ease-out; }
        @keyframes screenFlash { 0% { box-shadow: inset 0 0 100px rgba(239, 68, 68, 0.6); } 100% { box-shadow: none; } }
        @media (max-width: 1000px) {
            .grid-container, .bottom-cards {
                grid-template-columns: 1fr;
            }
            .dashboard-wrapper, .main-content {
                overflow-y: auto;
            }
        }
    </style>
</head>
<body>
    <div class="dashboard-wrapper" id="dashboard">
        <div class="sidebar">
            <div class="logo">✦</div>
            <div class="nav-items">
                <div class="nav-item active">⊞</div>
            </div>
        </div>
        <div class="main-content">
            <div class="top-nav">
                <div class="user-profile"><div class="user-avatar"></div><span>System Admin</span></div>
            </div>
            <div class="header-section">
                <div class="welcome-text">
                    <h1>Welcome Back, Admin.</h1>
                    <p>Gateway Uptime: <span id="uptime">0</span>s</p>
                </div>
            </div>
            <div class="grid-container">
                <div class="card">
                    <div class="card-header"><div class="card-title">Node Cluster</div><div style="color:var(--text-muted);font-size:12px">Server Racks</div></div>
                    <div class="nodes-container" id="nodes"></div>
                </div>
                <div class="card" id="wafFlash">
                    <div class="card-header"><div class="card-title">Security Feed</div><div style="color:var(--text-muted);font-size:12px">WAF Blocks: <span id="wafBlocked" style="color:var(--accent-magenta)">0</span></div></div>
                    <div class="terminal" id="logs"></div>
                </div>
                <div class="bottom-cards">
                    <div class="card">
                        <div class="card-header"><div class="card-title">Live Traffic</div><div>↗</div></div>
                        <div class="metric-large" id="routed">0</div>
                        <div class="bar-chart-container" id="barChart"></div>
                    </div>
                    <div class="card">
                        <div class="card-header"><div class="card-title">Compression Savings</div><div>↗</div></div>
                        <div class="metric-large" id="compRatio">1.0x</div>
                        <div class="ring-container"><div class="progress-ring" id="circProg"><div class="progress-ring-inner"></div></div></div>
                    </div>
                    <div class="card">
                        <div class="card-header"><div class="card-title">Threat Mitigation</div><div>↗</div></div>
                        <div class="metric-large" id="rlBlocked">0</div>
                        <div style="color:var(--text-muted); margin-top:5px; font-size:14px;">Rate-Limit Blocks</div>
                    </div>
                </div>
            </div>
        </div>
    </div>
    <script>
        let lastWaf = 0; let lastRouted = 0; let history = Array(12).fill(0);
        const barChart = document.getElementById('barChart');
        for(let i=0; i<12; i++) { let b = document.createElement('div'); b.className = 'bar'; barChart.appendChild(b); }
        
        let isFetching = false;
        let controller = new AbortController();
        async function fetchStats() {
            if (isFetching) { controller.abort(); controller = new AbortController(); }
            isFetching = true;
            try {
                setTimeout(() => controller.abort(), 800);
                const response = await fetch('/stats', { signal: controller.signal });
                const data = await response.json();
                document.getElementById('uptime').innerText = data.uptime;
                document.getElementById('routed').innerText = data.routed;
                document.getElementById('rlBlocked').innerText = data.rlBlocked;
                document.getElementById('wafBlocked').innerText = data.wafBlocked;
                document.getElementById('compRatio').innerText = data.compRatio;
                let rps = data.routed - lastRouted; if (lastRouted === 0) rps = 0; lastRouted = data.routed;
                history.shift(); history.push(rps); let maxRps = Math.max(...history, 10);
                const bars = barChart.children; for(let i=0; i<12; i++) { let h = Math.max(5, (history[i] / maxRps) * 60); bars[i].style.height = h + 'px'; }
                let ratioVal = parseFloat(data.compRatio); let pct = Math.min(100, Math.max(0, ((ratioVal - 1) / 3) * 100));
                document.getElementById('circProg').style.background = `conic-gradient(var(--accent-purple) ${pct}%, rgba(255,255,255,0.05) 0)`;
                if(data.wafBlocked > lastWaf && lastWaf !== 0) { let el = document.getElementById('wafFlash'); el.classList.remove('flash'); void el.offsetWidth; el.classList.add('flash'); }
                lastWaf = data.wafBlocked;
                let nodesHtml = ''; for(let [node, status] of Object.entries(data.nodes)) { let isTripped = status !== 'CLOSED'; let clz = isTripped ? 'rack-tripped' : 'rack-healthy'; let lblClz = isTripped ? 'bg-red' : 'bg-purple'; let lbl = isTripped ? 'TRIPPED' : 'ONLINE'; nodesHtml += `<div class='server-rack ${clz}'><div style='font-family:monospace'>${node}</div><div class='status-badge ${lblClz}'>${lbl}</div></div>`; }
                document.getElementById('nodes').innerHTML = nodesHtml;
                document.getElementById('logs').innerHTML = data.logs.join('<br/>');
            } catch (error) {
                if (error.name !== 'AbortError') console.error('Telemetry Error:', error);
            } finally {
                isFetching = false;
                setTimeout(fetchStats, 800);
            }
        }
        fetchStats();
    </script>
</body>
</html>
""";

        sendResponse(clientOut, "text/html", html, useGzip);
    }

    private void serveAvatar(OutputStream clientOut) throws Exception {
        java.io.File file = new java.io.File("admin_avatar.png");
        if (!file.exists()) {
            String response = "HTTP/1.1 404 Not Found\r\nConnection: close\r\n\r\n";
            clientOut.write(response.getBytes(StandardCharsets.UTF_8));
            clientOut.flush();
            return;
        }
        byte[] imgBytes = java.nio.file.Files.readAllBytes(file.toPath());
        String headers = "HTTP/1.1 200 OK\r\n" +
                "Content-Type: image/png\r\n" +
                "Content-Length: " + imgBytes.length + "\r\n" +
                "Cache-Control: public, max-age=3600\r\n" +
                "Connection: close\r\n\r\n";
        clientOut.write(headers.getBytes(StandardCharsets.UTF_8));
        clientOut.write(imgBytes);
        clientOut.flush();
    }

    private void serveStats(OutputStream clientOut, boolean useGzip) throws Exception {
        String nodesStatus = circuitBreaker.getDashboardStatus();
        StringBuilder nodesJson = new StringBuilder("{");
        if (!nodesStatus.isEmpty()) {
            String[] parts = nodesStatus.split("  ");
            boolean first = true;
            for(int i=0; i<parts.length; i++) {
                String p = parts[i].trim();
                if (p.isEmpty()) continue;
                int lastColon = p.lastIndexOf(':');
                if (lastColon != -1) {
                    String node = p.substring(0, lastColon);
                    String state = p.substring(lastColon + 1);
                    if (!first) nodesJson.append(",");
                    nodesJson.append("\"").append(node).append("\":\"").append(state).append("\"");
                    first = false;
                }
            }
        }
        nodesJson.append("}");

        StringBuilder logsJson = new StringBuilder("[");
        java.util.List<String> logs = auditLogger.getRecentLogs();
        for(int i=0; i<logs.size(); i++) {
            logsJson.append("\"").append(logs.get(i).replace("\"", "\\\"")).append("\"");
            if(i < logs.size() - 1) logsJson.append(",");
        }
        logsJson.append("]");

        long bytesO = telemetryDashboard.getBytesOriginal();
        long bytesC = telemetryDashboard.getBytesCompressed();
        String compRatio = "1.0";
        if (bytesC > 0) {
            double r = (double) bytesO / bytesC;
            compRatio = String.format("%.1f", r);
        }

        String json = "{" +
                "\"uptime\":" + telemetryDashboard.getUptime() + "," +
                "\"routed\":" + telemetryDashboard.getRequestsRouted() + "," +
                "\"rlBlocked\":" + telemetryDashboard.getRequestsBlocked() + "," +
                "\"wafBlocked\":" + telemetryDashboard.getWafBlocked() + "," +
                "\"compRatio\":\"" + compRatio + "\"," +
                "\"nodes\":" + nodesJson.toString() + "," +
                "\"logs\":" + logsJson.toString() +
                "}";

        sendResponse(clientOut, "application/json", json, useGzip);
    }

    private void sendResponse(OutputStream out, String contentType, String body, boolean useGzip) throws Exception {
        byte[] bodyBytes = body.getBytes(StandardCharsets.UTF_8);
        byte[] finalBody;
        if (useGzip) {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try (GZIPOutputStream gzipOut = new GZIPOutputStream(baos)) {
                gzipOut.write(bodyBytes);
            }
            finalBody = baos.toByteArray();
        } else {
            finalBody = bodyBytes;
        }

        telemetryDashboard.addBytesOriginal(bodyBytes.length);
        telemetryDashboard.addBytesCompressed(finalBody.length);

        String headers = "HTTP/1.1 200 OK\r\n" +
                "Content-Type: " + contentType + "\r\n" +
                "Content-Length: " + finalBody.length + "\r\n" +
                "Connection: close\r\n";
        if (useGzip) {
            headers += "Content-Encoding: gzip\r\n";
        }
        headers += "\r\n";

        out.write(headers.getBytes(StandardCharsets.UTF_8));
        out.write(finalBody);
        out.flush();
    }
}