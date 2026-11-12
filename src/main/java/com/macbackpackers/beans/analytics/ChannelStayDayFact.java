package com.macbackpackers.beans.analytics;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Channel Production day × source row for the stay-date analytics mart.
 */
public class ChannelStayDayFact {

    private String propertyCode;
    private LocalDate stayDate;
    private String bookingSource;
    private String sourceCategory;
    private BigDecimal roomRevenue = BigDecimal.ZERO;
    private int roomsSold;
    private BigDecimal adr;

    public String getPropertyCode() {
        return propertyCode;
    }

    public void setPropertyCode( String propertyCode ) {
        this.propertyCode = propertyCode;
    }

    public LocalDate getStayDate() {
        return stayDate;
    }

    public void setStayDate( LocalDate stayDate ) {
        this.stayDate = stayDate;
    }

    public String getBookingSource() {
        return bookingSource;
    }

    public void setBookingSource( String bookingSource ) {
        this.bookingSource = bookingSource;
    }

    public String getSourceCategory() {
        return sourceCategory;
    }

    public void setSourceCategory( String sourceCategory ) {
        this.sourceCategory = sourceCategory;
    }

    public BigDecimal getRoomRevenue() {
        return roomRevenue;
    }

    public void setRoomRevenue( BigDecimal roomRevenue ) {
        this.roomRevenue = roomRevenue == null ? BigDecimal.ZERO : roomRevenue;
    }

    public int getRoomsSold() {
        return roomsSold;
    }

    public void setRoomsSold( int roomsSold ) {
        this.roomsSold = roomsSold;
    }

    public BigDecimal getAdr() {
        return adr;
    }

    public void setAdr( BigDecimal adr ) {
        this.adr = adr;
    }
}
