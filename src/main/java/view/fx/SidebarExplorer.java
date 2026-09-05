package view.fx;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.*;
import org.kordamp.ikonli.codicons.Codicons;
import org.kordamp.ikonli.javafx.FontIcon;
import template.Template;
import template.TemplateFactory;

import java.awt.Desktop;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.BiConsumer;

/**
 * AuraOrbit VS Code-Identical Explorer Sidebar.
 * Features:
 * - Dual-header layout (Top "EXPLORER" + "..." action; Workspace row "⌄ Coding" + Action buttons)
 * - 4 Action toolbar buttons: New File, New Folder, Refresh, Collapse All
 * - In-place inline file and folder creation with blue active border and auto-focus
 * - Full right-click context menu (New File, New Folder, Reveal, Copy Path, Delete)
 * - High-performance lazy file tree loading
 */
public class SidebarExplorer extends VBox {

    private final Label topTitleLabel;
    private final StackPane contentStack;
    private final VBox explorerPane;
    private final VBox templatesPane;

    private Label workspaceTitleLabel;
    private FontIcon workspaceChevron;
    private TreeView<FileItem> fileTreeView;
    private VBox emptyWorkspaceBox;
    private Path currentWorkspacePath;

    private Consumer<Path> onFileSelected;
    private Consumer<TemplateChoice> onTemplateSelected;
    private Runnable onNewFileRequested;
    private Runnable onOpenFolderRequested;
    private Consumer<Path> onWorkspaceChanged;
    private BiConsumer<Path, Path> onFileRenamed;
    private TreeItem<FileItem> renamingTreeItem;
    private ModalOverlayPane modalOverlayPane;

    public void setModalOverlayPane(ModalOverlayPane modalOverlayPane) {
        this.modalOverlayPane = modalOverlayPane;
    }

    /**
     * Tree item data model representing files, folders, or in-progress inline creation.
     */
    public static class FileItem {
        public final File file;
        public final boolean isCreationItem;
        public final boolean isFolderCreation;
        public final File targetParentDir;
        private FontIcon cachedIcon;

        // Normal file or directory item
        public FileItem(File file) {
            this(file, false, false, null);
        }

        // Inline creation placeholder item
        public FileItem(File file, boolean isCreationItem, boolean isFolderCreation, File targetParentDir) {
            this.file = file;
            this.isCreationItem = isCreationItem;
            this.isFolderCreation = isFolderCreation;
            this.targetParentDir = targetParentDir;
        }

        public FontIcon getIcon(boolean isExpanded) {
            if (isCreationItem) {
                return isFolderCreation ? IconFactory.getFolderIcon(false, 14) : IconFactory.getFileIcon("", 14);
            }
            if (file != null && file.isDirectory()) {
                return IconFactory.getFolderIcon(isExpanded, 14);
            }
            if (cachedIcon == null && file != null) {
                cachedIcon = IconFactory.getFileIcon(file.getName(), 14);
            }
            return cachedIcon;
        }

        @Override
        public String toString() {
            if (isCreationItem) return "";
            if (file == null) return "";
            return file.getName().isEmpty() ? file.getAbsolutePath() : file.getName();
        }
    }

    public static class TemplateChoice {
        public final Template template;
        public final String defaultName;
        public TemplateChoice(Template template, String defaultName) {
            this.template = template;
            this.defaultName = defaultName;
        }
    }

    public SidebarExplorer() {
        getStyleClass().add("sidebar");
        setMinWidth(180);
        setPrefWidth(260);
        setMaxWidth(500);

        // 1. Top Primary Explorer Header: [EXPLORER          ...]
        HBox topHeader = new HBox(8);
        topHeader.setAlignment(Pos.CENTER_LEFT);
        topHeader.setPadding(new Insets(10, 14, 8, 14));
        topHeader.getStyleClass().add("sidebar-header");

        topTitleLabel = new Label("EXPLORER");
        topTitleLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 11px; -fx-text-fill: -text-secondary;");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button moreBtn = createActionButton(Codicons.ELLIPSIS, "More Actions...");
        moreBtn.setOnAction(e -> showMoreMenu(moreBtn));

        topHeader.getChildren().addAll(topTitleLabel, spacer, moreBtn);

        // 2. Explorer Pane (contains Workspace Section Header + TreeView)
        explorerPane = createExplorerPane();

        // 3. Templates Pane (for scaffolding)
        templatesPane = createTemplatesPane();
        templatesPane.setVisible(false);
        templatesPane.setManaged(false);

        contentStack = new StackPane(explorerPane, templatesPane);
        VBox.setVgrow(contentStack, Priority.ALWAYS);

        getChildren().addAll(topHeader, contentStack);

        // Default workspace is current working directory
        setWorkspacePath(Paths.get("."));
    }

