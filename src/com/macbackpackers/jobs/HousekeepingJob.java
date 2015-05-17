package com.macbackpackers.jobs;

import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

/**
 * Job that updates the housekeeping tables for the given date
 *
 */
@Component
@Scope( "prototype" )
public class HousekeepingJob extends AbstractJob {
    
    @Override
    public void processJob() throws Exception {
        // TODO
    }
}