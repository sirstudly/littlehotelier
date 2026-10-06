package com.macbackpackers.services.shuffle;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.macbackpackers.beans.BedLock;
import com.macbackpackers.beans.BookingAssignment;
import com.macbackpackers.beans.RoomBed;
import com.macbackpackers.dao.WordPressDAO;
import com.macbackpackers.services.shuffle.CpSatShuffleSolver.Result;
import com.macbackpackers.services.shuffle.CpSatShuffleSolver.Spec;

/**
 * Suggests calendar moves that put an unassigned reservation on one bed for its whole stay:
 * <ol>
 * <li>per-night capacity check of the booked room type;</li>
 * <li>CP-SAT at each {@link FallbackLevel} until one finds a solution;</li>
 * <li>if none does: the reason, the fewest-bed-changes split within the rules, and up to
 * {@link Options#maxAlternatives} alternatives that each break one rule ({@link Relaxation}, in order).</li>
 * </ol>
 * Suggestion only; nothing is changed in Cloudbeds.
 */
@Service
public class BedShuffleService {

    private static final Logger LOGGER = LoggerFactory.getLogger( BedShuffleService.class );

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern( "dd-MMM" );

    private static final Set<String> IGNORED_BED_STATUSES = Set.of( "canceled", "cancelled", "no_show", "checked_out" );

    private static final int MAX_BLOCKERS_LISTED = 12;

    /** Search settings. */
    public static final class Options {
        /** Bookings starting before this are fixed; null to use {@link #horizonDays} before the stay. */
        LocalDate today;
        int horizonDays = 14;
        /** First try with only bookings within this many days of the stay allowed to move (quick, short lists). */
        int nearDays = 3;
        double timeLimitSeconds = 15;
        double explainTimeLimitSeconds = 5;
        double alternativeTimeLimitSeconds = 5;
        int maxAlternatives = 5;
        /** Alternatives needing more steps than this aren't worth offering. */
        int maxAlternativeMoves = 20;
        /** Also bring the reservation's already-assigned beds together when split across rooms or beds. */
        boolean consolidate;

        public Options today( LocalDate today ) {
            this.today = today;
            return this;
        }

        public Options horizonDays( int days ) {
            this.horizonDays = days;
            return this;
        }

        public Options timeLimitSeconds( double seconds ) {
            this.timeLimitSeconds = seconds;
            return this;
        }

        public Options maxAlternatives( int max ) {
            this.maxAlternatives = max;
            return this;
        }

        public Options consolidate( boolean consolidate ) {
            this.consolidate = consolidate;
            return this;
        }
    }

    /** A reservation's split groups to bring together, the back-to-back bookings to keep on one bed, and the beds that may move. */
    record Consolidation( Set<String> groups, List<BedCalendar.Chain> chains, List<ShuffleBooking> targets ) {
    }

    @Autowired
    private WordPressDAO dao;

    public ShuffleSuggestion suggestForReservation( long reservationId ) {
        return suggestForReservation( reservationId, new Options() );
    }

    /** From the local calendar as of today ({@code options.today} is overwritten). */
    public ShuffleSuggestion suggestForReservation( long reservationId, Options options ) {
        LocalDate today = LocalDate.now();
        BedCalendar calendar = buildCalendar( dao.fetchActiveHousekeepingRooms(),
                dao.fetchCurrentBookingAssignmentsCheckingOutAfter( today ), today, dao.fetchActiveBedLocks() );
        return suggest( calendar, reservationId, options.today( today ) );
    }

    public static ShuffleSuggestion suggest( BedCalendar calendar, long reservationId ) {
        return suggest( calendar, reservationId, new Options() );
    }

    /**
     * Places every unassigned bed of the reservation at once (and with {@link Options#consolidate}, brings its split
     * beds together). When found, the result is applied to {@code calendar} so a following call builds on it.
     */
    public static ShuffleSuggestion suggest( BedCalendar calendar, long reservationId, Options options ) {
        List<ShuffleBooking> unassigned = calendar.bookings().stream()
                .filter( b -> b.reservationId() == reservationId )
                .filter( b -> calendar.currentAssignment().get( b.key() ) == null )
                .filter( b -> false == calendar.roomType( b.roomTypeId() ).isNonGuest() )
                .sorted( Comparator.comparing( ShuffleBooking::checkin ) )
                .collect( Collectors.toList() );
        if ( options.consolidate ) {
            Consolidation c = planConsolidation( calendar, reservationId );
            if ( false == c.targets().isEmpty() ) {
                List<ShuffleBooking> targets = new ArrayList<>( unassigned );
                targets.addAll( c.targets() );
                targets.sort( Comparator.comparing( ShuffleBooking::checkin ) );
                ShuffleSuggestion consolidated;
                calendar.consolidate( c.groups(), c.chains() );
                try {
                    // with unassigned beds too, placing those comes first if the group can't also be brought together
                    consolidated = solve( calendar, reservationId, Set.of( reservationId ), targets, options, c,
                            unassigned.isEmpty() );
                }
                finally {
                    calendar.clearConsolidation();
                }
                if ( consolidated != null ) {
                    return consolidated;
                }
                ShuffleSuggestion placed = solve( calendar, reservationId, Set.of( reservationId ), unassigned, options, null, true );
                placed.addNote( "Could not also bring " + describeGroups( calendar, c ) + " back together; left as they are" );
                return placed;
            }
        }
        return solve( calendar, reservationId, Set.of( reservationId ), unassigned, options, null, true );
    }

