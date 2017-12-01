
package com.macbackpackers.jobs;

import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;

import org.springframework.transaction.annotation.Transactional;

import com.macbackpackers.beans.BookingByCheckinDate;
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
        for ( BookingByCheckinDate booking : dao.getHostelworldHostelBookersUnpaidDepositReservations( getAllocationScraperJobId() ) ) {
            LOGGER.info( "Creating a ConfirmDepositAmountsJob for reservation " + booking.getBookingRef() );
            ConfirmDepositAmountsJob tickDepositJob = new ConfirmDepositAmountsJob();
            tickDepositJob.setStatus( JobStatus.submitted );
            tickDepositJob.setBookingRef( booking.getBookingRef() );
            tickDepositJob.setCheckinDate( booking.getCheckinDate() );
            tickDepositJob.setReservationId( booking.getReservationId() );
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
