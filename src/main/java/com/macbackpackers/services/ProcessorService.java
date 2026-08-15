
package com.macbackpackers.services;

import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.stripe.exception.ApiConnectionException;
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
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.stereotype.Service;

import javax.mail.MessagingException;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.sql.Timestamp;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

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

    private final AtomicBoolean shutdownRequested = new AtomicBoolean( false );

    private volatile Thread loopThread;

    /** Serializes remote log copies without blocking job claim/process workers. */
    private volatile ExecutorService logCopyExecutor;

    /**
     * Requests a graceful shutdown: stop claiming new jobs, finish in-flight work, then exit the
     * processing loop.
     */
    public void requestShutdown() {
        if ( shutdownRequested.compareAndSet( false, true ) ) {
            LOGGER.info( "Graceful shutdown requested" );
            Thread t = loopThread;
            if ( t != null ) {
                t.interrupt();
            }
        }
    }

    /**
     * @return true if a graceful shutdown has been requested
     */
    public boolean isShutdownRequested() {
        return shutdownRequested.get();
    }

    /**
     * Checks for any jobs that need to be run ('submitted') and processes them.
     */
    public void processJobs() {

        // check if we have anything to do first
        if( dao.getOutstandingJobCount() == 0 ) {
            LOGGER.info( "No outstanding jobs. Nothing to do." );
            return;
        }

        ensureLogCopyExecutor();
        ExecutorService executor = Executors.newFixedThreadPool( threadCount );
        for ( int i = 0 ; i < threadCount ; i++ ) {
            executor.execute( new JobProcessorThread( this, false ) );
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
            Thread.currentThread().interrupt();
            LOGGER.warn( "Interrupted while waiting for batch workers to finish" );
        }
        finally {
            drainLogCopyExecutor();
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
     * @return true if any jobs are still outstanding (submitted/retry/etc.)
     */
    public boolean hasOutstandingJobs() {
        return dao.getOutstandingJobCount() > 0;
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
     * Process all jobs. Coordinator refreshes Cloudbeds session and overdue schedules on an
     * interval while a fixed pool of continuous workers claim and run jobs. Returns when
     * {@link #requestShutdown()} is called and in-flight workers have finished (or the drain
     * timeout elapses).
     */
    public void processJobsLoopIndefinitely() {
        loopThread = Thread.currentThread();
        ensureLogCopyExecutor();
        ExecutorService executor = Executors.newFixedThreadPool( threadCount );
        for ( int i = 0 ; i < threadCount ; i++ ) {
            executor.execute( new JobProcessorThread( this, true ) );
        }
        LOGGER.info( "Started {} continuous job workers", threadCount );

        try {
            while ( !shutdownRequested.get() ) {
                try {
                    initCloudbeds();
                }
                catch ( Throwable th ) {
                    LOGGER.error( "Failed to initialise cloudbeds.. Have we been logged out?", th );
                }
                if ( shutdownRequested.get() ) {
                    break;
                }
                try {
                    createOverdueScheduledJobs();
                }
                catch ( Throwable th ) {
                    LOGGER.error( "Error creating overdue scheduled jobs", th );
                }
                if ( shutdownRequested.get() ) {
                    break;
                }
                try {
                    LOGGER.info( "Waiting for {} seconds before next coordinator cycle", repeatIntervalMillis / 1000 );
                    Thread.sleep( repeatIntervalMillis );
                }
                catch ( InterruptedException e ) {
                    // Do not restore the interrupt flag here: requestShutdown() interrupts this
                    // thread only to wake sleep. Restoring it would make awaitTermination below
                    // throw immediately and force-kill in-flight jobs.
                    LOGGER.info( "Coordinator loop interrupted; exiting" );
                    break;
                }
            }
        }
        finally {
            LOGGER.info( "Draining job executor; waiting for in-flight jobs to finish..." );
            // Workers observe shutdownRequested via sliced idle sleeps and exit cooperatively
            executor.shutdown();
            // Clear any leftover interrupt from waking the coordinator sleep so we can actually
            // wait for workers (up to docker stop_grace_period).
            Thread.interrupted();
            try {
                // Leave headroom under docker stop_grace_period (5m) for cleanup after drain
                if ( !executor.awaitTermination( 4, TimeUnit.MINUTES ) ) {
                    LOGGER.warn( "Jobs still running after 4 minutes; waiting 30s more before force shutdown" );
                    if ( !executor.awaitTermination( 30, TimeUnit.SECONDS ) ) {
                        LOGGER.warn( "Forcing job executor shutdown" );
                        executor.shutdownNow();
                    }
                }
                else {
                    LOGGER.info( "All job workers terminated" );
                }
            }
            catch ( InterruptedException e ) {
                LOGGER.warn( "Interrupted while draining job executor; forcing shutdown" );
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
            finally {
                drainLogCopyExecutor();
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

                    // catch SNI errors and random connection errors and retry later
                    if ( !( ex instanceof GoogleJsonResponseException ) && ( ex instanceof IOException || ex instanceof TimeoutException || ex instanceof IORuntimeException || ex instanceof ApiConnectionException || ex instanceof CannotAcquireLockException ) ) {
                        LOGGER.info( "Maximum number of attempts reached. Transient error on job " + job.getId() + ". Setting status to RETRY" );
                        dao.updateJobStatusToRetry( job.getId() );
                    }
                    else {
                        LOGGER.error( "Maximum number of attempts reached. Job " + job.getId() + " failed" );
                        dao.updateJobStatus( job.getId(), JobStatus.failed, JobStatus.processing );
                        emailJobFailureToSupport( job, ex );
                    }
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
                scheduleJobLogCopy( job.getId() );
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
     * Queues remote log copy on a dedicated single-thread executor so workers can claim the next
     * job without waiting on gzip/scp. Copies remain serialized (one at a time).
     *
     * @param jobId ID of the job whose log should be copied
     */
    void scheduleJobLogCopy( int jobId ) {
        ensureLogCopyExecutor();
        logCopyExecutor.execute( () -> {
            MDC.put( "jobId", String.valueOf( jobId ) );
            try {
                copyJobLogToRemoteHost( jobId );
            }
            catch ( Throwable th ) {
                LOGGER.error( "Failed to copy log for job " + jobId, th );
            }
            finally {
                MDC.remove( "jobId" );
            }
        } );
    }

    private void ensureLogCopyExecutor() {
        ExecutorService existing = logCopyExecutor;
        if ( existing != null && !existing.isShutdown() ) {
            return;
        }
        synchronized ( this ) {
            if ( logCopyExecutor == null || logCopyExecutor.isShutdown() ) {
                logCopyExecutor = Executors.newSingleThreadExecutor( r -> {
                    Thread t = new Thread( r, "job-log-copy" );
                    t.setDaemon( true );
                    return t;
                } );
            }
        }
    }

    /**
     * Stops accepting new log copies and waits for in-flight gzip/scp work to finish.
     */
    void drainLogCopyExecutor() {
        ExecutorService executor = logCopyExecutor;
        if ( executor == null ) {
            return;
        }
        LOGGER.info( "Draining job log copy executor..." );
        executor.shutdown();
        try {
            if ( !executor.awaitTermination( 1, TimeUnit.MINUTES ) ) {
                LOGGER.warn( "Log copies still running after 1 minute; forcing shutdown" );
                executor.shutdownNow();
            }
            else {
                LOGGER.info( "Job log copy executor drained" );
            }
        }
        catch ( InterruptedException e ) {
            LOGGER.warn( "Interrupted while draining log copy executor; forcing shutdown" );
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Copies the log file from this host to the remote host in {@code destinationLogLocation}.
     * Invoked only from the log-copy executor so scp work does not block job claiming.
     *
     * @param jobId ID of the job to copy
     * @throws InterruptedException on process timeout
     * @throws IOException on copy error
     */
    private void copyJobLogToRemoteHost( int jobId ) throws InterruptedException, IOException {

        String destinationLogLocation = dao.getOption( "hbo_processor_copy_job_log_to" );
        if ( StringUtils.isNotBlank( destinationLogLocation ) ) {
            LOGGER.info( "Compressing log file" );
            ProcessBuilder pb = new ProcessBuilder( "gzip" );
            pb.redirectInput( new File( localLogDirectory + "/job-" + jobId + ".log" ) );
            pb.redirectOutput( new File( localLogDirectory + "/job-" + jobId + ".gz" ) );
            Process p = pb.start();
            int exitVal = p.waitFor();
            LOGGER.info( "GZipped file completed with exit code(" + exitVal + ")" );

            final int MAX_ATTEMPTS = 3;
            for ( int attempt = 1 ; attempt <= MAX_ATTEMPTS ; attempt++ ) {
                pb = new ProcessBuilder( "scp", localLogDirectory + "/job-" + jobId + ".gz", destinationLogLocation );
                pb.redirectOutput( new File( localLogDirectory + "/job-" + jobId + ".scp.out" ) );
                pb.redirectError( new File( localLogDirectory + "/job-" + jobId + ".scp.err" ) );
                LOGGER.info( "Copying log file (attempt " + attempt + "/" + MAX_ATTEMPTS + ")" );
                p = pb.start();
                exitVal = p.waitFor();
                LOGGER.info( "Log file copy completed with exit code(" + exitVal + ")" );
                if ( exitVal == 0 ) {
                    break;
                }
                if ( attempt < MAX_ATTEMPTS ) {
                    LOGGER.warn( "scp failed with exit code(" + exitVal + "); retrying in 5s..." );
                    Thread.sleep( 5000 );
                }
            }
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
