package com.codetutor.backend.parser.dto;

import java.util.List;

/**
 * Structured representation of a method for code intelligence.
 */
public class MethodStructure {

    private String name;
    private String className;
    private String returnType;
    private List<String> parameters;
    private List<String> modifiers;
    private List<String> annotations;
    private List<String> calledMethods;
    private String filePath;
    private int startLine;
    private int endLine;

    public MethodStructure() {}

    public MethodStructure(
            String name,
            String className,
            String returnType,
            List<String> parameters,
            List<String> modifiers,
            List<String> annotations,
            List<String> calledMethods,
            String filePath,
            int startLine,
            int endLine
    ) {
        this.name = name;
        this.className = className;
        this.returnType = returnType;
        this.parameters = parameters;
        this.modifiers = modifiers;
        this.annotations = annotations;
        this.calledMethods = calledMethods;
        this.filePath = filePath;
        this.startLine = startLine;
        this.endLine = endLine;
    }

    public List<String> getCalledMethods() { return calledMethods; }
    public void setCalledMethods(List<String> calledMethods) { this.calledMethods = calledMethods; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getClassName() { return className; }
    public void setClassName(String className) { this.className = className; }

    public String getReturnType() { return returnType; }
    public void setReturnType(String returnType) { this.returnType = returnType; }

    public List<String> getParameters() { return parameters; }
    public void setParameters(List<String> parameters) { this.parameters = parameters; }

    public List<String> getModifiers() { return modifiers; }
    public void setModifiers(List<String> modifiers) { this.modifiers = modifiers; }

    public List<String> getAnnotations() { return annotations; }
    public void setAnnotations(List<String> annotations) { this.annotations = annotations; }

    public String getFilePath() { return filePath; }
    public void setFilePath(String filePath) { this.filePath = filePath; }

    public int getStartLine() { return startLine; }
    public void setStartLine(int startLine) { this.startLine = startLine; }

    public int getEndLine() { return endLine; }
    public void setEndLine(int endLine) { this.endLine = endLine; }
}
