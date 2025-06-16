
package com.macbackpackers.scrapers;

import com.macbackpackers.dao.WordPressDAO;
import com.macbackpackers.exceptions.MissingUserDataException;
import com.macbackpackers.exceptions.UnrecoverableFault;
import com.macbackpackers.services.AuthenticationService;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.pool2.impl.GenericObjectPool;
import org.htmlunit.WebClient;
import org.openqa.selenium.By;
import org.openqa.selenium.Cookie;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.net.URLDecoder;
import java.time.Duration;
import java.util.stream.Collectors;

import static org.openqa.selenium.support.ui.ExpectedConditions.presenceOfElementLocated;
import static org.openqa.selenium.support.ui.ExpectedConditions.stalenessOf;
import static org.openqa.selenium.support.ui.ExpectedConditions.urlContains;

@Component
public class ChromeScraper {

    private final Logger LOGGER = LoggerFactory.getLogger( getClass() );

    @Value( "${chromescraper.maxwait.seconds:30}" )
    private int MAX_WAIT_SECONDS;

    @Autowired
    private GenericObjectPool<WebDriver> driverFactory;

    @Autowired
    private AuthenticationService authService;

    @Autowired
    private ApplicationContext appContext;

    @Autowired
    private WordPressDAO dao;

    @Autowired
    private CloudbedsJsonRequestFactory jsonRequestFactory;

    class AutocloseableWebDriver implements AutoCloseable {

        private WebDriver driver;
        private WebDriverWait wait;

        public AutocloseableWebDriver() throws Exception {
            this.driver = driverFactory.borrowObject();
            this.wait = new WebDriverWait(driver, Duration.ofSeconds( MAX_WAIT_SECONDS ));
        }

        public WebDriver getDriver() {
            return this.driver;
        }

        public WebDriverWait getDriverWait() {
            return this.wait;
        }

        @Override
        public void close() throws Exception {
            driverFactory.returnObject(this.driver);
        }
    }

    public synchronized void loginToCloudbedsAndSaveSession() throws Exception {

        String username = dao.getOption( "hbo_cloudbeds_username" );
        String password = dao.getOption( "hbo_cloudbeds_password" );

        if ( username == null || password == null ) {
            throw new MissingUserDataException( "Missing username/password" );
        }

        try ( AutocloseableWebDriver driverFactory = new AutocloseableWebDriver() ) {
            WebDriver driver = driverFactory.getDriver();
            WebDriverWait wait = driverFactory.getDriverWait();

            // we need to navigate first before loading the cookies for that domain
            driver.get( "https://hotels.cloudbeds.com/auth/login" );
            LOGGER.info( "Current URL is " + driver.getCurrentUrl() );
            if ( false == driver.getCurrentUrl().contains( "/connect/" ) ) { // will redirect to dashboard if we're already logged in
                LOGGER.info( "Current URL is " + driver.getCurrentUrl() );
                WebElement emailInput = driver.findElement( By.id( "email" ) );
                emailInput.sendKeys( username );

                WebElement nextButton = driver.findElement( By.xpath( "//button[@type='submit']" ) );
                nextButton.click();

                wait.until( presenceOfElementLocated( By.id( "okta-signin-password" ) ) );
                WebElement passwordInput = driver.findElement( By.id( "okta-signin-password" ) );
                passwordInput.sendKeys( password );

                WebElement rememberMe = driver.findElement( By.xpath( "//label[@data-se-for-name='remember']" ) );
                rememberMe.click();

                nextButton = driver.findElement( By.id( "okta-signin-submit" ) );
                nextButton.click();

                wait.until( stalenessOf( nextButton ) );

                if ( false == driver.getCurrentUrl().contains( "/connect/" ) ) {
                    wait.until( urlContains( "/signin/verify/google" ) ); // assumes always google authenticator

                    WebElement scaCode = driver.findElement( By.name( "answer" ) );
                    String googleAuth2faCode = authService.fetchCloudbedsGoogleAuth2faCode();
                    if ( StringUtils.isNotBlank( googleAuth2faCode ) ) {
                        LOGGER.info( "Attempting TOTP verification: " + googleAuth2faCode );
                        scaCode.sendKeys( googleAuth2faCode );
                    } else {
                        LOGGER.info( "Attempting SMS verification" );
                        String otp = authService.fetchCloudbeds2FACode( appContext.getBean( "webClientForCloudbeds", WebClient.class ) );
                        scaCode.sendKeys( otp );
                    }

                    nextButton = driver.findElement( By.xpath( "//input[@data-type='save']" ) );
                    nextButton.click();
                }
            }

            LOGGER.info( "Loading dashboard..." );
            wait.until( presenceOfElementLocated( By.id( "tab_arrivals-today" ) ) );

            // if we're actually logged in, we should be able to get the hostel name
            Cookie hc = driver.manage().getCookieNamed( "hotel_name" );
            if ( hc == null ) {
                LOGGER.info( "Hostel cookie not set? Currently on " + driver.getCurrentUrl() );
                throw new UnrecoverableFault( "Failed login. Hostel cookie not set." );
            }
            LOGGER.info( "PROPERTY NAME is: " + URLDecoder.decode( hc.getValue(), "UTF-8" ) );

            // save credentials to disk so we don't need to do this again
            jsonRequestFactory.setCookies(
                    driver.manage().getCookies().stream()
                            .map( c -> c.getName() + "=" + c.getValue() )
                            .collect( Collectors.joining( ";" ) ) );
            jsonRequestFactory.setUserAgent(
                    (String) ( (JavascriptExecutor) driver ).executeScript( "return navigator.userAgent;" ) );
        }
    }
}
