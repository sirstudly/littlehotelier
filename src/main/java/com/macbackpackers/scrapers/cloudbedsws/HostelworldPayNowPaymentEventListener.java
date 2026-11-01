package com.macbackpackers.scrapers.cloudbedsws;

import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.apache.commons.lang3.StringUtils;
import org.htmlunit.WebClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import com.macbackpackers.beans.JobStatus;
import com.macbackpackers.beans.cloudbeds.responses.Reservation;
import com.macbackpackers.dao.WordPressDAO;
import com.macbackpackers.jobs.CalculateEdinburghVisitorLevyForBookingJob;
import com.macbackpackers.jobs.CreateHostelworldPayNowPaymentJob;
import com.macbackpackers.jobs.HostelworldPayNowPaymentJob;
import com.macbackpackers.scrapers.CloudbedsScraper;
import com.macbackpackers.services.EdinburghVisitorLevyJobSupport;
import com.macbackpackers.services.PaymentProcessorService;

/**
 * Enqueues {@link HostelworldPayNowPaymentJob}s for Hostelworld channel-collect non-refundable
 * (Pay Now) bookings seen on the calendar WebSocket (assigned Events and NonAssigned rows).
 * Ignores the initial snapshot.
 * <p>
 * When Cloudbeds assigns via {@code delete.NonAssignedReservations} without Events:
 * <ul>
 * <li>cache hit on {@code booking_rooms.id} → {@code get_reservation} and enqueue if still
 * eligible / folio unhealthy;</li>
 * <li>cache miss (orphan delete) with no assigned Events → debounced
 * {@link CreateHostelworldPayNowPaymentJob} ({@code days_back=1}).</li>
 * </ul>
 * Depends on {@link CalculateEdinburghVisitorLevyForBookingJob} when EVL is enabled.
 */
@Component
public class HostelworldPayNowPaymentEventListener implements CloudbedsEventListener {

    private static final Logger LOGGER = LoggerFactory.getLogger( HostelworldPayNowPaymentEventListener.class );

    /** Coalesce orphan NonAssigned-delete reconciles per property. */
    static final long ORPHAN_RECONCILE_DEBOUNCE_SECONDS = 180;

    @Autowired
    private WordPressDAO dao;

    @Autowired
    private CloudbedsScraper cloudbedsScraper;

    @Autowired
    private EdinburghVisitorLevyJobSupport edinburghVisitorLevyJobSupport;

    @Autowired
    private CloudbedsCalendarEventRegistry eventRegistry;

    @Autowired
    private ApplicationContext context;

    private final ScheduledExecutorService orphanReconcileExecutor = Executors.newSingleThreadScheduledExecutor( r -> {
        Thread t = new Thread( r, "hwl-paynow-orphan-reconcile" );
        t.setDaemon( true );
        return t;
    } );

    private final ConcurrentHashMap<String, ScheduledFuture<?>> pendingOrphanReconcileByProperty =
            new ConcurrentHashMap<>();

    @Override
    public void onSnapshot( String propertyId, List<CloudbedsCalendarEvent> events ) {
        // intentionally ignored: only react to live booking changes
    }

    @Override
    public void onUpdate( String propertyId, CloudbedsCalendarUpdate update ) {
        if ( update == null ) {
            return;
        }
        // Includes assigned Events and NonAssignedReservations (when CB sends them).
        processReservationEvents( update.getAllReservationEvents() );
        processNonAssignedDeletes( propertyId, update );
    }

    private void processReservationEvents( List<CloudbedsCalendarEvent> events ) {
        if ( events == null || events.isEmpty() ) {
            return;
        }
        Set<String> eligibleSources = getEligibleSubSourceIds();
        if ( eligibleSources.isEmpty() ) {
            LOGGER.warn( "No Hostelworld Pay Now booking-source ids resolved; skipping Pay Now payment checks" );
            return;
        }

        Set<String> reservationIdsSeenInBatch = new HashSet<>();
        for ( CloudbedsCalendarEvent event : events ) {
            if ( false == HostelworldPayNowPaymentCriteria.matchesCalendarEvent( event, eligibleSources ) ) {
                continue;
            }
            String reservationId = event.getBookingId();
            if ( false == reservationIdsSeenInBatch.add( reservationId ) ) {
                continue;
            }
            if ( dao.hasPendingHostelworldPayNowPaymentJobForReservation( reservationId ) ) {
                LOGGER.info( "Skipping HostelworldPayNowPaymentJob for reservation {} (job already pending)",
                        reservationId );
                continue;
            }
            if ( dao.hasCompletedHostelworldPayNowPaymentJobForReservation( reservationId ) ) {
                LOGGER.info( "Skipping HostelworldPayNowPaymentJob for reservation {} (already completed)",
                        reservationId );
                continue;
            }
            enqueuePayNowJob( event.getThirdPartyIdentifier(), event.getStatus(),
                    event.getFirstName(), event.getLastName(), reservationId );
        }
    }

