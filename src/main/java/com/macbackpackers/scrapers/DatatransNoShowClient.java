package com.macbackpackers.scrapers;

import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import org.htmlunit.HttpMethod;
import org.htmlunit.Page;
import org.htmlunit.WebClient;
import org.htmlunit.WebRequest;
import org.htmlunit.util.Cookie;
import org.htmlunit.util.NameValuePair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.stereotype.Component;

import com.macbackpackers.dao.WordPressDAO;
import com.macbackpackers.exceptions.UnrecoverableFault;
import com.macbackpackers.services.GmailService;

/**
 * Reveals Hostelworld credit card numbers via the Datatrans NoShow PCI page.
 */
@Component
public class DatatransNoShowClient {

    private static final String DATATRANS_ORIGIN = "https://pay.datatrans.com";

    private static final String NOSHOW_POST_URL = DATATRANS_ORIGIN + "/upp/noshow";

    private static final String ACTIVATION_TOKEN_OPTION = "hbo_hw_datatrans_token";

    private static final Pattern TOKEN_QUERY = Pattern.compile( "[?&]token=([^&]+)" );

    private static final Pattern ACTIVATION_TOKEN_INPUT = Pattern.compile(
            "name=[\"']NoShowActivationToken[\"'][^>]*value=[\"']([^\"']*)[\"']",
            Pattern.CASE_INSENSITIVE );

    private static final Pattern ACTIVATION_TOKEN_INPUT_REVERSED = Pattern.compile(
            "value=[\"']([^\"']*)[\"'][^>]*name=[\"']NoShowActivationToken[\"']",
            Pattern.CASE_INSENSITIVE );

    private static final Pattern ACTIVATION_TOKEN_INLINE = Pattern.compile(
            "NoShowActivationToken[\"']?\\s*[:=]\\s*[\"']([a-f0-9]{32,})[\"']",
            Pattern.CASE_INSENSITIVE );

    private static final Pattern ACTIVATION_TOKEN_LOCAL_STORAGE = Pattern.compile(
            "localStorage\\.setItem\\s*\\(\\s*['\"]NoShowActivationToken['\"]\\s*,\\s*['\"]([a-f0-9]{32,})['\"]",
            Pattern.CASE_INSENSITIVE );

    private static final Pattern CAPTCHA_SEED_INPUT = Pattern.compile(
            "name=\"captchaSeed\"\\s+value=\"([^\"]+)\"" );

    private static final Pattern IMGHDN_GIF = Pattern.compile(
            "src=\"(/upp/imghdn/\\d+/spacer\\.gif)\"" );

    private final Logger LOGGER = LoggerFactory.getLogger( getClass() );

    @Autowired
    private GmailService gmailService;

    @Autowired
    private DatatransImageDecoder imageDecoder;

    @Autowired
    private WordPressDAO wordPressDAO;

