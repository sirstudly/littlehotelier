package com.macbackpackers.scrapers;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URL;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import com.gargoylesoftware.htmlunit.HttpMethod;
import com.gargoylesoftware.htmlunit.Page;
import com.gargoylesoftware.htmlunit.WebClient;
import com.gargoylesoftware.htmlunit.WebRequest;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import com.gargoylesoftware.htmlunit.util.NameValuePair;
import com.google.common.base.Optional;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.macbackpackers.beans.Allocation;
import com.macbackpackers.beans.AllocationList;
import com.macbackpackers.beans.CardDetails;
import com.macbackpackers.beans.GuestCommentReportEntry;
import com.macbackpackers.beans.cloudbeds.responses.CloudbedsJsonResponse;
import com.macbackpackers.beans.cloudbeds.responses.Customer;
import com.macbackpackers.beans.cloudbeds.responses.Reservation;
import com.macbackpackers.dao.WordPressDAO;
import com.macbackpackers.exceptions.MissingUserDataException;
import com.macbackpackers.exceptions.PaymentCardNotAcceptedException;
import com.macbackpackers.exceptions.RecordPaymentFailedException;
import com.macbackpackers.exceptions.UnrecoverableFault;
import com.macbackpackers.scrapers.matchers.BedAssignment;
import com.macbackpackers.scrapers.matchers.RoomBedMatcher;
import com.macbackpackers.services.FileService;

@Component
@Scope( "prototype" )
public class CloudbedsScraper {
    
    private final Logger LOGGER = LoggerFactory.getLogger( getClass() );
    
    // a class-level lock
    private static final Object CLASS_LOCK = new Object();

    @Autowired
    @Qualifier( "gsonForCloudbeds" )
    private Gson gson;

    @Autowired
    private FileService fileService;
    
    @Autowired
    private WordPressDAO dao;
    
    @Autowired
    private CloudbedsJsonRequestFactory jsonRequestFactory;

    @Autowired
    private RoomBedMatcher roomBedMatcher;
    
    @Value( "${cloudbeds.property.id:0}" )
    private String PROPERTY_ID;

    @Value( "${process.jobs.retries:3}" )
    private int MAX_RETRY;
    
    // once we've verified we're logged in, we'll assume be logged-in for the duration of this object
    private boolean loggedIn;

    /**
     * Verifies that we're logged in (or otherwise log us in if not).
     * 
     * @param webClient web client instance to use
     * @throws IOException on connection error
     */
    private void validateLoggedIn( WebClient webClient ) throws IOException {

        // should only need to do this once for the lifetime of this object
        if( loggedIn ) {
            return;
        }

        // don't allow multiple threads writing to the same cookie file at the same time
        synchronized( CLASS_LOCK ) {
            fileService.loadCookiesFromFile( webClient );
    
            // we'll just send a sample request; we just want to make sure we get a valid response back
            WebRequest requestSettings = jsonRequestFactory.createGetPaymentMethods();
    
            Page redirectPage = webClient.getPage( requestSettings );
            LOGGER.info( "Going to: " + redirectPage.getUrl().getPath() );
            Optional<CloudbedsJsonResponse> response = Optional.fromNullable( 
                    gson.fromJson( redirectPage.getWebResponse().getContentAsString(),
                    CloudbedsJsonResponse.class ) );
            if ( false == response.isPresent() || false == response.get().isSuccess() ) {
                LOGGER.info( "I don't think we're logged in, doing it now." );
                if ( response.isPresent() ) {
                    LOGGER.info( redirectPage.getWebResponse().getContentAsString() );
                }
                login( webClient );
            }
            else {
                LOGGER.debug( redirectPage.getWebResponse().getContentAsString() );
            }

            // if we make it to this point, we're probably logged in
            loggedIn = true;
        }
    }

    /**
     * Logs in with the saved credentials.
     * 
     * @param webClient web client instance to use
     * @return logged in page
     * @throws IOException on page load failure
     */
    public HtmlPage login( WebClient webClient ) throws IOException {
        return login( webClient, dao.getOption( "hbo_cloudbeds_username" ), dao.getOption( "hbo_cloudbeds_password" ) );
    }