    private void processNonAssignedDeletes( String propertyId, CloudbedsCalendarUpdate update ) {
        List<String> deletedRoomsIds = update.getDeleteSection().get( "NonAssignedReservations" );
        if ( deletedRoomsIds == null || deletedRoomsIds.isEmpty() ) {
            return;
        }

        boolean orphanUnresolved = false;
        Set<String> reservationIdsSeen = new HashSet<>();
        for ( String bookingRoomsId : deletedRoomsIds ) {
            if ( StringUtils.isBlank( bookingRoomsId ) ) {
                continue;
            }
            CloudbedsCalendarEventRegistry.ResolvedBookingId resolved =
                    eventRegistry.resolveRemovedBookingRoomsBookingId( propertyId, bookingRoomsId );
            if ( false == resolved.isKnown() ) {
                orphanUnresolved = true;
                continue;
            }
            String reservationId = resolved.getBookingId();
            if ( false == reservationIdsSeen.add( reservationId ) ) {
                continue;
            }
            tryEnqueuePayNowFromReservation( reservationId );
        }

        // Eden-class gap: delete NonAssigned with no prior sighting and no assigned Events in frame.
        if ( orphanUnresolved && update.getEvents().isEmpty() ) {
            scheduleOrphanReconcile( propertyId );
        }
    }

    private void tryEnqueuePayNowFromReservation( String reservationId ) {
        if ( dao.hasPendingHostelworldPayNowPaymentJobForReservation( reservationId ) ) {
            LOGGER.info( "Skipping HostelworldPayNowPaymentJob for reservation {} (job already pending)",
                    reservationId );
            return;
        }
        if ( dao.hasCompletedHostelworldPayNowPaymentJobForReservation( reservationId ) ) {
            LOGGER.info( "Skipping HostelworldPayNowPaymentJob for reservation {} (already completed)",
                    reservationId );
            return;
        }
        try ( WebClient webClient = context.getBean( "webClientForCloudbeds", WebClient.class ) ) {
            Reservation res = cloudbedsScraper.getReservation( webClient, reservationId );
            if ( res == null || StringUtils.isBlank( res.getReservationId() ) ) {
                return;
            }
            if ( "canceled".equalsIgnoreCase( res.getStatus() ) ) {
                return;
            }
            if ( false == res.isChannelCollectBooking() || false == res.isNonRefundable() ) {
                return;
            }
            if ( PaymentProcessorService.isHealthyChannelCollectPrepaidFolio( res ) ) {
                LOGGER.info( "Skipping HostelworldPayNowPaymentJob for reservation {} (folio already healthy)",
                        reservationId );
                return;
            }
            enqueuePayNowJob( res.getThirdPartyIdentifier(), res.getStatus(),
                    res.getFirstName(), res.getLastName(), reservationId );
        }
        catch ( Exception e ) {
            LOGGER.warn( "Unable to evaluate Hostelworld Pay Now enqueue for reservation {}: {}",
                    reservationId, e.toString() );
        }
    }

    private void enqueuePayNowJob( String thirdPartyId, String status, String firstName, String lastName,
            String reservationId ) {
        LOGGER.info( "Creating a HostelworldPayNowPaymentJob for booking {} ({}): {} {}",
                thirdPartyId, status, firstName, lastName );
        CalculateEdinburghVisitorLevyForBookingJob evlJob =
                edinburghVisitorLevyJobSupport.findOrCreatePendingJob( reservationId );
        HostelworldPayNowPaymentJob payJob = new HostelworldPayNowPaymentJob();
        payJob.setStatus( JobStatus.submitted );
        payJob.setReservationId( reservationId );
        if ( evlJob != null ) {
            payJob.getDependentJobs().add( evlJob );
        }
        dao.insertJob( payJob );
    }

    void scheduleOrphanReconcile( String propertyId ) {
        String key = StringUtils.defaultIfBlank( propertyId, "_" );
        ScheduledFuture<?> prev = pendingOrphanReconcileByProperty.get( key );
        if ( prev != null ) {
            prev.cancel( false );
        }
        ScheduledFuture<?> next = orphanReconcileExecutor.schedule(
                () -> runOrphanReconcile( key ),
                ORPHAN_RECONCILE_DEBOUNCE_SECONDS,
                TimeUnit.SECONDS );
        pendingOrphanReconcileByProperty.put( key, next );
        LOGGER.info( "Scheduled debounced CreateHostelworldPayNowPaymentJob for property {} in {}s "
                        + "(orphan NonAssigned delete)",
                key, ORPHAN_RECONCILE_DEBOUNCE_SECONDS );
    }

    private void runOrphanReconcile( String propertyKey ) {
        pendingOrphanReconcileByProperty.remove( propertyKey );
        try {
            if ( dao.hasPendingCreateHostelworldPayNowPaymentJob() ) {
                LOGGER.info( "Skipping CreateHostelworldPayNowPaymentJob reconcile (already pending)" );
                return;
            }
            CreateHostelworldPayNowPaymentJob createJob = new CreateHostelworldPayNowPaymentJob();
            createJob.setStatus( JobStatus.submitted );
            createJob.setDaysBack( 1 );
            dao.insertJob( createJob );
            LOGGER.info( "Enqueued CreateHostelworldPayNowPaymentJob days_back=1 after orphan "
                    + "NonAssigned delete (property {})", propertyKey );
        }
        catch ( Exception e ) {
            LOGGER.warn( "Failed to enqueue CreateHostelworldPayNowPaymentJob after orphan delete: {}",
                    e.toString() );
        }
    }

    private Set<String> getEligibleSubSourceIds() {
        try ( WebClient webClient = context.getBean( "webClientForCloudbeds", WebClient.class ) ) {
            return cloudbedsScraper.lookupHostelworldPayNowSubSourceIds( webClient );
        }
        catch ( IOException e ) {
            LOGGER.warn( "Unable to resolve Hostelworld Pay Now booking-source ids: {}", e.getMessage() );
            return Collections.emptySet();
        }
    }

    /** Visible for tests. */
    Map<String, ScheduledFuture<?>> getPendingOrphanReconcileByProperty() {
        return pendingOrphanReconcileByProperty;
    }
}
