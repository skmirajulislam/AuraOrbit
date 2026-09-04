package service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Builds safe, platform-neutral shell commands for common runnable source files. */
public final class CodeExecutionService {
    private static final Pattern JAVA_PACKAGE = Pattern.compile("(?m)^\\s*package\\s+([A-Za-z_$][\\w$]*(?:\\.[A-Za-z_$][\\w$]*)*)\\s*;");

    public record ExecutionPlan(String command, String missingTool, String message) {
        public boolean isRunnable() { return command != null; }
    }

    public ExecutionPlan createPlan(Path source) {
        if (source == null || !Files.isRegularFile(source)) {
            return new ExecutionPlan(null, null, "Save the active file before running it.");
        }
        String name = source.getFileName().toString();
        String extension = extensionOf(name);
        return switch (extension) {
            case "java" -> javaPlan(source);
            case "py" -> interpreterPlan(source, "python3", "python", "Python");
            case "js", "mjs", "cjs" -> interpreterPlan(source, "node", "node", "Node.js");
            case "sh" -> interpreterPlan(source, "bash", "bash", "Bash");
            case "rb" -> interpreterPlan(source, "ruby", "ruby", "Ruby");
            case "c" -> compiledPlan(source, "gcc", "GCC", "c");
            case "cpp", "cc", "cxx" -> compiledPlan(source, "g++", "G++", "cpp");
            default -> new ExecutionPlan(null, null,
                    "No runner is configured for ." + (extension.isEmpty() ? "(no extension)" : extension)
                            + ". Supported: Java, Python, JavaScript, Bash, Ruby, C, and C++.");
        };
    }

    /** Lightweight capability check used by the Run button state. */
    public boolean isRunnable(Path source) {
        if (source == null || !Files.isRegularFile(source)) return false;
        return switch (extensionOf(source.getFileName().toString())) {
            case "java" -> isAvailable("javac") && isAvailable("java");
            case "py" -> isAvailable("python3") || isAvailable("python");
            case "js", "mjs", "cjs" -> isAvailable("node");
            case "sh" -> isAvailable("bash");
            case "rb" -> isAvailable("ruby");
            case "c" -> isAvailable("gcc");
            case "cpp", "cc", "cxx" -> isAvailable("g++");
            default -> false;
        };
    }

    private ExecutionPlan javaPlan(Path source) {
        if (!isAvailable("javac")) return new ExecutionPlan(null, "javac", "Java JDK compiler (javac) was not found on PATH.");
        if (!isAvailable("java")) return new ExecutionPlan(null, "java", "Java runtime was not found on PATH.");
        try {
            String simpleName = source.getFileName().toString().replaceFirst("\\.java$", "");
            String text = Files.readString(source);
            Matcher matcher = JAVA_PACKAGE.matcher(text);
            String mainClass = matcher.find() ? matcher.group(1) + "." + simpleName : simpleName;
            Path output = Files.createTempDirectory("auraorbit-java-");
            output.toFile().deleteOnExit();
            return new ExecutionPlan("javac -d " + quote(output) + " " + quote(source)
                    + " && java -cp " + quote(output) + " " + quote(mainClass), null, null);
        } catch (IOException exception) {
            return new ExecutionPlan(null, null, "Unable to prepare Java execution: " + exception.getMessage());
        }
    }

    private ExecutionPlan interpreterPlan(Path source, String preferredTool, String fallbackTool, String displayName) {
        String tool = isAvailable(preferredTool) ? preferredTool : (isAvailable(fallbackTool) ? fallbackTool : null);
        if (tool == null) return new ExecutionPlan(null, preferredTool, displayName + " was not found on PATH.");
        return new ExecutionPlan(tool + " " + quote(source), null, null);
    }

    private ExecutionPlan compiledPlan(Path source, String compiler, String displayName, String kind) {
        if (!isAvailable(compiler)) return new ExecutionPlan(null, compiler, displayName + " was not found on PATH.");
        try {
            Path output = Files.createTempFile("auraorbit-" + kind + "-", isWindows() ? ".exe" : "");
            Files.deleteIfExists(output);
            output.toFile().deleteOnExit();
            return new ExecutionPlan(compiler + " " + quote(source) + " -o " + quote(output)
                    + " && " + quote(output), null, null);
        } catch (IOException exception) {
            return new ExecutionPlan(null, null, "Unable to prepare executable: " + exception.getMessage());
        }
    }

    private boolean isAvailable(String command) {
        try {
            Process process = new ProcessBuilder(command, "--version").redirectErrorStream(true).start();
            return process.waitFor(3, TimeUnit.SECONDS) && process.exitValue() == 0;
        } catch (IOException exception) {
            return false;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private static String extensionOf(String name) {
        int index = name.lastIndexOf('.');
        return index < 0 ? "" : name.substring(index + 1).toLowerCase(Locale.ROOT);
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }

    private static String quote(Object path) {
        return "\"" + String.valueOf(path).replace("\"", "\\\"") + "\"";
    }
}
