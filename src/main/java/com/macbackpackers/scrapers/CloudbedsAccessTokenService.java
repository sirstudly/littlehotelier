
package com.macbackpackers.scrapers;

import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.htmlunit.CookieManager;
import org.htmlunit.HttpMethod;
import org.htmlunit.Page;
import org.htmlunit.WebClient;
import org.htmlunit.WebRequest;
import org.htmlunit.util.Cookie;
import org.htmlunit.util.NameValuePair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Mints short-lived Cloudbeds access tokens (~8h JWT) used as {@code Authorization: Bearer} for
 * api.cloudbeds.com (e.g. Data Insights) and by the calendar WebSocket.
 * <p>
 * The browser keeps the access token in {@code localStorage["access-token"]} (not a cookie) and
 * obtains it via {@code POST /auth/session_access_token/refresh} with
 * {@code code=<rt cookie>&csrf_accessa=<csrf>}. The refresh token is rotated (one-time use), so on
 * success the updated cookie set is persisted back to {@code hbo_cloudbeds_cookies}.
 */
@Service
public class CloudbedsAccessTokenService {

    private static final Logger LOGGER = LoggerFactory.getLogger( CloudbedsAccessTokenService.class );

    private static final String ORIGIN = "https://hotels.cloudbeds.com";
    private static final String REFRESH_URL = ORIGIN + "/auth/session_access_token/refresh";
    private static final Pattern CSRF_COOKIE_PATTERN = Pattern.compile( "csrf_accessa_cookie=([0-9a-f]+)" );

    @Autowired
    private CloudbedsJsonRequestFactory jsonRequestFactory;

    @Autowired
    private ApplicationContext context;

    /**
     * Exchanges the {@code rt} cookie in {@code cookies} for a fresh access token and persists the
     * rotated cookies.
     *
     * @param cookies current Cloudbeds cookie header value (must contain {@code rt} and {@code csrf_accessa_cookie})
     * @param userAgent user agent to send (may be null)
     * @return the new access token and updated cookie string
     * @throws IOException if the refresh fails
     */
    public synchronized AccessToken refresh( String cookies, String userAgent ) throws IOException {
        String refreshToken = extractCookieValue( cookies, "rt" );
        if ( refreshToken == null ) {
            throw new IOException( "No 'rt' (refresh token) cookie in Cloudbeds session" );
        }
        String csrf = extractCsrf( cookies );
        if ( csrf == null ) {
            throw new IOException( "No 'csrf_accessa_cookie' in Cloudbeds session" );
        }
        String propertyId = jsonRequestFactory.getPropertyId();

        try ( WebClient webClient = context.getBean( "webClientForCloudbeds", WebClient.class ) ) {
            webClient.getOptions().setThrowExceptionOnFailingStatusCode( false );

            // seed the cookie jar with the stored session so HtmlUnit sends them and
            // applies the Set-Cookie updates (rt rotation) from the response
            CookieManager cookieManager = webClient.getCookieManager();
            cookieManager.setCookiesEnabled( true );
            cookieManager.clearCookies();
            for ( String pair : cookies.split( ";" ) ) {
                int eq = pair.indexOf( '=' );
                if ( eq > 0 ) {
                    cookieManager.addCookie( new Cookie( ".cloudbeds.com",
                            pair.substring( 0, eq ).trim(), pair.substring( eq + 1 ).trim(), "/", null, true ) );
                }
            }

            WebRequest req = new WebRequest( new URL( REFRESH_URL ), HttpMethod.POST );
            req.setAdditionalHeader( "Accept", "application/json, text/plain, */*" );
            req.setAdditionalHeader( "Content-Type", "application/x-www-form-urlencoded; charset=UTF-8" );
            req.setAdditionalHeader( "Origin", ORIGIN );
            req.setAdditionalHeader( "Referer", ORIGIN + "/connect/" + propertyId );
            req.setAdditionalHeader( "X-Property-Id", propertyId );
            req.setAdditionalHeader( "Cache-Control", "no-cache" );
            req.setAdditionalHeader( "Pragma", "no-cache" );
            if ( userAgent != null ) {
                req.setAdditionalHeader( "User-Agent", userAgent );
            }
            req.setRequestParameters( Arrays.asList(
                    new NameValuePair( "code", refreshToken ),
                    new NameValuePair( "csrf_accessa", csrf ) ) );

            Page page = webClient.getPage( req );
            int status = page.getWebResponse().getStatusCode();
            String body = page.getWebResponse().getContentAsString();
            if ( status != 200 ) {
                throw new IOException( "Access token refresh failed (HTTP " + status + "): " + abbreviate( body ) );
            }
            JsonObject json = JsonParser.parseString( body ).getAsJsonObject();
            if ( false == json.has( "accessToken" ) || json.get( "accessToken" ).isJsonNull() ) {
                throw new IOException( "Access token refresh response has no accessToken: " + abbreviate( body ) );
            }

            String refreshedCookies = rebuildCookieString( cookieManager );
            jsonRequestFactory.setCookies( refreshedCookies );
            LOGGER.info( "Access token refreshed OK; updated cookies persisted ({} cookies)",
                    cookieManager.getCookies().size() );
            return new AccessToken( json.get( "accessToken" ).getAsString(), refreshedCookies );
        }
    }

    private static String extractCsrf( String cookies ) {
        Matcher m = CSRF_COOKIE_PATTERN.matcher( cookies );
        return m.find() ? m.group( 1 ) : null;
    }

    /** Joins all cookies in the jar into a single Cookie header value. */
    private static String rebuildCookieString( CookieManager cookieManager ) {
        List<String> pairs = new ArrayList<>();
        for ( Cookie c : cookieManager.getCookies() ) {
            pairs.add( c.getName() + "=" + c.getValue() );
        }
        return String.join( "; ", pairs );
    }

    /** Extracts the value of the named cookie from a Cookie header string, or null if absent. */
    static String extractCookieValue( String cookies, String name ) {
        if ( cookies == null ) {
            return null;
        }
        for ( String pair : cookies.split( ";" ) ) {
            int eq = pair.indexOf( '=' );
            if ( eq > 0 && name.equals( pair.substring( 0, eq ).trim() ) ) {
                return pair.substring( eq + 1 ).trim();
            }
        }
        return null;
    }

    private static String abbreviate( String body ) {
        if ( body == null ) {
            return "(empty)";
        }
        return body.length() > 500 ? body.substring( 0, 500 ) + "..." : body;
    }

    /** Result of a refresh: bearer token plus the rotated cookie string. */
    public static final class AccessToken {
        private final String token;
        private final String cookies;

        public AccessToken( String token, String cookies ) {
            this.token = token;
            this.cookies = cookies;
        }

        public String getToken() {
            return token;
        }

        public String getCookies() {
            return cookies;
        }
    }
}
