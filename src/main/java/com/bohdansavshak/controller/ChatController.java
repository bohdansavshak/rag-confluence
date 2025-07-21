package com.bohdansavshak.controller;

import com.bohdansavshak.service.RagService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.ModelAndView;
import reactor.core.publisher.Flux;
import org.springframework.http.codec.ServerSentEvent;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/chat")
public class ChatController {
    private static final Logger logger = LoggerFactory.getLogger(ChatController.class);
    
    private final RagService ragService;

    public ChatController(RagService ragService) {
        this.ragService = ragService;
    }

    @PostMapping("/ask")
    public ResponseEntity<Map<String, Object>> askQuestion(@RequestBody ChatRequest request) {
        try {
            logger.info("Received chat question: {}", request.getQuestion());
            
            if (request.getQuestion() == null || request.getQuestion().trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of(
                    "status", "error",
                    "message", "Question cannot be empty"
                ));
            }
            
            var response = ragService.chatWithSources(request.getQuestion());
            
            return ResponseEntity.ok(Map.of(
                "status", "success",
                "question", request.getQuestion(),
                "answer", response.getAnswer(),
                "sourcePages", response.getSourcePages()
            ));
            
        } catch (Exception e) {
            logger.error("Error processing chat question: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(Map.of(
                "status", "error",
                "message", "Failed to process question: " + e.getMessage()
            ));
        }
    }

    @GetMapping(value = "/ask-stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<Object>> askQuestionStream(@RequestParam String question) {
        try {
            logger.info("Received streaming chat question: {}", question);
            
            if (question == null || question.trim().isEmpty()) {
                return Flux.just(ServerSentEvent.builder()
                        .event("error")
                        .data(Map.of("message", "Question cannot be empty"))
                        .build());
            }
            
            // Process streaming response using reactive approach
            return ragService.chatWithSourcesStream(question)
                    .onErrorResume(error -> {
                        logger.error("Error processing streaming chat question: {}", error.getMessage(), error);
                        return Flux.just(ServerSentEvent.builder()
                                .event("error")
                                .data(Map.of("message", "Failed to process question: " + error.getMessage()))
                                .build());
                    });
            
        } catch (Exception e) {
            logger.error("Error setting up streaming chat question: {}", e.getMessage(), e);
            return Flux.just(ServerSentEvent.builder()
                    .event("error")
                    .data(Map.of("message", "Failed to process question: " + e.getMessage()))
                    .build());
        }
    }

    @PostMapping("/relevant-docs")
    public ResponseEntity<Map<String, Object>> getRelevantDocuments(@RequestBody ChatRequest request) {
        try {
            logger.info("Getting relevant documents for query: {}", request.getQuestion());
            
            if (request.getQuestion() == null || request.getQuestion().trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of(
                    "status", "error",
                    "message", "Query cannot be empty"
                ));
            }
            
            List<String> relevantTitles = ragService.getRelevantDocumentTitles(request.getQuestion());
            
            return ResponseEntity.ok(Map.of(
                "status", "success",
                "query", request.getQuestion(),
                "relevantDocuments", relevantTitles
            ));
            
        } catch (Exception e) {
            logger.error("Error getting relevant documents: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(Map.of(
                "status", "error",
                "message", "Failed to get relevant documents: " + e.getMessage()
            ));
        }
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of(
            "status", "healthy",
            "service", "chat",
            "message", "Chat service is running"
        ));
    }

    // Request DTOs
    public static class ChatRequest {
        private String question;

        public String getQuestion() {
            return question;
        }

        public void setQuestion(String question) {
            this.question = question;
        }
    }

    public static class AdvancedChatRequest {
        private String question;
        private Integer topK;
        private Double similarityThreshold;

        public String getQuestion() {
            return question;
        }

        public void setQuestion(String question) {
            this.question = question;
        }

        public Integer getTopK() {
            return topK;
        }

        public void setTopK(Integer topK) {
            this.topK = topK;
        }

        public Double getSimilarityThreshold() {
            return similarityThreshold;
        }

        public void setSimilarityThreshold(Double similarityThreshold) {
            this.similarityThreshold = similarityThreshold;
        }
    }
}