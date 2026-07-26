package com.macbackpackers.scrapers;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.macbackpackers.beans.CardDetails;
import com.macbackpackers.dao.WordPressDAO;
import com.macbackpackers.exceptions.MissingUserDataException;
import com.macbackpackers.services.BasicCardMask;
import com.macbackpackers.services.PaymentProcessorService;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
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

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.text.MessageFormat;
import java.text.ParseException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.openqa.selenium.support.ui.ExpectedConditions.stalenessOf;
import static org.openqa.selenium.support.ui.ExpectedConditions.urlMatches;

@Component
public class BookingComSeleniumScraper {

    private final Logger LOGGER = LoggerFactory.getLogger( getClass() );

    /** Multi-property accounts land here; property {@code home.html} without a fresh {@code ses} can 400. */
    static final String BDC_GROUPS_HOME =
            "https://admin.booking.com/hotel/hoteladmin/groups/home/index.html";

    @Autowired
    private WordPressDAO wordPressDAO;

    /**
     * Logs into BDC providing the necessary credentials.
     *
     * @param driver web client
     * @param wait
     * @throws IOException
     */
    public void doLogin(WebDriver driver, WebDriverWait wait ) throws IOException {
        doLogin( driver, wait,
                wordPressDAO.getMandatoryOption( "hbo_bdc_username" ),
                wordPressDAO.getMandatoryOption( "hbo_bdc_password" ) );
    }

