package model;

import java.io.BufferedWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Consumer;

/**
 * Thread-safe, high-concurrency line buffer.
 * Uses ReentrantReadWriteLock for concurrent reads and zero-CPU O(1) running statistics.
 */
public class TextBuffer {
    private final List<String> lines;
    private final ReentrantReadWriteLock rwLock;
    private final ReentrantReadWriteLock.ReadLock readLock;
    private final ReentrantReadWriteLock.WriteLock writeLock;

    private boolean dirty;
    private long totalCharacters;

    public TextBuffer() {
        this(16);
    }

    public TextBuffer(int initialCapacity) {
        this.lines = new ArrayList<>(Math.max(16, initialCapacity));
        this.rwLock = new ReentrantReadWriteLock();
        this.readLock = rwLock.readLock();
        this.writeLock = rwLock.writeLock();
        this.dirty = false;
        this.totalCharacters = 0;
    }

    public TextBuffer(List<String> initialLines) {
        int cap = (initialLines != null) ? initialLines.size() : 16;
        this.lines = new ArrayList<>(Math.max(16, cap));
        this.rwLock = new ReentrantReadWriteLock();
        this.readLock = rwLock.readLock();
        this.writeLock = rwLock.writeLock();
        this.dirty = false;
        this.totalCharacters = 0;

        if (initialLines != null) {
            for (String line : initialLines) {
                String val = (line != null) ? line : "";
                this.lines.add(val);
                this.totalCharacters += val.length() + 1; // +1 for newline
            }
        }
    }

    public int getLineCount() {
        readLock.lock();
        try {
            return lines.size();
        } finally {
            readLock.unlock();
        }
    }

    public boolean isEmpty() {
        readLock.lock();
        try {
            return lines.isEmpty();
        } finally {
            readLock.unlock();
        }
    }

    public String getLine(int lineNumber) {
        readLock.lock();
        try {
            validateLineNumber(lineNumber);
            return lines.get(lineNumber - 1);
        } finally {
            readLock.unlock();
        }
    }

    public void insertLine(int lineNumber, String content) {
        String safeContent = (content != null) ? content : "";
        String[] split = safeContent.split("\\R", -1);
        writeLock.lock();
        try {
            if (lineNumber < 1 || lineNumber > lines.size() + 1) {
                throw new IndexOutOfBoundsException(
                        "Invalid line number for insertion: " + lineNumber + " (Total lines: " + lines.size() + ")"
                );
            }
            int targetIndex = lineNumber - 1;
            for (int i = 0; i < split.length; i++) {
                String sub = split[i];
                lines.add(targetIndex + i, sub);
                totalCharacters += sub.length() + 1;
            }
            this.dirty = true;
        } finally {
            writeLock.unlock();
        }
    }

    public void appendLine(String content) {
        String safeContent = (content != null) ? content : "";
        String[] split = safeContent.split("\\R", -1);
        writeLock.lock();
        try {
            for (String sub : split) {
                lines.add(sub);
                totalCharacters += sub.length() + 1;
            }
            this.dirty = true;
        } finally {
            writeLock.unlock();
        }
    }

    public String deleteLine(int lineNumber) {
        writeLock.lock();
        try {
            validateLineNumber(lineNumber);
            String removed = lines.remove(lineNumber - 1);
            int remLen = (removed != null) ? removed.length() : 0;
            totalCharacters -= (remLen + 1);
            if (totalCharacters < 0) totalCharacters = 0;
            this.dirty = true;
            return removed;
        } finally {
            writeLock.unlock();
        }
    }

    public String replaceLine(int lineNumber, String newContent) {
        String safeContent = (newContent != null) ? newContent : "";
        // If replacement contains newlines, replace first line and insert subsequent
        String[] split = safeContent.split("\\R", -1);
        writeLock.lock();
        try {
            validateLineNumber(lineNumber);
            String previous = lines.set(lineNumber - 1, split[0]);
            int prevLen = (previous != null) ? previous.length() : 0;
            totalCharacters += (split[0].length() - prevLen);

            for (int i = 1; i < split.length; i++) {
                lines.add(lineNumber - 1 + i, split[i]);
                totalCharacters += split[i].length() + 1;
            }

            if (totalCharacters < 0) totalCharacters = 0;
            this.dirty = true;
            return previous;
        } finally {
            writeLock.unlock();
        }
    }

    public void clear() {
        writeLock.lock();
        try {
            if (!lines.isEmpty()) {
                lines.clear();
                totalCharacters = 0;
                this.dirty = true;
            }
        } finally {
            writeLock.unlock();
        }
    }

    /**
     * Streams buffer content directly to a BufferedWriter without copying lists into memory.
     */
    public void writeTo(BufferedWriter writer) throws IOException {
        readLock.lock();
        try {
            int size = lines.size();
            for (int i = 0; i < size; i++) {
                writer.write(lines.get(i));
                if (i < size - 1) {
                    writer.newLine();
                }
            }
        } finally {
            readLock.unlock();
        }
    }

    /**
     * Executes a read action for each line without generating intermediate collections.
     */
    public void forEachLine(Consumer<String> action) {
        readLock.lock();
        try {
            for (String line : lines) {
                action.accept(line);
            }
        } finally {
            readLock.unlock();
        }
    }

    public List<String> getLines() {
        readLock.lock();
        try {
            return Collections.unmodifiableList(new ArrayList<>(lines));
        } finally {
            readLock.unlock();
        }
    }

    public List<String> getPage(int startLine, int pageSize) {
        readLock.lock();
        try {
            if (lines.isEmpty() || startLine > lines.size()) {
                return Collections.emptyList();
            }
            int safePageSize = Math.max(1, Math.min(1000, pageSize));
            int start = Math.max(1, startLine) - 1;
            int end = (int) Math.min((long) lines.size(), (long) start + safePageSize);
            return Collections.unmodifiableList(new ArrayList<>(lines.subList(start, end)));
        } finally {
            readLock.unlock();
        }
    }

    public boolean isDirty() {
        readLock.lock();
        try {
            return dirty;
        } finally {
            readLock.unlock();
        }
    }

    public void setDirty(boolean dirty) {
        writeLock.lock();
        try {
            this.dirty = dirty;
        } finally {
            writeLock.unlock();
        }
    }

    public void markClean() {
        writeLock.lock();
        try {
            this.dirty = false;
        } finally {
            writeLock.unlock();
        }
    }

    private void validateLineNumber(int lineNumber) {
        if (lineNumber < 1 || lineNumber > lines.size()) {
            throw new IndexOutOfBoundsException(
                    "Line " + lineNumber + " is out of bounds (1-" + lines.size() + ")"
            );
        }
    }

    /**
     * Returns estimated character count in O(1) time without CPU-draining loops.
     */
    public long getEstimatedCharacterCount() {
        readLock.lock();
        try {
            return totalCharacters;
        } finally {
            readLock.unlock();
        }
    }
}
