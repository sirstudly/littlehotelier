
package com.macbackpackers.scrapers;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.macbackpackers.beans.RoomBed;
import com.macbackpackers.scrapers.matchers.BedAssignment;
import com.macbackpackers.scrapers.matchers.RoomBedMatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Builds {@link RoomBed} rows from Cloudbeds {@code roomtypes/find} + {@code roomtypes/find_one} JSON.
 */
public final class CloudbedsRoomBedSyncMapper {

    private static final Logger LOGGER = LoggerFactory.getLogger( CloudbedsRoomBedSyncMapper.class );

    private CloudbedsRoomBedSyncMapper() {
    }

    /**
     * Maps WP / report {@code room_type} from find-row fields.
     *
     * @param isPrivate {@code is_private} ({@code Y}/{@code N})
     * @param gender    {@code gender} (e.g. {@code FE}, {@code MI})
     * @param capacity  numeric {@code room_capacity}
     */
    public static String deriveRoomTypeCode( String isPrivate, String gender, int capacity ) {
        boolean priv = "Y".equals( isPrivate );
        if ( priv ) {
            if ( capacity == 1 ) {
                return "SGL";
            }
            if ( capacity == 2 ) {
                return "TWN";
            }
            if ( capacity == 4 ) {
                return "QUAD";
            }
            return "MX";
        }
        if ( "FE".equals( gender ) ) {
            return "F";
        }
        return "MX";
    }

    static int parseRoomCapacity( JsonObject roomTypeFindRow ) {
        JsonElement cap = roomTypeFindRow.get( "num_beds" );
        if ( cap == null || cap.isJsonNull() ) {
            return 0;
        }
        try {
            return Integer.parseInt( cap.getAsString().trim() );
        }
        catch ( NumberFormatException ex ) {
            LOGGER.warn( "Unable to parse room capacity: " + cap.getAsString() );
            return 0;
        }
    }

    /**
     * Produces one {@link RoomBed} per {@code accommodation_names} entry.
     *
     * @param roomTypeFindRow element from {@code find} {@code data} array (summary fields)
     * @param findOneData     {@code data} object from {@code find_one}
     * @param matcher         property-specific label parser ({@code name} column)
     * @return non-null list (empty if no beds)
     */
    public static List<RoomBed> buildRoomBedsFromFindOne(
            JsonObject roomTypeFindRow, JsonObject findOneData, RoomBedMatcher matcher ) {

        if ( findOneData == null ) {
            return Collections.emptyList();
        }

        JsonElement namesEl = findOneData.get( "accommodation_names" );
        if ( namesEl == null || namesEl.isJsonNull() || !namesEl.isJsonArray() ) {
            return Collections.emptyList();
        }

        JsonArray names = namesEl.getAsJsonArray();
        String isPrivate = getStringMember( roomTypeFindRow, "is_private" );
        String gender = getStringMember( roomTypeFindRow, "gender" );
        int capacity = parseRoomCapacity( roomTypeFindRow );
        String roomTypeCode = deriveRoomTypeCode( isPrivate, gender, capacity );

        String roomTypeIdStr = getStringMember( roomTypeFindRow, "id" );
        final int roomTypeId;
        try {
            roomTypeId = Integer.parseInt( roomTypeIdStr );
        }
        catch ( NumberFormatException ex ) {
            LOGGER.warn( "Unable to parse room type id: " + roomTypeIdStr );
            return Collections.emptyList();
        }

        List<RoomBed> out = new ArrayList<>( names.size() );
        for ( JsonElement el : names ) {
            if ( !el.isJsonObject() ) {
                continue;
            }
            JsonObject acc = el.getAsJsonObject();
            String bedId = getStringMember( acc, "id" );
            String labelName = getStringMember( acc, "name" );
            if ( bedId.isEmpty() || labelName.isEmpty() ) {
                continue;
            }
            BedAssignment assign = matcher.parse( labelName );
            RoomBed rb = new RoomBed();
            rb.setId( bedId );
            rb.setRoom( assign.getRoom() );
            rb.setBedName( assign.getBedName() );
            rb.setCapacity( capacity );
            rb.setRoomTypeId( roomTypeId );
            rb.setRoomType( roomTypeCode );
            rb.setActive( "Y" );
            out.add( rb );
        }
        return out;
    }

    private static String getStringMember( JsonObject obj, String name ) {
        JsonElement e = obj.get( name );
        if ( e == null || e.isJsonNull() ) {
            return "";
        }
        return e.getAsString().trim();
    }

    /**
     * Expands {@code find} payload into beds by calling builder for each pre-fetched {@code find_one}.
     *
     * @param findResponse {@code fetchRoomTypesFind} root object
     * @param findOneByRoomTypeId room type id string → {@code data} object from {@code find_one}
     * @param matcher      label parser
     */
    public static List<RoomBed> buildAllRoomBeds(
            JsonObject findResponse, java.util.Map<String, JsonObject> findOneByRoomTypeId, RoomBedMatcher matcher ) {

        JsonElement dataEl = findResponse.get( "data" );
        if ( dataEl == null || !dataEl.isJsonArray() ) {
            return Collections.emptyList();
        }

        JsonArray rows = dataEl.getAsJsonArray();
        List<RoomBed> all = new ArrayList<>();
        for ( JsonElement rowEl : rows ) {
            if ( !rowEl.isJsonObject() ) {
                continue;
            }
            JsonObject rt = rowEl.getAsJsonObject();
            String id = getStringMember( rt, "id" );
            if ( id.isEmpty() ) {
                continue;
            }
            JsonObject findOneData = findOneByRoomTypeId.get( id );
            if ( findOneData != null ) {
                all.addAll( buildRoomBedsFromFindOne( rt, findOneData, matcher ) );
            }
        }
        return all;
    }
}
