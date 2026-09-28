package com.macbackpackers.services.shuffle;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.ortools.Loader;
import com.google.ortools.sat.BoolVar;
import com.google.ortools.sat.CpModel;
import com.google.ortools.sat.CpSolver;
import com.google.ortools.sat.CpSolverStatus;
import com.google.ortools.sat.IntVar;
import com.google.ortools.sat.LinearArgument;
import com.google.ortools.sat.LinearExpr;
import com.google.ortools.sat.LinearExprBuilder;
import com.google.ortools.sat.Literal;

/**
 * Places the target bookings with Google OR-Tools CP-SAT: one boolean per (booking, candidate bed), at most one
 * booking per bed per night, groups limited to as few rooms as they need, and an objective that minimises the number
 * of other bookings moved (plus penalties for any room type change or rule relaxation the {@link Spec} allows).
 * <p>
 * Pinned bookings and bookings crossing the window edges are fixed to their bed. In explain mode each such booking
 * over the target's nights and each group rule gets a switch, and the model finds the fewest switches to turn off for
 * the target to fit (an infeasibility core via assumptions proved too slow to extract on real calendars).
 */
public class CpSatShuffleSolver {

    private static final Logger LOGGER = LoggerFactory.getLogger( CpSatShuffleSolver.class );

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern( "dd-MMM" );

    static final long MOVE_COST = 1;
    static final long UNPLACED_COST = 5;
    static final long QUAD_COST = 10;
    static final long EXPLAIN_COST = 100_000;
    static final long DOWNSIZE_COST = 10;
    static final long RELAXED_COST = 100;
    static final long DISPLACE_COST = 100;
    static final long SPLIT_BED_CHANGE_COST = 1000;
    static final long SPLIT_ROOM_CHANGE_COST = 200;
    static final int QUAD_GUESTS = 4;

    public enum Outcome {
        FOUND, INFEASIBLE, UNKNOWN
    }

    /** What to solve. */
    public static final class Spec {
        Set<String> targetKeys = Set.of();
        FallbackLevel level = FallbackLevel.SAME_TYPE;
        Relaxation relaxation;
        boolean splitTargets;
        boolean explain;
        LocalDate windowStart;
        LocalDate windowEnd;
        LocalDate movableStart;
        LocalDate movableEnd;
        double timeLimitSeconds = 15;
        int workers = 8;
        int maxDownsizedPerGroup = 4;

        public Spec targets( Set<String> keys ) {
            this.targetKeys = keys;
            return this;
        }

        public Spec level( FallbackLevel level ) {
            this.level = level;
            return this;
        }

        public Spec relaxation( Relaxation relaxation ) {
            this.relaxation = relaxation;
            return this;
        }

        public Spec splitTargets( boolean split ) {
            this.splitTargets = split;
            return this;
        }

        public Spec explain( boolean explain ) {
            this.explain = explain;
            return this;
        }

        public Spec window( LocalDate start, LocalDate end ) {
            this.windowStart = start;
            this.windowEnd = end;
            return this;
        }

        /** Only bookings overlapping this span may move (null for the whole window). */
        public Spec movable( LocalDate start, LocalDate end ) {
            this.movableStart = start;
            this.movableEnd = end;
            return this;
        }

        public Spec copy() {
            Spec s = new Spec();
            s.targetKeys = targetKeys;
            s.level = level;
            s.relaxation = relaxation;
            s.splitTargets = splitTargets;
            s.explain = explain;
            s.windowStart = windowStart;
            s.windowEnd = windowEnd;
            s.movableStart = movableStart;
            s.movableEnd = movableEnd;
            s.timeLimitSeconds = timeLimitSeconds;
            s.workers = workers;
            s.maxDownsizedPerGroup = maxDownsizedPerGroup;
            return s;
        }

        public Spec timeLimitSeconds( double seconds ) {
            this.timeLimitSeconds = seconds;
            return this;
        }

        public Spec maxDownsizedPerGroup( int max ) {
            this.maxDownsizedPerGroup = max;
            return this;
        }

        @Override
        public String toString() {
            return "level=" + level + ( relaxation == null ? "" : " relaxation=" + relaxation )
                    + ( splitTargets ? " split" : "" ) + ( explain ? " explain" : "" )
                    + " window=" + windowStart + ".." + windowEnd
                    + ( movableStart == null ? "" : " movable=" + movableStart + ".." + movableEnd );
        }
    }

    /** A fixed booking or group rule that would have to give way (together with the others reported) for the target to fit. */
    public record Blocker( String text, ShuffleBooking booking ) {
    }

    /**
     * @param calendar    calendar the assignment refers to: the input calendar, or a copy where each split target is
     *                    replaced by its per-bed segments
     * @param assignment  full target assignment (booking key to bed id; absent when unassigned)
     * @param relaxedKeys bookings placed outside their room type, plus split segments
     * @param blockers    explain mode only
     */
    public record Result( Outcome outcome, BedCalendar calendar, Map<String, String> assignment, long objective,
            Set<String> relaxedKeys, boolean groupSplit, List<Blocker> blockers ) {

        public boolean isFound() {
            return outcome == Outcome.FOUND;
        }
    }

