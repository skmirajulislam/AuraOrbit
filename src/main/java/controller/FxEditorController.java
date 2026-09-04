package controller;

import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import service.FileService;
import service.ThemeService;
import template.Template;
import view.fx.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Master JavaFX Controller orchestrating multi-tab documents, sidebars,
 * Split Editor side-by-side view, AI Copilot studio, themes, search, and commands.
 */
public class FxEditorController {

    private final Stage stage;
    private final ThemeService themeService;
    private final FileService fileService;

    private TabPane tabPaneLeft;
    private TabPane tabPaneRight;
    private SplitPane editorSplitPane;
    private ActivityBar activityBar;
    private SidebarExplorer sidebarExplorer;
    private AiAssistantPane aiAssistantPane;
    private FxStatusBar statusBar;
    private CommandPalette commandPalette;
    private SplitPane masterSplitPane;
    private TerminalPane terminalPane;
    private SplitPane editorTerminalSplitPane;

    private final List<EditorTabController> tabControllers = new ArrayList<>();
    private boolean isSplitEditorActive = false;

    public FxEditorController(Stage stage, ThemeService themeService) {
        this.stage = stage;
        this.themeService = themeService;
        this.fileService = new FileService();
    }

    public void initializeComponents(
            TabPane tabPaneLeft,
            TabPane tabPaneRight,
            SplitPane editorSplitPane,
            SplitPane masterSplitPane,
            ActivityBar activityBar,
            SidebarExplorer sidebarExplorer,
            AiAssistantPane aiAssistantPane,
            FxStatusBar statusBar,
            CommandPalette commandPalette,
            TerminalPane terminalPane,
            SplitPane editorTerminalSplitPane
    ) {
        this.tabPaneLeft = tabPaneLeft;
        this.tabPaneRight = tabPaneRight;
        this.editorSplitPane = editorSplitPane;
        this.masterSplitPane = masterSplitPane;
        this.activityBar = activityBar;
        this.sidebarExplorer = sidebarExplorer;
        this.aiAssistantPane = aiAssistantPane;
        this.statusBar = statusBar;
        this.commandPalette = commandPalette;
        this.terminalPane = terminalPane;
        this.editorTerminalSplitPane = editorTerminalSplitPane;

        setupTabPanes();
        setupSidebar();
        setupActivityBar();
        setupAiAssistant();
        setupTerminal();
        setupStatusBar();
        setupCommandPalette();

        // Create default initial tab
        createNewTab("untitled.txt");
    }

    private void setupTabPanes() {
        tabPaneLeft.getSelectionModel().selectedItemProperty().addListener((obs, oldTab, newTab) -> updateActiveTabMetrics());
        tabPaneRight.getSelectionModel().selectedItemProperty().addListener((obs, oldTab, newTab) -> updateActiveTabMetrics());
    }

    private void setupSidebar() {
        sidebarExplorer.setOnFileSelected(this::openFile);
        sidebarExplorer.setOnTemplateSelected(choice -> {
            if (choice != null && choice.template != null) {
                createFromTemplate(choice.template, choice.defaultName);
            }
        });
        sidebarExplorer.setOnNewFileRequested(() -> createNewTab("untitled.txt"));
    }

    private void setupActivityBar() {
        activityBar.setOnPanelToggled(panel -> {
            if (panel == ActivityBar.Panel.EXPLORER || panel == ActivityBar.Panel.TEMPLATES) {
                sidebarExplorer.showView(panel);
                ensureSidebarVisible(true);
            } else if (panel == ActivityBar.Panel.SEARCH) {
                handleFind(true);
            } else if (panel == ActivityBar.Panel.AI_COPILOT) {
                toggleAiPanel();
            } else if (panel == ActivityBar.Panel.TERMINAL) {
                toggleTerminal();
            } else if (panel == null) {
                ensureSidebarVisible(false);
            }
        });
        activityBar.setOnThemeAction(this::showThemePicker);
        activityBar.setOnInfoAction(this::showAboutDialog);
    }

    private void ensureSidebarVisible(boolean visible) {
        if (visible) {
            if (!masterSplitPane.getItems().contains(sidebarExplorer)) {
                masterSplitPane.getItems().add(0, sidebarExplorer);
                masterSplitPane.setDividerPosition(0, 0.22);
            }
            sidebarExplorer.setVisible(true);
            sidebarExplorer.setManaged(true);
        } else {
            sidebarExplorer.setVisible(false);
            sidebarExplorer.setManaged(false);
            masterSplitPane.getItems().remove(sidebarExplorer);
        }
    }

