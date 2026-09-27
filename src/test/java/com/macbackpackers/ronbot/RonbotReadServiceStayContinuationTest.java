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

import com.google.gson.JsonParser;
import com.macbackpackers.beans.cloudbeds.responses.Reservation;
import com.macbackpackers.ronbot.RonbotReadService.RoomAssignmentHit;
import com.macbackpackers.scrapers.datainsights.CloudbedsDataInsightsClient;
import com.macbackpackers.scrapers.datainsights.RoomAssignmentsReportRow;

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
        List<RoomAssignmentsReportRow> report = loadReport();

        Map<String, List<RoomAssignmentHit>> byBed =
                RonbotReadService.indexAssignmentsByBed( report, LocalDate.of( 2026, 9, 26 ) );

        // one hit per bed: the blocked defunct listing of room 21 has no booking
        assertEquals( 1, byBed.get( "21-04- Stag" ).size() );
        assertEquals( "183380606", byBed.get( "21-04- Stag" ).get( 0 ).bookingId );
        assertEquals( "Test", byBed.get( "21-04- Stag" ).get( 0 ).guestLastName );

        assertEquals( "183945221", byBed.get( "21-08- Let It Be" ).get( 0 ).bookingId );

        // closed and unassigned beds are omitted
        assertFalse( byBed.containsKey( "11-01- Night & Day" ) );
        assertFalse( byBed.containsKey( "PB((4)" ) );
    }

    @Test
    public void indexAssignmentsByBedEmptyWhenDateMissing() throws Exception {
        Map<String, List<RoomAssignmentHit>> byBed =
                RonbotReadService.indexAssignmentsByBed( loadReport(), LocalDate.of( 2026, 9, 27 ) );
        assertTrue( byBed.isEmpty() );
    }

    private List<RoomAssignmentsReportRow> loadReport() throws Exception {
        try ( InputStreamReader reader = new InputStreamReader(
                getClass().getResourceAsStream( "/room_assignments_report_datainsights.json" ),
                StandardCharsets.UTF_8 ) ) {
            return CloudbedsDataInsightsClient.parseRoomAssignments(
                    JsonParser.parseReader( reader ).getAsJsonObject() );
        }
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
