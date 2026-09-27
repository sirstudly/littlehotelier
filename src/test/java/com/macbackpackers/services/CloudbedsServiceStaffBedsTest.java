package com.macbackpackers.services;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonParser;

public class CloudbedsServiceStaffBedsTest {

    private static final LocalDate STAY_DATE = LocalDate.of( 2026, 9, 26 );

    @Test
    public void closedBedInDefunctRoomTypeIsNotStaffWhenOpenInAnotherRoomType() {
        String json = "{ \"rooms\": { \"2026-09-26\": {"
                + " \"10 Bed Mixed Dormitory\": { \"rooms\": {"
                + "   \"21-01- VW \": [ { \"type\": \"Out of Service\" } ],"
                + "   \"21-02- Horned \": [ { \"type\": \"Out of Service\" } ],"
                + "   \"21-03- Spotted \": [ { \"type\": \"Out of Service\" } ] } },"
                + " \"10 Bed Female Dormitory\": { \"rooms\": {"
                + "   \"21-01- VW \": [ { \"booking_id\": \"183866189\" } ],"
                + "   \"21-02- Horned \": [],"
                + "   \"21-03- Spotted \": [ { \"type\": \"Blocked Dates\" } ] } },"
                + " \"12 Bed Female Dormitory\": { \"rooms\": {"
                + "   \"11-02- Tall & Short \": [ { \"type\": \"Out of Service\" } ] } }"
                + " } } }";

        List<String> staff = CloudbedsService.extractStaffBedsFromRoomAssignmentReport(
                JsonParser.parseString( json ).getAsJsonObject(), STAY_DATE );

        // 21-01 has a guest and 21-02 is empty in the active room type; 21-03 is closed in both
        assertThat( staff, containsInAnyOrder( "21-03- Spotted", "11-02- Tall & Short" ) );
    }
}
