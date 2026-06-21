
package com.macbackpackers.scrapers.cloudbedsws;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Logs incoming calendar WebSocket data to {@code cloudbeds.events} ({@code cloudbeds-events.log}).
 * <p>
 * Snapshot lines are prefixed {@code SNAPSHOT}; incremental guarantee payloads use {@code UPDATE}
 * with the payload {@code action}, delete sections, and per-row {@code EVENT} /
 * {@code NON_ASSIGNED} lines.
 */
@Component
public class LoggingCloudbedsEventListener implements CloudbedsEventListener {

    private static final Logger EVENTS_LOG = LoggerFactory.getLogger( "cloudbeds.events" );

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
                }
            }
        }
        if ( false == update.getRemovedEventIds().isEmpty() ) {
            EVENTS_LOG.info( "[{}] UPDATE REMOVED event_ids={}", propertyId, update.getRemovedEventIds() );
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

    private static Map<String, Integer> countByType( List<CloudbedsCalendarEvent> events ) {
        Map<String, Integer> counts = new TreeMap<>();
        for ( CloudbedsCalendarEvent ev : events ) {
            String type = ev.getType() == null ? "(none)" : ev.getType();
            counts.merge( type, 1, Integer::sum );
        }
        return counts;
    }
}
