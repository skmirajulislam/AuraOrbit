package view.fx;

import javafx.animation.AnimationTimer;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.IndexRange;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.fxmisc.flowless.VirtualizedScrollPane;
import org.fxmisc.richtext.CodeArea;
import org.fxmisc.richtext.model.StyleSpans;
import org.fxmisc.richtext.model.StyleSpansBuilder;
import org.reactfx.Subscription;
import service.AutoCompleteService;
import service.CodeFormatterService;
import service.GitGutterService;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Modern JavaFX Code Editor component replacing legacy Swing views.
 * Features:
 *  - RichTextFX CodeArea with async syntax highlighting (Java, Python, JS, XML, Markdown, CSS, SQL, JSON)
 *  - VS Code-style minimap overview ruler
 *  - Breadcrumb navigation bar showing file path and language
 *  - Git gutter annotations (added, modified, deleted lines)
 *  - Native find-and-replace overlay bar
 *  - Line numbering with dynamic width
 *  - Zoom in/out support (Ctrl+Scroll / Ctrl+= / Ctrl+-)
 *  - Integrated auto-complete IntelliSense popup
 */
public class CodeEditorPane extends StackPane {

    private final CodeArea codeArea;
    private final FindReplaceBar findReplaceBar;
    private final BreadcrumbBar breadcrumbBar;
    private final MinimapPane minimapPane;
    private final AutoCompletePopup autoCompletePopup;
    private final Map<Integer, GitGutterService.GutterType> gitDiffMap = new ConcurrentHashMap<>();
    private final ExecutorService highlightExecutor;
    private Subscription highlightSubscription;
    private Subscription autoCompleteSubscription;
    private final Set<String> cachedDocumentSymbols = ConcurrentHashMap.newKeySet();
    private String fileType = "java";
    private Path currentFilePath;

    // VS Code / Antigravity Smooth Inertial Scrolling Engine
    private double targetScrollY = 0.0;
    private double targetScrollX = 0.0;
    private double currentScrollY = 0.0;
    private double currentScrollX = 0.0;
    private boolean isSmoothScrolling = false;
    private boolean smoothScrollingEnabled = true;
    private AnimationTimer smoothScrollTimer;

    // 1. Control flow keywords (purple/magenta in Dark+, red in Light)
    private static final String[] CONTROL_KEYWORDS = new String[] {
            "if", "else", "switch", "case", "default", "break", "continue",
            "return", "try", "catch", "finally", "throw", "throws", "while",
            "for", "do", "yield", "new"
    };

