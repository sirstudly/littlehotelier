
package com.macbackpackers.config;

import io.github.bonigarcia.wdm.WebDriverManager;
import org.apache.commons.pool2.BasePooledObjectFactory;
import org.apache.commons.pool2.PooledObject;
import org.apache.commons.pool2.impl.DefaultPooledObject;
import org.apache.commons.lang3.StringUtils;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeDriverService;
import org.openqa.selenium.chrome.ChromeOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.File;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A factory for creating WebDriver instances logged into LittleHotelier.
 *
 */
@Component
public class LittleHotelierWebDriverFactory extends BasePooledObjectFactory<WebDriver> {

    private final Logger LOGGER = LoggerFactory.getLogger( getClass() );

    @Value( "${chromescraper.maxwait.seconds:60}" )
    private int maxWaitSeconds;

    @Value( "${chromescraper.driver.options:user-data-dir=chromeprofile --headless=new --disable-gpu --start-maximized --ignore-certificate-errors --remote-allow-origins=*}" )
    private String chromeOptions;

    @Value( "${chromescraper.driver.verbose:false}" )
    private boolean chromeDriverVerbose;

    /**
     * Optional Chrome user-agent. Spaces are allowed here (unlike {@code chromescraper.driver.options},
     * which is split on whitespace). Leave blank to keep Chrome's default (HeadlessChrome when headless).
     */
    @Value( "${chromescraper.driver.useragent:}" )
    private String chromeUserAgent;

    @Value( "${processor.job.log.localdir:logs}" )
    private String logDir;

    @Override
    public WebDriver create() throws Exception {
        // Use Chrome for Testing - WebDriverManager will automatically download and manage the correct ChromeDriver version
        WebDriverManager.chromedriver().setup();

        ChromeOptions options = new ChromeOptions();
        List<String> optionValues = new ArrayList<>(Arrays.asList(chromeOptions.split( " " )));
        options.addArguments( optionValues.toArray(new String[optionValues.size()]) );
        options.addArguments( "--disable-blink-features=AutomationControlled" );
        options.setExperimentalOption( "excludeSwitches", Collections.singletonList( "enable-automation" ) );
        options.setExperimentalOption( "useAutomationExtension", false );
        if ( StringUtils.isNotBlank( chromeUserAgent ) ) {
            options.addArguments( "--user-agent=" + chromeUserAgent.trim() );
        }

        // Use Chrome for Testing binary if specified
        String chromeBinaryPath = System.getProperty("chrome.binary.path");
        if (chromeBinaryPath != null && !chromeBinaryPath.isEmpty()) {
            options.setBinary(chromeBinaryPath);
        }

        boolean verbose = chromeDriverVerbose
                || Boolean.parseBoolean( System.getProperty( "webdriver.chrome.verboseLogging", "false" ) );
        ChromeDriver driver;
        if ( verbose ) {
            File logFile = new File( logDir, "chromedriver.log" );
            logFile.getParentFile().mkdirs();
            LOGGER.info( "ChromeDriver verbose log: {}", logFile.getAbsolutePath() );
            options.addArguments( "--enable-logging", "--v=1" );
            ChromeDriverService service = new ChromeDriverService.Builder()
                    .withVerbose( true )
                    .withLogFile( logFile )
                    .withAppendLog( true )
                    .build();
            driver = new ChromeDriver( service, options );
        }
        else {
            driver = new ChromeDriver( options );
        }

        // configure wait-time when finding elements on the page
        driver.manage().timeouts().pageLoadTimeout(Duration.ofSeconds(maxWaitSeconds));
        applyAutomationHiding( driver );

        return driver;
    }

    /**
     * Use the default PooledObject implementation.
     */
    @Override
    public PooledObject<WebDriver> wrap( WebDriver driver ) {
        return new DefaultPooledObject<WebDriver>( driver );
    }

    @Override
    public void destroyObject( PooledObject<WebDriver> pooledObj ) throws Exception {
        pooledObj.getObject().quit();
    }

    /**
     * ChromeDriver always adds {@code --enable-automation} unless excluded above. CDP still patches
     * {@code navigator.webdriver} and aligns client hints with a headed Chrome UA when configured.
     */
    private void applyAutomationHiding( ChromeDriver driver ) {
        Map<String, Object> webdriverPatch = new HashMap<>();
        webdriverPatch.put( "source",
                "Object.defineProperty(navigator, 'webdriver', {get: () => undefined});" );
        driver.executeCdpCommand( "Page.addScriptToEvaluateOnNewDocument", webdriverPatch );
        if ( StringUtils.isBlank( chromeUserAgent ) ) {
            return;
        }
        // application.properties ships a Linux UA for Docker. Applying that plus
        // Emulation.setUserAgentOverride(platform=Linux) on macOS makes Booking's WAF
        // reject otherwise-valid tokens (this Mac test logged X11 Linux on chromedriver mac64).
        if ( false == "Linux".equalsIgnoreCase( System.getProperty( "os.name" ) ) ) {
            LOGGER.info( "Skipping Linux user-agent override on {} (chromescraper.driver.useragent is for Docker)",
                    System.getProperty( "os.name" ) );
            return;
        }
        String ua = chromeUserAgent.trim();
        List<Map<String, String>> brands = new ArrayList<>();
        brands.add( brand( "Not:A-Brand", "99" ) );
        brands.add( brand( "Google Chrome", "151" ) );
        brands.add( brand( "Chromium", "151" ) );
        List<Map<String, String>> fullVersionList = new ArrayList<>();
        fullVersionList.add( brand( "Not:A-Brand", "10.0.1.4" ) );
        fullVersionList.add( brand( "Google Chrome", "151.0.7922.138" ) );
        fullVersionList.add( brand( "Chromium", "151.0.7922.138" ) );
        Map<String, Object> metadata = new HashMap<>();
        metadata.put( "brands", brands );
        metadata.put( "fullVersionList", fullVersionList );
        metadata.put( "platform", "Linux" );
        metadata.put( "platformVersion", "6.8.0" );
        metadata.put( "architecture", "x86" );
        metadata.put( "model", "" );
        metadata.put( "mobile", Boolean.FALSE );
        metadata.put( "bitness", "64" );
        metadata.put( "wow64", Boolean.FALSE );
        Map<String, Object> uaOverride = new HashMap<>();
        uaOverride.put( "userAgent", ua );
        uaOverride.put( "platform", "Linux x86_64" );
        uaOverride.put( "userAgentMetadata", metadata );
        driver.executeCdpCommand( "Network.setUserAgentOverride", uaOverride );
        driver.executeCdpCommand( "Emulation.setUserAgentOverride", uaOverride );
        LOGGER.info( "Chrome user-agent override: {}", ua );
    }

    private static Map<String, String> brand( String name, String version ) {
        Map<String, String> brand = new HashMap<>();
        brand.put( "brand", name );
        brand.put( "version", version );
        return brand;
    }

}
