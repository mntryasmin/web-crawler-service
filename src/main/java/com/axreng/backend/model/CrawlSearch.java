package com.axreng.backend.model;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import com.axreng.backend.configuration.ApplicationProperties;

public class CrawlSearch {
    private static final Logger logger = LoggerFactory.getLogger(CrawlSearch.class);
    private final String id;
    private final String keyword;
    private final List<String> urls;
    private SearchStatus status;
    private long lastUpdateTime;

    public CrawlSearch(String id, String keyword) {
        this.id = id;
        this.keyword = keyword;
        this.urls = new CopyOnWriteArrayList<>();
        this.status = SearchStatus.ACTIVE;
        this.lastUpdateTime = System.currentTimeMillis();
    }

    public String getId() {
        return id;
    }

    public String getKeyword() {
        return keyword;
    }

    public List<String> getUrls() {
        if (!ApplicationProperties.getInstance().isLimitResults()) {
            return urls;
        }
        int maxResults = ApplicationProperties.getInstance().getCrawlerMaxResults();
        return urls.size() <= maxResults ? urls : urls.subList(0, maxResults);
    }

    public synchronized SearchStatus getStatus() {
        return status;
    }

    public synchronized void setStatus(SearchStatus status) {
        // Update status immediately; caller controls when to mark DONE
        this.status = status;
        this.lastUpdateTime = System.currentTimeMillis();
        logger.debug("Search {} status set to {}", id, status);
    }

    public synchronized void addUrl(String url) {
        if (!urls.contains(url)) {
            urls.add(url);
            this.lastUpdateTime = System.currentTimeMillis();
        }
    }

    public synchronized long getLastUpdateTime() {
        return lastUpdateTime;
    }
}