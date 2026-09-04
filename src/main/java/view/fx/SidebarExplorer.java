package view.fx;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.*;
import org.kordamp.ikonli.codicons.Codicons;
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
        public FileItem(File file) {
            this.file = file;
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
                    if (item.file.isDirectory()) {
                        setGraphic(IconFactory.getFolderIcon(getTreeItem() != null && getTreeItem().isExpanded(), 14));
                    } else {
                        setGraphic(IconFactory.getFileIcon(item.file.getName(), 14));
                    }
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
        TreeItem<FileItem> rootItem = new TreeItem<>(new FileItem(rootFile));
        rootItem.setExpanded(true);

        populateTree(rootFile, rootItem);
        fileTreeView.setRoot(rootItem);
        fileTreeView.setShowRoot(true);
    }

    private void populateTree(File dir, TreeItem<FileItem> parent) {
        File[] files = dir.listFiles();
        if (files == null) return;

        // Sort folders first, then files alphabetically
        java.util.Arrays.sort(files, (a, b) -> {
            if (a.isDirectory() && !b.isDirectory()) return -1;
            if (!a.isDirectory() && b.isDirectory()) return 1;
            return a.getName().compareToIgnoreCase(b.getName());
        });

        for (File f : files) {
            if (f.getName().startsWith(".") || f.getName().equals("target")) continue;
            TreeItem<FileItem> item = new TreeItem<>(new FileItem(f));
            parent.getChildren().add(item);
            if (f.isDirectory()) {
                populateTree(f, item);
            }
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
