package com.macbackpackers.scrapers.cloudbedsws;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.macbackpackers.beans.OccupancyVersion;
import com.macbackpackers.beans.RoomBed;
import com.macbackpackers.dao.WordPressDAO;
import com.macbackpackers.services.HousekeepingStatusService;
import com.macbackpackers.services.OccupancyReconcileService;

/**
 * Maintains {@code wp_lh_occupancy} from Cloudbeds calendar WebSocket events and refreshes
 * housekeeping bedsheet projections (debounced Mercure publish).
 */
@Component
public class HousekeepingCloudbedsEventListener implements CloudbedsEventListener {

    private static final Logger LOGGER = LoggerFactory.getLogger( HousekeepingCloudbedsEventListener.class );

    @Autowired
    private WordPressDAO dao;

    @Autowired
    private OccupancyReconcileService occupancyReconcileService;

    @Autowired
    private HousekeepingStatusService housekeepingStatusService;

    /** calendar event id → assignment_key for delete / room_free resolution. */
    private final Map<String, String> eventIdToAssignmentKey = new ConcurrentHashMap<>();

    private final ScheduledExecutorService debounceExecutor = Executors.newSingleThreadScheduledExecutor( r -> {
        Thread t = new Thread( r, "housekeeping-ws-debounce" );
        t.setDaemon( true );
        return t;
    } );

    private volatile ScheduledFuture<?> pendingRecompute;

    @Override
    public void onSnapshot( String propertyId, List<CloudbedsCalendarEvent> events ) {
        if ( false == dao.isCloudbeds() || events == null ) {
            return;
        }
        LocalDate today = LocalDate.now();
        LocalDate windowStart = today.minusDays( 1 );
        LocalDate windowEnd = today.plusDays( 2 );
        Map<String, RoomBed> roomsById = indexRoomsById();
        List<OccupancyVersion> desired = new ArrayList<>();
        eventIdToAssignmentKey.clear();
        for ( CloudbedsCalendarEvent event : events ) {
            OccupancyVersion o = toOccupancy( event, roomsById, windowStart, windowEnd );
            if ( o == null ) {
                continue;
            }
            desired.add( o );
            if ( StringUtils.isNotBlank( event.getId() ) ) {
                eventIdToAssignmentKey.put( event.getId(), o.getAssignmentKey() );
            }
        }
        LOGGER.info( "Housekeeping WS snapshot: {} occupancy rows for property {}", desired.size(), propertyId );
        occupancyReconcileService.reconcileFromSnapshot( desired, today );
    }

    @Override
    public void onUpdate( String propertyId, CloudbedsCalendarUpdate update ) {
        if ( false == dao.isCloudbeds() || update == null ) {
            return;
        }
        LocalDate today = LocalDate.now();
        LocalDate windowStart = today.minusDays( 1 );
        LocalDate windowEnd = today.plusDays( 2 );
        Map<String, RoomBed> roomsById = indexRoomsById();
        boolean changed = false;

        for ( CloudbedsCalendarEvent event : update.getAllReservationEvents() ) {
            OccupancyVersion o = toOccupancy( event, roomsById, windowStart, windowEnd );
            if ( o == null ) {
                // Outside window or incomplete — if we previously tracked this event, close it
                if ( StringUtils.isNotBlank( event.getId() ) ) {
                    String key = eventIdToAssignmentKey.remove( event.getId() );
                    if ( key != null ) {
                        dao.closeOccupancyVersion( key );
                        changed = true;
                    }
                }
                continue;
            }
            // Room move: previous current for same assignment may have different room_id — upsert handles versioning
            if ( dao.upsertOccupancyVersion( o ) ) {
                changed = true;
            }
            if ( StringUtils.isNotBlank( event.getId() ) ) {
                eventIdToAssignmentKey.put( event.getId(), o.getAssignmentKey() );
            }
        }

        for ( String removedId : update.getRemovedEventIds() ) {
            if ( closeByEventId( removedId ) ) {
                changed = true;
            }
        }
        if ( update.getDeleteSection() != null ) {
            for ( List<String> ids : update.getDeleteSection().values() ) {
                if ( ids == null ) {
                    continue;
                }
                for ( String id : ids ) {
                    if ( closeByEventId( id ) ) {
                        changed = true;
                    }
                }
            }
        }

        if ( changed ) {
            scheduleRecompute( today );
        }
    }

