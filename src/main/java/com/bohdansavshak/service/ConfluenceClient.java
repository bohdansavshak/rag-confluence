package com.bohdansavshak.service;

import com.bohdansavshak.config.ConfluenceProperties;
import com.bohdansavshak.model.ConfluencePage;
import com.bohdansavshak.model.ConfluenceResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

@Service
public class ConfluenceClient {
    private static final Logger logger = LoggerFactory.getLogger(ConfluenceClient.class);
    private final RestTemplate restTemplate;
    private final ConfluenceProperties confluenceProperties;
    private final HttpEntity<String> httpEntity;

    public ConfluenceClient(ConfluenceProperties confluenceProperties) {
        this.confluenceProperties = confluenceProperties;
        this.restTemplate = new RestTemplate();

        String auth = confluenceProperties.getUsername() + ":" + confluenceProperties.getPassword();
        String encodedAuth = Base64.getEncoder().encodeToString(auth.getBytes(StandardCharsets.UTF_8));

        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.AUTHORIZATION, "Basic " + encodedAuth);
        headers.set(HttpHeaders.CONTENT_TYPE, "application/json");

        this.httpEntity = new HttpEntity<>(headers);
    }

    public List<ConfluencePage> getAllPages() {
        if (confluenceProperties.getSpaceKeys() != null && !confluenceProperties.getSpaceKeys().isEmpty()) {
            String[] spaceKeys = confluenceProperties.getSpaceKeys().split(",");
            List<ConfluencePage> allPages = new ArrayList<>();
            for (String spaceKey : spaceKeys) {
                allPages.addAll(getPagesFromSpace(spaceKey.trim()));
            }
            return allPages;
        } else {
            return getAllPagesFromAllSpaces();
        }
    }

    private List<ConfluencePage> getPagesFromSpace(String spaceKey) {
        logger.info("Fetching pages from space: {}", spaceKey);
        return fetchPagesIteratively("/rest/api/content", "space=" + spaceKey, 0);
    }

    private List<ConfluencePage> getAllPagesFromAllSpaces() {
        logger.info("Fetching pages from all spaces");
        return fetchPagesIteratively("/rest/api/content", "type=page", 0);
    }

    private List<ConfluencePage> fetchPagesIteratively(String endpoint, String query, int start) {
        List<ConfluencePage> allPages = new ArrayList<>();
        int currentStart = start;

        while (true) {
            try {
                String url = confluenceProperties.getBaseUrl() + endpoint + "?expand=body.storage,space&" + query + "&start=" + currentStart + "&limit=50";

                ResponseEntity<ConfluenceResponse> response = restTemplate.exchange(
                        url,
                        HttpMethod.GET,
                        httpEntity,
                        ConfluenceResponse.class
                );

                ConfluenceResponse confluenceResponse = response.getBody();
                if (confluenceResponse == null || confluenceResponse.getResults() == null) {
                    break;
                }

                List<ConfluencePage> pages = confluenceResponse.getResults();
                logger.debug("Fetched {} pages, start: {}", confluenceResponse.getSize(), confluenceResponse.getStart());

                allPages.addAll(pages);

                // If we got a full page of results, there might be more
                if (confluenceResponse.getSize() == 50) {
                    currentStart += 50;
                } else {
                    break;
                }

            } catch (Exception error) {
                logger.error("Error fetching pages: {}", error.getMessage());
                break;
            }
        }

        return allPages;
    }

    public ConfluencePage getPageById(String pageId) {
        try {
            String url = confluenceProperties.getBaseUrl() + "/rest/api/content/" + pageId + "?expand=body.storage,space";

            ResponseEntity<ConfluencePage> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    httpEntity,
                    ConfluencePage.class
            );

            ConfluencePage page = response.getBody();
            if (page != null) {
                logger.debug("Fetched page: {} - {}", page.getId(), page.getTitle());
            }
            return page;

        } catch (Exception error) {
            logger.error("Error fetching page {}: {}", pageId, error.getMessage());
            return null;
        }
    }
}
