
package com.macbackpackers.scrapers;

import java.io.IOException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.time.FastDateFormat;
import org.htmlunit.WebClient;
import org.htmlunit.html.DomElement;
import org.htmlunit.html.HtmlPage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import com.macbackpackers.beans.Allocation;
import com.macbackpackers.beans.AllocationList;
import com.macbackpackers.beans.GuestCommentReportEntry;
import com.macbackpackers.dao.WordPressDAO;
import com.macbackpackers.exceptions.UnrecoverableFault;
import com.macbackpackers.scrapers.matchers.BedAssignment;
import com.macbackpackers.scrapers.matchers.RoomBedMatcher;
import com.macbackpackers.services.AuthenticationService;

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

    private final Logger LOGGER = LoggerFactory.getLogger( getClass() );

    public static final FastDateFormat DATE_FORMAT_YYYY_MM_DD = FastDateFormat.getInstance( "yyyy-MM-dd" );
    public static final String POUND = "\u00a3";

    @Autowired
    private WordPressDAO dao;

    @Autowired
    private AuthenticationService authService;

    @Autowired
    private RoomBedMatcher roomBedMatcher;

    @Value( "${lilhotelier.propertyid}" )
    private String lhPropertyId;

    /**
     * Returns the URL for the calendar page.
     * 
     * @return calendar URL
     */
    private String getCalendarURL() {
        return String.format( "https://app.littlehotelier.com/extranet/properties/%s/calendar", lhPropertyId );
    }

    /**
     * Loads the calendar page starting from the given date.
     * 
     * @param webClient the web client to use
     * @param date start (checkin date)
     * @return loaded page
     * @throws IOException on load error
     */
    private HtmlPage goToCalendarPage( WebClient webClient, Date date ) throws IOException {
        String dateAsString = DATE_FORMAT_YYYY_MM_DD.format( date );
        String pageURL = getCalendarURL() + "?start_date=" + dateAsString;
        LOGGER.info( "Loading calendar page: " + pageURL );
        HtmlPage nextPage = authService.goToPage( pageURL, webClient );
        LOGGER.trace( nextPage.asXml() );
        return nextPage;
    }

    /**
     * Dumps the allocations starting on the given date (inclusive). There may be some allocations
     * beyond the end date if the span doesn't fall within an exact 2 week period as that is what is
     * currently shown on the calendar page.
     * 
     * @param webClient web client instance to use
     * @param jobId the job ID to associate with this dump
     * @param startDate the start date to check allocations for (inclusive)
     * @throws IOException on read/write error
     */
