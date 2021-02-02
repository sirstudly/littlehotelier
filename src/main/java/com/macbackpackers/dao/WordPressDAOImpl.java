
package com.macbackpackers.dao;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.persistence.EntityManager;
import javax.persistence.NoResultException;
import javax.persistence.PersistenceContext;
import javax.persistence.Query;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.text.StringEscapeUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.dao.IncorrectResultSizeDataAccessException;
import org.springframework.jdbc.IncorrectResultSetColumnCountException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.macbackpackers.beans.Allocation;
import com.macbackpackers.beans.AllocationList;
import com.macbackpackers.beans.BookingByCheckinDate;
import com.macbackpackers.beans.BookingReport;
import com.macbackpackers.beans.BookingWithGuestComments;
import com.macbackpackers.beans.GuestCommentReportEntry;
import com.macbackpackers.beans.HostelworldBooking;
import com.macbackpackers.beans.Job;
import com.macbackpackers.beans.JobScheduler;
import com.macbackpackers.beans.JobStatus;
import com.macbackpackers.beans.PxPostTransaction;
import com.macbackpackers.beans.RoomBed;
import com.macbackpackers.beans.RoomBedLookup;
import com.macbackpackers.beans.SagepayRefund;
import com.macbackpackers.beans.SagepayTransaction;
import com.macbackpackers.beans.ScheduledJob;
import com.macbackpackers.beans.SendEmailEntry;
import com.macbackpackers.beans.StripeRefund;
import com.macbackpackers.beans.StripeTransaction;
import com.macbackpackers.beans.UnpaidDepositReportEntry;
import com.macbackpackers.exceptions.IncorrectNumberOfRecordsUpdatedException;
import com.macbackpackers.exceptions.MissingUserDataException;
import com.macbackpackers.jobs.AbstractJob;
import com.macbackpackers.jobs.AllocationScraperJob;
import com.macbackpackers.jobs.ResetCloudbedsSessionJob;

@Repository
@Transactional
public class WordPressDAOImpl implements WordPressDAO {

    private final Logger LOGGER = LoggerFactory.getLogger( getClass() );

    @PersistenceContext
    private EntityManager em;
    
    @Autowired
    @Qualifier( "reportsSQL" )
    private Properties sql;

    @Value( "${hostelworld.hostelnumber}" )
    private String hostelNumber;

    @Value( "${processor.id}" )
    private String processorId;

    @Value( "${wordpress.db.prefix}" )
    private String wordpressPrefix;

    @Override
    public boolean isCloudbeds() {
        return "cloudbeds".equalsIgnoreCase( getOption( "hbo_property_manager" ) );
    }

    @Override
    public boolean isLittleHotelier() {
        return false == isCloudbeds();
    }

    @Override
    public void insertAllocation( Allocation alloc ) {
        em.persist( alloc );
    }

    @Override
    public void insertAllocations( AllocationList allocations ) {
        if ( allocations.size() > 0 ) {
            Query q = em.createNativeQuery( allocations.getBulkInsertStatement() );
            for ( int i = 0 ; i < allocations.size() ; i++ ) {
                Allocation a = allocations.get( i );
                Object params[] = a.getAsParameters();
                for ( int j = 0 ; j < params.length ; j++ ) {
                    q.setParameter( i * params.length + j + 1, params[j] );
                }
            }
            int rowsInserted = q.executeUpdate();
            LOGGER.info( rowsInserted + " allocation rows inserted." );
        }
        else {
            LOGGER.info( "Nothing to update." );
        }
    }

    @Override
    public void insertBookingReport( List<BookingReport> bookingReport ) {
        if ( bookingReport.size() > 0 ) {
            Query q = em.createNativeQuery( getBulkInsertBookingReportStatement( bookingReport ) );
            for ( int i = 0 ; i < bookingReport.size() ; i++ ) {
                BookingReport a = bookingReport.get( i );
                Object params[] = a.getAsParameters();
                for ( int j = 0 ; j < params.length ; j++ ) {
                    q.setParameter( i * params.length + j + 1, params[j] );
                }
            }
            int rowsInserted = q.executeUpdate();
            LOGGER.info( rowsInserted + " booking report rows inserted." );
        }
        else {
            LOGGER.info( "Nothing to insert." );
        }
    }
    
    @Override
    public void deleteBookingReport( int jobId ) {
        int rowsDeleted = em
            .createQuery( "DELETE BookingReport WHERE jobId = :jobId" )
            .setParameter( "jobId", jobId )
            .executeUpdate();
        LOGGER.info( rowsDeleted + " booking report rows deleted." );
    }

    /**
     * Returns bulk insert statement.
     * 
     * @param bookingReport booking report entries to insert
     * @return SQL statement
     */
    private String getBulkInsertBookingReportStatement( List<BookingReport> bookingReport ) {
        if ( bookingReport.isEmpty() ) {
            throw new IllegalStateException( "Nothing to insert!" );
        }
        String columnNames = BookingReport.getColumnNames();
        int paramCount = StringUtils.countMatches( columnNames, ',' ) + 1;
        return "INSERT INTO " + BookingReport.getTableName() + "(" + columnNames + ") VALUES " +
                StringUtils.repeat(
                        "(" + StringUtils.repeat( "?", ",", paramCount ) + ")", ",", bookingReport.size() );
    }

    @Override
    public Allocation fetchAllocation( int id ) {
        Allocation alloc = em.find( Allocation.class, id );
        if ( alloc == null ) {
            throw new EmptyResultDataAccessException( 1 );
        }
        return alloc;
    }

    @Override
    public List<String> fetchDistinctBookingsByCheckinDate( int allocationScraperJobId, Date checkinDate ) {
        return em.createQuery( "SELECT DISTINCT bookingReference FROM Allocation "
                + "WHERE jobId = :jobId AND reservationId > 0 "
                + "AND checkinDate = :checkinDate", String.class )
                .setParameter( "jobId", allocationScraperJobId )
                .setParameter( "checkinDate", checkinDate )
                .getResultList();
    }

    @Override
    public void updateAllocation( Allocation alloc ) {
        alloc.setCreatedDate( new Timestamp( System.currentTimeMillis() ) );
        em.merge( alloc );
    }

