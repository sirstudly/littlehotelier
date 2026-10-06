package com.macbackpackers.services;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.apache.commons.lang3.StringUtils;
import org.htmlunit.WebClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import com.macbackpackers.beans.cloudbeds.responses.ActivityLogEntry;
import com.macbackpackers.beans.cloudbeds.responses.Customer;
import com.macbackpackers.beans.cloudbeds.responses.Reservation;
import com.macbackpackers.dao.WordPressDAO;
import com.macbackpackers.scrapers.CloudbedsScraper;

/**
 * Daily catch-up of {@code wp_lh_booking_assignment} from the Cloudbeds property activity log.
 * Events are only used as triggers: every reservation referenced by a relevant event since the
 * last sync is REST-fetched and re-applied. The only stored state is the watermark option
 * {@value #OPTION_SYNCED_TO} (UTC, on a half-hour boundary).
 */
@Service
public class ActivityLogSyncService {

    private static final Logger LOGGER = LoggerFactory.getLogger( ActivityLogSyncService.class );

    static final String OPTION_SYNCED_TO = "hbo_cloudbeds_activity_log_synced_to";

    /** How far back the first sync (no watermark) looks. */
    static final Duration BOOTSTRAP_LOOKBACK = Duration.ofHours( 24 );

    private static final long HALF_HOUR_SECONDS = TimeUnit.MINUTES.toSeconds( 30 );

    /** An email matching more reservations than this is treated as junk (eg. asd@asd.com). */
    static final int MAX_GUEST_EMAIL_MATCHES = 5;

    @Autowired
    private CloudbedsScraper scraper;

    @Autowired
    private WordPressDAO dao;

    @Autowired
    private BookingAssignmentEnrichService enrichService;

    @Autowired
    @Qualifier( "ioThreadPool" )
    private ExecutorService ioThreadPool;

    private Clock clock = Clock.systemUTC();

    void setClock( Clock clock ) {
        this.clock = clock;
    }

    /**
     * Fetches the activity log from the watermark (or {@link #BOOTSTRAP_LOOKBACK} ago) up to the
     * last half-hour boundary, refreshes every referenced reservation and closes removed ones.
     * The watermark only advances when every reservation was refreshed.
     *
     * @param webClient web client instance to use
     * @throws IOException if the activity log could not be read
     */
    public void sync( WebClient webClient ) throws IOException {
        Instant now = clock.instant();
        Instant end = floorToHalfHour( now );
        Instant start = readWatermark();
        if ( start == null ) {
            start = floorToHalfHour( now.minus( BOOTSTRAP_LOOKBACK ) );
            LOGGER.info( "Activity log sync: no watermark, starting from {}", start );
        }
        if ( false == start.isBefore( end ) ) {
            LOGGER.info( "Activity log sync: already synced to {}", start );
            return;
        }

        List<ActivityLogEntry> entries = new ArrayList<>();
        for ( Instant[] chunk : dayChunks( start, end ) ) {
            List<ActivityLogEntry> chunkEntries = scraper.getActivityLog( webClient, chunk[0], chunk[1] );
            LOGGER.info( "Activity log sync: {} event(s) between {} and {}", chunkEntries.size(), chunk[0], chunk[1] );
            entries.addAll( chunkEntries );
        }

        SyncPlan plan = plan( entries );
        LOGGER.info( "Activity log sync: events by title {}", plan.titleCounts );
        for ( String email : plan.unresolvedGuestEmails ) {
            plan.identifiers.addAll( findIdentifiersByGuestEmail( webClient, email ) );
        }
        if ( plan.unresolvedGuestEvents > 0 ) {
            LOGGER.info( "Activity log sync: {} guest event(s) without a reservation or usable email",
                    plan.unresolvedGuestEvents );
        }

        for ( String identifier : plan.removed ) {
            int closed = dao.closeBookingAssignmentsForReservationIdentifier( identifier );
            LOGGER.info( "Activity log sync: reservation {} removed, closed {} assignment(s)", identifier, closed );
        }
        plan.identifiers.removeAll( plan.removed );

        LOGGER.info( "Activity log sync: refreshing {} reservation(s) by identifier, {} by id",
                plan.identifiers.size(), plan.reservationIds.size() );
        List<String> failed = refreshAll( webClient, plan );
        if ( failed.isEmpty() ) {
            dao.setOption( OPTION_SYNCED_TO, end.toString() );
            LOGGER.info( "Activity log sync: synced to {}", end );
        }
        else {
            LOGGER.error( "Activity log sync: {} reservation(s) failed to refresh, watermark left at {}: {}",
                    failed.size(), start, failed );
        }
    }

