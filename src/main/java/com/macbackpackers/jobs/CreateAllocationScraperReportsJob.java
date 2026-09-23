package com.macbackpackers.jobs;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;

import org.springframework.transaction.annotation.Transactional;

import com.macbackpackers.beans.JobStatus;

/**
 * Creates report jobs from current {@code wp_lh_booking_assignment} / dual-written calendar
 * for a completed {@link AllocationScraperJob} heal.
 */
@Entity
@DiscriminatorValue( value = "com.macbackpackers.jobs.CreateAllocationScraperReportsJob" )
public class CreateAllocationScraperReportsJob extends AbstractJob {

    @Override
    @Transactional
    public void processJob() throws Exception {
        // Worker remapping retired — AllocationScraperJob dual-writes calendar under its own job id.
        insertSplitRoomReportJob();
        insertUnpaidDepositReportJob();
        insertGroupBookingsReportJob();
        insertMostlyFullDormReportJob();
        insertBlacklistEmailJob();
    }

    private void insertSplitRoomReportJob() {
        SplitRoomReservationReportJob splitRoomReportJob = new SplitRoomReservationReportJob();
        splitRoomReportJob.setStatus( JobStatus.submitted );
        splitRoomReportJob.setAllocationScraperJobId( getAllocationScraperJobId() );
        dao.insertJob( splitRoomReportJob );
    }

    private void insertUnpaidDepositReportJob() {
        UnpaidDepositReportJob unpaidDepositRptJob = new UnpaidDepositReportJob();
        unpaidDepositRptJob.setStatus( JobStatus.submitted );
        unpaidDepositRptJob.setAllocationScraperJobId( getAllocationScraperJobId() );
        dao.insertJob( unpaidDepositRptJob );
    }

    private void insertGroupBookingsReportJob() {
        GroupBookingsReportJob groupBookingRptJob = new GroupBookingsReportJob();
        groupBookingRptJob.setStatus( JobStatus.submitted );
        groupBookingRptJob.setAllocationScraperJobId( getAllocationScraperJobId() );
        dao.insertJob( groupBookingRptJob );
    }

    private void insertMostlyFullDormReportJob() {
        MostlyFullDormReportJob mostlyFullDormRptJob = new MostlyFullDormReportJob();
        mostlyFullDormRptJob.setStatus( JobStatus.submitted );
        mostlyFullDormRptJob.setAllocationScraperJobId( getAllocationScraperJobId() );
        dao.insertJob( mostlyFullDormRptJob );
    }

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
