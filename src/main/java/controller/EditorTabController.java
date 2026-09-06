package controller;


import javafx.beans.value.ChangeListener;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.HBox;
import model.Document;
import org.fxmisc.richtext.CodeArea;
import org.kordamp.ikonli.codicons.Codicons;
import org.kordamp.ikonli.javafx.FontIcon;
import org.reactfx.Subscription;
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
    private Consumer<EditorTabController> onCursorMoved;
    private Consumer<EditorTabController> onTextChanged;
    private Consumer<EditorTabController> onCloseRequested;
    private Consumer<EditorTabController> onCloseOthersRequested;
    private Runnable onCloseAllRequested;

    private Subscription textChangeSub;
    private ChangeListener<Number> caretListener;

    private String lineEndings = "LF";
    private String indentation = "Spaces: 4";
    private String lastIconName = "";

    private boolean isPreview = false;
    private int errorCount = 0;
    private int warningCount = 0;

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

        // Middle click to close tab, double click to pin
        this.tabHeaderBox.setOnMouseClicked(e -> {
            if (e.getButton() == MouseButton.MIDDLE) {
                if (onCloseRequested != null) onCloseRequested.accept(this);
            } else if (e.getButton() == MouseButton.PRIMARY && e.getClickCount() == 2) {
                pin();
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

        // Middle click to close tab, double click to pin
        this.tabHeaderBox.setOnMouseClicked(e -> {
            if (e.getButton() == MouseButton.MIDDLE) {
                if (onCloseRequested != null) onCloseRequested.accept(this);
            } else if (e.getButton() == MouseButton.PRIMARY && e.getClickCount() == 2) {
                pin();
            }
        });

        String ft = getFileExtension();
        if (java.nio.file.Files.exists(sanitized)) {
            suppressEvents = true;
            String content = fileService.readString(sanitized);
            editorPane.loadContentInstantly(content, ft);
            updateCachedMetadata(content);
            suppressEvents = false;
            editorPane.updateBreadcrumbs(document.getFilePath(), ft, content);
        } else {
            editorPane.setFileType(ft);
        }

        initListeners();
        setupContextMenu();
        updateTabTitle();
    }

    private void updateCachedMetadata(String content) {
        if (content == null || content.isEmpty()) return;
        this.lineEndings = content.contains("\r\n") ? "CRLF" : "LF";
        if (content.contains("\t")) {
            this.indentation = "Tab Size: 4";
        } else {
            String name = document != null ? document.getFileName().toLowerCase() : "";
            if (name.endsWith(".json") || name.endsWith(".yaml") || name.endsWith(".yml") ||
                name.endsWith(".js") || name.endsWith(".ts") || name.endsWith(".html") ||
                name.endsWith(".css") || name.endsWith(".xml")) {
                this.indentation = "Spaces: 2";
            } else {
                this.indentation = "Spaces: 4";
            }
        }
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

        // High-performance event stream: zero full-text string joins on keystrokes
        textChangeSub = codeArea.plainTextChanges().subscribe(change -> {
            if (!suppressEvents) {
                if (isPreview) {
                    pin();
                }
                if (!isModified) {
                    isModified = true;
                    updateTabTitle();
                }
                if (onTextChanged != null) {
                    onTextChanged.accept(this);
                } else if (onStateChanged != null) {
                    onStateChanged.run();
                }
            }
        });

        // Caret position updates: instantaneous cursor tracking
        caretListener = (obs, oldVal, newVal) -> {
            editorPane.updateCaretLine(codeArea.getCurrentParagraph() + 1);
            if (onCursorMoved != null) {
                onCursorMoved.accept(this);
            } else if (onStateChanged != null) {
                onStateChanged.run();
            }
        };
        codeArea.caretPositionProperty().addListener(caretListener);
    }

    public void setContent(String content, boolean markModified) {
        suppressEvents = true;
        String ft = getFileExtension();
        editorPane.loadContentInstantly(content != null ? content : "", ft);
        updateCachedMetadata(content);
        suppressEvents = false;

        isModified = markModified;
        updateTabTitle();
        editorPane.updateBreadcrumbs(document.getFilePath(), ft, content);
        if (onTextChanged != null) {
            onTextChanged.accept(this);
        } else if (onStateChanged != null) {
            onStateChanged.run();
        }
    }

    public boolean save(boolean forceSaveAs, File targetFile) {
        if (document.getFilePath() == null || forceSaveAs) {
            if (targetFile == null) return false;
            document.setFilePath(targetFile.toPath());
        }

        try {
            String text = editorPane.getCodeArea().getText();
            fileService.saveStringAtomically(document.getFilePath(), text, false);
            isModified = false;
            updateCachedMetadata(text);
            updateTabTitle();
            String ft = getFileExtension();
            editorPane.updateBreadcrumbs(document.getFilePath(), ft, text);
            if (onTextChanged != null) {
                onTextChanged.accept(this);
            } else if (onStateChanged != null) {
                onStateChanged.run();
            }
            return true;
        } catch (IOException e) {
            System.err.println("Save failed: " + e.getMessage());
            return false;
        }
    }

    public void markUnsaved() {
        this.isModified = true;
        updateTabTitle();
    }

    public String getFileExtension() {
        if (document == null || document.getFileName() == null) return "";
        String name = document.getFileName();
        int dot = name.lastIndexOf('.');
        return (dot != -1 && dot < name.length() - 1) ? name.substring(dot + 1).toLowerCase() : "";
    }

    public void setDiagnostics(int errors, int warnings) {
        this.errorCount = errors;
        this.warningCount = warnings;
        updateTabTitle();
    }

    public int getErrorCount() {
        return errorCount;
    }

    public int getWarningCount() {
        return warningCount;
    }

    public boolean isPreview() {
        return isPreview;
    }

    public void setPreview(boolean preview) {
        this.isPreview = preview;
        updateTabTitle();
    }

    public void pin() {
        if (this.isPreview) {
            this.isPreview = false;
            updateTabTitle();
        }
    }

    public void loadNewFile(Path newPath) {
        Path sanitized = FileSecurityValidator.sanitizeAndResolvePath(newPath.toString());
        this.document = new Document(sanitized);
        String name = document.getFileName();
        FontIcon newIcon = IconFactory.getFileIcon(name, 13);
        this.fileIconNode.setIconCode(newIcon.getIconCode());
        this.fileIconNode.setIconColor(newIcon.getIconColor());
        this.lastIconName = name;

        if (java.nio.file.Files.exists(sanitized)) {
            suppressEvents = true;
            try {
                String content = fileService.readString(sanitized);
                String ft = getFileExtension();
                editorPane.loadContentInstantly(content, ft);
                updateCachedMetadata(content);
                editorPane.updateBreadcrumbs(document.getFilePath(), ft, content);
            } catch (Exception e) {
                System.err.println("Failed to read file: " + e.getMessage());
            } finally {
                suppressEvents = false;
            }
        }
        this.isModified = false;
        updateTabTitle();
    }

    public void updateTabTitle() {
        String name = (document != null) ? document.getFileName() : "untitled";
        String expectedText = (isModified ? "● " : "") + name;
        if (!expectedText.equals(titleLabel.getText())) {
            titleLabel.setText(expectedText);
        }

        // Only update file icon if the filename actually changed
        if (!name.equals(lastIconName)) {
            lastIconName = name;
            FontIcon newIcon = IconFactory.getFileIcon(name, 13);
            fileIconNode.setIconCode(newIcon.getIconCode());
            fileIconNode.setIconColor(newIcon.getIconColor());
        }

        StringBuilder style = new StringBuilder();
        if (isPreview) {
            style.append("-fx-font-style: italic; ");
        } else {
            style.append("-fx-font-style: normal; ");
        }

        if (errorCount > 0) {
            style.append("-fx-text-fill: #f14c4c; ");
            titleLabel.setTooltip(new Tooltip(name + " (" + errorCount + " error" + (errorCount > 1 ? "s" : "") + ")"));
        } else if (warningCount > 0) {
            style.append("-fx-text-fill: #cca700; ");
            titleLabel.setTooltip(new Tooltip(name + " (" + warningCount + " warning" + (warningCount > 1 ? "s" : "") + ")"));
        } else {
            style.append("-fx-text-fill: -text-primary; ");
            titleLabel.setTooltip(new Tooltip(document != null && document.getFilePath() != null ? document.getFilePath().toString() : name));
        }

        titleLabel.setStyle(style.toString());
    }

    public Tab getTab() { return tab; }
    public CodeEditorPane getEditorPane() { return editorPane; }
    public Document getDocument() { return document; }
    public boolean isModified() { return isModified; }
    public void setOnStateChanged(Runnable onStateChanged) { this.onStateChanged = onStateChanged; }
    public void setOnCursorMoved(Consumer<EditorTabController> onCursorMoved) { this.onCursorMoved = onCursorMoved; }
    public void setOnTextChanged(Consumer<EditorTabController> onTextChanged) { this.onTextChanged = onTextChanged; }
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
        return lineEndings;
    }

    public void toggleLineEndings() {
        String text = editorPane.getCodeArea().getText();
        if (text.contains("\r\n")) {
            setContent(text.replace("\r\n", "\n"), true);
            this.lineEndings = "LF";
        } else {
            setContent(text.replace("\n", "\r\n"), true);
            this.lineEndings = "CRLF";
        }
    }

    public String getIndentation() {
        return indentation;
    }

    public String getEncoding() {
        return "UTF-8";
    }

    public void navigateToLineAndHighlight(int line1Indexed) {
        editorPane.navigateToLineAndHighlight(line1Indexed);
    }

    public void dispose() {
        if (textChangeSub != null) {
            textChangeSub.unsubscribe();
            textChangeSub = null;
        }
        if (caretListener != null) {
            editorPane.getCodeArea().caretPositionProperty().removeListener(caretListener);
            caretListener = null;
        }
        onStateChanged = null;
        onCursorMoved = null;
        onTextChanged = null;
        onCloseRequested = null;
        onCloseOthersRequested = null;
        onCloseAllRequested = null;
        editorPane.dispose();
    }
}
