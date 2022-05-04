
package com.macbackpackers.scrapers;

import java.util.List;

import com.macbackpackers.beans.bdc.BookingComRefundRequest;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import com.gargoylesoftware.htmlunit.WebClient;
import com.macbackpackers.config.LittleHotelierConfig;

@RunWith( SpringJUnit4ClassRunner.class )
@ContextConfiguration( classes = LittleHotelierConfig.class )
public class BookingComScraperTest {

    private final Logger LOGGER = LoggerFactory.getLogger( getClass() );

    @Autowired
    BookingComScraper scraper;

    @Autowired
    @Qualifier( "webClientForBDC" )
    private WebClient webClient;

    @Test
    public void testLoginSuccessful() throws Exception {
        scraper.doLogin( webClient );
    }

    @Test
    public void testLoadReservation() throws Exception {
        scraper.lookupReservation( webClient, "2316646060" );
    }

    @Test
    public void testMarkCardInvalid() throws Exception {
        scraper.markCreditCardAsInvalid( webClient, "3913632669", "7916" );
    }

    @Test
    public void testGetVirtualCardBalance() throws Exception {
        scraper.getVirtualCardBalance( webClient, "3349847478" );
    }

    @Test
    public void testReturnCardDetailsForBooking() throws Exception {
        scraper.returnCardDetailsForBooking( webClient, "2955126491" );
    }

    @Test
    public void testGetAllVCCBookingsThatCanBeCharged() throws Exception {
        List<String> bookingRefs = scraper.getAllVCCBookingsThatCanBeCharged( webClient );
        LOGGER.info( "Found {} bookings", bookingRefs.size() );
        bookingRefs.stream().forEach( b -> LOGGER.info( b ) );
    }

    @Test
    public void testGetAllVCCBookingsThatMustBeRefunded() throws Exception {
        List<BookingComRefundRequest> bookings = scraper.getAllVCCBookingsThatMustBeRefunded(webClient);
        LOGGER.info("Found {} bookings", bookings.size());
        bookings.stream().forEach(b -> LOGGER.info(ToStringBuilder.reflectionToString(b)));
    }
}