    private static boolean nativesLoaded;

    /** Loads the bundled OR-Tools native library (once per JVM). */
    public static synchronized void loadNatives() {
        if ( false == nativesLoaded ) {
            long start = System.currentTimeMillis();
            Loader.loadNativeLibraries();
            nativesLoaded = true;
            LOGGER.info( "Loaded OR-Tools natives in {}ms", System.currentTimeMillis() - start );
        }
    }

    public static Result solve( BedCalendar calendar, Spec spec ) {
        loadNatives();
        return new CpSatShuffleSolver( calendar, spec ).run();
    }

    /** A booking (or one night of a split target) the model places. */
    private static final class Piece {
        final ShuffleBooking booking;
        final ShuffleBooking parent;
        final boolean target;
        final String current;
        final boolean fallbackEligible;
        final Map<String, BoolVar> x = new LinkedHashMap<>();
        BoolVar none;
        long noneCost;

        Piece( ShuffleBooking booking, ShuffleBooking parent, boolean target, String current, boolean fallbackEligible ) {
            this.booking = booking;
            this.parent = parent;
            this.target = target;
            this.current = current;
            this.fallbackEligible = fallbackEligible;
        }

        List<Literal> options() {
            List<Literal> options = new ArrayList<>( x.values() );
            if ( none != null ) {
                options.add( none );
            }
            return options;
        }
    }

    /** A group of 4+ from a 4-bed dorm that may put up to 4 of its members into one Quad. */
    private record QuadUse( String bedId, BoolVar z, List<Piece> members ) {
    }

    private final BedCalendar calendar;
    private final Spec spec;
    private final CpModel model = new CpModel();
    private final LinearExprBuilder objective = LinearExpr.newBuilder();
    private final List<Piece> pieces = new ArrayList<>();
    private final Map<String, List<ShuffleBooking>> fixedByBed = new HashMap<>();
    private final Set<Integer> targetTypes = new HashSet<>();
    private final Set<Long> targetReservations = new HashSet<>();
    private final Map<Integer, Map<Integer, Long>> fallbackTypes = new HashMap<>();
    private final Map<String, ShuffleBed> scopeBeds = new LinkedHashMap<>();
    private final List<QuadUse> quadUses = new ArrayList<>();
    private final Set<BoolVar> quadMemberVars = new HashSet<>();
    private final Map<BoolVar, Blocker> assumptions = new LinkedHashMap<>();
    private final List<BoolVar> groupSplitVars = new ArrayList<>();

    private CpSatShuffleSolver( BedCalendar calendar, Spec spec ) {
        this.calendar = calendar;
        this.spec = Objects.requireNonNull( spec );
        if ( spec.windowStart == null || spec.windowEnd == null ) {
            throw new IllegalArgumentException( "Window required: " + spec );
        }
    }

    private Result run() {
        for ( String key : spec.targetKeys ) {
            targetTypes.add( calendar.booking( key ).roomTypeId() );
            targetReservations.add( calendar.booking( key ).reservationId() );
        }
        Set<Integer> scopeTypes = new HashSet<>( targetTypes );
        for ( int type : targetTypes ) {
            Map<Integer, Long> fallbacks = computeFallbackTypes( type );
            fallbackTypes.put( type, fallbacks );
            scopeTypes.addAll( fallbacks.keySet() );
            if ( spec.level.compareTo( FallbackLevel.QUAD_FOR_GROUPS ) >= 0 && calendar.roomType( type ).isFourBedDorm() ) {
                scopeTypes.addAll( allRoomTypes().stream().filter( t -> calendar.roomType( t ).isQuad() ).collect( Collectors.toSet() ) );
            }
        }
        calendar.beds().stream().filter( b -> scopeTypes.contains( b.roomTypeId() ) ).forEach( b -> scopeBeds.put( b.id(), b ) );

        collectPieces();
        addPlacementVariables();
        addQuadUses();
        addBedNightConstraints();
        addGroupConstraints();
        addChainConstraints();
        addSplitCosts();

        CpSolver solver = new CpSolver();
        solver.getParameters().setMaxTimeInSeconds( spec.timeLimitSeconds );
        solver.getParameters().setNumWorkers( spec.workers );
        for ( BoolVar a : assumptions.keySet() ) {
            objective.add( EXPLAIN_COST ).addTerm( a, -EXPLAIN_COST );
        }
        model.minimize( objective.build() );
        CpSolverStatus status = solver.solve( model );
        LOGGER.info( "CP-SAT {} for {}: {} pieces, {} beds, status {} in {}s", spec, spec.targetKeys, pieces.size(),
                scopeBeds.size(), status, String.format( "%.2f", solver.wallTime() ) );

        if ( status == CpSolverStatus.INFEASIBLE ) {
            return new Result( Outcome.INFEASIBLE, calendar, Map.of(), 0, Set.of(), false, List.of() );
        }
        if ( status != CpSolverStatus.OPTIMAL && status != CpSolverStatus.FEASIBLE ) {
            return new Result( Outcome.UNKNOWN, calendar, Map.of(), 0, Set.of(), false, List.of() );
        }
        if ( spec.explain ) {
            List<Blocker> blockers = assumptions.entrySet().stream()
                    .filter( e -> false == solver.booleanValue( e.getKey() ) )
                    .map( Map.Entry::getValue )
                    .collect( Collectors.toList() );
            return new Result( Outcome.FOUND, calendar, Map.of(), solver.objectiveValue() > 0 ? (long) solver.objectiveValue() : 0,
                    Set.of(), false, blockers );
        }
        return buildResult( solver );
    }

