package com.macbackpackers.services;

import java.sql.SQLException;

import com.macbackpackers.beans.Job;
import com.macbackpackers.dao.WordPressDAO;

public class ProcessorService {

    private HousekeepingService housekeeping = new HousekeepingService();

    /**
     * Checks for any housekeeping jobs that need to be run ('submitted') and processes them.
     * 
     * @throws SQLException
     *             on error
     */
    public void processJobs() throws SQLException {
        // find submitted jobs
        WordPressDAO dao = new WordPressDAO(
                Config.getProperty( "db.prefix" ),
                Config.getProperty( "db.hostname" ),
                Config.getProperty( "db.port" ),
                Config.getProperty( "db.instance" ) );
        dao.connect( Config.getProperty( "db.username" ), Config.getProperty( "db.password" ) );

        Job job = dao.getNextJobToProcess();

        if ( "bedsheets".equals( job.getName() ) ) {
            housekeeping.processJob( job );
        }
    }
}