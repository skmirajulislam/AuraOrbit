package service;

import org.kordamp.ikonli.codicons.Codicons;
import view.fx.TerminalPane;

import javax.tools.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * High-performance, production-grade diagnostic and linting engine.
 * Dynamically analyzes source code across multiple languages (Java, Python,
 * JavaScript, TypeScript, CSS, JSON, YAML, XML, HTML, etc.) in real time.
 */
public class CodeDiagnosticsService {

    // Java Patterns
    private static final Pattern JAVA_IMPORT_PATTERN = Pattern.compile("^\\s*import\\s+(?:static\\s+)?([a-zA-Z0-9_.]+\\.([a-zA-Z0-9_]+))\\s*;");
    private static final Pattern JAVA_CLASS_PATTERN = Pattern.compile("public\\s+(?:class|interface|enum|record)\\s+([a-zA-Z0-9_]+)");
    private static final Pattern JAVA_PRIVATE_FIELD_PATTERN = Pattern.compile("^\\s*private\\s+(?:(?:static|final|volatile|transient)\\s+)*(?:[a-zA-Z0-9_<>,\\[\\]\\s]+?)\\s+([a-zA-Z0-9_]+)\\s*(?:=.*?)?;");
    private static final Pattern JAVA_EMPTY_CATCH = Pattern.compile("catch\\s*\\([^)]*?\\b([a-zA-Z0-9_]+)\\s*\\)\\s*\\{\\s*\\}");

    // Python Patterns
    private static final Pattern PY_IMPORT_PATTERN = Pattern.compile("^\\s*import\\s+([a-zA-Z0-9_., ]+)(?:\\s+as\\s+([a-zA-Z0-9_]+))?");
    private static final Pattern PY_FROM_IMPORT_PATTERN = Pattern.compile("^\\s*from\\s+[a-zA-Z0-9_.]+\\s+import\\s+([a-zA-Z0-9_., *]+)");
    private static final Pattern PY_BARE_EXCEPT = Pattern.compile("^\\s*except\\s*:");

    // JS/TS Patterns
    private static final Pattern JS_IMPORT_NAMED = Pattern.compile("import\\s*\\{([^}]+)\\}\\s*from");
    private static final Pattern JS_IMPORT_DEFAULT = Pattern.compile("import\\s+([a-zA-Z0-9_]+)\\s+from");
    private static final Pattern JS_REQUIRE = Pattern.compile("(?:const|let|var)\\s+([a-zA-Z0-9_]+)\\s*=\\s*require\\(");
    private static final Pattern JS_DEBUGGER = Pattern.compile("\\bdebugger\\s*;");
    private static final Pattern JS_CONSOLE = Pattern.compile("\\bconsole\\.(?:log|debug|info)\\s*\\(");
    private static final Pattern JS_LOOSE_EQ = Pattern.compile("[^!=<>]={2}[^=]");

    // CSS Patterns
    private static final Pattern CSS_EMPTY_RULE = Pattern.compile("([^{}]+)\\{\\s*\\}");

    // JSON Patterns
    private static final Pattern JSON_TRAILING_COMMA = Pattern.compile(",\\s*([}\\]])");
    private static final Pattern JSON_SINGLE_QUOTE = Pattern.compile("'([^']*)'");

