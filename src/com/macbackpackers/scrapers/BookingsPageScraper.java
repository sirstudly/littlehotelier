package com.macbackpackers.scrapers;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Scope;
import org.springframework.core.env.Environment;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.stereotype.Component;

import com.gargoylesoftware.htmlunit.WebClient;
import com.gargoylesoftware.htmlunit.html.DomElement;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import com.macbackpackers.beans.Allocation;
import com.macbackpackers.dao.WordPressDAO;
import com.macbackpackers.services.AuthenticationService;
import com.macbackpackers.services.FileService;

/**
 * Scrapes the bookings page
 *
 */
@Component
@Scope( "prototype" )
public class BookingsPageScraper {
    
    private final Logger LOGGER = LogManager.getLogger( getClass() );

    public static final SimpleDateFormat DATE_FORMAT_YYYY_MM_DD = new SimpleDateFormat( "yyyy-MM-dd" );
    public static final SimpleDateFormat DATE_FORMAT_BOOKING_URL = new SimpleDateFormat( "dd+MMM+yyyy" );
	
    @Autowired
    private WebClient webClient;

    @Autowired
    private AuthenticationService authService;

    @Autowired
    private FileService fileService;
    
    @Autowired
    private WordPressDAO wordPressDAO;

    @Autowired
    private Environment env;
    
    @Value( "${lilhotelier.url.bookings}" )
    private String bookingUrl;
    
    public HtmlPage goToBookingPage( Date date ) throws IOException {
        String pageURL = getBookingsURLForDate( date );
        LOGGER.info( "Loading bookings page: " + pageURL );
        HtmlPage nextPage = authService.loginAndGoToPage( pageURL, webClient );
        LOGGER.info( nextPage.asXml() );

        // save it to disk so we can use it later - ConcurrentModificationException?
        //fileService.serialisePageToDisk( nextPage, getBookingPageSerialisedObjectFilename( date ) );
        return nextPage;
    }
    
    /**
     * Returns the booking URL for the given date.
     * 
     * @param date date to query
     * @return URL of bookings arriving on this date
     */
    private String getBookingsURLForDate( Date date ) {
        return bookingUrl.replaceAll( "__DATE__", DATE_FORMAT_BOOKING_URL.format( date ) );
    }

    /**
     * Returns a unique filename for a given date to use when serilaising/deserialising
     * page data when scraping.
     * 
     * @param date date of calendar page
     * @return filename of calendar page on the given date
     */
    private static String getBookingPageSerialisedObjectFilename( Date date ) {
        String dateAsString = DATE_FORMAT_YYYY_MM_DD.format( date );
        return "bookings_page_" + dateAsString + ".ser";
    }

