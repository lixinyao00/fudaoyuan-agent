package com.example.daoyuanagent.safety;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class SecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void testAdminEndpointRequiresAuth() throws Exception {
        mockMvc.perform(get("/api/v1/admin/chunks"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void testAdminFeedbackRequiresAuth() throws Exception {
        mockMvc.perform(get("/api/v1/admin/feedback"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void testAdminRisksRequiresAuth() throws Exception {
        mockMvc.perform(get("/api/v1/admin/risks"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void testAdminAuditRequiresAuth() throws Exception {
        mockMvc.perform(get("/api/v1/admin/audit"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void testHealthEndpointPublic() throws Exception {
        mockMvc.perform(get("/api/v1/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void testErrorResponseNoStack() throws Exception {
        mockMvc.perform(post("/api/v1/chat")
                .contentType("application/json")
                .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").exists())
                .andExpect(jsonPath("$.message").exists());
    }
}
