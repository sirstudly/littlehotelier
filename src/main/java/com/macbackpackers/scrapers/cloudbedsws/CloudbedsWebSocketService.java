
package com.macbackpackers.scrapers.cloudbedsws;

import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
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
import com.macbackpackers.dao.WordPressDAO;
import com.macbackpackers.scrapers.CloudbedsJsonRequestFactory;
import com.macbackpackers.scrapers.CloudbedsScraper;

/**
 * Maintains a permanent connection to the Cloudbeds calendar WebSocket so the processor can monitor
 * bookings in real time. Started from server-mode only (see {@code RunProcessor.runInServerMode()}).
 * <p>
 * Responsibilities:
 * <ul>
 * <li>build the {@code migrate} session parameters from the DB-backed Cloudbeds session
 * (property id, version, frontVersion, CSRF, cookies, user-agent),</li>
 * <li>run a connect/reconnect loop on a daemon thread with capped exponential backoff, and</li>
 * <li>rebuild the session on every (re)connect so a session refresh
 * ({@code ResetCloudbedsSessionJob}) is picked up automatically.</li>
 * </ul>
 * This first step forwards events to registered {@link CloudbedsEventListener} implementations.
 */
@Service
public class CloudbedsWebSocketService {

    private static final Logger LOGGER = LoggerFactory.getLogger( CloudbedsWebSocketService.class );

    private static final String WS_URL_PREFIX = "wss://websocket.cloudbeds.com/calendar/";
    private static final String ORIGIN = "https://hotels.cloudbeds.com";
    private static final String REFRESH_URL = ORIGIN + "/auth/session_access_token/refresh";
    private static final Pattern CSRF_COOKIE_PATTERN = Pattern.compile( "csrf_accessa_cookie=([0-9a-f]+)" );

    private static final int CONNECT_TIMEOUT_SEC = 30;
    private static final long BACKOFF_MIN_MS = 5_000L;
    private static final long BACKOFF_MAX_MS = 300_000L; // 5 minutes

    @Autowired
    private WordPressDAO dao;

    @Autowired
    private CloudbedsScraper cloudbedsScraper;

    @Autowired
    private CloudbedsJsonRequestFactory jsonRequestFactory;

    @Autowired
    private List<CloudbedsEventListener> eventListeners;

    @Autowired
    private ApplicationContext context;

    private volatile boolean running;
    private volatile Thread monitorThread;
    private volatile CloudbedsWebSocketClient currentClient;

    /**
     * Starts the WebSocket monitor on a background daemon thread. Safe to call once; subsequent calls
     * while already running are ignored.
     */
    public synchronized void startMonitoring() {
        if ( running ) {
            LOGGER.warn( "Cloudbeds WebSocket monitor already running; ignoring start request" );
            return;
        }
        running = true;
        monitorThread = new Thread( this::runLoop, "cloudbeds-ws-monitor" );
        monitorThread.setDaemon( true );
        monitorThread.start();
        LOGGER.info( "Started Cloudbeds calendar WebSocket monitor" );
    }

    /**
     * Stops the WebSocket monitor and closes any open connection.
     */
    public synchronized void stopMonitoring() {
        if ( !running ) {
            return;
        }
        LOGGER.info( "Stopping Cloudbeds calendar WebSocket monitor" );
        running = false;
        CloudbedsWebSocketClient client = currentClient;
        if ( client != null ) {
            try {
                client.close();
            }
            catch ( Exception e ) {
                LOGGER.warn( "Error closing Cloudbeds WebSocket client: {}", e.getMessage() );
            }
        }
        Thread t = monitorThread;
        if ( t != null ) {
            t.interrupt();
        }
    }

