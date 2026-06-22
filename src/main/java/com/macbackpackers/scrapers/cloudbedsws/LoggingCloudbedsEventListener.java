
package com.macbackpackers.scrapers.cloudbedsws;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Logs incoming calendar WebSocket data to {@code cloudbeds.events} ({@code cloudbeds-events.log}).
 * <p>
 * Snapshot lines are prefixed {@code SNAPSHOT}; incremental guarantee payloads use {@code UPDATE}
 * with the payload {@code action}, delete sections, per-row {@code EVENT} /
 * {@code NON_ASSIGNED} lines, and {@code CANCEL_CANDIDATE} when calendar event ids are removed.
 */
@Component
public class LoggingCloudbedsEventListener implements CloudbedsEventListener {

    private static final Logger EVENTS_LOG = LoggerFactory.getLogger( "cloudbeds.events" );

    @Autowired
    private CloudbedsCalendarEventRegistry eventRegistry;

    @Override
    public void onSnapshot( String propertyId, List<CloudbedsCalendarEvent> events ) {
        EVENTS_LOG.info( "[{}] SNAPSHOT received: {} events. Counts by type: {}",
                propertyId, events.size(), countByType( events ) );
        for ( CloudbedsCalendarEvent ev : events ) {
            String prefix = ev.isUnassignedReservation() ? "SNAPSHOT NON_ASSIGNED" : "SNAPSHOT";
            EVENTS_LOG.info( "[{}] {} {}", propertyId, prefix, ev.toLogString() );
        }
    }

    @Override
    public void onUpdate( String propertyId, CloudbedsCalendarUpdate update ) {
        if ( update == null ) {
            return;
        }
        EVENTS_LOG.info( "[{}] UPDATE {}", propertyId, update.toLogHeader() );
        if ( false == update.getDeleteSection().isEmpty() ) {
            for ( Map.Entry<String, List<String>> entry : update.getDeleteSection().entrySet() ) {
                if ( false == entry.getValue().isEmpty() ) {
                    EVENTS_LOG.info( "[{}] UPDATE DELETE {} ids={}", propertyId, entry.getKey(), entry.getValue() );
                    if ( "Events".equals( entry.getKey() ) ) {
                        logCancelCandidates( propertyId, update, entry.getValue(), "delete.Events" );
                    }
                }
            }
        }
        if ( false == update.getRemovedEventIds().isEmpty() ) {
            EVENTS_LOG.info( "[{}] UPDATE REMOVED event_ids={}", propertyId, update.getRemovedEventIds() );
            if ( "room_free".equals( update.getPayloadAction() ) && Boolean.FALSE.equals( update.getRoomFreeAdd() ) ) {
                logCancelCandidates( propertyId, update, update.getRemovedEventIds(), "room_free" );
            }
        }
        if ( false == update.getEvents().isEmpty() ) {
            EVENTS_LOG.info( "[{}] UPDATE events by type: {}", propertyId, update.countByType() );
        }
        for ( CloudbedsCalendarEvent ev : update.getEvents() ) {
            EVENTS_LOG.info( "[{}] UPDATE EVENT {}", propertyId, ev.toLogString() );
        }
        for ( CloudbedsCalendarEvent ev : update.getNonAssignedReservations() ) {
            EVENTS_LOG.info( "[{}] UPDATE NON_ASSIGNED {}", propertyId, ev.toLogString() );
        }
    }

    private void logCancelCandidates( String propertyId, CloudbedsCalendarUpdate update,
            List<String> eventIds, String via ) {
        Set<String> logged = new LinkedHashSet<>();
        for ( String eventId : eventIds ) {
            if ( false == logged.add( eventId ) ) {
                continue;
            }
            CloudbedsCalendarEventRegistry.ResolvedBookingId resolved =
                    eventRegistry.resolveRemovedEventBookingId( propertyId, eventId );
            EVENTS_LOG.info(
                    "[{}] UPDATE CANCEL_CANDIDATE event_id={} booking_id={} booking_id_source={} via={} action={} replacement_types={}",
                    propertyId,
                    eventId,
                    resolved.isKnown() ? resolved.getBookingId() : "?",
                    resolved.getSource(),
                    via,
                    update.getPayloadAction(),
                    describeReplacementTypes( update ) );
        }
    }

    private static String describeReplacementTypes( CloudbedsCalendarUpdate update ) {
        Map<String, Integer> types = update.countByType();
        if ( types.isEmpty() ) {
            return "{}";
        }
        return types.toString();
    }

    private static Map<String, Integer> countByType( List<CloudbedsCalendarEvent> events ) {
        Map<String, Integer> counts = new TreeMap<>();
        for ( CloudbedsCalendarEvent ev : events ) {
            String type = ev.getType() == null ? "(none)" : ev.getType();
            counts.merge( type, 1, Integer::sum );
        }
        return counts;
    }
}
