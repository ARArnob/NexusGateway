package nexus.routing;

import nexus.security.RateLimiter;
import nexus.telemetry.AuditLogger;
import nexus.telemetry.TelemetryDashboard;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

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

            String path = parts[1];

            if (!rateLimiter.allowRequest(clientIp)) {
                telemetryDashboard.incrementBlocked();
                auditLogger.log("BLOCKED | IP: " + clientIp + " | Path: " + path);
                String response = "HTTP/1.1 429 Too Many Requests\r\nConnection: close\r\n\r\n";
                clientOut.write(response.getBytes(StandardCharsets.UTF_8));
                clientOut.flush();
                return;
            }

            String targetNode = router.getRoute(path, clientIp);
            if (targetNode == null) {
                auditLogger.log("NO_ROUTE | IP: " + clientIp + " | Path: " + path);
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
                auditLogger.log("ROUTED | IP: " + clientIp + " | Path: " + path + " -> " + targetNode);

            } catch (Exception e) {
                circuitBreaker.recordFailure(targetNode);
                auditLogger.log("ERROR | IP: " + clientIp + " | Path: " + path + " -> " + targetNode + " (" + e.getMessage() + ")");
                String response = "HTTP/1.1 502 Bad Gateway\r\nConnection: close\r\n\r\n";
                clientOut.write(response.getBytes(StandardCharsets.UTF_8));
                clientOut.flush();
            }

        } catch (Exception e) {
            System.err.println("ClientHandler Error: " + e.getMessage());
        }
    }
}
