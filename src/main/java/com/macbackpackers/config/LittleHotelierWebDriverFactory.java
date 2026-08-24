
package com.macbackpackers.config;

import io.github.bonigarcia.wdm.WebDriverManager;
import org.apache.commons.pool2.BasePooledObjectFactory;
import org.apache.commons.pool2.PooledObject;
import org.apache.commons.pool2.impl.DefaultPooledObject;
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
import java.util.List;

/**
 * A factory for creating WebDriver instances logged into LittleHotelier.
 *
 */
@Component
public class LittleHotelierWebDriverFactory extends BasePooledObjectFactory<WebDriver> {

    private final Logger LOGGER = LoggerFactory.getLogger( getClass() );

    @Value( "${chromescraper.maxwait.seconds:60}" )
    private int maxWaitSeconds;

    @Value( "${chromescraper.driver.options:user-data-dir=chromeprofile --headless --disable-gpu --start-maximized --ignore-certificate-errors --remote-allow-origins=*}" )
    private String chromeOptions;

    @Value( "${chromescraper.driver.verbose:false}" )
    private boolean chromeDriverVerbose;

    @Value( "${processor.job.log.localdir:logs}" )
    private String logDir;

    @Override
    public WebDriver create() throws Exception {
        // Use Chrome for Testing - WebDriverManager will automatically download and manage the correct ChromeDriver version
        WebDriverManager.chromedriver().setup();

        ChromeOptions options = new ChromeOptions();
        List<String> optionValues = new ArrayList<>(Arrays.asList(chromeOptions.split( " " )));
        options.addArguments( optionValues.toArray(new String[optionValues.size()]) );

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

}
