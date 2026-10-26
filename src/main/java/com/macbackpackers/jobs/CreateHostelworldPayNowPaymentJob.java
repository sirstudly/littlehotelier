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
import com.macbackpackers.scrapers.cloudbedsws.HostelworldPayNowPaymentCriteria;
import com.macbackpackers.services.EdinburghVisitorLevyJobSupport;
import com.macbackpackers.services.PaymentProcessorService;

/**
 * Scans recent Hostelworld channel-collect NRR (Pay Now) bookings and enqueues
 * {@link HostelworldPayNowPaymentJob}s for those that still need a third-party payment or whose
 * folio no longer looks healthy after payment.
 */
@Entity
@DiscriminatorValue( value = "com.macbackpackers.jobs.CreateHostelworldPayNowPaymentJob" )
public class CreateHostelworldPayNowPaymentJob extends AbstractJob {

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
                HostelworldPayNowPaymentCriteria.ELIGIBLE_SOURCE_NAMES )
                .stream()
                .filter( p -> false == "canceled".equalsIgnoreCase( p.getStatus() ) )
                .filter( p -> p.isChannelCollectBooking() )
                .filter( p -> p.isNonRefundable() )
                .filter( p -> false == dao.hasPendingHostelworldPayNowPaymentJobForReservation( p.getReservationId() ) )
                .filter( p -> false == PaymentProcessorService.isHealthyChannelCollectPrepaidFolio( p ) )
                .forEach( p -> {
                    LOGGER.info( "Creating a HostelworldPayNowPaymentJob for booking {} ({}): {} {}",
                            p.getThirdPartyIdentifier(), p.getStatus(),
                            p.getFirstName(), p.getLastName() );
                    CalculateEdinburghVisitorLevyForBookingJob evlJob =
                            edinburghVisitorLevyJobSupport.findOrCreatePendingJob( p.getReservationId() );
                    HostelworldPayNowPaymentJob payJob = new HostelworldPayNowPaymentJob();
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
