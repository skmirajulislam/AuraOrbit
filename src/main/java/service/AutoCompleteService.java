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
     */
    public static List<CompletionItem> computeCompletions(String prefix, String fileType, String documentText) {
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

        // 3. Check Document Symbols
        Set<String> docSymbols = extractDocumentSymbols(documentText);
        for (String sym : docSymbols) {
            if (sym.toLowerCase(Locale.ROOT).startsWith(pLower) && !sym.equals(prefix)) {
                ItemKind kind = Character.isUpperCase(sym.charAt(0)) ? ItemKind.CLASS : ItemKind.VARIABLE;
                results.add(new CompletionItem(sym, sym, kind, "identifier"));
            }
        }

        // Sort by relevance:
        // 1. Snippets first
        // 2. Exact case matches before case-insensitive
        // 3. Shorter matches before longer matches
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
}