    /** From the local calendar as of today ({@code options.today} is overwritten). */
    public ShuffleSuggestion suggestForConsecutive( long reservationId, long nextReservationId, int roomTypeId, Options options ) {
        LocalDate today = LocalDate.now();
        BedCalendar calendar = buildCalendar( dao.fetchActiveHousekeepingRooms(),
                dao.fetchCurrentBookingAssignmentsCheckingOutAfter( today ), today, dao.fetchActiveBedLocks() );
        return suggestConsecutive( calendar, reservationId, nextReservationId, roomTypeId, options.today( today ) );
    }

    /**
     * Puts one guest's back-to-back reservations on one bed: each bed of {@code reservationId} in {@code roomTypeId}
     * is chained to a bed of {@code nextReservationId} checking in the day it checks out. Either side may move to any
     * bed (not necessarily one either is on now); only pinned (e.g. in-house) beds stay where they are. When found,
     * the result is applied to {@code calendar}.
     */
    public static ShuffleSuggestion suggestConsecutive( BedCalendar calendar, long reservationId, long nextReservationId,
            int roomTypeId, Options options ) {
        List<ShuffleBooking> firsts = calendar.bookings().stream()
                .filter( b -> b.reservationId() == reservationId && b.roomTypeId() == roomTypeId )
                .collect( Collectors.toList() );
        List<ShuffleBooking> seconds = calendar.bookings().stream()
                .filter( b -> b.reservationId() == nextReservationId && b.roomTypeId() == roomTypeId )
                .collect( Collectors.toList() );
        List<BedCalendar.Chain> chains = pairChains( calendar, firsts, seconds );
        Map<String, String> current = calendar.currentAssignment();
        boolean together = chains.stream().allMatch( c -> current.get( c.first() ) != null
                && current.get( c.first() ).equals( current.get( c.second() ) ) );
        if ( together ) {
            return new ShuffleSuggestion( nextReservationId, List.of() );
        }
        List<ShuffleBooking> targets = new ArrayList<>();
        targets.addAll( firsts );
        targets.addAll( seconds );
        targets.removeIf( b -> b.pinned() || current.get( b.key() ) == null );
        targets.sort( Comparator.comparing( ShuffleBooking::checkin ) );
        Consolidation c = new Consolidation( Set.of(), chains, targets );
        if ( targets.isEmpty() ) {
            ShuffleSuggestion suggestion = new ShuffleSuggestion( nextReservationId, targets );
            suggestion.consolidation();
            suggestion.impossible( ShuffleSuggestion.Status.INFEASIBLE, new ShuffleSuggestion.Reason(
                    "Could not keep " + describeConsolidation( calendar, c ) + " on one bed: both are already in-house or locked",
                    List.of(), List.of() ) );
            return suggestion;
        }
        calendar.consolidate( Set.of(), chains );
        try {
            return solve( calendar, nextReservationId, Set.of( reservationId, nextReservationId ), targets, options, c, true );
        }
        finally {
            calendar.clearConsolidation();
        }
    }

