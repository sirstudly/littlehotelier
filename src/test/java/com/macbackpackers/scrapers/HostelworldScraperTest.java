package com.macbackpackers.scrapers;

import java.util.Calendar;

import com.macbackpackers.SecretsManagerTestApp;
import com.macbackpackers.utils.AnyByteStringToStringConverter;
import jakarta.persistence.Transient;

import org.htmlunit.WebClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.convert.support.DefaultConversionService;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import com.macbackpackers.exceptions.UnrecoverableFault;

import static org.apache.commons.lang3.builder.ToStringBuilder.reflectionToString;
import static org.junit.jupiter.api.Assertions.assertThrows;

@ExtendWith( SpringExtension.class )
@SpringBootTest( classes = SecretsManagerTestApp.class )
@TestPropertySource( properties = {
        "spring.profiles.active=crh"
} )
public class HostelworldScraperTest {

    private final Logger LOGGER = LoggerFactory.getLogger( getClass() );

    @Autowired
    HostelworldScraper scraper;

    @Autowired
    @Transient
    @Qualifier( "webClientForHostelworld" )
    private WebClient webClient;

    static {
        // Register the ByteString converter before Spring tries to resolve Secret Manager placeholders
        // This is essential for proper Secret Manager integration in tests
        ( (DefaultConversionService) DefaultConversionService.getSharedInstance() ).addConverter( new AnyByteStringToStringConverter() );
    }

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
        LOGGER.info( reflectionToString( scraper.getCardDetails( webClient, "HWL-551-578032609" ) ) );
    }

    @Test
    public void testAcknowledgeFullPaymentTaken() throws Exception {
        scraper.acknowledgeFullPaymentTaken( webClient, "551-380044923" );
    }
}
