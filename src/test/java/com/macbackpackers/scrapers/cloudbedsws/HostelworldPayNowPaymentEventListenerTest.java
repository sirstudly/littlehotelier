package com.macbackpackers.scrapers.cloudbedsws;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ScheduledFuture;

import org.htmlunit.WebClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationContext;

import com.macbackpackers.beans.cloudbeds.responses.Reservation;
import com.macbackpackers.dao.WordPressDAO;
import com.macbackpackers.jobs.CreateHostelworldPayNowPaymentJob;
import com.macbackpackers.jobs.HostelworldPayNowPaymentJob;
import com.macbackpackers.scrapers.CloudbedsScraper;
import com.macbackpackers.services.EdinburghVisitorLevyJobSupport;

public class HostelworldPayNowPaymentEventListenerTest {

    private static final String PROPERTY_ID = "18265";
    private static final String SOURCE_ID = "466672";

    private WordPressDAO dao;
    private CloudbedsScraper scraper;
    private EdinburghVisitorLevyJobSupport evlSupport;
    private CloudbedsCalendarEventRegistry registry;
    private ApplicationContext appContext;
    private WebClient webClient;
    private HostelworldPayNowPaymentEventListener listener;

    @BeforeEach
    public void setUp() throws Exception {
        dao = mock( WordPressDAO.class );
        scraper = mock( CloudbedsScraper.class );
        evlSupport = mock( EdinburghVisitorLevyJobSupport.class );
        registry = new CloudbedsCalendarEventRegistry();
        appContext = mock( ApplicationContext.class );
        webClient = mock( WebClient.class );

        listener = new HostelworldPayNowPaymentEventListener();
        setField( listener, "dao", dao );
        setField( listener, "cloudbedsScraper", scraper );
        setField( listener, "edinburghVisitorLevyJobSupport", evlSupport );
        setField( listener, "eventRegistry", registry );
        setField( listener, "context", appContext );

        when( appContext.getBean( eq( "webClientForCloudbeds" ), eq( WebClient.class ) ) )
                .thenReturn( webClient );
        when( scraper.lookupHostelworldPayNowSubSourceIds( webClient ) )
                .thenReturn( Collections.singleton( SOURCE_ID ) );
        when( dao.hasPendingHostelworldPayNowPaymentJobForReservation( anyString() ) ).thenReturn( false );
        when( dao.hasCompletedHostelworldPayNowPaymentJobForReservation( anyString() ) ).thenReturn( false );
        when( dao.hasPendingCreateHostelworldPayNowPaymentJob() ).thenReturn( false );
        when( evlSupport.findOrCreatePendingJob( anyString() ) ).thenReturn( null );
    }

    @Test
    public void onUpdate_enqueuesFromNonAssignedRow() {
        CloudbedsCalendarEvent nonAssigned = payNowEvent( "245179610", "190486983", null );
        CloudbedsCalendarUpdate update = new CloudbedsCalendarUpdate();
        update.setPayloadAction( "changes" );
        update.setNonAssignedReservations( Collections.singletonList( nonAssigned ) );

        listener.onUpdate( PROPERTY_ID, update );

        ArgumentCaptor<HostelworldPayNowPaymentJob> captor =
                ArgumentCaptor.forClass( HostelworldPayNowPaymentJob.class );
        verify( dao, times( 1 ) ).insertJob( captor.capture() );
        assertEquals( "190486983", captor.getValue().getReservationId() );
        assertTrue( listener.getPendingOrphanReconcileByProperty().isEmpty() );
    }

    @Test
    public void onUpdate_orphanNonAssignedDelete_schedulesDebouncedCreate() {
        CloudbedsCalendarUpdate update = orphanDeleteUpdate( "245179610" );
        registry.beginUpdate( PROPERTY_ID, update );
        listener.onUpdate( PROPERTY_ID, update );
        registry.commitUpdate( PROPERTY_ID, update );

        verify( dao, never() ).insertJob( any( HostelworldPayNowPaymentJob.class ) );
        Map<String, ScheduledFuture<?>> pending = listener.getPendingOrphanReconcileByProperty();
        assertEquals( 1, pending.size() );
        assertTrue( pending.containsKey( PROPERTY_ID ) );
        assertFalse( pending.get( PROPERTY_ID ).isDone() );
    }

