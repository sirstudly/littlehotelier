
package com.macbackpackers.jobs;

import java.time.LocalDate;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.Transient;

import org.htmlunit.WebClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

import com.macbackpackers.services.EdinburghVisitorLevyService;

/**
 * Dry-run visitor levy calculation for bookings in a booking-date range (all statuses, including
 * canceled and no_show) that would normally be queued by
 * {@link CreateCalculateEdinburghVisitorLevyForBookingJob}. Logs whether each reservation needs
 * an adjustment without posting charges.
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

        for ( EdinburghVisitorLevyService.CustomerLevyAssessment entry
                : edinburghVisitorLevyService.assessReservationsInBookingDateRange(
                cbWebClient, getBookingDateStart(), getBookingDateEnd() ) ) {
            edinburghVisitorLevyService.logDryRunAssessment( entry.getCustomer(), entry.getAssessment() );
            checked++;
            if ( entry.getAssessment().needsAdjustment() ) {
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
