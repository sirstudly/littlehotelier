package com.macbackpackers.services;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.htmlunit.WebClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.google.gson.JsonParser;
import com.macbackpackers.beans.cloudbeds.responses.ActivityLogEntry;
import com.macbackpackers.beans.cloudbeds.responses.Customer;
import com.macbackpackers.beans.cloudbeds.responses.Reservation;
import com.macbackpackers.dao.WordPressDAO;
import com.macbackpackers.scrapers.ActivityLogParser;
import com.macbackpackers.scrapers.CloudbedsScraper;

public class ActivityLogSyncServiceTest {

    private static final Instant NOW = Instant.parse( "2026-10-05T10:17:42Z" );

    private final CloudbedsScraper scraper = mock( CloudbedsScraper.class );
    private final WordPressDAO dao = mock( WordPressDAO.class );
    private final BookingAssignmentEnrichService enrichService = mock( BookingAssignmentEnrichService.class );
    private final WebClient webClient = mock( WebClient.class );
    private ExecutorService ioThreadPool;
    private ActivityLogSyncService service;

    @BeforeEach
    public void setUp() {
        ioThreadPool = Executors.newFixedThreadPool( 2 );
        service = new ActivityLogSyncService();
        ReflectionTestUtils.setField( service, "scraper", scraper );
        ReflectionTestUtils.setField( service, "dao", dao );
        ReflectionTestUtils.setField( service, "enrichService", enrichService );
        ReflectionTestUtils.setField( service, "ioThreadPool", ioThreadPool );
        service.setClock( Clock.fixed( NOW, ZoneOffset.UTC ) );
        when( dao.getDefaultOption( eq( "hbo_cloudbeds_request_timeout" ), anyString() ) ).thenReturn( "30" );
    }

    @AfterEach
    public void tearDown() {
        ioThreadPool.shutdownNow();
    }

    private static ActivityLogEntry entry( String title, String... identifiers ) {
        ActivityLogEntry e = new ActivityLogEntry();
        e.setTitle( title );
        e.getReservationIdentifiers().addAll( Arrays.asList( identifiers ) );
        return e;
    }

    private static List<ActivityLogEntry> fixture() throws IOException {
        try (Reader r = new InputStreamReader(
                ActivityLogSyncServiceTest.class.getClassLoader().getResourceAsStream( "activity_log_property.json" ),
                StandardCharsets.UTF_8 )) {
            return ActivityLogParser.parse( JsonParser.parseReader( r ).getAsJsonObject().getAsJsonArray( "aaData" ) );
        }
    }

    @Test
    public void floorToHalfHour() {
        assertThat( ActivityLogSyncService.floorToHalfHour( Instant.parse( "2026-10-05T09:00:00Z" ) ),
                is( Instant.parse( "2026-10-05T09:00:00Z" ) ) );
        assertThat( ActivityLogSyncService.floorToHalfHour( Instant.parse( "2026-10-05T09:29:59Z" ) ),
                is( Instant.parse( "2026-10-05T09:00:00Z" ) ) );
        assertThat( ActivityLogSyncService.floorToHalfHour( Instant.parse( "2026-10-05T09:31:00Z" ) ),
                is( Instant.parse( "2026-10-05T09:30:00Z" ) ) );
    }

    @Test
    public void dayChunksSplitAtUtcMidnight() {
        List<Instant[]> chunks = ActivityLogSyncService.dayChunks(
                Instant.parse( "2026-10-03T22:30:00Z" ), Instant.parse( "2026-10-05T01:00:00Z" ) );
        assertThat( chunks, hasSize( 3 ) );
        assertThat( chunks.get( 0 ), arrayContaining( Instant.parse( "2026-10-03T22:30:00Z" ), Instant.parse( "2026-10-04T00:00:00Z" ) ) );
        assertThat( chunks.get( 1 ), arrayContaining( Instant.parse( "2026-10-04T00:00:00Z" ), Instant.parse( "2026-10-05T00:00:00Z" ) ) );
        assertThat( chunks.get( 2 ), arrayContaining( Instant.parse( "2026-10-05T00:00:00Z" ), Instant.parse( "2026-10-05T01:00:00Z" ) ) );
    }