    @Test
    public void onUpdate_orphanNonAssignedDelete_coalescesDebounce() {
        CloudbedsCalendarUpdate first = orphanDeleteUpdate( "245179610" );
        registry.beginUpdate( PROPERTY_ID, first );
        listener.onUpdate( PROPERTY_ID, first );
        registry.commitUpdate( PROPERTY_ID, first );
        ScheduledFuture<?> firstFuture = listener.getPendingOrphanReconcileByProperty().get( PROPERTY_ID );

        CloudbedsCalendarUpdate second = orphanDeleteUpdate( "245218513" );
        registry.beginUpdate( PROPERTY_ID, second );
        listener.onUpdate( PROPERTY_ID, second );
        registry.commitUpdate( PROPERTY_ID, second );

        assertEquals( 1, listener.getPendingOrphanReconcileByProperty().size() );
        ScheduledFuture<?> secondFuture = listener.getPendingOrphanReconcileByProperty().get( PROPERTY_ID );
        assertTrue( firstFuture.isCancelled() );
        assertFalse( secondFuture.isCancelled() );
    }

    @Test
    public void onUpdate_orphanDeleteWithAssignedEvents_doesNotScheduleReconcile() {
        CloudbedsCalendarEvent assigned = payNowEvent( "evt-1", "190469923", "112202-2" );
        CloudbedsCalendarUpdate update = orphanDeleteUpdate( "245179610" );
        update.setEvents( Collections.singletonList( assigned ) );

        registry.beginUpdate( PROPERTY_ID, update );
        listener.onUpdate( PROPERTY_ID, update );
        registry.commitUpdate( PROPERTY_ID, update );

        // Assigned Pay Now Event still enqueues a payment job; orphan reconcile must not schedule.
        verify( dao, times( 1 ) ).insertJob( any( HostelworldPayNowPaymentJob.class ) );
        assertTrue( listener.getPendingOrphanReconcileByProperty().isEmpty() );
    }

    @Test
    public void onUpdate_cachedNonAssignedDelete_enqueuesViaGetReservation() throws Exception {
        CloudbedsCalendarEvent unassigned = payNowEvent( "245179610", "190486983", null );
        registry.onSnapshot( PROPERTY_ID, Collections.singletonList( unassigned ) );

        Reservation res = reservation( "190486983", "confirmed", true, true,
                BigDecimal.ZERO, new BigDecimal( "51.27" ) );
        when( scraper.getReservation( webClient, "190486983" ) ).thenReturn( res );

        CloudbedsCalendarUpdate update = orphanDeleteUpdate( "245179610" );
        registry.beginUpdate( PROPERTY_ID, update );
        listener.onUpdate( PROPERTY_ID, update );
        registry.commitUpdate( PROPERTY_ID, update );

        ArgumentCaptor<HostelworldPayNowPaymentJob> captor =
                ArgumentCaptor.forClass( HostelworldPayNowPaymentJob.class );
        verify( dao, times( 1 ) ).insertJob( captor.capture() );
        assertEquals( "190486983", captor.getValue().getReservationId() );
        assertTrue( listener.getPendingOrphanReconcileByProperty().isEmpty() );
        verify( dao, never() ).insertJob( any( CreateHostelworldPayNowPaymentJob.class ) );
    }

