package com.macbackpackers.jobs;

import java.time.LocalDate;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.Transient;

import org.htmlunit.WebClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationContext;

import com.macbackpackers.services.CloudbedsService;

/**
 * Job that creates individual jobs for sending out guest registration request emails.
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
        try (WebClient webClient = appContext.getBean( "webClientForCloudbeds", WebClient.class )) {
            cloudbedsService.createSendGuestRegistrationJobs( webClient, getBookingDate(), getMinGuests(), getMaxGuests() );
        }
    }

    /**
     * Gets the booking date.
     *
     * @return non-null date parameter
     */
    public LocalDate getBookingDate() {
        return LocalDate.parse( getParameter( "booking_date" ) );
    }

    /**
     * Sets the booking date.
     *
     * @param bookingDate non-null date
     */
    public void setBookingDate( LocalDate bookingDate ) {
        setParameter( "booking_date", bookingDate.toString() );
    }

    /**
     * Gets the minimum number of guests in the booking (inclusive).
     *
     * @return minimum guest count
     */
    public int getMinGuests() {
        return Integer.parseInt( getParameter( "min_guests" ) );
    }

    /**
     * Sets the minimum number of guests in the booking (inclusive).
     *
     * @param minGuests minimum guest count
     */
    public void setMinGuests( int minGuests ) {
        setParameter( "min_guests", String.valueOf( minGuests ) );
    }

    /**
     * Gets the maximum number of guests in the booking (inclusive).
     *
     * @return maximum guest count
     */
    public int getMaxGuests() {
        return Integer.parseInt( getParameter( "max_guests" ) );
    }

    /**
     * Sets the maximum number of guests in the booking (inclusive).
     *
     * @param maxGuests maximum guest count
     */
    public void setMaxGuests( int maxGuests ) {
        setParameter( "max_guests", String.valueOf( maxGuests ) );
    }
}