    /**
     * Groups of the reservation (one room type) split over more rooms than they need, or with back-to-back bookings on
     * different beds. Only their unpinned, assigned beds become targets; pinned (in-house) beds stay and anchor them.
     */
    static Consolidation planConsolidation( BedCalendar calendar, long reservationId ) {
        Set<String> groups = new LinkedHashSet<>();
        List<BedCalendar.Chain> chains = new ArrayList<>();
        List<ShuffleBooking> targets = new ArrayList<>();
        Map<String, String> current = calendar.currentAssignment();
        for ( Map.Entry<String, List<ShuffleBooking>> e : calendar.groups().entrySet() ) {
            List<ShuffleBooking> members = e.getValue();
            if ( members.get( 0 ).reservationId() != reservationId
                    || false == calendar.roomType( members.get( 0 ).roomTypeId() ).isGuestDorm() ) {
                continue;
            }
            List<ShuffleBooking> movable = members.stream()
                    .filter( m -> false == m.pinned() && current.get( m.key() ) != null )
                    .collect( Collectors.toList() );
            if ( movable.isEmpty() ) {
                continue;
            }
            List<ShuffleBooking> pinned = members.stream().filter( ShuffleBooking::pinned ).collect( Collectors.toList() );
            int allowed = Math.max( calendar.roomsNeeded( members ), calendar.currentRooms( pinned ).size() );
            List<BedCalendar.Chain> groupChains = pairChains( calendar, members );
            boolean spread = calendar.currentRooms( members ).size() > allowed;
            boolean bedChange = groupChains.stream().anyMatch( c -> current.get( c.first() ) != null
                    && current.get( c.second() ) != null && false == current.get( c.first() ).equals( current.get( c.second() ) ) );
            if ( spread || bedChange ) {
                groups.add( e.getKey() );
                chains.addAll( groupChains );
                targets.addAll( movable );
            }
        }
        return new Consolidation( groups, chains, targets );
    }

    /**
     * Pairs each booking with one that checks in the day it checks out, preferring one already on the same bed, then
     * in the same room.
     */
    static List<BedCalendar.Chain> pairChains( BedCalendar calendar, List<ShuffleBooking> members ) {
        return pairChains( calendar, members, members );
    }

    /** As {@link #pairChains(BedCalendar, List)}, with each chain's first half from {@code firsts} and second from {@code seconds}. */
    static List<BedCalendar.Chain> pairChains( BedCalendar calendar, List<ShuffleBooking> firsts, List<ShuffleBooking> seconds ) {
        Comparator<ShuffleBooking> order = Comparator.comparing( ShuffleBooking::checkin ).thenComparing( ShuffleBooking::key );
        List<ShuffleBooking> sortedFirsts = firsts.stream().sorted( order ).collect( Collectors.toList() );
        List<ShuffleBooking> sortedSeconds = seconds.stream().sorted( order ).collect( Collectors.toList() );
        Map<String, String> current = calendar.currentAssignment();
        Set<String> continued = new LinkedHashSet<>();
        List<BedCalendar.Chain> chains = new ArrayList<>();
        for ( ShuffleBooking a : sortedFirsts ) {
            ShuffleBed bedA = calendar.bed( current.get( a.key() ) );
            ShuffleBooking best = null;
            int bestScore = -1;
            for ( ShuffleBooking b : sortedSeconds ) {
                if ( continued.contains( b.key() ) || false == b.checkin().equals( a.checkout() ) ) {
                    continue;
                }
                ShuffleBed bedB = calendar.bed( current.get( b.key() ) );
                int score = bedA == null || bedB == null ? 0
                        : bedA.id().equals( bedB.id() ) ? 2 : bedA.room().equals( bedB.room() ) ? 1 : 0;
                if ( score > bestScore ) {
                    best = b;
                    bestScore = score;
                }
            }
            if ( best != null ) {
                continued.add( best.key() );
                chains.add( new BedCalendar.Chain( a.key(), best.key() ) );
            }
        }
        return chains;
    }

    /** E.g. "group 181470761 (Guest Name) in rooms 20, 41". */
    private static String describeGroups( BedCalendar calendar, Consolidation c ) {
        Map<String, List<ShuffleBooking>> groups = calendar.groups();
        return c.groups().stream()
                .map( g -> groups.get( g ) )
                .map( members -> "group " + members.get( 0 ).label() + " in rooms "
                        + String.join( ", ", calendar.currentRooms( members ) ) )
                .collect( Collectors.joining( "; " ) );
    }

    /** The groups being brought together or, with none, the back-to-back bookings being kept on one bed. */
    private static String describeConsolidation( BedCalendar calendar, Consolidation c ) {
        if ( false == c.groups().isEmpty() ) {
            return describeGroups( calendar, c );
        }
        return c.chains().stream()
                .map( chain -> {
                    ShuffleBooking a = calendar.booking( chain.first() );
                    ShuffleBooking b = calendar.booking( chain.second() );
                    return a.label() + " " + DATE.format( a.checkin() ) + " to " + DATE.format( a.checkout() )
                            + " and " + b.label() + " " + DATE.format( b.checkin() ) + " to " + DATE.format( b.checkout() );
                } )
                .distinct()
                .collect( Collectors.joining( "; " ) );
    }

