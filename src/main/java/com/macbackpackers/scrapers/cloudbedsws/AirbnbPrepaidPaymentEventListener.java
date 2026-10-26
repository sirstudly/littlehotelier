package com.macbackpackers.scrapers.cloudbedsws;

import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.htmlunit.WebClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import com.macbackpackers.beans.JobStatus;
import com.macbackpackers.dao.WordPressDAO;
import com.macbackpackers.jobs.AirbnbPrepaidPaymentJob;
import com.macbackpackers.jobs.CalculateEdinburghVisitorLevyForBookingJob;
import com.macbackpackers.scrapers.CloudbedsScraper;
import com.macbackpackers.services.EdinburghVisitorLevyJobSupport;

/**
 * Enqueues {@link AirbnbPrepaidPaymentJob}s for new Airbnb (API) channel-collect bookings seen on
 * the calendar WebSocket. Ignores the initial snapshot. Depends on
 * {@link CalculateEdinburghVisitorLevyForBookingJob} when EVL is enabled.
 */
@Component
public class AirbnbPrepaidPaymentEventListener implements CloudbedsEventListener {

    private static final Logger LOGGER = LoggerFactory.getLogger( AirbnbPrepaidPaymentEventListener.class );

    @Autowired
    private WordPressDAO dao;

    @Autowired
    private CloudbedsScraper cloudbedsScraper;

    @Autowired
    private EdinburghVisitorLevyJobSupport edinburghVisitorLevyJobSupport;

    @Autowired
    private ApplicationContext context;

    @Override
    public void onSnapshot( String propertyId, List<CloudbedsCalendarEvent> events ) {
        // intentionally ignored: only react to live booking changes
    }

    @Override
    public void onUpdate( String propertyId, CloudbedsCalendarUpdate update ) {
        if ( update == null ) {
            return;
        }
        processReservationEvents( update.getAllReservationEvents() );
    }

    private void processReservationEvents( List<CloudbedsCalendarEvent> events ) {
        if ( events == null || events.isEmpty() ) {
            return;
        }
        Set<String> eligibleSources = getEligibleSubSourceIds();
        if ( eligibleSources.isEmpty() ) {
            LOGGER.warn( "No Airbnb prepaid booking-source ids resolved; skipping Airbnb prepaid payment checks" );
            return;
        }

        Set<String> reservationIdsSeenInBatch = new HashSet<>();
        for ( CloudbedsCalendarEvent event : events ) {
            if ( false == AirbnbPrepaidPaymentCriteria.matchesCalendarEvent( event, eligibleSources ) ) {
                continue;
            }
            String reservationId = event.getBookingId();
            if ( false == reservationIdsSeenInBatch.add( reservationId ) ) {
                continue;
            }
            if ( dao.hasPendingAirbnbPrepaidPaymentJobForReservation( reservationId ) ) {
                LOGGER.info( "Skipping AirbnbPrepaidPaymentJob for reservation {} (job already pending)",
                        reservationId );
                continue;
            }
            if ( dao.hasCompletedAirbnbPrepaidPaymentJobForReservation( reservationId ) ) {
                LOGGER.info( "Skipping AirbnbPrepaidPaymentJob for reservation {} (already completed)",
                        reservationId );
                continue;
            }
            enqueuePrepaidJob( event, reservationId );
        }
    }

    private void enqueuePrepaidJob( CloudbedsCalendarEvent event, String reservationId ) {
        LOGGER.info( "Creating an AirbnbPrepaidPaymentJob for booking {} ({}): {} {}",
                event.getThirdPartyIdentifier(), event.getStatus(),
                event.getFirstName(), event.getLastName() );
        CalculateEdinburghVisitorLevyForBookingJob evlJob =
                edinburghVisitorLevyJobSupport.findOrCreatePendingJob( reservationId );
        AirbnbPrepaidPaymentJob payJob = new AirbnbPrepaidPaymentJob();
        payJob.setStatus( JobStatus.submitted );
        payJob.setReservationId( reservationId );
        if ( evlJob != null ) {
            payJob.getDependentJobs().add( evlJob );
        }
        dao.insertJob( payJob );
    }

    private Set<String> getEligibleSubSourceIds() {
        try ( WebClient webClient = context.getBean( "webClientForCloudbeds", WebClient.class ) ) {
            return cloudbedsScraper.lookupAirbnbPrepaidSubSourceIds( webClient );
        }
        catch ( IOException e ) {
            LOGGER.warn( "Unable to resolve Airbnb prepaid booking-source ids: {}", e.getMessage() );
            return Collections.emptySet();
        }
    }
}
