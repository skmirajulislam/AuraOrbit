package service;

import org.kordamp.ikonli.codicons.Codicons;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * High-performance IntelliSense autocompletion and snippet engine.
 * Computes prefix-matched and fuzzy-scored completions combining language keywords,
 * common code snippets, and active document symbol identifiers.
 */
public class AutoCompleteService {

    public enum ItemKind {
        KEYWORD(Codicons.SYMBOL_KEYWORD, "keyword"),
        SNIPPET(Codicons.SYMBOL_SNIPPET, "snippet"),
        VARIABLE(Codicons.SYMBOL_VARIABLE, "variable"),
        METHOD(Codicons.SYMBOL_METHOD, "method"),
        CLASS(Codicons.SYMBOL_CLASS, "class"),
        PROPERTY(Codicons.SYMBOL_PROPERTY, "property");

        private final Codicons codicon;
        private final String label;

        ItemKind(Codicons codicon, String label) {
            this.codicon = codicon;
            this.label = label;
        }

        public Codicons getCodicon() {
            return codicon;
        }

        public String getLabel() {
            return label;
        }
    }

    public record CompletionItem(
            String label,
            String insertText,
            ItemKind kind,
            String detail
    ) {}

    private static final Pattern IDENTIFIER_PATTERN = Pattern.compile("\\b[a-zA-Z_][a-zA-Z0-9_]{2,}\\b");

    // Standard Language Keywords
    private static final Map<String, List<String>> LANGUAGE_KEYWORDS = new HashMap<>();
    // Common Code Snippets per Language
    private static final Map<String, List<CompletionItem>> LANGUAGE_SNIPPETS = new HashMap<>();

    static {
        // Java Keywords
        LANGUAGE_KEYWORDS.put("java", List.of(
                "abstract", "assert", "boolean", "break", "byte", "case", "catch", "char", "class",
                "const", "continue", "default", "do", "double", "else", "enum", "extends", "final",
                "finally", "float", "for", "goto", "if", "implements", "import", "instanceof", "int",
                "interface", "long", "native", "new", "package", "private", "protected", "public",
                "record", "return", "short", "static", "strictfp", "super", "switch", "synchronized",
                "this", "throw", "throws", "transient", "try", "void", "volatile", "while", "yield",
                "true", "false", "null", "var"
        ));

        // Python Keywords
        LANGUAGE_KEYWORDS.put("py", List.of(
                "False", "None", "True", "and", "as", "assert", "async", "await", "break", "class",
                "continue", "def", "del", "elif", "else", "except", "finally", "for", "from",
                "global", "if", "import", "in", "is", "lambda", "nonlocal", "not", "or", "pass",
                "raise", "return", "try", "while", "with", "yield"
        ));

        // JavaScript / TypeScript Keywords
        List<String> jsKeywords = List.of(
                "async", "await", "break", "case", "catch", "class", "const", "continue", "debugger",
                "default", "delete", "do", "else", "export", "extends", "finally", "for", "function",
                "if", "import", "in", "instanceof", "let", "new", "return", "super", "switch",
                "this", "throw", "try", "typeof", "var", "void", "while", "with", "yield"
        );
        LANGUAGE_KEYWORDS.put("js", jsKeywords);
        LANGUAGE_KEYWORDS.put("ts", jsKeywords);
        LANGUAGE_KEYWORDS.put("jsx", jsKeywords);
        LANGUAGE_KEYWORDS.put("tsx", jsKeywords);

        // Java Snippets
        LANGUAGE_SNIPPETS.put("java", List.of(
                new CompletionItem("psvm", "public static void main(String[] args) {\n    \n}", ItemKind.SNIPPET, "Main method"),
                new CompletionItem("sout", "System.out.println();", ItemKind.SNIPPET, "Print to stdout"),
                new CompletionItem("serr", "System.err.println();", ItemKind.SNIPPET, "Print to stderr"),
                new CompletionItem("fori", "for (int i = 0; i < ; i++) {\n    \n}", ItemKind.SNIPPET, "For-loop with index"),
                new CompletionItem("trycatch", "try {\n    \n} catch (Exception e) {\n    e.printStackTrace();\n}", ItemKind.SNIPPET, "Try-catch block"),
                new CompletionItem("iff", "if () {\n    \n}", ItemKind.SNIPPET, "If conditional block")
        ));

        // Python Snippets
        LANGUAGE_SNIPPETS.put("py", List.of(
                new CompletionItem("def", "def ():\n    pass", ItemKind.SNIPPET, "Function definition"),
                new CompletionItem("main", "if __name__ == '__main__':\n    main()", ItemKind.SNIPPET, "Main entrypoint"),
                new CompletionItem("class", "class :\n    def __init__(self):\n        pass", ItemKind.SNIPPET, "Class definition"),
                new CompletionItem("tryexcept", "try:\n    pass\nexcept Exception as e:\n    print(e)", ItemKind.SNIPPET, "Try-except block")
        ));

        // JavaScript Snippets
        List<CompletionItem> jsSnippets = List.of(
                new CompletionItem("clg", "console.log();", ItemKind.SNIPPET, "Console log statement"),
                new CompletionItem("afn", "const  = () => {\n    \n};", ItemKind.SNIPPET, "Arrow function"),
                new CompletionItem("edoc", "export default ;", ItemKind.SNIPPET, "Export default module")
        );
        LANGUAGE_SNIPPETS.put("js", jsSnippets);
        LANGUAGE_SNIPPETS.put("ts", jsSnippets);
        LANGUAGE_SNIPPETS.put("jsx", jsSnippets);
    }

