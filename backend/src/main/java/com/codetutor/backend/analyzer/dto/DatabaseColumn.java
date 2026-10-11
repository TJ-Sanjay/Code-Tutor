package com.codetutor.backend.analyzer.dto;

import java.util.List;

public class DatabaseColumn {
    private String name;
    private String type;
    private boolean isPrimaryKey;

    public DatabaseColumn() {}

    public DatabaseColumn(String name, String type, boolean isPrimaryKey) {
        this.name = name;
        this.type = type;
        this.isPrimaryKey = isPrimaryKey;
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public boolean isPrimaryKey() { return isPrimaryKey; }
    public void setPrimaryKey(boolean primaryKey) { isPrimaryKey = primaryKey; }
}
