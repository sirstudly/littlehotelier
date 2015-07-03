package com.macbackpackers.dao;

import java.util.Date;
import java.util.List;

import org.apache.commons.lang3.time.FastDateFormat;
import org.springframework.dao.EmptyResultDataAccessException;

import com.macbackpackers.beans.Allocation;
import com.macbackpackers.beans.Job;
import com.macbackpackers.beans.JobStatus;
import com.macbackpackers.exceptions.IncorrectNumberOfRecordsUpdatedException;
import com.macbackpackers.jobs.AbstractJob;

public interface WordPressDAO {

    /**
     * Format for use when parsing whole dates (e.g. checkin/checkout dates, etc.)
     */
    public static final FastDateFormat DATE_FORMAT_YYYY_MM_DD = FastDateFormat.getInstance(  "yyyy-MM-dd 00:00:00" );

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
     * Returns an allocation by primary key.
     * @param id primary key
     * @return non-null allocation
     * @throws EmptyResultDataAccessException if allocation does not exist
     */
    public Allocation fetchAllocation( int id ) throws EmptyResultDataAccessException;
    
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
    public AbstractJob fetchJobById( int id ) throws EmptyResultDataAccessException;
    
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