    private void runLoop() {
        long backoff = BACKOFF_MIN_MS;
        while ( running ) {
            try {
                boolean connected = connectAndBlock();
                if ( connected ) {
                    backoff = BACKOFF_MIN_MS; // reset after a successful session
                }
                else {
                    backoff = nextBackoff( backoff );
                }
            }
            catch ( InterruptedException e ) {
                Thread.currentThread().interrupt();
                break;
            }
            catch ( Exception e ) {
                LOGGER.error( "Cloudbeds WebSocket connection attempt failed: {}", e.getMessage(), e );
                backoff = nextBackoff( backoff );
            }

            if ( !running ) {
                break;
            }
            try {
                LOGGER.info( "Reconnecting to Cloudbeds calendar WebSocket in {} ms", backoff );
                Thread.sleep( backoff );
            }
            catch ( InterruptedException e ) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        LOGGER.info( "Cloudbeds calendar WebSocket monitor loop exited" );
    }

    /**
     * Builds a fresh session, connects, and blocks until the connection closes.
     *
     * @return true if a connection was established (so backoff can be reset), false otherwise
     * @throws InterruptedException if interrupted while connecting/waiting
     */
    private boolean connectAndBlock() throws InterruptedException {
        WsSession session = buildSession();
        if ( session == null ) {
            return false; // session not ready (e.g. not logged in yet)
        }

        URI uri = URI.create( WS_URL_PREFIX + session.propertyId );
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put( "Origin", ORIGIN );
        if ( session.cookies != null ) {
            headers.put( "Cookie", session.cookies );
        }
        if ( session.userAgent != null ) {
            headers.put( "User-Agent", session.userAgent );
        }

        CloudbedsWebSocketClient client = new CloudbedsWebSocketClient( uri, headers,
                session.propertyId, session.version, session.frontVersion, session.csrf,
                session.migrateId, multiplexEventListeners() );
        currentClient = client;
        try {
            LOGGER.info( "Connecting to Cloudbeds calendar WebSocket: {}", uri );
            LOGGER.info( "WS handshake details: cookiePresent={} cookieNames={} userAgentPresent={} frontVersion={}",
                    session.cookies != null, summariseCookieNames( session.cookies ),
                    session.userAgent != null, session.frontVersion );
            boolean connected = client.connectBlocking( CONNECT_TIMEOUT_SEC, TimeUnit.SECONDS );
            if ( !connected ) {
                LOGGER.warn( "Could not connect to Cloudbeds calendar WebSocket (timeout)" );
                client.close();
                return false;
            }
            client.awaitClosed();
            if ( client.isAuthRejected() ) {
                LOGGER.warn( "Cloudbeds WebSocket authentication was rejected. The stored session cookies "
                        + "(hbo_cloudbeds_cookies) are not accepted by websocket.cloudbeds.com. A session refresh "
                        + "(ResetCloudbedsSessionJob) may be required, or the WS needs a cookie not captured at login." );
                return false; // treat as failure so we back off rather than hot-loop
            }
            return true;
        }
        finally {
            currentClient = null;
        }
    }

    /**
     * Returns a listener that forwards each event to every registered {@link CloudbedsEventListener}.
     */
    private CloudbedsEventListener multiplexEventListeners() {
        return new CloudbedsEventListener() {
            @Override
            public void onSnapshot( String propertyId, List<CloudbedsCalendarEvent> events ) {
                for ( CloudbedsEventListener listener : eventListeners ) {
                    try {
                        listener.onSnapshot( propertyId, events );
                    }
                    catch ( Exception e ) {
                        LOGGER.error( "Cloudbeds WebSocket snapshot listener failed: {}", listener.getClass().getSimpleName(), e );
                    }
                }
            }

            @Override
            public void onChanges( String propertyId, List<CloudbedsCalendarEvent> events ) {
                for ( CloudbedsEventListener listener : eventListeners ) {
                    try {
                        listener.onChanges( propertyId, events );
                    }
                    catch ( Exception e ) {
                        LOGGER.error( "Cloudbeds WebSocket changes listener failed: {}", listener.getClass().getSimpleName(), e );
                    }
                }
            }
        };
    }

    /**
     * Returns a comma-separated list of cookie names (no values) for diagnostic logging.
     */
    private static String summariseCookieNames( String cookies ) {
        if ( cookies == null || cookies.isEmpty() ) {
            return "(none)";
        }
        StringBuilder sb = new StringBuilder();
        for ( String pair : cookies.split( ";" ) ) {
            int eq = pair.indexOf( '=' );
            String name = ( eq > 0 ? pair.substring( 0, eq ) : pair ).trim();
            if ( !name.isEmpty() ) {
                if ( sb.length() > 0 ) {
                    sb.append( ',' );
                }
                sb.append( name );
            }
        }
        return sb.toString();
    }

    /**
     * Reads the current Cloudbeds session from the DB and resolves the migrate parameters. Returns
     * null if the session is not usable yet (e.g. not logged in), so the caller can back off and retry.
     */
    private WsSession buildSession() {
        try {
            WsSession s = new WsSession();
            s.propertyId = jsonRequestFactory.getPropertyId();
            s.version = jsonRequestFactory.getVersion();
            s.cookies = dao.getOptionNoCache( "hbo_cloudbeds_cookies" );
            s.userAgent = dao.getOptionNoCache( "hbo_cloudbeds_useragent" );
            s.csrf = resolveCsrf( s.cookies );
            s.frontVersion = resolveFrontVersion();
            s.migrateId = newMigrateId();

            if ( s.csrf == null ) {
                LOGGER.warn( "Cloudbeds session has no CSRF token yet; cannot open WebSocket" );
                return null;
            }

            // the WS rejects sessions whose short-lived access token ('at' cookie; ~8h JWT) has
            // expired, so mint a fresh one immediately before connecting (mirrors the browser,
            // which POSTs /auth/session_access_token/refresh right before opening the WS)
            s.cookies = refreshAccessToken( s );
            s.csrf = resolveCsrf( s.cookies );
            return s;
        }
        catch ( Exception e ) {
            LOGGER.warn( "Cloudbeds session not ready for WebSocket: {}", e.getMessage() );
            return null;
        }
    }

    /**
     * Resolves a fresh CSRF token directly from the cookie string (avoiding the static cache in
     * {@code WordPressDAOImpl.getCsrfToken()} which is not cleared on session refresh). Falls back to
     * the DAO if the cookie cannot be parsed.
     */
    private String resolveCsrf( String cookies ) {
        if ( cookies != null ) {
            Matcher m = CSRF_COOKIE_PATTERN.matcher( cookies );
            if ( m.find() ) {
                return m.group( 1 );
            }
        }
        try {
            return dao.getCsrfToken();
        }
        catch ( Exception e ) {
            return null;
        }
    }

    /**
     * Refreshes the short-lived Cloudbeds access token ('at' cookie) using the stored refresh token
     * ('rt' cookie), exactly as the browser does before opening the calendar WebSocket:
     * {@code POST /auth/session_access_token/refresh} with {@code code=<rt>&csrf_accessa=<csrf>}.
     * <p>
     * The refresh rotates the refresh token (one-time use), so on success the updated cookie set is
     * persisted back to {@code hbo_cloudbeds_cookies} (REST is unaffected; it does not use at/rt).
     * On any failure the original cookie string is returned unchanged and nothing is persisted.
     *
     * @param s session holding the current cookies/csrf/propertyId/userAgent
     * @return the refreshed cookie string, or the original cookies if the refresh failed
     */
    private String refreshAccessToken( WsSession s ) {
        String refreshToken = extractCookieValue( s.cookies, "rt" );
        if ( refreshToken == null ) {
            LOGGER.warn( "No 'rt' (refresh token) cookie in stored session; skipping access token refresh" );
            return s.cookies;
        }
        try ( WebClient webClient = context.getBean( "webClientForCloudbeds", WebClient.class ) ) {
            webClient.getOptions().setThrowExceptionOnFailingStatusCode( false );

            // seed the cookie jar with the stored session so HtmlUnit sends them and
            // applies the Set-Cookie updates (at/rt rotation) from the response
            CookieManager cookieManager = webClient.getCookieManager();
            cookieManager.setCookiesEnabled( true );
            cookieManager.clearCookies();
            for ( String pair : s.cookies.split( ";" ) ) {
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
            req.setAdditionalHeader( "Referer", ORIGIN + "/connect/" + s.propertyId );
            req.setAdditionalHeader( "X-Property-Id", s.propertyId );
            req.setAdditionalHeader( "Cache-Control", "no-cache" );
            req.setAdditionalHeader( "Pragma", "no-cache" );
            if ( s.userAgent != null ) {
                req.setAdditionalHeader( "User-Agent", s.userAgent );
            }
            req.setRequestParameters( Arrays.asList(
                    new NameValuePair( "code", refreshToken ),
                    new NameValuePair( "csrf_accessa", s.csrf ) ) );

            Page page = webClient.getPage( req );
            int status = page.getWebResponse().getStatusCode();
            String body = page.getWebResponse().getContentAsString();
            if ( status != 200 ) {
                LOGGER.warn( "Access token refresh failed (HTTP {}): {}", status, abbreviate( body ) );
                return s.cookies;
            }
            JsonObject json = JsonParser.parseString( body ).getAsJsonObject();
            if ( false == json.has( "accessToken" ) ) {
                LOGGER.warn( "Access token refresh response has no accessToken: {}", abbreviate( body ) );
                return s.cookies;
            }

            // rebuild the cookie string from the (now updated) jar and persist the rotated tokens
            String refreshedCookies = rebuildCookieString( cookieManager );
            jsonRequestFactory.setCookies( refreshedCookies );
            LOGGER.info( "Access token refreshed OK; updated cookies persisted ({} cookies)",
                    cookieManager.getCookies().size() );
            return refreshedCookies;
        }
        catch ( Exception e ) {
            LOGGER.warn( "Access token refresh failed; continuing with stored cookies: {}", e.toString() );
            return s.cookies;
        }
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
    private static String extractCookieValue( String cookies, String name ) {
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

    /** Trims a (possibly large) response body for logging. */
    private static String abbreviate( String body ) {
        if ( body == null ) {
            return "(empty)";
        }
        return body.length() > 500 ? body.substring( 0, 500 ) + "..." : body;
    }

    private String resolveFrontVersion() {
        try ( WebClient webClient = context.getBean( "webClientForCloudbeds", WebClient.class ) ) {
            return cloudbedsScraper.getFrontVersion( webClient );
        }
        catch ( Exception e ) {
            LOGGER.warn( "Could not resolve Cloudbeds frontVersion; proceeding without it: {}", e.getMessage() );
            return null;
        }
    }

    private static long nextBackoff( long current ) {
        return Math.min( current * 2, BACKOFF_MAX_MS );
    }

    private static String newMigrateId() {
        // mimic the browser's long numeric tab/session id
        return System.currentTimeMillis() + String.format( "%06d", ThreadLocalRandom.current().nextInt( 1_000_000 ) );
    }

    /** Holder for the resolved migrate/session parameters. */
    private static class WsSession {
        String propertyId;
        String version;
        String frontVersion;
        String csrf;
        String cookies;
        String userAgent;
        String migrateId;
    }
}
