
package com.macbackpackers.scrapers.cloudbedsws;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;

/**
 * Shared eligibility rules for creating {@code ChargeNonRefundableBookingJob}s, matching
 * {@code CreateChargeNonRefundableBookingJob}.
 */
public final class NonRefundableBookingCriteria {

    /** Same booking-source names as {@code CreateChargeNonRefundableBookingJob}. */
    public static final String[] ELIGIBLE_SOURCE_NAMES = {
            "Booking.com (Hotel Collect Booking)",
            "Hostelworld & Hostelbookers (Hotel Collect Booking)",
            "Hostelworld (Hotel Collect Booking)"
    };

    private NonRefundableBookingCriteria() {
        // utility class
    }

    /**
     * Parses the comma-delimited {@code data-source-id} values returned by
     * {@code CloudbedsScraper.lookupBookingSourceIds()} (e.g. {@code 7-75120-1}) into the
     * {@code booking_source} sub-source ids used on calendar WebSocket events (e.g. {@code 75120}).
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
     * Returns true if the calendar event matches the same filters used by
     * {@code CreateChargeNonRefundableBookingJob}.
     *
     * @param event calendar event from the WebSocket
     * @param eligibleSubSourceIds sub-source ids for the eligible hotel-collect OTAs
     */
    public static boolean matchesCalendarEvent( CloudbedsCalendarEvent event, Set<String> eligibleSubSourceIds ) {
        if ( event == null || false == event.isReservationBooking() ) {
            return false;
        }
        if ( event.isCanceled() ) {
            return false;
        }
        if ( event.isPaid() ) {
            return false;
        }
        if ( false == event.isHotelCollectBooking() ) {
            return false;
        }
        if ( false == event.isNonRefundable() ) {
            return false;
        }
        String bookingSource = event.getBookingSource();
        return StringUtils.isNotBlank( bookingSource ) && eligibleSubSourceIds.contains( bookingSource );
    }
}
