
package com.macbackpackers;

import com.macbackpackers.exceptions.ShutdownException;
import com.macbackpackers.services.FileService;
import com.macbackpackers.services.ProcessorService;
import com.macbackpackers.utils.AnyByteStringToStringConverter;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.pool2.impl.GenericObjectPool;
import org.openqa.selenium.WebDriver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.convert.support.DefaultConversionService;

import java.io.File;
import java.io.IOException;
import java.nio.channels.FileLock;
import java.util.Date;
import java.util.TimeZone;

/**
 * The main bootstrap for running all available jobs.
 *
 */
@SpringBootApplication
public class RunProcessor
{
    private static final Logger LOGGER =  LoggerFactory.getLogger( RunProcessor.class );

    @Autowired
    private ProcessorService processorService;

    @Autowired
    private FileService fileService;

    @Autowired
    private GenericObjectPool<WebDriver> webDriverObjectPool;

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
     * Cleanup any resources before shutting down.
     */
    public void shutdown() {
        LOGGER.info( "Shutting down... Closing WebDriver pool." );
        try {
            webDriverObjectPool.close(); // This ensures all objects in the pool are destroyed
        }
        catch ( Exception ex ) {
            LOGGER.error( "Error attempting to shutdown webdriver pool.", ex );
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

        // The problem is that most likely, autoconfiguration happens early on, before
        // GcpSecretManagerEnvironmentPostProcessor had a chance to run and introduce ByteString converters.
        // See https://stackoverflow.com/a/71226714
        ( (DefaultConversionService) DefaultConversionService.getSharedInstance() ).addConverter( new AnyByteStringToStringConverter() );

        TimeZone.setDefault( TimeZone.getTimeZone( "Europe/London" ) );
        LOGGER.info( "Starting processor... " + new Date() );
        ConfigurableApplicationContext context = SpringApplication.run( RunProcessor.class, args );

        // make sure there is only ever one process running
        RunProcessor processor = context.getBean( RunProcessor.class );

        if( line.hasOption( "n" )) {
            processor.setCheckLock( false );
        }

        try {
            // Add a shutdown hook to clean up any open resources
            Runtime.getRuntime().addShutdownHook( new Thread( () -> processor.shutdown() ) );

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
