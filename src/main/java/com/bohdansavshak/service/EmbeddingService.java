package com.bohdansavshak.service;

import com.bohdansavshak.entity.DocumentEmbedding;
import com.bohdansavshak.model.ConfluencePage;
import com.bohdansavshak.repository.DocumentEmbeddingRepository;
import org.jsoup.Jsoup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@Transactional
public class EmbeddingService {
    private static final Logger logger = LoggerFactory.getLogger(EmbeddingService.class);

    private final DocumentEmbeddingRepository documentRepository;
    private final EmbeddingModel embeddingModel;
    private final VectorStore vectorStore;

    public EmbeddingService(DocumentEmbeddingRepository documentRepository,
                            EmbeddingModel embeddingModel,
                            VectorStore vectorStore) {
        this.documentRepository = documentRepository;
        this.embeddingModel = embeddingModel;
        this.vectorStore = vectorStore;
    }

    public void processAndStoreConfluencePage(ConfluencePage confluencePage) {
        try {
            String pageId = confluencePage.getId();
            String title = confluencePage.getTitle();
            String spaceKey = confluencePage.getSpace() != null ? confluencePage.getSpace().getKey() : "UNKNOWN";
            String spaceName = confluencePage.getSpace() != null ? confluencePage.getSpace().getName() : "Unknown Space";

            // Extract and clean content
            String content = extractTextContent(confluencePage);
            if (content == null || content.trim().isEmpty()) {
                logger.warn("No content found for page: {} - {}", pageId, title);
                return;
            }

            // Check if page already exists
            Optional<DocumentEmbedding> existingDoc = documentRepository.findByConfluencePageId(pageId);
            if (existingDoc.isPresent()) {
                logger.info("Page already exists, updating: {} - {}", pageId, title);
                updateExistingDocument(existingDoc.get(), content, title, spaceKey, spaceName);
            } else {
                logger.info("Processing new page: {} - {}", pageId, title);
                createNewDocument(pageId, title, content, spaceKey, spaceName);
            }

        } catch (Exception e) {
            logger.error("Error processing Confluence page {}: {}", confluencePage.getId(), e.getMessage(), e);
        }
    }

    private String extractTextContent(ConfluencePage confluencePage) {
        if (confluencePage.getBody() == null ||
                confluencePage.getBody().getStorage() == null ||
                confluencePage.getBody().getStorage().getValue() == null) {
            return null;
        }

        String htmlContent = confluencePage.getBody().getStorage().getValue();

        // Use Jsoup to extract plain text from HTML
        String plainText = Jsoup.parse(htmlContent).text();

        // Add title to the content for better context
        String title = confluencePage.getTitle();
        return title + "\n\n" + plainText;
    }

    private void createNewDocument(String pageId, String title, String content, String spaceKey, String spaceName) {
        // Create Spring AI Document
        Document document = new Document(content, Map.of(
                "id", pageId,
                "title", title,
                "spaceKey", spaceKey,
                "spaceName", spaceName,
                "type", "confluence-page"
        ));

        // Store in vector store (this will generate embeddings)
        vectorStore.add(List.of(document));

        // Create and save entity
        DocumentEmbedding documentEmbedding = DocumentEmbedding.fromDocument(document, pageId, title, spaceKey, spaceName);
        documentRepository.save(documentEmbedding);

        logger.info("Successfully stored new document: {} - {}", pageId, title);
    }

    private void updateExistingDocument(DocumentEmbedding existingDoc, String content, String title, String spaceKey, String spaceName) {
        // Update the existing document
        existingDoc.setTitle(title);
        existingDoc.setContent(content);
        existingDoc.setSpaceKey(spaceKey);
        existingDoc.setSpaceName(spaceName);

        // Create updated Spring AI Document
        Document document = new Document(content, Map.of(
                "id", existingDoc.getConfluencePageId(),
                "title", title,
                "spaceKey", spaceKey,
                "spaceName", spaceName,
                "type", "confluence-page"
        ));

        // Update in vector store
        vectorStore.delete(List.of(existingDoc.getConfluencePageId()));
        vectorStore.add(List.of(document));

        // Save updated entity
        documentRepository.save(existingDoc);

        logger.info("Successfully updated document: {} - {}", existingDoc.getConfluencePageId(), title);
    }

    public long getDocumentCount() {
        return documentRepository.countAllDocuments();
    }

    public long getDocumentCountBySpace(String spaceKey) {
        return documentRepository.countBySpaceKey(spaceKey);
    }

    public List<DocumentEmbedding> getDocumentsBySpace(String spaceKey) {
        return documentRepository.findBySpaceKey(spaceKey);
    }

    public void deleteDocument(String confluencePageId) {
        vectorStore.delete(List.of(confluencePageId));
        documentRepository.deleteByConfluencePageId(confluencePageId);
        logger.info("Deleted document: {}", confluencePageId);
    }
}