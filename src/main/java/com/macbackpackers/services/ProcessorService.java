
package com.macbackpackers.services;

import com.macbackpackers.beans.JobParameter;
import com.macbackpackers.beans.JobStatus;
import com.macbackpackers.dao.WordPressDAO;
import com.macbackpackers.exceptions.IORuntimeException;
import com.macbackpackers.jobs.AbstractJob;
import com.macbackpackers.jobs.ResetCloudbedsSessionJob;
import com.macbackpackers.scrapers.CloudbedsScraper;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.pool2.impl.GenericObjectPool;
import org.htmlunit.WebClient;
import org.openqa.selenium.WebDriver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;

import javax.mail.MessagingException;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.sql.Timestamp;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Service
public class ProcessorService {

    private final Logger LOGGER = LoggerFactory.getLogger( getClass() );

    @Value( "${processor.thread.count:1}" )
    private int threadCount;

    @Value( "${gmail.sendfrom.name}" )
    private String gmailSendName;

    @Autowired
    private WordPressDAO dao;

    @Autowired
    private ApplicationContext context;

    @Autowired
    private AutowireCapableBeanFactory autowireBeanFactory;

    @Autowired
    private GmailService gmail;

    @Value( "${processor.repeat.interval.ms:60000}" )
    private long repeatIntervalMillis;

    @Value( "${process.jobs.backoff.millis:3000}" )
    private int backoffMillis; // time to wait before re-attempting failed job

    @Value( "${processor.job.log.localdir}" )
    private String localLogDirectory; // current log directory

