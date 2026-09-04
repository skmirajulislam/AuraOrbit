package collaboration.core;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks real-time presence and cursor positions of all collaborators.
 */
public class UserPresenceTracker {
    private static final String[] CURSOR_COLORS = {"#E91E63", "#2196F3", "#4CAF50", "#FFC107", "#9C27B0", "#00BCD4"};

    private final Map<String, UserPresence> presences;
    private int colorIndex = 0;

    public UserPresenceTracker() {
        this.presences = new ConcurrentHashMap<>();
    }

    public void updateUserPresence(String userId, Map<String, Object> data) {
        presences.computeIfAbsent(userId, k -> new UserPresence(
                userId,
                (String) data.get("name"),
                CURSOR_COLORS[colorIndex++ % CURSOR_COLORS.length],
                (boolean) data.getOrDefault("is_host", false)
        ));
    }

    public void updateCursorPosition(String userId, int line, int column) {
        UserPresence presence = presences.get(userId);
        if (presence != null) {
            presence.updateCursor(line, column);
        }
    }

    public void updateUserCursor(String userId, int line, int column) {
        updateCursorPosition(userId, line, column);
    }

    public void removeUserPresence(String userId) {
        presences.remove(userId);
    }

    public CollaborationSession.UserPresenceData getUserPresence(String userId) {
        UserPresence presence = presences.get(userId);
        if (presence != null) {
            return new CollaborationSession.UserPresenceData(
                    userId,
                    presence.line,
                    presence.column,
                    presence.cursorColor
            );
        }
        return null;
    }

    public Collection<UserPresence> getAllPresences() {
        return Collections.unmodifiableCollection(presences.values());
    }

    public Map<String, UserPresence> getPresencesMap() {
        return Collections.unmodifiableMap(new HashMap<>(presences));
    }

    public static class UserPresence {
        public final String userId;
        public final String userName;
        public final String cursorColor;
        public final boolean isHost;
        public volatile int line;
        public volatile int column;
        public volatile long lastUpdate;

        public UserPresence(String userId, String userName, String cursorColor, boolean isHost) {
            this.userId = userId;
            this.userName = userName;
            this.cursorColor = cursorColor;
            this.isHost = isHost;
            this.line = 0;
            this.column = 0;
            this.lastUpdate = System.currentTimeMillis();
        }

        public void updateCursor(int line, int column) {
            this.line = line;
            this.column = column;
            this.lastUpdate = System.currentTimeMillis();
        }

        public boolean isStale(long timeoutMs) {
            return System.currentTimeMillis() - lastUpdate > timeoutMs;
        }
    }
}