    // ---------------------------------------------------------------- scope

    private Set<Integer> allRoomTypes() {
        return calendar.beds().stream().map( ShuffleBed::roomTypeId ).collect( Collectors.toCollection( LinkedHashSet::new ) );
    }

    /**
     * Room types (other than its own) a movable booking of {@code type} may be placed in, with the per-booking cost.
     * F never goes to MX: dorm fallbacks keep the gender, and staff dorms follow the gender rules below.
     */
    private Map<Integer, Long> computeFallbackTypes( int type ) {
        Map<Integer, Long> result = new LinkedHashMap<>();
        RoomTypeInfo booked = calendar.roomType( type );
        if ( false == booked.isGuestDorm() || booked.gender() == null ) {
            return result;
        }
        for ( int other : allRoomTypes() ) {
            RoomTypeInfo info = calendar.roomType( other );
            if ( other == type || false == info.isGuestDorm() || info.isNonGuest() || info.gender() != booked.gender() ) {
                continue;
            }
            boolean smaller = info.dormSize() < booked.dormSize();
            if ( smaller && false == info.isFourBedDorm() && spec.level == FallbackLevel.SMALLER_DORM ) {
                result.put( other, DOWNSIZE_COST + ( booked.dormSize() - info.dormSize() ) / 2 );
            }
            else if ( smaller && spec.relaxation == Relaxation.OTHER_ROOM_TYPE ) {
                result.put( other, RELAXED_COST );
            }
            else if ( info.dormSize() > booked.dormSize() && spec.relaxation == Relaxation.LARGER_DORM ) {
                result.put( other, RELAXED_COST );
            }
        }
        if ( spec.relaxation == Relaxation.STAFF_LT_MIXED || spec.relaxation == Relaxation.STAFF_OTHER ) {
            staffTypesFor( type, spec.relaxation == Relaxation.STAFF_LT_MIXED ).forEach( t -> result.put( t, RELAXED_COST ) );
        }
        return result;
    }

    /**
     * Staff dorm types a booking of {@code type} could use. LT_MIXED only takes MX bookings; LT_FEMALE takes F and
     * MX; LT_MALE takes M and MX (staff check the guest's gender for MX).
     *
     * @param mixed true for LT_MIXED only, false for LT_MALE/LT_FEMALE only
     */
    private Set<Integer> staffTypesFor( int type, boolean mixed ) {
        RoomTypeInfo.Gender gender = calendar.roomType( type ).gender();
        Set<Integer> result = new LinkedHashSet<>();
        if ( gender == null || false == calendar.roomType( type ).isGuestDorm() ) {
            return result;
        }
        for ( int other : allRoomTypes() ) {
            RoomTypeInfo info = calendar.roomType( other );
            if ( false == info.isStaff() ) {
                continue;
            }
            boolean isMixed = info.gender() == RoomTypeInfo.Gender.MIXED;
            if ( mixed != isMixed ) {
                continue;
            }
            boolean allowed = isMixed ? gender == RoomTypeInfo.Gender.MIXED
                    : gender == RoomTypeInfo.Gender.MIXED || gender == info.gender();
            if ( allowed ) {
                result.add( other );
            }
        }
        return result;
    }

    private void collectPieces() {
        List<ShuffleBooking> targets = spec.targetKeys.stream().map( calendar::booking ).collect( Collectors.toList() );
        for ( ShuffleBooking b : calendar.bookings() ) {
            if ( false == b.overlaps( spec.windowStart, spec.windowEnd ) ) {
                continue;
            }
            String current = calendar.currentAssignment().get( b.key() );
            boolean target = spec.targetKeys.contains( b.key() );
            boolean eligible = targetTypes.contains( b.roomTypeId() );
            if ( target ) {
                if ( spec.splitTargets ) {
                    for ( LocalDate n = b.checkin(); n.isBefore( b.checkout() ); n = n.plusDays( 1 ) ) {
                        pieces.add( new Piece( new ShuffleBooking( b.key() + "@" + n, b.reservationId(), b.guestName(),
                                b.roomTypeId(), n, n.plusDays( 1 ), false ), b, true, current, true ) );
                    }
                }
                else {
                    // an assigned target (being consolidated) may stay where it is
                    pieces.add( new Piece( b, b, true, current, true ) );
                }
                continue;
            }
            boolean competes = targets.stream().anyMatch( t -> t.overlaps( b ) );
            if ( current == null ) {
                if ( eligible && competes && false == b.pinned() && false == calendar.roomType( b.roomTypeId() ).isNonGuest() ) {
                    Piece p = new Piece( b, b, false, null, true );
                    p.noneCost = UNPLACED_COST;
                    pieces.add( p );
                }
                continue;
            }
            if ( false == scopeBeds.containsKey( current ) ) {
                continue;
            }
            boolean crossesWindow = b.checkin().isBefore( spec.windowStart ) || b.checkout().isAfter( spec.windowEnd );
            // in fallback room types only guests in the target's way move; the rest just leave their free beds
            boolean fallbackBystander = false == eligible && false == competes;
            boolean outsideMovable = spec.movableStart != null && false == b.overlaps( spec.movableStart, spec.movableEnd );
            if ( b.pinned() || crossesWindow || fallbackBystander || outsideMovable || scopeBeds.get( current ).isNonGuest() ) {
                fixedByBed.computeIfAbsent( current, k -> new ArrayList<>() ).add( b );
                continue;
            }
            Piece p = new Piece( b, b, false, current, eligible );
            if ( spec.relaxation == Relaxation.DISPLACE && false == targetReservations.contains( b.reservationId() ) ) {
                long daysAhead = Math.max( 0, ChronoUnit.DAYS.between( spec.windowStart, b.checkin() ) );
                p.noneCost = DISPLACE_COST + Math.max( 0, 30 - Math.min( 30, daysAhead ) );
            }
            pieces.add( p );
        }
    }

