
package com.macbackpackers.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
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

    private final Logger LOGGER = LoggerFactory.getLogger( getClass() );

    @Autowired
    private WordPressDAO dao;

    @Autowired
    private AutowireCapableBeanFactory autowireBeanFactory;

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
//    @Transactional( propagation = Propagation.REQUIRES_NEW )
    private void processJob( AbstractJob job ) {

        LOGGER.info( "Attempting to lock job " + job.getId() );
        if ( false == dao.updateJobStatusToProcessing( job.getId() ) ) {
            LOGGER.info( "Could not lock job " + job.getId() + "; skipping" );
            return;
        }

        for ( int i = 0 ; i < job.getRetryCount() ; i++ ) {
            MDC.put( "jobId", String.valueOf( job.getId() ) ); // record the ID of this job for logging

            try {
                LOGGER.info( "Processing job " + job.getId() + "; Attempt " + (i + 1));
                job.resetJob();
                job.processJob();
                LOGGER.info( "Finished job " + job.getId() );
                dao.updateJobStatus( job.getId(), JobStatus.completed, JobStatus.processing );
                break; // break out of retry loop
            }
            catch ( Throwable ex ) {
                LOGGER.error( "Error occurred when running " + getClass().getSimpleName() + " id: " + job.getId(), ex );

                // if we're on our last retry, fail this job
                if ( i == job.getRetryCount() - 1 ) {
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
                MDC.remove( "jobId" );
            }
        }
    }
}
