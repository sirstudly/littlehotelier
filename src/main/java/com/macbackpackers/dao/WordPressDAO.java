
package com.macbackpackers.dao;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import javax.persistence.NoResultException;

import com.macbackpackers.beans.Allocation;
import com.macbackpackers.beans.AllocationList;
import com.macbackpackers.beans.BlacklistEntry;
import com.macbackpackers.beans.BookingByCheckinDate;
import com.macbackpackers.beans.BookingReport;
import com.macbackpackers.beans.BookingWithGuestComments;
import com.macbackpackers.beans.GuestCommentReportEntry;
import com.macbackpackers.beans.HostelworldBooking;
import com.macbackpackers.beans.Job;
import com.macbackpackers.beans.JobScheduler;
import com.macbackpackers.beans.JobStatus;
import com.macbackpackers.beans.RoomBed;
import com.macbackpackers.beans.RoomBedLookup;
import com.macbackpackers.beans.ScheduledJob;
import com.macbackpackers.beans.SendEmailEntry;
import com.macbackpackers.beans.StripeRefund;
import com.macbackpackers.beans.StripeTransaction;
import com.macbackpackers.beans.UnpaidDepositReportEntry;
import org.apache.commons.lang3.time.FastDateFormat;
import org.springframework.dao.EmptyResultDataAccessException;

import com.macbackpackers.exceptions.IncorrectNumberOfRecordsUpdatedException;
import com.macbackpackers.exceptions.MissingUserDataException;
import com.macbackpackers.jobs.AbstractJob;
import com.macbackpackers.jobs.ResetCloudbedsSessionJob;

public interface WordPressDAO {

    /**
     * Returns whether the property manager is Cloudbeds.
     * 
     * @return true if cloudbeds, false if not.
     */
    boolean isCloudbeds();

    /**
     * Format for use when parsing whole dates (e.g. checkin/checkout dates, etc.)
     */
    FastDateFormat DATE_FORMAT_YYYY_MM_DD = FastDateFormat.getInstance( "yyyy-MM-dd 00:00:00" );

    /**
     * Inserts a new allocation. The assigned id will be set on the given allocation object.
     * 
     * @param alloc the new allocation to insert
     */
    void insertAllocation( Allocation alloc );
    
    /**
     * Bulk insert a bunch of allocations.
     * 
     * @param allocations new allocations to create
     */
    void insertAllocations( AllocationList allocations );

    /**
     * Insert booking report records.
     * 
     * @param bookingReport records to insert
     */
    void insertBookingReport( List<BookingReport> bookingReport );

    /**
     * Delete booking report records by jobId.
     * 
     * @param jobId job id
     */
    void deleteBookingReport( int jobId );

    /**
     * Updates attributes on an existing allocation.
     * 
     * @param alloc allocation to update in DB
     */
    void updateAllocation( Allocation alloc );

    /**
     * Updates attributes to a list of existing allocations.
     * 
     * @param allocList allocations to update in DB
     */
    void updateAllocationList( AllocationList allocList );

    /**
     * Returns an allocation by primary key.
     * 
     * @param id primary key
     * @return non-null allocation
     * @throws EmptyResultDataAccessException if allocation does not exist
     */
    Allocation fetchAllocation( int id ) throws EmptyResultDataAccessException;

    /**
     * Returns all booking references with the given checkin date for the AllocationScraperJob.
     * 
     * @param allocationScraperJobId job id
     * @param checkinDate checkin date
     * @return non-null booking reference list
     */
    List<String> fetchDistinctBookingsByCheckinDate( int allocationScraperJobId, Date checkinDate );

    /**
     * Deletes all Allocations for the given jobId.
     * 
     * @param jobId the records for the job to delete.
     */
    void deleteAllocations( int jobId );
    
    /**
     * Deletes all cancelled Allocations for the given jobId.
     * 
     * @param jobId the records for the job to delete.
     * @param checkinDateStart checkin date start (inclusive)
     * @param checkinDateEnd checkin date end (inclusive)
     */
    void deleteCancelledAllocations( int jobId, Date checkinDateStart, Date checkinDateEnd );
    
    /**
     * Updates the jobId associated with the matching Allocation records.
     * 
     * @param oldAllocationJobId the *matching* jobId (on Allocation) to update
     * @param newAllocationJobId the updated jobId to set
     */
    void updateAllocationJobId( int oldAllocationJobId, int newAllocationJobId );

    /**
     * Queries all existing allocations by job id and reservation id.
     * 
     * @param jobId ID of job that scraped the original allocation
     * @param reservationId the reservationId to match
     * @return non-null list of matched allocations
     */
    AllocationList queryAllocationsByJobIdAndReservationId( int jobId, int reservationId );

