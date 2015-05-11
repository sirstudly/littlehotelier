package com.macbackpackers.dao;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Date;
import java.util.List;

import javax.sql.DataSource;

import org.apache.commons.dbcp.BasicDataSource;
import org.apache.commons.dbutils.QueryRunner;
import org.apache.commons.dbutils.ResultSetHandler;
import org.apache.commons.dbutils.handlers.BeanListHandler;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.macbackpackers.beans.Allocation;
import com.macbackpackers.beans.BedSheetEntry;
import com.macbackpackers.beans.Job;
import com.macbackpackers.beans.JobStatus;
import com.macbackpackers.dao.mappers.BedSheetRowHandler;
import com.macbackpackers.dao.mappers.JobRowHandler;
import com.macbackpackers.exceptions.IncorrectNumberOfRecordsUpdatedException;

public class WordPressDAO {

    private final Logger logger = LogManager.getLogger( getClass() );

    private String prefix;
    private String hostname;
    private String port;
    private String instance;

    private DataSource dataSource;

    public WordPressDAO( String prefix, String hostname, String port, String instance ) {
        this.prefix = prefix;
        this.hostname = hostname;
        this.port = port;
        this.instance = instance;
    }

    public String getPrefix() {
        return prefix;
    }

    public void setPrefix( String prefix ) {
        this.prefix = prefix;
    }

    public String getHostname() {
        return hostname;
    }

    public void setHostname( String hostname ) {
        this.hostname = hostname;
    }

    public String getPort() {
        return port;
    }

    public void setPort( String port ) {
        this.port = port;
    }

    public String getInstance() {
        return instance;
    }

    public void setInstance( String instance ) {
        this.instance = instance;
    }

    public DataSource getDataSource() {
        return dataSource;
    }

    public void connect( String username, String password ) {
        // First we set up the BasicDataSource.
        // Normally this would be handled auto-magically by
        // an external configuration, but in this example we'll
        // do it manually.
        //
        logger.info( "Setting up data source." );
        dataSource = setupDataSource( prefix + hostname + ":" + port + "/" + instance
                + "?zeroDateTimeBehavior=convertToNull", username, password );
        logger.info( "Done." );
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
    public List<BedSheetEntry> getAllBedSheetEntriesForDate( int jobId, Date date ) throws SQLException {

        if ( getDataSource() == null ) {
            throw new IllegalStateException( "Database not initialised!" );
        }

        QueryRunner runner = new QueryRunner( getDataSource() );
        List<BedSheetEntry> bedSheets = runner.query(
                "SELECT * from wp_bedsheets WHERE job_id = ? AND checkout_date = ?",
                new BeanListHandler<BedSheetEntry>( BedSheetEntry.class, new BedSheetRowHandler() ), jobId,
                new java.sql.Date( date.getTime() ) );
        return bedSheets;
    }

    public void insertBedSheetEntry( BedSheetEntry entry ) throws SQLException {
        QueryRunner runner = new QueryRunner( getDataSource() );
        runner.update( "INSERT INTO wp_bedsheets (job_id, room, bed_name, checkout_date, change_status)"
                + " VALUES ( ?, ?, ?, ?, ? )", entry.getJobId(), entry.getRoom(), entry.getBedName(),
                new java.sql.Date( entry.getCheckoutDate().getTime() ), entry.getStatus().getValue() );
    }

    public void deleteAllBedSheetEntriesForJobId( int jobId ) throws SQLException {
        QueryRunner runner = new QueryRunner( getDataSource() );
        runner.update( "DELETE FROM wp_bedsheets WHERE job_id = ?", jobId );
    }

    public void insertAllocation( Allocation alloc ) throws SQLException {
        QueryRunner runner = new QueryRunner( getDataSource() );
        runner.update( "INSERT INTO wp_lh_calendar (job_id, room_id, room, bed_name, reservation_id, "
                + "guest_name, checkin_date, checkout_date, payment_total, payment_outstanding, "
                + "rate_plan_name, payment_status, num_guests, data_href )"
                + " VALUES ( ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ? )", alloc.getJobId(), alloc.getRoomId(),
                alloc.getRoom(), alloc.getBedName(), alloc.getReservationId(), alloc.getGuestName(),
                alloc.getCheckinDate(), alloc.getCheckoutDate(), alloc.getPaymentTotal(),
                alloc.getPaymentOutstanding(), alloc.getRatePlanName(), alloc.getPaymentStatus(),
                alloc.getNumberGuests(), alloc.getDataHref() );
    }

    public int insertJob( Job job ) throws SQLException {
        QueryRunner runner = new QueryRunner( getDataSource() );
        runner.update( "INSERT INTO wp_lh_jobs ( name, status )" + " VALUES ( ?, ? )", job.getName(), job.getStatus()
                .name() );

        // just assume that it increments consecutively for now
        int jobId = runner.query( "SELECT MAX(job_id) AS job_id FROM wp_lh_jobs " + " WHERE name = ? AND status = ?",
                new ResultSetHandler<Integer>() {

                    @Override
                    public Integer handle( ResultSet rs ) throws SQLException {
                        if ( rs.next() ) {
                            return rs.getInt( "job_id" );
                        }
                        throw new IllegalStateException( "job_id was inserted a moment ago.. :S" );
                    }

                }, job.getName(), job.getStatus().name() );
        return jobId;
    }

    public void deleteAllJobData() throws Exception {
        QueryRunner runner = new QueryRunner( getDataSource() );
        runner.update( "DELETE FROM wp_lh_jobs" );
    }

    public void updateJobStatus( int jobId, JobStatus status, JobStatus prevStatus ) throws SQLException {
        QueryRunner runner = new QueryRunner( getDataSource() );
        int rowsUpdated = runner.update( "UPDATE wp_lh_jobs SET status = ?, last_updated_date = NOW() "
                + " WHERE job_id = ? and status = ?", status.name(), jobId, prevStatus.name() );
        if ( rowsUpdated == 0 ) {
            throw new IncorrectNumberOfRecordsUpdatedException(
                    "Unable to update status of job " + jobId + " to " + status );
        }
    }

    public Job getNextJobToProcess() throws SQLException {
        QueryRunner runner = new QueryRunner( getDataSource() );
        Integer jobId = runner.query( "SELECT MIN(job_id) AS job_id FROM wp_lh_jobs WHERE status = ?",
                new ResultSetHandler<Integer>() {

                    @Override
                    public Integer handle( ResultSet rs ) throws SQLException {
                        if ( rs.next() ) {
                            return rs.getInt( "job_id" );
                        }
                        return null;
                    }

                }, JobStatus.submitted.name() );
        return jobId == null ? null : getJobById( jobId );
    }

    public Job getJobById( int id ) throws SQLException {
        QueryRunner runner = new QueryRunner( getDataSource() );
        return runner.query( "SELECT job_id, name, status, created_date, last_updated_date "
                + "  FROM wp_lh_jobs WHERE job_id = ?", new JobRowHandler(), id );
    }

    public static DataSource setupDataSource( String connectURI, String username, String password ) {
        BasicDataSource ds = new BasicDataSource();
        ds.setDriverClassName( "com.mysql.jdbc.Driver" );
        ds.setUsername( username );
        ds.setPassword( password );
        ds.setUrl( connectURI );
        return ds;
    }

}