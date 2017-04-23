
package com.macbackpackers.jobs;

import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;

import org.springframework.transaction.annotation.Transactional;

import com.macbackpackers.beans.JobStatus;

/**
 * A job that creates other jobs of type {@link ConfirmDepositAmountsJob}.
 *
 */
@Entity
@DiscriminatorValue( value = "com.macbackpackers.jobs.CreateConfirmDepositAmountsJob" )
public class CreateConfirmDepositAmountsJob extends AbstractJob {

    @Override
    @Transactional // no re-run on this job; all must go thru or nothing
    public void processJob() throws Exception {
        for ( int reservationId : dao.getHostelworldHostelBookersUnpaidDepositReservations( getAllocationScraperJobId() ) ) {
            LOGGER.info( "Creating a ConfirmDepositAmountsJob for reservation_id " + reservationId );
            ConfirmDepositAmountsJob tickDepositJob = new ConfirmDepositAmountsJob();
            tickDepositJob.setStatus( JobStatus.submitted );
            tickDepositJob.setReservationId( reservationId );
            dao.insertJob( tickDepositJob );
        }
    }

    public int getAllocationScraperJobId() {
        return Integer.parseInt( getParameter( "allocation_scraper_job_id" ) );
    }

    public void setAllocationScraperJobId( int jobId ) {
        setParameter( "allocation_scraper_job_id", String.valueOf( jobId ) );
    }

}
