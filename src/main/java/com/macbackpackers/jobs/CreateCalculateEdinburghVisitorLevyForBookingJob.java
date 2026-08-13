package com.macbackpackers.jobs;

import java.time.LocalDate;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.Transient;

import org.apache.commons.lang3.StringUtils;
import org.htmlunit.WebClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

import com.macbackpackers.beans.JobStatus;
import com.macbackpackers.beans.cloudbeds.responses.Customer;
import com.macbackpackers.services.EdinburghVisitorLevyService;

/**
 * Creates {@link CalculateEdinburghVisitorLevyForBookingJob}s for bookings within an optional
 * booking-date and/or checkin-date range (all statuses) whose folio EVL differs from the
 * calculated amount. At least one of the two date pairs must be set; when both are set, only
 * bookings matching both ranges are assessed.
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
    public void processJob() throws Exception {
        if ( false == dao.isCloudbeds() ) {
            return;
        }

        LocalDate bookingDateStart = getBookingDateStart();
        LocalDate bookingDateEnd = getBookingDateEnd();
        LocalDate checkinDateStart = getCheckinDateStart();
        LocalDate checkinDateEnd = getCheckinDateEnd();
        validateDateRanges( bookingDateStart, bookingDateEnd, checkinDateStart, checkinDateEnd );

        // No outer @Transactional: each insertJob commits via WordPressDAO so child jobs
        // become visible to other processors as soon as a mismatch is discovered.
        edinburghVisitorLevyService.findReservationsRequiringVisitorLevyAdjustment(
                cbWebClient, bookingDateStart, bookingDateEnd, checkinDateStart, checkinDateEnd )
                .forEach( entry -> createCalculateJob( entry.getCustomer() ) );
    }

    private static void validateDateRanges( LocalDate bookingDateStart, LocalDate bookingDateEnd,
            LocalDate checkinDateStart, LocalDate checkinDateEnd ) {
        requireCompletePair( "booking_date", bookingDateStart, bookingDateEnd );
        requireCompletePair( "checkin_date", checkinDateStart, checkinDateEnd );
        if ( bookingDateStart == null && checkinDateStart == null ) {
            throw new IllegalArgumentException(
                    "Either booking_date_start/end or checkin_date_start/end must be non-blank" );
        }
    }

    private static void requireCompletePair( String prefix, LocalDate start, LocalDate end ) {
        if ( ( start == null ) != ( end == null ) ) {
            throw new IllegalArgumentException(
                    prefix + "_start and " + prefix + "_end must both be set or both blank" );
        }
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
        String bookingDateStart = getParameter( "booking_date_start" );
        return StringUtils.isBlank( bookingDateStart ) ? null : LocalDate.parse( bookingDateStart );
    }

    public void setBookingDateStart( LocalDate bookingDateStart ) {
        setParameter( "booking_date_start", bookingDateStart.toString() );
    }

    public LocalDate getBookingDateEnd() {
        String bookingDateEnd = getParameter( "booking_date_end" );
        return StringUtils.isBlank( bookingDateEnd ) ? null : LocalDate.parse( bookingDateEnd );
    }

    public void setBookingDateEnd( LocalDate bookingDateEnd ) {
        setParameter( "booking_date_end", bookingDateEnd.toString() );
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
