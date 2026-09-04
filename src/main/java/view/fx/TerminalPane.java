package view.fx;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.*;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import org.kordamp.ikonli.codicons.Codicons;
import org.kordamp.ikonli.javafx.FontIcon;
import service.CodeDiagnosticsService;

import java.awt.Desktop;
import java.io.*;
import java.lang.management.ManagementFactory;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

/**
 * VS Code-Identical Integrated Dock & Terminal Panel for AuraOrbit.
 * Features:
 * - Direct shell prompt rendered at the top of the terminal canvas, flowing downwards.
 * - Multi-session terminal with numbering (1: zsh, 2: zsh), session switcher menu, split view.
 * - Single-instance deletion: clicking delete on the only/last shell immediately closes the bottom panel tab.
 * - Functional PROBLEMS tab with diagnostics list, workspace scanner, and editor navigation.
 * - Functional OUTPUT tab with multi-channel streaming (AuraOrbit System, Tasks & Maven, Git, AI Copilot) and Maven build runner.
 * - Functional DEBUG CONSOLE with interactive REPL prompt, command history, and commands (help, mem, gc, threads, sys, eval).
 * - Functional PORTS tab with live TCP localhost port probe (detecting listening dev servers), port forwarding, and browser launcher.
 * - Cross-platform compatibility for macOS, Linux, and Windows.
 */
public class TerminalPane extends BorderPane {

    public enum PlatformOS {
        MAC,
        LINUX,
        WINDOWS
    }

    public static PlatformOS getPlatformOS() {
        String os = System.getProperty("os.name", "").toLowerCase();
        if (os.contains("win")) return PlatformOS.WINDOWS;
        if (os.contains("mac")) return PlatformOS.MAC;
        return PlatformOS.LINUX;
    }

    public enum DockTab {
        PROBLEMS,
        OUTPUT,
        DEBUG_CONSOLE,
        TERMINAL,
        PORTS
    }

    // ─────────────────────────────────────────────────────────────────────────
    // UI Layout Components
    // ─────────────────────────────────────────────────────────────────────────
    private final StackPane contentStack;
    private final VBox terminalContainer;
    private final VBox problemsView;
    private final VBox outputView;
    private final VBox debugView;
    private final VBox portsView;

    // Header Components
    private HBox dockHeader;
    private Label problemsTabBtn;
    private Label outputTabBtn;
    private Label debugTabBtn;
    private Label terminalTabBtn;
    private Label portsTabBtn;
    private HBox contextualActionsBox;

    // Terminal Session Management
    private final List<TerminalSession> sessions = new ArrayList<>();
    private TerminalSession activeSession;
    private int terminalCounter = 0;
    private Label sessionChip;
    private SplitPane splitTerminalPane;
    private boolean isSplit = false;

    // Background Threading
    private final ExecutorService ioExecutor;

    // Callbacks & Suppliers
    private Supplier<Path> workingDirectorySupplier;
    private Supplier<Path> workspaceSupplier;
    private Runnable onCloseRequested;
    private BiConsumer<String, Integer> onProblemNavigated;
    private BiConsumer<Integer, Integer> onProblemsUpdated;

    // Dock Tab State
    private DockTab currentTab = DockTab.TERMINAL;
    public DockTab getCurrentTab() { return currentTab; }

    // PROBLEMS Tab State
    public record ProblemItem(Codicons icon, String severity, String message, String file, int line, int column, String source) {}
    private final ObservableList<ProblemItem> problemItems = FXCollections.observableArrayList();
    private final ObservableList<ProblemItem> filteredProblemItems = FXCollections.observableArrayList();
    private VBox problemListContainer;
    private VBox problemEmptyState;
    private Label errorCountBadge;
    private Label warningCountBadge;
    private Label infoCountBadge;
    private TextField problemFilterField;

    // OUTPUT Tab State
    private final Map<String, TextArea> outputChannels = new LinkedHashMap<>();
    private ComboBox<String> outputChannelCombo;
    private StackPane outputAreaStack;
    private final java.util.concurrent.atomic.AtomicBoolean isGitQueryRunning = new java.util.concurrent.atomic.AtomicBoolean(false);

    // DEBUG CONSOLE Tab State
    private VBox debugHistoryBox;
    private ScrollPane debugScrollPane;
    private TextField debugInputField;
    private final List<String> debugCommandHistory = new ArrayList<>();
    private int debugHistoryIndex = -1;

    // PORTS Tab State
    public static class PortEntry {
        public final int port;
        public String protocol;
        public boolean listening;
        public String processName;
        public long pid;
        public String localAddress;
        public boolean isCustom;

        public PortEntry(int port, String protocol, boolean listening, String processName, long pid, boolean isCustom) {
            this.port = port;
            this.protocol = protocol;
            this.listening = listening;
            this.processName = (processName != null && !processName.isEmpty()) ? processName : "Unknown Process";
            this.pid = pid;
            this.isCustom = isCustom;
            this.localAddress = "http://localhost:" + port;
        }

        public String getLabel() {
            return processName;
        }
    }
    private final ObservableList<PortEntry> portEntries = FXCollections.observableArrayList();
    private final ObservableList<PortEntry> filteredPortEntries = FXCollections.observableArrayList();
    private final Set<Integer> userTrackedPorts = new LinkedHashSet<>();
    private VBox portsTableContainer;
    private VBox portsEmptyState;
    private TextField portFilterField;
    private ModalOverlayPane modalOverlayPane;

    public void setModalOverlayPane(ModalOverlayPane modalOverlayPane) {
        this.modalOverlayPane = modalOverlayPane;
    }
    private Label portCountBadge;
    private boolean portsScannedOnce;

    public TerminalPane() {
        getStyleClass().add("terminal-pane");

        ThreadFactory daemonFactory = r -> {
            Thread t = new Thread(r);
            t.setDaemon(true);
            t.setName("auraorbit-dock-" + t.threadId());
            return t;
        };
        ioExecutor = Executors.newCachedThreadPool(daemonFactory);

        // 1. VS Code Bottom Dock Header
        dockHeader = buildDockHeader();
        setTop(dockHeader);

        // 2. Build Views for all dock tabs
        terminalContainer = new VBox();
        VBox.setVgrow(terminalContainer, Priority.ALWAYS);

        problemsView = buildProblemsView();
        outputView = buildOutputView();
        debugView = buildDebugView();
        portsView = buildPortsView();

        contentStack = new StackPane(terminalContainer, problemsView, outputView, debugView, portsView);
        VBox.setVgrow(contentStack, Priority.ALWAYS);
        setCenter(contentStack);

        setVisible(false);
        setManaged(false);

        // Initial tab
        switchDockTab(DockTab.TERMINAL);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Header & Tab Switching
    // ─────────────────────────────────────────────────────────────────────────
    private HBox buildDockHeader() {
        HBox header = new HBox(8);
        header.getStyleClass().add("terminal-header");
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(4, 10, 4, 10));

        problemsTabBtn = createTabButton("PROBLEMS", "0", DockTab.PROBLEMS);
        outputTabBtn = createTabButton("OUTPUT", null, DockTab.OUTPUT);
        debugTabBtn = createTabButton("DEBUG CONSOLE", null, DockTab.DEBUG_CONSOLE);
        terminalTabBtn = createTabButton("TERMINAL", null, DockTab.TERMINAL);
        portsTabBtn = createTabButton("PORTS", null, DockTab.PORTS);

        HBox dockSegment = new HBox(2);
        dockSegment.getStyleClass().add("dock-segment");
        dockSegment.setAlignment(Pos.CENTER_LEFT);
        dockSegment.getChildren().addAll(
                problemsTabBtn, outputTabBtn, debugTabBtn, terminalTabBtn, portsTabBtn);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        contextualActionsBox = new HBox(6);
        contextualActionsBox.setAlignment(Pos.CENTER_RIGHT);

        header.getChildren().addAll(dockSegment, spacer, contextualActionsBox);
        return header;
    }

    private Label createTabButton(String text, String badge, DockTab tab) {
        Label btn = new Label(badge != null ? text + " " + badge : text);
        btn.getStyleClass().add("dock-tab-btn");
        btn.setOnMouseClicked(e -> {
            if (tab == DockTab.TERMINAL && sessions.isEmpty()) {
                createNewTerminal();
            } else {
                switchDockTab(tab);
            }
        });
        return btn;
    }

    public void switchDockTab(DockTab tab) {
        currentTab = tab;

        problemsTabBtn.getStyleClass().remove("active");
        outputTabBtn.getStyleClass().remove("active");
        debugTabBtn.getStyleClass().remove("active");
        terminalTabBtn.getStyleClass().remove("active");
        portsTabBtn.getStyleClass().remove("active");

        problemsView.setVisible(false);
        outputView.setVisible(false);
        debugView.setVisible(false);
        terminalContainer.setVisible(false);
        portsView.setVisible(false);

        updateContextualToolbar(tab);

        switch (tab) {
            case PROBLEMS -> {
                problemsTabBtn.getStyleClass().add("active");
                problemsView.setVisible(true);
            }
            case OUTPUT -> {
                outputTabBtn.getStyleClass().add("active");
                outputView.setVisible(true);
            }
            case DEBUG_CONSOLE -> {
                debugTabBtn.getStyleClass().add("active");
                debugView.setVisible(true);
                Platform.runLater(debugInputField::requestFocus);
            }
            case TERMINAL -> {
                terminalTabBtn.getStyleClass().add("active");
                terminalContainer.setVisible(true);
                if (activeSession != null) {
                    Platform.runLater(activeSession::focusInput);
                }
            }
            case PORTS -> {
                portsTabBtn.getStyleClass().add("active");
                portsView.setVisible(true);
                if (!portsScannedOnce) {
                    portsScannedOnce = true;
                    scanLocalPorts();
                }
            }
        }
    }

