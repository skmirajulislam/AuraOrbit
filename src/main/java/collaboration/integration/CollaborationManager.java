package collaboration.integration;

import collaboration.model.VirtualFileNode;
import collaboration.network.CollaborationGuestClient;
import collaboration.network.CollaborationHostServer;
import collaboration.network.TunnelProcessLauncher;
import collaboration.sync.DocumentSyncCoordinator;
import collaboration.sync.EditorSyncBridge;
import collaboration.ui.HostSessionDialog;
import collaboration.ui.JoinSessionDialog;
import collaboration.workspace.RemoteWorkspaceModel;
import javafx.application.Platform;
import javafx.stage.Stage;
import org.fxmisc.richtext.CodeArea;

import java.io.File;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Central state coordinator managing Host, Guest, Tunnel, and Editor Sync lifecycles.
 */
public class CollaborationManager {

    public enum Mode {
        INACTIVE,
        HOSTING,
        GUEST
    }

    private Mode currentMode = Mode.INACTIVE;

    // Host components
    private CollaborationHostServer hostServer;
    private TunnelProcessLauncher tunnelLauncher;
    private DocumentSyncCoordinator syncCoordinator;
    private HostSessionDialog hostDialog;

    // Guest components
    private CollaborationGuestClient guestClient;
    private RemoteWorkspaceModel remoteWorkspace;
    private JoinSessionDialog joinDialog;

    // Active editor bridges (fileUri -> EditorSyncBridge)
    private final Map<String, EditorSyncBridge> activeBridges = new ConcurrentHashMap<>();

    // IDE integration callbacks
    private Consumer<VirtualFileNode> onRemoteWorkspaceLoaded;
    private Consumer<String> onStatusNotification;

    public CollaborationManager() {
        this.syncCoordinator = new DocumentSyncCoordinator();
        this.remoteWorkspace = new RemoteWorkspaceModel();
    }

    public synchronized boolean isHosting() {
        return currentMode == Mode.HOSTING;
    }

    public synchronized boolean isGuest() {
        return currentMode == Mode.GUEST;
    }

    public synchronized boolean isConnected() {
        return currentMode != Mode.INACTIVE;
    }

    public synchronized Mode getMode() {
        return currentMode;
    }

    /**
     * Starts hosting a Live Share session on the local workspace directory.
     */
    public synchronized void startHosting(File workspaceDir, Stage ownerStage) {
        if (currentMode != Mode.INACTIVE) {
            stopSession();
        }

        try {
            int port = findFreePort();
            String sessionToken = UUID.randomUUID().toString();

            hostServer = new CollaborationHostServer(port, sessionToken, workspaceDir, syncCoordinator);
            hostServer.setOnClientConnected(client -> {
                notifyStatus("Collaborator joined: " + client.clientName());
                if (hostDialog != null) {
                    hostDialog.updateGuestList(hostServer.getConnectedGuests());
                }
            });
            hostServer.setOnClientDisconnected(clientId -> {
                notifyStatus("Collaborator left: " + clientId);
                if (hostDialog != null) {
                    hostDialog.updateGuestList(hostServer.getConnectedGuests());
                }
            });
            hostServer.setOnRemoteOpReceived(op -> {
                EditorSyncBridge bridge = activeBridges.get(op.fileUri());
                if (bridge != null) {
                    bridge.applyRemoteOp(op);
                }
            });

            hostServer.start();
            currentMode = Mode.HOSTING;

            // Open Host Dialog
            hostDialog = new HostSessionDialog(ownerStage, "Establishing Cloudflare tunnel...", this::stopSession);
            hostDialog.show();

            // Spawn Cloudflare Quick Tunnel
            tunnelLauncher = new TunnelProcessLauncher(port);
            tunnelLauncher.startAsync(30).thenAccept(publicUrl -> {
                String wssUrl = publicUrl.replace("https://", "wss://") + "/?token=" + sessionToken;
                if (hostDialog != null) {
                    hostDialog.setShareUrl(wssUrl);
                }
                notifyStatus("Live Share session ready!");
            }).exceptionally(ex -> {
                notifyStatus("Tunnel startup failed: " + ex.getMessage());
                return null;
            });

        } catch (Exception e) {
            notifyStatus("Failed to start collaboration host: " + e.getMessage());
            stopSession();
        }
    }

