package com.macbackpackers.scrapers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.macbackpackers.beans.RoomBed;
import com.macbackpackers.scrapers.matchers.CastleRockRoomBedMatcher;
import com.macbackpackers.scrapers.matchers.LochsideRoomBedMatcher;
import com.macbackpackers.scrapers.matchers.RoomBedMatcher;

import org.junit.jupiter.api.Test;

public class CloudbedsRoomBedSyncMapperTest {

    private final RoomBedMatcher lochMatcher = new LochsideRoomBedMatcher();
    private final RoomBedMatcher crhMatcher = new CastleRockRoomBedMatcher();

    @Test
    public void deriveRoomTypeCode_singlePrivate() {
        assertEquals( "SGL", CloudbedsRoomBedSyncMapper.deriveRoomTypeCode( "Y", "MI", 1 ) );
    }

    @Test
    public void deriveRoomTypeCode_twinPrivate() {
        assertEquals( "TWN", CloudbedsRoomBedSyncMapper.deriveRoomTypeCode( "Y", "MI", 2 ) );
    }

    @Test
    public void deriveRoomTypeCode_triplePrivate() {
        assertEquals( "TRIPLE", CloudbedsRoomBedSyncMapper.deriveRoomTypeCode( "Y", "MI", 3 ) );
    }

    @Test
    public void deriveRoomTypeCode_quadPrivate() {
        assertEquals( "QUAD", CloudbedsRoomBedSyncMapper.deriveRoomTypeCode( "Y", "FE", 4 ) );
    }

    @Test
    public void deriveRoomTypeCode_doubleFromTitle() {
        assertEquals( "DBL", CloudbedsRoomBedSyncMapper.deriveRoomTypeCode(
                "Y", "MA", 2, "Basic Double Room" ) );
    }

    @Test
    public void deriveRoomTypeCode_twinFromTitle() {
        assertEquals( "TWN", CloudbedsRoomBedSyncMapper.deriveRoomTypeCode(
                "Y", "MI", 2, "Budget Twin Room (Bunk Bed)" ) );
    }

    @Test
    public void deriveRoomTypeCode_femaleDorm() {
        assertEquals( "F", CloudbedsRoomBedSyncMapper.deriveRoomTypeCode( "N", "FE", 8 ) );
    }

    @Test
    public void deriveRoomTypeCode_mixedDorm() {
        assertEquals( "MX", CloudbedsRoomBedSyncMapper.deriveRoomTypeCode( "N", "MI", 8 ) );
    }

    @Test
    public void deriveRoomTypeCode_otherPrivateUsesMxFallback() {
        assertEquals( "MX", CloudbedsRoomBedSyncMapper.deriveRoomTypeCode( "Y", "MI", 8 ) );
    }

    @Test
    public void parseRoomCapacity_privateUsesMaxGuestsNotNumBeds() {
        JsonObject dbl = loadFindOneData( "/room_types_find_one_dbl_crh.json" );
        assertEquals( 2, CloudbedsRoomBedSyncMapper.parseRoomCapacity( dbl ) );

        JsonObject triple = loadFindOneData( "/room_types_find_one_triple_crh.json" );
        assertEquals( 3, CloudbedsRoomBedSyncMapper.parseRoomCapacity( triple ) );

        JsonObject quad = loadFindOneData( "/room_types_find_one_quad_crh.json" );
        assertEquals( 4, CloudbedsRoomBedSyncMapper.parseRoomCapacity( quad ) );
    }

    @Test
    public void parseRoomCapacity_dormUsesNumBeds() {
        JsonObject dorm = loadFindOneData( "/room_types_find_one.json" );
        assertEquals( 4, CloudbedsRoomBedSyncMapper.parseRoomCapacity( dorm ) );
    }

