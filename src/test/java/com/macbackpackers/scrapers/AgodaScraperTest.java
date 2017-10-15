
package com.macbackpackers.scrapers;

import java.time.LocalDate;
import java.time.Period;
import java.time.format.DateTimeFormatter;

import javax.persistence.Transient;

import org.apache.commons.lang3.builder.ToStringBuilder;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import com.gargoylesoftware.htmlunit.WebClient;
import com.gargoylesoftware.htmlunit.html.HtmlButton;
import com.gargoylesoftware.htmlunit.html.HtmlDivision;
import com.gargoylesoftware.htmlunit.html.HtmlLabel;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import com.gargoylesoftware.htmlunit.html.HtmlRadioButtonInput;
import com.gargoylesoftware.htmlunit.html.HtmlSpan;
import com.gargoylesoftware.htmlunit.html.HtmlTextInput;
import com.macbackpackers.beans.CardDetails;
import com.macbackpackers.config.LittleHotelierConfig;
import com.macbackpackers.exceptions.MissingUserDataException;

@RunWith( SpringJUnit4ClassRunner.class )
@ContextConfiguration( classes = LittleHotelierConfig.class )
public class AgodaScraperTest {

    private final Logger LOGGER = LoggerFactory.getLogger( getClass() );

    @Autowired
    private AgodaScraper scraper;

    @Autowired
    @Transient
    @Qualifier( "webClient" )
    private WebClient webClient;
    
    private static final DateTimeFormatter FORMAT_DD_MM_YYYY = DateTimeFormatter.ofPattern( "dd-MM-yyyy" );

    @Test
    public void testGotoPage() throws Exception {
        String bookingRef = "204600099";
        HtmlPage nextPage = scraper.gotoPage( webClient, "https://ycs.agoda.com/en-us/HotelBookings/Index/685076?" );
//        HtmlTextInput bookingIdField = HtmlTextInput.class.cast( nextPage.getElementById( "booking-id" ) );
//        bookingIdField.setText( "" ); // can't figure out why search doesn't return results if set
//        bookingIdField.type( "204600099" );
//        nextPage.setFocusedElement( null );

        // search within a checkin date of +/- 1 month
        HtmlLabel stayDateLabel = nextPage.getFirstByXPath( "//label[@id='stay-date']" );
        nextPage = HtmlPage.class.cast( stayDateLabel.click() );
        HtmlRadioButtonInput stayDateRadioButton = nextPage.getFirstByXPath( "//label[@id='stay-date']/input" );
        stayDateRadioButton.setChecked( true );
        
        final String fromDate = LocalDate.now().minus( Period.ofMonths( 1 ) ).format( FORMAT_DD_MM_YYYY );
        final String toDate = LocalDate.now().plus( Period.ofMonths( 1 ) ).format( FORMAT_DD_MM_YYYY );
        HtmlTextInput fromDateField = nextPage.getFirstByXPath( "//div[@id='start-date']/input" );
        fromDateField.setValueAttribute( fromDate );
        HtmlTextInput toDateField = nextPage.getFirstByXPath( "//div[@id='end-date']/input" );
        toDateField.setValueAttribute( toDate );
        
        HtmlButton searchButton = nextPage.getFirstByXPath( "//button[text()='Search']" );
        nextPage = searchButton.click();
        nextPage.getWebClient().waitForBackgroundJavaScript( 30000 ); // wait for page to load
        LOGGER.debug( nextPage.asXml() );
        
        // find the matched row by booking id and click on it to expand
        HtmlDivision selectedBookingDiv = nextPage.getFirstByXPath( 
                String.format( "//div[@id='booking-%s']/div/div/div", bookingRef ) );
        if ( selectedBookingDiv == null ) {
            throw new MissingUserDataException( "Unable to find booking AGO-" + bookingRef );
        }
        nextPage = selectedBookingDiv.click();
        
        // now find the UPC link and click on it
        HtmlSpan upcSpan = nextPage.getFirstByXPath( 
                String.format( "//a[@id='upc-payment-%s']/span", bookingRef ) );
        if ( upcSpan == null ) {
            throw new MissingUserDataException( "Unable to find UPC link for AGO-" + bookingRef );
        }
        nextPage = upcSpan.click();
        
        // find the card details
        HtmlDivision paymentDiv = nextPage.getFirstByXPath( "//div[@id='upc-payment-dialog']/div/div/div[@class='modal-body']" );
    }
    
    @Test
    public void getBookingDetails() throws Exception {
        CardDetails cardDetails = scraper.getAgodaCardDetails( webClient, "999999999" );
        LOGGER.info( ToStringBuilder.reflectionToString( cardDetails ) );
    }
    
}
