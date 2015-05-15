package com.macbackpackers.dao;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.sql.DataSource;

import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.SimpleJdbcInsert;
import org.springframework.stereotype.Repository;

import com.macbackpackers.beans.Allocation;
import com.macbackpackers.beans.BedSheetEntry;
import com.macbackpackers.beans.Job;
import com.macbackpackers.beans.JobStatus;
import com.macbackpackers.dao.mappers.BedSheetRowMapper;
import com.macbackpackers.dao.mappers.JobRowMapper;
import com.macbackpackers.exceptions.IncorrectNumberOfRecordsUpdatedException;

@Repository
public class WordPressDAOImpl implements WordPressDAO {

    private final Logger LOGGER = LogManager.getLogger( getClass() );

    @Autowired
    @Qualifier("txnDataSource")
    private DataSource dataSource;

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
        getJdbcTemplate().update( 
                "INSERT INTO wp_lh_calendar (job_id, room_id, room, bed_name, reservation_id, "
                + "guest_name, checkin_date, checkout_date, payment_total, payment_outstanding, "
                + "rate_plan_name, payment_status, num_guests, data_href )"
                + " VALUES ( ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ? )", alloc.getJobId(), alloc.getRoomId(),
                alloc.getRoom(), alloc.getBedName(), alloc.getReservationId(), alloc.getGuestName(),
                alloc.getCheckinDate(), alloc.getCheckoutDate(), alloc.getPaymentTotal(),
                alloc.getPaymentOutstanding(), alloc.getRatePlanName(), alloc.getPaymentStatus(),
                alloc.getNumberGuests(), alloc.getDataHref() );
    }

    public int insertJob( Job job ) {
        Map<String, Object> parameters = new HashMap<String, Object>();
        parameters.put( "name", job.getName() );
        parameters.put( "status", job.getStatus() );

        return getSimpleJdbcInsert()
            .withTableName( "wp_lh_jobs" )
            .usingGeneratedKeyColumns( "job_id" )
            .executeAndReturnKey( parameters )
            .intValue();
    }

    public void deleteAllJobData() throws Exception {
        getJdbcTemplate().update( "DELETE FROM wp_lh_jobs" );
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

    public Job getNextJobToProcess() {
        Integer jobId = getJdbcTemplate().queryForObject( 
                "SELECT MIN(job_id) AS job_id FROM wp_lh_jobs WHERE status = ?",
                Integer.class, 
                JobStatus.submitted.name() );
        return jobId == null ? null : getJobById( jobId );
    }

    public Job getJobById( int id ) {
        return getJdbcTemplate().queryForObject( 
                JobRowMapper.QUERY_BY_JOB_ID, 
                new JobRowMapper(), 
                id );
    }

}