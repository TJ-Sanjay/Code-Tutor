package com.codetutor.backend.explain;

import com.codetutor.backend.analysis.Knowledge;
import com.codetutor.backend.analysis.java.JavaIndex;
import com.codetutor.backend.analysis.model.AnalysisModel.*;
import com.github.javaparser.JavaParser;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.*;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.Range;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Produces AST-accurate "semantic tokens" for a Java file so the code viewer can colour classes, methods, fields,
 * parameters and local variables differently (like a real IDE) and resolve a click to the exact symbol.
 */
public class SemanticTokenService {

    public static final List<String> KINDS = List.of("class", "classDecl", "method", "methodDecl", "field", "fieldDecl",
            "param", "paramDecl", "local", "localDecl", "annotation", "constant", "enumMember", "interfaceDecl");

    public record Tokens(List<String> kinds, List<String> refs, List<int[]> tokens) {}

    private final Knowledge k;
    private final JavaIndex index = new JavaIndex();

    public SemanticTokenService(Knowledge k) {
        this.k = k;
        for (ClassInfo c : k.result.classes()) {
            index.register(new JavaIndex.Entry(c.id(), c.name(), c.pkg(), c.imports() == null ? List.of() : c.imports(),
                    c.nested() ? c.id().substring(0, Math.max(0, c.id().lastIndexOf('.'))) : null));
        }
    }

    public Tokens tokens(Path file, String relPath) throws IOException {
        List<String> refs = new ArrayList<>();
        Map<String, Integer> refIdx = new HashMap<>();
        List<int[]> out = new ArrayList<>();
        ParserConfiguration cfg = new ParserConfiguration().setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_21);
        CompilationUnit cu = new JavaParser(cfg).parse(file).getResult().orElse(null);
        if (cu == null) return new Tokens(KINDS, refs, out);

        // class context lookup
        List<ClassInfo> inFile = k.result.classes().stream().filter(c -> c.file().equals(relPath)).toList();
        Map<TypeDeclaration<?>, ClassInfo> clsOf = new IdentityHashMap<>();
        for (TypeDeclaration<?> td : cu.findAll(TypeDeclaration.class)) {
            String id = td.getFullyQualifiedName().orElse(null);
            if (id == null) continue;
            for (ClassInfo c : inFile) if (c.id().equals(id)) clsOf.put(td, c);
        }

        Set<Node> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        for (AnnotationExpr a : cu.findAll(AnnotationExpr.class)) {
            Range r = a.getName().getRange().orElse(null);
            if (r == null) continue;
            add(out, r, a.getName().asString().length(), "annotation", -1, refs, refIdx, null);
            for (Node n : a.getChildNodes()) if (n instanceof MemberValuePair p) seen.add(p.getName());
        }

