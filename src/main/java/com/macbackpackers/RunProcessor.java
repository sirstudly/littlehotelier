
package com.macbackpackers;

import java.io.File;
import java.io.IOException;
import java.nio.channels.FileLock;
import java.util.Date;
import java.util.TimeZone;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.quartz.SchedulerException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.support.AbstractApplicationContext;
import org.springframework.stereotype.Component;

import com.macbackpackers.config.LittleHotelierConfig;
import com.macbackpackers.dao.WordPressDAO;
import com.macbackpackers.exceptions.ShutdownException;
import com.macbackpackers.services.FileService;
import com.macbackpackers.services.ProcessorService;
import com.macbackpackers.services.SchedulerService;

/**
 * The main bootstrap for running all available jobs.
 *
 */
@Component
public class RunProcessor
{
    private static final Logger LOGGER =  LoggerFactory.getLogger( RunProcessor.class );

    @Autowired
    private ProcessorService processorService;

    @Autowired
    private FileService fileService;

    @Autowired
    private WordPressDAO dao;

    @Autowired
    private SchedulerService scheduler;

    @Value( "${processor.repeat.interval.ms:60000}" )
    private long repeatIntervalMillis;

    // exclusive-file lock so only ever one instance of the processor is running
    private FileLock processorLock;

    /**
     * Process all jobs. If no jobs are available to be run, then pause for a configured period
     * before checking again.
     * 
     * @throws IOException on lock read error
     * @throws ShutdownException if shutdown has been requested
     * @throws SchedulerException on scheduling exception
     */
    private void processJobsLoopIndefinitely() throws IOException, ShutdownException, SchedulerException {

        while ( true ) {

            // run all submitted jobs
            processorService.processJobs();

            // repeat indefinitely
            try {
                Thread.sleep( repeatIntervalMillis );
            }
            catch ( InterruptedException e ) {
                // ignored
            }
        }
    }

    private void acquireLock() throws IOException, ShutdownException {
        processorLock = fileService.lockFile( new File( "processor.lock" ) );
        if ( processorLock == null ) {
            throw new ShutdownException( "Could not acquire exclusive lock; shutting down" );
        }
    }

    /**
     * Runs this process in server-mode periodically polling the jobs table and executing any that
     * are outstanding.
     * 
     * @throws IOException
     * @throws ShutdownException
     * @throws SchedulerException
     */
    public void runInServerMode() throws IOException, ShutdownException, SchedulerException {
        acquireLock();
        dao.resetAllProcessingJobsToFailed();
        scheduler.reloadScheduledJobs(); // load and start the scheduler
        processJobsLoopIndefinitely();
    }

    public void runInStandardMode() throws IOException, ShutdownException, SchedulerException {
        acquireLock();
        dao.resetAllProcessingJobsToFailed();
        processorService.processJobs();
    }

    /**
     * Releases the exclusive (file) lock on this process if it has been initialised.
     * 
     * @throws IOException
     */
    public void releaseExclusivityLock() throws IOException {
        if ( processorLock != null ) {
            processorLock.release();
        }
    }

    /**
     * Runs the processor.
     * 
     * @param args no arguments expected
     * @throws Exception on disastrous failure
     */
    public static void main( String args[] ) throws Exception {

        // create the command line parser
        CommandLineParser parser = new DefaultParser();

        // create the Options
        Options options = new Options();
        options.addOption( "h", "help", false, "Show this help message" );
        options.addOption( "S", "server", false, "server-mode; keep the processor running continuously" );

        // parse the command line arguments
        CommandLine line = parser.parse( options, args );

        // automatically generate the help statement
        if ( line.hasOption( "h" ) ) {
            HelpFormatter formatter = new HelpFormatter();
            formatter.printHelp( RunProcessor.class.getName(), options );
            return;
        }

        TimeZone.setDefault( TimeZone.getTimeZone( "Europe/London" ) );
        LOGGER.info( "Starting processor... " + new Date() );
        AbstractApplicationContext context = new AnnotationConfigApplicationContext( LittleHotelierConfig.class );

        // make sure there is only ever one process running
        RunProcessor processor = context.getBean( RunProcessor.class );

        try {
            // server-mode: keep the processor running
            if ( line.hasOption( "S" ) ) {
                LOGGER.info( "Running in server-mode" );
                processor.runInServerMode();
            }
            // standard-mode: running all outstanding jobs and quit
            else {
                LOGGER.info( "Running in standard mode" );
                processor.runInStandardMode();
            }
        }
        catch ( ShutdownException ex ) {
            LOGGER.info( "Shutdown task requested" );
        }
        finally {
            processor.releaseExclusivityLock();
            context.close();
            LOGGER.info( "Finished processor... " + new Date() );
        }
    }

}
