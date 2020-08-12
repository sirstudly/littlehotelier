
package com.macbackpackers.jobs;

import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;
import javax.persistence.Transient;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;

import com.gargoylesoftware.htmlunit.WebClient;
import com.macbackpackers.services.CloudbedsService;

/**
 * Loads the Cloudbeds calendar page and updates the saved cookies.
 */
@Entity
@DiscriminatorValue( value = "com.macbackpackers.jobs.LoadCloudbedsCalendarJob" )
public class LoadCloudbedsCalendarJob extends AbstractJob {

    @Autowired
    @Transient
    private CloudbedsService service;

    @Autowired
    @Transient
    private ApplicationContext appContext;

    @Override
    public void processJob() throws Exception {
        try (WebClient webClient = appContext.getBean( "webClientForCloudbeds", WebClient.class )) {
            service.initiateWssOnCalendarPage( webClient );
        }
    }

}
