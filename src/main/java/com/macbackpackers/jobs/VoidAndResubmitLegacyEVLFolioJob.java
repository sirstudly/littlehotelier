package com.macbackpackers.jobs;

import com.macbackpackers.services.EdinburghVisitorLevyService;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.Transient;
import org.htmlunit.WebClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

/**
 * Voids folio lines labeled "Edinburgh Visitor Levy 2026" and re-posts the same amounts under
 * "Edinburgh Visitor Levy".
 */
@Entity
@DiscriminatorValue( value = "com.macbackpackers.jobs.VoidAndResubmitLegacyEVLFolioJob" )
public class VoidAndResubmitLegacyEVLFolioJob extends AbstractJob {

    @Autowired
    @Transient
    @Qualifier( "webClientForCloudbeds" )
    private WebClient cbWebClient;

    @Autowired
    @Transient
    private EdinburghVisitorLevyService edinburghVisitorLevyService;

    @Override
    public void processJob() throws Exception {
        edinburghVisitorLevyService.voidAndResubmitLegacyEvlFolio( cbWebClient, getReservationId() );
    }

    @Override
    public void finalizeJob() {
        cbWebClient.close();
    }

    public String getReservationId() {
        return getParameter( "reservation_id" );
    }

    public void setReservationId( String reservationId ) {
        setParameter( "reservation_id", reservationId );
    }
}
