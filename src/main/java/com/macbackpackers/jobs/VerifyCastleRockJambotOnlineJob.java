
package com.macbackpackers.jobs;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.Transient;

import java.io.IOException;

import org.htmlunit.WebClient;
import org.htmlunit.WebResponse;
import org.htmlunit.html.HtmlPage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

import com.macbackpackers.services.GmailService;

@Entity
@DiscriminatorValue( value = "com.macbackpackers.jobs.VerifyCastleRockJambotOnlineJob" )
public class VerifyCastleRockJambotOnlineJob extends AbstractJob {

    @Transient
    private final Logger LOGGER = LoggerFactory.getLogger( getClass() );

    @Autowired
    @Transient
    @Qualifier( "webClient" )
    private WebClient webClient;

    @Autowired
    @Transient
    private GmailService gmail;

    private static final String JAMBOT_URL = "https://jambot.castlerockedinburgh.com";
    private static final int RETRY_DELAY_MS = 120000; // 2 minutes

    @Override
    public void processJob() throws Exception {
        // First attempt
        try {
            queryJambot();
            // If we get here, Jambot is responding successfully
            return;
        }
        catch ( Exception e ) {
            LOGGER.warn( "First attempt to query Jambot failed: " + e.getMessage(), e );
        }

        // First attempt failed, wait and retry
        LOGGER.info( "First attempt failed. Waiting " + ( RETRY_DELAY_MS / 1000 ) + " seconds before retry..." );
        Thread.sleep( RETRY_DELAY_MS );

        // Second attempt
        try {
            queryJambot();
            // If we get here, Jambot is responding successfully on retry
            return;
        }
        catch ( Exception e ) {
            LOGGER.warn( "Second attempt to query Jambot failed: " + e.getMessage(), e );
        }

        // Both attempts failed, send email
        LOGGER.info( "Both attempts failed. Sending email..." );
        gmail.sendEmail( dao.getMandatoryOption( "hbo_support_email" ), null, "Castle Rock Jambot down?",
                "Help! Castle Rock Jambot is not responding! -RONBOT" );
    }

    /**
     * Queries the Jambot URL and verifies it responds with status code 200.
     *
     * @throws IOException if there's a connection error or non-200 status code
     */
    private void queryJambot() throws IOException {
        LOGGER.info( "Querying " + JAMBOT_URL );
        HtmlPage statusPage = webClient.getPage( JAMBOT_URL );
        WebResponse response = statusPage.getWebResponse();
        int statusCode = response.getStatusCode();

        LOGGER.info( "Response status code: " + statusCode );

        if ( statusCode == 200 ) {
            LOGGER.info( "Jambot is responding successfully" );
        }
        else {
            LOGGER.warn( "Jambot returned non-200 status code: " + statusCode );
            throw new IOException( "Jambot returned status code " + statusCode + ": " + response.getStatusMessage() );
        }
    }
}

