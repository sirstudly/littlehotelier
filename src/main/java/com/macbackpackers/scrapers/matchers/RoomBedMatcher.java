
package com.macbackpackers.scrapers.matchers;

/**
 * Interface for splitting up the room/beds from the single label used in little hotelier.
 */
public interface RoomBedMatcher {

    /**
     * Returns the name of the bed within the room.
     * 
     * @return bed name (can be null for a private room)
     */
    public String getBedName();

    /**
     * Returns the name of the room.
     * 
     * @return room name (not-null)
     */
    public String getRoom();
}