    /**
     * Returns the card number for the given Datatrans NoShow URL.
     */
    public String revealCardNumber( WebClient webClient, String datatransUrl ) throws IOException {
        String token = extractToken( datatransUrl );
        String activationToken = loadActivationToken();

        LOGGER.info( "Opening Datatrans NoShow page for token {}", token );
        webClient.getPage( datatransUrl );

        String html = postActivationTokenRetrieve( webClient, token, activationToken );
        if ( needsDeviceActivation( html ) ) {
            LOGGER.info( "Datatrans device activation required; waiting for activation email..." );
            sleep( 15000 );
            String activationCode = fetchDeviceActivationCode();
            html = activateDevice( webClient, token, activationCode );
            activationToken = resolveActivationToken( html, webClient, token );
            if ( StringUtils.isNotBlank( activationToken ) ) {
                saveActivationToken( activationToken );
            }
            else {
                LOGGER.info( "NoShowActivationToken not found after activation; continuing with session cookies" );
            }
            html = postContinueAfterActivation( webClient, token, activationToken );
            if ( StringUtils.isBlank( activationToken ) ) {
                activationToken = resolveActivationToken( html, webClient, token );
                if ( StringUtils.isNotBlank( activationToken ) ) {
                    saveActivationToken( activationToken );
                }
            }
        }
        else if ( false == hasCaptchaForm( html ) ) {
            html = postContinueAfterActivation( webClient, token, activationToken );
        }
        if ( false == hasCaptchaForm( html ) ) {
            html = postActivationTokenRetrieve( webClient, token, activationToken );
        }
        if ( false == hasCaptchaForm( html ) ) {
            throw new UnrecoverableFault( "Unable to reach Datatrans captcha page" );
        }

        String captchaSeed = extractCaptchaSeed( html );
        List<String> captchaImages = extractImghdnGifPaths( html, 4 );
        if ( captchaImages.isEmpty() ) {
            throw new UnrecoverableFault( "Unable to find Datatrans captcha images" );
        }

        String captchaCode = imageDecoder.decodeImageUrls( webClient, captchaImages );
        LOGGER.info( "Submitting Datatrans captcha" );
        html = postRevealCard( webClient, token, captchaSeed, captchaCode );

        if ( false == html.contains( "postMessage" ) || false == html.contains( "'CC'" ) ) {
            LOGGER.error( html );
            throw new UnrecoverableFault( "Datatrans did not return credit card images after captcha submission" );
        }

        imageDecoder.learnKnownMappings( webClient, captchaImages, captchaCode );

        List<String> cardImages = extractImghdnGifPaths( html );
        if ( cardImages.isEmpty() ) {
            throw new UnrecoverableFault( "Unable to find Datatrans credit card digit images" );
        }

        return imageDecoder.decodeImageUrls( webClient, cardImages );
    }

    private String postActivationTokenRetrieve( WebClient webClient, String token, String activationToken )
            throws IOException {
        return postForm( webClient, NOSHOW_POST_URL, Arrays.asList(
                new NameValuePair( "token", token ),
                new NameValuePair( "action", "activationTokenRetrieve" ),
                new NameValuePair( "storageAccessAllowed", "true" ),
                new NameValuePair( "NoShowActivationToken", StringUtils.defaultString( activationToken ) ) ) );
    }

    private String activateDevice( WebClient webClient, String token, String activationCode ) throws IOException {
        String activateUrl = NOSHOW_POST_URL + "?action=activate&token=" + token + "&code=" + activationCode;
        LOGGER.info( "Activating Datatrans device" );
        String html = webClient.getPage( activateUrl ).getWebResponse().getContentAsString();
        if ( StringUtils.isBlank( html ) || needsDeviceActivation( html ) ) {
            LOGGER.info( "GET activate did not complete device activation; trying POST" );
            html = postDeviceActivation( webClient, token, activationCode );
        }
        return html;
    }

    private String postDeviceActivation( WebClient webClient, String token, String activationCode ) throws IOException {
        return postForm( webClient, NOSHOW_POST_URL + "?token=" + token, Arrays.asList(
                new NameValuePair( "NoShowActivationToken", "" ),
                new NameValuePair( "action", "activate" ),
                new NameValuePair( "code", activationCode ) ) );
    }

    private String postContinueAfterActivation( WebClient webClient, String token, String activationToken )
            throws IOException {
        return postForm( webClient, NOSHOW_POST_URL, Arrays.asList(
                new NameValuePair( "token", token ),
                new NameValuePair( "NoShowActivationToken", activationToken ) ) );
    }

    private String postRevealCard( WebClient webClient, String token, String captchaSeed, String captchaCode )
            throws IOException {
        return postForm( webClient, NOSHOW_POST_URL, Arrays.asList(
                new NameValuePair( "token", token ),
                new NameValuePair( "action", "getCC" ),
                new NameValuePair( "captchaCode", captchaCode ),
                new NameValuePair( "captchaSeed", captchaSeed ),
                new NameValuePair( "submit", "Send" ) ) );
    }

    private String postForm( WebClient webClient, String url, List<NameValuePair> params ) throws IOException {
        WebRequest request = new WebRequest( new URL( url ), HttpMethod.POST );
        request.setRequestParameters( params );
        request.setAdditionalHeader( "Content-Type", "application/x-www-form-urlencoded" );
        request.setAdditionalHeader( "Origin", DATATRANS_ORIGIN );
        request.setAdditionalHeader( "Referer", NOSHOW_POST_URL );
        Page page = webClient.getPage( request );
        return page.getWebResponse().getContentAsString();
    }

