package com.macbackpackers.services;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.macbackpackers.beans.GuestCommentReportEntry;
import com.macbackpackers.dao.WordPressDAO;

/**
 * Fills {@code wp_lh_rpt_guest_comments.guest_request} with what the guest actually asked for, stripped
 * of OTA boilerplate. Extraction is done by ronbot-bridge ({@code POST /internal/guest-requests}),
 * which runs a text-only Cursor agent.
 * <p>
 * Config (WordPress options): {@code hbo_ronbot_bridge_url}, {@code hbo_guest_request_max_batches}.
 * The bearer token is {@code RONBOT_TOKEN} (shared with ronbot-read-api / ronbot-bridge).
 */
@Service
public class GuestRequestExtractionService {

    private static final Logger LOGGER = LoggerFactory.getLogger( GuestRequestExtractionService.class );

    /** Must not exceed the bridge's MAX_GUEST_REQUEST_BATCH. */
    static final int BATCH_SIZE = 100;

    /**
     * Abort the run after this many extraction calls in a row fail. Isolating one bad comment by
     * splitting a batch of 100 fails at most 8 calls in a row, so this only trips on systemic failure.
     */
    static final int MAX_CONSECUTIVE_FAILED_CALLS = 16;

    static final String OPTION_BRIDGE_URL = "hbo_ronbot_bridge_url";
    static final String OPTION_MAX_BATCHES = "hbo_guest_request_max_batches";

    /** One agent run per attempt and the bridge retries once on an invalid reply. */
    private static final int READ_TIMEOUT_MILLIS = 6 * 60 * 1000;

    @Autowired
    private WordPressDAO dao;

    @Value( "${RONBOT_TOKEN:${ronbot.token:}}" )
    private String ronbotToken;

    private final Gson gson = new Gson();

    /** Sends a batch to the extractor and returns reservation id to guest request (null value = nothing to action). */
    @FunctionalInterface
    interface GuestRequestClient {
        Map<Integer, String> extract( List<GuestCommentReportEntry> batch ) throws IOException;
    }

    @FunctionalInterface
    interface GuestRequestSink {
        void save( int reservationId, String guestRequest );
    }

    /**
     * The extractor couldn't produce a usable result for this batch (bad model reply, missing ids).
     * Worth retrying on smaller batches, unlike connection / auth / timeout failures.
     */
    static class GuestRequestContentException extends IOException {
        private static final long serialVersionUID = 1L;

        GuestRequestContentException( String message ) {
            super( message );
        }

        GuestRequestContentException( String message, Throwable cause ) {
            super( message, cause );
        }
    }

    /** Totals for one run. */
    static final class RunSummary {
        int classified;
        int failed;
        int calls;
        int consecutiveFailedCalls;
    }

    /**
     * Extracts guest requests for comments that are new or changed since they were last extracted.
     *
     * @throws IOException if the bridge is unreachable or keeps failing
     */
    public void extractPendingGuestRequests() throws IOException {
        String bridgeUrl = StringUtils.removeEnd(
                dao.getDefaultOption( OPTION_BRIDGE_URL, "http://ronbot-bridge:8787" ), "/" );
        int maxBatches = Integer.parseInt( dao.getDefaultOption( OPTION_MAX_BATCHES, "5" ) );
        if ( StringUtils.isBlank( ronbotToken ) ) {
            throw new IOException( "RONBOT_TOKEN not set; cannot call ronbot-bridge guest request extraction" );
        }
        List<GuestCommentReportEntry> pending = dao.fetchUnclassifiedGuestComments( maxBatches * BATCH_SIZE );
        LOGGER.info( "Guest request extraction: {} pending (cap {} batches of {})", pending.size(), maxBatches, BATCH_SIZE );
        RunSummary summary = extract( pending, batch -> postToBridge( bridgeUrl, batch ), dao::updateGuestRequest );
        LOGGER.info( "Guest request extraction: classified {}, failed {}, bridge calls {}",
                summary.classified, summary.failed, summary.calls );
    }

    /**
     * Classifies {@code pending} in batches of {@link #BATCH_SIZE}. When the extractor can't handle a batch
     * ({@link GuestRequestContentException}) it is split in half and each half retried, so a single bad
     * comment costs a handful of calls and doesn't block the rest; it stays unclassified for the next run.
     * Any other failure (bridge unreachable, auth, timeout) aborts the run straight away.
     *
     * @throws IOException on a non-content failure, or after {@link #MAX_CONSECUTIVE_FAILED_CALLS}
     *             failed calls in a row
     */
    static RunSummary extract( List<GuestCommentReportEntry> pending, GuestRequestClient client,
            GuestRequestSink sink ) throws IOException {
        RunSummary summary = new RunSummary();
        for ( int from = 0 ; from < pending.size() ; from += BATCH_SIZE ) {
            classify( pending.subList( from, Math.min( from + BATCH_SIZE, pending.size() ) ), client, sink, summary );
        }
        return summary;
    }

