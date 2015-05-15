package com.macbackpackers.scrapers;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.sql.SQLException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import com.gargoylesoftware.htmlunit.WebClient;
import com.gargoylesoftware.htmlunit.html.DomElement;
import com.gargoylesoftware.htmlunit.html.HtmlForm;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import com.gargoylesoftware.htmlunit.html.HtmlPasswordInput;
import com.gargoylesoftware.htmlunit.html.HtmlSubmitInput;
import com.gargoylesoftware.htmlunit.html.HtmlTextInput;
import com.macbackpackers.beans.Allocation;
import com.macbackpackers.beans.Job;
import com.macbackpackers.dao.WordPressDAO;

/**
 * Scrapes the allocations page for the specified dates.
 * 
 * TODO:
 * <ul>
 * <li>wordpress -> refresh button -> inserts "job" record into db.</li>
 * <li>fires off (async) shell command to scrape.</li>
 * <li>Polls job every 10 seconds until complete. Displays content of page.</li>
 * </ul>
 * 
 * <b>Scraper</b>
 * <ul>
 * <li>queries job table (if 'submitted', change to 'processing'), scrapes page contents</li>
 * <li>dumps data into report table</li>
 * <li>updates job to 'completed'</li>
 * </ul>
 * 
 * Each job id will have an associated table job_id, calendar_date_loaded (date) which will hold which job updated the
 * calendar for a particular date
 */
@Component
public class AllocationsPageScraper {

    private final Logger LOGGER = LogManager.getLogger( getClass() );

    private static final SimpleDateFormat DATE_FORMAT_YYYY_MM_DD = new SimpleDateFormat( "yyyy-MM-dd" );

    // current session
    private WebClient webClient;

    @Autowired
    private WordPressDAO dao;

    @Autowired
    private Environment env;
    
    public void doLogin() throws Exception {

        webClient = new WebClient();
        HtmlPage loginPage = webClient.getPage( env.getProperty( "lilhotelier.url.login" ) );

        // The form doesn't have a name so just take the only one on the page
        List<HtmlForm> forms = loginPage.getForms();
        HtmlForm form = forms.iterator().next();

        HtmlSubmitInput button = form.getInputByName( "commit" );
        HtmlTextInput usernameField = form.getInputByName( "user_session[username]" );
        HtmlPasswordInput passwordField = form.getInputByName( "user_session[password]" );

        // Change the value of the text field
        usernameField.setValueAttribute( env.getProperty( "lilhotelier.username" ) );
        passwordField.setValueAttribute( env.getProperty( "lilhotelier.password" ) );

        HtmlPage nextPage = button.click();
        LOGGER.info( "Finished logging in" );
        LOGGER.info( nextPage.asXml() );
    }

    public HtmlPage goToCalendarPage( Date date ) throws Exception {
        String dateAsString = DATE_FORMAT_YYYY_MM_DD.format( date );
        HtmlPage nextPage = webClient
                .getPage( env.getProperty( "lilhotelier.url.calendar" ) + "?start_date=" + dateAsString );
        LOGGER.info( "Calendar page" );

        serialiseToDisk( nextPage, getCalendarPageSerialisedObjectFilename( date ) );
        LOGGER.info( nextPage.asXml() );
        return nextPage;
    }

    private static String getCalendarPageSerialisedObjectFilename( Date date ) {
        String dateAsString = DATE_FORMAT_YYYY_MM_DD.format( date );
        return "calendar_page_" + dateAsString + ".ser";
    }

