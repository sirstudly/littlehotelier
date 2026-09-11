package com.macbackpackers.ronbot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.macbackpackers.beans.cloudbeds.responses.Reservation;
import com.macbackpackers.ronbot.RonbotReadService.RoomAssignmentHit;

public class RonbotReadServiceStayContinuationTest {

    @Test
    public void normalizeBedLabelUnescapesAndTrims() {
        assertEquals( "21-04- Stag", RonbotReadService.normalizeBedLabel( " 21-04- Stag " ) );
        assertEquals( "HAW 03 Winnie & Pooh",
                RonbotReadService.normalizeBedLabel( "HAW 03 Winnie &amp; Pooh" ) );
        assertEquals( "", RonbotReadService.normalizeBedLabel( null ) );
    }

    @Test
    public void isSameGuestMatchesCustomerId() {
        Reservation a = guest( "100", "Jane", "Doe", "a@example.com" );
        Reservation b = guest( "100", "Other", "Name", "b@example.com" );
        assertTrue( RonbotReadService.isSameGuest( a, b ) );
    }

    @Test
    public void isSameGuestFallsBackToNameAndEmail() {
        Reservation a = guest( null, "Jane", "Doe", "jane@example.com" );
        Reservation b = guest( null, "Jane", "Doe", "jane@example.com" );
        assertTrue( RonbotReadService.isSameGuest( a, b ) );

        Reservation differentEmail = guest( null, "Jane", "Doe", "other@example.com" );
        assertFalse( RonbotReadService.isSameGuest( a, differentEmail ) );
    }

    @Test
    public void isSameGuestNameOnlyWhenEmailMissing() {
        Reservation a = guest( null, "Jane", "  Doe ", null );
        Reservation b = guest( null, "jane", "doe", null );
        assertTrue( RonbotReadService.isSameGuest( a, b ) );

        Reservation other = guest( null, "John", "Doe", null );
        assertFalse( RonbotReadService.isSameGuest( a, other ) );
    }

    @Test
    public void isSameGuestDifferentCustomerIds() {
        Reservation a = guest( "1", "Jane", "Doe", "jane@example.com" );
        Reservation b = guest( "2", "Jane", "Doe", "jane@example.com" );
        assertFalse( RonbotReadService.isSameGuest( a, b ) );
    }

    @Test
    public void indexAssignmentsByBedFromFixture() throws Exception {
        JsonObject report;
        try ( InputStreamReader reader = new InputStreamReader(
                getClass().getResourceAsStream( "/room_assignments_report.json" ),
                StandardCharsets.UTF_8 ) ) {
            report = JsonParser.parseReader( reader ).getAsJsonObject();
        }

        Map<String, List<RoomAssignmentHit>> byBed =
                RonbotReadService.indexAssignmentsByBed( report, LocalDate.of( 2021, 1, 6 ) );

        assertTrue( byBed.containsKey( "21-04- Stag" ) );
        assertEquals( "38059804", byBed.get( "21-04- Stag" ).get( 0 ).bookingId );
        assertEquals( "Mercedes", byBed.get( "21-04- Stag" ).get( 0 ).guestFirstName );

        assertTrue( byBed.containsKey( "21-08- Let It Be" ) );
        assertEquals( "38059811", byBed.get( "21-08- Let It Be" ).get( 0 ).bookingId );

        // Empty assignment arrays are omitted
        assertFalse( byBed.containsKey( "21-01- VW" ) );
    }

    @Test
    public void indexAssignmentsByBedEmptyWhenDateMissing() {
        JsonObject report = JsonParser.parseString(
                "{\"success\":true,\"rooms\":{\"2021-01-06\":{}}}" ).getAsJsonObject();
        Map<String, List<RoomAssignmentHit>> byBed =
                RonbotReadService.indexAssignmentsByBed( report, LocalDate.of( 2021, 1, 7 ) );
        assertTrue( byBed.isEmpty() );
    }

    @Test
    public void normalizeGuestNameCollapsesWhitespace() {
        assertEquals( "jane doe", RonbotReadService.normalizeGuestName( " Jane ", "  Doe " ) );
        assertEquals( "", RonbotReadService.normalizeGuestName( null, null ) );
    }

    private static Reservation guest( String customerId, String first, String last, String email ) {
        Reservation r = new Reservation();
        r.setCustomerId( customerId );
        r.setFirstName( first );
        r.setLastName( last );
        r.setEmail( email );
        return r;
    }
}
