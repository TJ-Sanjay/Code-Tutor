package com.codetutor.backend.graph.dto;

import java.util.List;

public class DependencyGraph {
    private Long projectId;
    private List<GraphNode> nodes;
    private List<GraphEdge> edges;

    public DependencyGraph() {}

    public DependencyGraph(Long projectId, List<GraphNode> nodes, List<GraphEdge> edges) {
        this.projectId = projectId;
        this.nodes = nodes;
        this.edges = edges;
    }

    public Long getProjectId() { return projectId; }
    public void setProjectId(Long projectId) { this.projectId = projectId; }

    public List<GraphNode> getNodes() { return nodes; }
    public void setNodes(List<GraphNode> nodes) { this.nodes = nodes; }

    public List<GraphEdge> getEdges() { return edges; }
    public void setEdges(List<GraphEdge> edges) { this.edges = edges; }
}
