
package com.macbackpackers.scrapers;

import java.util.Calendar;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import com.macbackpackers.config.LittleHotelierConfig;

@RunWith( SpringJUnit4ClassRunner.class )
@ContextConfiguration( classes = LittleHotelierConfig.class )
public class BookingsPageScraperTest {

    protected final Logger LOGGER = LoggerFactory.getLogger( getClass() );

    @Autowired
    BookingsPageScraper scraper;

    @Test
    public void testGoToBookingPage() throws Exception {
        Calendar c = Calendar.getInstance();
        c.add( Calendar.MONTH, 2 );
        scraper.goToBookingPageForArrivals( c.getTime() );
    }

    @Test
    public void testUpdateBookingPage() throws Exception {
        Calendar startDate = Calendar.getInstance();
        startDate.add( Calendar.MONTH, 2 );
        startDate.add( Calendar.DATE, -1 );

        Calendar endDate = Calendar.getInstance();
        endDate.add( Calendar.MONTH, 2 );
        endDate.add( Calendar.DATE, -1 );

        scraper.updateBookingsBetween( 14, startDate.getTime(), endDate.getTime(), true );
    }

    @Test
    public void testInsertCancelledBookings() throws Exception {
        Calendar startDate = Calendar.getInstance();
        startDate.set( Calendar.MONTH, 6 ); // july
        startDate.set( Calendar.DATE, 28 );
        startDate.set( Calendar.YEAR, 2015 );

        scraper.insertCancelledBookingsFor( 1, startDate.getTime(), startDate.getTime(), null );
    }

}
