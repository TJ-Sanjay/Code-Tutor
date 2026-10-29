package com.codetutor.backend.explain;

import com.codetutor.backend.analysis.Knowledge;
import com.codetutor.backend.analysis.model.AnalysisModel.*;
import com.codetutor.backend.analysis.util.TypeUtil;
import com.codetutor.backend.explain.ExplainModel.Explanation;
import com.codetutor.backend.explain.ExplainModel.Link;

import java.util.List;
import java.util.Locale;

/** Resolves "what is this?" requests (by id, or by a click position in a file) into an {@link Explanation}. */
public final class ExplainResolver {

    private ExplainResolver() {}

    public static Explanation byId(Knowledge k, String kind, String id) {
        Describer d = k.describer();
        if (kind == null || id == null) return null;
        switch (kind) {
            case "class": {
                ClassInfo c = k.classById.get(id);
                return c == null ? null : d.classExplanation(c);
            }
            case "method": case "constructor": {
                MethodInfo m = k.methodById.get(id);
                if (m == null) return null;
                ClassInfo c = k.classOf(m);
                return c == null ? null : d.methodExplanation(c, m);
            }
            case "endpoint": {
                EndpointInfo e = k.endpointById.get(id);
                return e == null ? null : d.endpointExplanation(e);
            }
            case "table": {
                Table t = k.tableByName.get(id);
                return t == null ? null : d.tableExplanation(t);
            }
            case "file": return d.fileExplanation(id);
            default: return null;
        }
    }

    /** A reference that may point to a class id, a method id or a file. */
    public static Explanation byRef(Knowledge k, String ref) {
        if (ref == null) return null;
        if (ref.contains("#")) return byId(k, "method", ref);
        if (k.classById.containsKey(ref)) return byId(k, "class", ref);
        return null;
    }

    public static Explanation at(Knowledge k, String file, int line, String word) {
        Describer d = k.describer();
        ClassInfo cls = innermostClass(k, file, line);
        if (cls == null) return d.fileExplanation(file);
        MethodInfo enclosing = k.methodAtLine(cls, line);
        String w = word == null ? "" : word.trim();

        if (!w.isEmpty()) {
            // 1. a call on this line
            if (enclosing != null) {
                for (Call c : enclosing.calls()) {
                    if (c.method().equals(w) && Math.abs(c.line() - line) <= 1) {
                        if (c.targetMethodId() != null) {
                            Explanation e = byId(k, "method", c.targetMethodId());
                            if (e != null) return e;
                        }
                        ClassInfo tc = k.classById.get(c.targetClass());
                        if (tc != null) return d.classExplanation(tc);
                    }
                }
            }
            // 2. a class name
            List<ClassInfo> named = k.classesByName.get(w.toLowerCase(Locale.ROOT));
            if (named != null && !named.isEmpty() && Character.isUpperCase(w.charAt(0))) {
                ClassInfo pick = named.stream().filter(x -> x.pkg().equals(cls.pkg())).findFirst().orElse(named.get(0));
                return d.classExplanation(pick);
            }
            // 3. a method of this class
            for (MethodInfo m : cls.methods()) {
                if (m.name().equals(w) && !m.constructor()) return d.methodExplanation(cls, m);
            }
            // 4. a field of this class
            for (FieldInfo f : cls.fields()) {
                if (f.name().equals(w)) return fieldExplanation(k, cls, f);
            }
            // 5. a local variable / parameter of the enclosing method
            if (enclosing != null) {
                for (Param p : enclosing.params()) {
                    if (p.name().equals(w)) {
                        return new Explanation("parameter", p.name(), p.name(), p.type(), "Parameter “" + p.name() + "” of type " + p.type()
                                + " passed into " + cls.name() + "." + enclosing.name() + "().", List.of("Declared in " + enclosing.signature()),
                                List.of(), cls.file(), enclosing.line(), enclosing.endLine(), List.of("parameter"), cls.layer());
                    }
                }
            }
        }
        if (enclosing != null) return d.methodExplanation(cls, enclosing);
        return d.classExplanation(cls);
    }

    private static Explanation fieldExplanation(Knowledge k, ClassInfo c, FieldInfo f) {
        StringBuilder s = new StringBuilder("Field “" + f.name() + "” of type " + f.type() + " in " + c.name() + ".");
        if (f.injected()) {
            if (c.attrs().get("framework") != null) {
                s.append(" It is set in the initialiser, so ").append(c.name()).append(" keeps a ").append(TypeUtil.raw(f.type()))
                        .append(" to work with.");
            } else {
                s.append(" It is injected by Spring (dependency injection), so ").append(c.name())
                        .append(" can use ").append(TypeUtil.raw(f.type())).append(" without creating it.");
            }
        }
        if (!f.annotations().isEmpty() && c.attrs().get("framework") != null) {
            String t = f.annotations().get(0).name();
            if (t.equals("ForeignKey") || t.equals("OneToOneField") || t.equals("ManyToManyField")) {
                s.append(" It is a ").append(t.equals("ForeignKey") ? "many-to-one relationship (a foreign key column)"
                        : t.equals("OneToOneField") ? "one-to-one relationship (a unique foreign key column)"
                        : "many-to-many relationship (stored in a join table)").append('.');
            } else if (t.endsWith("Field")) {
                s.append(" A ").append(t).append(" column of the database table.");
            }
        }
        List<Ann> an = f.annotations();
        java.util.List<String> facts = new java.util.ArrayList<>();
        for (Ann a : an) facts.add(a.render());
        if (f.isStatic()) facts.add("static");
        if (f.isFinal()) facts.add("final");
        List<Link> links = new java.util.ArrayList<>();
        if (f.resolvedClass() != null) {
            ClassInfo t = k.classById.get(f.resolvedClass());
            if (t != null) links.add(new Link(t.name(), "class", t.id(), t.file(), t.line(), "Type"));
        }
        return new Explanation("field", c.id() + "." + f.name(), f.name(), f.type(), s.toString(), facts, links, c.file(), f.line(), f.line(),
                List.of("field"), c.layer());
    }

    private static ClassInfo innermostClass(Knowledge k, String file, int line) {
        ClassInfo best = null;
        for (ClassInfo c : k.result.classes()) {
            if (!c.file().equals(file)) continue;
            if (line >= c.line() && line <= c.endLine()) {
                if (best == null || (c.endLine() - c.line()) < (best.endLine() - best.line())) best = c;
            }
        }
        if (best == null) best = k.classForFile(file);
        return best;
    }
}
