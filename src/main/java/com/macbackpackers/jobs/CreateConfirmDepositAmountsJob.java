
package com.macbackpackers.jobs;

import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;

import com.macbackpackers.beans.Job;
import com.macbackpackers.beans.JobStatus;

/**
 * A job that creates other jobs of type {@link ConfirmDepositAmountsJob}.
 *
 */
@Entity
@DiscriminatorValue( value = "com.macbackpackers.jobs.CreateConfirmDepositAmountsJob" )
public class CreateConfirmDepositAmountsJob extends AbstractJob {

    @Override
    public void processJob() throws Exception {

        AllocationScraperJob job = dao.getLastCompletedJobOfType( AllocationScraperJob.class );
        if ( job != null ) {
            for ( int reservationId : dao.getHostelworldHostelBookersUnpaidDepositReservations( job.getId() ) ) {
                LOGGER.info( "Creating a ConfirmDepositAmountsJob for reservation_id " + reservationId );
                ConfirmDepositAmountsJob tickDepositJob = new ConfirmDepositAmountsJob();
                tickDepositJob.setStatus( JobStatus.submitted );
                tickDepositJob.setReservationId( reservationId );
                dao.insertJob( tickDepositJob );
            }
        }
    }

}
