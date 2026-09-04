package collaboration.network;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * WebSocket server for hosting collaborative sessions.
 * Manages connections, broadcasts messages, and coordinates state updates.
 */
public class CollaborativeWebSocketServer {
    private final int port;
    private final String sessionId;
    private final Map<String, ClientConnection> connections;
    private ServerSocket serverSocket;
    private ExecutorService executorService;
    private AtomicBoolean running;
    private Consumer<String> onMessageReceived;

    public CollaborativeWebSocketServer(int port, String sessionId) {
        this.port = port;
        this.sessionId = sessionId;
        this.connections = new ConcurrentHashMap<>();
        this.executorService = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "ws-server-" + sessionId);
            t.setDaemon(true);
            return t;
        });
        this.running = new AtomicBoolean(false);
    }

    /**
     * Start the WebSocket server.
     * In production, this would use a real WebSocket library like Tyrus or Spring WebSocket.
     * This is a simplified TCP-based implementation for demonstration.
     */
    public void start() throws IOException {
        if (running.getAndSet(true)) {
            return;
        }

        serverSocket = new ServerSocket(port);
        System.out.println("🔗 Collaborative server listening on port " + port + " (session: " + sessionId + ")");

        executorService.execute(() -> {
            while (running.get()) {
                try {
                    Socket clientSocket = serverSocket.accept();
                    executorService.execute(() -> handleClientConnection(clientSocket));
                } catch (SocketException ex) {
                    if (running.get()) {
                        System.err.println("❌ Server socket error: " + ex.getMessage());
                    }
                } catch (IOException ex) {
                    if (running.get()) {
                        System.err.println("❌ Client connection error: " + ex.getMessage());
                    }
                }
            }
        });
    }

    private void handleClientConnection(Socket clientSocket) {
        try {
            String clientId = UUID.randomUUID().toString();
            // Line framing prevents TCP packet boundaries from corrupting messages.
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream(), StandardCharsets.UTF_8))) {
                String handshake = reader.readLine();
                if (!isValidHandshake(handshake)) {
                    clientSocket.close();
                    return;
                }

                ClientConnection conn = new ClientConnection(clientId, clientSocket);
                connections.put(clientId, conn);
                System.out.println("✅ Client connected: " + clientId);
                String message;
                while (running.get() && (message = reader.readLine()) != null) {
                    if (onMessageReceived != null) onMessageReceived.accept(message);
                    broadcastMessage(message, clientId);
                }
            }

            connections.remove(clientId);
            System.out.println("❌ Client disconnected: " + clientId);
        } catch (IOException ex) {
            System.err.println("⚠️ Client error: " + ex.getMessage());
        }
    }

    private boolean isValidHandshake(String handshake) {
        return handshake != null && handshake.contains("\"type\":\"join\"")
                && handshake.contains("\"session_id\":\"" + sessionId + "\"");
    }

    public void broadcastMessage(String message, String excludeClientId) {
        for (ClientConnection conn : connections.values()) {
            if (!conn.clientId.equals(excludeClientId)) {
                try {
                    conn.send(message);
                } catch (IOException ex) {
                    System.err.println("⚠️ Failed to send to " + conn.clientId + ": " + ex.getMessage());
                }
            }
        }
    }

    public void broadcastToAll(String message) {
        for (ClientConnection conn : connections.values()) {
            try {
                conn.send(message);
            } catch (IOException ex) {
                System.err.println("⚠️ Failed to broadcast: " + ex.getMessage());
            }
        }
    }

    public void shutdown() {
        running.set(false);
        connections.forEach((id, conn) -> {
            try {
                conn.close();
            } catch (IOException ex) {
                System.err.println("⚠️ Error closing connection: " + ex.getMessage());
            }
        });
        connections.clear();
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException ex) {
            System.err.println("⚠️ Error closing server socket: " + ex.getMessage());
        }
        executorService.shutdown();
        System.out.println("🛑 Server shut down");
    }

    public int getConnectionCount() {
        return connections.size();
    }

    public void setOnMessageReceived(Consumer<String> onMessageReceived) {
        this.onMessageReceived = onMessageReceived;
    }

    private static class ClientConnection {
        private final String clientId;
        private final Socket socket;

        ClientConnection(String clientId, Socket socket) {
            this.clientId = clientId;
            this.socket = socket;
        }

        void send(String message) throws IOException {
            socket.getOutputStream().write((message + "\n").getBytes(StandardCharsets.UTF_8));
            socket.getOutputStream().flush();
        }

        void close() throws IOException {
            socket.close();
        }
    }
}