    /**
     * Logs in with the given credentials.
     * 
     * @param webClient web client instance to use
     * @param username username/email
     * @param password password
     * @return logged in page
     * @throws IOException on page load failure.
     */
    public HtmlPage login( WebClient webClient, String username, String password ) throws IOException {

        if ( StringUtils.isBlank( username ) || StringUtils.isBlank( password ) ) {
            throw new UnrecoverableFault( "Missing login details for Cloudbeds." );
        }
        URL url = new URL( "https://hotels.cloudbeds.com/auth/login" );
        WebRequest requestSettings = new WebRequest( url, HttpMethod.POST );

        requestSettings.setAdditionalHeader( "Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,image/apng,*/*;q=0.8" );
        requestSettings.setAdditionalHeader( "Content-Type", "application/x-www-form-urlencoded" );
        requestSettings.setAdditionalHeader( "Referer", "https://hotels.cloudbeds.com/auth/login" );
        requestSettings.setAdditionalHeader( "Accept-Language", "en-GB,en-US;q=0.9,en;q=0.8" );
        requestSettings.setAdditionalHeader( "Accept-Encoding", "gzip, deflate, br" );
        requestSettings.setAdditionalHeader( "Cache-Control", "no-cache" );
        requestSettings.setAdditionalHeader("Origin", "https://hotels.cloudbeds.com");
        requestSettings.setAdditionalHeader( "User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/65.0.3325.181 Safari/537.36" );
        requestSettings.setAdditionalHeader( "Upgrade-Insecure-Requests", "1" );

        requestSettings.setRequestParameters( Arrays.asList(
                new NameValuePair( "email", username ),
                new NameValuePair( "password", password ),
                new NameValuePair( "return_url", "" ) ) );

        HtmlPage redirectPage = webClient.getPage( requestSettings );
        LOGGER.info( "Going to: " + redirectPage.getBaseURI() );

        if ( redirectPage.getBaseURI().contains( "/login" ) ) {
            throw new UnrecoverableFault( "Login failed." );
        }

        fileService.writeCookiesToFile( webClient );
        return redirectPage;
    }

    /**
     * Goes to the Cloudbeds dashboard. Will login if required.
     * 
     * @param webClient web client instance to use
     * @return dashboard page
     * @throws IOException on navigation failure
     */
    public HtmlPage loadDashboard( WebClient webClient ) throws IOException {
        HtmlPage loadedPage = webClient.getPage( "https://hotels.cloudbeds.com/connect/" + PROPERTY_ID );
        LOGGER.info( "Loading Dashboard." );
        if ( loadedPage.getUrl().getPath().contains( "/login" ) ) {
            fileService.loadCookiesFromFile( webClient );
            LOGGER.info( "Oops, we're not logged in. Logging in now." );
            login( webClient, dao.getOption( "hbo_cloudbeds_username" ), dao.getOption( "hbo_cloudbeds_password" ) );
        }
        return loadedPage;
    }

    /**
     * Loads the given reservation by ID.
     * 
     * @param webClient web client instance to use
     * @param reservationId unique cloudbeds reservation ID
     * @return the loaded reservation (not-null)
     * @throws IOException on load failure
     * @throws MissingUserDataException if reservation not found
     */
    public Reservation getReservation( WebClient webClient, String reservationId ) throws IOException {

        validateLoggedIn( webClient );
        WebRequest requestSettings = jsonRequestFactory.createGetReservationRequest( reservationId );

        Page redirectPage = webClient.getPage( requestSettings );
        LOGGER.info( "Pulling data for " + reservationId + " from: " + redirectPage.getUrl().getPath() );
        LOGGER.debug( redirectPage.getWebResponse().getContentAsString() );

        Optional<Reservation> r = Optional.fromNullable( gson.fromJson( redirectPage.getWebResponse().getContentAsString(), Reservation.class ) );
        if ( false == r.isPresent() || false == r.get().isSuccess() ) {
            if( r.isPresent() ) {
                LOGGER.info( redirectPage.getWebResponse().getContentAsString() );
            }
            throw new MissingUserDataException( "Reservation not found." );
        }

        // need to parse the credit_cards object manually to check for presence
        JsonObject rootElem = gson.fromJson( redirectPage.getWebResponse().getContentAsString(), JsonObject.class );
        JsonElement creditCardsElem = rootElem.get( "credit_cards" );
        if ( creditCardsElem.isJsonObject() && creditCardsElem.getAsJsonObject().entrySet().size() > 0 ) {
            r.get().setCardDetailsPresent( true );
        }
        return r.get();
    }

