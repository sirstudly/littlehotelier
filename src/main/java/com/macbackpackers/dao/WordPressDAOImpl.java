
package com.macbackpackers.dao;

import com.macbackpackers.beans.Allocation;
import com.macbackpackers.beans.AllocationList;
import com.macbackpackers.beans.BlacklistEntry;
import com.macbackpackers.beans.BookingAssignment;
import com.macbackpackers.beans.BookingSourceLookup;
import com.macbackpackers.beans.BookingByCheckinDate;
import com.macbackpackers.beans.BookingReport;
import com.macbackpackers.beans.BookingWithGuestComments;
import com.macbackpackers.beans.GuestCommentReportEntry;
import com.macbackpackers.beans.HostelworldBooking;
import com.macbackpackers.beans.HousekeepingBed;
import com.macbackpackers.beans.Job;
import com.macbackpackers.beans.OccupancyVersion;
import com.macbackpackers.beans.JobScheduler;
import com.macbackpackers.beans.JobStatus;
import com.macbackpackers.beans.RoomBed;
import com.macbackpackers.beans.RoomBedLookup;
import com.macbackpackers.beans.ScheduledJob;
import com.macbackpackers.beans.SendEmailEntry;
import com.macbackpackers.beans.StripeRefund;
import com.macbackpackers.beans.StripeTransaction;
import com.macbackpackers.beans.UnpaidDepositReportEntry;
import com.macbackpackers.beans.MostlyFullDormReportEntry;
import com.macbackpackers.exceptions.IncorrectNumberOfRecordsUpdatedException;
import com.macbackpackers.exceptions.MissingUserDataException;
import com.macbackpackers.jobs.AbstractJob;
import com.macbackpackers.jobs.AllocationScraperJob;
import com.macbackpackers.jobs.CalculateEdinburghVisitorLevyForBookingJob;
import com.macbackpackers.jobs.JobPriorities;
import com.macbackpackers.jobs.ResetCloudbedsSessionJob;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.text.StringEscapeUtils;
import org.hibernate.Hibernate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.dao.IncorrectResultSizeDataAccessException;
import org.springframework.jdbc.IncorrectResultSetColumnCountException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import jakarta.persistence.EntityManager;
import jakarta.persistence.NoResultException;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import jakarta.persistence.TypedQuery;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Repository
@Transactional
public class WordPressDAOImpl implements WordPressDAO {

    private final Logger LOGGER = LoggerFactory.getLogger( getClass() );

    @PersistenceContext
    private EntityManager em;

    @Autowired
    private PlatformTransactionManager transactionManager;
    
    @Autowired
    @Qualifier( "reportsSQL" )
    private Properties sql;

    @Value( "${processor.id}" )
    private String processorId;

    // Per-instance caches: must NOT be static. ronbot-read-api runs all property
    // contexts in one JVM; a JVM-wide cache would mix Cloudbeds property_id / cookies
    // across hostels (e.g. LSH options returned for HSH availability).
    private String csrfToken = null;

    private static final long WP_OPTIONS_CACHE_TIMEOUT_MINUTES = 5;

    private final Cache<String, String> wpOptionsCache = CacheBuilder.newBuilder()
            .expireAfterAccess( WP_OPTIONS_CACHE_TIMEOUT_MINUTES, TimeUnit.MINUTES )
            .build();

    /** Min interval between abort-failed-parent sweeps (claim path; reduces lock contention). */
    private static final long ABORT_FAILED_PARENTS_MIN_INTERVAL_MS = 30_000L;

    private final AtomicLong lastAbortFailedParentsAt = new AtomicLong( 0 );

    @Override
    public boolean isCloudbeds() {
        return "cloudbeds".equalsIgnoreCase( getOption( "hbo_property_manager" ) );
    }

    @Override
    public void insertAllocation( Allocation alloc ) {
        em.persist( alloc );
    }

    /** Rows per multi-value INSERT for allocations (keeps each Tailscale round-trip short). */
    public static final int ALLOCATION_INSERT_BATCH_SIZE = 25;

    /** Rows per multi-value UPSERT for guest comments. */
    public static final int GUEST_COMMENT_UPSERT_BATCH_SIZE = 10;

    /**
     * Per-chunk transaction timeout (seconds). Must stay above slow Tailscale batch times
     * (observed ~60s for 50-row batches) and align with the query hint below.
     */
    private static final int CHUNK_TX_TIMEOUT_SECONDS = 180;

    private static final int BOOKING_ASSIGNMENT_RECONCILE_CHUNK_SIZE = 100;

    /** Timeout for other large bulk writes (occupancy / housekeeping). */
    private static final int BULK_PERSIST_TX_TIMEOUT_SECONDS = 300;

