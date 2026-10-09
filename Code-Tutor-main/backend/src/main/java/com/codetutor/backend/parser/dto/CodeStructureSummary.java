package com.codetutor.backend.parser.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * Summary DTO of all extracted code structures in a project workspace.
 */
public class CodeStructureSummary {

    private Long projectId;
    private int totalClasses;
    private int totalInterfaces;
    private int totalEnums;
    private int totalMethods;
    private int totalFields;
    private List<ClassStructure> classes;
    private List<MethodStructure> allMethods;

    public CodeStructureSummary() {
        this.classes = new ArrayList<>();
        this.allMethods = new ArrayList<>();
    }

    public CodeStructureSummary(
            Long projectId,
            int totalClasses,
            int totalInterfaces,
            int totalEnums,
            int totalMethods,
            int totalFields,
            List<ClassStructure> classes,
            List<MethodStructure> allMethods
    ) {
        this.projectId = projectId;
        this.totalClasses = totalClasses;
        this.totalInterfaces = totalInterfaces;
        this.totalEnums = totalEnums;
        this.totalMethods = totalMethods;
        this.totalFields = totalFields;
        this.classes = classes;
        this.allMethods = allMethods;
    }

    public Long getProjectId() { return projectId; }
    public void setProjectId(Long projectId) { this.projectId = projectId; }

    public int getTotalClasses() { return totalClasses; }
    public void setTotalClasses(int totalClasses) { this.totalClasses = totalClasses; }

    public int getTotalInterfaces() { return totalInterfaces; }
    public void setTotalInterfaces(int totalInterfaces) { this.totalInterfaces = totalInterfaces; }

    public int getTotalEnums() { return totalEnums; }
    public void setTotalEnums(int totalEnums) { this.totalEnums = totalEnums; }

    public int getTotalMethods() { return totalMethods; }
    public void setTotalMethods(int totalMethods) { this.totalMethods = totalMethods; }

    public int getTotalFields() { return totalFields; }
    public void setTotalFields(int totalFields) { this.totalFields = totalFields; }

    public List<ClassStructure> getClasses() { return classes; }
    public void setClasses(List<ClassStructure> classes) { this.classes = classes; }

    public List<MethodStructure> getAllMethods() { return allMethods; }
    public void setAllMethods(List<MethodStructure> allMethods) { this.allMethods = allMethods; }
}
