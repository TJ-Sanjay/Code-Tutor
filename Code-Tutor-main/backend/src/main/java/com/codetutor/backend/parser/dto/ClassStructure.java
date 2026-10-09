package com.codetutor.backend.parser.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * Structured representation of a Java class or interface.
 */
public class ClassStructure {

    private String name;
    private String type; // "CLASS", "INTERFACE", "ENUM"
    private String filePath;
    private int startLine;
    private int endLine;
    private List<String> modifiers;
    private List<String> annotations;
    private List<String> fields;
    private List<MethodStructure> methods;

    public ClassStructure() {
        this.modifiers = new ArrayList<>();
        this.annotations = new ArrayList<>();
        this.fields = new ArrayList<>();
        this.methods = new ArrayList<>();
    }

    public ClassStructure(String name, String type, String filePath, int startLine, int endLine) {
        this();
        this.name = name;
        this.type = type;
        this.filePath = filePath;
        this.startLine = startLine;
        this.endLine = endLine;
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getFilePath() { return filePath; }
    public void setFilePath(String filePath) { this.filePath = filePath; }

    public int getStartLine() { return startLine; }
    public void setStartLine(int startLine) { this.startLine = startLine; }

    public int getEndLine() { return endLine; }
    public void setEndLine(int endLine) { this.endLine = endLine; }

    public List<String> getModifiers() { return modifiers; }
    public void setModifiers(List<String> modifiers) { this.modifiers = modifiers; }

    public List<String> getAnnotations() { return annotations; }
    public void setAnnotations(List<String> annotations) { this.annotations = annotations; }

    public List<String> getFields() { return fields; }
    public void setFields(List<String> fields) { this.fields = fields; }

    public List<MethodStructure> getMethods() { return methods; }
    public void setMethods(List<MethodStructure> methods) { this.methods = methods; }
}