    @Override
    public void updateAllocationList( AllocationList allocList ) {
        allocList.setCreatedDate( new Timestamp( System.currentTimeMillis() ) );
        for ( Allocation a : allocList ) {
            em.merge( a );
        }
    }

    @Override
    public void deleteAllocations( int jobId ) {
        int rowsDeleted = em
            .createQuery( "DELETE Allocation WHERE jobId = :jobId" )
            .setParameter( "jobId", jobId )
            .executeUpdate();
        LOGGER.info( rowsDeleted + " allocation rows deleted." );
    }

    @Override
    public void deleteCancelledAllocations( int jobId, Date checkinDateStart, Date checkinDateEnd ) {
        int rowsDeleted = em
            .createQuery( "DELETE Allocation "
                    + "     WHERE jobId = :jobId "
                    + "       AND status = 'cancelled' "
                    + "       AND checkinDate >= :checkinDateStart "
                    + "       AND checkinDate <= :checkinDateEnd" )
            .setParameter( "jobId", jobId )
            .setParameter( "checkinDateStart", checkinDateStart )
            .setParameter( "checkinDateEnd", checkinDateEnd )
            .executeUpdate();
        LOGGER.info( rowsDeleted + " allocation rows deleted." );
    }

    @Override
    public void updateAllocationJobId( int oldAllocationJobId, int newAllocationJobId ) {
        int rowsUpdated = em
                .createQuery( "UPDATE Allocation SET jobId = :newJobId WHERE jobId = :oldJobId" )
                .setParameter( "oldJobId", oldAllocationJobId )
                .setParameter( "newJobId", newAllocationJobId )
                .executeUpdate();
            LOGGER.info( rowsUpdated + " allocation rows updated." );
    }

    @Override
    public AllocationList queryAllocationsByJobIdAndReservationId( int jobId, int reservationId ) {
        return new AllocationList( em
                .createQuery( "FROM Allocation WHERE jobId = :jobId AND reservationId = :reservationId", Allocation.class )
                .setParameter( "jobId", jobId )
                .setParameter( "reservationId", reservationId )
                .getResultList() );
    }

    @Override
    public int insertJob( Job job ) {
        em.persist( job );
        return job.getId();
    }

    @Override
    public long getOutstandingJobCount() {
        return em.createQuery( "SELECT COUNT(1) FROM AbstractJob "
                + "     WHERE status IN (:submittedStatus, :processingStatus)", Long.class )
                .setParameter( "submittedStatus", JobStatus.submitted )
                .setParameter( "processingStatus", JobStatus.processing )
                .getSingleResult();
    }

    @Override
    public void updateJobStatus( int jobId, JobStatus status, JobStatus prevStatus ) {
        AbstractJob job = fetchJobById( jobId );
        if ( prevStatus != job.getStatus() ) {
            throw new IncorrectNumberOfRecordsUpdatedException(
                    "Previous job " + jobId + " status is " + job.getStatus() + " when attempting to set to " + status );
        }
        updateJobStatus( job, status );
    }

    @Override
    @Deprecated
    public boolean updateJobStatusToProcessing( int jobId ) {
        // there should only be 1 row updated
        // if not, another processor may have taken it so return false
        return 1 == em.createQuery(
                "UPDATE AbstractJob "
                        + "   SET status = :processing, "
                        + "       jobStartDate = :now,"
                        + "       jobEndDate = NULL,"
                        + "       processedBy = :processedBy,"
                        + "       lastUpdatedDate = :now"
                        + " WHERE id = :jobId"
                        + "   AND status = :submitted"
                        + "    OR ( status = :processing AND processedBy = :processedBy)" )
                .setParameter( "jobId", jobId )
                .setParameter( "submitted", JobStatus.submitted )
                .setParameter( "processing", JobStatus.processing )
                .setParameter( "processedBy", getUniqueProcessorId() )
                .setParameter( "now", new Timestamp( System.currentTimeMillis() ) )
                .executeUpdate();
    }

    @Override
    public void updateJobStatus( int jobId, JobStatus status ) {
        updateJobStatus( fetchJobById( jobId ), status );
    }

    private void updateJobStatus( Job job, JobStatus status ) {
        if ( status == JobStatus.processing ) {
            throw new UnsupportedOperationException( "Use updateJobStatusToProcessing()" );
        }

        if ( status == JobStatus.completed ) {
            job.setJobEndDate( new Timestamp( System.currentTimeMillis() ) );
        }

        job.setStatus( status );
        job.setLastUpdatedDate( new Timestamp( System.currentTimeMillis() ) );
    }

    @Override
    public void resetAllProcessingJobsToFailed() {
        em.createQuery(
                "UPDATE AbstractJob "
                        + "   SET status = :failed, "
                        + "       lastUpdatedDate = :now"
                        + " WHERE status = :processing" )
                .setParameter( "failed", JobStatus.failed )
                .setParameter( "processing", JobStatus.processing )
                .setParameter( "now", new Timestamp( System.currentTimeMillis() ) )
                .executeUpdate();
    }

    @Override
    public synchronized AbstractJob getNextJobToProcess() {
        // shutdown job has priority if present
        // include any jobs that have been tagged as processing by us
        // (since there should only ever be 1 unique one; we'll be re-running these jobs)
        String thisProcessorId = getUniqueProcessorId();
        LOGGER.info( "Getting next job for " + thisProcessorId );
        List<Integer> jobs = em
                .createQuery( "SELECT id FROM AbstractJob "
                        + "     WHERE status = :submittedStatus "
                        + "        OR (status = :processingStatus AND processedBy = :processedBy)"
                        + "     ORDER BY CASE WHEN classname = 'com.macbackpackers.jobs.ShutdownJob' THEN 0 ELSE 1 END, job_id",
                        Integer.class )
                .setParameter( "submittedStatus", JobStatus.submitted )
                .setParameter( "processingStatus", JobStatus.processing )
                // processedBy includes name of current thread
                // if we terminated the job prematurely (and are now re-running it)
                // this will eventually be picked up by the same thread and be run again
                .setParameter( "processedBy", thisProcessorId )
                .getResultList();
        
        forAllJobs: for ( int jobId : jobs ) {
            AbstractJob job = fetchJobById( jobId ); // fetch by primary key
            // first check that all dependent jobs have completed successfully
            for ( Job dependentJob : job.getDependentJobs() ) {
                switch ( dependentJob.getStatus() ) {
                    case submitted:
                    case processing:
                        continue forAllJobs; // need to wait until parent job finishes
                    case failed:
                    case aborted:
                        // parent job failed/aborted so we abort this job and continue looking...
                        job.setStatus( JobStatus.aborted );
                        continue forAllJobs;
                    default: // (completed)
                        // ok; just check remaining dependent jobs
                }
            }

            LOGGER.debug( "Attempting to lock job " + job.getId() );
            Timestamp now = new Timestamp( System.currentTimeMillis() );
            job.setStatus( JobStatus.processing );
            job.setJobStartDate( now );
            job.setJobEndDate( null );
            job.setProcessedBy( thisProcessorId );
            job.setLastUpdatedDate( now );
            return job; // all dependent jobs are completed; this is the one 
        }
        LOGGER.info( "No more jobs to process..." );
        return null; // we couldn't find a job to process
    }

