package com.macbackpackers.services;

import java.sql.SQLException;

import com.macbackpackers.beans.Job;
import com.macbackpackers.beans.JobStatus;
import com.macbackpackers.dao.WordPressDAO;

public class HousekeepingService {
    
    /**
     * Checks for any housekeeping jobs that need to be run ('submitted') and 
     * processes them.
     * @throws SQLException 
     */
    public void processJob( Job job ) throws SQLException {
        // find submitted jobs
        WordPressDAO dao = new WordPressDAO(
                Config.getProperty( "db.prefix" ),
                Config.getProperty( "db.hostname" ),
                Config.getProperty( "db.port" ),
                Config.getProperty( "db.instance" ) );
        dao.connect( Config.getProperty( "db.username" ), Config.getProperty( "db.password" ) );

        dao.updateJobStatus( job.getId(), JobStatus.processing, JobStatus.submitted );
    }
}