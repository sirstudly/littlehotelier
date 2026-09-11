package com.macbackpackers.ronbot.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Redacted live Cloudbeds reservation summary for ronbot responses.
 */
public class BookingSummaryDto {

    private String property;
    private String reservationId;
    private String identifier;
    private String thirdPartyIdentifier;
    private String status;
    private String firstName;
    private String lastName;
    private String email;
    private String sourceName;
    private String checkinDate;
    private String checkoutDate;
    private String nights;
    private BigDecimal grandTotal;
    private BigDecimal balanceDue;
    private BigDecimal paidValue;
    /** OTA guest-facing listed price (Hostelworld / channel). */
    private BigDecimal channelPriceListed;
    /** OTA net / channel_balance anchor used with listed price for HW EVL rate delta. */
    private BigDecimal channelBalance;
    /** Sum of Edinburgh Visitor Levy lines on the folio (tax breakdown). */
    private BigDecimal visitorLevyTotal;
    private String channelPaymentType;
    private String isHotelCollectBooking;
    private Integer adultsNumber;
    private Integer kidsNumber;
    private String bookingDateHotelTime;
    private String specialRequests;
    /** Last4 only — never full PAN. */
    private String creditCardLast4Digits;
    private String creditCardType;
    private List<Map<String, Object>> notes;
    private List<Map<String, Object>> rooms;

    public String getProperty() {
        return property;
    }

    public void setProperty( String property ) {
        this.property = property;
    }

    public String getReservationId() {
        return reservationId;
    }

    public void setReservationId( String reservationId ) {
        this.reservationId = reservationId;
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

    public String getSourceName() {
        return sourceName;
    }

    public void setSourceName( String sourceName ) {
        this.sourceName = sourceName;
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

    public String getNights() {
        return nights;
    }

    public void setNights( String nights ) {
        this.nights = nights;
    }

    public BigDecimal getGrandTotal() {
        return grandTotal;
    }

    public void setGrandTotal( BigDecimal grandTotal ) {
        this.grandTotal = grandTotal;
    }

    public BigDecimal getBalanceDue() {
        return balanceDue;
    }

    public void setBalanceDue( BigDecimal balanceDue ) {
        this.balanceDue = balanceDue;
    }

    public BigDecimal getPaidValue() {
        return paidValue;
    }

    public void setPaidValue( BigDecimal paidValue ) {
        this.paidValue = paidValue;
    }

    public BigDecimal getChannelPriceListed() {
        return channelPriceListed;
    }

    public void setChannelPriceListed( BigDecimal channelPriceListed ) {
        this.channelPriceListed = channelPriceListed;
    }

    public BigDecimal getChannelBalance() {
        return channelBalance;
    }

    public void setChannelBalance( BigDecimal channelBalance ) {
        this.channelBalance = channelBalance;
    }

    public BigDecimal getVisitorLevyTotal() {
        return visitorLevyTotal;
    }

    public void setVisitorLevyTotal( BigDecimal visitorLevyTotal ) {
        this.visitorLevyTotal = visitorLevyTotal;
    }

    public String getChannelPaymentType() {
        return channelPaymentType;
    }

    public void setChannelPaymentType( String channelPaymentType ) {
        this.channelPaymentType = channelPaymentType;
    }

    public String getIsHotelCollectBooking() {
        return isHotelCollectBooking;
    }

    public void setIsHotelCollectBooking( String isHotelCollectBooking ) {
        this.isHotelCollectBooking = isHotelCollectBooking;
    }

    public Integer getAdultsNumber() {
        return adultsNumber;
    }

    public void setAdultsNumber( Integer adultsNumber ) {
        this.adultsNumber = adultsNumber;
    }

    public Integer getKidsNumber() {
        return kidsNumber;
    }

    public void setKidsNumber( Integer kidsNumber ) {
        this.kidsNumber = kidsNumber;
    }

    public String getBookingDateHotelTime() {
        return bookingDateHotelTime;
    }

    public void setBookingDateHotelTime( String bookingDateHotelTime ) {
        this.bookingDateHotelTime = bookingDateHotelTime;
    }

    public String getSpecialRequests() {
        return specialRequests;
    }

    public void setSpecialRequests( String specialRequests ) {
        this.specialRequests = specialRequests;
    }

    public String getCreditCardLast4Digits() {
        return creditCardLast4Digits;
    }

    public void setCreditCardLast4Digits( String creditCardLast4Digits ) {
        this.creditCardLast4Digits = creditCardLast4Digits;
    }

    public String getCreditCardType() {
        return creditCardType;
    }

    public void setCreditCardType( String creditCardType ) {
        this.creditCardType = creditCardType;
    }

    public List<Map<String, Object>> getNotes() {
        return notes;
    }

    public void setNotes( List<Map<String, Object>> notes ) {
        this.notes = notes;
    }

    public List<Map<String, Object>> getRooms() {
        return rooms;
    }

    public void setRooms( List<Map<String, Object>> rooms ) {
        this.rooms = rooms;
    }
}
