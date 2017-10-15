
package com.macbackpackers.scrapers;

import java.io.IOException;
import java.net.URL;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import com.gargoylesoftware.htmlunit.HttpMethod;
import com.gargoylesoftware.htmlunit.Page;
import com.gargoylesoftware.htmlunit.WebClient;
import com.gargoylesoftware.htmlunit.WebRequest;
import com.gargoylesoftware.htmlunit.WebResponse;
import com.gargoylesoftware.htmlunit.html.HtmlAnchor;
import com.gargoylesoftware.htmlunit.html.HtmlInput;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import com.gargoylesoftware.htmlunit.html.HtmlPasswordInput;
import com.gargoylesoftware.htmlunit.html.HtmlTextInput;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.macbackpackers.beans.CardDetails;
import com.macbackpackers.dao.WordPressDAO;
import com.macbackpackers.exceptions.MissingUserDataException;
import com.macbackpackers.exceptions.UnrecoverableFault;
import com.macbackpackers.services.FileService;
import com.macbackpackers.services.GmailService;

@Component
public class AgodaScraper {

    private final Logger LOGGER = LoggerFactory.getLogger( getClass() );

    /** for saving login credentials */
    private static final String COOKIE_FILE = "agoda.cookies";

    @Autowired
    private FileService fileService;

    @Autowired
    private WordPressDAO wordPressDAO;

    @Autowired
    private GmailService gmailService;

    @Value( "${agoda.property.id}" )
    private String agodaPropertyId;

    private static final Pattern CARD_EXPIRY_PATTERN = Pattern.compile( "(\\d{2})/\\d{2}(\\d{2})" );

    /**
     * Logs into Agoda providing the necessary credentials.
     * 
     * @param loginPage the page containing username, password, etc.
     * @return the page after login
     * @throws IOException
     */
    public HtmlPage doLogin( HtmlPage loginPage ) throws IOException {
        return doLogin( loginPage,
                wordPressDAO.getOption( "hbo_agoda_username" ),
                wordPressDAO.getOption( "hbo_agoda_password" ) );
    }

    /**
     * Logs into Agoda providing the necessary credentials.
     * 
     * @param loginPage the page containing username, password, etc.
     * @param username user credentials
     * @param password user credentials
     * @return the page after login
     * @throws IOException
     */
    public HtmlPage doLogin( HtmlPage loginPage, String username, String password ) throws IOException {
        LOGGER.info( "Logging into Agoda" );

        HtmlTextInput usernameField = HtmlTextInput.class.cast( loginPage.getElementById( "Username" ) );
        HtmlPasswordInput passwordField = HtmlPasswordInput.class.cast( loginPage.getElementById( "Password" ) );

        // Change the value of the text fields
        usernameField.setValueAttribute( username );
        passwordField.setValueAttribute( password );
        HtmlAnchor loginLink = loginPage.getFirstByXPath( "//a[@type='submit' and @title='Login']" );

        HtmlPage nextPage = loginLink.click();

        // if this is the first time logging in, retrieve and enter the passcode if requested
        HtmlInput passcodeInput = HtmlInput.class.cast( nextPage.getElementById( "passcode" ) );
        if ( passcodeInput != null ) {
            LOGGER.info( "Passcode requested. Retrieving from Gmail." );
            sleep( 50000 ); // it takes a few seconds for the email to arrive
            String passcode = gmailService.fetchAgodaPasscode();
            LOGGER.info( "Retrieved passcode " + passcode );
            passcodeInput.type( passcode );
            HtmlAnchor btnSubmitPasscode = HtmlAnchor.class.cast( nextPage.getElementById( "btnSubmitPasscode" ) );
            nextPage = btnSubmitPasscode.click();
            LOGGER.info( "Submitted passcode to Agoda" );
        }

        LOGGER.info( "Finished logging in" );
        nextPage.getWebClient().waitForBackgroundJavaScript( 30000 ); // wait for page to load
        LOGGER.debug( nextPage.asXml() );

        // if we get redirected to the login page again...
        if ( nextPage.getElementById( "Password" ) != null ) {
            LOGGER.debug( nextPage.asXml() );
            throw new UnrecoverableFault( "Unable to login to Agoda. Incorrect password?" );
        }

        // save credentials to disk so we don't need to do this again
        // for the immediate future
        fileService.writeCookiesToFile( nextPage.getWebClient(), COOKIE_FILE );
        return nextPage;
    }

