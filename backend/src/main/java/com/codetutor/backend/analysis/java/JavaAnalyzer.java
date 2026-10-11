package com.codetutor.backend.analysis.java;

import com.codetutor.backend.analysis.model.AnalysisModel.*;
import com.codetutor.backend.analysis.util.TypeUtil;
import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.Modifier;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.*;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.stmt.CatchClause;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.Type;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Parses every Java source file of a project (JavaParser AST) and extracts classes, fields, methods,
 * resolved method calls, class level dependencies, layers, modules and REST endpoints.
 */
public class JavaAnalyzer {

    private static final Logger log = LoggerFactory.getLogger(JavaAnalyzer.class);

    public record Output(List<ClassInfo> classes, List<EndpointInfo> endpoints, String basePackage,
                         List<String> errors) {}

    static final Set<String> REPO_BASES = Set.of("Repository", "CrudRepository", "JpaRepository",
            "PagingAndSortingRepository", "ListCrudRepository", "ListPagingAndSortingRepository", "MongoRepository",
            "ReactiveCrudRepository", "R2dbcRepository", "ElasticsearchRepository", "CassandraRepository",
            "Neo4jRepository", "ReactiveMongoRepository");
    static final Set<String> FILTER_BASES = Set.of("OncePerRequestFilter", "GenericFilterBean", "Filter",
            "AbstractAuthenticationProcessingFilter", "BasicAuthenticationFilter", "WebFilter");
    static final Set<String> BEAN_LAYERS = Set.of("CONTROLLER", "SERVICE", "REPOSITORY", "COMPONENT", "CONFIG",
            "SECURITY", "ADVICE");
    static final Set<String> MAPPING_ANNS = Set.of("RequestMapping", "GetMapping", "PostMapping", "PutMapping",
            "DeleteMapping", "PatchMapping");
    static final Set<String> LAYERISH_PACKAGES = Set.of("controller", "controllers", "service", "services",
            "repository", "repositories", "repo", "dao", "model", "models", "entity", "entities", "domain", "dto",
            "dtos", "config", "configuration", "security", "util", "utils", "exception", "exceptions", "web", "rest",
            "api", "mapper", "mappers", "payload", "request", "response", "handler", "filter", "jwt");

    // ───────────────────────── mutable builders ─────────────────────────

    static class FileCtx {
        String path;
        String pkg = "";
        boolean test;
        List<String> imports = new ArrayList<>();
        CompilationUnit cu;
    }

    static class MB {
        String id, name, signature, returnType, doc, endpointId;
        List<Param> params = new ArrayList<>();
        int line, endLine, loc;
        List<Ann> anns = new ArrayList<>();
        List<String> mods = new ArrayList<>();
        List<Call> calls = new ArrayList<>();
        boolean ctor;
        CallableDeclaration<?> decl;
    }

    static class CB {
        String id, name, pkg, file, kind, layer = "OTHER", module = "core", ext, doc, outer;
        int line, endLine;
        List<Ann> anns = new ArrayList<>();
        List<String> impls = new ArrayList<>();
        List<FieldInfo> fields = new ArrayList<>();
        List<MB> methods = new ArrayList<>();
        boolean test;
        Map<String, String> attrs = new LinkedHashMap<>();
        Map<String, Set<String>> deps = new LinkedHashMap<>();
        TypeDeclaration<?> td;
        FileCtx fc;
        JavaIndex.Entry entry;
        Set<String> annNames = new HashSet<>();
        boolean isInterface;
    }

    // ───────────────────────── state ─────────────────────────

    private final JavaIndex index = new JavaIndex();
    private final Map<String, CB> classes = new LinkedHashMap<>();
    private final List<EndpointInfo> endpoints = new ArrayList<>();
    private final List<String> errors = new ArrayList<>();
    private String contextPath = "";

    // ───────────────────────── entry point ─────────────────────────

    public Output analyze(Path root, List<String> javaFiles, long maxFileBytes, String ctxPath) {
        this.contextPath = ctxPath == null ? "" : ctxPath;
        ParserConfiguration cfg = new ParserConfiguration()
                .setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_21)
                .setAttributeComments(true);
        JavaParser parser = new JavaParser(cfg);

        // 1. parse + collect declarations
        for (String rel : javaFiles) {
            Path p = root.resolve(rel);
            try {
                if (Files.size(p) > maxFileBytes) continue;
                ParseResult<CompilationUnit> pr = parser.parse(p);
                if (pr.getResult().isEmpty()) {
                    errors.add(rel + ": unparseable");
                    continue;
                }
                collect(rel, pr.getResult().get());
            } catch (IOException | RuntimeException e) {
                errors.add(rel + ": " + e.getMessage());
                log.debug("Parse failure in {}", rel, e);
            }
        }

        // 2. layers, fields
        for (CB c : classes.values()) detectLayer(c);
        for (CB c : classes.values()) resolveFields(c);

