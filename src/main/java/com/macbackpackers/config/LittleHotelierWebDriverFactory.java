
package com.macbackpackers.config;

import java.util.concurrent.TimeUnit;

import org.apache.commons.lang3.SystemUtils;
import org.apache.commons.pool2.BasePooledObjectFactory;
import org.apache.commons.pool2.PooledObject;
import org.apache.commons.pool2.impl.DefaultPooledObject;
import org.openqa.selenium.By;
import org.openqa.selenium.Cookie;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.macbackpackers.dao.WordPressDAO;
import com.macbackpackers.exceptions.UnrecoverableFault;

/**
 * A factory for creating WebDriver instances logged into LittleHotelier.
 *
 */
@Component
public class LittleHotelierWebDriverFactory extends BasePooledObjectFactory<WebDriver> {

    private final Logger LOGGER = LoggerFactory.getLogger( getClass() );

    @Value( "${lilhotelier.propertyid}" )
    private String lhPropertyId;

    @Value( "${chromescraper.maxwait.seconds:30}" )
    private int maxWaitSeconds;

    @Value( "${chromescraper.driver.options:--headless --disable-gpu --ignore-certificate-errors}" )
    private String chromeOptions;

    @Autowired
    private WordPressDAO dao;

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

        driver.get( "https://littlehotelier-emea.sec-login.com/login/auth?lang_code=en" );

        WebElement usernameBox = driver.findElement( By.id( "username" ) );
        WebElement passwordBox = driver.findElement( By.id( "password" ) );
        WebElement submitButton = driver.findElement( By.id( "login-btn" ) );

        WebDriverWait wait = new WebDriverWait( driver, 30 );
        wait.until( ExpectedConditions.visibilityOfAllElements( usernameBox, passwordBox, submitButton ) );

        // load pre-logged in cookie
        String domain = "app.littlehotelier.com";
        driver.manage().addCookie( new Cookie( "_littlehotelier_session",
                dao.getOption( "hbo_lilho_session" ), domain, "/", null ) );

        String testUrl = String.format( "https://app.littlehotelier.com/extranet/properties/%s/setup", lhPropertyId );
        driver.get( testUrl );

        if ( driver.getCurrentUrl().contains( "login" ) ) {
            LOGGER.info( "Looks like our session has expired. Logging in." );
            usernameBox = driver.findElement( By.id( "username" ) );
            passwordBox = driver.findElement( By.id( "password" ) );
            submitButton = driver.findElement( By.id( "login-btn" ) );
            wait.until( ExpectedConditions.visibilityOfAllElements( usernameBox, passwordBox, submitButton ) );

            usernameBox.sendKeys( dao.getOption( "hbo_lilho_username" ) );
            passwordBox.sendKeys( dao.getOption( "hbo_lilho_password" ) );
            submitButton.click();

            // wait until we've moved onto the next page
            wait.until( ExpectedConditions.stalenessOf( submitButton ) );

            // redo get
            driver.get( testUrl );

            if ( driver.getCurrentUrl().contains( "login" ) ) {
                throw new UnrecoverableFault( "Unable to login. Has the password changed?" );
            }

            // update our session
            String sessionId = driver.manage().getCookieNamed( "_littlehotelier_session" ).getValue();
            dao.setOption( "hbo_lilho_session", sessionId );
        }
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
