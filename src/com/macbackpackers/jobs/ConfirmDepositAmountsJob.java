package com.macbackpackers.jobs;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import com.gargoylesoftware.htmlunit.html.HtmlPage;
import com.macbackpackers.scrapers.ReservationPageScraper;

/**
 * Job that goes to the individual bookings page and confirms any
 * outstanding deposit present and saves the booking.
 *
 */
@Component
@Scope( "prototype" )
public class ConfirmDepositAmountsJob extends AbstractJob {
    
    @Autowired
    private ReservationPageScraper reservationScraper;
    
    @Override
    public void processJob() throws Exception {
        HtmlPage reservationPage = reservationScraper.goToReservationPage( getReservationId() );
        reservationScraper.tickDeposit( reservationPage );
    }
    
    private int getReservationId() {
        return Integer.parseInt( getParameter( "reservation_id" ) );
    }
    
}