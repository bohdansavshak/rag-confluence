package com.bohdansavshak.entity;

import jakarta.persistence.*;
import org.springframework.ai.document.Document;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Entity
@Table(name = "document_embeddings")
public class DocumentEmbedding {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "confluence_page_id", unique = true, nullable = false)
    private String confluencePageId;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "content", columnDefinition = "TEXT")
    private String content;

    @Column(name = "space_key")
    private String spaceKey;

    @Column(name = "space_name")
    private String spaceName;

    @Column(name = "embedding", columnDefinition = "vector(768)")
    private List<Float> embedding;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public Document toDocument() {
        return new Document(content, Map.of(
            "id", confluencePageId,
            "title", title,
            "spaceKey", spaceKey,
            "spaceName", spaceName,
            "createdAt", createdAt.toString(),
            "updatedAt", updatedAt.toString()
        ));
    }

    public static DocumentEmbedding fromDocument(Document document, String confluencePageId, String title, String spaceKey, String spaceName) {
        DocumentEmbedding embedding = new DocumentEmbedding();
        embedding.setConfluencePageId(confluencePageId);
        embedding.setTitle(title);
        embedding.setContent(document.getText());
        embedding.setSpaceKey(spaceKey);
        embedding.setSpaceName(spaceName);
        return embedding;
    }

    // Getters and setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getConfluencePageId() {
        return confluencePageId;
    }

    public void setConfluencePageId(String confluencePageId) {
        this.confluencePageId = confluencePageId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getSpaceKey() {
        return spaceKey;
    }

    public void setSpaceKey(String spaceKey) {
        this.spaceKey = spaceKey;
    }

    public String getSpaceName() {
        return spaceName;
    }

    public void setSpaceName(String spaceName) {
        this.spaceName = spaceName;
    }

    public List<Float> getEmbedding() {
        return embedding;
    }

    public void setEmbedding(List<Float> embedding) {
        this.embedding = embedding;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
