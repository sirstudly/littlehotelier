
package com.macbackpackers.jobs;

import java.io.IOException;
import java.math.BigDecimal;
import java.text.ParseException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;
import java.util.List;

import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;
import javax.persistence.Transient;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;

import com.gargoylesoftware.htmlunit.WebClient;
import com.macbackpackers.beans.JobStatus;
import com.macbackpackers.beans.UnpaidDepositReportEntry;
import com.macbackpackers.scrapers.BookingsPageScraper;
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
        else {
            try (WebClient webClient = appContext.getBean( "webClient", WebClient.class )) {
                processJobForLittleHotelier( webClient );
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
                LocalDate.now(), LocalDate.now().plusDays( 7 ),
                null, null, "Booking.com" ) // version 7.9 removed (Hotel Collect Booking) from label
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

    private void processJobForLittleHotelier( WebClient webClient ) throws Exception {
        Calendar c = Calendar.getInstance();
        Date toDate = c.getTime();
        c.add( Calendar.DATE, getDaysBack() * -1 );
        Date fromDate = c.getTime();
        BookingsPageScraper scraper = appContext.getBean( BookingsPageScraper.class );
        createDepositChargeJobs( scraper.getUnpaidReservations( webClient, "BDC", fromDate, toDate ) );
        createDepositChargeJobs( scraper.getUnpaidReservations( webClient, "EXP", fromDate, toDate ) );
    }

    /**
     * Creates DepositChargeJobs for all unpaid deposits within the given dates.
     * 
     * @param webClient LH web client
     * @param bookingType type of booking ref to match (e.g. BDC, EXP)
     * @param fromDate start range of booked date
     * @param toDate end range of booked date
     * @throws IOException
     * @throws ParseException
     */
    private void createDepositChargeJobs( List<UnpaidDepositReportEntry> records ) throws IOException, ParseException {
        LOGGER.info( records.size() + " records found" );
        for ( UnpaidDepositReportEntry entry : records ) {
            LOGGER.info( "Creating a DepositChargeJob for booking " + entry.getBookingRef() + " on " + entry.getBookedDate() );
            DepositChargeJob depositJob = new DepositChargeJob();
            depositJob.setStatus( JobStatus.submitted );
            depositJob.setBookingRef( entry.getBookingRef() );
            depositJob.setBookingDate( entry.getBookedDate() );
            dao.insertJob( depositJob );
        }
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
