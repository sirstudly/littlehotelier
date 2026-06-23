
package com.macbackpackers.scrapers.cloudbedsws;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

public class EdinburghVisitorLevyBookingCriteriaTest {

    private static final LocalDate STAY_DATE_FROM = LocalDate.of( 2026, 7, 24 );

    @Test
    public void matchesNewBookingCalendarEvent_requiresBookedTypeAndEligibleDates() {
        CloudbedsCalendarEvent match = event(
                "booked", "confirmed", "176704729", "2026-08-01" );
        CloudbedsCalendarEvent canceled = event(
                "booked", "canceled", "176704730", "2026-08-01" );
        CloudbedsCalendarEvent stayTooEarly = event(
                "booked", "confirmed", "176704732", "2026-07-24" );
        CloudbedsCalendarEvent blocked = event(
                "blocked_dates", null, "0", "2026-08-01" );

        assertTrue( EdinburghVisitorLevyBookingCriteria.matchesNewBookingCalendarEvent(
                match, true, STAY_DATE_FROM ) );
        assertFalse( EdinburghVisitorLevyBookingCriteria.matchesNewBookingCalendarEvent(
                match, false, STAY_DATE_FROM ) );
        assertFalse( EdinburghVisitorLevyBookingCriteria.matchesNewBookingCalendarEvent(
                canceled, true, STAY_DATE_FROM ) );
        assertFalse( EdinburghVisitorLevyBookingCriteria.matchesNewBookingCalendarEvent(
                stayTooEarly, true, STAY_DATE_FROM ) );
        assertFalse( EdinburghVisitorLevyBookingCriteria.matchesNewBookingCalendarEvent(
                blocked, true, STAY_DATE_FROM ) );
    }

    private static CloudbedsCalendarEvent event( String type, String status, String bookingId,
            String endDate ) {
        Map<String, String> raw = new LinkedHashMap<>();
        raw.put( "type", type );
        raw.put( "status", status );
        raw.put( "booking_id", bookingId );
        raw.put( "end_date", endDate );
        return new CloudbedsCalendarEvent( raw );
    }
}