    private void updateContextualToolbar(DockTab tab) {
        contextualActionsBox.getChildren().clear();

        Button closeBtn = createHeaderButton(Codicons.CHEVRON_DOWN, "Hide Panel (Ctrl+`)", () -> {
            if (onCloseRequested != null) onCloseRequested.run();
        });

        switch (tab) {
            case TERMINAL -> {
                if (sessionChip == null) {
                    sessionChip = new Label(">_ 1: " + getShellName() + " ▾");
                    sessionChip.getStyleClass().add("terminal-session-chip");
                    sessionChip.setOnMouseClicked(e -> showSessionSwitcherMenu());
                }
                updateSessionChip();

                Button newTermBtn = createHeaderButton(Codicons.ADD, "New Terminal (Ctrl+Shift+`)", this::createNewTerminal);
                Button splitBtn = createHeaderButton(Codicons.SPLIT_HORIZONTAL, "Split Terminal", this::toggleSplitTerminal);
                Button killBtn = createHeaderButton(Codicons.TRASH, "Kill Active Terminal", this::killActiveTerminal);
                Button clearBtn = createHeaderButton(Codicons.CLEAR_ALL, "Clear Terminal (Ctrl+L)", this::clearActiveTerminal);

                contextualActionsBox.getChildren().addAll(sessionChip, newTermBtn, splitBtn, killBtn, clearBtn, closeBtn);
            }
            case PROBLEMS -> {
                Button scanBtn = createHeaderButton(Codicons.REFRESH, "Scan Workspace for Problems", this::scanWorkspaceForProblems);
                contextualActionsBox.getChildren().addAll(scanBtn, closeBtn);
            }
            case OUTPUT -> {
                Button runMvnBtn = createHeaderButton(Codicons.PLAY, "Run Maven compile", this::runMavenBuild);
                Button clearBtn = createHeaderButton(Codicons.CLEAR_ALL, "Clear Output", this::clearActiveOutput);
                Button copyBtn = createHeaderButton(Codicons.CLIPPY, "Copy Output to Clipboard", this::copyActiveOutput);
                contextualActionsBox.getChildren().addAll(outputChannelCombo, runMvnBtn, clearBtn, copyBtn, closeBtn);
            }
            case DEBUG_CONSOLE -> {
                Button clearBtn = createHeaderButton(Codicons.CLEAR_ALL, "Clear Debug Console", this::clearDebugConsole);
                contextualActionsBox.getChildren().addAll(clearBtn, closeBtn);
            }
            case PORTS -> {
                Button scanPortsBtn = createHeaderButton(Codicons.REFRESH, "Scan Localhost Ports", this::scanLocalPorts);
                Button addPortBtn = createHeaderButton(Codicons.ADD, "Forward / Add Port", this::showAddPortDialog);
                contextualActionsBox.getChildren().addAll(scanPortsBtn, addPortBtn, closeBtn);
            }
        }
    }

