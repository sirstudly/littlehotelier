
package com.macbackpackers.scrapers.cloudbedsws;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A single calendar/allocation event received over the Cloudbeds calendar WebSocket.
 * <p>
 * Events arrive from two places (see {@code CLOUDBEDS_WEBSOCKET_PROTOCOL.md}):
 * <ul>
 * <li>the {@code on_migrate} bulk snapshot (column-oriented {@code keys}/{@code rows}), and</li>
 * <li>incremental {@code changes}/{@code room_assign} payloads (array of objects).</li>
 * </ul>
 * Both are normalised into this object. The well-known columns are exposed as typed getters;
 * every field (including ones not promoted to a getter) is also available via {@link #getRaw()}.
 */
public class CloudbedsCalendarEvent {

    private final Map<String, String> raw;

    public CloudbedsCalendarEvent( Map<String, String> raw ) {
        this.raw = raw == null ? Collections.emptyMap() : new LinkedHashMap<>( raw );
    }

    /**
     * Returns the raw field map for this event (column name -&gt; value as string).
     *
     * @return non-null, unmodifiable map of all fields
     */
    public Map<String, String> getRaw() {
        return Collections.unmodifiableMap( raw );
    }

    private String get( String key ) {
        return raw.get( key );
    }

    public String getId() {
        return get( "id" );
    }

    public String getBookingId() {
        return get( "booking_id" );
    }

    /** e.g. booked, checked_in, checked_out, out_of_service, blocked_dates, courtesy_hold. */
    public String getType() {
        return get( "type" );
    }

    /** e.g. confirmed, checked_in, checked_out (may be null for blocked/out-of-service). */
    public String getStatus() {
        return get( "status" );
    }

    public String getStartDate() {
        return get( "start_date" );
    }

    public String getEndDate() {
        return get( "end_date" );
    }

    /** Room/bed id in the form {@code roomTypeId-bedId} (bed/unit {@code 0} when whole-room). */
    public String getRoomId() {
        return get( "room_id" );
    }

    /** Room type id (matches the {@code rate_ids} {@code 0-<id>} used by /connect/availability/get). */
    public String getResRtId() {
        return get( "res_rt_id" );
    }

    public String getFirstName() {
        return get( "first_name" );
    }

    public String getLastName() {
        return get( "last_name" );
    }

    public String getTotal() {
        return get( "total" );
    }

    public String getBalanceDue() {
        return get( "balance_due" );
    }

    public String getBookingSource() {
        return get( "booking_source" );
    }

    /**
     * Returns a concise single-line representation suitable for logging.
     *
     * @return human-readable summary of this event
     */
    public String toLogString() {
        StringBuilder sb = new StringBuilder();
        sb.append( "type=" ).append( getType() );
        sb.append( " status=" ).append( getStatus() );
        sb.append( " booking_id=" ).append( getBookingId() );
        sb.append( " guest=" ).append( trim( getFirstName() ) ).append( " " ).append( trim( getLastName() ) );
        sb.append( " dates=" ).append( getStartDate() ).append( "->" ).append( getEndDate() );
        sb.append( " room_id=" ).append( getRoomId() );
        sb.append( " rt=" ).append( getResRtId() );
        sb.append( " total=" ).append( getTotal() );
        sb.append( " balance_due=" ).append( getBalanceDue() );
        sb.append( " source=" ).append( getBookingSource() );
        return sb.toString();
    }

    private static String trim( String s ) {
        return s == null ? "" : s.trim();
    }

    @Override
    public String toString() {
        return "CloudbedsCalendarEvent{" + toLogString() + "}";
    }
}
