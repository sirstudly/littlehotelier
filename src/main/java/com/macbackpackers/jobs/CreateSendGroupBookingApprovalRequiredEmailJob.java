
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
 * Job that creates individual jobs for sending out emails for group bookings.
 *
 */
@Entity
@DiscriminatorValue( value = "com.macbackpackers.jobs.CreateSendGroupBookingApprovalRequiredEmailJob" )
public class CreateSendGroupBookingApprovalRequiredEmailJob extends AbstractJob {

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
            cloudbedsService.createSendGroupBookingApprovalRequiredEmailJobs( webClient, LocalDate.now().minusDays( getDaysBefore() ), LocalDate.now() );
        }
    }

    public int getDaysBefore() throws ParseException {
        return Integer.parseInt( getParameter( "days_before" ) );
    }

    public void setDaysBefore( int daysBefore ) {
        setParameter( "days_before", String.valueOf( daysBefore ) );
    }
}
