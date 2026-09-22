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
     * @param capacity  guests per private room, or beds per dorm room
     * @param title     Cloudbeds room type {@code title} (used to distinguish double vs twin, etc.)
     */
    public static String deriveRoomTypeCode( String isPrivate, String gender, int capacity, String title ) {
        boolean priv = "Y".equals( isPrivate );
        if ( priv ) {
            String fromTitle = derivePrivateRoomTypeFromTitle( title );
            if ( fromTitle != null ) {
                return fromTitle;
            }
            if ( capacity == 1 ) {
                return "SGL";
            }
            if ( capacity == 2 ) {
                return "TWN";
            }
            if ( capacity == 3 ) {
                return "TRIPLE";
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

    /** Capacity-only overload when title is unavailable. */
    public static String deriveRoomTypeCode( String isPrivate, String gender, int capacity ) {
        return deriveRoomTypeCode( isPrivate, gender, capacity, null );
    }

    static String derivePrivateRoomTypeFromTitle( String title ) {
        if ( title == null || title.isEmpty() ) {
            return null;
        }
        String lower = title.toLowerCase();
        if ( lower.contains( "double" ) ) {
            return "DBL";
        }
        if ( lower.contains( "twin" ) ) {
            return "TWN";
        }
        if ( lower.contains( "triple" ) ) {
            return "TRIPLE";
        }
        if ( lower.contains( "quad" ) ) {
            return "QUAD";
        }
        if ( lower.contains( "single" ) ) {
            return "SGL";
        }
        return null;
    }

    /**
     * Guests-per-room for private rooms ({@code max_guests}); beds-per-room for dorms ({@code num_beds}).
     * Private rooms are sold as a single unit so Cloudbeds sets {@code num_beds=1} even when the room
     * sleeps 2–4 guests.
     */
    static int parseRoomCapacity( JsonObject roomTypeFindRow ) {
        boolean isPrivate = "Y".equals( getStringMember( roomTypeFindRow, "is_private" ) );
        String field = isPrivate ? "max_guests" : "num_beds";
        int capacity = parsePositiveIntMember( roomTypeFindRow, field );
        if ( capacity > 0 ) {
            return capacity;
        }
        // fallback if preferred field missing
        capacity = parsePositiveIntMember( roomTypeFindRow, isPrivate ? "num_beds" : "max_guests" );
        return Math.max( capacity, 0 );
    }

    private static int parsePositiveIntMember( JsonObject obj, String name ) {
        JsonElement el = obj.get( name );
        if ( el == null || el.isJsonNull() ) {
            return 0;
        }
        try {
            int value = el.isJsonPrimitive() && el.getAsJsonPrimitive().isNumber()
                    ? el.getAsInt()
                    : Integer.parseInt( el.getAsString().trim() );
            return value > 0 ? value : 0;
        }
        catch ( NumberFormatException | UnsupportedOperationException ex ) {
            LOGGER.warn( "Unable to parse {}: {}", name, el );
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

        // Prefer find_one fields when present (same schema); fall back to find summary.
        JsonObject capacitySource = findOneData.has( "max_guests" ) || findOneData.has( "num_beds" )
                ? findOneData
                : roomTypeFindRow;

        JsonArray names = namesEl.getAsJsonArray();
        String isPrivate = firstNonEmpty(
                getStringMember( capacitySource, "is_private" ),
                getStringMember( roomTypeFindRow, "is_private" ) );
        String gender = firstNonEmpty(
                getStringMember( capacitySource, "gender" ),
                getStringMember( roomTypeFindRow, "gender" ) );
        String title = firstNonEmpty(
                getStringMember( capacitySource, "title" ),
                getStringMember( roomTypeFindRow, "title" ) );
        int capacity = parseRoomCapacity( capacitySource );
        String roomTypeCode = deriveRoomTypeCode( isPrivate, gender, capacity, title );

        String roomTypeIdStr = firstNonEmpty(
                getStringMember( roomTypeFindRow, "id" ),
                getStringMember( capacitySource, "id" ) );
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

    private static String firstNonEmpty( String a, String b ) {
        return a != null && !a.isEmpty() ? a : ( b == null ? "" : b );
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
