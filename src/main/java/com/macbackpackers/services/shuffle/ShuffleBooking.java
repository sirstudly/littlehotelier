package com.macbackpackers.services.shuffle;

import java.time.LocalDate;

/**
 * One bed of a reservation over {@code [checkin, checkout)} (one current row of {@code wp_lh_booking_assignment}).
 * Pinned bookings (in-house guests, blocks, stays already started) are never moved.
 */
public record ShuffleBooking( String key, long reservationId, String guestName, int roomTypeId,
        LocalDate checkin, LocalDate checkout, boolean pinned ) {

    public boolean overlaps( ShuffleBooking other ) {
        return overlaps( other.checkin, other.checkout );
    }

    public boolean overlaps( LocalDate from, LocalDate to ) {
        return checkin.isBefore( to ) && from.isBefore( checkout );
    }

    public String label() {
        return reservationId + " (" + guestName + ")";
    }
}
