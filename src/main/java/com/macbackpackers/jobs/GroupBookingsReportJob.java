
package com.macbackpackers.jobs;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;

/**
 * Creates a report with all bookings with more than 5 people using data from a previous
 * AllocationScraperJob.
 *
 */
@Entity
@DiscriminatorValue( value = "com.macbackpackers.jobs.GroupBookingsReportJob" )
public class GroupBookingsReportJob extends AbstractJob {

    @Override
    public void processJob() throws Exception {
        dao.runGroupBookingsReport( getAllocationScraperJobId() );
    }

    public int getAllocationScraperJobId() {
        return Integer.parseInt( getParameter( "allocation_scraper_job_id" ) );
    }

    public void setAllocationScraperJobId( int jobId ) {
        setParameter( "allocation_scraper_job_id", String.valueOf( jobId ) );
    }

}
