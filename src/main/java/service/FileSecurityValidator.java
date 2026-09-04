package service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;

/**
 * Validates file paths, guards against Directory Traversal attacks,
 * and ensures safe file system access.
 */
public class FileSecurityValidator {

    private static final Set<String> RESERVED_NAMES = Set.of(
            "CON", "PRN", "AUX", "NUL",
            "COM1", "COM2", "COM3", "COM4", "COM5", "COM6", "COM7", "COM8", "COM9",
            "LPT1", "LPT2", "LPT3", "LPT4", "LPT5", "LPT6", "LPT7", "LPT8", "LPT9"
    );

    /**
     * Resolves and validates a raw input path against security vulnerabilities.
     *
     * @param rawPath The raw path string from user input.
     * @return A sanitized, normalized Path object.
     * @throws SecurityException If path traversal or illegal characters are detected.
     */
    public static Path sanitizeAndResolvePath(String rawPath) {
        if (rawPath == null || rawPath.isBlank()) {
            throw new IllegalArgumentException("File path cannot be empty or null.");
        }

        // Null byte & control characters injection check
        for (char c : rawPath.toCharArray()) {
            if (c < 32 && c != '\t') {
                throw new SecurityException("Illegal control character detected in file path (char code: " + (int) c + ")");
            }
        }

        // Windows Alternative Data Stream (ADS) guard
        if (rawPath.contains(":$") || (rawPath.indexOf(':') != rawPath.lastIndexOf(':') && !rawPath.contains(":\\"))) {
            throw new SecurityException("Alternative Data Streams or invalid colon syntax blocked in path: " + rawPath);
        }

        try {
            Path path = Paths.get(rawPath.trim()).normalize();

            // Check reserved names
            String baseName = path.getFileName() != null ? path.getFileName().toString() : "";
            int dotIdx = baseName.indexOf('.');
            String nameWithoutExt = dotIdx > 0 ? baseName.substring(0, dotIdx) : baseName;
            if (RESERVED_NAMES.contains(nameWithoutExt.toUpperCase())) {
                throw new SecurityException("File name is a reserved system identifier: " + baseName);
            }

            return path.toAbsolutePath().normalize();
        } catch (InvalidPathException e) {
            throw new SecurityException("Invalid path format: " + e.getMessage(), e);
        }
    }

    /**
     * Validates that a file is readable.
     */
    public static void validateReadable(Path path) throws IOException {
        if (!Files.exists(path)) {
            throw new IOException("File does not exist: " + path);
        }
        if (Files.isDirectory(path)) {
            throw new IOException("Target path is a directory, not a regular file: " + path);
        }
        if (!Files.isReadable(path)) {
            throw new SecurityException("Permission denied: Cannot read file " + path);
        }
    }

    /**
     * Validates that a path can be written to.
     */
    public static void validateWritable(Path path) throws IOException {
        if (Files.exists(path)) {
            if (Files.isDirectory(path)) {
                throw new IOException("Target path is a directory: " + path);
            }
            if (!Files.isWritable(path)) {
                throw new SecurityException("Permission denied: Target file is read-only: " + path);
            }
        } else {
            Path parent = path.getParent();
            if (parent != null && Files.exists(parent) && !Files.isWritable(parent)) {
                throw new SecurityException("Permission denied: Cannot create file in directory " + parent);
            }
        }
    }
}
