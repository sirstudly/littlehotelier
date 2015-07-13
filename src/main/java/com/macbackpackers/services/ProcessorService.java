
package com.macbackpackers.services;

import javax.transaction.Transactional;

import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.apache.log4j.NDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.stereotype.Service;

import com.macbackpackers.beans.JobStatus;
import com.macbackpackers.dao.WordPressDAO;
import com.macbackpackers.exceptions.ShutdownException;
import com.macbackpackers.jobs.AbstractJob;
import com.macbackpackers.jobs.ShutdownJob;

@Service
public class ProcessorService {

    private final Logger LOGGER = LogManager.getLogger( getClass() );

    @Autowired
    private WordPressDAO dao;

    @Autowired
    private AutowireCapableBeanFactory autowireBeanFactory;

    @Value( "${process.jobs.retries:3}" )
    private int numberRetries;

    @Value( "${process.jobs.backoff.millis:3000}" )
    private int backoffMillis; // time to wait before re-attempting failed job

    /**
     * Checks for any housekeeping jobs that need to be run ('submitted') and processes them.
     * 
     * @throws ShutdownException if an immediate shutdown is requested
     */
    public void processJobs() throws ShutdownException {
        // find and run all submitted jobs
        for ( AbstractJob job = dao.getNextJobToProcess() ; job != null ; job = dao.getNextJobToProcess() ) {
            autowireBeanFactory.autowireBean( job ); // as job is an entity, wire up any spring collaborators 
            processJob( job );

            // stop processing all other jobs if shutdown requested
            if ( job instanceof ShutdownJob ) {
                throw new ShutdownException();
            }
        }
    }

    /**
     * Runs the job and updates the status when complete.
     */
    @Transactional
    private void processJob( AbstractJob job ) {

        for ( int i = 0 ; i < numberRetries ; i++ ) {
            NDC.push( String.valueOf( job.getId() ) ); // record the ID of this job for logging

            try {
                LOGGER.info( "Processing job " + job.getId() );
                dao.updateJobStatus( job.getId(), JobStatus.processing );
                job.processJob();
                LOGGER.info( "Finished job " + job.getId() );
                dao.updateJobStatus( job.getId(), JobStatus.completed, JobStatus.processing );
                break; // break out of retry loop
            }
            catch ( Throwable ex ) {
                LOGGER.error( "Error occurred when running " + getClass().getSimpleName() + " id: " + job.getId(), ex );

                // if we're on our last retry, fail this job
                if ( i == numberRetries - 1 ) {
                    LOGGER.error( "Maximum number of attempts reached. Job " + job.getId() + " failed" );
                    dao.updateJobStatus( job.getId(), JobStatus.failed, JobStatus.processing );
                }
                else { // wait a bit and try again
                    try {
                        Thread.sleep( backoffMillis );
                    }
                    catch ( InterruptedException e ) {
                        // ignore
                    }
                }
            }
            finally {
                NDC.pop();
            }
        }
    }
}
