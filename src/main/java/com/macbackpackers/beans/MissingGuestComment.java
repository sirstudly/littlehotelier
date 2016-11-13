package com.macbackpackers.beans;

import java.util.Date;

/**
 * A search result entity for retrieving bookings without an entry in {@link GuestCommentReportEntry}.
 *
 */
public class MissingGuestComment {

    private int reservationId;
    private String bookingReference;
    private Date checkinDate;
    
    public MissingGuestComment(int reservationId, String bookingRef, Date checkinDate) {
        this.reservationId = reservationId;
        this.bookingReference = bookingRef;
        this.checkinDate = checkinDate;
    }
    
    public int getReservationId() {
        return reservationId;
    }
    
    public void setReservationId( int reservationId ) {
        this.reservationId = reservationId;
    }
    
    public String getBookingReference() {
        return bookingReference;
    }

    public void setBookingReference( String bookingReference ) {
        this.bookingReference = bookingReference;
    }

    public Date getCheckinDate() {
        return checkinDate;
    }
    
    public void setCheckinDate( Date checkinDate ) {
        this.checkinDate = checkinDate;
    }
}
