
package com.macbackpackers.dao;

import java.sql.Timestamp;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.transaction.Transactional;

import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.hibernate.SessionFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.IncorrectResultSetColumnCountException;
import org.springframework.stereotype.Repository;

import com.macbackpackers.beans.Allocation;
import com.macbackpackers.beans.AllocationList;
import com.macbackpackers.beans.HostelworldBooking;
import com.macbackpackers.beans.Job;
import com.macbackpackers.beans.JobStatus;
import com.macbackpackers.beans.ScheduledJob;
import com.macbackpackers.exceptions.IncorrectNumberOfRecordsUpdatedException;
import com.macbackpackers.jobs.AbstractJob;

@Repository
public class WordPressDAOImpl implements WordPressDAO {

    private final Logger LOGGER = LogManager.getLogger( getClass() );

    @Autowired
    private SessionFactory sessionFactory;

    @Autowired
    @Qualifier( "reportsSQL" )
    private Properties sql;

    @Transactional
    @Override
    public void insertAllocation( Allocation alloc ) {
        sessionFactory.getCurrentSession().save( alloc );
    }

    @Transactional
    @Override
    public Allocation fetchAllocation( int id ) {
        Allocation alloc = (Allocation) sessionFactory.getCurrentSession().get( Allocation.class, id );
        if ( alloc == null ) {
            throw new EmptyResultDataAccessException( 1 );
        }
        return alloc;
    }

    @Transactional
    @Override
    public void updateAllocation( Allocation alloc ) {
        alloc.setCreatedDate( new Timestamp( System.currentTimeMillis() ) );
        sessionFactory.getCurrentSession().saveOrUpdate( alloc );
    }

    @Transactional
    @Override
    public void updateAllocationList( AllocationList allocList ) {
        allocList.setCreatedDate( new Timestamp( System.currentTimeMillis() ) );
        for ( Allocation a : allocList ) {
            sessionFactory.getCurrentSession().saveOrUpdate( a );
        }
    }

    @Transactional
    @SuppressWarnings( "unchecked" )
    @Override
    public AllocationList queryAllocationsByJobIdAndReservationId( int jobId, int reservationId ) {
        return new AllocationList( sessionFactory.getCurrentSession()
                .createQuery( "FROM Allocation WHERE jobId = :jobId AND reservationId = :reservationId" )
                .setParameter( "jobId", jobId )
                .setParameter( "reservationId", reservationId )
                .list() );
    }

    @Transactional
    @Override
    public int insertJob( Job job ) {
        sessionFactory.getCurrentSession().save( job );
        return job.getId();
    }

    @Transactional
    @Override
    public void updateJobStatus( int jobId, JobStatus status, JobStatus prevStatus ) {
        AbstractJob job = fetchJobById( jobId );
        if ( prevStatus != job.getStatus() ) {
            throw new IncorrectNumberOfRecordsUpdatedException(
                    "Previous job status is " + job.getStatus() + " when attempting to set to " + status );
        }
        updateJobStatus( job, status );
    }

    @Transactional
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

    @Transactional
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

    @Transactional
    @Override
    public AbstractJob getNextJobToProcess() {
        // shutdown job has priority if present
        Iterator<?> it = sessionFactory.getCurrentSession()
                .createQuery( "SELECT id FROM AbstractJob WHERE status = :status "
                        + " ORDER BY CASE WHEN classname = 'com.macbackpackers.jobs.ShutdownJob' THEN 0 ELSE 1 END, job_id" )
                .setParameter( "status", JobStatus.submitted ).iterate();
        Integer jobId = it.hasNext() ? (Integer) it.next() : null;
        LOGGER.info( "Next job to process: " + (jobId == null ? "none" : jobId) );
        return jobId == null ? null : fetchJobById( jobId );
    }

    @Transactional
    @Override
    public AbstractJob fetchJobById( int id ) {
        AbstractJob j = (AbstractJob) sessionFactory.getCurrentSession().get( AbstractJob.class, id );
        if ( j == null ) {
            throw new EmptyResultDataAccessException( 1 );
        }
        return j;
    }

    @Transactional
    @SuppressWarnings( "unchecked" )
    @Override
    public <T extends AbstractJob> T getLastCompletedJobOfType( Class<T> jobType ) {
        Integer jobId = (Integer) sessionFactory.getCurrentSession()
                .createQuery( "SELECT MAX(id) FROM AbstractJob WHERE classname = :classname AND status = :status" )
                .setParameter( "classname", jobType.getName() )
                .setParameter( "status", JobStatus.completed )
                .iterate().next();
        LOGGER.info( "Next job to process: " + (jobId == null ? "none" : jobId) );
        return jobId == null ? null : (T) fetchJobById( jobId );
    }

