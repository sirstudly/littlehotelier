
package com.macbackpackers.config;

import java.util.concurrent.TimeUnit;

import org.apache.commons.lang3.SystemUtils;
import org.apache.commons.pool2.BasePooledObjectFactory;
import org.apache.commons.pool2.PooledObject;
import org.apache.commons.pool2.impl.DefaultPooledObject;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * A factory for creating WebDriver instances logged into LittleHotelier.
 *
 */
@Component
public class LittleHotelierWebDriverFactory extends BasePooledObjectFactory<WebDriver> {

    @Value( "${lilhotelier.propertyid}" )
    private String lhPropertyId;

    @Value( "${chromescraper.maxwait.seconds:60}" )
    private int maxWaitSeconds;

    @Value( "${chromescraper.driver.options:user-data-dir=chromeprofile --headless --disable-gpu --start-maximized --ignore-certificate-errors}" )
    private String chromeOptions;

    @Override
    public WebDriver create() throws Exception {
        System.setProperty( "webdriver.chrome.driver", getClass().getClassLoader().getResource(
                SystemUtils.IS_OS_WINDOWS ? "chromedriver.exe" : "chromedriver" ).getPath() );

        ChromeOptions options = new ChromeOptions();
        options.addArguments( chromeOptions.split( " " ) );

        WebDriver driver = new ChromeDriver( options );

        // configure wait-time when finding elements on the page
        driver.manage().timeouts().implicitlyWait( maxWaitSeconds, TimeUnit.SECONDS );
        driver.manage().timeouts().pageLoadTimeout( maxWaitSeconds * 2, TimeUnit.SECONDS );

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
        pooledObj.getObject().close();
    }

}
