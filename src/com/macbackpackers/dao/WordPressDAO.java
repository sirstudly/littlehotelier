package com.macbackpackers.dao;

import java.sql.SQLException;
import java.util.Date;
import java.util.List;

import com.macbackpackers.beans.Allocation;
import com.macbackpackers.beans.BedSheetEntry;
import com.macbackpackers.beans.Job;
import com.macbackpackers.beans.JobStatus;

public interface WordPressDAO {

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
    public List<BedSheetEntry> getAllBedSheetEntriesForDate( int jobId, Date date );
    
    public void insertBedSheetEntry( BedSheetEntry entry );

    public void deleteAllBedSheetEntriesForJobId( int jobId );

    public void insertAllocation( Allocation alloc );

    public int insertJob( Job job );
    
    public void deleteAllJobData() throws Exception;

    public void updateJobStatus( int jobId, JobStatus status, JobStatus prevStatus );

    public Job getNextJobToProcess();
    
    public Job getJobById( int id );
}