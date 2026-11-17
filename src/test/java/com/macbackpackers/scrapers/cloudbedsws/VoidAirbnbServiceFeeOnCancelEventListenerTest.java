package com.macbackpackers.scrapers.cloudbedsws;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.htmlunit.WebClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationContext;

import com.macbackpackers.dao.WordPressDAO;
import com.macbackpackers.jobs.VoidAirbnbServiceFeeOnCancelJob;
import com.macbackpackers.scrapers.CloudbedsScraper;

public class VoidAirbnbServiceFeeOnCancelEventListenerTest {

    private static final String SOURCE_ID = "458851";

    private WordPressDAO dao;
    private CloudbedsScraper scraper;
    private ApplicationContext appContext;
    private VoidAirbnbServiceFeeOnCancelEventListener listener;

    @BeforeEach
    public void setUp() throws Exception {
        dao = mock( WordPressDAO.class );
        scraper = mock( CloudbedsScraper.class );
        appContext = mock( ApplicationContext.class );
        WebClient webClient = mock( WebClient.class );

        listener = new VoidAirbnbServiceFeeOnCancelEventListener();
        setField( listener, "dao", dao );
        setField( listener, "cloudbedsScraper", scraper );
        setField( listener, "eventRegistry", new CloudbedsCalendarEventRegistry() );
        setField( listener, "context", appContext );

        when( appContext.getBean( "webClientForCloudbeds", WebClient.class ) ).thenReturn( webClient );
        when( scraper.lookupAirbnbPrepaidSubSourceIds( webClient ) )
                .thenReturn( Collections.singleton( SOURCE_ID ) );
        when( dao.hasPendingVoidAirbnbServiceFeeOnCancelJobForReservation( any() ) ).thenReturn( false );
    }

    @Test
    public void matchesCanceledOrNoShowAirbnb_requiresCancelAndAirbnbSource() {
        assertTrue( VoidAirbnbServiceFeeOnCancelEventListener.matchesCanceledOrNoShowAirbnb(
                event( "canceled", SOURCE_ID ), Collections.singleton( SOURCE_ID ) ) );
        assertTrue( VoidAirbnbServiceFeeOnCancelEventListener.matchesCanceledOrNoShowAirbnb(
                event( "no_show", SOURCE_ID ), Collections.singleton( SOURCE_ID ) ) );
        assertFalse( VoidAirbnbServiceFeeOnCancelEventListener.matchesCanceledOrNoShowAirbnb(
                event( "confirmed", SOURCE_ID ), Collections.singleton( SOURCE_ID ) ) );
        assertFalse( VoidAirbnbServiceFeeOnCancelEventListener.matchesCanceledOrNoShowAirbnb(
                event( "canceled", "999" ), Collections.singleton( SOURCE_ID ) ) );
    }

    @Test
    public void onUpdate_enqueuesForCanceledAirbnbEvent() {
        CloudbedsCalendarEvent canceled = event( "canceled", SOURCE_ID );
        CloudbedsCalendarUpdate update = new CloudbedsCalendarUpdate();
        update.setPayloadAction( "changes" );
        update.setEvents( Collections.singletonList( canceled ) );

        listener.onUpdate( "17959", update );

        ArgumentCaptor<VoidAirbnbServiceFeeOnCancelJob> cap =
                ArgumentCaptor.forClass( VoidAirbnbServiceFeeOnCancelJob.class );
        verify( dao ).insertJob( cap.capture() );
        assertTrue( "190181178".equals( cap.getValue().getReservationId() ) );
    }

    @Test
    public void onUpdate_skipsWhenPendingExists() {
        when( dao.hasPendingVoidAirbnbServiceFeeOnCancelJobForReservation( "190181178" ) ).thenReturn( true );
        CloudbedsCalendarUpdate update = new CloudbedsCalendarUpdate();
        update.setPayloadAction( "changes" );
        update.setEvents( Collections.singletonList( event( "canceled", SOURCE_ID ) ) );

        listener.onUpdate( "17959", update );

        verify( dao, never() ).insertJob( any() );
    }

    private static CloudbedsCalendarEvent event( String status, String bookingSource ) {
        Map<String, String> data = new HashMap<>();
        data.put( "type", "booked" );
        data.put( "booking_id", "190181178" );
        data.put( "status", status );
        data.put( "booking_source", bookingSource );
        data.put( "is_hotel_collect_booking", "0" );
        data.put( "first_name", "Test" );
        data.put( "last_name", "Guest" );
        return new CloudbedsCalendarEvent( data );
    }

    private static void setField( Object target, String name, Object value ) throws Exception {
        Field f = target.getClass().getDeclaredField( name );
        f.setAccessible( true );
        f.set( target, value );
    }
}
