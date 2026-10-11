package com.codetutor.backend.parser.service;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Modifier;
import com.github.javaparser.ast.body.*;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.codetutor.backend.parser.dto.JavaAstNode;
import com.codetutor.backend.project.service.WorkspaceManagerService;
import com.codetutor.backend.scanner.service.FileScannerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Module 11 — Java Source Code Parser Service.
 *
 * Uses JavaParser AST Engine to parse Java source files into Abstract Syntax Trees (ASTs):
 *   1. Extracts imports, package declarations, and type declarations (Classes, Interfaces, Enums, Records)
 *   2. Extracts annotations (@Controller, @Service, @Repository, @Entity, @GetMapping, etc.)
 *   3. Extracts fields, constructors, and method signatures (modifiers, return type, parameters)
 *   4. Extracts method call expressions inside bodies to map method invocation chains
 *   5. Preserves exact source code line numbers (startLine, endLine)
 */
@Service
public class JavaParserService {

    private static final Logger log = LoggerFactory.getLogger(JavaParserService.class);

    private final WorkspaceManagerService workspaceManagerService;
    private final FileScannerService fileScannerService;
    private final JavaParser javaParser;

    public JavaParserService(
            WorkspaceManagerService workspaceManagerService,
            FileScannerService fileScannerService
    ) {
        this.workspaceManagerService = workspaceManagerService;
        this.fileScannerService = fileScannerService;
        this.javaParser = new JavaParser();
    }

    /**
     * Parses a single Java source file into a JavaAstNode tree.
     */
    public JavaAstNode parseJavaFile(Path filePath, String relPath) {
        if (!Files.exists(filePath)) {
            log.warn("Java file does not exist: {}", filePath);
            return null;
        }

        try {
            ParseResult<CompilationUnit> parseResult = javaParser.parse(filePath);
            if (!parseResult.isSuccessful() || parseResult.getResult().isEmpty()) {
                log.warn("Could not parse Java file: {}", relPath);
                return null;
            }

            CompilationUnit cu = parseResult.getResult().get();
            String fileName = filePath.getFileName().toString();

            JavaAstNode fileNode = new JavaAstNode(
                    "file_" + relPath.hashCode(),
                    fileName,
                    "FILE",
                    relPath,
                    1,
                    cu.getRange().map(r -> r.end.line).orElse(1)
            );

            // Extract imports
            cu.getImports().forEach(imp -> {
                fileNode.getImportedTypes().add(imp.getNameAsString());
            });

            // Extract Type Declarations (Classes, Interfaces, Enums, Records)
            cu.findAll(ClassOrInterfaceDeclaration.class).forEach(cls -> {
                JavaAstNode classNode = parseClassOrInterface(cls, relPath);
                fileNode.addChild(classNode);
            });

            cu.findAll(EnumDeclaration.class).forEach(enumDecl -> {
                JavaAstNode enumNode = parseEnum(enumDecl, relPath);
                fileNode.addChild(enumNode);
            });

            return fileNode;

        } catch (IOException e) {
            log.error("Failed to read Java file {}: {}", relPath, e.getMessage());
            return null;
        }
    }

    /**
     * Parses all Java source files in a project workspace into AST trees.
     */
    public List<JavaAstNode> parseWorkspaceJavaFiles(Long projectId) {
        Path workspaceDir = workspaceManagerService.getWorkspaceDir(projectId);
        List<String> flatFiles = fileScannerService.getFlatFileList(projectId);

        List<JavaAstNode> astRoots = new ArrayList<>();
        log.info("Starting Java AST parsing for project id={}", projectId);

        for (String relPath : flatFiles) {
            if (relPath.toLowerCase().endsWith(".java")) {
                Path filePath = workspaceDir.resolve(relPath).normalize();
                JavaAstNode root = parseJavaFile(filePath, relPath);
                if (root != null) {
                    astRoots.add(root);
                }
            }
        }

        log.info("Successfully parsed {} Java AST files for project id={}", astRoots.size(), projectId);
        return astRoots;
    }

    private JavaAstNode parseClassOrInterface(ClassOrInterfaceDeclaration cls, String relPath) {
        String name = cls.getNameAsString();
        String nodeType = cls.isInterface() ? "INTERFACE" : "CLASS";
        int start = cls.getRange().map(r -> r.begin.line).orElse(1);
        int end = cls.getRange().map(r -> r.end.line).orElse(1);

        JavaAstNode node = new JavaAstNode("type_" + name, name, nodeType, relPath, start, end);

        // Modifiers
        for (Modifier mod : cls.getModifiers()) {
            node.getModifiers().add(mod.getKeyword().asString());
        }

        // Annotations
        for (AnnotationExpr ann : cls.getAnnotations()) {
            node.getAnnotations().add(ann.toString());
        }

        // Fields
        for (FieldDeclaration field : cls.getFields()) {
            for (VariableDeclarator var : field.getVariables()) {
                JavaAstNode fieldNode = new JavaAstNode(
                        "field_" + var.getNameAsString(),
                        var.getNameAsString(),
                        "FIELD",
                        relPath,
                        field.getRange().map(r -> r.begin.line).orElse(start),
                        field.getRange().map(r -> r.end.line).orElse(end)
                );
                fieldNode.setReturnType(var.getTypeAsString());
                for (AnnotationExpr ann : field.getAnnotations()) {
                    fieldNode.getAnnotations().add(ann.toString());
                }
                node.addChild(fieldNode);
            }
        }

        // Constructors & Methods
        for (MethodDeclaration m : cls.getMethods()) {
            JavaAstNode methodNode = parseMethod(m, relPath);
            node.addChild(methodNode);
        }

        return node;
    }

    private JavaAstNode parseEnum(EnumDeclaration enumDecl, String relPath) {
        String name = enumDecl.getNameAsString();
        int start = enumDecl.getRange().map(r -> r.begin.line).orElse(1);
        int end = enumDecl.getRange().map(r -> r.end.line).orElse(1);

        JavaAstNode node = new JavaAstNode("enum_" + name, name, "ENUM", relPath, start, end);
        for (AnnotationExpr ann : enumDecl.getAnnotations()) {
            node.getAnnotations().add("@" + ann.getNameAsString());
        }
        return node;
    }

    private JavaAstNode parseMethod(MethodDeclaration m, String relPath) {
        String name = m.getNameAsString();
        int start = m.getRange().map(r -> r.begin.line).orElse(1);
        int end = m.getRange().map(r -> r.end.line).orElse(1);

        JavaAstNode node = new JavaAstNode("method_" + name, name, "METHOD", relPath, start, end);
        node.setReturnType(m.getTypeAsString());

        for (Modifier mod : m.getModifiers()) {
            node.getModifiers().add(mod.getKeyword().asString());
        }

        for (AnnotationExpr ann : m.getAnnotations()) {
            node.getAnnotations().add(ann.toString());
        }

        for (Parameter param : m.getParameters()) {
            node.getParameters().add(param.getTypeAsString() + " " + param.getNameAsString());
        }

        // Method Invocations inside method body
        m.findAll(MethodCallExpr.class).forEach(call -> {
            node.getCalledMethods().add(call.getNameAsString());
        });

        return node;
    }
}