    private void setupAiAssistant() {
        aiAssistantPane.setActiveFileSupplier(() -> {
            EditorTabController current = getActiveTabController();
            return current != null ? current.getDocument().getFileName() : "untitled.txt";
        });

        aiAssistantPane.setSelectedCodeSupplier(() -> {
            EditorTabController current = getActiveTabController();
            if (current != null) {
                return current.getEditorPane().getCodeArea().getSelectedText();
            }
            return "";
        });

        aiAssistantPane.setEntireFileContentSupplier(() -> {
            EditorTabController current = getActiveTabController();
            if (current != null) {
                return current.getEditorPane().getCodeArea().getText();
            }
            return "";
        });

        aiAssistantPane.setOnInsertCodeToEditor(code -> {
            EditorTabController current = getActiveTabController();
            if (current != null && code != null) {
                int caret = current.getEditorPane().getCodeArea().getCaretPosition();
                current.getEditorPane().getCodeArea().insertText(caret, code);
            }
        });

        aiAssistantPane.setOnReplaceSelectionInEditor(code -> {
            EditorTabController current = getActiveTabController();
            if (current != null && code != null) {
                current.getEditorPane().getCodeArea().replaceSelection(code);
            }
        });

        aiAssistantPane.setOnCloseRequested(this::toggleAiPanel);
    }

    private void setupTerminal() {
        terminalPane.setWorkingDirectorySupplier(() -> {
            // Use the sidebar explorer's root path if available
            Path explorerRoot = sidebarExplorer.getRootPath();
            if (explorerRoot != null) return explorerRoot;
            // Fallback: use the active file's parent directory
            EditorTabController current = getActiveTabController();
            if (current != null && current.getDocument().getFilePath() != null) {
                return current.getDocument().getFilePath().getParent();
            }
            return null;
        });
        terminalPane.setOnCloseRequested(this::toggleTerminal);
    }

    /**
     * Toggle integrated terminal panel visibility.
     */
    public void toggleTerminal() {
        if (terminalPane.isTerminalVisible()) {
            terminalPane.hideTerminal();
            editorTerminalSplitPane.getItems().remove(terminalPane);
        } else {
            terminalPane.showTerminal();
            if (!editorTerminalSplitPane.getItems().contains(terminalPane)) {
                editorTerminalSplitPane.getItems().add(terminalPane);
                editorTerminalSplitPane.setDividerPosition(
                        editorTerminalSplitPane.getItems().size() - 2, 0.7);
            }
        }
    }

    /**
     * Create a new terminal tab (Ctrl+Shift+`)
     */
    public void createNewTerminalTab() {
        if (!terminalPane.isTerminalVisible()) {
            toggleTerminal();
        } else {
            terminalPane.createNewTerminal();
        }
    }

    public void toggleAiPanel() {
        if (masterSplitPane.getItems().contains(aiAssistantPane)) {
            masterSplitPane.getItems().remove(aiAssistantPane);
            aiAssistantPane.setVisible(false);
            aiAssistantPane.setManaged(false);
            activityBar.setActivePanel(ActivityBar.Panel.EXPLORER);
        } else {
            masterSplitPane.getItems().add(aiAssistantPane);
            aiAssistantPane.setVisible(true);
            aiAssistantPane.setManaged(true);
            masterSplitPane.setDividerPosition(masterSplitPane.getItems().size() - 1, 0.75);
            activityBar.setActivePanel(ActivityBar.Panel.AI_COPILOT);
        }
    }

    public void toggleSplitEditor() {
        isSplitEditorActive = !isSplitEditorActive;
        if (isSplitEditorActive) {
            if (!editorSplitPane.getItems().contains(tabPaneRight)) {
                editorSplitPane.getItems().add(tabPaneRight);
                editorSplitPane.setDividerPosition(0, 0.5);
            }
            tabPaneRight.setVisible(true);
            tabPaneRight.setManaged(true);

            if (tabPaneRight.getTabs().isEmpty()) {
                EditorTabController current = getActiveTabController();
                if (current != null && current.getDocument().getFilePath() != null) {
                    openFileInTargetTabPane(current.getDocument().getFilePath(), tabPaneRight);
                } else {
                    createNewTabInTargetPane("split-view.txt", tabPaneRight);
                }
            }
        } else {
            editorSplitPane.getItems().remove(tabPaneRight);
            tabPaneRight.setVisible(false);
            tabPaneRight.setManaged(false);
        }
    }

