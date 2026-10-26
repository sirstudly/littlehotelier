package com.macbackpackers.jobs;

import java.time.LocalDate;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.Transient;

import org.htmlunit.WebClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.transaction.annotation.Transactional;

import com.macbackpackers.beans.JobStatus;
import com.macbackpackers.scrapers.CloudbedsScraper;
import com.macbackpackers.scrapers.cloudbedsws.AirbnbPrepaidPaymentCriteria;
import com.macbackpackers.services.EdinburghVisitorLevyJobSupport;
import com.macbackpackers.services.PaymentProcessorService;

/**
 * Scans recent Airbnb (API) channel-collect bookings and enqueues {@link AirbnbPrepaidPaymentJob}s
 * for those that still need a prepaid folio payment or whose folio no longer looks healthy.
 */
@Entity
@DiscriminatorValue( value = "com.macbackpackers.jobs.CreateAirbnbPrepaidPaymentJob" )
public class CreateAirbnbPrepaidPaymentJob extends AbstractJob {

    @Autowired
    @Transient
    @Qualifier( "webClientForCloudbeds" )
    private WebClient cbWebClient;

    @Autowired
    @Transient
    private CloudbedsScraper cbScraper;

    @Autowired
    @Transient
    private EdinburghVisitorLevyJobSupport edinburghVisitorLevyJobSupport;

    @Override
    @Transactional
    public void processJob() throws Exception {
        cbScraper.getReservationsForBookingSources( cbWebClient, null, null,
                LocalDate.now().minusDays( getDaysBack() ), LocalDate.now(),
                AirbnbPrepaidPaymentCriteria.ELIGIBLE_SOURCE_NAMES )
                .stream()
                .filter( p -> false == "canceled".equalsIgnoreCase( p.getStatus() ) )
                .filter( p -> p.isChannelCollectBooking() )
                .filter( p -> false == dao.hasPendingAirbnbPrepaidPaymentJobForReservation( p.getReservationId() ) )
                .filter( p -> false == PaymentProcessorService.isHealthyChannelCollectPrepaidFolio( p ) )
                .forEach( p -> {
                    LOGGER.info( "Creating an AirbnbPrepaidPaymentJob for booking {} ({}): {} {}",
                            p.getThirdPartyIdentifier(), p.getStatus(),
                            p.getFirstName(), p.getLastName() );
                    CalculateEdinburghVisitorLevyForBookingJob evlJob =
                            edinburghVisitorLevyJobSupport.findOrCreatePendingJob( p.getReservationId() );
                    AirbnbPrepaidPaymentJob payJob = new AirbnbPrepaidPaymentJob();
                    payJob.setStatus( JobStatus.submitted );
                    payJob.setReservationId( p.getReservationId() );
                    if ( evlJob != null ) {
                        payJob.getDependentJobs().add( evlJob );
                    }
                    dao.insertJob( payJob );
                } );
    }

    @Override
    public void finalizeJob() {
        cbWebClient.close();
    }

    public int getDaysBack() {
        String daysBack = getParameter( "days_back" );
        return daysBack == null ? 3 : Integer.parseInt( daysBack );
    }

    public void setDaysBack( int daysBack ) {
        if ( daysBack < 0 ) {
            throw new IllegalArgumentException( "Days back must be non-negative" );
        }
        setParameter( "days_back", String.valueOf( daysBack ) );
    }
}
