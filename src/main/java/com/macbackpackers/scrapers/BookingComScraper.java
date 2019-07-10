
package com.macbackpackers.scrapers;

import java.io.File;
import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.gargoylesoftware.htmlunit.WebClient;
import com.gargoylesoftware.htmlunit.html.HtmlAnchor;
import com.gargoylesoftware.htmlunit.html.HtmlButton;
import com.gargoylesoftware.htmlunit.html.HtmlHeading4;
import com.gargoylesoftware.htmlunit.html.HtmlNumberInput;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import com.gargoylesoftware.htmlunit.html.HtmlPasswordInput;
import com.gargoylesoftware.htmlunit.html.HtmlRadioButtonInput;
import com.gargoylesoftware.htmlunit.html.HtmlSearchInput;
import com.gargoylesoftware.htmlunit.html.HtmlSelect;
import com.gargoylesoftware.htmlunit.html.HtmlTextInput;
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
     * @param webClient web client
     * @return the page after login
     * @throws IOException
     */
    public HtmlPage doLogin( WebClient webClient ) throws IOException {
        return doLogin( webClient,
                wordPressDAO.getOption( "hbo_bdc_username" ),
                wordPressDAO.getOption( "hbo_bdc_password" ),
                wordPressDAO.getOption( "hbo_bdc_session" ) );
    }

    /**
     * Logs into BDC with the necessary credentials.
     * 
     * @param webClient web client to use
     * @param username user credentials
     * @param password user credentials
     * @param session previous session id (if available)
     * @return the page after login
     * @throws IOException
     */
    public HtmlPage doLogin( WebClient webClient, String username, String password, String session ) throws IOException {

        if ( new File( COOKIE_FILE ).exists() ) {
            fileService.loadCookiesFromFile( webClient, COOKIE_FILE );
        }

        if ( username == null || password == null ) {
            throw new MissingUserDataException( "Missing BDC username/password" );
        }

        HtmlPage nextPage = webClient.getPage(
                "https://admin.booking.com/hotel/hoteladmin/general/dashboard.html?lang=en&hotel_id="
                        + username + (session == null ? "" : "&ses=" + session) );
        webClient.waitForBackgroundJavaScript( 30000 ); // wait for page to load

        if ( nextPage.getBaseURI().startsWith( "https://account.booking.com/sign-in" ) ) {
            LOGGER.info( "Doesn't look like we're logged in. Logging into Booking.com" );

            HtmlTextInput usernameField = nextPage.getHtmlElementById( "loginname" );
            usernameField.setValueAttribute( username );
            HtmlButton nextButton = nextPage.getFirstByXPath( "//span[text()='Next']/.." );

            nextPage = nextButton.click();
            webClient.waitForBackgroundJavaScript( 30000 ); // wait for page to load

            HtmlPasswordInput passwordField = nextPage.getHtmlElementById( "password" );
            passwordField.setValueAttribute( password );

            nextButton = nextPage.getFirstByXPath( "//span[text()='Sign in']/.." );
            nextPage = nextButton.click();
            webClient.waitForBackgroundJavaScript( 30000 ); // wait for page to load
            LOGGER.info( "Finished logging in?" );
            LOGGER.debug( nextPage.asXml() );

            // required when logging in from a new device
            HtmlAnchor phoneLink = nextPage.getFirstByXPath( "//a[contains(@class, 'nw-call-verification-link')]" );
            if ( phoneLink != null && false == doVerification ) {
                throw new MissingUserDataException( "Verification required for BDC?" );
            }
            else if ( doVerification ) {
                nextPage = phoneLink.click();
                webClient.waitForBackgroundJavaScript( 30000 ); // wait for page to load

                nextButton = nextPage.getFirstByXPath( "//span[text()='Call now']/.." );
                nextPage = nextButton.click();
                webClient.waitForBackgroundJavaScript( 30000 ); // wait for page to load

                HtmlNumberInput verifyInput = nextPage.getHtmlElementById( "sms_code" );
                verifyInput.setValueAttribute( "XXXXXX" ); // set breakpoint here
                nextButton = nextPage.getFirstByXPath( "//span[text()='Verify now']/.." );
                nextPage = nextButton.click();
                LOGGER.info( nextPage.asXml() );
            }
        }

        // if we're actually logged in, we should get the hostel name identified here...
        LOGGER.info( "Property name identified as: " + nextPage.getTitleText() );

        // save credentials to disk so we don't need to do this again
        fileService.writeCookiesToFile( webClient, COOKIE_FILE );
        LOGGER.info( "Logged into Booking.com. Saving current session." );
        updateSession( nextPage );
        LOGGER.debug( nextPage.asXml() );
        return nextPage;
    }

    /**
     * Looks up a given reservation in BDC.
     * 
     * @param webClient
     * @param reservationId the BDC reference
     * @return the loaded BDC page
     * @throws IOException if booking not found or I/O error
     */
    public HtmlPage lookupReservation( WebClient webClient, String reservationId ) throws IOException {

        HtmlPage nextPage = doLogin( webClient );
        HtmlSearchInput searchInput = nextPage.getFirstByXPath( "//input[@placeholder='Search for reservations']" );
        if( searchInput == null ) {
            throw new IOException( "Unable to find search form field." );
        }
        searchInput.setValueAttribute( "" );
        searchInput.type( reservationId + "\n" );
        nextPage.getWebClient().waitForBackgroundJavaScript( 30000 );
        
        HtmlAnchor searchResult = nextPage.getFirstByXPath( "//a[@data-id='" + reservationId + "']" );
        if( searchResult == null ) {
            throw new IOException( "Unable to find reservation " + reservationId );
        }
        HtmlPage reservationPage = searchResult.click();
        reservationPage.getWebClient().waitForBackgroundJavaScript( 5000 );
        
        // if page isn't loaded, try clicking the link again...
        if ( false == reservationPage.getTitleText().contains( "Reservation details" ) ) {
            LOGGER.info( "Reservation details not loaded... clicking link again" );
            reservationPage = searchResult.click();
            reservationPage.getWebClient().waitForBackgroundJavaScript( 30000 );
        }
        LOGGER.info( "Loaded " + reservationPage.getBaseURI() );
        LOGGER.debug( reservationPage.asXml() );
        if ( false == reservationPage.getTitleText().contains( "Reservation details" ) ) {
            throw new IOException( "Unable to load reservation details" );
        }
        
        return reservationPage;
    }

    /**
     * Mark credit card for the given reservation as invalid.
     * 
     * @param webClient
     * @param reservationId BDC reservation
     * @param last4Digits last 4 digits of CC
     * @throws IOException
     */
    public void markCreditCardAsInvalid( WebClient webClient, String reservationId, String last4Digits ) throws IOException {
        HtmlPage bdcPage = lookupReservation( webClient, reservationId );
        LOGGER.info( "Marking card ending in " + last4Digits + " as invalid for reservation " + reservationId );

        if ( bdcPage.getFirstByXPath( "//h4[@id='mark_invalid_new_icc']" ) == null ) {
            LOGGER.info( "Link not available (or already marked invalid). Nothing to do..." );
            return;
        }
        HtmlHeading4 invalidCcHeading = bdcPage.getHtmlElementById( "mark_invalid_new_icc" );
        HtmlPage nextPage = invalidCcHeading.click();
        nextPage.getWebClient().waitForBackgroundJavaScript( 30000 );
        if ( nextPage.getFirstByXPath( "//input[@name='ccnumber']" ) == null ) {
            LOGGER.info( "CC number field not visible? Trying again..." );
            nextPage = invalidCcHeading.click();
            nextPage.getWebClient().waitForBackgroundJavaScript( 30000 );
            LOGGER.debug( nextPage.asXml() );
        }

        HtmlTextInput last4DigitsInput = nextPage.getElementByName( "ccnumber" );
        last4DigitsInput.setValueAttribute( last4Digits );
        HtmlSelect cardInvalidSelect = nextPage.getElementByName( "cc_invalid_reason" );
        cardInvalidSelect.setSelectedAttribute( "declined", true );
        HtmlRadioButtonInput depositRadio = nextPage.getFirstByXPath( "//input[@type='radio' and @name='preprocess_type' and @value='deposit']" );
        nextPage = depositRadio.click();
        HtmlSelect amountType = nextPage.getElementByName( "amount_type" );
        amountType.setSelectedAttribute( "first", true );

        HtmlButton confirmBtn = nextPage.getFirstByXPath( "//button/span[text()='Confirm']/.." );
        nextPage = confirmBtn.click();
        nextPage.getWebClient().waitForBackgroundJavaScript( 30000 );
        LOGGER.info( "Card marked as invalid." );
    }

    /**
     * Updates the current session ID when logged into BDC.
     * 
     * @param loggedInPage currently logged in BDC page.
     */
    private void updateSession( HtmlPage loggedInPage ) {
        Pattern p = Pattern.compile( "ses=([0-9a-f]+)" );
        Matcher m = p.matcher( loggedInPage.getBaseURI() );
        if ( m.find() ) {
            wordPressDAO.setOption( "hbo_bdc_session", m.group( 1 ) );
        }
        else {
            throw new MissingUserDataException( "Unable to find session id from " + loggedInPage.getBaseURI() );
        }
    }

}
