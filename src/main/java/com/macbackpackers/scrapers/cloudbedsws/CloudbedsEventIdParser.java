
package com.macbackpackers.scrapers.cloudbedsws;

import org.apache.commons.lang3.StringUtils;

/**
 * Parses reservation identifiers embedded in Cloudbeds calendar {@code event id} strings.
 * <p>
 * Incremental payloads refer to grid rows by numeric {@code event id}, not {@code booking_id}.
 * Some calendar event ids begin with the 9-digit reservation id (e.g. {@code 178177230140204791}
 * → booking {@code 178177230}); others use a unix-timestamp prefix from when the tile was created
 * (e.g. {@code 17820844265948041} at assignment time, not {@code 178599456}). Prefer
 * {@link CloudbedsCalendarEventRegistry} for delete resolution; this parser is a best-effort
 * fallback only. */
public final class CloudbedsEventIdParser {

    private static final int BOOKING_ID_LENGTH = 9;

    private CloudbedsEventIdParser() {
        // utility class
    }

    /**
     * Returns the reservation id prefix embedded in a calendar event id, if it looks plausible.
     *
     * @param eventId calendar event id from {@code delete.Events}, {@code room_free}, etc.
     * @return parsed booking id, or {@code null} when the event id is blank or not parseable
     */
    public static String parseBookingIdFromEventId( String eventId ) {
        if ( StringUtils.isBlank( eventId ) || false == StringUtils.isNumeric( eventId.trim() ) ) {
            return null;
        }
        String trimmed = eventId.trim();
        if ( trimmed.length() <= BOOKING_ID_LENGTH ) {
            return null;
        }
        String candidate = trimmed.substring( 0, BOOKING_ID_LENGTH );
        if ( false == looksLikeReservationId( candidate ) ) {
            return null;
        }
        return candidate;
    }

    static boolean looksLikeReservationId( String bookingId ) {
        return bookingId != null
                && bookingId.length() == BOOKING_ID_LENGTH
                && bookingId.matches( "1[67]\\d{7}" );
    }
}
