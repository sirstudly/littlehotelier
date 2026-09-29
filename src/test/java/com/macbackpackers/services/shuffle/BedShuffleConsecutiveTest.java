package com.macbackpackers.services.shuffle;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;
import static org.hamcrest.Matchers.containsString;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

/** Two 3-bed dorms, A and B, of one room type; reservation 1 checks out on the 3rd as reservation 2 checks in. */
public class BedShuffleConsecutiveTest {

    private static final BedShuffleService.Options OPTIONS = new BedShuffleService.Options();

    @Test
    public void nextBookingJoinsInHouseGuestsBed() {
        BedCalendar calendar = calendar();
        calendar.addBooking( booking( "left", 1L, d( 1 ), d( 3 ), true ), "A1" );
        calendar.addBooking( booking( "right", 2L, d( 3 ), d( 5 ), false ), "B1" );

        ShuffleSuggestion s = BedShuffleService.suggestConsecutive( calendar, 1L, 2L, 1, OPTIONS );
        System.out.println( s.describe() );
        assertThat( s.getStatus(), is( ShuffleSuggestion.Status.FOUND ) );
        assertThat( calendar.currentAssignment().get( "left" ), is( "A1" ) );
        assertThat( calendar.currentAssignment().get( "right" ), is( "A1" ) );
        assertThat( s.getNotes(), hasItem( startsWith( "Keeps " ) ) );
    }

    @Test
    public void bothBookingsMoveToABedNeitherIsOnNow() {
        BedCalendar calendar = calendar();
        calendar.addBooking( booking( "left", 1L, d( 1 ), d( 3 ), false ), "A1" );
        calendar.addBooking( booking( "right", 2L, d( 3 ), d( 5 ), false ), "B1" );
        calendar.addBooking( booking( "x", 3L, d( 3 ), d( 5 ), true ), "A1" );
        calendar.addBooking( booking( "y", 4L, d( 1 ), d( 3 ), true ), "B1" );
        fill( calendar, "A3", "B2", "B3" );

        ShuffleSuggestion s = BedShuffleService.suggestConsecutive( calendar, 1L, 2L, 1, OPTIONS );
        System.out.println( s.describe() );
        assertThat( s.getStatus(), is( ShuffleSuggestion.Status.FOUND ) );
        assertThat( calendar.currentAssignment().get( "left" ), is( "A2" ) );
        assertThat( calendar.currentAssignment().get( "right" ), is( "A2" ) );
        assertThat( calendar.validate( calendar.copyCurrentAssignment() ), is( empty() ) );
    }

    @Test
    public void alreadyOnOneBedNeedsNothing() {
        BedCalendar calendar = calendar();
        calendar.addBooking( booking( "left", 1L, d( 1 ), d( 3 ), false ), "A1" );
        calendar.addBooking( booking( "right", 2L, d( 3 ), d( 5 ), false ), "A1" );

        ShuffleSuggestion s = BedShuffleService.suggestConsecutive( calendar, 1L, 2L, 1, OPTIONS );
        assertThat( s.getStatus(), is( ShuffleSuggestion.Status.ALREADY_ASSIGNED ) );
        assertThat( s.getMoves(), is( empty() ) );
    }

    @Test
    public void blockedInHouseBedIsExplainedWithoutSplit() {
        BedCalendar calendar = calendar();
        calendar.addBooking( booking( "left", 1L, d( 1 ), d( 3 ), true ), "A1" );
        calendar.addBooking( booking( "right", 2L, d( 3 ), d( 5 ), false ), "B1" );
        calendar.addBooking( booking( "z", 3L, d( 3 ), d( 5 ), true ), "A1" );

        ShuffleSuggestion s = BedShuffleService.suggestConsecutive( calendar, 1L, 2L, 1, OPTIONS );
        System.out.println( s.describe() );
        assertThat( s.getStatus(), is( ShuffleSuggestion.Status.INFEASIBLE ) );
        assertThat( s.getReason().summary(), startsWith( "Could not keep 1 (Guest left)" ) );
        assertThat( s.describe(), not( containsString( "No split" ) ) );
        assertThat( calendar.currentAssignment().get( "right" ), is( "B1" ) );
    }

    @Test
    public void bothInHouseOnDifferentBedsIsExplained() {
        BedCalendar calendar = calendar();
        calendar.addBooking( booking( "left", 1L, d( 1 ), d( 3 ), true ), "A1" );
        calendar.addBooking( booking( "right", 2L, d( 3 ), d( 5 ), true ), "B1" );

        ShuffleSuggestion s = BedShuffleService.suggestConsecutive( calendar, 1L, 2L, 1, OPTIONS );
        assertThat( s.getStatus(), is( ShuffleSuggestion.Status.INFEASIBLE ) );
        assertThat( s.getReason().summary(), containsString( "both are already in-house" ) );
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

    private static ShuffleBooking booking( String key, long reservationId, LocalDate from, LocalDate to, boolean pinned ) {
        return new ShuffleBooking( key, reservationId, "Guest " + key, 1, from, to, pinned );
    }

    private static LocalDate d( int dayOfOctober ) {
        return LocalDate.of( 2026, 10, dayOfOctober );
    }
}
