package com.macbackpackers.services;

import com.gargoylesoftware.htmlunit.WebClient;
import com.macbackpackers.config.LittleHotelierConfig;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = LittleHotelierConfig.class)
public class ExternalWebServiceTest {

    private final Logger LOGGER = LoggerFactory.getLogger(getClass());

    @Autowired
    private ExternalWebService webService;

    @Autowired
    @Qualifier("webClient")
    WebClient webClient;

    @Test
    public void testGetCloudbedsLast2faCode() throws Exception {
        LOGGER.info(webService.getLast2faCode(webClient, "cloudbeds"));
    }

    @Test
    public void testGetBDCLast2faCode() throws Exception {
        LOGGER.info(webService.getLast2faCode(webClient, "bdc"));
    }
}
