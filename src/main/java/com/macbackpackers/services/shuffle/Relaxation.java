package com.macbackpackers.services.shuffle;

/**
 * One rule a rule-breaking alternative is allowed to break, most preferred first. F to MX is never relaxed, and
 * neither are in-house guests, blocks or mid-stay bed changes for anyone but the target.
 */
public enum Relaxation {

    STAFF_LT_MIXED( "Offline Room", "Offline rooms require staff approval" ),

    LARGER_DORM( "Larger dorm of the same gender", "stay in the booked room type" ),

    STAFF_OTHER( "Other staff dorm (LT_MALE / LT_FEMALE)", "staff dorms require staff approval" ),

    /** Other guests' bookings on their locked bed may move; the row's own locked beds never do. */
    UNLOCK( "Remove a bed lock", "locked bookings stay on their bed" ),

    GROUP_SPLIT( "Split a group across two rooms", "a group stays in one room" ),

    OTHER_ROOM_TYPE( "Other room type change (same gender)", "room type fallback limits" ),

    DISPLACE( "Leave another booking unassigned", "every booking keeps a bed" );

    private final String label;
    private final String ruleBroken;

    Relaxation( String label, String ruleBroken ) {
        this.label = label;
        this.ruleBroken = ruleBroken;
    }

    public String label() {
        return label;
    }

    public String ruleBroken() {
        return ruleBroken;
    }
}