    private String fetchDeviceActivationCode() throws IOException {
        try {
            return gmailService.fetchHostelworldDeviceActivationCode();
        }
        catch ( EmptyResultDataAccessException e ) {
            throw new UnrecoverableFault( "No Datatrans device activation email found", e );
        }
    }

    private static boolean needsDeviceActivation( String html ) {
        return html.contains( "action=activate" ) || html.contains( "name=\"code\"" )
                || html.toLowerCase().contains( "activation code" );
    }

    private static boolean hasCaptchaForm( String html ) {
        return html.contains( "name=\"captchaSeed\"" ) || html.contains( "name=\"captchaCode\"" );
    }

    private static String extractToken( String datatransUrl ) {
        Matcher matcher = TOKEN_QUERY.matcher( datatransUrl );
        if ( matcher.find() ) {
            return matcher.group( 1 );
        }
        throw new UnrecoverableFault( "Unable to find Datatrans token in URL: " + datatransUrl );
    }

    private String resolveActivationToken( String html, WebClient webClient, String token ) throws IOException {
        String activationToken = extractActivationTokenFromHtml( html );
        if ( StringUtils.isNotBlank( activationToken ) ) {
            return activationToken;
        }

        activationToken = findActivationTokenInCookies( webClient );
        if ( StringUtils.isNotBlank( activationToken ) ) {
            return activationToken;
        }

        html = webClient.getPage( NOSHOW_POST_URL + "?token=" + token ).getWebResponse().getContentAsString();
        activationToken = extractActivationTokenFromHtml( html );
        if ( StringUtils.isNotBlank( activationToken ) ) {
            return activationToken;
        }

        return findActivationTokenInCookies( webClient );
    }

    private static String findActivationTokenInCookies( WebClient webClient ) {
        for ( Cookie cookie : webClient.getCookieManager().getCookies() ) {
            if ( "NoShowActivationToken".equalsIgnoreCase( cookie.getName() ) ) {
                return cookie.getValue();
            }
        }
        return "";
    }

    private static String extractActivationTokenFromHtml( String html ) {
        if ( StringUtils.isBlank( html ) ) {
            return "";
        }
        for ( Pattern pattern : Arrays.asList(
                ACTIVATION_TOKEN_LOCAL_STORAGE,
                ACTIVATION_TOKEN_INPUT,
                ACTIVATION_TOKEN_INPUT_REVERSED,
                ACTIVATION_TOKEN_INLINE ) ) {
            Matcher matcher = pattern.matcher( html );
            if ( matcher.find() && StringUtils.isNotBlank( matcher.group( 1 ) ) ) {
                return matcher.group( 1 );
            }
        }
        return "";
    }

    private static String extractCaptchaSeed( String html ) {
        Matcher matcher = CAPTCHA_SEED_INPUT.matcher( html );
        if ( matcher.find() ) {
            return matcher.group( 1 );
        }
        throw new UnrecoverableFault( "Unable to find captchaSeed in Datatrans captcha page" );
    }

    private static List<String> extractImghdnGifPaths( String html ) {
        return extractImghdnGifPaths( html, Integer.MAX_VALUE );
    }

    private static List<String> extractImghdnGifPaths( String html, int maxCount ) {
        List<String> paths = new ArrayList<>();
        Matcher matcher = IMGHDN_GIF.matcher( html );
        while ( matcher.find() && paths.size() < maxCount ) {
            paths.add( matcher.group( 1 ) );
        }
        return paths;
    }

    private String loadActivationToken() {
        return StringUtils.defaultString( wordPressDAO.getOption( ACTIVATION_TOKEN_OPTION ) ).trim();
    }

    private void saveActivationToken( String activationToken ) {
        wordPressDAO.setOption( ACTIVATION_TOKEN_OPTION, activationToken );
        LOGGER.info( "Saved Datatrans activation token to {}", ACTIVATION_TOKEN_OPTION );
    }

    private static void sleep( int millis ) {
        try {
            Thread.sleep( millis );
        }
        catch ( InterruptedException e ) {
            Thread.currentThread().interrupt();
        }
    }
}