    private VBox createExplorerPane() {
        VBox pane = new VBox();
        VBox.setVgrow(pane, Priority.ALWAYS);

        // Workspace Section Header: [⌄ WorkspaceName       [📄+] [📁+] [🔄] [🗂️]]
        HBox workspaceHeader = new HBox(6);
        workspaceHeader.setAlignment(Pos.CENTER_LEFT);
        workspaceHeader.getStyleClass().add("explorer-workspace-header");

        workspaceChevron = IconFactory.getIcon(Codicons.CHEVRON_DOWN, 12);
        workspaceTitleLabel = new Label("CODING");
        workspaceTitleLabel.getStyleClass().add("explorer-workspace-title");

        HBox titleBox = new HBox(4, workspaceChevron, workspaceTitleLabel);
        titleBox.setAlignment(Pos.CENTER_LEFT);
        titleBox.setStyle("-fx-cursor: hand;");
        titleBox.setOnMouseClicked(e -> toggleTreeVisibility());

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button newFileBtn = createActionButton(Codicons.NEW_FILE, "New File...");
        newFileBtn.setOnAction(e -> {
            if (currentWorkspacePath == null && onNewFileRequested != null) {
                onNewFileRequested.run();
            } else {
                startInlineCreation(false);
            }
        });

        Button newFolderBtn = createActionButton(Codicons.NEW_FOLDER, "New Folder...");
        newFolderBtn.setOnAction(e -> startInlineCreation(true));

        Button refreshBtn = createActionButton(Codicons.REFRESH, "Refresh Explorer");
        refreshBtn.setOnAction(e -> refreshWorkspace());

        Button collapseAllBtn = createActionButton(Codicons.COLLAPSE_ALL, "Collapse Folders in Explorer");
        collapseAllBtn.setOnAction(e -> collapseAll());

        Button closeFolderBtn = createActionButton(Codicons.CLOSE, "Close Folder / Remove from Workspace");
        closeFolderBtn.setOnAction(e -> closeWorkspaceFolder());

        HBox actionToolbar = new HBox(2, newFileBtn, newFolderBtn, refreshBtn, collapseAllBtn, closeFolderBtn);
        actionToolbar.setAlignment(Pos.CENTER_RIGHT);

        workspaceHeader.getChildren().addAll(titleBox, spacer, actionToolbar);

        // TreeView
        fileTreeView = new TreeView<>();
        fileTreeView.getStyleClass().add("tree-view");
        fileTreeView.setShowRoot(false); // Contents of workspace folder render directly below header
        VBox.setVgrow(fileTreeView, Priority.ALWAYS);

        setupTreeCellFactory();
        setupTreeInteractions();

        // Empty Workspace State
        emptyWorkspaceBox = new VBox(12);
        emptyWorkspaceBox.setAlignment(Pos.CENTER);
        emptyWorkspaceBox.setPadding(new Insets(40, 16, 20, 16));
        emptyWorkspaceBox.setVisible(false);
        emptyWorkspaceBox.setManaged(false);
        VBox.setVgrow(emptyWorkspaceBox, Priority.ALWAYS);

        Label noFolderLbl = new Label("You have not opened a folder.");
        noFolderLbl.setStyle("-fx-text-fill: -text-secondary; -fx-font-size: 12px;");
        noFolderLbl.setWrapText(true);
        noFolderLbl.setTextAlignment(javafx.scene.text.TextAlignment.CENTER);

        Button openFolderBtn = new Button("Open Folder");
        openFolderBtn.setGraphic(IconFactory.getIcon(Codicons.FOLDER_OPENED, 13, "#ffffff"));
        openFolderBtn.getStyleClass().add("btn-modern");
        openFolderBtn.setStyle("-fx-background-color: -accent-color; -fx-text-fill: #ffffff; -fx-font-size: 12px; -fx-font-weight: bold; -fx-padding: 6 16 6 16; -fx-cursor: hand;");
        openFolderBtn.setOnAction(e -> promptOpenFolder());

        emptyWorkspaceBox.getChildren().addAll(noFolderLbl, openFolderBtn);

        pane.getChildren().addAll(workspaceHeader, fileTreeView, emptyWorkspaceBox);
        return pane;
    }

    private Button createActionButton(Codicons codicon, String tooltipText) {
        Button btn = new Button();
        btn.setGraphic(IconFactory.getIcon(codicon, 13));
        btn.getStyleClass().add("explorer-action-btn");
        if (tooltipText != null) {
            btn.setTooltip(new Tooltip(tooltipText));
        }
        return btn;
    }

    private void toggleTreeVisibility() {
        boolean visible = !fileTreeView.isVisible();
        fileTreeView.setVisible(visible);
        fileTreeView.setManaged(visible);
        workspaceChevron.setIconCode(visible ? Codicons.CHEVRON_DOWN : Codicons.CHEVRON_RIGHT);
    }

