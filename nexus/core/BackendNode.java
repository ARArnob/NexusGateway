package nexus.core;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

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
                try (Socket clientSocket = serverSocket.accept();
                     InputStream in = clientSocket.getInputStream();
                     OutputStream socketOut = clientSocket.getOutputStream()) {
                     
                    byte[] buffer = new byte[8192];
                    int read = in.read(buffer);
                    if (read > 0) {
                        StringBuilder jsonBuilder = new StringBuilder();
                        jsonBuilder.append("[\n");
                        for (int i = 1; i <= 200; i++) {
                            jsonBuilder.append("  {\n")
                                       .append("    \"id\": \"USR-").append(i).append("\",\n")
                                       .append("    \"status\": \"active\",\n")
                                       .append("    \"node_source\": \"localhost:").append(port).append("\",\n")
                                       .append("    \"timestamp\": \"").append(System.currentTimeMillis()).append("\"\n")
                                       .append("  }");
                            if (i < 500) jsonBuilder.append(",");
                            jsonBuilder.append("\n");
                        }
                        jsonBuilder.append("]");

                        String jsonResponse = jsonBuilder.toString();
                        byte[] jsonBytes = jsonResponse.getBytes(StandardCharsets.UTF_8);

                        String headers = "HTTP/1.1 200 OK\r\n" +
                                         "Content-Type: application/json\r\n" +
                                         "Content-Length: " + jsonBytes.length + "\r\n" +
                                         "Connection: close\r\n\r\n";
                        byte[] headerBytes = headers.getBytes(StandardCharsets.UTF_8);

                        socketOut.write(headerBytes);
                        socketOut.write(jsonBytes);
                        socketOut.flush();
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