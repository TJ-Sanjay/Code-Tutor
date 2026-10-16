package com.codetutor.backend.analysis;

import com.codetutor.backend.analysis.java.JavaAnalyzer;
import com.codetutor.backend.analysis.model.AnalysisModel.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Runs the analyzers on a tiny Spring Boot project written to a temp folder. */
class AnalyzerTest {

    @TempDir static Path root;
    static JavaAnalyzer.Output java;
    static Schema schema;
    static Security security;

    static void write(String rel, String content) throws IOException {
        Path p = root.resolve(rel);
        Files.createDirectories(p.getParent());
        Files.writeString(p, content);
    }

    @BeforeAll
    static void analyse() throws IOException {
        String base = "src/main/java/com/example/shop/";
        write(base + "user/UserController.java", """
                package com.example.shop.user;
                import org.springframework.web.bind.annotation.*;
                @RestController
                @RequestMapping("/api/users")
                public class UserController {
                    private final UserService userService;
                    public UserController(UserService userService) { this.userService = userService; }
                    @GetMapping("/{id}")
                    public User getUser(@PathVariable Long id) { return userService.findUser(id); }
                    @PostMapping
                    public User create(@RequestBody User u) { return userService.save(u); }
                    @GetMapping("/public/list")
                    public String list() { return "x"; }
                }
                """);
        write(base + "user/UserService.java", """
                package com.example.shop.user;
                import org.springframework.stereotype.Service;
                @Service
                public class UserService {
                    private final UserRepository userRepository;
                    public UserService(UserRepository userRepository) { this.userRepository = userRepository; }
                    public User findUser(Long id) { return userRepository.findById(id).orElseThrow(); }
                    public User save(User u) { return userRepository.save(u); }
                }
                """);
        write(base + "user/UserRepository.java", """
                package com.example.shop.user;
                import org.springframework.data.jpa.repository.JpaRepository;
                public interface UserRepository extends JpaRepository<User, Long> {}
                """);
        write(base + "user/User.java", """
                package com.example.shop.user;
                import jakarta.persistence.*;
                import java.util.List;
                @Entity @Table(name = "users")
                public class User {
                    @Id @GeneratedValue private Long id;
                    @Column(nullable = false, length = 50) private String name;
                    @OneToMany(mappedBy = "owner") private List<Order> orders;
                }
                """);
        write(base + "order/Order.java", """
                package com.example.shop.order;
                import com.example.shop.user.User;
                import jakarta.persistence.*;
                @Entity @Table(name = "orders")
                public class Order {
                    @Id private Long id;
                    @ManyToOne @JoinColumn(name = "user_id") private User owner;
                }
                """);
        write(base + "security/SecurityConfig.java", """
                package com.example.shop.security;
                import org.springframework.context.annotation.*;
                import org.springframework.http.HttpMethod;
                import org.springframework.security.config.annotation.web.builders.HttpSecurity;
                import org.springframework.security.config.http.SessionCreationPolicy;
                import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
                import org.springframework.security.web.SecurityFilterChain;
                @Configuration
                @EnableWebSecurity
                public class SecurityConfig {
                    @Bean
                    SecurityFilterChain chain(HttpSecurity http) throws Exception {
                        http.csrf(c -> c.disable())
                            .authorizeHttpRequests(a -> a
                                .requestMatchers("/api/users/public/**").permitAll()
                                .requestMatchers(HttpMethod.GET, "/api/users/*").hasRole("ADMIN")
                                .anyRequest().authenticated())
                            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS));
                        return http.build();
                    }
                    @Bean BCryptPasswordEncoder encoder() { return new BCryptPasswordEncoder(); }
                }
                """);

        List<String> files = List.of(
                base + "user/UserController.java", base + "user/UserService.java", base + "user/UserRepository.java",
                base + "user/User.java", base + "order/Order.java", base + "security/SecurityConfig.java");
        java = new JavaAnalyzer().analyze(root, files, 2_000_000, "");
        schema = new SchemaAnalyzer().analyze(root, java.classes(), List.of());
        security = new SecurityAnalyzer().analyze(root, java.classes(), java.endpoints(), List.of());
    }

