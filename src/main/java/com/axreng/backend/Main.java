package com.axreng.backend;

import com.axreng.backend.configuration.ApplicationProperties;
import com.axreng.backend.configuration.RouteConfiguration;
import com.axreng.backend.controller.CrawlController;
import com.axreng.backend.service.CrawlerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Main application entry point and server lifecycle manager.
 * Implements a thread-safe server initialization and shutdown process.
 *
 * Features:
 * - Safe server restart capability
 * - Synchronized server state management
 * - Configurable port through properties
 * - Graceful shutdown support
 */
public class Main {
    private static final Logger logger = LoggerFactory.getLogger(Main.class);
    private static final Object LOCK = new Object();
    private final CrawlController crawlController;
    private final RouteConfiguration routeConfiguration;

    public Main(CrawlerService crawlerService) {
        this(crawlerService, ApplicationProperties.getInstance().getServerPort());
    }
    
    public Main(CrawlerService crawlerService, int port) {
        this.crawlController = new CrawlController(crawlerService);
        this.routeConfiguration = new RouteConfiguration(crawlController, port);
        startServer();
    }

    private void startServer() {
        synchronized (LOCK) {
            logger.info("Initiating server start sequence");
            logger.debug("Stopping any existing server instance");
            stop();
            
            logger.debug("Waiting for complete server shutdown");
            spark.Spark.awaitStop();
            
            logger.info("Configuring new server instance");
            routeConfiguration.configureRoutes();
            
            logger.debug("Waiting for server initialization");
            spark.Spark.awaitInitialization();
            logger.info("Server successfully started and ready to accept requests");
        }
    }

    public void restart() {
        logger.info("Initiating server restart");
        startServer();
        logger.info("Server restart completed");
    }

    public static void main(String[] args) {
        logger.info("=== Web Crawler Application Starting ===");
        new Main(new CrawlerService());
    }

    public void stop() {
        logger.info("Initiating server shutdown");
        spark.Spark.stop();
        spark.Spark.awaitStop();
        logger.info("Server shutdown completed");
    }
}
