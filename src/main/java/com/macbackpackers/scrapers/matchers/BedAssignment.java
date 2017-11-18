
package com.macbackpackers.scrapers.matchers;

/**
 * Bean for holding room/bed name from the single label used in little hotelier.
 */
public class BedAssignment {

    private String room;
    private String bedName;

    /**
     * Default constructor.
     * 
     * @param room name of room
     * @param bedName name of bed (optional)
     */
    public BedAssignment( String room, String bedName ) {
        this.room = room;
        this.bedName = bedName;
    }

    /**
     * Returns the name of the bed within the room.
     * 
     * @return bed name (can be null for a private room)
     */
    public String getBedName() {
        return bedName;
    }

    /**
     * Returns the name of the room.
     * 
     * @return room name (not-null)
     */
    public String getRoom() {
        return room;
    }
}
