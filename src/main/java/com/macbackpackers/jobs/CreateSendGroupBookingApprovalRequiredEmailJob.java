
package com.macbackpackers.jobs;

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
            cloudbedsService.createSendGroupBookingApprovalRequiredEmailJobs( webClient, getBookingDate(), LocalDate.now() );
        }
    }

    /**
     * Gets the (start) booking date.
     * 
     * @return non-null date parameter
     */
    public LocalDate getBookingDate() {
        return LocalDate.parse( getParameter( "booking_date" ) );
    }

    /**
     * Sets the (start) booking date.
     * 
     * @param bookingDate non-null date
     */
    public void setBookingDate( LocalDate bookingDate ) {
        setParameter( "booking_date", bookingDate.toString() );
    }

}
