
package com.macbackpackers.services;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import org.apache.http.client.utils.URLEncodedUtils;
import org.htmlunit.HttpMethod;
import org.htmlunit.Page;
import org.htmlunit.WebClient;
import org.htmlunit.WebRequest;
import org.htmlunit.util.NameValuePair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.macbackpackers.beans.CaptchaSolveRequest;
import com.macbackpackers.dao.WordPressDAO;
import com.macbackpackers.exceptions.MissingUserDataException;
import com.macbackpackers.exceptions.UnrecoverableFault;
import com.macbackpackers.scrapers.CloudbedsJsonRequestFactory;
import com.macbackpackers.scrapers.CloudbedsScraper;

/**
 * For solving (re)captchas.
 */
@Service
public class CaptchaSolverService {

    private final Logger LOGGER = LoggerFactory.getLogger( getClass() );

    @Value( "${captcha.maxattempts:20}" )
    private int MAX_SOLVE_ATTEMPTS;

    @Autowired
    @Qualifier( "gsonForCloudbeds" )
    private Gson gson;

    @Autowired
    private WordPressDAO dao;

    @Autowired
    private CloudbedsJsonRequestFactory jsonRequestFactory;

    @Autowired
    private CloudbedsScraper cloudbedsScraper;

    /**
     * Sends a V2 recaptcha request.
     * 
     * @param webClient
     * @param pageUrl page where the recaptcha is found
     * @param key the google sitekey
     * @return captcha ID
     * @throws IOException
     */
    public String recaptchaV2Request( WebClient webClient, String pageUrl, String key ) throws IOException {
        LOGGER.info( "Building recaptcha V2 request for " + pageUrl + " and key " + key );
        WebRequest requestSettings = new WebRequest( new URL( "https://2captcha.com/in.php" ), HttpMethod.GET );
        requestSettings.setRequestParameters( new ArrayList<NameValuePair>( Arrays.asList(
                new NameValuePair( "key", dao.get2CaptchaApiKey() ),
                new NameValuePair( "method", "userrecaptcha" ),
                new NameValuePair( "googlekey", key ),
                new NameValuePair( "pageurl", pageUrl ),
                new NameValuePair( "invisible", "0" ),
                new NameValuePair( "json", "1" ) ) ) );

        String proxy = dao.getOption( "hbo_2captcha_proxy" );
        if ( proxy != null ) {
            requestSettings.getRequestParameters().add( new NameValuePair( "proxy", proxy ) );
            requestSettings.getRequestParameters().add( new NameValuePair( "proxytype",
                    StringUtils.defaultString( dao.getOption( "hbo_2captcha_proxytype" ), "HTTPS" ) ) );
        }

        Page redirectPage = webClient.getPage( requestSettings );
        LOGGER.debug( redirectPage.getWebResponse().getContentAsString() );

        JsonObject rootElem = gson.fromJson( redirectPage.getWebResponse().getContentAsString(), JsonObject.class );
        if ( 1 != rootElem.get( "status" ).getAsInt() ) {
            LOGGER.error( redirectPage.getWebResponse().getContentAsString() );
            throw new UnrecoverableFault( "Unexpected status from 2captcha" );
        }
        return rootElem.get( "request" ).getAsString();
    }

    /**
     * Sends a V3 recaptcha request.
     * 
     * @see https://2captcha.com/2captcha-api#solving_recaptchav3
     * @param webClient
     * @param key the google sitekey
     * @param action action found on site
     * @param pageUrl
     * @return captcha ID
     * @throws IOException
     */
    public String recaptchaV3Request( WebClient webClient, String key, String action, String pageUrl ) throws IOException {
        WebRequest requestSettings = new WebRequest( new URL( "https://2captcha.com/in.php" ), HttpMethod.GET );
        requestSettings.setRequestParameters( new ArrayList<NameValuePair>( Arrays.asList(
                new NameValuePair( "key", dao.get2CaptchaApiKey() ),
                new NameValuePair( "method", "userrecaptcha" ),
                new NameValuePair( "version", "v3" ),
                new NameValuePair( "googlekey", key ),
                new NameValuePair( "pageurl", pageUrl ),
                new NameValuePair( "action", action ),
                new NameValuePair( "min_score", "0.3" ),
                new NameValuePair( "json", "1" ) ) ) );

        String proxy = dao.getOption( "hbo_2captcha_proxy" );
        if ( proxy != null ) {
            requestSettings.getRequestParameters().add( new NameValuePair( "proxy", proxy ) );
            requestSettings.getRequestParameters().add( new NameValuePair( "proxytype",
                    StringUtils.defaultString( dao.getOption( "hbo_2captcha_proxytype" ), "HTTPS" ) ) );
        }

        Page redirectPage = webClient.getPage( requestSettings );
        LOGGER.debug( redirectPage.getWebResponse().getContentAsString() );

        JsonObject rootElem = gson.fromJson( redirectPage.getWebResponse().getContentAsString(), JsonObject.class );
        if ( 1 != rootElem.get( "status" ).getAsInt() ) {
            LOGGER.error( redirectPage.getWebResponse().getContentAsString() );
            throw new UnrecoverableFault( "Unexpected status from 2captcha" );
        }
        return rootElem.get( "request" ).getAsString();
    }

