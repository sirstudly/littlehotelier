package com.macbackpackers.jobs;

import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

/**
 * Creates a report with all bookings with more than 5 people using 
 * data from a previous AllocationScraperJob.
 *
 */
@Component
@Scope( "prototype" )
public class GroupBookingsReportJob extends AbstractJob {
    
    @Override
    public void processJob() throws Exception {
        dao.runGroupBookingsReport( getAllocationScraperJobId() );
    }
    
    private int getAllocationScraperJobId() {
        return Integer.parseInt( getParameter( "allocation_scraper_job_id" ) );
    }
    
}