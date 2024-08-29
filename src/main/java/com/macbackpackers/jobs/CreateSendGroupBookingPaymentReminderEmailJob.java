
package com.macbackpackers.jobs;

import java.text.ParseException;
import java.time.LocalDate;

import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;
import javax.persistence.Transient;

import org.htmlunit.WebClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationContext;

import com.macbackpackers.services.CloudbedsService;

/**
 * Job that creates individual jobs for sending out emails for group bookings.
 *
 */
@Entity
@DiscriminatorValue( value = "com.macbackpackers.jobs.CreateSendGroupBookingPaymentReminderEmailJob" )
public class CreateSendGroupBookingPaymentReminderEmailJob extends AbstractJob {

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
            cloudbedsService.createSendGroupBookingPaymentReminderEmailJobs( webClient, LocalDate.now().plusDays( 1 ), LocalDate.now().plusDays( getDaysBefore() ) );
        }
    }

    public int getDaysBefore() throws ParseException {
        return Integer.parseInt( getParameter( "days_before" ) );
    }

    public void setDaysBefore( int daysBefore ) {
        setParameter( "days_before", String.valueOf( daysBefore ) );
    }
}
