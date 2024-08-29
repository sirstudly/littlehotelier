
package com.macbackpackers;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.Date;
import java.util.TimeZone;

import org.htmlunit.WebClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.support.AbstractApplicationContext;
import org.springframework.stereotype.Component;

import com.macbackpackers.config.LittleHotelierConfig;
import com.macbackpackers.services.CloudbedsService;

/**
 * Recaptcha test by sending email.
 *
 */
@Component
public class SendEmailTest {

    private static final Logger LOGGER = LoggerFactory.getLogger( SendEmailTest.class );

    @Autowired
    private CloudbedsService service;

    @Autowired
    @Qualifier( "webClientForCloudbeds" )
    WebClient webClient;

    public void sendTestEmail() throws IOException {
        service.sendHostelworldLateCancellationEmail( webClient, "10568885", BigDecimal.ONE );
    }

    /**
     * Bootstrap.
     * 
     * @param argv
     * @throws Exception
     */
    public static void main( String argv[] ) throws Exception {

        AbstractApplicationContext context = null;
        try {
            TimeZone.setDefault( TimeZone.getTimeZone( "Europe/London" ) );
            LOGGER.info( "Starting SendTestEmail... " + new Date() );
            context = new AnnotationConfigApplicationContext( LittleHotelierConfig.class );
            SendEmailTest job = context.getBean( SendEmailTest.class );
            job.sendTestEmail();
        }
        catch ( Throwable th ) {
            System.err.println( "Unexpected exception: " );
            th.printStackTrace();
        }
        finally {
            if ( context != null ) {
                context.close();
                LOGGER.info( "Finished SendTestEmail... " + new Date() );
            }
        }
    }
}
