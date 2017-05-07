
package com.macbackpackers.services;

import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.stereotype.Service;

import com.macbackpackers.beans.JobStatus;
import com.macbackpackers.dao.WordPressDAO;
import com.macbackpackers.jobs.AbstractJob;

@Service
public class ProcessorService {

    private final Logger LOGGER = LoggerFactory.getLogger( getClass() );
    
    @Value( "${processor.thread.count:1}" )
    private int threadCount;

    @Autowired
    private WordPressDAO dao;

    @Autowired
    private AutowireCapableBeanFactory autowireBeanFactory;

    @Value( "${processor.repeat.interval.ms:60000}" )
    private long repeatIntervalMillis;

    @Value( "${process.jobs.backoff.millis:3000}" )
    private int backoffMillis; // time to wait before re-attempting failed job

    /**
     * Checks for any jobs that need to be run ('submitted') and processes them.
     */
    public void processJobs() {

        // check if we have anything to do first
        if( dao.getOutstandingJobCount() == 0 ) {
            LOGGER.info( "No outstanding jobs. Nothing to do." );
            return;
        }

        // start thread pool
        ExecutorService executor = Executors.newFixedThreadPool( threadCount );
        CyclicBarrier barrier = new CyclicBarrier( threadCount );
        for ( int i = 0 ; i < threadCount ; i++ ) {
            JobProcessorThread th = new JobProcessorThread( barrier );
            autowireBeanFactory.autowireBean( th );
            executor.execute( th );
        }
        LOGGER.info( "Finished thread pool creation." );

        // wait until all threads terminate nicely
        executor.shutdown();
        try {
            if ( executor.awaitTermination( 1, TimeUnit.DAYS ) ) {
                LOGGER.info( "All threads terminated." );
            }
            else {
                LOGGER.info( "Timeout waiting for threads to terminate" );
            }
        }
        catch ( InterruptedException e ) {
            // ignored
        }
    }

    /**
     * Synchronize block around {@link WordPressDAO#getNextJobToProcess()} otherwise the transaction
     * may not commit before the next thread runs.
     * 
     * @return next job or null if none found
     */
    public synchronized AbstractJob getNextJobToProcess() {
        AbstractJob job = dao.getNextJobToProcess(); 
        if( job != null ) {
            autowireBeanFactory.autowireBean( job ); // as job is an entity, wire up any spring collaborators
        }
        return job;
    }

    /**
     * Process all jobs. If no jobs are available to be run, then pause for a configured period
     * before checking again.
     * 
     */
    public void processJobsLoopIndefinitely() {
        // start thread pool
        ExecutorService executor = Executors.newFixedThreadPool( threadCount );
        CyclicBarrier barrier = new CyclicBarrier( threadCount );
        
        while(true) {
            for ( int i = 0 ; i < threadCount ; i++ ) {
                JobProcessorThread th = new JobProcessorThread( barrier );
                autowireBeanFactory.autowireBean( th );
                executor.execute( th );
            }
            try {
                Thread.sleep( repeatIntervalMillis ); // wait then repeat loop
            }
            catch ( InterruptedException e ) {
                // ignore
            }
        }
    }

    /**
     * Runs the job and updates the status when complete.
     * 
     * @param job the job that will be executed
     */
//    @Transactional( propagation = Propagation.REQUIRES_NEW )
    public void processJob( AbstractJob job ) {

        MDC.put( "jobId", String.valueOf( job.getId() ) ); // record the ID of this job for logging
        for ( int i = 0 ; i < job.getRetryCount() ; i++ ) {
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
        }

        try {
            job.finalizeJob();
        }
        catch ( Throwable ex ) {
            LOGGER.error( "Error finalising job " + job.getId(), ex );
        }
        finally {
            MDC.remove( "jobId" );
        }
    }
}
