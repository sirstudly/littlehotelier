package com.macbackpackers.jobs;

import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import com.macbackpackers.beans.Job;
import com.macbackpackers.beans.JobStatus;

/**
 * A job that creates other jobs of type {@link ConfirmDepositAmountsJob}.
 *
 */
@Component
@Scope( "prototype" )
public class CreateConfirmDepositAmountsJob extends AbstractJob {
    
    @Override
    public void processJob() throws Exception {
        
        AllocationScraperJob job = dao.getLastCompletedJobOfType( AllocationScraperJob.class );
        if( job != null ) {
            for( int reservationId : dao.getHostelworldHostelBookersUnpaidDepositReservations( job.getId() ) ) {
                LOGGER.info( "Creating a ConfirmDepositAmountsJob for reservation_id " + reservationId );
                Job tickDepositJob = new Job();
                tickDepositJob.setClassName( ConfirmDepositAmountsJob.class.getName() );
                tickDepositJob.setStatus( JobStatus.submitted );
                tickDepositJob.setParameter( "reservation_id", String.valueOf( reservationId ) );
                dao.insertJob( tickDepositJob );
            }
        }
    }

}