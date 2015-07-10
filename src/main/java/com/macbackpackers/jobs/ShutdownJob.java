
package com.macbackpackers.jobs;

import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;
import javax.persistence.Transient;

import org.springframework.beans.factory.annotation.Autowired;

import com.macbackpackers.services.SchedulerService;

/**
 * Job that shuts down this application immediately after the last executing job has completed.
 *
 */
@Entity
@DiscriminatorValue( value = "com.macbackpackers.jobs.ShutdownJob" )
public class ShutdownJob extends AbstractJob {

    @Autowired
    @Transient
    private SchedulerService scheduler;

    @Override
    public void processJob() throws Exception {
        scheduler.shutdown(); // take out all the active threads
    }

}
