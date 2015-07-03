
package com.macbackpackers.jobs;

import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;

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

    private int getAllocationScraperJobId() {
        return Integer.parseInt( getParameter( "allocation_scraper_job_id" ) );
    }

}
