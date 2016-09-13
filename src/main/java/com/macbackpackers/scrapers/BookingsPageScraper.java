
package com.macbackpackers.scrapers;

import java.io.File;
import java.io.IOException;
import java.math.BigInteger;
import java.text.ParseException;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.time.FastDateFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import com.gargoylesoftware.htmlunit.WebClient;
import com.gargoylesoftware.htmlunit.html.DomElement;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import com.gargoylesoftware.htmlunit.html.HtmlSpan;
import com.macbackpackers.beans.Allocation;
import com.macbackpackers.beans.AllocationList;
import com.macbackpackers.beans.Job;
import com.macbackpackers.beans.JobStatus;
import com.macbackpackers.dao.WordPressDAO;
import com.macbackpackers.jobs.ConfirmDepositAmountsJob;
import com.macbackpackers.services.AuthenticationService;
import com.macbackpackers.services.FileService;

/**
 * Scrapes the bookings page
 *
 */
@Component
@Scope( "prototype" )
public class BookingsPageScraper {

    private final Logger LOGGER = LoggerFactory.getLogger( getClass() );

    public static final FastDateFormat DATE_FORMAT_YYYY_MM_DD = FastDateFormat.getInstance( "yyyy-MM-dd" );
    public static final FastDateFormat DATE_FORMAT_DD_MON_YYYY = FastDateFormat.getInstance( "dd MMM yyyy" );
    public static final FastDateFormat DATE_FORMAT_BOOKING_URL = FastDateFormat.getInstance( "dd+MMM+yyyy" );

    @Autowired
    @Qualifier( "webClient" )
    private WebClient webClient;

    @Autowired
    private AuthenticationService authService;

    @Autowired
    private FileService fileService;

    @Autowired
    private WordPressDAO wordPressDAO;

    @Value( "${lilhotelier.url.bookings}" )
    private String bookingUrl;

    @Value( "${lilhotelier.url.reservation}" )
    private String bookingReservationUrl;

    /**
     * Goes to the booking page showing arrivals for the given date.
     * 
     * @param date date to query
     * @return URL of bookings arriving on this date
     * @throws IOException if credentials could not be loaded
     */
    public HtmlPage goToBookingPageForArrivals( Date date ) throws IOException {
        return goToBookingPageForArrivals( date, null, null );
    }

    /**
     * Goes to the booking page showing arrivals for the given date and booking reference.
     * 
     * @param date date to query
     * @param bookingRef booking reference (optional)
     * @param status status of reservation (optional)
     * @return URL of bookings arriving on this date
     * @throws IOException if credentials could not be loaded
     */
    public HtmlPage goToBookingPageForArrivals( Date date, String bookingRef, String status ) throws IOException {
        String pageURL = getBookingsURLForArrivalsByDate( date, date, bookingRef, status );
        LOGGER.info( "Loading bookings page: " + pageURL );
        return authService.goToPage( pageURL, webClient );
    }

    /**
     * Goes to the booking page showing arrivals for the given dates and booking reference.
     * 
     * @param fromDate date to start query
     * @param toDate date to query to
     * @param bookingRef booking reference (optional)
     * @param status status of reservation (optional)
     * @return URL of bookings arriving on this date
     * @throws IOException if credentials could not be loaded
     */
    public HtmlPage goToBookingPageForArrivals( Date fromDate, Date toDate, String bookingRef, String status ) throws IOException {
        String pageURL = getBookingsURLForArrivalsByDate( fromDate, toDate, bookingRef, status );
        LOGGER.info( "Loading bookings page: " + pageURL );
        HtmlPage nextPage = authService.goToPage( pageURL, webClient );
        return nextPage;
    }

