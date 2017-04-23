
package com.macbackpackers.dao;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.Date;
import java.util.List;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import org.hibernate.SessionFactory;
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
import com.macbackpackers.jobs.AllocationScraperJob;

@Repository
@Transactional
public class WordPressDAOImpl implements WordPressDAO {

    private final Logger LOGGER = LoggerFactory.getLogger( getClass() );

    @Autowired
    private SessionFactory sessionFactory;

    @Autowired
    @Qualifier( "reportsSQL" )
    private Properties sql;

    @Value( "${hostelworld.hostelnumber}" )
    private String hostelNumber;

    @Value( "${processor.id}" )
    private String processorId;

    @Override
    public void insertAllocation( Allocation alloc ) {
        sessionFactory.getCurrentSession().save( alloc );
    }

    @Override
    public Allocation fetchAllocation( int id ) {
        Allocation alloc = (Allocation) sessionFactory.getCurrentSession().get( Allocation.class, id );
        if ( alloc == null ) {
            throw new EmptyResultDataAccessException( 1 );
        }
        return alloc;
    }

    @Override
    public void updateAllocation( Allocation alloc ) {
        alloc.setCreatedDate( new Timestamp( System.currentTimeMillis() ) );
        sessionFactory.getCurrentSession().saveOrUpdate( alloc );
    }

    @Override
    public void updateAllocationList( AllocationList allocList ) {
        allocList.setCreatedDate( new Timestamp( System.currentTimeMillis() ) );
        for ( Allocation a : allocList ) {
            sessionFactory.getCurrentSession().saveOrUpdate( a );
        }
    }

    @Override
    public void deleteAllocations( int jobId ) {
        int rowsDeleted = sessionFactory.getCurrentSession()
            .createQuery( "DELETE Allocation WHERE jobId = :jobId" )
            .setParameter( "jobId", jobId )
            .executeUpdate();
        LOGGER.info( rowsDeleted + " allocation rows deleted." );
    }

    @Override
    public void updateAllocationJobId( int oldAllocationJobId, int newAllocationJobId ) {
        int rowsUpdated = sessionFactory.getCurrentSession()
                .createQuery( "UPDATE Allocation SET jobId = :newJobId WHERE jobId = :oldJobId" )
                .setParameter( "oldJobId", oldAllocationJobId )
                .setParameter( "newJobId", newAllocationJobId )
                .executeUpdate();
            LOGGER.info( rowsUpdated + " allocation rows updated." );
    }

    @SuppressWarnings( "unchecked" )
    @Override
    public AllocationList queryAllocationsByJobIdAndReservationId( int jobId, int reservationId ) {
        return new AllocationList( sessionFactory.getCurrentSession()
                .createQuery( "FROM Allocation WHERE jobId = :jobId AND reservationId = :reservationId" )
                .setParameter( "jobId", jobId )
                .setParameter( "reservationId", reservationId )
                .getResultList() );
    }

    @Override
    public int insertJob( Job job ) {
        sessionFactory.getCurrentSession().saveOrUpdate( job );
        return job.getId();
    }

    @Override
    public void updateJobStatus( int jobId, JobStatus status, JobStatus prevStatus ) {
        AbstractJob job = fetchJobById( jobId );
        if ( prevStatus != job.getStatus() ) {
            throw new IncorrectNumberOfRecordsUpdatedException(
                    "Previous job status is " + job.getStatus() + " when attempting to set to " + status );
        }
        updateJobStatus( job, status );
    }

