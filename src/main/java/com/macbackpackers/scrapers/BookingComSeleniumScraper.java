package com.macbackpackers.scrapers;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.macbackpackers.beans.AmazonWafChallengeParams;
import com.macbackpackers.beans.AmazonWafSolution;
import com.macbackpackers.beans.CardDetails;
import com.macbackpackers.beans.bdc.BookingComRefundRequest;
import com.macbackpackers.beans.bdc.BookingComVCCToCharge;
import com.macbackpackers.dao.WordPressDAO;
import com.macbackpackers.exceptions.MissingUserDataException;
import com.macbackpackers.exceptions.UnrecoverableFault;
import com.macbackpackers.services.BasicCardMask;
import com.macbackpackers.services.CaptchaSolverService;
import com.macbackpackers.services.PaymentProcessorService;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.htmlunit.BrowserVersion;
import org.htmlunit.WebClient;
import org.openqa.selenium.By;
import org.openqa.selenium.Cookie;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.TimeoutException;
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
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.openqa.selenium.support.ui.ExpectedConditions.stalenessOf;

@Component
public class BookingComSeleniumScraper {

    private final Logger LOGGER = LoggerFactory.getLogger( getClass() );

    /** Multi-property accounts land here; property {@code home.html} without a fresh {@code ses} can 400. */
    static final String BDC_GROUPS_HOME =
            "https://admin.booking.com/hotel/hoteladmin/groups/home/index.html";

    private static final int AWS_WAF_MAX_ATTEMPTS = 5;

    /** WAF verify POSTs go to www.booking.com; host-only account.booking.com cookies are not sent. */
    private static final String AWS_WAF_COOKIE_DOMAIN = ".booking.com";

    @Autowired
    private WordPressDAO wordPressDAO;

    @Autowired
    private CaptchaSolverService captchaSolverService;

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

        if ( isAwsWafChallenge( driver ) ) {
            LOGGER.info( "AWS WAF challenge on BDC entry; solving via 2captcha..." );
            solveAwsWafChallenge( driver, wait );
            if ( false == isLoggedInUrl( driver.getCurrentUrl() )
                    && false == StringUtils.startsWith( driver.getCurrentUrl(), "https://account.booking.com/sign-in" ) ) {
                driver.get( BDC_GROUPS_HOME );
            }
        }

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
    private void doLoginForm( WebDriver driver, WebDriverWait wait, String username, String password )
            throws IOException {

        if ( isAwsWafChallenge( driver ) ) {
            LOGGER.info( "AWS WAF challenge before BDC username; solving via 2captcha..." );
            solveAwsWafChallenge( driver, wait );
        }

        // WAF solve may already have restored a warm session.
        if ( isLoggedInUrl( driver.getCurrentUrl() ) ) {
            return;
        }

        WebElement usernameField = findElement( driver, wait, By.id( "loginname" ) );
        usernameField.sendKeys( username );
        WebElement nextButton = findElement( driver, wait, By.xpath( "//span[text()='Next']/.." ) );
        nextButton.click();
        wait.until( stalenessOf( nextButton ) );

        // Captcha often appears after username (jsapi / awswaf-captcha), before the password field.
        // Cookie-bag inject reloads the page: captcha clears but the wizard resets to username, so we
        // re-submit username after a successful solve. May hit captcha again (especially without a
        // matching hbo_2captcha_proxy).
        for ( int captchaGate = 0 ; captchaGate < 3 ; captchaGate++ ) {
            wait.until( d -> isAwsWafChallenge( d )
                    || false == d.findElements( By.id( "password" ) ).isEmpty()
                    || isLoggedInUrl( d.getCurrentUrl() )
                    || isTwoFactorPage( d.getCurrentUrl() )
                    || false == d.findElements( By.id( "loginname" ) ).isEmpty() );

            if ( false == driver.findElements( By.id( "password" ) ).isEmpty()
                    || isLoggedInUrl( driver.getCurrentUrl() )
                    || isTwoFactorPage( driver.getCurrentUrl() ) ) {
                break;
            }

            if ( isAwsWafChallenge( driver ) ) {
                LOGGER.info( "AWS WAF challenge after BDC username; solving via 2captcha (gate {})...",
                        captchaGate + 1 );
                try {
                    solveAwsWafChallenge( driver, wait );
                }
                catch ( Exception e ) {
                    LOGGER.warn( "AWS WAF solve after username failed (gate {}): {}",
                            captchaGate + 1, e.toString() );
                    if ( captchaGate >= 2 ) {
                        if ( e instanceof IOException ) {
                            throw (IOException) e;
                        }
                        throw new IOException( e );
                    }
                }
            }

            if ( false == driver.findElements( By.id( "password" ) ).isEmpty()
                    || isLoggedInUrl( driver.getCurrentUrl() )
                    || isTwoFactorPage( driver.getCurrentUrl() ) ) {
                break;
            }

            if ( false == driver.findElements( By.id( "loginname" ) ).isEmpty() ) {
                LOGGER.info( "BDC sign-in on username form after captcha gate {}; re-submitting username...",
                        captchaGate + 1 );
                WebElement userAgain = findElement( driver, wait, By.id( "loginname" ) );
                userAgain.clear();
                userAgain.sendKeys( username );
                WebElement nextAgain = findElement( driver, wait, By.xpath( "//span[text()='Next']/.." ) );
                nextAgain.click();
                wait.until( stalenessOf( nextAgain ) );
                continue;
            }
            break;
        }
        wait.until( d -> false == d.findElements( By.id( "password" ) ).isEmpty()
                || isLoggedInUrl( d.getCurrentUrl() )
                || isTwoFactorPage( d.getCurrentUrl() ) );

        if ( isLoggedInUrl( driver.getCurrentUrl() ) || isTwoFactorPage( driver.getCurrentUrl() ) ) {
            if ( isTwoFactorPage( driver.getCurrentUrl() ) ) {
                completeTwoFactorVerification( driver, wait );
                wait.until( d -> false == isTwoFactorPage( d.getCurrentUrl() ) );
            }
            return;
        }

        WebElement passwordField = findElement( driver, wait, By.id( "password" ) );
        passwordField.sendKeys( password );

        WebElement signInButton = findElement( driver, wait, By.xpath( "//span[text()='Sign in']/.." ) );
        signInButton.click();
        // Must pass ExpectedCondition directly — `d -> stalenessOf(btn)` is always truthy and returns immediately.
        wait.until( stalenessOf( signInButton ) );

        // Wait until password submit navigates away (2FA, admin, captcha, or error page).
        wait.until( d -> {
            String url = d.getCurrentUrl();
            return url != null && false == url.contains( "/sign-in/password" );
        } );

        if ( isAwsWafChallenge( driver ) ) {
            LOGGER.info( "AWS WAF challenge after BDC password; solving via 2captcha..." );
            solveAwsWafChallenge( driver, wait );
        }

        if ( isTwoFactorPage( driver.getCurrentUrl() ) ) {
            completeTwoFactorVerification( driver, wait );
            wait.until( d -> false == isTwoFactorPage( d.getCurrentUrl() ) );
            if ( isAwsWafChallenge( driver ) ) {
                LOGGER.info( "AWS WAF challenge after BDC 2FA; solving via 2captcha..." );
                solveAwsWafChallenge( driver, wait );
            }
        }
    }

