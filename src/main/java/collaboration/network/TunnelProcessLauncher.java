package collaboration.network;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Zero-config Cloudflare Quick Tunnel process manager.
 * Spawns an ephemeral `cloudflared` tunnel, parses the generated public ingress URL,
 * and ensures guaranteed process termination on shutdown.
 */
public class TunnelProcessLauncher {

    private static final Pattern TUNNEL_URL_PATTERN =
            Pattern.compile("https://([a-zA-Z0-9-]+)\\.trycloudflare\\.com");

    private Process process;
    private String assignedPublicUrl;
    private final int localPort;
    private final ExecutorService logExecutor = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "Cloudflare-Tunnel-Logger");
        t.setDaemon(true);
        return t;
    });

    private Thread shutdownHook;

    public TunnelProcessLauncher(int localPort) {
        this.localPort = localPort;
    }

    /**
     * Resolves the cloudflared binary location on the host system.
     */
    public static String resolveCloudflaredPath() {
        String[] candidatePaths = {
                "/opt/homebrew/bin/cloudflared",
                "/usr/local/bin/cloudflared",
                "/usr/bin/cloudflared",
                "cloudflared"
        };
        for (String candidate : candidatePaths) {
            try {
                Process test = new ProcessBuilder(candidate, "--version").start();
                boolean finished = test.waitFor(2, TimeUnit.SECONDS);
                if (!finished) {
                    test.destroyForcibly();
                    continue;
                }
                if (test.exitValue() == 0) {
                    return candidate;
                }
            } catch (Exception ignored) {}
        }
        return "cloudflared"; // Fallback to PATH
    }

    /**
     * Starts the Cloudflare Quick Tunnel and blocks until the assigned URL is discovered
     * or the timeout expires (default 30 seconds).
     */
    public CompletableFuture<String> startAsync(int timeoutSeconds) {
        CompletableFuture<String> future = new CompletableFuture<>();
        String binary = resolveCloudflaredPath();

        ProcessBuilder pb = new ProcessBuilder(
                binary, "tunnel", "--url", "http://127.0.0.1:" + localPort
        );
        pb.redirectErrorStream(true);

        try {
            process = pb.start();

            // Install JVM shutdown hook to prevent zombie daemon processes
            shutdownHook = new Thread(this::stop, "Cloudflare-Tunnel-Shutdown");
            Runtime.getRuntime().addShutdownHook(shutdownHook);

            logExecutor.submit(() -> {
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        Matcher matcher = TUNNEL_URL_PATTERN.matcher(line);
                        if (matcher.find() && !future.isDone()) {
                            assignedPublicUrl = matcher.group(0);
                            future.complete(assignedPublicUrl);
                        }
                    }
                } catch (Exception e) {
                    if (!future.isDone()) {
                        future.completeExceptionally(e);
                    }
                }
            });

            // Schedule timeout fallback
            ScheduledExecutorService watcher = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "Tunnel-Timeout-Watcher");
                t.setDaemon(true);
                return t;
            });
            watcher.schedule(() -> {
                try {
                    if (!future.isDone()) {
                        future.completeExceptionally(new TimeoutException(
                                "Cloudflare tunnel connection timed out after " + timeoutSeconds + "s"));
                        stop();
                    }
                } finally {
                    watcher.shutdown();
                }
            }, timeoutSeconds, TimeUnit.SECONDS);

        } catch (Exception e) {
            future.completeExceptionally(e);
        }

        return future;
    }

    /**
     * Forcibly terminates the cloudflared process and releases system resources.
     */
    public synchronized void stop() {
        if (shutdownHook != null) {
            try {
                Runtime.getRuntime().removeShutdownHook(shutdownHook);
            } catch (Exception ignored) {}
            shutdownHook = null;
        }

        if (process != null && process.isAlive()) {
            process.destroy();
            try {
                if (!process.waitFor(2, TimeUnit.SECONDS)) {
                    process.destroyForcibly();
                }
            } catch (InterruptedException e) {
                process.destroyForcibly();
                Thread.currentThread().interrupt();
            }
        }
        logExecutor.shutdownNow();
    }

    public String getAssignedPublicUrl() {
        return assignedPublicUrl;
    }

    public boolean isRunning() {
        return process != null && process.isAlive();
    }
}
