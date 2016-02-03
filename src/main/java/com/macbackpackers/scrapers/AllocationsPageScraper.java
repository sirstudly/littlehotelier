
package com.macbackpackers.scrapers;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.text.ParseException;
import java.util.Calendar;
import java.util.Date;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.transaction.Transactional;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.time.FastDateFormat;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import com.gargoylesoftware.htmlunit.WebClient;
import com.gargoylesoftware.htmlunit.html.DomElement;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import com.macbackpackers.beans.Allocation;
import com.macbackpackers.dao.WordPressDAO;
import com.macbackpackers.services.AuthenticationService;
import com.macbackpackers.services.FileService;

/**
 * Scrapes the allocations page for the specified dates.
 * 
 * <ul>
 * <li>wordpress -> refresh button -> inserts "job" record into db.</li>
 * </ul>
 * 
 * <b>Scraper</b>
 * <ul>
 * <li>queries job table (if 'submitted', change to 'processing'), scrapes page contents</li>
 * <li>dumps data into report table</li>
 * <li>updates job to 'completed'</li>
 * </ul>
 * 
 * Each job id will have an associated table job_id, calendar_date_loaded (date) which will hold
 * which job updated the calendar for a particular date
 */
@Component
@Scope( "prototype" )
public class AllocationsPageScraper {

    private final Logger LOGGER = LogManager.getLogger( getClass() );

    public static final FastDateFormat DATE_FORMAT_YYYY_MM_DD = FastDateFormat.getInstance( "yyyy-MM-dd" );
    public static final String POUND = "\u00a3";

    @Autowired
    @Qualifier( "webClientScriptingDisabled" )
    private WebClient webClient;

    @Autowired
    private WordPressDAO dao;

    @Autowired
    private FileService fileService;

    @Autowired
    private AuthenticationService authService;

    @Value( "${lilhotelier.url.calendar}" )
    private String calendarUrl;

    public HtmlPage goToCalendarPage( Date date ) throws IOException {
        String dateAsString = DATE_FORMAT_YYYY_MM_DD.format( date );
        String pageURL = calendarUrl + "?start_date=" + dateAsString;
        LOGGER.info( "Loading calendar page: " + pageURL );
        HtmlPage nextPage = authService.goToPage( pageURL, webClient );
        LOGGER.debug( nextPage.asXml() );
        return nextPage;
    }

    /**
     * Save the given page to disk so we can use it later (for debugging).
     *
     * @param page page to save
     * @param date (for filename)
     */
    public void serialisePageToDisk( HtmlPage page, Date date ) {
        fileService.serialisePageToDisk( page, getCalendarPageSerialisedObjectFilename( date ) );
    }

    /**
     * Returns a unique filename for a given date to use when serilaising/deserialising page data
     * when scraping.
     * 
     * @param date date of calendar page
     * @return filename of calendar page on the given date
     */
    private static String getCalendarPageSerialisedObjectFilename( Date date ) {
        String dateAsString = DATE_FORMAT_YYYY_MM_DD.format( date );
        return "calendar_page_" + dateAsString + ".ser";
    }

    /**
     * Dumps the allocations between the given dates (inclusive). There may be some allocations
     * beyond the end date if the span doesn't fall within an exact 2 week period as that is what is
     * currently shown on the calendar page.
     * 
     * @param jobId the job ID to associate with this dump
     * @param startDate the start date to check allocations for (inclusive)
     * @param endDate the minimum date in which to include allocations for
     * @param useSerialisedDataIfAvailable check if we've already seen this page already and used
     *            the cached version if available.
     * @throws IOException on read/write error
     */
    @Transactional
    public void dumpAllocationsBetween(
            int jobId, Date startDate, Date endDate, boolean useSerialisedDataIfAvailable ) throws IOException {

        Calendar currentDate = Calendar.getInstance();
        currentDate.setTime( startDate );

        while ( currentDate.getTime().before( endDate ) ) {
            HtmlPage calendarPage;
            String serialisedFileName = getCalendarPageSerialisedObjectFilename( currentDate.getTime() );
            if ( useSerialisedDataIfAvailable
                    && new File( serialisedFileName ).exists() ) {
                calendarPage = fileService.loadPageFromDisk( serialisedFileName );
            }
            else {
                // this takes about 10 minutes...
                calendarPage = goToCalendarPage( currentDate.getTime() );
            }

            dumpAllocations( jobId, calendarPage );
            currentDate.add( Calendar.DATE, 14 ); // calendar page shows 2 weeks at a time
        }
    }