    @Override
    @Transactional( propagation = Propagation.NOT_SUPPORTED )
    public void insertAllocations( AllocationList allocations ) {
        if ( allocations == null || allocations.isEmpty() ) {
            LOGGER.info( "Nothing to update." );
            return;
        }
        TransactionTemplate tt = new TransactionTemplate( transactionManager );
        tt.setPropagationBehavior( TransactionDefinition.PROPAGATION_REQUIRES_NEW );
        tt.setTimeout( CHUNK_TX_TIMEOUT_SECONDS );
        int totalInserted = 0;
        for ( int from = 0 ; from < allocations.size() ; from += ALLOCATION_INSERT_BATCH_SIZE ) {
            int to = Math.min( from + ALLOCATION_INSERT_BATCH_SIZE, allocations.size() );
            AllocationList batch = new AllocationList( allocations.subList( from, to ) );
            long started = System.currentTimeMillis();
            try {
                Integer inserted = tt.execute( status -> {
                    Query q = em.createNativeQuery( batch.getBulkInsertStatement() );
                    // Override global jakarta.persistence.query.timeout=60000 for slow Tailscale links
                    q.setHint( "jakarta.persistence.query.timeout", CHUNK_TX_TIMEOUT_SECONDS * 1000 );
                    for ( int i = 0 ; i < batch.size() ; i++ ) {
                        Object[] params = batch.get( i ).getAsParameters();
                        for ( int j = 0 ; j < params.length ; j++ ) {
                            q.setParameter( i * params.length + j + 1, params[j] );
                        }
                    }
                    return q.executeUpdate();
                } );
                totalInserted += inserted == null ? 0 : inserted;
                LOGGER.info( "Inserted {}/{} allocation rows ({} ms for batch of {}).",
                        totalInserted, allocations.size(), System.currentTimeMillis() - started, batch.size() );
            }
            catch ( RuntimeException ex ) {
                LOGGER.error( "Allocation insert failed after {}/{} rows (batch {}-{}, {} ms): {}",
                        totalInserted, allocations.size(), from, to, System.currentTimeMillis() - started, ex.toString() );
                throw ex;
            }
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
    @Transactional( propagation = Propagation.NOT_SUPPORTED )
    public int insertJob( Job job ) {
        // Own short transactions per attempt so MySQL deadlocks on wp_lh_job_dependency
        // (common under concurrent claim + insert) can be retried cleanly.
        TransactionTemplate tt = new TransactionTemplate( transactionManager );
        tt.setPropagationBehavior( TransactionDefinition.PROPAGATION_REQUIRES_NEW );
        final int maxAttempts = 3;
        for ( int attempt = 1 ; attempt <= maxAttempts ; attempt++ ) {
            try {
                return tt.execute( status -> {
                    em.persist( job );
                    em.flush();
                    return job.getId();
                } );
            }
            catch ( CannotAcquireLockException ex ) {
                if ( attempt == maxAttempts ) {
                    throw ex;
                }
                long sleepMs = 50L * attempt + ThreadLocalRandom.current().nextInt( 50 );
                LOGGER.warn( "Deadlock inserting job (attempt {}/{}); retrying in {}ms",
                        attempt, maxAttempts, sleepMs );
                try {
                    Thread.sleep( sleepMs );
                }
                catch ( InterruptedException ie ) {
                    Thread.currentThread().interrupt();
                    throw ex;
                }
            }
        }
        throw new IllegalStateException( "insertJob retry loop exited without result" );
    }

    @Override
    public boolean hasChargeNonRefundableJobForReservation( String reservationId ) {
        return hasRecentChargeNonRefundableJobForReservation( reservationId, NON_REFUNDABLE_CHARGE_COOLDOWN_HOURS, null );
    }

    @Override
    public boolean hasRecentChargeNonRefundableJobForReservation( String reservationId, int hoursBack, Integer excludeJobId ) {
        if ( StringUtils.isBlank( reservationId ) ) {
            return false;
        }
        StringBuilder sql = new StringBuilder(
                "SELECT COUNT(1) FROM wp_lh_jobs j "
                        + "  JOIN wp_lh_job_param p ON j.job_id = p.job_id "
                        + " WHERE j.classname = 'com.macbackpackers.jobs.ChargeNonRefundableBookingJob' "
                        + "   AND p.name = 'reservation_id' "
                        + "   AND p.value = :reservationId " );
        if ( excludeJobId != null ) {
            sql.append( "   AND j.job_id != :excludeJobId " );
        }
        if ( excludeJobId == null ) {
            // enqueue dedup: block while a job is pending, or any finished attempt within the cooldown
            sql.append( "   AND (j.status IN ('submitted', 'processing', 'retry') "
                    + "        OR (j.status IN ('completed', 'failed') "
                    + "            AND (j.created_date >= DATE_SUB(NOW(), INTERVAL :hoursBack HOUR) "
                    + "                 OR j.last_updated_date >= DATE_SUB(NOW(), INTERVAL :hoursBack HOUR))))" );
        }
        else {
            // execution cooldown: only count jobs that have actually run (or are running now), not
            // other submitted jobs waiting in the queue
            sql.append( "   AND (j.status = 'processing' "
                    + "        OR (j.status IN ('completed', 'failed', 'retry') "
                    + "            AND (j.created_date >= DATE_SUB(NOW(), INTERVAL :hoursBack HOUR) "
                    + "                 OR j.last_updated_date >= DATE_SUB(NOW(), INTERVAL :hoursBack HOUR))))" );
        }
        Query query = em.createNativeQuery( sql.toString() )
                .setParameter( "reservationId", reservationId )
                .setParameter( "hoursBack", hoursBack );
        if ( excludeJobId != null ) {
            query.setParameter( "excludeJobId", excludeJobId );
        }
        Number count = (Number) query.getSingleResult();
        return count.longValue() > 0;
    }

    @Override
    public boolean hasRecentSendGmailJobWithSubject( String subject, int hoursBack ) {
        if ( StringUtils.isBlank( subject ) ) {
            return false;
        }
        Number count = (Number) em.createNativeQuery(
                "SELECT COUNT(1) FROM wp_lh_jobs j "
                        + "  JOIN wp_lh_job_param p ON j.job_id = p.job_id "
                        + " WHERE j.classname = 'com.macbackpackers.jobs.SendGmailJob' "
                        + "   AND p.name = 'subject' "
                        + "   AND p.value = :subject "
                        + "   AND (j.status IN ('submitted', 'processing', 'retry') "
                        + "        OR (j.status IN ('completed', 'failed') "
                        + "            AND (j.created_date >= DATE_SUB(NOW(), INTERVAL :hoursBack HOUR) "
                        + "                 OR j.last_updated_date >= DATE_SUB(NOW(), INTERVAL :hoursBack HOUR))))" )
                .setParameter( "subject", subject )
                .setParameter( "hoursBack", hoursBack )
                .getSingleResult();
        return count.longValue() > 0;
    }

    @Override
    public boolean hasCalculateEdinburghVisitorLevyJobForReservation( String reservationId ) {
        return findPendingCalculateEdinburghVisitorLevyJobForReservation( reservationId ) != null;
    }

    @Override
    public boolean hasBookingAssignmentEnrichJobForReservation( String reservationId ) {
        if ( StringUtils.isBlank( reservationId ) ) {
            return false;
        }
        String sql = "SELECT COUNT(1) FROM wp_lh_jobs j "
                + "  JOIN wp_lh_job_param p ON j.job_id = p.job_id "
                + " WHERE j.classname = 'com.macbackpackers.jobs.BookingAssignmentEnrichJob' "
                + "   AND p.name = 'reservation_id' "
                + "   AND p.value = :reservationId "
                + "   AND j.status IN ('submitted', 'processing', 'retry')";
        Number count = (Number) em.createNativeQuery( sql )
                .setParameter( "reservationId", reservationId )
                .getSingleResult();
        return count.longValue() > 0;
    }

    @Override
    public CalculateEdinburghVisitorLevyForBookingJob findPendingCalculateEdinburghVisitorLevyJobForReservation(
            String reservationId ) {
        if ( StringUtils.isBlank( reservationId ) ) {
            return null;
        }
        String sql = "SELECT j.job_id FROM wp_lh_jobs j "
                + "  JOIN wp_lh_job_param p ON j.job_id = p.job_id "
                + " WHERE j.classname = 'com.macbackpackers.jobs.CalculateEdinburghVisitorLevyForBookingJob' "
                + "   AND p.name = 'reservation_id' "
                + "   AND p.value = :reservationId "
                + "   AND j.status IN ('submitted', 'processing', 'retry') "
                + " ORDER BY j.job_id ASC";
        @SuppressWarnings( "unchecked" )
        List<Number> jobIds = em.createNativeQuery( sql )
                .setParameter( "reservationId", reservationId )
                .getResultList();
        if ( jobIds.isEmpty() ) {
            return null;
        }
        return fetchJobById( jobIds.get( 0 ).intValue(), CalculateEdinburghVisitorLevyForBookingJob.class );
    }

    @Override
    public boolean hasArchiveAllTransactionNotesJobForReservation( String reservationId ) {
        if ( StringUtils.isBlank( reservationId ) ) {
            return false;
        }
        String sql = "SELECT COUNT(1) FROM wp_lh_jobs j "
                + "  JOIN wp_lh_job_param p ON j.job_id = p.job_id "
                + " WHERE j.classname = 'com.macbackpackers.jobs.ArchiveAllTransactionNotesJob' "
                + "   AND p.name = 'reservation_id' "
                + "   AND p.value = :reservationId "
                + "   AND j.status IN ('submitted', 'processing', 'retry')";
        Number count = (Number) em.createNativeQuery( sql )
                .setParameter( "reservationId", reservationId )
                .getSingleResult();
        return count.longValue() > 0;
    }

    @Override
    @SuppressWarnings( "unchecked" )
    public List<Map<String, Object>> listJobsForReservation( String reservationId, int limit ) {
        if ( StringUtils.isBlank( reservationId ) ) {
            return Collections.emptyList();
        }
        int capped = Math.max( 1, Math.min( limit, 200 ) );
        // MySQL 5.5-safe: no CTE. Jobs that mention this reservation_id in params.
        List<Object[]> rows = em.createNativeQuery(
                "SELECT j.job_id, j.classname, j.status, j.processed_by, "
                        + "       j.created_date, j.start_date, j.end_date, j.last_updated_date "
                        + "  FROM wp_lh_jobs j "
                        + " WHERE j.job_id IN ( "
                        + "       SELECT p.job_id FROM wp_lh_job_param p "
                        + "        WHERE p.name = 'reservation_id' AND p.value = :reservationId "
                        + " ) "
                        + " ORDER BY j.job_id DESC "
                        + " LIMIT " + capped )
                .setParameter( "reservationId", reservationId )
                .getResultList();

        List<Map<String, Object>> result = new ArrayList<>();
        for ( Object[] row : rows ) {
            Map<String, Object> job = new LinkedHashMap<>();
            Number jobId = (Number) row[0];
            job.put( "jobId", jobId.intValue() );
            job.put( "classname", row[1] );
            job.put( "status", row[2] );
            job.put( "processedBy", row[3] );
            job.put( "createdDate", row[4] == null ? null : row[4].toString() );
            job.put( "startDate", row[5] == null ? null : row[5].toString() );
            job.put( "endDate", row[6] == null ? null : row[6].toString() );
            job.put( "lastUpdatedDate", row[7] == null ? null : row[7].toString() );

            List<Object[]> params = em.createNativeQuery(
                    "SELECT name, value FROM wp_lh_job_param WHERE job_id = :jobId" )
                    .setParameter( "jobId", jobId.intValue() )
                    .getResultList();
            Map<String, String> paramMap = new LinkedHashMap<>();
            for ( Object[] p : params ) {
                paramMap.put( String.valueOf( p[0] ), p[1] == null ? null : String.valueOf( p[1] ) );
            }
            job.put( "parameters", paramMap );
            result.add( job );
        }
        return result;
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
    public long getProcessingJobCount() {
        return em.createQuery( "SELECT COUNT(1) FROM AbstractJob "
                + "     WHERE status = :processingStatus", Long.class )
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
    public boolean updateJobStatusToRetry( int jobId ) {
        AbstractJob job = fetchJobById( jobId );
        if ( JobStatus.processing != job.getStatus() ) {
            throw new IncorrectNumberOfRecordsUpdatedException(
                    "Previous job " + jobId + " status is " + job.getStatus() + " when attempting to set to retry" );
        }

        String retryCount = job.getParameter( "retry_count_remaining" );
        if ( retryCount == null ) {
            // set default number of retries to 5
            job.setParameter( "retry_count_remaining", "5" );
            updateJobStatus( job, JobStatus.retry );
            return true;
        }
        int retries = Integer.parseInt( retryCount ) - 1;
        if ( retries <= 0 ) {
            LOGGER.error( "Job {} exhausted retry_count_remaining; marking failed", jobId );
            updateJobStatus( job, JobStatus.failed );
            return false;
        }
        job.setParameter( "retry_count_remaining", Integer.toString( retries ) );
        updateJobStatus( job, JobStatus.retry );
        return true;
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
    public void resetAllProcessingJobsToSubmitted() {
        em.createQuery(
                "UPDATE AbstractJob "
                        + "   SET status = :submitted, "
                        + "       lastUpdatedDate = :now"
                        + " WHERE status = :processing "
                        + "   AND processedBy LIKE :processedBy" )
                .setParameter( "submitted", JobStatus.submitted )
                .setParameter( "processing", JobStatus.processing )
                .setParameter( "processedBy", this.processorId + "-%" )
                .setParameter( "now", new Timestamp( System.currentTimeMillis() ) )
                .executeUpdate();
    }

    @Override
    public synchronized AbstractJob getNextJobToProcess() {
        // include any jobs that have been tagged as processing by us
        // (since there should only ever be 1 unique one; we'll be re-running these jobs)
        String thisProcessorId = getUniqueProcessorId();
        LOGGER.info( "Getting next job for " + thisProcessorId );

        Timestamp now = new Timestamp( System.currentTimeMillis() );
        maybeAbortJobsWithFailedOrAbortedParents( now );

        // Single eligible job by priority then id — avoid N+1 fetch of the entire submitted queue
        @SuppressWarnings( "unchecked" )
        List<Number> jobIds = em.createNativeQuery(
                "SELECT j.`job_id` FROM `wp_lh_jobs` j "
                        + " WHERE (j.`status` IN ('submitted', 'retry') "
                        + "        OR (j.`status` = 'processing' AND j.`processed_by` = :processedBy))"
                        + "   AND NOT EXISTS ("
                        + "         SELECT 1 FROM `wp_lh_job_dependency` d "
                        + "           JOIN `wp_lh_jobs` p ON p.`job_id` = d.`depends_on_job_id` "
                        + "          WHERE d.`job_id` = j.`job_id` "
                        + "            AND p.`status` IN ('submitted', 'retry', 'processing')"
                        + "       )"
                        + " ORDER BY " + JobPriorities.sqlCaseExpression( "j.`classname`" ) + ", j.`job_id`" )
                .setParameter( "processedBy", thisProcessorId )
                .setMaxResults( 1 )
                .getResultList();

        if ( jobIds.isEmpty() ) {
            LOGGER.info( "No more jobs to process..." );
            return null;
        }

        AbstractJob job = fetchJobById( jobIds.get( 0 ).intValue() );
        // Claim no longer iterates dependentJobs in Java (SQL handles eligibility); initialize here
        // so processJob() can read them after this @Transactional method returns.
        Hibernate.initialize( job.getDependentJobs() );
        LOGGER.debug( "Attempting to lock job " + job.getId() );
        job.setStatus( JobStatus.processing );
        job.setJobStartDate( now );
        job.setJobEndDate( null );
        job.setProcessedBy( thisProcessorId );
        job.setLastUpdatedDate( now );
        return job;
    }

    /**
     * Throttled wrapper: abort sweep locks {@code wp_lh_job_dependency} / {@code wp_lh_jobs} and
     * races with concurrent {@link #insertJob} dependency inserts under multi-worker load.
     */
    private void maybeAbortJobsWithFailedOrAbortedParents( Timestamp now ) {
        long last = lastAbortFailedParentsAt.get();
        long t = now.getTime();
        if ( t - last < ABORT_FAILED_PARENTS_MIN_INTERVAL_MS ) {
            return;
        }
        if ( !lastAbortFailedParentsAt.compareAndSet( last, t ) ) {
            return; // another thread just ran / claimed the slot
        }
        abortJobsWithFailedOrAbortedParents( now );
    }

    /**
     * Marks submitted/retry jobs as aborted when any dependency parent has failed or been aborted.
     * Uses a derived-table subquery so MySQL 5.5 allows updating {@code wp_lh_jobs} while reading it
     * ("You can't specify target table for update in FROM clause").
     */
    private void abortJobsWithFailedOrAbortedParents( Timestamp now ) {
        int aborted = em.createNativeQuery(
                "UPDATE `wp_lh_jobs` "
                        + "   SET `status` = 'aborted', `last_updated_date` = :now "
                        + " WHERE `status` IN ('submitted', 'retry') "
                        + "   AND `job_id` IN ("
                        + "         SELECT `job_id` FROM ("
                        + "             SELECT DISTINCT d.`job_id` "
                        + "               FROM `wp_lh_job_dependency` d "
                        + "               JOIN `wp_lh_jobs` p ON p.`job_id` = d.`depends_on_job_id` "
                        + "              WHERE p.`status` IN ('failed', 'aborted')"
                        + "         ) AS doomed"
                        + "       )" )
                .setParameter( "now", now )
                .executeUpdate();
        if ( aborted > 0 ) {
            LOGGER.info( "Aborted {} job(s) whose dependency parents failed or were aborted", aborted );
        }
    }

    @Override
    public AbstractJob fetchJobById( int id ) {
        return fetchJobById( id, AbstractJob.class );
    }

    @Override
    public <T extends AbstractJob> T fetchJobById( int id, Class<T> clazz) {
        T j = em.find( clazz, id);
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
                "wp_lh_rpt_mostly_full_dorms",
                "wp_lh_calendar" );

        // now delete from jobs
        deleteFromTablesByJobId( specifiedDate, "wp_lh_job_param" );
        deleteFromTablesByJobId( specifiedDate, "wp_lh_job_dependency" );

        int rowsDeleted = em
                .createNativeQuery( "DELETE FROM wp_lh_jobs WHERE last_updated_date < :specifiedDate" )
                .setParameter( "specifiedDate", specifiedDate )
                .executeUpdate();
        LOGGER.info( "Purge Job: deleted " + rowsDeleted + " records from wp_lh_jobs" );

        rowsDeleted = em
                .createNativeQuery( "DELETE FROM wp_booking_lookup_key WHERE created_date < DATE_SUB(NOW(), INTERVAL 1 YEAR)" )
                .executeUpdate();
        LOGGER.info( "Purge Job: deleted " + rowsDeleted + " records from wp_booking_lookup_key older than 1 year" );

        rowsDeleted = em
                .createNativeQuery( "DELETE FROM wp_lh_rpt_guest_comments WHERE created_date < DATE_SUB(NOW(), INTERVAL 1 YEAR)" )
                .executeUpdate();
        LOGGER.info( "Purge Job: deleted " + rowsDeleted + " records from wp_lh_rpt_guest_comments older than 1 year" );
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

    @Override
    public void replaceAllRoomBeds( List<RoomBed> roomBeds ) {
        int deleted = em.createQuery( "DELETE FROM RoomBed rb WHERE rb.room <> 'Unallocated'" ).executeUpdate();
        LOGGER.info( "replaceAllRoomBeds: cleared {} wp_lh_rooms row(s)", deleted );
        if ( roomBeds.isEmpty() ) {
            return;
        }
        // Multi-row INSERT — individual em.persist() hits the 60s txn timeout on large properties
        final int cols = 7;
        Query q = em.createNativeQuery(
                "INSERT INTO wp_lh_rooms (id, room, bed_name, capacity, room_type_id, room_type, active_yn) VALUES "
                        + StringUtils.repeat( "(?,?,?,?,?,?,?)", ",", roomBeds.size() ) );
        for ( int i = 0 ; i < roomBeds.size() ; i++ ) {
            RoomBed rb = roomBeds.get( i );
            int p = i * cols;
            q.setParameter( p + 1, rb.getId() );
            q.setParameter( p + 2, rb.getRoom() );
            q.setParameter( p + 3, rb.getBedName() );
            q.setParameter( p + 4, rb.getCapacity() );
            q.setParameter( p + 5, rb.getRoomTypeId() );
            q.setParameter( p + 6, rb.getRoomType() );
            q.setParameter( p + 7, rb.getActive() );
        }
        int inserted = q.executeUpdate();
        LOGGER.info( "replaceAllRoomBeds: inserted {} row(s)", inserted );
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
    public boolean isHostelworldCancelBookingExempt( String bookingReference ) {
        Number count = (Number) em.createNativeQuery(
                "SELECT COUNT(1) FROM wp_hwl_cancel_booking_exempt WHERE booking_reference = :bookingReference" )
                .setParameter( "bookingReference", bookingReference )
                .getSingleResult();
        return count.longValue() > 0;
    }

    @Override
    public List<MostlyFullDormReportEntry> fetchMostlyFullDormReport( int allocationScraperJobId ) {
        LOGGER.info( "Fetching mostly-full dorm report for allocation job id " + allocationScraperJobId );
        return em.createQuery(
                "FROM MostlyFullDormReportEntry WHERE jobId = :jobId", MostlyFullDormReportEntry.class )
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
    public List<Allocation> fetchBookingsMatchingBlacklist( int allocationScraperJobId, List<BlacklistEntry> blacklistEntries ) {
        if ( blacklistEntries.size() > 0 ) {
            List<String> sqlClauses = new ArrayList<>();
            List<Object> params = new ArrayList<>();
            blacklistEntries.stream()
                    .forEach( e -> {
                        if( StringUtils.isNotBlank( e.getFirstName() ) && StringUtils.isNotBlank( e.getLastName() ) ) {
                            sqlClauses.add( "LOWER(c.guestName) = ?" + (sqlClauses.size() + 1) );
                            params.add( (e.getFirstName() + " " + e.getLastName()).toLowerCase() );
                        }
                        if( StringUtils.isNotBlank( e.getEmail() ) ) {
                            sqlClauses.add( "LOWER(c.email) = ?" + (sqlClauses.size() + 1) );
                            params.add( e.getEmail().toLowerCase() );
                        }
                    } );
            TypedQuery<Allocation> query = em.createQuery( "FROM Allocation c "
                            + " WHERE c.jobId = :allocationScraperJobId "
                            + "   AND (" + sqlClauses.stream().collect(Collectors.joining(" OR ")) +  ")", Allocation.class )
                    .setParameter( "allocationScraperJobId", allocationScraperJobId );
            for( int i = 0; i < params.size(); i++ ) {
                query.setParameter(i + 1, params.get( i ));
            }
            return query.getResultList();
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
    public void runMostlyFullDormReport( int allocationScraperJobId ) {
        LOGGER.info( "Running mostly-full dorm report for job id: " + allocationScraperJobId );

        // first remove any previous data in case we're running this again
        int rowsDeleted = em
                .createNativeQuery( "DELETE FROM wp_lh_rpt_mostly_full_dorms WHERE job_id = :jobId" )
                .setParameter( "jobId", allocationScraperJobId )
                .executeUpdate();
        LOGGER.info( "Deleted " + rowsDeleted + " previous records from wp_lh_rpt_mostly_full_dorms" );

        em.createNativeQuery( sql.getProperty( "mostly.full.dorms" ) )
            .setParameter( "jobId", allocationScraperJobId )
            .setParameter( "propertyManager", StringUtils.defaultIfBlank( getOption( "hbo_property_manager" ), "n/a" ) )
            .executeUpdate();
    }

    @Override
    public void runBedCountsReport( int bedCountJobId, LocalDate selectionDate ) {
        LOGGER.info( "Running bedcounts report for job id: " + bedCountJobId );

        // first remove any previous data in case we're running this again
        int rowsDeleted = em
                .createNativeQuery( "DELETE FROM wp_lh_bedcounts WHERE report_date = :selectionDate" )
                .setParameter( "selectionDate", selectionDate )
                .executeUpdate();
        LOGGER.info( "Deleted " + rowsDeleted + " previous records from wp_lh_bedcounts" );

        int rowsAdded = em.createNativeQuery( sql.getProperty( getOption( "siteurl" ).contains( "highstreet" )
                                ? "bedcounts.report.insert.hsh" : "bedcounts.report.insert" )
                        .replaceAll( "__SQL_SELECT__", sql.getProperty( "bedcounts.report.select" ) ) )
                .setParameter( "jobId", bedCountJobId )
                .setParameter( "selectionDate", selectionDate )
                .executeUpdate();
        LOGGER.info( "Added " + rowsAdded + " records to wp_lh_bedcounts" );
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
    public List<String> searchReservationIdsInLatestCalendar( String query, int maxResults ) {
        String q = StringUtils.trimToNull( query );
        if ( q == null || maxResults <= 0 ) {
            return Collections.emptyList();
        }

        Integer jobId = getLastCompletedAllocationScraperJobId();
        if ( jobId == null ) {
            LOGGER.info( "No completed allocation scrape; skipping calendar search for query={}", q );
            return Collections.emptyList();
        }

        // Tokenize name: "Jane Smith" → "%jane%smith%"
        String namePattern = "%" + q.toLowerCase().replaceAll( "\\s+", "%" ) + "%";

        int capped = Math.min( maxResults, 100 );
        // Prefer one representative row per reservation (lowest id) without CTEs/window funcs.
        @SuppressWarnings( "unchecked" )
        List<Number> reservationIds = em.createNativeQuery(
                "SELECT a.reservation_id FROM wp_lh_calendar a "
                        + " WHERE a.job_id = :jobId "
                        + "   AND a.reservation_id > 0 "
                        + "   AND a.id = ("
                        + "         SELECT MIN(a2.id) FROM wp_lh_calendar a2 "
                        + "          WHERE a2.job_id = a.job_id "
                        + "            AND a2.reservation_id = a.reservation_id"
                        + "       ) "
                        + "   AND ("
                        + "         LOWER(IFNULL(a.guest_name, '')) LIKE :namePattern "
                        + "      OR LOWER(IFNULL(a.booking_reference, '')) = :exact "
                        + "      OR CAST(a.reservation_id AS CHAR) = :exact"
                        + "       ) "
                        + " ORDER BY a.checkin_date DESC, a.reservation_id "
                        + " LIMIT " + capped )
                .setParameter( "jobId", jobId )
                .setParameter( "namePattern", namePattern )
                .setParameter( "exact", q.toLowerCase() )
                .getResultList();

        List<String> out = new ArrayList<>( reservationIds.size() );
        for ( Number id : reservationIds ) {
            if ( id != null ) {
                out.add( String.valueOf( id.longValue() ) );
            }
        }
        LOGGER.info( "Calendar search jobId={} query={} matches={}", jobId, q, out.size() );
        return out;
    }

    @Override
    @Transactional( propagation = Propagation.NOT_SUPPORTED )
    public void updateGuestCommentsForReservations( List<GuestCommentReportEntry> comments ) {
        if ( comments == null || comments.isEmpty() ) {
            return;
        }
        TransactionTemplate tt = new TransactionTemplate( transactionManager );
        tt.setPropagationBehavior( TransactionDefinition.PROPAGATION_REQUIRES_NEW );
        tt.setTimeout( CHUNK_TX_TIMEOUT_SECONDS );
        int totalUpdated = 0;
        for ( int from = 0 ; from < comments.size() ; from += GUEST_COMMENT_UPSERT_BATCH_SIZE ) {
            int to = Math.min( from + GUEST_COMMENT_UPSERT_BATCH_SIZE, comments.size() );
            List<GuestCommentReportEntry> batch = comments.subList( from, to );
            tt.executeWithoutResult( status -> {
                Query q = em.createNativeQuery( "INSERT INTO wp_lh_rpt_guest_comments ( reservation_id, comments ) "
                        + " VALUES " + StringUtils.repeat( "( ?, ? )", ",", batch.size() )
                        + " ON DUPLICATE KEY UPDATE "
                        + " reservation_id = VALUES( reservation_id ), "
                        + " comments = VALUES( comments )" );
                q.setHint( "jakarta.persistence.query.timeout", CHUNK_TX_TIMEOUT_SECONDS * 1000 );
                for ( int i = 0 ; i < batch.size() ; i++ ) {
                    GuestCommentReportEntry entry = batch.get( i );
                    q.setParameter( 2 * i + 1, entry.getReservationId() );
                    q.setParameter( 2 * i + 2, entry.getComments() );
                }
                q.executeUpdate();
            } );
            totalUpdated += batch.size();
            LOGGER.info( "Updated {}/{} guest comments.", totalUpdated, comments.size() );
        }
    }

    @SuppressWarnings( "unchecked" )
    private String loadOptionFromDb( String property ) {
        List<String> sqlResult = em.createNativeQuery(
                        "          SELECT option_value"
                                + "  FROM wp_options"
                                + " WHERE option_name = :optionName " )
                .setParameter( "optionName", property )
                .getResultList();
        return sqlResult.isEmpty() ? null : sqlResult.get( 0 );
    }

    @Override
    @Transactional( readOnly = true )
    public String getOption( String property ) {
        String cached = wpOptionsCache.getIfPresent( property );
        if ( cached != null ) {
            return cached;
        }
        String fromDb = loadOptionFromDb( property );
        if ( fromDb != null ) {
            wpOptionsCache.put( property, fromDb );
        }
        return fromDb;
    }

    @Override
    public String getDefaultOption( String property, String defaultValue ) {
        String option = getOption( property );
        return option == null ? defaultValue : option;
    }

    @Override
    @Transactional( readOnly = true )
    public String getOptionNoCache( String property ) {
        String value = loadOptionFromDb( property );
        if ( value != null ) {
            wpOptionsCache.put( property, value );
        } else {
            wpOptionsCache.invalidate( property );
        }
        return value;
    }

    @Override
    public String getCsrfToken() {
        if ( csrfToken == null ) {
            String cookies = getOption( "hbo_cloudbeds_cookies" );
            Pattern p = Pattern.compile( "csrf_accessa_cookie=([0-9a-f]+)" );
            Matcher m = p.matcher( cookies );
            if ( false == m.find() ) {
                throw new MissingUserDataException( "Missing CSRF cookie??" );
            }
            csrfToken = m.group( 1 );
        }
        return csrfToken;
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
        return Integer.parseInt( getOptionNoCache( "hbo_group_booking_size" ) );
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
                "   INSERT INTO wp_options(option_name, option_value) "
                + " VALUES (:name, :value) "
                + " ON DUPLICATE KEY "
                + " UPDATE option_name = :name, option_value = :value")
            .setParameter( "name", property )
            .setParameter( "value", value )
            .executeUpdate();
        wpOptionsCache.put( property, value );
        if ( "hbo_cloudbeds_cookies".equals( property ) ) {
            csrfToken = null;
        }
    }

    @Override
    public void invalidateOptionsCache() {
        wpOptionsCache.invalidateAll();
        csrfToken = null;
        LOGGER.info( "Options cache invalidated." );
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
    public void updateStripeTransaction( int id, String paymentStatus, String authStatus, String authStatusDetail, String chargeId, String cardType, String last4Digits ) {
        StripeTransaction txn = em.find( StripeTransaction.class, id );
        if ( txn == null ) {
            throw new EmptyResultDataAccessException( "Unable to find StripeTransaction with ID " + id, 1 );
        }
        txn.setPaymentStatus( paymentStatus );
        txn.setAuthStatus( authStatus );
        txn.setAuthStatusDetail( authStatusDetail );
        txn.setChargeId( chargeId );
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
    @SuppressWarnings( "unchecked" )
    public String findLatestBookingLookupKey( String reservationId ) {
        List<String> keys = em.createNativeQuery(
                        "SELECT lookup_key FROM wp_booking_lookup_key "
                                + " WHERE reservation_id = :reservationId "
                                + " ORDER BY created_date DESC" )
                .setParameter( "reservationId", reservationId )
                .setMaxResults( 1 )
                .getResultList();
        return keys.isEmpty() ? null : keys.get( 0 );
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

    @Override
    @Transactional( readOnly = true )
    public List<OccupancyVersion> fetchCurrentOccupancy() {
        return em.createQuery(
                "FROM OccupancyVersion o WHERE o.validTo IS NULL", OccupancyVersion.class )
                .getResultList();
    }

    @Override
    @Transactional( readOnly = true )
    public OccupancyVersion fetchCurrentOccupancyByAssignmentKey( String assignmentKey ) {
        List<OccupancyVersion> currents = listCurrentOccupancyByAssignmentKey( assignmentKey );
        return currents.isEmpty() ? null : currents.get( 0 );
    }

    private List<OccupancyVersion> listCurrentOccupancyByAssignmentKey( String assignmentKey ) {
        if ( StringUtils.isBlank( assignmentKey ) ) {
            return Collections.emptyList();
        }
        return em.createQuery(
                "FROM OccupancyVersion o WHERE o.assignmentKey = :key AND o.validTo IS NULL",
                OccupancyVersion.class )
                .setParameter( "key", assignmentKey )
                .getResultList();
    }

    @Override
    @Transactional( readOnly = true )
    public OccupancyVersion fetchCurrentOccupancyByCalendarEventId( String calendarEventId ) {
        List<OccupancyVersion> currents = listCurrentOccupancyByCalendarEventId( calendarEventId );
        return currents.isEmpty() ? null : currents.get( 0 );
    }

    private List<OccupancyVersion> listCurrentOccupancyByCalendarEventId( String calendarEventId ) {
        if ( StringUtils.isBlank( calendarEventId ) ) {
            return Collections.emptyList();
        }
        return em.createQuery(
                "FROM OccupancyVersion o WHERE o.calendarEventId = :eid AND o.validTo IS NULL",
                OccupancyVersion.class )
                .setParameter( "eid", calendarEventId )
                .getResultList();
    }

    @Override
    @Transactional
    public boolean upsertOccupancyVersion( OccupancyVersion next ) {
        if ( next == null || StringUtils.isBlank( next.getAssignmentKey() ) ) {
            return false;
        }
        List<OccupancyVersion> currents = listCurrentOccupancyByAssignmentKey( next.getAssignmentKey() );
        OccupancyVersion current = currents.isEmpty() ? null : currents.get( 0 );
        next.preserveCalendarEventIdFrom( current );
        // Single matching current: no version. Multiple currents: heal by closing all and rewriting.
        if ( currents.size() == 1 && false == currents.get( 0 ).differsForVersioning( next ) ) {
            return false;
        }
        Timestamp now = new Timestamp( System.currentTimeMillis() );
        for ( OccupancyVersion cur : currents ) {
            cur.setValidTo( now );
            em.merge( cur );
        }
        next.setId( 0 );
        next.setValidFrom( now );
        next.setValidTo( null );
        em.persist( next );
        return true;
    }

    @Override
    @Transactional
    public void closeOccupancyVersion( String assignmentKey ) {
        if ( StringUtils.isBlank( assignmentKey ) ) {
            return;
        }
        Timestamp now = new Timestamp( System.currentTimeMillis() );
        for ( OccupancyVersion current : listCurrentOccupancyByAssignmentKey( assignmentKey ) ) {
            current.setValidTo( now );
            em.merge( current );
        }
    }

    @Override
    @Transactional
    public void closeOccupancyVersionByCalendarEventId( String calendarEventId ) {
        if ( StringUtils.isBlank( calendarEventId ) ) {
            return;
        }
        Timestamp now = new Timestamp( System.currentTimeMillis() );
        for ( OccupancyVersion current : listCurrentOccupancyByCalendarEventId( calendarEventId ) ) {
            current.setValidTo( now );
            em.merge( current );
        }
    }

    @Override
    @Transactional( timeout = BULK_PERSIST_TX_TIMEOUT_SECONDS )
    public void reconcileOccupancyCurrents( List<OccupancyVersion> desiredCurrents ) {
        Map<String, OccupancyVersion> desiredByKey = new HashMap<>();
        if ( desiredCurrents != null ) {
            for ( OccupancyVersion d : desiredCurrents ) {
                if ( d != null && StringUtils.isNotBlank( d.getAssignmentKey() ) ) {
                    desiredByKey.put( d.getAssignmentKey(), d );
                }
            }
        }
        List<OccupancyVersion> existing = fetchCurrentOccupancy();
        Timestamp now = new Timestamp( System.currentTimeMillis() );
        for ( OccupancyVersion cur : existing ) {
            OccupancyVersion desired = desiredByKey.get( cur.getAssignmentKey() );
            if ( desired == null ) {
                cur.setValidTo( now );
                em.merge( cur );
            }
            else {
                desired.preserveCalendarEventIdFrom( cur );
                if ( cur.differsForVersioning( desired ) ) {
                    cur.setValidTo( now );
                    em.merge( cur );
                    desired.setId( 0 );
                    desired.setValidFrom( now );
                    desired.setValidTo( null );
                    em.persist( desired );
                    desiredByKey.remove( cur.getAssignmentKey() );
                }
                else {
                    desiredByKey.remove( cur.getAssignmentKey() );
                }
            }
        }
        for ( OccupancyVersion remaining : desiredByKey.values() ) {
            remaining.setId( 0 );
            remaining.setValidFrom( now );
            remaining.setValidTo( null );
            em.persist( remaining );
        }
    }

    @Override
    @Transactional( readOnly = true )
    public List<RoomBed> fetchActiveHousekeepingRooms() {
        return em.createQuery(
                "FROM RoomBed r WHERE r.active = 'Y' AND r.room <> 'Unallocated' ORDER BY r.room, r.bedName",
                RoomBed.class )
                .getResultList();
    }

    @Override
    @Transactional( timeout = BULK_PERSIST_TX_TIMEOUT_SECONDS )
    public void replaceHousekeepingBeds( List<HousekeepingBed> beds ) {
        em.createQuery( "DELETE FROM HousekeepingBed" ).executeUpdate();
        if ( beds == null || beds.isEmpty() ) {
            return;
        }
        Timestamp now = new Timestamp( System.currentTimeMillis() );
        for ( HousekeepingBed bed : beds ) {
            if ( bed.getUpdatedAt() == null ) {
                bed.setUpdatedAt( now );
            }
            em.persist( bed );
        }
    }

    @Override
    @Transactional( readOnly = true )
    public List<HousekeepingBed> fetchHousekeepingBeds() {
        return em.createQuery(
                "FROM HousekeepingBed b ORDER BY b.room, b.bedName", HousekeepingBed.class )
                .getResultList();
    }

    // --- Booking assignment SCD2 ---

    @Override
    @Transactional( readOnly = true )
    public List<BookingAssignment> fetchCurrentBookingAssignments() {
        return em.createQuery(
                "FROM BookingAssignment a WHERE a.validTo IS NULL", BookingAssignment.class )
                .getResultList();
    }

    @Override
    @Transactional( readOnly = true )
    public List<BookingAssignment> fetchCurrentBookingAssignmentsCheckingOutAfter( LocalDate date ) {
        return em.createQuery(
                "FROM BookingAssignment a WHERE a.validTo IS NULL AND a.checkoutDate > :date",
                BookingAssignment.class )
                .setParameter( "date", java.sql.Date.valueOf( date ) )
                .getResultList();
    }

    private List<BookingAssignment> fetchCurrentBookingAssignmentsForReconcile( LocalDate windowStart,
            Set<String> desiredKeys ) {
        if ( desiredKeys.isEmpty() ) {
            return fetchCurrentBookingAssignmentsCheckingOutAfter( windowStart );
        }
        return em.createQuery(
                "FROM BookingAssignment a WHERE a.validTo IS NULL "
                        + "AND ( a.checkoutDate > :date OR a.assignmentKey IN (:keys) )",
                BookingAssignment.class )
                .setParameter( "date", java.sql.Date.valueOf( windowStart ) )
                .setParameter( "keys", desiredKeys )
                .getResultList();
    }

    @Override
    @Transactional( readOnly = true )
    public Set<Long> fetchBookingAssignmentReservationIds() {
        return new HashSet<>( em.createQuery(
                "SELECT DISTINCT a.reservationId FROM BookingAssignment a WHERE a.reservationId IS NOT NULL",
                Long.class ).getResultList() );
    }

    @Override
    @Transactional( propagation = Propagation.NOT_SUPPORTED )
    public int insertBookingAssignments( List<BookingAssignment> rows ) {
        if ( rows == null || rows.isEmpty() ) {
            return 0;
        }
        TransactionTemplate tt = new TransactionTemplate( transactionManager );
        tt.setPropagationBehavior( TransactionDefinition.PROPAGATION_REQUIRES_NEW );
        tt.setTimeout( CHUNK_TX_TIMEOUT_SECONDS );
        int totalInserted = 0;
        for ( List<BookingAssignment> chunk : BookingAssignment.chunkByReservation( rows, ALLOCATION_INSERT_BATCH_SIZE ) ) {
            long started = System.currentTimeMillis();
            Integer inserted = tt.execute( status -> {
                // a live writer may have created the reservation since the caller's skip check
                Set<Long> ids = chunk.stream().map( BookingAssignment::getReservationId )
                        .filter( id -> id != null ).collect( Collectors.toSet() );
                Set<Long> existing = ids.isEmpty() ? Collections.emptySet() : new HashSet<>( em.createQuery(
                        "SELECT DISTINCT a.reservationId FROM BookingAssignment a WHERE a.reservationId IN (:ids)",
                        Long.class ).setParameter( "ids", ids ).getResultList() );
                List<BookingAssignment> toInsert = chunk.stream()
                        .filter( a -> false == existing.contains( a.getReservationId() ) )
                        .collect( Collectors.toList() );
                if ( toInsert.isEmpty() ) {
                    return 0;
                }
                Query q = em.createNativeQuery( BookingAssignment.getBulkInsertStatement( toInsert.size() ) );
                q.setHint( "jakarta.persistence.query.timeout", CHUNK_TX_TIMEOUT_SECONDS * 1000 );
                for ( int i = 0 ; i < toInsert.size() ; i++ ) {
                    Object[] params = toInsert.get( i ).getInsertParameters();
                    for ( int j = 0 ; j < params.length ; j++ ) {
                        q.setParameter( i * params.length + j + 1, params[j] );
                    }
                }
                return q.executeUpdate();
            } );
            totalInserted += inserted == null ? 0 : inserted;
            LOGGER.info( "Inserted {}/{} booking assignment rows ({} ms for chunk of {}).",
                    totalInserted, rows.size(), System.currentTimeMillis() - started, chunk.size() );
        }
        return totalInserted;
    }

    @Override
    @Transactional( readOnly = true )
    public BookingAssignment fetchCurrentBookingAssignmentByKey( String assignmentKey ) {
        List<BookingAssignment> currents = listCurrentBookingAssignmentByKey( assignmentKey );
        return currents.isEmpty() ? null : currents.get( 0 );
    }

    private List<BookingAssignment> listCurrentBookingAssignmentByKey( String assignmentKey ) {
        if ( StringUtils.isBlank( assignmentKey ) ) {
            return Collections.emptyList();
        }
        return em.createQuery(
                "FROM BookingAssignment a WHERE a.assignmentKey = :key AND a.validTo IS NULL",
                BookingAssignment.class )
                .setParameter( "key", assignmentKey )
                .getResultList();
    }

    @Override
    @Transactional( readOnly = true )
    public BookingAssignment fetchCurrentBookingAssignmentByCalendarEventId( String calendarEventId ) {
        List<BookingAssignment> currents = listCurrentBookingAssignmentByCalendarEventId( calendarEventId );
        return currents.isEmpty() ? null : currents.get( 0 );
    }

    private List<BookingAssignment> listCurrentBookingAssignmentByCalendarEventId( String calendarEventId ) {
        if ( StringUtils.isBlank( calendarEventId ) ) {
            return Collections.emptyList();
        }
        return em.createQuery(
                "FROM BookingAssignment a WHERE a.calendarEventId = :eid AND a.validTo IS NULL",
                BookingAssignment.class )
                .setParameter( "eid", calendarEventId )
                .getResultList();
    }

    @Override
    @Transactional
    public boolean upsertBookingAssignment( BookingAssignment next ) {
        if ( next == null || StringUtils.isBlank( next.getAssignmentKey() ) ) {
            return false;
        }
        List<BookingAssignment> currents = listCurrentBookingAssignmentByKey( next.getAssignmentKey() );
        BookingAssignment current = currents.isEmpty() ? null : currents.get( 0 );
        next.preserveCalendarEventIdFrom( current );
        if ( currents.size() == 1 && false == currents.get( 0 ).differsForVersioning( next ) ) {
            return false;
        }
        Timestamp now = new Timestamp( System.currentTimeMillis() );
        if ( current != null ) {
            next.carryFolioFrom( current );
        }
        for ( BookingAssignment cur : currents ) {
            cur.setValidTo( now );
            em.merge( cur );
        }
        next.setId( 0 );
        next.setValidFrom( now );
        next.setValidTo( null );
        em.persist( next );
        return true;
    }

    @Override
    @Transactional
    public void closeBookingAssignment( String assignmentKey ) {
        if ( StringUtils.isBlank( assignmentKey ) ) {
            return;
        }
        Timestamp now = new Timestamp( System.currentTimeMillis() );
        for ( BookingAssignment current : listCurrentBookingAssignmentByKey( assignmentKey ) ) {
            current.setValidTo( now );
            em.merge( current );
        }
    }

    @Override
    @Transactional
    public void closeBookingAssignmentByCalendarEventId( String calendarEventId ) {
        if ( StringUtils.isBlank( calendarEventId ) ) {
            return;
        }
        Timestamp now = new Timestamp( System.currentTimeMillis() );
        for ( BookingAssignment current : listCurrentBookingAssignmentByCalendarEventId( calendarEventId ) ) {
            current.setValidTo( now );
            em.merge( current );
        }
    }

    @Override
    @Transactional( readOnly = true )
    public List<BookingAssignment> fetchCurrentBookingAssignmentsForReservation( long reservationId ) {
        return em.createQuery(
                "FROM BookingAssignment a WHERE a.reservationId = :rid AND a.source = :guest AND a.validTo IS NULL",
                BookingAssignment.class )
                .setParameter( "rid", reservationId )
                .setParameter( "guest", BookingAssignment.SOURCE_GUEST )
                .getResultList();
    }

    @Override
    @Transactional
    public int closeBookingAssignmentsForReservation( long reservationId ) {
        return em.createQuery( "UPDATE BookingAssignment a SET a.validTo = :now "
                + "WHERE a.reservationId = :rid AND a.source = :guest AND a.validTo IS NULL" )
                .setParameter( "now", new Timestamp( System.currentTimeMillis() ) )
                .setParameter( "rid", reservationId )
                .setParameter( "guest", BookingAssignment.SOURCE_GUEST )
                .executeUpdate();
    }

    /** True when the assignment stay overlaps {@code [windowStart, windowEnd]} (null bounds = open). */
    private static boolean overlapsWindow( BookingAssignment a, LocalDate windowStart, LocalDate windowEnd ) {
        LocalDate checkin = a.getCheckinLocalDate();
        LocalDate checkout = a.getCheckoutLocalDate();
        if ( windowEnd != null && checkin != null && checkin.isAfter( windowEnd ) ) {
            return false;
        }
        if ( windowStart != null && checkout != null && false == checkout.isAfter( windowStart ) ) {
            return false;
        }
        return true;
    }

    @Override
    @Transactional( propagation = Propagation.NOT_SUPPORTED )
    public void reconcileBookingAssignmentCurrents( List<BookingAssignment> desiredCurrents,
            LocalDate windowStart, LocalDate windowEnd ) {
        Map<String, BookingAssignment> desiredByKey = new HashMap<>();
        if ( desiredCurrents != null ) {
            for ( BookingAssignment d : desiredCurrents ) {
                if ( d != null && StringUtils.isNotBlank( d.getAssignmentKey() ) ) {
                    desiredByKey.put( d.getAssignmentKey(), d );
                }
            }
        }
        // currents checking out on/before windowStart are kept anyway unless the snapshot has their key
        List<BookingAssignment> existing = windowStart == null
                ? fetchCurrentBookingAssignments()
                : fetchCurrentBookingAssignmentsForReconcile( windowStart, desiredByKey.keySet() );
        Timestamp now = new Timestamp( System.currentTimeMillis() );

        // each change is (current row id to close, new row to insert); either side may be absent
        List<Long> closeIds = new ArrayList<>();
        List<BookingAssignment> inserts = new ArrayList<>();
        int keptOutsideWindow = 0;
        for ( BookingAssignment cur : existing ) {
            BookingAssignment desired = desiredByKey.remove( cur.getAssignmentKey() );
            if ( desired == null ) {
                if ( false == overlapsWindow( cur, windowStart, windowEnd ) ) {
                    keptOutsideWindow++;
                    continue;
                }
                closeIds.add( cur.getId() );
                inserts.add( null );
                continue;
            }
            desired.preserveCalendarEventIdFrom( cur );
            if ( cur.differsForVersioning( desired ) ) {
                desired.carryFolioFrom( cur );
                closeIds.add( cur.getId() );
                inserts.add( desired );
            }
        }
        for ( BookingAssignment remaining : desiredByKey.values() ) {
            closeIds.add( null );
            inserts.add( remaining );
        }
        if ( keptOutsideWindow > 0 ) {
            LOGGER.info( "BookingAssignment reconcile: kept {} currents outside snapshot window {} - {}",
                    keptOutsideWindow, windowStart, windowEnd );
        }
        if ( inserts.isEmpty() ) {
            LOGGER.info( "BookingAssignment reconcile: no changes ({} currents)", existing.size() );
            return;
        }

        // one short transaction per chunk: a single long one exceeds the tx timeout on slow DB links;
        // a close and its replacement insert stay in the same chunk so an assignment is never missing
        TransactionTemplate tt = new TransactionTemplate( transactionManager );
        tt.setPropagationBehavior( TransactionDefinition.PROPAGATION_REQUIRES_NEW );
        tt.setTimeout( CHUNK_TX_TIMEOUT_SECONDS );
        int applied = 0;
        int failedChunks = 0;
        long started = System.currentTimeMillis();
        for ( int from = 0 ; from < inserts.size() ; from += BOOKING_ASSIGNMENT_RECONCILE_CHUNK_SIZE ) {
            int to = Math.min( from + BOOKING_ASSIGNMENT_RECONCILE_CHUNK_SIZE, inserts.size() );
            List<Long> chunkCloseIds = new ArrayList<>();
            List<BookingAssignment> chunkInserts = new ArrayList<>();
            for ( int i = from ; i < to ; i++ ) {
                if ( closeIds.get( i ) != null ) {
                    chunkCloseIds.add( closeIds.get( i ) );
                }
                if ( inserts.get( i ) != null ) {
                    chunkInserts.add( inserts.get( i ) );
                }
            }
            try {
                tt.executeWithoutResult( status -> {
                    if ( false == chunkCloseIds.isEmpty() ) {
                        em.createQuery( "UPDATE BookingAssignment a SET a.validTo = :now "
                                + "WHERE a.id IN (:ids) AND a.validTo IS NULL" )
                                .setParameter( "now", now )
                                .setParameter( "ids", chunkCloseIds )
                                .executeUpdate();
                    }
                    for ( BookingAssignment a : chunkInserts ) {
                        a.setId( 0 );
                        a.setValidFrom( now );
                        a.setValidTo( null );
                        em.persist( a );
                    }
                } );
                applied += to - from;
            }
            catch ( RuntimeException ex ) {
                failedChunks++;
                LOGGER.error( "BookingAssignment reconcile chunk {}-{} failed; next snapshot will retry: {}",
                        from, to, ex.toString() );
            }
        }
        LOGGER.info( "BookingAssignment reconcile: applied {}/{} changes ({} closes, {} inserts) in {} ms; {} failed chunks",
                applied, inserts.size(), closeIds.stream().filter( id -> id != null ).count(),
                inserts.stream().filter( a -> a != null ).count(), System.currentTimeMillis() - started, failedChunks );
    }

    @Override
    @Transactional
    public boolean patchBookingAssignmentFolio( String assignmentKey, BookingAssignment folio ) {
        if ( StringUtils.isBlank( assignmentKey ) || folio == null ) {
            return false;
        }
        BookingAssignment current = fetchCurrentBookingAssignmentByKey( assignmentKey );
        if ( current == null ) {
            return false;
        }
        current.applyFolioFrom( folio );
        current.setLastRestFetchedAt( new Timestamp( System.currentTimeMillis() ) );
        em.merge( current );
        return true;
    }

    @Override
    @Transactional( readOnly = true )
    public List<BookingAssignment> fetchBookingAssignmentsNeedingRestEnrich() {
        return em.createQuery(
                "FROM BookingAssignment a WHERE a.validTo IS NULL AND a.lastRestFetchedAt IS NULL "
                        + "AND a.source = :guest AND a.reservationId IS NOT NULL AND a.reservationId > 0",
                BookingAssignment.class )
                .setParameter( "guest", BookingAssignment.SOURCE_GUEST )
                .getResultList();
    }

    @Override
    @Transactional( readOnly = true )
    public List<Long> fetchReservationIdsNeedingRestEnrich() {
        return em.createQuery(
                "SELECT DISTINCT a.reservationId FROM BookingAssignment a "
                        + "WHERE a.validTo IS NULL AND a.lastRestFetchedAt IS NULL "
                        + "AND a.source = :guest AND a.reservationId IS NOT NULL AND a.reservationId > 0",
                Long.class )
                .setParameter( "guest", BookingAssignment.SOURCE_GUEST )
                .getResultList();
    }

    @Override
    @Transactional( readOnly = true )
    public List<BookingSourceLookup> fetchCommissionRates( LocalDate asOf ) {
        return em.createQuery(
                "FROM BookingSourceLookup b WHERE b.validFrom <= :asOf "
                        + "AND (b.validTo IS NULL OR b.validTo >= :asOf) ORDER BY b.source",
                BookingSourceLookup.class )
                .setParameter( "asOf", asOf )
                .getResultList();
    }

}
