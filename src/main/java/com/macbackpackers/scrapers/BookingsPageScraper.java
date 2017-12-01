
package com.macbackpackers.scrapers;

import java.io.IOException;
import java.io.StringReader;
import java.lang.reflect.InvocationTargetException;
import java.math.BigDecimal;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import org.apache.commons.beanutils.PropertyUtils;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.time.FastDateFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import com.gargoylesoftware.htmlunit.TextPage;
import com.gargoylesoftware.htmlunit.WebClient;
import com.gargoylesoftware.htmlunit.html.DomElement;
import com.gargoylesoftware.htmlunit.html.HtmlAnchor;
import com.gargoylesoftware.htmlunit.html.HtmlOption;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import com.gargoylesoftware.htmlunit.html.HtmlSelect;
import com.gargoylesoftware.htmlunit.html.HtmlSpan;
import com.gargoylesoftware.htmlunit.html.HtmlTableCell;
import com.gargoylesoftware.htmlunit.html.HtmlTableDataCell;
import com.gargoylesoftware.htmlunit.html.HtmlTableRow;
import com.macbackpackers.beans.Allocation;
import com.macbackpackers.beans.AllocationList;
import com.macbackpackers.beans.BookingByCheckinDate;
import com.macbackpackers.beans.JobStatus;
import com.macbackpackers.beans.UnpaidDepositReportEntry;
import com.macbackpackers.dao.WordPressDAO;
import com.macbackpackers.exceptions.UnrecoverableFault;
import com.macbackpackers.jobs.ConfirmDepositAmountsJob;
import com.macbackpackers.scrapers.matchers.BedAssignment;
import com.macbackpackers.scrapers.matchers.RoomBedMatcher;
import com.macbackpackers.services.AuthenticationService;

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
    private AuthenticationService authService;

    @Autowired
    private WordPressDAO wordPressDAO;

    @Value( "${lilhotelier.propertyid}" )
    private String lhPropertyId;

    @Autowired
    private ApplicationContext context;

    @Autowired
    private RoomBedMatcher roomBedMatcher;

    /**
     * Returns the base URL for a bookings search.
     * 
     * @return booking URL
     */
    private String getBookingURL() {
        return "https://app.littlehotelier.com/extranet/properties/" + lhPropertyId
                + "/reservations?utf8=%E2%9C%93&reservation_filter%5Bguest_last_name%5D=&reservation_filter%5Bbooking_reference_id%5D=__BOOKING_REF_ID__&reservation_filter%5Bdate_type%5D=__DATE_TYPE__&reservation_filter%5Bdate_from%5D=__DATE_FROM__&reservation_filter%5Bdate_to%5D=__DATE_TO__&reservation_filter%5Bstatus%5D=__STATUS__&commit=Search";
    }

    /**
     * Returns the base URL for looking up a single reservation.
     * 
     * @return reservation URL
     */
    private String getReservationURL() {
        return "https://app.littlehotelier.com/extranet/properties/" + lhPropertyId
                + "/reservations/__RESERVATION_ID__/edit";
    }

    /**
     * Goes to the booking page showing arrivals for the given date.
     * 
     * @param webClient web client to use
     * @param date date to query
     * @return URL of bookings arriving on this date
     * @throws IOException if credentials could not be loaded
     */
    private HtmlPage goToBookingPageForArrivals( WebClient webClient, Date date ) throws IOException {
        return goToBookingPageForArrivals( webClient, date, null, null );
    }

    /**
     * Goes to the booking page showing arrivals for the given date and booking reference.
     * 
     * @param webClient web client to use
     * @param date date to query
     * @param bookingRef booking reference (optional)
     * @param status status of reservation (optional)
     * @return URL of bookings arriving on this date
     * @throws IOException if credentials could not be loaded
     */
    public HtmlPage goToBookingPageForArrivals( WebClient webClient, Date date, String bookingRef, String status ) throws IOException {
        String pageURL = getBookingsURLForArrivalsByDate( date, date, bookingRef, status );
        LOGGER.info( "Loading bookings page: " + pageURL );
        return authService.goToPage( pageURL, webClient );
    }

    /**
     * Goes to the booking page showing arrivals for the given dates and booking reference.
     * 
     * @param webClient web client to use
     * @param fromDate date to start query
     * @param toDate date to query to
     * @param bookingRef booking reference (optional)
     * @param status status of reservation (optional)
     * @return URL of bookings arriving on this date
     * @throws IOException if credentials could not be loaded
     */
    public HtmlPage goToBookingPageForArrivals( WebClient webClient, Date fromDate, Date toDate, String bookingRef, String status ) throws IOException {
        String pageURL = getBookingsURLForArrivalsByDate( fromDate, toDate, bookingRef, status );
        LOGGER.info( "Loading bookings page: " + pageURL );
        HtmlPage nextPage = authService.goToPage( pageURL, webClient );
        return nextPage;
    }

    /**
     * Goes to the booking page showing reservations that have been booked on the given date with a
     * matching booking ref.
     * 
     * @param webClient web client to use
     * @param date date to query
     * @param bookingRef a matching booking ref; can be "HWL" or "HBK" for example
     * @return URL of bookings booked on this date
     * @throws IOException if credentials could not be loaded
     */
    public HtmlPage goToBookingPageBookedOn( WebClient webClient, Date date, String bookingRef ) throws IOException {
        String pageURL = getBookingsURLForBookedOnDate( date, date, bookingRef, null );
        LOGGER.info( "Loading bookings page: " + pageURL );
        HtmlPage nextPage = authService.goToPage( pageURL, webClient );
        return nextPage;
    }

    /**
     * Goes to the booking page showing reservations for arrivals on the given date with a
     * matching booking ref.
     * 
     * @param webClient web client to use
     * @param date date to query
     * @param bookingRef a matching booking ref; can be "HWL" or "HBK" for example
     * @return URL of bookings booked on this date
     * @throws IOException if credentials could not be loaded
     */
    public HtmlPage goToBookingPageArrivedOn( WebClient webClient, Date date, String bookingRef ) throws IOException {
        String pageURL = getBookingsURLForArrivalsByDate( date, date, bookingRef, null );
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
        return getBookingURL()
                .replaceAll( "__DATE_FROM__", DATE_FORMAT_BOOKING_URL.format( fromDate ) )
                .replaceAll( "__DATE_TO__", DATE_FORMAT_BOOKING_URL.format( toDate ) )
                .replaceAll( "__BOOKING_REF_ID__", bookingRef == null ? "" : bookingRef )
                .replaceAll( "__DATE_TYPE__", "CheckIn" )
                .replaceAll( "__STATUS__", status == null ? "" : status );
    }

    /**
     * Returns the booking URL for all checkins occurring on the given date.
     * 
     * @param fromDate date to query from
     * @param toDate date to query to
     * @param bookingRefId a matching booking ref; can be "HWL" or "HBK" for example
     * @param status status of reservation (optional)
     * @return URL of bookings booked on on this date
     */
    private String getBookingsURLForBookedOnDate( Date fromDate, Date toDate, String bookingRefId, String status ) {
        return getBookingURL()
                .replaceAll( "__DATE_FROM__", DATE_FORMAT_BOOKING_URL.format( fromDate ) )
                .replaceAll( "__DATE_TO__", DATE_FORMAT_BOOKING_URL.format( toDate ) )
                .replaceAll( "__BOOKING_REF_ID__", bookingRefId == null ? "" : bookingRefId )
                .replaceAll( "__DATE_TYPE__", "BookedOn" )
                .replaceAll( "__STATUS__", status == null ? "" : status );
    }

    /**
     * Updates the extended attributes on the existing allocation records for the given jobId.
     * 
     * @param jobId ID of job to update
     * @param bookingsPage the current bookings page to parse data from
     */
    private void updateBookings( int jobId, HtmlPage bookingsPage ) {
        for ( DomElement tr : bookingsPage.getElementsByTagName( "tr" ) ) {

            String styleClass = tr.getAttribute( "class" );
            if ( false == StringUtils.contains( styleClass, "reservation_room_type" ) ) {
                continue;
            }

            // status       
            DomElement td = tr.getFirstElementChild();
            LOGGER.debug( "  status: " + StringUtils.trim( td.getAttribute( "class" ) ) );

            // existing record should already have the guest name(s)
            td = td.getNextElementSibling();
            LOGGER.debug( "  name: " + StringUtils.trim( td.getTextContent() ) );

            td = td.getNextElementSibling();
            DomElement bookingAnchor = td.getFirstElementChild();
            String dataId = bookingAnchor.getAttribute( "data-reservation-id" );
            LOGGER.debug( "  booking_reference: " + StringUtils.trim( bookingAnchor.getTextContent() ) );
            LOGGER.debug( "  reservation_id: " + dataId );

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

            td = td.getNextElementSibling();
            LOGGER.debug( "  booking_source: " + StringUtils.trim( td.getTextContent() ) );

            td = td.getNextElementSibling();
            LOGGER.debug( "  guests: " + StringUtils.trim( td.getTextContent().replaceAll( "\\s+", " " ) ) );

            // existing record should have checkin/checkout dates
            td = td.getNextElementSibling();
            LOGGER.debug( "  check in: " + StringUtils.trim( td.getTextContent() ) );

            td = td.getNextElementSibling();
            LOGGER.debug( "  check out: " + StringUtils.trim( td.getTextContent() ) );

            td = td.getNextElementSibling();
            LOGGER.debug( "  booked on: " + StringUtils.trim( td.getTextContent() ) );
            allocList.setBookedDate( StringUtils.trim( td.getTextContent() ) );

            td = td.getNextElementSibling();
            LOGGER.debug( "  ETA: " + StringUtils.trim( td.getTextContent() ) );

            td = td.getNextElementSibling();
            LOGGER.debug( "  Room Name: " + StringUtils.trim( td.getTextContent().replaceAll( "\\s+", " " ) ) );

            td = td.getNextElementSibling();
            LOGGER.debug( "  Total: " + StringUtils.trim( td.getTextContent() ) );

            td = td.getNextElementSibling();
            LOGGER.debug( "  Total Outstanding: " + StringUtils.trim( td.getTextContent() ) );

            // write the updated attributes to datastore
            wordPressDAO.updateAllocationList( allocList );
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
                    alloc.setDataHref( getReservationURL()
                            .replace( "https://app.littlehotelier.com", "" )
                            .replace( "__RESERVATION_ID__", dataId ) );
                    alloc.setReservationId( Integer.parseInt( dataId ) );

                    // may want to improve this later if i split out the table so it's not flattened
                    DomElement td = tr.getFirstElementChild();

                    // viewed_yn
                    List<String> classAttrs = Arrays.<String> asList( tr.getAttribute( "class" ).split( "\\s" ) );
                    alloc.setViewed( classAttrs.contains( "read" ) );

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
                    bookingPage.getWebClient().waitForBackgroundJavaScript( 30000 );
                    updateAllocationFromBookingDetailsPage( alloc, bookingPage );
                    
                    // go back to the summary page
                    bookingPage.getWebClient().getWebWindows().get(0).getHistory().back();

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
     * @throws NoSuchMethodException on bean copy failure
     * @throws InvocationTargetException on bean copy failure
     * @throws IllegalAccessException on bean copy failure
     */
    private void updateAllocationFromBookingDetailsPage( Allocation alloc, HtmlPage bookingPage ) 
            throws ParseException, IllegalAccessException, InvocationTargetException, NoSuchMethodException {
        
        alloc.setGuestName( bookingPage.getElementById( "reservation_guest_first_name" ).getAttribute( "value" )
                + " " + bookingPage.getElementById( "reservation_guest_last_name" ).getAttribute( "value" ) );
        LOGGER.debug( "  Guest Name: " + alloc.getGuestName() );

        alloc.setCheckinDate( DATE_FORMAT_YYYY_MM_DD.parse(
                bookingPage.getElementById( "reservation_check_in_date" ).getAttribute( "value" ) ) );
        alloc.setCheckoutDate( DATE_FORMAT_YYYY_MM_DD.parse(
                bookingPage.getElementById( "reservation_check_out_date" ).getAttribute( "value" ) ) );
        LOGGER.debug( "  Checkin Date: " + alloc.getCheckinDate() );
        LOGGER.debug( "  Checkout Date: " + alloc.getCheckoutDate() );

        alloc.setNotes( StringUtils.trimToNull( bookingPage.getElementById( "notes" ).getTextContent() ) );
        LOGGER.debug( "  Notes: " + alloc.getNotes() );

        // set totals
        HtmlSpan amountSpan = bookingPage.getFirstByXPath("//div[label='Total']/span");
        alloc.setPaymentTotal( StringUtils.trim( amountSpan.getTextContent() ) );
        LOGGER.debug( "  Total: " + alloc.getPaymentTotal() );

        amountSpan = bookingPage.getFirstByXPath("//div[label='Total Outstanding']/span");
        alloc.setPaymentOutstanding( StringUtils.trim( amountSpan.getTextContent() ) );
        LOGGER.debug( "  Total Outstanding: " + alloc.getPaymentOutstanding() );
        
        // for each of the room assignments, we'll need to create a new allocation record
        final int roomAssignments = bookingPage.getByXPath( "//div[@class='reservation_room_type']").size();
        for(int i = 1; i <= roomAssignments; i++) {
            
            // first create a duplicate allocation record that we will insert
            Allocation newAllocation = new Allocation();
            PropertyUtils.copyProperties( newAllocation, alloc );
            
            // set the room type
            HtmlSelect roomSelect = bookingPage.getFirstByXPath( String.format( 
                    "//div[@class='reservation_room_type'][%d]//select[@id='reservation_reservation_room_types__room_type_id']", i ) );
            for ( HtmlOption roomOption : roomSelect.getOptions() ) {
                if ( roomOption.isSelected() ) {
                    newAllocation.setRoomTypeId( Integer.parseInt( roomOption.getValueAttribute() ) );
                    LOGGER.debug( "  Room Type: " + roomOption.getTextContent() + " (" + newAllocation.getRoomTypeId() + ")" );
                    break;
                }
            }

            // set the rate plan if applicable
            HtmlSelect ratePlanSelect = bookingPage.getFirstByXPath( String.format( 
                    "//div[@class='reservation_room_type'][%d]//select[@id='reservation_reservation_room_types__rate_plan_id']", i ) );
            for ( HtmlOption ratePlanOption : ratePlanSelect.getOptions() ) {
                if ( ratePlanOption.isSelected() ) {
                    newAllocation.setRatePlanName( StringUtils.trim( ratePlanOption.getTextContent() ) );
                    LOGGER.debug( "  Rate Plan: " + newAllocation.getRatePlanName() );
                    break;
                }
            }

            // set the room assignment if applicable
            roomSelect = bookingPage.getFirstByXPath( String.format( 
                    "//div[@class='reservation_room_type'][%d]//select[@id='reservation_reservation_room_types__room_id']", i ) );
            if ( roomSelect.isDisabled() ) {
                newAllocation.setRoom( "Unallocated" ); // not set if disabled 
                LOGGER.debug( "  Room: " + newAllocation.getRoom() );
            }
            else {
                for ( HtmlOption roomOption : roomSelect.getOptions() ) {
                    if ( roomOption.isSelected() ) {
                        newAllocation.setRoomId( Integer.parseInt( roomOption.getValueAttribute() ) );

                        BedAssignment bedAssignment = roomBedMatcher.parse( 
                                StringUtils.trim( roomOption.getTextContent() ) );
                        newAllocation.setRoom( bedAssignment.getRoom() );
                        newAllocation.setBedName( bedAssignment.getBedName() );

                        LOGGER.debug( "  Room Id: " + newAllocation.getRoomId() );
                        LOGGER.debug( "  Room: " + newAllocation.getRoom() );
                        LOGGER.debug( "  Bed Name: " + newAllocation.getBedName() );
                        break;
                    }
                }
            }

            // insert the allocation to datastore
            wordPressDAO.insertAllocation( newAllocation );
        }
    }

    /**
     * For the booking page in question, find any where the amount payable is equal to the total
     * payable and create a job to confirm the deposit amount on the booking page.
     * 
     * @param bookingsPage the current bookings page to parse data from
     */
    public void createConfirmDepositJobs( HtmlPage bookingsPage ) {

        final FastDateFormat DATE_FORMAT_DD_MM_YY = FastDateFormat.getInstance( "dd-MM-yy" );
        List<HtmlTableRow> rows = bookingsPage.getByXPath( "//tr[@class='reservation_room_type']" );
        rows.stream()
            .filter( p -> {
                HtmlTableCell totalAmtCell = p.getFirstByXPath( "td[contains(@class,'total')]" );
                HtmlTableCell outstandingAmtCell = p.getFirstByXPath( "td[contains(@class,'outstanding')]" );
                return StringUtils.equals( totalAmtCell.getTextContent(), outstandingAmtCell.getTextContent() );
            } )
            .forEach( r -> {
                HtmlTableCell bookingRefCell = r.getFirstByXPath( "td[contains(@class,'booking-reference')]" );
                HtmlTableCell checkinCell = r.getFirstByXPath( "td[contains(@class,'check_in')]" );
                HtmlAnchor bookingLink = bookingRefCell.getFirstByXPath( "a" );
                try {
                    LOGGER.info( "Creating ConfirmDepositAmountsJob for " + StringUtils.trim( bookingRefCell.getTextContent() ) );
                    ConfirmDepositAmountsJob tickDepositJob = new ConfirmDepositAmountsJob();
                    tickDepositJob.setStatus( JobStatus.submitted );
                    tickDepositJob.setBookingRef( bookingRefCell.getTextContent() );
                    tickDepositJob.setCheckinDate( DATE_FORMAT_DD_MM_YY.parse( checkinCell.getTextContent() ) );
                    tickDepositJob.setReservationId( Integer.parseInt( bookingLink.getAttribute( "data-reservation-id" ) ) );
                    wordPressDAO.insertJob( tickDepositJob );
                }
                catch ( ParseException ex ) {
                    LOGGER.error( "Unable to parse checkin date for " + bookingRefCell.getTextContent() );
                }
            } );
    }

    /**
     * Returns all HWL bookings where total outstanding = total payable.
     * 
     * @param webClient the web client to use
     * @param checkinDateFrom check in date start
     * @param checkinDateTo check in date end
     * @return non-null list of matched bookings
     * @throws IOException
     * @throws ParseException on parse error
     */
    public List<BookingByCheckinDate> getUnpaidHostelworldBookings( WebClient webClient, Date checkinDateFrom, 
            Date checkinDateTo ) throws IOException, ParseException {
        String url = getBookingURL()
                .replaceAll( "__DATE_FROM__", DATE_FORMAT_BOOKING_URL.format( checkinDateFrom ) )
                .replaceAll( "__DATE_TO__", DATE_FORMAT_BOOKING_URL.format( checkinDateTo ) )
                .replaceAll( "__BOOKING_REF_ID__", "HWL" )
                .replaceAll( "__DATE_TYPE__", "CheckIn" )
                .replaceAll( "__STATUS__", "confirmed" );
        LOGGER.info( "Retrieving CSV file from: " + url );
        HtmlPage thePage = authService.goToPage( url, webClient );
        HtmlAnchor a = thePage.getFirstByXPath( "//a[@class='export']" );
        TextPage txtPage = a.click();
        String csvContent = txtPage.getContent();
        Iterable<CSVRecord> records = CSVFormat.RFC4180
                .withFirstRecordAsHeader().parse(new StringReader(csvContent));
        
        return StreamSupport.stream( records.spliterator(), false )
                .filter( p -> {
                    // include only those records with nothing received
                    BigDecimal paymentTotal = new BigDecimal( p.get( "Payment total" ) );
                    BigDecimal paymentOutstanding = new BigDecimal( p.get( "Payment outstanding" ) );
                    return paymentTotal.equals( paymentOutstanding ) && paymentOutstanding.compareTo( BigDecimal.ZERO ) > 0;
                } )
                .map( r -> {
                    try {
                        return new BookingByCheckinDate( r.get( "Booking reference" ), 0,
                                DATE_FORMAT_YYYY_MM_DD.parse( r.get( "Check in date" ) ) );
                    }
                    catch ( ParseException pe ) {
                        throw new UnrecoverableFault( pe );
                    }
                } )
                .collect( Collectors.toList() );
    }

    /**
     * Updates the calendar records by querying the bookings page between the given dates
     * (inclusive) for the given job.
     * 
     * @param webClient web client to use
     * @param jobId the job ID to associate with this dump
     * @param startDate the start date to check allocations for (inclusive)
     * @param endDate the end date in which to include allocations for
     * @throws IOException on read/write error
     */
//    @Transactional
    public void updateBookingsBetween( WebClient webClient, int jobId, Date startDate, Date endDate ) throws IOException {

        Calendar currentDate = Calendar.getInstance();
        currentDate.setTime( startDate );

        while ( currentDate.getTime().compareTo( endDate ) <= 0 ) {
            // this may take a few minutes...
            updateBookings( jobId, goToBookingPageForArrivals( webClient, currentDate.getTime() ) );
            currentDate.add( Calendar.DATE, 1 ); // keep going to the next day
        }
    }

    /**
     * Updates the calendar records using by querying the bookings page for the given date and job.
     * 
     * @param webClient web client to use
     * @param jobId the job ID to associate with this dump
     * @param arrivalDate the checkin-date to search on
     * @param bookingRef (optional) booking reference (to narrow down the subset of records)
     * @throws IOException on read/write error
     */
//    @Transactional
    public void updateBookingsFor( WebClient webClient, int jobId, Date arrivalDate, String bookingRef ) throws IOException {
        HtmlPage bookingsPage = goToBookingPageForArrivals( webClient, arrivalDate, bookingRef, null );
        updateBookings( jobId, bookingsPage );
    }

    /**
     * Attempts to find and insert any bookings that are present in the HW scraped tables
     * for the given job id but not in the LH calendar table for the same jobId.
     * 
     * @param webClient web client to use
     * @param jobId ID of DiffBookingEnginesJob
     * @param checkinDate checkin date (of report)
     * @throws IOException on web error
     */
    public void insertMissingHWBookings( WebClient webClient, int jobId, Date checkinDate ) throws IOException {
        List<String> bookingRefs = wordPressDAO.findMissingHwBookingRefs( jobId, checkinDate );

        // search for the booking reference within an 8 month window of their expected checkin date
        Calendar startDate = Calendar.getInstance();
        startDate.setTime( checkinDate );
        startDate.add( Calendar.MONTH, -4 );

        Calendar endDate = Calendar.getInstance();
        endDate.setTime( checkinDate );
        endDate.add( Calendar.MONTH, 4 );

        // insert any missing records if found
        for ( String bookingRef : bookingRefs ) {
            HtmlPage bookingPage = goToBookingPageForArrivals( webClient, startDate.getTime(), endDate.getTime(), bookingRef, null );
            insertBookings( jobId, bookingPage );
        }
    }

    /**
     * Attempts to find and insert any bookings that are present in the HW scraped tables
     * for the given job id but not in the LH calendar table for the same jobId.
     * 
     * @param webClient web client to use
     * @param jobId ID of DiffBookingEnginesJob
     * @param checkinDateFrom checkin date start (inclusive)
     * @param checkinDateTo checkin date end (inclusive)
     * @throws IOException on web error
     * @throws ParseException on date parse error
     */
    public void insertCancelledBookings( WebClient webClient, int jobId, Date checkinDateFrom, 
            Date checkinDateTo ) throws IOException, ParseException {
        String url = getBookingURL()
                .replaceAll( "__DATE_FROM__", DATE_FORMAT_BOOKING_URL.format( checkinDateFrom ) )
                .replaceAll( "__DATE_TO__", DATE_FORMAT_BOOKING_URL.format( checkinDateTo ) )
                .replaceAll( "__BOOKING_REF_ID__", "" )
                .replaceAll( "__DATE_TYPE__", "CheckIn" )
                .replaceAll( "__STATUS__", "cancelled" );
        LOGGER.info( "Retrieving CSV file from: " + url );
        HtmlPage thePage = authService.goToPage( url, webClient );
        HtmlAnchor a = thePage.getFirstByXPath( "//a[@class='export']" );
        TextPage txtPage = a.click();
        String csvContent = txtPage.getContent();
        Iterable<CSVRecord> records = CSVFormat.RFC4180
                .withFirstRecordAsHeader().parse(new StringReader(csvContent));
        
        for (CSVRecord record : records) {
            LOGGER.info( "Inserting cancelled allocation for " + record.get( "Booking reference" ) );
            Allocation alloc = new Allocation();
            alloc.setJobId( jobId );
            alloc.setViewed( true ); // not available
            alloc.setStatus( record.get( "Status" ) );
            alloc.setGuestName( record.get( "Guest first name" ) + " " + record.get( "Guest last name" ) );
            alloc.setBookingReference( record.get( "Booking reference" ) );
            alloc.setBookingSource( record.get( "Channel name" ) );
            alloc.setNumberGuests( Integer.parseInt( record.get( "Number of adults" ) ) );
            alloc.setBookedDate( DATE_FORMAT_YYYY_MM_DD.parse( record.get( "Booked on date" ) ) );
            alloc.setEta( record.get( "Arrival time" ) );
            alloc.setCheckinDate( DATE_FORMAT_YYYY_MM_DD.parse( record.get( "Check in date" ) ) );
            alloc.setCheckoutDate( DATE_FORMAT_YYYY_MM_DD.parse( record.get( "Check out date" ) ) );
            alloc.setPaymentTotal( record.get("Payment total") );
            alloc.setPaymentOutstanding( record.get( "Payment outstanding" ) );
            alloc.setRoom( "Unallocated" ); // cannot be null 

            // we need the notes from the bookings page
            updateAllocationWithBookingDetails( alloc );

            // insert the allocation to datastore
            wordPressDAO.insertAllocation( alloc );
        }
    }

    /**
     * Updates the allocation object with the missing details from the booking details page.
     * 
     * @param alloc allocation object being updated
     * @throws IOException
     */
    private void updateAllocationWithBookingDetails( Allocation alloc ) throws IOException {

        // instantiating new web client here due to excessive memory usage
        // and resources not being released fast enough
        try (WebClient bookingClient = context.getBean( "webClient", WebClient.class )) {
            HtmlPage thePage = goToBookingPageForArrivals( bookingClient, 
                    alloc.getCheckinDate(), alloc.getCheckinDate(), 
                    alloc.getBookingReference(), alloc.getStatus() );

            for ( DomElement tr : thePage.getElementsByTagName( "tr" ) ) {
                String dataId = tr.getAttribute( "data-id" );
                if ( StringUtils.isNotBlank( dataId ) ) {
                    alloc.setReservationId( Integer.parseInt( dataId ) );
                    alloc.setDataHref( getReservationURL()
                            .replace( "https://app.littlehotelier.com", "" )
                            .replace( "__RESERVATION_ID__", dataId ) );

                    // click on booking to get booking details
                    DomElement cell = tr.getFirstElementChild();
                    HtmlPage bookingPage = HtmlTableDataCell.class.cast( cell ).click();
                    bookingPage.getWebClient().waitForBackgroundJavaScript( 30000 );
                    alloc.setNotes( StringUtils.trimToNull( 
                            thePage.getElementById( "notes" ).getTextContent() ) );
                    break; // there should only ever be one record anyways
                }
            }
        }
    }

    /**
     * Scrapes the guest comments field for the given booking.
     * 
     * @param webClient web client to use
     * @param bookingRef the booking to load
     * @param checkinDate the expected checkin date for the booking
     * @return the contents of the guest comments field
     * @throws IOException on read/write error
     */
    public String getGuestCommentsForReservation( WebClient webClient, String bookingRef, Date checkinDate ) throws IOException {
        String url = getBookingURL()
                .replaceAll( "__DATE_FROM__", DATE_FORMAT_BOOKING_URL.format( checkinDate ) )
                .replaceAll( "__DATE_TO__", DATE_FORMAT_BOOKING_URL.format( checkinDate ) )
                .replaceAll( "__BOOKING_REF_ID__", bookingRef )
                .replaceAll( "__DATE_TYPE__", "CheckIn" )
                .replaceAll( "__STATUS__", "" );
        LOGGER.info( "Retrieving CSV file from: " + url );
        HtmlPage thePage = authService.goToPage( url, webClient );
        HtmlAnchor a = thePage.getFirstByXPath( "//a[@class='export']" );
        TextPage txtPage = a.click();
        String csvContent = txtPage.getContent();
        Iterable<CSVRecord> records = CSVFormat.RFC4180
                .withFirstRecordAsHeader().parse(new StringReader(csvContent));
        
        for (CSVRecord record : records) {
            String comment = record.get("Guest comments");
            LOGGER.info( "Guest comment: " + comment );
            return StringUtils.trimToNull( comment );
        }

        throw new IllegalStateException("guest comment not found for booking " + bookingRef + " on " + checkinDate);
    }

    /**
     * Queries all reservations within the given dates where status = 'confirmed'
     * and where no payment has been made yet (payment received = 0).
     * 
     * @param webClient web client to use
     * @param bookingRefMatch matching these booking refs
     * @param dateFrom from this booking date
     * @param dateTo until this booking date (inclusive)
     * @return non-null list of matched reservations (only the following fields are populated:
     *                  guestName, checkinDate, checkoutDate, bookingRef, bookingSource, bookedDate, paymentTotal)
     * @throws IOException on read/write error
     * @throws ParseException on date parse error
     */
    public List<UnpaidDepositReportEntry> getUnpaidReservations( 
            WebClient webClient, String bookingRefMatch, Date dateFrom, Date dateTo ) throws IOException, ParseException {
        String url = getBookingURL()
                .replaceAll( "__DATE_FROM__", DATE_FORMAT_BOOKING_URL.format( dateFrom ) )
                .replaceAll( "__DATE_TO__", DATE_FORMAT_BOOKING_URL.format( dateTo ) )
                .replaceAll( "__BOOKING_REF_ID__", bookingRefMatch )
                .replaceAll( "__DATE_TYPE__", "BookedOn" )
                .replaceAll( "__STATUS__", "confirmed" );
        LOGGER.info( "Retrieving CSV file from: " + url );
        HtmlPage thePage = authService.goToPage( url, webClient );
        HtmlAnchor a = thePage.getFirstByXPath( "//a[@class='export']" );
        TextPage txtPage = a.click();
        String csvContent = txtPage.getContent();
        Iterable<CSVRecord> records = CSVFormat.RFC4180
                .withFirstRecordAsHeader().parse(new StringReader(csvContent));
        
        List<UnpaidDepositReportEntry> results = new ArrayList<UnpaidDepositReportEntry>();
        for ( CSVRecord csv : records ) {
            String bookingRef = csv.get( "Booking reference" );
            LOGGER.info( "Checking " + bookingRefMatch + " reservation: " + bookingRef );

            // check if we've already charged them
            if ( false == "0".equals( csv.get( "Payment Received" ) ) ) {
                LOGGER.info( "Already charged " + bookingRef + "; skipping..." );
                continue;
            }

            // we should already be filtered on "confirmed" but just an extra sanity check
            if ( false == "confirmed".equals( csv.get( "Status" ) ) ) {
                LOGGER.info( bookingRef + " not at confirmed; skipping..." );
                continue;
            }

            // group bookings needs approval so processed manually
            if ( Integer.parseInt( csv.get( "Number of adults" ) ) >= wordPressDAO.getGroupBookingSize() ) {
                LOGGER.info( "Booking " + bookingRef + " has "
                        + csv.get( "Number of adults" ) + " guests. Skipping..." );
                continue;
            }
            
            if ( "BDC".equals( bookingRefMatch ) && StringUtils.trimToEmpty( csv.get( "Guest comments" ) )
                    .contains( "You have received a virtual credit card for this reservation" ) ) {
                LOGGER.info( "Virtual CC; to charge on day." );
                continue;
            }

            UnpaidDepositReportEntry entry = new UnpaidDepositReportEntry();
            entry.setBookingRef( bookingRef );
            entry.setBookedDate( DATE_FORMAT_YYYY_MM_DD.parse( csv.get( "Booked on date" ) ) );
            entry.setBookingSource( csv.get( "Channel name" ) );
            entry.setGuestNames( csv.get( "Guest first name" ) + " " + csv.get( "Guest last name" ) );
            entry.setCheckinDate( DATE_FORMAT_YYYY_MM_DD.parse( csv.get( "Check in date" ) ) );
            entry.setCheckoutDate( DATE_FORMAT_YYYY_MM_DD.parse( csv.get( "Check out date" ) ) );
            entry.setPaymentTotal( new BigDecimal( csv.get( "Payment total" ) ) );
            results.add( entry );
        }
        return results;
    }

    /**
     * Returns the CSV records for all checkouts for the given date.
     * 
     * @param webClient web client to use
     * @param bookingRef (optional) match on booking reference
     * @param checkoutDate date guest is checking-out
     * @return all bookings where the guest is checked-out
     * @throws IOException on parsing error
     */
    public List<CSVRecord> getAllCheckouts( WebClient webClient, String bookingRef, Date checkoutDate ) throws IOException {
        String url = getBookingURL()
                .replaceAll( "__DATE_FROM__", DATE_FORMAT_BOOKING_URL.format( checkoutDate ) )
                .replaceAll( "__DATE_TO__", DATE_FORMAT_BOOKING_URL.format( checkoutDate ) )
                .replaceAll( "__BOOKING_REF_ID__", bookingRef == null ? "" : bookingRef )
                .replaceAll( "__DATE_TYPE__", "CheckOut" )
                .replaceAll( "__STATUS__", "checked-out" );
        LOGGER.info( "Retrieving CSV file from: " + url );
        HtmlPage thePage = authService.goToPage( url, webClient );
        HtmlAnchor a = thePage.getFirstByXPath( "//a[@class='export']" );
        TextPage txtPage = a.click();
        String csvContent = txtPage.getContent();
        Iterable<CSVRecord> records = CSVFormat.RFC4180
                .withFirstRecordAsHeader().parse(new StringReader(csvContent));
        
        List<CSVRecord> results = new ArrayList<CSVRecord>(); 
        for (CSVRecord record : records) {
            results.add( record );
        }
        return results;
    }
    
    /**
     * Returns the CSV records for all Agoda reservations between the given dates.
     * 
     * @param webClient web client to use
     * @param bookingRef (optional) match on booking reference
     * @param checkoutDate date guest is checking-out
     * @return all bookings where the guest is checked-out
     * @throws IOException on parsing error
     */
    public List<CSVRecord> getAgodaReservations( WebClient webClient, Date checkinDate, Date checkoutDate ) throws IOException {
        String url = getBookingURL()
                .replaceAll( "__DATE_FROM__", DATE_FORMAT_BOOKING_URL.format( checkinDate ) )
                .replaceAll( "__DATE_TO__", DATE_FORMAT_BOOKING_URL.format( checkoutDate ) )
                .replaceAll( "__BOOKING_REF_ID__", "AGO" )
                .replaceAll( "__DATE_TYPE__", "CheckIn" )
                .replaceAll( "__STATUS__", "" );
        LOGGER.info( "Retrieving CSV file from: " + url );
        HtmlPage thePage = authService.goToPage( url, webClient );
        HtmlAnchor a = thePage.getFirstByXPath( "//a[@class='export']" );
        TextPage txtPage = a.click();
        String csvContent = txtPage.getContent();
        Iterable<CSVRecord> records = CSVFormat.RFC4180
                .withFirstRecordAsHeader().parse(new StringReader(csvContent));
        
        List<CSVRecord> results = new ArrayList<CSVRecord>(); 
        for (CSVRecord record : records) {
            results.add( record );
        }
        return results;
    }
}
