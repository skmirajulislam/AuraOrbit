package command;

import model.TextBuffer;

/**
 * Command to replace the text of a line, preserving the old content for undo.
 */
public class ReplaceLineCommand implements EditorCommand {
    private final int lineNumber;
    private final String newContent;
    private String oldContent;
    private int extraLinesAdded = 0;

    public ReplaceLineCommand(int lineNumber, String newContent) {
        this.lineNumber = lineNumber;
        this.newContent = newContent != null ? newContent : "";
    }

    @Override
    public void execute(TextBuffer buffer) {
        int before = buffer.getLineCount();
        this.oldContent = buffer.replaceLine(lineNumber, newContent);
        this.extraLinesAdded = Math.max(0, buffer.getLineCount() - before);
    }

    @Override
    public void undo(TextBuffer buffer) {
        for (int i = 0; i < extraLinesAdded; i++) {
            buffer.deleteLine(lineNumber + 1);
        }
        buffer.replaceLine(lineNumber, oldContent);
    }

    @Override
    public String getDescription() {
        return "Replace line " + lineNumber + ": \"" + oldContent + "\" -> \"" + newContent + "\"";
    }
}
