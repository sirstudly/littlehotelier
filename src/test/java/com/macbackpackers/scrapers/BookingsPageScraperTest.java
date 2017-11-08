
package com.macbackpackers.scrapers;

import java.util.Calendar;
import java.util.Date;
import java.util.List;

import org.apache.commons.csv.CSVRecord;
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
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import com.macbackpackers.beans.UnpaidDepositReportEntry;
import com.macbackpackers.config.LittleHotelierConfig;

@RunWith( SpringJUnit4ClassRunner.class )
@ContextConfiguration( classes = LittleHotelierConfig.class )
public class BookingsPageScraperTest {

    protected final Logger LOGGER = LoggerFactory.getLogger( getClass() );

    @Autowired
    BookingsPageScraper scraper;

    @Autowired
    @Qualifier( "webClient" )
    WebClient webClient;

    @Test
    public void testUpdateBookingPage() throws Exception {
        Calendar startDate = Calendar.getInstance();
        startDate.add( Calendar.MONTH, 2 );
        startDate.add( Calendar.DATE, -1 );

        Calendar endDate = Calendar.getInstance();
        endDate.add( Calendar.MONTH, 2 );
        endDate.add( Calendar.DATE, -1 );

        scraper.updateBookingsBetween( webClient, 14, startDate.getTime(), endDate.getTime() );
    }

    @Test
    public void testGuestComments() throws Exception {
        Calendar c = Calendar.getInstance();
        c.set( Calendar.YEAR, 2016 );
        c.set( Calendar.MONTH, 9 );
        c.set( Calendar.DATE, 6 );
        String comment = scraper.getGuestCommentsForReservation( webClient, "EXP-719896551", c.getTime() );
        LOGGER.info( comment );
    }
    
    @Test
    public void testCreateConfirmDepositJobs() throws Exception {
        Calendar c = Calendar.getInstance();
        c.set( Calendar.YEAR, 2017 );
        c.set( Calendar.MONTH, Calendar.NOVEMBER );
        c.set( Calendar.DATE, 3 );
        HtmlPage bookingsPage = scraper.goToBookingPageBookedOn( webClient, c.getTime(), "HWL" );
        scraper.createConfirmDepositJobs( bookingsPage );
    }

    @Test
    public void testGetUnpaidBDCReservations() throws Exception {
        Calendar c = Calendar.getInstance();
        c.set( Calendar.YEAR, 2017 );
        c.set( Calendar.MONTH, Calendar.JANUARY );
        c.set( Calendar.DATE, 15 );
        Date dateFrom = c.getTime();
        c.set( Calendar.DATE, 30 );
        Date dateTo = c.getTime();
        List<UnpaidDepositReportEntry> records = scraper.getUnpaidReservations( webClient, "BDC", dateFrom, dateTo );
        LOGGER.info( records.size() + " recs found" );
        for( UnpaidDepositReportEntry entry : records ) {
            LOGGER.info( ToStringBuilder.reflectionToString( entry ) );
        }
    }

    @Test
    public void testUpdateBookingsBetween() throws Exception {
        Calendar c = Calendar.getInstance();
        c.set( Calendar.YEAR, 2017 );
        c.set( Calendar.MONTH, Calendar.OCTOBER );
        c.set( Calendar.DATE, 24 );
        scraper.updateBookingsBetween(
                webClient, 11729, c.getTime(), c.getTime() );
    }

    @Test
    public void testGetAgodaReservations() throws Exception {
        Calendar c = Calendar.getInstance();
        c.set( Calendar.YEAR, 2017 );
        c.set( Calendar.MONTH, Calendar.OCTOBER );
        c.set( Calendar.DATE, 7 );
        Date dateFrom = c.getTime();
        c.set( Calendar.DATE, 15 );
        Date dateTo = c.getTime();
        List<CSVRecord> records = scraper.getAgodaReservations( webClient, dateFrom, dateTo );
        LOGGER.info( records.size() + " recs found" );
        for( CSVRecord entry : records ) {
            LOGGER.info( ToStringBuilder.reflectionToString( entry ) );
        }
    }
    
}
