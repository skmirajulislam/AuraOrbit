package command;

import model.TextBuffer;

/**
 * Command to insert a line at a specific 1-based index.
 */
public class InsertLineCommand implements EditorCommand {
    private final int lineNumber;
    private final String content;
    private int insertedCount = 1;

    public InsertLineCommand(int lineNumber, String content) {
        this.lineNumber = lineNumber;
        this.content = content != null ? content : "";
    }

    @Override
    public void execute(TextBuffer buffer) {
        int before = buffer.getLineCount();
        buffer.insertLine(lineNumber, content);
        this.insertedCount = Math.max(1, buffer.getLineCount() - before);
    }

    @Override
    public void undo(TextBuffer buffer) {
        for (int i = 0; i < insertedCount; i++) {
            buffer.deleteLine(lineNumber);
        }
    }

    @Override
    public String getDescription() {
        return "Insert line at " + lineNumber + ": \"" + content + "\"";
    }
}
