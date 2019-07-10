
package com.macbackpackers.jobs;

import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;
import javax.persistence.Transient;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

import com.gargoylesoftware.htmlunit.WebClient;
import com.macbackpackers.scrapers.BookingComScraper;

/**
 * Job that marks the given card invalid on Booking.com
 * Not currently enabled as not sure how to handle when guest updates their card.
 *
 */
@Entity
@DiscriminatorValue( value = "com.macbackpackers.jobs.BDCMarkCreditCardInvalidJob" )
public class BDCMarkCreditCardInvalidJob extends AbstractJob {

    @Autowired
    @Transient
    private BookingComScraper scraper;

    @Autowired
    @Qualifier( "webClient" )
    @Transient
    private WebClient webClient;

    @Override
    public void finalizeJob() {
        webClient.close(); // cleans up JS threads
    }

    @Override
    public void processJob() throws Exception {
        scraper.markCreditCardAsInvalid( webClient, getBdcReservationId(), getLast4Digits() );
    }

    public String getBdcReservationId() {
        return getParameter( "bdc_reservation_id" );
    }

    public void setBdcReservationId( String id ) {
        setParameter( "bdc_reservation_id", id );
    }

    public String getLast4Digits() {
        return getParameter( "last_4_digits" );
    }

    public void setLast4Digits( String last4Digits ) {
        setParameter( "last_4_digits", last4Digits );
    }
}
