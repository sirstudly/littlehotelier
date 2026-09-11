package com.macbackpackers.ronbot.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * Whether a guest is staying on past an as-of date — same reservation extended,
 * or a linked follow-on booking in the same beds.
 */
public class StayContinuationDto {

    private String asOf;
    private String checkoutDate;
    private boolean stayingOn;
    /** none | same_reservation | linked_reservation */
    private String kind;
    private BookingSummaryDto booking;
    private List<ContinuingRoomDto> continuingRooms = new ArrayList<>();
    private List<BookingSummaryDto> followingBookings = new ArrayList<>();

    public String getAsOf() {
        return asOf;
    }

    public void setAsOf( String asOf ) {
        this.asOf = asOf;
    }

    public String getCheckoutDate() {
        return checkoutDate;
    }

    public void setCheckoutDate( String checkoutDate ) {
        this.checkoutDate = checkoutDate;
    }

    public boolean isStayingOn() {
        return stayingOn;
    }

    public void setStayingOn( boolean stayingOn ) {
        this.stayingOn = stayingOn;
    }

    public String getKind() {
        return kind;
    }

    public void setKind( String kind ) {
        this.kind = kind;
    }

    public BookingSummaryDto getBooking() {
        return booking;
    }

    public void setBooking( BookingSummaryDto booking ) {
        this.booking = booking;
    }

    public List<ContinuingRoomDto> getContinuingRooms() {
        return continuingRooms;
    }

    public void setContinuingRooms( List<ContinuingRoomDto> continuingRooms ) {
        this.continuingRooms = continuingRooms;
    }

    public List<BookingSummaryDto> getFollowingBookings() {
        return followingBookings;
    }

    public void setFollowingBookings( List<BookingSummaryDto> followingBookings ) {
        this.followingBookings = followingBookings;
    }
}
