
package com.macbackpackers.jobs;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;

import org.springframework.transaction.annotation.Transactional;

import com.macbackpackers.beans.Job;
import com.macbackpackers.beans.JobStatus;

/**
 * Job that creates {@link BookingScraperJob} and all dependent report instances for all allocation
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
            if ( dependentJob instanceof AllocationScraperWorkerJob ) {
                dao.updateAllocationJobId( dependentJob.getId(), getAllocationScraperJobId() );
            }
        }

        // update allocation rows with missing data (from AllocationScraperWorkerJobs)
        List<BookingScraperJob> bookingScraperJobs = insertBookingsScraperJobs();

        // insert jobs to create any reports
        insertSplitRoomReportJob( bookingScraperJobs );
        insertUnpaidDepositReportJob( bookingScraperJobs );
        insertGroupBookingsReportJob( bookingScraperJobs );
        insertHostelworldHostelBookersConfirmDepositJob();
        insertCreateAgodaNoChargeNoteJob( bookingScraperJobs );
    }

    /**
     * We need additional jobs to update some fields by scraping the bookings pages and any reports
     * that use this data. Create these jobs now.
     * 
     * @return List of created BookingScraperJob
     */
    private List<BookingScraperJob> insertBookingsScraperJobs() {

        // create a separate job for each checkin_date for the given allocation records
        // this will make it easier to re-run if any date fails
        List<BookingScraperJob> jobs = new ArrayList<BookingScraperJob>();
        for ( Date checkinDate : dao.getCheckinDatesForAllocationScraperJobId( getAllocationScraperJobId() ) ) {
            BookingScraperJob bookingScraperJob = new BookingScraperJob();
            bookingScraperJob.setStatus( JobStatus.submitted );
            bookingScraperJob.setAllocationScraperJobId( getAllocationScraperJobId() );
            bookingScraperJob.setCheckinDate( checkinDate );
            int jobId = dao.insertJob( bookingScraperJob );
            jobs.add( BookingScraperJob.class.cast( dao.fetchJobById( jobId ) ) );
        }
        return jobs;
    }

    /**
     * Creates an additional job to run the split room report.
     * 
     * @param dependentJobs the jobs which need to complete successfully before running the jobs
     *            being created
     */
    private void insertSplitRoomReportJob( List<? extends Job> dependentJobs ) {
        SplitRoomReservationReportJob splitRoomReportJob = new SplitRoomReservationReportJob();
        splitRoomReportJob.setStatus( JobStatus.submitted );
        splitRoomReportJob.setAllocationScraperJobId( getAllocationScraperJobId() );
        splitRoomReportJob.getDependentJobs().addAll( dependentJobs );
        dao.insertJob( splitRoomReportJob );
    }

    /**
     * Creates an additional job to run the unpaid deposit report.
     * 
     * @param dependentJobs the jobs which need to complete successfully before running the jobs
     *            being created
     */
    private void insertUnpaidDepositReportJob( List<? extends Job> dependentJobs ) {
        UnpaidDepositReportJob unpaidDepositRptJob = new UnpaidDepositReportJob();
        unpaidDepositRptJob.setStatus( JobStatus.submitted );
        unpaidDepositRptJob.setAllocationScraperJobId( getAllocationScraperJobId() );
        unpaidDepositRptJob.getDependentJobs().addAll( dependentJobs );
        dao.insertJob( unpaidDepositRptJob );
    }

    /**
     * Creates an additional job to run the group bookings report.
     * 
     * @param dependentJobs the jobs which need to complete successfully before running the jobs
     *            being created
     */
    private void insertGroupBookingsReportJob( List<? extends Job> dependentJobs ) {
        GroupBookingsReportJob groupBookingRptJob = new GroupBookingsReportJob();
        groupBookingRptJob.setStatus( JobStatus.submitted );
        groupBookingRptJob.setAllocationScraperJobId( getAllocationScraperJobId() );
        groupBookingRptJob.getDependentJobs().addAll( dependentJobs );
        dao.insertJob( groupBookingRptJob );
    }

    /**
     * Creates additional jobs to tick off any unpaid deposits for HW/HB.
     * 
     */
    private void insertHostelworldHostelBookersConfirmDepositJob() {
        CreateConfirmDepositAmountsJob j = new CreateConfirmDepositAmountsJob();
        j.setStatus( JobStatus.submitted );
        j.setAllocationScraperJobId( getAllocationScraperJobId() );
        dao.insertJob( j );
    }

    /**
     * Creates jobs to add a no-charge note to all Agoda bookings.
     * 
     * @param dependentJobs the jobs which need to complete successfully before running the jobs
     *            being created
     */
    private void insertCreateAgodaNoChargeNoteJob( List<? extends Job> dependentJobs ) {
        CreateAgodaNoChargeNoteJob j = new CreateAgodaNoChargeNoteJob();
        j.getDependentJobs().addAll( dependentJobs );
        j.setStatus( JobStatus.submitted );
        dao.insertJob( j );
    }

    public int getAllocationScraperJobId() {
        return Integer.parseInt( getParameter( "allocation_scraper_job_id" ) );
    }

    public void setAllocationScraperJobId( int jobId ) {
        setParameter( "allocation_scraper_job_id", String.valueOf( jobId ) );
    }

}
