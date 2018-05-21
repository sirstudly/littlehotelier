package com.macbackpackers.scrapers;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URL;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
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
import com.macbackpackers.beans.cloudbeds.responses.CloudbedsJsonResponse;
import com.macbackpackers.beans.cloudbeds.responses.Customer;
import com.macbackpackers.beans.cloudbeds.responses.Reservation;
import com.macbackpackers.dao.WordPressDAO;
import com.macbackpackers.exceptions.MissingUserDataException;
import com.macbackpackers.exceptions.RecordPaymentFailedException;
import com.macbackpackers.exceptions.UnrecoverableFault;
import com.macbackpackers.services.FileService;

@Component
@Scope( "prototype" )
public class CloudbedsScraper {
    
    private final Logger LOGGER = LoggerFactory.getLogger( getClass() );

    @Autowired
    @Qualifier( "webClient" )
    private WebClient webClient;

    @Autowired
    @Qualifier( "gsonForCloudbeds" )
    private Gson gson;

    @Autowired
    private FileService fileService;
    
    @Autowired
    private WordPressDAO dao;
    
    @Autowired
    private CloudbedsJsonRequestFactory jsonRequestFactory;

    @Value( "${cloudbeds.property.id}" )
    private String PROPERTY_ID;

    @Value( "${process.jobs.retries:3}" )
    private int MAX_RETRY;

    /**
     * Verifies that we're logged in (or otherwise log us in if not).
     * 
     * @throws IOException on connection error
     */
    private synchronized void validateLoggedIn() throws IOException {

        fileService.loadCookiesFromFile( webClient );

        // we'll just send a sample request; we just want to make sure we get a valid response back
        WebRequest requestSettings = jsonRequestFactory.createGetPaymentMethods();

        Page redirectPage = webClient.getPage( requestSettings );
        LOGGER.info( "Going to: " + redirectPage.getUrl().getPath() );
        Optional<CloudbedsJsonResponse> response = Optional.fromNullable( gson.fromJson( redirectPage.getWebResponse().getContentAsString(),
                CloudbedsJsonResponse.class ) );
        if ( false == response.isPresent() || false == response.get().isSuccess() ) {
            LOGGER.info( "I don't think we're logged in, doing it now." );
            if ( response.isPresent() ) {
                LOGGER.info( redirectPage.getWebResponse().getContentAsString() );
            }
            login();
        }
        else {
            LOGGER.debug( redirectPage.getWebResponse().getContentAsString() );
        }
    }

    /**
     * Logs in with the saved credentials.
     * @return logged in page
     * @throws IOException on page load failure
     */
    public HtmlPage login() throws IOException {
        return login( dao.getOption( "hbo_cloudbeds_username" ), dao.getOption( "hbo_cloudbeds_password" ) );
    }

