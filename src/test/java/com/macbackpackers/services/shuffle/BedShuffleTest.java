package com.macbackpackers.services.shuffle;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.startsWith;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Test;

public class BedShuffleTest {

    private static final int ROOM_TYPE_14MX = 112528;
    private static final long TARGET = 187460991L;
    private static final String TARGET_KEY = TARGET + ":-";
    private static final String HIGHLAND_TOFFEE = "112528-6";
    private static final String NEEPS = "112528-10";
    private static final String TATTIES = "112528-11";

    private static final String[] BED_NAMES = { "01- Steak n' Ale", "02- Baked Potato", "03- Haggis",
            "04- Deep Fried Mars", "05- Blood Pudding", "06- Scotch Egg", "07- Highland Toffee", "08- Nessie Burger",
            "09- Fish n' Chips", "10- Venison", "11- Neeps", "12- Tatties", "13- Porridge", "14- Smoked Salmon" };

    @Test
    public void targetFitsExactlyAtCapacity() throws IOException {
        BedCalendar calendar = loadCrhRoom25();
        RoomTypeFeasibility f = RoomTypeFeasibility.check( calendar, ROOM_TYPE_14MX, d( 11 ), d( 13 ) );
        assertThat( f.getCapacity(), is( 14 ) );
        assertThat( f.getDemandByNight().get( d( 11 ) ), is( 14 ) );
        assertThat( f.isFeasible(), is( true ) );
    }

    @Test
    public void extraBookingMakesRoomTypeOverbooked() throws IOException {
        BedCalendar calendar = loadCrhRoom25();
        calendar.addBooking( booking( "extra", TARGET, ROOM_TYPE_14MX, d( 11 ), d( 12 ), false ), null );
        ShuffleSuggestion s = BedShuffleService.suggest( calendar, TARGET );
        assertThat( s.getStatus(), is( ShuffleSuggestion.Status.OVERBOOKED ) );
        assertThat( s.getReason().overbookedNights(), contains( d( 11 ) ) );
        assertThat( s.getReason().blockers(), contains( "11-Oct: 15 guests for 14 beds in 14-bed MX dorm (112528)" ) );
        assertThat( s.getSplit(), nullValue() );
        // no staff dorms or other room types in this calendar; only bumping someone else can make room
        assertThat( s.getAlternatives(), hasSize( 1 ) );
        assertThat( s.getAlternatives().get( 0 ).relaxation(), is( Relaxation.DISPLACE ) );
        assertThat( s.getAlternatives().get( 0 ).notes(), hasItem( startsWith( "Leaves " ) ) );
    }

    @Test
    public void manualSequenceFromStaffIsValid() throws IOException {
        BedCalendar calendar = loadCrhRoom25();
        Map<String, String> target = calendar.copyCurrentAssignment();
        target.put( "187170106:" + HIGHLAND_TOFFEE, NEEPS );
        target.put( "187216199:" + HIGHLAND_TOFFEE, TATTIES );
        target.put( "187286098:" + NEEPS, HIGHLAND_TOFFEE );
        target.put( TARGET_KEY, HIGHLAND_TOFFEE );
        assertThat( calendar.validate( target ), is( empty() ) );

        List<ShuffleMove> moves = MoveSequencer.sequence( calendar, target );
        replay( calendar, moves );
        List<String> described = moves.stream().map( ShuffleMove::describe ).collect( Collectors.toList() );
        assertThat( described, contains(
                "Move 187216199 (Guest 187216199) from 25-07- Highland Toffee to 25-12- Tatties (15-Oct to 16-Oct)",
                "Unassign 187170106 (Guest 187170106) from 25-07- Highland Toffee (12-Oct to 14-Oct)",
                "Assign 187460991 (Guest 187460991) to 25-07- Highland Toffee (11-Oct to 13-Oct)",
                "Move 187286098 (Guest 187286098) from 25-11- Neeps to 25-07- Highland Toffee (13-Oct to 16-Oct)",
                "Assign 187170106 (Guest 187170106) to 25-11- Neeps (12-Oct to 14-Oct)" ) );
    }

    @Test
    public void searchFindsShuffleForTarget() throws IOException {
        BedCalendar calendar = loadCrhRoom25();
        ShuffleSuggestion s = BedShuffleService.suggest( calendar, TARGET );
        System.out.println( s.describe() );

        assertThat( s.getStatus(), is( ShuffleSuggestion.Status.FOUND ) );
        assertThat( calendar.currentAssignment().get( TARGET_KEY ), notNullValue() );
        long existingMoved = s.getMoves().stream()
                .filter( m -> m.type() != ShuffleMove.Type.UNASSIGN && m.booking().reservationId() != TARGET )
                .count();
        assertThat( (int) existingMoved, lessThanOrEqualTo( 3 ) );
    }

    @Test
    public void solverResultReplaysOnFreeBeds() throws IOException {
        BedCalendar calendar = loadCrhRoom25();
        CpSatShuffleSolver.Result r = CpSatShuffleSolver.solve( calendar, new CpSatShuffleSolver.Spec()
                .targets( Set.of( TARGET_KEY ) ).window( d( 1 ), d( 31 ) ) );
        assertThat( r.outcome(), is( CpSatShuffleSolver.Outcome.FOUND ) );
        assertThat( r.objective(), lessThanOrEqualTo( 2L ) );
        assertThat( calendar.validate( r.assignment() ), is( empty() ) );
        replay( calendar, MoveSequencer.sequence( calendar, r.assignment() ) );
    }