    private static List<String> describeConsolidations( BedCalendar calendar, Result r, Consolidation c ) {
        List<String> notes = new ArrayList<>();
        Map<String, List<ShuffleBooking>> groups = calendar.groups();
        for ( String key : c.groups() ) {
            List<ShuffleBooking> members = groups.get( key );
            Map<String, Integer> before = roomCounts( calendar, members, calendar.currentAssignment() );
            Map<String, Integer> after = roomCounts( calendar, members, r.assignment() );
            if ( after.size() < before.size() ) {
                notes.add( "Brings group " + members.get( 0 ).label() + " together in room" + ( after.size() == 1 ? " " : "s " )
                        + String.join( ", ", new TreeSet<>( after.keySet() ) )
                        + " (was rooms " + String.join( ", ", new TreeSet<>( before.keySet() ) ) + ")" );
            }
        }
        for ( BedCalendar.Chain chain : c.chains() ) {
            String beforeA = calendar.currentAssignment().get( chain.first() );
            String beforeB = calendar.currentAssignment().get( chain.second() );
            String after = r.assignment().get( chain.first() );
            if ( beforeA != null && beforeB != null && false == beforeA.equals( beforeB ) && after != null ) {
                ShuffleBooking a = calendar.booking( chain.first() );
                ShuffleBooking b = calendar.booking( chain.second() );
                notes.add( "Keeps " + a.label() + " on " + calendar.bed( after ).label() + " from " + DATE.format( a.checkin() )
                        + " to " + DATE.format( b.checkout() ) + " (no bed change on " + DATE.format( b.checkin() ) + ")" );
            }
        }
        return notes;
    }

    /**
     * Gives each target one bed for its stay, trying each fallback level; when none works, the reason, the
     * fewest-bed-change split and alternatives that break one rule each.
     *
     * @param keepLocked     reservations of this row, whose locked beds are never unlocked
     * @param c              groups being brought together ({@link BedCalendar#consolidate} already applied); null if none
     * @param ifImpossible   false to return null rather than explain, split and look for alternatives
     */
    private static ShuffleSuggestion solve( BedCalendar calendar, long reservationId, Set<Long> keepLocked,
            List<ShuffleBooking> targets, Options options, Consolidation c, boolean ifImpossible ) {
        ShuffleSuggestion suggestion = new ShuffleSuggestion( reservationId, targets );
        if ( c != null ) {
            suggestion.consolidation();
        }
        if ( targets.isEmpty() ) {
            return suggestion;
        }
        Set<String> keys = targets.stream().map( ShuffleBooking::key ).collect( Collectors.toCollection( LinkedHashSet::new ) );
        LocalDate first = targets.stream().map( ShuffleBooking::checkin ).min( Comparator.naturalOrder() ).get();
        LocalDate last = targets.stream().map( ShuffleBooking::checkout ).max( Comparator.naturalOrder() ).get();
        LocalDate start = options.today == null ? first.minusDays( options.horizonDays )
                : ( options.today.isBefore( first ) ? options.today : first );
        LocalDate end = last.plusDays( options.horizonDays );

        List<String> capacityLines = new ArrayList<>();
        Set<LocalDate> overbooked = new TreeSet<>();
        for ( int type : targets.stream().map( ShuffleBooking::roomTypeId ).collect( Collectors.toCollection( LinkedHashSet::new ) ) ) {
            RoomTypeFeasibility f = RoomTypeFeasibility.check( calendar, type, first, last,
                    b -> keys.contains( b.key() ) || calendar.currentAssignment().get( b.key() ) != null );
            for ( LocalDate night : f.getOverbookedNights() ) {
                overbooked.add( night );
                capacityLines.add( DATE.format( night ) + ": " + f.getDemandByNight().get( night ) + " guests for "
                        + f.getCapacity() + " beds in " + calendar.roomType( type ).describe() );
            }
        }

        Spec base = new Spec().targets( keys ).keepLocked( keepLocked ).window( start, end )
                .timeLimitSeconds( options.timeLimitSeconds );
        LocalDate nearStart = first.minusDays( options.nearDays );
        LocalDate nearEnd = last.plusDays( options.nearDays );
        Map<FallbackLevel, CpSatShuffleSolver.Outcome> outcomes = new LinkedHashMap<>();
        if ( overbooked.isEmpty() ) {
            for ( FallbackLevel level : applicableLevels( calendar, targets ) ) {
                Result r = solveNearThenFull( calendar, base.copy().level( level ), nearStart, nearEnd );
                if ( r.isFound() ) {
                    List<String> errors = calendar.validate( r.assignment(), r.relaxedKeys(), false );
                    if ( false == errors.isEmpty() ) {
                        throw new IllegalStateException( "Invalid shuffle for " + reservationId + ": " + errors );
                    }
                    List<String> notes = new ArrayList<>();
                    if ( c != null ) {
                        notes.addAll( describeConsolidations( calendar, r, c ) );
                    }
                    notes.addAll( describePlacements( calendar, r, keys ) );
                    suggestion.found( level, MoveSequencer.sequence( calendar, r.assignment() ), notes );
                    calendar.applyAssignment( r.assignment() );
                    return suggestion;
                }
                outcomes.put( level, r.outcome() );
            }
        }
        if ( false == ifImpossible ) {
            return null;
        }

        if ( false == overbooked.isEmpty() ) {
            suggestion.impossible( ShuffleSuggestion.Status.OVERBOOKED, new ShuffleSuggestion.Reason(
                    "More guests than beds on " + overbooked.stream().map( DATE::format ).collect( Collectors.joining( ", " ) ),
                    List.copyOf( overbooked ), capacityLines ) );
        }
        else {
            boolean timedOut = outcomes.containsValue( CpSatShuffleSolver.Outcome.UNKNOWN );
            boolean chainsOnly = c != null && c.groups().isEmpty();
            String problem = c == null ? null
                    : chainsOnly ? "Could not keep " + describeConsolidation( calendar, c )
                            + " on one bed without moving in-house guests, blocks or locked beds"
                    : "Could not bring " + describeGroups( calendar, c )
                            + " together without moving in-house guests, blocks or locked beds";
            suggestion.impossible( timedOut ? ShuffleSuggestion.Status.UNKNOWN : ShuffleSuggestion.Status.INFEASIBLE,
                    explain( calendar, targets, base, nearStart, nearEnd, options, outcomes, problem ) );
            if ( chainsOnly ) {
                // splitting a chain's halves across beds is just where they are now
                suggestion.withoutSplit();
            }
            else {
                suggestion.setSplit( bestSplit( calendar, keys,
                        solveNearThenFull( calendar, base.copy().splitTargets( true ), nearStart, nearEnd ) ) );
            }
        }
        for ( Relaxation relaxation : Relaxation.values() ) {
            if ( suggestion.getAlternatives().size() >= options.maxAlternatives ) {
                break;
            }
            if ( applicable( calendar, targets, keepLocked, relaxation ) ) {
                Result r = solveNearThenFull( calendar, base.copy().relaxation( relaxation )
                        .timeLimitSeconds( options.alternativeTimeLimitSeconds ), nearStart, nearEnd );
                ShuffleSuggestion.Option alt = alternative( calendar, keys, relaxation, r, options );
                if ( alt != null ) {
                    suggestion.addAlternative( alt );
                }
            }
        }
        return suggestion;
    }

