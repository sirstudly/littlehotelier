package com.macbackpackers.services;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import com.gargoylesoftware.htmlunit.WebClient;
import com.macbackpackers.config.LittleHotelierConfig;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = LittleHotelierConfig.class)
public class AuthenticationServiceTest {
    
    @Autowired
    AuthenticationService authService;
    
    @Autowired 
    WebClient webClient;

    @Test
    public void testDoLogin() throws Exception {
        authService.doLogin();
    }
    
    @Test
    public void testGoToPageSkippingLogin() throws Exception {
        authService.loginAndGoToPage( "https://emea.littlehotelier.com/extranet/reports/summary?property_id=533", webClient );
    }

    
}