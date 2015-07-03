package com.macbackpackers;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.nio.channels.FileLock;
import java.util.Calendar;
import java.util.Date;
import java.util.TimeZone;

import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.support.AbstractApplicationContext;

import com.macbackpackers.beans.Job;
import com.macbackpackers.beans.JobStatus;
import com.macbackpackers.config.LittleHotelierConfig;
import com.macbackpackers.dao.WordPressDAO;
import com.macbackpackers.jobs.AllocationScraperJob;
import com.macbackpackers.jobs.CreateConfirmDepositAmountsJob;
import com.macbackpackers.jobs.HousekeepingJob;
import com.macbackpackers.jobs.ScrapeReservationsBookedOnJob;
import com.macbackpackers.scrapers.BookingsPageScraper;
import com.macbackpackers.services.FileService;
import com.macbackpackers.services.ProcessorService;

/**
 * The main bootstrap for running all available jobs.
 *
 */
public class RunProcessor implements Closeable
{
    private static final Logger LOGGER = LogManager.getLogger( RunProcessor.class );
    private AbstractApplicationContext context;
    private ProcessorService processorService;
    private FileService fileService;
    private WordPressDAO dao;
    
    public RunProcessor() {
        context = new AnnotationConfigApplicationContext(LittleHotelierConfig.class);
        processorService = context.getBean(ProcessorService.class);
        fileService = context.getBean(FileService.class);
        dao = context.getBean(WordPressDAO.class);
    }
    
    public void close() throws IOException {
        context.close();
    }

    /**
     * Processes all available jobs. To ensure only one processor is running
     * at a time, attempt to get an exclusive file lock before attempting.
     * If a lock is not obtained, this method returns immediately.
     * 
     * @throws IOException if file could not be read/created
     */
    public void processJobs() throws IOException {
        FileLock lock = fileService.lockFile( new File( "processor.lock" ) );
        if( lock != null ) {
            dao.resetAllProcessingJobsToFailed();
            processorService.processJobs();
            lock.release();
        }
    }
    
    /**
     * Creates an allocation scraper job.
     */
    public void insertAllocationScraperJob() {
        Job j = new AllocationScraperJob();
        j.setStatus( JobStatus.submitted );
        
        Calendar c = Calendar.getInstance();
        j.setParameter( "start_date", WordPressDAO.DATE_FORMAT_YYYY_MM_DD.format( c.getTime() ) );
        
        // scrape the next 4 months
        c.add( Calendar.MONTH, 4 );

        j.setParameter( "end_date", WordPressDAO.DATE_FORMAT_YYYY_MM_DD.format( c.getTime() ) );
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
    public static void main(String args[]) throws Exception {
        TimeZone.setDefault(TimeZone.getTimeZone("Europe/London"));
        LOGGER.info( "Starting processor... " + new Date() );
        RunProcessor processor = new RunProcessor();
        
        // option -h for help
        if( args.length == 1 && "-h".equals( args[0] ) ) {
            System.out.println( "USAGE: " + RunProcessor.class.getName() );
            System.out.println( "Flags (only one allowed at a time)");
            System.out.println( "    -a    Adds an allocation scraper job");
            System.out.println( "          This will queue up all the report jobs");
            System.out.println();
            System.out.println( "    -d    Create a click deposits job");
            System.out.println( "          This will find the last completed AllocationScraperJob and find");
            System.out.println( "          all HW/HB reservations where the total amount = payment outstanding");
            System.out.println( "          and create a click deposit job for each one");
            System.out.println();
            System.out.println( "    -b    This will create a job that will search all bookings on HW/HB");
            System.out.println( "          made today and, if they're unread, create a click deposit job");
            System.out.println( "          for that reservation");
            System.out.println();
            System.out.println( " --housekeeping     This will create an equivalent of an AllocationScraperJob");
            System.out.println( "          for the previous day except it won't create the corresponding BookingScraperJob");
            System.out.println( "          (for additional details). The Housekeeping report will pull the information");
            System.out.println( "          directly from the allocation data");
            
            System.out.println( "The current date is " + new Date() );

            System.exit( 0 );
        }

        // option -a to add allocations job
        if( args.length == 1 && "-a".equals( args[0] ) ) {
            LOGGER.info( "Allocation job requested; queueing job..." );
            processor.insertAllocationScraperJob();
        }
        
        // option -d to add click deposits job
        if( args.length == 1 && "-d".equals( args[0] ) ) {
            LOGGER.info( "Create Confirm Deposits job requested; queueing job..." );
            processor.insertCreateConfirmDepositAmountsJob();
        }
        
        // option -b to add click deposits job (by booking date - today)
        if( args.length == 1 && "-b".equals( args[0] ) ) {
            LOGGER.info( "ScrapeReservationsBookedOn job requested; queueing job..." );
            processor.insertScrapeReservationsBookedOnJob();
        }

        // option -h to add a housekeeping report job
        if( args.length == 1 && "--housekeeping".equals( args[0] ) ) {
            LOGGER.info( "Housekeeping job requested; queueing job..." );
            processor.insertHousekeepingJob();
        }

        processor.processJobs();
        processor.close();
        LOGGER.info( "Finished processor... " + new Date() );
    }
    
}
