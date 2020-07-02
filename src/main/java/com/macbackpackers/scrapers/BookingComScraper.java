
package com.macbackpackers.scrapers;

import static org.openqa.selenium.support.ui.ExpectedConditions.stalenessOf;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.text.ParseException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.SystemUtils;
import org.openqa.selenium.By;
import org.openqa.selenium.NoSuchElementException;
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
        driver.get( lasturl == null ? "https://admin.booking.com/hotel/hoteladmin/" : lasturl );
        LOGGER.info( "Loading Booking.com website: " + driver.getCurrentUrl() );

        if ( driver.getCurrentUrl().startsWith( "https://account.booking.com/sign-in" ) ) {
            LOGGER.info( "Doesn't look like we're logged in. Logging into Booking.com" );
            doLoginForm( driver, wait, username, password );
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
     * Performs sign-in from the sign-in screen.
     * 
     * @param driver web client to use
     * @param wait
     * @param username user credentials
     * @param password user credentials
     */
    private void doLoginForm( WebDriver driver, WebDriverWait wait, String username, String password ) {

        WebElement usernameField = findElement( driver, wait, By.id( "loginname" ) );
        usernameField.sendKeys( wordPressDAO.getOption( "hbo_bdc_username" ) );
        findElement( driver, wait, By.xpath( "//span[text()='Next']/.." ) ).click();
        WebElement passwordField = findElement( driver, wait, By.id( "password" ) );
        passwordField.sendKeys( wordPressDAO.getOption( "hbo_bdc_password" ) );

        WebElement nextButton = findElement( driver, wait, By.xpath( "//span[text()='Sign in']/.." ) );
        nextButton.click();
        wait.until( stalenessOf( nextButton ) );

        if ( driver.getCurrentUrl().startsWith( "https://account.booking.com/sign-in/verification" ) ||
                driver.getCurrentUrl().startsWith( "https://secure-admin.booking.com/2fa/" ) ) {
            // if this is the first time we're doing this, we'll need to go thru 2FA
            LOGGER.info( "BDC verification required" );
            List<WebElement> phoneLinks = driver.findElements( By.xpath( "//a[contains(@class, 'nw-call-verification-link')] | //input[@value='call']" ) );
            List<WebElement> smsLinks = driver.findElements( By.xpath( "//a[contains(@class, 'nw-sms-verification-link')] | //input[@value='sms']" ) );

            String verificationMode = wordPressDAO.getOption( "hbo_bdc_verificationmode" );
            if ( "sms".equalsIgnoreCase( verificationMode ) && smsLinks.size() > 0 ) {
                LOGGER.info( "Performing SMS verification" );
                smsLinks.get( 0 ).click();
                WebElement selectedPhone = driver.findElement( By.xpath( "//*[@id='selected_phone'] | //select[@name='phone_id_sms']" ) );
                if ( false == selectedPhone.getText().trim().endsWith( "4338" ) ) {
                    throw new MissingUserDataException( "Phone number not registered: " + selectedPhone.getText() );
                }

                driver.findElement( By.xpath( "//span[text()='Send verification code'] "
                        + "| //div[contains(@class,'cta-phone')]/input[@value='Send text message']" ) ).click();

                // now blank out the code and wait for it to appear
                findElement( driver, wait, By.xpath( "//*[@id='sms_code' or @id='ask_pin_input']" ) ).sendKeys( fetch2FACode() );

                nextButton = driver.findElement( By.xpath( "//span[text()='Verify now']/.. | //div[contains(@class,'ctas')]/input[@value='Verify now']" ) );
                nextButton.click();
                wait.until( stalenessOf( nextButton ) );
            }
            else if ( "phone".equalsIgnoreCase( verificationMode ) && phoneLinks.size() > 0 ) {
                LOGGER.info( "Performing phone verification" );
                phoneLinks.get( 0 ).click();
                nextButton = driver.findElement( By.xpath( "//span[text()='Call now']/.." ) );
                nextButton.click();

                findElement( driver, wait, By.xpath( "//*[@id='sms_code' or @id='ask_pin_input']" ) ).sendKeys( fetch2FACode() );

                nextButton = driver.findElement( By.xpath( "//span[text()='Verify now']/.. | //div[contains(@class,'ctas')]/input[@value='Verify now']" ) );
                nextButton.click();
                wait.until( stalenessOf( nextButton ) );
            }
            else {
                throw new MissingUserDataException( "Verification required for BDC?" );
            }
        }

        if ( driver.getCurrentUrl().startsWith( "https://account.booking.com/sign-in" ) ) {
            throw new MissingUserDataException( "Failed to login to BDC" );
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
        loadReservationFromSearchTab( driver, wait, reservationId );

        wait.until( ExpectedConditions.or(
                ExpectedConditions.titleContains( "Reservation Details" ),
                ExpectedConditions.titleContains( "Reservation details" ) ) );
        LOGGER.info( "Loaded " + driver.getCurrentUrl() );

        // multiple places where the booking reference can appear; it should be in one of these
        LOGGER.info( "Looking up reservation ID by hidden field." );
        By BOOKING_NUMBER_XPATH = By.xpath( "//input[@type='hidden' and @name='res_id'] "
                + "| //p/span[text()='Booking number:']/../following-sibling::p "
                + "| //div[not(contains(@class, 'hidden-print'))]/span[normalize-space(text())='Booking number:']/following-sibling::span" );
        wait.until( ExpectedConditions.visibilityOfElementLocated( BOOKING_NUMBER_XPATH ) );
        WebElement bookingNumberField = driver.findElement( BOOKING_NUMBER_XPATH );
        String resIdFromPage = "input".equals( bookingNumberField.getTagName() ) ? bookingNumberField.getAttribute( "value" ) : bookingNumberField.getText();

        if ( false == reservationId.equals( resIdFromPage ) ) {
            LOGGER.error( "Reservation ID mismatch?!: Expected " + reservationId + " but found " + resIdFromPage );
            LOGGER.info( driver.getPageSource() );
            File scrFile = ((TakesScreenshot) driver).getScreenshotAs( OutputType.FILE );
            String filename = "logs/bdc_reservation_" + reservationId + ".png";
            FileUtils.copyFile( scrFile, new File( filename ) );
            LOGGER.info( "Screenshot written to " + filename );
            throw new IOException( "Unable to load reservation details. Reservation ID mismatch!" );
        }
    }

    /**
     * Goes to the Reservation (search) tab to lookup a reservation (for older bookings).
     * @param driver
     * @param wait
     * @param reservationId BDC reservation
     * @throws IOException
     */
    private void loadReservationFromSearchTab( WebDriver driver, WebDriverWait wait, String reservationId ) throws IOException {
        LOGGER.info( "Loading Reservations tab" );
        WebElement reservationsAnchor = driver.findElement( By.xpath( "//nav//a/span[normalize-space(text())='Reservations']/.." ) );
        driver.get( reservationsAnchor.getAttribute( "href" ) );
        LOGGER.info( "Entering checkin date to 6 months prior" );
        WebElement dateFrom = driver.findElement( By.id( "date_from" ) );
        dateFrom.click();
        dateFrom.clear();
        dateFrom.sendKeys( LocalDate.now().minusMonths( 6 ).format( DateTimeFormatter.ISO_DATE ) );
        LOGGER.info( "Setting keyword(s) to reservation ID" );
        WebElement keywords = driver.findElement( By.xpath( "//input[@name='term']" ) );
        keywords.click();
        keywords.sendKeys( reservationId );
        LOGGER.info( "Clicking on 'Show'" );
        driver.findElement( By.xpath( "//button/*/span[normalize-space(text())='Show']/.." ) ).click();

        // either we get a span with "Oops, no results." or we get a table of results
        By SEARCH_RESULTS_XPATH = By.xpath( "//span[contains(text(),'Oops, no results.')] "
                + "| //a[contains(@href, 'res_id=" + reservationId + "')]" );
        wait.until( ExpectedConditions.visibilityOfElementLocated( SEARCH_RESULTS_XPATH ) );
        WebElement searchResult = driver.findElement( SEARCH_RESULTS_XPATH );
        if ( "span".equals( searchResult.getTagName() ) ) {
            throw new IOException( "Reservation " + reservationId + " not found." );
        }
        LOGGER.info( "Reservation found. Redirecting to " + searchResult.getAttribute( "href" ) );
        driver.get( searchResult.getAttribute( "href" ) ); // click would open a new tab
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

        // payment details loaded by javascript
        final By PAYMENT_DETAILS_BLOCK = By.xpath( "//span[normalize-space(text())='Virtual credit card'] | //span[contains(text(),'successfully charged')]" );
        wait.until( ExpectedConditions.visibilityOfElementLocated( PAYMENT_DETAILS_BLOCK ) );

        try {
            // two different views of a booking? this one is from CRH
            LOGGER.info( "Looking up VCC balance (attempt 1)" );
            return fetchVccBalanceFromPage( driver,
                    By.xpath( "//p[@class='reservation_bvc--balance']" ),
                    Pattern.compile( "Virtual card balance: £(\\d+\\.?\\d*)" ),
                    e -> e.getText() );
        }
        catch ( NoSuchElementException ex ) {
            try { // this view is from HSH
                LOGGER.info( "Looking up VCC balance (attempt 2)" );
                return fetchVccBalanceFromPage( driver,
                        By.xpath( "//div/span[normalize-space(text())='Virtual card balance:']/../following-sibling::div" ),
                        Pattern.compile( "£(\\d+\\.?\\d*)" ),
                        e -> getTextNode( e ) ); // remove any subelements in case of a partial charge
            }
            catch ( NoSuchElementException ex2 ) {
                LOGGER.info( "Unable to find VCC amount to charge." );
                // the next line will either a) display that we have already fully charged the VCC or
                // b) throw an exception if we can't find anything wrt the VCC amount to charge
                LOGGER.info( "Looks like we've already charged it! BDC message: "
                        + driver.findElement( By.xpath( "//div[contains(@class, 'fully_charged')] | //span[contains(text(),'successfully charged the total amount')]" ) ).getText() );
                return BigDecimal.ZERO;
            }
        }
    }

    private BigDecimal fetchVccBalanceFromPage( WebDriver driver, By path, Pattern p, Function<WebElement, String> fn ) {
        String totalAmount = fn.apply( driver.findElement( path ) );

        LOGGER.info( "Matched " + totalAmount + " from " + path );
        LOGGER.info( "Attempting match with pattern " + p.pattern() );
        Matcher m = p.matcher( totalAmount );
        if ( m.find() == false ) {
            throw new NoSuchElementException( "Couldn't find virtual card balance from '" + totalAmount );
        }
        LOGGER.info( "Found VCC balance of " + m.group( 1 ) );
        return new BigDecimal( m.group( 1 ) );
    }

    /**
     * Takes a parent element and strips out the textContent of all child elements and returns
     * textNode content only
     * 
     * @param e the parent element
     * @return the text from the child textNodes
     */
    private static String getTextNode( WebElement e ) {
        String text = e.getText().trim();
        List<WebElement> children = e.findElements( By.xpath( "./*" ) );
        for ( WebElement child : children ) {
            text = text.replaceFirst( child.getText(), "" ).trim();
        }
        return text;
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

        // payment details loaded by javascript
        final By PAYMENT_DETAILS_BLOCK = By.xpath( "//span[normalize-space(text())='Virtual credit card'] "
                + "| //span[contains(text(),'successfully charged')] "
                + "| //span[contains(text(),'This virtual card is no longer active')] "
                + "| //span[contains(text(),\"This virtual card isn't active anymore\")] "
                + "| //a/span/span[normalize-space(text())='View credit card details']" );
        wait.until( ExpectedConditions.visibilityOfElementLocated( PAYMENT_DETAILS_BLOCK ) );

        List<WebElement> headerViewCCDetails = driver.findElements( By.xpath( "//*[self::button or self::a]/span/span[normalize-space(text())='View credit card details']/../.." ) );
        LOGGER.info( "Matched " + headerViewCCDetails.size() + " sign-in elements" );
        if ( headerViewCCDetails.isEmpty() ) {
            WebElement paymentDetails = driver.findElement( PAYMENT_DETAILS_BLOCK );
            if ( paymentDetails.getText().contains( "This virtual card is no longer active." )
                    || paymentDetails.getText().contains( "This virtual card isn't active anymore" ) ) {
                throw new MissingUserDataException( "This virtual card is no longer active." );
            }
            throw new MissingUserDataException( "No card details link available." );
        }

        LOGGER.info( "Found {} view CC details elements.", headerViewCCDetails.size() );
        String nextLink = headerViewCCDetails.get( 0 ).getAttribute( "href" );
        if ( StringUtils.isNotBlank( nextLink ) ) {
            LOGGER.info( "Link found, going to " + nextLink );
            driver.get( nextLink );
        }
        else {
            LOGGER.info( "Clicking on View CC details." );
            headerViewCCDetails.get( 0 ).click();
        }

        // we may or may not need to login again to view CC details
        final String SIGN_IN_LOCATOR_PATH = "//span[normalize-space(text())='Sign in to view credit card details']";
        final String CC_DETAILS_PATH = "//th[contains(text(),'Credit Card Details')] | //th[contains(text(),'Credit card details')]";
        final String CC_DETAILS_NOT_AVAIL_PATH = "//h2[contains(text(),\"credit card details aren't available\")]";
        final String CONTINUE_WITH_CC_DETAILS = "//p[normalize-space(text())='Continue to view the credit card details.']";
        final WebElement locator = wait.until( ExpectedConditions.visibilityOfElementLocated( By.xpath( 
                SIGN_IN_LOCATOR_PATH + " | " + CC_DETAILS_PATH + " | " + CC_DETAILS_NOT_AVAIL_PATH + " | " + CONTINUE_WITH_CC_DETAILS ) ) );

        Optional<Runnable> CLOSE_WINDOW_TASK = Optional.empty();
        if ( "h2".equals( locator.getTagName() ) ) {
            throw new MissingUserDataException( "Credit card details aren't available." );
        }
        else if ( "p".equals( locator.getTagName() ) ) {
            doLoginForm( driver, wait, wordPressDAO.getOption( "hbo_bdc_username" ), wordPressDAO.getOption( "hbo_bdc_password" ) );
        }
        else if ( "span".equals( locator.getTagName() ) ) {
            // the following should open a new window; switch to new window
            LOGGER.info( "Logging in again to view CC details..." );
            final String CURRENT_WINDOW = driver.getWindowHandle();
            driver.findElement( By.xpath( SIGN_IN_LOCATOR_PATH ) ).click();
            wait.until( ExpectedConditions.numberOfWindowsToBe( 2 ) );

            // switch to other window
            driver.getWindowHandles().stream()
                .filter( w -> false == CURRENT_WINDOW.equals( w ) )
                .findFirst()
                .map( w -> driver.switchTo().window( w ) )
                .orElseThrow( () -> new IOException("Unable to find new login window.") );

            // login again
            doLoginForm( driver, wait, wordPressDAO.getOption( "hbo_bdc_username" ), wordPressDAO.getOption( "hbo_bdc_password" ) );

            CLOSE_WINDOW_TASK = Optional.of( () -> {
                driver.findElement( By.xpath( "//div[contains(@class,'sbm')]/button[normalize-space(text())='Close']" ) ).click();
                driver.switchTo().window( CURRENT_WINDOW ); // switch back to main tab
            } );
        }

        wait.until( ExpectedConditions.visibilityOfElementLocated( By.xpath( CC_DETAILS_PATH ) ) );
        CardDetails cardDetails = new CardDetails();
        cardDetails.setName( driver.findElement( By.xpath( "//td[text()=\"Card holder's name:\"]/following-sibling::td" ) ).getText().trim() );
        cardDetails.setCardNumber( driver.findElement( By.xpath( "//td[text()='Card number:']/following-sibling::td" ) ).getText().replaceAll("\\s", "") );
        cardDetails.setCardType( driver.findElement( By.xpath( "//td[text()='Card type:']/following-sibling::td" ) ).getText().trim() );
        cardDetails.setExpiry( parseExpiryDate( driver.findElement( By.xpath( "//td[contains(text(),'Expiration')]/following-sibling::td" ) ).getText().trim() ) );
        cardDetails.setCvv( StringUtils.trimToNull( driver.findElement( By.xpath( "//td[contains(text(),'CVC')]/following-sibling::td" ) ).getText() ) );
        LOGGER.info( "Retrieved card: " + new BasicCardMask().applyCardMask( cardDetails.getCardNumber() ) + " for " + cardDetails.getName() );

        // we're done here; close the newly opened window
        CLOSE_WINDOW_TASK.ifPresent( t -> t.run() );
        return cardDetails;
    }

    /**
     * Searches for all prepaid bookings between the given dates with VCC details and can be charged
     * immediately.
     * 
     * @param driver
     * @param wait
     * @return non-null list of BDC booking refs
     * @throws IOException
     */
    public List<String> getAllVCCBookingsThatCanBeCharged( WebDriver driver, WebDriverWait wait ) throws IOException {
        doLogin( driver, wait );
        LOGGER.info( "Loading Reservations tab" );
        WebElement reservationsAnchor = driver.findElement( By.xpath( "//nav//a/span[normalize-space(text())='Reservations']/.." ) );
        driver.get( reservationsAnchor.getAttribute( "href" ) );

        LOGGER.info( "Show uncharged virtual cards..." ); // BDC goes all wonky if not done in this order
        By VIEW_UNCHARGED_VCC_BUTTON = By.xpath( "//button[contains(@class, 'uncharged-vccs-button')]" );
        wait.until( ExpectedConditions.visibilityOfElementLocated( VIEW_UNCHARGED_VCC_BUTTON ) );
        WebElement viewUnchargedVirtualCards = driver.findElement( VIEW_UNCHARGED_VCC_BUTTON );
        viewUnchargedVirtualCards.click();

        // either we get a span with "Oops, no results." or we get a table of results
        By SEARCH_RESULTS_XPATH = By.xpath( "//span[contains(text(),'No results')] "
                + "| //th/a[contains(@class, 'bui-link')]" );
        wait.until( ExpectedConditions.visibilityOfElementLocated( SEARCH_RESULTS_XPATH ) );
        WebElement searchResult = driver.findElement( SEARCH_RESULTS_XPATH );
        if ( searchResult.getText().contains( "No results" ) ) {
            LOGGER.info( "No results.. nothing to do." );
            return Collections.emptyList();
        }

        List<String> reservationIds = new ArrayList<>();
        reservationIds.addAll( driver.findElements( By.xpath( "//td[@data-heading='Booking number']" ) )
                .stream().map( a -> a.getText() ).collect( Collectors.toList() ) );
        return reservationIds;
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

    /**
     * Waits until element is visible and returns it.
     * 
     * @param wait
     * @param by
     * @return visible element
     */
    private WebElement findElement( WebDriver driver, WebDriverWait wait, By by ) {
        wait.until( ExpectedConditions.visibilityOfElementLocated( by ) );
        return driver.findElement( by );
    }

}