    @Override
    public AbstractJob fetchJobById( int id ) {
        AbstractJob j = em.find( AbstractJob.class, id );
        if ( j == null ) {
            throw new EmptyResultDataAccessException( "Unable to find Job with ID " + id, 1 );
        }
        return j;
    }

    @Override
    public Optional<ResetCloudbedsSessionJob> fetchResetCloudbedsSessionJob() {
        List<Integer> jobs = em
                .createQuery( "SELECT id FROM AbstractJob "
                        + "     WHERE (status = :submittedStatus "
                        + "        OR (status = :processingStatus AND processedBy = :processedBy))"
                        + "       AND classname = 'com.macbackpackers.jobs.ResetCloudbedsSessionJob'"
                        + "     ORDER by id",
                        Integer.class )
                .setParameter( "submittedStatus", JobStatus.submitted )
                .setParameter( "processingStatus", JobStatus.processing )
                // processedBy includes name of current thread
                // if we terminated the job prematurely (and are now re-running it)
                // this will eventually be picked up by the same thread and be run again
                .setParameter( "processedBy", getUniqueProcessorId() )
                .getResultList();

        if( jobs.size() > 0 ) {
            ResetCloudbedsSessionJob j = em.find( ResetCloudbedsSessionJob.class, jobs.get( 0 ) );
            j.setStatus( JobStatus.processing );
            return Optional.of( j );
        }
        return Optional.empty();
    }

    @SuppressWarnings( "unchecked" )
    @Override
    public <T extends AbstractJob> T getLastJobOfType( Class<T> jobType ) {
        List<Integer> jobIds = em
                .createQuery( "SELECT MAX(id) FROM AbstractJob WHERE classname = :classname", Integer.class )
                .setParameter( "classname", jobType.getName() )
                .getResultList();
        Integer jobId = jobIds.isEmpty() ? null : jobIds.get( 0 );
        LOGGER.info( "Last " + jobType + ": " + (jobId == null ? "none" : jobId) );
        return jobId == null ? null : (T) fetchJobById( jobId );
    }

    @SuppressWarnings( "unchecked" )
    @Override
    public <T extends AbstractJob> T getLastCompletedJobOfType( Class<T> jobType ) {
        List<Integer> jobIds = em
                .createQuery( "SELECT MAX(id) FROM AbstractJob WHERE classname = :classname AND status = :status", Integer.class )
                .setParameter( "classname", jobType.getName() )
                .setParameter( "status", JobStatus.completed )
                .getResultList();
        Integer jobId = jobIds.isEmpty() ? null : jobIds.get( 0 );
        LOGGER.info( "Last completed " + jobType + ": " + (jobId == null ? "none" : jobId) );
        return jobId == null ? null : (T) fetchJobById( jobId );
    }

    @SuppressWarnings( "unchecked" )
    @Override
    public List<String> getReservationIdsForDepositChargeJobs( int jobIdStart, int jobIdEnd ) {
        return em.createNativeQuery(
                "SELECT DISTINCT p.value FROM wp_lh_jobs j " +
                        "  JOIN wp_lh_job_param p ON j.job_id = p.job_id " +
                        " WHERE j.classname = 'com.macbackpackers.jobs.DepositChargeJob'"
                        + " AND j.job_id BETWEEN :jobIdStart AND :jobIdEnd" )
                .setParameter( "jobIdStart", jobIdStart )
                .setParameter( "jobIdEnd", jobIdEnd )
                .getResultList();
    }

    @SuppressWarnings( "unchecked" )
    @Override
    public List<Date> getCheckinDatesForAllocationScraperJobId( int jobId ) {
        // dates from calendar for a given (allocation scraper) job id
        // do not include room closures
        return em.createQuery(
                "SELECT DISTINCT checkinDate"
                        + "     FROM Allocation "
                        + "    WHERE jobId = :jobId"
                        + "      AND reservationId > 0"
                        + "    ORDER BY checkinDate" )
                .setParameter( "jobId", jobId )
                .getResultList();
    }

    @Override
    public List<BookingByCheckinDate> getHostelworldHostelBookersUnpaidDepositReservations( int allocationScraperJobId ) {
        LOGGER.info( "Querying unpaid reservations for allocation job : " + allocationScraperJobId );
        return em.createQuery(
                "SELECT new com.macbackpackers.beans.BookingByCheckinDate(bookingReference, reservationId, checkinDate) " +
                        "  FROM Allocation " +
                        "WHERE jobId = :jobId " +
                        "  AND paymentTotal = paymentOutstanding " +
                        "  AND bookingSource IN ( 'Hostelworld', 'Hostelbookers', 'Hostelworld Group' ) " +
                        "GROUP BY reservationId", BookingByCheckinDate.class )
                .setParameter( "jobId", allocationScraperJobId )
                .getResultList();
    }

    @SuppressWarnings( "unchecked" )
    @Override
    public List<ScheduledJob> fetchActiveScheduledJobs() {
        return em.createQuery(
                "FROM ScheduledJob WHERE active = true" )
                .getResultList();
    }

    @Override
    public ScheduledJob fetchScheduledJobById( int jobId ) {
        ScheduledJob j = em.find( ScheduledJob.class, jobId );
        if ( j == null ) {
            throw new EmptyResultDataAccessException( 1 );
        }
        return j;
    }

    @Override
    public void updateScheduledJob( int jobId ) {
        ScheduledJob job = em.find( ScheduledJob.class, jobId );
        if ( job == null ) {
            throw new EmptyResultDataAccessException( 1 );
        }
        job.setLastScheduledDate( new Timestamp( System.currentTimeMillis() ) );
    }

