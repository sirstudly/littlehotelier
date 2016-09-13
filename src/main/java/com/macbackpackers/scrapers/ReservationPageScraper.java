
package com.macbackpackers.scrapers;

import java.io.IOException;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import com.gargoylesoftware.htmlunit.WebClient;
import com.gargoylesoftware.htmlunit.html.HtmlAnchor;
import com.gargoylesoftware.htmlunit.html.HtmlButton;
import com.gargoylesoftware.htmlunit.html.HtmlCheckBoxInput;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import com.gargoylesoftware.htmlunit.html.HtmlSubmitInput;
import com.gargoylesoftware.htmlunit.html.HtmlTextArea;
import com.macbackpackers.services.AuthenticationService;

/**
 * Scrapes an individual reservation page
 *
 */
@Component
@Scope( "prototype" )
public class ReservationPageScraper {

    private final Logger LOGGER = LoggerFactory.getLogger( getClass() );

    @Autowired
    @Qualifier( "webClient" )
    private WebClient webClient;

    @Autowired
    private AuthenticationService authService;

    @Value( "${lilhotelier.url.reservation}" )
    private String reservationUrl;

    public HtmlPage goToReservationPage( int reservationId ) throws IOException {
        String pageURL = getReservationURL( reservationId );
        LOGGER.info( "Loading reservations page: " + pageURL );
        HtmlPage nextPage = authService.goToPage( pageURL, webClient );
        return nextPage;
    }

    /**
     * Returns the reservation URL for the given ID.
     * 
     * @param reservationId ID of reservation to lookup
     * @return URL of individual reservation
     */
    private String getReservationURL( int reservationId ) {
        return reservationUrl.replaceAll( "__RESERVATION_ID__", String.valueOf( reservationId ) );
    }

    /**
     * Tick the "add payment" button for the automated deposit. Does nothing if already saved.
     * 
     * @param reservationPage the individual reservation page we're looking at.
     * @throws IOException on comms error
     */
    public void tickDeposit( HtmlPage reservationPage ) throws IOException {
        
        webClient.waitForBackgroundJavaScript( 30000 ); // wait for page to load
        HtmlAnchor addPayment = reservationPage.getFirstByXPath( "//a[@data-click='pendingPayment.recordPayment']" );
        if(addPayment != null) {
            LOGGER.info( "Clicking update button on reservation" );
            addPayment.click();
            webClient.waitForBackgroundJavaScript( 30000 );
            HtmlTextArea descriptionTxt = HtmlTextArea.class.cast( reservationPage.getElementById( "description" )); 
            descriptionTxt.type( "HW automated deposit" );
            HtmlButton paymentBtn = reservationPage.getFirstByXPath( "//button[@data-click='payment.create']" );
            paymentBtn.click();
        }
        else{
            LOGGER.info( "Payment button not found or already clicked " + reservationPage.getUrl() );
        }
    }
}