    // ---------------------------------------------------------------- variables

    private boolean blockedByFixed( String bedId, ShuffleBooking booking ) {
        for ( ShuffleBooking f : fixedByBed.getOrDefault( bedId, List.of() ) ) {
            if ( f.overlaps( booking ) ) {
                return true;
            }
        }
        return false;
    }

    private void addPlacementVariables() {
        Map<String, BoolVar> fixedSwitches = new HashMap<>();
        for ( Piece p : pieces ) {
            int type = p.parent.roomTypeId();
            Map<Integer, Long> fallbacks = p.fallbackEligible ? fallbackTypes.getOrDefault( type, Map.of() ) : Map.of();
            for ( ShuffleBed bed : scopeBeds.values() ) {
                boolean own = bed.roomTypeId() == type && false == bed.isNonGuest();
                Long fallbackCost = fallbacks.get( bed.roomTypeId() );
                if ( false == own && fallbackCost == null && false == bed.id().equals( p.current ) ) {
                    continue;
                }
                boolean blocked = blockedByFixed( bed.id(), p.booking );
                // only stays over the target's nights are worth naming; the rest stay hard
                boolean switchable = spec.explain && own && overlapsTargets( bed.id() );
                if ( blocked && false == switchable && false == bed.id().equals( p.current ) ) {
                    continue;
                }
                BoolVar x = model.newBoolVar( "x_" + p.booking.key() + "_" + bed.id() );
                p.x.put( bed.id(), x );
                if ( blocked && switchable ) {
                    for ( ShuffleBooking f : fixedByBed.get( bed.id() ) ) {
                        if ( f.overlaps( p.booking ) && false == isTargetStay( f ) ) {
                            model.addEquality( x, 0 );
                        }
                        else if ( f.overlaps( p.booking ) ) {
                            BoolVar a = fixedSwitches.computeIfAbsent( f.key(), k -> newAssumption( describeFixed( f, bed ), f ) );
                            model.addImplication( a, x.not() );
                        }
                    }
                }
                if ( fallbackCost != null ) {
                    objective.addTerm( x, fallbackCost );
                }
            }
            if ( p.noneCost > 0 ) {
                p.none = model.newBoolVar( "none_" + p.booking.key() );
                objective.addTerm( p.none, p.noneCost );
            }
            if ( p.current != null ) {
                BoolVar stay = p.x.get( p.current );
                objective.add( MOVE_COST );
                if ( stay != null ) {
                    objective.addTerm( stay, -MOVE_COST );
                    model.addHint( stay, 1 );
                }
            }
        }
    }

    private boolean overlapsTargets( String bedId ) {
        return fixedByBed.getOrDefault( bedId, List.of() ).stream().anyMatch( this::isTargetStay );
    }

    /** Fixed booking overlapping any target's stay. */
    private boolean isTargetStay( ShuffleBooking f ) {
        return spec.targetKeys.stream().map( calendar::booking ).anyMatch( t -> t.overlaps( f ) );
    }

    private BoolVar newAssumption( String text, ShuffleBooking booking ) {
        BoolVar a = model.newBoolVar( "assume_" + assumptions.size() );
        assumptions.put( a, new Blocker( text, booking ) );
        model.addHint( a, 1 );
        return a;
    }