    /**
     * Solves with only bookings near the stay allowed to move; if that finds nothing, again over the whole window
     * (needed to prove it impossible). An unproven near result is never reported as infeasible.
     */
    private static Result solveNearThenFull( BedCalendar calendar, Spec spec, LocalDate nearStart, LocalDate nearEnd ) {
        boolean nearCoversWindow = false == nearStart.isAfter( spec.windowStart ) && false == nearEnd.isBefore( spec.windowEnd );
        if ( false == nearCoversWindow ) {
            Result near = CpSatShuffleSolver.solve( calendar, spec.copy().movable( nearStart, nearEnd ) );
            if ( near.isFound() ) {
                return near;
            }
        }
        return CpSatShuffleSolver.solve( calendar, spec );
    }

    private static List<FallbackLevel> applicableLevels( BedCalendar calendar, List<ShuffleBooking> targets ) {
        List<FallbackLevel> levels = new ArrayList<>( List.of( FallbackLevel.SAME_TYPE ) );
        if ( targets.stream().anyMatch( t -> calendar.roomType( t.roomTypeId() ).isFourBedDorm() ) ) {
            levels.add( FallbackLevel.QUAD_FOR_GROUPS );
        }
        if ( targets.stream().anyMatch( t -> calendar.roomType( t.roomTypeId() ).isGuestDorm() ) ) {
            levels.add( FallbackLevel.SMALLER_DORM );
        }
        return levels;
    }

    private static boolean applicable( BedCalendar calendar, List<ShuffleBooking> targets, Set<Long> keepLocked,
            Relaxation relaxation ) {
        boolean dorm = targets.stream().anyMatch( t -> calendar.roomType( t.roomTypeId() ).isGuestDorm() );
        switch ( relaxation ) {
            case UNLOCK:
                return calendar.bookings().stream().anyMatch( b -> b.locked() && false == keepLocked.contains( b.reservationId() ) );
            case STAFF_LT_MIXED:
                return targets.stream().anyMatch( t -> calendar.roomType( t.roomTypeId() ).isGuestDorm()
                        && calendar.roomType( t.roomTypeId() ).gender() == RoomTypeInfo.Gender.MIXED );
            case LARGER_DORM:
            case STAFF_OTHER:
            case OTHER_ROOM_TYPE:
                return dorm;
            default:
                return true;
        }
    }

