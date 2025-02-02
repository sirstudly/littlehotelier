package com.macbackpackers.beans;

public class RoomType {
    private String id;
    private String shortTitle;
    private String isPrivate;
    private String title;
    private String gender;
    private String numBeds;
    private int numRooms;
    private String roomCapacity;

    public String getId() {
        return id;
    }

    public void setId( String id ) {
        this.id = id;
    }

    public String getShortTitle() {
        return shortTitle;
    }

    public void setShortTitle( String shortTitle ) {
        this.shortTitle = shortTitle;
    }

    public String getIsPrivate() {
        return isPrivate;
    }

    public void setIsPrivate( String isPrivate ) {
        this.isPrivate = isPrivate;
    }

    public boolean isPrivate() {
        return "Y".equals( isPrivate );
    }

    public String getTitle() {
        return title;
    }

    public void setTitle( String title ) {
        this.title = title;
    }

    public String getGender() {
        return gender;
    }

    public void setGender( String gender ) {
        this.gender = gender;
    }

    public String getNumBeds() {
        return numBeds;
    }

    public void setNumBeds( String numBeds ) {
        this.numBeds = numBeds;
    }

    public int getNumRooms() {
        return numRooms;
    }

    public void setNumRooms( int numRooms ) {
        this.numRooms = numRooms;
    }

    public String getRoomCapacity() {
        return roomCapacity;
    }

    public void setRoomCapacity( String roomCapacity ) {
        this.roomCapacity = roomCapacity;
    }
}
