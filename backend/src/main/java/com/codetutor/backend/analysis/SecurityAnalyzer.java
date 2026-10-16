package com.codetutor.backend.analysis;

import com.codetutor.backend.analysis.model.AnalysisModel.*;
import com.codetutor.backend.analysis.util.TypeUtil;
import com.github.javaparser.JavaParser;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.expr.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.AntPathMatcher;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

/** Detects how a Spring application secures itself: filter chain, auth rules, JWT, sessions, CSRF, CORS... */
public class SecurityAnalyzer {

    private static final Logger log = LoggerFactory.getLogger(SecurityAnalyzer.class);
    private static final AntPathMatcher MATCHER = new AntPathMatcher();

    private static final Set<String> ACCESS_CALLS = Set.of("permitAll", "authenticated", "hasRole", "hasAnyRole",
            "hasAuthority", "hasAnyAuthority", "denyAll", "anonymous", "access", "fullyAuthenticated", "rememberMe");
    private static final Set<String> MATCHER_CALLS = Set.of("requestMatchers", "antMatchers", "mvcMatchers",
            "securityMatchers", "regexMatchers");

    private record Pos(int line, int col) {}

    public Security analyze(Path root, List<ClassInfo> classes, List<EndpointInfo> endpoints, List<Library> libs) {
        boolean starter = libs.stream().anyMatch(l -> l.name().contains("spring-boot-starter-security")
                || l.name().contains("spring-security"));
        List<ClassInfo> secClasses = classes.stream()
                .filter(c -> !c.test() && ("SECURITY".equals(c.layer()) || hasAnn(c, "EnableWebSecurity")))
                .toList();
        List<ClassInfo> configCandidates = classes.stream().filter(c -> !c.test() && !c.id().isEmpty()
                && (hasAnn(c, "EnableWebSecurity") || hasAnn(c, "Configuration") || "SECURITY".equals(c.layer())))
                .toList();

        List<AuthRule> rules = new ArrayList<>();
        List<String> mechanisms = new ArrayList<>();
        List<String> notes = new ArrayList<>();
        List<SecurityFilter> customFilters = new ArrayList<>();
        String session = null, csrf = null, cors = null, encoder = null;
        String configClass = null, configFile = null;
        boolean corsEnabled = false, csrfDisabled = false, formLogin = false, httpBasic = false, oauth = false;
        boolean entryPoint = false;
        Map<String, Integer> callLines = new HashMap<>();

        ParserConfiguration cfg = new ParserConfiguration().setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_21);
        JavaParser parser = new JavaParser(cfg);

