package com.codetutor.backend.analyzer.dto;

import java.util.List;

public class ClassRelationship {
    private String sourceClass;
    private String targetClass;
    private String relationshipType; // e.g. "INJECTS", "CALLS", "IMPORTS"
    
    public ClassRelationship() {}
    
    public ClassRelationship(String sourceClass, String targetClass, String relationshipType) {
        this.sourceClass = sourceClass;
        this.targetClass = targetClass;
        this.relationshipType = relationshipType;
    }

    public String getSourceClass() { return sourceClass; }
    public void setSourceClass(String sourceClass) { this.sourceClass = sourceClass; }
    
    public String getTargetClass() { return targetClass; }
    public void setTargetClass(String targetClass) { this.targetClass = targetClass; }
    
    public String getRelationshipType() { return relationshipType; }
    public void setRelationshipType(String relationshipType) { this.relationshipType = relationshipType; }
}