    private void setupStatusBar() {
        statusBar.setOnThemeSelected(theme -> {
            themeService.applyTheme(stage.getScene(), theme);
        });
    }

    private void setupCommandPalette() {
        commandPalette.registerCommand("File: New File", "Cmd+N", () -> createNewTab("untitled.txt"));
        commandPalette.registerCommand("File: Open File...", "Cmd+O", this::handleOpenFile);
        commandPalette.registerCommand("File: Save", "Cmd+S", () -> handleSave(false));
        commandPalette.registerCommand("File: Save As...", "Cmd+Shift+S", () -> handleSave(true));
        commandPalette.registerCommand("File: Close Active Tab", "Cmd+W", this::closeActiveTab);
        commandPalette.registerCommand("File: Close All Tabs", "", this::closeAllTabs);
        commandPalette.registerCommand("Edit: Find & Replace", "Cmd+F", () -> handleFind(true));
        commandPalette.registerCommand("View: Toggle Side-by-Side Split Editor", "Cmd+\\", this::toggleSplitEditor);
        commandPalette.registerCommand("View: Toggle AI IDE Copilot", "Cmd+Shift+A", this::toggleAiPanel);
        commandPalette.registerCommand("View: Toggle Explorer", "Cmd+Shift+E", () -> activityBar.setActivePanel(ActivityBar.Panel.EXPLORER));
        commandPalette.registerCommand("View: Toggle Templates", "", () -> activityBar.setActivePanel(ActivityBar.Panel.TEMPLATES));
        commandPalette.registerCommand("View: Toggle Integrated Terminal", "Ctrl+`", this::toggleTerminal);
        commandPalette.registerCommand("Terminal: Create New Terminal", "Ctrl+Shift+`", this::createNewTerminalTab);

        for (ThemeService.Theme theme : ThemeService.Theme.values()) {
            commandPalette.registerCommand("Preferences: Color Theme - " + theme.getDisplayName(), "", () -> {
                themeService.applyTheme(stage.getScene(), theme);
            });
        }
    }

    public EditorTabController createNewTab(String name) {
        return createNewTabInTargetPane(name, tabPaneLeft);
    }

    public EditorTabController createNewTabInTargetPane(String name, TabPane targetPane) {
        EditorTabController tabCtrl = new EditorTabController(name, fileService);
        bindTabController(tabCtrl, targetPane);
        tabControllers.add(tabCtrl);
        targetPane.getTabs().add(tabCtrl.getTab());
        targetPane.getSelectionModel().select(tabCtrl.getTab());
        updateActiveTabMetrics();
        return tabCtrl;
    }

    public void createFromTemplate(Template template, String fileName) {
        List<String> scaffold = template.generateScaffold(fileName);
        EditorTabController tabCtrl = createNewTab(fileName);
        tabCtrl.setContent(String.join("\n", scaffold), true);
    }

    public void openFile(Path path) {
        openFileInTargetTabPane(path, tabPaneLeft);
    }

    public void openFileInTargetTabPane(Path path, TabPane targetPane) {
        if (path == null) return;
        Path absPath = path.toAbsolutePath().normalize();

        for (EditorTabController tc : tabControllers) {
            if (tc.getDocument().getFilePath() != null && tc.getDocument().getFilePath().equals(absPath)) {
                if (tabPaneLeft.getTabs().contains(tc.getTab())) {
                    tabPaneLeft.getSelectionModel().select(tc.getTab());
                } else if (tabPaneRight.getTabs().contains(tc.getTab())) {
                    tabPaneRight.getSelectionModel().select(tc.getTab());
                }
                updateActiveTabMetrics();
                return;
            }
        }

        try {
            if (targetPane.getTabs().size() == 1) {
                Tab initialTab = targetPane.getTabs().get(0);
                EditorTabController initialTc = findTabController(initialTab);
                if (initialTc != null && !initialTc.isModified() && initialTc.getDocument().getFilePath() == null && initialTc.getCharCount() == 0) {
                    targetPane.getTabs().remove(initialTab);
                    tabControllers.remove(initialTc);
                    initialTc.dispose();
                }
            }

            EditorTabController tabCtrl = new EditorTabController(absPath, fileService);
            bindTabController(tabCtrl, targetPane);
            tabControllers.add(tabCtrl);
            targetPane.getTabs().add(tabCtrl.getTab());
            targetPane.getSelectionModel().select(tabCtrl.getTab());
            updateActiveTabMetrics();
        } catch (Exception e) {
            Alert alert = new Alert(Alert.AlertType.ERROR, "Failed to open file:\n" + e.getMessage(), ButtonType.OK);
            alert.showAndWait();
        }
    }

