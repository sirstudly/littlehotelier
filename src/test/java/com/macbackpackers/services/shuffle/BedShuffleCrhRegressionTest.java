package com.macbackpackers.services.shuffle;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.startsWith;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import com.macbackpackers.beans.BookingAssignment;

/**
 * Replays CRH's 28-Sep-2026 03:35 calendar (guest names replaced by reservation ids), where staff couldn't place
 * Reservation 187493873 in a 12-bed MX dorm without a bed change, plus synthetic group cases.
 */
public class BedShuffleCrhRegressionTest {

    private static final String ROOMS = "/shuffle/crh_rooms_20260928.tsv";
    private static final String ASSIGNMENTS = "/shuffle/crh_assignments_20260928_0335.tsv";
    private static final LocalDate TODAY = LocalDate.of( 2026, 9, 28 );

    private static final long MX_GUEST = 187493873L;
    private static final long F_GUEST = 186826115L;
    private static final long BLOCKER_A = 187425969L;
    private static final long BLOCKER_B = 187101316L;

    private static ShuffleSuggestion mxGuest;
    private static BedCalendar mxGuestCalendar;

    private static BedCalendar crh( long... excludedReservations ) {
        Set<Long> excluded = Arrays.stream( excludedReservations ).boxed().collect( Collectors.toSet() );
        return ShuffleDumps.calendar( ROOMS, ASSIGNMENTS, TODAY,
                ( BookingAssignment a ) -> a.getReservationId() != null && excluded.contains( a.getReservationId() ) );
    }

    private static synchronized ShuffleSuggestion mxGuest() {
        if ( mxGuest == null ) {
            mxGuestCalendar = crh();
            mxGuest = BedShuffleService.suggest( mxGuestCalendar, MX_GUEST,
                    new BedShuffleService.Options().today( TODAY ).maxAlternatives( 6 ) );
            System.out.println( mxGuest.describe() );
        }
        return mxGuest;
    }

    @Test
    public void currentCalendarValidates() {
        BedCalendar calendar = crh();
        assertThat( calendar.validate( calendar.copyCurrentAssignment() ), is( empty() ) );
    }

    @Test
    public void staffDormBookingsArePinned() {
        BedCalendar calendar = crh();
        Map<String, String> current = calendar.currentAssignment();
        List<ShuffleBooking> onStaffBeds = calendar.bookings().stream()
                .filter( b -> current.get( b.key() ) != null && calendar.bed( current.get( b.key() ) ).isNonGuest() )
                .collect( Collectors.toList() );
        assertThat( onStaffBeds, is( not( empty() ) ) );
        onStaffBeds.forEach( b -> assertThat( b.label(), b.pinned(), is( true ) ) );
    }

    @Test
    public void mxGuestIsImpossibleWithinTheRules() {
        ShuffleSuggestion s = mxGuest();
        assertThat( s.getStatus(), is( ShuffleSuggestion.Status.INFEASIBLE ) );
        assertThat( s.getReason().overbookedNights(), is( empty() ) );
        assertThat( s.getReason().blockers(), is( not( empty() ) ) );
        assertThat( mxGuestCalendar.currentAssignment().get( "241548942" ), is( (String) null ) );
    }

    @Test
    public void mxGuestBestSplitIsOneBedChangeInHerRoomType() {
        ShuffleSuggestion.Option split = mxGuest().getSplit();
        assertThat( split, notNullValue() );
        assertThat( split.label(), is( "1 bed change" ) );
        Set<ShuffleBed> herBeds = split.moves().stream()
                .filter( m -> m.booking().reservationId() == MX_GUEST && m.toBed() != null )
                .map( ShuffleMove::toBed )
                .collect( Collectors.toSet() );
        assertThat( herBeds, hasSize( 2 ) );
        herBeds.forEach( b -> assertThat( b.label(), b.roomTypeId(), is( 112188 ) ) );
    }

