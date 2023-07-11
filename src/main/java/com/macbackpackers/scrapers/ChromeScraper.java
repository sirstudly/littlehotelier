
package com.macbackpackers.scrapers;

import com.gargoylesoftware.htmlunit.WebClient;
import com.macbackpackers.beans.CardDetails;
import com.macbackpackers.beans.GuestDetails;
import com.macbackpackers.dao.WordPressDAO;
import com.macbackpackers.exceptions.MissingUserDataException;
import com.macbackpackers.exceptions.UnrecoverableFault;
import com.macbackpackers.services.AuthenticationService;
import com.macbackpackers.services.GmailService;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.pool2.impl.GenericObjectPool;
import org.openqa.selenium.By;
import org.openqa.selenium.Cookie;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.Select;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URLDecoder;
import java.time.Duration;
import java.util.Date;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.openqa.selenium.support.ui.ExpectedConditions.presenceOfElementLocated;
import static org.openqa.selenium.support.ui.ExpectedConditions.urlContains;

@Component
public class ChromeScraper {

    private final Logger LOGGER = LoggerFactory.getLogger( getClass() );

    @Value( "${chromescraper.maxwait.seconds:30}" )
    private int MAX_WAIT_SECONDS;

    @Autowired
    private BookingsPageScraper bookingsScraper;

    @Autowired
    private GenericObjectPool<WebDriver> driverFactory;

    @Autowired
    private GmailService gmailService;

    @Autowired
    private AuthenticationService authService;

    @Autowired
    private ApplicationContext appContext;

    @Autowired
    private WordPressDAO dao;

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
     * Returns the guest details for a reservation.
     *
     * @param bookingRef booking reference
     * @param checkinDate checkin-date
     * @return guest details (card details may be null if not found)
     * @throws Exception on error
     */
    public GuestDetails retrieveGuestDetailsFromLH( String bookingRef, Date checkinDate ) throws Exception {
        String pageURL = bookingsScraper.getBookingsURLForArrivalsByDate( checkinDate, checkinDate, bookingRef, null );
        LOGGER.info( "Loading bookings page: " + pageURL );
        WebDriver driver = driverFactory.borrowObject();

        try {
            driver.get( pageURL );
            WebDriverWait wait = new WebDriverWait( driver, Duration.ofSeconds( MAX_WAIT_SECONDS ) );

            // select only row and click
            WebElement bookingLink = driver.findElement( By.xpath( "//a[text()='" + bookingRef + "']" ) );
            //wait.until( visibilityOf( bookingLink ) );
            bookingLink.click();

            // wait for header
            WebElement reservationHeader = driver.findElement( By.xpath( "//h4[starts-with(.,'Edit Reservation')]" ) );
            //wait.until( visibilityOf( reservationHeader ) );

            ensureCardDetailsVisible( driver, wait );

            String lhBookingRef = getBookingRef( reservationHeader.getText() );
            if ( false == bookingRef.equals( lhBookingRef ) ) {
                throw new UnrecoverableFault( "booking reference " + bookingRef + " does not match " );
            }

            GuestDetails guestDetails = new GuestDetails();
            guestDetails.setFirstName( driver.findElement( By.id( "guest_first_name" ) ).getAttribute( "value" ) );
            guestDetails.setLastName( driver.findElement( By.id( "guest_last_name" ) ).getAttribute( "value" ) );

            String cardNumber = driver.findElement( By.id( "payment_card_number" ) ).getAttribute( "value" );
            if ( StringUtils.isNotBlank( cardNumber ) ) {
                CardDetails cardDetails = new CardDetails();
                cardDetails.setName( driver.findElement( By.id( "payment_card_name" ) ).getAttribute( "value" ) );
                cardDetails.setCardNumber( cardNumber );
                String expiryMonth = new Select( driver.findElement( By.id( "payment_card_expiry_month" ) ) ).getFirstSelectedOption().getAttribute( "value" );
                String expiryYear = new Select( driver.findElement( By.id( "payment_card_expiry_year" ) ) ).getFirstSelectedOption().getAttribute( "value" );
                cardDetails.setExpiry( StringUtils.leftPad( expiryMonth, 2, "0" ) + expiryYear.substring( 2 ) );
                guestDetails.setCardDetails( cardDetails );
            }

            return guestDetails;
        }
        finally {
            driverFactory.returnObject( driver );
        }
    }

