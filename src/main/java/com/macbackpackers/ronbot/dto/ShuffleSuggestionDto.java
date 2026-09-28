package com.macbackpackers.ronbot.dto;

import java.util.List;
import java.util.stream.Collectors;

import com.macbackpackers.services.shuffle.ShuffleSuggestion;

/**
 * Suggested bed moves for placing a reservation's unassigned beds without a mid-stay bed change; when impossible, why,
 * the best split within the rules and ranked alternatives that each break one rule. Nothing is applied.
 *
 * @param status       ALREADY_ASSIGNED | FOUND | OVERBOOKED | INFEASIBLE | UNKNOWN
 * @param level        room type fallback used when found
 * @param moves        steps in order (found only)
 * @param reason       why it's impossible (null when found)
 * @param split        fewest bed changes within the rules (impossible only; may be null)
 * @param alternatives each breaks exactly one rule, best first
 * @param text         the whole suggestion as plain text, ready to send
 */
public record ShuffleSuggestionDto( String property, long reservationId, String status, String level,
        List<String> moves, List<String> notes, Reason reason, Option split, List<Option> alternatives, String text ) {

    /**
     * @param overbookedNights nights with more guests than beds (capacity reason)
     * @param blockers         bookings and rules that would have to give way (fragmentation reason)
     */
    public record Reason( String summary, List<String> overbookedNights, List<String> blockers ) {
    }

    /** @param rule relaxation enum name (null for the split within the rules) */
    public record Option( String rule, String label, String ruleBroken, List<String> notes, List<String> moves ) {

        static Option of( ShuffleSuggestion.Option o ) {
            return o == null ? null
                    : new Option( o.relaxation() == null ? null : o.relaxation().name(), o.label(), o.ruleBroken(),
                            o.notes(), ShuffleSuggestion.describe( o.moves() ) );
        }
    }

    public static ShuffleSuggestionDto of( String property, ShuffleSuggestion s ) {
        ShuffleSuggestion.Reason r = s.getReason();
        Reason reason = r == null ? null
                : new Reason( r.summary(), r.overbookedNights().stream().map( Object::toString ).collect( Collectors.toList() ),
                        r.blockers() );
        return new ShuffleSuggestionDto( property, s.getReservationId(), s.getStatus().name(),
                s.getLevel() == null ? null : s.getLevel().name(), ShuffleSuggestion.describe( s.getMoves() ), s.getNotes(),
                reason, Option.of( s.getSplit() ),
                s.getAlternatives().stream().map( Option::of ).collect( Collectors.toList() ), s.describe() );
    }
}
