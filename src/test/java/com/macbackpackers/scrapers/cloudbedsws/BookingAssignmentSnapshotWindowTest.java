package com.macbackpackers.scrapers.cloudbedsws;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

public class BookingAssignmentSnapshotWindowTest {

    @Test
    public void longRunningClosureDoesNotStretchWindowBack() {
        List<CloudbedsCalendarEvent> events = Arrays.asList(
                event( "2022-08-31", "2026-12-31" ), // out_of_service closure still in the snapshot
                event( "2026-08-29", "2026-09-01" ),
                event( "2026-09-20", "2026-09-27" ),
                event( "2026-11-05", "2026-11-08" ) );

        LocalDate[] window = BookingAssignmentCloudbedsEventListener.snapshotWindow( events );

        assertThat( window[0], is( LocalDate.parse( "2026-09-01" ) ) );
        assertThat( window[1], is( LocalDate.parse( "2026-11-05" ) ) );
    }

    @Test
    public void longTermStayDoesNotStretchWindowBack() {
        List<CloudbedsCalendarEvent> events = Arrays.asList(
                event( "2025-01-10", "2026-10-10" ),
                event( "2026-09-01", "2026-09-03" ) );

        LocalDate[] window = BookingAssignmentCloudbedsEventListener.snapshotWindow( events );

        assertThat( window[0], is( LocalDate.parse( "2026-09-03" ) ) );
        assertThat( window[1], is( LocalDate.parse( "2026-09-01" ) ) );
    }

    @Test
    public void eventsWithoutDatesGiveNoWindow() {
        List<CloudbedsCalendarEvent> events = Collections.singletonList( event( null, "" ) );

        LocalDate[] window = BookingAssignmentCloudbedsEventListener.snapshotWindow( events );

        assertThat( window[0], nullValue() );
        assertThat( window[1], nullValue() );
    }

    private static CloudbedsCalendarEvent event( String start, String end ) {
        Map<String, String> raw = new HashMap<>();
        raw.put( "start_date", start );
        raw.put( "end_date", end );
        return new CloudbedsCalendarEvent( raw );
    }
}
