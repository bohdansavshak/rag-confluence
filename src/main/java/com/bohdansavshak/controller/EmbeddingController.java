package com.bohdansavshak.controller;

import com.bohdansavshak.service.EmbeddingOrchestrationService;
import com.bohdansavshak.service.EmbeddingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/embeddings")
public class EmbeddingController {
    private static final Logger logger = LoggerFactory.getLogger(EmbeddingController.class);
    
    private final EmbeddingOrchestrationService orchestrationService;
    private final EmbeddingService embeddingService;

    public EmbeddingController(EmbeddingOrchestrationService orchestrationService, EmbeddingService embeddingService) {
        this.orchestrationService = orchestrationService;
        this.embeddingService = embeddingService;
    }

    @PostMapping("/process-all")
    public ResponseEntity<Map<String, String>> processAllPages() {
        try {
            logger.info("Manual trigger: Processing all Confluence pages");
            // Run in a separate thread to avoid blocking the HTTP request
            new Thread(orchestrationService::processAllPages).start();
            
            return ResponseEntity.ok(Map.of(
                "status", "started",
                "message", "Confluence page processing started in background"
            ));
        } catch (Exception e) {
            logger.error("Error starting processing: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(Map.of(
                "status", "error",
                "message", "Failed to start processing: " + e.getMessage()
            ));
        }
    }

    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getStatus() {
        try {
            long totalDocuments = embeddingService.getDocumentCount();
            
            return ResponseEntity.ok(Map.of(
                "status", "success",
                "totalDocuments", totalDocuments,
                "message", "Current embedding status retrieved successfully"
            ));
        } catch (Exception e) {
            logger.error("Error getting status: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(Map.of(
                "status", "error",
                "message", "Failed to get status: " + e.getMessage()
            ));
        }
    }
}