package com.macbackpackers.beans;

import java.sql.Timestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * A reservation locked to a bed from the Cloudbeds calendar (CloudbedsBedLock.js userscript).
 * Written by the backoffice REST API; active while {@code unlocked_date} is null.
 */
@Entity
@Table( name = "wp_lh_bed_lock" )
public class BedLock {

    @Id
    @GeneratedValue( strategy = GenerationType.IDENTITY )
    @Column( name = "id", nullable = false )
    private long id;

    @Column( name = "reservation_id", nullable = false )
    private long reservationId;

    /** Cloudbeds {@code roomTypeId-bedId} the reservation must stay in. */
    @Column( name = "room_id", nullable = false )
    private String roomId;

    @Column( name = "room" )
    private String room;

    @Column( name = "bed_name" )
    private String bedName;

    @Column( name = "calendar_event_id" )
    private String calendarEventId;

    @Column( name = "guest_name" )
    private String guestName;

    @Column( name = "checkin_date" )
    private java.sql.Date checkinDate;

    @Column( name = "checkout_date" )
    private java.sql.Date checkoutDate;

    @Column( name = "locked_by" )
    private String lockedBy;

    @Column( name = "locked_date", insertable = false, updatable = false )
    private Timestamp lockedDate;

    @Column( name = "unlocked_by" )
    private String unlockedBy;

    @Column( name = "unlocked_date" )
    private Timestamp unlockedDate;

    public long getId() {
        return id;
    }

    public void setId( long id ) {
        this.id = id;
    }

    public long getReservationId() {
        return reservationId;
    }

    public void setReservationId( long reservationId ) {
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

    public String getCalendarEventId() {
        return calendarEventId;
    }

    public void setCalendarEventId( String calendarEventId ) {
        this.calendarEventId = calendarEventId;
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

    public java.sql.Date getCheckoutDate() {
        return checkoutDate;
    }

    public void setCheckoutDate( java.sql.Date checkoutDate ) {
        this.checkoutDate = checkoutDate;
    }

    public String getLockedBy() {
        return lockedBy;
    }

    public void setLockedBy( String lockedBy ) {
        this.lockedBy = lockedBy;
    }

    public Timestamp getLockedDate() {
        return lockedDate;
    }

    public void setLockedDate( Timestamp lockedDate ) {
        this.lockedDate = lockedDate;
    }

    public String getUnlockedBy() {
        return unlockedBy;
    }

    public void setUnlockedBy( String unlockedBy ) {
        this.unlockedBy = unlockedBy;
    }

    public Timestamp getUnlockedDate() {
        return unlockedDate;
    }

    public void setUnlockedDate( Timestamp unlockedDate ) {
        this.unlockedDate = unlockedDate;
    }
}