    ClassInfo cls(String name) {
        return java.classes().stream().filter(c -> c.name().equals(name)).findFirst().orElseThrow();
    }

    @Test
    void detects_layers_and_modules() {
        assertEquals("CONTROLLER", cls("UserController").layer());
        assertEquals("SERVICE", cls("UserService").layer());
        assertEquals("REPOSITORY", cls("UserRepository").layer());
        assertEquals("ENTITY", cls("User").layer());
        assertEquals("user", cls("UserService").module());
        assertEquals("order", cls("Order").module());
        assertEquals("com.example.shop", java.basePackage());
    }

    @Test
    void resolves_dependencies_and_calls() {
        assertTrue(cls("UserController").dependsOn().stream().anyMatch(d -> d.kind().equals("INJECTS") && d.target().endsWith("UserService")));
        assertTrue(cls("UserService").dependsOn().stream().anyMatch(d -> d.kind().equals("INJECTS") && d.target().endsWith("UserRepository")));
        MethodInfo find = cls("UserService").methods().stream().filter(m -> m.name().equals("findUser")).findFirst().orElseThrow();
        assertTrue(find.calls().stream().anyMatch(c -> c.method().equals("findById") && c.targetClassName().equals("UserRepository")));
        MethodInfo get = cls("UserController").methods().stream().filter(m -> m.name().equals("getUser")).findFirst().orElseThrow();
        assertTrue(get.calls().stream().anyMatch(c -> c.method().equals("findUser") && c.targetMethodId() != null));
    }

    @Test
    void extracts_endpoints() {
        assertEquals(3, java.endpoints().size());
        assertTrue(java.endpoints().stream().anyMatch(e -> e.httpMethod().equals("GET") && e.path().equals("/api/users/{id}")));
        assertTrue(java.endpoints().stream().anyMatch(e -> e.httpMethod().equals("POST") && e.path().equals("/api/users")));
    }

    @Test
    void builds_schema_with_relationship() {
        assertTrue(schema.tables().stream().anyMatch(t -> t.name().equals("users")));
        Table orders = schema.tables().stream().filter(t -> t.name().equals("orders")).findFirst().orElseThrow();
        assertTrue(orders.columns().stream().anyMatch(c -> c.name().equals("user_id") && "users.id".equals(c.foreignKey())));
        assertEquals(1, schema.relations().size());
        assertEquals("N:1", schema.relations().get(0).cardinality());
        Table users = schema.tables().stream().filter(t -> t.name().equals("users")).findFirst().orElseThrow();
        Column name = users.columns().stream().filter(c -> c.name().equals("name")).findFirst().orElseThrow();
        assertFalse(name.nullable());
        assertEquals(50, name.length());
    }

    @Test
    void security_rules_are_ordered_and_evaluated() {
        assertTrue(security.enabled());
        assertEquals("STATELESS", security.sessionPolicy());
        assertEquals("disabled", security.csrf());
        assertEquals("BCrypt", security.passwordEncoder());
        assertEquals("/api/users/public/**", security.rules().get(0).pattern());
        assertEquals("/**", security.rules().get(security.rules().size() - 1).pattern());

        EndpointInfo pub = java.endpoints().stream().filter(e -> e.path().equals("/api/users/public/list")).findFirst().orElseThrow();
        EndpointInfo get = java.endpoints().stream().filter(e -> e.httpMethod().equals("GET") && e.path().equals("/api/users/{id}")).findFirst().orElseThrow();
        EndpointInfo post = java.endpoints().stream().filter(e -> e.httpMethod().equals("POST")).findFirst().orElseThrow();
        assertEquals("PUBLIC", SecurityAnalyzer.accessFor(security, pub));
        assertEquals("ROLE:ADMIN", SecurityAnalyzer.accessFor(security, get));
        assertEquals("AUTHENTICATED", SecurityAnalyzer.accessFor(security, post));
    }
}
