
package com.macbackpackers.scrapers;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import com.gargoylesoftware.htmlunit.WebClient;
import com.macbackpackers.config.LittleHotelierConfig;

@RunWith( SpringJUnit4ClassRunner.class )
@ContextConfiguration( classes = LittleHotelierConfig.class )
public class BookingComScraperTest {

    @Autowired
    BookingComScraper scraper;

    @Autowired
    @Qualifier( "webClient" )
    WebClient webClient;

    @Test
    public void testLoginSuccessful() throws Exception {
        scraper.doLogin( webClient );
    }

    @Test
    public void testLoadReservation() throws Exception {
        scraper.lookupReservation( webClient, "3647119967" );
    }

    @Test
    public void testMarkCardInvalid() throws Exception {
        scraper.markCreditCardAsInvalid( webClient, "2141932992", "4583" );
    }
}
