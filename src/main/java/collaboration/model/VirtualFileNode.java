package collaboration.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Lightweight, zero-disk-footprint representation of a remote file tree node.
 */
public class VirtualFileNode {

    private String name;
    private String relativePath;
    private boolean isDirectory;
    private long sizeBytes;
    private List<VirtualFileNode> children;

    public VirtualFileNode() {
        this.children = new ArrayList<>();
    }

    public VirtualFileNode(String name, String relativePath, boolean isDirectory, long sizeBytes) {
        this.name = name;
        this.relativePath = relativePath;
        this.isDirectory = isDirectory;
        this.sizeBytes = sizeBytes;
        this.children = isDirectory ? new ArrayList<>() : null;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getRelativePath() {
        return relativePath;
    }

    public void setRelativePath(String relativePath) {
        this.relativePath = relativePath;
    }

    public boolean isDirectory() {
        return isDirectory;
    }

    public void setDirectory(boolean directory) {
        isDirectory = directory;
    }

    public long getSizeBytes() {
        return sizeBytes;
    }

    public void setSizeBytes(long sizeBytes) {
        this.sizeBytes = sizeBytes;
    }

    public List<VirtualFileNode> getChildren() {
        return children;
    }

    public void setChildren(List<VirtualFileNode> children) {
        this.children = children;
    }

    public void addChild(VirtualFileNode child) {
        if (this.children == null) {
            this.children = new ArrayList<>();
        }
        this.children.add(child);
    }
}