    /**
     * Dumps the allocations on the given page to the database.
     * 
     * @param jobId id of job to associate with
     * @param calendarPage the current calendar page
     */
    public void dumpAllocations( int jobId, HtmlPage calendarPage ) {

        // now iterate over all div's and gather all our information
        String currentBedName = null;
        String dataRoomId = null;
        String dataRoomTypeId = null;
        for ( DomElement div : calendarPage.getElementsByTagName( "div" ) ) {
            try {
                String dataDate = div.getAttribute( "data-date" );

                if ( StringUtils.isNotBlank( div.getAttribute( "data-room_type_id" ) ) ) {
                    dataRoomTypeId = div.getAttribute( "data-room_type_id" );
                    currentBedName = null; // reset

                    if ( StringUtils.isNotBlank( div.getAttribute( "data-room_id" ) ) ) {
                        dataRoomId = div.getAttribute( "data-room_id" );
                    }
                    else if ( StringUtils.contains( div.getAttribute( "class" ), "unallocated" ) ) {
                        LOGGER.debug( "unallocated row for room_type_id: " + dataRoomTypeId );
                        dataRoomId = null;
                        currentBedName = "Unallocated";
                    }
                }

                if ( StringUtils.isNotBlank( div.getAttribute( "data-room_id" ) ) ) {
                    LOGGER.debug( "data-room_id: " + dataRoomId );
                    LOGGER.debug( "data-room_type_id: " + dataRoomTypeId );

                    if ( false == div.hasChildNodes() ) {
                        LOGGER.warn( "no child nodes for " + div.asText() );
                    }
                    else {
                        DomElement label = div.getFirstElementChild();
                        if ( false == "label".equals( label.getTagName() ) ) {
                            LOGGER.debug( "not a label? " + label.asText() );
                        }
                        else {
                            LOGGER.debug( "Bed Name: " + label.getAttribute( "title" ) );
                            currentBedName = StringUtils.trimToNull( label.getAttribute( "title" ) );
                        }
                    }
                }
                else if ( StringUtils.isNotBlank( dataDate ) ) {
                    LOGGER.debug( "data-date: " + dataDate );

                    // first entry after the data-date div is not always correct
                    // it could be one day off screen
                    for ( DomElement elem : div.getChildElements() ) {
                        if ( "span".equals( elem.getTagName() ) ) {
                            insertAllocationFromSpan( jobId, Integer.parseInt( dataRoomTypeId ),
                                    dataRoomId == null ? null : Integer.parseInt( dataRoomId ),
                                    currentBedName, dataDate, elem );
                        }
                    }
                    if ( div.hasChildNodes() == false ) {
                        LOGGER.debug( "no records for " + dataDate );
                    }
                }
            }
            catch ( Exception ex ) {
                LOGGER.error( "Exception handled.", ex );
            }
        }
    }

