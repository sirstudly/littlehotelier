package com.macbackpackers.scrapers.cloudbedsws;

import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.htmlunit.WebClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import com.macbackpackers.beans.JobStatus;
import com.macbackpackers.dao.WordPressDAO;
import com.macbackpackers.jobs.VoidAirbnbServiceFeeOnCancelJob;
import com.macbackpackers.scrapers.CloudbedsScraper;

/**
 * Enqueues {@link VoidAirbnbServiceFeeOnCancelJob} when Airbnb channel-collect bookings move to
 * canceled/no-show (or calendar cancel-delete), so orphaned service-fee revenue adjusts can be
 * voided once folio rates are cleared.
 */
@Component
public class VoidAirbnbServiceFeeOnCancelEventListener implements CloudbedsEventListener {

    private static final Logger LOGGER = LoggerFactory.getLogger( VoidAirbnbServiceFeeOnCancelEventListener.class );

    @Autowired
    private WordPressDAO dao;

    @Autowired
    private CloudbedsScraper cloudbedsScraper;

    @Autowired
    private CloudbedsCalendarEventRegistry eventRegistry;

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
        Set<String> eligibleSources = getEligibleSubSourceIds();
        if ( eligibleSources.isEmpty() ) {
            LOGGER.warn( "No Airbnb prepaid booking-source ids resolved; skipping service-fee cancel voids" );
            return;
        }
        processReservationEvents( update.getAllReservationEvents(), eligibleSources );
        processCancelDeleteCandidates( propertyId, update );
    }

    private void processReservationEvents( List<CloudbedsCalendarEvent> events, Set<String> eligibleSources ) {
        if ( events == null || events.isEmpty() ) {
            return;
        }
        Set<String> reservationIdsSeenInBatch = new HashSet<>();
        for ( CloudbedsCalendarEvent event : events ) {
            if ( false == matchesCanceledOrNoShowAirbnb( event, eligibleSources ) ) {
                continue;
            }
            String reservationId = event.getBookingId();
            if ( false == reservationIdsSeenInBatch.add( reservationId ) ) {
                continue;
            }
            tryEnqueue( event, reservationId );
        }
    }

    private void processCancelDeleteCandidates( String propertyId, CloudbedsCalendarUpdate update ) {
        if ( false == EdinburghVisitorLevyBookingCriteria.isLikelyCancellationDelete( update ) ) {
            return;
        }
        Set<String> reservationIdsSeenInBatch = new HashSet<>();
        for ( String eventId : update.getAllRemovedCalendarEventIds() ) {
            CloudbedsCalendarEventRegistry.ResolvedBookingId resolved =
                    eventRegistry.resolveRemovedEventBookingId( propertyId, eventId );
            if ( false == resolved.isKnown() ) {
                continue;
            }
            String reservationId = resolved.getBookingId();
            if ( false == reservationIdsSeenInBatch.add( reservationId ) ) {
                continue;
            }
            LOGGER.info( "Cancel-delete candidate for Airbnb service-fee void on reservation {} (event_id={})",
                    reservationId, eventId );
            // Job itself verifies Airbnb channel-collect + rates cleared.
            tryEnqueue( null, reservationId );
        }
    }

    private void tryEnqueue( CloudbedsCalendarEvent event, String reservationId ) {
        if ( dao.hasPendingVoidAirbnbServiceFeeOnCancelJobForReservation( reservationId ) ) {
            LOGGER.info( "Skipping VoidAirbnbServiceFeeOnCancelJob for reservation {} (already pending)",
                    reservationId );
            return;
        }
        if ( event != null ) {
            LOGGER.info( "Creating VoidAirbnbServiceFeeOnCancelJob for booking {} ({}): {} {}",
                    reservationId, event.getStatus(), event.getFirstName(), event.getLastName() );
        }
        else {
            LOGGER.info( "Creating VoidAirbnbServiceFeeOnCancelJob for booking {} (cancel-delete candidate)",
                    reservationId );
        }
        VoidAirbnbServiceFeeOnCancelJob job = new VoidAirbnbServiceFeeOnCancelJob();
        job.setStatus( JobStatus.submitted );
        job.setReservationId( reservationId );
        dao.insertJob( job );
    }

    static boolean matchesCanceledOrNoShowAirbnb( CloudbedsCalendarEvent event,
            Set<String> eligibleSubSourceIds ) {
        if ( event == null || false == event.isReservationBooking() ) {
            return false;
        }
        if ( false == event.isCanceledOrNoShow() ) {
            return false;
        }
        if ( event.isHotelCollectBooking() ) {
            return false;
        }
        String bookingSource = event.getBookingSource();
        return StringUtils.isNotBlank( bookingSource )
                && eligibleSubSourceIds.contains( bookingSource );
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