    @Test
    public void buildsRoomBedsFromFixtures_femaleRoomType397485() throws Exception {
        JsonObject findRoot;
        try ( InputStreamReader r = new InputStreamReader(
                Objects.requireNonNull( getClass().getResourceAsStream( "/room_types_find.json" ) ),
                StandardCharsets.UTF_8 ) ) {
            findRoot = JsonParser.parseReader( r ).getAsJsonObject();
        }
        JsonObject findOneRoot;
        try ( InputStreamReader r = new InputStreamReader(
                Objects.requireNonNull( getClass().getResourceAsStream( "/room_types_find_one.json" ) ),
                StandardCharsets.UTF_8 ) ) {
            findOneRoot = JsonParser.parseReader( r ).getAsJsonObject();
        }

        JsonObject row397485 = null;
        for ( JsonElement e : findRoot.getAsJsonArray( "data" ) ) {
            JsonObject o = e.getAsJsonObject();
            if ( "397485".equals( o.get( "id" ).getAsString() ) ) {
                row397485 = o;
                break;
            }
        }

        JsonObject findOneData = findOneRoot.getAsJsonObject( "data" );
        List<RoomBed> beds = CloudbedsRoomBedSyncMapper.buildRoomBedsFromFindOne( row397485, findOneData, lochMatcher );

        assertEquals( 8, beds.size() );
        RoomBed first = beds.get( 0 );
        assertEquals( "397485-0", first.getId() );
        assertEquals( 397485, first.getRoomTypeId() );
        assertEquals( 4, first.getCapacity() );
        assertEquals( "F", first.getRoomType() );
        assertEquals( "Y", first.getActive() );
        assertEquals( "12", first.getRoom() );
        assertEquals( "(FC) Fingal [TOP]", first.getBedName() );
    }

    @Test
    public void buildsPrivateRoomsFromCrhFixtures_dblTripleQuad() {
        assertPrivateCrhRoom( "/room_types_find_one_dbl_crh.json", 8, 2, "DBL", "53" );
        assertPrivateCrhRoom( "/room_types_find_one_triple_crh.json", 3, 3, "TRIPLE", "58" );
        assertPrivateCrhRoom( "/room_types_find_one_quad_crh.json", 3, 4, "QUAD", "56" );
    }

    private void assertPrivateCrhRoom( String findOneResource, int expectedBeds, int expectedCapacity,
            String expectedRoomType, String firstRoom ) {
        JsonObject findOneData = loadFindOneData( findOneResource );
        // find summary not needed when find_one carries the fields
        JsonObject findRow = new JsonObject();
        findRow.addProperty( "id", findOneData.get( "id" ).getAsString() );

        List<RoomBed> beds = CloudbedsRoomBedSyncMapper.buildRoomBedsFromFindOne(
                findRow, findOneData, crhMatcher );

        assertEquals( expectedBeds, beds.size() );
        RoomBed first = beds.get( 0 );
        assertEquals( expectedCapacity, first.getCapacity() );
        assertEquals( expectedRoomType, first.getRoomType() );
        assertEquals( firstRoom, first.getRoom() );
        assertNull( first.getBedName() );
        for ( RoomBed bed : beds ) {
            assertEquals( expectedCapacity, bed.getCapacity() );
            assertEquals( expectedRoomType, bed.getRoomType() );
        }
    }

    @Test
    public void buildAllRoomBeds_onlyIncludesTypesProvidedInMap() throws Exception {
        JsonObject findRoot;
        try ( InputStreamReader r = new InputStreamReader(
                Objects.requireNonNull( getClass().getResourceAsStream( "/room_types_find.json" ) ),
                StandardCharsets.UTF_8 ) ) {
            findRoot = JsonParser.parseReader( r ).getAsJsonObject();
        }
        JsonObject findOneRoot;
        try ( InputStreamReader r = new InputStreamReader(
                Objects.requireNonNull( getClass().getResourceAsStream( "/room_types_find_one.json" ) ),
                StandardCharsets.UTF_8 ) ) {
            findOneRoot = JsonParser.parseReader( r ).getAsJsonObject();
        }
        Map<String, JsonObject> byId = new HashMap<>();
        byId.put( "397485", findOneRoot.getAsJsonObject( "data" ) );

        List<RoomBed> out = CloudbedsRoomBedSyncMapper.buildAllRoomBeds( findRoot, byId, lochMatcher );

        assertEquals( 8, out.size() );
        assertEquals( "397485-7", out.get( 7 ).getId() );
    }

    private static JsonObject loadFindOneData( String resource ) {
        try ( InputStreamReader r = new InputStreamReader(
                Objects.requireNonNull( CloudbedsRoomBedSyncMapperTest.class.getResourceAsStream( resource ) ),
                StandardCharsets.UTF_8 ) ) {
            return JsonParser.parseReader( r ).getAsJsonObject().getAsJsonObject( "data" );
        }
        catch ( Exception e ) {
            throw new RuntimeException( "Failed to load " + resource, e );
        }
    }
}
