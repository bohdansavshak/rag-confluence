package com.bohdansavshak.service;

import com.bohdansavshak.config.ConfluenceProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.document.Document;
import org.springframework.ai.template.st.StTemplateRenderer;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class RagService {
    private static final Logger logger = LoggerFactory.getLogger(RagService.class);
    private static final int DEFAULT_TOP_K = 5;
    private static final double DEFAULT_SIMILARITY_THRESHOLD = 0.5d;
    private final VectorStore vectorStore;
    private final ChatClient chatClient;
    private final ConfluenceProperties confluenceProperties;

    public RagService(VectorStore vectorStore, ChatClient.Builder chatClientBuilder, ChatMemory chatMemory, ConfluenceProperties confluenceProperties) {
        this.vectorStore = vectorStore;
        this.confluenceProperties = confluenceProperties;
        PromptTemplate customPromptTemplate = PromptTemplate.builder()
                .renderer(StTemplateRenderer.builder().startDelimiterToken('<').endDelimiterToken('>').build())
                .template("""
                        <query>
                        
                        Context information is below.
                        
                        ---------------------
                        <question_answer_context>
                        ---------------------
                        
                        Given the context information, answer the query.
                        
                        Follow these rules:
                        
                        1. If the answer is not in the context, just say that you don't know.
                        2. Avoid statements like "Based on the context..." or "The provided information...".
                        """)
                .build();

        var advisor = QuestionAnswerAdvisor.builder(vectorStore)
                .searchRequest(SearchRequest.builder().similarityThreshold(DEFAULT_SIMILARITY_THRESHOLD).topK(DEFAULT_TOP_K).build())
                .promptTemplate(customPromptTemplate)
                .build();
        this.chatClient = chatClientBuilder
                .defaultAdvisors(advisor, MessageChatMemoryAdvisor.builder(chatMemory).build())
                .build();
    }

    public String chat(String userQuestion) {
        try {
            logger.info("Processing chat question: {}", userQuestion);
            String userConversationId = "001";
            String response = chatClient.prompt()
                    .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, userConversationId))
                    .system("""
                            You are a helpful assistant for Sombra company employees that answers questions based on Confluence documentation.
                            Use the provided context from Confluence pages to answer the user's question.
                            
                            Guidelines:
                            - If the context doesn't contain enough information to answer the question, say so
                            - Be concise but comprehensive in your response
                            - Include relevant page titles or spaces when referencing information
                            - If multiple documents contain relevant information, synthesize them appropriately
                            """)
                    .user(userQuestion)
                    .call()
                    .content();

            logger.info("Successfully generated response for question: {}", userQuestion);
            return response;

        } catch (Exception e) {
            logger.error("Error processing chat question: {}", e.getMessage(), e);
            return "I'm sorry, but I encountered an error while processing your question. Please try again later.";
        }
    }

    public List<String> getRelevantDocumentTitles(String query) {
        return getRelevantDocumentTitles(query, DEFAULT_TOP_K, DEFAULT_SIMILARITY_THRESHOLD);
    }

    public List<String> getRelevantDocumentTitles(String query, int topK, double similarityThreshold) {
        try {
            var documents = vectorStore.similaritySearch(
                    SearchRequest.builder()
                            .query(query)
                            .topK(topK)
                            .similarityThreshold(similarityThreshold).build());

            return documents.stream()
                    .map(doc -> doc.getMetadata().getOrDefault("title", "Unknown").toString())
                    .collect(Collectors.toList());
        } catch (Exception e) {
            logger.error("Error retrieving document titles: {}", e.getMessage(), e);
            return List.of();
        }
    }

    public ChatWithSourcesResponse chatWithSources(String userQuestion) {
        try {
            logger.info("Processing chat question with sources: {}", userQuestion);

            // Get relevant documents first
            var documents = vectorStore.similaritySearch(
                    SearchRequest.builder()
                            .query(userQuestion)
                            .topK(DEFAULT_TOP_K)
                            .similarityThreshold(DEFAULT_SIMILARITY_THRESHOLD).build());

            // Extract source pages information
            List<SourcePage> sourcePages = documents.stream()
                    .map(this::createSourcePage)
                    .collect(Collectors.toList());

            // Generate the answer using the existing chat method
            String answer = chat(userQuestion);

            return new ChatWithSourcesResponse(answer, sourcePages);

        } catch (Exception e) {
            logger.error("Error processing chat question with sources: {}", e.getMessage(), e);
            return new ChatWithSourcesResponse(
                    "I'm sorry, but I encountered an error while processing your question. Please try again later.",
                    List.of()
            );
        }
    }

    public Flux<ServerSentEvent<Object>> chatWithSourcesStream(String userQuestion) {
        return Flux.create(sink -> {
            try {
                logger.info("Processing streaming chat question with sources: {}", userQuestion);

                // Get relevant documents first
                var documents = vectorStore.similaritySearch(
                        SearchRequest.builder()
                                .query(userQuestion)
                                .topK(DEFAULT_TOP_K)
                                .similarityThreshold(DEFAULT_SIMILARITY_THRESHOLD).build());

                // Extract source pages information
                List<SourcePage> sourcePages = documents.stream()
                        .map(this::createSourcePage)
                        .collect(Collectors.toList());

                // Send source pages first
                sink.next(ServerSentEvent.builder()
                        .event("sources")
                        .data(Map.of("sourcePages", sourcePages))
                        .build());

                // Generate streaming response
                String userConversationId = "001";
                StringBuilder fullResponse = new StringBuilder();

                chatClient.prompt()
                        .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, userConversationId))
                        .system("""
                                You are a helpful assistant for Sombra company employees that answers questions based on Confluence documentation.
                                Use the provided context from Confluence pages to answer the user's question.
                                
                                Guidelines:
                                - If the context doesn't contain enough information to answer the question, say so
                                - Be concise but comprehensive in your response
                                - Include relevant page titles or spaces when referencing information
                                - If multiple documents contain relevant information, synthesize them appropriately
                                """)
                        .user(userQuestion)
                        .stream()
                        .content()
                        .doOnNext(content -> {
                            if (content != null && !content.isEmpty()) {
                                fullResponse.append(content);
                                // Send each chunk as it arrives
                                sink.next(ServerSentEvent.builder()
                                        .event("chunk")
                                        .data(Map.of("content", content))
                                        .build());
                            }
                        })
                        .doOnComplete(() -> {
                            // Send completion event
                            sink.next(ServerSentEvent.builder()
                                    .event("complete")
                                    .data(Map.of("fullResponse", fullResponse.toString()))
                                    .build());
                            sink.complete();
                            logger.info("Successfully completed streaming response for question: {}", userQuestion);
                        })
                        .doOnError(error -> {
                            logger.error("Error in streaming response: {}", error.getMessage(), error);
                            sink.next(ServerSentEvent.builder()
                                    .event("error")
                                    .data(Map.of("message", "Error generating response: " + error.getMessage()))
                                    .build());
                            sink.error(error);
                        })
                        .subscribe();

            } catch (Exception e) {
                logger.error("Error processing streaming chat question with sources: {}", e.getMessage(), e);
                sink.next(ServerSentEvent.builder()
                        .event("error")
                        .data(Map.of("message", "I'm sorry, but I encountered an error while processing your question. Please try again later."))
                        .build());
                sink.error(e);
            }
        });
    }

    private SourcePage createSourcePage(Document document) {
        String pageId = document.getMetadata().getOrDefault("id", "").toString();
        String title = document.getMetadata().getOrDefault("title", "Unknown").toString();
        String spaceKey = document.getMetadata().getOrDefault("spaceKey", "").toString();
        String spaceName = document.getMetadata().getOrDefault("spaceName", "").toString();

        // Construct Confluence URL
        String url = "";
        if (confluenceProperties.getBaseUrl() != null && !pageId.isEmpty()) {
            url = confluenceProperties.getBaseUrl() + "/pages/viewpage.action?pageId=" + pageId;
        }

        return new SourcePage(pageId, title, spaceKey, spaceName, url);
    }

    // Response DTO classes
    public static class ChatWithSourcesResponse {
        private final String answer;
        private final List<SourcePage> sourcePages;

        public ChatWithSourcesResponse(String answer, List<SourcePage> sourcePages) {
            this.answer = answer;
            this.sourcePages = sourcePages;
        }

        public String getAnswer() {
            return answer;
        }

        public List<SourcePage> getSourcePages() {
            return sourcePages;
        }
    }

    public static class SourcePage {
        private final String pageId;
        private final String title;
        private final String spaceKey;
        private final String spaceName;
        private final String url;

        public SourcePage(String pageId, String title, String spaceKey, String spaceName, String url) {
            this.pageId = pageId;
            this.title = title;
            this.spaceKey = spaceKey;
            this.spaceName = spaceName;
            this.url = url;
        }

        public String getPageId() {
            return pageId;
        }

        public String getTitle() {
            return title;
        }

        public String getSpaceKey() {
            return spaceKey;
        }

        public String getSpaceName() {
            return spaceName;
        }

        public String getUrl() {
            return url;
        }
    }
}