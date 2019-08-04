
package com.macbackpackers.scrapers;

import static org.openqa.selenium.support.ui.ExpectedConditions.stalenessOf;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.io.FileUtils;
import org.openqa.selenium.By;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.Select;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.macbackpackers.dao.WordPressDAO;
import com.macbackpackers.exceptions.MissingUserDataException;
import com.macbackpackers.services.FileService;

@Component
public class BookingComScraper {

    private final Logger LOGGER = LoggerFactory.getLogger( getClass() );

    /** for saving login credentials */
    private static final String COOKIE_FILE = "booking.com.cookies";

    @Autowired
    private FileService fileService;

    @Autowired
    private WordPressDAO wordPressDAO;

    // enable for 2fa verification
    private boolean doVerification = false;

    /**
     * Logs into BDC providing the necessary credentials.
     * 
     * @param driver web client
     * @param wait
     * @throws IOException 
     */
    public void doLogin( WebDriver driver, WebDriverWait wait ) throws IOException {
        doLogin( driver, wait,
                wordPressDAO.getOption( "hbo_bdc_username" ),
                wordPressDAO.getOption( "hbo_bdc_password" ),
                wordPressDAO.getOption( "hbo_bdc_session" ) );
    }

    /**
     * Logs into BDC with the necessary credentials.
     * 
     * @param driver web client to use
     * @param wait
     * @param username user credentials
     * @param password user credentials
     * @param session previous session id (if available)
     * @throws IOException 
     */
    public void doLogin( WebDriver driver, WebDriverWait wait, String username, String password, String session ) throws IOException {

        if ( username == null || password == null ) {
            throw new MissingUserDataException( "Missing BDC username/password" );
        }

        // we need to navigate first before loading the cookies for that domain
        driver.get( "https://www.booking.com/" );
        if ( new File( COOKIE_FILE ).exists() ) {
            fileService.loadCookiesFromFile( driver, COOKIE_FILE );
        }

        driver.get( "https://admin.booking.com/hotel/hoteladmin/general/dashboard.html?lang=en&hotel_id="
                + username + (session == null ? "" : "&ses=" + session) );

        if ( driver.getCurrentUrl().startsWith( "https://account.booking.com/sign-in" ) ) {
            LOGGER.info( "Doesn't look like we're logged in. Logging into Booking.com" );

            WebElement usernameField = driver.findElement( By.id( "loginname" ) );
            usernameField.sendKeys( username );

            WebElement nextButton = driver.findElement( By.xpath( "//span[text()='Next']/.." ) );
            nextButton.click();

            // wait until password field is visible
            wait.until( ExpectedConditions.visibilityOfElementLocated( By.id( "password" ) ) );
            WebElement passwordField = driver.findElement( By.id( "password" ) );
            passwordField.sendKeys( password );

            nextButton = driver.findElement( By.xpath( "//span[text()='Sign in']/.." ) );
            nextButton.click();
            wait.until( stalenessOf( nextButton ) );

            List<WebElement> phoneLinks = driver.findElements( By.xpath( "//a[contains(@class, 'nw-call-verification-link')]" ) );
            if ( phoneLinks.size() > 0 && false == doVerification ) {
                throw new MissingUserDataException( "Verification required for BDC?" );
            }
            else if ( doVerification ) {
                // if this is the first time we're doing this, we'll need to go thru 2FA
                phoneLinks.get( 0 ).click();
                nextButton = driver.findElement( By.xpath( "//span[text()='Call now']/.." ) );
                nextButton.click();

                WebElement smsCode = driver.findElement( By.id( "sms_code" ) );
                smsCode.sendKeys( "XXXXXXX" ); // SET BREAKPOINT HERE

                nextButton = driver.findElement( By.xpath( "//span[text()='Verify now']/.." ) );
                nextButton.click();
                wait.until( stalenessOf( nextButton ) );
                LOGGER.info( driver.getPageSource() );
            }

        }

        // if we're actually logged in, we should get the hostel name identified here...
        LOGGER.info( "Property name identified as: " + driver.getTitle() );

        // save credentials to disk so we don't need to do this again
        fileService.writeCookiesToFile( driver, COOKIE_FILE );
        LOGGER.info( "Logged into Booking.com. Saving current session." );
        LOGGER.info( "Loaded " + driver.getCurrentUrl() );
        updateSession( driver.getCurrentUrl() );
    }

