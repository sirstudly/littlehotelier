package com.macbackpackers.scrapers.cloudbedsws;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;

/**
 * Eligibility rules for enqueueing {@code AirbnbPrepaidPaymentJob}s from calendar WebSocket events
 * and {@code CreateAirbnbPrepaidPaymentJob}.
 */
public final class AirbnbPrepaidPaymentCriteria {

    /** Cloudbeds source name for Airbnb channel-collect (prepaid) bookings. */
    public static final String[] ELIGIBLE_SOURCE_NAMES = {
            "Airbnb (API) (Channel Collect Booking)"
    };

    private AirbnbPrepaidPaymentCriteria() {
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
     * Returns true if the calendar event is an Airbnb channel-collect booking (NRR not required).
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
        String bookingSource = event.getBookingSource();
        return StringUtils.isNotBlank( bookingSource ) && eligibleSubSourceIds.contains( bookingSource );
    }
}
