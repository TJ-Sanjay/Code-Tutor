package com.codetutor.backend.search;

import com.codetutor.backend.analysis.model.AnalysisModel.Chunk;
import com.codetutor.backend.analysis.util.TypeUtil;

import java.util.*;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Local semantic index over the code chunks.
 *
 * Every chunk is turned into a sparse TF-IDF embedding (identifiers are split into words, Java noise words are
 * dropped, light stemming, synonym expansion at query time). Search is cosine similarity with a boost for chunks
 * whose name matches the question. It needs no external service, so Q&amp;A works offline; the same interface can be
 * backed by a neural embedding model / pgvector later.
 */
public class VectorIndex {

    public record Hit(Chunk chunk, double score) {}

    private static final Set<String> STOP = new HashSet<>(Arrays.asList(
            "the", "a", "an", "of", "to", "in", "on", "for", "and", "or", "is", "are", "be", "this", "that", "it",
            "with", "as", "by", "at", "from", "how", "what", "where", "when", "why", "which", "does", "do", "did",
            "can", "i", "me", "my", "you", "we", "show", "tell", "about", "explain", "please", "give", "work", "works",
            "public", "private", "protected", "static", "final", "void", "return", "new", "import", "package", "class",
            "interface", "extends", "implements", "if", "else", "null", "true", "false", "this", "var", "int", "string",
            "get", "set", "throws", "throw", "try", "catch", "override", "java", "org", "com", "springframework"));

    private static final Map<String, List<String>> SYNONYMS = new HashMap<>();
    static {
        syn("login", "auth", "authenticate", "signin", "credentials", "token", "jwt", "password");
        syn("signin", "login", "auth", "authenticate");
        syn("logout", "signout", "token");
        syn("register", "signup", "create", "account", "user");
        syn("security", "auth", "jwt", "filter", "token", "authorization", "authentication", "permit", "role");
        syn("authentication", "login", "jwt", "token", "auth");
        syn("authorization", "role", "permission", "access", "authority");
        syn("database", "table", "entity", "repository", "jpa", "schema", "column");
        syn("db", "database", "table", "entity", "repository");
        syn("table", "entity", "column", "schema");
        syn("endpoint", "mapping", "controller", "route", "request", "api");
        syn("api", "endpoint", "controller", "mapping", "rest");
        syn("route", "endpoint", "mapping");
        syn("delete", "remove", "destroy");
        syn("remove", "delete");
        syn("create", "add", "save", "insert", "new", "register");
        syn("add", "create", "save", "insert");
        syn("update", "edit", "modify", "save", "put");
        syn("edit", "update", "modify");
        syn("find", "search", "get", "fetch", "query", "lookup", "list");
        syn("search", "find", "query", "filter");
        syn("list", "all", "find", "page", "collection");
        syn("user", "account", "owner", "person", "member");
        syn("config", "configuration", "setting", "bean", "property");
        syn("configuration", "config", "bean", "setting");
        syn("error", "exception", "handler", "advice", "fail");
        syn("exception", "error", "handler", "advice");
        syn("validate", "validation", "valid", "constraint", "validator");
        syn("validation", "validate", "valid", "constraint", "validator");
        syn("frontend", "react", "component", "ui", "page");
        syn("ui", "react", "component", "frontend", "page");
        syn("flow", "call", "controller", "service", "repository");
        syn("test", "junit", "mock", "assert");
        syn("start", "main", "application", "run", "boot");
        syn("startup", "main", "application", "boot");
        syn("upload", "multipart", "zip", "file");
        syn("clone", "git", "jgit", "repository");
    }

    private static void syn(String k, String... v) { SYNONYMS.put(k, Arrays.asList(v)); }

    private static final Pattern TOKEN = Pattern.compile("[A-Za-z][A-Za-z0-9_]*");

    private final List<Chunk> chunks;
    private final List<Map<String, Float>> vectors = new ArrayList<>();
    private final Map<String, Float> idf = new HashMap<>();
    private final List<Set<String>> nameTokens = new ArrayList<>();

