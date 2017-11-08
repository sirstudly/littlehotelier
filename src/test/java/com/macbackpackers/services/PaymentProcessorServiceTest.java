
package com.macbackpackers.services;

import java.math.BigDecimal;
import java.util.Calendar;
import java.util.List;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.IncorrectResultSizeDataAccessException;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import com.gargoylesoftware.htmlunit.WebClient;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import com.gargoylesoftware.htmlunit.html.HtmlTableRow;
import com.macbackpackers.config.LittleHotelierConfig;
import com.macbackpackers.dao.WordPressDAO;
import com.macbackpackers.scrapers.BookingsPageScraper;

@RunWith( SpringJUnit4ClassRunner.class )
@ContextConfiguration( classes = LittleHotelierConfig.class )
public class PaymentProcessorServiceTest {

    private final Logger LOGGER = LoggerFactory.getLogger( getClass() );

    @Autowired
    PaymentProcessorService paymentService;

    @Autowired
    @Qualifier( "webClient" )
    WebClient webClient;

    @Autowired
    @Qualifier( "webClientForHostelworld" )
    WebClient hwWebClient;

    @Autowired
    WordPressDAO dao;
    
    @Autowired
    BookingsPageScraper bookingScraper;

    @Test
    public void testPayment() throws Exception {
        Calendar c = Calendar.getInstance();
        c.set( Calendar.YEAR, 2017 );
        c.set( Calendar.MONTH, Calendar.OCTOBER );
        c.set( Calendar.DATE, 26 );
        paymentService.processDepositPayment( webClient, 0, "BDC-1264569063", c.getTime() );
        LOGGER.info( "done" );
    }
    
    @Test
    public void testDepositChargeAmount() throws Exception {
        String bookingRef = "BDC-1192773406";

        Calendar c = Calendar.getInstance();
        c.set( Calendar.YEAR, 2017 );
        c.set( Calendar.MONTH, 5 ); // 0-based
        c.set( Calendar.DATE, 7 );
        
        LOGGER.info( "Processing payment for booking " + bookingRef );
        HtmlPage bookingsPage = bookingScraper.goToBookingPageBookedOn( webClient, c.getTime(), bookingRef );
        
        List<?> rows = bookingsPage.getByXPath( 
                "//div[@id='content']/div[@class='reservations']/div[@class='data']/table/tbody/tr[@class!='group_header']" );
        if(rows.size() != 1) {
            throw new IncorrectResultSizeDataAccessException("Unable to find unique booking " + bookingRef, 1);
        }
        // need the LH reservation ID before clicking on the row
        HtmlTableRow row = HtmlTableRow.class.cast( rows.get( 0 ) );
        int reservationId = Integer.parseInt( row.getAttribute( "data-id" ) );
        LOGGER.info( "Reservation ID: " + reservationId );
        
        // click on the only reservation on the page
        HtmlPage reservationPage = row.click();
        reservationPage.getWebClient().waitForBackgroundJavaScript( 30000 ); // wait for page to load
        
        BigDecimal amountToCharge = paymentService.getBdcDepositChargeAmount( reservationPage );
        LOGGER.info( "Amount to charge: " + amountToCharge );
    }

    @Test
    public void testSyncLastPxPostTransactionInLH() throws Exception {

        // This is a valid successful txn in PxPost (UAT) : 1508959296186
        String bookingRef = "EXP-924636178";
        Calendar c = Calendar.getInstance();
        c.set( Calendar.YEAR, 2017 );
        c.set( Calendar.MONTH, Calendar.OCTOBER );
        c.set( Calendar.DATE, 13 );

        HtmlPage bookingsPage = bookingScraper.goToBookingPageBookedOn( webClient, c.getTime(), bookingRef );

        List<?> rows = bookingsPage.getByXPath(
                "//div[@id='content']/div[@class='reservations']/div[@class='data']/table/tbody/tr/td[@class='booking_reference' and text()='" + bookingRef + "']/.." );
        if ( rows.size() != 1 ) {
            throw new IncorrectResultSizeDataAccessException( "Unable to find unique booking " + bookingRef, 1 );
        }
        // need the LH reservation ID before clicking on the row
        HtmlTableRow row = HtmlTableRow.class.cast( rows.get( 0 ) );

        // click on the only reservation on the page
        HtmlPage reservationPage = row.click();

        // this should add the payment details to the LH reservation above
        bookingRef = "HWL-123-1234567";
        paymentService.syncLastPxPostTransactionInLH( bookingRef, true, reservationPage );
    }

    @Test
    public void testProcessManualPayment() throws Exception {
        paymentService.processManualPayment( webClient, hwWebClient, 0,
                "HWL-552-320209037", new BigDecimal( "0.01" ), "Testing -rc" );
    }
}
