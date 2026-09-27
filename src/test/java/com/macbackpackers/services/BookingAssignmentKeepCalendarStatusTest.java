package com.macbackpackers.services;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

import org.junit.jupiter.api.Test;

import com.macbackpackers.beans.BookingAssignment;

public class BookingAssignmentKeepCalendarStatusTest {

    @Test
    public void earlyCheckoutIsNotRevertedToConfirmed() {
        BookingAssignment rest = row( "confirmed", "N" );
        BookingAssignmentEnrichService.keepCalendarBedStatus( rest, row( "checked_out", "N" ) );
        assertThat( rest.getBedStatus(), is( "checked_out" ) );
        assertThat( rest.getInHouseYn(), is( "N" ) );
    }

    @Test
    public void checkedOutIsNotRevertedToCheckedIn() {
        BookingAssignment rest = row( "checked_in", "Y" );
        BookingAssignmentEnrichService.keepCalendarBedStatus( rest, row( "checked_out", "N" ) );
        assertThat( rest.getBedStatus(), is( "checked_out" ) );
        assertThat( rest.getInHouseYn(), is( "N" ) );
    }

    @Test
    public void checkedInIsNotRevertedToConfirmed() {
        BookingAssignment rest = row( "confirmed", "N" );
        BookingAssignmentEnrichService.keepCalendarBedStatus( rest, row( "checked_in", "Y" ) );
        assertThat( rest.getBedStatus(), is( "checked_in" ) );
        assertThat( rest.getInHouseYn(), is( "Y" ) );
    }

    @Test
    public void forwardChangesAreKept() {
        BookingAssignment checkIn = row( "checked_in", "Y" );
        BookingAssignmentEnrichService.keepCalendarBedStatus( checkIn, row( "confirmed", "N" ) );
        assertThat( checkIn.getBedStatus(), is( "checked_in" ) );

        BookingAssignment checkOut = row( "checked_out", "N" );
        BookingAssignmentEnrichService.keepCalendarBedStatus( checkOut, row( "checked_in", "Y" ) );
        assertThat( checkOut.getBedStatus(), is( "checked_out" ) );

        BookingAssignment noShow = row( "no_show", "N" );
        BookingAssignmentEnrichService.keepCalendarBedStatus( noShow, row( "confirmed", "N" ) );
        assertThat( noShow.getBedStatus(), is( "no_show" ) );

        BookingAssignment fresh = row( "confirmed", "N" );
        BookingAssignmentEnrichService.keepCalendarBedStatus( fresh, null );
        assertThat( fresh.getBedStatus(), is( "confirmed" ) );
    }

    private static BookingAssignment row( String status, String inHouseYn ) {
        BookingAssignment a = new BookingAssignment();
        a.setBedStatus( status );
        a.setInHouseYn( inHouseYn );
        return a;
    }
}