    private boolean closeByEventId( String eventId ) {
        if ( StringUtils.isBlank( eventId ) ) {
            return false;
        }
        String key = eventIdToAssignmentKey.remove( eventId );
        if ( key != null ) {
            dao.closeOccupancyVersion( key );
            return true;
        }
        OccupancyVersion current = dao.fetchCurrentOccupancyByCalendarEventId( eventId );
        if ( current != null ) {
            dao.closeOccupancyVersion( current.getAssignmentKey() );
            return true;
        }
        return false;
    }

    private void scheduleRecompute( LocalDate selectedDate ) {
        ScheduledFuture<?> prev = pendingRecompute;
        if ( prev != null ) {
            prev.cancel( false );
        }
        pendingRecompute = debounceExecutor.schedule( () -> {
            try {
                housekeepingStatusService.recomputeAndPublish( selectedDate );
            }
            catch ( Exception e ) {
                LOGGER.warn( "Housekeeping recompute after WS update failed: {}", e.toString() );
            }
        }, 400, TimeUnit.MILLISECONDS );
    }

    OccupancyVersion toOccupancy( CloudbedsCalendarEvent event, Map<String, RoomBed> roomsById,
            LocalDate windowStart, LocalDate windowEnd ) {
        if ( event == null ) {
            return null;
        }
        String type = StringUtils.defaultString( event.getType() ).toLowerCase();
        boolean closure = "out_of_service".equals( type ) || "blocked_dates".equals( type );
        boolean guest = "booked".equals( type ) || "checked_in".equals( type ) || "checked_out".equals( type );
        if ( false == closure && false == guest ) {
            return null;
        }
        if ( false == closure && ( StringUtils.isBlank( event.getBookingId() ) || "0".equals( event.getBookingId().trim() ) ) ) {
            return null;
        }
        LocalDate start;
        LocalDate end;
        try {
            start = LocalDate.parse( event.getStartDate().trim() );
            end = LocalDate.parse( event.getEndDate().trim() );
        }
        catch ( Exception e ) {
            return null;
        }
        // Keep assignments that overlap [windowStart, windowEnd)
        if ( end.isBefore( windowStart ) || false == start.isBefore( windowEnd ) ) {
            return null;
        }
        if ( StringUtils.isBlank( event.getRoomId() ) ) {
            return null; // unassigned — ignore for bed sheet report
        }

        OccupancyVersion o = new OccupancyVersion();
        if ( closure ) {
            o.setAssignmentKey( "closure:" + event.getId() );
            o.setSource( OccupancyVersion.SOURCE_CLOSURE );
            o.setReservationId( 0L );
            o.setGuestName( StringUtils.defaultIfBlank( event.getRaw().get( "reason" ), "Room closure" ) );
            o.setInHouse( false );
            o.setBedStatus( type );
        }
        else {
            String bookingRoomsId = event.getBookingRoomsId();
            o.setAssignmentKey( StringUtils.defaultIfBlank( bookingRoomsId,
                    "res:" + event.getBookingId() + ":" + event.getRoomId() ) );
            o.setBookingRoomsId( bookingRoomsId );
            o.setSource( OccupancyVersion.SOURCE_GUEST );
            try {
                o.setReservationId( Long.parseLong( event.getBookingId().trim() ) );
            }
            catch ( NumberFormatException e ) {
                o.setReservationId( null );
            }
            String name = StringUtils.trimToEmpty( event.getFirstName() ) + " "
                    + StringUtils.trimToEmpty( event.getLastName() );
            o.setGuestName( name.trim() );
            String status = StringUtils.defaultIfBlank( event.getStatus(), type );
            o.setBedStatus( status );
            boolean inHouse = "checked_in".equalsIgnoreCase( type ) || "checked_in".equalsIgnoreCase( status );
            o.setInHouse( inHouse );
        }
        o.setCalendarEventId( event.getId() );
        o.setRoomId( event.getRoomId() );
        o.setCheckinDate( start );
        o.setCheckoutDate( end );
        RoomBed rb = roomsById.get( event.getRoomId() );
        if ( rb != null ) {
            o.setRoom( rb.getRoom() );
            o.setBedName( rb.getBedName() );
            o.setRoomTypeId( rb.getRoomTypeId() );
        }
        o.setValidFrom( new Timestamp( System.currentTimeMillis() ) );
        return o;
    }

    private Map<String, RoomBed> indexRoomsById() {
        Map<String, RoomBed> map = new ConcurrentHashMap<>();
        for ( RoomBed rb : dao.fetchActiveHousekeepingRooms() ) {
            map.put( rb.getId(), rb );
        }
        // also include inactive for lookup during moves
        for ( RoomBed rb : dao.fetchAllRoomBeds().values() ) {
            map.putIfAbsent( rb.getId(), rb );
        }
        return map;
    }
}