    @Test
    public void mxGuestAlternativesAreRankedWithGenderCheckNotes() {
        List<ShuffleSuggestion.Option> alternatives = mxGuest().getAlternatives();
        assertThat( alternatives.size() >= 3, is( true ) );
        assertThat( alternatives.get( 0 ).relaxation(), is( Relaxation.STAFF_LT_MIXED ) );
        List<Relaxation> order = alternatives.stream().map( ShuffleSuggestion.Option::relaxation ).collect( Collectors.toList() );
        List<Relaxation> sorted = new ArrayList<>( order );
        sorted.sort( null );
        assertThat( order, is( sorted ) );
        assertThat( order, hasItem( Relaxation.GROUP_SPLIT ) );
        assertThat( order, hasItem( Relaxation.DISPLACE ) );
        for ( ShuffleSuggestion.Option alt : alternatives ) {
            if ( alt.relaxation() == Relaxation.STAFF_OTHER ) {
                assertThat( alt.notes(), hasItem( startsWith( "Check " ) ) );
            }
        }
    }

    @Test
    public void femaleGuestHasNoMixedOrMaleOption() {
        BedCalendar calendar = crh();
        ShuffleSuggestion s = BedShuffleService.suggest( calendar, F_GUEST, new BedShuffleService.Options().today( TODAY ) );
        System.out.println( s.describe() );
        assertThat( s.getStatus(), is( ShuffleSuggestion.Status.INFEASIBLE ) );
        assertThat( s.getAlternatives().stream().map( ShuffleSuggestion.Option::relaxation ).collect( Collectors.toList() ),
                hasItem( Relaxation.GROUP_SPLIT ) );
        for ( ShuffleSuggestion.Option alt : s.getAlternatives() ) {
            for ( ShuffleMove m : alt.moves() ) {
                if ( m.booking().reservationId() == F_GUEST && m.toBed() != null ) {
                    assertThat( alt.label() + ": " + m.describe(),
                            calendar.roomType( m.toBed().roomTypeId() ).gender(), is( RoomTypeInfo.Gender.FEMALE ) );
                }
            }
        }
    }

    /** What staff effectively did: one blocker came off the calendar and the other went to a staff dorm bed. */
    @Test
    public void mxGuestFitsOnceBlockersAreOffTheDorm() {
        BedCalendar calendar = crh( BLOCKER_A, BLOCKER_B );
        ShuffleSuggestion s = BedShuffleService.suggest( calendar, MX_GUEST, new BedShuffleService.Options().today( TODAY ) );
        System.out.println( s.describe() );
        assertThat( s.getStatus(), is( ShuffleSuggestion.Status.FOUND ) );
        assertThat( s.getLevel(), is( FallbackLevel.SAME_TYPE ) );
        assertThat( calendar.currentAssignment().get( "241548942" ), notNullValue() );
        for ( ShuffleMove m : s.getMoves() ) {
            if ( m.toBed() != null ) {
                assertThat( m.describe(), m.toBed().isNonGuest(), is( false ) );
            }
        }
    }

    /** A group of 6 that fits no single 10-bed room splits 3 + 3 rather than 5 + 1. */
    @Test
    public void groupOfSixSplitsEvenly() {
        List<ShuffleBed> beds = new ArrayList<>();
        for ( String room : List.of( "P", "Q" ) ) {
            for ( int i = 1; i <= 10; i++ ) {
                beds.add( new ShuffleBed( room + i, room, String.valueOf( i ), 10, "MX", 10 ) );
            }
        }
        BedCalendar calendar = new BedCalendar( beds );
        LocalDate from = TODAY.plusDays( 5 );
        LocalDate to = from.plusDays( 2 );
        for ( int i = 1; i <= 5; i++ ) {
            calendar.addBooking( new ShuffleBooking( "p" + i, 100 + i, "Guest p" + i, 10, from, to, true ), "P" + i );
            calendar.addBooking( new ShuffleBooking( "q" + i, 200 + i, "Guest q" + i, 10, from, to, true ), "Q" + i );
        }
        for ( int i = 1; i <= 6; i++ ) {
            calendar.addBooking( new ShuffleBooking( "g" + i, 1, "Guest g", 10, from, to, false ), null );
        }
        ShuffleSuggestion s = BedShuffleService.suggest( calendar, 1, new BedShuffleService.Options().today( TODAY ) );
        System.out.println( s.describe() );
        assertThat( s.getStatus(), is( ShuffleSuggestion.Status.INFEASIBLE ) );
        assertThat( s.getAlternatives(), hasSize( 1 ) );
        ShuffleSuggestion.Option alt = s.getAlternatives().get( 0 );
        assertThat( alt.relaxation(), is( Relaxation.GROUP_SPLIT ) );
        assertThat( alt.notes(), hasItem( containsString( "across rooms P (3) and Q (3)" ) ) );
    }
}
