
package com.macbackpackers.jobs;

import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;
import javax.persistence.Transient;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

import com.gargoylesoftware.htmlunit.WebClient;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import com.macbackpackers.scrapers.ReservationPageScraper;

/**
 * Job that goes to the individual bookings page and confirms any outstanding deposit present and
 * saves the booking.
 *
 */
@Entity
@DiscriminatorValue( value = "com.macbackpackers.jobs.ConfirmDepositAmountsJob" )
public class ConfirmDepositAmountsJob extends AbstractJob {

    @Autowired
    @Transient
    private ReservationPageScraper reservationScraper;

    @Autowired
    @Transient
    @Qualifier( "webClient" )
    private WebClient webClient;

    @Override
    public void processJob() throws Exception {
        HtmlPage reservationPage = reservationScraper.goToReservationPage( webClient, getReservationId() );
        reservationScraper.tickDeposit( reservationPage );
    }

    @Override
    public void finalizeJob() {
        webClient.close(); // cleans up JS threads
    }

    public int getReservationId() {
        return Integer.parseInt( getParameter( "reservation_id" ) );
    }

    public void setReservationId(int reservationId) {
        setParameter( "reservation_id", String.valueOf( reservationId ) );
    }
}