    /**
     * Logs into BDC with the necessary credentials.
     *
     * @param driver web client to use
     * @param wait
     * @param username user credentials
     * @param password user credentials
     * @throws IOException
     */
    public synchronized void doLogin( WebDriver driver, WebDriverWait wait, String username, String password ) throws IOException {

        if ( username == null || password == null ) {
            throw new MissingUserDataException( "Missing BDC username/password" );
        }

        final String bdcLastUrlOption = "Linux".equalsIgnoreCase( System.getProperty( "os.name" ) ) ? "hbo_bdc_lasturl" : "hbo_bdc_lasturl_dev";
        // Always open groups home for multi-property users. Do not reuse a saved property home.html
        // (stale/missing ses → HTTP 400). Persist groups home so DB lasturl stays safe.
        driver.get( BDC_GROUPS_HOME );
        LOGGER.info( "Loading Booking.com website: " + driver.getCurrentUrl() );

        if ( driver.getCurrentUrl().startsWith( "https://account.booking.com/sign-in" ) ) {
            LOGGER.info( "Doesn't look like we're logged in. Logging into Booking.com" );
            doLoginForm( driver, wait, username, password );
        }

        // if we're actually logged in, we should get the hostel name identified here...
        LOGGER.info( "Current URL: " + driver.getCurrentUrl() );
        LOGGER.info( "Property name identified as: " + driver.getTitle() );

        // verify we are logged in
        if ( false == driver.getCurrentUrl().startsWith( "https://admin.booking.com/hotel/hoteladmin/extranet_ng/manage/home.html" ) &&
                false == driver.getCurrentUrl().startsWith( "https://admin.booking.com/hotel/hoteladmin/groups/home/index.html" ) ) {
            LOGGER.info( "Current URL: " + driver.getCurrentUrl() );
            LOGGER.info( driver.getPageSource() );
            throw new MissingUserDataException( "Are we logged in? Unexpected URL." );
        }

        LOGGER.info( "Logged into Booking.com. Saving groups home as last URL." );
        wordPressDAO.setOption( bdcLastUrlOption, BDC_GROUPS_HOME );
        LOGGER.info( "Loaded " + driver.getCurrentUrl() );
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
        usernameField.sendKeys( username );
        findElement( driver, wait, By.xpath( "//span[text()='Next']/.." ) ).click();
        WebElement passwordField = findElement( driver, wait, By.id( "password" ) );
        passwordField.sendKeys( password );

        {
            final WebElement nextButton = findElement(driver, wait, By.xpath("//span[text()='Sign in']/.."));
            nextButton.click();
            wait.until(d -> stalenessOf(nextButton));
        }

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

                final WebElement nextButton = driver.findElement( By.xpath( "//span[text()='Verify now']/.. | //div[contains(@class,'ctas')]/input[@value='Verify now']" ) );
                nextButton.click();
                wait.until( d -> stalenessOf( nextButton ) );
            }
            else if ( "phone".equalsIgnoreCase( verificationMode ) && phoneLinks.size() > 0 ) {
                LOGGER.info( "Performing phone verification" );
                phoneLinks.get( 0 ).click();
                WebElement nextButton = driver.findElement( By.xpath( "//span[text()='Call now']/.." ) );
                nextButton.click();

                findElement( driver, wait, By.xpath( "//*[@id='sms_code' or @id='ask_pin_input']" ) ).sendKeys( fetch2FACode() );

                final WebElement verifyButton = driver.findElement( By.xpath( "//span[text()='Verify now']/.. | //div[contains(@class,'ctas')]/input[@value='Verify now']" ) );
                verifyButton.click();
                wait.until( d -> stalenessOf( verifyButton ) );
            }
            else {
                throw new MissingUserDataException( "Verification required for BDC?" );
            }
        }

        wait.until(d -> urlMatches("https://account.booking.com/sign-in.*"));
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
            String scaCode = wordPressDAO.getOptionNoCache( "hbo_bdc_2facode" );
            if ( StringUtils.isNotBlank( scaCode ) ) {
                return scaCode;
            }
            LOGGER.info( "waiting for another 10 seconds..." );
            sleep( 10 );
        }
        throw new MissingUserDataException( "2FA code timeout waiting for BDC verification." );
    }

    /**
     * Returns the session from the URL.
     * @param url
     * @return non-null URL
     * @throws NoSuchElementException if not found
     */
    private String getSessionFromURL( String url ) {
        Pattern p = Pattern.compile( "ses=([a-f\\d]+)" );
        Matcher m = p.matcher( url );
        if ( m.find() ) {
            return m.group( 1 );
        }
        throw new NoSuchElementException( "Couldn't find session from URL: " + url );
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

        String hotelId = wordPressDAO.getMandatoryOption( "hbo_bdc_hotel_id" );
        String ses = ensureSessionForHotel( driver, wait, hotelId );

        String reservationUrl = MessageFormat.format(
                "https://admin.booking.com/hotel/hoteladmin/extranet_ng/manage/booking.html?res_id={0}&ses={1}&lang=en&hotel_id={2}",
                reservationId, ses, hotelId );
        LOGGER.info( "Looking up reservation " + reservationId + " using URL " + reservationUrl );
        driver.get( reservationUrl );

        wait.until( d -> ExpectedConditions.or(
                ExpectedConditions.titleContains( "Reservation Details" ),
                ExpectedConditions.titleContains( "Reservation details" ) ) );
        LOGGER.info( "Loaded " + driver.getCurrentUrl() );

        // multiple places where the booking reference can appear; it should be in one of these
        LOGGER.info( "Looking up reservation ID by hidden field." );
        By BOOKING_NUMBER_XPATH = By.xpath( "//input[@type='hidden' and @name='res_id'] "
                + "| //p/span[text()='Booking number:']/../following-sibling::p "
                + "| //div[not(contains(@class, 'hidden-print'))]/span[normalize-space(text())='Booking number:']/following-sibling::span" );
        wait.until( d -> ExpectedConditions.visibilityOfElementLocated( BOOKING_NUMBER_XPATH ) );
        WebElement bookingNumberField = driver.findElement( BOOKING_NUMBER_XPATH );
        String resIdFromPage = "input".equals( bookingNumberField.getTagName() ) ? bookingNumberField.getAttribute( "value" ) : bookingNumberField.getText();

        if ( false == reservationId.equals( resIdFromPage ) ) {
            LOGGER.error( "Reservation ID mismatch?!: Expected " + reservationId + " but found " + resIdFromPage );
            LOGGER.info( driver.getPageSource() );
            File scrFile = ( (TakesScreenshot) driver ).getScreenshotAs( OutputType.FILE );
            String filename = "logs/bdc_reservation_" + reservationId + ".png";
            FileUtils.copyFile( scrFile, new File( filename ) );
            LOGGER.info( "Screenshot written to " + filename );
            throw new IOException( "Unable to load reservation details. Reservation ID mismatch!" );
        }
    }

    /**
     * Looks up a given reservation in BDC and returns the virtual card balance on the booking
     * via the fresa {@code get_reservation_payout} API.
     *
     * @param driver
     * @param wait
     * @param reservationId the BDC reference
     * @return the amount available on the VCC (zero if no chargeable balance)
     * @throws IOException if unable to login or payout API fails
     */
    public BigDecimal getVirtualCardBalance( WebDriver driver, WebDriverWait wait, String reservationId ) throws IOException {
        lookupReservation( driver, wait, reservationId );

        String hotelId = wordPressDAO.getMandatoryOption( "hbo_bdc_hotel_id" );
        String ses = getSessionFromURL( driver.getCurrentUrl() );
        String hotelAccountId = extractHotelAccountIdFromPage( driver );

        String apiUrl = MessageFormat.format(
                "https://admin.booking.com/fresa/extranet/reservations/details/get_reservation_payout?hres_id={0}&hotel_id={1}&ses={2}&lang=en{3}",
                reservationId, hotelId, ses,
                hotelAccountId == null ? "" : "&hotel_account_id=" + hotelAccountId );
        LOGGER.info( "Fetching reservation payout for VCC balance: {}", apiUrl );
        String json = fetchJsonInBrowser( driver, apiUrl, "POST" );
        LOGGER.debug( "get_reservation_payout response: {}", json );

        JsonObject root;
        try {
            root = JsonParser.parseString( json ).getAsJsonObject();
        }
        catch ( Exception e ) {
            throw new IOException( "Unparseable get_reservation_payout response: " + json, e );
        }
        if ( root.get( "success" ) == null || root.get( "success" ).getAsInt() != 1 ) {
            throw new IOException( "Unexpected get_reservation_payout response: " + json );
        }
        if ( root.get( "data" ) == null || false == root.get( "data" ).isJsonObject() ) {
            throw new IOException( "Missing data in get_reservation_payout response: " + json );
        }
        JsonObject data = root.getAsJsonObject( "data" );
        JsonArray cards = data.getAsJsonArray( "virtualCreditCards" );
        if ( cards == null || cards.size() == 0 ) {
            LOGGER.info( "No virtual credit cards on payout response; balance is zero." );
            return BigDecimal.ZERO;
        }

        BigDecimal total = BigDecimal.ZERO;
        for ( JsonElement elem : cards ) {
            JsonObject card = elem.getAsJsonObject();
            if ( card.get( "currentAmount" ) == null ) {
                throw new IOException( "VCC missing currentAmount: " + json );
            }
            total = total.add( new BigDecimal( card.get( "currentAmount" ).getAsString() ) );
        }
        LOGGER.info( "Found VCC balance of {} for reservation {}", total, reservationId );
        return total;
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

        List<WebElement> headerMarkInvalid = driver.findElements( By.xpath( "//button[span/span[text()='Mark credit card as invalid']]" ) );
        if ( headerMarkInvalid.isEmpty() ) {
            LOGGER.info( "Link not available (or already marked invalid). Nothing to do..." );
            return;
        }
        headerMarkInvalid.get( 0 ).click();
        wait.until( d -> ExpectedConditions.visibilityOfElementLocated( By.id( "last-digits" ) ) );

        WebElement last4DigitsInput = driver.findElement( By.id( "last-digits" ) );
        last4DigitsInput.sendKeys( last4Digits );

        Select cardInvalidSelect = new Select( driver.findElement( By.id( "reason" ) ) );
        cardInvalidSelect.selectByValue( "declined" );

        WebElement confirmBtn = driver.findElement( By.xpath( "//button[span/span[text()='Confirm']]" ) );
        confirmBtn.click();

        By CLOSE_MODAL_BTN = By.xpath( "//aside[header/h1/span[text()='Mark credit card as invalid']]/footer/button[span/span[text()='Close']]" );
        WebElement modalBtn = wait.until(d -> ExpectedConditions.visibilityOfElementLocated(CLOSE_MODAL_BTN).apply(d));
        modalBtn.click();
        LOGGER.info( "Card marked as invalid." );
    }

    /**
     * Retrieves the card details for the given booking via secure-admin
     * {@code booking_cc_details.html} (optionally after {@code vccs_access_details}).
     *
     * @param driver
     * @param wait
     * @param bdcReservation BDC reservation
     * @return credit card details
     * @throws IOException
     * @throws ParseException on parse error during retrieval
     * @throws MissingUserDataException if card details are missing or access denied
     */
    public CardDetails returnCardDetailsForBooking( WebDriver driver, WebDriverWait wait, String bdcReservation )
            throws IOException, ParseException {
        doLogin( driver, wait );

        String hotelId = wordPressDAO.getMandatoryOption( "hbo_bdc_hotel_id" );
        String ses = ensureSessionForHotel( driver, wait, hotelId );
        String hotelAccountId = extractHotelAccountIdFromPage( driver );

        String ccDetailsUrl = resolveCcDetailsUrl( driver, hotelId, ses, hotelAccountId, bdcReservation );
        LOGGER.info( "Looking up VCC card details " + ccDetailsUrl );
        driver.get( ccDetailsUrl );

        String pageState = waitForCcDetailsPageState( driver, wait );
        if ( "unavailable".equals( pageState ) ) {
            throw new MissingUserDataException( "Credit card details aren't available." );
        }
        if ( "signin".equals( pageState ) ) {
            LOGGER.info( "Secure-admin requires re-auth to view card details; signing in..." );
            doLoginForm( driver, wait,
                    wordPressDAO.getMandatoryOption( "hbo_bdc_username" ),
                    wordPressDAO.getMandatoryOption( "hbo_bdc_password" ) );
            pageState = waitForCcDetailsAfterReauth( driver, wait );
            if ( "unavailable".equals( pageState ) ) {
                throw new MissingUserDataException( "Credit card details aren't available." );
            }
            if ( false == "details".equals( pageState ) ) {
                LOGGER.error( "Unexpected page after secure-admin re-auth: {} url={}", pageState, driver.getCurrentUrl() );
                throw new MissingUserDataException( "Expecting credit card details page but not found?" );
            }
        }
        else if ( false == "details".equals( pageState ) ) {
            LOGGER.error( "Unexpected CC details page state: {} url={}", pageState, driver.getCurrentUrl() );
            throw new MissingUserDataException( "Expecting credit card details page but not found?" );
        }

        CardDetails cardDetails = scrapeCardDetailsFromPage( driver );
        LOGGER.info( "Retrieved card: " + new BasicCardMask().applyCardMask( cardDetails.getCardNumber() )
                + " for " + cardDetails.getName() );
        return cardDetails;
    }

    /**
     * Resolves the secure-admin card details URL from {@code vccs_access_details}, or builds a
     * fallback URL when that API does not return one for the reservation.
     */
    private String resolveCcDetailsUrl( WebDriver driver, String hotelId, String ses,
            String hotelAccountId, String bdcReservation ) throws IOException {
        String apiUrl = MessageFormat.format(
                "https://admin.booking.com/fresa/extranet/payments/vccs_access_details"
                        + "?hotel_id={0}&lang=en&ses={1}&reservation_ids=[{2}]{3}",
                hotelId, ses, bdcReservation,
                hotelAccountId == null ? "" : "&hotel_account_id=" + hotelAccountId );
        try {
            LOGGER.info( "Fetching VCC access details: {}", apiUrl );
            String json = fetchJsonInBrowser( driver, apiUrl, "POST" );
            LOGGER.debug( "vccs_access_details response: {}", json );

            JsonObject root = JsonParser.parseString( json ).getAsJsonObject();
            if ( root.get( "success" ) == null || root.get( "success" ).getAsInt() != 1 ) {
                throw new IOException( "Unexpected vccs_access_details response: " + json );
            }
            JsonObject data = root.getAsJsonObject( "data" );
            if ( data == null || false == data.has( "vccs" ) || false == data.get( "vccs" ).isJsonObject() ) {
                throw new IOException( "Missing vccs in vccs_access_details response: " + json );
            }
            JsonObject vccs = data.getAsJsonObject( "vccs" );
            if ( false == vccs.has( bdcReservation ) || false == vccs.get( bdcReservation ).isJsonObject() ) {
                LOGGER.warn( "Reservation {} omitted from vccs_access_details; using fallback URL", bdcReservation );
                return buildFallbackCcDetailsUrl( hotelId, bdcReservation );
            }
            JsonObject vcc = vccs.getAsJsonObject( bdcReservation );
            String accessDetail = vcc.has( "access_detail" ) && false == vcc.get( "access_detail" ).isJsonNull()
                    ? vcc.get( "access_detail" ).getAsString() : null;
            if ( false == "CC_ALLOW_VIEW".equals( accessDetail ) ) {
                throw new MissingUserDataException(
                        "Credit card details access denied for reservation " + bdcReservation
                                + " (access_detail=" + accessDetail + ")" );
            }
            if ( false == vcc.has( "cc_details_url" ) || vcc.get( "cc_details_url" ).isJsonNull()
                    || StringUtils.isBlank( vcc.get( "cc_details_url" ).getAsString() ) ) {
                LOGGER.warn( "cc_details_url missing for {}; using fallback URL", bdcReservation );
                return buildFallbackCcDetailsUrl( hotelId, bdcReservation );
            }
            return vcc.get( "cc_details_url" ).getAsString();
        }
        catch ( MissingUserDataException e ) {
            throw e;
        }
        catch ( Exception e ) {
            LOGGER.warn( "vccs_access_details failed for {}: {}; using fallback URL", bdcReservation, e.toString() );
            return buildFallbackCcDetailsUrl( hotelId, bdcReservation );
        }
    }

    private static String buildFallbackCcDetailsUrl( String hotelId, String bdcReservation ) {
        return MessageFormat.format(
                "https://secure-admin.booking.com/booking_cc_details.html?lang=en&bn={0}&hotel_id={1}&has_bvc=1",
                bdcReservation, hotelId );
    }

    /**
     * Waits until the secure-admin page shows card details, a sign-in challenge, or unavailability.
     *
     * @return one of {@code details}, {@code signin}, {@code unavailable}
     */
    private String waitForCcDetailsPageState( WebDriver driver, WebDriverWait wait ) {
        final By CC_DETAILS = ccDetailsLocator();
        final By CC_NOT_AVAIL = ccUnavailableLocator();
        final By CONTINUE_CC = By.xpath( "//p[normalize-space(text())='Continue to view the credit card details.']" );

        return wait.until( d -> {
            String url = d.getCurrentUrl();
            if ( url.contains( "account.booking.com/sign-in" )
                    || false == d.findElements( By.id( "loginname" ) ).isEmpty()
                    || false == d.findElements( CONTINUE_CC ).isEmpty() ) {
                return "signin";
            }
            if ( false == d.findElements( CC_NOT_AVAIL ).isEmpty() ) {
                return "unavailable";
            }
            if ( false == d.findElements( CC_DETAILS ).isEmpty() ) {
                return "details";
            }
            return null;
        } );
    }

    /**
     * After OAuth re-auth, wait only for details or unavailability (ignore transient sign-in URLs).
     */
    private String waitForCcDetailsAfterReauth( WebDriver driver, WebDriverWait wait ) {
        final By CC_DETAILS = ccDetailsLocator();
        final By CC_NOT_AVAIL = ccUnavailableLocator();
        return wait.until( d -> {
            if ( false == d.findElements( CC_NOT_AVAIL ).isEmpty() ) {
                return "unavailable";
            }
            if ( false == d.findElements( CC_DETAILS ).isEmpty() ) {
                return "details";
            }
            return null;
        } );
    }

    private static By ccDetailsLocator() {
        return By.xpath(
                "//th[contains(text(),'Credit Card Details')] | //th[contains(text(),'credit card details')]"
                        + " | //td[text()='Card number:'] | //td[contains(text(),'Card number')]" );
    }

    private static By ccUnavailableLocator() {
        return By.xpath(
                "//h2[contains(text(),\"credit card details aren't available\")]"
                        + " | //*[contains(text(),\"This virtual card is no longer active\")]"
                        + " | //*[contains(text(),\"This virtual card isn't active anymore\")]" );
    }

    private CardDetails scrapeCardDetailsFromPage( WebDriver driver ) throws ParseException {
        CardDetails cardDetails = new CardDetails();
        cardDetails.setName( driver.findElement(
                By.xpath( "//td[text()=\"Card holder's name:\"]/following-sibling::td" ) ).getText().trim() );
        cardDetails.setCardNumber( driver.findElement(
                By.xpath( "//td[text()='Card number:']/following-sibling::td" ) ).getText().replaceAll( "\\s", "" ) );
        cardDetails.setCardType( driver.findElement(
                By.xpath( "//td[text()='Card type:']/following-sibling::td" ) ).getText().trim() );
        cardDetails.setExpiry( parseExpiryDate( driver.findElement(
                By.xpath( "//td[contains(text(),'Expiration')]/following-sibling::td" ) ).getText().trim() ) );
        cardDetails.setCvv( StringUtils.trimToNull( driver.findElement(
                By.xpath( "//td[contains(text(),'CVC')]/following-sibling::td" ) ).getText() ) );
        if ( StringUtils.isBlank( cardDetails.getCardNumber() ) ) {
            throw new MissingUserDataException( "Card number missing from credit card details page." );
        }
        return cardDetails;
    }

    /**
     * Searches for all VCC bookings that can be charged immediately via the
     * extranet fresa JSON API (not the SPA table DOM).
     *
     * @param driver
     * @param wait
     * @return non-null list of BDC booking refs
     * @throws IOException
     */
    public List<String> getAllVCCBookingsThatCanBeCharged( WebDriver driver, WebDriverWait wait ) throws IOException {
        doLogin( driver, wait );

        String hotelId = wordPressDAO.getMandatoryOption( "hbo_bdc_hotel_id" );
        String ses = ensureSessionForHotel( driver, wait, hotelId );
        String hotelAccountId = resolveHotelAccountId( driver, wait, hotelId, ses );

        List<String> chargeableRefs = new ArrayList<>();
        int page = 1;
        final int limit = 50;
        boolean lastPage = false;
        while ( false == lastPage ) {
            String apiUrl = MessageFormat.format(
                    "https://admin.booking.com/fresa/extranet/payments/vccs_to_charge?lang=en&hotel_id={0}&ses={1}&limit={2}&page={3}{4}",
                    hotelId, ses, String.valueOf( limit ), String.valueOf( page ),
                    hotelAccountId == null ? "" : "&hotel_account_id=" + hotelAccountId );
            LOGGER.info( "Fetching VCCs to charge page {}: {}", page, apiUrl );
            String json = fetchJsonInBrowser( driver, apiUrl );
            LOGGER.debug( "vccs_to_charge response: {}", json );

            JsonObject root = JsonParser.parseString( json ).getAsJsonObject();
            if ( root.get( "success" ) == null || root.get( "success" ).getAsInt() != 1 ) {
                throw new IOException( "Unexpected vccs_to_charge response: " + json );
            }
            JsonObject data = root.getAsJsonObject( "data" );
            JsonArray vccs = data.getAsJsonArray( "vccs" );
            if ( vccs != null ) {
                for ( JsonElement elem : vccs ) {
                    JsonObject vcc = elem.getAsJsonObject();
                    String formatted = vcc.getAsJsonObject( "current_amount" ).get( "formatted" ).getAsString();
                    if ( PaymentProcessorService.isChargeableAmount( formatted ) ) {
                        chargeableRefs.add( String.valueOf( vcc.get( "hres_id" ).getAsLong() ) );
                    }
                }
            }
            JsonObject pagination = data.getAsJsonObject( "pagination" );
            lastPage = pagination == null || pagination.get( "is_last_page" ).getAsInt() == 1;
            page++;
        }

        LOGGER.info( "Found {} chargeable VCC bookings for hotel_id={}", chargeableRefs.size(), hotelId );
        return chargeableRefs;
    }

    /**
     * Navigates to the given property home so the URL contains {@code ses}, then returns it.
     */
    private String ensureSessionForHotel( WebDriver driver, WebDriverWait wait, String hotelId ) {
        if ( driver.getCurrentUrl().contains( "ses=" ) && driver.getCurrentUrl().contains( "hotel_id=" + hotelId ) ) {
            return getSessionFromURL( driver.getCurrentUrl() );
        }
        String homeUrl = MessageFormat.format(
                "https://admin.booking.com/hotel/hoteladmin/extranet_ng/manage/home.html?lang=en&hotel_id={0}",
                hotelId );
        LOGGER.info( "Switching to BDC property hotel_id={}: {}", hotelId, homeUrl );
        driver.get( homeUrl );
        wait.until( d -> d.getCurrentUrl().contains( "ses=" ) );
        return getSessionFromURL( driver.getCurrentUrl() );
    }

    /**
     * Loads the VCC management page and extracts {@code hotel_account_id} when present
     * (required by some fresa payment endpoints).
     */
    private String resolveHotelAccountId( WebDriver driver, WebDriverWait wait, String hotelId, String ses ) {
        String vccUrl = MessageFormat.format(
                "https://admin.booking.com/hotel/hoteladmin/extranet_ng/manage/vccs_management.html?lang=en&ses={0}&hotel_id={1}&route=vccs_to_charge",
                ses, hotelId );
        LOGGER.info( "Loading VCC management page to resolve hotel_account_id: {}", vccUrl );
        driver.get( vccUrl );
        wait.until( d -> d.getCurrentUrl().contains( "vccs_management" ) );
        return extractHotelAccountIdFromPage( driver );
    }

    /**
     * Extracts {@code hotel_account_id} from the current page source when present.
     */
    private String extractHotelAccountIdFromPage( WebDriver driver ) {
        Matcher m = Pattern.compile( "hotel_account_id[=\"'\\s:]+(\\d+)" ).matcher( driver.getPageSource() );
        if ( m.find() ) {
            LOGGER.info( "Resolved hotel_account_id={}", m.group( 1 ) );
            return m.group( 1 );
        }
        Object fromJs = ( (JavascriptExecutor) driver ).executeScript(
                "var m = document.documentElement.innerHTML.match(/hotel_account_id[=\\\"'\\s:]+(\\d+)/); return m ? m[1] : null;" );
        if ( fromJs != null ) {
            LOGGER.info( "Resolved hotel_account_id={} from DOM", fromJs );
            return fromJs.toString();
        }
        LOGGER.warn( "hotel_account_id not found on current page; calling fresa without it" );
        return null;
    }

    /**
     * Same-origin GET fetch inside the logged-in Chrome session (cookies + WAF tokens).
     */
    private String fetchJsonInBrowser( WebDriver driver, String url ) throws IOException {
        return fetchJsonInBrowser( driver, url, "GET" );
    }

    /**
     * Same-origin fetch inside the logged-in Chrome session (cookies + WAF tokens).
     *
     * @param method HTTP method, e.g. {@code GET} or {@code POST}
     */
    private String fetchJsonInBrowser( WebDriver driver, String url, String method ) throws IOException {
        driver.manage().timeouts().scriptTimeout( Duration.ofSeconds( 60 ) );
        Object result = ( (JavascriptExecutor) driver ).executeAsyncScript(
                "var url = arguments[0];"
                        + "var method = arguments[1];"
                        + "var callback = arguments[arguments.length - 1];"
                        + "fetch(url, { method: method, credentials: 'include', headers: { 'Accept': 'application/json' } })"
                        + ".then(function(r) { return r.text().then(function(t) {"
                        + "  if (!r.ok) { callback('HTTP_ERROR:' + r.status + ':' + t); }"
                        + "  else { callback(t); }"
                        + "}); })"
                        + ".catch(function(e) { callback('FETCH_ERROR:' + e); });",
                url, method );
        if ( result == null ) {
            throw new IOException( "Empty response fetching " + url );
        }
        String body = result.toString();
        if ( body.startsWith( "HTTP_ERROR:" ) || body.startsWith( "FETCH_ERROR:" ) ) {
            throw new IOException( "Failed fetching " + url + ": " + body );
        }
        return body;
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
        return wait.until(d -> ExpectedConditions.visibilityOfElementLocated(by).apply(d));
    }

}
