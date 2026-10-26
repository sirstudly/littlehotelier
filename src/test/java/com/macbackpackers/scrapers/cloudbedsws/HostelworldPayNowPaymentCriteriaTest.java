package com.macbackpackers.scrapers.cloudbedsws;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

public class HostelworldPayNowPaymentCriteriaTest {

    @Test
    public void parseSubSourceIds_extractsMiddleSegment() {
        Set<String> ids = HostelworldPayNowPaymentCriteria.parseSubSourceIds( "7-99901-0,7-99902-0" );
        assertTrue( ids.contains( "99901" ) );
        assertTrue( ids.contains( "99902" ) );
    }

    @Test
    public void matchesCalendarEvent_requiresChannelCollectNonRefundableHwl() {
        Set<String> eligible = new HashSet<>();
        eligible.add( "99901" );

        CloudbedsCalendarEvent match = event(
                "booked", "confirmed", "189900001", "99901", "0",
                "34.43", "[{\"rate_description\":\"Non-refundable\"}]" );
        CloudbedsCalendarEvent hotelCollect = event(
                "booked", "confirmed", "189900002", "99901", "1",
                "34.43", "[{\"rate_description\":\"Non-refundable\"}]" );
        CloudbedsCalendarEvent refundable = event(
                "booked", "confirmed", "189900003", "99901", "0",
                "34.43", "[{\"rate_description\":\"Flexible\"}]" );
        CloudbedsCalendarEvent canceled = event(
                "booked", "canceled", "189900004", "99901", "0",
                "34.43", "[{\"rate_description\":\"Non-refundable\"}]" );
        CloudbedsCalendarEvent wrongSource = event(
                "booked", "confirmed", "189900005", "11111", "0",
                "34.43", "[{\"rate_description\":\"Non-refundable\"}]" );

        assertTrue( HostelworldPayNowPaymentCriteria.matchesCalendarEvent( match, eligible ) );
        assertFalse( HostelworldPayNowPaymentCriteria.matchesCalendarEvent( hotelCollect, eligible ) );
        assertFalse( HostelworldPayNowPaymentCriteria.matchesCalendarEvent( refundable, eligible ) );
        assertFalse( HostelworldPayNowPaymentCriteria.matchesCalendarEvent( canceled, eligible ) );
        assertFalse( HostelworldPayNowPaymentCriteria.matchesCalendarEvent( wrongSource, eligible ) );
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
