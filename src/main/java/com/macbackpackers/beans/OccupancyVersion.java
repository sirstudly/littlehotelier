package com.macbackpackers.beans;

import java.sql.Timestamp;
import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;

/**
 * SCD2 version row for a Cloudbeds bed/room assignment used by realtime housekeeping.
 * Current rows have {@code valid_to = null}.
 */
@Entity
@Table( name = "wp_lh_occupancy" )
public class OccupancyVersion {

    public static final String SOURCE_GUEST = "guest";
    public static final String SOURCE_CLOSURE = "closure";

    @Id
    @GeneratedValue( strategy = GenerationType.IDENTITY )
    @Column( name = "id", nullable = false )
    private long id;

    /** Stable key: booking_rooms_id, or {@code closure:<calendar_event_id>}. */
    @Column( name = "assignment_key", nullable = false )
    private String assignmentKey;

    @Column( name = "calendar_event_id" )
    private String calendarEventId;

    @Column( name = "booking_rooms_id" )
    private String bookingRoomsId;

    @Column( name = "reservation_id" )
    private Long reservationId;

    @Column( name = "room_id" )
    private String roomId;

    @Column( name = "room" )
    private String room;

    @Column( name = "bed_name" )
    private String bedName;

    @Column( name = "room_type_id" )
    private Integer roomTypeId;

    @Column( name = "guest_name" )
    private String guestName;

    @Column( name = "checkin_date", nullable = false )
    private java.sql.Date checkinDate;

    @Column( name = "checkout_date", nullable = false )
    private java.sql.Date checkoutDate;

    /** Cloudbeds bed/event status: booked, confirmed, checked_in, checked_out, out_of_service, blocked_dates. */
    @Column( name = "bed_status" )
    private String bedStatus;

    @Column( name = "in_house_yn" )
    private String inHouseYn;

    @Column( name = "source", nullable = false )
    private String source;

    @Column( name = "valid_from", nullable = false )
    private Timestamp validFrom;

    @Column( name = "valid_to" )
    private Timestamp validTo;

    public long getId() {
        return id;
    }

    public void setId( long id ) {
        this.id = id;
    }

    public String getAssignmentKey() {
        return assignmentKey;
    }

    public void setAssignmentKey( String assignmentKey ) {
        this.assignmentKey = assignmentKey;
    }

    public String getCalendarEventId() {
        return calendarEventId;
    }

    public void setCalendarEventId( String calendarEventId ) {
        this.calendarEventId = calendarEventId;
    }

    public String getBookingRoomsId() {
        return bookingRoomsId;
    }

    public void setBookingRoomsId( String bookingRoomsId ) {
        this.bookingRoomsId = bookingRoomsId;
    }

    public Long getReservationId() {
        return reservationId;
    }

    public void setReservationId( Long reservationId ) {
        this.reservationId = reservationId;
    }

    public String getRoomId() {
        return roomId;
    }

    public void setRoomId( String roomId ) {
        this.roomId = roomId;
    }

    public String getRoom() {
        return room;
    }

    public void setRoom( String room ) {
        this.room = room;
    }

    public String getBedName() {
        return bedName;
    }

    public void setBedName( String bedName ) {
        this.bedName = bedName;
    }

    public Integer getRoomTypeId() {
        return roomTypeId;
    }

    public void setRoomTypeId( Integer roomTypeId ) {
        this.roomTypeId = roomTypeId;
    }

    public String getGuestName() {
        return guestName;
    }

    public void setGuestName( String guestName ) {
        this.guestName = guestName;
    }

    public java.sql.Date getCheckinDate() {
        return checkinDate;
    }

    public void setCheckinDate( java.sql.Date checkinDate ) {
        this.checkinDate = checkinDate;
    }

    public void setCheckinDate( LocalDate checkinDate ) {
        this.checkinDate = checkinDate == null ? null : java.sql.Date.valueOf( checkinDate );
    }

    public java.sql.Date getCheckoutDate() {
        return checkoutDate;
    }

    public void setCheckoutDate( java.sql.Date checkoutDate ) {
        this.checkoutDate = checkoutDate;
    }

    public void setCheckoutDate( LocalDate checkoutDate ) {
        this.checkoutDate = checkoutDate == null ? null : java.sql.Date.valueOf( checkoutDate );
    }

    public String getBedStatus() {
        return bedStatus;
    }

    public void setBedStatus( String bedStatus ) {
        this.bedStatus = bedStatus;
    }

    public String getInHouseYn() {
        return inHouseYn;
    }

    public void setInHouseYn( String inHouseYn ) {
        this.inHouseYn = inHouseYn;
    }

    public void setInHouse( boolean inHouse ) {
        this.inHouseYn = inHouse ? "Y" : "N";
    }

    @Transient
    public boolean isInHouse() {
        return "Y".equalsIgnoreCase( inHouseYn );
    }

    public String getSource() {
        return source;
    }

    public void setSource( String source ) {
        this.source = source;
    }

    public Timestamp getValidFrom() {
        return validFrom;
    }

    public void setValidFrom( Timestamp validFrom ) {
        this.validFrom = validFrom;
    }

    public Timestamp getValidTo() {
        return validTo;
    }

    public void setValidTo( Timestamp validTo ) {
        this.validTo = validTo;
    }

    @Transient
    public boolean isCurrent() {
        return validTo == null;
    }

    @Transient
    public boolean isClosure() {
        return SOURCE_CLOSURE.equalsIgnoreCase( source );
    }

    @Transient
    public LocalDate getCheckinLocalDate() {
        return checkinDate == null ? null : checkinDate.toLocalDate();
    }

    @Transient
    public LocalDate getCheckoutLocalDate() {
        return checkoutDate == null ? null : checkoutDate.toLocalDate();
    }

    /**
     * Returns true when HK-relevant fields differ from {@code other} (triggers a new version).
     */
    public boolean differsForVersioning( OccupancyVersion other ) {
        if ( other == null ) {
            return true;
        }
        return false == eq( roomId, other.roomId )
                || false == eq( checkinDate, other.checkinDate )
                || false == eq( checkoutDate, other.checkoutDate )
                || false == eq( bedStatus, other.bedStatus )
                || false == eq( inHouseYn, other.inHouseYn )
                || false == eq( guestName, other.guestName )
                || false == eq( source, other.source )
                || false == eq( calendarEventId, other.calendarEventId );
    }

    private static boolean eq( Object a, Object b ) {
        if ( a == null ) {
            return b == null;
        }
        return a.equals( b );
    }
}
