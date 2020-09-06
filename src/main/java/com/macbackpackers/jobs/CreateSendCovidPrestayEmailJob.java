
package com.macbackpackers.jobs;

import java.text.ParseException;
import java.time.LocalDate;

import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;
import javax.persistence.Transient;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationContext;

import com.gargoylesoftware.htmlunit.WebClient;
import com.macbackpackers.services.CloudbedsService;

/**
 * Job that creates individual jobs for sending out emails to guests due to checkin.
 *
 */
@Entity
@DiscriminatorValue( value = "com.macbackpackers.jobs.CreateSendCovidPrestayEmailJob" )
public class CreateSendCovidPrestayEmailJob extends AbstractJob {

    @Autowired
    @Transient
    private ApplicationContext appContext;

    @Autowired
    @Transient
    private CloudbedsService cloudbedsService;

    @Autowired
    @Transient
    @Qualifier( "webClient" )
    private WebClient webClient;

    @Override
    public void processJob() throws Exception {
        try (WebClient webClient = appContext.getBean( "webClientForCloudbeds", WebClient.class )) {
            cloudbedsService.createSendCovidPrestayEmailJobs( webClient, LocalDate.now().plusDays( getDaysBeforeCheckin() ) );
        }
    }

    public int getDaysBeforeCheckin() throws ParseException {
        return Integer.parseInt( getParameter( "days_before" ) );
    }

    public void setDaysBeforeCheckin( int daysBefore ) {
        setParameter( "days_before", String.valueOf( daysBefore ) );
    }
}