    /**
     * Why the reservation can't have one bed within the rules, naming the blocking bookings and rules.
     *
     * @param problem what couldn't be done; null for "no single bed ... for the whole stay"
     */
    private static ShuffleSuggestion.Reason explain( BedCalendar calendar, List<ShuffleBooking> targets, Spec base,
            LocalDate nearStart, LocalDate nearEnd, Options options, Map<FallbackLevel, CpSatShuffleSolver.Outcome> outcomes,
            String problem ) {
        String who = targets.stream().map( t -> t.label() + " " + DATE.format( t.checkin() ) + " to " + DATE.format( t.checkout() ) )
                .distinct().collect( Collectors.joining( "; " ) );
        String what = problem != null ? problem
                : "Enough beds each night, but no single bed in the booked room type is free for the whole stay of " + who;
        List<String> unproven = outcomes.entrySet().stream()
                .filter( e -> e.getValue() == CpSatShuffleSolver.Outcome.UNKNOWN )
                .map( e -> e.getKey().description() )
                .collect( Collectors.toList() );
        String timeLimitNote = unproven.isEmpty() ? ""
                : " (not proven within the time limit with " + String.join( "; ", unproven ) + ")";
        if ( outcomes.get( FallbackLevel.SAME_TYPE ) != CpSatShuffleSolver.Outcome.INFEASIBLE ) {
            return new ShuffleSuggestion.Reason( "No shuffle found for " + who + " within the time limit" + timeLimitNote,
                    List.of(), List.of() );
        }
        Spec explainSpec = base.copy().level( FallbackLevel.SAME_TYPE ).explain( true ).timeLimitSeconds( options.explainTimeLimitSeconds );
        Result r = CpSatShuffleSolver.solve( calendar, explainSpec.copy().movable( nearStart, nearEnd ) );
        if ( r.blockers().isEmpty() ) {
            r = CpSatShuffleSolver.solve( calendar, explainSpec );
        }
        List<String> blockers = r.blockers().stream()
                .map( CpSatShuffleSolver.Blocker::text )
                .distinct()
                .sorted()
                .collect( Collectors.toList() );
        if ( blockers.size() > MAX_BLOCKERS_LISTED ) {
            int more = blockers.size() - MAX_BLOCKERS_LISTED;
            blockers = new ArrayList<>( blockers.subList( 0, MAX_BLOCKERS_LISTED ) );
            blockers.add( "... and " + more + " more" );
        }
        String summary = what + timeLimitNote + ( blockers.isEmpty() ? "" : ". It would only fit if these gave way:" );
        return new ShuffleSuggestion.Reason( summary, List.of(), blockers );
    }

    private static ShuffleSuggestion.Option bestSplit( BedCalendar calendar, Set<String> keys, Result r ) {
        if ( false == r.isFound() ) {
            return null;
        }
        BedCalendar split = r.calendar();
        List<String> errors = split.validate( r.assignment(), r.relaxedKeys(), false );
        if ( false == errors.isEmpty() ) {
            LOGGER.warn( "Discarding invalid split: {}", errors );
            return null;
        }
        List<String> notes = new ArrayList<>();
        int bedChanges = 0;
        for ( String key : keys ) {
            List<ShuffleBooking> segments = split.bookings().stream()
                    .filter( b -> b.key().startsWith( key + "#" ) )
                    .sorted( Comparator.comparing( ShuffleBooking::checkin ) )
                    .collect( Collectors.toList() );
            bedChanges += Math.max( 0, segments.size() - 1 );
            notes.add( segments.get( 0 ).label() + ": " + segments.stream()
                    .map( s -> DATE.format( s.checkin() ) + " to " + DATE.format( s.checkout() ) + " in "
                            + split.bed( r.assignment().get( s.key() ) ).label() )
                    .collect( Collectors.joining( ", then " ) ) );
        }
        notes.addAll( describePlacements( split, r, Set.of() ).stream()
                .filter( n -> n.startsWith( "Also places" ) ).collect( Collectors.toList() ) );
        return new ShuffleSuggestion.Option( null, bedChanges + " bed change" + ( bedChanges == 1 ? "" : "s" ),
                MoveSequencer.sequence( split, r.assignment() ), notes );
    }

