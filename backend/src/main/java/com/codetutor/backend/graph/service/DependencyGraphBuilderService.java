package com.codetutor.backend.graph.service;

import com.codetutor.backend.analyzer.dto.ClassRelationship;
import com.codetutor.backend.analyzer.dto.SpringComponentsSummary;
import com.codetutor.backend.analyzer.service.RelationshipResolverService;
import com.codetutor.backend.analyzer.service.SpringComponentDetectorService;
import com.codetutor.backend.graph.dto.DependencyGraph;
import com.codetutor.backend.graph.dto.GraphEdge;
import com.codetutor.backend.graph.dto.GraphNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Module 19/20 — Dependency Graph Builder.
 *
 * Converts resolved relationships and detected components into a generic nodes-and-edges
 * graph format that can be persisted (Module 20) or served to frontend visualization (React Flow).
 */
@Service
public class DependencyGraphBuilderService {

    private static final Logger log = LoggerFactory.getLogger(DependencyGraphBuilderService.class);

    private final SpringComponentDetectorService springComponentDetectorService;
    private final RelationshipResolverService relationshipResolverService;

    public DependencyGraphBuilderService(
            SpringComponentDetectorService springComponentDetectorService,
            RelationshipResolverService relationshipResolverService
    ) {
        this.springComponentDetectorService = springComponentDetectorService;
        this.relationshipResolverService = relationshipResolverService;
    }

    public DependencyGraph buildGraph(Long projectId) {
        log.info("Building Dependency Graph for project id={}", projectId);

        SpringComponentsSummary components = springComponentDetectorService.detectComponents(projectId);
        List<ClassRelationship> relationships = relationshipResolverService.resolveRelationships(projectId);

        List<GraphNode> nodes = new ArrayList<>();
        List<GraphEdge> edges = new ArrayList<>();
        Set<String> addedNodes = new HashSet<>();

        // Add nodes for all detected Spring components
        addComponentNodes(nodes, addedNodes, components.getControllers(), "CONTROLLER");
        addComponentNodes(nodes, addedNodes, components.getServices(), "SERVICE");
        addComponentNodes(nodes, addedNodes, components.getRepositories(), "REPOSITORY");
        addComponentNodes(nodes, addedNodes, components.getEntities(), "ENTITY");
        addComponentNodes(nodes, addedNodes, components.getConfigurations(), "CONFIGURATION");
        addComponentNodes(nodes, addedNodes, components.getComponents(), "COMPONENT");

        // Add edges and implicitly add any missing nodes
        for (ClassRelationship rel : relationships) {
            String source = rel.getSourceClass();
            String target = rel.getTargetClass();

            if (!addedNodes.contains(source)) {
                nodes.add(new GraphNode(source, source, "OTHER"));
                addedNodes.add(source);
            }
            if (!addedNodes.contains(target)) {
                nodes.add(new GraphNode(target, target, "OTHER"));
                addedNodes.add(target);
            }

            String edgeId = "edge_" + UUID.randomUUID().toString().substring(0, 8);
            edges.add(new GraphEdge(edgeId, source, target, rel.getRelationshipType()));
        }

        log.info("Generated graph with {} nodes and {} edges for project id={}", nodes.size(), edges.size(), projectId);

        return new DependencyGraph(projectId, nodes, edges);
    }

    private void addComponentNodes(List<GraphNode> nodes, Set<String> addedNodes, List<String> classes, String group) {
        if (classes == null) return;
        for (String cls : classes) {
            if (!addedNodes.contains(cls)) {
                nodes.add(new GraphNode(cls, cls, group));
                addedNodes.add(cls);
            }
        }
    }
}