    public void dumpPageForJob( Job job, HtmlPage calendarPage ) throws Exception {

        // read in the file if page not specified
        if ( calendarPage == null ) {
            ObjectInputStream ois = new ObjectInputStream( new FileInputStream(
                    getCalendarPageSerialisedObjectFilename( job.getCreatedDate() ) ) );
            calendarPage = (HtmlPage) ois.readObject();
            ois.close();
        }

        // now iterate over all div's and gather all our information
        String currentBedName = null;
        String dataRoomId = "";
        for( DomElement div : calendarPage.getElementsByTagName( "div" ) ) {
            try {
                String dataDate = div.getAttribute( "data-date" );

                if ( StringUtils.isNotBlank( div.getAttribute( "data-room_id" ) ) ) {
                    dataRoomId = div.getAttribute( "data-room_id" );
                }

                if ( StringUtils.isNotBlank( div.getAttribute( "data-room_id" ) ) ) {
                    LOGGER.info( "data-room_id: " + dataRoomId );
                    LOGGER.info( "data-room_type_id: " + div.getAttribute( "data-room_type_id" ) );

                    if ( false == div.hasChildNodes() ) {
                        LOGGER.warn( "no child nodes for " + div.asText() );
                    } else {
                        DomElement label = div.getFirstElementChild();
                        if ( false == "label".equals( label.getTagName() ) ) {
                            LOGGER.info( "not a label? " + label.asText() );
                        } else {
                            LOGGER.info( "Bed Name: " + label.getAttribute( "title" ) );
                            currentBedName = label.getAttribute( "title" );
                        }
                    }
                } else if ( StringUtils.isNotBlank( dataDate ) ) {
                    LOGGER.info( "data-date: " + dataDate );

                    // first entry after the data-date div is not always correct
                    // it could be one day off screen
                    for( DomElement elem : div.getChildElements() ) {
                        if ( "span".equals( elem.getTagName() ) ) {
                            insertAllocationFromSpan( job, Integer.parseInt( dataRoomId ),
                                    currentBedName, dataDate, elem );
                        }
                    }
                    if ( div.hasChildNodes() == false ) {
                        LOGGER.info( "no records for " + dataDate );
                    }
                }
            } catch ( Exception ex ) {
                LOGGER.error( "Exception handled.", ex );
            }
        }
    }

    /**
     * Builds an allocation object from the given span element and inserts it into the db.
     * 
     * @param job
     *            job we are currently running
     * @param dataRoomId
     *            room id
     * @param currentBedName
     *            the bed name for the allocation (required)
     * @param dataDate
     *            the data date for the record we are currently processing
     * @param span
     *            the span element containing the allocation details
     * @throws ParseException
     *             if date could not be parsed
     * @throws SQLException
     *             on data creation error
     */
    private void insertAllocationFromSpan( Job job, int dataRoomId, String currentBedName, String dataDate,
            DomElement span )
            throws ParseException, SQLException {

        // should have 3 spans
        // 1) wrapper holding the following info
        // 2) the guest name (displayed in table)
        // 3) a resizable arrow on the right
        if ( currentBedName == null ) {
            LOGGER.error( "No current bed name, skipping record..." );
            return;
        }

        LOGGER.info( "  class: " + span.getAttribute( "class" ) );
        LOGGER.info( "  style: " + span.getAttribute( "style" ) );
        LOGGER.info( "  data-reservation_payment_total: " + span.getAttribute( "data-reservation_payment_total" ) );
        LOGGER.info( "  data-reservation_payment_oustanding: "
                + span.getAttribute( "data-reservation_payment_oustanding" ) );
        LOGGER.info( "  data-reservation_id: " + span.getAttribute( "data-reservation_id" ) );
        LOGGER.info( "  data-rate_plan_name: " + span.getAttribute( "data-rate_plan_name" ) );
        LOGGER.info( "  data-payment_status: " + span.getAttribute( "data-payment_status" ) );
        LOGGER.info( "  data-occupancy: " + span.getAttribute( "data-occupancy" ) );
        LOGGER.info( "  data-href: " + span.getAttribute( "data-href" ) );
        LOGGER.info( "  data-guest_name: " + span.getAttribute( "data-guest_name" ) );

        // split room/bed name
        Pattern p = Pattern.compile( "([^\\-]*)-(.*)$" ); // anything but dash for room #, everything else for bed
        Matcher m = p.matcher( currentBedName );
        String room = null, bed = null;
        if ( m.find() == false ) {
            LOGGER.warn( "Couldn't determine bed name from '" + currentBedName + "'. Is it a private?" );
            room = currentBedName;
        } else {
            room = m.group( 1 );
            bed = m.group( 2 );
        }

        Allocation alloc = new Allocation();
        alloc.setJobId( job.getId() );
        alloc.setRoomId( dataRoomId );
        alloc.setRoom( room );
        alloc.setBedName( bed );
        setCheckInOutDates( alloc, dataDate, span.getAttribute( "style" ) );

        // check for "room closures"
        if ( StringUtils.contains( span.getAttribute( "class" ), "room_closure" ) ) {
            DomElement closedRoom = span.getFirstElementChild();
            if ( false == "span".equals( closedRoom.getTagName() ) ) {
                LOGGER.info( "not a span? " );
                LOGGER.info( closedRoom.asText() );
            } else {
                LOGGER.info( "closed room?: " + closedRoom.getTextContent() );
                alloc.setGuestName( closedRoom.getTextContent() );
            }
        } else {

            alloc.setReservationId( Integer.parseInt( span.getAttribute( "data-reservation_id" ) ) );
            alloc.setGuestName( span.getAttribute( "data-guest_name" ) );
            alloc.setPaymentTotal( span.getAttribute( "data-reservation_payment_total" ) );
            alloc.setPaymentOutstanding( span.getAttribute( "data-reservation_payment_oustanding" ) );
            alloc.setRatePlanName( span.getAttribute( "data-rate_plan_name" ) );
            alloc.setPaymentStatus( span.getAttribute( "data-payment_status" ) );
            alloc.setNumberGuests( calculateNumberOfGuests( span.getAttribute( "data-occupancy" ) ) );
        }

        alloc.setDataHref( span.getAttribute( "data-href" ) );

        LOGGER.info( "Done allocation!" );
        LOGGER.info( alloc );
        dao.insertAllocation( alloc );
    }

