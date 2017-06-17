
package com.macbackpackers.jobs;

import java.util.Calendar;

import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;

import com.macbackpackers.beans.JobStatus;

/**
 * Job that creates {@link ScrapeCancelledBookingsJob}s X days into the future.
 */
@Entity
@DiscriminatorValue( value = "com.macbackpackers.jobs.CreateScrapeCancelledBookingsJob" )
public class CreateScrapeCancelledBookingsJob extends AbstractJob {

    @Override
    public void processJob() throws Exception {

        Calendar c = Calendar.getInstance();
        int daysAhead = getDaysAhead();
        for ( int i = 0 ; i < daysAhead ; i++ ) {
            ScrapeCancelledBookingsJob j = new ScrapeCancelledBookingsJob();
            j.setStatus( JobStatus.submitted );
            j.setAllocationScraperJobId( getAllocationScraperJobId() );
            j.setCheckinDateStart( c.getTime() );
            j.setCheckinDateEnd( c.getTime() );
            dao.insertJob( j );
            c.add( Calendar.DATE, 1 );
        }
    }

    /**
     * Returns the parent AllocationScraperJob that created this one.
     * 
     * @return id of AllocationScraperJob
     */
    public int getAllocationScraperJobId() {
        return Integer.parseInt( getParameter( "allocation_scraper_job_id" ) );
    }

    /**
     * Sets the parent AllocationScraperJob that created this one.
     * 
     * @param jobId id of AllocationScraperJob
     */
    public void setAllocationScraperJobId( int jobId ) {
        setParameter( "allocation_scraper_job_id", String.valueOf( jobId ) );
    }

    /**
     * Returns the number of days ahead to check for records.
     * 
     * @return number of days ahead to check
     */
    public int getDaysAhead() {
        return Integer.parseInt( getParameter( "days_ahead" ) );
    }

    /**
     * Sets the number of days ahead to check for records.
     * 
     * @param daysBack number of days back to check
     */
    public void setDaysAhead( int daysAhead ) {
        setParameter( "days_ahead", String.valueOf( daysAhead ) );
    }

}
