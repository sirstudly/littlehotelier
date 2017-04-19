
package com.macbackpackers.dao;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

import org.apache.commons.lang3.time.FastDateFormat;
import org.springframework.dao.EmptyResultDataAccessException;

import com.macbackpackers.beans.Allocation;
import com.macbackpackers.beans.AllocationList;
import com.macbackpackers.beans.HostelworldBooking;
import com.macbackpackers.beans.Job;
import com.macbackpackers.beans.JobStatus;
import com.macbackpackers.beans.MissingGuestComment;
import com.macbackpackers.beans.PxPostTransaction;
import com.macbackpackers.beans.ScheduledJob;
import com.macbackpackers.beans.SendEmailEntry;
import com.macbackpackers.beans.UnpaidDepositReportEntry;
import com.macbackpackers.exceptions.IncorrectNumberOfRecordsUpdatedException;
import com.macbackpackers.jobs.AbstractJob;

public interface WordPressDAO {

    /**
     * Format for use when parsing whole dates (e.g. checkin/checkout dates, etc.)
     */
    public static final FastDateFormat DATE_FORMAT_YYYY_MM_DD = FastDateFormat.getInstance( "yyyy-MM-dd 00:00:00" );

    /**
     * Inserts a new allocation. The assigned id will be set on the given allocation object.
     * 
     * @param alloc the new allocation to insert
     */
    public void insertAllocation( Allocation alloc );

    /**
     * Updates attributes on an existing allocation.
     * 
     * @param alloc allocation to update in DB
     */
    public void updateAllocation( Allocation alloc );

    /**
     * Updates attributes to a list of existing allocations.
     * 
     * @param allocList allocations to update in DB
     */
    public void updateAllocationList( AllocationList allocList );

    /**
     * Returns an allocation by primary key.
     * 
     * @param id primary key
     * @return non-null allocation
     * @throws EmptyResultDataAccessException if allocation does not exist
     */
    public Allocation fetchAllocation( int id ) throws EmptyResultDataAccessException;

    /**
     * Deletes all Allocations for the given jobId.
     * 
     * @param jobId the records for the job to delete.
     */
    public void deleteAllocations( int jobId );

    /**
     * Queries all existing allocations by job id and reservation id.
     * 
     * @param jobId ID of job that scraped the original allocation
     * @param reservationId the reservationId to match
     * @return non-null list of matched allocations
     */
    public AllocationList queryAllocationsByJobIdAndReservationId( int jobId, int reservationId );

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
     * Updates the status of the given job.
     * 
     * @param jobId PK of job
     * @param status status to set
     * @throws EmptyResultDataAccessException if record not found
     */
    public void updateJobStatus( int jobId, JobStatus status ) throws EmptyResultDataAccessException;
    
    /**
     * Updates the given job from 'submitted' to 'processing' and sets the jobStartDate and
     * jobEndDate. If the job is already at processing and the "processedBy" field matches this
     * processor, then this is also included and the fields updated. This method can be used to
     * "lock" a record for processing so another processor doesn't take it.
     * 
     * @param jobId the unique id of the job to update
     * @return true if we managed to update the row; false if the job wasn't found or wasn't in the
     *         correct state (if another processor had picked it up for example).
     */
    public boolean updateJobStatusToProcessing( int jobId );

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
     * Returns a list of active jobs to schedule with the scheduler.
     * 
     * @return non-null list of jobs
     */
    public List<ScheduledJob> fetchActiveScheduledJobs();

    /**
     * Retrieve a scheduled job by PK.
     * 
     * @param jobId job ID
     * @return non-null job
     * @throws EmptyResultDataAccessException if job not found
     */
    public ScheduledJob fetchScheduledJobById( int jobId ) throws EmptyResultDataAccessException;

    /**
     * Updates the last scheduled date for the given scheduled job.
     * 
     * @param jobId ID of scheduled job to update
     * @throws EmptyResultDataAccessException if jobId doesn't exist
     */
    public void updateScheduledJob( int jobId ) throws EmptyResultDataAccessException;

    /**
     * Returns the most recent job of the given type.
     * 
     * @param jobType type of job
     * @return last job or null if not found
     */
    public <T extends AbstractJob> T getLastJobOfType( Class<T> jobType );

    /**
     * Returns the most recent completed job of the given type.
     * 
     * @param jobType type of job
     * @return last job or null if not found
     */
    public <T extends AbstractJob> T getLastCompletedJobOfType( Class<T> jobType );

    /**
     * Removes records older than the given date.
     * 
     * @param specifiedDate records older than this will be removed.
     */
    public void purgeRecordsOlderThan( Date specifiedDate );

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
     * Returns the last completed unpaid deposit report.
     * 
     * @param allocationScraperJobId job ID of the allocation scraper job to use data from
     * @return unpaid deposit report or null if never run successfully
     */
    public List<UnpaidDepositReportEntry> fetchUnpaidDepositReport( int allocationScraperJobId );

    /**
     * Creates a report with all bookings with more than 5 guests.
     * 
     * @param allocationScraperJobId job ID of the allocation scraper job to use data from
     */
    public void runGroupBookingsReport( int allocationScraperJobId );

    /**
     * Returns the list of HW/HB reservation IDs for which the deposit amount has not yet been
     * deducted from the total.
     * 
     * @param allocationScraperJobId job ID of the allocation scraper job to use data from
     * @return non-null list of reservation IDs
     */
    public List<Integer> getHostelworldHostelBookersUnpaidDepositReservations( int allocationScraperJobId );