        for (ClassInfo c : configCandidates) {
            CompilationUnit cu;
            try {
                cu = parser.parse(root.resolve(c.file())).getResult().orElse(null);
            } catch (IOException | RuntimeException e) {
                continue;
            }
            if (cu == null) continue;
            String src = cu.toString();
            boolean hasChain = src.contains("HttpSecurity");
            if (src.contains("BCryptPasswordEncoder")) encoder = "BCrypt";
            else if (src.contains("Argon2")) encoder = "Argon2";
            else if (src.contains("Pbkdf2")) encoder = "PBKDF2";
            else if (src.contains("SCrypt")) encoder = "SCrypt";
            else if (src.contains("NoOpPasswordEncoder")) encoder = "NoOp (plain text!)";
            else if (src.contains("PasswordEncoderFactories")) encoder = "DelegatingPasswordEncoder";
            if (!hasChain) continue;
            configClass = c.id();
            configFile = c.file();

            for (MethodDeclaration md : cu.findAll(MethodDeclaration.class)) {
                boolean secMethod = md.getParameters().stream().anyMatch(p -> p.getType().asString().contains("HttpSecurity"))
                        || md.getType().asString().contains("SecurityFilterChain");
                if (!secMethod) continue;
                Map<String, String> fieldTypes = new HashMap<>();
                for (var f : c.fields()) fieldTypes.put(f.name(), f.type());
                for (Parameter p : md.getParameters()) fieldTypes.put(p.getNameAsString(), p.getType().asString());

                List<AuthRule> local = new ArrayList<>();
                Map<AuthRule, Pos> positions = new IdentityHashMap<>();
                for (MethodCallExpr mc : md.findAll(MethodCallExpr.class)) {
                    String n = mc.getNameAsString();
                    // NB: a chained call "begins" where its whole chain begins, so use the position of the method name
                    Pos pos = new Pos(mc.getName().getBegin().map(p -> p.line).orElse(0), mc.getName().getBegin().map(p -> p.column).orElse(0));
                    callLines.putIfAbsent(n, pos.line());
                    if (ACCESS_CALLS.contains(n)) {
                        AuthRule r = ruleOf(mc, c.file(), pos.line());
                        if (r != null) {
                            local.add(r);
                            // rules are ordered by where the matcher call starts, i.e. source order
                            positions.put(r, matcherPos(mc, pos));
                        }
                    }
                    switch (n) {
                        case "csrf" -> csrfDisabled |= mc.toString().contains("disable");
                        case "cors" -> corsEnabled = true;
                        case "formLogin" -> formLogin = true;
                        case "httpBasic" -> httpBasic = true;
                        case "oauth2Login", "oauth2Client" -> oauth = true;
                        case "oauth2ResourceServer" -> { oauth = true; mechanisms.add("OAuth2 Resource Server (JWT)"); }
                        case "exceptionHandling" -> entryPoint = true;
                        case "addFilterBefore", "addFilterAfter", "addFilterAt" -> {
                            if (mc.getArguments().size() == 2) {
                                String filterExpr = mc.getArgument(0).toString();
                                String anchor = mc.getArgument(1).toString().replace(".class", "");
                                String type = fieldTypes.get(filterExpr);
                                if (type == null && mc.getArgument(0) instanceof ObjectCreationExpr oc) {
                                    type = oc.getType().asString();
                                }
                                String rawType = TypeUtil.raw(type);
                                ClassInfo fc = classes.stream().filter(k -> k.name().equals(rawType)).findFirst()
                                        .orElse(null);
                                String where = n.replace("addFilter", "").toLowerCase(Locale.ROOT);
                                customFilters.add(new SecurityFilter(0, rawType != null ? rawType : filterExpr,
                                        fc != null ? fc.id() : null, fc != null ? fc.file() : c.file(),
                                        fc != null ? fc.line() : pos.line(),
                                        "Custom filter registered " + where + " " + anchor + "."));
                            }
                        }
                        default -> { }
                    }
                }
                for (Expression e : md.findAll(FieldAccessExpr.class)) {
                    FieldAccessExpr fa = (FieldAccessExpr) e;
                    if (fa.getScope().toString().endsWith("SessionCreationPolicy")) session = fa.getNameAsString();
                }
                local.sort(Comparator.comparingInt((AuthRule r) -> positions.get(r).line())
                        .thenComparingInt(r -> positions.get(r).col()));
                // anyRequest always last
                local.sort(Comparator.comparingInt(r -> "/**".equals(r.pattern()) && r.httpMethod() == null ? 1 : 0));
                rules.addAll(local);
            }
            for (Node n : cu.findAll(MethodDeclaration.class)) {
                MethodDeclaration md = (MethodDeclaration) n;
                if (md.getType().asString().contains("CorsConfigurationSource")) corsEnabled = true;
            }
            if (src.contains("setAllowedOrigins")) cors = "custom CorsConfigurationSource";
        }

        // jwt / token detection
        ClassInfo jwtFilter = classes.stream().filter(c -> !c.test() && "true".equals(c.attrs().get("filter"))
                && (c.name().toLowerCase(Locale.ROOT).contains("jwt") || c.name().toLowerCase(Locale.ROOT).contains("token")
                || c.name().toLowerCase(Locale.ROOT).contains("auth"))).findFirst().orElse(null);
        ClassInfo tokenService = classes.stream().filter(c -> !c.test() && c.name().toLowerCase(Locale.ROOT).contains("jwt")
                && c != jwtFilter && !"true".equals(c.attrs().get("filter"))).findFirst().orElse(null);
        ClassInfo userDetails = classes.stream().filter(c -> !c.test() && (c.implementsTypes().stream()
                .anyMatch(t -> TypeUtil.raw(t).equals("UserDetailsService")))).findFirst().orElse(null);
        if (jwtFilter != null) mechanisms.add("JWT Bearer token");
        if (formLogin) mechanisms.add("Form login");
        if (httpBasic) mechanisms.add("HTTP Basic");
        if (oauth && !mechanisms.contains("OAuth2 Resource Server (JWT)")) mechanisms.add("OAuth2");
        if (!mechanisms.isEmpty() && jwtFilter != null && session == null) session = "STATELESS";