    /**
     * Joins an existing Live Share session via invite URL.
     */
    public synchronized void joinSession(String inviteUrl, String displayName, Stage ownerStage) {
        if (currentMode != Mode.INACTIVE) {
            stopSession();
        }

        try {
            URI uri = URI.create(inviteUrl.trim());
            guestClient = new CollaborationGuestClient(uri, displayName);

            guestClient.setOnAuthSuccess(clientId -> {
                currentMode = Mode.GUEST;
                notifyStatus("Connected as " + displayName + " (" + clientId + ")");
                if (joinDialog != null) {
                    joinDialog.close();
                }
            });

            guestClient.setOnTreeSyncReceived(tree -> {
                remoteWorkspace.setRootNode(tree);
                if (onRemoteWorkspaceLoaded != null) {
                    onRemoteWorkspaceLoaded.accept(tree);
                }
            });

            guestClient.setOnFileResponseReceived((fileUri, content, rev) -> {
                remoteWorkspace.setDocumentContent(fileUri, content, rev);
            });

            guestClient.setOnRemoteOpReceived(op -> {
                EditorSyncBridge bridge = activeBridges.get(op.fileUri());
                if (bridge != null) {
                    bridge.applyRemoteOp(op);
                }
            });

            guestClient.setOnDisconnectNotice(reason -> {
                notifyStatus("Disconnected from host: " + reason);
                stopSession();
            });

            guestClient.connect();

        } catch (Exception e) {
            if (joinDialog != null) {
                joinDialog.showError("Failed to connect: " + e.getMessage());
            }
        }
    }

    /**
     * Binds an open RichTextFX CodeArea editor to live collaboration.
     */
    public synchronized EditorSyncBridge bindEditor(CodeArea codeArea, String fileUri) {
        String clientId = isHosting() ? "host" : (guestClient != null ? guestClient.getAssignedClientId() : "anon");
        String clientName = isHosting() ? "Host" : (guestClient != null ? "Guest" : "Anon");
        String colorHex = isHosting() ? "#007acc" : (guestClient != null ? guestClient.getAssignedColorHex() : "#4ec9b0");

        EditorSyncBridge bridge = new EditorSyncBridge(codeArea, fileUri, clientId, clientName, colorHex);

        bridge.setOnLocalTextOp(op -> {
            if (isHosting() && hostServer != null) {
                hostServer.broadcastLocalHostOp(op);
            } else if (isGuest() && guestClient != null) {
                guestClient.sendTextDelta(op);
            }
        });

        bridge.setOnLocalCursorMove(cursor -> {
            if (isHosting() && hostServer != null) {
                hostServer.broadcastLocalHostCursor(cursor);
            } else if (isGuest() && guestClient != null) {
                guestClient.sendCursorMove(cursor);
            }
        });

        activeBridges.put(fileUri, bridge);
        return bridge;
    }

    public synchronized void unbindEditor(String fileUri) {
        EditorSyncBridge bridge = activeBridges.remove(fileUri);
        if (bridge != null) {
            bridge.dispose();
        }
    }

    /**
     * Shuts down all active collaboration resources.
     */
    public synchronized void stopSession() {
        if (tunnelLauncher != null) {
            tunnelLauncher.stop();
            tunnelLauncher = null;
        }

        if (hostServer != null) {
            try {
                hostServer.stop(1000);
            } catch (Exception ignored) {}
            hostServer = null;
        }

        if (guestClient != null) {
            try {
                guestClient.close();
            } catch (Exception ignored) {}
            guestClient = null;
        }

        for (EditorSyncBridge bridge : activeBridges.values()) {
            bridge.dispose();
        }
        activeBridges.clear();
        remoteWorkspace.clear();

        currentMode = Mode.INACTIVE;
        notifyStatus("Collaboration session closed.");
    }

    private int findFreePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            socket.setReuseAddress(true);
            return socket.getLocalPort();
        }
    }

    private void notifyStatus(String msg) {
        if (onStatusNotification != null) {
            Platform.runLater(() -> onStatusNotification.accept(msg));
        }
    }

    public RemoteWorkspaceModel getRemoteWorkspace() {
        return remoteWorkspace;
    }

    public void setOnRemoteWorkspaceLoaded(Consumer<VirtualFileNode> onRemoteWorkspaceLoaded) {
        this.onRemoteWorkspaceLoaded = onRemoteWorkspaceLoaded;
    }

    public void setOnStatusNotification(Consumer<String> onStatusNotification) {
        this.onStatusNotification = onStatusNotification;
    }
}
