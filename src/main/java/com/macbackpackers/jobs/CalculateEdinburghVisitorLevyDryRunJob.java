
package com.macbackpackers.jobs;

import java.time.LocalDate;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.Transient;

import org.htmlunit.WebClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

import com.macbackpackers.beans.cloudbeds.responses.Customer;
import com.macbackpackers.services.EdinburghVisitorLevyService;
import com.macbackpackers.services.EdinburghVisitorLevyService.LevyAssessment;

/**
 * Dry-run visitor levy calculation for active bookings in a booking-date range that would normally
 * be queued by {@link CreateCalculateEdinburghVisitorLevyForBookingJob}. Logs whether each
 * reservation needs an adjustment without posting charges.
 */
@Entity
@DiscriminatorValue( value = "com.macbackpackers.jobs.CalculateEdinburghVisitorLevyDryRunJob" )
public class CalculateEdinburghVisitorLevyDryRunJob extends AbstractJob {

    @Autowired
    @Transient
    @Qualifier( "webClientForCloudbeds" )
    private WebClient cbWebClient;

    @Autowired
    @Transient
    private EdinburghVisitorLevyService edinburghVisitorLevyService;

    @Override
    public void processJob() throws Exception {
        if ( false == dao.isCloudbeds() ) {
            return;
        }

        int needingAdjustment = 0;
        int checked = 0;

        for ( Customer customer : edinburghVisitorLevyService.findReservationsRequiringVisitorLevyAdjustment(
                cbWebClient, getBookingDateStart(), getBookingDateEnd() ) ) {
            LevyAssessment assessment = edinburghVisitorLevyService.assessVisitorLevyForBooking(
                    cbWebClient, customer.getId() );
            edinburghVisitorLevyService.logDryRunAssessment( customer, assessment );
            checked++;
            if ( assessment.needsAdjustment() ) {
                needingAdjustment++;
            }
        }

        LOGGER.info( "Visitor levy dry run {} to {}: {} reservations checked, {} need adjustment",
                getBookingDateStart(), getBookingDateEnd(), checked, needingAdjustment );
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
