
package com.macbackpackers.scrapers.cloudbedsws;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class CloudbedsCalendarEventRegistryTest {

    private static final String PROPERTY_ID = "17959";

    private CloudbedsCalendarEventRegistry registry;

    @BeforeEach
    public void setUp() {
        registry = new CloudbedsCalendarEventRegistry();
    }

    @Test
    public void resolveRemovedEventBookingId_usesCachedMappingForTimestampBasedEventId() {
        CloudbedsCalendarEvent assigned = event(
                "17820844265948041",
                "178599456",
                "booked",
                "111754-5",
                null );
        registry.onSnapshot( PROPERTY_ID, Collections.singletonList( assigned ) );
        assertEquals( 1, registry.calendarEventCount( PROPERTY_ID ) );

        CloudbedsCalendarUpdate deleteUpdate = deleteEventsUpdate( "17820844265948041" );
        registry.beginUpdate( PROPERTY_ID, deleteUpdate );
        CloudbedsCalendarEventRegistry.ResolvedBookingId resolved =
                registry.resolveRemovedEventBookingId( PROPERTY_ID, "17820844265948041" );
        registry.commitUpdate( PROPERTY_ID, deleteUpdate );

        assertEquals( "178599456", resolved.getBookingId() );
        assertEquals( CloudbedsCalendarEventRegistry.BookingIdSource.CACHE, resolved.getSource() );
        assertEquals( "178208442", CloudbedsEventIdParser.parseBookingIdFromEventId( "17820844265948041" ) );
        assertEquals( 0, registry.calendarEventCount( PROPERTY_ID ) );
    }

    @Test
    public void resolveRemovedEventBookingId_fallsBackToPrefixParserWhenNotCached() {
        CloudbedsCalendarUpdate deleteUpdate = deleteEventsUpdate( "178177230140204791" );
        registry.beginUpdate( PROPERTY_ID, deleteUpdate );
        CloudbedsCalendarEventRegistry.ResolvedBookingId resolved =
                registry.resolveRemovedEventBookingId( PROPERTY_ID, "178177230140204791" );
        registry.commitUpdate( PROPERTY_ID, deleteUpdate );

        assertEquals( "178177230", resolved.getBookingId() );
        assertEquals( CloudbedsCalendarEventRegistry.BookingIdSource.PARSED_PREFIX, resolved.getSource() );
    }

    @Test
    public void resolveRemovedEventBookingId_unknownWhenNotCachedAndPrefixNotReservationLike() {
        CloudbedsCalendarUpdate deleteUpdate = deleteEventsUpdate( "12345678901234567" );
        registry.beginUpdate( PROPERTY_ID, deleteUpdate );
        CloudbedsCalendarEventRegistry.ResolvedBookingId resolved =
                registry.resolveRemovedEventBookingId( PROPERTY_ID, "12345678901234567" );
        registry.commitUpdate( PROPERTY_ID, deleteUpdate );

        assertFalse( resolved.isKnown() );
        assertEquals( CloudbedsCalendarEventRegistry.BookingIdSource.UNKNOWN, resolved.getSource() );
    }

    @Test
    public void indexUnassignedRowByBookingRoomsId() {
        CloudbedsCalendarEvent unassigned = event(
                "230860568",
                "178599456",
                "booked",
                null,
                null );
        registry.onSnapshot( PROPERTY_ID, Collections.singletonList( unassigned ) );

        CloudbedsCalendarUpdate autoAssign = new CloudbedsCalendarUpdate();
        autoAssign.setPayloadAction( "auto_assign" );
        autoAssign.setDeleteSection( Collections.singletonMap(
                "NonAssignedReservations", Collections.singletonList( "230860568" ) ) );
        registry.beginUpdate( PROPERTY_ID, autoAssign );
        registry.commitUpdate( PROPERTY_ID, autoAssign );

        assertEquals( 0, registry.calendarEventCount( PROPERTY_ID ) );
    }

    @Test
    public void incrementalUpdateIndexesNewEventsBeforeDeleteIsCommitted() {
        CloudbedsCalendarEvent existing = event(
                "17820844265948041",
                "178599456",
                "booked",
                "111754-5",
                "230860568" );
        registry.onSnapshot( PROPERTY_ID, Collections.singletonList( existing ) );

        CloudbedsCalendarUpdate deleteUpdate = deleteEventsUpdate( "17820844265948041" );
        registry.beginUpdate( PROPERTY_ID, deleteUpdate );
        assertTrue( registry.resolveRemovedEventBookingId( PROPERTY_ID, "17820844265948041" ).isKnown() );
        registry.commitUpdate( PROPERTY_ID, deleteUpdate );
    }

    private static CloudbedsCalendarEvent event( String id, String bookingId, String type,
            String roomId, String bookingRoomsId ) {
        Map<String, String> raw = new LinkedHashMap<>();
        raw.put( "id", id );
        raw.put( "booking_id", bookingId );
        raw.put( "type", type );
        raw.put( "status", "confirmed" );
        if ( roomId != null ) {
            raw.put( "room_id", roomId );
        }
        if ( bookingRoomsId != null ) {
            raw.put( "booking_rooms_id", bookingRoomsId );
        }
        return new CloudbedsCalendarEvent( raw );
    }

    private static CloudbedsCalendarUpdate deleteEventsUpdate( String eventId ) {
        CloudbedsCalendarUpdate update = new CloudbedsCalendarUpdate();
        update.setPayloadAction( "changes" );
        update.setPayloadTime( "1782084847" );
        update.setRemovedEventIds( Collections.singletonList( eventId ) );
        update.setDeleteSection( Collections.singletonMap( "Events", Collections.singletonList( eventId ) ) );
        return update;
    }
}
