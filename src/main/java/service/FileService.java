package service;

import model.Document;
import model.TextBuffer;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Robust File I/O service supporting NIO.2 buffered streaming,
 * atomic save operations, backup creation, and file integrity validation.
 */
public class FileService {

    public static final int BUFFER_SIZE = 8192; // 8KB buffer for optimal I/O throughput
    private static final Charset DEFAULT_CHARSET = StandardCharsets.UTF_8;

    public static final long MAX_FILE_SIZE_BYTES = 50 * 1024 * 1024; // 50MB safety ceiling for in-memory line buffer

    /**
     * Reads a file line-by-line into a new TextBuffer with memory efficiency.
     * Guards against OutOfMemoryError for extremely large files.
     */
    public TextBuffer loadFile(Path path) throws IOException {
        FileSecurityValidator.validateReadable(path);

        long fileSize = Files.size(path);
        if (fileSize > MAX_FILE_SIZE_BYTES) {
            throw new IOException(String.format(
                    "File size (%.2f MB) exceeds safety threshold (50 MB). Opening files beyond this limit risks OutOfMemoryError in console line-buffer mode.",
                    fileSize / (1024.0 * 1024.0)
            ));
        }

        // Cap pre-allocation to prevent giant upfront heap reservation
        int estimatedLines = (int) Math.min(100_000, Math.max(32, fileSize / 50));
        List<String> lines = new ArrayList<>(estimatedLines);

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(Files.newInputStream(path), DEFAULT_CHARSET), BUFFER_SIZE)) {
            String line;
            while ((line = reader.readLine()) != null) {
                lines.add(line);
            }
        }
        TextBuffer buffer = new TextBuffer(lines);
        buffer.markClean();
        return buffer;
    }

    /**
     * Reads a file directly into a String using NIO.2 without creating intermediate line lists.
     * Ideal for GUI code editors to cut memory consumption and load time.
     */
    public String readString(Path path) throws IOException {
        FileSecurityValidator.validateReadable(path);

        long fileSize = Files.size(path);
        if (fileSize > MAX_FILE_SIZE_BYTES) {
            throw new IOException(String.format(
                    "File size (%.2f MB) exceeds safety threshold (50 MB).",
                    fileSize / (1024.0 * 1024.0)
            ));
        }

        return Files.readString(path, DEFAULT_CHARSET);
    }

    /**
     * Atomically saves a raw String to disk via a sibling temporary file.
     * Prevents partial write corruption and avoids line-splitting allocations.
     */
    public void saveStringAtomically(Path targetPath, String content, boolean createBackup) throws IOException {
        FileSecurityValidator.validateWritable(targetPath);

        Path parentDir = targetPath.getParent();
        if (parentDir != null && !Files.exists(parentDir)) {
            Files.createDirectories(parentDir);
        }

        if (parentDir != null && Files.exists(parentDir)) {
            cleanOrphanTempFiles(parentDir);
        }

        // 1. Create a backup if requested and original file exists
        if (createBackup && Files.exists(targetPath)) {
            Path backupPath = Paths.get(targetPath.toString() + ".bak");
            Files.copy(targetPath, backupPath, StandardCopyOption.REPLACE_EXISTING);
        }

        // 2. Write to sibling temp file
        String tempFileName = "." + targetPath.getFileName().toString() + ".tmp." + UUID.randomUUID().toString().substring(0, 8);
        Path tempPath = (parentDir != null) ? parentDir.resolve(tempFileName) : Paths.get(tempFileName);
        tempPath.toFile().deleteOnExit();

        try {
            try (BufferedWriter writer = Files.newBufferedWriter(
                    tempPath,
                    DEFAULT_CHARSET,
                    StandardOpenOption.CREATE_NEW,
                    StandardOpenOption.WRITE,
                    StandardOpenOption.SYNC)) {
                if (content != null) {
                    writer.write(content);
                }
                writer.flush();
            }

            // 3. Atomically replace target file
            try {
                Files.move(tempPath, targetPath, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tempPath, targetPath, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            if (Files.exists(tempPath)) {
                try {
                    Files.delete(tempPath);
                } catch (IOException ignored) {}
            }
        }
    }

    /**
     * Atomically saves the TextBuffer to disk.
     * Prevents partial write corruption by writing to a sibling temp file
     * and performing an atomic rename/move.
     *
     * @param targetPath The destination file path.
     * @param buffer The in-memory buffer to persist.
     * @param createBackup If true, keeps a .bak copy of the original file.
     */
    public void saveFileAtomically(Path targetPath, TextBuffer buffer, boolean createBackup) throws IOException {
        FileSecurityValidator.validateWritable(targetPath);

        Path parentDir = targetPath.getParent();
        if (parentDir != null && !Files.exists(parentDir)) {
            Files.createDirectories(parentDir);
        }

        // Clean up any stale temp files from previous system crashes if directory exists
        if (parentDir != null && Files.exists(parentDir)) {
            cleanOrphanTempFiles(parentDir);
        }

        // 1. Create a backup if requested and original file exists
        if (createBackup && Files.exists(targetPath)) {
            Path backupPath = Paths.get(targetPath.toString() + ".bak");
            Files.copy(targetPath, backupPath, StandardCopyOption.REPLACE_EXISTING);
        }

        // 2. Write to a temporary file in the same directory (same filesystem)
        String tempFileName = "." + targetPath.getFileName().toString() + ".tmp." + UUID.randomUUID().toString().substring(0, 8);
        Path tempPath = (parentDir != null) ? parentDir.resolve(tempFileName) : Paths.get(tempFileName);

        // Register JVM shutdown hook in case of abnormal JVM exit
        tempPath.toFile().deleteOnExit();

        try {
            try (BufferedWriter writer = Files.newBufferedWriter(
                    tempPath,
                    DEFAULT_CHARSET,
                    StandardOpenOption.CREATE_NEW,
                    StandardOpenOption.WRITE,
                    StandardOpenOption.SYNC)) {

                // Stream directly from buffer under read-lock without allocating new lists
                buffer.writeTo(writer);
                writer.flush();
            }

            // 3. Atomically replace target file
            try {
                Files.move(tempPath, targetPath, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                // Fallback for filesystems that do not support atomic move across boundaries
                Files.move(tempPath, targetPath, StandardCopyOption.REPLACE_EXISTING);
            }

            buffer.markClean();
        } finally {
            // Clean up temporary file in case of failure
            if (Files.exists(tempPath)) {
                try {
                    Files.delete(tempPath);
                } catch (IOException ignored) {}
            }
        }
    }

    private void cleanOrphanTempFiles(Path dir) {
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, ".*.tmp.*")) {
            long now = System.currentTimeMillis();
            for (Path entry : stream) {
                // Delete if older than 1 hour
                if (now - Files.getLastModifiedTime(entry).toMillis() > 3600_000L) {
                    try {
                        Files.delete(entry);
                    } catch (IOException ignored) {}
                }
            }
        } catch (Exception ignored) {}
    }

    /**
     * Retrieves file metadata and statistics.
     */
    public String getFileDetails(Document document) {
        if (document.getFilePath() == null || !Files.exists(document.getFilePath())) {
            return String.format("File: %s [Not Persisted to Disk yet]", document.getFileName());
        }

        Path path = document.getFilePath();
        try {
            BasicFileAttributes attrs = Files.readAttributes(path, BasicFileAttributes.class);
            return String.format(
                    "File: %s\nPath: %s\nSize: %,d bytes\nCreated: %s\nModified: %s\nReadable: %s | Writable: %s",
                    document.getFileName(),
                    path.toAbsolutePath(),
                    attrs.size(),
                    attrs.creationTime(),
                    attrs.lastModifiedTime(),
                    Files.isReadable(path),
                    Files.isWritable(path)
            );
        } catch (IOException e) {
            return "Unable to read attributes for: " + path.toAbsolutePath() + " (" + e.getMessage() + ")";
        }
    }
}