    private EditorTabController findTabController(Tab tab) {
        for (EditorTabController tc : tabControllers) {
            if (tc.getTab() == tab) return tc;
        }
        return null;
    }

    private void bindTabController(EditorTabController tabCtrl, TabPane parentPane) {
        tabCtrl.getTab().setOnCloseRequest(e -> {
            boolean canClose = confirmAndSaveIfModified(tabCtrl);
            if (!canClose) {
                e.consume();
            } else {
                parentPane.getTabs().remove(tabCtrl.getTab());
                tabControllers.remove(tabCtrl);
                tabCtrl.dispose();
                updateActiveTabMetrics();
            }
        });

        tabCtrl.setOnCloseRequested(this::closeTab);
        tabCtrl.setOnCloseOthersRequested(this::closeOtherTabs);
        tabCtrl.setOnCloseAllRequested(this::closeAllTabs);
        tabCtrl.setOnStateChanged(this::updateActiveTabMetrics);
    }

    public void closeActiveTab() {
        EditorTabController current = getActiveTabController();
        if (current != null) {
            closeTab(current);
        }
    }

    public void closeTab(EditorTabController tc) {
        if (tc == null) return;
        boolean canClose = confirmAndSaveIfModified(tc);
        if (canClose) {
            tabPaneLeft.getTabs().remove(tc.getTab());
            tabPaneRight.getTabs().remove(tc.getTab());
            tabControllers.remove(tc);
            tc.dispose();
            updateActiveTabMetrics();
        }
    }

    public void closeOtherTabs(EditorTabController keep) {
        List<EditorTabController> toRemove = new ArrayList<>();
        for (EditorTabController tc : tabControllers) {
            if (tc != keep) {
                toRemove.add(tc);
            }
        }
        for (EditorTabController tc : toRemove) {
            closeTab(tc);
        }
    }

    public void closeAllTabs() {
        List<EditorTabController> toRemove = new ArrayList<>(tabControllers);
        for (EditorTabController tc : toRemove) {
            closeTab(tc);
        }
    }

    private boolean confirmAndSaveIfModified(EditorTabController tabCtrl) {
        if (tabCtrl.isModified()) {
            Alert alert = new Alert(
                    Alert.AlertType.CONFIRMATION,
                    "Save changes to " + tabCtrl.getDocument().getFileName() + " before closing?",
                    ButtonType.YES, ButtonType.NO, ButtonType.CANCEL
            );
            var res = alert.showAndWait();
            if (res.isPresent()) {
                if (res.get() == ButtonType.YES) {
                    return handleSaveTab(tabCtrl, false);
                } else if (res.get() == ButtonType.CANCEL) {
                    return false;
                }
            }
        }
        return true;
    }

    public void updateActiveTabMetrics() {
        EditorTabController current = getActiveTabController();
        if (current == null) {
            statusBar.updatePosition(1, 1);
            statusBar.updateStats(0, 0);
            statusBar.setModified(false);
            statusBar.setLanguage("Plain Text");
            stage.setTitle("Minimal Code Studio (JavaFX 21+)");
            if (aiAssistantPane != null) aiAssistantPane.updateActiveContext("No active file", 0, 0);
            return;
        }

        statusBar.updatePosition(current.getCurrentLine(), current.getCurrentColumn());
        statusBar.updateStats(current.getLineCount(), current.getCharCount());
        statusBar.setModified(current.isModified());
        statusBar.setLanguage(current.getDocument().getFileType().toUpperCase());

        String docName = current.getDocument().getFileName();
        String pathStr = current.getDocument().isPersisted() ? current.getDocument().getFilePath().toString() : "[Unsaved]";
        stage.setTitle((current.isModified() ? "● " : "") + docName + " (" + pathStr + ") — Minimal Code Studio");

        int selectedLength = current.getEditorPane().getCodeArea().getSelectedText().length();
        if (aiAssistantPane != null) {
            aiAssistantPane.updateActiveContext(docName, current.getLineCount(), selectedLength);
        }
    }