    /**
     * Gets reservation but retries {@code MAX_RETRY} number of times before failing.
     * 
     * @param webClient web client instance to use
     * @param reservationId the reservation to query
     * @return non-null reservation
     */
    private Reservation getReservationRetry( WebClient webClient, String reservationId ) {
        for ( int i = 0 ; i < MAX_RETRY ; i++ ) {
            try {
                return getReservation( webClient, reservationId );
            }
            catch ( IOException ex ) {
                LOGGER.error( "Failed to retrieve reservation " + reservationId, ex );
            }
        }
        throw new UnrecoverableFault( "Max attempts made to retrieve reservation " + reservationId );
    }

    /**
     * Converts a Reservation object (which contains multiple bed assignments) into a List of
     * Allocation.
     * 
     * @param lhWebClient web client instance to use
     * @param jobId job ID to populate allocation
     * @param r reservation to be converted
     * @return non-null list of allocation
     */
    private List<Allocation> reservationToAllocation( int jobId, Reservation r ) {
        
        // we create one record for each "booking room"
        return r.getBookingRooms().stream()
            .map( br -> { 
                BedAssignment bed = roomBedMatcher.parse( br.getRoomNumber() );
                Allocation a = new Allocation();
                a.setBedName( bed.getBedName() );
                a.setBookedDate( LocalDate.parse( r.getBookingDateHotelTime().substring( 0, 10 ) ) );
                a.setBookingReference( 
                        StringUtils.defaultIfBlank( r.getThirdPartyIdentifier(), r.getIdentifier() ) );
                a.setBookingSource( r.getSourceName() );
                a.setCheckinDate( LocalDate.parse( br.getStartDate() ) );
                a.setCheckoutDate( LocalDate.parse( br.getEndDate() ) );
                a.setDataHref( "/connect/" + PROPERTY_ID + "#/reservations/" + r.getReservationId());
                a.setGuestName( r.getFirstName() + " " + r.getLastName() );
                a.setJobId( jobId );
                a.setNumberGuests( r.getAdultsNumber() + r.getKidsNumber() );
                a.setPaymentOutstanding( r.getBalanceDue() );
                a.setPaymentTotal( r.getGrandTotal() );
                a.setReservationId( Integer.parseInt( r.getReservationId() ) );
                a.setRoom( bed.getRoom() );
                a.setRoomId( br.getRoomId() );
                a.setRoomTypeId( Integer.parseInt( br.getRoomTypeId() ) );
                a.setStatus( r.getStatus() );
                a.setViewed( true );
                a.setNotes( r.getNotesAsString() );
                return a;
            } )
            .collect( Collectors.toList() );
    }

    /**
     * Retrieve all customers between the given checkin dates (inclusive).
     * 
     * @param webClient web client instance to use
     * @param checkinDateStart checkin date start
     * @param checkinDateEnd checkin date end (inclusive)
     * @return non-null customer list
     * @throws IOException on page load failure
     */
    public List<Customer> getCustomers( WebClient webClient, LocalDate checkinDateStart, LocalDate checkinDateEnd ) throws IOException {
        return getCustomers( webClient, jsonRequestFactory.createGetCustomersRequest(
                checkinDateStart, checkinDateEnd ) );
    }

    /**
     * Get all reservations staying between the given checkin date range.
     * 
     * @param webClient web client instance to use
     * @param stayDateStart stay date (inclusive)
     * @param stayDateEnd stay date (exclusive)
     * @return non-null list of customer reservations
     * @throws IOException
     */
    public List<Customer> getReservations( WebClient webClient, LocalDate stayDateStart, LocalDate stayDateEnd ) throws IOException {
        return getCustomers( webClient, jsonRequestFactory.createGetReservationsRequestByStayDate(
                stayDateStart, stayDateEnd ) );
    }

    /**
     * Get all reservations matching the given query.
     * 
     * @param webClient web client instance to use
     * @param query the query string
     * @return non-null list of customer reservations
     * @throws IOException
     */
    public List<Customer> getReservations( WebClient webClient, String query ) throws IOException {
        return getCustomers( webClient, jsonRequestFactory.createGetReservationsRequest( query ) );
    }

