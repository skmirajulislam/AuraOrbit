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
    private static final Pattern JAVA_EMPTY_CATCH = Pattern.compile("catch\\s*\\([^)]+\\)\\s*\\{\\s*\\}");

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

        // 1. Universal checks: TODOs, FIXMEs, bracket depth, string balances
        checkUniversalDiagnostics(lines, fullPath, problems);

        // 2. Language-specific static analysis
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

    private static void checkUniversalDiagnostics(List<String> lines, String fullPath, List<TerminalPane.ProblemItem> problems) {
        int braceDepth = 0;
        int parenDepth = 0;
        int bracketDepth = 0;

        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            int lineNum = i + 1;

            // Task markers in user source are surfaced as informational diagnostics.
            String todoMarker = "TO" + "DO:";
            String fixmeMarker = "FIX" + "ME:";
            String hackMarker = "HA" + "CK:";
            if (line.contains(todoMarker) || line.contains(fixmeMarker) || line.contains(hackMarker)) {
                String clean = line.trim();
                problems.add(new TerminalPane.ProblemItem(Codicons.INFO, "Info", clean, fullPath, lineNum, 1, "todo"));
            }

            // Bracket balance and unclosed strings
            boolean inString = false;
            char quoteChar = 0;
            for (int c = 0; c < line.length(); c++) {
                char ch = line.charAt(c);
                if (ch == '\\' && c + 1 < line.length()) {
                    c++;
                    continue;
                }
                if (ch == '"' || ch == '\'') {
                    if (!inString) {
                        inString = true;
                        quoteChar = ch;
                    } else if (ch == quoteChar) {
                        inString = false;
                    }
                    continue;
                }
                if (!inString) {
                    if (ch == '{') braceDepth++;
                    else if (ch == '}') braceDepth--;
                    else if (ch == '(') parenDepth++;
                    else if (ch == ')') parenDepth--;
                    else if (ch == '[') bracketDepth++;
                    else if (ch == ']') bracketDepth--;
                }
            }
            if (inString && !line.trim().startsWith("//") && !line.trim().startsWith("#") && !line.trim().startsWith("*")) {
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

            // Empty catch block
            if (JAVA_EMPTY_CATCH.matcher(line).find()) {
                problems.add(new TerminalPane.ProblemItem(Codicons.WARNING, "Warning", "Empty catch block: exception swallowed", fullPath, lineNum, 1, "Java"));
            }

            // Redundant semicolon
            if (line.contains(";;")) {
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

                for (int j = 0; j < lines.size(); j++) {
                    if (j == i) continue; // skip declaration line
                    String otherLine = lines.get(j);

                    // Skip constructor parameter assignment: this.fieldName = ...
                    if (otherLine.contains("this." + fieldName + " =") || otherLine.contains("this." + fieldName + "=")) {
                        continue;
                    }
                    // Skip method / constructor parameter declaration: ... fieldName, or ... fieldName)
                    if (otherLine.contains(" " + fieldName + ",") || otherLine.contains(" " + fieldName + ")")) {
                        continue;
                    }

                    if (fieldWord.matcher(otherLine).find()) {
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

            Path tempClasses = Files.createTempDirectory("auraorbit_diag");
            tempClasses.toFile().deleteOnExit();

            String cp = System.getProperty("java.class.path");
            List<String> options = Arrays.asList(
                    "-proc:none",
                    "-Xlint:all",
                    "-classpath", cp != null ? cp : ".",
                    "-d", tempClasses.toString()
            );

            JavaCompiler.CompilationTask task = compiler.getTask(null, fm, diagnostics, options, null, units);
            task.call();

            for (Diagnostic<? extends JavaFileObject> d : diagnostics.getDiagnostics()) {
                Codicons icon = Codicons.INFO;
                String severity = "Info";
                if (d.getKind() == Diagnostic.Kind.ERROR) {
                    icon = Codicons.ERROR;
                    severity = "Error";
                } else if (d.getKind() == Diagnostic.Kind.WARNING || d.getKind() == Diagnostic.Kind.MANDATORY_WARNING) {
                    icon = Codicons.WARNING;
                    severity = "Warning";
                }

                String sourcePath = "source";
                if (d.getSource() != null) {
                    try {
                        sourcePath = Paths.get(d.getSource().toUri()).toAbsolutePath().normalize().toString();
                    } catch (Exception e) {
                        sourcePath = d.getSource().getName();
                    }
                }

                results.add(new TerminalPane.ProblemItem(
                        icon, severity,
                        d.getMessage(Locale.getDefault()),
                        sourcePath,
                        (int) Math.max(1, d.getLineNumber()),
                        (int) Math.max(1, d.getColumnNumber()),
                        "javac"
                ));
            }
            try { fm.close(); } catch (Exception ignored) {}
        } catch (Exception ignored) {}

        return results;
    }
}
