
package com.macbackpackers.jobs;

import java.util.Calendar;
import java.util.Date;
import java.util.List;

import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;
import javax.persistence.Transient;

import org.springframework.beans.factory.annotation.Autowired;

import com.macbackpackers.beans.JobStatus;
import com.macbackpackers.beans.UnpaidDepositReportEntry;
import com.macbackpackers.scrapers.BookingsPageScraper;

/**
 * Job that creates {@link BDCDepositChargeJob}s for the past X days.
 */
@Entity
@DiscriminatorValue( value = "com.macbackpackers.jobs.CreateBDCDepositChargeJob" )
public class CreateBDCDepositChargeJob extends AbstractJob {

    @Autowired
    @Transient
    private BookingsPageScraper scraper;

    @Override
    public void processJob() throws Exception {
        Calendar c = Calendar.getInstance();
        Date toDate = c.getTime();
        c.add( Calendar.DATE, getDaysBack() * -1 );
        Date fromDate = c.getTime();
        List<UnpaidDepositReportEntry> records = scraper.getUnpaidBDCReservations( fromDate, toDate );
        LOGGER.info( records.size() + " records found" );
        for ( UnpaidDepositReportEntry entry : records ) {
            LOGGER.info( "Creating a BDCDepositChargeJob for booking " + entry.getBookingRef() + " on " + entry.getBookedDate() );
            BDCDepositChargeJob bdcDepositJob = new BDCDepositChargeJob();
            bdcDepositJob.setStatus( JobStatus.submitted );
            bdcDepositJob.setBookingRef( entry.getBookingRef() );
            bdcDepositJob.setBookingDate( entry.getBookedDate() );
            dao.insertJob( bdcDepositJob );
        }
    }

    /**
     * Returns the number of days back to check for BDC records.
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