    @Override
    public List<JobScheduler> fetchActiveJobSchedules() {
        return em.createQuery(
                "FROM JobScheduler WHERE active = true", JobScheduler.class )
                .getResultList();
    }

    @Override
    public void updateJobScheduler( JobScheduler schedule ) {
        em.merge( schedule );
    }
    
    @Override
    public boolean isJobCurrentlyPending( String classname ) {
        return em.createQuery( "SELECT COUNT(1) FROM AbstractJob "
                + "     WHERE classname = :classname "
                + "     AND status IN (:submittedStatus, :processingStatus)",
                Long.class )
                .setParameter( "classname", classname )
                .setParameter( "submittedStatus", JobStatus.submitted )
                .setParameter( "processingStatus", JobStatus.processing )
                .getSingleResult() > 0;
    }

    @Override
    public void purgeRecordsOlderThan( Date specifiedDate ) {

        // delete from associated tables
        deleteFromTablesByJobId( specifiedDate,
                "wp_lh_rpt_split_rooms",
                "wp_lh_rpt_unpaid_deposit",
                "wp_lh_group_bookings",
                "wp_lh_calendar" );

        // now delete from jobs
        deleteFromTablesByJobId( specifiedDate, "wp_lh_job_param" );
        deleteFromTablesByJobId( specifiedDate, "wp_lh_job_dependency" );

        int rowsDeleted = em
                .createNativeQuery( "DELETE FROM wp_lh_jobs WHERE last_updated_date < :specifiedDate" )
                .setParameter( "specifiedDate", specifiedDate )
                .executeUpdate();
        LOGGER.info( "Purge Job: deleted " + rowsDeleted + " records from wp_lh_jobs" );

// need to keep old records from migration
//        rowsDeleted = em
//                // doesn't take into the date; just deletes all guest comments that
//                // are no longer present when running the allocation scraper job
//                .createNativeQuery( 
//                        "  DELETE m FROM wp_lh_rpt_guest_comments m "
//                        + "  LEFT OUTER JOIN wp_lh_calendar c "
//                        + "    ON m.reservation_id = c.reservation_id "
//                        // match up with the allocation scraper job that completed last by
//                        // finding the last completed SplitRoomReservationReportJob
//                        + "   AND c.job_id = (SELECT CAST(p.value AS UNSIGNED) FROM wp_lh_job_param p "
//                        + "                    WHERE p.job_id IN (SELECT MAX(j.job_id) FROM wp_lh_jobs j "
//                        + "                                        WHERE j.classname = 'com.macbackpackers.jobs.SplitRoomReservationReportJob' "
//                        + "                                          AND j.status = 'completed') "
//                        + "                    AND p.name = 'allocation_scraper_job_id') " +
//                        " WHERE c.reservation_id IS NULL" )
//                .executeUpdate();
//        LOGGER.info( "Purge Job: deleted " + rowsDeleted + " records from wp_lh_rpt_guest_comments" );
    }

    /**
     * Deletes all records older than the given date from the specified tables.
     * 
     * @param specifiedDate records to be deleted older than this date
     * @param tables name of tables to be deleted
     */
    private void deleteFromTablesByJobId( Date specifiedDate, String ... tables ) {
        final String JOB_ID_SELECT = "SELECT job_id FROM wp_lh_jobs WHERE last_updated_date < :specifiedDate";

        for ( String table : tables ) {
            int rowsDeleted = em
                    .createNativeQuery( "DELETE FROM " + table + " WHERE job_id IN ( " + JOB_ID_SELECT + ")" )
                    .setParameter( "specifiedDate", specifiedDate )
                    .executeUpdate();
            LOGGER.info( "Purge Job: deleted " + rowsDeleted + " records from " + table );
        }
    }

    @Override
    public void insertHostelworldBooking( HostelworldBooking booking ) {
        em.persist( booking );
    }
    
    @Override
    @SuppressWarnings( "unchecked" )
    public List<String> findMissingHwBookingRefs( int jobId, Date checkinDate ) {
        return em.createNativeQuery(
                "           SELECT CONCAT('HWL-" + hostelNumber + "-', b.booking_reference) "
                        + "   FROM "
                        + "       (SELECT * FROM wp_hw_booking b "
                        + "         WHERE (SELECT MIN(d.booked_date) "
                        + "                  FROM wp_hw_booking_dates d "
                        + "                 WHERE b.id = d.hw_booking_id) = :checkinDate ) b "
                        + "   LEFT OUTER JOIN "
                        + "       (SELECT guest_name, booking_reference "
                        + "          FROM wp_lh_calendar "
                        + "         WHERE booking_source = 'Hostelworld Group' "
                        + "           AND job_id = :jobId "
                        + "         GROUP BY guest_name, booking_reference ) c "
                        + "     ON CONCAT('HWL-" + hostelNumber + "-', b.booking_reference) = c.booking_reference "
                        + "  WHERE c.booking_reference IS NULL" )
                .setParameter( "checkinDate", checkinDate )
                .setParameter( "jobId", jobId )
                .getResultList();
    }

    @Override
    @SuppressWarnings( "unchecked" )
    public void deleteHostelworldBookingsWithArrivalDate( Date checkinDate ) {

        // find all bookings where the first (booked) date matches the checkin date
        List<Integer> bookingIds = em.createQuery(
                "           SELECT d.bookingId "
                        + "   FROM HostelworldBookingDate d"
                        + "  WHERE EXISTS( "
                        + "          SELECT 1 FROM HostelworldBooking w "
                        + "           WHERE w.id = d.bookingId )"
                        + "  GROUP BY d.bookingId"
                        + " HAVING MIN( d.bookedDate ) = :bookedDate" )
                .setParameter( "bookedDate", checkinDate )
                .getResultList();

        deleteHostelworldBookings( bookingIds );
    }

