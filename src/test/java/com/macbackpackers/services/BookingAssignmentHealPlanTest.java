package com.macbackpackers.services;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.macbackpackers.beans.BookingAssignment;
import com.macbackpackers.beans.cloudbeds.responses.Customer;

public class BookingAssignmentHealPlanTest {

    private static final LocalDate TODAY = LocalDate.of( 2026, 9, 25 );
    private static final LocalDate END = TODAY.plusDays( 140 );
    private static final LocalDate REFRESH_END = TODAY.plusDays( 14 );

    @Test
    public void unchangedFarFutureReservationIsNotFetched() {
        Map<Long, Customer> list = new HashMap<>();
        Map<Long, List<BookingAssignment>> currents = new HashMap<>();
        put( list, currents, 1L, customer( 1, "2026-12-05", "2026-12-07", "confirmed", "10.00", "30.00" ),
                row( 1, "2026-12-05", "2026-12-07", "confirmed", "10.00", "30.00" ) );

        BookingAssignmentEnrichService.HealPlan plan = plan( list, currents );
        assertThat( plan.toFetch, empty() );
        assertThat( plan.toClose, empty() );
    }

    @Test
    public void nearTermReservationIsAlwaysRefreshed() {
        Map<Long, Customer> list = new HashMap<>();
        Map<Long, List<BookingAssignment>> currents = new HashMap<>();
        put( list, currents, 1L, customer( 1, "2026-10-01", "2026-10-03", "confirmed", "0.00", "30.00" ),
                row( 1, "2026-10-01", "2026-10-03", "confirmed", "0.00", "30.00" ) );

        BookingAssignmentEnrichService.HealPlan plan = plan( list, currents );
        assertThat( plan.toFetch, contains( 1L ) );
        assertThat( plan.nearTerm, is( 1 ) );
    }

    @Test
    public void missingReservationIsFetched() {
        Map<Long, Customer> list = new HashMap<>();
        list.put( 7L, customer( 7, "2027-01-10", "2027-01-12", "confirmed", "30.00", "30.00" ) );

        BookingAssignmentEnrichService.HealPlan plan = plan( list, new HashMap<>() );
        assertThat( plan.toFetch, contains( 7L ) );
        assertThat( plan.missing, is( 1 ) );
    }

    @Test
    public void changedDatesStatusBalanceOrTotalAreFetched() {
        assertChanged( customer( 1, "2026-12-06", "2026-12-07", "confirmed", "10.00", "30.00" ) );
        assertChanged( customer( 1, "2026-12-05", "2026-12-08", "confirmed", "10.00", "30.00" ) );
        assertChanged( customer( 1, "2026-12-05", "2026-12-07", "not_confirmed", "10.00", "30.00" ) );
        assertChanged( customer( 1, "2026-12-05", "2026-12-07", "confirmed", "0.00", "30.00" ) );
        assertChanged( customer( 1, "2026-12-05", "2026-12-07", "confirmed", "10.00", "45.00" ) );
    }

    @Test
    public void multiRoomSpanUsesMinCheckinAndMaxCheckout() {
        Map<Long, Customer> list = new HashMap<>();
        Map<Long, List<BookingAssignment>> currents = new HashMap<>();
        put( list, currents, 1L, customer( 1, "2026-12-05", "2026-12-09", "confirmed", "10.00", "30.00" ),
                row( 1, "2026-12-05", "2026-12-07", "confirmed", "10.00", "30.00" ),
                row( 1, "2026-12-06", "2026-12-09", "confirmed", "10.00", "30.00" ) );

        assertThat( plan( list, currents ).toFetch, empty() );
    }

    @Test
    public void canceledInListIsClosedNotFetched() {
        Map<Long, Customer> list = new HashMap<>();
        Map<Long, List<BookingAssignment>> currents = new HashMap<>();
        BookingAssignment r = row( 1, "2026-12-05", "2026-12-07", "confirmed", "10.00", "30.00" );
        r.setLastRestFetchedAt( null );
        put( list, currents, 1L, customer( 1, "2026-12-05", "2026-12-07", "canceled", "0.00", "30.00" ), r );

        BookingAssignmentEnrichService.HealPlan plan = plan( list, currents );
        assertThat( plan.toClose, contains( 1L ) );
        assertThat( plan.toFetch, empty() );
    }

