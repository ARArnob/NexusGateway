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
        byte[] htmlBytes = java.nio.file.Files.readAllBytes(java.nio.file.Paths.get("dashboard.html").toAbsolutePath());
        sendResponse(clientOut, "text/html", htmlBytes, useGzip);
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
        sendResponse(out, contentType, body.getBytes(StandardCharsets.UTF_8), useGzip);
    }

    private void sendResponse(OutputStream out, String contentType, byte[] bodyBytes, boolean useGzip) throws Exception {
        boolean shouldCompress = useGzip && bodyBytes.length > 1024;
        byte[] finalBody;
        if (shouldCompress) {
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
        if (shouldCompress) {
            headers += "Content-Encoding: gzip\r\n";
        }
        headers += "\r\n";

        out.write(headers.getBytes(StandardCharsets.UTF_8));
        out.write(finalBody);
        out.flush();
    }
}