    /**
     * Verify that the current bookings page has the card details elements visible. Throw an
     * exception if not.
     * 
     * @param driver WebDriver to use
     * @param wait driver wait to use
     * @throws IOException on mail lookup error
     */
    private void ensureCardDetailsVisible( WebDriver driver, WebDriverWait wait ) throws IOException {
        try {
            // speed it up; we should already be on the correct screen
            driver.manage().timeouts().implicitlyWait( 5, TimeUnit.SECONDS );
            WebElement viewCardDetailsAnchor = driver.findElement( By.xpath( "//a[@class='view-card-details']" ) );
            LOGGER.info( "Card details hidden!" );
            driver.manage().timeouts().implicitlyWait( MAX_WAIT_SECONDS, TimeUnit.SECONDS );
            viewCardDetailsAnchor.click();

            // wait for id='security_token'
            WebElement securityTokenInput = driver.findElement( By.id( "security_token" ) );
            WebElement confirmAnchor = driver.findElement( By.xpath( "//a[text()='Confirm']" ) );

            //wait.until( visibilityOfAllElements( securityTokenInput, confirmAnchor ) );

            // enter confirmation
            securityTokenInput.sendKeys( gmailService.fetchLHSecurityToken() );
            confirmAnchor.click();

            // wait until reservations form is visible again
            //wait.until( stalenessOf( confirmAnchor ) );
        }
        catch ( NoSuchElementException ex ) {
            LOGGER.info( "View card details anchor not present. I guess we can see the card details..." );
        }
    }

    /**
     * Retrieves to booking reference from the given reservation page.
     * 
     * @param reservationHeader the header to check
     * @return non-null booking reference
     * @throws UnrecoverableFault if booking ref not found
     */
    private String getBookingRef( String reservationHeader ) throws UnrecoverableFault {

        Pattern p = Pattern.compile( "Edit Reservation - (.*?) for (.*)" );
        Matcher m = p.matcher( reservationHeader );
        String bookingRef;
        if ( m.find() ) {
            bookingRef = m.group( 1 );
        }
        else {
            throw new UnrecoverableFault( "Unable to determine booking reference" );
        }
        LOGGER.info( "Loaded reservation " + bookingRef + " for " + m.group( 2 ) );
        return bookingRef;
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
            WebElement emailInput = driver.findElement( By.id( "email" ) );
            emailInput.sendKeys( username );

            WebElement nextButton = driver.findElement( By.xpath( "//button[@type='submit']" ) );
            nextButton.click();

            wait.until( presenceOfElementLocated( By.id( "okta-signin-password" ) ) );
            WebElement passwordInput = driver.findElement( By.id( "okta-signin-password" ) );
            passwordInput.sendKeys( password );

            WebElement rememberMe = driver.findElement( By.name( "remember" ) );
            rememberMe.click();

            nextButton = driver.findElement( By.id( "okta-signin-submit" ) );
            nextButton.click();
            wait.until( urlContains( "/signin/verify/google" ) ); // FIXME: assumes always google authenticator

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

            WebElement rememberDevice = driver.findElement( By.name( "rememberDevice" ) );
            rememberDevice.click();

            nextButton = driver.findElement( By.xpath( "//input[@data-type='save']" ) );
            nextButton.click();

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
            dao.setOption( "hbo_cloudbeds_cookies",
                    driver.manage().getCookies().stream()
                            .map( c -> c.getName() + "=" + c.getValue() )
                            .collect( Collectors.joining( ";" ) ) );
            dao.setOption( "hbo_cloudbeds_useragent",
                    (String) ( (JavascriptExecutor) driver ).executeScript( "return navigator.userAgent;" ) );
        }
    }
}
