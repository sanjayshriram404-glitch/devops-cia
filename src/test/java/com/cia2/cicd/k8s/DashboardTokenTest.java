package com.cia2.cicd.k8s;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/** With DASHBOARD_TOKEN set, changes need the X-Dashboard-Token header. */
@SpringBootTest(properties = {"dashboard.k8s.enabled=false", "dashboard.token=s3cret"})
@AutoConfigureMockMvc
class DashboardTokenTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void missingTokenIsUnauthorized() throws Exception {
        mvc.perform(post("/api/k8s/restart"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void wrongTokenIsUnauthorized() throws Exception {
        mvc.perform(post("/api/k8s/scale").header("X-Dashboard-Token", "nope")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"replicas\": 2}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void correctTokenPassesTheCheck() throws Exception {
        // Gets past the token check, then fails only because there is no cluster.
        mvc.perform(post("/api/k8s/restart").header("X-Dashboard-Token", "s3cret"))
                .andExpect(status().isServiceUnavailable());
    }
}
