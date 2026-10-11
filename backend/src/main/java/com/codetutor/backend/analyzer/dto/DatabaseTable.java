package com.codetutor.backend.analyzer.dto;

import java.util.List;

public class DatabaseTable {
    private String entityClass;
    private String tableName;
    private List<DatabaseColumn> columns;
    private List<DatabaseRelationship> relationships;

    public DatabaseTable() {}

    public DatabaseTable(String entityClass, String tableName, List<DatabaseColumn> columns, List<DatabaseRelationship> relationships) {
        this.entityClass = entityClass;
        this.tableName = tableName;
        this.columns = columns;
        this.relationships = relationships;
    }

    public String getEntityClass() { return entityClass; }
    public void setEntityClass(String entityClass) { this.entityClass = entityClass; }

    public String getTableName() { return tableName; }
    public void setTableName(String tableName) { this.tableName = tableName; }

    public List<DatabaseColumn> getColumns() { return columns; }
    public void setColumns(List<DatabaseColumn> columns) { this.columns = columns; }

    public List<DatabaseRelationship> getRelationships() { return relationships; }
    public void setRelationships(List<DatabaseRelationship> relationships) { this.relationships = relationships; }
}
