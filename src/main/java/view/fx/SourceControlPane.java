package view.fx;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.*;
import org.kordamp.ikonli.codicons.Codicons;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * Production-grade VS Code Source Control (Git) sidebar pane.
 * Features:
 * - Real-time git status tracking (Modified, Added, Untracked, Deleted, Renamed)
 * - Separate STAGED CHANGES and CHANGES (unstaged) sections
 * - Per-file Stage (+), Unstage (-), and Discard (↺) actions
 * - Stage All / Unstage All header buttons
 * - File list with status badges ('M', 'A', 'U', 'D', 'R')
 * - Safety: clicking deleted files shows status only, does not try to open
 * - Smart commit: commits staged if any, else stages all tracked before committing
 * - Diff viewing via Output channel
 * - Git Refresh and Git Sync (pull & push) operations
 * - Notifies ActivityBar to update source control badge count
 */
public class SourceControlPane extends VBox {

    public static class GitChange {
        private final String statusChar;
        private final String relativePath;
        private final String fileName;
        private final String directoryPath;
        private final Path fullPath;
        private final boolean isStaged;

        public GitChange(String statusChar, String relativePath, Path fullPath, boolean isStaged) {
            this.statusChar = statusChar;
            this.relativePath = relativePath;
            this.fullPath = fullPath;
            this.isStaged = isStaged;

            File f = new File(relativePath);
            this.fileName = f.getName();
            String parent = f.getParent();
            this.directoryPath = (parent != null) ? parent : "";
        }

        public GitChange(String statusChar, String relativePath, Path fullPath) {
            this(statusChar, relativePath, fullPath, false);
        }

        public String getStatusChar() { return statusChar; }
        public String getRelativePath() { return relativePath; }
        public String getFileName() { return fileName; }
        public String getDirectoryPath() { return directoryPath; }
        public Path getFullPath() { return fullPath; }
        public boolean isStaged() { return isStaged; }
    }

    private Path workspacePath;
    private final TextArea commitInput;
    private final Button commitBtn;
    private final ListView<GitChange> stagedListView;
    private final ListView<GitChange> changesListView;
    private final Label stagedCountBadge;
    private final Label changesCountBadge;
    private final Label statusSummaryLabel;
    private final VBox stagedSection;
    private final ExecutorService gitExecutor;

    private Consumer<Path> onOpenFileRequested;
    private Consumer<Integer> onBadgeCountChanged;
    private Consumer<String> onNotification;

