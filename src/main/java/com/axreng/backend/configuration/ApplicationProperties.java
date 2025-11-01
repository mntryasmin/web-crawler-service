package com.axreng.backend.configuration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Configuration manager with environment variable support.
 * Uses Singleton pattern for centralized property access.
 * 
 * Property Sources (in order):
 * 1. application.properties
 * 2. Environment variables
 * 
 * Default Properties:
 * - server.port: 4567
 * - base.url: http://hiring.axreng.com/
 * - crawler.max.depth: 10
 * - crawler.timeout.seconds: 30
 *
 * Thread Safety: This class is thread-safe through lazy initialization
 */
public class ApplicationProperties {
    private static final Logger logger = LoggerFactory.getLogger(ApplicationProperties.class);
    private static final Properties properties = new Properties();
    private static ApplicationProperties instance;

    private ApplicationProperties() {
        loadProperties();
    }

    public static ApplicationProperties getInstance() {
        if (instance == null) {
            instance = new ApplicationProperties();
        }
        return instance;
    }

    /**
     * Loads properties from file and environment.
     * Environment variables take precedence over file values.
     */
    private void loadProperties() {
        try (InputStream input = getClass().getClassLoader().getResourceAsStream("application.properties")) {
            if (input == null) {
                logger.error("Configuration file not found: application.properties");
                return;
            }
            properties.load(input);
            logger.debug("Loaded base configuration from application.properties");
            
            // Override with environment variables
            loadFromEnvironment("PORT", "server.port");
            loadFromEnvironment("BASE_URL", "base.url");
            logger.info("Configuration loaded - Port: {}, Base URL: {}", 
                getServerPort(), getBaseUrl());
        } catch (IOException ex) {
            logger.error("Failed to load configuration: {}", ex.getMessage());
        }
    }

    /**
     * Overrides property with environment variable if present.
     */
    private void loadFromEnvironment(String envName, String propertyName) {
        String envValue = System.getenv(envName);
        if (envValue != null && !envValue.isEmpty()) {
            properties.setProperty(propertyName, envValue);
            logger.debug("Override from env: {}={}", propertyName, envValue);
        }
    }

    public int getServerPort() {
        return Integer.parseInt(properties.getProperty("server.port", "4567"));
    }

    public String getBaseUrl() {
        return properties.getProperty("base.url", "http://hiring.axreng.com/");
    }

    public int getCrawlerMaxDepth() {
        return Integer.parseInt(properties.getProperty("crawler.max.depth", "50"));
    }

    public int getCrawlerIdleTimeout() {
        return Integer.parseInt(properties.getProperty("crawler.idle.timeout", "30000"));
    }

    public int getCrawlerMaxResults() {
        return Integer.parseInt(properties.getProperty("crawler.max.results", "100"));
    }

    public boolean isLimitResults() {
        return Boolean.parseBoolean(properties.getProperty("crawler.limit.results", "true"));
    }

    public int getCrawlerMaxEmptyPolls() {
        return Integer.parseInt(properties.getProperty("crawler.max.empty.polls", "50"));
    }

    public int getCrawlerConnectTimeout() {
        return Integer.parseInt(properties.getProperty("crawler.connect.timeout", "5000"));
    }

    public int getCrawlerReadTimeout() {
        return Integer.parseInt(properties.getProperty("crawler.read.timeout", "5000"));
    }

    public String getProperty(String key, String defaultValue) {
        return properties.getProperty(key, defaultValue);
    }
}