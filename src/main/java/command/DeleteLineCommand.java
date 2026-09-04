package command;

import model.TextBuffer;

/**
 * Command to delete a line at a specific 1-based index, preserving deleted content for undo.
 */
public class DeleteLineCommand implements EditorCommand {
    private final int lineNumber;
    private String deletedContent;

    public DeleteLineCommand(int lineNumber) {
        this.lineNumber = lineNumber;
    }

    @Override
    public void execute(TextBuffer buffer) {
        this.deletedContent = buffer.deleteLine(lineNumber);
    }

    @Override
    public void undo(TextBuffer buffer) {
        buffer.insertLine(lineNumber, deletedContent);
    }

    @Override
    public String getDescription() {
        return "Delete line " + lineNumber + (deletedContent != null ? " (\"" + deletedContent + "\")" : "");
    }
}
