package com.macbackpackers.services;

import org.htmlunit.WebClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import com.macbackpackers.config.LittleHotelierConfig;

@ExtendWith( SpringExtension.class )
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
