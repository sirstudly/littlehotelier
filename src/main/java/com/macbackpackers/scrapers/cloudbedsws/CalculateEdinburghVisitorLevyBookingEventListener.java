
package com.macbackpackers.scrapers.cloudbedsws;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.macbackpackers.beans.JobStatus;
import com.macbackpackers.dao.WordPressDAO;
import com.macbackpackers.jobs.CalculateEdinburghVisitorLevyForBookingJob;
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

        Set<String> reservationIdsSeenInBatch = new HashSet<>();
        for ( CloudbedsCalendarEvent event : events ) {
            if ( false == edinburghVisitorLevyService.isPotentiallyEligibleForNewBooking( event ) ) {
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
}
