package com.macbackpackers.jobs;

import java.text.ParseException;
import java.util.Date;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import com.gargoylesoftware.htmlunit.html.HtmlPage;
import com.macbackpackers.scrapers.AllocationsPageScraper;
import com.macbackpackers.scrapers.BookingsPageScraper;

/**
 * Job which searches for bookings booked on a particular date 
 * (for HW/HB bookings) and creates a confirm deposit job for 
 * any that haven't been done yet.
 */
@Component
@Scope( "prototype" )
public class ScrapeReservationsBookedOnJob extends AbstractJob {

    @Autowired
    private BookingsPageScraper bookingScraper;
    
    @Override
    public void processJob() throws Exception {
        Date bookedOnDate = getBookedOnDate();
        HtmlPage bookingsPage = bookingScraper.goToBookingPageBookedOn( bookedOnDate, "HWL" );
        bookingScraper.createConfirmDepositJobs( bookingsPage );
        bookingsPage = bookingScraper.goToBookingPageBookedOn( bookedOnDate, "HBK" );
        bookingScraper.createConfirmDepositJobs( bookingsPage );
    }
    
    /**
     * Gets the booked on date to scrape for the booking data.
     * 
     * @return non-null date parameter
     * @throws ParseException
     */
    private Date getBookedOnDate() throws ParseException {
        return AllocationsPageScraper.DATE_FORMAT_YYYY_MM_DD.parse( getParameter( "booked_on_date" ) );
    }
    
}