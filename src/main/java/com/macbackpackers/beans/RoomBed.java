
package com.macbackpackers.beans;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Each row maps to a different room/bed.
 *
 */
@Entity
@Table( name = "wp_lh_rooms" )
public class RoomBed {

    @Id
    @Column( name = "id", nullable = false )
    private String id;

    @Column( name = "room", nullable = false )
    private String room;

    @Column( name = "bed_name" )
    private String bedName;

    @Column( name = "capacity", nullable = false )
    private int capacity;

    @Column( name = "room_type_id", nullable = false )
    private int roomTypeId;

    @Column( name = "room_type", nullable = false )
    private String roomType;

    @Column( name = "active_yn", nullable = false )
    private String active;

    public String getId() {
        return id;
    }

    public void setId( String id ) {
        this.id = id;
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

    public int getCapacity() {
        return capacity;
    }

    public void setCapacity( int capacity ) {
        this.capacity = capacity;
    }

    public int getRoomTypeId() {
        return roomTypeId;
    }

    public void setRoomTypeId( int roomTypeId ) {
        this.roomTypeId = roomTypeId;
    }

    public String getRoomType() {
        return roomType;
    }

    public void setRoomType( String roomType ) {
        this.roomType = roomType;
    }

    public String getActive() {
        return active;
    }

    public void setActive( String active ) {
        this.active = active;
    }

}