    @Override
    @SuppressWarnings( "unchecked" )
    public void deleteHostelworldBookingsWithBookedDate( Date bookedDate ) {

        // find all bookings matched by booked date
        // we just need to compare the date portion
        Calendar c = Calendar.getInstance();
        c.setTime( bookedDate );
        c.add( Calendar.DATE, 1 );
        List<Integer> bookingIds = em.createQuery(
                "           SELECT b.id "
                        + "   FROM HostelworldBooking b"
                        + "  WHERE DATE(:bookedDate) <= b.bookedDate"
                        + "    AND DATE(:bookedDatePlus1) > b.bookedDate")
                .setParameter( "bookedDate", bookedDate )
                .setParameter( "bookedDatePlus1", c.getTime() )
                .getResultList();

        deleteHostelworldBookings( bookingIds );
    }

    @Override
    @SuppressWarnings( "unchecked" )
    public void deleteHostelbookersBookingsWithArrivalDate( Date checkinDate ) {

        // find all bookings where the first (booked) date matches the checkin date
        List<Integer> bookingIds = em.createQuery(
                "           SELECT d.bookingId "
                        + "   FROM HostelworldBookingDate d "
                        + "  WHERE EXISTS( "
                        + "          SELECT 1 FROM HostelworldBooking w "
                        + "           WHERE w.id = d.bookingId "
                        + "             AND w.bookingSource = 'Hostelbookers' )"
                        + "  GROUP BY d.bookingId"
                        + " HAVING MIN( d.bookedDate ) = :bookedDate" )
                .setParameter( "bookedDate", checkinDate )
                .getResultList();

        deleteHostelworldBookings( bookingIds );
    }

    /**
     * Deletes all HW/HB bookings with the given primary keys.
     * 
     * @param bookingIds list of primary keys
     */
    private void deleteHostelworldBookings( List<Integer> bookingIds ) {

        // now delete the records one by one since i couldn't figure out
        // how to do a cascade delete correctly
        LOGGER.info( "Deleting " + bookingIds.size() + " records from HW bookings" );
        for ( Integer bookingId : bookingIds ) {
            em.createQuery(
                    "DELETE HostelworldBookingDate WHERE bookingId = :bookingId" )
                    .setParameter( "bookingId", bookingId )
                    .executeUpdate();
            em.createQuery(
                    "DELETE HostelworldBooking WHERE id = :bookingId" )
                    .setParameter( "bookingId", bookingId )
                    .executeUpdate();
        }
    }

    @Override
    public Integer getRoomTypeIdForHostelworldLabel( String roomTypeLabel ) {

        String roomType;
        int capacity = 0;

        Pattern p = Pattern.compile( "([\\d]{1,2}) Bed" );
        Matcher m = p.matcher( roomTypeLabel );
        if ( m.find() ) {
            capacity = Integer.parseInt( m.group( 1 ) );
        }

        if ( roomTypeLabel.contains( "Mixed" ) ) {
            roomType = "MX";
        }
        else if ( roomTypeLabel.contains( "Female" ) ) {
            roomType = "F";
        }
        else if ( roomTypeLabel.contains( "Male" ) ) {
            roomType = "M";
        }
        else if ( roomTypeLabel.contains( "Double" ) ) {
            roomType = "DBL";
            capacity = 2;
        }
        else if ( roomTypeLabel.contains( "3 Bed Private" ) || roomTypeLabel.contains( "Triple" ) ) {
            roomType = "TRIPLE";
            capacity = 3;
        }
        else if ( roomTypeLabel.contains( "4 Bed Private" ) || roomTypeLabel.contains( "4 person" ) ) {
            roomType = "QUAD";
            capacity = 4;
        }
        else {
            LOGGER.error( "Unsupported room type, unable to determine type: " + roomTypeLabel );
            return null;
        }

        if ( capacity == 0 ) {
            LOGGER.error( "Unsupported room type, unable to determine capacity: " + roomTypeLabel );
            return null;
        }

        @SuppressWarnings( "unchecked" )
        List<Integer> roomTypeIds = em
                .createNativeQuery( "SELECT DISTINCT room_type_id "
                        + "  FROM wp_lh_rooms "
                        + " WHERE room_type = :roomType "
                        + "   AND capacity = :capacity" )
                .setParameter( "roomType", roomType )
                .setParameter( "capacity", capacity )
                .getResultList();

        if ( roomTypeIds.isEmpty() ) {
            LOGGER.error( "Unable to determine room type id for " + roomTypeLabel, 1 );
            return null;
        }

        // this is the only error we will generate; if there is some invalid reference data in the table
        if ( roomTypeIds.size() > 1 ) {
            throw new IncorrectResultSetColumnCountException( "Unable to determine room type id for " + roomTypeLabel, 1, roomTypeIds.size() );
        }
        return roomTypeIds.get( 0 );
    }

    @Override
    @SuppressWarnings( "unchecked" )
    public List<Integer> getAllRoomTypeIds() {
        return em.createNativeQuery( "SELECT DISTINCT room_type_id FROM wp_lh_rooms" ).getResultList();
    }

    @Override
    public Map<RoomBedLookup, RoomBed> fetchAllRoomBeds() {
        List<RoomBed> roomBeds = em.createQuery( "FROM RoomBed WHERE room != 'Unallocated'", RoomBed.class )
                .getResultList();

        // key/value will be the same
        // lookup will be done using RoomBed.equals()
        HashMap<RoomBedLookup, RoomBed> roomBedMap = new HashMap<>();
        roomBeds.stream()
                .forEach( rb -> {
                    rb.setBedName( StringEscapeUtils.unescapeHtml4( rb.getBedName() ) );
                    roomBedMap.put( new RoomBedLookup( rb.getRoom(), rb.getBedName() ), rb );
                });
        return roomBedMap;
    }

    /////////////////////////////////////////////////////////////////////
    //    REPORTING SPECIFIC
    /////////////////////////////////////////////////////////////////////

    @Override
    public void runSplitRoomsReservationsReport( int allocationScraperJobId ) {
        LOGGER.info( "Running report for job id: " + allocationScraperJobId );

        // first remove any previous data in case we're running this again
        int rowsDeleted = em
                .createNativeQuery( "DELETE FROM wp_lh_rpt_split_rooms WHERE job_id = :jobId" )
                .setParameter( "jobId", allocationScraperJobId )
                .executeUpdate();
        LOGGER.info( "Deleted " + rowsDeleted + " previous records from wp_lh_rpt_split_rooms" );

        em.createNativeQuery( sql.getProperty( "reservations.split.rooms" ) )
            .setParameter( "jobId", allocationScraperJobId )
            .executeUpdate();
    }

