
package com.macbackpackers.scrapers.matchers;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Matches rooms/bed labels in LH for CastleRock.
 */
public class CastleRockRoomBedMatcher implements RoomBedMatcher {

    private final Logger LOGGER = LoggerFactory.getLogger( getClass() );

    private String room;
    private String bedName;

    /**
     * Default constructor.
     * 
     * @param roomLabel label of the room/bed in LH
     */
    public CastleRockRoomBedMatcher( String roomLabel ) {
        Pattern p = Pattern.compile( "([^\\-]*)-(.*)$" ); // anything but dash for room #, everything else for bed
        Matcher m = p.matcher( roomLabel );
        if ( m.find() == false ) {
            LOGGER.info( "Couldn't determine bed name from '" + roomLabel + "'. Is it a private?" );
            room = roomLabel;
        }
        else {
            room = m.group( 1 );
            bedName = m.group( 2 );
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
