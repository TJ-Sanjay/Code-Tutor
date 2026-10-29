package com.codetutor.backend.analysis;

import com.codetutor.backend.analysis.model.AnalysisModel.*;
import com.codetutor.backend.scanner.service.FileFilterService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/** Runs the whole analysis pipeline on a small Django + Django REST Framework project (src/test/resources/django-sample). */
class DjangoAnalysisTest {

    static Result r;

    @BeforeAll
    static void analyse() throws Exception {
        Path root = Paths.get("src/test/resources/django-sample").toAbsolutePath();
        AnalysisPipeline pipeline = new AnalysisPipeline(new FileFilterService(), 8000, 200_000_000, 2_000_000);
        r = pipeline.analyze(root, "django-sample", Set.of(), (p, m) -> { });
    }

    static Optional<EndpointInfo> ep(String method, String path) {
        return r.endpoints().stream().filter(e -> e.httpMethod().equals(method) && e.path().equals(path)).findFirst();
    }

    static ClassInfo cls(String name) {
        return r.classes().stream().filter(c -> c.name().equals(name)).findFirst().orElseThrow(() -> new AssertionError("no class " + name));
    }

    @Test
    void detectsTheStack() {
        assertTrue(r.overview().frameworks().contains("Django"), r.overview().frameworks().toString());
        assertTrue(r.overview().frameworks().contains("Django REST Framework"), r.overview().frameworks().toString());
        assertTrue(r.overview().databases().contains("PostgreSQL"), r.overview().databases().toString());
        assertEquals("Python", r.overview().languages().keySet().iterator().next());
    }

    @Test
    void assignsLayersAndModules() {
        assertEquals("ENTITY", cls("Product").layer());
        assertEquals("CONTROLLER", cls("ProductViewSet").layer());
        assertEquals("CONTROLLER", cls("OrderHistoryView").layer());
        assertEquals("DTO", cls("ProductSerializer").layer());
        assertEquals("SERVICE", cls("OrderService").layer());
        assertEquals("SECURITY", cls("IsOwner").layer());
        assertEquals("REPOSITORY", cls("ProductQuerySet").layer());
        assertEquals("CONFIG", cls("ProductAdmin").layer());
        assertEquals("shop", cls("Product").module());
        assertEquals("accounts", cls("User").module());
        assertTrue(r.modules().stream().anyMatch(m -> m.name().equals("shop")));
    }

    @Test
    void followsTheUrlConfigurationIntoEndpoints() {
        EndpointInfo list = ep("GET", "/api/catalogue/").orElseThrow();
        assertEquals("PUBLIC", list.access());
        assertTrue(list.viewResponse());
        assertTrue(list.params().stream().anyMatch(p -> p.name().equals("q") && p.source().equals("query")));

        EndpointInfo detail = ep("GET", "/api/catalogue/{pk}/").orElseThrow();
        assertEquals("AUTHENTICATED", detail.access());
        assertTrue(detail.params().stream().anyMatch(p -> p.name().equals("pk") && p.source().equals("path") && p.type().equals("int")));

        assertTrue(ep("GET", "/api/my-orders/").isPresent());
        assertEquals("AUTHENTICATED", ep("GET", "/api/my-orders/").get().access());
        assertTrue(ep("GET", "/accounts/login/").isPresent());
        assertTrue(ep("POST", "/accounts/login/").isPresent());
        assertTrue(ep("POST", "/accounts/register/").isPresent());
    }

