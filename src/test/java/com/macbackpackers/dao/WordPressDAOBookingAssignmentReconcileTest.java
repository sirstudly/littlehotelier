package com.macbackpackers.dao;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.macbackpackers.beans.BookingAssignment;

public class WordPressDAOBookingAssignmentReconcileTest {

    private static final LocalDate WINDOW_START = LocalDate.parse( "2026-09-01" );
    private static final LocalDate WINDOW_END = LocalDate.parse( "2026-11-05" );

    @Test
    public void backfilledStayIsOutsideSnapshotWindow() {
        assertThat( WordPressDAOImpl.overlapsWindow(
                assignment( "2024-03-10", "2024-03-12" ), WINDOW_START, WINDOW_END ), is( false ) );
        assertThat( WordPressDAOImpl.overlapsWindow(
                assignment( "2026-08-28", "2026-09-01" ), WINDOW_START, WINDOW_END ), is( false ) );
        assertThat( WordPressDAOImpl.overlapsWindow(
                assignment( "2026-09-20", "2026-09-27" ), WINDOW_START, WINDOW_END ), is( true ) );
    }

    @Test
    public void mixedChangesUnderCapAreKept() {
        List<Long> closeIds = new ArrayList<>();
        List<BookingAssignment> inserts = new ArrayList<>();
        addPureCloses( closeIds, inserts, 10 );
        closeIds.add( 999L );
        inserts.add( new BookingAssignment() );

        int dropped = WordPressDAOImpl.dropMassCloses( closeIds, inserts, 1000 );

        assertThat( dropped, is( 0 ) );
        assertThat( closeIds, hasSize( 11 ) );
        assertThat( inserts, hasSize( 11 ) );
    }

    @Test
    public void massCloseIsBlockedButOtherChangesRemain() {
        List<Long> closeIds = new ArrayList<>();
        List<BookingAssignment> inserts = new ArrayList<>();
        addPureCloses( closeIds, inserts, 46035 );
        BookingAssignment moved = new BookingAssignment();
        closeIds.add( 999999L );
        inserts.add( moved );
        BookingAssignment added = new BookingAssignment();
        closeIds.add( null );
        inserts.add( added );

        int dropped = WordPressDAOImpl.dropMassCloses( closeIds, inserts, 49000 );

        assertThat( dropped, is( 46035 ) );
        assertThat( closeIds, contains( 999999L, null ) );
        assertThat( inserts, contains( moved, added ) );
    }

    @Test
    public void capIsFractionOfCurrentsWithFloor() {
        assertThat( WordPressDAOImpl.maxReconcileCloses( 100 ), is( 500 ) );
        assertThat( WordPressDAOImpl.maxReconcileCloses( 10000 ), is( 2000 ) );
    }

    private static void addPureCloses( List<Long> closeIds, List<BookingAssignment> inserts, int count ) {
        for ( long i = 1 ; i <= count ; i++ ) {
            closeIds.add( i );
            inserts.add( null );
        }
    }

    private static BookingAssignment assignment( String checkin, String checkout ) {
        BookingAssignment a = new BookingAssignment();
        a.setCheckinDate( LocalDate.parse( checkin ) );
        a.setCheckoutDate( LocalDate.parse( checkout ) );
        return a;
    }
}