        // 3. method calls
        for (CB c : classes.values()) {
            try {
                resolveCalls(c);
            } catch (RuntimeException e) {
                errors.add(c.file + ": call resolution failed (" + e.getMessage() + ")");
            }
        }

        // 4. dependencies
        for (CB c : classes.values()) computeDependencies(c);

        // 5. modules
        String basePackage = assignModules();

        // 6. endpoints
        for (CB c : classes.values()) {
            if ("CONTROLLER".equals(c.layer) || "ADVICE".equals(c.layer)) extractEndpoints(c);
        }

        // 7. materialise
        List<ClassInfo> out = new ArrayList<>();
        for (CB c : classes.values()) out.add(toInfo(c));
        log.info("Java analysis complete: {} classes, {} endpoints, {} parse errors", out.size(), endpoints.size(),
                errors.size());
        return new Output(out, endpoints, basePackage, errors);
    }

    // ───────────────────────── declaration collection ─────────────────────────

    private void collect(String rel, CompilationUnit cu) {
        FileCtx fc = new FileCtx();
        fc.path = rel;
        fc.cu = cu;
        fc.pkg = cu.getPackageDeclaration().map(pd -> pd.getNameAsString()).orElse("");
        String lower = rel.toLowerCase(Locale.ROOT);
        fc.test = lower.contains("src/test/") || lower.startsWith("test/") || lower.endsWith("test.java")
                || lower.endsWith("tests.java");
        for (ImportDeclaration imp : cu.getImports()) {
            fc.imports.add((imp.isStatic() ? "static " : "") + imp.getNameAsString() + (imp.isAsterisk() ? ".*" : ""));
        }

        for (TypeDeclaration<?> td : cu.findAll(TypeDeclaration.class)) {
            String fqcn = td.getFullyQualifiedName().orElse(null);
            if (fqcn == null) continue;
            CB c = new CB();
            c.id = fqcn;
            c.name = td.getNameAsString();
            c.pkg = fc.pkg;
            c.file = rel;
            c.fc = fc;
            c.td = td;
            c.test = fc.test;
            c.line = line(td);
            c.endLine = endLine(td);
            c.anns = annotations(td.getAnnotations());
            for (Ann a : c.anns) c.annNames.add(a.name());
            c.doc = javadoc(td);
            Optional<Node> parent = td.getParentNode();
            if (parent.isPresent() && parent.get() instanceof TypeDeclaration<?> pt) {
                c.outer = pt.getFullyQualifiedName().orElse(null);
                c.attrs.put("nested", "true");
            }
            fillTypeInfo(c, td);
            c.entry = new JavaIndex.Entry(c.id, c.name, c.pkg, fc.imports, c.outer);
            index.register(c.entry);
            classes.put(c.id, c);
        }
    }

    private void fillTypeInfo(CB c, TypeDeclaration<?> td) {
        if (td instanceof ClassOrInterfaceDeclaration cid) {
            c.isInterface = cid.isInterface();
            c.kind = cid.isInterface() ? "INTERFACE" : "CLASS";
            if (cid.getExtendedTypes().isNonEmpty()) {
                if (cid.isInterface()) {
                    for (ClassOrInterfaceType t : cid.getExtendedTypes()) c.impls.add(t.asString());
                    c.ext = null;
                    c.attrs.put("extendedInterfaces", cid.getExtendedTypes().stream()
                            .map(ClassOrInterfaceType::asString).collect(Collectors.joining(", ")));
                } else {
                    c.ext = cid.getExtendedTypes().get(0).asString();
                }
            }
            if (!cid.isInterface()) {
                for (ClassOrInterfaceType t : cid.getImplementedTypes()) c.impls.add(t.asString());
            }
        } else if (td instanceof EnumDeclaration ed) {
            c.kind = "ENUM";
            for (ClassOrInterfaceType t : ed.getImplementedTypes()) c.impls.add(t.asString());
            for (EnumConstantDeclaration k : ed.getEntries()) {
                c.fields.add(new FieldInfo(k.getNameAsString(), c.name, line(k), List.of(), false, null, true, true));
            }
        } else if (td instanceof RecordDeclaration rd) {
            c.kind = "RECORD";
            for (ClassOrInterfaceType t : rd.getImplementedTypes()) c.impls.add(t.asString());
            for (Parameter p : rd.getParameters()) {
                c.fields.add(new FieldInfo(p.getNameAsString(), p.getType().asString(), line(p),
                        annotations(p.getAnnotations()), false, null, false, true));
            }
        } else {
            c.kind = "ANNOTATION";
        }

        for (FieldDeclaration fd : td.getFields()) {
            List<Ann> fa = annotations(fd.getAnnotations());
            boolean inj = fa.stream().anyMatch(a -> Set.of("Autowired", "Inject", "Resource").contains(a.name()));
            for (VariableDeclarator vd : fd.getVariables()) {
                c.fields.add(new FieldInfo(vd.getNameAsString(), vd.getType().asString(), line(vd), fa, inj, null,
                        fd.isStatic(), fd.isFinal()));
            }
        }
        for (ConstructorDeclaration cd : td.getConstructors()) c.methods.add(methodOf(c, cd, true));
        for (MethodDeclaration md : td.getMethods()) c.methods.add(methodOf(c, md, false));
    }

    private MB methodOf(CB c, CallableDeclaration<?> d, boolean ctor) {
        MB m = new MB();
        m.decl = d;
        m.ctor = ctor;
        m.name = ctor ? c.name : d.getNameAsString();
        m.line = line(d);
        m.endLine = endLine(d);
        m.loc = Math.max(1, m.endLine - m.line + 1);
        m.anns = annotations(d.getAnnotations());
        for (Modifier mod : d.getModifiers()) m.mods.add(mod.getKeyword().asString());
        for (Parameter p : d.getParameters()) m.params.add(new Param(p.getNameAsString(), p.getType().asString()));
        m.returnType = ctor ? null : ((MethodDeclaration) d).getType().asString();
        m.doc = javadoc(d);
        String ptypes = m.params.stream().map(p -> TypeUtil.raw(p.type())).collect(Collectors.joining(","));
        m.id = c.id + "#" + (ctor ? "<init>" : m.name) + "(" + ptypes + ")";
        String ps = m.params.stream().map(p -> p.type() + " " + p.name()).collect(Collectors.joining(", "));
        m.signature = (ctor ? "" : m.returnType + " ") + m.name + "(" + ps + ")";
        return m;
    }

    // ───────────────────────── layers ─────────────────────────

    private void detectLayer(CB c) {
        Set<String> an = c.annNames;
        Set<String> supers = new HashSet<>();
        if (c.ext != null) supers.add(TypeUtil.raw(c.ext));
        for (String i : c.impls) supers.add(TypeUtil.raw(i));
        String n = c.name;
        String layer;

        if (c.test) layer = "TEST";
        else if (an.contains("SpringBootApplication")) layer = "APPLICATION";
        else if (an.contains("ControllerAdvice") || an.contains("RestControllerAdvice")) layer = "ADVICE";
        else if (an.contains("RestController") || an.contains("Controller")) layer = "CONTROLLER";
        else if (an.contains("Entity") || an.contains("MappedSuperclass") || an.contains("Embeddable")
                || an.contains("Document") || an.contains("Table")) layer = "ENTITY";
        else if (an.contains("Service")) layer = "SERVICE";
        else if (an.contains("Repository") || (c.isInterface && supers.stream().anyMatch(REPO_BASES::contains))
                || supers.stream().anyMatch(s -> s.endsWith("Repository") && !s.equals(n) && c.isInterface)) {
            layer = "REPOSITORY";
        } else if (supers.stream().anyMatch(FILTER_BASES::contains)
                || an.contains("EnableWebSecurity") || an.contains("EnableMethodSecurity")
                || (an.contains("Configuration") && n.toLowerCase(Locale.ROOT).contains("security"))
                || n.toLowerCase(Locale.ROOT).contains("jwt") || n.endsWith("UserDetailsService")
                || n.endsWith("UserDetailsServiceImpl") || supers.contains("UserDetailsService")
                || supers.contains("AuthenticationEntryPoint") || supers.contains("AuthenticationProvider")) {
            layer = "SECURITY";
        } else if (an.contains("Configuration") || an.contains("ConfigurationProperties")
                || an.contains("EnableAutoConfiguration") || an.contains("EnableAsync")
                || an.contains("EnableScheduling") || an.contains("EnableCaching")) {
            layer = "CONFIG";
        } else if (an.contains("Component") || an.contains("Bean") || supers.contains("Validator")
                || supers.contains("Formatter") || supers.contains("Converter")
                || supers.contains("HandlerInterceptor")) layer = "COMPONENT";
        else if (isExceptionType(c, supers)) layer = "EXCEPTION";
        else if (n.matches(".*(Dto|DTO|Request|Response|Payload|Form|Command|Summary|Result)$")) layer = "DTO";
        else if (n.matches(".*(Util|Utils|Helper|Utility|Constants)$")) layer = "UTIL";
        else if ("ENUM".equals(c.kind) || "RECORD".equals(c.kind)) layer = "MODEL";
        else layer = classifyByPackage(c);
        c.layer = layer;

        // Spring Data repositories: remember the entity & id types
        if ("REPOSITORY".equals(layer) && c.isInterface) {
            for (String s : c.impls) {
                List<String> args = TypeUtil.typeArgs(s);
                if (!args.isEmpty() && REPO_BASES.contains(TypeUtil.raw(s))) {
                    c.attrs.put("entityType", TypeUtil.raw(args.get(0)));
                    if (args.size() > 1) c.attrs.put("idType", TypeUtil.raw(args.get(1)));
                    c.attrs.put("springData", "true");
                    break;
                }
            }
        }
        if (supers.stream().anyMatch(FILTER_BASES::contains)) c.attrs.put("filter", "true");
    }

    private boolean isExceptionType(CB c, Set<String> supers) {
        if (c.name.endsWith("Exception") || c.name.endsWith("Error")) return true;
        return supers.stream().anyMatch(s -> s.endsWith("Exception") || s.equals("Throwable"));
    }

    private String classifyByPackage(CB c) {
        String p = c.pkg.toLowerCase(Locale.ROOT);
        if (p.endsWith(".model") || p.endsWith(".domain") || p.endsWith(".entity") || p.contains(".model."))
            return "MODEL";
        if (p.endsWith(".dto") || p.contains(".dto.") || p.endsWith(".payload")) return "DTO";
        if (p.endsWith(".config") || p.endsWith(".configuration")) return "CONFIG";
        if (p.endsWith(".util") || p.endsWith(".utils")) return "UTIL";
        return "OTHER";
    }

    // ───────────────────────── fields / injection ─────────────────────────

    private void resolveFields(CB c) {
        List<FieldInfo> out = new ArrayList<>();
        Set<String> ctorParamTypes = new HashSet<>();
        for (MB m : c.methods) {
            if (m.ctor) for (Param p : m.params) ctorParamTypes.add(TypeUtil.raw(p.type()));
        }
        boolean lombokCtor = c.annNames.contains("RequiredArgsConstructor") || c.annNames.contains("AllArgsConstructor");
        boolean bean = BEAN_LAYERS.contains(c.layer);
        for (FieldInfo f : c.fields) {
            String resolved = index.resolve(c.entry, f.type());
            boolean injected = f.injected();
            if (!injected && bean && !f.isStatic() && resolved != null) {
                if (ctorParamTypes.contains(TypeUtil.raw(f.type()))) injected = true;
                else if (lombokCtor && f.isFinal()) injected = true;
            }
            out.add(new FieldInfo(f.name(), f.type(), f.line(), f.annotations(), injected, resolved, f.isStatic(),
                    f.isFinal()));
        }
        c.fields = out;
    }

    // ───────────────────────── call resolution ─────────────────────────

    private void resolveCalls(CB c) {
        Map<String, String> fieldTypes = new HashMap<>();
        collectFieldTypes(c, fieldTypes, new HashSet<>());

        for (MB m : c.methods) {
            Optional<? extends Node> body = m.decl instanceof MethodDeclaration md ? md.getBody()
                    : Optional.of(((ConstructorDeclaration) m.decl).getBody());
            if (body.isEmpty()) continue;
            Node b = body.get();

            Map<String, String> scope = new HashMap<>(fieldTypes);
            for (Param p : m.params) scope.put(p.name(), p.type());
            for (VariableDeclarator vd : b.findAll(VariableDeclarator.class)) {
                String t = vd.getType().isVarType() ? null : vd.getType().asString();
                if (t == null && vd.getInitializer().isPresent()) t = typeOf(vd.getInitializer().get(), c, scope, 0);
                if (t != null) scope.put(vd.getNameAsString(), t);
            }
            for (CatchClause cc : b.findAll(CatchClause.class)) {
                scope.put(cc.getParameter().getNameAsString(), cc.getParameter().getType().asString());
            }

            Set<String> seen = new HashSet<>();
            for (MethodCallExpr mc : b.findAll(MethodCallExpr.class)) {
                String name = mc.getNameAsString();
                int argc = mc.getArguments().size();
                String targetClassId = null;
                if (mc.getScope().isEmpty()) {
                    targetClassId = c.id;
                } else {
                    String st = typeOf(mc.getScope().get(), c, scope, 0);
                    if (st != null) targetClassId = index.resolve(c.entry, st);
                }
                if (targetClassId == null) continue;
                String key = targetClassId + "#" + name + "/" + argc + "@" + line(mc);
                if (!seen.add(key)) continue;

                MB target = findMethod(targetClassId, name, argc, new HashSet<>());
                CB tc = classes.get(targetClassId);
                if (target != null) {
                    String owner = target.id.substring(0, target.id.indexOf('#'));
                    CB ownerClass = classes.get(owner);
                    // trivial getters/setters on data classes are noise in call graphs
                    if (ownerClass != null && Set.of("ENTITY", "DTO", "MODEL").contains(ownerClass.layer)
                            && name.matches("^(get|set|is)[A-Z].*")) continue;
                    if (owner.equals(c.id) && mc.getScope().isEmpty() && target == m) continue; // plain recursion
                    CB oc = classes.get(owner);
                    m.calls.add(new Call(owner, oc != null ? oc.name : TypeUtil.simpleName(owner), name, line(mc),
                            target.id, false));
                } else if (tc != null && (("REPOSITORY".equals(tc.layer) && "true".equals(tc.attrs.get("springData")))
                        || (mc.getScope().isPresent() && !targetClassId.equals(c.id) && BEAN_LAYERS.contains(tc.layer)))) {
                    m.calls.add(new Call(targetClassId, tc.name, name, line(mc), null, true));
                }
            }
        }
    }

    private void collectFieldTypes(CB c, Map<String, String> out, Set<String> visited) {
        if (c == null || !visited.add(c.id)) return;
        // inherited & outer first so that the nearest declaration wins
        if (c.ext != null) {
            String sup = index.resolve(c.entry, c.ext);
            if (sup != null) collectFieldTypes(classes.get(sup), out, visited);
        }
        if (c.outer != null) collectFieldTypes(classes.get(c.outer), out, visited);
        for (FieldInfo f : c.fields) out.put(f.name(), f.type());
    }

    private MB findMethod(String classId, String name, int argc, Set<String> visited) {
        CB c = classes.get(classId);
        if (c == null || !visited.add(classId)) return null;
        MB loose = null;
        for (MB m : c.methods) {
            if (!m.ctor && m.name.equals(name)) {
                if (m.params.size() == argc) return m;
                if (loose == null) loose = m;
            }
        }
        if (loose != null) return loose;
        List<String> supers = new ArrayList<>(c.impls);
        if (c.ext != null) supers.add(c.ext);
        for (String s : supers) {
            String id = index.resolve(c.entry, s);
            if (id != null) {
                MB r = findMethod(id, name, argc, visited);
                if (r != null) return r;
            }
        }
        return null;
    }

    /** Best-effort static type of an expression; returns a type string (generics kept) or null. */
    private String typeOf(Expression e, CB ctx, Map<String, String> scope, int depth) {
        if (e == null || depth > 6) return null;
        if (e instanceof NameExpr ne) {
            String t = scope.get(ne.getNameAsString());
            if (t != null) return t;
            String n = ne.getNameAsString();
            if (!n.isEmpty() && Character.isUpperCase(n.charAt(0)) && index.resolve(ctx.entry, n) != null) return n;
            return null;
        }
        if (e instanceof ThisExpr) return ctx.name;
        if (e instanceof SuperExpr) return ctx.ext;
        if (e instanceof EnclosedExpr ee) return typeOf(ee.getInner(), ctx, scope, depth + 1);
        if (e instanceof CastExpr ce) return ce.getType().asString();
        if (e instanceof ObjectCreationExpr oc) return oc.getType().asString();
        if (e instanceof StringLiteralExpr) return "String";
        if (e instanceof ConditionalExpr ce) return typeOf(ce.getThenExpr(), ctx, scope, depth + 1);
        if (e instanceof FieldAccessExpr fa) {
            Expression s = fa.getScope();
            if (s instanceof ThisExpr) return scope.get(fa.getNameAsString());
            String st = typeOf(s, ctx, scope, depth + 1);
            String id = st == null ? null : index.resolve(ctx.entry, st);
            CB tc = id == null ? null : classes.get(id);
            if (tc != null) {
                for (FieldInfo f : tc.fields) if (f.name().equals(fa.getNameAsString())) return f.type();
            }
            return null;
        }
        if (e instanceof MethodCallExpr mc) {
            String name = mc.getNameAsString();
            String st = mc.getScope().isEmpty() ? ctx.name : typeOf(mc.getScope().get(), ctx, scope, depth + 1);
            if (st == null) return null;
            String rawT = TypeUtil.raw(st);
            // well known wrappers
            if (Set.of("Optional", "List", "Set", "Collection", "Iterable").contains(rawT)) {
                List<String> a = TypeUtil.typeArgs(st);
                if (!a.isEmpty() && Set.of("get", "orElse", "orElseThrow", "orElseGet", "getFirst", "iterator", "stream")
                        .contains(name)) {
                    return a.get(0);
                }
                return null;
            }
            String id = index.resolve(ctx.entry, st);
            if (id == null) return null;
            MB target = findMethod(id, name, mc.getArguments().size(), new HashSet<>());
            if (target != null && target.returnType != null && !"void".equals(target.returnType)) {
                return target.returnType;
            }
            CB tc = classes.get(id);
            if (tc != null && "true".equals(tc.attrs.get("springData"))) {
                String ent = tc.attrs.get("entityType");
                if (ent != null) {
                    if (Set.of("findById", "findOne", "findByIdOrNull").contains(name)) return "Optional<" + ent + ">";
                    if (Set.of("save", "saveAndFlush", "getReferenceById", "getById", "getOne").contains(name)) return ent;
                    if (name.startsWith("findAll") || (name.startsWith("findBy") && !name.endsWith("Id"))) {
                        return "List<" + ent + ">";
                    }
                }
            }
            return null;
        }
        return null;
    }

    // ───────────────────────── dependencies ─────────────────────────

    private void computeDependencies(CB c) {
        Map<String, Set<String>> deps = c.deps;
        // inheritance
        if (c.ext != null) {
            String id = index.resolve(c.entry, c.ext);
            if (id != null && !id.equals(c.id)) add(deps, id, "EXTENDS");
        }
        for (String i : c.impls) {
            String id = index.resolve(c.entry, i);
            if (id != null && !id.equals(c.id)) add(deps, id, c.isInterface ? "EXTENDS" : "IMPLEMENTS");
            for (String arg : TypeUtil.allNames(i)) {
                String aid = index.resolve(c.entry, arg);
                if (aid != null && !aid.equals(c.id) && !aid.equals(id)) add(deps, aid, "USES");
            }
        }
        // fields
        for (FieldInfo f : c.fields) {
            for (String n : TypeUtil.allNames(f.type())) {
                String id = index.resolve(c.entry, n);
                if (id == null || id.equals(c.id)) continue;
                add(deps, id, f.injected() && TypeUtil.raw(f.type()).equals(n) ? "INJECTS" : "USES");
            }
        }
        // constructor / @Bean parameters in bean classes → injection
        for (MB m : c.methods) {
            boolean beanMethod = m.anns.stream().anyMatch(a -> a.name().equals("Bean"));
            for (Param p : m.params) {
                for (String n : TypeUtil.allNames(p.type())) {
                    String id = index.resolve(c.entry, n);
                    if (id == null || id.equals(c.id)) continue;
                    boolean inject = (m.ctor && BEAN_LAYERS.contains(c.layer) || beanMethod)
                            && TypeUtil.raw(p.type()).equals(n) && isBeanType(id);
                    add(deps, id, inject ? "INJECTS" : "USES");
                }
            }
            if (m.returnType != null) {
                for (String n : TypeUtil.allNames(m.returnType)) {
                    String id = index.resolve(c.entry, n);
                    if (id != null && !id.equals(c.id)) add(deps, id, "USES");
                }
            }
            for (Call call : m.calls) {
                if (call.targetClass() != null && !call.targetClass().equals(c.id)) add(deps, call.targetClass(), "CALLS");
            }
        }
        // type references inside method bodies (new X(), X.staticCall(), casts, locals)
        if (c.td != null) {
            for (ClassOrInterfaceType t : c.td.findAll(ClassOrInterfaceType.class)) {
                if (owner(t) != c.td) continue;
                String id = index.resolve(c.entry, t.getNameWithScope());
                if (id != null && !id.equals(c.id)) add(deps, id, "USES");
            }
            for (NameExpr ne : c.td.findAll(NameExpr.class)) {
                String n = ne.getNameAsString();
                if (n.isEmpty() || !Character.isUpperCase(n.charAt(0)) || owner(ne) != c.td) continue;
                String id = index.resolve(c.entry, n);
                if (id != null && !id.equals(c.id)) add(deps, id, "USES");
            }
        }
    }

    private boolean isBeanType(String id) {
        CB t = classes.get(id);
        return t != null && BEAN_LAYERS.contains(t.layer);
    }

    private static void add(Map<String, Set<String>> deps, String id, String kind) {
        deps.computeIfAbsent(id, k -> new LinkedHashSet<>()).add(kind);
    }

    private static Node owner(Node n) {
        Node p = n.getParentNode().orElse(null);
        while (p != null && !(p instanceof TypeDeclaration<?>)) p = p.getParentNode().orElse(null);
        return p;
    }

    // ───────────────────────── modules ─────────────────────────

    private String assignModules() {
        List<CB> main = classes.values().stream().filter(c -> !c.test && c.outer == null).toList();
        List<String> pkgs = main.stream().map(c -> c.pkg).filter(p -> !p.isEmpty()).distinct().toList();
        String base = commonPrefix(pkgs);

        // is the package structure "by layer" (controller/, service/, ...) rather than "by feature"?
        int layered = 0;
        int considered = 0;
        for (CB c : main) {
            String seg = firstSegment(c.pkg, base);
            if (seg == null) continue;
            considered++;
            if (LAYERISH_PACKAGES.contains(seg)) layered++;
        }
        boolean byLayer = considered > 0 && layered >= Math.ceil(considered * 0.7);

        for (CB c : classes.values()) {
            CB root = c;
            while (root.outer != null && classes.get(root.outer) != null) root = classes.get(root.outer);
            if (root != c) { c.module = null; continue; }
            if (c.test) { c.module = "tests"; continue; }
            String seg = firstSegment(c.pkg, base);
            if (byLayer) {
                c.module = stem(c);
            } else if (seg == null) {
                c.module = "core";
            } else {
                c.module = seg;
            }
        }
        for (CB c : classes.values()) {
            if (c.module == null) {
                CB root = c;
                while (root.outer != null && classes.get(root.outer) != null) root = classes.get(root.outer);
                c.module = root.module;
            }
        }
        return base;
    }

    private String stem(CB c) {
        switch (c.layer) {
            case "APPLICATION": return "core";
            case "CONFIG": case "SECURITY": case "ADVICE": case "UTIL": return c.layer.toLowerCase(Locale.ROOT);
            default: break;
        }
        String n = c.name.replaceAll("(ServiceImpl|Service|Controller|RestController|Repository|Repo|Dao|DAO|Impl|"
                + "Dto|DTO|Request|Response|Entity|Mapper|Exception|Handler|Form)$", "");
        if (n.isBlank()) n = c.name;
        return TypeUtil.decapitalize(n);
    }

    private static String firstSegment(String pkg, String base) {
        if (pkg == null || pkg.isEmpty()) return null;
        String rest = pkg;
        if (!base.isEmpty()) {
            if (pkg.equals(base)) return null;
            if (pkg.startsWith(base + ".")) rest = pkg.substring(base.length() + 1);
        }
        int dot = rest.indexOf('.');
        return dot < 0 ? rest : rest.substring(0, dot);
    }

    private static String commonPrefix(List<String> pkgs) {
        if (pkgs.isEmpty()) return "";
        String[] first = pkgs.get(0).split("\\.");
        int len = first.length;
        for (String p : pkgs) {
            String[] parts = p.split("\\.");
            int i = 0;
            while (i < len && i < parts.length && parts[i].equals(first[i])) i++;
            len = i;
        }
        return String.join(".", Arrays.copyOf(first, len));
    }

    // ───────────────────────── endpoints ─────────────────────────

    private void extractEndpoints(CB c) {
        if (!(c.td instanceof ClassOrInterfaceDeclaration)) return;
        List<String> prefixes = List.of("");
        for (AnnotationExpr a : c.td.getAnnotations()) {
            if (a.getNameAsString().equals("RequestMapping")) {
                List<String> v = values(a, "value", "path");
                if (!v.isEmpty()) prefixes = v;
            }
        }
        boolean restClass = c.annNames.contains("RestController") || c.annNames.contains("ResponseBody");
        for (MB m : c.methods) {
            if (m.ctor || !(m.decl instanceof MethodDeclaration md)) continue;
            for (AnnotationExpr a : md.getAnnotations()) {
                String an = a.getNameAsString();
                if (!MAPPING_ANNS.contains(an)) continue;
                List<String> paths = values(a, "value", "path");
                if (paths.isEmpty()) paths = List.of("");
                List<String> methods = httpMethods(a);
                boolean respBody = restClass || m.anns.stream().anyMatch(x -> x.name().equals("ResponseBody"));
                String rt = TypeUtil.raw(m.returnType);
                boolean view = !respBody && ("String".equals(rt) || "ModelAndView".equals(rt) || "void".equals(rt)
                        || "View".equals(rt));
                List<EndpointParam> ps = endpointParams(md);
                for (String prefix : prefixes) {
                    for (String path : paths) {
                        for (String hm : methods) {
                            String p = joinPath(prefix, path);
                            String full = joinPath(contextPath, p);
                            String id = hm + " " + p + " @" + c.id + "#" + m.name;
                            endpoints.add(new EndpointInfo(id, hm, p, full, c.id, m.id, c.file, m.line, ps,
                                    m.returnType, null, null, null, view));
                            m.endpointId = id;
                        }
                    }
                }
            }
        }
    }

    private List<EndpointParam> endpointParams(MethodDeclaration md) {
        List<EndpointParam> out = new ArrayList<>();
        for (Parameter p : md.getParameters()) {
            for (AnnotationExpr a : p.getAnnotations()) {
                String an = a.getNameAsString();
                String src = switch (an) {
                    case "PathVariable" -> "path";
                    case "RequestParam" -> "query";
                    case "RequestBody" -> "body";
                    case "RequestHeader" -> "header";
                    case "RequestPart" -> "part";
                    case "ModelAttribute" -> "form";
                    case "CookieValue" -> "cookie";
                    default -> null;
                };
                if (src == null) continue;
                List<String> named = values(a, "value", "name");
                String name = named.isEmpty() ? p.getNameAsString() : named.get(0);
                boolean required = true;
                for (Ann ann : annotations(NodeList.nodeList(a))) {
                    if ("false".equals(ann.arg("required")) || ann.arg("defaultValue") != null) required = false;
                }
                out.add(new EndpointParam(name, p.getType().asString(), src, required));
            }
        }
        return out;
    }

    private List<String> httpMethods(AnnotationExpr a) {
        switch (a.getNameAsString()) {
            case "GetMapping": return List.of("GET");
            case "PostMapping": return List.of("POST");
            case "PutMapping": return List.of("PUT");
            case "DeleteMapping": return List.of("DELETE");
            case "PatchMapping": return List.of("PATCH");
            default: break;
        }
        List<String> out = new ArrayList<>();
        if (a instanceof NormalAnnotationExpr n) {
            for (MemberValuePair p : n.getPairs()) {
                if (p.getNameAsString().equals("method")) {
                    for (Expression e : flatten(p.getValue())) {
                        String s = e.toString();
                        out.add(s.substring(s.lastIndexOf('.') + 1).toUpperCase(Locale.ROOT));
                    }
                }
            }
        }
        return out.isEmpty() ? List.of("ANY") : out;
    }

    private static String joinPath(String a, String b) {
        String s = ((a == null ? "" : a) + "/" + (b == null ? "" : b)).replaceAll("/+", "/");
        if (s.length() > 1 && s.endsWith("/")) s = s.substring(0, s.length() - 1);
        if (!s.startsWith("/")) s = "/" + s;
        return s;
    }

    // ───────────────────────── AST helpers ─────────────────────────

    private static List<Expression> flatten(Expression e) {
        if (e instanceof ArrayInitializerExpr ai) return new ArrayList<>(ai.getValues());
        return List.of(e);
    }

    private static List<String> values(AnnotationExpr a, String... keys) {
        List<String> out = new ArrayList<>();
        Expression val = null;
        if (a instanceof SingleMemberAnnotationExpr s) val = s.getMemberValue();
        else if (a instanceof NormalAnnotationExpr n) {
            for (MemberValuePair p : n.getPairs()) {
                for (String k : keys) if (p.getNameAsString().equals(k)) val = p.getValue();
            }
        }
        if (val == null) return out;
        for (Expression e : flatten(val)) out.add(render(e));
        return out;
    }

    static String render(Expression e) {
        if (e instanceof StringLiteralExpr s) return s.getValue();
        if (e instanceof BinaryExpr b && b.getOperator() == BinaryExpr.Operator.PLUS) {
            return render(b.getLeft()) + render(b.getRight());
        }
        if (e instanceof EnclosedExpr en) return render(en.getInner());
        if (e instanceof ArrayInitializerExpr ai) {
            return "{" + ai.getValues().stream().map(JavaAnalyzer::render).collect(Collectors.joining(", ")) + "}";
        }
        return e.toString();
    }

    static List<Ann> annotations(NodeList<AnnotationExpr> list) {
        List<Ann> out = new ArrayList<>();
        for (AnnotationExpr a : list) {
            Map<String, String> args = new LinkedHashMap<>();
            if (a instanceof SingleMemberAnnotationExpr s) args.put("value", quoteIfString(s.getMemberValue()));
            else if (a instanceof NormalAnnotationExpr n) {
                for (MemberValuePair p : n.getPairs()) args.put(p.getNameAsString(), quoteIfString(p.getValue()));
            }
            out.add(new Ann(a.getNameAsString(), args));
        }
        return out;
    }

    private static String quoteIfString(Expression e) {
        if (e instanceof StringLiteralExpr s) return "\"" + s.getValue() + "\"";
        return render(e);
    }

    private static String javadoc(Node n) {
        if (n instanceof com.github.javaparser.ast.nodeTypes.NodeWithJavadoc<?> j) {
            return j.getJavadoc().map(d -> d.getDescription().toText().trim()).filter(s -> !s.isBlank()).orElse(null);
        }
        return null;
    }

    private static int line(Node n) { return n.getBegin().map(p -> p.line).orElse(0); }

    private static int endLine(Node n) { return n.getEnd().map(p -> p.line).orElse(line(n)); }

    // ───────────────────────── materialise ─────────────────────────

    private ClassInfo toInfo(CB c) {
        List<MethodInfo> ms = new ArrayList<>();
        for (MB m : c.methods) {
            ms.add(new MethodInfo(m.id, m.name, m.signature, m.returnType, m.params, m.line, m.endLine, m.anns, m.mods,
                    m.doc, m.calls, m.ctor, m.endpointId, m.loc));
        }
        List<Dep> deps = new ArrayList<>();
        for (var e : c.deps.entrySet()) deps.add(new Dep(e.getKey(), primary(e.getValue())));
        return new ClassInfo(c.id, c.name, c.pkg, c.file, c.line, c.endLine, c.kind, c.layer, c.module, c.anns, c.ext,
                c.impls, c.fields, ms, c.doc, deps, c.test, c.attrs, c.fc.imports, null);
    }

    private static String primary(Set<String> kinds) {
        for (String k : List.of("EXTENDS", "IMPLEMENTS", "INJECTS", "CALLS", "USES")) if (kinds.contains(k)) return k;
        return "USES";
    }
}
