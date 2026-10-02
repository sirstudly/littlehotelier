package com.macbackpackers.services;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Timestamp;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.htmlunit.WebClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.test.util.ReflectionTestUtils;

import com.macbackpackers.beans.cloudbeds.responses.Reservation;
import com.macbackpackers.dao.WordPressDAO;
import com.macbackpackers.scrapers.CloudbedsScraper;

public class BookingAssignmentNoteReportRefreshTest {

    private static final Timestamp HEAL_STARTED_AT = Timestamp.valueOf( "2026-10-02 15:53:13" );

    private final WordPressDAO dao = mock( WordPressDAO.class );
    private final CloudbedsScraper scraper = mock( CloudbedsScraper.class );
    private final WebClient webClient = mock( WebClient.class );
    private final ExecutorService ioThreadPool = Executors.newSingleThreadExecutor();
    private final BookingAssignmentEnrichService service = new BookingAssignmentEnrichService();

    @BeforeEach
    public void setUp() throws Exception {
        ReflectionTestUtils.setField( service, "dao", dao );
        ReflectionTestUtils.setField( service, "scraper", scraper );
        ReflectionTestUtils.setField( service, "ioThreadPool", ioThreadPool );
        when( dao.getDefaultOption( eq( "hbo_cloudbeds_request_timeout" ), anyString() ) ).thenReturn( "60" );
        when( scraper.getReservationRetry( any(), anyString() ) ).thenReturn( new Reservation() );
    }

    @AfterEach
    public void tearDown() {
        ioThreadPool.shutdownNow();
    }

    @Test
    public void runsReportsThenFetchesStaleReservations() throws Exception {
        when( dao.fetchStaleNoteReportReservationIds( 5, HEAL_STARTED_AT ) ).thenReturn( List.of( 11L, 12L ) );

        service.refreshNoteReportReservations( webClient, 5, HEAL_STARTED_AT );

        InOrder order = inOrder( dao );
        order.verify( dao ).runSplitRoomsReservationsReport( 5 );
        order.verify( dao ).runUnpaidDepositReport( 5 );
        order.verify( dao ).runGroupBookingsReport( 5 );
        order.verify( dao ).runMostlyFullDormReport( 5 );
        order.verify( dao ).fetchStaleNoteReportReservationIds( 5, HEAL_STARTED_AT );
        verify( scraper ).getReservationRetry( webClient, "11" );
        verify( scraper ).getReservationRetry( webClient, "12" );
    }

    @Test
    public void fetchesNothingWhenNoStaleReservations() throws Exception {
        when( dao.fetchStaleNoteReportReservationIds( 5, HEAL_STARTED_AT ) ).thenReturn( List.of() );

        service.refreshNoteReportReservations( webClient, 5, HEAL_STARTED_AT );

        verify( scraper, never() ).getReservationRetry( any(), anyString() );
    }
}
