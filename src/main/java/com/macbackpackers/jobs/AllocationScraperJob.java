
package com.macbackpackers.jobs;

import java.text.ParseException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;

import com.macbackpackers.config.LittleHotelierConfig;
import org.springframework.transaction.annotation.Transactional;

import com.macbackpackers.beans.Job;
import com.macbackpackers.beans.JobStatus;

/**
 * Job that initiates all allocation scraper jobs for a particular date range.
 *
 */
@Entity
@DiscriminatorValue( value = "com.macbackpackers.jobs.AllocationScraperJob" )
public class AllocationScraperJob extends AbstractJob {

    @Override
    @Transactional // no re-run on this job; all must go thru or nothing
    public void processJob() throws Exception {
        insertCreateReportsJob( insertCloudbedsAllocationScraperWorkerJobs() );
    }

    /**
     * Create jobs to scrape the calendar page(s) from start date to end date.
     * 
     * @return List of created AllocationScraperWorkerJob
     * @throws ParseException on date parse error from parameters
     */
    private List<CloudbedsAllocationScraperWorkerJob> insertCloudbedsAllocationScraperWorkerJobs() throws ParseException {
        List<CloudbedsAllocationScraperWorkerJob> jobs = new ArrayList<CloudbedsAllocationScraperWorkerJob>();

        LocalDate currentDate = LocalDate.now();
        while ( currentDate.isBefore( getEndLocalDate() ) ) {
            CloudbedsAllocationScraperWorkerJob workerJob = new CloudbedsAllocationScraperWorkerJob();
            workerJob.setStatus( JobStatus.submitted );
            workerJob.setAllocationScraperJobId( getId() );
            workerJob.setStartDate( currentDate );
            workerJob.setEndDate( currentDate.plusDays( 1 ) );
            int jobId = dao.insertJob( workerJob );
            jobs.add( CloudbedsAllocationScraperWorkerJob.class.cast( dao.fetchJobById( jobId ) ) );
            currentDate = currentDate.plusDays( 1 ); // calendar page shows 2 weeks at a time
        }
        return jobs;
    }

    /**
     * Creates a job for consolidating results and running collating reports.
     * 
     * @param dependentJobs the jobs which need to complete successfully before running the jobs
     *            being created
     */
    private void insertCreateReportsJob( List<? extends Job> dependentJobs ) {
        CreateAllocationScraperReportsJob job = new CreateAllocationScraperReportsJob();
        job.setStatus( JobStatus.submitted );
        job.setAllocationScraperJobId( getId() );
        job.getDependentJobs().addAll( dependentJobs );
        dao.insertJob( job );
    }

    /**
     * Gets the date to start scraping the allocation data (inclusive).
     * 
     * @return non-null date parameter
     * @throws ParseException
     */
    public Date getStartDate() throws ParseException {
        return LittleHotelierConfig.DATE_FORMAT_YYYY_MM_DD.parse( getParameter( "start_date" ) );
    }

    /**
     * Sets the date to start scraping the allocation data (inclusive).
     * 
     * @param startDate non-null date parameter
     */
    public void setStartDate( Date startDate ) {
        setParameter( "start_date", LittleHotelierConfig.DATE_FORMAT_YYYY_MM_DD.format( startDate ) );
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
        cal.setTime( getStartDate() );
        cal.add( Calendar.DATE, getDaysAhead() );
        return cal.getTime();
    }

    /**
     * Gets the date to stop scraping the allocation data (inclusive).
     * 
     * @return non-null local date parameter
     */
    public LocalDate getEndLocalDate() {
        return LocalDate.now().plusDays( getDaysAhead() );
    }

}
