package com.codetutor.backend.scanner.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * Hierarchical tree node representing a file or directory in a project workspace.
 */
public class FileNode {

    private String name;
    private String path;
    private String type; // "FILE" or "DIRECTORY"
    private Long size;
    private String extension;
    private List<FileNode> children;

    public FileNode() {
        this.children = new ArrayList<>();
    }

    public FileNode(String name, String path, String type, Long size, String extension) {
        this.name = name;
        this.path = path;
        this.type = type;
        this.size = size;
        this.extension = extension;
        this.children = new ArrayList<>();
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getPath() { return path; }
    public void setPath(String path) { this.path = path; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public Long getSize() { return size; }
    public void setSize(Long size) { this.size = size; }

    public String getExtension() { return extension; }
    public void setExtension(String extension) { this.extension = extension; }

    public List<FileNode> getChildren() { return children; }
    public void setChildren(List<FileNode> children) { this.children = children; }

    public void addChild(FileNode child) {
        if (this.children == null) {
            this.children = new ArrayList<>();
        }
        this.children.add(child);
    }
}
