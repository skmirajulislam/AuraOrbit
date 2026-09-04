package collaboration.security;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Audit logging for security and compliance.
 * Tracks all collaboration events for audit trails.
 */
public class AuditLogger {
    private final List<AuditEntry> auditLog;
    private final Map<String, List<AuditEntry>> userAuditIndex;
    private static final int MAX_LOG_SIZE = 10000;

    public enum EventType {
        SESSION_CREATED,
        SESSION_JOINED,
        SESSION_CLOSED,
        USER_LEFT,
        PERMISSION_GRANTED,
        PERMISSION_REVOKED,
        DOCUMENT_EDITED,
        AUTHENTICATION_FAILED,
        RATE_LIMIT_EXCEEDED,
        SECURITY_VIOLATION,
        TOKEN_ISSUED,
        TOKEN_REVOKED,
        ENCRYPTION_ENABLED,
        MESSAGE_SENT,
        FILE_SHARED
    }

    public AuditLogger() {
        this.auditLog = Collections.synchronizedList(new ArrayList<>());
        this.userAuditIndex = new ConcurrentHashMap<>();
    }

    /**
     * Log an event.
     */
    public void log(String userId, String sessionId, EventType eventType, String details) {
        AuditEntry entry = new AuditEntry(
                System.currentTimeMillis(),
                userId,
                sessionId,
                eventType,
                details
        );

        auditLog.add(entry);
        userAuditIndex.computeIfAbsent(userId, k -> new ArrayList<>()).add(entry);

        // Trim log if too large
        if (auditLog.size() > MAX_LOG_SIZE) {
            AuditEntry removed = auditLog.remove(0);
            List<AuditEntry> userLog = userAuditIndex.get(removed.userId);
            if (userLog != null) {
                userLog.remove(removed);
            }
        }
    }

    /**
     * Get audit entries for a user.
     */
    public List<AuditEntry> getUserAuditTrail(String userId) {
        return new ArrayList<>(userAuditIndex.getOrDefault(userId, new ArrayList<>()));
    }

    /**
     * Get audit entries for a session.
     */
    public List<AuditEntry> getSessionAuditTrail(String sessionId) {
        List<AuditEntry> result = new ArrayList<>();
        synchronized (auditLog) {
            for (AuditEntry entry : auditLog) {
                if (entry.sessionId.equals(sessionId)) {
                    result.add(entry);
                }
            }
        }
        return result;
    }

    /**
     * Get security violations.
     */
    public List<AuditEntry> getSecurityViolations(String userId) {
        List<AuditEntry> result = new ArrayList<>();
        List<AuditEntry> userLog = userAuditIndex.get(userId);
        if (userLog != null) {
            for (AuditEntry entry : userLog) {
                if (entry.eventType == EventType.SECURITY_VIOLATION ||
                    entry.eventType == EventType.AUTHENTICATION_FAILED ||
                    entry.eventType == EventType.RATE_LIMIT_EXCEEDED) {
                    result.add(entry);
                }
            }
        }
        return result;
    }

    /**
     * Get recent events.
     */
    public List<AuditEntry> getRecentEvents(int count) {
        List<AuditEntry> recent = new ArrayList<>();
        synchronized (auditLog) {
            int start = Math.max(0, auditLog.size() - count);
            recent.addAll(auditLog.subList(start, auditLog.size()));
        }
        return recent;
    }

    /**
     * Export audit log as CSV.
     */
    public String exportAsCsv() {
        StringBuilder csv = new StringBuilder();
        csv.append("Timestamp,User ID,Session ID,Event Type,Details\n");

        synchronized (auditLog) {
            for (AuditEntry entry : auditLog) {
                csv.append(String.format("%d,%s,%s,%s,\"%s\"\n",
                        entry.timestamp,
                        entry.userId,
                        entry.sessionId,
                        entry.eventType,
                        entry.details.replace("\"", "\\\"")));
            }
        }

        return csv.toString();
    }

    /**
     * Audit log entry.
     */
    public static class AuditEntry {
        public final long timestamp;
        public final String userId;
        public final String sessionId;
        public final EventType eventType;
        public final String details;

        public AuditEntry(long timestamp, String userId, String sessionId,
                         EventType eventType, String details) {
            this.timestamp = timestamp;
            this.userId = userId;
            this.sessionId = sessionId;
            this.eventType = eventType;
            this.details = details;
        }

        @Override
        public String toString() {
            return String.format("[%d] %s (%s): %s - %s",
                    timestamp, userId, eventType, sessionId, details);
        }
    }
}
