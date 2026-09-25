package com.macbackpackers.services;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import com.macbackpackers.beans.BookingAssignment;
import com.macbackpackers.beans.cloudbeds.responses.Reservation;

public class BookingAssignmentBackfillTest {

    private static final Timestamp FETCHED_AT = Timestamp.valueOf( "2026-09-25 23:00:00" );

    @Test
    public void checkedOutStayIsCurrentFromBookingDate() {
        BookingAssignment a = row( 1, "2024-01-05", "2024-01-07", "checked_out", LocalDate.of( 2023, 12, 1 ) );
        BookingAssignmentEnrichService.applyBackfillRules( reservation( "checked_out" ), List.of( a ), FETCHED_AT );

        assertThat( a.getValidFrom(), is( Timestamp.valueOf( "2023-12-01 00:00:00" ) ) );
        assertThat( a.getValidTo(), nullValue() );
        assertThat( a.getLastRestFetchedAt(), is( FETCHED_AT ) );
    }

    @Test
    public void personalDataIsNulled() {
        BookingAssignment a = row( 1, "2024-01-05", "2024-01-07", "checked_out", LocalDate.of( 2023, 12, 1 ) );
        a.setGuestName( "Jane Doe" );
        a.setEmail( "jane@example.com" );
        a.setNotes( "call +44 7000 000000" );
        a.setComments( "late arrival" );
        a.setBookingReference( "6123049999" );
        BookingAssignmentEnrichService.applyBackfillRules( reservation( "checked_out" ), List.of( a ), FETCHED_AT );

        assertThat( a.getGuestName(), nullValue() );
        assertThat( a.getEmail(), nullValue() );
        assertThat( a.getNotes(), nullValue() );
        assertThat( a.getComments(), nullValue() );
        assertThat( a.getBookingReference(), is( "6123049999" ) );
    }

    @Test
    public void canceledAndNoShowReservationsAreClosedAtCheckin() {
        for ( String status : new String[] { "canceled", "no_show" } ) {
            BookingAssignment a = row( 1, "2024-01-05", "2024-01-07", "confirmed", LocalDate.of( 2023, 12, 1 ) );
            BookingAssignmentEnrichService.applyBackfillRules( reservation( status ), List.of( a ), FETCHED_AT );
            assertThat( status, a.getValidTo(), is( Timestamp.valueOf( "2024-01-05 00:00:00" ) ) );
        }
    }

    @Test
    public void canceledBedInsideLiveReservationIsClosedOthersStayCurrent() {
        BookingAssignment live = row( 1, "2024-01-05", "2024-01-07", "checked_out", LocalDate.of( 2023, 12, 1 ) );
        BookingAssignment canceled = row( 1, "2024-01-05", "2024-01-07", "canceled", LocalDate.of( 2023, 12, 1 ) );
        BookingAssignmentEnrichService.applyBackfillRules( reservation( "checked_out" ),
                List.of( live, canceled ), FETCHED_AT );

        assertThat( live.getValidTo(), nullValue() );
        assertThat( canceled.getValidTo(), is( Timestamp.valueOf( "2024-01-05 00:00:00" ) ) );
    }

    @Test
    public void missingBookingDateFallsBackToCheckinAndNeverClosesBeforeOpening() {
        BookingAssignment noBooked = row( 1, "2024-01-05", "2024-01-07", "checked_out", null );
        BookingAssignment bookedAfterCheckin = row( 1, "2024-01-05", "2024-01-07", "confirmed", LocalDate.of( 2024, 1, 6 ) );
        BookingAssignmentEnrichService.applyBackfillRules( reservation( "checked_out" ), List.of( noBooked ), FETCHED_AT );
        BookingAssignmentEnrichService.applyBackfillRules( reservation( "canceled" ), List.of( bookedAfterCheckin ), FETCHED_AT );

        assertThat( noBooked.getValidFrom(), is( Timestamp.valueOf( "2024-01-05 00:00:00" ) ) );
        assertThat( bookedAfterCheckin.getValidTo(), is( bookedAfterCheckin.getValidFrom() ) );
    }

    @Test
    public void chunksNeverSplitAReservation() {
        List<BookingAssignment> rows = new ArrayList<>();
        addRows( rows, 1, 2 );
        addRows( rows, 2, 2 );
        addRows( rows, 3, 3 );
        addRows( rows, 4, 1 );

        List<List<BookingAssignment>> chunks = BookingAssignment.chunkByReservation( rows, 3 );

        assertThat( chunks.stream().map( List::size ).collect( Collectors.toList() ), contains( 4, 3, 1 ) );
        for ( List<BookingAssignment> chunk : chunks ) {
            for ( List<BookingAssignment> other : chunks ) {
                if ( chunk != other ) {
                    assertThat( ids( chunk ), everyItem( not( is( in( ids( other ) ) ) ) ) );
                }
            }
        }
    }

    @Test
    public void singleLargeReservationStaysInOneChunk() {
        List<BookingAssignment> rows = new ArrayList<>();
        addRows( rows, 1, 5 );
        assertThat( BookingAssignment.chunkByReservation( rows, 2 ), hasSize( 1 ) );
    }

    @Test
    public void bulkInsertStatementMatchesParameterCount() {
        String sql = BookingAssignment.getBulkInsertStatement( 2 );
        long placeholders = sql.chars().filter( ch -> ch == '?' ).count();
        assertThat( placeholders, is( 2L * BookingAssignment.INSERT_COLUMNS.length ) );
        assertThat( new BookingAssignment().getInsertParameters().length, is( BookingAssignment.INSERT_COLUMNS.length ) );
    }

    @Test
    public void insertParametersUseYesNoForBooleans() {
        BookingAssignment a = new BookingAssignment();
        a.setHotelCollect( true );
        a.setViewed( false );
        Object[] params = a.getInsertParameters();
        List<String> cols = List.of( BookingAssignment.INSERT_COLUMNS );
        assertThat( params[cols.indexOf( "hotel_collect_yn" )], is( "Y" ) );
        assertThat( params[cols.indexOf( "viewed_yn" )], is( "N" ) );
    }

    private static List<Long> ids( List<BookingAssignment> rows ) {
        return rows.stream().map( BookingAssignment::getReservationId ).distinct().collect( Collectors.toList() );
    }

    private static void addRows( List<BookingAssignment> rows, long reservationId, int count ) {
        for ( int i = 0 ; i < count ; i++ ) {
            rows.add( row( reservationId, "2024-01-05", "2024-01-07", "checked_out", null ) );
        }
    }

    private static Reservation reservation( String status ) {
        Reservation r = new Reservation();
        r.setStatus( status );
        return r;
    }

    private static BookingAssignment row( long reservationId, String checkin, String checkout, String bedStatus,
            LocalDate booked ) {
        BookingAssignment a = new BookingAssignment();
        a.setReservationId( reservationId );
        a.setCheckinDate( LocalDate.parse( checkin ) );
        a.setCheckoutDate( LocalDate.parse( checkout ) );
        a.setBedStatus( bedStatus );
        a.setBookedDate( booked );
        return a;
    }
}