    /**
     * Inserts a job and associated parameters
     * 
     * @param job job to insert
     * @return PK of job
     */
    int insertJob( Job job );

    /**
     * Returns the number of jobs at 'submitted' or 'processing'.
     * 
     * @return number of jobs
     */
    long getOutstandingJobCount();

    /**
     * Updates the status of the given job.
     * 
     * @param jobId PK of job
     * @param status status to set
     * @param prevStatus the previous status to verify
     * @throws IncorrectNumberOfRecordsUpdatedException if job doesn't match prevStatus
     */
    void updateJobStatus( int jobId, JobStatus status, JobStatus prevStatus )
            throws IncorrectNumberOfRecordsUpdatedException;

    /**
     * Updates the status of the given job.
     * 
     * @param jobId PK of job
     * @param status status to set
     * @throws EmptyResultDataAccessException if record not found
     */
    void updateJobStatus( int jobId, JobStatus status ) throws EmptyResultDataAccessException;
    
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
    boolean updateJobStatusToProcessing( int jobId );

    /**
     * Updates all job statuses of 'processing' to 'failed'.
     */
    void resetAllProcessingJobsToFailed();

    /**
     * Returns the first job with a state of 'submitted' and by virtue of having selecting it, sets
     * the status to 'processing'. Additional side effect: when picking up the next job to process;
     * if it is dependent on another job at failed/aborted, it will set the status of this job to
     * aborted.
     * 
     * @return next job to run or null if no jobs to run
     */
    AbstractJob getNextJobToProcess();

    /**
     * Retrieve a job by PK.
     * 
     * @param id job ID
     * @return non-null job
     * @throws EmptyResultDataAccessException if job not found
     */
    AbstractJob fetchJobById( int id ) throws EmptyResultDataAccessException;

    /**
     * Retrieve a job by PK.
     *
     * @param id job ID
     * @param clazz expected class
     * @return non-null job
     * @throws EmptyResultDataAccessException if job not found
     */
    <T extends AbstractJob> T fetchJobById( int id, Class<T> clazz) throws EmptyResultDataAccessException;

    /**
     * Finds any outstanding {@link ResetCloudbedsSessionJob}s and returns the first one.
     * 
     * @return first ResetCloudbedsSessionJob if any
     */
    Optional<ResetCloudbedsSessionJob> fetchResetCloudbedsSessionJob();

    /**
     * Returns a list of active jobs to schedule with the scheduler.
     * 
     * @return non-null list of jobs
     */
    List<ScheduledJob> fetchActiveScheduledJobs();

    /**
     * Retrieve a scheduled job by PK.
     * 
     * @param jobId job ID
     * @return non-null job
     * @throws EmptyResultDataAccessException if job not found
     */
    ScheduledJob fetchScheduledJobById( int jobId ) throws EmptyResultDataAccessException;

    /**
     * Updates the last scheduled date for the given scheduled job.
     * 
     * @param jobId ID of scheduled job to update
     * @throws EmptyResultDataAccessException if jobId doesn't exist
     */
    void updateScheduledJob( int jobId ) throws EmptyResultDataAccessException;

    /**
     * Returns the list of active job schedules.
     * 
     * @return non-null list of job schedules
     */
    List<JobScheduler> fetchActiveJobSchedules();
    
    /**
     * Updates an existing JobScheduler.
     * 
     * @param schedule job scheduler to save
     */
    void updateJobScheduler( JobScheduler schedule );

    /**
     * Checks whether the given job is at {@link JobStatus#processing} or
     * {@link JobStatus#submitted}.
     * 
     * @param classname fully qualified name of job class
     * @return true if job is currently running or submitted
     */
    boolean isJobCurrentlyPending( String classname );

    /**
     * Returns the most recent job of the given type.
     * 
     * @param jobType type of job
     * @return last job or null if not found
     */
    <T extends AbstractJob> T getLastJobOfType( Class<T> jobType );

    /**
     * Returns the most recent completed job of the given type.
     * 
     * @param jobType type of job
     * @return last job or null if not found
     */
    <T extends AbstractJob> T getLastCompletedJobOfType( Class<T> jobType );

    /**
     * Retrieves all reservation IDs used for all DepositChargeJobs between the given job IDs (Issue
     * with accidentally charging incorrect bookings - need to send retraction email).
     * 
     * @param jobIdStart job id inclusive
     * @param jobIdEnd job id inclusive
     * @return non-null list
     */
    List<String> getReservationIdsForDepositChargeJobs( int jobIdStart, int jobIdEnd );

    /**
     * Removes records older than the given date.
     * 
     * @param specifiedDate records older than this will be removed.
     */
    void purgeRecordsOlderThan( Date specifiedDate );

