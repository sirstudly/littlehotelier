
package com.macbackpackers.jobs;

import java.time.LocalDate;

import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;
import javax.persistence.Transient;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;

import com.gargoylesoftware.htmlunit.WebClient;
import com.macbackpackers.services.CloudbedsService;

/**
 * Job that creates send email jobs for the Hogmanay email.
 */
@Entity
@DiscriminatorValue( value = "com.macbackpackers.jobs.CreateSendHogmanayEmailJob" )
public class CreateSendHogmanayEmailJob extends AbstractJob {

    @Autowired
    @Transient
    private ApplicationContext appContext;

    @Autowired
    @Transient
    private CloudbedsService cloudbedsService;

    @Override
    public void processJob() throws Exception {
        try (WebClient webClient = appContext.getBean( "webClientForCloudbedsNoValidate", WebClient.class )) {
            cloudbedsService.createBulkEmailJob( webClient, "Hogmanay at Castle Rock!",
                    LocalDate.now().withMonth( 12 ).withDayOfMonth( 31 ),
                    LocalDate.now().withMonth( 12 ).withDayOfMonth( 31 ),
                    null, null, "confirmed,not_confirmed" );
        }
    }

}
