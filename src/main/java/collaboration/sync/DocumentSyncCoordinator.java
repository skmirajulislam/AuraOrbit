package collaboration.sync;

import collaboration.model.TextOpEvent;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Server-authoritative Operational Transformation (OT) and revision coordinator.
 * Maintains monotonic sequence counters per file and rebases stale incoming deltas
 * against intervening revision logs.
 */
public class DocumentSyncCoordinator {

    // fileUri -> monotonic revision counter
    private final Map<String, AtomicLong> fileRevisions = new ConcurrentHashMap<>();
    // fileUri -> history of executed operations for OT rebasing
    private final Map<String, List<TextOpEvent>> operationHistory = new ConcurrentHashMap<>();
    // fileUri -> current authoritative document string
    private final Map<String, StringBuilder> authoritativeBuffers = new ConcurrentHashMap<>();

    private static final int MAX_HISTORY_SIZE = 500;

    /**
     * Initializes or updates the authoritative document buffer when opened by the host.
     */
    public synchronized void initializeDocument(String fileUri, String initialContent) {
        authoritativeBuffers.put(fileUri, new StringBuilder(initialContent));
        fileRevisions.put(fileUri, new AtomicLong(0));
        operationHistory.put(fileUri, Collections.synchronizedList(new ArrayList<>()));
    }

    public synchronized String getDocumentContent(String fileUri) {
        StringBuilder sb = authoritativeBuffers.get(fileUri);
        return sb != null ? sb.toString() : "";
    }

    public synchronized long getCurrentRevision(String fileUri) {
        AtomicLong rev = fileRevisions.get(fileUri);
        return rev != null ? rev.get() : 0L;
    }

    /**
     * Submits an incoming edit event, applies server-authoritative Operational Transformation
     * if the incoming revision is stale, updates the local authoritative buffer, and returns
     * the canonical rebased TextOpEvent ready for peer broadcast.
     */
    public synchronized TextOpEvent processIncomingOp(TextOpEvent incoming) {
        String uri = incoming.fileUri();
        AtomicLong revCounter = fileRevisions.computeIfAbsent(uri, k -> new AtomicLong(0));
        List<TextOpEvent> history = operationHistory.computeIfAbsent(uri, k -> Collections.synchronizedList(new ArrayList<>()));
        StringBuilder buffer = authoritativeBuffers.computeIfAbsent(uri, k -> new StringBuilder());

        long hostRev = revCounter.get();
        TextOpEvent transformed = incoming;

        // If client operated on an older revision, rebase against all intervening operations
        if (incoming.revision() < hostRev) {
            int transformedOffset = incoming.offset();
            int transformedLength = incoming.length();

            for (TextOpEvent priorOp : history) {
                if (priorOp.revision() >= incoming.revision()) {
                    // Rebase offset based on prior operation
                    transformedOffset = transformOffset(transformedOffset, priorOp);
                }
            }

            transformed = new TextOpEvent(
                    incoming.fileUri(),
                    hostRev + 1,
                    Math.max(0, transformedOffset),
                    transformedLength,
                    incoming.insertedText(),
                    incoming.deletedText(),
                    incoming.originClientId()
            );
        } else {
            transformed = new TextOpEvent(
                    incoming.fileUri(),
                    hostRev + 1,
                    incoming.offset(),
                    incoming.length(),
                    incoming.insertedText(),
                    incoming.deletedText(),
                    incoming.originClientId()
            );
        }

        // Apply to canonical server buffer
        applyToBuffer(buffer, transformed);

        // Advance monotonic counter
        revCounter.incrementAndGet();

        // Record in circular bounded history
        history.add(transformed);
        if (history.size() > MAX_HISTORY_SIZE) {
            history.remove(0);
        }

        return transformed;
    }

    private int transformOffset(int targetOffset, TextOpEvent priorOp) {
        int priorStart = priorOp.offset();
        int priorDelLen = priorOp.deletedText() != null ? priorOp.deletedText().length() : 0;
        int priorInsLen = priorOp.insertedText() != null ? priorOp.insertedText().length() : 0;
        int netDelta = priorInsLen - priorDelLen;

        // If prior edit was strictly before target, shift target offset by net change
        if (priorStart + priorDelLen <= targetOffset) {
            return targetOffset + netDelta;
        }
        // If prior edit overlaps target offset, clamp to start
        if (priorStart < targetOffset) {
            return priorStart + priorInsLen;
        }
        return targetOffset;
    }

    private void applyToBuffer(StringBuilder sb, TextOpEvent op) {
        int start = Math.min(op.offset(), sb.length());
        int end = Math.min(start + op.length(), sb.length());

        if (start <= end && start >= 0) {
            sb.delete(start, end);
        }
        if (op.insertedText() != null && !op.insertedText().isEmpty()) {
            sb.insert(Math.min(start, sb.length()), op.insertedText());
        }
    }

    public synchronized void closeDocument(String fileUri) {
        authoritativeBuffers.remove(fileUri);
        fileRevisions.remove(fileUri);
        operationHistory.remove(fileUri);
    }
}
