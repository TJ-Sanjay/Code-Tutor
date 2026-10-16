package com.codetutor.backend.analysis;

import com.codetutor.backend.analysis.model.AnalysisModel.*;
import com.codetutor.backend.analysis.util.TypeUtil;
import com.codetutor.backend.search.VectorIndex;

import java.util.*;

/**
 * In-memory, derived view of an {@link Result}: fast look-ups used by the flow generator, the explain popups
 * and the chat retrieval. Built once per loaded analysis.
 */
public class Knowledge {

    public final Result result;
    public final Map<String, ClassInfo> classById = new LinkedHashMap<>();
    public final Map<String, MethodInfo> methodById = new HashMap<>();
    public final Map<String, ClassInfo> classOfMethod = new HashMap<>();
    public final Map<String, List<ClassInfo>> classesByName = new HashMap<>();
    public final Map<String, EndpointInfo> endpointById = new LinkedHashMap<>();
    public final Map<String, List<EndpointInfo>> endpointsByHandler = new HashMap<>();
    public final Map<String, Table> tableByName = new LinkedHashMap<>();
    public final Map<String, List<String>> dependents = new HashMap<>();   // class id -> classes that depend on it
    public final Map<String, List<MethodInfo>> callers = new HashMap<>();  // method id -> methods calling it
    public final Map<String, List<String>> implementations = new HashMap<>(); // interface id -> implementing class ids
    public final Map<String, String> entityTable = new HashMap<>();        // entity class id -> table
    public final Map<String, FileInfo> fileByPath = new HashMap<>();
    public final Map<String, JsFile> jsByPath = new HashMap<>();
    private volatile VectorIndex vectors;

    public Knowledge(Result r) {
        this.result = r;
        for (ClassInfo c : r.classes()) {
            classById.put(c.id(), c);
            classesByName.computeIfAbsent(c.name().toLowerCase(Locale.ROOT), k -> new ArrayList<>()).add(c);
            for (MethodInfo m : c.methods()) {
                methodById.put(m.id(), m);
                classOfMethod.put(m.id(), c);
            }
        }
        for (EndpointInfo e : r.endpoints()) {
            endpointById.put(e.id(), e);
            endpointsByHandler.computeIfAbsent(e.handler(), k -> new ArrayList<>()).add(e);
        }
        if (r.schema() != null) {
            for (Table t : r.schema().tables()) {
                tableByName.put(t.name(), t);
                if (t.entity() != null) entityTable.put(t.entity(), t.name());
            }
        }
        for (ClassInfo c : r.classes()) {
            for (Dep d : c.dependsOn()) {
                dependents.computeIfAbsent(d.target(), k -> new ArrayList<>()).add(c.id());
            }
            for (MethodInfo m : c.methods()) {
                for (Call call : m.calls()) {
                    if (call.targetMethodId() != null) {
                        callers.computeIfAbsent(call.targetMethodId(), k -> new ArrayList<>()).add(m);
                    }
                }
            }
        }
        for (ClassInfo c : r.classes()) {
            for (Dep d : c.dependsOn()) {
                if ("IMPLEMENTS".equals(d.kind()) && !"INTERFACE".equals(c.kind())) {
                    implementations.computeIfAbsent(d.target(), k -> new ArrayList<>()).add(c.id());
                }
            }
        }
        for (FileInfo f : r.files()) fileByPath.put(f.path(), f);
        if (r.frontend() != null) for (JsFile j : r.frontend().files()) jsByPath.put(j.path(), j);
    }

    private volatile com.codetutor.backend.explain.Describer describer;
    private volatile com.codetutor.backend.flow.FlowService flows;

    public com.codetutor.backend.explain.Describer describer() {
        com.codetutor.backend.explain.Describer d = describer;
        if (d == null) {
            synchronized (this) {
                if (describer == null) describer = new com.codetutor.backend.explain.Describer(this);
                d = describer;
            }
        }
        return d;
    }

    public com.codetutor.backend.flow.FlowService flows() {
        com.codetutor.backend.flow.FlowService f = flows;
        if (f == null) {
            synchronized (this) {
                if (flows == null) flows = new com.codetutor.backend.flow.FlowService(this, describer());
                f = flows;
            }
        }
        return f;
    }

    public VectorIndex vectors() {
        VectorIndex v = vectors;
        if (v == null) {
            synchronized (this) {
                if (vectors == null) vectors = new VectorIndex(result.chunks());
                v = vectors;
            }
        }
        return v;
    }

    // ───────────────────────── look-ups ─────────────────────────

    public ClassInfo findClass(String nameOrId) {
        if (nameOrId == null) return null;
        ClassInfo c = classById.get(nameOrId);
        if (c != null) return c;
        List<ClassInfo> l = classesByName.get(TypeUtil.raw(nameOrId).toLowerCase(Locale.ROOT));
        if (l == null || l.isEmpty()) return null;
        return l.stream().filter(x -> !x.test()).findFirst().orElse(l.get(0));
    }

    public ClassInfo classOf(MethodInfo m) { return classOfMethod.get(m.id()); }

    public boolean isInterface(String classId) {
        ClassInfo c = classById.get(classId);
        return c != null && "INTERFACE".equals(c.kind());
    }

    /** Concrete methods that really run when the given method is invoked (resolves interface → implementation). */
    public List<MethodInfo> concreteTargets(MethodInfo m) {
        ClassInfo owner = classOf(m);
        if (owner == null) return List.of(m);
        if (!"INTERFACE".equals(owner.kind())) return List.of(m);
        List<MethodInfo> out = new ArrayList<>();
        for (String implId : implementations.getOrDefault(owner.id(), List.of())) {
            ClassInfo impl = classById.get(implId);
            if (impl == null) continue;
            for (MethodInfo im : impl.methods()) {
                if (im.name().equals(m.name()) && im.params().size() == m.params().size()) out.add(im);
            }
        }
        return out.isEmpty() ? List.of(m) : out;
    }

    public String tableOfRepository(ClassInfo repo) {
        if (repo == null) return null;
        String ent = repo.attrs().get("entityType");
        if (ent == null) return null;
        ClassInfo e = findClass(ent);
        return e == null ? null : entityTable.get(e.id());
    }

    public ClassInfo entityOfRepository(ClassInfo repo) {
        if (repo == null) return null;
        String ent = repo.attrs().get("entityType");
        return ent == null ? null : findClass(ent);
    }

    public List<ClassInfo> mainClasses() {
        return result.classes().stream().filter(c -> !c.test()).toList();
    }

    public List<EndpointInfo> endpointsOf(ClassInfo c) {
        List<EndpointInfo> out = new ArrayList<>();
        for (EndpointInfo e : result.endpoints()) if (e.controller().equals(c.id())) out.add(e);
        return out;
    }

    public List<ClassInfo> dependentsOf(String classId) {
        List<ClassInfo> out = new ArrayList<>();
        for (String id : dependents.getOrDefault(classId, List.of())) {
            ClassInfo c = classById.get(id);
            if (c != null && !c.test()) out.add(c);
        }
        return out;
    }

    public MethodInfo methodAtLine(ClassInfo c, int line) {
        MethodInfo best = null;
        for (MethodInfo m : c.methods()) {
            if (line >= m.line() && line <= m.endLine()) {
                if (best == null || (m.endLine() - m.line()) < (best.endLine() - best.line())) best = m;
            }
        }
        return best;
    }

    public ClassInfo classForFile(String path) {
        ClassInfo best = null;
        for (ClassInfo c : result.classes()) {
            if (c.file().equals(path) && (best == null || c.outerRank() < best.outerRank())) best = c;
        }
        return best;
    }
}