    /**
     * Goes to the booking page showing reservations that have been booked on the given date with a
     * matching booking ref.
     * 
     * @param date date to query
     * @param bookingRefId a matching booking ref; can be "HWL" or "HBK" for example
     * @return URL of bookings booked on this date
     * @throws IOException if credentials could not be loaded
     */
    public HtmlPage goToBookingPageBookedOn( Date date, String bookingRefId ) throws IOException {
        String pageURL = getBookingsURLForBookedOnDate( date, bookingRefId, null );
        LOGGER.info( "Loading bookings page: " + pageURL );
        HtmlPage nextPage = authService.goToPage( pageURL, webClient );
        return nextPage;
    }

    /**
     * Returns the booking URL for all checkins occurring on the given date.
     * 
     * @param fromDate date to start querying from
     * @param toDate date to query to
     * @param bookingRef booking reference (optional)
     * @param status status of reservation (optional)
     * @return URL of bookings arriving on this date
     */
    private String getBookingsURLForArrivalsByDate( Date fromDate, Date toDate, String bookingRef, String status ) {
        return bookingUrl
                .replaceAll( "__DATE_FROM__", DATE_FORMAT_BOOKING_URL.format( fromDate ) )
                .replaceAll( "__DATE_TO__", DATE_FORMAT_BOOKING_URL.format( toDate ) )
                .replaceAll( "__BOOKING_REF_ID__", bookingRef == null ? "" : bookingRef )
                .replaceAll( "__DATE_TYPE__", "CheckIn" )
                .replaceAll( "__STATUS__", status == null ? "" : status );
    }

    /**
     * Returns the booking URL for all checkins occurring on the given date.
     * 
     * @param date date to query
     * @param bookingRefId a matching booking ref; can be "HWL" or "HBK" for example
     * @param status status of reservation (optional)
     * @return URL of bookings booked on on this date
     */
    private String getBookingsURLForBookedOnDate( Date date, String bookingRefId, String status ) {
        return bookingUrl
                .replaceAll( "__DATE_FROM__", DATE_FORMAT_BOOKING_URL.format( date ) )
                .replaceAll( "__DATE_TO__", DATE_FORMAT_BOOKING_URL.format( date ) )
                .replaceAll( "__BOOKING_REF_ID__", bookingRefId == null ? "" : bookingRefId )
                .replaceAll( "__DATE_TYPE__", "BookedOn" )
                .replaceAll( "__STATUS__", status == null ? "" : status );
    }

    /**
     * Returns the booking reservation detail URL for the given reservation ID.
     * 
     * @param reservationId LH reservation ID
     * @return URL for detailed booking record
     */
    private String getBookingReservationURL( String reservationId ) {
        return bookingReservationUrl.replaceAll( "__RESERVATION_ID__", reservationId );
    }

    /**
     * Returns a unique filename for a given date to use when serilaising/deserialising page data
     * when scraping.
     * 
     * @param date date of calendar page
     * @return filename of calendar page on the given date
     */
    private static String getBookingPageSerialisedObjectFilename( Date date ) {
        String dateAsString = DATE_FORMAT_YYYY_MM_DD.format( date );
        return "bookings_page_" + dateAsString + ".ser";
    }

