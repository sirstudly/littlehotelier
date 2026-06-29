
package com.macbackpackers.jobs;

import java.time.LocalDate;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.Transient;

import org.htmlunit.WebClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.transaction.annotation.Transactional;

import com.macbackpackers.beans.JobStatus;
import com.macbackpackers.beans.cloudbeds.responses.Customer;
import com.macbackpackers.services.EdinburghVisitorLevyService;

/**
 * Creates {@link CalculateEdinburghVisitorLevyForBookingJob}s for bookings made within a
 * booking-date range (all statuses) whose folio EVL differs from the calculated amount.
 */
@Entity
@DiscriminatorValue( value = "com.macbackpackers.jobs.CreateCalculateEdinburghVisitorLevyForBookingJob" )
public class CreateCalculateEdinburghVisitorLevyForBookingJob extends AbstractJob {

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

        edinburghVisitorLevyService.findReservationsRequiringVisitorLevyAdjustment(
                cbWebClient, getBookingDateStart(), getBookingDateEnd() )
                .forEach( entry -> createCalculateJob( entry.getCustomer() ) );
    }

    private void createCalculateJob( Customer customer ) {
        LOGGER.info( "Creating CalculateEdinburghVisitorLevyForBookingJob for Res #{} ({}) {} {} ({} nights)",
                customer.getId(), customer.getSourceName(), customer.getFirstName(), customer.getLastName(),
                customer.getNights() );
        CalculateEdinburghVisitorLevyForBookingJob job = new CalculateEdinburghVisitorLevyForBookingJob();
        job.setStatus( JobStatus.submitted );
        job.setReservationId( customer.getId() );
        dao.insertJob( job );
    }

    @Override
    public void finalizeJob() {
        cbWebClient.close();
    }

    public LocalDate getBookingDateStart() {
        return LocalDate.parse( getParameter( "booking_date_start" ) );
    }

    public void setBookingDateStart( LocalDate bookingDateStart ) {
        setParameter( "booking_date_start", bookingDateStart.toString() );
    }

    public LocalDate getBookingDateEnd() {
        return LocalDate.parse( getParameter( "booking_date_end" ) );
    }

    public void setBookingDateEnd( LocalDate bookingDateEnd ) {
        setParameter( "booking_date_end", bookingDateEnd.toString() );
    }
}
