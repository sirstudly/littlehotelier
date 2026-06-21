
package com.macbackpackers.scrapers.cloudbedsws;

/**
 * Receives calendar events decoded from the Cloudbeds calendar WebSocket.
 * <p>
 * This is the extension point for reacting to bookings. Implementations log, enqueue jobs, or
 * trigger other actions based on snapshot and incremental update payloads.
 */
public interface CloudbedsEventListener {

    /**
     * Called once per connection with the full calendar snapshot (the {@code on_migrate} payload).
     *
     * @param propertyId the Cloudbeds property id this snapshot belongs to
     * @param events all current events (assigned grid rows and {@code NonAssignedReservations})
     */
    void onSnapshot( String propertyId, java.util.List<CloudbedsCalendarEvent> events );

    /**
     * Called for each incremental guarantee payload ({@code changes}, {@code room_assign},
     * {@code room_free}, etc.).
     *
     * @param propertyId the Cloudbeds property id this update belongs to
     * @param update decoded payload including assigned events, unassigned reservations, deletes, etc.
     */
    void onUpdate( String propertyId, CloudbedsCalendarUpdate update );
}
