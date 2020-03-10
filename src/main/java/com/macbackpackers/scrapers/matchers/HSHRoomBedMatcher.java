
package com.macbackpackers.scrapers.matchers;

import com.macbackpackers.exceptions.MissingUserDataException;

/**
 * Matches rooms/bed labels in LH for High Street Hostel.
 */
public class HSHRoomBedMatcher implements RoomBedMatcher {

    /**
     * These are the different room labels that has been set up. The first bit of the room label must
     * start with one of these...
     */
    enum ROOM_NAMES {
        THREE_A ("3A", "3A"), 
        THREE_B ("3B", "3B"),
        THREE_C ("3C", "3C"),
        THREE_D ("3D", "3D"),
        THREE_E ("3E", "3E"),
        FIVE_A ("5A", "5A"),
        FIVE_B ("5B", "5B"),
        FIVE_C ("5C", "5C"),
        FIVE_D ("5D", "5D"),
        FIVE_E ("5E", "5E"),
        FIVE_F ("5F", "5F"),
        FIVE_G ("5G", "5G"),
        SIX_P ("6P", "6P"),
        SIX_R ("6R", "6R"),
        WILL_KATE ("7Will", "TA", 1),
        BATMAN_ROBIN ("7Batman", "TB", 1),
        LADY_TRAMP ("7Lady", "TC", 1),
        LOTR ("7LOTR", "LOTR"),
        T3M ("7T3M", "T3M"),
        TMNT ("7TMNT", "TMNT"),
        FRUIT_VEG ("4F&V", "F&V"),
        ZOO ("4Z", "Zoo"),
        UNALLOCATED ("N/A", "Unallocated");

        final String room;
        final String mappedRoom;
        final int substringLength;

        private ROOM_NAMES( String room, String mappedRoom ) {
            this.room = room;
            this.mappedRoom = mappedRoom;
            this.substringLength = room.length();
        }

        private ROOM_NAMES( String room, String mappedRoom, int substringLength ) {
            this.room = room;
            this.mappedRoom = mappedRoom;
            this.substringLength = substringLength;
        }

        public String getRoom() {
            return room;
        }

        public String getMappedRoom() {
            return mappedRoom;
        }

        public int getSubstringLength() {
            return substringLength;
        }
    }

    /**
     * Returns the room given the room number.
     * 
     * @param roomNumber "room number"
     * @return non-null room name.
     */
    private static String getRoomFromRoomNumber( String roomNumber ) {
        if ( roomNumber == null ) {
            return "Unallocated";
        }
        for ( ROOM_NAMES room : ROOM_NAMES.values() ) {
            if ( roomNumber.startsWith( room.getRoom() ) ) {
                return room.getMappedRoom();
            }
        }
        throw new MissingUserDataException( "Missing room mapping for " + roomNumber );
    }

    /**
     * Returns the bedname given the room number.
     * 
     * @param roomNumber "room number"
     * @return non-null room name.
     */
    private static String getBedNameFromRoomNumber( String roomNumber ) {
        if ( roomNumber == null || "N/A".equals( roomNumber ) ) {
            return null;
        }
        for ( ROOM_NAMES room : ROOM_NAMES.values() ) {
            if ( roomNumber.startsWith( room.getRoom() ) ) {
                return roomNumber.substring( room.getSubstringLength() );
            }
        }
        throw new MissingUserDataException( "Missing room mapping for " + roomNumber );
    }

    @Override
    public BedAssignment parse( String roomNumber ) {
        return new BedAssignment( getRoomFromRoomNumber( roomNumber ), getBedNameFromRoomNumber( roomNumber ) );
    }
}
