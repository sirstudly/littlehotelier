package com.macbackpackers.scrapers.cloudbedsws;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.macbackpackers.dao.WordPressDAO;
import com.macbackpackers.jobs.ArchiveAllTransactionNotesJob;

public class ArchiveTransactionNotesBookingEventListenerTest {

    private WordPressDAO dao;
    private ArchiveTransactionNotesBookingEventListener listener;

    @BeforeEach
    public void setUp() throws Exception {
        dao = mock( WordPressDAO.class );
        listener = new ArchiveTransactionNotesBookingEventListener();
        Field daoField = ArchiveTransactionNotesBookingEventListener.class.getDeclaredField( "dao" );
        daoField.setAccessible( true );
        daoField.set( listener, dao );
        when( dao.hasArchiveAllTransactionNotesJobForReservation( any() ) ).thenReturn( false );
    }

    @Test
    public void isGuestReservationEvent_acceptsBookedCheckedInOut() {
        assertTrue( ArchiveTransactionNotesBookingEventListener.isGuestReservationEvent(
                event( "booked", "100", "50.00" ) ) );
        assertTrue( ArchiveTransactionNotesBookingEventListener.isGuestReservationEvent(
                event( "checked_in", "100", "50.00" ) ) );
        assertTrue( ArchiveTransactionNotesBookingEventListener.isGuestReservationEvent(
                event( "checked_out", "100", "0" ) ) );
        assertFalse( ArchiveTransactionNotesBookingEventListener.isGuestReservationEvent(
                event( "blocked_dates", "0", null ) ) );
        assertFalse( ArchiveTransactionNotesBookingEventListener.isGuestReservationEvent(
                event( "out_of_service", "0", null ) ) );
    }

    @Test
    public void onSnapshot_seedsCacheWithoutEnqueueing() {
        listener.onSnapshot( "17363", Collections.singletonList( event( "booked", "176704729", "100.00" ) ) );

        assertEquals( 0, new BigDecimal( "100.00" ).compareTo( listener.getCachedBalanceDue( "176704729" ) ) );
        verify( dao, never() ).insertJob( any() );
    }

    @Test
    public void onUpdate_enqueuesWhenBalanceDrops() {
        listener.onSnapshot( "17363", Collections.singletonList( event( "booked", "176704729", "100.00" ) ) );

        CloudbedsCalendarUpdate update = updateWith( event( "booked", "176704729", "5.00" ) );
        listener.onUpdate( "17363", update );

        verify( dao, times( 1 ) ).insertJob( any( ArchiveAllTransactionNotesJob.class ) );
        assertEquals( 0, new BigDecimal( "5.00" ).compareTo( listener.getCachedBalanceDue( "176704729" ) ) );
    }

    @Test
    public void onUpdate_doesNotEnqueueWhenBalanceUnchanged() {
        listener.onSnapshot( "17363", Collections.singletonList( event( "booked", "176704729", "100.00" ) ) );

        CloudbedsCalendarUpdate update = updateWith( event( "booked", "176704729", "100.00" ) );
        listener.onUpdate( "17363", update );

        verify( dao, never() ).insertJob( any() );
    }

    @Test
    public void onUpdate_enqueuesWhenPaidWithoutPriorCache() {
        CloudbedsCalendarUpdate update = updateWith( event( "checked_in", "176704729", "0.00" ) );
        listener.onUpdate( "17363", update );

        verify( dao, times( 1 ) ).insertJob( any( ArchiveAllTransactionNotesJob.class ) );
        assertEquals( 0, new BigDecimal( "0.00" ).compareTo( listener.getCachedBalanceDue( "176704729" ) ) );
    }

    @Test
    public void onUpdate_doesNotEnqueueUnpaidWithoutPriorCache() {
        CloudbedsCalendarUpdate update = updateWith( event( "booked", "176704729", "50.00" ) );
        listener.onUpdate( "17363", update );

        verify( dao, never() ).insertJob( any() );
        assertEquals( 0, new BigDecimal( "50.00" ).compareTo( listener.getCachedBalanceDue( "176704729" ) ) );
    }

    @Test
    public void onUpdate_dedupesSameBookingIdInBatch() {
        listener.onSnapshot( "17363", Collections.singletonList( event( "booked", "176704729", "100.00" ) ) );

        CloudbedsCalendarUpdate update = updateWith(
                event( "booked", "176704729", "0.00" ),
                event( "booked", "176704729", "0.00" ) );
        listener.onUpdate( "17363", update );

        verify( dao, times( 1 ) ).insertJob( any( ArchiveAllTransactionNotesJob.class ) );
    }

    @Test
    public void onUpdate_skipsWhenArchiveJobAlreadyPending() {
        when( dao.hasArchiveAllTransactionNotesJobForReservation( "176704729" ) ).thenReturn( true );
        listener.onSnapshot( "17363", Collections.singletonList( event( "booked", "176704729", "100.00" ) ) );

        CloudbedsCalendarUpdate update = updateWith( event( "booked", "176704729", "0.00" ) );
        listener.onUpdate( "17363", update );

        verify( dao, never() ).insertJob( any() );
        assertEquals( 0, new BigDecimal( "0.00" ).compareTo( listener.getCachedBalanceDue( "176704729" ) ) );
    }

    @Test
    public void onUpdate_ignoresBlockedDates() {
        CloudbedsCalendarUpdate update = updateWith( event( "blocked_dates", "0", "0" ) );
        listener.onUpdate( "17363", update );

        verify( dao, never() ).insertJob( any() );
        assertNull( listener.getCachedBalanceDue( "0" ) );
    }

    private static CloudbedsCalendarUpdate updateWith( CloudbedsCalendarEvent... events ) {
        CloudbedsCalendarUpdate update = new CloudbedsCalendarUpdate();
        update.setEvents( Arrays.asList( events ) );
        return update;
    }

    private static CloudbedsCalendarEvent event( String type, String bookingId, String balanceDue ) {
        Map<String, String> raw = new LinkedHashMap<>();
        raw.put( "type", type );
        raw.put( "status", "confirmed" );
        raw.put( "booking_id", bookingId );
        raw.put( "balance_due", balanceDue );
        raw.put( "first_name", "Test" );
        raw.put( "last_name", "Guest" );
        raw.put( "third_party_identifier", "BDC-1" );
        return new CloudbedsCalendarEvent( raw );
    }
}
