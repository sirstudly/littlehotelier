package com.macbackpackers.scrapers;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.macbackpackers.beans.cloudbeds.responses.ActivityLogEntry;

public class ActivityLogParserTest {

    private static List<ActivityLogEntry> entries;

    @BeforeAll
    public static void loadFixture() throws IOException {
        try (Reader r = new InputStreamReader(
                ActivityLogParserTest.class.getClassLoader().getResourceAsStream( "activity_log_property.json" ),
                StandardCharsets.UTF_8 )) {
            JsonObject root = JsonParser.parseReader( r ).getAsJsonObject();
            entries = ActivityLogParser.parse( root.getAsJsonArray( "aaData" ) );
        }
    }

    private static ActivityLogEntry byTitle( String title ) {
        return entries.stream().filter( e -> title.equals( e.getTitle() ) ).findFirst()
                .orElseThrow( () -> new AssertionError( "No entry titled " + title ) );
    }

    @Test
    public void parsesEveryRow() {
        assertThat( entries, hasSize( 55 ) );
        assertThat( entries.stream().filter( e -> e.getTitle() == null ).count(), is( 0L ) );
    }

    @Test
    public void exactDuplicateRowsAreKept() {
        assertThat( entries.get( 0 ).getContents(), is( entries.get( 1 ).getContents() ) );
        assertThat( entries.get( 0 ).getCreatedDate(), is( entries.get( 1 ).getCreatedDate() ) );
    }

    @Test
    public void parsesTimestampUserActionAndIdentifier() {
        ActivityLogEntry e = byTitle( "Reservation status modified" );
        assertThat( e.getCreatedDate(), is( LocalDateTime.of( 2026, 10, 5, 22, 6 ) ) );
        assertThat( e.getCreatedBy(), is( "System" ) );
        assertThat( e.getAction(), is( "modify" ) );
        assertThat( e.getReservationIdentifiers(), contains( "8837578952568" ) );
        assertThat( e.getReservationIds(), empty() );
    }

    @Test
    public void trailingSpaceInTitleIsTrimmed() {
        ActivityLogEntry e = byTitle( "Reservation updated via the Cloudbeds Channel Manager" );
        assertThat( e.getReservationIdentifiers(), contains( "8837578952568" ) );
    }

    @Test
    public void malformedChannelManagerLinkStillYieldsIdentifier() {
        ActivityLogEntry e = byTitle( "Reservation received via the Cloudbeds Channel Manager" );
        assertThat( e.getReservationIdentifiers(), contains( "1535393630573" ) );
    }

    @Test
    public void guestAssignedToRoomHasNumericIdAndIdentifierFromLinkText() {
        ActivityLogEntry e = byTitle( "Guest assigned to room" );
        assertThat( e.getReservationIds(), contains( "188230061" ) );
        assertThat( e.getReservationIdentifiers(), contains( "1535393630573" ) );
        assertThat( e.getGuestIds(), contains( "190180480" ) );
    }

    @Test
    public void guestInfoModifiedHasGuestIdAndEmailOnly() {
        ActivityLogEntry e = byTitle( "Guest Info modified" );
        assertThat( e.getReservationIdentifiers(), empty() );
        assertThat( e.getReservationIds(), empty() );
        assertThat( e.getGuestIds(), contains( "190133010" ) );
        assertThat( e.getGuestEmail(), is( "guest.123456@guest.booking.com" ) );
    }

    @Test
    public void blockEventReferencesNoReservation() {
        ActivityLogEntry e = byTitle( "Out-of-service block created" );
        assertThat( e.getReservationIdentifiers(), empty() );
        assertThat( e.getReservationIds(), empty() );
        assertThat( e.getGuestIds(), empty() );
    }

    @Test
    public void bothTitleCasingsAreParsed() {
        assertThat( byTitle( "Reservation Reassigned to new room type" ).getReservationIdentifiers(),
                contains( "5698844246515" ) );
        assertThat( byTitle( "Reservation reassigned to new room type" ).getReservationIdentifiers(),
                contains( "1176176932510" ) );
    }

    @Test
    public void htmlEntitiesInTitleAreUnescaped() {
        assertThat( entries.stream().map( ActivityLogEntry::getTitle ).collect( Collectors.toList() ),
                hasItem( "Guest accepted the terms & conditions" ) );
    }

    @Test
    public void unparseableTimestampIsRejected() {
        JsonArray row = new JsonArray();
        row.add( "2026-10-05 22:06" );
        row.add( "System" );
        row.add( "" );
        assertThrows( IllegalArgumentException.class, () -> ActivityLogParser.parseRow( row ) );
    }
}