    /**
     * Extracts dynamic document symbols from the full text buffer.
     */
    public static Set<String> extractDocumentSymbols(String text) {
        if (text == null || text.isBlank()) {
            return Collections.emptySet();
        }
        Set<String> symbols = new HashSet<>();
        Matcher matcher = IDENTIFIER_PATTERN.matcher(text);
        while (matcher.find()) {
            symbols.add(matcher.group());
        }
        return symbols;
    }

    /**
     * Resolves matching completions for a given prefix in a document.
     * Uses precomputed document symbols for zero UI-thread regex overhead.
     */
    public static List<CompletionItem> computeCompletions(String prefix, String fileType, Set<String> docSymbols) {
        if (prefix == null || prefix.isBlank()) {
            return Collections.emptyList();
        }
        String pLower = prefix.toLowerCase(Locale.ROOT);
        String lang = fileType != null ? fileType.toLowerCase(Locale.ROOT) : "java";

        List<CompletionItem> results = new ArrayList<>();

        // 1. Check Snippets
        List<CompletionItem> snippets = LANGUAGE_SNIPPETS.getOrDefault(lang, Collections.emptyList());
        for (CompletionItem snip : snippets) {
            if (snip.label().toLowerCase(Locale.ROOT).startsWith(pLower)) {
                results.add(snip);
            }
        }

        // 2. Check Keywords
        List<String> keywords = LANGUAGE_KEYWORDS.getOrDefault(lang, Collections.emptyList());
        for (String kw : keywords) {
            if (kw.toLowerCase(Locale.ROOT).startsWith(pLower) && !kw.equalsIgnoreCase(prefix)) {
                results.add(new CompletionItem(kw, kw, ItemKind.KEYWORD, "keyword"));
            }
        }

        // 3. Check Document Symbols (current file)
        if (docSymbols != null) {
            for (String sym : docSymbols) {
                if (sym.toLowerCase(Locale.ROOT).startsWith(pLower) && !sym.equals(prefix)) {
                    ItemKind kind = Character.isUpperCase(sym.charAt(0)) ? ItemKind.CLASS : ItemKind.VARIABLE;
                    results.add(new CompletionItem(sym, sym, kind, "identifier"));
                }
            }
        }

        // 4. Check Workspace Symbols (other files in the project)
        Set<String> addedLabels = results.stream().map(CompletionItem::label).collect(Collectors.toSet());
        for (WorkspaceSymbol ws : workspaceSymbols) {
            String label = ws.name();
            if (label.toLowerCase(Locale.ROOT).startsWith(pLower) && !label.equals(prefix) && !addedLabels.contains(label)) {
                results.add(new CompletionItem(label, label, ws.kind(), "workspace"));
                addedLabels.add(label);
            }
        }

        // Sort by relevance
        results.sort((a, b) -> {
            if (a.kind() == ItemKind.SNIPPET && b.kind() != ItemKind.SNIPPET) return -1;
            if (b.kind() == ItemKind.SNIPPET && a.kind() != ItemKind.SNIPPET) return 1;

            boolean aExact = a.label().startsWith(prefix);
            boolean bExact = b.label().startsWith(prefix);
            if (aExact != bExact) return aExact ? -1 : 1;

            return Integer.compare(a.label().length(), b.label().length());
        });

        // Cap to 25 items for UI responsiveness
        return results.stream().limit(25).collect(Collectors.toList());
    }

