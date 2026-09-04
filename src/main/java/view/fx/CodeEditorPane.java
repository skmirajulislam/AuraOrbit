package view.fx;

import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.geometry.Pos;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.StackPane;
import org.fxmisc.flowless.VirtualizedScrollPane;
import org.fxmisc.richtext.CodeArea;
import org.fxmisc.richtext.LineNumberFactory;
import org.fxmisc.richtext.model.StyleSpans;
import org.fxmisc.richtext.model.StyleSpansBuilder;
import service.CodeFormatterService;

import java.time.Duration;
import java.util.Collection;
import java.util.Collections;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Modern high-performance virtualized code editor pane with VS Code-grade
 * syntax highlighting, line numbers, code formatting, and search integration.
 */
public class CodeEditorPane extends StackPane {

    private final CodeArea codeArea;
    private final FindReplaceBar findReplaceBar;
    private final ExecutorService highlightExecutor;
    private String fileType = "java";

    // 1. Control flow keywords (purple/magenta in Dark+, red in Light)
    private static final String[] CONTROL_KEYWORDS = new String[] {
            "if", "else", "switch", "case", "default", "break", "continue",
            "return", "try", "catch", "finally", "throw", "throws", "while",
            "for", "do", "yield"
    };

    // 2. Declaration & storage keywords (blue in Dark+, red in Light)
    private static final String[] KEYWORDS = new String[] {
            "abstract", "assert", "class", "const", "enum", "extends", "final",
            "goto", "implements", "import", "instanceof", "interface", "native", "new",
            "package", "private", "protected", "public", "static", "strictfp", "super",
            "synchronized", "this", "transient", "volatile", "record", "sealed",
            "permits", "non-sealed", "var", "def", "function", "let", "lambda"
    };

    // 3. Builtin primitives & types
    private static final String[] PRIMITIVE_TYPES = new String[] {
            "boolean", "byte", "char", "double", "float", "int", "long", "short", "void"
    };

    // 4. Literals & Constants
    private static final String[] CONSTANTS = new String[] {
            "true", "false", "null", "undefined", "nil", "None", "True", "False"
    };

    private static final String CONTROL_KEYWORD_PATTERN = "\\b(" + String.join("|", CONTROL_KEYWORDS) + ")\\b";
    private static final String KEYWORD_PATTERN = "\\b(" + String.join("|", KEYWORDS) + ")\\b";
    private static final String PRIMITIVE_PATTERN = "\\b(" + String.join("|", PRIMITIVE_TYPES) + ")\\b";
    private static final String CONSTANT_PATTERN = "\\b(" + String.join("|", CONSTANTS) + ")\\b";
    private static final String TYPE_PATTERN = "\\b[A-Z][a-zA-Z0-9_]*\\b";
    private static final String FUNCTION_PATTERN = "\\b([a-zA-Z_][a-zA-Z0-9_]*)(?=\\s*\\()";
    private static final String ANNOTATION_PATTERN = "@[a-zA-Z_0-9]+";
    private static final String STRING_PATTERN = "\"\"\"[\\s\\S]*?\"\"\"|\"([^\"\\\\]|\\\\.)*\"|'([^'\\\\]|\\\\.)*'";
    private static final String COMMENT_PATTERN = "//[^\\r\\n]*|/\\*[\\s\\S]*?\\*/|#[^\\r\\n]*";
    private static final String NUMBER_PATTERN = "\\b0[xX][0-9a-fA-F]+|\\b\\d+(\\.\\d+)?([eE][+-]?\\d+)?[fFdDlL]?\\b";
    private static final String PAREN_PATTERN = "\\(|\\)";
    private static final String BRACE_PATTERN = "\\{|\\}";
    private static final String BRACKET_PATTERN = "\\[|\\]";
    private static final String SEMICOLON_PATTERN = "\\;";

