
package com.macbackpackers.beans.cloudbeds.responses;

import org.apache.commons.lang3.builder.ToStringBuilder;

/**
 * Essentially an "allocation" for a specific reservation. Each reservation may have one or more of
 * these.
 *
 */
public class BookingRoom {

    private String id;
    private String bookingId;
    private String roomId;
    private String roomIdentifier;
    private String startDate;
    private String endDate;
    private String roomTypeName;
    private String roomTypeNameShort;
    private String roomTypeId;
    private String roomNumber;
    private String detailedRates;
    private String rateId;
    private String guestId;

    public String getId() {
        return id;
    }

    public void setId( String id ) {
        this.id = id;
    }

    public String getBookingId() {
        return bookingId;
    }

    public void setBookingId( String bookingId ) {
        this.bookingId = bookingId;
    }

    public String getRoomId() {
        return roomId;
    }

    public void setRoomId( String roomId ) {
        this.roomId = roomId;
    }

    public String getRoomIdentifier() {
        return roomIdentifier;
    }

    public void setRoomIdentifier( String roomIdentifier ) {
        this.roomIdentifier = roomIdentifier;
    }

    public String getStartDate() {
        return startDate;
    }

    public void setStartDate( String startDate ) {
        this.startDate = startDate;
    }

    public String getEndDate() {
        return endDate;
    }

    public void setEndDate( String endDate ) {
        this.endDate = endDate;
    }

    public String getRoomTypeName() {
        return roomTypeName;
    }

    public void setRoomTypeName( String roomTypeName ) {
        this.roomTypeName = roomTypeName;
    }

    public String getRoomTypeNameShort() {
        return roomTypeNameShort;
    }

    public void setRoomTypeNameShort( String roomTypeNameShort ) {
        this.roomTypeNameShort = roomTypeNameShort;
    }

    public String getRoomTypeId() {
        return roomTypeId;
    }

    public void setRoomTypeId( String roomTypeId ) {
        this.roomTypeId = roomTypeId;
    }

    public String getRoomNumber() {
        return roomNumber;
    }

    public void setRoomNumber( String roomNumber ) {
        this.roomNumber = roomNumber;
    }

    public String getDetailedRates() {
        return detailedRates;
    }

    public void setDetailedRates( String detailedRates ) {
        this.detailedRates = detailedRates;
    }

    public String getRateId() {
        return rateId;
    }

    public void setRateId( String rateId ) {
        this.rateId = rateId;
    }

    public String getGuestId() {
        return guestId;
    }

    public void setGuestId( String guestId ) {
        this.guestId = guestId;
    }

    @Override
    public String toString() {
        return ToStringBuilder.reflectionToString( this );
    }
}
