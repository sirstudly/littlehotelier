package com.macbackpackers.scrapers.cloudbedsws;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.macbackpackers.beans.BedLock;
import com.macbackpackers.beans.BedLockViolation;
import com.macbackpackers.beans.BookingAssignment;
import com.macbackpackers.dao.WordPressDAO;
import com.macbackpackers.services.GmailService;

@ExtendWith( MockitoExtension.class )
public class BedLockMonitorTest {

    private static final long RES_ID = 176766409L;

    @Mock
    private WordPressDAO dao;

    @Mock
    private GmailService gmail;

    @InjectMocks
    private BedLockMonitor monitor;

    @Test
    public void movingOffLockedBedRecordsViolationAndEmails() throws Exception {
        when( dao.fetchActiveBedLocksForReservation( RES_ID ) ).thenReturn( List.of( lock( "111746-13" ) ) );

        monitor.onPlacementChange( assignment( "111746-13", "41", "Bed A" ), assignment( "111746-14", "41", "Bed B" ) );

        ArgumentCaptor<BedLockViolation> saved = ArgumentCaptor.forClass( BedLockViolation.class );
        verify( dao ).saveBedLockViolation( saved.capture() );
        BedLockViolation v = saved.getValue();
        assertThat( v.getBedLockId(), is( 7L ) );
        assertThat( v.getFromRoomId(), is( "111746-13" ) );
        assertThat( v.getToRoomId(), is( "111746-14" ) );
        assertThat( v.getToBedName(), is( "Bed B" ) );
        assertThat( v.getResolvedDate(), nullValue() );
        verify( gmail ).sendEmailToSelf( ArgumentMatchers.contains( "41 - Bed A -> 41 - Bed B" ), anyString() );
    }

    @Test
    public void emailAlertsCanBeSwitchedOff() throws Exception {
        when( dao.fetchActiveBedLocksForReservation( RES_ID ) ).thenReturn( List.of( lock( "111746-13" ) ) );
        when( dao.getOptionNoCache( BedLockMonitor.OPTION_EMAIL_ALERTS ) ).thenReturn( "false" );

        monitor.onPlacementChange( assignment( "111746-13", "41", "Bed A" ), assignment( "111746-14", "41", "Bed B" ) );

        verify( dao ).saveBedLockViolation( any() );
        verify( gmail, never() ).sendEmailToSelf( anyString(), anyString() );
    }

    @Test
    public void movingOtherBedOfSameReservationIsIgnored() {
        when( dao.fetchActiveBedLocksForReservation( RES_ID ) ).thenReturn( List.of( lock( "111746-13" ) ) );

        monitor.onPlacementChange( assignment( "111746-20", "42", "Bed A" ), assignment( "111746-21", "42", "Bed B" ) );

        verify( dao, never() ).saveBedLockViolation( any() );
    }

    @Test
    public void unchangedRoomIsIgnored() {
        monitor.onPlacementChange( assignment( "111746-13", "41", "Bed A" ), assignment( "111746-13", "41", "Bed A" ) );

        verify( dao, never() ).fetchActiveBedLocksForReservation( RES_ID );
    }

    @Test
    public void movingBackResolvesViolation() throws Exception {
        when( dao.fetchActiveBedLocksForReservation( RES_ID ) ).thenReturn( List.of( lock( "111746-13" ) ) );
        when( dao.fetchOpenBedLockViolation( 7L ) ).thenReturn( openViolation( "111746-14" ) );

        monitor.onPlacementChange( assignment( "111746-14", "41", "Bed B" ), assignment( "111746-13", "41", "Bed A" ) );

        ArgumentCaptor<BedLockViolation> saved = ArgumentCaptor.forClass( BedLockViolation.class );
        verify( dao ).saveBedLockViolation( saved.capture() );
        assertThat( saved.getValue().getResolvedDate(), notNullValue() );
        assertThat( saved.getValue().getResolution(), is( BedLockViolation.RESOLUTION_MOVED_BACK ) );
        verify( gmail, never() ).sendEmailToSelf( anyString(), anyString() );
    }

    @Test
    public void unassignedThenReassignedUpdatesDestination() {
        when( dao.fetchActiveBedLocksForReservation( RES_ID ) ).thenReturn( List.of( lock( "111746-13" ) ) );
        when( dao.fetchOpenBedLockViolation( 7L ) ).thenReturn( openViolation( null ) );

        monitor.onPlacementChange( assignment( null, "Unallocated", null ), assignment( "111746-15", "41", "Bed C" ) );

        ArgumentCaptor<BedLockViolation> saved = ArgumentCaptor.forClass( BedLockViolation.class );
        verify( dao ).saveBedLockViolation( saved.capture() );
        assertThat( saved.getValue().getToRoomId(), is( "111746-15" ) );
        assertThat( saved.getValue().getToBedName(), is( "Bed C" ) );
        assertThat( saved.getValue().getResolvedDate(), nullValue() );
    }

