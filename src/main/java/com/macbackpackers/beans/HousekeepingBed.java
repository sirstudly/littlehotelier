package com.macbackpackers.beans;

import java.sql.Timestamp;
import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;

/**
 * Current projected housekeeping badge per bed (denormalised for fast PHP reads / Mercure).
 */
@Entity
@Table( name = "wp_lh_housekeeping_bed" )
public class HousekeepingBed {

    @Id
    @Column( name = "room_id", nullable = false )
    private String roomId;

    @Column( name = "room", nullable = false )
    private String room;

    @Column( name = "bed_name" )
    private String bedName;

    @Column( name = "room_type" )
    private String roomType;

    @Column( name = "capacity" )
    private int capacity;

    @Column( name = "guest_name" )
    private String guestName;

    @Column( name = "checkin_date" )
    private java.sql.Date checkinDate;

    @Column( name = "checkout_date" )
    private java.sql.Date checkoutDate;

    @Column( name = "data_href" )
    private String dataHref;

    @Column( name = "bedsheet", nullable = false )
    private String bedsheet;

    @Column( name = "selected_date", nullable = false )
    private java.sql.Date selectedDate;

    @Column( name = "updated_at", nullable = false )
    private Timestamp updatedAt;

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

    public String getRoomType() {
        return roomType;
    }

    public void setRoomType( String roomType ) {
        this.roomType = roomType;
    }

    public int getCapacity() {
        return capacity;
    }

    public void setCapacity( int capacity ) {
        this.capacity = capacity;
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

    public String getDataHref() {
        return dataHref;
    }

    public void setDataHref( String dataHref ) {
        this.dataHref = dataHref;
    }

    public String getBedsheet() {
        return bedsheet;
    }

    public void setBedsheet( String bedsheet ) {
        this.bedsheet = bedsheet;
    }

    public java.sql.Date getSelectedDate() {
        return selectedDate;
    }

    public void setSelectedDate( java.sql.Date selectedDate ) {
        this.selectedDate = selectedDate;
    }

    public void setSelectedDate( LocalDate selectedDate ) {
        this.selectedDate = selectedDate == null ? null : java.sql.Date.valueOf( selectedDate );
    }

    public Timestamp getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt( Timestamp updatedAt ) {
        this.updatedAt = updatedAt;
    }

    @Transient
    public boolean isChange() {
        return bedsheet != null && bedsheet.startsWith( "CHANGE" );
    }
}