    /**
     * Resolves matching completions for a given prefix in a document text.
     * Backwards-compatible overload.
     */
    public static List<CompletionItem> computeCompletions(String prefix, String fileType, String documentText) {
        return computeCompletions(prefix, fileType, extractDocumentSymbols(documentText));
    }

    // ── Workspace Symbol Index ───────────────────────────────────────────────

    public record WorkspaceSymbol(String name, ItemKind kind, String sourceFile) {}

    private static final List<WorkspaceSymbol> workspaceSymbols = new java.util.concurrent.CopyOnWriteArrayList<>();

    private static final Pattern WS_JAVA_CLASS = Pattern.compile("(?:public\\s+)?(?:abstract\\s+)?(?:final\\s+)?(?:class|interface|enum|record)\\s+([A-Z][a-zA-Z0-9_]*)");
    private static final Pattern WS_JAVA_METHOD = Pattern.compile("(?:public|private|protected|static|final|\\s)+[\\w<>,\\[\\]\\s]+\\s+([a-z][a-zA-Z0-9_]*)\\s*\\([^)]*\\)\\s*(?:throws\\s+[\\w,\\s]+)?\\s*\\{");
    private static final Pattern WS_PY_CLASS = Pattern.compile("^\\s*class\\s+([A-Z][a-zA-Z0-9_]*)");
    private static final Pattern WS_PY_DEF = Pattern.compile("^\\s*def\\s+([a-zA-Z0-9_]+)\\s*\\(");
    private static final Pattern WS_JS_FUNC = Pattern.compile("(?:function\\s+([a-zA-Z0-9_]+)|(?:const|let|var)\\s+([a-zA-Z0-9_]+)\\s*=\\s*(?:async\\s*)?(?:\\([^)]*\\)|[a-zA-Z0-9_]+)\\s*=>)");

    private static final Set<String> CONTROL_KEYWORDS = Set.of("if", "while", "for", "switch", "catch", "try", "else", "do", "return");

