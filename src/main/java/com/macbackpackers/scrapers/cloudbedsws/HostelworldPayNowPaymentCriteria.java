package com.macbackpackers.scrapers.cloudbedsws;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;

/**
 * Eligibility rules for enqueueing {@code HostelworldPayNowPaymentJob}s from calendar WebSocket
 * events and {@code CreateHostelworldPayNowPaymentJob}.
 */
public final class HostelworldPayNowPaymentCriteria {

    /** Cloudbeds source names for Hostelworld channel-collect (Pay Now) bookings. */
    public static final String[] ELIGIBLE_SOURCE_NAMES = {
            "Hostelworld (Channel Collect Booking)",
            "Hostelworld & Hostelbookers (Channel Collect Booking)"
    };

    private HostelworldPayNowPaymentCriteria() {
        // utility class
    }

    /**
     * Parses the comma-delimited {@code data-source-id} values returned by
     * {@code CloudbedsScraper.lookupBookingSourceIds()} into calendar {@code booking_source}
     * sub-source ids.
     */
    public static Set<String> parseSubSourceIds( String lookupResult ) {
        if ( StringUtils.isBlank( lookupResult ) ) {
            return Collections.emptySet();
        }
        Set<String> ids = new HashSet<>();
        for ( String token : lookupResult.split( "," ) ) {
            String[] parts = token.trim().split( "-" );
            if ( parts.length >= 2 && StringUtils.isNotBlank( parts[1] ) ) {
                ids.add( parts[1] );
            }
        }
        return ids;
    }

    /**
     * Returns true if the calendar event is a Hostelworld channel-collect non-refundable booking.
     */
    public static boolean matchesCalendarEvent( CloudbedsCalendarEvent event, Set<String> eligibleSubSourceIds ) {
        if ( event == null || false == event.isReservationBooking() ) {
            return false;
        }
        if ( event.isCanceled() ) {
            return false;
        }
        if ( event.isHotelCollectBooking() ) {
            return false;
        }
        if ( false == event.isNonRefundable() ) {
            return false;
        }
        String bookingSource = event.getBookingSource();
        return StringUtils.isNotBlank( bookingSource ) && eligibleSubSourceIds.contains( bookingSource );
    }
}
