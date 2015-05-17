package com.macbackpackers.dao;

import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import javax.sql.DataSource;

import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.SimpleJdbcInsert;
import org.springframework.jdbc.support.rowset.SqlRowSet;
import org.springframework.stereotype.Repository;

import com.macbackpackers.beans.Allocation;
import com.macbackpackers.beans.BedSheetEntry;
import com.macbackpackers.beans.Job;
import com.macbackpackers.beans.JobStatus;
import com.macbackpackers.dao.mappers.BedSheetRowMapper;
import com.macbackpackers.dao.mappers.JobRowMapper;
import com.macbackpackers.exceptions.IncorrectNumberOfRecordsUpdatedException;
import com.macbackpackers.jobs.AbstractJob;

@Repository
public class WordPressDAOImpl implements WordPressDAO {

    private final Logger LOGGER = LogManager.getLogger( getClass() );

    @Autowired
    @Qualifier("txnDataSource")
    private DataSource dataSource;
    
    @Autowired
    private JobRowMapper jobRowMapper;

    public DataSource getDataSource() {
        return dataSource;
    }
    
    private JdbcTemplate getJdbcTemplate() {
        return new JdbcTemplate( getDataSource() );
    }
    
    private SimpleJdbcInsert getSimpleJdbcInsert() {
        return new SimpleJdbcInsert( getDataSource() );
    }

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
    public List<BedSheetEntry> getAllBedSheetEntriesForDate( int jobId, Date date ) {
        List<BedSheetEntry> bedSheets = getJdbcTemplate().query(
                BedSheetRowMapper.QUERY_BY_JOB_ID_CHECKOUT_DATE,
                new BedSheetRowMapper(),
                jobId,
                new java.sql.Date( date.getTime() ) );
        return bedSheets;
    }

    public void insertBedSheetEntry( BedSheetEntry entry ) {
        getJdbcTemplate().update( 
                "INSERT INTO wp_bedsheets (job_id, room, bed_name, checkout_date, change_status)"
                + " VALUES ( ?, ?, ?, ?, ? )", 
                entry.getJobId(), entry.getRoom(), entry.getBedName(),
                new java.sql.Date( entry.getCheckoutDate().getTime() ), entry.getStatus().getValue() );
    }

    public void deleteAllBedSheetEntriesForJobId( int jobId ) {
        getJdbcTemplate().update( "DELETE FROM wp_bedsheets WHERE job_id = ?", jobId );
    }

    public void insertAllocation( Allocation alloc ) {

        Map<String, Object> parameters = new HashMap<String, Object>();
        parameters.put( "job_id", alloc.getJobId() );
        parameters.put( "room_id", alloc.getRoomId() );
        parameters.put( "room", alloc.getRoom() );
        parameters.put( "bed_name", alloc.getBedName() );
        parameters.put( "reservation_id", alloc.getReservationId() );
        parameters.put( "guest_name", alloc.getGuestName() );
        parameters.put( "checkin_date", alloc.getCheckinDate() );
        parameters.put( "checkout_date", alloc.getCheckoutDate() );
        parameters.put( "payment_total", alloc.getPaymentTotal() );
        parameters.put( "payment_outstanding", alloc.getPaymentOutstanding() );
        parameters.put( "rate_plan_name", alloc.getRatePlanName() );
        parameters.put( "payment_status", alloc.getPaymentStatus() );
        parameters.put( "num_guests", alloc.getNumberGuests() );
        parameters.put( "data_href", alloc.getDataHref() );
        parameters.put( "notes", alloc.getNotes() );

        alloc.setId( getSimpleJdbcInsert()
            .withTableName( "wp_lh_calendar" )
            .usingGeneratedKeyColumns( "id" )
            .executeAndReturnKey( parameters )
            .intValue() );
    }

    public int insertJob( Job job ) {
        Map<String, Object> parameters = new HashMap<String, Object>();
        parameters.put( "classname", job.getClassName() );
        parameters.put( "status", job.getStatus() );

        int jobId = getSimpleJdbcInsert()
            .withTableName( "wp_lh_jobs" )
            .usingGeneratedKeyColumns( "job_id" )
            .executeAndReturnKey( parameters )
            .intValue();
        insertJobParameters( jobId, job.getParameters() );
        return jobId;
    }
    
    private void insertJobParameters( int jobId, Properties parameters ) {
        for( Enumeration<Object> keys = parameters.keys(); keys.hasMoreElements(); ) {
            Object key = keys.nextElement();
            getJdbcTemplate().update( 
                    "INSERT INTO wp_lh_job_param( job_id, name, value ) VALUES ( ?, ?, ? )", 
                    jobId, key.toString(), parameters.get( key ).toString() );
        }
    }

    public void deleteAllJobData() {
        getJdbcTemplate().update( "DELETE FROM wp_lh_job_param" );
        getJdbcTemplate().update( "DELETE FROM wp_lh_jobs" );
    }

    public void deleteAllTransactionalData() {
        deleteAllJobData();
        getJdbcTemplate().update( "DELETE FROM wp_lh_calendar" );
    }

    public void updateJobStatus( int jobId, JobStatus status, JobStatus prevStatus ) {
        int rowsUpdated = getJdbcTemplate().update( 
                "UPDATE wp_lh_jobs SET status = ?, last_updated_date = NOW() "
                + " WHERE job_id = ? and status = ?", status.name(), jobId, prevStatus.name() );

        if ( rowsUpdated == 0 ) {
            throw new IncorrectNumberOfRecordsUpdatedException(
                    "Unable to update status of job " + jobId + " to " + status );
        }
    }

    public AbstractJob getNextJobToProcess() {
        Integer jobId = getJdbcTemplate().queryForObject( 
                "SELECT MIN(job_id) AS job_id FROM wp_lh_jobs WHERE status = ?",
                Integer.class, 
                JobStatus.submitted.name() );
        return jobId == null ? null : getJobById( jobId );
    }

    public AbstractJob getJobById( int id ) {
        return getJdbcTemplate().queryForObject( 
                JobRowMapper.QUERY_BY_JOB_ID, 
                jobRowMapper, 
                id );
    }
    
    public Properties getJobParameters( int jobId ) {
        Properties props = new Properties();
        SqlRowSet rowSet = getJdbcTemplate().queryForRowSet( 
                "SELECT name, value FROM wp_lh_job_param WHERE job_id = ?", jobId );
        while( rowSet.next() ) {
            props.setProperty( rowSet.getString( "name" ), rowSet.getString( "value" ) );
        }
        return props;
    }

}