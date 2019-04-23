
package com.macbackpackers.scrapers.matchers;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Matches rooms/bed labels in Cloudbeds for Lochside.
 */
public class LochsideRoomBedMatcher implements RoomBedMatcher {

    private final Logger LOGGER = LoggerFactory.getLogger( getClass() );

    @Override
    public BedAssignment parse( String roomLabel ) {
        String room = null;
        String bedName = null;
        Pattern p = Pattern.compile( "(.*)\\((.*)\\)$" );
        Matcher m = p.matcher( roomLabel );
        if ( m.find() == false ) {
            LOGGER.debug( "Couldn't determine bed name from '" + roomLabel + "'. Is it a private?" );
            room = roomLabel.trim();
        }
        else {
            room = m.group( 1 ).trim();
            bedName = m.group( 2 ).trim();
        }
        return new BedAssignment( room, bedName );
    }

}
