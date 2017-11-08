
package com.macbackpackers.jobs.quartz;

import org.quartz.JobDataMap;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.macbackpackers.beans.ScheduledJob;
import com.macbackpackers.dao.WordPressDAO;

/**
 * This is instantiated whenever the trigger gets fired (when the scheduler deems it's time to run
 * the job)
 *
 */
public class CronJob implements org.quartz.Job {

    private final Logger LOGGER = LoggerFactory.getLogger( getClass() );

    /** The job that is being scheduled */
    public static final String PARAM_SCHEDULED_JOB = "scheduled_job";
    /** The DAO used for inserting the job */
    public static final String PARAM_DAO = "dao";

    public void execute( JobExecutionContext ctx ) throws JobExecutionException {

        LOGGER.info( "JOB FIRED: " + ctx.getJobDetail().getKey().getName() );
        try {
            // create the actual job that is going to be run
            JobDataMap params = ctx.getJobDetail().getJobDataMap();
            WordPressDAO dao = WordPressDAO.class.cast( params.get( PARAM_DAO ) );
            ScheduledJob schedJob = ScheduledJob.class.cast( params.get( PARAM_SCHEDULED_JOB ) );

            // insert the pending job into DB; let the processor handle it
            dao.insertJob( schedJob.createNewJob() );
            dao.updateScheduledJob( schedJob.getId() );
        }
        catch ( ReflectiveOperationException e ) {
            throw new JobExecutionException( e );
        }
    }
}
