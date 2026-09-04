package view.fx;

import javafx.concurrent.Task;
import javafx.geometry.Pos;
import javafx.scene.layout.StackPane;
import org.fxmisc.richtext.CodeArea;
import org.fxmisc.richtext.LineNumberFactory;
import org.fxmisc.richtext.model.StyleSpans;
import org.fxmisc.richtext.model.StyleSpansBuilder;

import java.time.Duration;
import java.util.Collection;
import java.util.Collections;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Modern high-performance virtualized code editor pane with syntax highlighting,
 * line numbers, and search integration.
 */
public class CodeEditorPane extends StackPane {

    private final CodeArea codeArea;
    private final FindReplaceBar findReplaceBar;
    private final ExecutorService highlightExecutor;
    private String fileType = "txt";

    // Java Syntax Highlighting Patterns
    private static final String[] KEYWORDS = new String[] {
            "abstract", "assert", "boolean", "break", "byte", "case", "catch", "char", "class", "const",
            "continue", "default", "do", "double", "else", "enum", "extends", "final", "finally", "float",
            "for", "goto", "if", "implements", "import", "instanceof", "int", "interface", "long", "native",
            "new", "package", "private", "protected", "public", "return", "short", "static", "strictfp", "super",
            "switch", "synchronized", "this", "throw", "throws", "transient", "try", "void", "volatile", "while",
            "var", "record", "sealed", "permits", "yield", "non-sealed"
    };

    private static final String KEYWORD_PATTERN = "\\b(" + String.join("|", KEYWORDS) + ")\\b";
    private static final String PAREN_PATTERN = "\\(|\\)";
    private static final String BRACE_PATTERN = "\\{|\\}";
    private static final String BRACKET_PATTERN = "\\[|\\]";
    private static final String SEMICOLON_PATTERN = "\\;";
    private static final String STRING_PATTERN = "\"([^\"\\\\]|\\\\.)*\"|'([^'\\\\]|\\\\.)*'";
    private static final String COMMENT_PATTERN = "//[^\n]*" + "|" + "/\\*(.|\\R)*?\\*/";
    private static final String NUMBER_PATTERN = "\\b\\d+(\\.\\d+)?([eE][+-]?\\d+)?[fFdDlL]?\\b";
    private static final String ANNOTATION_PATTERN = "@[a-zA-Z_0-9]+";

    private static final Pattern PATTERN = Pattern.compile(
            "(?<KEYWORD>" + KEYWORD_PATTERN + ")"
                    + "|(?<PAREN>" + PAREN_PATTERN + ")"
                    + "|(?<BRACE>" + BRACE_PATTERN + ")"
                    + "|(?<BRACKET>" + BRACKET_PATTERN + ")"
                    + "|(?<SEMICOLON>" + SEMICOLON_PATTERN + ")"
                    + "|(?<STRING>" + STRING_PATTERN + ")"
                    + "|(?<COMMENT>" + COMMENT_PATTERN + ")"
                    + "|(?<NUMBER>" + NUMBER_PATTERN + ")"
                    + "|(?<ANNOTATION>" + ANNOTATION_PATTERN + ")"
    );

    public CodeEditorPane() {
        this.codeArea = new CodeArea();
        this.codeArea.getStyleClass().add("code-area");
        this.codeArea.setParagraphGraphicFactory(LineNumberFactory.get(codeArea));
        this.highlightExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "syntax-highlight-thread");
            t.setDaemon(true);
            return t;
        });

        // Async debounced syntax highlighting
        this.codeArea.multiPlainChanges()
                .successionEnds(Duration.ofMillis(100))
                .retainLatestUntilLater(highlightExecutor)
                .supplyTask(this::computeHighlightingAsync)
                .awaitLatest(codeArea.multiPlainChanges())
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

        getChildren().addAll(codeArea, findReplaceBar);
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

        // For plain text, logs, or simple documents, skip expensive multi-group regex matching
        if (fileType != null && (fileType.equals("txt") || fileType.equals("log") || fileType.equals("csv") || fileType.equals("tsv"))) {
            return new StyleSpansBuilder<Collection<String>>().add(Collections.emptyList(), text.length()).create();
        }

        Matcher matcher = PATTERN.matcher(text);
        int lastKwEnd = 0;
        StyleSpansBuilder<Collection<String>> spansBuilder = new StyleSpansBuilder<>();

        while (matcher.find()) {
            String styleClass =
                    matcher.group("KEYWORD") != null ? "keyword" :
                    matcher.group("STRING") != null ? "string" :
                    matcher.group("COMMENT") != null ? "comment" :
                    matcher.group("NUMBER") != null ? "number" :
                    matcher.group("ANNOTATION") != null ? "annotation" :
                    matcher.group("PAREN") != null ? "paren" :
                    matcher.group("BRACE") != null ? "brace" :
                    matcher.group("BRACKET") != null ? "bracket" :
                    matcher.group("SEMICOLON") != null ? "semicolon" :
                    null;

            spansBuilder.add(Collections.emptyList(), matcher.start() - lastKwEnd);
            spansBuilder.add(Collections.singleton(styleClass != null ? styleClass : "plain"), matcher.end() - matcher.start());
            lastKwEnd = matcher.end();
        }
        spansBuilder.add(Collections.emptyList(), text.length() - lastKwEnd);
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
        this.fileType = fileType;
    }

    public void dispose() {
        highlightExecutor.shutdown();
    }
}
