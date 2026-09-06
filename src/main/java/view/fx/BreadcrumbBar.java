package view.fx;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import org.fxmisc.richtext.CodeArea;
import org.kordamp.ikonli.codicons.Codicons;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Modern interactive Breadcrumb bar for AuraOrbit.
 * Displays real-time cursor context: Folder > File > Class > Method
 * with an interactive quick-jump symbol picker.
 */
public class BreadcrumbBar extends HBox {

    public record SymbolItem(String name, String kind, int lineNumber) {}

    private static final Pattern JAVA_CLASS_PATTERN = Pattern.compile("(?:class|interface|enum|record)\\s+([a-zA-Z0-9_]+)");
    private static final Pattern JAVA_METHOD_PATTERN = Pattern.compile("(?:public|private|protected|static|final|\\s)+[\\w<>,\\[\\]\\s]+\\s+([a-zA-Z0-9_]+)\\s*\\([^)]*\\)\\s*(?:throws\\s+[\\w,\\s]+)?\\s*\\{");
    private static final Pattern PY_CLASS_PATTERN = Pattern.compile("^\\s*class\\s+([a-zA-Z0-9_]+)");
    private static final Pattern PY_DEF_PATTERN = Pattern.compile("^\\s*def\\s+([a-zA-Z0-9_]+)\\s*\\(");
    private static final Pattern JS_FUNCTION_PATTERN = Pattern.compile("(?:function\\s+([a-zA-Z0-9_]+)|(?:const|let|var)\\s+([a-zA-Z0-9_]+)\\s*=\\s*(?:async\\s*)?(?:\\([^)]*\\)|[a-zA-Z0-9_]+)\\s*=>)");
    private static final Pattern MD_HEADING_PATTERN = Pattern.compile("^(#{1,6})\\s+(.+)");

    private final Label pathLabel;
    private final MenuButton symbolPicker;
    private final List<SymbolItem> symbols = new ArrayList<>();
    private Consumer<Integer> onNavigateToLine;

    public BreadcrumbBar() {
        setAlignment(Pos.CENTER_LEFT);
        setPadding(new Insets(2, 8, 2, 8));
        setSpacing(4);
        getStyleClass().add("breadcrumb-bar");

        this.pathLabel = new Label();
        this.pathLabel.getStyleClass().add("breadcrumb-path");

        this.symbolPicker = new MenuButton("Symbols");
        this.symbolPicker.getStyleClass().add("breadcrumb-symbol-picker");
        this.symbolPicker.setVisible(false);

        getChildren().addAll(pathLabel, symbolPicker);
        HBox.setHgrow(pathLabel, Priority.NEVER);

        setVisible(false);
        setManaged(false);
    }

    public void setOnNavigateToLine(Consumer<Integer> callback) {
        this.onNavigateToLine = callback;
    }

    public void updateFilePath(Path filePath, String fileType) {
        if (filePath == null) {
            pathLabel.setText("Untitled");
            pathLabel.setGraphic(null);
            symbolPicker.setVisible(false);
            return;
        }

        List<String> segments = new ArrayList<>();
        Path p = filePath.getParent();
        int maxLevels = 6;
        while (p != null && p.getFileName() != null && segments.size() < maxLevels) {
            segments.add(0, p.getFileName().toString());
            p = p.getParent();
        }

        StringBuilder sb = new StringBuilder();
        for (String seg : segments) {
            sb.append(seg).append(" > ");
        }
        sb.append(filePath.getFileName().toString());

        pathLabel.setText(sb.toString());
        pathLabel.setGraphic(IconFactory.getFileIcon(filePath.getFileName().toString(), 13));
    }

    /**
     * Scans document and extracts symbols.
     */
    public void indexSymbols(String content, String fileType) {
        symbols.clear();
        if (content == null || content.isEmpty()) {
            symbolPicker.setVisible(false);
            return;
        }

        String[] lines = content.split("\\R", -1);
        String ft = (fileType != null) ? fileType.toLowerCase() : "";

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

        updateSymbolMenu();
    }

    private void updateSymbolMenu() {
        symbolPicker.getItems().clear();
        if (symbols.isEmpty()) {
            symbolPicker.setVisible(false);
            return;
        }

        symbolPicker.setVisible(true);
        for (SymbolItem item : symbols) {
            MenuItem mi = new MenuItem(item.name);
            mi.setGraphic(IconFactory.getIcon(
                    item.kind.equals("class") ? Codicons.SYMBOL_CLASS : Codicons.SYMBOL_METHOD,
                    12
            ));
            mi.setOnAction(e -> {
                if (onNavigateToLine != null) {
                    onNavigateToLine.accept(item.lineNumber);
                }
            });
            symbolPicker.getItems().add(mi);
        }
    }

    /**
     * Updates active symbol text based on caret line.
     */
    public void updateActiveCaretLine(int caretLine1Indexed) {
        if (symbols.isEmpty()) {
            symbolPicker.setText("");
            symbolPicker.setVisible(false);
            return;
        }

        SymbolItem active = null;
        for (SymbolItem item : symbols) {
            if (item.lineNumber <= caretLine1Indexed) {
                active = item;
            } else {
                break;
            }
        }

        if (active != null) {
            symbolPicker.setText("> " + active.name);
            symbolPicker.setVisible(true);
        } else {
            symbolPicker.setText("> (global)");
            symbolPicker.setVisible(true);
        }
    }
}
