package com.example.daoyuanagent.integration;

import com.example.daoyuanagent.api.dto.ChatRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class ChatApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void testChatEndpoint() throws Exception {
        ChatRequest req = new ChatRequest();
        req.setMessage("奖学金怎么申请？");
        mockMvc.perform(post("/api/v1/chat")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer").exists())
                .andExpect(jsonPath("$.conversation_id").exists())
                .andExpect(jsonPath("$.request_id").exists())
                .andExpect(jsonPath("$.risk_level").exists());
    }

    @Test
    void testEmptyMessage() throws Exception {
        ChatRequest req = new ChatRequest();
        req.setMessage("");
        mockMvc.perform(post("/api/v1/chat")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testHealthEndpoint() throws Exception {
        mockMvc.perform(post("/api/v1/chat")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"message\":\"test\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void testRetrievalSearch() throws Exception {
        mockMvc.perform(post("/api/v1/retrieval/search")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"query\":\"奖学金\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void testFeedbackSubmission() throws Exception {
        mockMvc.perform(post("/api/v1/feedback")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"message_id\":\"test-123\",\"feedback_type\":\"HELPFUL\"}"))
                .andExpect(status().isOk());
    }
}
