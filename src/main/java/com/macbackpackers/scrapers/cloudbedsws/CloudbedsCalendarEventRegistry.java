
package com.macbackpackers.scrapers.cloudbedsws;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

/**
 * In-memory index of Cloudbeds calendar {@code event id} → {@code booking_id} mappings built from
 * {@code on_migrate} snapshots and incremental WebSocket updates.
 * <p>
 * {@code delete.Events} and {@code room_free} removals only carry opaque calendar event ids — they do
 * not embed a reliable booking id (newly assigned tiles often use a unix-timestamp prefix rather
 * than the reservation id). This registry resolves deletes by looking up ids seen earlier on the
 * wire.
 * <p>
 * {@link CloudbedsWebSocketService} calls {@link #onSnapshot}, then {@link #beginUpdate} /
 * {@link #commitUpdate} around listener fan-out so removals are resolved before logging or other
 * reactions run.
 */
@Component
public class CloudbedsCalendarEventRegistry {

    /** How {@link #resolveRemovedEventBookingId} obtained a booking id. */
    public enum BookingIdSource {
        /** Previously indexed from snapshot or incremental {@code Events}. */
        CACHE,
        /** Best-effort {@link CloudbedsEventIdParser} prefix parse (may be wrong). */
        PARSED_PREFIX,
        /** No mapping available. */
        UNKNOWN
    }

    /** Result of resolving a removed calendar event id to a reservation id. */
    public static final class ResolvedBookingId {

        private final String bookingId;
        private final BookingIdSource source;

        private ResolvedBookingId( String bookingId, BookingIdSource source ) {
            this.bookingId = bookingId;
            this.source = source;
        }

        public static ResolvedBookingId cached( String bookingId ) {
            return new ResolvedBookingId( bookingId, BookingIdSource.CACHE );
        }

        public static ResolvedBookingId parsedPrefix( String bookingId ) {
            return new ResolvedBookingId( bookingId, BookingIdSource.PARSED_PREFIX );
        }

        public static ResolvedBookingId unknown() {
            return new ResolvedBookingId( null, BookingIdSource.UNKNOWN );
        }

        public String getBookingId() {
            return bookingId;
        }

        public BookingIdSource getSource() {
            return source;
        }

        public boolean isKnown() {
            return StringUtils.isNotBlank( bookingId );
        }
    }

    private final Map<String, PropertyIndex> indexesByProperty = new ConcurrentHashMap<>();

    private final ThreadLocal<Map<String, String>> resolvedRemovals = new ThreadLocal<>();

    /**
     * Rebuilds the index for a property from the {@code on_migrate} snapshot.
     */
    public void onSnapshot( String propertyId, List<CloudbedsCalendarEvent> events ) {
        PropertyIndex index = indexFor( propertyId );
        index.clear();
        if ( events != null ) {
            for ( CloudbedsCalendarEvent event : events ) {
                index.index( event );
            }
        }
    }

    /**
     * Captures booking ids for calendar event ids removed in this update, before listeners run.
     */
    public void beginUpdate( String propertyId, CloudbedsCalendarUpdate update ) {
        Map<String, String> resolved = new LinkedHashMap<>();
        if ( update != null ) {
            PropertyIndex index = indexesByProperty.get( propertyId );
            if ( index != null ) {
                for ( String eventId : update.getAllRemovedCalendarEventIds() ) {
                    String bookingId = index.getBookingIdForCalendarEvent( eventId );
                    if ( StringUtils.isNotBlank( bookingId ) ) {
                        resolved.put( eventId, bookingId );
                    }
                }
            }
        }
        resolvedRemovals.set( resolved );
    }

    /**
     * Applies incremental index changes after listeners have processed the update.
     */
    public void commitUpdate( String propertyId, CloudbedsCalendarUpdate update ) {
        try {
            if ( update == null ) {
                return;
            }
            PropertyIndex index = indexFor( propertyId );
            for ( CloudbedsCalendarEvent event : update.getAllReservationEvents() ) {
                index.index( event );
            }
            for ( String eventId : update.getAllRemovedCalendarEventIds() ) {
                index.removeCalendarEvent( eventId );
            }
            List<String> removedUnassigned = update.getDeleteSection().get( "NonAssignedReservations" );
            if ( removedUnassigned != null ) {
                for ( String bookingRoomsId : removedUnassigned ) {
                    index.removeBookingRoomsId( bookingRoomsId );
                }
            }
        }
        finally {
            resolvedRemovals.remove();
        }
    }

    /**
     * Resolves a removed calendar {@code event id} to a booking id for the current update cycle.
     */
    public ResolvedBookingId resolveRemovedEventBookingId( String propertyId, String eventId ) {
        if ( StringUtils.isBlank( eventId ) ) {
            return ResolvedBookingId.unknown();
        }
        Map<String, String> resolved = resolvedRemovals.get();
        if ( resolved != null ) {
            String bookingId = resolved.get( eventId.trim() );
            if ( StringUtils.isNotBlank( bookingId ) ) {
                return ResolvedBookingId.cached( bookingId );
            }
        }
        PropertyIndex index = indexesByProperty.get( propertyId );
        if ( index != null ) {
            String bookingId = index.getBookingIdForCalendarEvent( eventId.trim() );
            if ( StringUtils.isNotBlank( bookingId ) ) {
                return ResolvedBookingId.cached( bookingId );
            }
        }
        String parsed = CloudbedsEventIdParser.parseBookingIdFromEventId( eventId );
        if ( StringUtils.isNotBlank( parsed ) ) {
            return ResolvedBookingId.parsedPrefix( parsed );
        }
        return ResolvedBookingId.unknown();
    }

    /** Visible for tests. */
    int calendarEventCount( String propertyId ) {
        PropertyIndex index = indexesByProperty.get( propertyId );
        return index == null ? 0 : index.calendarEventCount();
    }

    private PropertyIndex indexFor( String propertyId ) {
        return indexesByProperty.computeIfAbsent( propertyId, id -> new PropertyIndex() );
    }

    private static final class PropertyIndex {

        private final Map<String, String> calendarEventIdToBookingId = new HashMap<>();
        private final Map<String, String> bookingRoomsIdToBookingId = new HashMap<>();

        void clear() {
            calendarEventIdToBookingId.clear();
            bookingRoomsIdToBookingId.clear();
        }

        void index( CloudbedsCalendarEvent event ) {
            if ( event == null ) {
                return;
            }
            String bookingId = event.getBookingId();
            if ( StringUtils.isBlank( bookingId ) || "0".equals( bookingId.trim() ) ) {
                return;
            }
            bookingId = bookingId.trim();

            String rowId = StringUtils.trimToNull( event.getId() );
            if ( rowId != null ) {
                if ( event.isUnassignedReservation() ) {
                    bookingRoomsIdToBookingId.put( rowId, bookingId );
                }
                else {
                    calendarEventIdToBookingId.put( rowId, bookingId );
                }
            }

            String bookingRoomsId = StringUtils.trimToNull( event.getBookingRoomsId() );
            if ( bookingRoomsId != null ) {
                bookingRoomsIdToBookingId.put( bookingRoomsId, bookingId );
            }
        }

        String getBookingIdForCalendarEvent( String eventId ) {
            return calendarEventIdToBookingId.get( eventId );
        }

        void removeCalendarEvent( String eventId ) {
            calendarEventIdToBookingId.remove( eventId );
        }

        void removeBookingRoomsId( String bookingRoomsId ) {
            bookingRoomsIdToBookingId.remove( bookingRoomsId );
        }

        int calendarEventCount() {
            return calendarEventIdToBookingId.size();
        }
    }
}
