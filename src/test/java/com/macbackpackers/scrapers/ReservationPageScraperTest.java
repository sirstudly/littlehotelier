package com.macbackpackers.scrapers;

import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import com.gargoylesoftware.htmlunit.html.HtmlPage;
import com.macbackpackers.config.LittleHotelierConfig;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = LittleHotelierConfig.class)
public class ReservationPageScraperTest {

	private final Logger LOGGER = LogManager.getLogger(getClass());
	
	@Autowired
	ReservationPageScraper scraper;
	
	@Test
	public void testGoToReservationPage() throws Exception {
	    HtmlPage reservationPage = scraper.goToReservationPage( 851741 );
	    scraper.tickDeposit( reservationPage );
	}
	
}