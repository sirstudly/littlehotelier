
package com.macbackpackers.services;

import java.io.File;
import java.io.IOException;
import java.sql.Timestamp;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.SystemUtils;
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

    private static final Object CLASS_LEVEL_LOCK = new Object();

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

    @Value( "${processor.job.log.localdir}" )
    private String localLogDirectory; // current log directory

    @Value( "${processor.job.log.copyto:}" )
    private String destinationLogLocation; // where to copy log files (optional)

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
     * Runs through all scheduled jobs and creates any that need to be run.
     */
    public void createOverdueScheduledJobs() {
        dao.fetchActiveJobSchedules()
                .stream()
                .filter( s -> s.isOverdue() && s.isActive() )
                .forEach( s -> {
                    try {
                        s.setLastRunDate( new Timestamp( System.currentTimeMillis() ) );
                        dao.updateJobScheduler( s );

                        // only need to create job if it's not already queued
                        if( false == dao.isJobCurrentlyPending( s.getClassname() ) ) {
                            LOGGER.info( "Creating new job " + s.getClassname() );
                            dao.insertJob( s.createNewJob() );
                        }
                    }
                    catch ( ReflectiveOperationException e ) {
                        LOGGER.error( "Whoops! Something went wrong here!", e );
                    }
                } );
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
        
        while ( true ) {
            try {
                createOverdueScheduledJobs();
            }
            catch ( Throwable th ) {
                LOGGER.error( "Error creating overdue scheduled jobs", th );
            }
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
            try {
                copyJobLogToRemoteHost( job.getId() );
            }
            catch ( Throwable th ) {
                LOGGER.error( "Failed to copy log for job " + job.getId(), th );
            }
            finally {
                MDC.remove( "jobId" );
            }
        }
    }

    /**
     * Copies the log file from this host to the remote host in {@code destinationLogLocation}.
     * 
     * @param jobId ID of the job to copy
     * @throws InterruptedException on process timeout
     * @throws IOException on copy error
     */
    private void copyJobLogToRemoteHost( int jobId ) throws InterruptedException, IOException {
        // only allow one copy job to take place at a time system-wide
        synchronized ( CLASS_LEVEL_LOCK ) {
            if ( false == SystemUtils.IS_OS_WINDOWS && StringUtils.isNotBlank( destinationLogLocation ) ) {
                LOGGER.info( "Compressing log file" );
                ProcessBuilder pb = new ProcessBuilder( "gzip" );
                pb.redirectInput( new File( localLogDirectory + "/job-" + jobId + ".log" ) );
                pb.redirectOutput( new File( localLogDirectory + "/job-" + jobId + ".gz" ) );
                Process p = pb.start();
                int exitVal = p.waitFor();
                LOGGER.info( "GZipped file completed with exit code(" + exitVal + ")" );

                pb = new ProcessBuilder( "scp", localLogDirectory + "/job-" + jobId + ".gz", destinationLogLocation );
                LOGGER.info( "Copying log file" );
                p = pb.start();
                exitVal = p.waitFor();
                LOGGER.info( "Log file copy completed with exit code(" + exitVal + ")" );
            }
        }
    }
}
