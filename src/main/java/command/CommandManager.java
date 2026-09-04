package command;

import model.TextBuffer;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Manages the execution, undo, and redo lifecycle of EditorCommands.
 * Maintains bounded history stacks to ensure memory efficiency.
 */
public class CommandManager {
    private static final int DEFAULT_MAX_HISTORY = 100;

    private final int maxHistory;
    private final Deque<EditorCommand> undoStack;
    private final Deque<EditorCommand> redoStack;

    public CommandManager() {
        this(DEFAULT_MAX_HISTORY);
    }

    public CommandManager(int maxHistory) {
        this.maxHistory = maxHistory;
        this.undoStack = new ArrayDeque<>();
        this.redoStack = new ArrayDeque<>();
    }

    /**
     * Executes a command on the buffer, stores it in the undo stack,
     * and clears the redo stack.
     */
    public synchronized void executeCommand(EditorCommand command, TextBuffer buffer) {
        command.execute(buffer);
        if (undoStack.size() >= maxHistory) {
            undoStack.removeLast(); // Evict oldest command to prevent unbounded memory growth
        }
        undoStack.push(command);
        redoStack.clear();
    }

    /**
     * Undoes the most recent command.
     */
    public synchronized String undo(TextBuffer buffer) {
        if (undoStack.isEmpty()) {
            return null;
        }
        EditorCommand command = undoStack.pop();
        command.undo(buffer);
        redoStack.push(command);
        return command.getDescription();
    }

    /**
     * Redoes the most recently undone command.
     */
    public synchronized String redo(TextBuffer buffer) {
        if (redoStack.isEmpty()) {
            return null;
        }
        EditorCommand command = redoStack.pop();
        command.execute(buffer);
        undoStack.push(command);
        return command.getDescription();
    }

    public synchronized boolean canUndo() {
        return !undoStack.isEmpty();
    }

    public synchronized boolean canRedo() {
        return !redoStack.isEmpty();
    }

    public synchronized void clearHistory() {
        undoStack.clear();
        redoStack.clear();
    }

    public synchronized int getUndoCount() {
        return undoStack.size();
    }

    public synchronized int getRedoCount() {
        return redoStack.size();
    }

    public synchronized List<String> getRecentHistory(int limit) {
        List<String> list = new ArrayList<>();
        int count = 0;
        for (EditorCommand cmd : undoStack) {
            if (count++ >= limit) break;
            list.add(cmd.getDescription());
        }
        return list;
    }
}