    private String describeFixed( ShuffleBooking f, ShuffleBed bed ) {
        String dates = " (" + DATE.format( f.checkin() ) + " to " + DATE.format( f.checkout() ) + ")";
        if ( f.reservationId() <= 0 ) {
            return "Block on " + bed.label() + dates + ( f.guestName().isBlank() ? "" : ": " + f.guestName() );
        }
        if ( spec.movableStart != null && false == f.overlaps( spec.movableStart, spec.movableEnd ) && false == f.pinned() ) {
            return f.label() + " on " + bed.label() + dates + " (a later/earlier stay that would also have to move)";
        }
        if ( f.checkin().isBefore( spec.windowStart ) && false == f.pinned() ) {
            return f.label() + " on " + bed.label() + dates + " starts before the search window";
        }
        if ( f.checkout().isAfter( spec.windowEnd ) && false == f.pinned() ) {
            return f.label() + " on " + bed.label() + dates + " runs past the search window";
        }
        return f.label() + " on " + bed.label() + dates + " is in-house or already arrived";
    }

    // ---------------------------------------------------------------- constraints

    private void addQuadUses() {
        if ( spec.level.compareTo( FallbackLevel.QUAD_FOR_GROUPS ) < 0 ) {
            return;
        }
        List<ShuffleBed> quads = scopeBeds.values().stream()
                .filter( b -> calendar.roomType( b.roomTypeId() ).isQuad() && false == b.isNonGuest() )
                .collect( Collectors.toList() );
        if ( quads.isEmpty() ) {
            return;
        }
        for ( List<Piece> group : piecesByGroup().values() ) {
            int type = group.get( 0 ).parent.roomTypeId();
            long members = group.stream().map( p -> p.parent.key() ).distinct().count()
                    + fixedMembers( group.get( 0 ).parent ).size();
            if ( false == targetTypes.contains( type ) || false == calendar.roomType( type ).isFourBedDorm() || members < QUAD_GUESTS ) {
                continue;
            }
            List<BoolVar> zs = new ArrayList<>();
            for ( ShuffleBed quad : quads ) {
                List<Piece> using = new ArrayList<>();
                BoolVar z = model.newBoolVar( "z_" + group.get( 0 ).parent.reservationId() + "_" + quad.id() );
                for ( Piece p : group ) {
                    if ( blockedByFixed( quad.id(), p.booking ) ) {
                        continue;
                    }
                    BoolVar x = model.newBoolVar( "q_" + p.booking.key() + "_" + quad.id() );
                    p.x.put( quad.id(), x );
                    quadMemberVars.add( x );
                    model.addImplication( x, z );
                    objective.addTerm( x, QUAD_COST );
                    using.add( p );
                }
                if ( using.isEmpty() ) {
                    continue;
                }
                zs.add( z );
                quadUses.add( new QuadUse( quad.id(), z, using ) );
                for ( LocalDate n = spec.windowStart; n.isBefore( spec.windowEnd ); n = n.plusDays( 1 ) ) {
                    LocalDate night = n;
                    List<LinearArgument> covering = using.stream()
                            .filter( p -> p.booking.overlaps( night, night.plusDays( 1 ) ) )
                            .map( p -> (LinearArgument) p.x.get( quad.id() ) )
                            .collect( Collectors.toList() );
                    if ( covering.size() > QUAD_GUESTS ) {
                        model.addLessOrEqual( LinearExpr.sum( covering.toArray( new LinearArgument[0] ) ), QUAD_GUESTS );
                    }
                }
            }
            if ( zs.size() > 1 ) {
                model.addAtMostOne( new ArrayList<>( zs ) );
            }
        }
    }

    private void addBedNightConstraints() {
        for ( Piece p : pieces ) {
            model.addExactlyOne( p.options() );
        }
        Map<String, List<Piece>> byBed = new HashMap<>();
        for ( Piece p : pieces ) {
            for ( Map.Entry<String, BoolVar> e : p.x.entrySet() ) {
                if ( false == quadMemberVars.contains( e.getValue() ) ) {
                    byBed.computeIfAbsent( e.getKey(), k -> new ArrayList<>() ).add( p );
                }
            }
        }
        for ( ShuffleBed bed : scopeBeds.values() ) {
            List<Piece> onBed = byBed.getOrDefault( bed.id(), List.of() );
            List<QuadUse> uses = quadUses.stream().filter( q -> q.bedId().equals( bed.id() ) ).collect( Collectors.toList() );
            if ( onBed.size() + uses.size() < 2 ) {
                continue;
            }
            // the largest sets of overlapping stays all include one that starts that night
            Set<LocalDate> starts = new TreeSet<>();
            onBed.forEach( p -> starts.add( p.booking.checkin() ) );
            uses.forEach( q -> q.members().forEach( p -> starts.add( p.booking.checkin() ) ) );
            for ( LocalDate n : starts ) {
                LocalDate next = n.plusDays( 1 );
                List<Literal> covering = new ArrayList<>();
                for ( Piece p : onBed ) {
                    if ( p.booking.overlaps( n, next ) ) {
                        covering.add( p.x.get( bed.id() ) );
                    }
                }
                for ( QuadUse q : uses ) {
                    LocalDate night = n;
                    if ( q.members().stream().anyMatch( p -> p.booking.overlaps( night, next ) ) ) {
                        covering.add( q.z() );
                    }
                }
                if ( covering.size() > 1 ) {
                    model.addAtMostOne( covering );
                }
            }
        }
    }

