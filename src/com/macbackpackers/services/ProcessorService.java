package com.macbackpackers.services;

import java.sql.SQLException;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.macbackpackers.dao.WordPressDAO;
import com.macbackpackers.jobs.AbstractJob;
import com.macbackpackers.scrapers.AllocationsPageScraper;

@Service
public class ProcessorService {

    @Autowired
    private HousekeepingService housekeeping;

    @Autowired
    private WordPressDAO dao;

    @Autowired
    private AllocationsPageScraper allocationScraper;

    /**
     * Checks for any housekeeping jobs that need to be run ('submitted') and processes them.
     * 
     * @throws SQLException
     *             on error
     */
    public void processJobs() throws SQLException {
        // find and run all submitted jobs
        for( AbstractJob job = dao.getNextJobToProcess(); job != null; job = dao.getNextJobToProcess() ) {
            job.doProcessJob();
        }
    }
    
}