
package com.macbackpackers.scrapers;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.Calendar;
import java.util.Date;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import com.gargoylesoftware.htmlunit.WebClient;
import com.gargoylesoftware.htmlunit.html.HtmlForm;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import com.macbackpackers.config.LittleHotelierConfig;

@RunWith( SpringJUnit4ClassRunner.class )
@ContextConfiguration( classes = LittleHotelierConfig.class )
public class ReservationPageScraperTest {

    final Logger LOGGER = LoggerFactory.getLogger( getClass() );

    @Autowired
    ReservationPageScraper reservationPageScraper;

    @Autowired
    BookingsPageScraper bookingsPageScraper;

    @Autowired
    @Qualifier( "webClient" )
    WebClient webClient;

    @Test
    public void testGoToReservationPage() throws Exception {
        Calendar c = Calendar.getInstance();
        c.set( Calendar.YEAR, 2017 );
        c.set( Calendar.MONTH, Calendar.NOVEMBER );
        c.set( Calendar.DATE, 4 );
        String bookingRef = "HWL-552-335597572";
        HtmlPage bookingsPage = bookingsPageScraper.goToBookingPageBookedOn( webClient, c.getTime(), bookingRef );
        HtmlPage reservationPage = reservationPageScraper.getReservationPage( webClient, bookingsPage, bookingRef );

        reservationPageScraper.tickDeposit( reservationPage );
    }

    @Test
    public void testGetReservationPage() throws Exception {
        Calendar c = Calendar.getInstance();
        c.set( Calendar.YEAR, 2017 );
        c.set( Calendar.MONTH, Calendar.OCTOBER );
        c.set( Calendar.DATE, 31 );
        String bookingRef = "AGO-205184799";
        HtmlPage bookingsPage = bookingsPageScraper.goToBookingPageArrivedOn( webClient, c.getTime(), bookingRef );
        HtmlPage reservationPage = reservationPageScraper.getReservationPage( webClient, bookingsPage, bookingRef );

        HtmlForm editform = reservationPage.getFirstByXPath( "//form[not(@method)]" );
        String postActionUrl = editform.getAttribute( "action" );
        LOGGER.info( "Hopefully, this is the direct URL for the reservation: " + postActionUrl );
        int reservationId = Integer.parseInt( postActionUrl.substring( postActionUrl.lastIndexOf( '/' ) + 1 ) );

        LOGGER.info( reservationId + "" );
    }

    @Test
    public void testAddPayment() throws IOException {
        Calendar c = Calendar.getInstance();
        c.set( Calendar.YEAR, 2017 );
        c.set( Calendar.MONTH, Calendar.OCTOBER );
        c.set( Calendar.DATE, 30 );
        String bookingRef = "BDC-1773508518";
        HtmlPage bookingsPage = bookingsPageScraper.goToBookingPageArrivedOn( webClient, c.getTime(), bookingRef );
        HtmlPage reservationPage = reservationPageScraper.getReservationPage( webClient, bookingsPage, bookingRef );
        reservationPageScraper.addPayment( reservationPage, new BigDecimal( "1.23" ), "MasterCard", true, "Testing -ronbot" );
    }

    @Test
    public void testAppendNote() throws Exception {
        Calendar c = Calendar.getInstance();
        c.set( Calendar.YEAR, 2017 );
        c.set( Calendar.MONTH, Calendar.OCTOBER );
        c.set( Calendar.DATE, 12 );
        String bookingRef = "BDC-2003879268";
        HtmlPage bookingsPage = bookingsPageScraper.goToBookingPageBookedOn( webClient, c.getTime(), bookingRef );
        HtmlPage reservationPage = reservationPageScraper.getReservationPage( webClient, bookingsPage, bookingRef );
        reservationPageScraper.appendNote( reservationPage, "This is only a test. " + new Date() );
    }
}
