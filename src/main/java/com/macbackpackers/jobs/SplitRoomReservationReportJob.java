
package com.macbackpackers.jobs;

import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;

/**
 * Creates the split room reservation report using data from a previous AllocationScraperJob.
 *
 */
@Entity
@DiscriminatorValue( value = "com.macbackpackers.jobs.SplitRoomReservationReportJob" )
public class SplitRoomReservationReportJob extends AbstractJob {

    @Override
    public void processJob() throws Exception {
        dao.runSplitRoomsReservationsReport( getAllocationScraperJobId() );
    }

    public int getAllocationScraperJobId() {
        return Integer.parseInt( getParameter( "allocation_scraper_job_id" ) );
    }

    public void setAllocationScraperJobId( int jobId ) {
        setParameter( "allocation_scraper_job_id", String.valueOf( jobId ) );
    }

}
