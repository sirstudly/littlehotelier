package com.macbackpackers.services;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonParser;
import com.macbackpackers.scrapers.datainsights.CloudbedsDataInsightsClient;
import com.macbackpackers.scrapers.datainsights.RoomAssignmentsReportRow;

public class CloudbedsServiceStaffBedsTest {

    private static final LocalDate STAY_DATE = LocalDate.of( 2026, 9, 26 );

    @Test
    public void closedBedInDefunctRoomTypeIsNotStaffWhenOpenInAnotherRoomType() {
        List<RoomAssignmentsReportRow> rpt = Arrays.asList(
                row( "10 Bed Mixed Dormitory", "21-01- VW", null, 1, 0 ),
                row( "10 Bed Mixed Dormitory", "21-02- Horned", null, 1, 0 ),
                row( "10 Bed Mixed Dormitory", "21-03- Spotted", null, 1, 0 ),
                row( "10 Bed Female Dormitory", "21-01- VW", "183866189", 0, 0 ),
                row( "10 Bed Female Dormitory", "21-02- Horned", null, 0, 0 ),
                row( "10 Bed Female Dormitory", "21-03- Spotted", null, 0, 1 ),
                row( "xLT Female Dorm", "11-02- Tall & Short", null, 0, 1 ) );

        List<String> staff = CloudbedsService.extractStaffBedsFromRoomAssignmentReport( rpt, STAY_DATE );

        // 21-01 has a guest and 21-02 is empty in the active room type; 21-03 is closed in both
        assertThat( staff, containsInAnyOrder( "21-03- Spotted", "11-02- Tall & Short" ) );
        assertThat( CloudbedsService.extractStaffBedsFromRoomAssignmentReport( rpt, STAY_DATE.plusDays( 1 ) ),
                empty() );
    }

    @Test
    public void staffBedsFromReportFixture() throws Exception {
        List<RoomAssignmentsReportRow> rpt;
        try ( InputStreamReader reader = new InputStreamReader(
                getClass().getResourceAsStream( "/room_assignments_report_datainsights.json" ),
                StandardCharsets.UTF_8 ) ) {
            rpt = CloudbedsDataInsightsClient.parseRoomAssignments( JsonParser.parseReader( reader ).getAsJsonObject() );
        }

        List<String> staff = CloudbedsService.extractStaffBedsFromRoomAssignmentReport( rpt, STAY_DATE );

        assertThat( staff, hasSize( 28 ) );
        assertThat( staff, hasItems( "11-01- Night & Day", "12-08- Poopy", "14-02- Maggie Dickson's",
                "14-08- Deacon Brodie's", "14-13- World's End" ) );
        // blocked defunct MX listing of room 21 while the F listing has guests
        assertThat( staff, not( hasItem( startsWith( "21-" ) ) ) );
        // LT beds with a reservation
        assertThat( staff, not( hasItems( "11-08- Lust & Rage", "14-11- Sneaky Pete's" ) ) );
    }

    private static RoomAssignmentsReportRow row( String roomType, String roomName, String bookingId,
            int blocked, int outOfService ) {
        RoomAssignmentsReportRow r = new RoomAssignmentsReportRow();
        r.setStayDate( STAY_DATE );
        r.setRoomType( roomType );
        r.setRoomName( roomName );
        r.setBookingId( bookingId );
        r.setReservationsCount( bookingId == null ? 0 : 1 );
        r.setBlockedCount( blocked );
        r.setOutOfServiceCount( outOfService );
        r.setAvailableCount( bookingId == null && blocked == 0 && outOfService == 0 ? 1 : 0 );
        return r;
    }
}
