package com.macbackpackers.services;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;

import com.macbackpackers.beans.OccupancyVersion;

/**
 * Pure unit tests for bedsheet rules (no Spring / Mockito).
 */
public class HousekeepingStatusServiceTest {

    private final HousekeepingStatusService service = new HousekeepingStatusService();
    private final LocalDate today = LocalDate.of( 2026, 9, 14 );

    @Test
    public void emptyWhenNoOccupancy() {
        assertThat( service.computeBedsheet( null, null, null, today, 3 ),
                is( HousekeepingBedsheet.EMPTY ) );
    }

    @Test
    public void changeCheckedOutWhenDepartingAndNotInHouse() {
        OccupancyVersion o = guest( today.minusDays( 2 ), today, "checked_out", false );
        assertThat( service.computeBedsheet( o, null, null, today, 3 ),
                is( HousekeepingBedsheet.CHANGE_CHECKED_OUT ) );
    }

    @Test
    public void changeInHouseWhenDepartingAndStillCheckedIn() {
        OccupancyVersion o = guest( today.minusDays( 2 ), today, "checked_in", true );
        assertThat( service.computeBedsheet( o, null, null, today, 3 ),
                is( HousekeepingBedsheet.CHANGE_IN_HOUSE ) );
    }

    @Test
    public void roomClosureChange() {
        OccupancyVersion o = closure( today.minusDays( 1 ), today.plusDays( 1 ) );
        assertThat( service.computeBedsheet( null, null, o, today, 3 ),
                is( HousekeepingBedsheet.CHANGE_ROOM_CLOSURE ) );
        assertThat( service.computeBedsheet( o, null, o, today, 3 ),
                is( HousekeepingBedsheet.CHANGE_ROOM_CLOSURE ) );
    }

    @Test
    public void stayContinuationSuppressesChange() {
        OccupancyVersion first = guest( today.minusDays( 2 ), today, "checked_out", false );
        first.setGuestName( "Ada Lovelace" );
        OccupancyVersion next = guest( today, today.plusDays( 3 ), "confirmed", false );
        next.setGuestName( "Ada Lovelace" );
        assertThat( service.computeBedsheet( first, next, null, today, 3 ),
                is( HousekeepingBedsheet.NO_CHANGE ) );
    }

    @Test
    public void confirmedNotInHouseIsEmpty() {
        OccupancyVersion o = guest( today.minusDays( 1 ), today.plusDays( 2 ), "confirmed", false );
        assertThat( service.computeBedsheet( o, null, null, today, 3 ),
                is( HousekeepingBedsheet.EMPTY ) );
    }

    @Test
    public void nDayChangeOnThirdMorning() {
        OccupancyVersion o = guest( today.minusDays( 3 ), today.plusDays( 3 ), "checked_in", true );
        assertThat( service.computeBedsheet( o, null, null, today, 3 ),
                is( HousekeepingBedsheet.N_DAY_CHANGE ) );
    }

    @Test
    public void findStayContinuationMatchesSameGuest() {
        OccupancyVersion first = guest( today.minusDays( 2 ), today, "checked_out", false );
        first.setGuestName( "Ada" );
        first.setRoomId( "1-1" );
        OccupancyVersion next = guest( today, today.plusDays( 2 ), "confirmed", false );
        next.setGuestName( "Ada" );
        next.setRoomId( "1-1" );
        OccupancyVersion found = HousekeepingStatusService.findStayContinuation(
                first, java.util.Arrays.asList( first, next ), today );
        assertThat( found, is( next ) );
    }

    private static OccupancyVersion guest( LocalDate checkin, LocalDate checkout,
            String status, boolean inHouse ) {
        OccupancyVersion o = new OccupancyVersion();
        o.setAssignmentKey( "br-1" );
        o.setRoomId( "1-1" );
        o.setRoom( "10" );
        o.setBedName( "A" );
        o.setGuestName( "Guest" );
        o.setCheckinDate( checkin );
        o.setCheckoutDate( checkout );
        o.setBedStatus( status );
        o.setInHouse( inHouse );
        o.setSource( OccupancyVersion.SOURCE_GUEST );
        return o;
    }

    private static OccupancyVersion closure( LocalDate checkin, LocalDate checkout ) {
        OccupancyVersion o = new OccupancyVersion();
        o.setAssignmentKey( "closure:1" );
        o.setRoomId( "1-1" );
        o.setRoom( "10" );
        o.setBedName( "A" );
        o.setGuestName( "Room closure" );
        o.setCheckinDate( checkin );
        o.setCheckoutDate( checkout );
        o.setBedStatus( "out_of_service" );
        o.setInHouse( false );
        o.setSource( OccupancyVersion.SOURCE_CLOSURE );
        return o;
    }
}
