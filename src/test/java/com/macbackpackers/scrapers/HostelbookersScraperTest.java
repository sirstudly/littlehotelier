
package com.macbackpackers.scrapers;

import java.util.Calendar;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import com.gargoylesoftware.htmlunit.html.HtmlPage;
import com.macbackpackers.config.LittleHotelierConfig;
import com.macbackpackers.exceptions.UnrecoverableFault;

@RunWith( SpringJUnit4ClassRunner.class )
@ContextConfiguration( classes = LittleHotelierConfig.class )
public class HostelbookersScraperTest {

    protected final Logger LOGGER = LoggerFactory.getLogger( getClass() );

    @Autowired
    HostelbookersScraper scraper;

    @Test
    public void testLogin() throws Exception {
        HtmlPage nextPage = scraper.doLogin( "CastleRock", "Chickenrun69" );
        LOGGER.info( nextPage.asXml() );
    }

    @Test( expected = UnrecoverableFault.class )
    public void testLoginWithIncorrectPassword() throws Exception {
        HtmlPage nextPage = scraper.doLogin( "CastleRock", "xxxxx" );
        LOGGER.info( nextPage.asXml() );
    }

    @Test( expected = UnrecoverableFault.class )
    public void testLoginWithInvalidUsername() throws Exception {
        HtmlPage nextPage = scraper.doLogin( "testlogin", "xxxxx" );
        LOGGER.info( nextPage.asXml() );
    }

    @Test
    public void testGotoPage() throws Exception {
        HtmlPage nextPage = scraper.gotoPage( "https://admin.hostelbookers.com/backoffice/booking/index.cfm?fuseaction=search&sub=query&page=1&searchType=Arrival&strArrivalDateStart=28-January-2016&strArrivalDateEnd=28-January-2016&intArrivalStatusID=0&intArrivalSourceID=0&btnSubmit=Search" );
        LOGGER.info( nextPage.asXml() );
    }

    @Test
    public void testDumpBookingsForArrivalDate() throws Exception {
        Calendar cal = Calendar.getInstance();
        cal.add( Calendar.DATE, 1 );
        scraper.dumpBookingsForArrivalDate( cal.getTime() );
    }
}
