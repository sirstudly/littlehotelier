
package com.macbackpackers.scrapers.cloudbedsws;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Default {@link CloudbedsEventListener} that simply logs incoming calendar events to a dedicated
 * logger ({@code cloudbeds.events}, routed to {@code cloudbeds-events.log} via logback).
 * <p>
 * This is the logging-only first step; it performs no DB writes or actions.
 */
@Component
public class LoggingCloudbedsEventListener implements CloudbedsEventListener {

    // dedicated logger name so logback can route it to its own file
    private static final Logger EVENTS_LOG = LoggerFactory.getLogger( "cloudbeds.events" );

    @Override
    public void onSnapshot( String propertyId, List<CloudbedsCalendarEvent> events ) {
        EVENTS_LOG.info( "[{}] SNAPSHOT received: {} events. Counts by type: {}",
                propertyId, events.size(), countByType( events ) );
        for ( CloudbedsCalendarEvent ev : events ) {
            EVENTS_LOG.info( "[{}] SNAPSHOT {}", propertyId, ev.toLogString() );
        }
    }

    @Override
    public void onChanges( String propertyId, List<CloudbedsCalendarEvent> events ) {
        if ( events.isEmpty() ) {
            return;
        }
        EVENTS_LOG.info( "[{}] CHANGES received: {} events", propertyId, events.size() );
        for ( CloudbedsCalendarEvent ev : events ) {
            EVENTS_LOG.info( "[{}] CHANGE {}", propertyId, ev.toLogString() );
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
