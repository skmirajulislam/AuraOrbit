package model;

import java.nio.file.Path;
import java.util.Objects;

/**
 * Encapsulates file metadata including path, name, extension, and modification state.
 */
public class Document {
    private Path filePath;
    private String fileName;
    private String fileType;
    private boolean readOnly;

    public Document(Path filePath) {
        this.filePath = Objects.requireNonNull(filePath, "filePath cannot be null");
        this.fileName = filePath.getFileName() != null ? filePath.getFileName().toString() : "untitled";
        this.fileType = extractExtension(this.fileName);
        this.readOnly = false;
    }

    public Document(String fileName) {
        this.filePath = null;
        this.fileName = (fileName == null || fileName.isBlank()) ? "untitled.txt" : fileName.trim();
        this.fileType = extractExtension(this.fileName);
        this.readOnly = false;
    }

    private static String extractExtension(String name) {
        int dotIndex = name.lastIndexOf('.');
        if (dotIndex > 0 && dotIndex < name.length() - 1) {
            return name.substring(dotIndex + 1).toLowerCase();
        }
        return "txt";
    }

    public synchronized Path getFilePath() {
        return filePath;
    }

    public synchronized void setFilePath(Path filePath) {
        this.filePath = filePath;
        if (filePath != null && filePath.getFileName() != null) {
            this.fileName = filePath.getFileName().toString();
            this.fileType = extractExtension(this.fileName);
        }
    }

    public synchronized String getFileName() {
        return fileName;
    }

    public synchronized void setFileName(String fileName) {
        this.fileName = fileName;
        this.fileType = extractExtension(fileName);
    }

    public synchronized String getFileType() {
        return fileType;
    }

    public synchronized boolean isReadOnly() {
        return readOnly;
    }

    public synchronized void setReadOnly(boolean readOnly) {
        this.readOnly = readOnly;
    }

    public synchronized boolean isPersisted() {
        return filePath != null;
    }

    @Override
    public String toString() {
        return "Document{" +
                "fileName='" + fileName + '\'' +
                ", fileType='" + fileType + '\'' +
                ", filePath=" + (filePath != null ? filePath.toAbsolutePath() : "[Unsaved]") +
                '}';
    }
}