    @Override
    public boolean updateJobStatusToProcessing( int jobId ) {
        // there should only be 1 row updated
        // if not, another processor may have taken it so return false
        return 1 == sessionFactory.getCurrentSession().createQuery(
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
                .setParameter( "processedBy", processorId )
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
        sessionFactory.getCurrentSession().createQuery(
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
    public AbstractJob getNextJobToProcess() {
        // shutdown job has priority if present
        // include any jobs that have been tagged as processing by us
        // (since there should only ever be 1 unique one; we'll be re-running these jobs)
        List<AbstractJob> jobs = sessionFactory.getCurrentSession()
                .createQuery( "FROM AbstractJob "
                        + "     WHERE status = :submittedStatus "
                        + "        OR (status = :processingStatus AND processedBy = :processedBy)"
                        + "     ORDER BY CASE WHEN classname = 'com.macbackpackers.jobs.ShutdownJob' THEN 0 ELSE 1 END, job_id",
                        AbstractJob.class )
                .setParameter( "submittedStatus", JobStatus.submitted )
                .setParameter( "processingStatus", JobStatus.processing )
                .setParameter( "processedBy", processorId )
                .getResultList();
        
        forAllJobs: for ( AbstractJob job : jobs ) {
            // first check that all dependent jobs have completed successfully
            for ( Job dependentJob : job.getDependentJobs() ) {
                switch( dependentJob.getStatus()) {
                    case submitted:
                    case processing:
                        continue forAllJobs; // need to wait until parent job finishes
                    case failed:
                    case aborted:
                        // parent job failed/aborted so we abort 
                        job.setStatus( JobStatus.aborted );
                        continue forAllJobs;
                    default: // (completed)
                        // ok; just check remaining dependent jobs
                }
            }
            
            return job; // all dependent jobs are completed; this is the one
        }

        return null; // we couldn't find a job to process
    }

    @Override
    public AbstractJob fetchJobById( int id ) {
        AbstractJob j = (AbstractJob) sessionFactory.getCurrentSession().get( AbstractJob.class, id );
        if ( j == null ) {
            throw new EmptyResultDataAccessException( "Unable to find Job with ID " + id, 1 );
        }
        return j;
    }

    @SuppressWarnings( "unchecked" )
    @Override
    public <T extends AbstractJob> T getLastJobOfType( Class<T> jobType ) {
        List<?> jobIds = sessionFactory.getCurrentSession()
                .createQuery( "SELECT MAX(id) FROM AbstractJob WHERE classname = :classname" )
                .setParameter( "classname", jobType.getName() )
                .getResultList();
        Integer jobId = jobIds.isEmpty() ? null : Integer.class.cast( jobIds.get( 0 ));
        LOGGER.info( "Last " + jobType + ": " + (jobId == null ? "none" : jobId) );
        return jobId == null ? null : (T) fetchJobById( jobId );
    }

    @SuppressWarnings( "unchecked" )
    @Override
    public <T extends AbstractJob> T getLastCompletedJobOfType( Class<T> jobType ) {
        List<?> jobIds = sessionFactory.getCurrentSession()
                .createQuery( "SELECT MAX(id) FROM AbstractJob WHERE classname = :classname AND status = :status" )
                .setParameter( "classname", jobType.getName() )
                .setParameter( "status", JobStatus.completed )
                .getResultList();
        Integer jobId = jobIds.isEmpty() ? null : Integer.class.cast( jobIds.get( 0 ));
        LOGGER.info( "Last completed " + jobType + ": " + (jobId == null ? "none" : jobId) );
        return jobId == null ? null : (T) fetchJobById( jobId );
    }

    @SuppressWarnings( "unchecked" )
    @Override
    public List<Date> getCheckinDatesForAllocationScraperJobId( int jobId ) {
        // dates from calendar for a given (allocation scraper) job id
        // do not include room closures
        return sessionFactory.getCurrentSession().createQuery(
                "SELECT DISTINCT checkinDate"
                        + "     FROM Allocation "
                        + "    WHERE jobId = :jobId"
                        + "      AND reservationId > 0"
                        + "    ORDER BY checkinDate" )
                .setParameter( "jobId", jobId )
                .getResultList();
    }

    @SuppressWarnings( "unchecked" )
    @Override
    public List<Integer> getHostelworldHostelBookersUnpaidDepositReservations( int allocationScraperJobId ) {
        LOGGER.info( "Querying unpaid reservations for allocation job : " + allocationScraperJobId );
        return sessionFactory.getCurrentSession().createQuery(
                "SELECT reservationId " +
                        "  FROM Allocation " +
                        "WHERE jobId = :jobId " +
                        "  AND paymentTotal = paymentOutstanding " +
                        "  AND bookingSource IN ( 'Hostelworld', 'Hostelbookers', 'Hostelworld Group' ) " +
                        "GROUP BY reservationId" )
                .setParameter( "jobId", allocationScraperJobId )
                .getResultList();
    }

    @SuppressWarnings( "unchecked" )
    @Override
    public List<ScheduledJob> fetchActiveScheduledJobs() {
        return sessionFactory.getCurrentSession().createQuery(
                "FROM ScheduledJob WHERE active = true" )
                .getResultList();
    }

    @Override
    public ScheduledJob fetchScheduledJobById( int jobId ) {
        ScheduledJob j = (ScheduledJob) sessionFactory.getCurrentSession().get( ScheduledJob.class, jobId );
        if ( j == null ) {
            throw new EmptyResultDataAccessException( 1 );
        }
        return j;
    }

    @Override
    public void updateScheduledJob( int jobId ) {
        ScheduledJob job = (ScheduledJob) sessionFactory.getCurrentSession().get( ScheduledJob.class, jobId );
        if ( job == null ) {
            throw new EmptyResultDataAccessException( 1 );
        }
        job.setLastScheduledDate( new Timestamp( System.currentTimeMillis() ) );
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

        int rowsDeleted = sessionFactory.getCurrentSession()
                .createNativeQuery( "DELETE FROM wp_lh_jobs WHERE last_updated_date < :specifiedDate" )
                .setParameter( "specifiedDate", specifiedDate )
                .executeUpdate();
        LOGGER.info( "Purge Job: deleted " + rowsDeleted + " records from wp_lh_jobs" );
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
            int rowsDeleted = sessionFactory.getCurrentSession()
                    .createNativeQuery( "DELETE FROM " + table + " WHERE job_id IN ( " + JOB_ID_SELECT + ")" )
                    .setParameter( "specifiedDate", specifiedDate )
                    .executeUpdate();
            LOGGER.info( "Purge Job: deleted " + rowsDeleted + " records from " + table );
        }
    }

    @Override
    public void insertHostelworldBooking( HostelworldBooking booking ) {
        sessionFactory.getCurrentSession().save( booking );
    }
    
    @Override
    @SuppressWarnings( "unchecked" )
    public List<String> findMissingHwBookingRefs( int jobId, Date checkinDate ) {
        return sessionFactory.getCurrentSession().createNativeQuery(
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
        List<Integer> bookingIds = sessionFactory.getCurrentSession().createQuery(
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
    public void deleteHostelbookersBookingsWithArrivalDate( Date checkinDate ) {

        // find all bookings where the first (booked) date matches the checkin date
        List<Integer> bookingIds = sessionFactory.getCurrentSession().createQuery(
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
        for ( Integer bookingId : bookingIds ) {
            sessionFactory.getCurrentSession().createQuery(
                    "DELETE HostelworldBookingDate WHERE bookingId = :bookingId" )
                    .setParameter( "bookingId", bookingId )
                    .executeUpdate();
            sessionFactory.getCurrentSession().createQuery(
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
        List<Integer> roomTypeIds = sessionFactory.getCurrentSession()
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

    /////////////////////////////////////////////////////////////////////
    //    REPORTING SPECIFIC
    /////////////////////////////////////////////////////////////////////

    @Override
    public void runSplitRoomsReservationsReport( int allocationScraperJobId ) {
        LOGGER.info( "Running report for job id: " + allocationScraperJobId );

        // first remove any previous data in case we're running this again
        int rowsDeleted = sessionFactory.getCurrentSession()
                .createNativeQuery( "DELETE FROM wp_lh_rpt_split_rooms WHERE job_id = :jobId" )
                .setParameter( "jobId", allocationScraperJobId )
                .executeUpdate();
        LOGGER.info( "Deleted " + rowsDeleted + " previous records from wp_lh_rpt_split_rooms" );

        sessionFactory.getCurrentSession()
                .createNativeQuery( sql.getProperty( "reservations.split.rooms" ) )
                .setParameter( "jobId", allocationScraperJobId )
                .executeUpdate();
    }

    @Override
    public void runUnpaidDepositReport( int allocationScraperJobId ) {
        LOGGER.info( "Running report for job id: " + allocationScraperJobId );

        // first remove any previous data in case we're running this again
        int rowsDeleted = sessionFactory.getCurrentSession()
                .createNativeQuery( "DELETE FROM wp_lh_rpt_unpaid_deposit WHERE job_id = :jobId" )
                .setParameter( "jobId", allocationScraperJobId )
                .executeUpdate();
        LOGGER.info( "Deleted " + rowsDeleted + " previous records from wp_lh_rpt_unpaid_deposit" );

        sessionFactory.getCurrentSession()
                .createNativeQuery( sql.getProperty( "unpaid.deposit.report" ) )
                .setParameter( "jobId", allocationScraperJobId )
                .executeUpdate();
    }
    
    @Override
    @SuppressWarnings( "unchecked" )
    public List<UnpaidDepositReportEntry> fetchUnpaidDepositReport( int allocationScraperJobId ) {
        LOGGER.info( "Fetching last unpaid deposit report for allocation job id " + allocationScraperJobId );
        return sessionFactory.getCurrentSession().createQuery(
                "FROM UnpaidDepositReportEntry WHERE jobId = :jobId" )
                .setParameter( "jobId", allocationScraperJobId )
                .getResultList();
    }

    @Override
    public void runGroupBookingsReport( int allocationScraperJobId ) {
        LOGGER.info( "Running report for job id: " + allocationScraperJobId );

        // first remove any previous data in case we're running this again
        int rowsDeleted = sessionFactory.getCurrentSession()
                .createNativeQuery( "DELETE FROM wp_lh_group_bookings WHERE job_id = :jobId" )
                .setParameter( "jobId", allocationScraperJobId )
                .executeUpdate();
        LOGGER.info( "Deleted " + rowsDeleted + " previous records from wp_lh_group_bookings" );

        sessionFactory.getCurrentSession()
                .createNativeQuery( sql.getProperty( "group.bookings" ) )
                .setParameter( "jobId", allocationScraperJobId )
                .setParameter( "groupSize", getGroupBookingSize() )
                .executeUpdate();
    }

    @Override
    @SuppressWarnings( "unchecked" )
    public Integer getLastCompletedAllocationScraperJobId() {
        List<Integer> results = sessionFactory.getCurrentSession()
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
    public List<MissingGuestComment> getAllocationsWithoutEntryInGuestCommentsReport( int allocationScraperJobId ) {
        List<MissingGuestComment> allocations = sessionFactory.getCurrentSession().createQuery(
                        "SELECT DISTINCT new com.macbackpackers.beans.MissingGuestComment(c.reservationId, c.bookingReference, c.checkinDate)"
                        + "  FROM Allocation c "
                        + "  LEFT OUTER JOIN GuestCommentReportEntry r "
                        + "    ON c.reservationId = r.reservationId "
                        + " WHERE c.jobId = :allocationScraperJobId "
                        + "   AND c.bookingReference IS NOT NULL " // could be NULL if BookingScraperJob failed to run
                        + "   AND r.reservationId IS NULL "
                        + "   AND c.reservationId > 0"
                        + "   AND NOT EXISTS( "
                        // if this job failed earlier, don't include entries where we 
                        // have a pending job to update the guest comment table
                        + "         SELECT 1 FROM GuestCommentSaveJob gcj"
                        + "           JOIN JobParameter gcjp ON gcj.id = gcjp.jobId"
                        + "          WHERE gcjp.name = 'reservation_id'"
                        + "            AND gcjp.value = c.reservationId"
                        + "            AND gcj.status IN ('submitted', 'processing'))", MissingGuestComment.class )
                .setParameter( "allocationScraperJobId", allocationScraperJobId )
                .getResultList();
        return allocations;
    }

    @Override
    public void updateGuestCommentsForReservation( int reservationId, String comment ) {
        sessionFactory.getCurrentSession()
                .createNativeQuery( "INSERT INTO wp_lh_rpt_guest_comments ( reservation_id, comments ) "
                        + " VALUES ( :reservationId, :comments ) "
                        + " ON DUPLICATE KEY UPDATE "
                        + " reservation_id = VALUES( reservation_id ), "
                        + " comments = VALUES( comments )" )
                .setParameter( "reservationId", reservationId )
                .setParameter( "comments", comment )
                .executeUpdate();
        LOGGER.info( "Updating guest comments for reservation " + reservationId );
    }

    @Override
    @SuppressWarnings( "unchecked" )
    @Transactional( readOnly = true )
    public String getOption( String property ) {
        List<String> sqlResult = sessionFactory.getCurrentSession().createNativeQuery(
                "          SELECT option_value"
                        + "  FROM wp_options"
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
    @Transactional( readOnly = true )
    public int getGroupBookingSize() {
        return Integer.parseInt( getOption( "hbo_group_booking_size" ) );
    }

    @Override
    public void setOption( String property, String value ) {
        sessionFactory.getCurrentSession().createNativeQuery(
                "   INSERT INTO wp_options(option_name, option_value) "
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
        List<PxPostTransaction> results = sessionFactory.getCurrentSession().createQuery(
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
        PxPostTransaction txn = (PxPostTransaction) sessionFactory.getCurrentSession().get( PxPostTransaction.class, txnId );
        if ( txn == null ) {
            throw new EmptyResultDataAccessException( "Unable to find PxPostTransaction with ID " + txn, 1 );
        }
        return txn;
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
    public int insertNewPxPostTransaction( String bookingRef, BigDecimal amountToPay ) {
        
        if ( StringUtils.isBlank( bookingRef ) ) {
            throw new IllegalArgumentException( "Booking Reference must not be null!" );
        }

        // amount must be present and positive
        if ( amountToPay == null || amountToPay.compareTo( new BigDecimal( "0" ) ) <= 0 ) {
            throw new IllegalArgumentException( "Invalid amount " + amountToPay );
        }

        PxPostTransaction pxpost = new PxPostTransaction();
        pxpost.setBookingReference( bookingRef );
        pxpost.setPaymentAmount( amountToPay );
        sessionFactory.getCurrentSession().save( pxpost );
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
        return sessionFactory.getCurrentSession()
                .createQuery( "SELECT COUNT(*) FROM PxPostTransaction "
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
        return sessionFactory.getCurrentSession()
                .createQuery( "SELECT COUNT(*) FROM SendEmailEntry WHERE email = :email", Number.class )
                .setParameter( "email", email )
                .getSingleResult().intValue() > 0;
    }
    
    @Override
    public void saveSendEmailEntry( SendEmailEntry record ) {
        record.setLastUpdatedDate( new Timestamp( System.currentTimeMillis() ) );
        sessionFactory.getCurrentSession().saveOrUpdate( record );
    }
    
    @Override
    public void deleteSendEmailEntry( String emailAddress ) {
        List<SendEmailEntry> matchedEntries = sessionFactory.getCurrentSession().createQuery(
                "FROM SendEmailEntry WHERE email = :email", SendEmailEntry.class )
            .setParameter( "email", emailAddress )
            .getResultList();
        
        // delete all matched entries
        for( SendEmailEntry entry : matchedEntries ) {
            sessionFactory.getCurrentSession().delete( entry );
        }
    }
    
    @SuppressWarnings( "unchecked" )
    @Override
    public List<SendEmailEntry> fetchAllUnsentEmails() {
        return sessionFactory.getCurrentSession().createQuery(
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

}
