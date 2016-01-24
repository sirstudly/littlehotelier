
package com.macbackpackers.scrapers;

import java.util.Calendar;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import com.macbackpackers.config.LittleHotelierConfig;
import com.macbackpackers.exceptions.UnrecoverableFault;

@RunWith( SpringJUnit4ClassRunner.class )
@ContextConfiguration( classes = LittleHotelierConfig.class )
public class HostelworldScraperTest {

    @Autowired
    HostelworldScraper scraper;

    @Test
    public void testDumpBookingsForArrivalDate() throws Exception {
        Calendar cal = Calendar.getInstance();
        cal.add( Calendar.DATE, 1 );
        scraper.dumpBookingsForArrivalDate( cal.getTime() );
    }

    @Test
    public void testLoginSuccessful() throws Exception {
        scraper.doLogin( "peter", "Castlerock1!" );
    }

    @Test( expected = UnrecoverableFault.class )
    public void testLoginFailure() throws Exception {
        scraper.doLogin( "guest", "testpassword" );
    }
}
