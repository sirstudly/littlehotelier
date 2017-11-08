
package com.macbackpackers.jobs;

import static com.macbackpackers.scrapers.AllocationsPageScraper.DATE_FORMAT_YYYY_MM_DD;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;

import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;
import javax.persistence.Transient;

import org.apache.commons.csv.CSVRecord;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

import com.gargoylesoftware.htmlunit.WebClient;
import com.macbackpackers.beans.JobStatus;
import com.macbackpackers.scrapers.BookingsPageScraper;

/**
 * Job that creates {@link AgodaChargeJob}s for the past X days.
 */
@Entity
@DiscriminatorValue( value = "com.macbackpackers.jobs.CreateAgodaChargeJob" )
public class CreateAgodaChargeJob extends AbstractJob {

    @Autowired
    @Transient
    private BookingsPageScraper bookingScraper;

    @Autowired
    @Transient
    @Qualifier( "webClient" )
    private WebClient webClient;

    @Override
    public void processJob() throws Exception {
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

                LOGGER.info( "Creating a AgodaChargeJob for booking " + bookingRef );
                AgodaChargeJob agodaChargeJob = new AgodaChargeJob();
                agodaChargeJob.setStatus( JobStatus.submitted );
                agodaChargeJob.setBookingRef( bookingRef );
                agodaChargeJob.setCheckinDate( checkinDate );
                dao.insertJob( agodaChargeJob );
            }
        }
    }

    @Override
    public void finalizeJob() {
        webClient.close(); // cleans up JS threads
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
