
package com.macbackpackers.jobs;

import java.time.LocalDate;

import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;
import javax.persistence.Transient;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.transaction.annotation.Transactional;

import com.gargoylesoftware.htmlunit.WebClient;
import com.macbackpackers.beans.JobStatus;
import com.macbackpackers.scrapers.CloudbedsScraper;

/**
 * Creates a job to copy card details for each HWL booking without credit card details into
 * Cloudbeds for the given booking date.
 *
 */
@Entity
@DiscriminatorValue( value = "com.macbackpackers.jobs.CreateCopyCardDetailsToCloudbedsJob" )
public class CreateCopyCardDetailsToCloudbedsJob extends AbstractJob {

    @Autowired
    @Transient
    @Qualifier( "webClientForCloudbeds" )
    private WebClient cbWebClient;

    @Autowired
    @Transient
    private CloudbedsScraper cbScraper;

    @Override
    @Transactional
    public void processJob() throws Exception {
        cbScraper.getReservationsForBookingSources( cbWebClient,
                getBookingDate(), getBookingDate().plusDays( getDaysAhead() ), "Hostelworld & Hostelbookers" )
                .stream()
                .filter( p -> false == p.isCardDetailsPresent() )
                // some migrated records have invalid dates; continue, logging error
                .filter( p -> {
                    if ( p.getCheckoutDate() == null ) {
                        LOGGER.error( "Invalid date for " + p.getThirdPartyIdentifier() + ": " + p.getFirstName() + " " + p.getLastName() );
                        return false;
                    }
                    return true;
                } )
                // HWL only keeps credit cards up to 7 days after checkout; so bother if we're past this
                .filter( p -> LocalDate.parse( p.getCheckoutDate() ).plusDays( 7 ).isAfter( LocalDate.now() ) )
                .forEach( p -> {
                    LOGGER.info( "Creating a CopyCardDetailsToCloudbedsJob for booking "
                            + p.getThirdPartyIdentifier() + ": " + p.getFirstName() + " " + p.getLastName() );
                    CopyCardDetailsToCloudbedsJob copyJob = new CopyCardDetailsToCloudbedsJob();
                    copyJob.setStatus( JobStatus.submitted );
                    copyJob.setReservationId( p.getReservationId() );
                    dao.insertJob( copyJob );
                } );
    }

    @Override
    public void finalizeJob() {
        cbWebClient.close(); // cleans up JS threads
    }

    /**
     * Gets the (start) booking date.
     * 
     * @return non-null date parameter
     */
    public LocalDate getBookingDate() {
        return LocalDate.parse( getParameter( "booking_date" ) );
    }

    /**
     * Sets the (start) booking date.
     * 
     * @param bookingDate non-null date
     */
    public void setBookingDate( LocalDate bookingDate ) {
        setParameter( "booking_date", bookingDate.toString() );
    }

    /**
     * Returns the number of days ahead of the booking date to include.
     * 
     * @return non-negative number of days
     */
    public int getDaysAhead() {
        String daysAhead = getParameter( "days_ahead" );
        return daysAhead == null ? 0 : Integer.parseInt( daysAhead );
    }

    /**
     * Sets the number of days ahead of the booking date to include.
     * 
     * @param daysAhead number of days
     */
    public void setDaysAhead( int daysAhead ) {
        if ( daysAhead < 0 ) {
            throw new IllegalArgumentException( "Days ahead must be non-negative" );
        }
        setParameter( "days_ahead", String.valueOf( daysAhead ) );
    }

}
