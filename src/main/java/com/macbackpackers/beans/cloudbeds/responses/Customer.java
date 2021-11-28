
package com.macbackpackers.beans.cloudbeds.responses;

import java.math.BigDecimal;

import org.apache.commons.lang3.builder.ToStringBuilder;

/**
 * Also known as a reservation. Depending on the response type, some fields may not be used.
 */
public class Customer extends CloudbedsJsonResponse {

    private String firstName;
    private String lastName;
    private String customerId;
    private String email;
    private String bookingDate;
    private String checkinDate;
    private String checkoutDate;
    private BigDecimal balanceDue;
    private String grandTotal;
    private String id;
    private String sourceName;
    private String status;
    private String nights;
    private String isHotelCollectBooking;

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

    public String getCustomerId() {
        return customerId;
    }

    public void setCustomerId( String customerId ) {
        this.customerId = customerId;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail( String email ) {
        this.email = email;
    }

    public String getBookingDate() {
        return bookingDate;
    }

    public void setBookingDate( String bookingDate ) {
        this.bookingDate = bookingDate;
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

    public BigDecimal getBalanceDue() {
        return balanceDue;
    }

    public void setBalanceDue( BigDecimal balanceDue ) {
        this.balanceDue = balanceDue;
    }

    public String getGrandTotal() {
        return grandTotal;
    }

    public void setGrandTotal( String grandTotal ) {
        this.grandTotal = grandTotal;
    }

    public String getId() {
        return id;
    }

    public void setId( String id ) {
        this.id = id;
    }

    public String getSourceName() {
        return sourceName;
    }

    public void setSourceName( String sourceName ) {
        this.sourceName = sourceName;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus( String status ) {
        this.status = status;
    }

    public String getNights() {
        return nights;
    }

    public void setNights( String nights ) {
        this.nights = nights;
    }

    public String getIsHotelCollectBooking() {
        return isHotelCollectBooking;
    }

    public void setIsHotelCollectBooking( String isHotelCollectBooking ) {
        this.isHotelCollectBooking = isHotelCollectBooking;
    }

    public boolean isHotelCollectBooking() {
        return "1".equals( getIsHotelCollectBooking() );
    }

    @Override
    public String toString() {
        return ToStringBuilder.reflectionToString( this );
    }
}