    @Test
    public void inWindowCurrentAbsentFromListIsVerifiedViaRest() {
        Map<Long, Customer> list = new HashMap<>();
        list.put( 2L, customer( 2, "2026-12-01", "2026-12-02", "confirmed", "0.00", "30.00" ) );
        Map<Long, List<BookingAssignment>> currents = new HashMap<>();
        currents.put( 2L, rows( row( 2, "2026-12-01", "2026-12-02", "confirmed", "0.00", "30.00" ) ) );
        currents.put( 3L, rows( row( 3, "2026-12-10", "2026-12-12", "confirmed", "0.00", "30.00" ) ) );
        // departed today: outside heal window
        currents.put( 4L, rows( row( 4, "2026-09-22", "2026-09-25", "checked_out", "0.00", "30.00" ) ) );

        BookingAssignmentEnrichService.HealPlan plan = plan( list, currents );
        assertThat( plan.toFetch, contains( 3L ) );
        assertThat( plan.absentFromList, is( 1 ) );
        assertThat( plan.toClose, empty() );
    }

    @Test
    public void emptyListDoesNotTreatCurrentsAsGone() {
        Map<Long, List<BookingAssignment>> currents = new HashMap<>();
        currents.put( 3L, rows( row( 3, "2026-12-10", "2026-12-12", "confirmed", "0.00", "30.00" ) ) );

        BookingAssignmentEnrichService.HealPlan plan = plan( new HashMap<>(), currents );
        assertThat( plan.toFetch, empty() );
        assertThat( plan.toClose, empty() );
    }

    @Test
    public void neverEnrichedCurrentIsFetched() {
        Map<Long, List<BookingAssignment>> currents = new HashMap<>();
        BookingAssignment r = row( 5, "2026-12-10", "2026-12-12", "confirmed", "0.00", null );
        r.setLastRestFetchedAt( null );
        currents.put( 5L, rows( r ) );
        Map<Long, Customer> list = new HashMap<>();
        list.put( 5L, customer( 5, "2026-12-10", "2026-12-12", "confirmed", "0.00", "30.00" ) );

        BookingAssignmentEnrichService.HealPlan plan = plan( list, currents );
        assertThat( plan.toFetch, contains( 5L ) );
        assertThat( plan.notEnriched, is( 1 ) );
        assertThat( plan.changed, is( 0 ) );
    }

    private static void assertChanged( Customer c ) {
        Map<Long, Customer> list = new HashMap<>();
        Map<Long, List<BookingAssignment>> currents = new HashMap<>();
        put( list, currents, 1L, c, row( 1, "2026-12-05", "2026-12-07", "confirmed", "10.00", "30.00" ) );
        BookingAssignmentEnrichService.HealPlan plan = plan( list, currents );
        assertThat( plan.toFetch, contains( 1L ) );
        assertThat( plan.changed, is( 1 ) );
    }

    private static BookingAssignmentEnrichService.HealPlan plan( Map<Long, Customer> list,
            Map<Long, List<BookingAssignment>> currents ) {
        return BookingAssignmentEnrichService.planHeal( list, currents, TODAY, END, REFRESH_END );
    }

    private static void put( Map<Long, Customer> list, Map<Long, List<BookingAssignment>> currents, long id,
            Customer c, BookingAssignment... rows ) {
        list.put( id, c );
        currents.put( id, rows( rows ) );
    }

    private static List<BookingAssignment> rows( BookingAssignment... rows ) {
        List<BookingAssignment> l = new ArrayList<>();
        for ( BookingAssignment r : rows ) {
            l.add( r );
        }
        return l;
    }

    private static Customer customer( long id, String checkin, String checkout, String status,
            String balance, String total ) {
        Customer c = new Customer();
        c.setId( String.valueOf( id ) );
        c.setCheckinDate( checkin );
        c.setCheckoutDate( checkout );
        c.setStatus( status );
        c.setBalanceDue( new BigDecimal( balance ) );
        c.setGrandTotal( total );
        return c;
    }

    private static BookingAssignment row( long reservationId, String checkin, String checkout, String status,
            String balance, String total ) {
        BookingAssignment a = new BookingAssignment();
        a.setAssignmentKey( "br-" + reservationId + "-" + checkin );
        a.setReservationId( reservationId );
        a.setSource( BookingAssignment.SOURCE_GUEST );
        a.setCheckinDate( LocalDate.parse( checkin ) );
        a.setCheckoutDate( LocalDate.parse( checkout ) );
        a.setBedStatus( status );
        a.setPaymentOutstanding( new BigDecimal( balance ) );
        a.setPaymentTotal( total == null ? null : new BigDecimal( total ) );
        a.setLastRestFetchedAt( new Timestamp( 1000L ) );
        return a;
    }
}
