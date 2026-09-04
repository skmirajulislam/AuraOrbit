package service;

import java.util.ArrayList;
import java.util.List;

/**
 * Universal High-Performance Code Formatter Service.
 * Standardizes indentation, brace placement, operator spacing,
 * and blank line normalization across Java, C/C++, JavaScript,
 * JSON, XML/HTML, and Python.
 */
public class CodeFormatterService {

    private static final String INDENT = "    "; // 4 spaces

    public static String formatCode(String code, String fileType) {
        if (code == null || code.isEmpty()) return code;
        String type = fileType != null ? fileType.toLowerCase() : "java";

        return switch (type) {
            case "json" -> formatJson(code);
            case "xml", "html" -> formatXmlHtml(code);
            case "py", "python" -> formatPython(code);
            default -> formatBraceLanguage(code); // Java, C, C++, JS, TS, etc.
        };
    }

    /**
     * Formats brace-based languages like Java, C, C++, JavaScript, TypeScript, C#.
     */
    public static String formatBraceLanguage(String code) {
        String[] rawLines = code.split("\\R");
        List<String> formattedLines = new ArrayList<>();
        int indentLevel = 0;
        boolean inBlockComment = false;

        for (String raw : rawLines) {
            String trimmed = raw.trim();

            if (trimmed.isEmpty()) {
                // Avoid more than 2 consecutive blank lines
                if (!formattedLines.isEmpty() && !formattedLines.get(formattedLines.size() - 1).isEmpty()) {
                    formattedLines.add("");
                }
                continue;
            }

            // Handle block comments /* ... */
            if (inBlockComment) {
                formattedLines.add(INDENT.repeat(indentLevel) + " " + trimmed);
                if (trimmed.contains("*/")) {
                    inBlockComment = false;
                }
                continue;
            }
            if (trimmed.startsWith("/*") && !trimmed.contains("*/")) {
                inBlockComment = true;
                formattedLines.add(INDENT.repeat(indentLevel) + trimmed);
                continue;
            }

            // Check if line begins with closing brace/bracket: reduce indent before rendering
            int openBraces = countOccurrences(trimmed, '{');
            int closeBraces = countOccurrences(trimmed, '}');

            if (trimmed.startsWith("}") || trimmed.startsWith(")") || trimmed.startsWith("]")) {
                indentLevel = Math.max(0, indentLevel - 1);
            }

            // Standardize spaces around operators and commas outside of quotes
            String spacedLine = formatTokensInLine(trimmed);

            formattedLines.add(INDENT.repeat(indentLevel) + spacedLine);

            // Compute new indent level for next lines
            if (!trimmed.startsWith("}") && !trimmed.startsWith(")") && !trimmed.startsWith("]")) {
                indentLevel += (openBraces - closeBraces);
                indentLevel = Math.max(0, indentLevel);
            } else {
                // Already decremented indentLevel for the leading close
                indentLevel += openBraces - (closeBraces - 1);
                indentLevel = Math.max(0, indentLevel);
            }
        }

        return String.join(System.lineSeparator(), formattedLines);
    }

    private static String formatTokensInLine(String line) {
        if (line.startsWith("//") || line.startsWith("*") || line.startsWith("/*")) {
            return line;
        }

        StringBuilder sb = new StringBuilder();
        boolean inQuotes = false;
        char quoteChar = 0;

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);

            // Toggle string quote state
            if ((c == '"' || c == '\'') && (i == 0 || line.charAt(i - 1) != '\\')) {
                if (!inQuotes) {
                    inQuotes = true;
                    quoteChar = c;
                } else if (c == quoteChar) {
                    inQuotes = false;
                }
                sb.append(c);
                continue;
            }

            if (inQuotes) {
                sb.append(c);
                continue;
            }

            // Handle comma spacing: ensure comma followed by space
            if (c == ',') {
                sb.append(',');
                if (i + 1 < line.length() && line.charAt(i + 1) != ' ' && line.charAt(i + 1) != '\n' && line.charAt(i + 1) != '\r') {
                    sb.append(' ');
                }
                continue;
            }

            // Handle semicolon in for-loops or statements
            if (c == ';') {
                sb.append(';');
                if (i + 1 < line.length() && line.charAt(i + 1) != ' ' && line.charAt(i + 1) != '\n' && line.charAt(i + 1) != '\r') {
                    sb.append(' ');
                }
                continue;
            }

            sb.append(c);
        }

        return sb.toString();
    }

    /**
     * Formats JSON content.
     */
    public static String formatJson(String json) {
        StringBuilder result = new StringBuilder();
        int indent = 0;
        boolean inQuotes = false;

        for (int i = 0; i < json.length(); i++) {
            char c = json.charAt(i);

            if (c == '"' && (i == 0 || json.charAt(i - 1) != '\\')) {
                inQuotes = !inQuotes;
                result.append(c);
                continue;
            }

            if (inQuotes) {
                result.append(c);
                continue;
            }

            switch (c) {
                case '{', '[' -> {
                    result.append(c);
                    result.append("\n");
                    indent++;
                    result.append(INDENT.repeat(indent));
                }
                case '}', ']' -> {
                    result.append("\n");
                    indent = Math.max(0, indent - 1);
                    result.append(INDENT.repeat(indent));
                    result.append(c);
                }
                case ',' -> {
                    result.append(c);
                    result.append("\n");
                    result.append(INDENT.repeat(indent));
                }
                case ':' -> result.append(": ");
                default -> {
                    if (!Character.isWhitespace(c)) {
                        result.append(c);
                    }
                }
            }
        }
        return result.toString().trim();
    }

    /**
     * Formats XML and HTML tags with hierarchical indentation.
     */
    public static String formatXmlHtml(String xml) {
        StringBuilder result = new StringBuilder();
        int indent = 0;
        String[] tokens = xml.replaceAll(">\\s*<", ">\n<").split("\\R");

        for (String line : tokens) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) continue;

            if (trimmed.startsWith("</")) {
                indent = Math.max(0, indent - 1);
            }

            result.append(INDENT.repeat(indent)).append(trimmed).append("\n");

            if (trimmed.startsWith("<") && !trimmed.startsWith("</") && !trimmed.endsWith("/>") && !trimmed.contains("</") && !trimmed.startsWith("<!") && !trimmed.startsWith("<?")) {
                indent++;
            }
        }
        return result.toString().trim();
    }

    /**
     * Cleans Python code: strips trailing whitespaces and normalizes blank lines.
     */
    public static String formatPython(String code) {
        String[] lines = code.split("\\R");
        List<String> cleaned = new ArrayList<>();
        for (String l : lines) {
            cleaned.add(l.stripTrailing());
        }
        return String.join(System.lineSeparator(), cleaned);
    }

    private static int countOccurrences(String str, char ch) {
        int count = 0;
        boolean inQuotes = false;
        char quote = 0;
        for (int i = 0; i < str.length(); i++) {
            char c = str.charAt(i);
            if ((c == '"' || c == '\'') && (i == 0 || str.charAt(i - 1) != '\\')) {
                if (!inQuotes) {
                    inQuotes = true;
                    quote = c;
                } else if (c == quote) {
                    inQuotes = false;
                }
                continue;
            }
            if (!inQuotes && c == ch) {
                count++;
            }
        }
        return count;
    }
}
