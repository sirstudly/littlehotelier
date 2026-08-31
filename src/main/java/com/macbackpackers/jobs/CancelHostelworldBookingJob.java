package com.macbackpackers.jobs;

import java.time.LocalDate;

import org.htmlunit.WebClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationContext;

import com.macbackpackers.beans.cloudbeds.responses.Reservation;
import com.macbackpackers.exceptions.MissingUserDataException;
import com.macbackpackers.exceptions.UnrecoverableFault;
import com.macbackpackers.scrapers.CloudbedsScraper;
import com.macbackpackers.scrapers.HostelworldScraper;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.Transient;

/**
 * Cancels a booking on the Hostelworld portal and notes the matching Cloudbeds booking.
 */
@Entity
@DiscriminatorValue( value = "com.macbackpackers.jobs.CancelHostelworldBookingJob" )
public class CancelHostelworldBookingJob extends AbstractJob {

    public static final String CANCEL_NOTE = "Canceling due to non-payment -RONBOT";

    @Autowired
    @Transient
    private HostelworldScraper hostelworldScraper;

    @Autowired
    @Transient
    private CloudbedsScraper cloudbedsScraper;

    @Autowired
    @Transient
    @Qualifier( "webClientForHostelworld" )
    private WebClient hwlWebClient;

    @Autowired
    @Transient
    private ApplicationContext appContext;

    @Override
    public void processJob() throws Exception {
        if ( false == isEnabled() ) { // default to false; to be removed once testing is complete
            throw new UnrecoverableFault( "This job is not currently enabled!" );
        }

        if ( dao.isHostelworldCancelBookingExempt( getHostelworldReservationId() ) ) {
            throw new UnrecoverableFault(
                    "Booking " + getHostelworldReservationId() + " is exempt from automated Hostelworld cancellation." );
        }
        try (WebClient cbWebClient = appContext.getBean( "webClientForCloudbeds", WebClient.class )) {
            Reservation reservation = findCloudbedsReservation( cbWebClient );
            if ( reservation.containsNote( CANCEL_NOTE ) ) {
                LOGGER.info( "Reservation {} already has cancel note. Nothing to do.",
                        reservation.getReservationId() );
                return;
            }
            if ( isWithinMinDaysOfCheckin( reservation ) ) {
                return;
            }

            hostelworldScraper.cancelBooking( hwlWebClient, getHostelworldReservationId() );
            cloudbedsScraper.addNote( cbWebClient, reservation.getReservationId(), CANCEL_NOTE );
        }
    }

    /**
     * Returns true if today is on/after midnight of (check-in − {@code hbo_hwl_cancel_booking_min_days}),
     * i.e. too close to arrival to auto-cancel.
     */
    private boolean isWithinMinDaysOfCheckin( Reservation reservation ) {
        int minDays = Integer.parseInt( dao.getMandatoryOption( "hbo_hwl_cancel_booking_min_days" ) );
        LocalDate checkinDate = reservation.getCheckinDateAsLocalDate();
        LocalDate cancelCutoff = checkinDate.minusDays( minDays );
        if ( false == LocalDate.now().isBefore( cancelCutoff ) ) {
            LOGGER.info( "Reservation {} checks in on {}; within {} day(s) of check-in (from {}). Skipping cancel.",
                    reservation.getReservationId(), checkinDate, minDays, cancelCutoff );
            return true;
        }
        return false;
    }

    private Reservation findCloudbedsReservation( WebClient cbWebClient ) throws Exception {
        String hwlReservationId = getHostelworldReservationId();
        return cloudbedsScraper.getReservations( cbWebClient, hwlReservationId ).stream()
                .filter( c -> c.getSourceName() != null && c.getSourceName().contains( "Hostelworld" ) )
                .map( c -> cloudbedsScraper.getReservationRetry( cbWebClient, c.getId() ) )
                .filter( r -> r.getThirdPartyIdentifier().equals( hwlReservationId ) )
                .findFirst()
                .orElseThrow( () -> new MissingUserDataException(
                        "No Cloudbeds Hostelworld booking found for hwl_reservation_id " + hwlReservationId ) );
    }

    @Override
    public void finalizeJob() {
        hwlWebClient.close();
    }

    public void setHostelworldReservationId( String hwlReservationId ) {
        setParameter( "hwl_reservation_id", hwlReservationId );
    }

    public String getHostelworldReservationId() {
        return getParameter( "hwl_reservation_id" );
    }

    public void setEnabled( boolean isEnabled ) {
        setParameter( "is_enabled", Boolean.toString( isEnabled ) );
    }

    public boolean isEnabled() {
        return Boolean.TRUE.toString().equalsIgnoreCase( getParameter( "is_enabled" ) );
    }

    @Override
    public int getRetryCount() {
        return 1; // limit failed attempts
    }

}
