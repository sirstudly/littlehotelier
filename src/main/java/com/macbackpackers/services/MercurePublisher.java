package com.macbackpackers.services;

import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.macbackpackers.beans.HousekeepingBed;
import com.macbackpackers.dao.WordPressDAO;
import com.macbackpackers.scrapers.CloudbedsScraper;

/**
 * Publishes housekeeping snapshots to a Mercure hub so browser EventSource clients update in place.
 * <p>
 * Config (WordPress options): {@code hbo_mercure_hub_url}, {@code hbo_mercure_publisher_jwt}.
 * Topic: {@code housekeeping/{propertyId}}.
 */
@Service
public class MercurePublisher {

    private static final Logger LOGGER = LoggerFactory.getLogger( MercurePublisher.class );

    @Autowired
    private WordPressDAO dao;

    @Autowired
    private CloudbedsScraper scraper;

    private final Gson gson = new Gson();

    public void publishHousekeepingSnapshot( List<HousekeepingBed> beds, LocalDate selectedDate ) {
        String hubUrl = dao.getOption( "hbo_mercure_hub_url" );
        String jwt = dao.getOption( "hbo_mercure_publisher_jwt" );
        if ( StringUtils.isBlank( hubUrl ) || StringUtils.isBlank( jwt ) ) {
            LOGGER.debug( "Mercure not configured (hbo_mercure_hub_url / hbo_mercure_publisher_jwt); skip publish." );
            return;
        }
        String propertyId = scraper.getPropertyId();
        String topic = "housekeeping/" + propertyId;
        JsonObject payload = buildPayload( beds, selectedDate, propertyId );
        try {
            postToHub( hubUrl, jwt, topic, gson.toJson( payload ) );
            LOGGER.info( "Published housekeeping Mercure update topic={} beds={}", topic,
                    beds == null ? 0 : beds.size() );
        }
        catch ( IOException e ) {
            LOGGER.warn( "Failed to publish housekeeping Mercure update: {}", e.toString() );
        }
    }

    JsonObject buildPayload( List<HousekeepingBed> beds, LocalDate selectedDate, String propertyId ) {
        JsonObject root = new JsonObject();
        root.addProperty( "action", "housekeeping_snapshot" );
        root.addProperty( "property_id", propertyId );
        root.addProperty( "selected_date", selectedDate == null ? null : selectedDate.toString() );
        root.addProperty( "updated_at", java.time.Instant.now().toString() );
        JsonArray bedArr = new JsonArray();
        if ( beds != null ) {
            for ( HousekeepingBed b : beds ) {
                JsonObject o = new JsonObject();
                o.addProperty( "room_id", b.getRoomId() );
                o.addProperty( "room", b.getRoom() );
                o.addProperty( "bed_name", b.getBedName() );
                o.addProperty( "room_type", b.getRoomType() );
                o.addProperty( "capacity", b.getCapacity() );
                o.addProperty( "guest_name", b.getGuestName() );
                o.addProperty( "checkin_date", b.getCheckinDate() == null ? null : b.getCheckinDate().toString() );
                o.addProperty( "checkout_date", b.getCheckoutDate() == null ? null : b.getCheckoutDate().toString() );
                o.addProperty( "data_href", b.getDataHref() );
                o.addProperty( "bedsheet", b.getBedsheet() );
                bedArr.add( o );
            }
        }
        root.add( "beds", bedArr );
        root.add( "totals", computeTotals( beds ) );
        return root;
    }

    /**
     * Matches PHP housekeeping totals: Castle Rock gets floor buckets (20s/40s/50s/60–70s);
     * other hostels only get a single {@code total}.
     */
    private JsonObject computeTotals( List<HousekeepingBed> beds ) {
        boolean castleRock = StringUtils.defaultString( dao.getOption( "siteurl" ) ).contains( "castlerock" );
        JsonObject totals = new JsonObject();
        int total = 0;
        int level2 = 0;
        int level4 = 0;
        int level5 = 0;
        int level6_7 = 0;
        if ( beds != null ) {
            for ( HousekeepingBed b : beds ) {
                if ( false == HousekeepingBedsheet.countsTowardTotals( b.getBedsheet() ) ) {
                    continue;
                }
                int incr = isPrivateRoom( b.getRoomType() ) ? Math.max( 1, b.getCapacity() ) : 1;
                total += incr;
                if ( false == castleRock ) {
                    continue;
                }
                String room = StringUtils.defaultString( b.getRoom() );
                if ( room.matches( "^2.*" ) ) {
                    level2 += incr;
                }
                else if ( room.matches( "^4.*" ) ) {
                    level4 += incr;
                }
                else if ( room.matches( "^5.*" ) ) {
                    level5 += incr;
                }
                else if ( room.matches( "^[67].*" ) ) {
                    level6_7 += incr;
                }
            }
        }
        totals.addProperty( "total", total );
        if ( castleRock ) {
            // CRH total is floors that match room-number patterns only (same as PHP)
            totals.addProperty( "level2", level2 );
            totals.addProperty( "level4", level4 );
            totals.addProperty( "level5", level5 );
            totals.addProperty( "level6_7", level6_7 );
            totals.addProperty( "upstairs", level4 + level5 + level6_7 );
            totals.addProperty( "total", level2 + level4 + level5 + level6_7 );
        }
        return totals;
    }

    private static boolean isPrivateRoom( String roomType ) {
        return "TWIN".equals( roomType ) || "DBL".equals( roomType )
                || "TRIPLE".equals( roomType ) || "QUAD".equals( roomType );
    }

    private void postToHub( String hubUrl, String jwt, String topic, String data ) throws IOException {
        String body = "topic=" + URLEncoder.encode( topic, StandardCharsets.UTF_8 )
                + "&data=" + URLEncoder.encode( data, StandardCharsets.UTF_8 );
        HttpURLConnection conn = (HttpURLConnection) new URL( hubUrl ).openConnection();
        conn.setRequestMethod( "POST" );
        conn.setDoOutput( true );
        conn.setConnectTimeout( 5000 );
        conn.setReadTimeout( 10000 );
        conn.setRequestProperty( "Authorization", "Bearer " + jwt );
        conn.setRequestProperty( "Content-Type", "application/x-www-form-urlencoded" );
        try ( OutputStream out = conn.getOutputStream() ) {
            out.write( body.getBytes( StandardCharsets.UTF_8 ) );
        }
        int code = conn.getResponseCode();
        if ( code < 200 || code >= 300 ) {
            throw new IOException( "Mercure hub HTTP " + code );
        }
        conn.disconnect();
    }
}
