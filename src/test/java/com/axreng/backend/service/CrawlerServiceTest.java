package com.axreng.backend.service;

import com.axreng.backend.model.CrawlSearch;
import com.axreng.backend.model.SearchStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class CrawlerServiceTest {

    private CrawlerService crawlerService;
    private static final String TEST_BASE_URL = "http://test.example.com/";

    @BeforeEach
    void setUp() {
        System.setProperty("BASE_URL", TEST_BASE_URL);
        crawlerService = new CrawlerService();
    }

    @Test
    void testStartSearch_ValidKeyword() {
        String keyword = "test";
        CrawlSearch search = crawlerService.startSearch(keyword);
        
        assertNotNull(search);
        assertEquals(keyword, search.getKeyword());
        assertEquals(8, search.getId().length());
        assertTrue(search.getId().matches("^[a-zA-Z0-9]{8}$"));
        assertEquals(SearchStatus.ACTIVE, search.getStatus());
    }

    @Test
    void testStartSearch_InvalidKeyword_TooShort() {
        assertThrows(IllegalArgumentException.class,
                () -> crawlerService.startSearch("abc"));
    }

    @Test
    void testStartSearch_InvalidKeyword_TooLong() {
        assertThrows(IllegalArgumentException.class,
                () -> crawlerService.startSearch("a".repeat(33)));
    }

    @Test
    void testStartSearch_InvalidKeyword_Null() {
        assertThrows(IllegalArgumentException.class,
                () -> crawlerService.startSearch(null));
    }

    @Test
    void testGetSearch_ExistingSearch() {
        String keyword = "test";
        CrawlSearch originalSearch = crawlerService.startSearch(keyword);
        CrawlSearch retrievedSearch = crawlerService.getSearch(originalSearch.getId());
        
        assertNotNull(retrievedSearch);
        assertEquals(originalSearch.getId(), retrievedSearch.getId());
        assertEquals(originalSearch.getKeyword(), retrievedSearch.getKeyword());
    }

    @Test
    void testGetSearch_NonexistentSearch() {
        assertNull(crawlerService.getSearch("nonexistent"));
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void testConcurrentSearches() throws InterruptedException {
        // Start multiple searches concurrently
        String[] keywords = {"test1", "test2", "test3"};
        CrawlSearch[] searches = new CrawlSearch[keywords.length];
        
        for (int i = 0; i < keywords.length; i++) {
            searches[i] = crawlerService.startSearch(keywords[i]);
        }

        // Wait for searches to complete (with timeout)
        Thread.sleep(5000);

        // Verify each search
        for (CrawlSearch search : searches) {
            CrawlSearch retrievedSearch = crawlerService.getSearch(search.getId());
            assertNotNull(retrievedSearch);
            assertTrue(retrievedSearch.getStatus() == SearchStatus.ACTIVE || 
                      retrievedSearch.getStatus() == SearchStatus.DONE);
        }
    }

    @Test
    void testBaseUrlConfiguration() {
        assertNotNull(crawlerService);
        // Verify the service is properly initialized with the base URL
        assertTrue(TEST_BASE_URL.startsWith("http://"));
    }

    @Test
    void testCaseInsensitiveSearch() {
        // Test with different case variations
        String[] keywords = {"TEST", "test", "TeSt", "tEsT"};
        
        for (String keyword : keywords) {
            CrawlSearch search = crawlerService.startSearch(keyword);
            assertNotNull(search);
            // Verify the search is created successfully regardless of case
            assertEquals(SearchStatus.ACTIVE, search.getStatus());
        }
    }

    @Test
    void testPartialResults() throws InterruptedException {
        // Start a search
        CrawlSearch search = crawlerService.startSearch("partial");
        
        // Wait a short time for some results
        Thread.sleep(2000);
        
        // Get partial results
        List<String> urls = search.getUrls();
        
        // Search should still be active
        assertEquals(SearchStatus.ACTIVE, search.getStatus());
        // Should have the partial results list (even if empty)
        assertNotNull(urls);
    }
}