    /**
     * Updates the extended attributes on the existing allocation records for the given jobId.
     * 
     * @param jobId ID of job to update
     * @param bookingsPage the current bookings page to parse data from
     */
    private void updateBookings( int jobId, HtmlPage bookingsPage ) {
        for ( DomElement tr : bookingsPage.getElementsByTagName( "tr" ) ) {
            try {
                String dataId = tr.getAttribute( "data-id" );
                String styleClass = tr.getAttribute( "class" ); // read or unread

                if ( StringUtils.isNotBlank( dataId ) ) {
                    LOGGER.debug( "Found record " + dataId + " " + styleClass );

                    // attempt to load the existing record if possible
                    AllocationList allocList = wordPressDAO.queryAllocationsByJobIdAndReservationId(
                            jobId, Integer.parseInt( dataId ) );
                    if ( allocList.isEmpty() ) {
                        // this may happen if there are additional records in the bookings page
                        // that weren't in our list of allocations
                        LOGGER.debug( "No allocation record found for reservation_id " + dataId + " and job " + jobId );
                        continue;
                    }

                    // Because some reservations have multiple allocations
                    // we'll be updating *all* records for that booking in this method.
                    // The updateAllocationList() call below will update *all* matching records
                    // by reservation_id and job_id

                    // may want to improve this later if i split out the table so it's not flattened
                    DomElement td = tr.getFirstElementChild();

                    // viewed_yn
                    List<String> classAttrs = Arrays.<String> asList( tr.getAttribute( "class" ).split( "\\s" ) );
                    if ( classAttrs.contains( "read" ) ) {
                        allocList.setViewed( true );
                    }
                    else {
                        allocList.setViewed( false );
                    }

                    // status       
                    DomElement span = td.getFirstElementChild();
                    if ( span != null ) {
                        LOGGER.debug( "  status: " + span.getAttribute( "class" ) );
                        allocList.setStatus( span.getAttribute( "class" ) );
                    }
                    else {
                        LOGGER.warn( "No span found in status? " );
                    }

                    // existing record should already have the guest name(s)
                    td = td.getNextElementSibling();
                    LOGGER.debug( "  name: " + StringUtils.trim( td.getTextContent() ) );

                    td = td.getNextElementSibling();
                    allocList.setBookingReference( StringUtils.trim( td.getTextContent() ) );
                    LOGGER.debug( "  booking_reference: " + StringUtils.trim( td.getTextContent() ) );

                    td = td.getNextElementSibling();
                    allocList.setBookingSource( StringUtils.trim( td.getTextContent() ) );
                    LOGGER.debug( "  booking_source: " + StringUtils.trim( td.getTextContent() ) );

                    td = td.getNextElementSibling();
                    LOGGER.debug( "  guests: " + StringUtils.trim( td.getTextContent() ) );

                    // existing record should have checkin/checkout dates
                    td = td.getNextElementSibling();
                    LOGGER.debug( "  check in: " + StringUtils.trim( td.getTextContent() ) );

                    td = td.getNextElementSibling();
                    LOGGER.debug( "  check out: " + StringUtils.trim( td.getTextContent() ) );

                    td = td.getNextElementSibling();
                    allocList.setBookedDate( StringUtils.trim( td.getTextContent() ) );
                    LOGGER.debug( "  booked on: " + StringUtils.trim( td.getTextContent() ) );

                    td = td.getNextElementSibling();
                    LOGGER.debug( "  total: " + StringUtils.trim( td.getTextContent() ) );

                    td = td.getNextElementSibling();
                    allocList.setEta( StringUtils.trim( td.getTextContent() ) );
                    LOGGER.debug( "  ETA: " + StringUtils.trim( td.getTextContent() ) );

                    td = td.getNextElementSibling();
                    LOGGER.debug( "  number of 'rooms': " + StringUtils.trim( td.getTextContent() ) );

                    // write the updated attributes to datastore
                    wordPressDAO.updateAllocationList( allocList );

                } // data-id isNotBlank

            }
            catch ( Exception ex ) {
                LOGGER.error( "Exception handled.", ex );
            }
        }
    }

