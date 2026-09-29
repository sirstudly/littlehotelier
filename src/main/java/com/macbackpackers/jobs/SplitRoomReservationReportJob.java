
package com.macbackpackers.jobs;

import java.util.List;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;

import org.springframework.transaction.annotation.Transactional;

import com.macbackpackers.beans.ConsecutiveBookingPair;
import com.macbackpackers.beans.JobStatus;

/**
 * Creates the split room reservation and consecutive bookings reports from the current booking assignments (stamped
 * with the id of a previous AllocationScraperJob), then queues a {@link BedShuffleHintJob} for each row on them
 * checking in today or later. Re-running it for the same allocation scraper job refreshes both reports and their hints.
 */
@Entity
@DiscriminatorValue( value = "com.macbackpackers.jobs.SplitRoomReservationReportJob" )
public class SplitRoomReservationReportJob extends AbstractJob {

    @Override
    @Transactional
    public void processJob() throws Exception {
        int reportJobId = getAllocationScraperJobId();
        dao.runSplitRoomsReservationsReport( reportJobId );

        List<Long> reservationIds = dao.fetchSplitRoomReservationIdsForShuffleHints( reportJobId );
        for ( long reservationId : reservationIds ) {
            dao.insertJob( newHintJob( reportJobId, reservationId ) );
        }
        List<ConsecutiveBookingPair> pairs = dao.fetchConsecutiveBookingPairsForShuffleHints( reportJobId );
        for ( ConsecutiveBookingPair pair : pairs ) {
            BedShuffleHintJob hintJob = newHintJob( reportJobId, pair.reservationId() );
            hintJob.setNextReservationId( pair.nextReservationId() );
            hintJob.setRoomTypeId( pair.roomTypeId() );
            dao.insertJob( hintJob );
        }
        LOGGER.info( "Queued {} split room and {} consecutive booking shuffle hint jobs", reservationIds.size(), pairs.size() );
    }

    private static BedShuffleHintJob newHintJob( int reportJobId, long reservationId ) {
        BedShuffleHintJob hintJob = new BedShuffleHintJob();
        hintJob.setStatus( JobStatus.submitted );
        hintJob.setAllocationScraperJobId( reportJobId );
        hintJob.setReservationId( reservationId );
        return hintJob;
    }

    public int getAllocationScraperJobId() {
        return Integer.parseInt( getParameter( "allocation_scraper_job_id" ) );
    }

    public void setAllocationScraperJobId( int jobId ) {
        setParameter( "allocation_scraper_job_id", String.valueOf( jobId ) );
    }

}
