
package com.macbackpackers.services;

import org.htmlunit.WebClient;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import com.macbackpackers.config.LittleHotelierConfig;

@RunWith( SpringJUnit4ClassRunner.class )
@ContextConfiguration( classes = LittleHotelierConfig.class )
public class CaptchaSolverServiceTest {

    @Autowired
    CaptchaSolverService service;

    @Autowired
    @Qualifier( "webClientForCloudbeds" )
    WebClient webClient;

    @Test
    public void testRecaptchaReportGood() throws Exception {
        service.recaptchaReportGood( webClient, "62077284165" );
        service.recaptchaReportGood( webClient, "62077288134" );
        service.recaptchaReportGood( webClient, "62077286515" );
        service.recaptchaReportGood( webClient, "62077265076" );
    }
    
 }
