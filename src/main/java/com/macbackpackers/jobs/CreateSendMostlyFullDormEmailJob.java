package com.macbackpackers.jobs;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.Transient;

import org.htmlunit.WebClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;

import com.macbackpackers.services.CloudbedsService;

/**
 * Job that creates individual jobs for emailing guests from the latest mostly-full dorm report.
 * Requires WP option {@code hbo_mostly_full_dorm_email_template}.
 *
 */
@Entity
@DiscriminatorValue( value = "com.macbackpackers.jobs.CreateSendMostlyFullDormEmailJob" )
public class CreateSendMostlyFullDormEmailJob extends AbstractJob {

    @Autowired
    @Transient
    private ApplicationContext appContext;

    @Autowired
    @Transient
    private CloudbedsService cloudbedsService;

    @Override
    public void processJob() throws Exception {
        try ( WebClient webClient = appContext.getBean( "webClientForCloudbeds", WebClient.class ) ) {
            cloudbedsService.createSendMostlyFullDormEmailJobs( webClient );
        }
    }

}
