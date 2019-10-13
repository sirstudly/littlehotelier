
package com.macbackpackers.jobs;

import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;
import javax.persistence.Transient;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;

import com.gargoylesoftware.htmlunit.WebClient;
import com.macbackpackers.services.CloudbedsService;

/**
 * Job that attempts to login to Cloudbeds and saves the session once successful.
 */
@Entity
@DiscriminatorValue( value = "com.macbackpackers.jobs.ResetCloudbedsSessionJob" )
public class ResetCloudbedsSessionJob extends AbstractJob {

    @Autowired
    @Transient
    private CloudbedsService service;

    @Autowired
    @Transient
    private ApplicationContext appContext;

    @Override
    public void processJob() throws Exception {
        try (WebClient webClient = appContext.getBean( "webClientForCloudbedsNoValidate", WebClient.class )) {
            service.loginAndSaveSession( webClient );
        }
    }

    @Override
    public int getRetryCount() {
        return 1;
    }

}