    private Map<String, List<Piece>> piecesByGroup() {
        Map<String, List<Piece>> groups = new LinkedHashMap<>();
        for ( Piece p : pieces ) {
            if ( p.parent.reservationId() > 0 ) {
                groups.computeIfAbsent( p.parent.reservationId() + ":" + p.parent.roomTypeId(), k -> new ArrayList<>() ).add( p );
            }
        }
        return groups;
    }

    private List<ShuffleBooking> fixedMembers( ShuffleBooking parent ) {
        List<ShuffleBooking> result = new ArrayList<>();
        fixedByBed.values().forEach( list -> list.stream()
                .filter( f -> f.reservationId() == parent.reservationId() && f.roomTypeId() == parent.roomTypeId() )
                .forEach( result::add ) );
        return result;
    }

    /**
     * A group uses at most {@link BedCalendar#allowedGroupRooms} rooms of its own type, members placed in another
     * room type share one room, and no more than {@code maxDownsizedPerGroup} members leave the booked type.
     */
    private void addGroupConstraints() {
        Map<String, List<ShuffleBooking>> allGroups = calendar.groups();
        for ( Map.Entry<String, List<Piece>> e : piecesByGroup().entrySet() ) {
            List<Piece> group = e.getValue();
            ShuffleBooking first = group.get( 0 ).parent;
            List<ShuffleBooking> fixed = fixedMembers( first );
            Set<String> parents = group.stream().map( p -> p.parent.key() ).collect( Collectors.toSet() );
            if ( parents.size() + fixed.size() < 2 ) {
                continue;
            }
            int type = first.roomTypeId();
            List<ShuffleBooking> members = new ArrayList<>( allGroups.getOrDefault( e.getKey(), List.of() ) );
            members.removeIf( m -> false == m.overlaps( spec.windowStart, spec.windowEnd ) );
            int allowed = calendar.allowedGroupRooms( members );

            Map<String, BoolVar> ownRooms = new LinkedHashMap<>();
            Map<String, BoolVar> otherRooms = new LinkedHashMap<>();
            Map<String, LinearExprBuilder> countByRoom = new LinkedHashMap<>();
            List<LinearArgument> leftType = new ArrayList<>();
            for ( ShuffleBooking f : fixed ) {
                ShuffleBed bed = calendar.bed( calendar.currentAssignment().get( f.key() ) );
                BoolVar y = ownRooms.computeIfAbsent( bed.room(), r -> model.newBoolVar( "y_" + e.getKey() + "_" + r ) );
                model.addEquality( y, 1 );
                countByRoom.computeIfAbsent( bed.room(), r -> LinearExpr.newBuilder() ).add( 1 );
            }
            for ( Piece p : group ) {
                for ( Map.Entry<String, BoolVar> opt : p.x.entrySet() ) {
                    if ( quadMemberVars.contains( opt.getValue() ) ) {
                        continue;
                    }
                    ShuffleBed bed = scopeBeds.get( opt.getKey() );
                    if ( bed.roomTypeId() == type ) {
                        BoolVar y = ownRooms.computeIfAbsent( bed.room(), r -> model.newBoolVar( "y_" + e.getKey() + "_" + r ) );
                        model.addImplication( opt.getValue(), y );
                        countByRoom.computeIfAbsent( bed.room(), r -> LinearExpr.newBuilder() ).add( opt.getValue() );
                    }
                    else {
                        BoolVar y = otherRooms.computeIfAbsent( bed.room(), r -> model.newBoolVar( "o_" + e.getKey() + "_" + r ) );
                        model.addImplication( opt.getValue(), y );
                        leftType.add( opt.getValue() );
                    }
                }
            }
            LinearExpr ownRoomCount = LinearExpr.sum( ownRooms.values().toArray( new LinearArgument[0] ) );
            if ( spec.relaxation == Relaxation.GROUP_SPLIT ) {
                BoolVar split = model.newBoolVar( "split_" + e.getKey() );
                groupSplitVars.add( split );
                model.addLessOrEqual( ownRoomCount, LinearExpr.newBuilder().add( allowed ).add( split ).build() );
                addGroupSplitCost( e.getKey(), split, countByRoom, parents.size() + fixed.size(), type );
            }
            // relaxing a consolidated group's rule just means leaving it split, which explains nothing
            else if ( spec.explain && false == calendar.consolidateGroups().contains( e.getKey() ) ) {
                BoolVar a = newAssumption( "Group " + first.reservationId() + " (" + ( parents.size() + fixed.size() )
                        + " beds) must stay in " + ( allowed == 1 ? "one room" : allowed + " rooms" )
                        + currentRoomsText( members ), first );
                model.addLessOrEqual( ownRoomCount, allowed ).onlyEnforceIf( a );
            }
            else {
                model.addLessOrEqual( ownRoomCount, allowed );
            }
            if ( otherRooms.size() > 1 ) {
                model.addLessOrEqual( LinearExpr.sum( otherRooms.values().toArray( new LinearArgument[0] ) ), 1 );
            }
            if ( false == leftType.isEmpty() && spec.relaxation != Relaxation.OTHER_ROOM_TYPE
                    && leftType.size() > spec.maxDownsizedPerGroup ) {
                model.addLessOrEqual( LinearExpr.sum( leftType.toArray( new LinearArgument[0] ) ), spec.maxDownsizedPerGroup );
            }
        }
        if ( groupSplitVars.size() > 1 ) {
            model.addAtMostOne( new ArrayList<>( groupSplitVars ) );
        }
    }

