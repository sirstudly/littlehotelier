
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
import com.macbackpackers.jobs.ChargeNonRefundableBookingJob;
import com.macbackpackers.scrapers.CloudbedsScraper;

/**
 * Reacts to incremental calendar WebSocket updates by enqueueing
 * {@link ChargeNonRefundableBookingJob}s for new non-refundable hotel-collect bookings, using the
 * same criteria as {@code CreateChargeNonRefundableBookingJob}.
 * <p>
 * Only {@link #onUpdate} is handled; the initial {@code on_migrate} snapshot is ignored so
 * existing bookings are not charged when the monitor connects or reconnects.
 */
@Component
public class ChargeNonRefundableBookingEventListener implements CloudbedsEventListener {

    private static final Logger LOGGER = LoggerFactory.getLogger( ChargeNonRefundableBookingEventListener.class );

    @Autowired
    private WordPressDAO dao;

    @Autowired
    private CloudbedsScraper cloudbedsScraper;

    @Autowired
    private ApplicationContext context;

    /** Avoid duplicate inserts when Cloudbeds sends multiple room rows for one booking. */
    private final Set<String> recentlyEnqueuedReservationIds = ConcurrentHashMap.newKeySet();

    private volatile Set<String> eligibleSubSourceIds = Collections.emptySet();
    private volatile long eligibleSubSourceIdsLoadedAt;

    private static final long SOURCE_LOOKUP_TTL_MS = 24L * 60L * 60L * 1000L;

    @Override
    public void onSnapshot( String propertyId, List<CloudbedsCalendarEvent> events ) {
        // intentionally ignored: only react to live booking changes, not the bulk snapshot
    }

    @Override
    public void onUpdate( String propertyId, CloudbedsCalendarUpdate update ) {
        if ( update == null ) {
            return;
        }
        processReservationEvents( propertyId, update.getAllReservationEvents() );
    }

    private void processReservationEvents( String propertyId, List<CloudbedsCalendarEvent> events ) {
        if ( events == null || events.isEmpty() ) {
            return;
        }
        Set<String> eligibleSources = getEligibleSubSourceIds();
        if ( eligibleSources.isEmpty() ) {
            LOGGER.warn( "No eligible booking-source ids resolved; skipping non-refundable charge checks" );
            return;
        }

        Set<String> reservationIdsSeenInBatch = new HashSet<>();
        for ( CloudbedsCalendarEvent event : events ) {
            if ( false == NonRefundableBookingCriteria.matchesCalendarEvent( event, eligibleSources ) ) {
                continue;
            }
            String reservationId = event.getBookingId();
            if ( false == reservationIdsSeenInBatch.add( reservationId ) ) {
                continue;
            }
            if ( recentlyEnqueuedReservationIds.contains( reservationId ) ) {
                continue;
            }
            if ( dao.hasChargeNonRefundableJobForReservation( reservationId ) ) {
                recentlyEnqueuedReservationIds.add( reservationId );
                LOGGER.info( "Skipping ChargeNonRefundableBookingJob for reservation {} (charge attempted within last {} hours or job pending)",
                        reservationId, WordPressDAO.NON_REFUNDABLE_CHARGE_COOLDOWN_HOURS );
                continue;
            }
            enqueueChargeJob( event, reservationId );
        }
    }

    private void enqueueChargeJob( CloudbedsCalendarEvent event, String reservationId ) {
        LOGGER.info( "Creating a ChargeNonRefundableBookingJob for booking {} ({}): {} {}",
                event.getThirdPartyIdentifier(), event.getStatus(),
                event.getFirstName(), event.getLastName() );
        ChargeNonRefundableBookingJob chargeJob = new ChargeNonRefundableBookingJob();
        chargeJob.setStatus( JobStatus.submitted );
        chargeJob.setReservationId( reservationId );
        dao.insertJob( chargeJob );
        recentlyEnqueuedReservationIds.add( reservationId );
    }

    private Set<String> getEligibleSubSourceIds() {
        long now = System.currentTimeMillis();
        if ( false == eligibleSubSourceIds.isEmpty() && now - eligibleSubSourceIdsLoadedAt < SOURCE_LOOKUP_TTL_MS ) {
            return eligibleSubSourceIds;
        }
        synchronized ( this ) {
            now = System.currentTimeMillis();
            if ( false == eligibleSubSourceIds.isEmpty() && now - eligibleSubSourceIdsLoadedAt < SOURCE_LOOKUP_TTL_MS ) {
                return eligibleSubSourceIds;
            }
            try ( WebClient webClient = context.getBean( "webClientForCloudbeds", WebClient.class ) ) {
                String lookup = cloudbedsScraper.lookupBookingSourceIds( webClient,
                        NonRefundableBookingCriteria.ELIGIBLE_SOURCE_NAMES );
                eligibleSubSourceIds = NonRefundableBookingCriteria.parseSubSourceIds( lookup );
                eligibleSubSourceIdsLoadedAt = now;
                LOGGER.info( "Resolved eligible non-refundable booking sub-source ids: {}", eligibleSubSourceIds );
            }
            catch ( IOException e ) {
                LOGGER.warn( "Unable to resolve eligible booking-source ids: {}", e.getMessage() );
            }
            return eligibleSubSourceIds;
        }
    }
}
