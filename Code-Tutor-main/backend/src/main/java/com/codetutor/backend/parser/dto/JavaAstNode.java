package com.codetutor.backend.parser.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * Representation of a parsed Java AST node (Class, Interface, Method, Field, Annotation, Import).
 */
public class JavaAstNode {

    private String id;
    private String name;
    private String nodeType; // "CLASS", "INTERFACE", "ENUM", "RECORD", "METHOD", "FIELD", "CONSTRUCTOR", "ANNOTATION", "IMPORT"
    private String filePath;
    private int startLine;
    private int endLine;
    private List<String> modifiers;
    private List<String> annotations;
    private String returnType;
    private List<String> parameters;
    private List<String> calledMethods;
    private List<String> importedTypes;
    private List<JavaAstNode> children;

    public JavaAstNode() {
        this.modifiers = new ArrayList<>();
        this.annotations = new ArrayList<>();
        this.parameters = new ArrayList<>();
        this.calledMethods = new ArrayList<>();
        this.importedTypes = new ArrayList<>();
        this.children = new ArrayList<>();
    }

    public JavaAstNode(String id, String name, String nodeType, String filePath, int startLine, int endLine) {
        this();
        this.id = id;
        this.name = name;
        this.nodeType = nodeType;
        this.filePath = filePath;
        this.startLine = startLine;
        this.endLine = endLine;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getNodeType() { return nodeType; }
    public void setNodeType(String nodeType) { this.nodeType = nodeType; }

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

    public String getReturnType() { return returnType; }
    public void setReturnType(String returnType) { this.returnType = returnType; }

    public List<String> getParameters() { return parameters; }
    public void setParameters(List<String> parameters) { this.parameters = parameters; }

    public List<String> getCalledMethods() { return calledMethods; }
    public void setCalledMethods(List<String> calledMethods) { this.calledMethods = calledMethods; }

    public List<String> getImportedTypes() { return importedTypes; }
    public void setImportedTypes(List<String> importedTypes) { this.importedTypes = importedTypes; }

    public List<JavaAstNode> getChildren() { return children; }
    public void setChildren(List<JavaAstNode> children) { this.children = children; }

    public void addChild(JavaAstNode child) {
        if (this.children == null) this.children = new ArrayList<>();
        this.children.add(child);
    }
}
