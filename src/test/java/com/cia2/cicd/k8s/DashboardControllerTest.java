package com.cia2.cicd.k8s;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/** Outside a cluster the dashboard must report "not connected" and refuse changes. */
@SpringBootTest(properties = "dashboard.k8s.enabled=false")
@AutoConfigureMockMvc
class DashboardControllerTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void statusReportsDisconnectedOutsideCluster() throws Exception {
        mvc.perform(get("/api/k8s/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.connected").value(false))
                .andExpect(jsonPath("$.maxReplicas").value(5))
                .andExpect(jsonPath("$.pods").isEmpty());
    }

    @Test
    void scaleOutOfRangeIsRejected() throws Exception {
        mvc.perform(post("/api/k8s/scale").contentType(MediaType.APPLICATION_JSON).content("{\"replicas\": 9}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Replicas must be between 1 and 5."));
    }

    @Test
    void scaleWithoutClusterIsUnavailable() throws Exception {
        mvc.perform(post("/api/k8s/scale").contentType(MediaType.APPLICATION_JSON).content("{\"replicas\": 3}"))
                .andExpect(status().isServiceUnavailable());
    }

    @Test
    void scaleWithoutBodyFieldIsRejected() throws Exception {
        mvc.perform(post("/api/k8s/scale").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }
}
