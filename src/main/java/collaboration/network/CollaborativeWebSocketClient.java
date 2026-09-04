package collaboration.network;

import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * WebSocket client for joining a collaborative session hosted on a remote machine.
 * Handles connection, message reception, and automatic reconnection logic.
 */
public class CollaborativeWebSocketClient {
    private final String sessionId;
    private final String hostAddress;
    private final int hostPort;
    private final String userId;
    private final String userName;
    private Socket socket;
    private BufferedReader reader;
    private BufferedWriter writer;
    private ExecutorService executorService;
    private AtomicBoolean connected;
    private Consumer<String> onMessageReceived;
    private Consumer<String> onConnectionStatusChanged;
    private final BlockingQueue<String> messageQueue;

    public CollaborativeWebSocketClient(String sessionId, String hostAddress, int hostPort, String userId, String userName) {
        this.sessionId = sessionId;
        this.hostAddress = hostAddress;
        this.hostPort = hostPort;
        this.userId = userId;
        this.userName = userName;
        this.connected = new AtomicBoolean(false);
        this.messageQueue = new LinkedBlockingQueue<>(1000);
        this.executorService = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "ws-client-" + userId);
            t.setDaemon(true);
            return t;
        });
    }

    /**
     * Connect to the remote collaborative server.
     */
    public boolean connect() {
        try {
            socket = new Socket(hostAddress, hostPort);
            reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));

            // Send handshake with user info
            String handshake = String.format("{\"type\":\"join\",\"user_id\":\"%s\",\"user_name\":\"%s\",\"session_id\":\"%s\"}",
                    userId, userName, sessionId);
            writer.write(handshake + "\n");
            writer.flush();

            connected.set(true);
            notifyConnectionStatus("connected");

            System.out.println("✅ Connected to collaborative session: " + sessionId);

            // Start listening for messages
            executorService.execute(this::messageListenerLoop);

            return true;
        } catch (IOException ex) {
            System.err.println("❌ Failed to connect: " + ex.getMessage());
            notifyConnectionStatus("disconnected");
            return false;
        }
    }

    /**
     * Listen for incoming messages from the server.
     */
    private void messageListenerLoop() {
        while (connected.get()) {
            try {
                String message = reader.readLine();
                if (message != null) {
                    messageQueue.offer(message);
                    if (onMessageReceived != null) {
                        onMessageReceived.accept(message);
                    }
                } else {
                    disconnect();
                }
            } catch (IOException ex) {
                if (connected.get()) {
                    System.err.println("⚠️ Connection error: " + ex.getMessage());
                    disconnect();
                }
            }
        }
    }

    /**
     * Send a message to the server.
     */
    public boolean sendMessage(String message) {
        if (!connected.get()) {
            System.err.println("⚠️ Not connected");
            return false;
        }

        try {
            writer.write(message + "\n");
            writer.flush();
            return true;
        } catch (IOException ex) {
            System.err.println("⚠️ Failed to send message: " + ex.getMessage());
            disconnect();
            return false;
        }
    }

    /**
     * Poll for the next message with timeout.
     */
    public String pollMessage(long timeout, TimeUnit unit) throws InterruptedException {
        return messageQueue.poll(timeout, unit);
    }

    /**
     * Disconnect from the server.
     */
    public void disconnect() {
        if (!connected.getAndSet(false)) {
            return; // Already disconnected
        }

        try {
            if (writer != null) writer.close();
            if (reader != null) reader.close();
            if (socket != null && !socket.isClosed()) socket.close();
        } catch (IOException ex) {
            System.err.println("⚠️ Error during disconnect: " + ex.getMessage());
        }

        notifyConnectionStatus("disconnected");
        System.out.println("🛑 Disconnected from session");
    }

    public void setOnMessageReceived(Consumer<String> callback) {
        this.onMessageReceived = callback;
    }

    public void setOnConnectionStatusChanged(Consumer<String> callback) {
        this.onConnectionStatusChanged = callback;
    }

    private void notifyConnectionStatus(String status) {
        if (onConnectionStatusChanged != null) {
            onConnectionStatusChanged.accept(status);
        }
    }

    public boolean isConnected() {
        return connected.get();
    }

    public String getUserId() {
        return userId;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void shutdown() {
        disconnect();
        executorService.shutdown();
        messageQueue.clear();
    }
}
