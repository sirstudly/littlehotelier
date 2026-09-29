
package com.macbackpackers.beans;

/**
 * A row on the consecutive bookings report: the same guest's reservation checking out as {@code nextReservationId}
 * checks in, in a different room of {@code roomTypeId}.
 */
public record ConsecutiveBookingPair( long reservationId, long nextReservationId, int roomTypeId ) {
}