    private Button createHeaderButton(Codicons icon, String tooltip, Runnable action) {
        Button btn = new Button();
        btn.setGraphic(IconFactory.getIcon(icon, 13));
        btn.getStyleClass().add("terminal-header-btn");
        btn.setTooltip(new Tooltip(tooltip));
        btn.setOnAction(e -> {
            if (action != null) action.run();
        });
        return btn;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // TAB 1: TERMINAL & SESSIONS (Interactive CLI at Row 0, Multi-terminal, Kill)
    // ─────────────────────────────────────────────────────────────────────────
    public void createNewTerminal() {
        terminalCounter++;
        File wd = getWorkingDirectory();
        TerminalSession session = new TerminalSession(terminalCounter, wd);
        sessions.add(session);
        activeSession = session;

        switchDockTab(DockTab.TERMINAL);
        renderTerminalView();
        updateSessionChip();

        session.start();
        Platform.runLater(session::focusInput);
    }

    private void renderTerminalView() {
        terminalContainer.getChildren().clear();
        if (isSplit && sessions.size() >= 2) {
            if (splitTerminalPane == null) {
                splitTerminalPane = new SplitPane();
                splitTerminalPane.getStyleClass().add("terminal-split-pane");
            }
            splitTerminalPane.getItems().clear();
            TerminalSession s1 = sessions.get(sessions.size() - 2);
            TerminalSession s2 = sessions.get(sessions.size() - 1);
            splitTerminalPane.getItems().addAll(s1.getNode(), s2.getNode());
            splitTerminalPane.setDividerPositions(0.5);
            VBox.setVgrow(splitTerminalPane, Priority.ALWAYS);
            terminalContainer.getChildren().add(splitTerminalPane);
        } else {
            isSplit = false;
            if (activeSession != null) {
                terminalContainer.getChildren().add(activeSession.getNode());
                VBox.setVgrow(activeSession.getNode(), Priority.ALWAYS);
            }
        }
    }

    private void toggleSplitTerminal() {
        if (sessions.isEmpty()) {
            createNewTerminal();
            return;
        }
        if (sessions.size() == 1) {
            // Create a second terminal session to split
            terminalCounter++;
            File wd = getWorkingDirectory();
            TerminalSession second = new TerminalSession(terminalCounter, wd);
            sessions.add(second);
            activeSession = second;
            second.start();
        }
        isSplit = !isSplit;
        renderTerminalView();
        updateSessionChip();
        if (activeSession != null) {
            Platform.runLater(activeSession::focusInput);
        }
    }

    public void killActiveTerminal() {
        if (activeSession != null) {
            TerminalSession toKill = activeSession;
            toKill.destroy();
            sessions.remove(toKill);

            if (sessions.isEmpty()) {
                activeSession = null;
                terminalContainer.getChildren().clear();
                isSplit = false;
                updateSessionChip();
                // Critical requirement: If single shell instance was deleted, close the dock tab!
                if (onCloseRequested != null) {
                    onCloseRequested.run();
                }
            } else {
                activeSession = sessions.get(sessions.size() - 1);
                renderTerminalView();
                updateSessionChip();
                Platform.runLater(activeSession::focusInput);
            }
        } else if (sessions.isEmpty()) {
            if (onCloseRequested != null) {
                onCloseRequested.run();
            }
        }
    }

    private void clearActiveTerminal() {
        if (activeSession != null) {
            activeSession.clearOutput();
        }
    }

    private void updateSessionChip() {
        if (sessionChip != null) {
            if (activeSession != null) {
                sessionChip.setText(">_ " + activeSession.getTitle() + " - " + activeSession.getWorkingDirName() + " ▾");
            } else {
                sessionChip.setText("No Terminal ▾");
            }
        }
    }

    private void showSessionSwitcherMenu() {
        ContextMenu menu = new ContextMenu();
        menu.getStyleClass().add("terminal-session-menu");

        if (sessions.isEmpty()) {
            MenuItem emptyItem = new MenuItem("No Active Terminals");
            emptyItem.setDisable(true);
            menu.getItems().add(emptyItem);
        } else {
            for (TerminalSession session : sessions) {
                String prefix = (session == activeSession) ? "✓  " : "    ";
                MenuItem item = new MenuItem(prefix + session.getTitle() + " (" + session.getWorkingDirName() + ")");
                item.setOnAction(e -> {
                    activeSession = session;
                    isSplit = false;
                    renderTerminalView();
                    updateSessionChip();
                    Platform.runLater(activeSession::focusInput);
                });
                menu.getItems().add(item);
            }
        }

        menu.getItems().add(new SeparatorMenuItem());

        MenuItem newTerm = new MenuItem("New Terminal", IconFactory.getIcon(Codicons.ADD, 12));
        newTerm.setOnAction(e -> createNewTerminal());

        MenuItem splitTerm = new MenuItem("Split Terminal", IconFactory.getIcon(Codicons.SPLIT_HORIZONTAL, 12));
        splitTerm.setOnAction(e -> toggleSplitTerminal());

        MenuItem killTerm = new MenuItem("Kill Active Terminal", IconFactory.getIcon(Codicons.TRASH, 12));
        killTerm.setOnAction(e -> killActiveTerminal());

        menu.getItems().addAll(newTerm, splitTerm, killTerm);
        menu.show(sessionChip, javafx.geometry.Side.BOTTOM, 0, 4);
    }

    public void showTerminal() {
        if (sessions.isEmpty()) {
            createNewTerminal();
        }
        setVisible(true);
        setManaged(true);
        switchDockTab(DockTab.TERMINAL);
        if (activeSession != null) {
            Platform.runLater(activeSession::focusInput);
        }
    }

    /** Runs a generated IDE command in the integrated terminal at the requested directory. */
    public void executeInIntegratedTerminal(String command, Path workingDirectory) {
        if (command == null || command.isBlank()) return;
        showTerminal();
        if (activeSession == null) return;
        String directory = workingDirectory == null ? null : workingDirectory.toAbsolutePath().toString();
        String prefix;
        if (directory == null) {
            prefix = command;
        } else if (getPlatformOS() == PlatformOS.WINDOWS) {
            if (getShellName().toLowerCase(Locale.ROOT).contains("power")) {
                prefix = "Set-Location -LiteralPath '" + directory.replace("'", "''") + "'; "
                        + translateForPowerShell(command);
            } else {
                prefix = "cd /d \"" + directory.replace("\"", "\\\"") + "\" && " + command;
            }
        } else {
            prefix = "cd '" + directory.replace("'", "'\\\"'\\\"'") + "' && " + command;
        }
        activeSession.executeCommand(prefix);
    }

    /**
     * Compiles/runs a program as its own process so stdin is only user input,
     * never the wrapper {@code cd ... && java ...} command line.
     */
    public void executeProgram(List<List<String>> steps, Path workingDirectory, String displayCommand) {
        if (steps == null || steps.isEmpty()) return;
        showTerminal();
        if (activeSession == null) return;
        activeSession.runForegroundProgram(steps, workingDirectory, displayCommand);
    }

    private String translateForPowerShell(String command) {
        String[] stages = command.split("\\s+&&\\s+");
        if (stages.length == 1) return command;
        String translated = stages[stages.length - 1];
        for (int index = stages.length - 2; index >= 0; index--) {
            translated = stages[index] + "; if ($LASTEXITCODE -eq 0) { " + translated + " }";
        }
        return translated;
    }

    public void hideTerminal() {
        setVisible(false);
        setManaged(false);
    }

    public boolean isTerminalVisible() {
        return isVisible();
    }

    private File getWorkingDirectory() {
        if (workingDirectorySupplier != null) {
            Path wd = workingDirectorySupplier.get();
            if (wd != null && wd.toFile().isDirectory()) {
                return wd.toFile();
            }
        }
        return new File(System.getProperty("user.dir", System.getProperty("user.home")));
    }

    public static String[] getShellCommand() {
        PlatformOS os = getPlatformOS();
        if (os == PlatformOS.WINDOWS) {
            String sysRoot = System.getenv("SystemRoot");
            if (sysRoot == null || sysRoot.isEmpty()) sysRoot = "C:\\Windows";
            String psPath = sysRoot + "\\System32\\WindowsPowerShell\\v1.0\\powershell.exe";
            if (new File(psPath).exists()) {
                return new String[]{psPath, "-NoLogo", "-NoExit"};
            }
            return new String[]{"cmd.exe", "/Q"};
        } else if (os == PlatformOS.MAC) {
            String shell = System.getenv("SHELL");
            if (shell != null && new File(shell).exists()) {
                return new String[]{shell};
            }
            if (new File("/bin/zsh").exists()) return new String[]{"/bin/zsh"};
            return new String[]{"/bin/bash"};
        } else { // Linux
            String shell = System.getenv("SHELL");
            if (shell != null && new File(shell).exists()) {
                return new String[]{shell};
            }
            if (new File("/bin/bash").exists()) return new String[]{"/bin/bash"};
            return new String[]{"/bin/sh"};
        }
    }

    public static String getShellName() {
        String[] cmd = getShellCommand();
        String path = cmd[0];
        int sep = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
        String name = (sep >= 0) ? path.substring(sep + 1) : path;
        if (name.toLowerCase().endsWith(".exe")) {
            name = name.substring(0, name.length() - 4);
        }
        return name;
    }

    public void setWorkingDirectorySupplier(Supplier<Path> supplier) {
        this.workingDirectorySupplier = supplier;
    }

    public void setWorkspaceSupplier(Supplier<Path> supplier) {
        this.workspaceSupplier = supplier;
    }

    public void setOnCloseRequested(Runnable handler) {
        this.onCloseRequested = handler;
    }

    public void setOnProblemNavigated(BiConsumer<String, Integer> callback) {
        this.onProblemNavigated = callback;
    }

    public void setOnProblemsUpdated(BiConsumer<Integer, Integer> callback) {
        this.onProblemsUpdated = callback;
    }

    public int getSessionsCount() {
        return sessions.size();
    }

    public int getProblemsCount() {
        return problemItems.size();
    }

    public boolean hasChannel(String channel) {
        return outputChannels.containsKey(channel);
    }

    public void dispose() {
        for (TerminalSession s : sessions) {
            try {
                s.destroy();
            } catch (Exception ex) {
                System.err.println("Error destroying terminal session: " + ex.getMessage());
            }
        }
        sessions.clear();
        
        if (ioExecutor != null && !ioExecutor.isShutdown()) {
            ioExecutor.shutdown();
            try {
                if (!ioExecutor.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS)) {
                    ioExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                ioExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // TAB 2: PROBLEMS VIEW (Diagnostics, Live Filters, Navigation)
    // ─────────────────────────────────────────────────────────────────────────
    private VBox buildProblemsView() {
        VBox root = new VBox(0);
        root.getStyleClass().add("dock-content-pane");
        VBox.setVgrow(root, Priority.ALWAYS);

        // Subheader with filter and counter pills
        HBox subHeader = new HBox(12);
        subHeader.setAlignment(Pos.CENTER_LEFT);
        subHeader.setPadding(new Insets(6, 12, 6, 12));
        subHeader.setStyle("-fx-background-color: -bg-secondary; -fx-border-color: transparent transparent -border-color transparent; -fx-border-width: 0 0 1 0;");

        problemFilterField = new TextField();
        problemFilterField.setPromptText("Filter problems (e.g. text, error, file)...");
        problemFilterField.getStyleClass().add("dock-filter-input");
        problemFilterField.setPrefWidth(280);
        problemFilterField.textProperty().addListener((obs, oldVal, newVal) -> filterProblems(newVal));

        errorCountBadge = new Label("● 0 Errors");
        errorCountBadge.setStyle("-fx-text-fill: #f14c4c; -fx-font-size: 11px; -fx-font-weight: bold;");

        warningCountBadge = new Label("▲ 0 Warnings");
        warningCountBadge.setStyle("-fx-text-fill: #cca700; -fx-font-size: 11px; -fx-font-weight: bold;");

        infoCountBadge = new Label("ℹ 0 Info");
        infoCountBadge.setStyle("-fx-text-fill: #3794ff; -fx-font-size: 11px; -fx-font-weight: bold;");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button scanBtn = new Button("Scan Workspace");
        scanBtn.setGraphic(IconFactory.getIcon(Codicons.REFRESH, 12));
        scanBtn.getStyleClass().add("dock-action-btn");
        scanBtn.setOnAction(e -> scanWorkspaceForProblems());

        subHeader.getChildren().addAll(problemFilterField, errorCountBadge, warningCountBadge, infoCountBadge, spacer, scanBtn);

        // Problem List / Empty State Container
        problemListContainer = new VBox(2);
        problemListContainer.setPadding(new Insets(6, 10, 10, 10));

        ScrollPane scrollPane = new ScrollPane(problemListContainer);
        scrollPane.setFitToWidth(true);
        scrollPane.getStyleClass().add("terminal-scroll-pane");
        VBox.setVgrow(scrollPane, Priority.ALWAYS);

        problemEmptyState = new VBox(8);
        problemEmptyState.setAlignment(Pos.CENTER);
        problemEmptyState.setPadding(new Insets(30));
        FontIcon checkIcon = IconFactory.getIcon(Codicons.CHECK, 28, "#89d185");
        Label emptyTitle = new Label("No problems have been detected in the workspace.");
        emptyTitle.setStyle("-fx-font-weight: bold; -fx-font-size: 13px; -fx-text-fill: -text-primary;");
        Label emptySubtitle = new Label("Workspace is clean. AuraOrbit diagnostic engine is monitoring open files.");
        emptySubtitle.setStyle("-fx-font-size: 11.5px; -fx-text-fill: -text-secondary;");
        problemEmptyState.getChildren().addAll(checkIcon, emptyTitle, emptySubtitle);

        root.getChildren().addAll(subHeader, scrollPane);

        updateProblemsDisplay();
        return root;
    }

    public void addProblem(Codicons icon, String severity, String message, String file, int line, int column, String source) {
        ProblemItem item = new ProblemItem(icon, severity, message, file, line, column, source);
        Runnable action = () -> {
            problemItems.add(item);
            filterProblems(problemFilterField != null ? problemFilterField.getText() : "");
            updateProblemsDisplay();
        };
        if (Platform.isFxApplicationThread()) {
            action.run();
        } else {
            Platform.runLater(action);
        }
    }

    public void clearProblems() {
        Runnable action = () -> {
            problemItems.clear();
            filteredProblemItems.clear();
            updateProblemsDisplay();
        };
        if (Platform.isFxApplicationThread()) {
            action.run();
        } else {
            Platform.runLater(action);
        }
    }

    private void filterProblems(String query) {
        filteredProblemItems.clear();
        if (query == null || query.trim().isEmpty()) {
            filteredProblemItems.addAll(problemItems);
        } else {
            String lower = query.toLowerCase();
            for (ProblemItem item : problemItems) {
                if (item.message.toLowerCase().contains(lower) ||
                    item.file.toLowerCase().contains(lower) ||
                    item.severity.toLowerCase().contains(lower) ||
                    item.source.toLowerCase().contains(lower)) {
                    filteredProblemItems.add(item);
                }
            }
        }
        renderProblemItems();
    }

    private void updateProblemsDisplay() {
        long errors = problemItems.stream().filter(p -> "Error".equalsIgnoreCase(p.severity)).count();
        long warnings = problemItems.stream().filter(p -> "Warning".equalsIgnoreCase(p.severity)).count();
        long infos = problemItems.stream().filter(p -> "Info".equalsIgnoreCase(p.severity)).count();

        errorCountBadge.setText("● " + errors + " Errors");
        warningCountBadge.setText("▲ " + warnings + " Warnings");
        infoCountBadge.setText("ℹ " + infos + " Info");

        problemsTabBtn.setText("PROBLEMS " + problemItems.size());

        filterProblems(problemFilterField != null ? problemFilterField.getText() : "");

        if (onProblemsUpdated != null) {
            onProblemsUpdated.accept((int) errors, (int) warnings);
        }
    }

    private void renderProblemItems() {
        problemListContainer.getChildren().clear();
        if (filteredProblemItems.isEmpty()) {
            problemListContainer.getChildren().add(problemEmptyState);
        } else {
            for (ProblemItem item : filteredProblemItems) {
                HBox row = new HBox(8);
                row.setAlignment(Pos.CENTER_LEFT);
                row.setPadding(new Insets(6, 8, 6, 8));
                row.getStyleClass().add("problem-row-item");

                String color = switch (item.severity.toLowerCase()) {
                    case "error" -> "#f14c4c";
                    case "warning" -> "#cca700";
                    default -> "#3794ff";
                };
                FontIcon icon = IconFactory.getIcon(item.icon, 14, color);

                Label msgLabel = new Label(item.message);
                msgLabel.setStyle("-fx-text-fill: -text-primary; -fx-font-size: 12px; -fx-font-weight: 500;");

                Region spacer = new Region();
                HBox.setHgrow(spacer, Priority.ALWAYS);

                String displayFileName = item.file;
                try {
                    displayFileName = Paths.get(item.file).getFileName().toString();
                } catch (Exception ignored) {}
                Label locLabel = new Label(displayFileName + ":" + item.line + ":" + item.column);
                locLabel.setStyle("-fx-text-fill: -text-secondary; -fx-font-size: 11px; -fx-font-family: monospace;");

                Label srcLabel = new Label("[" + item.source + "]");
                srcLabel.setStyle("-fx-text-fill: -text-secondary; -fx-font-size: 11px;");

                row.getChildren().addAll(icon, msgLabel, spacer, locLabel, srcLabel);

                row.setOnMouseClicked(e -> {
                    if (onProblemNavigated != null) {
                        onProblemNavigated.accept(item.file, item.line);
                    }
                });

                problemListContainer.getChildren().add(row);
            }
        }
    }

    public void scanWorkspaceForProblems() {
        ioExecutor.submit(() -> {
            Platform.runLater(this::clearProblems);
            Path root = workspaceSupplier != null ? workspaceSupplier.get() : null;
            if (root == null && workingDirectorySupplier != null) root = workingDirectorySupplier.get();
            if (root == null) root = Paths.get(".");

            List<ProblemItem> found = new ArrayList<>();
            List<Path> javaFiles = new ArrayList<>();
            try {
                Files.walkFileTree(root, EnumSet.noneOf(FileVisitOption.class), 20, new SimpleFileVisitor<>() {
                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                        String name = file.getFileName().toString();
                        if (name.endsWith(".java") || name.endsWith(".py") || name.endsWith(".js") ||
                            name.endsWith(".ts") || name.endsWith(".jsx") || name.endsWith(".tsx") ||
                            name.endsWith(".css") || name.endsWith(".scss") || name.endsWith(".json") ||
                            name.endsWith(".xml") || name.endsWith(".yaml") || name.endsWith(".yml") ||
                            name.endsWith(".md") || name.endsWith(".html")) {
                            found.addAll(CodeDiagnosticsService.analyzeFile(file));
                            if (name.endsWith(".java")) {
                                javaFiles.add(file);
                            }
                        }
                        return FileVisitResult.CONTINUE;
                    }
                    @Override
                    public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                        String dirName = dir.getFileName().toString();
                        if (dirName.startsWith(".") || dirName.equals("target") || dirName.equals("node_modules") ||
                            dirName.equals("build") || dirName.equals("bin") || dirName.equals(".gradle")) {
                            return FileVisitResult.SKIP_SUBTREE;
                        }
                        return FileVisitResult.CONTINUE;
                    }
                });
            } catch (Exception ignored) {}

            // Dynamic batch javac compiler diagnostics (with full classpath and lints)
            if (!javaFiles.isEmpty()) {
                found.addAll(CodeDiagnosticsService.runJavaCompilerDiagnostics(javaFiles));
            }

            Platform.runLater(() -> {
                for (ProblemItem item : found) {
                    problemItems.add(item);
                }
                updateProblemsDisplay();
            });
        });
    }

    public void updateFileProblems(Path file) {
        if (file == null) return;
        ioExecutor.submit(() -> {
            List<ProblemItem> fileProblems = CodeDiagnosticsService.analyzeFile(file);
            String targetPath = file.toAbsolutePath().normalize().toString();
            Platform.runLater(() -> {
                problemItems.removeIf(p -> p.file != null && p.file.equals(targetPath));
                problemItems.addAll(fileProblems);
                updateProblemsDisplay();
            });
        });
    }

    // ─────────────────────────────────────────────────────────────────────────
    // TAB 3: OUTPUT VIEW (Channel Streaming, Maven Build Runner, Log View)
    // ─────────────────────────────────────────────────────────────────────────
    private VBox buildOutputView() {
        VBox root = new VBox(0);
        root.getStyleClass().add("dock-content-pane");
        VBox.setVgrow(root, Priority.ALWAYS);

        outputChannelCombo = new ComboBox<>();
        outputChannelCombo.getItems().addAll("AuraOrbit (System)", "Tasks & Maven", "Git", "AI Copilot");
        outputChannelCombo.getSelectionModel().selectFirst();
        outputChannelCombo.getStyleClass().add("dock-channel-combo");

        outputAreaStack = new StackPane();
        VBox.setVgrow(outputAreaStack, Priority.ALWAYS);

        for (String channel : outputChannelCombo.getItems()) {
            TextArea area = new TextArea();
            area.setEditable(false);
            area.setWrapText(true);
            area.getStyleClass().add("dock-output-text-area");
            outputChannels.put(channel, area);
            outputAreaStack.getChildren().add(area);
        }

        outputChannelCombo.setOnAction(e -> {
            String selected = outputChannelCombo.getValue();
            if (selected == null) return;
            for (Map.Entry<String, TextArea> entry : outputChannels.entrySet()) {
                entry.getValue().setVisible(entry.getKey().equals(selected));
            }
        });

        // Initialize system channel with startup log
        logOutput("AuraOrbit (System)", "AuraOrbit Studio v2.0.0 [Ready]");
        logOutput("AuraOrbit (System)", "OS: " + System.getProperty("os.name") + " (" + System.getProperty("os.arch") + ")");
        logOutput("AuraOrbit (System)", "Java: " + System.getProperty("java.version") + " (" + System.getProperty("java.vendor") + ")");
        logOutput("AuraOrbit (System)", "User Directory: " + System.getProperty("user.dir"));
        logOutput("AuraOrbit (System)", "All core services running smoothly.");

        logOutput("Tasks & Maven", "[Maven] Ready for build commands.");
        logOutput("Git", "[Git] Monitoring workspace repository.");
        logOutput("AI Copilot", "[Copilot] AI Assistant initialized and ready.");

        // Show first channel
        outputChannels.get("AuraOrbit (System)").setVisible(true);

        root.getChildren().add(outputAreaStack);
        return root;
    }

    public void logOutput(String channel, String message) {
        Platform.runLater(() -> {
            TextArea area = outputChannels.get(channel);
            if (area != null) {
                String timestamp = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
                area.appendText("[" + timestamp + "] " + message + "\n");
                area.positionCaret(area.getLength());
            }
        });
    }

    private void runMavenBuild() {
        outputChannelCombo.getSelectionModel().select("Tasks & Maven");
        logOutput("Tasks & Maven", "==================================================");
        logOutput("Tasks & Maven", "Running 'mvn test-compile' in background...");
        logOutput("Tasks & Maven", "==================================================");

        ioExecutor.submit(() -> {
            try {
                File wd = getWorkingDirectory();
                ProcessBuilder pb = new ProcessBuilder("mvn", "test-compile");
                pb.directory(wd);
                pb.redirectErrorStream(true);

                Process p = pb.start();
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        final String l = line;
                        logOutput("Tasks & Maven", l);
                        if (l.contains("[ERROR]")) {
                            addProblem(Codicons.ERROR, "Error", l, "pom.xml", 1, 1, "maven");
                        }
                    }
                }
                int exitCode = p.waitFor();
                logOutput("Tasks & Maven", exitCode == 0 ? "[BUILD SUCCESS]" : "[BUILD FAILURE exit " + exitCode + "]");
            } catch (Exception e) {
                logOutput("Tasks & Maven", "Failed to run Maven: " + e.getMessage());
            }
        });
    }