        boolean enabled = starter || !secClasses.isEmpty() || configClass != null;
        if (!enabled) {
            return new Security(false, List.of(), "No Spring Security configuration detected: every endpoint is public.",
                    List.of(), List.of(), null, null, null, null, null, null, null, null, List.of());
        }

        // ordered filter chain
        List<SecurityFilter> chain = new ArrayList<>();
        int order = 1;
        if (corsEnabled) {
            chain.add(new SecurityFilter(order++, "CorsFilter", configClass, configFile, callLines.getOrDefault("cors", 1),
                    "Applies the CORS policy so the browser app on another origin may call the API."));
        }
        if (!csrfDisabled) {
            chain.add(new SecurityFilter(order++, "CsrfFilter", configClass, configFile, callLines.getOrDefault("csrf", 1),
                    "Validates the CSRF token on state-changing requests."));
        }
        for (SecurityFilter f : customFilters) {
            chain.add(new SecurityFilter(order++, f.name(), f.classId(), f.file(), f.line(), f.description()));
        }
        if (jwtFilter != null && customFilters.stream().noneMatch(f -> jwtFilter.id().equals(f.classId()))) {
            chain.add(new SecurityFilter(order++, jwtFilter.name(), jwtFilter.id(), jwtFilter.file(), jwtFilter.line(),
                    "Reads the Authorization header and authenticates the request."));
        }
        if (formLogin) {
            chain.add(new SecurityFilter(order++, "UsernamePasswordAuthenticationFilter", configClass, configFile,
                    callLines.getOrDefault("formLogin", 1), "Handles form login (username + password)."));
        }
        chain.add(new SecurityFilter(order++, "ExceptionTranslationFilter", configClass, configFile,
                callLines.getOrDefault("exceptionHandling", callLines.getOrDefault("authorizeHttpRequests", 1)),
                entryPoint ? "Converts access errors to 401/403 through the configured entry point."
                        : "Converts security exceptions into 401 / 403 responses."));
        chain.add(new SecurityFilter(order, "AuthorizationFilter", configClass, configFile,
                callLines.getOrDefault("authorizeHttpRequests", 1),
                "Checks the request against the authorizeHttpRequests rules below."));
        // enrich jwt filter description from javadoc / class summary
        for (int i = 0; i < chain.size(); i++) {
            SecurityFilter f = chain.get(i);
            ClassInfo fc = f.classId() == null ? null : classes.stream().filter(k -> k.id().equals(f.classId())).findFirst().orElse(null);
            if (fc != null && "true".equals(fc.attrs().get("filter")) && fc.doc() != null) {
                chain.set(i, new SecurityFilter(f.order(), f.name(), f.classId(), f.file(), f.line(),
                        TypeUtil.firstSentence(fc.doc())));
            }
        }

        if (csrfDisabled) notes.add("CSRF protection is disabled (normal for a stateless token API).");
        if (session != null) notes.add("Session policy: " + session + ".");
        if (rules.stream().anyMatch(r -> r.pattern().contains("h2-console"))) {
            notes.add("The H2 database console path is publicly accessible — fine for development, not for production.");
        }
        long pre = classes.stream().flatMap(c -> c.methods().stream())
                .filter(m -> m.annotations().stream().anyMatch(a -> Set.of("PreAuthorize", "Secured", "RolesAllowed",
                        "PostAuthorize").contains(a.name()))).count();
        if (pre > 0) notes.add(pre + " method(s) are additionally protected with method-level security annotations.");
        if (encoder == null) encoder = userDetails != null ? "PasswordEncoder bean" : null;

        String login = endpoints.stream().filter(e -> "POST".equals(e.httpMethod())
                && e.path().toLowerCase(Locale.ROOT).matches(".*(login|signin|authenticate|token).*"))
                .map(EndpointInfo::id).findFirst().orElse(null);

