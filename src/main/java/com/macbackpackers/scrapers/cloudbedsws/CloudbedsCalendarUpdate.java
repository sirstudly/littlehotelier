
package com.macbackpackers.scrapers.cloudbedsws;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * A decoded incremental update from a Cloudbeds calendar WebSocket guarantee payload
 * ({@code payload.action} such as {@code changes}, {@code room_assign}, {@code room_free}, etc.).
 */
public class CloudbedsCalendarUpdate {

    private String payloadAction;
    private String payloadTime;
    private String payloadId;
    private Boolean roomFreeAdd;

    private List<CloudbedsCalendarEvent> events = Collections.emptyList();
    private List<CloudbedsCalendarEvent> nonAssignedReservations = Collections.emptyList();
    private List<String> removedEventIds = Collections.emptyList();
    private Map<String, List<String>> deleteSection = Collections.emptyMap();
    private List<String> extraPayloadKeys = Collections.emptyList();

    public String getPayloadAction() {
        return payloadAction;
    }

    public void setPayloadAction( String payloadAction ) {
        this.payloadAction = payloadAction;
    }

    public String getPayloadTime() {
        return payloadTime;
    }

    public void setPayloadTime( String payloadTime ) {
        this.payloadTime = payloadTime;
    }

    public String getPayloadId() {
        return payloadId;
    }

    public void setPayloadId( String payloadId ) {
        this.payloadId = payloadId;
    }

    /** For {@code room_free} payloads: {@code true} = add/free, {@code false} = remove listed event ids. */
    public Boolean getRoomFreeAdd() {
        return roomFreeAdd;
    }

    public void setRoomFreeAdd( Boolean roomFreeAdd ) {
        this.roomFreeAdd = roomFreeAdd;
    }

    /** Assigned calendar grid rows ({@code data.Events}). */
    public List<CloudbedsCalendarEvent> getEvents() {
        return events;
    }

    public void setEvents( List<CloudbedsCalendarEvent> events ) {
        this.events = events == null ? Collections.emptyList() : events;
    }

    /** Bookings not yet placed on the calendar grid ({@code data.NonAssignedReservations}). */
    public List<CloudbedsCalendarEvent> getNonAssignedReservations() {
        return nonAssignedReservations;
    }

    public void setNonAssignedReservations( List<CloudbedsCalendarEvent> nonAssignedReservations ) {
        this.nonAssignedReservations = nonAssignedReservations == null ? Collections.emptyList() : nonAssignedReservations;
    }

    /** Event ids removed by {@code room_free} or listed under {@code delete}. */
    public List<String> getRemovedEventIds() {
        return removedEventIds;
    }

    public void setRemovedEventIds( List<String> removedEventIds ) {
        this.removedEventIds = removedEventIds == null ? Collections.emptyList() : removedEventIds;
    }

    /**
     * Parsed {@code delete} object (e.g. {@code Events}, {@code NonAssignedReservations} id lists).
     */
    public Map<String, List<String>> getDeleteSection() {
        return deleteSection;
    }

    public void setDeleteSection( Map<String, List<String>> deleteSection ) {
        this.deleteSection = deleteSection == null ? Collections.emptyMap() : deleteSection;
    }

    /** Top-level payload keys present besides the well-known ones (rates, roomRelations, etc.). */
    public List<String> getExtraPayloadKeys() {
        return extraPayloadKeys;
    }

    public void setExtraPayloadKeys( List<String> extraPayloadKeys ) {
        this.extraPayloadKeys = extraPayloadKeys == null ? Collections.emptyList() : extraPayloadKeys;
    }

    /** All reservation-like rows in this update (assigned + unassigned). */
    public List<CloudbedsCalendarEvent> getAllReservationEvents() {
        List<CloudbedsCalendarEvent> combined = new ArrayList<>( events.size() + nonAssignedReservations.size() );
        combined.addAll( events );
        combined.addAll( nonAssignedReservations );
        return combined;
    }

    public boolean hasDeletes() {
        return false == removedEventIds.isEmpty() || deleteSection.values().stream().anyMatch( list -> false == list.isEmpty() );
    }

    /**
     * Calendar grid event ids removed by {@code delete.Events} and {@code room_free} ({@code add:false}).
     */
    public List<String> getAllRemovedCalendarEventIds() {
        Map<String, String> seen = new LinkedHashMap<>();
        for ( String eventId : removedEventIds ) {
            if ( eventId != null ) {
                seen.put( eventId, eventId );
            }
        }
        List<String> deletedEvents = deleteSection.get( "Events" );
        if ( deletedEvents != null ) {
            for ( String eventId : deletedEvents ) {
                if ( eventId != null ) {
                    seen.put( eventId, eventId );
                }
            }
        }
        return new ArrayList<>( seen.keySet() );
    }

    public String toLogHeader() {
        StringBuilder sb = new StringBuilder();
        sb.append( "action=" ).append( payloadAction );
        if ( payloadTime != null ) {
            sb.append( " time=" ).append( payloadTime );
        }
        if ( payloadId != null ) {
            sb.append( " id=" ).append( payloadId );
        }
        if ( roomFreeAdd != null ) {
            sb.append( " room_free_add=" ).append( roomFreeAdd );
        }
        if ( false == extraPayloadKeys.isEmpty() ) {
            sb.append( " extras=" ).append( String.join( ",", extraPayloadKeys ) );
        }
        if ( hasDeletes() ) {
            sb.append( " delete=" ).append( formatDeleteSection() );
        }
        if ( false == removedEventIds.isEmpty() ) {
            sb.append( " removed_event_ids=" ).append( removedEventIds );
        }
        sb.append( " events=" ).append( events.size() );
        sb.append( " non_assigned=" ).append( nonAssignedReservations.size() );
        return sb.toString();
    }

    private String formatDeleteSection() {
        return deleteSection.entrySet().stream()
                .map( e -> e.getKey() + ":" + e.getValue() )
                .collect( Collectors.joining( ";" ) );
    }

    /** Counts calendar {@code type} values across assigned events and non-assigned reservations. */
    public Map<String, Integer> countByType() {
        Map<String, Integer> counts = new LinkedHashMap<>();
        Stream.concat( events.stream(), nonAssignedReservations.stream() )
                .forEach( ev -> {
                    String type = ev.getType() == null ? "(none)" : ev.getType();
                    counts.merge( type, 1, Integer::sum );
                } );
        return counts;
    }
}
