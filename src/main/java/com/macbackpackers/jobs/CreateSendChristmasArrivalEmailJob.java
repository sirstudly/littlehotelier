
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
 * Job that creates send email jobs for all arrivals on Christmas day.
 */
@Entity
@DiscriminatorValue( value = "com.macbackpackers.jobs.CreateSendChristmasArrivalEmailJob" )
public class CreateSendChristmasArrivalEmailJob extends AbstractJob {

    @Autowired
    @Transient
    private ApplicationContext appContext;

    @Autowired
    @Transient
    private CloudbedsService cloudbedsService;

    @Override
    public void processJob() throws Exception {
        try (WebClient webClient = appContext.getBean( "webClientForCloudbedsNoValidate", WebClient.class )) {
            cloudbedsService.createBulkEmailJob( webClient, "Arriving on Christmas Day!", null, null,
                    LocalDate.now().withMonth( 12 ).withDayOfMonth( 25 ),
                    LocalDate.now().withMonth( 12 ).withDayOfMonth( 25 ), "confirmed,not_confirmed" );
        }
    }

}
