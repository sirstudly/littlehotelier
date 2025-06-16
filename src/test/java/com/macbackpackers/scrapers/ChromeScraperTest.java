package com.macbackpackers.scrapers;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import com.macbackpackers.config.LittleHotelierConfig;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = LittleHotelierConfig.class)
public class ChromeScraperTest {

    private final Logger LOGGER = LoggerFactory.getLogger( getClass() );

    @Autowired
    ChromeScraper scraper;

    @Test
    public void testLoginToCloudbedsAndSaveSession() throws Exception {
        scraper.loginToCloudbedsAndSaveSession();
    }
}
