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
                try (Socket socket = serverSocket.accept();
                     InputStream in = socket.getInputStream();
                     OutputStream out = socket.getOutputStream()) {
                     
                    byte[] buffer = new byte[8192];
                    int read = in.read(buffer);
                    if (read > 0) {
                        String response = "HTTP/1.1 200 OK\r\n" +
                                          "Content-Type: text/plain\r\n" +
                                          "Connection: close\r\n\r\n" +
                                          "Served by Node " + port + "\n";
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