    /**
     * Builds an allocation object from the given span element and inserts it into the db.
     * 
     * @param job job we are currently running
     * @param dataRoomTypeId the unique id for the room <i>type</i> (required)
     * @param dataRoomId room id (optional)
     * @param currentBedName the bed name for the allocation (required)
     * @param dataDate the data date for the record we are currently processing
     * @param span the span element containing the allocation details
     * @throws ParseException if date could not be parsed
     * @throws SQLException on data creation error
     */
    private void insertAllocationFromSpan( int jobId, int dataRoomTypeId,
            Integer dataRoomId, String currentBedName, String dataDate, DomElement span )
                    throws ParseException, SQLException {

        // should have 3 spans
        // 1) wrapper holding the following info
        // 2) the guest name (displayed in table)
        // 3) a resizable arrow on the right
        if ( currentBedName == null ) {
            LOGGER.error( "No current bed name, skipping record..." );
            return;
        }

        LOGGER.debug( "  class: " + span.getAttribute( "class" ) );
        LOGGER.debug( "  style: " + span.getAttribute( "style" ) );
        LOGGER.debug( "  data-reservation_payment_total: " + span.getAttribute( "data-reservation_payment_total" ) );
        LOGGER.debug( "  data-reservation_payment_oustanding: "
                + span.getAttribute( "data-reservation_payment_oustanding" ) );
        LOGGER.debug( "  data-reservation_id: " + span.getAttribute( "data-reservation_id" ) );
        LOGGER.debug( "  data-rate_plan_name: " + span.getAttribute( "data-rate_plan_name" ) );
        LOGGER.debug( "  data-payment_status: " + span.getAttribute( "data-payment_status" ) );
        LOGGER.debug( "  data-href: " + span.getAttribute( "data-href" ) );
        LOGGER.debug( "  data-notes: " + span.getAttribute( "data-notes" ) );
        LOGGER.debug( "  data-guest_name: " + span.getAttribute( "data-guest_name" ) );

        // split room/bed name
        Pattern p = Pattern.compile( "([^\\-]*)-(.*)$" ); // anything but dash for room #, everything else for bed
        Matcher m = p.matcher( currentBedName );
        String room = null, bed = null;
        if ( m.find() == false ) {
            LOGGER.info( "Couldn't determine bed name from '" + currentBedName + "'. Is it a private?" );
            room = currentBedName;
        }
        else {
            room = m.group( 1 );
            bed = m.group( 2 );
        }

        Allocation alloc = new Allocation();
        alloc.setJobId( jobId );
        alloc.setRoomId( dataRoomId );
        alloc.setRoomTypeId( dataRoomTypeId );
        alloc.setRoom( room );
        alloc.setBedName( bed );
        setCheckInOutDates( alloc, dataDate, span );

        // check for "room closures"
        if ( StringUtils.contains( span.getAttribute( "class" ), "room_closure" ) ) {
            DomElement closedRoom = span.getFirstElementChild();
            if ( false == "span".equals( closedRoom.getTagName() ) ) {
                LOGGER.debug( "not a span? " );
                LOGGER.debug( closedRoom.asText() );
            }
            else {
                LOGGER.debug( "closed room?: " + closedRoom.getTextContent() );
                alloc.setGuestName( closedRoom.getTextContent() );
            }
        }
        else {
            if ( StringUtils.contains( span.getAttribute( "class" ), "checked-in" ) ) {
                alloc.setStatus( "checked-in" );
            }
            else if ( StringUtils.contains( span.getAttribute( "class" ), "checked-out" ) ) {
                alloc.setStatus( "checked-out" );
            }
            else if ( StringUtils.contains( span.getAttribute( "class" ), "confirmed" ) ) {
                alloc.setStatus( "confirmed" );
            }
            alloc.setReservationId( Integer.parseInt( span.getAttribute( "data-reservation_id" ) ) );
            alloc.setGuestName( span.getAttribute( "data-guest_name" ) );
            alloc.setPaymentTotal( StringUtils.replaceChars( span.getAttribute( "data-reservation_payment_total" ), POUND + ",", "" ) );
            alloc.setPaymentOutstanding( StringUtils.replaceChars( span.getAttribute( "data-reservation_payment_oustanding" ), POUND + ",", "" ) );
            alloc.setRatePlanName( span.getAttribute( "data-rate_plan_name" ) );
            alloc.setPaymentStatus( span.getAttribute( "data-payment_status" ) );
            alloc.setNumberGuests( calculateNumberOfGuests( span ) );
        }

        alloc.setDataHref( span.getAttribute( "data-href" ) );
        alloc.setNotes( StringUtils.trimToNull( span.getAttribute( "data-notes" ) ) );

        LOGGER.info( "Done allocation!" );
        LOGGER.info( alloc );
        dao.insertAllocation( alloc );
    }

    /**
     * Sums the adults/children/infants from the reservation span.
     * 
     * @param reservationSpan HTML span containing the reservation details on the calendar page
     * @return sum of occupancy values
     */
    private int calculateNumberOfGuests( DomElement reservationSpan ) {
        return Integer.parseInt( reservationSpan.getAttribute( "data-number_adults" ) ) +
                Integer.parseInt( reservationSpan.getAttribute( "data-number_children" ) ) +
                Integer.parseInt( reservationSpan.getAttribute( "data-number_infants" ) );
    }

    /**
     * Sets the checkin/checkout dates on the allocation based on the String values in the form.
     * 
     * @param alloc object to update
     * @param dataDate this is the date in the html table we are currently processing, in format
     *            yyyy-MM-dd
     * @param reservationSpan HTML span of the reservation within the calendar page
     * @throws ParseException on date parse error
     */
    private void setCheckInOutDates( Allocation alloc, String dataDate, DomElement reservationSpan ) throws ParseException {

        // if the start date is defined, then use that. otherwise use the date passed in.
        // usually the start date only appears if the record appears off-screen
        Calendar checkinDate = Calendar.getInstance();
        String checkinDateStr = StringUtils.trimToNull( reservationSpan.getAttribute( "data-start-date" ) );
        LOGGER.info( "checkin-date: " + checkinDateStr );
        checkinDate.setTime( DATE_FORMAT_YYYY_MM_DD.parse( 
                checkinDateStr != null ? checkinDateStr : dataDate ) );
        alloc.setCheckinDate( checkinDate.getTime() );

        // adjust checkout date by number of nights
        Calendar checkoutDate = Calendar.getInstance();
        String checkoutDateStr = StringUtils.trimToNull( reservationSpan.getAttribute( "data-end-date" ) );
        LOGGER.info( "checkout-date: " + checkoutDateStr );
        checkoutDate.setTime( DATE_FORMAT_YYYY_MM_DD.parse( checkoutDateStr ) );
        alloc.setCheckoutDate( checkoutDate.getTime() );
    }

    public void closeAllWindows() {
        webClient.close();
    }

}