//    @Transactional
    public void dumpAllocationsFrom( WebClient webClient, int jobId, Date startDate ) throws IOException {
        Calendar currentDate = Calendar.getInstance();
        currentDate.setTime( startDate );
        dumpAllocations( jobId, goToCalendarPage( webClient, currentDate.getTime() ) );
    }

    /**
     * Dumps the allocations on the given page to the database.
     * 
     * @param jobId id of job to associate with
     * @param calendarPage the current calendar page
     */
    public void dumpAllocations( int jobId, HtmlPage calendarPage ) throws IOException {

        // now iterate over all div's and gather all our information
        String currentBedName = null;
        String dataRoomId = null;
        String dataRoomTypeId = null;
        AllocationList allocations = new AllocationList();
        List<GuestCommentReportEntry> guestComments = new ArrayList<>();

        for ( DomElement div : calendarPage.getElementsByTagName( "div" ) ) {
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
                    LOGGER.warn( "no child nodes for " + div.getTextContent() );
                }
                else {
                    DomElement label = div.getFirstElementChild();
                    if ( false == "label".equals( label.getTagName() ) ) {
                        LOGGER.debug( "not a label? " + label.getTextContent() );
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
                        collectAllocationsFromSpan( jobId, Integer.parseInt( dataRoomTypeId ),
                                dataRoomId == null ? null : Integer.parseInt( dataRoomId ),
                                currentBedName, dataDate, elem, allocations, guestComments );
                    }
                }
                if ( div.hasChildNodes() == false ) {
                    LOGGER.debug( "no records for " + dataDate );
                }
            }
        }

        // batch update
        if ( allocations.size() > 0 ) {
            dao.insertAllocations( allocations );
            dao.updateGuestCommentsForReservations( guestComments );
        }
    }

    /**
     * Builds an allocation object from the given span element and inserts it into the db.
     * 
     * @param jobId job we are currently running
     * @param dataRoomTypeId the unique id for the room <i>type</i> (required)
     * @param dataRoomId room id (optional)
     * @param currentBedName the bed name for the allocation (required)
     * @param dataDate the data date for the record we are currently processing
     * @param span the span element containing the allocation details
     * @param allocations container to add allocations to insert
     * @param comments container to add guest comments to insert/update
     */
    private void collectAllocationsFromSpan( int jobId, int dataRoomTypeId,
            Integer dataRoomId, String currentBedName, String dataDate, DomElement span, 
            List<Allocation> allocations, List<GuestCommentReportEntry> comments ) {

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
        LOGGER.debug( "  data-status: " + span.getAttribute( "data-status" ) );
        LOGGER.debug( "  data-rate_plan_name: " + span.getAttribute( "data-rate_plan_name" ) );
        LOGGER.debug( "  data-payment_status: " + span.getAttribute( "data-payment_status" ) );
        LOGGER.debug( "  data-description: " + span.getAttribute( "data-description" ) );
        LOGGER.debug( "  data-href: " + span.getAttribute( "data-href" ) );
        LOGGER.debug( "  data-notes: " + span.getAttribute( "data-notes" ) );
        LOGGER.debug( "  data-guest_name: " + span.getAttribute( "data-guest_name" ) );

        Allocation alloc = new Allocation();
        alloc.setJobId( jobId );
        alloc.setRoomId( dataRoomId == null ? null : String.valueOf( dataRoomId ) );
        alloc.setRoomTypeId( dataRoomTypeId );
        BedAssignment bedAssignment = roomBedMatcher.parse( currentBedName );
        alloc.setRoom( bedAssignment.getRoom() );
        alloc.setBedName( bedAssignment.getBedName() );
        setCheckInOutDates( alloc, dataDate, span );

        // check for "room closures"
        if ( StringUtils.contains( span.getAttribute( "class" ), "room_closure" ) ) {
            alloc.setNotes( span.getAttribute( "data-description" ) );

            // confusingly, room closures have their end-dates inclusive rather than exclusive for reservations
            // so we need to add a day to the "checkout date"
            Calendar checkoutDate = Calendar.getInstance();
            checkoutDate.setTime( alloc.getCheckoutDate() );
            checkoutDate.add( Calendar.DATE, 1 );
            alloc.setCheckoutDate( checkoutDate.getTime() );
        }
        else {
            alloc.setStatus( span.getAttribute( "data-status" ) );
            alloc.setReservationId( Integer.parseInt( span.getAttribute( "data-reservation_id" ) ) );
            alloc.setGuestName( span.getAttribute( "data-guest_name" ) );
            alloc.setPaymentTotal( StringUtils.replaceChars( span.getAttribute( "data-reservation_payment_total" ), POUND + ",", "" ) );
            alloc.setPaymentOutstanding( StringUtils.replaceChars( span.getAttribute( "data-reservation_payment_oustanding" ), POUND + ",", "" ) );
            alloc.setRatePlanName( span.getAttribute( "data-rate_plan_name" ) );
            alloc.setPaymentStatus( span.getAttribute( "data-payment_status" ) );
            alloc.setNumberGuests( calculateNumberOfGuests( span ) );
            alloc.setBookingSource( span.getAttribute( "data-channel_name" ) );
            alloc.setBookingReference( span.getAttribute( "data-booking_reference" ) );
            alloc.setEta( span.getAttribute( "data-arrival_time" ) );
            alloc.setNotes( StringUtils.trimToNull( span.getAttribute( "data-notes" ) ) );
        }

        alloc.setDataHref( span.getAttribute( "data-href" ) );
        alloc.setViewed( true ); // this attribute doesn't appear to be present anymore in LH

        LOGGER.info( "Done allocation " + alloc.getReservationId() + ": " + alloc.getGuestName() );
        LOGGER.debug( alloc.toString() );
        allocations.add( alloc );

        comments.add( new GuestCommentReportEntry(
                alloc.getReservationId(),
                StringUtils.trimToNull( span.getAttribute( "data-guest_comments" ) ) ) );
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
     * Throws UnrecoverableFault on parse exception.
     * 
     * @param alloc object to update
     * @param dataDate this is the date in the html table we are currently processing, in format
     *            yyyy-MM-dd
     * @param reservationSpan HTML span of the reservation within the calendar page
     */
    private void setCheckInOutDates( Allocation alloc, String dataDate, DomElement reservationSpan ) {

        try {
            // if the start date is defined, then use that. otherwise use the date passed in.
            // usually the start date only appears if the record appears off-screen
            Calendar checkinDate = Calendar.getInstance();
            String checkinDateStr = StringUtils.trimToNull( reservationSpan.getAttribute( "data-start-date" ) );
            LOGGER.debug( "checkin-date: " + checkinDateStr );
            checkinDate.setTime( DATE_FORMAT_YYYY_MM_DD.parse(
                    checkinDateStr != null ? checkinDateStr : dataDate ) );
            alloc.setCheckinDate( checkinDate.getTime() );

            // adjust checkout date by number of nights
            Calendar checkoutDate = Calendar.getInstance();
            String checkoutDateStr = StringUtils.trimToNull( reservationSpan.getAttribute( "data-end-date" ) );
            LOGGER.debug( "checkout-date: " + checkoutDateStr );
            checkoutDate.setTime( DATE_FORMAT_YYYY_MM_DD.parse( checkoutDateStr ) );
            alloc.setCheckoutDate( checkoutDate.getTime() );
        }
        catch ( ParseException e ) {
            // can't do anything if the date isn't formatted correctly
            throw new UnrecoverableFault( e );
        }
    }

}
