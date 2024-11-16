
package com.macbackpackers;

import com.macbackpackers.config.LittleHotelierConfig;
import com.macbackpackers.exceptions.ShutdownException;
import com.macbackpackers.services.FileService;
import com.macbackpackers.services.ProcessorService;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.support.AbstractApplicationContext;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.nio.channels.FileLock;
import java.util.Date;
import java.util.TimeZone;

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

    // exclusive-file lock so only ever one instance of the processor is running
    private FileLock processorLock;
    
    // make sure only one instance is running by checking a file-level lock
    private boolean checkLock = true;

    /**
     * Returns whether or not to check the lock before starting.
     * 
     * @return true to check file exclusivity lock; false to ignore
     */
    public boolean isCheckLock() {
        return checkLock;
    }

    /**
     * Sets the lock check.
     * 
     * @param checkLock true to check file exclusivity lock; false to ignore
     */
    public void setCheckLock( boolean checkLock ) {
        this.checkLock = checkLock;
    }

    private void acquireLock() throws IOException, ShutdownException {
        if( isCheckLock() ) {
            processorLock = fileService.lockFile( new File( "processor.lock" ) );
            if ( processorLock == null ) {
                throw new ShutdownException( "Could not acquire exclusive lock; shutting down" );
            }
        }
    }

    /**
     * Runs this process in server-mode periodically polling the jobs table and executing any that
     * are outstanding.
     * 
     * @throws Exception
     */
    public void runInServerMode() throws Exception {
        acquireLock();
//        dao.resetAllProcessingJobsToFailed();
//        scheduler.reloadScheduledJobs(); // load and start the scheduler
        processorService.processJobsLoopIndefinitely();
    }

    /**
     * Run the processor once executing any outstanding jobs.
     * @throws Exception
     */
    public void runInStandardMode() throws Exception {
        acquireLock();
        processorService.initCloudbeds();
//        dao.resetAllProcessingJobsToFailed();
        processorService.createOverdueScheduledJobs();

        try {
            processorService.processJobs();
        }
        finally {
            processorService.shutdownDriverPool();
        }
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
        options.addOption( "n", "nolock", false, "allow multiple instances to run by ignoring exclusivity lock" );

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

        if( line.hasOption( "n" )) {
            processor.setCheckLock( false );
        }

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
        catch ( Throwable th ) {
            LOGGER.error( "Unexpected error", th );
        }
        finally {
            processor.releaseExclusivityLock();
            context.close();
            LOGGER.info( "Finished processor... " + new Date() );
            System.exit(0);
        }
    }

}