    @Test
    public void planRefreshesRelevantReservationsOnly() throws IOException {
        ActivityLogSyncService.SyncPlan plan = ActivityLogSyncService.plan( fixture() );

        assertThat( plan.identifiers, hasItems(
                "8837578952568", // channel update / status / room assignment removed
                "1535393630573", // channel booking (malformed link)
                "7905331903375", // room status modified
                "9211648941194", // payment added (credit card edited is ignored)
                "5698844246515", "1176176932510" ) ); // both casings of room type reassignment
        assertThat( plan.identifiers, not( hasItems( "1568513150270" ) ) ); // sensitive data viewed
        assertThat( plan.identifiers, not( hasItems( "8889407785921" ) ) ); // email manually sent
        assertThat( plan.identifiers, not( hasItems( "7775841864214" ) ) ); // card details deleted
        assertThat( plan.identifiers, not( hasItems( "8689445922276" ) ) ); // payment pending approval
        assertThat( plan.reservationIds, empty() );
        assertThat( plan.removed, empty() );
        assertThat( plan.unresolvedGuestEmails, contains( "guest.123456@guest.booking.com" ) );
        assertThat( plan.titleCounts.get( "Reservation reassigned to new room type" ), is( 2 ) );
        assertThat( plan.titleCounts.get( "Reservation updated via the Cloudbeds Channel Manager" ), is( 2 ) );
    }

    @Test
    public void guestEventResolvedFromGuestAssignedInSameWindow() {
        ActivityLogEntry assigned = entry( "Guest assigned to room", "1111111111111" );
        assigned.getGuestIds().add( "42" );
        assigned.getReservationIds().add( "900" );
        ActivityLogEntry modified = entry( "Guest Info modified" );
        modified.getGuestIds().add( "42" );
        modified.setGuestEmail( "someone@example.com" );

        ActivityLogSyncService.SyncPlan plan = ActivityLogSyncService.plan( List.of( modified, assigned ) );
        assertThat( plan.identifiers, contains( "1111111111111" ) );
        assertThat( plan.unresolvedGuestEmails, empty() );
    }

    @Test
    public void guestEventWithJunkEmailIsCountedNotSearched() {
        ActivityLogEntry modified = entry( "Guest Info modified" );
        modified.getGuestIds().add( "42" );
        modified.setGuestEmail( "not-an-email" );
        ActivityLogSyncService.SyncPlan plan = ActivityLogSyncService.plan( List.of( modified ) );
        assertThat( plan.unresolvedGuestEmails, empty() );
        assertThat( plan.unresolvedGuestEvents, is( 1 ) );
    }

    @Test
    public void unknownTitleReferencingReservationIsRefreshed() {
        ActivityLogSyncService.SyncPlan plan = ActivityLogSyncService.plan(
                List.of( entry( "Something new from Cloudbeds", "2222222222222" ) ) );
        assertThat( plan.identifiers, contains( "2222222222222" ) );
    }

    @Test
    public void selectsReservationsEndingTodayOrLater() {
        Customer past = customer( "1", "2026-10-04" );
        Customer today = customer( "2", "2026-10-05" );
        Customer future = customer( "3", "2026-11-01" );
        assertThat( ActivityLogSyncService.selectCurrentIdentifiers( List.of( past, today, future ), LocalDate.of( 2026, 10, 5 ) ),
                contains( "2", "3" ) );
    }

    @Test
    public void syncFromBootstrapRefreshesDedupedAndAdvancesWatermark() throws IOException {
        Instant start = Instant.parse( "2026-10-04T10:00:00Z" );
        Instant midnight = Instant.parse( "2026-10-05T00:00:00Z" );
        Instant end = Instant.parse( "2026-10-05T10:00:00Z" );
        when( scraper.getActivityLog( webClient, start, midnight ) ).thenReturn( new ArrayList<>( List.of(
                entry( "Room assigned", "1111111111111" ),
                entry( "Invoice Created", "3333333333333" ) ) ) );
        when( scraper.getActivityLog( webClient, midnight, end ) ).thenReturn( new ArrayList<>( List.of(
                entry( "Reservation dates modified", "1111111111111" ),
                entry( "Reservation removed", "4444444444444" ) ) ) );
        Reservation r = new Reservation();
        when( scraper.getReservationByIdentifier( webClient, "1111111111111" ) ).thenReturn( r );

        service.sync( webClient );

        verify( scraper ).getReservationByIdentifier( webClient, "1111111111111" );
        verify( scraper, never() ).getReservationByIdentifier( webClient, "3333333333333" );
        verify( scraper, never() ).getReservationByIdentifier( webClient, "4444444444444" );
        verify( enrichService ).applyReservationEnrich( r );
        verify( dao ).closeBookingAssignmentsForReservationIdentifier( "4444444444444" );
        verify( dao ).setOption( ActivityLogSyncService.OPTION_SYNCED_TO, end.toString() );
    }

