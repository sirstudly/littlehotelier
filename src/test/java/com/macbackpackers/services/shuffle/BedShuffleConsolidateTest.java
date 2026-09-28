package com.macbackpackers.services.shuffle;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

/** Two 3-bed MX dorms, A and B, of one room type. */
public class BedShuffleConsolidateTest {

    private static final BedShuffleService.Options CONSOLIDATE = new BedShuffleService.Options().consolidate( true );

    @Test
    public void groupSplitAcrossRoomsIsBroughtTogether() {
        BedCalendar calendar = calendar();
        calendar.addBooking( booking( "g1", 1L, d( 1 ), d( 3 ), false ), "A1" );
        calendar.addBooking( booking( "g2", 1L, d( 1 ), d( 3 ), false ), "B1" );
        calendar.addBooking( booking( "x", 2L, d( 1 ), d( 3 ), false ), "A2" );

        ShuffleSuggestion s = BedShuffleService.suggest( calendar, 1L, CONSOLIDATE );
        System.out.println( s.describe() );
        assertThat( s.getStatus(), is( ShuffleSuggestion.Status.FOUND ) );
        assertThat( s.isConsolidation(), is( true ) );
        assertThat( room( calendar, "g1" ), is( room( calendar, "g2" ) ) );
        assertThat( calendar.validate( calendar.copyCurrentAssignment() ), is( empty() ) );
        assertThat( s.getNotes(), hasItem( startsWith( "Brings group " ) ) );
    }

    @Test
    public void pinnedMemberAnchorsTheGroupRoom() {
        BedCalendar calendar = calendar();
        calendar.addBooking( booking( "g1", 1L, d( 1 ), d( 3 ), true ), "B1" );
        calendar.addBooking( booking( "g2", 1L, d( 1 ), d( 3 ), false ), "A1" );
        calendar.addBooking( booking( "g3", 1L, d( 1 ), d( 3 ), false ), "A2" );
        calendar.addBooking( booking( "x", 2L, d( 1 ), d( 3 ), false ), "B2" );

        ShuffleSuggestion s = BedShuffleService.suggest( calendar, 1L, CONSOLIDATE );
        System.out.println( s.describe() );
        assertThat( s.getStatus(), is( ShuffleSuggestion.Status.FOUND ) );
        assertThat( calendar.currentAssignment().get( "g1" ), is( "B1" ) );
        assertThat( room( calendar, "g2" ), is( "B" ) );
        assertThat( room( calendar, "g3" ), is( "B" ) );
        assertThat( room( calendar, "x" ), is( "A" ) );
        for ( ShuffleMove m : s.getMoves() ) {
            assertThat( m.booking().key(), is( not( "g1" ) ) );
        }
    }

    @Test
    public void bedChangeWithinStayEndsOnOneBed() {
        BedCalendar calendar = calendar();
        calendar.addBooking( booking( "c1", 1L, d( 1 ), d( 3 ), false ), "A1" );
        calendar.addBooking( booking( "c2", 1L, d( 3 ), d( 5 ), false ), "A2" );
        calendar.addBooking( booking( "y", 2L, d( 3 ), d( 5 ), false ), "A1" );
        calendar.addBooking( booking( "z", 3L, d( 1 ), d( 3 ), false ), "A2" );
        fill( calendar, "A3", "B1", "B2", "B3" );

        ShuffleSuggestion s = BedShuffleService.suggest( calendar, 1L, CONSOLIDATE );
        System.out.println( s.describe() );
        assertThat( s.getStatus(), is( ShuffleSuggestion.Status.FOUND ) );
        assertThat( calendar.currentAssignment().get( "c1" ), is( calendar.currentAssignment().get( "c2" ) ) );
        assertThat( s.getNotes(), hasItem( startsWith( "Keeps " ) ) );
    }

    @Test
    public void bedChangeAfterInHouseFirstHalfContinuesOnThatBed() {
        BedCalendar calendar = calendar();
        calendar.addBooking( booking( "c1", 1L, d( 1 ), d( 3 ), true ), "A1" );
        calendar.addBooking( booking( "c2", 1L, d( 3 ), d( 5 ), false ), "A2" );
        calendar.addBooking( booking( "y", 2L, d( 3 ), d( 5 ), false ), "A1" );

        ShuffleSuggestion s = BedShuffleService.suggest( calendar, 1L, CONSOLIDATE );
        System.out.println( s.describe() );
        assertThat( s.getStatus(), is( ShuffleSuggestion.Status.FOUND ) );
        assertThat( calendar.currentAssignment().get( "c1" ), is( "A1" ) );
        assertThat( calendar.currentAssignment().get( "c2" ), is( "A1" ) );
    }

    @Test
    public void chainAlreadyOnOneBedNeedsNothing() {
        BedCalendar calendar = calendar();
        calendar.addBooking( booking( "c1", 1L, d( 1 ), d( 3 ), false ), "A1" );
        calendar.addBooking( booking( "c2", 1L, d( 3 ), d( 5 ), false ), "A1" );

        ShuffleSuggestion s = BedShuffleService.suggest( calendar, 1L, CONSOLIDATE );
        assertThat( s.getStatus(), is( ShuffleSuggestion.Status.ALREADY_ASSIGNED ) );
        assertThat( s.getMoves(), is( empty() ) );
    }

