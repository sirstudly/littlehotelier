package com.macbackpackers.jobs;

import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

/**
 * Creates a report with all bookings that have not yet paid a deposit using 
 * data from a previous AllocationScraperJob.
 *
 */
@Component
@Scope( "prototype" )
public class UnpaidDepositReportJob extends AbstractJob {
    
    @Override
    public void processJob() throws Exception {
        dao.runUnpaidDepositReport( getAllocationScraperJobId() );
    }
    
    private int getAllocationScraperJobId() {
        return Integer.parseInt( getParameter( "allocation_scraper_job_id" ) );
    }
    
}