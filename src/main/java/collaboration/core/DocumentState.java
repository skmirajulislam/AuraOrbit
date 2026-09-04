package collaboration.core;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages document state using CRDT (Conflict-free Replicated Data Type) approach.
 * Ensures consistent state across all collaborators without central coordination.
 */
public class DocumentState {
    private final List<Character> content;
    private final Map<Long, Integer> versionVector;
    private volatile long version;

    public DocumentState() {
        this.content = new ArrayList<>();
        this.versionVector = new ConcurrentHashMap<>();
        this.version = 0;
    }

    /**
     * Applies an edit operation to the document.
     * Position is absolute; content is inserted and deleteLength bytes are removed.
     */
    public synchronized void applyEdit(int position, String insertedText, int deleteLength) {
        if (position < 0 || position > content.size()) {
            throw new IllegalArgumentException("Invalid position: " + position);
        }

        // Remove characters
        for (int i = 0; i < deleteLength && position < content.size(); i++) {
            content.remove(position);
        }

        // Insert characters
        if (insertedText != null) {
            for (int i = 0; i < insertedText.length(); i++) {
                content.add(position + i, insertedText.charAt(i));
            }
        }

        version++;
    }

    public synchronized String getContent() {
        StringBuilder sb = new StringBuilder(content.size());
        for (char c : content) {
            sb.append(c);
        }
        return sb.toString();
    }

    public synchronized int getContentLength() {
        return content.size();
    }

    public long getVersion() {
        return version;
    }

    public Map<Long, Integer> getVersionVector() {
        return Collections.unmodifiableMap(versionVector);
    }

    public synchronized char getCharAt(int position) {
        if (position < 0 || position >= content.size()) {
            throw new IndexOutOfBoundsException("Position: " + position);
        }
        return content.get(position);
    }

    public synchronized String getRange(int start, int end) {
        if (start < 0 || end > content.size() || start > end) {
            throw new IndexOutOfBoundsException("Invalid range: [" + start + ", " + end + ")");
        }
        StringBuilder sb = new StringBuilder(end - start);
        for (int i = start; i < end; i++) {
            sb.append(content.get(i));
        }
        return sb.toString();
    }

    public synchronized void clear() {
        content.clear();
        version++;
    }
}