    private void clearActiveOutput() {
        String selected = outputChannelCombo.getValue();
        TextArea area = outputChannels.get(selected);
        if (area != null) {
            area.clear();
        }
    }

    private void copyActiveOutput() {
        String selected = outputChannelCombo.getValue();
        TextArea area = outputChannels.get(selected);
        if (area != null) {
            javafx.scene.input.ClipboardContent content = new javafx.scene.input.ClipboardContent();
            content.putString(area.getText());
            javafx.scene.input.Clipboard.getSystemClipboard().setContent(content);
        }
    }

    public void selectOutputChannel(String channel) {
        Platform.runLater(() -> {
            if (outputChannelCombo != null && outputChannels.containsKey(channel)) {
                if (!Objects.equals(outputChannelCombo.getValue(), channel)) {
                    outputChannelCombo.getSelectionModel().select(channel);
                }
                for (Map.Entry<String, TextArea> entry : outputChannels.entrySet()) {
                    entry.getValue().setVisible(entry.getKey().equals(channel));
                }
            }
        });
    }

    public void refreshGitOutput() {
        if (!isGitQueryRunning.compareAndSet(false, true)) {
            return;
        }
        selectOutputChannel("Git");
        Platform.runLater(() -> {
            TextArea gitArea = outputChannels.get("Git");
            if (gitArea != null) {
                gitArea.clear();
            }
        });
        logOutput("Git", "==================================================");
        logOutput("Git", "Dynamic Git Status & Commit History query...");
        logOutput("Git", "==================================================");

        ioExecutor.submit(() -> {
            try {
                File wd = getWorkingDirectory();
                ProcessBuilder pbStatus = new ProcessBuilder("git", "status", "-s");
                pbStatus.directory(wd);
                pbStatus.redirectErrorStream(true);
                Process p1 = pbStatus.start();
                try (BufferedReader r = new BufferedReader(new InputStreamReader(p1.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    boolean hasChanges = false;
                    while ((line = r.readLine()) != null) {
                        hasChanges = true;
                        logOutput("Git", line);
                    }
                    if (!hasChanges) {
                        logOutput("Git", "Working tree clean: all files synchronized with git repository.");
                    }
                }
                p1.waitFor();

                logOutput("Git", "\n--- Recent Commits (git log -n 5) ---");
                ProcessBuilder pbLog = new ProcessBuilder("git", "log", "-n", "5", "--oneline");
                pbLog.directory(wd);
                pbLog.redirectErrorStream(true);
                Process p2 = pbLog.start();
                try (BufferedReader r = new BufferedReader(new InputStreamReader(p2.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = r.readLine()) != null) {
                        logOutput("Git", line);
                    }
                }
                p2.waitFor();
            } catch (Exception e) {
                logOutput("Git", "Git query error: " + e.getMessage());
            } finally {
                isGitQueryRunning.set(false);
            }
        });
    }

    // ─────────────────────────────────────────────────────────────────────────
    // TAB 4: DEBUG CONSOLE (Interactive REPL, Expression Evaluator, History)
    // ─────────────────────────────────────────────────────────────────────────
    private VBox buildDebugView() {
        VBox root = new VBox(0);
        root.getStyleClass().add("dock-content-pane");
        VBox.setVgrow(root, Priority.ALWAYS);

        debugHistoryBox = new VBox(4);
        debugHistoryBox.setPadding(new Insets(8, 12, 8, 12));

        debugScrollPane = new ScrollPane(debugHistoryBox);
        debugScrollPane.setFitToWidth(true);
        debugScrollPane.getStyleClass().add("terminal-scroll-pane");
        VBox.setVgrow(debugScrollPane, Priority.ALWAYS);

        // Initial welcome message
        appendDebugEntry("sys", "AuraOrbit Interactive Debug Console [Java " + System.getProperty("java.version") + "]", "#858585");
        appendDebugEntry("sys", "Type 'help' for available debug commands, or type an expression to evaluate.", "#858585");

        // Input prompt row
        HBox inputRow = new HBox(6);
        inputRow.setAlignment(Pos.CENTER_LEFT);
        inputRow.setPadding(new Insets(6, 12, 8, 12));
        inputRow.setStyle("-fx-background-color: #181818; -fx-border-color: -border-color transparent transparent transparent; -fx-border-width: 1 0 0 0;");

        Label promptLabel = new Label(">");
        promptLabel.setStyle("-fx-text-fill: #4ec9b0; -fx-font-family: monospace; -fx-font-size: 13px; -fx-font-weight: bold;");

        debugInputField = new TextField();
        debugInputField.setPromptText("Evaluate expression or run debug command ('help' for commands)...");
        debugInputField.getStyleClass().add("terminal-direct-input");
        HBox.setHgrow(debugInputField, Priority.ALWAYS);

        debugInputField.addEventFilter(KeyEvent.KEY_PRESSED, this::handleDebugKey);

        inputRow.getChildren().addAll(promptLabel, debugInputField);

        root.getChildren().addAll(debugScrollPane, inputRow);
        return root;
    }

    private void handleDebugKey(KeyEvent e) {
        if (e.getCode() == KeyCode.ENTER) {
            String text = debugInputField.getText();
            debugInputField.clear();
            if (text != null && !text.trim().isEmpty()) {
                debugCommandHistory.add(text);
                debugHistoryIndex = debugCommandHistory.size();
                executeDebugCommand(text.trim());
            }
            e.consume();
        } else if (e.getCode() == KeyCode.UP) {
            if (!debugCommandHistory.isEmpty() && debugHistoryIndex > 0) {
                debugHistoryIndex--;
                debugInputField.setText(debugCommandHistory.get(debugHistoryIndex));
                debugInputField.positionCaret(debugInputField.getText().length());
            }
            e.consume();
        } else if (e.getCode() == KeyCode.DOWN) {
            if (debugHistoryIndex < debugCommandHistory.size() - 1) {
                debugHistoryIndex++;
                debugInputField.setText(debugCommandHistory.get(debugHistoryIndex));
                debugInputField.positionCaret(debugInputField.getText().length());
            } else {
                debugHistoryIndex = debugCommandHistory.size();
                debugInputField.clear();
            }
            e.consume();
        }
    }

    private void executeDebugCommand(String cmd) {
        appendDebugEntry("cmd", "> " + cmd, "#4ec9b0");

        String lower = cmd.toLowerCase();
        if (lower.equals("help")) {
            appendDebugEntry("res", """
                    Available Debug Console Commands:
                      help             - Show this command reference
                      mem / memory     - Inspect JVM heap memory allocation & usage
                      gc               - Trigger garbage collection and print reclaimed memory
                      threads          - List active JVM threads and their execution states
                      sys / env        - Print runtime environment, OS details & VM properties
                      workspace        - Inspect active workspace root directory
                      eval <expr>      - Evaluate arithmetic/math expressions (e.g. eval 24 * 60)
                      clear / cls      - Clear debug console history
                    """, "#9cdcfe");
        } else if (lower.equals("mem") || lower.equals("memory")) {
            Runtime rt = Runtime.getRuntime();
            long total = rt.totalMemory() / (1024 * 1024);
            long free = rt.freeMemory() / (1024 * 1024);
            long used = total - free;
            long max = rt.maxMemory() / (1024 * 1024);
            double pct = (double) used / total * 100.0;
            appendDebugEntry("res", String.format("Heap Usage: %d MB / %d MB (%.1f%% allocated) | Max Heap: %d MB", used, total, pct, max), "#89d185");
        } else if (lower.equals("gc")) {
            Runtime rt = Runtime.getRuntime();
            long before = rt.totalMemory() - rt.freeMemory();
            System.gc();
            long after = rt.totalMemory() - rt.freeMemory();
            long freed = Math.max(0, before - after) / (1024 * 1024);
            appendDebugEntry("res", "System.gc() executed. Memory freed: " + freed + " MB (Current used: " + (after / (1024 * 1024)) + " MB)", "#89d185");
        } else if (lower.equals("threads")) {
            Set<Thread> threadSet = Thread.getAllStackTraces().keySet();
            StringBuilder sb = new StringBuilder("Active JVM Threads (" + threadSet.size() + "):\n");
            for (Thread t : threadSet) {
                sb.append(String.format("  • [%-15s] id=%-4d daemon=%-5b state=%s\n", t.getName(), t.threadId(), t.isDaemon(), t.getState()));
            }
            appendDebugEntry("res", sb.toString(), "#ce9178");
        } else if (lower.equals("sys") || lower.equals("env")) {
            appendDebugEntry("res", String.format("""
                    System Information:
                      OS: %s (%s, version %s)
                      Java Version: %s (%s)
                      Available Processors: %d
                      PID: %s
                    """,
                    System.getProperty("os.name"), System.getProperty("os.arch"), System.getProperty("os.version"),
                    System.getProperty("java.version"), System.getProperty("java.vendor"),
                    Runtime.getRuntime().availableProcessors(),
                    ManagementFactory.getRuntimeMXBean().getName()
            ), "#dcdcaa");
        } else if (lower.equals("workspace")) {
            Path root = workspaceSupplier != null ? workspaceSupplier.get() : null;
            appendDebugEntry("res", "Workspace Root: " + (root != null ? root.toAbsolutePath().toString() : System.getProperty("user.dir")), "#9cdcfe");
        } else if (lower.equals("clear") || lower.equals("cls")) {
            clearDebugConsole();
        } else if (lower.startsWith("eval ") || cmd.matches("^[0-9+\\-*/%^().\\s]+$")) {
            String expr = lower.startsWith("eval ") ? cmd.substring(5).trim() : cmd;
            try {
                double result = evaluateSimpleMath(expr);
                appendDebugEntry("res", "= " + result, "#89d185");
            } catch (Exception e) {
                appendDebugEntry("err", "Evaluation error: " + e.getMessage(), "#f14c4c");
            }
        } else {
            appendDebugEntry("err", "Unrecognized command '" + cmd + "'. Type 'help' for command list.", "#f14c4c");
        }
    }

    private double evaluateSimpleMath(String expr) {
        return new Object() {
            int pos = -1, ch;
            void nextChar() {
                ch = (++pos < expr.length()) ? expr.charAt(pos) : -1;
            }
            boolean eat(int charToEat) {
                while (ch == ' ') nextChar();
                if (ch == charToEat) {
                    nextChar();
                    return true;
                }
                return false;
            }
            double parse() {
                nextChar();
                double x = parseExpression();
                if (pos < expr.length()) throw new RuntimeException("Unexpected char: " + (char) ch);
                return x;
            }
            double parseExpression() {
                double x = parseTerm();
                for (;;) {
                    if      (eat('+')) x += parseTerm();
                    else if (eat('-')) x -= parseTerm();
                    else return x;
                }
            }
            double parseTerm() {
                double x = parseFactor();
                for (;;) {
                    if      (eat('*')) x *= parseFactor();
                    else if (eat('/')) x /= parseFactor();
                    else if (eat('%')) x %= parseFactor();
                    else return x;
                }
            }
            double parseFactor() {
                if (eat('+')) return +parseFactor();
                if (eat('-')) return -parseFactor();
                double x;
                int startPos = this.pos;
                if (eat('(')) {
                    x = parseExpression();
                    if (!eat(')')) throw new RuntimeException("Missing closing parenthesis");
                } else if ((ch >= '0' && ch <= '9') || ch == '.') {
                    while ((ch >= '0' && ch <= '9') || ch == '.') nextChar();
                    x = Double.parseDouble(expr.substring(startPos, this.pos));
                } else {
                    throw new RuntimeException("Unexpected token");
                }
                if (eat('^')) x = Math.pow(x, parseFactor());
                return x;
            }
        }.parse();
    }

    private void appendDebugEntry(String type, String text, String colorHex) {
        Label lbl = new Label(text);
        lbl.setStyle("-fx-text-fill: " + colorHex + "; -fx-font-family: Menlo, Monaco, Consolas, monospace; -fx-font-size: 12px;");
        lbl.setWrapText(true);
        debugHistoryBox.getChildren().add(lbl);
        Platform.runLater(() -> debugScrollPane.setVvalue(1.0));
    }

    private void clearDebugConsole() {
        debugHistoryBox.getChildren().clear();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // TAB 5: PORTS VIEW (Live OS Port Discovery, Process Termination, Browser Launch)
    // ─────────────────────────────────────────────────────────────────────────
    private VBox buildPortsView() {
        VBox root = new VBox(0);
        root.getStyleClass().add("dock-content-pane");
        VBox.setVgrow(root, Priority.ALWAYS);

        // Subheader with filter, counter and action buttons
        HBox subHeader = new HBox(12);
        subHeader.setAlignment(Pos.CENTER_LEFT);
        subHeader.setPadding(new Insets(6, 12, 6, 12));
        subHeader.setStyle("-fx-background-color: -bg-secondary; -fx-border-color: transparent transparent -border-color transparent; -fx-border-width: 0 0 1 0;");

        portFilterField = new TextField();
        portFilterField.setPromptText("Filter ports (e.g. port, process, pid)...");
        portFilterField.getStyleClass().add("dock-filter-input");
        portFilterField.setPrefWidth(280);
        portFilterField.textProperty().addListener((obs, oldVal, newVal) -> filterPorts(newVal));

        portCountBadge = new Label("● 0 Ports Listening");
        portCountBadge.setStyle("-fx-text-fill: #89d185; -fx-font-size: 11px; -fx-font-weight: bold;");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button scanBtn = new Button("Scan Ports");
        scanBtn.setGraphic(IconFactory.getIcon(Codicons.REFRESH, 12));
        scanBtn.getStyleClass().add("dock-action-btn");
        scanBtn.setOnAction(e -> scanLocalPorts());

        Button addPortBtn = new Button("Forward Port");
        addPortBtn.setGraphic(IconFactory.getIcon(Codicons.ADD, 12));
        addPortBtn.getStyleClass().add("dock-action-btn");
        addPortBtn.setOnAction(e -> showAddPortDialog());

        subHeader.getChildren().addAll(portFilterField, portCountBadge, spacer, scanBtn, addPortBtn);

        portsTableContainer = new VBox(0);
        portsTableContainer.setPadding(new Insets(0));

        ScrollPane scrollPane = new ScrollPane(portsTableContainer);
        scrollPane.setFitToWidth(true);
        scrollPane.getStyleClass().add("terminal-scroll-pane");
        VBox.setVgrow(scrollPane, Priority.ALWAYS);

        portsEmptyState = new VBox(8);
        portsEmptyState.setAlignment(Pos.CENTER);
        portsEmptyState.setPadding(new Insets(30));
        FontIcon towerIcon = IconFactory.getIcon(Codicons.RADIO_TOWER, 28, "#6e7681");
        Label emptyTitle = new Label("No active listening ports detected on localhost.");
        emptyTitle.setStyle("-fx-font-weight: bold; -fx-font-size: 13px; -fx-text-fill: -text-primary;");
        Label emptySubtitle = new Label("Start a dev server or click 'Scan Ports' to detect active network processes.");
        emptySubtitle.setStyle("-fx-font-size: 11.5px; -fx-text-fill: -text-secondary;");
        portsEmptyState.getChildren().addAll(towerIcon, emptyTitle, emptySubtitle);

        root.getChildren().addAll(subHeader, scrollPane);
        return root;
    }

    private void filterPorts(String query) {
        filteredPortEntries.clear();
        if (query == null || query.trim().isEmpty()) {
            filteredPortEntries.addAll(portEntries);
        } else {
            String lower = query.toLowerCase();
            for (PortEntry entry : portEntries) {
                if (String.valueOf(entry.port).contains(lower) ||
                    entry.processName.toLowerCase().contains(lower) ||
                    String.valueOf(entry.pid).contains(lower) ||
                    entry.protocol.toLowerCase().contains(lower)) {
                    filteredPortEntries.add(entry);
                }
            }
        }
        renderPortsRows();
    }

    private void renderPortsTable() {
        long listeningCount = portEntries.stream().filter(p -> p.listening).count();
        portCountBadge.setText("● " + listeningCount + " Ports Listening");
        filterPorts(portFilterField != null ? portFilterField.getText() : "");
    }

    private void renderPortsRows() {
        portsTableContainer.getChildren().clear();

        // Table Header
        HBox header = new HBox(12);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(6, 12, 6, 12));
        header.setStyle("-fx-background-color: -bg-secondary; -fx-border-color: transparent transparent -border-color transparent; -fx-border-width: 0 0 1 0;");

        Label colPort = new Label("PORT");
        colPort.setPrefWidth(80);
        colPort.setStyle("-fx-text-fill: -text-secondary; -fx-font-weight: bold; -fx-font-size: 11px;");

        Label colPid = new Label("PID");
        colPid.setPrefWidth(70);
        colPid.setStyle("-fx-text-fill: -text-secondary; -fx-font-weight: bold; -fx-font-size: 11px;");

        Label colService = new Label("PROCESS / COMMAND");
        colService.setPrefWidth(200);
        colService.setStyle("-fx-text-fill: -text-secondary; -fx-font-weight: bold; -fx-font-size: 11px;");

        Label colProto = new Label("PROTOCOL");
        colProto.setPrefWidth(90);
        colProto.setStyle("-fx-text-fill: -text-secondary; -fx-font-weight: bold; -fx-font-size: 11px;");

        Label colStatus = new Label("STATUS");
        colStatus.setPrefWidth(110);
        colStatus.setStyle("-fx-text-fill: -text-secondary; -fx-font-weight: bold; -fx-font-size: 11px;");

        Label colAddress = new Label("LOCAL ADDRESS");
        HBox.setHgrow(colAddress, Priority.ALWAYS);
        colAddress.setStyle("-fx-text-fill: -text-secondary; -fx-font-weight: bold; -fx-font-size: 11px;");

        Label colActions = new Label("ACTIONS");
        colActions.setPrefWidth(120);
        colActions.setAlignment(Pos.CENTER_RIGHT);
        colActions.setStyle("-fx-text-fill: -text-secondary; -fx-font-weight: bold; -fx-font-size: 11px;");

        header.getChildren().addAll(colPort, colPid, colService, colProto, colStatus, colAddress, colActions);
        portsTableContainer.getChildren().add(header);

        if (filteredPortEntries.isEmpty()) {
            portsTableContainer.getChildren().add(portsEmptyState);
            return;
        }

        // Table Rows
        for (PortEntry entry : filteredPortEntries) {
            HBox row = new HBox(12);
            row.setAlignment(Pos.CENTER_LEFT);
            row.setPadding(new Insets(6, 12, 6, 12));
            row.setStyle("-fx-border-color: transparent transparent #252526 transparent; -fx-border-width: 0 0 1 0;");

            Label p = new Label(String.valueOf(entry.port));
            p.setPrefWidth(80);
            p.setStyle("-fx-text-fill: -text-primary; -fx-font-family: monospace; -fx-font-weight: bold; -fx-font-size: 12px;");

            Label pidLbl = new Label(entry.pid > 0 ? String.valueOf(entry.pid) : "-");
            pidLbl.setPrefWidth(70);
            pidLbl.setStyle("-fx-text-fill: -text-secondary; -fx-font-family: monospace; -fx-font-size: 11px;");

            Label srv = new Label(entry.processName);
            srv.setPrefWidth(200);
            srv.setStyle("-fx-text-fill: -text-primary; -fx-font-size: 12px; -fx-font-weight: 500;");

            Label proto = new Label(entry.protocol);
            proto.setPrefWidth(90);
            proto.setStyle("-fx-text-fill: -text-secondary; -fx-font-size: 11px;");

            Label status = new Label(entry.listening ? "● LISTENING" : "○ INACTIVE");
            status.setPrefWidth(110);
            status.setStyle(entry.listening ? "-fx-text-fill: #89d185; -fx-font-weight: bold; -fx-font-size: 11px;" : "-fx-text-fill: #6e7681; -fx-font-size: 11px;");

            Hyperlink link = new Hyperlink(entry.localAddress);
            HBox.setHgrow(link, Priority.ALWAYS);
            link.setStyle("-fx-font-family: monospace; -fx-font-size: 11.5px; -fx-text-fill: -accent-color; -fx-padding: 0;");
            link.setOnAction(e -> openUrlInBrowser(entry.localAddress));

            HBox actions = new HBox(4);
            actions.setPrefWidth(120);
            actions.setAlignment(Pos.CENTER_RIGHT);

            Button openBtn = new Button();
            openBtn.setGraphic(IconFactory.getIcon(Codicons.LINK_EXTERNAL, 12));
            openBtn.getStyleClass().add("dock-action-btn");
            openBtn.setTooltip(new Tooltip("Open in Browser (" + entry.localAddress + ")"));
            openBtn.setOnAction(e -> openUrlInBrowser(entry.localAddress));

            // Stop Process Button (Red Stop Icon)
            Button stopBtn = new Button();
            stopBtn.setGraphic(IconFactory.getIcon(Codicons.DEBUG_STOP, 12, "#f14c4c"));
            stopBtn.getStyleClass().add("dock-action-btn");
            stopBtn.setTooltip(new Tooltip(entry.pid > 0 ? "Stop process '" + entry.processName + "' (PID: " + entry.pid + ")" : "Stop process on port " + entry.port));
            stopBtn.setDisable(!entry.listening);
            stopBtn.setOnAction(e -> stopPortProcess(entry));

            Button removeBtn = new Button();
            removeBtn.setGraphic(IconFactory.getIcon(Codicons.TRASH, 12));
            removeBtn.getStyleClass().add("dock-action-btn");
            removeBtn.setTooltip(new Tooltip("Remove from list"));
            removeBtn.setOnAction(e -> {
                userTrackedPorts.remove(entry.port);
                portEntries.remove(entry);
                renderPortsTable();
            });

            actions.getChildren().addAll(openBtn, stopBtn, removeBtn);
            row.getChildren().addAll(p, pidLbl, srv, proto, status, link, actions);
            portsTableContainer.getChildren().add(row);
        }
    }

    public void scanLocalPorts() {
        ioExecutor.submit(() -> {
            PlatformOS os = getPlatformOS();
            Map<Integer, PortEntry> discovered = new LinkedHashMap<>();

            if (os == PlatformOS.MAC || os == PlatformOS.LINUX) {
                scanUnixListeningPorts(discovered);
            } else {
                scanWindowsListeningPorts(discovered);
            }

            // Also probe any user-tracked custom ports
            for (Integer userPort : userTrackedPorts) {
                if (!discovered.containsKey(userPort)) {
                    boolean alive = checkPortListening(userPort);
                    discovered.put(userPort, new PortEntry(userPort, "TCP", alive, "Forwarded Port " + userPort, -1, true));
                }
            }

            Platform.runLater(() -> {
                portEntries.setAll(discovered.values());
                renderPortsTable();
            });
        });
    }

    private void scanUnixListeningPorts(Map<Integer, PortEntry> discovered) {
        try {
            ProcessBuilder pb = new ProcessBuilder("lsof", "-nP", "-iTCP", "-sTCP:LISTEN");
            pb.redirectErrorStream(true);
            Process p = pb.start();

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    if (line.isEmpty() || line.startsWith("COMMAND")) continue;

                    String[] parts = line.split("\\s+");
                    if (parts.length >= 9) {
                        String cmd = parts[0];
                        long pid = -1;
                        try { pid = Long.parseLong(parts[1]); } catch (NumberFormatException ignored) {}
                        String type = parts.length > 4 ? parts[4] : "IPv4";

                        String addressToken = "";
                        for (int i = parts.length - 1; i >= 0; i--) {
                            if (parts[i].contains(":") && !parts[i].equals("(LISTEN)")) {
                                addressToken = parts[i];
                                break;
                            }
                        }

                        if (!addressToken.isEmpty()) {
                            int colon = addressToken.lastIndexOf(':');
                            if (colon != -1) {
                                String portStr = addressToken.substring(colon + 1).replaceAll("[^0-9]", "");
                                if (!portStr.isEmpty()) {
                                    try {
                                        int port = Integer.parseInt(portStr);
                                        if (port > 0 && port <= 65535) {
                                            PortEntry existing = discovered.get(port);
                                            if (existing != null) {
                                                if (!existing.protocol.contains(type)) {
                                                    existing.protocol = "TCP (IPv4/IPv6)";
                                                }
                                            } else {
                                                String friendlyName = resolveProcessName(pid, cmd);
                                                discovered.put(port, new PortEntry(port, "TCP (" + type + ")", true, friendlyName, pid, false));
                                            }
                                        }
                                    } catch (NumberFormatException ignored) {}
                                }
                            }
                        }
                    }
                }
            }
            p.waitFor(1500, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            fallbackSocketScan(discovered);
        }
    }

    private void scanWindowsListeningPorts(Map<Integer, PortEntry> discovered) {
        try {
            ProcessBuilder pb = new ProcessBuilder("netstat", "-ano", "-p", "tcp");
            pb.redirectErrorStream(true);
            Process p = pb.start();

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    if (!line.toUpperCase().contains("LISTENING")) continue;

                    String[] parts = line.split("\\s+");
                    if (parts.length >= 4) {
                        String localAddr = parts[1];
                        long pid = -1;
                        try {
                            pid = Long.parseLong(parts[parts.length - 1]);
                        } catch (NumberFormatException ignored) {}

                        int colon = localAddr.lastIndexOf(':');
                        if (colon != -1) {
                            String portStr = localAddr.substring(colon + 1);
                            try {
                                int port = Integer.parseInt(portStr);
                                if (port > 0 && port <= 65535 && !discovered.containsKey(port)) {
                                    String friendlyName = resolveProcessName(pid, "Process " + pid);
                                    discovered.put(port, new PortEntry(port, "TCP", true, friendlyName, pid, false));
                                }
                            } catch (NumberFormatException ignored) {}
                        }
                    }
                }
            }
            p.waitFor(1500, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            fallbackSocketScan(discovered);
        }
    }

