package com.macbackpackers.scrapers;

import com.gargoylesoftware.htmlunit.WebClient;
import com.gargoylesoftware.htmlunit.html.HtmlPage;

/**
 * Scrapes the bookings page
 * 
 *
 */
public class BookingsPageScraper {
	
	public String getPageAsXml() throws Exception {
	    final WebClient webClient = new WebClient();
	    final HtmlPage page = webClient.getPage("http://htmlunit.sourceforge.net");
	    final String pageAsXml = page.asXml();
	    webClient.closeAllWindows();
	    return pageAsXml;
	}
	
	public String getPageAsText() throws Exception {
	    final WebClient webClient = new WebClient();
	    final HtmlPage page = webClient.getPage("http://htmlunit.sourceforge.net");
	    final String pageAsText = page.asText();
	    webClient.closeAllWindows();
	    return pageAsText;
	}
	
}