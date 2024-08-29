
package com.macbackpackers.scrapers;

import com.macbackpackers.beans.CardDetails;
import com.macbackpackers.beans.bdc.BookingComRefundRequest;
import com.macbackpackers.dao.WordPressDAO;
import com.macbackpackers.exceptions.MissingUserDataException;
import com.macbackpackers.services.AuthenticationService;
import com.macbackpackers.services.BasicCardMask;
import com.macbackpackers.services.FileService;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.SystemUtils;
import org.htmlunit.WebClient;
import org.htmlunit.WebWindow;
import org.htmlunit.html.HtmlAnchor;
import org.htmlunit.html.HtmlButton;
import org.htmlunit.html.HtmlElement;
import org.htmlunit.html.HtmlOption;
import org.htmlunit.html.HtmlPage;
import org.htmlunit.html.HtmlPasswordInput;
import org.htmlunit.html.HtmlSelect;
import org.htmlunit.html.HtmlSpan;
import org.htmlunit.html.HtmlTableCell;
import org.htmlunit.html.HtmlTableRow;
import org.htmlunit.html.HtmlTextInput;
import org.openqa.selenium.NoSuchElementException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.math.BigDecimal;
import java.text.MessageFormat;
import java.text.ParseException;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Component
public class BookingComScraper {

    private final Logger LOGGER = LoggerFactory.getLogger( getClass() );

    @Autowired
    private WordPressDAO wordPressDAO;

    @Autowired
    private FileService fileService;

    @Autowired
    private AuthenticationService authService;

    /** for saving login credentials */
    private static final String COOKIE_FILE = "bdc.cookies";
    
    /**
     * Logs into BDC providing the necessary credentials.
     * 
     * @param webClient
     * @throws IOException
     */
    public synchronized void doLogin( WebClient webClient ) throws IOException {
        fileService.loadCookiesFromFile( webClient, COOKIE_FILE );
        doLogin( webClient, wordPressDAO.getOption( "hbo_bdc_username" ),
                wordPressDAO.getOption( "hbo_bdc_password" ) );
    }

    /**
     * Logs into BDC with the necessary credentials.
     * 
     * @param webClient
     * @param username user credentials
     * @param password user credentials
     * @throws IOException
     */
    public synchronized void doLogin( WebClient webClient, String username, String password ) throws IOException {

        if ( username == null || password == null ) {
            throw new MissingUserDataException( "Missing BDC username/password" );
        }

        // don't use session-tracked URL if running locally (ie. for debugging)
        String lasturl = wordPressDAO.getOption( SystemUtils.IS_OS_WINDOWS || SystemUtils.IS_OS_MAC ? "hbo_bdc_lasturl_win" : "hbo_bdc_lasturl" );
        HtmlPage currentPage = webClient.getPage( lasturl == null ? "https://admin.booking.com/hotel/hoteladmin/extranet_ng/manage/home.html" : lasturl );
        LOGGER.info( "Loading Booking.com website: " + currentPage.getBaseURL() );

        if ( currentPage.getBaseURL().getPath().startsWith( "/sign-in" ) ) {
            LOGGER.info( "Doesn't look like we're logged in. Logging into Booking.com" );
            doLoginForm( webClient, username, password );
        }

        // if we're actually logged in, we should get the hostel name identified here...
        currentPage = getCurrentPage( webClient );
        LOGGER.info( "Current URL: " + currentPage.getBaseURL() );
        LOGGER.info( "Property name identified as: " + currentPage.getTitleText() );

        // verify we are logged in
        if ( false == currentPage.getBaseURL().getPath().startsWith( "/hotel/hoteladmin/extranet_ng/manage/home.html" ) ) {
            LOGGER.info( "Current URL: " + currentPage.getBaseURL() );
            LOGGER.info( currentPage.getWebResponse().getContentAsString() );
            throw new MissingUserDataException( "Are we logged in? Unexpected URL." );
        }

        LOGGER.info( "Logged into Booking.com. Saving current URL." );
        LOGGER.info( "Loaded " + currentPage.getBaseURL() );
        wordPressDAO.setOption( SystemUtils.IS_OS_WINDOWS || SystemUtils.IS_OS_MAC ? "hbo_bdc_lasturl_win" : "hbo_bdc_lasturl", currentPage.getBaseURL().toString() );
    }