        for (SimpleName sn : cu.findAll(SimpleName.class)) {
            if (seen.contains(sn)) continue;
            Range r = sn.getRange().orElse(null);
            if (r == null || r.begin.line != r.end.line) continue;
            Node parent = sn.getParentNode().orElse(null);
            if (parent == null) continue;
            String name = sn.getIdentifier();
            TypeDeclaration<?> owner = enclosingType(sn);
            ClassInfo ctx = owner == null ? null : clsOf.get(owner);
            JavaIndex.Entry entry = ctx == null ? null : index.get(ctx.id());

            String kind = null;
            String ref = null;
            if (parent instanceof ClassOrInterfaceDeclaration cid && cid.getName() == sn) {
                kind = cid.isInterface() ? "interfaceDecl" : "classDecl";
                ref = ctx != null ? ctx.id() : null;
            } else if ((parent instanceof EnumDeclaration || parent instanceof RecordDeclaration || parent instanceof AnnotationDeclaration)
                    && ((TypeDeclaration<?>) parent).getName() == sn) {
                kind = "classDecl";
                ref = clsOf.get(parent) != null ? clsOf.get(parent).id() : null;
            } else if (parent instanceof MethodDeclaration md && md.getName() == sn) {
                kind = "methodDecl";
                ClassInfo c = clsOf.get(enclosingType(md));
                if (c != null) ref = methodId(c, md);
            } else if (parent instanceof ConstructorDeclaration cd && cd.getName() == sn) {
                kind = "methodDecl";
                ClassInfo c = clsOf.get(enclosingType(cd));
                if (c != null) ref = methodId(c, cd);
            } else if (parent instanceof VariableDeclarator vd && vd.getName() == sn) {
                boolean field = vd.getParentNode().map(p -> p instanceof FieldDeclaration).orElse(false);
                kind = field ? "fieldDecl" : "localDecl";
            } else if (parent instanceof Parameter) {
                kind = "paramDecl";
            } else if (parent instanceof EnumConstantDeclaration) {
                kind = "enumMember";
            } else if (parent instanceof ClassOrInterfaceType cit && cit.getName() == sn) {
                kind = "class";
                if (entry != null) {
                    String id = index.resolve(entry, cit.getNameWithScope());
                    ref = id;
                }
            } else if (parent instanceof MethodCallExpr mc && mc.getName() == sn) {
                kind = "method";
                if (ctx != null) ref = callRef(ctx, mc, sn);
            } else if (parent instanceof MethodReferenceExpr) {
                kind = "method";
            } else if (parent instanceof FieldAccessExpr fa && fa.getName() == sn) {
                kind = Character.isUpperCase(name.charAt(0)) && name.equals(name.toUpperCase()) ? "constant" : "field";
            } else if (parent instanceof NameExpr) {
                if (ctx != null) {
                    String resolved = resolveName(sn, name, owner, ctx);
                    kind = resolved;
                    if ("class".equals(resolved) && entry != null) ref = index.resolve(entry, name);
                } else {
                    kind = Character.isUpperCase(name.charAt(0)) ? "class" : "local";
                }
            }
            if (kind == null) continue;
            add(out, r, name.length(), kind, -1, refs, refIdx, ref);
        }
        out.sort(Comparator.<int[]>comparingInt(t -> t[0]).thenComparingInt(t -> t[1]));
        return new Tokens(KINDS, refs, out);
    }

    private void add(List<int[]> out, Range r, int len, String kind, int unused, List<String> refs, Map<String, Integer> idx, String ref) {
        int refIndex = -1;
        if (ref != null) {
            refIndex = idx.computeIfAbsent(ref, x -> { refs.add(x); return refs.size() - 1; });
        }
        out.add(new int[]{r.begin.line, r.begin.column, len, KINDS.indexOf(kind), refIndex});
    }

    private static TypeDeclaration<?> enclosingType(Node n) {
        Node p = n.getParentNode().orElse(null);
        while (p != null) {
            if (p instanceof TypeDeclaration<?> td && !(p == n)) return td;
            p = p.getParentNode().orElse(null);
        }
        return null;
    }

    private String methodId(ClassInfo c, CallableDeclaration<?> d) {
        int line = d.getBegin().map(p -> p.line).orElse(-1);
        for (MethodInfo m : c.methods()) if (m.line() == line) return m.id();
        return null;
    }

    private String callRef(ClassInfo ctx, MethodCallExpr mc, SimpleName sn) {
        int line = sn.getBegin().map(p -> p.line).orElse(-1);
        MethodInfo enclosing = k.methodAtLine(ctx, line);
        if (enclosing != null) {
            for (Call c : enclosing.calls()) {
                if (c.method().equals(mc.getNameAsString()) && Math.abs(c.line() - line) <= 0) {
                    return c.targetMethodId() != null ? c.targetMethodId() : c.targetClass();
                }
            }
        }
        return null;
    }

    /** Decides whether a bare name is a parameter, local variable, field, constant or a class reference. */
    private String resolveName(SimpleName sn, String name, TypeDeclaration<?> owner, ClassInfo ctx) {
        Node callable = sn;
        while (callable != null && !(callable instanceof MethodDeclaration) && !(callable instanceof ConstructorDeclaration)
                && !(callable instanceof LambdaExpr && lambdaDeclares((LambdaExpr) callable, name))) {
            callable = callable.getParentNode().orElse(null);
        }
        if (callable instanceof LambdaExpr) return "param";
        if (callable != null) {
            if (callable instanceof CallableDeclaration<?> cd) {
                for (Parameter p : cd.getParameters()) if (p.getNameAsString().equals(name)) return "param";
                for (VariableDeclarator vd : callable.findAll(VariableDeclarator.class)) {
                    if (vd.getNameAsString().equals(name)) return "local";
                }
            }
        }
        // enclosing lambdas not directly inside a callable are rare; fields next
        ClassInfo c = ctx;
        while (c != null) {
            for (FieldInfo f : c.fields()) {
                if (f.name().equals(name)) return f.isStatic() && f.isFinal() && name.equals(name.toUpperCase()) ? "constant" : "field";
            }
            c = c.nested() ? k.classById.get(c.id().substring(0, c.id().lastIndexOf('.'))) : null;
        }
        if (Character.isUpperCase(name.charAt(0))) return name.equals(name.toUpperCase()) && name.length() > 1 ? "constant" : "class";
        return "local";
    }

    private static boolean lambdaDeclares(LambdaExpr l, String name) {
        return l.getParameters().stream().anyMatch(p -> p.getNameAsString().equals(name));
    }

    public static Path resolve(Path root, String rel) throws IOException {
        Path p = root.resolve(rel).normalize();
        if (!p.startsWith(root) || !Files.isRegularFile(p)) throw new IOException("bad path");
        return p;
    }
}
