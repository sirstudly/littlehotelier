
package com.macbackpackers.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.stereotype.Service;

import com.macbackpackers.dao.WordPressDAO;
import com.macbackpackers.jobs.AbstractJob;

@Service
public class ProcessorService {

    @Autowired
    private WordPressDAO dao;

    @Autowired
    private AutowireCapableBeanFactory autowireBeanFactory;

    /**
     * Checks for any housekeeping jobs that need to be run ('submitted') and processes them.
     * 
     */
    public void processJobs() {
        // find and run all submitted jobs
        for ( AbstractJob job = dao.getNextJobToProcess() ; job != null ; job = dao.getNextJobToProcess() ) {
            autowireBeanFactory.autowireBean( job ); // as job is an entity, wire up any spring collaborators 
            job.doProcessJob();
        }
    }

}
