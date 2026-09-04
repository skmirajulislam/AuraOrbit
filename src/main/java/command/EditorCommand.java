package command;

import model.TextBuffer;

/**
 * Interface representing an executable and reversible editor command.
 */
public interface EditorCommand {
    /**
     * Executes the editing action against the provided text buffer.
     */
    void execute(TextBuffer buffer);

    /**
     * Reverts the editing action on the provided text buffer.
     */
    void undo(TextBuffer buffer);

    /**
     * User-readable description of this command (e.g. "Insert line 4").
     */
    String getDescription();
}
