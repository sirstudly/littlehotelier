
package com.macbackpackers.scrapers.cloudbedsws;

import java.time.LocalDate;
import java.util.Collections;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;

import com.macbackpackers.services.EdinburghVisitorLevyCalculator;

/**
 * Shared eligibility rules for enqueueing {@code CalculateEdinburghVisitorLevyForBookingJob}s from
 * calendar WebSocket {@code booked} events, mirroring the cheap checks in
 * {@code EdinburghVisitorLevyService#isPotentiallyEligible}.
 */
public final class EdinburghVisitorLevyBookingCriteria {

    /** Exact Cloudbeds source names for BDC and Agoda / Priceline (inclusive tax). */
    public static final String[] INCLUSIVE_TAX_SOURCE_NAMES = {
            "Booking.com (Hotel Collect Booking)",
            "Booking.com (Channel Collect Booking)",
            "Agoda (Hotel Collect Booking)",
            "Agoda (Channel Collect Booking)",
            "Agoda / Priceline (Hotel Collect Booking)",
            "Agoda / Priceline (Channel Collect Booking)"
    };

    private EdinburghVisitorLevyBookingCriteria() {
        // utility class
    }

    /**
     * Returns true when the source name indicates an inclusive-tax OTA booking (BDC or Agoda /
     * Priceline), matching {@code Reservation#isInclusiveTaxBooking()}.
     */
    public static boolean isInclusiveTaxSourceName( String sourceName ) {
        return sourceName != null
                && ( sourceName.startsWith( "Booking.com" ) || sourceName.startsWith( "Agoda" ) );
    }

    /**
     * Returns true when a calendar event is from an inclusive-tax OTA. {@code booking_source} is
     * usually a numeric sub-source id on WebSocket events; it may also be a source name string.
     */
    public static boolean isInclusiveTaxCalendarEvent( CloudbedsCalendarEvent event,
            Set<String> inclusiveTaxSubSourceIds ) {
        if ( event == null ) {
            return false;
        }
        String bookingSource = event.getBookingSource();
        if ( StringUtils.isBlank( bookingSource ) ) {
            return false;
        }
        if ( isInclusiveTaxSourceName( bookingSource ) ) {
            return true;
        }
        return inclusiveTaxSubSourceIds != null && inclusiveTaxSubSourceIds.contains( bookingSource );
    }

    /**
     * Returns true when a new {@code booked} calendar event may need a visitor levy adjustment.
     *
     * @param event calendar event from the WebSocket
     * @param evlEnabled {@code evl.enabled} property
     * @param stayDateFrom first stay date on which the levy applies
     */
    public static boolean matchesNewBookingCalendarEvent( CloudbedsCalendarEvent event,
            boolean evlEnabled, LocalDate stayDateFrom ) {
        return matchesNewBookingCalendarEvent( event, evlEnabled, stayDateFrom, Collections.emptySet() );
    }

    public static boolean matchesNewBookingCalendarEvent( CloudbedsCalendarEvent event,
            boolean evlEnabled, LocalDate stayDateFrom, Set<String> inclusiveTaxSubSourceIds ) {
        if ( false == evlEnabled ) {
            return false;
        }
        if ( event == null || false == event.isReservationBooking() ) {
            return false;
        }
        if ( event.isCanceled() ) {
            return false;
        }
        if ( isInclusiveTaxCalendarEvent( event, inclusiveTaxSubSourceIds ) ) {
            return false;
        }
        String endDateStr = event.getEndDate();
        if ( StringUtils.isNotBlank( endDateStr )
                && false == EdinburghVisitorLevyCalculator.hasEligibleStayDates(
                        LocalDate.parse( endDateStr.trim() ), stayDateFrom ) ) {
            return false;
        }
        return true;
    }
}
