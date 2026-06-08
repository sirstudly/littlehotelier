
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
import org.openqa.selenium.ElementNotInteractableException;
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
     * After password, Okta OIE may show select-authenticator-authenticate ({@code POST /idp/idx/challenge}
     * with {@code authenticator.id}), or it may default to email verification with a
     * {@code data-se="switchAuthenticator"} link. Always choose Google Authenticator when possible.
     */
    private void logOktaMfaPageState( WebDriver driver, String context ) {
        LOGGER.info( "Okta MFA diagnostics ({}): url={}, title={}",
                context, driver.getCurrentUrl(), driver.getTitle() );
        JavascriptExecutor js = (JavascriptExecutor) driver;
        LOGGER.info(
                "Okta MFA DOM counts: authenticatorRows={}, googleOtp={}, switchAuthenticator={}, passcodeInputs={}, gen3AuthButtons={}",
                js.executeScript( "return document.querySelectorAll('.authenticator-row').length" ),
                js.executeScript( "return document.querySelectorAll('[data-se=\"google_otp\"]').length" ),
                js.executeScript( "return document.querySelectorAll('[data-se=\"switchAuthenticator\"]').length" ),
                js.executeScript( "return document.querySelectorAll('input[name=\"credentials.passcode\"]').length" ),
                js.executeScript( "return document.querySelectorAll('[data-se=\"authenticator-button\"]').length" ) );
        @SuppressWarnings( "unchecked" )
        List<String> dataSe = (List<String>) js.executeScript(
                "return Array.from(document.querySelectorAll('[data-se]')).slice(0, 40)"
                        + ".map(e => e.getAttribute('data-se'));" );
        LOGGER.info( "Okta MFA data-se attributes (first 40): {}", dataSe );
        @SuppressWarnings( "unchecked" )
        List<String> labels = (List<String>) js.executeScript(
                "return Array.from(document.querySelectorAll('.authenticator-label'))"
                        + ".map(e => (e.textContent || '').trim()).filter(Boolean);" );
        if ( !labels.isEmpty() ) {
            LOGGER.info( "Okta authenticator labels: {}", labels );
        }
    }

    private boolean tryClick( WebDriver driver, WebElement element ) {
        try {
            if ( !element.isEnabled() ) {
                return false;
            }
            try {
                element.click();
            }
            catch ( ElementNotInteractableException e ) {
                ( (JavascriptExecutor) driver ).executeScript( "arguments[0].click();", element );
            }
            return true;
        }
        catch ( Exception e ) {
            return false;
        }
    }

    private void clickIfPresent( WebDriver driver, By locator, String description ) {
        List<WebElement> elements = driver.findElements( locator );
        if ( !elements.isEmpty() && tryClick( driver, elements.get( 0 ) ) ) {
            LOGGER.info( "Clicked {}", description );
        }
    }

    private void clickOktaGoogleAuthenticatorIfShown( WebDriver driver ) {
        logOktaMfaPageState( driver, "before MFA selection" );

        // Email may be pre-selected; use the footer link to reach the authenticator picker.
        clickIfPresent( driver, By.cssSelector( "[data-se='switchAuthenticator']" ), "switch authenticator link" );

        // Okta Gen2 uses <a class="button select-factor">, not <button>.
        By[] locators = {
                By.cssSelector( "[data-se='google_otp'] .select-factor" ),
                By.cssSelector( "[data-se='google_otp'] .button" ),
                By.cssSelector( ".authenticator-button[data-se='google_otp'] .button" ),
                By.xpath( "//div[contains(@class,'authenticator-row')][.//*[contains(.,'Google Authenticator')]]//*[contains(@class,'select-factor')]" ),
                By.xpath( "//div[contains(@class,'authenticator-row')][.//*[contains(.,'Google Authenticator')]]//a[contains(@class,'button')]" ),
                By.cssSelector( "button[data-se='authenticator-button'][aria-label*='Google Authenticator' i]" ),
                By.xpath( "//a[@data-se='factor-option'][.//*[contains(.,'Google Authenticator')]]" ),
        };

        WebDriverWait shortWait = new WebDriverWait( driver, Duration.ofSeconds( 30 ) );
        try {
            shortWait.until( d -> {
                for ( By locator : locators ) {
                    for ( WebElement element : d.findElements( locator ) ) {
                        if ( tryClick( d, element ) ) {
                            return true;
                        }
                    }
                }
                return false;
            } );
            LOGGER.info( "Selected Google Authenticator from MFA method picker" );
            logOktaMfaPageState( driver, "after Google Authenticator selection" );
        }
        catch ( org.openqa.selenium.TimeoutException e ) {
            logOktaMfaPageState( driver, "MFA picker not found" );
            LOGGER.info( "MFA method picker not shown; continuing to verification code step" );
        }
    }

    private void waitForPostPasswordMfaStep( WebDriver driver ) {
        WebDriverWait postPasswordWait = new WebDriverWait( driver, Duration.ofSeconds( 60 ) );
        postPasswordWait.until( or(
                presenceOfElementLocated( By.cssSelector( ".authenticator-list, .authenticator-row" ) ),
                presenceOfElementLocated( By.cssSelector( "[data-se='google_otp']" ) ),
                presenceOfElementLocated( By.cssSelector( "[data-se='switchAuthenticator']" ) ),
                presenceOfElementLocated( By.cssSelector( "[data-se='authenticator-button']" ) ),
                presenceOfElementLocated(
                        By.xpath( "//input[@name='credentials.passcode' and not(@type='password')]" ) ) ) );
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
                LOGGER.info( "After password submit, URL is {}", driver.getCurrentUrl() );

                if ( false == driver.getCurrentUrl().contains( "/connect/" ) ) {
                    try {
                        waitForPostPasswordMfaStep( driver );
                    }
                    catch ( org.openqa.selenium.TimeoutException e ) {
                        logOktaMfaPageState( driver, "post-password MFA step did not appear within 60s" );
                    }

                    String googleAuth2faCode = authService.fetchCloudbedsGoogleAuth2faCode();
                    boolean useTotp = StringUtils.isNotBlank( googleAuth2faCode );
                    clickOktaGoogleAuthenticatorIfShown( driver );

                    // MFA code: POST /idp/idx/challenge/answer with credentials.passcode (TOTP or email OTP)
                    By totpInput = By.xpath( "//input[@name='credentials.passcode' and not(@type='password')]" );
                    try {
                        wait.until( presenceOfElementLocated( totpInput ) );
                    }
                    catch ( org.openqa.selenium.TimeoutException e ) {
                        logOktaMfaPageState( driver, "TOTP input not found after MFA selection" );
                        throw e;
                    }

                    WebElement scaCode = driver.findElement( totpInput );
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
