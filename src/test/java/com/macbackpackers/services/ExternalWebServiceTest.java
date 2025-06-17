package com.macbackpackers.services;

import com.macbackpackers.config.LittleHotelierConfig;
import org.htmlunit.WebClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@ExtendWith( SpringExtension.class )
@ContextConfiguration( classes = LittleHotelierConfig.class )
public class ExternalWebServiceTest {

    private final Logger LOGGER = LoggerFactory.getLogger( getClass() );

    @Autowired
    private ExternalWebService webService;

    @Autowired
    @Qualifier( "webClient" )
    WebClient webClient;

    @Test
    public void testGetCloudbedsLast2faCode() throws Exception {
        LOGGER.info( webService.getLast2faCode( webClient, "cloudbeds" ) );
    }

    @Test
    public void testGetBDCLast2faCode() throws Exception {
        LOGGER.info( webService.getLast2faCode( webClient, "bdc" ) );
    }
}