    private Instant readWatermark() {
        String value = dao.getOption( OPTION_SYNCED_TO );
        if ( StringUtils.isBlank( value ) ) {
            return null;
        }
        try {
            return floorToHalfHour( Instant.parse( value.trim() ) );
        }
        catch ( DateTimeParseException e ) {
            LOGGER.warn( "Activity log sync: ignoring unparseable watermark {}", value );
            return null;
        }
    }

    /**
     * Rounds down to the previous :00 or :30 (the only times the Cloudbeds activity log filter accepts).
     */
    static Instant floorToHalfHour( Instant instant ) {
        long seconds = instant.getEpochSecond();
        return Instant.ofEpochSecond( seconds - Math.floorMod( seconds, HALF_HOUR_SECONDS ) );
    }

    /**
     * Splits {@code [start, end)} into windows that don't cross UTC midnight.
     */
    static List<Instant[]> dayChunks( Instant start, Instant end ) {
        List<Instant[]> chunks = new ArrayList<>();
        Instant chunkStart = start;
        while ( chunkStart.isBefore( end ) ) {
            Instant nextMidnight = chunkStart.truncatedTo( ChronoUnit.DAYS ).plus( 1, ChronoUnit.DAYS );
            Instant chunkEnd = nextMidnight.isBefore( end ) ? nextMidnight : end;
            chunks.add( new Instant[] { chunkStart, chunkEnd } );
            chunkStart = chunkEnd;
        }
        return chunks;
    }

    /**
     * Classifies the events: reservations to refresh, reservations removed and guest-only events
     * that need resolving to a reservation.
     */
    static SyncPlan plan( List<ActivityLogEntry> entries ) {
        SyncPlan plan = new SyncPlan();

        // guest id -> reservations, from events naming both (eg. "Guest assigned to room")
        Map<String, Set<String>> identifiersByGuest = new HashMap<>();
        for ( ActivityLogEntry e : entries ) {
            if ( false == e.getReservationIdentifiers().isEmpty() ) {
                for ( String guestId : e.getGuestIds() ) {
                    identifiersByGuest.computeIfAbsent( guestId, k -> new LinkedHashSet<>() )
                            .addAll( e.getReservationIdentifiers() );
                }
            }
        }

        for ( ActivityLogEntry e : entries ) {
            String title = StringUtils.defaultIfBlank( e.getTitle(), "(no title)" );
            plan.titleCounts.merge( title, 1, Integer::sum );
            if ( ActivityLogClassifier.isIgnored( e ) ) {
                continue;
            }
            if ( ActivityLogClassifier.isReservationRemoved( e ) ) {
                plan.removed.addAll( e.getReservationIdentifiers() );
                continue;
            }
            if ( false == e.getReservationIdentifiers().isEmpty() ) {
                plan.identifiers.addAll( e.getReservationIdentifiers() );
            }
            else if ( false == e.getReservationIds().isEmpty() ) {
                plan.reservationIds.addAll( e.getReservationIds() );
            }
            else if ( false == e.getGuestIds().isEmpty() ) {
                Set<String> resolved = new LinkedHashSet<>();
                for ( String guestId : e.getGuestIds() ) {
                    resolved.addAll( identifiersByGuest.getOrDefault( guestId, Set.of() ) );
                }
                if ( false == resolved.isEmpty() ) {
                    plan.identifiers.addAll( resolved );
                }
                else if ( isUsableEmail( e.getGuestEmail() ) ) {
                    plan.unresolvedGuestEmails.add( e.getGuestEmail().trim().toLowerCase() );
                }
                else {
                    plan.unresolvedGuestEvents++;
                }
            }
        }
        return plan;
    }

    static boolean isUsableEmail( String email ) {
        if ( StringUtils.isBlank( email ) ) {
            return false;
        }
        String e = email.trim();
        int at = e.indexOf( '@' );
        return at > 0 && e.indexOf( '.', at ) > at + 1 && false == e.endsWith( "." );
    }

    /**
     * Reservations for the guest email whose stay ends today or later; none if the email looks like
     * junk (matches too many reservations).
     */
    private List<String> findIdentifiersByGuestEmail( WebClient webClient, String email ) {
        List<Customer> matches;
        try {
            matches = scraper.getReservations( webClient, email );
        }
        catch ( IOException | RuntimeException ex ) {
            LOGGER.warn( "Activity log sync: guest email search failed for {}", email, ex );
            return List.of();
        }
        if ( matches.size() > MAX_GUEST_EMAIL_MATCHES ) {
            LOGGER.info( "Activity log sync: skipping guest email {} matching {} reservations", email, matches.size() );
            return List.of();
        }
        List<String> identifiers = selectCurrentIdentifiers( matches, LocalDate.now( clock.withZone( ZoneOffset.UTC ) ) );
        if ( identifiers.isEmpty() ) {
            LOGGER.info( "Activity log sync: no current reservation for guest email {}", email );
        }
        return identifiers;
    }

