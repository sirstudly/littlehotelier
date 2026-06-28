
package com.macbackpackers.scrapers.cloudbedsws;

import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.htmlunit.WebClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import com.macbackpackers.beans.JobStatus;
import com.macbackpackers.dao.WordPressDAO;
import com.macbackpackers.jobs.CalculateEdinburghVisitorLevyForBookingJob;
import com.macbackpackers.scrapers.CloudbedsScraper;
import com.macbackpackers.services.EdinburghVisitorLevyService;

/**
 * Reacts to incremental calendar WebSocket updates by enqueueing
 * {@link CalculateEdinburghVisitorLevyForBookingJob}s for new {@code booked} events where a visitor
 * levy adjustment may apply, using the same cheap eligibility checks as
 * {@code EdinburghVisitorLevyService#isPotentiallyEligible}.
 * <p>
 * Only {@link #onUpdate} is handled; the initial {@code on_migrate} snapshot is ignored so
 * existing bookings are not processed when the monitor connects or reconnects.
 */
@Component
public class CalculateEdinburghVisitorLevyBookingEventListener implements CloudbedsEventListener {

    private static final Logger LOGGER = LoggerFactory.getLogger( CalculateEdinburghVisitorLevyBookingEventListener.class );

    @Autowired
    private WordPressDAO dao;

    @Autowired
    private EdinburghVisitorLevyService edinburghVisitorLevyService;

    @Autowired
    private CloudbedsScraper cloudbedsScraper;

    @Autowired
    private ApplicationContext context;

    /** Avoid duplicate inserts when Cloudbeds sends multiple room rows for one booking. */
    private final Set<String> recentlyEnqueuedReservationIds = ConcurrentHashMap.newKeySet();

    @Override
    public void onSnapshot( String propertyId, List<CloudbedsCalendarEvent> events ) {
        // intentionally ignored: only react to live booking changes, not the bulk snapshot
    }

    @Override
    public void onUpdate( String propertyId, CloudbedsCalendarUpdate update ) {
        if ( update == null || false == dao.isCloudbeds() ) {
            return;
        }
        processReservationEvents( update.getAllReservationEvents() );
    }

    private void processReservationEvents( List<CloudbedsCalendarEvent> events ) {
        if ( events == null || events.isEmpty() ) {
            return;
        }

        Set<String> inclusiveTaxSources = getInclusiveTaxSubSourceIds();
        Set<String> reservationIdsSeenInBatch = new HashSet<>();
        for ( CloudbedsCalendarEvent event : events ) {
            if ( false == edinburghVisitorLevyService.isPotentiallyEligibleForNewBooking( event, inclusiveTaxSources ) ) {
                continue;
            }
            String reservationId = event.getBookingId();
            if ( false == reservationIdsSeenInBatch.add( reservationId ) ) {
                continue;
            }
            if ( recentlyEnqueuedReservationIds.contains( reservationId ) ) {
                continue;
            }
            if ( dao.hasCalculateEdinburghVisitorLevyJobForReservation( reservationId ) ) {
                recentlyEnqueuedReservationIds.add( reservationId );
                LOGGER.info( "Skipping CalculateEdinburghVisitorLevyForBookingJob for reservation {} (job already pending)",
                        reservationId );
                continue;
            }
            enqueueCalculateJob( event, reservationId );
        }
    }

    private void enqueueCalculateJob( CloudbedsCalendarEvent event, String reservationId ) {
        LOGGER.info( "Creating CalculateEdinburghVisitorLevyForBookingJob for booking {} ({}): {} {}",
                reservationId, event.getStatus(), event.getFirstName(), event.getLastName() );
        CalculateEdinburghVisitorLevyForBookingJob job = new CalculateEdinburghVisitorLevyForBookingJob();
        job.setStatus( JobStatus.submitted );
        job.setReservationId( reservationId );
        dao.insertJob( job );
        recentlyEnqueuedReservationIds.add( reservationId );
    }

    private Set<String> getInclusiveTaxSubSourceIds() {
        try ( WebClient webClient = context.getBean( "webClientForCloudbeds", WebClient.class ) ) {
            return cloudbedsScraper.lookupInclusiveTaxSubSourceIds( webClient );
        }
        catch ( IOException e ) {
            LOGGER.error( "Unable to resolve inclusive-tax booking-source ids: {}", e.getMessage() );
            return Collections.emptySet();
        }
    }
}
