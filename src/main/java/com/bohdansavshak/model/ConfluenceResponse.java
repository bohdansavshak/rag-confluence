package com.bohdansavshak.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class ConfluenceResponse {
    private List<ConfluencePage> results;
    private int start;
    private int limit;
    private int size;

    public List<ConfluencePage> getResults() {
        return results;
    }

    public void setResults(List<ConfluencePage> results) {
        this.results = results;
    }

    public int getStart() {
        return start;
    }

    public void setStart(int start) {
        this.start = start;
    }

    public int getLimit() {
        return limit;
    }

    public void setLimit(int limit) {
        this.limit = limit;
    }

    public int getSize() {
        return size;
    }

    public void setSize(int size) {
        this.size = size;
    }
}