    public EditorTabController getActiveTabController() {
        Tab selectedTab = tabPaneLeft.getSelectionModel().getSelectedItem();
        if (selectedTab != null && tabPaneLeft.isFocused()) {
            for (EditorTabController tc : tabControllers) {
                if (tc.getTab() == selectedTab) return tc;
            }
        }

        Tab selectedTabRight = tabPaneRight.getSelectionModel().getSelectedItem();
        if (selectedTabRight != null) {
            for (EditorTabController tc : tabControllers) {
                if (tc.getTab() == selectedTabRight) return tc;
            }
        }

        if (selectedTab != null) {
            for (EditorTabController tc : tabControllers) {
                if (tc.getTab() == selectedTab) return tc;
            }
        }
        return null;
    }

    public void handleOpenFile() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Open File");
        File file = chooser.showOpenDialog(stage);
        if (file != null) {
            openFile(file.toPath());
        }
    }

    public boolean handleSave(boolean forceSaveAs) {
        EditorTabController current = getActiveTabController();
        if (current == null) return false;
        return handleSaveTab(current, forceSaveAs);
    }

    private boolean handleSaveTab(EditorTabController tc, boolean forceSaveAs) {
        File targetFile = null;
        if (tc.getDocument().getFilePath() == null || forceSaveAs) {
            FileChooser chooser = new FileChooser();
            chooser.setTitle("Save File");
            chooser.setInitialFileName(tc.getDocument().getFileName());
            targetFile = chooser.showSaveDialog(stage);
            if (targetFile == null) return false;
        }
        return tc.save(forceSaveAs, targetFile);
    }

    public void handleFind(boolean withReplace) {
        EditorTabController current = getActiveTabController();
        if (current != null) {
            current.getEditorPane().showSearch(withReplace);
        }
    }

    public void showCommandPalette() {
        commandPalette.showPalette();
    }

    public void showThemePicker() {
        ChoiceDialog<String> dialog = new ChoiceDialog<>(
                themeService.getCurrentTheme().getDisplayName(),
                themeService.getAllThemes().keySet()
        );
        dialog.setTitle("Color Themes & Shades");
        dialog.setHeaderText("Choose your preferred aesthetic:");
        dialog.setContentText("Theme:");

        dialog.showAndWait().ifPresent(chosen -> {
            ThemeService.Theme theme = themeService.getAllThemes().get(chosen);
            if (theme != null) {
                themeService.applyTheme(stage.getScene(), theme);
            }
        });
    }

    public void showAboutDialog() {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("About Minimal Code Studio");
        alert.setHeaderText("Minimal Code Studio & AI IDE (OpenJFX 21+)");
        alert.setContentText(
                "Modern, Lightweight, GPU-accelerated Code Editor & AI IDE in Java 21+.\n\n" +
                "Key Features:\n" +
                "• Instant File Explorer & Scaffolding\n" +
                "• Side-by-Side Split Editor (Cmd+\\)\n" +
                "• Integrated AI Copilot (Cmd+Shift+A)\n" +
                "• Integrated Terminal (Ctrl+`)\n" +
                "• File Close (Cmd+W) & Tab Context Menu\n" +
                "• Official VS Code Codicons Vector Icons\n" +
                "• Multi-Theme Styling ('Various Shades')\n" +
                "• Atomic NIO.2 IO & RichTextFX Syntax Highlighting\n\n" +
                "Shortcuts:\n" +
                "• Cmd/Ctrl+W : Close Active Tab\n" +
                "• Cmd/Ctrl+P : Command Palette\n" +
                "• Cmd/Ctrl+\\ : Toggle Split Editor\n" +
                "• Cmd/Ctrl+Shift+A : Toggle AI Copilot\n" +
                "• Ctrl+` : Toggle Terminal\n" +
                "• Ctrl+Shift+` : New Terminal\n" +
                "• Cmd/Ctrl+F : Find & Replace\n" +
                "• Cmd/Ctrl+S : Save File"
        );
        alert.showAndWait();
    }

    public void shutdown() {
        if (terminalPane != null) {
            terminalPane.dispose();
        }
        for (EditorTabController tc : tabControllers) {
            tc.dispose();
        }
        tabControllers.clear();
    }
}