    /**
     * Creates a report that determines reservations which, for the same room type, are split
     * amongst different rooms.
     * 
     * @param allocationScraperJobId job ID of the allocation scraper job to use data from
     */
    void runSplitRoomsReservationsReport( int allocationScraperJobId );

    /**
     * Creates a report with all bookings where no deposit had been paid yet.
     * 
     * @param allocationScraperJobId job ID of the allocation scraper job to use data from
     */
    void runUnpaidDepositReport( int allocationScraperJobId );
    
    /**
     * Returns the last completed unpaid deposit report.
     * 
     * @param allocationScraperJobId job ID of the allocation scraper job to use data from
     * @return unpaid deposit report or null if never run successfully
     */
    List<UnpaidDepositReportEntry> fetchUnpaidDepositReport( int allocationScraperJobId );

    /**
     * Returns a List of all BDC bookings which use a prepaid "virtual" CC and where there is still a balance
     * outstanding. The data is retrieved from the last successful AllocationScraperJob.
     * 
     * @return non-null list of booking references
     */
    List<BookingWithGuestComments> fetchPrepaidBDCBookingsWithOutstandingBalance();

    /**
     * Fetch any allocations matching any entries in the given blacklist.
     *
     * @param allocationScraperJobId PK of allocation job
     * @param blacklistEntries non-null list of blacklist entries to match
     * @return non-null list of matching Allocation
     */
    List<Allocation> fetchBookingsMatchingBlacklist( int allocationScraperJobId, List<BlacklistEntry> blacklistEntries );

    /**
     * Retrieves all Agoda bookings that don't have a no charge note in either the user comments nor
     * notes sections.
     * 
     * @return non-null list
     */
    List<BookingWithGuestComments> fetchAgodaBookingsMissingNoChargeNote();

    /**
     * Returns a single guest comment by reservation ID.
     * 
     * @param reservationId ID of reservation
     * @return non-null guest comment report entry
     * @throws NoResultException if no results found
     */
    GuestCommentReportEntry fetchGuestComments( int reservationId ) throws NoResultException;

    /**
     * Creates a report with all bookings with more than 5 guests.
     * 
     * @param allocationScraperJobId job ID of the allocation scraper job to use data from
     */
    void runGroupBookingsReport( int allocationScraperJobId );

    /**
     * Updates the bed count report table for the given job ID.
     * @param bedCountJobId job ID of the bed count job to use data from
     * @param selectionDate the report date in which to run for
     */
    void runBedCountsReport( int bedCountJobId, LocalDate selectionDate );

    /**
     * Returns the list of HW/HB reservation IDs for which the deposit amount has not yet been
     * deducted from the total.
     * 
     * @param allocationScraperJobId job ID of the allocation scraper job to use data from
     * @return non-null list of reservations
     */
    List<BookingByCheckinDate> getHostelworldHostelBookersUnpaidDepositReservations( int allocationScraperJobId );

    /**
     * Returns the checkin dates for all allocations found by the given AllocationScraper job ID.
     * 
     * @param jobId allocation scraper job ID
     * @return list of checkin dates
     */
    List<Date> getCheckinDatesForAllocationScraperJobId( int jobId );

    /**
     * Inserts a new HW booking.
     * 
     * @param booking booking to save
     */
    void insertHostelworldBooking( HostelworldBooking booking );

    /**
     * Deletes all HW bookings matching the given arrival date.
     * 
     * @param checkinDate date of arrival
     */
    void deleteHostelworldBookingsWithArrivalDate( Date checkinDate );

    /**
     * Deletes all HW bookings matching the given booked date.
     * 
     * @param bookedDate date of booking
     */
    void deleteHostelworldBookingsWithBookedDate( Date bookedDate );

    /**
     * Deletes all HB bookings matching the given arrival date.
     * 
     * @param checkinDate date of arrival
     */
    void deleteHostelbookersBookingsWithArrivalDate( Date checkinDate );

    /**
     * Returns the room type (id) for the given hostelworld room type label.
     * 
     * @param roomTypeLabel e.g. 12 Bed Female Dorm
     * @return room type ID (or null if cannot be determined)
     */
    Integer getRoomTypeIdForHostelworldLabel( String roomTypeLabel );

    /**
     * Returns all room type IDs available.
     * 
     * @return non-null list of room types
     */
    List<Integer> getAllRoomTypeIds();

    /**
     * Returns a map of all RoomBeds.
     * 
     * @return non-null map
     */
    Map<RoomBedLookup, RoomBed> fetchAllRoomBeds();

    /**
     * Returns the ID of the last completed allocation scraper job.
     * 
     * @return job ID or null if none exist
     */
    Integer getLastCompletedAllocationScraperJobId();

    /**
     * Inserts/updates the table for guest comments with a block of guest comments.
     * {@code reservationId} and {@code comments} must be populated.
     * 
     * @param comments the guest comment (if applicable) for the reservation
     */
    void updateGuestCommentsForReservations( List<GuestCommentReportEntry> comments );

