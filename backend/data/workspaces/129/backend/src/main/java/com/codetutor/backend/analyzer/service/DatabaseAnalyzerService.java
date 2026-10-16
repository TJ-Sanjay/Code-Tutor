package com.codetutor.backend.analyzer.service;

import com.codetutor.backend.analyzer.dto.DatabaseColumn;
import com.codetutor.backend.analyzer.dto.DatabaseRelationship;
import com.codetutor.backend.analyzer.dto.DatabaseSchemaSummary;
import com.codetutor.backend.analyzer.dto.DatabaseTable;
import com.codetutor.backend.parser.dto.JavaAstNode;
import com.codetutor.backend.parser.service.JavaParserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Module 22 — Database Analyzer.
 *
 * Detects database schema mappings defined via JPA/Hibernate annotations.
 * Extracts tables, columns, primary keys, and relationships (OneToMany, ManyToOne, etc).
 */
@Service
public class DatabaseAnalyzerService {

    private static final Logger log = LoggerFactory.getLogger(DatabaseAnalyzerService.class);

    private final JavaParserService javaParserService;

    public DatabaseAnalyzerService(JavaParserService javaParserService) {
        this.javaParserService = javaParserService;
    }

    public DatabaseSchemaSummary analyzeDatabase(Long projectId) {
        log.info("Starting Database Analysis for project id={}", projectId);

        List<JavaAstNode> fileAsts = javaParserService.parseWorkspaceJavaFiles(projectId);
        List<DatabaseTable> tables = new ArrayList<>();

        for (JavaAstNode fileAst : fileAsts) {
            if (fileAst.getChildren() != null) {
                for (JavaAstNode typeNode : fileAst.getChildren()) {
                    if (isEntity(typeNode.getAnnotations())) {
                        String tableName = extractTableName(typeNode.getAnnotations(), typeNode.getName());
                        List<DatabaseColumn> columns = new ArrayList<>();
                        List<DatabaseRelationship> relationships = new ArrayList<>();

                        if (typeNode.getChildren() != null) {
                            for (JavaAstNode member : typeNode.getChildren()) {
                                if ("FIELD".equals(member.getNodeType())) {
                                    analyzeField(member, columns, relationships);
                                }
                            }
                        }
                        tables.add(new DatabaseTable(typeNode.getName(), tableName, columns, relationships));
                    }
                }
            }
        }

        log.info("Detected {} database tables for project id={}", tables.size(), projectId);
        return new DatabaseSchemaSummary(projectId, tables);
    }

    private boolean isEntity(List<String> annotations) {
        if (annotations == null) return false;
        for (String ann : annotations) {
            if (ann.contains("@Entity") || ann.contains("@Table") || ann.contains("@Document")) {
                return true;
            }
        }
        return false;
    }

    private String extractTableName(List<String> annotations, String defaultName) {
        if (annotations == null) return defaultName;
        for (String ann : annotations) {
            if (ann.contains("@Table")) {
                int nameIdx = ann.indexOf("name");
                if (nameIdx != -1) {
                    int startQuote = ann.indexOf('"', nameIdx);
                    if (startQuote != -1) {
                        int endQuote = ann.indexOf('"', startQuote + 1);
                        if (endQuote != -1) {
                            return ann.substring(startQuote + 1, endQuote);
                        }
                    }
                }
            }
        }
        return defaultName.toLowerCase();
    }

    private void analyzeField(JavaAstNode fieldNode, List<DatabaseColumn> columns, List<DatabaseRelationship> relationships) {
        List<String> annotations = fieldNode.getAnnotations();
        boolean isPrimaryKey = false;
        String columnName = fieldNode.getName();
        String relationshipType = null;

        if (annotations != null) {
            for (String ann : annotations) {
                if (ann.contains("@Id")) {
                    isPrimaryKey = true;
                }
                if (ann.contains("@Column")) {
                    int nameIdx = ann.indexOf("name");
                    if (nameIdx != -1) {
                        int startQuote = ann.indexOf('"', nameIdx);
                        if (startQuote != -1) {
                            int endQuote = ann.indexOf('"', startQuote + 1);
                            if (endQuote != -1) {
                                columnName = ann.substring(startQuote + 1, endQuote);
                            }
                        }
                    }
                }
                if (ann.contains("@OneToMany")) relationshipType = "OneToMany";
                if (ann.contains("@ManyToOne")) relationshipType = "ManyToOne";
                if (ann.contains("@OneToOne")) relationshipType = "OneToOne";
                if (ann.contains("@ManyToMany")) relationshipType = "ManyToMany";
            }
        }

        if (relationshipType != null) {
            String targetEntity = fieldNode.getReturnType();
            if (targetEntity != null && targetEntity.contains("<")) {
                targetEntity = targetEntity.substring(targetEntity.indexOf('<') + 1, targetEntity.indexOf('>'));
            }
            relationships.add(new DatabaseRelationship(relationshipType, targetEntity));
        } else {
            // It's a standard column
            columns.add(new DatabaseColumn(columnName, fieldNode.getReturnType(), isPrimaryKey));
        }
    }
}
