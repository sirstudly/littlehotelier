
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
 * Job that creates individual jobs for sending out emails to guests.
 *
 */
@Entity
@DiscriminatorValue( value = "com.macbackpackers.jobs.CreateSendBulkEmailJob" )
public class CreateSendBulkEmailJob extends AbstractJob {

    @Autowired
    @Transient
    private ApplicationContext appContext;

    @Autowired
    @Transient
    private CloudbedsService cloudbedsService;

    @Override
    public void processJob() throws Exception {
        try (WebClient webClient = appContext.getBean( "webClientForCloudbeds", WebClient.class )) {
            cloudbedsService.createSendTemplatedEmailJobs( webClient,
                    getEmailTemplate(), getCheckinDateStart(), getCheckinDateEnd() );
        }
    }

    public LocalDate getCheckinDateStart() {
        return LocalDate.parse( getParameter( "checkin_date_start" ) );
    }

    public void setCheckinDateStart( LocalDate checkinDateStart ) {
        setParameter( "checkin_date_start", checkinDateStart.toString() );
    }

    public LocalDate getCheckinDateEnd() {
        return LocalDate.parse( getParameter( "checkin_date_end" ) );
    }

    public void setCheckinDateEnd( LocalDate checkinDateEnd ) {
        setParameter( "checkin_date_end", checkinDateEnd.toString() );
    }

    public String getEmailTemplate() {
        return getParameter( "email_template" );
    }

    public void setEmailTemplate( String template ) {
        setParameter( "email_template", template );
    }
}
