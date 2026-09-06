package collaboration.network;

import collaboration.model.CollabPacket;
import collaboration.model.CursorEvent;
import collaboration.model.TextOpEvent;
import collaboration.model.VirtualFileNode;
import collaboration.sync.DocumentSyncCoordinator;
import collaboration.workspace.RemoteWorkspaceModel;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.java_websocket.WebSocket;
import org.java_websocket.drafts.Draft;
import org.java_websocket.exceptions.InvalidDataException;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.handshake.ServerHandshakeBuilder;
import org.java_websocket.server.WebSocketServer;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Host-side WebSocketServer running on loopback (127.0.0.1:[ephemeral-port]).
 * Enforces cryptographic handshake token validation and acts as the canonical
 * OT revision coordinator and packet router.
 */
public class CollaborationHostServer extends WebSocketServer {

    private final String sessionToken;
    private final File workspaceRoot;
    private final DocumentSyncCoordinator syncCoordinator;

    // Client tracking
    private final Map<WebSocket, ClientInfo> connectedClients = new ConcurrentHashMap<>();
    private static final String[] COLOR_PALETTE = {
            "#4ec9b0", "#ce9178", "#c586c0", "#dcdcaa", "#9cdcfe", "#b5cea8", "#ff79c6", "#50fa7b"
    };
    private int colorIndex = 0;

    // Callbacks to notify Host IDE
    private Consumer<TextOpEvent> onRemoteOpReceived;
    private Consumer<CursorEvent> onRemoteCursorReceived;
    private Consumer<ClientInfo> onClientConnected;
    private Consumer<String> onClientDisconnected;

    public record ClientInfo(String clientId, String clientName, String colorHex, WebSocket socket) {}

    public CollaborationHostServer(int port, String sessionToken, File workspaceRoot, DocumentSyncCoordinator syncCoordinator) {
        super(new InetSocketAddress("127.0.0.1", port));
        this.sessionToken = sessionToken;
        this.workspaceRoot = workspaceRoot;
        this.syncCoordinator = syncCoordinator;
        setReuseAddr(true);
        setTcpNoDelay(true);
    }

    @Override
    public ServerHandshakeBuilder onWebsocketHandshakeReceivedAsServer(
            WebSocket conn, Draft draft, ClientHandshake request) throws InvalidDataException {
        ServerHandshakeBuilder builder = super.onWebsocketHandshakeReceivedAsServer(conn, draft, request);

        // Security check: validate ?token= query parameter
        String descriptor = request.getResourceDescriptor();
        String queryToken = extractQueryParam(descriptor, "token");

        if (sessionToken != null && !sessionToken.isEmpty()) {
            if (queryToken == null || !queryToken.equals(sessionToken)) {
                throw new InvalidDataException(401, "Unauthorized: Invalid or missing session token");
            }
        }

        return builder;
    }

    private String extractQueryParam(String uri, String paramName) {
        if (uri == null || !uri.contains("?")) return null;
        String query = uri.substring(uri.indexOf("?") + 1);
        String[] pairs = query.split("&");
        for (String pair : pairs) {
            String[] kv = pair.split("=", 2);
            if (kv.length == 2 && kv[0].equalsIgnoreCase(paramName)) {
                return kv[1];
            }
        }
        return null;
    }

    @Override
    public void onOpen(WebSocket conn, ClientHandshake handshake) {
        // Connection opened after successful handshake authentication
    }

    @Override
    public void onClose(WebSocket conn, int code, String reason, boolean remote) {
        ClientInfo client = connectedClients.remove(conn);
        if (client != null) {
            CollabPacket notice = new CollabPacket(
                    CollabPacket.Type.PEER_LEFT, client.clientId(), client.clientName(), client.clientId()
            );
            broadcastExcept(conn, notice.toJson());
            if (onClientDisconnected != null) {
                onClientDisconnected.accept(client.clientId());
            }
        }
    }

    @Override
    public void onMessage(WebSocket conn, String message) {
        try {
            CollabPacket packet = CollabPacket.fromJson(message);
            if (packet == null || packet.getType() == null) return;

            switch (packet.getType()) {
                case HELLO -> handleHello(conn, packet);
                case FILE_REQUEST -> handleFileRequest(conn, packet);
                case OP_DELTA -> handleOpDelta(conn, packet);
                case CURSOR_MOVE -> handleCursorMove(conn, packet);
                case DISCONNECT -> conn.close(1000, "Client requested disconnect");
                default -> {}
            }
        } catch (Exception ignored) {}
    }

