package com.codetutor.backend.analysis.python;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A small, tolerant Python source parser. It does not build a full AST; it recovers what the analysis needs:
 * classes (bases, decorators, class attributes, methods), functions (parameters, decorators, body statements with
 * line numbers) and imports. Indentation, brackets, strings and decorators are handled, so it never throws on
 * unusual code — unknown syntax is simply skipped.
 */
public final class PyParser {

    private PyParser() {}

    /** One logical statement (physical lines joined while brackets / triple quotes / backslashes are open). */
    public record Stmt(int indent, int line, int endLine, String text) {}

    public record Param(String name, String annotation, String defaultValue) {}

    public static final class Func {
        public String name;
        public final List<Param> params = new ArrayList<>();
        public String returns;
        public final List<String> decorators = new ArrayList<>();
        public int line, endLine;
        public String doc;
        public boolean async;
        public final List<Stmt> body = new ArrayList<>();
        public Cls owner;
    }

    public static final class Cls {
        public String name;
        public final List<String> bases = new ArrayList<>();
        public final List<String> decorators = new ArrayList<>();
        public int line, endLine;
        public String doc;
        public final List<Func> methods = new ArrayList<>();
        public final List<Cls> nested = new ArrayList<>();
        public final List<Stmt> body = new ArrayList<>();
        public Cls outer;
        public int bodyIndent = -1;

        /** class-level {@code name = value} and {@code name: type = value} statements, in order. */
        public Map<String, Stmt> assignments() {
            Map<String, Stmt> out = new LinkedHashMap<>();
            for (Stmt s : body) {
                if (bodyIndent >= 0 && s.indent() != bodyIndent) continue;
                Assign a = assign(s.text());
                if (a != null) out.put(a.name(), new Stmt(s.indent(), s.line(), s.endLine(), a.value() == null ? "" : a.value()));
            }
            return out;
        }

        /** class-level annotated names without a value ({@code name: str}). */
        public Map<String, String> annotated() {
            Map<String, String> out = new LinkedHashMap<>();
            for (Stmt s : body) {
                if (bodyIndent >= 0 && s.indent() != bodyIndent) continue;
                Matcher m = ANNOTATED.matcher(s.text());
                if (m.matches()) out.put(m.group(1), m.group(2).trim());
            }
            return out;
        }
    }

    public record Imp(String module, String name, String alias, int level, boolean star) {}

    public static final class Module {
        public String path;
        public String doc;
        public final List<Cls> classes = new ArrayList<>();
        public final List<Func> functions = new ArrayList<>();
        public final List<Stmt> top = new ArrayList<>();
        public final List<Imp> imports = new ArrayList<>();
        public int lines;
    }

    public record Assign(String name, String annotation, String value) {}

    private static final Pattern ASSIGN = Pattern.compile("^([A-Za-z_][\\w]*)\\s*(?::\\s*([^=]+?))?\\s*(?<![=!<>])=(?!=)\\s*(.*)$", Pattern.DOTALL);
    private static final Pattern ANNOTATED = Pattern.compile("^([A-Za-z_]\\w*)\\s*:\\s*([^=]+)$");
    private static final Pattern CLASS_HDR = Pattern.compile("^class\\s+([A-Za-z_]\\w*)\\s*(\\(.*)?:?", Pattern.DOTALL);
    private static final Pattern DEF_HDR = Pattern.compile("^(async\\s+)?def\\s+([A-Za-z_]\\w*)\\s*\\(", Pattern.DOTALL);
    private static final Pattern FROM_IMPORT = Pattern.compile("^from\\s+(\\.*)([\\w.]*)\\s+import\\s+(.+)$", Pattern.DOTALL);
    private static final Pattern IMPORT = Pattern.compile("^import\\s+(.+)$", Pattern.DOTALL);

    // ───────────────────────── public API ─────────────────────────