    /**
     * Looks up a given reservation in BDC.
     * 
     * @param driver
     * @param wait
     * @param reservationId the BDC reference
     * @throws IOException 
     */
    public void lookupReservation( WebDriver driver, WebDriverWait wait, String reservationId ) throws IOException {
        doLogin( driver, wait );

        WebElement searchInput = driver.findElement( By.xpath( "//input[@placeholder='Search for reservations']" ) );
        searchInput.sendKeys( reservationId + "\n" );

        final By BY_RESERVATION_ANCHOR = By.xpath( "//a[@data-id='" + reservationId + "']" );
        wait.until( ExpectedConditions.visibilityOfElementLocated( BY_RESERVATION_ANCHOR ) );

        WebElement searchAnchor = driver.findElement( BY_RESERVATION_ANCHOR );
        driver.get( searchAnchor.getAttribute( "href" ) );

        LOGGER.info( "Loaded " + driver.getCurrentUrl() );
        LOGGER.debug( driver.getPageSource() );

        if ( false == driver.getTitle().contains( "Reservation details" ) ) {
            File scrFile = ((TakesScreenshot) driver).getScreenshotAs( OutputType.FILE );
            FileUtils.copyFile( scrFile, new File( "logs/bdc_reservation_" + reservationId + ".png" ) );
            throw new IOException( "Unable to load reservation details" );
        }
    }

    /**
     * Looks up a given reservation in BDC and returns the total amount for the booking.
     * 
     * @param driver
     * @param wait
     * @param reservationId the BDC reference
     * @throws Exception if booking not found or I/O error
     */
    public BigDecimal getTotalAmountFromReservation( WebDriver driver, WebDriverWait wait, String reservationId ) {
        WebElement totalAmount = driver.findElement( By.xpath( "//span[text() = 'Commissionable amount:']/following-sibling::span" ) );
        return new BigDecimal( totalAmount.getText().replaceAll( "£", "" ) );
    }

    /**
     * Mark credit card for the given reservation as invalid.
     * 
     * @param driver
     * @param wait
     * @param reservationId BDC reservation
     * @param last4Digits last 4 digits of CC
     * @throws IOException
     */
    public void markCreditCardAsInvalid( WebDriver driver, WebDriverWait wait, String reservationId, String last4Digits ) throws IOException {
        lookupReservation( driver, wait, reservationId );
        LOGGER.info( "Marking card ending in " + last4Digits + " as invalid for reservation " + reservationId );

        List<WebElement> headerMarkInvalid = driver.findElements( By.id( "mark_invalid_new_icc" ) );
        if ( headerMarkInvalid.isEmpty() ) {
            LOGGER.info( "Link not available (or already marked invalid). Nothing to do..." );
            return;
        }
        driver.findElement( By.id( "mark_invalid_new_icc" ) ).click();
        wait.until( ExpectedConditions.visibilityOfElementLocated( By.name( "ccnumber" ) ) );

        WebElement last4DigitsInput = driver.findElement( By.name( "ccnumber" ) );
        last4DigitsInput.sendKeys( last4Digits );

        Select cardInvalidSelect = new Select( driver.findElement( By.name( "cc_invalid_reason" ) ) );
        cardInvalidSelect.selectByValue( "declined" );

        WebElement depositRadio = driver.findElement( By.xpath( "//input[@type='radio' and @name='preprocess_type' and @value='deposit']/following-sibling::span" ) );
        depositRadio.click();

        Select amountTypeSelect = new Select( driver.findElement( By.name( "amount_type" ) ) );
        amountTypeSelect.selectByValue( "first" );

        driver.findElement( By.xpath( "//button/span[text()='Confirm']/.." ) ).click();
        LOGGER.info( "Card marked as invalid." );
    }

    /**
     * Updates the current session ID when logged into BDC.
     * 
     * @param url currently logged in BDC page.
     */
    private void updateSession( String url ) {
        Pattern p = Pattern.compile( "ses=([0-9a-f]+)" );
        Matcher m = p.matcher( url );
        if ( m.find() ) {
            wordPressDAO.setOption( "hbo_bdc_session", m.group( 1 ) );
        }
        else {
            throw new MissingUserDataException( "Unable to find session id from " + url );
        }
    }
}