    /**
     * Get all customers/reservations using the given request.
     * 
     * @param webClient web client instance to use
     * @param requestSettings request details
     * @return non-null list of customer reservations
     * @throws IOException
     */
    private List<Customer> getCustomers( WebClient webClient, WebRequest requestSettings ) throws IOException {
        
        validateLoggedIn( webClient );
        Page redirectPage = webClient.getPage( requestSettings );
        LOGGER.info( "Going to: " + redirectPage.getUrl().getPath() );
        LOGGER.debug( redirectPage.getWebResponse().getContentAsString() );
        
        Optional<JsonElement> jelement = Optional.fromNullable( gson.fromJson( redirectPage.getWebResponse().getContentAsString(), JsonElement.class ) );
        if( false == jelement.isPresent() ) {
            throw new MissingUserDataException( "Failed to retrieve reservations." );
        }

        Optional<JsonObject> jobject = Optional.fromNullable( jelement.get().getAsJsonObject() );
        if( false == jobject.isPresent() ) {
            throw new MissingUserDataException( "Failed to retrieve reservations." );
        }

        Optional<JsonArray> jarray = Optional.fromNullable( jobject.get().getAsJsonArray( "aaData" ) );
        if( false == jarray.isPresent() ) {
            throw new MissingUserDataException( "Failed to retrieve reservations." );
        }
        return Arrays.asList( gson.fromJson( jarray.get(), Customer[].class ) );
    }

    /**
     * Add a payment record to the given reservation.
     * 
     * @param webClient web client instance to use
     * @param reservationId unique reservation ID
     * @param cardType one of "mastercard", "visa". Anything else will blank the field.
     * @param amount amount to add
     * @param description description of payment
     * @throws IOException on page load failure
     * @throws RecordPaymentFailedException payment record failure
     */
    public void addPayment( WebClient webClient, String reservationId, String cardType, BigDecimal amount, String description ) throws IOException, RecordPaymentFailedException {

        // first we need to find a "room" we're booking to
        // it doesn't actually map to a room, just an assigned guest
        // it doesn't even have to be an allocated room
        Reservation reservation = getReservation( webClient, reservationId );
        if ( reservation.getBookingRooms() == null || reservation.getBookingRooms().isEmpty() ) {
            throw new MissingUserDataException( "Unable to find allocation to assign payment to!" );
        }

        // just take the first one
        String bookingRoomId = reservation.getBookingRooms().get( 0 ).getId();

        WebRequest requestSettings = jsonRequestFactory.createAddNewPaymentRequest(
                reservationId, bookingRoomId, cardType, amount, description );

        Page redirectPage = webClient.getPage( requestSettings );
        LOGGER.info( "Going to: " + redirectPage.getUrl().getPath() );
        String jsonResponse = redirectPage.getWebResponse().getContentAsString();
        LOGGER.debug( jsonResponse );
        
        if( StringUtils.isBlank( jsonResponse ) ) {
            throw new MissingUserDataException( "Missing response from add payment request?" );
        }
        CloudbedsJsonResponse response = gson.fromJson( jsonResponse, CloudbedsJsonResponse.class );

        // throw a wobbly if response not successful
        if ( response.isFailure() ) {
            throw new RecordPaymentFailedException( response.getMessage() );
        }
    }

    /**
     * Adds a note to an existing reservation.
     * 
     * @param webClient web client instance to use
     * @param reservationId unique reservation ID
     * @param note note description to add
     * @throws IOException on page load failure
     */
    public void addNote( WebClient webClient, String reservationId, String note ) throws IOException {

        validateLoggedIn( webClient );
        WebRequest requestSettings = jsonRequestFactory.createAddNoteRequest( reservationId, note );

        Page redirectPage = webClient.getPage( requestSettings );
        LOGGER.info( "POST: " + redirectPage.getUrl().getPath() );
        String jsonResponse = redirectPage.getWebResponse().getContentAsString();
        LOGGER.debug( jsonResponse );

        if ( StringUtils.isBlank( jsonResponse ) ) {
            throw new MissingUserDataException( "Missing response from add note request?" );
        }
        CloudbedsJsonResponse response = gson.fromJson( jsonResponse, CloudbedsJsonResponse.class );

        // throw a wobbly if response not successful
        if ( response.isFailure() ) {
            throw new IOException( response.getMessage() );
        }
    }

    /**
     * Adds a payment card to an existing reservation.
     * 
     * @param webClient web client instance to use
     * @param reservationId unique reservation ID
     * @param cardDetails card to add
     * @throws IOException on page load failure
     */
    public void addCardDetails( WebClient webClient, String reservationId, CardDetails cardDetails ) throws IOException {

        validateLoggedIn( webClient );
        WebRequest requestSettings = jsonRequestFactory.createAddCreditCardRequest( reservationId, cardDetails );

        Page redirectPage = webClient.getPage( requestSettings );
        LOGGER.info( "POST: " + redirectPage.getUrl().getPath() );
        String jsonResponse = redirectPage.getWebResponse().getContentAsString();
        LOGGER.debug( jsonResponse );

        if ( StringUtils.isBlank( jsonResponse ) ) {
            throw new MissingUserDataException( "Missing response from add card details request?" );
        }
        CloudbedsJsonResponse response = gson.fromJson( jsonResponse, CloudbedsJsonResponse.class );

        // throw a wobbly if response not successful
        if ( response.isFailure() ) {
            throw new PaymentCardNotAcceptedException( response.getMessage() );
        }
    }