    private static void classify( List<GuestCommentReportEntry> batch, GuestRequestClient client,
            GuestRequestSink sink, RunSummary summary ) throws IOException {
        summary.calls++;
        try {
            save( batch, client.extract( batch ), sink, summary );
            summary.consecutiveFailedCalls = 0;
            return;
        }
        catch ( GuestRequestContentException ex ) {
            if ( ++summary.consecutiveFailedCalls >= MAX_CONSECUTIVE_FAILED_CALLS ) {
                throw new IOException( "Guest request extraction aborted after " + summary.consecutiveFailedCalls
                        + " failed calls in a row (classified " + summary.classified + ")", ex );
            }
            if ( batch.size() == 1 ) {
                summary.failed++;
                LOGGER.warn( "Guest request extraction failed for reservation {}: {}",
                        batch.get( 0 ).getReservationId(), ex.getMessage() );
                return;
            }
            LOGGER.warn( "Guest request batch of {} failed, splitting: {}", batch.size(), ex.getMessage() );
        }
        int mid = batch.size() / 2;
        classify( batch.subList( 0, mid ), client, sink, summary );
        classify( batch.subList( mid, batch.size() ), client, sink, summary );
    }

    private static void save( List<GuestCommentReportEntry> batch, Map<Integer, String> results,
            GuestRequestSink sink, RunSummary summary ) throws IOException {
        for ( GuestCommentReportEntry entry : batch ) {
            if ( false == results.containsKey( entry.getReservationId() ) ) {
                throw new GuestRequestContentException( "No result for reservation " + entry.getReservationId() );
            }
        }
        for ( GuestCommentReportEntry entry : batch ) {
            sink.save( entry.getReservationId(), StringUtils.trimToNull( results.get( entry.getReservationId() ) ) );
            summary.classified++;
        }
    }

    String toRequestJson( List<GuestCommentReportEntry> batch ) {
        JsonArray arr = new JsonArray();
        for ( GuestCommentReportEntry entry : batch ) {
            JsonObject o = new JsonObject();
            o.addProperty( "id", entry.getReservationId() );
            o.addProperty( "text", StringUtils.defaultString( entry.getComments() ) );
            arr.add( o );
        }
        return gson.toJson( arr );
    }

    static Map<Integer, String> parseResponseJson( String json ) throws IOException {
        Map<Integer, String> results = new HashMap<>();
        try {
            for ( JsonElement el : JsonParser.parseString( json ).getAsJsonArray() ) {
                JsonObject o = el.getAsJsonObject();
                JsonElement request = o.get( "request" );
                results.put( o.get( "id" ).getAsInt(),
                        request == null || request.isJsonNull() ? null : request.getAsString() );
            }
        }
        catch ( RuntimeException ex ) {
            throw new GuestRequestContentException( "Unexpected guest request response: " + StringUtils.abbreviate( json, 300 ), ex );
        }
        return results;
    }

    private Map<Integer, String> postToBridge( String bridgeUrl, List<GuestCommentReportEntry> batch ) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL( bridgeUrl + "/internal/guest-requests" ).openConnection();
        try {
            conn.setRequestMethod( "POST" );
            conn.setDoOutput( true );
            conn.setConnectTimeout( 10000 );
            conn.setReadTimeout( READ_TIMEOUT_MILLIS );
            conn.setRequestProperty( "Authorization", "Bearer " + ronbotToken );
            conn.setRequestProperty( "Content-Type", "application/json; charset=utf-8" );
            try ( OutputStream out = conn.getOutputStream() ) {
                out.write( toRequestJson( batch ).getBytes( StandardCharsets.UTF_8 ) );
            }
            int code = conn.getResponseCode();
            String body = readBody( code < 400 ? conn.getInputStream() : conn.getErrorStream() );
            String error = "ronbot-bridge HTTP " + code + ": " + StringUtils.abbreviate( body, 300 );
            if ( code == HttpURLConnection.HTTP_BAD_GATEWAY ) {
                throw new GuestRequestContentException( error );
            }
            if ( code < 200 || code >= 300 ) {
                throw new IOException( error );
            }
            return parseResponseJson( body );
        }
        finally {
            conn.disconnect();
        }
    }

    private static String readBody( InputStream in ) throws IOException {
        if ( in == null ) {
            return "";
        }
        try ( InputStream is = in ) {
            return new String( is.readAllBytes(), StandardCharsets.UTF_8 );
        }
    }
}
