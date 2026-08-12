package com.macbackpackers.jobs;

import java.time.LocalDate;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.Transient;

import org.apache.commons.lang3.StringUtils;
import org.htmlunit.WebClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.transaction.annotation.Transactional;

import com.macbackpackers.beans.JobStatus;
import com.macbackpackers.beans.cloudbeds.responses.Customer;
import com.macbackpackers.services.EdinburghVisitorLevyService;

/**
 * Creates {@link VoidAndResubmitLegacyEVLFolioJob}s for bookings in a checkin-date range (all
 * statuses) that still have voidable folio lines labeled "Edinburgh Visitor Levy 2026".
 */
@Entity
@DiscriminatorValue( value = "com.macbackpackers.jobs.CreateVoidAndResubmitLegacyEVLFolioJob" )
public class CreateVoidAndResubmitLegacyEVLFolioJob extends AbstractJob {

    @Autowired
    @Transient
    @Qualifier( "webClientForCloudbeds" )
    private WebClient cbWebClient;

    @Autowired
    @Transient
    private EdinburghVisitorLevyService edinburghVisitorLevyService;

    @Override
    @Transactional
    public void processJob() throws Exception {
        if ( false == dao.isCloudbeds() ) {
            return;
        }

        LocalDate checkinDateStart = getCheckinDateStart();
        LocalDate checkinDateEnd = getCheckinDateEnd();
        if ( checkinDateStart == null || checkinDateEnd == null ) {
            throw new IllegalArgumentException( "checkin_date_start and checkin_date_end must both be set" );
        }

        edinburghVisitorLevyService.findReservationsWithLegacyEvlFolioLines(
                cbWebClient, checkinDateStart, checkinDateEnd )
                .forEach( this::createVoidAndResubmitJob );
    }

    private void createVoidAndResubmitJob( Customer customer ) {
        LOGGER.info( "Creating VoidAndResubmitLegacyEVLFolioJob for Res #{} ({}) {} {} ({} nights)",
                customer.getId(), customer.getSourceName(), customer.getFirstName(), customer.getLastName(),
                customer.getNights() );
        VoidAndResubmitLegacyEVLFolioJob job = new VoidAndResubmitLegacyEVLFolioJob();
        job.setStatus( JobStatus.submitted );
        job.setReservationId( customer.getId() );
        dao.insertJob( job );
    }

    @Override
    public void finalizeJob() {
        cbWebClient.close();
    }

    public LocalDate getCheckinDateStart() {
        String checkinDateStart = getParameter( "checkin_date_start" );
        return StringUtils.isBlank( checkinDateStart ) ? null : LocalDate.parse( checkinDateStart );
    }

    public void setCheckinDateStart( LocalDate checkinDateStart ) {
        setParameter( "checkin_date_start", checkinDateStart.toString() );
    }

    public LocalDate getCheckinDateEnd() {
        String checkinDateEnd = getParameter( "checkin_date_end" );
        return StringUtils.isBlank( checkinDateEnd ) ? null : LocalDate.parse( checkinDateEnd );
    }

    public void setCheckinDateEnd( LocalDate checkinDateEnd ) {
        setParameter( "checkin_date_end", checkinDateEnd.toString() );
    }
}
