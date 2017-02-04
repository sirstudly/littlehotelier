package com.macbackpackers.services;

import java.io.IOException;
import java.util.List;

import org.apache.commons.lang3.builder.ToStringBuilder;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.model.ListMessagesResponse;
import com.google.api.services.gmail.model.Message;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = GmailServiceTest.class)
@Configuration
public class GmailServiceTest {
    
    private final Logger LOGGER = LoggerFactory.getLogger( getClass() );

    private static final String BDC_SAMPLE = "BDC-1112582155";
    private static final String BDC_WITH_MODIFIED_CARD_DETAILS = "BDC-1697995930";
    
    @Autowired
    private GmailService gmailService;

    @Bean
    public GmailService getGmailService() throws IOException {
        return new GmailService();
    }
    
    @Test
    public void testListMessages() throws Exception {

        LOGGER.info( ToStringBuilder.reflectionToString( 
                gmailService.fetchBdcCardDetailsFromBookingRef( BDC_WITH_MODIFIED_CARD_DETAILS )));
    }
    
    @Test
    public void testFetchLHSecurityToken() throws Exception {
        LOGGER.info( gmailService.fetchLHSecurityToken() );
    }
}
