package com.macbackpackers;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.nio.channels.FileLock;
import java.util.Calendar;
import java.util.Date;

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
        Job j = new Job();
        j.setClassName( AllocationScraperJob.class.getName() );
        j.setStatus( JobStatus.submitted );
        
        Calendar c = Calendar.getInstance();
        j.setParameter( "start_date", WordPressDAO.DATE_FORMAT_YYYY_MM_DD.format( c.getTime() ) );
        
        // scrape the next 4 months
        c.add( Calendar.MONTH, 4 );

        j.setParameter( "end_date", WordPressDAO.DATE_FORMAT_YYYY_MM_DD.format( c.getTime() ) );
        dao.insertJob( j );
    }

    public void insertCreateConfirmDepositAmountsJob() {
        Job j = new Job();
        j.setClassName( CreateConfirmDepositAmountsJob.class.getName() );
        j.setStatus( JobStatus.submitted );
        dao.insertJob( j );
    }

    /**
     * Runs the processor.
     * 
     * @param args no arguments expected
     * @throws Exception on disastrous failure
     */
    public static void main(String args[]) throws Exception {
        LOGGER.info( "Starting processor... " + new Date() );
        RunProcessor processor = new RunProcessor();
        
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
        
        processor.processJobs();
        processor.close();
        LOGGER.info( "Finished processor... " + new Date() );
    }
    
}
