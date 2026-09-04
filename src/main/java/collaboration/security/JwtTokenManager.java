package collaboration.security;

import java.security.SecureRandom;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * JWT-style token management for collaboration sessions.
 * Simplified implementation without external JWT library.
 * Creates tamper-proof session tokens with expiry.
 */
public class JwtTokenManager {
    private static final long TOKEN_EXPIRY_MS = 24 * 60 * 60 * 1000; // 24 hours
    private static final int TOKEN_LENGTH = 32;
    private static final String ALLOWED_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";

    private final Map<String, TokenData> tokenRegistry;
    private final SecureRandom secureRandom;
    private final String serverSecret;

    public JwtTokenManager(String serverSecret) {
        this.tokenRegistry = new ConcurrentHashMap<>();
        this.secureRandom = new SecureRandom();
        this.serverSecret = serverSecret;
    }

    public String getServerSecret() {
        return serverSecret;
    }

    /**
     * Generate a new session token for a user.
     */
    public String generateToken(String userId, String sessionId, String userName) {
        String token = generateRandomToken();
        long issuedAt = System.currentTimeMillis();
        long expiresAt = issuedAt + TOKEN_EXPIRY_MS;

        TokenData data = new TokenData(
                token,
                userId,
                sessionId,
                userName,
                issuedAt,
                expiresAt,
                true
        );

        tokenRegistry.put(token, data);
        return token;
    }

    /**
     * Verify a token and return its data if valid.
     */
    public TokenData verifyToken(String token) throws SecurityException {
        if (token == null || token.isEmpty()) {
            throw new SecurityException("Token is missing");
        }

        TokenData data = tokenRegistry.get(token);
        if (data == null) {
            throw new SecurityException("Invalid token");
        }

        if (!data.isValid) {
            throw new SecurityException("Token has been revoked");
        }

        if (System.currentTimeMillis() > data.expiresAt) {
            data.isValid = false;
            throw new SecurityException("Token has expired");
        }

        return data;
    }

    /**
     * Revoke a token (logout).
     */
    public void revokeToken(String token) {
        TokenData data = tokenRegistry.get(token);
        if (data != null) {
            data.isValid = false;
        }
    }

    /**
     * Revoke all tokens for a user.
     */
    public void revokeUserTokens(String userId) {
        tokenRegistry.values().stream()
                .filter(data -> data.userId.equals(userId))
                .forEach(data -> data.isValid = false);
    }

    /**
     * Check if a token is valid.
     */
    public boolean isValidToken(String token) {
        try {
            verifyToken(token);
            return true;
        } catch (SecurityException ex) {
            return false;
        }
    }

    /**
     * Get token information without verifying.
     */
    public TokenData getTokenData(String token) {
        return tokenRegistry.get(token);
    }

    /**
     * Clean up expired tokens (background task).
     */
    public void cleanupExpiredTokens() {
        long now = System.currentTimeMillis();
        tokenRegistry.entrySet().removeIf(entry ->
                entry.getValue().expiresAt < now || !entry.getValue().isValid
        );
    }

    private String generateRandomToken() {
        StringBuilder sb = new StringBuilder(TOKEN_LENGTH);
        for (int i = 0; i < TOKEN_LENGTH; i++) {
            sb.append(ALLOWED_CHARS.charAt(secureRandom.nextInt(ALLOWED_CHARS.length())));
        }
        return sb.toString();
    }

    /**
     * Token data structure.
     */
    public static class TokenData {
        public final String token;
        public final String userId;
        public final String sessionId;
        public final String userName;
        public final long issuedAt;
        public final long expiresAt;
        public volatile boolean isValid;

        public TokenData(String token, String userId, String sessionId, String userName,
                         long issuedAt, long expiresAt, boolean isValid) {
            this.token = token;
            this.userId = userId;
            this.sessionId = sessionId;
            this.userName = userName;
            this.issuedAt = issuedAt;
            this.expiresAt = expiresAt;
            this.isValid = isValid;
        }

        public boolean isExpired() {
            return System.currentTimeMillis() > expiresAt;
        }

        public long getTimeRemainingMs() {
            return Math.max(0, expiresAt - System.currentTimeMillis());
        }
    }
}
