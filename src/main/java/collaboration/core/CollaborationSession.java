package collaboration.core;

import java.util.*;
import java.util.concurrent.*;

/**
 * Manages a collaborative editing session with real-time synchronization.
 * Supports multiple users, permissions, and presence tracking.
 */
public class CollaborationSession {
    private final String sessionId;
    private final String hostId;
    private final Map<String, RemoteUser> activeUsers;
    private final DocumentState documentState;
    private final PermissionManager permissionManager;
    private final UserPresenceTracker presenceTracker;
    private final BlockingQueue<SyncEvent> eventQueue;
    private volatile boolean isActive;

    public CollaborationSession(String sessionId, String hostId) {
        this.sessionId = sessionId;
        this.hostId = hostId;
        this.activeUsers = new ConcurrentHashMap<>();
        this.documentState = new DocumentState();
        this.permissionManager = new PermissionManager();
        this.presenceTracker = new UserPresenceTracker();
        this.eventQueue = new LinkedBlockingQueue<>(1000);
        this.isActive = true;
    }

    public String getSessionId() {
        return sessionId;
    }

    public String getHostId() {
        return hostId;
    }

    public void addUser(String userId, String userName, boolean isHost) {
        RemoteUser user = new RemoteUser(userId, userName, isHost);
        activeUsers.put(userId, user);
        presenceTracker.updateUserPresence(userId, user.toPresenceData());
        broadcastEvent(new SyncEvent("user_joined", Map.of(
                "user_id", userId,
                "user_name", userName,
                "is_host", isHost
        )));
    }

    public void removeUser(String userId) {
        activeUsers.remove(userId);
        presenceTracker.removeUserPresence(userId);
        broadcastEvent(new SyncEvent("user_left", Map.of("user_id", userId)));
    }

    public void applyEdit(String userId, int position, String content, int deleteLength) throws Exception {
        if (!canWrite(userId)) {
            throw new SecurityException("User " + userId + " does not have write permission");
        }

        documentState.applyEdit(position, content, deleteLength);
        presenceTracker.updateCursorPosition(userId, position, 0);

        broadcastEvent(new SyncEvent("edit", Map.of(
                "user_id", userId,
                "position", position,
                "content", content,
                "delete_length", deleteLength
        )));
    }

    public void updateCursorPosition(String userId, int line, int column) {
        presenceTracker.updateUserCursor(userId, line, column);
        broadcastEvent(new SyncEvent("cursor_update", Map.of(
                "user_id", userId,
                "line", line,
                "column", column
        )));
    }

    public void grantPermission(String targetUserId, PermissionLevel level) throws Exception {
        if (!isHostOrHasPermission(getLastActiveUser(), PermissionLevel.ADMIN)) {
            throw new SecurityException("Only host can grant permissions");
        }
        permissionManager.grantPermission(targetUserId, level);
        broadcastEvent(new SyncEvent("permission_granted", Map.of(
                "user_id", targetUserId,
                "level", level.name()
        )));
    }

    public void revokePermission(String targetUserId) throws Exception {
        if (!isHostOrHasPermission(getLastActiveUser(), PermissionLevel.ADMIN)) {
            throw new SecurityException("Only host can revoke permissions");
        }
        permissionManager.revokePermission(targetUserId);
        broadcastEvent(new SyncEvent("permission_revoked", Map.of(
                "user_id", targetUserId
        )));
    }

    public boolean canWrite(String userId) {
        PermissionLevel level = permissionManager.getPermissionLevel(userId);
        return level == PermissionLevel.WRITE || level == PermissionLevel.ADMIN;
    }

    public boolean canRead(String userId) {
        PermissionLevel level = permissionManager.getPermissionLevel(userId);
        return level != PermissionLevel.NONE;
    }

    private boolean isHostOrHasPermission(String userId, PermissionLevel required) {
        if (userId.equals(hostId)) return true;
        PermissionLevel userLevel = permissionManager.getPermissionLevel(userId);
        return userLevel.ordinal() >= required.ordinal();
    }

    public void broadcastEvent(SyncEvent event) {
        try {
            eventQueue.offer(event);
        } catch (Exception ex) {
            // Queue full - skip oldest events
            eventQueue.poll();
            eventQueue.offer(event);
        }
    }

    public SyncEvent pollEvent(long timeout, TimeUnit unit) throws InterruptedException {
        return eventQueue.poll(timeout, unit);
    }

    public String getDocumentContent() {
        return documentState.getContent();
    }

    public Map<String, RemoteUser> getActiveUsers() {
        return Collections.unmodifiableMap(activeUsers);
    }

    public UserPresenceData getUserPresence(String userId) {
        return presenceTracker.getUserPresence(userId);
    }

    public void shutdown() {
        isActive = false;
        eventQueue.clear();
        activeUsers.clear();
    }

    public boolean isActive() {
        return isActive;
    }

    private String getLastActiveUser() {
        return activeUsers.keySet().stream().findFirst().orElse(hostId);
    }

    public enum PermissionLevel {
        NONE, READ, WRITE, ADMIN
    }

    public record SyncEvent(String type, Map<String, Object> data) {}

    public record RemoteUser(String id, String name, boolean isHost) {
        public Map<String, Object> toPresenceData() {
            return Map.of("id", id, "name", name, "is_host", isHost);
        }
    }

    public record UserPresenceData(String userId, int line, int column, String cursorColor) {}
}