    /**
     * Logs in and navigates to the given page. Synchronized to avoid multiple simultaneous logins
     * if there are a bunch of jobs scraping at the same time.
     * 
     * @param webClient web client
     * @param url the HW page to go to
     * @return the accessed page
     * @throws IOException
     */
    public synchronized HtmlPage gotoPage( WebClient webClient, String url ) throws IOException {

        // load most recent cookies from disk first
        fileService.loadCookiesFromFile( webClient, COOKIE_FILE );

        HtmlPage nextPage = webClient.getPage( url );

        // if we got redirected, then login
        if ( nextPage.getElementById( "Password" ) != null ) {

            doLogin( nextPage );
            nextPage = webClient.getPage( url );

            // if we still get redirected to the login page...
            if ( nextPage.getElementById( "Password" ) != null ) {
                throw new UnrecoverableFault( "Unable to login to Agoda! Has the password changed?" );
            }
        }
        return nextPage;
    }

    /**
     * Retrieves the Agoda card details for a booking.
     * 
     * @param webClient web client
     * @param bookingRef Agoda booking reference
     * @return non-null card details
     * @throws IOException on page load error
     * @throws MissingUserDataException on missing data
     */
    public synchronized CardDetails getAgodaCardDetails( WebClient webClient, String bookingRef ) 
            throws IOException, MissingUserDataException {

        // this will load cookies and login to Agoda if we aren't already
        String referrerUrl = "https://ycs.agoda.com/en-us/HotelBookings/Index/" + agodaPropertyId + "?";
        gotoPage( webClient, referrerUrl );

        URL bookingDetailsUrl = new URL( "https://ycs.agoda.com/en-us/HotelBookings/GetDetails/" + agodaPropertyId );
        LOGGER.info( "Attempting POST to " + bookingDetailsUrl );
        WebRequest requestSettings = new WebRequest( bookingDetailsUrl, HttpMethod.POST );
        requestSettings.setAdditionalHeader( "Accept", "application/json, text/javascript, */*; q=0.01" );
        requestSettings.setAdditionalHeader( "Content-Type", "application/x-www-form-urlencoded; charset=UTF-8" );
        requestSettings.setAdditionalHeader( "Referer", referrerUrl );
        requestSettings.setAdditionalHeader( "Accept-Language", "en-GB,en-US;q=0.8,en;q=0.6" );
        requestSettings.setAdditionalHeader( "Accept-Encoding", "gzip, deflate, br" );
        requestSettings.setAdditionalHeader( "X-Requested-With", "XMLHttpRequest" );
        requestSettings.setAdditionalHeader( "Cache-Control", "no-cache" );
        requestSettings.setAdditionalHeader( "Pragma", "no-cache" );
        requestSettings.setAdditionalHeader( "Origin", "https://ycs.agoda.com" );

        requestSettings.setRequestBody( "bookingIds=" + bookingRef );

        Page redirectPage = webClient.getPage( requestSettings );
        WebResponse webResponse = redirectPage.getWebResponse();
        if ( webResponse.getContentType().equalsIgnoreCase( "application/json" ) ) {
            return parseCardDetails( redirectPage.getWebResponse().getContentAsString() );
        }
        throw new IOException( "Unexpected response type: " + webResponse.getContentType() );
    }

    /**
     * Parse card details from the Agoda JSON response.
     * 
     * @param jsonResponse JSON data
     * @return non-null card details
     * @throws MissingUserDataException on missing data
     */
    private CardDetails parseCardDetails( String jsonResponse ) throws MissingUserDataException {
        JsonElement jelement = new JsonParser().parse( jsonResponse );
        JsonObject jobject = jelement.getAsJsonObject();
        JsonArray jarray = jobject.getAsJsonArray( "ResponseData" );
        jobject = jarray.get( 0 ).getAsJsonObject();
        jobject = jobject.getAsJsonObject( "ApiData" );
        jobject = jobject.getAsJsonObject( "HotelPayment" );
        String cardExpiry = jobject.get( "CardExpiryDate" ).getAsString(); // MM/YYYY

        Matcher m = CARD_EXPIRY_PATTERN.matcher( cardExpiry );
        if ( m.find() ) {
            return new CardDetails(
                    "2".equals( jobject.get( "CardType" ).getAsString() ) ? "MC" : "VI",
                    jobject.get( "CardHolderName" ).getAsString(),
                    jobject.get( "CardNo" ).getAsString(),
                    m.group( 1 ) + m.group( 2 ),
                    jobject.get( "CardCVV" ).getAsString() );
        }
        else {
            throw new MissingUserDataException( "Unable to parse card expiry" );
        }
    }

    /**
     * Sleep for a few...
     * 
     * @param millis time to sleep in ms
     */
    private void sleep( int millis ) {
        try {
            LOGGER.info( "Waiting for " + millis + " ms." );
            Thread.sleep( millis );
        }
        catch ( InterruptedException e ) {
            LOGGER.info( "Sleep interrupted." );
        }
    }
}