    /**
     * Logs in with the given credentials.
     * @param username username/email
     * @param password password
     * @return logged in page
     * @throws IOException on page load failure.
     */
    public HtmlPage login( String username, String password ) throws IOException {

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
     * @return dashboard page
     * @throws IOException on navigation failure
     */
    public HtmlPage loadDashboard() throws IOException {
        HtmlPage loadedPage = webClient.getPage( "https://hotels.cloudbeds.com/connect/" + PROPERTY_ID );
        LOGGER.info( "Loading Dashboard." );
        if ( loadedPage.getUrl().getPath().contains( "/login" ) ) {
            fileService.loadCookiesFromFile( webClient );
            LOGGER.info( "Oops, we're not logged in. Logging in now." );
            login( dao.getOption( "hbo_cloudbeds_username" ), dao.getOption( "hbo_cloudbeds_password" ) );
        }
        return loadedPage;
    }

    /**
     * Loads the given reservation by ID.
     * @param reservationId unique cloudbeds reservation ID
     * @return the loaded reservation (not-null)
     * @throws IOException on load failure
     * @throws MissingUserDataException if reservation not found
     */
    public Reservation getReservation( String reservationId ) throws IOException {

        validateLoggedIn();
        WebRequest requestSettings = jsonRequestFactory.createGetReservationRequest( reservationId );

        Page redirectPage = webClient.getPage( requestSettings );
        LOGGER.info( "Going to: " + redirectPage.getUrl().getPath() );
        LOGGER.debug( redirectPage.getWebResponse().getContentAsString() );

        Optional<Reservation> r = Optional.fromNullable( gson.fromJson( redirectPage.getWebResponse().getContentAsString(), Reservation.class ) );
        if ( false == r.isPresent() || false == r.get().isSuccess() ) {
            if( r.isPresent() ) {
                LOGGER.info( redirectPage.getWebResponse().getContentAsString() );
            }
            throw new MissingUserDataException( "Reservation not found." );
        }
        return r.get();
    }

    private Reservation getReservationRetry( String reservationId ) {
        for ( int i = 0 ; i < MAX_RETRY ; i++ ) {
            try {
                return getReservation( reservationId );
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
     * @param jobId job ID to populate allocation
     * @param r reservation to be converted
     * @return non-null list of allocation
     */
    private List<Allocation> reservationToAllocation( int jobId, Reservation r ) {
        
        // we create one record for each "booking room"
        return r.getBookingRooms().stream()
            .map( br -> { 
                Allocation a = new Allocation();
                //a.setBedName( ?? );
                a.setBookedDate( r.getBookingDateHotelTime() );
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
                //a.setPaymentStatus( paymentStatus );
                a.setPaymentTotal( r.getPaidValue() );
                //a.setRatePlanName( ratePlanName );
                a.setReservationId( Integer.parseInt( r.getReservationId() ) );
                a.setRoom( br.getRoomNumber() );
                a.setRoomId( Integer.parseInt( br.getId() ) );
                a.setRoomTypeId( Integer.parseInt( br.getRoomTypeId() ) );
                a.setStatus( r.getStatus() );
                a.setViewed( true );
                return a;
            } )
            .collect(Collectors.toList());
    }

    /**
     * Retrieve all customers between the given checkin dates (inclusive).
     * @param checkinDateStart checkin date start
     * @param checkinDateEnd checkin date end (inclusive)
     * @return non-null customer list
     * @throws IOException on page load failure
     */
    public List<Customer> getCustomers( LocalDate checkinDateStart, LocalDate checkinDateEnd ) throws IOException {
        
        validateLoggedIn();
        WebRequest requestSettings = jsonRequestFactory.createGetCustomersRequest( checkinDateStart, checkinDateEnd );

        Page redirectPage = webClient.getPage(requestSettings);
        LOGGER.info( "Going to: " + redirectPage.getUrl().getPath() );
        LOGGER.debug( redirectPage.getWebResponse().getContentAsString() );
        
        Optional<JsonElement> jelement = Optional.fromNullable( gson.fromJson( redirectPage.getWebResponse().getContentAsString(), JsonElement.class ) );
        if( false == jelement.isPresent() ) {
            throw new MissingUserDataException( "Failed to retrieve customers." );
        }
        JsonObject jobject = jelement.get().getAsJsonObject();
        Optional<JsonArray> jarray = Optional.fromNullable( jobject.getAsJsonArray( "aaData" ) );
        if( false == jarray.isPresent() ) {
            throw new MissingUserDataException( "Failed to retrieve customers." );
        }
        return Arrays.asList( gson.fromJson( jarray.get(), Customer[].class ) );
    }

    /**
     * Get all reservations between the given checkin date range.
     * 
     * @param checkinDateStart checkin date (inclusive)
     * @param checkinDateEnd checkin date (inclusive)
     * @return non-null list of customer reservations
     * @throws IOException
     */
    public List<Customer> getReservations( LocalDate checkinDateStart, LocalDate checkinDateEnd ) throws IOException {
        
        validateLoggedIn();
        WebRequest requestSettings = jsonRequestFactory.createGetReservationsRequest( checkinDateStart, checkinDateEnd );

        Page redirectPage = webClient.getPage(requestSettings);
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
     * @param reservationId unique reservation ID
     * @param cardType one of "mastercard", "visa". Anything else will blank the field.
     * @param amount amount to add
     * @param description description of payment
     * @throws IOException on page load failure
     * @throws RecordPaymentFailedException payment record failure
     */
    public void addPayment( String reservationId, String cardType, BigDecimal amount, String description ) throws IOException, RecordPaymentFailedException {

        // first we need to find a "room" we're booking to
        // it doesn't actually map to a room, just an assigned guest
        // it doesn't even have to be an allocated room
        Reservation reservation = getReservation( reservationId );
        if ( reservation.getBookingRooms() == null || reservation.getBookingRooms().isEmpty() ) {
            throw new MissingUserDataException( "Unable to find allocation to assign payment to!" );
        }

        // just take the first one
        String bookingRoomId = reservation.getBookingRooms().get( 0 ).getId();

        WebRequest requestSettings = jsonRequestFactory.createAddNewPaymentRequest(
                reservationId, bookingRoomId, cardType, amount, description );

        Page redirectPage = webClient.getPage( requestSettings );
        LOGGER.info( "Going to: " + redirectPage.getUrl().getPath() );
        LOGGER.debug( redirectPage.getWebResponse().getContentAsString() );

        // throw a wobbly if response not successful
        Optional<JsonElement> jelement = Optional.fromNullable( gson.fromJson( redirectPage.getWebResponse().getContentAsString(), JsonElement.class ) );
        if ( false == jelement.isPresent() ) {
            throw new MissingUserDataException( "Missing response from add payment request?" );
        }

        JsonObject jobject = jelement.get().getAsJsonObject();
        if ( false == jobject.get( "success" ).getAsBoolean() ) {
            throw new RecordPaymentFailedException( jobject.get( "message" ).getAsString() );
        }
    }

    /**
     * Adds a note to an existing reservation.
     * @param reservationId unique reservation ID
     * @param note note description to add
     * @throws IOException on page load failure
     */
    public void addNote( String reservationId, String note ) throws IOException {

        validateLoggedIn();
        WebRequest requestSettings = jsonRequestFactory.createAddNoteRequest( reservationId, note );

        Page redirectPage = webClient.getPage( requestSettings );
        LOGGER.info( "POST: " + redirectPage.getUrl().getPath() );
        LOGGER.debug( redirectPage.getWebResponse().getContentAsString() );

        // throw a wobbly if response not successful
        Optional<JsonElement> jelement = Optional.fromNullable( gson.fromJson( redirectPage.getWebResponse().getContentAsString(), JsonElement.class ) );
        if ( false == jelement.isPresent() ) {
            throw new MissingUserDataException( "Missing response from add note request?" );
        }

        JsonObject jobject = jelement.get().getAsJsonObject();
        if ( false == jobject.get( "success" ).getAsBoolean() ) {
            throw new IOException( jobject.get( "message" ).getAsString() );
        }
    }

    /**
     * Sends a ping request to the server.
     * 
     * @throws IOException
     */
    public void ping() throws IOException {
        WebRequest requestSettings = jsonRequestFactory.createPingRequest();

        Page redirectPage = webClient.getPage( requestSettings );
        LOGGER.info( "Going to: " + redirectPage.getUrl().getPath() );
        LOGGER.debug( redirectPage.getWebResponse().getContentAsString() );
    }

    /**
     * Dumps the allocations starting on the given date (inclusive).
     * 
     * @param jobId the job ID to associate with this dump
     * @param startDate the start checkin date to check allocations for (inclusive)
     * @param endDate the end checkin date to check allocations for (inclusive)
     * @throws IOException on read/write error
     */
//    @Transactional
    public void dumpAllocationsFrom( int jobId, LocalDate startDate, LocalDate endDate ) throws IOException {
        AllocationList allocations = new AllocationList();
        getReservations( startDate, endDate )
            .parallelStream()
            .map( c -> getReservationRetry( c.getId() ) )
            .map( r -> reservationToAllocation( jobId, r ) )
            .forEach( a -> allocations.addAll( a ) );
        dao.insertAllocations( allocations );
   }
}