    /**
     * Inserts new allocation records from the given page for the given jobId.
     * 
     * @param jobId ID of job to update
     * @param bookingsPage the current bookings page to parse data from
     */
    private void insertBookings( int jobId, HtmlPage bookingsPage ) {

        for ( DomElement tr : bookingsPage.getElementsByTagName( "tr" ) ) {
            try {
                String dataId = tr.getAttribute( "data-id" );
                String styleClass = tr.getAttribute( "class" ); // read or unread

                if ( StringUtils.isNotBlank( dataId ) ) {
                    LOGGER.debug( "Found record " + dataId + " " + styleClass );

                    Allocation alloc = new Allocation();
                    alloc.setJobId( jobId );
                    alloc.setDataHref( "/extranet/properties/533/reservations/" + dataId + "/edit" );
                    alloc.setReservationId( Integer.parseInt( dataId ) );

                    // may want to improve this later if i split out the table so it's not flattened
                    DomElement td = tr.getFirstElementChild();

                    // viewed_yn
                    List<String> classAttrs = Arrays.<String> asList( tr.getAttribute( "class" ).split( "\\s" ) );
                    if ( classAttrs.contains( "read" ) ) {
                        alloc.setViewed( true );
                    }
                    else {
                        alloc.setViewed( false );
                    }

                    // status       
                    DomElement span = td.getFirstElementChild();
                    if ( span != null ) {
                        LOGGER.debug( "  status: " + span.getAttribute( "class" ) );
                        alloc.setStatus( span.getAttribute( "class" ) );
                    }
                    else {
                        LOGGER.warn( "No span found in status? " );
                    }

                    // existing record should already have the guest name(s)
                    td = td.getNextElementSibling();
                    span = td.getFirstElementChild();
                    if ( span != null ) {
                        alloc.setGuestName( StringUtils.trim( span.getTextContent() ) );
                    }
                    else {
                        LOGGER.warn( "No span found for guest name? " );
                    }
                    LOGGER.debug( "  name: " + alloc.getGuestName() );

                    td = td.getNextElementSibling();
                    alloc.setBookingReference( StringUtils.trim( td.getTextContent() ) );
                    LOGGER.debug( "  booking_reference: " + alloc.getBookingReference() );

                    td = td.getNextElementSibling();
                    span = td.getFirstElementChild();
                    if ( span != null ) {
                        alloc.setBookingSource( StringUtils.trim( span.getTextContent() ) );
                    }
                    else {
                        LOGGER.warn( "No span found for booking source? " );
                    }
                    LOGGER.debug( "  booking_source: " + alloc.getBookingSource() );

                    td = td.getNextElementSibling();
                    LOGGER.debug( "  guests: " + StringUtils.trim( td.getTextContent() ) );
                    alloc.setNumberGuests( 0 );
                    for ( String persons : td.getTextContent().split( "/" ) ) {
                        alloc.setNumberGuests( alloc.getNumberGuests() + Integer.parseInt( persons.trim() ) );
                    }
                    LOGGER.debug( "  number of guests: " + alloc.getNumberGuests() );

                    td = td.getNextElementSibling();
                    LOGGER.debug( "  check in: " + StringUtils.trim( td.getTextContent() ) );

                    td = td.getNextElementSibling();
                    LOGGER.debug( "  check out: " + StringUtils.trim( td.getTextContent() ) );

                    td = td.getNextElementSibling();
                    alloc.setBookedDate( StringUtils.trim( td.getTextContent() ) );
                    LOGGER.debug( "  booked on: " + StringUtils.trim( td.getTextContent() ) );

                    td = td.getNextElementSibling();
                    LOGGER.debug( "  total: " + StringUtils.trim( td.getTextContent() ) );

                    td = td.getNextElementSibling();
                    alloc.setEta( StringUtils.trim( td.getTextContent() ) );
                    LOGGER.debug( "  ETA: " + StringUtils.trim( td.getTextContent() ) );

                    td = td.getNextElementSibling();
                    LOGGER.debug( "  number of 'rooms': " + StringUtils.trim( td.getTextContent() ) );
                    
                    // click on booking to get booking details
                    HtmlPage bookingPage = HtmlSpan.class.cast( span ).click();
                    webClient.waitForBackgroundJavaScript( 30000 );
                    updateAllocationFromBookingDetailsPage( alloc, bookingPage );
                    
                    // go back to the summary page
                    webClient.getWebWindows().get(0).getHistory().back();

                    // insert the allocation to datastore
                    wordPressDAO.insertAllocation( alloc );
 
                } // data-id isNotBlank

            }
            catch ( Exception ex ) {
                LOGGER.error( "Exception handled.", ex );
            }
        }
    }

