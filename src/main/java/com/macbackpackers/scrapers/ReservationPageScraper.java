
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
import com.gargoylesoftware.htmlunit.html.HtmlCheckBoxInput;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import com.gargoylesoftware.htmlunit.html.HtmlSubmitInput;
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
    @Qualifier( "webClientScriptingDisabled" )
    private WebClient webClient;

    @Autowired
    private AuthenticationService authService;

    @Value( "${lilhotelier.url.reservation}" )
    private String reservationUrl;

    public HtmlPage goToReservationPage( int reservationId ) throws IOException {
        String pageURL = getReservationURL( reservationId );
        LOGGER.info( "Loading reservations page: " + pageURL );
        HtmlPage nextPage = authService.goToPage( pageURL, webClient );
        LOGGER.debug( nextPage.asXml() );
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
     * Tick the "confirm deposit" checkbox and save the form. Does nothing if already ticked.
     * 
     * @param reservationPage the individual reservation page we're looking at.
     * @throws IOException on comms error
     */
    public void tickDeposit( HtmlPage reservationPage ) throws IOException {
        HtmlCheckBoxInput depositCheckbox = (HtmlCheckBoxInput) reservationPage.getElementById(
                "reservation_payments_attributes_0_processed" );
        if ( depositCheckbox == null || depositCheckbox.isChecked() ) {
            LOGGER.info( "Deposit checkbox not found or already checked. " + reservationPage.getUrl() );
        }
        else {
            LOGGER.info( "Clicking on deposit checkbox for " + reservationPage.getUrl() );
            depositCheckbox.click();

            List<?> submitButtons = reservationPage.getByXPath( "//li[@class='commit']/input[@class='save' and @value='Update']" );
            if ( submitButtons.isEmpty() ) {
                LOGGER.error( "Could not find Update button??" );
            }
            else if ( submitButtons.size() > 1 ) {
                LOGGER.error( "More than 1 Update button found??" );
            }
            else {
                LOGGER.info( "Clicking update button on reservation" );
                HtmlSubmitInput updateButton = (HtmlSubmitInput) submitButtons.get( 0 );
                updateButton.click();
            }
        }
    }

}