    private void setupTreeCellFactory() {
        fileTreeView.setCellFactory(tv -> new TreeCell<>() {
            private TextField inlineInput = null;
            private HBox editorBox = null;
            private boolean isCommitted = false;

            @Override
            protected void updateItem(FileItem item, boolean empty) {
                super.updateItem(item, empty);

                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                    setContextMenu(null);
                    return;
                }

                if (item.isCreationItem) {
                    setText(null);
                    setContextMenu(null);
                    isCommitted = false;

                    if (editorBox == null) {
                        editorBox = new HBox(6);
                        editorBox.setAlignment(Pos.CENTER_LEFT);

                        inlineInput = new TextField();
                        inlineInput.getStyleClass().add("inline-tree-input");
                        HBox.setHgrow(inlineInput, Priority.ALWAYS);
                        inlineInput.setMaxWidth(Double.MAX_VALUE);
                    }

                    FontIcon icon = item.getIcon(false);
                    editorBox.getChildren().setAll(icon, inlineInput);
                    setGraphic(editorBox);

                    final TreeItem<FileItem> creationTreeItem = getTreeItem();
                    final File targetDir = item.targetParentDir;
                    final boolean isFolder = item.isFolderCreation;

                    inlineInput.setText("");

                    inlineInput.setOnKeyPressed(ke -> {
                        if (ke.getCode() == KeyCode.ENTER) {
                            String name = inlineInput.getText().trim();
                            isCommitted = true;
                            commitCreation(creationTreeItem, targetDir, name, isFolder);
                            ke.consume();
                        } else if (ke.getCode() == KeyCode.ESCAPE) {
                            isCommitted = true;
                            cancelCreation(creationTreeItem);
                            ke.consume();
                        }
                    });

                    inlineInput.focusedProperty().addListener((obs, oldF, newF) -> {
                        if (!newF && !isCommitted) {
                            Platform.runLater(() -> cancelCreation(creationTreeItem));
                        }
                    });

                    Platform.runLater(inlineInput::requestFocus);

                } else {
                    if (getTreeItem() == renamingTreeItem) {
                        setText(null);
                        setContextMenu(null);
                        if (editorBox == null) {
                            editorBox = new HBox(6);
                            editorBox.setAlignment(Pos.CENTER_LEFT);
                            inlineInput = new TextField();
                            inlineInput.getStyleClass().add("inline-tree-input");
                            HBox.setHgrow(inlineInput, Priority.ALWAYS);
                            inlineInput.setMaxWidth(Double.MAX_VALUE);
                        }
                        editorBox.getChildren().setAll(item.getIcon(false), inlineInput);
                        setGraphic(editorBox);
                        inlineInput.setText(item.file.getName());
                        inlineInput.selectAll();
                        inlineInput.setOnKeyPressed(ke -> {
                            if (ke.getCode() == KeyCode.ENTER) {
                                commitInlineRename(getTreeItem(), inlineInput.getText());
                                ke.consume();
                            } else if (ke.getCode() == KeyCode.ESCAPE) {
                                cancelInlineRename();
                                ke.consume();
                            }
                        });
                        inlineInput.focusedProperty().addListener((obs, oldFocus, focused) -> {
                            if (!focused && getTreeItem() == renamingTreeItem) {
                                Platform.runLater(SidebarExplorer.this::cancelInlineRename);
                            }
                        });
                        Platform.runLater(inlineInput::requestFocus);
                        return;
                    }
                    setText(item.file.getName().isEmpty() ? item.file.getAbsolutePath() : item.file.getName());
                    boolean expanded = getTreeItem() != null && getTreeItem().isExpanded();
                    setGraphic(item.getIcon(expanded));
                    setContextMenu(createTreeContextMenu(getTreeItem()));
                }
            }
        });
    }

    private void setupTreeInteractions() {
        // Selection change
        fileTreeView.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null && newVal.getValue() != null && !newVal.getValue().isCreationItem && newVal.getValue().file != null && newVal.getValue().file.isFile()) {
                if (onFileSelected != null) {
                    onFileSelected.accept(newVal.getValue().file.toPath());
                }
            }
        });

        // Click handler
        fileTreeView.setOnMouseClicked(e -> {
            if (e.getButton() == MouseButton.PRIMARY) {
                TreeItem<FileItem> selected = fileTreeView.getSelectionModel().getSelectedItem();
                if (selected != null && selected.getValue() != null && !selected.getValue().isCreationItem && selected.getValue().file != null && selected.getValue().file.isFile()) {
                    if (onFileSelected != null) {
                        onFileSelected.accept(selected.getValue().file.toPath());
                    }
                }
            }
        });

        // Enter key
        fileTreeView.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ENTER) {
                TreeItem<FileItem> selected = fileTreeView.getSelectionModel().getSelectedItem();
                if (selected != null && selected.getValue() != null && !selected.getValue().isCreationItem && selected.getValue().file != null && selected.getValue().file.isFile()) {
                    if (onFileSelected != null) {
                        onFileSelected.accept(selected.getValue().file.toPath());
                    }
                }
            }
        });
    }

    /**
     * Starts VS Code-identical inline creation of a file or folder inside the active folder.
     */
    public void startInlineCreation(boolean isFolder) {
        if (currentWorkspacePath == null || fileTreeView == null) return;

        TreeItem<FileItem> targetParentItem = null;
        TreeItem<FileItem> selected = fileTreeView.getSelectionModel().getSelectedItem();

        if (selected != null && selected.getValue() != null && !selected.getValue().isCreationItem) {
            FileItem selectedItem = selected.getValue();
            if (selectedItem.file != null && selectedItem.file.isDirectory()) {
                targetParentItem = selected;
            } else if (selected.getParent() != null) {
                targetParentItem = selected.getParent();
            }
        }

        if (targetParentItem == null) {
            targetParentItem = fileTreeView.getRoot();
        }

        if (targetParentItem == null) return;

        // Ensure target folder is expanded
        targetParentItem.setExpanded(true);

        File parentDir = (targetParentItem.getValue() != null && targetParentItem.getValue().file != null)
                ? targetParentItem.getValue().file
                : currentWorkspacePath.toFile();

        // Clean up any existing creation items
        removeCreationItems(targetParentItem);

        FileItem creationItem = new FileItem(null, true, isFolder, parentDir);
        TreeItem<FileItem> creationTreeItem = new TreeItem<>(creationItem);

        // Add at top of directory
        targetParentItem.getChildren().add(0, creationTreeItem);

        // Select and scroll to it
        fileTreeView.getSelectionModel().select(creationTreeItem);
        fileTreeView.scrollTo(fileTreeView.getRow(creationTreeItem));
    }

    private void removeCreationItems(TreeItem<FileItem> parent) {
        if (parent == null) return;
        parent.getChildren().removeIf(item -> item.getValue() != null && item.getValue().isCreationItem);
    }

    private void commitCreation(TreeItem<FileItem> creationTreeItem, File targetDir, String name, boolean isFolder) {
        if (creationTreeItem == null || targetDir == null) return;
        TreeItem<FileItem> parent = creationTreeItem.getParent();
        if (parent == null) return;

        if (name.isEmpty() || name.contains("/") || name.contains("\\") || name.contains(":") ||
                name.contains("*") || name.contains("?") || name.contains("\"") || name.contains("<") ||
                name.contains(">") || name.contains("|")) {
            parent.getChildren().remove(creationTreeItem);
            return;
        }

        File newFile = new File(targetDir, name);
        if (newFile.exists()) {
            parent.getChildren().remove(creationTreeItem);
            return;
        }

        try {
            if (isFolder) {
                Files.createDirectories(newFile.toPath());
            } else {
                Files.createFile(newFile.toPath());
            }

            parent.getChildren().remove(creationTreeItem);

            LazyTreeItem newTreeItem = new LazyTreeItem(new FileItem(newFile));

            // Sorted insertion: folders first, then files alphabetically
            int insertIndex = 0;
            for (int i = 0; i < parent.getChildren().size(); i++) {
                FileItem existing = parent.getChildren().get(i).getValue();
                if (existing == null || existing.file == null || existing.isCreationItem) continue;
                if (isFolder) {
                    if (!existing.file.isDirectory() || newFile.getName().compareToIgnoreCase(existing.file.getName()) < 0) {
                        insertIndex = i;
                        break;
                    }
                } else {
                    if (!existing.file.isDirectory() && newFile.getName().compareToIgnoreCase(existing.file.getName()) < 0) {
                        insertIndex = i;
                        break;
                    }
                }
                insertIndex = i + 1;
            }
            if (insertIndex > parent.getChildren().size()) insertIndex = parent.getChildren().size();
            parent.getChildren().add(insertIndex, newTreeItem);

            fileTreeView.getSelectionModel().select(newTreeItem);

            // Automatically open newly created file in editor tab!
            if (!isFolder && onFileSelected != null) {
                onFileSelected.accept(newFile.toPath());
            }

        } catch (IOException e) {
            System.err.println("Creation failed: " + e.getMessage());
            parent.getChildren().remove(creationTreeItem);
        }
    }

    private void cancelCreation(TreeItem<FileItem> creationTreeItem) {
        if (creationTreeItem != null && creationTreeItem.getParent() != null) {
            creationTreeItem.getParent().getChildren().remove(creationTreeItem);
        }
    }

    /**
     * Collapses all expanded folders back to the root level.
     */
    public void collapseAll() {
        TreeItem<FileItem> root = fileTreeView.getRoot();
        if (root != null) {
            for (TreeItem<FileItem> child : root.getChildren()) {
                collapseRecursively(child);
            }
        }
    }

    private void collapseRecursively(TreeItem<FileItem> item) {
        if (item == null) return;
        if (!item.isLeaf()) {
            item.setExpanded(false);
            for (TreeItem<FileItem> child : item.getChildren()) {
                collapseRecursively(child);
            }
        }
    }

    private ContextMenu createTreeContextMenu(TreeItem<FileItem> treeItem) {
        if (treeItem == null || treeItem.getValue() == null || treeItem.getValue().file == null) {
            return null;
        }
        File file = treeItem.getValue().file;
        ContextMenu menu = new ContextMenu();

        MenuItem newFile = new MenuItem("New File...");
        newFile.setGraphic(IconFactory.getIcon(Codicons.NEW_FILE, 13));
        newFile.setOnAction(e -> startInlineCreation(false));

        MenuItem newFolder = new MenuItem("New Folder...");
        newFolder.setGraphic(IconFactory.getIcon(Codicons.NEW_FOLDER, 13));
        newFolder.setOnAction(e -> startInlineCreation(true));

        String os = System.getProperty("os.name", "").toLowerCase();
        MenuItem reveal = new MenuItem(os.contains("mac") ? "Reveal in Finder" : "Show in File Explorer");
        reveal.setGraphic(IconFactory.getIcon(Codicons.FOLDER_OPENED, 13));
        reveal.setOnAction(e -> revealInFileManager(file));

        MenuItem copyPath = new MenuItem("Copy Path");
        copyPath.setGraphic(IconFactory.getIcon(Codicons.FILES, 13));
        copyPath.setOnAction(e -> copyToClipboard(file.getAbsolutePath()));

        MenuItem copyRelPath = new MenuItem("Copy Relative Path");
        copyRelPath.setOnAction(e -> {
            if (currentWorkspacePath != null) {
                try {
                    copyToClipboard(currentWorkspacePath.relativize(file.toPath()).toString());
                } catch (IllegalArgumentException ex) {
                    copyToClipboard(file.getName());
                }
            } else {
                copyToClipboard(file.getName());
            }
        });

        MenuItem renameItem = new MenuItem("Rename...");
        renameItem.setGraphic(IconFactory.getIcon(Codicons.EDIT, 13));
        renameItem.setOnAction(e -> renameFileItem(treeItem));

        MenuItem deleteItem = new MenuItem("Delete");
        deleteItem.setGraphic(IconFactory.getIcon(Codicons.TRASH, 13, "#e76f51"));
        deleteItem.setOnAction(e -> deleteFileItem(treeItem));

        menu.getItems().addAll(
                newFile, newFolder,
                new SeparatorMenuItem(),
                reveal, copyPath, copyRelPath, renameItem,
                new SeparatorMenuItem(),
                deleteItem
        );
        return menu;
    }

    private void renameFileItem(TreeItem<FileItem> treeItem) {
        if (treeItem == null || treeItem.getValue() == null || treeItem.getValue().file == null) return;
        renamingTreeItem = treeItem;
        fileTreeView.getSelectionModel().select(treeItem);
        fileTreeView.refresh();
    }

    private void commitInlineRename(TreeItem<FileItem> treeItem, String requestedName) {
        if (treeItem == null || treeItem.getValue() == null || treeItem.getValue().file == null) {
            cancelInlineRename();
            return;
        }
        File source = treeItem.getValue().file;
        String name = requestedName == null ? "" : requestedName.trim();
        if (name.isEmpty() || name.equals(source.getName()) || name.matches(".*[\\\\/:*?\"<>|].*")) {
            cancelInlineRename();
            return;
        }
        Path target = source.toPath().resolveSibling(name);
        if (Files.exists(target)) {
            cancelInlineRename();
            return;
        }
        try {
            Files.move(source.toPath(), target);
            if (onFileRenamed != null) onFileRenamed.accept(source.toPath(), target);
        } catch (IOException ex) {
            System.err.println("Rename failed: " + ex.getMessage());
        } finally {
            renamingTreeItem = null;
            if (currentWorkspacePath != null) setWorkspacePath(currentWorkspacePath);
            else fileTreeView.refresh();
        }
    }

    private void cancelInlineRename() {
        if (renamingTreeItem == null) return;
        renamingTreeItem = null;
        fileTreeView.refresh();
    }

    private void revealInFileManager(File file) {
        try {
            if (Desktop.isDesktopSupported()) {
                Desktop desktop = Desktop.getDesktop();
                if (file.isDirectory()) {
                    desktop.open(file);
                } else if (file.getParentFile() != null) {
                    desktop.open(file.getParentFile());
                }
            }
        } catch (Exception ex) {
            System.err.println("Cannot reveal file: " + ex.getMessage());
        }
    }

    private void copyToClipboard(String text) {
        javafx.scene.input.Clipboard clipboard = javafx.scene.input.Clipboard.getSystemClipboard();
        javafx.scene.input.ClipboardContent content = new javafx.scene.input.ClipboardContent();
        content.putString(text);
        clipboard.setContent(content);
    }

    private void deleteFileItem(TreeItem<FileItem> treeItem) {
        if (treeItem == null || treeItem.getValue() == null || treeItem.getValue().file == null) return;
        File file = treeItem.getValue().file;

        Runnable doDelete = () -> {
            try {
                if (file.isDirectory()) {
                    deleteRecursively(file);
                } else {
                    Files.deleteIfExists(file.toPath());
                }
                if (treeItem.getParent() != null) {
                    treeItem.getParent().getChildren().remove(treeItem);
                }
            } catch (IOException ex) {
                if (modalOverlayPane != null) {
                    modalOverlayPane.showError("Delete Failed", "Could not delete " + file.getName() + ": " + ex.getMessage());
                } else {
                    Alert err = new Alert(Alert.AlertType.ERROR);
                    err.setTitle("Delete Failed");
                    err.setContentText("Could not delete " + file.getName() + ": " + ex.getMessage());
                    err.showAndWait();
                }
            }
        };

        if (modalOverlayPane != null) {
            modalOverlayPane.showConfirmation(
                    "Delete " + (file.isDirectory() ? "Folder" : "File"),
                    "Are you sure you want to delete '" + file.getName() + "'?\n\nThis action permanently deletes the item from disk.",
                    "Delete",
                    doDelete
            );
        } else {
            Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
            alert.setTitle("Delete " + (file.isDirectory() ? "Folder" : "File"));
            alert.setHeaderText("Are you sure you want to delete '" + file.getName() + "'?");
            alert.setContentText("This action permanently deletes the item from disk.");

            Optional<ButtonType> result = alert.showAndWait();
            if (result.isPresent() && result.get() == ButtonType.OK) {
                doDelete.run();
            }
        }
    }

    private void deleteRecursively(File file) throws IOException {
        File[] children = file.listFiles();
        if (children != null) {
            for (File c : children) {
                deleteRecursively(c);
            }
        }
        Files.deleteIfExists(file.toPath());
    }

    private void showMoreMenu(Button anchor) {
        ContextMenu menu = new ContextMenu();
        MenuItem openFolder = new MenuItem("Open Folder...");
        openFolder.setGraphic(IconFactory.getIcon(Codicons.FOLDER_OPENED, 12));
        openFolder.setOnAction(e -> {
            javafx.stage.DirectoryChooser chooser = new javafx.stage.DirectoryChooser();
            chooser.setTitle("Open Workspace Folder");
            File folder = chooser.showDialog(getScene().getWindow());
            if (folder != null) {
                setWorkspacePath(folder.toPath());
            }
        });

        MenuItem closeFolder = new MenuItem("Close Folder");
        closeFolder.setGraphic(IconFactory.getIcon(Codicons.CLOSE, 12));
        closeFolder.setOnAction(e -> closeWorkspaceFolder());

        MenuItem refresh = new MenuItem("Refresh");
        refresh.setGraphic(IconFactory.getIcon(Codicons.REFRESH, 12));
        refresh.setOnAction(e -> {
            if (currentWorkspacePath != null) setWorkspacePath(currentWorkspacePath);
        });

        MenuItem collapse = new MenuItem("Collapse All");
        collapse.setGraphic(IconFactory.getIcon(Codicons.COLLAPSE_ALL, 12));
        collapse.setOnAction(e -> collapseAll());

        menu.getItems().addAll(openFolder, closeFolder, new SeparatorMenuItem(), refresh, collapse);
        menu.show(anchor, javafx.geometry.Side.BOTTOM, 0, 0);
    }

    private VBox createTemplatesPane() {
        VBox pane = new VBox(10);
        pane.setPadding(new Insets(14));
        VBox.setVgrow(pane, Priority.ALWAYS);

        Label desc = new Label("1-Click Project Scaffolding");
        desc.setStyle("-fx-font-size: 11px; -fx-text-fill: -text-secondary; -fx-font-weight: bold;");
        pane.getChildren().add(desc);

        Map<String, String> templates = TemplateFactory.getAvailableTemplates();
        for (Map.Entry<String, String> entry : templates.entrySet()) {
            String ext = entry.getKey();
            String name = entry.getValue();
            Template tpl = TemplateFactory.getTemplate(ext);

            Button btn = new Button(" " + name + " (." + ext + ")");
            btn.setGraphic(IconFactory.getFileIcon("." + ext, 14));
            btn.setMaxWidth(Double.MAX_VALUE);
            btn.getStyleClass().add("btn-modern");
            btn.setAlignment(Pos.CENTER_LEFT);
            btn.setOnAction(e -> {
                if (onTemplateSelected != null) {
                    onTemplateSelected.accept(new TemplateChoice(tpl, "Untitled." + ext));
                }
            });
            pane.getChildren().add(btn);
        }

        return pane;
    }

    public void showView(ActivityBar.Panel panel) {
        if (panel == ActivityBar.Panel.EXPLORER) {
            topTitleLabel.setText("EXPLORER");
            explorerPane.setVisible(true);
            explorerPane.setManaged(true);
            templatesPane.setVisible(false);
            templatesPane.setManaged(false);
            setVisible(true);
            setManaged(true);
        } else if (panel == ActivityBar.Panel.TEMPLATES) {
            topTitleLabel.setText("TEMPLATES & SCAFFOLDS");
            explorerPane.setVisible(false);
            explorerPane.setManaged(false);
            templatesPane.setVisible(true);
            templatesPane.setManaged(true);
            setVisible(true);
            setManaged(true);
        } else if (panel == null || panel == ActivityBar.Panel.AI_COPILOT || panel == ActivityBar.Panel.SEARCH) {
            if (panel == null) {
                setVisible(false);
                setManaged(false);
            }
        }
    }

    public void setWorkspacePath(Path path) {
        if (path == null) {
            closeWorkspaceFolder();
            return;
        }

        this.currentWorkspacePath = path.toAbsolutePath().normalize();
        File rootFile = this.currentWorkspacePath.toFile();

        // Update workspace name in the section header (e.g. "CODING")
        String folderName = rootFile.getName().isEmpty() ? rootFile.getAbsolutePath() : rootFile.getName();
        if (workspaceTitleLabel != null) {
            workspaceTitleLabel.setText(folderName.toUpperCase());
        }

        TreeItem<FileItem> rootItem = new LazyTreeItem(new FileItem(rootFile));
        rootItem.setExpanded(true);
        fileTreeView.setRoot(rootItem);
        fileTreeView.setVisible(true);
        fileTreeView.setManaged(true);
        if (emptyWorkspaceBox != null) {
            emptyWorkspaceBox.setVisible(false);
            emptyWorkspaceBox.setManaged(false);
        }

        if (onWorkspaceChanged != null) {
            onWorkspaceChanged.accept(this.currentWorkspacePath);
        }
    }

    public void closeWorkspaceFolder() {
        this.currentWorkspacePath = null;
        if (workspaceTitleLabel != null) {
            workspaceTitleLabel.setText("NO FOLDER OPENED");
        }
        fileTreeView.setRoot(null);
        fileTreeView.setVisible(false);
        fileTreeView.setManaged(false);
        if (emptyWorkspaceBox != null) {
            emptyWorkspaceBox.setVisible(true);
            emptyWorkspaceBox.setManaged(true);
        }

        if (onWorkspaceChanged != null) {
            onWorkspaceChanged.accept(null);
        }
    }

    public void promptOpenFolder() {
        if (onOpenFolderRequested != null) {
            onOpenFolderRequested.run();
            return;
        }
        javafx.stage.DirectoryChooser chooser = new javafx.stage.DirectoryChooser();
        chooser.setTitle("Open Workspace Folder");
        if (getScene() != null && getScene().getWindow() != null) {
            File folder = chooser.showDialog(getScene().getWindow());
            if (folder != null) {
                setWorkspacePath(folder.toPath());
            }
        }
    }

    public void refreshPath(Path targetPath) {
        if (targetPath == null) return;
        Platform.runLater(() -> {
            if (fileTreeView == null || fileTreeView.getRoot() == null) return;
            Path normalized = targetPath.toAbsolutePath().normalize();
            TreeItem<FileItem> item = findLoadedTreeItem(fileTreeView.getRoot(), normalized);
            if (item instanceof LazyTreeItem lazy) {
                lazy.refresh();
            }
        });
    }

    public void refreshWorkspace() {
        Platform.runLater(() -> {
            if (fileTreeView == null || fileTreeView.getRoot() == null) return;
            refreshLoadedItem(fileTreeView.getRoot());
        });
    }

    private void refreshLoadedItem(TreeItem<FileItem> item) {
        if (item == null) return;
        if (item instanceof LazyTreeItem lazy && lazy.isLoaded()) {
            lazy.refresh();
            for (TreeItem<FileItem> child : item.getChildren()) {
                refreshLoadedItem(child);
            }
        }
    }

    private TreeItem<FileItem> findLoadedTreeItem(TreeItem<FileItem> current, Path targetPath) {
        if (current == null || current.getValue() == null || current.getValue().file == null) {
            return null;
        }
        Path currentPath = current.getValue().file.toPath().toAbsolutePath().normalize();
        if (currentPath.equals(targetPath)) {
            return current;
        }
        if (current instanceof LazyTreeItem lazy && !lazy.isLoaded()) {
            return null;
        }
        for (TreeItem<FileItem> child : current.getChildren()) {
            TreeItem<FileItem> found = findLoadedTreeItem(child, targetPath);
            if (found != null) return found;
        }
        return null;
    }

    public void setOnOpenFolderRequested(Runnable onOpenFolderRequested) {
        this.onOpenFolderRequested = onOpenFolderRequested;
    }

    /**
     * High-performance lazy-loading TreeItem for file system hierarchies.
     * Children are only queried and instantiated when a folder is actually expanded,
     * reducing startup time and memory footprint to O(1) per folder.
     */
    private static class LazyTreeItem extends TreeItem<FileItem> {
        private boolean isFirstChildren = true;
        private boolean isFirstLeaf = true;
        private boolean isLeaf = false;

        public LazyTreeItem(FileItem item) {
            super(item);
        }

        public boolean isLoaded() {
            return !isFirstChildren;
        }

        public void refresh() {
            if (isFirstChildren) {
                return;
            }
            File dir = getValue() != null ? getValue().file : null;
            if (dir == null || !dir.isDirectory()) return;

            ObservableList<TreeItem<FileItem>> newChildren = buildChildren(this);
            Map<String, TreeItem<FileItem>> existingMap = new java.util.HashMap<>();
            for (TreeItem<FileItem> oldChild : super.getChildren()) {
                if (oldChild.getValue() != null && oldChild.getValue().file != null) {
                    existingMap.put(oldChild.getValue().file.getAbsolutePath(), oldChild);
                }
            }

            ObservableList<TreeItem<FileItem>> merged = FXCollections.observableArrayList();
            for (TreeItem<FileItem> newItem : newChildren) {
                if (newItem.getValue() != null && newItem.getValue().file != null) {
                    String pathKey = newItem.getValue().file.getAbsolutePath();
                    TreeItem<FileItem> oldItem = existingMap.get(pathKey);
                    if (oldItem != null) {
                        merged.add(oldItem);
                    } else {
                        merged.add(newItem);
                    }
                }
            }
            super.getChildren().setAll(merged);
        }

        @Override
        public ObservableList<TreeItem<FileItem>> getChildren() {
            if (isFirstChildren) {
                isFirstChildren = false;
                super.getChildren().setAll(buildChildren(this));
            }
            return super.getChildren();
        }

        @Override
        public boolean isLeaf() {
            if (isFirstLeaf) {
                isFirstLeaf = false;
                File f = getValue() != null ? getValue().file : null;
                isLeaf = f == null || f.isFile();
            }
            return isLeaf;
        }

        private static ObservableList<TreeItem<FileItem>> buildChildren(TreeItem<FileItem> treeItem) {
            if (treeItem == null || treeItem.getValue() == null) {
                return FXCollections.emptyObservableList();
            }
            File dir = treeItem.getValue().file;
            if (dir != null && dir.isDirectory()) {
                File[] files = dir.listFiles();
                if (files != null && files.length > 0) {
                    ObservableList<TreeItem<FileItem>> children = FXCollections.observableArrayList();
                    // Sort folders first, then files alphabetically
                    java.util.Arrays.sort(files, (a, b) -> {
                        if (a.isDirectory() && !b.isDirectory()) return -1;
                        if (!a.isDirectory() && b.isDirectory()) return 1;
                        return a.getName().compareToIgnoreCase(b.getName());
                    });
                    for (File child : files) {
                        String name = child.getName();
                        // Filter hidden files, VCS, and build output directories
                        if (name.startsWith(".") || name.equals("target") || name.equals("node_modules")) continue;
                        children.add(new LazyTreeItem(new FileItem(child)));
                    }
                    return children;
                }
            }
            return FXCollections.emptyObservableList();
        }
    }

    public void setOnFileSelected(Consumer<Path> onFileSelected) { this.onFileSelected = onFileSelected; }
    public void setOnTemplateSelected(Consumer<TemplateChoice> onTemplateSelected) { this.onTemplateSelected = onTemplateSelected; }
    public void setOnNewFileRequested(Runnable onNewFileRequested) { this.onNewFileRequested = onNewFileRequested; }
    public Runnable getOnNewFileRequested() { return this.onNewFileRequested; }
    public void setOnWorkspaceChanged(Consumer<Path> onWorkspaceChanged) { this.onWorkspaceChanged = onWorkspaceChanged; }
    public void setOnFileRenamed(BiConsumer<Path, Path> onFileRenamed) { this.onFileRenamed = onFileRenamed; }

    public Path getRootPath() {
        return currentWorkspacePath;
    }
}
