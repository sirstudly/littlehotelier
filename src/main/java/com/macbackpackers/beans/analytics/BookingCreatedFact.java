package com.macbackpackers.beans.analytics;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One Cloudbeds reservation for the booked-date analytics mart.
 */
public class BookingCreatedFact {

    private String propertyCode;
    private String reservationId;
    private LocalDate bookedOn;
    private String bookingSource;
    private String status;
    private BigDecimal grandTotal = BigDecimal.ZERO;
    private LocalDate checkinDate;
    private LocalDate checkoutDate;
    private String guestName;

    public String getPropertyCode() {
        return propertyCode;
    }

    public void setPropertyCode( String propertyCode ) {
        this.propertyCode = propertyCode;
    }

    public String getReservationId() {
        return reservationId;
    }

    public void setReservationId( String reservationId ) {
        this.reservationId = reservationId;
    }

    public LocalDate getBookedOn() {
        return bookedOn;
    }

    public void setBookedOn( LocalDate bookedOn ) {
        this.bookedOn = bookedOn;
    }

    public String getBookingSource() {
        return bookingSource;
    }

    public void setBookingSource( String bookingSource ) {
        this.bookingSource = bookingSource;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus( String status ) {
        this.status = status;
    }

    public BigDecimal getGrandTotal() {
        return grandTotal;
    }

    public void setGrandTotal( BigDecimal grandTotal ) {
        this.grandTotal = grandTotal == null ? BigDecimal.ZERO : grandTotal;
    }

    public LocalDate getCheckinDate() {
        return checkinDate;
    }

    public void setCheckinDate( LocalDate checkinDate ) {
        this.checkinDate = checkinDate;
    }

    public LocalDate getCheckoutDate() {
        return checkoutDate;
    }

    public void setCheckoutDate( LocalDate checkoutDate ) {
        this.checkoutDate = checkoutDate;
    }

    public String getGuestName() {
        return guestName;
    }

    public void setGuestName( String guestName ) {
        this.guestName = guestName;
    }
}
