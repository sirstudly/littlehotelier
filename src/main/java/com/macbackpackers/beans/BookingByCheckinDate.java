
package com.macbackpackers.beans;

import java.util.Date;

/**
 * A search result entity for retrieving unique bookings by checkin date.
 *
 */
public class BookingByCheckinDate {

    private String bookingRef;
    private int reservationId;
    private Date checkinDate;

    public BookingByCheckinDate( String bookingRef, int reservationId, Date checkinDate ) {
        this.bookingRef = bookingRef;
        this.reservationId = reservationId;
        this.checkinDate = checkinDate;
    }

    public String getBookingRef() {
        return bookingRef;
    }

    public void setBookingRef( String bookingRef ) {
        this.bookingRef = bookingRef;
    }

    public int getReservationId() {
        return reservationId;
    }

    public void setReservationId( int reservationId ) {
        this.reservationId = reservationId;
    }

    public Date getCheckinDate() {
        return checkinDate;
    }

    public void setCheckinDate( Date checkinDate ) {
        this.checkinDate = checkinDate;
    }
}
