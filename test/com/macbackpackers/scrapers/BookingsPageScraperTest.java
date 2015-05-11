package com.macbackpackers.scrapers;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.Test;

public class BookingsPageScraperTest {

	Logger logger = LogManager.getLogger(getClass());
	BookingsPageScraper scraper = new BookingsPageScraper();

	@Test
	public void testGetPageAsText() throws Exception {
		logger.info(scraper.getPageAsText());
	}

	@Test
	public void testGetPageAsXml() throws Exception {
		logger.info(scraper.getPageAsXml());
	}

}