    /**
     * Sends a ping request to the server.
     * 
     * @param webClient web client instance to use
     * @throws IOException
     */
    public void ping( WebClient webClient ) throws IOException {
        WebRequest requestSettings = jsonRequestFactory.createPingRequest();

        Page redirectPage = webClient.getPage( requestSettings );
        LOGGER.info( "Going to: " + redirectPage.getUrl().getPath() );
        LOGGER.debug( redirectPage.getWebResponse().getContentAsString() );
    }

    /**
     * Dumps the allocations starting on the given date (inclusive).
     * 
     * @param webClient web client instance to use
     * @param jobId the job ID to associate with this dump
     * @param startDate the start date to check allocations for (inclusive)
     * @param endDate the end date to check allocations for (exclusive)
     * @throws IOException on read/write error
     */
    public void dumpAllocationsFrom( WebClient webClient, int jobId, LocalDate startDate, LocalDate endDate ) throws IOException {
        AllocationList allocations = new AllocationList();
        List<Reservation> reservations = getReservations( webClient, startDate, endDate ).stream()
                .map( c -> getReservationRetry( webClient, c.getId() ) )
                .collect( Collectors.toList() );
        reservations.stream()
                .map( r -> reservationToAllocation( jobId, r ) )
                .forEach( a -> allocations.addAll( a ) );
        dao.insertAllocations( allocations );

        // now update the comments
        List<GuestCommentReportEntry> guestComments = reservations.stream()
                .filter( r -> StringUtils.isNotBlank( r.getSpecialRequests() ) )
                .map( r -> new GuestCommentReportEntry(
                        Integer.parseInt( r.getReservationId() ),
                        r.getSpecialRequests() ) )
                .collect( Collectors.toList() );
        dao.updateGuestCommentsForReservations( guestComments );
    }

    /**
     * Goes to the given page.
     * 
     * @param webClient web client instance to use
     * @param pageURL URL
     * @return non-null page
     * @throws IOException on i/o error
     */
    public HtmlPage navigateToPage( WebClient webClient, String pageURL ) throws IOException {
        // attempt to go the page directly using the current credentials
        LOGGER.info( "Loading page: " + pageURL );
        validateLoggedIn( webClient );
        HtmlPage nextPage = webClient.getPage( pageURL );
        LOGGER.debug( "Now on " + nextPage.getUrl() );
        if ( nextPage.getUrl().getPath().endsWith( "/login" ) ) {
            throw new UnrecoverableFault( "Login to Cloudbeds failed. Has the password changed?" );
        }
        return nextPage;
    }

    /**
     * Attempts to find the CB booking based on either the bookingRef or guest name. Throws
     * UnrecoverableFault if booking could not be resolved.
     * 
     * @param webClient web client instance to use
     * @param bookingRef LH booking reference
     * @param guestName name of guest
     * @return non-null reservation
     * @throws IOException on i/o error
     */
    public Reservation findBookingByLHBookingRef( WebClient webClient, String bookingRef, String guestName ) throws IOException {

        // first try to search by booking ref
        Pattern p = Pattern.compile( "^\\D*(\\d+)$" );
        Matcher m = p.matcher( bookingRef );
        if ( m.find() ) {
            List<Customer> reservations = getReservations( webClient, m.group( 1 ) );
            if ( reservations.size() > 1 ) {
                throw new UnrecoverableFault( "More than one CB booking found for " + bookingRef );
            }
            else if ( reservations.size() == 1 ) {
                return getReservationRetry( webClient, reservations.get( 0 ).getId() );
            }
        }

        // could not find booking by booking ref; try with guest name
        List<Customer> reservations = getReservations( webClient, guestName );
        if ( reservations.size() > 1 ) {
            throw new UnrecoverableFault( "More than one CB booking found for " + bookingRef + ": " + guestName );
        }
        else if ( reservations.size() == 1 ) {
            return getReservationRetry( webClient, reservations.get( 0 ).getId() );
        }
        throw new UnrecoverableFault( "No CB bookings found for " + bookingRef + ": " + guestName );
    }
}