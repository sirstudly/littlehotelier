
package com.macbackpackers.jobs;

import java.io.IOException;
import java.text.ParseException;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;
import javax.persistence.Transient;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

import com.gargoylesoftware.htmlunit.WebClient;
import com.macbackpackers.beans.JobStatus;
import com.macbackpackers.beans.UnpaidDepositReportEntry;
import com.macbackpackers.scrapers.BookingsPageScraper;

/**
 * Job that creates {@link DepositChargeJob}s for the past X days.
 */
@Entity
@DiscriminatorValue( value = "com.macbackpackers.jobs.CreateDepositChargeJob" )
public class CreateDepositChargeJob extends AbstractJob {

    @Autowired
    @Transient
    private BookingsPageScraper scraper;

    @Autowired
    @Transient
    @Qualifier( "webClient" )
    private WebClient webClient;

    @Override
    public void processJob() throws Exception {
        Calendar c = Calendar.getInstance();
        Date toDate = c.getTime();
        c.add( Calendar.DATE, getDaysBack() * -1 );
        Date fromDate = c.getTime();
        createDepositChargeJobs( "BDC", fromDate, toDate );
        createDepositChargeJobs( "EXP", fromDate, toDate );
    }

    /**
     * Creates DepositChargeJobs for all unpaid deposits within the given dates.
     * 
     * @param bookingType type of booking ref to match (e.g. BDC, EXP)
     * @param fromDate start range of booked date
     * @param toDate end range of booked date
     * @throws IOException
     * @throws ParseException
     */
    private void createDepositChargeJobs( String bookingType, Date fromDate, Date toDate ) throws IOException, ParseException {
        List<UnpaidDepositReportEntry> records = scraper.getUnpaidReservations( webClient, bookingType, fromDate, toDate );
        LOGGER.info( records.size() + " " + bookingType + " records found" );
        for ( UnpaidDepositReportEntry entry : records ) {
            LOGGER.info( "Creating a DepositChargeJob for booking " + entry.getBookingRef() + " on " + entry.getBookedDate() );
            DepositChargeJob depositJob = new DepositChargeJob();
            depositJob.setStatus( JobStatus.submitted );
            depositJob.setBookingRef( entry.getBookingRef() );
            depositJob.setBookingDate( entry.getBookedDate() );
            dao.insertJob( depositJob );
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
     * Sets the number of days back to check for BDC records.
     * 
     * @param daysBack number of days back to check
     */
    public void setDaysBack( int daysBack ) {
        setParameter( "days_back", String.valueOf( daysBack ) );
    }

}
