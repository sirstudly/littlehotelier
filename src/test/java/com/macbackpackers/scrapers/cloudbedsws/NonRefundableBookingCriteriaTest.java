
package com.macbackpackers.scrapers.cloudbedsws;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

public class NonRefundableBookingCriteriaTest {

    @Test
    public void parseSubSourceIds_extractsMiddleSegmentFromLookupResult() {
        Set<String> ids = NonRefundableBookingCriteria.parseSubSourceIds( "7-75120-1,7-75117-1,7-458851-1" );
        assertTrue( ids.contains( "75120" ) );
        assertTrue( ids.contains( "75117" ) );
        assertTrue( ids.contains( "458851" ) );
    }

    @Test
    public void matchesCalendarEvent_requiresSameFiltersAsBatchJob() {
        Set<String> eligible = new HashSet<>();
        eligible.add( "458851" );
        CloudbedsCalendarEvent match = event(
                "booked", "confirmed", "176704729", "458851", "1",
                "77.23", "[{\"rate_description\":\"Non-refundable\"}]" );
        CloudbedsCalendarEvent paid = event(
                "booked", "confirmed", "176704730", "458851", "1",
                "0", "[{\"rate_description\":\"Non-refundable\"}]" );
        CloudbedsCalendarEvent refundable = event(
                "booked", "confirmed", "176704731", "458851", "1",
                "77.23", "[{\"rate_description\":\"Flexible\"}]" );
        CloudbedsCalendarEvent channelCollect = event(
                "booked", "confirmed", "176704732", "458851", "0",
                "77.23", "[{\"rate_description\":\"Non-refundable\"}]" );
        CloudbedsCalendarEvent blocked = event(
                "blocked_dates", null, "0", null, null, null, null );

        assertTrue( NonRefundableBookingCriteria.matchesCalendarEvent( match, eligible ) );
        assertFalse( NonRefundableBookingCriteria.matchesCalendarEvent( paid, eligible ) );
        assertFalse( NonRefundableBookingCriteria.matchesCalendarEvent( refundable, eligible ) );
        assertFalse( NonRefundableBookingCriteria.matchesCalendarEvent( channelCollect, eligible ) );
        assertFalse( NonRefundableBookingCriteria.matchesCalendarEvent( blocked, eligible ) );
    }

    private static CloudbedsCalendarEvent event( String type, String status, String bookingId,
            String bookingSource, String hotelCollect, String balanceDue, String detailedRates ) {
        Map<String, String> raw = new LinkedHashMap<>();
        raw.put( "type", type );
        raw.put( "status", status );
        raw.put( "booking_id", bookingId );
        raw.put( "booking_source", bookingSource );
        raw.put( "is_hotel_collect_payment", hotelCollect );
        raw.put( "balance_due", balanceDue );
        raw.put( "detailed_rates", detailedRates );
        return new CloudbedsCalendarEvent( raw );
    }
}
