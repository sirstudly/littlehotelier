package com.macbackpackers.jobs;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.Transient;

import org.apache.commons.lang3.StringUtils;
import org.htmlunit.WebClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;

import com.macbackpackers.exceptions.MissingUserDataException;
import com.macbackpackers.services.CloudbedsService;

/**
 * Job that creates individual jobs for emailing guests from the latest mostly-full dorm report.
 * Requires non-blank job parameter {@code email_template} (Cloudbeds template name).
 *
 */
@Entity
@DiscriminatorValue( value = "com.macbackpackers.jobs.CreateSendMostlyFullDormEmailJob" )
public class CreateSendMostlyFullDormEmailJob extends AbstractJob {

    @Autowired
    @Transient
    private ApplicationContext appContext;

    @Autowired
    @Transient
    private CloudbedsService cloudbedsService;

    @Override
    public void processJob() throws Exception {
        try ( WebClient webClient = appContext.getBean( "webClientForCloudbeds", WebClient.class ) ) {
            cloudbedsService.createSendMostlyFullDormEmailJobs( webClient, getEmailTemplate() );
        }
    }

    public String getEmailTemplate() {
        String emailTemplate = getParameter( "email_template" );
        if ( StringUtils.isBlank( emailTemplate ) ) {
            throw new MissingUserDataException( "Missing or blank parameter email_template" );
        }
        return emailTemplate;
    }

    public void setEmailTemplate( String template ) {
        setParameter( "email_template", template );
    }

}
