
package com.macbackpackers.services;

import java.io.IOException;

import org.apache.commons.lang3.StringUtils;
import org.htmlunit.WebClient;
import org.htmlunit.html.HtmlPage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import com.macbackpackers.dao.WordPressDAO;
import com.macbackpackers.exceptions.MissingUserDataException;
import com.macbackpackers.exceptions.UnrecoverableFault;
import com.warrenstrange.googleauth.GoogleAuthenticator;

@Service
public class AuthenticationService {

    private final Logger LOGGER = LoggerFactory.getLogger( getClass() );

    @Autowired
    private FileService fileService;

    @Autowired
    private Environment env;

    @Autowired
    private ApplicationContext context;

    @Autowired
    private WordPressDAO wordpressDAO;

    @Autowired
    private ExternalWebService externalWebService;

    // the current site name
    private String siteName;

    /**
     * Loads the previous credentials and attempts to go to a specific page. If we get redirected
     * back to the login page, then this method will fail with an unrecoverable fault.
     * 
     * @param pageURL the page to go to
     * @return the loaded page
     * @throws IOException if unable to read previously saved credentials
     * @throws UnrecoverableFault if credentials were not successful
     */
    public HtmlPage goToPage( String pageURL, WebClient webClient ) throws IOException, UnrecoverableFault {
        fileService.loadCookiesFromFile( webClient );

        // attempt to go the page directly using the current credentials
        LOGGER.info( "Loading page: " + pageURL );
        HtmlPage nextPage = webClient.getPage( pageURL );

        if ( nextPage.getUrl().getPath().contains( "/login" ) ) {
            LOGGER.warn( "Current credentials not valid?" );
            LOGGER.debug( nextPage.asXml() );
            throw new UnrecoverableFault( "Unable to login using existing credentials. Has the password changed?" );
        }
        return nextPage;
    }

    /**
     * Updates the current cookie file with the given LH session key.
     * 
     * @param webClient active web client
     * @throws IOException on i/o error
     */
    public void updateLittleHotelierSessionKey( WebClient webClient ) throws IOException {
        fileService.loadCookiesFromFile( webClient );
        fileService.addLittleHotelierSessionCookie( webClient, wordpressDAO.getOption( "hbo_lilho_session" ) );
        fileService.writeCookiesToFile( webClient );
    }

    /**
     * Returns the time-based one time password for the given secret key.
     * @param key secret key
     * @return 6-digit password
     */
    public int getTotpPassword( String key ) {
        return new GoogleAuthenticator().getTotpPassword( key );
    }

    /**
     * If option hbo_sms_lookup_url is defined, then attempt to lookup on external host. Otherwise,
     * _blanks out_ the 2FA code from the DB and waits for it to be re-populated. This is done
     * outside this application.
     *
     * @param webClient web client
     * @return non-null 2FA code
     * @throws MissingUserDataException on timeout (1 + 10 minutes)
     * @throws IOException
     */
    public String fetchCloudbeds2FACode(WebClient webClient) throws MissingUserDataException, IOException {
        if ( wordpressDAO.getOption("hbo_sms_lookup_url") != null ) {
            LOGGER.info( "waiting 30 seconds before attempting 2FA lookup." );
            sleep( 30 );
            return externalWebService.getLast2faCode(webClient, "cloudbeds");
        }
        return fetch2FACode( "hbo_cloudbeds_2facode" );
    }

    /**
     * Returns the google authenticator one-time code for logging into cloudbeds.
     *
     * @return OTP for cloudbeds or null if not 2FA is not set to google authenticator.
     */
    public String fetchCloudbedsGoogleAuth2faCode() {
        String googleAuth2faSecret = wordpressDAO.getOption( "hbo_cloudbeds_googleauthenticator_2fa_secret" );
        if ( StringUtils.isNotBlank( googleAuth2faSecret ) ) {
            String otp = StringUtils.leftPad( String.valueOf( getTotpPassword( googleAuth2faSecret ) ), 6, '0' );
            return otp;
        }
        return null;
    }

    /**
     * If option hbo_sms_lookup_url is defined, then attempt to lookup on external host. Otherwise,
     * _blanks out_ the 2FA code from the DB and waits for it to be re-populated. This is done
     * outside this application.
     *
     * @return non-null 2FA code
     * @throws MissingUserDataException on timeout (1 + 10 minutes)
     * @throws IOException
     */
    public String fetchBDC2FACode() throws MissingUserDataException, IOException {
        if ( StringUtils.isNotBlank( wordpressDAO.getOption("hbo_sms_lookup_url" ) ) ) {
            LOGGER.info( "waiting 30 seconds before attempting 2FA lookup." );
            sleep( 30 );
            // we'll just re-use the webClient for Cloudbeds so we don't mess up the one for BDC
            try (WebClient c = context.getBean( "webClientForCloudbeds", WebClient.class )) {
                return externalWebService.getLast2faCode(c, "bdc");
            }
        }
        return fetch2FACode( "hbo_bdc_2facode" );
    }

    private String fetch2FACode( String optionName ) throws MissingUserDataException {
        // now blank out the code and wait for it to appear
        LOGGER.info( "waiting for {} to be set...", optionName );
        wordpressDAO.setOption( optionName, "" );
        sleep( 60 );
        // force timeout after 10 minutes (60x10 seconds)
        for ( int i = 0 ; i < 60 ; i++ ) {
            String scaCode = wordpressDAO.getOptionNoCache( optionName );
            if ( StringUtils.isNotBlank( scaCode ) ) {
                return scaCode;
            }
            LOGGER.info( "waiting for another 10 seconds..." );
            sleep( 10 );
        }
        throw new MissingUserDataException( "2FA code timeout waiting for verification." );
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
     * Checks whether the details saved are for high street hostel. Some things are just different
     * there...
     * 
     * @return true if HSH, false otherwise
     */
    public boolean isHighStreetHostel() {
        return "highstreethostel".equals( getSiteName() );
    }

    /**
     * Checks whether the details saved are for royal mile backpackers. Some things are just
     * different there...
     * 
     * @return true if RMB, false otherwise
     */
    public boolean isRoyalMileBackpackers() {
        return "royalmile".equals( getSiteName() );
    }

    /**
     * Returns the site name (everything after the hostname for wordpress).
     * 
     * @return non-null site name
     */
    private String getSiteName() {
        // saved locally on first-access; to prevent unnecessary DB calls
        if ( siteName == null ) {
            String homeurl = wordpressDAO.getOption( "home" );
            siteName = homeurl.substring( StringUtils.lastIndexOf( homeurl, '/' ) + 1 );
        }
        return siteName;
    }

}
