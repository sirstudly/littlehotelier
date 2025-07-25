package com.macbackpackers.scrapers;

import com.macbackpackers.config.LittleHotelierConfig;
import org.apache.commons.pool2.impl.GenericObjectPool;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.time.Duration;
import java.util.List;

@ExtendWith( SpringExtension.class )
@ContextConfiguration( classes = LittleHotelierConfig.class )
public class BookingComSeleniumScraperTest {

    private final Logger LOGGER = LoggerFactory.getLogger( getClass() );

    /**
     * maximum time to wait when navigating web requests
     */
    private static final int MAX_WAIT_SECONDS = 60;

    @Autowired
    BookingComSeleniumScraper scraper;

    @Autowired
    private GenericObjectPool<WebDriver> driverFactory;

    private WebDriver driver;
    private WebDriverWait wait;

    @BeforeEach
    public void setup() throws Exception {
        driver = driverFactory.borrowObject();
        wait = new WebDriverWait( driver, Duration.ofSeconds( MAX_WAIT_SECONDS ) );
    }

    @AfterEach
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
        scraper.getVirtualCardBalance( driver, wait, "3241593018" );
    }

    @Test
    public void testReturnCardDetailsForBooking() throws Exception {
        scraper.returnCardDetailsForBooking( driver, wait, "3492021542" );
    }

    @Test
    public void testGetAllVCCBookingsThatCanBeCharged() throws Exception {
        List<String> bookingRefs = scraper.getAllVCCBookingsThatCanBeCharged( driver, wait );
        LOGGER.info( "Found {} bookings", bookingRefs.size() );
        bookingRefs.forEach( b -> LOGGER.info( b ) );
    }
}
