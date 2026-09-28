package com.macbackpackers.services.shuffle;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Turns a target assignment into calendar steps where every step lands on a bed that is free at that point.
 * Cycles (A needs B's bed while B needs A's) are broken by unassigning one booking first and assigning it last.
 */
public final class MoveSequencer {

    private MoveSequencer() {
    }

    public static List<ShuffleMove> sequence( BedCalendar calendar, Map<String, String> target ) {
        Map<String, String> working = calendar.copyCurrentAssignment();
        Set<String> pending = new LinkedHashSet<>();
        for ( ShuffleBooking b : calendar.bookings() ) {
            if ( false == Objects.equals( working.get( b.key() ), target.get( b.key() ) ) ) {
                pending.add( b.key() );
            }
        }
        List<ShuffleMove> moves = new ArrayList<>();
        while ( false == pending.isEmpty() ) {
            boolean progress = false;
            for ( String key : new ArrayList<>( pending ) ) {
                ShuffleBooking booking = calendar.booking( key );
                String from = working.get( key );
                String to = target.get( key );
                if ( to == null ) {
                    moves.add( new ShuffleMove( ShuffleMove.Type.UNASSIGN, booking, calendar.bed( from ), null ) );
                }
                else if ( calendar.occupants( to, booking, working ).isEmpty() ) {
                    moves.add( new ShuffleMove( from == null ? ShuffleMove.Type.ASSIGN : ShuffleMove.Type.MOVE,
                            booking, calendar.bed( from ), calendar.bed( to ) ) );
                }
                else {
                    continue;
                }
                working.put( key, to );
                pending.remove( key );
                progress = true;
            }
            if ( false == progress ) {
                String blocker = pickBlocker( calendar, pending, working, target );
                moves.add( new ShuffleMove( ShuffleMove.Type.UNASSIGN, calendar.booking( blocker ),
                        calendar.bed( working.get( blocker ) ), null ) );
                working.remove( blocker );
            }
        }
        return moves;
    }

    /** Pending, still-assigned booking that blocks the most other pending steps. */
    private static String pickBlocker( BedCalendar calendar, Set<String> pending, Map<String, String> working,
            Map<String, String> target ) {
        Map<String, Integer> blocking = new HashMap<>();
        for ( String key : pending ) {
            String to = target.get( key );
            for ( ShuffleBooking occupant : calendar.occupants( to, calendar.booking( key ), working ) ) {
                if ( pending.contains( occupant.key() ) ) {
                    blocking.merge( occupant.key(), 1, Integer::sum );
                }
            }
        }
        return blocking.entrySet().stream()
                .max( Map.Entry.<String, Integer> comparingByValue()
                        .thenComparing( Map.Entry.comparingByKey() ) )
                .map( Map.Entry::getKey )
                .orElseThrow( () -> new IllegalStateException( "No blocker found for pending moves " + pending ) );
    }
}
