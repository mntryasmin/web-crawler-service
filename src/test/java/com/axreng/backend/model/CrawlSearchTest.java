package com.axreng.backend.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CrawlSearchTest {

    @Test
    void testCrawlSearchCreation() {
        String id = "12345678";
        String keyword = "test";
        
        CrawlSearch search = new CrawlSearch(id, keyword);
        
        assertEquals(id, search.getId());
        assertEquals(keyword, search.getKeyword());
        assertEquals(SearchStatus.ACTIVE, search.getStatus());
        assertTrue(search.getUrls().isEmpty());
    }

    @Test
    void testAddUrl() {
        CrawlSearch search = new CrawlSearch("12345678", "test");
        String url = "http://example.com";
        
        search.addUrl(url);
        
        assertTrue(search.getUrls().contains(url));
        assertEquals(1, search.getUrls().size());
    }

    @Test
    void testAddDuplicateUrl() {
        CrawlSearch search = new CrawlSearch("12345678", "test");
        String url = "http://example.com";
        
        search.addUrl(url);
        search.addUrl(url); // Add same URL again
        
        assertEquals(1, search.getUrls().size());
    }

    @Test
    void testSetStatus() throws InterruptedException {
        CrawlSearch search = new CrawlSearch("12345678", "test");
        
        // Initial status should be ACTIVE
        assertEquals(SearchStatus.ACTIVE, search.getStatus());
        
        // Wait for more than 5 seconds
        Thread.sleep(5100);
        
        // Change to DONE
        search.setStatus(SearchStatus.DONE);
        assertEquals(SearchStatus.DONE, search.getStatus());
    }

    @Test
    void testStatusUpdateTimeout() throws InterruptedException {
        CrawlSearch search = new CrawlSearch("12345678", "test");
        
        // Wait for more than 5 seconds
        Thread.sleep(5100);
        
        // Now try to set status to DONE
        search.setStatus(SearchStatus.DONE);
        assertEquals(SearchStatus.DONE, search.getStatus());
    }

    @Test
    void testConcurrentUrlAccess() throws InterruptedException {
        CrawlSearch search = new CrawlSearch("12345678", "test");
        int numThreads = 10;
        Thread[] threads = new Thread[numThreads];
        
        // Create multiple threads that add URLs concurrently
        for (int i = 0; i < numThreads; i++) {
            final int threadNum = i;
            threads[i] = new Thread(() -> {
                search.addUrl("http://example.com/page" + threadNum);
            });
            threads[i].start();
        }
        
        // Wait for all threads to complete
        for (Thread thread : threads) {
            thread.join();
        }
        
        // Verify all URLs were added
        assertEquals(numThreads, search.getUrls().size());
    }
}
