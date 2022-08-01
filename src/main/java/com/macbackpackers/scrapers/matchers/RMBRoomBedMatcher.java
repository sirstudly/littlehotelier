
package com.macbackpackers.scrapers.matchers;

/**
 * Matches rooms/bed labels in LH for Royal Mile Backpackers.
 */
public class RMBRoomBedMatcher implements RoomBedMatcher {

    /**
     * These are the different room labels that LH has set up. The first bit of the room label must
     * start with one of these...
     */
    private static final String ROOM_NAMES[] = {
            "LM", "T", "ME", "*", "GC", "HW", "SW"
    };

    @Override
    public BedAssignment parse( String roomLabel ) {

        String room = null;
        String bedName = null;

        // we just see what the label starts with and split it
        for ( String roomName : ROOM_NAMES ) {
            // remapped staff room
            if ( roomLabel.startsWith( "*" ) ) {
                room = "SW";
                bedName = roomLabel.substring( roomName.length() ).trim();
                break;
            }
            else if ( roomLabel.startsWith( roomName ) ) {
                room = roomName;
                bedName = roomLabel.substring( roomName.length() ).trim();
                break;
            }
        }

        // if we haven't matched anything, just use the room label as the room (no bed name)
        if ( room == null ) {
            room = roomLabel;
        }
        return new BedAssignment( room, bedName );
    }

}
