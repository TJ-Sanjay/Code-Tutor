package com.codetutor.backend.analyzer.dto;

public class DatabaseRelationship {
    private String type; // OneToMany, ManyToOne, OneToOne, ManyToMany
    private String targetEntity;

    public DatabaseRelationship() {}

    public DatabaseRelationship(String type, String targetEntity) {
        this.type = type;
        this.targetEntity = targetEntity;
    }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getTargetEntity() { return targetEntity; }
    public void setTargetEntity(String targetEntity) { this.targetEntity = targetEntity; }
}
