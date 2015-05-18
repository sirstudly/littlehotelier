package com.macbackpackers.dao;

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
     * Creates a report that determines reservations which, for the same room type, are split
     * amongst different rooms.
     * 
     * @param allocationScraperJobId job ID of the allocation scraper job to use data from
     */
    public void runSplitRoomsReservationsReport( int allocationScraperJobId );
}