    @Test
    void expandsRoutersAndViewSets() {
        // ModelViewSet → six routes, ReadOnlyModelViewSet → two, @action → one extra
        assertTrue(ep("GET", "/api/products/").isPresent());
        assertTrue(ep("POST", "/api/products/").isPresent());
        assertTrue(ep("GET", "/api/products/{pk}/").isPresent());
        assertTrue(ep("PUT", "/api/products/{pk}/").isPresent());
        assertTrue(ep("PATCH", "/api/products/{pk}/").isPresent());
        assertTrue(ep("DELETE", "/api/products/{pk}/").isPresent());
        assertTrue(ep("GET", "/api/products/in_stock/").isPresent());
        assertTrue(ep("GET", "/api/categories/").isPresent());
        assertTrue(ep("GET", "/api/categories/{pk}/").isPresent());
        assertFalse(ep("POST", "/api/categories/").isPresent(), "read-only viewset must not expose POST");

        // DRF default permission IsAuthenticatedOrReadOnly: reading is public, writing needs a login
        assertEquals("PUBLIC", ep("GET", "/api/products/").get().access());
        assertEquals("AUTHENTICATED", ep("POST", "/api/products/").get().access());
        // permission_classes on the viewset
        assertEquals("AUTHENTICATED", ep("GET", "/api/orders/").get().access());
        // POST body comes from the serializer fields
        assertTrue(ep("POST", "/api/products/").get().params().stream().anyMatch(p -> p.name().equals("price") && p.source().equals("body")));
    }

    @Test
    void buildsTheDatabaseSchemaFromModels() {
        Schema s = r.schema();
        List<String> names = s.tables().stream().map(Table::name).toList();
        assertTrue(names.containsAll(List.of("shop_category", "shop_product", "shop_tag", "shop_order", "shop_orderitem",
                "shop_invoice", "accounts_user", "shop_product_tags")), names.toString());
        Table product = s.tables().stream().filter(t -> t.name().equals("shop_product")).findFirst().orElseThrow();
        assertTrue(product.columns().stream().anyMatch(c -> c.name().equals("id") && c.primaryKey()));
        assertTrue(product.columns().stream().anyMatch(c -> c.name().equals("created_at")), "abstract base fields are inherited");
        assertTrue(product.columns().stream().anyMatch(c -> c.name().equals("category_id") && c.foreignKey() != null
                && c.foreignKey().equals("shop_category.id")));
        assertTrue(product.columns().stream().anyMatch(c -> c.name().equals("price") && c.type().equals("decimal(8,2)")));
        assertFalse(names.contains("shop_timestamped"), "abstract models have no table");
        // user model comes from AbstractUser
        Table user = s.tables().stream().filter(t -> t.name().equals("accounts_user")).findFirst().orElseThrow();
        assertTrue(user.columns().stream().anyMatch(c -> c.name().equals("username")));
        // relations: FK, one-to-one, many-to-many join table, custom user model
        assertTrue(s.relations().stream().anyMatch(x -> x.fromTable().equals("shop_product") && x.toTable().equals("shop_category") && x.cardinality().equals("N:1")));
        assertTrue(s.relations().stream().anyMatch(x -> x.fromTable().equals("shop_invoice") && x.toTable().equals("shop_order") && x.cardinality().equals("1:1")));
        assertTrue(s.relations().stream().anyMatch(x -> x.fromTable().equals("shop_order") && x.toTable().equals("accounts_user")));
        assertTrue(s.relations().stream().anyMatch(x -> x.fromTable().equals("shop_product_tags") && x.toTable().equals("shop_tag")));
        assertTrue(s.relations().stream().anyMatch(x -> x.fromTable().equals("shop_orderitem") && x.toTable().equals("shop_product")));
    }

    @Test
    void resolvesCallsAcrossModulesAndTheOrm() {
        MethodInfo place = cls("OrderService").methods().stream().filter(m -> m.name().equals("place_order")).findFirst().orElseThrow();
        assertTrue(place.calls().stream().anyMatch(c -> c.targetClassName().equals("Order") && c.method().equals("create") && c.library()));
        assertTrue(place.calls().stream().anyMatch(c -> c.targetClassName().equals("OrderItem") && c.method().equals("create")));

        MethodInfo checkout = r.classes().stream().filter(c -> c.name().equals("shop.views")).flatMap(c -> c.methods().stream())
                .filter(m -> m.name().equals("checkout")).findFirst().orElseThrow();
        assertTrue(checkout.calls().stream().anyMatch(c -> c.targetClassName().equals("OrderService") && c.method().equals("place_order")
                && c.targetMethodId() != null), "service.place_order() must resolve through the local variable");

        MethodInfo list = r.classes().stream().filter(c -> c.name().equals("shop.views")).flatMap(c -> c.methods().stream())
                .filter(m -> m.name().equals("product_list")).findFirst().orElseThrow();
        assertTrue(list.calls().stream().anyMatch(c -> c.targetClassName().equals("ProductQuerySet") && c.method().equals("in_stock")),
                "custom queryset method called through Product.objects");
    }

