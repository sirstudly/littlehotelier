
package com.macbackpackers.scrapers.cloudbedsws;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

public class EdinburghVisitorLevyBookingCriteriaTest {

    private static final LocalDate STAY_DATE_FROM = LocalDate.of( 2026, 7, 24 );

    @Test
    public void matchesNewBookingCalendarEvent_requiresBookedTypeAndEligibleDates() {
        CloudbedsCalendarEvent match = event(
                "booked", "confirmed", "176704729", "2026-08-01", null );
        CloudbedsCalendarEvent canceled = event(
                "booked", "canceled", "176704730", "2026-08-01", null );
        CloudbedsCalendarEvent stayTooEarly = event(
                "booked", "confirmed", "176704732", "2026-07-24", null );
        CloudbedsCalendarEvent blocked = event(
                "blocked_dates", null, "0", "2026-08-01", null );

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

    @Test
    public void matchesNewBookingCalendarEvent_excludesInclusiveTaxSources() {
        CloudbedsCalendarEvent bdcByName = event(
                "booked", "confirmed", "176704733", "2026-08-01",
                "Booking.com (Hotel Collect Booking)" );
        CloudbedsCalendarEvent agodaByName = event(
                "booked", "confirmed", "176704734", "2026-08-01",
                "Agoda (Channel Collect Booking)" );
        CloudbedsCalendarEvent direct = event(
                "booked", "confirmed", "176704735", "2026-08-01", "Walk-In" );
        CloudbedsCalendarEvent bdcBySubSourceId = event(
                "booked", "confirmed", "176704736", "2026-08-01", "458851" );

        assertFalse( EdinburghVisitorLevyBookingCriteria.matchesNewBookingCalendarEvent(
                bdcByName, true, STAY_DATE_FROM ) );
        assertFalse( EdinburghVisitorLevyBookingCriteria.matchesNewBookingCalendarEvent(
                agodaByName, true, STAY_DATE_FROM ) );
        assertTrue( EdinburghVisitorLevyBookingCriteria.matchesNewBookingCalendarEvent(
                direct, true, STAY_DATE_FROM ) );
        assertFalse( EdinburghVisitorLevyBookingCriteria.matchesNewBookingCalendarEvent(
                bdcBySubSourceId, true, STAY_DATE_FROM, Set.of( "458851" ) ) );
        assertTrue( EdinburghVisitorLevyBookingCriteria.matchesNewBookingCalendarEvent(
                bdcBySubSourceId, true, STAY_DATE_FROM, Set.of( "75120" ) ) );
    }

    @Test
    public void isInclusiveTaxSourceName_matchesBdcAndAgodaPrefixes() {
        assertTrue( EdinburghVisitorLevyBookingCriteria.isInclusiveTaxSourceName(
                "Booking.com (Channel Collect Booking)" ) );
        assertTrue( EdinburghVisitorLevyBookingCriteria.isInclusiveTaxSourceName(
                "Agoda / Priceline (Hotel Collect Booking)" ) );
        assertFalse( EdinburghVisitorLevyBookingCriteria.isInclusiveTaxSourceName( "Hostelworld Group" ) );
        assertFalse( EdinburghVisitorLevyBookingCriteria.isInclusiveTaxSourceName( null ) );
    }

    private static CloudbedsCalendarEvent event( String type, String status, String bookingId,
            String endDate, String bookingSource ) {
        Map<String, String> raw = new LinkedHashMap<>();
        raw.put( "type", type );
        raw.put( "status", status );
        raw.put( "booking_id", bookingId );
        raw.put( "end_date", endDate );
        if ( bookingSource != null ) {
            raw.put( "booking_source", bookingSource );
        }
        return new CloudbedsCalendarEvent( raw );
    }
}
