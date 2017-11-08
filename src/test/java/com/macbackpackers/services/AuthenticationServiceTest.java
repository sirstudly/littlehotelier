package com.macbackpackers.services;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import com.gargoylesoftware.htmlunit.WebClient;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import com.macbackpackers.config.LittleHotelierConfig;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = LittleHotelierConfig.class)
public class AuthenticationServiceTest {
    
    private final Logger LOGGER = LoggerFactory.getLogger( getClass() );

    @Autowired
    AuthenticationService authService;
    
    @Autowired
    @Qualifier( "webClient" )
    private WebClient webClient;

    @Test
    public void testDoLogin() throws Exception {
        authService.doLogin( webClient );
        HtmlPage page = authService.goToPage( "https://app.littlehotelier.com/extranet/reports/summary?property_id=526", webClient );
        LOGGER.debug( page.asXml() );
    }
    
    @Test
    public void testIsRoyalMileBackpackers() throws Exception {
        LOGGER.info( "Is RMB? " + authService.isRoyalMileBackpackers() );
    }
    
    @Test
    public void testGoToPageSkippingLogin() throws Exception {
        HtmlPage page = authService.goToPage( "https://app.littlehotelier.com/extranet/reports/summary?property_id=526", webClient );
        LOGGER.debug( page.asXml() );
    }

}