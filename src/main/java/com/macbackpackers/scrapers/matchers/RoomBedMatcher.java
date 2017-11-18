
package com.macbackpackers.scrapers.matchers;

/**
 * Interface for splitting up the room/beds from the single label used in little hotelier.
 */
public interface RoomBedMatcher {

    /**
     * Extracts the room/bed from the single label used in LH.
     * 
     * @param roomBedLabel LH room/bed
     * @return non-null room/bed assignment from label
     */
    BedAssignment parse( String roomBedLabel );

}
