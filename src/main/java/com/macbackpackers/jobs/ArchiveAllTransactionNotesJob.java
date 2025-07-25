package com.macbackpackers.jobs;

import com.macbackpackers.services.CloudbedsService;
import org.htmlunit.WebClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.Transient;

/**
 * Job that archives all previously generated transaction notes for a particular reservation (if no balance remaining)
 */
@Entity
@DiscriminatorValue( value = "com.macbackpackers.jobs.ArchiveAllTransactionNotesJob" )
public class ArchiveAllTransactionNotesJob extends AbstractJob {

    @Autowired
    @Transient
    private CloudbedsService cloudbedsService;

    @Autowired
    @Transient
    @Qualifier("webClientForCloudbeds")
    private WebClient webClient;

    @Override
    public void processJob() throws Exception {
        cloudbedsService.archiveAllTransactionNotes( webClient, getReservationId() );
    }

    @Override
    public void finalizeJob() {
        webClient.close(); // cleans up JS threads
    }

    public String getReservationId() {
        return getParameter( "reservation_id" );
    }

    public void setReservationId( String reservationId ) {
        setParameter( "reservation_id", reservationId );
    }
}
