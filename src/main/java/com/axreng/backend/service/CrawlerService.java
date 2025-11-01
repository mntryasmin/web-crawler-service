package com.axreng.backend.service;

import com.axreng.backend.configuration.ApplicationProperties;
import com.axreng.backend.model.CrawlSearch;
import com.axreng.backend.model.SearchStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Service responsible for web crawling operations with concurrent search capabilities.
 * 
 * Features:
 * - Concurrent web crawling with thread pool management
 * - Automatic timeout handling for long-running searches
 * - URL deduplication and cycle detection
 * - Graceful shutdown with search state preservation
 * 
 * Search Constraints:
 * - Keywords must be 4-32 characters
 * - Only crawls URLs within the configured base domain
 * - 5-second timeout per search operation
 * - Thread pool limited to 10 concurrent crawls
 *
 * Thread Safety: This class is thread-safe. All shared state is protected
 * using thread-safe collections and synchronized blocks where necessary.
 */
public class CrawlerService implements AutoCloseable {
    private static final Logger logger = LoggerFactory.getLogger(CrawlerService.class);

    private static final int MIN_THREAD_POOL_SIZE = 4;
    private static final int MAX_THREAD_POOL_SIZE = 20;
    private static final Pattern HREF_PATTERN = Pattern.compile("href=[\"'](.*?)[\"']", Pattern.CASE_INSENSITIVE);

    private final Map<String, CrawlSearch> searches;
    private final ExecutorService executorService;
    private final String baseUrl;
    private final int maxDepth;

    private static final int CONNECT_TIMEOUT = 3000; // 3 segundos
    private static final int READ_TIMEOUT = 5000;    // 5 segundos
    private static final int MAX_RETRIES = 3;

    public CrawlerService() {
        this(ApplicationProperties.getInstance());
    }

    public CrawlerService(ApplicationProperties properties) {
        if (properties == null) {
            throw new IllegalArgumentException("Properties cannot be null");
        }

        this.baseUrl = properties.getBaseUrl();
        if (this.baseUrl == null) {
            throw new IllegalStateException("Base URL not configured");
        }

        this.maxDepth = properties.getCrawlerMaxDepth();
        
        this.searches = new ConcurrentHashMap<>();
        
        this.executorService = new ThreadPoolExecutor(
            MIN_THREAD_POOL_SIZE,
            MAX_THREAD_POOL_SIZE,
            60L, TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(),
            new ThreadPoolExecutor.AbortPolicy()
        );
        
        logger.info("Crawler initialized [baseUrl={}, maxDepth={}, threadPool={}-{}]", 
            baseUrl, maxDepth, MIN_THREAD_POOL_SIZE, MAX_THREAD_POOL_SIZE);
    }

