
package com.macbackpackers.jobs;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;
import javax.persistence.Transient;

import org.htmlunit.WebClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;

import com.macbackpackers.beans.JobStatus;
import com.macbackpackers.scrapers.CloudbedsScraper;

/**
 * Job that creates {@link DepositChargeJob}s for the past X days.
 */
@Entity
@DiscriminatorValue( value = "com.macbackpackers.jobs.CreateDepositChargeJob" )
public class CreateDepositChargeJob extends AbstractJob {

    @Autowired
    @Transient
    private ApplicationContext appContext;

    @Override
    public void processJob() throws Exception {
        if ( dao.isCloudbeds() ) {
            try (WebClient webClient = appContext.getBean( "webClientForCloudbeds", WebClient.class )) {
                HashSet<DepositChargeJob> jobs = new HashSet<>(); // make unique
                jobs.addAll( collectNewlyBookedDepositJobsForCloudbeds( webClient ) );
                jobs.addAll( collectUpcomingBDCDepositJobsForCloudbeds( webClient ) );
                jobs.stream().forEach( j -> dao.insertJob( j ) );
            }
        }
    }

    /**
     * Create DepositChargeJobs for any new bookings without a deposit from BDC/Expedia.
     * 
     * @param webClient
     * @return non-null list of jobs
     * @throws Exception
     */
    private List<DepositChargeJob> collectNewlyBookedDepositJobsForCloudbeds( WebClient webClient ) throws Exception {
        List<DepositChargeJob> jobs = new ArrayList<>();
        CloudbedsScraper cbScraper = appContext.getBean( CloudbedsScraper.class );
        cbScraper.getReservationsForBookingSources( webClient,
                null, null,
                LocalDate.now().minusDays( getDaysBack() ), LocalDate.now(),
                "Booking.com (Hotel Collect Booking)", "Expedia (Hotel Collect Booking)" )
                .stream()
                .filter( p -> p.getPaidValue().equals( BigDecimal.ZERO ) )
                .filter( p -> p.isHotelCollectBooking() )
                .filter( p -> p.isCardDetailsPresent() )
                .filter( p -> false == p.isPrepaid() )
                .filter( p -> false == p.isNonRefundable() ) // non-refundables are processed by CreateChargeNonRefundableBookingJob
                .filter( p -> false == "canceled".equalsIgnoreCase( p.getStatus() ) )
                .forEach( p -> {
                    LOGGER.info( "Creating a DepositChargeJob for " + p.getSourceName() + " #"
                            + p.getThirdPartyIdentifier() + " (" + p.getStatus() + ")" );
                    LOGGER.info( p.getFirstName() + " " + p.getLastName() );
                    DepositChargeJob chargeJob = new DepositChargeJob();
                    chargeJob.setStatus( JobStatus.submitted );
                    chargeJob.setReservationId( Integer.parseInt( p.getReservationId() ) );
                    jobs.add( chargeJob );
                } );
        return jobs;
    }

    /**
     * Card details hidden until a day or two before checkin date. Charge a normal deposit
     * in these instances.
     * 
     * @param webClient
     * @return non-null list of jobs
     * @throws Exception
     */
    private List<DepositChargeJob> collectUpcomingBDCDepositJobsForCloudbeds( WebClient webClient ) throws Exception {
        List<DepositChargeJob> jobs = new ArrayList<>();
        CloudbedsScraper cbScraper = appContext.getBean( CloudbedsScraper.class );
        cbScraper.getReservationsForBookingSources( webClient,
                LocalDate.now(), LocalDate.now().plusDays( 3 ),
                null, null, "Booking.com (Hotel Collect Booking)" )
                .stream()
                .filter( p -> p.getPaidValue().equals( BigDecimal.ZERO ) )
                .filter( p -> p.isHotelCollectBooking() )
                .filter( p -> p.isCardDetailsPresent() )
                .filter( p -> false == p.isPrepaid() )
                .filter( p -> false == p.isNonRefundable() ) // non-refundables are processed by CreateChargeNonRefundableBookingJob
                .filter( p -> false == "canceled".equalsIgnoreCase( p.getStatus() ) )
                .filter( p -> false == p.containsNote( "will post automatically when customer confirms via email" ) ) // we haven't already tried to charge
                .forEach( p -> {
                    LOGGER.info( "Creating a DepositChargeJob for " + p.getSourceName() + " #"
                            + p.getThirdPartyIdentifier() + " (" + p.getStatus() + ")" );
                    LOGGER.info( p.getFirstName() + " " + p.getLastName() );
                    DepositChargeJob chargeJob = new DepositChargeJob();
                    chargeJob.setStatus( JobStatus.submitted );
                    chargeJob.setReservationId( Integer.parseInt( p.getReservationId() ) );
                    jobs.add( chargeJob );
                } );
        return jobs;
    }

    /**
     * Returns the number of days back to check for records.
     * 
     * @return number of days back to check
     */
    public int getDaysBack() {
        return Integer.parseInt( getParameter( "days_back" ) );
    }

    /**
     * Sets the number of days back to check for BDC records.
     * 
     * @param daysBack number of days back to check
     */
    public void setDaysBack( int daysBack ) {
        setParameter( "days_back", String.valueOf( daysBack ) );
    }

}
