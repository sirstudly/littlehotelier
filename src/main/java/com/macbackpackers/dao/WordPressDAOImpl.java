
package com.macbackpackers.dao;

import java.math.BigInteger;
import java.sql.Timestamp;
import java.util.Date;
import java.util.List;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.hibernate.SessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.IncorrectResultSetColumnCountException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.macbackpackers.beans.Allocation;
import com.macbackpackers.beans.AllocationList;
import com.macbackpackers.beans.HostelworldBooking;
import com.macbackpackers.beans.Job;
import com.macbackpackers.beans.JobStatus;
import com.macbackpackers.beans.ScheduledJob;
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
        sessionFactory.getCurrentSession().save( job );
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
    public void updateJobStatus( int jobId, JobStatus status ) {
        updateJobStatus( fetchJobById( jobId ), status );
    }

    private void updateJobStatus( Job job, JobStatus status ) {
        if ( status == JobStatus.processing ) {
            job.setJobStartDate( new Timestamp( System.currentTimeMillis() ) );
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
                        + "       lastUpdatedDate = NOW()"
                        + " WHERE status = :processing" )
                .setParameter( "failed", JobStatus.failed )
                .setParameter( "processing", JobStatus.processing )
                .executeUpdate();
    }

    @Override
    public AbstractJob getNextJobToProcess() {
        // shutdown job has priority if present
        List<?> jobIds = sessionFactory.getCurrentSession()
                .createQuery( "SELECT id FROM AbstractJob WHERE status = :status "
                        + " ORDER BY CASE WHEN classname = 'com.macbackpackers.jobs.ShutdownJob' THEN 0 ELSE 1 END, job_id" )
                .setParameter( "status", JobStatus.submitted ).getResultList();
        Integer jobId = jobIds.isEmpty() ? null : Integer.class.cast( jobIds.get( 0 ));
        LOGGER.info( "Next job to process: " + (jobId == null ? "none" : jobId) );
        return jobId == null ? null : fetchJobById( jobId );
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
    public <T extends AbstractJob> T getLastCompletedJobOfType( Class<T> jobType ) {
        List<?> jobIds = sessionFactory.getCurrentSession()
                .createQuery( "SELECT MAX(id) FROM AbstractJob WHERE classname = :classname AND status = :status" )
                .setParameter( "classname", jobType.getName() )
                .setParameter( "status", JobStatus.completed )
                .getResultList();
        Integer jobId = jobIds.isEmpty() ? null : Integer.class.cast( jobIds.get( 0 ));
        LOGGER.info( "Next job to process: " + (jobId == null ? "none" : jobId) );
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
        sessionFactory.getCurrentSession()
                .createNativeQuery( sql.getProperty( "reservations.split.rooms" ) )
                .setParameter( "jobId", allocationScraperJobId )
                .executeUpdate();
    }

    @Override
    public void runUnpaidDepositReport( int allocationScraperJobId ) {
        LOGGER.info( "Running report for job id: " + allocationScraperJobId );
        sessionFactory.getCurrentSession()
                .createNativeQuery( sql.getProperty( "unpaid.deposit.report" ) )
                .setParameter( 0, allocationScraperJobId )
                .executeUpdate();
    }

    @Override
    public void runGroupBookingsReport( int allocationScraperJobId ) {
        LOGGER.info( "Running report for job id: " + allocationScraperJobId );
        sessionFactory.getCurrentSession()
                .createNativeQuery( sql.getProperty( "group.bookings" ) )
                .setParameter( "jobId", allocationScraperJobId )
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
    @SuppressWarnings( "unchecked" )
    public List<BigInteger> getReservationIdsWithoutEntryInGuestCommentsReport( int allocationScraperJobId ) {
        List<BigInteger> reservationIds = sessionFactory.getCurrentSession().createNativeQuery(
                "          SELECT c.reservation_id "
                        + "  FROM wp_lh_calendar c "
                        + "  LEFT OUTER JOIN wp_lh_rpt_guest_comments r "
                        + "    ON c.reservation_id = r.reservation_id "
                        + " WHERE c.job_id = :allocationScraperJobId "
                        + "   AND r.reservation_id IS NULL "
                        + "   AND c.reservation_id > 0" )
                .setParameter( "allocationScraperJobId", allocationScraperJobId )
                .getResultList();
        return reservationIds;
    }

    @Override
    public void updateGuestCommentsForReservation( BigInteger reservationId, String comment ) {
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
}
