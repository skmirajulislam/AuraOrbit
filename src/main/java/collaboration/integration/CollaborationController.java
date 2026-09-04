package collaboration.integration;

import collaboration.core.*;
import collaboration.network.*;
import collaboration.security.*;
import collaboration.sync.*;
import collaboration.ui.*;

/**
 * Main collaboration controller that orchestrates all components.
 * Integrates security, networking, sync, and UI.
 */
public class CollaborationController {
    private final JwtTokenManager tokenManager;
    private final RateLimiter rateLimiter;
    private final AuditLogger auditLogger;
    private final EncryptionManager encryptionManager;
    private final OperationalTransform otEngine;
    private final SyncEventProcessor syncProcessor;

    private CollaborationSession currentSession;
    private CollaborativeWebSocketServer server;
    private CollaborativeWebSocketClient client;
    private String currentUserId;
    private String currentUserName;

    public CollaborationController(String serverSecret) throws Exception {
        this.tokenManager = new JwtTokenManager(serverSecret);
        this.rateLimiter = new RateLimiter(50, 100); // 50 ops/sec, burst 100
        this.auditLogger = new AuditLogger();
        this.encryptionManager = new EncryptionManager();
        this.otEngine = new OperationalTransform();
        this.syncProcessor = new SyncEventProcessor();
    }

    /**
     * Start hosting a collaborative session.
     */
    public void startHostingSession(String sessionId, int port) throws Exception {
        // Create session
        currentSession = new CollaborationSession(sessionId, currentUserId);
        currentSession.addUser(currentUserId, currentUserName, true);

        // Set up server
        server = new CollaborativeWebSocketServer(port, sessionId);
        server.start();

        // Log event
        auditLogger.log(currentUserId, sessionId,
                AuditLogger.EventType.SESSION_CREATED,
                "Hosted on port " + port);
    }

    /**
     * Join an existing collaborative session.
     */
    public void joinSession(String host, int port, String userName,
                           String sessionId) throws Exception {
        currentUserName = userName;
        currentUserId = generateUserId();

        // Create client connection
        client = new CollaborativeWebSocketClient(sessionId, host, port, currentUserId, userName);
        client.setOnMessageReceived(this::handleRemoteEdit);
        boolean connected = client.connect();
        
        if (!connected) {
            throw new Exception("Failed to connect to collaboration server");
        }

        // Log event
        auditLogger.log(currentUserId, sessionId,
                AuditLogger.EventType.SESSION_JOINED,
                "User: " + userName);
    }

    /**
     * Handle a local edit from the user.
     */
    public void applyLocalEdit(String userId, int position, String content,
                              int deleteLength) throws Exception {
        if (!rateLimiter.allowOperation(userId)) {
            auditLogger.log(userId, currentSession.getSessionId(),
                    AuditLogger.EventType.RATE_LIMIT_EXCEEDED,
                    "User exceeded rate limit");
            throw new SecurityException("Rate limit exceeded");
        }

        // Apply to session (includes permission check)
        currentSession.applyEdit(userId, position, content, deleteLength);

        // Log event
        auditLogger.log(userId, currentSession.getSessionId(),
                AuditLogger.EventType.DOCUMENT_EDITED,
                "Pos: " + position + ", Del: " + deleteLength);
    }

    /**
     * Handle a remote edit from another user.
     */
    private void handleRemoteEdit(String message) {
        try {
            // Parse operation from message
            // This is simplified; in production, deserialize properly
            SyncEventProcessor.SyncEvent event = new SyncEventProcessor.SyncEvent(
                    null, // Parse from message
                    ""    // Extract source user
            );

            syncProcessor.processNextEvent();
        } catch (Exception ex) {
            auditLogger.log("", currentSession.getSessionId(),
                    AuditLogger.EventType.SECURITY_VIOLATION,
                    "Failed to process remote edit: " + ex.getMessage());
        }
    }

    /**
     * Grant permission to a guest.
     */
    public void grantPermission(String userId,
                               CollaborationSession.PermissionLevel permission) throws Exception {
        if (currentSession == null) {
            throw new IllegalStateException("No active session");
        }

        currentSession.grantPermission(userId, permission);
        auditLogger.log(currentUserId, currentSession.getSessionId(),
                AuditLogger.EventType.PERMISSION_GRANTED,
                userId + " -> " + permission);
    }

    /**
     * Revoke permission from a guest.
     */
    public void revokePermission(String userId) throws Exception {
        if (currentSession == null) {
            throw new IllegalStateException("No active session");
        }

        currentSession.revokePermission(userId);
        auditLogger.log(currentUserId, currentSession.getSessionId(),
                AuditLogger.EventType.PERMISSION_REVOKED,
                userId);
    }

    /**
     * Disconnect from session.
     */
    public void disconnect() throws Exception {
        if (server != null) {
            server.shutdown();
            server = null;
        }

        if (client != null) {
            client.disconnect();
            client = null;
        }

        if (currentSession != null) {
            auditLogger.log(currentUserId, currentSession.getSessionId(),
                    AuditLogger.EventType.SESSION_CLOSED,
                    "Session ended");
            currentSession = null;
        }

        tokenManager.revokeUserTokens(currentUserId);
        syncProcessor.shutdown();
    }

    /**
     * Update user cursor position.
     */
    public void updateCursorPosition(int line, int column) {
        if (currentSession != null) {
            currentSession.updateCursorPosition(currentUserId, line, column);
        }
    }

    /**
     * Get current session.
     */
    public CollaborationSession getCurrentSession() {
        return currentSession;
    }

    /**
     * Get audit log.
     */
    public AuditLogger getAuditLogger() {
        return auditLogger;
    }

    /**
     * Get rate limiter.
     */
    public RateLimiter getRateLimiter() {
        return rateLimiter;
    }

    /**
     * Check if connected.
     */
    public boolean isConnected() {
        return currentSession != null && (server != null || client != null);
    }

    /**
     * Check if hosting.
     */
    public boolean isHosting() {
        return currentSession != null && server != null;
    }

    private String generateSessionId() {
        return "session_" + System.currentTimeMillis();
    }

    private String generateUserId() {
        return "user_" + System.nanoTime();
    }
}
