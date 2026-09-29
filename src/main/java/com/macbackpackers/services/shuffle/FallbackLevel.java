package com.macbackpackers.services.shuffle;

/**
 * Room type changes allowed within the rules, tried in order until one finds a solution.
 */
public enum FallbackLevel {

    /** Everyone stays in their booked room type. */
    SAME_TYPE( "same room type" ),

    /** Up to 4 members of a group of 4+ booked in a 4-bed dorm may share a Quad; the rest stay in the dorm. */
    QUAD_FOR_GROUPS( "up to 4 group members moved from a 4-bed dorm into a Quad" ),

    /** Guests may move to a smaller dorm of the same gender (never into a 4-bed dorm), keeping the booked price. */
    SMALLER_DORM( "guests moved to a smaller dorm of the same gender (keep price unchanged)" );

    private final String description;

    FallbackLevel( String description ) {
        this.description = description;
    }

    public String description() {
        return description;
    }
}