    /**
     * Returns the checkin dates for all allocations found by the given AllocationScraper job ID.
     * 
     * @param jobId allocation scraper job ID
     * @return list of checkin dates
     */
    public List<Date> getCheckinDatesForAllocationScraperJobId( int jobId );

    /**
     * Inserts a new HW booking.
     * 
     * @param booking booking to save
     */
    public void insertHostelworldBooking( HostelworldBooking booking );

    /**
     * Deletes all HW bookings matching the given arrival date.
     * 
     * @param checkinDate date of arrival
     */
    public void deleteHostelworldBookingsWithArrivalDate( Date checkinDate );

    /**
     * Deletes all HB bookings matching the given arrival date.
     * 
     * @param checkinDate date of arrival
     */
    public void deleteHostelbookersBookingsWithArrivalDate( Date checkinDate );

    /**
     * Returns the room type (id) for the given hostelworld room type label.
     * 
     * @param roomTypeLabel e.g. 12 Bed Female Dorm
     * @return room type ID (or null if cannot be determined)
     */
    public Integer getRoomTypeIdForHostelworldLabel( String roomTypeLabel );

    /**
     * Returns the ID of the last completed allocation scraper job.
     * 
     * @return job ID or null if none exist
     */
    public Integer getLastCompletedAllocationScraperJobId();

    /**
     * Returns the HW booking references which don't appear in the calendar table for the given
     * jobId.
     * 
     * @param jobId ID of (DiffBooking) job in HW dump/calendar tables that we're comparing
     * @param checkinDate date for which the report is being run
     * @return non-null list of HW booking references 
     */
    public List<String> findMissingHwBookingRefs( int jobId, Date checkinDate );

    /**
     * Returns a list of Allocations which haven't been added to the guest comments report
     * table.
     * 
     * @param allocationScraperJobId the scraper job ID which was run
     * @return non-null list of bookings
     */
    public List<MissingGuestComment> getAllocationsWithoutEntryInGuestCommentsReport( int allocationScraperJobId );

    /**
     * Inserts/updates the table for guest comments with the given guest comment.
     * 
     * @param reservationId ID of reservation to update
     * @param comment the guest comment (if applicable) for the reservation
     */
    public void updateGuestCommentsForReservation( int reservationId, String comment );

    /**
     * Returns the wordpress option for the given property.
     * 
     * @param property name of key to get
     * @return option value or null if key doesn't exist
     */
    public String getOption( String property );

    /**
     * Returns the minimum number of people considered a group.
     *  
     * @return group booking size
     */
    public int getGroupBookingSize();

    /**
     * Sets the wordpress option for the given property.
     * 
     * @param property name of key to get
     * @param value value to set
     */
    public void setOption( String property, String value );
    
    /**
     * Fetch px post transaction record by primary key.
     * 
     * @param txnId unique transaction ID
     * @return non-null transaction
     */
    public PxPostTransaction fetchPxPostTransaction( int txnId );

    /**
     * Returns the last px post transaction for a given booking reference.
     * 
     * @param bookingReference the LH booking reference
     * @return the last px post transaction entry or null if not found
     */
    public PxPostTransaction getLastPxPost( String bookingReference );

    /**
     * Updates the status XML for the given record.
     * 
     * @param txnId unique transaction id
     * @param maskedCardNumber the card used for the transaction
     * @param successful whether the payment successful
     * @param statusXml status XML
     */
    public void updatePxPostStatus( int txnId, String maskedCardNumber, boolean successful, String statusXml );

    /**
     * Inserts a new transaction into the PX Post table.
     * 
     * @param bookingRef the booking reference
     * @param amountToPay amount being charged
     * @return unique transaction id
     */
    public int insertNewPxPostTransaction( String bookingRef, BigDecimal amountToPay );

    /**
     * Updates the transaction after sending to the payment gateway.
     * 
     * @param txnId unique ID for the transaction
     * @param maskedCardNumber the masked card number
     * @param requestXML the XML sent to the payment gateway
     * @param httpStatus the response HTTP code
     * @param responseXML the response XML received from the payment gateway
     * @param successful whether this transaction was successful or not
     * @param helpText failure text (if applicable)
     */
    public void updatePxPostTransaction( int txnId, String maskedCardNumber, String requestXML, int httpStatus, 
            String responseXML, boolean successful, String helpText );

    /**
     * Returns the number of failed transactions for the given booking reference and (masked) card
     * number.
     * 
     * @param bookingRef e.g. BDC-123456789
     * @param maskedCardNumber the partial card number to match
     * @return number of failed payment attempts
     */
    public int getPreviousNumberOfFailedTxns( String bookingRef, String maskedCardNumber );
    
    /**
     * Checks whether we've already sent an email (or are ready to send) an email to the
     * given address.
     * @param email address to send to
     * @return true if entry already exists, false otherwise
     */
    public boolean doesSendEmailEntryExist( String email );
    
    /**
     * Creates the send email record specified.
     * 
     * @param record email record to save
     */
    public void saveSendEmailEntry( SendEmailEntry record );

    /**
     * Deletes all send email records by email address.
     * 
     * @param emailAddress matched email address
     */
    public void deleteSendEmailEntry( String emailAddress );

    /**
     * Returns all emails that haven't been sent yet.
     * @return list of unsent email entries
     */
    public List<SendEmailEntry> fetchAllUnsentEmails();

    /**
     * Returns the email subject when sending emails to guests who have checked-out.
     * 
     * @return non-null email subject
     */
    public String getGuestCheckoutEmailSubject();

    /**
     * Returns the email template when sending emails to guests who have checked-out.
     * 
     * @return non-null email template
     */
    public String getGuestCheckoutEmailTemplate();
}