    /**
     * Retrieves response of a Captcha request.
     * 
     * @param webClient
     * @param id 2captcha ID
     * @return the captcha answer!
     * @throws IOException
     */
    public String recaptchaRetrieveResponse( WebClient webClient, String id ) throws IOException {
        WebRequest requestSettings = new WebRequest( new URL( "https://2captcha.com/res.php" ), HttpMethod.GET );
        requestSettings.setRequestParameters( Arrays.asList(
                new NameValuePair( "key", dao.get2CaptchaApiKey() ),
                new NameValuePair( "action", "get" ),
                new NameValuePair( "id", id ),
                new NameValuePair( "taskinfo", "1" ),
                new NameValuePair( "json", "1" ) ) );

        // recaptcha valid for limited time; retry for 5 minutes
        Page redirectPage = null;
        for ( int i = 0 ; i < 20 ; i++ ) {
            try {
                Thread.sleep( 15000 );
            }
            catch ( InterruptedException e ) {
                // nothing to do
            }
            redirectPage = webClient.getPage( requestSettings );
            LOGGER.info( "Captcha response retrieval attempt #" + (i + 1) );
            LOGGER.debug( redirectPage.getWebResponse().getContentAsString() );
            JsonObject rootElem = gson.fromJson( redirectPage.getWebResponse().getContentAsString(), JsonObject.class );
            LOGGER.info( "2captcha response: " + rootElem.get( "request" ).getAsString() );
            if ( 1 == rootElem.get( "status" ).getAsInt() ) {
                return rootElem.get( "request" ).getAsString();
            }
            else if ( "ERROR_CAPTCHA_UNSOLVABLE".equals( rootElem.get( "request" ).getAsString() ) ) {
                break;
            }
        }
        LOGGER.error( redirectPage.getWebResponse().getContentAsString() );
        throw new UnrecoverableFault( "Failed to solve captcha" );
    }

    /**
     * Reports a Captcha that worked!
     * 
     * @param webClient
     * @param id recaptcha ID to report as good
     * @throws IOException
     */
    public void recaptchaReportGood( WebClient webClient, String id ) throws IOException {
        LOGGER.info( "Reporting Captcha as good! " + id );
        WebRequest requestSettings = new WebRequest( new URL( "https://2captcha.com/res.php" ), HttpMethod.GET );
        requestSettings.setRequestParameters( Arrays.asList(
                new NameValuePair( "key", dao.get2CaptchaApiKey() ),
                new NameValuePair( "action", "reportgood" ),
                new NameValuePair( "id", id ) ) );

        Page redirectPage = webClient.getPage( requestSettings );
        LOGGER.info( redirectPage.getWebResponse().getContentAsString() );
    }

    /**
     * Retrieves the Captcha request from the given (Cloudbeds) page.
     * @param html html text to parse
     * @return
     */
    public CaptchaSolveRequest buildCaptchaSolveRequest( String html ) {
        Pattern p = Pattern.compile( "new MultiCaptcha\\('(.*?)', '(.*?)', '(.*?)'" );
        Matcher m = p.matcher( html );
        if ( m.find() ) {
            CaptchaSolveRequest req = new CaptchaSolveRequest( m.group( 2 ), m.group( 1 ), m.group( 3 ) );
            LOGGER.info( req.toString() );
            return req;
        }
        else {
            LOGGER.info( html );
            throw new MissingUserDataException( "Failed to retrieve captcha key." );
        }
    }

    /**
     * Retrieves the key from a recaptcha URI.
     * 
     * @param googleUri the URI to parse
     * @return non-null google key
     * @throws URISyntaxException on parse exception or key not found
     */
    public String getCaptchaKeyFromURI( String googleUri ) throws URISyntaxException {
        LOGGER.info( "Retrieving captcha key from " + googleUri );
        List<org.apache.http.NameValuePair> params = URLEncodedUtils.parse( new URI( googleUri ), StandardCharsets.UTF_8 );
        for ( org.apache.http.NameValuePair nvp : params ) {
            if ( "k".equals( nvp.getName() ) ) {
                return nvp.getValue();
            }
        }
        throw new MissingUserDataException( "Failed to retrieve captcha key." );
    }

