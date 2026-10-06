package com.macbackpackers.services.shuffle;

import java.time.LocalDate;

/**
 * One bed of a reservation over {@code [checkin, checkout)} (one current row of {@code wp_lh_booking_assignment}).
 * Pinned bookings (in-house guests, blocks, stays already started, bookings on their locked bed) are never moved,
 * except that a {@code locked} one may be unlocked by the {@link Relaxation#UNLOCK} alternative.
 */
public record ShuffleBooking( String key, long reservationId, String guestName, int roomTypeId,
        LocalDate checkin, LocalDate checkout, boolean pinned, boolean locked ) {

    public ShuffleBooking( String key, long reservationId, String guestName, int roomTypeId,
            LocalDate checkin, LocalDate checkout, boolean pinned ) {
        this( key, reservationId, guestName, roomTypeId, checkin, checkout, pinned, false );
    }

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
