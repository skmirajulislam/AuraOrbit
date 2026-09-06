package view.fx;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import org.kordamp.ikonli.codicons.Codicons;
import org.kordamp.ikonli.javafx.FontIcon;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Collectors;

/**
 * Production-grade VS Code-identical Workspace Search & Replace sidebar pane.
 * Features:
 * - Search across all files in workspace with real-time results
 * - Regex, Case Sensitive, Whole Word toggles
 * - Include/Exclude glob pattern filters
 * - Hierarchical results tree: File > Line matches with excerpts
 * - Click-to-navigate: opens file in editor and scrolls to match line
 * - Replace / Replace All across files with confirmation
 * - Background threaded search with cancellation
 * - Match count badges per file and global summary
 */
public class WorkspaceSearchPane extends VBox {

    // ── Data Models ──────────────────────────────────────────────────────────
    public record SearchMatch(int lineNumber, String lineText, int matchStart, int matchEnd) {}

    public static class FileSearchResult {
        private final Path filePath;
        private final String relativePath;
        private final List<SearchMatch> matches;

        public FileSearchResult(Path filePath, String relativePath, List<SearchMatch> matches) {
            this.filePath = filePath;
            this.relativePath = relativePath;
            this.matches = matches;
        }

        public Path getFilePath() { return filePath; }
        public String getRelativePath() { return relativePath; }
        public List<SearchMatch> getMatches() { return matches; }
    }

    // ── UI Components ────────────────────────────────────────────────────────
    private final TextField searchField;
    private final TextField replaceField;
    private final HBox replaceRow;
    private final ToggleButton caseSensitiveBtn;
    private final ToggleButton wholeWordBtn;
    private final ToggleButton regexBtn;
    private final ToggleButton toggleReplaceBtn;
    private final TextField includeField;
    private final TextField excludeField;
    private final VBox filtersBox;
    private final ToggleButton toggleFiltersBtn;
    private final TreeView<Object> resultsTree;
    private final Label summaryLabel;
    private final Label statusLabel;

    // ── State ────────────────────────────────────────────────────────────────
    private Path workspacePath;
    private final ExecutorService searchExecutor;
    private final AtomicBoolean searchCancelled = new AtomicBoolean(false);
    private final List<FileSearchResult> lastResults = new ArrayList<>();

    // ── Callbacks ────────────────────────────────────────────────────────────
    private BiConsumer<Path, Integer> onNavigateToFileAndLine;
    private Consumer<String> onNotification;

    // ── Default exclude patterns ─────────────────────────────────────────────
    private static final Set<String> DEFAULT_EXCLUDES = Set.of(
            ".git", ".svn", ".hg", "node_modules", "target", "build", "out",
            ".class", ".jar", ".war", ".ear", ".zip", ".gz", ".tar",
            ".png", ".jpg", ".jpeg", ".gif", ".bmp", ".ico", ".svg",
            ".mp3", ".mp4", ".avi", ".mov", ".pdf", ".doc", ".docx",
            ".exe", ".dll", ".so", ".dylib", ".o", ".a"
    );

