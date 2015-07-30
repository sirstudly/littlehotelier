
package com.macbackpackers.scrapers;

import java.util.Calendar;

import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import com.gargoylesoftware.htmlunit.html.HtmlPage;
import com.macbackpackers.config.LittleHotelierConfig;

@RunWith( SpringJUnit4ClassRunner.class )
@ContextConfiguration( classes = LittleHotelierConfig.class )
public class HostelbookersScraperTest {

    protected final Logger LOGGER = LogManager.getLogger( getClass() );

    @Autowired
    HostelbookersScraper scraper;

    @Test
    public void testLogin() throws Exception {
        HtmlPage nextPage = scraper.login();
        LOGGER.info( nextPage.asXml() );
    }

    @Test
    public void testDumpBookingsForArrivalDate() throws Exception {
        Calendar cal = Calendar.getInstance();
        cal.add( Calendar.DATE, 1 );
        scraper.dumpBookingsForArrivalDate( cal.getTime() );
    }
}