    /**
     * Performs graceful shutdown of the crawler service.
     * - Marks all active searches as complete
     * - Attempts orderly thread pool shutdown
     * - Forces shutdown if graceful attempt fails
     * - Ensures all resources are properly released
     */
    @Override
    public void close() {
        logger.info("Initiating CrawlerService shutdown sequence");

        try {
            logger.debug("Marking active searches as complete");
            for (CrawlSearch search : searches.values()) {
                synchronized (search) {
                    if (search.getStatus() == SearchStatus.ACTIVE) {
                        logger.debug("Search [ID: {}] marked as complete during shutdown", search.getId());
                        search.setStatus(SearchStatus.DONE);
                        search.notifyAll();
                    }
                }
            }

            // Attempt orderly shutdown with longer timeout
            executorService.shutdown();

            if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
                // Force shutdown if tasks don't stop
                executorService.shutdownNow();

                // Wait a bit longer for forced shutdown
                if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
                    logger.warn("ExecutorService did not terminate after forced shutdown.");
                }
            }
        } catch (InterruptedException e) {
            logger.error("ExecutorService shutdown interrupted", e);
            
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        } finally {
            // Ensure all searches are marked as done even if shutdown fails
            for (CrawlSearch search : searches.values()) {
                synchronized (search) {
                    search.setStatus(SearchStatus.DONE);
                    search.notifyAll();
                }
            }
        }
    }

    /**
     * Initiates a new web crawl search operation.
     */
    public CrawlSearch startSearch(String keyword) {
        validateKeyword(keyword);
        
        String id = generateId();
        final CrawlSearch search = new CrawlSearch(id, keyword);
        searches.put(id, search);
        
        logger.info("Starting search [id={}, keyword={}, thread={}]", id, keyword, Thread.currentThread().getName());
        
        executorService.submit(() -> {
            logger.info("Crawl execution started [id={}, thread={}]", id, Thread.currentThread().getName());
            crawl(search);
        });
        
        return search;
    }

    private void crawl(CrawlSearch search) {
        long startTime = System.currentTimeMillis();
        int processedUrls = 0;
        
        Set<String> visited = Collections.synchronizedSet(new HashSet<>());
        Queue<String> queue = new ConcurrentLinkedQueue<>();
        queue.offer(baseUrl);
        
        Set<String> pendingVisit = Collections.synchronizedSet(new HashSet<>());
        pendingVisit.add(baseUrl);
        
        try {
            search.setStatus(SearchStatus.ACTIVE);
            int consecutiveEmptyPolls = 0;
            int maxEmptyPolls = ApplicationProperties.getInstance().getCrawlerMaxEmptyPolls();
            
            while ((!pendingVisit.isEmpty() || consecutiveEmptyPolls < maxEmptyPolls) && 
                   !Thread.currentThread().isInterrupted()) {
                String url = queue.poll();
                if (url == null) {
                    if (!pendingVisit.isEmpty() || consecutiveEmptyPolls < maxEmptyPolls) {
                        Thread.sleep(100);
                        consecutiveEmptyPolls++;
                        continue;
                    }
                    break;
                }
                
                consecutiveEmptyPolls = 0;
                
                pendingVisit.remove(url);
                
                if (visited.contains(url)) {
                    continue;
                }
                
                visited.add(url);
                processedUrls++;
                
                try {
                    logger.debug("Crawling URL: {}", url);
                    String content = fetchContent(url);
                    
                    if (content != null) {
                        Pattern pattern = Pattern.compile(search.getKeyword(), Pattern.CASE_INSENSITIVE);
                        Matcher matcher = pattern.matcher(content);
                        
                        if (matcher.find()) {
                            search.addUrl(url);
                            logger.info("Found match [url={}, keyword={}]", url, search.getKeyword());

                            // Early stop when reaching max results (if limit is active)
                            if (ApplicationProperties.getInstance().isLimitResults()) {
                                int maxResults = ApplicationProperties.getInstance().getCrawlerMaxResults();
                                int current = search.getUrls().size();
                                if (current >= maxResults) {
                                    logger.info("Max results ({}) reached for search id={}, stopping crawl", maxResults, search.getId());
                                    search.setStatus(SearchStatus.DONE);
                                    break; // break the while-loop
                                }
                            }
                        }
                        
                        if (matcher.find() || visited.size() < 500) {
                            Set<String> links = extractLinks(content, url);
                            if (!links.isEmpty()) {
                                for (String link : links) {
                                    if (!visited.contains(link)) {
                                        queue.offer(link);
                                        pendingVisit.add(link);
                                    }
                                }
                                logger.debug("Added {} new links from {}", links.size(), url);
                            }
                        }
                    }
                } catch (Exception e) {
                    logger.warn("Error processing {} : {}", url, e.getMessage());
                }
            }
            
            long duration = System.currentTimeMillis() - startTime;
            logger.info("Crawl completed [id={}, duration={}s, urls={}, processed={}]", 
                search.getId(), 
                duration/1000.0,
                search.getUrls().size(),
                processedUrls
            );
                
        } catch (Exception e) {
            logger.error("Search failed [id={}] : {}", search.getId(), e.getMessage());
        } finally {
            if (Thread.currentThread().isInterrupted()) {
                logger.warn("Search interrupted [id={}]", search.getId());
            }
            // Ensure status is DONE when crawl finishes
            search.setStatus(SearchStatus.DONE);
        }
    }

    /**
     * Retrieves a search operation by its ID.
     */
    public CrawlSearch getSearch(String id) {
        CrawlSearch search = searches.get(id);
        if (search == null) {
            logger.debug("Search not found - ID: {}", id);
        }
        return search;
    }

    /**
     * Validates search keyword against defined constraints.
     */
    private void validateKeyword(String keyword) {
        if (keyword == null || keyword.length() < 4 || keyword.length() > 32) {
            logger.warn("Keyword validation failed - Value: '{}', Length: {}", 
                keyword, (keyword != null ? keyword.length() : "null"));
            throw new IllegalArgumentException("Keyword must be between 4 and 32 characters.");
        }

        logger.debug("Keyword validation passed - Value: '{}', Length: {}", keyword, keyword.length());
    }

    /**
     * Generates a unique 8-character search identifier.
     * Uses UUID to ensure uniqueness across concurrent operations.
     */
    private String generateId() {
        String id = UUID.randomUUID().toString().substring(0, 8);
        logger.trace("Generated new search ID: {}", id);
        return id;
    }



    private String fetchContent(String urlString) {
        for (int i = 0; i < MAX_RETRIES; i++) {
            try {
                URL url = new URL(urlString);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(CONNECT_TIMEOUT);
                conn.setReadTimeout(READ_TIMEOUT);
                conn.setRequestProperty("User-Agent", "Mozilla/5.0");
                
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                    String content = reader.lines().collect(Collectors.joining("\n"));
                    if (content != null && !content.isEmpty()) {
                        return content;
                    }
                }
            } catch (IOException e) {
                logger.warn("Attempt {} - Error fetching content from {}: {}", 
                    i + 1, urlString, e.getMessage());
                if (i == MAX_RETRIES - 1) {
                    return null;
                }
                try {
                    Thread.sleep(1000L * (i + 1)); // Exponential backoff
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return null;
                }
            }
        }
        return null;
    }

    /**
     * Extracts all valid links from an HTML page content.
     * Only includes links that belong to the configured base domain.
     */
    private Set<String> extractLinks(String content, String baseUrl) {
        if (content == null) {
            return new HashSet<>();
        }
        
        Set<String> links = new HashSet<>();
        Matcher matcher = HREF_PATTERN.matcher(content);
        
        while (matcher.find()) {
            String link = matcher.group(1);
            // Skip mailto: and javascript: links
            if (link.startsWith("mailto:") || link.startsWith("javascript:")) {
                continue;
            }
            
            String absoluteLink = makeAbsolute(link, baseUrl);
            if (absoluteLink != null) {
                links.add(absoluteLink);
                logger.trace("Found link: {} -> {}", link, absoluteLink);
            }
        }
        
        return links;
    }

    /**
     * Converts a relative URL to an absolute URL using the given base URL.
     * Handles both relative and already absolute URLs correctly.
     */
    private String makeAbsolute(String link, String baseUrl) {
        try {
            int fragmentIndex = link.indexOf('#');
            if (fragmentIndex != -1) {
                link = link.substring(0, fragmentIndex);
            }

            if (link.trim().isEmpty()) {
                return null;
            }

            if (link.startsWith("http")) {
                URL url = new URL(link);
                String host = url.getHost();
                URL baseURL = new URL(this.baseUrl);
                String baseHost = baseURL.getHost();
                
                if (!host.equals(baseHost)) {
                    return null;
                }
                return link;
            }
            
            URL base = new URL(baseUrl);
            URL absolute = new URL(base, link);
            String absoluteUrl = absolute.toString();
            
            URL url = new URL(absoluteUrl);
            String host = url.getHost();
            URL baseURL = new URL(this.baseUrl);
            String baseHost = baseURL.getHost();
            
            if (host.equals(baseHost)) {
                return absoluteUrl;
            }
            
            return null;
        } catch (Exception e) {
            logger.trace("Failed to convert URL [link: {}, base: {}] - Error: {}", 
                link, baseUrl, e.getMessage());
            return null;
        }
    }


}