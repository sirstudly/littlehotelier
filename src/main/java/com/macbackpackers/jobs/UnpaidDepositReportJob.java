
package com.macbackpackers.jobs;

import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;

/**
 * Creates a report with all bookings that have not yet paid a deposit using data from a previous
 * AllocationScraperJob.
 *
 */
@Entity
@DiscriminatorValue( value = "com.macbackpackers.jobs.UnpaidDepositReportJob" )
public class UnpaidDepositReportJob extends AbstractJob {

    @Override
    public void processJob() throws Exception {
        dao.runUnpaidDepositReport( getAllocationScraperJobId() );
    }

    public int getAllocationScraperJobId() {
        return Integer.parseInt( getParameter( "allocation_scraper_job_id" ) );
    }

    public void setAllocationScraperJobId( int jobId ) {
        setParameter( "allocation_scraper_job_id", String.valueOf( jobId ) );
    }

}