    private static boolean isLoggedInUrl( String url ) {
        return url != null && (
                url.startsWith( "https://admin.booking.com/hotel/hoteladmin/extranet_ng/manage/home.html" )
                        || url.startsWith( "https://admin.booking.com/hotel/hoteladmin/groups/home/index.html" ) );
    }

    /**
     * True when Booking.com is showing an SMS/phone 2FA or auth-assurance challenge.
     */
    private static boolean isTwoFactorPage( String url ) {
        return url != null && (
                url.startsWith( "https://account.booking.com/sign-in/verification" )
                        || url.startsWith( "https://secure-admin.booking.com/2fa/" )
                        || url.contains( "account.booking.com/auth-assurance" ) );
    }

    /**
     * Completes SMS or phone verification when on a Booking.com 2FA / auth-assurance page.
     * Mode is controlled by WP option {@code hbo_bdc_verificationmode} ({@code sms} or {@code phone}).
     */
    private void completeTwoFactorVerification( WebDriver driver, WebDriverWait wait ) {
        LOGGER.info( "BDC verification required at {}", driver.getCurrentUrl() );
        List<WebElement> phoneLinks = driver.findElements(
                By.xpath( "//a[contains(@class, 'nw-call-verification-link')] | //input[@value='call']" ) );
        List<WebElement> smsLinks = driver.findElements(
                By.xpath( "//a[contains(@class, 'nw-sms-verification-link')] | //input[@value='sms']" ) );

        String verificationMode = wordPressDAO.getOption( "hbo_bdc_verificationmode" );
        if ( "sms".equalsIgnoreCase( verificationMode ) && smsLinks.size() > 0 ) {
            LOGGER.info( "Performing SMS verification" );
            smsLinks.get( 0 ).click();
            WebElement selectedPhone = driver.findElement(
                    By.xpath( "//*[@id='selected_phone'] | //select[@name='phone_id_sms']" ) );
            if ( false == selectedPhone.getText().trim().endsWith( "4338" ) ) {
                throw new MissingUserDataException( "Phone number not registered: " + selectedPhone.getText() );
            }

            driver.findElement( By.xpath( "//span[text()='Send verification code'] "
                    + "| //div[contains(@class,'cta-phone')]/input[@value='Send text message']" ) ).click();

            findElement( driver, wait, By.xpath( "//*[@id='sms_code' or @id='ask_pin_input']" ) )
                    .sendKeys( fetch2FACode() );

            final WebElement nextButton = driver.findElement( By.xpath(
                    "//span[text()='Verify now']/.. | //div[contains(@class,'ctas')]/input[@value='Verify now']" ) );
            nextButton.click();
            wait.until( stalenessOf( nextButton ) );
        }
        else if ( "phone".equalsIgnoreCase( verificationMode ) && phoneLinks.size() > 0 ) {
            LOGGER.info( "Performing phone verification" );
            phoneLinks.get( 0 ).click();
            WebElement nextButton = driver.findElement( By.xpath( "//span[text()='Call now']/.." ) );
            nextButton.click();

            findElement( driver, wait, By.xpath( "//*[@id='sms_code' or @id='ask_pin_input']" ) )
                    .sendKeys( fetch2FACode() );

            final WebElement verifyButton = driver.findElement( By.xpath(
                    "//span[text()='Verify now']/.. | //div[contains(@class,'ctas')]/input[@value='Verify now']" ) );
            verifyButton.click();
            wait.until( stalenessOf( verifyButton ) );
        }
        else {
            throw new MissingUserDataException( "Verification required for BDC?" );
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

        String pageState = wait.until( d -> {
            String url = d.getCurrentUrl();
            if ( url != null && url.contains( "account.booking.com/auth-assurance" ) ) {
                return "assurance";
            }
            String title = d.getTitle();
            if ( title != null && title.toLowerCase().contains( "reservation detail" ) ) {
                return "details";
            }
            return null;
        } );
        if ( "assurance".equals( pageState ) ) {
            LOGGER.info( "Reservation page requires auth-assurance; completing 2FA..." );
            completeTwoFactorVerification( driver, wait );
            wait.until( d -> {
                String title = d.getTitle();
                return title != null && title.toLowerCase().contains( "reservation detail" );
            } );
        }
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
     * <p>
     * Does not open {@code booking.html} (that page may still prompt auth-assurance SMS,
     * which {@link #lookupReservation} now completes); only needs a warm admin session +
     * property {@code ses}.
     *
     * @param driver
     * @param wait
     * @param reservationId the BDC reference
     * @return the amount available on the VCC (zero if no chargeable balance)
     * @throws IOException if unable to login or payout API fails
     */
    public BigDecimal getVirtualCardBalance( WebDriver driver, WebDriverWait wait, String reservationId ) throws IOException {
        doLogin( driver, wait );

        String hotelId = wordPressDAO.getMandatoryOption( "hbo_bdc_hotel_id" );
        String ses = ensureSessionForHotel( driver, wait, hotelId );
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
            // Skip closed/cancelled/replaced VCCs — only chargeable cards hold a usable balance.
            if ( card.get( "isInChargeableStatus" ) == null || card.get( "isInChargeableStatus" ).getAsInt() != 1 ) {
                LOGGER.info( "Skipping non-chargeable VCC ending {} (status={}, isInChargeableStatus={}, currentAmount={})",
                        card.has( "ccLastDigits" ) ? card.get( "ccLastDigits" ).getAsString() : "?",
                        card.has( "status" ) ? card.get( "status" ).getAsString() : "?",
                        card.has( "isInChargeableStatus" ) ? card.get( "isInChargeableStatus" ) : null,
                        card.has( "currentAmount" ) ? card.get( "currentAmount" ) : null );
                continue;
            }
            if ( card.get( "currentAmount" ) == null || card.get( "currentAmount" ).isJsonNull() ) {
                throw new IOException( "Chargeable VCC missing currentAmount: " + json );
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

        boolean detailsReady = false;
        for ( int gate = 0 ; gate < 6 ; gate++ ) {
            String pageState = waitForCcDetailsPageState( driver, wait );
            if ( "waf".equals( pageState ) ) {
                LOGGER.info( "AWS WAF challenge on CC details page; solving via 2captcha..." );
                solveAwsWafChallenge( driver, wait );
                continue;
            }
            if ( "unavailable".equals( pageState ) ) {
                throw new MissingUserDataException( "Credit card details aren't available." );
            }
            if ( "assurance".equals( pageState ) ) {
                LOGGER.info( "Secure-admin requires auth-assurance to view card details; completing 2FA..." );
                completeTwoFactorVerification( driver, wait );
                pageState = waitForCcDetailsAfterReauth( driver, wait );
                if ( "waf".equals( pageState ) ) {
                    LOGGER.info( "AWS WAF challenge after auth-assurance; solving via 2captcha..." );
                    solveAwsWafChallenge( driver, wait );
                    continue;
                }
                if ( "unavailable".equals( pageState ) ) {
                    throw new MissingUserDataException( "Credit card details aren't available." );
                }
                if ( false == "details".equals( pageState ) ) {
                    LOGGER.error( "Unexpected page after auth-assurance: {} url={}", pageState, driver.getCurrentUrl() );
                    throw new MissingUserDataException( "Expecting credit card details page but not found?" );
                }
                detailsReady = true;
                break;
            }
            if ( "signin".equals( pageState ) ) {
                LOGGER.info( "Secure-admin requires re-auth to view card details; signing in..." );
                doLoginForm( driver, wait,
                        wordPressDAO.getMandatoryOption( "hbo_bdc_username" ),
                        wordPressDAO.getMandatoryOption( "hbo_bdc_password" ) );
                pageState = waitForCcDetailsAfterReauth( driver, wait );
                if ( "waf".equals( pageState ) ) {
                    LOGGER.info( "AWS WAF challenge after secure-admin re-auth; solving via 2captcha..." );
                    solveAwsWafChallenge( driver, wait );
                    continue;
                }
                if ( "unavailable".equals( pageState ) ) {
                    throw new MissingUserDataException( "Credit card details aren't available." );
                }
                if ( false == "details".equals( pageState ) ) {
                    LOGGER.error( "Unexpected page after secure-admin re-auth: {} url={}", pageState, driver.getCurrentUrl() );
                    throw new MissingUserDataException( "Expecting credit card details page but not found?" );
                }
                detailsReady = true;
                break;
            }
            if ( "details".equals( pageState ) ) {
                detailsReady = true;
                break;
            }
            LOGGER.error( "Unexpected CC details page state: {} url={}", pageState, driver.getCurrentUrl() );
            throw new MissingUserDataException( "Expecting credit card details page but not found?" );
        }
        if ( false == detailsReady ) {
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
     * Waits until the secure-admin page shows card details, a sign-in challenge,
     * an auth-assurance 2FA challenge, an AWS WAF captcha, or unavailability.
     *
     * @return one of {@code details}, {@code signin}, {@code assurance}, {@code waf}, {@code unavailable}
     */
    private String waitForCcDetailsPageState( WebDriver driver, WebDriverWait wait ) {
        final By CC_DETAILS = ccDetailsLocator();
        final By CC_NOT_AVAIL = ccUnavailableLocator();
        final By CONTINUE_CC = By.xpath( "//p[normalize-space(text())='Continue to view the credit card details.']" );

        return wait.until( d -> {
            if ( isAwsWafChallenge( d ) ) {
                return "waf";
            }
            String url = d.getCurrentUrl();
            if ( url != null && url.contains( "account.booking.com/auth-assurance" ) ) {
                return "assurance";
            }
            if ( url != null && ( url.contains( "account.booking.com/sign-in" )
                    || false == d.findElements( By.id( "loginname" ) ).isEmpty()
                    || false == d.findElements( CONTINUE_CC ).isEmpty() ) ) {
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
     * After OAuth re-auth, wait for details, unavailability, or a post-reauth WAF challenge.
     */
    private String waitForCcDetailsAfterReauth( WebDriver driver, WebDriverWait wait ) {
        final By CC_DETAILS = ccDetailsLocator();
        final By CC_NOT_AVAIL = ccUnavailableLocator();
        return wait.until( d -> {
            if ( isAwsWafChallenge( d ) ) {
                return "waf";
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
     * @return non-null list of chargeable VCCs (booking ref, charge-before date, amount)
     * @throws IOException
     */
    public List<BookingComVCCToCharge> getAllVCCBookingsThatCanBeCharged( WebDriver driver, WebDriverWait wait )
            throws IOException {
        doLogin( driver, wait );

        String hotelId = wordPressDAO.getMandatoryOption( "hbo_bdc_hotel_id" );
        String ses = ensureSessionForHotel( driver, wait, hotelId );
        String hotelAccountId = resolveHotelAccountId( driver, wait, hotelId, ses );

        List<BookingComVCCToCharge> chargeable = new ArrayList<>();
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
                    JsonObject currentAmount = vcc.getAsJsonObject( "current_amount" );
                    String formatted = currentAmount.get( "formatted" ).getAsString();
                    if ( PaymentProcessorService.isChargeableAmount( formatted ) ) {
                        chargeable.add( new BookingComVCCToCharge(
                                String.valueOf( vcc.get( "hres_id" ).getAsLong() ),
                                LocalDate.parse( vcc.get( "expiry_date" ).getAsString() ),
                                new BigDecimal( currentAmount.get( "amount" ).getAsString() ) ) );
                    }
                }
            }
            JsonObject pagination = data.getAsJsonObject( "pagination" );
            lastPage = pagination == null || pagination.get( "is_last_page" ).getAsInt() == 1;
            page++;
        }

        LOGGER.info( "Found {} chargeable VCC bookings for hotel_id={}", chargeable.size(), hotelId );
        return chargeable;
    }

    /**
     * Searches for all VCC bookings that must be refunded via the extranet fresa JSON API
     * ({@code vccs_to_refund}), not the SPA table DOM.
     *
     * @param driver
     * @param wait
     * @return non-null list of BDC refunds (booking ref, reason, amount)
     * @throws IOException
     */
    public List<BookingComRefundRequest> getAllVCCBookingsThatMustBeRefunded( WebDriver driver, WebDriverWait wait )
            throws IOException {
        doLogin( driver, wait );

        String hotelId = wordPressDAO.getMandatoryOption( "hbo_bdc_hotel_id" );
        String ses = ensureSessionForHotel( driver, wait, hotelId );
        String hotelAccountId = resolveHotelAccountId( driver, wait, hotelId, ses );

        List<BookingComRefundRequest> refunds = new ArrayList<>();
        int page = 1;
        final int limit = 50;
        boolean lastPage = false;
        while ( false == lastPage ) {
            String apiUrl = MessageFormat.format(
                    "https://admin.booking.com/fresa/extranet/payments/vccs_to_refund?lang=en&hotel_id={0}&ses={1}&limit={2}&page={3}{4}",
                    hotelId, ses, String.valueOf( limit ), String.valueOf( page ),
                    hotelAccountId == null ? "" : "&hotel_account_id=" + hotelAccountId );
            LOGGER.info( "Fetching VCCs to refund page {}: {}", page, apiUrl );
            String json = fetchJsonInBrowser( driver, apiUrl );
            LOGGER.debug( "vccs_to_refund response: {}", json );

            JsonObject root = JsonParser.parseString( json ).getAsJsonObject();
            refunds.addAll( parseVccsToRefundPage( root ) );
            JsonObject data = root.getAsJsonObject( "data" );
            JsonObject pagination = data == null ? null : data.getAsJsonObject( "pagination" );
            lastPage = pagination == null || pagination.get( "is_last_page" ).getAsInt() == 1;
            page++;
        }

        LOGGER.info( "Found {} VCC refunds for hotel_id={}", refunds.size(), hotelId );
        return refunds;
    }

    /**
     * Parses one {@code vccs_to_refund} page. Skips zero amounts and already-confirmed rows.
     */
    List<BookingComRefundRequest> parseVccsToRefundPage( JsonObject root ) throws IOException {
        if ( root == null || root.get( "success" ) == null || root.get( "success" ).getAsInt() != 1 ) {
            throw new IOException( "Unexpected vccs_to_refund response: " + root );
        }
        JsonObject data = root.getAsJsonObject( "data" );
        JsonArray vccs = data == null ? null : data.getAsJsonArray( "vccs" );
        List<BookingComRefundRequest> refunds = new ArrayList<>();
        if ( vccs == null ) {
            return refunds;
        }
        for ( JsonElement elem : vccs ) {
            JsonObject vcc = elem.getAsJsonObject();
            if ( vcc.has( "is_action_confirmed" ) && false == vcc.get( "is_action_confirmed" ).isJsonNull()
                    && vcc.get( "is_action_confirmed" ).getAsInt() == 1 ) {
                continue;
            }
            JsonObject amountObj = vcc.getAsJsonObject( "amount_to_refund" );
            if ( amountObj == null || amountObj.get( "amount" ) == null ) {
                continue;
            }
            BigDecimal amount = amountObj.get( "amount" ).getAsBigDecimal();
            if ( amount.compareTo( BigDecimal.ZERO ) <= 0 ) {
                continue;
            }
            String bookingRef = vcc.get( "hres_id" ).getAsString();
            refunds.add( new BookingComRefundRequest( bookingRef, refundReasonFromVcc( vcc ), amount ) );
        }
        return refunds;
    }

    private static String refundReasonFromVcc( JsonObject vcc ) {
        JsonElement fm = vcc.get( "is_force_majeure" );
        if ( fm != null && false == fm.isJsonNull() ) {
            if ( fm.isJsonPrimitive() && fm.getAsJsonPrimitive().isNumber() && fm.getAsInt() == 1 ) {
                return "Force majeure";
            }
            if ( fm.isJsonPrimitive() && fm.getAsJsonPrimitive().isBoolean() && fm.getAsBoolean() ) {
                return "Force majeure";
            }
            if ( fm.isJsonPrimitive() && "1".equals( fm.getAsString() ) ) {
                return "Force majeure";
            }
        }
        JsonElement due = vcc.get( "due_date_to_refund_vcc" );
        if ( due != null && false == due.isJsonNull() && StringUtils.isNotBlank( due.getAsString() ) ) {
            return "Refund due by " + due.getAsString();
        }
        return "VCC refund";
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
     * True when the current page is showing a visual AWS WAF captcha (not silent token refresh).
     * <p>
     * Booking.com currently uses the captcha-sdk {@code jsapi.js} flow, which mounts a custom
     * {@code awswaf-captcha} element (see {@code booking.com-captcha.at.login.har}). Older pages
     * use {@code #captcha-container} + {@code window.gokuProps}. {@code challenge.js} alone is
     * common for silent token refresh and must not count as a visual challenge.
     */
    boolean isAwsWafChallenge( WebDriver driver ) {
        try {
            Object result = ( (JavascriptExecutor) driver ).executeScript(
                    "var g = window.gokuProps;"
                            + "var hasAwsElement = !!document.querySelector('awswaf-captcha');"
                            + "var hasContainer = !!(document.querySelector('#captcha-container')"
                            + "  || document.querySelector('#challenge-container')"
                            + "  || document.querySelector('[id*=\"amzn-captcha\"]')"
                            + "  || document.querySelector('iframe[src*=\"awswaf\"]')"
                            + "  || document.querySelector('iframe[src*=\"captcha\"]'));"
                            + "var hasGoku = !!(g && g.key && (g.iv || g.context));"
                            + "var scripts = Array.prototype.slice.call(document.querySelectorAll('script[src]'))"
                            + "  .map(function(s) { return s.src || ''; });"
                            + "var hasCaptchaJs = scripts.some(function(s) { return s.indexOf('captcha.js') >= 0; });"
                            + "var hasJsapi = scripts.some(function(s) {"
                            + "  return s.indexOf('jsapi.js') >= 0 || s.indexOf('captcha-sdk.awswaf.com') >= 0;"
                            + "});"
                            + "var visualProblem = false;"
                            + "try {"
                            + "  var entries = performance.getEntriesByType('resource') || [];"
                            + "  for (var i = 0; i < entries.length; i++) {"
                            + "    var n = entries[i].name || '';"
                            + "    if (n.indexOf('captcha-sdk.awswaf.com') >= 0 && n.indexOf('problem') >= 0"
                            + "        && n.indexOf('kind=visual') >= 0) { visualProblem = true; break; }"
                            + "  }"
                            + "} catch (e) {}"
                            + "var bodyText = (document.body && document.body.innerText) ? document.body.innerText : '';"
                            + "var humanPrompt = /confirm you are human|verify you are human|are you a human|solve this puzzle/i.test(bodyText);"
                            + "return !!(hasAwsElement"
                            + "  || (hasJsapi && (visualProblem || humanPrompt || hasContainer))"
                            + "  || (hasContainer && (hasGoku || hasCaptchaJs || hasJsapi))"
                            + "  || (hasGoku && (hasCaptchaJs || hasJsapi || humanPrompt)));" );
            return Boolean.TRUE.equals( result );
        }
        catch ( Exception e ) {
            LOGGER.debug( "AWS WAF detection failed: {}", e.toString() );
            return false;
        }
    }

    /**
     * Extracts challenge params, solves via 2captcha, injects the token, and waits for the challenge to clear.
     */
    void solveAwsWafChallenge( WebDriver driver, WebDriverWait wait ) throws IOException {
        IOException lastFailure = null;
        for ( int attempt = 1 ; attempt <= AWS_WAF_MAX_ATTEMPTS ; attempt++ ) {
            if ( false == isAwsWafChallenge( driver ) ) {
                if ( attempt == 1 ) {
                    LOGGER.info( "AWS WAF challenge no longer present before solve" );
                    return;
                }
                // After a failed inject + refresh we often land back on the username form (no captcha).
                // That is not success — surface the prior failure.
                if ( lastFailure != null ) {
                    throw lastFailure;
                }
                throw new UnrecoverableFault( "AWS WAF challenge disappeared after failed solve attempt" );
            }
            try {
                AmazonWafChallengeParams params = extractAmazonWafParams( driver );
                LOGGER.info( "Solving AWS WAF attempt {}/{}: {}", attempt, AWS_WAF_MAX_ATTEMPTS, params );
                try ( WebClient webClient = new WebClient( BrowserVersion.CHROME ) ) {
                    webClient.getOptions().setJavaScriptEnabled( false );
                    webClient.getOptions().setCssEnabled( false );
                    webClient.getOptions().setThrowExceptionOnFailingStatusCode( false );
                    webClient.getOptions().setThrowExceptionOnScriptError( false );
                    AmazonWafSolution solution = captchaSolverService.solveAmazonWaf( webClient, params );
                    injectAmazonWafSolution( driver, solution );
                }
                try {
                    new WebDriverWait( driver, Duration.ofSeconds( 20 ) )
                            .until( d -> false == isAwsWafChallenge( d ) );
                }
                catch ( TimeoutException te ) {
                    if ( isAwsWafChallenge( driver ) ) {
                        LOGGER.warn( "AWS WAF still showing after inject (challenge likely rotated); retrying with fresh params" );
                        lastFailure = new IOException( "AWS WAF challenge still present after inject", te );
                        saveAwsWafDebugArtifacts( driver, attempt );
                        continue;
                    }
                    throw te;
                }
                if ( isAwsWafTimeoutMessage( driver ) && isAwsWafChallenge( driver ) ) {
                    LOGGER.warn( "AWS WAF timeout copy still visible; retrying" );
                    lastFailure = new IOException( "AWS WAF captcha still shows timeout after inject" );
                    continue;
                }
                LOGGER.info( "AWS WAF challenge cleared after attempt {}", attempt );
                return;
            }
            catch ( Exception e ) {
                lastFailure = e instanceof IOException ? (IOException) e : new IOException( e );
                LOGGER.warn( "AWS WAF solve attempt {} failed: {}", attempt, e.toString() );
                saveAwsWafDebugArtifacts( driver, attempt );
                if ( attempt < AWS_WAF_MAX_ATTEMPTS && isAwsWafChallenge( driver ) ) {
                    LOGGER.info( "Reloading captcha page to obtain fresh AWS WAF params..." );
                    driver.navigate().refresh();
                    sleep( 2 );
                }
            }
        }
        if ( lastFailure != null ) {
            throw lastFailure;
        }
        throw new UnrecoverableFault( "Failed to solve AWS WAF captcha" );
    }

    private boolean isAwsWafTimeoutMessage( WebDriver driver ) {
        try {
            Object result = ( (JavascriptExecutor) driver ).executeScript(
                    "var t = (document.body && document.body.innerText) ? document.body.innerText : '';"
                            + "return /request timed out|please try again|something went wrong/i.test(t);" );
            return Boolean.TRUE.equals( result );
        }
        catch ( Exception e ) {
            return false;
        }
    }

    @SuppressWarnings( "unchecked" )
    private AmazonWafChallengeParams extractAmazonWafParams( WebDriver driver ) {
        Object raw = ( (JavascriptExecutor) driver ).executeScript(
                "var g = window.gokuProps || {};"
                        + "var scripts = Array.prototype.slice.call(document.querySelectorAll('script[src]'))"
                        + "  .map(function(s) { return s.src || ''; });"
                        + "function find(substr) {"
                        + "  for (var i = 0; i < scripts.length; i++) {"
                        + "    if (scripts[i].indexOf(substr) >= 0) return scripts[i];"
                        + "  }"
                        + "  return null;"
                        + "}"
                        + "var key = g.key || null;"
                        + "var iv = g.iv || null;"
                        + "var context = g.context || null;"
                        + "var el = document.querySelector('awswaf-captcha');"
                        + "if (el && el.config) {"
                        + "  key = key || el.config.apiKey || el.config.key || null;"
                        + "}"
                        + "try {"
                        + "  var entries = performance.getEntriesByType('resource') || [];"
                        + "  for (var i = 0; i < entries.length; i++) {"
                        + "    var n = entries[i].name || '';"
                        + "    if (n.indexOf('captcha-sdk.awswaf.com') < 0) continue;"
                        + "    var m = /[?&]api_key=([^&]+)/.exec(n);"
                        + "    if (m && !key) { key = decodeURIComponent(m[1]); }"
                        + "  }"
                        + "} catch (e) {}"
                        + "return {"
                        + "  key: key,"
                        + "  iv: iv,"
                        + "  context: context,"
                        + "  challengeScript: find('challenge.js'),"
                        + "  captchaScript: find('captcha.js'),"
                        + "  jsapiScript: find('jsapi.js'),"
                        + "  userAgent: navigator.userAgent || null"
                        + "};" );
        if ( false == ( raw instanceof Map ) ) {
            throw new UnrecoverableFault( "Unable to extract AWS WAF params from page" );
        }
        Map<String, Object> map = (Map<String, Object>) raw;
        String key = map.get( "key" ) == null ? null : map.get( "key" ).toString();
        String iv = map.get( "iv" ) == null ? null : map.get( "iv" ).toString();
        String context = map.get( "context" ) == null ? null : map.get( "context" ).toString();
        String challengeScript = map.get( "challengeScript" ) == null ? null : map.get( "challengeScript" ).toString();
        String captchaScript = map.get( "captchaScript" ) == null ? null : map.get( "captchaScript" ).toString();
        String jsapiScript = map.get( "jsapiScript" ) == null ? null : map.get( "jsapiScript" ).toString();
        String userAgent = map.get( "userAgent" ) == null ? null : map.get( "userAgent" ).toString();
        if ( StringUtils.isBlank( key ) ) {
            throw new UnrecoverableFault( "AWS WAF websiteKey missing from page" );
        }
        if ( StringUtils.isBlank( jsapiScript ) && ( StringUtils.isBlank( iv ) || StringUtils.isBlank( context ) ) ) {
            throw new UnrecoverableFault( "AWS WAF iv/context missing and no jsapiScript on page" );
        }
        return new AmazonWafChallengeParams( driver.getCurrentUrl(), key, iv, context,
                challengeScript, captchaScript, jsapiScript, userAgent );
    }

    private void injectAmazonWafSolution( WebDriver driver, AmazonWafSolution solution ) {
        LOGGER.info( "Injecting AWS WAF solution taskId={}", solution.getTaskId() );
        Map<String, String> cookies = new java.util.LinkedHashMap<>( solution.getCookies() );
        if ( StringUtils.isNotBlank( solution.getExistingToken() )
                && false == cookies.containsKey( "aws-waf-token" ) ) {
            cookies.put( "aws-waf-token", solution.getExistingToken() );
        }

        boolean cookieBagSolution = false == cookies.isEmpty()
                && StringUtils.isBlank( solution.getCaptchaVoucher() );

        if ( cookieBagSolution ) {
            applyBookingWafCookies( driver, cookies );
            try {
                ( (JavascriptExecutor) driver ).executeScript(
                        "var token = arguments[0];"
                                + "try {"
                                + "  var el = document.querySelector('awswaf-captcha');"
                                + "  if (el && el.config && typeof el.config.onSuccess === 'function' && token) {"
                                + "    el.config.onSuccess(token);"
                                + "  }"
                                + "} catch (e) {}",
                        solution.getExistingToken() );
            }
            catch ( Exception e ) {
                LOGGER.debug( "awswaf-captcha.onSuccess after cookie set: {}", e.toString() );
            }
            LOGGER.info( "AWS WAF cookies applied on {} names={}; reloading",
                    driver.getCurrentUrl(), cookies.keySet() );
            driver.navigate().refresh();
            sleep( 2 );
            return;
        }

        Object applied = ( (JavascriptExecutor) driver ).executeScript(
                "var voucher = arguments[0];"
                        + "var token = arguments[1];"
                        + "var applied = false;"
                        + "try {"
                        + "  if (voucher && window.CaptchaScript && typeof window.CaptchaScript.callback === 'function') {"
                        + "    window.CaptchaScript.callback({ captcha_voucher: voucher, existing_token: token });"
                        + "    applied = 'CaptchaScript.callback';"
                        + "  }"
                        + "} catch (e) { console && console.log && console.log(e); }"
                        + "try {"
                        + "  if (!applied && voucher && window.ChallengeScript"
                        + "      && typeof window.ChallengeScript.submitCaptcha === 'function') {"
                        + "    window.ChallengeScript.submitCaptcha(voucher, token);"
                        + "    applied = 'ChallengeScript.submitCaptcha';"
                        + "  }"
                        + "} catch (e) { console && console.log && console.log(e); }"
                        + "try {"
                        + "  var el = document.querySelector('awswaf-captcha');"
                        + "  if (!applied && el && el.config && typeof el.config.onSuccess === 'function' && token) {"
                        + "    el.config.onSuccess(token);"
                        + "    applied = 'awswaf-captcha.onSuccess';"
                        + "  }"
                        + "} catch (e) { console && console.log && console.log(e); }"
                        + "return applied;",
                solution.getCaptchaVoucher(), solution.getExistingToken() );
        LOGGER.info( "AWS WAF solution applied via: {}", applied );
        if ( StringUtils.isNotBlank( solution.getExistingToken() ) ) {
            java.util.Map<String, String> tokenCookie = new java.util.LinkedHashMap<>();
            tokenCookie.put( "aws-waf-token", solution.getExistingToken() );
            applyBookingWafCookies( driver, tokenCookie );
        }
        if ( applied == null || Boolean.FALSE.equals( applied ) || "".equals( applied ) ) {
            if ( StringUtils.isBlank( solution.getExistingToken() ) ) {
                throw new UnrecoverableFault( "Unable to inject AWS WAF solution into page" );
            }
            LOGGER.warn( "No WAF JS callback available; applied aws-waf-token cookie only" );
        }
        driver.navigate().refresh();
        sleep( 2 );
    }

    /**
     * Sets WAF/session cookies for the registrable domain so they are sent to both
     * {@code account.booking.com} and {@code www.booking.com} challenge endpoints.
     */
    void applyBookingWafCookies( WebDriver driver, Map<String, String> cookies ) {
        for ( Map.Entry<String, String> e : cookies.entrySet() ) {
            String name = e.getKey();
            String value = e.getValue();
            if ( "existing_token".equals( name ) || StringUtils.isBlank( name ) || value == null ) {
                continue;
            }
            String cookieValue = StringUtils.substringBefore( value, ";" );
            try {
                try {
                    driver.manage().deleteCookieNamed( name );
                }
                catch ( Exception ignored ) {
                    // cookie may not exist yet
                }
                Cookie cookie = new Cookie.Builder( name, cookieValue )
                        .domain( AWS_WAF_COOKIE_DOMAIN )
                        .path( "/" )
                        .isSecure( true )
                        .sameSite( "Lax" )
                        .build();
                driver.manage().addCookie( cookie );
            }
            catch ( Exception ex ) {
                LOGGER.warn( "Selenium cookie {} failed ({}): falling back to document.cookie", name, ex.toString() );
                ( (JavascriptExecutor) driver ).executeScript(
                        "document.cookie = arguments[0] + '=' + arguments[1]"
                                + " + '; Domain=.booking.com; Path=/; Secure; SameSite=Lax';",
                        name, cookieValue );
            }
        }
        StringBuilder present = new StringBuilder();
        for ( Cookie c : driver.manage().getCookies() ) {
            if ( cookies.containsKey( c.getName() ) || "aws-waf-token".equals( c.getName() ) ) {
                if ( present.length() > 0 ) {
                    present.append( ", " );
                }
                present.append( c.getName() ).append( "(domain=" ).append( c.getDomain() ).append( ")" );
            }
        }
        LOGGER.info( "WAF cookies now in jar: {}", present );
    }

    private void saveAwsWafDebugArtifacts( WebDriver driver, int attempt ) {
        try {
            File scrFile = ( (TakesScreenshot) driver ).getScreenshotAs( OutputType.FILE );
            File dest = new File( "logs/bdc-aws-waf-attempt-" + attempt + "-" + System.currentTimeMillis() + ".png" );
            dest.getParentFile().mkdirs();
            FileUtils.copyFile( scrFile, dest );
            LOGGER.info( "Saved AWS WAF debug screenshot to {}", dest.getAbsolutePath() );
        }
        catch ( Exception e ) {
            LOGGER.warn( "Unable to save AWS WAF screenshot: {}", e.toString() );
        }
        try {
            LOGGER.info( "AWS WAF page URL: {}", driver.getCurrentUrl() );
            LOGGER.debug( "AWS WAF page source: {}", driver.getPageSource() );
        }
        catch ( Exception e ) {
            LOGGER.warn( "Unable to dump AWS WAF page source: {}", e.toString() );
        }
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
