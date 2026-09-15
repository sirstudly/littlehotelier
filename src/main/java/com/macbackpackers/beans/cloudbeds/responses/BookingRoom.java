
package com.macbackpackers.beans.cloudbeds.responses;

import org.apache.commons.lang3.builder.ToStringBuilder;

import com.google.gson.annotations.SerializedName;

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
    /** Cloudbeds per-room flag: {@code "1"} = in-house, {@code "-1"} = not. */
    @SerializedName( "in_house" )
    private String inHouse;
    @SerializedName( "guest_first_name" )
    private String guestFirstName;
    @SerializedName( "guest_last_name" )
    private String guestLastName;
    @SerializedName( "adults" )
    private Integer adults;
    @SerializedName( "kids" )
    private Integer kids;

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

    public String getInHouse() {
        return inHouse;
    }

    public void setInHouse( String inHouse ) {
        this.inHouse = inHouse;
    }

    /** {@code true} when Cloudbeds marks this room/bed as in-house ({@code in_house=1}). */
    public boolean isInHouse() {
        return "1".equals( inHouse );
    }

    public String getGuestFirstName() {
        return guestFirstName;
    }

    public void setGuestFirstName( String guestFirstName ) {
        this.guestFirstName = guestFirstName;
    }

    public String getGuestLastName() {
        return guestLastName;
    }

    public void setGuestLastName( String guestLastName ) {
        this.guestLastName = guestLastName;
    }

    public Integer getAdults() {
        return adults;
    }

    public void setAdults( Integer adults ) {
        this.adults = adults;
    }

    public Integer getKids() {
        return kids;
    }

    public void setKids( Integer kids ) {
        this.kids = kids;
    }

    public int getGuestCount() {
        return (adults == null ? 0 : adults) + (kids == null ? 0 : kids);
    }

    @Override
    public String toString() {
        return ToStringBuilder.reflectionToString( this );
    }
}
