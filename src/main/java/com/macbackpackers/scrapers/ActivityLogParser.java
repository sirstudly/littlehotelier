package com.macbackpackers.scrapers;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.text.StringEscapeUtils;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.macbackpackers.beans.cloudbeds.responses.ActivityLogEntry;

/**
 * Parses rows of the Cloudbeds {@code hotel/get_activity_log} response. Each row is
 * {@code [ "dd/MM/yyyy hh:mm a", "user<br />email", html ]} where the html looks like
 * {@code <div class="activityLog_modify"></div>Title<br /><div class="activityLogDetails">...</div>}.
 */
public final class ActivityLogParser {

    private static final DateTimeFormatter DD_MM_YYYY_HH_MM = DateTimeFormatter.ofPattern( "dd/MM/yyyy hh:mm a", Locale.ENGLISH );

    private static final Pattern ACTION = Pattern.compile( "class=\"activityLog_([A-Za-z_]+)\"" );
    private static final Pattern TITLE = Pattern.compile( "activityLog_[A-Za-z_]+\"\\s*>\\s*</div>(.*?)<br", Pattern.DOTALL );
    private static final Pattern IDENTIFIER_HREF = Pattern.compile( "#/reservations/r(\\d+)" );
    // eg. Guest assigned to room: href=".../reservations/186075919">#7256854468260</a>
    private static final Pattern NUMERIC_HREF = Pattern.compile( "#/reservations/(\\d+)\"[^>]*>\\s*#?(\\d+)?" );
    private static final Pattern GUEST_HREF = Pattern.compile( "#/reservation_record/(\\d+)" );
    private static final Pattern GUEST_EMAIL = Pattern.compile( "Guest email\\s+([^\\s<]+)" );
    private static final Pattern TAGS = Pattern.compile( "<[^>]+>" );

    private ActivityLogParser() {
        // static methods only
    }

    /**
     * Parses all rows of an {@code aaData} array.
     * 
     * @param rows aaData rows
     * @return non-null list of entries
     */
    public static List<ActivityLogEntry> parse( Iterable<JsonElement> rows ) {
        List<ActivityLogEntry> entries = new ArrayList<>();
        for ( JsonElement row : rows ) {
            entries.add( parseRow( row.getAsJsonArray() ) );
        }
        return entries;
    }

    /**
     * Parses a single row.
     * 
     * @param row a single aaData row
     * @return parsed entry
     * @throws IllegalArgumentException if the timestamp can't be parsed
     */
    public static ActivityLogEntry parseRow( JsonArray row ) {
        ActivityLogEntry ent = new ActivityLogEntry();
        String timestamp = row.get( 0 ).getAsString();
        try {
            ent.setCreatedDate( LocalDateTime.parse( timestamp, DD_MM_YYYY_HH_MM ) );
        }
        catch ( DateTimeParseException ex ) {
            throw new IllegalArgumentException( "Failed to parse activity log entry: " + timestamp, ex );
        }
        ent.setCreatedBy( row.get( 1 ).getAsString() );
        String html = row.get( 2 ).getAsString();
        ent.setContents( html );

        Matcher m = ACTION.matcher( html );
        if ( m.find() ) {
            ent.setAction( m.group( 1 ) );
        }
        m = TITLE.matcher( html );
        if ( m.find() ) {
            ent.setTitle( toText( m.group( 1 ) ) );
        }
        m = IDENTIFIER_HREF.matcher( html );
        while ( m.find() ) {
            ent.getReservationIdentifiers().add( m.group( 1 ) );
        }
        m = NUMERIC_HREF.matcher( html );
        while ( m.find() ) {
            ent.getReservationIds().add( m.group( 1 ) );
            if ( m.group( 2 ) != null ) {
                ent.getReservationIdentifiers().add( m.group( 2 ) );
            }
        }
        m = GUEST_HREF.matcher( html );
        while ( m.find() ) {
            ent.getGuestIds().add( m.group( 1 ) );
        }
        m = GUEST_EMAIL.matcher( html );
        if ( m.find() ) {
            ent.setGuestEmail( StringEscapeUtils.unescapeHtml4( m.group( 1 ) ) );
        }
        return ent;
    }

    /**
     * Strips tags, unescapes html entities and normalises whitespace.
     */
    static String toText( String html ) {
        return StringUtils.normalizeSpace( StringEscapeUtils.unescapeHtml4( TAGS.matcher( html ).replaceAll( " " ) ) );
    }
}
