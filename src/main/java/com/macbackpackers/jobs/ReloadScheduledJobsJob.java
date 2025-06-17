
package com.macbackpackers.jobs;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.Transient;

import org.springframework.beans.factory.annotation.Autowired;

import com.macbackpackers.services.SchedulerService;

/**
 * This job clears and reloads the Quartz scheduler from the database.
 * 
 */
@Entity
@DiscriminatorValue( value = "com.macbackpackers.jobs.ReloadScheduledJobsJob" )
public class ReloadScheduledJobsJob extends AbstractJob {

    @Autowired
    @Transient
    private SchedulerService schedulerService;

    @Override
    public void processJob() throws Exception {
        schedulerService.reloadScheduledJobs();
    }

}