    @Test
    public void syncResumesFromWatermark() throws IOException {
        when( dao.getOption( ActivityLogSyncService.OPTION_SYNCED_TO ) ).thenReturn( "2026-10-05T08:30:00Z" );
        when( scraper.getActivityLog( any(), any(), any() ) ).thenReturn( new ArrayList<>() );

        service.sync( webClient );

        verify( scraper ).getActivityLog( webClient, Instant.parse( "2026-10-05T08:30:00Z" ), Instant.parse( "2026-10-05T10:00:00Z" ) );
        verify( dao ).setOption( ActivityLogSyncService.OPTION_SYNCED_TO, "2026-10-05T10:00:00Z" );
    }

    @Test
    public void nothingToDoWhenAlreadySynced() throws IOException {
        when( dao.getOption( ActivityLogSyncService.OPTION_SYNCED_TO ) ).thenReturn( "2026-10-05T10:00:00Z" );
        service.sync( webClient );
        verify( scraper, never() ).getActivityLog( any(), any(), any() );
        verify( dao, never() ).setOption( anyString(), anyString() );
    }

    @Test
    public void watermarkNotAdvancedWhenARefreshFails() throws IOException {
        when( dao.getOption( ActivityLogSyncService.OPTION_SYNCED_TO ) ).thenReturn( "2026-10-05T09:00:00Z" );
        when( scraper.getActivityLog( any(), any(), any() ) ).thenReturn( new ArrayList<>( List.of(
                entry( "Room assigned", "1111111111111" ),
                entry( "Room assigned", "5555555555555" ) ) ) );
        Reservation ok = new Reservation();
        Reservation bad = new Reservation();
        when( scraper.getReservationByIdentifier( webClient, "1111111111111" ) ).thenReturn( ok );
        when( scraper.getReservationByIdentifier( webClient, "5555555555555" ) ).thenReturn( bad );
        doThrow( new IllegalStateException( "db down" ) ).when( enrichService ).applyReservationEnrich( bad );

        service.sync( webClient );

        verify( enrichService ).applyReservationEnrich( ok );
        verify( dao, never() ).setOption( anyString(), anyString() );
    }

    @Test
    public void fallsBackToSearchWhenIdentifierLookupFails() throws IOException {
        when( dao.getOption( ActivityLogSyncService.OPTION_SYNCED_TO ) ).thenReturn( "2026-10-05T09:00:00Z" );
        when( scraper.getActivityLog( any(), any(), any() ) ).thenReturn( new ArrayList<>( List.of(
                entry( "Room assigned", "1111111111111" ) ) ) );
        when( scraper.getReservationByIdentifier( webClient, "1111111111111" ) ).thenThrow( new IOException( "nope" ) );
        Customer c = customer( "1111111111111", "2026-10-10" );
        c.setId( "900" );
        when( scraper.getReservations( webClient, "1111111111111" ) ).thenReturn( List.of( c ) );
        Reservation r = new Reservation();
        when( scraper.getReservation( webClient, "900" ) ).thenReturn( r );

        service.sync( webClient );

        verify( enrichService ).applyReservationEnrich( r );
        verify( dao ).setOption( ActivityLogSyncService.OPTION_SYNCED_TO, "2026-10-05T10:00:00Z" );
    }

    private static Customer customer( String identifier, String checkout ) {
        Customer c = new Customer();
        c.setIdentifier( identifier );
        c.setCheckoutDate( checkout );
        return c;
    }
}
