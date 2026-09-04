package collaboration.sync;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Processes sync events from multiple users in order.
 * Handles operation transformation and conflict resolution.
 */
public class SyncEventProcessor {
    private static final int QUEUE_CAPACITY = 1000;
    private static final long TIMEOUT_MS = 5000;

    private final BlockingQueue<SyncEvent> eventQueue;
    private final Map<String, Integer> userRevisions; // Track each user's revision number
    private final List<OperationalTransform.Operation> history; // Full operation history
    private volatile int documentRevision;
    private volatile boolean shutdown;

    public SyncEventProcessor() {
        this.eventQueue = new LinkedBlockingQueue<>(QUEUE_CAPACITY);
        this.userRevisions = new ConcurrentHashMap<>();
        this.history = Collections.synchronizedList(new ArrayList<>());
        this.documentRevision = 0;
    }

    /**
     * Queue a sync event for processing.
     */
    public void queueEvent(SyncEvent event) throws InterruptedException {
        if (!eventQueue.offer(event, TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
            throw new InterruptedException("Event queue is full");
        }
    }

    /**
     * Process the next event in the queue.
     * Returns the processed operation after conflict resolution.
     */
    public OperationalTransform.Operation processNextEvent() throws InterruptedException {
        SyncEvent event = eventQueue.poll(TIMEOUT_MS, TimeUnit.MILLISECONDS);
        if (event == null) {
            return null;
        }

        return processEvent(event);
    }

    /**
     * Process an event immediately (non-blocking).
     */
    public OperationalTransform.Operation processEventNow(SyncEvent event) {
        synchronized (this) {
            return processEvent(event);
        }
    }

    private OperationalTransform.Operation processEvent(SyncEvent event) {
        OperationalTransform.Operation op = event.operation;

        // If operation is not at the current revision, transform it
        if (op.revision < documentRevision) {
            // Transform against all operations since this user's edit
            for (int i = op.revision; i < documentRevision; i++) {
                OperationalTransform.Operation historyOp = history.get(i);
                op = OperationalTransform.transform(op, historyOp);
            }
        }

        // Record this operation in history
        history.add(op);
        documentRevision++;
        userRevisions.put(op.userId, documentRevision);

        return op;
    }

    /**
     * Get the full operation history.
     */
    public List<OperationalTransform.Operation> getHistory() {
        return new ArrayList<>(history);
    }

    /**
     * Get operations since a specific revision.
     */
    public List<OperationalTransform.Operation> getOperationsSince(int revision) {
        if (revision < 0 || revision > documentRevision) {
            return new ArrayList<>();
        }
        return new ArrayList<>(history.subList(revision, history.size()));
    }

    /**
     * Get current document revision.
     */
    public int getCurrentRevision() {
        return documentRevision;
    }

    /**
     * Get pending events in queue.
     */
    public int getPendingEventCount() {
        return eventQueue.size();
    }

    /**
     * Clear the queue (used for reconnection).
     */
    public void clearQueue() {
        eventQueue.clear();
    }

    /**
     * Shutdown the processor.
     */
    public void shutdown() {
        shutdown = true;
        eventQueue.clear();
    }

    public boolean isShutdown() {
        return shutdown;
    }

    /**
     * Sync event wrapper.
     */
    public static class SyncEvent {
        public final OperationalTransform.Operation operation;
        public final String sourceUserId;
        public final long receivedAt;

        public SyncEvent(OperationalTransform.Operation operation, String sourceUserId) {
            this.operation = operation;
            this.sourceUserId = sourceUserId;
            this.receivedAt = System.currentTimeMillis();
        }

        @Override
        public String toString() {
            return String.format("SyncEvent[%s from %s at %d]", operation, sourceUserId, receivedAt);
        }
    }
}
