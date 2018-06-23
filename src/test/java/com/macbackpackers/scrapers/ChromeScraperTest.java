package com.macbackpackers.scrapers;

import java.util.Calendar;

import org.apache.commons.lang3.builder.ToStringBuilder;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import com.macbackpackers.beans.GuestDetails;
import com.macbackpackers.config.LittleHotelierConfig;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = LittleHotelierConfig.class)
public class ChromeScraperTest {

    private final Logger LOGGER = LoggerFactory.getLogger( getClass() );

    @Autowired
    ChromeScraper scraper = new ChromeScraper();

    @Test
    public void testRetrieveCardDetailsFromLH() throws Exception {
        Calendar c = Calendar.getInstance();
        c.set( Calendar.DATE, 2 );
        c.set( Calendar.MONTH, Calendar.AUGUST );
        GuestDetails guest = scraper.retrieveGuestDetailsFromLH( "LH1804058846079", c.getTime() );
        c.set( Calendar.DATE, 20 );
        c.set( Calendar.MONTH, Calendar.JUNE );
        GuestDetails guest2 = scraper.retrieveGuestDetailsFromLH( "LH1803298780125", c.getTime() );
        LOGGER.info( ToStringBuilder.reflectionToString( guest ) );
        LOGGER.info( ToStringBuilder.reflectionToString( guest2 ) );
    }
}
