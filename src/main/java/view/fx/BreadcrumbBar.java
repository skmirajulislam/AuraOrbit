package view.fx;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import org.kordamp.ikonli.codicons.Codicons;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Modern interactive Breadcrumb bar for AuraOrbit matching Antigravity / VS Code.
 * Displays real-time cursor context: folder › folder › [file icon] File.java › [class icon] Class › [method icon] method()
 * with interactive quick-jump symbol pickers.
 */
public class BreadcrumbBar extends HBox {

    public record SymbolItem(String name, String kind, int lineNumber) {}

    private static final Pattern JAVA_CLASS_PATTERN = Pattern.compile("(?:class|interface|enum|record)\\s+([a-zA-Z0-9_]+)");
    private static final Pattern JAVA_METHOD_PATTERN = Pattern.compile("(?:public|private|protected|static|final|\\s)+[\\w<>,\\[\\]\\s]+\\s+([a-zA-Z0-9_]+)\\s*\\([^)]*\\)\\s*(?:throws\\s+[\\w,\\s]+)?\\s*\\{");
    private static final Pattern PY_CLASS_PATTERN = Pattern.compile("^\\s*class\\s+([a-zA-Z0-9_]+)");
    private static final Pattern PY_DEF_PATTERN = Pattern.compile("^\\s*def\\s+([a-zA-Z0-9_]+)\\s*\\(");
    private static final Pattern JS_FUNCTION_PATTERN = Pattern.compile("(?:function\\s+([a-zA-Z0-9_]+)|(?:const|let|var)\\s+([a-zA-Z0-9_]+)\\s*=\\s*(?:async\\s*)?(?:\\([^)]*\\)|[a-zA-Z0-9_]+)\\s*=>)");
    private static final Pattern MD_HEADING_PATTERN = Pattern.compile("^(#{1,6})\\s+(.+)");

    private final HBox pathBox;
    private final HBox symbolBox;
    private final List<SymbolItem> symbols = new ArrayList<>();
    private Path currentFilePath;
    private String currentFileType = "";
    private int currentCaretLine = 1;
    private Consumer<Integer> onNavigateToLine;

    public BreadcrumbBar() {
        setAlignment(Pos.CENTER_LEFT);
        setPadding(new Insets(0, 10, 0, 10));
        setSpacing(2);
        getStyleClass().add("breadcrumb-bar");

        this.pathBox = new HBox(2);
        this.pathBox.setAlignment(Pos.CENTER_LEFT);

        this.symbolBox = new HBox(2);
        this.symbolBox.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(symbolBox, Priority.ALWAYS);

        getChildren().addAll(pathBox, symbolBox);

        setVisible(true);
        setManaged(true);

        rebuildPath();
        rebuildSymbols();
    }

    public void setOnNavigateToLine(Consumer<Integer> callback) {
        this.onNavigateToLine = callback;
    }

    public void updateFilePath(Path filePath, String fileType) {
        this.currentFilePath = filePath;
        this.currentFileType = (fileType != null) ? fileType.toLowerCase() : "";
        rebuildPath();
        rebuildSymbols();
    }

    /**
     * Scans document and extracts symbols.
     */
    public void indexSymbols(String content, String fileType) {
        symbols.clear();
        this.currentFileType = (fileType != null) ? fileType.toLowerCase() : "";
        if (content != null && !content.isEmpty()) {
            String[] lines = content.split("\\R", -1);
            String ft = currentFileType;

            for (int i = 0; i < lines.length; i++) {
                String line = lines[i];
                int lineNum = i + 1;

                if (ft.equals("java")) {
                    Matcher cm = JAVA_CLASS_PATTERN.matcher(line);
                    if (cm.find()) {
                        symbols.add(new SymbolItem(cm.group(1), "class", lineNum));
                    }
                    Matcher mm = JAVA_METHOD_PATTERN.matcher(line);
                    if (mm.find()) {
                        String name = mm.group(1);
                        if (!name.equals("if") && !name.equals("while") && !name.equals("for") && !name.equals("switch") && !name.equals("catch")) {
                            symbols.add(new SymbolItem(name + "()", "method", lineNum));
                        }
                    }
                } else if (ft.equals("python") || ft.equals("py")) {
                    Matcher cm = PY_CLASS_PATTERN.matcher(line);
                    if (cm.find()) {
                        symbols.add(new SymbolItem(cm.group(1), "class", lineNum));
                    }
                    Matcher dm = PY_DEF_PATTERN.matcher(line);
                    if (dm.find()) {
                        symbols.add(new SymbolItem(dm.group(1) + "()", "method", lineNum));
                    }
                } else if (ft.contains("js") || ft.contains("ts")) {
                    Matcher jm = JS_FUNCTION_PATTERN.matcher(line);
                    if (jm.find()) {
                        String name = jm.group(1) != null ? jm.group(1) : jm.group(2);
                        if (name != null) {
                            symbols.add(new SymbolItem(name + "()", "function", lineNum));
                        }
                    }
                } else if (ft.equals("markdown") || ft.equals("md")) {
                    Matcher mdm = MD_HEADING_PATTERN.matcher(line);
                    if (mdm.find()) {
                        symbols.add(new SymbolItem(mdm.group(2), "heading", lineNum));
                    }
                }
            }
        }

        rebuildSymbols();
    }

