package com.macbackpackers.scrapers.cloudbedsws;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
import com.macbackpackers.jobs.AirbnbPrepaidPaymentJob;
import com.macbackpackers.jobs.CreateAirbnbPrepaidPaymentJob;
import com.macbackpackers.scrapers.CloudbedsScraper;
import com.macbackpackers.services.EdinburghVisitorLevyJobSupport;

public class AirbnbPrepaidPaymentEventListenerTest {

    private static final String PROPERTY_ID = "17363";
    private static final String SOURCE_ID = "458851";

    private WordPressDAO dao;
    private CloudbedsScraper scraper;
    private EdinburghVisitorLevyJobSupport evlSupport;
    private CloudbedsCalendarEventRegistry registry;
    private ApplicationContext appContext;
    private WebClient webClient;
    private AirbnbPrepaidPaymentEventListener listener;

    @BeforeEach
    public void setUp() throws Exception {
        dao = mock( WordPressDAO.class );
        scraper = mock( CloudbedsScraper.class );
        evlSupport = mock( EdinburghVisitorLevyJobSupport.class );
        registry = new CloudbedsCalendarEventRegistry();
        appContext = mock( ApplicationContext.class );
        webClient = mock( WebClient.class );

        listener = new AirbnbPrepaidPaymentEventListener();
        setField( listener, "dao", dao );
        setField( listener, "cloudbedsScraper", scraper );
        setField( listener, "edinburghVisitorLevyJobSupport", evlSupport );
        setField( listener, "eventRegistry", registry );
        setField( listener, "context", appContext );

        when( appContext.getBean( eq( "webClientForCloudbeds" ), eq( WebClient.class ) ) )
                .thenReturn( webClient );
        when( scraper.lookupAirbnbPrepaidSubSourceIds( webClient ) )
                .thenReturn( Collections.singleton( SOURCE_ID ) );
        when( dao.hasPendingAirbnbPrepaidPaymentJobForReservation( anyString() ) ).thenReturn( false );
        when( dao.hasCompletedAirbnbPrepaidPaymentJobForReservation( anyString() ) ).thenReturn( false );
        when( dao.hasPendingCreateAirbnbPrepaidPaymentJob() ).thenReturn( false );
        when( evlSupport.findOrCreatePendingJob( anyString() ) ).thenReturn( null );
    }

    @Test
    public void onUpdate_enqueuesFromNonAssignedRow() {
        CloudbedsCalendarEvent nonAssigned = airbnbEvent( "245211731", "190514833", null );
        CloudbedsCalendarUpdate update = new CloudbedsCalendarUpdate();
        update.setPayloadAction( "changes" );
        update.setNonAssignedReservations( Collections.singletonList( nonAssigned ) );

        listener.onUpdate( PROPERTY_ID, update );

        ArgumentCaptor<AirbnbPrepaidPaymentJob> captor =
                ArgumentCaptor.forClass( AirbnbPrepaidPaymentJob.class );
        verify( dao, times( 1 ) ).insertJob( captor.capture() );
        assertEquals( "190514833", captor.getValue().getReservationId() );
        assertTrue( listener.getPendingOrphanReconcileByProperty().isEmpty() );
    }

    @Test
    public void onUpdate_orphanNonAssignedDelete_schedulesDebouncedCreate() {
        CloudbedsCalendarUpdate update = orphanDeleteUpdate( "245211731" );
        registry.beginUpdate( PROPERTY_ID, update );
        listener.onUpdate( PROPERTY_ID, update );
        registry.commitUpdate( PROPERTY_ID, update );

        verify( dao, never() ).insertJob( any( AirbnbPrepaidPaymentJob.class ) );
        Map<String, ScheduledFuture<?>> pending = listener.getPendingOrphanReconcileByProperty();
        assertEquals( 1, pending.size() );
        assertTrue( pending.containsKey( PROPERTY_ID ) );
    }

    @Test
    public void onUpdate_cachedNonAssignedDelete_enqueuesViaGetReservation() throws Exception {
        CloudbedsCalendarEvent unassigned = airbnbEvent( "245211731", "190514833", null );
        registry.onSnapshot( PROPERTY_ID, Collections.singletonList( unassigned ) );

        Reservation res = new Reservation();
        res.setReservationId( "190514833" );
        res.setStatus( "confirmed" );
        res.setSourceName( "Airbnb (API)" );
        res.setChannelPaymentType( "Channel" );
        res.setIsHotelCollectBooking( "0" );
        res.setPaidValue( BigDecimal.ZERO );
        res.setBalanceDue( new BigDecimal( "100.00" ) );
        when( scraper.getReservation( webClient, "190514833" ) ).thenReturn( res );

        CloudbedsCalendarUpdate update = orphanDeleteUpdate( "245211731" );
        registry.beginUpdate( PROPERTY_ID, update );
        listener.onUpdate( PROPERTY_ID, update );
        registry.commitUpdate( PROPERTY_ID, update );

        ArgumentCaptor<AirbnbPrepaidPaymentJob> captor =
                ArgumentCaptor.forClass( AirbnbPrepaidPaymentJob.class );
        verify( dao, times( 1 ) ).insertJob( captor.capture() );
        assertEquals( "190514833", captor.getValue().getReservationId() );
        assertTrue( listener.getPendingOrphanReconcileByProperty().isEmpty() );
        verify( dao, never() ).insertJob( any( CreateAirbnbPrepaidPaymentJob.class ) );
    }

    private static CloudbedsCalendarUpdate orphanDeleteUpdate( String bookingRoomsId ) {
        CloudbedsCalendarUpdate update = new CloudbedsCalendarUpdate();
        update.setPayloadAction( "changes" );
        update.setDeleteSection( Collections.singletonMap(
                "NonAssignedReservations", Collections.singletonList( bookingRoomsId ) ) );
        return update;
    }

    private static CloudbedsCalendarEvent airbnbEvent( String id, String bookingId, String roomId ) {
        Map<String, String> raw = new LinkedHashMap<>();
        raw.put( "id", id );
        raw.put( "booking_id", bookingId );
        raw.put( "type", "booked" );
        raw.put( "status", "confirmed" );
        raw.put( "booking_source", SOURCE_ID );
        raw.put( "is_hotel_collect_payment", "0" );
        raw.put( "first_name", "Cam" );
        raw.put( "last_name", "Brunt" );
        if ( roomId != null ) {
            raw.put( "room_id", roomId );
        }
        return new CloudbedsCalendarEvent( raw );
    }

    private static void setField( Object target, String name, Object value ) throws Exception {
        Field field = AirbnbPrepaidPaymentEventListener.class.getDeclaredField( name );
        field.setAccessible( true );
        field.set( target, value );
    }
}