    @Test
    public void snapshotBackOnLockedBedResolvesOpenViolation() throws Exception {
        when( dao.fetchActiveBedLockReservationIds() ).thenReturn( Set.of( RES_ID ) );
        when( dao.fetchActiveBedLocksForReservation( RES_ID ) ).thenReturn( List.of( lock( "111746-13" ) ) );
        when( dao.fetchOpenBedLockViolation( 7L ) ).thenReturn( openViolation( "111746-14" ) );

        monitor.reconcileSnapshot( List.of( assignment( "111746-13", "41", "Bed A" ) ) );

        ArgumentCaptor<BedLockViolation> saved = ArgumentCaptor.forClass( BedLockViolation.class );
        verify( dao ).saveBedLockViolation( saved.capture() );
        assertThat( saved.getValue().getResolution(), is( BedLockViolation.RESOLUTION_MOVED_BACK ) );
        assertThat( saved.getValue().getResolvedDate(), notNullValue() );
        verify( gmail, never() ).sendEmailToSelf( anyString(), anyString() );
    }

    @Test
    public void snapshotOnLockedBedWithoutViolationDoesNothing() {
        when( dao.fetchActiveBedLockReservationIds() ).thenReturn( Set.of( RES_ID ) );
        when( dao.fetchActiveBedLocksForReservation( RES_ID ) ).thenReturn( List.of( lock( "111746-13" ) ) );

        monitor.reconcileSnapshot( List.of( assignment( "111746-13", "41", "Bed A" ) ) );

        verify( dao, never() ).saveBedLockViolation( any() );
    }

    @Test
    public void snapshotFlagsMoveMadeWhileDisconnected() throws Exception {
        when( dao.fetchActiveBedLockReservationIds() ).thenReturn( Set.of( RES_ID ) );
        when( dao.fetchActiveBedLocksForReservation( RES_ID ) ).thenReturn( List.of( lock( "111746-13" ) ) );

        monitor.reconcileSnapshot( List.of( assignment( "111746-14", "41", "Bed B" ) ) );

        ArgumentCaptor<BedLockViolation> saved = ArgumentCaptor.forClass( BedLockViolation.class );
        verify( dao ).saveBedLockViolation( saved.capture() );
        assertThat( saved.getValue().getFromRoomId(), is( "111746-13" ) );
        assertThat( saved.getValue().getToRoomId(), is( "111746-14" ) );
        verify( gmail ).sendEmailToSelf( ArgumentMatchers.contains( "41 - Bed A -> 41 - Bed B" ), anyString() );
    }

    @Test
    public void snapshotWithoutLockedReservationLeavesViolationOpen() {
        when( dao.fetchActiveBedLockReservationIds() ).thenReturn( Set.of( RES_ID ) );

        BookingAssignment other = assignment( "111746-13", "41", "Bed A" );
        other.setReservationId( 999L );
        monitor.reconcileSnapshot( List.of( other ) );

        verify( dao, never() ).fetchActiveBedLocksForReservation( RES_ID );
        verify( dao, never() ).saveBedLockViolation( any() );
    }

    @Test
    public void describeUnassignedAndUnmappedRooms() {
        assertThat( BedLockMonitor.describe( null, null, null ), is( "Unassigned" ) );
        assertThat( BedLockMonitor.describe( null, null, "111746-99" ), is( "111746-99" ) );
        assertThat( BedLockMonitor.describe( "PRIVATE", null, "111746-1" ), is( "PRIVATE" ) );
    }

    private static BedLock lock( String roomId ) {
        BedLock lock = new BedLock();
        lock.setId( 7L );
        lock.setReservationId( RES_ID );
        lock.setRoomId( roomId );
        lock.setRoom( "41" );
        lock.setBedName( "Bed A" );
        lock.setLockedBy( "Ron Chan" );
        return lock;
    }

    private static BedLockViolation openViolation( String toRoomId ) {
        BedLockViolation v = new BedLockViolation();
        v.setId( 3L );
        v.setBedLockId( 7L );
        v.setReservationId( RES_ID );
        v.setFromRoomId( "111746-13" );
        v.setToRoomId( toRoomId );
        return v;
    }

    private static BookingAssignment assignment( String roomId, String room, String bedName ) {
        BookingAssignment a = new BookingAssignment();
        a.setAssignmentKey( "230860568" );
        a.setReservationId( RES_ID );
        a.setRoomId( roomId );
        a.setRoom( room );
        a.setBedName( bedName );
        a.setGuestName( "Test Guest" );
        a.setDataHref( "/connect/17363#/reservations/" + RES_ID );
        return a;
    }
}
