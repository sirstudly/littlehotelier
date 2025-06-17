package com.macbackpackers.scrapers;

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
public class ChromeScraperTest {

    private final Logger LOGGER = LoggerFactory.getLogger( getClass() );

    @Autowired
    ChromeScraper scraper;

    @Test
    public void testLoginToCloudbedsAndSaveSession() throws Exception {
        scraper.loginToCloudbedsAndSaveSession();
    }
}