    @Autowired
    private GenericObjectPool<WebDriver> driverFactory;

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
     * Clears out any webdrivers currently running.
     */
    public void shutdownDriverPool() {
        driverFactory.clear();
        driverFactory.close();
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
                        LOGGER.info( "Creating new job " + s.getClassname() );
                        dao.insertJob( s.createNewJob() );
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
     * Make sure we can connect to Cloudbeds (if applicable). Email support if 3 failed logins in a
     * row.
     *
     * @throws Exception if unable to establish cloudbeds session
     */
    public void initCloudbeds() throws Exception {
        // if cloudbeds, check if we can connect first
        // this will fail-fast if not
        if ( dao.isCloudbeds() ) {
            processCloudbedsResetLoginJobs();
            String failedLoginCountStr = dao.getOptionNoCache( "hbo_failed_logins" );
            int failedLoginCount = failedLoginCountStr == null ? 0 : Integer.parseInt( failedLoginCountStr );
            if ( failedLoginCount == 10 ) {
                createAndRunResetCloudbedsLoginJob();
            }
            else if ( failedLoginCount == 20 ) {
                String supportEmail = dao.getOption( "hbo_support_email" );
                if ( supportEmail != null ) {
                    try {
                        GmailService gmail = context.getBean( GmailService.class );
                        gmail.sendEmail( supportEmail, null, "Login Failed", "Help! I'm no longer able to login to Cloudbeds!! -RONBOT" );
                    }
                    catch ( MessagingException | IOException ex ) {
                        LOGGER.error( "Failed to send login failed email" );
                    }
                }
            }
            try ( WebClient c = context.getBean( "webClientForCloudbeds", WebClient.class )) {
                CloudbedsScraper cloudbedsScraper = context.getBean( CloudbedsScraper.class );
                cloudbedsScraper.getReservations( c, "999999999" ); // keep session alive
                dao.setOption( "hbo_failed_logins", "0" ); // reset
            }
            catch ( Exception ex ) {
                dao.setOption( "hbo_failed_logins", String.valueOf( ++failedLoginCount ) ); // increment
                throw ex;
            }
        }
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
                initCloudbeds();
            }
            catch ( Throwable th ) {
                LOGGER.error( "Failed to initialise cloudbeds.. Have we been logged out?", th );
            }
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
                LOGGER.info( "Waiting for {} seconds before checking again for new jobs", repeatIntervalMillis / 1000 );
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
            catch ( IOException | TimeoutException | IORuntimeException ex ) {
                // catch SNI errors and random connection errors and retry later
                LOGGER.info( "Connection error on job " + job.getId() + ". Setting status to RETRY" );
                dao.updateJobStatusToRetry( job.getId() );
            }
            catch ( Throwable ex ) {
                LOGGER.error( "Error occurred when running " + getClass().getSimpleName() + " id: " + job.getId(), ex );

                // if we're on our last retry, fail this job
                if ( i == job.getRetryCount() - 1 ) {
                    LOGGER.error( "Maximum number of attempts reached. Job " + job.getId() + " failed" );
                    dao.updateJobStatus( job.getId(), JobStatus.failed, JobStatus.processing );
                    emailJobFailureToSupport( job, ex );
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

    private void emailJobFailureToSupport( AbstractJob job, Throwable ex ) {
        String supportEmail = dao.getOption( "hbo_support_email" );
        if ( supportEmail != null ) {
            StringWriter sw = new StringWriter();
            try ( PrintWriter pw = new PrintWriter( sw ) ) {
                pw.println( job.getClass().getName() + " (" + job.getId() + ") failed" );
                for ( JobParameter param : job.getParameters() ) {
                    pw.println( param.getName() + ": " + param.getValue() );
                }
                pw.println( "Stacktrace:" );
                ex.printStackTrace( pw );
                pw.println();
                pw.println( "-RONBOT" );
                pw.flush();
                gmail.sendEmail( supportEmail, null, gmailSendName + " Job Failed", sw.toString() );
            }
            catch ( Throwable th2 ) {
                LOGGER.error( "Failed to send support email!", th2 );
            }
        }
    }

    /**
     * Copies the log file from this host to the remote host in {@code destinationLogLocation}.
     * Synchronized so only one copy job to take place at a time system-wide.
     * 
     * @param jobId ID of the job to copy
     * @throws InterruptedException on process timeout
     * @throws IOException on copy error
     */
    private synchronized void copyJobLogToRemoteHost( int jobId ) throws InterruptedException, IOException {

        String destinationLogLocation = dao.getOption( "hbo_processor_copy_job_log_to" );
        if ( StringUtils.isNotBlank( destinationLogLocation ) ) {
            LOGGER.info( "Compressing log file" );
            ProcessBuilder pb = new ProcessBuilder( "gzip" );
            pb.redirectInput( new File( localLogDirectory + "/job-" + jobId + ".log" ) );
            pb.redirectOutput( new File( localLogDirectory + "/job-" + jobId + ".gz" ) );
            Process p = pb.start();
            int exitVal = p.waitFor();
            LOGGER.info( "GZipped file completed with exit code(" + exitVal + ")" );

            pb = new ProcessBuilder( "scp", localLogDirectory + "/job-" + jobId + ".gz", destinationLogLocation );
            pb.redirectOutput( new File( localLogDirectory + "/job-" + jobId + ".scp.out" ) );
            pb.redirectError( new File( localLogDirectory + "/job-" + jobId + ".scp.err" ) );
            LOGGER.info( "Copying log file" );
            p = pb.start();
            exitVal = p.waitFor();
            LOGGER.info( "Log file copy completed with exit code(" + exitVal + ")" );
        }
    }

    /**
     * Runs any ResetCloudbedsSessionJobs if found.
     */
    public void processCloudbedsResetLoginJobs() {
        dao.fetchResetCloudbedsSessionJob()
                .ifPresent( j -> {
                    LOGGER.info( "Found ResetCloudbedsSessionJob, running..." );
                    autowireBeanFactory.autowireBean( j );
                    processJob( j );
                } );
    }

    public void createAndRunResetCloudbedsLoginJob() {
        ResetCloudbedsSessionJob j = new ResetCloudbedsSessionJob();
        j.setStatus( JobStatus.submitted );
        dao.insertJob( j );
        processCloudbedsResetLoginJobs();
    }
}