    /**
     * Returns the wordpress option for the given property.
     * 
     * @param property name of key to get
     * @return option value or null if key doesn't exist
     */
    String getOption( String property );

    /**
     * Retrieves the default option based on the given property and default value.
     *
     * @param property the property key to search for the option
     * @param defaultValue the value to return if the property is not found
     * @return the option associated with the property key or the provided default value if the property is unavailable
     */
    String getDefaultOption(String property, String defaultValue);

    /**
     * Returns the wordpress option for the given property reading from the DB.
     *
     * @param property name of key to get
     * @return option value or null if key doesn't exist
     */
    String getOptionNoCache( String property );

    /**
     * Returns the Cloudbeds CSRF token from the current session.
     * 
     * @return CSRF token
     */
    String getCsrfToken();

    /**
     * Returns the wordpress option for the given property.
     * 
     * @param property name of key to get
     * @return non-null option value
     * @throws MissingUserDataException if property doesn't exist or is null
     */
    String getMandatoryOption( String property ) throws MissingUserDataException;

    /**
     * Returns the minimum number of people considered a group.
     *  
     * @return group booking size
     */
    int getGroupBookingSize();

    /**
     * Returns our 2Captcha API key.
     * 
     * @return API key (null if not found)
     */
    String get2CaptchaApiKey();

    /**
     * Returns true iff to send emails via Cloudbeds (rather than Gmail).
     * 
     * @return true to send email in cloudbeds, false otherwise.
     */
    boolean isCloudbedsEmailEnabled();

    /**
     * Sets the wordpress option for the given property.
     * 
     * @param property name of key to get
     * @param value value to set
     */
    void setOption( String property, String value );
    
    /**
     * Sets the auth details on a stripe transaction.
     * 
     * @param id unique PK on wp_stripe_transaction
     * @param paymentStatus status on paymentIntent
     * @param authStatus outcome type
     * @param authStatusDetail outcome seller message
     * @param chargeId charge identifier
     * @param cardType e.g. amex, mastercard, visa
     * @param last4Digits last 4 digits of card used as payment
     */
    void updateStripeTransaction( int id, String paymentStatus, String authStatus, String authStatusDetail, String chargeId, String cardType, String last4Digits );

    /**
     * Reads a record from the stripe transaction table.
     * @param vendorTxCode primary key
     */
    StripeTransaction fetchStripeTransaction( String vendorTxCode );

    /**
     * Reads a record from the stripe refund table.
     * 
     * @param id primary key on stripe refund table
     * @return refund object to persist
     */
    StripeRefund fetchStripeRefund( int id );

    /**
     * Returns all stripe refunds at a particular status.
     * 
     * @return non-null list of refunds
     */
    List<StripeRefund> fetchStripeRefundsAtStatus( String status );

    /**
     * Updates the stripe refund table after attempting a refund.
     * 
     * @param id primary key on stripe refund table
     * @param chargeId stripe charge id being refunded
     * @param response stripe refund response (JSON)
     * @param status stripe status of refund
     */
    void updateStripeRefund( int id, String chargeId, String response, String status );

    /**
     * Inserts a record into the wp_booking_lookup_key table.
     * 
     * @param reservationId the unique cloudbeds reference
     * @param key the lookup key
     * @param paymentRequested (optional) payment amount
     */
    void insertBookingLookupKey( String reservationId, String key, BigDecimal paymentRequested );

    /**
     * Checks whether we've already sent an email (or are ready to send) an email to the
     * given address.
     * @param email address to send to
     * @return true if entry already exists, false otherwise
     */
    boolean doesSendEmailEntryExist( String email );
    
    /**
     * Creates the send email record specified.
     * 
     * @param record email record to save
     */
    void saveSendEmailEntry( SendEmailEntry record );

    /**
     * Deletes all send email records by email address.
     * 
     * @param emailAddress matched email address
     */
    void deleteSendEmailEntry( String emailAddress );

    /**
     * Returns all emails that haven't been sent yet.
     * @return list of unsent email entries
     */
    List<SendEmailEntry> fetchAllUnsentEmails();

    /**
     * Returns the email subject when sending emails to guests who have checked-out.
     * 
     * @return non-null email subject
     */
    String getGuestCheckoutEmailSubject();

    /**
     * Returns the email template when sending emails to guests who have checked-out.
     * 
     * @return non-null email template
     */
    String getGuestCheckoutEmailTemplate();

    /**
     * Returns the base URL for the payment portal.
     *
     * @return non-null URL
     */
    String getBookingPaymentsURL();

    /**
     * Returns the base URL for the bookings portal.
     * 
     * @return non-null URL
     */
    String getBookingsURL();
}