    /**
     * Analyzes an individual source file and returns all detected errors, warnings, and info diagnostics.
     */
    public static List<TerminalPane.ProblemItem> analyzeFile(Path file) {
        List<TerminalPane.ProblemItem> problems = new ArrayList<>();
        if (file == null || !Files.isRegularFile(file)) return problems;

        String fileName = file.getFileName().toString();
        String fullPath = file.toAbsolutePath().normalize().toString();

        List<String> lines;
        try {
            lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return problems;
        }

        String ext = "";
        int dot = fileName.lastIndexOf('.');
        if (dot != -1) ext = fileName.substring(dot).toLowerCase();

        // 1. Task markers (todo, fixme, hack) for all files
        checkTaskMarkers(lines, fullPath, problems);

        // 2. Syntax & structural checks only for actual programming languages (never for markdown or text)
        boolean isCodeFile = switch (ext) {
            case ".java", ".py", ".js", ".jsx", ".ts", ".tsx", ".mjs", ".c", ".cpp", ".cs", ".go", ".rs" -> true;
            default -> false;
        };

        if (isCodeFile) {
            checkCodeBracketsAndStrings(lines, fullPath, ext, problems);
        }

        // 3. Language-specific static analysis
        switch (ext) {
            case ".java" -> analyzeJava(lines, fullPath, fileName, problems);
            case ".py" -> analyzePython(lines, fullPath, problems);
            case ".js", ".jsx", ".ts", ".tsx", ".mjs" -> analyzeJavaScript(lines, fullPath, problems);
            case ".css", ".scss" -> analyzeCss(lines, fullPath, problems);
            case ".json" -> analyzeJson(lines, fullPath, problems);
            case ".yaml", ".yml" -> analyzeYaml(lines, fullPath, problems);
        }

        return problems;
    }

