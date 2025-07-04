package com.macbackpackers.services;

import org.apache.commons.lang3.builder.ToStringBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import com.macbackpackers.config.LittleHotelierConfig;

@ExtendWith( SpringExtension.class )
@ContextConfiguration( classes = LittleHotelierConfig.class )
public class GmailServiceTest {

    private final Logger LOGGER = LoggerFactory.getLogger( getClass() );

    private static final String BDC_WITH_MODIFIED_CARD_DETAILS = "BDC-1697995930";

    @Autowired
    private GmailService gmailService;

    @Test
    public void testListMessages() throws Exception {
        LOGGER.info( ToStringBuilder.reflectionToString(
                gmailService.fetchBdcCardDetailsFromBookingRef( BDC_WITH_MODIFIED_CARD_DETAILS ) ) );
    }

    @Test
    public void testFetchLOHSecurityToken() throws Exception {
        LOGGER.info( gmailService.fetchLHSecurityToken() );
    }

    @Test
    public void testAuthorize() throws Exception {
        // this should throw up a login window on first run
        gmailService.connectAsClient();
    }

    @Test
    public void testSendEmail() throws Exception {
        gmailService.sendEmail( "ronchan@techie.com", "Mister Chan", "Testing",
                "<HTML><BODY>This is an <em>HTML</em> message. <br>Thanks for participating!</BODY></HTML>" );
    }

    @Test
    public void testSendEmailCcSelf() throws Exception {
        gmailService.sendEmailCcSelf( "ronchan@techie.com", "Mister Chan", "Testing",
                "<HTML><BODY>This is an <em>HTML</em> message. <br>Thanks for participating!</BODY></HTML>" );
    }

    @Test
    public void testfetchAgodaPasscode() throws Exception {
        gmailService.fetchAgodaPasscode();
    }
}
