package controller;


import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.HBox;
import model.Document;
import org.fxmisc.richtext.CodeArea;
import org.kordamp.ikonli.codicons.Codicons;
import org.kordamp.ikonli.javafx.FontIcon;
import service.FileSecurityValidator;
import service.FileService;
import view.fx.CodeEditorPane;
import view.fx.IconFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.function.Consumer;

/**
 * Controller for an individual Editor Tab with VS Code-style custom tab header:
 * [FileIcon | FileName ● | CloseBtn(✕)]
 */
public class EditorTabController {

    private final Tab tab;
    private final CodeEditorPane editorPane;
    private final FileService fileService;


    private final HBox tabHeaderBox;
    private final Label titleLabel;
    private final FontIcon fileIconNode;
    private final Button closeButton;

    private Document document;
    private boolean isModified;
    private boolean suppressEvents;
    private Runnable onStateChanged;
    private Consumer<EditorTabController> onCloseRequested;
    private Consumer<EditorTabController> onCloseOthersRequested;
    private Runnable onCloseAllRequested;

    public EditorTabController(String initialName, FileService fileService) {
        this.fileService = fileService;
        this.document = new Document(initialName);
        this.editorPane = new CodeEditorPane();
        int dot = initialName.lastIndexOf('.');
        if (dot != -1 && dot < initialName.length() - 1) {
            this.editorPane.setFileType(initialName.substring(dot + 1).toLowerCase());
        }
        this.tab = new Tab("", editorPane);
        this.tab.setClosable(false); // Using custom built-in close button
        this.isModified = false;
        this.suppressEvents = false;

        // Custom VS Code tab header components
        this.tabHeaderBox = new HBox(6);
        this.tabHeaderBox.setAlignment(Pos.CENTER_LEFT);
        this.tabHeaderBox.getStyleClass().add("custom-tab-header");

        this.fileIconNode = IconFactory.getFileIcon(initialName, 13);
        this.titleLabel = new Label(initialName);
        this.titleLabel.getStyleClass().add("custom-tab-label");

        this.closeButton = new Button();
        this.closeButton.setGraphic(IconFactory.getIcon(Codicons.CLOSE, 11));
        this.closeButton.getStyleClass().add("custom-tab-close-btn");
        this.closeButton.setTooltip(new Tooltip("Close (Cmd/Ctrl+W)"));
        this.closeButton.setOnAction(e -> {
            if (onCloseRequested != null) onCloseRequested.accept(this);
        });

        this.tabHeaderBox.getChildren().addAll(fileIconNode, titleLabel, closeButton);
        this.tab.setGraphic(tabHeaderBox);

        // Middle click to close tab
        this.tabHeaderBox.setOnMouseClicked(e -> {
            if (e.getButton() == MouseButton.MIDDLE) {
                if (onCloseRequested != null) onCloseRequested.accept(this);
            }
        });

        initListeners();
        setupContextMenu();
        updateTabTitle();
    }

    public EditorTabController(Path filePath, FileService fileService) throws IOException {
        this.fileService = fileService;
        this.editorPane = new CodeEditorPane();
        this.tab = new Tab("", editorPane);
        this.tab.setClosable(false);
        this.isModified = false;
        this.suppressEvents = false;

        Path sanitized = FileSecurityValidator.sanitizeAndResolvePath(filePath.toString());
        this.document = new Document(sanitized);

        // Custom VS Code tab header components
        this.tabHeaderBox = new HBox(6);
        this.tabHeaderBox.setAlignment(Pos.CENTER_LEFT);
        this.tabHeaderBox.getStyleClass().add("custom-tab-header");

        this.fileIconNode = IconFactory.getFileIcon(document.getFileName(), 13);
        this.titleLabel = new Label(document.getFileName());
        this.titleLabel.getStyleClass().add("custom-tab-label");

        this.closeButton = new Button();
        this.closeButton.setGraphic(IconFactory.getIcon(Codicons.CLOSE, 11));
        this.closeButton.getStyleClass().add("custom-tab-close-btn");
        this.closeButton.setTooltip(new Tooltip("Close (Cmd/Ctrl+W)"));
        this.closeButton.setOnAction(e -> {
            if (onCloseRequested != null) onCloseRequested.accept(this);
        });

        this.tabHeaderBox.getChildren().addAll(fileIconNode, titleLabel, closeButton);
        this.tab.setGraphic(tabHeaderBox);

        // Middle click to close tab
        this.tabHeaderBox.setOnMouseClicked(e -> {
            if (e.getButton() == MouseButton.MIDDLE) {
                if (onCloseRequested != null) onCloseRequested.accept(this);
            }
        });

        String name = document.getFileName();
        int dot = name.lastIndexOf('.');
        if (dot != -1 && dot < name.length() - 1) {
            editorPane.setFileType(name.substring(dot + 1).toLowerCase());
        }

        if (java.nio.file.Files.exists(sanitized)) {
            suppressEvents = true;
            String content = fileService.readString(sanitized);
            editorPane.getCodeArea().replaceText(content);
            editorPane.getCodeArea().moveTo(0);
            suppressEvents = false;
        }

        initListeners();
        setupContextMenu();
        updateTabTitle();
    }