    /**
     * Scans workspace source files asynchronously and populates the workspace symbol index.
     * Call this when workspace is opened/changed.
     */
    public static void scanWorkspaceSymbols(java.nio.file.Path workspacePath) {
        if (workspacePath == null) return;
        Thread.ofVirtual().start(() -> {
            List<WorkspaceSymbol> newSymbols = new ArrayList<>();
            try {
                java.nio.file.Files.walkFileTree(workspacePath, java.util.EnumSet.noneOf(java.nio.file.FileVisitOption.class), 15,
                        new java.nio.file.SimpleFileVisitor<java.nio.file.Path>() {
                            @Override
                            public java.nio.file.FileVisitResult preVisitDirectory(java.nio.file.Path dir, java.nio.file.attribute.BasicFileAttributes attrs) {
                                if (newSymbols.size() >= 5000) return java.nio.file.FileVisitResult.TERMINATE;
                                String dirName = dir.getFileName() != null ? dir.getFileName().toString() : "";
                                if (dirName.equals(".git") || dirName.equals("target") || dirName.equals("build") ||
                                        dirName.equals("node_modules") || dirName.equals("out") || dirName.startsWith(".")) {
                                    return java.nio.file.FileVisitResult.SKIP_SUBTREE;
                                }
                                return java.nio.file.FileVisitResult.CONTINUE;
                            }

                            @Override
                            public java.nio.file.FileVisitResult visitFile(java.nio.file.Path file, java.nio.file.attribute.BasicFileAttributes attrs) {
                                if (newSymbols.size() >= 5000) return java.nio.file.FileVisitResult.TERMINATE;
                                if (attrs.size() > 512 * 1024) return java.nio.file.FileVisitResult.CONTINUE; // Skip large files
                                String name = file.getFileName().toString();
                                String ext = "";
                                int dot = name.lastIndexOf('.');
                                if (dot >= 0) ext = name.substring(dot + 1).toLowerCase();

                                if (!ext.equals("java") && !ext.equals("py") && !ext.equals("js") && !ext.equals("ts") && !ext.equals("jsx") && !ext.equals("tsx")) {
                                    return java.nio.file.FileVisitResult.CONTINUE;
                                }

                                try {
                                    String content = java.nio.file.Files.readString(file, java.nio.charset.StandardCharsets.UTF_8);
                                    extractWorkspaceSymbols(content, ext, name, newSymbols);
                                } catch (Exception ignored) {}

                                return java.nio.file.FileVisitResult.CONTINUE;
                            }

                            @Override
                            public java.nio.file.FileVisitResult visitFileFailed(java.nio.file.Path file, java.io.IOException exc) {
                                return java.nio.file.FileVisitResult.CONTINUE;
                            }
                        });
            } catch (Exception ignored) {}

            workspaceSymbols.clear();
            workspaceSymbols.addAll(newSymbols);
        });
    }

    public static void addWorkspaceSymbol(String name, ItemKind kind, String sourceFile) {
        workspaceSymbols.add(new WorkspaceSymbol(name, kind, sourceFile));
    }

    public static void extractWorkspaceSymbols(String content, String ext, String fileName, List<WorkspaceSymbol> out) {
        String[] lines = content.split("\\R", -1);
        for (String line : lines) {
            if (ext.equals("java")) {
                Matcher cm = WS_JAVA_CLASS.matcher(line);
                if (cm.find()) {
                    out.add(new WorkspaceSymbol(cm.group(1), ItemKind.CLASS, fileName));
                }
                Matcher mm = WS_JAVA_METHOD.matcher(line);
                if (mm.find()) {
                    String mName = mm.group(1);
                    if (!CONTROL_KEYWORDS.contains(mName)) {
                        out.add(new WorkspaceSymbol(mName, ItemKind.METHOD, fileName));
                    }
                }
            } else if (ext.equals("py")) {
                Matcher cm = WS_PY_CLASS.matcher(line);
                if (cm.find()) out.add(new WorkspaceSymbol(cm.group(1), ItemKind.CLASS, fileName));
                Matcher dm = WS_PY_DEF.matcher(line);
                if (dm.find()) out.add(new WorkspaceSymbol(dm.group(1), ItemKind.METHOD, fileName));
            } else if (ext.equals("js") || ext.equals("ts") || ext.equals("jsx") || ext.equals("tsx")) {
                Matcher jm = WS_JS_FUNC.matcher(line);
                if (jm.find()) {
                    String n = jm.group(1) != null ? jm.group(1) : jm.group(2);
                    if (n != null) out.add(new WorkspaceSymbol(n, ItemKind.METHOD, fileName));
                }
            }
        }
    }

    /**
     * Returns the current workspace symbol count (for testing).
     */
    public static int getWorkspaceSymbolCount() {
        return workspaceSymbols.size();
    }

    /**
     * Clears the workspace symbol index.
     */
    public static void clearWorkspaceSymbols() {
        workspaceSymbols.clear();
    }
}

