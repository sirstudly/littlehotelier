package com.macbackpackers.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

import com.macbackpackers.beans.GuestCommentReportEntry;
import com.macbackpackers.services.GuestRequestExtractionService.GuestRequestClient;
import com.macbackpackers.services.GuestRequestExtractionService.GuestRequestContentException;
import com.macbackpackers.services.GuestRequestExtractionService.RunSummary;

/**
 * Pure unit tests for guest request batching and bridge JSON (no Spring / HTTP).
 */
public class GuestRequestExtractionServiceTest {

    private final Map<Integer, String> saved = new LinkedHashMap<>();
    private final List<Integer> batchSizes = new ArrayList<>();

    private static List<GuestCommentReportEntry> entries( int count ) {
        return IntStream.rangeClosed( 1, count )
                .mapToObj( i -> new GuestCommentReportEntry( i, "comment " + i ) )
                .collect( Collectors.toList() );
    }

    /** Echoes "request N" for even ids and null for odd ids. */
    private GuestRequestClient echoClient() {
        return batch -> {
            batchSizes.add( batch.size() );
            Map<Integer, String> out = new HashMap<>();
            batch.forEach( e -> out.put( e.getReservationId(), e.getReservationId() % 2 == 0 ? "request " + e.getReservationId() : null ) );
            return out;
        };
    }

    /** Fails with a content error for any batch containing one of {@code poison}. */
    private GuestRequestClient poisonClient( Set<Integer> poison ) {
        GuestRequestClient echo = echoClient();
        return batch -> {
            if ( batch.stream().anyMatch( e -> poison.contains( e.getReservationId() ) ) ) {
                batchSizes.add( batch.size() );
                throw new GuestRequestContentException( "bad reply" );
            }
            return echo.extract( batch );
        };
    }

    @Test
    public void classifiesInBatchesOfOneHundred() throws Exception {
        RunSummary summary = GuestRequestExtractionService.extract( entries( 250 ), echoClient(), saved::put );

        assertEquals( List.of( 100, 100, 50 ), batchSizes );
        assertEquals( 250, summary.classified );
        assertEquals( 0, summary.failed );
        assertEquals( 3, summary.calls );
        assertEquals( 250, saved.size() );
        assertEquals( "request 2", saved.get( 2 ) );
        assertTrue( saved.containsKey( 3 ) );
        assertNull( saved.get( 3 ), "nothing to action is saved as null" );
    }

    @Test
    public void blankRequestSavedAsNull() throws Exception {
        GuestRequestExtractionService.extract( entries( 1 ), batch -> {
            Map<Integer, String> out = new HashMap<>();
            out.put( 1, "   " );
            return out;
        }, saved::put );
        assertTrue( saved.containsKey( 1 ) );
        assertNull( saved.get( 1 ) );
    }

    @Test
    public void poisonEntryIsolatedBySplittingInFewCalls() throws Exception {
        RunSummary summary = GuestRequestExtractionService.extract( entries( 100 ), poisonClient( Set.of( 7 ) ), saved::put );

        assertEquals( 99, summary.classified );
        assertEquals( 1, summary.failed );
        assertEquals( 99, saved.size() );
        assertTrue( false == saved.containsKey( 7 ), "failing entry left unclassified for the next run" );
        assertTrue( summary.calls <= 2 * 8, "bisection, not one call per entry: " + summary.calls );
    }

    @Test
    public void severalPoisonEntriesDoNotAbortTheRun() throws Exception {
        RunSummary summary = GuestRequestExtractionService.extract( entries( 300 ),
                poisonClient( Set.of( 1, 2, 150, 299 ) ), saved::put );

        assertEquals( 296, summary.classified );
        assertEquals( 4, summary.failed );
    }

    @Test
    public void missingIdInReplyTriggersSplitWithoutPartialSaves() throws Exception {
        RunSummary summary = GuestRequestExtractionService.extract( entries( 2 ), batch -> {
            Map<Integer, String> out = new HashMap<>();
            if ( batch.size() == 1 ) {
                out.put( batch.get( 0 ).getReservationId(), "ok" );
            }
            else {
                out.put( 1, "only one" );
            }
            return out;
        }, saved::put );

        assertEquals( 2, summary.classified );
        assertEquals( "ok", saved.get( 1 ), "batch result discarded, split retry saved" );
        assertEquals( "ok", saved.get( 2 ) );
    }

    @Test
    public void connectionFailureAbortsImmediately() {
        assertThrows( IOException.class, () -> GuestRequestExtractionService.extract(
                entries( 300 ), batch -> {
                    batchSizes.add( batch.size() );
                    throw new IOException( "connection refused" );
                }, saved::put ) );
        assertEquals( List.of( 100 ), batchSizes, "no split / retry on non-content failures" );
        assertTrue( saved.isEmpty() );
    }

    @Test
    public void abortsWhenEveryCallFails() {
        IOException ex = assertThrows( IOException.class, () -> GuestRequestExtractionService.extract(
                entries( 300 ), batch -> {
                    batchSizes.add( batch.size() );
                    throw new GuestRequestContentException( "model unavailable" );
                }, saved::put ) );
        assertTrue( ex.getMessage().contains( "16 failed calls in a row" ) );
        assertEquals( GuestRequestExtractionService.MAX_CONSECUTIVE_FAILED_CALLS, batchSizes.size() );
        assertTrue( saved.isEmpty() );
    }

    @Test
    public void requestJsonCarriesIdAndText() {
        String json = new GuestRequestExtractionService().toRequestJson(
                List.of( new GuestCommentReportEntry( 42, "<br />Bottom bunk \"please\"" ) ) );
        assertEquals( "[{\"id\":42,\"text\":\"\\u003cbr /\\u003eBottom bunk \\\"please\\\"\"}]", json );
    }

    @Test
    public void parsesBridgeResponse() throws Exception {
        Map<Integer, String> results = GuestRequestExtractionService.parseResponseJson(
                "[{\"id\":1,\"request\":\"Bottom bunk please.\"},{\"id\":2,\"request\":null}]" );
        assertEquals( "Bottom bunk please.", results.get( 1 ) );
        assertTrue( results.containsKey( 2 ) );
        assertNull( results.get( 2 ) );
    }

    @Test
    public void unexpectedBridgeResponseIsContentFailure() {
        assertThrows( GuestRequestContentException.class,
                () -> GuestRequestExtractionService.parseResponseJson( "{\"error\":\"x\"}" ) );
    }
}
