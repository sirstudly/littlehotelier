
package com.macbackpackers.scrapers;

import java.util.Calendar;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import com.macbackpackers.config.LittleHotelierConfig;

@RunWith( SpringJUnit4ClassRunner.class )
@ContextConfiguration( classes = LittleHotelierConfig.class )
public class HostelworldScraperTest {

    @Autowired
    HostelworldScraper scraper;

    @Test
    public void testDumpBookingsForArrivalDate() throws Exception {
        Calendar cal = Calendar.getInstance();
        cal.add( Calendar.DATE, 1 );
        scraper.dumpBookingsForArrivalDate( 2, cal.getTime() );
    }
}
