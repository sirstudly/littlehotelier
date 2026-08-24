package com.macbackpackers.scrapers;

import com.macbackpackers.SecretsManagerTestApp;
import com.macbackpackers.utils.AnyByteStringToStringConverter;
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
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.convert.support.DefaultConversionService;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import com.macbackpackers.beans.CardDetails;
import com.macbackpackers.beans.bdc.BookingComRefundRequest;
import com.macbackpackers.beans.bdc.BookingComVCCToCharge;
import com.macbackpackers.services.BasicCardMask;
import org.apache.commons.lang3.StringUtils;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith( SpringExtension.class )
@SpringBootTest( classes = SecretsManagerTestApp.class )
@TestPropertySource( properties = {
        "spring.profiles.active=crh"
} )
public class BookingComSeleniumScraperTest {

    private final Logger LOGGER = LoggerFactory.getLogger( getClass() );

    static {
        // Register the ByteString converter before Spring tries to resolve Secret Manager placeholders
        // This is essential for proper Secret Manager integration in tests
        ( (DefaultConversionService) DefaultConversionService.getSharedInstance() ).addConverter( new AnyByteStringToStringConverter() );
    }

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
        BigDecimal balance = scraper.getVirtualCardBalance( driver, wait, "5027255020" );
        LOGGER.info( "VCC balance for booking: {}", balance );
        assertNotNull( balance );
    }

    @Test
    public void testReturnCardDetailsForBooking() throws Exception {
        CardDetails cardDetails = scraper.returnCardDetailsForBooking( driver, wait, "6684440976" );
        assertNotNull( cardDetails );
        assertNotNull( cardDetails.getCardNumber() );
        assertFalse( StringUtils.isBlank( cardDetails.getCardNumber() ) );
        assertTrue( cardDetails.getCardNumber().matches( "\\d+" ), "card number should be digits only" );
        LOGGER.info( "Retrieved card: {} for {}",
                new BasicCardMask().applyCardMask( cardDetails.getCardNumber() ), cardDetails.getName() );
    }

    @Test
    public void testGetAllVCCBookingsThatCanBeCharged() throws Exception {
        List<BookingComVCCToCharge> bookings = scraper.getAllVCCBookingsThatCanBeCharged( driver, wait );
        LOGGER.info( "Found {} bookings", bookings.size() );
        bookings.forEach( b -> LOGGER.info( "{}", b ) );
    }

    @Test
    public void testGetAllVCCBookingsThatMustBeRefunded() throws Exception {
        List<BookingComRefundRequest> bookings = scraper.getAllVCCBookingsThatMustBeRefunded( driver, wait );
        LOGGER.info( "Found {} refunds", bookings.size() );
        bookings.forEach( b -> LOGGER.info( "{} {} {}", b.getBookingRef(), b.getReason(), b.getRefundAmount() ) );
    }
}
