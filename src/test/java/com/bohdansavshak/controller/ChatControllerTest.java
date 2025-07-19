package com.bohdansavshak.controller;

import com.bohdansavshak.service.RagService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.mockito.junit.jupiter.MockitoSettings;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Arrays;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ChatController.class)
class ChatControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RagService ragService;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void testHealthEndpoint() throws Exception {
        mockMvc.perform(get("/api/chat/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("healthy"))
                .andExpect(jsonPath("$.service").value("chat"))
                .andExpect(jsonPath("$.message").value("Chat service is running"));
    }

    @Test
    void testAskQuestion() throws Exception {
        // Mock the service response with sources
        List<RagService.SourcePage> mockSourcePages = Arrays.asList(
            new RagService.SourcePage("123", "Test Page 1", "TEST", "Test Space", "http://confluence.example.com/pages/viewpage.action?pageId=123"),
            new RagService.SourcePage("456", "Test Page 2", "TEST", "Test Space", "http://confluence.example.com/pages/viewpage.action?pageId=456")
        );
        RagService.ChatWithSourcesResponse mockResponse = new RagService.ChatWithSourcesResponse(
            "This is a test response from RAG service.", 
            mockSourcePages
        );
        when(ragService.chatWithSources(anyString())).thenReturn(mockResponse);

        ChatController.ChatRequest request = new ChatController.ChatRequest();
        request.setQuestion("What is Confluence?");

        mockMvc.perform(post("/api/chat/ask")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.question").value("What is Confluence?"))
                .andExpect(jsonPath("$.answer").value("This is a test response from RAG service."))
                .andExpect(jsonPath("$.sourcePages").isArray())
                .andExpect(jsonPath("$.sourcePages[0].title").value("Test Page 1"))
                .andExpect(jsonPath("$.sourcePages[0].url").value("http://confluence.example.com/pages/viewpage.action?pageId=123"));
    }

    @Test
    void testAskQuestionWithEmptyQuestion() throws Exception {
        ChatController.ChatRequest request = new ChatController.ChatRequest();
        request.setQuestion("");

        mockMvc.perform(post("/api/chat/ask")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("error"))
                .andExpect(jsonPath("$.message").value("Question cannot be empty"));
    }

    @Test
    void testGetRelevantDocuments() throws Exception {
        // Mock the service response
        List<String> mockDocuments = Arrays.asList("Document 1", "Document 2", "Document 3");
        when(ragService.getRelevantDocumentTitles(anyString())).thenReturn(mockDocuments);

        ChatController.ChatRequest request = new ChatController.ChatRequest();
        request.setQuestion("How to use Confluence?");

        mockMvc.perform(post("/api/chat/relevant-docs")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.query").value("How to use Confluence?"))
                .andExpect(jsonPath("$.relevantDocuments").isArray())
                .andExpect(jsonPath("$.relevantDocuments[0]").value("Document 1"))
                .andExpect(jsonPath("$.relevantDocuments[1]").value("Document 2"))
                .andExpect(jsonPath("$.relevantDocuments[2]").value("Document 3"));
    }

    @Test
    void testGetRelevantDocumentsWithEmptyQuery() throws Exception {
        ChatController.ChatRequest request = new ChatController.ChatRequest();
        request.setQuestion("");

        mockMvc.perform(post("/api/chat/relevant-docs")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("error"))
                .andExpect(jsonPath("$.message").value("Query cannot be empty"));
    }
}