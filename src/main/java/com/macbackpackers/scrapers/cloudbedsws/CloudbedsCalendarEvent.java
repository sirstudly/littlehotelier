
package com.macbackpackers.scrapers.cloudbedsws;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;

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

    /** {@code booking_rooms.id} when present; also the {@code id} of {@code NonAssignedReservations} rows. */
    public String getBookingRoomsId() {
        return get( "booking_rooms_id" );
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

    public String getBookingDate() {
        return get( "booking_date" );
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

    public String getThirdPartyIdentifier() {
        return get( "third_party_identifier" );
    }

    public String getDetailedRates() {
        return get( "detailed_rates" );
    }

    public String getIsHotelCollectPayment() {
        return get( "is_hotel_collect_payment" );
    }

    /** True for calendar reservation rows (excludes blocked dates, out-of-service, etc.). */
    public boolean isReservationBooking() {
        return "booked".equalsIgnoreCase( getType() )
                && StringUtils.isNotBlank( getBookingId() )
                && false == "0".equals( getBookingId() );
    }

    public boolean isCanceled() {
        return "canceled".equalsIgnoreCase( getStatus() );
    }

    public boolean isHotelCollectBooking() {
        return "1".equals( getIsHotelCollectPayment() );
    }

    /** True iff balance due is zero or negative (matches {@code Reservation.isPaid()}). */
    public boolean isPaid() {
        if ( StringUtils.isBlank( getBalanceDue() ) ) {
            return false;
        }
        try {
            return new BigDecimal( getBalanceDue().trim() ).compareTo( BigDecimal.ZERO ) <= 0;
        }
        catch ( NumberFormatException e ) {
            return false;
        }
    }

    /**
     * True if any nightly rate description indicates a non-refundable plan (matches
     * {@code Reservation.isNonRefundable()} which checks {@code usedRoomTypes}).
     */
    public boolean isNonRefundable() {
        String detailedRates = getDetailedRates();
        if ( StringUtils.isBlank( detailedRates ) ) {
            return false;
        }
        String lower = detailedRates.toLowerCase();
        return lower.contains( "non-refundable" ) || lower.contains( "\"nonref\"" );
    }

    public BigDecimal getBalanceDueAmount() {
        if ( StringUtils.isBlank( getBalanceDue() ) ) {
            return null;
        }
        try {
            return new BigDecimal( getBalanceDue().trim() ).setScale( 2, RoundingMode.HALF_UP );
        }
        catch ( NumberFormatException e ) {
            return null;
        }
    }

    /** True when this is a reservation row not yet assigned to a calendar room/bed. */
    public boolean isUnassignedReservation() {
        return isReservationBooking() && StringUtils.isBlank( getRoomId() );
    }

    public String getAssignmentType() {
        return get( "assignment_type" );
    }

    public String getAssignmentDate() {
        return get( "assignment_date" );
    }

    public String getLastChange() {
        return get( "last_change" );
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
        if ( StringUtils.isNotBlank( getId() ) ) {
            sb.append( " event_id=" ).append( getId() );
        }
        sb.append( " booking_id=" ).append( getBookingId() );
        sb.append( " guest=" ).append( trim( getFirstName() ) ).append( " " ).append( trim( getLastName() ) );
        sb.append( " dates=" ).append( getStartDate() ).append( "->" ).append( getEndDate() );
        sb.append( " room_id=" ).append( getRoomId() );
        sb.append( " rt=" ).append( getResRtId() );
        if ( StringUtils.isNotBlank( getAssignmentType() ) ) {
            sb.append( " assignment=" ).append( getAssignmentType() );
        }
        if ( StringUtils.isNotBlank( getAssignmentDate() ) ) {
            sb.append( " assigned=" ).append( getAssignmentDate() );
        }
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