    private static final Pattern PATTERN = Pattern.compile(
            "(?<COMMENT>" + COMMENT_PATTERN + ")"
                    + "|(?<STRING>" + STRING_PATTERN + ")"
                    + "|(?<ANNOTATION>" + ANNOTATION_PATTERN + ")"
                    + "|(?<CONTROL>" + CONTROL_KEYWORD_PATTERN + ")"
                    + "|(?<KEYWORD>" + KEYWORD_PATTERN + ")"
                    + "|(?<CONSTANT>" + CONSTANT_PATTERN + ")"
                    + "|(?<PRIMITIVE>" + PRIMITIVE_PATTERN + ")"
                    + "|(?<FUNCTION>" + FUNCTION_PATTERN + ")"
                    + "|(?<TYPE>" + TYPE_PATTERN + ")"
                    + "|(?<NUMBER>" + NUMBER_PATTERN + ")"
                    + "|(?<PAREN>" + PAREN_PATTERN + ")"
                    + "|(?<BRACE>" + BRACE_PATTERN + ")"
                    + "|(?<BRACKET>" + BRACKET_PATTERN + ")"
                    + "|(?<SEMICOLON>" + SEMICOLON_PATTERN + ")"
    );

    public CodeEditorPane() {
        this.codeArea = new CodeArea();
        this.codeArea.getStyleClass().add("code-area");
        this.codeArea.getStyleClass().add("vscode-code-editor");
        this.codeArea.setWrapText(false);
        this.codeArea.setParagraphGraphicFactory(LineNumberFactory.get(codeArea));
        this.codeArea.setMaxWidth(Double.MAX_VALUE);
        this.codeArea.setMaxHeight(Double.MAX_VALUE);
        this.highlightExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "syntax-highlight-thread");
            t.setDaemon(true);
            return t;
        });

        // Fast responsive async debounced syntax highlighting
        this.codeArea.plainTextChanges()
                .successionEnds(Duration.ofMillis(60))
                .supplyTask(this::computeHighlightingAsync)
                .awaitLatest(codeArea.plainTextChanges())
                .filterMap(t -> {
                    if (t.isSuccess()) {
                        return Optional.of(t.get());
                    } else {
                        return Optional.empty();
                    }
                })
                .subscribe(this::applyHighlighting);

        // Find and Replace floating bar
        this.findReplaceBar = new FindReplaceBar();
        StackPane.setAlignment(findReplaceBar, Pos.TOP_RIGHT);

        setupFindReplaceActions();
        setupEditorContextMenu();

        // Keyboard Shortcut: Shift+Alt+F (Format Document)
        this.codeArea.setOnKeyPressed(e -> {
            if (e.isAltDown() && e.isShiftDown() && e.getCode() == KeyCode.F) {
                formatCode();
                e.consume();
            }
        });

        VirtualizedScrollPane<CodeArea> scrollPane = new VirtualizedScrollPane<>(codeArea);
        scrollPane.getStyleClass().add("code-scroll-pane");
        scrollPane.setMaxWidth(Double.MAX_VALUE);
        scrollPane.setMaxHeight(Double.MAX_VALUE);

        getChildren().addAll(scrollPane, findReplaceBar);
        setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        setMinSize(0, 0);
    }

    private void setupEditorContextMenu() {
        ContextMenu menu = new ContextMenu();
        menu.getStyleClass().add("editor-context-menu");

        MenuItem formatItem = new MenuItem("Format Document (Shift+Alt+F)");
        formatItem.setOnAction(e -> formatCode());

        MenuItem cutItem = new MenuItem("Cut");
        cutItem.setOnAction(e -> codeArea.cut());

        MenuItem copyItem = new MenuItem("Copy");
        copyItem.setOnAction(e -> codeArea.copy());

        MenuItem pasteItem = new MenuItem("Paste");
        pasteItem.setOnAction(e -> codeArea.paste());

        MenuItem selectAllItem = new MenuItem("Select All");
        selectAllItem.setOnAction(e -> codeArea.selectAll());

        menu.getItems().addAll(
                formatItem,
                new SeparatorMenuItem(),
                cutItem,
                copyItem,
                pasteItem,
                new SeparatorMenuItem(),
                selectAllItem
        );

        codeArea.setContextMenu(menu);
    }

    /**
     * Formats code according to active language syntax rules.
     */
    public void formatCode() {
        String currentText = codeArea.getText();
        if (currentText == null || currentText.isEmpty()) return;

        int originalCaret = codeArea.getCaretPosition();
        String formatted = CodeFormatterService.formatCode(currentText, fileType);

        if (!formatted.equals(currentText)) {
            codeArea.replaceText(formatted);
            int newCaret = Math.min(originalCaret, formatted.length());
            codeArea.moveTo(newCaret);
        }
    }

    private Task<StyleSpans<Collection<String>>> computeHighlightingAsync() {
        String text = codeArea.getText();
        Task<StyleSpans<Collection<String>>> task = new Task<>() {
            @Override
            protected StyleSpans<Collection<String>> call() {
                return computeHighlighting(text);
            }
        };
        highlightExecutor.execute(task);
        return task;
    }

    private void applyHighlighting(StyleSpans<Collection<String>> highlighting) {
        codeArea.setStyleSpans(0, highlighting);
    }

    private StyleSpans<Collection<String>> computeHighlighting(String text) {
        if (text.isEmpty()) {
            return new StyleSpansBuilder<Collection<String>>().add(Collections.emptyList(), 0).create();
        }

        // Safety limit for massive files (> 500KB) to prevent CPU spikes / regex backtracking
        if (text.length() > 500_000) {
            return new StyleSpansBuilder<Collection<String>>().add(Collections.singleton("plain"), text.length()).create();
        }

        // For plain text logs or CSV, skip syntax tokenizing
        if (fileType != null && (fileType.equals("txt") || fileType.equals("log") || fileType.equals("csv") || fileType.equals("tsv"))) {
            return new StyleSpansBuilder<Collection<String>>().add(Collections.singleton("plain"), text.length()).create();
        }

        Matcher matcher = PATTERN.matcher(text);
        int lastKwEnd = 0;
        StyleSpansBuilder<Collection<String>> spansBuilder = new StyleSpansBuilder<>();

        while (matcher.find()) {
            String styleClass =
                    matcher.group("COMMENT") != null ? "comment" :
                    matcher.group("STRING") != null ? "string" :
                    matcher.group("ANNOTATION") != null ? "annotation" :
                    matcher.group("CONTROL") != null ? "control-keyword" :
                    matcher.group("KEYWORD") != null ? "keyword" :
                    matcher.group("PRIMITIVE") != null ? "keyword" :
                    matcher.group("CONSTANT") != null ? "constant" :
                    matcher.group("FUNCTION") != null ? "function" :
                    matcher.group("TYPE") != null ? "type" :
                    matcher.group("NUMBER") != null ? "number" :
                    matcher.group("PAREN") != null ? "paren" :
                    matcher.group("BRACE") != null ? "brace" :
                    matcher.group("BRACKET") != null ? "bracket" :
                    matcher.group("SEMICOLON") != null ? "semicolon" :
                    null;

            int gap = matcher.start() - lastKwEnd;
            if (gap > 0) {
                spansBuilder.add(Collections.singleton("plain"), gap);
            }
            int matchLen = matcher.end() - matcher.start();
            if (matchLen > 0) {
                spansBuilder.add(Collections.singleton(styleClass != null ? styleClass : "plain"), matchLen);
            }
            lastKwEnd = matcher.end();
        }
        int remaining = text.length() - lastKwEnd;
        if (remaining > 0) {
            spansBuilder.add(Collections.singleton("plain"), remaining);
        }
        return spansBuilder.create();
    }

    private void setupFindReplaceActions() {
        findReplaceBar.setOnFindNext(req -> {
            int match = findText(req.query, req.matchCase, codeArea.getCaretPosition(), true);
            if (match == -1) {
                match = findText(req.query, req.matchCase, 0, true);
            }
            findReplaceBar.setResultText(match != -1 ? "Match found" : "No match");
        });

        findReplaceBar.setOnFindPrev(req -> {
            int match = findTextPrev(req.query, req.matchCase, codeArea.getCaretPosition() - 1);
            findReplaceBar.setResultText(match != -1 ? "Match found" : "No match");
        });

        findReplaceBar.setOnReplace(req -> {
            String selected = codeArea.getSelectedText();
            boolean isMatch = req.matchCase ? selected.equals(req.query) : selected.equalsIgnoreCase(req.query);
            if (isMatch) {
                codeArea.replaceSelection(req.replacement);
            }
            int match = findText(req.query, req.matchCase, codeArea.getCaretPosition(), true);
            findReplaceBar.setResultText(match != -1 ? "Replaced & found next" : "Replaced");
        });

        findReplaceBar.setOnReplaceAll(req -> {
            String fullText = codeArea.getText();
            int count = 0;
            if (req.matchCase) {
                int idx = 0;
                while ((idx = fullText.indexOf(req.query, idx)) != -1) {
                    count++;
                    idx += req.query.length();
                }
                if (count > 0) {
                    codeArea.replaceText(fullText.replace(req.query, req.replacement));
                }
            } else {
                String regex = "(?i)" + Pattern.quote(req.query);
                Matcher m = Pattern.compile(regex).matcher(fullText);
                while (m.find()) count++;
                if (count > 0) {
                    codeArea.replaceText(fullText.replaceAll(regex, Matcher.quoteReplacement(req.replacement)));
                }
            }
            findReplaceBar.setResultText(String.format("Replaced %d match(es)", count));
        });
    }

    public int findText(String query, boolean matchCase, int fromIndex, boolean select) {
        if (query == null || query.isEmpty()) return -1;
        String text = codeArea.getText();
        int textLen = text.length();
        int queryLen = query.length();
        int start = Math.max(0, fromIndex);

        int idx = -1;
        if (matchCase) {
            idx = text.indexOf(query, start);
        } else {
            // High-performance zero-allocation case-insensitive search
            int max = textLen - queryLen;
            for (int i = start; i <= max; i++) {
                if (text.regionMatches(true, i, query, 0, queryLen)) {
                    idx = i;
                    break;
                }
            }
        }

        if (idx != -1 && select) {
            codeArea.selectRange(idx, idx + queryLen);
            codeArea.showParagraphAtCenter(codeArea.getCurrentParagraph());
        }
        return idx;
    }

    public int findTextPrev(String query, boolean matchCase, int fromIndex) {
        if (query == null || query.isEmpty()) return -1;
        String text = codeArea.getText();
        int textLen = text.length();
        int queryLen = query.length();
        int start = Math.min(textLen - queryLen, fromIndex);

        int idx = -1;
        if (matchCase) {
            idx = text.lastIndexOf(query, start);
        } else {
            // High-performance zero-allocation case-insensitive reverse search
            for (int i = start; i >= 0; i--) {
                if (text.regionMatches(true, i, query, 0, queryLen)) {
                    idx = i;
                    break;
                }
            }
        }

        if (idx != -1) {
            codeArea.selectRange(idx, idx + queryLen);
            codeArea.showParagraphAtCenter(codeArea.getCurrentParagraph());
        }
        return idx;
    }

    public void showSearch(boolean withReplace) {
        String selected = codeArea.getSelectedText();
        findReplaceBar.showBar(withReplace, selected);
    }

    public CodeArea getCodeArea() {
        return codeArea;
    }

    public void setFileType(String fileType) {
        this.fileType = fileType != null ? fileType.toLowerCase() : "java";
        // Trigger immediate re-highlighting with new file type
        Platform.runLater(() -> applyHighlighting(computeHighlighting(codeArea.getText())));
    }

    public void navigateToLineAndHighlight(int line1Indexed) {
        if (line1Indexed <= 0) return;
        int pIndex = Math.max(0, line1Indexed - 1);
        if (pIndex < codeArea.getParagraphs().size()) {
            codeArea.showParagraphAtCenter(pIndex);
            int startPos = codeArea.getAbsolutePosition(pIndex, 0);
            int len = codeArea.getParagraph(pIndex).length();
            codeArea.moveTo(pIndex, 0);
            codeArea.selectRange(startPos, startPos + len);
            codeArea.requestFocus();
        }
    }

    public void dispose() {
        highlightExecutor.shutdown();
    }
}
