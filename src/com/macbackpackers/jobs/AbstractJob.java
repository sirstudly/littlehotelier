package com.macbackpackers.jobs;

import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.apache.log4j.NDC;
import org.springframework.beans.factory.annotation.Autowired;

import com.macbackpackers.beans.Job;
import com.macbackpackers.beans.JobStatus;
import com.macbackpackers.dao.WordPressDAO;

/**
 * Some framework code associated with executing jobs. 
 *
 */
public abstract class AbstractJob extends Job {

    private final Logger LOGGER = LogManager.getLogger( getClass() );

    @Autowired
    protected WordPressDAO dao;
    
    /**
     * Do whatever it is we need to do.
     * 
     * @throws Exception
     */
    public abstract void processJob() throws Exception;
    
    /**
     * Runs the job and updates the status when complete.
     */
    public void doProcessJob() {
        dao.updateJobStatus( getId(), JobStatus.processing, JobStatus.submitted );
        NDC.push( String.valueOf( getId() ) ); // record the ID of this job for logging

        try {
            processJob();
            dao.updateJobStatus( getId(), JobStatus.completed, JobStatus.processing );
        }
        catch( Exception ex ) {
            LOGGER.error( "Error occurred when running " + getClass().getSimpleName() + " id: " + getId(), ex );
            dao.updateJobStatus( getId(), JobStatus.failed, JobStatus.processing );
        } 
        finally {
            NDC.pop();
        }
    }

}