    /**
     * Each {@link BedCalendar.Chain} lands on one bed: both halves placed on the same bed, or the movable half on the
     * bed of a fixed (e.g. in-house) half. Chains with neither half in the model are left alone.
     */
    private void addChainConstraints() {
        if ( spec.splitTargets || calendar.chains().isEmpty() ) {
            return;
        }
        Map<String, Piece> byKey = new HashMap<>();
        pieces.forEach( p -> byKey.put( p.booking.key(), p ) );
        for ( BedCalendar.Chain c : calendar.chains() ) {
            Piece a = byKey.get( c.first() );
            Piece b = byKey.get( c.second() );
            if ( a != null && b != null ) {
                Set<String> bedIds = new LinkedHashSet<>( a.x.keySet() );
                bedIds.addAll( b.x.keySet() );
                for ( String bedId : bedIds ) {
                    BoolVar xa = a.x.get( bedId );
                    BoolVar xb = b.x.get( bedId );
                    if ( xa != null && xb != null ) {
                        model.addEquality( xa, xb );
                    }
                    else {
                        model.addEquality( xa != null ? xa : xb, 0 );
                    }
                }
            }
            else if ( a != null || b != null ) {
                Piece movable = a != null ? a : b;
                String fixedBed = calendar.currentAssignment().get( a != null ? c.second() : c.first() );
                if ( fixedBed == null ) {
                    continue;
                }
                for ( Map.Entry<String, BoolVar> opt : movable.x.entrySet() ) {
                    model.addEquality( opt.getValue(), opt.getKey().equals( fixedBed ) ? 1 : 0 );
                }
            }
        }
    }

    private String currentRoomsText( List<ShuffleBooking> members ) {
        Set<String> rooms = new LinkedHashSet<>();
        for ( ShuffleBooking m : members ) {
            ShuffleBed bed = calendar.bed( calendar.currentAssignment().get( m.key() ) );
            if ( bed != null ) {
                rooms.add( bed.room() );
            }
        }
        return rooms.isEmpty() ? "" : " (now in room " + String.join( ", ", rooms ) + ")";
    }

    /**
     * Splitting a group costs less the more of its room it fills (it frees more beds), plus a little per guest of
     * imbalance so 6 in a 10-bed dorm becomes 3 and 3 rather than 5 and 1.
     */
    private void addGroupSplitCost( String groupKey, BoolVar split, Map<String, LinearExprBuilder> countByRoom, int size, int type ) {
        int roomSize = Math.max( 1, calendar.roomType( type ).bedsPerRoom() );
        long splitCost = Math.max( 10, 40 - ( 20L * size ) / roomSize );
        objective.addTerm( split, splitCost );
        if ( countByRoom.isEmpty() ) {
            return;
        }
        IntVar maxCount = model.newIntVar( 0, size, "max_" + groupKey );
        model.addMaxEquality( maxCount, countByRoom.values().stream().map( LinearExprBuilder::build ).collect( Collectors.toList() ) );
        IntVar imbalance = model.newIntVar( 0, size, "imbalance_" + groupKey );
        int half = ( size + 1 ) / 2;
        // imbalance >= maxCount - half, only when split
        model.addGreaterOrEqual( imbalance, LinearExpr.newBuilder()
                .add( maxCount ).addTerm( split, size ).add( -half - size ).build() );
        objective.addTerm( imbalance, 3 );
    }

    /** Consecutive nights of a split target on different beds / rooms. */
    private void addSplitCosts() {
        if ( false == spec.splitTargets ) {
            return;
        }
        Map<String, List<Piece>> byParent = new LinkedHashMap<>();
        pieces.stream().filter( p -> p.target ).forEach( p -> byParent.computeIfAbsent( p.parent.key(), k -> new ArrayList<>() ).add( p ) );
        for ( List<Piece> nights : byParent.values() ) {
            for ( int i = 0; i + 1 < nights.size(); i++ ) {
                Piece a = nights.get( i );
                Piece b = nights.get( i + 1 );
                List<LinearArgument> sameBed = new ArrayList<>();
                for ( String bedId : a.x.keySet() ) {
                    if ( b.x.containsKey( bedId ) ) {
                        BoolVar s = model.newBoolVar( "sb_" + a.booking.key() + "_" + bedId );
                        model.addImplication( s, a.x.get( bedId ) );
                        model.addImplication( s, b.x.get( bedId ) );
                        sameBed.add( s );
                    }
                }
                Set<String> rooms = new LinkedHashSet<>();
                a.x.keySet().forEach( id -> rooms.add( scopeBeds.get( id ).room() ) );
                List<LinearArgument> sameRoom = new ArrayList<>();
                for ( String room : rooms ) {
                    LinearExprBuilder inA = LinearExpr.newBuilder();
                    LinearExprBuilder inB = LinearExpr.newBuilder();
                    a.x.forEach( ( id, v ) -> {
                        if ( scopeBeds.get( id ).room().equals( room ) ) {
                            inA.add( v );
                        }
                    } );
                    b.x.forEach( ( id, v ) -> {
                        if ( scopeBeds.get( id ).room().equals( room ) ) {
                            inB.add( v );
                        }
                    } );
                    BoolVar s = model.newBoolVar( "sr_" + a.booking.key() + "_" + room );
                    model.addLessOrEqual( s, inA.build() );
                    model.addLessOrEqual( s, inB.build() );
                    sameRoom.add( s );
                }
                // cost = 1000 * (1 - sameBed) + 200 * (1 - sameRoom)
                objective.add( SPLIT_BED_CHANGE_COST + SPLIT_ROOM_CHANGE_COST );
                sameBed.forEach( s -> objective.addTerm( s, -SPLIT_BED_CHANGE_COST ) );
                sameRoom.forEach( s -> objective.addTerm( s, -SPLIT_ROOM_CHANGE_COST ) );
            }
        }
    }

