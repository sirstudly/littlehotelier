
package com.macbackpackers.jobs;

import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;

import org.springframework.transaction.annotation.Transactional;

import com.macbackpackers.beans.Job;
import com.macbackpackers.beans.JobStatus;

/**
 * Job that creates all dependent report instances for all allocation
 * records from a previous {@link AllocationScraperJob}.
 *
 */
@Entity
@DiscriminatorValue( value = "com.macbackpackers.jobs.CreateAllocationScraperReportsJob" )
public class CreateAllocationScraperReportsJob extends AbstractJob {

    @Override
    @Transactional // no re-run on this job; all must go thru or nothing
    public void processJob() throws Exception {
        // first we consolidate results from all AllocationScraperWorkerJobs
        for ( Job dependentJob : getDependentJobs() ) {
            // remap the worker id to the parent AllocationScraperJob id (for consolidating results)
            if ( dependentJob instanceof CloudbedsAllocationScraperWorkerJob ) {
                dao.updateAllocationJobId( dependentJob.getId(), getAllocationScraperJobId() );
            }
        }

        // insert jobs to create any reports
        insertSplitRoomReportJob();
        insertUnpaidDepositReportJob();
        insertGroupBookingsReportJob();
        insertBlacklistEmailJob();
    }

    /**
     * Creates an additional job to run the split room report.
     */
    private void insertSplitRoomReportJob() {
        SplitRoomReservationReportJob splitRoomReportJob = new SplitRoomReservationReportJob();
        splitRoomReportJob.setStatus( JobStatus.submitted );
        splitRoomReportJob.setAllocationScraperJobId( getAllocationScraperJobId() );
        dao.insertJob( splitRoomReportJob );
    }

    /**
     * Creates an additional job to run the unpaid deposit report.
     */
    private void insertUnpaidDepositReportJob() {
        UnpaidDepositReportJob unpaidDepositRptJob = new UnpaidDepositReportJob();
        unpaidDepositRptJob.setStatus( JobStatus.submitted );
        unpaidDepositRptJob.setAllocationScraperJobId( getAllocationScraperJobId() );
        dao.insertJob( unpaidDepositRptJob );
    }

    /**
     * Creates an additional job to run the group bookings report.
     */
    private void insertGroupBookingsReportJob() {
        GroupBookingsReportJob groupBookingRptJob = new GroupBookingsReportJob();
        groupBookingRptJob.setStatus( JobStatus.submitted );
        groupBookingRptJob.setAllocationScraperJobId( getAllocationScraperJobId() );
        dao.insertJob( groupBookingRptJob );
    }

    /**
     * Creates an additional job identifying any bookings in the blacklist.
     */
    private void insertBlacklistEmailJob() {
        CheckNewBookingsOnBlacklistJob blacklistJob = new CheckNewBookingsOnBlacklistJob();
        blacklistJob.setStatus( JobStatus.submitted );
        blacklistJob.setAllocationScraperJobId( getAllocationScraperJobId() );
        dao.insertJob( blacklistJob );
    }

    public int getAllocationScraperJobId() {
        return Integer.parseInt( getParameter( "allocation_scraper_job_id" ) );
    }

    public void setAllocationScraperJobId( int jobId ) {
        setParameter( "allocation_scraper_job_id", String.valueOf( jobId ) );
    }

}
