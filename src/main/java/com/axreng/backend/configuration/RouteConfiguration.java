package com.axreng.backend.configuration;

import com.axreng.backend.controller.CrawlController;
import com.google.gson.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import static spark.Spark.*;

/**
 * Web API route configuration using Spark framework.
 * 
 * Features:
 * - JSON responses
 * - Global error handler
 * - Crawler endpoints (/crawl)
 * 
 * Routes:
 * - POST /crawl: Start search
 * - GET /crawl/:id: Get results
 */
public class RouteConfiguration {
    private static final Logger logger = LoggerFactory.getLogger(RouteConfiguration.class);
    private final CrawlController crawlController;
    private final int port;

    public RouteConfiguration(CrawlController crawlController, int port) {
        this.crawlController = crawlController;
        this.port = port;
    }

    /**
     * Configures all web API routes and middleware
     */
    public void configureRoutes() {
        try {
            logger.info("Starting server on port {}", port);
            port(port);
            
            logger.debug("Setting up response middleware");
            after((req, res) -> res.type("application/json"));

            logger.debug("Configuring error handling");
            exception(Exception.class, (e, req, res) -> {
                logger.error("Request failed [{}] {} {} - {}", 
                    req.ip(), req.requestMethod(), req.pathInfo(), e.getMessage());
                res.status(400);
                JsonObject error = new JsonObject();
                error.addProperty("error", e.getMessage());
                res.body(error.toString());
            });

            // POST endpoint to start a new search
            post("/crawl", crawlController::handlePostCrawl, crawlController.getGson()::toJson);

            // GET endpoint to retrieve search results
            get("/crawl/:id", crawlController::handleGetCrawl, crawlController.getGson()::toJson);
        } catch (Exception e) {
            logger.error("Failed to configure routes", e);
            throw e;
        }
    }
}