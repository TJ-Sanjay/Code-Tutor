package com.codetutor.backend.auth;

import com.codetutor.backend.auth.service.GoogleTokenVerifier;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** "Continue with Google": first use creates the account, later uses sign into the same one, bad tokens are refused. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:googlelogin;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "app.data-dir=target/test-data",
        "workspace.root-dir=target/test-data/workspaces"
})
@AutoConfigureMockMvc
class GoogleLoginTest {

    /** Stands in for Google: knows a few tokens, so the test needs no internet and no client id. */
    static class FakeGoogle extends GoogleTokenVerifier {
        final Map<String, Profile> tokens = new ConcurrentHashMap<>();

        FakeGoogle() { super("test-client.apps.googleusercontent.com", new ObjectMapper()); }

        @Override public Optional<Profile> verify(String idToken) { return Optional.ofNullable(tokens.get(idToken)); }
    }

    @TestConfiguration
    static class Config {
        @Bean @Primary FakeGoogle fakeGoogle() { return new FakeGoogle(); }
    }

    @Autowired MockMvc mvc;
    @Autowired FakeGoogle google;

    private String loginWithGoogle(String credential) throws Exception {
        return mvc.perform(post("/auth/google").contentType(MediaType.APPLICATION_JSON).content("{\"credential\":\"" + credential + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    @Test
    void newPersonGetsAnAccountAndCanComeBack() throws Exception {
        String email = "grace" + System.nanoTime() + "@gmail.com";
        google.tokens.put("good-token", new GoogleTokenVerifier.Profile(email, "Grace Hopper", "https://pic/x.png"));

        String first = loginWithGoogle("good-token");
        String token = JsonPath.read(first, "$.data.accessToken");
        assertEquals(email, JsonPath.read(first, "$.data.email"));
        assertEquals("Grace Hopper", JsonPath.read(first, "$.data.fullName"));

        // the token works on a protected endpoint
        mvc.perform(get("/auth/me").header("Authorization", "Bearer " + token)).andExpect(status().isOk());

        // second visit → same account (same user id), not a duplicate
        String second = loginWithGoogle("good-token");
        assertEquals((Integer) JsonPath.read(first, "$.data.userId"), (Integer) JsonPath.read(second, "$.data.userId"));
    }

    @Test
    void twoGoogleUsersWithTheSameNameGetDifferentUsernames() throws Exception {
        long n = System.nanoTime();
        google.tokens.put("t1" + n, new GoogleTokenVerifier.Profile("sam" + n + "@gmail.com", "Sam", ""));
        google.tokens.put("t2" + n, new GoogleTokenVerifier.Profile("sam" + n + "@yahoo.com", "Sam Two", ""));
        String a = loginWithGoogle("t1" + n);
        String b = loginWithGoogle("t2" + n);
        // both addresses start with the same text, but usernames must stay unique
        assertEquals(false, JsonPath.read(a, "$.data.username").equals(JsonPath.read(b, "$.data.username")));
    }

    @Test
    void invalidTokenIsRefused() throws Exception {
        mvc.perform(post("/auth/google").contentType(MediaType.APPLICATION_JSON).content("{\"credential\":\"forged\"}"))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void configEndpointIsPublicAndTellsTheFrontEndTheClientId() throws Exception {
        String res = mvc.perform(get("/auth/config")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertEquals("test-client.apps.googleusercontent.com", JsonPath.read(res, "$.data.googleClientId"));
    }
}
