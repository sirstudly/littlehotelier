package com.macbackpackers.scrapers;

import java.util.Calendar;

import jakarta.persistence.Transient;

import org.htmlunit.WebClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import com.macbackpackers.config.LittleHotelierConfig;
import com.macbackpackers.exceptions.UnrecoverableFault;

import static org.junit.jupiter.api.Assertions.assertThrows;

@ExtendWith( SpringExtension.class )
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

    @Test
    public void testLoginFailure() {
        assertThrows( UnrecoverableFault.class, () -> {
            scraper.doLogin( webClient, "guest", "testpassword" );
        } );
    }

    @Test
    public void testGetCardDetails() throws Exception {
        scraper.getCardDetails( webClient, "HWL-551-306023597" );
    }

    @Test
    public void testAcknowledgeFullPaymentTaken() throws Exception {
        scraper.acknowledgeFullPaymentTaken( webClient, "551-380044923" );
    }
}
