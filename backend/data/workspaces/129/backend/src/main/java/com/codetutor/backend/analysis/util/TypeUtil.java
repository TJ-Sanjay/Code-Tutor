package com.codetutor.backend.analysis.util;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Small helpers for working with Java type strings and identifiers. */
public final class TypeUtil {

    private TypeUtil() {}

    private static final Pattern WORD = Pattern.compile("[A-Z]+(?![a-z])|[A-Z]?[a-z]+|\\d+");

    /** "java.util.List&lt;Owner&gt;[]" → "List" */
    public static String raw(String type) {
        if (type == null) return null;
        String t = type.trim();
        int lt = t.indexOf('<');
        if (lt >= 0) t = t.substring(0, lt);
        t = t.replace("[]", "").replace("...", "").trim();
        int dot = t.lastIndexOf('.');
        // keep qualified Outer.Inner form only when the leading segment looks like a class
        if (dot >= 0) {
            String tail = t.substring(dot + 1);
            String head = t.substring(0, dot);
            String headLast = head.substring(head.lastIndexOf('.') + 1);
            if (!headLast.isEmpty() && Character.isUpperCase(headLast.charAt(0))) return headLast + "." + tail;
            return tail;
        }
        return t;
    }

    /** Top-level generic arguments: "Map&lt;String, List&lt;Owner&gt;&gt;" → ["String", "List&lt;Owner&gt;"] */
    public static List<String> typeArgs(String type) {
        List<String> out = new ArrayList<>();
        if (type == null) return out;
        int lt = type.indexOf('<');
        int gt = type.lastIndexOf('>');
        if (lt < 0 || gt < lt) return out;
        String inner = type.substring(lt + 1, gt);
        int depth = 0;
        StringBuilder cur = new StringBuilder();
        for (char c : inner.toCharArray()) {
            if (c == '<') depth++;
            if (c == '>') depth--;
            if (c == ',' && depth == 0) {
                out.add(cur.toString().trim());
                cur.setLength(0);
            } else cur.append(c);
        }
        if (cur.length() > 0) out.add(cur.toString().trim());
        return out;
    }

    /** Every simple type name mentioned in a type string, generic arguments included. */
    public static Set<String> allNames(String type) {
        Set<String> out = new LinkedHashSet<>();
        if (type == null) return out;
        Matcher m = Pattern.compile("[A-Za-z_][A-Za-z0-9_.]*").matcher(type);
        while (m.find()) {
            String s = m.group();
            int dot = s.lastIndexOf('.');
            out.add(dot >= 0 ? s.substring(dot + 1) : s);
        }
        return out;
    }

    /** "findByLastName" → ["find","by","last","name"] */
    public static List<String> words(String identifier) {
        List<String> out = new ArrayList<>();
        if (identifier == null) return out;
        Matcher m = WORD.matcher(identifier.replace('_', ' '));
        while (m.find()) out.add(m.group().toLowerCase(Locale.ROOT));
        return out;
    }

    /** "findByLastName" → "find by last name" */
    public static String humanize(String identifier) {
        return String.join(" ", words(identifier));
    }

    public static String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    public static String decapitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toLowerCase(s.charAt(0)) + s.substring(1);
    }

    public static String snake(String identifier) {
        return String.join("_", words(identifier));
    }

    public static String simpleName(String fqcn) {
        if (fqcn == null) return null;
        int i = fqcn.lastIndexOf('.');
        return i >= 0 ? fqcn.substring(i + 1) : fqcn;
    }

    public static String packageOf(String fqcn) {
        int i = fqcn.lastIndexOf('.');
        return i >= 0 ? fqcn.substring(0, i) : "";
    }

    public static String firstSentence(String text) {
        if (text == null) return null;
        String t = text.replaceAll("\\s+", " ").trim();
        if (t.isEmpty()) return null;
        int dot = t.indexOf(". ");
        if (dot > 0) t = t.substring(0, dot + 1);
        return t.length() > 220 ? t.substring(0, 217) + "..." : t;
    }
}
