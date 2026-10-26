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
import com.macbackpackers.jobs.CalculateEdinburghVisitorLevyForBookingJob;
import com.macbackpackers.jobs.HostelworldPayNowPaymentJob;
import com.macbackpackers.scrapers.CloudbedsScraper;
import com.macbackpackers.services.EdinburghVisitorLevyJobSupport;

/**
 * Enqueues {@link HostelworldPayNowPaymentJob}s for new Hostelworld channel-collect non-refundable
 * (Pay Now) bookings seen on the calendar WebSocket. Ignores the initial snapshot. Depends on
 * {@link CalculateEdinburghVisitorLevyForBookingJob} when EVL is enabled.
 */
@Component
public class HostelworldPayNowPaymentEventListener implements CloudbedsEventListener {

    private static final Logger LOGGER = LoggerFactory.getLogger( HostelworldPayNowPaymentEventListener.class );

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
            enqueuePayNowJob( event, reservationId );
        }
    }

    private void enqueuePayNowJob( CloudbedsCalendarEvent event, String reservationId ) {
        LOGGER.info( "Creating a HostelworldPayNowPaymentJob for booking {} ({}): {} {}",
                event.getThirdPartyIdentifier(), event.getStatus(),
                event.getFirstName(), event.getLastName() );
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

    private Set<String> getEligibleSubSourceIds() {
        try ( WebClient webClient = context.getBean( "webClientForCloudbeds", WebClient.class ) ) {
            return cloudbedsScraper.lookupHostelworldPayNowSubSourceIds( webClient );
        }
        catch ( IOException e ) {
            LOGGER.warn( "Unable to resolve Hostelworld Pay Now booking-source ids: {}", e.getMessage() );
            return Collections.emptySet();
        }
    }
}
