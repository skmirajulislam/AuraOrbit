package command;

import model.TextBuffer;

/**
 * Command to append a new line to the end of the text buffer.
 */
public class AppendLineCommand implements EditorCommand {
    private final String content;
    private int startLine;
    private int insertedCount = 1;

    public AppendLineCommand(String content) {
        this.content = content != null ? content : "";
    }

    @Override
    public void execute(TextBuffer buffer) {
        this.startLine = buffer.getLineCount() + 1;
        int before = buffer.getLineCount();
        buffer.appendLine(content);
        this.insertedCount = Math.max(1, buffer.getLineCount() - before);
    }

    @Override
    public void undo(TextBuffer buffer) {
        for (int i = 0; i < insertedCount; i++) {
            buffer.deleteLine(startLine);
        }
    }

    @Override
    public String getDescription() {
        return "Append line: \"" + content + "\"";
    }
}
