package controller;

import command.*;
import model.Document;
import model.TextBuffer;
import service.FileSecurityValidator;
import service.FileService;
import template.Template;
import template.TemplateFactory;
import view.ConsoleView;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * Controller mediating user actions from ConsoleView, manipulating TextBuffer via Commands,
 * and orchestrating FileService persistence.
 */
public class EditorController {

    private final ConsoleView view;
    private final FileService fileService;
    private final CommandManager commandManager;

    private Document currentDocument;
    private TextBuffer currentBuffer;
    private boolean running;

    public EditorController() {
        this.view = new ConsoleView();
        this.fileService = new FileService();
        this.commandManager = new CommandManager(100);

        // Initialize with default blank untitled document
        this.currentDocument = new Document("untitled.txt");
        this.currentBuffer = new TextBuffer();
        this.running = true;
    }

    public void start() {
        view.printBanner();

        try {
            while (running) {
                view.printStatusBar(currentDocument, currentBuffer, commandManager.getUndoCount(), commandManager.getRedoCount());
                view.printMenu();

                String choice = view.promptString("Enter command [0-12]");
                System.out.println();
                processCommand(choice);
            }
        } catch (java.util.NoSuchElementException | IllegalStateException e) {
            view.printInfo("\nStandard input closed (EOF). Exiting editor cleanly.");
            running = false;
        }

        view.printInfo("Editor closed. Goodbye!");
    }

    private void processCommand(String choice) {
        try {
            switch (choice) {
                case "1" -> handleView();
                case "2" -> handleInsert();
                case "3" -> handleAppend();
                case "4" -> handleReplace();
                case "5" -> handleDelete();
                case "6" -> handleUndo();
                case "7" -> handleRedo();
                case "8" -> handleOpen();
                case "9" -> handleNew();
                case "10" -> handleSave(false);
                case "11" -> handleSaveAs();
                case "12" -> handleFileInfo();
                case "0", "exit", "quit" -> handleExit();
                default -> view.printWarning("Unknown command: '" + choice + "'. Enter a number between 0 and 12.");
            }
        } catch (Exception e) {
            view.printError("Operation failed: " + e.getMessage());
        }
    }

    private void handleView() {
        if (currentBuffer.isEmpty()) {
            view.printInfo("Buffer is currently empty. Use [2] Insert, [3] Append (to write text), or [9] New to add content.");
            return;
        }
        int startLine = view.promptInt("Start line number to view", 1);
        int pageSize = view.promptInt("Lines per page", 20);
        view.printBufferView(currentBuffer, startLine, pageSize);
    }

    private void handleInsert() {
        int maxLine = currentBuffer.getLineCount() + 1;
        int lineNum = view.promptInt("Enter line number to insert at (1 to " + maxLine + ")", maxLine);
        if (lineNum < 1 || lineNum > maxLine) {
            view.printError("Invalid line number. Must be between 1 and " + maxLine);
            return;
        }
        String content = view.promptString("Enter line content / text");
        EditorCommand cmd = new InsertLineCommand(lineNum, content);
        commandManager.executeCommand(cmd, currentBuffer);
        view.printSuccess("Inserted into buffer at line " + lineNum);
    }

    private void handleAppend() {
        String content = view.promptString("Enter text / line to append (or paste text)");
        EditorCommand cmd = new AppendLineCommand(content);
        commandManager.executeCommand(cmd, currentBuffer);
        view.printSuccess("Appended to buffer (Total lines: " + currentBuffer.getLineCount() + ")");
    }

    private void handleReplace() {
        if (currentBuffer.isEmpty()) {
            view.printWarning("Buffer is empty. Nothing to replace.");
            return;
        }
        int lineNum = view.promptInt("Enter line number to replace (1 to " + currentBuffer.getLineCount() + ")", 1);
        if (lineNum < 1 || lineNum > currentBuffer.getLineCount()) {
            view.printError("Line number out of range.");
            return;
        }
        view.printInfo("Current line " + lineNum + ": \"" + currentBuffer.getLine(lineNum) + "\"");
        String newContent = view.promptString("Enter new content");
        EditorCommand cmd = new ReplaceLineCommand(lineNum, newContent);
        commandManager.executeCommand(cmd, currentBuffer);
        view.printSuccess("Line " + lineNum + " replaced.");
    }

    private void handleDelete() {
        if (currentBuffer.isEmpty()) {
            view.printWarning("Buffer is empty. Nothing to delete.");
            return;
        }
        int lineNum = view.promptInt("Enter line number to delete (1 to " + currentBuffer.getLineCount() + ")", currentBuffer.getLineCount());
        if (lineNum < 1 || lineNum > currentBuffer.getLineCount()) {
            view.printError("Line number out of range.");
            return;
        }
        String oldContent = currentBuffer.getLine(lineNum);
        EditorCommand cmd = new DeleteLineCommand(lineNum);
        commandManager.executeCommand(cmd, currentBuffer);
        view.printSuccess("Deleted line " + lineNum + ": \"" + oldContent + "\"");
    }

    private void handleUndo() {
        if (!commandManager.canUndo()) {
            view.printWarning("Nothing to undo.");
            return;
        }
        String desc = commandManager.undo(currentBuffer);
        view.printSuccess("Undone: " + desc);
    }

