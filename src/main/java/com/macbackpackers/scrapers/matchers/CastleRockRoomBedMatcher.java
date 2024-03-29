
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

    @Override
    public BedAssignment parse( String roomLabel ) {
        String room = null;
        String bedName = null;
        Matcher m = Pattern.compile( "PB\\(\\(\\d+\\)").matcher( roomLabel );
        if( m.find() ) {
            room = "PB"; // paid beds
            bedName = roomLabel;
        }
        else {
            Pattern p = Pattern.compile( "([^\\-]*)-(.*)$" ); // anything but dash for room #, everything else for bed
            m = p.matcher( roomLabel );
            if ( m.find() == false ) {
                LOGGER.debug( "Couldn't determine bed name from '" + roomLabel + "'. Is it a private?" );
                room = roomLabel;
            }
            else {
                room = m.group( 1 );
                bedName = m.group( 2 );
            }
        }
        return new BedAssignment( room, bedName );
    }

}