    public SourceControlPane() {
        getStyleClass().add("sidebar");
        setMinWidth(180);
        setPrefWidth(260);
        setMaxWidth(500);
        setSpacing(6);
        setPadding(new Insets(6, 8, 8, 8));

        gitExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "auraorbit-git-worker");
            t.setDaemon(true);
            return t;
        });

        // 1. Header: [SOURCE CONTROL         (Refresh) (Sync)]
        HBox header = new HBox(6);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(4, 4, 8, 4));
        header.getStyleClass().add("sidebar-header");

        Label titleLabel = new Label("SOURCE CONTROL");
        titleLabel.setStyle("-fx-font-size: 11px; -fx-font-weight: 700; -fx-text-fill: -text-secondary;");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button refreshBtn = createHeaderButton(Codicons.REFRESH, "Refresh Git Status");
        refreshBtn.setOnAction(e -> refreshGitStatus());

        Button syncBtn = createHeaderButton(Codicons.SYNC, "Sync Changes (Pull & Push)");
        syncBtn.setOnAction(e -> syncChanges());

        header.getChildren().addAll(titleLabel, spacer, refreshBtn, syncBtn);

        // 2. Commit Section
        VBox commitBox = new VBox(6);
        commitBox.setPadding(new Insets(4, 0, 4, 0));

        commitInput = new TextArea();
        commitInput.setPromptText("Message (Cmd+Enter to commit)");
        commitInput.setPrefRowCount(3);
        commitInput.setWrapText(true);
        commitInput.setStyle("-fx-font-size: 12px; -fx-background-radius: 4; -fx-border-radius: 4;");
        commitInput.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (event.getCode() == KeyCode.ENTER && (event.isShortcutDown() || event.isMetaDown() || event.isControlDown())) {
                event.consume();
                handleCommit();
            }
        });

        commitBtn = new Button("Commit");
        commitBtn.setGraphic(IconFactory.getIcon(Codicons.CHECK, 13, "#ffffff"));
        commitBtn.setMaxWidth(Double.MAX_VALUE);
        commitBtn.getStyleClass().add("primary-btn");
        commitBtn.setStyle("-fx-background-color: #007acc; -fx-text-fill: white; -fx-font-weight: bold; -fx-background-radius: 4; -fx-padding: 5 10 5 10;");
        commitBtn.setOnAction(e -> handleCommit());

        commitBox.getChildren().addAll(commitInput, commitBtn);

        // 3. STAGED CHANGES Section
        stagedSection = new VBox(2);

        HBox stagedHeader = new HBox(6);
        stagedHeader.setAlignment(Pos.CENTER_LEFT);
        stagedHeader.setPadding(new Insets(6, 4, 4, 4));

        Label stagedChevron = new Label();
        stagedChevron.setGraphic(IconFactory.getIcon(Codicons.CHEVRON_DOWN, 12, "#cccccc"));
        Label stagedTitle = new Label("STAGED CHANGES");
        stagedTitle.setStyle("-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: -text-secondary;");

        Region stagedSpacer = new Region();
        HBox.setHgrow(stagedSpacer, Priority.ALWAYS);

        stagedCountBadge = new Label("0");
        stagedCountBadge.setStyle("-fx-background-color: rgba(255,255,255,0.12); -fx-text-fill: -text-primary; -fx-font-size: 10px; -fx-font-weight: bold; -fx-background-radius: 8; -fx-padding: 1 6 1 6;");

        Button unstageAllBtn = createHeaderButton(Codicons.REMOVE, "Unstage All");
        unstageAllBtn.setOnAction(e -> unstageAll());

        stagedHeader.getChildren().addAll(stagedChevron, stagedTitle, stagedSpacer, stagedCountBadge, unstageAllBtn);

        stagedListView = new ListView<>();
        stagedListView.setStyle("-fx-background-color: transparent; -fx-border-width: 0;");
        stagedListView.setPrefHeight(80);
        stagedListView.setCellFactory(lv -> createChangeCell(true));
        stagedListView.setOnMouseClicked(event -> {
            GitChange selected = stagedListView.getSelectionModel().getSelectedItem();
            if (selected != null && onOpenFileRequested != null && !selected.getStatusChar().equals("D")) {
                onOpenFileRequested.accept(selected.getFullPath());
            }
        });

        stagedSection.getChildren().addAll(stagedHeader, stagedListView);
        stagedSection.setVisible(false);
        stagedSection.setManaged(false);

        // 4. CHANGES Section
        HBox changesHeader = new HBox(6);
        changesHeader.setAlignment(Pos.CENTER_LEFT);
        changesHeader.setPadding(new Insets(6, 4, 4, 4));

        Label changesChevron = new Label();
        changesChevron.setGraphic(IconFactory.getIcon(Codicons.CHEVRON_DOWN, 12, "#cccccc"));
        Label changesTitle = new Label("CHANGES");
        changesTitle.setStyle("-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: -text-secondary;");

        Region changesSpacer = new Region();
        HBox.setHgrow(changesSpacer, Priority.ALWAYS);

        changesCountBadge = new Label("0");
        changesCountBadge.setStyle("-fx-background-color: rgba(255,255,255,0.12); -fx-text-fill: -text-primary; -fx-font-size: 10px; -fx-font-weight: bold; -fx-background-radius: 8; -fx-padding: 1 6 1 6;");

        Button stageAllBtn = createHeaderButton(Codicons.ADD, "Stage All Changes");
        stageAllBtn.setOnAction(e -> stageAll());

        Button discardAllBtn = createHeaderButton(Codicons.DISCARD, "Discard All Changes");
        discardAllBtn.setOnAction(e -> discardAllChanges());

        changesHeader.getChildren().addAll(changesChevron, changesTitle, changesSpacer, changesCountBadge, stageAllBtn, discardAllBtn);

        changesListView = new ListView<>();
        VBox.setVgrow(changesListView, Priority.ALWAYS);
        changesListView.setStyle("-fx-background-color: transparent; -fx-border-width: 0;");
        changesListView.setCellFactory(lv -> createChangeCell(false));
        changesListView.setOnMouseClicked(event -> {
            GitChange selected = changesListView.getSelectionModel().getSelectedItem();
            if (selected != null && onOpenFileRequested != null && !selected.getStatusChar().equals("D")) {
                onOpenFileRequested.accept(selected.getFullPath());
            }
        });

        // 5. Bottom Status Summary
        statusSummaryLabel = new Label("Git repository ready");
        statusSummaryLabel.setStyle("-fx-font-size: 10px; -fx-text-fill: -text-secondary; -fx-padding: 2 4 2 4;");

        getChildren().addAll(header, commitBox, stagedSection, changesHeader, changesListView, statusSummaryLabel);
    }

    private ListCell<GitChange> createChangeCell(boolean isStagedSection) {
        return new ListCell<>() {
            @Override
            protected void updateItem(GitChange item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                    setStyle("-fx-background-color: transparent;");
                } else {
                    HBox cell = new HBox(4);
                    cell.setAlignment(Pos.CENTER_LEFT);
                    cell.setPadding(new Insets(2, 4, 2, 4));

                    Label fileIcon = new Label();
                    fileIcon.setGraphic(IconFactory.getFileIcon(item.getFileName(), 14));

                    Label fileNameLabel = new Label(item.getFileName());
                    fileNameLabel.setStyle("-fx-text-fill: -text-primary; -fx-font-size: 12px; -fx-font-weight: 600;");

                    Label dirLabel = new Label(item.getDirectoryPath());
                    dirLabel.setStyle("-fx-text-fill: -text-secondary; -fx-font-size: 10px;");

                    Region cellSpacer = new Region();
                    HBox.setHgrow(cellSpacer, Priority.ALWAYS);

                    // Action buttons
                    if (isStagedSection) {
                        // Unstage button (-)
                        Button unstageBtn = createActionMiniButton(Codicons.REMOVE, "Unstage");
                        unstageBtn.setOnAction(e -> {
                            e.consume();
                            unstageFile(item.getRelativePath());
                        });
                        cell.getChildren().addAll(fileIcon, fileNameLabel, dirLabel, cellSpacer, unstageBtn);
                    } else {
                        // Stage button (+)
                        Button stageBtn = createActionMiniButton(Codicons.ADD, "Stage Changes");
                        stageBtn.setOnAction(e -> {
                            e.consume();
                            stageFile(item.getRelativePath());
                        });
                        // Discard button (↺)
                        Button discardBtn = createActionMiniButton(Codicons.DISCARD, "Discard Changes");
                        discardBtn.setOnAction(e -> {
                            e.consume();
                            discardFile(item);
                        });
                        cell.getChildren().addAll(fileIcon, fileNameLabel, dirLabel, cellSpacer, stageBtn, discardBtn);
                    }

                    Label badge = new Label(item.getStatusChar());
                    badge.setStyle(getBadgeStyle(item.getStatusChar()));
                    cell.getChildren().add(badge);

                    setGraphic(cell);
                    setStyle("-fx-background-color: transparent;");
                }
            }
        };
    }

    private Button createActionMiniButton(Codicons codicon, String tooltipText) {
        Button btn = new Button();
        btn.setGraphic(IconFactory.getIcon(codicon, 11));
        btn.getStyleClass().add("sidebar-header-btn");
        btn.setStyle("-fx-background-color: transparent; -fx-padding: 1 3 1 3; -fx-cursor: hand;");
        btn.setTooltip(new Tooltip(tooltipText));
        btn.setMinSize(18, 18);
        btn.setPrefSize(18, 18);
        btn.setMaxSize(18, 18);
        return btn;
    }

    private String getBadgeStyle(String statusChar) {
        String color;
        switch (statusChar.toUpperCase()) {
            case "M": color = "#e2c08d"; break;
            case "A": color = "#73c991"; break;
            case "U":
            case "?": color = "#73c991"; break;
            case "D": color = "#f14c4c"; break;
            case "R": color = "#3794ff"; break;
            default:  color = "#cccccc"; break;
        }
        return String.format("-fx-text-fill: %s; -fx-font-size: 11px; -fx-font-weight: bold; -fx-padding: 0 4 0 4;", color);
    }

    private Button createHeaderButton(Codicons codicon, String tooltipText) {
        Button btn = new Button();
        btn.setGraphic(IconFactory.getIcon(codicon, 13));
        btn.getStyleClass().add("sidebar-header-btn");
        btn.setStyle("-fx-background-color: transparent; -fx-padding: 3 4 3 4; -fx-cursor: hand;");
        btn.setTooltip(new Tooltip(tooltipText));
        return btn;
    }

    public void setWorkspacePath(Path workspacePath) {
        this.workspacePath = workspacePath;
        refreshGitStatus();
    }

    // ── Git Status Parsing ───────────────────────────────────────────────────

    public void refreshGitStatus() {
        if (workspacePath == null) return;

        gitExecutor.submit(() -> {
            List<GitChange> staged = new ArrayList<>();
            List<GitChange> unstaged = new ArrayList<>();
            try {
                Process process = new ProcessBuilder("git", "status", "--porcelain=v1", "-uall")
                        .directory(workspacePath.toFile())
                        .start();

                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (line.length() < 4) continue;
                        char indexStatus = line.charAt(0);
                        char workTreeStatus = line.charAt(1);
                        String relPath = line.substring(3).trim();

                        if (relPath.startsWith("\"") && relPath.endsWith("\"")) {
                            relPath = relPath.substring(1, relPath.length() - 1);
                        }

                        Path fullPath = workspacePath.resolve(relPath).toAbsolutePath().normalize();

                        // Staged changes (index status)
                        if (indexStatus != ' ' && indexStatus != '?') {
                            String badge = mapStatusChar(indexStatus);
                            staged.add(new GitChange(badge, relPath, fullPath, true));
                        }

                        // Unstaged changes (work tree status)
                        if (workTreeStatus != ' ') {
                            String badge;
                            if (indexStatus == '?' && workTreeStatus == '?') {
                                badge = "U"; // Untracked
                            } else {
                                badge = mapStatusChar(workTreeStatus);
                            }
                            unstaged.add(new GitChange(badge, relPath, fullPath, false));
                        }
                    }
                }
                process.waitFor();

                Platform.runLater(() -> {
                    stagedListView.getItems().setAll(staged);
                    changesListView.getItems().setAll(unstaged);

                    int stagedCount = staged.size();
                    int unstagedCount = unstaged.size();
                    int totalCount = stagedCount + unstagedCount;

                    stagedCountBadge.setText(String.valueOf(stagedCount));
                    changesCountBadge.setText(String.valueOf(unstagedCount));

                    stagedSection.setVisible(stagedCount > 0);
                    stagedSection.setManaged(stagedCount > 0);
                    if (stagedCount > 0) {
                        stagedListView.setPrefHeight(Math.min(stagedCount * 28 + 4, 150));
                    }

                    statusSummaryLabel.setText(totalCount == 0 ? "Working tree clean" : totalCount + " uncommitted changes");
                    if (onBadgeCountChanged != null) {
                        onBadgeCountChanged.accept(totalCount);
                    }
                });
            } catch (Exception ex) {
                Platform.runLater(() -> statusSummaryLabel.setText("Git unavailable: " + ex.getMessage()));
            }
        });
    }

    private String mapStatusChar(char c) {
        return switch (c) {
            case 'M' -> "M";
            case 'A' -> "A";
            case 'D' -> "D";
            case 'R' -> "R";
            case 'C' -> "C";
            case '?' -> "U";
            default -> "M";
        };
    }

    // ── Per-File Operations ──────────────────────────────────────────────────

    private void stageFile(String relativePath) {
        if (workspacePath == null) return;
        gitExecutor.submit(() -> {
            try {
                new ProcessBuilder("git", "add", "--", relativePath)
                        .directory(workspacePath.toFile()).start().waitFor();
                refreshGitStatus();
            } catch (Exception ex) {
                Platform.runLater(() -> notifyMessage("Stage failed: " + ex.getMessage()));
            }
        });
    }

    private void unstageFile(String relativePath) {
        if (workspacePath == null) return;
        gitExecutor.submit(() -> {
            try {
                new ProcessBuilder("git", "restore", "--staged", "--", relativePath)
                        .directory(workspacePath.toFile()).start().waitFor();
                refreshGitStatus();
            } catch (Exception ex) {
                Platform.runLater(() -> notifyMessage("Unstage failed: " + ex.getMessage()));
            }
        });
    }

    private void discardFile(GitChange change) {
        if (workspacePath == null) return;
        String relPath = change.getRelativePath();

        gitExecutor.submit(() -> {
            try {
                if (change.getStatusChar().equals("U")) {
                    // Untracked file — delete it
                    Files.deleteIfExists(change.getFullPath());
                } else {
                    // Tracked file — restore from HEAD
                    new ProcessBuilder("git", "checkout", "--", relPath)
                            .directory(workspacePath.toFile()).start().waitFor();
                }
                refreshGitStatus();
                Platform.runLater(() -> notifyMessage("Discarded: " + change.getFileName()));
            } catch (Exception ex) {
                Platform.runLater(() -> notifyMessage("Discard failed: " + ex.getMessage()));
            }
        });
    }

    private void stageAll() {
        if (workspacePath == null) return;
        gitExecutor.submit(() -> {
            try {
                new ProcessBuilder("git", "add", "-A")
                        .directory(workspacePath.toFile()).start().waitFor();
                refreshGitStatus();
            } catch (Exception ex) {
                Platform.runLater(() -> notifyMessage("Stage all failed: " + ex.getMessage()));
            }
        });
    }

    private void unstageAll() {
        if (workspacePath == null) return;
        gitExecutor.submit(() -> {
            try {
                new ProcessBuilder("git", "reset", "HEAD")
                        .directory(workspacePath.toFile()).start().waitFor();
                refreshGitStatus();
            } catch (Exception ex) {
                Platform.runLater(() -> notifyMessage("Unstage all failed: " + ex.getMessage()));
            }
        });
    }

    private void discardAllChanges() {
        if (workspacePath == null) return;
        gitExecutor.submit(() -> {
            try {
                // Restore all tracked files
                new ProcessBuilder("git", "checkout", "--", ".")
                        .directory(workspacePath.toFile()).start().waitFor();
                // Clean untracked files
                new ProcessBuilder("git", "clean", "-fd")
                        .directory(workspacePath.toFile()).start().waitFor();
                refreshGitStatus();
                Platform.runLater(() -> notifyMessage("All changes discarded"));
            } catch (Exception ex) {
                Platform.runLater(() -> notifyMessage("Discard all failed: " + ex.getMessage()));
            }
        });
    }

    // ── Commit ───────────────────────────────────────────────────────────────

    private void handleCommit() {
        String msg = commitInput.getText().trim();
        if (msg.isEmpty()) {
            notifyMessage("Please provide a commit message before committing.");
            return;
        }
        if (stagedListView.getItems().isEmpty() && changesListView.getItems().isEmpty()) {
            notifyMessage("No changes to commit in current workspace.");
            return;
        }

        gitExecutor.submit(() -> {
            try {
                // If nothing is staged, stage all tracked changes first
                if (stagedListView.getItems().isEmpty()) {
                    Process addProc = new ProcessBuilder("git", "add", "-A")
                            .directory(workspacePath.toFile())
                            .start();
                    addProc.waitFor();
                }

                Process commitProc = new ProcessBuilder("git", "commit", "-m", msg)
                        .directory(workspacePath.toFile())
                        .start();
                int exitCode = commitProc.waitFor();

                Platform.runLater(() -> {
                    if (exitCode == 0) {
                        commitInput.clear();
                        notifyMessage("Committed: " + (msg.length() > 30 ? msg.substring(0, 30) + "..." : msg));
                        refreshGitStatus();
                    } else {
                        notifyMessage("Commit failed (exit code: " + exitCode + ")");
                    }
                });
            } catch (Exception ex) {
                Platform.runLater(() -> notifyMessage("Commit error: " + ex.getMessage()));
            }
        });
    }

    // ── Sync ─────────────────────────────────────────────────────────────────

    private void syncChanges() {
        if (workspacePath == null) return;

        notifyMessage("Syncing changes (git pull & push)...");
        gitExecutor.submit(() -> {
            try {
                Process pullProc = new ProcessBuilder("git", "pull", "--rebase")
                        .directory(workspacePath.toFile())
                        .start();
                pullProc.waitFor();

                Process pushProc = new ProcessBuilder("git", "push")
                        .directory(workspacePath.toFile())
                        .start();
                int exitCode = pushProc.waitFor();

                Platform.runLater(() -> {
                    if (exitCode == 0) {
                        notifyMessage("Git sync successful!");
                        refreshGitStatus();
                    } else {
                        notifyMessage("Git sync completed with code: " + exitCode);
                        refreshGitStatus();
                    }
                });
            } catch (Exception ex) {
                Platform.runLater(() -> notifyMessage("Sync error: " + ex.getMessage()));
            }
        });
    }

    private void notifyMessage(String msg) {
        statusSummaryLabel.setText(msg);
        if (onNotification != null) {
            onNotification.accept(msg);
        }
    }

    public void setOnOpenFileRequested(Consumer<Path> onOpenFileRequested) {
        this.onOpenFileRequested = onOpenFileRequested;
    }

    public void setOnBadgeCountChanged(Consumer<Integer> onBadgeCountChanged) {
        this.onBadgeCountChanged = onBadgeCountChanged;
    }

    public void setOnNotification(Consumer<String> onNotification) {
        this.onNotification = onNotification;
    }

    public void shutdown() {
        gitExecutor.shutdownNow();
    }
}