    private void fallbackSocketScan(Map<Integer, PortEntry> discovered) {
        int[] commonPorts = {3000, 3001, 5000, 5173, 7000, 8000, 8080, 8081, 9000};
        for (int port : commonPorts) {
            if (checkPortListening(port) && !discovered.containsKey(port)) {
                discovered.put(port, new PortEntry(port, "TCP", true, "Local Service", -1, false));
            }
        }
    }

    private String resolveProcessName(long pid, String fallback) {
        if (pid > 0) {
            try {
                Optional<ProcessHandle> ph = ProcessHandle.of(pid);
                if (ph.isPresent()) {
                    Optional<String> cmd = ph.get().info().command();
                    if (cmd.isPresent() && !cmd.get().isEmpty()) {
                        String path = cmd.get();
                        int sep = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
                        String name = (sep >= 0) ? path.substring(sep + 1) : path;
                        if (name.equalsIgnoreCase("java") || name.equalsIgnoreCase("javaw")) {
                            return "Java (AuraOrbit/App)";
                        } else if (name.equalsIgnoreCase("node")) {
                            return "Node.js Server";
                        } else if (name.toLowerCase().contains("python")) {
                            return "Python Server";
                        } else if (name.equalsIgnoreCase("ControlCenter")) {
                            return "ControlCenter (AirPlay)";
                        }
                        return name;
                    }
                }
            } catch (Exception ignored) {}
        }
        if (fallback.equalsIgnoreCase("ControlCe")) return "ControlCenter (AirPlay)";
        return fallback;
    }

