
package com.macbackpackers.beans.cloudbeds.responses;

import java.util.List;

import org.apache.commons.lang3.builder.ToStringBuilder;

public class Reservation extends CloudbedsJsonResponse {

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
    private String identifier;
    private String specialRequests;
    private String thirdPartyIdentifier;
    private String bookingDateHotelTime;
    private Integer kidsNumber;
    private Integer adultsNumber;
    private String paidValue;
    private List<BookingRoom> bookingRooms;
    private List<BookingNote> notes;
    private boolean cardDetailsPresent;

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

    public String getIdentifier() {
        return identifier;
    }

    public void setIdentifier( String identifier ) {
        this.identifier = identifier;
    }

    public String getThirdPartyIdentifier() {
        return thirdPartyIdentifier;
    }

    public void setThirdPartyIdentifier( String thirdPartyIdentifier ) {
        this.thirdPartyIdentifier = thirdPartyIdentifier;
    }

    public String getSpecialRequests() {
        return specialRequests;
    }

    public void setSpecialRequests( String specialRequests ) {
        this.specialRequests = specialRequests;
    }

    public String getBookingDateHotelTime() {
        return bookingDateHotelTime;
    }

    public void setBookingDateHotelTime( String bookingDateHotelTime ) {
        this.bookingDateHotelTime = bookingDateHotelTime;
    }

    public Integer getKidsNumber() {
        return kidsNumber;
    }

    public void setKidsNumber( Integer kidsNumber ) {
        this.kidsNumber = kidsNumber;
    }

    public Integer getAdultsNumber() {
        return adultsNumber;
    }

    public void setAdultsNumber( Integer adultsNumber ) {
        this.adultsNumber = adultsNumber;
    }

    public String getPaidValue() {
        return paidValue;
    }

    public void setPaidValue( String paidValue ) {
        this.paidValue = paidValue;
    }

    public List<BookingRoom> getBookingRooms() {
        return bookingRooms;
    }

    public void setBookingRooms( List<BookingRoom> bookingRooms ) {
        this.bookingRooms = bookingRooms;
    }

    public List<BookingNote> getNotes() {
        return notes;
    }

    public void setNotes( List<BookingNote> notes ) {
        this.notes = notes;
    }

    /**
     * Returns concatenated list of notes.
     * 
     * @return notes (or null if none found)
     */
    public String getNotesAsString() {
        if ( getNotes() == null || getNotes().isEmpty() ) {
            return null; // nothing to show
        }
        StringBuffer result = new StringBuffer();
        for ( BookingNote note : getNotes() ) {
            result.append( note.toString() ).append( "\n" );
        }
        return result.toString();
    }

    public boolean isCardDetailsPresent() {
        return cardDetailsPresent;
    }

    public void setCardDetailsPresent( boolean cardDetailsPresent ) {
        this.cardDetailsPresent = cardDetailsPresent;
    }

    @Override
    public String toString() {
        return ToStringBuilder.reflectionToString( this );
    }
}
