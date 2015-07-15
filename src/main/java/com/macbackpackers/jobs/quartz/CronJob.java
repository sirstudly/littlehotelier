
package com.macbackpackers.jobs.quartz;

import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.time.FastDateFormat;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.quartz.JobDataMap;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;

import com.macbackpackers.beans.Job;
import com.macbackpackers.beans.JobStatus;
import com.macbackpackers.dao.WordPressDAO;

/**
 * This is instantiated whenever the trigger gets fired (when the scheduler deems it's time to run
 * the job)
 *
 */
public class CronJob implements org.quartz.Job {

    private final Logger LOGGER = LogManager.getLogger( getClass() );

    public static final FastDateFormat DATE_FORMAT_YYYY_MM_DD = FastDateFormat.getInstance( "yyyy-MM-dd" );

    /** Name of the class that is being scheduled */
    public static final String PARAM_CLASSNAME = "classname";
    /** The DAO used for inserting the job */
    public static final String PARAM_DAO = "dao";
    /** The scheduled job id that kicked this cronjob off */
    public static final String PARAM_SCHEDULED_JOB_ID = "scheduled_job_id";

    // convenience for checking against all pre-defined keys
    private static final List<String> KEYS = Arrays.asList( PARAM_CLASSNAME, PARAM_DAO, PARAM_SCHEDULED_JOB_ID );

    public void execute( JobExecutionContext ctx ) throws JobExecutionException {

        LOGGER.info( "JOB FIRED: " + ctx.getJobDetail().getKey().getName() );
        try {
            // create the actual job that is going to be run
            JobDataMap params = ctx.getJobDetail().getJobDataMap();
            WordPressDAO dao = WordPressDAO.class.cast( params.get( PARAM_DAO ) );

            Job j = Job.class.cast( Class.forName( params.get( PARAM_CLASSNAME ).toString() ).newInstance() );
            j.setStatus( JobStatus.submitted );

            // now copy the parameters from the scheduled job to the actual job we're creating
            for ( String paramName : params.keySet() ) {
                if ( false == KEYS.contains( paramName ) ) {

                    Pattern p = Pattern.compile( "TODAY([\\+\\-][0-9]+)$" );
                    Matcher m = p.matcher( params.getString( paramName ) );

                    // auto-fill TODAY with today's date
                    if ( "TODAY".equals( params.getString( paramName ) ) ) {
                        j.setParameter( paramName, DATE_FORMAT_YYYY_MM_DD.format( new Date() ) );
                    }
                    // auto-fill TODAY(+/- adjustment value)
                    else if ( m.find() ) {
                        String matchedAdjustment = m.group( 1 );
                        Calendar cal = Calendar.getInstance();
                        cal.add( Calendar.DATE, Integer.parseInt( matchedAdjustment ) );
                        j.setParameter( paramName, DATE_FORMAT_YYYY_MM_DD.format( cal.getTime() ) );
                    }
                    else {
                        j.setParameter( paramName, params.getString( paramName ) );
                    }
                }
            }

            // insert the pending job into DB; let the processor handle it
            dao.insertJob( j );
            dao.updateScheduledJob( params.getInt( PARAM_SCHEDULED_JOB_ID ) );
        }
        catch ( InstantiationException e ) {
            throw new JobExecutionException( e );
        }
        catch ( IllegalAccessException e ) {
            throw new JobExecutionException( e );
        }
        catch ( ClassNotFoundException e ) {
            throw new JobExecutionException( e );
        }
    }
}