    @Test
    public void groupThatCannotBeBroughtTogetherIsExplained() {
        BedCalendar calendar = calendar();
        calendar.addBooking( booking( "g1", 1L, d( 1 ), d( 3 ), false ), "A1" );
        calendar.addBooking( booking( "g2", 1L, d( 1 ), d( 3 ), false ), "B1" );
        calendar.addBooking( booking( "p1", 2L, d( 1 ), d( 3 ), true ), "A2" );
        calendar.addBooking( booking( "p2", 3L, d( 1 ), d( 3 ), true ), "A3" );
        calendar.addBooking( booking( "p3", 4L, d( 1 ), d( 3 ), true ), "B2" );
        calendar.addBooking( booking( "p4", 5L, d( 1 ), d( 3 ), true ), "B3" );

        ShuffleSuggestion s = BedShuffleService.suggest( calendar, 1L, CONSOLIDATE );
        System.out.println( s.describe() );
        assertThat( s.getStatus(), is( ShuffleSuggestion.Status.INFEASIBLE ) );
        assertThat( s.isConsolidation(), is( true ) );
        assertThat( s.getReason().summary(), startsWith( "Could not bring group " ) );
        assertThat( calendar.currentAssignment().get( "g1" ), is( "A1" ) );
        assertThat( calendar.currentAssignment().get( "g2" ), is( "B1" ) );
    }

    /** Cloudbeds fills top to bottom, so a split group is no better than any other suggestion: it gets the full output. */
    @Test
    public void groupThatCannotBeBroughtTogetherGetsSplitOption() {
        BedCalendar calendar = calendar();
        calendar.addBooking( booking( "g1", 1L, d( 1 ), d( 3 ), false ), "A1" );
        calendar.addBooking( booking( "g2", 1L, d( 1 ), d( 3 ), false ), "B1" );
        calendar.addBooking( booking( "p1", 2L, d( 1 ), d( 3 ), true ), "A2" );
        calendar.addBooking( booking( "p2", 3L, d( 1 ), d( 3 ), true ), "A3" );
        calendar.addBooking( booking( "p3", 4L, d( 1 ), d( 2 ), true ), "B2" );
        calendar.addBooking( booking( "p4", 5L, d( 2 ), d( 3 ), true ), "B3" );

        ShuffleSuggestion s = BedShuffleService.suggest( calendar, 1L, CONSOLIDATE );
        System.out.println( s.describe() );
        assertThat( s.getStatus(), is( ShuffleSuggestion.Status.INFEASIBLE ) );
        assertThat( s.getReason().summary(), startsWith( "Could not bring group " ) );
        assertThat( s.getSplit().label(), is( "1 bed change" ) );
        // g1 joins g2 in room B: B3 the first night, then B2; g2 stays put
        assertThat( s.getSplit().moves().stream().map( ShuffleMove::describe ).toList(), everyItem( containsString( "Guest g1" ) ) );
        assertThat( calendar.currentAssignment().get( "g1" ), is( "A1" ) );
    }

    @Test
    public void unassignedBedIsStillPlacedWhenGroupCannotBeBroughtTogether() {
        BedCalendar calendar = calendar();
        calendar.addBooking( booking( "g1", 1L, d( 1 ), d( 3 ), false ), "A1" );
        calendar.addBooking( booking( "g2", 1L, d( 1 ), d( 3 ), false ), "B1" );
        calendar.addBooking( booking( "g3", 1L, d( 1 ), d( 3 ), false ), null );
        calendar.addBooking( booking( "p1", 2L, d( 1 ), d( 3 ), true ), "A2" );
        calendar.addBooking( booking( "p2", 3L, d( 1 ), d( 3 ), true ), "B2" );

        ShuffleSuggestion s = BedShuffleService.suggest( calendar, 1L, CONSOLIDATE );
        System.out.println( s.describe() );
        assertThat( s.getStatus(), is( ShuffleSuggestion.Status.FOUND ) );
        assertThat( s.isConsolidation(), is( false ) );
        assertThat( s.getNotes(), hasItem( startsWith( "Could not also bring group " ) ) );
        assertThat( calendar.currentAssignment().get( "g1" ), is( "A1" ) );
        assertThat( calendar.currentAssignment().get( "g2" ), is( "B1" ) );
    }

    @Test
    public void withoutConsolidateSplitGroupIsLeftAlone() {
        BedCalendar calendar = calendar();
        calendar.addBooking( booking( "g1", 1L, d( 1 ), d( 3 ), false ), "A1" );
        calendar.addBooking( booking( "g2", 1L, d( 1 ), d( 3 ), false ), "B1" );

        ShuffleSuggestion s = BedShuffleService.suggest( calendar, 1L );
        assertThat( s.getStatus(), is( ShuffleSuggestion.Status.ALREADY_ASSIGNED ) );
    }

    private static BedCalendar calendar() {
        return new BedCalendar( List.of(
                new ShuffleBed( "A1", "A", "1", 1 ), new ShuffleBed( "A2", "A", "2", 1 ), new ShuffleBed( "A3", "A", "3", 1 ),
                new ShuffleBed( "B1", "B", "1", 1 ), new ShuffleBed( "B2", "B", "2", 1 ), new ShuffleBed( "B3", "B", "3", 1 ) ) );
    }

    /** Pins a guest on each bed for the whole test period. */
    private static void fill( BedCalendar calendar, String... beds ) {
        long id = 100;
        for ( String bed : beds ) {
            calendar.addBooking( booking( "fill-" + bed, id++, d( 1 ), d( 5 ), true ), bed );
        }
    }

    private static String room( BedCalendar calendar, String key ) {
        return calendar.bed( calendar.currentAssignment().get( key ) ).room();
    }

    private static ShuffleBooking booking( String key, long reservationId, LocalDate from, LocalDate to, boolean pinned ) {
        return new ShuffleBooking( key, reservationId, "Guest " + key, 1, from, to, pinned );
    }

    private static LocalDate d( int dayOfOctober ) {
        return LocalDate.of( 2026, 10, dayOfOctober );
    }
}