    public static Module parse(String path, String source) {
        String[] lines = source.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1);
        Module mod = new Module();
        mod.path = path;
        mod.lines = lines.length;
        List<Stmt> stmts = statements(lines);
        build(mod, stmts);
        return mod;
    }

    public static Assign assign(String text) {
        Matcher m = ASSIGN.matcher(text);
        if (!m.matches()) return null;
        String name = m.group(1);
        if (Set.of("if", "for", "while", "return", "elif", "else", "with", "assert", "del", "raise", "yield", "lambda", "print")
                .contains(name)) return null;
        return new Assign(name, m.group(2), m.group(3));
    }

    // ───────────────────────── logical statements ─────────────────────────

    static List<Stmt> statements(String[] lines) {
        List<Stmt> out = new ArrayList<>();
        StringBuilder cur = null;
        int startLine = 0, indent = 0, depth = 0;
        char triple = 0;
        for (int i = 0; i < lines.length; i++) {
            String ln = lines[i];
            if (cur == null) {
                if (triple == 0 && isBlankOrComment(ln)) continue;
                indent = indentOf(ln);
                startLine = i + 1;
                cur = new StringBuilder();
                depth = 0;
            } else {
                cur.append('\n');
            }
            boolean cont = false;
            StringBuilder clean = new StringBuilder();
            int j = 0;
            while (j < ln.length()) {
                char ch = ln.charAt(j);
                if (triple != 0) {
                    if (ch == '\\' && j + 1 < ln.length()) { clean.append(ch).append(ln.charAt(j + 1)); j += 2; continue; }
                    if (ch == triple && ln.startsWith("" + triple + triple + triple, j)) {
                        clean.append(triple).append(triple).append(triple);
                        j += 3;
                        triple = 0;
                    } else {
                        clean.append(ch);
                        j++;
                    }
                    continue;
                }
                if (ch == '#') break;
                if (ch == '"' || ch == '\'') {
                    if (ln.startsWith("" + ch + ch + ch, j)) {
                        triple = ch;
                        clean.append(ch).append(ch).append(ch);
                        j += 3;
                        continue;
                    }
                    int k = j + 1;
                    while (k < ln.length() && ln.charAt(k) != ch) {
                        if (ln.charAt(k) == '\\') k++;
                        k++;
                    }
                    clean.append(ln, j, Math.min(k + 1, ln.length()));
                    j = k + 1;
                    continue;
                }
                if (ch == '(' || ch == '[' || ch == '{') depth++;
                else if (ch == ')' || ch == ']' || ch == '}') depth = Math.max(0, depth - 1);
                if (ch == '\\' && j == ln.length() - 1) { cont = true; j++; continue; }
                clean.append(ch);
                j++;
            }
            cur.append(clean);
            if (triple == 0 && depth == 0 && !cont) {
                out.add(new Stmt(indent, startLine, i + 1, cur.toString().strip()));
                cur = null;
            }
        }
        if (cur != null) out.add(new Stmt(indent, startLine, lines.length, cur.toString().strip()));
        return out;
    }

    private static boolean isBlankOrComment(String ln) {
        String t = ln.strip();
        return t.isEmpty() || t.startsWith("#");
    }

    private static int indentOf(String ln) {
        int n = 0;
        for (int i = 0; i < ln.length(); i++) {
            char c = ln.charAt(i);
            if (c == ' ') n++;
            else if (c == '\t') n += 4;
            else break;
        }
        return n;
    }

    // ───────────────────────── structure ─────────────────────────

    private static final class Node {
        final int indent;
        final Cls cls;
        final Func fn;
        boolean first = true;
        Node(int indent, Cls cls, Func fn) { this.indent = indent; this.cls = cls; this.fn = fn; }
    }

    private static void build(Module mod, List<Stmt> stmts) {
        Deque<Node> stack = new ArrayDeque<>();
        List<String> pending = new ArrayList<>();
        boolean firstTop = true;
        for (Stmt st : stmts) {
            while (!stack.isEmpty() && st.indent() <= stack.peek().indent) stack.pop();
            String t = st.text();
            if (t.startsWith("@")) { pending.add(t.substring(1).strip()); continue; }
            Node top = stack.peek();
            boolean inDef = top != null && top.fn != null;

            Matcher cm = CLASS_HDR.matcher(t);
            Matcher dm = DEF_HDR.matcher(t);
            if (!inDef && t.startsWith("class ") && cm.matches()) {
                Cls c = new Cls();
                c.name = cm.group(1);
                c.line = st.line();
                c.endLine = st.endLine();
                c.decorators.addAll(pending);
                pending.clear();
                if (cm.group(2) != null) {
                    int close = matchParen(cm.group(2), 0);
                    if (close > 0) for (String b : splitTop(cm.group(2).substring(1, close))) {
                        String s = b.strip();
                        if (!s.isEmpty() && !s.contains("=")) c.bases.add(s);
                    }
                }
                if (top != null && top.cls != null) {
                    c.outer = top.cls;
                    top.cls.nested.add(c);
                } else if (top == null) {
                    mod.classes.add(c);
                } else {
                    mod.classes.add(c);
                }
                stack.push(new Node(st.indent(), c, null));
                touch(stack, st);
                continue;
            }
            if (!inDef && (t.startsWith("def ") || t.startsWith("async ")) && dm.find()) {
                Func f = new Func();
                f.async = dm.group(1) != null;
                f.name = dm.group(2);
                f.line = st.line();
                f.endLine = st.endLine();
                f.decorators.addAll(pending);
                pending.clear();
                int open = dm.end() - 1;
                int close = matchParen(t, open);
                if (close > open) {
                    for (String p : splitTop(t.substring(open + 1, close))) {
                        Param pr = param(p);
                        if (pr != null) f.params.add(pr);
                    }
                    String rest = t.substring(close + 1);
                    int colon = headerColon(rest);
                    String ret = colon >= 0 ? rest.substring(0, colon) : rest;
                    ret = ret.strip();
                    if (ret.startsWith("->")) f.returns = ret.substring(2).strip();
                    if (colon >= 0 && colon + 1 < rest.length()) {
                        String inline = rest.substring(colon + 1).strip();
                        if (!inline.isEmpty()) f.body.add(new Stmt(st.indent() + 4, st.line(), st.endLine(), inline));
                    }
                }
                if (top != null && top.cls != null) {
                    f.owner = top.cls;
                    top.cls.methods.add(f);
                } else {
                    mod.functions.add(f);
                }
                stack.push(new Node(st.indent(), null, f));
                touch(stack, st);
                continue;
            }

            // ordinary statement
            pending.clear();
            if (top == null) {
                mod.top.add(st);
                if (firstTop && isString(t)) mod.doc = unquote(t);
                firstTop = false;
                parseImport(mod, t);
            } else if (top.fn != null) {
                if (top.first && isString(t)) top.fn.doc = unquote(t);
                else top.fn.body.add(st);
                top.first = false;
                parseImport(mod, t);
            } else {
                if (top.cls.bodyIndent < 0) top.cls.bodyIndent = st.indent();
                if (top.first && isString(t)) top.cls.doc = unquote(t);
                else top.cls.body.add(st);
                top.first = false;
            }
            touch(stack, st);
        }
    }

    private static void touch(Deque<Node> stack, Stmt st) {
        for (Node n : stack) {
            if (n.fn != null) n.fn.endLine = Math.max(n.fn.endLine, st.endLine());
            if (n.cls != null) n.cls.endLine = Math.max(n.cls.endLine, st.endLine());
        }
    }

    private static Param param(String raw) {
        String s = raw.strip();
        if (s.isEmpty() || s.equals("*") || s.equals("/")) return null;
        while (s.startsWith("*")) s = s.substring(1);
        Matcher m = Pattern.compile("^([A-Za-z_]\\w*)\\s*(?::\\s*([^=]+?))?\\s*(?:=\\s*(.*))?$", Pattern.DOTALL).matcher(s);
        if (!m.matches()) return null;
        return new Param(m.group(1), m.group(2), m.group(3));
    }

    private static void parseImport(Module mod, String t) {
        if (t.startsWith("from ")) {
            Matcher m = FROM_IMPORT.matcher(t);
            if (!m.matches()) return;
            int level = m.group(1).length();
            String module = m.group(2);
            String names = m.group(3).strip();
            if (names.startsWith("(")) names = names.substring(1, names.endsWith(")") ? names.length() - 1 : names.length());
            if (names.strip().equals("*")) {
                mod.imports.add(new Imp(module, null, null, level, true));
                return;
            }
            for (String n : names.split(",")) {
                String x = n.strip();
                if (x.isEmpty()) continue;
                String[] parts = x.split("\\s+as\\s+");
                String name = parts[0].strip();
                if (!name.matches("[\\w]+")) continue;
                mod.imports.add(new Imp(module, name, parts.length > 1 ? parts[1].strip() : name, level, false));
            }
        } else if (t.startsWith("import ")) {
            Matcher m = IMPORT.matcher(t);
            if (!m.matches()) return;
            for (String n : m.group(1).split(",")) {
                String[] parts = n.strip().split("\\s+as\\s+");
                String module = parts[0].strip();
                if (!module.matches("[\\w.]+")) continue;
                mod.imports.add(new Imp(module, null, parts.length > 1 ? parts[1].strip() : module.split("\\.")[0], 0, false));
            }
        }
    }

    // ───────────────────────── text helpers (shared with the analyzer) ─────────────────────────

    public static boolean isString(String t) {
        return t.matches("(?s)^[rRuUbBfF]{0,2}(\"\"\"|'''|\"|').*");
    }

    public static String unquote(String t) {
        String s = t.strip().replaceFirst("^[rRuUbBfF]{0,2}", "");
        for (String q : List.of("\"\"\"", "'''", "\"", "'")) {
            if (s.startsWith(q) && s.endsWith(q) && s.length() >= 2 * q.length()) {
                s = s.substring(q.length(), s.length() - q.length());
                break;
            }
        }
        return s.replaceAll("\\s*\\n\\s*", "\n").strip();
    }

    /** Index of the bracket matching the opener at {@code open}, ignoring strings; -1 if unbalanced. */
    public static int matchParen(String s, int open) {
        int depth = 0;
        for (int i = open; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '"' || c == '\'') {
                if (s.startsWith("" + c + c + c, i)) {
                    int end = s.indexOf("" + c + c + c, i + 3);
                    if (end < 0) return -1;
                    i = end + 2;
                } else {
                    int k = i + 1;
                    while (k < s.length() && s.charAt(k) != c) { if (s.charAt(k) == '\\') k++; k++; }
                    i = k;
                }
                continue;
            }
            if (c == '(' || c == '[' || c == '{') depth++;
            else if (c == ')' || c == ']' || c == '}') {
                depth--;
                if (depth == 0) return i;
            }
        }
        return -1;
    }

    private static int headerColon(String rest) {
        int depth = 0;
        for (int i = 0; i < rest.length(); i++) {
            char c = rest.charAt(i);
            if (c == '"' || c == '\'') {
                int k = i + 1;
                while (k < rest.length() && rest.charAt(k) != c) { if (rest.charAt(k) == '\\') k++; k++; }
                i = k;
                continue;
            }
            if (c == '(' || c == '[' || c == '{') depth++;
            else if (c == ')' || c == ']' || c == '}') depth--;
            else if (c == ':' && depth == 0) return i;
        }
        return -1;
    }

    /** Splits on top-level commas (outside brackets and strings). */
    public static List<String> splitTop(String s) {
        List<String> parts = new ArrayList<>();
        int depth = 0;
        int start = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '"' || c == '\'') {
                if (s.startsWith("" + c + c + c, i)) {
                    int end = s.indexOf("" + c + c + c, i + 3);
                    i = end < 0 ? s.length() : end + 2;
                } else {
                    int k = i + 1;
                    while (k < s.length() && s.charAt(k) != c) { if (s.charAt(k) == '\\') k++; k++; }
                    i = k;
                }
                continue;
            }
            if (c == '(' || c == '[' || c == '{') depth++;
            else if (c == ')' || c == ']' || c == '}') depth--;
            else if (c == ',' && depth == 0) {
                parts.add(s.substring(start, i));
                start = i + 1;
            }
        }
        if (start < s.length()) parts.add(s.substring(start));
        List<String> out = new ArrayList<>();
        for (String p : parts) if (!p.isBlank()) out.add(p.strip());
        return out;
    }

    /** Replaces the inside of string literals with spaces (keeps length and newlines) so regexes don't match inside text. */
    public static String maskStrings(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        int i = 0;
        while (i < s.length()) {
            char c = s.charAt(i);
            if (c == '"' || c == '\'') {
                boolean tri = s.startsWith("" + c + c + c, i);
                int q = tri ? 3 : 1;
                int k = i + q;
                while (k < s.length()) {
                    if (s.charAt(k) == '\\') { k += 2; continue; }
                    if (tri ? s.startsWith("" + c + c + c, k) : s.charAt(k) == c) break;
                    if (!tri && s.charAt(k) == '\n') break;
                    k++;
                }
                int end = Math.min(s.length(), k + q);
                sb.append(c);
                for (int x = i + 1; x < end - 1 && x < s.length(); x++) sb.append(s.charAt(x) == '\n' ? '\n' : ' ');
                if (end - 1 > i) sb.append(c);
                i = end;
                continue;
            }
            sb.append(c);
            i++;
        }
        return sb.toString();
    }

    /** The string value of a literal such as {@code 'abc'}, {@code r"^x$"}; null when it is not a plain literal. */
    public static String literal(String expr) {
        if (expr == null) return null;
        String s = expr.strip();
        Matcher m = Pattern.compile("^[rRuUbB]{0,2}(['\"])(.*)\\1$", Pattern.DOTALL).matcher(s);
        if (m.matches() && !m.group(2).contains(m.group(1))) return m.group(2);
        return null;
    }

    /** All plain string literals found in an expression, in order. */
    public static List<String> strings(String expr) {
        List<String> out = new ArrayList<>();
        Matcher m = Pattern.compile("[rRuUbB]{0,2}(['\"])((?:\\\\.|(?!\\1).)*)\\1").matcher(expr);
        while (m.find()) out.add(m.group(2));
        return out;
    }

    /** A call expression {@code name(args)} → [name, args]; null if the text is not a single call. */
    public static String[] call(String expr) {
        String s = expr.strip();
        int open = s.indexOf('(');
        if (open <= 0) return null;
        String name = s.substring(0, open).strip();
        if (!name.matches("[A-Za-z_][\\w.]*")) return null;
        int close = matchParen(s, open);
        if (close != s.length() - 1) return null;
        return new String[]{name, s.substring(open + 1, close)};
    }

    /** Splits call arguments into positional list and keyword map (values kept as source text). */
    public static void args(String argText, List<String> positional, Map<String, String> keywords) {
        for (String a : splitTop(argText)) {
            Matcher m = Pattern.compile("^([A-Za-z_]\\w*)\\s*=(?!=)\\s*(.*)$", Pattern.DOTALL).matcher(a);
            if (m.matches()) keywords.put(m.group(1), m.group(2).strip());
            else if (!a.startsWith("*")) positional.add(a);
        }
    }
}
