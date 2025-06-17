
package com.macbackpackers.jobs;

import com.macbackpackers.beans.JobStatus;
import com.macbackpackers.scrapers.CloudbedsScraper;
import com.macbackpackers.services.CloudbedsService;
import org.htmlunit.WebClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.Transient;
import java.io.IOException;

/**
 * Manual Custom Job that archives any notes matching some text.
 */
@Entity
@DiscriminatorValue( value = "com.macbackpackers.jobs.ArchiveNoteJob" )
public class ArchiveNoteJob extends AbstractJob {

    @Autowired
    @Transient
    @Qualifier( "webClientForCloudbeds" )
    private WebClient cbWebClient;

    @Autowired
    @Transient
    private CloudbedsScraper cloudbedsScraper;

    @Override
    public void processJob() throws Exception {

        cloudbedsScraper.getReservationRetry( cbWebClient, getReservationId() )
                .getNotes()
                .stream()
                .filter( p -> p.getOwnerName().equals( "RON BOT" ) )
                .forEach( n -> {
                    if ( ( n.getNotes().contains( "Outstanding balance" ) && n.getNotes().contains( "successfully charged" ) )
                            || n.getNotes().contains( "Non-Refundable Charge Successful email sent" )
                            || n.getNotes().contains( "Successfully charged deposit of" )
                            || n.getNotes().contains( "Deposit Charge Successful email sent" )
                            || n.getNotes().contains( "Processed Stripe transaction for" )
                            || ( n.getNotes().contains( "Stripe Payment Confirmation" ) && n.getNotes().contains( "email sent" ) ) ) {
                        try {
                            cloudbedsScraper.archiveNote( cbWebClient, getReservationId(), n.getId() );
                        }
                        catch ( IOException e ) {
                            throw new RuntimeException( e );
                        }
                    }
                } );
    }

    public String getReservationId() {
        return getParameter( "reservation_id" );
    }

    public void setReservationId( String reservationId ) {
        setParameter( "reservation_id", reservationId );
    }
}
