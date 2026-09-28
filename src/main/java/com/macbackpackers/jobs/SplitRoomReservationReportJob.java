
package com.macbackpackers.jobs;

import java.util.List;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;

import org.springframework.transaction.annotation.Transactional;

import com.macbackpackers.beans.JobStatus;

/**
 * Creates the split room reservation report using data from a previous AllocationScraperJob,
 * then queues a {@link BedShuffleHintJob} for each reservation on it checking in today or later.
 */
@Entity
@DiscriminatorValue( value = "com.macbackpackers.jobs.SplitRoomReservationReportJob" )
public class SplitRoomReservationReportJob extends AbstractJob {

    @Override
    @Transactional
    public void processJob() throws Exception {
        dao.runSplitRoomsReservationsReport( getAllocationScraperJobId() );

        List<Long> reservationIds = dao.fetchSplitRoomReservationIdsForShuffleHints( getAllocationScraperJobId() );
        for ( long reservationId : reservationIds ) {
            BedShuffleHintJob hintJob = new BedShuffleHintJob();
            hintJob.setStatus( JobStatus.submitted );
            hintJob.setAllocationScraperJobId( getAllocationScraperJobId() );
            hintJob.setReservationId( reservationId );
            dao.insertJob( hintJob );
        }
        LOGGER.info( "Queued {} bed shuffle hint jobs", reservationIds.size() );
    }

    public int getAllocationScraperJobId() {
        return Integer.parseInt( getParameter( "allocation_scraper_job_id" ) );
    }

    public void setAllocationScraperJobId( int jobId ) {
        setParameter( "allocation_scraper_job_id", String.valueOf( jobId ) );
    }

}
