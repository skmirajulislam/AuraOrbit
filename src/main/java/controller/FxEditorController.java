package controller;

import javafx.application.Platform;
import javafx.geometry.Side;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import service.FileService;
import service.CodeExecutionService;
import service.ScriptPluginService;
import service.ThemeService;
import template.Template;
import view.fx.*;
import collaboration.integration.CollaborationManager;
import collaboration.ui.JoinSessionDialog;
import org.kordamp.ikonli.codicons.Codicons;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
    private final CollaborationManager collaborationManager = new CollaborationManager();

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
    private TopCommandCenterBar topCommandCenterBar;
    private SourceControlPane sourceControlPane;
    private WorkspaceSearchPane workspaceSearchPane;
    private Button runButton;
    private HBox runOverlay;

    private final List<Path> navigationHistory = new ArrayList<>();
    private int navigationHistoryIndex = -1;
    private boolean isNavigatingHistory = false;

    private final List<EditorTabController> tabControllers = new ArrayList<>();
    private boolean isSplitEditorActive = false;
    private final java.util.concurrent.atomic.AtomicBoolean isRunningActiveFile = new java.util.concurrent.atomic.AtomicBoolean(false);

    private final ScheduledExecutorService diagnosticDebounceExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "Diagnostic-Debounce");
        t.setDaemon(true);
        return t;
    });
    private ScheduledFuture<?> pendingDiagnosticFuture;
    private ScheduledFuture<?> pendingMetricsFuture;

    public FxEditorController(Stage stage, ThemeService themeService) {
        this.stage = stage;
        this.themeService = themeService;
        this.fileService = new FileService();
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
        if (sidebarExplorer != null) {
            sidebarExplorer.setModalOverlayPane(modalOverlayPane);
        }
        if (terminalPane != null) {
            terminalPane.setModalOverlayPane(modalOverlayPane);
        }

        setupTabPanes();
        setupSidebar();
        setupActivityBar();
        setupAiAssistant();
        setupTerminal();
        setupStatusBar();
        setupCommandPalette();

        // Initialize empty tab metrics and welcome screen state
        updateActiveTabMetrics();

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
        tabPaneLeft.getTabs().addListener((javafx.collections.ListChangeListener<Tab>) c -> {
            updateActiveTabMetrics();
            refreshRunAvailability();
        });
        tabPaneRight.getTabs().addListener((javafx.collections.ListChangeListener<Tab>) c -> {
            updateActiveTabMetrics();
            refreshRunAvailability();
        });
    }

    private void setupSidebar() {
        sidebarExplorer.setOnFileSelectedWithPreview((path, isPreview) -> openFile(path, isPreview));
        sidebarExplorer.setOnFileSelected(path -> openFile(path, true));
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
            if (sourceControlPane != null) {
                sourceControlPane.setWorkspacePath(path);
            }
            if (workspaceSearchPane != null) {
                workspaceSearchPane.setWorkspacePath(path);
            }
            if (topCommandCenterBar != null) {
                topCommandCenterBar.setWorkspaceName(path != null && path.getFileName() != null ? path.getFileName().toString() : "AuraOrbit");
            }
            // Trigger workspace-wide symbol indexing for IntelliSense
            service.AutoCompleteService.scanWorkspaceSymbols(path);
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
            if (sourceControlPane != null) {
                sourceControlPane.setWorkspacePath(sidebarExplorer.getRootPath());
            }
            if (topCommandCenterBar != null) {
                topCommandCenterBar.setWorkspaceName(sidebarExplorer.getRootPath().getFileName().toString());
            }
        }
        sidebarExplorer.setOnCloseAllEditorsRequested(this::closeAllTabs);
    }

    private void setupActivityBar() {
        activityBar.setOnPanelToggled(panel -> {
            if (panel == ActivityBar.Panel.EXPLORER || panel == ActivityBar.Panel.TEMPLATES) {
                sidebarExplorer.showView(panel);
                showPrimarySidebarPane(sidebarExplorer);
            } else if (panel == ActivityBar.Panel.SOURCE_CONTROL) {
                if (sourceControlPane != null) {
                    sourceControlPane.refreshGitStatus();
                    showPrimarySidebarPane(sourceControlPane);
                }
            } else if (panel == ActivityBar.Panel.SEARCH) {
                if (workspaceSearchPane != null) {
                    workspaceSearchPane.setWorkspacePath(sidebarExplorer != null ? sidebarExplorer.getRootPath() : Paths.get(".").toAbsolutePath().normalize());
                    showPrimarySidebarPane(workspaceSearchPane);
                    workspaceSearchPane.focusSearchField();
                } else {
                    handleFind(true);
                }
            } else if (panel == ActivityBar.Panel.AI_COPILOT) {
                showAiPanel(true);
            } else if (panel == ActivityBar.Panel.TERMINAL) {
                toggleTerminal();
            } else if (panel == null) {
                ensureSidebarVisible(false);
                showAiPanel(false);
            }
        });
        activityBar.setOnThemeAction(this::showThemePicker);
        activityBar.setOnInfoAction(this::showAboutDialog);
        activityBar.setOnLiveShareAction(this::showLiveShareOptions);
        activityBar.setOnSettingsAction(this::showSettingsMenu);
        activityBar.setOnAccountAction(this::showAccountMenu);
        collaborationManager.setOnRemoteWorkspaceLoaded(tree -> {
            Platform.runLater(() -> {
                if (modalOverlayPane != null) {
                    modalOverlayPane.showInformation("Live Share Workspace",
                            "Connected to remote workspace: " + (tree != null ? tree.getName() : "Root"));
                }
            });
        });
    }

    public void showPrimarySidebarPane(javafx.scene.Node pane) {
        if (pane == null) return;
        if (masterSplitPane.getItems().isEmpty()) {
            masterSplitPane.getItems().add(0, pane);
            masterSplitPane.setDividerPosition(0, 0.22);
        } else {
            javafx.scene.Node first = masterSplitPane.getItems().get(0);
            if (first != pane) {
                if (first == sidebarExplorer || first == sourceControlPane || first == workspaceSearchPane) {
                    masterSplitPane.getItems().set(0, pane);
                } else {
                    masterSplitPane.getItems().add(0, pane);
                }
                masterSplitPane.setDividerPosition(0, 0.22);
            }
        }
        pane.setVisible(true);
        pane.setManaged(true);
    }

    public void togglePrimarySidebar() {
        boolean isCurrentlyOpen = masterSplitPane.getItems().contains(sidebarExplorer) ||
                (sourceControlPane != null && masterSplitPane.getItems().contains(sourceControlPane)) ||
                (workspaceSearchPane != null && masterSplitPane.getItems().contains(workspaceSearchPane));
        if (isCurrentlyOpen) {
            ensureSidebarVisible(false);
            activityBar.setActivePanel(null, false);
        } else {
            activityBar.setActivePanel(ActivityBar.Panel.EXPLORER, true);
        }
    }

    private void ensureSidebarVisible(boolean visible) {
        if (visible) {
            showPrimarySidebarPane(sidebarExplorer);
        } else {
            if (sidebarExplorer != null) {
                sidebarExplorer.setVisible(false);
                sidebarExplorer.setManaged(false);
                masterSplitPane.getItems().remove(sidebarExplorer);
            }
            if (sourceControlPane != null) {
                sourceControlPane.setVisible(false);
                sourceControlPane.setManaged(false);
                masterSplitPane.getItems().remove(sourceControlPane);
            }
            if (workspaceSearchPane != null) {
                workspaceSearchPane.setVisible(false);
                workspaceSearchPane.setManaged(false);
                masterSplitPane.getItems().remove(workspaceSearchPane);
            }
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
            Platform.runLater(() -> {
                statusBar.setProblems(errors, warnings);
                Map<String, int[]> counts = terminalPane.getProblemCountsByFile();
                if (sidebarExplorer != null) {
                    sidebarExplorer.updateDiagnostics(counts);
                }
                for (EditorTabController tc : tabControllers) {
                    if (tc.getDocument() != null && tc.getDocument().getFilePath() != null) {
                        String p = tc.getDocument().getFilePath().toAbsolutePath().normalize().toString();
                        int[] c = counts.get(p);
                        if (c != null) {
                            tc.setDiagnostics(c[0], c[1]);
                        } else {
                            tc.setDiagnostics(0, 0);
                        }
                    }
                }
                syncOpenEditorsSidebar();
            });
        });
    }

    /**
     * Ensure the bottom dock panel is visible and switch to the specified dock tab.
     */
    public void showDockPanel(TerminalPane.DockTab tab) {
        EditorTabController current = getActiveTabController();
        int topPar = 0;
        if (current != null && current.getEditorPane() != null && !current.getEditorPane().getCodeArea().getVisibleParagraphs().isEmpty()) {
            topPar = current.getEditorPane().getCodeArea().visibleParToAllParIndex(0);
        }

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

        final int restoreTop = topPar;
        Platform.runLater(() -> {
            if (current != null && current.getEditorPane() != null) {
                current.getEditorPane().getCodeArea().showParagraphAtTop(restoreTop);
            }
        });
    }

    /**
     * Toggle integrated terminal panel visibility.
     */
    public void toggleTerminal() {
        EditorTabController current = getActiveTabController();
        int topPar = 0;
        if (current != null && current.getEditorPane() != null && !current.getEditorPane().getCodeArea().getVisibleParagraphs().isEmpty()) {
            topPar = current.getEditorPane().getCodeArea().visibleParToAllParIndex(0);
        }

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

        final int restoreTop = topPar;
        Platform.runLater(() -> {
            if (current != null && current.getEditorPane() != null) {
                current.getEditorPane().getCodeArea().showParagraphAtTop(restoreTop);
            }
        });
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
            splitEditorRight();
        } else {
            editorSplitPane.getItems().remove(tabPaneRight);
            tabPaneRight.setVisible(false);
            tabPaneRight.setManaged(false);
        }
    }

    public void splitEditorRight() {
        splitEditor(javafx.geometry.Orientation.HORIZONTAL, true);
    }

    public void splitEditorLeft() {
        splitEditor(javafx.geometry.Orientation.HORIZONTAL, false);
    }

    public void splitEditorUp() {
        splitEditor(javafx.geometry.Orientation.VERTICAL, false);
    }

    public void splitEditorDown() {
        splitEditor(javafx.geometry.Orientation.VERTICAL, true);
    }

    public void splitEditor(javafx.geometry.Orientation orientation, boolean rightOrDown) {
        if (editorSplitPane == null) return;
        editorSplitPane.setOrientation(orientation);
        isSplitEditorActive = true;

        if (!editorSplitPane.getItems().contains(tabPaneRight)) {
            if (rightOrDown) {
                editorSplitPane.getItems().add(tabPaneRight);
            } else {
                editorSplitPane.getItems().add(0, tabPaneRight);
            }
            editorSplitPane.setDividerPosition(0, 0.5);
        }
        tabPaneRight.setVisible(true);
        tabPaneRight.setManaged(true);

        if (tabPaneLeft != null && tabPaneLeft.getTabs().isEmpty() && tabPaneRight.getTabs().isEmpty()) {
            createNewTab("untitled.txt");
        }

        if (tabPaneRight.getTabs().isEmpty()) {
            EditorTabController current = getActiveTabController();
            if (current != null && current.getDocument().getFilePath() != null) {
                openFileInTargetTabPane(current.getDocument().getFilePath(), tabPaneRight);
            } else {
                createNewTabInTargetPane("split-view.txt", tabPaneRight);
            }
        }
        updateActiveTabMetrics();
    }

    private boolean isGroupLocked = false;

    public void toggleLockGroup() {
        isGroupLocked = !isGroupLocked;
        if (statusBar != null) {
            statusBar.showTemporaryMessage(isGroupLocked ? "Editor Group: Locked" : "Editor Group: Unlocked", 3000);
        }
    }

    public boolean isGroupLocked() {
        return isGroupLocked;
    }

    public void openNewWindow() {
        Platform.runLater(() -> {
            try {
                Stage newStage = new Stage();
                app.JavaFxEditorApp newApp = new app.JavaFxEditorApp();
                newApp.start(newStage);
            } catch (Exception ex) {
                System.err.println("Failed to open new editor window: " + ex.getMessage());
            }
        });
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

        statusBar.setOnPositionClicked(this::showGoToLinePrompt);
        statusBar.setOnLanguageClicked(this::showLanguagePicker);

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
        commandPalette.registerCommand("Search: Find in Files", "Cmd+Shift+F", this::showWorkspaceSearch);
        commandPalette.registerCommand("Search: Replace in Files", "Cmd+Shift+H", this::showWorkspaceSearch);
        commandPalette.registerCommand("View: Show Search", "Cmd+Shift+F", this::showWorkspaceSearch);
        commandPalette.registerCommand("Navigation: Go to Line/Column...", "Cmd+G", this::showGoToLinePrompt);
        commandPalette.registerCommand("Navigation: Go Back", "Ctrl+-", this::navigateBack);
        commandPalette.registerCommand("Navigation: Go Forward", "Ctrl+Shift+-", this::navigateForward);
        commandPalette.registerCommand("View: Toggle Primary Side Bar", "Cmd+B", this::togglePrimarySidebar);
        commandPalette.registerCommand("View: Show Source Control (Git)", "Cmd+Shift+G", () -> activityBar.setActivePanel(ActivityBar.Panel.SOURCE_CONTROL, true));
        commandPalette.registerCommand("View: Show Explorer", "Cmd+Shift+E", () -> activityBar.setActivePanel(ActivityBar.Panel.EXPLORER, true));
        commandPalette.registerCommand("View: Change Language Mode", "", this::showLanguagePicker);
        commandPalette.registerCommand("View: Toggle Side-by-Side Split Editor", "Cmd+\\", this::toggleSplitEditor);
        commandPalette.registerCommand("View: Toggle Word Wrap", "Alt+Z", this::toggleWordWrap);
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
        commandPalette.registerCommand("View: Toggle Minimap", "Alt+M", () -> {
            EditorTabController current = getActiveTabController();
            if (current != null) {
                current.getEditorPane().toggleMinimap();
            }
        });
        commandPalette.registerCommand("View: Toggle Breadcrumbs", "Alt+B", this::toggleBreadcrumbs);
        commandPalette.registerCommand("View: Toggle Tab Orientation (Horizontal / Vertical Side)", "Alt+T", this::toggleTabOrientation);
        commandPalette.registerCommand("Scripts: Run Automation Script...", "", () -> {
            Path ws = sidebarExplorer.getCurrentWorkspacePath() != null ? sidebarExplorer.getCurrentWorkspacePath() : Paths.get(".");
            var scripts = ScriptPluginService.discoverScripts(ws);
            if (scripts.isEmpty()) {
                if (modalOverlayPane != null) {
                    modalOverlayPane.showInformation("Automation Scripts",
                            "No custom scripts found. Place .sh, .py, or .js scripts in ~/.auraorbit/scripts/ or .auraorbit/scripts/ in your workspace.");
                }
                return;
            }
            for (var s : scripts) {
                terminalPane.logOutput("Tasks & Maven", "Executing script: " + s.name() + " (" + s.interpreter() + ")");
                EditorTabController current = getActiveTabController();
                Path activeFile = (current != null && current.getDocument().isPersisted()) ? current.getDocument().getFilePath() : null;
                ScriptPluginService.executeScriptAsync(s, activeFile, ws,
                        line -> terminalPane.logOutput("Tasks & Maven", line),
                        code -> terminalPane.logOutput("Tasks & Maven", "[Process exited with code " + code + "]")
                );
            }
        });
        commandPalette.registerCommand("Help: About AuraOrbit", "", this::showAboutDialog);
        commandPalette.registerCommand("Live Share: Start Collaboration Session (Host)", "", this::startHostingLiveShare);
        commandPalette.registerCommand("Live Share: Join Collaboration Session (Guest)", "", this::showJoinLiveShareDialog);
        commandPalette.registerCommand("Live Share: Stop Active Session", "", () -> collaborationManager.stopSession());

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
        openFile(path, true);
    }

    public void openFile(Path path, boolean isPreview) {
        openFileInTargetTabPane(path, tabPaneLeft, isPreview);
    }

    public void openFileInTargetTabPane(Path path, TabPane targetPane) {
        openFileInTargetTabPane(path, targetPane, false);
    }

    public void openFileInTargetTabPane(Path path, TabPane targetPane, boolean isPreview) {
        if (path == null) return;
        Path absPath = path.toAbsolutePath().normalize();

        for (EditorTabController tc : tabControllers) {
            if (tc.getDocument().getFilePath() != null && tc.getDocument().getFilePath().equals(absPath)) {
                if (tabPaneLeft.getTabs().contains(tc.getTab())) {
                    tabPaneLeft.getSelectionModel().select(tc.getTab());
                } else if (tabPaneRight.getTabs().contains(tc.getTab())) {
                    tabPaneRight.getSelectionModel().select(tc.getTab());
                }
                if (!isPreview) {
                    tc.pin();
                }
                updateActiveTabMetrics();
                return;
            }
        }

        try {
            // If opening in preview mode, reuse existing unpinned and unmodified preview tab in targetPane
            if (isPreview) {
                for (EditorTabController tc : tabControllers) {
                    if (tc.isPreview() && !tc.isModified() && targetPane.getTabs().contains(tc.getTab())) {
                        tc.loadNewFile(absPath);
                        targetPane.getSelectionModel().select(tc.getTab());
                        if (terminalPane != null) {
                            Map<String, int[]> counts = terminalPane.getProblemCountsByFile();
                            int[] diag = counts.get(absPath.toString());
                            if (diag != null) {
                                tc.setDiagnostics(diag[0], diag[1]);
                            } else {
                                tc.setDiagnostics(0, 0);
                            }
                        }
                        updateActiveTabMetrics();
                        triggerLiveDiagnostics(tc);
                        return;
                    }
                }
            }

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
            tabCtrl.setPreview(isPreview);
            if (terminalPane != null) {
                Map<String, int[]> counts = terminalPane.getProblemCountsByFile();
                int[] diag = counts.get(absPath.toString());
                if (diag != null) {
                    tabCtrl.setDiagnostics(diag[0], diag[1]);
                }
            }
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
            e.consume();
            closeTab(tabCtrl);
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
        });

        tabCtrl.setOnStateChanged(() -> {
            updateActiveTabMetrics();
            triggerLiveDiagnostics(tabCtrl);
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
            if (tc.getDocument() != null && tc.getDocument().getFilePath() != null) {
                collaborationManager.unbindEditor(tc.getDocument().getFilePath().toUri().toString());
            }
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
            if (topCommandCenterBar != null) {
                topCommandCenterBar.setActiveFileName("");
            }
            statusBar.updatePosition(1, 1);
            statusBar.updateStats(0, 0);
            statusBar.setModified(false);
            statusBar.setLanguage("Plain Text");
            statusBar.setLineEndings("LF");
            statusBar.setIndentation("Spaces: 4");
            statusBar.setEncoding("UTF-8");
            stage.setTitle("AuraOrbit");
            if (aiAssistantPane != null) aiAssistantPane.updateActiveContext("No active file", 0, 0);
            syncOpenEditorsSidebar();
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
        if (topCommandCenterBar != null) {
            topCommandCenterBar.setActiveFileName(docName);
        }
        if (current.getDocument().getFilePath() != null) {
            recordNavigationHistory(current.getDocument().getFilePath());
        }

        String pathStr = current.getDocument().isPersisted() ? current.getDocument().getFilePath().toString() : "[Unsaved]";
        stage.setTitle((current.isModified() ? "● " : "") + docName + " (" + pathStr + ") — AuraOrbit");

        int selectedLength = current.getEditorPane().getCodeArea().getSelectedText().length();
        if (aiAssistantPane != null) {
            aiAssistantPane.updateActiveContext(docName, current.getLineCount(), selectedLength);
        }

        syncOpenEditorsSidebar();
    }

    private void syncOpenEditorsSidebar() {
        if (sidebarExplorer == null) return;
        EditorTabController active = getActiveTabController();
        List<SidebarExplorer.OpenEditorItem> items = new ArrayList<>();
        for (EditorTabController tc : tabControllers) {
            String title = tc.getDocument().getFileName();
            String path = tc.getDocument().getFilePath() != null ? tc.getDocument().getFilePath().toString() : "";
            boolean isModified = tc.isModified();
            boolean isActive = (tc == active);
            items.add(new SidebarExplorer.OpenEditorItem(
                    title,
                    path,
                    isModified,
                    isActive,
                    tc.isPreview(),
                    tc.getErrorCount(),
                    tc.getWarningCount(),
                    () -> selectTabController(tc),
                    () -> closeTab(tc)
            ));
        }
        sidebarExplorer.setOpenEditors(items);
    }

    private void selectTabController(EditorTabController tc) {
        if (tc == null) return;
        if (tabPaneLeft.getTabs().contains(tc.getTab())) {
            tabPaneLeft.getSelectionModel().select(tc.getTab());
        } else if (tabPaneRight.getTabs().contains(tc.getTab())) {
            tabPaneRight.getSelectionModel().select(tc.getTab());
        }
        updateActiveTabMetrics();
    }

    public void toggleTabOrientation() {
        Side currentSide = tabPaneLeft.getSide();
        Side newSide = (currentSide == Side.TOP) ? Side.LEFT : Side.TOP;
        tabPaneLeft.setSide(newSide);
        if (tabPaneRight != null) {
            tabPaneRight.setSide(newSide);
        }
        if (statusBar != null) {
            statusBar.showTemporaryMessage("Tabs position: " + (newSide == Side.LEFT ? "Vertical Side" : "Horizontal Top"), 3000);
        }
    }

    public void toggleBreadcrumbs() {
        EditorTabController current = getActiveTabController();
        if (current != null && current.getEditorPane() != null) {
            current.getEditorPane().toggleBreadcrumbs();
            boolean isVisible = current.getEditorPane().isBreadcrumbsVisible();
            if (statusBar != null) {
                statusBar.showTemporaryMessage("Breadcrumbs " + (isVisible ? "enabled" : "hidden"), 2500);
            }
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
            openFile(file.toPath(), false);
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

    public void toggleWordWrap() {
        EditorTabController current = getActiveTabController();
        if (current != null && current.getEditorPane() != null) {
            org.fxmisc.richtext.CodeArea ca = current.getEditorPane().getCodeArea();
            ca.setWrapText(!ca.isWrapText());
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

        // Prevent rapid double-click race conditions
        if (!isRunningActiveFile.compareAndSet(false, true)) {
            return;
        }

        // Ensure terminal is visible and added to the UI before execution
        showDockPanel(TerminalPane.DockTab.TERMINAL);

        Path source = current.getDocument().getFilePath();
        Thread detector = new Thread(() -> {
            CodeExecutionService.ExecutionPlan plan = codeExecutionService.createPlan(source);
            Platform.runLater(() -> {
                if (!plan.isRunnable()) {
                    isRunningActiveFile.set(false);
                    showRunMessage(plan.message());
                    return;
                }
                terminalPane.executeProgram(
                        plan.steps(),
                        source.getParent(),
                        plan.command(),
                        () -> {
                            try {
                                if (plan.cleanupHook() != null) {
                                    plan.cleanupHook().run();
                                }
                            } finally {
                                isRunningActiveFile.set(false);
                            }
                        },
                        dirPath -> {
                            if (sidebarExplorer != null && dirPath != null) {
                                sidebarExplorer.refreshPath(dirPath);
                            }
                        }
                );
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

    public void showLiveShareOptions() {
        if (collaborationManager.isHosting() || collaborationManager.isGuest()) {
            ContextMenu menu = new ContextMenu();
            MenuItem statusItem = new MenuItem("Session Active (" + (collaborationManager.isHosting() ? "Host" : "Guest") + ")");
            statusItem.setDisable(true);
            MenuItem stopItem = new MenuItem("End Collaboration Session");
            stopItem.setOnAction(e -> collaborationManager.stopSession());
            menu.getItems().addAll(statusItem, new SeparatorMenuItem(), stopItem);
            menu.show(stage, stage.getX() + 50, stage.getY() + 300);
        } else {
            ContextMenu menu = new ContextMenu();
            MenuItem hostItem = new MenuItem("Host Collaboration Session (Cloudflare Quick Tunnel)...");
            hostItem.setOnAction(e -> startHostingLiveShare());
            MenuItem joinItem = new MenuItem("Join Collaboration Session (Invite Link)...");
            joinItem.setOnAction(e -> showJoinLiveShareDialog());
            menu.getItems().addAll(hostItem, joinItem);
            menu.show(stage, stage.getX() + 50, stage.getY() + 300);
        }
    }

    public void startHostingLiveShare() {
        File ws = (sidebarExplorer != null && sidebarExplorer.getCurrentWorkspacePath() != null)
                ? sidebarExplorer.getCurrentWorkspacePath().toFile()
                : new File(".");
        collaborationManager.startHosting(ws, stage);
    }

    public void showJoinLiveShareDialog() {
        JoinSessionDialog dialog = new JoinSessionDialog(stage, (url, name) -> {
            collaborationManager.joinSession(url, name, stage);
        });
        dialog.show();
    }

    public void setTopCommandCenterBar(TopCommandCenterBar topCommandCenterBar) {
        this.topCommandCenterBar = topCommandCenterBar;
        if (topCommandCenterBar != null) {
            topCommandCenterBar.setOnBackAction(this::navigateBack);
            topCommandCenterBar.setOnForwardAction(this::navigateForward);
            topCommandCenterBar.setOnSearchAction(this::showCommandPalette);
            topCommandCenterBar.setOnToggleSidebarAction(this::togglePrimarySidebar);
            topCommandCenterBar.setOnTogglePanelAction(this::toggleTerminal);
            topCommandCenterBar.setOnToggleAiAction(this::toggleAiPanel);
            if (sidebarExplorer != null && sidebarExplorer.getRootPath() != null) {
                topCommandCenterBar.setWorkspaceName(sidebarExplorer.getRootPath().getFileName().toString());
            }
            updateActiveTabMetrics();
        }
    }

    public void setSourceControlPane(SourceControlPane sourceControlPane) {
        this.sourceControlPane = sourceControlPane;
        if (sourceControlPane != null) {
            if (sidebarExplorer != null && sidebarExplorer.getRootPath() != null) {
                sourceControlPane.setWorkspacePath(sidebarExplorer.getRootPath());
            }
            sourceControlPane.setOnOpenFileRequested(path -> openFile(path, true));
            sourceControlPane.setOnBadgeCountChanged(count -> {
                if (activityBar != null) {
                    activityBar.setSourceControlBadge(count);
                }
            });
            sourceControlPane.setOnNotification(msg -> {
                if (statusBar != null) {
                    statusBar.showTemporaryMessage(msg, 3000);
                }
            });
        }
    }

    public void setWorkspaceSearchPane(WorkspaceSearchPane workspaceSearchPane) {
        this.workspaceSearchPane = workspaceSearchPane;
        if (workspaceSearchPane != null) {
            if (sidebarExplorer != null && sidebarExplorer.getRootPath() != null) {
                workspaceSearchPane.setWorkspacePath(sidebarExplorer.getRootPath());
            }
            workspaceSearchPane.setOnNavigateToFileAndLine((path, line) -> {
                openFile(path, true);
                Platform.runLater(() -> {
                    EditorTabController tc = getActiveTabController();
                    if (tc != null) {
                        tc.getEditorPane().getCodeArea().showParagraphAtCenter(Math.max(0, line - 1));
                        tc.getEditorPane().getCodeArea().moveTo(line - 1, 0);
                    }
                });
            });
            workspaceSearchPane.setOnNotification(msg -> {
                if (statusBar != null) {
                    statusBar.showTemporaryMessage(msg, 3000);
                }
            });
        }
    }

    public void showWorkspaceSearch() {
        if (activityBar != null) {
            activityBar.setActivePanel(ActivityBar.Panel.SEARCH, true);
        }
    }

    private void recordNavigationHistory(Path path) {
        if (path == null || isNavigatingHistory) return;
        if (navigationHistoryIndex >= 0 && navigationHistoryIndex < navigationHistory.size() &&
                navigationHistory.get(navigationHistoryIndex).equals(path)) {
            return;
        }
        while (navigationHistory.size() > navigationHistoryIndex + 1) {
            navigationHistory.remove(navigationHistory.size() - 1);
        }
        navigationHistory.add(path);
        while (navigationHistory.size() > 100) {
            navigationHistory.remove(0);
        }
        navigationHistoryIndex = navigationHistory.size() - 1;
        updateNavigationButtons();
    }

    public void navigateBack() {
        if (navigationHistoryIndex > 0) {
            navigationHistoryIndex--;
            isNavigatingHistory = true;
            try {
                openFile(navigationHistory.get(navigationHistoryIndex), true);
            } finally {
                isNavigatingHistory = false;
                updateNavigationButtons();
            }
        }
    }

    public void navigateForward() {
        if (navigationHistoryIndex < navigationHistory.size() - 1) {
            navigationHistoryIndex++;
            isNavigatingHistory = true;
            try {
                openFile(navigationHistory.get(navigationHistoryIndex), true);
            } finally {
                isNavigatingHistory = false;
                updateNavigationButtons();
            }
        }
    }

    private void updateNavigationButtons() {
        if (topCommandCenterBar != null) {
            boolean canBack = navigationHistoryIndex > 0;
            boolean canForward = navigationHistoryIndex < navigationHistory.size() - 1;
            topCommandCenterBar.setNavigationState(canBack, canForward);
        }
    }

    public void showSettingsMenu() {
        if (modalOverlayPane != null) {
            modalOverlayPane.showOptionSelection(
                    "Manage AuraOrbit Preferences",
                    "Choose a preferences category or quick action:",
                    "Command Palette (Cmd+P)",
                    List.of(
                            "Command Palette (Cmd+P)",
                            "Color Theme Picker",
                            "Configure AI Copilot API Keys",
                            "Keyboard Shortcuts & Actions",
                            "Toggle Integrated Terminal (Ctrl+`)",
                            "About AuraOrbit"
                    ),
                    choice -> {
                        if (choice.contains("Command Palette")) showCommandPalette();
                        else if (choice.contains("Color Theme")) showThemePicker();
                        else if (choice.contains("AI Copilot")) configureAiKeys();
                        else if (choice.contains("Shortcuts")) showKeyboardShortcutsDialog();
                        else if (choice.contains("Terminal")) toggleTerminal();
                        else if (choice.contains("About")) showAboutDialog();
                    }
            );
        }
    }

    public void showAccountMenu() {
        if (modalOverlayPane != null) {
            String liveShareStatus = collaborationManager.isHosting()
                    ? "Hosting Active Live Share Session"
                    : collaborationManager.isConnected()
                    ? "Connected to Remote Live Share"
                    : "Collaboration Offline";
            String wsName = (sidebarExplorer != null && sidebarExplorer.getRootPath() != null)
                    ? sidebarExplorer.getRootPath().getFileName().toString()
                    : "None";

            modalOverlayPane.showInformation(
                    "User & Account Profile",
                    "User: " + System.getProperty("user.name") + "\n" +
                    "Live Share Status: " + liveShareStatus + "\n" +
                    "Active Workspace: " + wsName + "\n\n" +
                    "AuraOrbit Pro Edition \u2014 Zero-config collaboration, Git integration & AI Studio."
            );
        }
    }

    public void showGoToLinePrompt() {
        EditorTabController current = getActiveTabController();
        if (current == null) return;
        if (modalOverlayPane != null) {
            modalOverlayPane.showTextInput(
                    "Go to Line",
                    "Type line number to jump to (1 - " + current.getLineCount() + "):",
                    String.valueOf(current.getCurrentLine()),
                    input -> {
                        if (input == null || input.isBlank()) return;
                        try {
                            int line = Integer.parseInt(input.trim().split("[:;,]")[0]);
                            current.navigateToLineAndHighlight(line);
                            updateActiveTabMetrics();
                        } catch (NumberFormatException ignored) {}
                    }
            );
        }
    }

    public void showLanguagePicker() {
        EditorTabController current = getActiveTabController();
        if (current == null) return;
        List<String> languages = List.of(
                "Java", "Python", "JavaScript", "HTML", "CSS", "JSON", "XML", "YAML", "Markdown", "Plain Text"
        );
        if (modalOverlayPane != null) {
            modalOverlayPane.showOptionSelection(
                    "Select Language Mode",
                    "Choose syntax and diagnostic language for " + current.getDocument().getFileName() + ":",
                    current.getDocument().getFileType().toUpperCase(),
                    languages,
                    choice -> {
                        if (choice != null) {
                            current.getEditorPane().setFileType(choice.toLowerCase());
                            statusBar.setLanguage(choice);
                            triggerLiveDiagnostics(current);
                        }
                    }
            );
        }
    }

    public void showKeyboardShortcutsDialog() {
        if (modalOverlayPane != null) {
            modalOverlayPane.showInformation("Keyboard Shortcuts",
                    "Cmd/Ctrl+P : Command Palette / Quick Open\n" +
                    "Cmd/Ctrl+S : Save File\n" +
                    "Cmd/Ctrl+N : New File\n" +
                    "Cmd/Ctrl+O : Open File\n" +
                    "Cmd/Ctrl+W : Close Tab\n" +
                    "Cmd/Ctrl+B : Toggle Primary Side Bar\n" +
                    "Ctrl+` : Toggle Terminal Panel\n" +
                    "Cmd/Ctrl+Shift+A : Toggle AI Copilot Studio\n" +
                    "Cmd/Ctrl+Shift+G : Source Control Panel\n" +
                    "Cmd/Ctrl+Shift+E : Explorer Panel\n" +
                    "Cmd/Ctrl+G : Go to Line\n" +
                    "Shift+Alt+F : Format Document\n" +
                    "F5 : Run Active File\n" +
                    "Ctrl+- / Ctrl+Shift+- : Navigate Back / Forward");
        }
    }

    public void configureAiKeys() {
        if (modalOverlayPane != null && aiAssistantPane != null) {
            modalOverlayPane.showApiKeyDialog(aiAssistantPane.getAiService(), () -> {
                modalOverlayPane.showInformation("AI Copilot", "API Keys saved successfully! You can now query your configured models.");
            });
        }
    }

    public void shutdown() {
        diagnosticDebounceExecutor.shutdownNow();
        collaborationManager.stopSession();
        if (workspaceSearchPane != null) {
            workspaceSearchPane.shutdown();
        }
        if (sourceControlPane != null) {
            sourceControlPane.shutdown();
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
