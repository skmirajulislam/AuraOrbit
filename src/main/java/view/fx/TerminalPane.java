package view.fx;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.*;
import org.kordamp.ikonli.codicons.Codicons;
import org.kordamp.ikonli.javafx.FontIcon;

import java.io.*;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.function.Supplier;

/**
 * VS Code-style Integrated Terminal Panel.
 * Spawns an OS-native shell process and pipes I/O through JavaFX controls.
 * Supports multiple terminal tabs, command history, and cross-platform shells.
 */
public class TerminalPane extends BorderPane {

    private final TabPane terminalTabPane;
    private final List<TerminalSession> sessions = new ArrayList<>();
    private final ExecutorService ioExecutor;

    private Supplier<Path> workingDirectorySupplier;
    private Runnable onCloseRequested;

    // Counter for naming terminal tabs
    private int terminalCounter = 0;

    public TerminalPane() {
        getStyleClass().add("terminal-pane");

        // Daemon thread factory for I/O reader threads
        ThreadFactory daemonFactory = r -> {
            Thread t = new Thread(r);
            t.setDaemon(true);
            t.setName("terminal-io-" + t.threadId());
            return t;
        };
        ioExecutor = Executors.newCachedThreadPool(daemonFactory);

        // Header bar
        HBox header = buildHeader();
        setTop(header);

        // Terminal tab pane for multiple terminals
        terminalTabPane = new TabPane();
        terminalTabPane.getStyleClass().add("terminal-tab-pane");
        terminalTabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.ALL_TABS);
        setCenter(terminalTabPane);

