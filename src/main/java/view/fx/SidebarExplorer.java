package view.fx;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.*;
import org.kordamp.ikonli.codicons.Codicons;
import org.kordamp.ikonli.javafx.FontIcon;
import template.Template;
import template.TemplateFactory;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Collapsible, responsive Sidebar showing Project Explorer and 1-click Template Scaffolds
 * powered by official VS Code Codicons.
 * Features lazy on-demand directory expansion for high performance and low memory footprint.
 */
public class SidebarExplorer extends VBox {

    private final Label titleLabel;
    private final StackPane contentStack;
    private final VBox explorerPane;
    private final VBox templatesPane;

    private TreeView<FileItem> fileTreeView;
    private Path currentWorkspacePath;

    private Consumer<Path> onFileSelected;
    private Consumer<TemplateChoice> onTemplateSelected;
    private Runnable onNewFileRequested;

    public static class FileItem {
        public final File file;
        private FontIcon cachedIcon;

        public FileItem(File file) {
            this.file = file;
        }

        public FontIcon getIcon(boolean isExpanded) {
            if (file.isDirectory()) {
                return IconFactory.getFolderIcon(isExpanded, 14);
            }
            if (cachedIcon == null) {
                cachedIcon = IconFactory.getFileIcon(file.getName(), 14);
            }
            return cachedIcon;
        }

        @Override
        public String toString() {
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
        setMinWidth(160);
        setPrefWidth(240);
        setMaxWidth(500);

        // Header
        HBox header = new HBox(8);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(10, 14, 8, 14));
        header.getStyleClass().add("sidebar-header");

        titleLabel = new Label("EXPLORER");
        titleLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 11px; -fx-text-fill: -text-secondary;");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button newFileBtn = new Button();
        newFileBtn.setGraphic(IconFactory.getIcon(Codicons.NEW_FILE, 14));
        newFileBtn.setTooltip(new Tooltip("New File (Cmd/Ctrl+N)"));
        newFileBtn.setStyle("-fx-background-color: transparent; -fx-cursor: hand; -fx-padding: 0 4 0 4;");
        newFileBtn.setOnAction(e -> {
            if (onNewFileRequested != null) onNewFileRequested.run();
        });

        Button refreshBtn = new Button();
        refreshBtn.setGraphic(IconFactory.getIcon(Codicons.REFRESH, 14));
        refreshBtn.setTooltip(new Tooltip("Refresh Workspace"));
        refreshBtn.setStyle("-fx-background-color: transparent; -fx-cursor: hand; -fx-padding: 0 4 0 4;");
        refreshBtn.setOnAction(e -> {
            if (currentWorkspacePath != null) setWorkspacePath(currentWorkspacePath);
        });

        header.getChildren().addAll(titleLabel, spacer, newFileBtn, refreshBtn);

        // Explorer Pane
        explorerPane = createExplorerPane();

        // Templates Pane
        templatesPane = createTemplatesPane();
        templatesPane.setVisible(false);
        templatesPane.setManaged(false);

        contentStack = new StackPane(explorerPane, templatesPane);
        VBox.setVgrow(contentStack, Priority.ALWAYS);

        getChildren().addAll(header, contentStack);

        // Default workspace is current directory
        setWorkspacePath(Paths.get("."));
    }

    private VBox createExplorerPane() {
        VBox pane = new VBox();
        VBox.setVgrow(pane, Priority.ALWAYS);

        fileTreeView = new TreeView<>();
        fileTreeView.getStyleClass().add("tree-view");
        VBox.setVgrow(fileTreeView, Priority.ALWAYS);

        fileTreeView.setCellFactory(tv -> new TreeCell<>() {
            @Override
            protected void updateItem(FileItem item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    setText(item.file.getName().isEmpty() ? item.file.getAbsolutePath() : item.file.getName());
                    boolean expanded = getTreeItem() != null && getTreeItem().isExpanded();
                    setGraphic(item.getIcon(expanded));
                }
            }
        });

        // Trigger on selection change
        fileTreeView.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null && newVal.getValue() != null && newVal.getValue().file.isFile()) {
                if (onFileSelected != null) {
                    onFileSelected.accept(newVal.getValue().file.toPath());
                }
            }
        });

        // Trigger on single/double click
        fileTreeView.setOnMouseClicked(e -> {
            TreeItem<FileItem> selected = fileTreeView.getSelectionModel().getSelectedItem();
            if (selected != null && selected.getValue() != null && selected.getValue().file.isFile()) {
                if (onFileSelected != null) {
                    onFileSelected.accept(selected.getValue().file.toPath());
                }
            }
        });

        // Trigger on Enter key
        fileTreeView.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ENTER) {
                TreeItem<FileItem> selected = fileTreeView.getSelectionModel().getSelectedItem();
                if (selected != null && selected.getValue() != null && selected.getValue().file.isFile()) {
                    if (onFileSelected != null) {
                        onFileSelected.accept(selected.getValue().file.toPath());
                    }
                }
            }
        });

        pane.getChildren().add(fileTreeView);
        return pane;
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
            titleLabel.setText("EXPLORER");
            explorerPane.setVisible(true);
            explorerPane.setManaged(true);
            templatesPane.setVisible(false);
            templatesPane.setManaged(false);
            setVisible(true);
            setManaged(true);
        } else if (panel == ActivityBar.Panel.TEMPLATES) {
            titleLabel.setText("TEMPLATES & SCAFFOLDS");
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
        this.currentWorkspacePath = path.toAbsolutePath().normalize();
        File rootFile = this.currentWorkspacePath.toFile();
        TreeItem<FileItem> rootItem = new LazyTreeItem(new FileItem(rootFile));
        rootItem.setExpanded(true);
        fileTreeView.setRoot(rootItem);
        fileTreeView.setShowRoot(true);
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

    /**
     * Returns the current workspace root path, or null if not set.
     */
    public Path getRootPath() {
        return currentWorkspacePath;
    }
}
