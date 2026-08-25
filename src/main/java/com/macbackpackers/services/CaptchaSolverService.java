
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
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.macbackpackers.beans.AmazonWafChallengeParams;
import com.macbackpackers.beans.AmazonWafSolution;
import com.macbackpackers.beans.CaptchaSolveRequest;
import com.macbackpackers.dao.WordPressDAO;
import com.macbackpackers.exceptions.MissingUserDataException;
import com.macbackpackers.exceptions.UnrecoverableFault;
import com.macbackpackers.scrapers.CloudbedsJsonRequestFactory;
import com.macbackpackers.scrapers.CloudbedsScraper;

import java.util.LinkedHashMap;
import java.util.Map;

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

    /**
     * Solves an Amazon AWS WAF captcha via 2captcha createTask / getTaskResult.
     * Uses {@code AmazonTask} when {@code hbo_2captcha_proxy} is set, otherwise {@code AmazonTaskProxyless}.
     *
     * @param webClient client used for 2captcha HTTP
     * @param params challenge parameters extracted from the page
     * @return non-null solution (captcha_voucher + existing_token)
     * @throws IOException on API / solve failure
     * @see <a href="https://2captcha.com/api-docs/amazon-aws-waf-captcha">Amazon WAF API</a>
     */
    public AmazonWafSolution solveAmazonWaf( WebClient webClient, AmazonWafChallengeParams params ) throws IOException {
        long started = System.currentTimeMillis();
        String taskId = amazonWafCreateTask( webClient, params );
        LOGGER.info( "2captcha Amazon WAF taskId={} for {}", taskId, params.getWebsiteUrl() );
        AmazonWafSolution solution = amazonWafRetrieveResponse( webClient, taskId );
        LOGGER.info( "2captcha Amazon WAF solved taskId={} in {}ms {}",
                taskId, System.currentTimeMillis() - started, solution );
        return solution;
    }

    /**
     * Creates a 2captcha Amazon WAF task.
     *
     * @return task id
     */
    public String amazonWafCreateTask( WebClient webClient, AmazonWafChallengeParams params ) throws IOException {
        if ( params == null || StringUtils.isBlank( params.getWebsiteUrl() )
                || StringUtils.isBlank( params.getWebsiteKey() ) ) {
            throw new UnrecoverableFault( "Missing Amazon WAF challenge parameters" );
        }
        if ( false == params.isJsapi()
                && ( StringUtils.isBlank( params.getIv() ) || StringUtils.isBlank( params.getContext() ) ) ) {
            throw new UnrecoverableFault( "Missing Amazon WAF iv/context (and no jsapiScript)" );
        }

        JsonObject task = new JsonObject();
        String proxy = dao.getOption( "hbo_2captcha_proxy" );
        if ( StringUtils.isNotBlank( proxy ) ) {
            task.addProperty( "type", "AmazonTask" );
            applyAmazonTaskProxy( task, proxy,
                    StringUtils.defaultString( dao.getOption( "hbo_2captcha_proxytype" ), "http" ) );
        }
        else {
            task.addProperty( "type", "AmazonTaskProxyless" );
        }
        task.addProperty( "websiteURL", params.getWebsiteUrl() );
        task.addProperty( "websiteKey", params.getWebsiteKey() );
        if ( StringUtils.isNotBlank( params.getUserAgent() ) ) {
            task.addProperty( "userAgent", params.getUserAgent() );
        }
        if ( params.isJsapi() ) {
            // Do not also send challenge.js: workers then hit classic captcha.awswaf.com
            // (41bcdd…) instead of captcha-sdk (d8c14d4960ca) and return a token for the
            // wrong integration. Chrome's visual widget never accepts it.
            task.addProperty( "jsapiScript", params.getJsapiScript() );
        }
        else {
            task.addProperty( "iv", params.getIv() );
            task.addProperty( "context", params.getContext() );
            if ( StringUtils.isNotBlank( params.getChallengeScript() ) ) {
                task.addProperty( "challengeScript", params.getChallengeScript() );
            }
            if ( StringUtils.isNotBlank( params.getCaptchaScript() ) ) {
                task.addProperty( "captchaScript", params.getCaptchaScript() );
            }
        }

        JsonObject body = new JsonObject();
        body.addProperty( "clientKey", dao.get2CaptchaApiKey() );
        body.add( "task", task );

        WebRequest requestSettings = new WebRequest( new URL( "https://api.2captcha.com/createTask" ), HttpMethod.POST );
        requestSettings.setAdditionalHeader( "Content-Type", "application/json" );
        requestSettings.setRequestBody( gson.toJson( body ) );

        LOGGER.info( "Creating 2captcha Amazon WAF task type={} url={} proxy={}:{}",
                task.get( "type" ).getAsString(), params.getWebsiteUrl(),
                task.has( "proxyAddress" ) ? task.get( "proxyAddress" ).getAsString() : "none",
                task.has( "proxyPort" ) ? task.get( "proxyPort" ).getAsString() : "-" );
        Page response = webClient.getPage( requestSettings );
        String responseBody = response.getWebResponse().getContentAsString();
        LOGGER.debug( responseBody );

        JsonObject root = gson.fromJson( responseBody, JsonObject.class );
        if ( root == null || root.get( "errorId" ) == null || root.get( "errorId" ).getAsInt() != 0
                || root.get( "taskId" ) == null ) {
            LOGGER.error( responseBody );
            throw new UnrecoverableFault( "Unexpected status from 2captcha createTask: " + responseBody );
        }
        return root.get( "taskId" ).getAsString();
    }

    /**
     * Polls 2captcha getTaskResult until the Amazon WAF solution is ready.
     *
     * @param webClient client used for 2captcha HTTP
     * @param taskId createTask id
     * @return non-null solution
     */
    public AmazonWafSolution amazonWafRetrieveResponse( WebClient webClient, String taskId ) throws IOException {
        JsonObject body = new JsonObject();
        body.addProperty( "clientKey", dao.get2CaptchaApiKey() );
        body.addProperty( "taskId", Long.parseLong( taskId ) );

        WebRequest requestSettings = new WebRequest( new URL( "https://api.2captcha.com/getTaskResult" ), HttpMethod.POST );
        requestSettings.setAdditionalHeader( "Content-Type", "application/json" );
        requestSettings.setRequestBody( gson.toJson( body ) );

        Page response = null;
        for ( int i = 0 ; i < 20 ; i++ ) {
            try {
                Thread.sleep( 10000 );
            }
            catch ( InterruptedException e ) {
                Thread.currentThread().interrupt();
                throw new UnrecoverableFault( "Interrupted waiting for Amazon WAF captcha" );
            }
            response = webClient.getPage( requestSettings );
            String responseBody = response.getWebResponse().getContentAsString();
            LOGGER.info( "Amazon WAF captcha poll #{} taskId={}", i + 1, taskId );
            LOGGER.debug( responseBody );

            JsonObject root = gson.fromJson( responseBody, JsonObject.class );
            if ( root == null ) {
                continue;
            }
            int errorId = root.has( "errorId" ) ? root.get( "errorId" ).getAsInt() : -1;
            if ( errorId != 0 ) {
                String errorCode = root.has( "errorCode" ) ? root.get( "errorCode" ).getAsString() : responseBody;
                if ( "ERROR_CAPTCHA_UNSOLVABLE".equals( errorCode ) ) {
                    break;
                }
                LOGGER.error( responseBody );
                throw new UnrecoverableFault( "2captcha Amazon WAF error: " + errorCode );
            }
            String status = root.has( "status" ) ? root.get( "status" ).getAsString() : "";
            if ( "ready".equals( status ) && root.has( "solution" ) ) {
                return parseAmazonWafSolution( taskId, root.getAsJsonObject( "solution" ), responseBody );
            }
        }
        if ( response != null ) {
            LOGGER.error( response.getWebResponse().getContentAsString() );
        }
        throw new UnrecoverableFault( "Failed to solve Amazon WAF captcha taskId=" + taskId );
    }

    /**
     * Parses classic {@code captcha_voucher}/{@code existing_token} or Booking jsapi
     * {@code solution.token} (JSON string of cookies including {@code existing_token}).
     */
    AmazonWafSolution parseAmazonWafSolution( String taskId, JsonObject solution, String responseBody ) {
        if ( solution == null ) {
            throw new UnrecoverableFault( "2captcha Amazon WAF ready but missing solution: " + responseBody );
        }
        String voucher = solution.has( "captcha_voucher" ) && false == solution.get( "captcha_voucher" ).isJsonNull()
                ? solution.get( "captcha_voucher" ).getAsString() : null;
        String token = solution.has( "existing_token" ) && false == solution.get( "existing_token" ).isJsonNull()
                ? solution.get( "existing_token" ).getAsString() : null;
        Map<String, String> cookies = new LinkedHashMap<>();

        if ( solution.has( "token" ) && false == solution.get( "token" ).isJsonNull() ) {
            String tokenField = solution.get( "token" ).getAsString();
            try {
                JsonElement parsed = JsonParser.parseString( tokenField );
                if ( parsed != null && parsed.isJsonObject() ) {
                    JsonObject cookieBag = parsed.getAsJsonObject();
                    for ( Map.Entry<String, JsonElement> e : cookieBag.entrySet() ) {
                        if ( e.getValue() != null && false == e.getValue().isJsonNull() ) {
                            cookies.put( e.getKey(), e.getValue().getAsString() );
                        }
                    }
                    if ( StringUtils.isBlank( token ) && cookies.containsKey( "existing_token" ) ) {
                        token = cookies.get( "existing_token" );
                    }
                    // aws-waf-token cookie name used by browsers; map if only existing_token present
                    if ( false == cookies.containsKey( "aws-waf-token" ) && StringUtils.isNotBlank( token ) ) {
                        cookies.put( "aws-waf-token", token );
                    }
                }
                else if ( StringUtils.isBlank( token ) ) {
                    token = tokenField;
                }
            }
            catch ( Exception e ) {
                LOGGER.warn( "solution.token was not JSON; treating as opaque token: {}", e.toString() );
                if ( StringUtils.isBlank( token ) ) {
                    token = tokenField;
                }
            }
        }

        if ( StringUtils.isBlank( voucher ) && StringUtils.isBlank( token ) && cookies.isEmpty() ) {
            throw new UnrecoverableFault( "2captcha Amazon WAF ready but empty solution: " + responseBody );
        }
        AmazonWafSolution parsed = new AmazonWafSolution( taskId, voucher, token, cookies );
        LOGGER.info( "Parsed Amazon WAF solution {} tokenLen={}", parsed,
                token == null ? 0 : token.length() );
        return parsed;
    }

    /**
     * Parses {@code hbo_2captcha_proxy} ({@code [user:pass@]host:port}) into AmazonTask proxy fields.
     */
    void applyAmazonTaskProxy( JsonObject task, String proxy, String proxyType ) {
        String type = StringUtils.lowerCase( StringUtils.trimToEmpty( proxyType ) );
        if ( "https".equals( type ) ) {
            type = "http";
        }
        if ( false == ( "http".equals( type ) || "socks4".equals( type ) || "socks5".equals( type ) ) ) {
            type = "http";
        }
        task.addProperty( "proxyType", type );

        String hostPort = proxy;
        int at = proxy.lastIndexOf( '@' );
        if ( at >= 0 ) {
            String creds = proxy.substring( 0, at );
            hostPort = proxy.substring( at + 1 );
            int colon = creds.indexOf( ':' );
            if ( colon >= 0 ) {
                task.addProperty( "proxyLogin", creds.substring( 0, colon ) );
                task.addProperty( "proxyPassword", creds.substring( colon + 1 ) );
            }
            else {
                task.addProperty( "proxyLogin", creds );
            }
        }
        int colon = hostPort.lastIndexOf( ':' );
        if ( colon < 0 ) {
            throw new UnrecoverableFault( "Invalid hbo_2captcha_proxy (expected host:port): " + proxy );
        }
        task.addProperty( "proxyAddress", hostPort.substring( 0, colon ) );
        try {
            task.addProperty( "proxyPort", Integer.parseInt( hostPort.substring( colon + 1 ) ) );
        }
        catch ( NumberFormatException e ) {
            throw new UnrecoverableFault( "Invalid hbo_2captcha_proxy port: " + proxy );
        }
    }
}
