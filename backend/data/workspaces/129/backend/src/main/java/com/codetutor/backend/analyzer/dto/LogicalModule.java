package com.codetutor.backend.analyzer.dto;

import java.util.List;

public class LogicalModule {
    private String name;
    private List<String> classes;

    public LogicalModule() {}

    public LogicalModule(String name, List<String> classes) {
        this.name = name;
        this.classes = classes;
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public List<String> getClasses() { return classes; }
    public void setClasses(List<String> classes) { this.classes = classes; }
}
