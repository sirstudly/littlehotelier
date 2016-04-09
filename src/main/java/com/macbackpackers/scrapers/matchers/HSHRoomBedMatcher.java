
package com.macbackpackers.scrapers.matchers;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Matches rooms/bed labels in LH for High Street Hostel.
 */
public class HSHRoomBedMatcher implements RoomBedMatcher {

    private String room;
    private String bedName;

    /**
     * These are the different room labels that LH has set up. The first bit of the room label must
     * start with one of these...
     */
    private static final String ROOM_NAMES[] = {
            "3A", "3B", "3C", "3D", "3E",
            "5A", "5B", "5C", "5D", "5E", "5F", "5G", "6P",
            "TA", "TB", "TC",
            "LOTR", "T3M", "TMNT", "F&V", "Zoo"
    };

    /**
     * Default constructor.
     * 
     * @param roomLabel label for this bed/room in LH
     */
    public HSHRoomBedMatcher( String roomLabel ) {

        // for room 6, we setup a specific match
        Pattern p = Pattern.compile( "6([0-9]{2}.*)$" );
        Matcher m = p.matcher( roomLabel );
        if ( m.find() ) {
            room = "6";
            bedName = m.group( 1 );
        }
        else {
            // otherwise, we just see what the label starts with and split it
            for ( String roomName : ROOM_NAMES ) {
                if ( roomLabel.startsWith( roomName ) ) {
                    room = roomName;
                    bedName = roomLabel.substring( roomName.length() );
                    break;
                }
            }
        }

        // if we haven't matched anything, just use the room label as the room (no bed name)
        if ( room == null ) {
            room = roomLabel;
        }
    }

    @Override
    public String getBedName() {
        return bedName;
    }

    @Override
    public String getRoom() {
        return room;
    }

}