    @Override
    public void runUnpaidDepositReport( int allocationScraperJobId ) {
        LOGGER.info( "Running report for job id: " + allocationScraperJobId );

        // first remove any previous data in case we're running this again
        int rowsDeleted = em
                .createNativeQuery( "DELETE FROM wp_lh_rpt_unpaid_deposit WHERE job_id = :jobId" )
                .setParameter( "jobId", allocationScraperJobId )
                .executeUpdate();
        LOGGER.info( "Deleted " + rowsDeleted + " previous records from wp_lh_rpt_unpaid_deposit" );

        em.createNativeQuery( sql.getProperty( "unpaid.deposit.report" ) )
            .setParameter( "jobId", allocationScraperJobId )
            .executeUpdate();
    }

    @Override
    public List<UnpaidDepositReportEntry> fetchUnpaidDepositReport( int allocationScraperJobId ) {
        LOGGER.info( "Fetching last unpaid deposit report for allocation job id " + allocationScraperJobId );
        return em.createQuery(
                "FROM UnpaidDepositReportEntry WHERE jobId = :jobId", UnpaidDepositReportEntry.class )
                .setParameter( "jobId", allocationScraperJobId )
                .getResultList();
    }
    
    @Override
    public List<BookingWithGuestComments> fetchPrepaidBDCBookingsWithOutstandingBalance() {
        Integer allocationScraperJobId = getLastCompletedAllocationScraperJobId();
        if ( allocationScraperJobId != null ) {
            return em.createQuery( 
                    "  SELECT DISTINCT new com.macbackpackers.beans.BookingWithGuestComments( "
                    + "           c.reservationId, c.bookingReference, c.checkinDate, c.bookedDate, "
                    // consolidate notes/comments into the comments field for efficiency
                    + "           CONCAT( COALESCE( c.notes, '' ), COALESCE( r.comments, '' ) ) ) "
                    + "  FROM Allocation c "
                    + "  LEFT OUTER JOIN GuestCommentReportEntry r "
                    + "    ON c.reservationId = r.reservationId "
                    + " WHERE c.jobId = :allocationScraperJobId "
                    + "   AND c.paymentOutstanding > 0"
                    + "   AND (r.comments LIKE '%You have received a virtual credit card for this reservation%' "
                    + "     OR c.notes LIKE '%You have received a virtual credit card for this reservation%' "
                    + "     OR r.comments LIKE '%THIS RESERVATION HAS BEEN PRE-PAID%' "
                    + "     OR c.notes LIKE '%THIS RESERVATION HAS BEEN PRE-PAID%') "
                    + "   AND c.bookingSource = 'Booking.com'", BookingWithGuestComments.class )
                    .setParameter( "allocationScraperJobId", allocationScraperJobId )
                    .getResultList();
        }
        return Collections.emptyList();
    }

    @Override
    public List<BookingWithGuestComments> fetchAgodaBookingsMissingNoChargeNote() {
        Integer allocationScraperJobId = getLastCompletedAllocationScraperJobId();
        if ( allocationScraperJobId != null ) {
            return em.createQuery(
                    "  SELECT DISTINCT new com.macbackpackers.beans.BookingWithGuestComments( c.reservationId, c.bookingReference, c.checkinDate, c.bookedDate, r.comments ) "
                            + "  FROM Allocation c "
                            + " INNER JOIN GuestCommentReportEntry r "
                            + "    ON c.reservationId = r.reservationId "
                            + " WHERE c.jobId = :allocationScraperJobId "
                            + "   AND (IFNULL(r.comments, '') NOT LIKE '%- RONBOT%' "
                                    + "OR IFNULL(c.notes, '') NOT LIKE '%- RONBOT%')"
                            + "   AND c.bookingSource = 'Agoda'"
                            + "   AND c.status = 'confirmed'",
                    BookingWithGuestComments.class )
                    .setParameter( "allocationScraperJobId", allocationScraperJobId )
                    .getResultList();
        }
        return Collections.emptyList();
    }

    @Override
    public GuestCommentReportEntry fetchGuestComments( int reservationId ) throws NoResultException {
        return em.createQuery( "FROM GuestCommentReportEntry WHERE reservationId = :reservationId", GuestCommentReportEntry.class )
                .setParameter( "reservationId", reservationId )
                .getSingleResult();
    }

    @Override
    public void runGroupBookingsReport( int allocationScraperJobId ) {
        LOGGER.info( "Running report for job id: " + allocationScraperJobId );

        // first remove any previous data in case we're running this again
        int rowsDeleted = em
                .createNativeQuery( "DELETE FROM wp_lh_group_bookings WHERE job_id = :jobId" )
                .setParameter( "jobId", allocationScraperJobId )
                .executeUpdate();
        LOGGER.info( "Deleted " + rowsDeleted + " previous records from wp_lh_group_bookings" );

        em.createNativeQuery( sql.getProperty( "group.bookings" ) )
            .setParameter( "jobId", allocationScraperJobId )
            .setParameter( "groupSize", getGroupBookingSize() )
            .setParameter( "propertyManager", StringUtils.defaultIfBlank( getOption( "hbo_property_manager" ), "n/a" ) )
            .executeUpdate();
    }

    @Override
    @SuppressWarnings( "unchecked" )
    public Integer getLastCompletedAllocationScraperJobId() {
        List<Integer> results = em
                .createQuery( "SELECT id FROM AllocationScraperJob "
                        + "     WHERE jobEndDate IN ("
                        + "         SELECT MAX( jobEndDate ) FROM AllocationScraperJob "
                        + "          WHERE classname = :classname AND status = :status )" )
                .setParameter( "classname", AllocationScraperJob.class.getCanonicalName() )
                .setParameter( "status", JobStatus.completed )
                .getResultList();
        return results.isEmpty() ? null : results.get( 0 );
    }

    @Override
    public void updateGuestCommentsForReservations( List<GuestCommentReportEntry> comments ) {
        if ( comments.size() > 0 ) {
            Query q = em.createNativeQuery( "INSERT INTO wp_lh_rpt_guest_comments ( reservation_id, comments ) "
                    + " VALUES " + StringUtils.repeat( "( ?, ? )", ",", comments.size() )
                    + " ON DUPLICATE KEY UPDATE "
                    + " reservation_id = VALUES( reservation_id ), "
                    + " comments = VALUES( comments )" );
            for ( int i = 0 ; i < comments.size() ; i++ ) {
                GuestCommentReportEntry entry = comments.get( i );
                q.setParameter( 2 * i + 1, entry.getReservationId() );
                q.setParameter( 2 * i + 2, entry.getComments() );
            }
            q.executeUpdate();
            LOGGER.info( "Updated " + comments.size() + " guest comments." );
        }
    }