    /**
     * Performs sign-in from the sign-in screen.
     * 
     * @param webClient
     * @param username user credentials
     * @param password user credentials
     * @throws IOException
     */
    private void doLoginForm( WebClient webClient, String username, String password ) throws IOException {

        webClient.getPage( getCurrentPage( webClient ).getBaseURL().toString() ); // need to reload page for some reason
        HtmlPage page = getCurrentPage( webClient );
        HtmlTextInput usernameField = page.getHtmlElementById( "loginname" );
        usernameField.type( username );
        HtmlButton nextButton = page.getFirstByXPath( "//span[text()='Next']/.." );
        page = nextButton.click();

        int numberOfTasks = webClient.waitForBackgroundJavaScript( 30000 );
        if ( numberOfTasks > 0 ) {
            LOGGER.info( "Still waiting on {} javascript tasks to finish...", numberOfTasks );
        }
        HtmlPasswordInput passwordField = page.getHtmlElementById( "password" );
        passwordField.type( password );

        nextButton = page.getFirstByXPath( "//span[text()='Sign in']/.." );
        nextButton.click();

        if ( page.getBaseURL().getPath().startsWith( "/sign-in/verification" ) ||
                page.getBaseURL().getPath().startsWith( "/2fa/" ) ) {
            // if this is the first time we're doing this, we'll need to go thru 2FA
            LOGGER.info( "BDC verification required" );
            List<HtmlElement> phoneLinks = page.getByXPath( "//a[contains(@class, 'nw-call-verification-link')] | //input[@value='call']" );
            List<HtmlElement> smsLinks = page.getByXPath( "//a[contains(@class, 'nw-sms-verification-link')] | //input[@value='sms']" );

            String verificationMode = wordPressDAO.getOption( "hbo_bdc_verificationmode" );
            if ( "sms".equalsIgnoreCase( verificationMode ) && smsLinks.size() > 0 ) {
                LOGGER.info( "Performing SMS verification" );
                smsLinks.get( 0 ).click();
                HtmlElement selectedPhone = page.getFirstByXPath( "//*[@id='selected_phone'] | //select[@name='phone_id_sms']" );
                String last4digits = wordPressDAO.getMandatoryOption("hbo_bdc_sms_last4digits" );
                if ( false == selectedPhone.getVisibleText().trim().endsWith( last4digits ) ) {
                    // check if there is more than one number available
                    LOGGER.info( "More than one number available? " + selectedPhone.getVisibleText() );
                    HtmlOption phone = page.getFirstByXPath( "//select[@id='selected_phone']/option[contains(text(),'****" + last4digits + "')]" );
                    if( phone != null ) {
                        phone.setSelected( true );
                    }
                    else {
                        throw new MissingUserDataException("Phone number not registered: " + selectedPhone.getVisibleText());
                    }
                }

                HtmlElement send2faCode = page.getFirstByXPath( "//span[text()='Send verification code'] "
                        + "| //div[contains(@class,'cta-phone')]/input[@value='Send text message']" );
                send2faCode.click();

                // now blank out the code and wait for it to appear
                HtmlElement code2fa = page.getFirstByXPath( "//*[@id='sms_code' or @id='ask_pin_input']" );
                code2fa.type( authService.fetchBDC2FACode() );

                HtmlElement verifyNow = page.getFirstByXPath( "//span[text()='Verify now']/.. | //div[contains(@class,'ctas')]/input[@value='Verify now']" );
                verifyNow.click();
            }
            else if ( "phone".equalsIgnoreCase( verificationMode ) && phoneLinks.size() > 0 ) {
                LOGGER.info( "Performing phone verification" );
                phoneLinks.get( 0 ).click();
                nextButton = page.getFirstByXPath( "//span[text()='Call now']/.." );
                nextButton.click();

                HtmlElement code2fa = page.getFirstByXPath( "//*[@id='sms_code' or @id='ask_pin_input']" );
                code2fa.type( authService.fetchBDC2FACode() );

                HtmlElement verifyNow = page.getFirstByXPath( "//span[text()='Verify now']/.. | //div[contains(@class,'ctas')]/input[@value='Verify now']" );
                verifyNow.click();
            }
            else {
                throw new MissingUserDataException( "Verification required for BDC?" );
            }
        }

        numberOfTasks = webClient.waitForBackgroundJavaScript( 60000 );
        if ( numberOfTasks > 0 ) {
            LOGGER.info( "Still waiting on {} javascript tasks to finish...", numberOfTasks );
        }

        page = getCurrentPage( webClient );
        if ( page.getBaseURL().getPath().startsWith( "/sign-in" ) ) {
            LOGGER.error( page.getBaseURL().getPath() );
            LOGGER.error( page.asXml() );
            throw new MissingUserDataException( "Failed to login to BDC" );
        }
        
        fileService.writeCookiesToFile( webClient, COOKIE_FILE );
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
     * Returns the hotel id from the URL.
     * 
     * @param url
     * @return non-null hotel id
     * @throws NoSuchElementException if not found
     */
    private String getHotelIdFromURL( String url ) {
        Pattern p = Pattern.compile( "hotel_id=([\\d]+)" );
        Matcher m = p.matcher( url );
        if ( m.find() ) {
            return m.group( 1 );
        }
        throw new NoSuchElementException( "Couldn't find hotel id from URL: " + url );
    }

    /**
     * Looks up a given reservation in BDC.
     * 
     * @param webClient
     * @param reservationId the BDC reference
     * @throws IOException
     */
    public synchronized void lookupReservation( WebClient webClient, String reservationId ) throws IOException {
        doLogin( webClient );

        // load reservation from URL
        String currentUrl = getCurrentPage( webClient ).getBaseURL().toString();
        String reservationUrl = MessageFormat.format( "https://admin.booking.com/hotel/hoteladmin/extranet_ng/manage/booking.html?res_id={0}&ses={1}&lang=en&hotel_id={2}",
                reservationId, getSessionFromURL( currentUrl ), getHotelIdFromURL( currentUrl ) );
        LOGGER.info( "Looking up reservation " + reservationId + " using URL " + reservationUrl );
        HtmlPage currentPage = webClient.getPage( reservationUrl );
        LOGGER.info( "Loaded " + currentPage.getBaseURL() );

        // multiple places where the booking reference can appear; it should be in one of these
        LOGGER.info( "Looking up reservation ID by hidden field." );
        String BOOKING_NUMBER_XPATH = "//input[@type='hidden' and @name='res_id'] "
                + "| //p/span[text()='Booking number:']/../following-sibling::p "
                + "| //div[not(contains(@class, 'hidden-print'))]/span[normalize-space(text())='Booking number:']/following-sibling::span";
        HtmlElement bookingNumberField = getElementByXPath( webClient, BOOKING_NUMBER_XPATH );
        String resIdFromPage = "input".equals( bookingNumberField.getTagName() ) ? bookingNumberField.getAttribute( "value" ) : bookingNumberField.getVisibleText();

        if ( false == reservationId.equals( resIdFromPage ) ) {
            LOGGER.error( "Reservation ID mismatch?!: Expected " + reservationId + " but found " + resIdFromPage );
            LOGGER.info( currentPage.getWebResponse().getContentAsString() );
            throw new IOException( "Unable to load reservation details. Reservation ID mismatch!" );
        }
    }

    /**
     * Looks up a given reservation in BDC and returns the virtual card balance on the booking.
     * 
     * @param webClient
     * @param reservationId the BDC reference
     * @return the amount available on the VCC
     * @throws IOException if unable to login
     * @throws NoSuchElementException if VCC details not found
     */
    public synchronized BigDecimal getVirtualCardBalance( WebClient webClient, String reservationId ) throws IOException, NoSuchElementException {
        lookupReservation( webClient, reservationId );

        // Click on VCC confirmation dialog "VCC changes for Covid 19"
        HtmlPage currentPage = getCurrentPage( webClient );
        List<HtmlButton> okayGotItButtons = currentPage.getByXPath( "//button/span/span[text()='Okay, got it']/../.." );
        if ( okayGotItButtons.size() > 0 ) {
            okayGotItButtons.get( 0 ).click();
        }

        try {
            // two different views of a booking? this one is from CRH
            LOGGER.info( "Looking up VCC balance (attempt 1)" );
            return fetchVccBalanceFromPage( webClient,
                    "//p[@class='reservation_bvc--balance']",
                    Pattern.compile( "Virtual card balance: £(\\d+\\.?\\d*)" ),
                    e -> e.getVisibleText() );
        }
        catch ( NoSuchElementException ex ) {
            try { // this view is from HSH
                LOGGER.info( "Looking up VCC balance (attempt 2)" );
                return fetchVccBalanceFromPage( webClient,
                        "//div/span[normalize-space(text())='Virtual card balance:']/../following-sibling::div",
                        Pattern.compile( "£(\\d+\\.?\\d*)" ),
                        e -> getTextNode( e ) ); // remove any subelements in case of a partial charge
            }
            catch ( NoSuchElementException ex2 ) {
                LOGGER.info( "Unable to find VCC amount to charge." );
                // the next line will either a) display that we have already fully charged the VCC or
                // b) throw an exception if we can't find anything wrt the VCC amount to charge
                HtmlElement elem = currentPage.getFirstByXPath( "//div[contains(@class, 'fully_charged')] | //span[contains(text(),'successfully charged the total amount')]" );
                if ( elem != null ) {
                    LOGGER.info( "Looks like we've already charged it! BDC message: " + elem.getVisibleText() );
                }
                return BigDecimal.ZERO;
            }
        }
    }

    private BigDecimal fetchVccBalanceFromPage( WebClient webClient, String xpath, Pattern p, Function<HtmlElement, String> fn ) {
        List<HtmlElement> elem = getCurrentPage( webClient ).getByXPath( xpath );
        if ( elem.isEmpty() ) {
            throw new NoSuchElementException( "Couldn't find virtual card balance from '" + xpath );
        }
        String totalAmount = fn.apply( elem.get( 0 ) );

        LOGGER.info( "Matched " + totalAmount + " from " + xpath );
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
    private static String getTextNode( HtmlElement e ) {
        String text = e.getVisibleText().trim();
        List<HtmlElement> children = e.getByXPath( "./*" );
        for ( HtmlElement child : children ) {
            text = text.replaceFirst( child.getVisibleText(), "" ).trim();
        }
        return text;
    }

    /**
     * Mark credit card for the given reservation as invalid.
     * 
     * @param webClient
     * @param reservationId BDC reservation
     * @param last4Digits last 4 digits of CC
     * @throws IOException
     */
    public synchronized void markCreditCardAsInvalid( WebClient webClient, String reservationId, String last4Digits ) throws IOException {
        lookupReservation( webClient, reservationId );
        LOGGER.info( "Marking card ending in " + last4Digits + " as invalid for reservation " + reservationId );

        HtmlPage currentPage = getCurrentPage( webClient );
        List<HtmlElement> headerMarkInvalid = currentPage.getByXPath( "//button[span/span[text()='Mark credit card as invalid']]" );
        if ( headerMarkInvalid.isEmpty() ) {
            LOGGER.info( "Link not available (or already marked invalid). Nothing to do..." );
            return;
        }
        headerMarkInvalid.get( 0 ).click();

        HtmlElement last4DigitsInput = currentPage.getHtmlElementById( "last-digits" );
        last4DigitsInput.type( last4Digits );

        HtmlSelect select = currentPage.getHtmlElementById("reason");
        HtmlOption option = select.getOptionByValue("declined");
        select.setSelectedAttribute(option, true);

        HtmlElement confirmBtn = getElementByXPath( webClient, "//button[span/span[text()='Confirm']]" );
        currentPage = confirmBtn.click();

        String CLOSE_MODAL_BTN = "//aside[header/h1/span[text()='Mark credit card as invalid']]/footer/button[span/span[text()='Close']]";
        HtmlButton closeBtn = currentPage.getFirstByXPath( CLOSE_MODAL_BTN );
        if ( closeBtn == null ) {
            LOGGER.error( "Could not find Close (window) button? Assuming task completed anyways..." );
            LOGGER.error( currentPage.asXml() );
        }
        else {
            closeBtn.click();
            LOGGER.info( "Card marked as invalid." );
        }
    }

    /**
     * Retrieves the card details for the given booking.
     * 
     * @param webClient
     * @param bdcReservation BDC reservation
     * @return credit card details
     * @throws IOException
     * @throws ParseException on parse error during retrieval
     * @throws MissingUserDataException if card details are missing
     */
    public synchronized CardDetails returnCardDetailsForBooking( WebClient webClient, String bdcReservation ) throws IOException, ParseException {
        lookupReservation( webClient, bdcReservation );
        HtmlPage currentPage = getCurrentPage( webClient );
        String currentUrl = getCurrentPage( webClient ).getBaseURL().toString();

        String vccUrl = MessageFormat.format( "https://secure-admin.booking.com/booking_cc_details.html?lang=en&bn={0}&hotel_id={1}&ses={2}&has_bvc=1",
                bdcReservation, getHotelIdFromURL( currentUrl ), getSessionFromURL( currentUrl ) );
        LOGGER.info( "Looking up VCC card details " + vccUrl );
        currentPage = webClient.getPage( vccUrl );

        // we may or may not need to login again to view CC details
        final String SIGN_IN_LOCATOR_PATH = "//span[normalize-space(text())='Sign in to view credit card details']";
        final String CC_DETAILS_PATH = "//th[contains(text(),'Credit Card Details')] | //th[contains(text(),'credit card details')]";
        final String CC_DETAILS_NOT_AVAIL_PATH = "//h2[contains(text(),\"credit card details aren't available\")]";
        final String CONTINUE_WITH_CC_DETAILS = "//p[normalize-space(text())='Continue to view the credit card details.']";

        Optional<Runnable> CLOSE_WINDOW_TASK = Optional.empty();
        if ( currentPage.getFirstByXPath( CC_DETAILS_NOT_AVAIL_PATH ) != null ) {
            throw new MissingUserDataException( "Credit card details aren't available." );
        }
        else if ( currentPage.getFirstByXPath( CONTINUE_WITH_CC_DETAILS ) != null ) {
             doLoginForm( webClient, wordPressDAO.getOption( "hbo_bdc_username" ), wordPressDAO.getOption( "hbo_bdc_password" ) );
        }
        else if ( currentPage.getFirstByXPath( SIGN_IN_LOCATOR_PATH ) != null ) {
            // the following should open a new window; switch to new window
            LOGGER.info( "Logging in again to view CC details..." );
            final WebWindow CURRENT_WINDOW = webClient.getCurrentWindow();
            HtmlSpan signInToView = currentPage.getFirstByXPath( SIGN_IN_LOCATOR_PATH );
            signInToView.click(); // this should open a new window

            // switch to other window
            Optional<WebWindow> otherWindow = webClient.getWebWindows().stream()
                    .filter( w -> CURRENT_WINDOW != w )
                    .findFirst();
            if ( otherWindow.isPresent() ) {
                webClient.setCurrentWindow( otherWindow.get() );
            }
            else {
                throw new IOException( "Unable to find new login window." );
            }

            // login again
            doLoginForm( webClient, wordPressDAO.getOption( "hbo_bdc_username" ), wordPressDAO.getOption( "hbo_bdc_password" ) );

            CLOSE_WINDOW_TASK = Optional.of( () -> {
                HtmlElement btn = getElementByXPath( webClient, "//div[contains(@class,'sbm')]/button[normalize-space(text())='Close']" );
                try {
                    btn.click(); // this should close the window
                }
                catch ( IOException ex ) {
                    throw new RuntimeException( ex );
                }
                webClient.setCurrentWindow( CURRENT_WINDOW ); // switch back to main tab
            } );
        }
        else if ( getElementByXPath( webClient, CC_DETAILS_PATH ) == null ) {
            LOGGER.info( getCurrentPage( webClient ).getWebResponse().getContentAsString() );
            throw new MissingUserDataException( "Expecting credit card details page but not found?" );
        }

        CardDetails cardDetails = new CardDetails();
        cardDetails.setName( getElementByXPath( webClient, "//td[text()=\"Card holder's name:\"]/following-sibling::td" ).getVisibleText().trim() );
        cardDetails.setCardNumber( getElementByXPath( webClient, "//td[text()='Card number:']/following-sibling::td" ).getVisibleText().replaceAll( "\\s", "" ) );
        cardDetails.setCardType( getElementByXPath( webClient, "//td[text()='Card type:']/following-sibling::td" ).getVisibleText().trim() );
        cardDetails.setExpiry( parseExpiryDate( getElementByXPath( webClient, "//td[contains(text(),'Expiration')]/following-sibling::td" ).getVisibleText().trim() ) );
        cardDetails.setCvv( StringUtils.trimToNull( getElementByXPath( webClient, "//td[contains(text(),'CVC')]/following-sibling::td" ).getVisibleText() ) );
        LOGGER.info( "Retrieved card: " + new BasicCardMask().applyCardMask( cardDetails.getCardNumber() ) + " for " + cardDetails.getName() );

        // we're done here; close the newly opened window
        CLOSE_WINDOW_TASK.ifPresent( t -> t.run() );
        return cardDetails;
    }

    /**
     * Searches for all VCC bookings that can be charged immediately.
     * 
     * @param webClient
     * @return non-null list of BDC booking refs
     * @throws IOException
     */
    public synchronized List<String> getAllVCCBookingsThatCanBeCharged( WebClient webClient ) throws IOException {
        doLogin( webClient );

        // load Virtual cards to charge page
        String currentUrl = getCurrentPage( webClient ).getBaseURL().toString();
        String vccUrl = MessageFormat.format( "https://admin.booking.com/hotel/hoteladmin/extranet_ng/manage/vccs_management.html?lang=en&ses={0}&hotel_id={1}&route=vccs_to_charge",
                getSessionFromURL( currentUrl ), getHotelIdFromURL( currentUrl ) );
        LOGGER.info( "Looking up VCCs to charge " + vccUrl );
        HtmlPage currentPage = webClient.getPage( vccUrl );

        LOGGER.info( "Virtual cards to charge..." );
        List<HtmlTableRow> reservationRows = currentPage.getByXPath( "//div[div/h2/span/text()='Virtual cards to charge']/div/div/table/tbody/tr" );
        return reservationRows.stream()
                .filter(r -> com.macbackpackers.services.PaymentProcessorService.isChargeableAmount( HtmlTableCell.class.cast(r.getFirstByXPath("td[@data-heading='Amount']")).getVisibleText()))
                .map(r -> HtmlAnchor.class.cast(r.getFirstByXPath("th/span/a")).getVisibleText())
                .collect(Collectors.toList());
    }

    /**
     * Searches for all VCC bookings that need to be refunded.
     *
     * @param webClient
     * @return non-null list of BDC booking refs
     * @throws IOException
     */
    public synchronized List<BookingComRefundRequest> getAllVCCBookingsThatMustBeRefunded( WebClient webClient ) throws IOException {
        doLogin( webClient );

        // load Virtual cards to charge page
        String currentUrl = getCurrentPage( webClient ).getBaseURL().toString();
        String vccUrl = MessageFormat.format( "https://admin.booking.com/hotel/hoteladmin/extranet_ng/manage/vccs_management.html?lang=en&ses={0}&hotel_id={1}",
                getSessionFromURL( currentUrl ), getHotelIdFromURL( currentUrl ) );
        LOGGER.info( "Looking up VCCs to refund " + vccUrl );
        HtmlPage currentPage = webClient.getPage( vccUrl );

        LOGGER.info( "Virtual cards to refund..." );
        List<HtmlTableRow> reservationRows = currentPage.getByXPath( "//div[div/div/h2/span/text()='Virtual cards to refund']/table/tbody/tr" );
        return reservationRows.stream()
                .filter(r -> StringUtils.isBlank(HtmlAnchor.class.cast(r.getFirstByXPath("td[@data-heading='Possible actions']/div/a")).getAttribute("disabled")))
                .map(r -> new BookingComRefundRequest(HtmlAnchor.class.cast(r.getFirstByXPath("th/span/a")).getVisibleText(),
                        HtmlSpan.class.cast(r.getFirstByXPath("td[1]/span")).getVisibleText(),
                        HtmlSpan.class.cast(r.getFirstByXPath("td[3]/span")).getVisibleText()))
                .collect(Collectors.toList());
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

    private HtmlPage getCurrentPage( WebClient webClient ) {
        return HtmlPage.class.cast( webClient.getCurrentWindow().getEnclosedPage() );
    }

    private HtmlElement getElementByXPath( WebClient webClient, String xpath ) {
        return getCurrentPage( webClient ).getFirstByXPath( xpath );
    }
}
