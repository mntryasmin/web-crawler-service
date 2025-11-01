package com.axreng.backend.controller;

import com.axreng.backend.model.CrawlSearch;
import com.axreng.backend.service.CrawlerService;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Request;
import spark.Response;
import java.util.List;

/**
 * Controller responsible for handling web crawler endpoints.
 * Provides REST endpoints for starting new crawl searches and retrieving results.
 *
 * Endpoints:
 * - POST /crawl: Initiates a new web crawl search
 * - GET /crawl/:id: Retrieves results for a specific search
 *
 * All responses are in JSON format.
 */
public class CrawlController {
    private static final Logger logger = LoggerFactory.getLogger(CrawlController.class);
    private final CrawlerService crawlerService;
    private final Gson gson;

    public CrawlController(CrawlerService crawlerService) {
        this.crawlerService = crawlerService;
        this.gson = new Gson();
    }

    public Gson getGson() {
        return gson;
    }

    public Object handlePostCrawl(Request req, Response res) {
        String clientIp = req.ip();
        logger.info("[IP: {}] New crawl search request received", clientIp);
        logger.debug("[IP: {}] Request payload: {}", clientIp, req.body());
        
        try {
            JsonObject body = gson.fromJson(req.body(), JsonObject.class);
            String keyword = body.get("keyword").getAsString();
            
            logger.info("[IP: {}] Starting new search for keyword: {}", clientIp, keyword);
            CrawlSearch search = crawlerService.startSearch(keyword);
            
            JsonObject response = new JsonObject();
            response.addProperty("id", search.getId());
            logger.info("[IP: {}] Search initiated successfully - ID: {}", clientIp, search.getId());
            return response;
            
        } catch (IllegalArgumentException e) {
            res.status(400);
            JsonObject error = new JsonObject();
            error.addProperty("error", e.getMessage());
            logger.warn("[IP: {}] Invalid request - Error: {}", clientIp, e.getMessage());
            return error;
        }
    }

    public Object handleGetCrawl(Request req, Response res) {
        String clientIp = req.ip();
        String id = req.params("id");
        logger.info("[IP: {}] Retrieving search results for ID: {}", clientIp, id);
        
        CrawlSearch search = crawlerService.getSearch(id);
        
        if (search == null) {
            logger.warn("[IP: {}] Search not found - ID: {}", clientIp, id);
            res.status(404);
            JsonObject error = new JsonObject();
            error.addProperty("error", "Search not found");
            return error;
        }
        
        List<String> urls = search.getUrls();
        logger.info("[IP: {}] Found search [ID: {}, Status: {}, URLs: {}]", 
            clientIp, id, search.getStatus(), urls.size());
        
        JsonObject response = new JsonObject();
        response.addProperty("id", search.getId());
        response.addProperty("status", search.getStatus().toString().toLowerCase());
        response.add("urls", gson.toJsonTree(urls));
        return response;
    }
}