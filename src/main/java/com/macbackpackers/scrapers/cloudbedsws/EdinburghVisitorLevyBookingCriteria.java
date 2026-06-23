
package com.macbackpackers.scrapers.cloudbedsws;

import java.time.LocalDate;

import org.apache.commons.lang3.StringUtils;

import com.macbackpackers.services.EdinburghVisitorLevyCalculator;

/**
 * Shared eligibility rules for enqueueing {@code CalculateEdinburghVisitorLevyForBookingJob}s from
 * calendar WebSocket {@code booked} events, mirroring the cheap checks in
 * {@code EdinburghVisitorLevyService#isPotentiallyEligible}.
 */
public final class EdinburghVisitorLevyBookingCriteria {

    private EdinburghVisitorLevyBookingCriteria() {
        // utility class
    }

    /**
     * Returns true when a new {@code booked} calendar event may need a visitor levy adjustment.
     *
     * @param event calendar event from the WebSocket
     * @param evlEnabled {@code evl.enabled} property
     * @param stayDateFrom first stay date on which the levy applies
     * @param bookedDateFrom bookings made before this date are exempt
     */
    public static boolean matchesNewBookingCalendarEvent( CloudbedsCalendarEvent event,
            boolean evlEnabled, LocalDate stayDateFrom, LocalDate bookedDateFrom ) {
        if ( false == evlEnabled ) {
            return false;
        }
        if ( event == null || false == event.isReservationBooking() ) {
            return false;
        }
        if ( event.isCanceled() ) {
            return false;
        }
        String bookingDateStr = event.getBookingDate();
        if ( StringUtils.isNotBlank( bookingDateStr ) ) {
            LocalDate bookingDate = LocalDate.parse( bookingDateStr.trim().substring( 0, 10 ) );
            if ( EdinburghVisitorLevyCalculator.isBookingExempt( bookingDate, bookedDateFrom ) ) {
                return false;
            }
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
