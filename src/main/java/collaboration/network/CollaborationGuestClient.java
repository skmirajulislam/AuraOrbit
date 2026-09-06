package collaboration.network;

import collaboration.model.CollabPacket;
import collaboration.model.CursorEvent;
import collaboration.model.TextOpEvent;
import collaboration.model.VirtualFileNode;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import javafx.application.Platform;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

import javax.net.ssl.SSLContext;
import java.net.URI;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Guest-side WebSocketClient connecting over WSS through Cloudflare Quick Tunnel.
 * Dispatches network payloads safely to the JavaFX Application Thread, maintains
 * 10-second ping/pong keepalives, and coordinates volatile memory models.
 */
public class CollaborationGuestClient extends WebSocketClient {

    private String assignedClientId;
    private String assignedColorHex = "#4ec9b0";
    private final String guestDisplayName;

    // Heartbeat scheduler for edge tunnel persistence
    private final ScheduledExecutorService heartbeatExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "Collab-Keepalive-Timer");
        t.setDaemon(true);
        return t;
    });

    // Callbacks for Guest IDE
    private Consumer<String> onAuthSuccess;
    private Consumer<VirtualFileNode> onTreeSyncReceived;
    private TriConsumer<String, String, Long> onFileResponseReceived;
    private Consumer<TextOpEvent> onRemoteOpReceived;
    private Consumer<CursorEvent> onRemoteCursorReceived;
    private Consumer<String> onDisconnectNotice;

    @FunctionalInterface
    public interface TriConsumer<A, B, C> {
        void accept(A a, B b, C c);
    }

    public CollaborationGuestClient(URI serverUri, String guestDisplayName) {
        super(serverUri);
        this.guestDisplayName = guestDisplayName != null && !guestDisplayName.isBlank()
                ? guestDisplayName
                : "Guest-" + (int)(Math.random() * 1000);

        // Configure SSL context if connecting over WSS
        if ("wss".equalsIgnoreCase(serverUri.getScheme())) {
            try {
                SSLContext sslContext = SSLContext.getDefault();
                setSocketFactory(sslContext.getSocketFactory());
            } catch (Exception ignored) {}
        }
    }

    @Override
    public void onOpen(ServerHandshake handshakedata) {
        // Send initial HELLO packet with guest display name
        CollabPacket hello = new CollabPacket(CollabPacket.Type.HELLO, "init", guestDisplayName, "");
        send(hello.toJson());

        // Start 10-second ping/pong keepalive loop
        heartbeatExecutor.scheduleAtFixedRate(() -> {
            if (isOpen()) {
                sendPing();
            }
        }, 10, 10, TimeUnit.SECONDS);
    }

    @Override
    public void onMessage(String message) {
        try {
            CollabPacket packet = CollabPacket.fromJson(message);
            if (packet == null || packet.getType() == null) return;

            switch (packet.getType()) {
                case AUTH_OK -> {
                    JsonObject obj = JsonParser.parseString(packet.getPayload()).getAsJsonObject();
                    assignedClientId = obj.get("clientId").getAsString();
                    if (obj.has("colorHex")) {
                        assignedColorHex = obj.get("colorHex").getAsString();
                    }
                    if (onAuthSuccess != null) {
                        Platform.runLater(() -> onAuthSuccess.accept(assignedClientId));
                    }
                }
                case TREE_SYNC -> {
                    VirtualFileNode tree = CollabPacket.getGson().fromJson(packet.getPayload(), VirtualFileNode.class);
                    if (onTreeSyncReceived != null) {
                        Platform.runLater(() -> onTreeSyncReceived.accept(tree));
                    }
                }
                case FILE_RESPONSE -> {
                    JsonObject obj = JsonParser.parseString(packet.getPayload()).getAsJsonObject();
                    String fileUri = obj.get("fileUri").getAsString();
                    String content = obj.get("content").getAsString();
                    long rev = obj.get("revision").getAsLong();
                    if (onFileResponseReceived != null) {
                        Platform.runLater(() -> onFileResponseReceived.accept(fileUri, content, rev));
                    }
                }
                case OP_DELTA -> {
                    TextOpEvent op = CollabPacket.getGson().fromJson(packet.getPayload(), TextOpEvent.class);
                    // Don't apply our own edits back
                    if (op != null && !op.originClientId().equals(assignedClientId)) {
                        if (onRemoteOpReceived != null) {
                            Platform.runLater(() -> onRemoteOpReceived.accept(op));
                        }
                    }
                }
                case CURSOR_MOVE -> {
                    CursorEvent cursor = CollabPacket.getGson().fromJson(packet.getPayload(), CursorEvent.class);
                    if (cursor != null && !cursor.clientId().equals(assignedClientId)) {
                        if (onRemoteCursorReceived != null) {
                            Platform.runLater(() -> onRemoteCursorReceived.accept(cursor));
                        }
                    }
                }
                default -> {}
            }
        } catch (Exception ignored) {}
    }

    @Override
    public void onClose(int code, String reason, boolean remote) {
        heartbeatExecutor.shutdownNow();
        if (onDisconnectNotice != null) {
            Platform.runLater(() -> onDisconnectNotice.accept(reason));
        }
    }

    @Override
    public void onError(Exception ex) {
        // Handled in onClose
    }

    /**
     * Request contents of a remote file from host when user clicks a tree item or tab.
     */
    public void requestFile(String relativePath) {
        if (isOpen()) {
            CollabPacket req = new CollabPacket(
                    CollabPacket.Type.FILE_REQUEST, assignedClientId, guestDisplayName, relativePath
            );
            send(req.toJson());
        }
    }

    /**
     * Send local text delta to host for OT processing.
     */
    public void sendTextDelta(TextOpEvent op) {
        if (isOpen()) {
            CollabPacket packet = new CollabPacket(
                    CollabPacket.Type.OP_DELTA, assignedClientId, guestDisplayName, CollabPacket.getGson().toJson(op)
            );
            send(packet.toJson());
        }
    }

    /**
     * Send local cursor movement to host.
     */
    public void sendCursorMove(CursorEvent cursor) {
        if (isOpen()) {
            CollabPacket packet = new CollabPacket(
                    CollabPacket.Type.CURSOR_MOVE, assignedClientId, guestDisplayName, CollabPacket.getGson().toJson(cursor)
            );
            send(packet.toJson());
        }
    }

    public String getAssignedClientId() {
        return assignedClientId;
    }

    public String getAssignedColorHex() {
        return assignedColorHex;
    }

    public void setOnAuthSuccess(Consumer<String> onAuthSuccess) {
        this.onAuthSuccess = onAuthSuccess;
    }

    public void setOnTreeSyncReceived(Consumer<VirtualFileNode> onTreeSyncReceived) {
        this.onTreeSyncReceived = onTreeSyncReceived;
    }

    public void setOnFileResponseReceived(TriConsumer<String, String, Long> onFileResponseReceived) {
        this.onFileResponseReceived = onFileResponseReceived;
    }

    public void setOnRemoteOpReceived(Consumer<TextOpEvent> onRemoteOpReceived) {
        this.onRemoteOpReceived = onRemoteOpReceived;
    }

    public void setOnRemoteCursorReceived(Consumer<CursorEvent> onRemoteCursorReceived) {
        this.onRemoteCursorReceived = onRemoteCursorReceived;
    }

    public void setOnDisconnectNotice(Consumer<String> onDisconnectNotice) {
        this.onDisconnectNotice = onDisconnectNotice;
    }
}
