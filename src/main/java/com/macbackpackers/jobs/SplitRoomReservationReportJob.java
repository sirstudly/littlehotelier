package com.macbackpackers.jobs;

import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

/**
 * Creates the split room reservation report using data from a previous AllocationScraperJob.
 *
 */
@Component
@Scope( "prototype" )
public class SplitRoomReservationReportJob extends AbstractJob {
    
    @Override
    public void processJob() throws Exception {
        dao.runSplitRoomsReservationsReport( getAllocationScraperJobId() );
    }
    
    private int getAllocationScraperJobId() {
        return Integer.parseInt( getParameter( "allocation_scraper_job_id" ) );
    }
    
}