    // 2. Declaration & storage keywords (blue in Dark+, red in Light, cyan in Monokai)
    private static final String[] KEYWORDS = new String[] {
            "abstract", "assert", "class", "const", "enum", "extends", "final",
            "goto", "implements", "import", "instanceof", "interface", "native",
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
                    + "|(?<TYPE>" + TYPE_PATTERN + ")"
                    + "|(?<FUNCTION>" + FUNCTION_PATTERN + ")"
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
        this.codeArea.setParagraphGraphicFactory(this::createGutterGraphic);
        this.codeArea.setMaxWidth(Double.MAX_VALUE);
        this.codeArea.setMaxHeight(Double.MAX_VALUE);

        // Keep top visible line anchored when editor viewport is resized (e.g. when terminal opens/closes)
        this.codeArea.heightProperty().addListener((obs, oldH, newH) -> {
            if (oldH != null && newH != null && oldH.doubleValue() > 0 && newH.doubleValue() > 0) {
                if (!codeArea.getVisibleParagraphs().isEmpty()) {
                    int topPar = codeArea.visibleParToAllParIndex(0);
                    Platform.runLater(() -> codeArea.showParagraphAtTop(topPar));
                }
            }
        });

        this.breadcrumbBar = new BreadcrumbBar();
        this.breadcrumbBar.setOnNavigateToLine(this::navigateToLineAndHighlight);
        this.breadcrumbBar.setVisible(true);
        this.breadcrumbBar.setManaged(true);

        this.minimapPane = new MinimapPane(codeArea);
        this.minimapPane.setVisible(true);
        this.minimapPane.setManaged(true);

        this.highlightExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "syntax-highlight-thread");
            t.setDaemon(true);
            return t;
        });

        // Fast responsive async debounced syntax highlighting
        this.highlightSubscription = this.codeArea.plainTextChanges()
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

        this.autoCompletePopup = new AutoCompletePopup();

        // IntelliSense & Shortcuts Filter (Intercepts Up/Down/Tab/Enter/Esc when popup is open, plus Copy/Cut/Paste)
        this.codeArea.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
            if (autoCompletePopup.isShowing()) {
                if (e.getCode() == KeyCode.DOWN) {
                    autoCompletePopup.selectNext();
                    e.consume();
                    return;
                } else if (e.getCode() == KeyCode.UP) {
                    autoCompletePopup.selectPrevious();
                    e.consume();
                    return;
                } else if (e.getCode() == KeyCode.ENTER || e.getCode() == KeyCode.TAB) {
                    autoCompletePopup.commitSelection();
                    e.consume();
                    return;
                } else if (e.getCode() == KeyCode.ESCAPE) {
                    autoCompletePopup.hide();
                    e.consume();
                    return;
                }
            }

            // Explicit trigger via Ctrl + Space
            if (e.isControlDown() && e.getCode() == KeyCode.SPACE) {
                triggerAutoComplete(true);
                e.consume();
                return;
            }

            // Keyboard Shortcut: Shift+Alt+F (Format Document)
            if (e.isAltDown() && e.isShiftDown() && e.getCode() == KeyCode.F) {
                formatCode();
                e.consume();
                return;
            }

            // VS Code-grade Copy (Cmd+C / Ctrl+C)
            if ((e.isShortcutDown() || e.isControlDown()) && !e.isAltDown() && !e.isShiftDown() && e.getCode() == KeyCode.C) {
                copyAction();
                e.consume();
                return;
            }

            // VS Code-grade Cut (Cmd+X / Ctrl+X)
            if ((e.isShortcutDown() || e.isControlDown()) && !e.isAltDown() && !e.isShiftDown() && e.getCode() == KeyCode.X) {
                cutAction();
                e.consume();
                return;
            }

            // VS Code-grade Paste (Cmd+V / Ctrl+V)
            if ((e.isShortcutDown() || e.isControlDown()) && !e.isAltDown() && !e.isShiftDown() && e.getCode() == KeyCode.V) {
                pasteAction();
                e.consume();
                return;
            }

            // Select All (Cmd+A / Ctrl+A)
            if ((e.isShortcutDown() || e.isControlDown()) && !e.isAltDown() && !e.isShiftDown() && e.getCode() == KeyCode.A) {
                codeArea.selectAll();
                e.consume();
                return;
            }
        });

        // Trigger autocomplete dynamically as the user types without full-text string joins
        this.autoCompleteSubscription = this.codeArea.plainTextChanges()
                .filter(ch -> codeArea.isFocused())
                .subscribe(ch -> triggerAutoComplete(false));

        this.codeArea.focusedProperty().addListener((obs, wasFocused, isFocused) -> {
            if (!isFocused && autoCompletePopup.isShowing()) {
                autoCompletePopup.hide();
            }
        });

        // Active Line Highlight matching VS Code
        this.codeArea.currentParagraphProperty().addListener((obs, oldParagraph, newParagraph) -> {
            if (oldParagraph != null && oldParagraph >= 0 && oldParagraph < codeArea.getParagraphs().size()) {
                codeArea.setParagraphStyle(oldParagraph, Collections.emptyList());
            }
            if (newParagraph != null && newParagraph >= 0 && newParagraph < codeArea.getParagraphs().size()) {
                codeArea.setParagraphStyle(newParagraph, Collections.singletonList("active-line-highlight"));
                breadcrumbBar.updateActiveCaretLine(newParagraph + 1);
            }
        });

        VirtualizedScrollPane<CodeArea> scrollPane = new VirtualizedScrollPane<>(codeArea);
        scrollPane.getStyleClass().add("code-scroll-pane");
        scrollPane.setMaxWidth(Double.MAX_VALUE);
        scrollPane.setMaxHeight(Double.MAX_VALUE);
        HBox.setHgrow(scrollPane, Priority.ALWAYS);

        HBox centerBox = new HBox(0, scrollPane, minimapPane);
        centerBox.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        VBox.setVgrow(centerBox, Priority.ALWAYS);

        VBox layout = new VBox(0, breadcrumbBar, centerBox);
        layout.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);

        setupSmoothScrolling(scrollPane);

        getChildren().addAll(layout, findReplaceBar);
        setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        setMinSize(0, 0);
    }

    /**
     * Sets up VS Code / Antigravity-grade smooth inertial scrolling.
     * Intercepts raw discrete wheel/touchpad jumps, and interpolates smoothly
     * across 60/120Hz frames with exponential easing.
     */
    private void setupSmoothScrolling(VirtualizedScrollPane<CodeArea> scrollPane) {
        // Keep smooth scroll targets synchronized when viewport changes externally (minimap, search, cursor)
        codeArea.estimatedScrollYProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null && !isSmoothScrolling) {
                currentScrollY = newVal.doubleValue();
                targetScrollY = currentScrollY;
            }
        });

        codeArea.estimatedScrollXProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null && !isSmoothScrolling) {
                currentScrollX = newVal.doubleValue();
                targetScrollX = currentScrollX;
            }
        });

        smoothScrollTimer = new AnimationTimer() {
            @Override
            public void handle(long now) {
                if (!smoothScrollingEnabled) {
                    isSmoothScrolling = false;
                    stop();
                    return;
                }

                double diffY = targetScrollY - currentScrollY;
                double diffX = targetScrollX - currentScrollX;

                // Stop if we have arrived close enough (< 0.25px)
                if (Math.abs(diffY) < 0.25 && Math.abs(diffX) < 0.25) {
                    currentScrollY = targetScrollY;
                    currentScrollX = targetScrollX;
                    if (targetScrollY <= 0.0) {
                        codeArea.showParagraphAtTop(0);
                    } else {
                        codeArea.scrollYToPixel(currentScrollY);
                    }
                    codeArea.scrollXToPixel(currentScrollX);
                    isSmoothScrolling = false;
                    stop();
                    return;
                }

                // Smooth exponential easing (VS Code standard lerp factor: ~0.25)
                double stepY = diffY * 0.25;
                double stepX = diffX * 0.25;

                // Ensure minimum velocity step so we don't stall asymptotically on subpixels
                if (Math.abs(stepY) < 0.25 && Math.abs(diffY) >= 0.25) {
                    stepY = Math.signum(diffY) * 0.25;
                }
                if (Math.abs(stepX) < 0.25 && Math.abs(diffX) >= 0.25) {
                    stepX = Math.signum(diffX) * 0.25;
                }

                currentScrollY += stepY;
                currentScrollX += stepX;

                if (currentScrollY <= 0.0) {
                    codeArea.showParagraphAtTop(0);
                } else {
                    codeArea.scrollYToPixel(currentScrollY);
                }
                codeArea.scrollXToPixel(currentScrollX);
            }
        };

        // Intercept scroll events before Flowless VirtualFlow handles them
        scrollPane.addEventFilter(ScrollEvent.SCROLL, this::handleSmoothScrollEvent);
    }

    private void handleSmoothScrollEvent(ScrollEvent event) {
        if (!smoothScrollingEnabled) {
            return;
        }

        double deltaY = event.getDeltaY();
        double deltaX = event.getDeltaX();

        if (deltaY == 0 && deltaX == 0) {
            return;
        }

        // Shift + Wheel converts vertical scroll to horizontal scroll
        if (event.isShiftDown() && deltaX == 0 && deltaY != 0) {
            deltaX = deltaY;
            deltaY = 0;
        }

        // 1. Direct gesture events (macOS precision trackpad):
        // macOS hardware & OS provides native 120Hz subpixel momentum scrolling.
        // Let VirtualizedScrollPane handle it naturally without artificial overrides or event queue congestion.
        if (event.isDirect()) {
            if (isSmoothScrolling && smoothScrollTimer != null) {
                isSmoothScrolling = false;
                smoothScrollTimer.stop();
            }
            return;
        }

        event.consume();

        // 2. Discrete mouse wheel notches: smooth lerp interpolation
        double estLineHeight = 22.0;
        double totalLinesHeight = codeArea.getParagraphs().size() * estLineHeight;
        double rawEstimate = codeArea.totalHeightEstimateProperty().getValue() != null
                ? codeArea.totalHeightEstimateProperty().getValue() : totalLinesHeight;
        double contentHeight = Math.max(totalLinesHeight, rawEstimate);
        double viewportHeight = codeArea.getHeight();
        double maxScrollY = Math.max(0.0, contentHeight - viewportHeight);

        double contentWidth = codeArea.totalWidthEstimateProperty().getValue() != null
                ? codeArea.totalWidthEstimateProperty().getValue() : 0.0;
        double viewportWidth = codeArea.getWidth();
        double maxScrollX = Math.max(0.0, contentWidth - viewportWidth);

        if (!isSmoothScrolling) {
            Double curY = codeArea.estimatedScrollYProperty().getValue();
            currentScrollY = (curY != null) ? curY : 0.0;
            targetScrollY = currentScrollY;

            Double curX = codeArea.estimatedScrollXProperty().getValue();
            currentScrollX = (curX != null) ? curX : 0.0;
            targetScrollX = currentScrollX;
        }

        targetScrollY -= deltaY * 1.5;
        targetScrollX -= deltaX * 1.3;

        // Clamp within legal boundaries (allowing 0.0 to reach line 1 cleanly)
        targetScrollY = Math.max(0.0, Math.min(targetScrollY, maxScrollY));
        targetScrollX = Math.max(0.0, Math.min(targetScrollX, maxScrollX));

        if (!isSmoothScrolling) {
            isSmoothScrolling = true;
            smoothScrollTimer.start();
        }
    }

    public boolean isSmoothScrollingEnabled() {
        return smoothScrollingEnabled;
    }

    public void setSmoothScrollingEnabled(boolean enabled) {
        this.smoothScrollingEnabled = enabled;
        if (!enabled && isSmoothScrolling && smoothScrollTimer != null) {
            isSmoothScrolling = false;
            smoothScrollTimer.stop();
        }
    }

    public double getTargetScrollY() {
        return targetScrollY;
    }

    public double getTargetScrollX() {
        return targetScrollX;
    }

    public boolean isSmoothScrollingActive() {
        return isSmoothScrolling;
    }

    /**
     * VS Code-grade Copy: copies selection or whole line if empty.
     */
    public void copyAction() {
        IndexRange selection = codeArea.getSelection();
        if (selection.getLength() > 0) {
            String selected = codeArea.getSelectedText();
            ClipboardContent content = new ClipboardContent();
            content.putString(selected);
            Clipboard.getSystemClipboard().setContent(content);
        } else {
            int currentPar = codeArea.getCurrentParagraph();
            if (currentPar >= 0 && currentPar < codeArea.getParagraphs().size()) {
                String lineText = codeArea.getParagraph(currentPar).getText() + "\n";
                ClipboardContent content = new ClipboardContent();
                content.putString(lineText);
                Clipboard.getSystemClipboard().setContent(content);
            }
        }
    }

    /**
     * VS Code-grade Cut: cuts selection or whole line if empty.
     */
    public void cutAction() {
        IndexRange selection = codeArea.getSelection();
        if (selection.getLength() > 0) {
            String selected = codeArea.getSelectedText();
            ClipboardContent content = new ClipboardContent();
            content.putString(selected);
            Clipboard.getSystemClipboard().setContent(content);
            codeArea.replaceSelection("");
        } else {
            int currentPar = codeArea.getCurrentParagraph();
            if (currentPar >= 0 && currentPar < codeArea.getParagraphs().size()) {
                String lineText = codeArea.getParagraph(currentPar).getText() + "\n";
                ClipboardContent content = new ClipboardContent();
                content.putString(lineText);
                Clipboard.getSystemClipboard().setContent(content);

                int start = codeArea.getAbsolutePosition(currentPar, 0);
                int end;
                if (currentPar < codeArea.getParagraphs().size() - 1) {
                    end = codeArea.getAbsolutePosition(currentPar + 1, 0);
                } else {
                    end = start + codeArea.getParagraph(currentPar).length();
                }
                codeArea.deleteText(start, Math.min(end, codeArea.getLength()));
            }
        }
    }

    /**
     * VS Code-grade Paste: normalizes line breaks and pastes at caret / replaces selection.
     */
    public void pasteAction() {
        Clipboard clipboard = Clipboard.getSystemClipboard();
        if (clipboard.hasString()) {
            String text = clipboard.getString();
            if (text != null && !text.isEmpty()) {
                text = text.replace("\r\n", "\n").replace('\r', '\n');
                IndexRange selection = codeArea.getSelection();
                if (selection.getLength() > 0) {
                    codeArea.replaceSelection(text);
                } else {
                    int caret = codeArea.getCaretPosition();
                    codeArea.insertText(caret, text);
                }
                codeArea.requestFollowCaret();
            }
        }
    }

    private void setupEditorContextMenu() {
        ContextMenu menu = new ContextMenu();
        menu.getStyleClass().add("editor-context-menu");

        MenuItem formatItem = new MenuItem("Format Document (Shift+Alt+F)");
        formatItem.setOnAction(e -> formatCode());

        MenuItem cutItem = new MenuItem("Cut");
        cutItem.setAccelerator(new KeyCodeCombination(KeyCode.X, KeyCombination.SHORTCUT_DOWN));
        cutItem.setOnAction(e -> cutAction());

        MenuItem copyItem = new MenuItem("Copy");
        copyItem.setAccelerator(new KeyCodeCombination(KeyCode.C, KeyCombination.SHORTCUT_DOWN));
        copyItem.setOnAction(e -> copyAction());

        MenuItem pasteItem = new MenuItem("Paste");
        pasteItem.setAccelerator(new KeyCodeCombination(KeyCode.V, KeyCombination.SHORTCUT_DOWN));
        pasteItem.setOnAction(e -> pasteAction());

        MenuItem selectAllItem = new MenuItem("Select All");
        selectAllItem.setAccelerator(new KeyCodeCombination(KeyCode.A, KeyCombination.SHORTCUT_DOWN));
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
            cachedDocumentSymbols.clear();
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
        Set<String> discoveredSymbols = new HashSet<>();

        while (matcher.find()) {
            String func = matcher.group("FUNCTION");
            if (func != null && func.length() >= 2) discoveredSymbols.add(func);
            String type = matcher.group("TYPE");
            if (type != null && type.length() >= 2) discoveredSymbols.add(type);

            String styleClass =
                    matcher.group("COMMENT") != null ? "comment" :
                    matcher.group("STRING") != null ? "string" :
                    matcher.group("ANNOTATION") != null ? "annotation" :
                    matcher.group("CONTROL") != null ? "control-keyword" :
                    matcher.group("KEYWORD") != null ? "keyword" :
                    matcher.group("PRIMITIVE") != null ? "type" :
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
        cachedDocumentSymbols.clear();
        cachedDocumentSymbols.addAll(discoveredSymbols);
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
        String text = codeArea.getText();
        if (text != null && !text.isEmpty()) {
            highlightExecutor.submit(() -> {
                StyleSpans<Collection<String>> spans = computeHighlighting(text);
                Platform.runLater(() -> applyHighlighting(spans));
            });
        }
    }

    /**
     * Instantly loads document content and applies full syntax highlighting on frame 1,
     * completely eliminating unstyled white text flash and loading lag.
     */
    public void loadContentInstantly(String content, String fileType) {
        this.fileType = fileType != null ? fileType.toLowerCase() : "java";
        String safeContent = content != null ? content : "";
        codeArea.replaceText(safeContent);
        codeArea.moveTo(0);

        if (!safeContent.isEmpty()) {
            StyleSpans<Collection<String>> spans = computeHighlighting(safeContent);
            codeArea.setStyleSpans(0, spans);
            minimapPane.renderMinimap();
        }
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

    private Node createGutterGraphic(int paragraphIndex) {
        int line1Indexed = paragraphIndex + 1;
        int totalLines = Math.max(10, codeArea.getParagraphs().size());
        int digits = String.valueOf(totalLines).length();
        double minNumberWidth = Math.max(26.0, digits * 8.5 + 4.0);

        HBox gutter = new HBox(8);
        gutter.setAlignment(Pos.CENTER_RIGHT);
        gutter.setPadding(new Insets(0, 8, 0, 4));

        Label lineNum = new Label(String.valueOf(line1Indexed));
        lineNum.getStyleClass().add("lineno");
        lineNum.setMinWidth(minNumberWidth);
        lineNum.setPrefWidth(minNumberWidth);
        lineNum.setAlignment(Pos.CENTER_RIGHT);

        Region gitIndicator = new Region();
        gitIndicator.setPrefWidth(3);
        gitIndicator.setMinWidth(3);
        gitIndicator.setMaxWidth(3);
        VBox.setVgrow(gitIndicator, Priority.ALWAYS);

        GitGutterService.GutterType state = gitDiffMap.getOrDefault(line1Indexed, GitGutterService.GutterType.NONE);
        switch (state) {
            case ADDED -> gitIndicator.setStyle("-fx-background-color: #2ea043;");
            case MODIFIED -> gitIndicator.setStyle("-fx-background-color: #007acc;");
            case DELETED -> gitIndicator.setStyle("-fx-background-color: #f85149;");
            case NONE -> gitIndicator.setStyle("-fx-background-color: transparent;");
        }

        gutter.getChildren().addAll(lineNum, gitIndicator);
        return gutter;
    }

    public void refreshGitGutter(Path file) {
        if (file == null) return;
        this.currentFilePath = file;
        GitGutterService.computeDiffAsync(file, diff -> {
            gitDiffMap.clear();
            gitDiffMap.putAll(diff);
            Platform.runLater(() -> {
                codeArea.setParagraphGraphicFactory(this::createGutterGraphic);
            });
        });
    }

    public void updateBreadcrumbs(Path filePath, String fileType, String content) {
        this.currentFilePath = filePath;
        breadcrumbBar.updateFilePath(filePath, fileType);
        highlightExecutor.submit(() -> {
            breadcrumbBar.indexSymbols(content, fileType);
        });
        breadcrumbBar.setVisible(true);
        breadcrumbBar.setManaged(true);
        refreshGitGutter(filePath);
    }

    public Path getCurrentFilePath() {
        return currentFilePath;
    }

    public void updateCaretLine(int line1Indexed) {
        breadcrumbBar.updateActiveCaretLine(line1Indexed);
    }

    public void toggleMinimap() {
        boolean visible = !minimapPane.isVisible();
        minimapPane.setVisible(visible);
        minimapPane.setManaged(visible);
    }

    public void toggleBreadcrumbs() {
        boolean visible = !breadcrumbBar.isVisible();
        breadcrumbBar.setVisible(visible);
        breadcrumbBar.setManaged(visible);
    }

    public boolean isBreadcrumbsVisible() {
        return breadcrumbBar.isVisible();
    }

    public MinimapPane getMinimapPane() {
        return minimapPane;
    }

    public BreadcrumbBar getBreadcrumbBar() {
        return breadcrumbBar;
    }

    private String getPrefixAtCaret() {
        int caretPos = codeArea.getCaretPosition();
        if (caretPos <= 0) return "";
        int start = Math.max(0, caretPos - 64);
        String text = codeArea.getText(start, caretPos);
        int idx = text.length() - 1;
        while (idx >= 0 && Character.isJavaIdentifierPart(text.charAt(idx))) {
            idx--;
        }
        return text.substring(idx + 1);
    }

    public void triggerAutoComplete(boolean force) {
        String prefix = getPrefixAtCaret();
        if (!force && (prefix.length() < 2 || !Character.isJavaIdentifierStart(prefix.charAt(0)))) {
            if (autoCompletePopup.isShowing()) {
                autoCompletePopup.hide();
            }
            return;
        }

        List<AutoCompleteService.CompletionItem> items = AutoCompleteService.computeCompletions(
                prefix, fileType, cachedDocumentSymbols
        );

        if (items.isEmpty()) {
            if (autoCompletePopup.isShowing()) {
                autoCompletePopup.hide();
            }
        } else {
            autoCompletePopup.showAtCaret(codeArea, items, this::applyCompletion);
        }
    }

    private void applyCompletion(AutoCompleteService.CompletionItem item) {
        int caretPos = codeArea.getCaretPosition();
        String prefix = getPrefixAtCaret();
        int startPos = caretPos - prefix.length();

        String insert = item.insertText();
        codeArea.replaceText(startPos, caretPos, insert);
        codeArea.moveTo(startPos + insert.length());
    }

    public void dispose() {
        if (autoCompletePopup != null && autoCompletePopup.isShowing()) {
            autoCompletePopup.hide();
        }
        if (autoCompleteSubscription != null) {
            autoCompleteSubscription.unsubscribe();
            autoCompleteSubscription = null;
        }
        if (highlightSubscription != null) {
            highlightSubscription.unsubscribe();
            highlightSubscription = null;
        }
        if (smoothScrollTimer != null) {
            smoothScrollTimer.stop();
        }
        if (minimapPane != null) {
            minimapPane.dispose();
        }
        highlightExecutor.shutdownNow();
    }
}
