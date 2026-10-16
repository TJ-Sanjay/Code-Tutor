package com.codetutor.backend.parser.service;

import com.codetutor.backend.parser.dto.ClassStructure;
import com.codetutor.backend.parser.dto.CodeStructureSummary;
import com.codetutor.backend.parser.dto.JavaAstNode;
import com.codetutor.backend.parser.dto.MethodStructure;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Module 12 — Code Structure Extractor Service.
 *
 * Flattens and structures parsed Java AST trees into queryable CodeStructureSummary models:
 *   1. Extracts Classes, Interfaces, Enums, and Fields
 *   2. Extracts Methods with parameter lists, return types, and line numbers
 *   3. Collects overall codebase architectural metrics (total classes, methods, fields)
 */
@Service
public class CodeStructureExtractorService {

    private static final Logger log = LoggerFactory.getLogger(CodeStructureExtractorService.class);

    private final JavaParserService javaParserService;

    public CodeStructureExtractorService(JavaParserService javaParserService) {
        this.javaParserService = javaParserService;
    }

    /**
     * Extracts structured code metadata for all Java source files in a project workspace.
     */
    public CodeStructureSummary extractCodeStructure(Long projectId) {
        List<JavaAstNode> fileAsts = javaParserService.parseWorkspaceJavaFiles(projectId);

        List<ClassStructure> classStructures = new ArrayList<>();
        List<MethodStructure> allMethods = new ArrayList<>();

        int classCount = 0;
        int interfaceCount = 0;
        int enumCount = 0;
        int fieldCount = 0;

        for (JavaAstNode fileAst : fileAsts) {
            if (fileAst.getChildren() == null) continue;

            for (JavaAstNode typeNode : fileAst.getChildren()) {
                String nodeType = typeNode.getNodeType();
                if ("CLASS".equals(nodeType) || "INTERFACE".equals(nodeType) || "ENUM".equals(nodeType)) {
                    if ("CLASS".equals(nodeType)) classCount++;
                    else if ("INTERFACE".equals(nodeType)) interfaceCount++;
                    else if ("ENUM".equals(nodeType)) enumCount++;

                    ClassStructure clsStruct = new ClassStructure(
                            typeNode.getName(),
                            typeNode.getNodeType(),
                            typeNode.getFilePath(),
                            typeNode.getStartLine(),
                            typeNode.getEndLine()
                    );
                    clsStruct.getModifiers().addAll(typeNode.getModifiers());
                    clsStruct.getAnnotations().addAll(typeNode.getAnnotations());

                    if (typeNode.getChildren() != null) {
                        for (JavaAstNode child : typeNode.getChildren()) {
                            if ("FIELD".equals(child.getNodeType())) {
                                fieldCount++;
                                String fieldDesc = (child.getReturnType() != null ? child.getReturnType() + " " : "") + child.getName();
                                clsStruct.getFields().add(fieldDesc);
                            } else if ("METHOD".equals(child.getNodeType())) {
                                MethodStructure mStruct = new MethodStructure(
                                        child.getName(),
                                        typeNode.getName(),
                                        child.getReturnType(),
                                        child.getParameters(),
                                        child.getModifiers(),
                                        child.getAnnotations(),
                                        child.getCalledMethods(),
                                        child.getFilePath(),
                                        child.getStartLine(),
                                        child.getEndLine()
                                );
                                clsStruct.getMethods().add(mStruct);
                                allMethods.add(mStruct);
                            }
                        }
                    }
                    classStructures.add(clsStruct);
                }
            }
        }

        log.info("Extracted code structure for project id={}: {} classes, {} interfaces, {} methods, {} fields",
                projectId, classCount, interfaceCount, allMethods.size(), fieldCount);

        return new CodeStructureSummary(
                projectId,
                classCount,
                interfaceCount,
                enumCount,
                allMethods.size(),
                fieldCount,
                classStructures,
                allMethods
        );
    }
}
