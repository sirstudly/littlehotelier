
package com.macbackpackers;

import java.util.Date;
import java.util.TimeZone;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.support.AbstractApplicationContext;
import org.springframework.stereotype.Component;

import com.macbackpackers.beans.Job;
import com.macbackpackers.beans.ScheduledJob;
import com.macbackpackers.config.LittleHotelierConfig;
import com.macbackpackers.dao.WordPressDAO;

/**
 * Bootstrap for inserting a Job using the ScheduledJob table (rather than using the internal
 * SchedulerService).
 *
 */
@Component
public class InsertJob {

    private static final Logger LOGGER = LogManager.getLogger( InsertJob.class );

    @Autowired
    private WordPressDAO dao;

    /**
     * Loads the scheduled job and creates a new job based on it.
     * 
     * @param scheduledJobId the scheduled job ID
     * @throws ReflectiveOperationException
     */
    public void insertJob( int scheduledJobId ) throws ReflectiveOperationException {

        ScheduledJob schedJob = dao.fetchScheduledJobById( scheduledJobId );
        Job j = schedJob.createNewJob();

        // insert the pending job into DB; let the processor handle it
        dao.insertJob( j );
        dao.updateScheduledJob( scheduledJobId );
    }

    /**
     * Bootstrap for inserting jobs.
     * 
     * @param argv
     * @throws Exception
     */
    public static void main( String argv[] ) throws Exception {

        // create the command line parser
        CommandLineParser parser = new DefaultParser();

        // create the Options
        Options options = new Options();
        options.addOption( "h", "Show this help message" );

        AbstractApplicationContext context = null;
        try {
            // parse the command line arguments
            CommandLine line = parser.parse( options, argv );

            // automatically generate the help statement
            if ( line.hasOption( "h" ) || line.getArgs().length == 0 ) {
                HelpFormatter formatter = new HelpFormatter();
                formatter.printHelp( InsertJob.class.getName() + " <ID> <ID>...", "Each <ID> must correspond with an existing entry in wp_lh_scheduled_jobs\nOptions:", options, null );
            }
            else {

                TimeZone.setDefault( TimeZone.getTimeZone( "Europe/London" ) );
                LOGGER.info( "Processing arguments... " + new Date() );
                context = new AnnotationConfigApplicationContext( LittleHotelierConfig.class );
                InsertJob insertJob = context.getBean( InsertJob.class );

                // otherwise, create all the jobs
                for ( String schedJobId : line.getArgs() ) {
                    LOGGER.info( "Inserting Job " + schedJobId );
                    insertJob.insertJob( Integer.parseInt( schedJobId ) );
                }
            }
        }
        catch ( Throwable th ) {
            System.err.println( "Unexpected exception: " );
            th.printStackTrace();
        }
        finally {
            if ( context != null ) {
                context.close();
                LOGGER.info( "Finished inserting job... " + new Date() );
            }
        }
    }
}
