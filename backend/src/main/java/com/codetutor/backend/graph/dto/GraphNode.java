package com.codetutor.backend.graph.dto;

public class GraphNode {
    private String id;
    private String label;
    private String group; // e.g. "CONTROLLER", "SERVICE", "REPOSITORY", "ENTITY", "OTHER"

    public GraphNode() {}

    public GraphNode(String id, String label, String group) {
        this.id = id;
        this.label = label;
        this.group = group;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }

    public String getGroup() { return group; }
    public void setGroup(String group) { this.group = group; }
}
