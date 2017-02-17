
package com.macbackpackers.jobs;

import java.text.ParseException;
import java.util.Calendar;
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
    public void resetJob() throws Exception {
        dao.deleteAllocations( getId() );
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
            BookingScraperJob bookingScraperJob = new BookingScraperJob();
            bookingScraperJob.setStatus( JobStatus.submitted );
            bookingScraperJob.setAllocationScraperJobId( getId() );
            bookingScraperJob.setCheckinDate( checkinDate );
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
        SplitRoomReservationReportJob splitRoomReportJob = new SplitRoomReservationReportJob();
        splitRoomReportJob.setStatus( JobStatus.submitted );
        splitRoomReportJob.setAllocationScraperJobId( getId() );
        dao.insertJob( splitRoomReportJob );
    }

    /**
     * Creates an additional job to run the unpaid deposit report.
     */
    private void insertUnpaidDepositReportJob() {
        UnpaidDepositReportJob unpaidDepositRptJob = new UnpaidDepositReportJob();
        unpaidDepositRptJob.setStatus( JobStatus.submitted );
        unpaidDepositRptJob.setAllocationScraperJobId( getId() );
        dao.insertJob( unpaidDepositRptJob );
    }

    /**
     * Creates an additional job to run the group bookings report.
     */
    private void insertGroupBookingsReportJob() {
        GroupBookingsReportJob groupBookingRptJob = new GroupBookingsReportJob();
        groupBookingRptJob.setStatus( JobStatus.submitted );
        groupBookingRptJob.setAllocationScraperJobId( getId() );
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
    public Date getStartDate() throws ParseException {
        return AllocationsPageScraper.DATE_FORMAT_YYYY_MM_DD.parse( getParameter( "start_date" ) );
    }

    /**
     * Sets the date to start scraping the allocation data (inclusive).
     * 
     * @param startDate non-null date parameter
     */
    public void setStartDate( Date startDate ) {
        setParameter( "start_date", AllocationsPageScraper.DATE_FORMAT_YYYY_MM_DD.format( startDate ) );
    }

    /**
     * Returns the number of days ahead to look for allocations from the start date.
     * 
     * @return non-negative number of days
     */
    public int getDaysAhead() {
        return Integer.parseInt( getParameter( "days_ahead" ) );
    }

    /**
     * Sets the number of days ahead to look for allocations from the start date.
     * 
     * @param daysAhead number of days
     */
    public void setDaysAhead( int daysAhead ) {
        if ( daysAhead < 0 ) {
            throw new IllegalArgumentException( "Days ahead must be non-negative" );
        }
        setParameter( "days_ahead", String.valueOf( daysAhead ) );
    }

    /**
     * Gets the date to stop scraping the allocation data (inclusive).
     * 
     * @return non-null date parameter
     * @throws ParseException
     */
    public Date getEndDate() throws ParseException {
        Calendar cal = Calendar.getInstance();
        cal.add( Calendar.DATE, getDaysAhead() );
        return cal.getTime();
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
