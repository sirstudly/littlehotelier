package com.macbackpackers.scrapers.datainsights;

import java.time.LocalDate;

/**
 * One bed on one night of the Data Insights classic Room Assignments report. A bed is listed once
 * per room type it belongs to, so the same {@link #getRoomName()} can appear under several room
 * types (e.g. a room moved from a defunct room type that is kept blocked).
 */
public class RoomAssignmentsReportRow {

    private LocalDate stayDate;
    private String roomType;
    private String roomName;
    private String bookingId;
    private String reservationNumber;
    private String guestFirstName;
    private String guestLastName;
    private int reservationsCount;
    private int blockedCount;
    private int outOfServiceCount;
    private int availableCount;
    private int courtesyHoldCount;

    public LocalDate getStayDate() {
        return stayDate;
    }

    public void setStayDate( LocalDate stayDate ) {
        this.stayDate = stayDate;
    }

    public String getRoomType() {
        return roomType;
    }

    public void setRoomType( String roomType ) {
        this.roomType = roomType;
    }

    /** Bed label, e.g. {@code 21-04- Stag} (trimmed, HTML-unescaped). */
    public String getRoomName() {
        return roomName;
    }

    public void setRoomName( String roomName ) {
        this.roomName = roomName;
    }

    /** Internal reservation id, or null if the bed has no reservation. */
    public String getBookingId() {
        return bookingId;
    }

    public void setBookingId( String bookingId ) {
        this.bookingId = bookingId;
    }

    public String getReservationNumber() {
        return reservationNumber;
    }

    public void setReservationNumber( String reservationNumber ) {
        this.reservationNumber = reservationNumber;
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

    public int getReservationsCount() {
        return reservationsCount;
    }

    public void setReservationsCount( int reservationsCount ) {
        this.reservationsCount = reservationsCount;
    }

    public int getBlockedCount() {
        return blockedCount;
    }

    public void setBlockedCount( int blockedCount ) {
        this.blockedCount = blockedCount;
    }

    public int getOutOfServiceCount() {
        return outOfServiceCount;
    }

    public void setOutOfServiceCount( int outOfServiceCount ) {
        this.outOfServiceCount = outOfServiceCount;
    }

    public int getAvailableCount() {
        return availableCount;
    }

    public void setAvailableCount( int availableCount ) {
        this.availableCount = availableCount;
    }

    public int getCourtesyHoldCount() {
        return courtesyHoldCount;
    }

    public void setCourtesyHoldCount( int courtesyHoldCount ) {
        this.courtesyHoldCount = courtesyHoldCount;
    }

    /** True if blocked dates or out of service. */
    public boolean isClosed() {
        return blockedCount > 0 || outOfServiceCount > 0;
    }

    /** True if a reservation is assigned to this bed. */
    public boolean isReserved() {
        return bookingId != null || reservationsCount > 0;
    }

    /** True if the bed is not free to book (reserved, closed or on courtesy hold). */
    public boolean isUnavailable() {
        return isReserved() || isClosed() || courtesyHoldCount > 0;
    }
}
