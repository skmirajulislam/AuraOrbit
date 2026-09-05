package service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.function.Consumer;

/**
 * Custom Scripting & Automation Engine for AuraOrbit.
 * Discovers and executes custom user and workspace automation scripts
 * (.py, .sh, .js, .rb) with contextual environment variables.
 */
public class ScriptPluginService {

    public record ScriptCommand(String name, Path scriptPath, String interpreter) {}

    /**
     * Scans both user home and workspace folders for executable scripts.
     */
    public static List<ScriptCommand> discoverScripts(Path workspaceRoot) {
        List<ScriptCommand> result = new ArrayList<>();

        // 1. User global ~/.auraorbit/scripts
        Path userScripts = Paths.get(System.getProperty("user.home"), ".auraorbit", "scripts");
        scanDirectory(userScripts, result);

        // 2. Workspace .auraorbit/scripts
        if (workspaceRoot != null && Files.isDirectory(workspaceRoot)) {
            Path wsAuraScripts = workspaceRoot.resolve(".auraorbit").resolve("scripts");
            scanDirectory(wsAuraScripts, result);

            Path wsScripts = workspaceRoot.resolve(".scripts");
            scanDirectory(wsScripts, result);
        }

        return Collections.unmodifiableList(result);
    }

    private static void scanDirectory(Path dir, List<ScriptCommand> list) {
        if (dir == null || !Files.isDirectory(dir)) return;

        try (var stream = Files.newDirectoryStream(dir)) {
            for (Path file : stream) {
                if (!Files.isRegularFile(file)) continue;

                String fileName = file.getFileName().toString();
                String lower = fileName.toLowerCase();
                String interpreter = null;

                if (lower.endsWith(".sh") || lower.endsWith(".bash")) {
                    interpreter = "bash";
                } else if (lower.endsWith(".py")) {
                    interpreter = "python3";
                } else if (lower.endsWith(".js") || lower.endsWith(".mjs")) {
                    interpreter = "node";
                } else if (lower.endsWith(".rb")) {
                    interpreter = "ruby";
                }

                if (interpreter != null) {
                    String baseName = fileName.replaceFirst("\\.[^.]+$", "");
                    list.add(new ScriptCommand(baseName, file.toAbsolutePath().normalize(), interpreter));
                }
            }
        } catch (Exception ignored) {}
    }

    /**
     * Executes a script asynchronously and streams output.
     */
    public static void executeScriptAsync(ScriptCommand script, Path activeFile, Path workspaceRoot,
                                          Consumer<String> onOutput, Consumer<Integer> onComplete) {
        if (script == null) return;

        Thread.ofVirtual().start(() -> {
            try {
                List<String> cmd = List.of(script.interpreter(), script.scriptPath().toString());
                ProcessBuilder pb = new ProcessBuilder(cmd);
                if (workspaceRoot != null && Files.isDirectory(workspaceRoot)) {
                    pb.directory(workspaceRoot.toFile());
                } else if (script.scriptPath().getParent() != null) {
                    pb.directory(script.scriptPath().getParent().toFile());
                }

                Map<String, String> env = pb.environment();
                if (activeFile != null) {
                    env.put("AURA_ACTIVE_FILE", activeFile.toAbsolutePath().normalize().toString());
                }
                if (workspaceRoot != null) {
                    env.put("AURA_WORKSPACE", workspaceRoot.toAbsolutePath().normalize().toString());
                }

                pb.redirectErrorStream(true);
                Process proc = pb.start();

                try (BufferedReader reader = new BufferedReader(new InputStreamReader(proc.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (onOutput != null) onOutput.accept(line);
                    }
                }

                int exitCode = proc.waitFor();
                if (onComplete != null) onComplete.accept(exitCode);
            } catch (Exception e) {
                if (onOutput != null) onOutput.accept("Error running script: " + e.getMessage());
                if (onComplete != null) onComplete.accept(-1);
            }
        });
    }
}
