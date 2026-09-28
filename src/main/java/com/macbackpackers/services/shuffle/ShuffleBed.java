package com.macbackpackers.services.shuffle;

import com.macbackpackers.beans.RoomBed;

/**
 * A bookable bed (one row of {@code wp_lh_rooms}).
 *
 * @param roomType {@code wp_lh_rooms.room_type} (MX, F, QUAD, LT_MIXED, ...)
 * @param capacity beds per dorm room, or guests per private room; 0 when unknown
 */
public record ShuffleBed( String id, String room, String bedName, int roomTypeId, String roomType, int capacity ) {

    public ShuffleBed( String id, String room, String bedName, int roomTypeId ) {
        this( id, room, bedName, roomTypeId, "MX", 0 );
    }

    public String label() {
        return room + "-" + bedName;
    }

    /** Staff dorms, overflow and paid beds: never offered to guests by the main solve. */
    public boolean isNonGuest() {
        return RoomBed.NON_GUEST_ROOM_TYPES.contains( roomType );
    }
}