    static List<String> selectCurrentIdentifiers( List<Customer> matches, LocalDate today ) {
        List<String> identifiers = new ArrayList<>();
        for ( Customer c : matches ) {
            String checkout = c.getCheckoutDate();
            if ( StringUtils.isBlank( c.getIdentifier() ) || StringUtils.length( checkout ) < 10 ) {
                continue;
            }
            try {
                if ( false == LocalDate.parse( checkout.substring( 0, 10 ) ).isBefore( today ) ) {
                    identifiers.add( c.getIdentifier() );
                }
            }
            catch ( DateTimeParseException ignored ) {
                // skip
            }
        }
        return identifiers;
    }

    /**
     * REST-fetches and applies every reservation in the plan in parallel, continuing past failures.
     *
     * @return identifiers / ids that failed to refresh
     */
    private List<String> refreshAll( WebClient webClient, SyncPlan plan ) {
        var mdcContext = MDC.getCopyOfContextMap();
        Map<String, Future<?>> futures = new LinkedHashMap<>();
        for ( String identifier : plan.identifiers ) {
            futures.put( identifier, ioThreadPool.submit( withMdc( mdcContext,
                    () -> refreshByIdentifier( webClient, identifier ) ) ) );
        }
        for ( String reservationId : plan.reservationIds ) {
            futures.put( "id:" + reservationId, ioThreadPool.submit( withMdc( mdcContext,
                    () -> enrichService.applyReservationEnrich( scraper.getReservation( webClient, reservationId ) ) ) ) );
        }

        int requestTimeout = Integer.parseInt( dao.getDefaultOption( "hbo_cloudbeds_request_timeout", "900" ) );
        List<String> failed = new ArrayList<>();
        for ( Map.Entry<String, Future<?>> f : futures.entrySet() ) {
            try {
                f.getValue().get( requestTimeout, TimeUnit.SECONDS );
            }
            catch ( TimeoutException | ExecutionException e ) {
                LOGGER.error( "Activity log sync: failed to refresh {}", f.getKey(), e );
                f.getValue().cancel( true );
                failed.add( f.getKey() );
            }
            catch ( InterruptedException e ) {
                Thread.currentThread().interrupt();
                failed.add( f.getKey() );
                break;
            }
        }
        return failed;
    }

    /**
     * Loads by identifier, falling back to the reservation search when Cloudbeds can't resolve it.
     */
    private void refreshByIdentifier( WebClient webClient, String identifier ) throws IOException {
        Reservation r;
        try {
            r = scraper.getReservationByIdentifier( webClient, identifier );
        }
        catch ( IOException | RuntimeException ex ) {
            LOGGER.info( "Activity log sync: lookup by identifier {} failed ({}), trying search", identifier, ex.getMessage() );
            String reservationId = findReservationIdByIdentifier( webClient, identifier );
            if ( reservationId == null ) {
                LOGGER.warn( "Activity log sync: reservation {} not found, skipping", identifier );
                return;
            }
            r = scraper.getReservation( webClient, reservationId );
        }
        enrichService.applyReservationEnrich( r );
    }

    private String findReservationIdByIdentifier( WebClient webClient, String identifier ) throws IOException {
        for ( Customer c : scraper.getReservations( webClient, identifier ) ) {
            if ( identifier.equals( c.getIdentifier() ) ) {
                return c.getId();
            }
        }
        return null;
    }

    private static java.util.concurrent.Callable<Void> withMdc( Map<String, String> mdcContext, IORunnable task ) {
        return () -> {
            if ( mdcContext != null ) {
                MDC.setContextMap( mdcContext );
            }
            try {
                task.run();
                return null;
            }
            finally {
                MDC.clear();
            }
        };
    }

    @FunctionalInterface
    private interface IORunnable {
        void run() throws IOException;
    }

    /** Outcome of {@link #plan}. */
    static class SyncPlan {
        final Set<String> identifiers = new LinkedHashSet<>();
        final Set<String> reservationIds = new LinkedHashSet<>();
        final Set<String> removed = new LinkedHashSet<>();
        final Set<String> unresolvedGuestEmails = new LinkedHashSet<>();
        final Map<String, Integer> titleCounts = new TreeMap<>( String.CASE_INSENSITIVE_ORDER );
        int unresolvedGuestEvents;
    }
}