        StringBuilder sum = new StringBuilder("Spring Security is enabled");
        if (!mechanisms.isEmpty()) sum.append(" using ").append(String.join(" + ", mechanisms));
        if (!"STATELESS".equals(session) && mechanisms.isEmpty() && configClass == null) {
            sum.append(" with the Spring Boot defaults (every endpoint requires login)");
        }
        sum.append('.');
        if (encoder != null) sum.append(" Passwords are hashed with ").append(encoder).append('.');

        return new Security(true, mechanisms, sum.toString(), chain, rules, session,
                csrfDisabled ? "disabled" : "enabled", corsEnabled ? (cors != null ? cors : "enabled") : "default",
                encoder, userDetails != null ? userDetails.id() : null, tokenService != null ? tokenService.id() : null,
                login, configClass, notes);
    }

    private static boolean hasAnn(ClassInfo c, String name) {
        return c.annotations().stream().anyMatch(a -> a.name().equals(name));
    }

    private Pos matcherPos(MethodCallExpr access, Pos fallback) {
        Expression s = access.getScope().orElse(null);
        if (s instanceof MethodCallExpr sm && (MATCHER_CALLS.contains(sm.getNameAsString())
                || sm.getNameAsString().equals("anyRequest"))) {
            return new Pos(sm.getName().getBegin().map(p -> p.line).orElse(fallback.line()),
                    sm.getName().getBegin().map(p -> p.column).orElse(fallback.col()));
        }
        return fallback;
    }

    private AuthRule ruleOf(MethodCallExpr access, String file, int line) {
        String call = access.getNameAsString();
        String level = switch (call) {
            case "permitAll", "anonymous" -> "PUBLIC";
            case "authenticated", "fullyAuthenticated", "rememberMe" -> "AUTHENTICATED";
            case "denyAll" -> "DENIED";
            case "hasRole" -> "ROLE:" + strArgs(access).stream().findFirst().orElse("?");
            case "hasAnyRole" -> "ROLE:" + String.join("|", strArgs(access));
            case "hasAuthority" -> "AUTHORITY:" + strArgs(access).stream().findFirst().orElse("?");
            case "hasAnyAuthority" -> "AUTHORITY:" + String.join("|", strArgs(access));
            default -> "CUSTOM:" + access.getArguments().stream().map(Object::toString).collect(Collectors.joining(","));
        };
        Expression scope = access.getScope().orElse(null);
        if (!(scope instanceof MethodCallExpr sm)) return null;
        String sn = sm.getNameAsString();
        if (sn.equals("anyRequest")) return new AuthRule("/**", null, level, file, line);
        if (!MATCHER_CALLS.contains(sn)) return null;
        String method = null;
        List<String> patterns = new ArrayList<>();
        for (Expression a : sm.getArguments()) {
            if (a instanceof StringLiteralExpr s) patterns.add(s.getValue());
            else if (a instanceof FieldAccessExpr fa && fa.getScope().toString().endsWith("HttpMethod")) {
                method = fa.getNameAsString();
            } else if (a instanceof ArrayInitializerExpr ai) {
                for (Expression x : ai.getValues()) if (x instanceof StringLiteralExpr s2) patterns.add(s2.getValue());
            }
        }
        if (patterns.isEmpty()) return null;
        return new AuthRule(String.join(" , ", patterns), method, level, file, line);
    }

    private static List<String> strArgs(MethodCallExpr mc) {
        return mc.getArguments().stream().filter(a -> a instanceof StringLiteralExpr)
                .map(a -> ((StringLiteralExpr) a).getValue()).collect(Collectors.toList());
    }

    /** Evaluates which access level an endpoint gets from the security rules. */
    public static String accessFor(Security sec, EndpointInfo ep) {
        if (sec == null || !sec.enabled()) return "PUBLIC";
        if (sec.rules() != null) {
            for (AuthRule r : sec.rules()) {
                if (r.httpMethod() != null && !r.httpMethod().equalsIgnoreCase(ep.httpMethod())) continue;
                for (String p : r.pattern().split(" , ")) {
                    String pat = p.trim();
                    if (pat.equals("/**") || MATCHER.match(pat, ep.path())) return r.access();
                }
            }
        }
        return "AUTHENTICATED";
    }
}