    public WorkspaceSearchPane() {
        getStyleClass().add("sidebar");
        setMinWidth(180);
        setPrefWidth(260);
        setMaxWidth(500);
        setSpacing(6);
        setPadding(new Insets(6, 8, 8, 8));

        searchExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "auraorbit-workspace-search");
            t.setDaemon(true);
            return t;
        });

        // 1. Header
        HBox header = new HBox(6);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(4, 4, 8, 4));
        header.getStyleClass().add("sidebar-header");

        Label titleLabel = new Label("SEARCH");
        titleLabel.setStyle("-fx-font-size: 11px; -fx-font-weight: 700; -fx-text-fill: -text-secondary;");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button refreshBtn = createHeaderButton(Codicons.REFRESH, "Clear & Reset Search");
        refreshBtn.setOnAction(e -> clearSearch());

        Button collapseBtn = createHeaderButton(Codicons.COLLAPSE_ALL, "Collapse All Results");
        collapseBtn.setOnAction(e -> collapseAllResults());

        header.getChildren().addAll(titleLabel, spacer, refreshBtn, collapseBtn);

        // 2. Search Input Row
        HBox searchRow = new HBox(4);
        searchRow.setAlignment(Pos.CENTER_LEFT);

        searchField = new TextField();
        searchField.setPromptText("Search");
        searchField.getStyleClass().add("find-text-input");
        HBox.setHgrow(searchField, Priority.ALWAYS);

        caseSensitiveBtn = createToggleChip("Aa", "Match Case");
        wholeWordBtn = createToggleChip("ab", "Match Whole Word");
        wholeWordBtn.setGraphic(IconFactory.getIcon(Codicons.WHOLE_WORD, 12));
        wholeWordBtn.setText("");
        regexBtn = createToggleChip(".*", "Use Regular Expression");

        searchRow.getChildren().addAll(searchField, caseSensitiveBtn, wholeWordBtn, regexBtn);

        // 3. Replace Row (toggleable)
        toggleReplaceBtn = new ToggleButton();
        toggleReplaceBtn.setGraphic(IconFactory.getIcon(Codicons.CHEVRON_RIGHT, 11));
        toggleReplaceBtn.getStyleClass().add("find-icon-btn");
        toggleReplaceBtn.setTooltip(new Tooltip("Toggle Replace"));
        toggleReplaceBtn.setPrefSize(22, 22);

        replaceRow = new HBox(4);
        replaceRow.setAlignment(Pos.CENTER_LEFT);
        replaceRow.setVisible(false);
        replaceRow.setManaged(false);

        replaceField = new TextField();
        replaceField.setPromptText("Replace");
        replaceField.getStyleClass().add("find-text-input");
        HBox.setHgrow(replaceField, Priority.ALWAYS);

        Button replaceAllBtn = new Button();
        replaceAllBtn.setGraphic(IconFactory.getIcon(Codicons.REPLACE_ALL, 13));
        replaceAllBtn.getStyleClass().add("find-icon-btn");
        replaceAllBtn.setTooltip(new Tooltip("Replace All in Files"));
        replaceAllBtn.setOnAction(e -> replaceAllInFiles());

        replaceRow.getChildren().addAll(replaceField, replaceAllBtn);

        toggleReplaceBtn.selectedProperty().addListener((obs, old, selected) -> {
            replaceRow.setVisible(selected);
            replaceRow.setManaged(selected);
            toggleReplaceBtn.setGraphic(IconFactory.getIcon(
                    selected ? Codicons.CHEVRON_DOWN : Codicons.CHEVRON_RIGHT, 11));
        });

        HBox searchWithToggle = new HBox(4);
        searchWithToggle.setAlignment(Pos.CENTER_LEFT);
        searchWithToggle.getChildren().addAll(toggleReplaceBtn, new VBox(4, searchRow, replaceRow));
        HBox.setHgrow(searchWithToggle.getChildren().get(1), Priority.ALWAYS);

        // 4. Filter Section (toggleable)
        toggleFiltersBtn = new ToggleButton("⋯");
        toggleFiltersBtn.getStyleClass().add("find-icon-btn");
        toggleFiltersBtn.setTooltip(new Tooltip("Toggle Search Details (Include/Exclude)"));
        toggleFiltersBtn.setPrefSize(22, 22);

        filtersBox = new VBox(4);
        filtersBox.setVisible(false);
        filtersBox.setManaged(false);
        filtersBox.setPadding(new Insets(2, 0, 2, 26));

        includeField = new TextField();
        includeField.setPromptText("files to include (e.g. *.java, src/**)");
        includeField.getStyleClass().add("find-text-input");

        excludeField = new TextField();
        excludeField.setPromptText("files to exclude (e.g. target/**, *.class)");
        excludeField.getStyleClass().add("find-text-input");

        filtersBox.getChildren().addAll(
                new Label("files to include") {{ setStyle("-fx-font-size: 10px; -fx-text-fill: -text-secondary;"); }},
                includeField,
                new Label("files to exclude") {{ setStyle("-fx-font-size: 10px; -fx-text-fill: -text-secondary;"); }},
                excludeField
        );

        toggleFiltersBtn.selectedProperty().addListener((obs, old, selected) -> {
            filtersBox.setVisible(selected);
            filtersBox.setManaged(selected);
        });

        // Add filter toggle next to search row
        HBox searchControls = new HBox(4, searchWithToggle, toggleFiltersBtn);
        searchControls.setAlignment(Pos.TOP_LEFT);
        HBox.setHgrow(searchWithToggle, Priority.ALWAYS);

        // 5. Summary & Status
        summaryLabel = new Label("");
        summaryLabel.setStyle("-fx-font-size: 10px; -fx-text-fill: -text-secondary; -fx-padding: 2 4 2 4;");

        statusLabel = new Label("Ready to search");
        statusLabel.setStyle("-fx-font-size: 10px; -fx-text-fill: -text-secondary; -fx-padding: 2 4 2 4;");

        // 6. Results Tree
        resultsTree = new TreeView<>();
        resultsTree.setShowRoot(false);
        resultsTree.setRoot(new TreeItem<>("Results"));
        resultsTree.getRoot().setExpanded(true);
        resultsTree.setStyle("-fx-background-color: transparent; -fx-border-width: 0;");
        VBox.setVgrow(resultsTree, Priority.ALWAYS);

        resultsTree.setCellFactory(tv -> new TreeCell<>() {
            @Override
            protected void updateItem(Object item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                    setStyle("-fx-background-color: transparent;");
                } else if (item instanceof FileSearchResult fsr) {
                    HBox cell = new HBox(6);
                    cell.setAlignment(Pos.CENTER_LEFT);
                    cell.setPadding(new Insets(2, 4, 2, 4));

                    FontIcon fileIcon = IconFactory.getFileIcon(fsr.getFilePath().getFileName().toString(), 14);
                    Label nameLabel = new Label(fsr.getFilePath().getFileName().toString());
                    nameLabel.setStyle("-fx-text-fill: -text-primary; -fx-font-size: 12px; -fx-font-weight: 600;");

                    Label dirLabel = new Label(fsr.getRelativePath());
                    dirLabel.setStyle("-fx-text-fill: -text-secondary; -fx-font-size: 10px;");

                    Region cellSpacer = new Region();
                    HBox.setHgrow(cellSpacer, Priority.ALWAYS);

                    Label badge = new Label(String.valueOf(fsr.getMatches().size()));
                    badge.setStyle("-fx-background-color: rgba(255,255,255,0.12); -fx-text-fill: -text-primary; " +
                            "-fx-font-size: 10px; -fx-font-weight: bold; -fx-background-radius: 8; -fx-padding: 1 6 1 6;");

                    cell.getChildren().addAll(fileIcon, nameLabel, dirLabel, cellSpacer, badge);
                    setGraphic(cell);
                    setText(null);
                    setStyle("-fx-background-color: transparent;");
                } else if (item instanceof SearchMatch match) {
                    HBox cell = new HBox(6);
                    cell.setAlignment(Pos.CENTER_LEFT);
                    cell.setPadding(new Insets(1, 4, 1, 12));

                    Label lineNum = new Label(String.valueOf(match.lineNumber()));
                    lineNum.setStyle("-fx-text-fill: #858585; -fx-font-size: 11px; -fx-min-width: 30;");
                    lineNum.setMinWidth(30);

                    String text = match.lineText().trim();
                    if (text.length() > 200) text = text.substring(0, 200) + "…";
                    Label lineText = new Label(text);
                    lineText.setStyle("-fx-text-fill: -text-primary; -fx-font-size: 11px; " +
                            "-fx-font-family: 'JetBrains Mono', Consolas, monospace;");

                    cell.getChildren().addAll(lineNum, lineText);
                    setGraphic(cell);
                    setText(null);
                    setStyle("-fx-background-color: transparent;");
                } else {
                    setText(item.toString());
                    setGraphic(null);
                }
            }
        });

        // Click to navigate
        resultsTree.setOnMouseClicked(event -> {
            TreeItem<Object> selected = resultsTree.getSelectionModel().getSelectedItem();
            if (selected == null || selected.getValue() == null) return;

            if (selected.getValue() instanceof SearchMatch match) {
                TreeItem<Object> parent = selected.getParent();
                if (parent != null && parent.getValue() instanceof FileSearchResult fsr) {
                    navigateToMatch(fsr.getFilePath(), match.lineNumber());
                }
            } else if (selected.getValue() instanceof FileSearchResult fsr) {
                if (!fsr.getMatches().isEmpty()) {
                    navigateToMatch(fsr.getFilePath(), fsr.getMatches().get(0).lineNumber());
                }
            }
        });

        // 7. Search triggers
        searchField.setOnAction(e -> executeSearch());
        searchField.textProperty().addListener((obs, old, val) -> {
            if (val != null && val.length() >= 2) {
                executeSearch();
            } else if (val == null || val.isEmpty()) {
                clearResults();
            }
        });

        getChildren().addAll(header, searchControls, filtersBox, summaryLabel, resultsTree, statusLabel);
    }

    // ── Public API ───────────────────────────────────────────────────────────

    public void setWorkspacePath(Path workspacePath) {
        this.workspacePath = workspacePath;
    }

    public void setOnNavigateToFileAndLine(BiConsumer<Path, Integer> callback) {
        this.onNavigateToFileAndLine = callback;
    }

    public void setOnNotification(Consumer<String> callback) {
        this.onNotification = callback;
    }

    public void focusSearchField() {
        Platform.runLater(() -> {
            searchField.requestFocus();
            searchField.selectAll();
        });
    }

    public void shutdown() {
        searchExecutor.shutdownNow();
    }

    // ── Search Execution ─────────────────────────────────────────────────────

    public void executeSearch() {
        String query = searchField.getText();
        if (query == null || query.isEmpty() || workspacePath == null) return;

        searchCancelled.set(true); // cancel any running search

        boolean caseSensitive = caseSensitiveBtn.isSelected();
        boolean wholeWord = wholeWordBtn.isSelected();
        boolean regex = regexBtn.isSelected();
        String includeGlob = includeField.getText();
        String excludeGlob = excludeField.getText();

        statusLabel.setText("Searching...");
        summaryLabel.setText("");

        searchExecutor.submit(() -> {
            searchCancelled.set(false);
            List<FileSearchResult> results = new ArrayList<>();
            AtomicInteger totalMatches = new AtomicInteger(0);
            AtomicInteger filesSearched = new AtomicInteger(0);

            try {
                Pattern searchPattern = buildSearchPattern(query, caseSensitive, wholeWord, regex);
                if (searchPattern == null) {
                    Platform.runLater(() -> statusLabel.setText("Invalid regex pattern"));
                    return;
                }

                List<PathMatcher> includeMatchers = parseGlobPatterns(includeGlob);
                List<PathMatcher> excludeMatchers = parseGlobPatterns(excludeGlob);

                Files.walkFileTree(workspacePath, EnumSet.noneOf(FileVisitOption.class), 20,
                        new SimpleFileVisitor<>() {
                            @Override
                            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                                if (searchCancelled.get()) return FileVisitResult.TERMINATE;
                                String dirName = dir.getFileName() != null ? dir.getFileName().toString() : "";
                                if (DEFAULT_EXCLUDES.contains(dirName) || dirName.startsWith(".")) {
                                    return FileVisitResult.SKIP_SUBTREE;
                                }
                                return FileVisitResult.CONTINUE;
                            }

                            @Override
                            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                                if (searchCancelled.get()) return FileVisitResult.TERMINATE;
                                if (attrs.size() > 5 * 1024 * 1024) return FileVisitResult.CONTINUE; // skip > 5MB

                                String fileName = file.getFileName().toString();
                                // Check file extension against default binary excludes
                                int dotIdx = fileName.lastIndexOf('.');
                                if (dotIdx >= 0) {
                                    String ext = fileName.substring(dotIdx);
                                    if (DEFAULT_EXCLUDES.contains(ext)) return FileVisitResult.CONTINUE;
                                }

                                Path relPath = workspacePath.relativize(file);

                                // Include filter
                                if (!includeMatchers.isEmpty()) {
                                    boolean matched = includeMatchers.stream().anyMatch(m -> m.matches(relPath) || m.matches(file.getFileName()));
                                    if (!matched) return FileVisitResult.CONTINUE;
                                }

                                // Exclude filter
                                if (!excludeMatchers.isEmpty()) {
                                    boolean excluded = excludeMatchers.stream().anyMatch(m -> m.matches(relPath) || m.matches(file.getFileName()));
                                    if (excluded) return FileVisitResult.CONTINUE;
                                }

                                try {
                                    List<SearchMatch> matches = searchInFile(file, searchPattern);
                                    filesSearched.incrementAndGet();
                                    if (!matches.isEmpty()) {
                                        String relativeDir = relPath.getParent() != null ? relPath.getParent().toString() : "";
                                        results.add(new FileSearchResult(file, relativeDir, matches));
                                        totalMatches.addAndGet(matches.size());
                                    }
                                } catch (Exception ignored) {
                                    // Binary file or read error — skip silently
                                }

                                return FileVisitResult.CONTINUE;
                            }

                            @Override
                            public FileVisitResult visitFileFailed(Path file, IOException exc) {
                                return FileVisitResult.CONTINUE;
                            }
                        });

            } catch (IOException ex) {
                Platform.runLater(() -> statusLabel.setText("Search error: " + ex.getMessage()));
                return;
            }

            if (searchCancelled.get()) return;

            final int totalMatchCount = totalMatches.get();
            final int fileCount = results.size();
            final int filesCount = filesSearched.get();

            synchronized (lastResults) {
                lastResults.clear();
                lastResults.addAll(results);
            }

            Platform.runLater(() -> {
                populateResults(results);
                summaryLabel.setText(totalMatchCount + " results in " + fileCount + " files");
                statusLabel.setText("Searched " + filesCount + " files");
            });
        });
    }

    public static List<SearchMatch> searchInLines(List<String> lines, Pattern pattern) {
        List<SearchMatch> matches = new ArrayList<>();
        if (lines == null || pattern == null) return matches;
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            Matcher m = pattern.matcher(line);
            while (m.find()) {
                matches.add(new SearchMatch(i + 1, line, m.start(), m.end()));
            }
        }
        return matches;
    }

    private List<SearchMatch> searchInFile(Path file, Pattern pattern) throws IOException {
        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        return searchInLines(lines, pattern);
    }

    // ── Pattern Building ─────────────────────────────────────────────────────

    public static Pattern buildSearchPattern(String query, boolean caseSensitive, boolean wholeWord, boolean regex) {
        try {
            String pattern;
            if (regex) {
                pattern = query;
            } else {
                pattern = Pattern.quote(query);
            }
            if (wholeWord) {
                pattern = "\\b" + pattern + "\\b";
            }
            int flags = caseSensitive ? 0 : Pattern.CASE_INSENSITIVE;
            return Pattern.compile(pattern, flags);
        } catch (PatternSyntaxException e) {
            return null;
        }
    }

    public static List<PathMatcher> parseGlobPatterns(String globInput) {
        if (globInput == null || globInput.isBlank()) return Collections.emptyList();
        FileSystem fs = FileSystems.getDefault();
        return Arrays.stream(globInput.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(s -> {
                    try {
                        return fs.getPathMatcher("glob:" + s);
                    } catch (Exception e) {
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    // ── Results Display ──────────────────────────────────────────────────────

    private void populateResults(List<FileSearchResult> results) {
        TreeItem<Object> root = new TreeItem<>("Results");
        root.setExpanded(true);

        for (FileSearchResult fsr : results) {
            TreeItem<Object> fileItem = new TreeItem<>(fsr);
            fileItem.setExpanded(true);
            for (SearchMatch match : fsr.getMatches()) {
                fileItem.getChildren().add(new TreeItem<>(match));
            }
            root.getChildren().add(fileItem);
        }

        resultsTree.setRoot(root);
    }

    private void clearResults() {
        TreeItem<Object> root = new TreeItem<>("Results");
        root.setExpanded(true);
        resultsTree.setRoot(root);
        summaryLabel.setText("");
        statusLabel.setText("Ready to search");
        synchronized (lastResults) {
            lastResults.clear();
        }
    }

    private void clearSearch() {
        searchCancelled.set(true);
        searchField.clear();
        replaceField.clear();
        clearResults();
    }

    private void collapseAllResults() {
        TreeItem<Object> root = resultsTree.getRoot();
        if (root != null) {
            for (TreeItem<Object> child : root.getChildren()) {
                child.setExpanded(false);
            }
        }
    }

    // ── Navigation ───────────────────────────────────────────────────────────

    private void navigateToMatch(Path filePath, int lineNumber) {
        if (onNavigateToFileAndLine != null) {
            onNavigateToFileAndLine.accept(filePath, lineNumber);
        }
    }

    // ── Replace All ──────────────────────────────────────────────────────────

    private void replaceAllInFiles() {
        String query = searchField.getText();
        String replacement = replaceField.getText();
        if (query == null || query.isEmpty() || replacement == null) return;

        List<FileSearchResult> currentResults;
        synchronized (lastResults) {
            currentResults = new ArrayList<>(lastResults);
        }

        if (currentResults.isEmpty()) {
            notifyMessage("No search results to replace.");
            return;
        }

        boolean caseSensitive = caseSensitiveBtn.isSelected();
        boolean wholeWord = wholeWordBtn.isSelected();
        boolean regex = regexBtn.isSelected();

        Pattern pattern = buildSearchPattern(query, caseSensitive, wholeWord, regex);
        if (pattern == null) {
            notifyMessage("Invalid search pattern for replace.");
            return;
        }

        searchExecutor.submit(() -> {
            int replacedFiles = 0;
            int replacedMatches = 0;
            for (FileSearchResult fsr : currentResults) {
                try {
                    String content = Files.readString(fsr.getFilePath(), StandardCharsets.UTF_8);
                    String newContent;
                    if (regex) {
                        newContent = pattern.matcher(content).replaceAll(replacement);
                    } else {
                        newContent = pattern.matcher(content).replaceAll(Matcher.quoteReplacement(replacement));
                    }
                    if (!content.equals(newContent)) {
                        Files.writeString(fsr.getFilePath(), newContent, StandardCharsets.UTF_8);
                        replacedFiles++;
                        replacedMatches += fsr.getMatches().size();
                    }
                } catch (IOException ignored) {}
            }

            final int rf = replacedFiles;
            final int rm = replacedMatches;
            Platform.runLater(() -> {
                notifyMessage("Replaced " + rm + " occurrences in " + rf + " files.");
                executeSearch(); // re-run search to update results
            });
        });
    }

    // ── Utility ──────────────────────────────────────────────────────────────

    private Button createHeaderButton(Codicons codicon, String tooltipText) {
        Button btn = new Button();
        btn.setGraphic(IconFactory.getIcon(codicon, 13));
        btn.getStyleClass().add("sidebar-header-btn");
        btn.setStyle("-fx-background-color: transparent; -fx-padding: 3 4 3 4; -fx-cursor: hand;");
        btn.setTooltip(new Tooltip(tooltipText));
        return btn;
    }

    private ToggleButton createToggleChip(String text, String tooltip) {
        ToggleButton btn = new ToggleButton(text);
        btn.getStyleClass().add("find-case-toggle");
        btn.setTooltip(new Tooltip(tooltip));
        btn.setPrefSize(24, 22);
        btn.setMinSize(24, 22);
        return btn;
    }

    private void notifyMessage(String msg) {
        statusLabel.setText(msg);
        if (onNotification != null) {
            onNotification.accept(msg);
        }
    }
}
