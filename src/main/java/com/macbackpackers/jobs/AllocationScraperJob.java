
package com.macbackpackers.jobs;

import java.text.ParseException;
import java.util.Date;

import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;
import javax.persistence.Transient;

import org.springframework.beans.factory.annotation.Autowired;

import com.macbackpackers.beans.Job;
import com.macbackpackers.beans.JobStatus;
import com.macbackpackers.scrapers.AllocationsPageScraper;

/**
 * Job that scrapes the allocation data for a particular date range.
 *
 */
@Entity
@DiscriminatorValue( value = "com.macbackpackers.jobs.AllocationScraperJob" )
public class AllocationScraperJob extends AbstractJob {

    @Autowired
    @Transient
    private AllocationsPageScraper allocationScraper;

    public AllocationsPageScraper getAllocationPageScraper() {
        return allocationScraper;
    }

    @Override
    public void processJob() throws Exception {
        allocationScraper.dumpAllocationsBetween( getId(), getStartDate(), getEndDate(), isTestMode() );
        insertBookingsScraperJobs();
    }

    /**
     * We need additional jobs to update some fields by scraping the bookings pages and any reports
     * that use this data. Create these jobs now.
     */
    private void insertBookingsScraperJobs() {

        // create a separate job for each checkin_date for the given allocation records
        // this will make it easier to re-run if any date fails
        for ( Date checkinDate : dao.getCheckinDatesForAllocationScraperJobId( getId() ) ) {
            Job bookingScraperJob = new BookingScraperJob();
            bookingScraperJob.setStatus( JobStatus.submitted );
            bookingScraperJob.setParameter( "allocation_scraper_job_id", String.valueOf( getId() ) );
            bookingScraperJob.setParameter( "checkin_date",
                    AllocationsPageScraper.DATE_FORMAT_YYYY_MM_DD.format( checkinDate ) );
            dao.insertJob( bookingScraperJob );
        }

        // insert jobs to create any reports
        insertSplitRoomReportJob();
        insertUnpaidDepositReportJob();
        insertGroupBookingsReportJob();
        insertHostelworldHostelBookersConfirmDepositJob();
    }

    /**
     * Creates an additional job to run the split room report.
     */
    private void insertSplitRoomReportJob() {
        Job splitRoomReportJob = new SplitRoomReservationReportJob();
        splitRoomReportJob.setStatus( JobStatus.submitted );
        splitRoomReportJob.setParameter( "allocation_scraper_job_id", String.valueOf( getId() ) );
        dao.insertJob( splitRoomReportJob );
    }

    /**
     * Creates an additional job to run the unpaid deposit report.
     */
    private void insertUnpaidDepositReportJob() {
        Job unpaidDepositRptJob = new UnpaidDepositReportJob();
        unpaidDepositRptJob.setStatus( JobStatus.submitted );
        unpaidDepositRptJob.setParameter( "allocation_scraper_job_id", String.valueOf( getId() ) );
        dao.insertJob( unpaidDepositRptJob );
    }

    /**
     * Creates an additional job to run the group bookings report.
     */
    private void insertGroupBookingsReportJob() {
        Job groupBookingRptJob = new GroupBookingsReportJob();
        groupBookingRptJob.setStatus( JobStatus.submitted );
        groupBookingRptJob.setParameter( "allocation_scraper_job_id", String.valueOf( getId() ) );
        dao.insertJob( groupBookingRptJob );
    }

    /**
     * Creates additional jobs to tick off any unpaid deposits for HW/HB.
     */
    private void insertHostelworldHostelBookersConfirmDepositJob() {
        Job j = new CreateConfirmDepositAmountsJob();
        j.setStatus( JobStatus.submitted );
        dao.insertJob( j );
    }

    /**
     * Gets the date to start scraping the allocation data (inclusive).
     * 
     * @return non-null date parameter
     * @throws ParseException
     */
    private Date getStartDate() throws ParseException {
        return AllocationsPageScraper.DATE_FORMAT_YYYY_MM_DD.parse( getParameter( "start_date" ) );
    }

    /**
     * Gets the date to stop scraping the allocation data (inclusive).
     * 
     * @return non-null date parameter
     * @throws ParseException
     */
    private Date getEndDate() throws ParseException {
        return AllocationsPageScraper.DATE_FORMAT_YYYY_MM_DD.parse( getParameter( "end_date" ) );
    }

    /**
     * For testing, we may want to load from a previously serialised file (rather than scrape the
     * page again which takes about 10 minutes). Default value is false, but if the "test_mode"
     * parameter is set to true, then attempt to load from file first. If no file is found, then
     * scrape the page again.
     * 
     * @return true if test mode, false otherwise
     */
    private boolean isTestMode() {
        return "true".equalsIgnoreCase( getParameter( "test_mode" ) );
    }

}
