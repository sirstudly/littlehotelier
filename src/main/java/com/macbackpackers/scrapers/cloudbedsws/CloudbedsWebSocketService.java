
package com.macbackpackers.scrapers.cloudbedsws;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.htmlunit.WebClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;

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
 * This first step only forwards events to {@link CloudbedsEventListener} (logging); no DB writes.
 */
@Service
public class CloudbedsWebSocketService {

    private static final Logger LOGGER = LoggerFactory.getLogger( CloudbedsWebSocketService.class );

    private static final String WS_URL_PREFIX = "wss://websocket.cloudbeds.com/calendar/";
    private static final String ORIGIN = "https://hotels.cloudbeds.com";
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
    private CloudbedsEventListener eventListener;

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
                session.migrateId, eventListener );
        currentClient = client;
        try {
            LOGGER.info( "Connecting to Cloudbeds calendar WebSocket: {}", uri );
            boolean connected = client.connectBlocking( CONNECT_TIMEOUT_SEC, TimeUnit.SECONDS );
            if ( !connected ) {
                LOGGER.warn( "Could not connect to Cloudbeds calendar WebSocket (timeout)" );
                client.close();
                return false;
            }
            client.awaitClosed();
            return true;
        }
        finally {
            currentClient = null;
        }
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
