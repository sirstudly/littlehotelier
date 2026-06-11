
package com.macbackpackers.scrapers.cloudbedsws;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * WebSocket client for the Cloudbeds calendar feed ({@code wss://websocket.cloudbeds.com/calendar/{propertyId}}).
 * <p>
 * Productionised from the original {@code CloudbedsWebSocketTestClient}. Responsibilities:
 * <ul>
 * <li>send the {@code migrate} message on open,</li>
 * <li>decode the {@code on_migrate} snapshot and forward it to the {@link CloudbedsEventListener},</li>
 * <li>request deltas via {@code get_changes} (using the snapshot {@code time}),</li>
 * <li>send periodic {@code guarantee} heartbeats, and</li>
 * <li>forward incremental {@code changes}/{@code room_assign} payloads to the listener.</li>
 * </ul>
 * Reconnection is handled by {@code CloudbedsWebSocketService}; this client exposes {@link #awaitClosed()}
 * so the service can block until the connection drops and then reconnect with a fresh session.
 */
public class CloudbedsWebSocketClient extends WebSocketClient {

    private static final Logger LOGGER = LoggerFactory.getLogger( CloudbedsWebSocketClient.class );
    private static final Gson GSON = new Gson();
    private static final int GUARANTEE_INTERVAL_SEC = 20;

    private final String propertyId;
    private final String version;
    private final String frontVersion;
    private final String csrf;
    private final String migrateId;
    private final CloudbedsEventListener listener;

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor( r -> {
        Thread t = new Thread( r, "cloudbeds-ws-guarantee" );
        t.setDaemon( true );
        return t;
    } );
    private final CountDownLatch closedLatch = new CountDownLatch( 1 );

    private volatile String lastGuaranteeToken;

    public CloudbedsWebSocketClient( URI serverUri, Map<String, String> httpHeaders,
            String propertyId, String version, String frontVersion, String csrf, String migrateId,
            CloudbedsEventListener listener ) {
        super( serverUri, httpHeaders );
        this.propertyId = propertyId;
        this.version = version;
        this.frontVersion = frontVersion;
        this.csrf = csrf;
        this.migrateId = migrateId;
        this.listener = listener;
    }

    /**
     * Blocks until this client's connection has closed.
     *
     * @throws InterruptedException if interrupted while waiting
     */
    public void awaitClosed() throws InterruptedException {
        closedLatch.await();
    }

    @Override
    public void onOpen( ServerHandshake handshake ) {
        LOGGER.info( "Cloudbeds calendar WebSocket open: {} (status {} {})",
                getURI(), handshake.getHttpStatus(), handshake.getHttpStatusMessage() );
        send( buildMigrate() );
        lastGuaranteeToken = newGuaranteeToken();
        scheduler.scheduleAtFixedRate( this::sendGuarantee,
                GUARANTEE_INTERVAL_SEC, GUARANTEE_INTERVAL_SEC, TimeUnit.SECONDS );
    }

    @Override
    public void onMessage( String message ) {
        try {
            handleIncoming( message );
        }
        catch ( Exception e ) {
            LOGGER.warn( "Failed to handle Cloudbeds WebSocket message: {}", e.getMessage(), e );
        }
    }

    @Override
    public void onClose( int code, String reason, boolean remote ) {
        LOGGER.info( "Cloudbeds calendar WebSocket closed: code={} reason='{}' remote={}", code, reason, remote );
        scheduler.shutdownNow();
        closedLatch.countDown();
    }

    @Override
    public void onError( Exception ex ) {
        LOGGER.error( "Cloudbeds calendar WebSocket error", ex );
        // onClose is invoked separately by the library; don't count down here to avoid double-signalling
    }

    private void handleIncoming( String message ) {
        if ( message == null || message.isEmpty() ) {
            return;
        }
        String trimmed = message.trim();
        if ( trimmed.startsWith( "`" ) && trimmed.endsWith( "`" ) ) {
            trimmed = trimmed.substring( 1, trimmed.length() - 1 );
        }

        JsonObject root = JsonParser.parseString( trimmed ).getAsJsonObject();
        String action = root.has( "action" ) ? root.get( "action" ).getAsString() : null;

        if ( "auth".equals( action ) ) {
            LOGGER.info( "Cloudbeds WebSocket auth: success={} property_id={}",
                    root.get( "success" ), root.get( "property_id" ) );
            return;
        }

        if ( "on_migrate".equals( action ) ) {
            handleOnMigrate( root );
            return;
        }

        if ( root.has( "guarantee" ) && root.has( "payload" ) ) {
            lastGuaranteeToken = root.get( "guarantee" ).getAsString();
            handlePayload( root.get( "payload" ).getAsString() );
            return;
        }

        LOGGER.debug( "Cloudbeds WebSocket other message: {}",
                trimmed.length() > 200 ? trimmed.substring( 0, 200 ) + "..." : trimmed );
    }

    private void handleOnMigrate( JsonObject root ) {
        String time = root.has( "time" ) ? root.get( "time" ).getAsString() : null;
        if ( !root.has( "data" ) || root.get( "data" ).isJsonNull() ) {
            LOGGER.warn( "Cloudbeds on_migrate had no data field" );
            return;
        }
        String data = root.get( "data" ).getAsString();
        LOGGER.info( "Cloudbeds on_migrate received: time={} dataLength={}", time, data.length() );
        try {
            List<CloudbedsCalendarEvent> events = CloudbedsEventDecoder.decodeOnMigrate( data );
            listener.onSnapshot( propertyId, events );
        }
        catch ( Exception e ) {
            LOGGER.error( "Failed to decode on_migrate snapshot", e );
        }
        // request any changes since the snapshot timestamp
        if ( time != null ) {
            send( buildGetChanges( time ) );
        }
    }

    private void handlePayload( String payloadStr ) {
        JsonObject payload = JsonParser.parseString( payloadStr ).getAsJsonObject();
        String action = payload.has( "action" ) ? payload.get( "action" ).getAsString() : null;
        if ( "changes".equals( action ) || "room_assign".equals( action ) ) {
            if ( payload.has( "data" ) && payload.get( "data" ).isJsonObject() ) {
                List<CloudbedsCalendarEvent> events =
                        CloudbedsEventDecoder.decodeChanges( payload.getAsJsonObject( "data" ) );
                listener.onChanges( propertyId, events );
            }
        }
        else {
            LOGGER.debug( "Cloudbeds WebSocket payload action ignored: {}", action );
        }
    }

    private void sendGuarantee() {
        try {
            if ( !isOpen() ) {
                return;
            }
            JsonObject g = new JsonObject();
            g.addProperty( "action", "guarantee" );
            g.addProperty( "guarantee", lastGuaranteeToken );
            g.addProperty( "property_id", propertyId );
            g.addProperty( "version", version );
            g.addProperty( "frontVersion", frontVersion );
            g.addProperty( "csrf_accessa", csrf );
            send( GSON.toJson( g ) );
        }
        catch ( Exception e ) {
            LOGGER.warn( "Failed to send guarantee heartbeat: {}", e.getMessage() );
        }
    }

    private String buildMigrate() {
        JsonObject o = new JsonObject();
        o.addProperty( "action", "migrate" );
        o.addProperty( "id", migrateId );
        o.addProperty( "__created", System.currentTimeMillis() );
        o.addProperty( "property_id", propertyId );
        o.addProperty( "version", version );
        o.addProperty( "frontVersion", frontVersion );
        o.addProperty( "csrf_accessa", csrf );
        return GSON.toJson( o );
    }

    private String buildGetChanges( String last ) {
        JsonObject o = new JsonObject();
        o.addProperty( "action", "get_changes" );
        try {
            o.addProperty( "last", Long.parseLong( last.trim() ) );
        }
        catch ( NumberFormatException e ) {
            o.addProperty( "last", last );
        }
        o.addProperty( "property_id", propertyId );
        o.addProperty( "version", version );
        o.addProperty( "frontVersion", frontVersion );
        o.addProperty( "csrf_accessa", csrf );
        return GSON.toJson( o );
    }

    private static String newGuaranteeToken() {
        return UUID.randomUUID().toString().replace( "-", "" ).substring( 0, 16 ) + "." + System.currentTimeMillis();
    }
}
