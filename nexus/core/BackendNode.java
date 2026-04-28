package nexus.core;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

public class BackendNode {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java nexus.core.BackendNode <port>");
            System.exit(1);
        }
        int port = Integer.parseInt(args[0]);
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("BackendNode started on port " + port);
            while (true) {
                try (Socket socket = serverSocket.accept();
                     InputStream in = socket.getInputStream();
                     OutputStream out = socket.getOutputStream()) {
                     
                    byte[] buffer = new byte[8192];
                    int read = in.read(buffer);
                    if (read > 0) {
                        String requestHeader = new String(buffer, 0, read, StandardCharsets.UTF_8);
                        String[] lines = requestHeader.split("\r\n");
                        String requestedPath = "/";
                        if (lines.length > 0 && lines[0].contains(" ")) {
                            String[] parts = lines[0].split(" ");
                            if (parts.length > 1) {
                                requestedPath = parts[1];
                            }
                        }

                        String mockData = "\"status\":\"ok\"";
                        if (requestedPath.contains("auth")) {
                            mockData = "\"user_id\":\"u_7892\",\"role\":\"admin\",\"token\":\"abc123xyz\"";
                        } else if (requestedPath.contains("payments")) {
                            mockData = "\"transaction_id\":\"txn_001\",\"amount\":45.99,\"currency\":\"USD\",\"status\":\"processed\"";
                        } else if (requestedPath.contains("users")) {
                            mockData = "\"user_id\":\"u_1234\",\"name\":\"Alice Smith\",\"email\":\"alice@example.com\"";
                        }
                        
                        String jsonBody = "{" +
                                "\"timestamp\":\"" + Instant.now().toString() + "\"," +
                                "\"handled_by_node\":" + port + "," +
                                "\"requested_path\":\"" + requestedPath + "\"," +
                                "\"data\":{" + mockData + "}" +
                                "}";

                        String response = "HTTP/1.1 200 OK\r\n" +
                                          "Content-Type: application/json\r\n" +
                                          "Content-Length: " + jsonBody.getBytes(StandardCharsets.UTF_8).length + "\r\n" +
                                          "Connection: close\r\n\r\n" +
                                          jsonBody;
                        out.write(response.getBytes(StandardCharsets.UTF_8));
                        out.flush();
                    }
                } catch (Exception e) {
                    System.err.println("Error handling connection: " + e.getMessage());
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
