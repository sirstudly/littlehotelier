
package com.macbackpackers.scrapers;

import static org.openqa.selenium.support.ui.ExpectedConditions.stalenessOf;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.text.ParseException;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.SystemUtils;
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

import com.macbackpackers.beans.CardDetails;
import com.macbackpackers.dao.WordPressDAO;
import com.macbackpackers.exceptions.MissingUserDataException;
import com.macbackpackers.services.BasicCardMask;

@Component
public class BookingComScraper {

    private final Logger LOGGER = LoggerFactory.getLogger( getClass() );

    @Autowired
    private WordPressDAO wordPressDAO;

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
                wordPressDAO.getOption( "hbo_bdc_password" ) );
    }

    /**
     * Logs into BDC with the necessary credentials.
     * 
     * @param driver web client to use
     * @param wait
     * @param username user credentials
     * @param password user credentials
     * @param lasturl previous home URL (optional) - contains previous session
     * @throws IOException
     */
    public synchronized void doLogin( WebDriver driver, WebDriverWait wait, String username, String password ) throws IOException {

        if ( username == null || password == null ) {
            throw new MissingUserDataException( "Missing BDC username/password" );
        }

        // don't use session-tracked URL if running locally (ie. for debugging)
        String lasturl = SystemUtils.IS_OS_WINDOWS ? null : wordPressDAO.getOption( "hbo_bdc_lasturl" );
        driver.get( lasturl == null ? "https://admin.booking.com/hotel/hoteladmin/general/dashboard.html?lang=en&hotel_id=" + username : lasturl );
        LOGGER.info( "Loading Booking.com website: " + driver.getCurrentUrl() );

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

            if ( driver.getCurrentUrl().startsWith( "https://account.booking.com/sign-in/verification" ) ) {
                // if this is the first time we're doing this, we'll need to go thru 2FA
                LOGGER.info( "BDC verification required" );
                List<WebElement> phoneLinks = driver.findElements( By.xpath( "//a[contains(@class, 'nw-call-verification-link')]" ) );
                List<WebElement> smsLinks = driver.findElements( By.xpath( "//a[contains(@class, 'nw-sms-verification-link')]" ) );

                String verificationMode = wordPressDAO.getOption( "hbo_bdc_verificationmode" );
                if ( "sms".equalsIgnoreCase( verificationMode ) && smsLinks.size() > 0 ) {
                    LOGGER.info( "Performing SMS verification" );
                    smsLinks.get( 0 ).click();
                    WebElement selectedPhone = driver.findElement( By.id( "selected_phone" ) );
                    if ( false == selectedPhone.getText().endsWith( "4338" ) ) {
                        throw new MissingUserDataException( "Phone number not registered: " + selectedPhone.getText() );
                    }

                    WebElement sendCodeBtn = driver.findElement( By.xpath( "//span[text()='Send verification code']" ) );
                    sendCodeBtn.click();

                    // now blank out the code and wait for it to appear
                    WebElement smsCode = driver.findElement( By.id( "sms_code" ) );
                    smsCode.sendKeys( fetch2FACode() );

                    nextButton = driver.findElement( By.xpath( "//span[text()='Verify now']/.." ) );
                    nextButton.click();
                    wait.until( stalenessOf( nextButton ) );
                    LOGGER.debug( driver.getPageSource() );
                }
                else if ( "phone".equalsIgnoreCase( verificationMode ) && phoneLinks.size() > 0 ) {
                    LOGGER.info( "Performing phone verification" );
                    phoneLinks.get( 0 ).click();
                    nextButton = driver.findElement( By.xpath( "//span[text()='Call now']/.." ) );
                    nextButton.click();

                    WebElement smsCode = driver.findElement( By.id( "sms_code" ) );
                    smsCode.sendKeys( fetch2FACode() );

                    nextButton = driver.findElement( By.xpath( "//span[text()='Verify now']/.." ) );
                    nextButton.click();
                    wait.until( stalenessOf( nextButton ) );
                    LOGGER.info( driver.getPageSource() );
                }
                else {
                    throw new MissingUserDataException( "Verification required for BDC?" );
                }
            }

            if ( driver.getCurrentUrl().startsWith( "https://account.booking.com/sign-in" ) ) {
                throw new MissingUserDataException( "Failed to login to BDC" );
            }

        }

        // if we're actually logged in, we should get the hostel name identified here...
        LOGGER.info( "Current URL: " + driver.getCurrentUrl() );
        LOGGER.info( "Property name identified as: " + driver.getTitle() );

        // verify we are logged in
        if ( false == driver.getCurrentUrl().startsWith( "https://admin.booking.com/hotel/hoteladmin/extranet_ng/manage/home.html" ) ) {
            LOGGER.info( "Current URL: " + driver.getCurrentUrl() );
            LOGGER.info( driver.getPageSource() );
            throw new MissingUserDataException( "Are we logged in? Unexpected URL." );
        }

        if ( false == SystemUtils.IS_OS_WINDOWS ) {
            LOGGER.info( "Logged into Booking.com. Saving current URL." );
            LOGGER.info( "Loaded " + driver.getCurrentUrl() );
            wordPressDAO.setOption( "hbo_bdc_lasturl", driver.getCurrentUrl() );
        }
    }

    /**
     * First _blanks out_ the 2FA code from the DB and waits for it to be re-populated. This is done
     * outside this application.
     * 
     * @return non-null 2FA code
     * @throws MissingUserDataException on timeout (1 + 10 minutes)
     */
    private String fetch2FACode() throws MissingUserDataException {
        // now blank out the code and wait for it to appear
        LOGGER.info( "waiting for hbo_bdc_2facode to be set..." );
        wordPressDAO.setOption( "hbo_bdc_2facode", "" );
        sleep( 60 );
        // force timeout after 10 minutes (60x10 seconds)
        for ( int i = 0 ; i < 60 ; i++ ) {
            String scaCode = wordPressDAO.getOption( "hbo_bdc_2facode" );
            if ( StringUtils.isNotBlank( scaCode ) ) {
                return scaCode;
            }
            LOGGER.info( "waiting for another 10 seconds..." );
            sleep( 10 );
        }
        throw new MissingUserDataException( "2FA code timeout waiting for BDC verification." );
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
        LOGGER.info( "Looking up reservation " + reservationId );

        WebElement searchInput = driver.findElement( By.xpath( "//input[@placeholder='Search for reservations']" ) );
        searchInput.sendKeys( reservationId + "\n" );

        final By BY_RESERVATION_ANCHOR = By.xpath( "//a[@data-id='" + reservationId + "']" );
        wait.until( ExpectedConditions.visibilityOfElementLocated( BY_RESERVATION_ANCHOR ) );

        WebElement searchAnchor = driver.findElement( BY_RESERVATION_ANCHOR );
        driver.get( searchAnchor.getAttribute( "href" ) );

        LOGGER.info( "Loaded " + driver.getCurrentUrl() );
        LOGGER.debug( driver.getPageSource() );

        if ( false == driver.getTitle().contains( "Reservation Details" ) ) {
            File scrFile = ((TakesScreenshot) driver).getScreenshotAs( OutputType.FILE );
            FileUtils.copyFile( scrFile, new File( "logs/bdc_reservation_" + reservationId + ".png" ) );
            throw new IOException( "Unable to load reservation details" );
        }

        WebElement bookingNumberField = driver.findElement( By.xpath( "//input[@type='hidden' and @name='res_id']" ) );
        if ( false == reservationId.equals( bookingNumberField.getAttribute( "value" ) ) ) {
            LOGGER.error( "Reservation ID mismatch?!: Expected " + reservationId + " but found " + bookingNumberField.getAttribute( "value" ) );
            throw new IOException( "Unable to load reservation details" );
        }
    }

    /**
     * Looks up a given reservation in BDC and returns the virtual card balance on the booking.
     * 
     * @param driver
     * @param wait
     * @param reservationId the BDC reference
     * @return the amount available on the VCC
     * @throws IOException if unable to login
     * @throws NoSuchElementException if VCC details not found
     */
    public BigDecimal getVirtualCardBalance( WebDriver driver, WebDriverWait wait, String reservationId ) throws IOException, NoSuchElementException {
        lookupReservation( driver, wait, reservationId );
        WebElement totalAmount = driver.findElement( By.xpath( "//p[@class='reservation_bvc--balance']" ) );
        Pattern p = Pattern.compile( "Virtual card balance: £(\\d+\\.?\\d*)" );
        Matcher m = p.matcher( totalAmount.getText() );
        if ( m.find() == false ) {
            throw new NoSuchElementException( "Couldn't find virtual card balance from '" + totalAmount.getText() );
        }
        LOGGER.info( "Found VCC balance of " + m.group( 1 ) );
        return new BigDecimal( m.group( 1 ) );
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

        WebElement confirmBtn = driver.findElement( By.xpath( "//button/span[contains(text(), 'Confirm')]" ) );
        confirmBtn.click();
        LOGGER.info( "Card marked as invalid." );
    }

    /**
     * Retrieves the card details for the given booking.
     * 
     * @param driver
     * @param wait
     * @param bdcReservation BDC reservation
     * @return credit card details
     * @throws IOException
     * @throws ParseException on parse error during retrieval
     * @throws MissingUserDataException if card details are missing
     */
    public CardDetails returnCardDetailsForBooking( WebDriver driver, WebDriverWait wait, String bdcReservation ) throws IOException, ParseException {
        lookupReservation( driver, wait, bdcReservation );

        List<WebElement> headerViewCCDetails = driver.findElements( By.id( "js-view-cc-details-button" ) );
        if ( headerViewCCDetails.isEmpty() ) {
            throw new MissingUserDataException( "No card details link not available." );
        }
        driver.findElement( By.id( "js-view-cc-details-button" ) ).click();
        final By SIGN_IN_LOCATOR = By.xpath( "//a[normalize-space(text())='Sign in to view credit card details']" );
        wait.until( ExpectedConditions.visibilityOfElementLocated( SIGN_IN_LOCATOR ) );

        // the following should open a new window; focus should move to the new window automatically
        LOGGER.info( "Logging in again to view CC details..." );
        final String CURRENT_WINDOW = driver.getWindowHandle();
        driver.findElement( SIGN_IN_LOCATOR ).click();
        wait.until( ExpectedConditions.numberOfWindowsToBe( 2 ) );

        // switch to other window
        driver.getWindowHandles().stream()
            .filter( w -> false == CURRENT_WINDOW.equals( w ) )
            .findFirst()
            .map( w -> driver.switchTo().window( w ) )
            .orElseThrow( () -> new IOException("Unable to find new login window.") );
        
        // login again
        WebElement usernameField = driver.findElement( By.id( "loginname" ) );
        usernameField.sendKeys( wordPressDAO.getOption( "hbo_bdc_username" ) );

        WebElement nextButton = driver.findElement( By.xpath( "//span[text()='Next']/.." ) );
        nextButton.click();

        // wait until password field is visible
        wait.until( ExpectedConditions.visibilityOfElementLocated( By.id( "password" ) ) );
        WebElement passwordField = driver.findElement( By.id( "password" ) );
        passwordField.sendKeys( wordPressDAO.getOption( "hbo_bdc_password" ) );

        nextButton = driver.findElement( By.xpath( "//span[text()='Sign in']/.." ) );
        nextButton.click();
        wait.until( stalenessOf( nextButton ) );

        wait.until( ExpectedConditions.visibilityOfElementLocated( By.xpath( "//th[normalize-space(text())='Credit Card Details']" ) ) );
        CardDetails cardDetails = new CardDetails();
        cardDetails.setName( driver.findElement( By.xpath( "//td[text()=\"Card holder's name:\"]/following-sibling::td" ) ).getText().trim() );
        cardDetails.setCardNumber( driver.findElement( By.xpath( "//td[text()='Card number:']/following-sibling::td" ) ).getText().trim() );
        cardDetails.setCardType( driver.findElement( By.xpath( "//td[text()='Card type:']/following-sibling::td" ) ).getText().trim() );
        cardDetails.setExpiry( parseExpiryDate( driver.findElement( By.xpath( "//td[text()='Expiration Date:']/following-sibling::td" ) ).getText().trim() ) );
        cardDetails.setCvv( StringUtils.trimToNull( driver.findElement( By.xpath( "//td[text()='CVC Code:']/following-sibling::td" ) ).getText() ) );
        LOGGER.info( "Retrieved card: " + new BasicCardMask().applyCardMask( cardDetails.getCardNumber() ) + " for " + cardDetails.getName() );

        // we're done here
        driver.findElement( By.xpath( "//button[text()='Close']" ) ).click();
        return cardDetails;
    }

    /**
     * Converts "MM / YYYY" to MMYY
     *
     * @param bdcExpiryFormat non-null expiry date
     * @return 4 digit expiry of format MMYY
     * @throws ParseException on parse failure
     */
    private static String parseExpiryDate( String bdcExpiryFormat ) throws ParseException {
        Pattern p = Pattern.compile( "(\\d{2})\\s*/\\s*\\d{2}(\\d{2})" );
        Matcher m = p.matcher( bdcExpiryFormat );
        if ( false == m.find() ) {
            throw new ParseException( "Unable to get card expiry date", 0 );
        }
        return m.group( 1 ) + m.group( 2 );
    }

    private void sleep( int seconds ) {
        try {
            Thread.sleep( seconds * 1000 );
        }
        catch ( InterruptedException e ) {
            // nothing to do
        }
    }
}
