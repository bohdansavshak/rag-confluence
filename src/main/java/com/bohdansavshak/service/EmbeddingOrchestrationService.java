package com.bohdansavshak.service;

import com.bohdansavshak.model.ConfluencePage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class EmbeddingOrchestrationService {
    private static final Logger logger = LoggerFactory.getLogger(EmbeddingOrchestrationService.class);

    private final ConfluenceClient confluenceClient;
    private final EmbeddingService embeddingService;

    public EmbeddingOrchestrationService(ConfluenceClient confluenceClient, EmbeddingService embeddingService) {
        this.confluenceClient = confluenceClient;
        this.embeddingService = embeddingService;
    }

    public void processAllPages() {
        logger.info("Starting Confluence content embedding process");

        int processedCount = 0;
        int errorCount = 0;

        long startTime = System.currentTimeMillis();

        try {
            List<ConfluencePage> pages = confluenceClient.getAllPages();

            for (ConfluencePage page : pages) {
                try {
                    logger.info("Processing page: {} - {}", page.getId(), page.getTitle());
                    embeddingService.processAndStoreConfluencePage(page);
                    processedCount++;

                    if (processedCount % 10 == 0) {
                        logger.info("Processed {} pages so far...", processedCount);
                    }

                    // Rate limiting to avoid overwhelming the API
                    Thread.sleep(100);

                } catch (Exception e) {
                    errorCount++;
                    logger.error("Error processing page {}: {}", page.getId(), e.getMessage(), e);
                }
            }

        } catch (Exception error) {
            logger.error("Error in processing: {}", error.getMessage(), error);
        }

        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;

        logger.info("Confluence content embedding process completed!");
        logger.info("Total pages processed: {}", processedCount);
        logger.info("Total errors: {}", errorCount);
        logger.info("Total time: {} ms ({} seconds)", duration, duration / 1000.0);
        logger.info("Total documents in database: {}", embeddingService.getDocumentCount());
    }

    public void processSpecificSpace(String spaceKey) {
        logger.info("Starting Confluence content embedding process for space: {}", spaceKey);

        int processedCount = 0;
        int errorCount = 0;

        long startTime = System.currentTimeMillis();

        try {
            List<ConfluencePage> allPages = confluenceClient.getAllPages();
            List<ConfluencePage> pages;

            if (spaceKey != null && !spaceKey.trim().isEmpty()) {
                // Filter pages for specific space
                pages = allPages.stream()
                        .filter(page -> page.getSpace() != null && spaceKey.equals(page.getSpace().getKey()))
                        .toList();
            } else {
                pages = allPages;
            }

            for (ConfluencePage page : pages) {
                try {
                    logger.debug("Processing page: {} - {}", page.getId(), page.getTitle());
                    embeddingService.processAndStoreConfluencePage(page);
                    processedCount++;

                    if (processedCount % 10 == 0) {
                        logger.info("Processed {} pages so far...", processedCount);
                    }

                    // Rate limiting to avoid overwhelming the API
                    Thread.sleep(100);

                } catch (Exception e) {
                    errorCount++;
                    logger.error("Error processing page {}: {}", page.getId(), e.getMessage(), e);
                }
            }

        } catch (Exception error) {
            logger.error("Error in processing: {}", error.getMessage(), error);
        }

        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;

        logger.info("Confluence content embedding process completed for space: {}", spaceKey);
        logger.info("Total pages processed: {}", processedCount);
        logger.info("Total errors: {}", errorCount);
        logger.info("Total time: {} ms ({} seconds)", duration, duration / 1000.0);
        logger.info("Documents in space {}: {}", spaceKey, embeddingService.getDocumentCountBySpace(spaceKey));
    }

    public void getProcessingStatus() {
        long totalDocuments = embeddingService.getDocumentCount();
        logger.info("Current status:");
        logger.info("Total documents in database: {}", totalDocuments);
    }
}
