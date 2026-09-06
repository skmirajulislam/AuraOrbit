package view.fx;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import org.kordamp.ikonli.codicons.Codicons;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * Modern VS Code Source Control (Git) sidebar pane.
 * Features:
 * - Real-time git status tracking (Modified, Added, Untracked, Deleted)
 * - File list with status badges ('M', 'A', 'U', 'D')
 * - Direct click to open file in editor
 * - Commit message input with Cmd/Ctrl+Enter shortcut and Commit button
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

        public GitChange(String statusChar, String relativePath, Path fullPath) {
            this.statusChar = statusChar;
            this.relativePath = relativePath;
            this.fullPath = fullPath;

            File f = new File(relativePath);
            this.fileName = f.getName();
            String parent = f.getParent();
            this.directoryPath = (parent != null) ? parent : "";
        }

        public String getStatusChar() { return statusChar; }
        public String getRelativePath() { return relativePath; }
        public String getFileName() { return fileName; }
        public String getDirectoryPath() { return directoryPath; }
        public Path getFullPath() { return fullPath; }
    }

    private Path workspacePath;
    private final TextArea commitInput;
    private final Button commitBtn;
    private final ListView<GitChange> changesListView;
    private final Label changesCountBadge;
    private final Label statusSummaryLabel;
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

        // 2. Commit Section: Message box + Commit button
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

        // 3. Changes Section Header: [▼ CHANGES   (3)]
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

        changesHeader.getChildren().addAll(changesChevron, changesTitle, changesSpacer, changesCountBadge);

        // 4. Changes ListView
        changesListView = new ListView<>();
        VBox.setVgrow(changesListView, Priority.ALWAYS);
        changesListView.setStyle("-fx-background-color: transparent; -fx-border-width: 0;");
        changesListView.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(GitChange item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                    setStyle("-fx-background-color: transparent;");
                } else {
                    HBox cell = new HBox(6);
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

                    Label badge = new Label(item.getStatusChar());
                    badge.setStyle(getBadgeStyle(item.getStatusChar()));

                    cell.getChildren().addAll(fileIcon, fileNameLabel, dirLabel, cellSpacer, badge);
                    setGraphic(cell);
                    setStyle("-fx-background-color: transparent;");
                }
            }
        });

        changesListView.setOnMouseClicked(event -> {
            GitChange selected = changesListView.getSelectionModel().getSelectedItem();
            if (selected != null && onOpenFileRequested != null) {
                onOpenFileRequested.accept(selected.getFullPath());
            }
        });

        // 5. Bottom Status Summary
        statusSummaryLabel = new Label("Git repository ready");
        statusSummaryLabel.setStyle("-fx-font-size: 10px; -fx-text-fill: -text-secondary; -fx-padding: 2 4 2 4;");

        getChildren().addAll(header, commitBox, changesHeader, changesListView, statusSummaryLabel);
    }

    private String getBadgeStyle(String statusChar) {
        String color;
        switch (statusChar.toUpperCase()) {
            case "M": color = "#e2c08d"; break; // Modified (amber)
            case "A": color = "#73c991"; break; // Added (green)
            case "U":
            case "?": color = "#73c991"; break; // Untracked (green)
            case "D": color = "#f14c4c"; break; // Deleted (red)
            case "R": color = "#3794ff"; break; // Renamed (blue)
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

    public void refreshGitStatus() {
        if (workspacePath == null) return;

        gitExecutor.submit(() -> {
            List<GitChange> changes = new ArrayList<>();
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

                        // Remove possible quotes in path
                        if (relPath.startsWith("\"") && relPath.endsWith("\"")) {
                            relPath = relPath.substring(1, relPath.length() - 1);
                        }

                        String badgeChar;
                        if (indexStatus == '?' || workTreeStatus == '?') {
                            badgeChar = "U";
                        } else if (indexStatus == 'A' || workTreeStatus == 'A') {
                            badgeChar = "A";
                        } else if (indexStatus == 'D' || workTreeStatus == 'D') {
                            badgeChar = "D";
                        } else if (indexStatus == 'R' || workTreeStatus == 'R') {
                            badgeChar = "R";
                        } else {
                            badgeChar = "M";
                        }

                        Path fullPath = workspacePath.resolve(relPath).toAbsolutePath().normalize();
                        changes.add(new GitChange(badgeChar, relPath, fullPath));
                    }
                }
                process.waitFor();

                Platform.runLater(() -> {
                    changesListView.getItems().setAll(changes);
                    int count = changes.size();
                    changesCountBadge.setText(String.valueOf(count));
                    statusSummaryLabel.setText(count == 0 ? "Working tree clean" : count + " uncommitted changes");
                    if (onBadgeCountChanged != null) {
                        onBadgeCountChanged.accept(count);
                    }
                });
            } catch (Exception ex) {
                Platform.runLater(() -> statusSummaryLabel.setText("Git unavailable: " + ex.getMessage()));
            }
        });
    }

    private void handleCommit() {
        String msg = commitInput.getText().trim();
        if (msg.isEmpty()) {
            notifyMessage("Please provide a commit message before committing.");
            return;
        }
        if (changesListView.getItems().isEmpty()) {
            notifyMessage("No changes to commit in current workspace.");
            return;
        }

        gitExecutor.submit(() -> {
            try {
                // 1. git add -A
                Process addProc = new ProcessBuilder("git", "add", "-A")
                        .directory(workspacePath.toFile())
                        .start();
                addProc.waitFor();

                // 2. git commit -m <msg>
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

    private void syncChanges() {
        if (workspacePath == null) return;

        notifyMessage("Syncing changes (git pull & push)...");
        gitExecutor.submit(() -> {
            try {
                // git pull --rebase
                Process pullProc = new ProcessBuilder("git", "pull", "--rebase")
                        .directory(workspacePath.toFile())
                        .start();
                pullProc.waitFor();

                // git push
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