    public void stopPortProcess(PortEntry entry) {
        logOutput("AuraOrbit (System)", "Terminating process '" + entry.processName + "' on port " + entry.port + "...");
        ioExecutor.submit(() -> {
            boolean stopped = false;
            if (entry.pid > 0) {
                try {
                    Optional<ProcessHandle> ph = ProcessHandle.of(entry.pid);
                    if (ph.isPresent()) {
                        ProcessHandle h = ph.get();
                        h.destroy();
                        try {
                            h.onExit().get(400, TimeUnit.MILLISECONDS);
                            stopped = true;
                        } catch (Exception ignored) {
                            h.destroyForcibly();
                            stopped = true;
                        }
                    }
                } catch (Exception ignored) {}
            }

            if (entry.pid > 0) {
                try {
                    PlatformOS os = getPlatformOS();
                    if (os == PlatformOS.WINDOWS) {
                        new ProcessBuilder("taskkill", "/F", "/PID", String.valueOf(entry.pid)).start().waitFor(800, TimeUnit.MILLISECONDS);
                    } else {
                        new ProcessBuilder("kill", "-9", String.valueOf(entry.pid)).start().waitFor(800, TimeUnit.MILLISECONDS);
                    }
                    stopped = true;
                } catch (Exception ignored) {}
            }

            // Fallback kill by port
            killProcessByPort(entry.port);

            if (stopped) {
                logOutput("AuraOrbit (System)", "[Ports] Process '" + entry.processName + "' (PID: " + entry.pid + ") on port " + entry.port + " stopped.");
            } else {
                logOutput("AuraOrbit (System)", "[Ports] Termination signal sent for '" + entry.processName + "' on port " + entry.port + ".");
            }

            try { Thread.sleep(400); } catch (InterruptedException ignored) {}
            scanLocalPorts();
        });
    }