    @Override
    @SuppressWarnings( "unchecked" )
    @Transactional( readOnly = true )
    public String getOption( String property ) {
        List<String> sqlResult = em.createNativeQuery(
                "          SELECT option_value"
                        + "  FROM " + wordpressPrefix + "options"
                        + " WHERE option_name = :optionName " )
                .setParameter( "optionName", property )
                .getResultList();

        // key doesn't exist; just return null
        if ( sqlResult.isEmpty() ) {
            return null;
        }
        return sqlResult.get( 0 );
    }

    @Override
    public String getMandatoryOption( String property ) {
        String result = getOption( property );
        if ( result == null ) {
            throw new MissingUserDataException( "Missing property " + property );
        }
        return result;
    }

    @Override
    @Transactional( readOnly = true )
    public int getGroupBookingSize() {
        return Integer.parseInt( getOption( "hbo_group_booking_size" ) );
    }

    @Override
    @Transactional( readOnly = true )
    public String get2CaptchaApiKey() {
        return getOption( "hbo_2captcha_api_key" );
    }

    @Override
    @Transactional( readOnly = true )
    public boolean isCloudbedsEmailEnabled() {
        return "true".equalsIgnoreCase( getOption( "hbo_cloudbeds_email_enabled" ) );
    }

    @Override
    public void setOption( String property, String value ) {
        em.createNativeQuery(
                "   INSERT INTO " + wordpressPrefix + "options(option_name, option_value) "
                + " VALUES (:name, :value) "
                + " ON DUPLICATE KEY "
                + " UPDATE option_name = :name, option_value = :value")
            .setParameter( "name", property )
            .setParameter( "value", value )
            .executeUpdate();
    }

    @Override
    @SuppressWarnings( "unchecked" )
    public PxPostTransaction getLastPxPost( String bookingReference ) {
        List<PxPostTransaction> results = em.createQuery(
                    "FROM PxPostTransaction px "
                        + "  WHERE px.id IN ("
                        + "        SELECT MAX(px2.id) "
                        + "          FROM PxPostTransaction px2 "
                        + "         WHERE px2.bookingReference = :bookingRef)" )
                .setParameter( "bookingRef", bookingReference )
                .getResultList();
        return results.isEmpty() ? null : results.get( 0 );
    }

    @Override
    public PxPostTransaction fetchPxPostTransaction( int txnId ) {
        PxPostTransaction txn = em.find( PxPostTransaction.class, txnId );
        if ( txn == null ) {
            throw new EmptyResultDataAccessException( "Unable to find PxPostTransaction with ID " + txn, 1 );
        }
        return txn;
    }

    @Override
    public SagepayTransaction fetchSagepayTransaction( int id ) {
        SagepayTransaction txn = em.find( SagepayTransaction.class, id );
        if ( txn == null ) {
            throw new EmptyResultDataAccessException( "Unable to find SagepayTransaction with ID " + id, 1 );
        }
        return txn;
    }

    @Override
    public SagepayTransaction fetchSagepayTransaction( String vendorTxCode ) {
        return em.createQuery(
                "FROM SagepayTransaction WHERE vendorTxCode = :vendorTxCode AND authStatus = 'OK'", SagepayTransaction.class )
                .setParameter( "vendorTxCode", vendorTxCode )
                .getSingleResult();
    }

    @Override
    public void updateSagepayTransactionProcessedDate( int id ) {
        em.createNativeQuery( "UPDATE wp_sagepay_tx_auth "
                + "SET processed_date = NOW(), last_updated_date = NOW() "
                + "WHERE id = :id" )
                .setParameter( "id", id )
                .executeUpdate();
    }

    @Override
    public SagepayRefund fetchSagepayRefund( int id ) {
        SagepayRefund txn = em.find( SagepayRefund.class, id );
        if ( txn == null ) {
            throw new EmptyResultDataAccessException( "Unable to find SagepayRefund with ID " + id, 1 );
        }
        return txn;
    }

    @Override
    public void updateSagepayRefund( int id, String refVendorTxCode, String response, String status, String statusDetail, String transactionId ) {
        SagepayRefund refund = fetchSagepayRefund( id );
        refund.setVendorTxCode( refVendorTxCode );
        refund.setResponse( response );
        refund.setStatus( status );
        refund.setStatusDetail( statusDetail );
        refund.setTransactionId( transactionId );
        refund.setLastUpdatedDate( new Timestamp( System.currentTimeMillis() ) );
    }

    @Override
    public StripeRefund fetchStripeRefund( int id ) {
        StripeRefund txn = em.find( StripeRefund.class, id );
        if ( txn == null ) {
            throw new EmptyResultDataAccessException( "Unable to find StripeRefund with ID " + id, 1 );
        }
        return txn;
    }

    @Override
    public StripeTransaction fetchStripeTransaction( String vendorTxCode ) {
        return em.createQuery(
                "FROM StripeTransaction WHERE vendorTxCode = :vendorTxCode", StripeTransaction.class )
                .setParameter( "vendorTxCode", vendorTxCode )
                .getSingleResult();
    }

    @Override
    public List<StripeRefund> fetchStripeRefundsAtStatus( String status ) {
        return em.createQuery(
                "FROM StripeRefund WHERE status = :status", StripeRefund.class )
                .setParameter( "status", status )
                .getResultList();
    }

    @Override
    public void updateStripeRefund( int id, String chargeId, String response, String status ) {
        StripeRefund refund = fetchStripeRefund( id );
        boolean changed = false;
        if ( false == StringUtils.equals( refund.getChargeId(), chargeId ) ) {
            refund.setChargeId( chargeId );
            changed = true;
        }
        if ( false == StringUtils.equals( refund.getResponse(), response ) ) {
            refund.setResponse( response );
            changed = true;
        }
        if ( false == StringUtils.equals( refund.getStatus(), status ) ) {
            refund.setStatus( status );
            changed = true;
        }
        if ( changed ) {
            refund.setLastUpdatedDate( new Timestamp( System.currentTimeMillis() ) );
        }
    }