    @Test
    public void onUpdate_cachedNonAssignedDelete_skipsHealthyFolio() throws Exception {
        CloudbedsCalendarEvent unassigned = payNowEvent( "245179610", "190486983", null );
        registry.onSnapshot( PROPERTY_ID, Collections.singletonList( unassigned ) );

        Reservation res = reservation( "190486983", "confirmed", true, true,
                new BigDecimal( "48.84" ), new BigDecimal( "2.43" ) );
        // EVL matching balance → healthy; stub visitor levy via paid/balance only: paid>0 and
        // balance equals typical EVL path uses calculator — use balance 0 for clear healthy.
        res = reservation( "190486983", "confirmed", true, true,
                new BigDecimal( "51.27" ), BigDecimal.ZERO );
        when( scraper.getReservation( webClient, "190486983" ) ).thenReturn( res );

        CloudbedsCalendarUpdate update = orphanDeleteUpdate( "245179610" );
        registry.beginUpdate( PROPERTY_ID, update );
        listener.onUpdate( PROPERTY_ID, update );
        registry.commitUpdate( PROPERTY_ID, update );

        verify( dao, never() ).insertJob( any() );
        assertTrue( listener.getPendingOrphanReconcileByProperty().isEmpty() );
    }

    @Test
    public void onUpdate_cachedNonAssignedDelete_skipsNonHostelworldBooking() throws Exception {
        CloudbedsCalendarEvent unassigned = payNowEvent( "245193467", "190498918", null );
        registry.onSnapshot( PROPERTY_ID, Collections.singletonList( unassigned ) );

        Reservation res = reservation( "190498918", "confirmed", true, true,
                BigDecimal.ZERO, new BigDecimal( "56.16" ) );
        res.setSourceName( "Booking.com" );
        when( scraper.getReservation( webClient, "190498918" ) ).thenReturn( res );

        CloudbedsCalendarUpdate update = orphanDeleteUpdate( "245193467" );
        registry.beginUpdate( PROPERTY_ID, update );
        listener.onUpdate( PROPERTY_ID, update );
        registry.commitUpdate( PROPERTY_ID, update );

        verify( dao, never() ).insertJob( any() );
        assertTrue( listener.getPendingOrphanReconcileByProperty().isEmpty() );
    }

    private static CloudbedsCalendarUpdate orphanDeleteUpdate( String bookingRoomsId ) {
        CloudbedsCalendarUpdate update = new CloudbedsCalendarUpdate();
        update.setPayloadAction( "changes" );
        update.setDeleteSection( Collections.singletonMap(
                "NonAssignedReservations", Collections.singletonList( bookingRoomsId ) ) );
        return update;
    }

    private static CloudbedsCalendarEvent payNowEvent( String id, String bookingId, String roomId ) {
        Map<String, String> raw = new LinkedHashMap<>();
        raw.put( "id", id );
        raw.put( "booking_id", bookingId );
        raw.put( "type", "booked" );
        raw.put( "status", "confirmed" );
        raw.put( "booking_source", SOURCE_ID );
        raw.put( "is_hotel_collect_payment", "0" );
        raw.put( "detailed_rates", "[{\"rate_description\":\"Non-refundable\"}]" );
        raw.put( "third_party_identifier", "11242-580906088" );
        raw.put( "first_name", "Eden" );
        raw.put( "last_name", "Foote" );
        if ( roomId != null ) {
            raw.put( "room_id", roomId );
        }
        return new CloudbedsCalendarEvent( raw );
    }

    private static Reservation reservation( String id, String status, boolean channelCollect,
            boolean nonRefundable, BigDecimal paid, BigDecimal balanceDue ) {
        Reservation res = new Reservation();
        res.setReservationId( id );
        res.setStatus( status );
        res.setFirstName( "Eden" );
        res.setLastName( "Foote" );
        res.setThirdPartyIdentifier( "11242-580906088" );
        res.setSourceName( "Hostelworld" );
        res.setChannelPaymentType( channelCollect ? "Channel" : "Hotel" );
        res.setIsHotelCollectBooking( channelCollect ? "0" : "1" );
        res.setUsedRoomTypes( nonRefundable ? "Non-refundable" : "Flexible" );
        res.setPaidValue( paid );
        res.setBalanceDue( balanceDue );
        return res;
    }

    private static void setField( Object target, String name, Object value ) throws Exception {
        Field field = HostelworldPayNowPaymentEventListener.class.getDeclaredField( name );
        field.setAccessible( true );
        field.set( target, value );
    }
}