    @Transactional
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
                .list();
    }

    @Transactional
    @SuppressWarnings( "unchecked" )
    @Override
    public List<Integer> getHostelworldHostelBookersUnpaidDepositReservations( int allocationScraperJobId ) {
        LOGGER.info( "Querying unpaid reservations for allocation job : " + allocationScraperJobId );
        return sessionFactory.getCurrentSession().createQuery(
                "SELECT reservationId " +
                        "  FROM Allocation " +
                        "WHERE jobId = :jobId " +
                        "  AND paymentTotal = paymentOutstanding " +
                        "  AND bookingSource IN ( 'Hostelworld', 'Hostelbookers' ) " +
                        "GROUP BY reservationId" )
                .setParameter( "jobId", allocationScraperJobId )
                .list();
    }

    @Transactional
    @SuppressWarnings( "unchecked" )
    @Override
    public List<ScheduledJob> fetchActiveScheduledJobs() {
        return sessionFactory.getCurrentSession().createQuery(
                "FROM ScheduledJob WHERE active = true" )
                .list();
    }

    @Transactional
    @Override
    public ScheduledJob fetchScheduledJobById( int jobId ) {
        ScheduledJob j = (ScheduledJob) sessionFactory.getCurrentSession().get( ScheduledJob.class, jobId );
        if ( j == null ) {
            throw new EmptyResultDataAccessException( 1 );
        }
        return j;
    }

    @Transactional
    @Override
    public void updateScheduledJob( int jobId ) {
        ScheduledJob job = (ScheduledJob) sessionFactory.getCurrentSession().get( ScheduledJob.class, jobId );
        if ( job == null ) {
            throw new EmptyResultDataAccessException( 1 );
        }
        job.setLastScheduledDate( new Timestamp( System.currentTimeMillis() ) );
    }

    @Transactional
    @Override
    public void purgeRecordsOlderThan( Date specifiedDate ) {

        // delete from associated tables
        deleteFromTablesByJobId( specifiedDate,
                "wp_lh_rpt_split_rooms",
                "wp_lh_rpt_unpaid_deposit",
                "wp_lh_group_bookings",
                "wp_lh_calendar" );

        // delete from log4j
        int rowsDeleted = sessionFactory.getCurrentSession()
                .createSQLQuery( "DELETE FROM log4j_data WHERE date_logged < :specifiedDate" )
                .setParameter( "specifiedDate", specifiedDate )
                .executeUpdate();
        LOGGER.info( "Purge Job: deleted " + rowsDeleted + " records from log4j_data" );

        // delete from jobs
        deleteFromTablesByJobId( specifiedDate, "wp_lh_job_param" );

        rowsDeleted = sessionFactory.getCurrentSession()
                .createSQLQuery( "DELETE FROM wp_lh_jobs WHERE last_updated_date < :specifiedDate" )
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
                    .createSQLQuery( "DELETE FROM " + table + " WHERE job_id IN ( " + JOB_ID_SELECT + ")" )
                    .setParameter( "specifiedDate", specifiedDate )
                    .executeUpdate();
            LOGGER.info( "Purge Job: deleted " + rowsDeleted + " records from " + table );
        }
    }

    @Transactional
    @Override
    public void insertHostelworldBooking( HostelworldBooking booking ) {
        sessionFactory.getCurrentSession().save( booking );
    }

    @Transactional
    @Override
    public int getRoomTypeIdForHostelworldLabel( String roomTypeLabel ) {

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
        else if ( roomTypeLabel.contains( "3 Bed Private" ) ) {
            roomType = "TRIPLE";
        }
        else if ( roomTypeLabel.contains( "4 Bed Private" ) ) {
            roomType = "QUAD";
        }
        else {
            throw new IllegalArgumentException( "Unsupported room type, unable to determine type: " + roomTypeLabel );
        }

        if ( capacity == 0 ) {
            throw new IllegalArgumentException( "Unsupported room type, unable to determine capacity: " + roomTypeLabel );
        }

        @SuppressWarnings( "unchecked" )
        List<Integer> roomTypeIds = sessionFactory.getCurrentSession()
                .createSQLQuery( "SELECT DISTINCT room_type_id "
                        + "  FROM wp_lh_rooms "
                        + " WHERE room_type = :roomType "
                        + "   AND capacity = :capacity" )
                .setParameter( "roomType", roomType )
                .setParameter( "capacity", capacity )
                .list();

        if ( roomTypeIds.isEmpty() ) {
            throw new EmptyResultDataAccessException( 1 );
        }

        if ( roomTypeIds.size() > 1 ) {
            throw new IncorrectResultSetColumnCountException( 1, roomTypeIds.size() );
        }
        return roomTypeIds.get( 0 );
    }

    /////////////////////////////////////////////////////////////////////
    //    REPORTING SPECIFIC
    /////////////////////////////////////////////////////////////////////

    @Transactional
    @Override
    public void runSplitRoomsReservationsReport( int allocationScraperJobId ) {
        LOGGER.info( "Running report for job id: " + allocationScraperJobId );
        sessionFactory.getCurrentSession()
                .createSQLQuery( sql.getProperty( "reservations.split.rooms" ) )
                .setParameter( "jobId", allocationScraperJobId )
                .executeUpdate();
    }

    @Transactional
    @Override
    public void runUnpaidDepositReport( int allocationScraperJobId ) {
        LOGGER.info( "Running report for job id: " + allocationScraperJobId );
        sessionFactory.getCurrentSession()
                .createSQLQuery( sql.getProperty( "unpaid.deposit.report" ) )
                .setParameter( 0, allocationScraperJobId )
                .executeUpdate();
    }

    @Transactional
    @Override
    public void runGroupBookingsReport( int allocationScraperJobId ) {
        LOGGER.info( "Running report for job id: " + allocationScraperJobId );
        sessionFactory.getCurrentSession()
                .createSQLQuery( sql.getProperty( "group.bookings" ) )
                .setParameter( 0, allocationScraperJobId )
                .executeUpdate();
    }
}
