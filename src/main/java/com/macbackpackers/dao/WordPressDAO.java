package com.macbackpackers.dao;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Properties;

import org.springframework.dao.EmptyResultDataAccessException;

import com.macbackpackers.beans.Allocation;
import com.macbackpackers.beans.BedSheetEntry;
import com.macbackpackers.beans.Job;
import com.macbackpackers.beans.JobStatus;
import com.macbackpackers.exceptions.IncorrectNumberOfRecordsUpdatedException;
import com.macbackpackers.jobs.AbstractJob;

public interface WordPressDAO {
    
    public static final SimpleDateFormat DATE_FORMAT_YYYY_MM_DD = new SimpleDateFormat(  "yyyy-MM-dd 00:00:00" );

    // BedSheets
    // Room | Bed | W Th Fr Sa Su Mo Tu |
    // 12 Change / No Change / 3-Day Change
    //
    // wp_bedcount
    // job id
    // room #
    // bed name
    // date (mm/dd/yyyy)
    // change Y/N/3

    /*
     * 
     * CREATE TABLE `wp_bedsheets` ( `id` bigint(20) unsigned NOT NULL AUTO_INCREMENT, `job_id` bigint(2) unsigned,
     * `room` varchar(50) NOT NULL, `bed_name` varchar(50) DEFAULT NULL, `checkout_date` datetime NOT NULL, `change`
     * varchar(1) DEFAULT 'N' NOT NULL, `created_date` timestamp DEFAULT CURRENT_TIMESTAMP, PRIMARY KEY (`id`) )DEFAULT
     * CHARSET=utf8;
     */
    public List<BedSheetEntry> getAllBedSheetEntriesForDate( int jobId, Date date );
    
    public void insertBedSheetEntry( BedSheetEntry entry );

    public void deleteAllBedSheetEntriesForJobId( int jobId );

    /**
     * Inserts a new allocation. The assigned id will be set on the
     * given allocation object.
     * 
     * @param alloc the new allocation to insert
     */
    public void insertAllocation( Allocation alloc );
    
    /**
     * Updates extended attributes on an existing allocation by
     * reservation id <b>NOT by id (primary key)</i>.
     * 
     * @param alloc allocation to update in DB
     */
    public void updateAllocation( Allocation alloc );
    
    /**
     * Queries an existing allocation by job id and reservation id.
     * 
     * @param jobId ID of job that scraped the original allocation
     * @param reservationId the reservationId to match
     * @return list of matched allocations
     * @throws EmptyResultDataAccessException if no record found
     */
    public List<Allocation> queryAllocationsByJobIdAndReservationId( int jobId, int reservationId ) throws EmptyResultDataAccessException;

    /**
     * Inserts a job and associated parameters
     * 
     * @param job job to insert
     * @return PK of job
     */
    public int insertJob( Job job );

    /**
     * Purges all job data
     */
    public void deleteAllJobData();
    
    /**
     * Purges all transactional data.
     */
    public void deleteAllTransactionalData();

    /**
     * Updates the status of the given job.
     * 
     * @param jobId PK of job
     * @param status status to set
     * @param prevStatus the previous status to verify
     * @throws IncorrectNumberOfRecordsUpdatedException if job doesn't match prevStatus
     */
    public void updateJobStatus( int jobId, JobStatus status, JobStatus prevStatus ) 
            throws IncorrectNumberOfRecordsUpdatedException;

    /**
     * Updates all job statuses of 'processing' to 'failed'.
     */
    public void resetAllProcessingJobsToFailed();
    
    /**
     * Returns the first job with a state of 'submitted'.
     * 
     * @return next job to run or null if no jobs to run
     */
    public AbstractJob getNextJobToProcess();
    
    /**
     * Retrieve a job by PK.
     * 
     * @param id job ID
     * @return non-null job
     * @throws EmptyResultDataAccessException if job not found
     */
    public Job getJobById( int id ) throws EmptyResultDataAccessException;
    
    /**
     * Gets all parameters for the given job. 
     * 
     * @param jobId PK of job
     * @return non-null job parameters
     */
    public Properties getJobParameters( int jobId );
    
    /**
     * Returns the most recent completed job of the given type.
     * 
     * @param jobType type of job
     * @return last job or null if not found
     */
    public <T extends AbstractJob> T getLastCompletedJobOfType( Class<T> jobType );

    /**
     * Creates a report that determines reservations which, for the same room type, are split
     * amongst different rooms.
     * 
     * @param allocationScraperJobId job ID of the allocation scraper job to use data from
     */
    public void runSplitRoomsReservationsReport( int allocationScraperJobId );
    
    /**
     * Creates a report with all bookings where no deposit had been paid yet.
     * 
     * @param allocationScraperJobId job ID of the allocation scraper job to use data from
     */
    public void runUnpaidDepositReport( int allocationScraperJobId );
    
    /**
     * Creates a report with all bookings with more than 5 guests.
     * 
     * @param allocationScraperJobId job ID of the allocation scraper job to use data from
     */
    public void runGroupBookingsReport( int allocationScraperJobId );

    /**
     * Returns the list of HW/HB reservation IDs for which the deposit amount has
     * not yet been deducted from the total.
     * 
     * @param allocationScraperJobId job ID of the allocation scraper job to use data from
     * @return non-null list of reservation IDs
     */
    public List<Integer> getHostelworldHostelBookersUnpaidDepositReservations( int allocationScraperJobId );
    
    /**
     * Returns the checkin dates for all allocations found by the
     * given AllocationScraper job ID.
     * 
     * @param jobId allocation scraper job ID
     * @return list of checkin dates
     */
    public List<Date> getCheckinDatesForAllocationScraperJobId( int jobId );

}