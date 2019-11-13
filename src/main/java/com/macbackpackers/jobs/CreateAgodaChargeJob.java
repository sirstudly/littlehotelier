
package com.macbackpackers.jobs;

import static com.macbackpackers.scrapers.AllocationsPageScraper.DATE_FORMAT_YYYY_MM_DD;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;

import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;
import javax.persistence.Transient;

import org.apache.commons.csv.CSVRecord;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;

import com.gargoylesoftware.htmlunit.WebClient;
import com.macbackpackers.beans.JobStatus;
import com.macbackpackers.scrapers.BookingsPageScraper;
import com.macbackpackers.scrapers.CloudbedsScraper;

/**
 * Job that creates Agoda charge jobs for the past X days.
 */
@Entity
@DiscriminatorValue( value = "com.macbackpackers.jobs.CreateAgodaChargeJob" )
public class CreateAgodaChargeJob extends AbstractJob {

    @Autowired
    @Transient
    private BookingsPageScraper bookingScraper;

    @Autowired
    @Transient
    private ApplicationContext appContext;

    @Autowired
    @Transient
    private CloudbedsScraper cbScraper;

    @Override
    public void processJob() throws Exception {

        if ( dao.isCloudbeds() ) {
            try (WebClient webClient = appContext.getBean( "webClientForCloudbedsNoValidate", WebClient.class )) {
                processJobForCloudbeds( webClient );
            }
        }
        else {
            try (WebClient webClient = appContext.getBean( "webClient", WebClient.class )) {
                processJobForLittleHotelier( webClient );
            }
        }
    }

    private void processJobForCloudbeds( WebClient webClient ) throws Exception {
        cbScraper.getReservationsForBookingSources( webClient, null, null,
                LocalDate.now().minusDays( getDaysBack() ), LocalDate.now(), "Agoda (Channel Collect Booking)" )
                .stream()
                .filter( p -> false == p.isPaid() )
                .filter( p -> "Agoda".equals( p.getSourceName() ) ) // sure to be sure
                .filter( p -> p.isChannelCollectBooking() )
                .filter( p -> false == "canceled".equalsIgnoreCase( p.getStatus() ) )
                .filter( p -> p.isCheckinDateTodayOrInPast() )
                .forEach( p -> {
                    LOGGER.info( "Creating a PrepaidChargeJob for booking "
                            + p.getThirdPartyIdentifier() + " (" + p.getStatus() + ")" );
                    LOGGER.info( p.getFirstName() + " " + p.getLastName() );
                    PrepaidChargeJob chargeJob = new PrepaidChargeJob();
                    chargeJob.setStatus( JobStatus.submitted );
                    chargeJob.setReservationId( Integer.parseInt( p.getReservationId() ) );
                    dao.insertJob( chargeJob );
                } );
    }

    private void processJobForLittleHotelier( WebClient webClient ) throws Exception {
        Calendar c = Calendar.getInstance();
        c.add( Calendar.DATE, -1 );
        Date toDate = c.getTime(); // yesterday
        c.add( Calendar.DATE, getDaysBack() * -1 );
        Date fromDate = c.getTime();

        // retrieve all checkouts for the day
        for ( CSVRecord record : bookingScraper.getAgodaReservations( webClient, fromDate, toDate ) ) {
            LOGGER.info( "Booking ref: " + record.get( "Booking reference" ) );

            String bookingRef = record.get( "Booking reference" );
            Date checkinDate = DATE_FORMAT_YYYY_MM_DD.parse( record.get( "Check in date" ) );
            String status = record.get( "Status" );
            BigDecimal paymentOutstanding = new BigDecimal( record.get( "Payment outstanding" ) );
            BigDecimal paymentReceived = new BigDecimal( record.get( "Payment Received" ) );

            // need to charge all bookings that:
            // a) have been checked-in and payment outstanding > 0
            // b) are confirmed and checkin-date is at least one day in the past and payment received = 0
            if ( (Arrays.asList( "checked-in", "checked-out" ).contains( status ) && paymentOutstanding.compareTo( BigDecimal.ZERO ) > 0)
                    || ("confirmed".equals( status ) && checkinDate.before( toDate ) && paymentReceived.equals( BigDecimal.ZERO )) ) {

                LOGGER.info( "Creating a ManualChargeJob for booking " + bookingRef );
                ManualChargeJob agodaChargeJob = new ManualChargeJob();
                agodaChargeJob.setStatus( JobStatus.submitted );
                agodaChargeJob.setBookingRef( bookingRef );
                agodaChargeJob.setAmount( paymentOutstanding );
                agodaChargeJob.setMessage( "Automated charge attempt. -ronbot" );
                agodaChargeJob.setCardDetailsOverrideEnabled( true );
                dao.insertJob( agodaChargeJob );
            }
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
     * Sets the number of days back to check for records.
     * 
     * @param daysBack number of days back to check
     */
    public void setDaysBack( int daysBack ) {
        setParameter( "days_back", String.valueOf( daysBack ) );
    }

}
