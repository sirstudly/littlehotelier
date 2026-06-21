
package com.macbackpackers.scrapers.cloudbedsws;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonParser;

public class CloudbedsEventDecoderTest {

    @Test
    public void decodePayload_roomFree_parsesRemovedEventIds() {
        String json = "{"
                + "\"action\":\"room_free\","
                + "\"data\":[\"177213922705803971\"],"
                + "\"add\":false,"
                + "\"time\":1772139241"
                + "}";
        CloudbedsCalendarUpdate update = CloudbedsEventDecoder.decodePayload(
                JsonParser.parseString( json ).getAsJsonObject() );

        assertEquals( "room_free", update.getPayloadAction() );
        assertEquals( "1772139241", update.getPayloadTime() );
        assertEquals( Boolean.FALSE, update.getRoomFreeAdd() );
        assertEquals( 1, update.getRemovedEventIds().size() );
        assertEquals( "177213922705803971", update.getRemovedEventIds().get( 0 ) );
        assertTrue( update.getEvents().isEmpty() );
    }

    @Test
    public void decodePayload_changes_parsesEventsAndExtras() {
        String json = "{"
                + "\"action\":\"changes\","
                + "\"time\":1781108699,"
                + "\"delete\":false,"
                + "\"rates\":[],"
                + "\"data\":{"
                + "  \"NonAssignedReservations\":false,"
                + "  \"Events\":[{\"type\":\"booked\",\"status\":\"confirmed\",\"booking_id\":\"166419128\","
                + "\"room_id\":\"112188-39\",\"assignment_type\":\"manual\"}]"
                + "}"
                + "}";
        CloudbedsCalendarUpdate update = CloudbedsEventDecoder.decodePayload(
                JsonParser.parseString( json ).getAsJsonObject() );

        assertEquals( "changes", update.getPayloadAction() );
        assertEquals( 1, update.getEvents().size() );
        assertEquals( "booked", update.getEvents().get( 0 ).getType() );
        assertEquals( "manual", update.getEvents().get( 0 ).getAssignmentType() );
        assertTrue( update.getNonAssignedReservations().isEmpty() );
        assertTrue( update.getExtraPayloadKeys().contains( "rates" ) );
        assertFalse( update.hasDeletes() );
    }

    @Test
    public void decodePayload_roomAssign_parsesDeleteSection() {
        String json = "{"
                + "\"action\":\"room_assign\","
                + "\"time\":1772139228,"
                + "\"delete\":{\"NonAssignedReservations\":[\"229722818\"]},"
                + "\"data\":{\"Events\":[{\"type\":\"booked\",\"booking_id\":\"177663763\",\"room_id\":\"112559-1\"}]}"
                + "}";
        CloudbedsCalendarUpdate update = CloudbedsEventDecoder.decodePayload(
                JsonParser.parseString( json ).getAsJsonObject() );

        assertEquals( "room_assign", update.getPayloadAction() );
        assertEquals( 1, update.getEvents().size() );
        assertTrue( update.getDeleteSection().containsKey( "NonAssignedReservations" ) );
        assertEquals( "229722818", update.getDeleteSection().get( "NonAssignedReservations" ).get( 0 ) );
    }
}
