package com.macbackpackers.services;

/**
 * Computed housekeeping badge values for a bed on the selected date.
 */
public final class HousekeepingBedsheet {

    public static final String EMPTY = "EMPTY";
    public static final String NO_CHANGE = "NO CHANGE";
    public static final String N_DAY_CHANGE = "N DAY CHANGE";
    public static final String CHANGE_CHECKED_OUT = "CHANGE (CHECKED OUT)";
    public static final String CHANGE_IN_HOUSE = "CHANGE (IN-HOUSE)";
    public static final String CHANGE_ROOM_CLOSURE = "CHANGE (ROOM CLOSURE)";

    private HousekeepingBedsheet() {
    }

    public static boolean isChange( String bedsheet ) {
        return bedsheet != null && bedsheet.startsWith( "CHANGE" );
    }

    public static boolean countsTowardTotals( String bedsheet ) {
        return isChange( bedsheet ) || N_DAY_CHANGE.equals( bedsheet );
    }
}