    @Test
    void describesSecurity() {
        Security s = r.security();
        assertTrue(s.enabled());
        assertTrue(s.summary().contains("Django"), s.summary());
        assertTrue(s.mechanisms().contains("JWT Bearer token"), s.mechanisms().toString());
        assertTrue(s.mechanisms().stream().anyMatch(m -> m.startsWith("Session")), s.mechanisms().toString());
        assertEquals("enabled", s.csrf());
        assertTrue(s.cors().contains("localhost:5173"), s.cors());
        assertEquals("accounts.models.User", s.userDetailsService());
        List<String> chain = s.filters().stream().map(SecurityFilter::name).toList();
        assertTrue(chain.indexOf("SessionMiddleware") < chain.indexOf("AuthenticationMiddleware"), chain.toString());
        assertTrue(chain.contains("RequestTimingMiddleware"));
        assertEquals("AuthorizationFilter", chain.get(chain.size() - 1));
        assertTrue(s.notes().stream().anyMatch(n -> n.contains("DEBUG")), s.notes().toString());
        assertTrue(s.notes().stream().anyMatch(n -> n.contains("csrf_exempt")), s.notes().toString());
    }

    @Test
    void knowledgeGraphAndChunksCoverPython() {
        assertTrue(r.nodes().stream().anyMatch(n -> n.type().equals("CLASS") && n.label().equals("Product")));
        assertTrue(r.nodes().stream().anyMatch(n -> n.type().equals("TABLE") && n.label().equals("shop_product")));
        assertTrue(r.edges().stream().anyMatch(e -> e.type().equals("MAPS_TO")));
        assertTrue(r.edges().stream().anyMatch(e -> e.type().equals("REFERENCES")));
        assertFalse(r.chunks().isEmpty());
        assertTrue(r.chunks().stream().anyMatch(c -> c.text().contains("place_order")));
    }

    @Test
    void chatFindsEndpointsFromEverydayWording() {
        Knowledge k = new Knowledge(r);
        // "checks out" must find the one-word route /checkout/
        assertTrue(k.flows().matchEndpoints("what happens when a user checks out an order", 3).stream()
                .anyMatch(e -> e.path().equals("/api/checkout/")));
        // an explicit method + path still wins
        assertEquals("/api/products/", k.flows().matchEndpoints("How does POST /api/products/ work?", 3).get(0).path());
    }

    @Test
    void chatListsEveryEndpointEvenWithATypo() {
        Knowledge k = new Knowledge(r);
        var llm = new com.codetutor.backend.chat.LlmClient("none", "", "", "", new com.fasterxml.jackson.databind.ObjectMapper());
        var answer = new com.codetutor.backend.chat.ChatEngine(k, llm).answer("what are the ednpoints are there", null, List.of());
        assertTrue(answer.answer().contains("/api/catalogue/{pk}/") && answer.answer().contains("/api/orders/{pk}/")
                && answer.answer().contains("/accounts/register/"), answer.answer());
    }

    @Test
    void producesAFlowThroughTheOrmToTheTable() {
        Knowledge k = new Knowledge(r);
        EndpointInfo e = ep("GET", "/api/products/").orElseThrow();
        Flow f = k.flows().endpointFlow(e.id());
        assertNotNull(f);
        List<String> kinds = f.steps().stream().map(FlowStep::kind).toList();
        assertTrue(kinds.contains("FILTER"), kinds.toString());
        assertTrue(kinds.contains("CONTROLLER"), kinds.toString());
        assertTrue(kinds.contains("TABLE"), kinds.toString());
        assertTrue(f.steps().stream().anyMatch(s -> s.kind().equals("TABLE") && s.title().equals("shop_product")));

        // function view that goes through a service to several tables
        EndpointInfo co = ep("GET", "/api/checkout/").orElseThrow();
        Flow cf = k.flows().endpointFlow(co.id());
        assertTrue(cf.steps().stream().anyMatch(s -> s.kind().equals("TABLE") && s.title().equals("shop_order")), cf.steps().toString());
    }
}
