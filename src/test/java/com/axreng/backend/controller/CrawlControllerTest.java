package com.axreng.backend.controller;

import com.axreng.backend.model.CrawlSearch;
import com.axreng.backend.model.SearchStatus;
import com.axreng.backend.service.CrawlerService;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import spark.Request;
import spark.Response;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CrawlControllerTest {

    @Mock
    private CrawlerService crawlerService;

    @Mock
    private Request request;

    @Mock
    private Response response;

    private CrawlController crawlController;
    private Gson gson;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        crawlController = new CrawlController(crawlerService);
        gson = new Gson();
    }

    @Test
    void testHandlePostCrawl_ValidKeyword() {
        // Prepare test data
        String testKeyword = "security";
        String testId = "12345678";
        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("keyword", testKeyword);

        // Mock behavior
        when(request.body()).thenReturn(requestBody.toString());
        when(crawlerService.startSearch(testKeyword))
                .thenReturn(new CrawlSearch(testId, testKeyword));

        // Execute
        Object result = crawlController.handlePostCrawl(request, response);
        JsonObject jsonResult = (JsonObject) result;

        // Verify
        assertNotNull(jsonResult);
        assertTrue(jsonResult.has("id"));
        assertEquals(testId, jsonResult.get("id").getAsString());
        verify(crawlerService).startSearch(testKeyword);
    }

    @Test
    void testHandlePostCrawl_InvalidKeyword_TooShort() {
        // Prepare test data
        String testKeyword = "abc"; // 3 characters - too short
        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("keyword", testKeyword);

        // Mock behavior
        when(request.body()).thenReturn(requestBody.toString());
        when(crawlerService.startSearch(testKeyword))
                .thenThrow(new IllegalArgumentException("Keyword must be between 4 and 32 characters"));

        // Execute
        Object result = crawlController.handlePostCrawl(request, response);
        JsonObject jsonResult = (JsonObject) result;

        // Verify
        verify(response).status(400);
        assertTrue(jsonResult.has("error"));
        assertEquals("Keyword must be between 4 and 32 characters", jsonResult.get("error").getAsString());
    }

    @Test
    void testHandlePostCrawl_InvalidKeyword_TooLong() {
        // Prepare test data
        String testKeyword = "a".repeat(33); // 33 characters - too long
        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("keyword", testKeyword);

        // Mock behavior
        when(request.body()).thenReturn(requestBody.toString());
        when(crawlerService.startSearch(testKeyword))
                .thenThrow(new IllegalArgumentException("Keyword must be between 4 and 32 characters"));

        // Execute
        Object result = crawlController.handlePostCrawl(request, response);
        JsonObject jsonResult = (JsonObject) result;

        // Verify
        verify(response).status(400);
        assertTrue(jsonResult.has("error"));
        assertEquals("Keyword must be between 4 and 32 characters", jsonResult.get("error").getAsString());
    }

    @Test
    void testHandleGetCrawl_ActiveSearch() {
        // Prepare test data
        String testId = "12345678";
        String testKeyword = "security";
        CrawlSearch testSearch = new CrawlSearch(testId, testKeyword);
        testSearch.getUrls().add("http://example.com/page1");
        
        // Mock behavior
        when(request.params("id")).thenReturn(testId);
        when(crawlerService.getSearch(testId)).thenReturn(testSearch);

        // Execute
        Object result = crawlController.handleGetCrawl(request, response);
        JsonObject jsonResult = (JsonObject) result;

        // Verify
        assertNotNull(jsonResult);
        assertEquals(testId, jsonResult.get("id").getAsString());
        assertEquals("active", jsonResult.get("status").getAsString());
        assertTrue(jsonResult.get("urls").getAsJsonArray().size() > 0);
    }

    @Test
    void testHandleGetCrawl_CompletedSearch() {
        // Prepare test data
        String testId = "12345678";
        CrawlSearch testSearch = mock(CrawlSearch.class);
        when(testSearch.getId()).thenReturn(testId);
        when(testSearch.getStatus()).thenReturn(SearchStatus.DONE);
        when(testSearch.getUrls()).thenReturn(Arrays.asList(
            "http://example.com/page1",
            "http://example.com/page2"
        ));

        // Mock behavior
        when(request.params("id")).thenReturn(testId);
        when(crawlerService.getSearch(testId)).thenReturn(testSearch);

        // Execute
        Object result = crawlController.handleGetCrawl(request, response);
        JsonObject jsonResult = (JsonObject) result;

        // Verify
        assertNotNull(jsonResult);
        assertEquals(testId, jsonResult.get("id").getAsString());
        assertEquals("done", jsonResult.get("status").getAsString());
        assertEquals(2, jsonResult.get("urls").getAsJsonArray().size());
    }

    @Test
    void testHandleGetCrawl_NotFound() {
        // Mock behavior
        when(request.params("id")).thenReturn("nonexistent");
        when(crawlerService.getSearch("nonexistent")).thenReturn(null);

        // Execute
        Object result = crawlController.handleGetCrawl(request, response);
        JsonObject jsonResult = (JsonObject) result;

        // Verify
        verify(response).status(404);
        assertTrue(jsonResult.has("error"));
        assertEquals("Search not found", jsonResult.get("error").getAsString());
    }

    @Test
    void testHandleGetCrawl_InvalidId() {
        // Mock behavior
        when(request.params("id")).thenReturn(null);
        when(crawlerService.getSearch(null)).thenReturn(null);

        // Execute
        Object result = crawlController.handleGetCrawl(request, response);
        JsonObject jsonResult = (JsonObject) result;

        // Verify
        verify(response).status(404);
        assertTrue(jsonResult.has("error"));
        assertEquals("Search not found", jsonResult.get("error").getAsString());
    }
}
