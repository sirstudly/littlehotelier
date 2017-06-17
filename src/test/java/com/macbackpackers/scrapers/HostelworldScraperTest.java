
package com.macbackpackers.scrapers;

import java.util.Calendar;

import javax.persistence.Transient;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import com.gargoylesoftware.htmlunit.WebClient;
import com.macbackpackers.config.LittleHotelierConfig;
import com.macbackpackers.exceptions.UnrecoverableFault;

@RunWith( SpringJUnit4ClassRunner.class )
@ContextConfiguration( classes = LittleHotelierConfig.class )
public class HostelworldScraperTest {

    @Autowired
    HostelworldScraper scraper;

    @Autowired
    @Transient
    @Qualifier( "webClientForHostelworld" )
    private WebClient webClient;

    @Test
    public void testDumpBookingsForArrivalDate() throws Exception {
        Calendar cal = Calendar.getInstance();
        cal.add( Calendar.DATE, 1 );
        scraper.dumpBookingsForArrivalDate( webClient, cal.getTime() );
    }

    @Test
    public void testLoginSuccessful() throws Exception {
        scraper.doLogin( webClient );
    }

    @Test( expected = UnrecoverableFault.class )
    public void testLoginFailure() throws Exception {
        scraper.doLogin( webClient, "guest", "testpassword" );
    }
}
