package com.macbackpackers.ronbot.dto;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Sellable stock for one Cloudbeds room type across the requested nights.
 */
public class RoomTypeAvailabilityDto {

    private String roomTypeId;
    private String name;
    private Integer capacity;
    private Boolean isPrivate;
    /** Cloudbeds {@code sell} count per night (YYYY-MM-DD → count). */
    private Map<String, Integer> availableByDate = new LinkedHashMap<>();

    public String getRoomTypeId() {
        return roomTypeId;
    }

    public void setRoomTypeId( String roomTypeId ) {
        this.roomTypeId = roomTypeId;
    }

    public String getName() {
        return name;
    }

    public void setName( String name ) {
        this.name = name;
    }

    public Integer getCapacity() {
        return capacity;
    }

    public void setCapacity( Integer capacity ) {
        this.capacity = capacity;
    }

    public Boolean getIsPrivate() {
        return isPrivate;
    }

    public void setIsPrivate( Boolean isPrivate ) {
        this.isPrivate = isPrivate;
    }

    public Map<String, Integer> getAvailableByDate() {
        return availableByDate;
    }

    public void setAvailableByDate( Map<String, Integer> availableByDate ) {
        this.availableByDate = availableByDate;
    }
}
