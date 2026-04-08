
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
import java.util.List;
import java.util.stream.Collectors;

import static org.openqa.selenium.support.ui.ExpectedConditions.elementToBeClickable;
import static org.openqa.selenium.support.ui.ExpectedConditions.or;
import static org.openqa.selenium.support.ui.ExpectedConditions.presenceOfElementLocated;
import static org.openqa.selenium.support.ui.ExpectedConditions.stalenessOf;

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

    /**
     * After password, Okta may show a factor picker ({@code POST /idp/idx/challenge} with {@code authenticator.id}).
     * If that screen is absent, this is a no-op.
     */
    private void clickOktaAuthenticatorOptionIfShown( WebDriver driver, boolean preferGoogleAuthenticatorOverEmail ) {
        WebDriverWait shortWait = new WebDriverWait( driver, Duration.ofSeconds( 20 ) );
        By locator = preferGoogleAuthenticatorOverEmail
                ? By.xpath( "//a[@data-se='factor-option'][.//*[contains(.,'Google Authenticator')]]" )
                : By.xpath( "//a[@data-se='factor-option'][.//*[contains(.,'Email')] and not(.//*[contains(.,'Google Authenticator')])]" );
        try {
            shortWait.until( elementToBeClickable( locator ) ).click();
        }
        catch ( org.openqa.selenium.TimeoutException e ) {
            LOGGER.info( "MFA method picker not shown; continuing to verification code step" );
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
                // 1) Cloudbeds login page: HAR POST /auth/verify_mfa { email } — field id="email", then submit button
                wait.until( presenceOfElementLocated( By.id( "email" ) ) );
                driver.findElement( By.id( "email" ) ).sendKeys( username );
                WebElement cloudbedsSubmit = driver.findElement( By.xpath( "//button[@type='submit']" ) );
                cloudbedsSubmit.click();

                // 2) Okta hosted sign-in: may show identifier again (JSON field "identifier" on POST /idp/idx/identify), or go straight to password
                wait.until( or(
                        presenceOfElementLocated( By.name( "identifier" ) ),
                        presenceOfElementLocated( By.cssSelector( "input[name='credentials.passcode'][type='password']" ) ) ) );

                List<WebElement> oktaIdentifier = driver.findElements( By.name( "identifier" ) );
                if ( !oktaIdentifier.isEmpty() && oktaIdentifier.get( 0 ).isDisplayed() ) {
                    oktaIdentifier.get( 0 ).clear();
                    oktaIdentifier.get( 0 ).sendKeys( username );
                    WebElement oktaNext = driver.findElement( By.cssSelector(
                            "input[type='submit'].button-primary, button[type='submit'].button-primary" ) );
                    oktaNext.click();
                }

                wait.until( presenceOfElementLocated( By.cssSelector( "input[name='credentials.passcode'][type='password']" ) ) );
                WebElement passwordInput = driver.findElement( By.cssSelector( "input[name='credentials.passcode'][type='password']" ) );
                passwordInput.sendKeys( password );

                List<WebElement> rememberCheckboxes = driver.findElements( By.cssSelector( "input[type='checkbox'][name='remember']" ) );
                if ( !rememberCheckboxes.isEmpty() ) {
                    rememberCheckboxes.get( 0 ).click();
                }

                WebElement nextButton = driver.findElement( By.cssSelector(
                        "input[type='submit'].button-primary, button[type='submit'].button-primary" ) );
                nextButton.click();

                wait.until( stalenessOf( nextButton ) );

                if ( false == driver.getCurrentUrl().contains( "/connect/" ) ) {
                    String googleAuth2faCode = authService.fetchCloudbedsGoogleAuth2faCode();
                    boolean useTotp = StringUtils.isNotBlank( googleAuth2faCode );
                    clickOktaAuthenticatorOptionIfShown( driver, useTotp );

                    // MFA code: POST /idp/idx/challenge/answer with credentials.passcode (TOTP or email OTP)
                    wait.until( presenceOfElementLocated(
                            By.xpath( "//input[@name='credentials.passcode' and not(@type='password')]" ) ) );

                    WebElement scaCode = driver.findElement(
                            By.xpath( "//input[@name='credentials.passcode' and not(@type='password')]" ) );
                    if ( useTotp ) {
                        LOGGER.info( "Attempting TOTP verification: " + googleAuth2faCode );
                        scaCode.sendKeys( googleAuth2faCode );
                    } else {
                        LOGGER.info( "Attempting email/SMS verification" );
                        String otp = authService.fetchCloudbeds2FACode( appContext.getBean( "webClientForCloudbeds", WebClient.class ) );
                        scaCode.sendKeys( otp );
                    }

                    nextButton = driver.findElement( By.cssSelector(
                            "input[type='submit'].button-primary, button[type='submit'].button-primary" ) );
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