    private void setupContextMenu() {
        ContextMenu contextMenu = new ContextMenu();

        MenuItem closeItem = new MenuItem("Close");
        closeItem.setGraphic(IconFactory.getIcon(Codicons.CLOSE, 12));
        closeItem.setOnAction(e -> {
            if (onCloseRequested != null) onCloseRequested.accept(this);
        });

        MenuItem closeOthers = new MenuItem("Close Others");
        closeOthers.setOnAction(e -> {
            if (onCloseOthersRequested != null) onCloseOthersRequested.accept(this);
        });

        MenuItem closeAll = new MenuItem("Close All");
        closeAll.setGraphic(IconFactory.getIcon(Codicons.CLEAR_ALL, 12));
        closeAll.setOnAction(e -> {
            if (onCloseAllRequested != null) onCloseAllRequested.run();
        });

        contextMenu.getItems().addAll(closeItem, closeOthers, closeAll);
        tab.setContextMenu(contextMenu);
    }

    private void initListeners() {
        CodeArea codeArea = editorPane.getCodeArea();
        codeArea.textProperty().addListener((obs, oldVal, newVal) -> {
            if (!suppressEvents) {
                isModified = true;
                updateTabTitle();
                if (onStateChanged != null) onStateChanged.run();
            }
        });

        codeArea.caretPositionProperty().addListener((obs, oldVal, newVal) -> {
            if (onStateChanged != null) onStateChanged.run();
        });
    }

    public void setContent(String content, boolean markModified) {
        suppressEvents = true;
        editorPane.getCodeArea().replaceText(content != null ? content : "");
        editorPane.getCodeArea().moveTo(0);
        suppressEvents = false;

        isModified = markModified;
        updateTabTitle();
        if (onStateChanged != null) onStateChanged.run();
    }

    public boolean save(boolean forceSaveAs, File targetFile) {
        if (document.getFilePath() == null || forceSaveAs) {
            if (targetFile == null) return false;
            document.setFilePath(targetFile.toPath());
        }

        try {
            String text = editorPane.getCodeArea().getText();
            fileService.saveStringAtomically(document.getFilePath(), text, true);
            isModified = false;
            updateTabTitle();
            if (onStateChanged != null) onStateChanged.run();
            return true;
        } catch (IOException e) {
            System.err.println("Save failed: " + e.getMessage());
            return false;
        }
    }

    public void updateTabTitle() {
        String name = (document != null) ? document.getFileName() : "untitled";
        titleLabel.setText((isModified ? "● " : "") + name);

        // Update file icon based on latest extension
        FontIcon newIcon = IconFactory.getFileIcon(name, 13);
        fileIconNode.setIconCode(newIcon.getIconCode());
        fileIconNode.setIconColor(newIcon.getIconColor());
    }

    public Tab getTab() { return tab; }
    public CodeEditorPane getEditorPane() { return editorPane; }
    public Document getDocument() { return document; }
    public boolean isModified() { return isModified; }
    public void setOnStateChanged(Runnable onStateChanged) { this.onStateChanged = onStateChanged; }
    public void setOnCloseRequested(Consumer<EditorTabController> onCloseRequested) { this.onCloseRequested = onCloseRequested; }
    public void setOnCloseOthersRequested(Consumer<EditorTabController> onCloseOthersRequested) { this.onCloseOthersRequested = onCloseOthersRequested; }
    public void setOnCloseAllRequested(Runnable onCloseAllRequested) { this.onCloseAllRequested = onCloseAllRequested; }

    public int getLineCount() {
        return editorPane.getCodeArea().getParagraphs().size();
    }

    public long getCharCount() {
        return editorPane.getCodeArea().getLength();
    }

    public int getCurrentLine() {
        return editorPane.getCodeArea().getCurrentParagraph() + 1;
    }

    public int getCurrentColumn() {
        return editorPane.getCodeArea().getCaretColumn() + 1;
    }

    public String getLineEndings() {
        String text = editorPane.getCodeArea().getText();
        return text.contains("\r\n") ? "CRLF" : "LF";
    }

    public void toggleLineEndings() {
        String text = editorPane.getCodeArea().getText();
        if (text.contains("\r\n")) {
            setContent(text.replace("\r\n", "\n"), true);
        } else {
            setContent(text.replace("\n", "\r\n"), true);
        }
    }

    public String getIndentation() {
        String text = editorPane.getCodeArea().getText();
        if (text.contains("\t")) {
            return "Tab Size: 4";
        }
        String name = document != null ? document.getFileName().toLowerCase() : "";
        if (name.endsWith(".json") || name.endsWith(".yaml") || name.endsWith(".yml") ||
            name.endsWith(".js") || name.endsWith(".ts") || name.endsWith(".html") ||
            name.endsWith(".css") || name.endsWith(".xml")) {
            return "Spaces: 2";
        }
        return "Spaces: 4";
    }

    public String getEncoding() {
        return "UTF-8";
    }

    public void navigateToLineAndHighlight(int line1Indexed) {
        editorPane.navigateToLineAndHighlight(line1Indexed);
    }

    public void dispose() {
        editorPane.dispose();
    }
}