    /**
     * Updates additional fields in the booking reservation (details) page.
     * 
     * @param alloc allocation to update
     * @param bookingPage detailed reservation page
     * @throws ParseException on checkin/checkout date parse error
     */
    private void updateAllocationFromBookingDetailsPage( Allocation alloc, HtmlPage bookingPage ) throws ParseException {
        
        alloc.setCheckinDate( DATE_FORMAT_YYYY_MM_DD.parse(
                bookingPage.getElementById( "reservation_check_in_date" ).getAttribute( "value" ) ) );
        alloc.setCheckoutDate( DATE_FORMAT_YYYY_MM_DD.parse(
                bookingPage.getElementById( "reservation_check_out_date" ).getAttribute( "value" ) ) );
        LOGGER.debug( "  Checkin Date: " + alloc.getCheckinDate() );
        LOGGER.debug( "  Checkout Date: " + alloc.getCheckoutDate() );

        // set the room type
        DomElement roomSelect = bookingPage.getElementById( "reservation_reservation_room_types__room_type_id" );
        for ( DomElement roomOption : roomSelect.getElementsByTagName( "option" ) ) {
            if ( StringUtils.isNotBlank( roomOption.getAttribute( "selected" ) ) ) {
                alloc.setRoomTypeId( Integer.parseInt( roomOption.getAttribute( "value" ) ) );
                LOGGER.debug( "  Room Type: " + alloc.getRoomTypeId() );
                break;
            }
        }

        // set the rate plan if applicable
        DomElement ratePlanSelect = bookingPage.getElementById( "reservation_reservation_room_types__rate_plan_id" );
        for ( DomElement ratePlanOption : ratePlanSelect.getElementsByTagName( "option" ) ) {
            if ( StringUtils.isNotBlank( ratePlanOption.getAttribute( "selected" ) ) ) {
                alloc.setRatePlanName( StringUtils.trim( ratePlanOption.getTextContent() ) );
                LOGGER.debug( "  Rate Plan: " + alloc.getRatePlanName() );
                break;
            }
        }

        // set the room assignment if applicable
        roomSelect = bookingPage.getElementById( "reservation_reservation_room_types__room_id" );
        if ( StringUtils.isNotBlank( roomSelect.getAttribute( "disabled" ) ) ) {
            alloc.setRoom( "Unallocated" ); // not set if disabled 
            LOGGER.debug( "  Room: " + alloc.getRoom() );
        }
        else {
            for ( DomElement roomOption : roomSelect.getElementsByTagName( "option" ) ) {
                if ( StringUtils.isNotBlank( roomOption.getAttribute( "selected" ) ) ) {
                    alloc.setRoomId( Integer.parseInt( roomOption.getAttribute( "value" ) ) );
                    alloc.setRoom( StringUtils.trim( roomOption.getTextContent() ) );
                    LOGGER.debug( "  Room Id: " + alloc.getRoomId() );
                    LOGGER.debug( "  Room: " + alloc.getRoom() );
                    break;
                }
            }
        }

        alloc.setNotes( StringUtils.trimToNull( bookingPage.getElementById( "reservation_notes" ).getTextContent() ) );
        LOGGER.debug( "  Notes: " + alloc.getNotes() );

        // set totals
        HtmlSpan amountSpan = bookingPage.getFirstByXPath("//div[label='Total']/span");
        alloc.setPaymentTotal( StringUtils.trim( amountSpan.getTextContent() ) );
        LOGGER.debug( "  Total: " + alloc.getPaymentTotal() );

        amountSpan = bookingPage.getFirstByXPath("//div[label='Total Outstanding']/span");
        alloc.setPaymentOutstanding( StringUtils.trim( amountSpan.getTextContent() ) );
        LOGGER.debug( "  Total Outstanding: " + alloc.getPaymentOutstanding() );
    }

