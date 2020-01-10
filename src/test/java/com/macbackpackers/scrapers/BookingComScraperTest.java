
package com.macbackpackers.scrapers;

import java.time.LocalDate;
import java.util.List;

import org.apache.commons.pool2.impl.GenericObjectPool;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import com.macbackpackers.config.LittleHotelierConfig;

@RunWith( SpringJUnit4ClassRunner.class )
@ContextConfiguration( classes = LittleHotelierConfig.class )
public class BookingComScraperTest {

    private final Logger LOGGER = LoggerFactory.getLogger( getClass() );

    /** maximum time to wait when navigating web requests */
    private static final int MAX_WAIT_SECONDS = 60;

    @Autowired
    BookingComScraper scraper;

    @Autowired
    private GenericObjectPool<WebDriver> driverFactory;

    private WebDriver driver;
    private WebDriverWait wait;

    @Before
    public void setup() throws Exception {
        driver = driverFactory.borrowObject();
        wait = new WebDriverWait( driver, MAX_WAIT_SECONDS );
    }

    @After
    public void teardown() throws Exception {
        driverFactory.returnObject( driver );
    }

    @Test
    public void testLoginSuccessful() throws Exception {
        scraper.doLogin( driver, wait );
    }

    @Test
    public void testLoadReservation() throws Exception {
        scraper.lookupReservation( driver, wait, "2316646060" );
    }

    @Test
    public void testMarkCardInvalid() throws Exception {
        scraper.markCreditCardAsInvalid( driver, wait, "3913632669", "7916" );
    }

    @Test
    public void testGetVirtualCardBalance() throws Exception {
        scraper.getVirtualCardBalance( driver, wait, "3804638594" );
    }

    @Test
    public void testReturnCardDetailsForBooking() throws Exception {
        scraper.returnCardDetailsForBooking( driver, wait, "3218755671" );
    }

    @Test
    public void testGetAllVCCBookingsThatCanBeCharged() throws Exception {
        List<String> bookingRefs = scraper.getAllVCCBookingsThatCanBeCharged( driver, wait,
                LocalDate.parse( "2020-01-01" ), LocalDate.parse( "2020-01-10" ) );
        LOGGER.info( "Found {} bookings", bookingRefs.size() );
        bookingRefs.stream().forEach( b -> LOGGER.info( b ) );
    }
}