        // Start hidden by default
        setVisible(false);
        setManaged(false);
    }

    private HBox buildHeader() {
        HBox header = new HBox(8);
        header.getStyleClass().add("terminal-header");
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(4, 8, 4, 10));

        // Terminal icon + Title
        FontIcon termIcon = IconFactory.getIcon(Codicons.TERMINAL, 14);
        Label title = new Label("TERMINAL");
        title.getStyleClass().add("terminal-header-title");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        // New Terminal button
        Button newTermBtn = new Button();
        newTermBtn.setGraphic(IconFactory.getIcon(Codicons.ADD, 13));
        newTermBtn.getStyleClass().add("terminal-header-btn");
        newTermBtn.setTooltip(new Tooltip("New Terminal (Ctrl+Shift+`)"));
        newTermBtn.setOnAction(e -> createNewTerminal());

        // Split Terminal button
        Button splitBtn = new Button();
        splitBtn.setGraphic(IconFactory.getIcon(Codicons.SPLIT_HORIZONTAL, 13));
        splitBtn.getStyleClass().add("terminal-header-btn");
        splitBtn.setTooltip(new Tooltip("Split Terminal"));

        // Kill Terminal button
        Button killBtn = new Button();
        killBtn.setGraphic(IconFactory.getIcon(Codicons.TRASH, 13));
        killBtn.getStyleClass().add("terminal-header-btn");
        killBtn.setTooltip(new Tooltip("Kill Terminal"));
        killBtn.setOnAction(e -> killActiveTerminal());

        // Clear Terminal button
        Button clearBtn = new Button();
        clearBtn.setGraphic(IconFactory.getIcon(Codicons.CLEAR_ALL, 13));
        clearBtn.getStyleClass().add("terminal-header-btn");
        clearBtn.setTooltip(new Tooltip("Clear Terminal"));
        clearBtn.setOnAction(e -> clearActiveTerminal());

        // Close Panel button
        Button closeBtn = new Button();
        closeBtn.setGraphic(IconFactory.getIcon(Codicons.CHEVRON_DOWN, 13));
        closeBtn.getStyleClass().add("terminal-header-btn");
        closeBtn.setTooltip(new Tooltip("Hide Terminal Panel (Ctrl+`)"));
        closeBtn.setOnAction(e -> {
            if (onCloseRequested != null) onCloseRequested.run();
        });

        header.getChildren().addAll(termIcon, title, spacer, newTermBtn, splitBtn, killBtn, clearBtn, closeBtn);
        return header;
    }

    /**
     * Creates and shows a new terminal session tab.
     */
    public void createNewTerminal() {
        terminalCounter++;
        String shellName = detectShellName();
        String tabName = shellName + " " + terminalCounter;

        TerminalSession session = new TerminalSession(tabName, getWorkingDirectory());
        sessions.add(session);

        Tab tab = session.getTab();
        tab.setOnCloseRequest(e -> {
            session.destroy();
            sessions.remove(session);
        });

        terminalTabPane.getTabs().add(tab);
        terminalTabPane.getSelectionModel().select(tab);

        // Start the shell process
        session.start();
    }

    /**
     * Ensures at least one terminal exists and shows the panel.
     */
    public void showTerminal() {
        if (sessions.isEmpty()) {
            createNewTerminal();
        }
        setVisible(true);
        setManaged(true);
        // Focus the input field of the active terminal
        TerminalSession active = getActiveSession();
        if (active != null) {
            Platform.runLater(() -> active.focusInput());
        }
    }

    /**
     * Hides the terminal panel (does not kill sessions).
     */
    public void hideTerminal() {
        setVisible(false);
        setManaged(false);
    }

    public boolean isTerminalVisible() {
        return isVisible();
    }

    private void killActiveTerminal() {
        TerminalSession active = getActiveSession();
        if (active != null) {
            Tab tab = active.getTab();
            active.destroy();
            sessions.remove(active);
            terminalTabPane.getTabs().remove(tab);
        }
    }

    private void clearActiveTerminal() {
        TerminalSession active = getActiveSession();
        if (active != null) {
            active.clearOutput();
        }
    }

    private TerminalSession getActiveSession() {
        Tab selectedTab = terminalTabPane.getSelectionModel().getSelectedItem();
        if (selectedTab == null) return null;
        for (TerminalSession s : sessions) {
            if (s.getTab() == selectedTab) return s;
        }
        return null;
    }

    private File getWorkingDirectory() {
        if (workingDirectorySupplier != null) {
            Path wd = workingDirectorySupplier.get();
            if (wd != null && wd.toFile().isDirectory()) {
                return wd.toFile();
            }
        }
        return new File(System.getProperty("user.home"));
    }

    /**
     * Detect the user's default shell on the current OS.
     */
    private static String[] detectShellCommand() {
        String os = System.getProperty("os.name", "").toLowerCase();
        if (os.contains("win")) {
            // Prefer PowerShell if available, fallback to cmd
            String psPath = System.getenv("SystemRoot") + "\\System32\\WindowsPowerShell\\v1.0\\powershell.exe";
            if (new File(psPath).exists()) {
                return new String[]{psPath, "-NoLogo"};
            }
            return new String[]{"cmd.exe", "/Q"};
        } else {
            // macOS / Linux — use SHELL env or fallback to /bin/zsh then /bin/bash
            // Do NOT use -i (interactive) flag — ProcessBuilder has no PTY, causing TTY read errors
            String shell = System.getenv("SHELL");
            if (shell != null && new File(shell).exists()) {
                return new String[]{shell};
            }
            if (new File("/bin/zsh").exists()) {
                return new String[]{"/bin/zsh"};
            }
            return new String[]{"/bin/bash"};
        }
    }

    private static String detectShellName() {
        String[] cmd = detectShellCommand();
        String path = cmd[0];
        int sep = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
        String name = (sep >= 0) ? path.substring(sep + 1) : path;
        // Remove .exe extension on Windows
        if (name.endsWith(".exe")) {
            name = name.substring(0, name.length() - 4);
        }
        return name;
    }

    public void setWorkingDirectorySupplier(Supplier<Path> supplier) {
        this.workingDirectorySupplier = supplier;
    }

    public void setOnCloseRequested(Runnable handler) {
        this.onCloseRequested = handler;
    }

    /**
     * Gracefully destroy all terminal sessions and shut down I/O threads.
     */
    public void dispose() {
        for (TerminalSession s : sessions) {
            s.destroy();
        }
        sessions.clear();
        ioExecutor.shutdownNow();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Inner class: TerminalSession — wraps a single shell process + its UI tab
    // ─────────────────────────────────────────────────────────────────────────
    private class TerminalSession {

        private final Tab tab;
        private final TextArea outputArea;
        private final TextField inputField;
        private final List<String> commandHistory = new ArrayList<>();
        private int historyIndex = -1;

        private Process process;
        private BufferedWriter processWriter;
        private volatile boolean alive = false;

        TerminalSession(String name, File workingDir) {
            // Output area — read-only, monospace
            outputArea = new TextArea();
            outputArea.setEditable(false);
            outputArea.setWrapText(true);
            outputArea.getStyleClass().add("terminal-output");

            // Input field — command entry
            inputField = new TextField();
            inputField.getStyleClass().add("terminal-input");
            inputField.setPromptText("Type a command and press Enter...");

            // Input field key handling
            inputField.addEventFilter(KeyEvent.KEY_PRESSED, this::handleInputKey);

            // Layout: output on top, input at bottom
            HBox inputBar = new HBox(6);
            inputBar.setAlignment(Pos.CENTER_LEFT);
            inputBar.setPadding(new Insets(4, 6, 6, 6));

            Label promptLabel = new Label("❯");
            promptLabel.getStyleClass().add("terminal-prompt");
            HBox.setHgrow(inputField, Priority.ALWAYS);
            inputBar.getChildren().addAll(promptLabel, inputField);

            BorderPane sessionPane = new BorderPane();
            sessionPane.setCenter(outputArea);
            sessionPane.setBottom(inputBar);

            // Tab with Codicon terminal icon
            tab = new Tab();
            tab.setGraphic(buildTabGraphic(name));
            tab.setContent(sessionPane);

            // Store working directory for the process
            this.workingDirFile = workingDir;
        }

        private final File workingDirFile;

        private HBox buildTabGraphic(String name) {
            HBox box = new HBox(4);
            box.setAlignment(Pos.CENTER_LEFT);
            FontIcon icon = IconFactory.getIcon(Codicons.TERMINAL, 12);
            Label label = new Label(name);
            label.getStyleClass().add("terminal-tab-label");
            box.getChildren().addAll(icon, label);
            return box;
        }

        /**
         * Start the shell process and I/O reader threads.
         */
        void start() {
            try {
                String[] shellCmd = detectShellCommand();
                ProcessBuilder pb = new ProcessBuilder(shellCmd);
                pb.directory(workingDirFile);
                pb.redirectErrorStream(true); // merge stderr into stdout
                // Provide a sensible TERM value for interactive shells
                pb.environment().put("TERM", "dumb");
                pb.environment().put("NO_COLOR", "1");

                process = pb.start();
                alive = true;

                processWriter = new BufferedWriter(new OutputStreamWriter(process.getOutputStream()));

                // Background daemon thread to read process output
                ioExecutor.submit(this::readProcessOutput);

                appendOutput("Terminal started: " + String.join(" ", shellCmd) + "\n");
                appendOutput("Working directory: " + workingDirFile.getAbsolutePath() + "\n\n");

            } catch (IOException e) {
                appendOutput("Failed to start terminal: " + e.getMessage() + "\n");
            }
        }

        private void readProcessOutput() {
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                char[] buffer = new char[4096];
                int bytesRead;
                while (alive && (bytesRead = reader.read(buffer)) != -1) {
                    String chunk = new String(buffer, 0, bytesRead);
                    // Strip ANSI escape sequences for clean output
                    String cleaned = stripAnsiCodes(chunk);
                    if (!cleaned.isEmpty()) {
                        appendOutput(cleaned);
                    }
                }
            } catch (IOException e) {
                if (alive) {
                    appendOutput("\n[Terminal process ended]\n");
                }
            } finally {
                alive = false;
                Platform.runLater(() -> {
                    appendOutput("\n[Process exited]\n");
                    inputField.setDisable(true);
                    inputField.setPromptText("Terminal process has exited. Open a new terminal.");
                });
            }
        }

        /**
         * Send a command to the shell process.
         */
        void sendCommand(String command) {
            if (!alive || processWriter == null) return;

            // Add to history
            if (!command.trim().isEmpty()) {
                commandHistory.add(command);
                historyIndex = commandHistory.size();
            }

            // Echo command to terminal output
            appendOutput("❯ " + command + "\n");

            try {
                processWriter.write(command);
                processWriter.newLine();
                processWriter.flush();
            } catch (IOException e) {
                appendOutput("[Error sending command: " + e.getMessage() + "]\n");
            }
        }

        private void handleInputKey(KeyEvent event) {
            if (event.getCode() == KeyCode.ENTER) {
                String cmd = inputField.getText();
                inputField.clear();
                sendCommand(cmd);
                event.consume();
            } else if (event.getCode() == KeyCode.UP) {
                navigateHistory(-1);
                event.consume();
            } else if (event.getCode() == KeyCode.DOWN) {
                navigateHistory(1);
                event.consume();
            } else if (event.getCode() == KeyCode.L && event.isControlDown()) {
                clearOutput();
                event.consume();
            }
        }

        private void navigateHistory(int direction) {
            if (commandHistory.isEmpty()) return;
            historyIndex += direction;
            if (historyIndex < 0) historyIndex = 0;
            if (historyIndex >= commandHistory.size()) {
                historyIndex = commandHistory.size();
                inputField.clear();
                return;
            }
            inputField.setText(commandHistory.get(historyIndex));
            inputField.positionCaret(inputField.getText().length());
        }

        void appendOutput(String text) {
            Platform.runLater(() -> {
                outputArea.appendText(text);
                // Auto-scroll to bottom
                outputArea.setScrollTop(Double.MAX_VALUE);
            });
        }

        void clearOutput() {
            Platform.runLater(() -> outputArea.clear());
        }

        void focusInput() {
            inputField.requestFocus();
        }

        Tab getTab() { return tab; }

        /**
         * Kill the shell process and clean up.
         */
        void destroy() {
            alive = false;
            if (processWriter != null) {
                try { processWriter.close(); } catch (IOException ignored) {}
            }
            if (process != null) {
                process.destroy(); // graceful SIGTERM
                try {
                    // Wait briefly for graceful exit
                    if (!process.waitFor(500, java.util.concurrent.TimeUnit.MILLISECONDS)) {
                        process.destroyForcibly(); // force SIGKILL if still alive
                    }
                } catch (InterruptedException e) {
                    process.destroyForcibly();
                    Thread.currentThread().interrupt();
                }
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // ANSI code stripper
    // ─────────────────────────────────────────────────────────────────────────
    private static String stripAnsiCodes(String text) {
        // Strip standard ANSI escape sequences (CSI, OSC, etc.)
        return text.replaceAll("\\x1B\\[[0-9;]*[a-zA-Z]", "")
                   .replaceAll("\\x1B\\][^\u0007]*\u0007", "")
                   .replaceAll("\\x1B\\(B", "")
                   .replaceAll("\\x1B=", "")
                   .replaceAll("\\x1B>", "")
                   .replaceAll("\\x1B\\[\\?[0-9;]*[a-zA-Z]", "");
    }
}
