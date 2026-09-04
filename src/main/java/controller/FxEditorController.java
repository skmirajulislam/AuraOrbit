package controller;

import collaboration.integration.CollaborationController;
import collaboration.ui.HostWorkspaceDialog;
import collaboration.ui.JoinWorkspaceDialog;
import javafx.application.Platform;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import service.FileService;
import service.CodeExecutionService;
import service.ThemeService;
import template.Template;
import view.fx.*;
import org.kordamp.ikonli.codicons.Codicons;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.*;

/**
 * Master JavaFX Controller orchestrating multi-tab documents, sidebars,
 * Split Editor side-by-side view, AI Copilot studio, themes, search, and commands.
 */
public class FxEditorController {

    private final Stage stage;
    private final ThemeService themeService;
    private final FileService fileService;
    private final CodeExecutionService codeExecutionService = new CodeExecutionService();

    private WelcomeWatermarkPane welcomeWatermarkPane;
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
    private ModalOverlayPane modalOverlayPane;
    private CollaborationController collaborationController;
    private boolean applyingRemoteCollaborationChange;
    private Button runButton;
    private HBox runOverlay;

    private final List<EditorTabController> tabControllers = new ArrayList<>();
    private boolean isSplitEditorActive = false;

    private final ScheduledExecutorService diagnosticDebounceExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "Diagnostic-Debounce");
        t.setDaemon(true);
        return t;
    });
    private ScheduledFuture<?> pendingDiagnosticFuture;
    private ScheduledFuture<?> pendingMetricsFuture;
    private ScheduledFuture<?> pendingCollabSnapshotFuture;

    public FxEditorController(Stage stage, ThemeService themeService) {
        this.stage = stage;
        this.themeService = themeService;
        this.fileService = new FileService();
        try {
            this.collaborationController = new CollaborationController("auraorbit-local-session-secret");
            this.collaborationController.setOnRemoteMessage(this::handleCollaborationMessage);
        } catch (Exception exception) {
            System.err.println("Collaboration is unavailable: " + exception.getMessage());
        }
    }

    public void initializeComponents(
            TabPane tabPaneLeft,
            TabPane tabPaneRight,
            SplitPane editorSplitPane,
            WelcomeWatermarkPane welcomeWatermarkPane,
            SplitPane masterSplitPane,
            ActivityBar activityBar,
            SidebarExplorer sidebarExplorer,
            AiAssistantPane aiAssistantPane,
            FxStatusBar statusBar,
            CommandPalette commandPalette,
            TerminalPane terminalPane,
            SplitPane editorTerminalSplitPane,
            ModalOverlayPane modalOverlayPane
    ) {
        this.tabPaneLeft = tabPaneLeft;
        this.tabPaneRight = tabPaneRight;
        this.editorSplitPane = editorSplitPane;
        this.welcomeWatermarkPane = welcomeWatermarkPane;
        this.masterSplitPane = masterSplitPane;
        this.activityBar = activityBar;
        this.sidebarExplorer = sidebarExplorer;
        this.aiAssistantPane = aiAssistantPane;
        this.statusBar = statusBar;
        this.commandPalette = commandPalette;
        this.terminalPane = terminalPane;
        this.editorTerminalSplitPane = editorTerminalSplitPane;
        this.modalOverlayPane = modalOverlayPane;

        setupTabPanes();
        setupSidebar();
        setupActivityBar();
        setupAiAssistant();
        setupTerminal();
        setupStatusBar();
        setupCommandPalette();

        // Create default initial tab
        createNewTab("untitled.txt");

        // Deferred workspace scan — avoid hitching the first paint
        diagnosticDebounceExecutor.schedule(() -> Platform.runLater(() -> {
            if (terminalPane != null) {
                terminalPane.scanWorkspaceForProblems();
            }
        }), 1200, TimeUnit.MILLISECONDS);

        // Initial run button state
        refreshRunAvailability();
    }

    public void setRunButton(Button button) {
        this.runButton = button;
        refreshRunAvailability();
    }

    public void setRunOverlay(HBox runOverlay) {
        this.runOverlay = runOverlay;
        if (runOverlay != null) {
            boolean hasTabs = (tabPaneLeft != null && !tabPaneLeft.getTabs().isEmpty()) ||
                    (isSplitEditorActive && tabPaneRight != null && !tabPaneRight.getTabs().isEmpty());
            runOverlay.setVisible(hasTabs);
            runOverlay.setManaged(hasTabs);
        }
    }

    private void setupTabPanes() {
        tabPaneLeft.getSelectionModel().selectedItemProperty().addListener((obs, oldTab, newTab) -> {
            updateActiveTabMetrics();
            refreshRunAvailability();
        });
        tabPaneRight.getSelectionModel().selectedItemProperty().addListener((obs, oldTab, newTab) -> {
            updateActiveTabMetrics();
            refreshRunAvailability();
        });
    }

    private void setupSidebar() {
        sidebarExplorer.setOnFileSelected(this::openFile);
        sidebarExplorer.setOnTemplateSelected(choice -> {
            if (choice != null && choice.template != null) {
                createFromTemplate(choice.template, choice.defaultName);
            }
        });
        sidebarExplorer.setOnNewFileRequested(() -> createNewTab("untitled.txt"));
        sidebarExplorer.setOnOpenFolderRequested(this::handleOpenFolder);
        sidebarExplorer.setOnWorkspaceChanged(path -> {
            statusBar.updateGitBranch(path);
            if (terminalPane != null) {
                terminalPane.scanWorkspaceForProblems();
            }
            updateActiveTabMetrics();
        });
        sidebarExplorer.setOnFileRenamed((oldPath, newPath) -> {
            for (EditorTabController tabController : tabControllers) {
                if (oldPath.equals(tabController.getDocument().getFilePath())) {
                    tabController.getDocument().setFilePath(newPath);
                    tabController.updateTabTitle();
                }
            }
            updateActiveTabMetrics();
        });
        if (sidebarExplorer.getRootPath() != null) {
            statusBar.updateGitBranch(sidebarExplorer.getRootPath());
        }
    }

    private void setupActivityBar() {
        activityBar.setOnPanelToggled(panel -> {
            if (panel == ActivityBar.Panel.EXPLORER || panel == ActivityBar.Panel.TEMPLATES) {
                sidebarExplorer.showView(panel);
                ensureSidebarVisible(true);
            } else if (panel == ActivityBar.Panel.SEARCH) {
                handleFind(true);
            } else if (panel == ActivityBar.Panel.AI_COPILOT) {
                showAiPanel(true);
            } else if (panel == ActivityBar.Panel.COLLABORATION) {
                showCollaborationOptions();
            } else if (panel == ActivityBar.Panel.TERMINAL) {
                toggleTerminal();
            } else if (panel == null) {
                ensureSidebarVisible(false);
                showAiPanel(false);
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

        aiAssistantPane.setActiveFilePathSupplier(() -> {
            EditorTabController current = getActiveTabController();
            return current != null ? current.getDocument().getFilePath() : null;
        });

        aiAssistantPane.setWorkspacePathSupplier(() -> sidebarExplorer.getRootPath());

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
            if (code == null || code.isBlank()) return;
            if (current == null) {
                current = createNewTab("untitled.java");
            }
            if (current != null) {
                int caret = current.getEditorPane().getCodeArea().getCaretPosition();
                current.getEditorPane().getCodeArea().insertText(caret, code);
            }
        });

        aiAssistantPane.setOnReplaceSelectionInEditor(code -> {
            EditorTabController current = getActiveTabController();
            if (code == null || code.isBlank()) return;
            if (current == null) {
                current = createNewTab("untitled.java");
            }
            if (current != null) {
                current.getEditorPane().getCodeArea().replaceSelection(code);
            }
        });

        aiAssistantPane.setOnCloseRequested(this::toggleAiPanel);

        aiAssistantPane.setOnConfigureApiKeysRequested(() -> {
            if (modalOverlayPane != null) {
                modalOverlayPane.showApiKeyDialog(aiAssistantPane.getAiService(), () -> {
                    modalOverlayPane.showInformation("AI Copilot", "API Keys saved successfully! You can now query your configured models.");
                });
            }
        });
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
        terminalPane.setWorkspaceSupplier(() -> sidebarExplorer.getRootPath());
        terminalPane.setOnCloseRequested(this::toggleTerminal);
        terminalPane.setOnProblemNavigated((filePathOrName, line) -> {
            if (filePathOrName == null || filePathOrName.isBlank()) return;
            Path targetPath = null;
            try {
                Path direct = Paths.get(filePathOrName);
                if (Files.exists(direct)) {
                    targetPath = direct.toAbsolutePath().normalize();
                }
            } catch (Exception ignored) {}

            if (targetPath == null) {
                Path root = sidebarExplorer.getRootPath();
                if (root == null || !Files.exists(root)) {
                    root = Paths.get(".").toAbsolutePath().normalize();
                }
                try {
                    Path candidate = root.resolve(filePathOrName);
                    if (Files.exists(candidate)) {
                        targetPath = candidate.toAbsolutePath().normalize();
                    }
                } catch (Exception ignored) {}

                if (targetPath == null) {
                    try {
                        String nameOnly = Paths.get(filePathOrName).getFileName().toString();
                        try (var stream = Files.walk(root, 10)) {
                            targetPath = stream.filter(Files::isRegularFile)
                                    .filter(p -> p.getFileName().toString().equalsIgnoreCase(nameOnly))
                                    .findFirst()
                                    .map(p -> p.toAbsolutePath().normalize())
                                    .orElse(null);
                        }
                    } catch (Exception ignored) {}
                }
            }

            if (targetPath != null && Files.exists(targetPath)) {
                openFile(targetPath);
                final Path finalTarget = targetPath;
                final int targetLine = line;
                Platform.runLater(() -> {
                    EditorTabController targetCtrl = null;
                    for (EditorTabController tc : tabControllers) {
                        if (tc.getDocument().getFilePath() != null && tc.getDocument().getFilePath().equals(finalTarget)) {
                            targetCtrl = tc;
                            break;
                        }
                    }
                    if (targetCtrl == null) {
                        targetCtrl = getActiveTabController();
                    }
                    if (targetCtrl != null) {
                        final EditorTabController ctrl = targetCtrl;
                        ctrl.navigateToLineAndHighlight(targetLine);
                        Platform.runLater(() -> ctrl.navigateToLineAndHighlight(targetLine));
                    }
                });
            }
        });

        terminalPane.setOnProblemsUpdated((errors, warnings) -> {
            Platform.runLater(() -> statusBar.setProblems(errors, warnings));
        });
    }

    /**
     * Ensure the bottom dock panel is visible and switch to the specified dock tab.
     */
    public void showDockPanel(TerminalPane.DockTab tab) {
        if (!terminalPane.isTerminalVisible()) {
            terminalPane.showTerminal();
            if (!editorTerminalSplitPane.getItems().contains(terminalPane)) {
                editorTerminalSplitPane.getItems().add(terminalPane);
                SplitPane.setResizableWithParent(terminalPane, false);
                editorTerminalSplitPane.setDividerPosition(
                        editorTerminalSplitPane.getItems().size() - 2, 0.7);
            }
        }
        terminalPane.switchDockTab(tab);
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
                SplitPane.setResizableWithParent(terminalPane, false);
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

    public void showAiPanel(boolean show) {
        if (show) {
            aiAssistantPane.setVisible(true);
            aiAssistantPane.setManaged(true);
            if (!masterSplitPane.getItems().contains(aiAssistantPane)) {
                masterSplitPane.getItems().add(aiAssistantPane);
                SplitPane.setResizableWithParent(aiAssistantPane, false);
            }
            Platform.runLater(() -> {
                int divIdx = masterSplitPane.getItems().size() - 2;
                if (divIdx >= 0) {
                    masterSplitPane.setDividerPosition(divIdx, 0.72);
                }
            });
            activityBar.setActivePanel(ActivityBar.Panel.AI_COPILOT, false);
        } else {
            if (masterSplitPane.getItems().contains(aiAssistantPane)) {
                masterSplitPane.getItems().remove(aiAssistantPane);
            }
            aiAssistantPane.setVisible(false);
            aiAssistantPane.setManaged(false);
            if (activityBar.getActivePanel() == ActivityBar.Panel.AI_COPILOT) {
                activityBar.setActivePanel(ActivityBar.Panel.EXPLORER, false);
            }
        }
    }

    public void toggleAiPanel() {
        boolean isCurrentlyOpen = masterSplitPane.getItems().contains(aiAssistantPane) && aiAssistantPane.isVisible();
        showAiPanel(!isCurrentlyOpen);
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
        statusBar.setOnThemePickerRequested(this::showThemePicker);

        statusBar.setOnLineEndingsClicked(() -> {
            EditorTabController current = getActiveTabController();
            if (current != null) {
                current.toggleLineEndings();
                statusBar.setLineEndings(current.getLineEndings());
            }
        });

        statusBar.setOnIndentationClicked(() -> {
            if (modalOverlayPane != null) {
                modalOverlayPane.showOptionSelection(
                        "Indentation", "Select indentation mode:", statusBar.getIndentation(),
                        List.of("Spaces: 2", "Spaces: 4", "Tab Size: 4"), statusBar::setIndentation);
            }
        });

        statusBar.setOnEncodingClicked(() -> {
            if (modalOverlayPane != null) {
                modalOverlayPane.showOptionSelection(
                        "File Encoding", "Select file encoding:", statusBar.getEncoding(),
                        List.of("UTF-8", "UTF-16", "US-ASCII", "ISO-8859-1"), statusBar::setEncoding);
            }
        });

        statusBar.setOnGitBranchClicked(() -> {
            showDockPanel(TerminalPane.DockTab.OUTPUT);
            terminalPane.refreshGitOutput();
        });

        statusBar.setOnProblemsClicked(() -> {
            showDockPanel(TerminalPane.DockTab.PROBLEMS);
            terminalPane.scanWorkspaceForProblems();
        });

        statusBar.setOnBellClicked(() -> {
            showDockPanel(TerminalPane.DockTab.OUTPUT);
            terminalPane.selectOutputChannel("AuraOrbit (System)");
        });
    }

    private void setupCommandPalette() {
        commandPalette.registerCommand("File: New File", "Cmd+N", () -> createNewTab("untitled.txt"));
        commandPalette.registerCommand("File: Open File...", "Cmd+O", this::handleOpenFile);
        commandPalette.registerCommand("File: Open Folder...", "Cmd+K Cmd+O", this::handleOpenFolder);
        commandPalette.registerCommand("File: Save", "Cmd+S", () -> handleSave(false));
        commandPalette.registerCommand("File: Save As...", "Cmd+Shift+S", () -> handleSave(true));
        commandPalette.registerCommand("File: Close Active Tab", "Cmd+W", this::closeActiveTab);
        commandPalette.registerCommand("File: Close All Tabs", "", this::closeAllTabs);
        commandPalette.registerCommand("Workspaces: Close Workspace Folder", "", this::closeWorkspaceFolder);
        commandPalette.registerCommand("Edit: Format Document", "Shift+Alt+F", this::formatActiveDocument);
        commandPalette.registerCommand("Run: Run Active File", "F5", this::runActiveFile);
        commandPalette.registerCommand("Edit: Find & Replace", "Cmd+F", () -> handleFind(true));
        commandPalette.registerCommand("View: Toggle Side-by-Side Split Editor", "Cmd+\\", this::toggleSplitEditor);
        commandPalette.registerCommand("View: Toggle AI IDE Copilot", "Cmd+Shift+A", this::toggleAiPanel);
        commandPalette.registerCommand("AI Copilot: Configure API Keys (GPT, Gemini, Grok)", "", () -> {
            if (modalOverlayPane != null && aiAssistantPane != null) {
                modalOverlayPane.showApiKeyDialog(aiAssistantPane.getAiService(), () -> {
                    modalOverlayPane.showInformation("AI Copilot", "API Keys saved successfully! You can now query your configured models.");
                });
            }
        });
        commandPalette.registerCommand("View: Toggle Explorer", "Cmd+Shift+E", () -> activityBar.setActivePanel(ActivityBar.Panel.EXPLORER));
        commandPalette.registerCommand("View: Toggle Templates", "", () -> activityBar.setActivePanel(ActivityBar.Panel.TEMPLATES));
        commandPalette.registerCommand("View: Toggle Integrated Terminal", "Ctrl+`", this::toggleTerminal);
        commandPalette.registerCommand("Terminal: Create New Terminal", "Ctrl+Shift+`", this::createNewTerminalTab);
        commandPalette.registerCommand("Collaboration: Host Workspace...", "", this::hostCollaborationWorkspace);
        commandPalette.registerCommand("Collaboration: Join Workspace...", "", this::joinCollaborationWorkspace);
        commandPalette.registerCommand("Collaboration: Disconnect", "", this::disconnectCollaborationWorkspace);
        commandPalette.registerCommand("Help: About AuraOrbit", "", this::showAboutDialog);

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
            if (modalOverlayPane != null) {
                modalOverlayPane.showError("Failed to Open File", e.getMessage());
            } else {
                Alert alert = new Alert(Alert.AlertType.ERROR, "Failed to open file:\n" + e.getMessage(), ButtonType.OK);
                alert.showAndWait();
            }
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

        // Immediate lightweight cursor tracking on the status bar (zero string allocations)
        tabCtrl.setOnCursorMoved(tc -> {
            if (tc == getActiveTabController() && statusBar != null) {
                statusBar.updatePosition(tc.getCurrentLine(), tc.getCurrentColumn());
            }
        });

        // Fast typing responsive callback (debounced metrics and diagnostics)
        tabCtrl.setOnTextChanged(tc -> {
            scheduleDebouncedActiveTabMetrics();
            triggerLiveDiagnostics(tc);
            scheduleDebouncedCollaborationSnapshot(tc);
        });

        tabCtrl.setOnStateChanged(() -> {
            updateActiveTabMetrics();
            triggerLiveDiagnostics(tabCtrl);
            scheduleDebouncedCollaborationSnapshot(tabCtrl);
        });
    }

    private void scheduleDebouncedActiveTabMetrics() {
        if (pendingMetricsFuture != null && !pendingMetricsFuture.isDone()) {
            pendingMetricsFuture.cancel(false);
        }
        pendingMetricsFuture = diagnosticDebounceExecutor.schedule(() -> {
            Platform.runLater(this::updateActiveTabMetrics);
        }, 150, TimeUnit.MILLISECONDS);
    }

    private void scheduleDebouncedCollaborationSnapshot(EditorTabController tabCtrl) {
        if (collaborationController == null || !collaborationController.isConnected() || tabCtrl == null) return;
        if (pendingCollabSnapshotFuture != null && !pendingCollabSnapshotFuture.isDone()) {
            pendingCollabSnapshotFuture.cancel(false);
        }
        pendingCollabSnapshotFuture = diagnosticDebounceExecutor.schedule(() -> {
            Platform.runLater(() -> broadcastDocumentSnapshot(tabCtrl));
        }, 300, TimeUnit.MILLISECONDS);
    }

    private void triggerLiveDiagnostics(EditorTabController tc) {
        if (tc == null || tc.getDocument() == null || tc.getDocument().getFilePath() == null) return;
        Path file = tc.getDocument().getFilePath();
        if (pendingDiagnosticFuture != null && !pendingDiagnosticFuture.isDone()) {
            pendingDiagnosticFuture.cancel(false);
        }
        pendingDiagnosticFuture = diagnosticDebounceExecutor.schedule(() -> {
            if (terminalPane != null) {
                terminalPane.updateFileProblems(file);
            }
        }, 500, TimeUnit.MILLISECONDS);
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

    public void forceCloseTab(EditorTabController tc) {
        if (tc == null) return;
        tabPaneLeft.getTabs().remove(tc.getTab());
        tabPaneRight.getTabs().remove(tc.getTab());
        tabControllers.remove(tc);
        tc.dispose();
        updateActiveTabMetrics();
    }

    private boolean confirmAndSaveIfModified(EditorTabController tabCtrl) {
        if (tabCtrl.isModified()) {
            if (modalOverlayPane != null) {
                modalOverlayPane.showConfirmation(
                        "Unsaved Changes",
                        "Save changes to " + tabCtrl.getDocument().getFileName() + " before closing?",
                        "Save & Close",
                        () -> {
                            handleSaveTab(tabCtrl, false);
                            forceCloseTab(tabCtrl);
                        }
                );
                return false;
            } else {
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
        }
        return true;
    }

    public void updateActiveTabMetrics() {
        boolean hasTabs = (tabPaneLeft != null && !tabPaneLeft.getTabs().isEmpty()) ||
                (isSplitEditorActive && tabPaneRight != null && !tabPaneRight.getTabs().isEmpty());

        if (welcomeWatermarkPane != null) {
            welcomeWatermarkPane.setVisible(!hasTabs);
            welcomeWatermarkPane.setManaged(!hasTabs);
        }
        if (editorSplitPane != null) {
            editorSplitPane.setVisible(hasTabs);
            editorSplitPane.setManaged(hasTabs);
        }
        if (runOverlay != null) {
            runOverlay.setVisible(hasTabs);
            runOverlay.setManaged(hasTabs);
        }

        EditorTabController current = getActiveTabController();
        if (current == null) {
            statusBar.updatePosition(1, 1);
            statusBar.updateStats(0, 0);
            statusBar.setModified(false);
            statusBar.setLanguage("Plain Text");
            statusBar.setLineEndings("LF");
            statusBar.setIndentation("Spaces: 4");
            statusBar.setEncoding("UTF-8");
            stage.setTitle("AuraOrbit");
            if (aiAssistantPane != null) aiAssistantPane.updateActiveContext("No active file", 0, 0);
            return;
        }

        statusBar.updatePosition(current.getCurrentLine(), current.getCurrentColumn());
        statusBar.updateStats(current.getLineCount(), current.getCharCount());
        statusBar.setModified(current.isModified());
        statusBar.setLanguage(current.getDocument().getFileType().toUpperCase());
        statusBar.setLineEndings(current.getLineEndings());
        statusBar.setIndentation(current.getIndentation());
        statusBar.setEncoding(current.getEncoding());

        String docName = current.getDocument().getFileName();
        String pathStr = current.getDocument().isPersisted() ? current.getDocument().getFilePath().toString() : "[Unsaved]";
        stage.setTitle((current.isModified() ? "● " : "") + docName + " (" + pathStr + ") — AuraOrbit");

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

    public void handleOpenFolder() {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Open Workspace Folder");
        File folder = chooser.showDialog(stage);
        if (folder != null) {
            sidebarExplorer.setWorkspacePath(folder.toPath());
        }
    }

    public void closeWorkspaceFolder() {
        if (sidebarExplorer != null) {
            sidebarExplorer.closeWorkspaceFolder();
        }
    }

    public void formatActiveDocument() {
        EditorTabController current = getActiveTabController();
        if (current != null && current.getEditorPane() != null) {
            current.getEditorPane().formatCode();
        }
    }

    /** Detects the active file's runtime/compiler and executes it in the built-in terminal. */
    public void runActiveFile() {
        EditorTabController current = getActiveTabController();
        if (current == null) {
            showRunMessage("No file is open to run.");
            return;
        }
        if (current.getDocument().getFilePath() == null) {
            showRunMessage("Save the active file before running it.");
            return;
        }
        if (current.isModified() && !handleSave(false)) {
            return;
        }

        // Ensure terminal is visible and added to the UI before execution
        showDockPanel(TerminalPane.DockTab.TERMINAL);

        Path source = current.getDocument().getFilePath();
        Thread detector = new Thread(() -> {
            CodeExecutionService.ExecutionPlan plan = codeExecutionService.createPlan(source);
            Platform.runLater(() -> {
                if (!plan.isRunnable()) {
                    showRunMessage(plan.message());
                    return;
                }
                terminalPane.executeProgram(plan.steps(), source.getParent(), plan.command());
            });
        }, "runtime-detector");
        detector.setDaemon(true);
        detector.start();
    }

    private void showRunMessage(String message) {
        if (modalOverlayPane != null) {
            modalOverlayPane.showInformation("Run Active File", message);
        } else {
            System.err.println(message);
        }
    }

    private void refreshRunAvailability() {
        if (runButton == null) return;

        EditorTabController current = getActiveTabController();
        boolean canRun = false;
        String missingTool = null;

        if (current != null && current.getDocument().getFilePath() != null) {
            Path source = current.getDocument().getFilePath();
            canRun = codeExecutionService.isRunnable(source);

            if (!canRun) {
                // Check what tool is missing
                String extension = extensionOf(source.getFileName().toString());
                missingTool = switch (extension) {
                    case "java" -> (!codeExecutionService.isToolAvailable("javac") ? "javac" : (!codeExecutionService.isToolAvailable("java") ? "java" : null));
                    case "py" -> (!codeExecutionService.isToolAvailable("python3") && !codeExecutionService.isToolAvailable("python") ? "python3/python" : null);
                    case "js", "mjs", "cjs" -> (!codeExecutionService.isToolAvailable("node") ? "node" : null);
                    case "sh" -> (!codeExecutionService.isToolAvailable("bash") ? "bash" : null);
                    case "rb" -> (!codeExecutionService.isToolAvailable("ruby") ? "ruby" : null);
                    case "c" -> (!codeExecutionService.isToolAvailable("gcc") ? "gcc" : null);
                    case "cpp", "cc", "cxx" -> (!codeExecutionService.isToolAvailable("g++") ? "g++" : null);
                    default -> null;
                };
            }
        }

        final String finalMissingTool = missingTool;
        final boolean finalCanRun = canRun;
        Platform.runLater(() -> {
            runButton.getStyleClass().removeAll("run-ready", "run-blocked");
            if (finalCanRun) {
                runButton.getStyleClass().add("run-ready");
                runButton.setGraphic(IconFactory.getIcon(Codicons.PLAY, 14, "#89d185"));
                runButton.setTextFill(javafx.scene.paint.Color.web("#89d185"));
                runButton.setTooltip(new Tooltip("Run Active File (F5)"));
            } else {
                runButton.getStyleClass().add("run-blocked");
                runButton.setGraphic(IconFactory.getIcon(Codicons.PLAY, 14, "#f14c4c"));
                runButton.setTextFill(javafx.scene.paint.Color.web("#f14c4c"));
                String tooltipText = finalMissingTool != null
                    ? "Install " + finalMissingTool + " to run this file"
                    : "No runner configured for this file type";
                runButton.setTooltip(new Tooltip(tooltipText));
            }
        });
    }

    private String extensionOf(String fileName) {
        int lastDot = fileName.lastIndexOf('.');
        return lastDot > 0 ? fileName.substring(lastDot + 1).toLowerCase(Locale.ROOT) : "";
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
        boolean saved = tc.save(forceSaveAs, targetFile);
        if (saved) {
            if (terminalPane != null) {
                terminalPane.updateFileProblems(tc.getDocument().getFilePath());
            }
        }
        return saved;
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
        if (modalOverlayPane != null) {
            modalOverlayPane.showThemeSelectionDialog(themeService, theme -> {
                themeService.applyTheme(stage.getScene(), theme);
                statusBar.updateThemeDisplay(theme.getDisplayName());
            });
        }
    }

    private void showCollaborationOptions() {
        if (modalOverlayPane == null) {
            hostCollaborationWorkspace();
            return;
        }

        VBox content = new VBox(8);
        Label description = new Label("Share the active document with teammates on your local network. "
                + "Changes are synchronized as complete document updates.");
        description.setWrapText(true);
        description.setStyle("-fx-text-fill: -text-secondary; -fx-font-size: 12px;");
        Label status = new Label(collaborationController != null && collaborationController.isConnected()
                ? "Connected to a collaboration workspace." : "Not connected to a workspace.");
        status.setStyle("-fx-text-fill: -text-primary; -fx-font-size: 12px; -fx-font-weight: bold;");
        content.getChildren().addAll(description, status);

        Button host = modalOverlayPane.createPrimaryButton("Host Workspace", () -> {
            modalOverlayPane.close();
            hostCollaborationWorkspace();
        });
        Button join = modalOverlayPane.createSecondaryButton("Join Workspace", () -> {
            modalOverlayPane.close();
            joinCollaborationWorkspace();
        });
        Button disconnect = modalOverlayPane.createSecondaryButton("Disconnect", this::disconnectCollaborationWorkspace);
        disconnect.setDisable(collaborationController == null || !collaborationController.isConnected());
        modalOverlayPane.showCustom("Live Collaboration", org.kordamp.ikonli.codicons.Codicons.LIVE_SHARE,
                content, join, disconnect, host);
    }

    private void hostCollaborationWorkspace() {
        if (collaborationController == null) {
            showCollaborationError("Collaboration could not be initialized on this device.");
            return;
        }
        HostWorkspaceDialog dialog = new HostWorkspaceDialog();
        dialog.initOwner(stage);
        dialog.showAndWait();
        if (!dialog.isConfirmed()) return;

        try {
            disconnectExistingCollaboration();
            collaborationController.startHostingSession(dialog.getSessionName(), dialog.getServerPort(), "Host");
            EditorTabController active = getActiveTabController();
            if (active != null) broadcastDocumentSnapshot(active);
            showCollaborationInfo("Hosting \"" + dialog.getSessionName() + "\" on port " + dialog.getServerPort()
                    + ". Share your network address, port, and workspace name with collaborators.");
        } catch (Exception exception) {
            showCollaborationError("Unable to host workspace: " + exception.getMessage());
        }
    }

    private void joinCollaborationWorkspace() {
        if (collaborationController == null) {
            showCollaborationError("Collaboration could not be initialized on this device.");
            return;
        }
        JoinWorkspaceDialog dialog = new JoinWorkspaceDialog();
        dialog.initOwner(stage);
        dialog.showAndWait();
        if (!dialog.isConfirmed()) return;

        try {
            disconnectExistingCollaboration();
            collaborationController.joinSession(dialog.getHost(), dialog.getPort(), dialog.getUserName(), dialog.getSessionName());
            collaborationController.broadcastDocument("SYNC_REQUEST");
            showCollaborationInfo("Joined \"" + dialog.getSessionName() + "\". The host's active document will open shortly.");
        } catch (Exception exception) {
            showCollaborationError("Unable to join workspace: " + exception.getMessage());
        }
    }

    private void disconnectCollaborationWorkspace() {
        try {
            disconnectExistingCollaboration();
            if (modalOverlayPane != null) modalOverlayPane.close();
            showCollaborationInfo("Collaboration disconnected.");
        } catch (Exception exception) {
            showCollaborationError("Unable to disconnect: " + exception.getMessage());
        }
    }

    private void disconnectExistingCollaboration() throws Exception {
        if (collaborationController != null && collaborationController.isConnected()) {
            collaborationController.disconnect();
        }
    }

    private void broadcastDocumentSnapshot(EditorTabController tabController) {
        if (applyingRemoteCollaborationChange || collaborationController == null
                || !collaborationController.isConnected() || tabController == null) return;
        String fileName = tabController.getDocument().getFileName();
        String content = tabController.getEditorPane().getCodeArea().getText();
        String encodedName = Base64.getEncoder().encodeToString(fileName.getBytes(StandardCharsets.UTF_8));
        String encodedContent = Base64.getEncoder().encodeToString(content.getBytes(StandardCharsets.UTF_8));
        collaborationController.broadcastDocument("DOC\t" + encodedName + "\t" + encodedContent);
    }

    private void handleCollaborationMessage(String message) {
        if (message == null || message.isBlank()) return;
        Platform.runLater(() -> {
            if ("SYNC_REQUEST".equals(message)) {
                if (collaborationController != null && collaborationController.isHosting()) {
                    broadcastDocumentSnapshot(getActiveTabController());
                }
                return;
            }
            String[] parts = message.split("\\t", 3);
            if (parts.length != 3 || !"DOC".equals(parts[0])) return;
            try {
                String fileName = new String(Base64.getDecoder().decode(parts[1]), StandardCharsets.UTF_8);
                String content = new String(Base64.getDecoder().decode(parts[2]), StandardCharsets.UTF_8);
                EditorTabController target = tabControllers.stream()
                        .filter(tab -> fileName.equals(tab.getDocument().getFileName()))
                        .findFirst().orElseGet(() -> createNewTab(fileName));
                applyingRemoteCollaborationChange = true;
                target.setContent(content, false);
            } catch (IllegalArgumentException ignored) {
                // Invalid messages are ignored rather than rendered as editor content.
            } finally {
                applyingRemoteCollaborationChange = false;
            }
        });
    }

    private void showCollaborationInfo(String message) {
        if (modalOverlayPane != null) modalOverlayPane.showInformation("Live Collaboration", message);
    }

    private void showCollaborationError(String message) {
        if (modalOverlayPane != null) modalOverlayPane.showError("Live Collaboration", message);
    }

    public void showAboutDialog() {
        if (modalOverlayPane != null) {
            modalOverlayPane.showAbout();
        } else {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("About AuraOrbit");
            alert.setHeaderText("AuraOrbit 2.0.0 — Modern Desktop AI Code Studio");
            alert.setContentText("AuraOrbit is a modern desktop AI code studio.");
            alert.showAndWait();
        }
    }

    public void shutdown() {
        diagnosticDebounceExecutor.shutdown();
        try {
            disconnectExistingCollaboration();
        } catch (Exception exception) {
            System.err.println("Could not close collaboration session: " + exception.getMessage());
        }
        if (terminalPane != null) {
            terminalPane.dispose();
        }
        for (EditorTabController tc : tabControllers) {
            tc.dispose();
        }
        tabControllers.clear();
    }
}
