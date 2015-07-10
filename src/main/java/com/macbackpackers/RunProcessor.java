
package com.macbackpackers;

import java.io.File;
import java.io.IOException;
import java.nio.channels.FileLock;
import java.util.Date;
import java.util.TimeZone;

import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.quartz.SchedulerException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.support.AbstractApplicationContext;

import com.macbackpackers.beans.Job;
import com.macbackpackers.beans.JobStatus;
import com.macbackpackers.config.LittleHotelierConfig;
import com.macbackpackers.dao.WordPressDAO;
import com.macbackpackers.exceptions.ShutdownException;
import com.macbackpackers.jobs.AllocationScraperJob;
import com.macbackpackers.jobs.CreateConfirmDepositAmountsJob;
import com.macbackpackers.jobs.HousekeepingJob;
import com.macbackpackers.jobs.ScrapeReservationsBookedOnJob;
import com.macbackpackers.scrapers.BookingsPageScraper;
import com.macbackpackers.services.FileService;
import com.macbackpackers.services.ProcessorService;
import com.macbackpackers.services.SchedulerService;

/**
 * The main bootstrap for running all available jobs.
 *
 */
public class RunProcessor
{

    private static final Logger LOGGER = LogManager.getLogger( RunProcessor.class );

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

    /**
     * Processes all available jobs. To ensure only one processor is running at a time, attempt to
     * get an exclusive file lock before attempting. If a lock is not obtained, this method returns
     * immediately.
     * 
     * @throws IOException if file could not be read/created
     * @throws ShutdownException if an immediate shutdown is requested
     * @throws SchedulerException on scheduler exception
     */
    public void processJobs() throws IOException, ShutdownException, SchedulerException {
        processorService.processJobs();
    }

    /**
     * Process all jobs. If no jobs are available to be run, then pause for a configured period
     * before checking again.
     * 
     * @throws IOException on lock read error
     * @throws ShutdownException if shutdown has been requested
     * @throws SchedulerException on scheduling exception
     */
    public void processJobsLoopIndefinitely() throws IOException, ShutdownException, SchedulerException {

        dao.resetAllProcessingJobsToFailed();

        while ( true ) {

            // run all submitted jobs
            processJobs();

            // repeat indefinitely
            try {
                Thread.sleep( repeatIntervalMillis );
            }
            catch ( InterruptedException e ) {
                // ignored
            }
        }
    }

    /**
     * Loads and starts the scheduler.
     * 
     * @throws SchedulerException if scheduler could not be started
     */
    public void startScheduler() throws SchedulerException {
        scheduler.reloadScheduledJobs();
    }

    /**
     * Creates an allocation scraper job.
     */
    public void insertAllocationScraperJob() {
        AllocationScraperJob j = new AllocationScraperJob();
        j.setStatus( JobStatus.submitted );
        j.setStartDate( new Date() );
        j.setDaysAhead( 140 ); // look-ahead 4-5 months
        dao.insertJob( j );
    }

    public void insertCreateConfirmDepositAmountsJob() {
        Job j = new CreateConfirmDepositAmountsJob();
        j.setStatus( JobStatus.submitted );
        dao.insertJob( j );
    }

    public void insertScrapeReservationsBookedOnJob() {
        Job j = new ScrapeReservationsBookedOnJob();
        j.setStatus( JobStatus.submitted );
        j.setParameter( "booked_on_date", BookingsPageScraper.DATE_FORMAT_YYYY_MM_DD.format( new Date() ) );
        dao.insertJob( j );
    }

    public void insertHousekeepingJob() {
        Job j = new HousekeepingJob();
        j.setStatus( JobStatus.submitted );
        j.setParameter( "selected_date", BookingsPageScraper.DATE_FORMAT_YYYY_MM_DD.format( new Date() ) );
        dao.insertJob( j );
    }

    /**
     * Runs the processor.
     * 
     * @param args no arguments expected
     * @throws Exception on disastrous failure
     */
    public static void main( String args[] ) throws Exception {
        TimeZone.setDefault( TimeZone.getTimeZone( "Europe/London" ) );
        LOGGER.info( "Starting processor... " + new Date() );
        AbstractApplicationContext context = new AnnotationConfigApplicationContext( LittleHotelierConfig.class );

        // make sure there is only ever one process running
        FileService fileService = context.getBean( FileService.class );
        FileLock lock = fileService.lockFile( new File( "processor.lock" ) );
        if ( lock == null ) {
            LOGGER.info( "Could not acquire lock. Exiting." );
            context.close();
            LOGGER.info( "Finished processor... " + new Date() );
            return;
        }

        RunProcessor processor = new RunProcessor();
        context.getAutowireCapableBeanFactory().autowireBean( processor );

        try {
            // option -h for help
            if ( args.length == 1 && "-h".equals( args[0] ) ) {
                System.out.println( "USAGE: " + RunProcessor.class.getName() );
                System.out.println( "Flags (only one allowed at a time)" );
                System.out.println( "    -a    Adds an allocation scraper job" );
                System.out.println( "          This will queue up all the report jobs" );
                System.out.println();
                System.out.println( "    -d    Create a click deposits job" );
                System.out.println( "          This will find the last completed AllocationScraperJob and find" );
                System.out.println( "          all HW/HB reservations where the total amount = payment outstanding" );
                System.out.println( "          and create a click deposit job for each one" );
                System.out.println();
                System.out.println( "    -b    This will create a job that will search all bookings on HW/HB" );
                System.out.println( "          made today and, if they're unread, create a click deposit job" );
                System.out.println( "          for that reservation" );
                System.out.println();
                System.out.println( " --housekeeping     This will create an equivalent of an AllocationScraperJob" );
                System.out.println( "          for the previous day except it won't create the corresponding BookingScraperJob" );
                System.out.println( "          (for additional details). The Housekeeping report will pull the information" );
                System.out.println( "          directly from the allocation data" );

                System.out.println( "The current date is " + new Date() );

                System.exit( 0 );
            }

            // option -a to add allocations job
            if ( args.length == 1 && "-a".equals( args[0] ) ) {
                LOGGER.info( "Allocation job requested; queueing job..." );
                processor.insertAllocationScraperJob();
            }

            // option -d to add click deposits job
            if ( args.length == 1 && "-d".equals( args[0] ) ) {
                LOGGER.info( "Create Confirm Deposits job requested; queueing job..." );
                processor.insertCreateConfirmDepositAmountsJob();
            }

            // option -b to add click deposits job (by booking date - today)
            if ( args.length == 1 && "-b".equals( args[0] ) ) {
                LOGGER.info( "ScrapeReservationsBookedOn job requested; queueing job..." );
                processor.insertScrapeReservationsBookedOnJob();
            }

            // option -h to add a housekeeping report job
            if ( args.length == 1 && "--housekeeping".equals( args[0] ) ) {
                LOGGER.info( "Housekeeping job requested; queueing job..." );
                processor.insertHousekeepingJob();
            }

            processor.startScheduler();
            processor.processJobsLoopIndefinitely();
        }
        catch ( ShutdownException ex ) {
            LOGGER.info( "Shutdown task requested" );
        }
        finally {
            lock.release();
            context.close();
            LOGGER.info( "Finished processor... " + new Date() );
        }
    }

}
