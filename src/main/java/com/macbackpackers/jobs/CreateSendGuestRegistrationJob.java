package com.macbackpackers.jobs;

import java.time.LocalDate;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.Transient;

import org.apache.commons.lang3.StringUtils;
import org.htmlunit.WebClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationContext;

import com.macbackpackers.services.CloudbedsService;

/**
 * Job that creates individual jobs for sending out guest registration request emails.
 * Searches by optional booking-date and/or checkin-date range; at least one pair must be set.
 *
 */
@Entity
@DiscriminatorValue( value = "com.macbackpackers.jobs.CreateSendGuestRegistrationJob" )
public class CreateSendGuestRegistrationJob extends AbstractJob {

    @Autowired
    @Transient
    private ApplicationContext appContext;

    @Autowired
    @Transient
    private CloudbedsService cloudbedsService;

    @Autowired
    @Transient
    @Qualifier( "webClient" )
    private WebClient webClient;

    @Override
    public void processJob() throws Exception {
        LocalDate bookingDateStart = getBookingDateStart();
        LocalDate bookingDateEnd = getBookingDateEnd();
        LocalDate checkinDateStart = getCheckinDateStart();
        LocalDate checkinDateEnd = getCheckinDateEnd();
        validateDateRanges( bookingDateStart, bookingDateEnd, checkinDateStart, checkinDateEnd );

        try (WebClient webClient = appContext.getBean( "webClientForCloudbeds", WebClient.class )) {
            cloudbedsService.createSendGuestRegistrationJobs( webClient,
                    bookingDateStart, bookingDateEnd, checkinDateStart, checkinDateEnd );
        }
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
