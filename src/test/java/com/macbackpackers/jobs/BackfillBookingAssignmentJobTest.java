package com.macbackpackers.jobs;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.macbackpackers.beans.cloudbeds.responses.Customer;

public class BackfillBookingAssignmentJobTest {

    private static final LocalDate TODAY = LocalDate.of( 2026, 9, 25 );

    @Test
    public void departedReservationNotInTableIsBackfilled() {
        assertThat( BackfillBookingAssignmentJob.shouldBackfill( customer( "1", "2024-01-07" ), Set.of(), TODAY ), is( true ) );
    }

    @Test
    public void reservationCheckingOutTodayOrLaterIsSkipped() {
        assertThat( BackfillBookingAssignmentJob.shouldBackfill( customer( "1", "2026-09-25" ), Set.of(), TODAY ), is( false ) );
        assertThat( BackfillBookingAssignmentJob.shouldBackfill( customer( "1", "2026-09-26" ), Set.of(), TODAY ), is( false ) );
    }

    @Test
    public void reservationAlreadyInTableIsSkipped() {
        assertThat( BackfillBookingAssignmentJob.shouldBackfill( customer( "1", "2024-01-07" ), Set.of( 1L ), TODAY ), is( false ) );
    }

    @Test
    public void unparseableIdOrCheckoutIsSkipped() {
        assertThat( BackfillBookingAssignmentJob.shouldBackfill( customer( "abc", "2024-01-07" ), Set.of(), TODAY ), is( false ) );
        assertThat( BackfillBookingAssignmentJob.shouldBackfill( customer( "1", null ), Set.of(), TODAY ), is( false ) );
    }

    @Test
    public void weeklyWindowsAreContiguousAndClippedToEndDate() {
        List<LocalDate[]> weeks = CreateBackfillBookingAssignmentJob.weeklyWindows(
                LocalDate.of( 2024, 1, 1 ), LocalDate.of( 2024, 1, 17 ) );

        assertThat( weeks, hasSize( 3 ) );
        assertThat( weeks.get( 0 ), arrayContaining( LocalDate.of( 2024, 1, 1 ), LocalDate.of( 2024, 1, 7 ) ) );
        assertThat( weeks.get( 1 ), arrayContaining( LocalDate.of( 2024, 1, 8 ), LocalDate.of( 2024, 1, 14 ) ) );
        assertThat( weeks.get( 2 ), arrayContaining( LocalDate.of( 2024, 1, 15 ), LocalDate.of( 2024, 1, 17 ) ) );
    }

    private static Customer customer( String id, String checkout ) {
        Customer c = new Customer();
        c.setId( id );
        c.setCheckoutDate( checkout );
        return c;
    }
}