    public VectorIndex(List<Chunk> chunks) {
        this.chunks = chunks;
        int n = chunks.size();
        List<Map<String, Integer>> tfs = new ArrayList<>(n);
        Map<String, Integer> df = new HashMap<>();
        for (Chunk c : chunks) {
            Map<String, Integer> tf = new HashMap<>();
            // header (name / module / layer) is weighted ×3
            String header = (c.name() == null ? "" : c.name()) + " " + (c.module() == null ? "" : c.module()) + " "
                    + (c.layer() == null ? "" : c.layer()) + " " + (c.file() == null ? "" : c.file());
            for (String t : tokens(header)) tf.merge(t, 3, Integer::sum);
            for (String t : tokens(c.text())) tf.merge(t, 1, Integer::sum);
            tfs.add(tf);
            nameTokens.add(new HashSet<>(tokens(c.name() == null ? "" : c.name())));
            for (String t : tf.keySet()) df.merge(t, 1, Integer::sum);
        }
        for (var e : df.entrySet()) idf.put(e.getKey(), (float) (Math.log((n + 1.0) / (e.getValue() + 1.0)) + 1.0));
        for (Map<String, Integer> tf : tfs) {
            Map<String, Float> v = new HashMap<>();
            double norm = 0;
            for (var e : tf.entrySet()) {
                float w = (float) ((1 + Math.log(e.getValue())) * idf.getOrDefault(e.getKey(), 1f));
                v.put(e.getKey(), w);
                norm += w * w;
            }
            float inv = norm == 0 ? 1f : (float) (1.0 / Math.sqrt(norm));
            v.replaceAll((k, w) -> w * inv);
            vectors.add(v);
        }
    }

    public int size() { return chunks.size(); }

    public List<Hit> search(String query, int k, Predicate<Chunk> filter) {
        Map<String, Float> q = queryVector(query);
        Set<String> qTokens = new HashSet<>(tokens(query));
        Set<String> qExpanded = expand(qTokens);
        List<Hit> hits = new ArrayList<>();
        for (int i = 0; i < chunks.size(); i++) {
            Chunk c = chunks.get(i);
            if (filter != null && !filter.test(c)) continue;
            double s = dot(q, vectors.get(i));
            if (s <= 0) continue;
            // boost chunks whose own name appears in the question (also through synonyms: validation ~ validator)
            Set<String> nt = nameTokens.get(i);
            if (!nt.isEmpty() && qTokens.containsAll(nt)) s += 0.25;
            else if (!nt.isEmpty()) {
                long direct = nt.stream().filter(qTokens::contains).count();
                long viaSyn = nt.stream().filter(t -> !qTokens.contains(t) && qExpanded.contains(t)).count();
                s += 0.08 * direct + 0.05 * viaSyn;
            }
            hits.add(new Hit(c, s));
        }
        hits.sort((a, b) -> Double.compare(b.score(), a.score()));
        return hits.size() > k ? new ArrayList<>(hits.subList(0, k)) : hits;
    }

    private Map<String, Float> queryVector(String query) {
        Map<String, Float> v = new HashMap<>();
        double norm = 0;
        for (String t : tokens(query)) {
            addWeight(v, t, 1.0f);
            for (String s : SYNONYMS.getOrDefault(t, List.of())) addWeight(v, stem(s), 0.35f);
        }
        for (var e : v.entrySet()) {
            float w = e.getValue() * idf.getOrDefault(e.getKey(), 0f);
            e.setValue(w);
            norm += w * w;
        }
        float inv = norm == 0 ? 1f : (float) (1.0 / Math.sqrt(norm));
        v.replaceAll((k2, w) -> w * inv);
        return v;
    }

    private static void addWeight(Map<String, Float> v, String t, float w) { v.merge(t, w, Float::sum); }

    /** The given (already stemmed) tokens plus their synonyms — used to match questions against names. */
    public static Set<String> expand(Collection<String> tokens) {
        Set<String> out = new HashSet<>(tokens);
        for (String t : tokens) for (String s : SYNONYMS.getOrDefault(t, List.of())) out.add(stem(s));
        return out;
    }

    private static double dot(Map<String, Float> a, Map<String, Float> b) {
        Map<String, Float> small = a.size() <= b.size() ? a : b;
        Map<String, Float> big = small == a ? b : a;
        double s = 0;
        for (var e : small.entrySet()) {
            Float o = big.get(e.getKey());
            if (o != null) s += e.getValue() * o;
        }
        return s;
    }

    /** Words of a text: identifiers split on camelCase / snake_case, lower-cased, stop-words removed, stemmed. */
    public static List<String> tokens(String text) {
        List<String> out = new ArrayList<>();
        if (text == null) return out;
        Matcher m = TOKEN.matcher(text);
        while (m.find()) {
            for (String w : TypeUtil.words(m.group())) {
                if (w.length() < 2 || STOP.contains(w) || w.matches("\\d+")) continue;
                out.add(stem(w));
            }
        }
        return out;
    }

    static String stem(String w) {
        if (w.length() > 4 && w.endsWith("ies")) return w.substring(0, w.length() - 3) + "y";
        if (w.length() > 4 && w.endsWith("sses")) return w.substring(0, w.length() - 2);
        if (w.length() > 3 && w.endsWith("s") && !w.endsWith("ss") && !w.endsWith("us")) return w.substring(0, w.length() - 1);
        if (w.length() > 5 && w.endsWith("ing")) return w.substring(0, w.length() - 3);
        if (w.length() > 4 && w.endsWith("ed")) return w.substring(0, w.length() - 2);
        return w;
    }
}
