
package com.macbackpackers.beans.cloudbeds.responses;

import java.util.List;

import org.apache.commons.lang3.builder.ToStringBuilder;

public class Reservation {

    private String reservationId;
    private String status;
    private String firstName;
    private String lastName;
    private String email;
    private String customerId;
    private String grandTotal;
    private String bookingDeposit;
    private String balanceDue;
    private String bookingVia;
    private String checkinDate;
    private String checkoutDate;
    private String sourceName;
    private List<BookingRoom> bookingRooms;

    public String getReservationId() {
        return reservationId;
    }

    public void setReservationId( String reservationId ) {
        this.reservationId = reservationId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus( String status ) {
        this.status = status;
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName( String firstName ) {
        this.firstName = firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName( String lastName ) {
        this.lastName = lastName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail( String email ) {
        this.email = email;
    }

    public String getCustomerId() {
        return customerId;
    }

    public void setCustomerId( String customerId ) {
        this.customerId = customerId;
    }

    public String getGrandTotal() {
        return grandTotal;
    }

    public void setGrandTotal( String grandTotal ) {
        this.grandTotal = grandTotal;
    }

    public String getBookingDeposit() {
        return bookingDeposit;
    }

    public void setBookingDeposit( String bookingDeposit ) {
        this.bookingDeposit = bookingDeposit;
    }

    public String getBalanceDue() {
        return balanceDue;
    }

    public void setBalanceDue( String balanceDue ) {
        this.balanceDue = balanceDue;
    }

    public String getBookingVia() {
        return bookingVia;
    }

    public void setBookingVia( String bookingVia ) {
        this.bookingVia = bookingVia;
    }

    public String getCheckinDate() {
        return checkinDate;
    }

    public void setCheckinDate( String checkinDate ) {
        this.checkinDate = checkinDate;
    }

    public String getCheckoutDate() {
        return checkoutDate;
    }

    public void setCheckoutDate( String checkoutDate ) {
        this.checkoutDate = checkoutDate;
    }

    public String getSourceName() {
        return sourceName;
    }

    public void setSourceName( String sourceName ) {
        this.sourceName = sourceName;
    }

    public List<BookingRoom> getBookingRooms() {
        return bookingRooms;
    }

    public void setBookingRooms( List<BookingRoom> bookingRooms ) {
        this.bookingRooms = bookingRooms;
    }

    @Override
    public String toString() {
        return ToStringBuilder.reflectionToString( this );
    }
}
