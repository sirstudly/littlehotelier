package com.macbackpackers.services.shuffle;

import java.time.format.DateTimeFormatter;

/**
 * One step a person performs on the Cloudbeds calendar. Steps are ordered so each lands on a free bed.
 */
public record ShuffleMove( Type type, ShuffleBooking booking, ShuffleBed fromBed, ShuffleBed toBed ) {

    public enum Type {
        ASSIGN, MOVE, UNASSIGN
    }

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern( "dd-MMM" );

    public String describe() {
        String dates = " (" + DATE.format( booking.checkin() ) + " to " + DATE.format( booking.checkout() ) + ")";
        switch ( type ) {
            case UNASSIGN:
                return "Unassign " + booking.label() + " from " + fromBed.label() + dates;
            case ASSIGN:
                return "Assign " + booking.label() + " to " + toBed.label() + dates;
            default:
                return "Move " + booking.label() + " from " + fromBed.label() + " to " + toBed.label() + dates;
        }
    }

    @Override
    public String toString() {
        return describe();
    }
}
