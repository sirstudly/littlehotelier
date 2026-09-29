package com.macbackpackers.services.shuffle;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Suggested calendar moves for placing the unassigned beds of one reservation without a mid-stay bed change, or
 * when that's impossible: why, the best split within the rules, and ranked alternatives that each break one rule.
 * Suggestions only; nothing is applied to Cloudbeds.
 */
public class ShuffleSuggestion {

    public enum Status {
        /** Nothing to do: every bed of the reservation is already assigned. */
        ALREADY_ASSIGNED,
        /** Moves found for every unassigned bed within the rules. */
        FOUND,
        /** More beds booked than exist in the room type on some night. */
        OVERBOOKED,
        /** Proven impossible within the rules. */
        INFEASIBLE,
        /** No answer within the time limit. */
        UNKNOWN
    }

    /**
     * @param overbookedNights nights with more guests than beds (capacity reason)
     * @param blockers         bookings and rules that together block the target (fragmentation reason)
     */
    public record Reason( String summary, List<LocalDate> overbookedNights, List<String> blockers ) {
    }

    /**
     * One way to place the reservation.
     *
     * @param relaxation the rule broken, or null for a split within the rules
     */
    public record Option( Relaxation relaxation, String label, List<ShuffleMove> moves, List<String> notes ) {

        public String ruleBroken() {
            return relaxation == null ? null : relaxation.ruleBroken();
        }
    }

    private final long reservationId;
    private final List<ShuffleBooking> targets;
    private Status status = Status.ALREADY_ASSIGNED;
    private FallbackLevel level;
    private List<ShuffleMove> moves = List.of();
    private final List<String> notes = new ArrayList<>();
    private Reason reason;
    private Option split;
    private final List<Option> alternatives = new ArrayList<>();
    private boolean consolidation;
    private boolean splitApplicable = true;

    public ShuffleSuggestion( long reservationId, List<ShuffleBooking> targets ) {
        this.reservationId = reservationId;
        this.targets = List.copyOf( targets );
    }

    void found( FallbackLevel level, List<ShuffleMove> moves, List<String> notes ) {
        this.status = Status.FOUND;
        this.level = level;
        this.moves = List.copyOf( moves );
        this.notes.addAll( notes );
    }

    void addNote( String note ) {
        notes.add( note );
    }

    /** Marks this as bringing already-assigned beds together. */
    void consolidation() {
        this.consolidation = true;
    }

    public boolean isConsolidation() {
        return consolidation;
    }

    void impossible( Status status, Reason reason ) {
        this.status = status;
        this.reason = reason;
    }

    void setSplit( Option split ) {
        this.split = split;
    }

    /** No split was looked for, so none is reported as missing either. */
    void withoutSplit() {
        this.split = null;
        this.splitApplicable = false;
    }

    void addAlternative( Option alternative ) {
        alternatives.add( alternative );
    }

    public long getReservationId() {
        return reservationId;
    }

    /** The unassigned beds of the reservation this suggestion places. */
    public List<ShuffleBooking> getTargets() {
        return targets;
    }

    public Status getStatus() {
        return status;
    }

    public boolean isImpossible() {
        return status == Status.OVERBOOKED || status == Status.INFEASIBLE || status == Status.UNKNOWN;
    }

    /** Fallback level used when found. */
    public FallbackLevel getLevel() {
        return level;
    }

    /** Steps in the order to perform them (found only). */
    public List<ShuffleMove> getMoves() {
        return moves;
    }

    public List<String> getNotes() {
        return Collections.unmodifiableList( notes );
    }

    public Reason getReason() {
        return reason;
    }

    /** Fewest bed changes within the rules; null when found or when even a split can't fit. */
    public Option getSplit() {
        return split;
    }

    public List<Option> getAlternatives() {
        return Collections.unmodifiableList( alternatives );
    }

    public String describe() {
        StringBuilder sb = new StringBuilder( "Reservation " + reservationId );
        if ( false == targets.isEmpty() ) {
            sb.append( " (" ).append( targets.get( 0 ).guestName() ).append( ")" );
        }
        sb.append( ": " ).append( status );
        if ( status == Status.FOUND ) {
            if ( level != null && level != FallbackLevel.SAME_TYPE ) {
                sb.append( " with " ).append( level.description() );
            }
            appendNotes( sb, notes, "  " );
            appendMoves( sb, moves, "  " );
            return sb.toString();
        }
        if ( reason != null ) {
            sb.append( "\nWhy: " ).append( reason.summary() );
            reason.blockers().forEach( b -> sb.append( "\n  - " ).append( b ) );
        }
        appendNotes( sb, notes, "" );
        if ( split != null ) {
            sb.append( "\nBest split within the rules: " ).append( split.label() );
            appendNotes( sb, split.notes(), "  " );
            appendMoves( sb, split.moves(), "  " );
        }
        else if ( isImpossible() && splitApplicable ) {
            sb.append( "\nNo split within the rules fits either." );
        }
        if ( false == alternatives.isEmpty() ) {
            sb.append( "\nAlternatives that break a rule:" );
            char letter = 'A';
            for ( Option alt : alternatives ) {
                sb.append( "\n" ).append( letter++ ).append( ". " ).append( alt.label() )
                        .append( " (breaks: " ).append( alt.ruleBroken() ).append( ")" );
                appendNotes( sb, alt.notes(), "   " );
                appendMoves( sb, alt.moves(), "   " );
            }
        }
        return sb.toString();
    }

    private static void appendNotes( StringBuilder sb, List<String> notes, String indent ) {
        notes.forEach( n -> sb.append( "\n" ).append( indent ).append( "* " ).append( n ) );
    }

    private static void appendMoves( StringBuilder sb, List<ShuffleMove> moves, String indent ) {
        int i = 1;
        for ( ShuffleMove m : moves ) {
            sb.append( "\n" ).append( indent ).append( i++ ).append( ". " ).append( m.describe() );
        }
    }

    /** Moves as plain text, e.g. for JSON responses. */
    public static List<String> describe( List<ShuffleMove> moves ) {
        return moves.stream().map( ShuffleMove::describe ).collect( Collectors.toList() );
    }
}
