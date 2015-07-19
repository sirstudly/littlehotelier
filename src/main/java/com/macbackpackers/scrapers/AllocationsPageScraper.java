
package com.macbackpackers.scrapers;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.text.ParseException;
import java.util.Calendar;
import java.util.Date;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
        HtmlPage nextPage = authService.loginAndGoToPage( pageURL, webClient );
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
        LOGGER.debug( "  data-occupancy: " + span.getAttribute( "data-occupancy" ) );
        LOGGER.debug( "  data-href: " + span.getAttribute( "data-href" ) );
        LOGGER.debug( "  data-notes: " + span.getAttribute( "data-notes" ) );
        LOGGER.debug( "  data-guest_name: " + span.getAttribute( "data-guest_name" ) );

        // split room/bed name
        Pattern p = Pattern.compile( "([^\\-]*)-(.*)$" ); // anything but dash for room #, everything else for bed
        Matcher m = p.matcher( currentBedName );
        String room = null, bed = null;
        if ( m.find() == false ) {
            LOGGER.warn( "Couldn't determine bed name from '" + currentBedName + "'. Is it a private?" );
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
        setCheckInOutDates( alloc, dataDate, span.getAttribute( "style" ) );

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
            alloc.setNumberGuests( calculateNumberOfGuests( span.getAttribute( "data-occupancy" ) ) );
        }

        alloc.setDataHref( span.getAttribute( "data-href" ) );
        alloc.setNotes( StringUtils.trimToNull( span.getAttribute( "data-notes" ) ) );

        LOGGER.info( "Done allocation!" );
        LOGGER.info( alloc );
        dao.insertAllocation( alloc );
    }

    /**
     * Returns the attribute from the given style string.
     * 
     * @param attribute name of attribute
     * @param unit unit to match against
     * @return attribute value
     */
    private static String getStyleAttribute( String style, String attribute, String unit ) {
        Pattern p = Pattern.compile( attribute + ": *([\\-0-9]*)" + unit );
        Matcher m = p.matcher( style );
        if ( m.find() ) {
            return m.group( 1 );
        }
        throw new RuntimeException( "Attribute " + attribute + " not found in style " + style );
    }

    /**
     * Sums the adults/children/infants from the occupancy field.
     * 
     * @param occupancy , e.g. 2 / 0 / 0 technically, the latter two numbers should be 0
     * @return sum of occupancy values
     */
    private int calculateNumberOfGuests( String occupancy ) {
        String values[] = occupancy.split( "/" );
        if ( values.length != 3 ) {
            LOGGER.error( "unexpected occupancy " + occupancy );
        }
        int count = 0;
        for ( int i = 0 ; i < values.length ; i++ ) {
            count += Integer.parseInt( StringUtils.trim( values[i] ) );
        }
        return count;
    }

    /**
     * Sets the checkin/checkout dates on the allocation based on the String values in the form.
     * 
     * @param alloc object to update
     * @param dataDate this is the date in the html table we are currently processing, in format
     *            yyyy-MM-dd
     * @param style this is the style attribute on the form
     * @throws ParseException on date parse error
     */
    private void setCheckInOutDates( Allocation alloc, String dataDate, String style ) throws ParseException {

        int leftOffset = Integer.parseInt( getStyleAttribute( style, "left", "px" ) );
        // normally, the offset would be 30px but if they are off the screen (to the left)
        // then the one day off screen is -31px
        // 2 days off screen is -92px (-31px - 56px - 5px buffer), etc...
        int daysToSubtract = 0;
        if ( leftOffset < 0 ) {

            LOGGER.info( "offscreen record found" );
            // check if my calculation is correct
            // this should be a multiple of 61
            if ( (leftOffset - 30) % 61 != 0 ) {
                LOGGER.warn( "leftOffset has unexpected value " + leftOffset );
            }

            daysToSubtract = (leftOffset - 30) / 61;
        }

        Calendar checkinDate = Calendar.getInstance();
        checkinDate.setTime( DATE_FORMAT_YYYY_MM_DD.parse( dataDate ) );
        checkinDate.add( Calendar.DATE, daysToSubtract );
        alloc.setCheckinDate( checkinDate.getTime() );

        // calculate checkout date by calculating number of nights
        // added to the checkin date
        // width of a single day is 56px, gap is 5px
        int width = Integer.parseInt( getStyleAttribute( style, "width", "px" ) );
        int numberNights = 0;
        // width (minus first night) should be divisible by 61
        if ( (width - 56) % 61 != 0 ) {
            LOGGER.error( "unexpected width of record " + width );
        }
        if ( width == 56 ) {
            numberNights = 1;
        }
        else {
            numberNights = 1 + ((width - 56) / 61); // number of additional nights
        }
        LOGGER.info( "Number of nights: " + numberNights );

        // adjust checkout date by number of nights
        Calendar checkoutDate = Calendar.getInstance();
        checkoutDate.setTime( alloc.getCheckinDate() );
        checkoutDate.add( Calendar.DATE, numberNights );
        alloc.setCheckoutDate( checkoutDate.getTime() );
    }

    public void closeAllWindows() {
        webClient.close();
    }

}
