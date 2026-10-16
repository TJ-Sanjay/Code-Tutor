package com.codetutor.backend.project;

import com.codetutor.backend.auth.model.User;
import com.codetutor.backend.auth.repository.UserRepository;
import com.codetutor.backend.project.model.Project;
import com.codetutor.backend.project.repository.ProjectRepository;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The core privacy requirement: every account has its own workspace. A user must never be able to read, change or
 * even detect another user's projects, files, analysis or chat history.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:isolation;DB_CLOSE_DELAY=-1",
        "app.data-dir=target/test-data",
        "workspace.root-dir=target/test-data/workspaces"
})
@AutoConfigureMockMvc
class UserIsolationTest {

    @Autowired MockMvc mvc;
    @Autowired ProjectRepository projects;
    @Autowired UserRepository users;

    String aliceToken;
    String bobToken;
    long alicesProject;

    @BeforeEach
    void setUp() throws Exception {
        String suffix = Long.toString(System.nanoTime());
        aliceToken = register("alice" + suffix);
        bobToken = register("bob" + suffix);
        User alice = users.findByUsername("alice" + suffix).orElseThrow();
        Project p = Project.builder().name("Alice's secret project").sourceType(Project.SourceType.GITHUB)
                .githubUrl("https://github.com/x/y").repositoryName("x/y").owner(alice).build();
        p.setStatus(Project.AnalysisStatus.COMPLETED);
        alicesProject = projects.save(p).getId();
    }

    private String register(String username) throws Exception {
        String body = "{\"username\":\"" + username + "\",\"email\":\"" + username + "@test.dev\",\"password\":\"Passw0rd!\",\"fullName\":\"" + username + "\"}";
        String res = mvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(res, "$.data.accessToken");
    }

    private ResultActions as(String token, org.springframework.test.web.servlet.RequestBuilder b) throws Exception {
        return mvc.perform(b);
    }

    @Test
    void owner_can_see_own_project_but_other_user_cannot() throws Exception {
        mvc.perform(get("/projects/" + alicesProject).header("Authorization", "Bearer " + aliceToken)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Alice's secret project"));
        mvc.perform(get("/projects/" + alicesProject).header("Authorization", "Bearer " + bobToken)).andExpect(status().isNotFound());
    }

    @Test
    void project_list_only_contains_own_projects() throws Exception {
        mvc.perform(get("/projects").header("Authorization", "Bearer " + aliceToken)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value((int) alicesProject));
        mvc.perform(get("/projects").header("Authorization", "Bearer " + bobToken)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    void every_project_endpoint_hides_other_users_projects() throws Exception {
        String h = "Bearer " + bobToken;
        String base = "/projects/" + alicesProject;
        for (String path : new String[]{"", "/status", "/overview", "/graph", "/endpoints", "/schema", "/classes", "/files",
                "/file?path=pom.xml", "/semantic?path=A.java", "/explain?kind=file&id=pom.xml", "/symbols?q=user", "/dependencies",
                "/chat/history", "/chat/suggestions", "/flow?endpoint=security"}) {
            mvc.perform(get(base + path).header("Authorization", h)).andExpect(status().isNotFound());
        }
        mvc.perform(post(base + "/chat").header("Authorization", h).contentType(MediaType.APPLICATION_JSON).content("{\"query\":\"hi\"}"))
                .andExpect(status().isNotFound());
        mvc.perform(post(base + "/reanalyze").header("Authorization", h)).andExpect(status().isNotFound());
        mvc.perform(delete(base).header("Authorization", h)).andExpect(status().isNotFound());
        // …and the project is still there for its owner
        mvc.perform(get(base).header("Authorization", "Bearer " + aliceToken)).andExpect(status().isOk());
    }

    @Test
    void requests_without_a_valid_token_are_rejected() throws Exception {
        mvc.perform(get("/projects")).andExpect(status().isUnauthorized());
        mvc.perform(get("/projects").header("Authorization", "Bearer not.a.token")).andExpect(status().isUnauthorized());
        mvc.perform(get("/auth/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void two_accounts_cannot_share_an_email() throws Exception {
        String body = "{\"username\":\"someoneelse\",\"email\":\"" + users.findAll().get(0).getEmail() + "\",\"password\":\"Passw0rd!\",\"fullName\":\"X\"}";
        mvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isBadRequest());
    }
}
