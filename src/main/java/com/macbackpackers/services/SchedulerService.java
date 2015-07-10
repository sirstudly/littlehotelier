
package com.macbackpackers.services;

import java.util.TimeZone;

import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.quartz.CronScheduleBuilder;
import org.quartz.JobBuilder;
import org.quartz.JobDataMap;
import org.quartz.JobDetail;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.Trigger;
import org.quartz.TriggerBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.macbackpackers.beans.ScheduledJob;
import com.macbackpackers.beans.ScheduledJobParameter;
import com.macbackpackers.dao.WordPressDAO;
import com.macbackpackers.jobs.quartz.CronJob;

/**
 * CRON Service for scheduling jobs.
 */
@Service
public class SchedulerService {

    private final Logger LOGGER = LogManager.getLogger( getClass() );

    @Autowired
    private WordPressDAO dao;

    @Autowired
    private Scheduler scheduler;

    /**
     * Loads the scheduled jobs from the DB and starts the scheduler.
     * 
     * @throws SchedulerException on scheduling error
     */
    public void reloadScheduledJobs() throws SchedulerException {

        // clear out existing jobs
        scheduler.clear();

        // reload everything from the db
        for ( ScheduledJob job : dao.fetchActiveScheduledJobs() ) {
            LOGGER.info( "Scheduling job " + job.getClassname() + " for schedule: " + job.getCronSchedule() );

            // buildup job parameters
            JobDataMap jobData = new JobDataMap();
            jobData.put( CronJob.PARAM_DAO, dao );
            for ( ScheduledJobParameter param : job.getParameters() ) {
                jobData.put( param.getName(), param.getValue() );
            }

            JobDetail jobDetail = JobBuilder.newJob( CronJob.class )
                    .withIdentity( "job " + job.getClassname() + job.getId() )
                    .usingJobData( CronJob.PARAM_CLASSNAME, job.getClassname() )
                    .usingJobData( CronJob.PARAM_SCHEDULED_JOB_ID, job.getId() )
                    .usingJobData( jobData )
                    .build();

            Trigger trigger = TriggerBuilder.newTrigger()
                    .withIdentity( "trigger " + job.getClassname() + job.getId() )
                    .withSchedule( CronScheduleBuilder
                            .cronSchedule( job.getCronSchedule() )
                            .inTimeZone( TimeZone.getTimeZone( "Europe/London" ) )
                            .withMisfireHandlingInstructionIgnoreMisfires() )
                    .startNow()
                    .build();

            // schedule job
            scheduler.scheduleJob( jobDetail, trigger );
        }

        // start running jobs
        scheduler.start();
    }

    /**
     * Shuts down the scheduler.
     * 
     * @throws SchedulerException on scheduling error
     */
    public void shutdown() throws SchedulerException {
        scheduler.shutdown();
    }

}
