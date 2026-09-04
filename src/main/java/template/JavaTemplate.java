package template;

import java.util.List;

/**
 * Concrete template for Java source files.
 */
public class JavaTemplate extends Template {

    @Override
    protected List<String> generateHeader(String fileName) {
        String className = extractClassName(fileName);
        return List.of(
                "/**",
                " * Auto-generated file: " + fileName,
                " * Created with Console File Editor",
                " */",
                "import java.util.*;",
                ""
        );
    }

    @Override
    protected List<String> generateBody(String fileName) {
        String className = extractClassName(fileName);
        return List.of(
                "public class " + className + " {",
                "    public static void main(String[] args) {",
                "        System.out.println(\"Hello from " + className + "!\");",
                "    }",
                "}"
        );
    }

    private String extractClassName(String fileName) {
        if (fileName == null || fileName.isBlank()) return "MainClass";
        String clean = fileName.trim();
        int dot = clean.lastIndexOf('.');
        if (dot > 0) clean = clean.substring(0, dot);
        // Replace non-alphanumeric chars
        clean = clean.replaceAll("[^a-zA-Z0-9_]", "");
        if (clean.isEmpty() || Character.isDigit(clean.charAt(0))) {
            clean = "Class" + clean;
        }
        return Character.toUpperCase(clean.charAt(0)) + clean.substring(1);
    }

    @Override
    public String getTemplateType() {
        return "Java Source File";
    }

    @Override
    public String getDefaultExtension() {
        return "java";
    }
}