    /**
     * Returns the attribute from the given style string.
     * 
     * @param attribute
     *            name of attribute
     * @param unit
     *            unit to match against
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
     * @param occupancy
     *            , e.g. 2 / 0 / 0 technically, the latter two numbers should be 0
     * @return sum of occupancy values
     */
    private int calculateNumberOfGuests( String occupancy ) {
        String values[] = occupancy.split( "/" );
        if ( values.length != 3 ) {
            LOGGER.error( "unexpected occupancy " + occupancy );
        }
        int count = 0;
        for( int i = 0; i < values.length; i++ ) {
            count += Integer.parseInt( StringUtils.trim( values[i] ) );
        }
        return count;
    }

    /**
     * Sets the checkin/checkout dates on the allocation based on the String values in the form.
     * 
     * @param alloc
     *            object to update
     * @param dataDate
     *            this is the date in the html table we are currently processing, in format yyyy-MM-dd
     * @param style
     *            this is the style attribute on the form
     * @throws ParseException
     *             on date parse error
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
        } else {
            numberNights = 1 + ((width - 56) / 61); // number of additional nights
        }
        LOGGER.info( "Number of nights: " + numberNights );

        // adjust checkout date by number of nights
        Calendar checkoutDate = Calendar.getInstance();
        checkoutDate.setTime( alloc.getCheckinDate() );
        checkoutDate.add( Calendar.DATE, numberNights );
        alloc.setCheckoutDate( checkoutDate.getTime() );
    }

    private void serialiseToDisk( HtmlPage page, String filename ) {
        try {
            FileOutputStream fout = new FileOutputStream( filename );
            ObjectOutputStream oos = new ObjectOutputStream( fout );
            oos.writeObject( page );
            oos.close();

        } catch ( FileNotFoundException e ) {
            LOGGER.error( e );
        } catch ( IOException e ) {
            LOGGER.error( e );
        }
    }

    public void closeAllWindows() {
        webClient.closeAllWindows();
    }

    public String getPageAsXml() throws Exception {
        final WebClient webClient = new WebClient();
        final HtmlPage page = webClient.getPage( env.getProperty( "lilhotelier.url.login" ) );
        final String pageAsXml = page.asXml();
        webClient.closeAllWindows();
        return pageAsXml;
    }

    public String getPageAsText() throws Exception {
        final WebClient webClient = new WebClient();
        final HtmlPage page = webClient.getPage( env.getProperty( "lilhotelier.url.login" ) );
        final String pageAsText = page.asText();
        webClient.closeAllWindows();
        return pageAsText;
    }

}