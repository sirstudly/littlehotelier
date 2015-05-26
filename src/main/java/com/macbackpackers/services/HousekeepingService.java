package com.macbackpackers.services;

import java.sql.SQLException;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.macbackpackers.beans.Job;
import com.macbackpackers.beans.JobStatus;
import com.macbackpackers.dao.WordPressDAO;

@Service
public class HousekeepingService {
    
    @Autowired
    private WordPressDAO dao;

    /**
     * Checks for any housekeeping jobs that need to be run ('submitted') and 
     * processes them.
     * @throws SQLException 
     */
    public void processJob( Job job ) throws SQLException {
        // find submitted jobs
        dao.updateJobStatus( job.getId(), JobStatus.processing, JobStatus.submitted );
    }
}