    private static ShuffleSuggestion.Option alternative( BedCalendar calendar, Set<String> keys, Relaxation relaxation,
            Result r, Options options ) {
        if ( false == r.isFound() ) {
            return null;
        }
        List<String> errors = calendar.validate( r.assignment(), r.relaxedKeys(), relaxation == Relaxation.GROUP_SPLIT,
                relaxation == Relaxation.UNLOCK );
        if ( false == errors.isEmpty() ) {
            LOGGER.warn( "Discarding invalid {} alternative: {}", relaxation, errors );
            return null;
        }
        List<String> notes = new ArrayList<>();
        if ( relaxation == Relaxation.UNLOCK ) {
            for ( ShuffleBooking b : calendar.bookings() ) {
                String before = calendar.currentAssignment().get( b.key() );
                if ( b.locked() && false == Objects.equals( before, r.assignment().get( b.key() ) ) ) {
                    notes.add( "Remove the bed lock on " + b.label() + " (" + calendar.bed( before ).label() + ") first" );
                }
            }
            if ( notes.isEmpty() ) {
                return null;
            }
        }
        notes.addAll( describePlacements( calendar, r, keys ) );
        if ( relaxation == Relaxation.STAFF_OTHER ) {
            for ( String key : r.relaxedKeys() ) {
                ShuffleBooking b = calendar.booking( key );
                ShuffleBed bed = calendar.bed( r.assignment().get( key ) );
                if ( calendar.roomType( bed.roomTypeId() ).isStaff()
                        && calendar.roomType( b.roomTypeId() ).gender() == RoomTypeInfo.Gender.MIXED ) {
                    notes.add( "Check " + b.guestName() + "'s gender before using " + bed.label() + " (" + bed.roomType() + ")" );
                }
            }
        }
        if ( relaxation == Relaxation.GROUP_SPLIT ) {
            notes.addAll( describeGroupSplits( calendar, r ) );
        }
        List<ShuffleMove> moves = MoveSequencer.sequence( calendar, r.assignment() );
        if ( moves.isEmpty() ) {
            return null; // e.g. letting an already-split group stay split: that's just the current state
        }
        if ( moves.size() > options.maxAlternativeMoves ) {
            LOGGER.info( "Skipping {} alternative with {} steps", relaxation, moves.size() );
            return null;
        }
        return new ShuffleSuggestion.Option( relaxation, relaxation.label(), moves, notes );
    }

    /** Room type changes, bumped bookings and other unassigned bookings placed along the way. */
    private static List<String> describePlacements( BedCalendar calendar, Result r, Set<String> targetKeys ) {
        List<String> notes = new ArrayList<>();
        for ( String key : r.relaxedKeys() ) {
            ShuffleBooking b = r.calendar().booking( key );
            ShuffleBed bed = r.calendar().bed( r.assignment().get( key ) );
            if ( b == null || bed == null || bed.roomTypeId() == b.roomTypeId() ) {
                continue;
            }
            notes.add( b.label() + " goes to " + bed.label() + ", a " + calendar.roomType( bed.roomTypeId() ).describe()
                    + ", instead of the booked " + calendar.roomType( b.roomTypeId() ).describe() );
        }
        for ( ShuffleBooking b : r.calendar().bookings() ) {
            String before = r.calendar().currentAssignment().get( b.key() );
            String after = r.assignment().get( b.key() );
            if ( before != null && after == null ) {
                notes.add( "Leaves " + b.label() + " (" + DATE.format( b.checkin() ) + " to " + DATE.format( b.checkout() )
                        + ", now in " + r.calendar().bed( before ).label() + ") unassigned" );
            }
            else if ( before == null && after != null && false == targetKeys.contains( b.key() ) && false == b.key().contains( "#" ) ) {
                notes.add( "Also places unassigned " + b.label() + " (" + DATE.format( b.checkin() ) + " to "
                        + DATE.format( b.checkout() ) + ")" );
            }
            else if ( before == null && after == null && competesWithTargets( r.calendar(), b, targetKeys ) ) {
                notes.add( "Unassigned " + b.label() + " (" + DATE.format( b.checkin() ) + " to "
                        + DATE.format( b.checkout() ) + ") still has no bed" );
            }
        }
        return notes;
    }

    /** Another unassigned, movable booking of a target's room type overlapping it. */
    private static boolean competesWithTargets( BedCalendar calendar, ShuffleBooking b, Set<String> targetKeys ) {
        if ( targetKeys.contains( b.key() ) || b.key().contains( "#" ) || b.pinned()
                || calendar.roomType( b.roomTypeId() ).isNonGuest() ) {
            return false;
        }
        return targetKeys.stream().map( calendar::booking ).filter( Objects::nonNull )
                .anyMatch( t -> t.roomTypeId() == b.roomTypeId() && t.reservationId() != b.reservationId() && t.overlaps( b ) );
    }