    /**
     * Updates active symbol text based on caret line.
     */
    public void updateActiveCaretLine(int caretLine1Indexed) {
        this.currentCaretLine = caretLine1Indexed;
        rebuildSymbols();
    }

    private void rebuildPath() {
        pathBox.getChildren().clear();

        if (currentFilePath == null) {
            Label untitledLabel = new Label("untitled");
            untitledLabel.getStyleClass().addAll("breadcrumb-segment", "breadcrumb-active");
            pathBox.getChildren().add(untitledLabel);
            return;
        }

        // Extract relative folder path segments
        List<String> folderSegments = new ArrayList<>();
        Path p = currentFilePath.getParent();
        int maxLevels = 6;
        while (p != null && p.getFileName() != null && folderSegments.size() < maxLevels) {
            folderSegments.add(0, p.getFileName().toString());
            p = p.getParent();
        }

        // Add folder segments
        for (String folder : folderSegments) {
            Label segLabel = new Label(folder);
            segLabel.getStyleClass().add("breadcrumb-segment");
            pathBox.getChildren().add(segLabel);

            Label sep = new Label("\u203A");
            sep.getStyleClass().add("breadcrumb-separator");
            pathBox.getChildren().add(sep);
        }

        // Add File segment with icon
        String fileName = currentFilePath.getFileName().toString();
        Label fileSeg = new Label(fileName);
        fileSeg.setGraphic(IconFactory.getFileIcon(fileName, 13));
        fileSeg.setGraphicTextGap(5);
        fileSeg.getStyleClass().addAll("breadcrumb-segment", "breadcrumb-file", "breadcrumb-active");
        pathBox.getChildren().add(fileSeg);
    }

    private void rebuildSymbols() {
        symbolBox.getChildren().clear();

        // Find active symbols for current line
        SymbolItem activeClass = null;
        SymbolItem activeMethod = null;
        SymbolItem activeHeading = null;

        for (SymbolItem item : symbols) {
            if (item.lineNumber <= currentCaretLine) {
                if (item.kind.equals("class")) {
                    activeClass = item;
                } else if (item.kind.equals("method") || item.kind.equals("function")) {
                    activeMethod = item;
                } else if (item.kind.equals("heading")) {
                    activeHeading = item;
                }
            } else {
                break;
            }
        }

        if (activeHeading != null) {
            Label sepHeading = new Label("\u203A");
            sepHeading.getStyleClass().add("breadcrumb-separator");
            symbolBox.getChildren().add(sepHeading);

            Button headingBtn = new Button(activeHeading.name);
            headingBtn.getStyleClass().addAll("breadcrumb-segment", "breadcrumb-symbol", "breadcrumb-active");
            headingBtn.setOnAction(e -> showSymbolMenu(headingBtn, "heading"));
            symbolBox.getChildren().add(headingBtn);
            return;
        }

        if (activeClass != null) {
            Label sepClass = new Label("\u203A");
            sepClass.getStyleClass().add("breadcrumb-separator");
            symbolBox.getChildren().add(sepClass);

            Button classBtn = new Button(activeClass.name);
            classBtn.setGraphic(IconFactory.getIcon(Codicons.SYMBOL_CLASS, 12));
            classBtn.setGraphicTextGap(4);
            classBtn.getStyleClass().addAll("breadcrumb-segment", "breadcrumb-symbol", "breadcrumb-active");
            classBtn.setOnAction(e -> showSymbolMenu(classBtn, "class"));
            symbolBox.getChildren().add(classBtn);
        }

        if (activeMethod != null) {
            Label sepMethod = new Label("\u203A");
            sepMethod.getStyleClass().add("breadcrumb-separator");
            symbolBox.getChildren().add(sepMethod);

            Button methodBtn = new Button(activeMethod.name);
            methodBtn.setGraphic(IconFactory.getIcon(Codicons.SYMBOL_METHOD, 12));
            methodBtn.setGraphicTextGap(4);
            methodBtn.getStyleClass().addAll("breadcrumb-segment", "breadcrumb-symbol", "breadcrumb-active");
            methodBtn.setOnAction(e -> showSymbolMenu(methodBtn, "method"));
            symbolBox.getChildren().add(methodBtn);
        }
    }

    private void showSymbolMenu(Button anchor, String filterKind) {
        ContextMenu menu = new ContextMenu();
        menu.getStyleClass().add("editor-context-menu");

        for (SymbolItem item : symbols) {
            if (filterKind == null || item.kind.equals(filterKind) || item.kind.equals("function") || item.kind.equals("heading")) {
                MenuItem mi = new MenuItem(item.name + " (Line " + item.lineNumber + ")");
                if (item.kind.equals("class")) {
                    mi.setGraphic(IconFactory.getIcon(Codicons.SYMBOL_CLASS, 12));
                } else if (item.kind.equals("method") || item.kind.equals("function")) {
                    mi.setGraphic(IconFactory.getIcon(Codicons.SYMBOL_METHOD, 12));
                }
                mi.setOnAction(e -> {
                    if (onNavigateToLine != null) {
                        onNavigateToLine.accept(item.lineNumber);
                    }
                });
                menu.getItems().add(mi);
            }
        }

        if (!menu.getItems().isEmpty()) {
            menu.show(anchor, javafx.geometry.Side.BOTTOM, 0, 2);
        }
    }
}