    private void handleRedo() {
        if (!commandManager.canRedo()) {
            view.printWarning("Nothing to redo.");
            return;
        }
        String desc = commandManager.redo(currentBuffer);
        view.printSuccess("Redone: " + desc);
    }

    public void openOrInitFile(String rawPath) {
        try {
            Path path = FileSecurityValidator.sanitizeAndResolvePath(rawPath);
            if (java.nio.file.Files.exists(path)) {
                TextBuffer loadedBuffer = fileService.loadFile(path);
                this.currentDocument = new Document(path);
                this.currentBuffer = loadedBuffer;
                this.commandManager.clearHistory();
                view.printSuccess("Connected and loaded " + currentBuffer.getLineCount() + " lines from: " + path.toAbsolutePath());
            } else {
                this.currentDocument = new Document(path);
                this.currentBuffer = new TextBuffer();
                this.commandManager.clearHistory();
                view.printInfo("New file targeted at: " + path.toAbsolutePath() + " (will be created when saved)");
            }
        } catch (Exception e) {
            view.printError("Failed to open file: " + e.getMessage());
        }
    }

    private void handleOpen() throws IOException {
        if (currentBuffer.isDirty() && !confirmDiscard()) {
            view.printInfo("Open cancelled. Current unsaved edits preserved.");
            return;
        }

        String rawPath = view.promptString("Enter file path to connect / open");
        if (rawPath.isBlank()) {
            view.printWarning("File path cannot be empty.");
            return;
        }

        Path path = FileSecurityValidator.sanitizeAndResolvePath(rawPath);
        if (java.nio.file.Files.exists(path)) {
            TextBuffer loadedBuffer = fileService.loadFile(path);
            this.currentDocument = new Document(path);
            this.currentBuffer = loadedBuffer;
            this.commandManager.clearHistory();
            view.printSuccess("Connected and loaded " + currentBuffer.getLineCount() + " lines from: " + path.toAbsolutePath());
        } else {
            String createChoice = view.promptString("File does not exist. Create new file at this location? (Y/n)");
            if (!createChoice.equalsIgnoreCase("n")) {
                this.currentDocument = new Document(path);
                this.currentBuffer = new TextBuffer();
                this.commandManager.clearHistory();
                view.printSuccess("Created new file buffer targeted to: " + path.toAbsolutePath());
            } else {
                view.printInfo("File creation cancelled.");
            }
        }
    }

    private void handleNew() {
        if (currentBuffer.isDirty() && !confirmDiscard()) {
            view.printInfo("New file creation cancelled.");
            return;
        }

        view.printInfo("Available Templates:");
        Map<String, String> templates = TemplateFactory.getAvailableTemplates();
        templates.forEach((ext, name) -> System.out.println("  • " + ext + " -> " + name));
        System.out.println("  • [blank / none] -> Empty document");

        String templateChoice = view.promptString("Choose template extension (or press Enter for blank)");
        String fileName = view.promptString("Enter filename (e.g. MyClass.java, README.md)");
        if (fileName.isBlank()) {
            fileName = "untitled.txt";
        }

        Template template = TemplateFactory.getTemplate(templateChoice);
        if (template != null) {
            List<String> scaffoldLines = template.generateScaffold(fileName);
            this.currentBuffer = new TextBuffer(scaffoldLines);
            this.currentBuffer.setDirty(true);
            view.printSuccess("Created new document from template: " + template.getTemplateType());
        } else {
            this.currentBuffer = new TextBuffer();
            view.printSuccess("Created empty document: " + fileName);
        }

        this.currentDocument = new Document(fileName);
        this.commandManager.clearHistory();
    }

    private void handleSave(boolean forceNewPath) throws IOException {
        if (currentDocument.getFilePath() == null || forceNewPath) {
            String rawPath = view.promptString("Enter destination file path to save");
            Path path = FileSecurityValidator.sanitizeAndResolvePath(rawPath);
            currentDocument.setFilePath(path);
        }

        boolean backup = view.promptString("Create backup (.bak) copy? (y/n)").equalsIgnoreCase("y");
        fileService.saveFileAtomically(currentDocument.getFilePath(), currentBuffer, backup);
        view.printSuccess("File saved atomically to: " + currentDocument.getFilePath().toAbsolutePath());
    }

    private void handleSaveAs() throws IOException {
        handleSave(true);
    }

    private void handleFileInfo() {
        String details = fileService.getFileDetails(currentDocument);
        System.out.println(details);
        System.out.println("Buffer in-memory line count: " + currentBuffer.getLineCount());
        System.out.println("Estimated characters: " + currentBuffer.getEstimatedCharacterCount());
        System.out.println("Unsaved dirty state: " + currentBuffer.isDirty());
    }

    private void handleExit() {
        if (currentBuffer.isDirty()) {
            if (!confirmDiscard()) {
                view.printInfo("Exit cancelled.");
                return;
            }
        }
        running = false;
    }

    private boolean confirmDiscard() {
        String answer = view.promptString("You have unsaved changes! Discard them? (y/N)");
        return answer.equalsIgnoreCase("y") || answer.equalsIgnoreCase("yes");
    }

    // Getters for testing
    public Document getCurrentDocument() { return currentDocument; }
    public TextBuffer getCurrentBuffer() { return currentBuffer; }
    public CommandManager getCommandManager() { return commandManager; }
    public FileService getFileService() { return fileService; }
}
