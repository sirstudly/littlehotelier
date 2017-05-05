
package com.macbackpackers.scrapers;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import com.gargoylesoftware.htmlunit.WebClient;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import com.macbackpackers.config.LittleHotelierConfig;

@RunWith( SpringJUnit4ClassRunner.class )
@ContextConfiguration( classes = LittleHotelierConfig.class )
public class ReservationPageScraperTest {

    final Logger LOGGER = LoggerFactory.getLogger( getClass() );

    @Autowired
    ReservationPageScraper scraper;

    @Autowired
    @Qualifier( "webClient" )
    WebClient webClient;

    @Test
    public void testGoToReservationPage() throws Exception {
        HtmlPage reservationPage = scraper.goToReservationPage( webClient, 851741 );
        scraper.tickDeposit( reservationPage );
    }

}