    @Test
    public void competingUnassignedBookingIsReported() throws IOException {
        BedCalendar calendar = loadCrhRoom25();
        calendar.addBooking( booking( "other", 999L, ROOM_TYPE_14MX, d( 11 ), d( 12 ), false ), null );
        ShuffleSuggestion s = BedShuffleService.suggest( calendar, TARGET );
        System.out.println( s.describe() );
        assertThat( s.getStatus(), is( ShuffleSuggestion.Status.FOUND ) );
        assertThat( s.getNotes(), contains( "Unassigned 999 (Guest other) (11-Oct to 12-Oct) still has no bed" ) );
    }

    @Test
    public void unassignedGroupMemberStaysInGroupRoom() {
        BedCalendar calendar = twoRoomCalendar( false );
        ShuffleSuggestion s = BedShuffleService.suggest( calendar, 1L );
        System.out.println( s.describe() );
        assertThat( s.getStatus(), is( ShuffleSuggestion.Status.FOUND ) );
        assertThat( calendar.bed( calendar.currentAssignment().get( "g3" ) ).room(), is( "A" ) );
        assertThat( calendar.currentAssignment().get( "x" ), is( "B1" ) );
        assertThat( s.getMoves(), hasSize( 2 ) );
    }

    @Test
    public void pinnedGuestIsNeverMoved() {
        BedCalendar calendar = twoRoomCalendar( true );
        ShuffleSuggestion s = BedShuffleService.suggest( calendar, 1L );
        System.out.println( s.describe() );
        assertThat( s.getStatus(), is( ShuffleSuggestion.Status.INFEASIBLE ) );
        assertThat( calendar.currentAssignment().get( "x" ), is( "A3" ) );
        // either X giving way or the group splitting would let the third bed in
        assertThat( s.getReason().blockers(), contains( anyOf(
                containsString( "is in-house or already arrived" ),
                startsWith( "Group 1 (3 beds) must stay in one room" ) ) ) );
        // the only way in is the group's third bed going to room B
        assertThat( s.getAlternatives(), hasSize( 1 ) );
        assertThat( s.getAlternatives().get( 0 ).relaxation(), is( Relaxation.GROUP_SPLIT ) );
        for ( ShuffleMove m : s.getAlternatives().get( 0 ).moves() ) {
            assertThat( m.booking().key(), is( not( "x" ) ) );
        }
    }

    /**
     * Room A (3 beds) holds two beds of group 1 and guest X; room B (1 bed) is free. The group's third bed is
     * unassigned: it must go in room A, so X has to move to B.
     */
    private static BedCalendar twoRoomCalendar( boolean xPinned ) {
        BedCalendar calendar = new BedCalendar( List.of(
                new ShuffleBed( "A1", "A", "1", 1 ), new ShuffleBed( "A2", "A", "2", 1 ),
                new ShuffleBed( "A3", "A", "3", 1 ), new ShuffleBed( "B1", "B", "1", 1 ) ) );
        calendar.addBooking( booking( "g1", 1L, 1, d( 1 ), d( 3 ), false ), "A1" );
        calendar.addBooking( booking( "g2", 1L, 1, d( 1 ), d( 3 ), false ), "A2" );
        calendar.addBooking( booking( "g3", 1L, 1, d( 1 ), d( 3 ), false ), null );
        calendar.addBooking( booking( "x", 2L, 1, d( 1 ), d( 3 ), xPinned ), "A3" );
        return calendar;
    }

    /** Applies {@code moves} in order, asserting each assign/move lands on a bed free for the stay. */
    private static void replay( BedCalendar calendar, List<ShuffleMove> moves ) {
        Map<String, String> working = calendar.copyCurrentAssignment();
        for ( ShuffleMove m : moves ) {
            if ( m.type() == ShuffleMove.Type.UNASSIGN ) {
                working.remove( m.booking().key() );
            }
            else {
                assertThat( m.describe(), calendar.occupants( m.toBed().id(), m.booking(), working ), is( empty() ) );
                working.put( m.booking().key(), m.toBed().id() );
            }
        }
    }

    private static BedCalendar loadCrhRoom25() throws IOException {
        List<ShuffleBed> beds = new ArrayList<>();
        for ( int i = 0; i < BED_NAMES.length; i++ ) {
            beds.add( new ShuffleBed( ROOM_TYPE_14MX + "-" + i, "25", BED_NAMES[i], ROOM_TYPE_14MX ) );
        }
        BedCalendar calendar = new BedCalendar( beds );
        Map<String, String[]> rows = new LinkedHashMap<>();
        try ( InputStream in = BedShuffleTest.class.getResourceAsStream( "/shuffle/crh_112528_oct2026.tsv" ) ) {
            for ( String line : IOUtils.readLines( in, StandardCharsets.UTF_8 ) ) {
                if ( line.isBlank() || line.startsWith( "#" ) ) {
                    continue;
                }
                String[] f = line.split( "\t" );
                rows.put( f[0] + ":" + f[2], f );
            }
        }
        rows.forEach( ( key, f ) -> calendar.addBooking(
                new ShuffleBooking( key, Long.parseLong( f[0] ), f[1], ROOM_TYPE_14MX,
                        LocalDate.parse( f[3] ), LocalDate.parse( f[4] ), false ),
                "-".equals( f[2] ) ? null : f[2] ) );
        return calendar;
    }

    private static ShuffleBooking booking( String key, long reservationId, int roomTypeId, LocalDate from,
            LocalDate to, boolean pinned ) {
        return new ShuffleBooking( key, reservationId, "Guest " + key, roomTypeId, from, to, pinned );
    }

    private static LocalDate d( int dayOfOctober ) {
        return LocalDate.of( 2026, 10, dayOfOctober );
    }
}