    private static List<String> describeGroupSplits( BedCalendar calendar, Result r ) {
        List<String> notes = new ArrayList<>();
        for ( List<ShuffleBooking> group : calendar.groups().values() ) {
            Map<String, Integer> before = roomCounts( calendar, group, calendar.currentAssignment() );
            Map<String, Integer> after = roomCounts( calendar, group, r.assignment() );
            if ( after.size() > before.size() && false == Objects.equals( before, after ) ) {
                notes.add( "Splits group " + group.get( 0 ).label() + " across rooms " + after.entrySet().stream()
                        .sorted( Map.Entry.<String, Integer> comparingByValue().reversed().thenComparing( Map.Entry.comparingByKey() ) )
                        .map( e -> e.getKey() + " (" + e.getValue() + ")" ).collect( Collectors.joining( " and " ) ) );
            }
        }
        return notes;
    }

    private static Map<String, Integer> roomCounts( BedCalendar calendar, List<ShuffleBooking> group, Map<String, String> assignment ) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for ( ShuffleBooking b : group ) {
            ShuffleBed bed = calendar.bed( assignment.get( b.key() ) );
            if ( bed != null ) {
                counts.merge( bed.room(), 1, Integer::sum );
            }
        }
        return counts;
    }

    /**
     * @param rooms active beds (excluding Unallocated), including staff dorms (their bookings are pinned)
     * @param rows  current booking assignments checking out after {@code today}
     */
    public static BedCalendar buildCalendar( List<RoomBed> rooms, List<BookingAssignment> rows, LocalDate today ) {
        return buildCalendar( rooms, rows, today, List.of() );
    }

    /**
     * @param rooms active beds (excluding Unallocated), including staff dorms (their bookings are pinned)
     * @param rows  current booking assignments checking out after {@code today}
     * @param locks active bed locks; a booking on its locked bed is pinned (and marked locked)
     */
    public static BedCalendar buildCalendar( List<RoomBed> rooms, List<BookingAssignment> rows, LocalDate today,
            List<BedLock> locks ) {
        Set<String> lockedBeds = locks.stream()
                .map( l -> lockKey( l.getReservationId(), l.getRoomId() ) )
                .collect( Collectors.toSet() );
        Map<String, ShuffleBed> beds = rooms.stream()
                .collect( Collectors.toMap( RoomBed::getId,
                        r -> new ShuffleBed( r.getId(), r.getRoom(), r.getBedName(), r.getRoomTypeId(),
                                StringUtils.defaultString( r.getRoomType() ), r.getCapacity() ),
                        ( a, b ) -> a, LinkedHashMap::new ) );
        BedCalendar calendar = new BedCalendar( beds.values() );
        for ( BookingAssignment row : rows ) {
            if ( false == row.isCurrent() || row.getCheckinDate() == null || row.getCheckoutDate() == null
                    || IGNORED_BED_STATUSES.contains( StringUtils.lowerCase( row.getBedStatus() ) ) ) {
                continue;
            }
            ShuffleBed bed = beds.get( row.getRoomId() );
            boolean unassigned = StringUtils.isBlank( row.getRoomId() ) || "Unallocated".equalsIgnoreCase( row.getRoom() );
            if ( bed == null && false == unassigned ) {
                LOGGER.debug( "Skipping {} on unknown bed {}", row.getAssignmentKey(), row.getRoomId() );
                continue;
            }
            Integer roomTypeId = bed != null ? Integer.valueOf( bed.roomTypeId() ) : row.getRoomTypeId();
            if ( roomTypeId == null ) {
                continue;
            }
            boolean reservation = row.getReservationId() != null && row.getReservationId() > 0;
            boolean pinned = row.isInHouse() || "checked_in".equalsIgnoreCase( row.getBedStatus() )
                    || row.isClosure() || false == reservation
                    || row.getCheckinLocalDate().isBefore( today )
                    || ( bed != null && bed.isNonGuest() )
                    || calendar.roomType( roomTypeId ).isNonGuest();
            boolean locked = false == pinned && bed != null && lockedBeds.contains( lockKey( row.getReservationId(), bed.id() ) );
            calendar.addBooking( new ShuffleBooking( row.getAssignmentKey(),
                    reservation ? row.getReservationId() : -row.getId(),
                    StringUtils.defaultString( row.getGuestName() ), roomTypeId,
                    row.getCheckinLocalDate(), row.getCheckoutLocalDate(), pinned || locked, locked ),
                    bed == null ? null : bed.id() );
        }
        return calendar;
    }

    private static String lockKey( long reservationId, String roomId ) {
        return reservationId + "/" + roomId;
    }
}