    @Override
    public void updateStripeTransaction( int id, String paymentStatus, String authDetails, String cardType, String last4Digits ) {
        StripeTransaction txn = em.find( StripeTransaction.class, id );
        if ( txn == null ) {
            throw new EmptyResultDataAccessException( "Unable to find StripeTransaction with ID " + id, 1 );
        }
        txn.setPaymentStatus( paymentStatus );
        txn.setAuthDetails( authDetails );
        txn.setCardType( cardType );
        txn.setLast4Digits( last4Digits );
        txn.setProcessedDate( new Timestamp( System.currentTimeMillis() ) );
        txn.setLastUpdatedDate( new Timestamp( System.currentTimeMillis() ) );
    }

    @Override
    public void insertBookingLookupKey( String reservationId, String key, BigDecimal paymentRequested ) {
        em.createNativeQuery( "INSERT INTO wp_booking_lookup_key ( reservation_id, lookup_key, payment_requested ) VALUES (?, ?, ?)" )
                .setParameter( 1, reservationId )
                .setParameter( 2, key )
                .setParameter( 3, paymentRequested )
                .executeUpdate();
    }

    @Override
    public void updatePxPostStatus( int txnId, String maskedCardNumber, boolean successful, String statusXml ) {
        PxPostTransaction txn = fetchPxPostTransaction( txnId );
        if( maskedCardNumber != null ) {
            txn.setMaskedCardNumber( maskedCardNumber );
        }
        txn.setSuccessful( successful );
        txn.setPaymentStatusResponseXml( statusXml );
        txn.setLastUpdatedDate( new Timestamp( System.currentTimeMillis() ) );
    }

    @Override
    public int insertNewPxPostTransaction( int jobId, String bookingRef, BigDecimal amountToPay ) {
        
        if ( StringUtils.isBlank( bookingRef ) ) {
            throw new IllegalArgumentException( "Booking Reference must not be null!" );
        }

        // amount must be present and positive
        if ( amountToPay == null || amountToPay.compareTo( new BigDecimal( "0" ) ) <= 0 ) {
            throw new IllegalArgumentException( "Invalid amount " + amountToPay );
        }

        PxPostTransaction pxpost = new PxPostTransaction();
        pxpost.setJobId( jobId );
        pxpost.setBookingReference( bookingRef );
        pxpost.setPaymentAmount( amountToPay );
        em.persist( pxpost );
        return pxpost.getId();
    }
    
    @Override
    public void updatePxPostTransaction( int txnId, String maskedCardNumber, String requestXML, 
            int httpStatus, String responseXML, boolean successful, String helpText ) {
        PxPostTransaction txn = fetchPxPostTransaction( txnId );
        txn.setMaskedCardNumber( maskedCardNumber );
        txn.setPaymentRequestXml( requestXML );
        txn.setPaymentResponseHttpCode( httpStatus );
        txn.setPaymentResponseXml( responseXML );
        txn.setSuccessful( successful );
        txn.setHelpText( helpText );
        txn.setPostDate( new Timestamp( System.currentTimeMillis() ) );
        txn.setLastUpdatedDate( new Timestamp( System.currentTimeMillis() ) );
    }

    @Override
    public int getPreviousNumberOfFailedTxns( String bookingRef, String maskedCardNumber ) {
        return em.createQuery( "SELECT COUNT(*) FROM PxPostTransaction "
                        + "     WHERE bookingReference = :bookingRef "
                        + "       AND maskedCardNumber = :maskedCardNumber "
                        + "       AND successful = :successful ", Number.class )
                .setParameter( "bookingRef", bookingRef )
                .setParameter( "maskedCardNumber", maskedCardNumber )
                .setParameter( "successful", Boolean.FALSE )
                .getSingleResult()
                .intValue();
    }

    @Override
    public boolean doesSendEmailEntryExist( String email ) {
        return em.createQuery( "SELECT COUNT(*) FROM SendEmailEntry WHERE email = :email", Number.class )
                .setParameter( "email", email )
                .getSingleResult().intValue() > 0;
    }
    
    @Override
    public void saveSendEmailEntry( SendEmailEntry record ) {
        record.setLastUpdatedDate( new Timestamp( System.currentTimeMillis() ) );
        em.merge( record );
    }
    
    @Override
    public void deleteSendEmailEntry( String emailAddress ) {
        List<SendEmailEntry> matchedEntries = em.createQuery(
                "FROM SendEmailEntry WHERE email = :email", SendEmailEntry.class )
            .setParameter( "email", emailAddress )
            .getResultList();
        
        // delete all matched entries
        for( SendEmailEntry entry : matchedEntries ) {
            em.remove( entry );
        }
    }
    
    @SuppressWarnings( "unchecked" )
    @Override
    public List<SendEmailEntry> fetchAllUnsentEmails() {
        return em.createQuery(
                "FROM SendEmailEntry WHERE sendDate IS NULL" )
                .getResultList();
    }

    @Override
    public String getGuestCheckoutEmailSubject() {
        return getNotNullOption( "hbo_guest_email_subject" );
    }

    @Override
    public String getGuestCheckoutEmailTemplate() {
        return getNotNullOption( "hbo_guest_email_template" );
    }

    @Override
    public String getBookingPaymentsURL() {
        return getNotNullOption( "hbo_booking_payments_url" );
    }

    @Override
    public String getBookingsURL() {
        return getNotNullOption( "hbo_bookings_url" );
    }

    /**
     * Returns the option for the given key.
     * 
     * @param optionName option key
     * @return non-null option value
     * @throws IncorrectResultSizeDataAccessException if option does not exist or is null
     */
    private String getNotNullOption( String optionName ) throws IncorrectResultSizeDataAccessException {
        String optionValue = getOption( optionName );
        if ( optionValue == null ) {
            throw new IncorrectResultSizeDataAccessException( "Missing option " + optionName, 1 );
        }
        return optionValue;
    }

    /**
     * Each processor thread needs a unique ID so it doesn't clash with other threads. This
     * generates one based on the designated processor ID and the name of the current thread.
     * 
     * @return unique processor id
     */
    private String getUniqueProcessorId() {
        return processorId + "-" + Thread.currentThread().getName();
    }

}
