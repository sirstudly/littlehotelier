
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
 * Job that creates send email jobs for guests staying for Christmas.
 */
@Entity
@DiscriminatorValue( value = "com.macbackpackers.jobs.CreateSendChristmasLunchEmailJob" )
public class CreateSendChristmasLunchEmailJob extends AbstractJob {

    @Autowired
    @Transient
    private ApplicationContext appContext;

    @Autowired
    @Transient
    private CloudbedsService cloudbedsService;

    @Override
    public void processJob() throws Exception {
        try (WebClient webClient = appContext.getBean( "webClientForCloudbeds", WebClient.class )) {
            cloudbedsService.createBulkEmailJob( webClient, "Christmas at Castle Rock!",
                    LocalDate.now().withMonth( 12 ).withDayOfMonth( 25 ),
                    LocalDate.now().withMonth( 12 ).withDayOfMonth( 25 ),
                    null, null, "confirmed,not_confirmed" );
        }
    }

}
