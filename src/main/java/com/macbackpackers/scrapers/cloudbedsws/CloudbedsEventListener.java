
package com.macbackpackers.scrapers.cloudbedsws;

import java.util.List;

/**
 * Receives calendar events decoded from the Cloudbeds calendar WebSocket.
 * <p>
 * This is the extension point for reacting to bookings. The first implementation
 * ({@link LoggingCloudbedsEventListener}) only logs; later tasks can add listeners that enqueue
 * jobs or trigger actions based on specific events.
 */
public interface CloudbedsEventListener {

    /**
     * Called once per connection with the full calendar snapshot (the {@code on_migrate} payload).
     *
     * @param propertyId the Cloudbeds property id this snapshot belongs to
     * @param events all current events (reservations, blocked dates, out-of-service, etc.)
     */
    void onSnapshot( String propertyId, List<CloudbedsCalendarEvent> events );

    /**
     * Called for each incremental update ({@code changes} / {@code room_assign} payload).
     *
     * @param propertyId the Cloudbeds property id these changes belong to
     * @param events the changed events
     */
    void onChanges( String propertyId, List<CloudbedsCalendarEvent> events );
}