    private static void checkTaskMarkers(List<String> lines, String fullPath, List<TerminalPane.ProblemItem> problems) {
        String todoMarker = "TO" + "DO:";
        String fixmeMarker = "FIX" + "ME:";
        String hackMarker = "HA" + "CK:";
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line.contains(todoMarker) || line.contains(fixmeMarker) || line.contains(hackMarker)) {
                problems.add(new TerminalPane.ProblemItem(Codicons.INFO, "Info", line.trim(), fullPath, i + 1, 1, "todo"));
            }
        }
    }

    private static void checkCodeBracketsAndStrings(List<String> lines, String fullPath, String ext, List<TerminalPane.ProblemItem> problems) {
        int braceDepth = 0;
        int parenDepth = 0;
        int bracketDepth = 0;
        boolean inBlockComment = false;
        boolean inTextBlock = false; // For Java """ or Python """

        boolean isJava = ".java".equals(ext);
        boolean isPython = ".py".equals(ext);

        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            int lineNum = i + 1;
            int len = line.length();
            int c = 0;

            boolean inString = false;
            char quoteChar = 0;

            while (c < len) {
                // If inside block comment /* ... */
                if (inBlockComment) {
                    int endComment = line.indexOf("*/", c);
                    if (endComment != -1) {
                        inBlockComment = false;
                        c = endComment + 2;
                        continue;
                    } else {
                        break;
                    }
                }

                // If inside multi-line text block """
                if (inTextBlock) {
                    int endBlock = line.indexOf("\"\"\"", c);
                    if (endBlock != -1) {
                        inTextBlock = false;
                        c = endBlock + 3;
                        continue;
                    } else {
                        break;
                    }
                }

                // Check for start of text block """
                if (c + 2 < len && line.charAt(c) == '"' && line.charAt(c + 1) == '"' && line.charAt(c + 2) == '"') {
                    int close = line.indexOf("\"\"\"", c + 3);
                    if (close != -1) {
                        c = close + 3;
                        continue;
                    } else {
                        inTextBlock = true;
                        break;
                    }
                }

                // Check for start of block comment /* (only in non-Python)
                if (!isPython && !inString && c + 1 < len && line.charAt(c) == '/' && line.charAt(c + 1) == '*') {
                    inBlockComment = true;
                    c += 2;
                    continue;
                }

                // Check for single-line comment (// in Java/JS, # in Python)
                if (!inString) {
                    if (!isPython && c + 1 < len && line.charAt(c) == '/' && line.charAt(c + 1) == '/') {
                        break;
                    }
                    if (isPython && line.charAt(c) == '#') {
                        break;
                    }
                }

                char ch = line.charAt(c);

                // Escape character
                if (ch == '\\' && c + 1 < len) {
                    c += 2;
                    continue;
                }

                // Handle Character Literals in Java ('a', '\n', etc.)
                if (isJava && !inString && ch == '\'') {
                    int closeQuote = line.indexOf('\'', c + 1);
                    if (closeQuote != -1 && (closeQuote - c) <= 4) {
                        c = closeQuote + 1;
                        continue;
                    }
                }

                // Handle Strings
                if (ch == '"' || (!isJava && ch == '\'')) {
                    if (!inString) {
                        inString = true;
                        quoteChar = ch;
                    } else if (ch == quoteChar) {
                        inString = false;
                    }
                    c++;
                    continue;
                }

                // Outside strings & comments, count bracket balance
                if (!inString) {
                    if (ch == '{') braceDepth++;
                    else if (ch == '}') braceDepth--;
                    else if (ch == '(') parenDepth++;
                    else if (ch == ')') parenDepth--;
                    else if (ch == '[') bracketDepth++;
                    else if (ch == ']') bracketDepth--;
                }

                c++;
            }

            if (inString && !inTextBlock) {
                problems.add(new TerminalPane.ProblemItem(Codicons.ERROR, "Error", "Unclosed string literal", fullPath, lineNum, line.length(), "syntax"));
            }
        }

        if (braceDepth != 0) {
            problems.add(new TerminalPane.ProblemItem(Codicons.ERROR, "Error", "Mismatched braces (depth: " + braceDepth + ")", fullPath, lines.size(), 1, "syntax"));
        }
        if (parenDepth != 0) {
            problems.add(new TerminalPane.ProblemItem(Codicons.WARNING, "Warning", "Mismatched parentheses (depth: " + parenDepth + ")", fullPath, lines.size(), 1, "syntax"));
        }
        if (bracketDepth != 0) {
            problems.add(new TerminalPane.ProblemItem(Codicons.WARNING, "Warning", "Mismatched square brackets (depth: " + bracketDepth + ")", fullPath, lines.size(), 1, "syntax"));
        }
    }

    private static void analyzeJava(List<String> lines, String fullPath, String fileName, List<TerminalPane.ProblemItem> problems) {
        String simpleClass = fileName.replace(".java", "");
        for (String line : lines) {
            Matcher cm = JAVA_CLASS_PATTERN.matcher(line);
            if (cm.find()) {
                simpleClass = cm.group(1);
                break;
            }
        }

        // Collect imports
        record ImportEntry(int line, String fullImport, String simpleName) {}
        List<ImportEntry> imports = new ArrayList<>();
        StringBuilder nonImportBody = new StringBuilder();

        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            int lineNum = i + 1;
            Matcher im = JAVA_IMPORT_PATTERN.matcher(line);
            if (im.find()) {
                String fullImp = im.group(1);
                String simpleName = im.group(2);
                if (!"*".equals(simpleName)) {
                    imports.add(new ImportEntry(lineNum, fullImp, simpleName));
                }
            } else if (!line.trim().startsWith("package ")) {
                nonImportBody.append(line).append("\n");
            }

            // Empty catch block (ignore standard intentional suppression: ignored, expected, _)
            Matcher cm = JAVA_EMPTY_CATCH.matcher(line);
            if (cm.find()) {
                String param = cm.group(1);
                if (!"ignored".equalsIgnoreCase(param) && !"expected".equalsIgnoreCase(param) && !"_".equals(param)) {
                    problems.add(new TerminalPane.ProblemItem(Codicons.WARNING, "Warning", "Empty catch block: exception swallowed", fullPath, lineNum, 1, "Java"));
                }
            }

            // Redundant semicolon (ignore inside string literals, comments, and intentional for(;;) loops)
            String codeOnly = line.replaceAll("\"(\\\\.|[^\"])*\"", "\"\"").replaceAll("//.*", "");
            if (!codeOnly.contains("for (;;)") && !codeOnly.contains("for(;;)") && !codeOnly.contains("for (; ;)") && codeOnly.contains(";;")) {
                problems.add(new TerminalPane.ProblemItem(Codicons.INFO, "Info", "Redundant semicolon", fullPath, lineNum, line.indexOf(";;") + 1, "Java"));
            }
        }

        String bodyText = nonImportBody.toString();

        // 1. Unused imports detection
        for (ImportEntry imp : imports) {
            Pattern wordPattern = Pattern.compile("\\b" + Pattern.quote(imp.simpleName) + "\\b");
            if (!wordPattern.matcher(bodyText).find()) {
                problems.add(new TerminalPane.ProblemItem(
                        Codicons.WARNING, "Warning",
                        "The import " + imp.fullImport + " is never used",
                        fullPath, imp.line, 1, "Java"
                ));
            }
        }

        // 2. Unused private fields detection
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            int lineNum = i + 1;
            Matcher fm = JAVA_PRIVATE_FIELD_PATTERN.matcher(line);
            if (fm.find()) {
                String fieldName = fm.group(1);
                int reads = 0;
                Pattern fieldWord = Pattern.compile("\\b" + Pattern.quote(fieldName) + "\\b");
                Pattern paramDecl = Pattern.compile("\\b(?:[A-Z][a-zA-Z0-9_<>]*|int|long|boolean|double|float|char|byte|short)\\s+(?:final\\s+)?" + Pattern.quote(fieldName) + "\\s*[,)]");

                for (int j = 0; j < lines.size(); j++) {
                    if (j == i) continue; // skip declaration line
                    String otherLine = lines.get(j).trim();

                    // Strip string literals and line comments
                    String stripped = otherLine.replaceAll("\"(\\\\.|[^\"])*\"", "\"\"").replaceAll("//.*", "");

                    // Skip pure LHS assignment (e.g. this.fieldName = ... or fieldName = ...)
                    int eqIdx = stripped.indexOf('=');
                    if (eqIdx != -1) {
                        String lhs = stripped.substring(0, eqIdx).trim();
                        if (lhs.equals("this." + fieldName) || lhs.equals(fieldName)) {
                            // Only check RHS for reads
                            String rhs = stripped.substring(eqIdx + 1);
                            if (fieldWord.matcher(rhs).find()) {
                                // If RHS is simply the method parameter with the same name, don't count as a read
                                if (!rhs.trim().matches(Pattern.quote(fieldName) + "\\s*;?")) {
                                    reads++;
                                }
                            }
                            continue;
                        }
                    }

                    // Remove parameter declarations like "(String fieldName," or "(int fieldName)"
                    String cleanLine = paramDecl.matcher(stripped).replaceAll("");

                    if (fieldWord.matcher(cleanLine).find()) {
                        reads++;
                    }
                }

                if (reads == 0) {
                    problems.add(new TerminalPane.ProblemItem(
                            Codicons.WARNING, "Warning",
                            "The value of the field " + simpleClass + "." + fieldName + " is not used",
                            fullPath, lineNum, 1, "Java"
                    ));
                }
            }
        }
    }

    private static void analyzePython(List<String> lines, String fullPath, List<TerminalPane.ProblemItem> problems) {
        StringBuilder fullCode = new StringBuilder();
        boolean hasTabs = false;
        boolean hasSpaces = false;

        record PyImport(int line, String symbol) {}
        List<PyImport> imports = new ArrayList<>();

        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            int lineNum = i + 1;

            if (line.startsWith("\t")) hasTabs = true;
            if (line.startsWith("  ")) hasSpaces = true;

            Matcher m1 = PY_IMPORT_PATTERN.matcher(line);
            if (m1.find()) {
                String asName = m1.group(2);
                if (asName != null) {
                    imports.add(new PyImport(lineNum, asName.trim()));
                } else {
                    for (String s : m1.group(1).split(",")) {
                        String clean = s.trim();
                        int dotIdx = clean.lastIndexOf('.');
                        imports.add(new PyImport(lineNum, dotIdx != -1 ? clean.substring(dotIdx + 1) : clean));
                    }
                }
            }

            Matcher m2 = PY_FROM_IMPORT_PATTERN.matcher(line);
            if (m2.find()) {
                String symbols = m2.group(1);
                if (!symbols.contains("*")) {
                    for (String s : symbols.split(",")) {
                        String clean = s.trim();
                        if (clean.contains(" as ")) {
                            clean = clean.substring(clean.indexOf(" as ") + 4).trim();
                        }
                        if (!clean.isEmpty()) imports.add(new PyImport(lineNum, clean));
                    }
                }
            }

            if (PY_BARE_EXCEPT.matcher(line).find()) {
                problems.add(new TerminalPane.ProblemItem(Codicons.WARNING, "Warning", "Do not use bare 'except:' without exception type", fullPath, lineNum, 1, "Python"));
            }

            if (!line.trim().startsWith("import ") && !line.trim().startsWith("from ")) {
                fullCode.append(line).append("\n");
            }
        }

        if (hasTabs && hasSpaces) {
            problems.add(new TerminalPane.ProblemItem(Codicons.WARNING, "Warning", "Inconsistent indentation: mixed tabs and spaces", fullPath, 1, 1, "Python"));
        }

        String codeText = fullCode.toString();
        for (PyImport imp : imports) {
            Pattern p = Pattern.compile("\\b" + Pattern.quote(imp.symbol) + "\\b");
            if (!p.matcher(codeText).find()) {
                problems.add(new TerminalPane.ProblemItem(Codicons.WARNING, "Warning", "The import '" + imp.symbol + "' is not used", fullPath, imp.line, 1, "Python"));
            }
        }
    }

    private static void analyzeJavaScript(List<String> lines, String fullPath, List<TerminalPane.ProblemItem> problems) {
        StringBuilder fullCode = new StringBuilder();
        record JsImport(int line, String symbol) {}
        List<JsImport> imports = new ArrayList<>();

        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            int lineNum = i + 1;

            Matcher named = JS_IMPORT_NAMED.matcher(line);
            if (named.find()) {
                for (String part : named.group(1).split(",")) {
                    String sym = part.trim();
                    if (sym.contains(" as ")) sym = sym.substring(sym.indexOf(" as ") + 4).trim();
                    if (!sym.isEmpty()) imports.add(new JsImport(lineNum, sym));
                }
            }

            Matcher def = JS_IMPORT_DEFAULT.matcher(line);
            if (def.find()) {
                imports.add(new JsImport(lineNum, def.group(1).trim()));
            }

            Matcher req = JS_REQUIRE.matcher(line);
            if (req.find()) {
                imports.add(new JsImport(lineNum, req.group(1).trim()));
            }

            if (JS_DEBUGGER.matcher(line).find()) {
                problems.add(new TerminalPane.ProblemItem(Codicons.WARNING, "Warning", "Unexpected 'debugger' statement", fullPath, lineNum, 1, "JavaScript"));
            }

            if (JS_CONSOLE.matcher(line).find()) {
                problems.add(new TerminalPane.ProblemItem(Codicons.INFO, "Info", "Unexpected console statement", fullPath, lineNum, 1, "JavaScript"));
            }

            if (JS_LOOSE_EQ.matcher(line).find() && !line.contains("== null") && !line.contains("!= null")) {
                problems.add(new TerminalPane.ProblemItem(Codicons.WARNING, "Warning", "Expected '===' and instead saw '=='", fullPath, lineNum, 1, "JavaScript"));
            }

            if (!line.trim().startsWith("import ") && !line.trim().startsWith("const ") && !line.trim().startsWith("let ")) {
                fullCode.append(line).append("\n");
            }
        }

        String codeText = fullCode.toString();
        for (JsImport imp : imports) {
            Pattern p = Pattern.compile("\\b" + Pattern.quote(imp.symbol) + "\\b");
            if (!p.matcher(codeText).find()) {
                problems.add(new TerminalPane.ProblemItem(Codicons.WARNING, "Warning", "The import '" + imp.symbol + "' is never used", fullPath, imp.line, 1, "JavaScript"));
            }
        }
    }

    private static void analyzeCss(List<String> lines, String fullPath, List<TerminalPane.ProblemItem> problems) {
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            int lineNum = i + 1;
            Matcher m = CSS_EMPTY_RULE.matcher(line);
            if (m.find()) {
                problems.add(new TerminalPane.ProblemItem(Codicons.WARNING, "Warning", "Empty ruleset: " + m.group(1).trim(), fullPath, lineNum, 1, "CSS"));
            }
        }
    }

    private static void analyzeJson(List<String> lines, String fullPath, List<TerminalPane.ProblemItem> problems) {
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            int lineNum = i + 1;
            if (JSON_SINGLE_QUOTE.matcher(line).find()) {
                problems.add(new TerminalPane.ProblemItem(Codicons.ERROR, "Error", "JSON strings must use double quotes", fullPath, lineNum, 1, "JSON"));
            }
            if (JSON_TRAILING_COMMA.matcher(line).find()) {
                problems.add(new TerminalPane.ProblemItem(Codicons.ERROR, "Error", "Trailing comma in JSON is not allowed", fullPath, lineNum, line.lastIndexOf(',') + 1, "JSON"));
            } else if (line.trim().endsWith(",")) {
                for (int j = i + 1; j < lines.size(); j++) {
                    String next = lines.get(j).trim();
                    if (next.isEmpty() || next.startsWith("//") || next.startsWith("/*")) continue;
                    if (next.startsWith("}") || next.startsWith("]")) {
                        problems.add(new TerminalPane.ProblemItem(Codicons.ERROR, "Error", "Trailing comma in JSON is not allowed", fullPath, lineNum, line.lastIndexOf(',') + 1, "JSON"));
                    }
                    break;
                }
            }
        }
    }

    private static void analyzeYaml(List<String> lines, String fullPath, List<TerminalPane.ProblemItem> problems) {
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line.startsWith("\t")) {
                problems.add(new TerminalPane.ProblemItem(Codicons.ERROR, "Error", "YAML does not allow tab characters for indentation", fullPath, i + 1, 1, "YAML"));
            }
        }
    }

    /**
     * Dynamically builds a comprehensive classpath for in-memory javac analysis.
     * Accurately aggregates java.class.path, jdk.module.path, JVM VM launch arguments (--module-path),
     * loaded JavaFX/third-party jar code sources, boot layer modules, and target/classes.
     */
    public static String buildDynamicClasspath() {
        Set<String> entries = new LinkedHashSet<>();

        // 1. Current java.class.path
        String cp = System.getProperty("java.class.path");
        if (cp != null && !cp.isBlank()) {
            for (String part : cp.split(Pattern.quote(java.io.File.pathSeparator))) {
                if (!part.isBlank() && Files.exists(Paths.get(part))) {
                    entries.add(Paths.get(part).toAbsolutePath().toString());
                }
            }
        }

        // 2. Current jdk.module.path (if set)
        String mp = System.getProperty("jdk.module.path");
        if (mp != null && !mp.isBlank()) {
            for (String part : mp.split(Pattern.quote(java.io.File.pathSeparator))) {
                if (!part.isBlank() && Files.exists(Paths.get(part))) {
                    entries.add(Paths.get(part).toAbsolutePath().toString());
                }
            }
        }

        // 3. JVM input arguments (--module-path or -p passed to java)
        try {
            List<String> vmArgs = java.lang.management.ManagementFactory.getRuntimeMXBean().getInputArguments();
            for (int i = 0; i < vmArgs.size(); i++) {
                String arg = vmArgs.get(i);
                String modPath = null;
                if (arg.startsWith("--module-path=")) {
                    modPath = arg.substring("--module-path=".length());
                } else if (arg.startsWith("-p=")) {
                    modPath = arg.substring("-p=".length());
                } else if ((arg.equals("--module-path") || arg.equals("-p")) && i + 1 < vmArgs.size()) {
                    modPath = vmArgs.get(i + 1);
                }
                if (modPath != null) {
                    for (String part : modPath.split(Pattern.quote(java.io.File.pathSeparator))) {
                        if (!part.isBlank() && Files.exists(Paths.get(part))) {
                            entries.add(Paths.get(part).toAbsolutePath().toString());
                        }
                    }
                }
            }
        } catch (Throwable ignored) {}

        // 4. Code source locations of key loaded classes (JavaFX, RichTextFX, Ikonli, Gson, WebSockets, etc.)
        Class<?>[] probeClasses = new Class<?>[] {
                javafx.application.Application.class,
                javafx.stage.Stage.class,
                javafx.scene.Scene.class,
                javafx.scene.control.Control.class,
                javafx.scene.layout.Pane.class,
                javafx.geometry.Insets.class,
                javafx.beans.NamedArg.class,
                org.fxmisc.richtext.CodeArea.class,
                org.reactfx.EventStreams.class,
                org.fxmisc.flowless.VirtualizedScrollPane.class,
                org.fxmisc.undo.UndoManager.class,
                org.fxmisc.wellbehaved.event.EventPattern.class,
                org.kordamp.ikonli.Ikon.class,
                org.kordamp.ikonli.javafx.FontIcon.class,
                org.kordamp.ikonli.codicons.Codicons.class,
                org.kordamp.ikonli.devicons.Devicons.class,
                com.google.gson.Gson.class,
                org.java_websocket.client.WebSocketClient.class
        };
        for (Class<?> cls : probeClasses) {
            try {
                var cs = cls.getProtectionDomain().getCodeSource();
                if (cs != null && cs.getLocation() != null) {
                    Path p = Paths.get(cs.getLocation().toURI());
                    if (Files.exists(p)) {
                        entries.add(p.toAbsolutePath().toString());
                    }
                }
            } catch (Throwable ignored) {}
        }

        // 5. Modules from Boot Layer (Java 9+ JPMS resolved modules)
        try {
            for (java.lang.module.ResolvedModule rm : ModuleLayer.boot().configuration().modules()) {
                rm.reference().location().ifPresent(uri -> {
                    try {
                        if ("file".equalsIgnoreCase(uri.getScheme())) {
                            Path p = Paths.get(uri);
                            if (Files.exists(p)) {
                                entries.add(p.toAbsolutePath().toString());
                            }
                        }
                    } catch (Throwable ignored) {}
                });
            }
        } catch (Throwable ignored) {}

        // 6. Workspace target/classes and target/test-classes
        Path targetClasses = Paths.get("target", "classes");
        if (Files.isDirectory(targetClasses)) {
            entries.add(targetClasses.toAbsolutePath().toString());
        }
        Path targetTestClasses = Paths.get("target", "test-classes");
        if (Files.isDirectory(targetTestClasses)) {
            entries.add(targetTestClasses.toAbsolutePath().toString());
        }

        // 7. Fallback: Search ~/.m2/repository for openjfx, ikonli, and fxmisc jars if still missing
        try {
            Path m2Repo = Paths.get(System.getProperty("user.home"), ".m2", "repository");
            if (Files.isDirectory(m2Repo)) {
                if (entries.stream().noneMatch(e -> e.contains("javafx-controls"))) {
                    Path m2OpenJfx = m2Repo.resolve("org/openjfx");
                    if (Files.isDirectory(m2OpenJfx)) {
                        try (var stream = Files.walk(m2OpenJfx, 4)) {
                            stream.filter(p -> p.toString().endsWith(".jar") && !p.toString().endsWith("-sources.jar") && !p.toString().endsWith("-javadoc.jar"))
                                  .forEach(p -> entries.add(p.toAbsolutePath().toString()));
                        }
                    }
                }
                if (entries.stream().noneMatch(e -> e.contains("ikonli-codicons-pack"))) {
                    Path m2Ikonli = m2Repo.resolve("org/kordamp/ikonli");
                    if (Files.isDirectory(m2Ikonli)) {
                        try (var stream = Files.walk(m2Ikonli, 4)) {
                            stream.filter(p -> p.toString().endsWith(".jar") && !p.toString().endsWith("-sources.jar") && !p.toString().endsWith("-javadoc.jar"))
                                  .forEach(p -> entries.add(p.toAbsolutePath().toString()));
                        }
                    }
                }
                if (entries.stream().noneMatch(e -> e.contains("richtextfx"))) {
                    Path m2Fxmisc = m2Repo.resolve("org/fxmisc");
                    if (Files.isDirectory(m2Fxmisc)) {
                        try (var stream = Files.walk(m2Fxmisc, 4)) {
                            stream.filter(p -> p.toString().endsWith(".jar") && !p.toString().endsWith("-sources.jar") && !p.toString().endsWith("-javadoc.jar"))
                                  .forEach(p -> entries.add(p.toAbsolutePath().toString()));
                        }
                    }
                }
            }
        } catch (Throwable ignored) {}

        return String.join(java.io.File.pathSeparator, entries);
    }

    /**
     * Executes JDK compiler diagnostics with full project classpath and linting.
     */
    public static List<TerminalPane.ProblemItem> runJavaCompilerDiagnostics(List<Path> javaFiles) {
        List<TerminalPane.ProblemItem> results = new ArrayList<>();
        if (javaFiles == null || javaFiles.isEmpty()) return results;

        try {
            JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
            if (compiler == null) return results;

            DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
            StandardJavaFileManager fm = compiler.getStandardFileManager(diagnostics, null, StandardCharsets.UTF_8);
            Iterable<? extends JavaFileObject> units = fm.getJavaFileObjectsFromPaths(javaFiles);

            Path tempClasses = null;
            try {
                tempClasses = Files.createTempDirectory("auraorbit_diag");
                tempClasses.toFile().deleteOnExit();

                String cp = buildDynamicClasspath();
                List<String> options = Arrays.asList(
                        "-proc:none",
                        "-Xlint:all,-serial,-rawtypes,-unchecked,-preview",
                        "-classpath", cp.isBlank() ? "." : cp,
                        "-d", tempClasses.toString()
                );

                JavaCompiler.CompilationTask task = compiler.getTask(null, fm, diagnostics, options, null, units);
                task.call();

                for (Diagnostic<? extends JavaFileObject> d : diagnostics.getDiagnostics()) {
                    // Only process diagnostics for actual project Java source files
                    if (d.getSource() == null) continue;
                    String rawName = d.getSource().getName();
                    if (rawName.endsWith(".class")) continue; // Skip third-party classfile internal warnings

                    Codicons icon = Codicons.INFO;
                    String severity = "Info";
                    if (d.getKind() == Diagnostic.Kind.ERROR) {
                        icon = Codicons.ERROR;
                        severity = "Error";
                    } else if (d.getKind() == Diagnostic.Kind.WARNING || d.getKind() == Diagnostic.Kind.MANDATORY_WARNING) {
                        icon = Codicons.WARNING;
                        severity = "Warning";
                    }

                    String sourcePath = rawName;
                    try {
                        sourcePath = Paths.get(d.getSource().toUri()).toAbsolutePath().normalize().toString();
                    } catch (Exception ignored) {}

                    results.add(new TerminalPane.ProblemItem(
                            icon, severity,
                            d.getMessage(Locale.getDefault()),
                            sourcePath,
                            (int) Math.max(1, d.getLineNumber()),
                            (int) Math.max(1, d.getColumnNumber()),
                            "javac"
                    ));
                }
            } finally {
                try { fm.close(); } catch (Exception ignored) {}
                if (tempClasses != null && Files.exists(tempClasses)) {
                    try (var walk = Files.walk(tempClasses)) {
                        walk.sorted(Comparator.reverseOrder())
                            .map(Path::toFile)
                            .forEach(java.io.File::delete);
                    } catch (Exception ignored) {}
                }
            }
        } catch (Exception ignored) {}

        return results;
    }
}
