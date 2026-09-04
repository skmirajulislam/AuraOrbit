package service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Builds safe, platform-neutral process steps for common runnable source files. */
public final class CodeExecutionService {
    private static final Pattern JAVA_PACKAGE = Pattern.compile("(?m)^\\s*package\\s+([A-Za-z_$][\\w$]*(?:\\.[A-Za-z_$][\\w$]*)*)\\s*;");
    private static final Pattern ARG_TOKEN = Pattern.compile("\"([^\"]*)\"|'([^']*)'|\\S+");

    public record ExecutionPlan(String command, List<List<String>> steps, String missingTool, String message) {
        public boolean isRunnable() {
            return command != null && steps != null && !steps.isEmpty();
        }

        public ExecutionPlan withProgramArguments(List<String> programArgs) {
            if (!isRunnable() || programArgs == null || programArgs.isEmpty()) {
                return this;
            }
            List<List<String>> copy = new ArrayList<>(steps.size());
            for (int i = 0; i < steps.size(); i++) {
                List<String> step = new ArrayList<>(steps.get(i));
                if (i == steps.size() - 1) {
                    step.addAll(programArgs);
                }
                copy.add(List.copyOf(step));
            }
            StringBuilder display = new StringBuilder(command);
            for (String arg : programArgs) {
                display.append(' ').append(quote(arg));
            }
            return new ExecutionPlan(display.toString(), List.copyOf(copy), missingTool, message);
        }
    }

    public ExecutionPlan createPlan(Path source) {
        if (source == null || !Files.isRegularFile(source)) {
            return new ExecutionPlan(null, null, null, "Save the active file before running it.");
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
            default -> new ExecutionPlan(null, null, null,
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

    public static List<String> parseProgramArguments(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        List<String> args = new ArrayList<>();
        Matcher matcher = ARG_TOKEN.matcher(raw.trim());
        while (matcher.find()) {
            if (matcher.group(1) != null) {
                args.add(matcher.group(1));
            } else if (matcher.group(2) != null) {
                args.add(matcher.group(2));
            } else {
                args.add(matcher.group());
            }
        }
        return List.copyOf(args);
    }

    private ExecutionPlan javaPlan(Path source) {
        if (!isAvailable("javac")) return new ExecutionPlan(null, null, "javac", "Java JDK compiler (javac) was not found on PATH.");
        if (!isAvailable("java")) return new ExecutionPlan(null, null, "java", "Java runtime was not found on PATH.");
        try {
            String simpleName = source.getFileName().toString().replaceFirst("\\.java$", "");
            String text = Files.readString(source);
            Matcher matcher = JAVA_PACKAGE.matcher(text);
            String mainClass = matcher.find() ? matcher.group(1) + "." + simpleName : simpleName;
            Path output = Files.createTempDirectory("auraorbit-java-");
            output.toFile().deleteOnExit();
            List<List<String>> steps = List.of(
                    List.of("javac", "-d", output.toString(), source.toString()),
                    List.of("java", "-cp", output.toString(), mainClass)
            );
            return new ExecutionPlan("javac -d " + quote(output) + " " + quote(source)
                    + " && java -cp " + quote(output) + " " + quote(mainClass), steps, null, null);
        } catch (IOException exception) {
            return new ExecutionPlan(null, null, null, "Unable to prepare Java execution: " + exception.getMessage());
        }
    }

    private ExecutionPlan interpreterPlan(Path source, String preferredTool, String fallbackTool, String displayName) {
        String tool = isAvailable(preferredTool) ? preferredTool : (isAvailable(fallbackTool) ? fallbackTool : null);
        if (tool == null) return new ExecutionPlan(null, null, preferredTool, displayName + " was not found on PATH.");
        return new ExecutionPlan(tool + " " + quote(source), List.of(List.of(tool, source.toString())), null, null);
    }

    private ExecutionPlan compiledPlan(Path source, String compiler, String displayName, String kind) {
        if (!isAvailable(compiler)) return new ExecutionPlan(null, null, compiler, displayName + " was not found on PATH.");
        try {
            Path output = Files.createTempFile("auraorbit-" + kind + "-", isWindows() ? ".exe" : "");
            Files.deleteIfExists(output);
            output.toFile().deleteOnExit();
            List<List<String>> steps = List.of(
                    List.of(compiler, source.toString(), "-o", output.toString()),
                    List.of(output.toString())
            );
            return new ExecutionPlan(compiler + " " + quote(source) + " -o " + quote(output)
                    + " && " + quote(output), steps, null, null);
        } catch (IOException exception) {
            return new ExecutionPlan(null, null, null, "Unable to prepare executable: " + exception.getMessage());
        }
    }

    private static final Map<String, Boolean> TOOL_AVAILABILITY_CACHE = new ConcurrentHashMap<>();

    static {
        // Pre-warm common compilers/interpreters asynchronously on a virtual daemon thread
        Thread.ofVirtual().start(() -> {
            for (String tool : List.of("javac", "java", "python3", "python", "node", "bash", "ruby", "gcc", "g++")) {
                try {
                    Process process = new ProcessBuilder(tool, "--version").redirectErrorStream(true).start();
                    boolean ok = process.waitFor(1500, TimeUnit.MILLISECONDS) && process.exitValue() == 0;
                    TOOL_AVAILABILITY_CACHE.put(tool, ok);
                } catch (Exception ignored) {
                    TOOL_AVAILABILITY_CACHE.put(tool, false);
                }
            }
        });
    }

    public boolean isToolAvailable(String command) {
        if (command == null || command.isBlank()) return false;
        return TOOL_AVAILABILITY_CACHE.computeIfAbsent(command, this::checkToolAvailable);
    }

    private boolean checkToolAvailable(String command) {
        try {
            Process process = new ProcessBuilder(command, "--version").redirectErrorStream(true).start();
            return process.waitFor(2, TimeUnit.SECONDS) && process.exitValue() == 0;
        } catch (IOException exception) {
            return false;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private boolean isAvailable(String command) {
        return isToolAvailable(command);
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
