
package com.macbackpackers.jobs;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.Transient;

import com.google.gson.Gson;
import org.apache.commons.lang3.StringUtils;
import org.htmlunit.WebClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationContext;

import com.macbackpackers.services.CloudbedsService;

import java.util.Collections;
import java.util.Map;

/**
 * Job that sends email from a template.
 */
@Entity
@DiscriminatorValue( value = "com.macbackpackers.jobs.SendTemplatedEmailJob" )
public class SendTemplatedEmailJob extends AbstractJob {

    @Autowired
    @Transient
    private CloudbedsService cloudbedsService;

    @Autowired
    @Transient
    private ApplicationContext appContext;

    @Autowired
    @Qualifier( "gsonForCloudbeds" )
    @Transient
    private Gson gson;

    @Override
    public void processJob() throws Exception {
        try( WebClient webClient = appContext.getBean( "webClientForCloudbeds", WebClient.class ) ) {
            if( dao.isCloudbedsEmailEnabled() ) {
                cloudbedsService.sendTemplatedEmail( webClient, getReservationId(), getEmailTemplate() );
            }
            else {
                cloudbedsService.sendTemplatedGmail( webClient, getReservationId(), getEmailTemplate(), getReplacementMap(), isNoteArchived() );
            }
        }
    }

    /**
     * Returns the reservation id.
     *
     * @return reservationId
     */
    public String getReservationId() {
        return getParameter( "reservation_id" );
    }

    /**
     * Sets the reservation id.
     *
     * @param reservationId
     */
    public void setReservationId( String reservationId ) {
        setParameter( "reservation_id", reservationId );
    }

    public String getEmailTemplate() {
        return getParameter( "email_template" );
    }

    public void setEmailTemplate( String template ) {
        setParameter( "email_template", template );
    }

    public boolean isNoteArchived() {
        return "Y".equalsIgnoreCase( getParameter( "archive_note_yn" ) );
    }

    public void setNoteArchived( boolean archiveNote ) {
        setParameter( "archive_note_yn", archiveNote ? "Y" : "N" );
    }

    public void setReplacementMap( Map<String, String> replacementMap ) {
        setParameter( "replacement_map", gson.toJson( replacementMap ) );
    }

    public Map<String, String> getReplacementMap() {
        String replacementMap = getParameter( "replacement_map" );
        return StringUtils.isBlank( replacementMap ) ? Collections.EMPTY_MAP : gson.fromJson( replacementMap, Map.class );
    }

    @Override
    public int getRetryCount() {
        return 1; // limit failed email attempts
    }
}
