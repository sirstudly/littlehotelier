package com.macbackpackers.ronbot.dto;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Live Cloudbeds sellable availability for a property over an inclusive date range.
 */
public class AvailabilityDto {

    private String property;
    private String from;
    private String to;
    /** Sum of sell counts across all room types per night. */
    private Map<String, Integer> totalsByDate = new LinkedHashMap<>();
    private List<RoomTypeAvailabilityDto> roomTypes = new ArrayList<>();

    public String getProperty() {
        return property;
    }

    public void setProperty( String property ) {
        this.property = property;
    }

    public String getFrom() {
        return from;
    }

    public void setFrom( String from ) {
        this.from = from;
    }

    public String getTo() {
        return to;
    }

    public void setTo( String to ) {
        this.to = to;
    }

    public Map<String, Integer> getTotalsByDate() {
        return totalsByDate;
    }

    public void setTotalsByDate( Map<String, Integer> totalsByDate ) {
        this.totalsByDate = totalsByDate;
    }

    public List<RoomTypeAvailabilityDto> getRoomTypes() {
        return roomTypes;
    }

    public void setRoomTypes( List<RoomTypeAvailabilityDto> roomTypes ) {
        this.roomTypes = roomTypes;
    }
}
