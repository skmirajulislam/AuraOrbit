package collaboration.security;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Rate limiter to prevent DoS attacks and abuse.
 * Uses token bucket algorithm for per-user rate limiting.
 */
public class RateLimiter {
    private static final int DEFAULT_REQUESTS_PER_SECOND = 50;
    private static final int DEFAULT_BURST_SIZE = 100;
    private static final long CLEANUP_INTERVAL_MS = 60000; // 1 minute

    private final int requestsPerSecond;
    private final int burstSize;
    private final Map<String, UserBucket> buckets;
    private long lastCleanup;

    public RateLimiter() {
        this(DEFAULT_REQUESTS_PER_SECOND, DEFAULT_BURST_SIZE);
    }

    public RateLimiter(int requestsPerSecond, int burstSize) {
        this.requestsPerSecond = requestsPerSecond;
        this.burstSize = burstSize;
        this.buckets = new ConcurrentHashMap<>();
        this.lastCleanup = System.currentTimeMillis();
    }

    /**
     * Check if a user can perform an operation.
     * Returns true if allowed, false if rate limit exceeded.
     */
    public boolean allowOperation(String userId) {
        cleanupIfNeeded();

        UserBucket bucket = buckets.computeIfAbsent(userId, k ->
                new UserBucket(requestsPerSecond, burstSize)
        );

        return bucket.tryConsume();
    }

    /**
     * Check rate limit without consuming a token.
     */
    public boolean isAllowed(String userId) {
        UserBucket bucket = buckets.get(userId);
        if (bucket == null) return true;
        return bucket.canConsume();
    }

    /**
     * Get remaining requests for a user.
     */
    public int getRemainingRequests(String userId) {
        UserBucket bucket = buckets.get(userId);
        if (bucket == null) return requestsPerSecond;
        return bucket.getAvailableTokens();
    }

    /**
     * Reset rate limit for a user (admin operation).
     */
    public void resetUser(String userId) {
        buckets.remove(userId);
    }

    /**
     * Reset all rate limits.
     */
    public void resetAll() {
        buckets.clear();
    }

    private void cleanupIfNeeded() {
        long now = System.currentTimeMillis();
        if (now - lastCleanup > CLEANUP_INTERVAL_MS) {
            buckets.entrySet().removeIf(entry -> entry.getValue().isInactive(5000));
            lastCleanup = now;
        }
    }

    /**
     * Token bucket for a single user.
     */
    private static class UserBucket {
        private final int tokensPerSecond;
        private final int maxTokens;
        private volatile double availableTokens;
        private volatile long lastRefillTime;
        private final AtomicInteger consecutiveViolations;

        UserBucket(int tokensPerSecond, int maxTokens) {
            this.tokensPerSecond = tokensPerSecond;
            this.maxTokens = maxTokens;
            this.availableTokens = maxTokens;
            this.lastRefillTime = System.currentTimeMillis();
            this.consecutiveViolations = new AtomicInteger(0);
        }

        synchronized boolean tryConsume() {
            refillTokens();
            if (availableTokens >= 1.0) {
                availableTokens -= 1.0;
                consecutiveViolations.set(0);
                return true;
            } else {
                consecutiveViolations.incrementAndGet();
                return false;
            }
        }

        synchronized boolean canConsume() {
            refillTokens();
            return availableTokens >= 1.0;
        }

        synchronized int getAvailableTokens() {
            refillTokens();
            return (int) Math.floor(availableTokens);
        }

        private synchronized void refillTokens() {
            long now = System.currentTimeMillis();
            long timePassed = now - lastRefillTime;
            double tokensToAdd = (timePassed / 1000.0) * tokensPerSecond;

            availableTokens = Math.min(maxTokens, availableTokens + tokensToAdd);
            lastRefillTime = now;
        }

        boolean isInactive(long timeoutMs) {
            return System.currentTimeMillis() - lastRefillTime > timeoutMs;
        }
    }
}
