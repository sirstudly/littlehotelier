package com.macbackpackers.jobs;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;

/**
 * Creates a report with bookings where guest count is one less than dorm capacity
 * (e.g. 3 in a 4-bed, 5 in a 6-bed) using data from a previous AllocationScraperJob.
 *
 */
@Entity
@DiscriminatorValue( value = "com.macbackpackers.jobs.MostlyFullDormReportJob" )
public class MostlyFullDormReportJob extends AbstractJob {

    @Override
    public void processJob() throws Exception {
        dao.runMostlyFullDormReport( getAllocationScraperJobId() );
    }

    public int getAllocationScraperJobId() {
        return Integer.parseInt( getParameter( "allocation_scraper_job_id" ) );
    }

    public void setAllocationScraperJobId( int jobId ) {
        setParameter( "allocation_scraper_job_id", String.valueOf( jobId ) );
    }

}
