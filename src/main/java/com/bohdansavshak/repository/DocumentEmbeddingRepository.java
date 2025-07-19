package com.bohdansavshak.repository;

import com.bohdansavshak.entity.DocumentEmbedding;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DocumentEmbeddingRepository extends JpaRepository<DocumentEmbedding, Long> {
    
    Optional<DocumentEmbedding> findByConfluencePageId(String confluencePageId);
    
    List<DocumentEmbedding> findBySpaceKey(String spaceKey);
    
    @Query("SELECT d FROM DocumentEmbedding d WHERE d.spaceKey IN :spaceKeys")
    List<DocumentEmbedding> findBySpaceKeyIn(@Param("spaceKeys") List<String> spaceKeys);
    
    boolean existsByConfluencePageId(String confluencePageId);
    
    void deleteByConfluencePageId(String confluencePageId);
    
    @Query("SELECT COUNT(d) FROM DocumentEmbedding d")
    long countAllDocuments();
    
    @Query("SELECT COUNT(d) FROM DocumentEmbedding d WHERE d.spaceKey = :spaceKey")
    long countBySpaceKey(@Param("spaceKey") String spaceKey);
}