    /**
     * Updates the extended attributes on the existing allocation records
     * for the given jobId.
     * 
     * @param jobId ID of job to update
     * @param bookingsPage the current bookings page to parse data from
     */
    public void updateBookings( int jobId, HtmlPage bookingsPage ) {
        for( DomElement tr : bookingsPage.getElementsByTagName( "tr" ) ) {
            try {
                String dataId = tr.getAttribute( "data-id" );
                String styleClass = tr.getAttribute( "class" ); // read or unread
                
                if( StringUtils.isNotBlank( dataId )) {
                    LOGGER.info( "Found record " + dataId + " " + styleClass );
                    
                    // attempt to load the existing record if possible
                    List<Allocation> allocList = wordPressDAO.queryAllocationsByJobIdAndReservationId( 
                            jobId, Integer.parseInt( dataId ) );
                    if( allocList.isEmpty() ) {
                        LOGGER.warn( "No allocation record found for reservation_id " + dataId + " and job " + jobId );
                        LOGGER.warn( "This might occur if the booking has come in in between the time the original scraper job has run and now" );
                        continue;
                    }
                    
                    // HACK HACK! because some reservations have multiple allocations
                    // we'll be updating *all* records for that booking in this method.
                    // The updateAllocation() call below will update *all* matching records
                    // by reservation_id and job_id (not just the one we're modifying here)
                    
                    // may want to fix this later if i split out the table so it's not flattened
                    Allocation alloc = allocList.get( 0 );
                    
                    DomElement td = tr.getFirstElementChild();

                    // viewed_yn
                    if( "read".equals( tr.getAttribute( "class" ) ) ) {
                        alloc.setViewed( true );
                    }
                    else if( "unread".equals( tr.getAttribute( "class" ) ) ) {
                        alloc.setViewed( false );
                    }
                    else {
                        LOGGER.warn( "Unsupported attribute on booking row: " + tr.getAttribute( "class" ) );
                    }

                    // status       
                    {
                        DomElement ahref = td.getFirstElementChild();
                        if( false == "a".equals( ahref.getTagName() ) ) {
                            LOGGER.warn( "Expecting link but was " + ahref.getTagName() + " : " + ahref.asText() );
                        } else {
                            LOGGER.info( "  booking href: " + ahref.getAttribute( "href" ) );
                            DomElement span = ahref.getFirstElementChild();
                            if( span != null ) {
                                LOGGER.info( "  status: " + span.getAttribute( "class" ) );
                                alloc.setStatus( span.getAttribute( "class" ) );
                            } else {
                                LOGGER.warn( "No span found in status? " );
                            }
                        }
                    } // status
                    
                    // existing record should already have the guest name(s)
                    td = td.getNextElementSibling();
                    LOGGER.info( "  name: " + StringUtils.trim( td.getTextContent() ) );

                    td = td.getNextElementSibling();
                    alloc.setBookingReference( StringUtils.trim( td.getTextContent() ) );
                    LOGGER.info( "  booking_reference: " + alloc.getBookingReference() );
                    
                    td = td.getNextElementSibling();
                    alloc.setBookingSource( StringUtils.trim( td.getTextContent() ) );
                    LOGGER.info( "  booking_source: " + alloc.getBookingSource() );

                    td = td.getNextElementSibling();
                    LOGGER.info( "  guests: " + StringUtils.trim( td.getTextContent() ) );

                    // existing record should have checkin/checkout dates
                    td = td.getNextElementSibling();
                    LOGGER.info( "  check in: " + StringUtils.trim( td.getTextContent() ) );

                    td = td.getNextElementSibling();
                    LOGGER.info( "  check out: " + StringUtils.trim( td.getTextContent() ) );

                    td = td.getNextElementSibling();
                    alloc.setBookedDate( StringUtils.trim( td.getTextContent() ) );
                    LOGGER.info( "  booked on: " + StringUtils.trim( td.getTextContent() ) );

                    td = td.getNextElementSibling();
                    LOGGER.info( "  total: " + StringUtils.trim( td.getTextContent() ) );

                    td = td.getNextElementSibling();
                    alloc.setEta( StringUtils.trim( td.getTextContent() ) );
                    LOGGER.info( "  ETA: " + alloc.getEta() );

                    td = td.getNextElementSibling();
                    LOGGER.info( "  number of 'rooms': " + StringUtils.trim( td.getTextContent() ) );
            
                    // write the updated attributes to datastore
                    wordPressDAO.updateAllocation( alloc );

                } // data-id isNotBlank
                
            } catch ( Exception ex ) {
                LOGGER.error( "Exception handled.", ex );
            }
        }
    }
    
    /**
     * Updates the calendar records using by querying the bookings page between the given dates (inclusive) 
     * for the given job. 
     * 
     * @param jobId the job ID to associate with this dump
     * @param startDate the start date to check allocations for (inclusive)
     * @param endDate the minimum date in which to include allocations for
     * @param useSerialisedDataIfAvailable check if we've already seen this page already and used the
     *  cached version if available.
     * @throws IOException on read/write error
     */
    public void updateBookingsBetween( 
            int jobId, Date startDate, Date endDate, boolean useSerialisedDataIfAvailable ) throws IOException  {
        
        Calendar currentDate = Calendar.getInstance();
        currentDate.setTime( startDate );
        
        while( currentDate.getTime().compareTo( endDate ) <= 0 ) {
            HtmlPage bookingsPage;
            String serialisedFileName = getBookingPageSerialisedObjectFilename( currentDate.getTime() );
            if( useSerialisedDataIfAvailable 
                    && new File( serialisedFileName ).exists() ) {
                bookingsPage = fileService.loadPageFromDisk( serialisedFileName );
            } else {
                // this takes about 10 minutes...
                bookingsPage = goToBookingPage( currentDate.getTime() );
            }
    
            updateBookings( jobId, bookingsPage );
            currentDate.add( Calendar.DATE, 1 ); // keep going to the next day
        }
    }


}