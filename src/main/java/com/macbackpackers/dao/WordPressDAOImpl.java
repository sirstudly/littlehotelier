
package com.macbackpackers.dao;

import java.sql.Timestamp;
import java.util.Date;
import java.util.List;
import java.util.Properties;

import javax.transaction.Transactional;

import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.hibernate.SessionFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.stereotype.Repository;

import com.macbackpackers.beans.Allocation;
import com.macbackpackers.beans.Job;
import com.macbackpackers.beans.JobStatus;
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
    public void insertAllocation( Allocation alloc ) {
        sessionFactory.getCurrentSession().save( alloc );
    }

    @Transactional
    public Allocation fetchAllocation( int id ) {
        Allocation alloc = (Allocation) sessionFactory.getCurrentSession().get( Allocation.class, id );
        if ( alloc == null ) {
            throw new EmptyResultDataAccessException( 1 );
        }
        return alloc;
    }

    @Transactional
    public void updateAllocation( Allocation alloc ) {
        alloc.setCreatedDate( new Timestamp( System.currentTimeMillis() ) );
        sessionFactory.getCurrentSession().saveOrUpdate( alloc );
    }

    @Transactional
    @SuppressWarnings( "unchecked" )
    public List<Allocation> queryAllocationsByJobIdAndReservationId( int jobId, int reservationId ) {
        return sessionFactory.getCurrentSession()
                .createQuery( "FROM Allocation WHERE jobId = :jobId AND reservationId = :reservationId" )
                .setParameter( "jobId", jobId )
                .setParameter( "reservationId", reservationId )
                .list();
    }

    @Transactional
    public int insertJob( Job job ) {
        sessionFactory.getCurrentSession().save( job );
        return job.getId();
    }

    @Transactional
    public void updateJobStatus( int jobId, JobStatus status, JobStatus prevStatus ) {
        AbstractJob job = fetchJobById( jobId );
        if ( prevStatus != job.getStatus() ) {
            throw new IncorrectNumberOfRecordsUpdatedException(
                    "Previous job status is " + job.getStatus() + " when attempting to set to " + status );
        }

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
    public AbstractJob getNextJobToProcess() {
        Integer jobId = (Integer) sessionFactory.getCurrentSession()
                .createQuery( "SELECT MIN(id) FROM AbstractJob WHERE status = :status " )
                .setParameter( "status", JobStatus.submitted ).iterate().next();
        LOGGER.info( "Next job to process: " + (jobId == null ? "none" : jobId) );
        return jobId == null ? null : fetchJobById( jobId );
    }

    @Transactional
    public AbstractJob fetchJobById( int id ) {
        AbstractJob j = (AbstractJob) sessionFactory.getCurrentSession().get( AbstractJob.class, id );
        if ( j == null ) {
            throw new EmptyResultDataAccessException( 1 );
        }
        return j;
    }

    @Transactional
    @SuppressWarnings( "unchecked" )
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
    public List<Date> getCheckinDatesForAllocationScraperJobId( int jobId ) {
        // dates from calendar for a given (allocation scraper) job id
        // do not include room closures
        return (List<Date>) sessionFactory.getCurrentSession().createQuery(
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

    /////////////////////////////////////////////////////////////////////
    //    REPORTING SPECIFIC
    /////////////////////////////////////////////////////////////////////

    @Transactional
    public void runSplitRoomsReservationsReport( int allocationScraperJobId ) {
        LOGGER.info( "Running report for job id: " + allocationScraperJobId );
        sessionFactory.getCurrentSession()
                .createSQLQuery( sql.getProperty( "reservations.split.rooms" ) )
                .setParameter( 0, allocationScraperJobId )
                .executeUpdate();
    }

    @Transactional
    public void runUnpaidDepositReport( int allocationScraperJobId ) {
        LOGGER.info( "Running report for job id: " + allocationScraperJobId );
        sessionFactory.getCurrentSession()
                .createSQLQuery( sql.getProperty( "unpaid.deposit.report" ) )
                .setParameter( 0, allocationScraperJobId )
                .executeUpdate();
    }

    @Transactional
    public void runGroupBookingsReport( int allocationScraperJobId ) {
        LOGGER.info( "Running report for job id: " + allocationScraperJobId );
        sessionFactory.getCurrentSession()
                .createSQLQuery( sql.getProperty( "group.bookings" ) )
                .setParameter( 0, allocationScraperJobId )
                .executeUpdate();
    }
}