    private void handleHello(WebSocket conn, CollabPacket packet) {
        String clientId = UUID.randomUUID().toString().substring(0, 8);
        String clientName = packet.getSenderName() != null && !packet.getSenderName().isBlank()
                ? packet.getSenderName()
                : "Guest-" + clientId;

        String assignedColor = COLOR_PALETTE[Math.abs(colorIndex++ % COLOR_PALETTE.length)];
        ClientInfo client = new ClientInfo(clientId, clientName, assignedColor, conn);
        connectedClients.put(conn, client);

        // 1. Send AUTH_OK
        JsonObject authPayload = new JsonObject();
        authPayload.addProperty("clientId", clientId);
        authPayload.addProperty("colorHex", assignedColor);
        conn.send(new CollabPacket(CollabPacket.Type.AUTH_OK, "host", "Host", authPayload.toString()).toJson());

        // 2. Send initial TREE_SYNC
        VirtualFileNode tree = RemoteWorkspaceModel.buildFromLocalDirectory(workspaceRoot, 6);
        if (tree != null) {
            String treeJson = CollabPacket.getGson().toJson(tree);
            conn.send(new CollabPacket(CollabPacket.Type.TREE_SYNC, "host", "Host", treeJson).toJson());
        }

        // 3. Notify other peers
        CollabPacket joinNotice = new CollabPacket(
                CollabPacket.Type.PEER_JOINED, clientId, clientName, authPayload.toString()
        );
        broadcastExcept(conn, joinNotice.toJson());

        if (onClientConnected != null) {
            onClientConnected.accept(client);
        }
    }

    private void handleFileRequest(WebSocket conn, CollabPacket packet) {
        String relativePath = packet.getPayload();
        if (relativePath == null || relativePath.isBlank()) return;

        try {
            Path targetPath = workspaceRoot.toPath().resolve(relativePath).normalize();
            // Prevent path traversal outside workspace
            if (!targetPath.startsWith(workspaceRoot.toPath())) {
                return;
            }

            File file = targetPath.toFile();
            if (file.exists() && file.isFile()) {
                String content = Files.readString(targetPath, StandardCharsets.UTF_8);
                long currentRev = syncCoordinator.getCurrentRevision(relativePath);
                if (currentRev == 0 && syncCoordinator.getDocumentContent(relativePath).isEmpty()) {
                    syncCoordinator.initializeDocument(relativePath, content);
                } else {
                    content = syncCoordinator.getDocumentContent(relativePath);
                    currentRev = syncCoordinator.getCurrentRevision(relativePath);
                }

                JsonObject resp = new JsonObject();
                resp.addProperty("fileUri", relativePath);
                resp.addProperty("content", content);
                resp.addProperty("revision", currentRev);

                conn.send(new CollabPacket(CollabPacket.Type.FILE_RESPONSE, "host", "Host", resp.toString()).toJson());
            }
        } catch (IOException ignored) {}
    }

    private void handleOpDelta(WebSocket conn, CollabPacket packet) {
        TextOpEvent incomingOp = CollabPacket.getGson().fromJson(packet.getPayload(), TextOpEvent.class);
        if (incomingOp == null) return;

        // Apply OT rebasing via server coordinator
        TextOpEvent canonicalOp = syncCoordinator.processIncomingOp(incomingOp);

        // Broadcast canonical rebased op to all other connected clients
        CollabPacket broadcastPacket = new CollabPacket(
                CollabPacket.Type.OP_DELTA, incomingOp.originClientId(), packet.getSenderName(),
                CollabPacket.getGson().toJson(canonicalOp)
        );
        broadcastExcept(conn, broadcastPacket.toJson());

        // Notify Host IDE editor so local buffer updates in real time
        if (onRemoteOpReceived != null) {
            onRemoteOpReceived.accept(canonicalOp);
        }
    }

    private void handleCursorMove(WebSocket conn, CollabPacket packet) {
        CursorEvent cursor = CollabPacket.getGson().fromJson(packet.getPayload(), CursorEvent.class);
        if (cursor == null) return;

        // Broadcast cursor to all other peers
        broadcastExcept(conn, packet.toJson());

        if (onRemoteCursorReceived != null) {
            onRemoteCursorReceived.accept(cursor);
        }
    }

    /**
     * Broadcasts a text delta generated locally on the Host to all connected guests.
     */
    public void broadcastLocalHostOp(TextOpEvent hostOp) {
        TextOpEvent canonical = syncCoordinator.processIncomingOp(hostOp);
        CollabPacket packet = new CollabPacket(
                CollabPacket.Type.OP_DELTA, "host", "Host", CollabPacket.getGson().toJson(canonical)
        );
        broadcast(packet.toJson());
    }

    /**
     * Broadcasts cursor movement made locally on the Host.
     */
    public void broadcastLocalHostCursor(CursorEvent hostCursor) {
        CollabPacket packet = new CollabPacket(
                CollabPacket.Type.CURSOR_MOVE, "host", "Host", CollabPacket.getGson().toJson(hostCursor)
        );
        broadcast(packet.toJson());
    }

    private void broadcastExcept(WebSocket exclude, String text) {
        for (WebSocket client : getConnections()) {
            if (client != exclude && client.isOpen()) {
                client.send(text);
            }
        }
    }

    @Override
    public void onError(WebSocket conn, Exception ex) {
        // Non-fatal error handling
    }

    @Override
    public void onStart() {
        // Server started successfully
    }

    public List<ClientInfo> getConnectedGuests() {
        return new ArrayList<>(connectedClients.values());
    }

    public void setOnRemoteOpReceived(Consumer<TextOpEvent> listener) {
        this.onRemoteOpReceived = listener;
    }

    public void setOnRemoteCursorReceived(Consumer<CursorEvent> listener) {
        this.onRemoteCursorReceived = listener;
    }

    public void setOnClientConnected(Consumer<ClientInfo> listener) {
        this.onClientConnected = listener;
    }

    public void setOnClientDisconnected(Consumer<String> listener) {
        this.onClientDisconnected = listener;
    }
}