    /**
     * Attempts to solve a V2 recaptcha.
     * 
     * @param webClient
     * @param pageUrl the current URL we're on
     * @param pageContent current login page containing captcha
     * @return the solved captcha
     * @throws IOException
     * @throws URISyntaxException 
     */
    public String solveRecaptchaV2( WebClient webClient, String pageUrl, String pageContent ) throws IOException, URISyntaxException {
        LOGGER.info( "Attempting to solve recaptcha V2 request for " + pageUrl );
        
        String token = recaptchaRetrieveResponse( webClient,
                recaptchaV2Request( webClient, pageUrl, buildCaptchaSolveRequest( pageContent ).getV2Key() ) );
        if ( StringUtils.isBlank( token ) ) {
            throw new UnrecoverableFault( "Unable to solve captcha" );
        }
        return token;
    }

    /**
     * Attempts to solve a V3 recaptcha for Cloudbeds.
     * 
     * @param webClient
     * @param reservationId cloudbeds reservation
     * @return the solved captcha
     * @throws IOException
     */
    public String solveRecaptchaV3( WebClient webClient, String reservationId ) throws IOException {
        LOGGER.info( "Attempting to solve recaptcha V3 request for reservation " + reservationId );
        return solveRecaptchaV3( webClient,
                getCaptchaRequestFromReservation( webClient, reservationId ),
                "https://hotels.cloudbeds.com/connect/" + cloudbedsScraper.getPropertyId() + "#/reservations/" + reservationId );
    }

    /**
     * Finds all the (re)captcha parameters from the cloudbeds reservation page.
     * 
     * @param webClient
     * @param reservationId cloudbeds reservation
     * @throws IOException 
     */
    private CaptchaSolveRequest getCaptchaRequestFromReservation( WebClient webClient, String reservationId ) throws IOException {
        Page redirectPage = cloudbedsScraper.loadReservationPage( webClient, reservationId );
        LOGGER.debug( redirectPage.getWebResponse().getContentAsString() );

        String text = redirectPage.getWebResponse().getContentAsString();
        return buildCaptchaSolveRequest( text );
    }

    /**
     * Attempt to solve the V3 captcha request.
     * @param webClient
     * @param request the request parameters
     * @param pageUrl the page where the captcha was found
     * @return non-null solution
     * @throws IOException on failure
     */
    private String solveRecaptchaV3( WebClient webClient, CaptchaSolveRequest req, String pageUrl ) throws IOException {
        return solveRecaptcha(webClient, recaptchaV3Request( webClient, req.getV3Key(), req.getAction(), pageUrl ), pageUrl);
    }

    /**
     * Attempt to solve the V3 captcha request.
     * @param webClient
     * @param captchaId the 2CAPTCHA id we got from sending the request
     * @param pageUrl the page where the captcha was found
     * @return non-null solution
     * @throws IOException on failure
     */
    private String solveRecaptcha( WebClient webClient, String captchaId, String pageUrl ) throws IOException {

        for ( int i = 0 ; i < MAX_SOLVE_ATTEMPTS ; i++ ) {
            LOGGER.info( "Attempt #" + (i + 1) + " to solve CAPTCHA" );
            LOGGER.info( "Captcha ID: " + captchaId);

            String token = recaptchaRetrieveResponse( webClient, captchaId );
            if ( StringUtils.isBlank( token ) ) {
                throw new UnrecoverableFault( "Unable to solve captcha" );
            }

            WebRequest requestSettings = jsonRequestFactory.createVerifyCaptchaRequest( token );
            LOGGER.info( "Validating captcha with cloudbeds" );
            Page redirectPage = webClient.getPage( requestSettings );
            LOGGER.info( redirectPage.getWebResponse().getContentAsString() );

            Optional<JsonObject> rpt = Optional.ofNullable( gson.fromJson( redirectPage.getWebResponse().getContentAsString(), JsonObject.class ) );
            if ( false == rpt.isPresent() || false == rpt.get().get( "success" ).getAsBoolean() ) {
                LOGGER.info( "hmm... Cloudbeds doesn't like that answer..." );
            }
            else {
                LOGGER.info( "By jove, I think we've got it! Captcha ID: " + captchaId );
                recaptchaReportGood( webClient, captchaId );
                return token;
            }
        }
        throw new IOException( "Unable to solve captcha :(" );
    }
}