    /**
     * For the booking page in question, find any where the amount payable is equal to the total
     * payable and create a job to confirm the deposit amount on the booking page.
     * 
     * @param bookingsPage the current bookings page to parse data from
     */
    public void createConfirmDepositJobs( HtmlPage bookingsPage ) {

        for ( DomElement tr : bookingsPage.getElementsByTagName( "tr" ) ) {
            try {
                String dataId = tr.getAttribute( "data-id" );
                String styleClass = tr.getAttribute( "class" ); // read or unread

                // only look at the "unread" records ... any ones that are read
                // will be picked up the allocation scraper job run daily
                if ( StringUtils.isNotBlank( dataId ) 
                        && Arrays.asList( styleClass.split( "\\s" )).contains( "unread" ) ) {
                    LOGGER.info( "Creating ConfirmDepositAmountsJob for reservation id " + dataId );
                    Job tickDepositJob = new ConfirmDepositAmountsJob();
                    tickDepositJob.setStatus( JobStatus.submitted );
                    tickDepositJob.setParameter( "reservation_id", dataId );
                    wordPressDAO.insertJob( tickDepositJob );
                } // data-id isNotBlank

            }
            catch ( Exception ex ) {
                LOGGER.error( "Exception handled.", ex );
            }
        }
    }

    /**
     * Updates the calendar records by querying the bookings page between the given dates
     * (inclusive) for the given job.
     * 
     * @param jobId the job ID to associate with this dump
     * @param startDate the start date to check allocations for (inclusive)
     * @param endDate the end date in which to include allocations for
     * @param useSerialisedDataIfAvailable check if we've already seen this page already and used
     *            the cached version if available.
     * @throws IOException on read/write error
     */
//    @Transactional
    public void updateBookingsBetween(
            int jobId, Date startDate, Date endDate, boolean useSerialisedDataIfAvailable ) throws IOException {

        Calendar currentDate = Calendar.getInstance();
        currentDate.setTime( startDate );

        while ( currentDate.getTime().compareTo( endDate ) <= 0 ) {
            HtmlPage bookingsPage;
            String serialisedFileName = getBookingPageSerialisedObjectFilename( currentDate.getTime() );
            if ( useSerialisedDataIfAvailable
                    && new File( serialisedFileName ).exists() ) {
                bookingsPage = fileService.loadPageFromDisk( serialisedFileName );
            }
            else {
                // this may take a few minutes...
                bookingsPage = goToBookingPageForArrivals( currentDate.getTime() );
            }

            updateBookings( jobId, bookingsPage );
            currentDate.add( Calendar.DATE, 1 ); // keep going to the next day
        }
    }

    /**
     * Updates the calendar records using by querying the bookings page for the given date and job.
     * 
     * @param jobId the job ID to associate with this dump
     * @param arrivalDate the checkin-date to search on
     * @param bookingRef (optional) booking reference (to narrow down the subset of records)
     * @throws IOException on read/write error
     */
//    @Transactional
    public void updateBookingsFor( int jobId, Date arrivalDate, String bookingRef ) throws IOException {
        HtmlPage bookingsPage = goToBookingPageForArrivals( arrivalDate, bookingRef, null );
        updateBookings( jobId, bookingsPage );
    }

    /**
     * Inserts the calendar records by querying the bookings page for the given date.
     * 
     * @param jobId the job ID to associate with this dump
     * @param fromDate the date to start searching from
     * @param toDate the end date to search to
     * @param bookingRef (optional) booking reference (to narrow down the subset of records)
     * @throws IOException on read/write error
     */
//    @Transactional
    public void insertCancelledBookingsFor( int jobId, Date fromDate, Date toDate, String bookingRef ) throws IOException {
        HtmlPage bookingsPage = goToBookingPageForArrivals( fromDate, toDate, bookingRef, "cancelled" );
        insertBookings( jobId, bookingsPage );
    }

    /**
     * Scrapes the guest comments field for the given reservation ID.
     * 
     * @param reservationId the reservation ID to scrape
     * @return the contents of the guest comments field
     * @throws IOException on read/write error
     */
    public String getGuestCommentsForReservation( BigInteger reservationId ) throws IOException {
        HtmlPage bookingPage = authService.goToPage( getBookingReservationURL(
                String.valueOf( reservationId ) ), webClient );
        DomElement elem = bookingPage.getElementById( "reservation_guest_comments" );
        return elem == null ? null : StringUtils.trimToNull( elem.getTextContent() );
    }
}
