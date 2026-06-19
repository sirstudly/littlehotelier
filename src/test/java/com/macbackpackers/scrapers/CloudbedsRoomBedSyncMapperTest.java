
package com.macbackpackers.scrapers;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
import com.macbackpackers.scrapers.matchers.LochsideRoomBedMatcher;
import com.macbackpackers.scrapers.matchers.RoomBedMatcher;

import org.junit.jupiter.api.Test;

public class CloudbedsRoomBedSyncMapperTest {

    private final RoomBedMatcher lochMatcher = new LochsideRoomBedMatcher();

    @Test
    public void deriveRoomTypeCode_singlePrivate() {
        assertEquals( "SGL", CloudbedsRoomBedSyncMapper.deriveRoomTypeCode( "Y", "MI", 1 ) );
    }

    @Test
    public void deriveRoomTypeCode_twinPrivate() {
        assertEquals( "TWN", CloudbedsRoomBedSyncMapper.deriveRoomTypeCode( "Y", "MI", 2 ) );
    }

    @Test
    public void deriveRoomTypeCode_quadPrivate() {
        assertEquals( "QUAD", CloudbedsRoomBedSyncMapper.deriveRoomTypeCode( "Y", "FE", 4 ) );
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
}