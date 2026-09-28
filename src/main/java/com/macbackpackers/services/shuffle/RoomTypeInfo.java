package com.macbackpackers.services.shuffle;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import com.macbackpackers.beans.RoomBed;

/**
 * What the fallback rules need to know about a room type, derived from its beds.
 *
 * @param code        {@code wp_lh_rooms.room_type}
 * @param capacity    beds per dorm room or guests per private room (0 if unknown)
 * @param bedsPerRoom most bed rows in one room of this type (private rooms have one row per room)
 */
public record RoomTypeInfo( int roomTypeId, String code, int capacity, int bedsPerRoom ) {

    public enum Gender {
        FEMALE, MALE, MIXED
    }

    private static final Set<String> DORM_CODES = Set.of( "MX", "F", "M" );

    static RoomTypeInfo of( int roomTypeId, Collection<ShuffleBed> beds ) {
        String code = null;
        int capacity = 0;
        Map<String, Integer> perRoom = new HashMap<>();
        for ( ShuffleBed b : beds ) {
            if ( b.roomTypeId() != roomTypeId ) {
                continue;
            }
            code = code == null ? b.roomType() : code;
            capacity = Math.max( capacity, b.capacity() );
            perRoom.merge( b.room(), 1, Integer::sum );
        }
        int bedsPerRoom = perRoom.values().stream().mapToInt( Integer::intValue ).max().orElse( 0 );
        return new RoomTypeInfo( roomTypeId, code == null ? "" : code, capacity, bedsPerRoom );
    }

    public boolean isStaff() {
        return RoomBed.STAFF_ROOM_TYPES.contains( code );
    }

    public boolean isNonGuest() {
        return RoomBed.NON_GUEST_ROOM_TYPES.contains( code );
    }

    /** Shared guest dorm (private rooms have a single bed row per room even when coded MX). */
    public boolean isGuestDorm() {
        return DORM_CODES.contains( code ) && bedsPerRoom > 1;
    }

    public boolean isQuad() {
        return "QUAD".equals( code );
    }

    public int dormSize() {
        return capacity > 0 ? capacity : bedsPerRoom;
    }

    public boolean isFourBedDorm() {
        return isGuestDorm() && dormSize() == 4;
    }

    /** Null for private rooms and anything without a gender. */
    public Gender gender() {
        switch ( code ) {
            case "F":
            case RoomBed.ROOM_TYPE_LT_FEMALE:
                return Gender.FEMALE;
            case "M":
            case RoomBed.ROOM_TYPE_LT_MALE:
                return Gender.MALE;
            case "MX":
            case RoomBed.ROOM_TYPE_LT_MIXED:
                return Gender.MIXED;
            default:
                return null;
        }
    }

    public String describe() {
        if ( isStaff() ) {
            return code + " staff dorm (" + roomTypeId + ")";
        }
        if ( isGuestDorm() ) {
            return dormSize() + "-bed " + code + " dorm (" + roomTypeId + ")";
        }
        return code + " (" + roomTypeId + ")";
    }
}
