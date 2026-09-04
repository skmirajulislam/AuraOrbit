package collaboration.core;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages user permissions within a collaboration session.
 */
public class PermissionManager {
    private final Map<String, CollaborationSession.PermissionLevel> permissions;

    public PermissionManager() {
        this.permissions = new ConcurrentHashMap<>();
    }

    public void grantPermission(String userId, CollaborationSession.PermissionLevel level) {
        permissions.put(userId, level);
    }

    public void revokePermission(String userId) {
        permissions.remove(userId);
    }

    public CollaborationSession.PermissionLevel getPermissionLevel(String userId) {
        return permissions.getOrDefault(userId, CollaborationSession.PermissionLevel.NONE);
    }

    public boolean hasPermission(String userId, CollaborationSession.PermissionLevel required) {
        CollaborationSession.PermissionLevel userLevel = getPermissionLevel(userId);
        return userLevel.ordinal() >= required.ordinal();
    }

    public Map<String, CollaborationSession.PermissionLevel> getAllPermissions() {
        return Collections.unmodifiableMap(new HashMap<>(permissions));
    }
}
