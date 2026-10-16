package com.codetutor.backend.analysis.java;

import com.codetutor.backend.analysis.util.TypeUtil;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Name-resolution index over the project's own classes. Resolves a simple (or Outer.Inner) type name,
 * written inside a given class, to a project class id using the file's package, imports and nesting.
 * Returns {@code null} for anything external (JDK, Spring, other libraries).
 */
public class JavaIndex {

    public record Entry(String id, String name, String pkg, List<String> imports, String outer) {}

    private final Map<String, Entry> byId = new HashMap<>();
    private final Map<String, List<Entry>> bySimple = new HashMap<>();

    public void register(Entry e) {
        byId.put(e.id(), e);
        bySimple.computeIfAbsent(e.name(), k -> new ArrayList<>()).add(e);
    }

    public Entry get(String id) { return id == null ? null : byId.get(id); }

    public boolean contains(String id) { return byId.containsKey(id); }

    public List<Entry> bySimpleName(String simple) { return bySimple.getOrDefault(simple, List.of()); }

    public String resolve(Entry ctx, String typeName) {
        if (typeName == null || ctx == null) return null;
        String t = TypeUtil.raw(typeName);
        if (t == null || t.isBlank()) return null;

        // Outer.Inner form
        if (t.contains(".")) {
            String head = t.substring(0, t.indexOf('.'));
            String rest = t.substring(t.indexOf('.') + 1);
            String headId = resolve(ctx, head);
            if (headId != null) {
                String candidate = headId + "." + rest;
                if (byId.containsKey(candidate)) return candidate;
            }
            if (byId.containsKey(t)) return t;
            t = t.substring(t.lastIndexOf('.') + 1);
        }

        // nested classes of this class and of its outer classes
        Entry cur = ctx;
        int guard = 0;
        while (cur != null && guard++ < 8) {
            String nested = cur.id() + "." + t;
            if (byId.containsKey(nested)) return nested;
            if (cur.name().equals(t)) return cur.id();
            cur = cur.outer() == null ? null : byId.get(cur.outer());
        }

        // explicit imports
        for (String imp : ctx.imports()) {
            if (imp.startsWith("static ")) continue;
            if (imp.endsWith("." + t)) {
                if (byId.containsKey(imp)) return imp;
                return null; // explicitly imported but not a project class → external
            }
        }
        // same package
        String samePkg = ctx.pkg().isEmpty() ? t : ctx.pkg() + "." + t;
        if (byId.containsKey(samePkg)) return samePkg;
        // wildcard imports
        for (String imp : ctx.imports()) {
            if (imp.endsWith(".*")) {
                String candidate = imp.substring(0, imp.length() - 1) + t;
                if (byId.containsKey(candidate)) return candidate;
            }
        }
        // unique simple name anywhere in the project (handles partially-resolvable projects)
        List<Entry> list = bySimple.get(t);
        if (list != null && list.size() == 1 && !isLikelyExternal(ctx, t)) return list.get(0).id();
        return null;
    }

    private boolean isLikelyExternal(Entry ctx, String simple) {
        for (String imp : ctx.imports()) {
            if (!imp.startsWith("static ") && imp.endsWith("." + simple)) return true; // imported from elsewhere
        }
        return false;
    }
}
