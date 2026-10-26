package com.macbackpackers.scrapers.cloudbedsws;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

public class AirbnbPrepaidPaymentCriteriaTest {

    @Test
    public void parseSubSourceIds_extractsMiddleSegment() {
        Set<String> ids = AirbnbPrepaidPaymentCriteria.parseSubSourceIds( "7-88801-0" );
        assertTrue( ids.contains( "88801" ) );
    }

    @Test
    public void matchesCalendarEvent_requiresChannelCollectAirbnb_noNrrRequired() {
        Set<String> eligible = new HashSet<>();
        eligible.add( "88801" );

        CloudbedsCalendarEvent matchFlexible = event(
                "booked", "confirmed", "189800001", "88801", "0",
                "100.00", "[{\"rate_description\":\"Flexible\"}]" );
        CloudbedsCalendarEvent hotelCollect = event(
                "booked", "confirmed", "189800002", "88801", "1",
                "100.00", "[{\"rate_description\":\"Non-refundable\"}]" );
        CloudbedsCalendarEvent canceled = event(
                "booked", "canceled", "189800003", "88801", "0",
                "100.00", null );
        CloudbedsCalendarEvent wrongSource = event(
                "booked", "confirmed", "189800004", "11111", "0",
                "100.00", null );

        assertTrue( AirbnbPrepaidPaymentCriteria.matchesCalendarEvent( matchFlexible, eligible ),
                "Airbnb channel-collect should match without requiring NRR" );
        assertFalse( AirbnbPrepaidPaymentCriteria.matchesCalendarEvent( hotelCollect, eligible ) );
        assertFalse( AirbnbPrepaidPaymentCriteria.matchesCalendarEvent( canceled, eligible ) );
        assertFalse( AirbnbPrepaidPaymentCriteria.matchesCalendarEvent( wrongSource, eligible ) );
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
        if ( detailedRates != null ) {
            raw.put( "detailed_rates", detailedRates );
        }
        return new CloudbedsCalendarEvent( raw );
    }
}
