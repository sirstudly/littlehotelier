package com.macbackpackers.config;

import io.github.bonigarcia.wdm.WebDriverManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PostConstruct;

/**
 * Configuration class for Chrome for Testing setup.
 * This class handles the automatic download and management of ChromeDriver
 * that matches the installed Chrome version.
 */
@Configuration
public class ChromeForTestingConfig {

    private final Logger LOGGER = LoggerFactory.getLogger(getClass());

    @Value("${chromescraper.chrome.version:}")
    private String chromeVersion;

    /** When false (ronbot-read-api children), skip WebDriverManager — HtmlUnit only. */
    @Value("${chrome.for.testing.enabled:true}")
    private boolean chromeForTestingEnabled;

    @PostConstruct
    public void setupChromeForTesting() {
        if ( !chromeForTestingEnabled ) {
            LOGGER.info( "Chrome for Testing disabled (chrome.for.testing.enabled=false); skipping WebDriverManager" );
            return;
        }
        try {
            LOGGER.info("Setting up Chrome for Testing...");
            
            if (chromeVersion != null && !chromeVersion.isEmpty()) {
                LOGGER.info("Using specific Chrome version: {}", chromeVersion);
                WebDriverManager.chromedriver().browserVersion(chromeVersion).setup();
            } else {
                LOGGER.info("Using latest Chrome version");
                WebDriverManager.chromedriver().setup();
            }
            
            String driverPath = WebDriverManager.chromedriver().getDownloadedDriverPath();
            LOGGER.info("ChromeDriver path: {}", driverPath);
            
        } catch (Exception e) {
            LOGGER.error("Failed to setup Chrome for Testing", e);
            throw new RuntimeException("Chrome for Testing setup failed", e);
        }
    }
} 