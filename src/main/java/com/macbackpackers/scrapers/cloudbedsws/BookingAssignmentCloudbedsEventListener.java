package com.macbackpackers.scrapers.cloudbedsws;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.macbackpackers.beans.BookingAssignment;
import com.macbackpackers.beans.JobStatus;
import com.macbackpackers.beans.RoomBed;
import com.macbackpackers.dao.WordPressDAO;
import com.macbackpackers.jobs.BookingAssignmentEnrichJob;

/**
 * Maintains {@code wp_lh_booking_assignment} from the full Cloudbeds calendar WebSocket horizon
 * (assigned + {@code NonAssignedReservations}). Does not apply the housekeeping ±2 day window.
 */
@Component
public class BookingAssignmentCloudbedsEventListener implements CloudbedsEventListener {

    private static final Logger LOGGER = LoggerFactory.getLogger( BookingAssignmentCloudbedsEventListener.class );

    @Autowired
    private WordPressDAO dao;

    @Autowired
    private BookingAssignmentMapper mapper;

    @Autowired
    private CloudbedsCalendarEventRegistry eventRegistry;

    /** calendar event id → assignment_key for delete / room_free resolution. */
    private final Map<String, String> eventIdToAssignmentKey = new ConcurrentHashMap<>();

    /** DB option to switch this listener off at runtime (any value other than "false" leaves it on). */
    static final String OPTION_ENABLED = "hbo_booking_assignment_ws_enabled";

    @Override
    public void onSnapshot( String propertyId, List<CloudbedsCalendarEvent> events ) {
        if ( false == isEnabled() || events == null ) {
            return;
        }
        Map<String, RoomBed> roomsById = indexRoomsById();
        List<BookingAssignment> desired = new ArrayList<>();
        eventIdToAssignmentKey.clear();
        for ( CloudbedsCalendarEvent event : events ) {
            BookingAssignment a = mapper.toAssignment( event, roomsById );
            if ( a == null ) {
                // canceled / incomplete: reconcile below closes any current not in the desired set
                continue;
            }
            desired.add( a );
            if ( StringUtils.isNotBlank( event.getId() ) ) {
                eventIdToAssignmentKey.put( event.getId(), a.getAssignmentKey() );
            }
        }
        LOGGER.info( "BookingAssignment WS snapshot: {} rows for property {}", desired.size(), propertyId );
        dao.reconcileBookingAssignmentCurrents( desired );
        // REST enrich deferred to AllocationScraperJob heal / incremental updates (avoid reconnect storm)
    }

    @Override
    public void onUpdate( String propertyId, CloudbedsCalendarUpdate update ) {
        if ( false == isEnabled() || update == null ) {
            return;
        }
        Map<String, RoomBed> roomsById = indexRoomsById();
        boolean needEnrich = false;

        for ( CloudbedsCalendarEvent event : update.getAllReservationEvents() ) {
            BookingAssignment a = mapper.toAssignment( event, roomsById );
            if ( a == null ) {
                if ( StringUtils.isNotBlank( event.getId() ) ) {
                    String key = eventIdToAssignmentKey.remove( event.getId() );
                    if ( key != null ) {
                        dao.closeBookingAssignment( key );
                    }
                    else {
                        dao.closeBookingAssignmentByCalendarEventId( event.getId() );
                    }
                }
                continue;
            }
            boolean wrote = dao.upsertBookingAssignment( a );
            if ( StringUtils.isNotBlank( event.getId() ) ) {
                eventIdToAssignmentKey.put( event.getId(), a.getAssignmentKey() );
            }
            if ( wrote && a.needsRestEnrich() ) {
                needEnrich = true;
            }
        }

        for ( String removedId : update.getAllRemovedCalendarEventIds() ) {
            closeByEventId( propertyId, removedId );
        }
        // NonAssigned deletes are not included in getAllRemovedCalendarEventIds
        if ( update.getDeleteSection() != null ) {
            List<String> nonAssignedDeletes = update.getDeleteSection().get( "NonAssignedReservations" );
            if ( nonAssignedDeletes != null ) {
                for ( String id : nonAssignedDeletes ) {
                    closeByEventId( propertyId, id );
                }
            }
        }

        if ( needEnrich ) {
            enqueueEnrichForMissingRest();
        }
    }

    private void closeByEventId( String propertyId, String eventId ) {
        if ( StringUtils.isBlank( eventId ) ) {
            return;
        }
        String key = eventIdToAssignmentKey.remove( eventId );
        if ( key != null ) {
            dao.closeBookingAssignment( key );
            return;
        }
        BookingAssignment current = dao.fetchCurrentBookingAssignmentByCalendarEventId( eventId );
        if ( current != null ) {
            dao.closeBookingAssignment( current.getAssignmentKey() );
            return;
        }
        // Registry may still resolve booking_id for logging / future cancel tables
        eventRegistry.resolveRemovedEventBookingId( propertyId, eventId );
    }

    private void enqueueEnrichForMissingRest() {
        List<Long> reservationIds = dao.fetchReservationIdsNeedingRestEnrich();
        if ( reservationIds == null || reservationIds.isEmpty() ) {
            return;
        }
        int queued = 0;
        for ( Long reservationId : reservationIds ) {
            if ( reservationId == null || queued >= 25 ) {
                break;
            }
            String id = String.valueOf( reservationId );
            if ( dao.hasBookingAssignmentEnrichJobForReservation( id ) ) {
                continue;
            }
            BookingAssignmentEnrichJob job = new BookingAssignmentEnrichJob();
            job.setStatus( JobStatus.submitted );
            job.setReservationId( id );
            dao.insertJob( job );
            queued++;
        }
        if ( queued > 0 ) {
            LOGGER.info( "BookingAssignment enrich: queued {} jobs ({} pending enrich)",
                    queued, reservationIds.size() );
        }
    }

    private boolean isEnabled() {
        return dao.isCloudbeds()
                && false == "false".equalsIgnoreCase( dao.getOptionNoCache( OPTION_ENABLED ) );
    }

    private Map<String, RoomBed> indexRoomsById() {
        Map<String, RoomBed> map = new ConcurrentHashMap<>();
        for ( RoomBed rb : dao.fetchAllRoomBeds().values() ) {
            map.put( rb.getId(), rb );
        }
        return map;
    }
}
