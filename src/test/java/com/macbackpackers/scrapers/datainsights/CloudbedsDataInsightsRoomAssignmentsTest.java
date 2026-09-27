package com.macbackpackers.scrapers.datainsights;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public class CloudbedsDataInsightsRoomAssignmentsTest {

    @Test
    public void parsesRoomAssignmentsFixture() throws Exception {
        List<RoomAssignmentsReportRow> rows = CloudbedsDataInsightsClient.parseRoomAssignments( load() );
        assertThat( rows, hasSize( 63 ) );

        RoomAssignmentsReportRow guest = find( rows, "10 Bed Female Dormitory", "21-01- VW" );
        assertThat( guest.getStayDate(), is( LocalDate.of( 2026, 9, 26 ) ) );
        assertThat( guest.getBookingId(), is( "183866189" ) );
        assertThat( guest.getGuestFirstName(), is( "Guest1" ) );
        assertThat( guest.isReserved(), is( true ) );
        assertThat( guest.isClosed(), is( false ) );

        RoomAssignmentsReportRow defunct = find( rows, "10 Bed Mixed Dormitory", "21-01- VW" );
        assertThat( defunct.getBookingId(), nullValue() );
        assertThat( defunct.getReservationNumber(), nullValue() );
        assertThat( defunct.getBlockedCount(), is( 1 ) );
        assertThat( defunct.isClosed(), is( true ) );

        // trailing space in the report is trimmed
        RoomAssignmentsReportRow blocked = find( rows, "xLT Male Dorm- Rm. 14", "14-08- Deacon Brodie's" );
        assertThat( blocked.isClosed(), is( true ) );

        // unassigned virtual beds report booking_id -1
        RoomAssignmentsReportRow paidBed = find( rows, "PAID BED", "PB((4)" );
        assertThat( paidBed.getBookingId(), nullValue() );
        assertThat( paidBed.isUnavailable(), is( false ) );
    }

    @Test
    public void parsesPicklist() {
        JsonObject picklist = JsonParser.parseString(
                "{ \"headers\": [\"room_type\"], \"index\": [ [\"10 Bed Mixed Dormitory\"], [\"14 Bed Mixed Dormitory \"] ] }" )
                .getAsJsonObject();
        assertThat( CloudbedsDataInsightsClient.parsePicklist( picklist ),
                contains( "10 Bed Mixed Dormitory", "14 Bed Mixed Dormitory " ) );
    }

    @Test
    public void buildsRoomAssignmentsBody() {
        JsonObject body = CloudbedsDataInsightsClient.buildRoomAssignmentsBody( "17363",
                LocalDate.of( 2026, 9, 26 ), LocalDate.of( 2026, 9, 27 ), Arrays.asList( "A", "B" ) );
        assertThat( body.toString(), is( "{\"property_ids\":[\"17363\"],\"filters\":{\"and\":["
                + "{\"cdf\":{\"column\":\"stay_date\"},\"operator\":\"greater_than_or_equal\",\"value\":\"2026-09-26\"},"
                + "{\"cdf\":{\"column\":\"stay_date\"},\"operator\":\"less_than_or_equal\",\"value\":\"2026-09-27\"},"
                + "{\"cdf\":{\"column\":\"room_type\"},\"operator\":\"list_contains\",\"value\":[\"A\",\"B\"]}]},"
                + "\"settings\":{\"details\":true,\"totals\":false,\"transpose\":false}}" ) );
    }

    static JsonObject load() throws Exception {
        try ( InputStreamReader reader = new InputStreamReader(
                CloudbedsDataInsightsRoomAssignmentsTest.class.getResourceAsStream(
                        "/room_assignments_report_datainsights.json" ),
                StandardCharsets.UTF_8 ) ) {
            return JsonParser.parseReader( reader ).getAsJsonObject();
        }
    }

    private static RoomAssignmentsReportRow find( List<RoomAssignmentsReportRow> rows, String roomType, String roomName ) {
        List<RoomAssignmentsReportRow> hits = rows.stream()
                .filter( r -> roomType.equals( r.getRoomType() ) && roomName.equals( r.getRoomName() ) )
                .collect( Collectors.toList() );
        assertThat( hits, hasSize( 1 ) );
        return hits.get( 0 );
    }
}