    private void killProcessByPort(int port) {
        try {
            PlatformOS os = getPlatformOS();
            if (os == PlatformOS.WINDOWS) {
                Process p = new ProcessBuilder("cmd.exe", "/c", "for /f \"tokens=5\" %a in ('netstat -aon ^| findstr :" + port + "') do taskkill /f /pid %a").start();
                p.waitFor(800, TimeUnit.MILLISECONDS);
            } else {
                Process p = new ProcessBuilder("sh", "-c", "lsof -ti tcp:" + port + " | xargs kill -9").start();
                p.waitFor(800, TimeUnit.MILLISECONDS);
            }
        } catch (Exception ignored) {}
    }

    private boolean checkPortListening(int port) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress("127.0.0.1", port), 100);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private void showAddPortDialog() {
        if (modalOverlayPane != null) {
            modalOverlayPane.showTextInput(
                    "Forward / Add Port",
                    "Enter local TCP port number to monitor or forward:",
                    "8080",
                    str -> {
                        if (str == null || str.isBlank()) return;
                        try {
                            int p = Integer.parseInt(str.trim());
                            if (p > 0 && p <= 65535) {
                                userTrackedPorts.add(p);
                                scanLocalPorts();
                            } else {
                                modalOverlayPane.showError("Invalid Port", "Port number must be between 1 and 65535.");
                            }
                        } catch (NumberFormatException ignored) {
                            modalOverlayPane.showError("Invalid Port", "Please enter a valid numeric port.");
                        }
                    }
            );
        } else {
            TextInputDialog dialog = new TextInputDialog("8080");
            dialog.setTitle("Forward / Add Port");
            dialog.setHeaderText("Forward or monitor a local TCP port");
            dialog.setContentText("Port number:");
            dialog.showAndWait().ifPresent(str -> {
                try {
                    int p = Integer.parseInt(str.trim());
                    if (p > 0 && p <= 65535) {
                        userTrackedPorts.add(p);
                        scanLocalPorts();
                    }
                } catch (NumberFormatException ignored) {}
            });
        }
    }

    private void openUrlInBrowser(String url) {
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(new URI(url));
            }
        } catch (Exception ignored) {}
    }

    public int getPortsCount() {
        return portEntries.size();
    }

    public void refreshPorts() {
        scanLocalPorts();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Inner class: TerminalSession — Top-Aligned Direct Shell Prompt & Streaming
    // ─────────────────────────────────────────────────────────────────────────
    private class TerminalSession {

        private final int sessionId;
        private final VBox sessionRoot;
        private final ScrollPane scrollPane;
        private final VBox terminalContent;
        private final TextFlow outputFlow;
        private final HBox promptRow;
        private final Label promptLabel;
        private final TextField cmdInput;

        private final List<String> commandHistory = new ArrayList<>();
        private int historyIndex = -1;

        private File currentDir;
        private Process process;
        private BufferedWriter processWriter;
        private Process foregroundProcess;
        private BufferedWriter programWriter;
        private volatile boolean alive = false;
        private volatile boolean programRunning = false;

        TerminalSession(int id, File workingDir) {
            this.sessionId = id;
            this.currentDir = workingDir != null ? workingDir : new File(".");

            sessionRoot = new VBox(0);
            sessionRoot.getStyleClass().add("terminal-session-pane");
            VBox.setVgrow(sessionRoot, Priority.ALWAYS);

            // Inner scrollable content container: starts at row 0 (TOP)
            terminalContent = new VBox(0);
            terminalContent.getStyleClass().add("terminal-content-box");
            terminalContent.setPadding(new Insets(6, 10, 10, 10));

            // Output stream (grows downwards as commands run)
            outputFlow = new TextFlow();
            outputFlow.getStyleClass().add("terminal-text-flow");

            // Direct Shell Prompt Line: sits directly at the top when empty!
            promptRow = new HBox(2);
            promptRow.setAlignment(Pos.CENTER_LEFT);
            promptRow.getStyleClass().add("terminal-prompt-row");

            promptLabel = new Label(computePromptString());
            promptLabel.getStyleClass().add("terminal-shell-prompt");

            cmdInput = new TextField();
            cmdInput.getStyleClass().add("terminal-direct-input");
            HBox.setHgrow(cmdInput, Priority.ALWAYS);

            // Handle keyboard navigation, history, Ctrl+C, Ctrl+L
            cmdInput.addEventFilter(KeyEvent.KEY_PRESSED, this::handlePromptKey);

            promptRow.getChildren().addAll(promptLabel, cmdInput);

            terminalContent.getChildren().addAll(outputFlow, promptRow);

            // Outer ScrollPane pinned to dark background
            scrollPane = new ScrollPane(terminalContent);
            scrollPane.setFitToWidth(true);
            scrollPane.getStyleClass().add("terminal-scroll-pane");
            VBox.setVgrow(scrollPane, Priority.ALWAYS);

            // Clicking anywhere in the terminal canvas immediately focuses the shell prompt
            scrollPane.setOnMouseClicked(e -> focusInput());
            terminalContent.setOnMouseClicked(e -> focusInput());
            outputFlow.setOnMouseClicked(e -> focusInput());
            promptRow.setOnMouseClicked(e -> focusInput());

            sessionRoot.getChildren().add(scrollPane);
        }

        public VBox getNode() {
            return sessionRoot;
        }

        public String getTitle() {
            return sessionId + ": " + getShellName();
        }

        public String getWorkingDirName() {
            String name = currentDir.getName();
            return name.isEmpty() ? currentDir.getAbsolutePath() : name;
        }

        private String computePromptString() {
            PlatformOS os = getPlatformOS();
            String dir = getWorkingDirName();

            if (os == PlatformOS.WINDOWS) {
                String shell = getShellName().toLowerCase();
                if (shell.contains("power")) {
                    return "PS " + (currentDir != null ? currentDir.getAbsolutePath() : "") + "> ";
                } else {
                    return (currentDir != null ? currentDir.getAbsolutePath() : "") + "> ";
                }
            } else if (os == PlatformOS.MAC) {
                String user = System.getProperty("user.name", "user");
                String host = getCleanHostName();
                return user + "@" + host + " " + dir + " % ";
            } else { // Linux
                String user = System.getProperty("user.name", "user");
                String host = getCleanHostName();
                return user + "@" + host + " " + dir + " $ ";
            }
        }

        void setProgramRunning(boolean running) {
            this.programRunning = running;
            Platform.runLater(() -> {
                if (running) {
                    promptLabel.setText(">> ");
                    promptLabel.setStyle("-fx-text-fill: -accent-color; -fx-font-style: italic;");
                } else {
                    promptLabel.setText(computePromptString());
                    promptLabel.setStyle("-fx-text-fill: -text-primary; -fx-font-style: normal;");
                }
            });
        }

        void runForegroundProgram(List<List<String>> steps, Path workingDirectory, String displayCommand) {
            if (programRunning) {
                appendOutputText("[A program is already running. Press Ctrl+C to stop it.]\n");
                return;
            }
            File dir = workingDirectory != null ? workingDirectory.toFile() : currentDir;
            String shown = displayCommand != null && !displayCommand.isBlank()
                    ? displayCommand
                    : String.join(" && ", steps.stream().map(step -> String.join(" ", step)).toList());
            appendOutputText(promptLabel.getText() + shown + "\n");
            setProgramRunning(true);
            focusInput();

            ioExecutor.submit(() -> {
                int lastExit = 0;
                try {
                    for (int i = 0; i < steps.size(); i++) {
                        List<String> step = steps.get(i);
                        if (step == null || step.isEmpty()) continue;
                        boolean interactive = i == steps.size() - 1;
                        lastExit = runForegroundStep(step, dir, interactive);
                        if (lastExit != 0) {
                            break;
                        }
                    }
                    final int exit = lastExit;
                    Platform.runLater(() -> appendOutputText("[Process exited with code " + exit + "]\n"));
                } catch (Exception e) {
                    Platform.runLater(() -> appendOutputText("[Failed to run program: " + e.getMessage() + "]\n"));
                } finally {
                    closeProgramWriter();
                    foregroundProcess = null;
                    Platform.runLater(() -> {
                        setProgramRunning(false);
                        focusInput();
                    });
                }
            });
        }

        private int runForegroundStep(List<String> argv, File dir, boolean interactive) throws IOException, InterruptedException {
            ProcessBuilder pb = new ProcessBuilder(argv);
            if (dir != null && dir.isDirectory()) {
                pb.directory(dir);
            }
            pb.redirectErrorStream(true);
            pb.environment().put("PYTHONUNBUFFERED", "1");
            pb.environment().put("TERM", "dumb");

            Process child = pb.start();
            if (interactive) {
                foregroundProcess = child;
                programWriter = new BufferedWriter(new OutputStreamWriter(child.getOutputStream(), StandardCharsets.UTF_8));
            } else {
                try {
                    child.getOutputStream().close();
                } catch (IOException ignored) {}
            }

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(child.getInputStream(), StandardCharsets.UTF_8))) {
                char[] buf = new char[2048];
                int n;
                while ((n = reader.read(buf)) != -1) {
                    String cleaned = stripAnsi(new String(buf, 0, n));
                    if (!cleaned.isEmpty()) {
                        final String textChunk = cleaned;
                        Platform.runLater(() -> appendOutputText(textChunk));
                    }
                }
            }
            return child.waitFor();
        }

        private void closeProgramWriter() {
            BufferedWriter writer = programWriter;
            programWriter = null;
            if (writer != null) {
                try {
                    writer.close();
                } catch (IOException ignored) {}
            }
        }

        private static String getCleanHostName() {
            try {
                String host = InetAddress.getLocalHost().getHostName();
                if (host != null && !host.isBlank()) {
                    if (host.endsWith(".local")) {
                        host = host.substring(0, host.length() - 6);
                    }
                    return host;
                }
            } catch (Exception ignored) {}
            String envHost = System.getenv("HOSTNAME");
            if (envHost == null || envHost.isBlank()) envHost = System.getenv("COMPUTERNAME");
            if (envHost != null && !envHost.isBlank()) return envHost;
            return "localhost";
        }

        void start() {
            try {
                String[] shellCmd = getShellCommand();
                ProcessBuilder pb = new ProcessBuilder(shellCmd);
                pb.directory(currentDir);
                pb.redirectErrorStream(true);
                pb.environment().put("TERM", "dumb");
                pb.environment().put("NO_COLOR", "1");

                process = pb.start();
                alive = true;

                Charset charset = (getPlatformOS() == PlatformOS.WINDOWS)
                        ? Charset.defaultCharset()
                        : StandardCharsets.UTF_8;

                processWriter = new BufferedWriter(new OutputStreamWriter(process.getOutputStream(), charset));

                ioExecutor.submit(this::readOutputLoop);

                Platform.runLater(() -> {
                    promptLabel.setText(computePromptString());
                    focusInput();
                });

            } catch (IOException e) {
                appendOutputText("Failed to launch shell: " + e.getMessage() + "\n");
            }
        }

        private void readOutputLoop() {
            Charset charset = (getPlatformOS() == PlatformOS.WINDOWS)
                    ? Charset.defaultCharset()
                    : StandardCharsets.UTF_8;

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), charset))) {
                char[] buf = new char[2048];
                int n;
                while (alive && (n = reader.read(buf)) != -1) {
                    String chunk = new String(buf, 0, n);
                    String cleaned = stripAnsi(chunk);

                    // Check for internal working directory markers
                    if (cleaned.contains("__AURA_PWD__")) {
                        int idx = cleaned.indexOf("__AURA_PWD__");
                        String after = cleaned.substring(idx + "__AURA_PWD__".length()).trim();
                        String newPath = after.split("\\r?\\n")[0].trim();
                        if (!newPath.isEmpty()) {
                            File f = new File(newPath);
                            if (f.isDirectory()) {
                                currentDir = f;
                                Platform.runLater(() -> {
                                    promptLabel.setText(computePromptString());
                                    updateSessionChip();
                                });
                            }
                        }
                        cleaned = cleaned.replaceAll("__AURA_PWD__.*(\\r?\\n|$)", "");
                    }

                    if (!cleaned.isEmpty()) {
                        final String textChunk = cleaned;
                        Platform.runLater(() -> appendOutputText(textChunk));
                    }
                }
            } catch (IOException ignored) {
            } finally {
                alive = false;
            }
        }

        private static final int MAX_TERMINAL_OUTPUT_NODES = 2000;
        private static final int TERMINAL_TRIM_COUNT = 200;

        private void appendOutputText(String text) {
            if (outputFlow.getChildren().size() >= MAX_TERMINAL_OUTPUT_NODES) {
                outputFlow.getChildren().subList(0, TERMINAL_TRIM_COUNT).clear();
            }
            Text textNode = new Text(text);
            textNode.getStyleClass().add("terminal-output-text");
            outputFlow.getChildren().add(textNode);
            scrollPane.setVvalue(1.0);
        }

        private void handlePromptKey(KeyEvent event) {
            if (event.getCode() == KeyCode.ENTER) {
                String cmd = cmdInput.getText();
                cmdInput.clear();
                
                if (programRunning) {
                    // When a program is running, send input directly to the program
                    sendInputToProgram(cmd);
                } else {
                    // Normal shell command execution
                    executeCommand(cmd);
                }
                event.consume();
            } else if (event.getCode() == KeyCode.UP) {
                if (!programRunning) {
                    navigateHistory(-1);
                }
                event.consume();
            } else if (event.getCode() == KeyCode.DOWN) {
                if (!programRunning) {
                    navigateHistory(1);
                }
                event.consume();
            } else if (event.getCode() == KeyCode.C && event.isControlDown()) {
                appendOutputText(promptLabel.getText() + cmdInput.getText() + "^C\n");
                cmdInput.clear();
                interrupt();
                event.consume();
            } else if (event.getCode() == KeyCode.L && event.isControlDown()) {
                clearOutput();
                event.consume();
            }
        }

        private void sendInputToProgram(String input) {
            BufferedWriter writer = programWriter;
            if (writer == null) {
                appendOutputText("[Waiting for the program to start. Try again in a moment.]\n");
                return;
            }
            try {
                appendOutputText(input + "\n");
                writer.write(input);
                writer.newLine();
                writer.flush();
            } catch (IOException e) {
                appendOutputText("[Failed to send input: " + e.getMessage() + "]\n");
            }
        }

        private void executeCommand(String command) {
            if (command == null) command = "";
            String trimmed = command.trim();

            if (!trimmed.isEmpty()) {
                commandHistory.add(command);
                historyIndex = commandHistory.size();
            }

            // Immediately echo prompt + command to the terminal output stream
            appendOutputText(promptLabel.getText() + command + "\n");

            if (trimmed.equals("clear") || trimmed.equals("cls")) {
                clearOutput();
                return;
            }

            // Local directory resolution for fast feedback
            if (trimmed.startsWith("cd ") || trimmed.equals("cd")) {
                String target = trimmed.length() > 2 ? trimmed.substring(2).trim() : "";
                if (target.isEmpty() || target.equals("~")) {
                    currentDir = new File(System.getProperty("user.home"));
                } else if (target.equals("..")) {
                    if (currentDir.getParentFile() != null) currentDir = currentDir.getParentFile();
                } else {
                    File next = new File(currentDir, target);
                    if (next.isDirectory()) currentDir = next;
                    else if (new File(target).isDirectory()) currentDir = new File(target);
                }
                promptLabel.setText(computePromptString());
                updateSessionChip();
            }

            if (!alive || processWriter == null) {
                start();
            }

            try {
                PlatformOS os = getPlatformOS();
                String sendCmd;
                if (os == PlatformOS.WINDOWS) {
                    String shell = getShellName().toLowerCase();
                    if (shell.contains("power")) {
                        sendCmd = command + "; Write-Output \"__AURA_PWD__ $((Get-Location).Path)\"";
                    } else {
                        sendCmd = command + " & echo __AURA_PWD__ %CD%";
                    }
                } else {
                    sendCmd = command + "; echo \"__AURA_PWD__ $PWD\"";
                }

                processWriter.write(sendCmd);
                processWriter.newLine();
                processWriter.flush();
            } catch (IOException e) {
                appendOutputText("[Failed to send command: " + e.getMessage() + "]\n");
            }
        }

        private void navigateHistory(int direction) {
            if (commandHistory.isEmpty()) return;
            historyIndex += direction;
            if (historyIndex < 0) historyIndex = 0;
            if (historyIndex >= commandHistory.size()) {
                historyIndex = commandHistory.size();
                cmdInput.clear();
                return;
            }
            cmdInput.setText(commandHistory.get(historyIndex));
            cmdInput.positionCaret(cmdInput.getText().length());
        }

        void clearOutput() {
            Platform.runLater(() -> {
                outputFlow.getChildren().clear();
                scrollPane.setVvalue(0.0);
            });
        }

        void focusInput() {
            cmdInput.requestFocus();
        }

        void interrupt() {
            Process child = foregroundProcess;
            if (child != null && child.isAlive()) {
                ioExecutor.submit(() -> {
                    child.destroy();
                    try {
                        if (!child.waitFor(300, TimeUnit.MILLISECONDS)) {
                            child.destroyForcibly();
                        }
                    } catch (InterruptedException e) {
                        child.destroyForcibly();
                        Thread.currentThread().interrupt();
                    }
                });
                return;
            }
            PlatformOS os = getPlatformOS();
            if (processWriter != null) {
                try {
                    processWriter.write(os == PlatformOS.WINDOWS ? "\u0003\r\n" : "\u0003\n");
                    processWriter.flush();
                } catch (IOException ignored) {}
            }
        }

        void destroy() {
            alive = false;
            closeProgramWriter();
            if (foregroundProcess != null) {
                foregroundProcess.destroyForcibly();
                foregroundProcess = null;
            }
            if (processWriter != null) {
                try { processWriter.close(); } catch (IOException ignored) {}
            }
            if (process != null) {
                process.destroy();
                try {
                    if (!process.waitFor(300, java.util.concurrent.TimeUnit.MILLISECONDS)) {
                        process.destroyForcibly();
                    }
                } catch (InterruptedException e) {
                    process.destroyForcibly();
                    Thread.currentThread().interrupt();
                }
            }
        }
    }

    private static String stripAnsi(String text) {
        return text.replaceAll("\\x1B\\[[0-9;]*[a-zA-Z]", "")
                   .replaceAll("\\x1B\\][^\u0007]*\u0007", "")
                   .replaceAll("\\x1B\\(B", "")
                   .replaceAll("\\x1B=", "")
                   .replaceAll("\\x1B>", "")
                   .replaceAll("\\x1B\\[\\?[0-9;]*[a-zA-Z]", "");
    }
}
