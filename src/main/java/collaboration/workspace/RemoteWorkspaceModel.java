package collaboration.workspace;

import collaboration.model.VirtualFileNode;
import javafx.scene.control.TreeItem;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Volatile in-memory virtual workspace model for remote guest sessions.
 * Never writes remote files to disk; manages virtual buffer memory and purges
 * completely upon disconnect.
 */
public class RemoteWorkspaceModel {

    private VirtualFileNode rootNode;
    // fileUri -> volatile in-memory document content
    private final Map<String, String> virtualBuffers = new ConcurrentHashMap<>();
    // fileUri -> current local revision
    private final Map<String, Long> localRevisions = new ConcurrentHashMap<>();

    public RemoteWorkspaceModel() {}

    /**
     * Builds a VirtualFileNode hierarchy from a local host directory tree
     * (used by Host when transmitting initial TREE_SYNC to guests).
     */
    public static VirtualFileNode buildFromLocalDirectory(File rootDir, int maxDepth) {
        if (rootDir == null || !rootDir.exists()) {
            return null;
        }
        return scanDirectoryRecursive(rootDir, rootDir.toPath(), 0, maxDepth);
    }

    private static VirtualFileNode scanDirectoryRecursive(File file, Path rootPath, int depth, int maxDepth) {
        String relative = rootPath.relativize(file.toPath()).toString();
        if (relative.isEmpty()) {
            relative = file.getName();
        }

        VirtualFileNode node = new VirtualFileNode(
                file.getName().isEmpty() ? file.getPath() : file.getName(),
                relative,
                file.isDirectory(),
                file.isFile() ? file.length() : 0L
        );

        if (file.isDirectory() && depth < maxDepth) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    // Filter hidden git or build caches from bloating tree sync
                    if (child.getName().startsWith(".git") || child.getName().equals("target") || child.getName().equals(".idea")) {
                        continue;
                    }
                    node.addChild(scanDirectoryRecursive(child, rootPath, depth + 1, maxDepth));
                }
            }
        }
        return node;
    }

    /**
     * Converts a VirtualFileNode tree into a JavaFX TreeItem hierarchy for the guest UI.
     */
    public static TreeItem<VirtualFileNode> toTreeItem(VirtualFileNode node) {
        if (node == null) return null;
        TreeItem<VirtualFileNode> item = new TreeItem<>(node);
        item.setExpanded(true);

        if (node.getChildren() != null) {
            for (VirtualFileNode child : node.getChildren()) {
                item.getChildren().add(toTreeItem(child));
            }
        }
        return item;
    }

    public synchronized void setRootNode(VirtualFileNode rootNode) {
        this.rootNode = rootNode;
    }

    public synchronized VirtualFileNode getRootNode() {
        return rootNode;
    }

    public synchronized void setDocumentContent(String fileUri, String content, long revision) {
        virtualBuffers.put(fileUri, content);
        localRevisions.put(fileUri, revision);
    }

    public synchronized String getDocumentContent(String fileUri) {
        return virtualBuffers.get(fileUri);
    }

    public synchronized long getRevision(String fileUri) {
        return localRevisions.getOrDefault(fileUri, 0L);
    }

    public synchronized void updateRevision(String fileUri, long revision) {
        localRevisions.put(fileUri, revision);
    }

    /**
     * Purges all volatile memory buffers when the session ends.
     */
    public synchronized void clear() {
        virtualBuffers.clear();
        localRevisions.clear();
        rootNode = null;
    }
}
