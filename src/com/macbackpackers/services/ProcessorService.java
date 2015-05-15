package com.macbackpackers.services;

import java.sql.SQLException;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.macbackpackers.beans.Job;
import com.macbackpackers.dao.WordPressDAO;

@Service
public class ProcessorService {

    @Autowired
    private HousekeepingService housekeeping;

    @Autowired
    private WordPressDAO dao;

    /**
     * Checks for any housekeeping jobs that need to be run ('submitted') and processes them.
     * 
     * @throws SQLException
     *             on error
     */
    public void processJobs() throws SQLException {
        // find submitted jobs
        Job job = dao.getNextJobToProcess();

        if ( "bedsheets".equals( job.getName() ) ) {
            housekeeping.processJob( job );
        }
    }
}