    // ---------------------------------------------------------------- result

    private Result buildResult( CpSolver solver ) {
        Map<String, String> chosen = new HashMap<>();
        for ( Piece p : pieces ) {
            String bed = null;
            for ( Map.Entry<String, BoolVar> e : p.x.entrySet() ) {
                if ( solver.booleanValue( e.getValue() ) ) {
                    bed = e.getKey();
                    break;
                }
            }
            chosen.put( p.booking.key(), bed );
        }
        BedCalendar resultCalendar = calendar;
        Set<String> relaxed = new LinkedHashSet<>();
        Map<String, String> assignment;
        if ( spec.splitTargets ) {
            resultCalendar = splitCalendar( chosen, relaxed );
            assignment = resultCalendar.copyCurrentAssignment();
            for ( Map.Entry<String, String> e : chosen.entrySet() ) {
                if ( resultCalendar.booking( e.getKey() ) != null ) {
                    putOrRemove( assignment, e.getKey(), e.getValue() );
                }
            }
            for ( ShuffleBooking seg : resultCalendar.bookings() ) {
                if ( seg.key().contains( "#" ) ) {
                    assignment.put( seg.key(), segmentBeds.get( seg.key() ) );
                }
            }
        }
        else {
            assignment = calendar.copyCurrentAssignment();
            chosen.forEach( ( k, v ) -> putOrRemove( assignment, k, v ) );
        }
        for ( Map.Entry<String, String> e : assignment.entrySet() ) {
            ShuffleBooking b = resultCalendar.booking( e.getKey() );
            ShuffleBed bed = resultCalendar.bed( e.getValue() );
            if ( b != null && bed != null && bed.roomTypeId() != b.roomTypeId() ) {
                relaxed.add( b.key() );
            }
        }
        boolean groupSplit = groupSplitVars.stream().anyMatch( solver::booleanValue );
        return new Result( Outcome.FOUND, resultCalendar, assignment, Math.round( solver.objectiveValue() ), relaxed,
                groupSplit, List.of() );
    }

    private static void putOrRemove( Map<String, String> assignment, String key, String bed ) {
        if ( bed == null ) {
            assignment.remove( key );
        }
        else {
            assignment.put( key, bed );
        }
    }

    private final Map<String, String> segmentBeds = new HashMap<>();

    /** Copy of the calendar with each split target replaced by one booking per run of nights on the same bed. */
    private BedCalendar splitCalendar( Map<String, String> chosen, Set<String> relaxed ) {
        BedCalendar copy = new BedCalendar( calendar.beds() );
        copy.consolidate( calendar.consolidateGroups(), List.of() );
        Map<String, List<Piece>> nightsByTarget = new LinkedHashMap<>();
        pieces.stream().filter( p -> p.target ).forEach( p -> nightsByTarget.computeIfAbsent( p.parent.key(), k -> new ArrayList<>() ).add( p ) );
        for ( ShuffleBooking b : calendar.bookings() ) {
            List<Piece> nights = nightsByTarget.get( b.key() );
            if ( nights == null ) {
                copy.addBooking( b, calendar.currentAssignment().get( b.key() ) );
                continue;
            }
            int segment = 0;
            int i = 0;
            while ( i < nights.size() ) {
                String bed = chosen.get( nights.get( i ).booking.key() );
                int j = i;
                while ( j + 1 < nights.size() && Objects.equals( chosen.get( nights.get( j + 1 ).booking.key() ), bed ) ) {
                    j++;
                }
                ShuffleBooking seg = new ShuffleBooking( b.key() + "#" + ( ++segment ), b.reservationId(), b.guestName(),
                        b.roomTypeId(), nights.get( i ).booking.checkin(), nights.get( j ).booking.checkout(), false );
                // segments of an assigned target start on its bed, so only the nights that change bed show as moves
                copy.addBooking( seg, calendar.currentAssignment().get( b.key() ) );
                segmentBeds.put( seg.key(), bed );
                relaxed.add( seg.key() );
                i = j + 1;
            }
        }
        return copy;
    }
}
